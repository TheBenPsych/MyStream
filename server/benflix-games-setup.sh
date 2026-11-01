#!/bin/bash
# Benflix game streaming: sets up the emulators for EmulationStation running in Wolf on Unraid.
# Safe to run again any time (for example after adding BIOS files or a new console).
#
# What it does:
#  1. Downloads the RetroArch "cores" (emulators) for every console we support
#  2. Downloads the extra files some cores need (Dolphin, PPSSPP)
#  3. Tells EmulationStation that GameCube and Dreamcast games live in roms/ngc and roms/dc, and to use the
#     RetroArch emulators for PS1 (SwanStation), PS2, GameCube and Wii
#  4. Groups multi-disc PS1 games into one entry each (Game.m3u folders)
#  5. Copies BIOS files from Games/bios/<console> to where the emulators look
#
# Run on the Unraid server:
#   curl -fsSL https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/benflix-games-setup.sh | bash

GAMES=/mnt/user/Homelab/Games
ROMS=$GAMES/roms
BIOS=$GAMES/bios
WOLF=/mnt/user/appdata/wolf
BUILDBOT=https://buildbot.libretro.com
CORES="mesen nestopia snes9x mupen64plus_next gambatte mgba melonds desmume
mednafen_psx mednafen_psx_hw swanstation pcsx_rearmed pcsx2 dolphin ppsspp flycast
genesis_plus_gx picodrive mednafen_saturn mednafen_pce mednafen_pce_fast mednafen_supergrafx
stella prosystem handy mednafen_lynx mednafen_ngp mednafen_wswan mednafen_vb
bluemsx gearcoleco freeintv vecx opera fbneo mame2003_plus"

say() { printf '\n== %s ==\n' "$1"; }

say "Finding EmulationStation's home folder"
LOG=$(find "$WOLF" -path '*ES-DE/logs/es_log.txt' 2>/dev/null | head -1)
if [ -z "$LOG" ]; then
  echo "Couldn't find it. Open EmulationStation once in Moonlight (Wolf UI > profile > EmulationStation), then run this again."
  exit 1
fi
H=$(dirname "$(dirname "$(dirname "$LOG")")")
OWNER=$(stat -c %u:%g "$H")
RA=$H/.config/retroarch
SYS=$RA/system
echo "Found: $H"
mkdir -p "$ROMS/psp" "$ROMS/dc" "$RA/cores" "$SYS/pcsx2/bios" "$H/ES-DE/custom_systems" "$BIOS/psx" "$BIOS/ps2" "$BIOS/saturn" "$BIOS/segacd" "$BIOS/pcengine" "$BIOS/neogeo" "$BIOS/dreamcast"
chown nobody:users "$ROMS/psp" "$ROMS/dc" "$BIOS" "$BIOS"/* 2>/dev/null

say "Downloading emulators (cores)"
TMP=$(mktemp -d)
for c in $CORES; do
  if curl -fsSL "$BUILDBOT/nightly/linux/x86_64/latest/${c}_libretro.so.zip" -o "$TMP/$c.zip" && unzip -oq "$TMP/$c.zip" -d "$RA/cores"; then
    echo "  ok  $c"
  else
    echo "  --  $c (not available, skipped)"
  fi
done

say "Downloading extra emulator files"
for a in Dolphin PPSSPP; do
  if curl -fsSL "$BUILDBOT/assets/system/$a.zip" -o "$TMP/$a.zip" && unzip -oq "$TMP/$a.zip" -d "$SYS"; then
    echo "  ok  $a"
  else
    echo "  --  $a (download failed)"
  fi
done
rm -rf "$TMP"

say "Telling EmulationStation about PS1, PS2, GameCube, Wii and Dreamcast"
cat >"$H/ES-DE/custom_systems/es_systems.xml" <<'EOF'
<?xml version="1.0"?>
<!-- Written by benflix-games-setup.sh. GameCube and Dreamcast use RomM folder names (roms/ngc, roms/dc). -->
<systemList>
    <system>
        <name>gc</name>
        <fullname>Nintendo GameCube</fullname>
        <path>%ROMPATH%/ngc</path>
        <extension>.ciso .CISO .dol .DOL .elf .ELF .gcm .GCM .gcz .GCZ .iso .ISO .m3u .M3U .rvz .RVZ .tgc .TGC .wia .WIA</extension>
        <command label="Dolphin (RetroArch)">%EMULATOR_RETROARCH% -L %CORE_RETROARCH%/dolphin_libretro.so %ROM%</command>
        <platform>gc</platform>
        <theme>gc</theme>
    </system>
    <system>
        <name>wii</name>
        <fullname>Nintendo Wii</fullname>
        <path>%ROMPATH%/wii</path>
        <extension>.ciso .CISO .dol .DOL .elf .ELF .gcm .GCM .gcz .GCZ .iso .ISO .m3u .M3U .rvz .RVZ .wad .WAD .wbfs .WBFS .wia .WIA</extension>
        <command label="Dolphin (RetroArch)">%EMULATOR_RETROARCH% -L %CORE_RETROARCH%/dolphin_libretro.so %ROM%</command>
        <platform>wii</platform>
        <theme>wii</theme>
    </system>
    <system>
        <name>dreamcast</name>
        <fullname>Sega Dreamcast</fullname>
        <path>%ROMPATH%/dc</path>
        <extension>.cdi .CDI .chd .CHD .cue .CUE .gdi .GDI .m3u .M3U .7z .7Z .zip .ZIP</extension>
        <command label="Flycast (RetroArch)">%EMULATOR_RETROARCH% -L %CORE_RETROARCH%/flycast_libretro.so %ROM%</command>
        <platform>dreamcast</platform>
        <theme>dreamcast</theme>
    </system>
    <system>
        <name>psx</name>
        <fullname>Sony PlayStation</fullname>
        <path>%ROMPATH%/psx</path>
        <extension>.bin .BIN .cbn .CBN .ccd .CCD .chd .CHD .cue .CUE .ecm .ECM .exe .EXE .img .IMG .iso .ISO .m3u .M3U .mds .MDS .pbp .PBP .psexe .PSEXE .psf .PSF .toc .TOC .z .Z .znx .ZNX .7z .7Z .zip .ZIP</extension>
        <command label="SwanStation (RetroArch)">%EMULATOR_RETROARCH% -L %CORE_RETROARCH%/swanstation_libretro.so %ROM%</command>
        <platform>psx</platform>
        <theme>psx</theme>
    </system>
    <system>
        <name>ps2</name>
        <fullname>Sony PlayStation 2</fullname>
        <path>%ROMPATH%/ps2</path>
        <extension>.bin .BIN .chd .CHD .cso .CSO .cue .CUE .elf .ELF .gz .GZ .img .IMG .iso .ISO .m3u .M3U .zso .ZSO</extension>
        <command label="LRPS2 (RetroArch)">%EMULATOR_RETROARCH% -L %CORE_RETROARCH%/pcsx2_libretro.so %ROM%</command>
        <platform>ps2</platform>
        <theme>ps2</theme>
    </system>
</systemList>
EOF
echo "  ok"

say "Grouping multi-disc PS1 games"
grouped=0
if [ -d "$ROMS/psx" ]; then
  cd "$ROMS/psx" || exit 1
  for f in *"(Disc 1)"*.chd *"(Disc 1)"*.cue; do
    [ -e "$f" ] || continue
    ext="${f##*.}"
    base=$(printf '%s' "${f%.*}" | sed -E 's/ ?\(Disc 1\)//')
    dir="$base.m3u"
    mkdir -p "$dir"
    prefix=$(printf '%s' "$f" | sed -E 's/\(Disc 1\).*//')
    for d in "$prefix"*"(Disc "*")"*; do
      [ -e "$d" ] && [ ! -d "$d" ] && mv "$d" "$dir/"
    done
    (cd "$dir" && ls -1 *."$ext" | sort -V >"$base.m3u")
    grouped=$((grouped + 1))
    echo "  ok  $base"
  done
  chown -R nobody:users "$ROMS/psx" 2>/dev/null
fi
echo "  $grouped multi-disc game(s) grouped"

say "Copying BIOS files"
cp -n "$BIOS"/psx/* "$BIOS"/saturn/* "$BIOS"/segacd/* "$BIOS"/pcengine/* "$BIOS"/neogeo/* "$SYS"/ 2>/dev/null
mkdir -p "$SYS/dc"
cp -n "$BIOS"/dreamcast/* "$SYS/dc/" 2>/dev/null
cp -n "$BIOS"/ps2/* "$SYS/pcsx2/bios/" 2>/dev/null
have() {
  if compgen -G "$1" >/dev/null; then
    echo "  ok  $2"
  else
    echo "  MISSING  $2 -> put it in \\\\TOWER\\Homelab\\Games\\bios\\$3"
  fi
}
have "$SYS/scph[15]00[0-2].bin" "PS1 BIOS (scph1001.bin or scph5501.bin)" psx
have "$SYS/pcsx2/bios/*" "PS2 BIOS (any scph*.bin)" ps2

say "Pointing RetroArch at the BIOS and system files folder"
CFG=$RA/retroarch.cfg
touch "$CFG"
sed -i '/^system_directory/d' "$CFG"
echo 'system_directory = "~/.config/retroarch/system"' >>"$CFG"
echo "  ok"

chown -R "$OWNER" "$H/.config" "$H/ES-DE"

# ---------- One-press play from Benflix ----------
# A small helper (port 8097) gives Benflix the game list and remembers which game to start, and a
# "Benflix" app in Moonlight starts that game straight away.
RAW=https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server
BF=$WOLF/benflix
HOST_IP=$(ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p')
[ -n "$HOST_IP" ] || HOST_IP=10.0.0.58
mkdir -p "$BF"

say "Downloading the Benflix play helper"
ok=1
for f in benflix-games-helper.py benflix-play-startup.sh benflix-run-game.sh benflix-skip-setup.sh; do
  curl -fsSL "$RAW/$f" -o "$BF/$f" || { echo "  --  couldn't download $f"; ok=0; }
done
[ $ok = 1 ] && echo "  ok"
chmod 755 "$BF"/*.sh
cat >"$BF/retroarch-benflix.cfg" <<'EOF'
system_directory = "/es/.config/retroarch/system"
savefile_directory = "/es/.config/retroarch/saves"
savestate_directory = "/es/.config/retroarch/states"
libretro_directory = "/es/.config/retroarch/cores"
joypad_autoconfig_dir = "/es/.config/retroarch/autoconfig"
input_joypad_driver = "sdl2"
input_quit_gamepad_combo = "4"
input_menu_toggle_gamepad_combo = "7"
quit_press_twice = "false"
config_save_on_exit = "false"
savestate_auto_save = "true"
savestate_auto_load = "true"
EOF

say "RomM login for the helper"
ENV=$BF/helper.env
if [ -f "$ENV" ]; then
  echo "  Using the login saved last time (delete $ENV to change it)"
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
docker rm -f benflix-games >/dev/null 2>&1
docker run -d --name benflix-games --restart unless-stopped --network host \
  --env-file "$ENV" -e ROMM_URL=http://127.0.0.1:8095 -e JELLYFIN_URL="$JF" -e WOLF_URL=http://127.0.0.1:47989 \
  -v "$BF:/benflix:rw" -v "$ROMS:/ROMs:ro" \
  python:3.12-alpine python -u /benflix/benflix-games-helper.py >/dev/null && sleep 3
if curl -fs -m 5 http://127.0.0.1:8097/health >/dev/null; then echo "  ok  http://$HOST_IP:8097"; else
  echo "  PROBLEM  The helper didn't start. Its log:"
  docker logs --tail 20 benflix-games 2>&1
fi

say "Adding the Benflix app to Moonlight (Wolf)"
CFGT=$WOLF/cfg/config.toml
if [ ! -f "$CFGT" ]; then
  echo "  PROBLEM  $CFGT not found"
else
  docker stop wolf >/dev/null 2>&1
  cp "$CFGT" "$CFGT.benflix-bak"
  docker run --rm -i -v "$WOLF/cfg:/cfg" -e ESHOME="$H" -e BF="$BF" -e ROMS="$ROMS" python:3.12-alpine python - <<'PYEOF'
import os, re, tomllib
p = "/cfg/config.toml"
lines = open(p).read().split("\n")
def is_header(l):
    s = l.strip()
    return s.startswith("[[profiles.apps]]") or s.startswith("[[profiles]]")
def blocks():
    """(start, end) of every app block"""
    out = []
    for i, l in enumerate(lines):
        if l.strip().startswith("[[profiles.apps]]"):
            j = i + 1
            while j < len(lines) and not is_header(lines[j]):
                j += 1
            out.append((i, j))
    return out
def title_of(b):
    for l in lines[b[0]:b[1]]:
        m = re.match(r"\s*title\s*=\s*['\"](.*)['\"]", l)
        if m:
            return m.group(1)
for b in reversed(blocks()):
    if title_of(b) == "Benflix":
        del lines[b[0]:b[1]]
es = next((b for b in blocks() if title_of(b) == "EmulationStation"), None)
if es is None:
    raise SystemExit("  PROBLEM  EmulationStation app not found in Wolf's settings")
new, skip = [], False
mounts = (f"'{os.environ['ROMS']}:/ROMs:rw', '{os.environ['ESHOME']}:/es:rw', '{os.environ['BF']}:/benflix:ro', "
          f"'{os.environ['BF']}/benflix-play-startup.sh:/opt/gow/startup-app.sh:ro', "
          f"'{os.environ['BF']}/benflix-skip-setup.sh:/etc/cont-init.d/setup-de.sh:ro'")
for l in lines[es[0]:es[1]]:
    if skip:
        if "]" in l:
            skip = False
        continue
    m = re.match(r"(\s*)mounts\s*=\s*\[", l)
    if m:
        new.append(f"{m.group(1)}mounts = [ {mounts} ]")
        skip = "]" not in l[l.index("[") :]
        continue
    l = re.sub(r"^(\s*title\s*=\s*)['\"]EmulationStation['\"]", r"\1'Benflix'", l)
    l = re.sub(r"^(\s*name\s*=\s*)['\"]WolfES-DE['\"]", r"\1'BenflixPlay'", l)
    new.append(l)
if not any("mounts" in l for l in new):
    raise SystemExit("  PROBLEM  Couldn't find the EmulationStation app's folders (mounts)")
start = next(i for i, l in enumerate(lines) if re.match(r"\s*id\s*=\s*['\"]moonlight-profile-id['\"]", l))
end = next((i for i in range(start + 1, len(lines)) if lines[i].strip().startswith("[[profiles]]")), len(lines))
while end > start and lines[end - 1].strip() == "":
    end -= 1
lines[end:end] = [""] + new
text = "\n".join(lines)
cfg = tomllib.loads(text)
top = next(pr for pr in cfg["profiles"] if pr.get("id") == "moonlight-profile-id")
assert any(a.get("title") == "Benflix" for a in top.get("apps", [])), "Benflix app missing after edit"
open(p, "w").write(text)
print("  ok  Benflix app added")
PYEOF
  [ $? = 0 ] || { echo "  Putting Wolf's settings back as they were"; cp "$CFGT.benflix-bak" "$CFGT"; }
  docker start wolf >/dev/null && echo "  ok  Wolf restarted"
fi

say "Done"
echo "Close Moonlight on the TV and reopen EmulationStation to see the changes."
echo "In Benflix, open Games: your games now show up by themselves, and pressing one starts it."
