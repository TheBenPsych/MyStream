"""
Glowfin Books: ebooks, audiobooks and manga, all from Tower's own folders (manga back 2026-11-21, filled by Harbor).

How it works, in plain words:
- Tower looks through Homelab/Media/Books and Audiobooks every few minutes and makes a list
  of everything there. Benflix (TV, phone and browser) reads that list from here.
    * Books: every .epub or .pdf (other ebook files are listed too, but only EPUB and PDF open in Benflix).
      The title and author come from the book itself (or a metadata.opf file next to it),
      else from the folder names (Books/Author/Title).
    * Audiobooks: each folder of sound files is one audiobook (Audiobooks/Author/Author - Title), or one
      .m4b file. The FOLDER names win over the song info inside the files, because downloaded audiobooks
      often say the narrator is the "artist". Each file is a chapter (or the chapters inside one .m4b).
    * Manga: Manga/Series/ with one .cbz per chapter ("Series - Chapter 010.cbz", halves like 004.5), a cover.jpg
      and a series.json that Harbor writes (title, author, story, status, AniList number, where each chapter
      came from). Older layouts (one file or picture folder per volume) still work. Manga/engine-trial and
      anything starting with "." or "_" is skipped. Manga never shows on Kids profiles.
- Descriptions, genres, years and scores come from Open Library (books) and AniList / MyAnimeList (manga),
  looked up once and saved.
- Manga pages are shrunk on Tower to the size the TV or phone asks for, and the next three are made ready
  ahead, so page turns stay quick on slow TVs.
- Your place in every book, what you want to read and what you've finished are saved here for each
  profile, so they follow you from the TV to the phone to the browser.
- Asking for a book: Benflix searches Open Library, and the request goes to Chaptarr (which finds an
  ebook and/or audiobook the same way Sonarr and Radarr do). Ben's requests go straight through; anyone
  else's wait for Ben to say yes.

Settings from the setup command: CHAPTARR_URL and CHAPTARR_KEY (Chaptarr, the Readarr-style app Ben picked,
which keeps ebooks in /data/Books and audiobooks in /data/Audiobooks, renamed into Author/Title folders).
Saved in /booksdata (Tower's appdata/benflix-books). Only uses the Python standard library, plus
ffmpeg/ffprobe (sound file lengths, chapters, small cover pictures) and 7z (for .cbr files) when present.
"""

import hashlib
import html
import json
import os
import random
import re
import shutil
import subprocess
import threading
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
import xml.etree.ElementTree as ET

try:  # Requests > Books rows (NYT, Hardcover, Open Library), in benflix_booklists.py next to this file
    import benflix_booklists as booklists
except ImportError:
    booklists = None

MEDIA = "/media"
SECTIONS = (
    ("book", ("Books", "Ebooks", "eBooks")),
    ("audiobook", ("Audiobooks", "AudioBooks", "Audio Books")),
    ("manga", ("Manga",)),
)
MANGA_SKIP = {"engine-trial"}  # the trial engine's raw downloads; Harbor's tidy copies are what Glowfin reads
DATA_DIRS = ("/booksdata", "/musicdata/books", "/skips/books")
CH_URL = os.environ.get("CHAPTARR_URL", "").rstrip("/")
CH_KEY = os.environ.get("CHAPTARR_KEY", "")


def can_ask():
    return bool(CH_URL)
UA = "Benflix-Books/1.0 (home media server)"

EBOOK = {".epub", ".pdf", ".mobi", ".azw3", ".azw", ".kepub", ".fb2", ".txt"}
READABLE = {".epub", ".pdf"}
AUDIO = {".mp3", ".m4a", ".m4b", ".aac", ".ogg", ".opus", ".flac", ".wav", ".wma", ".mka"}
PAGES = {".cbz", ".zip", ".cbr", ".rar", ".cb7", ".7z", ".pdf", ".epub"}
PICS = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".avif"}
TYPES = {
    ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp", ".gif": "image/gif",
    ".bmp": "image/bmp", ".avif": "image/avif", ".svg": "image/svg+xml",
    ".mp3": "audio/mpeg", ".m4a": "audio/mp4", ".m4b": "audio/mp4", ".aac": "audio/aac", ".ogg": "audio/ogg",
    ".opus": "audio/ogg", ".flac": "audio/flac", ".wav": "audio/wav", ".wma": "audio/x-ms-wma", ".mka": "audio/x-matroska",
    ".epub": "application/epub+zip", ".pdf": "application/pdf", ".mobi": "application/x-mobipocket-ebook",
    ".azw3": "application/vnd.amazon.ebook", ".xhtml": "application/xhtml+xml", ".html": "text/html; charset=utf-8",
    ".htm": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".ttf": "font/ttf", ".otf": "font/otf",
    ".woff": "font/woff", ".woff2": "font/woff2", ".xml": "application/xml", ".ncx": "application/xml",
}
THUMB_WIDTHS = (160, 320, 640, 1200)
PAGE_WIDTHS = (160, 480, 800, 1100, 1600)  # manga pages: small strip, phone, TV, big tablet, browser
PAGES_AHEAD = 3  # how many pages after the one asked for Tower shrinks ahead of time
NEW_DAYS = 14  # "New for you" keeps a title this long unless it's opened
GIVE_UP_AFTER = 7 * 86400  # A request still not found after a week says "Couldn't find it"
KIDS_WORDS = ("juvenile", "children", "picture book", "kids", "middle grade", "for young readers")
ADULT_WORDS = ("hentai", "ecchi", "erotic", "erotica", "adult", "smut", "mature")

_lock = threading.RLock()
_kick = threading.Event()
_dir = None
_jf = None
_lib = {"titles": {}, "vols": {}, "scanned": 0, "folders": {}}
_doubles = {}  # a second copy of a book -> the copy that shows
_meta = {}  # title key -> looked-up info (description, genres, scores, cover link ...)
_users = {}  # Jellyfin account id -> that profile's places, want list, finished ...
_state = {"requests": [], "kids_allow": [], "covers": {}, "manga_to_add": []}
_probe = {}  # sound file path -> (mtime, seconds, tags, chapters)
_log_lines = []
_admin = {}


class BooksError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def log(text):
    print("[books] " + text, flush=True)
    with _lock:
        _log_lines.append(time.strftime("%H:%M ") + text)
        del _log_lines[:-200]


# ---------- Starting and saving ----------


def start(jellyfin_call=None):
    """Called once by the helper when it starts"""
    global _dir, _jf
    _jf = jellyfin_call
    for d in DATA_DIRS:
        try:
            os.makedirs(d, exist_ok=True)
            test = os.path.join(d, ".write-test")
            with open(test, "w") as f:
                f.write("ok")
            os.remove(test)
            _dir = d
            break
        except OSError:
            continue
    if _dir:
        for sub in ("users", "thumbs", "pages", "covers"):
            os.makedirs(os.path.join(_dir, sub), exist_ok=True)
        _meta.update(_read(os.path.join(_dir, "meta.json"), {}))
        _state.update(_read(os.path.join(_dir, "state.json"), {}))
        _look_again_once()
        _probe_saved = _read(os.path.join(_dir, "probe.json"), {})
        for k, v in _probe_saved.items():
            _probe[k] = tuple(v)
    else:
        log("No folder to save in, so places and requests won't be kept. Run the Benflix setup command again.")
    threading.Thread(target=_loop, name="books", daemon=True).start()
    if booklists is not None:
        import sys

        booklists.start(sys.modules[__name__], _dir)


def _read(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def _write(path, data):
    if not _dir:
        return
    tmp = path + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        os.replace(tmp, path)
    except OSError as e:
        log(f"Couldn't save {os.path.basename(path)}: {e}")


def _save_state():
    if _dir:
        with _lock:
            _write(os.path.join(_dir, "state.json"), _state)


def _save_meta():
    if _dir:
        with _lock:
            _write(os.path.join(_dir, "meta.json"), _meta)


def _save_probe():
    if _dir:
        with _lock:
            _write(os.path.join(_dir, "probe.json"), {k: list(v) for k, v in _probe.items()})


def _user(uid):
    uid = re.sub(r"[^\w-]", "", str(uid or ""))[:64] or "anyone"
    with _lock:
        u = _users.get(uid)
        if u is None:
            u = _read(os.path.join(_dir, "users", uid + ".json"), {}) if _dir else {}
            for k in ("place", "done", "want", "finished", "opened", "modes", "sent", "time", "marks"):
                u.setdefault(k, {})
            u["_id"] = uid
            _users[uid] = u
        return u


def _save_user(u):
    if _dir:
        with _lock:
            _write(os.path.join(_dir, "users", u["_id"] + ".json"), {k: v for k, v in u.items() if not k.startswith("_")})


def _look_again_once():
    """Before Benflix knew Chaptarr keeps ebooks and audiobooks apart (and hides downloads that don't name
    the author), every book request ended up as "Couldn't find it". Look for all of those again, once."""
    if _state.get("looked_again", 0) >= 1:
        return
    n = 0
    for r in _state["requests"]:
        if r.get("state") == "cant":
            r.update(state="problem", at=int(time.time()), problem="Looking again")
            for k in ("ch", "chIds", "pickedAt", "searchedAt"):
                r.pop(k, None)
            n += 1
    _state["looked_again"] = 1
    if n:
        log(f"Looking again for {n} book requests that said Couldn't find it")
        _save_state()


def _loop():
    last_scan = 0
    last_meta = 0
    last_ll = 0
    last_tidy = time.time() - 86400 + 900  # first tidy a quarter of an hour after starting, then daily
    while True:
        now = time.time()
        try:
            if now - last_scan > 600 or not _lib["scanned"]:
                scan()
                last_scan = time.time()
            if now - last_meta > 30:
                look_up_some()
                last_meta = time.time()
            if now - last_ll > 300:
                check_requests()
                manga_check()
                last_ll = time.time()
            if now - last_tidy > 86400:
                last_tidy = time.time()
                tidy_page_copies()
        except Exception as e:  # Keep going whatever happens
            log(f"Problem: {e!r}")
        _kick.wait(30)
        if _kick.is_set():
            _kick.clear()
            last_scan = 0


# ---------- Small helpers ----------


def _hid(*parts):
    return hashlib.sha1("\x00".join(parts).encode("utf-8", "surrogateescape")).hexdigest()[:12]


def natural(text):
    return [int(p) if p.isdigit() else p.lower() for p in re.split(r"(\d+)", text)]


def norm(text):
    t = unicodedata.normalize("NFKD", str(text or "")).encode("ascii", "ignore").decode().lower()
    t = re.sub(r"\(.*?\)|\[.*?\]", " ", t)
    t = re.sub(r"\b(the|a|an)\b", " ", t)
    return re.sub(r"[^a-z0-9]+", " ", t).strip()


def tidy_name(name):
    """Folder or file name -> a readable title"""
    n = re.sub(r"[_]+", " ", name)
    n = re.sub(r"\s*[\[(](?:epub|pdf|mobi|azw3|retail|unabridged|abridged|mp3|m4b|\d{2,4}\s*kbps|v\d|digital|scan\w*)[\])]", "", n, flags=re.I)
    n = re.sub(r"\s{2,}", " ", n).strip(" -.")
    return n


def strip_author(title, author):
    """'J.K. Rowling - Harry Potter and ...' -> 'Harry Potter and ...'"""
    if author:
        for sep in (" - ", " – ", " — ", "-"):
            if title.lower().startswith(author.lower() + sep):
                return title[len(author) + len(sep):].strip()
            if title.lower().endswith(sep + author.lower()):
                return title[: -len(author) - len(sep)].strip()
    return title


# Words that are publisher lines or book types, not real series
NOT_SERIES = {
    "hard case crime", "penguin classics", "vintage classics", "oxford world s classics", "modern library",
    "everyman s library", "signet classics", "bantam classics", "dover thrift editions", "a novel", "novel",
    "fiction", "nonfiction", "non fiction", "unknown", "none", "standalone", "stand alone", "kindle edition",
    "audible originals", "collected works", "short stories", "stories", "omnibus", "book", "books",
}
NAME_SUFFIX = {"jr", "jr.", "sr", "sr.", "ii", "iii", "iv", "phd", "md"}


def clean_author(raw):
    """'King, Stephen, 1947-;Fitzgerald, F-stop' -> 'Stephen King', 'Rowling, J. K' -> 'J.K. Rowling'"""
    s = html.unescape(str(raw or "")).strip()
    if not s:
        return ""
    # Only the first of several authors (the rest are photographers, editors, narrators...)
    s = re.split(r"\s*(?:;|\||&|\band\b|\bwith\b|\bfeat\.?\b)\s*", s, flags=re.I)[0]
    # Birth and death years: '1947-', '1890-1976', '(1947-2020)'
    s = re.sub(r"[(\[]?\b\d{3,4}\s*-\s*(?:\d{3,4})?[)\]]?", " ", s)
    s = re.sub(r"\s*[(\[].*?[)\]]", " ", s)
    parts = [p.strip(" .-") if p.strip().lower().rstrip(".") in NAME_SUFFIX else p.strip(" -") for p in s.split(",")]
    parts = [p for p in parts if p]
    if len(parts) >= 2:
        if " " not in parts[0] or len(parts) > 2 and all(p.lower().rstrip(".") in NAME_SUFFIX for p in parts[2:]):
            # 'Last, First' (and maybe ', Jr.')
            rest = [p for p in parts[2:] if p.lower().rstrip(".") in NAME_SUFFIX]
            s = " ".join([parts[1], parts[0]] + rest)
        else:
            # 'Stephen King, Peter Straub' -> the first one
            s = parts[0]
    else:
        s = parts[0] if parts else ""
    # Initials together: 'J. K Rowling', 'J K Rowling', 'J.K.Rowling' -> 'J.K. Rowling'
    s = re.sub(r"\b([A-Z])\.(?=[A-Z][a-z])", r"\1. ", s)
    words, out, initials = s.split(), [], ""
    for w in words:
        if re.fullmatch(r"(?:[A-Z]\.?)+", w) and (len(w) == 1 or "." in w or len(w) <= 3 and w.isupper() and len(words) > 1):
            initials += "".join(c + "." for c in w if c.isalpha())
            continue
        if initials:
            out.append(initials)
            initials = ""
        out.append(w)
    if initials:
        out.append(initials)
    s = " ".join(out)
    return re.sub(r"\s{2,}", " ", s).strip(" ,.-") + ("." if s.endswith(".") and re.search(r"\b[A-Z]\.$", s) else "")


def author_key(name):
    """The same person however their name is written ('J.K. Rowling' = 'Rowling, J. K' = 'J K Rowling')"""
    t = unicodedata.normalize("NFKD", clean_author(name)).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z]+", "", t)


def tidy_title(title):
    """'Doctor Sleep: A Novel' -> 'Doctor Sleep'"""
    t = re.sub(r"\s*[:(\[-]\s*a\s+novel\s*[)\]]?\s*$", "", str(title or ""), flags=re.I)
    return t.strip() or str(title or "")


def clean_series(series, title=""):
    """A real series name, or '' for publisher lines ('Hard Case Crime') and books that are their own 'series'"""
    s = re.sub(r"\s*[(\[].*?[)\]]", "", str(series or "")).strip()
    s = re.sub(r"\s*(?:#|no\.?|book|vol\.?|volume)\s*\d+(?:\.\d+)?\s*$", "", s, flags=re.I)
    s = re.sub(r"\s+(?:series|saga|trilogy|sequence|chronicles collection)$", "", s, flags=re.I).strip(" ,;:-")
    n = norm(s)
    if not n or n in NOT_SERIES or n.isdigit():
        return ""
    if title and n == norm(tidy_title(title)):
        return ""
    return s


def link_key(name):
    """Book names that mean the same book in the US and UK"""
    n = norm(tidy_title(name))
    return n.replace("philosopher s stone", "sorcerer s stone")


def year_of(text):
    m = re.search(r"\b(1[5-9]\d\d|20\d\d)\b", str(text or ""))
    return int(m.group(1)) if m else None


def _strip_year(t):
    return re.sub(r"\s*[(\[]\s*(1[5-9]\d\d|20\d\d)\s*[)\]]\s*$", "", t).strip()


def clean_html(text):
    t = re.sub(r"<br\s*/?>|</p>", "\n", str(text or ""), flags=re.I)
    t = re.sub(r"<[^>]+>", "", t)
    t = html.unescape(t)
    t = re.sub(r"\(Source: [^)]*\)|\[Written by [^\]]*\]", "", t)
    return re.sub(r"\n{3,}", "\n\n", t).strip()


def _fetch(url, data=None, headers=None, timeout=20):
    h = {"User-Agent": UA, "Accept": "application/json"}
    h.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=h)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def _json(url, data=None, headers=None, timeout=20):
    return json.loads(_fetch(url, data, headers, timeout) or b"null")


def have_tool(name):
    return shutil.which(name) is not None


# ---------- Looking through the folders ----------


def section_dirs(kind):
    names = dict(SECTIONS)[kind]
    out = []
    for n in names:
        p = os.path.join(MEDIA, n)
        if os.path.isdir(p) and p not in out:
            out.append(p)
    return out


def scan():
    """Makes the list of everything in the book folders (quick: only names and sizes)"""
    titles, vols = {}, {}
    for kind, _ in SECTIONS:
        for root in section_dirs(kind):
            try:
                if kind == "book":
                    _scan_books(root, titles)
                elif kind == "audiobook":
                    _scan_audio(root, titles)
                else:
                    _scan_pages(root, kind, titles, vols)
            except OSError as e:
                log(f"Couldn't look through {root}: {e}")
    _same_names(titles)
    _drop_doubles(titles)
    _link_formats(titles)
    with _lock:
        old = _lib["titles"]
        for tid, t in titles.items():
            # Keep the first time a title was seen, so a rescan doesn't make it "new" again
            if tid in old:
                t["added"] = min(t["added"], old[tid].get("added", t["added"]))
        _lib["titles"], _lib["vols"], _lib["scanned"] = titles, vols, int(time.time())
    ready_check()


def _mtime(p):
    try:
        return int(os.path.getmtime(p))
    except OSError:
        return int(time.time())


def _scan_books(root, titles):
    for folder, dirs, files in os.walk(root):
        dirs.sort(key=natural)
        books = [f for f in files if os.path.splitext(f)[1].lower() in EBOOK and not f.startswith(".")]
        if not books:
            continue
        # One title per book name in a folder (Title.epub and Title.mobi are the same book)
        by_stem = {}
        for f in sorted(books, key=natural):
            by_stem.setdefault(os.path.splitext(f)[0].lower(), []).append(f)
        rel_folder = os.path.relpath(folder, root)
        parts = [] if rel_folder == "." else rel_folder.split(os.sep)
        opf_beside = os.path.join(folder, "metadata.opf")
        for stem, group in by_stem.items():
            best = sorted(group, key=lambda f: (0 if f.lower().endswith(".epub") else 1 if f.lower().endswith(".pdf") else 2))[0]
            path = os.path.join(folder, best)
            ext = os.path.splitext(best)[1].lower()
            info = {}
            if len(by_stem) == 1 and os.path.isfile(opf_beside):
                info = _opf_file(opf_beside)
            if not info.get("title") and ext == ".epub":
                info = {**_epub_meta(path), **{k: v for k, v in info.items() if v}}
            # Author/Title/book.epub: the folder name is the tidiest author name (the book's own is often 'King, Stephen, 1947-')
            author = clean_author(tidy_name(parts[0]) if len(parts) >= 2 else (info.get("author") or (parts[0] if parts else "")))
            title = info.get("title") or strip_author(tidy_name(os.path.splitext(best)[0]), author)
            if not info.get("title") and len(parts) >= 2:
                title = strip_author(_strip_year(tidy_name(parts[-1])), author)
            title = tidy_title(_strip_year(title) or title)
            tid = "b" + _hid("book", os.path.relpath(path, MEDIA))
            titles[tid] = {
                "id": tid,
                "kind": "book",
                "name": title,
                "author": author,
                "series": clean_series(info.get("series"), title),
                "seriesIndex": info.get("series_index"),
                "year": info.get("year") or year_of(rel_folder),
                "path": path,
                "ext": ext,
                "readable": ext in READABLE,
                "size": os.path.getsize(path),
                "added": _mtime(path),
                "desc": info.get("desc") or "",
                "subjects": info.get("subjects") or [],
                "isbn": info.get("isbn") or "",
                "publisher": info.get("publisher") or "",
            }


def _scan_audio(root, titles):
    for folder, dirs, files in os.walk(root):
        dirs.sort(key=natural)
        sounds = sorted([f for f in files if os.path.splitext(f)[1].lower() in AUDIO and not f.startswith(".")], key=natural)
        if not sounds:
            continue
        rel_folder = os.path.relpath(folder, root)
        parts = [] if rel_folder == "." else rel_folder.split(os.sep)
        # A folder with only big .m4b files and no chapter folder: each .m4b is a book
        singles = [f for f in sounds if f.lower().endswith(".m4b")]
        if singles and len(singles) == len(sounds) and len(singles) > 1:
            groups = [(os.path.splitext(f)[0], [f]) for f in singles]
        elif len(parts) <= 1 and len(sounds) == 1:
            groups = [(os.path.splitext(sounds[0])[0], sounds)]
        elif len(parts) == 0:
            groups = [(os.path.splitext(f)[0], [f]) for f in sounds]
        else:
            # Disc folders (CD1, Disc 2, Part 3) belong to the book folder above them
            if re.fullmatch(r"(?i)(cd|disc|disk|part)\s*\d+", parts[-1]) and len(parts) >= 2:
                continue
            groups = [(parts[-1], sounds)]
        for name, group in groups:
            files = [os.path.join(folder, f) for f in group]
            if len(groups) == 1 and len(parts) >= 1 and not singles:
                # Pick up disc folders under this one too
                for d in sorted(dirs, key=natural):
                    if re.fullmatch(r"(?i)(cd|disc|disk|part)\s*\d+", d):
                        sub = os.path.join(folder, d)
                        try:
                            files += [os.path.join(sub, f) for f in sorted(os.listdir(sub), key=natural) if os.path.splitext(f)[1].lower() in AUDIO]
                        except OSError:
                            pass
            author = parts[0] if len(parts) >= 2 or (len(parts) == 1 and len(groups) > 1) else ""
            if not author and len(parts) == 1 and len(groups) == 1:
                # Audiobooks/Author - Title
                m = re.match(r"(.+?)\s+-\s+(.+)", parts[0])
                if m:
                    author = m.group(1)
            title = tidy_title(strip_author(_strip_year(tidy_name(name)), author))
            # "HP07: Title" or "01 - Title" at the start
            title = re.sub(r"^(?:[A-Z]{1,4}\s?\d{1,2}\s*[:.-]\s+|\d{1,2}\s*[-.]\s+)", "", title) or title
            series, idx = "", None
            m = re.match(r"^(.*?)\s*(?:#|book|vol\.?|volume)\s*(\d+(?:\.\d)?)\s*[-:]\s*(.+)$", title, re.I)
            if m and m.group(1):
                series, idx, title = m.group(1).strip(" -,"), float(m.group(2)), m.group(3).strip()
            first = files[0]
            tid = "a" + _hid("audio", os.path.relpath(folder if len(groups) == 1 else first, MEDIA))
            titles[tid] = {
                "id": tid,
                "kind": "audiobook",
                "name": title,
                "author": clean_author(tidy_name(author)),
                "series": clean_series(series, title),
                "seriesIndex": idx,
                "year": year_of(name),
                "folder": folder,
                "files": files,
                "readable": True,
                "size": sum(os.path.getsize(f) for f in files if os.path.isfile(f)),
                "added": max(_mtime(f) for f in files),
                "desc": "",
                "subjects": [],
            }


def _vol_number(name, series=""):
    n = name.lower()
    # "One Piece - Chapter 010": the series name itself may hold numbers ("20th Century Boys"), so it's cut off first
    sn = series.lower().strip()
    if sn and n.startswith(sn):
        n = n[len(sn):]
    m = re.search(r"(?:\bch\.?|\bchapter|\bchap\.?|\bepisode|\bep\.?)\s*0*(\d+(?:\.\d+)?)", n)
    if m:
        return float(m.group(1)), "ch"
    m = re.search(r"(?:\bv|\bvol\.?|\bvolume|\btome|\bbook)\s*0*(\d+(?:\.\d+)?)", n)
    if m:
        return float(m.group(1)), "vol"
    m = re.search(r"(?:\bc|#)\s*0*(\d+(?:\.\d+)?)", n)
    if m:
        return float(m.group(1)), "ch"
    nums = re.findall(r"(?<![\d.])(\d{1,4}(?:\.\d+)?)(?![\d.])", re.sub(r"\(.*?\)|\[.*?\]", "", n))
    nums = [x for x in nums if not (len(x) == 4 and 1900 < float(x) < 2100)]
    return (float(nums[-1]), "vol") if nums else (None, "vol")


def _series_json(sdir):
    """What Harbor wrote about a manga series (series.json in its folder). Every part is optional."""
    j = _read(os.path.join(sdir, "series.json"), None)
    if not isinstance(j, dict):
        return {}, {"file": {}, "num": {}}

    def num(*keys):
        for k in keys:
            try:
                v = int(float(j.get(k)))
                if v > 0:
                    return v
            except (TypeError, ValueError):
                pass
        return None

    def text(*keys):
        for k in keys:
            v = j.get(k)
            if isinstance(v, list):
                v = ", ".join(str(x) for x in v[:3] if isinstance(x, (str, int, float)))
            if isinstance(v, (str, int, float)) and str(v).strip():
                return str(v).strip()
        return ""

    info = {
        "name": text("title", "name")[:200],
        "author": text("author", "authors")[:120],
        "desc": clean_html(text("story", "description", "summary"))[:6000],
        "status": text("status")[:40],
        "anilist": num("anilist", "anilist_id", "anilistId"),
        "mal": num("mal", "mal_id", "malId"),
        "year": num("year"),
        "genres": [str(g)[:40] for g in (j.get("genres") or []) if isinstance(g, str)][:8] if isinstance(j.get("genres"), list) else [],
        "adult": bool(j.get("adult")),
        "webtoon": bool(j.get("webtoon")),
        "total": num("total_chapters", "totalChapters"),
    }
    by_file, by_num = {}, {}
    chapters = j.get("chapters")
    for c in chapters if isinstance(chapters, list) else []:
        if not isinstance(c, dict):
            continue
        entry = {
            "title": str(c.get("title") or "").strip()[:160],
            "source": str(c.get("source") or "").strip()[:60],
            "official": bool(c.get("official")),
        }
        f = c.get("file") or c.get("name")
        if isinstance(f, str) and f:
            by_file[os.path.basename(f).lower()] = entry
        try:
            by_num[float(c.get("number", c.get("num")))] = entry
        except (TypeError, ValueError):
            pass
    return info, {"file": by_file, "num": by_num}


def _vol_label(num, unit, i):
    if num is None:
        return ("Volume " if unit == "vol" else "Chapter ") + str(i + 1)
    return ("Volume " if unit == "vol" else "Chapter ") + f"{num:g}"


def _scan_pages(root, kind, titles, vols):
    try:
        entries = sorted(os.listdir(root), key=natural)
    except OSError:
        return
    for series_name in entries:
        sdir = os.path.join(root, series_name)
        if series_name.startswith((".", "_")) or series_name.lower() in MANGA_SKIP:
            continue
        items = []
        info, about = {}, {"file": {}, "num": {}}
        if os.path.isfile(sdir) and os.path.splitext(series_name)[1].lower() in PAGES:
            # A single file straight in Manga/ is a one-shot
            items = [(series_name, sdir)]
            series_title = tidy_name(os.path.splitext(series_name)[0])
        elif os.path.isdir(sdir):
            series_title = tidy_name(series_name)
            info, about = _series_json(sdir)
            for folder, dirs, files in os.walk(sdir):
                # Half-downloaded and hidden things (names starting with "." or "_") are left alone
                dirs[:] = sorted((d for d in dirs if not d.startswith((".", "_"))), key=natural)
                files = [f for f in files if not f.startswith((".", "_"))]
                pics = [f for f in files if os.path.splitext(f)[1].lower() in PICS]
                docs = [f for f in files if os.path.splitext(f)[1].lower() in PAGES]
                for f in sorted(docs, key=natural):
                    items.append((f, os.path.join(folder, f)))
                # A folder of pictures is one volume or chapter (cover.jpg beside the chapter files isn't)
                pages = [f for f in pics if os.path.splitext(f)[0].lower() not in ("cover", "folder", "poster")]
                if pages and not docs and folder != sdir:
                    items.append((os.path.basename(folder), folder))
                elif pages and not docs and folder == sdir and not dirs:
                    items.append((series_name, folder))
        else:
            continue
        if not items:
            continue
        series_title = info.get("name") or _strip_year(series_title) or series_title
        sid = ("m" if kind == "manga" else "c") + _hid(kind, os.path.relpath(sdir, MEDIA))
        vol_list = []
        folder_title = tidy_name(os.path.splitext(series_name)[0]) if os.path.isfile(sdir) else tidy_name(series_name)
        for i, (name, path) in enumerate(items):
            is_file = os.path.isfile(path)
            stem = os.path.splitext(name)[0] if is_file else name
            num, unit = _vol_number(stem, folder_title)
            if num is None and len(items) == 1:
                unit = "one"
            extra = about["file"].get(name.lower()) or (about["num"].get(num) if num is not None and unit == "ch" else None) or {}
            vid = "v" + _hid("vol", os.path.relpath(path, MEDIA))
            vols[vid] = {
                "id": vid,
                "title": sid,
                "path": path,
                "name": "Read" if unit == "one" else _vol_label(num, unit, i),
                "chapterName": extra.get("title", ""),
                "source": extra.get("source", ""),
                "official": bool(extra.get("official")),
                "num": num if num is not None else float(i + 1),
                "unit": unit,
                "ext": os.path.splitext(path)[1].lower() if is_file else "",
                "added": _mtime(path),
                "size": os.path.getsize(path) if is_file else 0,
            }
            vol_list.append(vid)
        vol_list.sort(key=lambda v: (vols[v]["unit"] != "vol", vols[v]["num"], natural(os.path.basename(vols[v]["path"]))))
        chapters = sum(1 for v in vol_list if vols[v]["unit"] == "ch")
        titles[sid] = {
            "id": sid,
            "kind": kind,
            "name": series_title,
            "author": info.get("author", ""),
            "series": series_title,
            "seriesIndex": None,
            "year": info.get("year") or year_of(series_name),
            "folder": sdir,
            "vols": vol_list,
            "unit": "ch" if chapters * 2 >= len(vol_list) else "vol",
            "readable": True,
            "size": sum(vols[v]["size"] for v in vol_list),
            "added": max(vols[v]["added"] for v in vol_list),
            "desc": info.get("desc", ""),
            "subjects": info.get("genres") or [],
            "rtl": kind == "manga" and not info.get("webtoon"),
            "webtoon": bool(info.get("webtoon")),
            "pubStatus": info.get("status", ""),
            "anilist": info.get("anilist"),
            "mal": info.get("mal"),
            "adult": bool(info.get("adult")),
            "total": info.get("total"),
        }


def _same_names(titles):
    """One spelling per author ('Rowling, J. K' and 'J.K. Rowling' both show as 'J.K. Rowling')"""
    spellings = {}
    for t in titles.values():
        if t["author"]:
            spellings.setdefault(author_key(t["author"]), []).append(t["author"])
    best = {}
    for k, names in spellings.items():
        # The most used spelling wins, then the one with dots in initials ('J.K.' over 'JK')
        best[k] = sorted(set(names), key=lambda n: (-names.count(n), -n.count("."), n))[0]
    for t in titles.values():
        if t["author"]:
            t["author"] = best[author_key(t["author"])]


def _drop_doubles(titles):
    """The same book twice (two downloads, or Title.epub in two folders) only shows once"""
    groups = {}
    for t in titles.values():
        if t["kind"] in ("book", "audiobook"):
            groups.setdefault((t["kind"], link_key(t["name"]), author_key(t["author"])), []).append(t)
    rank = {".epub": 0, ".pdf": 1}
    for group in groups.values():
        if len(group) < 2:
            continue
        # Keep the one Benflix can open best, then the one with series info, then the first one added
        group.sort(key=lambda t: (rank.get(t.get("ext"), 2) if t["kind"] == "book" else -len(t.get("files") or []), 0 if t.get("series") else 1, t["added"], t["id"]))
        keep = group[0]
        for t in group[1:]:
            if not keep.get("series") and t.get("series"):
                keep["series"], keep["seriesIndex"] = t["series"], t.get("seriesIndex")
            _doubles[t["id"]] = keep["id"]
            del titles[t["id"]]


def _link_formats(titles):
    """An ebook and an audiobook of the same book find each other (Read and Listen on one page)"""
    books = {}
    for t in titles.values():
        if t["kind"] == "book":
            books.setdefault(link_key(t["name"]), []).append(t)
    for t in titles.values():
        if t["kind"] != "audiobook":
            continue
        n = link_key(t["name"])
        match = books.get(n)
        if not match:
            # "Title: A Novel" vs "Title" match
            for k, v in books.items():
                if k and n and (k.startswith(n + " ") or n.startswith(k + " ")) and len(min(k, n, key=len)) > 6:
                    match = v
                    break
        if match:
            ak = author_key(t["author"])
            b = sorted(match, key=lambda x: 0 if author_key(x["author"]) == ak else 1)[0]
            bk = author_key(b["author"])
            if not ak or not bk or ak == bk or ak in bk or bk in ak:
                t["linked"] = b["id"]
                b["linked"] = t["id"]
                if not t["author"]:
                    t["author"] = b["author"]
                if not t["series"] and b["series"]:
                    t["series"], t["seriesIndex"] = b["series"], b["seriesIndex"]
                elif not b["series"] and t["series"]:
                    b["series"], b["seriesIndex"] = t["series"], t["seriesIndex"]
                if t.get("seriesIndex") is None and b.get("seriesIndex") is not None and norm(t["series"]) == norm(b["series"]):
                    t["seriesIndex"] = b["seriesIndex"]
                if b.get("seriesIndex") is None and t.get("seriesIndex") is not None and norm(t["series"]) == norm(b["series"]):
                    b["seriesIndex"] = t["seriesIndex"]


# ---------- Reading inside files ----------


def _xml(data):
    try:
        return ET.fromstring(data)
    except ET.ParseError:
        # Some books have HTML entities XML doesn't know (like &nbsp;)
        text = data.decode("utf-8", "replace") if isinstance(data, bytes) else data
        text = re.sub(r"&(?!amp;|lt;|gt;|quot;|apos;|#)\w+;", " ", text)
        return ET.fromstring(text.encode("utf-8"))


def _local(tag):
    return tag.rsplit("}", 1)[-1] if isinstance(tag, str) else ""


def _opf_info(root):
    info = {"subjects": []}
    meta = None
    for el in root.iter():
        if _local(el.tag) == "metadata":
            meta = el
            break
    if meta is None:
        return info
    for el in meta.iter():
        tag = _local(el.tag)
        text = (el.text or "").strip()
        if tag == "title" and text and "title" not in info:
            info["title"] = text
        elif tag == "creator" and text and "author" not in info:
            role = next((v for k, v in el.attrib.items() if _local(k) == "role"), "aut")
            if role in ("aut", ""):
                info["author"] = text
        elif tag == "description" and text:
            info["desc"] = clean_html(text)
        elif tag == "subject" and text:
            info["subjects"].append(text)
        elif tag == "date" and text and "year" not in info:
            info["year"] = year_of(text)
        elif tag == "publisher" and text:
            info["publisher"] = text
        elif tag == "identifier" and text:
            digits = re.sub(r"[^0-9Xx]", "", text)
            if len(digits) in (10, 13) and "isbn" not in info:
                info["isbn"] = digits
        elif tag == "meta":
            name = el.attrib.get("name", "")
            prop = el.attrib.get("property", "")
            if name == "calibre:series" or prop == "belongs-to-collection":
                info["series"] = el.attrib.get("content", text)
            elif name == "calibre:series_index" or prop == "group-position":
                try:
                    info["series_index"] = float(el.attrib.get("content", text))
                except ValueError:
                    pass
            elif name == "cover":
                info["cover_id"] = el.attrib.get("content", "")
    if "author" not in info:
        for el in meta.iter():
            if _local(el.tag) == "creator" and (el.text or "").strip():
                info["author"] = el.text.strip()
                break
    return info


def _opf_file(path):
    try:
        with open(path, "rb") as f:
            return _opf_info(_xml(f.read()))
    except (OSError, ET.ParseError):
        return {}


def _epub_opf_path(z):
    try:
        root = _xml(z.read("META-INF/container.xml"))
        for el in root.iter():
            if _local(el.tag) == "rootfile" and el.attrib.get("full-path"):
                return el.attrib["full-path"]
    except (KeyError, ET.ParseError):
        pass
    for n in z.namelist():
        if n.lower().endswith(".opf"):
            return n
    return None


def _epub_meta(path):
    try:
        with zipfile.ZipFile(path) as z:
            opf = _epub_opf_path(z)
            return _opf_info(_xml(z.read(opf))) if opf else {}
    except (OSError, zipfile.BadZipFile, KeyError, ET.ParseError):
        return {}


def _join(base, href):
    href = urllib.parse.unquote(href.split("#")[0])
    p = os.path.normpath(os.path.join(os.path.dirname(base), href)).replace(os.sep, "/")
    return p.lstrip("./") if p.startswith("./") else p


def epub_book(path):
    """The reading order, chapter names and cover of an EPUB"""
    with zipfile.ZipFile(path) as z:
        opf = _epub_opf_path(z)
        if not opf:
            raise BooksError(422, "This book file is damaged.")
        root = _xml(z.read(opf))
        manifest, spine, nav, ncx, cover = {}, [], None, None, None
        info = _opf_info(root)
        for el in root.iter():
            tag = _local(el.tag)
            if tag == "item":
                href = _join(opf, el.attrib.get("href", ""))
                manifest[el.attrib.get("id", "")] = (href, el.attrib.get("media-type", ""), el.attrib.get("properties", ""))
                if "nav" in el.attrib.get("properties", "").split():
                    nav = href
                if "cover-image" in el.attrib.get("properties", "").split():
                    cover = href
            elif tag == "spine":
                tocid = el.attrib.get("toc")
                if tocid:
                    ncx = tocid
            elif tag == "itemref" and el.attrib.get("linear", "yes") != "no":
                spine.append(el.attrib.get("idref", ""))
        if ncx in manifest:
            ncx = manifest[ncx][0]
        else:
            ncx = next((h for h, t, _ in manifest.values() if t == "application/x-dtbncx+xml"), None)
        if not cover and info.get("cover_id") in manifest:
            cover = manifest[info["cover_id"]][0]
        names = set(z.namelist())
        sizes = {i.filename: i.file_size for i in z.infolist()}
        items = []
        for idref in spine:
            if idref in manifest:
                href, mtype, _ = manifest[idref]
                if href in names:
                    items.append({"href": href, "size": sizes.get(href, 0)})
        toc = []
        try:
            if nav and nav in names:
                doc = _xml(z.read(nav))
                for n in doc.iter():
                    if _local(n.tag) == "nav" and any(_local(k) == "type" and "toc" in v for k, v in n.attrib.items()):
                        for a in n.iter():
                            if _local(a.tag) == "a" and a.attrib.get("href"):
                                toc.append({"title": " ".join("".join(a.itertext()).split()), "href": _join(nav, a.attrib["href"]), "frag": a.attrib["href"].partition("#")[2]})
                        break
            if not toc and ncx and ncx in names:
                doc = _xml(z.read(ncx))
                for p in doc.iter():
                    if _local(p.tag) == "navPoint":
                        label = next((" ".join("".join(t.itertext()).split()) for t in p.iter() if _local(t.tag) == "text"), "")
                        src = next((c.attrib.get("src", "") for c in p if _local(c.tag) == "content"), "")
                        if src:
                            toc.append({"title": label, "href": _join(ncx, src), "frag": src.partition("#")[2]})
        except ET.ParseError:
            pass
        hrefs = [i["href"] for i in items]
        chapters = []
        for t in toc:
            if t["href"] in hrefs and t["title"]:
                chapters.append({"title": t["title"], "index": hrefs.index(t["href"]), "frag": t["frag"]})
        return {"spine": items, "chapters": chapters, "cover": cover, "info": info, "names": names}


_epub_cache = {}


def epub_cached(path):
    st = _mtime(path)
    hit = _epub_cache.get(path)
    if hit and hit[0] == st:
        return hit[1]
    book = epub_book(path)
    _epub_cache[path] = (st, book)
    if len(_epub_cache) > 30:
        _epub_cache.pop(next(iter(_epub_cache)))
    return book


# ---------- Pages of manga and comics ----------


def _page_names(names):
    pics = [n for n in names if os.path.splitext(n)[1].lower() in PICS and not os.path.basename(n).startswith(".") and "__MACOSX" not in n]
    return sorted(pics, key=natural)


def _rar_folder(vol):
    """.cbr and .cb7 files are unpacked once (with 7z) into a folder of pictures"""
    out = os.path.join(_dir or "/tmp", "pages", vol["id"])
    stamp = os.path.join(out, ".done")
    if os.path.isfile(stamp) and _read(stamp, {}).get("mtime") == vol["added"]:
        return out
    if not have_tool("7z"):
        raise BooksError(422, "This volume is a .cbr file, and Tower can't open those yet. Run the Glowfin setup command again.")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out, exist_ok=True)
    r = subprocess.run(["7z", "x", "-y", "-o" + out, vol["path"]], capture_output=True, timeout=300)
    if r.returncode != 0:
        raise BooksError(422, "Tower couldn't open this volume. The file may be damaged.")
    with open(stamp, "w") as f:
        json.dump({"mtime": vol["added"]}, f)
    return out


def vol_pages(vol):
    """The page pictures of a volume, in order: [(where, name)]"""
    path, ext = vol["path"], vol["ext"]
    if os.path.isdir(path):
        names = []
        for folder, _, files in os.walk(path):
            names += [os.path.relpath(os.path.join(folder, f), path) for f in files]
        return "dir", path, _page_names(names)
    if ext in (".cbz", ".zip", ".epub"):
        try:
            with zipfile.ZipFile(path) as z:
                return "zip", path, _page_names(z.namelist())
        except zipfile.BadZipFile:
            # Some .cbz files are really .cbr inside
            folder = _rar_folder(vol)
            return "dir", folder, _page_names([os.path.relpath(os.path.join(a, f), folder) for a, _, fs in os.walk(folder) for f in fs])
    if ext in (".cbr", ".rar", ".cb7", ".7z"):
        folder = _rar_folder(vol)
        return "dir", folder, _page_names([os.path.relpath(os.path.join(a, f), folder) for a, _, fs in os.walk(folder) for f in fs])
    if ext == ".pdf":
        return "pdf", path, []
    return "none", path, []


_pages_cache = {}


def pages_of(vol_id):
    vol = _lib["vols"].get(vol_id)
    if not vol:
        raise BooksError(404, "That volume isn't on Tower any more.")
    hit = _pages_cache.get(vol_id)
    if hit and hit[0] == vol["added"]:
        return vol, hit[1]
    found = vol_pages(vol)
    _pages_cache[vol_id] = (vol["added"], found)
    if len(_pages_cache) > 60:
        _pages_cache.pop(next(iter(_pages_cache)))
    return vol, found


def page_bytes(vol_id, n):
    vol, (how, where, names) = pages_of(vol_id)
    if not 0 <= n < len(names):
        raise BooksError(404, "No such page.")
    name = names[n]
    ctype = TYPES.get(os.path.splitext(name)[1].lower(), "image/jpeg")
    if how == "zip":
        with zipfile.ZipFile(where) as z:
            return z.read(name), ctype
    with open(os.path.join(where, name), "rb") as f:
        return f.read(), ctype


# ---------- Sound files ----------


def probe(path):
    """(seconds, tags, chapters) for a sound file, worked out once with ffprobe"""
    st = _mtime(path)
    hit = _probe.get(path)
    if hit and hit[0] == st:
        return hit[1], hit[2], hit[3]
    secs, tags, chapters = 0.0, {}, []
    if have_tool("ffprobe"):
        try:
            r = subprocess.run(
                ["ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", "-show_chapters", path],
                capture_output=True, timeout=60,
            )
            j = json.loads(r.stdout or b"{}")
            fmt = j.get("format") or {}
            secs = float(fmt.get("duration") or 0)
            tags = {k.lower(): v for k, v in (fmt.get("tags") or {}).items() if isinstance(v, str) and len(v) < 300}
            for c in j.get("chapters") or []:
                chapters.append({"title": (c.get("tags") or {}).get("title") or "", "start": float(c.get("start_time") or 0), "end": float(c.get("end_time") or 0)})
        except (OSError, ValueError, subprocess.TimeoutExpired):
            pass
    with _lock:
        _probe[path] = (st, secs, tags, chapters)
    return secs, tags, chapters


def chapter_name(file_name, tags, book_title, i):
    t = (tags.get("title") or "").strip()
    if t and norm(t) != norm(book_title) and not re.fullmatch(r"(?i)track\s*\d+", t):
        return re.sub(r"^\d{1,3}\s*[-.:]\s*", "", t)
    n = os.path.splitext(os.path.basename(file_name))[0]
    n = re.sub(r"^(?:[A-Z]{1,4}\s?\d{1,2}\s*[:.-]\s*)", "", n)
    n = n.replace(book_title, "").strip(" -_.")
    n = re.sub(r"^\d{1,3}\s*[-._]\s*", "", n).strip(" -_.")
    if not n or n.isdigit():
        return f"Chapter {i + 1}"
    if re.fullmatch(r"(?i)(chapter|ch|part|track)\s*\d+", n):
        return re.sub(r"(?i)^(ch|track)\b", "Chapter", n).title()
    return n


def audio_book(t):
    """Chapters (each with the file it's in and where it starts), total length, narrator, missing chapters"""
    chapters, total, narrator, tracks = [], 0.0, "", []
    for i, f in enumerate(t["files"]):
        secs, tags, inner = probe(f)
        if not narrator:
            for key in ("narrator", "composer", "artist", "album_artist"):
                v = (tags.get(key) or "").strip()
                # Downloaded audiobooks often put the narrator in "artist"; the author comes from the folder
                if v and norm(v) != norm(t["author"]) and (key in ("narrator", "composer") or t["author"]):
                    narrator = re.sub(r"(?i)^(read|narrated) by\s+", "", v)
                    break
        tr = (tags.get("track") or "").split("/")
        if len(tr) == 2 and tr[1].strip().isdigit():
            tracks.append(int(tr[1]))
        if len(inner) > 1 and len(t["files"]) == 1:
            for j, c in enumerate(inner):
                chapters.append({"title": c["title"] or f"Chapter {j + 1}", "file": i, "start": c["start"], "length": max(0.0, c["end"] - c["start"]), "at": total + c["start"]})
        else:
            chapters.append({"title": chapter_name(f, tags, t["name"], i), "file": i, "start": 0.0, "length": secs, "at": total})
        total += secs
    missing = 0
    if tracks and max(tracks) > len(t["files"]):
        missing = max(tracks) - len(t["files"])
    else:
        nums = [int(m.group(1)) for m in (re.search(r"(?:^|\D)(\d{1,3})(?:\D|$)", os.path.basename(f)) for f in t["files"]) if m]
        if len(nums) == len(t["files"]) and nums and len(set(nums)) == len(nums):
            gaps = max(nums) - min(nums) + 1 - len(nums)
            if 0 < gaps < 40:
                missing = gaps
    return {"chapters": chapters, "seconds": total, "narrator": narrator, "missing": missing, "files": [os.path.basename(f) for f in t["files"]]}


# ---------- Looking things up (Open Library) ----------


def meta_key(t):
    if t["kind"] in ("manga", "comic"):
        return t["kind"] + ":" + norm(t["name"]) + (f"#{t['anilist']}" if t.get("anilist") else "")
    return "book:" + norm(t["name"]) + "|" + norm(t["author"])


def look_up_some(limit=6):
    """Looks up a few titles that haven't been looked up yet (a few at a time, to be kind to the free sites)"""
    with _lock:
        todo = [t for t in _lib["titles"].values() if meta_key(t) not in _meta]
    done = 0
    for t in sorted(todo, key=lambda x: -x["added"]):
        if done >= limit:
            break
        key = meta_key(t)
        try:
            info = look_up_manga(t["name"], t.get("anilist")) if t["kind"] in ("manga", "comic") else look_up_book(t["name"], t["author"], t.get("isbn"))
        except Exception as e:
            log(f"Couldn't look up {t['name']}: {e!r}")
            info = {"failed": int(time.time())}
        with _lock:
            _meta[key] = info or {"none": True}
        done += 1
        time.sleep(1.2)
    if done:
        _save_meta()
    # Things that failed (no internet) are tried again a day later
    with _lock:
        for k in [k for k, v in _meta.items() if v.get("failed") and time.time() - v["failed"] > 86400]:
            del _meta[k]


def look_up_book(title, author, isbn=None):
    q = {"title": title, "limit": "5", "fields": "key,title,author_name,first_publish_year,cover_i,subject,ratings_average,ratings_count,number_of_pages_median,cover_edition_key,isbn,publisher"}
    if author:
        q["author"] = author
    docs = (_json("https://openlibrary.org/search.json?" + urllib.parse.urlencode(q)) or {}).get("docs") or []
    if not docs and author:
        q.pop("author")
        docs = (_json("https://openlibrary.org/search.json?" + urllib.parse.urlencode(q)) or {}).get("docs") or []
    if not docs:
        return {"none": True}
    best = sorted(docs, key=lambda d: (norm(d.get("title")) != norm(title), -(d.get("ratings_count") or 0)))[0]
    info = {
        "ol": best.get("key"),
        "year": best.get("first_publish_year"),
        "cover": f"https://covers.openlibrary.org/b/id/{best['cover_i']}-L.jpg" if best.get("cover_i") else None,
        "genres": _book_genres(best.get("subject") or []),
        "subjects": [s for s in (best.get("subject") or [])[:40]],
        "score": round(best["ratings_average"], 1) if best.get("ratings_average") else None,
        "votes": best.get("ratings_count") or 0,
        "pages": best.get("number_of_pages_median"),
        "author": (best.get("author_name") or [""])[0],
    }
    subj = " ".join(info["subjects"]).lower()
    info["kids"] = any(w in subj for w in KIDS_WORDS) and not any(w in subj for w in ADULT_WORDS)
    try:
        work = _json("https://openlibrary.org" + best["key"] + ".json")
        d = work.get("description")
        info["desc"] = clean_html(d.get("value") if isinstance(d, dict) else d or "")
        series = [s for s in (work.get("subjects") or []) if s.lower().startswith("series:")]
        if series:
            info["series"] = series[0].split(":", 1)[1].replace("_", " ").strip()
    except Exception:
        pass
    return info


GENRE_WORDS = [
    ("Fantasy", ("fantasy", "magic", "wizards", "dragons")),
    ("Science fiction", ("science fiction", "sci-fi", "space")),
    ("Mystery", ("mystery", "detective", "crime")),
    ("Thriller", ("thriller", "suspense")),
    ("Romance", ("romance", "love stories")),
    ("Horror", ("horror", "ghost")),
    ("History", ("history", "historical")),
    ("Biography", ("biography", "autobiography", "memoir")),
    ("Young adult", ("young adult",)),
    ("Kids", ("juvenile", "children")),
    ("Adventure", ("adventure",)),
    ("Humor", ("humor", "humour", "comedy")),
    ("Self-help", ("self-help", "self help", "personal development")),
    ("Business", ("business", "economics", "finance")),
    ("Science", ("science", "physics", "biology")),
    ("Classics", ("classic",)),
]


def _book_genres(subjects):
    s = " | ".join(subjects).lower()
    out = [g for g, words in GENRE_WORDS if any(w in s for w in words)]
    if out and "Kids" in out and len(out) > 1:
        out.remove("Kids")
        out.append("Kids")
    return out[:4]


ANILIST_FIELDS = """id idMal title { romaji english } description(asHtml: false) genres tags { name rank isAdult } averageScore
  volumes chapters status startDate { year } isAdult format countryOfOrigin
  coverImage { extraLarge large } staff(perPage: 3) { edges { role node { name { full } } } }"""
ANILIST_Q = "query ($q: String) { Page(perPage: 5) { media(search: $q, type: MANGA, sort: SEARCH_MATCH) { " + ANILIST_FIELDS + " } } }"
ANILIST_ID_Q = "query ($id: Int) { Media(id: $id, type: MANGA) { " + ANILIST_FIELDS + " } }"
ANILIST_STATUS = {"FINISHED": "Finished", "RELEASING": "Still coming out", "NOT_YET_RELEASED": "Not out yet", "CANCELLED": "Cancelled", "HIATUS": "On a break"}


def look_up_manga(name, anilist_id=None):
    """A manga series on AniList (by Harbor's AniList number when series.json has one, else by name), plus its MyAnimeList score"""
    best = None
    if anilist_id:
        body = json.dumps({"query": ANILIST_ID_Q, "variables": {"id": int(anilist_id)}}).encode()
        j = _json("https://graphql.anilist.co", data=body, headers={"Content-Type": "application/json"})
        best = ((j or {}).get("data") or {}).get("Media")
    if not best:
        body = json.dumps({"query": ANILIST_Q, "variables": {"q": name}}).encode()
        j = _json("https://graphql.anilist.co", data=body, headers={"Content-Type": "application/json"})
        media = ((j or {}).get("data") or {}).get("Page", {}).get("media") or []
        media = [m for m in media if m.get("format") != "NOVEL"] or media
        if not media:
            return {"none": True}
        n = norm(name)
        best = sorted(media, key=lambda m: (norm((m.get("title") or {}).get("english")) != n and norm((m.get("title") or {}).get("romaji")) != n))[0]
    author = ""
    for e in ((best.get("staff") or {}).get("edges") or []):
        if "story" in (e.get("role") or "").lower() or not author:
            author = ((e.get("node") or {}).get("name") or {}).get("full") or author
    info = {
        "anilist": best.get("id"),
        "mal": best.get("idMal"),
        "desc": clean_html(best.get("description")),
        "genres": (best.get("genres") or [])[:4],
        "subjects": (best.get("genres") or []) + [t["name"] for t in best.get("tags") or [] if (t.get("rank") or 0) >= 60][:12],
        "anilistScore": round(best["averageScore"] / 10, 1) if best.get("averageScore") else None,
        "year": (best.get("startDate") or {}).get("year"),
        "cover": (best.get("coverImage") or {}).get("extraLarge") or (best.get("coverImage") or {}).get("large"),
        "totalVols": best.get("volumes"),
        "totalChapters": best.get("chapters"),
        "status": ANILIST_STATUS.get(best.get("status") or "", ""),
        "author": author,
        "adult": bool(best.get("isAdult")),
        "origin": best.get("countryOfOrigin"),
        "english": (best.get("title") or {}).get("english"),
    }
    info["kids"] = False  # manga never shows on Kids (Ben 2026-11-21)
    if info["mal"]:
        try:
            time.sleep(0.5)
            m = _json(f"https://api.jikan.moe/v4/manga/{info['mal']}")
            info["malScore"] = (m.get("data") or {}).get("score")
        except Exception:
            pass
    return info


def meta_of(t):
    with _lock:
        return _meta.get(meta_key(t)) or {}


# ---------- What each title looks like to the apps ----------


def _is_webtoon(t):
    """Read top to bottom in one long strip (Korean and Chinese series), not right to left page by page"""
    return bool(t.get("webtoon")) or (t.get("kind") == "manga" and meta_of(t).get("origin") in ("KR", "CN"))


def kids_ok(t):
    # Ben 2026-11-21: no manga in Kids at all (not even through the Kids allow list)
    if t["kind"] in ("manga", "comic"):
        return False
    m = meta_of(t)
    if t["id"] in _state.get("kids_allow", []):
        return True
    if t.get("linked") and t["linked"] in _state.get("kids_allow", []):
        return True
    return bool(m.get("kids")) and not m.get("adult")


def visible(user_is_kids, t):
    return kids_ok(t) if user_is_kids else True


def cover_url(tid, w=320):
    return f"/api/books/cover/{tid}?w={w}"


def _status_of(t, u):
    """not | reading | done, plus how far (0 to 1) and the words under the cover"""
    tid = t["id"]
    if tid in u["finished"]:
        return "done", 1.0
    if t["kind"] in ("manga", "comic"):
        vols = t.get("vols") or []
        read = [v for v in vols if v in u["done"]]
        if vols and len(read) == len(vols):
            return "done", 1.0
        p = u["place"].get(tid)
        if read or p:
            frac = (len(read) + ((p or {}).get("pct") or 0) * (0 if (p or {}).get("vol") in read else 1)) / max(1, len(vols))
            return "reading", min(0.999, frac)
        return "not", 0.0
    p = u["place"].get(tid)
    if p and (p.get("pct") or 0) > 0.0:
        return "reading", min(0.999, p.get("pct") or 0)
    return "not", 0.0


def _vol_line(t, u):
    vols = t.get("vols") or []
    if not vols:
        return ""
    p = u["place"].get(t["id"]) or {}
    cur = p.get("vol")
    if cur in vols and cur not in u["done"]:
        at = vols.index(cur)
    else:
        nxt = next((i for i, v in enumerate(vols) if v not in u["done"]), None)
        at = nxt if nxt is not None else len(vols) - 1
    left = sum(1 for v in vols[at:] if v not in u["done"])
    return f"{_lib['vols'][vols[at]]['name']} · {left} left" if left > 1 else _lib["vols"][vols[at]]["name"]


def _count_words(t):
    """'164 chapters' / '12 volumes'"""
    n = len(t.get("vols") or [])
    word = "chapter" if t.get("unit") == "ch" else "volume"
    return f"{n} {word}" + ("" if n == 1 else "s")


def tile(t, u, now=None):
    now = now or time.time()
    m = meta_of(t)
    status, frac = _status_of(t, u)
    tid = t["id"]
    opened = u["opened"].get(tid, 0)
    new_vols = 0
    if t["kind"] in ("manga", "comic"):
        new_vols = sum(1 for v in t["vols"] if _lib["vols"][v]["added"] > max(opened, now - NEW_DAYS * 86400))
    is_new = (now - t["added"] < NEW_DAYS * 86400) and t["added"] > opened
    line = ""
    if t["kind"] in ("manga", "comic"):
        line = "Finished" if status == "done" else _vol_line(t, u) if status != "not" else _count_words(t)
    elif status == "reading":
        line = f"{round(frac * 100)}%"
    elif status == "done":
        line = "Finished"
    else:
        line = t["author"]
    return {
        "id": tid,
        "kind": t["kind"],
        "name": t["name"],
        "author": t["author"] or (m.get("author") or "" if t["kind"] in ("manga", "comic") else clean_author(m.get("author"))),
        "series": "" if t["kind"] in ("manga", "comic") else t.get("series") or clean_series(m.get("series"), t["name"]),
        "seriesIndex": t.get("seriesIndex"),
        "year": t.get("year") or m.get("year"),
        "cover": cover_url(tid),
        "added": t["added"],
        "status": status,
        "progress": round(frac, 4),
        "line": line,
        "new": is_new,
        "newCount": new_vols if (opened or status != "not") else 0,
        "want": tid in u["want"],
        "linked": t.get("linked"),
        "readable": t.get("readable", True),
        "genres": m.get("genres") or [],
        "lastAt": (u["place"].get(tid) or {}).get("at", 0),
        "vols": len(t.get("vols") or []),
        "unit": t.get("unit") or "",
        "kids": kids_ok(t),
    }


def library(user_id, is_kids):
    u = _user(user_id)
    now = time.time()
    with _lock:
        titles = list(_lib["titles"].values())
    tiles = _fill_series([tile(t, u, now) for t in titles if visible(is_kids, t)])
    tiles.sort(key=lambda x: natural(x["name"]))
    kinds = {k: sum(1 for x in tiles if x["kind"] == k) for k, _ in SECTIONS}
    # Want to read also holds books you asked for that aren't on Tower yet (shown dimmed, "Not on Glowfin yet")
    asked = []
    for key, w in sorted(u["want"].items(), key=lambda kv: -(kv[1].get("at") or 0)):
        if key.startswith("ol:"):
            asked.append({"id": key, "kind": w.get("kind") or "book", "name": w.get("name") or "", "author": w.get("author") or "", "cover": w.get("cover"), "notHere": True, "status": "not", "progress": 0, "line": w.get("author") or "", "want": True, "added": w.get("at") or 0})
    return {
        "titles": tiles,
        "asked": asked,
        "counts": kinds,
        "scanned": _lib["scanned"],
        "folders": {k: [os.path.relpath(p, MEDIA) for p in section_dirs(k)] for k, _ in SECTIONS},
        "requests": can_ask(),
    }


def _fill_series(tiles):
    """'Harry Potter and the Goblet of Fire' with no series joins the author's 'Harry Potter' series"""
    known = {}
    for t in tiles:
        if t["series"] and t["kind"] in ("book", "audiobook"):
            known.setdefault(author_key(t["author"]), {})[norm(t["series"])] = t["series"]
    for t in tiles:
        if t["series"] or t["kind"] not in ("book", "audiobook"):
            continue
        n = norm(t["name"])
        for key, name in (known.get(author_key(t["author"])) or {}).items():
            if key and n.startswith(key + " ") and len(key) > 3:
                t["series"] = name
                break
    return tiles


def series_order(books):
    """Book order in a series: by number when every book has one, otherwise by year it came out"""
    numbered = all(x.get("seriesIndex") is not None for x in books)
    return sorted(
        books,
        key=lambda x: (x["seriesIndex"] if numbered else (x.get("year") or 9999), x.get("seriesIndex") or 0, natural(x["name"])),
    )


def _one_per_book(tiles):
    """A linked ebook + audiobook only shows once in a row (the first one, like the one used last)"""
    seen, out = set(), []
    for t in tiles:
        pair = tuple(sorted([t["id"], t.get("linked") or t["id"]]))
        if pair in seen:
            continue
        seen.add(pair)
        out.append(t)
    return out


HOME_GENRE_NAMES = {"Kids": "For kids", "Biography": "Biography & memoir", "Sci-Fi": "Science fiction"}


def _home_extra_rows(tiles, user_id, is_kids):
    """More rows for Books Home from the books themselves: a few genres (a different few each day), the best
    rated, and quick reads. A row needs at least 4 books so it never looks half empty."""
    with _lock:
        titles = dict(_lib["titles"])
    books = [t for t in tiles.values() if t["kind"] in ("book", "audiobook")]
    # Best rated (Open Library readers' scores, with enough votes to trust)
    rated = []
    for t in books:
        m = meta_of(titles[t["id"]]) if t["id"] in titles else {}
        if (m.get("score") or 0) >= 4.0 and (m.get("votes") or 0) >= 10:
            rated.append((m["score"], m.get("votes") or 0, t))
    rated.sort(key=lambda x: (-x[0], -x[1]))
    top_rated = _one_per_book([dict(t, line=f"★ {sc} · {t['author']}".strip(" ·") if t["status"] == "not" else t["line"]) for sc, _v, t in rated])[:30]
    # Quick reads: ebooks under about 220 pages
    quick = []
    for t in books:
        m = meta_of(titles[t["id"]]) if t["id"] in titles else {}
        if t["kind"] == "book" and 0 < (m.get("pages") or 0) <= 220 and t["status"] != "done":
            quick.append((m["pages"], dict(t, line=f"{m['pages']} pages" if t["status"] == "not" else t["line"])))
    quick = [t for _p, t in sorted(quick, key=lambda x: x[0])][:30]
    # Genres with at least 4 books, shuffled once a day (each profile gets its own order)
    by_genre = {}
    for t in tiles.values():
        for g in t.get("genres") or []:
            if g == "Kids" and is_kids:
                continue
            by_genre.setdefault(g, []).append(t)
    rng = random.Random(f"{time.strftime('%Y-%m-%d')}-{user_id}")
    names = sorted(g for g, ts in by_genre.items() if len(_one_per_book(ts)) >= 4)
    rng.shuffle(names)
    genre_rows = []
    for g in names[:5]:
        ts = _one_per_book(by_genre[g])
        # Not started first (something new to try), then the rest; shuffled inside so it isn't always A to Z
        rng.shuffle(ts)
        ts.sort(key=lambda t: t["status"] == "done")
        genre_rows.append({"key": "genre-" + re.sub(r"\W+", "-", g.lower()), "title": HOME_GENRE_NAMES.get(g, g), "tiles": ts[:30]})
    # Spread them out: a genre, the best rated, a genre, quick reads, then the other genres
    order = genre_rows[:1] + [{"key": "rated", "title": "Readers love these", "tiles": top_rated}] + genre_rows[1:2]
    order += [{"key": "quick", "title": "Quick reads", "tiles": quick}] + genre_rows[2:]
    return [r for r in order if len(r["tiles"]) >= 4]


def home(user_id, is_kids):
    """The rows on Books For you (Glowfin B1): Keep going (books and audiobooks), New for you, the top lists, then
    Recently added and a few more. Want to read has its own tab now."""
    lib = library(user_id, is_kids)
    tiles = {t["id"]: t for t in lib["titles"]}
    u = _user(user_id)
    deck = []
    # Sent from the TV to the phone come first
    for tid, _ in sorted(u["sent"].items(), key=lambda kv: -kv[1]):
        if tid in tiles and tiles[tid]["status"] != "done":
            deck.append(dict(tiles[tid], sent=True))
    started = [t for t in tiles.values() if t["status"] == "reading" or (t["kind"] in ("manga", "comic") and t["status"] != "done" and t["lastAt"])]
    for t in sorted(started, key=lambda t: -t["lastAt"]):
        if t["id"] not in {d["id"] for d in deck}:
            deck.append(t)
    out = _one_per_book(deck)
    newf = _one_per_book(sorted([t for t in tiles.values() if t["new"] or t["newCount"]], key=lambda t: -t["added"]))[:30]
    recent = _one_per_book(sorted(tiles.values(), key=lambda t: -t["added"]))[:30]
    rows = [
        {"key": "deck", "title": "Keep going", "tiles": out[:30]},
        {"key": "new", "title": "New for you", "tiles": newf},
        {"key": "chapters", "title": "New chapters", "tiles": sorted([t for t in tiles.values() if t["kind"] == "manga" and t["newCount"]], key=lambda t: -t["added"])[:30]},
    ]
    # The top lists (NYT, Hardcover, Apple Books, Open Library, award winners), only books on Benflix
    if booklists is not None:
        try:
            for key, title, got in booklists.home_rows(list(tiles.values())):
                rows.append({"key": key, "title": title, "tiles": got})
        except Exception as e:
            log(f"Home list rows: {e!r}")
    rows.append({"key": "recent", "title": "Recently added", "tiles": recent})
    rows += _home_extra_rows(tiles, user_id, is_kids)
    return {"rows": [r for r in rows if r["tiles"]], "counts": lib["counts"], "empty": not lib["titles"], "requests": lib["requests"], "folders": lib["folders"]}


def _place_line(t, other, u):
    """'Your place matches' or where reading and listening are, for books you have both ways"""
    a = u["place"].get(t["id"]) or {}
    b = u["place"].get(other["id"]) or {}
    pa, pb = a.get("pct") or 0, b.get("pct") or 0
    if not pa and not pb:
        return ""
    book, audio = (a, b) if t["kind"] == "book" else (b, a)
    rb, ra = (book.get("pct") or 0), (audio.get("pct") or 0)
    if abs(rb - ra) < 0.02:
        return "Your reading and listening places match"
    if rb > ra:
        return f"You've read further ({round(rb * 100)}%) than you've listened ({round(ra * 100)}%)"
    return f"You've listened further ({round(ra * 100)}%) than you've read ({round(rb * 100)}%)"


def _hm(seconds):
    m = int(seconds // 60)
    return f"{m // 60} h {m % 60} m" if m >= 60 else f"{m} min"


def _handoff_target(t, other, pct):
    """Where the other way of the same book is, at this share of the way through.
    Idea Box 29 "Read and listen at the same spot": the ebook and audiobook are matched by how far in you are."""
    if other["kind"] == "audiobook":
        ab = audio_book(other)
        total = ab["seconds"] or 0.0
        if total <= 0:
            return None
        pos = max(0.0, pct * total - 15.0)  # a few seconds back, so the sentence isn't cut
        start, index = 0.0, 0
        for i, f in enumerate(other["files"]):
            secs = probe(f)[0]
            if pos < start + secs or i == len(other["files"]) - 1:
                index = i
                break
            start += secs
        chapter = max(0, max((i for i, c in enumerate(ab["chapters"]) if c["at"] <= pos + 0.5), default=0))
        return {"place": {"pos": round(pos, 2), "file": index, "cpos": round(pos - start, 2), "chapter": chapter, "pct": round(pos / total, 4)}, "line": _hm(pos) + " in"}
    place = {"pct": round(pct, 4)}
    if other.get("ext") == ".epub":
        try:
            spine = epub_cached(other["path"])["spine"]
        except Exception:
            spine = []
        sizes = [max(1, s.get("size") or 1) for s in spine]
        total = sum(sizes)
        if total:
            want, run = pct * total, 0
            for i, size in enumerate(sizes):
                if want < run + size or i == len(sizes) - 1:
                    place.update(chapter=i, cpos=round(max(0.0, min(1.0, (want - run) / size)), 4))
                    break
                run += size
    return {"place": place, "line": f"{round(pct * 100)}%"}


def handoff_line(t, other, u):
    """"6 h 12 m in" / "64%": where Listen from here / Read from here would start (None when the places already match)"""
    a = u["place"].get(t["id"]) or {}
    b = u["place"].get(other["id"]) or {}
    pa, pb = a.get("pct") or 0, b.get("pct") or 0
    if pa < 0.01 or abs(pa - pb) < 0.02 or (other["kind"] != "audiobook" and not other.get("readable", True)):
        return None
    try:
        got = _handoff_target(t, other, pa)
    except Exception:
        return None
    return got["line"] if got else None


def handoff(user_id, tid):
    """Listen from here / Read from here: the other way of this book jumps to where you are in this one"""
    with _lock:
        t = _lib["titles"].get(tid)
        other = _lib["titles"].get(t.get("linked")) if t and t.get("linked") else None
    if not t or not other:
        raise BooksError(404, "This book isn't on Glowfin both ways.")
    u = _user(user_id)
    pct = (u["place"].get(tid) or {}).get("pct") or 0
    got = _handoff_target(t, other, pct)
    if not got:
        raise BooksError(400, "Couldn't work out the same spot. Start from the chapter list instead.")
    with _lock:
        u["place"][other["id"]] = dict(u["place"].get(other["id"]) or {}, **got["place"], at=int(time.time()))
        u["opened"][other["id"]] = int(time.time())
    _save_user(u)
    return {"id": other["id"], "kind": other["kind"], "line": got["line"]}


def title_page(tid, user_id, is_kids, is_admin):
    with _lock:
        t = _lib["titles"].get(tid)
    if not t or not visible(is_kids, t):
        raise BooksError(404, "That isn't on Glowfin any more.")
    u = _user(user_id)
    m = meta_of(t)
    out = tile(t, u)
    out.update(
        {
            "desc": t.get("desc") or m.get("desc") or "",
            "subjects": m.get("subjects") or t.get("subjects") or [],
            "scores": [],
            "place": u["place"].get(tid),
            "file": t.get("ext", ""),
            "size": t.get("size", 0),
            "rtl": t.get("rtl", False),
            "mode": u["modes"].get(tid),
            "canPickCover": is_admin,
            "volumes": [],
            "chapters": [],
        }
    )
    if t["kind"] in ("manga", "comic"):
        if m.get("anilistScore"):
            out["scores"].append({"site": "AniList", "score": m["anilistScore"]})
        if m.get("malScore"):
            out["scores"].append({"site": "MyAnimeList", "score": m["malScore"]})
        if m.get("totalVols"):
            out["totalVols"] = m["totalVols"]
        out["canPickCover"] = False  # the cover is Harbor's cover.jpg (or AniList's)
        if not out["subjects"]:
            out["subjects"] = t.get("subjects") or []
        chapters_unit = t.get("unit") == "ch"
        out["unit"] = t.get("unit") or "vol"
        out["webtoon"] = _is_webtoon(t)
        if out["webtoon"]:
            out["rtl"] = False
        out["pubStatus"] = t.get("pubStatus") or m.get("status") or ""
        out["anilist"] = t.get("anilist") or m.get("anilist")
        out["mal"] = t.get("mal") or m.get("mal")
        out["totalChapters"] = t.get("total") or m.get("totalChapters") or 0
        place = u["place"].get(tid) or {}
        opened_at = u["opened"].get(tid)
        for v in t["vols"]:
            vol = _lib["vols"][v]
            vp = (u["place"].get(v) or {})
            out["volumes"].append(
                {
                    "id": v,
                    "name": vol["name"],
                    "title": vol.get("chapterName") or "",
                    "num": vol["num"],
                    "unit": vol["unit"],
                    "source": vol.get("source") or "",
                    "official": bool(vol.get("official")),
                    "done": v in u["done"],
                    "progress": 1.0 if v in u["done"] else (vp.get("pct") or 0),
                    "current": v == place.get("vol"),
                    "cover": cover_url(v),
                    "new": opened_at is not None and vol["added"] > max(opened_at, time.time() - NEW_DAYS * 86400),
                    "pdf": vol["ext"] == ".pdf",
                    "size": vol.get("size") or 0,
                }
            )
        nxt = next((x for x in out["volumes"] if x["current"] and not x["done"]), None) or next((x for x in out["volumes"] if not x["done"]), None)
        out["next"] = nxt["id"] if nxt else (out["volumes"][0]["id"] if out["volumes"] else None)
        # For AniList and MyAnimeList: the highest whole chapter (or volume) read with nothing unread before it
        read_to = 0
        for x in out["volumes"]:
            if x["unit"] != ("ch" if chapters_unit else "vol"):
                continue
            if not x["done"]:
                break
            read_to = max(read_to, int(x["num"]))
        out["readTo"] = read_to
        out["readAll"] = bool(out["volumes"]) and all(x["done"] for x in out["volumes"])
        if out["volumes"]:
            out["line"] = _count_words(t)
    elif t["kind"] == "book":
        if m.get("score"):
            out["scores"].append({"site": "Open Library", "score": m["score"], "outOf": 5})
        if t["ext"] == ".epub":
            try:
                b = epub_cached(t["path"])
                out["chapters"] = [{"title": c["title"], "index": c["index"]} for c in b["chapters"]]
                words = sum(i["size"] for i in b["spine"]) / 6.5
                out["minutes"] = round(words / 250)
            except Exception as e:
                log(f"Couldn't open {t['name']}: {e!r}")
                out["readable"] = False
        if m.get("pages"):
            out["pages"] = m["pages"]
    elif t["kind"] == "audiobook":
        ab = audio_book(t)
        out["chapters"] = [
            {"title": c["title"], "index": i, "seconds": round(c["length"]), "file": c["file"], "start": round(c["start"], 2), "at": round(c["at"], 2)}
            for i, c in enumerate(ab["chapters"])
        ]
        out["fileSeconds"] = [round(probe(f)[0], 2) for f in t["files"]]
        out["seconds"] = round(ab["seconds"])
        out["narrator"] = ab["narrator"]
        out["missing"] = ab["missing"]
        p = u["place"].get(tid) or {}
        out["secondsLeft"] = round(max(0.0, ab["seconds"] - (p.get("pos") or 0)))
        lb = m
        if t.get("linked") and t["linked"] in _lib["titles"]:
            lb = meta_of(_lib["titles"][t["linked"]]) or m
        if lb.get("score"):
            out["scores"].append({"site": "Open Library", "score": lb["score"], "outOf": 5})
        if not out["desc"] and t.get("linked") in _lib["titles"]:
            other = _lib["titles"][t["linked"]]
            out["desc"] = other.get("desc") or meta_of(other).get("desc") or ""
    if t.get("linked") and t["linked"] in _lib["titles"]:
        other = _lib["titles"][t["linked"]]
        out["other"] = {"id": other["id"], "kind": other["kind"], "readable": other.get("readable", True)}
        out["placeLine"] = _place_line(t, other, u)
        out["handoff"] = handoff_line(t, other, u)
    # More by this author, then More like this (both from what's on Benflix)
    tiles = [tile(x, u) for x in _lib["titles"].values() if x["id"] not in (tid, t.get("linked")) and visible(is_kids, x)]
    _fill_series(tiles + [out])
    author = author_key(out["author"])
    by = [x for x in tiles if author and author_key(x["author"]) == author]
    by.sort(key=lambda x: (x["series"] != out["series"], x["seriesIndex"] or 0, natural(x["name"])))
    mine = set(g.lower() for g in (m.get("genres") or [])) | set(s.lower() for s in (m.get("subjects") or [])[:15])
    like = []
    for x in tiles:
        if x in by:
            continue
        xm = meta_of(_lib["titles"][x["id"]])
        theirs = set(g.lower() for g in (xm.get("genres") or [])) | set(s.lower() for s in (xm.get("subjects") or [])[:15])
        score = len(mine & theirs) + (2 if x["kind"] == t["kind"] else 0)
        if score >= 2:
            like.append((score, x))
    like.sort(key=lambda s: -s[0])
    out["moreBy"] = _one_per_book(by)[:20]
    out["moreLike"] = _one_per_book([x for _, x in like])[:20]
    if out["series"]:
        same = [x for x in tiles if x["series"] and norm(x["series"]) == norm(out["series"]) and x["kind"] == t["kind"]]
        same.append(out)
        same = series_order(same)
        out["seriesBooks"] = [{"id": x["id"], "name": x["name"], "seriesIndex": x["seriesIndex"]} for x in same]
    return out


def what_next(tid, user_id, is_kids):
    """3 picks at the end of a book: the next in the series, then more by the author, then more like it"""
    page = title_page(tid, user_id, is_kids, False)
    picks = []
    sb = page.get("seriesBooks") or []
    ids = [x["id"] for x in sb]
    if tid in ids and ids.index(tid) + 1 < len(ids):
        nxt = _lib["titles"].get(ids[ids.index(tid) + 1])
        if nxt:
            picks.append(tile(nxt, _user(user_id)))
    for x in page["moreBy"] + page["moreLike"]:
        if len(picks) >= 3:
            break
        if x["status"] != "done" and x["id"] not in [p["id"] for p in picks]:
            picks.append(x)
    return {"picks": picks[:3]}


def series_list(user_id, is_kids):
    lib = library(user_id, is_kids)
    groups = {}
    for t in lib["titles"]:
        if t["kind"] in ("manga", "comic"):
            continue  # manga has its own tab
        if t["series"] and not (t["kind"] == "audiobook" and t.get("linked")):
            g = groups.setdefault(("s", norm(t["series"])), {"name": t["series"], "kind": "series", "titles": [], "cover": t["cover"]})
            g["titles"].append(t)
    out = []
    for g in groups.values():
        if g["kind"] == "series":
            g["titles"] = series_order(g["titles"])
            # A "series" with only one book here isn't worth a stack (it still shows on the book's page)
            if len(g["titles"]) < 2:
                continue
        if g["titles"]:
            g["cover"] = g["titles"][0]["cover"]
        books = [x for x in g["titles"]]
        done = sum(1 for x in books if x["status"] == "done")
        if g["kind"] in ("manga", "comic"):
            g["line"] = books[0]["line"]
        else:
            g["line"] = f"{len(books)} " + ("books" if len(books) != 1 else "book") + (f" · {done} finished" if done else "")
        out.append(g)
    out.sort(key=lambda g: natural(g["name"]))
    return {"series": out}


def authors_list(user_id, is_kids):
    lib = library(user_id, is_kids)
    groups = {}
    for t in lib["titles"]:
        if t["author"] and not (t["kind"] == "audiobook" and t.get("linked")):
            g = groups.setdefault(author_key(t["author"]), {"name": t["author"], "titles": []})
            g["titles"].append(t)
    out = sorted(groups.values(), key=lambda g: natural(g["name"].split()[-1] if g["name"].split() else g["name"]))
    for g in out:
        g["cover"] = g["titles"][0]["cover"]
        g["line"] = f"{len(g['titles'])} " + ("titles" if len(g["titles"]) != 1 else "title")
    return {"authors": out}


# ---------- Places, finished, want to read ----------


def save_place(user_id, tid, body):
    u = _user(user_id)
    t = _lib["titles"].get(tid)
    vol = _lib["vols"].get(tid)
    if not t and not vol:
        raise BooksError(404, "That isn't on Glowfin any more.")
    now = int(time.time())
    keep = {k: body[k] for k in ("pct", "page", "pages", "chapter", "cpos", "pos", "file", "vol", "mode") if k in body}
    if "pct" in keep:
        try:
            keep["pct"] = max(0.0, min(1.0, float(keep["pct"])))
        except (TypeError, ValueError):
            keep.pop("pct")
    with _lock:
        if vol:
            # A manga volume: the volume's own place, and the series remembers which volume you're on
            u["place"][tid] = dict(u["place"].get(tid) or {}, **keep, at=now)
            series = u["place"].setdefault(vol["title"], {})
            series.update(vol=tid, pct=keep.get("pct", series.get("pct", 0)), at=now)
            u["opened"][vol["title"]] = now
            if keep.get("pct", 0) >= 0.98 or body.get("finished"):
                u["done"][tid] = now
                _maybe_series_done(u, vol["title"])
        else:
            u["place"][tid] = dict(u["place"].get(tid) or {}, **keep, at=now)
            u["opened"][tid] = now
            if body.get("finished"):
                u["finished"][tid] = now
            u["sent"].pop(tid, None)
        # Time spent, for My watching and Wrapped (minutes a day, read or listen)
        add = body.get("seconds")
        if isinstance(add, (int, float)) and 0 < add < 3600:
            day = time.strftime("%Y-%m-%d")
            kind = "listen" if (t or {}).get("kind") == "audiobook" else "read"
            d = u["time"].setdefault(day, {})
            d[kind] = round(d.get(kind, 0) + add / 60.0, 2)
            if t or vol:
                tt = t or _lib["titles"].get(vol["title"])
                if tt:
                    a = tt["author"] or meta_of(tt).get("author") or ""
                    if a:
                        d.setdefault("authors", {})
                        d["authors"][a] = round(d["authors"].get(a, 0) + add / 60.0, 2)
        mode = body.get("readerMode")
        if isinstance(mode, dict):
            key = vol["title"] if vol else tid
            u["modes"][key] = {k: v for k, v in mode.items() if k in ("mode", "dir")}
    _save_user(u)
    return {"ok": True}


def _maybe_series_done(u, sid):
    t = _lib["titles"].get(sid)
    if t and all(v in u["done"] for v in t["vols"]):
        u["finished"][sid] = int(time.time())


def mark(user_id, body):
    """Mark as read (or not), optionally everything before it too"""
    u = _user(user_id)
    tid = str(body.get("id") or "")
    done = bool(body.get("done", True))
    before = bool(body.get("before"))
    now = int(time.time())
    with _lock:
        vol = _lib["vols"].get(tid)
        if vol:
            series = _lib["titles"].get(vol["title"])
            vols = series["vols"] if series else [tid]
            pick = vols[: vols.index(tid) + 1] if before and tid in vols else [tid]
            for v in pick:
                if done:
                    u["done"][v] = now
                else:
                    u["done"].pop(v, None)
            if done:
                _maybe_series_done(u, vol["title"])
                # Your place moves on to the next volume
                if tid in vols and vols.index(tid) + 1 < len(vols):
                    u["place"].setdefault(vol["title"], {}).update(vol=vols[vols.index(tid) + 1], pct=0, at=now)
            else:
                u["finished"].pop(vol["title"], None)
        elif tid in _lib["titles"]:
            t = _lib["titles"][tid]
            ids = [tid]
            if before and t.get("series"):
                ids += [x["id"] for x in _lib["titles"].values() if x["kind"] == t["kind"] and norm(x.get("series")) == norm(t["series"]) and (x.get("seriesIndex") or 0) < (t.get("seriesIndex") or 0)]
            for i in ids:
                if done:
                    u["finished"][i] = now
                    if _lib["titles"][i]["kind"] in ("manga", "comic"):
                        for v in _lib["titles"][i]["vols"]:
                            u["done"][v] = now
                else:
                    u["finished"].pop(i, None)
                    if _lib["titles"][i]["kind"] in ("manga", "comic"):
                        for v in _lib["titles"][i]["vols"]:
                            u["done"].pop(v, None)
                    p = u["place"].get(i)
                    if p:
                        p["pct"] = 0
        else:
            raise BooksError(404, "That isn't on Glowfin any more.")
    _save_user(u)
    return {"ok": True}


def set_want(user_id, body):
    u = _user(user_id)
    key = str(body.get("id") or "")[:80]
    if not key:
        raise BooksError(400, "Which book?")
    with _lock:
        if body.get("want", True):
            u["want"][key] = {"at": int(time.time()), "name": str(body.get("name") or "")[:200], "author": str(body.get("author") or "")[:120], "cover": body.get("cover"), "kind": body.get("kind") or ""}
        else:
            u["want"].pop(key, None)
    _save_user(u)
    return {"ok": True, "want": key in u["want"]}


def send_to_phone(user_id, tid):
    u = _user(user_id)
    if tid not in _lib["titles"]:
        raise BooksError(404, "That isn't on Glowfin any more.")
    with _lock:
        u["sent"][tid] = int(time.time())
    _save_user(u)
    return {"ok": True, "words": "Sent. It's at the front of On deck on your phone."}


# ---------- Highlights and notes (Ben 2026-11-21): private to each profile, they follow you between screens ----------
MARKS_MAX = 500


def marks_of(user_id, tid):
    u = _user(user_id)
    with _lock:
        return {"marks": list(u["marks"].get(tid) or [])}


def save_mark(user_id, tid, body):
    """Adds a highlight, changes its note, or (remove) takes it away"""
    u = _user(user_id)
    if tid not in _lib["titles"]:
        raise BooksError(404, "That isn't on Glowfin any more.")
    mid = re.sub(r"\W", "", str(body.get("id") or ""))[:32]
    note = " ".join(str(body.get("note") or "").split())[:500]
    with _lock:
        marks = u["marks"].setdefault(tid, [])
        old = next((m for m in marks if m.get("id") == mid), None) if mid else None
        if body.get("remove"):
            if old:
                marks.remove(old)
        elif old:
            old["note"] = note
        else:
            text = " ".join(str(body.get("text") or "").split())[:600]
            if len(text) < 2:
                raise BooksError(400, "There's nothing to highlight there.")
            if len(marks) >= MARKS_MAX:
                raise BooksError(400, "That's a lot of highlights for one book. Remove a few first.")
            try:
                chapter = max(0, int(body.get("chapter") or 0))
                cpos = max(0.0, min(1.0, float(body.get("cpos") or 0)))
            except (TypeError, ValueError):
                chapter, cpos = 0, 0.0
            marks.append({"id": uuid.uuid4().hex[:12], "chapter": chapter, "cpos": cpos, "text": text, "note": note, "at": int(time.time())})
        if not marks:
            u["marks"].pop(tid, None)
        out = sorted(u["marks"].get(tid) or [], key=lambda m: (m.get("chapter", 0), m.get("cpos", 0)))
    _save_user(u)
    return {"ok": True, "marks": out}


# ---------- Send to Kindle (Ben 2026-11-21): Tower emails the EPUB to the person's Kindle address ----------
KINDLE_MAX_BYTES = 24 * 1024 * 1024  # most mail services stop at 25 MB


def kindle_info(user_id, is_admin):
    u = _user(user_id)
    mail = _state.get("mail") or {}
    out = {"address": u.get("kindle") or "", "ready": bool(mail.get("host") and mail.get("user") and mail.get("password"))}
    if is_admin:
        out["mail"] = {"host": mail.get("host", ""), "port": mail.get("port", 587), "user": mail.get("user", ""), "set": bool(mail.get("password"))}
    return out


def kindle_address(user_id, body):
    address = str(body.get("address") or "").strip()[:120]
    if address and not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", address):
        raise BooksError(400, "That doesn't look like an email address. A Kindle address ends in @kindle.com.")
    u = _user(user_id)
    with _lock:
        u["kindle"] = address
    _save_user(u)
    return {"ok": True, "address": address}


def kindle_mail(body):
    """Ben only: the email account Tower sends from (kept on Tower)"""
    host = str(body.get("host") or "").strip()[:120]
    user = str(body.get("user") or "").strip()[:120]
    password = str(body.get("password") or "")[:200]
    try:
        port = int(body.get("port") or 587)
    except (TypeError, ValueError):
        port = 587
    with _lock:
        old = _state.get("mail") or {}
        _state["mail"] = {"host": host, "port": port, "user": user, "password": password or old.get("password", "")}
    _save_state()
    return {"ok": True}


def send_to_kindle(user_id, tid):
    import smtplib
    from email.message import EmailMessage

    t = _lib["titles"].get(tid)
    if not t:
        raise BooksError(404, "That isn't on Glowfin any more.")
    # Ben: only EPUB goes to a Kindle, nothing else is allowed
    path = t.get("path") or ""
    if t.get("ext") != ".epub" or not path.lower().endswith(".epub") or not os.path.isfile(path) or not zipfile.is_zipfile(path):
        raise BooksError(400, "Only EPUB books can go to a Kindle, and this one isn't an EPUB.")
    u = _user(user_id)
    to = u.get("kindle") or ""
    if not to:
        raise BooksError(400, "Type your Kindle's email address first.")
    mail = _state.get("mail") or {}
    if not (mail.get("host") and mail.get("user") and mail.get("password")):
        raise BooksError(400, "Ben hasn't given Tower an email account to send from yet.")
    if os.path.getsize(path) > KINDLE_MAX_BYTES:
        raise BooksError(400, "This book is too big to email (over 24 MB).")
    msg = EmailMessage()
    msg["From"] = mail["user"]
    msg["To"] = to
    msg["Subject"] = t["name"][:120]
    msg.set_content("Sent from Glowfin.")
    name = re.sub(r"[^\w .,'-]", "", t["name"]).strip()[:80] or "book"
    with open(path, "rb") as f:
        msg.add_attachment(f.read(), maintype="application", subtype="epub+zip", filename=name + ".epub")
    try:
        port = int(mail.get("port") or 587)
        if port == 465:
            server = smtplib.SMTP_SSL(mail["host"], port, timeout=60)
        else:
            server = smtplib.SMTP(mail["host"], port, timeout=60)
            server.starttls()
        with server:
            server.login(mail["user"], mail["password"])
            server.send_message(msg)
    except smtplib.SMTPAuthenticationError:
        raise BooksError(502, "The email account didn't accept Tower's sign-in. Ben needs to check the password.")
    except (smtplib.SMTPException, OSError) as e:
        log(f"Send to Kindle failed: {e!r}")
        raise BooksError(502, "The email didn't go out. Check Tower's email settings and try again.")
    return {"ok": True, "words": "Sent. It shows up on the Kindle in a few minutes."}


def opened(user_id, tid):
    u = _user(user_id)
    with _lock:
        u["opened"][tid] = int(time.time())
    _save_user(u)
    return {"ok": True}


def stats(user_id, days=7):
    """Minutes read and listened (for My watching), and a year of it for Wrapped"""
    u = _user(user_id)
    out_days = []
    for i in range(days - 1, -1, -1):
        day = time.strftime("%Y-%m-%d", time.localtime(time.time() - i * 86400))
        d = u["time"].get(day, {})
        out_days.append({"day": day, "read": round(d.get("read", 0)), "listen": round(d.get("listen", 0))})
    year = time.strftime("%Y")
    yr = {"read": 0.0, "listen": 0.0}
    authors = {}
    for day, d in u["time"].items():
        if day.startswith(year):
            yr["read"] += d.get("read", 0)
            yr["listen"] += d.get("listen", 0)
            for a, mins in (d.get("authors") or {}).items():
                authors[a] = authors.get(a, 0) + mins
    finished = [tid for tid, at in u["finished"].items() if time.strftime("%Y", time.localtime(at)) == year and tid in _lib["titles"]]
    return {
        "days": out_days,
        "year": {"readMinutes": round(yr["read"]), "listenMinutes": round(yr["listen"]), "finished": len(finished)},
        "topAuthors": [a for a, _ in sorted(authors.items(), key=lambda kv: -kv[1])[:5]],
        "finishedTitles": [{"id": tid, "name": _lib["titles"][tid]["name"], "cover": cover_url(tid)} for tid in finished[:12]],
    }


# ---------- Covers ----------


def _thumb(src_bytes, key, w):
    """A smaller copy of a picture (made once with ffmpeg and kept)"""
    if not _dir or not have_tool("ffmpeg"):
        return src_bytes
    out = os.path.join(_dir, "thumbs", f"{key}-{w}.jpg")
    if os.path.isfile(out):
        with open(out, "rb") as f:
            return f.read()
    tmp_in = out + ".in"
    try:
        with open(tmp_in, "wb") as f:
            f.write(src_bytes)
        r = subprocess.run(
            ["ffmpeg", "-v", "quiet", "-y", "-i", tmp_in, "-vf", f"scale='min({w},iw)':-2", "-frames:v", "1", "-q:v", "4", "-f", "image2", out],
            capture_output=True, timeout=60,
        )
        if r.returncode == 0 and os.path.isfile(out):
            with open(out, "rb") as f:
                return f.read()
    except (OSError, subprocess.TimeoutExpired):
        pass
    finally:
        try:
            os.remove(tmp_in)
        except OSError:
            pass
    return src_bytes


def _download_cover(url, dest):
    data = _fetch(url, headers={"Accept": "image/*"}, timeout=30)
    if len(data) < 1200:  # Open Library sends a tiny blank picture when it has none
        raise BooksError(404, "No cover there.")
    with open(dest, "wb") as f:
        f.write(data)
    return data


def _local_cover(t):
    """A cover from the files themselves: a picture in the folder, the ebook's cover, the first manga page, or the audiobook's picture"""
    folders = []
    if t.get("folder"):
        folders.append(t["folder"])
    if t.get("path"):
        folders.append(os.path.dirname(t["path"]))
    for folder in folders:
        try:
            for f in sorted(os.listdir(folder), key=lambda n: (0 if os.path.splitext(n)[0].lower() in ("cover", "folder", "poster") else 1, n)):
                if os.path.splitext(f)[0].lower() in ("cover", "folder", "poster") and os.path.splitext(f)[1].lower() in PICS:
                    with open(os.path.join(folder, f), "rb") as fh:
                        return fh.read()
        except OSError:
            pass
    if t["kind"] == "book" and t["ext"] == ".epub":
        try:
            b = epub_cached(t["path"])
            if b["cover"]:
                with zipfile.ZipFile(t["path"]) as z:
                    return z.read(b["cover"])
        except Exception:
            pass
    if t["kind"] in ("manga", "comic") and t.get("vols"):
        try:
            return page_bytes(t["vols"][0], 0)[0]
        except Exception:
            pass
    if t["kind"] == "audiobook" and have_tool("ffmpeg") and _dir:
        out = os.path.join(_dir, "thumbs", t["id"] + "-art.jpg")
        if not os.path.isfile(out):
            subprocess.run(["ffmpeg", "-v", "quiet", "-y", "-i", t["files"][0], "-an", "-frames:v", "1", out], capture_output=True, timeout=60)
        if os.path.isfile(out) and os.path.getsize(out) > 1000:
            with open(out, "rb") as f:
                return f.read()
    return None


def cover(tid, w):
    w = min(THUMB_WIDTHS, key=lambda x: abs(x - (w or 320)))
    vol = _lib["vols"].get(tid)
    if vol:
        picked = (_state.get("covers") or {}).get(tid)
        if picked and _dir and os.path.isfile(os.path.join(_dir, "covers", tid + ".jpg")):
            with open(os.path.join(_dir, "covers", tid + ".jpg"), "rb") as f:
                return _thumb(f.read(), f"{tid}-pick{picked.get('at', 0)}", w), "image/jpeg"
        data = page_bytes(tid, 0)[0]
        return _thumb(data, f"{tid}-{vol['added']}", w), "image/jpeg"
    t = _lib["titles"].get(tid)
    if not t:
        raise BooksError(404, "No such book.")
    picked = (_state.get("covers") or {}).get(tid)
    if picked and _dir and os.path.isfile(os.path.join(_dir, "covers", tid + ".jpg")):
        with open(os.path.join(_dir, "covers", tid + ".jpg"), "rb") as f:
            return _thumb(f.read(), f"{tid}-pick{picked.get('at', 0)}", w), "image/jpeg"
    data = _local_cover(t)
    stamp = t["added"]
    if not data and t.get("linked") and t["linked"] in _lib["titles"]:
        data = _local_cover(_lib["titles"][t["linked"]])
    if not data and _dir:
        m = meta_of(t)
        link = m.get("cover")
        if not link and t.get("linked") in _lib["titles"]:
            link = meta_of(_lib["titles"][t["linked"]]).get("cover")
        if link:
            dest = os.path.join(_dir, "covers", "web-" + _hid(link) + ".jpg")
            if os.path.isfile(dest):
                with open(dest, "rb") as f:
                    data = f.read()
            else:
                try:
                    data = _download_cover(link, dest)
                except Exception:
                    data = None
            stamp = _hid(link)
    if not data:
        raise BooksError(404, "No cover yet.")
    return _thumb(data, f"{tid}-{stamp}", w), "image/jpeg"


def cover_choices(tid):
    """Ben's cover picker: covers of every edition, labelled"""
    t = _lib["titles"].get(tid) or (_lib["titles"].get(_lib["vols"][tid]["title"]) if tid in _lib["vols"] else None)
    if not t:
        raise BooksError(404, "No such book.")
    out = []
    if True:
        m = meta_of(t)
        if not m.get("ol") and t.get("linked") in _lib["titles"]:
            m = meta_of(_lib["titles"][t["linked"]])
        if m.get("ol"):
            try:
                eds = (_json("https://openlibrary.org" + m["ol"] + "/editions.json?limit=120") or {}).get("entries") or []
            except Exception:
                eds = []
            seen = set()
            for e in eds:
                for c in (e.get("covers") or [])[:1]:
                    if not c or c < 0 or c in seen:
                        continue
                    seen.add(c)
                    pub = ", ".join((e.get("publishers") or [])[:1])
                    when = year_of(e.get("publish_date")) or ""
                    lang = ""
                    langs = e.get("languages") or []
                    if langs:
                        lang = langs[0].get("key", "").rsplit("/", 1)[-1]
                    out.append(
                        {
                            "url": f"https://covers.openlibrary.org/b/id/{c}-L.jpg",
                            "thumb": f"https://covers.openlibrary.org/b/id/{c}-M.jpg",
                            "label": " · ".join(str(x) for x in (pub, when) if x) or "Edition",
                            "edition": norm(pub) or "unknown",
                            "lang": lang,
                        }
                    )
            # English editions first
            out.sort(key=lambda c: (c.get("lang") not in ("eng", ""), c["label"]))
    return {"covers": out[:80], "series": bool(t.get("series") or t["kind"] in ("manga", "comic"))}


def pick_cover(body):
    """Saves Ben's cover pick. scope 'all' = every volume or book in the series gets the same edition's cover"""
    tid = str(body.get("id") or "")
    url = str(body.get("url") or "")
    edition = str(body.get("edition") or "")
    scope = body.get("scope") or "one"
    if not _dir:
        raise BooksError(503, "Tower has no folder to save covers in. Run the Glowfin setup command again.")
    if not url.startswith("https://"):
        raise BooksError(400, "That cover link doesn't look right.")
    if tid not in _lib["titles"] and tid not in _lib["vols"]:
        raise BooksError(404, "No such book.")
    jobs = [(tid, url)]
    if scope == "all":
        choices = cover_choices(tid)["covers"]
        t = _lib["titles"].get(tid) or _lib["titles"].get(_lib["vols"][tid]["title"])
        if t["kind"] in ("manga", "comic"):
            by_vol = {}
            for c in choices:
                if c.get("edition") == edition and c.get("volume"):
                    by_vol.setdefault(str(float(c["volume"])), c["url"])
            for v in t["vols"]:
                vol = _lib["vols"][v]
                link = by_vol.get(str(float(vol["num"])))
                if link:
                    jobs.append((v, link))
            jobs.append((t["id"], by_vol.get("1.0", url)))
        elif t.get("series"):
            for other in _lib["titles"].values():
                if other["id"] != t["id"] and other["kind"] == t["kind"] and norm(other.get("series")) == norm(t["series"]):
                    try:
                        oc = cover_choices(other["id"])["covers"]
                    except Exception:
                        continue
                    same = next((c for c in oc if c.get("edition") == edition), None)
                    if same:
                        jobs.append((other["id"], same["url"]))
    done = 0
    for key, link in dict(jobs).items():
        try:
            _download_cover(link, os.path.join(_dir, "covers", key + ".jpg"))
            with _lock:
                _state.setdefault("covers", {})[key] = {"url": link, "at": int(time.time())}
            done += 1
        except Exception as e:
            log(f"Cover for {key}: {e!r}")
    _save_state()
    if not done:
        raise BooksError(502, "Couldn't get that cover. Try another one.")
    return {"ok": True, "words": "Cover changed." if done == 1 else f"{done} covers changed."}


def reset_cover(tid):
    with _lock:
        _state.setdefault("covers", {}).pop(tid, None)
    if _dir:
        try:
            os.remove(os.path.join(_dir, "covers", tid + ".jpg"))
        except OSError:
            pass
    _save_state()
    return {"ok": True}


# ---------- Kids ----------


def kids_list():
    out = []
    for t in sorted(_lib["titles"].values(), key=lambda t: natural(t["name"])):
        m = meta_of(t)
        if m.get("adult") or t["kind"] in ("manga", "comic"):
            continue
        out.append({"id": t["id"], "name": t["name"], "author": t["author"] or m.get("author") or "", "kind": t["kind"], "rated": bool(m.get("kids")), "allowed": t["id"] in _state.get("kids_allow", []), "cover": cover_url(t["id"], 160)})
    return {"titles": [x for x in out if not x["rated"]], "rated": [x for x in out if x["rated"]]}


def kids_allow(body):
    tid = str(body.get("id") or "")
    with _lock:
        allow = set(_state.get("kids_allow", []))
        if body.get("allowed"):
            allow.add(tid)
        else:
            allow.discard(tid)
        _state["kids_allow"] = sorted(allow)
    _save_state()
    return {"ok": True}


# ---------- Asking for books (Chaptarr) ----------


def search(text, is_kids):
    """Books to ask for (Open Library), marked when they're already on Benflix"""
    text = (text or "").strip()
    if len(text) < 2:
        return {"books": []}
    have = {norm(t["name"]): t for t in _lib["titles"].values()}
    books = []
    try:
        q = {"q": text, "limit": "20", "fields": "key,title,author_name,first_publish_year,cover_i,ratings_average,subject,edition_count"}
        docs = (_json("https://openlibrary.org/search.json?" + urllib.parse.urlencode(q)) or {}).get("docs") or []
        for d in docs:
            name = d.get("title") or ""
            subj = " ".join(d.get("subject") or []).lower()
            if is_kids and not any(w in subj for w in KIDS_WORDS):
                continue
            here = have.get(norm(name))
            books.append(
                {
                    "key": "ol:" + (d.get("key") or "").rsplit("/", 1)[-1],
                    "name": name,
                    "author": ", ".join((d.get("author_name") or [])[:2]),
                    "year": d.get("first_publish_year"),
                    "cover": f"https://covers.openlibrary.org/b/id/{d['cover_i']}-M.jpg" if d.get("cover_i") else None,
                    "score": round(d["ratings_average"], 1) if d.get("ratings_average") else None,
                    "have": here["id"] if here else None,
                    "asked": _asked_words("ol:" + (d.get("key") or "").rsplit("/", 1)[-1]),
                }
            )
    except Exception as e:
        log(f"Book search: {e!r}")
    return {"books": books}


def _asked_words(key):
    for r in _state["requests"]:
        if r["key"] == key and r["state"] != "no":
            return words_for(r)
    return None


def words_for(r):
    return {
        "waiting": "Waiting for Ben",
        "searching": "Searching",
        "downloading": "Downloading",
        "ready": "Ready",
        "cant": "Couldn't find it",
        "no": "Ben said no",
        "problem": "Waiting for Tower",
    }.get(r["state"], "Searching")


def request_view(r, is_admin):
    out = {k: r.get(k) for k in ("id", "key", "name", "author", "year", "cover", "types", "state", "title", "who", "at", "problem")}
    out["words"] = words_for(r)
    out["canRetry"] = r["state"] in ("cant", "problem") and is_admin
    return out


def ask(user_id, user_name, is_admin, body, for_person=None):
    """[for_person] = (id, name) when Ben asked for someone in the family: it's theirs (their list and news)."""
    if is_admin and for_person:
        user_id, user_name = for_person
    key = str(body.get("key") or "")[:60]
    if not key:
        raise BooksError(400, "Which book?")
    types = [x for x in (body.get("types") or ["ebook", "audiobook"]) if x in ("ebook", "audiobook")] or ["ebook", "audiobook"]
    if key.startswith("al:"):
        raise BooksError(400, "Manga can't be asked for any more. Books and audiobooks can.")
    with _lock:
        for r in _state["requests"]:
            if r["key"] == key and r["state"] not in ("no", "cant"):
                r["types"] = sorted(set(r["types"]) | set(types))
                return {"request": request_view(r, is_admin), "words": "Already asked for."}
        r = {
            "id": uuid.uuid4().hex[:10],
            "key": key,
            "name": str(body.get("name") or "")[:200],
            "author": str(body.get("author") or "")[:200],
            "year": body.get("year"),
            "cover": body.get("cover"),
            "types": types,
            "user": user_id,
            "who": user_name,
            "at": int(time.time()),
            "state": "waiting" if not is_admin else "searching",
        }
        _state["requests"].insert(0, r)
        del _state["requests"][300:]
    _save_state()
    if r["state"] == "searching":
        threading.Thread(target=_send, args=(r,), daemon=True).start()
        words = f"Asked for {user_name}. Tower is looking for it." if for_person else "Asked for. Tower is looking for it."
    else:
        words = "Asked Ben."
    # Asking also puts it on your Want to read list
    set_want(user_id, {"id": key, "name": r["name"], "author": r["author"], "cover": r["cover"], "kind": "book"})
    return {"request": request_view(r, is_admin), "words": words}


def answer(body):
    """Ben says yes or no to someone else's request"""
    rid = str(body.get("id") or "")
    yes = bool(body.get("yes"))
    with _lock:
        r = next((x for x in _state["requests"] if x["id"] == rid), None)
        if not r:
            raise BooksError(404, "That request is gone.")
        if not yes:
            r["state"] = "no"
        else:
            r["state"] = "searching"
            r["at"] = int(time.time())
    _save_state()
    if r["state"] == "searching":
        threading.Thread(target=_send, args=(r,), daemon=True).start()
    return {"ok": True}


def retry(rid):
    with _lock:
        r = next((x for x in _state["requests"] if x["id"] == rid), None)
        if not r:
            raise BooksError(404, "That request is gone.")
        r["state"] = "searching"
        r["at"] = int(time.time())
        r.pop("problem", None)
        r.pop("pickedAt", None)
    _save_state()
    threading.Thread(target=_send, args=(r,), daemon=True).start()
    return {"ok": True}


def forget(rid, user_id, is_admin):
    with _lock:
        r = next((x for x in _state["requests"] if x["id"] == rid), None)
        if r and (is_admin or r.get("user") == user_id):
            _state["requests"].remove(r)
    _save_state()
    return {"ok": True}


# ---------- Chaptarr (Readarr-style /api/v1) ----------


def ch(method, path, body=None, timeout=60):
    if not CH_URL or not CH_KEY:
        raise BooksError(503, "Chaptarr isn't connected yet. Run the Glowfin setup command again.")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        CH_URL + path, data=data, method=method,
        headers={"X-Api-Key": CH_KEY, "Accept": "application/json", "Content-Type": "application/json", "User-Agent": UA},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        detail = ""
        try:
            j = json.loads(e.read() or b"null")
            detail = (j[0].get("errorMessage") if isinstance(j, list) and j else (j or {}).get("message")) or ""
        except Exception:
            pass
        if "already" in detail.lower() or "exists" in detail.lower():
            raise BooksError(409, detail)
        raise BooksError(502, f"Chaptarr said no ({e.code}). {detail}".strip())


_ch_setup = {"at": 0}


def _ch_defaults():
    """Chaptarr's quality and metadata profiles and its Books / Audiobooks root folders (looked up once an hour)"""
    if time.time() - _ch_setup["at"] < 3600:
        return _ch_setup
    roots = ch("GET", "/api/v1/rootfolder") or []
    if not roots:
        raise BooksError(503, "Chaptarr has no root folders yet. Add /data/Books and /data/Audiobooks in Chaptarr's Media Management.")
    quality = ch("GET", "/api/v1/qualityprofile") or []
    try:
        meta = ch("GET", "/api/v1/metadataprofile") or []
    except BooksError:
        meta = []
    audio = next((x for x in roots if "audio" in (x.get("path") or "").lower()), None)
    ebook = next((x for x in roots if x is not audio and "book" in (x.get("path") or "").lower()), None) or next((x for x in roots if x is not audio), None)
    first = quality[0].get("id") if quality else 1
    _ch_setup.update(
        at=time.time(), ebook=ebook or audio, audio=audio or ebook,
        audio_quality=next((q.get("id") for q in quality if q.get("profileType") == "audiobook"), None)
        or next((q.get("id") for q in quality if "audio" in (q.get("name") or "").lower()), first),
        ebook_quality=next((q.get("id") for q in quality if q.get("profileType") == "ebook"), None)
        or next((q.get("id") for q in quality if "audio" not in (q.get("name") or "").lower()), first),
        meta=_ch_english_meta(meta),
        ebook_meta=next((m.get("id") for m in meta if "ebook" in (m.get("name") or "").lower()), None),
        audiobook_meta=next((m.get("id") for m in meta if "audio" in (m.get("name") or "").lower()), None),
    )
    _ch_english_releases()
    _ch_setup["indexers"] = _ch_indexers()
    return _ch_setup


ENGLISH = "eng, en, en-US, en-GB, en-CA, en-AU, English, null"
FOREIGN_RELEASES = (
    r"/[\[\(](german|deutsch|ger|deu|french|fre|fra|spanish|spa|esp|italian|ita|polish|pol|russian|rus|ukrainian|ukr|"
    r"dutch|nld|portuguese|por|swedish|swe|turkish|tur|czech|cze|hungarian|hun|japanese|jpn|chinese|chi)[\]\)]"
    r"|\b(deutsch|fran[cç]ais|espa[nñ]ol|italiano|polski|nederlands|portugu[eê]s|svenska|t[uü]rk[cç]e|[cč]e[sš]tina|magyar)\b/i"
)


SKIP_TITLES = ["podcast", "calendar", "planner", "box set", "boxed set", "bundle", "collection set", "books set",
               "bonus chapter", "exclusive scenes", "coloring book", "colouring book", "pick-a-stick",
               "dramatized adaptation", "preorder", "edition tapa", "edición", "deutsche ausgabe", "german edition"]
ENGLISH_PROFILE = {"allowedLanguages": ENGLISH, "skipPartsAndSets": True, "skipMissingDate": True, "minPopularity": 0}


def _ch_english_meta(meta):
    """A "Benflix English" metadata profile in Chaptarr: English editions only, and no box sets, calendars,
    podcasts or bonus chapters, so asking for one book doesn't pull in hundreds of foreign copies of
    the author's other books. Made once by copying Chaptarr's first profile, then kept up to date.
    Falls back to the first profile if Chaptarr says no."""
    mine = next((m for m in meta if m.get("name") == "Benflix English"), None)

    def ours(base):
        out = dict(base, name="Benflix English", **ENGLISH_PROFILE)
        old = base.get("ignored")
        out["ignored"] = SKIP_TITLES if isinstance(old, list) or old is None else ", ".join(SKIP_TITLES)
        return out

    try:
        if mine is None and meta:
            mine = ch("POST", "/api/v1/metadataprofile", {k: v for k, v in ours(meta[0]).items() if k != "id"}) or None
            log("Made the Benflix English profile in Chaptarr")
        elif mine is not None:
            want = ours(mine)
            if any(mine.get(k) != want.get(k) for k in [*ENGLISH_PROFILE, "ignored"]):
                ch("PUT", f"/api/v1/metadataprofile/{mine['id']}", want)
                log("Updated the Benflix English profile in Chaptarr")
    except BooksError as e:
        log(f"Couldn't set Chaptarr to English books: {e}")
        mine = None
    if mine and mine.get("id"):
        return mine["id"]
    return meta[0].get("id") if meta else 1


def _ch_indexers():
    """How many places Chaptarr can search for books (None when it won't say)"""
    try:
        return len([i for i in ch("GET", "/api/v1/indexer") or [] if i.get("enableAutomaticSearch", True) or i.get("enableInteractiveSearch")])
    except BooksError:
        return None


def _english_author(book):
    """An author Chaptarr already had (from an older request) moves onto the English profile too"""
    d = _ch_setup
    aid = book.get("authorId") or (book.get("author") or {}).get("id")
    if not aid or not d.get("meta"):
        return
    try:
        a = ch("GET", f"/api/v1/author/{aid}") or {}
        if "ebookMetadataProfileId" in a or "audiobookMetadataProfileId" in a:
            return  # Newer Chaptarr: a profile per format, and the old single one is ignored
        if a and a.get("metadataProfileId") != d["meta"]:
            a["metadataProfileId"] = d["meta"]
            ch("PUT", f"/api/v1/author/{aid}", a)
            ch("POST", "/api/v1/command", {"name": "RefreshAuthor", "authorId": aid})
            log(f"Moved {a.get('authorName') or 'an author'} onto English books in Chaptarr")
    except BooksError as e:
        log(f"Couldn't move the author onto English books: {e}")


def _pick_english_edition(book_id, book):
    """Chaptarr searches for the book by its chosen edition's title, so a Persian or Spanish edition
    finds nothing. Switch the chosen edition to the English one most people rated."""
    try:
        eds = ch("GET", "/api/v1/edition?" + urllib.parse.urlencode({"bookId": book_id})) or []
    except BooksError:
        eds = book.get("editions") or []
    picked = _english_edition({"editions": eds}).get("editions")
    if not picked or picked == eds:
        return book
    try:
        book = dict(book, editions=picked)
        ch("PUT", f"/api/v1/book/{book_id}", book)
        log(f"Chose the English edition of {book.get('title')}")
    except BooksError as e:
        log(f"Couldn't choose the English edition: {e}")
    return book


def _ch_english_releases():
    """A Chaptarr release profile that skips downloads marked as another language ("[German]", "Deutsch" ...)"""
    try:
        have = ch("GET", "/api/v1/releaseprofile") or []
        for rp in have:
            ign = rp.get("ignored")
            if FOREIGN_RELEASES in (ign if isinstance(ign, list) else str(ign or "").split(",")):
                return
        try:
            ch("POST", "/api/v1/releaseprofile", {"enabled": True, "required": [], "ignored": [FOREIGN_RELEASES], "indexerId": 0, "tags": []})
        except BooksError:
            ch("POST", "/api/v1/releaseprofile", {"enabled": True, "required": "", "ignored": FOREIGN_RELEASES, "indexerId": 0, "tags": []})
        log("Told Chaptarr to skip downloads in other languages")
    except BooksError as e:
        log(f"Couldn't add Chaptarr's English-only download rule: {e}")


def _is_english(e):
    lang = str((e or {}).get("language") or "").lower()
    return lang in ("eng", "en", "english") or lang.startswith("en-")


def _english_edition(book):
    """Monitor only the English edition (the one most people rated) when Chaptarr knows the editions"""
    eds = [e for e in book.get("editions") or [] if isinstance(e, dict)]
    eng = [e for e in eds if _is_english(e)]
    if not eng:
        return book
    chosen = [e for e in eds if e.get("monitored")]
    if chosen and all(_is_english(e) for e in chosen):
        return book
    pick = max(eng, key=lambda e: ((e.get("ratings") or {}).get("votes") or 0, bool(e.get("isbn13"))))
    return dict(book, editions=[dict(e, monitored=e is pick) for e in eds])


NOT_THE_BOOK = ("summary", "study guide", "analysis of", "supersummary", "workbook", "sparknotes", "cliffsnotes", "quiz book")


def _author_parts(author):
    """'Matt, Matt Dinniman' or 'Sarah J. Maas, Narrator' -> the names worth matching on"""
    parts = [p for p in (norm(x) for x in str(author or "").split(",")) if len(p) > 3]
    return [p for p in parts if " " in p] or parts or [norm(author)]


def _lookup(r):
    """Ask Chaptarr for the book. Asking with the author's name too can bring back only study guides
    ("Summary of Project Hail Mary by Andy Weir"), so ask both ways and keep the best match."""
    want_t, want_as = norm(r["name"]), _author_parts(r["author"])
    found, seen = [], set()
    for term in (f"{r['name']} {r['author']}".strip(), r["name"]):
        try:
            hits = ch("GET", "/api/v1/book/lookup?" + urllib.parse.urlencode({"term": term})) or []
        except BooksError as e:
            log(f"Chaptarr lookup for {term}: {e.message}")
            hits = []
        for b in hits:
            k = b.get("foreignBookId") or b.get("titleSlug") or b.get("title")
            if k not in seen and not any(w in str(b.get("title") or "").lower() for w in NOT_THE_BOOK):
                seen.add(k)
                found.append(b)

    def score(b):
        bt = norm(b.get("title"))
        ba = norm((b.get("author") or {}).get("authorName") or b.get("authorTitle") or "")
        s = 3 if bt == want_t else 1 if want_t and (want_t in bt or bt in want_t) else 0
        return s + (2 if any(a and (a in ba or ba in a) for a in want_as) else 0)

    found = [b for b in found if score(b) not in (0, 2)]  # the title has to match, not just the author
    if not found or score(max(found, key=score)) < 2:
        raise BooksError(404, "Chaptarr couldn't find that exact book.")
    return max(found, key=score)


def _same_book(a, b):
    ka = {a.get("foreignBookId"), a.get("goodreadsWorkId"), a.get("hardcoverBookId")} - {None, ""}
    kb = {b.get("foreignBookId"), b.get("goodreadsWorkId"), b.get("hardcoverBookId")} - {None, ""}
    return bool(ka & kb)


def _book_formats(book):
    """Chaptarr keeps a title's ebook and audiobook as two books (twins that share an id from
    Hardcover). Returns {"ebook": book, "audiobook": book} for the ones it has. An older Chaptarr
    without twins has one book with an ebook switch and an audiobook switch, so it covers both."""
    if not book.get("mediaType"):
        return {"ebook": book, "audiobook": book}
    try:
        books = ch("GET", f"/api/v1/book?authorId={book.get('authorId')}") or []
    except BooksError:
        books = [book]
    out = {}
    for b in sorted(books, key=lambda x: (x.get("id") != book.get("id"), not x.get("hasFiles"), not x.get("monitored"), x.get("id") or 0)):
        if b.get("id") == book.get("id") or _same_book(b, book):
            out.setdefault(b.get("mediaType") or "ebook", b)
    return out


def _settle(book_id, seconds=120):
    """A new author: wait until Chaptarr has finished filling in their books (the count stops growing)"""
    try:
        aid = (ch("GET", f"/api/v1/book/{book_id}") or {}).get("authorId")
    except BooksError:
        return
    last, end = -1, time.time() + seconds
    while aid and time.time() < end:
        time.sleep(10)
        try:
            n = len(ch("GET", f"/api/v1/book?authorId={aid}") or [])
        except BooksError:
            return
        if n == last:
            return
        last = n


def _wait_for(cmd, seconds=120):
    """Waits (a little) for a Chaptarr job like RefreshAuthor to finish"""
    cid = (cmd or {}).get("id")
    end = time.time() + seconds
    while cid and time.time() < end:
        time.sleep(5)
        try:
            if (ch("GET", f"/api/v1/command/{cid}") or {}).get("status") in ("completed", "failed", "aborted", "cancelled"):
                return
        except BooksError:
            return


def _author_formats(aid, kinds, keep):
    """Chaptarr only makes the ebook (or audiobook) twin of a book when the author has a folder for that
    format. Authors added by older Benflix only had one, so "ebook and audiobook" requests only ever
    searched for one of them. Adds the missing folder, and turns off "get every new book by this author"
    so only the books people ask for download. Anything Chaptarr switches on while doing that (other
    than [keep]) is switched back off."""
    if not aid:
        return False
    try:
        a = ch("GET", f"/api/v1/author/{aid}") or {}
    except BooksError:
        return False
    if "ebookRootFolderPath" not in a and "audiobookRootFolderPath" not in a and "ebookStatistics" not in a:
        return False  # Older Chaptarr: one folder per author
    d = _ch_setup
    change = {}
    for k in kinds:
        if not a.get(f"{k}RootFolderPath"):
            root = d["ebook"] if k == "ebook" else d["audio"]
            change.update({
                f"{k}RootFolderPath": root["path"],
                f"{k}QualityProfileId": d["ebook_quality"] if k == "ebook" else d["audio_quality"],
                f"{k}Monitored": True,
            })
            if not a.get(f"{k}MetadataProfileId"):
                change[f"{k}MetadataProfileId"] = d.get(f"{k}_meta") or d.get("meta")
    for k in ("ebook", "audiobook"):
        if a.get(f"{k}MonitorFuture") or a.get(f"{k}MonitorNewItems") not in (None, "none"):
            change.update({f"{k}MonitorFuture": False, f"{k}MonitorNewItems": "none"})
    if not change:
        return False
    try:
        before = {b.get("id") for b in ch("GET", f"/api/v1/book?authorId={aid}") or [] if b.get("monitored")}
        ch("PUT", f"/api/v1/author/{aid}", dict(a, **change))
        if not any(k.endswith("RootFolderPath") for k in change):
            return False
        log(f"Gave {a.get('authorName') or 'an author'} a folder for {', '.join(k for k in kinds if f'{k}RootFolderPath' in change)} in Chaptarr")
        _wait_for(ch("POST", "/api/v1/command", {"name": "RefreshAuthor", "authorId": aid}))
        _unmonitor_extras(aid, before | set(keep))
        return True
    except BooksError as e:
        log(f"Couldn't set up {a.get('authorName') or 'the author'} for both formats: {e.message}")
        return False


def _unmonitor_extras(aid, keep):
    try:
        extra = [b["id"] for b in ch("GET", f"/api/v1/book?authorId={aid}") or [] if b.get("monitored") and b.get("id") not in keep]
        if extra:
            ch("PUT", "/api/v1/book/monitor", {"bookIds": extra, "monitored": False})
            log(f"Switched {len(extra)} books nobody asked for back off in Chaptarr")
    except BooksError as e:
        log(f"Couldn't switch off books nobody asked for: {e.message}")


def _switch_on(b, kind):
    """Monitor one book for the format asked for (both switches on an older Chaptarr)"""
    bid = b["id"]
    try:
        if not b.get("monitored"):
            ch("PUT", "/api/v1/book/monitor", {"bookIds": [bid], "monitored": True})
        flags = ["ebook", "audiobook"] if not b.get("mediaType") else [b["mediaType"]]
        want = [f for f in flags if (f == kind or not b.get("mediaType")) and b.get(f"{f}Monitored") is False]
        if want:
            b = ch("GET", f"/api/v1/book/{bid}") or b
            b.update(monitored=True, **{f"{f}Monitored": True for f in want})
            ch("PUT", f"/api/v1/book/{bid}", b)
    except BooksError as e:
        log(f"Couldn't switch on book {bid}: {e.message}")


def _send_to_chaptarr(r):
    best = _lookup(r)
    d = _ch_defaults()
    kinds = r["types"]
    folder = d["ebook"] if "ebook" in kinds else d["audio"]
    book_id = best.get("id") or 0
    new_author = False
    if not book_id:
        book = _english_edition(dict(best))
        author = dict(book.get("author") or {})
        author.update(
            qualityProfileId=d["ebook_quality"] if "ebook" in kinds else d["audio_quality"],
            metadataProfileId=d["meta"],
            rootFolderPath=folder["path"],
            monitored=True,
            addOptions={"monitor": "none", "searchForMissingBooks": False},
        )
        # Chaptarr's own settings: a folder per format, and don't grab every other book by this author
        for k in ("ebook", "audiobook"):
            root = d["ebook"] if k == "ebook" else d["audio"]
            author.update({
                f"{k}RootFolderPath": root["path"], f"{k}Monitored": True,
                f"{k}QualityProfileId": d["ebook_quality"] if k == "ebook" else d["audio_quality"],
                f"{k}MonitorFuture": False, f"{k}MonitorNewItems": "none",
            })
            if d.get(f"{k}_meta"):
                author[f"{k}MetadataProfileId"] = d[f"{k}_meta"]
        # Older Chaptarr keeps an ebook switch and an audiobook switch on each book, so "Both" turns both on
        book.update(
            author=author, monitored=True, addOptions={"searchForNewBook": False},
            ebookMonitored="ebook" in kinds, audiobookMonitored="audiobook" in kinds,
        )
        try:
            added = ch("POST", "/api/v1/book", book) or {}
            book_id = added.get("id") or 0
            new_author = not (best.get("author") or {}).get("id")
        except BooksError as e:
            if e.status != 409:
                raise
            # Already in Chaptarr: find it and just search again
            mine = ch("GET", "/api/v1/book") or []
            hit = next((b for b in mine if b.get("foreignBookId") == best.get("foreignBookId") or _same_book(b, best)), None) \
                or next((b for b in mine if norm(b.get("title")) == norm(best.get("title"))), None)
            book_id = (hit or {}).get("id") or 0
    if not book_id:
        raise BooksError(502, "Chaptarr didn't keep the book.")
    if new_author:
        _settle(book_id)
    book = ch("GET", f"/api/v1/book/{book_id}") or {"id": book_id}
    _english_author(book)
    aid = book.get("authorId")
    forms = _book_formats(book)
    if any(k not in forms for k in kinds) and _author_formats(aid, kinds, {b["id"] for b in forms.values()}):
        forms = _book_formats(book)
    elif aid:
        _author_formats(aid, [], {b["id"] for b in forms.values()})
    ids = []
    for k in kinds:
        b = forms.get(k)
        if b and b["id"] not in ids:
            if k == "ebook" or not b.get("mediaType"):
                b = _pick_english_edition(b["id"], b) or b
            _switch_on(b, k)
            ids.append(b["id"])
    if not ids:
        ids = [book_id]
        _switch_on(book, kinds[0])
    if new_author and aid:
        # A new author: Chaptarr may switch on all their other books while it fills them in
        _unmonitor_extras(aid, set(ids))
    ch("POST", "/api/v1/command", {"name": "BookSearch", "bookIds": ids})
    with _lock:
        r["ch"] = ids[0]
        r["chIds"] = ids
        r["searchedAt"] = int(time.time())
        r.pop("pickedAt", None)
        r["state"] = "searching"
        r.pop("problem", None)
    log(f"Asked Chaptarr for {r['name']} ({', '.join(kinds)})")


def _ids(r):
    return r.get("chIds") or ([r["ch"]] if r.get("ch") else [])


def _pick_release(r):
    """Chaptarr hides downloads whose name doesn't spell out the author ("Halo - The Flood (Chaptered
    Audiobook)" is hidden as a "Title/Author mismatch"), so it never grabs them by itself. When its own
    search found nothing, look at what it hid and grab the closest one whose name has the whole title in it,
    the way you would by hand in Chaptarr's Interactive Search."""
    got = False
    for bid in _ids(r):
        try:
            b = ch("GET", f"/api/v1/book/{bid}") or {}
            if b.get("hasFiles") or ((b.get("statistics") or {}).get("bookFileCount") or 0) > 0:
                continue
            res = ch("GET", f"/api/v1/release?bookId={bid}", timeout=180) or []
        except BooksError as e:
            log(f"Couldn't look through Chaptarr's results for {r['name']}: {e.message}")
            continue
        if isinstance(res, dict):
            shown, hidden = res.get("releases") or [], res.get("hiddenReleases") or []
        else:
            shown, hidden = [x for x in res if x.get("approved")], [x for x in res if not x.get("approved")]
        title = norm(b.get("title") or r["name"])
        words = set(title.split()) or set(norm(r["name"]).split())
        author = (b.get("author") or {}).get("authorName") or r["author"]
        last = (norm(author).split() or [""])[-1]

        def ok(x):
            details = x.get("rejectionDetails")
            if details is None:
                details = [{"reason": t, "category": "Matching" if "mismatch" in str(t).lower() else "", "canBypass": "mismatch" in str(t).lower()}
                           for t in x.get("rejections") or []]
            if not all(dd.get("canBypass") and dd.get("category") == "Matching" for dd in details):
                return False
            name = str(x.get("title") or "")
            low = name.lower()
            if any(w in low for w in NOT_THE_BOOK) or re.search(FOREIGN_RELEASES[1:-2], name, re.I):
                return False
            if x.get("protocol") == "torrent" and (x.get("seeders") or 0) < 1:
                return False
            return words <= set(norm(name).split())

        picks = [x for x in shown if x.get("downloadAllowed", True)] + [x for x in hidden if ok(x)]
        if not picks:
            continue
        pick = max(picks, key=lambda x: (bool(x.get("approved")), last and last in norm(x.get("title")), min(x.get("seeders") or 0, 50), -(x.get("ageHours") or 0)))
        try:
            ch("POST", "/api/v1/release", {"guid": pick.get("guid"), "indexerId": pick.get("indexerId"), "bookId": bid}, timeout=120)
            log(f"Picked {pick.get('title')} for {r['name']} (Chaptarr had hidden it)")
            got = True
        except BooksError as e:
            log(f"Couldn't grab {pick.get('title')}: {e.message}")
    return got


def _check_chaptarr(active):
    try:
        queue = ch("GET", "/api/v1/queue?pageSize=200") or {}
    except Exception as e:
        log(f"Couldn't ask Chaptarr how requests are going: {e!r}")
        return False
    records = queue.get("records") if isinstance(queue, dict) else queue
    downloading = {q.get("bookId") for q in records or []}
    changed = False
    for r in active:
        ids = _ids(r)
        if not ids:
            continue
        new = r["state"]
        if any(i in downloading for i in ids):
            new = "downloading"
        else:
            for i in ids:
                try:
                    b = ch("GET", f"/api/v1/book/{i}") or {}
                    if ((b.get("statistics") or {}).get("bookFileCount") or 0) > 0:
                        new = "downloading" if not r.get("title") else "ready"
                        break
                except Exception:
                    pass
        if new != r["state"]:
            r["state"] = new
            changed = True
    return changed


def _send(r):
    """Hands a request to Chaptarr"""
    try:
        _send_to_chaptarr(r)
    except BooksError as e:
        with _lock:
            r["state"] = "problem" if e.status in (502, 503) else "cant"
            r["problem"] = e.message
        log(f"Request {r['name']}: {e.message}")
    except Exception as e:
        with _lock:
            r["state"] = "problem"
            r["problem"] = "Tower couldn't reach Chaptarr."
        log(f"Request {r['name']}: {e!r}")
    _save_state()


def check_requests():
    """Every few minutes: how far along Chaptarr is with each request, and whether it's arrived"""
    active = [r for r in _state["requests"] if r["state"] in ("searching", "downloading", "problem")]
    if not active:
        return
    resend = [r for r in active if r["state"] == "problem" and not r.get("ch")]
    for r in resend[:5] if can_ask() else []:  # a few at a time, so a big batch doesn't hold everything up
        _send(r)
    changed = _check_chaptarr(active) if CH_URL else False
    picks = 0
    for r in active:
        if r["state"] != "searching" or not _ids(r):
            continue
        since = time.time() - (r.get("searchedAt") or r["at"])
        # Chaptarr's own search found nothing: look at what it hid (30 minutes after asking, then twice a day)
        if picks < 3 and since > 1800 and time.time() - (r.get("pickedAt") or 0) > 12 * 3600:
            picks += 1
            r["pickedAt"] = int(time.time())
            changed = True
            if _pick_release(r):
                r["state"] = "downloading"
                continue
        # Nothing found yet: Chaptarr only looks once, so ask it to look again every 12 hours
        if since > 12 * 3600:
            try:
                ch("POST", "/api/v1/command", {"name": "BookSearch", "bookIds": _ids(r)})
                r["searchedAt"] = int(time.time())
                changed = True
            except Exception as e:
                log(f"Couldn't ask Chaptarr to look again for {r['name']}: {e!r}")
    for r in active:
        if r["state"] in ("searching",) and time.time() - r["at"] > GIVE_UP_AFTER:
            r["state"] = "cant"
            changed = True
    if changed:
        _save_state()
    ready_check()


def ready_check():
    """A request is Ready when its book shows up in the Books or Audiobooks folder"""
    changed = False
    have = {}
    for t in _lib["titles"].values():
        have.setdefault(norm(t["name"]), []).append(t)
    for r in _state["requests"]:
        if r["state"] in ("ready", "no", "waiting", "manga"):
            continue
        hits = have.get(norm(r["name"])) or []
        hits = [t for t in hits if t["kind"] in ("book", "audiobook")]
        if not hits:
            continue
        kinds = {t["kind"] for t in hits}
        need = {"book" if k == "ebook" else k for k in r["types"] if k != "manga"}
        if need <= kinds or (need and kinds and time.time() - r["at"] > 3 * 86400):
            r["state"] = "ready"
            r["title"] = hits[0]["id"]
            r["readyAt"] = int(time.time())
            changed = True
            # Want to read now points at the real book
            u = _user(r.get("user"))
            if r["key"] in u["want"]:
                u["want"].pop(r["key"], None)
                u["want"][hits[0]["id"]] = {"at": int(time.time())}
                _save_user(u)
    if changed:
        _save_state()


def requests_view(user_id, is_admin):
    # Old manga requests (manga was removed 2026-11-18) are left out everywhere
    mine = [request_view(r, is_admin) for r in _state["requests"] if r.get("user") == user_id and r["state"] not in ("no", "manga")]
    out = {"mine": mine[:60], "connected": can_ask(), "problems": []}
    if is_admin:
        out["waiting"] = [request_view(r, True) for r in _state["requests"] if r["state"] == "waiting"]
        out["others"] = [request_view(r, True) for r in _state["requests"] if r.get("user") != user_id and r["state"] not in ("waiting", "no", "manga")][:60]
        if not can_ask():
            out["problems"].append("Chaptarr isn't connected. Set it up, then run the Glowfin setup command again.")
        elif _ch_setup.get("indexers") == 0:
            out["problems"].append(
                "Chaptarr has nowhere to look for books yet, so requests find nothing. In Prowlarr, add book sites "
                "(indexers) and connect Chaptarr under Settings > Apps."
            )
    return out


# ---------- The web pages and files ----------


def file_for(tid, index=0):
    """(path, content type) for an ebook file, an audiobook chapter file or a PDF manga volume"""
    t = _lib["titles"].get(tid)
    if t and t["kind"] == "audiobook":
        if not 0 <= index < len(t["files"]):
            raise BooksError(404, "No such chapter.")
        p = t["files"][index]
    elif t and t.get("path"):
        p = t["path"]
    elif tid in _lib["vols"]:
        p = _lib["vols"][tid]["path"]
        if os.path.isdir(p):
            raise BooksError(404, "That volume is a folder of pictures.")
    else:
        raise BooksError(404, "That isn't on Glowfin any more.")
    if not os.path.isfile(p):
        _kick.set()
        raise BooksError(404, "That file has moved. Tower is looking again; try in a minute.")
    return p, TYPES.get(os.path.splitext(p)[1].lower(), "application/octet-stream")


def epub_info(tid):
    t = _lib["titles"].get(tid)
    if not t or t.get("ext") != ".epub":
        raise BooksError(404, "That isn't an EPUB book.")
    b = epub_cached(t["path"])
    return {"id": tid, "name": t["name"], "author": t["author"], "spine": b["spine"], "chapters": b["chapters"]}


def epub_part(tid, inner):
    """One file from inside an EPUB (a chapter, a picture, a style sheet), for the reader"""
    t = _lib["titles"].get(tid)
    if not t or t.get("ext") != ".epub":
        raise BooksError(404, "That isn't an EPUB book.")
    inner = urllib.parse.unquote(inner).lstrip("/")
    if ".." in inner.split("/"):
        raise BooksError(400, "Not allowed.")
    b = epub_cached(t["path"])
    if inner not in b["names"]:
        raise BooksError(404, "Not in this book.")
    with zipfile.ZipFile(t["path"]) as z:
        data = z.read(inner)
    ext = os.path.splitext(inner)[1].lower()
    ctype = TYPES.get(ext, "application/octet-stream")
    if ext in (".xhtml", ".html", ".htm"):
        # Served as HTML (not strict XHTML) so a small mistake in the book doesn't stop the page
        ctype = "text/html; charset=utf-8"
        if not data.lstrip()[:200].lower().startswith((b"<?xml", b"<!doctype", b"<html")):
            pass
    return data, ctype


def vol_info(vol_id, user_id):
    vol, (how, where, names) = pages_of(vol_id)
    t = _lib["titles"].get(vol["title"]) or {}
    vols = t.get("vols") or [vol_id]
    i = vols.index(vol_id) if vol_id in vols else 0
    u = _user(user_id)
    nxt = vols[i + 1] if i + 1 < len(vols) else None
    webtoon = bool(t) and _is_webtoon(t)
    # Long strips open in Long scroll unless you picked another way for this series
    mode = u["modes"].get(vol["title"]) or ({"mode": "scroll", "dir": "ltr"} if webtoon else None)
    return {
        "id": vol_id,
        "title": vol["title"],
        "series": t.get("name", ""),
        "name": vol["name"],
        "chapterName": vol.get("chapterName") or "",
        "unit": vol.get("unit") or "vol",
        "num": vol.get("num"),
        "pages": len(names),
        "pdf": how == "pdf",
        "rtl": t.get("rtl", False) and not webtoon,
        "webtoon": webtoon,
        "mode": mode,
        "place": u["place"].get(vol_id),
        "next": {"id": nxt, "name": _lib["vols"][nxt]["name"], "cover": cover_url(nxt)} if nxt else None,
        "prev": vols[i - 1] if i > 0 else None,
        "stamp": vol["added"],
    }


# ---------- Manga pages, shrunk for the screen ----------

_warm_lock = threading.Lock()
_warm_todo = []  # (vol id, page, width) waiting to be shrunk ahead of time
_warm_busy = [False]


def page_small(vol_id, n, w):
    """Page n of a chapter, shrunk to about w wide (made once with ffmpeg and kept)"""
    w = min(PAGE_WIDTHS, key=lambda x: abs(x - w))
    vol = _lib["vols"].get(vol_id)
    if not vol:
        raise BooksError(404, "That chapter isn't on Tower any more.")
    key = f"{vol_id}-{vol['added']}-p{n}"
    if _dir:
        out = os.path.join(_dir, "thumbs", f"{key}-{w}.jpg")
        if os.path.isfile(out):
            try:
                os.utime(out)  # still in use, so the monthly tidy keeps it
            except OSError:
                pass
            with open(out, "rb") as f:
                return f.read(), "image/jpeg"
    data, ctype = page_bytes(vol_id, n)
    small = _thumb(data, key, w)
    return small, ("image/jpeg" if small is not data else ctype)


def warm_pages(vol_id, n, w):
    """Gets the next few pages ready while you read this one, so the page turn doesn't wait on Tower"""
    if not _dir or not have_tool("ffmpeg"):
        return
    with _warm_lock:
        # Only the newest ask matters (someone flipping fast shouldn't queue a hundred pages)
        del _warm_todo[:]
        _warm_todo.extend((vol_id, n + i, w) for i in range(1, PAGES_AHEAD + 1))
        if _warm_busy[0]:
            return
        _warm_busy[0] = True
    threading.Thread(target=_warm_run, name="books-pages", daemon=True).start()


def _warm_run():
    try:
        while True:
            with _warm_lock:
                if not _warm_todo:
                    return
                vol_id, n, w = _warm_todo.pop(0)
            try:
                _, (_, _, names) = pages_of(vol_id)
                if 0 <= n < len(names):
                    page_small(vol_id, n, w)
            except Exception:
                pass
    finally:
        with _warm_lock:
            _warm_busy[0] = False


def tidy_page_copies(days=30):
    """Shrunk manga pages nobody has opened for a month are deleted (they're made again when needed)"""
    if not _dir:
        return
    folder = os.path.join(_dir, "thumbs")
    cut = time.time() - days * 86400
    gone = 0
    try:
        for name in os.listdir(folder):
            if not re.search(r"-p\d+-\d+\.jpg$", name):
                continue
            p = os.path.join(folder, name)
            try:
                if os.path.getmtime(p) < cut:
                    os.remove(p)
                    gone += 1
            except OSError:
                pass
    except OSError:
        return
    if gone:
        log(f"Tidied {gone} shrunk manga pages nobody opened for {days} days")


# ---------- Manga in Requests (Ben 2026-11-22, Idea Box 26 "manga") ----------
# Popular manga, genres and a page for each one from AniList, like the Anime tab. Asking goes to Ben (or straight
# through for Ben); a yes is written into Manga/.harbor/asks.json, and Harbor gets it from the manga sites by itself
# and writes what happened into Manga/.harbor/asks-done.json. Never on Kids profiles.

MANGA_GENRES = ["Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery", "Psychological", "Romance",
                "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller", "Mecha", "Music"]
_MR_FIELDS = """id title { romaji english native } synonyms description(asHtml: false) genres averageScore popularity
  volumes chapters status startDate { year } format countryOfOrigin isAdult coverImage { extraLarge large } bannerImage
  staff(perPage: 3) { edges { role node { name { full } } } }"""
_MR_LIST = ("query ($page: Int, $sort: [MediaSort], $genre: String, $status: MediaStatus, $q: String) { Page(page: $page, perPage: 24) { "
            "media(type: MANGA, isAdult: false, format_not: NOVEL, sort: $sort, genre: $genre, status: $status, search: $q) { " + _MR_FIELDS + " } } }")
# Glowfin new look (Ben 2026-11-22): "Trending on MyAnimeList" looks the MyAnimeList numbers up on AniList, so asking works the same
_MR_BY_MAL = ("query ($ids: [Int]) { Page(page: 1, perPage: 30) { media(type: MANGA, isAdult: false, idMal_in: $ids) { " + _MR_FIELDS + " idMal } } }")
_MR_ONE = "query ($id: Int) { Media(id: $id, type: MANGA) { " + _MR_FIELDS + " recommendations(perPage: 12, sort: RATING_DESC) { nodes { mediaRecommendation { " + _MR_FIELDS + " } } } } }"
_mr_cache = {}


def _anilist(query, variables, ttl=6 * 3600):
    key = query[:40] + json.dumps(variables, sort_keys=True)
    hit = _mr_cache.get(key)
    if hit and time.time() - hit[0] < ttl:
        return hit[1]
    body = json.dumps({"query": query, "variables": variables}).encode()
    j = _json("https://graphql.anilist.co", data=body, headers={"Content-Type": "application/json", "Accept": "application/json"})
    data = (j or {}).get("data") or {}
    if len(_mr_cache) > 400:
        _mr_cache.clear()
    _mr_cache[key] = (time.time(), data)
    return data


def _manga_here():
    """What's on Glowfin's Manga shelf: AniList numbers and plain names"""
    ids, names = set(), {}
    for t in _lib["titles"].values():
        if t["kind"] not in ("manga", "comic"):
            continue
        names[norm(t["name"])] = t["id"]
        al = t.get("anilist") or (_meta.get(meta_key(t)) or {}).get("anilist")
        if al:
            ids.add(int(al))
            names[f"#{al}"] = t["id"]
    return ids, names


def _manga_names(m):
    ti = m.get("title") or {}
    out = []
    for n in [ti.get("english"), ti.get("romaji")] + list(m.get("synonyms") or [])[:4]:
        if n and n not in out and all(ord(ch) < 0x3000 for ch in n):
            out.append(n)
    return out or [ti.get("native") or "Manga"]


def _manga_card(m, here=None, asks=None):
    here = here or _manga_here()
    names = _manga_names(m)
    shelf = here[1].get(f"#{m['id']}") or next((here[1].get(norm(n)) for n in names if here[1].get(norm(n))), None)
    a = (asks or {}).get(m["id"])
    return {
        "id": m["id"],
        "name": names[0],
        "cover": (m.get("coverImage") or {}).get("extraLarge") or (m.get("coverImage") or {}).get("large"),
        "year": (m.get("startDate") or {}).get("year"),
        "score": round(m["averageScore"] / 10, 1) if m.get("averageScore") else None,
        "genres": (m.get("genres") or [])[:3],
        "chapters": m.get("chapters"),
        "volumes": m.get("volumes"),
        "status": ANILIST_STATUS.get(m.get("status") or "", ""),
        "from": {"JP": "Japan", "KR": "Korea", "CN": "China", "TW": "Taiwan"}.get(m.get("countryOfOrigin") or "", ""),
        "here": shelf,
        "asked": _manga_ask_words(a) if a and not shelf else None,
        # Glowfin new look: the Requests top describes the manga you're on, with its banner behind (web)
        "desc": clean_html(m.get("description") or "")[:600],
        "banner": m.get("bannerImage"),
    }


def _asks_by_id():
    return {a["anilist"]: a for a in _state.setdefault("manga_asks", []) if a["state"] not in ("no",)}


def _manga_list(variables):
    here, asks = _manga_here(), _asks_by_id()
    media = (_anilist(_MR_LIST, variables).get("Page") or {}).get("media") or []
    return [_manga_card(m, here, asks) for m in media if not m.get("isAdult")]


_mal_trend = [0, []]


def _mal_trending_manga():
    """MyAnimeList's most popular manga coming out now (Jikan), as AniList cards in MyAnimeList's order (saved 6 hours)"""
    if time.time() - _mal_trend[0] < 6 * 3600 and _mal_trend[1]:
        return _mal_trend[1]
    try:
        j = _json("https://api.jikan.moe/v4/top/manga?filter=publishing&limit=25") or {}
        mal = [x.get("mal_id") for x in (j.get("data") or []) if x.get("mal_id") and x.get("type") not in ("Novel", "Light Novel")]
    except Exception:
        mal = []
    if not mal:
        return _mal_trend[1]
    here, asks = _manga_here(), _asks_by_id()
    media = (_anilist(_MR_BY_MAL, {"ids": mal}).get("Page") or {}).get("media") or []
    order = {m: i for i, m in enumerate(mal)}
    media = sorted((m for m in media if not m.get("isAdult")), key=lambda m: order.get(m.get("idMal"), 999))
    cards = [_manga_card(m, here, asks) for m in media]
    _mal_trend[0], _mal_trend[1] = time.time(), cards
    return cards


def manga_discover():
    rows = [
        # Glowfin new look (Ben 2026-11-22): "Trending from MyAnimeList and AniList would be good here too"
        ("Trending on AniList", {"sort": ["TRENDING_DESC"], "page": 1}),
        ("Most loved of all time", {"sort": ["POPULARITY_DESC"], "page": 1}),
        ("Top rated", {"sort": ["SCORE_DESC"], "page": 1}),
        ("Still coming out", {"sort": ["POPULARITY_DESC"], "status": "RELEASING", "page": 1}),
        ("Finished, read it all", {"sort": ["POPULARITY_DESC"], "status": "FINISHED", "page": 1}),
    ]
    out = [{"name": n, "items": _manga_list(v)} for n, v in rows]
    mal = _mal_trending_manga()
    if mal:
        out.insert(1, {"name": "Trending on MyAnimeList", "items": mal})
    return {"rows": out, "genres": MANGA_GENRES}


def manga_genre(genre, page=1):
    if genre not in MANGA_GENRES:
        raise BooksError(404, "No such genre.")
    return {"name": genre, "items": _manga_list({"sort": ["POPULARITY_DESC"], "genre": genre, "page": max(1, min(page, 20))})}


def manga_search(q):
    q = (q or "").strip()[:80]
    if len(q) < 2:
        return {"items": []}
    return {"items": _manga_list({"sort": ["SEARCH_MATCH"], "q": q, "page": 1})}


def manga_title(aid, is_admin):
    m = _anilist(_MR_ONE, {"id": int(aid)}).get("Media")
    if not m or m.get("isAdult"):
        raise BooksError(404, "That manga isn't on AniList.")
    here, asks = _manga_here(), _asks_by_id()
    out = _manga_card(m, here, asks)
    author = ""
    for e in ((m.get("staff") or {}).get("edges") or []):
        if "story" in (e.get("role") or "").lower() or not author:
            author = ((e.get("node") or {}).get("name") or {}).get("full") or author
    a = asks.get(m["id"])
    out.update({
        "author": author,
        "story": clean_html(m.get("description")),
        "banner": m.get("bannerImage"),
        "names": _manga_names(m),
        "askState": a["state"] if a and not out["here"] else None,
        "askId": a["id"] if a else None,
        "more": [_manga_card(r["mediaRecommendation"], here, asks) for r in ((m.get("recommendations") or {}).get("nodes") or [])
                 if r.get("mediaRecommendation") and not r["mediaRecommendation"].get("isAdult")][:12],
    })
    return out


def _manga_ask_words(a):
    return {
        "waiting": "Asked · waiting for Ben",
        "sent": "Getting it",
        "getting": "Getting it",
        "ready": "On Glowfin",
        "cant": "Couldn't find it on the manga sites",
        "no": "Ben said no",
    }.get(a["state"], "Getting it")


def _manga_ask_view(a, is_admin):
    out = {k: a.get(k) for k in ("id", "anilist", "name", "cover", "state", "who", "at", "title", "note")}
    out["words"] = _manga_ask_words(a)
    out["canRetry"] = is_admin and a["state"] == "cant"
    return out


def _write_harbor_asks():
    """The asks Harbor should get, in the file Harbor reads (Manga/.harbor/asks.json)"""
    dirs = section_dirs("manga")
    if not dirs:
        return
    path = os.path.join(dirs[0], ".harbor", "asks.json")
    with _lock:
        sent = [{"id": a["id"] + (f"-{a['tries']}" if a.get("tries") else ""), "anilist": a["anilist"], "title": a["name"], "titles": a.get("names") or [],
                 "asked": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(a["at"]))}
                for a in _state.setdefault("manga_asks", []) if a["state"] in ("sent", "getting", "cant", "ready")]
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path + ".tmp", "w", encoding="utf-8") as f:
            json.dump(sent, f, ensure_ascii=False, indent=1)
        os.replace(path + ".tmp", path)
    except OSError as e:
        log(f"Couldn't write Harbor's manga asks: {e}")


def manga_ask(user_id, user_name, is_admin, body):
    aid = int(body.get("id") or 0)
    if not aid:
        raise BooksError(400, "Which manga?")
    m = _anilist(_MR_ONE, {"id": aid}).get("Media")
    if not m or m.get("isAdult"):
        raise BooksError(404, "That manga isn't on AniList.")
    with _lock:
        for a in _state.setdefault("manga_asks", []):
            if a["anilist"] == aid and a["state"] not in ("no", "cant"):
                return {"ask": _manga_ask_view(a, is_admin), "words": "Already asked for."}
        a = {
            "id": uuid.uuid4().hex[:10],
            "anilist": aid,
            "name": _manga_names(m)[0],
            "names": _manga_names(m),
            "cover": (m.get("coverImage") or {}).get("large"),
            "user": user_id,
            "who": user_name,
            "at": int(time.time()),
            "state": "sent" if is_admin else "waiting",
        }
        _state["manga_asks"].insert(0, a)
        del _state["manga_asks"][200:]
    _save_state()
    if a["state"] == "sent":
        _write_harbor_asks()
    return {"ask": _manga_ask_view(a, is_admin), "words": "Harbor is getting it. It shows in Books > Manga as chapters arrive." if is_admin else "Asked Ben."}


def manga_answer(body):
    with _lock:
        a = next((x for x in _state.setdefault("manga_asks", []) if x["id"] == str(body.get("id") or "")), None)
        if not a:
            raise BooksError(404, "That request is gone.")
        a["state"] = "sent" if body.get("yes") else "no"
        a["at"] = int(time.time())
    _save_state()
    _write_harbor_asks()
    return {"ok": True}


def manga_retry(aid):
    with _lock:
        a = next((x for x in _state.setdefault("manga_asks", []) if x["id"] == aid), None)
        if not a:
            raise BooksError(404, "That request is gone.")
        a["state"], a["tries"], a["at"] = "sent", (a.get("tries") or 0) + 1, int(time.time())
        a.pop("note", None)
    _save_state()
    _write_harbor_asks()
    return {"ok": True}


def manga_forget(aid, user_id, is_admin):
    with _lock:
        a = next((x for x in _state.setdefault("manga_asks", []) if x["id"] == aid), None)
        if a and (is_admin or a.get("user") == user_id):
            _state["manga_asks"].remove(a)
    _save_state()
    _write_harbor_asks()
    return {"ok": True}


def manga_check():
    """Every few minutes: what Harbor did (asks-done.json), and which asks are on the Manga shelf now"""
    asks = _state.setdefault("manga_asks", [])
    if not any(a["state"] in ("sent", "getting") for a in asks):
        return
    done = {}
    for d in section_dirs("manga")[:1]:
        try:
            with open(os.path.join(d, ".harbor", "asks-done.json"), encoding="utf-8") as f:
                done = {x.get("id"): x for x in json.load(f) or []}
        except (OSError, ValueError):
            done = {}
    here = _manga_here()
    changed = False
    with _lock:
        for a in asks:
            if a["state"] not in ("sent", "getting"):
                continue
            shelf = here[1].get(f"#{a['anilist']}") or next((here[1].get(norm(n)) for n in a.get("names") or [a["name"]] if here[1].get(norm(n))), None)
            d = done.get(a["id"] + (f"-{a['tries']}" if a.get("tries") else "")) or {}
            if not shelf and d.get("shelf"):
                shelf = here[1].get(norm(d["shelf"]))
            if shelf:
                a.update(state="ready", title=shelf, readyAt=int(time.time()))
                changed = True
            elif d.get("state") == "got" and a["state"] != "getting":
                a["state"] = "getting"
                changed = True
            elif d.get("state") == "notfound" or (d.get("state") == "trouble" and (d.get("tries") or 0) >= 3):
                a.update(state="cant", note=d.get("note") or "")
                changed = True
    if changed:
        _save_state()


def manga_requests_view(user_id, is_admin):
    asks = _state.setdefault("manga_asks", [])
    out = {"mine": [_manga_ask_view(a, is_admin) for a in asks if a.get("user") == user_id and a["state"] != "no"][:60]}
    if is_admin:
        out["waiting"] = [_manga_ask_view(a, True) for a in asks if a["state"] == "waiting"]
        out["others"] = [_manga_ask_view(a, True) for a in asks if a.get("user") != user_id and a["state"] not in ("waiting", "no")][:60]
    return out


def _manga_route(method, path, q, user_id, is_admin, is_kids, read_json, user_name):
    if is_kids:
        raise BooksError(404, "That isn't on Glowfin.")
    if method == "GET" and path == "/api/books/manga/discover":
        return manga_discover()
    if method == "GET" and path == "/api/books/manga/genre":
        return manga_genre(q("g"), int(q("page") or 1))
    if method == "GET" and path == "/api/books/manga/search":
        return manga_search(q("q"))
    m = re.fullmatch(r"/api/books/manga/title/(\d+)", path)
    if method == "GET" and m:
        return manga_title(m.group(1), is_admin)
    if method == "GET" and path == "/api/books/manga/requests":
        return manga_requests_view(user_id, is_admin)
    if method == "POST" and path == "/api/books/manga/ask":
        return manga_ask(user_id, user_name, is_admin, read_json())
    if method == "POST" and path == "/api/books/manga/answer":
        if not is_admin:
            raise BooksError(403, "Only Ben can say yes or no.")
        return manga_answer(read_json())
    m = re.fullmatch(r"/api/books/manga/requests/(\w+)/retry", path)
    if method == "POST" and m and is_admin:
        return manga_retry(m.group(1))
    m = re.fullmatch(r"/api/books/manga/requests/(\w+)", path)
    if method == "DELETE" and m:
        return manga_forget(m.group(1), user_id, is_admin)
    return None


def _kids_blocked(path):
    """Ben 2026-11-21: no manga on Kids profiles at all, so a Kids profile can't open manga by its link either"""
    # Security check 2026-11-24: a Kids profile also can't open a grown-up book by its link (the file, the
    # EPUB pages or the cover), only the kids' books it can see in its library
    m = re.match(r"/api/books/(?:title|next|cover|vol|file|epub|x|place|opened|marks|send)/(\w+)", path)
    if not m:
        return False
    key = m.group(1)
    if key in _lib["vols"]:
        return True
    t = _lib["titles"].get(key)
    return bool(t) and not kids_ok(t)


def status():
    return {
        "titles": len(_lib["titles"]),
        "scanned": _lib["scanned"],
        "lookedUp": len(_meta),
        "chaptarr": bool(CH_URL),
        "folders": {k: [os.path.relpath(p, MEDIA) for p in section_dirs(k)] for k, _ in SECTIONS},
        "tools": {"ffmpeg": have_tool("ffmpeg"), "7z": have_tool("7z")},
        "log": _log_lines[-30:],
    }


def route(method, path, query, token, user_id, is_admin, is_kids, read_json, user_name="", for_person=None):
    """The helper sends /api/books/... here. Returns (status, answer) for JSON, or (status, bytes, content type),
    or ("file", path, content type) for big files the helper streams (with Range for seeking)."""
    q = lambda k: (query.get(k) or [""])[0].strip()  # noqa: E731
    # A link to a second copy that was folded into another one opens the one that shows
    path = re.sub(r"(?<=/)(\w{13})(?=/|$)", lambda mm: _doubles.get(mm.group(1), mm.group(1)), path)
    try:
        if is_kids and _kids_blocked(path):
            return 404, {"error": "That isn't on Glowfin."}
        if path.startswith("/api/books/manga/"):
            answer = _manga_route(method, path, q, user_id, is_admin, is_kids, read_json, user_name)
            if answer is not None:
                return 200, answer
        if method == "GET" and path == "/api/books/home":
            return 200, home(user_id, is_kids)
        if method == "GET" and path == "/api/books/library":
            return 200, library(user_id, is_kids)
        if method == "GET" and path == "/api/books/series":
            return 200, series_list(user_id, is_kids)
        if method == "GET" and path == "/api/books/authors":
            return 200, authors_list(user_id, is_kids)
        if method == "GET" and path == "/api/books/news":
            # Home has no news any more (Ben 2026-11-18); older apps get an empty list
            return 200, {"news": []}
        if method == "GET" and path == "/api/books/stats":
            return 200, stats(user_id, int(q("days") or 7))
        if method == "GET" and path == "/api/books/status":
            return 200, status()
        m = re.fullmatch(r"/api/books/title/(\w+)", path)
        if method == "GET" and m:
            return 200, title_page(m.group(1), user_id, is_kids, is_admin)
        m = re.fullmatch(r"/api/books/next/(\w+)", path)
        if method == "GET" and m:
            return 200, what_next(m.group(1), user_id, is_kids)
        m = re.fullmatch(r"/api/books/cover/(\w+)", path)
        if method in ("GET", "HEAD") and m:
            data, ctype = cover(m.group(1), int(q("w") or 320))
            return 200, data, ctype
        m = re.fullmatch(r"/api/books/vol/(\w+)", path)
        if method == "GET" and m:
            return 200, vol_info(m.group(1), user_id)
        m = re.fullmatch(r"/api/books/vol/(\w+)/p/(\d+)", path)
        if method in ("GET", "HEAD") and m:
            w = int(q("w") or 0)
            if not w:
                data, ctype = page_bytes(m.group(1), int(m.group(2)))
                return 200, data, ctype
            data, ctype = page_small(m.group(1), int(m.group(2)), w)
            if w > 200 and q("ahead") != "0":
                warm_pages(m.group(1), int(m.group(2)), w)
            return 200, data, ctype
        m = re.fullmatch(r"/api/books/file/(\w+)(?:/(\d+))?", path)
        if method in ("GET", "HEAD") and m:
            p, ctype = file_for(m.group(1), int(m.group(2) or 0))
            return "file", p, ctype
        m = re.fullmatch(r"/api/books/epub/(\w+)", path)
        if method == "GET" and m:
            return 200, epub_info(m.group(1))
        m = re.fullmatch(r"/api/books/x/(\w+)/(.+)", path)
        if method in ("GET", "HEAD") and m:
            data, ctype = epub_part(m.group(1), m.group(2))
            return 200, data, ctype
        m = re.fullmatch(r"/api/books/handoff/(\w+)", path)
        if method == "POST" and m:
            return 200, handoff(user_id, m.group(1))
        m = re.fullmatch(r"/api/books/place/(\w+)", path)
        if method in ("POST", "PUT") and m:
            return 200, save_place(user_id, m.group(1), read_json())
        if method == "POST" and path == "/api/books/mark":
            return 200, mark(user_id, read_json())
        if method == "POST" and path == "/api/books/want":
            return 200, set_want(user_id, read_json())
        m = re.fullmatch(r"/api/books/opened/(\w+)", path)
        if method == "POST" and m:
            return 200, opened(user_id, m.group(1))
        m = re.fullmatch(r"/api/books/send/(\w+)", path)
        if method == "POST" and m:
            return 200, send_to_phone(user_id, m.group(1))
        m = re.fullmatch(r"/api/books/marks/(\w+)", path)
        if m and method == "GET":
            return 200, marks_of(user_id, m.group(1))
        if m and method == "POST":
            return 200, save_mark(user_id, m.group(1), read_json())
        if path == "/api/books/kindle" and method == "GET":
            return 200, kindle_info(user_id, is_admin)
        if path == "/api/books/kindle" and method == "POST":
            if is_kids:
                return 403, {"error": "Ask a grown-up to set this up."}
            return 200, kindle_address(user_id, read_json())
        m = re.fullmatch(r"/api/books/kindle/(\w+)", path)
        if m and method == "POST":
            if is_kids:
                return 403, {"error": "Ask a grown-up to send this one."}
            return 200, send_to_kindle(user_id, m.group(1))
        if booklists is not None and (path.startswith("/api/books/discover") or path.startswith("/api/books/about") or path.startswith("/api/books/lists/")):
            if is_kids:
                return 403, {"error": "Ask a grown-up to get this book."}
            got = booklists.route(method, path, query, user_id, is_admin, read_json)
            if got is not None:
                return got
        if method == "GET" and path == "/api/books/search":
            return 200, search(q("q"), is_kids)
        if method == "GET" and path == "/api/books/requests":
            return 200, requests_view(user_id, is_admin)
        if method == "POST" and path == "/api/books/request":
            if is_kids:
                return 403, {"error": "Ask a grown-up to get this book."}
            return 200, ask(user_id, user_name, is_admin, read_json(), for_person=for_person)
        m = re.fullmatch(r"/api/books/request/(\w+)", path)
        if method == "DELETE" and m:
            return 200, forget(m.group(1), user_id, is_admin)
        if method == "POST" and path == "/api/books/rescan":
            _kick.set()
            return 200, {"ok": True, "words": "Tower is looking through the book folders again."}
        # Only Ben from here on
        if not is_admin:
            return 403, {"error": "Only Ben can do that."}
        if method == "POST" and path == "/api/books/kindle/mail":
            return 200, kindle_mail(read_json())
        if method == "POST" and path == "/api/books/answer":
            return 200, answer(read_json())
        m = re.fullmatch(r"/api/books/retry/(\w+)", path)
        if method == "POST" and m:
            return 200, retry(m.group(1))
        m = re.fullmatch(r"/api/books/covers/(\w+)", path)
        if method == "GET" and m:
            return 200, cover_choices(m.group(1))
        if method == "POST" and path == "/api/books/cover":
            return 200, pick_cover(read_json())
        m = re.fullmatch(r"/api/books/cover/(\w+)", path)
        if method == "DELETE" and m:
            return 200, reset_cover(m.group(1))
        if method == "GET" and path == "/api/books/kids":
            return 200, kids_list()
        if method == "POST" and path == "/api/books/kids":
            return 200, kids_allow(read_json())
        return 404, {"error": "Not found"}
    except BooksError as e:
        return e.status, {"error": e.message}
