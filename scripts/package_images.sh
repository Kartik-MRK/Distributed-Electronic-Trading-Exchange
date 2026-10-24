#!/usr/bin/env bash
set -euo pipefail
IMAGE_TAG="${IMAGE_TAG:-latest}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Packaging DETE Container Images from Pre-Built Jars"
echo "=========================================================="

SERVICES=(
  "gateway"
  "auth"
  "account"
  "order"
  "matching-engine"
  "risk"
  "audit"
  "market-data"
  "simulator"
)

for NAME in "${SERVICES[@]}"; do
  JAR=$(ls services/$NAME/build/libs/*.jar | grep -v 'plain' | head -n 1)
  echo "--> Packaging dete/$NAME:$IMAGE_TAG from $JAR..."
  sudo docker build -f infra/docker/Dockerfile.service --build-arg JAR_FILE="$JAR" -t "dete/$NAME:$IMAGE_TAG" .
done

echo "--> Packaging dete/frontend:$IMAGE_TAG..."
sudo docker build -t "dete/frontend:$IMAGE_TAG" ./frontend

echo "=========================================================="
echo " All DETE Docker Images Built Successfully"
echo "=========================================================="
sudo docker images | grep dete/
