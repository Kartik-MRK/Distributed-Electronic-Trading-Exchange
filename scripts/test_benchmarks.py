import tempfile
import unittest
from unittest.mock import patch
import run_benchmarks as benchmark


class BenchmarkSessionTests(unittest.TestCase):
    def exchange(self, active):
        self.orders = []
        def call(url, method='GET', data=None, **kwargs):
            if url.endswith('/auth/login'):
                payload = {'accessToken': 'test', 'userId': 'test'}
            elif url.endswith('/market-simulation/status'):
                payload = {'clock': {'tradingAllowed': active, 'sessionId': 'session-a', 'remainingMs': 60000}}
            elif url.endswith('/price'):
                payload = {'lastTradedPrice': 90000 * benchmark.SCALE}
            elif url.endswith('/orders'):
                self.orders.append(data)
                return {'status': 202, 'data': {}, 'elapsed_ms': 10}
            else:
                raise AssertionError(url)
            return {'status': 200, 'data': payload, 'elapsed_ms': 1}
        return call

    def test_paused_benchmark_skips_without_orders(self):
        with patch.object(benchmark, 'http_call', side_effect=self.exchange(False)):
            result = benchmark.run_benchmark(pairs=1)
        self.assertIn('skipped', result)
        self.assertEqual([], self.orders)

    def test_active_benchmark_uses_session_and_current_price(self):
        with patch.object(benchmark, 'http_call', side_effect=self.exchange(True)), patch.object(benchmark.time, 'sleep'):
            result = benchmark.run_benchmark(pairs=1)
        self.assertEqual(2, result['success_count'])
        self.assertTrue(all(o.get('sessionId') == 'session-a' for o in self.orders))
        self.assertTrue(all(o['price'] == 90000 * benchmark.SCALE for o in self.orders))

    def test_partial_success_is_not_a_performance_pass(self):
        metrics = dict(timestamp='test', success_rate_pct=50, p50_latency_ms=1, p95_latency_ms=1, p99_latency_ms=1)
        with tempfile.TemporaryDirectory() as directory, patch.object(benchmark, 'RESULTS_DIR', directory):
            self.assertFalse(benchmark.evaluate_regression(metrics, benchmark.load_baseline()))


if __name__ == '__main__': unittest.main()
