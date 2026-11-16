#!/bin/bash
# Benflix: keeps Tower's helper up to date by itself, so nobody has to run the setup command after an update.
# The setup command puts this on Unraid's USB stick (/boot/config/benflix/autosetup.sh) and asks Unraid's
# scheduler to run it every hour. Each time it looks at the newest helper files on GitHub, and only when
# something changed (the setup command itself or any helper file) does it run the setup command, the same
# way Ben would, with nobody typing. Nothing changes and nothing restarts when everything is already current.
# Silent (Ben 2026-11-15): it never updates while anyone is watching or using Benflix. It waits until Jellyfin
# is playing nothing and the helper hasn't been used for 15 minutes, checking again every hour.
#
# It also keeps Recyclarr's settings (Sonarr/Radarr scoring) in step with GitHub and syncs them.
#
# What it did last: /mnt/user/appdata/benflix-games/autosetup.log (and autosetup-last.txt, the full output).
# To turn it off: delete /boot/config/plugins/dynamix/benflix-autosetup.cron and run update_cron.

RAW=https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server
APP=/mnt/user/appdata/benflix-games
LOG=$APP/autosetup.log
TMP=/tmp/benflix-autosetup

# Only one at a time
exec 9>/tmp/benflix-autosetup.lock
flock -n 9 || exit 0
# The very first setup has to be done by hand (it asks for the RomM login)
[ -f "$APP/helper.env" ] || exit 0

note() {
  echo "$(date '+%Y-%m-%d %H:%M') $1" >>"$LOG"
  # Keep the log short
  tail -n 200 "$LOG" >"$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
}

rm -rf "$TMP"
mkdir -p "$TMP"

# Recyclarr (Sonarr and Radarr scoring, Ben 2026-11-16): new settings on GitHub are synced straight away.
# A sync only changes download scores, so it never bothers anyone watching and doesn't wait.
RC=/mnt/user/appdata/recyclarr
if [ -f "$RC/recyclarr.yml" ] && docker ps --format '{{.Names}}' | grep -qx recyclarr; then
  if curl -fsSL -m 60 "$RAW/recyclarr/recyclarr.yml" -o "$TMP/recyclarr.yml" && grep -q "^radarr:" "$TMP/recyclarr.yml" &&
    ! cmp -s "$TMP/recyclarr.yml" "$RC/recyclarr.yml"; then
    cp "$TMP/recyclarr.yml" "$RC/recyclarr.yml" && chown 99:100 "$RC/recyclarr.yml"
    if docker exec recyclarr recyclarr sync >"$RC/sync-last.txt" 2>&1; then
      note "Recyclarr: new settings from GitHub synced to Sonarr and Radarr"
    else
      note "Recyclarr: PROBLEM syncing the new settings. See $RC/sync-last.txt"
    fi
  fi
fi

# No internet or GitHub is down: try again next hour
curl -fsSL -m 60 "$RAW/benflix-games-helper-setup.sh" -o "$TMP/setup.sh" || exit 0
grep -q "All done" "$TMP/setup.sh" || exit 0

why=""
if ! cmp -s "$TMP/setup.sh" "$APP/setup-last.sh"; then
  why="the setup command changed"
else
  files=$(sed -n 's/^for f in \(benflix-games-helper\.py[^;]*\); do$/\1/p' "$TMP/setup.sh" | head -1)
  [ -n "$files" ] || exit 0
  for f in $files; do
    curl -fsSL -m 60 "$RAW/$f" -o "$TMP/$f" || exit 0
    if ! cmp -s "$TMP/$f" "$APP/$f"; then
      why="$f changed"
      break
    fi
  done
fi
[ -n "$why" ] || exit 0

# Never while someone is watching or using Benflix: try again next hour
answer=$(curl -fs -m 20 http://127.0.0.1:8097/busy 2>/dev/null)
if [ -n "$answer" ]; then
  if ! echo "$answer" | grep -q '"busy": false'; then
    note "Update waiting: $(echo "$answer" | sed -n 's/.*"why": "\([^"]*\)".*/\1/p')"
    exit 0
  fi
  # Jellyfin couldn't be asked (Ben hasn't opened Benflix since Tower started): only in the middle of the night
  if echo "$answer" | grep -q '"jellyfin": false'; then
    hour=$(date +%-H)
    if [ "$hour" -lt 3 ] || [ "$hour" -ge 6 ]; then
      note "Update waiting for the night (can't see if anyone is watching)"
      exit 0
    fi
  fi
elif curl -fs -m 10 http://127.0.0.1:8097/health >/dev/null 2>&1; then
  # An older helper that can't say yet: only in the middle of the night
  hour=$(date +%-H)
  if [ "$hour" -lt 3 ] || [ "$hour" -ge 6 ]; then
    note "Update waiting for the night"
    exit 0
  fi
fi

note "Updating Tower's helper ($why)"
if bash "$TMP/setup.sh" </dev/null >"$APP/autosetup-last.txt" 2>&1; then
  note "  ok  done"
else
  note "  PROBLEM  the setup command didn't finish. See autosetup-last.txt"
fi
# Remembered even when it failed, so a broken update doesn't restart the helper every hour
cp "$TMP/setup.sh" "$APP/setup-last.sh"
rm -rf "$TMP"
