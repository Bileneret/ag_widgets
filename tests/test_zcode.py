"""Tests for ZCode API response parsing and degraded states."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from agwidgets import zcode  # noqa: E402


class ZCodeParsingTests(unittest.TestCase):
    def test_extract_claim_coupon_banner(self):
        payload = {
            "data": {
                "deliveries": [
                    {
                        "resource_position": "banner",
                        "banner": {
                            "title": {"content": "300M tokens"},
                            "buttons": [
                                {
                                    "text": {"content": "Claim"},
                                    "action": {"type": "claim_zcode_plan", "args": {"plan_id": "p1"}},
                                }
                            ],
                        },
                    }
                ]
            }
        }
        self.assertEqual(zcode._extract_claim_coupon(payload), (True, "p1", "Claim"))

    def test_extract_claim_coupon_none(self):
        self.assertEqual(zcode._extract_claim_coupon({"data": {"deliveries": []}}), (False, "", ""))
        self.assertEqual(zcode._extract_claim_coupon({}), (False, "", ""))

    def test_extract_balance_only_active_plans(self):
        payload = {
            "data": {
                "server_time": 2_000,
                "plans": [
                    {"plan_id": "active", "status": "active", "name": "Pro", "ends_at": 3_000},
                    {"plan_id": "old", "status": "expired", "ends_at": 1_000},
                ],
                "balances": [
                    {"plan_id": "active", "total_units": 500, "available_units": 200, "period_end": 3_000},
                    {"plan_id": "old", "total_units": 900, "available_units": 900, "period_end": 1_000},
                ],
            }
        }
        result = zcode._extract_balance(payload)
        self.assertEqual(result["remaining_units"], 200)
        self.assertEqual(result["total_units"], 500)
        self.assertEqual(result["plan_name"], "Pro")
        self.assertEqual(result["ends_at"], "1970-01-01T00:50:00Z")

    def test_extract_balance_used_units_fallback(self):
        payload = {
            "data": {
                "server_time": 1000,
                "plans": [{"plan_id": "p", "status": "active", "ends_at": 2000}],
                "balances": [{"plan_id": "p", "total_units": 100, "used_units": 40}],
            }
        }
        result = zcode._extract_balance(payload)
        self.assertEqual(result["remaining_units"], 60)
        self.assertEqual(result["total_units"], 100)


class ZCodeStateTests(unittest.TestCase):
    @mock.patch.object(zcode, "_http_get_json")
    def test_network_failure_is_not_expired_token(self, http_get):
        http_get.return_value = (0, None)
        result = zcode.query_zcode_api("token", "mid")
        self.assertTrue(result["network_error"])
        self.assertIsNone(result["auth_ok"])

    @mock.patch.object(zcode, "_http_get_json")
    def test_401_is_expired_token_not_network(self, http_get):
        http_get.return_value = (401, {"code": 401})
        result = zcode.query_zcode_api("token", "mid")
        self.assertFalse(result["network_error"])
        self.assertFalse(result["auth_ok"])

    @mock.patch.object(zcode, "_http_get_json")
    def test_partial_failure_keeps_token_valid(self, http_get):
        http_get.side_effect = [(200, {"code": 0, "data": {"deliveries": []}}), (500, None)]
        result = zcode.query_zcode_api("token", "mid")
        self.assertFalse(result["network_error"])
        self.assertTrue(result["auth_ok"])


if __name__ == "__main__":
    unittest.main()
