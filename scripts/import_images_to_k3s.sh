#!/usr/bin/env bash
set -euo pipefail
IMAGE_TAG="${IMAGE_TAG:-latest}"

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
  img="${img%:latest}:$IMAGE_TAG"
  echo "Importing $img into K3s containerd..."
  sudo docker save "$img" | sudo k3s ctr images import -
done

echo "All DETE images successfully imported into K3s containerd."
