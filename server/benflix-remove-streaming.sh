#!/bin/bash
# Removes the old game streaming setup (Wolf + Moonlight) from Tower. Games now play right on the TV
# or phone inside Benflix, so it's not needed. Your games, BIOS files, saves, RomM and the Benflix
# helper are NOT touched.
#
# Run on the Unraid server (Tools > Terminal):
#   curl -fsSL https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/benflix-remove-streaming.sh | bash

WOLF=/mnt/user/appdata/wolf
APP=/mnt/user/appdata/benflix-games
RAW=https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server

say() { printf '\n== %s ==\n' "$1"; }

say "Keeping the RomM login for the helper"
mkdir -p "$APP"
if [ ! -f "$APP/helper.env" ] && [ -f "$WOLF/benflix/helper.env" ]; then
  cp "$WOLF/benflix/helper.env" "$APP/helper.env" && chmod 600 "$APP/helper.env"
  echo "  ok  Copied it to $APP"
else
  echo "  ok  Already saved"
fi

say "Removing the streaming containers"
found=0
while read -r name image; do
  [ -z "$name" ] && continue
  case "$name" in benflix-games) continue ;; esac
  if echo "$name $image" | grep -qiE 'wolf|games-on-whales'; then
    docker rm -f "$name" >/dev/null 2>&1 && echo "  removed  $name ($image)"
    found=1
  fi
done < <(docker ps -a --format '{{.Names}} {{.Image}}')
[ $found = 1 ] || echo "  ok  None left"

say "Removing the streaming images (frees disk space)"
found=0
while read -r ref id; do
  [ -z "$id" ] && continue
  docker rmi -f "$id" >/dev/null 2>&1 && echo "  removed  $ref"
  found=1
done < <(docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' | grep -iE 'wolf|games-on-whales')
[ $found = 1 ] || echo "  ok  None left"

say "Removing the streaming folder"
if [ -d "$WOLF" ]; then
  echo "  $(du -sh "$WOLF" 2>/dev/null | cut -f1) in $WOLF"
  rm -rf "$WOLF" && echo "  removed  $WOLF"
else
  echo "  ok  Already gone"
fi

say "Checking Unraid's startup file"
# Not changed automatically: a wrong edit here could stop Tower from starting properly. These lines only
# set up controllers for streaming; with the folder gone they do nothing, so leaving them is harmless.
GO=/boot/config/go
if [ -f "$GO" ] && grep -qi 'wolf' "$GO"; then
  echo "  These streaming lines are still in $GO (harmless, send them to Claude if you want them gone):"
  grep -n -i 'wolf' "$GO" | sed 's/^/    /'
else
  echo "  ok  Nothing about streaming in it"
fi

say "Restarting the Benflix helper without the old folder"
curl -fsSL "$RAW/benflix-games-helper-setup.sh" | bash

echo
echo "Old game streaming removed. You can also uninstall the Moonlight app from your TV and phones."
