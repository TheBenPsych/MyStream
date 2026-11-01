#!/bin/bash
# Runs inside the "Benflix" game app in Wolf (it replaces the app's normal start script).
# Starts the game Benflix asked for, straight in RetroArch. Quitting RetroArch ends the stream,
# which takes you back to Benflix.
source /opt/gow/bash-lib/utils.sh
source /opt/gow/launch-comp.sh

NEXT=/benflix/next-game
RA=/es/.config/retroarch
if [ ! -s "$NEXT" ]; then
  gow_log "[benflix] No game picked. Start games from the Games tab in Benflix."
  exit 0
fi
CORE=$(sed -n 1p "$NEXT")
ROM=$(sed -n 2p "$NEXT")
gow_log "[benflix] Starting $ROM with $CORE"
printf '%s\n%s\n' "$CORE" "$ROM" >/tmp/benflix-game
launcher /benflix/run-game.sh
