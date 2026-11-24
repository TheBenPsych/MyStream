"""
Glowfin "Follow your teams" (Idea Box 28, Ben 2026-11-24): each person picks their teams once, on Live TV's
Sports tab (or Settings > Watching). Their teams' games then come first on the Sports tab, get a gold
"Your team" tag in the guide, and Glowfin says "Your team is on" when one starts.

How it works, in plain words:
- The leagues and teams are written out below (NFL, college football, NBA, WNBA, MLB, NHL, MLS).
- A game is found by reading Live TV's guide (Jellyfin's, so it works with any channels Tower has) for the
  next 7 days and looking for a picked team's name in what's on ("Chiefs at Bills", "Kansas City Chiefs").
  A team name that two leagues share (Giants, Rangers, Kings, Panthers, Cardinals, Jets) only counts with
  its city too, so the Giants on a baseball channel don't light up for a football fan.
- The guide is read at most every 10 minutes and shared by everyone; each person's picks are matched to it.

Saved beside the skip times (/skips/teams/picks.json): each person's picked teams. Only the Python standard library.
"""

import json
import os
import re
import threading
import time
import urllib.parse

# league key, name, [(id, city, nickname, short, colour)]
LEAGUES = [
    ("nfl", "NFL", [
        ("nfl-ari", "Arizona", "Cardinals", "ARI", "#97233F"), ("nfl-atl", "Atlanta", "Falcons", "ATL", "#A71930"),
        ("nfl-bal", "Baltimore", "Ravens", "BAL", "#241773"), ("nfl-buf", "Buffalo", "Bills", "BUF", "#00338D"),
        ("nfl-car", "Carolina", "Panthers", "CAR", "#0085CA"), ("nfl-chi", "Chicago", "Bears", "CHI", "#0B162A"),
        ("nfl-cin", "Cincinnati", "Bengals", "CIN", "#FB4F14"), ("nfl-cle", "Cleveland", "Browns", "CLE", "#311D00"),
        ("nfl-dal", "Dallas", "Cowboys", "DAL", "#041E42"), ("nfl-den", "Denver", "Broncos", "DEN", "#FB4F14"),
        ("nfl-det", "Detroit", "Lions", "DET", "#0076B6"), ("nfl-gb", "Green Bay", "Packers", "GB", "#203731"),
        ("nfl-hou", "Houston", "Texans", "HOU", "#03202F"), ("nfl-ind", "Indianapolis", "Colts", "IND", "#002C5F"),
        ("nfl-jax", "Jacksonville", "Jaguars", "JAX", "#006778"), ("nfl-kc", "Kansas City", "Chiefs", "KC", "#E31837"),
        ("nfl-lv", "Las Vegas", "Raiders", "LV", "#2b2b2b"), ("nfl-lac", "Los Angeles", "Chargers", "LAC", "#0080C6"),
        ("nfl-lar", "Los Angeles", "Rams", "LAR", "#003594"), ("nfl-mia", "Miami", "Dolphins", "MIA", "#008E97"),
        ("nfl-min", "Minnesota", "Vikings", "MIN", "#4F2683"), ("nfl-ne", "New England", "Patriots", "NE", "#002244"),
        ("nfl-no", "New Orleans", "Saints", "NO", "#9F8958"), ("nfl-nyg", "New York", "Giants", "NYG", "#0B2265"),
        ("nfl-nyj", "New York", "Jets", "NYJ", "#125740"), ("nfl-phi", "Philadelphia", "Eagles", "PHI", "#004C54"),
        ("nfl-pit", "Pittsburgh", "Steelers", "PIT", "#2b2b2b"), ("nfl-sf", "San Francisco", "49ers", "SF", "#AA0000"),
        ("nfl-sea", "Seattle", "Seahawks", "SEA", "#002244"), ("nfl-tb", "Tampa Bay", "Buccaneers", "TB", "#D50A0A"),
        ("nfl-ten", "Tennessee", "Titans", "TEN", "#0C2340"), ("nfl-was", "Washington", "Commanders", "WAS", "#5A1414"),
    ]),
    ("college", "College football", [
        ("cf-bama", "Alabama", "Crimson Tide", "BAMA", "#9E1B32"), ("cf-uga", "Georgia", "Bulldogs", "UGA", "#BA0C2F"),
        ("cf-osu", "Ohio State", "Buckeyes", "OSU", "#BB0000"), ("cf-mich", "Michigan", "Wolverines", "MICH", "#00274C"),
        ("cf-tex", "Texas", "Longhorns", "TEX", "#BF5700"), ("cf-nd", "Notre Dame", "Fighting Irish", "ND", "#0C2340"),
        ("cf-lsu", "LSU", "Tigers", "LSU", "#461D7C"), ("cf-osu2", "Oregon", "Ducks", "ORE", "#154733"),
        ("cf-psu", "Penn State", "Nittany Lions", "PSU", "#041E42"), ("cf-clem", "Clemson", "Tigers", "CLEM", "#F56600"),
        ("cf-usc", "USC", "Trojans", "USC", "#990000"), ("cf-okla", "Oklahoma", "Sooners", "OU", "#841617"),
        ("cf-fsu", "Florida State", "Seminoles", "FSU", "#782F40"), ("cf-uf", "Florida", "Gators", "UF", "#0021A5"),
        ("cf-tamu", "Texas A&M", "Aggies", "TAMU", "#500000"), ("cf-tenn", "Tennessee", "Volunteers", "TENN", "#FF8200"),
        ("cf-aub", "Auburn", "Tigers", "AUB", "#0C2340"), ("cf-miami", "Miami", "Hurricanes", "MIA", "#F47321"),
        ("cf-wisc", "Wisconsin", "Badgers", "WIS", "#C5050C"), ("cf-iowa", "Iowa", "Hawkeyes", "IOWA", "#2b2b2b"),
        ("cf-neb", "Nebraska", "Cornhuskers", "NEB", "#E41C38"), ("cf-msu", "Michigan State", "Spartans", "MSU", "#18453B"),
        ("cf-ole", "Ole Miss", "Rebels", "MISS", "#CE1126"), ("cf-ucla", "UCLA", "Bruins", "UCLA", "#2D68C4"),
        ("cf-wash", "Washington", "Huskies", "UW", "#4B2E83"), ("cf-utah", "Utah", "Utes", "UTAH", "#CC0000"),
        ("cf-colo", "Colorado", "Buffaloes", "COLO", "#2b2b2b"), ("cf-ksu", "Kansas State", "Wildcats", "KSU", "#512888"),
        ("cf-sc", "South Carolina", "Gamecocks", "SC", "#73000A"), ("cf-ark", "Arkansas", "Razorbacks", "ARK", "#9D2235"),
        ("cf-uk", "Kentucky", "Wildcats", "UK", "#0033A0"), ("cf-vt", "Virginia Tech", "Hokies", "VT", "#630031"),
        ("cf-unc", "North Carolina", "Tar Heels", "UNC", "#4B9CD3"), ("cf-byu", "BYU", "Cougars", "BYU", "#002E5D"),
        ("cf-isu", "Iowa State", "Cyclones", "ISU", "#C8102E"), ("cf-mizz", "Missouri", "Tigers", "MIZ", "#F1B82D"),
        ("cf-tcu", "TCU", "Horned Frogs", "TCU", "#4D1979"), ("cf-ttu", "Texas Tech", "Red Raiders", "TTU", "#CC0000"),
        ("cf-boise", "Boise State", "Broncos", "BSU", "#0033A0"), ("cf-army", "Army", "Black Knights", "ARMY", "#2b2b2b"),
        ("cf-navy", "Navy", "Midshipmen", "NAVY", "#00205B"), ("cf-minn", "Minnesota", "Golden Gophers", "MINN", "#7A0019"),
    ]),
    ("nba", "NBA", [
        ("nba-atl", "Atlanta", "Hawks", "ATL", "#E03A3E"), ("nba-bos", "Boston", "Celtics", "BOS", "#007A33"),
        ("nba-bkn", "Brooklyn", "Nets", "BKN", "#2b2b2b"), ("nba-cha", "Charlotte", "Hornets", "CHA", "#1D1160"),
        ("nba-chi", "Chicago", "Bulls", "CHI", "#CE1141"), ("nba-cle", "Cleveland", "Cavaliers", "CLE", "#860038"),
        ("nba-dal", "Dallas", "Mavericks", "DAL", "#00538C"), ("nba-den", "Denver", "Nuggets", "DEN", "#0E2240"),
        ("nba-det", "Detroit", "Pistons", "DET", "#C8102E"), ("nba-gsw", "Golden State", "Warriors", "GSW", "#1D428A"),
        ("nba-hou", "Houston", "Rockets", "HOU", "#CE1141"), ("nba-ind", "Indiana", "Pacers", "IND", "#002D62"),
        ("nba-lac", "LA", "Clippers", "LAC", "#C8102E"), ("nba-lal", "Los Angeles", "Lakers", "LAL", "#552583"),
        ("nba-mem", "Memphis", "Grizzlies", "MEM", "#5D76A9"), ("nba-mia", "Miami", "Heat", "MIA", "#98002E"),
        ("nba-mil", "Milwaukee", "Bucks", "MIL", "#00471B"), ("nba-min", "Minnesota", "Timberwolves", "MIN", "#0C2340"),
        ("nba-nop", "New Orleans", "Pelicans", "NOP", "#0C2340"), ("nba-nyk", "New York", "Knicks", "NYK", "#006BB6"),
        ("nba-okc", "Oklahoma City", "Thunder", "OKC", "#007AC1"), ("nba-orl", "Orlando", "Magic", "ORL", "#0077C0"),
        ("nba-phi", "Philadelphia", "76ers", "PHI", "#006BB6"), ("nba-phx", "Phoenix", "Suns", "PHX", "#1D1160"),
        ("nba-por", "Portland", "Trail Blazers", "POR", "#E03A3E"), ("nba-sac", "Sacramento", "Kings", "SAC", "#5A2D81"),
        ("nba-sas", "San Antonio", "Spurs", "SAS", "#2b2b2b"), ("nba-tor", "Toronto", "Raptors", "TOR", "#CE1141"),
        ("nba-uta", "Utah", "Jazz", "UTA", "#002B5C"), ("nba-was", "Washington", "Wizards", "WAS", "#002B5C"),
    ]),
    ("wnba", "WNBA", [
        ("wnba-atl", "Atlanta", "Dream", "ATL", "#C8102E"), ("wnba-chi", "Chicago", "Sky", "CHI", "#418FDE"),
        ("wnba-con", "Connecticut", "Sun", "CON", "#F05023"), ("wnba-dal", "Dallas", "Wings", "DAL", "#002B5C"),
        ("wnba-gsv", "Golden State", "Valkyries", "GSV", "#5A2D81"), ("wnba-ind", "Indiana", "Fever", "IND", "#002D62"),
        ("wnba-lv", "Las Vegas", "Aces", "LV", "#2b2b2b"), ("wnba-la", "Los Angeles", "Sparks", "LA", "#552583"),
        ("wnba-min", "Minnesota", "Lynx", "MIN", "#0C2340"), ("wnba-ny", "New York", "Liberty", "NY", "#6ECEB2"),
        ("wnba-phx", "Phoenix", "Mercury", "PHX", "#3C286E"), ("wnba-por", "Portland", "Fire", "POR", "#C8102E"),
        ("wnba-sea", "Seattle", "Storm", "SEA", "#2C5234"), ("wnba-tor", "Toronto", "Tempo", "TOR", "#0B6E4F"),
        ("wnba-was", "Washington", "Mystics", "WAS", "#C8102E"),
    ]),
    ("mlb", "MLB", [
        ("mlb-ari", "Arizona", "Diamondbacks", "ARI", "#A71930"), ("mlb-atl", "Atlanta", "Braves", "ATL", "#13274F"),
        ("mlb-bal", "Baltimore", "Orioles", "BAL", "#DF4601"), ("mlb-bos", "Boston", "Red Sox", "BOS", "#BD3039"),
        ("mlb-chc", "Chicago", "Cubs", "CHC", "#0E3386"), ("mlb-cws", "Chicago", "White Sox", "CWS", "#27251F"),
        ("mlb-cin", "Cincinnati", "Reds", "CIN", "#C6011F"), ("mlb-cle", "Cleveland", "Guardians", "CLE", "#00385D"),
        ("mlb-col", "Colorado", "Rockies", "COL", "#33006F"), ("mlb-det", "Detroit", "Tigers", "DET", "#0C2340"),
        ("mlb-hou", "Houston", "Astros", "HOU", "#002D62"), ("mlb-kc", "Kansas City", "Royals", "KC", "#004687"),
        ("mlb-laa", "Los Angeles", "Angels", "LAA", "#BA0021"), ("mlb-lad", "Los Angeles", "Dodgers", "LAD", "#005A9C"),
        ("mlb-mia", "Miami", "Marlins", "MIA", "#00A3E0"), ("mlb-mil", "Milwaukee", "Brewers", "MIL", "#12284B"),
        ("mlb-min", "Minnesota", "Twins", "MIN", "#002B5C"), ("mlb-nym", "New York", "Mets", "NYM", "#002D72"),
        ("mlb-nyy", "New York", "Yankees", "NYY", "#003087"), ("mlb-ath", "Athletics", "Athletics", "ATH", "#003831"),
        ("mlb-phi", "Philadelphia", "Phillies", "PHI", "#E81828"), ("mlb-pit", "Pittsburgh", "Pirates", "PIT", "#27251F"),
        ("mlb-sd", "San Diego", "Padres", "SD", "#2F241D"), ("mlb-sf", "San Francisco", "Giants", "SF", "#FD5A1E"),
        ("mlb-sea", "Seattle", "Mariners", "SEA", "#0C2C56"), ("mlb-stl", "St. Louis", "Cardinals", "STL", "#C41E3A"),
        ("mlb-tb", "Tampa Bay", "Rays", "TB", "#092C5C"), ("mlb-tex", "Texas", "Rangers", "TEX", "#003278"),
        ("mlb-tor", "Toronto", "Blue Jays", "TOR", "#134A8E"), ("mlb-wsh", "Washington", "Nationals", "WSH", "#AB0003"),
    ]),
    ("nhl", "NHL", [
        ("nhl-ana", "Anaheim", "Ducks", "ANA", "#F47A38"), ("nhl-bos", "Boston", "Bruins", "BOS", "#2b2b2b"),
        ("nhl-buf", "Buffalo", "Sabres", "BUF", "#002654"), ("nhl-cgy", "Calgary", "Flames", "CGY", "#C8102E"),
        ("nhl-car", "Carolina", "Hurricanes", "CAR", "#CC0000"), ("nhl-chi", "Chicago", "Blackhawks", "CHI", "#CF0A2C"),
        ("nhl-col", "Colorado", "Avalanche", "COL", "#6F263D"), ("nhl-cbj", "Columbus", "Blue Jackets", "CBJ", "#002654"),
        ("nhl-dal", "Dallas", "Stars", "DAL", "#006847"), ("nhl-det", "Detroit", "Red Wings", "DET", "#CE1126"),
        ("nhl-edm", "Edmonton", "Oilers", "EDM", "#041E42"), ("nhl-fla", "Florida", "Panthers", "FLA", "#041E42"),
        ("nhl-lak", "Los Angeles", "Kings", "LAK", "#2b2b2b"), ("nhl-min", "Minnesota", "Wild", "MIN", "#154734"),
        ("nhl-mtl", "Montreal", "Canadiens", "MTL", "#AF1E2D"), ("nhl-nsh", "Nashville", "Predators", "NSH", "#041E42"),
        ("nhl-njd", "New Jersey", "Devils", "NJD", "#CE1126"), ("nhl-nyi", "New York", "Islanders", "NYI", "#00539B"),
        ("nhl-nyr", "New York", "Rangers", "NYR", "#0038A8"), ("nhl-ott", "Ottawa", "Senators", "OTT", "#C52032"),
        ("nhl-phi", "Philadelphia", "Flyers", "PHI", "#F74902"), ("nhl-pit", "Pittsburgh", "Penguins", "PIT", "#2b2b2b"),
        ("nhl-sjs", "San Jose", "Sharks", "SJS", "#006D75"), ("nhl-sea", "Seattle", "Kraken", "SEA", "#001628"),
        ("nhl-stl", "St. Louis", "Blues", "STL", "#002F87"), ("nhl-tbl", "Tampa Bay", "Lightning", "TBL", "#002868"),
        ("nhl-tor", "Toronto", "Maple Leafs", "TOR", "#00205B"), ("nhl-uta", "Utah", "Mammoth", "UTA", "#6CACE4"),
        ("nhl-van", "Vancouver", "Canucks", "VAN", "#00205B"), ("nhl-vgk", "Vegas", "Golden Knights", "VGK", "#B4975A"),
        ("nhl-wsh", "Washington", "Capitals", "WSH", "#041E42"), ("nhl-wpg", "Winnipeg", "Jets", "WPG", "#041E42"),
    ]),
    ("mls", "Soccer", [
        ("mls-atl", "Atlanta", "United", "ATL", "#80000A"), ("mls-aus", "Austin", "FC", "ATX", "#00B140"),
        ("mls-clt", "Charlotte", "FC", "CLT", "#1A85C8"), ("mls-chi", "Chicago", "Fire", "CHI", "#7CCDEF"),
        ("mls-cin", "FC Cincinnati", "FC Cincinnati", "CIN", "#F05323"), ("mls-col", "Colorado", "Rapids", "COL", "#862633"),
        ("mls-clb", "Columbus", "Crew", "CLB", "#2b2b2b"), ("mls-dal", "FC Dallas", "FC Dallas", "DAL", "#BF0D3E"),
        ("mls-dc", "D.C.", "United", "DC", "#2b2b2b"), ("mls-hou", "Houston", "Dynamo", "HOU", "#FF6B00"),
        ("mls-mia", "Inter Miami", "Inter Miami", "MIA", "#F7B5CD"), ("mls-la", "LA", "Galaxy", "LA", "#00245D"),
        ("mls-lafc", "LAFC", "LAFC", "LAFC", "#2b2b2b"), ("mls-min", "Minnesota", "United", "MIN", "#8CD2F4"),
        ("mls-mtl", "CF Montréal", "CF Montréal", "MTL", "#0033A1"), ("mls-nsh", "Nashville", "SC", "NSH", "#ECE83A"),
        ("mls-ne", "New England", "Revolution", "NE", "#0A2240"), ("mls-nyc", "New York City", "FC", "NYC", "#6CACE4"),
        ("mls-nyrb", "New York", "Red Bulls", "NYRB", "#ED1E36"), ("mls-orl", "Orlando", "City", "ORL", "#633492"),
        ("mls-phi", "Philadelphia", "Union", "PHI", "#071B2C"), ("mls-por", "Portland", "Timbers", "POR", "#004812"),
        ("mls-rsl", "Real Salt Lake", "Real Salt Lake", "RSL", "#B30838"), ("mls-sd", "San Diego", "FC", "SD", "#2b2b2b"),
        ("mls-sj", "San Jose", "Earthquakes", "SJ", "#0067B1"), ("mls-sea", "Seattle", "Sounders", "SEA", "#5D9741"),
        ("mls-skc", "Sporting", "Kansas City", "SKC", "#93B1D7"), ("mls-stl", "St. Louis", "City SC", "STL", "#EC1458"),
        ("mls-tor", "Toronto", "FC", "TOR", "#B81137"), ("mls-van", "Vancouver", "Whitecaps", "VAN", "#00245E"),
    ]),
]

TEAMS = {t[0]: (league, t) for league, _name, teams in LEAGUES for t in teams}
# Nicknames too plain to find on their own: they need the city in front ("Atlanta United", "Austin FC")
PLAIN = {"fc", "united", "city", "sc", "fire", "sun", "sky", "magic", "heat", "storm", "wild", "stars", "jazz", "kings",
         "thunder", "union", "crew", "galaxy", "dream", "fever", "liberty", "mercury", "aces", "wings", "lynx", "sparks",
         "tempo", "blues", "flames", "lightning", "rapids", "dynamo", "tigers", "wildcats", "bulldogs", "huskies", "ducks",
         "cougars", "broncos", "cardinals", "rams", "giants", "jets", "panthers", "rangers", "hurricanes", "nationals",
         "reds", "royals", "spurs", "suns", "rockets", "nets", "bulls", "bears", "lions", "eagles", "saints",
         "cowboys", "titans", "athletics", "trojans", "rebels", "aggies", "bruins", "seminoles", "sooners", "kansas city"}
_nick_count = {}
for _id, (_lg, _t) in TEAMS.items():
    _nick_count[_t[2].lower()] = _nick_count.get(_t[2].lower(), 0) + 1

GUIDE_EVERY = 600
DAYS = 7

_jf = None
_admin = None
_dir = None
_lock = threading.RLock()
_picks = {}  # user id -> {"teams": [...], "notNow": bool}
_guide = {"at": 0, "programs": []}


def _pick_dir():
    for d in (os.environ.get("TEAMS_DIR"), "/skips/teams", "/tracks/benflix-teams", "/tmp/benflix-teams"):
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


def _save():
    if not _dir:
        return
    tmp = os.path.join(_dir, "picks.json.tmp")
    try:
        with open(tmp, "w") as f:
            json.dump(_picks, f)
        os.replace(tmp, os.path.join(_dir, "picks.json"))
    except OSError as e:
        print(f"teams: couldn't save picks: {e}", flush=True)


# Cities with a pro team: a college with the same name ("Texas", "Miami") needs its nickname too
_PRO_CITIES = {t[1].lower() for _id, (lg, t) in TEAMS.items() if lg != "college"}


def words_for(team_id):
    """The ways a team's name shows up in a guide ("Kansas City Chiefs", "Chiefs"; colleges: "Ohio State")"""
    league, (_id, city, nick, _short, _colour) = TEAMS[team_id]
    out = []
    full = nick if nick.lower().startswith(city.lower()) or city == nick else f"{city} {nick}"
    out.append(full)
    if nick.lower() not in PLAIN and _nick_count.get(nick.lower(), 0) == 1 and len(nick) > 3:
        out.append(nick)
    if league == "college" and city.lower() not in _PRO_CITIES:
        out.append(city)
    return out


def _pattern(team_ids):
    words = sorted({w for t in team_ids if t in TEAMS for w in words_for(t)}, key=len, reverse=True)
    if not words:
        return None
    # "Michigan" never matches "Michigan State" (or "Iowa", "Iowa State")
    return re.compile(r"(?<![\w])(" + "|".join(re.escape(w) for w in words) + r")(?![\w])(?!\s+State\b)", re.I)


def teams_in(text, team_ids):
    """Which of these teams a guide entry is about"""
    found = []
    for t in team_ids:
        pat = _pattern([t])
        if pat and pat.search(text or ""):
            found.append(t)
    return found


def _team_view(team_id):
    league, (tid, city, nick, short, colour) = TEAMS[team_id]
    name = nick if nick.lower().startswith(city.lower()) or city == nick else f"{city} {nick}"
    return {"id": tid, "league": league, "name": name, "nick": nick, "short": short, "colour": colour, "words": words_for(tid)}


def _refresh_guide():
    if time.time() - _guide["at"] < GUIDE_EVERY:
        return
    token, user = _admin() if _admin else (None, None)
    if not token or not _jf:
        return
    now = time.time()
    q = urllib.parse.urlencode(
        {
            "UserId": user or "",
            "MinEndDate": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now)),
            "MaxStartDate": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now + DAYS * 86400)),
            "Fields": "Overview",
            "EnableImages": "false",
            "EnableTotalRecordCount": "false",
            "Limit": "60000",
        }
    )
    try:
        status, data, _ = _jf("GET", "/LiveTv/Programs?" + q, token=token)
    except Exception as e:  # noqa: BLE001 (Jellyfin down: keep the old guide)
        print(f"teams: couldn't read the guide: {e}", flush=True)
        return
    if status != 200:
        return
    progs = []
    for p in json.loads(data or b"{}").get("Items") or []:
        progs.append(
            {
                "id": p.get("Id"),
                "channelId": p.get("ChannelId"),
                "channel": p.get("ChannelName"),
                "title": p.get("Name") or "",
                "episode": p.get("EpisodeTitle") or "",
                "start": p.get("StartDate"),
                "end": p.get("EndDate"),
                "text": " ".join(str(x or "") for x in (p.get("Name"), p.get("EpisodeTitle"), p.get("Overview"))),
            }
        )
    with _lock:
        _guide.update({"at": time.time(), "programs": progs})


def games_for(team_ids):
    """Every guide entry in the next 7 days about one of these teams, soonest first (on now first)"""
    if not team_ids:
        return []
    _refresh_guide()
    pat = _pattern(team_ids)
    out = []
    with _lock:
        progs = list(_guide["programs"])
    for p in progs:
        if not pat or not pat.search(p["text"]):
            continue
        hit = teams_in(p["text"], team_ids)
        out.append({k: p[k] for k in ("id", "channelId", "channel", "title", "episode", "start", "end")} | {"teams": hit})
    out.sort(key=lambda g: g["start"] or "")
    # One entry per channel and start time (some guides list a show twice)
    seen, kept = set(), []
    for g in out:
        k = (g["channelId"], g["start"])
        if k not in seen:
            seen.add(k)
            kept.append(g)
    return kept[:60]


def route(method, path, user, is_kids, read_json):
    """GET /api/teams (leagues + your picks), POST /api/teams ({"teams": [...]} or {"notNow": true}),
    GET /api/teams/games (your teams' games in the guide, next 7 days)"""
    if is_kids:
        return 200, {"leagues": [], "mine": [], "games": [], "kids": True}
    if not user:
        return 401, {"error": "Sign in first."}
    if path == "/api/teams" and method == "GET":
        with _lock:
            me = dict(_picks.get(user) or {})
        return 200, {
            "leagues": [{"key": k, "name": n, "teams": [_team_view(t[0]) for t in teams]} for k, n, teams in LEAGUES],
            "mine": [_team_view(t) for t in me.get("teams") or [] if t in TEAMS],
            "notNow": bool(me.get("notNow")),
        }
    if path == "/api/teams" and method == "POST":
        body = read_json() or {}
        with _lock:
            me = dict(_picks.get(user) or {})
            if "teams" in body:
                me["teams"] = [t for t in body.get("teams") or [] if t in TEAMS][:40]
            if "notNow" in body:
                me["notNow"] = bool(body.get("notNow"))
            _picks[user] = me
            _save()
        return 200, {"ok": True, "mine": [_team_view(t) for t in me.get("teams") or []], "notNow": bool(me.get("notNow"))}
    if path == "/api/teams/games" and method == "GET":
        with _lock:
            mine = list((_picks.get(user) or {}).get("teams") or [])
        return 200, {"games": games_for(mine), "mine": [_team_view(t) for t in mine if t in TEAMS]}
    return 404, {"error": "No such page."}


def start(jellyfin_call, admin):
    global _jf, _admin, _dir
    _jf, _admin = jellyfin_call, admin
    _dir = _pick_dir()
    if _dir:
        try:
            with open(os.path.join(_dir, "picks.json")) as f:
                _picks.update(json.load(f))
        except (OSError, ValueError):
            pass
