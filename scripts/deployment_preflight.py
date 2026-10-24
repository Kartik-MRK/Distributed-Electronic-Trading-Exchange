#!/usr/bin/env python3
"""Read-only production prerequisites. Never repair or reset live data here."""
import base64
import json
import os
import re
import subprocess
import sys


def run(*args, data=None):
    result = subprocess.run(args, input=data, capture_output=True, timeout=90)
    if result.returncode:
        # Commands can involve Secret material. Do not echo command output on failure.
        raise RuntimeError(f"Prerequisite command failed: {args[0]}")
    return result.stdout


def main():
    namespace = os.environ.get("DETE_NAMESPACE", "dete")
    secret_name = os.environ.get("DETE_JWT_SECRET", "dete-jwt-keys")
    blockers = []
    try:
        secret = json.loads(run("kubectl", "get", "secret", secret_name, "-n", namespace, "-o", "json"))
        private = base64.b64decode(secret["data"]["private-key"])
        public = base64.b64decode(secret["data"]["public-key"])
        # Accept the same PEM or Base64 DER formats as the application.
        def pem(value, label):
            if value.strip().startswith(b"-----BEGIN "):
                return value
            return b"-----BEGIN " + label + b"-----\n" + value.strip() + b"\n-----END " + label + b"-----\n"
        derived = run("openssl", "pkey", "-pubout", "-outform", "DER", data=pem(private, b"PRIVATE KEY"))
        configured = run("openssl", "pkey", "-pubin", "-outform", "DER", data=pem(public, b"PUBLIC KEY"))
        if derived != configured:
            raise ValueError("mismatched key pair")
        from pathlib import Path
        source = (Path(__file__).resolve().parents[1] / "libs/common-security/src/main/java/com/dete/common/security/RsaKeyUtils.java").read_text()
        default = source.split("DEFAULT_PUBLIC_KEY =", 1)[1].split(";", 1)[0]
        if configured == base64.b64decode("".join(re.findall(r'"([^"]+)"', default))):
            raise ValueError("development key")
        print("PASS: dedicated matching RSA key pair is provisioned")
    except (RuntimeError, ValueError, KeyError):
        blockers.append("Provision a matching non-development RSA key pair in " + secret_name)

    try:
        offsets = run("kubectl", "exec", "-n", namespace, "kafka-0", "--",
                      "kafka-run-class", "kafka.tools.GetOffsetShell", "--broker-list", "localhost:9092",
                      "--topic", "order.commands", "--time", "-2").decode()
        starts = [int(line.rsplit(":", 1)[1]) for line in offsets.splitlines() if line.startswith("order.commands:")]
        if not starts or any(start != 0 for start in starts):
            blockers.append("Complete order.commands history is missing; a reviewed data recovery is required")
        else:
            print("PASS: command history begins at offset zero in every partition")
    except (RuntimeError, ValueError):
        blockers.append("Cannot verify Kafka command-history completeness")

    query = """
    BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
    WITH ledger AS (
      SELECT account_id, asset, sum(CASE WHEN entry_type IN ('DEPOSIT','CREDIT') THEN amount
        WHEN entry_type IN ('WITHDRAWAL','DEBIT','FEE') THEN -amount ELSE 0 END) total
      FROM account.ledger_entries GROUP BY account_id, asset
    ) SELECT count(*) FROM account.balances b FULL JOIN ledger l USING(account_id,asset)
      WHERE coalesce(b.available::numeric + b.reserved,0) <> coalesce(l.total,0);
    COMMIT;
    """
    try:
        result = run("kubectl", "exec", "-i", "-n", namespace, "postgres-0", "--",
                     "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "dete", "-d", "dete",
                     data=query.encode()).decode().strip()
        if int(result) != 0:
            blockers.append("Account totals disagree with the ledger; reconcile before rollout")
        else:
            print("PASS: account totals match ledger-derived totals")
    except (RuntimeError, ValueError):
        blockers.append("Cannot verify balance/ledger conservation")

    for blocker in blockers:
        print("BLOCKED: " + blocker)
    return 1 if blockers else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("BLOCKED: prerequisite inspection failed (" + type(exc).__name__ + ")")
        sys.exit(1)
