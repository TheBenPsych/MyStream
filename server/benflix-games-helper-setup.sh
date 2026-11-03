#!/bin/bash
# Benflix helper: lets Benflix play your RomM games right on the TV or phone, and powers the
# Tower tools on Benflix's Server page (apps, downloads, how busy Tower is, track tidy).
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
MEDIA=/mnt/user/Homelab/Media
TRACKS=/mnt/user/appdata/benflix-tracks
OLD=/mnt/user/appdata/wolf/benflix
RAW=https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server

say() { printf '\n== %s ==\n' "$1"; }

mkdir -p "$APP" "$BIOS" "$SAVES" "$TRACKS"
chmod 777 "$SAVES"
HOST_IP=$(ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p')
[ -n "$HOST_IP" ] || HOST_IP=10.0.0.58

say "Downloading the helper"
for f in benflix-games-helper.py benflix_server_tools.py benflix-fix-tracks.py benflix-web.html; do
  if curl -fsSL "$RAW/$f" -o "$APP/$f"; then echo "  ok  $f"; else
    echo "  PROBLEM  Couldn't download $f. Check the server has internet."
    exit 1
  fi
done

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

say "Connecting Sonarr and Radarr (for the Server page)"
# Reads each app's key from its own settings file, then finds an address the helper can reach
TOOLS_ENV=$APP/tools.env
: >"$TOOLS_ENV"
chmod 600 "$TOOLS_ENV"
find_arr() { # $1 = sonarr or radarr, $2 = usual port
  local c cfg key port base ipaddr u
  c=$(docker ps -a --format '{{.Names}}' | grep -i "$1" | head -1)
  if [ -z "$c" ]; then echo "  skip  No $1 app found"; return; fi
  cfg=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/config"}}{{.Source}}{{end}}{{end}}' "$c")/config.xml
  if [ ! -f "$cfg" ]; then echo "  PROBLEM  Couldn't find $1's settings file ($cfg)"; return; fi
  key=$(sed -n 's:.*<ApiKey>\(.*\)</ApiKey>.*:\1:p' "$cfg" | head -1)
  port=$(sed -n 's:.*<Port>\(.*\)</Port>.*:\1:p' "$cfg" | head -1)
  base=$(sed -n 's:.*<UrlBase>\(.*\)</UrlBase>.*:\1:p' "$cfg" | head -1)
  [ -n "$port" ] || port=$2
  ipaddr=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$c" | awk '{print $1}')
  for u in "http://127.0.0.1:$port$base" "http://$HOST_IP:$port$base" ${ipaddr:+"http://$ipaddr:$port$base"}; do
    if curl -fs -m 5 -H "X-Api-Key: $key" "$u/api/v3/system/status" >/dev/null; then
      NAME=$(echo "$1" | tr '[:lower:]' '[:upper:]')
      printf '%s_URL=%s\n%s_KEY=%s\n' "$NAME" "$u" "$NAME" "$key" >>"$TOOLS_ENV"
      echo "  ok  $c ($u)"
      return
    fi
  done
  echo "  PROBLEM  Found $c but couldn't reach it on port $port. Send this output to Claude."
}
find_arr sonarr 8989
find_arr radarr 7878

say "Starting the helper (benflix-games on port 8097)"
docker rm -f benflix-games >/dev/null 2>&1
docker run -d --name benflix-games --restart unless-stopped --network host \
  --env-file "$ENV" --env-file "$TOOLS_ENV" -e LC_ALL=C.UTF-8 -e ROMM_URL=http://127.0.0.1:8095 -e JELLYFIN_URL="$JF" \
  -v "$APP:/app:ro" -v "$ROMS:/ROMs:ro" -v "$BIOS:/bios:ro" -v "$SAVES:/saves:rw" \
  -v /var/run/docker.sock:/var/run/docker.sock -v "$MEDIA:/media:rw" -v "$TRACKS:/tracks:rw" \
  python:3.12-alpine sh -c 'apk add --no-cache mkvtoolnix >/dev/null 2>&1 || echo "Track tool missing (no internet?)"; exec python -u /app/benflix-games-helper.py' \
  >/dev/null && sleep 5
for _ in 1 2 3 4 5 6; do curl -fs -m 5 http://127.0.0.1:8097/health | grep -q local_play && break; sleep 5; done
if curl -fs -m 5 http://127.0.0.1:8097/health | grep -q '"server_tools": true'; then echo "  ok  http://$HOST_IP:8097 (games + Server page tools)"
  echo "  ok  Benflix tools in a PC browser: open http://$HOST_IP:8097 and sign in with your Jellyfin admin login"
elif curl -fs -m 5 http://127.0.0.1:8097/health | grep -q local_play; then echo "  ok  http://$HOST_IP:8097 (games only: the Server page tools didn't load)"; else
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
echo "The Server page (My Benflix > Server) now shows your apps, downloads and how busy Tower is."
