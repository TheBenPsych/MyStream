/* Glowfin desktop: the offline library and player (offline.html). Lists what was downloaded to this
   computer and plays it with no connection to Tower. Where you stopped is remembered on this computer. */
(function () {
"use strict";

const G = window.glowfinDesktop;
const $ = (s, r) => (r || document).querySelector(s);
const esc = (t) => String(t == null ? "" : t).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const page = () => $("#page");
const file = (id, name) => `dl/${id}/${name}`;
const size = (b) => (b >= 1073741824 ? (b / 1073741824).toFixed(1) + " GB" : Math.max(1, Math.round(b / 1048576)) + " MB");
let items = [];
let leave = null;

const top = (where) => `<div class="top"><img src="glowfin-mark.svg" alt=""><span class="name">Glow<b>fin</b></span><span class="where">${esc(where)}</span>
  <span class="right"><button class="btn" id="oAgain">Try Tower again</button></span></div>`;
function wireTop() { const b = $("#oAgain"); if (b) b.onclick = () => { b.disabled = true; b.textContent = "Trying"; G.reload(); }; }

// Who's watching: only asked when more than one profile has downloads here. A Kids profile sees only its own.
const who = () => { try { return JSON.parse(sessionStorage.getItem("who") || "null"); } catch (e) { return null; } };
function mine() {
  const w = who();
  if (!w) return items;
  return w.kids ? items.filter((m) => m.kids && m.userId === w.id) : items.filter((m) => !m.kids || m.userId === w.id);
}

function library() {
  const people = [];
  items.forEach((m) => { if (m.userId && !people.some((p) => p.id === m.userId)) people.push({ id: m.userId, name: m.user || "Someone", kids: !!m.kids }); });
  if (!items.length) {
    page().innerHTML = top("Your downloads") + `<div class="empty"><h2>Nothing downloaded yet</h2><p>When Tower is reachable, open a movie or an episode, pick More, then Download to this computer. It shows up here to watch anywhere.</p></div>`;
    return wireTop();
  }
  if (people.length > 1 && !who()) {
    page().innerHTML = top("Your downloads") + `<div class="empty"><h2>Who's watching?</h2><div class="who">${people.map((p, i) => `<button data-p="${i}">${esc(p.name)}</button>`).join("")}</div></div>`;
    wireTop();
    page().onclick = (e) => { const b = e.target.closest("[data-p]"); if (!b) return; sessionStorage.setItem("who", JSON.stringify(people[+b.dataset.p])); page().onclick = null; library(); };
    const f = $(".who button"); if (f) f.focus();
    return;
  }
  const list = mine();
  const movies = list.filter((m) => m.kind !== "episode").sort((a, b) => a.name.localeCompare(b.name));
  const shows = {};
  list.filter((m) => m.kind === "episode").forEach((m) => { (shows[m.show || "Shows"] = shows[m.show || "Shows"] || []).push(m); });
  const left = (m) => (m.position > 30 && m.minutes ? `<div class="bar"><i style="width:${Math.min(100, Math.round((m.position / (m.minutes * 60)) * 100))}%"></i></div>` : "");
  const card = (m) => `<a class="card" href="#/play/${m.id}" data-id="${m.id}"><div class="im" ${m.poster ? `style="background-image:url('${file(m.id, "poster.jpg")}')"` : ""}>${m.poster ? "" : esc(m.name)}</div>${left(m)}<div class="cap"><b>${esc(m.name)}</b><small>${[m.year || "", m.minutes ? m.minutes + " min" : "", size(m.bytes || 0)].filter(Boolean).join(" · ")}</small></div></a>`;
  const ep = (m) => `<a class="ep" href="#/play/${m.id}" data-id="${m.id}"><span class="n">S${m.season} E${m.episode}</span><span><b>${esc(m.name)}</b><small>${[m.watched ? "Watched" : m.position > 30 ? "Started" : "", m.minutes ? m.minutes + " min" : "", size(m.bytes || 0)].filter(Boolean).join(" · ")}</small></span></a>`;
  const w = who();
  page().innerHTML = top("Your downloads" + (w && people.length > 1 ? " · " + w.name : ""))
    + `<div class="wrap">${list.length ? "" : `<div class="empty"><h2>Nothing here for ${esc(w ? w.name : "you")}</h2></div>`}
    ${movies.length ? `<h2>Movies</h2><div class="grid">${movies.map(card).join("")}</div>` : ""}
    ${Object.keys(shows).length ? `<h2>Shows</h2>` + Object.keys(shows).sort().map((s) => `<h3>${esc(s)}</h3><div class="eps">${shows[s].sort((a, b) => a.season - b.season || a.episode - b.episode).map(ep).join("")}</div>`).join("") : ""}
    ${people.length > 1 ? `<p style="margin-top:34px"><button class="btn" id="oWho">Switch profile</button></p>` : ""}</div>`;
  wireTop();
  const sw = $("#oWho"); if (sw) sw.onclick = () => { sessionStorage.removeItem("who"); library(); };
}

function play(id) {
  const m = items.find((x) => x.id === id);
  if (!m || !mine().includes(m)) { location.hash = "#/"; return; }
  const title = m.kind === "episode" ? `${m.show} · S${m.season} E${m.episode} · ${m.name}` : m.name;
  page().innerHTML = `<div class="player ui" id="pl"><video id="v" controls playsinline></video>
    <div class="topline"><button class="btn" id="pBack">Back</button><span class="tt">${esc(title)}</span></div><div class="msg" id="msg"></div></div>`;
  const v = $("#v"), pl = $("#pl");
  let hls = null, uiTimer = null, saver = null;
  const poke = () => { pl.classList.add("ui"); clearTimeout(uiTimer); uiTimer = setTimeout(() => { if (!v.paused) pl.classList.remove("ui"); }, 3500); };
  const save = (done) => { if (v.currentTime > 0) G.downloads.position(m.id, v.currentTime, !!done).catch(() => {}); m.position = done ? 0 : v.currentTime; if (done) m.watched = true; };
  (m.tracks || []).forEach((t) => {
    const tr = document.createElement("track");
    tr.kind = "subtitles"; tr.label = t.label; tr.srclang = t.lang || "en"; tr.src = file(m.id, t.file); tr.default = !!t.on;
    v.appendChild(tr);
  });
  const src = file(m.id, "index.m3u8");
  const begin = () => { if (m.position > 30 && (!m.minutes || m.position < m.minutes * 60 * 0.95)) v.currentTime = m.position; v.play().catch(() => {}); };
  if (window.Hls && window.Hls.isSupported()) {
    hls = new window.Hls({ maxBufferLength: 60 });
    hls.loadSource(src);
    hls.attachMedia(v);
    hls.on(window.Hls.Events.MANIFEST_PARSED, begin);
    hls.on(window.Hls.Events.ERROR, (e, d) => { if (!d.fatal) return; if (d.type === window.Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError(); else $("#msg").textContent = "This download won't play. Delete it and download it again."; });
  } else { v.src = src; v.addEventListener("loadedmetadata", begin, { once: true }); }
  const back = () => { if (history.length > 1) history.back(); else location.hash = "#/"; };
  $("#pBack").onclick = back;
  const keys = (e) => {
    if (e.target && e.target.tagName === "BUTTON" && (e.key === " " || e.key === "Enter")) return;
    const k = e.key;
    if (k === " " || k === "k") { e.preventDefault(); v.paused ? v.play() : v.pause(); }
    else if (k === "ArrowLeft" || k === "j") { e.preventDefault(); v.currentTime = Math.max(0, v.currentTime - 10); }
    else if (k === "ArrowRight" || k === "l") { e.preventDefault(); v.currentTime = Math.min(v.duration || 1e9, v.currentTime + 10); }
    else if (k === "ArrowUp") { e.preventDefault(); v.volume = Math.min(1, v.volume + 0.1); }
    else if (k === "ArrowDown") { e.preventDefault(); v.volume = Math.max(0, v.volume - 0.1); }
    else if (k === "f" || k === "F") G.fullscreen();
    else if (k === "s" || k === "S" || k === "c") { const tt = Array.from(v.textTracks); const on = tt.find((t) => t.mode === "showing"); tt.forEach((t) => { t.mode = "disabled"; }); if (!on && tt[0]) tt[0].mode = "showing"; }
    else if (k === "Escape") back();
    else return;
    poke();
  };
  document.addEventListener("keydown", keys);
  pl.addEventListener("mousemove", poke);
  v.addEventListener("pause", () => { poke(); save(false); });
  v.addEventListener("ended", () => { save(true); back(); });
  saver = setInterval(() => { if (!v.paused) save(v.duration && v.currentTime > v.duration * 0.95); }, 5000);
  poke();
  leave = () => { save(v.duration && v.currentTime > v.duration * 0.95); clearInterval(saver); clearTimeout(uiTimer); document.removeEventListener("keydown", keys); if (hls) hls.destroy(); v.removeAttribute("src"); v.load(); };
}

async function render() {
  if (leave) { try { leave(); } catch (e) { /* the player is going away anyway */ } leave = null; }
  items = (await G.downloads.list().catch(() => [])).filter((m) => m.state === "done");
  const m = /^#\/play\/([0-9a-f]{32})/.exec(location.hash);
  if (m) play(m[1]); else library();
}
window.addEventListener("hashchange", render);
render();
})();
