#!/bin/bash
# Benflix: small helper for Tdarr on Tower. setup-tdarr.sh puts it on the USB stick and Unraid runs it:
#   every 2 minutes ("pause"): while someone is watching Benflix, Tdarr starts nothing new
#                              (the file it's on finishes). It carries on once everyone stops.
#   every hour ("hourly"):     deletes backups older than BACKUP_DAYS, and re-runs the Tdarr setup
#                              by itself when the Benflix Tdarr files on GitHub changed.
# Log: /mnt/user/appdata/tdarr/benflix-watch.log
# To turn it off: delete /boot/config/plugins/dynamix/benflix-tdarr.cron and run update_cron.

. /boot/config/benflix/tdarr-watch.env 2>/dev/null
BACKUP=${BACKUP:-/mnt/user/Homelab/tdarr-backup}
BACKUP_DAYS=${BACKUP_DAYS:-3}
RAW=${RAW:-https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/tdarr}
APP=${APP:-/mnt/user/appdata/tdarr}
API=http://127.0.0.1:8265/api/v2
LOG=$APP/benflix-watch.log
FLAG=$APP/benflix-paused-for-watching

note() {
  echo "$(date '+%Y-%m-%d %H:%M') $1" >>"$LOG"
  tail -n 300 "$LOG" >"$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
}

exec 9>"/tmp/benflix-tdarr-watch-$1.lock"
flock -n 9 || exit 0
[ "$(docker inspect -f '{{.State.Running}}' tdarr 2>/dev/null)" = "true" ] || exit 0

set_paused() { # $1 = true or false
  local node
  node=$(curl -fs -m 10 "$API/get-nodes" | sed -n 's/^{"\([^"]*\)".*/\1/p')
  [ -n "$node" ] || return 1
  curl -fs -m 10 -X POST "$API/update-node" -H 'content-type: application/json' \
    -d "{\"data\":{\"nodeID\":\"$node\",\"nodeUpdates\":{\"nodePaused\":$1}}}" >/dev/null
}

if [ "$1" = "pause" ]; then
  answer=$(curl -fs -m 20 http://127.0.0.1:8097/busy 2>/dev/null)
  [ -n "$answer" ] || exit 0
  if echo "$answer" | grep -q '"busy": true'; then
    if [ ! -f "$FLAG" ] && set_paused true; then
      touch "$FLAG"
      note "Paused: $(echo "$answer" | sed -n 's/.*"why": "\([^"]*\)".*/\1/p')"
    fi
  elif [ -f "$FLAG" ]; then
    # Only un-pause what this watcher paused (a pause Ben made by hand stays)
    set_paused false && rm -f "$FLAG" && note "Carrying on (nobody watching)"
  fi
  exit 0
fi

if [ "$1" = "hourly" ]; then
  # Old backups go
  if [ -d "$BACKUP" ]; then
    gone=$(find "$BACKUP" -type f ! -name 'shrink-log.txt' -mmin +$((BACKUP_DAYS * 1440)) -print -delete 2>/dev/null | wc -l)
    find "$BACKUP" -mindepth 1 -type d -empty -delete 2>/dev/null
    [ "$gone" -gt 0 ] && note "Deleted $gone backups older than $BACKUP_DAYS days"
    if [ -f "$BACKUP/shrink-log.txt" ]; then
      tail -n 3000 "$BACKUP/shrink-log.txt" >"$BACKUP/shrink-log.tmp" && mv "$BACKUP/shrink-log.tmp" "$BACKUP/shrink-log.txt"
      # Tdarr writes the log as "nobody", so it must stay theirs (root made it unwritable, 2026-11-17)
      chown 99:100 "$BACKUP/shrink-log.txt" && chmod 664 "$BACKUP/shrink-log.txt"
    fi
  fi

  # Anything new on GitHub? Then run the setup again, the same way Ben would
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  curl -fsSL -m 60 "$RAW/setup-tdarr.sh" -o "$TMP/setup-tdarr.sh" || exit 0
  grep -q "All done" "$TMP/setup-tdarr.sh" || exit 0
  changed=""
  cmp -s "$TMP/setup-tdarr.sh" "$APP/benflix-installed/setup-tdarr.sh" || changed="setup-tdarr.sh"
  if [ -z "$changed" ]; then
    for f in benflix-shrink-flow.json tdarr-watch.sh; do
      curl -fsSL -m 60 "$RAW/$f" -o "$TMP/$f" || exit 0
      cmp -s "$TMP/$f" "$APP/benflix-installed/$f" || { changed=$f; break; }
    done
  fi
  if [ -z "$changed" ]; then
    for p in $(sed -n 's/^PLUGINS="\(.*\)"$/\1/p' "$TMP/setup-tdarr.sh"); do
      curl -fsSL -m 60 "$RAW/plugins/benflix/$p/1.0.0/index.js" -o "$TMP/$p.js" || exit 0
      cmp -s "$TMP/$p.js" "$APP/benflix-installed/$p.js" || { changed=$p; break; }
    done
  fi
  [ -n "$changed" ] || exit 0
  note "Updating the Tdarr setup ($changed changed)"
  if bash "$TMP/setup-tdarr.sh" </dev/null >"$APP/benflix-setup-last.txt" 2>&1; then
    note "  ok  done"
  else
    note "  PROBLEM  the Tdarr setup didn't finish, see benflix-setup-last.txt"
    # Remember it anyway so a broken update isn't retried every hour
    mkdir -p "$APP/benflix-installed" && cp "$TMP/setup-tdarr.sh" "$APP/benflix-installed/setup-tdarr.sh"
  fi
fi
