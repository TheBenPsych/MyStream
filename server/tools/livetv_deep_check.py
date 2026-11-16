"""Deep Live TV check (run on GitHub Actions): tries every copy of every line-up channel the way a TV
player would (main list -> picture size list -> a real video piece) and writes a report.

python server/tools/livetv_deep_check.py report.json
"""

import json
import os
import re
import sys
import time
import urllib.parse
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import benflix_livetv as lt  # noqa: E402
from benflix_livetv_lineup import LINEUP  # noqa: E402

EXO_UA = "ExoPlayerLib/2.19.1"
# What Android's own web code sends when the app's player sets no name (the TV player today)
DALVIK_UA = "Dalvik/2.1.0 (Linux; U; Android 11; 65S455 Build/RP1A.200720.012)"


def get(url, ua, limit=256 * 1024, timeout=15):
    req = lt.urllib.request.Request(url, headers={"User-Agent": ua, "Accept": "*/*"})
    with lt.urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.geturl(), r.read(limit)


def deep(url, ua):
    """(ok, step, detail)"""
    steps = []
    try:
        status, final, data = get(url, ua)
        steps.append(f"{status} {urllib.parse.urlsplit(final).netloc}")
        text = data.decode("utf-8", "replace")
        if not text.startswith("#EXTM3U"):
            return (data[:1] == b"\x47", "direct", " | ".join(steps))
        for _ in range(3):
            lines = [x.strip() for x in text.splitlines()]
            if "#EXT-X-STREAM-INF" in text:
                i = next(i for i, x in enumerate(lines) if x.startswith("#EXT-X-STREAM-INF"))
                nxt = next((x for x in lines[i + 1:] if x and not x.startswith("#")), "")
                status, final, data = get(urllib.parse.urljoin(final, nxt), ua)
                steps.append(f"variant {status} {urllib.parse.urlsplit(final).netloc}")
                text = data.decode("utf-8", "replace")
                continue
            segs = [x for x in lines if x and not x.startswith("#")]
            if not segs:
                return (False, "no-pieces", " | ".join(steps))
            # The last pieces are what a live player starts with
            seg = urllib.parse.urljoin(final, segs[-1] if len(segs) < 3 else segs[-3])
            status, sfinal, sdata = get(seg, ua, limit=4096)
            steps.append(f"piece {status} {urllib.parse.urlsplit(sfinal).netloc} {len(sdata)}b {sdata[:4].hex()}")
            ok = status == 200 and len(sdata) > 100 and not sdata.lstrip()[:1] in (b"<", b"{")
            return (ok, "piece", " | ".join(steps))
        return (False, "too-deep", " | ".join(steps))
    except Exception as e:
        steps.append(f"ERR {type(e).__name__}: {str(e)[:120]}")
        return (False, "error", " | ".join(steps))


def main(out):
    lineup = {lt._key(n): (t, n) for t, names in LINEUP.items() for n in names}
    copies = []
    for key, label, url, _ in lt.SOURCES:
        _, _, data = lt._get(url, timeout=60)
        found, _ = lt.parse_m3u(data.decode("utf-8", "replace"), key)
        copies += [c for c in found if lt.wanted(c) and lt._key(c["name"]) in lineup]
    seen, cand = set(), []
    for c in copies:
        if c["url"] not in seen:
            seen.add(c["url"])
            cand.append(c)
    print(f"{len(cand)} copies of {len(lineup)} line-up channels", flush=True)

    def check(c):
        quick = lt.alive(c["url"])
        d = deep(c["url"], EXO_UA)
        dv = deep(c["url"], DALVIK_UA)
        br = deep(c["url"], lt.UA)
        return {"dalvik": dv[0], "dalvik_detail": dv[2], "browser": br[0], "name": lt.tidy_name(c["name"]), "source": c["source"], "url": c["url"], "quick": quick,
                "deep": d[0], "step": d[1], "detail": d[2]}

    t = time.time()
    with ThreadPoolExecutor(max_workers=24) as pool:
        res = list(pool.map(check, cand))
    by_src = {}
    for r in res:
        s = by_src.setdefault(r["source"], {"copies": 0, "quick_ok": 0, "deep_ok": 0, "quick_ok_deep_bad": 0, "dalvik_ok": 0, "browser_ok": 0})
        s["dalvik_ok"] += r["dalvik"]
        s["browser_ok"] += r["browser"]
        s["copies"] += 1
        s["quick_ok"] += r["quick"]
        s["deep_ok"] += r["deep"]
        s["quick_ok_deep_bad"] += r["quick"] and not r["deep"]
    names = {}
    for r in res:
        n = names.setdefault(lt._key(r["name"]), {"name": r["name"], "deep_ok_sources": [], "quick_ok_sources": []})
        if r["deep"]:
            n["deep_ok_sources"].append(r["source"])
        if r["quick"]:
            n["quick_ok_sources"].append(r["source"])
    no_working = sorted(n["name"] for n in names.values() if not n["deep_ok_sources"])
    summary = {"minutes": round((time.time() - t) / 60, 1), "by_source": by_src,
               "lineup_channels": len(lineup), "found": len(names), "with_a_working_copy": len(names) - len(no_working),
               "no_working_copy": no_working}
    print(json.dumps(summary, indent=1), flush=True)
    for src in by_src:
        bad = [r for r in res if r["source"] == src and r["quick"] and not r["deep"]][:6]
        good = [r for r in res if r["source"] == src and r["deep"]][:2]
        print(f"--- {src} passes quick check but fails deep:")
        for r in bad:
            print(f"  {r['name']}: {r['url']}\n     {r['detail']}")
        print(f"--- {src} works:")
        for r in good:
            print(f"  {r['name']}: {r['detail']}")
    for r in [r for r in res if r["deep"] and not r["dalvik"]][:15]:
        print(f"DALVIK FAILS {r['source']} {r['name']}: {r['dalvik_detail']}")
    for nm in ("hotones", "80srewind", "classictvcomedy", "pluto"):
        for r in res:
            if nm in lt._key(r["name"]):
                print(f"[{nm}] {r['name']} {r['source']} quick={r['quick']} deep={r['deep']} {r['detail']}")
    with open(out, "w") as f:
        json.dump({"summary": summary, "results": res}, f, indent=1)


if __name__ == "__main__":
    main(sys.argv[1])
