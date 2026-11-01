#!/bin/bash
# Started by benflix-play-startup.sh inside the game app: opens the chosen game in RetroArch, with button hints.
# When RetroArch closes, the app's screen (Sway) is closed too, which ends the stream and takes you back to Benflix.
RA=/es/.config/retroarch
CORE=$(sed -n 1p /tmp/benflix-game)
ROM=$(sed -n 2p /tmp/benflix-game)
CFG=/benflix/retroarch-benflix.cfg

# How wide each wide-screen console's picture is (width / height). These get a slim hint bar on top,
# with the game moved down just below it. Everything else is narrow enough to have black bars at the
# sides, where the hints go instead.
ASPECT=""
case $CORE in
  mgba) ASPECT=1.5 ;;                 # Game Boy Advance
  ppsspp) ASPECT=1.7647 ;;            # PSP
  handy | mednafen_lynx) ASPECT=1.5686 ;;
  mednafen_wswan) ASPECT=1.5556 ;;
  mednafen_vb) ASPECT=1.7143 ;;
  dolphin) case $ROM in /ROMs/wii/*) ASPECT=1.7778 ;; esac ;; # Wii is wide; GameCube is 4:3
esac

if [ -n "$ASPECT" ] && [ -f /benflix/retroarch-hints-top.cfg ]; then
  # The screen size of this stream (it follows the TV or PC that is playing)
  OUT=$(swaymsg -t get_outputs -r 2>/dev/null | tr -d ' \n')
  MODE=${OUT#*\"current_mode\":}
  W=$(printf '%s' "$MODE" | grep -o '"width":[0-9]*' | head -1 | cut -d: -f2)
  H=$(printf '%s' "$MODE" | grep -o '"height":[0-9]*' | head -1 | cut -d: -f2)
  [ -n "$W" ] && [ -n "$H" ] || { W=1920; H=1080; }
  read -r VX VY VW VH < <(awk -v w="$W" -v h="$H" -v a="$ASPECT" 'BEGIN {
    bar = int(h * 0.07 + 0.5); gh = h - bar; gw = int(gh * a + 0.5)
    if (gw > w) { gw = w; gh = int(w / a + 0.5) }
    printf "%d %d %d %d\n", int((w - gw) / 2), bar, gw, gh }')
  cat >/tmp/benflix-viewport.cfg <<CFGEOF
aspect_ratio_index = "23"
video_scale_integer = "false"
custom_viewport_x = "$VX"
custom_viewport_y = "$VY"
custom_viewport_width = "$VW"
custom_viewport_height = "$VH"
CFGEOF
  CFG="$CFG|/benflix/retroarch-hints-top.cfg|/tmp/benflix-viewport.cfg"
elif [ -f /benflix/retroarch-hints.cfg ]; then
  CFG="$CFG|/benflix/retroarch-hints.cfg"
fi

/usr/games/retroarch --config "$RA/retroarch.cfg" --appendconfig "$CFG" \
  -L "$RA/cores/${CORE}_libretro.so" "$ROM"
swaymsg exit >/dev/null 2>&1 || pkill -x sway
