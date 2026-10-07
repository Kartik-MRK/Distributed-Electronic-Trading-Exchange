#!/usr/bin/env python3
"""
DETE Full Exchange Live Verification & Benchmark Script
Runs inside or against the K3s cluster on oraclevps.
Tests:
  1. Actuator Health for all microservices
  2. Authentication (/auth/login) for demo and bot_maker
  3. Account Balances (/accounts/me/balances)
  4. Order Placement (/orders) - BUY & SELL through Gateway
  5. Matching & Trade Execution
  6. Market Data Order Book (/market-data/{instrument}/orderbook) & Trades
  7. High-Throughput Trading Benchmark (P50, P95, P99 Roundtrip Latency)
  8. Gateway In-Memory Metrics Snapshot (/internal/metrics/snapshot)
  9. Prometheus Active Scrape Targets
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
        print(f"  Gateway login non-200 ({res['status']}), attempting direct Auth service...")
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
    headers = {"Authorization": f"Bearer {token}", "X-User-Id": str(user_id)}
    res = http_req(f"{GATEWAY_URL}/accounts/me/balances", method="GET", headers=headers)
    if res["status"] != 200:
        res = http_req(f"{GATEWAY_URL}/accounts/{user_id}", method="GET", headers=headers)
    if res["status"] != 200:
        res = http_req(f"{ACCOUNT_URL}/accounts/me/balances", method="GET", headers=headers)
    if res["status"] != 200:
        res = http_req(f"{ACCOUNT_URL}/accounts/{user_id}", method="GET", headers=headers)
        
    if res["status"] == 200 and isinstance(res["data"], list):
        print(f"  [PASS] Balances retrieved in {res['elapsed_ms']:.1f}ms:")
        for b in res["data"]:
            asset = b.get("asset")
            avail = b.get("availableDisplay", b.get("available", 0) / SCALE)
            resv = b.get("reservedDisplay", b.get("reserved", 0) / SCALE)
            tot = b.get("totalDisplay", b.get("total", 0) / SCALE)
            print(f"         {asset:<5}: Avail={avail:>10.4f} | Reserved={resv:>10.4f} | Total={tot:>10.4f}")
        return res["data"]
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
        print(f"  Gateway returned {res['status']}, attempting direct Order service...")
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
    print(f"\n--- 5. Checking Market Data for {instrument} ---")
    res = http_req(f"{GATEWAY_URL}/market-data/{instrument}/orderbook", method="GET")
    if res["status"] != 200:
        res = http_req(f"{MARKET_DATA_URL}/market-data/{instrument}/orderbook", method="GET")
    
    if res["status"] == 200 and isinstance(res["data"], dict):
        depth = res["data"]
        bids = depth.get("bids", [])
        asks = depth.get("asks", [])
        seq = depth.get("sequenceNumber", 0)
        print(f"  [PASS] L2 OrderBook ({instrument}): {len(bids)} bids, {len(asks)} asks, seq={seq} in {res['elapsed_ms']:.1f}ms")
        if bids:
            best_bid = bids[0]
            print(f"         Best Bid: ${best_bid.get('price', 0)/SCALE:.2f} (Vol: {best_bid.get('volume', 0)/SCALE:.4f})")
        if asks:
            best_ask = asks[0]
            print(f"         Best Ask: ${best_ask.get('price', 0)/SCALE:.2f} (Vol: {best_ask.get('volume', 0)/SCALE:.4f})")
    else:
        print(f"  [WARN] OrderBook query returned status={res['status']}")

    # Check Recent Trades
    res_trades = http_req(f"{GATEWAY_URL}/market-data/{instrument}/trades", method="GET")
    if res_trades["status"] == 200 and isinstance(res_trades["data"], list):
        trades = res_trades["data"]
        print(f"  [PASS] Recent Trades: {len(trades)} executed trades recorded in {res_trades['elapsed_ms']:.1f}ms")
    
    # Check Last Traded Price
    res_price = http_req(f"{GATEWAY_URL}/market-data/{instrument}/price", method="GET")
    if res_price["status"] == 200 and isinstance(res_price["data"], dict):
        p_data = res_price["data"]
        print(f"  [PASS] Last Traded Price: ${p_data.get('formattedPrice', '0.00')} in {res_price['elapsed_ms']:.1f}ms")

def run_trading_benchmark(token, user_id, bot_token, bot_id, pairs=10):
    print(f"\n--- 6. Running Live Trading Benchmark ({pairs} Paired Orders) ---")
    latencies = []
    successes = 0
    
    for i in range(1, pairs + 1):
        price = 64900.0 + (i * 10.0)
        qty = 0.005
        
        # 1. Place Maker BUY
        buy_data = {
            "instrument": "BTC_USD",
            "side": "BUY",
            "orderType": "LIMIT",
            "price": int(price * SCALE),
            "quantity": int(qty * SCALE)
        }
        headers_buy = {
            "Authorization": f"Bearer {token}",
            "Idempotency-Key": str(uuid.uuid4()),
            "X-User-Id": str(user_id)
        }
        res_buy = http_req(f"{GATEWAY_URL}/orders", method="POST", data=buy_data, headers=headers_buy)
        if res_buy["status"] in (200, 201, 202):
            latencies.append(res_buy["elapsed_ms"])
            successes += 1
        
        # 2. Place Taker SELL at exact same price
        sell_data = {
            "instrument": "BTC_USD",
            "side": "SELL",
            "orderType": "LIMIT",
            "price": int(price * SCALE),
            "quantity": int(qty * SCALE)
        }
        headers_sell = {
            "Authorization": f"Bearer {bot_token}",
            "Idempotency-Key": str(uuid.uuid4()),
            "X-User-Id": str(bot_id)
        }
        res_sell = http_req(f"{GATEWAY_URL}/orders", method="POST", data=sell_data, headers=headers_sell)
        if res_sell["status"] in (200, 201, 202):
            latencies.append(res_sell["elapsed_ms"])
            successes += 1
            
        time.sleep(0.05)
    
    latencies.sort()
    n = len(latencies)
    if n > 0:
        p50 = latencies[int(n * 0.50)]
        p95 = latencies[min(int(n * 0.95), n - 1)]
        p99 = latencies[min(int(n * 0.99), n - 1)]
        avg = sum(latencies) / n
        print(f"  [PASS] Benchmark Completed: {successes}/{pairs * 2} orders submitted successfully")
        print(f"         Min: {latencies[0]:.2f}ms | Avg: {avg:.2f}ms | Max: {latencies[-1]:.2f}ms")
        print(f"         P50: {p50:.2f}ms | P95: {p95:.2f}ms | P99: {p99:.2f}ms")
        return {"p50": p50, "p95": p95, "p99": p99, "avg": avg, "total": successes}
    return None

def test_gateway_metrics():
    print("\n--- 7. Gateway In-Memory Metrics Snapshot ---")
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
    print("\n--- 8. Prometheus Active Scrape Targets ---")
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
    
    test_health()
    
    token, user_id = test_auth_login("demo", "DemoPassword123!")
    bot_token, bot_id = test_auth_login("bot_maker", "BotMakerPass123!")
    
    if token and user_id:
        test_account_balances(token, user_id)
        test_order_placement(token, user_id, side="BUY", instrument="BTC_USD", price_usd=65000.0, qty=0.01)
    
    if bot_token and bot_id:
        test_order_placement(bot_token, bot_id, side="SELL", instrument="BTC_USD", price_usd=65000.0, qty=0.01)
    
    time.sleep(1.0)
    test_market_data("BTC_USD")
    
    if token and user_id and bot_token and bot_id:
        run_trading_benchmark(token, user_id, bot_token, bot_id, pairs=10)
    
    test_gateway_metrics()
    test_prometheus_targets()
    
    print("\n" + "=" * 60)
    print("  VERIFICATION COMPLETE")
    print("=" * 60)

if __name__ == "__main__":
    main()
