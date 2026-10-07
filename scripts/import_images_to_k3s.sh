#!/usr/bin/env bash
set -e

IMAGES=(
  "dete/gateway:latest"
  "dete/auth:latest"
  "dete/account:latest"
  "dete/order:latest"
  "dete/matching-engine:latest"
  "dete/risk:latest"
  "dete/audit:latest"
  "dete/market-data:latest"
  "dete/simulator:latest"
  "dete/frontend:latest"
)

for img in "${IMAGES[@]}"; do
  echo "Importing $img into K3s containerd..."
  docker save "$img" | sudo k3s ctr images import -
done

echo "All DETE images successfully imported into K3s containerd."
