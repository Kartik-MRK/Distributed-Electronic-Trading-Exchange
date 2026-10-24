import unittest
from smoke_test import select_price, verify_deltas


class SmokeAssertionsTest(unittest.TestCase):
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
