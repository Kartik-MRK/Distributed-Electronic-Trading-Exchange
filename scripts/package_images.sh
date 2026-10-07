#!/usr/bin/env bash
set -e

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
  JAR=$(ls services/$NAME/build/libs/*.jar | head -n 1)
  echo "--> Packaging dete/$NAME:latest from $JAR..."
  sudo docker build -f infra/docker/Dockerfile.service --build-arg JAR_FILE="$JAR" -t "dete/$NAME:latest" .
done

echo "--> Packaging dete/frontend:latest..."
sudo docker build -t dete/frontend:latest ./frontend

echo "=========================================================="
echo " All DETE Docker Images Built Successfully"
echo "=========================================================="
sudo docker images | grep dete/
