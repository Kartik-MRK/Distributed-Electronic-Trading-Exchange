import unittest
import tempfile
from pathlib import Path
from reset_demo_market import validate_manifest, reset_sql, MANIFEST

class ResetGuards(unittest.TestCase):
    def test_backup_rejects_shadowed_zookeeper_storage_before_stopping_writers(self):
        from reset_demo_market import Operator
        class LegacyStorage(Operator):
            def json(self,*args):
                return {'spec':{'template':{'spec':{'containers':[{'name':'zookeeper','volumeMounts':[{'name':'zookeeper-data','mountPath':'/var/lib/zookeeper'}]}],'volumes':[{'name':'zookeeper-data','persistentVolumeClaim':{'claimName':'zookeeper-data'}}]}}}}
            def save(self,*args): pass
            def k(self,*args,**kwargs): raise AssertionError('Reached cluster mutation before rejecting ephemeral storage')
        with tempfile.TemporaryDirectory() as directory:
            op=LegacyStorage('dete',Path(directory)/'backup')
            with self.assertRaisesRegex(ValueError,'ZooKeeper'): op.backup_all()

    def test_manifest_cannot_reset_auth_or_broker_internals(self):
        validate_manifest(MANIFEST)
        for key,item in [('tables','auth.users'),('topics','__consumer_offsets'),('cache_prefixes','')]:
            changed={k:list(v) if isinstance(v,list) else dict(v) for k,v in MANIFEST.items()}
            if isinstance(changed[key],dict): changed[key][item]=1
            else: changed[key].append(item)
            with self.assertRaises(ValueError): validate_manifest(changed)
    def test_sql_preserves_identities_and_leaves_market_closed(self):
        sql=reset_sql('00000000-0000-0000-0000-000000000001')
        self.assertNotIn('TRUNCATE auth.',sql)
        self.assertNotIn('CASCADE',sql)
        self.assertIn('maintenance',sql)
        self.assertIn('true',sql)
        self.assertIn('account.ledger_entries',sql)

if __name__=='__main__': unittest.main()
