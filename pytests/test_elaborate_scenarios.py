import requests
import pytest
import uuid
import random
import string
from decimal import Decimal

BASE_URL = "http://localhost:8080"

def random_string(length=8):
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))

class ChipInClient:
    def __init__(self, email=None, password="password123"):
        self.email = email or f"test_{random_string()}@example.com"
        self.password = password
        self.name = f"User_{random_string(4)}"
        self.token = None
        self.user_id = None

    def signup(self):
        payload = {
            "name": self.name,
            "email": self.email,
            "password": self.password,
            "phone": "1234567890"
        }
        res = requests.post(f"{BASE_URL}/api/users/register", json=payload)
        assert res.status_code == 200 or res.status_code == 201, f"Signup failed: {res.text}"
        return res.json()

    def login(self):
        payload = {
            "email": self.email,
            "password": self.password
        }
        res = requests.post(f"{BASE_URL}/auth/login", json=payload)
        assert res.status_code == 200, f"Login failed: {res.text}"
        data = res.json()
        self.token = data["token"]
        self.user_id = data["userId"]
        return data

    @property
    def headers(self):
        return {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }

@pytest.fixture
def users():
    # Create 3 fresh users for each test to ensure isolation
    clients = [ChipInClient() for _ in range(3)]
    for c in clients:
        c.signup()
        c.login()
    return clients

@pytest.fixture
def currency_id(users):
    # Get a global currency ID (INR)
    res = requests.get(f"{BASE_URL}/api/currencies", headers=users[0].headers)
    assert res.status_code == 200
    # Find INR or just take the first one
    for curr in res.json():
        if curr["code"] == "INR":
            return curr["currencyId"]
    return res.json()[0]["currencyId"]

def test_full_lifecycle_and_simplification(users, currency_id):
    u1, u2, u3 = users
    
    # 1. CREATE GROUP
    group_payload = {
        "name": f"Trip_{random_string(4)}",
        "description": "Elaborate Testing Group",
        "type": "TRIP",
        "simplifyDebt": True,
        "defaultCurrencyId": currency_id
    }
    res = requests.post(f"{BASE_URL}/api/groups", json=group_payload, headers=u1.headers)
    assert res.status_code == 200
    group_id = res.json()["groupId"]

    # 2. ADD MEMBERS
    for user in [u2, u3]:
        payload = {"email": user.email, "isAdmin": False}
        res = requests.post(f"{BASE_URL}/api/groups/{group_id}/members", json=payload, headers=u1.headers)
        assert res.status_code == 200

    # 3. ADD EXPENSE 1: U1 pays 300 for everyone (EQUAL)
    # Expected: U1 lent 200, U2 owes 100, U3 owes 100
    expense_payload = {
        "description": "Dinner",
        "amount": 300.00,
        "currencyId": currency_id,
        "splitType": "EQUAL",
        "type": "FOOD",
        "payers": [{"userId": u1.user_id, "paidAmount": 300.00}],
        "splits": [
            {"userId": u1.user_id, "amountOwed": 100.00},
            {"userId": u2.user_id, "amountOwed": 100.00},
            {"userId": u3.user_id, "amountOwed": 100.00}
        ]
    }
    res = requests.post(f"{BASE_URL}/api/groups/{group_id}/expenses", json=expense_payload, headers=u1.headers)
    assert res.status_code == 200

    # 4. ADD EXPENSE 2: U2 pays 150 for U1 (UNEQUAL)
    # Expected: U2 lent 150 to U1.
    # Total state so far:
    # U1: +200 (lent) - 150 (owed to U2) = +50
    # U2: -100 (owed to U1) + 150 (lent to U1) = +50
    # U3: -100 (owed to U1)
    expense_payload = {
        "description": "Taxi",
        "amount": 150.00,
        "currencyId": currency_id,
        "splitType": "EXACT",
        "type": "TRANSPORTATION",
        "payers": [{"userId": u2.user_id, "paidAmount": 150.00}],
        "splits": [
            {"userId": u1.user_id, "amountOwed": 150.00}
        ]
    }
    res = requests.post(f"{BASE_URL}/api/groups/{group_id}/expenses", json=expense_payload, headers=u2.headers)
    assert res.status_code == 200

    # 5. VERIFY DASHBOARD AND SIMPLIFICATION
    res = requests.get(f"{BASE_URL}/api/groups/{group_id}/dashboard", headers=u1.headers)
    assert res.status_code == 200
    data = res.json()
    
    balances = {b["userId"]: float(b["netBalance"]) for b in data["userBalances"]}
    assert balances[u1.user_id] == 50.0
    assert balances[u2.user_id] == 50.0
    assert balances[u3.user_id] == -100.0

    # Zero-sum check
    assert sum(balances.values()) == 0.0

    # Simplification check:
    # Instead of U3 paying U1 ($100) and U1 paying U2 ($50), 
    # the algorithm should suggest U3 -> U1 ($50) and U3 -> U2 ($50)
    # OR U3 -> U2 ($100) and U2 -> U1 ($50)... wait, net is 50, 50, -100.
    # The greedy matcher will do:
    # U3 owes 100. U1 needs 50, U2 needs 50.
    # Suggestion 1: U3 pays U1 $50.
    # Suggestion 2: U3 pays U2 $50.
    settlements = data["settlements"]
    assert len(settlements) == 2
    for s in settlements:
        assert s["payerId"] == u3.user_id
        assert float(s["amount"]) == 50.0

def test_settle_up_flow(users, currency_id):
    u1, u2, _ = users
    
    # 1. Create group and add U2
    group_payload = {"name": "SettleGroup", "defaultCurrencyId": currency_id}
    res = requests.post(f"{BASE_URL}/api/groups", json=group_payload, headers=u1.headers)
    group_id = res.json()["groupId"]
    requests.post(f"{BASE_URL}/api/groups/{group_id}/members", json={"email": u2.email}, headers=u1.headers)

    # 2. U1 pays 100 for U2
    expense_payload = {
        "description": "Loan",
        "amount": 100.0,
        "currencyId": currency_id,
        "splitType": "EXACT",
        "payers": [{"userId": u1.user_id, "paidAmount": 100.0}],
        "splits": [{"userId": u2.user_id, "amountOwed": 100.0}]
    }
    requests.post(f"{BASE_URL}/api/groups/{group_id}/expenses", json=expense_payload, headers=u1.headers)

    # 3. U2 SETTLES UP (Pays back U1)
    settle_payload = {
        "groupId": group_id,
        "payerId": u2.user_id,
        "payeeId": u1.user_id,
        "amount": 100.0,
        "currencyId": currency_id,
        "notes": "Paying back for dinner"
    }
    res = requests.post(f"{BASE_URL}/api/settlements", json=settle_payload, headers=u2.headers)
    assert res.status_code == 200

    # 4. Verify balance is now 0
    res = requests.get(f"{BASE_URL}/api/groups/{group_id}/dashboard", headers=u1.headers)
    data = res.json()
    for b in data["userBalances"]:
        assert float(b["netBalance"]) == 0.0
    assert len(data["settlements"]) == 0

def test_rounding_integrity(users, currency_id):
    u1, u2, u3 = users
    group_id = requests.post(f"{BASE_URL}/api/groups", json={"name": "Rounding", "defaultCurrencyId": currency_id}, headers=u1.headers).json()["groupId"]
    requests.post(f"{BASE_URL}/api/groups/{group_id}/members", json={"email": u2.email}, headers=u1.headers)
    requests.post(f"{BASE_URL}/api/groups/{group_id}/members", json={"email": u3.email}, headers=u1.headers)

    # Split 10.00 among 3 people
    expense_payload = {
        "description": "Beer",
        "amount": 10.00,
        "currencyId": currency_id,
        "splitType": "EQUAL",
        "payers": [{"userId": u1.user_id, "paidAmount": 10.00}],
        "splits": [
            {"userId": u1.user_id, "amountOwed": 3.33},
            {"userId": u2.user_id, "amountOwed": 3.33},
            {"userId": u3.user_id, "amountOwed": 3.34}
        ]
    }
    requests.post(f"{BASE_URL}/api/groups/{group_id}/expenses", json=expense_payload, headers=u1.headers)

    res = requests.get(f"{BASE_URL}/api/groups/{group_id}/dashboard", headers=u1.headers)
    data = res.json()
    total_net = sum(Decimal(str(b["netBalance"])) for b in data["userBalances"])
    assert total_net == Decimal("0.00"), f"Financial leak: {total_net}"
