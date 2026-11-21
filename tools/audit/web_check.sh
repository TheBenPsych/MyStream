#!/usr/bin/env bash
# Quick check of the browser version, Tower's helper and the computer app before an update goes in.
# Catches the simple slips that would otherwise reach Tower by themselves within the hour:
# a file that doesn't parse, a name that was never defined, the same name made twice.
# Run from the top of the repo: bash tools/audit/web_check.sh
set -euo pipefail
ROOT=$(pwd)
WEB="$ROOT/server/web"
bad=0

echo "== Every script parses"
for f in "$WEB"/js/*.js "$WEB"/sw.js "$ROOT"/desktop/app/*.js "$ROOT"/tools/audit/*.js; do
  node --check "$f" || bad=1
done

echo "== Tower's helper files parse"
python3 -m py_compile "$ROOT"/server/*.py "$ROOT"/tools/*.py "$ROOT"/tools/audit/*.py || bad=1
python3 -m pyflakes "$ROOT"/server/*.py "$ROOT"/tools/*.py "$ROOT"/tools/audit/*.py || bad=1

echo "== The list of files Tower downloads matches the real files"
diff <(cd "$WEB" && find . -type f ! -name files.txt | sed 's|^\./||' | sort) \
     <(grep -v '^#' "$WEB/files.txt" | sed '/^[[:space:]]*$/d' | sort) || { echo "server/web/files.txt is out of step with server/web"; bad=1; }

echo "== No name is used that was never defined"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$ROOT/tools/audit/web_check.eslint.mjs" "$WORK/eslint.config.mjs"
( cd "$WORK" && npm init -y >/dev/null && npm install --no-audit --no-fund --silent eslint@9.39.5 globals@17.14.0 )
# The main page's scripts share one set of names (they are plain scripts loaded one after another), so
# they are checked as one file, in the order index.html loads them.
: > "$WORK/main.js"
for f in $(grep -o 'web/js/[a-z]*\.js' "$WEB/index.html"); do
  echo "/* ---- $f ---- */" >> "$WORK/main.js"
  cat "$WEB/${f#web/}" >> "$WORK/main.js"
  echo >> "$WORK/main.js"
done
# Pages that load their own scripts (the 3D lobbies, the book reader) and the computer app
mkdir -p "$WORK/own" "$WORK/desktop"
for f in $(ls "$WEB"/js/*.js); do
  grep -q "web/js/$(basename "$f")" "$WEB/index.html" || cp "$f" "$WORK/own/"
done
cp "$ROOT"/desktop/app/*.js "$WORK/desktop/"
( cd "$WORK" && npx eslint --quiet main.js own desktop ) || { echo "Line numbers for main.js count through the scripts in index.html order; look for the /* ---- file ---- */ marker above the line in $WORK/main.js (rebuild it with this script)."; bad=1; }

[ "$bad" = 0 ] && echo "All good." || { echo "PROBLEMS found above."; exit 1; }
