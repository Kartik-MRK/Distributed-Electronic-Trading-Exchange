#!/usr/bin/env python3
"""Explicit, dry-run-first coordinated reset of the approved disposable trading data.
Authentication identities and broker internals are never reset. See docs/simulation_reset_runbook.md.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid
import zipfile

APPS=('auth','account','order','matching-engine','risk','audit','market-data','simulator','gateway')
MANIFEST={
 'tables':['account.balances','account.ledger_entries','account.settlements','account.outbox','account.order_reservations','order_svc.orders','order_svc.outbox','market_data.trades','market_data.candles','audit.audit_log','simulator.matching_output','simulator.matching_admission','simulator.consumer_receipt','simulator.order_intent','simulator.market_clock','simulator.market_resume_price','simulator.simulation_session'],
 'topics':{'order.commands':3,'order.events':3,'trade.executions':3,'market-data':3,'ledger.events':3,'audit.events':3,'simulator.commands':1,'order.commands.DLQ':1,'order.events.DLQ':1,'trade.executions.DLQ':1,'ledger.events.DLQ':1,'audit.events.DLQ':1,'system.alerts':1},
 'groups':['order-service-group','market-data-trade-group','account-service-group','market-data-order-group','audit-service-group','matching-engine-group','market-data-snapshot-group','risk-service-group'],
 'cache_prefixes':['idempotency:order:'],
 'seed_scaled':{'USD':5000000000000,'BTC':100000000,'ETH':500000000,'SOL':5000000000},
 'bot_seed_scaled':{'USD':10000000000000000,'BTC':1000000000000,'ETH':10000000000000,'SOL':100000000000000},
}

def file_sha256(path):
    digest=hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda:source.read(1024*1024),b''): digest.update(chunk)
    return digest.hexdigest()

def validate_manifest(manifest):
    if manifest!=MANIFEST: raise ValueError('Only the reviewed reset manifest is allowed')

def reset_sql(epoch):
    epoch=str(uuid.UUID(epoch))
    values=','.join("('%s',%d::bigint,%d::bigint,%d)"%(asset,amount,MANIFEST['bot_seed_scaled'][asset],index+1) for index,(asset,amount) in enumerate(MANIFEST['seed_scaled'].items()))
    # Existing application seed IDs use Java UUID.nameUUIDFromBytes (MD5/version 3).
    # Retain those IDs for known seed accounts so startup provisioning cannot double credit them.
    return """BEGIN;
TRUNCATE """+','.join(MANIFEST['tables'])+""" RESTART IDENTITY;
INSERT INTO account.accounts(account_id) SELECT user_id FROM auth.users ON CONFLICT DO NOTHING;
CREATE TEMP TABLE reset_seed ON COMMIT DROP AS
SELECT u.user_id account_id,v.asset,
 CASE WHEN u.username IN ('bot_maker','bot_taker') THEN v.bot_amount ELSE v.amount END amount,
 v.seq, md5('simulator-seed:'||u.user_id::text||':'||v.asset) reference
FROM auth.users u CROSS JOIN (VALUES """+values+""") v(asset,amount,bot_amount,seq);
INSERT INTO account.balances(account_id,asset,available,reserved) SELECT account_id,asset,amount,0 FROM reset_seed;
INSERT INTO account.ledger_entries(account_id,asset,entry_type,amount,reference_id,sequence_num)
SELECT account_id,asset,'DEPOSIT',amount,
 (substr(reference,1,12)||'3'||substr(reference,14,3)||substr('89ab',1+(('x'||substr(reference,17,1))::bit(4)::int & 3),1)||substr(reference,18))::uuid,seq FROM reset_seed;
INSERT INTO simulator.market_clock(epoch_id,initialized_at,elapsed_ms,maintenance)
VALUES ('"""+epoch+"""',date_trunc('milliseconds',clock_timestamp()),0,true);
COMMIT;
"""

class Operator:
    def __init__(self,namespace,backup):
        if not re.fullmatch(r'[a-z][a-z0-9-]*',namespace): raise ValueError('Invalid namespace')
        self.namespace=namespace; self.backup=backup
    def run(self,*args,data=None,timeout=300):
        result=subprocess.run(args,input=data,capture_output=True,timeout=timeout)
        if result.returncode: raise RuntimeError('Operator command failed: '+args[0]+' (output withheld; may contain secrets)')
        return result.stdout
    def k(self,*args,data=None,timeout=300): return self.run('kubectl','-n',self.namespace,*args,data=data,timeout=timeout)
    def json(self,*args): return json.loads(self.k(*args,'-o','json'))
    def query(self,sql,database='dete'):
        return self.k('exec','-i','postgres-0','--','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','dete','-d',database,data=sql.encode()).decode().strip()
    def kafka(self,*args): return self.k('exec','kafka-0','--',*args).decode()
    def save(self,name,*command):
        with (self.backup/name).open('wb') as output:
            result=subprocess.run(command,stdout=output,stderr=subprocess.PIPE,timeout=900)
            if result.returncode: raise RuntimeError('Backup failed: '+name)
    def stopped(self):
        deployments=self.json('get','deployments')['items']
        for name in APPS:
            deployment=next(d for d in deployments if d['metadata']['name']==name)
            if deployment['spec']['replicas']!=0: raise RuntimeError('Writers must be stopped: '+name)
        if any(p['metadata'].get('labels',{}).get('app') in APPS for p in self.json('get','pods')['items']): raise RuntimeError('Application pods still exist')
    def volume(self,claim):
        pvc=self.json('get','pvc',claim)
        pv=json.loads(self.run('kubectl','get','pv',pvc['spec']['volumeName'],'-o','json'))
        if pv['spec']['claimRef']['namespace']!=self.namespace or pv['spec']['claimRef']['name']!=claim: raise RuntimeError('Volume ownership mismatch')
        path=self.run('sudo','realpath','--',pv['spec'].get('hostPath',pv['spec'].get('local'))['path']).decode().strip()
        if not path.startswith('/var/lib/rancher/k3s/storage/') or not path.endswith('_'+self.namespace+'_'+claim): raise RuntimeError('Unexpected volume path')
        return path
    def inventory(self):
        print(json.dumps({'namespace':self.namespace, 'manifest':MANIFEST,'preserve':['auth.*','account.accounts','Kafka broker internals','unlisted topics/groups/cache keys']},indent=2))
    def backup_all(self):
        spec=self.json('get','deployment','zookeeper')['spec']['template']['spec']
        mounts=next(c for c in spec['containers'] if c['name']=='zookeeper').get('volumeMounts',[])
        volumes={v['name']:v for v in spec.get('volumes',[])}
        for child in ('data','log'):
            valid=any(m.get('mountPath')=='/var/lib/zookeeper/'+child
                and m.get('subPath')==child and not m.get('readOnly',False)
                and volumes.get(m['name'],{}).get('persistentVolumeClaim',{}).get('claimName')=='zookeeper-data'
                for m in mounts)
            if not valid: raise ValueError('ZooKeeper data and log must have explicit persistent subPath mounts before backup')
        self.backup.mkdir(parents=True,exist_ok=False)
        (self.backup/'reset-manifest.json').write_text(json.dumps(MANIFEST,indent=2))
        for resource in ('deployments','statefulsets','services','configmaps','secrets','pvc'):
            self.save(resource+'.json','kubectl','-n',self.namespace,'get',resource,'-o','json')
        self.save('helm-values.yaml','helm','get','values','dete','-n',self.namespace,'-o','yaml')
        self.save('helm-manifest.yaml','helm','get','manifest','dete','-n',self.namespace)
        self.k('scale','--replicas=0',*('deployment/'+n for n in APPS))
        self.k('wait','--for=delete','pod','-l','app in ('+','.join(APPS)+')','--timeout=180s')
        self.stopped()
        self.save('database.dump','kubectl','-n',self.namespace,'exec','postgres-0','--','pg_dump','-U','dete','-d','dete','-Fc')
        counts={}
        for table in ('auth.users','auth.refresh_tokens','account.accounts',*MANIFEST['tables']):
            if self.query("SELECT to_regclass('"+table+"') IS NOT NULL;")=='t': counts[table]=int(self.query('SELECT count(*) FROM '+table+';'))
        (self.backup/'rowcounts.json').write_text(json.dumps(counts,indent=2))
        (self.backup/'users-digest.txt').write_text(self.query("SELECT md5(string_agg(row_to_json(u)::text,',' ORDER BY user_id)) FROM auth.users u;"))
        (self.backup/'topics.txt').write_text(self.kafka('kafka-topics','--bootstrap-server','localhost:9092','--describe'))
        (self.backup/'offsets.txt').write_text(self.kafka('kafka-consumer-groups','--bootstrap-server','localhost:9092','--describe','--all-groups'))
        (self.backup/'command-end-offsets.txt').write_text(self.kafka('kafka-run-class','kafka.tools.GetOffsetShell','--broker-list','localhost:9092','--topic','order.commands','--time','-1'))
        volumes={name:self.volume(claim) for name,claim in [('kafka','kafka-data-kafka-0'),('zookeeper','zookeeper-data')]}
        (self.backup/'volume-paths.json').write_text(json.dumps(volumes))
        self.k('scale','statefulset/kafka','--replicas=0')
        self.k('wait','--for=delete','pod/kafka-0','--timeout=180s')
        self.k('scale','deployment/zookeeper','--replicas=0')
        self.k('wait','--for=delete','pod','-l','app=zookeeper','--timeout=180s')
        for name,path in volumes.items(): self.save(name+'.tgz','sudo','tar','-czf','-','-C',path,'.')
        self.k('exec','redis-0','--','sh','-c','REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli SAVE')
        (self.backup/'redis-key-count.txt').write_bytes(self.k('exec','redis-0','--','sh','-c','REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli DBSIZE'))
        self.save('redis.tgz','kubectl','-n',self.namespace,'exec','redis-0','--','tar','-czf','-','-C','/data','.')
        deployments=json.loads((self.backup/'deployments.json').read_text())['items']
        images=[d['spec']['template']['spec']['containers'][0]['image'] for d in deployments if d['metadata']['name'] in (*APPS,'frontend')]
        images=['docker.io/'+i if i.startswith('dete/') else i for i in images]
        self.run('sudo','k3s','ctr','images','export',str(self.backup/'images.tar'),*images,timeout=900)
        self.run('sudo','chown',str(os.getuid())+':'+str(os.getgid()),str(self.backup/'images.tar'))
        self.k('scale','deployment/zookeeper','--replicas=1')
        self.k('rollout','status','deployment/zookeeper','--timeout=180s')
        self.k('scale','statefulset/kafka','--replicas=1')
        self.k('rollout','status','statefulset/kafka','--timeout=180s')
        checksums={p.name:file_sha256(p) for p in self.backup.iterdir() if p.is_file()}
        (self.backup/'sha256.json').write_text(json.dumps(checksums,indent=2))
        print('Consistent backup sealed at '+str(self.backup)+'. Application writers remain stopped.',flush=True)
    def verify_backup(self):
        validate_manifest(json.loads((self.backup/'reset-manifest.json').read_text()))
        for name,digest in json.loads((self.backup/'sha256.json').read_text()).items():
            if Path(name).name!=name: raise ValueError('Unsafe backup entry')
            if file_sha256(self.backup/name)!=digest: raise RuntimeError('Backup checksum mismatch: '+name)
    def migrate(self,root):
        self.stopped(); self.verify_backup()
        libs=self.backup/'migration-libs'; libs.mkdir(exist_ok=True)
        jar=next(p for p in (root/'services/simulator/build/libs').glob('*.jar') if 'plain' not in p.name)
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'): archive.extract(name,libs)
        secrets=self.json('get','secret','dete-secrets')['data']
        ip=self.json('get','service','postgres')['spec']['clusterIP']
        env=dict(os.environ,RESET_DB_URL='jdbc:postgresql://'+ip+':5432/dete',RESET_DB_USER=base64.b64decode(secrets['POSTGRES_USER']).decode(),RESET_DB_PASSWORD=base64.b64decode(secrets['POSTGRES_PASSWORD']).decode())
        with (self.backup/'migration.log').open('w') as log:
            subprocess.run(['java','-cp',str(libs/'BOOT-INF/lib/*'),str(root/'scripts/MigrateResetSchema.java'),str(root)],env=env,stdout=log,stderr=log,check=True)
    def reset(self,epoch):
        self.stopped(); self.verify_backup()
        proof=json.loads((self.backup/'restore-rehearsal.json').read_text())
        digest=hashlib.sha256((self.backup/'sha256.json').read_bytes()).hexdigest()
        if proof.get('backup_manifest_sha256')!=digest or not proof.get('database') or not proof.get('kafka') or not proof.get('redis'): raise RuntimeError('Verified restore rehearsal required')
        existing=set(self.kafka('kafka-topics','--bootstrap-server','localhost:9092','--list').splitlines())
        groups=set(self.kafka('kafka-consumer-groups','--bootstrap-server','localhost:9092','--list').splitlines())
        for group in MANIFEST['groups']:
            if group in groups: self.kafka('kafka-consumer-groups','--bootstrap-server','localhost:9092','--delete','--group',group)
        for topic,partitions in MANIFEST['topics'].items():
            if topic in existing:
                self.kafka('kafka-topics','--bootstrap-server','localhost:9092','--delete','--topic',re.escape(topic))
                for attempt in range(60):
                    if topic not in self.kafka('kafka-topics','--bootstrap-server','localhost:9092','--list').splitlines(): break
                    time.sleep(1)
                else: raise RuntimeError('Topic deletion did not complete: '+topic)
            self.kafka('kafka-topics','--bootstrap-server','localhost:9092','--create','--topic',topic,'--partitions',str(partitions),'--replication-factor','1')
        self.kafka('kafka-configs','--bootstrap-server','localhost:9092','--entity-type','topics','--entity-name','order.commands','--alter','--add-config','retention.ms=-1,retention.bytes=-1,cleanup.policy=delete')
        self.query(reset_sql(epoch))
        # Fixed allowlisted Lua script, preserving authentication/rate-limit keys.
        lua="local c='0'; repeat local r=redis.call('SCAN',c,'MATCH','idempotency:order:*','COUNT',500); c=r[1]; for _,k in ipairs(r[2]) do redis.call('DEL',k) end until c=='0'; return 1"
        self.k('exec','redis-0','--','sh','-c','REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli EVAL "$1" 0','reset',lua)
        (self.backup/'reset-complete.json').write_text(json.dumps({'epoch':str(uuid.UUID(epoch)),'clock':self.query('SELECT row_to_json(c) FROM simulator.market_clock c;')}))
        print('Reset complete. New clock remains in maintenance; application replicas remain zero.',flush=True)

def main():
    os.umask(0o077)
    parser=argparse.ArgumentParser()
    parser.add_argument('--namespace',required=True)
    parser.add_argument('--backup',type=Path,required=True)
    parser.add_argument('--step',choices=['inventory','backup','migrate','reset'],default='inventory')
    parser.add_argument('--epoch')
    parser.add_argument('--execute',action='store_true')
    args=parser.parse_args()
    op=Operator(args.namespace,args.backup.resolve())
    if not args.execute or args.step=='inventory': op.inventory(); return
    if args.step=='backup': op.backup_all()
    elif args.step=='migrate': op.migrate(Path(__file__).resolve().parents[1])
    elif args.step=='reset':
        if not args.epoch: parser.error('--epoch is required')
        op.reset(args.epoch)

if __name__=='__main__': main()
