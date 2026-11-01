#!/bin/bash
# Started by benflix-play-startup.sh inside the game app: opens the chosen game in RetroArch.
# When RetroArch closes, the app's screen (Sway) is closed too, which ends the stream and takes you back to Benflix.
RA=/es/.config/retroarch
CORE=$(sed -n 1p /tmp/benflix-game)
ROM=$(sed -n 2p /tmp/benflix-game)
CFG=/benflix/retroarch-benflix.cfg
# Consoles with a 4:3 picture have black bars at the sides: show the button hints there
case $CORE in
  gambatte | mgba | desmume | melonds | ppsspp | dolphin | handy | mednafen_lynx | mednafen_ngp | mednafen_wswan | mednafen_vb) ;;
  *) [ -f /benflix/retroarch-hints.cfg ] && CFG="$CFG|/benflix/retroarch-hints.cfg" ;;
esac
/usr/games/retroarch --config "$RA/retroarch.cfg" --appendconfig "$CFG" \
  -L "$RA/cores/${CORE}_libretro.so" "$ROM"
swaymsg exit >/dev/null 2>&1 || pkill -x sway
