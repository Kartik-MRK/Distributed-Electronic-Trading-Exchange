import unittest
from reset_demo_market import validate_manifest, reset_sql, MANIFEST

class ResetGuards(unittest.TestCase):
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
