"""
Benflix "Did you know" facts for every movie and show on Tower.

Loaded by benflix-games-helper.py (it sits next to it on Tower). Three jobs:
  * Keeps a list of short facts for each movie and show. Two kinds, mixed:
      - hand-checked facts from the Benflix repo (server/facts/title-facts.json), downloaded again every
        night so new ones arrive without running the setup command
      - behind-the-scenes facts found on the title's Wikipedia page (Production, Filming, Casting...).
        The page is found through Wikidata by the title's IMDb or TMDB number, so it's the right page.
  * Every night (and once soon after the helper starts) it looks for movies and shows it hasn't looked up
    yet, so new titles get facts by the next morning.
  * Answers the apps: /api/facts/<item id> gives that title's facts (a show's episodes and seasons use the
    show's facts). /facts/titles.txt (at home only) lists every title and how many facts it has.

Facts stay short (they're shown on a TV in a small spot), never about endings or deaths (no spoilers),
and never plain information like who directed it, release dates, budgets or script drafts. Only lines that
tell a little behind-the-scenes story are kept (see fun_score), and old saved ones are checked again.

Saves its notes in /skips/facts (next to the skip times).
"""

import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

_jf = None  # The helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_admin_from_helper = None  # () -> (token, user id) of the admin, known once they've signed in
_dir = None
_lock = threading.RLock()
_log_lines = []
_gathering = threading.Event()
_checked = {}  # normalised "name year" or "tt123" -> [facts]
_auto = {}  # Jellyfin item id -> {"name", "year", "type", "facts", "at", "page"}
_titles = {}  # Jellyfin item id -> {"name", "year", "type", "imdb", "tmdb"}

CHECKED_URL = "https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/facts/title-facts.json"
UA = "Benflix/1.0 (home media app; https://github.com/TheBenPsych/MyStream)"
PER_NIGHT = 1500  # Wikipedia lookups in one night (about one a second, so well under an hour)
RETRY_DAYS = 30  # titles with no facts are looked at again after this long (Wikipedia pages grow)
MAX_AUTO = 4
MAX_LEN = 170
MIN_LEN = 45

SECTIONS = re.compile(
    r"\n== *(Production|Development|Filming|Casting|Pre-production|Writing|Design|Visual effects|Effects|"
    r"Special effects|Music|Soundtrack|Animation|Conception|Origins?|Background|Behind the scenes)[^=]*==\n",
    re.IGNORECASE,
)
# A fact has to tell a little story (something real, secret, improvised, hidden, hand-made...), not just
# who made it, when, where or for how much. Each STRONG cue is worth 2 points, each WEAK cue 1, and a
# sentence needs 2 points. Ben (2026-11-17): lines like "Within 12 hours, Scott returned the script with
# notes" are not fun facts. Any DULL or UNSAFE cue throws the sentence out, whatever its points.
# The app has the same lists in FunFactRules.kt: change both together.
RULES_VERSION = 2  # titles looked up with older rules are looked up again
STRONG = [
 r"improvis", r"ad-lib", r"unscripted", r"\bgenuine", r"reaction (?:was|were) real", r"\bwas real\b", r"\bwere real\b", r"\bfor real\b",
 r"\breal (?:gang|animals?|snow|food|tattoo|bugs?|fire|explosions?|scream|bees|spiders?|snakes?|rats?|cockroach)",
 r"\baccidental|\bby accident|\bhad an accident|\bin an accident", r"(?:was|were|got) (?:badly |seriously |slightly )?injured|injured (?:his|her|their|himself|herself)|(?:minor|serious) injur", r"\bbroke (?:his|her|their) ", r"nicknamed", r"easter egg", r"\bcameo", r"\bhomage\b", r"\btribute to\b",
 r"turned down", r"refused to", r"in secret", r"\bsecretly\b", r"did not (?:inform|tell|warn)", r"didn't (?:tell|warn)", r"without (?:telling|warning)",
 r"never broke character", r"stayed in character", r"method act", r"did (?:his|her|their|all of his|all of her|most of his|most of her) own", r"own stunts",
 r"stunt doubles", r"learned (?:to|how to) (?!be\b)", r"\bshaved\b", r"\bgained \d", r"\blost \d", r"\bpounds\b", r"kilograms",
 r"animatronic", r"\bpuppet", r"\bbackwards\b", r"made (?:of|from|out of|with) (?:real |actual )?(?:foam|wax|chocolate|ketchup|syrup|jelly|gelatin|cereal|candy|cardboard|paper|clay|plasticine|food|bread|lego|corn|rubber|hair|soap|cotton)",
 r"\bsounds? (?:of|effects?|for)\b.{0,60}\b(?:were|was) (?:made|created|recorded|based|generated)", r"\bvoiced? (?:of|by) (?:his|her) (?:own )?(?:son|daughter|wife|husband|mother|father)",
 r"\bby mistake|\bmistakenly\b|\bby accident", r"\bby chance\b", r"\bprank", r"\bon a dare\b", r"\bfell asleep", r"\bvomit|\bthrew up",
 r"\bin one take|\bsingle take|\bonly one take|\bfirst take|\bone continuous", r"\bnamed (?:after|for) (?:his|her|their|a|the director|the writer|[A-Z])",
 r"\bhidden (?:in|on|throughout|inside)", r"\bspelled backwards", r"\breal-life\b", r"\bfull-size|\bfull-scale|\blife-size",
 r"\bwhole (?:town|village|street|house)", r"\bactually (?:filmed|shot|built|made|real|ate|did|cried|hit|fell|broke|got)",
 r"\bthe same (?:costume|set|prop|car|house|dress)", r"\breused\b", r"\bwore (?:his|her) own", r"\b\d+ (?:takes|costume changes|wigs|outfits)",
 r"\bbanned\b", r"\bscared\b|\bterrified\b|\bfrightened\b", r"\bthought it was (?:a |an )?real", r"\bpopping candy|\bpop rocks",
 r"\bboot ?camp\b|\btraining camp\b|\bcheerleading camp", r"\bbefore (?:he|she) (?:was|became) famous",
]

WEAK = [
 r"\boriginally\b", r"\balmost\b", r"\bnearly\b", r"inspired by", r"\bactually\b", r"\bsurpris", r"\bfan of\b", r"\bparrot|\bdogs?\b|\bcats?\b|\bhorses?\b",
 r"\bjok", r"\bfoam\b", r"\bwax\b", r"\bgallons\b", r"\bfirst (?:ever|film to|movie to|time a)", r"\bthousands? of\b", r"\bfirst choice|\bonly choice",
 r"\bpractical effects?\b", r"\bprosthetic", r"by hand|hand-?made|hand-drawn|hand-painted", r"\bmade (?:of|from|out of)\b",
 r"\bhis (?:son|daughter|wife|mother|father|dog)\b|\bher (?:son|daughter|husband|mother|father|dog)\b", r"\bown (?:car|house|home|clothes|money|dog|family)\b",
 r"\bto avoid\b", r"\bcrushed\b|\bflooded\b|\bexploded\b|\bblew up\b", r"\bscream", r"\breversed\b|\bflipped\b|\bupside down",
 r"\bresemble\b", r"\bmodel(?:l)?ed (?:on|after)\b", r"\bbased on (?:his|her|their|a real|real|an actual|the real)", r"\bfriends?\b", r"\bcousin\b|\bbrother\b|\bsister\b",
]

DULL = [
 r"announc", r"confirm", r"in talks", r"negotiat", r"\breport", r"principal photography", r"\bwrapped\b", r"release",
 r"box office", r"joined the (?:cast|project|film)", r"\bwas cast\b", r"\bwere cast\b", r"\bcast as\b", r"signed on", r"\bset to\b", r"expected to",
 r"would return", r"screenplay", r"\bscript", r"\bdraft", r"rewr", r"re-wr", r"\bdeal\b", r"\bpremiere", r"\bgross", r"distribut", r"\brights\b",
 r"trailer", r"streaming", r"according to", r"citation", r"lawsuit", r"\bsued\b", r"allegation", r"\bexecutive", r"\bproducer", r"\bproduced\b",
 r"\bhired\b", r"\bbudget", r"\bmillion\b", r"\bbillion\b", r"\bfinanc", r"\bfund", r"\bpitch", r"\bdevelopment\b", r"greenl", r"green light", r"\bschedul",
 r"filming (?:began|took place|started|commenced|location)", r"\bwas shot (?:in|at|on)\b", r"\bwere shot (?:in|at|on)\b", r"\bon location\b", r"\blocations?\b",
 r"soundtrack", r"\bscore\b", r"\balbum", r"\blabel\b", r"\bcredited\b", r"critic", r"controvers", r"backlash", r"commercial", r"percent", r"%",
 r"\bstudios?\b", r"\boption", r"\bcontract", r"\bagent\b", r"\bacquir", r"\bpurchas", r"\bbought\b", r"\bsequel", r"\bfranchise", r"\bmarketing",
 r"\bsales\b", r"\bsold\b", r"\bawards?\b", r"\bnominat", r"\boscar", r"\bacademy\b", r"\bfestival", r"\bscreening", r"\btest audience",
 r"\b(?:January|February|March|April|May|June|July|August|September|October|November|December)\b", r"\b(?:19|20)\d\d\b",
 r"\bwas considered\b", r"\bwere considered\b", r"\bconsidered for\b", r"\bdirect(?:ed|or)? the film\b", r"\bwas directed\b", r"\bto direct\b", r"\bto star\b",
 r"\bnovel by\b", r"\bbased on the\b", r"\badaptation\b", r"\badapt", r"\bpre-production\b", r"\bpost-production\b", r"\bediting\b", r"\bedited\b",
 r"\btitle (?:was|is)\b", r"\bworking title\b", r"\bcodename\b|\bcode name\b", r"\bdelayed (?:the )?(?:release|production|filming|shoot)", r"\bpostpon", r"\bcancel", r"\bsecrecy\b",
 r"\b(?:was|were) cut\b", r"^[^,]{0,60},\s*(?:they|he|she|it)\s", r"\bstated\b", r"\bsaid\b", r"\bsays\b", r"\bexplained\b", r"\bcommented\b", r"\bdescribed\b", r"\bbelieved\b",
]

UNSAFE = [r"end of the (?:film|movie|series|show|season)", r"\bkill", r"\bdead\b", r"\bdies\b", r"\bdied\b", r"\bdeath", r"\bcorpse", r"\bmurder", r"\bsuicide", r"\bfuneral", r"\bending\b", r"\btwist",
 r"\bclimax", r"\breveal", r"\bspoiler", r"final scene", r"last scene", r"\bnud", r"\bnaked", r"\bsex", r"\bpenis", r"\bdrug", r"\bheroin", r"\bcocaine",
 r"\bbullshit|\bfuck|\bshit\b", r"\bdrunk", r"\brape", r"\bpregnan", r"\bdisown", r"\bbigot|\bracis|\bwhitewash", r"\bpoison", r"\bdecapitat", r"\bblood"]

STRONG = [re.compile(p) if p.startswith("\\bnamed") else re.compile(p, re.IGNORECASE) for p in STRONG]
WEAK = [re.compile(p, re.IGNORECASE) for p in WEAK]
DULL = [re.compile(p, re.IGNORECASE) for p in DULL]
UNSAFE = [re.compile(p, re.IGNORECASE) for p in UNSAFE]
FIRST_PERSON = re.compile(r"(^|[^\w'])(I|I'm|I've|I'd|me|my|we|We|our|Our|you|You|your)([^\w']|$)")
BAD_STARTS = ("He", "She", "It", "They", "This", "These", "That", "His", "Her", "Their", "However", "Also",
              "In addition", "Additionally", "As a result", "Later", "The scene", "The film", "The series", "The show",
              "Both", "The two", "Such", "Instead", "Afterwards", "In contrast", "Furthermore", "Moreover", "Still", "Then",
              "There", "So", "But", "And", "Or", "Despite this", "Even", "Other", "Another", "Some", "Several", "Many", "Most",
              "One of the")
SENTENCE_END = re.compile(r"(?<=[.!?])(?<!\b[A-Z]\.)(?<!\b(?:Mr|Ms|Dr|St|Jr|Sr|vs|Mt|No)\.)(?<!\bMrs\.)\s+(?=[A-Z0-9\"“])")


def log(text):
    line = time.strftime("%H:%M:%S ") + text
    print("facts: " + text, flush=True)
    with _lock:
        _log_lines.append(line)
        del _log_lines[:-40]


def _pick_dir():
    for d in (os.environ.get("FACTS_DIR"), "/skips/facts", "/tracks/benflix-facts"):
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


def _read(name, default=None):
    if not _dir:
        return default
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


def start(jellyfin_call, admin):
    """Called once by the helper when it starts. admin() gives (token, user id) of the admin."""
    global _jf, _admin_from_helper, _dir, _auto, _titles
    _jf, _admin_from_helper = jellyfin_call, admin
    _dir = _pick_dir()
    if not _dir:
        log("no folder to save facts in, so facts are off")
        return
    _auto = _read("auto.json", {}) or {}
    _titles = _read("titles.json", {}) or {}
    _load_checked(_read("checked.json", {}) or {})
    threading.Thread(target=_nightly, name="facts-nightly", daemon=True).start()
    log(f"ready ({len(_auto)} titles looked up, saving in {_dir})")


def remember_admin(token, user_id):
    if not _dir or not token:
        return
    saved = _read("admin.json", {}) or {}
    if saved.get("token") != token:
        _write("admin.json", {"token": token, "user": user_id})


def _admin():
    token, user = (_admin_from_helper() if _admin_from_helper else (None, None)) or (None, None)
    if not token:
        saved = _read("admin.json", {}) or {}
        token, user = saved.get("token"), saved.get("user")
    return token, user


# ---------- names ----------

def _norm(text):
    return re.sub(r"[^a-z0-9]+", " ", (text or "").lower().replace("&", " and ")).strip()


def _keys(name, year, imdb=None):
    keys = []
    if imdb:
        keys.append(imdb.lower())
    if year:
        keys.append(f"{_norm(name)} {year}")
    keys.append(_norm(name))
    return keys


def _load_checked(data):
    global _checked
    found = {}
    for key, facts in (data or {}).items():
        if key.startswith("_") or not isinstance(facts, list):
            continue
        clean = [f.strip() for f in facts if isinstance(f, str) and f.strip()]
        if not clean:
            continue
        # "Five Nights at Freddy's (2023)" -> "five nights at freddy s 2023"; "tt4589218" stays as it is
        m = re.fullmatch(r"\s*(.+?)\s*\((\d{4})\)\s*", key)
        k = key.lower().strip() if re.fullmatch(r"tt\d+", key.strip().lower()) else (f"{_norm(m.group(1))} {m.group(2)}" if m else _norm(key))
        found[k] = clean
    _checked = found


def _checked_for(t):
    for k in _keys(t.get("name"), t.get("year"), t.get("imdb")):
        if k in _checked:
            return _checked[k]
    return []


# ---------- web ----------

def _http_json(url, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


def _refresh_checked():
    try:
        req = urllib.request.Request(CHECKED_URL, headers={"User-Agent": UA, "Cache-Control": "no-cache"})
        with urllib.request.urlopen(req, timeout=30) as r:
            data = json.loads(r.read().decode("utf-8"))
        _write("checked.json", data)
        _load_checked(data)
        log(f"hand-checked facts: {len(_checked)} titles")
    except Exception as e:  # noqa: BLE001
        log(f"couldn't download the hand-checked facts ({e}); using the saved copy")


def _wikidata_page(imdb=None, tmdb=None, kind="Movie"):
    """The English Wikipedia page name for this IMDb / TMDB number, through Wikidata"""
    tries = []
    if imdb:
        tries.append(f"haswbstatement:P345={imdb}")
    if tmdb:
        tries.append(f"haswbstatement:{'P4947' if kind == 'Movie' else 'P4983'}={tmdb}")
    for q in tries:
        try:
            found = _http_json(
                "https://www.wikidata.org/w/api.php?"
                + urllib.parse.urlencode({"action": "query", "list": "search", "srsearch": q, "format": "json", "srlimit": 1})
            )
            hits = (found.get("query") or {}).get("search") or []
            if not hits:
                continue
            qid = hits[0]["title"]
            ent = _http_json(
                "https://www.wikidata.org/w/api.php?"
                + urllib.parse.urlencode({"action": "wbgetentities", "ids": qid, "props": "sitelinks", "sitefilter": "enwiki", "format": "json"})
            )
            link = (((ent.get("entities") or {}).get(qid) or {}).get("sitelinks") or {}).get("enwiki")
            if link and link.get("title"):
                return link["title"]
        except Exception as e:  # noqa: BLE001
            log(f"lookup failed: {e}")
            continue
    return None


def _wikipedia_text(title):
    data = _http_json(
        "https://en.wikipedia.org/w/api.php?"
        + urllib.parse.urlencode({"action": "query", "prop": "extracts", "explaintext": 1, "redirects": 1, "format": "json", "titles": title})
    )
    pages = (data.get("query") or {}).get("pages") or {}
    for key, page in pages.items():
        if key != "-1" and page.get("extract"):
            return page["extract"]
    return None


def _guess_page(name, year, kind):
    word = "film" if kind == "Movie" else "series"
    guesses = [f"{name} ({year} film)", f"{name} (film)", name] if kind == "Movie" else [
        f"{name} ({year} TV series)", f"{name} (TV series)", name]
    for title in guesses if year else guesses[1:]:
        try:
            text = _wikipedia_text(title)
        except Exception as e:  # noqa: BLE001
            log(f"lookup failed: {e}")
            continue
        if text and word in text[:1500].lower():
            return title, text
    return None, None


def _complete(s):
    if not s.endswith((".", "!", "?", ".”", '."')):
        return False
    if re.search(r"(^|\s)[A-Z]\.$", s):
        return False
    if s.count("(") != s.count(")") or s.count("“") != s.count("”") or s.count('"') % 2:
        return False
    return s[0].isupper() or s[0].isdigit()


def fun_score(s):
    """How much of a fun story this sentence tells (0 = leave it out). See the rules above."""
    s = (s or "").strip()
    if not (MIN_LEN <= len(s) <= MAX_LEN) or not _complete(s):
        return 0
    if s.startswith(('"', "“")) or any(s.startswith(b + " ") or s.startswith(b + ",") for b in BAD_STARTS):
        return 0
    if FIRST_PERSON.search(s) or any(p.search(s) for p in DULL) or any(p.search(s) for p in UNSAFE):
        return 0
    return 2 * sum(1 for p in STRONG if p.search(s)) + sum(1 for p in WEAK if p.search(s))


def _still_fun(name, fact):
    """A saved Wikipedia fact still passes today's rules (so old dull ones stop showing straight away)"""
    if fact.startswith((name or "") + ": "):
        fact = fact[len(name) + 2:]
    return fun_score(fact) >= 2


def _facts_from(name, text):
    found = []
    for m in SECTIONS.finditer(text):
        rest = text[m.end() - 1:]
        end = re.search(r"\n== [^=]+ ==\n", rest)
        section = re.sub(r"\n=+[^=]+=+\n", "\n", rest[: end.start() if end else len(rest)])
        for s in SENTENCE_END.split(section.replace("\n", " ")):
            s = re.sub(r"\s+", " ", s).strip()
            score = fun_score(s)
            if score < 2:
                continue
            if name.lower() not in s.lower() and len(s) + len(name) + 2 <= MAX_LEN:
                s = f"{name}: {s}"
            if all(s != f for _, f in found):
                found.append((score, s))
    # The best stories first; lines that name the title read best on their own, so they win a tie
    found.sort(key=lambda sf: (-sf[0], 0 if not sf[1].startswith(name + ":") else 1))
    return [s for _, s in found[:MAX_AUTO]]


def _look_up(item_id, t):
    page = _wikidata_page(t.get("imdb"), t.get("tmdb"), t.get("type"))
    text = None
    if page:
        try:
            text = _wikipedia_text(page)
        except Exception:  # noqa: BLE001
            text = None
    if not text:
        page, text = _guess_page(t["name"], t.get("year"), t.get("type"))
    facts = _facts_from(t["name"], text) if text else []
    with _lock:
        _auto[item_id] = {"name": t["name"], "year": t.get("year"), "type": t.get("type"), "facts": facts, "at": int(time.time()), "page": page, "rules": RULES_VERSION}
    return facts


# ---------- the library ----------

def _library():
    token, user = _admin()
    if not token or not user:
        return None
    q = urllib.parse.urlencode({
        "Recursive": "true",
        "IncludeItemTypes": "Movie,Series",
        "Fields": "ProviderIds,ProductionYear",
        "EnableImages": "false",
        "EnableUserData": "false",
    })
    status, data, _ = _jf("GET", f"/Users/{user}/Items?{q}", token=token)
    if status != 200:
        return None
    found = {}
    for it in (json.loads(data or b"{}").get("Items") or []):
        # Jellyfin can send collections too; facts are only for movies and shows
        if it.get("Type") not in ("Movie", "Series"):
            continue
        ids = it.get("ProviderIds") or {}
        found[it["Id"].replace("-", "")] = {
            "name": it.get("Name") or "",
            "year": it.get("ProductionYear"),
            "type": it.get("Type"),
            "imdb": ids.get("Imdb") or ids.get("imdb"),
            "tmdb": ids.get("Tmdb") or ids.get("tmdb"),
        }
    return found


def gather(why="night"):
    """Looks up every title that hasn't been looked up yet (or had nothing a month ago)"""
    if _gathering.is_set():
        return
    _gathering.set()
    try:
        _refresh_checked()
        lib = _library()
        if lib is None:
            log("Tower needs the main profile to sign in on Benflix once before facts can be gathered")
            return
        global _titles
        _titles = lib
        _write("titles.json", lib)
        now = time.time()
        todo = [
            (i, t) for i, t in lib.items()
            if t["name"] and (i not in _auto or (not _auto[i].get("facts") and now - _auto[i].get("at", 0) > RETRY_DAYS * 86400))
        ]
        # Titles last looked up with older (looser) rules get picked again, after the brand-new ones
        first = {i for i, _ in todo}
        todo += [(i, t) for i, t in lib.items() if t["name"] and i not in first and i in _auto and (_auto[i].get("rules") or 1) < RULES_VERSION]
        done = got = 0
        for item_id, t in todo[:PER_NIGHT]:
            try:
                if _look_up(item_id, t):
                    got += 1
            except Exception as e:  # noqa: BLE001
                log(f"skipped {t['name']}: {e}")
            done += 1
            if done % 25 == 0:
                with _lock:
                    _write("auto.json", _auto)
            time.sleep(1)
        with _lock:
            _write("auto.json", _auto)
        _write("night.json", {"at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "looked_up": done, "found": got, "left": max(0, len(todo) - done), "why": why})
        log(f"looked up {done} titles, {got} had facts ({max(0, len(todo) - done)} left for next time)")
    except Exception as e:  # noqa: BLE001
        log(f"couldn't gather facts: {e}")
    finally:
        _gathering.clear()


def _nightly():
    time.sleep(600)
    if _admin()[0]:
        gather("start")
    done_day = None
    while True:
        time.sleep(600)
        now = time.localtime()
        if not (4 <= now.tm_hour < 6) or done_day == now.tm_yday:
            continue
        done_day = now.tm_yday
        if _admin()[0]:
            gather()


# ---------- answers ----------

def _series_of(item_id, token):
    """A season or episode's show id (so its pages and screensaver use the show's facts), or None"""
    status, data, _ = _jf("GET", f"/Items?Ids={item_id}&Fields=ProviderIds,ProductionYear&EnableImages=false", token=token)
    if status != 200:
        return None, None
    items = json.loads(data or b"{}").get("Items") or []
    if not items:
        return None, None
    it = items[0]
    if it.get("Type") in ("Episode", "Season") and it.get("SeriesId"):
        return it["SeriesId"].replace("-", ""), None
    ids = it.get("ProviderIds") or {}
    return item_id, {"name": it.get("Name") or "", "year": it.get("ProductionYear"), "type": it.get("Type"),
                     "imdb": ids.get("Imdb"), "tmdb": ids.get("Tmdb")}


def facts_for(item_id, token):
    item_id = item_id.replace("-", "").lower()
    t = _titles.get(item_id)
    if t is None:
        sid, info = _series_of(item_id, token)
        if sid and sid != item_id:
            item_id, t = sid, _titles.get(sid)
            if t is None:
                _, t = _series_of(sid, token)
        else:
            t = info
    if not t or t.get("type") not in ("Movie", "Series"):
        return {"facts": [], "ready": True}
    checked = _checked_for(t)
    auto = (_auto.get(item_id) or {}).get("facts")
    if auto is not None:
        auto = [f for f in auto if _still_fun(t.get("name"), f)]
    if auto is None and not checked and _dir:
        # A brand-new title: look it up now, in the background, so it's there next time
        threading.Thread(target=lambda: _safe_look_up(item_id, t), daemon=True).start()
    seen = {_norm(f) for f in checked}
    mixed = list(checked) + [f for f in (auto or []) if _norm(f) not in seen]
    return {"facts": [{"text": f, "checked": i < len(checked)} for i, f in enumerate(mixed)], "ready": auto is not None or bool(checked)}


def _safe_look_up(item_id, t):
    try:
        _look_up(item_id, t)
        with _lock:
            _write("auto.json", _auto)
    except Exception as e:  # noqa: BLE001
        log(f"couldn't look up {t.get('name')}: {e}")


def titles_text(few=False):
    """
    Every movie and show with its fact counts, for sending to a chat (at home only). With few=True, only
    titles with fewer than 2 facts so far (the ones that need hand-checked facts most), in a short form.
    """
    lines = ["# Benflix titles and their facts: name (year) | type | IMDb | hand-checked | found on Wikipedia", ""]
    for item_id, t in sorted(_titles.items(), key=lambda kv: (kv[1].get("type") or "", _norm(kv[1].get("name")))):
        if t.get("type") not in ("Movie", "Series"):
            continue
        auto = _auto.get(item_id)
        checked = len(_checked_for(t))
        found = len(auto["facts"]) if auto else None
        if few and (auto is None or checked + found >= 2):
            continue
        lines.append(
            f"{t['name']} ({t.get('year') or '?'}) | {t.get('type')} | {t.get('imdb') or '-'} | "
            f"{checked} | {found if found is not None else 'not yet'}"
        )
    if not _titles:
        lines.append("Nothing yet. Tower gathers the list about 10 minutes after the helper starts, once Ben has signed in on Benflix.")
    return "\n".join(lines) + "\n"


def status():
    n = _read("night.json", {}) or {}
    with_facts = sum(1 for i in _titles if (_auto.get(i) or {}).get("facts") or _checked_for(_titles[i]))
    return {"titles": len(_titles), "with_facts": with_facts, "checked_titles": len(_checked), "busy": _gathering.is_set(), "last": n, "log": _log_lines[-15:]}


def _can_see(item_id, token, user_id):
    """Is this title in this person's Benflix? (Jellyfin knows each person's libraries and age limits)"""
    try:
        status, _, _ = _jf("GET", f"/Users/{user_id}/Items/{item_id}", token=token)
        return status == 200
    except Exception:  # noqa: BLE001
        return False


def home_facts(token, user_id, count=2, skip=(), longest=MAX_LEN):
    """
    Home's "Did you know" rows: [count] facts, each about a different movie or show this person can watch,
    picked at random every time Home opens. [skip] = title ids shown last time, so it changes.
    """
    import random

    skip = {s.replace("-", "").lower() for s in skip if s}
    with _lock:
        ids = [i for i, t in _titles.items() if t.get("type") in ("Movie", "Series") and i not in skip]
    random.shuffle(ids)
    out = []
    for item_id in ids[:60]:
        t = _titles[item_id]
        checked = _checked_for(t)
        auto = [f for f in ((_auto.get(item_id) or {}).get("facts") or []) if _still_fun(t.get("name"), f)]
        pool = list(checked) + [f for f in auto if _norm(f) not in {_norm(c) for c in checked}]
        # Home's rows are small: only the shorter facts
        pool = [f for f in pool if len(f) <= longest]
        if not pool:
            continue
        if not _can_see(item_id, token, user_id):
            continue
        out.append({"id": item_id, "name": t.get("name") or "", "type": t.get("type"), "fact": random.choice(pool)})
        if len(out) >= count:
            break
    return {"facts": out}


# ---------- Parents' guide ----------
# How much violence, swearing, scary bits and adult scenes a title has, from IMDb's public parents guide
# (the same one the TV and phone app shows). Kept for a month per title, in parents.json.
PARENTS_NAMES = {"VIOLENCE": "Violence", "PROFANITY": "Swearing", "FRIGHTENING": "Scary bits", "NUDITY": "Adult scenes", "ALCOHOL": "Drinking and drugs"}
PARENTS_ORDER = ["VIOLENCE", "PROFANITY", "FRIGHTENING", "NUDITY", "ALCOHOL"]
PARENTS_LEVELS = {"none": 0, "mild": 1, "moderate": 2, "severe": 3}
PARENTS_WORDS = ["None", "A little", "Some", "A lot"]
PARENTS_QUERY = ("query ParentsGuide($id: ID!) { title(id: $id) { parentsGuide { categories "
                 "{ category { id text } severity { text } } } } }")
_parents_lock = threading.Lock()


def parents_guide(imdb_id):
    """[{label, level, rank}] for an IMDb id, or [] when IMDb has no guide (or can't be reached)."""
    month = 30 * 86400
    with _parents_lock:
        saved = _read("parents.json", {}) or {}
        hit = saved.get(imdb_id)
        if isinstance(hit, dict) and time.time() - hit.get("at", 0) < month:
            return hit.get("lines") or []
    try:
        body = json.dumps({"query": PARENTS_QUERY, "variables": {"id": imdb_id}}).encode()
        req = urllib.request.Request("https://graphql.imdb.com/", data=body, method="POST", headers={
            "Content-Type": "application/json", "Referer": "https://www.imdb.com/", "User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=12) as r:
            data = json.loads(r.read().decode("utf-8", "replace"))
    except (urllib.error.URLError, OSError, ValueError):
        return []
    cats = ((((data or {}).get("data") or {}).get("title") or {}).get("parentsGuide") or {}).get("categories") or []
    found = {}
    for c in cats:
        cid = str(((c or {}).get("category") or {}).get("id") or "").upper()
        sev = str(((c or {}).get("severity") or {}).get("text") or "").lower()
        if cid in PARENTS_NAMES and sev in PARENTS_LEVELS:
            rank = PARENTS_LEVELS[sev]
            found[cid] = {"label": PARENTS_NAMES[cid], "level": PARENTS_WORDS[rank], "rank": rank}
    lines = [found[k] for k in PARENTS_ORDER if k in found]
    with _parents_lock:
        saved = _read("parents.json", {}) or {}
        saved[imdb_id] = {"lines": lines, "at": int(time.time())}
        if len(saved) > 5000:
            saved = dict(sorted(saved.items(), key=lambda kv: kv[1].get("at", 0))[-4000:])
        try:
            _write("parents.json", saved)
        except OSError:
            pass
    return lines


def route(method, path, query, token, user_id, is_admin):
    """/api/facts/... for the apps. Returns (status, answer)."""
    if method == "GET" and path == "/api/facts/home":
        n = max(1, min(4, int((query.get("n") or ["2"])[0] or 2)))
        skip = ",".join(query.get("skip") or []).split(",")
        longest = max(60, min(MAX_LEN, int((query.get("max") or [str(MAX_LEN)])[0] or MAX_LEN)))
        return 200, home_facts(token, user_id, n, skip, longest)
    if method == "GET" and path == "/api/facts/status":
        return 200, status()
    if method == "POST" and path == "/api/facts/gather":
        if not is_admin:
            return 403, {"error": "Only the server admin can do that."}
        remember_admin(token, user_id)
        threading.Thread(target=gather, kwargs={"why": "asked"}, name="facts-gather", daemon=True).start()
        return 200, {"message": "Gathering facts now. It takes a while the first time."}
    m = re.fullmatch(r"/api/facts/parents/(tt\d{5,10})", path)
    if method == "GET" and m:
        # Parents' guide for the web version (browsers can't ask IMDb themselves)
        return 200, {"lines": parents_guide(m.group(1))}
    m = re.fullmatch(r"/api/facts/([0-9a-fA-F-]{32,36})", path)
    if method == "GET" and m:
        if is_admin:
            remember_admin(token, user_id)
        return 200, facts_for(m.group(1), token)
    return 404, {"error": "Not found"}
