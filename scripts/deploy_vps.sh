#!/usr/bin/env bash
set -euo pipefail
umask 077

SHA="${1:?Pass the CI-verified commit SHA}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid commit SHA"; exit 1; }
REPO="${DETE_REPO_DIR:-$HOME/dete}"
RELEASE="$HOME/dete-releases/$SHA"
STATE="$HOME/.local/state/dete/$SHA"
NAMESPACE="${DETE_NAMESPACE:-dete}"
mkdir -p "$HOME/dete-releases" "$STATE"
exec 9>"$HOME/.local/state/dete/deployment.lock"
flock -n 9 || { echo "Another deployment is active"; exit 1; }

git -C "$REPO" fetch origin "$SHA"
if [[ ! -e "$RELEASE" ]]; then
  git -C "$REPO" worktree add --detach "$RELEASE" "$SHA"
fi
[[ "$(git -C "$RELEASE" rev-parse HEAD)" == "$SHA" ]]
git -C "$RELEASE" diff --exit-code --quiet
git -C "$RELEASE" diff --cached --exit-code --quiet
cd "$RELEASE"

# No image, deployment, topic, account, or Secret mutation precedes this check.
python3 scripts/deployment_preflight.py
helm get values dete -n "$NAMESPACE" -o yaml > "$STATE/values.yaml"
bash ./gradlew bootJar -x test --no-daemon --max-workers=2
export IMAGE_TAG="$SHA"
bash scripts/package_images.sh
bash scripts/import_images_to_k3s.sh

HELM_ARGS=()
for name in gateway auth account order matching-engine risk audit market-data simulator frontend; do
  key="$name"
  [[ "$name" == matching-engine ]] && key=matchingEngine
  [[ "$name" == market-data ]] && key=marketData
  HELM_ARGS+=(--set-string "$key.image=dete/$name:$SHA")
done
helm lint infra/helm/dete -f "$STATE/values.yaml" "${HELM_ARGS[@]}"

# A completed Job's pod template is immutable. Recreate only this initialization Job.
kubectl delete job kafka-init-topics -n "$NAMESPACE" --ignore-not-found=true --wait=true
helm upgrade --install dete infra/helm/dete -n "$NAMESPACE" \
  -f "$STATE/values.yaml" "${HELM_ARGS[@]}" \
  --set-string "secrets.jwtKeySecretName=${DETE_JWT_SECRET:-dete-jwt-keys}" \
  --wait --wait-for-jobs --timeout 12m
for name in gateway auth account order matching-engine risk audit market-data simulator frontend; do
  kubectl rollout status "deployment/$name" -n "$NAMESPACE" --timeout=360s
done
python3 - "$SHA" "$NAMESPACE" <<'PY'
import json, subprocess, sys
sha, namespace = sys.argv[1:]
deployments = json.loads(subprocess.check_output([
    "kubectl", "get", "deployments", "-n", namespace, "-o", "json"
]))["items"]
for name in ("gateway", "auth", "account", "order", "matching-engine", "risk",
             "audit", "market-data", "simulator", "frontend"):
    deployment = next(item for item in deployments if item["metadata"]["name"] == name)
    desired = deployment["spec"]["replicas"]
    status = deployment["status"]
    if (desired < 1 or status.get("readyReplicas", 0) != desired
            or status.get("updatedReplicas", 0) != desired
            or status.get("observedGeneration", 0) < deployment["metadata"]["generation"]):
        raise SystemExit(name + ": expected at least one ready replica of the current revision")
    containers = deployment["spec"]["template"]["spec"]["containers"]
    if not any(container["image"] == "dete/" + name + ":" + sha for container in containers):
        raise SystemExit(name + ": deployed image does not match the CI-verified commit")
print("Verified ready replicas and exact release images for all ten applications")
PY
printf '%s\n' "$SHA" > "$HOME/.local/state/dete/deployed-sha"
echo "All ten application deployments are ready at commit $SHA"
