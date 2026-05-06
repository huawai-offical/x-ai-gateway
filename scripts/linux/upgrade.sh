#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=false
ARTIFACT=""
INSTALL_ROOT="/opt/x-ai-gateway"

run() {
  if [[ "$DRY_RUN" == "true" ]]; then
    printf '[dry-run] %s\n' "$*"
  else
    "$@"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact)
      ARTIFACT="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$ARTIFACT" ]]; then
  echo "--artifact is required" >&2
  exit 2
fi

if [[ "$DRY_RUN" != "true" && ! -f "$ARTIFACT" ]]; then
  echo "Artifact does not exist: $ARTIFACT" >&2
  exit 2
fi

RELEASE_ID="$(date +%Y%m%d%H%M%S)"
RELEASE_DIR="$INSTALL_ROOT/releases/$RELEASE_ID"
PREVIOUS="$(readlink -f "$INSTALL_ROOT/current" || true)"

run mkdir -p "$RELEASE_DIR"
run cp "$ARTIFACT" "$RELEASE_DIR/x-ai-gateway.jar"
run chown -R x-ai-gateway:x-ai-gateway "$RELEASE_DIR"
run ln -sfn "$RELEASE_DIR" "$INSTALL_ROOT/current"
run systemctl restart x-ai-gateway
run curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness

if [[ -n "$PREVIOUS" ]]; then
  printf '%s\n' "$PREVIOUS" | run tee "$INSTALL_ROOT/previous-release"
fi
