"""
Benflix Books: manga, comics, ebooks and audiobooks, all from Tower's own folders.

How it works, in plain words:
- Tower looks through Homelab/Media/Books, Audiobooks, Manga and Comics every few minutes and makes a list
  of everything there. Benflix (TV, phone and browser) reads that list from here.
    * Books: every .epub or .pdf (other ebook files are listed too, but only EPUB and PDF open in Benflix).
      The title and author come from the book itself (or a metadata.opf file next to it),
      else from the folder names (Books/Author/Title).
    * Audiobooks: each folder of sound files is one audiobook (Audiobooks/Author/Author - Title), or one
      .m4b file. The FOLDER names win over the song info inside the files, because downloaded audiobooks
      often say the narrator is the "artist". Each file is a chapter (or the chapters inside one .m4b).
    * Manga and comics: Manga/Series/ with one file (.cbz, .zip, .cbr, .pdf) or one folder of pictures
      per volume or chapter.
- Descriptions, genres, years and scores come from Open Library (books) and AniList / MyAnimeList (manga),
  looked up once and saved.
- Your place in every book, what you want to read and what you've finished are saved here for each
  profile, so they follow you from the TV to the phone to the browser.
- Asking for a book: Benflix searches Open Library, and the request goes to Chaptarr (which finds an
  ebook and/or audiobook the same way Sonarr and Radarr do). Ben's requests go straight through; anyone
  else's wait for Ben to say yes. Manga requests go on a "Manga to add" list for Ben.

Settings from the setup command: CHAPTARR_URL and CHAPTARR_KEY (Chaptarr, the Readarr-style app Ben picked,
which keeps ebooks in /data/Books and audiobooks in /data/Audiobooks, renamed into Author/Title folders).
Saved in /booksdata (Tower's appdata/benflix-books). Only uses the Python standard library, plus
ffmpeg/ffprobe (sound file lengths, chapters, small cover pictures) and 7z (for .cbr files) when present.
"""

import hashlib
import html
import json
import os
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

MEDIA = "/media"
SECTIONS = (
    ("book", ("Books", "Ebooks", "eBooks")),
    ("audiobook", ("Audiobooks", "AudioBooks", "Audio Books")),
    ("manga", ("Manga",)),
    ("comic", ("Comics",)),
)
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
NEW_DAYS = 14  # "New for you" keeps a title this long unless it's opened
NEWS_DAYS = 3  # Home news pills last this long
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
        _probe_saved = _read(os.path.join(_dir, "probe.json"), {})
        for k, v in _probe_saved.items():
            _probe[k] = tuple(v)
    else:
        log("No folder to save in, so places and requests won't be kept. Run the Benflix setup command again.")
    threading.Thread(target=_loop, name="books", daemon=True).start()


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
            for k in ("place", "done", "want", "finished", "opened", "modes", "sent", "time"):
                u.setdefault(k, {})
            u["_id"] = uid
            _users[uid] = u
        return u


def _save_user(u):
    if _dir:
        with _lock:
            _write(os.path.join(_dir, "users", u["_id"] + ".json"), {k: v for k, v in u.items() if not k.startswith("_")})


def _loop():
    last_scan = 0
    last_meta = 0
    last_ll = 0
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
                last_ll = time.time()
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


def _vol_number(name):
    n = name.lower()
    m = re.search(r"(?:\bv|\bvol\.?|\bvolume|\btome|\bbook)\s*0*(\d+(?:\.\d+)?)", n)
    if m:
        return float(m.group(1)), "vol"
    m = re.search(r"(?:\bc|\bch\.?|\bchapter|#)\s*0*(\d+(?:\.\d+)?)", n)
    if m:
        return float(m.group(1)), "ch"
    nums = re.findall(r"(?<![\d.])(\d{1,4}(?:\.\d+)?)(?![\d.])", re.sub(r"\(.*?\)|\[.*?\]", "", n))
    nums = [x for x in nums if not (len(x) == 4 and 1900 < float(x) < 2100)]
    return (float(nums[-1]), "vol") if nums else (None, "vol")


def _scan_pages(root, kind, titles, vols):
    try:
        entries = sorted(os.listdir(root), key=natural)
    except OSError:
        return
    for series_name in entries:
        sdir = os.path.join(root, series_name)
        if series_name.startswith("."):
            continue
        items = []
        if os.path.isfile(sdir) and os.path.splitext(series_name)[1].lower() in PAGES:
            # A single file straight in Manga/ is a one-shot
            items = [(series_name, sdir)]
            series_title = tidy_name(os.path.splitext(series_name)[0])
        elif os.path.isdir(sdir):
            series_title = tidy_name(series_name)
            for folder, dirs, files in os.walk(sdir):
                dirs.sort(key=natural)
                pics = [f for f in files if os.path.splitext(f)[1].lower() in PICS]
                docs = [f for f in files if os.path.splitext(f)[1].lower() in PAGES]
                for f in sorted(docs, key=natural):
                    items.append((f, os.path.join(folder, f)))
                if pics and not docs and folder != sdir:
                    items.append((os.path.basename(folder), folder))
                elif pics and not docs and folder == sdir and not dirs:
                    items.append((series_name, folder))
        else:
            continue
        if not items:
            continue
        series_title = _strip_year(series_title) or series_title
        sid = ("m" if kind == "manga" else "c") + _hid(kind, os.path.relpath(sdir, MEDIA))
        vol_list = []
        for i, (name, path) in enumerate(items):
            num, unit = _vol_number(os.path.splitext(name)[0] if os.path.isfile(path) else name)
            vid = "v" + _hid("vol", os.path.relpath(path, MEDIA))
            label = ("Vol " if unit == "vol" else "Ch ") + (f"{num:g}" if num is not None else str(i + 1))
            vols[vid] = {
                "id": vid,
                "title": sid,
                "path": path,
                "name": label,
                "num": num if num is not None else float(i + 1),
                "unit": unit,
                "ext": os.path.splitext(path)[1].lower() if os.path.isfile(path) else "",
                "added": _mtime(path),
                "size": os.path.getsize(path) if os.path.isfile(path) else 0,
            }
            vol_list.append(vid)
        vol_list.sort(key=lambda v: (vols[v]["unit"] != "vol", vols[v]["num"]))
        titles[sid] = {
            "id": sid,
            "kind": kind,
            "name": series_title,
            "author": "",
            "series": series_title,
            "seriesIndex": None,
            "year": year_of(series_name),
            "folder": sdir,
            "vols": vol_list,
            "readable": True,
            "size": sum(vols[v]["size"] for v in vol_list),
            "added": max(vols[v]["added"] for v in vol_list),
            "desc": "",
            "subjects": [],
            "rtl": kind == "manga",
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
        raise BooksError(422, "This volume is a .cbr file, and Tower can't open those yet. Run the Benflix setup command again.")
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


# ---------- Looking things up (Open Library, AniList, MyAnimeList) ----------


def meta_key(t):
    if t["kind"] in ("manga", "comic"):
        return t["kind"] + ":" + norm(t["name"])
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
            info = look_up_manga(t["name"]) if t["kind"] in ("manga", "comic") else look_up_book(t["name"], t["author"], t.get("isbn"))
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


ANILIST_Q = """query ($q: String) { Page(perPage: 5) { media(search: $q, type: MANGA, sort: SEARCH_MATCH) {
  id idMal title { romaji english } description(asHtml: false) genres tags { name rank isAdult } averageScore
  volumes chapters status startDate { year } isAdult format countryOfOrigin
  coverImage { extraLarge large } staff(perPage: 3) { edges { role node { name { full } } } } } } }"""


def look_up_manga(name):
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
        "status": best.get("status"),
        "author": author,
        "adult": bool(best.get("isAdult")),
        "origin": best.get("countryOfOrigin"),
        "english": (best.get("title") or {}).get("english"),
    }
    g = set(x.lower() for x in info["subjects"])
    info["kids"] = (not info["adult"]) and bool(g & {"kids", "kodomo", "children"}) and not (g & set(ADULT_WORDS))
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


def kids_ok(t):
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
    unit = "Vol" if _lib["vols"][vols[0]]["unit"] == "vol" else "Ch"
    return f"{_lib['vols'][vols[at]]['name']} of {len(vols)}" if unit == "Vol" else f"{_lib['vols'][vols[at]]['name']} of {len(vols)} chapters"


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
        line = _vol_line(t, u) if status != "not" else f"{len(t['vols'])} " + ("volumes" if len(t["vols"]) != 1 else "volume")
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
        "author": t["author"] or clean_author(m.get("author")),
        "series": t.get("series") or clean_series(m.get("series"), t["name"]),
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
    return {
        "titles": tiles,
        "counts": kinds,
        "vols": {k: sum(x["vols"] for x in tiles if x["kind"] == k) for k in ("manga", "comic")},
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


def home(user_id, is_kids):
    """The rows on Books Home: On deck, New for you, Want to read, Recently added"""
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
    want = [t for t in tiles.values() if t["want"]]
    asked = []
    for key, w in sorted(u["want"].items(), key=lambda kv: -(kv[1].get("at") or 0)):
        if key.startswith(("ol:", "al:")):
            asked.append({"id": key, "kind": w.get("kind") or "book", "name": w.get("name") or "", "author": w.get("author") or "", "cover": w.get("cover"), "notHere": True, "status": "not", "progress": 0, "line": w.get("author") or ""})
    recent = _one_per_book(sorted(tiles.values(), key=lambda t: -t["added"]))[:30]
    rows = [
        {"key": "deck", "title": "On deck", "tiles": out[:30]},
        {"key": "new", "title": "New for you", "tiles": newf},
        {"key": "want", "title": "Want to read", "tiles": want + asked},
        {"key": "recent", "title": "Recently added", "tiles": recent},
    ]
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


def title_page(tid, user_id, is_kids, is_admin):
    with _lock:
        t = _lib["titles"].get(tid)
    if not t or not visible(is_kids, t):
        raise BooksError(404, "That isn't on Benflix any more.")
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
        place = u["place"].get(tid) or {}
        for v in t["vols"]:
            vol = _lib["vols"][v]
            vp = (u["place"].get(v) or {})
            out["volumes"].append(
                {
                    "id": v,
                    "name": vol["name"],
                    "done": v in u["done"],
                    "progress": 1.0 if v in u["done"] else (vp.get("pct") or 0),
                    "current": v == place.get("vol"),
                    "cover": cover_url(v),
                    "new": vol["added"] > max(u["opened"].get(tid, 0), time.time() - NEW_DAYS * 86400) and u["opened"].get(tid) is not None,
                    "pdf": vol["ext"] == ".pdf",
                }
            )
        nxt = next((x for x in out["volumes"] if x["current"] and not x["done"]), None) or next((x for x in out["volumes"] if not x["done"]), None)
        out["next"] = nxt["id"] if nxt else (out["volumes"][0]["id"] if out["volumes"] else None)
        if out["volumes"]:
            out["line"] = f"{len(out['volumes'])} " + ("volumes" if _lib["vols"][t["vols"][0]]["unit"] == "vol" else "chapters")
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
            groups.setdefault(("m", t["id"]), {"name": t["name"], "kind": t["kind"], "titles": [t], "cover": t["cover"]})
        elif t["series"] and not (t["kind"] == "audiobook" and t.get("linked")):
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
        raise BooksError(404, "That isn't on Benflix any more.")
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
            raise BooksError(404, "That isn't on Benflix any more.")
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
        raise BooksError(404, "That isn't on Benflix any more.")
    with _lock:
        u["sent"][tid] = int(time.time())
    _save_user(u)
    return {"ok": True, "words": "Sent. It's at the front of On deck on your phone."}


def opened(user_id, tid):
    u = _user(user_id)
    with _lock:
        u["opened"][tid] = int(time.time())
    _save_user(u)
    return {"ok": True}


def news(user_id, is_kids):
    """Home's news pills: a new volume of a series you read, and a book you asked for being ready"""
    u = _user(user_id)
    out = []
    cutoff = time.time() - NEWS_DAYS * 86400
    for t in list(_lib["titles"].values()):
        if t["kind"] in ("manga", "comic") and any(v in u["done"] or v in u["place"] for v in t["vols"]) and visible(is_kids, t):
            fresh = [v for v in t["vols"] if _lib["vols"][v]["added"] > cutoff and v not in u["done"]]
            if fresh:
                v = _lib["vols"][fresh[-1]]
                out.append({"id": "bk-" + fresh[-1], "text": f"{t['name']} {v['name']} arrived", "title": t["id"], "at": v["added"]})
    for r in _state["requests"]:
        if r.get("user") == user_id and r.get("readyAt", 0) > cutoff and r.get("title"):
            out.append({"id": "bkr-" + r["id"], "text": f"{r['name']} is ready", "title": r["title"], "at": r["readyAt"]})
    for tid, at in u["sent"].items():
        if at > cutoff and tid in _lib["titles"]:
            out.append({"id": f"bks-{tid}-{at}", "text": f"{_lib['titles'][tid]['name']} is ready on your phone", "title": tid, "at": at})
    out.sort(key=lambda n: -n["at"])
    return {"news": out[:3]}


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
    """Ben's cover picker: covers of every edition (books) or every volume (manga), labelled"""
    t = _lib["titles"].get(tid) or (_lib["titles"].get(_lib["vols"][tid]["title"]) if tid in _lib["vols"] else None)
    if not t:
        raise BooksError(404, "No such book.")
    out = []
    if t["kind"] in ("manga", "comic"):
        out = _mangadex_covers(t)
        m = meta_of(t)
        if m.get("cover"):
            out.insert(0, {"url": m["cover"], "label": "AniList", "edition": "anilist"})
    else:
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


def _mangadex_covers(t):
    try:
        found = _json("https://api.mangadex.org/manga?" + urllib.parse.urlencode({"title": t["name"], "limit": 1}))
        data = (found or {}).get("data") or []
        if not data:
            return []
        mid = data[0]["id"]
        covers = _json(f"https://api.mangadex.org/cover?manga[]={mid}&limit=100&order[volume]=asc")
        out = []
        for c in (covers or {}).get("data") or []:
            a = c.get("attributes") or {}
            f = a.get("fileName")
            if not f:
                continue
            loc = a.get("locale") or ""
            out.append(
                {
                    "url": f"https://uploads.mangadex.org/covers/{mid}/{f}",
                    "thumb": f"https://uploads.mangadex.org/covers/{mid}/{f}.256.jpg",
                    "label": ("Vol " + a["volume"] if a.get("volume") else "Cover") + (f" · {loc.upper()}" if loc else ""),
                    "edition": loc or "unknown",
                    "volume": a.get("volume"),
                }
            )
        return out
    except Exception as e:
        log(f"MangaDex covers for {t['name']}: {e!r}")
        return []


def pick_cover(body):
    """Saves Ben's cover pick. scope 'all' = every volume or book in the series gets the same edition's cover"""
    tid = str(body.get("id") or "")
    url = str(body.get("url") or "")
    edition = str(body.get("edition") or "")
    scope = body.get("scope") or "one"
    if not _dir:
        raise BooksError(503, "Tower has no folder to save covers in. Run the Benflix setup command again.")
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
        if m.get("adult"):
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
    """Books to ask for (Open Library) and manga (AniList), marked when they're already on Benflix"""
    text = (text or "").strip()
    if len(text) < 2:
        return {"books": [], "manga": []}
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
    manga = []
    if not is_kids:
        try:
            body = json.dumps({"query": "query ($q: String) { Page(perPage: 8) { media(search: $q, type: MANGA, isAdult: false) { id title { romaji english } startDate { year } coverImage { large } volumes averageScore staff(perPage: 1) { edges { node { name { full } } } } } } }", "variables": {"q": text}}).encode()
            j = _json("https://graphql.anilist.co", data=body, headers={"Content-Type": "application/json"})
            for m in ((j or {}).get("data") or {}).get("Page", {}).get("media") or []:
                name = (m.get("title") or {}).get("english") or (m.get("title") or {}).get("romaji") or ""
                here = have.get(norm(name)) or have.get(norm((m.get("title") or {}).get("romaji")))
                edges = (m.get("staff") or {}).get("edges") or []
                manga.append(
                    {
                        "key": f"al:{m['id']}",
                        "name": name,
                        "author": ((edges[0].get("node") or {}).get("name") or {}).get("full", "") if edges else "",
                        "year": (m.get("startDate") or {}).get("year"),
                        "cover": (m.get("coverImage") or {}).get("large"),
                        "score": round(m["averageScore"] / 10, 1) if m.get("averageScore") else None,
                        "volumes": m.get("volumes"),
                        "have": here["id"] if here else None,
                        "asked": _asked_words(f"al:{m['id']}"),
                    }
                )
        except Exception as e:
            log(f"Manga search: {e!r}")
    return {"books": books, "manga": manga}


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
        "manga": "On Ben's manga list",
        "no": "Ben said no",
        "problem": "Waiting for Tower",
    }.get(r["state"], "Searching")


def request_view(r, is_admin):
    out = {k: r.get(k) for k in ("id", "key", "name", "author", "year", "cover", "types", "state", "title", "who", "at", "problem")}
    out["words"] = words_for(r)
    out["canRetry"] = r["state"] in ("cant", "problem") and is_admin
    return out


def ask(user_id, user_name, is_admin, body):
    key = str(body.get("key") or "")[:60]
    if not key:
        raise BooksError(400, "Which book?")
    types = [x for x in (body.get("types") or ["ebook", "audiobook"]) if x in ("ebook", "audiobook")] or ["ebook", "audiobook"]
    manga = key.startswith("al:")
    with _lock:
        for r in _state["requests"]:
            if r["key"] == key and r["state"] not in ("no", "cant"):
                if not manga:
                    r["types"] = sorted(set(r["types"]) | set(types))
                return {"request": request_view(r, is_admin), "words": "Already asked for."}
        r = {
            "id": uuid.uuid4().hex[:10],
            "key": key,
            "name": str(body.get("name") or "")[:200],
            "author": str(body.get("author") or "")[:200],
            "year": body.get("year"),
            "cover": body.get("cover"),
            "types": types if not manga else ["manga"],
            "user": user_id,
            "who": user_name,
            "at": int(time.time()),
            "state": "manga" if manga and is_admin else "waiting" if not is_admin else "searching",
        }
        _state["requests"].insert(0, r)
        del _state["requests"][300:]
    _save_state()
    if r["state"] == "searching":
        threading.Thread(target=_send, args=(r,), daemon=True).start()
        words = "Asked for. Tower is looking for it."
    elif r["state"] == "manga":
        words = "Added to your manga list."
    else:
        words = "Asked Ben."
    # Asking also puts it on your Want to read list
    set_want(user_id, {"id": key, "name": r["name"], "author": r["author"], "cover": r["cover"], "kind": "manga" if manga else "book"})
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
        elif r["key"].startswith("al:"):
            r["state"] = "manga"
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


def ch(method, path, body=None):
    if not CH_URL or not CH_KEY:
        raise BooksError(503, "Chaptarr isn't connected yet. Run the Benflix setup command again.")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        CH_URL + path, data=data, method=method,
        headers={"X-Api-Key": CH_KEY, "Accept": "application/json", "Content-Type": "application/json", "User-Agent": UA},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
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
        meta=(meta[0].get("id") if meta else 1),
    )
    return _ch_setup


def _send_to_chaptarr(r):
    want_t, want_a = norm(r["name"]), norm(r["author"].split(",")[0])
    found = ch("GET", "/api/v1/book/lookup?" + urllib.parse.urlencode({"term": f"{r['name']} {r['author']}".strip()})) or []
    if not found:
        found = ch("GET", "/api/v1/book/lookup?" + urllib.parse.urlencode({"term": r["name"]})) or []

    def score(b):
        bt = norm(b.get("title"))
        ba = norm((b.get("author") or {}).get("authorName") or b.get("authorTitle") or "")
        s = 3 if bt == want_t else 1 if want_t and (want_t in bt or bt in want_t) else 0
        return s + (2 if want_a and (want_a in ba or ba in want_a) else 0)

    if not found or score(max(found, key=score)) < 2:
        raise BooksError(404, "Chaptarr couldn't find that exact book.")
    best = max(found, key=score)
    d = _ch_defaults()
    kinds = r["types"]
    folder = d["ebook"] if "ebook" in kinds else d["audio"]
    book_id = best.get("id") or 0
    if not book_id:
        book = dict(best)
        author = dict(book.get("author") or {})
        author.update(
            qualityProfileId=d["ebook_quality"] if "ebook" in kinds else d["audio_quality"],
            metadataProfileId=d["meta"],
            rootFolderPath=folder["path"],
            monitored=True,
            addOptions={"monitor": "none", "searchForMissingBooks": False},
        )
        # Chaptarr keeps an ebook switch and an audiobook switch on each book, so "Both" turns both on
        book.update(
            author=author, monitored=True, addOptions={"searchForNewBook": True},
            ebookMonitored="ebook" in kinds, audiobookMonitored="audiobook" in kinds,
        )
        try:
            added = ch("POST", "/api/v1/book", book) or {}
            book_id = added.get("id") or 0
        except BooksError as e:
            if e.status != 409:
                raise
            # Already in Chaptarr: find it and just search again
            mine = ch("GET", "/api/v1/book") or []
            hit = next((b for b in mine if b.get("foreignBookId") == best.get("foreignBookId") or norm(b.get("title")) == norm(best.get("title"))), None)
            book_id = (hit or {}).get("id") or 0
    if not book_id:
        raise BooksError(502, "Chaptarr didn't keep the book.")
    try:
        book = ch("GET", f"/api/v1/book/{book_id}") or {}
        if not book.get("monitored"):
            ch("PUT", "/api/v1/book/monitor", {"bookIds": [book_id], "monitored": True})
        # Already in Chaptarr (maybe as the other format): switch on the format(s) asked for, keep what was on
        want_e = "ebook" in kinds and book.get("ebookMonitored") is False
        want_a = "audiobook" in kinds and book.get("audiobookMonitored") is False
        if want_e or want_a:
            book.update(monitored=True)
            if want_e:
                book["ebookMonitored"] = True
            if want_a:
                book["audiobookMonitored"] = True
            ch("PUT", f"/api/v1/book/{book_id}", book)
    except BooksError:
        pass
    ch("POST", "/api/v1/command", {"name": "BookSearch", "bookIds": [book_id]})
    with _lock:
        r["ch"] = book_id
        r["state"] = "searching"
        r.pop("problem", None)
    log(f"Asked Chaptarr for {r['name']} ({', '.join(kinds)})")


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
        if not r.get("ch"):
            continue
        new = r["state"]
        if r["ch"] in downloading:
            new = "downloading"
        else:
            try:
                b = ch("GET", f"/api/v1/book/{r['ch']}") or {}
                if ((b.get("statistics") or {}).get("bookFileCount") or 0) > 0:
                    new = "downloading" if not r.get("title") else "ready"
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
    for r in active:
        if r["state"] == "problem" and not r.get("ch") and can_ask():
            _send(r)
    changed = _check_chaptarr(active) if CH_URL else False
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
        if r["state"] in ("ready", "no", "waiting"):
            continue
        hits = have.get(norm(r["name"])) or []
        if r["state"] == "manga":
            hits = [t for t in hits if t["kind"] in ("manga", "comic")]
        else:
            hits = [t for t in hits if t["kind"] in ("book", "audiobook")]
        if not hits:
            continue
        kinds = {t["kind"] for t in hits}
        need = {"book" if k == "ebook" else k for k in r["types"] if k != "manga"}
        if r["state"] == "manga" or need <= kinds or (need and kinds and time.time() - r["at"] > 3 * 86400):
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
    mine = [request_view(r, is_admin) for r in _state["requests"] if r.get("user") == user_id and r["state"] != "no"]
    out = {"mine": mine[:60], "connected": can_ask(), "problems": []}
    if is_admin:
        out["waiting"] = [request_view(r, True) for r in _state["requests"] if r["state"] == "waiting"]
        out["others"] = [request_view(r, True) for r in _state["requests"] if r.get("user") != user_id and r["state"] not in ("waiting", "no")][:60]
        out["mangaToAdd"] = [request_view(r, True) for r in _state["requests"] if r["state"] == "manga"]
        if not can_ask():
            out["problems"].append("Chaptarr isn't connected. Set it up, then run the Benflix setup command again.")
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
        raise BooksError(404, "That isn't on Benflix any more.")
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
    return {
        "id": vol_id,
        "title": vol["title"],
        "series": t.get("name", ""),
        "name": vol["name"],
        "pages": len(names),
        "pdf": how == "pdf",
        "rtl": t.get("rtl", False),
        "mode": u["modes"].get(vol["title"]),
        "place": u["place"].get(vol_id),
        "next": {"id": nxt, "name": _lib["vols"][nxt]["name"], "cover": cover_url(nxt)} if nxt else None,
        "prev": vols[i - 1] if i > 0 else None,
    }


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


def route(method, path, query, token, user_id, is_admin, is_kids, read_json, user_name=""):
    """The helper sends /api/books/... here. Returns (status, answer) for JSON, or (status, bytes, content type),
    or ("file", path, content type) for big files the helper streams (with Range for seeking)."""
    q = lambda k: (query.get(k) or [""])[0].strip()  # noqa: E731
    # A link to a second copy that was folded into another one opens the one that shows
    path = re.sub(r"(?<=/)(\w{13})(?=/|$)", lambda mm: _doubles.get(mm.group(1), mm.group(1)), path)
    try:
        if method == "GET" and path == "/api/books/home":
            return 200, home(user_id, is_kids)
        if method == "GET" and path == "/api/books/library":
            return 200, library(user_id, is_kids)
        if method == "GET" and path == "/api/books/series":
            return 200, series_list(user_id, is_kids)
        if method == "GET" and path == "/api/books/authors":
            return 200, authors_list(user_id, is_kids)
        if method == "GET" and path == "/api/books/news":
            return 200, news(user_id, is_kids)
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
            data, ctype = page_bytes(m.group(1), int(m.group(2)))
            w = int(q("w") or 0)
            if w:
                data, ctype = _thumb(data, f"{m.group(1)}-p{m.group(2)}", min(THUMB_WIDTHS, key=lambda x: abs(x - w))), "image/jpeg"
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
        if method == "GET" and path == "/api/books/search":
            return 200, search(q("q"), is_kids)
        if method == "GET" and path == "/api/books/requests":
            return 200, requests_view(user_id, is_admin)
        if method == "POST" and path == "/api/books/request":
            if is_kids:
                return 403, {"error": "Ask a grown-up to get this book."}
            return 200, ask(user_id, user_name, is_admin, read_json())
        m = re.fullmatch(r"/api/books/request/(\w+)", path)
        if method == "DELETE" and m:
            return 200, forget(m.group(1), user_id, is_admin)
        if method == "POST" and path == "/api/books/rescan":
            _kick.set()
            return 200, {"ok": True, "words": "Tower is looking through the book folders again."}
        # Only Ben from here on
        if not is_admin:
            return 403, {"error": "Only Ben can do that."}
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
