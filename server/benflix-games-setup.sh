#!/bin/bash
# Benflix game streaming: sets up the emulators for EmulationStation running in Wolf on Unraid.
# Safe to run again any time (for example after adding BIOS files or a new console).
#
# What it does:
#  1. Downloads the RetroArch "cores" (emulators) for every console we support
#  2. Downloads the extra files some cores need (Dolphin, PPSSPP)
#  3. Tells EmulationStation that GameCube and Dreamcast games live in roms/ngc and roms/dc, and to use the
#     RetroArch emulators for GameCube, Wii and PS2
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

say "Telling EmulationStation about GameCube, Wii, Dreamcast and PS2"
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
have "$SYS/scph5501.bin" "PS1 BIOS (scph5501.bin)" psx
have "$SYS/pcsx2/bios/*" "PS2 BIOS (any scph*.bin)" ps2

chown -R "$OWNER" "$H/.config" "$H/ES-DE"

say "Done"
echo "Close Moonlight on the TV and reopen EmulationStation to see the changes."
