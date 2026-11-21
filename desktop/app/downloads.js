/* Glowfin desktop: movies and episodes saved on this computer, to watch with no connection to Tower.
   A Glowfin page asks for a download and hands over the address of the video as a list of small pieces
   (an HLS playlist that Tower makes in a format every computer can play). This file fetches the pieces one
   after another into the app's own folder, and later serves them to the built-in offline player.
   One folder per title: meta.json (what it is and how far along), poster.jpg, index.m3u8, the pieces, subtitles. */
"use strict";

const { app, protocol, net } = require("electron");
const path = require("path");
const fs = require("fs");
const fsp = fs.promises;
const { pathToFileURL } = require("url");

const ROOT = path.join(app.getPath("userData"), "downloads");
const ID = /^[0-9a-f]{32}$/;
const jobs = new Map();   // id -> meta (everything on disk, plus what is running now)
const queue = [];         // ids waiting their turn
let running = null;       // { id, abort }
let notify = () => {};
let allowed = () => false;

const dirOf = (id) => path.join(ROOT, id);
const publicMeta = (m) => {
  const o = Object.assign({}, m);
  delete o.url; delete o.subs; delete o.posterUrl;
  return o;
};
async function saveMeta(m) {
  try { await fsp.mkdir(dirOf(m.id), { recursive: true }); await fsp.writeFile(path.join(dirOf(m.id), "meta.json"), JSON.stringify(publicMeta(m))); } catch (e) { /* disk trouble shows up as a failed download */ }
}
function changed(m) { notify(m ? publicMeta(m) : null); }

function loadAll() {
  let names = [];
  try { names = fs.readdirSync(ROOT); } catch (e) { return; }
  names.filter((n) => ID.test(n)).forEach((id) => {
    try {
      const m = JSON.parse(fs.readFileSync(path.join(ROOT, id, "meta.json"), "utf8"));
      if (!m || m.id !== id) return;
      // The app was closed halfway: the pieces came from a session Tower has since ended, so it starts over
      if (m.state !== "done") { m.state = "failed"; m.error = "Stopped before it finished."; }
      jobs.set(id, m);
    } catch (e) { /* a half-made folder: ignore it */ }
  });
}

// ---------- fetching ----------
async function get(url, signal, tries) {
  let last;
  for (let i = 0; i < (tries || 3); i++) {
    try {
      // Tower makes the pieces as they're asked for, so the first ones can take a while
      const r = await net.fetch(url, { signal, cache: "no-store" });
      if (r.ok) return r;
      last = new Error(r.status === 401 || r.status === 403 ? "Tower asked to sign in again." : "Tower answered " + r.status + ".");
      if (r.status >= 400 && r.status < 500) break;
    } catch (e) {
      if (signal && signal.aborted) throw e;
      last = new Error("Lost the connection to Tower.");
    }
    await new Promise((res) => setTimeout(res, 1500 * (i + 1)));
  }
  throw last;
}
async function saveTo(url, file, signal) {
  const r = await get(url, signal);
  const buf = Buffer.from(await r.arrayBuffer());
  await fsp.writeFile(file, buf);
  return buf.length;
}
const lines = (text) => String(text).split(/\r?\n/).map((l) => l.trim());

async function run(m) {
  const abort = new AbortController();
  running = { id: m.id, abort };
  const dir = dirOf(m.id);
  try {
    await fsp.rm(dir, { recursive: true, force: true });
    await fsp.mkdir(dir, { recursive: true });
    m.state = "downloading"; m.progress = 0; m.bytes = 0; m.error = "";
    await saveMeta(m); changed(m);
    if (m.posterUrl) { try { await saveTo(m.posterUrl, path.join(dir, "poster.jpg"), abort.signal); m.poster = true; } catch (e) { m.poster = false; } }

    // The first list names the stream; the second lists every piece of it
    let listUrl = m.url;
    let text = await (await get(listUrl, abort.signal)).text();
    if (/#EXT-X-STREAM-INF/.test(text)) {
      const ls = lines(text);
      const at = ls.findIndex((l) => l.startsWith("#EXT-X-STREAM-INF"));
      const next = ls.slice(at + 1).find((l) => l && !l.startsWith("#"));
      if (!next) throw new Error("Tower didn't send a video.");
      listUrl = new URL(next, listUrl).href;
      text = await (await get(listUrl, abort.signal)).text();
    }
    const src = lines(text);
    const total = src.filter((l) => l && !l.startsWith("#")).length;
    if (!total) throw new Error("Tower didn't send a video.");
    const out = [];
    let n = 0;
    for (const l of src) {
      if (!l) continue;
      if (l.startsWith("#EXT-X-MAP")) {
        // The opening piece some formats need before any other
        const uri = (/URI="([^"]+)"/.exec(l) || [])[1];
        if (uri) { m.bytes += await saveTo(new URL(uri, listUrl).href, path.join(dir, "init.mp4"), abort.signal); out.push(l.replace(/URI="[^"]+"/, 'URI="init.mp4"')); }
        continue;
      }
      if (l.startsWith("#")) { out.push(l); continue; }
      const name = "p" + String(n).padStart(5, "0") + (/\.(m4s|mp4)(\?|$)/i.test(l) ? ".m4s" : ".ts");
      m.bytes += await saveTo(new URL(l, listUrl).href, path.join(dir, name), abort.signal);
      out.push(name);
      n++;
      const p = Math.floor((n / total) * 100);
      if (p !== m.progress) { m.progress = p; changed(m); if (p % 10 === 0) saveMeta(m); }
    }
    if (!out.includes("#EXT-X-ENDLIST")) out.push("#EXT-X-ENDLIST");
    await fsp.writeFile(path.join(dir, "index.m3u8"), out.join("\n") + "\n");

    // Subtitles that sit beside the video (the ones drawn into the picture are already in it)
    m.tracks = [];
    for (const s of (m.subs || []).slice(0, 6)) {
      try { const f = `sub${m.tracks.length}.vtt`; await saveTo(s.url, path.join(dir, f), abort.signal); m.tracks.push({ file: f, label: String(s.label || "Subtitles").slice(0, 60), lang: String(s.lang || "").slice(0, 8), on: !!s.on }); } catch (e) { if (abort.signal.aborted) throw e; }
    }
    m.state = "done"; m.progress = 100; m.doneAt = Date.now();
  } catch (e) {
    if (abort.signal.aborted && !jobs.has(m.id)) return; // it was removed while running
    m.state = "failed";
    m.error = abort.signal.aborted ? "Stopped." : (e && e.message) || "Something went wrong.";
  } finally {
    running = null;
    if (jobs.has(m.id)) { await saveMeta(m); changed(m); }
    next();
  }
}
function next() {
  if (running) return;
  while (queue.length) {
    const m = jobs.get(queue.shift());
    if (m && m.state === "queued") return void run(m);
  }
}

// ---------- what the pages can ask ----------
const text = (v, max) => String(v == null ? "" : v).slice(0, max);
function start(job) {
  if (!job || !ID.test(String(job.id || ""))) throw new Error("That can't be downloaded.");
  if (!allowed(job.url)) throw new Error("Downloads only come from Tower.");
  const had = jobs.get(job.id);
  if (had && (had.state === "queued" || had.state === "downloading")) return publicMeta(had);
  const ours = (u) => (u && allowed(u) ? String(u) : "");
  const m = {
    id: job.id, kind: job.kind === "episode" ? "episode" : "movie",
    name: text(job.name, 200), show: text(job.show, 200), showId: ID.test(String(job.showId || "")) ? job.showId : "",
    season: Number(job.season) || 0, episode: Number(job.episode) || 0, year: Number(job.year) || 0,
    minutes: Number(job.minutes) || 0, about: text(job.about, 1200), rating: text(job.rating, 12), quality: text(job.quality, 20),
    user: text(job.user, 80), userId: text(job.userId, 40), kids: !!job.kids, burned: !!job.burned,
    state: "queued", progress: 0, bytes: 0, error: "", addedAt: Date.now(), position: 0,
    url: String(job.url), posterUrl: ours(job.posterUrl),
    subs: (Array.isArray(job.subs) ? job.subs : []).filter((s) => s && allowed(s.url)).map((s) => ({ url: String(s.url), label: s.label, lang: s.lang, on: s.on })),
  };
  jobs.set(m.id, m);
  queue.push(m.id);
  saveMeta(m); changed(m);
  next();
  return publicMeta(m);
}
async function remove(id) {
  if (!ID.test(String(id))) return false;
  jobs.delete(id);
  const i = queue.indexOf(id); if (i >= 0) queue.splice(i, 1);
  if (running && running.id === id) running.abort.abort();
  await fsp.rm(dirOf(id), { recursive: true, force: true }).catch(() => {});
  changed(null);
  return true;
}
const list = () => Array.from(jobs.values()).sort((a, b) => b.addedAt - a.addedAt).map(publicMeta);
function setPosition(id, seconds, done) {
  const m = jobs.get(id);
  if (!m) return;
  m.position = Math.max(0, Number(seconds) || 0);
  if (done) { m.watched = true; m.position = 0; }
  saveMeta(m);
}

// ---------- the offline player's own address ----------
// glowfin-offline://app/<page> are the shell's own pages; glowfin-offline://app/dl/<id>/<file> are downloaded files
function registerScheme() {
  protocol.registerSchemesAsPrivileged([{ scheme: "glowfin-offline", privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } }]);
}
// Files the offline pages share with the browser version (copied in when the app is built)
const SHARED = { "hls.min.js": ["vendor", "../../server/web/vendor"], "pad.js": ["vendor", "../../server/web/js"] };
function serve() {
  protocol.handle("glowfin-offline", async (request) => {
    try {
      const u = new URL(request.url);
      const parts = decodeURIComponent(u.pathname).split("/").filter(Boolean);
      let file = null;
      if (u.hostname === "app" && parts.length === 1 && /^[a-z0-9.-]+\.(html|js|css|svg|png)$/i.test(parts[0])) {
        const sh = SHARED[parts[0]];
        if (sh) file = [path.join(__dirname, sh[0], parts[0]), path.join(__dirname, sh[1], parts[0])].find((f) => fs.existsSync(f));
        else if (/^(offline\.(html|js|css)|glowfin-mark\.svg|icon\.png)$/.test(parts[0])) file = path.join(__dirname, parts[0]);
      } else if (u.hostname === "app" && parts.length === 3 && parts[0] === "dl" && ID.test(parts[1]) && /^[a-z0-9]+\.(m3u8|ts|m4s|mp4|vtt|jpg)$/i.test(parts[2])) {
        file = path.join(ROOT, parts[1], parts[2]);
      }
      if (!file || !fs.existsSync(file)) return new Response("Not found", { status: 404 });
      const r = await net.fetch(pathToFileURL(file).href);
      const type = { ".m3u8": "application/vnd.apple.mpegurl", ".ts": "video/mp2t", ".vtt": "text/vtt", ".js": "text/javascript", ".html": "text/html", ".css": "text/css", ".svg": "image/svg+xml" }[path.extname(file).toLowerCase()];
      const headers = new Headers(r.headers);
      if (type) headers.set("Content-Type", type);
      headers.set("Cache-Control", "no-store");
      return new Response(r.body, { status: r.status, headers });
    } catch (e) { return new Response("Not found", { status: 404 }); }
  });
}

module.exports = {
  registerScheme,
  init(opts) { allowed = opts.allowed; notify = opts.notify; try { fs.mkdirSync(ROOT, { recursive: true }); } catch (e) { /* shown later as a failed download */ } loadAll(); serve(); },
  start, remove, list, setPosition,
  hasAny: () => Array.from(jobs.values()).some((m) => m.state === "done"),
  PAGE: "glowfin-offline://app/offline.html",
};
