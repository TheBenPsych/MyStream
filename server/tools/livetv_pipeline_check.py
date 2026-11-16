"""Runs the real Live TV build, then plays every channel through /livetv/play the way the TV does
(Tower's list -> the picture size list -> a real video piece). python server/tools/livetv_pipeline_check.py"""

import json
import os
import sys
import urllib.parse
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import benflix_livetv as lt  # noqa: E402

UA = "Dalvik/2.1.0 (Linux; U; Android 11; 65S455 Build/RP1A.200720.012)"


def get(url, limit=4096):
    req = lt.urllib.request.Request(url, headers={"User-Agent": UA})
    with lt.urllib.request.urlopen(req, timeout=15) as r:
        return r.status, r.geturl(), r.read(limit)


def tv_plays(key):
    found = lt.play(key)
    if not found:
        return False, "Tower: no copy plays"
    try:
        if found[0] == "go":
            st, _, d = get(found[1])
            return st == 200 and d[:1] == b"\x47", "plain stream"
        text = found[1].decode()
        for _ in range(2):
            lines = [x.strip() for x in text.splitlines() if x.strip() and not x.startswith("#")]
            if "#EXT-X-STREAM-INF" in text:
                st, final, d = get(lines[0], 512 * 1024)
                text = d.decode("utf-8", "replace")
                continue
            seg = urllib.parse.urljoin(final, lines[-1])
            st, _, d = get(seg)
            return st == 200 and len(d) > 100 and d[:1] not in (b"<", b"{"), f"piece {st}"
        return False, "too deep"
    except Exception as e:
        return False, f"{type(e).__name__}: {e}"[:150]


rep = lt.build()
print(json.dumps({k: rep[k] for k in ("kept", "dead", "checked", "types", "guide")}, indent=1))
with open(lt.M3U_FILE) as f:
    text = f.read()
assert lt.HOST_MARK in text and "jmp2" not in text, "list should only hold Tower's own addresses"
keys = sorted(lt._copies)
with ThreadPoolExecutor(max_workers=16) as pool:
    res = list(pool.map(tv_plays, keys))
bad = [(k, r[1]) for k, r in zip(keys, res) if not r[0]]
print(f"PLAYS ON THE TV: {len(keys) - len(bad)} of {len(keys)} channels in the list")
for k, why in bad:
    print(f"  doesn't play: {k}: {why} (copies: {len(lt._copies[k])})")
spares = sum(1 for k in keys if len(lt._copies[k]) > 1)
print(f"channels with a spare copy: {spares}")
print("missing from the line-up (no working copy tonight):", len(rep["missing"]))
