"""
Benflix Books requests: the rows on Requests > Books (and each book's details page).

How it works, in plain words:
- Once a day Tower gathers the lists and keeps them, so the TV, phone and browser open the page straight away:
    * NYT best sellers (Fiction, Nonfiction and the monthly audiobook lists) from the New York Times Books API.
      Needs Ben's free NYT key, typed once on the Server page (Tools > Book lists).
    * Top 10 books right now: what Hardcover readers added most this week (Hardcover's own trending list).
      Needs Ben's free Hardcover token, typed the same way. Without it, Open Library's weekly list stands in.
    * Top on Apple Books: Apple's top paid books chart in the US (a free feed, no key needed).
    * Trending this week: Open Library's weekly trending list (no key needed).
    * Award winners: a fixed list of the last five years of big book prizes (covers looked up once).
    * Next in your series and New from authors you read: worked out from the books already on Benflix
      (Hardcover for the series order, Open Library for authors' new books).
    * Genre pages: Open Library's trending books for each genre.
- Book of the week is the #1 NYT Fiction book, picked each Monday and kept for the week.
- Books already on Benflix are left out of the rows. Asking for a book goes to Chaptarr (benflix_books.py).

Saved in the Books folder (/booksdata) as lists.json, about.json and keys.json (the two keys stay on Tower).
Only uses the Python standard library.
"""

import datetime
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "Benflix-Books/1.0 (home media server)"
NYT = "https://api.nytimes.com/svc/books/v3"
HC = "https://api.hardcover.app/v1/graphql"
OL = "https://openlibrary.org"

NYT_LISTS = (
    ("fiction", "combined-print-and-e-book-fiction"),
    ("nonfiction", "combined-print-and-e-book-nonfiction"),
    ("audioFiction", "audio-fiction"),
    ("audioNonfiction", "audio-nonfiction"),
)

# The 12 tiles on the page (Ben picked 12), then the rest on "All genres". (slug, name, Open Library subject, colour)
GENRES = [
    ("fantasy", "Fantasy", "fantasy", "#8B7CF6"),
    ("scifi", "Science fiction", "science fiction", "#4FB3E8"),
    ("mystery", "Mystery", "mystery", "#E8B04F"),
    ("thriller", "Thriller", "thriller", "#E86F4F"),
    ("romance", "Romance", "romance", "#F07AA8"),
    ("horror", "Horror", "horror", "#B84A4A"),
    ("historical", "Historical fiction", "historical fiction", "#C9A27A"),
    ("memoir", "Biography & memoir", "biography", "#6FCF97"),
    ("ya", "Young adult", "young adult fiction", "#5ED1C1"),
    ("selfhelp", "Self-help", "self-help", "#F2C94C"),
    ("history", "History", "history", "#A0896B"),
    ("science", "Science", "popular science", "#56CCF2"),
    # All genres
    ("truecrime", "True crime", "true crime", "#9B6B6B"),
    ("humor", "Humor", "humor", "#F2994A"),
    ("classics", "Classics", "classic literature", "#BFA37C"),
    ("graphic", "Graphic novels", "graphic novels", "#BB6BD9"),
    ("adventure", "Adventure", "adventure", "#27AE60"),
    ("business", "Business & money", "business", "#7F8C8D"),
    ("psychology", "Psychology", "psychology", "#9B8AE0"),
    ("poetry", "Poetry", "poetry", "#E0A3C8"),
    ("cooking", "Cooking", "cooking", "#E57E5B"),
    ("travel", "Travel", "travel", "#4EA8DE"),
    ("philosophy", "Philosophy", "philosophy", "#8E9AAF"),
    ("religion", "Religion & spirituality", "spirituality", "#D4B483"),
]
GENRE_BY_SLUG = {g[0]: g for g in GENRES}

# Award winners, last five years (prize, year, title, author, genre slugs for the genre pages)
AWARDS = [
    ("Booker Prize", 2025, "Flesh", "David Szalay", ()),
    ("Hugo Award", 2025, "The Tainted Cup", "Robert Jackson Bennett", ("fantasy", "mystery")),
    ("Nebula Award", 2025, "Someone You Can Build a Nest In", "John Wiswell", ("fantasy",)),
    ("Pulitzer Prize", 2025, "James", "Percival Everett", ("historical",)),
    ("National Book Award", 2024, "James", "Percival Everett", ("historical",)),
    ("Booker Prize", 2024, "Orbital", "Samantha Harvey", ("scifi",)),
    ("Hugo Award", 2024, "Some Desperate Glory", "Emily Tesh", ("scifi",)),
    ("Nebula Award", 2024, "The Saint of Bright Doors", "Vajra Chandrasekera", ("fantasy",)),
    ("Pulitzer Prize", 2024, "Night Watch", "Jayne Anne Phillips", ("historical",)),
    ("Edgar Award", 2024, "The River We Remember", "William Kent Krueger", ("mystery",)),
    ("Booker Prize", 2023, "Prophet Song", "Paul Lynch", ()),
    ("Hugo Award", 2023, "Nettle & Bone", "T. Kingfisher", ("fantasy",)),
    ("Nebula Award", 2023, "Babel", "R. F. Kuang", ("fantasy", "historical")),
    ("Pulitzer Prize", 2023, "Demon Copperhead", "Barbara Kingsolver", ()),
    ("Pulitzer Prize", 2023, "Trust", "Hernan Diaz", ("historical",)),
    ("National Book Award", 2023, "Blackouts", "Justin Torres", ()),
    ("Edgar Award", 2023, "Notes on an Execution", "Danya Kukafka", ("mystery", "thriller")),
    ("Booker Prize", 2022, "The Seven Moons of Maali Almeida", "Shehan Karunatilaka", ("mystery",)),
    ("Hugo Award", 2022, "A Desolation Called Peace", "Arkady Martine", ("scifi",)),
    ("Nebula Award", 2022, "A Master of Djinn", "P. Djèlí Clark", ("fantasy", "mystery")),
    ("Pulitzer Prize", 2022, "The Netanyahus", "Joshua Cohen", ()),
    ("National Book Award", 2022, "The Rabbit Hutch", "Tess Gunty", ()),
    ("Edgar Award", 2022, "Five Decembers", "James Kestrel", ("mystery", "historical")),
    ("Booker Prize", 2021, "The Promise", "Damon Galgut", ()),
    ("Hugo Award", 2021, "Network Effect", "Martha Wells", ("scifi",)),
    ("Nebula Award", 2021, "Network Effect", "Martha Wells", ("scifi",)),
    ("Pulitzer Prize", 2021, "The Night Watchman", "Louise Erdrich", ("historical",)),
    ("National Book Award", 2021, "Hell of a Book", "Jason Mott", ()),
    ("Edgar Award", 2021, "Djinn Patrol on the Purple Line", "Deepa Anappara", ("mystery",)),
]

OL_FIELDS = "key,title,author_name,first_publish_year,cover_i,ratings_average,ratings_count,subject,number_of_pages_median,isbn,language"

_lock = threading.RLock()
_kick = threading.Event()
_books = None  # the benflix_books module (library, profiles, requests)
_dir = None
_keys = {"nyt": "", "hardcover": ""}
_health = {"nyt": {}, "hardcover": {}}
_lists = {}  # what the daily gather found
_about = {}  # key -> a book's details (looked up when someone opens it, kept a week)
_genre_cache = {}  # slug -> (time, rows) for genres not gathered every day
_hc_calls = []  # times of recent Hardcover calls (60 a minute at most)


def log(text):
    if _books:
        _books.log("lists: " + text)
    else:
        print("[books lists] " + text, flush=True)


# ---------- Starting and saving ----------


def start(books_module, data_dir):
    global _books, _dir
    _books = books_module
    _dir = data_dir
    if _dir:
        _keys.update(_read("keys.json", {}))
        _lists.update(_read("lists.json", {}))
        _about.update(_read("about.json", {}))
        _health.update(_read("health.json", {}))
    threading.Thread(target=_loop, name="book-lists", daemon=True).start()


def _read(name, default):
    if not _dir:
        return default
    try:
        with open(os.path.join(_dir, name), encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def _write(name, data):
    if not _dir:
        return
    path = os.path.join(_dir, name)
    tmp = path + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        os.replace(tmp, path)
    except OSError as e:
        log(f"Couldn't save {name}: {e}")


def _loop():
    time.sleep(20)  # let the library scan start first
    while True:
        try:
            age = time.time() - (_lists.get("at") or 0)
            if age > 23 * 3600 or _kick.is_set() or not _lists.get("at"):
                _kick.clear()
                gather()
            _week_pick()
        except Exception as e:  # keep going whatever happens
            log(f"Problem: {e!r}")
        _kick.wait(1800)


# ---------- Asking the sites ----------


def _get(url, data=None, headers=None, timeout=25):
    h = {"User-Agent": UA, "Accept": "application/json"}
    h.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=h)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        raw = r.read()
    return json.loads(raw) if raw else None


def _nyt(path):
    key = _keys.get("nyt") or ""
    if not key:
        return None
    sep = "&" if "?" in path else "?"
    return _get(NYT + path + sep + "api-key=" + urllib.parse.quote(key))


_hc_style = {"bearer": None}  # which way of sending the key Hardcover took last time


def _hc_headers():
    """The ways to send the key: older tokens go as "Bearer <token>", newer API keys may go as they are"""
    tok = re.sub(r"\s+", " ", (_keys.get("hardcover") or "").strip())
    if not tok:
        return []
    raw = tok[7:].strip() if tok.lower().startswith("bearer ") else tok
    ways = [(True, "Bearer " + raw), (False, raw)]
    if _hc_style["bearer"] is False:
        ways.reverse()
    return ways


def hc(query, variables=None):
    """One Hardcover question (GraphQL). None when there's no token."""
    ways = _hc_headers()
    if not ways:
        return None
    with _lock:
        now = time.time()
        _hc_calls[:] = [t for t in _hc_calls if now - t < 60]
        wait = 0 if len(_hc_calls) < 50 else 60 - (now - _hc_calls[0])
        _hc_calls.append(now + max(0, wait))
    if wait > 0:
        time.sleep(wait)
    body = json.dumps({"query": query, "variables": variables or {}}).encode()
    j, last = None, None
    for bearer, auth in ways:
        try:
            j = _get(HC, data=body, headers={"Content-Type": "application/json", "authorization": auth}, timeout=30) or {}
            _hc_style["bearer"] = bearer
            break
        except urllib.error.HTTPError as e:
            if e.code not in (401, 403):
                raise
            try:
                e.detail = e.read()[:300].decode("utf-8", "replace")
            except Exception:
                e.detail = ""
            last = e
    if j is None:
        raise last
    if j.get("errors"):
        raise RuntimeError("Hardcover: " + str((j["errors"][0] or {}).get("message") or j["errors"][0])[:200])
    return j.get("data") or {}


def _ol(path, params=None):
    if path == "/search.json" and params is not None:
        params = dict(params, lang="en")  # English titles and covers when a book has editions in many languages
    url = OL + path + ("?" + urllib.parse.urlencode(params) if params else "")
    return _get(url) or {}


# ---------- Turning answers into cards ----------


def _title_case(t):
    t = str(t or "").strip()
    if t and t.upper() == t:
        small = {"a", "an", "the", "of", "and", "or", "in", "on", "at", "to", "for", "by", "with", "from"}
        words = t.lower().split()
        t = " ".join(w if (i and w in small) else w[:1].upper() + w[1:] for i, w in enumerate(words))
        t = re.sub(r"(?<=[\-'’])([a-z])", lambda m: m.group(1), t)
    return t


def _ol_card(d, **extra):
    key = (d.get("key") or "").rsplit("/", 1)[-1]
    if not key:
        return None
    authors = d.get("author_name") or d.get("authors") or []
    if authors and isinstance(authors[0], dict):
        authors = [a.get("name") for a in authors if a.get("name")]
    c = {
        "key": "ol:" + key,
        "name": d.get("title") or "",
        "author": ", ".join(authors[:2]),
        "year": d.get("first_publish_year"),
        "cover": f"https://covers.openlibrary.org/b/id/{d['cover_i']}-L.jpg" if d.get("cover_i") else None,
        "score": round(d["ratings_average"], 1) if d.get("ratings_average") else None,
        "votes": d.get("ratings_count") or 0,
        "pages": d.get("number_of_pages_median"),
        "subjects": [s for s in (d.get("subject") or [])[:30]],
    }
    c.update({k: v for k, v in extra.items() if v is not None})
    return c


def _hc_author(b):
    names = []
    for c in b.get("contributions") or []:
        n = ((c or {}).get("author") or {}).get("name")
        role = ((c or {}).get("contribution") or "").lower()
        if n and (not role or role == "author") and n not in names:
            names.append(n)
    if not names:
        names = [n for n in (b.get("author_names") or []) if n][:2]
    return ", ".join(names[:2])


def _hc_genres(b):
    tags = b.get("cached_tags") or {}
    if isinstance(tags, str):
        try:
            tags = json.loads(tags)
        except ValueError:
            tags = {}
    out = []
    for t in (tags.get("Genre") or []) if isinstance(tags, dict) else []:
        name = t.get("tag") if isinstance(t, dict) else t
        if name and name not in out:
            out.append(name)
    return out[:8]


def _hc_card(b, **extra):
    if not b or not b.get("title"):
        return None
    img = b.get("image")
    cover = (img or {}).get("url") if isinstance(img, dict) else img
    series = None
    for s in b.get("book_series") or b.get("featured_series") and [b["featured_series"]] or []:
        if (s or {}).get("series"):
            series = {"name": s["series"].get("name"), "pos": s.get("position")}
            break
    c = {
        "key": "hc:" + str(b.get("id")),
        "name": b.get("title"),
        "author": _hc_author(b),
        "year": b.get("release_year"),
        "cover": cover,
        "score": round(float(b["rating"]), 1) if b.get("rating") else None,
        "votes": b.get("ratings_count") or 0,
        "readers": b.get("users_count") or b.get("users_read_count") or 0,
        "pages": b.get("pages"),
        "audioMinutes": round(b["audio_seconds"] / 60) if b.get("audio_seconds") else None,
        "desc": (b.get("description") or "").strip() or None,
        "genres": _hc_genres(b),
        "series": series,
        "hc": b.get("id"),
        "slug": b.get("slug"),
    }
    c.update({k: v for k, v in extra.items() if v is not None})
    return c


HC_BOOK = """id title slug description release_year pages audio_seconds rating ratings_count users_count cached_tags
  image { url } contributions(limit: 4) { contribution author { name } } book_series(limit: 2) { position series { id name } }"""


def hc_books(ids):
    if not ids:
        return []
    d = hc("query ($ids: [Int!]) { books(where: {id: {_in: $ids}}) { " + HC_BOOK + " } }", {"ids": [int(i) for i in ids]})
    by_id = {b.get("id"): b for b in (d or {}).get("books") or []}
    return [by_id[i] for i in ids if i in by_id]


def hc_search(text, per_page=5):
    d = hc(
        "query ($q: String!, $n: Int!) { search(query: $q, query_type: \"Book\", per_page: $n, page: 1) { results } }",
        {"q": text, "n": per_page},
    )
    res = ((d or {}).get("search") or {}).get("results") or {}
    if isinstance(res, str):
        try:
            res = json.loads(res)
        except ValueError:
            res = {}
    return [(h or {}).get("document") or {} for h in res.get("hits") or []]


_NOT_LATIN = re.compile(r"[\u0370-\u03ff\u0400-\u052f\u0590-\u06ff\u0900-\u0dff\u0e00-\u0eff\u3040-\u30ff\u3400-\u9fff\uac00-\ud7af]")
_ACCENTS = re.compile(r"[ąćęłńśźżđőűøåæßñãõçěščřžůýäöüìòùõ]", re.I)


def _english_rank(title):
    """How English a title looks: 2 = plain English letters, 1 = Latin letters with foreign accents
    (a German or Polish copy), 0 = another alphabet (Cyrillic, Greek, Japanese ...)"""
    t = str(title or "")
    if not t or _NOT_LATIN.search(t):
        return 0
    return 1 if _ACCENTS.search(t) else 2


def hc_series_books(name):
    """A series in order from Hardcover, one book per place: the English book most people read.
    Hardcover lists every translation as its own book at the same place, so the most-read
    English-looking one wins (that's the original edition, with its English cover)."""
    d = hc(
        "query ($n: String!) { series(where: {name: {_eq: $n}}, limit: 1, order_by: {books_count: desc}) { id name "
        "book_series(order_by: {position: asc}, limit: 300) { position book { id title users_count } } } }",
        {"n": name},
    )
    found = ((d or {}).get("series") or [None])[0]
    best = {}
    for bs in (found or {}).get("book_series") or []:
        pos = bs.get("position")
        b = bs.get("book") or {}
        if pos is None or not b.get("id") or float(pos) != int(float(pos)) or int(float(pos)) < 1:
            continue  # skip "1.5" novellas
        p = int(float(pos))
        rank = (_english_rank(b.get("title")), b.get("users_count") or 0)
        if p not in best or rank > best[p][0]:
            best[p] = (rank, b)
    rows = [(p, best[p][1]["id"]) for p in sorted(best) if best[p][0][0]]
    full = {b.get("id"): _hc_card(b) for b in hc_books([i for _p, i in rows][:30])}
    return [dict(full[i], pos=p) for p, i in rows if full.get(i)]


# ---------- The daily gather ----------


def gather():
    log("Gathering the book lists")
    out = {"at": int(time.time())}
    old = dict(_lists)
    # NYT (5 a minute at most, so a short wait between lists)
    if _keys.get("nyt"):
        ok = False
        for i, (name, code) in enumerate(NYT_LISTS):
            try:
                if i:
                    time.sleep(13)
                j = _nyt(f"/lists/current/{code}.json") or {}
                res = j.get("results") or {}
                cards = []
                for b in res.get("books") or []:
                    cards.append(_nyt_card(b, res))
                out["nyt_" + name] = [c for c in cards if c]
                out["nyt_" + name + "_date"] = res.get("published_date")
                ok = True
            except Exception as e:
                log(f"NYT {code}: {e!r}")
                out["nyt_" + name] = old.get("nyt_" + name) or []
                _set_health("nyt", False, _nyt_problem(e))
        if ok:
            _set_health("nyt", True, None)
    # Hardcover: the Top 10 (what readers added most this week) and a pool for genres
    if _keys.get("hardcover"):
        try:
            today = datetime.date.today()
            d = hc(
                "query ($from: date!, $to: date!) { books_trending(from: $from, to: $to, limit: 60, offset: 0) { ids } }",
                {"from": str(today - datetime.timedelta(days=7)), "to": str(today)},
            )
            ids = ((d or {}).get("books_trending") or {}).get("ids") or []
            books = []
            for i in range(0, len(ids), 25):
                books += hc_books(ids[i : i + 25])
            out["hc_trending"] = [c for c in (_hc_card(b) for b in books) if c]
            _set_health("hardcover", True, None)
        except Exception as e:
            log(f"Hardcover trending: {e!r}")
            out["hc_trending"] = old.get("hc_trending") or []
            _set_health("hardcover", False, _hc_problem(e))
    # Apple Books top paid chart (US, free feed, no key)
    try:
        j = _get("https://rss.applemarketingtools.com/api/v2/us/books/top-paid/50/books.json") or {}
        out["apple_top"] = [c for c in (_apple_card(r, i + 1) for i, r in enumerate((j.get("feed") or {}).get("results") or [])) if c]
    except Exception as e:
        log(f"Apple Books chart: {e!r}")
        out["apple_top"] = old.get("apple_top") or []
    # Open Library weekly trending (no key)
    try:
        j = _ol("/trending/weekly.json", {"limit": "80"})
        out["ol_trending"] = [c for c in (_ol_card(w) for w in j.get("works") or []) if c]
    except Exception as e:
        log(f"Open Library trending: {e!r}")
        out["ol_trending"] = old.get("ol_trending") or []
    # Award winners (covers looked up once, then kept)
    awards = []
    known = {(a.get("name"), a.get("award")): a for a in old.get("awards") or []}
    for prize, year, title, author, slugs in AWARDS:
        c = known.get((title, f"{prize} {year}"))
        if not c or not c.get("cover"):
            c = _find_ol(title, author) or {"key": "ol:" + re.sub(r"\W+", "-", title.lower()), "name": title, "author": author}
            time.sleep(0.6)
        c.update(name=title, author=author, award=f"{prize} {year}", genreSlugs=list(slugs))
        awards.append(c)
    out["awards"] = awards
    # Your series and your authors (from what's on Benflix)
    out["series_next"] = _gather_series(old.get("series_next") or {})
    out["author_new"] = _gather_authors(old.get("author_new") or {})
    # The 12 genre tiles: each genre's trending books
    genres = {}
    for slug, _name, subject, _col in GENRES[:12]:
        try:
            genres[slug] = _gather_genre(subject)
        except Exception as e:
            log(f"Genre {slug}: {e!r}")
            genres[slug] = (old.get("genres") or {}).get(slug) or {}
        time.sleep(0.8)
    out["genres"] = genres
    out["botw"] = old.get("botw")
    with _lock:
        _lists.clear()
        _lists.update(out)
    _week_pick()
    _write("lists.json", _lists)
    log("Book lists ready")


def _nyt_problem(e):
    if isinstance(e, urllib.error.HTTPError) and e.code in (401, 403):
        return "The NYT key didn't work. Check it was copied whole and the Books API is turned on for it."
    if isinstance(e, urllib.error.HTTPError) and e.code == 429:
        return "NYT said 'too many asks today'. Tower tries again tomorrow."
    return f"Couldn't reach the NYT ({e.__class__.__name__})."


def _hc_problem(e):
    if isinstance(e, urllib.error.HTTPError) and e.code in (401, 403):
        detail = re.sub(r"\s+", " ", str(getattr(e, "detail", "") or ""))[:160]
        return (f"Hardcover said no ({e.code}{': ' + detail if detail else ''}). "
                "Check the key was copied whole and has read:catalog ticked.")
    if isinstance(e, urllib.error.HTTPError) and e.code == 429:
        return "Hardcover asked Tower to slow down. It tries again later."
    return f"Couldn't reach Hardcover ({str(e)[:120]})."


def _set_health(which, ok, problem):
    with _lock:
        _health[which] = {"ok": ok, "problem": problem, "at": int(time.time())}
    _write("health.json", _health)


def _nyt_card(b, res):
    isbn = b.get("primary_isbn13") or b.get("primary_isbn10") or ""
    name = _title_case(b.get("title"))
    if not name:
        return None
    c = {
        "key": "isbn:" + isbn if isbn else "nyt:" + re.sub(r"\W+", "-", name.lower()),
        "name": name,
        "author": b.get("author") or "",
        "cover": b.get("book_image") or (f"https://covers.openlibrary.org/b/isbn/{isbn}-L.jpg" if isbn else None),
        "desc": (b.get("description") or "").strip() or None,
        "rank": b.get("rank"),
        "weeks": b.get("weeks_on_list") or 0,
        "isbn": isbn,
        "publisher": b.get("publisher"),
        "nytList": res.get("display_name") or res.get("list_name"),
    }
    return c


def _apple_card(r, rank):
    name = (r.get("name") or "").strip()
    if not name:
        return None
    art = r.get("artworkUrl100") or ""
    return {
        "key": "ab:" + str(r.get("id") or re.sub(r"\W+", "-", name.lower())),
        "name": name,
        "author": r.get("artistName") or "",
        "cover": re.sub(r"/\d+x\d+(bb)?\.", "/600x900bb.", art) if art else None,
        "year": int(r["releaseDate"][:4]) if str(r.get("releaseDate") or "")[:4].isdigit() else None,
        "rank": rank,
        "genres": [g.get("name") for g in r.get("genres") or [] if g.get("name") and g.get("name") != "Books"],
    }


def _find_ol(title, author):
    try:
        q = {"title": title, "limit": "3", "fields": OL_FIELDS}
        if author:
            q["author"] = author
        docs = _ol("/search.json", q).get("docs") or []
        if docs:
            return _ol_card(docs[0])
    except Exception as e:
        log(f"Open Library {title}: {e!r}")
    return None


def _gather_genre(subject):
    year = datetime.date.today().year
    top = _ol("/search.json", {"q": f'subject:"{subject}" language:eng', "sort": "trending", "limit": "40", "fields": OL_FIELDS}).get("docs") or []
    time.sleep(0.6)
    new = _ol(
        "/search.json",
        {"q": f'subject:"{subject}" language:eng first_publish_year:[{year - 2} TO {year}]', "sort": "trending", "limit": "30", "fields": OL_FIELDS},
    ).get("docs") or []
    return {
        "top": [c for c in (_ol_card(d) for d in top) if c],
        "trending": [c for c in (_ol_card(d) for d in new) if c],
    }


def genre_lists(slug):
    """A genre's lists: from the daily gather for the 12 tiles, looked up when first opened for the rest"""
    g = GENRE_BY_SLUG.get(slug)
    if not g:
        return None
    got = (_lists.get("genres") or {}).get(slug)
    if got:
        return got
    with _lock:
        hit = _genre_cache.get(slug)
    if hit and time.time() - hit[0] < 86400:
        return hit[1]
    rows = _gather_genre(g[2])
    with _lock:
        _genre_cache[slug] = (time.time(), rows)
    return rows


def _library_titles():
    with _books._lock:
        return [t for t in _books._lib["titles"].values() if t["kind"] in ("book", "audiobook")]


def _gather_series(old):
    """For each series on Benflix: the next few books (from Hardcover's series order)"""
    if not _keys.get("hardcover"):
        return {}
    series = {}
    for t in _library_titles():
        s = t.get("series")
        if s:
            series.setdefault(s, max(series.get(s, 0), float(t.get("seriesIndex") or 0)))
    out = {}
    for name in sorted(series)[:60]:
        prev = old.get(name)
        if prev and prev.get("v") == 2 and time.time() - prev.get("at", 0) < 6 * 86400:
            out[name] = prev
            continue
        try:
            books = hc_series_books(name)
            out[name] = {"at": int(time.time()), "v": 2, "books": books}
        except Exception as e:
            log(f"Series {name}: {e!r}")
            if prev:
                out[name] = prev
    return out


def _gather_authors(old):
    """New books (this year and last) by the authors on Benflix"""
    year = datetime.date.today().year
    authors = {}
    for t in _library_titles():
        a = (t.get("author") or "").split(",")[0].strip()
        if a:
            authors[a] = max(authors.get(a, 0), t.get("added") or 0)
    out = {}
    for a in sorted(authors, key=lambda x: -authors[x])[:50]:
        prev = old.get(a)
        if prev and time.time() - prev.get("at", 0) < 3 * 86400:
            out[a] = prev
            continue
        try:
            docs = _ol(
                "/search.json",
                {"q": f'author:"{a}" language:eng first_publish_year:[{year - 1} TO {year + 1}]', "sort": "new", "limit": "6", "fields": OL_FIELDS},
            ).get("docs") or []
            out[a] = {"at": int(time.time()), "books": [c for c in (_ol_card(d) for d in docs) if c]}
            time.sleep(0.6)
        except Exception as e:
            log(f"Author {a}: {e!r}")
            if prev:
                out[a] = prev
    return out


def _monday(d=None):
    d = d or datetime.date.today()
    return str(d - datetime.timedelta(days=d.weekday()))


def _week_pick():
    """Book of the week: the #1 NYT Fiction book, picked on Monday and kept all week"""
    week = _monday()
    cur = _lists.get("botw") or {}
    fic = _lists.get("nyt_fiction") or []
    if cur.get("week") == week and cur.get("card"):
        return
    if fic:
        with _lock:
            _lists["botw"] = {"week": week, "card": fic[0]}
        _write("lists.json", _lists)


# ---------- What the page shows ----------


def _have_index():
    have = {}
    for t in _library_titles():
        have.setdefault(_books.norm(t["name"]), t["id"])
    return have


def _asked(c):
    words = _books._asked_words(c["key"])
    if words:
        return words
    n = _books.norm(c.get("name"))
    for r in _books._state["requests"]:
        if r["state"] != "no" and _books.norm(r.get("name")) == n:
            return _books.words_for(r)
    return None


def _view(c, have, **extra):
    """A card for the apps (None when the book is already on Benflix)"""
    if not c or not c.get("name"):
        return None
    if _books.norm(c["name"]) in have:
        return None
    out = {k: c.get(k) for k in ("key", "name", "author", "year", "cover", "score", "rank", "weeks", "award", "audioMinutes")}
    out["asked"] = _asked(c)
    out.update(extra)
    return out


def _row(cards, have, limit=30, seen=None, **extra):
    """A row's books, each only once (the apps need every book in a row to be different; a book that won
    two prizes, like James, shows once with both prizes)"""
    out = []
    here = {}
    for c in cards or []:
        v = _view(c, have, **extra)
        if not v:
            continue
        n = _books.norm(v["name"])
        twin = here.get(v["key"]) or here.get(n)
        if twin is not None:
            if v.get("award") and v["award"] not in (twin.get("award") or ""):
                twin["award"] = (twin["award"] + " · " if twin.get("award") else "") + v["award"]
            continue
        if seen is not None:
            if n in seen:
                continue
            seen.add(n)
        here[v["key"]] = here[n] = v
        out.append(v)
        if len(out) >= limit:
            break
    return out


def _user_reading(user_id):
    """Series and authors this profile has started or finished (else everything on Benflix)"""
    u = _books._user(user_id)
    titles = _library_titles()
    started = [t for t in titles if _books._status_of(t, u)[0] != "not"]
    pool = started or titles
    series = {}
    for t in pool:
        if t.get("series"):
            series[t["series"]] = max(series.get(t["series"], 0), float(t.get("seriesIndex") or 0))
    authors = [a for a in dict.fromkeys((t.get("author") or "").split(",")[0].strip() for t in sorted(pool, key=lambda x: -(x.get("added") or 0))) if a]
    owned = {}
    for t in titles:
        if t.get("series"):
            owned.setdefault(t["series"], set()).add(float(t.get("seriesIndex") or 0))
    return series, authors, owned


def _next_in_series(user_id, have, genre_words=None):
    series, _authors, owned = _user_reading(user_id)
    found = _lists.get("series_next") or {}
    out = []
    for name in series:
        books = (found.get(name) or {}).get("books") or []
        nxt = [b for b in books if b.get("pos") and b["pos"] not in owned.get(name, set())]
        for b in nxt[:2]:
            if genre_words and not _matches(b, genre_words):
                continue
            v = _view(b, have, line=f"{name}, book {b['pos']}")
            if v:
                out.append(v)
    return out[:30]


def _new_from_authors(user_id, have, genre_words=None):
    _series, authors, _owned = _user_reading(user_id)
    found = _lists.get("author_new") or {}
    out, seen = [], set()
    for a in authors:
        for b in (found.get(a) or {}).get("books") or []:
            if genre_words and not _matches(b, genre_words):
                continue
            v = _view(b, have)
            if v and _books.norm(v["name"]) not in seen:
                seen.add(_books.norm(v["name"]))
                out.append(v)
    return out[:30]


def _matches(c, words):
    text = " ".join((c.get("subjects") or []) + (c.get("genres") or [])).lower()
    return any(w in text for w in words)


def _genre_tiles():
    return [{"slug": s, "name": n, "color": col} for s, n, _subj, col in GENRES]


def page(user_id):
    """Requests > Books, top to bottom (Ben's approved order)"""
    have = _have_index()
    seen = set()
    botw = (_lists.get("botw") or {}).get("card")
    hero = None
    if botw:
        # Shown even when it's already on Benflix (then it says "On Benflix" instead of Ask for it)
        hero = _view(botw, {})
        hero.update(desc=botw.get("desc"), line="#1 NYT best seller · Fiction", have=have.get(_books.norm(botw.get("name"))))
    top_src = _lists.get("hc_trending") or _lists.get("ol_trending") or []
    top10 = _row(top_src, have, limit=10, seen=seen)
    rows = []

    def add(key, title, items, **kw):
        if items:
            rows.append(dict({"key": key, "title": title, "items": items}, **kw))

    add("series", "Next in your series", _next_in_series(user_id, have))
    add("authors", "New from authors you read", _new_from_authors(user_id, have))
    add("nytf", "NYT best sellers · Fiction", _row(_lists.get("nyt_fiction"), have), ranked=True)
    add("nytn", "NYT best sellers · Nonfiction", _row(_lists.get("nyt_nonfiction"), have), ranked=True)
    add("apple", "Top on Apple Books", _row(_lists.get("apple_top"), have), ranked=True)
    trend = _row(_lists.get("ol_trending"), have, limit=30, seen=seen)
    add("trending", "Trending this week", trend)
    rows.append({"key": "genres", "title": "Browse by genre", "genres": _genre_tiles()[:12], "items": []})
    add("awards", "Award winners", _row(_lists.get("awards"), have, limit=40))
    audio = _row((_lists.get("nyt_audioFiction") or []) + (_lists.get("nyt_audioNonfiction") or []), have, limit=30, audioOnly=True)
    if not audio:
        audio = _row([c for c in _lists.get("hc_trending") or [] if c.get("audioMinutes")], have, limit=30, audioOnly=True)
    add("audio", "Audiobooks people love", audio, audioOnly=True)
    return {
        "hero": hero,
        "top10": top10,
        "rows": rows,
        "gatheredAt": _lists.get("at"),
        "missing": [w for w, k in (("NYT", "nyt"), ("Hardcover", "hardcover")) if not _keys.get(k)],
    }


def genre_page(slug, user_id):
    g = GENRE_BY_SLUG.get(slug)
    if not g:
        raise _books.BooksError(404, "No such genre.")
    lists = genre_lists(slug) or {}
    have = _have_index()
    words = [w for w in {g[2], g[1].lower().split(" ")[0]}]
    seen = set()
    # A Hardcover top pick for this genre when the trending pool has some, else Open Library's
    hc_pool = [c for c in _lists.get("hc_trending") or [] if _matches(c, words)]
    top = _row(hc_pool + (lists.get("top") or []), have, limit=10, seen=seen)
    hero = dict(top[0], line=f"Pick of the week · {g[1]}") if top else None
    rows = []

    def add(key, title, items, **kw):
        if items:
            rows.append(dict({"key": key, "title": title, "items": items}, **kw))

    add("series", "Next in your series", _next_in_series(user_id, have, words))
    add("authors", "New from authors you read", _new_from_authors(user_id, have, words))
    add("trending", f"Trending in {g[1]}", _row((lists.get("trending") or []) + (lists.get("top") or [])[10:], have, seen=seen))
    add("awards", "Award winners", _row([a for a in _lists.get("awards") or [] if slug in (a.get("genreSlugs") or [])], have))
    audio = [c for c in (_lists.get("nyt_audioFiction") or []) + (_lists.get("nyt_audioNonfiction") or []) if _matches(_about.get(c["key"], {}), words)]
    add("audio", "Audiobooks people love", _row(audio, have, audioOnly=True), audioOnly=True)
    return {"slug": slug, "name": g[1], "color": g[3], "hero": hero, "top10": top[:10], "rows": rows}


def all_genres():
    return {"genres": _genre_tiles()}


# ---------- One book's details ----------


def _known(key):
    """A card from the lists with this key, if any"""
    for v in _lists.values():
        if isinstance(v, list):
            for c in v:
                if isinstance(c, dict) and c.get("key") == key:
                    return c
    for v in (_lists.get("genres") or {}).values():
        for c in (v.get("top") or []) + (v.get("trending") or []):
            if c.get("key") == key:
                return c
    for v in (_lists.get("series_next") or {}).values():
        for c in v.get("books") or []:
            if c.get("key") == key:
                return c
    for v in (_lists.get("author_new") or {}).values():
        for c in v.get("books") or []:
            if c.get("key") == key:
                return c
    return None


def _nyt_rank(name):
    n = _books.norm(name)
    for which, label in (("nyt_fiction", "Fiction"), ("nyt_nonfiction", "Nonfiction"), ("nyt_audioFiction", "Audio fiction"), ("nyt_audioNonfiction", "Audio nonfiction")):
        for c in _lists.get(which) or []:
            if _books.norm(c.get("name")) == n:
                return {"rank": c.get("rank"), "list": label, "weeks": c.get("weeks")}
    return None


def _age_of(subjects, genres=None):
    """Who it's for. Hardcover's genres first (cleaner), then Open Library's subjects, which often
    say "juvenile fiction" on grown-up books too, so "young adult" and "adult" win over it."""
    g = " ".join(genres or []).lower()
    s = " ".join(subjects or []).lower()
    if "children" in g or "middle grade" in g or "picture book" in g:
        return "Kids"
    if "young adult" in g:
        return "Teens"
    if "adult" in g:
        return "Adults"
    if "young adult" in s:
        return "Teens"
    if any(w in s for w in ("picture book", "middle grade", "juvenile literature", "children's stories")):
        return "Kids"
    return "Adults"


def _short(text, n=320):
    text = re.sub(r"\s+", " ", str(text or "")).strip()
    if len(text) <= n:
        return text
    cut = text[:n]
    end = max(cut.rfind(". "), cut.rfind("! "), cut.rfind("? "))
    return cut[: end + 1] if end > 120 else cut.rsplit(" ", 1)[0] + "…"


def _look_up(key, name, author):
    """Everything for the details page, from Hardcover (when there's a token) and Open Library"""
    info = dict(_known(key) or {})
    info.setdefault("key", key)
    info.setdefault("name", name)
    info.setdefault("author", author)
    name, author = info.get("name") or name, info.get("author") or author
    # Open Library: description, subjects, first year, pages
    try:
        ol = None
        if key.startswith("ol:"):
            ol = key[3:]
        elif info.get("isbn"):
            d = _ol("/search.json", {"isbn": info["isbn"], "limit": "1", "fields": OL_FIELDS}).get("docs") or []
            if d:
                ol = (d[0].get("key") or "").rsplit("/", 1)[-1]
                info = dict(_ol_card(d[0]), **{k: v for k, v in info.items() if v})
        if not ol and name:
            found = _find_ol(name, author)
            if found:
                ol = found["key"][3:]
                info = dict(found, **{k: v for k, v in info.items() if v})
        if ol:
            w = _ol(f"/works/{ol}.json")
            d = w.get("description")
            d = d.get("value") if isinstance(d, dict) else d
            if d and not info.get("desc"):
                info["desc"] = _books.clean_html(d)
            info["subjects"] = info.get("subjects") or (w.get("subjects") or [])[:30]
            if not info.get("cover") and w.get("covers"):
                info["cover"] = f"https://covers.openlibrary.org/b/id/{w['covers'][0]}-L.jpg"
            info["olKey"] = ol
    except Exception as e:
        log(f"Open Library details {name}: {e!r}")
    # Hardcover: score, series, audiobook length, the longer description and reader quotes
    if _keys.get("hardcover"):
        try:
            hid = info.get("hc")
            if not hid:
                hits = hc_search(f"{name} {author.split(',')[0]}".strip(), 5)
                want = _books.norm(name)
                best = next((h for h in hits if _books.norm(h.get("title")) == want), None) or next(
                    (h for h in hits if want and want in _books.norm(h.get("title"))), None
                )
                hid = int(best["id"]) if best and best.get("id") else None
            if hid:
                b = (hc_books([int(hid)]) or [None])[0]
                card = _hc_card(b) or {}
                for k, v in card.items():
                    if v and (k in ("score", "votes", "readers", "audioMinutes", "series", "genres", "hc", "slug") or not info.get(k)):
                        info[k] = v
                if card.get("desc") and len(card["desc"]) > len(info.get("desc") or ""):
                    info["desc"] = card["desc"]
                info["scoreFrom"] = "Hardcover"
                try:
                    r = hc(
                        "query ($id: Int!) { user_books(where: {book_id: {_eq: $id}, has_review: {_eq: true}}, "
                        "order_by: {likes_count: desc}, limit: 6) { review_raw rating } }",
                        {"id": int(hid)},
                    )
                    quotes = []
                    for x in (r or {}).get("user_books") or []:
                        q = _short(x.get("review_raw"), 180)
                        if 40 <= len(q) and len(quotes) < 2:
                            quotes.append({"text": q, "stars": x.get("rating")})
                    info["quotes"] = quotes
                except Exception as e:
                    log(f"Hardcover quotes {name}: {e!r}")
                s = info.get("series")
                if s and s.get("name"):
                    info["seriesBooks"] = hc_series_books(s["name"])
        except Exception as e:
            log(f"Hardcover details {name}: {e!r}")
    # More by this author, more like this (Open Library)
    try:
        a = (author or "").split(",")[0].strip()
        if a:
            docs = _ol("/search.json", {"q": f'author:"{a}" language:eng', "sort": "rating", "limit": "16", "fields": OL_FIELDS}).get("docs") or []
            info["byAuthor"] = [c for c in (_ol_card(d) for d in docs) if c and _books.norm(c["name"]) != _books.norm(name)]
        subj = next((s for s in info.get("subjects") or [] if s.lower() in {g[2] for g in GENRES}), None)
        if not subj and info.get("genres"):
            subj = info["genres"][0]
        if subj:
            docs = _ol("/search.json", {"q": f'subject:"{subj}" language:eng', "sort": "trending", "limit": "20", "fields": OL_FIELDS}).get("docs") or []
            info["likeThis"] = [c for c in (_ol_card(d) for d in docs) if c and _books.norm(c["name"]) != _books.norm(name)]
    except Exception as e:
        log(f"More like {name}: {e!r}")
    info["at"] = int(time.time())
    info["v"] = 2  # 2 = English series books
    return info


def about(key, name, author, user_id, is_admin):
    key = str(key or "")[:80]
    if not key:
        raise _books.BooksError(400, "Which book?")
    with _lock:
        info = _about.get(key)
    if not info or info.get("v") != 2 or time.time() - info.get("at", 0) > 7 * 86400:
        info = _look_up(key, name or "", author or "")
        if not info.get("desc"):
            info["at"] -= 6 * 86400  # nothing found (maybe no internet): look again in a day, not a week
        with _lock:
            _about[key] = info
            if len(_about) > 1500:
                for k in sorted(_about, key=lambda k: _about[k].get("at", 0))[:300]:
                    _about.pop(k, None)
        _write("about.json", _about)
    have = _have_index()
    u = _books._user(user_id)
    here = have.get(_books.norm(info.get("name")))
    desc = info.get("desc") or ""
    subjects = info.get("subjects") or []
    series = info.get("series") or None
    sbooks = []
    for b in info.get("seriesBooks") or []:
        bh = have.get(_books.norm(b.get("name")))
        sbooks.append(
            {
                "key": b["key"],
                "name": b.get("name"),
                "pos": b.get("pos"),
                "cover": b.get("cover"),
                "author": b.get("author"),
                "have": bh,
                "asked": None if bh else _asked(b),
                "isThis": _books.norm(b.get("name")) == _books.norm(info.get("name")),
            }
        )
    facts = []
    if info.get("pages"):
        facts.append({"label": "Pages", "value": str(info["pages"])})
    if info.get("year"):
        facts.append({"label": "Published", "value": str(info["year"])})
    if info.get("audioMinutes"):
        h, m = divmod(int(info["audioMinutes"]), 60)
        facts.append({"label": "Audiobook", "value": (f"{h} hr " if h else "") + f"{m} min"})
    if info.get("narrator"):
        facts.append({"label": "Narrator", "value": info["narrator"]})
    if series and series.get("name"):
        facts.append({"label": "Series", "value": series["name"] + (f", book {series['pos']:g}" if isinstance(series.get("pos"), (int, float)) else "")})
    facts.append({"label": "Age", "value": _age_of(subjects, info.get("genres"))})
    if info.get("publisher"):
        facts.append({"label": "Publisher", "value": info["publisher"]})
    genres = info.get("genres") or _books._book_genres(subjects)
    audio_only = bool(info.get("audioOnly"))
    return {
        "key": info["key"],
        "name": info.get("name"),
        "author": info.get("author"),
        "cover": info.get("cover"),
        "year": info.get("year"),
        "desc": desc,
        "genres": genres[:4],
        "score": info.get("score"),
        "votes": info.get("votes") or 0,
        "scoreFrom": info.get("scoreFrom") or ("Open Library" if info.get("score") else None),
        "nyt": _nyt_rank(info.get("name")),
        "award": info.get("award"),
        "facts": facts,
        "series": {"name": series.get("name"), "books": sbooks} if series and sbooks else None,
        "back": {"blurb": _short(desc, 420), "quotes": info.get("quotes") or []},
        "byAuthor": _row(info.get("byAuthor"), have, limit=16),
        "likeThis": _row(info.get("likeThis"), have, limit=20),
        "have": here,
        "asked": None if here else _asked(info),
        "want": info["key"] in u["want"],
        "audioOnly": audio_only,
        "canAsk": _books.can_ask() or not is_admin,
    }


# ---------- The two keys (Server page) ----------


def keys_status():
    def one(which):
        h = _health.get(which) or {}
        return {"set": bool(_keys.get(which)), "ok": h.get("ok"), "problem": h.get("problem"), "checkedAt": h.get("at")}

    return {
        "nyt": one("nyt"),
        "hardcover": one("hardcover"),
        "gatheredAt": _lists.get("at"),
        "counts": {
            "nytFiction": len(_lists.get("nyt_fiction") or []),
            "top10": len(_lists.get("hc_trending") or []),
            "trending": len(_lists.get("ol_trending") or []),
        },
    }


def set_keys(body):
    """Ben types (or clears) the NYT key and the Hardcover token. Each is tried straight away."""
    words = []
    if "nyt" in body:
        k = re.sub(r"\s+", "", str(body.get("nyt") or ""))[:120]
        _keys["nyt"] = k
        if k:
            try:
                j = _nyt("/lists/current/combined-print-and-e-book-fiction.json") or {}
                n = len((j.get("results") or {}).get("books") or [])
                _set_health("nyt", True, None)
                words.append(f"NYT key works ({n} best sellers found).")
            except Exception as e:
                _set_health("nyt", False, _nyt_problem(e))
                words.append(_nyt_problem(e))
        else:
            _set_health("nyt", None, None)
            words.append("NYT key removed.")
    if "hardcover" in body:
        k = str(body.get("hardcover") or "").strip()[:4000]
        _keys["hardcover"] = k
        if k:
            try:
                # A book lookup, not "who am I": a key with only read:catalog can still do this
                d = hc("query { books(limit: 1, order_by: {users_count: desc}) { id title } }") or {}
                if not d.get("books"):
                    raise RuntimeError("Hardcover answered but sent no books")
                _set_health("hardcover", True, None)
                words.append("Hardcover token works.")
            except Exception as e:
                _set_health("hardcover", False, _hc_problem(e))
                words.append(_hc_problem(e))
        else:
            _set_health("hardcover", None, None)
            words.append("Hardcover token removed.")
    _write("keys.json", _keys)
    _kick.set()
    words.append("Tower is gathering the book lists now (about 2 minutes).")
    return {"message": " ".join(words), **keys_status()}


def route(method, path, query, user_id, is_admin, read_json):
    """/api/books/discover..., /api/books/about, /api/books/lists/keys. Returns (status, answer) or None."""
    q = lambda k: (query.get(k) or [""])[0].strip()  # noqa: E731
    if method == "GET" and path == "/api/books/discover":
        return 200, page(user_id)
    if method == "GET" and path == "/api/books/discover/genres":
        return 200, all_genres()
    m = re.fullmatch(r"/api/books/discover/genre/(\w+)", path)
    if method == "GET" and m:
        return 200, genre_page(m.group(1), user_id)
    if method == "GET" and path == "/api/books/about":
        return 200, about(q("key"), q("name"), q("author"), user_id, is_admin)
    if path == "/api/books/lists/keys":
        if not is_admin:
            return 403, {"error": "Only Ben can do that."}
        if method == "GET":
            return 200, keys_status()
        if method == "POST":
            return 200, set_keys(read_json())
    if method == "POST" and path == "/api/books/lists/gather":
        if not is_admin:
            return 403, {"error": "Only Ben can do that."}
        _kick.set()
        return 200, {"message": "Tower is gathering the book lists now (about 2 minutes)."}
    return None
