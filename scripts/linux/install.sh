#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=false
ARTIFACT=""
INSTALL_ROOT="/opt/x-ai-gateway"
ENV_DIR="/etc/x-ai-gateway"
DATA_DIR="/var/lib/x-ai-gateway"
LOG_DIR="/var/log/x-ai-gateway"
SERVICE_FILE="/etc/systemd/system/x-ai-gateway.service"

usage() {
  cat <<'USAGE'
Usage: install.sh --artifact <x-ai-gateway.jar> [--dry-run]

Installs x-ai-gateway into /opt/x-ai-gateway/current and configures systemd.
USAGE
}

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
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
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

run id -u x-ai-gateway >/dev/null 2>&1 || run useradd --system --home "$DATA_DIR" --shell /usr/sbin/nologin x-ai-gateway
run mkdir -p "$RELEASE_DIR" "$ENV_DIR" "$DATA_DIR" "$LOG_DIR"
run cp "$ARTIFACT" "$RELEASE_DIR/x-ai-gateway.jar"
run chown -R x-ai-gateway:x-ai-gateway "$INSTALL_ROOT" "$DATA_DIR" "$LOG_DIR"
run ln -sfn "$RELEASE_DIR" "$INSTALL_ROOT/current"

if [[ ! -f "$ENV_DIR/x-ai-gateway.env" ]]; then
  run cp "$(dirname "$0")/x-ai-gateway.env.example" "$ENV_DIR/x-ai-gateway.env"
fi

run cp "$(dirname "$0")/../../deploy/systemd/x-ai-gateway.service" "$SERVICE_FILE"
run systemctl daemon-reload
run systemctl enable x-ai-gateway
run systemctl restart x-ai-gateway
run systemctl --no-pager status x-ai-gateway
