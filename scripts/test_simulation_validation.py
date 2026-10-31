import unittest
from verify_simulation_clock import expected_candles, compare_candles

class CandleChecks(unittest.TestCase):
    def test_same_time_sequence_and_exact_ohlcv(self):
        trades=[{'elapsed_ms':200,'sequence_number':2,'price':110,'quantity':3}, {'elapsed_ms':100,'sequence_number':1,'price':100,'quantity':2}, {'elapsed_ms':200,'sequence_number':3,'price':105,'quantity':4}]
        expected=expected_candles(trades,123,60000)
        self.assertEqual([{'openTime':123,'open':100,'high':110,'low':100,'close':105,'volume':9,'tradeCount':3}],expected)
        compare_candles(expected,expected)
        with self.assertRaises(AssertionError): compare_candles(expected,[dict(expected[0],volume=10)])

if __name__=='__main__': unittest.main()
