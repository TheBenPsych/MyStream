/* Benflix web: the startup intro, signing in, Who's watching, Home with the four doors and the
   profile menu. Follows the approved redesign (setup screens, intro, Home, menu). */
"use strict";

// ---------- sounds (soft mallets: two notes when Benflix opens, four as the doors grow) ----------
B.sound = (name) => {
  if (B.webSettings().sounds === false) return;
  try { const a = new Audio(`web/sounds/${name}.wav`); a.volume = name === "doors" ? 0.55 : 0.45; a.play().catch(() => {}); } catch (e) {}
};

// ---------- intro: logo fades in, holds, then glides up into Who's watching ----------
B.intro = () => new Promise((done) => {
  const lite = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const el = document.createElement("div");
  el.id = "intro";
  el.innerHTML = B.logo();
  document.body.appendChild(el);
  B.sound("opening");
  requestAnimationFrame(() => el.classList.add("on"));
  setTimeout(() => {
    if (!lite) el.classList.add("glide");
    setTimeout(() => { el.classList.add("done"); done(); setTimeout(() => el.remove(), 300); }, lite ? 250 : 750);
  }, 700);
});

// ---------- Who's watching ----------
B.route(/^\/who$/, async () => {
  // Switching profile stops the last person's music and forgets their hearts
  if (B.music) { B.music.stop(); B.music.liked.clear(); }
  B.me = null;
  const list = B.profiles();
  if (!list.length) return B.go("#/signin");
  const last = load("benflix.last");
  const order = list.slice().sort((a, b) => (a.kids - b.kids) || a.name.localeCompare(b.name));
  B.page().innerHTML = `<div class="who fade-in">
    ${B.logo()}
    <h1>Who's watching?</h1>
    <div class="people">${order.map((p) => `<button class="person" data-id="${esc(p.id)}">${B.picHtml(p, "", 260)}<span>${esc(p.name)}</span>${p.expired ? '<span class="gone">Sign in again</span>' : ""}</button>`).join("")}</div>
    <div class="add"><button class="linkbtn" id="addSomeone">Add someone</button></div>
  </div>`;
  const focus = $(`.person[data-id="${CSS.escape(last || "")}"]`) || $(".person");
  if (focus) focus.focus();
  $(".people").onclick = (e) => {
    const b = e.target.closest(".person"); if (!b) return;
    const p = B.profiles().find((x) => x.id === b.dataset.id);
    if (!p) return;
    if (p.expired) return B.go(`#/signin?name=${enc(p.name)}`);
    B.pick(p.id);
    sessionStorage.setItem("benflix.picked", p.id);
    B.doorsGrow = true;
    B.go("#/home");
  };
  $("#addSomeone").onclick = () => B.go("#/signin");
});

// ---------- sign in: one question per screen, then Quick Connect as the second way ----------
B.route(/^\/signin/, async () => {
  B.me = null;
  let name = B.q("name") || "";
  const back = B.profiles().length ? `<button class="linkbtn" id="toWho">Back to Who's watching</button>` : "";
  const shell = (inner) => {
    B.page().innerHTML = `<div class="signin"><div class="card fade-in">${B.logo()}${inner}<div style="margin-top:22px">${back}</div></div></div>`;
    const w = $("#toWho"); if (w) w.onclick = () => B.go("#/who");
  };
  const askName = () => {
    shell(`<h1>Sign in to Benflix</h1><p>Type your name, the one you use for Benflix on the TV.</p>
      <form id="f"><input class="field" id="v" autocomplete="username" placeholder="Your name" value="${esc(name)}">
      <div class="err" id="err"></div>
      <div class="btns"><button class="btn white" type="submit">Next</button><button class="btn" type="button" id="qc">${I("phone")} Sign in with my phone or TV</button></div></form>`);
    $("#v").focus();
    $("#f").onsubmit = (e) => {
      e.preventDefault();
      name = $("#v").value.trim();
      if (!name) { $("#v").classList.add("bad"); $("#err").textContent = "Type your name first."; return; }
      askPassword();
    };
    $("#qc").onclick = quickConnect;
  };
  const askPassword = () => {
    shell(`<h1>Hi ${esc(name)}</h1><p>Now your password. Leave it empty if you don't have one.</p>
      <form id="f"><input type="text" autocomplete="username" value="${esc(name)}" hidden><input class="field" id="v" type="password" autocomplete="current-password" placeholder="Password">
      <div class="err" id="err"></div>
      <div class="btns"><button class="btn white" type="submit" id="go">Sign in</button><button class="btn ghost" type="button" id="bk">Not ${esc(name)}?</button></div></form>`);
    $("#v").focus();
    $("#bk").onclick = askName;
    $("#f").onsubmit = async (e) => {
      e.preventDefault();
      const go = $("#go"); go.disabled = true; go.textContent = "Signing in…"; $("#err").textContent = "";
      const device = B.newDevice();
      try {
        const s = await B.call("POST", "api/web/login", { username: name, password: $("#v").value, device }, { device });
        finish(s, device);
      } catch (err) {
        $("#v").classList.add("bad"); $("#err").textContent = err.message;
        go.disabled = false; go.textContent = "Sign in";
      }
    };
  };
  const quickConnect = async () => {
    const device = B.newDevice();
    shell(`<h1>Sign in with a code</h1><p>On a TV or phone that's already signed in to Benflix, open Settings, then Quick Connect, and type this code.</p><div id="code">${B.spinner()}</div><div class="err" id="err"></div>
      <div class="btns"><button class="btn" id="bk">Type my name instead</button></div>`);
    $("#bk").onclick = () => { stop = true; askName(); };
    let stop = false;
    try {
      const s = await B.call("POST", "api/web/qc/start", {}, { device });
      $("#code").innerHTML = `<div class="code">${String(s.code || "").split("").map((c) => `<b>${esc(c)}</b>`).join("")}</div><p style="text-align:center" class="dim">Waiting for the code…</p>`;
      const tick = async () => {
        if (stop || !$("#code")) return;
        try {
          const c = await B.call("POST", "api/web/qc/check", { secret: s.secret }, { device });
          if (c.ok) return finish(await B.call("POST", "api/web/qc/finish", { secret: s.secret }, { device }), device);
          if (c.gone) { $("#err").textContent = "That code ran out. Press back and try again."; return; }
        } catch (err) { $("#err").textContent = err.message; }
        setTimeout(tick, 3000);
      };
      setTimeout(tick, 3000);
    } catch (err) { $("#code").innerHTML = ""; $("#err").textContent = err.message; }
  };
  const finish = (s, device) => {
    const p = { id: s.id, name: s.name, token: s.token, admin: !!s.admin, kids: !!s.kids, image: s.image || null, device };
    const old = B.profiles().find((x) => x.id === p.id);
    if (old && old.token !== p.token) {
      // Signed in again: the old sign-in isn't needed any more
      B.call("POST", "api/web/jf/Sessions/Logout", undefined, { profile: old, quiet401: true }).catch(() => {});
    }
    B.addProfile(p);
    B.pick(p.id);
    sessionStorage.setItem("benflix.picked", p.id);
    B.doorsGrow = true;
    B.go("#/home");
  };
  askName();
});

// ---------- Home ----------
// Everyone but Kids gets the approved Today board (f3-panel): the greeting with Tower's weather line, six square
// doors 3 by 2, and a soft list panel as tall as the doors (news, On the way, Did you know). Kids keep the big doors.
B.route(/^\/home$/, async (m, nav) => {
  const grow = B.doorsGrow; B.doorsGrow = false;
  const kids = B.me.kids;
  // Benflix music: the violet fifth door (not on Kids); Books: the lime sixth door (Kids too, instead of Music)
  const doors = ["movies", "shows", "live", "games"].concat(kids || !DOORS.music ? [] : ["music"]).concat(DOORS.books ? ["books"] : []).map((k) => DOORS[k]);
  const doorHtml = (d) => `<a class="door" href="#/${d.key}" style="--c:${d.color}">${I(d.icon)}<div><div class="n">${esc(d.name)}</div>${kids ? "" : `<div class="d">${esc(d.desc)}</div>`}</div></a>`;
  const greet = `${esc(B.holiday() || B.greeting())}, ${esc(B.me.name)}`;
  if (kids) {
    B.page().innerHTML = B.top("") + `<div class="home">
      <div class="greet">${greet}</div>
      <div class="doors ${grow ? "grow" : ""} ${doors.length === 5 ? "five" : doors.length === 6 ? "six" : ""}" id="doors">${doors.map(doorHtml).join("")}</div>
    </div>`;
  } else {
    const wx = B.savedWeather();
    B.page().innerHTML = B.top("") + `<div class="home board ${grow ? "grow" : ""}">
      <div class="bgreet"><div class="greet">${greet}</div><div class="wx" id="wx">${esc(wx)}</div></div>
      <div class="bmain"><div class="doors sq3 ${grow ? "grow" : ""}" id="doors">${doors.map(doorHtml).join("")}</div><div class="blist" id="blist"></div></div>
    </div>`;
  }
  B.wireTop();
  if (grow) {
    // The doors grow out of the logo squares, one after another
    const logo = $(".top .logo .sq").getBoundingClientRect();
    $$(".door").forEach((d) => {
      const r = d.getBoundingClientRect();
      d.style.setProperty("--fx", `${logo.left - r.left}px`);
      d.style.setProperty("--fy", `${logo.top - r.top}px`);
    });
    B.sound("doors");
  }
  setTimeout(() => { const f = $(".door"); if (f && B.alive(nav)) f.focus({ preventScroll: true }); }, grow ? 800 : 0);
  if (!kids) B.loadBoard(nav);
});

// The weather Tower gave last time (for today, from the last 3 hours), so the line is there straight away
const dayName = () => new Date().toLocaleDateString([], { weekday: "long" });
B.savedWeather = () => {
  try {
    const w = JSON.parse(localStorage.getItem("benflix.weather") || "null");
    if (w && Date.now() - w.at < 3 * 3600000 && w.line.startsWith(dayName())) return w.line;
  } catch (e) {}
  return "";
};

// Home's news panel (Redesign 2, approved hm-tall): up to 5 lines on the web. Your news newest first, then
// On the way, then one Did you know at the bottom. A quiet day fills up with On the way and Did you know (a second
// fact when there's very little); a busy day keeps the last line for "4 more" and See all (like the TV and phone).
const HOME_SLOTS = 5;
B.arrangeRows = (news, way, facts) => {
  const all = news.concat(way);
  if (all.length > HOME_SLOTS) return { rows: all.slice(0, HOME_SLOTS - 1), more: all.length - (HOME_SLOTS - 1), all };
  const out = all.slice();
  if (out.length < HOME_SLOTS && facts.length) out.push(facts[0]);
  if (out.length < 4 && facts.length > 1) out.push(facts[1]);
  return { rows: out.slice(0, HOME_SLOTS), more: 0, all };
};
// The small coloured line's colours: green ready, amber on the way, teal Did you know, grey everything else
const KIND_READY = "#5BD18B", KIND_WAY = "#FFB547", KIND_FACT = "#3DD6C6", KIND_OTHER = "#9497A3";
// "On the way, about 20 minutes left" becomes "On the way · 20 min left"
const wayKind = (line) => {
  const l = String(line || "").toLowerCase();
  let m;
  const short = l.includes("waiting for a yes") ? "waiting for a yes" : l.includes("looking for a copy") ? "searching" : l.includes("almost here") ? "almost here"
    : l.includes("an hour left") ? "1h left" : (m = l.match(/about (\d+) (minutes|hours) left/)) ? m[1] + (m[2] === "hours" ? "h left" : " min left")
    : (m = l.match(/(\d+)% downloaded/)) ? m[1] + "%" : "";
  return short ? `On the way · ${short}` : "On the way";
};

const wayLine = (r, details) => {
  if (r.status === 1) return "Asked for, waiting for a yes";
  const dl = ((details && details.mediaInfo && details.mediaInfo.downloadStatus) || []);
  if (!dl.length) return "On the way, looking for a copy";
  const ends = dl.map((d) => Date.parse(d.estimatedCompletionTime)).filter((t) => !isNaN(t));
  if (ends.length) {
    const min = Math.round((Math.max(...ends) - Date.now()) / 60000);
    if (min < 2) return "On the way, almost here";
    if (min < 60) return `On the way, about ${min} minutes left`;
    if (min < 120) return "On the way, about an hour left";
    if (min < 48 * 60) return `On the way, about ${Math.floor(min / 60)} hours left`;
  }
  const size = dl.reduce((a, d) => a + (+d.size || 0), 0), left = dl.reduce((a, d) => a + (+d.sizeLeft || 0), 0);
  return size > 0 ? `On the way, ${Math.min(99, Math.max(0, Math.floor((size - left) / size * 100)))}% downloaded` : "On the way, downloading";
};

// Your own requests: ready ones in the last 3 days are news, the rest are On the way
const myRequests = async () => {
  const ready = [], way = [];
  if (!B.seerr || !B.seerrMe) return { ready, way };
  const me = await B.seerrMe();
  if (!me || !me.active || !me.userFound) return { ready, way };
  const list = await B.seerr(`/request?take=20&skip=0&sort=added&requestedBy=${me.jellyseerrUserId}`).catch(() => null);
  const mine = ((list && list.results) || []).filter((r) => r.media && r.media.tmdbId && r.status !== 3);
  await Promise.all(mine.slice(0, 12).map(async (r) => {
    const md = r.media;
    const here = md.status === 5;
    const fresh = Date.now() - Date.parse(md.updatedAt || r.updatedAt || 0) < 3 * 86400000;
    if (here && !(fresh && md.jellyfinMediaId)) return;
    if (!here && !(r.status === 1 || r.status === 2)) return;
    const d = await B.cached(`seerr-${r.type}-${md.tmdbId}-home`, 300000, () => B.seerr(`/${r.type}/${md.tmdbId}`)).catch(() => null);
    if (!d) return;
    const title = d.title || d.name || "";
    const img = B.tmdbImg ? B.tmdbImg(d.posterPath, "w185") : "";
    if (here) ready.push({ id: `req-${md.jellyfinMediaId}`, news: true, kind: "Ready to watch", title: `${title} · you asked`, name: title, kc: KIND_READY, img: B.img(md.jellyfinMediaId, "Primary", { h: 240 }) || img, href: `#/${r.type === "tv" ? "show" : "movie"}/${md.jellyfinMediaId}`, at: r.createdAt, t: Date.parse(md.updatedAt || r.updatedAt || 0) || 0 });
    else way.push({ id: `way-${r.id}`, kind: wayKind(wayLine(r, d)), title, kc: KIND_WAY, img, href: "#/requests", at: r.createdAt });
  }));
  const newest = (a, b) => String(b.at).localeCompare(String(a.at));
  return { ready: ready.sort(newest).slice(0, 2), way: way.sort(newest).slice(0, 3) };
};

let lastFacts = "";
// A note from Ben that talks about one part of Benflix gets a button in its pop-up to go there
const noteLink = (text) => {
  const t = text.toLowerCase();
  if (/live ?tv|\bchannels?\b/.test(t)) return ["Open Live TV", "#/live"];
  if (/\bmusic\b|\bsongs?\b|\balbums?\b/.test(t) && DOORS.music && !B.me.kids) return ["Open Music", "#/music"];
  if (/\bbooks?\b|\bmanga\b|\baudiobooks?\b/.test(t) && DOORS.books) return ["Open Books", "#/books"];
  if (/\bgames?\b/.test(t)) return ["Open Games", "#/games"];
  if (/\brequests?\b/.test(t)) return ["Open Requests", "#/requests"];
  if (/\bshows?\b|\bepisodes?\b|\bseries\b/.test(t)) return ["Open Shows", "#/shows"];
  if (/\bmovies?\b|\bfilms?\b/.test(t)) return ["Open Movies", "#/movies"];
  return null;
};
// Rows this profile cleared (the X, Clear all, or opening news). Saved with the profile on the server
// (benflix.cleared, shared with the TV and phone app), so every screen hides the same rows. Facts are only
// hidden until Home opens again, because they change every time anyway.
let clearedNow = new Set();
const CLEARED = "benflix.cleared";
const saveCleared = (ids) => {
  const keep = ids.filter((id) => id && !id.startsWith("fact-"));
  ids.forEach((id) => clearedNow.add(id));
  if (!keep.length) return;
  B.savePrefs((c) => {
    const old = String(c[CLEARED] || "").split("\n").filter((x) => x && !keep.includes(x));
    c[CLEARED] = old.concat(keep).slice(-80).join("\n");
  }).catch(() => {});
};
B.loadBoard = async (nav) => {
  clearedNow = new Set();
  const news = [];
  const add = (row) => news.push(row);
  const [prefs, nextUp, music, books, reqs, facts, weather] = await Promise.all([
    B.prefs().catch(() => null),
    B.get(`/Shows/NextUp?userId=${B.uid()}&limit=24&fields=DateCreated&enableResumable=false&enableRewatching=false&disableFirstEpisode=true`).catch(() => null),
    (async () => {
      const f = await B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=MusicArtist&Recursive=true&Filters=IsFavorite&Limit=200&EnableImages=false`);
      const ids = (f.Items || []).map((x) => x.Id);
      return ids.length ? B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=MusicAlbum&Recursive=true&AlbumArtistIds=${ids.join(",")}&SortBy=DateCreated&SortOrder=Descending&Fields=DateCreated&Limit=10&EnableImages=false`) : null;
    })().catch(() => null),
    DOORS.books ? B.call("GET", "api/books/news").catch(() => null) : null,
    myRequests().catch(() => ({ ready: [], way: [] })),
    B.call("GET", `api/facts/home?n=2&max=130&skip=${lastFacts}`).catch(() => null),
    B.call("GET", "api/home/weather").catch(() => null),
  ]);
  if (!B.alive(nav) || !$("#blist")) return;
  // Weather: Tower's line with today's name in front (nothing when no town is set on the Server page)
  if (weather) {
    const line = weather.ready ? `${dayName()} · ${weather.line}` : "";
    try { localStorage.setItem("benflix.weather", JSON.stringify({ line, at: Date.now() })); } catch (e) {}
    $("#wx").textContent = line;
  }
  // News: a note from the server owner, a fix, new episodes, new music, new books, requests that are ready
  const c = (prefs && prefs.CustomPrefs) || {};
  const cleared = new Set(String(c[CLEARED] || "").split("\n").filter(Boolean));
  try {
    const note = String(c["benflix.note"] || "");
    const i = note.indexOf("\n");
    if (i > 0) {
      const head = note.slice(0, i), exp = +(head.split("~")[1] || 0);
      // The tab is called Live TV now, so old notes that say "under Channels" read right
      const where = /\s*\b(under|in|on|to) (the )?Channels\b( (tab|page|door|section))?/gi;
      let text = note.slice(i + 1);
      text = /live ?tv/i.test(text.replace(where, "")) ? text.replace(where, "") : text.replace(where, " $1 Live TV");
      if (text.trim() && (!exp || exp > Date.now())) add({ id: `note-${head.split("~")[0]}`, news: true, kind: "A note from Ben", title: text.split("\n")[0], full: text, go: noteLink(text), kc: KIND_OTHER, href: "", t: Infinity });
    }
    String(c["benflix.fixed"] || "").split("\n").filter(Boolean).slice(-1).forEach((l) => {
      const [ts, title] = l.split("|");
      if (Date.now() - +ts < 3 * 86400000) add({ id: `fixed-${ts}`, news: true, kind: "Ben fixed it", title, full: `Ben fixed ${title}. Try it again!`, kc: KIND_READY, href: "", t: +ts });
    });
  } catch (e) {}
  const seen = new Set();
  ((nextUp && nextUp.Items) || []).filter((e) => B.isNew(e) && Date.now() - new Date(e.DateCreated) < 3 * 86400000).forEach((e) => {
    if (seen.has(e.SeriesId)) return;
    seen.add(e.SeriesId);
    const newSeason = e.IndexNumber === 1 && (e.ParentIndexNumber || 1) > 1;
    // The episode number sits in the small line, so the name gets the whole width
    add({ id: `ep-${e.Id}`, news: true, kind: newSeason ? "New season" : e.IndexNumber != null ? `New episode · S${e.ParentIndexNumber} E${e.IndexNumber}` : "New episode",
      title: newSeason ? `${e.SeriesName} · Season ${e.ParentIndexNumber}` : e.SeriesName,
      kc: KIND_OTHER, img: B.img(e.SeriesId, "Primary", { h: 240 }), href: `#/show/${e.SeriesId}?ep=${e.Id}`, t: Date.parse(e.DateCreated) || 0 });
  });
  const who = new Set();
  ((music && music.Items) || []).filter((a) => Date.now() - new Date(a.DateCreated) < 3 * 86400000).forEach((a) => {
    const name = a.AlbumArtist || a.Name;
    if (who.has(name) || who.size >= 2) return;
    who.add(name);
    add({ id: `music-${a.Id}`, news: true, kind: "New music", title: `${name} · ${a.Name}`, kc: KIND_OTHER, img: B.img(a.Id, "Primary", { h: 160 }), square: true, href: `#/album/${a.Id}`, t: Date.parse(a.DateCreated) || 0 });
  });
  const pic = (u) => (u ? u.replace(/^\//, "") + (u.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token) : "");
  ((books && books.news) || []).slice(0, 2).forEach((b) => {
    const name = b.name || b.text, id = String(b.id || "");
    const vol = String(b.line || "").replace(/^New: /, "").replace(/ is here$/, "");
    const [kind, title] = id.startsWith("bkr-") ? ["Ready to read", name] : id.startsWith("bks-") ? ["On your phone", name] : ["New volume", vol && vol !== b.line ? `${name} · ${vol}` : name];
    add({ id: `book-${b.id}`, news: true, kind, title, kc: id.startsWith("bkr-") ? KIND_READY : KIND_OTHER, img: pic(b.cover), href: `#/book/${enc(b.title)}`, t: (+b.at || 0) * 1000 });
  });
  reqs.ready.forEach(add);
  // Did you know: about a movie or show this person can watch (Tower picks them)
  const factRows = ((facts && facts.facts) || []).map((f) => {
    let text = f.fact;
    if (f.name && !text.toLowerCase().includes(f.name.split(":")[0].trim().toLowerCase())) text = `${f.name}: ${text}`;
    const show = f.type === "Series";
    return { kind: "Did you know", title: text, kc: KIND_FACT, img: B.img(f.id, "Primary", { h: 240 }), href: `#/${show ? "show" : "movie"}/${f.id}`, id: `fact-${f.id}`, fid: f.id };
  });
  lastFacts = factRows.map((f) => f.fid).join(",");
  const keep = (r) => !cleared.has(r.id) && !clearedNow.has(r.id);
  boardParts = { news: news.filter(keep).sort((a, b) => (b.t || 0) - (a.t || 0)).slice(0, 12), way: reqs.way.filter(keep), facts: factRows.filter(keep) };
  drawBoard();
  homeNotices(c, reqs.ready);
};

// Notices on Home: a corner card for each request that's newly ready (once per browser), and a message
// from Ben (saved for the next time Benflix opens) in the Needs-you card, which waits for Got it.
const homeNotices = async (c, ready) => {
  let carded = [];
  try { carded = JSON.parse(localStorage.getItem("benflix.carded") || "[]"); } catch (e) {}
  const fresh = ready.filter((r) => !carded.includes(r.id));
  // The very first time, only remember what's already here (no pile of old cards)
  if (localStorage.getItem("benflix.carded") !== null) fresh.slice().reverse().forEach((r) => B.corner({ id: r.id, title: `${r.name} is ready`, line: "You asked for it", img: r.img, href: r.href }));
  try { localStorage.setItem("benflix.carded", JSON.stringify(carded.concat(fresh.map((r) => r.id)).slice(-60))); } catch (e) {}
  const saved = String(c["benflix.messages"] || "").split("\u001E").map((r) => r.split("\u001F")).filter((f) => f.length >= 3 && f[1] !== "Message from " + B.me.name);
  if (!saved.length) return;
  // Take them off the profile first, so the TV or phone doesn't show the same one again
  await B.savePrefs((p) => {
    p["benflix.messages"] = String(p["benflix.messages"] || "").split("\u001E").filter((r) => !saved.some((f) => r === f.join("\u001F"))).join("\u001E");
  }).catch(() => {});
  for (const f of saved) {
    if (!$("#blist")) return;
    await B.needsYou(f[1] || "Message from Ben", f.slice(2).join("\u001F"), "");
  }
};

// Every row can be pressed (a row with nowhere to go opens a pop-up with all its words), and the X beside it
// clears it. Clear all (top right of the panel) clears the whole list. New news still shows when it comes.
let boardParts = { news: [], way: [], facts: [] };
const rowHtml = (r, n) => {
  const logo = `<span class="lgthumb"><i></i><i></i><i></i><i></i></span>`;
  return `<div class="bitem">
    <${r.href ? `a href="${esc(r.href)}"` : `button type="button"`} class="brow" data-row="${n}">
    ${r.img ? `<span class="th" style="background-image:url('${esc(r.img)}')"></span>` : logo}
    <div class="tx"><small style="color:${r.kc}">${esc(r.kind)}</small><b><i>${esc(r.title)}</i></b></div></${r.href ? "a" : "button"}>
    <button type="button" class="bx" data-clear="${n}" title="Clear" aria-label="Clear ${esc(r.title)}">${I("x")}</button></div>`;
};
const live = (list) => list.filter((r) => !clearedNow.has(r.id));
const drawBoard = () => {
  const list = $("#blist");
  if (!list) return;
  const shown = B.arrangeRows(live(boardParts.news), live(boardParts.way), live(boardParts.facts));
  const rows = shown.rows;
  list.innerHTML = rows.length ? `<div class="bpanel"><div class="bhead"><button type="button" class="bclear">Clear all</button></div>
    ${rows.map(rowHtml).join("")}
    ${shown.more ? `<div class="bmore"><span>${shown.more} more</span><button type="button" class="bsee">See all ›</button></div>` : ""}</div>` : "";
  wireRows(list, rows, () => drawBoard());
  const see = $(".bsee", list);
  if (see) see.onclick = () => seeAll();
  const all = $(".bclear", list);
  if (all) all.onclick = () => {
    saveCleared(boardParts.news.concat(boardParts.way, boardParts.facts).map((r) => r.id));
    drawBoard();
    const d = $(".door"); if (d) d.focus({ preventScroll: true });
  };
  fitRows(list);
  slideNames(list);
};
// Pressing a row, its X, or a row with nowhere to go (a pop-up with all its words)
const wireRows = (box, rows, redraw) => {
  box.onclick = async (e) => {
    const x = e.target.closest("[data-clear]");
    if (x) {
      e.preventDefault();
      const at = +x.dataset.clear;
      saveCleared([rows[at].id]);
      redraw();
      // Keep the keyboard on the list: the next row's X, or the last one, or the first door
      const xs = $$(".bx", box);
      const next = xs[at] || xs[xs.length - 1] || $(".door");
      if (next) next.focus({ preventScroll: true });
      return;
    }
    const b = e.target.closest("[data-row]");
    if (!b) return;
    const r = rows[+b.dataset.row];
    if (r.href) {
      // News goes away once it's been opened (like the TV and phone app)
      if (r.news) saveCleared([r.id]);
      return;
    }
    // A note about one part of Benflix: go there (and clear the note); otherwise just read it and clear it
    const yes = await B.ask(r.kind, r.full || r.title, r.go ? r.go[0] : "Got it, clear it", "Keep it");
    if (!yes) return;
    saveCleared([r.id]);
    if (r.go) B.go(r.go[1]);
    else drawBoard();
  };
};
// See all: everything new in the side panel (a sheet from the bottom on phones), with Clear all
const seeAll = () => {
  const rows = live(boardParts.news).concat(live(boardParts.way));
  const panel = B.side(`<h3>Everything new</h3><div class="seeall"><div class="bhead"><button type="button" class="bclear">Clear all</button></div><div class="sarows">${rows.map(rowHtml).join("")}</div></div>`, () => { const s = $(".bsee"); if (s) s.focus(); });
  const redraw = () => {
    const left = live(boardParts.news).concat(live(boardParts.way));
    drawBoard();
    if (!left.length) { B.closeAll(); return; }
    rows.splice(0, rows.length, ...left);
    $(".sarows", panel).innerHTML = rows.map(rowHtml).join("");
  };
  wireRows(panel, rows, redraw);
  $(".bclear", panel).onclick = () => {
    saveCleared(rows.map((r) => r.id));
    B.closeAll();
    drawBoard();
    const d = $(".door"); if (d) d.focus({ preventScroll: true });
  };
};
// A name too long for 2 lines slides by when the row is picked (not when the computer asks for less motion)
const slideNames = (box) => {
  if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  $$(".brow", box).forEach((row) => {
    const b = $(".tx b", row);
    if (!b) return;
    row.classList.toggle("long", b.scrollHeight > b.clientHeight + 2);
  });
};

// Every row's words fit in full: if any row would overflow, all of them get a little smaller together
const fitRows = (list) => {
  const panel = $(".bpanel", list);
  if (!panel || matchMedia("(max-width: 900px)").matches) return;
  let s = 1;
  panel.style.setProperty("--fs", s);
  const over = () => $$(".brow .tx", panel).some((t) => t.scrollHeight > t.parentElement.clientHeight - 2);
  while (s > 0.62 && over()) { s -= 0.04; panel.style.setProperty("--fs", s.toFixed(2)); }
};
addEventListener("resize", () => { const l = $("#blist"); if (l) fitRows(l); });

// Big holidays only, clean and simple
B.holiday = () => {
  const d = new Date(), md = (d.getMonth() + 1) * 100 + d.getDate();
  if (md === 1225) return "Merry Christmas";
  if (md === 101) return "Happy New Year";
  if (md === 1031) return "Happy Halloween";
  if (md === 704) return "Happy 4th of July";
  return "";
};

// ---------- profile menu: corner card, picture + name on the left, list on the right ----------
B.openMenu = async () => {
  if (!B.me) return;
  if (B.me.kids) return B.go("#/who");
  B.closeAll();
  B.shade();
  const c = document.createElement("div");
  c.className = "menu-card";
  const items = [
    ["#/requests", "plus", "Requests"],
    ["#/mine", "chart", "My watching"],
    ["#/settings", "gear", "Settings"],
    B.me.admin ? ["#/server", "server", "Server"] : null,
    ["#/help", "help", "Help and updates"],
    ["report", "flag", "Report a problem"],
  ].filter(Boolean);
  c.innerHTML = `<div class="left">${B.picHtml(B.me, "", 220)}<div><div class="nm">${esc(B.me.name)}</div><div class="wt" id="wt">&nbsp;</div></div><button class="btn small" data-go="#/who">${I("swap")} Switch profile</button></div>
    <div class="list">${items.map(([href, icon, label]) => `<button data-go="${href}">${I(icon)}${esc(label)}${label === "Server" ? '<span class="badge hidden" id="srvBadge"></span>' : ""}</button>`).join("")}
      <div class="sep"></div><button class="out" data-go="signout">${I("out")}Sign out</button></div>`;
  document.body.appendChild(c);
  $("button[data-go]", c).focus();
  c.onclick = async (e) => {
    const b = e.target.closest("[data-go]"); if (!b) return;
    const where = b.dataset.go;
    B.closeAll();
    if (where === "signout") return B.signOut();
    if (where === "report") return B.report();
    B.go(where);
  };
  B.watchTime().then((w) => { const el = $("#wt"); if (el) el.textContent = `${B.hm(w.month)} this month`; }).catch(() => {});
};

B.signOut = async () => {
  if (!(await B.ask(`Sign ${B.me.name} out of Benflix in this browser?`, "Your lists and settings stay safe on the server. Sign back in any time.", "Sign out"))) return;
  const me = B.me;
  try { await B.jf("POST", "/Sessions/Logout", undefined, { quiet401: true }); } catch (e) {}
  B.forget(me.id);
  B.me = null;
  sessionStorage.removeItem("benflix.picked");
  B.go("#/who");
};

// Report a problem: pick what's wrong; it lands on the Server page's Problems list
B.report = (item) => {
  const kinds = ["No sound", "Keeps stopping to load", "Subtitles are wrong", "Will not play", "Something else"];
  const what = item || B.lastPlayed || null;
  const panel = B.side(`<h3>Report a problem</h3><p class="dim">Pick what's wrong. ${what ? `It's about <b>${esc(what.title)}</b>.` : ""} Ben gets it on the Server page.</p>
    ${kinds.map((k) => `<button class="opt" data-k="${esc(k)}">${esc(k)}</button>`).join("")}`);
  panel.onclick = async (e) => {
    const b = e.target.closest("[data-k]"); if (!b) return;
    let problem = b.dataset.k;
    if (problem === "Something else") {
      const t = await B.askText("What's wrong?", "A few words is fine.", "", { long: true, ok: "Send" });
      if (!t || !t.trim()) return;
      problem = t.trim();
    }
    B.closeAll();
    B.busy(null, async () => {
      const line = [Date.now(), what ? what.id : "", (what ? what.title : "Benflix web").replace(/[|\n]/g, " "), problem.replace(/[|\n]/g, " "), "Web browser"].join("|");
      await B.savePrefs((c) => {
        const lines = String(c["benflix.problems"] || "").split("\n").filter(Boolean);
        lines.push(line);
        c["benflix.problems"] = lines.slice(-20).join("\n");
      });
      return "Sent. Thanks for telling Ben.";
    });
  };
};

// ---------- start ----------
B.start = async () => {
  const picked = sessionStorage.getItem("benflix.picked");
  if (picked && B.profiles().some((p) => p.id === picked && !p.expired)) {
    B.pick(picked);
    return B.render();
  }
  if (!location.hash || location.hash === "#/" || location.hash === "#/who") {
    if (B.profiles().length) {
      history.replaceState(null, "", "#/who");
      await B.intro();
    }
  }
  B.render();
};
document.addEventListener("DOMContentLoaded", B.start);
