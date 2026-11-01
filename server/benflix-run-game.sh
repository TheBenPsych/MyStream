#!/bin/bash
# Started by benflix-play-startup.sh inside the game app: opens the chosen game in RetroArch.
# When RetroArch closes, the app's screen (Sway) is closed too, which ends the stream and takes you back to Benflix.
RA=/es/.config/retroarch
CORE=$(sed -n 1p /tmp/benflix-game)
ROM=$(sed -n 2p /tmp/benflix-game)
/usr/games/retroarch --config "$RA/retroarch.cfg" --appendconfig /benflix/retroarch-benflix.cfg \
  -L "$RA/cores/${CORE}_libretro.so" "$ROM"
swaymsg exit >/dev/null 2>&1 || pkill -x sway
