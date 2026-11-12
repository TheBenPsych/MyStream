"""
Benflix "Tower care": the Server page's weekly report card, the tidy-up helper, the night-time
play test and fixing subtitles that are early or late.

Loaded by benflix-games-helper.py (it sits next to it on Tower). Everything here is read-only except:
  * Tidy-up removes a movie or show only when the admin ticks it on the Server page and presses Remove
    (it goes through Radarr or Sonarr, so they forget it too and don't download it again).
  * Fixing subtitles rewrites a .srt subtitle file next to a video (the old one is kept as
    <name>.srt.benflix-old so it can be put back), or adds a new .srt beside the video when the
    subtitles were inside the video file.

Saves its notes in /skips/care (the same folder the skip times use).
"""

import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
import time
import urllib.parse

_jf = None  # The helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_admin_from_helper = None  # () -> (token, user id) of the admin, known once they've signed in
_tools = None  # benflix_server_tools (Radarr, Sonarr, disks, apps), or None
_dir = None
_lock = threading.RLock()
_log_lines = []
_sync_queue = []
_sync_jobs = {}  # item id -> {"state", "message", ...}
_sync_wake = threading.Event()
_cache = {}

DAY = 86400
YEAR = 365 * DAY
PLAYTEST_PER_NIGHT = 80
FRAME = 0.1  # seconds per step when lining subtitles up with the voices


class CareError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def log(text):
    line = time.strftime("%H:%M:%S ") + text
    print("care: " + text, flush=True)
    with _lock:
        _log_lines.append(line)
        del _log_lines[:-60]


# ---------- Start up and saving ----------


def _pick_dir():
    for d in (os.environ.get("CARE_DIR"), "/skips/care", "/tracks/benflix-care"):
        if not d:
            continue
        try:
            os.makedirs(d, exist_ok=True)
            test = os.path.join(d, ".write-test")
            with open(test, "w") as f:
                f.write("ok")
            os.remove(test)
            return d
        except OSError:
            continue
    return None


def ffmpeg_ok():
    return bool(shutil.which("ffmpeg") and shutil.which("ffprobe"))


def start(jellyfin_call, admin, tools=None):
    """Called once by the helper when it starts. admin() gives (token, user id) of the admin."""
    global _jf, _admin_from_helper, _tools, _dir
    _jf, _admin_from_helper, _tools = jellyfin_call, admin, tools
    _dir = _pick_dir()
    if not _dir:
        log("no folder to save notes in, so the play test and subtitle fixer are off")
        return
    threading.Thread(target=_nightly, name="care-nightly", daemon=True).start()
    threading.Thread(target=_sync_worker, name="care-subsync", daemon=True).start()
    log(f"ready (saving in {_dir}; ffmpeg {'found' if ffmpeg_ok() else 'MISSING, run the setup command again'})")


def _read(name, default=None):
    try:
        with open(os.path.join(_dir, name)) as f:
            return json.load(f)
    except (OSError, ValueError, TypeError):
        return default


def _write(name, data):
    if not _dir:
        return
    path = os.path.join(_dir, name)
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(data, f)
    os.replace(tmp, path)


def _admin():
    token, user = (_admin_from_helper() if _admin_from_helper else (None, None)) or (None, None)
    if not token:
        saved = _read("admin.json", {}) or {}
        token, user = saved.get("token"), saved.get("user")
    return token, user


def remember_admin(token, user_id):
    if not _dir or not token:
        return
    saved = _read("admin.json", {}) or {}
    if saved.get("token") != token:
        _write("admin.json", {"token": token, "user": user_id})


def _get(path, token=None):
    token = token or _admin()[0]
    if not token:
        raise CareError(503, "Tower needs the main profile to sign in on Benflix once first.")
    status, data, _ = _jf("GET", path, token=token)
    if status != 200:
        raise CareError(502, f"Jellyfin answered with an error ({status}).")
    try:
        return json.loads(data or b"null")
    except ValueError:
        raise CareError(502, "Jellyfin sent something the helper didn't understand.")


def _post(path, token=None):
    token = token or _admin()[0]
    if token:
        try:
            _jf("POST", path, token=token, body=b"")
        except Exception:
            pass


def _when(iso):
    """Jellyfin / Radarr time text -> seconds since 1970 (0 when there's none)"""
    if not iso:
        return 0
    try:
        text = re.sub(r"\.\d+", "", str(iso)).replace("Z", "+00:00")
        if not re.search(r"[+-]\d\d:\d\d$", text):
            text += "+00:00"
        from datetime import datetime

        return datetime.fromisoformat(text).timestamp()
    except ValueError:
        return 0


def _iso(seconds):
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(seconds))


def _newest(limit=400, kinds="Movie,Episode"):
    _, user = _admin()
    q = urllib.parse.urlencode(
        {
            "IncludeItemTypes": kinds,
            "Recursive": "true",
            "SortBy": "DateCreated",
            "SortOrder": "Descending",
            "Limit": str(limit),
            "Fields": "Path,DateCreated,MediaStreams,ProviderIds",
            "EnableImages": "false",
            "EnableUserData": "false",
            **({"userId": user} if user else {}),
        }
    )
    return (_get("/Items?" + q) or {}).get("Items") or []


def _nice_name(it):
    if it.get("Type") == "Episode":
        s, e = it.get("ParentIndexNumber"), it.get("IndexNumber")
        code = f" S{s}E{e}" if s is not None and e is not None else ""
        return f"{it.get('SeriesName') or 'A show'}{code}"
    year = it.get("ProductionYear")
    return f"{it.get('Name') or 'A movie'}{f' ({year})' if year else ''}"


# ---------- Night-time play test ----------

LANGS = {
    "eng": "English", "en": "English", "jpn": "Japanese", "ja": "Japanese", "spa": "Spanish", "es": "Spanish",
    "fre": "French", "fra": "French", "fr": "French", "ger": "German", "deu": "German", "de": "German",
    "ita": "Italian", "it": "Italian", "kor": "Korean", "ko": "Korean", "chi": "Chinese", "zho": "Chinese",
    "rus": "Russian", "ru": "Russian", "hin": "Hindi", "por": "Portuguese", "pt": "Portuguese",
}
UNKNOWN_LANG = {"", "und", "unk", "unknown", "mis", "zxx"}


def _run(cmd, timeout):
    try:
        p = subprocess.run(cmd, capture_output=True, timeout=timeout)
        return p.returncode, p.stdout.decode("utf-8", "replace"), p.stderr.decode("utf-8", "replace")
    except subprocess.TimeoutExpired:
        return -1, "", "took too long"
    except OSError as e:
        return -1, "", str(e)


def check_file(path, anime=False):
    """Opens one video for a few seconds. Returns (problems, notes): problems are things that are broken,
    notes are things worth a look (like the sound only being in another language)."""
    if not os.path.isfile(path):
        return None, ["Tower couldn't find the file to check it."]
    code, out, err = _run(["ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", path], 90)
    if code != 0:
        first = (err.strip().splitlines() or ["it won't open"])[0][:120]
        return [f"Won't open ({first})"], []
    try:
        info = json.loads(out)
    except ValueError:
        return ["Won't open (the file looks damaged)"], []
    streams = info.get("streams") or []
    video = [s for s in streams if s.get("codec_type") == "video" and not (s.get("disposition") or {}).get("attached_pic")]
    audio = [s for s in streams if s.get("codec_type") == "audio"]
    problems, notes = [], []
    if not video:
        problems.append("No picture")
    if not audio:
        problems.append("No sound")
    try:
        length = float((info.get("format") or {}).get("duration") or 0)
    except ValueError:
        length = 0
    if 0 < length < 60:
        problems.append(f"Only {int(length)} seconds long")
    langs = [str((s.get("tags") or {}).get("language") or "").lower() for s in audio]
    if audio and not any(l in ("eng", "en") or l in UNKNOWN_LANG for l in langs):
        names = sorted({LANGS.get(l, l.upper()) for l in langs})
        if not (anime and any(l in ("jpn", "ja") for l in langs)):
            notes.append(f"Sound is only in {', '.join(names)} (fine if it's a foreign film)")
    if video and length > 60:
        for at in (0.12, 0.62):
            c, _, e = _run(["ffmpeg", "-v", "error", "-xerror", "-ss", str(int(length * at)), "-i", path, "-t", "3", "-map", "0:v:0", "-f", "null", "-"], 90)
            if c != 0:
                problems.append(f"Picture breaks up about {int(length * at / 60)} minutes in")
                break
    if audio and length > 60:
        c, _, e = _run(["ffmpeg", "-v", "info", "-ss", str(int(length * 0.3)), "-i", path, "-t", "20", "-map", "0:a:0", "-af", "volumedetect", "-f", "null", "-"], 90)
        m = re.search(r"max_volume:\s*(-?[\d.]+|-inf) dB", e)
        if c != 0:
            problems.append("Sound breaks up")
        elif m and (m.group(1) == "-inf" or float(m.group(1)) < -70):
            problems.append("Sound track is silent")
    return problems, notes


def playtest(limit=PLAYTEST_PER_NIGHT, reason="night run"):
    """Checks the newest files that haven't been checked yet"""
    if not (_dir and ffmpeg_ok() and _admin()[0]):
        return 0
    seen = _read("playtest.json", {}) or {}
    try:
        items = _newest(500)
    except CareError as e:
        log("play test: " + e.message)
        return 0
    todo = [it for it in items if it.get("Path") and it["Id"] not in seen][:limit]
    if todo:
        log(f"play test ({reason}): checking {len(todo)} new files")
    for it in todo:
        path = it["Path"]
        problems, notes = check_file(path, anime="anime" in path.lower())
        seen[it["Id"]] = {
            "at": time.time(),
            "name": _nice_name(it),
            "type": it.get("Type"),
            "series": it.get("SeriesId"),
            "path": path,
            "problems": problems or [],
            "notes": notes,
            "missing": problems is None,
        }
        if problems:
            log(f"play test: {_nice_name(it)}: {', '.join(problems)}")
        with _lock:
            cutoff = time.time() - 120 * DAY
            seen = {k: v for k, v in seen.items() if v.get("at", 0) > cutoff}
            _write("playtest.json", seen)
        _auto_sync(it)
    return len(todo)


def playtest_status():
    seen = _read("playtest.json", {}) or {}
    rows = sorted(seen.items(), key=lambda kv: -kv[1].get("at", 0))
    broken = [dict(v, id=k) for k, v in rows if v.get("problems") and not v.get("ok")]
    look = [dict(v, id=k) for k, v in rows if v.get("notes") and not v.get("problems") and not v.get("ok") and not v.get("missing")]
    week = [v for _, v in rows if v.get("at", 0) > time.time() - 7 * DAY]
    return {
        "on": bool(_dir) and ffmpeg_ok(),
        "checked_this_week": len(week),
        "broken": broken[:40],
        "look": look[:40],
        "last": rows[0][1].get("at") if rows else None,
    }


def playtest_ok(item_id):
    """The admin says a flagged file is fine (or fixed): stop showing it"""
    with _lock:
        seen = _read("playtest.json", {}) or {}
        if item_id in seen:
            seen[item_id]["ok"] = True
            _write("playtest.json", seen)
    return playtest_status()


def playtest_again(item_id):
    with _lock:
        seen = _read("playtest.json", {}) or {}
        seen.pop(item_id, None)
        _write("playtest.json", seen)
    threading.Thread(target=playtest, args=(5, "check again"), daemon=True).start()
    return {"message": "Checking it again. Give it a minute."}


def _nightly():
    time.sleep(600)
    playtest(25, "start-up")
    done_day = None
    while True:
        time.sleep(600)
        now = time.localtime()
        if not (1 <= now.tm_hour < 3) or done_day == now.tm_yday:
            continue
        done_day = now.tm_yday
        playtest()
        if now.tm_wday == 6:  # Sunday: have the report card ready for the morning
            try:
                report(fresh=True)
            except Exception as e:
                log(f"report card: {e}")


# ---------- Subtitles that are early or late ----------


def _read_levels(out):
    levels = []
    with open(out) as f:
        for line in f:
            if "RMS_level=" in line:
                v = line.split("=", 1)[1].strip()
                levels.append(-120.0 if v in ("-inf", "nan") else float(v))
    return levels


def _speech(path, audio_index=None):
    """Where people are talking, as a bit per 0.1 second (1 = voices). Uses ffmpeg to measure the
    sound twice: the part where voices are (250-3400 Hz) and the hiss above it (4000 Hz and up).
    A tenth of a second counts as talking when the voice part is fairly loud, clearly louder than the
    hiss (laugh tracks, clapping and crowd noise are full of hiss), and going up and down the way
    words do (music and steady background noise stay level)."""
    outs = []
    for _ in range(2):
        fd, out = tempfile.mkstemp(suffix=".txt", dir=_dir)
        os.close(fd)
        outs.append(out)
    voice_file, hiss_file = outs
    try:
        amap = f"[0:{audio_index}]" if audio_index is not None else "[0:a:0]"
        meta = "astats=metadata=1:reset=1,ametadata=print:key=lavfi.astats.Overall.RMS_level:file="
        graph = (
            f"{amap}aformat=channel_layouts=mono,aresample=16000,asetnsamples=n=1600:p=0,asplit=2[a][b];"
            f"[a]highpass=f=250,lowpass=f=3400,{meta}{voice_file}[v];"
            f"[b]highpass=f=4000,{meta}{hiss_file}[h]"
        )
        code, _, err = _run(
            ["ffmpeg", "-v", "error", "-i", path, "-filter_complex", graph, "-map", "[v]", "-f", "null", "-", "-map", "[h]", "-f", "null", "-"],
            1800,
        )
        if code != 0:
            raise CareError(500, "Tower couldn't read the sound in that video.")
        voice, hiss = _read_levels(voice_file), _read_levels(hiss_file)
    finally:
        for out in outs:
            try:
                os.remove(out)
            except OSError:
                pass
    n = min(len(voice), len(hiss))
    if n < 300:
        raise CareError(500, "The sound in that video is too short to line subtitles up with.")
    voice, hiss = voice[:n], hiss[:n]

    def middle(xs, at=0.5):
        ranked = sorted(xs)
        return ranked[int(len(ranked) * at)]

    above = [v - h for v, h in zip(voice, hiss)]
    swing = []
    for i in range(n):
        near = voice[max(0, i - 3) : i + 4]
        mean = sum(near) / len(near)
        swing.append((sum((x - mean) ** 2 for x in near) / len(near)) ** 0.5)
    loud, clear, moving = middle(voice, 0.3), middle(above) - 1, middle(swing)
    bits = "".join("1" if v > loud and d > clear and m > moving else "0" for v, d, m in zip(voice, above, swing))
    return int(bits[::-1], 2), n


SRT_TIME = re.compile(r"(\d+):(\d\d):(\d\d)[,.](\d{1,3})\s*-->\s*(\d+):(\d\d):(\d\d)[,.](\d{1,3})")


def _parse_srt(text):
    cues = []
    for block in re.split(r"\r?\n\s*\r?\n", text.replace("\ufeff", "")):
        lines = block.strip().splitlines()
        for i, line in enumerate(lines):
            m = SRT_TIME.search(line)
            if m:
                g = [int(x) for x in m.groups()]
                start = g[0] * 3600 + g[1] * 60 + g[2] + g[3] / (10 ** len(m.group(4)))
                end = g[4] * 3600 + g[5] * 60 + g[6] + g[7] / (10 ** len(m.group(8)))
                words = "\n".join(lines[i + 1 :]).strip()
                if words and end > start:
                    cues.append([start, end, words])
                break
    return cues


def _stamp(t):
    t = max(0.0, t)
    ms = int(round(t * 1000))
    return f"{ms // 3600000:02d}:{ms // 60000 % 60:02d}:{ms // 1000 % 60:02d},{ms % 1000:03d}"


def _srt(cues):
    return "\n".join(f"{i}\n{_stamp(s)} --> {_stamp(e)}\n{w}\n" for i, (s, e, w) in enumerate(cues, 1))


def _sub_bits(cues, frames, scale):
    bits = bytearray(b"0" * frames)
    for s, e, _ in cues:
        a, b = int(s * scale / FRAME), int(e * scale / FRAME)
        for i in range(max(0, a), min(frames, b + 1)):
            bits[i] = 49
    return int(bits.decode()[::-1], 2)


RATES = (1.0, 25 / 23.976, 23.976 / 25, 24 / 23.976, 23.976 / 24, 25 / 24, 24 / 25)


def line_up(cues, speech, frames, reach=90.0):
    """Finds the shift (and speed, for subtitles made for a different frame rate) that puts the
    subtitles on top of the voices. Returns (scale, shift seconds, how sure 0-1, gain)."""
    steps = int(reach / FRAME)
    mask = (1 << frames) - 1
    best = plain = None
    base = None
    for scale in RATES:
        subs = _sub_bits(cues, frames, scale)
        scores = []
        for k in range(-steps, steps + 1):
            moved = (subs << k) & mask if k >= 0 else subs >> -k
            scores.append((speech & moved).bit_count())
        if scale == 1.0:
            base = scores[steps]
        top = max(scores)
        k = scores.index(top) - steps
        mean = sum(scores) / len(scores)
        spread = (sum((x - mean) ** 2 for x in scores) / len(scores)) ** 0.5 or 1
        found = (top, scale, k * FRAME, (top - mean) / spread)
        if scale == 1.0:
            plain = found
        # A different speed has to be clearly better, or the normal speed wins
        if best is None or top > best[0] * (1.0 if best[1] != 1.0 else 1.03):
            best = found
    if best[1] != 1.0 and best[0] < plain[0] * 1.03:
        best = plain
    top, scale, shift, z = best
    gain = top / base if base else 9.9
    sure = max(0.0, min(1.0, (z - 3) / 5))
    return scale, round(shift, 2), round(sure, 2), round(gain, 3)


def _score_at(bits, speech, k, mask):
    moved = (bits << k) & mask if k >= 0 else bits >> -k
    return (speech & moved).bit_count()


def line_up_parts(cues, speech, frames, scale, shift, reach=60.0, part=120.0):
    """After the whole-video fix, checks the subtitles a couple of minutes at a time. Subtitles
    made for a different cut of a show (other ad breaks, a longer recap) are right in some parts
    and seconds off in others, and one shift for the whole video can't see that. Returns the
    shift for every cue, plus how many parts were checked and how many were sure."""
    mask = (1 << frames) - 1
    steps = int(reach / FRAME)
    placed = [(s * scale + shift, e * scale + shift) for s, e, _ in cues]
    # Cut the cues into parts of about two minutes each
    groups, start = [], 0
    for i in range(1, len(placed) + 1):
        if i == len(placed) or placed[i][0] - placed[start][0] >= part:
            groups.append((start, i))
            start = i
    if len(groups) > 1 and groups[-1][1] - groups[-1][0] < 8:  # a tiny last part joins the one before
        groups[-2:] = [(groups[-2][0], groups[-1][1])]
    moves, sure_parts = [], 0
    for a, b in groups:
        bits = bytearray(b"0" * frames)
        for s, e in placed[a:b]:
            for i in range(max(0, int(s / FRAME)), min(frames, int(e / FRAME) + 1)):
                bits[i] = 49
        bits = int(bits.decode()[::-1], 2)
        scores = [_score_at(bits, speech, k, mask) for k in range(-steps, steps + 1)]
        top = max(scores)
        k = scores.index(top) - steps
        mean = sum(scores) / len(scores)
        spread = (sum((x - mean) ** 2 for x in scores) / len(scores)) ** 0.5 or 1
        here = scores[steps] or 1
        if b - a >= 6 and (top - mean) / spread >= 3.0:
            moves.append(k * FRAME if top >= here * 1.05 else 0.0)  # only move a part that clearly gets better
            sure_parts += 1
        else:
            moves.append(None)  # not sure: goes with the parts around it
    # Unsure parts take the nearest sure part's move (or none)
    filled = []
    for i, m in enumerate(moves):
        if m is None:
            near = [(abs(j - i), moves[j]) for j in range(len(moves)) if moves[j] is not None]
            m = min(near)[1] if near else 0.0
        filled.append(m)
    # Where two parts meet, the change can fall in the middle of a part: each line picks whichever
    # of the moves nearby puts it and its neighbours on top of the voices best
    per_cue = [0.0] * len(cues)
    for g, (a, b) in enumerate(groups):
        options = sorted({filled[x] for x in (g - 1, g, g + 1) if 0 <= x < len(filled)}, key=lambda m: m != filled[g])
        for i in range(a, b):
            best_m, best_score = filled[g], -1
            if len(options) > 1:
                near = placed[max(0, i - 3) : i + 4]
                for m in options:
                    score = _cover([(s + m, e + m) for s, e in near], frames, speech)
                    if score > best_score:
                        best_m, best_score = m, score
            per_cue[i] = round(shift + best_m, 2)
    return per_cue, len(groups), sure_parts


def _cover(cues, frames, speech):
    bits = bytearray(b"0" * frames)
    for s, e in cues:
        for i in range(max(0, int(s / FRAME)), min(frames, int(e / FRAME) + 1)):
            bits[i] = 49
    return (speech & int(bits.decode()[::-1], 2)).bit_count()


def _stream_info(item_id, index=None):
    token, user = _admin()
    if not token:
        raise CareError(503, "Tower needs the main profile to sign in on Benflix once first.")
    it = _get(f"/Items/{item_id}?" + urllib.parse.urlencode({"userId": user or "", "Fields": "Path,MediaStreams,MediaSources"}))
    if it and "Items" in it:
        it = (it.get("Items") or [None])[0]
    if not it or not it.get("Path"):
        raise CareError(404, "Tower couldn't find that video.")
    streams = it.get("MediaStreams") or ((it.get("MediaSources") or [{}])[0].get("MediaStreams")) or []
    subs = [s for s in streams if s.get("Type") == "Subtitle" and s.get("IsTextSubtitleStream", True)]
    pick = None
    if index is not None:
        pick = next((s for s in subs if s.get("Index") == index), None)
        chosen = next((s for s in streams if s.get("Type") == "Subtitle" and s.get("Index") == index), None)
        if pick is None and chosen is not None:
            # Never check a different track than the one on screen: that made Tower say "fine" before
            others = [s for s in subs if str(s.get("Language") or "").lower() == str(chosen.get("Language") or "").lower()]
            tip = " Pick the other " + (chosen.get("DisplayTitle") or "subtitles").split(" - ")[0] + " subtitles in the list, they're words and Tower can fix those." if others else ""
            raise CareError(400, "These subtitles are pictures (copied from a disc), so Tower can't move them." + tip)
    if pick is None:
        eng = [s for s in subs if str(s.get("Language") or "").lower() in ("eng", "en")]
        pick = (eng or subs or [None])[0]
    if pick is None:
        raise CareError(400, "That video has no word subtitles Tower can fix (picture subtitles can't be moved).")
    return it, pick


def _sub_text(video, sub):
    """The subtitle's text as .srt, wherever it lives"""
    if sub.get("IsExternal") and sub.get("Path", "").lower().endswith(".srt") and os.path.isfile(sub["Path"]):
        with open(sub["Path"], encoding="utf-8", errors="replace") as f:
            return f.read()
    src = sub["Path"] if sub.get("IsExternal") else video
    amap = "0:s:0" if sub.get("IsExternal") else f"0:{sub.get('Index')}"
    code, out, err = _run(["ffmpeg", "-v", "error", "-i", src, "-map", amap, "-f", "srt", "-"], 300)
    if code != 0 or not out.strip():
        raise CareError(500, "Tower couldn't read those subtitles.")
    return out


def _new_name(video, sub):
    base = os.path.splitext(video)[0]
    lang = str(sub.get("Language") or "eng").lower()
    name = f"{base}.{lang}.srt"
    if os.path.exists(name):
        name = f"{base}.{lang}.fixed.srt"
    return name


def _no_words(text):
    """True for subtitle lines that are only sounds or music, like [LAUGHTER], (applause) or a song note"""
    left = re.sub(r"\[[^\]]*\]|\([^)]*\)|<[^>]*>|\{[^}]*\}", " ", text)
    left = re.sub(r"[\u266a\u266b#*\-\s.,!?:]", "", left)
    return not left


def fix_subtitles(item_id, index=None, auto=False):
    """Lines one subtitle track up with the voices. Returns a plain answer for the person."""
    video, sub = _stream_info(item_id, index)
    path = video["Path"]
    if not os.path.isfile(path):
        raise CareError(404, "Tower couldn't find the video file.")
    cues = _parse_srt(_sub_text(path, sub))
    # Lines like [LAUGHTER] or a music note aren't voices, so only the spoken lines are matched
    talk = [i for i, (_, _, w) in enumerate(cues) if not _no_words(w)]
    if len(talk) < 20:
        raise CareError(400, "Those subtitles are too short to line up.")
    spoken = [cues[i] for i in talk]
    speech, frames = _speech(path)
    scale, shift, sure, gain = line_up(spoken, speech, frames)
    talk_shift, parts, sure_parts = line_up_parts(spoken, speech, frames, scale, shift)
    per_cue, j = [], 0
    for i in range(len(cues)):
        while j + 1 < len(talk) and talk[j + 1] <= i:
            j += 1
        per_cue.append(talk_shift[j])
    fixed = [[s * scale + d, e * scale + d, w] for (s, e, w), d in zip(cues, per_cue)]
    before = _cover([(s, e) for s, e, _ in cues], frames, speech) or 1
    after = _cover([(s, e) for s, e, _ in fixed], frames, speech)
    total_gain = round(after / before, 3)
    log(
        f"subtitles {_nice_name(video)}: speed x{scale:.4f}, shift {shift:+.1f}s, sure {sure}, gain {gain}; "
        f"by parts {sorted(set(per_cue))} ({sure_parts} of {parts} parts sure), gain {total_gain}"
    )
    biggest = max(per_cue, key=abs) if per_cue else 0.0
    # When a person asks, they've already seen the words are off, so a weaker match is enough
    # (laugh tracks keep "sure" low even when the answer is right, like a PAL-speed file: x0.959)
    trusted = sure >= (0.55 if auto else 0.1) or sure_parts * 2 >= parts
    if not trusted or total_gain < (1.12 if auto else 1.03) or (abs(biggest) < 0.3 and scale == 1.0):
        why = "They look lined up with the voices to Tower" if trusted else "Tower couldn't hear the voices clearly enough to be sure"
        return {
            "changed": False,
            "message": f"{why}, so it left them alone. If they still look off, move them yourself with Subtitle timing.",
        }
    fixed = [c for c in fixed if c[1] > 0]
    shift = biggest
    external = bool(sub.get("IsExternal")) and sub.get("Path", "").lower().endswith(".srt")
    target = sub["Path"] if external else _new_name(path, sub)
    if external:
        keep = target + ".benflix-old"
        if not os.path.exists(keep):
            shutil.copy2(target, keep)
    with open(target, "w", encoding="utf-8") as f:
        f.write(_srt(fixed))
    _post(f"/Items/{item_id}/Refresh?metadataRefreshMode=Default&imageRefreshMode=None")
    with _lock:
        done = _read("subsync.json", []) or []
        done.append({"at": time.time(), "id": item_id, "name": _nice_name(video), "shift": shift, "scale": scale, "auto": auto, "file": target})
        _write("subsync.json", done[-300:])
    was = "early" if shift > 0 else "late"  # moved later = they came too early
    speed = "" if scale == 1.0 else " and the speed was a little off"
    if len(set(per_cue)) > 1:
        speed += " in some parts of the video (Tower fixed each part on its own)"
    where = "" if external else " They're a new subtitle choice called the same language (pick it in Subtitles)."
    return {"changed": True, "shift": shift, "message": f"Fixed. They were about {abs(shift):.1f} seconds {was}{speed}.{where}"}


def _auto_sync(it):
    """During the night test: fix .srt files next to new videos that are clearly early or late"""
    try:
        streams = it.get("MediaStreams") or []
        ext = [s for s in streams if s.get("Type") == "Subtitle" and s.get("IsExternal") and str(s.get("Path", "")).lower().endswith(".srt")]
        for s in ext[:2]:
            fix_subtitles(it["Id"], s.get("Index"), auto=True)
    except CareError:
        pass
    except Exception as e:
        log(f"subtitles: {e}")


def sync_request(item_id, index):
    if not (_dir and ffmpeg_ok()):
        raise CareError(503, "The subtitle fixer isn't on Tower yet. Run the Benflix setup command again.")
    with _lock:
        job = _sync_jobs.get(item_id)
        if job and job["state"] in ("waiting", "working"):
            return job
        job = {"state": "waiting", "message": "Tower is lining the subtitles up with the voices. It takes a minute or two.", "at": time.time()}
        _sync_jobs[item_id] = job
        _sync_queue.append((item_id, index))
    _sync_wake.set()
    return job


def sync_status(item_id):
    return _sync_jobs.get(item_id) or {"state": "none"}


def _sync_worker():
    while True:
        _sync_wake.wait(60)
        _sync_wake.clear()
        while True:
            with _lock:
                if not _sync_queue:
                    break
                item_id, index = _sync_queue.pop(0)
                job = _sync_jobs[item_id]
                job["state"] = "working"
            try:
                answer = fix_subtitles(item_id, index)
                job.update(state="done", **answer)
            except CareError as e:
                job.update(state="failed", message=e.message)
            except Exception as e:
                log(f"subtitles: {e}")
                job.update(state="failed", message="Something went wrong lining them up.")


# ---------- Tidy-up helper ----------


def _watched_lately(token):
    """Jellyfin id -> last time anyone watched it (movies, and shows by their episodes)"""
    users = _get("/Users", token) or []
    last = {}
    for u in users:
        uid = u.get("Id")
        for kind, extra in (("Movie", {}), ("Episode", {"Filters": "IsPlayed"}), ("Episode", {"Filters": "IsResumable"})):
            q = urllib.parse.urlencode(
                {
                    "IncludeItemTypes": kind,
                    "Recursive": "true",
                    "SortBy": "DatePlayed",
                    "SortOrder": "Descending",
                    "Limit": "20000",
                    "Fields": "ProviderIds",
                    "EnableImages": "false",
                    **extra,
                }
            )
            try:
                items = (_get(f"/Users/{uid}/Items?" + q, token) or {}).get("Items") or []
            except CareError:
                continue
            for it in items:
                ud = it.get("UserData") or {}
                when = _when(ud.get("LastPlayedDate"))
                if not when and (ud.get("Played") or ud.get("PlaybackPositionTicks")):
                    when = 1  # watched, but Jellyfin didn't keep when
                if not when:
                    continue
                key = it.get("SeriesId") if kind == "Episode" else it.get("Id")
                if key and when > last.get(key, 0):
                    last[key] = when
    return last


def _library(token):
    """Movies and shows in Jellyfin, by their outside ids (to match up with Radarr and Sonarr)"""
    out = {}
    q = urllib.parse.urlencode({"IncludeItemTypes": "Movie,Series", "Recursive": "true", "Fields": "ProviderIds,DateCreated", "EnableImages": "false", "Limit": "50000"})
    _, user = _admin()
    found = _get(f"/Users/{user}/Items?" + q, token) if user else _get("/Items?" + q, token)
    for it in (found or {}).get("Items") or []:
        ids = {k.lower(): str(v) for k, v in (it.get("ProviderIds") or {}).items() if v}
        for k in ("tmdb", "imdb", "tvdb"):
            if ids.get(k):
                out[(it["Type"], k, ids[k])] = it
    return out


_tidy_running = threading.Event()


def tidy(fresh=False):
    """The tidy-up lists. Looking through everything can take a while on a big library, so it works in
    the background and answers {"working": true} until it's done (the Server page asks again)."""
    if _tools is None:
        raise CareError(503, "The Server page tools aren't on Tower yet. Run the Benflix setup command again.")
    if not _admin()[0]:
        raise CareError(503, "Tower needs the main profile to sign in on Benflix once first.")
    with _lock:
        c = _cache.get("tidy")
        if c and not fresh and time.time() - c[0] < 6 * 3600:
            return c[1]
        if fresh:
            _cache.pop("tidy", None)
        if not _tidy_running.is_set():
            _tidy_running.set()
            threading.Thread(target=_tidy_work, name="care-tidy", daemon=True).start()
    return {"working": True, "old": [], "big": [], "old_total": 0, "old_count": 0}


def _tidy_work():
    try:
        _tidy_build()
    except CareError as e:
        with _lock:
            _cache["tidy"] = (time.time() - 6 * 3600 + 60, {"working": False, "problem": e.message, "old": [], "big": [], "old_total": 0, "old_count": 0})
    except Exception as e:
        log(f"tidy-up: {e}")
    finally:
        _tidy_running.clear()


def _tidy_build():
    token = _admin()[0]
    if not token:
        raise CareError(503, "Tower needs the main profile to sign in on Benflix once first.")
    last = _watched_lately(token)
    lib = _library(token)
    rows = []
    for app in ("radarr", "sonarr"):
        if not _tools.arr_ready(app):
            continue
        try:
            entries = _tools._arr(app, "GET", "/api/v3/movie" if app == "radarr" else "/api/v3/series", timeout=60) or []
        except Exception:
            continue
        for e in entries:
            size = e.get("sizeOnDisk") if app == "radarr" else (e.get("statistics") or {}).get("sizeOnDisk")
            if not size:
                continue
            if app == "radarr":
                jf = lib.get(("Movie", "tmdb", str(e.get("tmdbId")))) or lib.get(("Movie", "imdb", str(e.get("imdbId"))))
            else:
                jf = lib.get(("Series", "tvdb", str(e.get("tvdbId")))) or lib.get(("Series", "imdb", str(e.get("imdbId"))))
            added = max(_when(e.get("added")), _when((jf or {}).get("DateCreated")))
            rows.append(
                {
                    "app": app,
                    "id": e.get("id"),
                    "jellyfin_id": (jf or {}).get("Id"),
                    "title": e.get("title"),
                    "year": e.get("year"),
                    "kind": "Movie" if app == "radarr" else "Show",
                    "size": size,
                    "added": added,
                    "watched": last.get((jf or {}).get("Id"), 0) if jf else 0,
                }
            )
    now = time.time()
    old = [r for r in rows if r["added"] and now - r["added"] > YEAR and (now - r["watched"] > YEAR if r["watched"] > 1 else r["watched"] == 0)]
    old.sort(key=lambda r: -r["size"])
    big = sorted(rows, key=lambda r: -r["size"])[:30]
    answer = {
        "working": False,
        "old": old[:80],
        "old_total": sum(r["size"] for r in old),
        "old_count": len(old),
        "big": big,
        "radarr": _tools.arr_ready("radarr"),
        "sonarr": _tools.arr_ready("sonarr"),
        "at": now,
    }
    with _lock:
        _cache["tidy"] = (now, answer)
    return answer


def tidy_remove(body):
    """Removes the ticked movies and shows, files and all, through Radarr / Sonarr"""
    if _tools is None:
        raise CareError(503, "The Server page tools aren't on Tower yet.")
    picks = body.get("items") or []
    if not picks or not body.get("sure"):
        raise CareError(400, "Tick what to remove first.")
    done, failed = [], []
    for p in picks[:100]:
        app, arr_id = p.get("app"), p.get("id")
        if app not in ("radarr", "sonarr") or not isinstance(arr_id, int):
            continue
        path = (
            f"/api/v3/movie/{arr_id}?deleteFiles=true&addImportExclusion=true"
            if app == "radarr"
            else f"/api/v3/series/{arr_id}?deleteFiles=true&addImportListExclusion=true"
        )
        try:
            _tools._arr(app, "DELETE", path, timeout=60)
            done.append(p.get("title") or str(arr_id))
        except Exception as e:
            failed.append(f"{p.get('title') or arr_id}: {getattr(e, 'message', e)}")
    log(f"tidy-up removed {len(done)}: {', '.join(done)[:300]}")
    with _lock:
        history = _read("tidy.json", []) or []
        history.extend({"at": time.time(), "title": t} for t in done)
        _write("tidy.json", history[-500:])
        _cache.pop("tidy", None)
    _post("/Library/Refresh")
    msg = f"Removed {len(done)}." if done else "Nothing was removed."
    if failed:
        msg += " Couldn't remove: " + "; ".join(failed[:5])
    return {"message": msg, "removed": done, "failed": failed}


# ---------- Sunday report card ----------


def report(fresh=False, extra=None):
    with _lock:
        c = _cache.get("report")
        if c and not fresh and time.time() - c[0] < 3600:
            return dict(c[1], **(extra or {}))
    token, user = _admin()
    if not token:
        raise CareError(503, "Tower needs the main profile to sign in on Benflix once first.")
    now = time.time()
    week = now - 7 * DAY
    # What got added
    added = [it for it in _newest(800, "Movie,Episode") if _when(it.get("DateCreated")) > week]
    movies = [it for it in added if it.get("Type") == "Movie"]
    shows = {}
    for it in added:
        if it.get("Type") == "Episode":
            shows[it.get("SeriesName") or "A show"] = shows.get(it.get("SeriesName") or "A show", 0) + 1
    # Who watched the most (from Jellyfin's activity list)
    names = {u.get("Id"): u.get("Name") for u in (_get("/Users", token) or [])}
    plays = {}
    try:
        log_q = urllib.parse.urlencode({"minDate": _iso(week), "limit": "3000", "hasUserId": "true"})
        for e in (_get("/System/ActivityLog/Entries?" + log_q, token) or {}).get("Items") or []:
            if e.get("Type") in ("VideoPlayback", "AudioPlayback") and e.get("UserId"):
                plays[e["UserId"]] = plays.get(e["UserId"], 0) + 1
    except CareError:
        pass
    watchers = sorted(({"name": names.get(k) or "Someone", "plays": v} for k, v in plays.items()), key=lambda w: -w["plays"])[:5]
    # Problems found and fixed
    tests = playtest_status()
    synced = [s for s in (_read("subsync.json", []) or []) if s.get("at", 0) > week]
    removed = [t for t in (_read("tidy.json", []) or []) if t.get("at", 0) > week]
    # Space and things that need you
    disks = _tools.disks() if _tools else []
    needs = []
    for d in disks:
        total = d["used"] + d["free"]
        if total and d["free"] / total < 0.1:
            needs.append(f"Only {d['free'] / 1e12:.1f} TB of space left. Tidy-up can free some.")
    if tests["broken"]:
        needs.append(f"{len(tests['broken'])} file{'s' if len(tests['broken']) != 1 else ''} didn't pass the night-time play test.")
    if _tools is not None:
        try:
            t = _tools.tower()
            sick = [a["nice"] for a in (t.get("apps") or []) if a.get("problem")]
            if sick:
                needs.append("Needs a restart or a look: " + ", ".join(sick[:4]) + ".")
            stuck = [d for d in (t.get("downloads") or []) if d.get("stuck")]
            if stuck:
                needs.append(f"{len(stuck)} download{'s are' if len(stuck) != 1 else ' is'} stuck.")
        except Exception:
            pass
    answer = {
        "from": week,
        "to": now,
        "added_movies": len(movies),
        "added_episodes": sum(shows.values()),
        "movie_names": [_nice_name(m) for m in movies[:8]],
        "show_names": [f"{k} ({v} new)" if v > 1 else k for k, v in sorted(shows.items(), key=lambda kv: -kv[1])[:8]],
        "watchers": watchers,
        "checked": tests["checked_this_week"],
        "broken": len(tests["broken"]),
        "subtitles_fixed": len(synced),
        "removed": len(removed),
        "disks": disks,
        "needs": needs,
    }
    with _lock:
        _cache["report"] = (now, answer)
    return dict(answer, **(extra or {}))


# ---------- Routes (called by the helper) ----------


def status():
    return {"on": bool(_dir), "ffmpeg": ffmpeg_ok(), "log": list(_log_lines[-20:])}


def admin_route(method, path, query, read_json):
    """/api/admin/care/... for the Server page. Returns (status, answer) or None when it isn't one."""
    fresh = (query.get("fresh") or ["0"])[0] == "1"
    try:
        if method == "GET" and path == "/api/admin/care/report":
            return 200, report(fresh)
        if method == "GET" and path == "/api/admin/care/tidy":
            return 200, tidy(fresh)
        if method == "POST" and path == "/api/admin/care/tidy/remove":
            return 200, tidy_remove(read_json())
        if method == "GET" and path == "/api/admin/care/playtest":
            return 200, playtest_status()
        m = re.fullmatch(r"/api/admin/care/playtest/([\w-]+)/(ok|again)", path)
        if method == "POST" and m:
            return 200, playtest_ok(m.group(1)) if m.group(2) == "ok" else playtest_again(m.group(1))
        if method == "GET" and path == "/api/admin/care/status":
            return 200, status()
    except CareError as e:
        return e.status, {"error": e.message}
    return None


def user_route(method, path, read_json):
    """/api/subsync/... for the player's "Subtitles are off" button (anyone signed in)"""
    try:
        m = re.fullmatch(r"/api/subsync/([\w-]+)", path)
        if m and method == "POST":
            body = read_json() or {}
            index = body.get("index")
            return 200, sync_request(m.group(1), index if isinstance(index, int) else None)
        if m and method == "GET":
            return 200, sync_status(m.group(1))
    except CareError as e:
        return e.status, {"error": e.message}
    return 404, {"error": "Not found"}
