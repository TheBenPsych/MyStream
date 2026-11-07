/* Benflix web: My watching, Settings and Help and updates. Settings and Help work like the TV:
   list on the left, page on the right; choices pop out beside the row; saves straight away. */
"use strict";

// ---------- My watching ----------
B.route(/^\/mine/, async (m, nav) => {
  const range = B.q("range") || "month";
  B.page().innerHTML = B.top("My watching", null) + `<div class="wrap" id="mw">${B.spinner()}</div>`;
  B.wireTop();
  const u = B.uid();
  const since = range === "month" ? new Date(new Date().getFullYear(), new Date().getMonth(), 1) : new Date(new Date().getFullYear(), 0, 1);
  const [wt, played, liked] = await Promise.all([
    B.watchTime().catch(() => ({ month: 0, byMonth: {} })),
    B.get(`/Items?userId=${u}&IncludeItemTypes=Movie,Episode&Recursive=true&Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=400&Fields=Genres,RunTimeTicks,SeriesName,SeriesId`).then((r) => r.Items || []).catch(() => []),
    B.get(`/Items?userId=${u}&IncludeItemTypes=Movie,Series&Recursive=true&Filters=Likes&Limit=30&Fields=${B.FIELDS}`).then((r) => B.keep(r.Items || [])).catch(() => []),
  ]);
  if (!B.alive(nav)) return;
  const inRange = played.filter((x) => x.UserData && x.UserData.LastPlayedDate && new Date(x.UserData.LastPlayedDate) >= since);
  const secs = range === "month" ? wt.month : Object.entries(wt.byMonth).filter(([k]) => k.startsWith(String(new Date().getFullYear()))).reduce((a, [, v]) => a + v, 0);
  const movieSecs = inRange.filter((x) => x.Type === "Movie").reduce((a, x) => a + (x.RunTimeTicks || 0) / B.TICKS, 0);
  const showSecs = inRange.filter((x) => x.Type === "Episode").reduce((a, x) => a + (x.RunTimeTicks || 0) / B.TICKS, 0);
  const sum = movieSecs + showSecs || 1;
  const count = {}; inRange.filter((x) => x.SeriesName).forEach((x) => { count[x.SeriesName] = (count[x.SeriesName] || 0) + 1; });
  const most = Object.entries(count).sort((a, b) => b[1] - a[1])[0];
  const g = {}; inRange.forEach((x) => (x.Genres || []).forEach((n) => { g[n] = (g[n] || 0) + 1; }));
  const genre = Object.entries(g).sort((a, b) => b[1] - a[1])[0];
  const seen = new Set(), lately = [];
  played.forEach((x) => { const key = x.SeriesId || x.Id; if (!seen.has(key)) { seen.add(key); lately.push(x); } });
  const latelyItems = lately.slice(0, 20);
  const series = latelyItems.filter((x) => x.SeriesId).map((x) => x.SeriesId);
  const sItems = series.length ? await B.get(`/Items?userId=${u}&Ids=${series.join(",")}&Fields=${B.FIELDS}`).then((r) => B.keep(r.Items || [])).catch(() => []) : [];
  const byId = {}; sItems.forEach((s) => { byId[s.Id] = s; });
  if (!B.alive(nav)) return;
  $("#mw").innerHTML = `<div class="row" style="justify-content:space-between;align-items:flex-end;margin-top:10px">
      <div><div class="dim" style="font-weight:700">${range === "month" ? "This month" : "This year"}</div><h1 class="bigweek">${esc(B.hm(secs))}</h1></div>
      <div class="row"><a class="chip ${range === "month" ? "on" : ""}" href="#/mine?range=month">This month</a><a class="chip ${range === "year" ? "on" : ""}" href="#/mine?range=year">This year</a></div></div>
    <div class="doorbar"><i style="width:${(movieSecs / sum) * 100}%;background:${DOORS.movies.color}"></i><i style="width:${(showSecs / sum) * 100}%;background:${DOORS.shows.color}"></i></div>
    <div class="legend">${["movies", "shows", "live", "games"].map((k) => `<span><i style="background:${DOORS[k].color}"></i>${DOORS[k].name}</span>`).join("")}</div>
    <div class="tiles" style="margin-top:22px">
      <div class="tile"><div class="k">Most watched</div><div class="v" style="font-size:20px">${esc(most ? most[0] : "–")}</div></div>
      <div class="tile"><div class="k">Top genre</div><div class="v" style="font-size:20px">${esc(genre ? genre[0] : "–")}</div></div>
      <div class="tile"><div class="k">Things watched</div><div class="v" style="font-size:20px">${inRange.length}</div></div>
      <div class="tile"><div class="k">See your year</div><div class="v" style="font-size:20px">${new Date().getMonth() === 11 ? "Wrapped" : "In December"}</div></div>
    </div>
    <p class="dim" style="font-size:14px">Time counts every TV, phone and browser where you watch Benflix.</p></div>
    <div class="rows">${B.row("Watched lately", latelyItems.map((x) => B.posterCard(byId[x.SeriesId] || x)))}${B.row("You liked", liked.map((x) => B.posterCard(x)))}</div>`;
});

// ---------- Settings ----------
const GROUPS = [
  { id: "you", label: "You", icon: "user", line: "Follows you on every TV, phone and browser" },
  { id: "watching", label: "Watching", icon: "play", line: "Follows you on every TV, phone and browser" },
  { id: "picture", label: "Picture and sound", icon: "tv", line: "Only this browser" },
  { id: "look", label: "Look", icon: "gear", line: "Only this browser" },
  { id: "family", label: "Family", icon: "heart", line: "Follows you", adminOnly: true },
  { id: "devices", label: "Phones and TVs", icon: "phone", line: "Sign in a TV or phone with a code" },
  { id: "help", label: "Help", icon: "help", line: "" },
  { id: "behind", label: "Behind the scenes", icon: "key", line: "Main profile only", adminOnly: true },
];
const setRow = (t, s, control, id) => `<div class="setrow" ${id ? `id="${id}"` : ""}><div class="grow"><div class="t">${esc(t)}</div>${s ? `<div class="s">${esc(s)}</div>` : ""}</div>${control}</div>`;
const valBtn = (key, label) => `<button class="valbtn" data-pick="${esc(key)}">${esc(label)}</button>`;
B.route(/^\/settings(?:\/(\w+))?/, async (m, nav) => {
  if (B.me.kids) return B.go("#/home");
  const groups = GROUPS.filter((g) => !g.adminOnly || B.me.admin);
  const g = groups.find((x) => x.id === m[1]) || groups[0];
  B.page().innerHTML = B.top("Settings", null, { solid: true }) + `<div class="split">
    <nav><input class="field navsearch" id="setSearch" placeholder="Search settings">${groups.map((x) => `<a href="#/settings/${x.id}" class="${x === g ? "on" : ""}">${I(x.icon)}${esc(x.label)}</a>`).join("")}</nav>
    <main><h2>${esc(g.label)}</h2>${g.line ? `<div class="group-line" style="margin-top:4px">${esc(g.line)}</div>` : ""}<div id="sp">${B.spinner()}</div></main></div>`;
  B.wireTop();
  $("#setSearch").onkeydown = (e) => {
    if (e.key !== "Enter") return;
    const t = e.target.value.trim().toLowerCase();
    const map = { subtitle: "watching", audio: "watching", skip: "watching", autoplay: "watching", next: "watching", quality: "picture", sound: "picture", volume: "picture", text: "look", size: "look", password: "you", picture: "you", code: "devices", quick: "devices", connect: "devices", people: "family", reset: "behind" };
    const hit = Object.keys(map).find((k) => t.includes(k) || k.includes(t));
    if (hit) B.go("#/settings/" + map[hit]); else B.toast("No setting by that name.");
  };
  const ws = B.webSettings();
  const sp = $("#sp");
  if (g.id === "you") {
    sp.innerHTML = `<div class="row" style="gap:18px;margin-bottom:18px">${B.picHtml(B.me, "", 220).replace('class="pic', 'style="width:96px;height:96px;font-size:36px" class="pic')}<div><div style="font-size:24px;font-weight:800">${esc(B.me.name)}</div><div class="dim">${B.me.admin ? "Main profile" : "Profile"}</div></div></div>
      ${setRow("Profile picture", "Pick a picture from this computer. It shows on every TV and phone.", `<label class="valbtn" style="display:inline-block;cursor:pointer">Change<input type="file" accept="image/*" id="picFile" hidden></label>`)}
      ${setRow("Password", "Change the password you sign in with.", `<button class="valbtn" id="pwBtn">Change</button>`)}`;
    $("#picFile").onchange = (e) => {
      const f = e.target.files[0]; if (!f) return;
      const r = new FileReader();
      r.onload = () => B.busy(null, async () => {
        // Jellyfin takes the picture as base64 text
        const b64 = String(r.result).split(",")[1];
        const res = await fetch(`/api/web/jf/Users/${B.uid()}/Images/Primary`, { method: "POST", headers: { "X-Benflix-Token": B.me.token, "X-Benflix-Device": B.me.device, "Content-Type": f.type || "image/png" }, body: b64 });
        if (!res.ok) throw new Error("Couldn't save that picture (it may be too big; try one under 700 KB).");
        const me = await B.get(`/Users/${B.uid()}`);
        B.me.image = me.PrimaryImageTag; B.addProfile(B.me); B.render();
        return "New picture saved.";
      });
      r.readAsDataURL(f);
    };
    $("#pwBtn").onclick = async () => {
      const cur = await B.askText("Your current password", "", "", { password: true, ok: "Next" }); if (cur == null) return;
      const nw = await B.askText("Your new password", "", "", { password: true, ok: "Save" }); if (nw == null) return;
      B.busy(null, async () => { await B.jf("POST", `/Users/Password?userId=${B.uid()}`, { CurrentPw: cur, NewPw: nw }).catch(() => B.jf("POST", `/Users/${B.uid()}/Password`, { CurrentPw: cur, NewPw: nw })); return "Password changed."; });
    };
  }
  if (g.id === "watching") {
    const me = await B.get(`/Users/${B.uid()}`);
    if (!B.alive(nav)) return;
    const cfg = me.Configuration || {};
    const subModes = [["Default", "Only for other languages"], ["Always", "Always"], ["OnlyForced", "Only signs and songs"], ["None", "Off"]];
    const langs = [["", "The film's own language"], ["eng", "English"], ["jpn", "Japanese"], ["spa", "Spanish"], ["fre", "French"], ["ger", "German"], ["kor", "Korean"]];
    const label = (list, v) => (list.find((x) => x[0] === v) || list[0])[1];
    sp.innerHTML = setRow("Play the next episode by itself", "When an episode ends, the next one starts after 10 seconds.", valBtn("autoplay", ws.autoplay === false ? "Off" : "On"))
      + setRow("Skip intros and credits by themselves", "Otherwise a Skip button shows. You can also pick this per show in the player.", valBtn("autoskip", ws.autoskip ? "On" : "Off"))
      + setRow("Subtitles", "When subtitles turn on by themselves.", valBtn("subMode", label(subModes, cfg.SubtitleMode || "Default")))
      + setRow("Subtitle language", "", valBtn("subLang", label(langs, cfg.SubtitleLanguagePreference || "")))
      + setRow("Audio language", "", valBtn("audioLang", label(langs, cfg.AudioLanguagePreference || "")));
    sp.onclick = (e) => {
      const b = e.target.closest("[data-pick]"); if (!b) return;
      const k = b.dataset.pick;
      const saveCfg = (change) => B.busy(null, async () => { change(cfg); await B.jf("POST", `/Users/Configuration?userId=${B.uid()}`, cfg).catch(() => B.jf("POST", `/Users/${B.uid()}/Configuration`, cfg)); B.render(); });
      if (k === "autoplay") { B.setWebSetting("autoplay", ws.autoplay === false); cfg.EnableNextEpisodeAutoPlay = ws.autoplay === false; saveCfg(() => {}); }
      if (k === "autoskip") { B.setWebSetting("autoskip", !ws.autoskip); B.render(); }
      if (k === "subMode") B.dropdown(b, subModes.map(([value, l]) => ({ value, label: l })), cfg.SubtitleMode || "Default", (v) => saveCfg((c) => { c.SubtitleMode = v; }));
      if (k === "subLang") B.dropdown(b, langs.map(([value, l]) => ({ value, label: l })), cfg.SubtitleLanguagePreference || "", (v) => saveCfg((c) => { c.SubtitleLanguagePreference = v; }));
      if (k === "audioLang") B.dropdown(b, langs.map(([value, l]) => ({ value, label: l })), cfg.AudioLanguagePreference || "", (v) => saveCfg((c) => { c.AudioLanguagePreference = v; c.PlayDefaultAudioTrack = !v; }));
    };
  }
  if (g.id === "picture") {
    const q = [["auto", "Best (no limit)"], ["1080", "1080p"], ["720", "720p"], ["480", "480p (slow internet)"]];
    sp.innerHTML = setRow("Quality", "Lower it if videos keep stopping to load.", valBtn("quality", (q.find((x) => x[0] === (ws.quality || "auto")) || q[0])[1]))
      + setRow("Sounds", "The soft notes when Benflix opens and the doors appear.", valBtn("sounds", ws.sounds === false ? "Off" : "On"));
    sp.onclick = (e) => {
      const b = e.target.closest("[data-pick]"); if (!b) return;
      if (b.dataset.pick === "quality") B.dropdown(b, q.map(([value, label]) => ({ value, label })), ws.quality || "auto", (v) => { B.setWebSetting("quality", v); B.render(); });
      if (b.dataset.pick === "sounds") { B.setWebSetting("sounds", ws.sounds === false); B.render(); }
    };
  }
  if (g.id === "look") {
    const size = ws.textSize || 100;
    sp.innerHTML = setRow("Text size", "Makes everything in Benflix bigger or smaller in this browser.", `<div class="stepper"><button data-step="-10">−</button><span>${size}%</span><button data-step="10">+</button></div>`);
    sp.onclick = (e) => { const b = e.target.closest("[data-step]"); if (!b) return; const v = Math.max(80, Math.min(150, size + +b.dataset.step)); B.setWebSetting("textSize", v); B.applyLook(); B.render(); };
  }
  if (g.id === "family") {
    sp.innerHTML = setRow("People and their limits", "Add people, set screens, libraries and passwords.", `<a class="valbtn" href="#/server/people/people">Open</a>`)
      + setRow("What Kids watched", "", `<a class="valbtn" href="#/server/watching/now">Open</a>`);
  }
  if (g.id === "devices") {
    sp.innerHTML = `<p class="dim">On the TV or phone you want to sign in, choose “Sign in with my phone” or Quick Connect. It shows a 6-letter code. Type it here.</p>
      <form id="qcf" class="row"><input class="field" id="qcCode" maxlength="6" placeholder="Code" style="max-width:240px;font-size:32px;letter-spacing:.3em;text-transform:uppercase;font-weight:800;text-align:center"><button class="btn white big">Sign it in</button></form>`;
    $("#qcf").onsubmit = (e) => { e.preventDefault(); const c = $("#qcCode").value.trim().toUpperCase(); if (!c) return; B.busy(null, async () => { await B.jf("POST", `/QuickConnect/Authorize?code=${enc(c)}&userId=${B.uid()}`); $("#qcCode").value = ""; return "Done. That TV or phone is signing in as " + B.me.name + "."; }); };
  }
  if (g.id === "help") sp.innerHTML = setRow("Help and updates", "How to use Benflix, questions and answers, and what's new.", `<a class="valbtn" href="#/help">Open</a>`);
  if (g.id === "behind") {
    sp.innerHTML = `<div class="warnbox">These are for fixing things. Changing them can make Benflix act oddly in this browser.</div>
      ${setRow("Get the newest web version now", "Normally Tower checks every hour by itself.", `<a class="valbtn" href="#/server/tools/webversion">Open</a>`)}
      ${setRow("Reset this browser", "Forgets every profile and setting saved in this browser. Nothing on the server changes.", `<button class="valbtn" id="resetAll">Reset</button>`)}`;
    $("#resetAll").onclick = async () => {
      if (!(await B.ask("Reset Benflix in this browser?", "Everyone will need to sign in again here.", "Reset"))) return;
      try { Object.keys(localStorage).filter((k) => k.startsWith("benflix")).forEach((k) => localStorage.removeItem(k)); sessionStorage.clear(); } catch (e) {}
      location.hash = "#/signin"; location.reload();
    };
  }
});
B.applyLook = () => { const s = B.me ? B.webSettings().textSize || 100 : 100; document.body.style.zoom = s === 100 ? "" : s / 100; };
window.addEventListener("hashchange", () => B.applyLook());

// ---------- Help and updates ----------
const HELP = [
  { id: "updates", label: "Updates", icon: "dl" },
  { id: "new", label: "What's new", icon: "plus" },
  { id: "how", label: "How to use Benflix", icon: "help" },
  { id: "qa", label: "Questions and answers", icon: "search" },
  { id: "report", label: "Report a problem", icon: "flag" },
  { id: "about", label: "About this app", icon: "info" },
];
const QA = [
  ["Why won't a video play?", "Some files need the server to convert them for a browser. If one won't start, try lowering Quality in Settings > Picture and sound, or watch it on the TV or phone app."],
  ["Can I use this away from home?", "Not yet. Benflix in a browser only works on your home Wi-Fi."],
  ["How do I add someone to Who's watching here?", "Press “Add someone” under the pictures on Who's watching and sign in with their name and password, or with a code from a TV or phone."],
  ["Where are Benflix's own channels and games?", "Benflix's own channels and the Benflix games (puzzles, party games, Ember Road) are on the TV and phone app. The browser has live channels and console games."],
  ["Do my lists and progress follow me?", "Yes. My List, what you've watched, where you stopped, and your favourite channels are saved on the server, so they're the same on the TV, phone and here."],
  ["How do I skip intros by themselves?", "Settings > Watching > Skip intros and credits by themselves. Or open the player's Audio and subtitles panel and pick Always skip the intro for that show."],
  ["Keyboard keys in the player?", "Space pauses, the left and right arrows jump 10 seconds, up and down change the sound, F is full screen, M mutes, S skips the intro, Esc goes back."],
];
B.route(/^\/help(?:\/(\w+))?/, async (m, nav) => {
  const sec = HELP.find((h) => h.id === m[1]) || HELP[0];
  B.page().innerHTML = B.top("Help and updates", null, { solid: true }) + `<div class="split">
    <nav>${HELP.map((h) => `<a href="#/help/${h.id}" class="${h === sec ? "on" : ""}">${I(h.icon)}${esc(h.label)}</a>`).join("")}</nav>
    <main><h2>${esc(sec.label)}</h2><div id="hp" style="margin-top:14px">${B.spinner()}</div></main></div>`;
  B.wireTop();
  const hp = $("#hp");
  if (sec.id === "updates") {
    const s = await B.call("GET", "/api/web/version").catch(() => ({}));
    hp.innerHTML = `<p class="dim">Benflix in a browser updates itself: Tower checks for a new version when it starts and then every hour. Reload the page to use the newest one.</p>
      <div class="list"><div class="item"><div class="grow"><div class="t">Last checked</div><div class="s">${s.checked ? esc(B.when(new Date(s.checked * 1000).toISOString())) : "Not yet"}</div></div><div class="acts"><button class="btn small white" onclick="location.reload()">Reload now</button></div></div></div>
      <p class="dim">The TV and phone app update from Settings > Help and updates on each device.</p>`;
  }
  if (sec.id === "new") {
    hp.innerHTML = B.spinner();
    try {
      const r = await fetch("https://api.github.com/repos/TheBenPsych/MyStream/releases/tags/latest-build").then((x) => x.json());
      const lines = String(r.body || "").split("\n").map((l) => l.trim()).filter((l) => /^[-*]\s/.test(l)).slice(0, 25).map((l) => l.replace(/^[-*]\s+/, "").replace(/\(#\d+\)/, "").replace(/<!--.*?-->/g, "").trim()).filter(Boolean);
      hp.innerHTML = lines.length ? `<div class="list">${lines.map((l) => `<div class="item"><div class="grow">${esc(l)}</div></div>`).join("")}</div>` : `<p class="dim">No news yet.</p>`;
    } catch (e) { hp.innerHTML = `<p class="dim">Couldn't get the list of changes right now.</p>`; }
  }
  if (sec.id === "how") {
    hp.innerHTML = `<div class="list">
      ${[["Home", "Pick a door: Movies, Shows, Live TV or Games. Your picture in the top corner opens your menu."], ["Rows", "Point at a poster to see what it is at the top. Press it to open its page. Arrows at the sides of a row scroll it."], ["Playing", "Press Play or Resume. Move the mouse to see the controls. Space pauses, arrows jump 10 seconds."], ["Live TV", "Press a channel to watch. Up and down change channel, left opens the guide, typing a number jumps to that channel."], ["Search", "The magnifying glass at the top looks through everything, and shows what you can ask for if it isn't on Benflix."], ["My List", "Press My List on any movie or show page to save it for later."]].map(([t, s]) => `<div class="item"><div class="grow"><div class="t">${esc(t)}</div><div class="s">${esc(s)}</div></div></div>`).join("")}</div>`;
  }
  if (sec.id === "qa") {
    hp.innerHTML = `<input class="field" id="qaSearch" placeholder="Search questions" style="margin-bottom:14px"><div id="qaList"></div>`;
    const draw = (t) => { $("#qaList").innerHTML = `<div class="list">${QA.filter(([q2, a]) => !t || (q2 + a).toLowerCase().includes(t.toLowerCase())).map(([q2, a]) => `<div class="item"><div class="grow"><div class="t">${esc(q2)}</div><div class="s">${esc(a)}</div></div></div>`).join("") || '<div class="empty">No answer for that yet. Use Report a problem to ask Ben.</div>'}</div>`; };
    $("#qaSearch").oninput = (e) => draw(e.target.value);
    draw("");
  }
  if (sec.id === "report") {
    hp.innerHTML = `<p class="dim">Pick what's wrong. Ben gets it with what was playing.</p><button class="btn white" id="rp">Report a problem</button>`;
    $("#rp").onclick = () => B.report();
  }
  if (sec.id === "about") {
    const info = await B.get("/System/Info/Public").catch(() => ({}));
    hp.innerHTML = `<div class="list">
      <div class="item"><div class="grow"><div class="t">Benflix in a browser</div><div class="s">Runs on Tower's Benflix helper. Signed in as ${esc(B.me.name)}.</div></div></div>
      <div class="item"><div class="grow"><div class="t">Server</div><div class="s">${esc(info.ServerName || "")} · Jellyfin ${esc(info.Version || "")}</div></div></div>
      <div class="item"><div class="grow"><div class="t">Made from</div><div class="s">Benflix is built on Wholphin (GPL-2.0) by damontecres. Video in the browser uses hls.js (Apache-2.0). Console games use EmulatorJS (GPL-3.0).</div></div></div></div>`;
  }
});
