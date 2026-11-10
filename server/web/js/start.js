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

// Home's list: up to 4 rows. News first, then your requests On the way, then Did you know (new every time).
// One spot is kept for the fact and one for On the way; rows come in twos, so a 3rd fact-less row gets a 2nd fact.
B.arrangeRows = (news, way, facts) => {
  const out = news.slice(0, 4 - (facts.length ? 1 : 0) - (way.length ? 1 : 0));
  out.push(...way.slice(0, Math.max(0, 4 - (facts.length ? 1 : 0) - out.length)));
  out.push(...facts.slice(0, 1));
  if (out.length % 2 === 1 && facts.length > 1) out.push(facts[1]);
  return out.slice(0, 4);
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
    if (here) ready.push({ title, detail: "Your request is ready to watch", dot: "#F1F2F5", img: B.img(md.jellyfinMediaId, "Primary", { h: 240 }) || img, href: `#/${r.type === "tv" ? "show" : "movie"}/${md.jellyfinMediaId}`, at: r.createdAt });
    else way.push({ title, detail: wayLine(r, d), dot: "#F1F2F5", img, href: "#/requests", at: r.createdAt });
  }));
  const newest = (a, b) => String(b.at).localeCompare(String(a.at));
  return { ready: ready.sort(newest).slice(0, 2), way: way.sort(newest).slice(0, 3) };
};

let lastFacts = "";
B.loadBoard = async (nav) => {
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
  try {
    const c = (prefs && prefs.CustomPrefs) || {};
    const note = String(c["benflix.note"] || "");
    const i = note.indexOf("\n");
    if (i > 0) {
      const exp = +(note.slice(0, i).split("~")[1] || 0);
      if (!exp || exp > Date.now()) add({ title: "A note from Ben", detail: note.slice(i + 1).split("\n")[0], dot: "#F1F2F5", href: "" });
    }
    String(c["benflix.fixed"] || "").split("\n").filter(Boolean).slice(-1).forEach((l) => {
      const [ts, title] = l.split("|");
      if (Date.now() - +ts < 3 * 86400000) add({ title, detail: "Ben fixed it, it's ready to watch again", dot: "#F1F2F5", href: "" });
    });
  } catch (e) {}
  const seen = new Set();
  ((nextUp && nextUp.Items) || []).filter((e) => B.isNew(e) && Date.now() - new Date(e.DateCreated) < 3 * 86400000).forEach((e) => {
    if (seen.has(e.SeriesId)) return;
    seen.add(e.SeriesId);
    const newSeason = e.IndexNumber === 1 && (e.ParentIndexNumber || 1) > 1;
    add({ title: e.SeriesName, detail: newSeason ? `Season ${e.ParentIndexNumber} is here` : `New episode: season ${e.ParentIndexNumber}, episode ${e.IndexNumber}`,
      dot: DOORS.shows.color, img: B.img(e.SeriesId, "Primary", { h: 240 }), href: `#/show/${e.SeriesId}?ep=${e.Id}` });
  });
  const who = new Set();
  ((music && music.Items) || []).filter((a) => Date.now() - new Date(a.DateCreated) < 3 * 86400000).forEach((a) => {
    const name = a.AlbumArtist || a.Name;
    if (who.has(name) || who.size >= 2) return;
    who.add(name);
    add({ title: name, detail: `New music: ${a.Name}`, dot: "#B69CFF", img: B.img(a.Id, "Primary", { h: 160 }), square: true, href: `#/album/${a.Id}` });
  });
  const pic = (u) => (u ? u.replace(/^\//, "") + (u.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token) : "");
  ((books && books.news) || []).slice(0, 2).forEach((b) => add({ title: b.name || b.text, detail: b.line || "", dot: DOORS.books.color, img: pic(b.cover), href: `#/book/${enc(b.title)}` }));
  reqs.ready.forEach(add);
  // Did you know: about a movie or show this person can watch (Tower picks them)
  const factRows = ((facts && facts.facts) || []).map((f) => {
    let text = f.fact;
    if (f.name && !text.toLowerCase().includes(f.name.split(":")[0].trim().toLowerCase())) text = `${f.name}: ${text}`;
    const show = f.type === "Series";
    return { title: "Did you know?", detail: text, dot: show ? DOORS.shows.color : DOORS.movies.color, img: B.img(f.id, "Primary", { h: 240 }), href: `#/${show ? "show" : "movie"}/${f.id}`, id: f.id };
  });
  lastFacts = factRows.map((f) => f.id).join(",");
  const rows = B.arrangeRows(news.slice(0, 3), reqs.way, factRows);
  const list = $("#blist");
  list.classList.toggle("quiet", rows.length <= 2);
  list.style.setProperty("--n", rows.length <= 2 ? 2 : rows.length);
  const logo = `<span class="lgthumb"><i></i><i></i><i></i><i></i></span>`;
  list.innerHTML = rows.length ? `<div class="bpanel">${rows.map((r) => `<${r.href ? `a href="${esc(r.href)}"` : "div"} class="brow">
    <i class="dot" style="background:${r.dot}"></i><div class="tx"><b>${esc(r.title)}</b>${r.detail ? `<span>${esc(r.detail)}</span>` : ""}</div>
    ${r.img ? `<span class="th ${r.square ? "sq" : ""}" style="background-image:url('${esc(r.img)}')"></span>` : logo}</${r.href ? "a" : "div"}>`).join("")}</div>` : "";
  fitRows(list);
};

// Every row's words fit in full: if any row would overflow, all of them get a little smaller together
const fitRows = (list) => {
  const panel = $(".bpanel", list);
  if (!panel || matchMedia("(max-width: 900px)").matches) return;
  let s = 1;
  panel.style.setProperty("--fs", s);
  const over = () => $$(".brow .tx", panel).some((t) => t.scrollHeight > t.parentElement.clientHeight - 4);
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
