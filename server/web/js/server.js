/* Benflix web: the Server page (main profile only). Same five sections as the TV's Server page:
   Overview, Watching, Library, People and Tools. Everything the old Tools website did lives here. */
"use strict";

const call = (m, p, b) => B.call(m, p, b);
const jf = (m, p, b) => B.jf(m, p, b);
const tower = (fresh) => call("GET", "/api/admin/tower" + (fresh ? "?fresh=1" : ""));
const every = (ms, fn) => B.every(ms, fn);
let current = null; // which tool page is showing (pages stop drawing when you move on)

function pct(v) { return v == null ? "–" : v + "%"; }
function barClass(v) { return v >= 90 ? "bad" : v >= 75 ? "warn" : ""; }
function bytes(n) {
  if (n == null) return "–";
  const u = ["B", "KB", "MB", "GB", "TB", "PB"]; let i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return (n >= 100 || i === 0 ? Math.round(n) : n.toFixed(1)) + " " + u[i];
}
function upTime(s) {
  if (s == null) return "–";
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  return d ? `${d} day${d === 1 ? "" : "s"}, ${h} h` : h ? `${h} h ${m} min` : `${m} min`;
}
function tile(k, v, barValue) {
  const bar = barValue == null ? "" : `<div class="bar"><i class="${barClass(barValue)}" style="width:${Math.min(100, barValue)}%"></i></div>`;
  return `<div class="tile"><div class="k">${esc(k)}</div><div class="v">${esc(v)}</div>${bar}</div>`;
}
function problemsNote(list) { return (list || []).map((p) => `<div class="note">${esc(p)}</div>`).join(""); }
function loading(text) { return `<div class="list"><div class="empty">${esc(text || "Loading…")}</div></div>`; }
B.notice = (text) => B.ask(text, "", "OK", "Close");

// Status box: always a word as well as a colour
function statusBox(name, level, word) {
  const cls = level === "ok" ? "" : level === "check" ? "warn" : "bad";
  return `<div class="tile"><div class="k">${esc(name)}</div><div class="v"><span class="dot ${cls}"></span><span class="word-${level}">${esc(word || (level === "ok" ? "OK" : level === "check" ? "Check" : "Problem"))}</span></div></div>`;
}

const PAGES = {};
const SECTIONS = [
  { id: "overview", label: "Overview", icon: "gear", subs: [["overview", "Overview"]] },
  { id: "watching", label: "Watching", icon: "tv", subs: [["now", "Right now"]] },
  { id: "library", label: "Library", icon: "film", subs: [["library", "Library"], ["downloads", "Downloads"], ["find", "Find copies"], ["chores", "Chores"]] },
  { id: "people", label: "People", icon: "user", subs: [["people", "People"], ["messages", "Messages"], ["problems", "Problems"]] },
  { id: "tools", label: "Tools", icon: "server", subs: [["apps", "Apps"], ["tracks", "Track tidy"], ["livetv", "Live TV lists"], ["webversion", "Web version"]] },
];
const SUB_HOME = {};
SECTIONS.forEach((s) => s.subs.forEach(([id]) => { SUB_HOME[id] = s.id; }));
const go = (sub) => B.go(`#/server/${SUB_HOME[sub] || "overview"}/${sub}`);

B.route(/^\/server(?:\/(\w+))?(?:\/(\w+))?/, async (m) => {
  if (!B.me.admin) return B.go("#/home");
  const sec = SECTIONS.find((s) => s.id === m[1]) || SECTIONS[0];
  const sub = sec.subs.find(([id]) => id === m[2]) ? m[2] : sec.subs[0][0];
  B.page().innerHTML = B.top("Server", null, { solid: true }) + `<div class="split">
    <nav>${SECTIONS.map((s) => `<a href="#/server/${s.id}" class="${s === sec ? "on" : ""}">${B.I(s.icon)}${esc(s.label)}${s.id === "overview" ? '<span class="cnt" id="needCount"></span>' : ""}</a>`).join("")}</nav>
    <main><h2>${esc(sec.label)}</h2>
      ${sec.subs.length > 1 ? `<div class="tabs">${sec.subs.map(([id, label]) => `<a class="chip ${id === sub ? "on" : ""}" href="#/server/${sec.id}/${id}">${esc(label)}</a>`).join("")}</div>` : ""}
      <div id="main" class="subpage"></div></main>
  </div>`;
  B.wireTop();
  current = sub;
  B.onLeave(() => { current = null; });
  PAGES[sub]($("#main"));
});

// ---------- Overview: status boxes, what needs you, who's watching ----------
PAGES.overview = (el) => {
  el.innerHTML = `<p class="sub">How Tower and Jellyfin are doing. Updates by itself every 15 seconds.</p><div id="ov">${loading()}</div>`;
  const draw = async (fresh) => {
    try {
      const [t, sessions, info, reqs] = await Promise.all([
        tower(fresh).catch((e) => ({ problems: [e.message] })),
        jf("GET", "/Sessions?activeWithinSeconds=960").catch(() => null),
        jf("GET", "/System/Info").catch(() => null),
        jf("GET", "/JellyfinEnhanced/jellyseerr/request?take=50&filter=pending").catch(() => null),
      ]);
      if (current !== "overview") return;
      const watching = (sessions || []).filter((s) => s.NowPlayingItem);
      const apps = t.apps || [];
      const down = apps.filter((a) => (a.problem ?? !a.ok) && a.status !== "Never started");
      const stuck = (t.downloads || []).filter((d) => d.stuck);
      const disksFull = (t.disks || []).filter((d) => d.used / (d.used + d.free) > 0.9);
      const pending = ((reqs && reqs.results) || []).filter((r) => r.status === 1);
      let html = problemsNote(t.problems);
      html += `<div class="status">
        ${statusBox("Jellyfin", info ? "ok" : "bad", info ? "OK" : "Not answering")}
        ${statusBox("Apps", down.length ? "bad" : "ok", down.length ? `${down.length} down` : "OK")}
        ${statusBox("Downloads", stuck.length ? "check" : "ok", stuck.length ? `${stuck.length} stuck` : "OK")}
        ${statusBox("Disks", disksFull.length ? "check" : "ok", disksFull.length ? "Nearly full" : "OK")}
        ${statusBox("Requests", pending.length ? "check" : "ok", pending.length ? `${pending.length} waiting` : "OK")}
      </div>`;
      const needs = [];
      if (pending.length) needs.push(`<div class="item needs"><div class="grow"><div class="t">${pending.length} request${pending.length === 1 ? "" : "s"} waiting</div><div class="s">${esc(pending.slice(0, 3).map((r) => (r.media && (r.media.title || r.media.name)) || "").filter(Boolean).join(", ") || "Open Requests to say yes or no")}</div></div><div class="acts"><a class="btn small white" href="#/requests">Go</a></div></div>`);
      down.forEach((a) => needs.push(`<div class="item needs"><div class="grow"><div class="t">${esc(a.nice)} isn't working</div><div class="s">${esc(a.status)}</div></div><div class="acts"><a class="btn small white" href="#/server/tools/apps">Go</a></div></div>`));
      stuck.forEach((d) => needs.push(`<div class="item needs"><div class="grow"><div class="t">Download stuck: ${esc(d.title)}</div><div class="s">${esc(d.why || "Stuck")}</div></div><div class="acts"><a class="btn small white" href="#/server/library/downloads">Go</a></div></div>`));
      html += `<h3 class="section-title">Needs you</h3><div class="list">${needs.join("") || `<div class="empty">Nothing needs you right now.</div>`}</div>`;
      html += `<h3 class="section-title">Watching now</h3><div class="list">` + (watching.map((s) => {
        const it = s.NowPlayingItem;
        const title = it.Type === "Episode" ? [it.SeriesName, it.Name].filter(Boolean).join(" · ") : it.Name;
        return `<div class="item"><div class="grow"><div class="t">${esc(s.UserName || "Someone")} · ${esc(title)}</div><div class="s">${esc(s.DeviceName || "")}${s.PlayState && s.PlayState.PlayMethod === "Transcode" ? " · converting" : ""}</div></div></div>`;
      }).join("") || `<div class="empty">Nobody is watching right now.</div>`) + `</div>`;
      if (info) html += `<p class="sub">${esc(info.ServerName)} · Jellyfin ${esc(info.Version)}</p>`;
      html += `<div class="tiles">
        ${tile("Processor", pct(t.cpu), t.cpu)}${tile("Memory", pct(t.memory), t.memory)}${tile("Graphics chip", pct(t.gpu), t.gpu)}${tile("Up for", upTime(t.up_seconds))}
        ${(t.disks || []).map((d) => { const used = Math.round(100 * d.used / (d.used + d.free)); return tile(d.name + " free", bytes(d.free), used); }).join("")}
      </div>`;
      $("#ov").innerHTML = html;
      const n = needs.length, c = $("#needCount");
      if (c) c.textContent = n ? `${n} need${n === 1 ? "s" : ""} you` : "";
    } catch (err) {
      if (current === "overview") $("#ov").innerHTML = `<div class="note">${esc(err.message)}</div>`;
    }
  };
  draw(true); every(15000, () => draw(false));
};

// ---------- Live TV lists (the M3U and guide Jellyfin reads from this helper) ----------
PAGES.livetv = (el) => {
  const host = location.hostname + (location.port ? ":" + location.port : "");
  el.innerHTML = `<p class="sub">The free Live TV channel list and guide that Jellyfin reads from Tower. It rebuilds itself every night.</p>
    <div class="list">
      <div class="item"><div class="grow"><div class="t">Tuner (M3U)</div><div class="s">http://${esc(host)}/livetv/channels.m3u</div></div></div>
      <div class="item"><div class="grow"><div class="t">Guide (XMLTV)</div><div class="s">http://${esc(host)}/livetv/guide.xml</div></div></div>
    </div><div id="lt">${loading()}</div>`;
  const draw = async () => {
    try {
      const s = await fetch("/livetv/status").then((r) => r.json());
      if (current !== "livetv") return;
      const ago = (t) => t ? B.when(new Date(t * 1000).toISOString()) : "never";
      const types = Object.entries(s.types || {}).filter(([, n]) => n).map(([k, n]) => `${esc(k)} ${n}`).join(" · ");
      const src = Object.entries(s.sources || {}).map(([k, v]) => `<div class="item"><div class="grow"><div class="t">${esc(k)}</div><div class="s">${v.error ? esc(v.error) : `${v.listed || 0} channels, ${v.english || 0} in English`}</div></div><span class="word-${v.error ? "bad" : "ok"}">${v.error ? "Problem" : "OK"}</span></div>`).join("");
      const line = (t, v) => `<div class="item"><div class="grow"><div class="t">${t}</div><div class="s">${v}</div></div></div>`;
      const rows = !s.ready && !s.running ? "" : [
        line("Right now", s.running ? "Making the list. This takes about 15 minutes." : s.ready ? "The list is ready." : "Not made yet."),
        line("Last made", `${ago(s.finished)}${s.minutes ? ` · took ${Math.round(s.minutes)} min` : ""}`),
        s.checked != null ? line("Channels", `${s.kept || 0} kept out of ${s.checked} checked${s.dead ? ` (${s.dead} weren't working)` : ""}`) : "",
        types ? line("By type", types) : "",
      ].join("") + src;
      $("#lt").innerHTML = `<div class="list">${rows || '<div class="empty">No status yet.</div>'}</div><button class="btn white" id="ltRefresh">Rebuild the list now</button>`;
      $("#ltRefresh").onclick = (e) => B.busy(e.target, async () => { await call("POST", "/api/admin/livetv/refresh"); setTimeout(draw, 1000); return "Rebuilding. It checks every channel, so it takes about 15 minutes."; });
    } catch (err) { if (current === "livetv") $("#lt").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  draw(); every(10000, draw);
};

// ---------- Web version: where these files came from ----------
PAGES.webversion = (el) => {
  el.innerHTML = `<p class="sub">Benflix in a browser gets its newest version from GitHub by itself, when Tower's helper starts and then every hour.</p><div id="wv">${loading()}</div>`;
  call("GET", "/api/web/version").then((s) => {
    if (current !== "webversion") return;
    $("#wv").innerHTML = `<div class="list">
      <div class="item"><div class="grow"><div class="t">Using</div><div class="s">${s.source === "github" ? "The newest version from GitHub" : "The copy from the setup command"}</div></div></div>
      <div class="item"><div class="grow"><div class="t">Last checked</div><div class="s">${s.checked ? esc(B.when(new Date(s.checked * 1000).toISOString())) : "Not yet"}</div></div></div>
      ${s.problem ? `<div class="item"><span class="dot warn"></span><div class="grow"><div class="t">Problem</div><div class="s">${esc(s.problem)}</div></div></div>` : ""}
    </div>`;
  }).catch((err) => { $("#wv").innerHTML = `<div class="note">${esc(err.message)}</div>`; });
};

// ---------- the old Tools website's pages, moved here ----------
PAGES.apps = (el) => {
  el.innerHTML = `<h2>Apps</h2><p class="sub">Everything running in Unraid's Docker tab. Restart one if it's acting up.</p><div id="ap">${loading()}</div>`;
  const draw = async (fresh) => {
    try {
      const t = await tower(fresh);
      if (current !== "apps") return;
      let html = problemsNote(t.problems);
      if (!t.apps) { $("#ap").innerHTML = html || loading("No apps found."); return; }
      html += `<div class="list">` + t.apps.map((a) => `
        <div class="item"><span class="dot ${a.ok ? "" : a.running ? "warn" : (a.problem ?? true) ? "bad" : "off"}"></span>
          <div class="grow"><div class="t">${esc(a.nice)}</div><div class="s">${esc(a.status)}</div></div>
          <div class="acts">${a.restartable ? `<button class="btn small" data-restart="${esc(a.name)}" data-nice="${esc(a.nice)}">Restart</button>` : ""}</div>
        </div>`).join("") + `</div>`;
      $("#ap").innerHTML = html;
    } catch (err) { if (current === "apps") $("#ap").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  el.onclick = async (e) => {
    const b = e.target.closest("[data-restart]"); if (!b) return;
    if (!await B.ask(`Restart ${b.dataset.nice}? Anyone using it will be cut off for about a minute.`)) return;
    b.disabled = true; b.textContent = "Restarting…";
    try { B.toast((await call("POST", `/api/admin/apps/${encodeURIComponent(b.dataset.restart)}/restart`)).message); }
    catch (err) { B.toast(err.message); }
    draw(true);
  };
  draw(true); every(20000, () => draw(false));
};

PAGES.downloads = (el) => {
  el.innerHTML = `<h2>Downloads</h2><p class="sub">What Sonarr and Radarr are getting. Stuck ones are at the top.</p><div id="dl">${loading()}</div>`;
  const draw = async (fresh) => {
    try {
      const t = await tower(fresh);
      if (current !== "downloads") return;
      let html = problemsNote(t.problems);
      if (!t.sonarr && !t.radarr) html += `<div class="note">Sonarr and Radarr aren't connected to the helper yet. Run the Benflix setup command on Tower again.</div>`;
      const list = t.downloads || [];
      html += `<div class="list">` + (list.map((d) => `
        <div class="item"><span class="dot ${d.stuck ? "bad" : d.paused || d.queued ? "off" : ""}"></span>
          <div class="grow"><div class="t">${esc(d.title)}</div>
            <div class="s">${d.stuck ? esc(d.why || "Stuck") : d.paused ? "Paused" : d.queued ? "Waiting to start" : `${d.pct}%${d.left ? " · " + esc(d.left) + " left" : ""}`} · ${d.app === "sonarr" ? "Sonarr" : "Radarr"}</div>
            ${d.stuck ? "" : `<div class="bar"><i style="width:${d.pct}%"></i></div>`}
          </div>
          <div class="acts">
            <button class="btn small" data-act="retry" data-app="${d.app}" data-id="${d.id}">Try again</button>
            <button class="btn small" data-act="replace" data-app="${d.app}" data-id="${d.id}" data-title="${esc(d.title)}" data-episodes="${d.episodes || 1}">${(d.episodes || 1) > 1 ? "Get a different copy of the whole pack" : "Get a different copy"}</button>
          </div>
        </div>`).join("") || `<div class="empty">Nothing downloading right now.</div>`) + `</div>`;
      $("#dl").innerHTML = html;
    } catch (err) { if (current === "downloads") $("#dl").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  el.onclick = async (e) => {
    const b = e.target.closest("[data-act]"); if (!b) return;
    const episodes = Number(b.dataset.episodes || 1);
    const ask = episodes > 1
      ? `${b.dataset.title} is one download with all ${episodes} episodes in it. Remove the whole pack and look for another copy of each episode?`
      : `Remove this download of ${b.dataset.title} and look for a different copy?`;
    if (b.dataset.act === "replace" && !await B.ask(ask)) return;
    b.disabled = true;
    try { B.toast((await call("POST", `/api/admin/downloads/${b.dataset.app}/${b.dataset.id}/${b.dataset.act}`)).message); }
    catch (err) { B.toast(err.message); }
    draw(true);
  };
  draw(true); every(15000, () => draw(true));
};

PAGES.find = (el) => {
  el.innerHTML = `<h2>Find copies</h2>
    <p class="sub">Search your library. For a show, Sonarr looks for every missing episode. For a movie, Radarr looks for a better copy.</p>
    <form class="row" id="fForm" style="margin-bottom:16px">
      <input class="field inline" id="fText" placeholder="Show or movie name" autocomplete="off">
      <button class="btn red" type="submit">Search</button>
    </form>
    <div id="fRes"></div>`;
  $("#fText").focus();
  $("#fForm").onsubmit = async (e) => {
    e.preventDefault();
    const q = $("#fText").value.trim(); if (!q) return;
    $("#fRes").innerHTML = loading("Searching…");
    try {
      const r = await jf("GET", `/Items?userId=${encodeURIComponent(B.me.id)}&searchTerm=${encodeURIComponent(q)}&IncludeItemTypes=Series,Movie&Recursive=true&Fields=ProviderIds&Limit=20`);
      const items = (r && r.Items) || [];
      $("#fRes").innerHTML = `<div class="list">` + (items.map((it, i) => `
        <div class="item"><div class="grow"><div class="t">${esc(it.Name)}${it.ProductionYear ? ` (${it.ProductionYear})` : ""}</div>
          <div class="s">${it.Type === "Series" ? "Show" : "Movie"}</div></div>
          <div class="acts"><button class="btn small" data-i="${i}">${it.Type === "Series" ? "Find missing episodes" : "Find a better copy"}</button></div>
        </div>`).join("") || `<div class="empty">Nothing in your library matches “${esc(q)}”.</div>`) + `</div>`;
      $("#fRes").onclick = async (ev) => {
        const b = ev.target.closest("[data-i]"); if (!b) return;
        const it = items[+b.dataset.i], ids = it.ProviderIds || {};
        const info = { name: it.Name, tmdb: ids.Tmdb || ids.tmdb, tvdb: ids.Tvdb || ids.tvdb, imdb: ids.Imdb || ids.imdb };
        b.disabled = true;
        try { B.toast((await call("POST", it.Type === "Series" ? "/api/admin/search/missing" : "/api/admin/search/better", info)).message); }
        catch (err) { B.toast(err.message); }
        b.disabled = false;
      };
    } catch (err) { $("#fRes").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
};

PAGES.tracks = (el) => {
  el.innerHTML = `<h2>Track tidy</h2>
    <p class="sub">Gives audio and subtitle tracks clear names (like “English · 5.1”) without re-encoding anything.
    Try <b>Preview</b> first: it only shows what it would change.</p><div id="tr">${loading()}</div>`;
  let picked = load("benflix_web_folder") || "";
  const draw = async () => {
    try {
      const s = await call("GET", "/api/admin/tracks");
      if (current !== "tracks") return;
      if (!s.ready) { $("#tr").innerHTML = `<div class="note">${esc(s.problem)}</div>`; return; }
      if (!s.folders.includes(picked)) picked = s.folders[0] || "";
      const state = s.running ? `Running: ${esc(s.mode)} on ${esc(s.folder)}…`
        : s.finished ? `Last run: ${esc(s.mode)} on ${esc(s.folder)} ${s.result === "done" ? "finished" : "had a problem"} at ${new Date(s.finished * 1000).toLocaleTimeString()}.`
        : "Not run yet since the helper started.";
      const keepFocus = document.activeElement && document.activeElement.id;
      $("#tr").innerHTML = `
        <div class="list"><div class="item" style="flex-wrap:wrap">
          <div class="grow" style="min-width:200px"><div class="t">Folder</div>
            <select class="field" id="trFolder" ${s.running ? "disabled" : ""}>${s.folders.map((f) => `<option ${f === picked ? "selected" : ""}>${esc(f)}</option>`).join("")}</select></div>
          <div class="acts">
            <button class="btn small" data-mode="preview" ${s.running ? "disabled" : ""}>Preview</button>
            <button class="btn small red" data-mode="apply" ${s.running ? "disabled" : ""}>Fix names</button>
          </div>
        </div>
        ${(s.undo_files || []).length ? `<div class="item"><div class="grow"><div class="t">Undo a past fix</div><div class="s">Puts the old names back.</div></div>
          <div class="acts">${s.undo_files.map((u) => `<button class="btn small" data-undo="${esc(u)}" ${s.running ? "disabled" : ""}>${esc(u.replace(/^undo-|\.json$/g, ""))}</button>`).join("")}</div></div>` : ""}
        </div>
        <p class="sub" style="margin-bottom:10px">${state}</p>
        <pre class="log">${s.lines.length ? s.lines.map(esc).join("\n") : "What the tool does will show here."}</pre>`;
      const log = $("#tr pre.log"); log.scrollTop = log.scrollHeight;
      $("#trFolder").onchange = (e) => { picked = e.target.value; store("benflix_web_folder", picked); };
      if (keepFocus === "trFolder") $("#trFolder").focus();
    } catch (err) { if (current === "tracks") $("#tr").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  el.onclick = async (e) => {
    const b = e.target.closest("[data-mode],[data-undo]"); if (!b) return;
    let body;
    if (b.dataset.undo) {
      if (!await B.ask("Put back the old track names from that fix?")) return;
      body = { mode: "undo", undo: b.dataset.undo };
    } else {
      if (b.dataset.mode === "apply" && !await B.ask(`Fix the track names in ${picked}? You can undo it afterwards.`)) return;
      body = { mode: b.dataset.mode, folder: picked };
    }
    try { await call("POST", "/api/admin/tracks", body); } catch (err) { B.toast(err.message); }
    draw();
  };
  draw(); every(2000, () => { if (document.activeElement?.id !== "trFolder") draw(); });
};

// ---------- Jellyfin tools (part 2) ----------
const TICKS_PER_MIN = 600000000;
const PREFS_CLIENT = "Wholphin"; // where the Benflix app keeps notes and problem reports
const imgUrl = (id, h) => `/api/web/jf/Items/${encodeURIComponent(id)}/Images/Primary?maxHeight=${h || 180}&api_key=${encodeURIComponent(B.me.token)}`;
function when(iso) {
  const d = new Date(iso); if (isNaN(d)) return "";
  const today = new Date(); const sameDay = d.toDateString() === today.toDateString();
  return sameDay ? d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })
    : d.toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" }) + ", " + d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}
function isAway(ip) {
  if (!ip) return false;
  ip = ip.replace(/^::ffff:/, "");
  const [a, b] = ip.split(".").map(Number);
  if (ip.includes(":")) return !(ip === "::1" || ip.startsWith("fe80") || ip.startsWith("fd") || ip.startsWith("fc"));
  return !(a === 10 || a === 127 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31) || (a === 100 && b >= 64 && b <= 127));
}
function whyConverting(t) {
  const r = (t.TranscodeReasons || []).map((x) => String(x).toLowerCase());
  if (r.some((x) => x.startsWith("subtitle"))) return "Subtitles have to be burned into the picture. Tip: pick a different subtitle (SRT), or turn subtitles off.";
  if (r.some((x) => x.includes("bitrate"))) return "The file is bigger than their quality limit (often the away-from-home limit).";
  if (r.some((x) => x.startsWith("audio") || x.includes("secondaryaudio")) && t.IsVideoDirect) return "Their device can't play this sound format, so only the sound is converted (light work).";
  if (r.some((x) => x.includes("range") || x.includes("bitdepth"))) return "Their screen can't show this HDR video, so colours are converted.";
  if (r.some((x) => x.startsWith("video") || x.includes("container") || x.includes("refframes"))) return "Their device can't play this video format.";
  return r.length ? r.join(", ") : "Jellyfin didn't say why.";
}
function plainProblem(name, type, detail) {
  const t = String(type || "").toLowerCase(), n = String(name || "").trim();
  if (t.includes("authenticationfailed") || /failed login/i.test(n)) return [n, "Fine if it was a typo. Lots in a row from away from home could be a stranger guessing."];
  if (t.includes("userlockedout") || /locked out/i.test(n)) return [n, "Too many wrong passwords. Give them a new password in People to let them back in."];
  if (t.includes("subtitledownloadfailure") || /subtitles failed/i.test(n)) return [n, "Usually nothing to worry about. The subtitle site may be busy or have none for it."];
  if (t.includes("scheduledtaskfailed")) return [n, "Jellyfin tries again on its next run. If it keeps failing, restart Jellyfin (Chores)."];
  if (t.includes("plugin")) return ["A plugin had a problem: " + n, "Check the plugin in Jellyfin's Dashboard > Plugins."];
  if (/playback|transcod/i.test(n)) return ["A video wouldn't play: " + n, "Ask who was watching, or check Problems reported."];
  return [n, detail && detail !== n ? detail : null];
}
async function getPrefs(userId) {
  return jf("GET", `/DisplayPreferences/default?userId=${encodeURIComponent(userId)}&client=${PREFS_CLIENT}`);
}
async function updatePrefs(userId, change) {
  const p = await getPrefs(userId);
  const custom = Object.assign({}, p.CustomPrefs || {});
  change(custom);
  Object.keys(custom).forEach((k) => { if (custom[k] == null) delete custom[k]; });
  p.CustomPrefs = custom;
  await jf("POST", `/DisplayPreferences/default?userId=${encodeURIComponent(userId)}&client=${PREFS_CLIENT}`, p);
}
const users = () => jf("GET", "/Users").then((u) => (u || []).sort((a, b) => a.Name.localeCompare(b.Name)));
const busy = async (b, fn) => {
  b.disabled = true;
  try { const msg = await fn(); if (msg) B.toast(msg); } catch (err) { B.toast(err.message); }
  b.disabled = false;
};

PAGES.now = (el) => {
  el.innerHTML = `<h2>Right now</h2><p class="sub">Who's watching, and how hard the server works for it. Updates every 10 seconds.</p>
    <div id="nw">${loading()}</div><h3 class="section-title">Watched this week</h3><div id="hist">${loading()}</div>`;
  let sessions = [];
  const draw = async () => {
    try {
      sessions = ((await jf("GET", "/Sessions?activeWithinSeconds=960")) || []).filter((s) => s.NowPlayingItem);
      if (current !== "now") return;
      $("#nw").innerHTML = `<div class="list">` + (sessions.map((s, i) => {
        const it = s.NowPlayingItem, ps = s.PlayState || {}, tr = s.TranscodingInfo;
        const converting = ps.PlayMethod === "Transcode";
        const title = it.Type === "Episode" ? [it.SeriesName, it.Name].filter(Boolean).join(" · ") : it.Name;
        const pos = ps.PositionTicks, len = it.RunTimeTicks;
        const prog = pos != null && len ? Math.min(100, Math.round(100 * pos / len)) : null;
        const left = pos != null && len > pos ? Math.round((len - pos) / TICKS_PER_MIN) : null;
        const how = converting ? (tr && tr.VideoCodec ? `Converting to ${tr.VideoCodec.toUpperCase()} (server is working hard)` : "Converting (server is working hard)")
          : ps.PlayMethod === "DirectStream" ? "Direct stream (light work)" : "Direct play (no work for the server)";
        const poster = it.Type === "Episode" ? (it.SeriesId || it.Id) : it.Id;
        return `<div class="item">
          <img src="${imgUrl(poster, 120)}" alt="" style="width:48px;height:72px;object-fit:cover;border-radius:4px;background:#333;flex:none" onerror="this.style.visibility='hidden'">
          <div class="grow"><div class="t">${esc(s.UserName || "Someone")} · ${esc(title)}</div>
            <div class="s">${esc(s.DeviceName || "")} · ${esc([s.Client, s.ApplicationVersion].filter(Boolean).join(" "))}${isAway(s.RemoteEndPoint) ? " · away from home" : ""}</div>
            <div class="s">${ps.IsPaused ? "Paused · " : ""}${esc(how)}${left != null ? " · " + (left < 1 ? "almost done" : left < 60 ? left + " min left" : Math.floor(left / 60) + " h " + (left % 60) + " min left") : ""}</div>
            ${converting && tr ? `<div class="s" style="color:#f3dc94">${esc(whyConverting(tr))}</div>` : ""}
            ${prog != null ? `<div class="bar"><i style="width:${prog}%"></i></div>` : ""}
          </div>
          <div class="acts">${s.SupportsRemoteControl ? `<button class="btn small" data-pp="${i}">${ps.IsPaused ? "Resume" : "Pause"}</button><button class="btn small" data-msg="${i}">Message</button>` : ""}</div>
        </div>`;
      }).join("") || `<div class="empty">Nobody is watching right now.</div>`) + `</div>`;
    } catch (err) { if (current === "now") $("#nw").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  const history = async () => {
    try {
      const since = new Date(Date.now() - 7 * 86400000).toISOString();
      const r = await jf("GET", `/System/ActivityLog/Entries?startIndex=0&limit=300&minDate=${encodeURIComponent(since)}&hasUserId=true`);
      const plays = ((r && r.Items) || []).filter((e) => e.Type === "VideoPlayback").slice(0, 60);
      if (current !== "now") return;
      $("#hist").innerHTML = `<div class="list">` + (plays.map((e) => `<div class="item"><div class="grow"><div class="t">${esc(e.Name)}</div><div class="s">${esc(when(e.Date))}</div></div></div>`).join("")
        || `<div class="empty">Nothing watched this week.</div>`) + `</div>`;
    } catch (err) { if (current === "now") $("#hist").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  el.onclick = async (e) => {
    const pp = e.target.closest("[data-pp]"), m = e.target.closest("[data-msg]");
    if (pp) {
      const s = sessions[+pp.dataset.pp]; const paused = s.PlayState && s.PlayState.IsPaused;
      await busy(pp, async () => { await jf("POST", `/Sessions/${encodeURIComponent(s.Id)}/Playing/${paused ? "Unpause" : "Pause"}`); });
      setTimeout(draw, 800);
    } else if (m) {
      const s = sessions[+m.dataset.msg];
      const text = await B.askText(`Message for ${s.UserName || "them"} on ${s.DeviceName || "their screen"}:`);
      if (!text || !text.trim()) return;
      await busy(m, async () => {
        await jf("POST", `/Sessions/${encodeURIComponent(s.Id)}/Message`, { Header: "Message from " + B.me.name, Text: text.trim(), TimeoutMs: 15000 });
        return "Sent.";
      });
    }
  };
  draw(); history(); every(10000, draw);
};

PAGES.library = (el) => {
  el.innerHTML = `<h2>Library</h2><p class="sub">How much is on Benflix, and scanning for new files.</p><div id="lb">${loading()}</div>`;
  const draw = async () => {
    try {
      const weekAgo = new Date(Date.now() - 7 * 86400000);
      const [c, libs, recent] = await Promise.all([
        jf("GET", "/Items/Counts"),
        jf("GET", "/Library/MediaFolders?isHidden=false"),
        jf("GET", `/Items?userId=${encodeURIComponent(B.me.id)}&IncludeItemTypes=Movie,Episode&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Fields=DateCreated&Limit=500&EnableImages=false&EnableUserData=false`),
      ]);
      if (current !== "library") return;
      const added = ((recent && recent.Items) || []).filter((i) => new Date(i.DateCreated) > weekAgo).length;
      const list = ((libs && libs.Items) || []).sort((a, b) => a.Name.localeCompare(b.Name));
      $("#lb").innerHTML = `<div class="tiles">${tile("Movies", (c.MovieCount || 0).toLocaleString())}${tile("Shows", (c.SeriesCount || 0).toLocaleString())}${tile("Episodes", (c.EpisodeCount || 0).toLocaleString())}${tile("Added this week", added >= 500 ? "500+" : added)}</div>
        <h3 class="section-title">Scan for new files</h3>
        <div class="list"><div class="item"><div class="grow"><div class="t">Every library</div><div class="s">Finds new and changed files everywhere. Can take a while.</div></div><div class="acts"><button class="btn small red" data-scan="">Scan all</button></div></div>
        ${list.map((l) => `<div class="item"><div class="grow"><div class="t">${esc(l.Name)}</div></div><div class="acts"><button class="btn small" data-scan="${esc(l.Id)}" data-name="${esc(l.Name)}">Scan</button></div></div>`).join("")}</div>`;
    } catch (err) { if (current === "library") $("#lb").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  el.onclick = async (e) => {
    const b = e.target.closest("[data-scan]"); if (!b) return;
    const id = b.dataset.scan;
    busy(b, async () => {
      if (id) await jf("POST", `/Items/${encodeURIComponent(id)}/Refresh?metadataRefreshMode=Default&imageRefreshMode=Default&recursive=true`);
      else await jf("POST", "/Library/Refresh");
      return id ? `Scanning ${b.dataset.name}.` : "Scanning every library. New things show up as they're found.";
    });
  };
  draw();
};

PAGES.people = (el) => {
  el.innerHTML = `<h2>People</h2><p class="sub">Accounts for family and friends.</p>
    <div class="row" style="margin-bottom:16px"><button class="btn red" id="addPerson">Add a person</button></div><div id="pp">${loading()}</div>`;
  let list = [], libs = [];
  const draw = async () => {
    try {
      [list, libs] = await Promise.all([users(), jf("GET", "/Library/MediaFolders?isHidden=false").then((r) => (r && r.Items) || [])]);
      if (current !== "people") return;
      $("#pp").innerHTML = `<div class="list">` + list.map((u, i) => {
        const p = u.Policy || {};
        const seen = u.LastActivityDate ? "Last seen " + when(u.LastActivityDate) : "Never signed in";
        const bits = [p.IsAdministrator ? "Admin" : null, p.IsDisabled ? "Turned off" : null, p.MaxParentalRating != null ? "Kids limits" : null,
          p.MaxActiveSessions ? `${p.MaxActiveSessions} screen${p.MaxActiveSessions === 1 ? "" : "s"} at once` : "Any number of screens",
          p.EnableAllFolders ? "All libraries" : `${(p.EnabledFolders || []).length} of ${libs.length} libraries`].filter(Boolean);
        return `<div class="item">
          <img src="/api/web/jf/Users/${encodeURIComponent(u.Id)}/Images/Primary?maxHeight=96&api_key=${encodeURIComponent(B.me.token)}" alt="" style="width:40px;height:40px;border-radius:6px;object-fit:cover;background:#333;flex:none" onerror="this.style.visibility='hidden'">
          <div class="grow"><div class="t">${esc(u.Name)}</div><div class="s">${esc(seen)} · ${esc(bits.join(" · "))}</div></div>
          <div class="acts">
            <button class="btn small" data-a="screens" data-i="${i}">Screens</button>
            <button class="btn small" data-a="libs" data-i="${i}">Libraries</button>
            <button class="btn small" data-a="password" data-i="${i}">New password</button>
            <button class="btn small" data-a="signout" data-i="${i}">Sign out everywhere</button>
            ${u.Id === B.me.id ? "" : `<button class="btn small" data-a="remove" data-i="${i}">Remove</button>`}
          </div></div>`;
      }).join("") + `</div><div id="libPick"></div>`;
    } catch (err) { if (current === "people") $("#pp").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  const newPassword = () => {
    const letters = "abcdefghjkmnpqrstuvwxyz23456789", a = new Uint32Array(8);
    crypto.getRandomValues(a); return Array.from(a, (n) => letters[n % letters.length]).join("");
  };
  const setPolicy = async (u, change) => {
    const full = await jf("GET", `/Users/${encodeURIComponent(u.Id)}`);
    const p = Object.assign({}, full.Policy); change(p);
    await jf("POST", `/Users/${encodeURIComponent(u.Id)}/Policy`, p);
  };
  $("#addPerson").onclick = async (e) => {
    const name = await B.askText("Their name (this is what they type to sign in):");
    if (!name || !name.trim()) return;
    busy(e.target, async () => {
      const pw = newPassword();
      await jf("POST", "/Users/New", { Name: name.trim(), Password: pw });
      B.notice(`${name.trim()} is ready.\n\nUser name: ${name.trim()}\nPassword: ${pw}\n\nWrite it down: it won't show again.`);
      draw();
    });
  };
  el.onclick = async (e) => {
    const b = e.target.closest("[data-a]"); if (!b) return;
    const u = list[+b.dataset.i];
    if (b.dataset.a === "screens") {
      const cur = (u.Policy && u.Policy.MaxActiveSessions) || 0;
      const v = await B.askText(`How many screens can ${u.Name} watch on at once?`, "0 means no limit.", String(cur));
      if (v == null || !/^\d+$/.test(v.trim())) return;
      busy(b, async () => { await setPolicy(u, (p) => { p.MaxActiveSessions = +v.trim(); }); draw(); return "Saved."; });
    } else if (b.dataset.a === "libs") {
      const p = u.Policy || {};
      $("#libPick").innerHTML = `<h3 class="section-title" style="margin-top:8px">Libraries ${esc(u.Name)} can see</h3><div class="list">
        ${libs.map((l) => `<label class="item" style="cursor:pointer"><input type="checkbox" value="${esc(l.Id)}" ${p.EnableAllFolders || (p.EnabledFolders || []).includes(l.Id) ? "checked" : ""}> <div class="grow">${esc(l.Name)}</div></label>`).join("")}
        <div class="item"><div class="grow"></div><div class="acts"><button class="btn small" id="libCancel">Cancel</button><button class="btn small red" id="libSave">Save</button></div></div></div>`;
      $("#libPick").scrollIntoView({ behavior: "smooth" });
      $("#libCancel").onclick = () => { $("#libPick").innerHTML = ""; };
      $("#libSave").onclick = (ev) => {
        const ids = [...document.querySelectorAll("#libPick input:checked")].map((c) => c.value);
        busy(ev.target, async () => {
          await setPolicy(u, (pol) => { if (ids.length === libs.length) { pol.EnableAllFolders = true; } else { pol.EnableAllFolders = false; pol.EnabledFolders = ids; } });
          $("#libPick").innerHTML = ""; draw(); return "Saved.";
        });
      };
    } else if (b.dataset.a === "password") {
      if (!await B.ask(`Give ${u.Name} a new password? Their old one stops working.`)) return;
      busy(b, async () => {
        const pw = newPassword();
        const body = { NewPw: pw, ResetPassword: false };
        try { await jf("POST", `/Users/Password?userId=${encodeURIComponent(u.Id)}`, body); }
        catch (err) { await jf("POST", `/Users/${encodeURIComponent(u.Id)}/Password`, body); } // older Jellyfin
        B.notice(`${u.Name}'s new password: ${pw}`);
      });
    } else if (b.dataset.a === "signout") {
      if (!await B.ask(`Sign ${u.Name} out on every TV, phone and browser?${u.Id === B.me.id ? " (Not this page.)" : ""}`)) return;
      busy(b, async () => {
        // Jellyfin lists every device the person may use (one row per signed-in account), and deleting a
        // device id signs out everyone on it. Profiles on one Benflix TV share an id, so only devices this
        // person is signed in on are used, and ones someone else is also signed in on are left alone.
        const d = await jf("GET", `/Devices`);
        const rows = (d && d.Items) || [];
        const mine = "benflix-web-" + B.me.device;
        const shared = new Set(rows.filter((x) => x.LastUserId && x.LastUserId !== u.Id).map((x) => x.Id));
        const ids = [...new Set(rows.filter((x) => x.Id && x.Id !== mine && x.LastUserId === u.Id).map((x) => x.Id))];
        const devs = ids.filter((x) => !shared.has(x));
        const skipped = ids.length - devs.length;
        let n = 0;
        for (const x of devs) { try { await jf("DELETE", `/Devices?id=${encodeURIComponent(x)}`); n++; } catch (err) {} }
        return `Signed out on ${n} device${n === 1 ? "" : "s"}.` +
          (skipped ? ` ${skipped} left alone because other people are signed in on ${skipped === 1 ? "it" : "them"} too.` : "");
      });
    } else if (b.dataset.a === "remove") {
      if (!await B.ask(`Remove ${u.Name}'s account? Their watch history goes too. This can't be undone.`)) return;
      busy(b, async () => { await jf("DELETE", `/Users/${encodeURIComponent(u.Id)}`); draw(); return `${u.Name} was removed.`; });
    }
  };
  draw();
};

PAGES.problems = (el) => {
  el.innerHTML = `<h2>Problems</h2><p class="sub">Problems people reported from Benflix, and problems from the server in the last day.</p>
    <h3 class="section-title">Reported by people</h3><div id="rep">${loading()}</div>
    <h3 class="section-title">From the server</h3><div id="srv">${loading()}</div>`;
  let reports = [];
  const drawReports = async () => {
    try {
      const all = await users();
      const per = await Promise.all(all.map(async (u) => {
        try {
          const p = await getPrefs(u.Id);
          return String((p.CustomPrefs || {})["benflix.problems"] || "").split("\n").filter(Boolean).map((line) => {
            const f = line.split("|"); if (f.length < 5) return null;
            return { at: +f[0], raw: f[0], userId: u.Id, user: u.Name, itemId: f[1], title: f[2], problem: f[3], device: f[4] };
          }).filter(Boolean);
        } catch (err) { return []; }
      }));
      reports = per.flat().sort((a, b) => b.at - a.at);
      if (current !== "problems") return;
      $("#rep").innerHTML = `<div class="list">` + (reports.map((r, i) => `<div class="item"><span class="dot warn"></span>
        <div class="grow"><div class="t">${esc(r.problem)} · ${esc(r.title)}</div><div class="s">${esc(r.user)} on ${esc(r.device)} · ${esc(when(new Date(r.at).toISOString()))}</div></div>
        <div class="acts"><button class="btn small" data-fix="${i}">Fixed</button></div></div>`).join("")
        || `<div class="empty">Nobody reported a problem. 🎉</div>`) + `</div>`;
    } catch (err) { if (current === "problems") $("#rep").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  const drawServer = async () => {
    try {
      const since = new Date(Date.now() - 86400000).toISOString();
      const r = await jf("GET", `/System/ActivityLog/Entries?startIndex=0&limit=300&minDate=${encodeURIComponent(since)}`);
      const errs = ((r && r.Items) || []).filter((e) => e.Severity === "Error" || e.Severity === "Critical");
      const groups = new Map();
      errs.forEach((e) => {
        const [text, tip] = plainProblem(e.Name, e.Type, e.ShortOverview);
        const g = groups.get(text) || { text, tip, count: 0, date: e.Date };
        g.count++; if (e.Date > g.date) g.date = e.Date; groups.set(text, g);
      });
      const list = [...groups.values()].sort((a, b) => (a.date < b.date ? 1 : -1));
      if (current !== "problems") return;
      $("#srv").innerHTML = `<div class="list">` + (list.map((g) => `<div class="item"><span class="dot bad"></span><div class="grow">
        <div class="t">${esc(g.text)}${g.count > 1 ? ` ×${g.count}` : ""}</div><div class="s">${esc(when(g.date))}${g.tip ? " · " + esc(g.tip) : ""}</div></div></div>`).join("")
        || `<div class="empty">No server problems in the last day.</div>`) + `</div>`;
    } catch (err) { if (current === "problems") $("#srv").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  el.onclick = async (e) => {
    const b = e.target.closest("[data-fix]"); if (!b) return;
    const r = reports[+b.dataset.fix];
    busy(b, async () => {
      await updatePrefs(r.userId, (c) => {
        const left = String(c["benflix.problems"] || "").split("\n").filter((l) => l && !l.startsWith(r.raw + "|"));
        c["benflix.problems"] = left.length ? left.join("\n") : null;
        const fixed = String(c["benflix.fixed"] || "").split("\n").filter(Boolean);
        fixed.push(r.raw + "|" + r.title.replace(/[|\n]/g, " "));
        c["benflix.fixed"] = fixed.slice(-5).join("\n");
      });
      drawReports();
      return `Done. ${r.user} will see “Ben fixed ${r.title}” on their Home.`;
    });
  };
  drawReports(); drawServer();
};

PAGES.messages = (el) => {
  el.innerHTML = `<h2>Messages</h2><p class="sub">Notes on everyone's Home, the welcome note new people see once, and pop-ups on screens that are on right now.</p><div id="ms">${loading()}</div>`;
  const decode = (v) => {
    if (!v) return null; const i = v.indexOf("\n"); if (i <= 0) return null;
    const head = v.slice(0, i), exp = +(head.split("~")[1] || 0);
    return { text: v.slice(i + 1), expires: exp || null };
  };
  const draw = async () => {
    try {
      const p = await getPrefs(B.me.id);
      const c = p.CustomPrefs || {};
      const note = decode(c["benflix.note"]), welcome = decode(c["benflix.welcome"]);
      if (current !== "messages") return;
      const live = note && (!note.expires || note.expires > Date.now()) ? note : null;
      $("#ms").innerHTML = `
        <h3 class="section-title">Note on Home</h3>
        <div class="list"><div class="item" style="flex-direction:column;align-items:stretch">
          <textarea class="field" id="noteText" rows="3" placeholder="Like: Server is getting new drives Saturday morning" style="resize:vertical;margin:0">${esc(live ? live.text : "")}</textarea>
          <div class="row"><label class="s">Take it down after
            <select class="field" id="noteDays" style="min-width:0;width:auto;display:inline-block;padding:6px 10px"><option value="0">never</option><option value="1">1 day</option><option value="3">3 days</option><option value="7" selected>1 week</option><option value="30">1 month</option></select></label>
            <span style="flex:1"></span><button class="btn small" data-note="clear">Remove note</button><button class="btn small red" data-note="save">Put on Home</button></div>
          ${live && live.expires ? `<div class="s">Showing now, until ${esc(when(new Date(live.expires).toISOString()))}.</div>` : live ? `<div class="s">Showing now.</div>` : ""}
        </div></div>
        <h3 class="section-title">Welcome note</h3>
        <div class="list"><div class="item" style="flex-direction:column;align-items:stretch">
          <textarea class="field" id="welText" rows="3" placeholder="Shows once to each person the first time they open Benflix" style="resize:vertical;margin:0">${esc(welcome ? welcome.text : "")}</textarea>
          <div class="row"><span style="flex:1"></span><button class="btn small" data-wel="clear">Remove</button><button class="btn small red" data-wel="save">Save welcome note</button></div>
        </div></div>
        <h3 class="section-title">Pop-up right now</h3>
        <div class="list"><div class="item" style="flex-direction:column;align-items:stretch">
          <input class="field" id="popText" placeholder="Like: Restarting the server in 5 minutes" style="margin:0">
          <div class="row"><span class="s" style="flex:1">Shows for 15 seconds on every Benflix screen that's on.</span><button class="btn small red" id="popSend">Send pop-up</button></div>
        </div></div>`;
    } catch (err) { if (current === "messages") $("#ms").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  const publish = async (key, text, expires) => {
    const all = await users();
    const value = text ? Date.now() + (expires ? "~" + expires : "") + "\n" + text : null;
    let n = 0;
    for (const u of all) { try { await updatePrefs(u.Id, (c) => { c[key] = value; }); n++; } catch (err) {} }
    return n;
  };
  el.onclick = async (e) => {
    const nb = e.target.closest("[data-note]"), wb = e.target.closest("[data-wel]"), pb = e.target.closest("#popSend");
    if (nb) {
      const text = nb.dataset.note === "save" ? $("#noteText").value.trim() : "";
      if (nb.dataset.note === "save" && !text) return B.toast("Type a note first.");
      const days = +$("#noteDays").value;
      busy(nb, async () => { const n = await publish("benflix.note", text, text && days ? Date.now() + days * 86400000 : null); draw(); return text ? `The note is on ${n} people's Home.` : "Note removed."; });
    } else if (wb) {
      const text = wb.dataset.wel === "save" ? $("#welText").value.trim() : "";
      if (wb.dataset.wel === "save" && !text) return B.toast("Type a welcome note first.");
      busy(wb, async () => { await publish("benflix.welcome", text, null); draw(); return text ? "Welcome note saved." : "Welcome note removed."; });
    } else if (pb) {
      const text = $("#popText").value.trim(); if (!text) return B.toast("Type the pop-up first.");
      busy(pb, async () => {
        const s = ((await jf("GET", "/Sessions?activeWithinSeconds=960")) || []).filter((x) => x.SupportsRemoteControl && x.Id);
        let n = 0;
        for (const x of s) { try { await jf("POST", `/Sessions/${encodeURIComponent(x.Id)}/Message`, { Header: "Message from " + B.me.name, Text: text, TimeoutMs: 15000 }); n++; } catch (err) {} }
        $("#popText").value = "";
        return n ? `Sent to ${n} screen${n === 1 ? "" : "s"}.` : "No Benflix screens are on right now.";
      });
    }
  };
  draw();
};

PAGES.chores = (el) => {
  el.innerHTML = `<h2>Chores</h2><p class="sub">Jellyfin's background jobs. Run one now if you don't want to wait for its turn.</p>
    <div class="list" style="margin-bottom:16px"><div class="item"><span class="dot warn"></span><div class="grow"><div class="t">Restart Jellyfin</div><div class="s">Fixes most odd problems. Everyone watching is cut off for about a minute.</div></div><div class="acts"><button class="btn small" id="restartJf">Restart Jellyfin</button></div></div></div>
    <div id="ch">${loading()}</div>`;
  const draw = async () => {
    try {
      const tasks = ((await jf("GET", "/ScheduledTasks?isHidden=false")) || []).sort((a, b) => (b.State === "Running") - (a.State === "Running") || a.Name.localeCompare(b.Name));
      if (current !== "chores") return;
      $("#ch").innerHTML = `<div class="list">` + tasks.map((t) => {
        const r = t.LastExecutionResult, running = t.State !== "Idle";
        const failed = r && (r.Status === "Failed" || r.Status === "Aborted");
        return `<div class="item"><span class="dot ${running ? "" : failed ? "bad" : "off"}"></span>
          <div class="grow"><div class="t">${esc(t.Name)}</div>
            <div class="s">${running ? `Running${t.CurrentProgressPercentage != null ? " · " + Math.round(t.CurrentProgressPercentage) + "%" : ""}` : r ? (failed ? "Failed " : "Last ran ") + esc(when(r.EndTimeUtc)) : "Hasn't run yet"}${failed && r.ErrorMessage ? " · " + esc(r.ErrorMessage) : ""}</div>
            ${running && t.CurrentProgressPercentage != null ? `<div class="bar"><i style="width:${t.CurrentProgressPercentage}%"></i></div>` : ""}</div>
          <div class="acts">${running ? "" : `<button class="btn small" data-run="${esc(t.Id)}" data-name="${esc(t.Name)}">Run now</button>`}</div></div>`;
      }).join("") + `</div>`;
    } catch (err) { if (current === "chores") $("#ch").innerHTML = `<div class="note">${esc(err.message)}</div>`; }
  };
  el.onclick = async (e) => {
    const b = e.target.closest("[data-run]");
    if (b) busy(b, async () => { await jf("POST", `/ScheduledTasks/Running/${encodeURIComponent(b.dataset.run)}`); setTimeout(draw, 800); return `${b.dataset.name} started.`; });
    if (e.target.id === "restartJf") {
      if (!await B.ask("Restart Jellyfin now? Everyone watching is cut off for about a minute.")) return;
      busy(e.target, async () => { await jf("POST", "/System/Restart"); return "Jellyfin is restarting. Give it a minute."; });
    }
  };
  draw(); every(5000, draw);
};

