# Oracle VPS rollout — 2026-10-24

## Scope and authorization

The user authorized committing and pushing the reviewed changes, running GitHub CI/CD, verifying the public deployment, and a **backed-up reset of disposable demo trading data**. This remains a simulated-funds demonstration. The architectural limitations in [the source audit](project_review_2026-10-24.md#remaining-blockers-and-limits), particularly aggregate reservations and in-memory risk admission, are not resolved by a clean data baseline or a successful smoke test.

## Recovery boundary

- Paused the simulator and application writers before taking the PostgreSQL backup; stopped Kafka before archiving its data.
- Saved PostgreSQL, Kafka, ZooKeeper, Redis, Kubernetes configuration/Secrets, Helm values/manifests, Nginx configuration, and the existing VPS checkout's modified scripts and benchmark results under `/home/ubuntu/dete-backups/20261024T161539Z` (private permissions).
- Restored the PostgreSQL archive into a temporary database and verified matching user, order, ledger, and settlement row counts, then dropped only that verification database. Archive integrity and SHA-256 checks were recorded. The original Kafka directory was also renamed and retained as a second copy.
- Preserved all **29 existing authentication accounts** and the audit history. Reset orders, trading accounts/balances/ledger/settlements/outboxes, market trades, refresh sessions, Redis state, and Kafka topics as an explicit demo migration; this did not reconstruct lost historical trading data.
- Seeded each preserved user with ledger-backed demo grants of USD 50,000, BTC 1, ETH 5, and SOL 50, producing 116 deposit entries and zero initial orders/settlements. The existing simulator has its own additional, now idempotent funding operations.
- Created a fresh RSA-3072 signing identity in a separately provisioned Kubernetes Secret. Private keys are not in Git or Helm values. Existing access and refresh sessions are invalidated; users must sign in again.
- Added persistent ZooKeeper storage and stopped deleting Kafka's broker identity file on startup. The fresh command log begins at offset zero in all three partitions and retains complete history for the current replay implementation.

## Deployment controls

The source fixes are committed as `9020d05`; deployment and smoke-test improvements are committed as `d9c345e`. GitHub CI passed for the exact release commit: [master CI run 40111281174](https://github.com/Kartik-MRK/Distributed-Electronic-Trading-Exchange/actions/runs/40111281174).

The deployment now checks exact-commit CI success, dedicated matching keys, complete command history, and ledger conservation before changing images or workloads. It creates a detached release checkout under `/home/ubuntu/dete-releases/<commit>`, preserving the original `/home/ubuntu/dete` working-tree edits. Images use the commit SHA instead of the mutable `latest` tag. Deployment serialization, Helm completion, all ten application rollouts, nonzero ready replicas, and exact image tags are checked before recording the deployed SHA.

CI covers Java unit/integration tests, frontend tests/type checking/lint/build, formatting, Helm lint, and deployment-script checks. The existing vulnerability scanner is advisory; a green workflow is not proof that no vulnerable dependencies exist.

## Live verification

The exact release `d9c345e30638d2dcb038831575b7248bd7df9b6e` is live on the Oracle VPS. [GitHub CI](https://github.com/Kartik-MRK/Distributed-Electronic-Trading-Exchange/actions/runs/40111281174) and [GitHub CD, including its automated smoke job](https://github.com/Kartik-MRK/Distributed-Electronic-Trading-Exchange/actions/runs/40111694762) both completed successfully.

- All ten application deployments have one ready replica using the release's commit-tagged image. Kafka and persistent ZooKeeper are also ready.
- The GitHub smoke job passed its ingress → authentication → risk → reservation → match → settlement → market-data checks. It exercised registration/login, rejected anonymous and cross-account balance access, completed a buyer/seller trade, checked shared trade IDs and public privacy, and verified exact balance deltas with released reservations.
- An independent public HTTPS check returned valid pages for `/`, `/login`, and `/terminal`; all 18 discovered Next.js static assets loaded. The public JWKS, BTC order-book, and trades endpoints returned valid JSON.
- An independent STOMP check connected over public WSS, received a BTC order-book message, and verified that an anonymous private-order subscription receives a denial.
- At the database/engine consistency check, PostgreSQL had 15 active BTC orders, 13 active ETH orders (12 accepted and one partial), and 4 active SOL orders (3 accepted and one partial). Matching-engine book counts were respectively 15, 13, and 4. These are sequential service observations while the simulator is active, not one distributed transaction snapshot.
- The post-reset ledger conservation check found **zero** mismatched balances. The final database contained 32 users: the 29 accounts present before reset plus accounts created by demo/smoke activity.
- The backup was copied to `C:\Users\mavin\AppData\Local\DETE\backups\20261024T161539Z` and every file with a recorded SHA-256 checksum matched. The compressed Kafka archive is approximately 1.15 GB; the PostgreSQL archive also passed temporary-database restore and row-count verification.

The deployment script now also validates each workload's desired, ready, and updated replica counts and exact image tag after Helm rollouts. This closes a gap where a zero-replica rollout could look successful. It is included in the follow-up commit after the initial live release and will be checked by the next CI/CD run.

## Remaining limits

The reset resolves the old missing-history and balance-drift deployment blockers by replacing authorized demo data. It does not implement per-order reservation ownership, a durable placement/compensation saga, durable risk admission, service-to-service authentication, arbitrary crash/rebalance correctness, full historical candle recovery, or live email delivery verification. Do not use real funds or interpret the finite checks below as a proof of all failure schedules.
