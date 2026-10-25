import unittest
from unittest.mock import call as mock_call, patch

from smoke_test import select_price, verify_deltas, verify_simulator_api


class SmokeAssertionsTest(unittest.TestCase):
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

    def test_smoke_price_does_not_cross_existing_orders(self):
        self.assertEqual(105, select_price({"bids": [{"price": 100}], "asks": [{"price": 110}]}))
        with self.assertRaises(RuntimeError):
            select_price({"bids": [{"price": 100}], "asks": [{"price": 101}]})


if __name__ == "__main__":
    unittest.main()
