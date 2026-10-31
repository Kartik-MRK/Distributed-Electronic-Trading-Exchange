#!/usr/bin/env python3
"""Restore the sealed database/Kafka/ZooKeeper/Redis backup in a private Docker network.
Production volumes are only read by the separate backup step; this script mounts copies.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import time
import uuid
from reset_demo_market import Operator

def main():
    os.umask(0o077)
    parser=argparse.ArgumentParser()
    parser.add_argument('--backup',type=Path,required=True)
    parser.add_argument('--namespace',required=True)
    args=parser.parse_args()
    backup=args.backup.resolve()
    Operator(args.namespace,backup).verify_backup()
    scratch=backup/('rehearsal-'+uuid.uuid4().hex[:8]);scratch.mkdir()
    project='dete-restore-'+uuid.uuid4().hex[:8]
    created=[]
    def run(*command,data=None):
        result=subprocess.run(command,input=data,capture_output=True,timeout=300)
        if result.returncode: raise RuntimeError('Restore verification command failed: '+command[0])
        return result.stdout
    def docker(name,*command,data=None): return run('docker','exec','-i',project+'-'+name,*command,data=data)
    def wait(check):
        end=time.monotonic()+180
        while time.monotonic()<end:
            try:
                if check(): return
            except (RuntimeError,ValueError): pass
            time.sleep(2)
        raise RuntimeError('Restored service did not become ready')
    for name in ('kafka','zookeeper','redis'):
        target=scratch/name;target.mkdir()
        with tarfile.open(backup/(name+'.tgz')) as archive:
            for member in archive.getmembers():
                if member.issym() or member.islnk() or not (target/member.name).resolve().is_relative_to(target.resolve()): raise RuntimeError('Unsafe archive entry')
        run('sudo','tar','-xzf',str(backup/(name+'.tgz')),'-C',str(target))
    deployments=json.loads((backup/'deployments.json').read_text())['items']
    statefulsets=json.loads((backup/'statefulsets.json').read_text())['items']
    def config(items,name): return next(x for x in items if x['metadata']['name']==name)['spec']['template']['spec']['containers'][0]
    zk=config(deployments,'zookeeper'); kafka=config(statefulsets,'kafka')
    secrets=json.loads((backup/'secrets.json').read_text())['items']
    redis_password=base64.b64decode(next(x for x in secrets if x['metadata']['name']=='dete-secrets')['data']['REDIS_PASSWORD']).decode()
    run('docker','network','create',project)
    def start(name,image,env,mount=None,extra=()):
        command=['docker','run','-d','--name',project+'-'+name,'--network',project,'--network-alias',name]
        for key,value in env.items(): command+=['-e',key+'='+value]
        if mount: command+=['-v',str(scratch/name)+':'+mount]
        command += [image,*extra]
        run(*command);created.append(project+'-'+name)
    try:
        start('postgres','postgres:16-alpine',{'POSTGRES_USER':'dete','POSTGRES_PASSWORD':'restore-only','POSTGRES_DB':'dete'})
        wait(lambda: b'accepting connections' in docker('postgres','pg_isready','-U','dete','-d','dete'))
        with (backup/'database.dump').open('rb') as dump:
            restored=subprocess.run(['docker','exec','-i',project+'-postgres','pg_restore','-U','dete','-d','dete','--exit-on-error'],stdin=dump,capture_output=True,timeout=600)
            if restored.returncode: raise RuntimeError('Postgres archive failed restoration')
        def query(sql): return docker('postgres','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','dete','-d','dete',data=sql.encode()).decode().strip()
        for table,count in json.loads((backup/'rowcounts.json').read_text()).items():
            if int(query('SELECT count(*) FROM '+table))!=count: raise RuntimeError('Restored row count differs: '+table)
        if query("SELECT md5(string_agg(row_to_json(u)::text,',' ORDER BY user_id)) FROM auth.users u;")!=(backup/'users-digest.txt').read_text(): raise RuntimeError('Restored identities differ')
        zk_env={e['name']:e['value'] for e in zk['env'] if 'value' in e}
        start('zookeeper',zk['image'],zk_env,'/var/lib/zookeeper')
        kafka_env={e['name']:e['value'] for e in kafka['env'] if 'value' in e}
        kafka_env['KAFKA_HEAP_OPTS']='-Xms256m -Xmx512m'
        start('kafka',kafka['image'],kafka_env,'/var/lib/kafka/data')
        wait(lambda: b'order.commands' in docker('kafka','kafka-topics','--bootstrap-server','localhost:9092','--list'))
        offsets=docker('kafka','kafka-run-class','kafka.tools.GetOffsetShell','--broker-list','localhost:9092','--topic','order.commands','--time','-1').decode()
        def command_offsets(text): return sorted(line.strip() for line in text.splitlines() if line.startswith('order.commands:'))
        if command_offsets(offsets)!=command_offsets((backup/'command-end-offsets.txt').read_text()): raise RuntimeError('Restored Kafka history differs')
        groups=docker('kafka','kafka-consumer-groups','--bootstrap-server','localhost:9092','--describe','--all-groups').decode()
        def group_offsets(text):
            return sorted(tuple(parts[:4]) for line in text.splitlines() if len(parts:=line.split())>=4 and parts[2].isdigit())
        if group_offsets(groups)!=group_offsets((backup/'offsets.txt').read_text()): raise RuntimeError('Restored consumer offsets differ')
        start('redis','redis:7-alpine',{},'/data',('redis-server','--requirepass',redis_password))
        wait(lambda: docker('redis','redis-cli','-a',redis_password,'PING').strip()==b'PONG')
        if docker('redis','redis-cli','-a',redis_password,'DBSIZE').strip()!=(backup/'redis-key-count.txt').read_bytes().strip(): raise RuntimeError('Restored Redis keys differ')
        proof={'backup_manifest_sha256':hashlib.sha256((backup/'sha256.json').read_bytes()).hexdigest(),'database':True,'kafka':True,'redis':True,'images':'Original images archived with deployment references','checked':'All stored table counts, identity digest, command history ends, consumer offsets and cache key count'}
        (backup/'restore-rehearsal.json').write_text(json.dumps(proof,indent=2))
        print('PASS: database, identities, Kafka history/offsets and Redis restored in isolated containers',flush=True)
    finally:
        for name in reversed(created): run('docker','rm','-f',name)
        run('docker','network','rm',project)

if __name__=='__main__': main()
