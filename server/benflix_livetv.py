"""
Benflix Live TV: free, legal live channels for Jellyfin's Live TV, checked every night.

Where the channels come from (all free and public, no logins):
- Pluto TV, Samsung TV Plus, Plex, Roku and Tubi free channels (US lists rebuilt daily at
  github.com/BuddyChewChew/app-m3u-generator, guides from github.com/matthuisman/i.mjh.nz).
  These come with a TV guide.
- iptv-org's public list of English channels (https://github.com/iptv-org/iptv). No guide for these.

What it does, once at start-up (when the saved list is old) and then every 24 hours:
1. Downloads the lists and keeps only the channels in Ben's line-up (benflix_livetv_lineup.py, matched by name).
2. Tries every copy of them and drops the ones that don't answer (dead links).
3. Keeps one working copy of each channel (one with a guide first).
4. Numbers them in blocks by type: News 100s, Movies 200s, Comedy 300s and so on (CATEGORIES).
   Benflix uses those blocks to show the types on the Channels page.
5. Saves channels.m3u and a matching guide.xml, which Jellyfin reads:
     Tuner (M3U):   http://<server>:8097/livetv/channels.m3u
     Guide (XMLTV): http://<server>:8097/livetv/guide.xml
   http://<server>:8097/livetv/status shows when it last ran and how many channels it kept.
6. If a check finds far fewer channels working than last time (usually Tower's internet dropping for a
   moment), the old list is kept and the check runs again 30 minutes later.
7. The TV guide is fetched again every 3 hours (a saved copy is used if a download fails), and Jellyfin
   is asked to read it, so the guide never runs out.

Only uses the Python standard library.
"""

import gzip
import hashlib
import io
import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor

from benflix_livetv_lineup import LINEUP

DIR = "/livetv" if os.path.isdir("/livetv") else "/tmp/benflix-livetv"
M3U_FILE = os.path.join(DIR, "channels.m3u")
GUIDE_FILE = os.path.join(DIR, "guide.xml")
STATUS_FILE = os.path.join(DIR, "status.json")
# Every working copy of each channel, best first: /livetv/play/<channel> plays the first one that answers
COPIES_FILE = os.path.join(DIR, "copies.json")
# channels.m3u is saved with this in place of Tower's address and filled in from the address Jellyfin asked on
HOST_MARK = "__BENFLIX_HOST__"
# Benflix "This channel is broken": copies people reported from the app, kept out for a month
BROKEN_FILE = os.path.join(DIR, "broken.json")
BROKEN_DAYS = 30
EVERY = 24 * 3600
# The TV guide is fetched again this often (the free guides only reach 12-24 hours ahead, so a guide
# fetched once a night ran out by the evening)
GUIDE_EVERY = 3 * 3600
# Each free service's guide as last downloaded, used when the download fails
GUIDE_SAVED = os.path.join(DIR, "guide-{}.xml")
# What the guide needs to know about each kept channel, so the guide can be redone without a full check
FINAL_FILE = os.path.join(DIR, "final.json")
# A check that keeps far fewer channels than the last list (Tower's internet dropped, or a free service
# had a bad minute) doesn't replace the list. It's tried again later; after HOLD_MAX tries in a row
# that all say the same, the smaller list is used.
HOLD_SHARE = 0.6
HOLD_RETRY = 30 * 60
HOLD_MAX = 3
# Bump to make every Tower redo its list straight after updating
CHECK_VERSION = 2
UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

# key, name, channel list, guide (None = use the one the list names, if any)
G = "https://raw.githubusercontent.com"
PLAYLISTS = G + "/BuddyChewChew/app-m3u-generator/main/playlists"  # rebuilt daily from the i.mjh.nz channel data
GUIDES = G + "/matthuisman/i.mjh.nz/master"
SOURCES = [
    ("pluto", "Pluto TV", PLAYLISTS + "/plutotv_us.m3u", GUIDES + "/PlutoTV/us.xml.gz"),
    ("samsung", "Samsung TV Plus", PLAYLISTS + "/samsungtvplus_us.m3u", GUIDES + "/SamsungTVPlus/us.xml.gz"),
    ("plex", "Plex", PLAYLISTS + "/plex_us.m3u", GUIDES + "/Plex/us.xml.gz"),
    ("roku", "Roku", PLAYLISTS + "/roku_all.m3u", GUIDES + "/Roku/all.xml.gz"),
    ("tubi", "Tubi", PLAYLISTS + "/tubi_all.m3u", None),  # its guide is named in the list
    ("iptv", "iptv-org", "https://iptv-org.github.io/iptv/languages/eng.m3u", None),
]

# Channel types with the first channel number of each block. Keep in step with LiveTvGroups in the app
# (services/Channels.kt) and TYPES in server/web/js/livetv.js. 1000 (was Music) is left empty (Ben, 2026-11-08).
CATEGORIES = [
    ("News", 100),
    ("Movies", 200),
    ("Comedy", 300),
    ("Drama & Crime", 400),
    ("Reality", 500),
    ("Kids & Family", 600),
    ("Sports", 700),
    ("Sci-Fi & Anime", 800),
    ("Classic TV", 900),
    ("Documentaries & Nature", 1100),
    ("Food & Home", 1200),
    ("Holiday", 1300),
]
PER_BLOCK = 99

NOT_ENGLISH = re.compile(
    r"espa[nñ]ol|spanish|latino|latina|en vivo|novela|cine |telemundo|univision|\bmexic|portugu|brasil|"
    r"fran[cç]ais|french|deutsch|german|italiano|hindi|punjabi|tamil|telugu|urdu|bangla|arab|korean|"
    r"chinese|mandarin|japanese|tagalog|filipino|vietnam|russian|turk|polsk|greek|\bpinoy",
    re.I,
)
NOT_WANTED = re.compile(r"xxx|adult|\b18\+|erotic|playboy|\[geo-blocked\]|\[not 24/7\]", re.I)
# Whole groups left out: shopping and religious channels (iptv-org has hundreds of them)
NOT_WANTED_GROUPS = re.compile(r"shop|religio", re.I)

_lock = threading.Lock()
_state = {"running": False}
_copies = {}  # channel -> addresses of its working copies, best first (from COPIES_FILE)
_good = {}  # channel -> (address, when): the copy that played last, tried first for a few minutes
_bad = {}  # address -> when it last failed to answer: skipped for a while
GOOD_FOR = 180
BAD_FOR = 600
_admin = {"token": None}  # a Jellyfin admin's sign-in, only in memory, to ask Jellyfin to re-read the list


def _get(url, limit=None, timeout=30):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read(limit) if limit else r.read()
        return r.status, r.geturl(), data


def _attrs(line):
    return dict(re.findall(r'([\w-]+)="([^"]*)"', line))


def parse_m3u(text, source):
    """Channels in an M3U list as dicts. Channels that need special player settings are skipped."""
    out = []
    guide = None
    info = None
    needs_options = False
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#EXTM3U"):
            a = _attrs(line)
            guide = a.get("x-tvg-url") or a.get("url-tvg")
        elif line.startswith("#EXTINF"):
            a = _attrs(line)
            name = line.rsplit(",", 1)[-1].strip() if "," in line else a.get("tvg-name", "")
            info = {"name": name, "id": a.get("tvg-id") or a.get("channel-id") or "", "logo": a.get("tvg-logo", ""),
                    "group": a.get("group-title", ""), "source": source}
            needs_options = False
        elif line.startswith("#EXTVLCOPT") or line.startswith("#KODIPROP") or line.startswith("#EXTHTTP"):
            needs_options = True  # Jellyfin can't send the extra settings these channels need
        elif line.startswith("#"):
            continue
        elif info is not None:
            if not needs_options and line.startswith("http"):
                info["url"] = line
                out.append(info)
            info = None
    return out, guide


def tidy_name(name):
    name = re.sub(r"\s*\((?:\d{3,4}p|SD|HD)\)", "", name)  # iptv-org adds the picture size
    name = re.sub(r"\s*\[[^\]]*\]", "", name)
    return name.strip()


def wanted(ch):
    text = f"{ch['name']} {ch['group']}"
    return not NOT_WANTED.search(text) and not NOT_ENGLISH.search(text) and not NOT_WANTED_GROUPS.search(ch["group"])


def alive(url):
    """True when the channel answers with a playable stream, None when the service said "too many at once"."""
    try:
        status, final, data = _get(url, limit=256 * 1024, timeout=12)
        if status != 200 or not data:
            return False
        head = data[:7].decode("latin-1")
        if head != "#EXTM3U":
            return True  # A direct stream (not a playlist) that answered
        text = data.decode("utf-8", "replace")
        if "#EXT-X-STREAM-INF" in text:
            # A list of picture sizes: check the first one really plays
            lines = text.splitlines()
            for i, line in enumerate(lines):
                if line.startswith("#EXT-X-STREAM-INF"):
                    nxt = next((x.strip() for x in lines[i + 1:] if x.strip() and not x.startswith("#")), "")
                    if not nxt:
                        return False
                    status, _, sub = _get(urllib.parse.urljoin(final, nxt), limit=256 * 1024, timeout=12)
                    return status == 200 and (b"#EXTINF" in sub or b"#EXT-X-TARGETDURATION" in sub)
        return "#EXTINF" in text or "#EXT-X-TARGETDURATION" in text
    except urllib.error.HTTPError as e:
        return None if e.code == 429 else False
    except Exception:
        return False


def _check_all(urls):
    """alive() for every address. Services that answer "too many at once" (Plex, Tubi) are asked again
    slowly afterwards, so their channels aren't dropped just because the check was quick."""
    with ThreadPoolExecutor(max_workers=24) as pool:
        results = list(pool.map(alive, urls))
    for i, ok in enumerate(results):
        if ok is None:
            time.sleep(0.5)
            results[i] = bool(alive(urls[i]))
    return results


def _key(name):
    return re.sub(r"[^a-z0-9]", "", tidy_name(name).lower())


def build():
    started = time.time()
    report = {"started": int(started), "sources": {}, "lists": _lists_key()}
    channels = []
    guides = {}
    for key, label, url, guide in SOURCES:
        try:
            _, _, data = _get(url, timeout=60)
            found, named_guide = parse_m3u(data.decode("utf-8", "replace"), key)
            guides[key] = guide or named_guide
            kept = [c for c in found if wanted(c)]
            # US channels first, then UK/Canada, then the rest (each type only keeps 99 channels)
            kept.sort(key=lambda c: 0 if re.search(r"\.us\b", c["id"]) else 1 if re.search(r"\.(uk|ca)\b", c["id"]) else 2)
            report["sources"][label] = {"listed": len(found), "english": len(kept)}
            channels += kept
        except Exception as e:
            report["sources"][label] = {"error": f"Couldn't download the list ({e})"}

    # Only the channels in the line-up: {name key: (type, place in its type)}
    lineup = {_key(n): (t, i) for t, names in LINEUP.items() for i, n in enumerate(names)}
    seen_urls, candidates = set(), []
    for c in channels:
        if _key(c["name"]) in lineup and c["url"] not in seen_urls:
            seen_urls.add(c["url"])
            candidates.append(c)

    # Copies someone pressed "This channel is broken" on are left out, so another copy (or the next
    # channel of the same type) takes the place
    broken = load_broken()
    report["reported_broken"] = len(broken)
    candidates = [c for c in candidates if c["url"] not in broken]

    results = _check_all([c["url"] for c in candidates])
    working = [c for c, ok in zip(candidates, results) if ok]
    report["checked"] = len(candidates)
    report["dead"] = len(candidates) - len(working)

    # The guides, read once: used to pick the copy with show info, then written out for Jellyfin
    guide_data = load_guides(guides)
    with_guide = {src: ids for src, (_, ids) in guide_data.items()}
    ranked = sorted(
        enumerate(working),
        key=lambda ic: (0 if ic[1]["id"] in with_guide.get(ic[1]["source"], ()) else 1, 1 if ic[1]["source"] == "iptv" else 0, ic[0]),
    )
    # One copy of each channel: the best working one. The others are kept as spares for /livetv/play
    best, spares = {}, {}
    for _, c in ranked:
        best.setdefault(_key(c["name"]), c)
        spares.setdefault(_key(c["name"]), []).append(c["url"])

    # Number them in blocks by type, in the line-up's order
    bases = dict(CATEGORIES)
    per_cat = {}
    for k, c in sorted(best.items(), key=lambda kc: lineup[kc[0]][1]):
        name = lineup[k][0]
        block = per_cat.setdefault(name, [])
        if name in bases and len(block) < PER_BLOCK:
            c["number"] = bases[name] + len(block) + 1
            c["category"] = name
            block.append(c)
    report["missing"] = sorted(n for t, names in LINEUP.items() for n in names if _key(n) not in best)
    final = sorted((c for c in best.values() if "number" in c), key=lambda c: c["number"])
    report["kept"] = len(final)
    report["types"] = {name: len(per_cat.get(name, [])) for name, _ in CATEGORIES}

    # Safety net: a check that lost most of the channels at once is almost always Tower's internet
    # or one service having a bad minute, not hundreds of channels closing overnight. Keep the list
    # people have now and try again in a while.
    before = status()
    had = before.get("kept")
    held = int(before.get("held") or 0)
    if had and os.path.isfile(M3U_FILE) and len(final) < had * HOLD_SHARE and held < HOLD_MAX:
        before.pop("running", None)
        before.pop("ready", None)
        before["held"] = held + 1
        before["held_at"] = int(time.time())
        before["held_note"] = (f"Tonight's check only found {len(final)} of {had} channels working "
                               f"({report['dead']} of {report['checked']} copies didn't answer), so the list "
                               f"was kept and will be checked again in {HOLD_RETRY // 60} minutes.")
        before["retry_at"] = int(time.time()) + HOLD_RETRY
        before["lists"] = report["lists"]
        with open(STATUS_FILE, "w") as f:
            json.dump(before, f, indent=1)
        print("Live TV: " + before["held_note"], flush=True)
        return before

    # Each channel's address in the list is Tower's own /livetv/play/<channel>, never the free service's
    # address. Jellyfin names its channels after their address, so when tonight's check swaps a dead copy
    # for another one, Jellyfin's channel stays the same and keeps playing (before, it pointed at a copy
    # that was gone until Jellyfin's next guide refresh, so the channel wouldn't play).
    for c in final:
        c["key"] = _key(c["name"])
        c["tvg"] = "benflix." + c["key"]
    os.makedirs(DIR, exist_ok=True)
    copies = {c["key"]: spares.get(c["key"], [c["url"]]) for c in final}
    tmp = COPIES_FILE + ".tmp"
    with open(tmp, "w") as f:
        json.dump(copies, f)
    os.replace(tmp, COPIES_FILE)
    with _lock:
        _copies.clear()
        _copies.update(copies)
        _good.clear()
    tmp = M3U_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write("#EXTM3U\n")
        for c in final:
            name = tidy_name(c["name"]).replace(",", " ")
            f.write(
                f'#EXTINF:-1 tvg-id="{c["tvg"]}" tvg-chno="{c["number"]}" tvg-name="{name}" '
                f'tvg-logo="{c["logo"]}" group-title="{c["category"]}",{name}\n{HOST_MARK}/livetv/play/{c["key"]}.m3u8\n'
            )
    os.replace(tmp, M3U_FILE)

    tmp = FINAL_FILE + ".tmp"
    with open(tmp, "w") as f:
        json.dump({"guides": guides, "channels": [{"id": c["id"], "source": c["source"], "tvg": c["tvg"]} for c in final]}, f)
    os.replace(tmp, FINAL_FILE)
    report["guide"] = write_guide(final, guide_data)
    report["guide_at"] = int(time.time())
    report["guide_key"] = _guide_key(report["guide"])
    report["channels_key"] = hashlib.sha1(" ".join(sorted(copies)).encode()).hexdigest()[:12]
    report["finished"] = int(time.time())
    report["minutes"] = round((time.time() - started) / 60, 1)
    report["jellyfin_synced"] = before.get("jellyfin_synced")
    report["jellyfin_guide_synced"] = before.get("jellyfin_guide_synced")
    with open(STATUS_FILE, "w") as f:
        json.dump(report, f, indent=1)
    print(f"Live TV: kept {report['kept']} channels ({report['dead']} dead) in {report['minutes']} min", flush=True)
    return report


def load_guides(guides):
    """{source: (guide bytes, ids of channels that have shows)} for each list that has a guide"""
    out = {}
    for source, url in guides.items():
        if not url:
            continue
        saved = GUIDE_SAVED.format(source)
        data = None
        for attempt in range(3):
            try:
                _, _, data = _get(url, timeout=120)
                if data[:2] == b"\x1f\x8b":
                    data = gzip.decompress(data)
                if b"<programme" not in data:
                    raise ValueError("no shows in it")
                try:
                    with open(saved + ".tmp", "wb") as f:
                        f.write(data)
                    os.replace(saved + ".tmp", saved)
                except OSError:
                    pass
                break
            except Exception as e:
                data = None
                print(f"Live TV: couldn't read the {source} guide ({e})", flush=True)
                time.sleep(5 * (attempt + 1))
        if data is None:
            # The download failed: the copy from last time still has today's shows in it
            try:
                with open(saved, "rb") as f:
                    data = f.read()
                print(f"Live TV: using the saved {source} guide", flush=True)
            except OSError:
                pass
        if data is None:
            out[source] = (None, set())
            continue
        ids = set(re.findall(rb'<programme[^>]*?channel="([^"]+)"', data))
        out[source] = (data, {i.decode("utf-8", "replace") for i in ids})
    return out


def _guide_key(counts):
    return hashlib.sha1(json.dumps([counts, int(time.time())]).encode()).hexdigest()[:12]


def refresh_guide():
    """Fetches the TV guides again for the channels in the list and rewrites guide.xml, then asks
    Jellyfin to read it. Runs every few hours between the nightly channel checks."""
    try:
        with open(FINAL_FILE) as f:
            saved = json.load(f)
    except (OSError, ValueError):
        return None
    final = saved.get("channels") or []
    if not final:
        return None
    counts = write_guide(final, load_guides(saved.get("guides") or {}))
    with _lock:
        st = {}
        try:
            with open(STATUS_FILE) as f:
                st = json.load(f)
        except (OSError, ValueError):
            pass
        st["guide"] = counts
        st["guide_at"] = int(time.time())
        st["guide_key"] = _guide_key(counts)
        with open(STATUS_FILE, "w") as f:
            json.dump(st, f, indent=1)
    print(f"Live TV: guide fetched again ({counts})", flush=True)
    return counts


def write_guide(final, guide_data):
    """One guide.xml with only the kept channels, ids matching channels.m3u."""
    wanted_ids = {}
    for c in final:
        if c["id"]:
            wanted_ids.setdefault(c["source"], {})[c["id"]] = c["tvg"]
    counts = {}
    tmp = GUIDE_FILE + ".tmp"
    with open(tmp, "wb") as out:
        out.write(b'<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="Benflix">\n')
        for source, ids in wanted_ids.items():
            data, _ = guide_data.get(source, (None, None))
            if not data:
                if source in guide_data:
                    counts[source] = "Couldn't read the guide"
                continue
            try:
                n = 0
                for _, el in ET.iterparse(io.BytesIO(data), events=("end",)):
                    if el.tag == "channel" and el.get("id") in ids:
                        el.set("id", ids[el.get("id")])
                        out.write(ET.tostring(el, encoding="utf-8"))
                        el.clear()
                    elif el.tag == "programme":
                        if el.get("channel") in ids:
                            el.set("channel", ids[el.get("channel")])
                            out.write(ET.tostring(el, encoding="utf-8"))
                            n += 1
                        el.clear()
                counts[source] = n
            except Exception as e:
                counts[source] = f"Couldn't read the guide ({e})"
        out.write(b"</tv>\n")
    os.replace(tmp, GUIDE_FILE)
    return counts


def load_broken():
    """{stream address: report} for reports from the last BROKEN_DAYS days"""
    try:
        with open(BROKEN_FILE) as f:
            data = json.load(f)
    except (OSError, ValueError):
        return {}
    cutoff = time.time() - BROKEN_DAYS * 86400
    return {url: r for url, r in data.items() if isinstance(r, dict) and r.get("at", 0) >= cutoff}


def report_broken(body, user_id):
    """Benflix "This channel is broken" from the app: remembers the copy so tonight's check swaps it."""
    url = str(body.get("url") or "").strip()
    name = str(body.get("name") or "").strip()[:120]
    number = str(body.get("number") or "").strip()[:10]
    if not url and number:
        # The app may only know the channel number: find its address in tonight's list
        try:
            with open(M3U_FILE, encoding="utf-8") as f:
                lines = f.read().splitlines()
            for i, line in enumerate(lines):
                if f'tvg-chno="{number}"' in line and i + 1 < len(lines):
                    url = lines[i + 1].strip()
                    break
        except OSError:
            pass
    m = re.search(r"/livetv/play/([a-z0-9]+)", url)
    if m:
        # Tower's own address for the channel: the copy that was playing is the broken one
        bad = playing_copy(m.group(1))
        url = bad
        with _lock:
            _good.pop(m.group(1), None)
            if bad:
                _bad[bad] = time.time()
    if not url.startswith("http"):
        return {"ok": False, "message": "Couldn't tell which channel that was."}
    with _lock:
        data = load_broken()
        data[url] = {"name": name, "number": number, "at": int(time.time()), "by": user_id or ""}
        os.makedirs(DIR, exist_ok=True)
        tmp = BROKEN_FILE + ".tmp"
        with open(tmp, "w") as f:
            json.dump(data, f, indent=1)
        os.replace(tmp, BROKEN_FILE)
    return {"ok": True, "message": "Thanks! Glowfin uses another copy of this channel from now on."}


def _copies_for(key):
    with _lock:
        if not _copies:
            try:
                with open(COPIES_FILE) as f:
                    _copies.update(json.load(f))
            except (OSError, ValueError):
                pass
        return list(_copies.get(key, []))


def _absolute(text, base):
    """A playlist with every address in it made complete, so the player fetches the pieces straight from
    the free service (only the first list goes through Tower)."""
    out = []
    for line in text.splitlines():
        s = line.strip()
        if s and not s.startswith("#"):
            out.append(urllib.parse.urljoin(base, s))
        elif s.startswith("#") and 'URI="' in s:
            out.append(re.sub(r'URI="([^"]+)"', lambda m: f'URI="{urllib.parse.urljoin(base, m.group(1))}"', s))
        else:
            out.append(line)
    return "\n".join(out) + "\n"


def _answer(url):
    """("list", playlist bytes) or ("go", address) when this copy plays right now, else None."""
    try:
        status, final, data = _get(url, limit=1024 * 1024, timeout=8)
        if status != 200 or not data:
            return None
        if data[:7] != b"#EXTM3U":
            return ("go", final) if data[:1] == b"\x47" else None  # a plain video stream
        text = data.decode("utf-8", "replace")
        if "#EXT-X-STREAM-INF" in text:
            # A list of picture sizes: the first one must answer too
            lines = text.splitlines()
            i = next(i for i, x in enumerate(lines) if x.startswith("#EXT-X-STREAM-INF"))
            nxt = next((x.strip() for x in lines[i + 1:] if x.strip() and not x.startswith("#")), "")
            if not nxt:
                return None
            st, _, sub = _get(urllib.parse.urljoin(final, nxt), limit=256 * 1024, timeout=8)
            if st != 200 or not (b"#EXTINF" in sub or b"#EXT-X-TARGETDURATION" in sub):
                return None
        elif "#EXTINF" not in text and "#EXT-X-TARGETDURATION" not in text:
            return None
        return ("list", _absolute(text, final).encode())
    except Exception:
        return None


def play(key, skip=0):
    """/livetv/play/<channel>.m3u8: the first copy of the channel that plays right now. A copy that
    doesn't answer is skipped for 10 minutes and the next one is tried, so a channel keeps playing
    when one free service has a bad night. skip=N passes over N working copies (the TV asks for the
    next one when the first one won't play)."""
    key = re.sub(r"[^a-z0-9]", "", key.lower())
    urls = _copies_for(key)
    if not urls:
        return None
    now = time.time()
    with _lock:
        good = _good.get(key)
        broken = load_broken()
        bad = {u for u, t in _bad.items() if now - t < BAD_FOR}
    # Copies that answered lately first, then ones that didn't, then ones someone said were broken
    order = (
        [u for u in urls if u not in broken and u not in bad]
        + [u for u in urls if u in bad and u not in broken]
        + [u for u in urls if u in broken]
    )
    if good and now - good[1] < GOOD_FOR and good[0] in order and not skip:
        order.remove(good[0])
        order.insert(0, good[0])
    for url in order[:8]:
        found = _answer(url)
        with _lock:
            if found is None:
                _bad[url] = time.time()
                continue
            _bad.pop(url, None)
        if skip > 0:
            skip -= 1
            continue
        with _lock:
            _good[key] = (url, time.time())
        return found
    return None


def playing_copy(key):
    """The free service's address the channel played from last (for "This channel is broken")."""
    key = re.sub(r"[^a-z0-9]", "", key.lower())
    with _lock:
        good = _good.get(key)
    if good:
        return good[0]
    urls = _copies_for(key)
    return urls[0] if urls else ""


def m3u(host):
    """channels.m3u with Tower's address (as Jellyfin asked for it) filled in"""
    with open(M3U_FILE, encoding="utf-8") as f:
        text = f.read()
    host = re.sub(r"[^A-Za-z0-9.:\[\]-]", "", host or "") or "127.0.0.1:8097"
    return text.replace(HOST_MARK, "http://" + host).encode()


def _jellyfin(path, token, method="GET"):
    url = os.environ.get("JELLYFIN_URL", "http://127.0.0.1:8096").rstrip("/") + path
    req = urllib.request.Request(url, method=method, data=b"" if method == "POST" else None,
                                 headers={"Authorization": f'MediaBrowser Token="{token}"', "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return r.read()


def sync_jellyfin(token=None):
    """When the channel list changed since Jellyfin last read it, ask Jellyfin to read it again now
    ("Refresh Guide" in Jellyfin), so new and removed channels show up straight away. Uses the sign-in
    of an admin who used Benflix since the helper started (kept in memory only)."""
    if token:
        _admin["token"] = token
    # Else the admin sign-in Tower's skip-times finder keeps (benflix_skips), if it has one
    skips = sys.modules.get("benflix_skips")
    token = _admin["token"] or (getattr(skips, "_admin", None) or {}).get("token")
    if not token:
        return
    st = status()
    want = st.get("channels_key")
    guide = st.get("guide_key")
    if not want or st.get("running") or (st.get("jellyfin_synced") == want and st.get("jellyfin_guide_synced") == guide):
        return
    try:
        tasks = json.loads(_jellyfin("/ScheduledTasks?isHidden=false", token))
        task = next((t for t in tasks if t.get("Key") == "RefreshGuide"), None)
        if not task:
            return
        if task.get("State") != "Running":
            _jellyfin(f"/ScheduledTasks/Running/{task['Id']}", token, "POST")
        st_file = {}
        try:
            with open(STATUS_FILE) as f:
                st_file = json.load(f)
        except (OSError, ValueError):
            pass
        st_file["jellyfin_synced"] = want
        st_file["jellyfin_guide_synced"] = guide
        with open(STATUS_FILE, "w") as f:
            json.dump(st_file, f, indent=1)
        print("Live TV: asked Jellyfin to read the new channel list", flush=True)
    except Exception as e:
        print(f"Live TV: couldn't ask Jellyfin to read the channel list ({e})", flush=True)


def refresh():
    """Runs build() once in the background (does nothing if it's already running)."""
    with _lock:
        if _state["running"]:
            return False
        _state["running"] = True

    def run():
        try:
            build()
            _state["running"] = False
            sync_jellyfin()
        except Exception as e:
            print(f"Live TV update failed: {e!r}", flush=True)
        finally:
            _state["running"] = False

    threading.Thread(target=run, daemon=True).start()
    return True


def status():
    out = {"running": _state["running"], "ready": os.path.isfile(M3U_FILE)}
    try:
        with open(STATUS_FILE) as f:
            out.update(json.load(f))
    except (OSError, ValueError):
        pass
    return out


def _lists_key():
    """Changes whenever the lists in SOURCES, the channel types or the line-up change, so an update remakes the list"""
    return hashlib.sha1(json.dumps([SOURCES, CATEGORIES, PER_BLOCK, LINEUP, CHECK_VERSION]).encode()).hexdigest()[:12]


def start():
    """Keeps the channel list fresh: now if it's old, missing or made from other lists, then every 24 hours."""

    def loop():
        # Give Tower's network a couple of minutes after a restart before checking hundreds of channels
        time.sleep(120)
        while True:
            try:
                st = status()
                now = time.time()
                try:
                    age = now - os.path.getmtime(M3U_FILE)
                except OSError:
                    age = EVERY
                if st.get("lists") != _lists_key() or not os.path.isfile(COPIES_FILE):
                    age = EVERY  # Benflix was updated with different lists: make it again now
                if st.get("retry_at"):
                    due = now >= st["retry_at"]  # the last check was held back: check again after a while
                else:
                    due = age >= EVERY - 600
                if due:
                    refresh()
                elif now - (st.get("guide_at") or 0) >= GUIDE_EVERY and not st.get("running"):
                    refresh_guide()
                    sync_jellyfin()
            except Exception as e:
                print(f"Live TV: {e!r}", flush=True)
            time.sleep(600)

    threading.Thread(target=loop, daemon=True).start()


def file_for(path):
    return {"/livetv/channels.m3u": (M3U_FILE, "audio/x-mpegurl"), "/livetv/guide.xml": (GUIDE_FILE, "application/xml")}.get(path)
