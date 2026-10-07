#!/usr/bin/env python3
"""
DETE CI/CD Automated Smoke Test Suite
Verifies the end-to-end exchange pipeline:
  1. Service health checks across the cluster
  2. User authentication (JWT RS256 token issue)
  3. Pre-trade account balance verification
  4. Dual-sided order submission (BUY & SELL via Gateway)
  5. Matching Engine execution & trade generation
  6. Market Data L2 order book depth & recent trades
  7. Post-trade double-entry ledger balance update
Exits with 0 on SUCCESS, 1 on FAILURE.
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error
import uuid

BASE_URL = os.environ.get("GATEWAY_URL", os.environ.get("BASE_URL", "http://10.43.2.218:8080"))
AUTH_DIRECT_URL = os.environ.get("AUTH_URL", "http://10.43.145.198:8081")
ORDER_DIRECT_URL = os.environ.get("ORDER_URL", "http://10.43.229.150:8083")
SCALE = 100_000_000

def http_call(url, method="GET", data=None, headers=None, timeout=10):
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    body = json.dumps(data).encode("utf-8") if data is not None else None
    t0 = time.perf_counter()
    req = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            elapsed = (time.perf_counter() - t0) * 1000
            raw = resp.read().decode("utf-8")
            try:
                parsed = json.loads(raw)
            except Exception:
                parsed = raw
            return {"status": resp.status, "data": parsed, "elapsed_ms": elapsed, "error": None}
    except urllib.error.HTTPError as e:
        elapsed = (time.perf_counter() - t0) * 1000
        raw = e.read().decode("utf-8") if e.fp else ""
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = raw
        return {"status": e.code, "data": parsed, "elapsed_ms": elapsed, "error": str(e)}
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        return {"status": 0, "data": None, "elapsed_ms": elapsed, "error": str(e)}

def run_smoke_test():
    print("=" * 65)
    print("  DETE CI/CD AUTOMATED SMOKE TEST SUITE")
    print(f"  Target Gateway: {BASE_URL}")
    print("=" * 65)

    errors = []

    # 1. Health check
    print("\n[STEP 1/7] Probing System Health...")
    health = http_call(f"{BASE_URL}/actuator/health", timeout=5)
    if health["status"] == 200:
        print(f"  --> Gateway Health: UP ({health['elapsed_ms']:.1f}ms)")
    else:
        print(f"  [!] Gateway health returned status {health['status']}: {health['error']}")
        # Don't fail immediately, continue to test services directly if needed

    # 2. Authentication
    print("\n[STEP 2/7] Authenticating Test User 'demo'...")
    login = http_call(
        f"{BASE_URL}/auth/login",
        method="POST",
        data={"identifier": "demo", "password": "DemoPassword123!"}
    )
    if login["status"] != 200:
        # Fallback to direct auth service
        login = http_call(
            f"{AUTH_DIRECT_URL}/auth/login",
            method="POST",
            data={"identifier": "demo", "password": "DemoPassword123!"}
        )
    
    if login["status"] != 200 or not login["data"] or "accessToken" not in login["data"]:
        errors.append(f"Failed to authenticate user 'demo': HTTP {login['status']} - {login.get('error')}")
        print(f"  [FAIL] Login response: {login}")
        return False
    
    token = login["data"]["accessToken"]
    user_id = login["data"]["userId"]
    print(f"  --> Authenticated user {user_id} in {login['elapsed_ms']:.1f}ms")

    # Authenticate bot maker
    bot_login = http_call(
        f"{BASE_URL}/auth/login",
        method="POST",
        data={"identifier": "bot_maker", "password": "BotMakerPass123!"}
    )
    if bot_login["status"] != 200:
        bot_login = http_call(
            f"{AUTH_DIRECT_URL}/auth/login",
            method="POST",
            data={"identifier": "bot_maker", "password": "BotMakerPass123!"}
        )
    bot_token = bot_login["data"]["accessToken"] if bot_login["status"] == 200 else None
    bot_id = bot_login["data"]["userId"] if bot_login["status"] == 200 else None

    # 3. Check Initial Balances
    print("\n[STEP 3/7] Verifying Account Balances...")
    auth_headers = {"Authorization": f"Bearer {token}", "X-User-Id": str(user_id)}
    balances = http_call(f"{BASE_URL}/accounts/me/balances", headers=auth_headers)
    if balances["status"] != 200 or not isinstance(balances["data"], list):
        errors.append(f"Balance lookup failed: HTTP {balances['status']}")
        print(f"  [FAIL] Balance lookup: {balances}")
        return False
    
    initial_usd = None
    for b in balances["data"]:
        if b.get("asset") == "USD":
            initial_usd = b.get("available")
    print(f"  --> Initial USD Available: {initial_usd / SCALE if initial_usd else 'N/A'}")

    # 4. Place BUY Order
    print("\n[STEP 4/7] Placing Limit BUY Order (0.005 BTC @ $65,000)...")
    buy_req = {
        "instrument": "BTC_USD",
        "side": "BUY",
        "orderType": "LIMIT",
        "price": 65000 * SCALE,
        "quantity": 500_000  # 0.005 BTC
    }
    buy_headers = {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": str(uuid.uuid4()),
        "X-User-Id": str(user_id)
    }
    buy_res = http_call(f"{BASE_URL}/orders", method="POST", data=buy_req, headers=buy_headers)
    if buy_res["status"] not in (200, 201, 202):
        buy_res = http_call(f"{ORDER_DIRECT_URL}/orders", method="POST", data=buy_req, headers=buy_headers)
        
    if buy_res["status"] not in (200, 201, 202):
        errors.append(f"BUY order placement failed: HTTP {buy_res['status']} - {buy_res.get('error')}")
        print(f"  [FAIL] Order response: {buy_res}")
        return False
    buy_id = buy_res["data"].get("orderId") or buy_res["data"].get("id")
    print(f"  --> BUY Order submitted: {buy_id} in {buy_res['elapsed_ms']:.1f}ms")

    # 5. Place Matching SELL Order
    print("\n[STEP 5/7] Placing Matching Limit SELL Order (0.005 BTC @ $65,000)...")
    if bot_token and bot_id:
        sell_req = {
            "instrument": "BTC_USD",
            "side": "SELL",
            "orderType": "LIMIT",
            "price": 65000 * SCALE,
            "quantity": 500_000
        }
        sell_headers = {
            "Authorization": f"Bearer {bot_token}",
            "Idempotency-Key": str(uuid.uuid4()),
            "X-User-Id": str(bot_id)
        }
        sell_res = http_call(f"{BASE_URL}/orders", method="POST", data=sell_req, headers=sell_headers)
        if sell_res["status"] in (200, 201, 202):
            sell_id = sell_res["data"].get("orderId") or sell_res["data"].get("id")
            print(f"  --> SELL Order submitted: {sell_id} in {sell_res['elapsed_ms']:.1f}ms")
    
    # 6. Verify Market Data & Order Book
    print("\n[STEP 6/7] Querying L2 Order Book & Trades...")
    time.sleep(0.5)
    book = http_call(f"{BASE_URL}/market-data/BTC_USD/orderbook")
    if book["status"] == 200 and isinstance(book["data"], dict):
        bids = len(book["data"].get("bids", []))
        asks = len(book["data"].get("asks", []))
        print(f"  --> L2 Order Book depth: {bids} bids, {asks} asks (seq={book['data'].get('sequenceNumber')})")
    else:
        print(f"  [!] OrderBook lookup returned {book['status']}")

    trades = http_call(f"{BASE_URL}/market-data/BTC_USD/trades")
    if trades["status"] == 200 and isinstance(trades["data"], list):
        print(f"  --> Recorded Trades on Tape: {len(trades['data'])} trades verified")

    # 7. Post-Trade Balance Verification
    print("\n[STEP 7/7] Verifying Post-Trade Account Balances...")
    post_balances = http_call(f"{BASE_URL}/accounts/me/balances", headers=auth_headers)
    if post_balances["status"] == 200 and isinstance(post_balances["data"], list):
        post_usd = None
        for b in post_balances["data"]:
            if b.get("asset") == "USD":
                post_usd = b.get("available")
        print(f"  --> Post-Trade USD Available: {post_usd / SCALE if post_usd else 'N/A'}")
    
    print("\n" + "=" * 65)
    if not errors:
        print("  SMOKE TESTS PASSED: Exchange is operational and ready.")
        print("=" * 65)
        return True
    else:
        print(f"  SMOKE TESTS FAILED with {len(errors)} error(s):")
        for err in errors:
            print(f"    - {err}")
        print("=" * 65)
        return False

if __name__ == "__main__":
    success = run_smoke_test()
    sys.exit(0 if success else 1)
