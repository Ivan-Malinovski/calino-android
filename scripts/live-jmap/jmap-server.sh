#!/usr/bin/env bash
# A throwaway local Stalwart for Calino's JMAP live tests and emulator checks.
# Rootless podman, bound to 127.0.0.1. The image keeps its state in /etc/stalwart and
# /var/lib/stalwart, so those are named volumes (<name>-etc, <name>-lib) that `down`
# keeps; the generated admin secret lives in scripts/live-jmap/state (git-ignored).
#
#   scripts/live-jmap/jmap-server.sh up      start (and bootstrap on first run)
#   scripts/live-jmap/jmap-server.sh down    stop and remove the container, keep its volumes
#   scripts/live-jmap/jmap-server.sh reset   stop, delete its volumes and secret, start fresh
#   scripts/live-jmap/jmap-server.sh env     print the CALINO_JMAP_* exports
#
# Overrides: CALINO_JMAP_PORT (18080), CALINO_JMAP_NAME (calino-stalwart),
# CALINO_JMAP_IMAGE, CALINO_JMAP_DATA. The two users are fixed, local-only values.
set -euo pipefail

PORT="${CALINO_JMAP_PORT:-18080}"
NAME="${CALINO_JMAP_NAME:-calino-stalwart}"
IMAGE="${CALINO_JMAP_IMAGE:-docker.io/stalwartlabs/stalwart:latest}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA="${CALINO_JMAP_DATA:-$HERE/state}"
DOMAIN="example.org"
# Pinned password of the temporary bootstrap admin. Stalwart replaces it with a
# generated permanent admin at the end of bootstrap and returns that secret only
# once, so the script keeps it in $DATA/admin-secret (mode 600, git-ignored).
RECOVERY_PASSWORD="calino-dev-recovery"
ADMIN_SECRET_FILE="$DATA/admin-secret"
USER_NAME="alice"
USER_PASSWORD="calino-dev-alice"
USER2_NAME="bob"
USER2_PASSWORD="calino-dev-bob"
BASE="http://127.0.0.1:$PORT"
USING='["urn:ietf:params:jmap:core","urn:stalwart:jmap"]'

# JMAP call as the temporary bootstrap admin or the permanent one.
jmap() { # <user> <password> <using-json-array> <methodCalls-json>
  curl -sf -u "$1:$2" -H 'Content-Type: application/json' "$BASE/jmap/" \
    -d "{\"using\":$3,\"methodCalls\":$4}"
}

wait_http() {
  for _ in $(seq 1 60); do
    curl -s -o /dev/null "$BASE/.well-known/jmap" && return 0
    sleep 1
  done
  echo "Stalwart did not answer on $BASE" >&2
  return 1
}

running() { podman container exists "$NAME" 2>/dev/null; }

up() {
  df -h "$HERE" | tail -1
  mkdir -p "$DATA"
  if ! running; then
    podman run -d --name "$NAME" \
      -p "127.0.0.1:$PORT:8080" \
      -e "STALWART_RECOVERY_ADMIN=admin:$RECOVERY_PASSWORD" \
      -v "$NAME-etc:/etc/stalwart" -v "$NAME-lib:/var/lib/stalwart" "$IMAGE" >/dev/null
  else
    podman start "$NAME" >/dev/null 2>&1 || true
  fi
  wait_http

  # A fresh server starts in bootstrap mode; finish it once. The secret file alone does
  # not prove it was finished (the volumes may be gone), so check that it still logs in.
  if [ ! -s "$ADMIN_SECRET_FILE" ] ||
    ! curl -sf -o /dev/null -u "admin@$DOMAIN:$(<"$ADMIN_SECRET_FILE")" "$BASE/jmap/session"; then
    echo "Completing Stalwart bootstrap..."
    local reply
    reply=$(jmap admin "$RECOVERY_PASSWORD" "$USING" \
      "[[\"x:Bootstrap/set\",{\"update\":{\"singleton\":{\"serverHostname\":\"localhost\",\"defaultDomain\":\"$DOMAIN\",\"requestTlsCertificate\":false,\"generateDkimKeys\":false}}},\"a\"]]")
    (
      umask 077
      printf '%s' "$reply" | python3 -I -c 'import json,sys
print(json.load(sys.stdin)["methodResponses"][0][1]["updated"]["singleton"]["secret"])' >"$ADMIN_SECRET_FILE"
    )
    podman restart "$NAME" >/dev/null
    wait_http
  fi
  ensure_user "$USER_NAME" "$USER_PASSWORD"
  ensure_user "$USER2_NAME" "$USER2_PASSWORD"
  print_env
}

# Create a test user if it does not exist yet. Idempotent.
ensure_user() { # <name> <password>
  local name="$1" password="$2"
  local admin="admin@$DOMAIN"
  local admin_password accounts
  admin_password=$(<"$ADMIN_SECRET_FILE")
  accounts=$(jmap "$admin" "$admin_password" "$USING" '[["x:Account/get",{"ids":null},"a"]]')
  local existing
  existing=$(printf '%s' "$accounts" | python3 -I -c 'import json,sys
n=sys.argv[1]
d=json.load(sys.stdin)["methodResponses"][0][1]["list"]
print(any(a["name"]==n for a in d))' "$name")
  if [ "$existing" = "True" ]; then return 0; fi
  local domain_id
  domain_id=$(printf '%s' "$accounts" | python3 -I -c 'import json,sys
d=json.load(sys.stdin)["methodResponses"][0][1]["list"]
print(next(a["domainId"] for a in d if a["name"]=="admin"))')
  echo "Creating test user $name@$DOMAIN..."
  jmap "$admin" "$admin_password" "$USING" \
    "[[\"x:Account/set\",{\"create\":{\"u\":{\"@type\":\"User\",\"name\":\"$name\",\"domainId\":\"$domain_id\",\"credentials\":{\"0\":{\"@type\":\"Password\",\"secret\":\"$password\"}}}}},\"a\"]]" >/dev/null
}

print_env() {
  cat <<EOF
export CALINO_JMAP_URL=$BASE
export CALINO_JMAP_USER=$USER_NAME@$DOMAIN
export CALINO_JMAP_PASS=$USER_PASSWORD
export CALINO_JMAP_USER2=$USER2_NAME@$DOMAIN
export CALINO_JMAP_PASS2=$USER2_PASSWORD
EOF
}

case "${1:-}" in
up) up ;;
down) podman rm -f "$NAME" >/dev/null 2>&1 || true ;;
reset)
  podman rm -f "$NAME" >/dev/null 2>&1 || true
  podman volume rm -f "$NAME-etc" "$NAME-lib" >/dev/null 2>&1 || true
  rm -rf "$DATA"
  up
  ;;
env) print_env ;;
*)
  sed -n '2,11p' "$0"
  exit 1
  ;;
esac
