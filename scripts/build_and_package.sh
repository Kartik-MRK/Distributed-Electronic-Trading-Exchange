#!/usr/bin/env bash
set -e

echo "=========================================================="
echo " DETE Fast Build & Container Image Packaging"
echo "=========================================================="

echo "=== [1/3] Building all Spring Boot service jars in one batch ==="
./gradlew bootJar -x test --no-daemon --max-workers=2

echo "=== [2/3] Packaging minimal JRE runtime Docker images ==="
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
  JAR=$(ls services/$NAME/build/libs/*.jar 2>/dev/null | head -n 1)
  echo "Packaging dete/$NAME:latest from $JAR..."
  docker build -f infra/docker/Dockerfile.service --build-arg JAR_FILE="$JAR" -t "dete/$NAME:latest" .
done

echo "=== [3/3] Building Next.js Frontend Docker image ==="
docker build -t dete/frontend:latest ./frontend

echo "=========================================================="
echo " All DETE Docker Images Built Successfully"
echo "=========================================================="
docker images | grep dete/
