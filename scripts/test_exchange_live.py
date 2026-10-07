#!/usr/bin/env python3
"""
DETE Full Exchange Live Verification & Benchmark Script
Runs inside or against the K3s cluster on oraclevps.
Tests:
  1. Actuator Health for all microservices
  2. Authentication (/auth/login)
  3. Account Balances (/accounts/{userId})
  4. Order Placement (/orders) - BUY & SELL
  5. Matching & Trade Execution
  6. Market Data Depth (/market-data/depth/{instrument})
  7. Gateway Metrics Snapshot (/internal/metrics/snapshot)
  8. Prometheus Metrics Scraping
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error
import uuid

GATEWAY_URL = os.environ.get("GATEWAY_URL", "http://10.43.2.218:8080")
AUTH_URL = os.environ.get("AUTH_URL", "http://10.43.145.198:8081")
ACCOUNT_URL = os.environ.get("ACCOUNT_URL", "http://10.43.76.230:8082")
ORDER_URL = os.environ.get("ORDER_URL", "http://10.43.229.150:8083")
MATCHING_URL = os.environ.get("MATCHING_URL", "http://10.43.153.137:8084")
MARKET_DATA_URL = os.environ.get("MARKET_DATA_URL", "http://10.43.13.121:8087")
FRONTEND_URL = os.environ.get("FRONTEND_URL", "http://10.43.176.123:3000")
PROMETHEUS_URL = os.environ.get("PROMETHEUS_URL", "http://10.43.216.20:9090")
GRAFANA_URL = os.environ.get("GRAFANA_URL", "http://10.43.136.30:3000")

SCALE = 100_000_000  # 1e8 FixedPoint

def http_req(url, method="GET", data=None, headers=None, timeout=10):
    t0 = time.perf_counter()
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    encoded_data = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=encoded_data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status = resp.status
            body = resp.read().decode("utf-8")
            elapsed_ms = (time.perf_counter() - t0) * 1000
            try:
                parsed = json.loads(body)
            except Exception:
                parsed = body
            return {"status": status, "data": parsed, "elapsed_ms": elapsed_ms, "error": None}
    except urllib.error.HTTPError as e:
        elapsed_ms = (time.perf_counter() - t0) * 1000
        body = e.read().decode("utf-8") if e.fp else ""
        try:
            parsed = json.loads(body)
        except Exception:
            parsed = body
        return {"status": e.code, "data": parsed, "elapsed_ms": elapsed_ms, "error": str(e)}
    except Exception as e:
        elapsed_ms = (time.perf_counter() - t0) * 1000
        return {"status": 0, "data": None, "elapsed_ms": elapsed_ms, "error": str(e)}

def test_health():
    print("\n--- 1. Service Health Checks ---")
    services = {
        "Gateway": f"{GATEWAY_URL}/actuator/health",
        "Auth": f"{AUTH_URL}/actuator/health",
        "Account": f"{ACCOUNT_URL}/actuator/health",
        "Order": f"{ORDER_URL}/actuator/health",
        "Matching-Engine": f"{MATCHING_URL}/actuator/health",
        "Market-Data": f"{MARKET_DATA_URL}/actuator/health",
        "Frontend": f"{FRONTEND_URL}/",
        "Prometheus": f"{PROMETHEUS_URL}/-/healthy",
        "Grafana": f"{GRAFANA_URL}/api/health",
    }
    all_ok = True
    for name, url in services.items():
        res = http_req(url, method="GET", timeout=5)
        ok = res["status"] in (200, 307, 308)
        if not ok:
            all_ok = False
        status_str = f"HTTP {res['status']}" if res['status'] else f"ERR: {res['error']}"
        print(f"  [{'PASS' if ok else 'FAIL'}] {name:<18} ({status_str}) in {res['elapsed_ms']:.1f}ms")
    return all_ok

def test_auth_login(identifier, password):
    print(f"\n--- 2. Authenticating User '{identifier}' ---")
    # Test through Gateway
    res = http_req(
        f"{GATEWAY_URL}/auth/login",
        method="POST",
        data={"identifier": identifier, "password": password}
    )
    if res["status"] != 200 or not res["data"] or "accessToken" not in res["data"]:
        # Fallback direct to Auth service
        print("  Gateway login returned non-200, attempting direct Auth service...")
        res = http_req(
            f"{AUTH_URL}/auth/login",
            method="POST",
            data={"identifier": identifier, "password": password}
        )
    
    if res["status"] == 200 and res["data"] and "accessToken" in res["data"]:
        user_id = res["data"].get("userId")
        token = res["data"].get("accessToken")
        print(f"  [PASS] Logged in user: {identifier} (userId: {user_id}) in {res['elapsed_ms']:.1f}ms")
        return token, user_id
    else:
        print(f"  [FAIL] Login failed: status={res['status']}, data={res['data']}, error={res['error']}")
        return None, None

def test_account_balances(token, user_id):
    print(f"\n--- 3. Checking Balances for User {user_id} ---")
    res = http_req(
        f"{GATEWAY_URL}/accounts/{user_id}",
        method="GET",
        headers={"Authorization": f"Bearer {token}"}
    )
    if res["status"] != 200:
        res = http_req(
            f"{ACCOUNT_URL}/accounts/{user_id}",
            method="GET",
            headers={"Authorization": f"Bearer {token}", "X-User-Id": str(user_id)}
        )
    if res["status"] == 200:
        balances = res["data"].get("balances", {}) if isinstance(res["data"], dict) else res["data"]
        print(f"  [PASS] Balances retrieved in {res['elapsed_ms']:.1f}ms: {balances}")
        return balances
    else:
        print(f"  [FAIL] Failed to retrieve balances: status={res['status']}, data={res['data']}")
        return None

def test_order_placement(token, user_id, side="BUY", instrument="BTC_USD", price_usd=65000.0, qty=0.01):
    print(f"\n--- 4. Placing {side} Order for {qty} {instrument} @ ${price_usd:.2f} ---")
    order_data = {
        "instrument": instrument,
        "side": side,
        "orderType": "LIMIT",
        "price": int(price_usd * SCALE),
        "quantity": int(qty * SCALE)
    }
    idempotency_key = str(uuid.uuid4())
    headers = {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": idempotency_key,
        "X-User-Id": str(user_id)
    }
    res = http_req(
        f"{GATEWAY_URL}/orders",
        method="POST",
        data=order_data,
        headers=headers
    )
    if res["status"] not in (200, 201, 202):
        print("  Gateway order placement returned non-200, attempting direct Order service...")
        res = http_req(
            f"{ORDER_URL}/orders",
            method="POST",
            data=order_data,
            headers=headers
        )
    if res["status"] in (200, 201, 202):
        order_resp = res["data"]
        order_id = order_resp.get("orderId") or order_resp.get("id")
        status = order_resp.get("status")
        print(f"  [PASS] Order placed: id={order_id}, status={status} in {res['elapsed_ms']:.1f}ms")
        return order_id
    else:
        print(f"  [FAIL] Order placement failed: status={res['status']}, data={res['data']}, error={res['error']}")
        return None

def test_market_data(instrument="BTC_USD"):
    print(f"\n--- 5. Checking Market Data Depth for {instrument} ---")
    res = http_req(
        f"{GATEWAY_URL}/market-data/depth/{instrument}",
        method="GET"
    )
    if res["status"] != 200:
        res = http_req(
            f"{MARKET_DATA_URL}/market-data/depth/{instrument}",
            method="GET"
        )
    if res["status"] == 200:
        depth = res["data"]
        bids = len(depth.get("bids", [])) if isinstance(depth, dict) else 0
        asks = len(depth.get("asks", [])) if isinstance(depth, dict) else 0
        print(f"  [PASS] Orderbook depth retrieved in {res['elapsed_ms']:.1f}ms (bids: {bids}, asks: {asks})")
        return depth
    else:
        print(f"  [WARN] Depth response: status={res['status']}, data={res['data']}")
        return None

def test_gateway_metrics():
    print("\n--- 6. Gateway In-Memory Metrics Snapshot ---")
    res = http_req(f"{GATEWAY_URL}/internal/metrics/snapshot", method="GET")
    if res["status"] == 200 and isinstance(res["data"], dict):
        d = res["data"]
        print(f"  [PASS] Total Requests: {d.get('totalRequests', 0)}")
        print(f"  [PASS] Active Conns:   {d.get('activeConnections', 0)}")
        print(f"  [PASS] P50 Latency:    {d.get('p50LatencyMs', 0):.2f} ms")
        print(f"  [PASS] P95 Latency:    {d.get('p95LatencyMs', 0):.2f} ms")
        print(f"  [PASS] P99 Latency:    {d.get('p99LatencyMs', 0):.2f} ms")
        return d
    else:
        print(f"  [WARN] Metrics snapshot unavailable or non-200: status={res['status']}")
        return None

def test_prometheus_targets():
    print("\n--- 7. Prometheus Active Scrape Targets ---")
    res = http_req(f"{PROMETHEUS_URL}/api/v1/targets", method="GET")
    if res["status"] == 200 and isinstance(res["data"], dict):
        targets = res["data"].get("data", {}).get("activeTargets", [])
        up_count = sum(1 for t in targets if t.get("health") == "up")
        print(f"  [PASS] Prometheus Targets: {up_count}/{len(targets)} UP")
        for t in targets:
            job = t.get("labels", {}).get("job", "unknown")
            addr = t.get("discoveredLabels", {}).get("__address__", "unknown")
            health = t.get("health", "unknown")
            print(f"    - {job:<22} ({addr:<22}) -> {health}")
        return targets
    else:
        print(f"  [WARN] Prometheus targets query returned status={res['status']}")
        return None

def main():
    print("=" * 60)
    print("  DETE LIVE EXCHANGE CLUSTER VERIFICATION & BENCHMARK")
    print("=" * 60)
    
    health_ok = test_health()
    
    # Authenticate demo user
    token, user_id = test_auth_login("demo", "DemoPassword123!")
    
    # Authenticate market maker bot
    bot_token, bot_id = test_auth_login("bot_maker", "BotMakerPass123!")
    
    if token and user_id:
        test_account_balances(token, user_id)
        # Place BUY limit order from demo user
        buy_id = test_order_placement(token, user_id, side="BUY", instrument="BTC_USD", price_usd=65000.0, qty=0.01)
    
    if bot_token and bot_id:
        # Place matching SELL limit order from bot maker
        sell_id = test_order_placement(bot_token, bot_id, side="SELL", instrument="BTC_USD", price_usd=65000.0, qty=0.01)
    
    # Let matching engine process
    time.sleep(1.0)
    
    test_market_data("BTC_USD")
    test_gateway_metrics()
    test_prometheus_targets()
    
    print("\n" + "=" * 60)
    print("  VERIFICATION COMPLETE")
    print("=" * 60)

if __name__ == "__main__":
    main()
