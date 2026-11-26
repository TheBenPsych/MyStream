"""Glowfin Tower health (Ben's picks, 2026-11-22): the Server page's Health section.

Tower itself (benflix-health.sh, run every minute by cron outside Docker) writes everything into
appdata/benflix-health, which the helper sees as /towerhealth:
  status.json          once a minute: the drives' state, error counts since Tower started, board, memory sticks,
                       the Arc's slot speed, UPS model, the freeze timer and Ben's on/off choices
  blackbox-<day>.jsonl a reading every 10 seconds (temperatures, Arc load and power, memory, UPS, what's busy)
  boots.jsonl          one line per start: was the stop before it clean, and did the board save a crash record
The helper writes back:
  settings.want        on/off choices from the Server page (Tower copies them to the USB stick within a minute)
  diary.json           the changes diary ("Arc slot set to Gen 3" and when)

Everything is drawn as Tower cards (benflix_keeper.py), so the browser, TV and phone show it with no new screens:
  health      the answer first, the last crash, the black box, and "Copy report for Claude"
  healthparts memory, processor, Arc, drives, power, motherboard
  health24    the last 24 hours as small graphs (rows carry "spark")
  healthfix   Warn me and fix it: the freeze timer, starting the drives after a crash, messages
  crashes     the crash notebook: what happened, what we tried and what it showed, what's next
  diary       the changes diary, with crashes before and after each change
"""

import json
import os
import threading
import time

DIR = "/towerhealth"
DAY = 86400
SETTINGS = (
    ("watchdog", "Restart by itself if Tower freezes", "The board's own safety timer. If Tower freezes hard, it restarts after 3 minutes instead of staying dead."),
    ("autostart", "Start the drives by itself after a crash", "Only when every drive is there. Otherwise Unraid waits for you, like before."),
    ("crashnote", "Tell me after a crash", "An Unraid notification (and an email, if email is set up in Unraid's Notification settings)."),
    ("cpulimit", "Keep the processor at Intel's power limit", "65 W for long jobs, 154 W for short bursts (Intel's own numbers for this chip). The board allowed about 250 W, which pushed it to 98°C on busy nights. Videos play the same; long scans take a little longer."),
    ("hotnote", "Warn me if anything runs hot", "Processor 85°C, Arc 90°C, NVMe drives 75°C. One message an hour at most."),
)

# Before this page existed (from Ben's notes and the crash hunt, 2026-11-20). "count" = a crash nobody has explained yet.
HISTORY = [
    {"at": "2026-09-25 12:00", "what": "Hard freeze", "s": "On but dead: no web page, no network, no screen. Needed the power button.", "count": True},
    {"at": "2026-10-06 21:00", "what": "Hard freeze", "s": "During a game server and several Jellyfin streams.", "count": True},
    {"at": "2026-10-15 21:05", "what": "Hard freeze", "s": "Lights and fans on, no ping, no screen. The last readings looked normal.", "count": True},
    {"at": "2026-10-20 11:49", "what": "Ran out of memory (explained)", "s": "Only 32 GB in, qBittorrent and posterpilot used most of it. Fixed with memory caps.", "count": False},
    {"at": "2026-10-31 20:22", "what": "Network dropped (explained)", "s": "The network port's old driver. Fixed with the r8125 driver.", "count": False},
    {"at": "2026-11-20 02:00", "what": "Sudden restart", "s": "The board did an emergency reset with no warning. Its crash record doesn't name the part. Tdarr was shrinking a video on the Arc, and the hourly mover was due.", "count": True},
]
TRIED = [
    ("ok", "Memory test, 4 passes with all 4 sticks (Oct 8)", "No errors. A memory test is only sure when it finds errors."),
    ("warn", "New 1000 W power supply", "The crashes kept going, so the power supply isn't the cause."),
    ("warn", "C-states off (about Oct 8)", "A freeze still came a week later."),
    ("ok", "Arc and Corsair sticks out (Oct 15 to Nov 13)", "No freezes or sudden restarts in those 4 weeks."),
    ("warn", "Arc and Corsair sticks back in (Nov 13 to 14)", "A sudden restart 6 days later, during a Tdarr night."),
    ("ok", "Drives, drive card, UPS and parity checks", "All checked Nov 20: healthy. The old 'interrupted' parity checks were the 3 AM pause, not crashes."),
]
NEXT = [
    ("Arc slot to Gen 3 in the BIOS", "Free, and costs the Arc nothing for video. A slot that's unstable at Gen 4 can freeze or reset a PC."),
    ("BIOS update (2803 is old)", "Newer BIOS versions fix stability with Rocket Lake processors and Arc cards."),
    ("Corsair sticks out for a while", "If the crashes stop, the mixed memory was it. Cap qBittorrent again first."),
]
SEED_DIARY = [
    ("2026-09-01 12:00", "Arc A750 put in, Jellyfin uses it"),
    ("2026-09-06 12:00", "Corsair sticks added (64 GB, two kits mixed)"),
    ("2026-10-08 12:00", "C-states turned off in the BIOS"),
    ("2026-10-15 23:43", "Arc and Corsair sticks taken out"),
    ("2026-11-14 12:00", "Arc and Corsair sticks put back in"),
]
NOTES = {
    "gen3": "Arc slot set to Gen 3 in the BIOS",
    "bios": "BIOS updated",
    "corsair-out": "Corsair sticks taken out",
    "corsair-in": "Corsair sticks put back in",
    "arc-out": "Arc taken out",
    "arc-in": "Arc put back in",
}

_lock = threading.Lock()
_tools = None  # benflix_server_tools (drive health from Scrutiny)


def log(text):
    print(f"Health: {text}", flush=True)


def start(tools=None):
    global _tools
    _tools = tools
    log("ready" if os.path.isdir(DIR) else "waiting for Tower to share appdata/benflix-health (the setup command does it)")


# --------------------------------------------------------------------------- reading Tower's notes


def _json(name, default):
    try:
        with open(os.path.join(DIR, name)) as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def _lines(path):
    out = []
    try:
        with open(path) as f:
            for line in f:
                try:
                    out.append(json.loads(line))
                except ValueError:
                    continue
    except OSError:
        pass
    return out


def _status():
    s = _json("status.json", None)
    return s if isinstance(s, dict) else None


def _offset(st=None):
    """Tower's time zone, as seconds from UTC (the helper's own clock is UTC)"""
    tz = str((st or _status() or {}).get("tz") or "")
    try:
        sign = -1 if tz.startswith("-") else 1
        return sign * (int(tz[1:3]) * 3600 + int(tz[3:5]) * 60)
    except (ValueError, IndexError):
        return 0


def _local(t, st=None):
    return time.gmtime(t + _offset(st))


def _when(t, st=None):
    """"Nov 20, 2:00 AM" in Tower's own time"""
    g = _local(t, st)
    h = g.tm_hour % 12 or 12
    return f"{time.strftime('%b', g)} {g.tm_mday}, {h}:{g.tm_min:02d} {'AM' if g.tm_hour < 12 else 'PM'}"


def _clock(t, st=None):
    g = _local(t, st)
    return f"{g.tm_hour % 12 or 12}:{g.tm_min:02d}:{g.tm_sec:02d} {'AM' if g.tm_hour < 12 else 'PM'}"


def _parse(text, st=None):
    """'2026-10-15 21:05' in Tower's time -> seconds"""
    try:
        import calendar

        return int(calendar.timegm(time.strptime(text, "%Y-%m-%d %H:%M"))) - _offset(st)
    except ValueError:
        return 0


def _span(secs):
    secs = max(0, int(secs))
    d, h = secs // DAY, (secs % DAY) // 3600
    if d:
        return f"{d} day{'' if d == 1 else 's'}" + (f", {h} hour{'' if h == 1 else 's'}" if h else "")
    if h:
        return f"{h} hour{'' if h == 1 else 's'}"
    m = max(1, secs // 60)
    return f"{m} minute{'' if m == 1 else 's'}"


# Speed (Ben 2026-11-24, "Server pages take a lot longer to load"): the black box gets a reading every 10 seconds,
# so each day's file grows to about 8,600 lines. Every Server page visit used to read the last 3 days again (and all 7
# days once per crash), several times at once because the Health dot, Overview and each card asked separately, which
# kept the helper busy and made every Server page wait. Now each file is read once and only its new lines after that,
# one reader at a time, and the crash list is worked out again only when boots.jsonl changes.
_read_lock = threading.Lock()
_files = {}  # file name -> {"ino", "pos" (bytes read so far), "rows"}
KEEP_FILES = 3  # the newest day files kept in memory (enough for 26 hours)


def _day_names():
    try:
        return sorted(n for n in os.listdir(DIR) if n.startswith("blackbox-") and n.endswith(".jsonl"))
    except OSError:
        return []


def _read_new(name, keep=True):
    """Every reading in one day file. Only the lines added since the last look are read. Call with _read_lock held."""
    path = os.path.join(DIR, name)
    try:
        info = os.stat(path)
    except OSError:
        _files.pop(name, None)
        return []
    f = _files.get(name)
    if f is None or f["ino"] != info.st_ino or info.st_size < f["pos"]:
        f = {"ino": info.st_ino, "pos": 0, "rows": []}
    if info.st_size > f["pos"]:
        try:
            with open(path, "rb") as fh:
                fh.seek(f["pos"])
                data = fh.read()
        except OSError:
            data = b""
        whole = data.rfind(b"\n") + 1  # a line Tower is still writing waits for the next look
        for line in data[:whole].splitlines():
            try:
                s = json.loads(line)
            except ValueError:
                continue
            if isinstance(s, dict) and isinstance(s.get("t"), (int, float)):
                f["rows"].append(s)
        f["pos"] += whole
    if keep:
        _files[name] = f
    return f["rows"]


def _samples(hours=26):
    """The black box's readings from the last [hours] hours, oldest first"""
    since = time.time() - hours * 3600
    with _read_lock:
        names = _day_names()[-KEEP_FILES:]
        for old in [n for n in _files if n not in names]:
            del _files[old]
        out = []
        for n in names:
            out.extend(s for s in _read_new(n) if s["t"] >= since)
    out.sort(key=lambda s: s["t"])
    return out


def _samples_around(t, before=90, st=None):
    """Readings in the [before] seconds up to t, from only the day files that can have them"""
    off = _offset(st)
    days = {time.strftime("%Y-%m-%d", time.gmtime(x + off)) for x in (t - before - DAY, t - before, t, t + DAY)}
    out = []
    with _read_lock:
        for n in _day_names():
            if n[len("blackbox-"):-len(".jsonl")] in days:
                rows = _files[n]["rows"] if n in _files else _read_new(n, keep=False)
                out.extend(s for s in rows if t - before <= s["t"] < t)
    return sorted(out, key=lambda s: s["t"])


_crash_cache = {"sig": None, "data": []}
_crash_lock = threading.Lock()


def _crashes(st=None):
    """Every crash Tower noticed since this page started, newest first. Each knows its last black box minute."""
    path = os.path.join(DIR, "boots.jsonl")
    with _crash_lock:  # one at a time: the others wait and get the saved answer
        try:
            info = os.stat(path)
            sig = (info.st_ino, info.st_size, info.st_mtime)
        except OSError:
            sig = None
        if sig is not None and sig == _crash_cache["sig"]:
            return _crash_cache["data"]
        out = []
        for b in _lines(path):
            if not isinstance(b, dict) or b.get("clean"):
                continue
            boot = int(b.get("boot") or 0)
            last = _samples_around(boot, before=3 * 3600, st=st)
            died = (last[-1].get("t") if last else None) or b.get("log_end") or b.get("prev_boot") or boot
            out.append({"at": int(died), "boot": boot, "bert": bool(b.get("bert")), "last": [s for s in last if s.get("t", 0) >= died - 60]})
        out.sort(key=lambda c: c["at"], reverse=True)
        _crash_cache.update(sig=sig, data=out)
        return out


def _incidents(st=None):
    """The history above plus the crashes Tower noticed, newest first"""
    items = [dict(h, t=_parse(h["at"], st)) for h in HISTORY]
    items = [i for i in items if i["t"]]
    for c in _crashes(st):
        if any(abs(c["at"] - i["t"]) < 3 * 3600 for i in items):
            continue  # already in the history (Nov 20)
        items.append({"t": c["at"], "what": "Sudden restart" if c["bert"] else "Crash", "s": _crash_words(c, st), "count": True})
    return sorted(items, key=lambda i: i["t"], reverse=True)


def _busy_words(s):
    bits = []
    if s.get("ff"):
        bits.append("video converting (ffmpeg) x" + str(s["ff"]) if s["ff"] > 1 else "video converting (ffmpeg)")
    if s.get("mv"):
        bits.append("the mover")
    if s.get("par"):
        bits.append("a parity check")
    if s.get("apps"):
        bits.append("busiest apps: " + s["apps"])
    return ", ".join(bits)


def _reading(s):
    """One black box reading in plain words"""
    bits = []
    if s.get("ct") is not None:
        bits.append(f"processor {s['ct']}°C" + (f" ({s['cpu']}% busy)" if s.get("cpu") is not None else ""))
    if s.get("at") is not None:
        arc = f"Arc {s['at']}°C"
        extra = [x for x in (f"{s['ap']} W" if s.get("ap") is not None else "", f"{s['ag']}% busy" if s.get("ag") is not None else "") if x]
        bits.append(arc + (f" ({', '.join(extra)})" if extra else ""))
    if s.get("mem") is not None:
        bits.append(f"memory {s['mem']}% used")
    if s.get("nt") is not None:
        bits.append(f"NVMe {s['nt']}°C")
    if s.get("ups"):
        bits.append(f"UPS {s['ups'].replace('_', ' ').lower()}" + (f" {s['lv']:g} V" if isinstance(s.get("lv"), (int, float)) else "") + (f", {s['ul']:g}% load" if isinstance(s.get("ul"), (int, float)) else ""))
    return " · ".join(bits) or "no readings"


def _crash_words(c, st=None):
    words = "The board did an emergency reset and saved a crash record." if c["bert"] else "Tower stopped without shutting down."
    if c["last"]:
        busy = _busy_words(c["last"][-1])
        words += f" Last reading {_clock(c['last'][-1]['t'], st)}: {_reading(c['last'][-1])}."
        if busy:
            words += f" Busy then: {busy}."
    else:
        words += " The black box has no readings from just before it."
    return words


def _days_since_crash(st):
    counted = [i["t"] for i in _incidents(st) if i["count"]]
    last = max(counted) if counted else None
    gaps = sorted(counted)
    longest = max([b - a for a, b in zip(gaps, gaps[1:])] + [0]) if len(gaps) > 1 else 0
    return last, longest


# --------------------------------------------------------------------------- the parts


def _gen(speed):
    for gt, g in (("2.5GT/s", 1), ("5GT/s", 2), ("8GT/s", 3), ("16GT/s", 4), ("32GT/s", 5)):
        if speed.startswith(gt):
            return f"Gen {g}"
    return speed


def _link_words(text):
    parts = (text or "").split()
    if len(parts) < 2:
        return ""
    return f"{_gen(parts[0])}, {parts[1].lower()}"


def _oom(st):
    """Memory kills since Tower started, worst first: [{"app", "prog", "times", "own", "cap", "last"}]"""
    out = []
    for item in str((st or {}).get("oom_apps") or "").split(";"):
        f = item.split("|")
        if len(f) < 6:
            continue
        try:
            out.append({"app": f[0], "prog": f[1], "times": int(f[2]), "own": f[3] == "own", "cap": int(f[4] or 0), "last": int(f[5] or 0)})
        except ValueError:
            continue
    return out


def _size(mb):
    return f"{mb / 1024:g} GB" if mb >= 1024 else f"{mb} MB"


def _oom_words(k, st=None):
    """One memory kill line in plain words"""
    who = "Tower itself" if k["app"] == "Tower" else k["app"]
    prog = f" ({k['prog']})" if k["prog"] and k["prog"] != "?" and k["prog"] != k["app"] else ""
    times = f"{k['times']} time{'' if k['times'] == 1 else 's'}"
    if k["own"]:
        limit = f"its own {_size(k['cap'])} limit" if k["cap"] else "its own memory limit"
        words = f"{who}{prog} hit {limit} {times}"
    elif k["app"] == "Tower":
        words = f"Tower ran out of memory and stopped {k['prog'] if k['prog'] != '?' else 'a program'} {times}"
    else:
        words = f"Tower ran out of memory and stopped {who}{prog} {times}"
    if k["last"]:
        words += f", last at {_when(k['last'], st)}"
    return words


def _hottest(samples):
    """The hottest processor reading, with what was busy then (busy apps are noted once a minute)"""
    best, apps = None, ""
    for s in samples:
        if s.get("apps"):
            apps = s["apps"]
        ct = s.get("ct")
        if isinstance(ct, (int, float)) and (best is None or ct > best["ct"]):
            best = dict(s)
            if not best.get("apps"):
                best["apps"] = apps
    return best


def _parts(st, now_s):
    """[(light, title, words)] for memory, processor, Arc, drives, power and the motherboard"""
    st = st or {}
    e = st.get("errors") or {}
    s = now_s or {}
    rows = []
    # Memory
    sticks = [d.split("|") for d in str(st.get("dimms") or "").split(";") if d]
    sizes = []
    for d in sticks:
        try:
            n, unit = d[0].split()[:2]
            sizes.append(int(n) * (1024 if unit.upper().startswith("T") else 1) // (1024 if unit.upper().startswith("M") else 1))
        except (ValueError, IndexError):
            pass
    makes = {}
    for d in sticks:
        p = (d[2] if len(d) > 2 else "").upper()
        name = "Corsair" if p.startswith("CM") else "Micron" if p[:2].isdigit() and "ATF" in p else (p.split()[0] if p else "unknown")
        makes[name] = makes.get(name, 0) + 1
    speed = sticks[0][1] if sticks and len(sticks[0]) > 1 else ""
    mce = int(e.get("mce") or 0)
    mem_words = [f"{len(sticks)} sticks" if sticks else "", speed, " + ".join(f"{n} {m}" for m, n in makes.items()) if len(makes) > 1 else "", f"{mce} memory or processor errors" if mce else "no memory errors since Tower started"]
    title = f"Memory: {sum(sizes)} GB" if sizes else "Memory"
    if s.get("mem") is not None:
        title += f", {s['mem']}% used"
    kills = _oom(st)
    if kills:
        mem_words.append("; ".join(_oom_words(k, st) for k in kills[:3]))
    recent = any(k["last"] >= time.time() - DAY for k in kills)
    rows.append(("warn" if mce or recent else "ok", title, " · ".join(w for w in mem_words if w)))
    # Processor
    ct = s.get("ct")
    light = "bad" if (ct or 0) >= 85 else "warn" if (ct or 0) >= 80 or mce else "ok"
    cpuw = str(st.get("cpuw") or "").split()
    power = f"power limit {cpuw[0]} W (bursts {cpuw[1]} W)" if len(cpuw) == 2 else ""
    rows.append((light, f"Processor: {ct}°C" if ct is not None else "Processor", " · ".join(w for w in [str(st.get("cpu") or ""), f"{s['cpu']}% busy" if s.get("cpu") is not None else "", power, "safe up to 82°C"] if w)))
    # Arc
    at, gpu_err, aer_arc = s.get("at"), int(e.get("gpu") or 0), int(e.get("aer_arc") or 0)
    light = "bad" if (at or 0) >= 90 else "warn" if (at or 0) >= 85 or gpu_err or aer_arc else "ok"
    arc_words = [
        f"fan {s['af']} rpm" if s.get("af") is not None else "",
        f"{s['ap']} W" if s.get("ap") is not None else "",
        f"{s['ag']}% busy" if s.get("ag") is not None else "",
        "slot " + _link_words(st.get("link")) if st.get("link") else "",
        f"{gpu_err} graphics driver errors" if gpu_err else "no driver errors",
        f"{aer_arc} slot errors" if aer_arc else "no slot errors",
    ]
    rows.append((light, f"Arc A750: {at}°C" if at is not None else "Arc A750", " · ".join(w for w in arc_words if w)))
    # Drives (Scrutiny, plus Tower's own drive error count)
    disk_err = int(e.get("disk") or 0)
    try:
        dr = _tools.drives() if _tools is not None else {"set_up": False, "drives": []}
    except Exception:  # noqa: BLE001 - Scrutiny not answering
        dr = {"set_up": False, "drives": []}
    if dr.get("set_up"):
        drives = dr.get("drives") or []
        bad = [d for d in drives if not d.get("ok")]
        temps = [d.get("temp") for d in drives if isinstance(d.get("temp"), (int, float))]
        words = [f"hottest {max(temps)}°C" if temps else "", f"{disk_err} drive errors in Tower's log" if disk_err else "no drive errors in Tower's log", "details on Tools > Tower > Drive health"]
        title = f"Drives: {len(drives) - len(bad)} of {len(drives)} healthy" if bad else f"Drives: all {len(drives)} healthy"
        rows.append(("bad" if any(not d.get("ok") and "Failing" in d.get("words", "") for d in bad) else "warn" if bad or disk_err else "ok", title, " · ".join(w for w in words if w)))
    else:
        rows.append(("warn" if disk_err else "off", "Drives", (f"{disk_err} drive errors in Tower's log · " if disk_err else "") + "Scrutiny isn't connected, so drive health can't show"))
    # Power
    ups = s.get("ups")
    if ups:
        on_wall = ups.upper().startswith("ONLINE")
        words = [str(st.get("ups_model") or ""), f"wall {s['lv']:g} V" if isinstance(s.get("lv"), (int, float)) else "", f"{s['ul']:g}% load" if isinstance(s.get("ul"), (int, float)) else ""]
        rows.append(("ok" if on_wall else "warn", "Power: UPS on wall power" if on_wall else f"Power: UPS says {ups.replace('_', ' ').lower()}", " · ".join(w for w in words if w)))
    else:
        rows.append(("off", "Power", "No UPS readings (apcupsd isn't answering)"))
    # Motherboard
    other = int(e.get("aer_other") or 0)
    words = [f"BIOS {st['bios']}" if st.get("bios") else "", f"inside {s['bt']}°C" if s.get("bt") is not None else "", f"{other} corrected slot messages on other ports (not the Arc)" if other else "no slot errors", "the board saved a crash record at this start" if st.get("bert") else ""]
    rows.append(("ok", f"Motherboard: {st.get('board') or 'unknown'}", " · ".join(w for w in words if w)))
    return rows


# --------------------------------------------------------------------------- cards


def _not_yet():
    return {
        "title": "Health",
        "level": "off",
        "headline": "Tower health hasn't started yet",
        "reason": "It starts by itself with Tower's next hourly update (or run the setup command once). The first readings arrive a minute later.",
        "rows": [],
    }


def health_card(copy=True):
    st = _status()
    if not st:
        return _not_yet()
    samples = _samples(1)
    now_s = samples[-1] if samples else {}
    parts = _parts(st, now_s)
    up = time.time() - (st.get("boot") or time.time())
    last, longest = _days_since_crash(st)
    crashes = _crashes(st)
    recent = crashes and crashes[0]["boot"] >= (st.get("boot") or 0) - 60
    worst = "bad" if any(p[0] == "bad" for p in parts) else "warn" if any(p[0] == "warn" for p in parts) else "ok"
    if recent and up < DAY:
        level, headline = "warn", "Tower restarted after a crash"
    elif worst != "ok":
        level = worst
        names = [p[1].split(":")[0] for p in parts if p[0] == worst]
        headline = "Something needs a look: " + ", ".join(names).lower()
    else:
        level, headline = "ok", "Tower is running fine"
    days = int((time.time() - last) / DAY) if last else None
    reason = f"On for {_span(up)}, since {_when(st.get('boot') or time.time(), st)}."
    if days is not None:
        reason += f" {days} day{'' if days == 1 else 's'} without a crash" + (f" (the longest lately was {int(longest / DAY)} days)." if longest else ".")
    rows = []
    inc = [i for i in _incidents(st) if i["count"]]
    if inc:
        rows.append({"light": "warn", "t": f"Last crash: {_when(inc[0]['t'], st)}", "s": f"{inc[0]['what']}. {inc[0]['s']}"})
    if crashes and crashes[0]["last"]:
        for s in reversed(crashes[0]["last"][-4:]):
            rows.append({"light": "off", "t": f"Black box, {_clock(s['t'], st)}", "s": _reading(s) + (f" · {_busy_words(s)}" if _busy_words(s) else "")})
    if st.get("drives_started"):
        rows.append({"light": "ok", "t": f"Started the drives by itself at {_when(st['drives_started'], st)}", "s": "Every drive was there after the crash, so Glowfin pressed Start for you."})
    age = time.time() - (now_s.get("t") or 0)
    if now_s and age < 180:
        rows.append({"light": "ok", "t": "Black box is recording", "s": f"A reading every 10 seconds, the last one {int(age)} seconds ago. After a crash, the last minute shows here. 7 days are kept."})
    else:
        rows.append({"light": "warn", "t": "Black box isn't recording", "s": "Tower's minute job hasn't written a reading lately. It comes back with Tower's next hourly update."})
    wd = st.get("wd") or ""
    if (st.get("settings") or {}).get("watchdog") == "on" and wd == "unavailable":
        rows.append({"light": "warn", "t": "The freeze timer can't switch on", "s": "This board's BIOS keeps its safety timer locked, so a hard freeze still needs the power button."})
    return {"title": "Health", "intro": "How Tower's parts are doing, and what happened before a crash.", "level": level, "headline": headline, "reason": reason, "rows": rows, "copy": report() if copy else "", "copy_label": "Copy report for Claude"}


def parts_card():
    st = _status()
    if not st:
        return _not_yet()
    samples = _samples(1)
    parts = _parts(st, samples[-1] if samples else {})
    bad = [p for p in parts if p[0] in ("warn", "bad")]
    return {
        "title": "Parts",
        "level": "bad" if any(p[0] == "bad" for p in parts) else "warn" if bad else "ok",
        "headline": "Every part looks fine" if not bad else f"{len(bad)} part{'' if len(bad) == 1 else 's'} need a look",
        "reason": "Live readings from Tower, and errors counted since it last started.",
        "rows": [{"light": p[0], "t": p[1], "s": p[2]} for p in parts],
    }


SPARKS = (
    ("ct", "Processor temperature", "°C"),
    ("at", "Arc temperature", "°C"),
    ("ap", "Arc power", " W"),
    ("ag", "Arc busy", "%"),
    ("cpu", "Processor busy", "%"),
    ("mem", "Memory used", "%"),
    ("nt", "Hottest NVMe drive", "°C"),
    ("ul", "UPS load", "%"),
)


def day_card():
    st = _status()
    if not st:
        return _not_yet()
    samples = _samples(24)
    if not samples:
        return {"title": "Last 24 hours", "level": "off", "headline": "No readings yet", "reason": "The graphs fill in as the black box records.", "rows": []}
    now = time.time()
    buckets = 96  # one point every 15 minutes
    rows = []
    for key, name, unit in SPARKS:
        points = [None] * buckets
        for s in samples:
            v = s.get(key)
            if not isinstance(v, (int, float)):
                continue
            i = int((s["t"] - (now - DAY)) / (DAY / buckets))
            if 0 <= i < buckets:
                points[i] = v if points[i] is None else max(points[i], v)
        have = [p for p in points if p is not None]
        if not have:
            continue
        current = next((s.get(key) for s in reversed(samples) if isinstance(s.get(key), (int, float))), None)
        rows.append({"light": "off", "t": f"{name}: {current:g}{unit}" if current is not None else name, "s": f"Last 24 hours: {min(have):g} to {max(have):g}{unit}", "spark": points})
    hot = _hottest(samples)
    if hot and hot["ct"] >= 85:
        rows.insert(0, {"light": "warn", "t": f"Hottest moment: {hot['ct']}°C at {_when(hot['t'], st)}", "s": _reading(hot) + (f" · {_busy_words(hot)}" if _busy_words(hot) else "")})
    first = samples[0]["t"]
    span = "Since " + _when(first, st) if now - first < DAY - 900 else "The last 24 hours"
    return {"title": "Last 24 hours", "level": "off", "headline": span, "reason": "Each line shows the highest reading in every 15 minutes, oldest on the left. Tdarr's nights are 1 to 8 AM.", "rows": rows}


def fix_card():
    st = _status() or {}
    chosen = st.get("settings") or {}
    want = _json("settings.want.json", {})
    rows = []
    for key, title, words in SETTINGS:
        on = (want.get(key) or chosen.get(key) or "on") == "on"
        extra = ""
        if key == "watchdog" and on:
            wd = st.get("wd") or ""
            extra = " Right now: running." if wd == "running" else " Right now: this board's BIOS keeps the timer locked, so it can't run." if wd == "unavailable" else " Right now: starting."
        if key in want:
            extra += " (Tower picks this up within a minute.)"
        rows.append({"light": "ok" if on else "off", "t": f"{title}: {'on' if on else 'off'}", "s": words + extra, "acts": [{"label": "Turn off" if on else "Turn on", "do": f"set:{key}:{'off' if on else 'on'}"}]})
    rows.append(
        {
            "light": "ok" if st.get("hc") else "off",
            "t": "Email if Tower goes quiet: " + ("on" if st.get("hc") else "not found"),
            "s": "Your Healthchecks check (set up Nov 20) emails you when Tower stops answering." if st.get("hc") else "The Healthchecks user script isn't on Tower.",
        }
    )
    rows.append({"light": "ok", "t": "\"Tower is restarting\" in the apps: on", "s": "TVs, phones and the browser say Tower is restarting or offline instead of showing an error."})
    rows.append({"light": "ok", "t": "Red dot on the Server page: on", "s": "Health gets a dot when a part needs a look or Tower just crashed."})
    return {"title": "Warn me and fix it", "level": "off", "headline": "Warn me and fix it", "reason": "What Tower does by itself when something goes wrong.", "rows": rows}


def crashes_card():
    st = _status()
    rows = []
    for i in _incidents(st):
        rows.append({"light": "warn" if i["count"] else "off", "t": f"{_when(i['t'], st)}: {i['what']}", "s": i["s"]})
    for light, t, s in TRIED:
        rows.append({"light": light, "t": "Tried: " + t, "s": s})
    for t, s in NEXT:
        rows.append({"light": "off", "t": "Next to try: " + t, "s": s})
    counted = [i for i in _incidents(st) if i["count"]]
    return {
        "title": "Crash notebook",
        "level": "off",
        "headline": f"{len(counted)} unexplained crashes since September",
        "reason": "Everything that happened and everything we tried, in one place. The Changes diary below shows which change helped.",
        "rows": rows,
    }


def _diary():
    d = _json("diary.json", None)
    if isinstance(d, list):
        return d
    st = _status()
    return [{"id": f"seed{n}", "t": _parse(at, st), "what": what} for n, (at, what) in enumerate(SEED_DIARY)]


def _write(name, data):
    try:
        os.makedirs(DIR, exist_ok=True)
        tmp = os.path.join(DIR, name + ".tmp")
        with open(tmp, "w") as f:
            json.dump(data, f)
        os.replace(tmp, os.path.join(DIR, name))
        return True
    except OSError:
        return False


def _count(items, a, b):
    return sum(1 for i in items if a <= i["t"] < b)


def diary_card():
    st = _status()
    entries = sorted(_diary(), key=lambda e: e.get("t") or 0)
    crashes = [i for i in _incidents(st) if i["count"]]
    now = time.time()
    rows = []
    for n, e in enumerate(entries):
        t = e.get("t") or 0
        before_from = entries[n - 1]["t"] if n else t - 30 * DAY
        after_to = entries[n + 1]["t"] if n + 1 < len(entries) else now
        b, a = _count(crashes, before_from, t), _count(crashes, t, after_to)

        def words(k, secs):
            return f"no crashes in {_span(secs)}" if k == 0 else f"{k} crash{'' if k == 1 else 'es'} in {_span(secs)}"

        rows.append(
            {
                "light": "ok" if a < b else "warn" if a > b else "off",
                "t": f"{_when(t, st).split(',')[0]}: {e.get('what')}",
                "s": f"Before: {words(b, t - before_from)} · After: {words(a, after_to - t)}" + (" (so far)" if n + 1 == len(entries) else ""),
                "acts": [{"label": "Remove", "do": "del:" + str(e.get("id"))}],
            }
        )
    rows.reverse()
    return {
        "title": "Changes diary",
        "level": "off",
        "headline": "Changes diary",
        "reason": "Press what you changed and when it happened is noted. Each change shows the crashes before and after it.",
        "acts": [{"label": label, "do": "note:" + key} for key, label in NOTES.items()],
        "rows": rows,
    }


def report():
    """Everything on the page as plain text, for pasting to Claude"""
    st = _status() or {}
    lines = ["Glowfin Tower health report", f"Made {_when(time.time(), st)} (Tower's time)"]
    if st:
        up = time.time() - (st.get("boot") or time.time())
        lines.append(f"On for {_span(up)}, since {_when(st.get('boot') or 0, st)}. Drives: {st.get('array') or 'unknown'}.")
        lines.append(f"Board {st.get('board')} BIOS {st.get('bios')}, CPU {st.get('cpu')}, Arc slot {_link_words(st.get('link'))} (can do {_link_words(st.get('link_cap'))}).")
        lines.append(f"Memory sticks: {st.get('dimms')}")
        lines.append("Errors since start: " + ", ".join(f"{k} {v}" for k, v in (st.get("errors") or {}).items()) + f", board crash record at this start: {'yes' if st.get('bert') else 'no'}.")
        lines.append(f"Freeze timer: {st.get('wd')}. Settings: {json.dumps(st.get('settings') or {})}.")
        kills = _oom(st)
        if kills:
            lines.append("Memory kills since start: " + "; ".join(_oom_words(k, st) for k in kills) + ".")
    samples = _samples(1)
    lines.append("")
    lines.append("Parts:")
    for light, t, s in _parts(st, samples[-1] if samples else {}):
        lines.append(f"  [{light}] {t}: {s}")
    lines.append("")
    lines.append("Crashes:")
    for i in _incidents(st)[:8]:
        lines.append(f"  {_when(i['t'], st)}: {i['what']}. {i['s']}")
    for c in _crashes(st)[:3]:
        lines.append(f"  Black box before the crash at {_when(c['at'], st)}:")
        for s in c["last"]:
            lines.append(f"    {_clock(s['t'], st)} {json.dumps(s)}")
    lines.append("")
    lines.append("Changes diary:")
    for e in sorted(_diary(), key=lambda e: e.get("t") or 0):
        lines.append(f"  {_when(e.get('t') or 0, st)}: {e.get('what')}")
    day = _samples(24)
    if day:
        lines.append("")
        lines.append("Last 24 hours (highest):")
        for key, name, unit in SPARKS:
            vals = [s[key] for s in day if isinstance(s.get(key), (int, float))]
            if vals:
                lines.append(f"  {name}: {min(vals):g} to {max(vals):g}{unit}")
        hot = _hottest(day)
        if hot:
            lines.append(f"  Hottest moment: {_when(hot['t'], st)}: {_reading(hot)}" + (f" · {_busy_words(hot)}" if _busy_words(hot) else ""))
    return "\n".join(lines)


def summary():
    """For the Server page's rail dot and Overview: {"level", "headline"}"""
    try:
        c = health_card(copy=False)
        return {"level": c.get("level"), "headline": c.get("headline")}
    except Exception:  # noqa: BLE001
        return None


def card_do(card, do):
    if card == "healthfix" and do.startswith("set:"):
        _, key, value = (do.split(":") + ["", ""])[:3]
        if key not in {k for k, _, _ in SETTINGS} or value not in ("on", "off"):
            return "Glowfin didn't understand that button."
        st = _status() or {}
        chosen = dict(st.get("settings") or {})
        want = _json("settings.want.json", {})
        chosen.update(want)
        chosen[key] = value
        # Tower reads settings.want each minute and copies it to the USB stick
        text = "".join(f"{k}={chosen.get(k) or 'on'}\n" for k, _, _ in SETTINGS)
        try:
            with open(os.path.join(DIR, "settings.want"), "w") as f:
                f.write(text)
        except OSError:
            return "Couldn't save that on Tower. Is the setup command up to date?"
        want[key] = value
        _write("settings.want.json", want)
        return "Saved. Tower picks it up within a minute."
    if card == "diary":
        entries = _diary()
        if do.startswith("note:") and do[5:] in NOTES:
            entries.append({"id": f"n{int(time.time())}", "t": int(time.time()), "what": NOTES[do[5:]]})
            return "Noted in the diary." if _write("diary.json", entries) else "Couldn't save the diary on Tower."
        if do.startswith("del:"):
            keep = [e for e in entries if str(e.get("id")) != do[4:]]
            if len(keep) == len(entries):
                return "That note was already gone."
            return "Removed." if _write("diary.json", keep) else "Couldn't save the diary on Tower."
    return "Glowfin didn't understand that button."


def _clear_want():
    """Once Tower has copied the choices (status.json shows them), the 'waiting' note goes away"""
    st = _status() or {}
    want = _json("settings.want.json", {})
    if want and all((st.get("settings") or {}).get(k) == v for k, v in want.items()):
        _write("settings.want.json", {})


def fix_card_fresh():
    _clear_want()
    return fix_card()


CARDS = {
    "health": health_card,
    "healthparts": parts_card,
    "health24": day_card,
    "healthfix": fix_card_fresh,
    "crashes": crashes_card,
    "diary": diary_card,
}


def register(keeper):
    """Adds the six cards to the Server page (keeper.add_card)"""
    for name, fn in CARDS.items():
        keeper.add_card(name, fn, lambda do, name=name: card_do(name, do))
