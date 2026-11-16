#!/bin/bash
# Benflix: installs Recyclarr on Tower and syncs Ben's Sonarr and Radarr scoring.
# Safe to run again: it refreshes the settings from GitHub and syncs once more.
# Recyclarr then re-syncs by itself every night, so the scores can't drift.
set -u
APP=/mnt/user/appdata/recyclarr
RAW=https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/recyclarr
say() { echo; echo "== $*"; }

HOST_IP=$(ip -4 -o addr show br0 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -1)
[ -n "$HOST_IP" ] || HOST_IP=$(hostname -I | awk '{print $1}')

say "Getting the Benflix Recyclarr settings"
mkdir -p "$APP"
if ! wget -qO "$APP/recyclarr.yml.new" "$RAW/recyclarr.yml"; then
  echo "  PROBLEM  Couldn't download the settings from GitHub. Check Tower's internet and try again."
  exit 1
fi
mv "$APP/recyclarr.yml.new" "$APP/recyclarr.yml"
echo "  ok  $APP/recyclarr.yml"

say "Finding Sonarr and Radarr (keys stay on Tower in secrets.yml)"
: >"$APP/secrets.yml"
find_arr() { # $1 = sonarr or radarr, $2 = usual port
  local c cfg key port base
  c=$(docker ps -a --format '{{.Names}}' | grep -i "$1" | head -1)
  if [ -z "$c" ]; then echo "  PROBLEM  No $1 app found"; return 1; fi
  cfg=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/config"}}{{.Source}}{{end}}{{end}}' "$c")/config.xml
  key=$(sed -n 's:.*<ApiKey>\(.*\)</ApiKey>.*:\1:p' "$cfg" | head -1)
  port=$(sed -n 's:.*<Port>\(.*\)</Port>.*:\1:p' "$cfg" | head -1)
  base=$(sed -n 's:.*<UrlBase>\(.*\)</UrlBase>.*:\1:p' "$cfg" | head -1)
  [ -n "$port" ] || port=$2
  if [ -z "$key" ]; then echo "  PROBLEM  Couldn't read $1's key from $cfg"; return 1; fi
  if ! curl -fs -m 5 -H "X-Api-Key: $key" "http://$HOST_IP:$port$base/api/v3/system/status" >/dev/null; then
    echo "  PROBLEM  Found $c but couldn't reach it at http://$HOST_IP:$port$base"; return 1
  fi
  printf '%s_url: http://%s:%s%s\n%s_key: %s\n' "$1" "$HOST_IP" "$port" "$base" "$1" "$key" >>"$APP/secrets.yml"
  echo "  ok  $c (http://$HOST_IP:$port$base)"
}
find_arr sonarr 8989 || exit 1
find_arr radarr 7878 || exit 1
chown -R 99:100 "$APP"
chmod 600 "$APP/secrets.yml"

say "Starting Recyclarr (syncs again by itself every night)"
docker rm -f recyclarr >/dev/null 2>&1
docker run -d --name recyclarr --restart unless-stopped --user 99:100 \
  -e TZ=America/New_York -e CRON_SCHEDULE=@daily \
  -v "$APP":/config ghcr.io/recyclarr/recyclarr:8 >/dev/null || { echo "  PROBLEM  Recyclarr didn't start"; exit 1; }
sleep 5
echo "  ok  recyclarr is running"

say "First sync (takes a minute)"
docker exec recyclarr recyclarr sync 2>&1 | grep -viE "api[_ ]?key" | tail -60
echo
echo "Done. Paste everything above to Claude if any line says error, warning or PROBLEM."
