#!/usr/bin/env python3
"""
Benflix track tidier.

Looks at every MKV file under a folder and makes the audio and subtitle
tracks follow one simple standard:

  Audio names      "Japanese 2.0", "English 5.1", "English Commentary"
  Subtitle names   "English", "English - Signs & Songs", "English - Forced", "English - SDH",
                   "Spanish (Latin America)"
  Languages        filled in from the track name when the file says "unknown"
  Forced flag      only on signs/songs/forced tracks, never on full subtitles
  Default flag     anime: Japanese audio + full English subtitles
                   other: one normal audio track; English forced subtitles only
                          (or full English subtitles when the audio is not English)

Nothing is re-encoded. It only edits the small "label" part of the file with
mkvpropedit, which takes about a second per file.

SAFE BY DEFAULT: without --apply it only prints what it WOULD change.

  python3 benflix-fix-tracks.py /media/Anime                 preview only
  python3 benflix-fix-tracks.py /media/Anime --apply         make the changes
  python3 benflix-fix-tracks.py --undo /logs/undo-XXXX.json  put everything back

Other options:
  --newer-than-days N     only look at files changed in the last N days
  --include-hardlinked    also edit files that still have a second copy
                          (hardlink) in the downloads folder. Skipped by default
                          because editing them breaks torrent seeding.
  --log-dir DIR           where reports and undo files go (default: /logs)
"""

import argparse
import csv
import datetime as dt
import json
import os
import re
import subprocess
import sys
import time

PREFERRED_SUBS = "eng"  # the subtitle language Benflix should pick by default
ANIME_AUDIO = "jpn"  # anime plays in this language by default
SKIP_RECENT_MINUTES = 15
# never look inside these folders (the downloads folder lives inside the Media share)
SKIP_FOLDERS = {"downloads", "incomplete", "trailers", "extras", "featurettes", "behind the scenes"}  # do not touch files that are still being downloaded/imported

# ---------------------------------------------------------------------------
# Languages
# ---------------------------------------------------------------------------

# code -> nice name (several codes can mean the same language)
LANG_NAMES = {
    "eng": "English", "jpn": "Japanese", "spa": "Spanish", "fre": "French", "fra": "French",
    "ger": "German", "deu": "German", "ita": "Italian", "por": "Portuguese", "kor": "Korean",
    "chi": "Chinese", "zho": "Chinese", "rus": "Russian", "ara": "Arabic", "hin": "Hindi",
    "dut": "Dutch", "nld": "Dutch", "pol": "Polish", "tur": "Turkish", "swe": "Swedish",
    "nor": "Norwegian", "dan": "Danish", "fin": "Finnish", "tha": "Thai", "vie": "Vietnamese",
    "ind": "Indonesian", "may": "Malay", "msa": "Malay", "heb": "Hebrew", "gre": "Greek",
    "ell": "Greek", "cze": "Czech", "ces": "Czech", "hun": "Hungarian", "rum": "Romanian",
    "ron": "Romanian", "ukr": "Ukrainian", "tgl": "Tagalog", "fil": "Filipino",
    "hrv": "Croatian", "srp": "Serbian", "bul": "Bulgarian", "slo": "Slovak", "slk": "Slovak",
    "slv": "Slovenian", "cat": "Catalan", "baq": "Basque", "eus": "Basque", "glg": "Galician",
    "per": "Persian", "fas": "Persian", "tam": "Tamil", "tel": "Telugu", "ben": "Bengali",
    "est": "Estonian", "lav": "Latvian", "lit": "Lithuanian", "ice": "Icelandic", "isl": "Icelandic",
    "nob": "Norwegian", "nno": "Norwegian", "urd": "Urdu", "mal": "Malayalam", "kan": "Kannada",
}
SAME_LANG = {"fra": "fre", "deu": "ger", "zho": "chi", "nld": "dut", "msa": "may",
             "ell": "gre", "ces": "cze", "ron": "rum", "slk": "slo", "eus": "baq", "fas": "per",
             "isl": "ice", "nob": "nor", "nno": "nor"}
TWO_LETTER = {"en": "eng", "ja": "jpn", "es": "spa", "fr": "fre", "de": "ger", "it": "ita",
              "pt": "por", "ko": "kor", "zh": "chi", "ru": "rus", "ar": "ara", "hi": "hin",
              "id": "ind", "ms": "may", "vi": "vie", "th": "tha", "pl": "pol", "tr": "tur",
              "nl": "dut", "hu": "hun", "hr": "hrv", "sv": "swe", "no": "nor", "nb": "nor",
              "nn": "nor", "da": "dan", "fi": "fin", "he": "heb", "el": "gre", "cs": "cze",
              "ro": "rum", "uk": "ukr", "sr": "srp", "bg": "bul", "sk": "slo", "sl": "slv",
              "ca": "cat", "eu": "baq", "gl": "glg", "fa": "per", "ta": "tam", "te": "tel",
              "bn": "ben", "et": "est", "lv": "lav", "lt": "lit", "is": "ice", "ur": "urd",
              "ml": "mal", "kn": "kan", "tl": "tgl", "fil": "fil"}

# words in a track name that tell us the language
LANG_WORDS = [
    (r"\benglish\b|\beng\b|\ben\b|\bdub(bed)?\b", "eng"),
    (r"japanese|\bjpn\b|\bjap\b|\bjp\b|\bja\b|日本語", "jpn"),
    (r"spanish|espa[nñ]ol|latino|castellano|\bspa\b|\besp\b", "spa"),
    (r"french|fran[cç]ais|\bfre\b|\bfra\b", "fre"),
    (r"german|deutsch|\bger\b|\bdeu\b", "ger"),
    (r"italian|italiano|\bita\b", "ita"),
    (r"portugu[eê]s|brazil|brasil|\bpor\b|pt-br", "por"),
    (r"korean|한국어|\bkor\b", "kor"),
    (r"chinese|mandarin|cantonese|简体|繁體|中文|\bchi\b|\bzho\b", "chi"),
    (r"russian|русский|\brus\b", "rus"),
    (r"arabic|العربية|\bara\b", "ara"),
    (r"hindi|\bhin\b", "hin"),
]

COMMENTARY = re.compile(r"comment", re.I)
SIGNS = re.compile(
    r"sign|song|forced|\bs\s*&\s*s\b|karaoke|\bfx\b|lyrics"
    # the same words in Portuguese, Spanish and French
    r"|letreiro|can[cç][oõ]es|for[cç]ad|forzad|cartel|canciones|panneaux|chansons|forc[ée]",
    re.I,
)
# words that say nothing about a track apart from its language, sound or file format
GENERIC_WORDS = {
    "english", "japanese", "eng", "jpn", "jap", "jp", "en", "stereo", "surround", "mono", "aac",
    "ac", "eac", "e", "dd", "ddp", "flac", "opus", "dts", "hd", "ma", "truehd", "atmos", "ch",
    "full", "subtitles", "subtitle", "subs", "sub", "dialog", "dialogue", "main", "default",
    "pgs", "ass", "ssa", "srt", "kbps", "bit", "audio", "track", "original",
}
FULL = re.compile(r"\bfull\b|dialog|dialogue", re.I)
SDH = re.compile(r"\bsdh\b|\bcc\b|hearing|\bhi\b", re.I)


def norm_lang(code):
    code = (code or "").strip().lower()
    if not code or code in ("und", "unk", "mis", "zxx", "mul"):
        return None
    code = code.split("-")[0]
    code = TWO_LETTER.get(code, code)
    return SAME_LANG.get(code, code)


def lang_from_name(name):
    for pattern, code in LANG_WORDS:
        if re.search(pattern, name or "", re.I):
            return code
    return None


# regional versions worth keeping in the name
REGIONS = [
    (r"latino|latin\s*am|lat\.?\s*am|\bes-419\b|\b419\b", "spa", "Latin America"),
    (r"castellano|espa[nñ]a|\bspain\b|\bes-es\b|european", "spa", "Spain"),
    (r"brazil|brasil|pt-br", "por", "Brazil"),
    (r"canad|qu[eé]b|fr-ca", "fre", "Canada"),
    (r"cantonese|\bzh-hk\b|hong\s*kong", "chi", "Cantonese"),
    (r"traditional|繁體|繁体|\bzh-hant\b|\bzh-tw\b", "chi", "Traditional"),
    (r"simplified|简体|\bzh-hans\b|\bzh-cn\b", "chi", "Simplified"),
]


def is_generic(name):
    words = re.findall(r"[a-z]+", (name or "").lower())
    return all(w in GENERIC_WORDS for w in words)


def lang_name(code, old_name=""):
    """Nice name for a language, or None when we don't know the code (then the track keeps its name)."""
    name = LANG_NAMES.get(code)
    if not name:
        return None
    for pattern, region_lang, region in REGIONS:
        if region_lang == code and re.search(pattern, old_name or "", re.I):
            return f"{name} ({region})"
    return name


# ---------------------------------------------------------------------------
# Reading a file
# ---------------------------------------------------------------------------


# mkvtoolnix mangles non-English track names (like "Español") unless it runs in a UTF-8 locale
UTF8_ENV = dict(os.environ, LC_ALL="C.UTF-8", LANG="C.UTF-8")


def run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", env=UTF8_ENV)


def read_tracks(path):
    out = run(["mkvmerge", "-J", path])
    if out.returncode not in (0, 1):  # 1 = warnings only
        raise RuntimeError((out.stderr or out.stdout).strip()[:200])
    info = json.loads(out.stdout)
    tracks = []
    for t in info.get("tracks", []):
        if t.get("type") not in ("audio", "subtitles"):
            continue
        p = t.get("properties", {})
        # how many lines a subtitle has (from the statistics tags, or else from the index)
        frames = p.get("tag_number_of_frames") or (p.get("num_index_entries") if t["type"] == "subtitles" else None)
        tracks.append({
            "number": p.get("number"),
            "type": t["type"],
            "name": p.get("track_name") or "",
            "lang_raw": p.get("language") or "und",
            # the classic 3-letter code first; the newer IETF code only when the classic one is missing
            "lang": norm_lang(p.get("language")) or norm_lang(p.get("language_ietf")),
            "default": bool(p.get("default_track")),
            "forced": bool(p.get("forced_track")),
            "channels": p.get("audio_channels"),
            "frames": int(frames) if str(frames or "").isdigit() and int(frames) > 0 else None,
        })
    return tracks


def channels_text(n):
    return {1: "Mono", 2: "2.0", 3: "2.1", 4: "4.0", 5: "5.0", 6: "5.1", 7: "6.1", 8: "7.1"}.get(n, f"{n}ch" if n else "")


# ---------------------------------------------------------------------------
# Working out what each track SHOULD look like
# ---------------------------------------------------------------------------


def plan_file(path, tracks, is_anime):
    """Returns a list of (track, {property: new value}) for tracks that need a change."""
    want = {id(t): {"name": t["name"], "lang": t["lang"], "default": t["default"], "forced": t["forced"]}
            for t in tracks}
    audio = [t for t in tracks if t["type"] == "audio"]
    subs = [t for t in tracks if t["type"] == "subtitles"]

    # 1. Fill in missing languages from the track name
    for t in tracks:
        if not t["lang"]:
            guess = lang_from_name(t["name"])
            if guess:
                want[id(t)]["lang"] = guess
    lang = lambda t: want[id(t)]["lang"]

    # 2. Work out what kind each subtitle is: signs, sdh or full
    kind = {}
    for t in subs:
        name = t["name"]
        if FULL.search(name) and not re.search(r"no\s*full", name, re.I):
            kind[id(t)] = "full"
        elif SIGNS.search(name):
            kind[id(t)] = "signs"
        elif SDH.search(name):
            kind[id(t)] = "sdh"
        else:
            kind[id(t)] = "full"
    # Use the number of lines to catch badly named tracks: a "forced" track with as many
    # lines as the biggest one is really a full track, and a tiny unnamed one is signs.
    by_lang = {}
    for t in subs:
        by_lang.setdefault(lang(t), []).append(t)
    for group in by_lang.values():
        counts = [t["frames"] for t in group if t["frames"]]
        if len(group) < 2 or len(counts) < 2:
            continue
        biggest = max(counts)
        for t in group:
            if not t["frames"]:
                continue
            if kind[id(t)] == "signs" and t["frames"] >= 0.7 * biggest and not SIGNS.search(t["name"]):
                kind[id(t)] = "full"
            elif kind[id(t)] == "full" and t["frames"] < 0.35 * biggest and not FULL.search(t["name"]):
                kind[id(t)] = "signs"
    # A track that has the forced flag but nothing in its name: trust the flag only when
    # there is a bigger track of the same language next to it.
    for group in by_lang.values():
        for t in group:
            if t["forced"] and kind[id(t)] == "full" and not t["name"]:
                others = [o for o in group if o is not t and (o["frames"] or 0) > (t["frames"] or 0)]
                if others:
                    kind[id(t)] = "signs"

    # 3. Names
    def unique(names_for):
        seen = {}
        for t, base in names_for:
            seen.setdefault(base, []).append(t)
        for base, ts in seen.items():
            if len(ts) == 1:
                want[id(ts[0])]["name"] = base
                continue
            # Look-alike tracks: keep the uploader's name as a hint ("English [MTBB]") when it says
            # something useful; otherwise the first is plain "English" and the rest "English (2)"...
            olds = []
            for t in ts:
                old = t["name"].strip()
                # a name this tool already gave ("English [MTBB]") keeps just its hint ("MTBB")
                if old.startswith(base + " [") and old.endswith("]"):
                    old = old[len(base) + 2:-1]
                olds.append(old)
            plain = 0
            for t, old in zip(ts, olds):
                if old and not is_generic(old) and olds.count(old) == 1:
                    want[id(t)]["name"] = f"{base} [{old}]"
                else:
                    plain += 1
                    want[id(t)]["name"] = base if plain == 1 else f"{base} ({plain})"

    names = []
    for t in audio:
        if not lang(t):
            continue  # do not rename a track when we do not know its language
        base = lang_name(lang(t), t["name"])
        if not base:
            continue
        base += " Commentary" if COMMENTARY.search(t["name"]) else (" " + channels_text(t["channels"])).rstrip()
        names.append((t, base))
    unique(names)
    names = []
    for t in subs:
        if not lang(t):
            continue
        k = kind[id(t)]
        base = lang_name(lang(t), t["name"])
        if not base:
            continue
        if COMMENTARY.search(t["name"]):
            base += " - Commentary"
        elif k == "signs":
            base += " - Signs & Songs" if is_anime else " - Forced"
        elif k == "sdh":
            base += " - SDH"
        names.append((t, base))
    unique(names)

    # 4. Forced flag: only on signs tracks. Audio is never forced.
    for t in audio:
        want[id(t)]["forced"] = False
    for t in subs:
        want[id(t)]["forced"] = kind[id(t)] == "signs"

    # 5. Default audio: exactly one
    normal_audio = [t for t in audio if not COMMENTARY.search(t["name"])] or audio
    pick = None
    if is_anime:
        japanese = [t for t in normal_audio if lang(t) == ANIME_AUDIO]
        # with two Japanese tracks, keep the one the uploader already picked
        pick = next((t for t in japanese if t["default"]), None) or (japanese[0] if japanese else None)
    if pick is None:
        pick = next((t for t in normal_audio if t["default"]), None) or (normal_audio[0] if normal_audio else None)
    for t in audio:
        want[id(t)]["default"] = t is pick

    # 6. Default subtitles
    audio_lang = lang(pick) if pick else None
    pref = [t for t in subs if lang(t) == PREFERRED_SUBS and not COMMENTARY.search(t["name"])]
    full = [t for t in pref if kind[id(t)] == "full"] + [t for t in pref if kind[id(t)] == "sdh"]
    signs = [t for t in pref if kind[id(t)] == "signs"]
    sub_pick = None
    if audio_lang and audio_lang != PREFERRED_SUBS:
        # foreign audio (anime in Japanese, foreign films): full English subtitles
        sub_pick = next((t for t in full if t["default"]), None) or (full[0] if full else None)
    elif signs:
        # English audio: only the forced/signs track, so on-screen text still gets translated
        sub_pick = signs[0]
    if pick is not None and not audio_lang:
        # We can't tell what language the audio is, so we can't know if subtitles are needed.
        # Leave the subtitle on/off and forced flags exactly as they are.
        for t in subs:
            want[id(t)]["default"] = t["default"]
            want[id(t)]["forced"] = t["forced"]
    else:
        for t in subs:
            want[id(t)]["default"] = t is sub_pick

    # 7. Compare with what is there now
    changes = []
    for t in tracks:
        w = want[id(t)]
        diff = {}
        if w["name"] != t["name"]:
            diff["name"] = w["name"]
        if w["lang"] and w["lang"] != t["lang"]:
            diff["language"] = w["lang"]
        if w["default"] != t["default"]:
            diff["flag-default"] = "1" if w["default"] else "0"
        if w["forced"] != t["forced"]:
            diff["flag-forced"] = "1" if w["forced"] else "0"
        if diff:
            changes.append((t, diff))
    return changes


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def describe(t, diff):
    kind = "Audio" if t["type"] == "audio" else "Subs "
    before = f'"{t["name"]}" {t["lang_raw"]}' + (" default" if t["default"] else "") + (" forced" if t["forced"] else "")
    bits = []
    if "name" in diff:
        bits.append(f'name -> "{diff["name"]}"')
    if "language" in diff:
        bits.append(f'language -> {diff["language"]}')
    if "flag-default" in diff:
        bits.append("default ON" if diff["flag-default"] == "1" else "default off")
    if "flag-forced" in diff:
        bits.append("forced ON" if diff["flag-forced"] == "1" else "forced off")
    return f"   {kind} #{t['number']}: {before}  =>  " + ", ".join(bits)


def apply_changes(path, changes):
    cmd = ["mkvpropedit", "--quiet", path]
    for t, diff in changes:
        cmd += ["--edit", f"track:@{t['number']}"]
        for prop, value in diff.items():
            cmd += ["--set", f"{prop}={value}"]
    out = run(cmd)
    if out.returncode not in (0, 1):
        raise RuntimeError((out.stderr or out.stdout).strip()[:200])


def undo(undo_file):
    with open(undo_file) as f:
        entries = json.load(f)
    ok = 0
    for e in entries:
        cmd = ["mkvpropedit", "--quiet", e["file"]]
        for t in e["tracks"]:
            cmd += ["--edit", f"track:@{t['number']}"]
            cmd += ["--set", f"name={t['name']}"] if t["name"] else ["--delete", "name"]
            cmd += ["--set", f"language={t['lang_raw']}",
                    "--set", f"flag-default={1 if t['default'] else 0}",
                    "--set", f"flag-forced={1 if t['forced'] else 0}"]
        r = run(cmd)
        if r.returncode in (0, 1):
            ok += 1
        else:
            print("Could not undo:", e["file"], (r.stderr or r.stdout).strip()[:200])
    print(f"Put back {ok} of {len(entries)} files. Now run 'Scan All Libraries' in Jellyfin.")


def main():
    ap = argparse.ArgumentParser(description="Tidy audio and subtitle track labels in MKV files (no re-encoding).")
    ap.add_argument("folders", nargs="*")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--undo")
    ap.add_argument("--newer-than-days", type=float)
    ap.add_argument("--include-hardlinked", action="store_true")
    ap.add_argument("--log-dir", default="/logs")
    args = ap.parse_args()

    if args.undo:
        return undo(args.undo)
    if not args.folders:
        ap.error("give a folder, for example /media/Anime")

    os.makedirs(args.log_dir, exist_ok=True)
    stamp = dt.datetime.now().strftime("%Y-%m-%d_%H%M")
    mode = "apply" if args.apply else "preview"
    report_path = os.path.join(args.log_dir, f"{mode}-{stamp}.txt")
    csv_path = os.path.join(args.log_dir, f"{mode}-{stamp}.csv")
    undo_path = os.path.join(args.log_dir, f"undo-{stamp}.json")
    now = time.time()

    files = []
    other_videos = 0
    for folder in args.folders:
        for root, dirs, names in os.walk(folder):
            dirs[:] = [d for d in dirs if not d.startswith(".") and d.lower() not in SKIP_FOLDERS]
            for n in names:
                low = n.lower()
                if low.endswith(".mkv") and not low.endswith(("-trailer.mkv", "-sample.mkv")):
                    files.append(os.path.join(root, n))
                elif low.endswith((".mp4", ".m4v", ".avi", ".ts")):
                    other_videos += 1
    files.sort()

    checked = to_change = changed = skipped_links = skipped_recent = errors = 0
    undo_entries = []
    shown = 0
    with open(report_path, "w") as report, open(csv_path, "w", newline="") as cf:
        w = csv.writer(cf)
        w.writerow(["file", "track", "type", "old name", "old language", "old default", "old forced", "changes"])
        for path in files:
            try:
                st = os.stat(path)
            except OSError:
                continue
            if args.newer_than_days and now - st.st_mtime > args.newer_than_days * 86400:
                continue
            if now - st.st_mtime < SKIP_RECENT_MINUTES * 60:
                skipped_recent += 1
                continue
            checked += 1
            try:
                tracks = read_tracks(path)
                is_anime = "anime" in path.lower()
                changes = plan_file(path, tracks, is_anime)
            except Exception as e:  # noqa: BLE001
                errors += 1
                report.write(f"ERROR reading {path}: {e}\n")
                continue
            if not changes:
                continue
            if st.st_nlink > 1 and not args.include_hardlinked:
                skipped_links += 1
                report.write(f"SKIPPED (still has a copy in downloads, probably seeding): {path}\n")
                continue
            to_change += 1
            lines = [path] + [describe(t, d) for t, d in changes]
            report.write("\n".join(lines) + "\n\n")
            for t, d in changes:
                w.writerow([path, t["number"], t["type"], t["name"], t["lang_raw"], t["default"], t["forced"],
                            "; ".join(f"{k}={v}" for k, v in d.items())])
            if shown < 15:
                print("\n".join(lines) + "\n")
                shown += 1
            if args.apply:
                try:
                    apply_changes(path, changes)
                    changed += 1
                    undo_entries.append({"file": path, "tracks": [t for t, _ in changes]})
                except Exception as e:  # noqa: BLE001
                    errors += 1
                    report.write(f"ERROR changing {path}: {e}\n")

    if undo_entries:
        with open(undo_path, "w") as f:
            json.dump(undo_entries, f, ensure_ascii=False, indent=1)

    print("=" * 60)
    print(f"Checked {checked} MKV files.")
    if args.apply:
        print(f"Changed {changed} files.")
        if undo_entries:
            print(f"Undo file (puts everything back): {undo_path}")
        print("Next: in Jellyfin go to Dashboard > Libraries > Scan All Libraries.")
    else:
        print(f"{to_change} files WOULD change. Nothing was touched (this was a preview).")
    if skipped_links:
        print(f"{skipped_links} files skipped because they are still seeding (see the report).")
    if skipped_recent:
        print(f"{skipped_recent} files skipped because they were changed in the last {SKIP_RECENT_MINUTES} minutes.")
    if other_videos:
        print(f"{other_videos} non-MKV videos (MP4 etc.) were left alone.")
    if errors:
        print(f"{errors} files could not be read or changed (see the report).")
    print(f"Full list: {report_path}")
    print(f"Spreadsheet: {csv_path}")


if __name__ == "__main__":
    sys.exit(main())
