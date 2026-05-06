#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=false
INSTALL_ROOT="/opt/x-ai-gateway"
TARGET_RELEASE=""

run() {
  if [[ "$DRY_RUN" == "true" ]]; then
    printf '[dry-run] %s\n' "$*"
  else
    "$@"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release)
      TARGET_RELEASE="${2:-}"
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

if [[ -z "$TARGET_RELEASE" && -f "$INSTALL_ROOT/previous-release" ]]; then
  TARGET_RELEASE="$(cat "$INSTALL_ROOT/previous-release")"
fi

if [[ -z "$TARGET_RELEASE" ]]; then
  echo "No target release provided and previous-release is missing" >&2
  exit 2
fi

run ln -sfn "$TARGET_RELEASE" "$INSTALL_ROOT/current"
run systemctl restart x-ai-gateway
run curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness
