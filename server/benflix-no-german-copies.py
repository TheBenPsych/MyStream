#!/usr/bin/env python3
"""Benflix: stop Sonarr picking German-TV copies of shows, and swap out the ones you have.

German TV copies (often called "German DL") have German words baked into the picture, like the
episode name in yellow after the theme song. Subtitle settings can't remove that, so the file
has to be replaced.

What it does:
  1. Adds a Sonarr "custom format" called "Benflix: German copy" and gives it -10000 points in
     every quality profile, so Sonarr never downloads one again.
  2. Lists your episodes that are German copies (for the show you name, or every show with --all).
  3. With --go: deletes those files in Sonarr and asks it to search for new copies straight away.

Runs inside the Benflix helper (it already knows Sonarr's address and key):
  docker exec -i benflix-games python3 - "Big Bang Theory" < benflix-no-german-copies.py        (look only)
  docker exec -i benflix-games python3 - "Big Bang Theory" --go < benflix-no-german-copies.py   (swap)
Only uses the Python standard library.
"""

import json
import os
import re
import sys
import urllib.error
import urllib.request

FORMAT_NAME = "Benflix: German copy"
GERMAN_ID = 4  # Sonarr's number for German
SCORE = -10000
GERMAN_WORDS = re.compile(r"(?<![a-z])(german|deutsch)(?![a-z])", re.I)

URL = os.environ.get("SONARR_URL", "").rstrip("/")
KEY = os.environ.get("SONARR_KEY", "")


def api(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        f"{URL}/api/v3{path}",
        data=data,
        method=method,
        headers={"X-Api-Key": KEY, "Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


def block_german_copies():
    formats = api("GET", "/customformat") or []
    fmt = next((f for f in formats if f.get("name") == FORMAT_NAME), None)
    if fmt is None:
        fmt = api(
            "POST",
            "/customformat",
            {
                "name": FORMAT_NAME,
                "includeCustomFormatWhenRenaming": False,
                "specifications": [
                    {
                        "name": "German sound",
                        "implementation": "LanguageSpecification",
                        "negate": False,
                        "required": False,
                        "fields": [{"name": "value", "value": GERMAN_ID}],
                    },
                    {
                        "name": "German in the name",
                        "implementation": "ReleaseTitleSpecification",
                        "negate": False,
                        "required": False,
                        "fields": [{"name": "value", "value": r"\b(German|Deutsch)\b"}],
                    },
                ],
            },
        )
        print(f"  ok  Added '{FORMAT_NAME}' to Sonarr")
    else:
        print(f"  ok  '{FORMAT_NAME}' was already in Sonarr")
    for p in api("GET", "/qualityprofile") or []:
        items = p.get("formatItems") or []
        item = next((i for i in items if i.get("format") == fmt["id"]), None)
        if item and item.get("score") == SCORE:
            continue
        if item:
            item["score"] = SCORE
        else:
            items.append({"format": fmt["id"], "name": FORMAT_NAME, "score": SCORE})
        p["formatItems"] = items
        api("PUT", f"/qualityprofile/{p['id']}", p)
        print(f"  ok  Quality profile '{p.get('name')}' now never takes German copies")


def is_german(f):
    if any((lang or {}).get("id") == GERMAN_ID for lang in f.get("languages") or []):
        return True
    text = " ".join(str(f.get(k) or "") for k in ("sceneName", "relativePath", "path", "originalFilePath"))
    return bool(GERMAN_WORDS.search(text))


def main():
    args = [a for a in sys.argv[1:] if a]
    go = "--go" in args
    every = "--all" in args
    words = " ".join(a for a in args if not a.startswith("--")).strip().lower()
    if not URL or not KEY:
        print("  PROBLEM  The helper doesn't know Sonarr's address. Re-run the Benflix setup command, then try again.")
        return 1
    if not words and not every:
        print("  PROBLEM  Say which show, like \"Big Bang Theory\", or --all for every show.")
        return 1
    block_german_copies()
    shows = api("GET", "/series") or []
    if not every:
        shows = [s for s in shows if words in (s.get("title") or "").lower()]
        if not shows:
            print(f"  PROBLEM  Sonarr has no show called '{words}'.")
            return 1
    total = 0
    for s in sorted(shows, key=lambda x: x.get("sortTitle") or ""):
        files = [f for f in api("GET", f"/episodefile?seriesId={s['id']}") or [] if is_german(f)]
        if not files:
            if not every:
                print(f"  ok  {s['title']}: no German copies")
            continue
        total += len(files)
        print(f"  {s['title']}: {len(files)} German cop{'y' if len(files) == 1 else 'ies'}")
        if not go:
            for f in files[:5]:
                print(f"      {f.get('relativePath')}")
            if len(files) > 5:
                print(f"      ... and {len(files) - 5} more")
            continue
        episodes = api("GET", f"/episode?seriesId={s['id']}") or []
        file_ids = {f["id"] for f in files}
        episode_ids = [e["id"] for e in episodes if e.get("episodeFileId") in file_ids]
        for f in files:
            try:
                api("DELETE", f"/episodefile/{f['id']}")
            except urllib.error.HTTPError as e:
                print(f"      PROBLEM  Couldn't remove {f.get('relativePath')} ({e.code})")
        if episode_ids:
            api("POST", "/command", {"name": "EpisodeSearch", "episodeIds": episode_ids})
        print(f"      Swapping: removed them and Sonarr is searching for English copies now")
    if not go and total:
        print(f"\n{total} German cop{'y' if total == 1 else 'ies'} found. Nothing removed yet (add --go to swap them).")
    elif go and total:
        print(f"\nDone. Sonarr is downloading {total} new cop{'y' if total == 1 else 'ies'}. They show up in Benflix as they finish.")
    elif every:
        print("  ok  No German copies anywhere")
    return 0


if __name__ == "__main__":
    sys.exit(main())
