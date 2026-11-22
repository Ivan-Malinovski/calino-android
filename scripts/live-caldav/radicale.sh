#!/usr/bin/env bash
#
# A throwaway CalDAV/CardDAV server for live tests.
#
# Calino's live tests and its emulator checks both need a real server. This
# runs Radicale on the developer's machine, in its own virtualenv, with its own
# storage under scripts/live-caldav/state/ -- none of which is committed, and
# all of which can be deleted at any time.
#
# It listens over plain HTTP on purpose. The alternative is a self-signed
# certificate that an emulator with a locked bootloader will not trust without
# rooting it, and the debug build already permits cleartext to 10.0.2.2 and
# localhost and nothing else (app/src/debug/res/xml/network_security_config.xml).
# The credentials below are throwaway and local; never point a live test at a
# real calendar.
#
# Usage:
#   scripts/live-caldav/radicale.sh start     # install if needed, then serve
#   scripts/live-caldav/radicale.sh stop
#   scripts/live-caldav/radicale.sh status
#   scripts/live-caldav/radicale.sh reset     # wipe the stored calendars
#   scripts/live-caldav/radicale.sh env       # print the CALINO_CALDAV_* exports
#
# From the emulator the server is  http://10.0.2.2:5232/
# From this machine it is          http://127.0.0.1:5232/

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE="$HERE/state"
VENV="$STATE/venv"
DATA="$STATE/collections"
CONF="$STATE/radicale.conf"
USERS="$STATE/users"
LOG="$STATE/radicale.log"
PIDFILE="$STATE/radicale.pid"

PORT="${CALINO_RADICALE_PORT:-5232}"
USER_NAME="${CALINO_RADICALE_USER:-calino}"
PASSWORD="${CALINO_RADICALE_PASS:-calinopass}"

install_server() {
    mkdir -p "$STATE" "$DATA"
    if [ ! -x "$VENV/bin/python" ]; then
        echo "Creating virtualenv in $VENV"
        python3 -m venv "$VENV"
    fi
    if ! "$VENV/bin/python" -c "import radicale" >/dev/null 2>&1; then
        echo "Installing Radicale"
        "$VENV/bin/pip" install --quiet --upgrade pip
        "$VENV/bin/pip" install --quiet radicale bcrypt
    fi
    "$VENV/bin/python" - "$USER_NAME" "$PASSWORD" > "$USERS" <<'PY'
import bcrypt, sys
user, password = sys.argv[1], sys.argv[2].encode()
print(user + ":" + bcrypt.hashpw(password, bcrypt.gensalt()).decode())
PY
    cat > "$CONF" <<EOF
[server]
hosts = 0.0.0.0:$PORT

[auth]
type = htpasswd
htpasswd_filename = $USERS
htpasswd_encryption = bcrypt

[storage]
filesystem_folder = $DATA

# Any authenticated account can read and write any collection, which is what
# makes a "shared with me" collection testable: on this server a collection
# outside a user's own home set is otherwise invisible to them, and Calino's
# discovery of shared collections is exactly what needs exercising.
[rights]
type = authenticated
EOF
}

running_pid() {
    [ -f "$PIDFILE" ] || return 1
    local pid
    pid="$(cat "$PIDFILE")"
    [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && printf '%s' "$pid"
}

start() {
    if running_pid >/dev/null; then
        echo "Already running (pid $(running_pid)) on port $PORT"
        return 0
    fi
    install_server
    # setsid so the server outlives the shell that started it, which matters
    # when an agent or a script starts it and then moves on.
    setsid nohup "$VENV/bin/python" -m radicale --config "$CONF" \
        > "$LOG" 2>&1 < /dev/null &
    echo $! > "$PIDFILE"
    for _ in $(seq 1 40); do
        if curl -s -o /dev/null "http://127.0.0.1:$PORT/" 2>/dev/null; then
            ensure_address_book
            ensure_fixtures
            echo "Radicale ready on http://127.0.0.1:$PORT/ (pid $(cat "$PIDFILE"))"
            echo "From the emulator: http://10.0.2.2:$PORT/"
            return 0
        fi
        sleep 0.25
    done
    echo "Radicale did not become ready; see $LOG" >&2
    tail -20 "$LOG" >&2 || true
    return 1
}

stop() {
    local pid
    if pid="$(running_pid)"; then
        kill "$pid"
        rm -f "$PIDFILE"
        echo "Stopped (pid $pid)"
    else
        rm -f "$PIDFILE"
        echo "Not running"
    fi
}

status() {
    local pid
    if pid="$(running_pid)"; then
        echo "Running (pid $pid) on http://127.0.0.1:$PORT/"
        curl -s -o /dev/null -w 'auth check: %{http_code}\n' \
            -u "$USER_NAME:$PASSWORD" "http://127.0.0.1:$PORT/"
    else
        echo "Not running"
        return 1
    fi
}

# CardDAV needs an address book to discover. A freshly wiped server has none,
# so the live CardDAV test failed on a bare harness with "expected at least one
# address book" -- a precondition, not a bug in the client. Create it on start so
# a clean checkout can run the whole live suite.
ensure_address_book() {
    local book="http://127.0.0.1:$PORT/$USER_NAME/contacts/"
    local probe
    probe="$(curl -s -o /dev/null -w '%{http_code}' -u "$USER_NAME:$PASSWORD" -X PROPFIND "$book" \
        -H 'Depth: 0' -H 'Content-Type: application/xml' --data \
        '<propfind xmlns="DAV:"><prop><resourcetype/></prop></propfind>')"
    case "$probe" in
        207|200) return 0 ;;
    esac
    curl -s -o /dev/null -u "$USER_NAME:$PASSWORD" -X MKCOL "$book" \
        -H 'Content-Type: application/xml' --data \
        '<mkcol xmlns="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav"><set><prop><resourcetype><collection/><card:addressbook/></resourcetype><displayname>Contacts</displayname></prop></set></mkcol>'
}

# The live tests read more than they write, and two of them need content that a
# wiped harness does not have: a repeating series (recurrence is expanded on the
# client, so a series has to reach more than one date) and a contact. Seed both,
# idempotently, with dates relative to today so the fetch window always covers
# them.
ensure_fixtures() {
    local day0 base
    day0="$(date -u +%Y%m%d)"
    base="http://127.0.0.1:$PORT/$USER_NAME"
    curl -s -o /dev/null -u "$USER_NAME:$PASSWORD" -X PUT \
        -H 'Content-Type: text/calendar' --data-binary \
        "BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Calino//live harness//EN
BEGIN:VEVENT
UID:live-recurring-series@calino
DTSTAMP:${day0}T090000Z
DTSTART;TZID=Europe/Copenhagen:${day0}T100000
DTEND;TZID=Europe/Copenhagen:${day0}T110000
RRULE:FREQ=WEEKLY;COUNT=3
SUMMARY:Live recurring series
END:VEVENT
END:VCALENDAR" "$base/personal/live-recurring-series.ics"
    curl -s -o /dev/null -u "$USER_NAME:$PASSWORD" -X PUT \
        -H 'Content-Type: text/vcard' --data-binary \
        "BEGIN:VCARD
VERSION:3.0
UID:live-contact@calino
FN:Live Contact
N:Contact;Live;;;
EMAIL:live.contact@example.com
END:VCARD" "$base/contacts/live-contact.vcf"
}

reset_collections() {
    stop || true
    rm -rf "$DATA"
    mkdir -p "$DATA"
    echo "Collections wiped: $DATA"
}

print_env() {
    echo "export CALINO_CALDAV_URL=http://127.0.0.1:$PORT/"
    echo "export CALINO_CALDAV_USER=$USER_NAME"
    echo "export CALINO_CALDAV_PASS=$PASSWORD"
}

case "${1:-start}" in
    start) start ;;
    stop) stop ;;
    status) status ;;
    reset) reset_collections ;;
    env) print_env ;;
    *) echo "Usage: $0 {start|stop|status|reset|env}" >&2; exit 2 ;;
esac
