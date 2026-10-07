#!/usr/bin/env bash
set -e

echo "=========================================================="
echo " DETE Fast Build & Container Image Packaging"
echo "=========================================================="

echo "=== [1/3] Building all Spring Boot service jars in one batch ==="
./gradlew bootJar -x test --no-daemon --max-workers=2

echo "=== [2/3] Packaging minimal JRE runtime Docker images ==="
SERVICES=(
  "gateway:services/gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar"
  "auth:services/auth/build/libs/auth-0.0.1-SNAPSHOT.jar"
  "account:services/account/build/libs/account-0.0.1-SNAPSHOT.jar"
  "order:services/order/build/libs/order-0.0.1-SNAPSHOT.jar"
  "matching-engine:services/matching-engine/build/libs/matching-engine-0.0.1-SNAPSHOT.jar"
  "risk:services/risk/build/libs/risk-0.0.1-SNAPSHOT.jar"
  "audit:services/audit/build/libs/audit-0.0.1-SNAPSHOT.jar"
  "market-data:services/market-data/build/libs/market-data-0.0.1-SNAPSHOT.jar"
  "simulator:services/simulator/build/libs/simulator-0.0.1-SNAPSHOT.jar"
)

for entry in "${SERVICES[@]}"; do
  NAME="${entry%%:*}"
  JAR="${entry#*:}"
  echo "Packaging dete/$NAME:latest from $JAR..."
  docker build -f infra/docker/Dockerfile.service --build-arg JAR_FILE="$JAR" -t "dete/$NAME:latest" .
done

echo "=== [3/3] Building Next.js Frontend Docker image ==="
docker build -t dete/frontend:latest ./frontend

echo "=========================================================="
echo " All DETE Docker Images Built Successfully"
echo "=========================================================="
docker images | grep dete/
