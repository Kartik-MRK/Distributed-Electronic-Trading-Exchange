# Coordinated demo market reset

Scope approved by the user: preserve login identities and infrastructure; archive and replace disposable trading history, balances, reservations, session state and affected application Kafka streams. No broker directory deletion, Redis FLUSHDB, authentication reset, or unrelated application mutation is part of this procedure.

`scripts/reset_demo_market.py` contains the exact allowlist. It defaults to inventory even when a step is supplied unless `--execute` is present. Require a dedicated timestamped backup directory under `/home/ubuntu/dete-backups`. Keep permissions private: archives include account/audit records, deployment configuration and Secrets. Do not upload them to GitHub.

Reset tables: account balances, ledger, settlements, outbox, order reservations; order orders/outbox; market-data trades/candles; audit log; simulator sessions/clock/resume prices/intents/admissions/outputs/receipts. `auth.*`, `account.accounts`, migration history and unlisted tables are preserved. The archived audit log preserves the prior trading record.

Reset topics: order.commands, order.events, trade.executions, market-data, ledger.events, audit.events, simulator.commands, system.alerts and the explicitly listed application DLQs. Only the eight named application consumer groups are removed. `__consumer_offsets` and ZooKeeper metadata remain in place. Only Redis `idempotency:order:*` keys are cleared; rate-limit/authentication keys remain.

Existing ordinary accounts receive 50000 USD, 1 BTC, 5 ETH and 50 SOL, with matching DEPOSIT ledger entries. Bot accounts receive 100000000 USD, 10000 BTC, 100000 ETH and 1000000 SOL. Seed references match application seed IDs to prevent duplicate bot/demo provisioning. Newly registered users retain the existing welcome-funding behavior.

## Preparation

1. Finish all component/integration tests and the isolated full-stack run; review the whole branch before live changes. Build the operator source and boot jars in an isolated checkout. Check `python3`, Java 21, kubectl, Helm, Docker, OpenSSL, tar and disk headroom.
2. Run inventory with explicit namespace and backup path. Record the deployed SHA, manifests, exact service images and relevant resources. Select a new UUID epoch; never reuse an epoch with trading history.
3. Announce the maintenance operation in the task's progress update. No additional approval is needed: the user approved this backed-up demo reset and deployment.

## Backup and rehearsal

Run `python3 scripts/reset_demo_market.py --namespace dete --backup /home/ubuntu/dete-backups/TIMESTAMP --step backup --execute`.

This stops all nine application writers, waits for their pods to disappear, dumps PostgreSQL, records row counts/identity digest and Kafka offsets, stops Kafka and ZooKeeper to archive their verified namespace-owned volumes, snapshots Redis, exports current application images, restarts only Kafka/ZooKeeper, and seals SHA-256 checksums. Application writers remain stopped. A failure leaves maintenance in place for inspection; never proceed with an incomplete backup.

The backup preflight requires explicit PVC subPath mounts at both `/var/lib/zookeeper/data` and `/var/lib/zookeeper/log`. Mounting only their parent is unsafe: the image declares child volumes that can shadow the persistent mount. Do not restart a legacy ZooKeeper pod until its actual child-volume data has been captured. The restore rehearsal also mounts both child directories explicitly. Any rollback must retain this persistence fix even when restoring older application images or Helm values.

Run `python3 scripts/rehearse_market_restore.py --namespace dete --backup /home/ubuntu/dete-backups/TIMESTAMP`.

The rehearsal restores copies into an isolated Docker network without host ports. It compares all stored table counts, the complete authentication identity digest, command-log ends, consumer offsets and Redis key count. Only a successful rehearsal writes the proof required by the reset command. Original deployment images and configuration are retained in the sealed backup.

## Migrate, reset and deploy

1. Run the operator `migrate` step with `--execute` from the reviewed source checkout. It uses Flyway directly, with no application listeners or scheduled tasks, to migrate the six service schemas. It reads existing database credentials without printing them.
2. Run the `reset` step with `--execute --epoch NEW_UUID`. It rechecks writer shutdown, checksums and restore proof, resets only allowlisted application topics/groups, seeds reconciled trading state, clears only order-idempotency keys, and initializes the exact new T0 in maintenance mode. All application replicas stay stopped.
3. Push the reviewed commits to the authorized branch. Monitor CI and CD for the exact SHA. Helm restores application replicas using the existing release values; all services use the new event/schema version against reset state. The clock remains in maintenance.
4. The automatic smoke check verifies ingress, authentication and closed admission in maintenance. It explicitly reports trading validation as deferred, preserving the first retained market history for the controlled run. Subsequent ordinary deployments run a sixty-second session-aware smoke test with real IOC fills and settlement checks.
5. Confirm every deployed image matches the SHA, migrations are current, all deployments are ready, and the clock is initialized at zero. Then set `simulator.market_clock.maintenance=false` with a conditional update for the expected epoch and zero elapsed time. Verify IDLE before validation.

## Validation

Run `scripts/verify_simulation_clock.py --base https://dete.kartik-mrk.me --namespace dete --output PRIVATE_EVIDENCE_PATH` on the VPS. It creates two disposable authenticated test accounts, starts five minutes, compares all five candles per instrument with executions, checks longer interval aggregation and ledger/reservation invariants, waits sixty seconds frozen, and starts one minute from the second account. Require exactly 360000ms cumulative time with no idle gap. Capture the evidence and redact credentials from the public report (the script never writes tokens or passwords).

Verify paused mutation rejection, candle history after safe service restart and reconnect behavior where browser tooling is available. A missing full-minute execution window is a failed validation, not a reason to synthesize market movement.

## Recovery

If a coordinated stage fails, keep application writers stopped and market admission closed. Preserve the failed state separately for diagnosis. Restore the matching set of PostgreSQL dump, Kafka/ZooKeeper volume copies, Redis snapshot, archived images and Helm configuration; do not roll back only code or only Kafka. Verify namespace/PVC paths against `volume-paths.json` before restoring, stop backing services during physical restoration, and repeat row-count/identity/offset checks before restarting the prior application release. Backups remain retained after success.
