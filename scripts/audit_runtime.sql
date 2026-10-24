-- Read-only health audit. No account identifiers, credentials, or personal data are returned.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT instrument, status, count(*) AS orders, sum(remaining_qty) AS remaining_quantity
FROM order_svc.orders GROUP BY instrument, status ORDER BY instrument, status;
SELECT asset, sum(available) AS available, sum(reserved) AS reserved
FROM account.balances GROUP BY asset ORDER BY asset;
WITH ledger AS (
  SELECT account_id, asset, sum(CASE
    WHEN entry_type IN ('DEPOSIT', 'CREDIT') THEN amount
    WHEN entry_type IN ('WITHDRAWAL', 'DEBIT', 'FEE') THEN -amount ELSE 0 END) AS total
  FROM account.ledger_entries GROUP BY account_id, asset
), differences AS (
  SELECT coalesce(b.asset, l.asset) AS asset,
    coalesce(b.available + b.reserved, 0) - coalesce(l.total, 0) AS drift
  FROM account.balances b FULL JOIN ledger l USING (account_id, asset)
)
SELECT asset, count(*) AS mismatched_balances, sum(drift) AS net_drift
FROM differences WHERE drift <> 0 GROUP BY asset;
SELECT 'order' AS service, count(*) AS pending_outbox FROM order_svc.outbox WHERE NOT published
UNION ALL SELECT 'account', count(*) FROM account.outbox WHERE NOT published;
COMMIT;
