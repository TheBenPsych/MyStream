#!/bin/bash
# Benflix: small helper for Tdarr on Tower. setup-tdarr.sh puts it on the USB stick and Unraid runs it:
#   every 2 minutes ("pause"): while someone is watching Benflix, Tdarr starts nothing new
#                              (the file it's on finishes). It carries on once everyone stops.
#   every 2 minutes ("harbor"): talks with Harbor's Shrinker page through small files in the Tdarr folder:
#                              writes benflix-status.json, does what Harbor asked in harbor/actions.txt
#                              (put an original back, un-pause, release held files, retry failed ones,
#                              run the setup again) and re-runs the setup when Harbor saved new settings.
#   once ("recheck"):          after new size goals, files left alone before go back in the queue.
#   every hour ("hourly"):     deletes backups older than BACKUP_DAYS, counts the files in Tdarr for Harbor,
#                              and re-runs the Tdarr setup by itself when the files on GitHub changed.
# Log: /mnt/user/appdata/tdarr/benflix-watch.log
# To turn it off: delete /boot/config/plugins/dynamix/benflix-tdarr.cron and run update_cron.

. /boot/config/benflix/tdarr-watch.env 2>/dev/null
BACKUP=${BACKUP:-/mnt/user/Homelab/tdarr-backup}
BACKUP_DAYS=${BACKUP_DAYS:-3}
RAW=${RAW:-https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/tdarr}
APP=${APP:-/mnt/user/appdata/tdarr}
API=http://127.0.0.1:8265/api/v2
LOG=$APP/benflix-watch.log
MEDIA=${MEDIA:-/mnt/user/Homelab/Media}
FLAG=$APP/benflix-paused-for-watching   # holds why this watcher paused Tdarr
HARBOR=$APP/harbor                       # what Harbor writes (it runs as "nobody", 99:100)
SKIP=$HARBOR/skip-night                 # "start end" (seconds): Harbor's Skip tonight

note() {
  echo "$(date '+%Y-%m-%d %H:%M') $1" >>"$LOG"
  tail -n 300 "$LOG" >"$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
}

exec 9>"/tmp/benflix-tdarr-watch-$1.lock"
flock -n 9 || exit 0
RUNNING=false
[ "$(docker inspect -f '{{.State.Running}}' tdarr 2>/dev/null)" = "true" ] && RUNNING=true
[ "$RUNNING" = true ] || [ "$1" = "harbor" ] || exit 0

jesc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

# Runs a small script inside Tdarr that reads or changes its file list (too big for curl + grep).
#   tdarr_files count             -> {"Queued":123,"Hold":4,...}
#   tdarr_files requeue <status>  -> how many it set back to Queued
#   tdarr_files forget <path>     -> forgets one file, so the next scan reads it fresh
tdarr_files() {
  docker exec -i tdarr node -e '
const h=require("http");
const call=(body)=>new Promise((ok,no)=>{const q=h.request({host:"127.0.0.1",port:8265,path:"/api/v2/cruddb",method:"POST",
  headers:{"content-type":"application/json"}},s=>{let d="";s.on("data",c=>d+=c);s.on("end",()=>{try{ok(JSON.parse(d||"null"))}catch(e){ok(null)}})});
  q.on("error",no);q.end(JSON.stringify({data:body}));});
(async()=>{const [mode,arg]=process.argv.slice(1);
  if(mode==="forget"){await call({collection:"FileJSONDB",mode:"removeOne",docID:arg});console.log("ok");return;}
  const all=await call({collection:"FileJSONDB",mode:"getAll"})||[];
  if(mode==="count"){const c={};for(const f of all){const k=f.TranscodeDecisionMaker||"Not checked";c[k]=(c[k]||0)+1;}console.log(JSON.stringify(c));return;}
  if(mode==="requeue"){let n=0;for(const f of all){if(f.TranscodeDecisionMaker===arg){
    await call({collection:"FileJSONDB",mode:"update",docID:f._id,obj:{TranscodeDecisionMaker:"Queued"}});n++;}}console.log(n);}
})().catch(e=>{console.error(e.message);process.exit(1)});' "$@" 2>/dev/null
}

save_counts() {
  local c
  c=$(tdarr_files count) || return
  [ -n "$c" ] || return
  printf '{"at":"%s","files":%s}\n' "$(date -Iseconds)" "$c" >"$APP/benflix-counts.json.tmp" \
    && mv "$APP/benflix-counts.json.tmp" "$APP/benflix-counts.json"
}

# Runs the setup again in the background, fresh from GitHub, the same way Ben would
run_setup() { # $1 = why
  note "Running the Tdarr setup again ($1)"
  (
    flock -n 8 || exit 0
    t=$(mktemp)
    if curl -fsSL -m 60 "$RAW/setup-tdarr.sh" -o "$t" && bash "$t" </dev/null >"$APP/benflix-setup-last.txt" 2>&1; then
      note "  ok  done"
    else
      note "  PROBLEM  the Tdarr setup didn't finish, see benflix-setup-last.txt"
    fi
    rm -f "${t:?}"
  ) 8>/tmp/benflix-tdarr-setup.lock </dev/null >/dev/null 2>&1 &
}

set_paused() { # $1 = true or false
  local node
  node=$(curl -fs -m 10 "$API/get-nodes" | sed -n 's/^{"\([^"]*\)".*/\1/p')
  [ -n "$node" ] || return 1
  curl -fs -m 10 -X POST "$API/update-node" -H 'content-type: application/json' \
    -d "{\"data\":{\"nodeID\":\"$node\",\"nodeUpdates\":{\"nodePaused\":$1}}}" >/dev/null
}

if [ "$1" = "pause" ]; then
  why=""
  # Skip tonight (from Harbor): "start end" in seconds
  now=$(date +%s)
  if read -r s e <"$SKIP" 2>/dev/null; then
    if [[ "$s" =~ ^[0-9]+$ ]] && [[ "$e" =~ ^[0-9]+$ ]] && [ "$now" -lt "$e" ]; then
      [ "$now" -ge "$s" ] && why="Skipping tonight (asked in Harbor)"
    else
      rm -f "${SKIP:?}"
    fi
  fi
  answer=$(curl -fs -m 20 http://127.0.0.1:8097/busy 2>/dev/null)
  # Only real watching counts (something playing, or Jellyfin not answering). Just having the app open
  # doesn't: a TV left on the Home screen kept Tdarr paused most of the night (2026-11-18).
  if [ -z "$why" ] && echo "$answer" | grep -q '"busy": true' && echo "$answer" | grep -qiE 'playing|answer'; then
    why=$(echo "$answer" | sed -n 's/.*"why": "\([^"]*\)".*/\1/p')
    why=${why:-Someone is watching}
  fi
  [ -n "$answer" ] || [ -n "$why" ] || exit 0
  if [ -n "$why" ]; then
    if [ ! -f "$FLAG" ] && set_paused true; then
      echo "$why" >"$FLAG"
      note "Paused: $why"
    elif [ -f "$FLAG" ] && [ "$(cat "$FLAG")" != "$why" ]; then
      echo "$why" >"$FLAG"
    fi
  elif [ -f "$FLAG" ]; then
    # Only un-pause what this watcher paused (a pause Ben made by hand stays)
    set_paused false && rm -f "${FLAG:?}" && note "Carrying on (nobody watching)"
  fi
  exit 0
fi

if [ "$1" = "harbor" ]; then
  # 1. New settings saved in Harbor? Use them.
  if [ -f "$HARBOR/settings.env" ] || [ -f "$APP/benflix-installed/settings.env" ]; then
    if ! cmp -s "$HARBOR/settings.env" "$APP/benflix-installed/settings.env"; then
      mkdir -p "$APP/benflix-installed"
      if [ -f "$HARBOR/settings.env" ]; then cp "$HARBOR/settings.env" "$APP/benflix-installed/settings.env"
      else rm -f "${APP:?}/benflix-installed/settings.env"; fi
      run_setup "new settings from Harbor"
    fi
  fi

  # 2. What Harbor asked for (one line each), taken in one go so nothing is done twice
  if [ -s "$HARBOR/actions.txt" ] && mv "$HARBOR/actions.txt" "$APP/benflix-actions.work"; then
    while IFS= read -r line; do
      line=${line%$'\r'}
      case "$line" in
        "putback /media/"*)
          path=${line#putback }
          rel=${path#/media/}
          case "$rel" in *..*|"") note "Couldn't put back $rel (odd name)"; continue ;; esac
          src="${BACKUP:?}/${rel:?}"
          dst="${MEDIA:?}/${rel:?}"
          if [ ! -f "$src" ]; then note "Couldn't put back $rel: the original is gone (kept $BACKUP_DAYS days)"; continue; fi
          if cp "$src" "$dst.benflix-restore" && mv -f "$dst.benflix-restore" "$dst"; then
            chown 99:100 "$dst"; chmod 666 "$dst" 2>/dev/null
            grep -qxF "$path" "$BACKUP/benflix-keep.txt" 2>/dev/null || echo "$path" >>"$BACKUP/benflix-keep.txt"
            chown 99:100 "$BACKUP/benflix-keep.txt"; chmod 664 "$BACKUP/benflix-keep.txt"
            rm -f "$src"
            if [ -f "$BACKUP/shrink-log.txt" ]; then
              grep -vF "  $path" "$BACKUP/shrink-log.txt" >"$BACKUP/shrink-log.tmp"
              cat "$BACKUP/shrink-log.tmp" >"$BACKUP/shrink-log.txt"
              rm -f "${BACKUP:?}/shrink-log.tmp"
            fi
            [ "$RUNNING" = true ] && tdarr_files forget "$path" >/dev/null
            note "Put back the original: $rel"
          else
            rm -f "$dst.benflix-restore"; note "Couldn't put back $rel (copy failed)"
          fi
          ;;
        "fix unpause")
          [ "$RUNNING" = true ] && set_paused false && rm -f "${FLAG:?}" "${SKIP:?}" && note "Carrying on (un-paused in Harbor)" ;;
        "fix hold")
          [ "$RUNNING" = true ] && note "Released $(tdarr_files requeue Hold) files on hold (asked in Harbor)" && save_counts ;;
        "fix failed")
          [ "$RUNNING" = true ] && note "Trying $(tdarr_files requeue 'Transcode error') failed files again (asked in Harbor)" && save_counts ;;
        "fix setup")
          run_setup "asked in Harbor" ;;
      esac
    done <"$APP/benflix-actions.work"
    rm -f "${APP:?}/benflix-actions.work"
  fi

  # 3. How things are, for Harbor
  nodes=""; answering=false
  if [ "$RUNNING" = true ] && nodes=$(curl -fs -m 10 "$API/get-nodes" 2>/dev/null) && [ -n "$nodes" ]; then answering=true; fi
  node_paused=$(printf '%s' "$nodes" | grep -o '"nodePaused":[a-z]*' | head -1 | cut -d: -f2)
  gpu_do_cpu=$(printf '%s' "$nodes" | grep -o '"allowGpuDoCpu":[a-z]*' | head -1 | cut -d: -f2)
  gpu_workers=$(printf '%s' "$nodes" | grep -o '"workerLimits":{[^}]*}' | head -1 | sed -n 's/.*"transcodegpu":\([0-9]*\).*/\1/p')
  setup_ok=null
  if [ -f "$APP/benflix-setup-last.txt" ]; then
    if grep -q "All done" "$APP/benflix-setup-last.txt"; then setup_ok=true; else setup_ok=false; fi
  fi
  setup_busy=false
  flock -n /tmp/benflix-tdarr-setup.lock true 2>/dev/null || setup_busy=true
  {
    printf '{"at":"%s","running":%s,"answering":%s,' "$(date -Iseconds)" "$RUNNING" "$answering"
    printf '"node_paused":%s,"gpu_do_cpu":%s,"gpu_workers":%s,' "${node_paused:-null}" "${gpu_do_cpu:-null}" "${gpu_workers:-null}"
    printf '"paused_for":"%s","setup_ok":%s,"setup_busy":%s}\n' "$(jesc "$(cat "$FLAG" 2>/dev/null)")" "$setup_ok" "$setup_busy"
  } >"$APP/benflix-status.json.tmp" && mv "$APP/benflix-status.json.tmp" "$APP/benflix-status.json"
  if [ ! -f "$APP/benflix-counts.json" ] && [ "$RUNNING" = true ]; then save_counts; fi
  # Harbor runs as "nobody" and writes the actions, the settings and Skip tonight in its own folder
  mkdir -p "$HARBOR"
  chown 99:100 "$HARBOR" && chmod 775 "$HARBOR"
  chmod 644 "$APP/benflix-status.json" "$APP/benflix-counts.json" "$APP/benflix-setup.json" 2>/dev/null
  exit 0
fi

# After new size goals ("recheck", from the setup): files left alone before are looked at again tonight
if [ "$1" = "recheck" ]; then
  note "Looking again at $(tdarr_files requeue 'Not required') files that were left alone before (new size goals)"
  save_counts
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

  # Counts of the files in Tdarr (queued, on hold, failed...) for Harbor
  save_counts

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
