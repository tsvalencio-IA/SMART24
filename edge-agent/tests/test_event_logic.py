from __future__ import annotations

import sys
import unittest
from pathlib import Path

EDGE_DIR = Path(__file__).resolve().parents[1]
if str(EDGE_DIR) not in sys.path:
    sys.path.insert(0, str(EDGE_DIR))

from camera_probe import mask_stream_url
from event_logic import reconcile_items


class ReconciliationTests(unittest.TestCase):
    def test_regular_purchase(self) -> None:
        events = [
            {"type": "PRODUCT_PICKUP", "sku": "A", "quantity": 10, "confidence": 0.95},
            {"type": "PRODUCT_RETURN", "sku": "A", "quantity": 1, "confidence": 0.92},
            {"type": "CHECKOUT_ITEM_REGISTERED", "sku": "A", "quantity": 9, "confidence": 1.0},
            {"type": "PAYMENT_APPROVED", "sku": "A", "quantity": 9, "confidence": 1.0},
        ]
        result = reconcile_items(events)[0]
        self.assertEqual(result["expected"], 9)
        self.assertEqual(result["difference"], 0)
        self.assertFalse(result["reviewRequired"])

    def test_same_total_different_skus_is_detected(self) -> None:
        events = [
            {"type": "PRODUCT_PICKUP", "sku": "A", "quantity": 2},
            {"type": "PAYMENT_APPROVED", "sku": "A", "quantity": 1},
            {"type": "PRODUCT_PICKUP", "sku": "B", "quantity": 1},
            {"type": "PAYMENT_APPROVED", "sku": "B", "quantity": 2},
        ]
        results = {item["sku"]: item for item in reconcile_items(events)}
        self.assertEqual(results["A"]["difference"], 1)
        self.assertEqual(results["B"]["difference"], -1)
        self.assertTrue(results["A"]["reviewRequired"])
        self.assertTrue(results["B"]["reviewRequired"])

    def test_ambiguous_event_never_invents_quantity(self) -> None:
        events = [
            {"type": "AMBIGUOUS_INTERACTION", "sku": "A", "confidence": 0.3},
            {"type": "PAYMENT_APPROVED", "sku": "A", "quantity": 1},
        ]
        result = reconcile_items(events)[0]
        self.assertIsNone(result["expected"])
        self.assertIsNone(result["difference"])
        self.assertTrue(result["reviewRequired"])


class SecurityTests(unittest.TestCase):
    def test_stream_password_is_masked(self) -> None:
        masked = mask_stream_url("rtsp://admin:secret@192.168.1.10:554/live")
        self.assertNotIn("admin", masked)
        self.assertNotIn("secret", masked)
        self.assertIn("***:***", masked)
        self.assertIn("192.168.1.10", masked)


if __name__ == "__main__":
    unittest.main()
