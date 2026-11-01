import unittest
from unittest.mock import call as mock_call, patch

from smoke_test import new_account, select_price, verify_deltas, verify_simulator_api


class SmokeAssertionsTest(unittest.TestCase):
    @patch('smoke_test.time.sleep')
    @patch('smoke_test.call')
    def test_smoke_recovers_stopping_session_and_retries_busy_cleanup(self, request, sleep):
        from smoke_test import start_smoke_session, HTTPStatusError
        started={'status':'ACTIVE','clock':{'tradingAllowed':True,'sessionId':'new-session'}}
        attempts=0
        def endpoint(path,*args,**kwargs):
            nonlocal attempts
            if path.endswith('/status'):
                if request.call_count>4: raise AssertionError('Waited instead of recovering expired session')
                return {'status':'STOPPING'}
            attempts+=1
            if attempts==1: raise HTTPStatusError('POST',path,409,(202,))
            return started
        request.side_effect=endpoint
        self.assertEqual(started,start_smoke_session('buyer-token'))

    @patch('smoke_test.call')
    def test_account_creation_recovers_when_registration_commits_before_503(self, request):
        request.side_effect = [
            RuntimeError('POST /auth/register: HTTP 503'),
            {'userId': 'account-id', 'accessToken': 'buyer-token'},
        ]

        self.assertEqual(('account-id', 'buyer-token'), new_account('buyer'))
        self.assertEqual(2, request.call_count)
        self.assertEqual('/auth/register', request.call_args_list[0].args[0])
        self.assertEqual('/auth/login', request.call_args_list[1].args[0])

    @patch('smoke_test.call')
    def test_simulator_status_requires_auth_and_uses_session_route(self, request):
        request.side_effect = [None, {'status': 'IDLE'}]

        verify_simulator_api('buyer-token')

        self.assertEqual(
            [
                mock_call('/market-simulation/status', expected=(401,)),
                mock_call('/market-simulation/status', token='buyer-token'),
            ],
            request.call_args_list,
        )

    @patch('smoke_test.call')
    def test_simulator_status_rejects_unknown_response(self, request):
        request.side_effect = [None, {'status': 'running'}]

        with self.assertRaisesRegex(
                RuntimeError, 'Invalid authenticated market simulation status'
        ):
            verify_simulator_api('buyer-token')

    def test_success_requires_both_sides_and_reservation_release(self):
        buyer = {"USD": (1000, 0), "BTC": (0, 0)}
        seller = {"USD": (0, 0), "BTC": (10, 0)}
        after_buyer = {"USD": (900, 0), "BTC": (10, 0)}
        after_seller = {"USD": (100, 0), "BTC": (0, 0)}
        verify_deltas(buyer, after_buyer, seller, after_seller, 10, 100)
        with self.assertRaises(RuntimeError):
            verify_deltas(buyer, {"USD": (890, 10), "BTC": (10, 0)}, seller, after_seller, 10, 100)
        with self.assertRaises(RuntimeError):
            verify_deltas(buyer, after_buyer, seller, seller, 10, 100)

    def test_independent_ioc_fill_deltas_release_all_reservations(self):
        from smoke_test import verify_execution_delta
        before={'USD':(1000,0),'BTC':(0,0)}
        rows=[{'price':100000000,'quantity':100}]
        verify_execution_delta(before,{'USD':(900,0),'BTC':(100,0)},rows,'BUY')
        with self.assertRaises(RuntimeError):
            verify_execution_delta(before,{'USD':(900,10),'BTC':(100,0)},rows,'BUY')

    def test_smoke_price_does_not_cross_existing_orders(self):
        self.assertEqual(105, select_price({"bids": [{"price": 100}], "asks": [{"price": 110}]}))
        with self.assertRaises(RuntimeError):
            select_price({"bids": [{"price": 100}], "asks": [{"price": 101}]})


if __name__ == "__main__":
    unittest.main()
