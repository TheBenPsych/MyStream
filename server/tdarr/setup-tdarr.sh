#!/bin/bash
# Benflix: installs Tdarr on Tower and sets it up to shrink big videos by itself (Ben's picks, 2026-11-16).
#   - Only the anime folders for now (every folder in Media with "anime" in its name)
#   - Re-encodes to HEVC on the Intel Arc card (Quick Sync), so every TV and phone can still play it
#   - Episodes over 1 GB and movies over 2 GB, and only files that aren't HEVC or AV1 yet
#   - Keeps English + the original language for sound (anime keeps Japanese) and English subtitles
#   - Works 1 AM to 8 AM, and doesn't start anything new while someone is watching Benflix
#   - Keeps the original in a backup folder for 3 days, and only replaces a file when the new one
#     is clearly smaller, the same length and still has its video and sound
# Safe to run again: it refreshes everything from GitHub. Tower also re-runs it by itself (hourly check)
# when any of these files change on GitHub, so Ben never needs to paste this again after the first time.
#
# Web page: http://<Tower>:8265   Shrink log: /mnt/user/Homelab/tdarr-backup/shrink-log.txt
set -u

# ---- Ben's settings (change here, Tower picks it up within the hour) ----
EPISODE_GB=${TDARR_EPISODE_GB:-1}
MOVIE_GB=${TDARR_MOVIE_GB:-2}
QUALITY=24          # Quick Sync quality: lower = better looking and bigger
BACKUP_DAYS=3
NIGHT_START=1       # 1 AM
NIGHT_END=8         # 8 AM
FOLDER_MATCH=anime  # which Media folders it works on (any folder whose name contains this)
SETUP_VERSION=2     # bump when the container itself needs re-making

# ---- Where things are ----
MEDIA=${TDARR_MEDIA:-/mnt/user/Homelab/Media}
APP=${TDARR_APP:-/mnt/user/appdata/tdarr}
BACKUP=${TDARR_BACKUP:-/mnt/user/Homelab/tdarr-backup}
CACHE=${TDARR_CACHE:-/mnt/user/appdata/tdarr/cache}
RAW=${TDARR_RAW:-https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/tdarr}
ENCODER=${TDARR_ENCODER:-qsv}
IMAGE=haveagitgat/tdarr:latest
PLUGINS="benflixShouldShrink benflixPrepare benflixCheckNewFile benflixKeepBackup benflixReport"
API=http://127.0.0.1:8265/api/v2
say() { echo; echo "== $*"; }
problem() { echo "  PROBLEM  $*"; }

HOST_IP=$(ip -4 -o addr show br0 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -1)
[ -n "$HOST_IP" ] || HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
[ -n "$HOST_IP" ] || HOST_IP=127.0.0.1

say "Getting the Benflix Tdarr files from GitHub"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
for f in benflix-shrink-flow.json tdarr-watch.sh; do
  wget -qO "$TMP/$f" "$RAW/$f" || { problem "Couldn't download $f. Check Tower's internet and try again."; exit 1; }
done
for p in $PLUGINS; do
  wget -qO "$TMP/$p.js" "$RAW/plugins/benflix/$p/1.0.0/index.js" || { problem "Couldn't download $p"; exit 1; }
done
echo "  ok  downloaded"

say "Finding the folders to shrink"
FOLDERS=()
while IFS= read -r d; do FOLDERS+=("$(basename "$d")"); done < <(find "$MEDIA" -mindepth 1 -maxdepth 1 -type d -iname "*$FOLDER_MATCH*" | sort)
if [ ${#FOLDERS[@]} -eq 0 ]; then problem "No folder with \"$FOLDER_MATCH\" in its name inside $MEDIA"; exit 1; fi
for d in "${FOLDERS[@]}"; do echo "  ok  $MEDIA/$d"; done

say "Finding the Arc graphics card"
GPU=""
for n in /dev/dri/by-path/pci-*-render; do
  [ -e "$n" ] || continue
  pci=$(basename "$n" | sed 's/^pci-\(.*\)-render$/\1/')
  vendor=$(cat "/sys/bus/pci/devices/$pci/vendor" 2>/dev/null)
  # The Intel card that isn't the built-in one (00:02.0) is the Arc
  if [ "$vendor" = "0x8086" ] && [ "$pci" != "0000:00:02.0" ]; then GPU=$(readlink -f "$n"); fi
done
if [ -z "$GPU" ] && [ -e /dev/dri/renderD129 ]; then GPU=/dev/dri/renderD129; fi
if [ -z "$GPU" ] && [ "$ENCODER" = "qsv" ]; then problem "Couldn't find the Arc card in /dev/dri"; exit 1; fi
[ -n "$GPU" ] && echo "  ok  $GPU"

say "Finding Sonarr and Radarr (to know each title's original language)"
find_arr() { # $1 = sonarr or radarr, $2 = usual port. Sets ${1}_url, ${1}_key, arr_root
  local c cfg key port base
  c=$(docker ps --format '{{.Names}}' | grep -i "$1" | head -1)
  if [ -z "$c" ]; then echo "  note  No running $1 app, anime falls back to Japanese"; return 0; fi
  cfg=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/config"}}{{.Source}}{{end}}{{end}}' "$c")/config.xml
  key=$(sed -n 's:.*<ApiKey>\(.*\)</ApiKey>.*:\1:p' "$cfg" 2>/dev/null | head -1)
  port=$(sed -n 's:.*<Port>\(.*\)</Port>.*:\1:p' "$cfg" 2>/dev/null | head -1)
  base=$(sed -n 's:.*<UrlBase>\(.*\)</UrlBase>.*:\1:p' "$cfg" 2>/dev/null | head -1)
  [ -n "$port" ] || port=$2
  if [ -z "$key" ] || ! curl -fs -m 5 -H "X-Api-Key: $key" "http://$HOST_IP:$port$base/api/v3/system/status" >/dev/null; then
    echo "  note  Found $c but couldn't use it, anime falls back to Japanese"; return 0
  fi
  printf -v "${1}_url" '%s' "http://$HOST_IP:$port$base"
  printf -v "${1}_key" '%s' "$key"
  local root
  root=$(docker inspect -f '{{range .Mounts}}{{if eq .Source "'"$MEDIA"'"}}{{.Destination}}{{end}}{{end}}' "$c")
  [ -n "$root" ] && arr_root=$root
  echo "  ok  $c (http://$HOST_IP:$port$base, Media is $root there)"
}
sonarr_url="" sonarr_key="" radarr_url="" radarr_key="" arr_root=/data
find_arr sonarr 8989
find_arr radarr 7878

say "Starting Tdarr"
mkdir -p "$APP/server" "$APP/configs" "$APP/logs" "$CACHE" "$BACKUP"
chown 99:100 "$BACKUP" "$CACHE" 2>/dev/null
[ -f "$BACKUP/shrink-log.txt" ] && chown 99:100 "$BACKUP/shrink-log.txt" && chmod 664 "$BACKUP/shrink-log.txt"
RECIPE="v$SETUP_VERSION gpu=$GPU media=$MEDIA backup=$BACKUP cache=$CACHE"
running=$(docker inspect -f '{{.State.Running}}' tdarr 2>/dev/null)
if [ "$running" = "true" ] && [ "$(cat "$APP/benflix-recipe.txt" 2>/dev/null)" = "$RECIPE" ]; then
  echo "  ok  already running"
else
  docker rm -f tdarr >/dev/null 2>&1
  dev=()
  # The whole /dev/dri, so each card keeps its real name (renaming the Arc to renderD128 broke Quick Sync,
  # 2026-11-16). The flow picks the Arc by its name, so the built-in graphics stays free for everything else.
  [ -n "$GPU" ] && dev=(--device /dev/dri)
  if ! docker run -d --name tdarr --restart unless-stopped --memory=4g \
    -p 8265:8265 -p 8266:8266 "${dev[@]}" \
    -e serverIP=0.0.0.0 -e serverPort=8266 -e webUIPort=8265 -e internalNode=true -e inContainer=true \
    -e ffmpegVersion=8 -e nodeName=TowerNode -e auth=false -e PUID=99 -e PGID=100 -e UMASK_SET=002 \
    -e TZ=America/New_York \
    -v "$APP/server":/app/server -v "$APP/configs":/app/configs -v "$APP/logs":/app/logs \
    -v "$MEDIA":/media -v "$CACHE":/temp -v "$BACKUP":/backup \
    -l net.unraid.docker.webui="http://[IP]:[PORT:8265]/" \
    -l net.unraid.docker.icon="https://raw.githubusercontent.com/selfhosters/unRAID-CA-templates/master/templates/img/tdarr.png" \
    "$IMAGE" >/dev/null; then
    problem "Tdarr didn't start"; exit 1
  fi
  echo "$RECIPE" >"$APP/benflix-recipe.txt"
  echo "  ok  started (first start downloads a few things, up to a few minutes)"
fi

# Wait for Tdarr and its built-in worker to be ready
NODE=""
for i in $(seq 1 90); do
  if curl -fs -m 5 "$API/status" 2>/dev/null | grep -q '"good"'; then
    NODE=$(curl -fs -m 5 "$API/get-nodes" 2>/dev/null | sed -n 's/^{"\([^"]*\)".*/\1/p')
    [ -n "$NODE" ] && break
  fi
  sleep 4
done
if [ -z "$NODE" ]; then problem "Tdarr didn't answer after 6 minutes. Run this again, or paste: docker logs tdarr --tail 40"; exit 1; fi
echo "  ok  Tdarr is answering"

db() { # $1 = collection, $2 = mode, $3 = id, $4 = json object (or empty)
  local obj=${4:-"{}"}
  curl -fs -m 30 -X POST "$API/cruddb" -H 'content-type: application/json' \
    -d "{\"data\":{\"collection\":\"$1\",\"mode\":\"$2\",\"docID\":\"$3\",\"obj\":$obj}}"
}
post() { curl -fs -m 30 -X POST "$API/$1" -H 'content-type: application/json' -d "$2"; }

say "Adding the Benflix shrink steps"
LOCAL="$APP/server/Tdarr/Plugins/FlowPlugins/LocalFlowPlugins/benflix"
for p in $PLUGINS; do
  mkdir -p "$LOCAL/$p/1.0.0"
  cp "$TMP/$p.js" "$LOCAL/$p/1.0.0/index.js"
done
chown -R 99:100 "$LOCAL"
post sync-plugins '{"data":{}}' >/dev/null
echo "  ok  5 Benflix steps"

esc() { printf '%s' "$1" | sed -e 's/[\/&|]/\\&/g'; }
sed -e "s|__EPISODE_GB__|$EPISODE_GB|" -e "s|__MOVIE_GB__|$MOVIE_GB|" -e "s|__QUALITY__|$QUALITY|" \
  -e "s|__ENCODER__|$ENCODER|" -e "s|__DEVICE__|${GPU:-/dev/dri/renderD128}|" -e "s|__BACKUP__|/backup|" -e "s|__ARR_ROOT__|$(esc "$arr_root")|" \
  -e "s|__RADARR_URL__|$(esc "$radarr_url")|" -e "s|__RADARR_KEY__|$(esc "$radarr_key")|" \
  -e "s|__SONARR_URL__|$(esc "$sonarr_url")|" -e "s|__SONARR_KEY__|$(esc "$sonarr_key")|" \
  "$TMP/benflix-shrink-flow.json" | tr -d '\n' >"$TMP/flow.json"
db FlowsJSONDB removeOne benflix_shrink >/dev/null
if db FlowsJSONDB insert benflix_shrink "$(cat "$TMP/flow.json")" >/dev/null \
  && db FlowsJSONDB getById benflix_shrink | grep -q benflixPrepare; then
  echo "  ok  flow \"Benflix shrink\""
else
  problem "Couldn't save the flow"; exit 1
fi

say "Adding the libraries (works $NIGHT_START AM to $NIGHT_END AM)"
schedule=""
for day in Sun Mon Tue Wed Thur Fri Sat; do
  for h in $(seq 0 23); do
    a=$(printf '%02d' "$h"); b=$(printf '%02d' $(((h + 1) % 24)))
    on=false
    if [ "$h" -ge "$NIGHT_START" ] && [ "$h" -lt "$NIGHT_END" ]; then on=true; fi
    schedule="$schedule{\"_id\":\"$day:$a-$b\",\"checked\":$on},"
  done
done
schedule="[${schedule%,}]"
n=0
for d in "${FOLDERS[@]}"; do
  id="benflix_$(echo "$d" | tr 'A-Z' 'a-z' | tr -c 'a-z0-9' '_' | sed 's/_*$//')"
  name=$(printf '%s' "$d" | sed 's/["\\]//g')
  lib="{\"name\":\"$name\",\"folder\":\"/media/$name\",\"cache\":\"/temp\",\"output\":\"\",
\"priority\":$n,\"foldersToIgnore\":\"\",\"foldersToIgnoreCaseInsensitive\":false,
\"folderWatchScanInterval\":30,\"scannerThreadCount\":2,\"folderToFolderConversion\":false,
\"folderToFolderConversionDeleteSource\":false,\"folderToFolderRecordHistory\":true,\"copyIfConditionsMet\":false,
\"container\":\".mkv\",\"containerFilter\":\"mkv,mp4,m4v\",\"folderWatching\":true,\"useFsEvents\":false,
\"scheduledScanFindNew\":true,\"processLibrary\":true,\"processTranscodes\":true,\"processHealthChecks\":false,
\"scanOnStart\":true,\"exifToolScan\":true,\"mediaInfoScan\":true,\"ffprobeShowData\":false,\"isDirectoryLibrary\":false,
\"closedCaptionScan\":false,\"scanButtons\":true,\"scanFound\":\"\",\"navItemSelected\":\"navSourceFolder\",
\"pluginIDs\":[],\"pluginCommunity\":true,\"handbrake\":false,\"ffmpeg\":true,\"handbrakescan\":true,\"ffmpegscan\":true,
\"preset\":\"\",\"flowId\":\"benflix_shrink\",
\"decisionMaker\":{\"settingsPlugin\":false,\"settingsFlows\":true,\"settingsVideo\":false,\"videoExcludeSwitch\":true,
\"video_codec_names_exclude\":[],\"video_size_range_include\":{\"min\":0,\"max\":100000},
\"video_height_range_include\":{\"min\":0,\"max\":3000},\"video_width_range_include\":{\"min\":0,\"max\":4000},
\"settingsAudio\":false,\"audioExcludeSwitch\":true,\"audio_codec_names_exclude\":[],
\"audio_size_range_include\":{\"min\":0,\"max\":10}},
\"schedule\":$schedule,
\"holdNewFiles\":false,\"holdFor\":3600,\"holdForDisplayUnit\":\"hours\",\"pluginStackOverview\":true,
\"filterResolutionsSkip\":\"\",\"filterCodecsSkip\":\"\",\"filterContainersSkip\":\"\",\"filterHardlinked\":false,
\"processPluginsSequentially\":true}"
  lib=$(printf '%s' "$lib" | tr -d '\n')
  if db LibrarySettingsJSONDB getById "$id" | grep -q '"_id"'; then
    db LibrarySettingsJSONDB update "$id" "$lib" >/dev/null
  else
    db LibrarySettingsJSONDB insert "$id" "$(printf '%s' "$lib" | sed 's/^{/{"_id":"'"$id"'","createdAt":'"$(date +%s)000"',"totalHealthCheckCount":0,"totalTranscodeCount":0,"sizeDiff":0,/')" >/dev/null
  fi
  if db LibrarySettingsJSONDB getById "$id" | grep -q benflix_shrink; then
    echo "  ok  $name"
  else
    problem "Couldn't save the $name library"; exit 1
  fi
  post scan-files "{\"data\":{\"scanConfig\":{\"dbID\":\"$id\",\"arrayOrPath\":\"/media/$name\",\"mode\":\"scanFindNew\"}}}" >/dev/null
  n=$((n + 1))
done

say "Removing libraries that aren't Benflix ones (your files are never touched)"
# Tdarr can make its own library over all of Media with its default rules (it did on Tower, 2026-11-16).
# Those rules would re-encode everything on the CPU, so only the Benflix libraries may stay.
others=$(docker exec tdarr node -e '
const h=require("http");
const q=h.request({host:"127.0.0.1",port:8265,path:"/api/v2/cruddb",method:"POST",headers:{"content-type":"application/json"}},s=>{
  let d="";s.on("data",c=>d+=c);s.on("end",()=>{for(const l of JSON.parse(d||"[]")){
    if(!String(l._id).startsWith("benflix_"))console.log(l._id+"\t"+(l.name||"")+"\t"+(l.folder||""));}});});
q.end(JSON.stringify({data:{collection:"LibrarySettingsJSONDB",mode:"getAll"}}));' 2>/dev/null)
if [ -z "$others" ]; then
  echo "  ok  none"
else
  while IFS=$'\t' read -r lid lname lfolder; do
    [ -n "$lid" ] || continue
    post remove-library-files "{\"data\":{\"DB\":\"$lid\"}}" >/dev/null
    db LibrarySettingsJSONDB removeOne "$lid" >/dev/null
    echo "  ok  removed \"$lname\" ($lfolder)"
  done <<<"$others"
fi

say "One GPU worker, nothing on the CPU"
limits=$(curl -fs -m 10 "$API/get-nodes" | grep -o '"workerLimits":{[^}]*}' | head -1)
set_limit() { # $1 = worker type, $2 = how many
  local now i
  now=$(printf '%s' "$limits" | sed -n "s/.*\"$1\":\([0-9]*\).*/\1/p" | head -1)
  now=${now:-0}
  for ((i = now; i < $2; i++)); do post alter-worker-limit "{\"data\":{\"nodeID\":\"$NODE\",\"process\":\"increase\",\"workerType\":\"$1\"}}" >/dev/null; done
  for ((i = now; i > $2; i--)); do post alter-worker-limit "{\"data\":{\"nodeID\":\"$NODE\",\"process\":\"decrease\",\"workerType\":\"$1\"}}" >/dev/null; done
}
gpu_workers=1
[ "$ENCODER" = "cpu" ] && gpu_workers=0
set_limit transcodegpu $gpu_workers
set_limit transcodecpu $((1 - gpu_workers))
set_limit healthcheckgpu 0
set_limit healthcheckcpu 0
# Tdarr counts our flow as a "CPU" job, so the GPU worker must be allowed to take CPU jobs (2026-11-17:
# without this nothing ran all night)
post update-node "{\"data\":{\"nodeID\":\"$NODE\",\"nodeUpdates\":{\"allowGpuDoCpu\":true}}}" >/dev/null
echo "  ok  $(curl -fs -m 10 "$API/get-nodes" | grep -o '"workerLimits":{[^}]*}' | head -1)"

if [ -z "${TDARR_TEST:-}" ]; then
  say "Watcher: pauses while someone watches, clears old backups, keeps this up to date"
  mkdir -p /boot/config/benflix
  cp "$TMP/tdarr-watch.sh" /boot/config/benflix/tdarr-watch.sh
  printf 'BACKUP=%s\nBACKUP_DAYS=%s\nRAW=%s\nAPP=%s\n' "$BACKUP" "$BACKUP_DAYS" "$RAW" "$APP" >/boot/config/benflix/tdarr-watch.env
  cron=/boot/config/plugins/dynamix/benflix-tdarr.cron
  {
    echo "# Benflix Tdarr: pause while someone watches (every 2 minutes), tidy + self-update (hourly)"
    echo "*/2 * * * * /bin/bash /boot/config/benflix/tdarr-watch.sh pause >/dev/null 2>&1"
    echo "41 * * * * /bin/bash /boot/config/benflix/tdarr-watch.sh hourly >/dev/null 2>&1"
  } >"$cron"
  if command -v update_cron >/dev/null 2>&1; then update_cron; else
    (crontab -l 2>/dev/null | grep -v benflix/tdarr-watch.sh; grep -v '^#' "$cron") | crontab -
  fi
  echo "  ok  every 2 minutes + hourly"
  # Remember exactly what was installed, so the hourly check knows when GitHub has something new
  mkdir -p "$APP/benflix-installed"
  cp "$TMP"/* "$APP/benflix-installed/" 2>/dev/null
  wget -qO "$APP/benflix-installed/setup-tdarr.sh" "$RAW/setup-tdarr.sh"
fi

echo
echo "All done. Tdarr's page: http://$HOST_IP:8265"
echo "It shrinks between $NIGHT_START AM and $NIGHT_END AM. What it did: $BACKUP/shrink-log.txt"
echo "Paste everything above to Claude if any line says PROBLEM."
