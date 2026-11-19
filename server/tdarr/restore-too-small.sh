#!/bin/bash
# Benflix: puts back the originals of files the Tdarr shrinker squeezed too hard, then lets Tdarr redo them
# with the newer settings (about half the size, never below the minimum). Run it on Tower (>_ Terminal):
#   bash <(curl -fsSL https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/tdarr/restore-too-small.sh)
# "Too hard" = the new file is under MIN_GB, or under MIN_SHARE of the old file. Backups only live 3 days.
MEDIA=${TDARR_MEDIA:-/mnt/user/Homelab/Media}
BACKUP=${TDARR_BACKUP:-/mnt/user/Homelab/tdarr-backup}
MIN_GB=${MIN_GB:-0.40}
MIN_SHARE=${MIN_SHARE:-0.45}
API=${TDARR_API:-http://127.0.0.1:8265/api/v2}
LOG=$BACKUP/shrink-log.txt

[ -f "$LOG" ] || { echo "No shrink log at $LOG, nothing to put back"; exit 0; }
post() { curl -fs -m 30 -X POST "$API/$1" -H 'content-type: application/json' -d "$2"; }
jesc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

done_n=0; missing=0; keep_lines=$(mktemp); libs=$(mktemp)
while IFS= read -r line; do
  # 2026-11-19 02:11  1.46 GB -> 0.16 GB  (saved 1.30 GB)  /media/Anime Shows/...
  old=$(echo "$line" | awk '{print $3}'); new=$(echo "$line" | awk '{print $6}')
  path=$(echo "$line" | sed -n 's/^.*(saved [^)]*)  //p')
  if [ -z "$path" ] || [ -z "$new" ] || ! awk -v o="$old" -v n="$new" -v g="$MIN_GB" -v s="$MIN_SHARE" \
       'BEGIN{exit !(n < g || (o > 0 && n / o < s))}'; then
    echo "$line" >>"$keep_lines"; continue
  fi
  rel=${path#/media/}
  if [ ! -f "$BACKUP/$rel" ]; then
    missing=$((missing + 1)); echo "  --  no backup any more: $rel"; echo "$line" >>"$keep_lines"; continue
  fi
  if cp "$BACKUP/$rel" "$MEDIA/$rel.benflix-restore" && mv -f "$MEDIA/$rel.benflix-restore" "$MEDIA/$rel"; then
    chown 99:100 "$MEDIA/$rel"; chmod 666 "$MEDIA/$rel" 2>/dev/null
    rm -f "$BACKUP/$rel"
    # Tdarr forgets the file, so the next scan finds it as new and shrinks it again with the new settings
    post cruddb "{\"data\":{\"collection\":\"FileJSONDB\",\"mode\":\"removeOne\",\"docID\":\"$(jesc "$path")\"}}" >/dev/null
    echo "$rel" | cut -d/ -f1 >>"$libs"
    done_n=$((done_n + 1)); echo "  ok  $old GB again: $rel"
  else
    rm -f "$MEDIA/$rel.benflix-restore"; echo "  PROBLEM  couldn't copy back: $rel"; echo "$line" >>"$keep_lines"
  fi
done <"$LOG"

cat "$keep_lines" >"$LOG"; chown 99:100 "$LOG"; chmod 664 "$LOG"
find "$BACKUP" -mindepth 1 -type d -empty -delete 2>/dev/null
# Look for the files again in each library that got some back
sort -u "$libs" | while IFS= read -r lib; do
  id="benflix_$(echo "$lib" | tr 'A-Z' 'a-z' | tr -c 'a-z0-9' '_' | sed 's/_*$//')"  # same as setup-tdarr.sh
  post scan-files "{\"data\":{\"scanConfig\":{\"dbID\":\"$id\",\"arrayOrPath\":\"/media/$(jesc "$lib")\",\"mode\":\"scanFindNew\"}}}" >/dev/null \
    && echo "  ok  Tdarr is looking at $lib again"
done
rm -f "$keep_lines" "$libs"
echo "All done: put back $done_n originals ($missing had no backup any more). Tdarr redoes them tonight at 1 AM."
