/* Benflix web: My watching, Settings and Help and updates. Settings and Help work like the TV:
   list on the left, page on the right; choices pop out beside the row; saves straight away. */
"use strict";

// ---------- My watching ----------
B.route(/^\/mine/, async (m, nav) => {
  const range = B.q("range") || "month";
  B.page().innerHTML = B.top("My watching", null) + `<div class="wrap" id="mw">${B.spinner("rows")}</div>`;
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
    <div class="doorbar"><i style="width:${(movieSecs / sum) * 100}%;background:#FFB547"></i><i style="width:${(showSecs / sum) * 100}%;background:#7AA7FF"></i></div>
    <div class="legend">${["movies", "shows", "live", "games"].map((k) => `<span><i style="background:${({ movies: "#FFB547", shows: "#7AA7FF", live: "#3DD6C6", games: "#FF6F91" })[k]}"></i>${DOORS[k].name}</span>`).join("")}</div>
    <div class="tiles" style="margin-top:22px">
      <div class="tile"><div class="k">Most watched</div><div class="v" style="font-size:20px">${esc(most ? most[0] : "–")}</div></div>
      <div class="tile"><div class="k">Top genre</div><div class="v" style="font-size:20px">${esc(genre ? genre[0] : "–")}</div></div>
      <div class="tile"><div class="k">Things watched</div><div class="v" style="font-size:20px">${inRange.length}</div></div>
      <div class="tile"><div class="k">See your year</div><div class="v" style="font-size:20px">${new Date().getMonth() === 11 ? "Wrapped" : "In December"}</div></div>
    </div>
    <p class="dim" style="font-size:14px">Time counts every TV, phone and browser where you watch Glowfin.</p>
    <p><a class="btn small" href="#/history">Watch history</a></p></div>
    <div class="rows">${B.row("Watched lately", latelyItems.map((x) => B.posterCard(byId[x.SeriesId] || x)))}${B.row("You liked", liked.map((x) => B.posterCard(x)))}</div>`;
});

// ---------- Watch history (2026-11-21): everything watched, newest first, with Remove (not for Kids) ----------
B.route(/^\/history/, async (m, nav) => {
  B.page().innerHTML = B.top("Watch history", null) + `<div class="wrap" id="wh">${B.spinner("list")}</div>`;
  B.wireTop();
  const u = B.uid();
  const played = await B.get(`/Items?userId=${u}&IncludeItemTypes=Movie,Episode&Recursive=true&Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=300&Fields=SeriesName,SeriesId,ParentIndexNumber,IndexNumber,ProductionYear&EnableUserData=true`).then((r) => B.keep(r.Items || [])).catch(() => null);
  if (!B.alive(nav)) return;
  const box = $("#wh");
  if (!played) { box.innerHTML = `<div class="note">Couldn't load your history. Check the connection and open this page again.</div>`; return; }
  if (!played.length) { box.innerHTML = `<p class="dim">Nothing watched yet. Whatever you finish shows up here.</p>`; return; }
  const kids = !!B.me.kids;
  const line = (x) => {
    const ep = x.Type === "Episode";
    const title = ep ? x.SeriesName || x.Name : x.Name;
    const sub = [ep ? `S${x.ParentIndexNumber || 0} E${x.IndexNumber || 0} · ${x.Name}` : x.ProductionYear, x.UserData && x.UserData.LastPlayedDate ? "Watched " + B.when(x.UserData.LastPlayedDate) : ""].filter(Boolean).join(" · ");
    return `<div class="item" data-h="${esc(x.Id)}"><div class="rqthumb" style="background-image:url('${B.poster(x, 200)}')"></div>
      <div class="grow"><div class="t">${esc(title)}</div><div class="s">${esc(sub)}</div></div>
      <div class="acts"><a class="btn small" href="#/${ep ? `show/${esc(x.SeriesId)}?ep=${esc(x.Id)}` : `movie/${esc(x.Id)}`}">Open</a>${kids ? "" : `<button class="btn small" data-hx="${esc(x.Id)}">Remove</button>`}</div></div>`;
  };
  box.innerHTML = `<p class="dim" style="margin:6px 0 14px">${kids ? "Everything you've watched, newest first." : "Everything you've watched, newest first. Remove takes one off and marks it as not watched."}</p><div class="list">${played.map(line).join("")}</div>`;
  box.onclick = (e) => {
    const b = e.target.closest("[data-hx]");
    if (!b) return;
    B.busy(b, async () => {
      const id = b.dataset.hx;
      await B.jf("DELETE", `/UserPlayedItems/${id}?userId=${u}`).catch(() => B.jf("DELETE", `/Users/${u}/PlayedItems/${id}`));
      const row = b.closest(".item"); if (row) row.remove();
      return "Taken off your history.";
    });
  };
});

// ---------- Settings ----------
const GROUPS = [
  { id: "you", label: "You", icon: "user", line: "Follows you on every TV, phone and browser" },
  { id: "watching", label: "Watching", icon: "play", line: "Follows you on every TV, phone and browser" },
  { id: "picture", label: "Picture and sound", icon: "tv", line: "Only this browser" },
  { id: "look", label: "Look", icon: "gear", line: "Only this browser" },
  { id: "family", label: "Family", icon: "heart", line: "Follows you", adminOnly: true },
  { id: "devices", label: "Phones and TVs", icon: "phone", line: "Sign in a TV or phone with a code" },
  { id: "install", label: "Install Glowfin", icon: "dl", line: "Only this computer" },
  { id: "help", label: "Help", icon: "help", line: "" },
  { id: "behind", label: "Behind the scenes", icon: "key", line: "Main profile only", adminOnly: true },
];
// Glowfin (S1): the setting's name, its current choice in aqua on the right, and one explaining line while it's lit
const setRow = (t, s, control, id) => `<div class="setrow" data-title="${esc(t)}" ${id ? `id="${id}"` : ""}><div class="grow"><div class="t">${esc(t)}</div>${s ? `<div class="s">${esc(s)}</div>` : ""}</div>${control}</div>`;
// Choices open in the small list on the right (like the TV), and save straight away
const pickFrom = (b, options, current, onPick) => {
  const row = b.closest(".setrow");
  B.dropdown(b, options, current, onPick, { side: true, title: row ? row.dataset.title : "" });
};
const valBtn = (key, label) => `<button class="valbtn${label === "On" ? " on" : ""}" data-pick="${esc(key)}">${esc(label)}</button>`;
B.route(/^\/settings(?:\/(\w+))?/, async (m, nav) => {
  if (B.me.kids) return B.go("#/home");
  const groups = GROUPS.filter((g) => !g.adminOnly || B.me.admin);
  const g = groups.find((x) => x.id === m[1]) || groups[0];
  B.page().innerHTML = B.top("Settings", null, { solid: true }) + `<div class="split">
    <nav><div class="splith">Settings</div><input class="field navsearch" id="setSearch" placeholder="Find a setting" autocomplete="off"><div class="find-hits" id="findHits"></div>${groups.map((x) => `<a href="#/settings/${x.id}" class="${x === g ? "on" : ""}${x.id === "behind" ? " admin" : ""}">${esc(x.label)}</a>`).join("")}</nav>
    <main><h2>${esc(g.label)}</h2>${g.line ? `<div class="group-line" style="margin-top:4px">${esc(g.line)}</div>` : ""}<div id="sp">${B.spinner()}</div></main></div>`;
  B.wireTop();
  // Approved ss-family + ss-q-find: "Find a setting" lists matches as you type. For Ben it also finds Server tools.
  const FIND = [
    ["Subtitles", "subtitle captions", "#/settings/watching"], ["Audio language", "audio language dub", "#/settings/watching"],
    ["Skip intros by themselves", "skip intro credits", "#/settings/watching"], ["Next episode by itself", "autoplay next episode", "#/settings/watching"],
    ["Quality", "quality picture slow", "#/settings/picture"], ["Sounds", "sound volume notes music bars", "#/settings/picture"],
    ["Text size", "text size bigger zoom", "#/settings/look"], ["Password", "password", "#/settings/you"], ["Profile picture", "picture photo avatar", "#/settings/you"],
    ["Sign in a TV or phone", "code quick connect sign in tv phone", "#/settings/devices"], ["Install Glowfin", "install app", "#/settings/install"],
    ["Help and updates", "help update how", "#/settings/help"],
  ].concat(B.me.admin ? [
    ["People and their limits", "people family kids limits", "#/settings/family"], ["Reset this browser", "reset behind", "#/settings/behind"],
    ["Server: Who's watching now", "watching streams stop", "#/server/watching/now"], ["Server: People and accounts", "users add person account", "#/server/people/people"],
    ["Server: Library checkup", "checkup tidy unwatched biggest", "#/server/tools/tidy"], ["Server: Fix posters", "poster pictures art", "#/server/tools/posters"],
    ["Server: Find copies", "find better copy missing episodes", "#/server/tools/find"], ["Server: Downloads", "download stuck sonarr radarr", "#/server/tools/downloads"],
    ["Server: Messages", "message note send", "#/server/tools/messages"], ["Server: Glowfin versions", "versions update out of date", "#/server/tools/versions"],
    ["Server: Track names", "track names audio subtitle labels", "#/server/tools/tracks"], ["Server: Setup command", "setup command tower helper terminal", "#/server/tools/setup"],
    ["Server: Apps on Tower", "apps docker restart", "#/server/tools/apps"], ["Server: Chores and restart", "chores restart jellyfin tasks", "#/server/tools/chores"],
    ["Server: Live TV lists", "live tv channels guide", "#/server/tools/livetv"], ["Server: Book lists", "books nyt hardcover keys", "#/server/tools/booklists"],
  ] : []);
  const findHits = (t) => FIND.filter(([label, words]) => t.length > 1 && (label.toLowerCase().includes(t) || words.split(" ").some((w) => w.startsWith(t)))).slice(0, 8);
  $("#setSearch").oninput = (e) => {
    const t = e.target.value.trim().toLowerCase();
    $("#findHits").innerHTML = findHits(t).map(([label, , hash]) => {
      const server = label.startsWith("Server: ");
      return `<a href="${esc(hash)}">${esc(server ? label.slice(8) : label)}<small>${server ? "On the Server page" : "Settings"}</small></a>`;
    }).join("") || (t.length > 1 ? `<div class="dim" style="padding:8px 14px;font-size:14px">Nothing by that name</div>` : "");
  };
  $("#setSearch").onkeydown = (e) => {
    if (e.key !== "Enter") return;
    const hit = findHits(e.target.value.trim().toLowerCase())[0];
    if (hit) B.go(hit[2]); else B.toast("No setting by that name.");
  };
  const ws = B.webSettings();
  const sp = $("#sp");
  if (g.id === "you") {
    sp.innerHTML = `<div class="row" style="gap:18px;margin-bottom:18px">${B.picHtml(B.me, "", 220).replace('class="pic', 'style="width:96px;height:96px;font-size:36px" class="pic')}<div><div style="font-size:24px;font-weight:800">${esc(B.me.name)}</div><div class="dim">${B.me.admin ? "Main profile" : "Profile"}</div></div></div>
      ${setRow("Profile picture", "Pick a picture from this computer. It shows on every TV and phone.", `<label class="valbtn" style="display:inline-block;cursor:pointer">Change<input type="file" accept="image/*" id="picFile" hidden></label>`)}
      ${setRow("Password", "Change the password you sign in with.", `<button class="valbtn" id="pwBtn">Change</button>`)}
      ${B.seaPic ? `<h3 style="margin:22px 0 10px">Pick your sea friend</h3>
      <div class="seapick" id="seaAnimals">${B.SEA_KEYS.map((k, i) => `<button type="button" class="seabtn" data-a="${i}" title="${esc(B.SEA_LABEL[k])}">${B.seaPic(i, 0)}</button>`).join("")}</div>
      <div class="seadots" id="seaColours">${B.SEA_COLOURS.map(([a, b], i) => `<button type="button" class="seadot ${i === 0 ? "on" : ""}" data-c="${i}" style="background:linear-gradient(45deg,${a},${b})" title="Colour ${i + 1}"></button>`).join("")}</div>
      <p class="dim" style="margin:8px 0 0">Pick a colour, then press an animal to save it. It shows on every TV and phone.</p>` : ""}`;
    // Glowfin sea friends (ui-5): pick a colour, then an animal; the picture is drawn here and saved to your account
    let seaColour = 0;
    const seaBox = document.getElementById("seaAnimals");
    if (seaBox) {
      document.getElementById("seaColours").onclick = (e) => {
        const d = e.target.closest("[data-c]"); if (!d) return;
        seaColour = +d.dataset.c;
        sp.querySelectorAll(".seadot").forEach((x) => x.classList.toggle("on", x === d));
        seaBox.querySelectorAll(".seabtn").forEach((b) => { b.innerHTML = B.seaPic(+b.dataset.a, seaColour); });
      };
      seaBox.onclick = (e) => {
        const b = e.target.closest("[data-a]"); if (!b) return;
        const svg = B.seaPic(+b.dataset.a, seaColour).replace("<svg ", '<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" ');
        B.busy(null, async () => {
          const img = new Image();
          img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
          await img.decode();
          const c = document.createElement("canvas"); c.width = 256; c.height = 256;
          c.getContext("2d").drawImage(img, 0, 0, 256, 256);
          const b64 = c.toDataURL("image/png").split(",")[1];
          const res = await fetch(`api/web/jf/Users/${B.uid()}/Images/Primary`, { method: "POST", headers: { "X-Benflix-Token": B.me.token, "X-Benflix-Device": B.me.device, "Content-Type": "image/png" }, body: b64 });
          if (!res.ok) throw new Error("Couldn't save that picture. Try again in a moment.");
          const me = await B.get(`/Users/${B.uid()}`);
          B.me.image = me.PrimaryImageTag; B.addProfile(B.me); B.render();
          return "New picture saved.";
        });
      };
    }
    $("#picFile").onchange = (e) => {
      const f = e.target.files[0]; if (!f) return;
      const r = new FileReader();
      r.onload = () => B.busy(null, async () => {
        // Jellyfin takes the picture as base64 text
        const b64 = String(r.result).split(",")[1];
        const res = await fetch(`api/web/jf/Users/${B.uid()}/Images/Primary`, { method: "POST", headers: { "X-Benflix-Token": B.me.token, "X-Benflix-Device": B.me.device, "Content-Type": f.type || "image/png" }, body: b64 });
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
    // Subtitles are English unless someone picks another language
    const subLangs = langs.slice(1);
    const subLang = cfg.SubtitleLanguagePreference || "eng";
    const label = (list, v) => (list.find((x) => x[0] === v) || list[0])[1];
    sp.innerHTML = setRow("Play the next episode by itself", "When an episode ends, the next one starts after 10 seconds.", valBtn("autoplay", ws.autoplay === false ? "Off" : "On"))
      + setRow("Skip intros and credits by themselves", "Otherwise a Skip button shows. You can also pick this per show in the player.", valBtn("autoskip", ws.autoskip ? "On" : "Off"))
      + setRow("Subtitles", "When subtitles turn on by themselves.", valBtn("subMode", label(subModes, cfg.SubtitleMode || "Default")))
      + setRow("Subtitle language", "", valBtn("subLang", label(subLangs, subLang)))
      + setRow("Audio language", "", valBtn("audioLang", label(langs, cfg.AudioLanguagePreference || "")));
    sp.onclick = (e) => {
      const b = e.target.closest("[data-pick]"); if (!b) return;
      const k = b.dataset.pick;
      const saveCfg = (change) => B.busy(null, async () => { change(cfg); await B.jf("POST", `/Users/Configuration?userId=${B.uid()}`, cfg).catch(() => B.jf("POST", `/Users/${B.uid()}/Configuration`, cfg)); B.render(); });
      if (k === "autoplay") { B.setWebSetting("autoplay", ws.autoplay === false); cfg.EnableNextEpisodeAutoPlay = ws.autoplay === false; saveCfg(() => {}); }
      if (k === "autoskip") { B.setWebSetting("autoskip", !ws.autoskip); B.render(); }
      if (k === "subMode") pickFrom(b, subModes.map(([value, l]) => ({ value, label: l })), cfg.SubtitleMode || "Default", (v) => saveCfg((c) => { c.SubtitleMode = v; }));
      if (k === "subLang") pickFrom(b, subLangs.map(([value, l]) => ({ value, label: l })), subLang, (v) => saveCfg((c) => { c.SubtitleLanguagePreference = v; }));
      if (k === "audioLang") pickFrom(b, langs.map(([value, l]) => ({ value, label: l })), cfg.AudioLanguagePreference || "", (v) => saveCfg((c) => { c.AudioLanguagePreference = v; c.PlayDefaultAudioTrack = !v; }));
    };
  }
  if (g.id === "picture") {
    const q = [["auto", "Best (no limit)"], ["1080", "1080p"], ["720", "720p"], ["480", "480p (slow internet)"]];
    sp.innerHTML = setRow("Quality", "Lower it if videos keep stopping to load.", valBtn("quality", (q.find((x) => x[0] === (ws.quality || "auto")) || q[0])[1]))
      + setRow("Music bars", "The towers of coloured squares that move with the music on Now playing.", valBtn("musicBars", ws.musicBarsOff ? "Off" : "On"));
    sp.onclick = (e) => {
      const b = e.target.closest("[data-pick]"); if (!b) return;
      if (b.dataset.pick === "quality") pickFrom(b, q.map(([value, label]) => ({ value, label })), ws.quality || "auto", (v) => { B.setWebSetting("quality", v); B.render(); });
      if (b.dataset.pick === "musicBars") { B.setWebSetting("musicBarsOff", !ws.musicBarsOff); B.render(); }
    };
  }
  if (g.id === "look") {
    const size = ws.textSize || 100;
    sp.innerHTML = setRow("Text size", "Makes everything in Glowfin bigger or smaller in this browser.", `<div class="stepper"><button data-step="-10">−</button><span>${size}%</span><button data-step="10">+</button></div>`);
    sp.onclick = (e) => {
      const b = e.target.closest("[data-step]"); if (!b) return; const v = Math.max(80, Math.min(150, size + +b.dataset.step)); B.setWebSetting("textSize", v); B.applyLook(); B.render();
    };
  }
  if (g.id === "family") {
    sp.innerHTML = setRow("People and their limits", "Add people, set screens, libraries and passwords.", `<a class="valbtn" href="#/server/people/people">Open</a>`)
      + setRow("What Kids watched", "", `<a class="valbtn" href="#/server/watching/now">Open</a>`);
  }
  if (g.id === "devices") {
    sp.innerHTML = `<p class="dim">On the TV or phone you want to sign in, choose “Sign in with my phone” or Quick Connect. It shows a 6-letter code. Type it here.</p>
      <form id="qcf" class="row"><input class="field" id="qcCode" maxlength="6" placeholder="Code" style="max-width:240px;font-size:32px;letter-spacing:.3em;text-transform:uppercase;font-weight:800;text-align:center"><button class="btn white big">Sign it in</button></form>`;
    $("#qcf").onsubmit = (e) => { e.preventDefault(); const c = $("#qcCode").value.trim().toUpperCase(); if (!c) { $("#qcCode").focus(); return B.toast("Type the 6-letter code from the TV or phone first."); } B.busy(null, async () => { await B.jf("POST", `/QuickConnect/Authorize?code=${enc(c)}&userId=${B.uid()}`); $("#qcCode").value = ""; return "Done. That TV or phone is signing in as " + B.me.name + "."; }); };
  }
  if (g.id === "install") {
    sp.innerHTML = setRow("Install Glowfin on this computer", B.installLine(), `<button class="valbtn" id="installBtn">${B.installed() ? "Installed" : "Install"}</button>`);
    $("#installBtn").onclick = () => B.install();
  }
  if (g.id === "help") sp.innerHTML = setRow("Help and updates", "How to use Glowfin, questions and answers, and what's new.", `<a class="valbtn" href="#/help">Open</a>`);
  if (g.id === "behind") {
    sp.innerHTML = `<div class="warnbox">These are for fixing things. Changing them can make Glowfin act oddly in this browser.</div>
      ${setRow("Get the newest web version now", "Normally Tower checks every hour by itself.", `<a class="valbtn" href="#/server/tools/webversion">Open</a>`)}
      ${setRow("Reset this browser", "Forgets every profile and setting saved in this browser. Nothing on Tower changes.", `<button class="valbtn" id="resetAll">Reset</button>`)}`;
    $("#resetAll").onclick = async () => {
      if (!(await B.ask("Reset Glowfin in this browser?", "Everyone will need to sign in again here.", "Reset"))) return;
      try { Object.keys(localStorage).filter((k) => k.startsWith("benflix")).forEach((k) => localStorage.removeItem(k)); sessionStorage.clear(); } catch (e) {}
      location.hash = "#/signin"; location.reload();
    };
  }
});
// Text size (Settings > Look) zooms the whole page in this browser
B.applyLook = () => { const w = B.me ? B.webSettings() : {}; const s = w.textSize || 100; document.body.style.zoom = s === 100 ? "" : s / 100; B.kidsLook(); };
window.addEventListener("hashchange", () => B.applyLook());
// Kids look (Ben's pick kc-sky, 13 Nov 2026): Sky and Sunshine blue and yellow with round letters while a Kids
// profile is open, and the calmer Night Light colours from 8 PM (an hour before the usual 9 PM bedtime) until 6 AM.
B.kidsLook = () => {
  const kids = !!(B.me && B.me.kids);
  const h = new Date().getHours();
  const root = document.documentElement;
  root.classList.toggle("kids", kids);
  root.classList.toggle("night", kids && (h >= 20 || h < 6));
};
setInterval(() => B.kidsLook(), 60000);

// ---------- Help and updates ----------
const HELP = [
  { id: "updates", label: "Updates", icon: "dl" },
  { id: "new", label: "What's new", icon: "plus" },
  { id: "how", label: "How to use Glowfin", icon: "help" },
  { id: "qa", label: "Questions and answers", icon: "search" },
  { id: "report", label: "Report a problem", icon: "flag" },
  { id: "about", label: "About this app", icon: "info" },
];
const QA = [
  ["Why won't a video play?", "Some files need Tower to convert them for a browser. If one won't start, try lowering Quality in Settings > Picture and sound, or watch it on the TV or phone app."],
  ["Can I use this away from home?", "Yes, once Ben has set it up: open your Glowfin internet address (the yohobox one) with /benflix-games/ at the end and sign in like normal."],
  ["Can I install it like an app?", "Yes. Settings > Install Glowfin. It gets its own window and icon in the dock or Start menu."],
  ["Can I put Glowfin on a Fire TV stick?", "Older Fire TV sticks, Fire TV Cubes and TVs with Fire TV built in work (their About page says Fire OS). The newest sticks run Vega OS, which can't install Glowfin: that's the Fire TV Stick 4K Select (2025), the Fire TV Stick HD (2026) and newer. Check in Settings › My Fire TV › About before you try. Google TV devices always work."],
  ["How do I add someone to Who's watching here?", "Press “Add someone” under the pictures on Who's watching and sign in with their name and password, or with a code from a TV or phone."],
  ["Where are the Glowfin games?", "The Glowfin games (puzzles, party games, Ember Road) are on the TV and phone app. The browser has live channels and console games."],
  ["Do my lists and progress follow me?", "Yes. My List, what you've watched, where you stopped, and your favourite channels are saved on Tower, so they're the same on the TV, phone and here."],
  ["How do I skip intros by themselves?", "Settings > Watching > Skip intros and credits by themselves. Or open the player's Audio and subtitles panel and pick Always skip the intro for that show."],
  ["Keyboard keys in the player?", "Space pauses, the left and right arrows jump 10 seconds, up and down change the sound, F is full screen, M mutes, S turns subtitles on and off, Enter skips the intro, N is the next episode, Esc goes back. Press ? in the player to see them all."],
];
B.route(/^\/help(?:\/(\w+))?/, async (m, nav) => {
  const sec = HELP.find((h) => h.id === m[1]) || HELP[0];
  B.page().innerHTML = B.top("Help and updates", null, { solid: true }) + `<div class="split">
    <nav><div class="splith">Help</div>${HELP.map((h) => `<a href="#/help/${h.id}" class="${h === sec ? "on" : ""}">${esc(h.label)}</a>`).join("")}</nav>
    <main><h2>${esc(sec.label)}</h2><div id="hp" style="margin-top:14px">${B.spinner("list")}</div></main></div>`;
  B.wireTop();
  const hp = $("#hp");
  if (sec.id === "updates") {
    const s = await B.call("GET", "api/web/version").catch(() => ({}));
    hp.innerHTML = `<p class="dim">Glowfin in a browser updates itself: Tower checks for a new version when it starts and then every hour. Reload the page to use the newest one.</p>
      <div class="list"><div class="item"><div class="grow"><div class="t">Last checked</div><div class="s">${s.checked ? esc(B.when(new Date(s.checked * 1000).toISOString())) : "Not yet"}</div></div><div class="acts"><button class="btn small white" onclick="location.reload()">Reload now</button></div></div></div>
      <p class="dim">The TV and phone app update from Settings > Help and updates on each device.</p>`;
  }
  if (sec.id === "new") {
    hp.innerHTML = B.spinner("list");
    try {
      const r = await fetch("https://api.github.com/repos/TheBenPsych/MyStream/releases/tags/latest-build").then((x) => x.json());
      const lines = String(r.body || "").split("\n").map((l) => l.trim()).filter((l) => /^[-*]\s/.test(l)).slice(0, 25).map((l) => l.replace(/^[-*]\s+/, "").replace(/\(#\d+\)/, "").replace(/<!--.*?-->/g, "").trim()).filter(Boolean);
      hp.innerHTML = lines.length ? `<div class="list">${lines.map((l) => `<div class="item"><div class="grow">${esc(l)}</div></div>`).join("")}</div>` : `<p class="dim">No news yet.</p>`;
    } catch (e) { hp.innerHTML = `<p class="dim">Couldn't get the list of changes right now.</p>`; }
  }
  if (sec.id === "how") {
    hp.innerHTML = `<div class="list">
      ${[["Home", "Pick a door: Movies, Shows, Live TV or Games. Your picture in the top corner opens your menu."], ["Rows", "Point at a poster to see what it is at the top. Press it to open its page. Arrows at the sides of a row scroll it."], ["Playing", "Press Play or Resume. Move the mouse to see the controls. Space pauses, arrows jump 10 seconds."], ["Live TV", "Press a channel to watch. Up and down change channel, left opens the guide, typing a number jumps to that channel."], ["Search", "The magnifying glass at the top looks through everything, and shows what you can ask for if it isn't on Glowfin."], ["My List", "Press My List on any movie or show page to save it for later."]].map(([t, s]) => `<div class="item"><div class="grow"><div class="t">${esc(t)}</div><div class="s">${esc(s)}</div></div></div>`).join("")}</div>`;
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
      <div class="item"><div class="grow"><div class="t">Glowfin in a browser</div><div class="s">Runs on Tower's Glowfin helper. Signed in as ${esc(B.me.name)}.</div></div></div>
      <div class="item"><div class="grow"><div class="t">Server</div><div class="s">${esc(info.ServerName || "")} · Jellyfin ${esc(info.Version || "")}</div></div></div>
      <div class="item"><div class="grow"><div class="t">Made from</div><div class="s">Glowfin is built on Wholphin (GPL-2.0) by damontecres. Video in the browser uses hls.js (Apache-2.0). Console games use EmulatorJS (GPL-3.0).</div></div></div></div>`;
  }
});
