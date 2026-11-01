#!/bin/bash
# Started by benflix-play-startup.sh inside the game app: opens the chosen game in RetroArch.
RA=/es/.config/retroarch
CORE=$(sed -n 1p /tmp/benflix-game)
ROM=$(sed -n 2p /tmp/benflix-game)
exec /usr/games/retroarch --config "$RA/retroarch.cfg" --appendconfig /benflix/retroarch-benflix.cfg \
  -L "$RA/cores/${CORE}_libretro.so" "$ROM"
