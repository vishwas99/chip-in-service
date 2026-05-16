import os

import pytest
import requests
from decimal import Decimal

# Pytests are intentionally NOT run inside the JVM test phase — they hit a
# locally-running ChipIn instance. All connection knobs come from the
# environment so credentials never live in the repo.
#
#   export CHIPIN_BASE_URL=http://localhost:8080
#   export CHIPIN_TEST_USERS='[{"email":"alice@example.com","password":"..."}]'
#   pytest pytests/

BASE_URL = os.environ.get("CHIPIN_BASE_URL", "http://localhost:8080")

_USERS_RAW = os.environ.get("CHIPIN_TEST_USERS")
if not _USERS_RAW:
    pytest.skip(
        "CHIPIN_TEST_USERS env var not set; skipping end-to-end pytests. "
        "Set it to a JSON list of {email,password} objects to run.",
        allow_module_level=True,
    )

try:
    import json
    USERS = json.loads(_USERS_RAW)
    assert isinstance(USERS, list) and len(USERS) >= 3
    for u in USERS:
        assert "email" in u and "password" in u
except Exception as exc:  # pragma: no cover - misconfiguration path
    pytest.skip(f"CHIPIN_TEST_USERS is malformed: {exc}", allow_module_level=True)

class ChipInTestContext:
    def __init__(self):
        self.tokens = {}
        self.user_ids = {}
        self.currency_id = None
        self.group_id = None

    def login_users(self):
        for credentials in USERS:
            res = requests.post(f"{BASE_URL}/auth/login", json=credentials)
            if res.status_code == 200:
                # Based on contract output schema
                self.tokens[credentials["email"]] = res.json()["token"]

                # Fetch profile to secure the UUIDs safely
                headers = {"Authorization": f"Bearer {res.json()['token']}"}
                profile = requests.get(f"{BASE_URL}/api/users/me", headers=headers).json()
                self.user_ids[credentials["email"]] = profile["userid"]
            else:
                pytest.fail(f"Setup failure: Login failed for {credentials['email']}: {res.text}")

    def headers(self, email):
        return {
            "Authorization": f"Bearer {self.tokens[email]}",
            "Content-Type": "application/json"
        }

@pytest.fixture(scope="module")
def ctx():
    context = ChipInTestContext()
    context.login_users()

    # Grab the active global currency to feed Group Creation
    admin_headers = context.headers(USERS[0]["email"])
    curr_res = requests.get(f"{BASE_URL}/api/currencies", headers=admin_headers)

    if curr_res.status_code != 200 or not curr_res.json():
        pytest.fail("Pre-condition failed: Ensure global currencies are seeded in the DB.")

    context.currency_id = curr_res.json()[0]["currencyId"]
    return context


def test_double_ledger_integrity(ctx):
    admin = USERS[0]["email"]
    u2 = USERS[1]["email"]
    u3 = USERS[2]["email"]

    admin_headers = ctx.headers(admin)

    # 1. CREATE GROUP
    group_payload = {
        "name": "E2E Ledger Trip",
        "description": "Validating double-ledger calculations",
        "type": "TRIP",
        "simplifyDebt": True,
        "defaultCurrencyId": ctx.currency_id
    }
    group_res = requests.post(f"{BASE_URL}/api/groups", json=group_payload, headers=admin_headers)
    assert group_res.status_code == 200, f"Failed Group Creation: {group_res.text}"
    ctx.group_id = group_res.json()["groupId"]

    # 2. ADD COMPANIONS TO GROUP
    for email in [u2, u3]:
        payload = {"email": email, "isAdmin": False}
        res = requests.post(f"{BASE_URL}/api/groups/{ctx.group_id}/members", json=payload, headers=admin_headers)
        assert res.status_code == 200, f"Failed adding member {email}: {res.text}"

    # 3. LOG AN EXPENSE OF 100 SPLIT AMONG 3 USERS
    # Double-Ledger Mapping: Payer table gets 100 entry. Splits get 33.33, 33.33, 33.34 entries.
    expense_payload = {
        "description": "Validation Dinner",
        "amount": 100.00,
        "currencyId": ctx.currency_id,
        "splitType": "EQUAL",
        "type": "FOOD",
        "payers": [{"userId": ctx.user_ids[admin], "paidAmount": 100.00}],
        "splits": [
            {"userId": ctx.user_ids[admin], "amountOwed": 33.33},
            {"userId": ctx.user_ids[u2], "amountOwed": 33.33},
            {"userId": ctx.user_ids[u3], "amountOwed": 33.34}
        ]
    }
    exp_res = requests.post(f"{BASE_URL}/api/groups/{ctx.group_id}/expenses", json=expense_payload, headers=admin_headers)
    assert exp_res.status_code == 200, f"Failed registering expense: {exp_res.text}"

    # 4. GRANULAR LEDGER AUDIT
    balance_res = requests.get(f"{BASE_URL}/api/groups/{ctx.group_id}/balances", headers=admin_headers)
    balances = balance_res.json()["userBalances"]

    print("\n--- Current Double Ledger State ---")
    for b in balances:
        print(f"User: {b['userName']} | Net Balance: {b['netBalance']}")

    # Verification 1: Zero-Sum Check (Total ledger balance must equal exactly 0)
    net_sum = sum(Decimal(str(b["netBalance"])) for b in balances)
    assert net_sum == Decimal("0.00"), f"Financial Leak Detected! Ledger out of balance by: {net_sum}"

    # Verification 2: Specific Payer Credit Audit
    admin_balance = next(Decimal(str(b["netBalance"])) for b in balances if b["userId"] == ctx.user_ids[admin])
    assert admin_balance == Decimal("66.67"), f"Payer was not credited properly! Expected +66.67, got {admin_balance}"