"""
Benflix Live TV: free, legal live channels for Jellyfin's Live TV, checked every night.

Where the channels come from (all free and public, no logins):
- Pluto TV, Samsung TV Plus, Plex and Roku free channels (US lists rebuilt daily at
  github.com/BuddyChewChew/app-m3u-generator, guides from github.com/matthuisman/i.mjh.nz).
  These come with a TV guide.
- iptv-org's public list of English channels (https://github.com/iptv-org/iptv). No guide for these.

What it does, once at start-up (when the saved list is old) and then every 24 hours:
1. Downloads the lists and keeps only English channels (no Spanish/other-language channels, no adult ones,
   none marked geo-blocked or "not 24/7").
2. Removes doubles (the same channel in two lists).
3. Tries every channel and drops the ones that don't answer (dead links).
4. Sorts them by type and numbers them in blocks: News 100s, Movies 200s, Comedy 300s and so on (CATEGORIES).
   Benflix uses those blocks to show the groups on the Channels page.
5. Saves channels.m3u and a matching guide.xml, which Jellyfin reads:
     Tuner (M3U):   http://<server>:8097/livetv/channels.m3u
     Guide (XMLTV): http://<server>:8097/livetv/guide.xml
   http://<server>:8097/livetv/status shows when it last ran and how many channels it kept.

Only uses the Python standard library.
"""

import gzip
import hashlib
import io
import json
import os
import re
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor

DIR = "/livetv" if os.path.isdir("/livetv") else "/tmp/benflix-livetv"
M3U_FILE = os.path.join(DIR, "channels.m3u")
GUIDE_FILE = os.path.join(DIR, "guide.xml")
STATUS_FILE = os.path.join(DIR, "status.json")
EVERY = 24 * 3600
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
    ("iptv", "iptv-org", "https://iptv-org.github.io/iptv/languages/eng.m3u", None),
]

# Channel types, in the order they show in Benflix, with the first channel number of each block.
# Keep in step with LiveTvGroups in the app (services/LiveTv.kt).
CATEGORIES = [
    ("News", 100, r"news|weather|politic|business|finance|cnbc|bloomberg|cbsn|abc news|nbc news|fox weather|newsmax"),
    ("Movies", 200, r"movie|film|cinema|western|thriller|horror|action"),
    ("Comedy", 300, r"comed|funny|laugh|sitcom|stand.?up|snl|\bcomic"),
    ("Drama & Crime", 400, r"drama|crime|mystery|detective|cops|court|investigat|true crime|soap|law"),
    ("Reality", 500, r"reality|competition|game show|dating|paranormal|lifestyle"),
    ("Kids & Family", 600, r"kid|family|cartoon|children|toon|junior|nick|baby|teen|animation"),
    ("Sports", 700, r"sport|football|soccer|nfl|nba|mlb|nhl|golf|tennis|racing|motor|fight|wrestl|boxing|ufc|outdoor|fishing|hunting|poker"),
    ("Sci-Fi & Anime", 800, r"sci.?fi|science fiction|anime|fantasy|star trek|doctor who|space"),
    ("Classic TV", 900, r"classic|retro|vintage|\b(?:50|60|70|80|90)s\b|throwback|nostalg"),
    ("Music", 1000, r"music|concert|karaoke|\bhits\b|radio|mtv|vevo|country|hip.?hop|jazz|rock"),
    ("Documentaries & Nature", 1100, r"documentar|nature|history|science|animal|wild|travel|discovery|explor|planet|earth|educat"),
    ("Food & Home", 1200, r"food|cook|kitchen|chef|home|garden|diy|design|house|craft|shop"),
    ("Other", 1300, r""),
]
# The best 40 of each type (Ben, 2026-11-06): ones with a guide first, then the big free TV services
PER_BLOCK = 40

# News: only these US national channels (Ben's pick, 2026-11-06), matched on the channel's name.
# No city stations, other countries or iptv-org copies.
NEWS_KEEP = [
    r"abc news live$", r"cbs news 24/7", r"nbc news now", r"today all day", r"cnn headlines", r"livenow from fox",
    r"scripps news", r"reuters", r"the hill$", r"bloomberg tv\+?$", r"bloomberg originals$", r"yahoo finance",
    r"business insider", r"fox weather", r"accuweather", r"weathernation$",
]
# Opinion and talk news, left out unless switched on
NEWS_OPINION = [
    r"newsmax2?$", r"real america'?s voice", r"oan plus$", r"blaze", r"the first$", r"daily wire", r"salem news",
    r"tyt network",
]
KEEP_OPINION_NEWS = False


def news_wanted(ch):
    """Only the main US news channels, once each"""
    name = tidy_name(ch["name"]).lower()
    keep = NEWS_KEEP + (NEWS_OPINION if KEEP_OPINION_NEWS else [])
    return any(re.search(p, name) for p in keep)

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


def category(ch):
    text = f"{ch['group']} {ch['name']}".lower()
    for name, base, pattern in CATEGORIES:
        # The list's own group says the most, so check it first
        if pattern and ch["group"] and re.search(pattern, ch["group"].lower()):
            return name, base
    for name, base, pattern in CATEGORIES:
        if not pattern or re.search(pattern, text):
            return name, base
    return CATEGORIES[-1][0], CATEGORIES[-1][1]


def alive(url):
    """True when the channel answers with a playable stream."""
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
    except Exception:
        return False


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

    # The same channel can be in two lists; keep the first (the lists with a guide come first)
    seen_urls, seen_names, unique = set(), set(), []
    for c in channels:
        k = _key(c["name"])
        if c["url"] in seen_urls or (k and k in seen_names):
            continue
        seen_urls.add(c["url"])
        seen_names.add(k)
        unique.append(c)

    with ThreadPoolExecutor(max_workers=24) as pool:
        results = list(pool.map(lambda c: alive(c["url"]), unique))
    working = [c for c, ok in zip(unique, results) if ok]
    report["checked"] = len(unique)
    report["dead"] = len(unique) - len(working)

    # The guides, read once: used to put channels with show info first, then written out for Jellyfin
    guide_data = load_guides(guides)
    with_guide = {src: ids for src, (_, ids) in guide_data.items()}
    ranked = sorted(
        enumerate(working),
        key=lambda ic: (0 if ic[1]["id"] in with_guide.get(ic[1]["source"], ()) else 1, 1 if ic[1]["source"] == "iptv" else 0, ic[0]),
    )

    # Number them in blocks by type
    per_cat = {}
    news_seen = set()
    for _, c in ranked:
        name, base = category(c)
        if name == "News":
            if not news_wanted(c) or _key(c["name"]) in news_seen:
                continue
            news_seen.add(_key(c["name"]))
        block = per_cat.setdefault(name, [])
        if len(block) < PER_BLOCK:
            c["number"] = base + len(block) + 1
            c["category"] = name
            block.append(c)
    final = sorted((c for c in working if "number" in c), key=lambda c: c["number"])
    report["kept"] = len(final)
    report["types"] = {name: len(per_cat.get(name, [])) for name, _, _ in CATEGORIES}

    for c in final:
        c["tvg"] = f"{c['source']}.{c['id']}" if c["id"] else f"{c['source']}.n{c['number']}"
    os.makedirs(DIR, exist_ok=True)
    tmp = M3U_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write("#EXTM3U\n")
        for c in final:
            name = tidy_name(c["name"]).replace(",", " ")
            f.write(
                f'#EXTINF:-1 tvg-id="{c["tvg"]}" tvg-chno="{c["number"]}" tvg-name="{name}" '
                f'tvg-logo="{c["logo"]}" group-title="{c["category"]}",{name}\n{c["url"]}\n'
            )
    os.replace(tmp, M3U_FILE)

    report["guide"] = write_guide(final, guide_data)
    report["finished"] = int(time.time())
    report["minutes"] = round((time.time() - started) / 60, 1)
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
        try:
            _, _, data = _get(url, timeout=120)
            if data[:2] == b"\x1f\x8b":
                data = gzip.decompress(data)
            ids = set(re.findall(rb'<programme[^>]*?channel="([^"]+)"', data))
            out[source] = (data, {i.decode("utf-8", "replace") for i in ids})
        except Exception as e:
            out[source] = (None, set())
            print(f"Live TV: couldn't read the {source} guide ({e})", flush=True)
    return out


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


def refresh():
    """Runs build() once in the background (does nothing if it's already running)."""
    with _lock:
        if _state["running"]:
            return False
        _state["running"] = True

    def run():
        try:
            build()
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
    """Changes whenever the lists in SOURCES or the channel types change, so an update remakes the list"""
    return hashlib.sha1(json.dumps([SOURCES, CATEGORIES, PER_BLOCK, NEWS_KEEP, NEWS_OPINION, KEEP_OPINION_NEWS]).encode()).hexdigest()[:12]


def start():
    """Keeps the channel list fresh: now if it's old, missing or made from other lists, then every 24 hours."""

    def loop():
        while True:
            try:
                age = time.time() - os.path.getmtime(M3U_FILE)
            except OSError:
                age = EVERY
            if status().get("lists") != _lists_key():
                age = EVERY  # Benflix was updated with different lists: make it again now
            if age >= EVERY - 600:
                refresh()
                time.sleep(EVERY)
            else:
                time.sleep(EVERY - age)

    threading.Thread(target=loop, daemon=True).start()


def file_for(path):
    return {"/livetv/channels.m3u": (M3U_FILE, "audio/x-mpegurl"), "/livetv/guide.xml": (GUIDE_FILE, "application/xml")}.get(path)
