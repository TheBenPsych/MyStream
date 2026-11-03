#!/bin/bash
# Benflix games helper: lets Benflix play your RomM games right on the TV or phone.
# The helper sends each game's files, your BIOS files and your saves to Benflix, and keeps the saves
# (one folder per Jellyfin account) in Games/saves. Safe to run again any time.
#
# Run on the Unraid server (Tools > Terminal):
#   curl -fsSL https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/benflix-games-helper-setup.sh | bash

GAMES=/mnt/user/Homelab/Games
ROMS=$GAMES/roms
BIOS=$GAMES/bios
SAVES=$GAMES/saves
APP=/mnt/user/appdata/benflix-games
OLD=/mnt/user/appdata/wolf/benflix
RAW=https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server

say() { printf '\n== %s ==\n' "$1"; }

mkdir -p "$APP" "$BIOS" "$SAVES"
chmod 777 "$SAVES"
HOST_IP=$(ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p')
[ -n "$HOST_IP" ] || HOST_IP=10.0.0.58

say "Downloading the helper"
if curl -fsSL "$RAW/benflix-games-helper.py" -o "$APP/benflix-games-helper.py"; then echo "  ok"; else
  echo "  PROBLEM  Couldn't download it. Check the server has internet."
  exit 1
fi

say "RomM login for the helper"
ENV=$APP/helper.env
if [ ! -f "$ENV" ] && [ -f "$OLD/helper.env" ]; then
  cp "$OLD/helper.env" "$ENV"
  echo "  Copied the login from the old game streaming setup"
fi
if [ -f "$ENV" ]; then
  echo "  Using the saved login (delete $ENV to change it)"
else
  printf "  Your RomM username: "
  read -r RU </dev/tty
  printf "  Your RomM password (it won't show while typing): "
  read -rs RP </dev/tty
  echo
  printf 'ROMM_USER=%s\nROMM_PASS=%s\n' "$RU" "$RP" >"$ENV"
fi
chmod 600 "$ENV"
# shellcheck disable=SC1090
. "$ENV"
code=$(curl -s -o /dev/null -w '%{http_code}' -u "$ROMM_USER:$ROMM_PASS" "http://127.0.0.1:8095/api/roms?limit=1")
case $code in
  200) echo "  ok  RomM accepted the login" ;;
  401 | 403) echo "  PROBLEM  RomM didn't accept that login ($code). Run: rm $ENV  then run this command again." ;;
  *) echo "  PROBLEM  RomM answered $code. Is RomM running on port 8095?" ;;
esac

say "Finding Jellyfin"
JF=""
for u in http://127.0.0.1:8096 "http://$HOST_IP:8096"; do
  curl -fs -m 5 "$u/System/Info/Public" >/dev/null && JF=$u && break
done
if [ -z "$JF" ]; then
  for c in $(docker ps --format '{{.Names}}' | grep -i jellyfin); do
    ipaddr=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$c" | awk '{print $1}')
    [ -n "$ipaddr" ] && curl -fs -m 5 "http://$ipaddr:8096/System/Info/Public" >/dev/null && JF="http://$ipaddr:8096" && break
  done
fi
if [ -n "$JF" ]; then echo "  ok  $JF"; else
  JF=http://127.0.0.1:8096
  echo "  PROBLEM  Couldn't find Jellyfin on port 8096. Send this output to Claude."
fi

say "Starting the helper (benflix-games on port 8097)"
# The old streaming setup's folder is shared too while it's still there, so streaming keeps working
EXTRA=()
[ -d "$OLD" ] && EXTRA=(-v "$OLD:/benflix:rw")
docker rm -f benflix-games >/dev/null 2>&1
docker run -d --name benflix-games --restart unless-stopped --network host \
  --env-file "$ENV" -e ROMM_URL=http://127.0.0.1:8095 -e JELLYFIN_URL="$JF" -e WOLF_URL=http://127.0.0.1:47989 \
  -v "$APP:/app:ro" -v "$ROMS:/ROMs:ro" -v "$BIOS:/bios:ro" -v "$SAVES:/saves:rw" "${EXTRA[@]}" \
  python:3.12-alpine python -u /app/benflix-games-helper.py >/dev/null && sleep 3
if curl -fs -m 5 http://127.0.0.1:8097/health | grep -q local_play; then echo "  ok  http://$HOST_IP:8097"; else
  echo "  PROBLEM  The helper didn't start. Its log:"
  docker logs --tail 20 benflix-games 2>&1
  exit 1
fi

say "BIOS files"
have() {
  if find "$BIOS" -iname "$1" 2>/dev/null | grep -q .; then echo "  ok       $2"; else echo "  missing  $2 -> put $1 in \\\\TOWER\\Homelab\\Games\\bios"; fi
}
have "scph5501.bin" "PlayStation 1 (scph5501.bin or scph1001.bin; most games still work without it)"
have "gba_bios.bin" "Game Boy Advance (optional)"

echo
echo "All done. Open Benflix > Games on your TV or phone and press a game."
