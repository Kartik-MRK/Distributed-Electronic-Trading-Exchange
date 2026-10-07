#!/usr/bin/env python3
"""
DETE CI/CD Performance Regression Checker
Executes performance benchmark against the deployed exchange,
compares results against benchmarks/baseline.json,
and generates automated GitHub Actions summary & regression report.

Exits with:
  0 - Performance within baseline SLA thresholds
  1 - Regression exceeded maximum allowed threshold (>20%)
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error
import uuid
from datetime import datetime

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASELINE_FILE = os.path.join(ROOT_DIR, "benchmarks", "baseline.json")
RESULTS_DIR = os.path.join(ROOT_DIR, "benchmarks", "results")

GATEWAY_URL = os.environ.get("GATEWAY_URL", os.environ.get("BASE_URL", "http://10.43.2.218:8080"))
AUTH_URL = os.environ.get("AUTH_URL", "http://10.43.145.198:8081")
ORDER_URL = os.environ.get("ORDER_URL", "http://10.43.229.150:8083")
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

def load_baseline():
    if not os.path.exists(BASELINE_FILE):
        print(f"[WARN] Baseline file not found at {BASELINE_FILE}. Using fallback values.")
        return {
            "sla_thresholds": {
                "order_placement_p50_max_ms": 250.0,
                "order_placement_p95_max_ms": 350.0,
                "order_placement_p99_max_ms": 400.0,
                "max_allowed_regression_pct": 20.0
            },
            "baseline_metrics": {
                "p50_latency_ms": 211.18,
                "p95_latency_ms": 296.35,
                "p99_latency_ms": 296.35,
                "avg_latency_ms": 200.06
            }
        }
    with open(BASELINE_FILE, "r") as f:
        return json.load(f)

def run_benchmark(pairs=10):
    print("=" * 65)
    print("  DETE AUTOMATED PERFORMANCE BENCHMARK & REGRESSION EVALUATION")
    print(f"  Target Gateway: {GATEWAY_URL}")
    print("=" * 65)

    # 1. Login demo user
    login = http_call(f"{GATEWAY_URL}/auth/login", method="POST", data={"identifier": "demo", "password": "DemoPassword123!"})
    if login["status"] != 200:
        login = http_call(f"{AUTH_URL}/auth/login", method="POST", data={"identifier": "demo", "password": "DemoPassword123!"})
    if login["status"] != 200:
        print("[FAIL] Could not authenticate demo user")
        return None
    token = login["data"]["accessToken"]
    user_id = login["data"]["userId"]

    # 2. Login bot maker
    bot_login = http_call(f"{GATEWAY_URL}/auth/login", method="POST", data={"identifier": "bot_maker", "password": "BotMakerPass123!"})
    if bot_login["status"] != 200:
        bot_login = http_call(f"{AUTH_URL}/auth/login", method="POST", data={"identifier": "bot_maker", "password": "BotMakerPass123!"})
    if bot_login["status"] != 200:
        print("[FAIL] Could not authenticate bot_maker")
        return None
    bot_token = bot_login["data"]["accessToken"]
    bot_id = bot_login["data"]["userId"]

    # 3. Execute paired benchmark
    latencies = []
    successes = 0
    total_orders = pairs * 2

    print(f"\nSubmitting {pairs} paired Maker/Taker orders ({total_orders} total requests)...")
    for i in range(1, pairs + 1):
        price = 64900.0 + (i * 10.0)
        qty = 0.005

        # BUY order
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
        res_buy = http_call(f"{GATEWAY_URL}/orders", method="POST", data=buy_data, headers=headers_buy)
        if res_buy["status"] in (200, 201, 202):
            latencies.append(res_buy["elapsed_ms"])
            successes += 1

        # SELL order
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
        res_sell = http_call(f"{GATEWAY_URL}/orders", method="POST", data=sell_data, headers=headers_sell)
        if res_sell["status"] in (200, 201, 202):
            latencies.append(res_sell["elapsed_ms"])
            successes += 1

        time.sleep(0.05)

    if not latencies:
        print("[FAIL] No orders succeeded during benchmark")
        return None

    latencies.sort()
    n = len(latencies)
    p50 = latencies[int(n * 0.50)]
    p95 = latencies[min(int(n * 0.95), n - 1)]
    p99 = latencies[min(int(n * 0.99), n - 1)]
    avg = sum(latencies) / n

    return {
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "total_orders": total_orders,
        "success_count": successes,
        "success_rate_pct": (successes / total_orders) * 100.0,
        "min_latency_ms": latencies[0],
        "avg_latency_ms": avg,
        "p50_latency_ms": p50,
        "p95_latency_ms": p95,
        "p99_latency_ms": p99,
        "max_latency_ms": latencies[-1]
    }

def evaluate_regression(current, baseline):
    base_m = baseline.get("baseline_metrics", {})
    sla = baseline.get("sla_thresholds", {})
    max_reg_pct = sla.get("max_allowed_regression_pct", 20.0)

    base_p50 = base_m.get("p50_latency_ms", 211.18)
    base_p95 = base_m.get("p95_latency_ms", 296.35)
    base_p99 = base_m.get("p99_latency_ms", 296.35)

    p50_delta_pct = ((current["p50_latency_ms"] - base_p50) / base_p50) * 100.0
    p95_delta_pct = ((current["p95_latency_ms"] - base_p95) / base_p95) * 100.0
    p99_delta_pct = ((current["p99_latency_ms"] - base_p99) / base_p99) * 100.0

    print("\n--- PERFORMANCE REGRESSION EVALUATION ---")
    print(f"  P50 Latency: {current['p50_latency_ms']:.2f} ms (Baseline: {base_p50:.2f} ms | Delta: {p50_delta_pct:+.2f}%)")
    print(f"  P95 Latency: {current['p95_latency_ms']:.2f} ms (Baseline: {base_p95:.2f} ms | Delta: {p95_delta_pct:+.2f}%)")
    print(f"  P99 Latency: {current['p99_latency_ms']:.2f} ms (Baseline: {base_p99:.2f} ms | Delta: {p99_delta_pct:+.2f}%)")
    print(f"  Success Rate: {current['success_rate_pct']:.1f}%")

    # Generate Markdown Summary for GHA
    summary_md = f"""## 📊 DETE Performance Regression Report
**Date**: `{current['timestamp']}`  
**Status**: `{'✅ PASSED' if p99_delta_pct <= max_reg_pct else '❌ REGRESSION DETECTED'}`

| Metric | Measured | Baseline | Delta | Threshold | Status |
|---|---|---|---|---|---|
| **P50 Latency** | `{current['p50_latency_ms']:.2f} ms` | `{base_p50:.2f} ms` | `{p50_delta_pct:+.2f}%` | `< {sla.get('order_placement_p50_max_ms', 250)} ms` | {'✅' if current['p50_latency_ms'] <= sla.get('order_placement_p50_max_ms', 250) else '⚠️'} |
| **P95 Latency** | `{current['p95_latency_ms']:.2f} ms` | `{base_p95:.2f} ms` | `{p95_delta_pct:+.2f}%` | `< {sla.get('order_placement_p95_max_ms', 350)} ms` | {'✅' if current['p95_latency_ms'] <= sla.get('order_placement_p95_max_ms', 350) else '⚠️'} |
| **P99 Latency** | `{current['p99_latency_ms']:.2f} ms` | `{base_p99:.2f} ms` | `{p99_delta_pct:+.2f}%` | `+{max_reg_pct}%` | {'✅ PASS' if p99_delta_pct <= max_reg_pct else '❌ REGRESSED'} |
| **Success Rate** | `{current['success_rate_pct']:.1f}%` | `100.0%` | `0.0%` | `100.0%` | {'✅ PASS' if current['success_rate_pct'] >= 99.0 else '❌ FAIL'} |
"""
    github_step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if github_step_summary:
        with open(github_step_summary, "a") as f:
            f.write(summary_md)

    # Save to results directory
    os.makedirs(RESULTS_DIR, exist_ok=True)
    today_str = datetime.utcnow().strftime("%Y-%m-%d")
    result_path = os.path.join(RESULTS_DIR, f"{today_str}_benchmark.json")
    with open(result_path, "w") as f:
        json.dump({"metrics": current, "evaluation": {"p50_delta": p50_delta_pct, "p99_delta": p99_delta_pct}}, f, indent=2)
    print(f"\nSaved benchmark results to {result_path}")

    if p99_delta_pct > max_reg_pct:
        print(f"\n[FAIL] Performance regression detected: P99 delta ({p99_delta_pct:.2f}%) exceeds allowable threshold ({max_reg_pct}%).")
        return False
    else:
        print(f"\n[PASS] Performance verified: P99 delta ({p99_delta_pct:+.2f}%) within allowable SLA threshold.")
        return True

def main():
    baseline = load_baseline()
    current = run_benchmark(pairs=10)
    if not current:
        sys.exit(1)
    passed = evaluate_regression(current, baseline)
    sys.exit(0 if passed else 1)

if __name__ == "__main__":
    main()
