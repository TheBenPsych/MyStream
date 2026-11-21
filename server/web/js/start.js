/* Benflix web: the startup intro, signing in, Who's watching, Home with the four doors and the
   profile menu. Follows the approved redesign (setup screens, intro, Home, menu). */
"use strict";

// ---------- intro: logo fades in, holds, then glides up into Who's watching ----------
B.intro = () => new Promise((done) => {
  const lite = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const el = document.createElement("div");
  el.id = "intro";
  el.innerHTML = B.logo();
  document.body.appendChild(el);
  requestAnimationFrame(() => el.classList.add("on"));
  setTimeout(() => {
    if (!lite) el.classList.add("glide");
    setTimeout(() => { el.classList.add("done"); done(); setTimeout(() => el.remove(), 300); }, lite ? 250 : 750);
  }, 700);
});

// ---------- Who's watching ----------
// Redesign 2 (su-family): the night sea behind, the grown-ups as big round pictures in one row,
// and Kids profiles a little smaller at the end with the names lined up.
B.route(/^\/who$/, async () => {
  // Switching profile stops the last person's music and forgets their hearts
  if (B.music) { B.music.stop(); B.music.liked.clear(); }
  B.me = null;
  const list = B.profiles();
  if (!list.length) return B.go("#/signin");
  const last = load("benflix.last");
  const order = list.slice().sort((a, b) => (!!a.kids - !!b.kids) || a.name.localeCompare(b.name));
  B.page().innerHTML = `<div class="who fade-in">
    ${B.nightSea()}
    ${B.logo()}
    <h1>Who's watching?</h1>
    <div class="people">${order.map((p) => `<button class="person${p.kids ? " small" : ""}" data-id="${esc(p.id)}">${B.picHtml(p, "", 260)}<span>${esc(p.name)}</span>${p.expired ? '<span class="gone">Sign in again</span>' : ""}</button>`).join("")}</div>
    <p class="hint">Change your picture any time in Settings › You</p>
    <div class="add"><button class="linkbtn" id="addSomeone">Add someone</button></div>
  </div>`;
  const focus = $(`.person[data-id="${CSS.escape(last || "")}"]`) || $(".person");
  if (focus) focus.focus();
  $(".people").onclick = (e) => {
    const b = e.target.closest(".person"); if (!b) return;
    const p = B.profiles().find((x) => x.id === b.dataset.id);
    if (!p) return;
    if (p.expired) return B.go(`#/signin?name=${enc(p.name)}`);
    const open = () => {
      B.pick(p.id);
      sessionStorage.setItem("benflix.picked", p.id);
      B.doorsGrow = true;
      B.go("#/home");
    };
    open();
  };
  $("#addSomeone").onclick = () => B.go("#/signin");
});

// Ben 2026-11-18: the still "night sea" behind Who's watching and sign-in (same as the TV and phone app).
// Night sky + faint stars, violet glow on the horizon, calm water, and the Glowfin fin with its mirror
// image in the water. No animations and no lines on the water (Ben). Nothing to load from the Tower.
B.nightSea = () => {
  const fin = "M17 64 C29 45 46 24 79 13 C72 31 70 48 81 64 Z";
  return `<div class="nsea" aria-hidden="true"><div class="sky"></div><div class="haze"></div>
    <div class="water"><div class="pool"></div><div class="line"></div></div>
    <svg class="far" viewBox="8 8 80 104"><defs>
      <linearGradient id="nsG" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#a55be0"/><stop offset=".6" stop-color="#1fa8e6"/><stop offset="1" stop-color="#3fe0e8"/></linearGradient>
      <linearGradient id="nsF" gradientUnits="userSpaceOnUse" x1="0" y1="64" x2="0" y2="108"><stop offset="0" stop-color="#fff" stop-opacity=".75"/><stop offset=".45" stop-color="#fff" stop-opacity=".3"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
      <mask id="nsM" maskUnits="userSpaceOnUse" x="0" y="64" width="100" height="50"><rect x="0" y="64" width="100" height="50" fill="url(#nsF)"/></mask>
      <radialGradient id="nsR"><stop offset="0" stop-color="#1fa8e6" stop-opacity=".45"/><stop offset="1" stop-color="#1fa8e6" stop-opacity="0"/></radialGradient></defs>
      <ellipse cx="50" cy="64" rx="48" ry="9" fill="url(#nsR)"/>
      <g mask="url(#nsM)"><path d="${fin}" transform="matrix(1 0 0 -0.85 0 118.4)" fill="url(#nsG)"/></g>
      <path d="${fin}" fill="url(#nsG)"/></svg></div>`;
};

// ---------- sign in ----------
// Redesign 2 (su-s-quick): on a wide screen the left half is "Sign in with your phone" (a Quick Connect
// code and the steps) and the right half is "Or type it in". Narrow screens get just the form, with a
// line about signing in with a code. When it doesn't work, plain words and one button.
B.signInProblem = (err, name) => {
  const st = err && err.status, msg = String((err && err.message) || "");
  const who = String(name || "").trim();
  if (st === 0 || /couldn't reach|is it on/i.test(msg)) return { kind: "reach", title: "Can't reach the Tower (is it on?)", line: "Check the Tower is switched on, then try again in a minute.", button: "Try again" };
  if (st === 403 || /too many|limit how many devices/i.test(msg)) {
    const whose = who ? `${who}'s account` : "This account";
    return { kind: "many", title: "On too many TVs", line: `${whose} is on too many TVs. Sign out on one${/^ben$/i.test(who) ? "" : ", or ask Ben"}.`, button: "OK" };
  }
  if (st === 401 || /accept that name and password|wrong password/i.test(msg)) return { kind: "wrong", title: "Wrong password", line: "Check the name and password and try again. Capital letters count.", button: "Type it again" };
  if (st === 400 && /name/i.test(msg)) return { kind: "wrong", title: "Type your name first", line: "Your name is the one you use for Glowfin on the TV.", button: "OK" };
  return { kind: "other", title: "That didn't work", line: msg || "Something went wrong signing in. Try again in a minute.", button: "Try again" };
};
B.route(/^\/signin/, async () => {
  B.me = null;
  const name0 = B.q("name") || "";
  const back = B.profiles().length ? `<button class="linkbtn" id="toWho">Back to Who's watching</button>` : "";
  const wide = () => matchMedia("(min-width: 820px)").matches;
  let gen = 0, codeOnPhone = false; // gen: each new code wait gets a number, so old waits stop
  const draw = () => {
    gen++;
    const form = `<form id="f" class="half type">
        <h2>${wide() ? "Or type it in" : "Sign in to Glowfin"}</h2>
        <label>Name<input class="field" id="nm" autocomplete="username" value="${esc(name0)}"></label>
        <label>Password<input class="field" id="pw" type="password" autocomplete="current-password" placeholder="Leave it empty if you don't have one"></label>
        <div id="prob"></div>
        <div class="btns"><button class="btn white" type="submit" id="go">Sign in</button></div>
        ${wide() ? "" : `<p class="dim small">Already signed in on a TV or phone? <button class="linkbtn" type="button" id="useCode">Sign in with a code instead</button></p>`}
      </form>`;
    const phone = `<div class="half phone">
        <h2>Sign in with your phone</h2>
        <p>On your phone, open Glowfin, then My Glowfin › Quick Connect, and type this code:</p>
        <div id="code">${B.spinner()}</div>
        <div id="qcProb"></div>
        ${wide() ? "" : `<button class="btn" type="button" id="useForm">Type it in instead</button>`}
      </div>`;
    const inner = wide() ? `${phone}<div class="divider"></div>${form}` : (codeOnPhone ? phone : form);
    B.page().innerHTML = `<div class="signin">${B.nightSea()}<div class="card ${wide() ? "two" : ""} fade-in">${B.logo()}<div class="halves">${inner}</div><div style="margin-top:22px">${back}</div></div></div>`;
    const w = $("#toWho"); if (w) w.onclick = () => { gen++; B.go("#/who"); };
    const uc = $("#useCode"); if (uc) uc.onclick = () => { codeOnPhone = true; draw(); };
    const uf = $("#useForm"); if (uf) uf.onclick = () => { codeOnPhone = false; draw(); };
    if ($("#f")) wireForm();
    if ($("#code")) quickConnect();
  };
  const showProblem = (box, p, onButton) => {
    box.innerHTML = `<div class="problem"><b>${esc(p.title)}</b><p>${esc(p.line)}</p><button class="btn white" type="button">${esc(p.button)}</button></div>`;
    const b = box.querySelector("button"); b.focus();
    b.onclick = () => { box.innerHTML = ""; onButton(); };
  };
  const wireForm = () => {
    const nm = $("#nm"), pw = $("#pw");
    (name0 ? pw : nm).focus();
    $("#f").onsubmit = async (e) => {
      e.preventDefault();
      const name = nm.value.trim();
      if (!name) { nm.classList.add("bad"); nm.focus(); return; }
      const go = $("#go"); go.disabled = true; go.textContent = "Signing in…"; $("#prob").innerHTML = "";
      const device = B.newDevice();
      try {
        const s = await B.call("POST", "api/web/login", { username: name, password: pw.value, device }, { device });
        finish(s, device);
      } catch (err) {
        go.disabled = false; go.textContent = "Sign in";
        const p = B.signInProblem(err, name);
        if (p.kind === "wrong") pw.classList.add("bad");
        showProblem($("#prob"), p, () => {
          pw.classList.remove("bad");
          if (p.kind === "wrong") { pw.value = ""; pw.focus(); }
          else if (p.kind === "reach" || p.kind === "other") $("#f").requestSubmit();
          else nm.focus();
        });
      }
    };
    nm.oninput = () => nm.classList.remove("bad");
  };
  const quickConnect = async () => {
    const my = ++gen, device = B.newDevice();
    const stop = () => my !== gen || !$("#code") || !location.hash.startsWith("#/signin");
    try {
      const s = await B.call("POST", "api/web/qc/start", {}, { device });
      if (stop()) return;
      $("#code").innerHTML = `<div class="code">${String(s.code || "").split("").map((c) => `<b>${esc(c)}</b>`).join("")}</div><p class="dim">Waiting for your phone…</p>`;
      const tick = async () => {
        if (stop()) return;
        try {
          const c = await B.call("POST", "api/web/qc/check", { secret: s.secret }, { device });
          if (c.ok) return finish(await B.call("POST", "api/web/qc/finish", { secret: s.secret }, { device }), device);
          // the code ran out: get a fresh one
          if (c.gone) return quickConnect();
        } catch (err) {}
        setTimeout(tick, 3000);
      };
      setTimeout(tick, 3000);
    } catch (err) {
      if (stop()) return;
      $("#code").innerHTML = "";
      const p = B.signInProblem(err, "");
      showProblem($("#qcProb"), Object.assign({}, p, { title: /turned off/i.test(err.message) ? "Quick Connect is off" : "No code right now", line: /turned off/i.test(err.message) ? err.message : p.line, button: "Try again" }), () => { $("#code").innerHTML = B.spinner(); quickConnect(); });
    }
  };
  const finish = (s, device) => {
    gen++;
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
  draw();
});

// ---------- Home ----------
// Grown-ups get the Glowfin Home (ui-6): the top area and rows (no news row, Ben 2026-11-18).
// Kids (Ben's pick K1, ui-17): "Hi there! What do you want to watch?" (no names: Kids is shared), three big round
// buttons (Movies lilac, Shows sky blue, Games mint), then Picked for you and Keep watching. Easy mode is gone.
B.route(/^\/home$/, async (m, nav) => {
  // Someone has the Lobby open: a tap on the shoulder to join (lobby.js)
  if (B.lobby) B.lobby.homeNudge(nav);
  const grow = B.doorsGrow; B.doorsGrow = false;
  if (!B.me.kids) {
    B.page().innerHTML = B.top("") + `<div class="home ghome">${B.doorTop()}<div class="rows" id="rows">${B.spinner()}</div></div>`;
    B.wireTop();
    B.loadHomeRows(nav);
    return;
  }
  const btn = (k) => { const d = DOORS[k]; return d ? `<a class="kbtn" data-door="${k}" href="#/${k}">${B.glowIcon ? B.glowIcon(k) : I(d.icon)}<span>${esc(d.name)}</span></a>` : ""; };
  B.page().innerHTML = B.top("") + `<div class="home fam kidsh">
    <div class="greet">Hi there!<small class="kq">What do you want to watch?</small></div>
    <div class="kbtns">${["movies", "shows", "games"].map(btn).join("")}</div>
    <div id="famrow">${B.spinner()}</div>
  </div>`;
  B.wireTop();
  // When Glowfin opens, the rail's icons light up one after another
  if (grow && B.railLightUp) setTimeout(() => B.railLightUp(), 60);
  const f = $(".kbtns .kbtn"); if (f) f.focus({ preventScroll: true });
  B.loadFamilyRow(nav, true);
});

// Glowfin Home rows: Continue watching, Next up, then Recently added, Top 10 this week, Because you
// watched, Trending not on Glowfin, Anime airing this season and Watch it again
B.loadHomeRows = async (nav) => {
  const u = B.uid(), F = B.FIELDS;
  const get = (path) => B.get(path).then((r) => B.keep(r.Items || [])).catch(() => []);
  const animeShows = (await B.libs("shows")).filter(B.isAnimeLib);
  const [movies, eps, next, freshM, freshS, topM, topS, again, missing] = await Promise.all([
    get(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Movie&Limit=20&Fields=${F}`),
    get(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Episode&Limit=20&Fields=${F}`).then((l) => l.filter((e) => !B.watchedEp(e))),
    get(`/Shows/NextUp?userId=${u}&Limit=24&Fields=${F}&enableResumable=false&enableRewatching=false`),
    get(`/Items?userId=${u}&IncludeItemTypes=Movie&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=20&Fields=${F}`),
    get(`/Items?userId=${u}&IncludeItemTypes=Series&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=20&Fields=${F}`),
    B.trending ? B.trending("Movie").catch(() => []) : [],
    B.trending ? B.trending("Series").catch(() => []) : [],
    get(`/Items?userId=${u}&IncludeItemTypes=Movie,Series&Recursive=true&Filters=IsPlayed&MinCommunityRating=7&SortBy=Random&Limit=20&ImageTypes=Primary&Fields=${F}`),
    B.trendingMissing ? B.trendingMissing() : [],
  ]);
  const airing = [];
  for (const l of animeShows) airing.push(...(await get(`/Items?userId=${u}&ParentId=${l.Id}&IncludeItemTypes=Series&Recursive=true&SeriesStatus=Continuing&SortBy=DateLastContentAdded&SortOrder=Descending&Limit=20&ImageTypes=Primary&Fields=${F}`)));
  const played = await get(`/Items?userId=${u}&IncludeItemTypes=Movie&Recursive=true&Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=1&Fields=${F}`);
  const because = played[0] ? await get(`/Items/${played[0].Id}/Similar?userId=${u}&Limit=20&Fields=${F}`) : [];
  if (!B.alive(nav) || !$("#rows")) return;
  const seen = new Set();
  const cont = movies.concat(eps)
    .sort((a, b) => String((b.UserData || {}).LastPlayedDate || "").localeCompare(String((a.UserData || {}).LastPlayedDate || "")))
    .filter((it) => { const k = it.SeriesId || it.Id; if (seen.has(k)) return false; seen.add(k); return true; });
  const card = (it) => (it.Type === "Episode"
    ? B.posterCard(Object.assign({}, it, { Name: it.SeriesName }), { href: B.hrefOf(it), sub: `S${it.ParentIndexNumber} E${it.IndexNumber}`, noTick: true, progress: true })
    : B.posterCard(it, { progress: true }));
  const top = [];
  for (let i = 0; i < 10 && top.length < 10; i++) { if (topM[i]) top.push(topM[i]); if (topS[i]) top.push(topS[i]); }
  const fresh = freshM.concat(freshS).sort((a, b) => String(b.DateCreated || "").localeCompare(String(a.DateCreated || ""))).slice(0, 20);
  $("#rows").innerHTML = [
    B.row("Continue watching", cont.slice(0, 20).map(card)),
    B.row("Next up", next.map(card)),
    B.row("Recently added", fresh.map((it) => B.posterCard(it, { markNew: true }))),
    B.row("Top 10 this week", B.top10(top.slice(0, 10))),
    played[0] && because.length >= 5 ? B.row(`Because you watched ${played[0].Name}`, because.map((it) => B.posterCard(it))) : "",
    B.row("Trending, not on Glowfin", missing.map(({ r, kind }) => (B.askCard ? B.askCard(Object.assign({ mediaType: kind === "movies" ? "movie" : "tv" }, r)) : ""))),
    B.row("Anime airing this season", airing.slice(0, 20).map((it) => B.posterCard(it))),
    B.row("Watch it again", again.map((it) => B.posterCard(it))),
  ].join("");
  B.wireDescribe(cont[0] || next[0] || fresh[0]);
  const f = $("#rows .poster"); if (f) f.focus({ preventScroll: true });
  B.loadHomeNotices(nav);
};

// Kids: Picked for you (Ben's picks), then one Keep watching row with names under the pictures
B.loadFamilyRow = async (nav, kids) => {
  const u = B.uid(), F = B.FIELDS;
  const get = (path) => B.get(path).then((r) => B.keep(r.Items || [])).catch(() => []);
  const [movies, eps, next] = await Promise.all([
    get(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Movie&Limit=12&Fields=${F}`),
    get(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Episode&Limit=12&Fields=${F}`).then((l) => l.filter((e) => !B.watchedEp(e))),
    get(`/Shows/NextUp?userId=${u}&Limit=12&Fields=${F}&enableResumable=false&enableRewatching=false`),
  ]);
  if (!B.alive(nav)) return;
  const seen = new Set();
  const list = movies.concat(eps, next)
    .sort((a, b) => String((b.UserData || {}).LastPlayedDate || "").localeCompare(String((a.UserData || {}).LastPlayedDate || "")))
    .filter((it) => { const k = it.SeriesId || it.Id; if (seen.has(k)) return false; seen.add(k); return true; })
    .slice(0, 12);
  const card = (it) => (it.Type === "Episode"
    ? B.posterCard(Object.assign({}, it, { Name: it.SeriesName }), { href: B.hrefOf(it), sub: `S${it.ParentIndexNumber} E${it.IndexNumber}`, noTick: true })
    : B.posterCard(it, { progress: true }));
  // Idea Box 25: titles Ben put on the Kids home come first ("Picked for you")
  const picks = kids ? await get(`/Items?userId=${u}&Tags=benflix-kids-pick&IncludeItemTypes=Movie,Series&Recursive=true&SortBy=SortName&Limit=30&Fields=${F}`) : [];
  if (!B.alive(nav)) return;
  const box = $("#famrow");
  if (box) {
    box.innerHTML = (picks.length ? B.row("Picked for you", picks.map((it) => B.posterCard(it))) : "") + (list.length
      ? B.row("Keep watching", list.map(card))
      : `<section class="rowx"><h3>Keep watching</h3><p class="sub">Nothing started yet. Pick Movies or Shows to find something fun!</p></section>`);
  }
};

// Home has no news list any more (Ben 2026-11-18). It still gives a corner card for each request of yours
// that's newly ready (once per browser), and shows messages from Ben (the Needs-you card waits for Got it).
const myReady = async () => {
  if (!B.seerr || !B.seerrMe) return [];
  const me = await B.seerrMe();
  if (!me || !me.active || !me.userFound) return [];
  const list = await B.seerr(`/request?take=20&skip=0&sort=added&requestedBy=${me.jellyseerrUserId}`).catch(() => null);
  const mine = ((list && list.results) || []).filter((r) => r.media && r.media.tmdbId && r.media.status === 5 && r.media.jellyfinMediaId
    && Date.now() - Date.parse(r.media.updatedAt || r.updatedAt || 0) < 3 * 86400000);
  const ready = await Promise.all(mine.slice(0, 12).map(async (r) => {
    const md = r.media;
    const d = await B.cached(`seerr-${r.type}-${md.tmdbId}-home`, 300000, () => B.seerr(`/${r.type}/${md.tmdbId}`)).catch(() => null);
    if (!d) return null;
    const title = d.title || d.name || "";
    const img = B.tmdbImg ? B.tmdbImg(d.posterPath, "w185") : "";
    return { id: `req-${md.jellyfinMediaId}`, name: title, img: B.img(md.jellyfinMediaId, "Primary", { h: 240 }) || img, href: `#/${r.type === "tv" ? "show" : "movie"}/${md.jellyfinMediaId}`, at: r.createdAt };
  }));
  return ready.filter(Boolean).sort((a, b) => String(b.at).localeCompare(String(a.at))).slice(0, 2);
};
B.loadHomeNotices = async (nav) => {
  const [prefs, ready] = await Promise.all([B.prefs().catch(() => null), myReady().catch(() => [])]);
  if (!B.alive(nav) || !$("#rows")) return;
  homeNotices((prefs && prefs.CustomPrefs) || {}, ready);
};

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
    if (!$("#rows")) return;
    await B.needsYou(f[1] || "Message from Ben", f.slice(2).join("\u001F"), "");
  }
};

// ---------- your page (Me) ----------
// Ben 2026-11-18 (approved profile-page-1): your picture opens a full page like every other menu spot, with
// big tiles for My watching, Settings, Server, Lobby, Help and Report a problem. Requests isn't listed: it has
// its own spot on the rail and in More. [where, icon, name, short line]
B.profileItems = () => [
  ["#/mine", "chart", "My watching", "Your hours, top shows and streaks"],
  ["#/settings", "gear", "Settings", "Picture, sound, subtitles, Home rows"],
  B.offline && B.offline.can() ? ["#/downloads", "dl", "Downloads", "Watch on this computer with no connection"] : null,
  B.me && B.me.admin ? ["#/server", "server", "Server", "Who's watching and how the server is doing"] : null,
  B.lobby && B.lobby.allowed() ? ["#/lobby", "tv", "Lobby: watch together", "Start or join a room"] : null,
  ["#/help", "help", "Help and updates", "Guides and what's new"],
  ["report", "flag", "Report a problem", "Send a note about what went wrong"],
].filter(Boolean);
// Where a Me page tile goes
B.profileGo = (where) => {
  B.closeAll();
  if (where === "signout") return B.signOut();
  if (where === "report") return B.report();
  B.go(where);
};
B.openMenu = async (byKeys) => {
  if (!B.me) return;
  if (B.me.kids) return B.go("#/who");
  B.meByKeys = !!byKeys;
  if (location.hash === "#/me") {
    // Already here: go back to the first tile (My watching)
    const first = byKeys && $(".mepage .me-tile");
    if (first) { B.meByKeys = false; first.focus({ preventScroll: true }); }
    return;
  }
  B.go("#/me");
};
B.route(/^\/me$/, async (m, nav) => {
  if (!B.me) return B.go("#/who");
  if (B.me.kids) return B.go("#/who");
  const items = B.profileItems();
  B.page().innerHTML = B.top("Me", null) + `<div class="wrap mepage">
    <div class="me-hero">${B.picHtml(B.me, "", 220)}<div><h1>${esc(B.me.name)}</h1><div class="me-wt" id="meWt">&nbsp;</div>
      <button type="button" class="btn small" data-go="#/who">${I("swap")} Switch profile</button></div></div>
    <div class="me-tiles">${items.map(([where, icon, label, line]) => `<button type="button" class="me-tile ${where === "#/server" ? "admin" : ""}" data-go="${esc(where)}"><span class="me-ic">${I(icon)}</span><span class="me-tx"><b>${esc(label)}</b><small>${esc(line || "")}</small></span></button>`).join("")}</div>
    <button type="button" class="me-out" data-go="signout">${I("out")}<span>Sign out</span></button>
    ${window.glowfinDesktop ? `<div class="me-app"><span class="dim">Glowfin on this computer</span><button type="button" class="btn small" data-app="fullscreen">Full screen on or off</button><button type="button" class="btn small" data-app="changeServer">Change Tower's address</button><button type="button" class="btn small" data-app="quit">Close Glowfin</button></div>` : ""}
  </div>`;
  B.wireTop();
  const page = $(".mepage");
  page.addEventListener("click", (e) => {
    // The Glowfin app for computers (Linux, Steam Deck, Windows): its own window buttons, reachable by controller
    const app = e.target.closest("[data-app]");
    if (app && window.glowfinDesktop && window.glowfinDesktop[app.dataset.app]) { window.glowfinDesktop[app.dataset.app](); return; }
    const b = e.target.closest("[data-go]"); if (!b) return;
    B.profileGo(b.dataset.go);
  });
  if (B.meByKeys) { B.meByKeys = false; const first = $(".me-tile", page); if (first) first.focus({ preventScroll: true }); }
  B.watchTime().then((w) => { const el = $("#meWt"); if (el && B.alive(nav)) el.textContent = `${B.hm(w.month)} this month`; }).catch(() => {});
});

B.signOut = async () => {
  if (!(await B.ask(`Sign ${B.me.name} out of Glowfin in this browser?`, "Your lists and settings stay safe on the server. Sign back in any time.", "Sign out"))) return;
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
      const line = [Date.now(), what ? what.id : "", (what ? what.title : "Glowfin web").replace(/[|\n]/g, " "), problem.replace(/[|\n]/g, " "), "Web browser"].join("|");
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
