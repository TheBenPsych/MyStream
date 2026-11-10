"""
Benflix Home extras on Tower: the weather line under the greeting.

Loaded by benflix-games-helper.py (it sits next to it on Tower).
  * Ben types the town once on the Server page (Tools > Home weather). Tower finds it with Open-Meteo's
    free place search (no key, no account) and remembers it.
  * Once an hour Tower asks Open-Meteo for that town's weather and keeps the answer.
  * /api/home/weather gives every TV, phone and browser the same short line, like "52°, cloudy" and
    "rain after 6 pm". The TVs and phones never use their own location.

Saves its notes in /skips/home (next to the skip times).
"""

import json
import os
import threading
import time
import urllib.parse
import urllib.request

_dir = None
_lock = threading.RLock()
_wake = threading.Event()
_weather = {}
_place = {}
_last_problem = None

UA = "Benflix/1.0 (home media app; https://github.com/TheBenPsych/MyStream)"
EVERY = 3600  # seconds between weather checks
GEO_URL = "https://geocoding-api.open-meteo.com/v1/search"
WEATHER_URL = "https://api.open-meteo.com/v1/forecast"

# Open-Meteo's weather codes (WMO) in plain words
WORDS = {
    0: "clear", 1: "mostly clear", 2: "partly cloudy", 3: "cloudy",
    45: "foggy", 48: "foggy",
    51: "light drizzle", 53: "drizzle", 55: "heavy drizzle", 56: "freezing drizzle", 57: "freezing drizzle",
    61: "light rain", 63: "rain", 65: "heavy rain", 66: "freezing rain", 67: "freezing rain",
    71: "light snow", 73: "snow", 75: "heavy snow", 77: "snow",
    80: "showers", 81: "showers", 82: "heavy showers",
    85: "snow showers", 86: "snow showers",
    95: "thunderstorms", 96: "thunderstorms", 99: "thunderstorms",
}


def _kind(code):
    """What's coming, for "rain after 6 pm" (None = nothing to mention)"""
    if code is None:
        return None
    if code in (71, 73, 75, 77, 85, 86):
        return "snow"
    if code in (95, 96, 99):
        return "storms"
    if code in (51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82):
        return "rain"
    return None


def _pick_dir():
    for d in (os.environ.get("HOME_DIR"), "/skips/home", "/tracks/benflix-home"):
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


def _get(url, timeout=15):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read() or b"{}")


def start():
    """Called once by the helper when it starts"""
    global _dir, _place, _weather
    _dir = _pick_dir()
    _place = _read("place.json", {}) or {}
    _weather = _read("weather.json", {}) or {}
    threading.Thread(target=_hourly, name="home-weather", daemon=True).start()
    print(f"home: weather {'for ' + _place.get('name', '') if _place.get('name') else 'not set up yet'}", flush=True)


def _hourly():
    time.sleep(20)
    while True:
        if _place.get("lat") is not None:
            try:
                _check()
            except Exception as e:  # noqa: BLE001
                global _last_problem
                _last_problem = str(e)
                print(f"home: couldn't check the weather: {e}", flush=True)
        _wake.wait(EVERY)
        _wake.clear()


def _find(town):
    """The best match for what Ben typed, like "Springfield, IL" or "Leeds" """
    text = town.strip()
    # Open-Meteo searches by place name only, so "Springfield, IL" is searched as "Springfield" and the
    # rest is used to pick the right one
    name, _, rest = text.partition(",")
    q = urllib.parse.urlencode({"name": name.strip(), "count": 10, "language": "en", "format": "json"})
    found = _get(f"{GEO_URL}?{q}").get("results") or []
    if not found:
        return None
    hint = rest.strip().lower()

    def score(p):
        if not hint:
            return 0
        words = " ".join(str(p.get(k) or "") for k in ("admin1", "admin2", "country", "country_code")).lower()
        codes = {str(p.get("country_code") or "").lower()}
        return 2 if hint in codes else 1 if hint in words or any(w in words for w in hint.split()) else 0

    best = max(found, key=lambda p: (score(p), p.get("population") or 0))
    label = ", ".join(x for x in (best.get("name"), best.get("admin1"), best.get("country")) if x)
    return {"name": best.get("name") or name, "label": label, "lat": best.get("latitude"), "lon": best.get("longitude")}


def _hour_words(hour):
    if hour == 0:
        return "midnight"
    if hour == 12:
        return "noon"
    return f"{hour % 12} {'am' if hour < 12 else 'pm'}"


def _check():
    """Asks Open-Meteo for the town's weather now and for the rest of today"""
    global _weather, _last_problem
    units = "celsius" if _place.get("units") == "C" else "fahrenheit"
    q = urllib.parse.urlencode({
        "latitude": _place["lat"],
        "longitude": _place["lon"],
        "current": "temperature_2m,weather_code",
        "hourly": "weather_code,precipitation_probability",
        "daily": "temperature_2m_max",
        "temperature_unit": units,
        "timezone": "auto",
        "forecast_days": 2,
    })
    d = _get(f"{WEATHER_URL}?{q}")
    cur = d.get("current") or {}
    temp = cur.get("temperature_2m")
    code = cur.get("weather_code")
    if temp is None:
        raise ValueError("Open-Meteo didn't send a temperature")
    # What's coming later today (in the town's own time): the first hour of rain, snow or storms
    later = None
    hourly = d.get("hourly") or {}
    now_text = cur.get("time") or ""
    today = now_text[:10]
    now_hour = int(now_text[11:13]) if len(now_text) >= 13 else 0
    for t, c, p in zip(hourly.get("time") or [], hourly.get("weather_code") or [], hourly.get("precipitation_probability") or []):
        if t[:10] != today:
            continue
        hour = int(t[11:13])
        if hour <= now_hour:
            continue
        kind = _kind(c)
        if kind and (p is None or p >= 50):
            if _kind(code) == kind:
                break  # It's already raining: "rain" is in the first part
            later = f"{kind} after {_hour_words(hour)}"
            break
    if later is None:
        highs = (d.get("daily") or {}).get("temperature_2m_max") or []
        if highs and highs[0] is not None and round(highs[0]) > round(temp):
            later = f"high of {round(highs[0])}°"
    word = WORDS.get(code, "")
    with _lock:
        _weather = {"temp": round(temp), "word": word, "later": later, "units": _place.get("units") or "F", "at": int(time.time()), "place": _place.get("name")}
        _write("weather.json", _weather)
    _last_problem = None
    return _weather


def _line(w):
    first = f"{w['temp']}°" + (f", {w['word']}" if w.get("word") else "")
    return " · ".join(x for x in (first, w.get("later")) if x)


def weather():
    """For everyone signed in: the short weather line (ready=False until a town is set and checked)"""
    w = _weather
    # Old weather is worse than none: after 3 hours without a check, leave the line off
    if not w or not _place.get("lat") or time.time() - w.get("at", 0) > 3 * 3600:
        return {"ready": False}
    return {"ready": True, "line": _line(w), "temp": w["temp"], "word": w.get("word"), "later": w.get("later"), "units": w.get("units"), "at": w["at"]}


def set_town(body):
    """Ben's town (and °F or °C) from the Server page. An empty town turns the weather off."""
    global _place, _weather
    town = str(body.get("town") or "").strip()[:120]
    units = "C" if str(body.get("units") or "").upper().startswith("C") else "F"
    if not town:
        with _lock:
            _place, _weather = {}, {}
            _write("place.json", _place)
            _write("weather.json", _weather)
        return 200, {"message": "Weather turned off. Home shows just the greeting.", **town_status()}
    try:
        found = _find(town)
    except Exception as e:  # noqa: BLE001
        return 502, {"error": f"Tower couldn't look up places right now ({e}). Try again in a minute."}
    if not found:
        return 404, {"error": f"Couldn't find a place called \"{town}\". Try just the town's name, or add the state or country after a comma."}
    with _lock:
        _place = dict(found, typed=town, units=units)
        _write("place.json", _place)
    try:
        w = _check()
        msg = f"Weather is set to {found['label']}. Right now: {_line(w)}."
    except Exception as e:  # noqa: BLE001
        msg = f"Weather is set to {found['label']}. Tower will check it within the hour ({e})."
    return 200, {"message": msg, **town_status()}


def town_status():
    return {
        "town": _place.get("typed") or "",
        "place": _place.get("label") or "",
        "units": _place.get("units") or "F",
        "weather": weather(),
        "problem": _last_problem,
    }


def route(method, path, read_json, is_admin):
    """/api/home/... (anyone signed in) and /api/admin/home/... (admin). Returns (status, answer) or None."""
    if method == "GET" and path == "/api/home/weather":
        return 200, weather()
    if path == "/api/admin/home/town":
        if not is_admin:
            return 403, {"error": "Only the server admin can change that."}
        if method == "GET":
            return 200, town_status()
        if method == "POST":
            return set_town(read_json())
    return None
