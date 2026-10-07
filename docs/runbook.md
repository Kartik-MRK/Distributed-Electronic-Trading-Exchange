# Distributed Electronic Trading Exchange (DETE) — Operational Runbook

> **Audience:** Exchange Site Reliability Engineers (SRE), DevOps Engineers, Platform Administrators  
> **Target Environment:** Kubernetes / K3s Production Cluster (`oraclevps`: `141.148.223.82`, Namespace: `dete`)  
> **Version:** 1.0.0 (Production Release)

---

## Table of Contents

1. [Emergency Contacts & Incident Severities](#1-emergency-contacts--incident-severities)
2. [Matching Engine Crash Recovery & State Replay](#2-matching-engine-crash-recovery--state-replay)
3. [Double-Entry Balance Reconciliation & Drift Remediation](#3-double-entry-balance-reconciliation--drift-remediation)
4. [Dead-Letter Queue (DLQ) Inspection & Reprocessing](#4-dead-letter-queue-dlq-inspection--reprocessing)
5. [Zero-Downtime Secret & Key Rotation](#5-zero-downtime-secret--key-rotation)
6. [Adding a New Trading Instrument](#6-adding-a-new-trading-instrument)
7. [Grafana Telemetry & Alert Response Runbook](#7-grafana-telemetry--alert-response-runbook)
8. [Chaos Engineering & Disaster Recovery Checklist](#8-chaos-engineering--disaster-recovery-checklist)

---

## 1. Emergency Contacts & Incident Severities

| Severity | Definition | Target MTTR | Escalation |
|---|---|---|---|
| **SEV-1 (Critical)** | Core matching engine down, balance drift detected, double-spend detected | < 15 mins | Page Lead SRE & Core Architect immediately |
| **SEV-2 (High)** | Market data WebSocket dropped, Risk Service fallback open, Gateway degradation | < 30 mins | Page On-Call SRE |
| **SEV-3 (Medium)** | DLQ poison messages isolated, single bot simulator thread crashed | < 2 hours | DevOps Slack / Jira ticket |

---

## 2. Matching Engine Crash Recovery & State Replay

### Architecture Overview
The DETE Matching Engine is an in-memory single-writer deterministic state machine per trading pair (`BTC_USD`, `ETH_USD`, `SOL_USD`). No database hits occur on the matching path. State persistence and disaster recovery are guaranteed via Kafka append-only command log topic `order.commands`.

### Symptoms
- Pod restart (`kubectl get pods -n dete -l app=matching-engine`).
- REST endpoints `/matching/orderbook/{symbol}` returning 503 during warmup.
- Prometheus alert: `MatchingEngineDown`.

### Recovery Procedure

1. **Verify Pod Status & Logs:**
   ```bash
   kubectl get pods -n dete -l app=matching-engine
   kubectl logs -n dete -l app=matching-engine --tail=200 -f
   ```

2. **Automatic State Replay:**
   On startup, `KafkaReplayService` automatically initializes consumer group `matching-engine-replay-[uuid]` from `offset 0` of topic `order.commands`. It reconstructs all resting orders and current sequence numbers.
   Look for the log confirmation:
   ```
   [KafkaReplayService] Replayed X order commands for BTC_USD. Current sequence: Y.
   [MatchingEngineService] In-memory order books successfully reconstructed.
   ```

3. **Manual Trigger of State Replay (if offset drifted):**
   If state verification indicates a desynchronization, replay can be triggered dynamically via admin REST endpoint:
   ```bash
   curl -X POST http://141.148.223.82/matching/replay
   ```

4. **Verify Invariants Post-Recovery:**
   - Order book is uncrossed (`bestBid < bestAsk`):
     ```bash
     curl -s http://141.148.223.82/matching/orderbook/BTC_USD?depth=5 | jq .
     ```
   - Outbound duplicate trade check: Outgoing trades emitted during replay are suppressed by checking the highest processed sequence number, ensuring zero duplicate `TradeExecutedEvent`s dispatched to `trade.executions`.

---

## 3. Double-Entry Balance Reconciliation & Drift Remediation

### Theoretical Guarantee
DETE enforces an absolute mathematical invariant:
$$\sum_{\text{all accounts}} (\text{available} + \text{reserved}) = \sum_{\text{ledger}} (\text{DEPOSIT} + \text{CREDIT}) - \sum_{\text{ledger}} (\text{WITHDRAWAL} + \text{DEBIT})$$
Any difference ($\text{drift} \neq 0$) indicates data corruption, race condition, or hardware bit-flip.

### Automated Reconciliation Job
- **Schedule:** Runs automatically nightly at `00:00:00 UTC` via Spring `@Scheduled` in `AccountService`.
- **Alert Topic:** Publishes `CRITICAL` alert payload to Kafka topic `system.alerts` on mismatch.

### Manual Run & Verification
1. **Trigger Immediate Reconciliation:**
   ```bash
   curl -X POST http://141.148.223.82/accounts/reconciliation/run \
     -H "Content-Type: application/json" | jq .
   ```

2. **Sample Healthy Output:**
   ```json
   {
     "timestamp": "2026-10-07T14:30:00Z",
     "balanced": true,
     "assetCount": 4,
     "assets": {
       "USD": { "balancesSum": 5000000000000, "ledgerSum": 5000000000000, "drift": 0, "balanced": true },
       "BTC": { "balancesSum": 10000000000, "ledgerSum": 10000000000, "drift": 0, "balanced": true },
       "ETH": { "balancesSum": 50000000000, "ledgerSum": 50000000000, "drift": 0, "balanced": true },
       "SOL": { "balancesSum": 250000000000, "ledgerSum": 250000000000, "drift": 0, "balanced": true }
     }
   }
   ```

3. **Action if `balanced: false` (SEV-1):**
   - Identify the affected asset and drift quantity from the report.
   - Halt trading on affected instrument by pausing Gateway route:
     ```bash
     kubectl scale deployment gateway --replicas=0 -n dete  # Emergency freeze
     ```
   - Inspect PostgreSQL ledger records directly:
     ```sql
     SELECT account_id, asset, SUM(amount) FROM account.ledger_entries WHERE asset = 'USD' GROUP BY account_id, asset;
     ```
   - Cross-reference with the latest settled `trade.executions` events in Kafka.

---

## 4. Dead-Letter Queue (DLQ) Inspection & Reprocessing

### Background
When a poisoned message or unparseable payload is encountered by Kafka consumers (`OrderEventConsumer`, `TradeSettlementConsumer`, etc.), it is routed to a corresponding DLQ topic (e.g. `order.events.DLQ`) after exponential backoff retries to prevent blocking head-of-line execution.

### Inspection Procedure
1. **Query DLQ Messages:**
   ```bash
   curl -s http://141.148.223.82/admin/dlq/messages?topic=order.events.DLQ&limit=10 | jq .
   ```

2. **Inspect Poison Header:**
   Each DLQ record includes error metadata:
   - `x-exception-message`: Root cause message.
   - `x-exception-stacktrace`: Truncated stack trace.
   - `x-original-topic`: Origin topic.

### Reprocessing Procedure
1. **Fix Upstream Data / Deploy Hotfix** (if schema evolution issue).
2. **Replay DLQ Messages back to Main Topic:**
   ```bash
   curl -X POST http://141.148.223.82/admin/dlq/reprocess \
     -H "Content-Type: application/json" \
     -d '{"topic": "order.events.DLQ", "batchSize": 50}'
   ```
3. **Verify DLQ Depth Drops to Zero:**
   ```bash
   curl -s http://141.148.223.82/admin/dlq/messages?topic=order.events.DLQ | jq '. | length'
   ```

---

## 5. Zero-Downtime Secret & Key Rotation

### 5.1 RS256 JWT Keypair Rotation
DETE uses asymmetric RS256 JWT tokens. `auth` service signs tokens using the private key, while other services (`gateway`, `order`, `account`) verify tokens using the public key via JWKS or config.

1. **Generate New 2048-bit RSA Keypair:**
   ```bash
   openssl genpkey -algorithm RSA -out /tmp/new_private.pem -pkeyopt rsa_keygen_bits:2048
   openssl rsa -pubout -in /tmp/new_private.pem -out /tmp/new_public.pem
   NEW_PRIV_B64=$(base64 -w0 /tmp/new_private.pem)
   NEW_PUB_B64=$(base64 -w0 /tmp/new_public.pem)
   ```

2. **Deploy Public Key to Consumer Services First (Dual-Key Support):**
   Update `infra/helm/dete/values.yaml` with `auth.jwt.publicKey` and run `helm upgrade`:
   ```bash
   helm upgrade dete ./infra/helm/dete -n dete --reuse-values --set auth.jwt.publicKey="$NEW_PUB_B64"
   ```

3. **Deploy Private Key to Auth Service:**
   ```bash
   helm upgrade dete ./infra/helm/dete -n dete --reuse-values \
     --set auth.jwt.privateKey="$NEW_PRIV_B64" \
     --set auth.jwt.keyId="dete-auth-key-2"
   ```

4. **Verify Token Issuance & Validation:**
   ```bash
   TOKEN=$(curl -s -X POST http://141.148.223.82/auth/login -H "Content-Type: application/json" \
     -d '{"username":"demo","password":"DemoPassword123!"}' | jq -r .accessToken)
   curl -s http://141.148.223.82/accounts/balance -H "Authorization: Bearer $TOKEN" | jq .
   ```

### 5.2 Redis Password Rotation
1. Update `redis` password with `AUTH <old> REQUIREPASS <new>` in Redis CLI.
2. Upgrade Helm release with new `redis.password`.

---

## 6. Adding a New Trading Instrument

To add a new instrument (e.g. `AVAX_USD`):

1. **Add Enum Entry ([`libs/common-domain`](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/libs/common-domain/src/main/java/com/dete/common/domain/enums/Instrument.java)):**
   ```java
   AVAX_USD("AVAX", "USD", 100_000_000L, 100_000L, 50_000_00000000L)
   ```
2. **Configure Kafka Topics ([`scripts/init-kafka.sh`](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/scripts/init-kafka.sh)):**
   Topic `market-data.AVAX_USD` created automatically on startup or via `kafka-topics.sh`.
3. **Configure Risk Bands ([`services/risk`](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/engine/RiskRuleEvaluator.java)):**
   Define max size (e.g. 5,000 AVAX) and 10% price band thresholds.
4. **Deploy Simulator Market-Maker Bot:**
   Enable `AVAX_USD` bot strategy in `SimulatorConfig`.
5. **Update Frontend UI:**
   Add `AVAX-USD` to ticker selector in `TradingTerminal`.

---

## 7. Grafana Telemetry & Alert Response Runbook

### Primary Dashboard URL
`http://141.148.223.82/grafana/` (Credentials: `admin` / `admin`)

### Key Metrics & Action Thresholds

| Panel / Metric | Normal Range | Alert Threshold | Remediation Action |
|---|---|---|---|
| **E2E Order Latency (P99)** | 40 ms – 140 ms | > 200 ms for > 2 min | Check matching engine GC pause log; check Kafka consumer lag. |
| **Matching Engine Execution Time** | 0.5 μs – 6.4 μs | > 50 μs | Inspect thread contention on `me-[symbol]` single-writer executor. |
| **Kafka Consumer Lag** | 0 – 5 msgs | > 500 msgs | Scale order processing consumers or check PostgreSQL lock contention. |
| **JVM Old Gen Memory (G1GC)** | < 65% | > 85% | Trigger manual GC or increase `-Xmx` in Helm values (`matchingEngine.jvmArgs`). |
| **Active WebSocket Clients** | 10 – 500 clients | Sudden drop to 0 | Check Nginx reverse proxy connection limits (`worker_connections`). |
| **System Alerts Topic Depth** | 0 | > 0 | SEV-1! Check reconciliation alerts immediately. |

---

## 8. Chaos Engineering & Disaster Recovery Checklist

### Post-Pod-Kill Verification Steps

When any service pod is terminated (e.g. `kubectl delete pod ...` or node restart):

- [ ] **Kubernetes Self-Healing:** Verify pod returns to `1/1 Running` within 30 seconds (`kubectl get pods -n dete`).
- [ ] **Kafka Consumer Rebalance:** Verify group rebalance finishes without `CommitFailedException` in consumer logs.
- [ ] **Matching Engine Replay:** Verify matching engine reconstructs sequence numbers from offset 0 without emitting duplicate trades.
- [ ] **Balance Drift Zero Check:** Trigger `POST /accounts/reconciliation/run` and verify `drift == 0`.
- [ ] **End-to-End Trade Smoke Test:** Run `scripts/smoke_test.py` — verify 7/7 checks pass with HTTP 200.
- [ ] **WebSocket Streaming Integrity:** Verify live market data frames continue flowing on `/ws/topic/market-data.BTC_USD`.

---
*Operational Runbook verified and certified for Distributed Electronic Trading Exchange v1.0.0.*
