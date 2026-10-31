#!/usr/bin/env python3
"""Prepare a private, disposable Compose stack for integrated simulation checks.
No production Kubernetes resource or existing Docker volume is accessed.
Requires PyYAML and Docker Compose on the test host.
"""
import argparse
import base64
import os
from pathlib import Path
import subprocess
import yaml

APPS = ('auth', 'account', 'risk', 'order', 'matching-engine', 'audit', 'market-data', 'gateway', 'simulator')
PROJECT = 'dete-clock-preview'

def prepare(root, output):
    os.umask(0o077)
    output.mkdir(parents=True, exist_ok=True)
    private = output / 'private.pem'
    public = output / 'public.pem'
    if not private.exists():
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private)], check=True, stderr=subprocess.DEVNULL)
        subprocess.run(['openssl', 'pkey', '-in', str(private), '-pubout', '-out', str(public)], check=True)
    private_der = subprocess.check_output(['openssl', 'pkey', '-in', str(private), '-outform', 'DER'])
    # Java's RSA key reader accepts PKCS8, so use openssl pkcs8 explicitly.
    private_der = subprocess.check_output(['openssl', 'pkcs8', '-topk8', '-nocrypt', '-in', str(private), '-outform', 'DER'])
    public_der = subprocess.check_output(['openssl', 'pkey', '-pubin', '-in', str(public), '-outform', 'DER'])
    data = yaml.safe_load((root / 'infra/docker-compose-full.yml').read_text())
    keep = (*APPS, 'postgres', 'redis', 'zookeeper', 'kafka', 'kafka-init')
    data['services'] = {name: service for name, service in data['services'].items() if name in keep}
    data.pop('version', None)
    for name, service in data['services'].items():
        service.pop('container_name', None)
        service.pop('ports', None)
        service['restart'] = 'no'
        env = service.get('environment', {})
        for key in tuple(env):
            if key == 'AUTH_JWT_PRIVATE_KEY': env[key] = base64.b64encode(private_der).decode()
            elif key in ('AUTH_JWT_PUBLIC_KEY', 'SECURITY_JWT_PUBLIC_KEY'): env[key] = base64.b64encode(public_der).decode()
            elif isinstance(env[key], str) and env[key].startswith('${POSTGRES_'): env[key] = 'dete' if 'USER' in key else 'dete_local_secret'
        if name in APPS:
            service['image'] = 'dete/' + name + ':clock-preview'
            service['mem_limit'] = '448m'
            env['JAVA_OPTS'] = '-Xms64m -Xmx192m -XX:MaxMetaspaceSize=160m -XX:ActiveProcessorCount=2'
            env['MANAGEMENT_TRACING_ENABLED'] = 'false'
        if name == 'kafka':
            env['KAFKA_ADVERTISED_LISTENERS'] = 'INTERNAL://kafka:9092'
            env['KAFKA_LISTENERS'] = 'INTERNAL://0.0.0.0:9092'
            env['KAFKA_HEAP_OPTS'] = '-Xms256m -Xmx512m'
        if name == 'postgres':
            service['volumes'][1] = str(root / 'infra/postgres/init.sql') + ':/docker-entrypoint-initdb.d/init.sql:ro'
        if name == 'gateway': service['ports'] = ['127.0.0.1:28080:8080']
        if name == 'account': service['ports'] = ['127.0.0.1:28082:8082']
    path = output / 'compose.yaml'
    path.write_text(yaml.safe_dump(data))
    return path

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path, default=Path('/tmp/dete-clock-preview'))
    parser.add_argument('--start', action='store_true')
    args=parser.parse_args()
    root=args.source.resolve()
    if not (root/'settings.gradle.kts').exists(): raise SystemExit('Not an exchange source checkout')
    path=prepare(root,args.output)
    print('Prepared private preview project '+PROJECT, flush=True)
    if args.start:
        command=['docker','compose','-p',PROJECT,'-f',str(path)]
        subprocess.run(command+['up','-d','postgres','redis','zookeeper','kafka'],check=True)
        subprocess.run(command+['run','--rm','kafka-init'],check=True)
        subprocess.run(command+['up','-d',*APPS],check=True)

if __name__=='__main__': main()
