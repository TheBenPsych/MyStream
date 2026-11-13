/* Benflix web: the shared pieces every page uses (talking to the helper and Jellyfin, profiles,
   the page switcher, the top bar, pop-ups, cards and rows). Plain JavaScript, no build step. */
"use strict";

const B = (window.B = {});
const $ = (s, r) => (r || document).querySelector(s);
const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const enc = encodeURIComponent;

function store(k, v) { try { v === undefined ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch (e) {} }
function load(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
function loadJson(k, d) { try { const v = JSON.parse(load(k)); return v == null ? d : v; } catch (e) { return d; } }

// ---------- icons (simple line icons) ----------
const P = {
  film: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M7 3v18M17 3v18M3 8h4M3 12h4M3 16h4M17 8h4M17 12h4M17 16h4"/>',
  tv: '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8M12 17v4"/>',
  live: '<circle cx="12" cy="12" r="2"/><path d="M8.5 15.5a5 5 0 0 1 0-7M15.5 8.5a5 5 0 0 1 0 7M5.6 18.4a9 9 0 0 1 0-12.8M18.4 5.6a9 9 0 0 1 0 12.8"/>',
  pad: '<path d="M6 11h4M8 9v4M15 12h.01M18 10h.01"/><path d="M17.3 5H6.7a4 4 0 0 0-4 3.6L2 15a3 3 0 0 0 5.4 2l1.4-1.6a2 2 0 0 1 1.5-.7h3.4a2 2 0 0 1 1.5.7L16.6 17a3 3 0 0 0 5.4-2l-.7-6.4a4 4 0 0 0-4-3.6Z"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  down: '<path d="m6 9 6 6 6-6"/>', up: '<path d="m18 15-6-6-6 6"/>',
  left: '<path d="m15 18-6-6 6-6"/>', right: '<path d="m9 18 6-6-6-6"/>',
  play: '<path d="M7 4.5v15l12-7.5-12-7.5Z" fill="currentColor"/>', stop: '<rect x="6.5" y="6.5" width="11" height="11" rx="1.5" fill="currentColor"/>', pause: '<path d="M8 5v14M16 5v14" stroke-width="3.2"/>',
  plus: '<path d="M12 5v14M5 12h14"/>', check: '<path d="m5 12 5 5 9-10"/>', x: '<path d="M6 6l12 12M18 6 6 18"/>',
  star: '<path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9L12 3Z"/>',
  thumb: '<path d="M7 10v11M7 10l4-7a2 2 0 0 1 3 2l-1 5h6a2 2 0 0 1 2 2.3l-1.4 7A2 2 0 0 1 17.6 21H7"/>',
  more: '<circle cx="5" cy="12" r="1.3" fill="currentColor"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/><circle cx="19" cy="12" r="1.3" fill="currentColor"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z"/>',
  server: '<rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 7.5h.01M7 16.5h.01"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.5v.7M12 17h.01"/>',
  out: '<path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l-5-5 5-5M5 12h11"/>',
  swap: '<path d="M7 7h12l-3-3M17 17H5l3 3"/>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  bell: '<path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0"/>',
  flag: '<path d="M4 22V4M4 4h13l-2 4 2 4H4"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 16v-4M12 8h.01"/>',
  back10: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><text x="12" y="15.5" text-anchor="middle" font-size="7" font-weight="700" fill="currentColor" stroke="none">10</text>',
  fwd10: '<path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/><text x="12" y="15.5" text-anchor="middle" font-size="7" font-weight="700" fill="currentColor" stroke="none">10</text>',
  next: '<path d="M5 4.5v15l10-7.5-10-7.5Z" fill="currentColor"/><path d="M19 5v14" stroke-width="2.6"/>',
  sub: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 13h4M13 13h4M7 16h10"/>',
  vol: '<path d="M11 5 6 9H2v6h4l5 4V5ZM15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14"/>',
  mute: '<path d="M11 5 6 9H2v6h4l5 4V5ZM22 9l-6 6M16 9l6 6"/>',
  full: '<path d="M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3"/>',
  pip: '<rect x="2" y="4" width="20" height="16" rx="2"/><rect x="12" y="11" width="8" height="7" rx="1"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
  dl: '<path d="M12 3v12M7 10l5 5 5-5M5 21h14"/>',
  trailer: '<rect x="2" y="5" width="20" height="14" rx="3"/><path d="m10 9 5 3-5 3V9Z" fill="currentColor"/>',
  heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8Z"/>',
  restart: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  phone: '<rect x="6" y="2" width="12" height="20" rx="2"/><path d="M11 18h2"/>',
  note: '<path d="M9 18V5l11-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="17" cy="16" r="3"/>',
  key: '<circle cx="8" cy="15" r="4"/><path d="m10.8 12.2 9.2-9.2M17 6l3 3M14 9l2 2"/>',
  book: '<path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5Z"/><path d="M4 19a2 2 0 0 1 2-2h13"/>',
};
const I = (name, cls) => `<svg class="i ${cls || ""}" viewBox="0 0 24 24" aria-hidden="true">${P[name] || ""}</svg>`;
B.I = I;

// ---------- doors ----------
const DOORS = {
  movies: { key: "movies", name: "Movies", desc: "Films for any night", color: "#FFB547", icon: "film" },
  shows: { key: "shows", name: "Shows", desc: "Series and episodes", color: "#7AA7FF", icon: "tv" },
  live: { key: "live", name: "Live TV", desc: "Channels playing now", color: "#3DD6C6", icon: "live" },
  games: { key: "games", name: "Games", desc: "Play in your browser", color: "#FF6F91", icon: "pad" },
};
B.DOORS = DOORS;
B.logo = () => `<span class="logo"><span class="sq"><i></i><i></i><i></i><i></i></span>Benflix</span>`;

// ---------- profiles (people signed in on this browser) ----------
// Each person keeps their own Jellyfin sign-in, like profiles on the TV.
B.profiles = () => loadJson("benflix.profiles", []);
B.saveProfiles = (list) => store("benflix.profiles", JSON.stringify(list));
B.me = null;
B.pick = (id) => {
  B.me = B.profiles().find((p) => p.id === id) || null;
  if (B.me) store("benflix.last", B.me.id);
  B.cache = {};
  if (B.me) B.englishSubs();
};
// Subtitles are English unless someone picked another language. A profile that never picked one
// gets English saved on the server, so Jellyfin picks English subtitle tracks for this player too.
B.englishSubs = async () => {
  const id = B.me && B.me.id;
  try {
    const me = await B.get(`/Users/${enc(id)}`);
    const cfg = me && me.Configuration;
    if (!cfg || cfg.SubtitleLanguagePreference || !B.me || B.me.id !== id) return;
    cfg.SubtitleLanguagePreference = "eng";
    await B.jf("POST", `/Users/Configuration?userId=${enc(id)}`, cfg).catch(() => B.jf("POST", `/Users/${enc(id)}/Configuration`, cfg));
  } catch (e) { /* tries again next time this profile is picked */ }
};
B.addProfile = (s) => {
  const list = B.profiles().filter((p) => p.id !== s.id);
  list.push(s);
  B.saveProfiles(list);
};
B.forget = (id) => B.saveProfiles(B.profiles().filter((p) => p.id !== id));
B.browserId = () => {
  let d = load("benflix.browser");
  if (!d) { d = Math.random().toString(36).slice(2, 10); store("benflix.browser", d); }
  return d;
};
B.newDevice = () => B.browserId() + "-" + Math.random().toString(36).slice(2, 7);
B.initials = (n) => String(n || "?").trim().charAt(0).toUpperCase();
B.picOf = (p, size) => {
  const colors = ["#FFB547", "#7AA7FF", "#3DD6C6", "#FF6F91"];
  const c = colors[(String(p.name || "").charCodeAt(0) || 0) % 4];
  const url = p.image ? `api/web/jf/Users/${enc(p.id)}/Images/Primary?tag=${enc(p.image)}&fillHeight=${size || 200}&quality=90&api_key=${enc(p.token)}` : "";
  return { url, color: c };
};
B.picHtml = (p, cls, size) => {
  const { url, color } = B.picOf(p, size);
  return `<span class="pic ${cls || ""}" style="background-color:${color};${url ? `background-image:url('${url}')` : ""}">${url ? "" : esc(B.initials(p.name))}</span>`;
};

// ---------- talking to the helper ----------
B.call = async (method, path, body, opts) => {
  const who = (opts && opts.profile) || B.me;
  const headers = { Accept: "application/json" };
  if (who) { headers["X-Benflix-Token"] = who.token; headers["X-Benflix-Device"] = who.device || ""; }
  if (opts && opts.device) headers["X-Benflix-Device"] = opts.device;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  let r;
  try {
    r = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch (e) {
    const err = new Error("Couldn't reach Benflix on the server. Is it on?"); err.status = 0; throw err;
  }
  const text = await r.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
  if (r.status === 401 && who && !(opts && opts.quiet401)) {
    who.expired = true;
    B.saveProfiles(B.profiles().map((p) => (p.id === who.id ? Object.assign(p, { expired: true }) : p)));
    if (who === B.me) { B.me = null; B.go("#/who"); }
    throw new Error("Please sign in again.");
  }
  if (!r.ok) { const err = new Error((data && (data.error || data.message || data.Message)) || `Something went wrong (${r.status}).`); err.status = r.status; throw err; }
  return data;
};
B.jf = (method, path, body, opts) => B.call(method, "api/web/jf" + path, body, opts);
B.get = (path) => B.jf("GET", path);
B.uid = () => enc(B.me.id);
B.cache = {};
B.cached = async (key, ms, fn) => {
  const c = B.cache[key];
  if (c && Date.now() - c.t < ms) return c.v;
  const v = await fn();
  B.cache[key] = { t: Date.now(), v };
  return v;
};

// Pictures from Jellyfin (an <img> can't send the sign-in header, so it rides in the address)
B.img = (id, type, o) => {
  o = o || {};
  if (!id) return "";
  let q = `quality=${o.q || 88}`;
  if (o.h) q += `&fillHeight=${o.h}`;
  if (o.w) q += `&fillWidth=${o.w}`;
  if (o.tag) q += `&tag=${enc(o.tag)}`;
  return `api/web/jf/Items/${enc(id)}/Images/${type || "Primary"}${o.index != null ? "/" + o.index : ""}?${q}&api_key=${enc(B.me.token)}`;
};
B.poster = (it, h) => {
  const tags = it.ImageTags || {};
  if (tags.Primary) return B.img(it.Id, "Primary", { h: h || 400, tag: tags.Primary });
  if (it.SeriesPrimaryImageTag) return B.img(it.SeriesId, "Primary", { h: h || 400, tag: it.SeriesPrimaryImageTag });
  if (it.AlbumPrimaryImageTag) return B.img(it.AlbumId, "Primary", { h: h || 400 });
  return "";
};
B.backdrop = (it, w) => {
  if (it.BackdropImageTags && it.BackdropImageTags.length) return B.img(it.Id, "Backdrop", { w: w || 1600, tag: it.BackdropImageTags[0], index: 0 });
  if (it.ParentBackdropImageTags && it.ParentBackdropImageTags.length) return B.img(it.ParentBackdropItemId, "Backdrop", { w: w || 1600, tag: it.ParentBackdropImageTags[0], index: 0 });
  if (it.ImageTags && it.ImageTags.Thumb) return B.img(it.Id, "Thumb", { w: w || 1600 });
  return "";
};
B.thumb = (it, w) => {
  const tags = it.ImageTags || {};
  if (it.Type === "Episode" && tags.Primary) return B.img(it.Id, "Primary", { w: w || 520, tag: tags.Primary });
  if (tags.Thumb) return B.img(it.Id, "Thumb", { w: w || 520, tag: tags.Thumb });
  if (it.ParentThumbItemId) return B.img(it.ParentThumbItemId, "Thumb", { w: w || 520 });
  return B.backdrop(it, w || 520) || B.poster(it);
};

// ---------- time words ----------
const TICKS = 10000000;
B.TICKS = TICKS;
B.minutes = (ticks) => Math.round((ticks || 0) / TICKS / 60);
B.len = (min) => (min >= 60 ? `${Math.floor(min / 60)} h ${min % 60 ? (min % 60) + " min" : ""}`.trim() : `${min} min`);
B.clock = (d) => d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
B.hms = (s) => {
  s = isFinite(s) ? Math.max(0, Math.floor(s || 0)) : 0;
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
  return (h ? h + ":" + String(m).padStart(2, "0") : m) + ":" + String(x).padStart(2, "0");
};
B.when = (iso) => {
  const d = new Date(iso); if (isNaN(d)) return "";
  const today = new Date();
  return d.toDateString() === today.toDateString() ? B.clock(d) : d.toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" }) + ", " + B.clock(d);
};
B.day = (iso) => { const d = new Date(iso); return isNaN(d) ? "" : d.toLocaleDateString([], { month: "long", day: "numeric", year: d.getFullYear() === new Date().getFullYear() ? undefined : "numeric" }); };
B.greeting = () => { const h = new Date().getHours(); return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening"; };

// ---------- pages ----------
const ROUTES = [];
let timers = [], leaving = [];
B.route = (re, fn) => ROUTES.push([re, fn]);
B.every = (ms, fn) => timers.push(setInterval(fn, ms));
B.onLeave = (fn) => leaving.push(fn);
B.go = (hash, opts) => {
  if (location.hash === hash) B.render();
  else if (opts && opts.replace) location.replace(hash);
  else location.hash = hash;
};
B.back = () => (history.length > 1 ? history.back() : B.go("#/home"));
B.page = () => $("#page");
B.nav = 0;
B.render = async () => {
  timers.forEach(clearInterval); timers = [];
  const l = leaving; leaving = [];
  l.forEach((f) => { try { f(); } catch (e) {} });
  B.closeAll();
  const hash = location.hash || "#/who";
  const nav = ++B.nav;
  if (!B.me && !/^#\/(who|signin)/.test(hash)) {
    const last = load("benflix.last");
    if (last && sessionStorage.getItem("benflix.picked") === last) B.pick(last);
    if (!B.me) return B.go("#/who");
  }
  for (const [re, fn] of ROUTES) {
    const m = hash.slice(1).match(re);
    if (m) {
      window.scrollTo(0, 0);
      B.placeName = "";
      try { await fn(m, nav); } catch (e) { if (nav === B.nav) B.fail(e); }
      if (nav === B.nav) B.where(hash);
      return;
    }
  }
  B.go(B.me ? "#/home" : "#/who");
};
B.alive = (nav) => nav === B.nav;
B.fail = (e) => {
  console.error(e);
  B.page().innerHTML = B.top("") + `<div class="center"><div><div class="h1">Something went wrong</div><p class="sub" style="overflow-wrap:anywhere;max-width:560px">${esc(e.message || e)}</p><button class="btn white" onclick="B.render()">Try again</button></div></div>`;
  B.wireTop();
};
window.addEventListener("hashchange", B.render);
B.q = (k) => new URLSearchParams((location.hash.split("?")[1] || "")).get(k);

// ---------- "Where am I" line (Idea Box 24) ----------
// Deeper pages show a small "Movies > Action > Die Hard" in the top bar, in place of the door name.
// Click any part to jump back to it. The trail lives for this tab only.
const TRAIL_KEY = "benflix.trail";
B.placeName = "";
B.where = (hash) => {
  let trail = [];
  try { trail = JSON.parse(sessionStorage.getItem(TRAIL_KEY) || "[]"); } catch (e) {}
  const page = hash.split("?")[0];
  if (/^#\/(home|who|signin)$/.test(page)) trail = [];
  const name = B.placeName || "";
  const at = trail.findIndex((t) => t.h === hash);
  if (at >= 0) trail = trail.slice(0, at + 1);
  else if (name) trail.push({ h: hash, n: name.slice(0, 80) });
  trail = trail.slice(-6);
  try { sessionStorage.setItem(TRAIL_KEY, JSON.stringify(trail)); } catch (e) {}
  let place = $(".top .place");
  if (!place && $(".top .homelink") && trail.length >= 2) {
    place = document.createElement("span");
    place.className = "place";
    $(".top .homelink").after(place);
  }
  if (!place || trail.length < 2 || /^#\/(home|who|signin|play|game|read)/.test(page)) return;
  const steps = trail.slice(-4);
  place.classList.add("where");
  place.innerHTML = steps.map((t, i) => (i === steps.length - 1 || t.h === hash
    ? `<b>${esc(t.n)}</b>`
    : `<a href="${esc(t.h)}">${esc(t.n)}</a>`)).join('<span class="sep">›</span>');
};

// ---------- top bar ----------
B.top = (place, color, opts) => {
  opts = opts || {};
  if (place) B.placeName = place;
  const now = new Date();
  const t = B.clock(now).split(" ");
  return `<header class="top ${opts.solid ? "solid" : ""}">
    <a class="homelink" href="#/home" title="Home">${B.logo()}</a>
    ${B.me && B.me.kids ? '<span class="pbadge">Kids</span>' : ""}
    ${place ? `<span class="place" style="color:${color || "var(--ink)"}">${esc(place)}</span>` : ""}
    <div class="right">
      <span class="clock" id="clock">${esc(t[0])}<small>${esc(t[1] || "")}</small></span>
      <a class="circle" href="#/search" title="Search">${I("search")}</a>
      <button class="me" id="meBtn" title="${esc(B.me ? B.me.name : "")}">${B.me ? B.picHtml(B.me, "", 100) : ""}${B.newsDot ? '<span class="dotnew"></span>' : ""}${I("down")}</button>
    </div>
  </header>`;
};
B.wireTop = () => {
  const b = $("#meBtn");
  if (b) b.onclick = () => B.openMenu();
};
setInterval(() => {
  const c = $("#clock"); if (!c) return;
  const t = B.clock(new Date()).split(" ");
  c.innerHTML = `${esc(t[0])}<small>${esc(t[1] || "")}</small>`;
}, 10000);

// ---------- pop-ups ----------
B.closeAll = () => $$(".shade, .modal, .side, .menu-card, .dropdown").forEach((e) => e.remove());
B.shade = (onClose) => {
  const s = document.createElement("div");
  s.className = "shade";
  s.onclick = () => { B.closeAll(); if (onClose) onClose(); };
  document.body.appendChild(s);
  return s;
};
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && $(".shade")) { e.stopPropagation(); B.closeAll(); }
}, true);
// Small "are you sure" pop-up. Resolves true when the person presses the main button.
B.ask = (title, text, okLabel, cancelLabel) => new Promise((done) => {
  B.closeAll();
  B.shade(() => done(false));
  const m = document.createElement("div");
  m.className = "modal";
  m.innerHTML = `<h3>${esc(title)}</h3>${text ? `<p>${esc(text)}</p>` : ""}<div class="acts"><button class="btn white" data-a="0">${esc(cancelLabel || "Stay")}</button><button class="btn" data-a="1">${esc(okLabel || "Yes")}</button></div>`;
  m.onclick = (e) => { const b = e.target.closest("[data-a]"); if (!b) return; B.closeAll(); done(b.dataset.a === "1"); };
  document.body.appendChild(m);
  $("button", m).focus();
});
// Small pop-up with a box to type in. Resolves the text, or null when cancelled.
B.askText = (title, label, value, opts) => new Promise((done) => {
  opts = opts || {};
  B.closeAll();
  B.shade(() => done(null));
  const m = document.createElement("form");
  m.className = "modal";
  m.innerHTML = `<h3>${esc(title)}</h3>${label ? `<p>${esc(label)}</p>` : ""}
    ${opts.long ? `<textarea class="field" name="v"></textarea>` : `<input class="field" name="v" type="${opts.password ? "password" : "text"}" autocomplete="off">`}
    <div class="acts"><button class="btn white" type="submit">${esc(opts.ok || "OK")}</button><button class="btn" type="button" data-c>Cancel</button></div>`;
  const f = m.elements.v;
  f.value = value || "";
  m.onsubmit = (e) => { e.preventDefault(); B.closeAll(); done(f.value); };
  m.querySelector("[data-c]").onclick = () => { B.closeAll(); done(null); };
  document.body.appendChild(m);
  f.focus(); f.select && f.select();
});
// Side panel (TV) / sheet (phone). Returns the panel element.
B.side = (html, onClose) => {
  B.closeAll();
  B.shade(onClose);
  const s = document.createElement("div");
  s.className = "side";
  s.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button>` + html;
  s.addEventListener("click", (e) => { if (e.target.closest("[data-close]")) { B.closeAll(); if (onClose) onClose(); } });
  document.body.appendChild(s);
  const f = s.querySelector("button:not(.x), a, input");
  if (f) f.focus();
  return s;
};
// Small list that pops out beside a button
B.dropdown = (anchor, options, current, onPick) => {
  B.closeAll();
  const d = document.createElement("div");
  d.className = "dropdown";
  d.innerHTML = options.map((o, i) => `<button data-i="${i}" class="${o.value === current ? "on" : ""}">${esc(o.label)}</button>`).join("");
  const r = anchor.getBoundingClientRect();
  d.style.left = Math.min(r.left + window.scrollX, window.innerWidth - 240) + "px";
  d.style.top = r.bottom + window.scrollY + 6 + "px";
  document.body.appendChild(d);
  setTimeout(() => {
    const away = (e) => { if (!d.contains(e.target)) { d.remove(); document.removeEventListener("click", away, true); } };
    document.addEventListener("click", away, true);
  });
  d.onclick = (e) => { const b = e.target.closest("[data-i]"); if (!b) return; d.remove(); onPick(options[+b.dataset.i].value); };
  const on = d.querySelector(".on") || d.querySelector("button");
  if (on) on.focus();
};
// Notices (Redesign 2, approved three levels): quick "done" words are one white pill at the bottom for 3 seconds
let toastTimer;
B.toast = (text) => {
  let t = $("#toast");
  if (!t) { t = document.createElement("div"); t.id = "toast"; document.body.appendChild(t); }
  t.textContent = text; t.classList.remove("hidden");
  clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.add("hidden"), 3000);
};
// Tap on the shoulder: a small card under the top bar on the right for 7 seconds, with a thin timer line.
// It never takes the keyboard's focus; clicking it opens it. One at a time, oldest first.
const cornerQueue = [];
let cornerOn = null;
B.corner = (card) => {
  if (!card || (cornerOn && cornerOn.id === card.id) || cornerQueue.some((c) => c.id === card.id)) return;
  cornerQueue.push(card);
  if (!cornerOn) nextCorner();
};
const nextCorner = () => {
  const card = cornerQueue.shift();
  cornerOn = card || null;
  if (!card) return;
  const el = document.createElement("div");
  el.className = "corner";
  el.innerHTML = `${card.img ? `<span class="cimg" style="background-image:url('${esc(card.img)}')"></span>` : ""}<div class="cw"><b>${esc(card.title)}</b><span>${esc(card.line || "")}${card.href ? " · Click to open" : ""}</span></div><i class="ctime"></i>`;
  if (card.href) el.onclick = () => { done(); B.go(card.href); };
  document.body.appendChild(el);
  let gone = false;
  const done = () => { if (gone) return; gone = true; el.remove(); nextCorner(); };
  setTimeout(done, 7000);
};
// Needs you: a centre card on the web that waits for Got it (messages from Ben)
B.needsYou = (label, title, text) => new Promise((resolve) => {
  B.closeAll();
  B.shade(() => resolve());
  const m = document.createElement("div");
  m.className = "modal needs";
  m.innerHTML = `<div class="nlabel">${esc(label)}</div><h3>${esc(title)}</h3>${text ? `<p>${esc(text)}</p>` : ""}<div class="acts"><button class="btn white" data-ok>Got it</button></div>`;
  m.onclick = (e) => { if (e.target.closest("[data-ok]")) { B.closeAll(); resolve(); } };
  document.body.appendChild(m);
  $("button", m).focus();
});
B.busy = async (btn, fn) => {
  if (btn) btn.disabled = true;
  try { const msg = await fn(); if (msg) B.toast(msg); } catch (err) { B.toast(err.message); }
  if (btn) btn.disabled = false;
};
B.spinner = () => `<div class="spin"></div>`;

// ---------- cards and rows ----------
B.FIELDS = "Overview,Genres,ProductionYear,OfficialRating,CommunityRating,CriticRating,RunTimeTicks,DateCreated,PremiereDate,ChildCount,RecursiveItemCount,ProviderIds,Path,ParentId,Status,EndDate,AirDays,Taglines,PrimaryImageAspectRatio";
B.items = {}; // id -> item, for the focus line and clicks
B.keep = (list) => { (list || []).forEach((it) => { B.items[it.Id] = it; }); return list || []; };
B.isNew = (it) => it.DateCreated && Date.now() - new Date(it.DateCreated) < 7 * 86400000;
B.hrefOf = (it) => {
  if (it.Type === "Episode") return `#/show/${it.SeriesId}?ep=${it.Id}`;
  if (it.Type === "Series") return `#/show/${it.Id}`;
  if (it.Type === "Season") return `#/show/${it.SeriesId}?season=${it.Id}`;
  if (it.Type === "BoxSet") return `#/collection/${it.Id}`;
  if (it.Type === "Person") return `#/person/${it.Id}`;
  if (it.Type === "TvChannel") return `#/live?play=${it.Id}`;
  return `#/movie/${it.Id}`;
};
// Redesign 2 (rows, posters and covers): the door colour for the short line on a card with no picture
B.doorOf = (it) => {
  const t = (it && it.Type) || "";
  if (t === "Movie" || t === "BoxSet") return "var(--movies)";
  if (t === "TvChannel" || t === "Program") return "var(--live)";
  if (/^(MusicAlbum|MusicArtist|Audio|Playlist)$/.test(t)) return "var(--music)";
  if (t === "Book" || t === "AudioBook") return "var(--books)";
  return "var(--shows)";
};
// Pictures fade in when they arrive (dark card first). Covers already in the browser's memory show at once.
(() => {
  const done = (img) => img.classList.add("in");
  const isArt = (t) => t && t.tagName === "IMG" && t.classList.contains("art");
  document.addEventListener("load", (e) => { if (isArt(e.target)) done(e.target); }, true);
  document.addEventListener("error", (e) => {
    if (!isArt(e.target)) return;
    const im = e.target.parentElement;
    e.target.remove();
    // No picture after all: show the dark card with the name
    if (im && !im.querySelector(".noart")) {
      const card = im.closest(".poster, .mcard, .wide, .chcard");
      const nm = card && card.querySelector(".nm");
      const name = card ? card.getAttribute("title") || (nm ? nm.textContent : "") : "";
      if (name) {
        const d = document.createElement("div");
        d.className = "noart";
        d.textContent = name;
        const it = card.dataset.id && B.items[card.dataset.id];
        if (it && it.Type) d.style.setProperty("--door", B.doorOf(it));
        im.appendChild(d);
      }
    }
  }, true);
  // Pictures that finished before they were on the page
  new MutationObserver((list) => {
    for (const m of list) for (const n of m.addedNodes) {
      if (n.nodeType !== 1) continue;
      const imgs = n.tagName === "IMG" ? [n] : n.querySelectorAll ? n.querySelectorAll("img.art") : [];
      for (const i of imgs) if (isArt(i) && i.complete && i.naturalWidth) done(i);
    }
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
// Redesign 2 (rc-q-hold): the same hold menu on every kind of card. Right-click (or hold a finger on a phone)
// any card: open, play, watched, My List, and for Ben's profile Pick a picture.
B.cardMenu = (it, href) => {
  if (!it || !B.tpMore) return;
  const ud = it.UserData || {};
  const video = it.Type === "Movie" || it.Type === "Episode" || it.Type === "Video";
  const watchable = video || it.Type === "Series" || it.Type === "Season";
  const word = /^(MusicAlbum|Book|AudioBook|Playlist)$/.test(it.Type) ? "cover" : /^(Movie|Series|Season|BoxSet)$/.test(it.Type) ? "poster" : "picture";
  B.tpMore(it.Name || "", [
    href ? { label: "Open", icon: "right", href } : null,
    video ? { label: ud.PlaybackPositionTicks > 0 && !ud.Played ? "Resume" : "Play", icon: "play", onClick: () => B.go(`#/play/${it.Id}${ud.PlaybackPositionTicks > 0 && !ud.Played ? "?resume=1" : ""}`) } : null,
    watchable && B.markWatched ? { label: ud.Played ? "Mark as not watched" : "Mark as watched", icon: "check", onClick: () => B.busy(null, async () => { await B.markWatched(it, !ud.Played); B.render(); return ud.Played ? "Marked as not watched." : "Marked as watched."; }) } : null,
    B.toggleList && it.Type !== "TvChannel" ? { label: ud.IsFavorite ? "Take off My List" : "Add to My List", icon: ud.IsFavorite ? "check" : "plus", onClick: () => B.toggleList(it) } : null,
    B.me && B.me.admin && B.fixItem && it.Type !== "Playlist" ? { label: "Pick a " + word, icon: "swap", youOnly: true, onClick: () => B.fixItem(Object.assign(it, { fixTab: "Primary" })) } : null,
  ]);
};
(() => {
  const CARDS = "a.poster[data-id], a.wide[data-id], a.mcard[data-id], a.chcard[data-id]";
  let timer = null, held = null;
  const open = (card) => {
    const it = B.items && B.items[card.dataset.id];
    if (!it || !it.Type) return false;
    B.cardMenu(it, card.getAttribute("href"));
    return true;
  };
  document.addEventListener("contextmenu", (e) => {
    if (e.defaultPrevented) return;
    const card = e.target.closest && e.target.closest(CARDS);
    if (!card) return;
    clearTimeout(timer);
    if (held === card) { e.preventDefault(); return; }
    if (open(card)) { e.preventDefault(); if ("ontouchstart" in window) { held = card; setTimeout(() => { held = null; }, 1500); } }
  });
  // Phones that don't send a right-click for a long press (iPhone): hold for half a second
  document.addEventListener("touchstart", (e) => {
    const card = e.target.closest && e.target.closest(CARDS);
    clearTimeout(timer);
    if (!card) return;
    timer = setTimeout(() => { held = card; open(card); setTimeout(() => { held = null; }, 1500); }, 550);
  }, { passive: true });
  const cancel = () => clearTimeout(timer);
  document.addEventListener("touchmove", cancel, { passive: true });
  document.addEventListener("touchend", cancel, { passive: true });
  // The press that opened the menu doesn't also open the card
  document.addEventListener("click", (e) => {
    if (held && e.target.closest && e.target.closest(CARDS) === held) { e.preventDefault(); e.stopPropagation(); }
    held = null;
  }, true);
})();
B.posterCard = (it, opts) => {
  opts = opts || {};
  B.items[it.Id] = it;
  const ud = it.UserData || {};
  const src = B.poster(it, 420);
  const pct = opts.progress && ud.PlayedPercentage ? Math.min(100, ud.PlayedPercentage) : 0;
  const newCount = it.Type === "Series" && ud.UnplayedItemCount && opts.newCount ? opts.newCount : 0;
  const year = it.ProductionYear ? String(it.ProductionYear) : "";
  return `<a class="poster" href="${opts.href || B.hrefOf(it)}" data-id="${esc(it.Id)}" title="${esc(it.Name)}">
    <div class="im">${src ? `<img class="art" src="${src}" alt="" loading="lazy" decoding="async">` : `<div class="noart" style="--door:${B.doorOf(it)}">${esc(it.SeriesName || it.Name)}</div>`}
      ${pct ? `<div class="prog"><i style="width:${pct}%"></i></div>` : ""}
      ${ud.Played && !opts.noTick ? `<span class="tick">${I("check")}</span>` : ""}
      ${newCount ? `<span class="newp">${newCount > 9 ? "9+ NEW" : newCount > 1 ? newCount + " NEW" : "NEW"}</span>` : opts.markNew && B.isNew(it) && !ud.Played ? `<span class="newp">NEW</span>` : ""}
    </div>
    <div class="cap"><span class="nm">${esc(it.Type === "Episode" ? it.SeriesName : it.Name)}</span><small>${esc(opts.sub || year)}</small></div>
  </a>`;
};
B.wideCard = (it, opts) => {
  opts = opts || {};
  B.items[it.Id] = it;
  const ud = it.UserData || {};
  const src = B.thumb(it, 560);
  const pct = ud.PlayedPercentage ? Math.min(100, ud.PlayedPercentage) : 0;
  const soon = opts.soon;
  return `<${soon ? "div" : "a"} class="wide ${soon ? "soon" : ""}" ${soon ? "" : `href="${opts.href || B.hrefOf(it)}"`} data-id="${esc(it.Id)}" ${opts.play ? `data-play="${esc(it.Id)}"` : ""}>
    <div class="im">${src ? `<img class="art" src="${src}" alt="" loading="lazy" decoding="async">` : `<div class="noart" style="--door:${B.doorOf(it)}">${esc(opts.title || it.Name)}</div>`}
      ${pct && !ud.Played ? `<div class="prog" style="position:absolute;left:10px;right:10px;bottom:9px;height:4px;border-radius:2px;background:rgba(0,0,0,.55);overflow:hidden"><i style="display:block;height:100%;width:${pct}%;background:#fff"></i></div>` : ""}
      ${ud.Played ? `<span class="tick" style="position:absolute;right:8px;top:8px;width:24px;height:24px;border-radius:50%;background:#fff;color:#000;display:grid;place-items:center">${I("check")}</span>` : ""}
      ${opts.tag ? `<span class="tag">${esc(opts.tag)}</span>` : ""}
    </div>
    <div class="cap"><span class="nm">${esc(opts.title || it.Name)}</span><small>${esc(opts.sub || "")}</small>${opts.story && it.Overview ? `<div class="story">${esc(it.Overview)}</div>` : ""}</div>
  </${soon ? "div" : "a"}>`;
};
// ---------- Song previews: 30 seconds of a song before asking for it (Requests > Music, Search's Ask for it) ----------
// Tower's helper (/api/music/preview) hands back a fresh free preview link (Deezer, or Apple's iTunes when Deezer
// has none). One plays at a time; pressing it again stops it. Benflix music pauses for it and carries on after.
const PV = { audio: null, id: null, paused: false, n: 0 };
B.previewBtn = (id, cls) => (id ? `<button class="circle pv ${cls || ""}" data-pv="${esc(String(id))}" title="Hear 30 seconds" aria-label="Hear 30 seconds">${I("play")}</button>` : "");
const pvDraw = () => $$("[data-pv]").forEach((b) => {
  const on = PV.id != null && b.dataset.pv === String(PV.id);
  b.classList.toggle("white", on);
  b.innerHTML = I(on ? "stop" : "play");
  b.title = on ? "Stop the preview" : "Hear 30 seconds";
  b.setAttribute("aria-label", b.title);
});
const pvQuiet = () => { if (PV.audio) { PV.audio.pause(); PV.audio.src = ""; PV.audio = null; } };
B.previewStop = () => {
  PV.n++;
  pvQuiet();
  PV.id = null;
  if (PV.paused && B.music && B.music.audio) B.music.audio.play().catch(() => {});
  PV.paused = false;
  pvDraw();
};
B.previewToggle = async (id, btn) => {
  if (PV.id === String(id)) return B.previewStop();
  pvQuiet();
  const n = ++PV.n;
  PV.id = String(id);
  pvDraw();
  let url = "";
  try { url = (await B.call("GET", "api/music/preview?id=" + encodeURIComponent(id))).url || ""; } catch (e) { url = ""; }
  if (n !== PV.n) return;
  if (!url) {
    PV.id = null;
    pvDraw();
    if (PV.paused && B.music && B.music.audio) B.music.audio.play().catch(() => {});
    PV.paused = false;
    if (btn && document.contains(btn)) { btn.innerHTML = "–"; btn.title = "No preview for this one"; setTimeout(pvDraw, 2500); }
    return;
  }
  const m = B.music && B.music.audio;
  if (m && !m.paused) { m.pause(); PV.paused = true; }
  const a = new Audio(url);
  a.volume = 0.9;
  a.onended = () => { if (PV.audio === a) B.previewStop(); };
  a.onerror = () => { if (PV.audio === a) B.previewStop(); };
  PV.audio = a;
  a.play().catch(() => { if (PV.audio === a) B.previewStop(); });
};
// The capture phase, so pressing play never also opens the Ask for it panel underneath
document.addEventListener("click", (e) => {
  const b = e.target.closest && e.target.closest("[data-pv]");
  if (!b) return;
  e.preventDefault();
  e.stopPropagation();
  B.previewToggle(b.dataset.pv, b);
}, true);
window.addEventListener("hashchange", () => { if (PV.id) B.previewStop(); });

B.row = (title, cards, opts) => {
  opts = opts || {};
  if (!cards || !cards.length) return "";
  return `<section class="rowx" ${opts.id ? `id="${opts.id}"` : ""}>
    <h3>${esc(title)}${opts.more ? `<a class="more" href="${opts.more}">See all</a>` : ""}</h3>
    <button class="arrow l" data-scroll="-1" aria-label="Scroll left">${I("left")}</button>
    <div class="strip">${cards.join("")}${opts.seeAll ? `<a class="poster see" href="${opts.seeAll}">See all</a>` : ""}</div>
    <button class="arrow r" data-scroll="1" aria-label="Scroll right">${I("right")}</button>
  </section>`;
};
document.addEventListener("click", (e) => {
  const a = e.target.closest("[data-scroll]");
  if (!a) return;
  const strip = a.parentElement.querySelector(".strip");
  strip.scrollBy({ left: +a.dataset.scroll * strip.clientWidth * 0.8, behavior: "smooth" });
});
B.top10 = (items) => items.slice(0, 10).map((it, i) => `<div class="top10"><span class="num ${i === 9 ? "ten" : ""}">${i + 1}</span>${B.posterCard(it)}</div>`);
B.scores = (it) => {
  const out = [];
  if (it.CommunityRating) out.push(`<span><b>${(+it.CommunityRating).toFixed(1)}</b>IMDb</span>`);
  if (it.CriticRating) out.push(`<span><b>${Math.round(it.CriticRating)}%</b>Rotten Tomatoes</span>`);
  if (B.me && B.me.kids) return "";
  return out.length ? `<div class="scores">${out.join("")}</div>` : "";
};

// ---------- libraries (to tell anime and stand-up apart) ----------
B.views = () => B.cached("views", 600000, async () => ((await B.get(`/Users/${B.uid()}/Views`)).Items || []));
B.libs = async (kind) => {
  const v = await B.views();
  const type = kind === "movies" ? "movies" : "tvshows";
  return v.filter((l) => l.CollectionType === type);
};
B.isAnimeLib = (l) => /anime/i.test(l.Name);
B.isStandLib = (l) => /stand/i.test(l.Name);

// Profile settings that follow the person (the same keys the TV and phone app use)
B.PREFS_CLIENT = "Wholphin";
B.prefs = async (id) => B.get(`/DisplayPreferences/${id || "default"}?userId=${B.uid()}&client=${B.PREFS_CLIENT}`);
B.savePrefs = async (change, id) => {
  const p = await B.prefs(id);
  const custom = Object.assign({}, p.CustomPrefs || {});
  change(custom);
  Object.keys(custom).forEach((k) => { if (custom[k] == null) delete custom[k]; });
  p.CustomPrefs = custom;
  await B.jf("POST", `/DisplayPreferences/${id || "default"}?userId=${B.uid()}&client=${B.PREFS_CLIENT}`, p);
};
B.webSettings = () => loadJson("benflix.web." + (B.me ? B.me.id : ""), {});
B.setWebSetting = (k, v) => { const s = B.webSettings(); s[k] = v; store("benflix.web." + B.me.id, JSON.stringify(s)); };

// ---------- volume button + slider (web players) ----------
// With a mouse the slider slides out when you hover the speaker, and clicking the speaker mutes.
// On touch screens (no hover) the first tap opens the slider and a tap anywhere else closes it.
// The level is remembered per profile under `key`, so it is the same next visit.
B.volume = (id, cls) => `<span class="volw"><button class="${cls || "pbtn"} vbtn"${id ? ` id="${id}"` : ""} title="Sound (click to mute, M)">${I("vol")}</button><span class="vslide"><input type="range" min="0" max="100" step="1" aria-label="Volume"></span></span>`;
B.wireVolume = (wrap, media, key) => {
  if (!wrap || !media || wrap.dataset.wired) return;
  wrap.dataset.wired = "1";
  const btn = wrap.querySelector(".vbtn"), r = wrap.querySelector("input");
  const saved = B.me ? B.webSettings()[key] : null;
  if (saved != null && isFinite(+saved)) media.volume = Math.max(0, Math.min(1, +saved));
  let shown = "";
  const draw = () => {
    const v = media.muted ? 0 : media.volume;
    r.value = Math.round(v * 100);
    r.style.setProperty("--v", r.value + "%");
    const ic = v === 0 ? "mute" : "vol";
    if (ic !== shown) { btn.innerHTML = I(ic); shown = ic; }
    btn.title = (v === 0 ? "Sound is off" : `Sound ${r.value}%`) + " (click to " + (v === 0 ? "turn on" : "mute") + ", M)";
  };
  let saveT;
  media.addEventListener("volumechange", () => {
    draw();
    clearTimeout(saveT);
    saveT = setTimeout(() => { if (B.me) B.setWebSetting(key, media.volume); }, 300);
  });
  r.addEventListener("input", () => { media.volume = r.value / 100; if (+r.value > 0) media.muted = false; });
  r.addEventListener("change", () => r.blur()); // so Space and the arrow keys go back to the player
  r.addEventListener("click", (e) => e.stopPropagation());
  const touch = () => window.matchMedia && matchMedia("(hover: none)").matches;
  const outside = (e) => {
    if (!document.contains(wrap) || !wrap.contains(e.target)) { wrap.classList.remove("open"); document.removeEventListener("pointerdown", outside, true); }
  };
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    if (touch() && !wrap.classList.contains("open")) {
      wrap.classList.add("open");
      document.addEventListener("pointerdown", outside, true);
      return;
    }
    if (media.volume === 0) { media.volume = 0.5; media.muted = false; }
    else media.muted = !media.muted;
  });
  wrap.addEventListener("wheel", (e) => {
    e.preventDefault();
    media.muted = false;
    media.volume = Math.max(0, Math.min(1, media.volume + (e.deltaY < 0 ? 0.05 : -0.05)));
  }, { passive: false });
  draw();
};

// Watch time this month (adds up every TV and phone, like the app's My watching)
B.watchTime = async () => {
  const p = await B.prefs("benflix-watch-time").catch(() => null);
  const month = new Date().toISOString().slice(0, 7);
  const byMonth = {};
  Object.entries((p && p.CustomPrefs) || {}).forEach(([k, v]) => {
    if (!k.startsWith("device-") || !v) return;
    String(v).split(";").forEach((part) => {
      const [key, n] = part.split("=");
      if (key && key !== "since" && +n) byMonth[key] = (byMonth[key] || 0) + Math.round(+n / 1000);
    });
  });
  return { month: byMonth[month] || 0, byMonth };
};
B.hm = (sec) => { const m = Math.round(sec / 60); return m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`; };

// ---------- Benflix "Install it like an app" ----------
// Browsers only offer to install a site that comes over a secure (https) address, so at home
// (http://10.0.0.58:8097) the button explains the browser's own "open as window" shortcut instead.
B.installPrompt = null;
window.addEventListener("beforeinstallprompt", (e) => { e.preventDefault(); B.installPrompt = e; });
window.addEventListener("appinstalled", () => { B.installPrompt = null; B.toast && B.toast("Benflix is installed. Look for it in your dock or Start menu."); });
B.installed = () => { try { return window.matchMedia("(display-mode: standalone)").matches || navigator.standalone === true; } catch (e) { return false; } };
B.installLine = () => B.installed() ? "Benflix is open in its own window." : "Its own window and icon in the dock or Start menu, no browser tabs.";
B.install = async () => {
  if (B.installed()) return B.toast("Benflix is already installed here.");
  if (B.installPrompt) {
    const p = B.installPrompt;
    B.installPrompt = null;
    p.prompt();
    try { await p.userChoice; } catch (e) {}
    return;
  }
  const edge = /Edg\//.test(navigator.userAgent), safari = /Safari\//.test(navigator.userAgent) && !/Chrome\//.test(navigator.userAgent);
  const steps = safari
    ? "In Safari on a Mac: File menu > Add to Dock."
    : edge
      ? "In Edge: the ⋯ menu (top right) > Apps > Install this site as an app."
      : "In Chrome: the ⋮ menu (top right) > Cast, save and share > Install page as app. If you only see Create shortcut, pick it and tick Open as window.";
  B.ask("Install Benflix", steps, "OK").catch(() => {});
};
if ("serviceWorker" in navigator && window.isSecureContext) {
  window.addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));
}

// Idea Box 24 "The Play button plays": press P on a poster (picked with Tab, or under the mouse) and it
// starts right away, from where you left off. A show plays its next episode. Enter still opens the page.
document.addEventListener("keydown", (e) => {
  if (e.key !== "p" && e.key !== "P") return;
  if (e.ctrlKey || e.metaKey || e.altKey || !B.me) return;
  const t = e.target;
  if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))) return;
  if (/^#\/(play|playgame|read)/.test(location.hash)) return;
  const hovered = Array.from(document.querySelectorAll(":hover")).reverse().map((x) => x.closest && x.closest("[data-id]")).find(Boolean);
  const card = (t && t.closest && t.closest("[data-id]")) || hovered;
  if (!card) return;
  const it = B.items[card.dataset.id];
  if (!it) return;
  e.preventDefault();
  if (["Movie", "Episode", "Video", "MusicVideo"].includes(it.Type)) return B.go(`#/play/${it.Id}?resume=1`);
  if (it.Type === "Series") {
    B.get(`/Shows/NextUp?SeriesId=${it.Id}&UserId=${B.uid()}&Limit=1&EnableResumable=true`)
      .then((r) => (r.Items || [])[0] || B.get(`/Shows/${it.Id}/Episodes?UserId=${B.uid()}&Limit=1`).then((x) => (x.Items || [])[0]))
      .then((ep) => B.go(ep ? `#/play/${ep.Id}?resume=1` : B.hrefOf(it)))
      .catch(() => B.go(B.hrefOf(it)));
    return;
  }
  B.go(B.hrefOf(it));
});
