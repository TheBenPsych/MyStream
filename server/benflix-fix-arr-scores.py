#!/usr/bin/env python3
"""Benflix: tidy up Sonarr and Radarr scoring so they grab English copies of good quality.

Why: a German TV copy of The Big Bang Theory had German episode names baked into the picture.
Nothing in the normal profiles stopped foreign copies, a few scores were backwards, and
"min score 100" made ordinary releases get skipped without a word.

What it changes (prints every change; run again any time, it only fixes what is still off):
  * Every profile: new "Benflix: German copy" format (German sound, or German in the name) = -10000.
  * Normal (non-anime) profiles: "Language: Not English" = -10000, min score = 0.
  * Sonarr: "LQ" and "LQ (Release Title)" = -10000 wherever they were above 0.
  * Radarr: new "Benflix: YTS" format = -10000 (YTS files are tiny and blurry).
Anime profiles keep their Japanese rules.

Runs inside the Benflix helper, which already knows both apps' addresses and keys:
  docker exec -i benflix-games python3 - < benflix-fix-arr-scores.py
Only uses the Python standard library.
"""

import json
import os
import sys
import urllib.request

BLOCK = -10000
ENGLISH, GERMAN = 1, 4  # the apps' numbers for these languages


def api(app, method, path, body=None):
    url, key = os.environ.get(app + "_URL", "").rstrip("/"), os.environ.get(app + "_KEY", "")
    req = urllib.request.Request(
        f"{url}/api/v3{path}",
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
        headers={"X-Api-Key": key, "Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


def spec(name, impl, value, negate=False):
    return {"name": name, "implementation": impl, "negate": negate, "required": False, "fields": [{"name": "value", "value": value}]}


def ensure_format(app, formats, name, specs):
    f = next((x for x in formats if x.get("name") == name), None)
    if f:
        return f
    f = api(app, "POST", "/customformat", {"name": name, "includeCustomFormatWhenRenaming": False, "specifications": specs})
    formats.append(f)
    print(f"  ok  Added '{name}'")
    return f


def fix(app):
    if not os.environ.get(app + "_URL"):
        print(f"{app.title()}: not connected to the Benflix helper, skipped (re-run the setup command to connect it)")
        return
    print(f"===== {app.title()} =====")
    formats = api(app, "GET", "/customformat") or []
    by_name = {f["name"]: f for f in formats}
    german = ensure_format(
        app,
        formats,
        "Benflix: German copy",
        [spec("German sound", "LanguageSpecification", GERMAN), spec("German in the name", "ReleaseTitleSpecification", r"\b(German|Deutsch)\b")],
    )
    not_english = by_name.get("Language: Not English") or ensure_format(
        app, formats, "Language: Not English", [spec("Not English", "LanguageSpecification", ENGLISH, negate=True)]
    )
    yts = ensure_format(app, formats, "Benflix: YTS", [spec("YTS", "ReleaseGroupSpecification", r"^(YTS(\.MX|\.AM|\.LT)?)$")]) if app == "RADARR" else None
    lq_ids = {by_name[n]["id"] for n in ("LQ", "LQ (Release Title)") if n in by_name} if app == "SONARR" else set()

    for p in api(app, "GET", "/qualityprofile") or []:
        anime = "anime" in (p.get("name") or "").lower()
        want = {german["id"]: BLOCK}
        if not anime:
            want[not_english["id"]] = BLOCK
        if yts:
            want[yts["id"]] = BLOCK
        items = p.get("formatItems") or []
        for i in items:
            if i.get("format") in lq_ids and (i.get("score") or 0) > 0:
                want[i["format"]] = BLOCK
        changes = []
        for fid, score in want.items():
            item = next((i for i in items if i.get("format") == fid), None)
            name = next((f["name"] for f in formats if f["id"] == fid), str(fid))
            if item is None:
                items.append({"format": fid, "name": name, "score": score})
            elif item.get("score") != score:
                item["score"] = score
            else:
                continue
            changes.append(f"{name} = {score}")
        if not anime and (p.get("minFormatScore") or 0) > 0:
            changes.append(f"min score {p['minFormatScore']} -> 0")
            p["minFormatScore"] = 0
        if changes:
            p["formatItems"] = items
            api(app, "PUT", f"/qualityprofile/{p['id']}", p)
            print(f"  ok  Profile '{p['name']}': " + "; ".join(changes))
        else:
            print(f"  ok  Profile '{p['name']}': already right")


def recyclarr_note():
    print(
        "\nIf Recyclarr manages these profiles, its next sync can put back the old LQ scores and min score."
        " Tell Claude and it will give you the matching Recyclarr change."
    )


def main():
    for app in ("SONARR", "RADARR"):
        try:
            fix(app)
        except Exception as e:  # keep going with the other app
            print(f"  PROBLEM  {app.title()}: {e}")
    recyclarr_note()
    print("\nDone. New downloads now skip German and other non-English copies of normal shows and movies.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
