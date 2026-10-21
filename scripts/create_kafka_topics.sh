#!/usr/bin/env bash
set -e

echo "Provisioning DETE Kafka topics inside K3s cluster..."

TOPICS=(
  "order.commands:3"
  "order.events:3"
  "trade.executions:3"
  "market-data:3"
  "ledger.events:3"
  "audit.events:3"
  "simulator.commands:1"
  "order.commands.DLQ:1"
  "order.events.DLQ:1"
  "trade.executions.DLQ:1"
  "ledger.events.DLQ:1"
  "audit.events.DLQ:1"
  "system.alerts:1"
)

for item in "${TOPICS[@]}"; do
  IFS=':' read -r topic partitions <<< "$item"
  echo "Creating topic $topic (partitions: $partitions)..."
  kubectl exec -n dete kafka-0 -- kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
    --topic "$topic" --partitions "$partitions" --replication-factor 1
done

echo "=== All DETE topics active in Kafka cluster ==="
kubectl exec -n dete kafka-0 -- kafka-topics --bootstrap-server kafka:9092 --list
