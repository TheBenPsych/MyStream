#!/usr/bin/env bash
# A private Jellyfin test server for the robots, made from scratch on the GitHub computer (nothing from Tower).
# Makes short test videos with ffmpeg that cover the tricky cases:
#   - Test Pattern One: 1080p, two audio languages (English + Spanish), English and Spanish subtitles, 12 minutes
#   (movies are 10+ minutes: Jellyfin only remembers your place in videos over 5 minutes, and "Up next" shows
#   4 minutes before the end. 2 pictures a second keeps them quick to make.)
#   - Big Color Test: 4K HEVC 10-bit with HDR10 info and 5.1 surround sound
#   - Surround Test: 1080p, 5.1 Dolby (E-AC3), styled (ASS) subtitles
#   - Kids Cartoon Test: rated G, so the Kids profile has something to see (everything else is PG-13 or R)
#   - Test Show: 3 short episodes (for Next episode / autoplay)
# Then starts Jellyfin in Docker on port 8096 with user "demo" (no password) and the two libraries.
# Usage: test_server.sh <media dir>
set -euo pipefail
MEDIA=${1:-$HOME/testmedia}
mkdir -p "$MEDIA/Movies" "$MEDIA/Shows"
command -v ffmpeg >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg >/dev/null; }

srt() { # language, words
  cat <<EOF
1
00:00:02,000 --> 00:00:20,000
$2 one

2
00:00:22,000 --> 00:00:40,000
$2 two

3
00:00:42,000 --> 00:11:50,000
$2 three
EOF
}
nfo() { # kind title year rating
  printf '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<%s><title>%s</title><year>%s</year><mpaa>%s</mpaa><plot>A Benflix robot test video.</plot><genre>Animation</genre></%s>\n' "$1" "$2" "$3" "$4" "$1"
}

d="$MEDIA/Movies/Test Pattern One (2020)"; mkdir -p "$d"
srt en "English line" > /tmp/en.srt; srt es "Linea en espanol" > /tmp/es.srt
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=1920x1080:rate=2:duration=720" \
  -f lavfi -i "sine=frequency=440:duration=720" -f lavfi -i "sine=frequency=660:duration=720" -i /tmp/en.srt -i /tmp/es.srt \
  -map 0:v -map 1:a -map 2:a -map 3 -map 4 -c:v libx264 -preset ultrafast -b:v 1500k -c:a aac -b:a 96k -c:s srt \
  -metadata:s:a:0 language=eng -metadata:s:a:0 title="English" -metadata:s:a:1 language=spa -metadata:s:a:1 title="Spanish" \
  -metadata:s:s:0 language=eng -metadata:s:s:1 language=spa "$d/Test Pattern One (2020).mkv"
nfo movie "Test Pattern One" 2020 PG-13 > "$d/movie.nfo"

d="$MEDIA/Movies/Big Color Test (2021)"; mkdir -p "$d"
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=3840x2160:rate=2:duration=600" \
  -f lavfi -i "sine=frequency=330:duration=600" \
  -map 0:v -map 1:a -c:v libx265 -preset ultrafast -pix_fmt yuv420p10le -b:v 6000k \
  -x265-params "log-level=error:hdr10=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400" \
  -c:a ac3 -ac 6 -b:a 384k -tag:v hvc1 "$d/Big Color Test (2021).mkv"
nfo movie "Big Color Test" 2021 R > "$d/movie.nfo"

d="$MEDIA/Movies/Surround Test (2019)"; mkdir -p "$d"
printf '[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, Bold, Alignment\nStyle: Default,Arial,48,&H0000FFFF,1,2\n\n[Events]\nFormat: Layer, Start, End, Style, Text\nDialogue: 0,0:00:02.00,0:09:50.00,Default,Yellow styled words\n' > /tmp/styled.ass
ffmpeg -loglevel error -y -f lavfi -i "smptehdbars=size=1920x1080:rate=2:duration=600" \
  -f lavfi -i "sine=frequency=550:duration=600" -i /tmp/styled.ass \
  -map 0:v -map 1:a -map 2 -c:v libx264 -preset ultrafast -b:v 1200k -c:a eac3 -ac 6 -b:a 384k -c:s ass \
  -metadata:s:s:0 language=eng "$d/Surround Test (2019).mkv"
nfo movie "Surround Test" 2019 PG-13 > "$d/movie.nfo"

d="$MEDIA/Movies/Kids Cartoon Test (2022)"; mkdir -p "$d"
ffmpeg -loglevel error -y -f lavfi -i "mandelbrot=size=1280x720:rate=2" -t 600 -f lavfi -i "sine=frequency=880:duration=600" \
  -c:v libx264 -preset ultrafast -b:v 1000k -c:a aac -shortest "$d/Kids Cartoon Test (2022).mp4"
nfo movie "Kids Cartoon Test" 2022 G > "$d/movie.nfo"

d="$MEDIA/Shows/Test Show (2020)"; mkdir -p "$d/Season 01"
printf '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<tvshow><title>Test Show</title><year>2020</year><mpaa>TV-PG</mpaa><plot>A Benflix robot test show.</plot></tvshow>\n' > "$d/tvshow.nfo"
for e in 1 2 3; do
  ffmpeg -loglevel error -y -f lavfi -i "testsrc=size=1280x720:rate=24:duration=75" -f lavfi -i "sine=frequency=$((300 + e * 100)):duration=75" \
    -c:v libx264 -preset ultrafast -b:v 800k -c:a aac "$d/Season 01/Test Show S01E0$e.mkv"
  printf '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<episodedetails><title>Episode number %s</title><season>1</season><episode>%s</episode><plot>Test episode %s.</plot></episodedetails>\n' "$e" "$e" "$e" > "$d/Season 01/Test Show S01E0$e.nfo"
done
# An anime show with two seasons (like the shows on Tower's anime shelf): the anime tabs and season chips
d="$MEDIA/Shows/Robot Anime Test (2024)"
for s in 1 2; do
  mkdir -p "$d/Season 0$s"
  for e in 1 2 3; do
    ffmpeg -loglevel error -y -f lavfi -i "testsrc=size=1280x720:rate=12:duration=30" -f lavfi -i "sine=frequency=$((200 + e * 50)):duration=30" \
      -c:v libx264 -preset ultrafast -b:v 500k -c:a aac "$d/Season 0$s/Robot Anime Test S0${s}E0$e.mkv"
    printf '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<episodedetails><title>Anime episode %s</title><season>%s</season><episode>%s</episode><plot>Anime test episode %s.</plot></episodedetails>\n' "$e" "$s" "$e" "$e" > "$d/Season 0$s/Robot Anime Test S0${s}E0$e.nfo"
  done
done
printf '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<tvshow><title>Robot Anime Test</title><year>2024</year><mpaa>TV-14</mpaa><genre>Anime</genre><genre>Fantasy</genre><plot>A Benflix robot anime test.</plot></tvshow>\n' > "$d/tvshow.nfo"
du -sh "$MEDIA"

docker run -d --name jellyfin -p 8096:8096 -v "$MEDIA:/media:ro" jellyfin/jellyfin:10.10.7 >/dev/null
J=http://127.0.0.1:8096
# Jellyfin answers "starting up" for a while: wait until the first-time setup questions answer
for i in $(seq 1 120); do curl -fs "$J/Startup/Configuration" >/dev/null && break; sleep 2; done
H='Content-Type: application/json'
A='X-Emby-Authorization: MediaBrowser Client="robot", Device="robot", DeviceId="robot", Version="1"'
call() { # method path [body]: tries again for a while, shows what went wrong
  for t in $(seq 1 20); do
    code=$(curl -s -o /tmp/call.out -w '%{http_code}' -X "$1" "$J$2" -H "$H" -H "$A" ${TOKEN:+-H "X-Emby-Token: $TOKEN"} ${3:+-d "$3"})
    case $code in 2*) cat /tmp/call.out; return 0;; esac
    echo "  $1 $2 answered $code: $(head -c 200 /tmp/call.out)" >&2
    sleep 3
  done
  return 1
}
TOKEN=
call POST /Startup/Configuration '{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}' >/dev/null
call GET /Startup/User >/dev/null
# Jellyfin wants a password on the first (admin) user; the robots use "demo" (no password), made below
call POST /Startup/User '{"Name":"robotadmin","Password":"robot-admin-pw"}' >/dev/null
call POST /Startup/RemoteAccess '{"EnableRemoteAccess":true,"EnableAutomaticPortMapping":false}' >/dev/null
call POST /Startup/Complete >/dev/null
for i in $(seq 1 30); do curl -fs "$J/System/Info/Public" | grep -q '"StartupWizardCompleted":true' && break; sleep 2; done
TOKEN=$(call POST /Users/AuthenticateByName '{"Username":"robotadmin","Pw":"robot-admin-pw"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['AccessToken'])")
T="X-Emby-Token: $TOKEN"
call POST "/Library/VirtualFolders?name=Movies&collectionType=movies&refreshLibrary=false" '{"LibraryOptions":{"PathInfos":[{"Path":"/media/Movies"}],"EnableRealtimeMonitor":false}}'
call POST "/Library/VirtualFolders?name=Shows&collectionType=tvshows&refreshLibrary=false" '{"LibraryOptions":{"PathInfos":[{"Path":"/media/Shows"}],"EnableRealtimeMonitor":false}}'
call POST /Library/Refresh
call POST /Users/New '{"Name":"demo","Password":""}' >/dev/null
# A Jellyfin user called Kids that may only see G (what a parent would set up on Tower)
KID=$(call POST /Users/New '{"Name":"Kids","Password":""}' | python3 -c "import json,sys;print(json.load(sys.stdin)['Id'])")
call GET "/Users/$KID" | python3 -c "
import json,sys
p=json.load(sys.stdin)['Policy']; p['MaxParentalRating']=1; p['BlockUnratedItems']=['Movie','Series']
print(json.dumps(p))" > /tmp/kidpolicy.json
call POST "/Users/$KID/Policy" "$(cat /tmp/kidpolicy.json)" || echo "Kids limits could not be set"
for i in $(seq 1 90); do
  n=$(curl -fs "$J/Items?Recursive=true&IncludeItemTypes=Movie,Episode" -H "$T" | python3 -c "import json,sys;print(json.load(sys.stdin)['TotalRecordCount'])" || echo 0)
  [ "$n" -ge 7 ] && break
  sleep 3
done
echo "Test server ready with $n movies and episodes"
curl -fs "$J/Items?Recursive=true&IncludeItemTypes=Movie,Series,Episode&Fields=OfficialRating" -H "$T" | python3 -c "
import json,sys
for it in json.load(sys.stdin)['Items']: print(' ', it['Type'], it['Name'], it.get('OfficialRating'))"
