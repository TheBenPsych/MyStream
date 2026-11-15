#!/bin/bash
# Benflix: after Tower gets a new home address (a move or a new router), swap the old address for the new one
# inside every app's saved settings (Radarr, Prowlarr, Bazarr, Lidarr, Chaptarr, Trailarr, Seerr, Tdarr, ...).
# Each changed file is backed up next to itself as <name>.before-move first. Apps are stopped while their
# settings change, then started again.
#
# Run on the Unraid server (Terminal):
#   curl -fsSL https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/benflix-move-address.sh | OLD=10.0.0.58 bash

OLD=${OLD:-10.0.0.58}
NEW=${NEW:-$(ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p')}
APPDATA=/mnt/user/appdata

if [ -z "$NEW" ] || [ "$NEW" = "$OLD" ]; then echo "PROBLEM  Couldn't tell Tower's new address. Send this to Claude."; exit 1; fi
echo "Changing $OLD to $NEW in the apps' settings"

# Settings files that mention the old address (logs, backups and Benflix's own files are left alone)
mapfile -t FILES < <(find "$APPDATA" -maxdepth 3 -type f \( -name '*.json' -o -name '*.xml' -o -name '*.yaml' -o -name '*.yml' \
  -o -name '*.ini' -o -name '*.conf' -o -name '*.db' -o -name '*.sqlite' \) -size -200M \
  -not -path '*/backups/*' -not -path '*/Backups/*' -not -path '*/logs/*' -not -path '*/log/*' \
  -not -name 'logs.db' -not -name 'events.db' -not -name '*.before-move' \
  -not -path "$APPDATA/Jellyfin/*" -not -path "$APPDATA/benflix-*" 2>/dev/null | xargs -r grep -laF "$OLD" 2>/dev/null)
if [ ${#FILES[@]} -eq 0 ]; then echo "ok  Nothing uses $OLD any more."; exit 0; fi

# The apps those files belong to (any container with a folder from the same appdata folder)
DIRS=$(printf '%s\n' "${FILES[@]}" | cut -d/ -f5 | sort -u)
APPS=""
for c in $(docker ps -a --format '{{.Names}}'); do
  srcs=$(docker inspect -f '{{range .Mounts}}{{.Source}} {{end}}' "$c")
  for d in $DIRS; do
    case " $srcs" in *" $APPDATA/$d"*) APPS="$APPS $c"; break ;; esac
  done
done
RUNNING=""
for c in $APPS; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$c")" = true ]; then RUNNING="$RUNNING $c"; fi
done
echo "Stopping for a moment:$APPS"
for c in $RUNNING; do docker stop -t 30 "$c" >/dev/null; done

DBS=()
for f in "${FILES[@]}"; do
  for x in "$f" "$f-wal" "$f-shm"; do [ -f "$x" ] && [ ! -f "$x.before-move" ] && cp -p "$x" "$x.before-move"; done
  if head -c 15 "$f" | grep -q "SQLite format 3"; then
    DBS+=("$f")
  else
    sed -i "s/${OLD//./\\.}/$NEW/g" "$f" && echo "  ok  $f"
  fi
done

if [ ${#DBS[@]} -gt 0 ]; then
  PY=$(mktemp /tmp/benflix-move-XXXX.py)
  cat >"$PY" <<'PYEOF'
import sqlite3, sys
old, new = sys.argv[1], sys.argv[2]
for path in sys.argv[3:]:
    n = 0
    try:
        db = sqlite3.connect(path)
        tables = [r[0] for r in db.execute("select name from sqlite_master where type = 'table' and name not like 'sqlite_%'")]
        for t in tables:
            try:
                cols = [r[1] for r in db.execute('pragma table_info("%s")' % t)]
            except Exception:
                continue
            for c in cols:
                try:
                    cur = db.execute(
                        'update "%s" set "%s" = replace("%s", ?, ?) where typeof("%s") = \'text\' and instr("%s", ?) > 0'
                        % (t, c, c, c, c),
                        (old, new, old),
                    )
                    n += cur.rowcount
                except Exception:
                    pass
        db.commit()
        db.close()
        print("  ok  %s (%d changed)" % (path, n))
    except Exception as e:
        print("  PROBLEM  %s: %s" % (path, e))
PYEOF
  docker run --rm -v "$APPDATA:$APPDATA" -v "$PY:/move.py:ro" python:3.12-alpine python /move.py "$OLD" "$NEW" "${DBS[@]}"
  rm -f "$PY"
fi

echo "Starting again:$RUNNING"
for c in $RUNNING; do docker start "$c" >/dev/null || echo "  PROBLEM  $c didn't start"; done
echo
echo "All done. Old copies are kept next to each file as .before-move (safe to delete in a week)."
