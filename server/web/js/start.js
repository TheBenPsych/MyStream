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
// Redesign 2 (su-family): the dim wall of posters behind, the grown-ups as big round pictures in one row,
// and Kids profiles a little smaller at the end with the names lined up.
B.route(/^\/who$/, async () => {
  // Switching profile stops the last person's music and forgets their hearts
  if (B.music) { B.music.stop(); B.music.liked.clear(); }
  B.me = null;
  const list = B.profiles();
  if (!list.length) return B.go("#/signin");
  const last = load("benflix.last");
  // Christmas (approved 2026-11-16): the cosy room with the tree, the train and the parade, 1 to 26 December
  const xmas = B.xmasOn();
  // Thanksgiving (approved 2026-11-16): the feast table room, 16 to 30 November
  const feast = !xmas && B.tgOn();
  // Friday the 13th (approved 2026-11-17): the room with the sleeping black cat, that day only (it takes over any
  // season room, because B.xmasOn and B.tgOn are off that day)
  const f13 = B.f13On();
  // Valentine's Day (approved 2026-11-17): the table set for two, 1 to 14 February, petals falling on the 14th
  const val = !xmas && !feast && !f13 && B.valOn();
  // New Year's (approved 2026-11-17): the party room, 27 December to 1 January, with a 10-second countdown at midnight
  const ny = !xmas && !feast && !f13 && B.nyOn();
  // 4th of July (approved 2026-11-17): the cookout room, 1 to 4 July, with the big fireworks show from 9 PM on the 4th
  const j4 = !xmas && !feast && !ny && B.j4On();
  // Easter (approved 2026-11-17): the spring morning room with the egg tree and the pet rabbit, Monday to Easter Sunday
  const easter = !xmas && !feast && !ny && !j4 && B.eaOn();
  const eaNow = new Date(), eaFound = easter && B.ea.isSunday(eaNow) && B.ea.hunt.all(eaNow);
  const eaCrack = eaFound && !B.ea.hunt.cracked(eaNow);
  // Popcorn Day (approved 2026-11-17): the popcorn cart room, just 19 January, the heap growing through the day
  const pcRoom = !xmas && !feast && !ny && !val && B.pcOn();
  // Star Wars Day (approved 2026-11-17): the desert home, 4 May only. The laser sword matches each profile's side
  const sw = !xmas && !feast && !ny && !val && !pcRoom && B.swOn();
  // Earth Day (approved 2026-11-17): the plant corner and the spring window, all Earth week
  const earth = !xmas && !feast && !ny && B.edOn();
  // Halloween (approved 2026-11-17): the spooky room, 1 to 31 October; bats and "Happy Halloween!" from 5 PM on the 31st
  const hw = !xmas && !feast && !ny && B.hwOn();
  // St. Patrick's Day (approved 2026-11-17): the rainy Irish room, 10 to 17 March, with a rainbow on the 17th
  const sp = !xmas && !feast && !f13 && !ny && !val && !pcRoom && !sw && !earth && !hw && B.spOn();
  // Video Games Day (approved 2026-11-17): the arcade room, 12 September
  const arcade = !xmas && !feast && !f13 && !ny && !j4 && !val && !pcRoom && !sw && !earth && !hw && !sp && !easter && B.vgOn();
  const order = list.slice().sort((a, b) => (!!a.kids - !!b.kids) || a.name.localeCompare(b.name));
  B.page().innerHTML = `<div class="who fade-in">
    ${xmas || feast || f13 || ny || j4 || val || pcRoom || sw || earth || hw || sp || easter || arcade ? '<svg class="xroom" aria-hidden="true" xmlns="http://www.w3.org/2000/svg"></svg>' : '<div class="wall" aria-hidden="true"></div>'}
    ${B.logo()}
    ${xmas ? `<h1 class="xmas">${esc(B.xmasGreeting())}</h1>${B.xmas.countdown(new Date()) ? `<p class="xcount">${esc(B.xmas.countdown(new Date()))}</p>` : ""}` : feast ? `<h1 class="xmas">${esc(B.tg.greeting())}</h1>` : f13 ? `<h1 class="xmas">${esc(B.f13.greeting())}</h1><p class="xline">${esc(B.f13.line())}</p>`: ny ? `<h1 class="xmas" id="nyGreet">${esc(B.ny.greeting(new Date()))}</h1><p class="xcount nycount" id="nyCount">${esc(B.ny.countdown(new Date()))}</p>` : j4 ? `<h1 class="xmas">${esc(B.j4.greeting())}</h1><p class="xcount j4count" id="j4Count">${esc(B.j4.countdown(new Date()))}</p>` : val ? `<h1 class="xmas">${esc(B.val.greeting(new Date()))}</h1><p class="xcount vcount">${esc(B.val.countdown(new Date()))}</p>` : pcRoom ? `<h1 class="xmas">${esc(B.pc.greeting())}</h1><p class="xcount pcline">${esc(B.pc.line())}</p>` : sw ? `<h1 class="swh">${esc(B.sw.greeting())}</h1>` : earth ? `<h1 class="xmas">${esc(B.ed.greeting())}</h1><p class="xcount edline">${esc(B.ed.line(new Date()))}</p>` : hw ? `<h1 class="xmas" id="hwGreet">${esc(B.hw.greeting(new Date()))}</h1><p class="xcount" id="hwCount">${esc(B.hw.countdown(new Date()))}</p>` : sp ? `<h1 class="xmas">${esc(B.sp.greeting(new Date()))}</h1>${B.sp.countdown(new Date()) ? `<p class="xcount spcount">${esc(B.sp.countdown(new Date()))}</p>` : ""}` : easter ? `<h1 class="xmas" id="eaGreet">${esc(eaFound && !eaCrack ? "You found all six!" : B.ea.greeting())}</h1>${B.ea.countdown(eaNow) ? `<p class="xcount ea">${esc(B.ea.countdown(eaNow))}</p>` : ""}` : arcade ? `<h1 class="vgh">${esc(B.vg.greeting())}</h1><p class="vgline">${esc(B.vg.LINE)}</p>` : "<h1>Who's watching?</h1>"}
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
    // Friday the 13th: the sleeping cat opens its eyes and yawns before Home opens
    const svg = $(".who .xroom");
    if (f13 && svg && svg.scene && svg.clock && !svg.dataset.waking) {
      svg.dataset.waking = "1";
      svg.scene.wake(svg.clock());
      setTimeout(open, 1500);
    } else if (!f13 || !svg || !svg.dataset.waking) open();
  };
  $("#addSomeone").onclick = () => B.go("#/signin");
  if (xmas || feast || f13 || ny || j4 || val || pcRoom || sw || earth || hw || sp || easter || arcade) {
    const svg = $(".who .xroom");
    const tall = () => innerWidth < innerHeight * 0.8;
    const room = xmas ? B.xmas : feast ? B.tg : f13 ? B.f13 : val ? B.val : pcRoom ? B.pc : j4 ? B.j4 : sw ? B.sw : ny ? B.ny : hw ? B.hw : sp ? B.sp : easter ? B.ea : earth ? B.ed : B.vg;
    // Star Wars Day: the blade takes the side of the profile you're on (or the last one picked)
    let side = B.swSide(last);
    if (sw) {
      const onTo = (e) => { const b = e.target.closest && e.target.closest(".person"); if (b && svg.scene && svg.scene.blade) { side = B.swSide(b.dataset.id); svg.scene.blade(side); } };
      $(".who .people").addEventListener("focusin", onTo);
      $(".who .people").addEventListener("mouseover", onTo);
    }
    let clock = null;
    // Easter Sunday: once all six hidden eggs are found, the golden egg cracks open (the first time), then stays open
    const draw = () => { if (!svg.isConnected) return; const scene = room.build(svg, { layout: tall() ? "tall" : "wide", open: eaFound && !eaCrack, side }); svg.dataset.shape = tall() ? "tall" : "wide"; if (!svg.dataset.playing) { svg.dataset.playing = "1"; const c = room.play(svg, { at: (t) => svg.scene && svg.scene.at(t) }); if (typeof c === "function") { clock = c; svg.clock = c; } } svg.scene = scene; };
    draw();
    if (eaCrack) {
      B.ea.hunt.setCracked(eaNow);
      setTimeout(() => { if (svg.scene && svg.scene.crack) svg.scene.crack(clock ? clock() : 0); }, 900);
      setTimeout(() => { const g = $("#eaGreet"); if (g) g.textContent = "You found all six!"; }, 3100);
    }
    if (hw) {
      // Halloween: at 5 PM on the 31st the bats come out and the greeting gets its "!"
      let was = B.hw.stage(new Date());
      const tick = () => {
        if (!svg.isConnected) return;
        const d = new Date(), g = $("#hwGreet"), c = $("#hwCount");
        if (g) g.textContent = B.hw.greeting(d);
        if (c) c.textContent = B.hw.countdown(d);
        const st = B.hw.stage(d);
        if (st !== was) { was = st; draw(); }
        setTimeout(tick, 30000);
      };
      setTimeout(tick, 30000);
    }
    if (j4) {
      // 4th of July: the line under the greeting keeps up, and at 9 PM on the 4th the fireworks show starts
      let was = B.j4.variant(new Date());
      const tick = () => {
        if (!svg.isConnected) return;
        const d = new Date(), c = $("#j4Count");
        if (c) c.textContent = B.j4.countdown(d);
        const v = B.j4.variant(d);
        if (v !== was) { was = v; draw(); }
        setTimeout(tick, 20000);
      };
      tick();
    }
    if (ny) {
      // New Year's: the greeting counts down the last 10 seconds of the year, then the room pops open with confetti
      let was = B.ny.variant(new Date());
      const tick = () => {
        if (!svg.isConnected) return;
        const d = new Date();
        const g = $("#nyGreet"), c = $("#nyCount");
        if (g) g.textContent = B.ny.greeting(d);
        if (c) c.textContent = B.ny.countdown(d);
        const v = B.ny.variant(d);
        if (v !== was) { was = v; draw(); if (svg.scene && svg.scene.celebrate && clock) svg.scene.celebrate(clock()); }
        setTimeout(tick, B.ny.countdownSecs(d) || (d.getMonth() === 11 && d.getDate() === 31 && d.getHours() === 23 && d.getMinutes() >= 58) ? 250 : 20000);
      };
      tick();
    }
    const onResize = () => { if (!svg.isConnected) return removeEventListener("resize", onResize); if (svg.dataset.shape !== (tall() ? "tall" : "wide")) draw(); };
    addEventListener("resize", onResize);
  } else whoWall(list);
});

// 12 still posters (PG-13 or lower), picked once a day and kept, so the wall costs nothing most days
async function whoWall(list) {
  const el = $(".who .wall");
  const who = list.find((p) => !p.expired && !p.kids) || list.find((p) => !p.expired);
  if (!el || !who) return;
  let wall = loadJson("benflix.wall", null);
  if (!wall || wall.by !== who.id || Date.now() - wall.t > 864e5 || !(wall.items || []).length) {
    try {
      const r = await B.call("GET", `api/web/jf/Users/${enc(who.id)}/Items?IncludeItemTypes=Movie,Series&Recursive=true&SortBy=Random&Limit=12&MaxOfficialRating=PG-13&HasParentalRating=true&ImageTypes=Primary&Fields=PrimaryImageAspectRatio`, undefined, { profile: who, quiet401: true });
      wall = { t: Date.now(), by: who.id, items: ((r && r.Items) || []).filter((it) => it.ImageTags && it.ImageTags.Primary).map((it) => [it.Id, it.ImageTags.Primary]) };
      try { localStorage.setItem("benflix.wall", JSON.stringify(wall)); } catch (e) {}
    } catch (e) { return; }
  }
  if (!$(".who .wall")) return;
  el.innerHTML = wall.items.slice(0, 12).map(([id, tag]) => `<i style="background-image:url('api/web/jf/Items/${enc(id)}/Images/Primary?fillHeight=300&quality=80&tag=${enc(tag)}&api_key=${enc(who.token)}')"></i>`).join("");
  requestAnimationFrame(() => el.classList.add("on"));
}

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
  if (st === 400 && /name/i.test(msg)) return { kind: "wrong", title: "Type your name first", line: "Your name is the one you use for Benflix on the TV.", button: "OK" };
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
        <h2>${wide() ? "Or type it in" : "Sign in to Benflix"}</h2>
        <label>Name<input class="field" id="nm" autocomplete="username" value="${esc(name0)}"></label>
        <label>Password<input class="field" id="pw" type="password" autocomplete="current-password" placeholder="Leave it empty if you don't have one"></label>
        <div id="prob"></div>
        <div class="btns"><button class="btn white" type="submit" id="go">Sign in</button></div>
        ${wide() ? "" : `<p class="dim small">Already signed in on a TV or phone? <button class="linkbtn" type="button" id="useCode">Sign in with a code instead</button></p>`}
      </form>`;
    const phone = `<div class="half phone">
        <h2>Sign in with your phone</h2>
        <p>On your phone, open Benflix, then My Benflix › Quick Connect, and type this code:</p>
        <div id="code">${B.spinner()}</div>
        <div id="qcProb"></div>
        ${wide() ? "" : `<button class="btn" type="button" id="useForm">Type it in instead</button>`}
      </div>`;
    const inner = wide() ? `${phone}<div class="divider"></div>${form}` : (codeOnPhone ? phone : form);
    B.page().innerHTML = `<div class="signin"><div class="card ${wide() ? "two" : ""} fade-in">${B.logo()}<div class="halves">${inner}</div><div style="margin-top:22px">${back}</div></div></div>`;
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
// Everyone but Kids and Easy mode gets the approved Today board (f3-panel): the greeting with Tower's weather line,
// six square doors 3 by 2, and a soft list panel as tall as the doors (news, On the way, Did you know).
// Kids (kd-family): three big doors (Movies, Shows, Games) that name a couple of titles, then one Keep watching row.
// Easy mode (ez-family, Settings > Look): the four doors this person opens most (the rest under More), 25% bigger,
// then one Keep watching row.
B.easyMode = () => !!(B.me && !B.me.kids && B.webSettings().easy);
const doorUseKey = () => "benflix.dooruse." + (B.me ? B.me.id : "");
B.doorUse = () => loadJson(doorUseKey(), {});
document.addEventListener("click", (e) => {
  const d = e.target.closest && e.target.closest("a.door[data-door]");
  if (!d || !B.me) return;
  const use = B.doorUse(); use[d.dataset.door] = (use[d.dataset.door] || 0) + 1;
  store(doorUseKey(), JSON.stringify(use));
});
B.route(/^\/home$/, async (m, nav) => {
  // Someone has the Lobby open: a tap on the shoulder to join (lobby.js)
  if (B.lobby) B.lobby.homeNudge(nav);
  const grow = B.doorsGrow; B.doorsGrow = false;
  const kids = B.me.kids;
  const easy = B.easyMode();
  // Benflix music: the violet fifth door (not on Kids); Books: the lime sixth door
  const all = ["movies", "shows", "live", "games"].concat(kids || !DOORS.music ? [] : ["music"]).concat(DOORS.books ? ["books"] : []);
  let keys = all, more = [];
  if (kids) keys = ["movies", "shows", "games"];
  if (easy) {
    const use = B.doorUse();
    // Movies always stays, so it's always in the same place; then the ones opened most (a tie keeps the usual order)
    const top = ["movies"].concat(all.slice().sort((a, b) => (use[b] || 0) - (use[a] || 0))).filter((k, i, arr) => arr.indexOf(k) === i).slice(0, 4);
    keys = all.filter((k) => top.includes(k));
    more = all.filter((k) => !top.includes(k));
  }
  const doors = keys.map((k) => DOORS[k]);
  // Navigation redesign: Easy mode gets the normal Home (the rail has every door); Kids get their own
  const small = kids;
  // Video Games Day look: the Games door nudges you to play something today
  const vgDay = B.vgOn();
  const doorDesc = (d) => (vgDay && d.key === "games" ? "Play something today" : d.desc);
  const doorHtml = (d) => `<a class="door" data-door="${d.key}" href="#/${d.key}" style="--c:${d.color}">${I(d.icon)}<div><div class="n">${esc(d.name)}</div><div class="d" id="dl-${d.key}">${kids ? "" : esc(doorDesc(d))}</div></div></a>`;
  // Ben: Kids profiles are shared by every kid in the house, so no names there, just a friendly question
  // Thanksgiving look: "Happy Thanksgiving, Ben" in the curly holiday font (html.tg .greet.tgh)
  const tgGreet = !kids && B.tgOn();
  // Friday the 13th look (Kids too): "Happy Friday the 13th, Ben" in the curly holiday font, and a lucky line
  // instead of the weather (filled in by B.loadBoard once the news is in)
  const f13 = B.f13On();
  // Valentine's look: "Happy Valentine's Day, Ben" in the curly holiday font, with "4 days to Valentine's Day" under it
  const valGreet = !kids && !tgGreet && !f13 && B.valOn();
  // New Year's look: "Happy New Year, Ben" in the curly holiday font, with "4 days to 2027" leading the line under it
  const nyGreet = !kids && !f13 && B.nyOn();
  // Earth Day look: "Happy Earth Day, Ben" in the curly holiday font (Kids too), "Earth week, day 3" under it
  const edGreet = B.edOn();
  // 4th of July look: "Happy 4th of July, Ben" in the curly holiday font, with "2 days to the Fourth" leading the line under it
  const j4Greet = !kids && B.j4On();
  // Popcorn Day look: "Happy Popcorn Day, Ben" in the curly holiday font (Kids too), "It's National Popcorn Day" under it
  const pcGreet = B.pcOn();
  // Star Wars Day: "May the 4th be with you, Ben" in yellow crawl lettering (html.sw .greet.swg), Kids too
  const swGreet = B.swOn();
  // St. Patrick's Day look: "Happy St. Patrick's Day, Ben" in the curly holiday font, with "3 days to St. Patrick's Day" under it
  const spGreet = !kids && B.spOn();
  // Halloween look: "Happy Halloween, Ben" in the curly holiday font, with "14 days to Halloween" under it;
  // Kids gets "Happy Halloween! What do you want to watch?"
  const hwOn = B.hwOn(), hwGreet = !kids && hwOn;
  // Easter look: "Happy Easter, Ben" in the curly holiday font, with the countdown leading the line under it
  const eaGreet = !nyGreet && !tgGreet && !valGreet && !pcGreet && !swGreet && !j4Greet && !edGreet && !spGreet && !hwGreet && !f13 && B.eaOn();
  const greet = kids ? `${f13 ? "Happy Friday the 13th!" : edGreet ? "Happy Earth Day!" : pcGreet ? "Happy Popcorn Day!" : swGreet ? "May the 4th be with you!" : "Hi there!"}<small class="kq">${hwOn ? "Happy Halloween! " : ""}What do you want to watch?</small>` : `${esc(vgDay ? B.vg.greeting() : f13 ? "Happy Friday the 13th" : edGreet ? "Happy Earth Day" : hwGreet ? "Happy Halloween" : pcGreet ? "Happy Popcorn Day" : swGreet ? B.sw.greeting() : spGreet ? "Happy St. Patrick\u2019s Day" : j4Greet ? "Happy 4th of July" : nyGreet ? "Happy New Year" : tgGreet ? "Happy Thanksgiving" : valGreet ? "Happy Valentine’s Day" : eaGreet ? "Happy Easter" : (B.holiday() || B.greeting()))}, ${esc(B.me.name)}`;
  if (small) {
    B.page().innerHTML = B.top("") + `<div class="home fam spotted ${kids ? "kidsh" : "easyh"}">
      <div class="greet${edGreet || pcGreet ? " tgh" : ""}${swGreet ? " swg" : ""}${f13 ? " f13h" : ""}">${greet}</div>
      ${B.spotHtml()}
      <div id="famrow">${B.spinner()}</div>
    </div>`;
  } else {
    const wx = f13 ? B.f13Lucky(0, 0) : B.savedWeather();
    B.page().innerHTML = B.top("") + `<div class="home board spotted ${grow ? "grow" : ""}">
      <div class="bgreet"><div class="greet${tgGreet || nyGreet || j4Greet || valGreet || pcGreet || edGreet || spGreet ? " tgh" : eaGreet ? " eah" : ""}${swGreet ? " swg" : ""}${hwGreet ? " hwh" : ""}${f13 ? " f13h" : ""}">${greet}</div>${j4Greet && B.j4.countdown(new Date()) ? `<div class="wx j4c">${esc(B.j4.countdown(new Date()))}</div>` : ""}${hwGreet && B.hw.homeLine(new Date()) ? `<div class="wx hwc">${esc(B.hw.homeLine(new Date()))}</div>` : ""}${j4Greet && B.j4.countdown(new Date()) ? `<div class="wx j4c">${esc(B.j4.countdown(new Date()))}</div>` : ""}${nyGreet && B.ny.countdown(new Date()) ? `<div class="wx nyc">${esc(B.ny.countdown(new Date()))}</div>` : ""}${eaGreet && B.ea.countdown(new Date()) ? `<div class="wx eac">${esc(B.ea.countdown(new Date()))}</div>` : ""}${valGreet && B.val.countdown(new Date()) ? `<div class="wx vlc">${esc(B.val.countdown(new Date()))}</div>` : ""}${pcGreet ? `<div class="wx pcw">${esc("It\u2019s National Popcorn Day")}</div>` : ""}${edGreet ? `<div class="wx edw">${esc(B.ed.line(new Date()))}</div>` : ""}${spGreet && B.sp.countdown(new Date()) ? `<div class="wx spc">${esc(B.sp.countdown(new Date()))}</div>` : ""}<div class="wx" id="wx">${esc(vgDay ? [B.vg.LINE, wx].filter(Boolean).join(" · ") : wx)}</div></div>
      ${B.spotHtml()}
      <div class="bmain"><div class="blist" id="blist"></div></div>
    </div>`;
  }
  B.wireTop();
  const mb = $("#moreDoors");
  if (mb) mb.onclick = () => B.side(`<h3>More doors</h3><div class="fmore">${more.map((k) => doorHtml(DOORS[k])).join("")}</div>`);
  // Navigation redesign (approved in-light): when Benflix opens, the rail's icons light up one after another
  // with the door notes, in place of the doors growing out of the logo
  if (grow && B.railLightUp) setTimeout(() => B.railLightUp(), 60);
  B.loadSpot(nav);
  // The keyboard starts on Play (once the first pick is there)
  const focusPlay = (n) => { if (!B.alive(nav)) return; if (!B.spotFocus() && n < 20) setTimeout(() => focusPlay(n + 1), 150); };
  focusPlay(0);
  if (small) B.loadFamilyRow(nav, kids); else B.loadBoard(nav);
});

// Kids and Easy mode: one Keep watching row with names under the pictures, and (Kids) a couple of titles on each door
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
      : `<section class="rowx"><h3>Keep watching</h3><p class="sub">${kids ? "Nothing started yet. Open a door and pick something fun!" : "Nothing started yet. Open a door to find something to watch."}</p></section>`);
  }
  if (!kids) return;
  const names = async (type) => (await get(`/Items?userId=${u}&IncludeItemTypes=${type}&Recursive=true&SortBy=DatePlayed,DateCreated&SortOrder=Descending&Limit=4&EnableImages=false`))
    .map((it) => it.Name).filter(Boolean).filter((n, i, arr) => arr.indexOf(n) === i).slice(0, 2);
  const [mn, sn] = await Promise.all([names("Movie"), names("Series")]);
  if (!B.alive(nav)) return;
  const line = (key, found, fallback) => { const el = $("#dl-" + key); if (el) el.textContent = found.length ? found.join(", ") + " and more" : fallback; };
  line("movies", mn, "Films for any night");
  line("shows", sn, "Series and episodes");
  line("games", [], "Games to play");
};

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
const KIND_READY = "#5BD18B", KIND_WAY = "#FFB547", KIND_FACT = "#3DD6C6", KIND_OTHER = "#9497A3", KIND_CANT = "#F07A3E";
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

// Something you asked for that Ben couldn't find (Ben 2026-11-15: a Home news row, never a pop-up). The list is
// the one the TV and phone app shares with every profile (benflix-shared / benflix.cantfind.v1). Ben marks these
// himself, so he isn't told. A row stays 14 days, or until it's cleared.
const cantFindRows = async () => {
  if (B.me.admin || B.me.kids || !B.seerrMe) return [];
  const [me, shared] = await Promise.all([B.seerrMe(), B.prefs("benflix-shared").catch(() => null)]);
  if (!me || !me.active || !me.userFound) return [];
  let list = [];
  try { list = JSON.parse(((shared && shared.CustomPrefs) || {})["benflix.cantfind.v1"] || "[]"); } catch (e) {}
  const mine = (Array.isArray(list) ? list : [])
    .filter((e) => e && e.key && !e.keepLooking && (e.askedBy || []).includes(me.jellyseerrUserId))
    .map((e) => ({ e, t: Date.parse(`${e.date}T00:00:00`) || 0 }))
    .filter((x) => x.t && Date.now() - x.t < 14 * 86400000)
    .sort((a, b) => b.t - a.t).slice(0, 2);
  const rows = await Promise.all(mine.map(async ({ e, t }) => {
    const type = e.key.startsWith("MOVIE") ? "movie" : "tv", tmdb = e.key.split("-")[1];
    const d = await B.cached(`seerr-${type}-${tmdb}-home`, 300000, () => B.seerr(`/${type}/${tmdb}`)).catch(() => null);
    // It turned up in the meantime: nothing to say sorry about
    if (d && d.mediaInfo && d.mediaInfo.status === 5) return null;
    const title = (d && (d.title || d.name)) || e.title || "Something you asked for";
    const why = e.auto ? `We looked for ${title} for a week but couldn't find a copy yet.` : `Ben looked for ${title} but couldn't find a copy yet.`;
    const img = (d && B.tmdbImg ? B.tmdbImg(d.posterPath, "w185") : "") || e.posterUrl || "";
    return { id: `cant-${e.key}-${e.date}`, news: true, kind: "Couldn't find it", title, full: `${why} If one turns up, it'll come to Benflix by itself.${e.note ? ` Ben's note: \u201C${e.note}\u201D` : ""}`,
      go: ["See details", `#/ask/${type}/${tmdb}`], kc: KIND_CANT, img, href: "", t };
  }));
  return rows.filter(Boolean);
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
  const [prefs, nextUp, music, books, reqs, facts, weather, cant] = await Promise.all([
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
    cantFindRows().catch(() => []),
  ]);
  // Music redesign 3: songs, albums, playlists and artists someone sent you (Send to someone)
  const sentMusic = DOORS.music && !B.me.kids && B.musicMe ? await B.musicMe(true).catch(() => null) : null;
  if (!B.alive(nav) || !$("#blist")) return;
  // Weather: Tower's line with today's name in front (nothing when no town is set on the Server page)
  if (weather) {
    const line = weather.ready ? `${dayName()} · ${weather.line}` : "";
    try { localStorage.setItem("benflix.weather", JSON.stringify({ line, at: Date.now() })); } catch (e) {}
    if (!B.f13On()) $("#wx").textContent = B.vgOn() ? [B.vg.LINE, line].filter(Boolean).join(" · ") : line;
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
  const SENT_HREF = { song: "album", album: "album", playlist: "playlist", artist: "artist" };
  ((sentMusic && sentMusic.sent) || []).slice(0, 3).forEach((x) => {
    if (!SENT_HREF[x.kind] || !x.id) return;
    const href = x.kind === "song" ? `#/song/${x.id}` : `#/${SENT_HREF[x.kind]}/${x.id}`;
    add({ id: `sent-${x.id_sent}`, news: true, kind: x.from ? `From ${x.from}` : "Sent to you", title: x.name, kc: KIND_OTHER, img: B.img(x.id, "Primary", { h: 160 }), square: true, href, t: (x.at || 0) * 1000 });
  });
  const pic = (u) => (u ? u.replace(/^\//, "") + (u.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token) : "");
  ((books && books.news) || []).slice(0, 2).forEach((b) => {
    const name = b.name || b.text, id = String(b.id || "");
    const vol = String(b.line || "").replace(/^New: /, "").replace(/ is here$/, "");
    const [kind, title] = id.startsWith("bkr-") ? ["Ready to read", name] : id.startsWith("bks-") ? ["On your phone", name] : ["New volume", vol && vol !== b.line ? `${name} · ${vol}` : name];
    add({ id: `book-${b.id}`, news: true, kind, title, kc: id.startsWith("bkr-") ? KIND_READY : KIND_OTHER, img: pic(b.cover), href: `#/book/${enc(b.title)}`, t: (+b.at || 0) * 1000 });
  });
  reqs.ready.forEach(add);
  cant.forEach(add);
  // Holiday marks (approved hq-count A): the first card counts down to the day (js/holidaynav.js)
  const hol = B.holidayNav ? B.holidayNav() : null;
  const left = hol ? hol.countdown() : "";
  if (left) add({ id: `holiday-${hol.key}-${new Date().toDateString()}`, kind: "Countdown", title: left, full: left, kc: hol.lights[0], href: "", t: Infinity });
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
  // Friday the 13th look: "Lucky you: 3 new episodes and 1 request ready" under the greeting
  if (B.f13On() && $("#wx")) $("#wx").textContent = B.f13Lucky(boardParts.news.filter((r) => /^New episode/.test(r.kind)).length, boardParts.news.filter((r) => r.kind === "Ready to watch").length);
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
// New Year's look: each kind of notice gets its own small gold mark (html.ny .brow small::before)
const nyMark = (kind) => { const k = String(kind || "").toLowerCase(); return k.startsWith("new") ? "glass" : k.startsWith("ready") ? "star" : k.startsWith("on the way") ? "clock" : k.includes("live") ? "ball" : "spark"; };
// Video Games Day look: each kind of news gets its own pixel picture (a TV, a star, an hourglass or a joystick)
const vgMark = (r) => {
  if (!(B.vgOn && B.vgOn())) return "";
  const k = String(r.kind || "").toLowerCase();
  const m = k.includes("live") ? "live" : k.includes("episode") ? "ep" : r.kc === KIND_READY ? "ready" : r.kc === KIND_WAY ? "way" : "";
  return m ? `<img class="vgmark" src="web/img/vg/vg_mark_${m}.png" alt="">` : "";
};
// Halloween look: each kind of notice gets its own mark (html.hw .brow small[data-hw]::before)
const hwMark = (kind) => { const k = String(kind || "").toLowerCase(); return k.startsWith("ready") || k.includes("is ready") ? "sweet" : k.startsWith("on the way") ? "spider" : k.startsWith("new") ? "moon" : "cauldron"; };
// Valentine's look: each kind of notice gets its own small mark (html.val .brow small::before)
const valMark = (kind) => { const k = String(kind || "").toLowerCase(); return k.startsWith("new") ? "choc" : k.startsWith("ready") ? "rose" : k.startsWith("on the way") ? "letter" : k.includes("live") ? "balloon" : "choc"; };
// St. Patrick's Day look: each kind of notice gets its own small mark (html.sp .brow small::before)
const spMark = (kind) => { const k = String(kind || "").toLowerCase(); return k.startsWith("ready") || k.includes("is ready") ? "pot" : k.startsWith("on the way") || k.includes("arrives") ? "rainbow" : k.startsWith("new") ? "horseshoe" : "hat"; };
const rowHtml = (r, n) => {
  const logo = `<span class="lgthumb"><i></i><i></i><i></i><i></i></span>`;
  return `<div class="bitem${r.kc === KIND_FACT ? " fact" : ""}">
    <${r.href ? `a href="${esc(r.href)}"` : `button type="button"`} class="brow" data-row="${n}">
    ${r.img ? `<span class="th" style="background-image:url('${esc(r.img)}')"></span>` : logo}
    <div class="tx"><small style="color:${r.kc}" data-vmark="${valMark(r.kind)}" data-mark="${nyMark(r.kind)}" data-hw="${hwMark(r.kind)}" data-pc="${B.pcMark(r.kind)}" data-ed="${B.edMark(r.kind)}" data-sp="${spMark(r.kind)}">${vgMark(r)}${esc(r.kind)}</small><b><i>${esc(r.title)}</i></b></div></${r.href ? "a" : "button"}>
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

// Christmas look: on from 1 to 26 December (New Year's has the 27th on) unless switched off in Settings › Look (this browser)
B.xmas = window.BenflixXmas;
B.xmasOff = () => { try { return localStorage.getItem("benflix.xmasOff") === "1"; } catch (e) { return false; } };
B.setXmasOff = (off) => { try { localStorage.setItem("benflix.xmasOff", off ? "1" : "0"); } catch (e) {} };
B.xmasOn = () => !!B.xmas && B.xmas.season(new Date()) && !B.xmasOff() && !B.f13On();
B.xmasGreeting = () => (new Date().getDate() === 24 ? "Merry Christmas Eve" : "Merry Christmas");
// Thanksgiving look: on from 16 to 30 November unless switched off in Settings › Look (this browser)
B.tg = window.BenflixFeast;
B.tgOff = () => { try { return localStorage.getItem("benflix.tgOff") === "1"; } catch (e) { return false; } };
B.setTgOff = (off) => { try { localStorage.setItem("benflix.tgOff", off ? "1" : "0"); } catch (e) {} };
B.tgOn = () => !!B.tg && B.tg.season(new Date()) && !B.tgOff() && !B.f13On();
// Halloween look: on from 1 to 31 October unless switched off in Settings › Look (this browser)
B.hw = window.BenflixHalloween;
B.hwOff = () => { try { return localStorage.getItem("benflix.hwOff") === "1"; } catch (e) { return false; } };
B.setHwOff = (off) => { try { localStorage.setItem("benflix.hwOff", off ? "1" : "0"); } catch (e) {} };
B.hwOn = () => !!B.hw && B.hw.season(new Date()) && !B.hwOff() && !B.f13On();
// Valentine's look: on from 1 to 14 February unless switched off in Settings › Look (this browser), not on Kids
B.val = window.BenflixValentine;
B.valOff = () => { try { return localStorage.getItem("benflix.valOff") === "1"; } catch (e) { return false; } };
B.setValOff = (off) => { try { localStorage.setItem("benflix.valOff", off ? "1" : "0"); } catch (e) {} };
B.valOn = () => !!B.val && B.val.season(new Date()) && !B.valOff() && !B.f13On();
// New Year's look: on from 27 December to 1 January unless switched off in Settings › Look (this browser), Kids too
B.ny = window.BenflixNewYear;
B.nyOff = () => { try { return localStorage.getItem("benflix.nyOff") === "1"; } catch (e) { return false; } };
B.setNyOff = (off) => { try { localStorage.setItem("benflix.nyOff", off ? "1" : "0"); } catch (e) {} };
B.nyOn = () => !!B.ny && B.ny.season(new Date()) && !B.nyOff() && !B.f13On();
// Easter look: Monday to Easter Sunday (worked out each year), Kids too, unless switched off in Settings › Look (this browser)
B.ea = window.BenflixEaster;
B.eaOff = () => { try { return localStorage.getItem("benflix.eaOff") === "1"; } catch (e) { return false; } };
B.setEaOff = (off) => { try { localStorage.setItem("benflix.eaOff", off ? "1" : "0"); } catch (e) {} };
B.eaOn = () => !!B.ea && B.ea.season(new Date()) && !B.eaOff() && !B.f13On();
/** Easter look: which small mark a notice gets (egg, chick when something is ready, paw prints on the way, tulip for live) */
B.eaMark = (title) => {
  const k = String(title || "").toLowerCase();
  if (k.includes("ready")) return "ready";
  if (k.includes("on the way") || k.includes("arrives")) return "way";
  if (k.includes("live")) return "live";
  return "ep";
};
// Earth Day look: Earth week (22 to 28 April, or after Easter week) unless switched off in Settings › Look (this browser), Kids too
B.ed = window.BenflixEarth;
B.edOff = () => { try { return localStorage.getItem("benflix.edOff") === "1"; } catch (e) { return false; } };
B.setEdOff = (off) => { try { localStorage.setItem("benflix.edOff", off ? "1" : "0"); } catch (e) {} };
B.edOn = () => !!B.ed && B.ed.season(new Date()) && !B.edOff();
// the mark for each kind of Home news (html.ed .brow small[data-ed]): sprout = new, flower = ready, bud = on the way, Earth = the rest
B.edMark = (kind) => { const k = String(kind || "").toLowerCase(); return k.includes("ready") || k.includes("fixed") ? "bloom" : /way|waiting|search|almost|coming|arrives/.test(k) ? "bud" : /new|sent|from /.test(k) ? "sprout" : "globe"; };
// Video Games Day look: on 12 September (Kids too) unless switched off in Settings › Look (this browser)
B.vg = window.BenflixArcade;
B.vgOff = () => { try { return localStorage.getItem("benflix.vgOff") === "1"; } catch (e) { return false; } };
B.setVgOff = (off) => { try { localStorage.setItem("benflix.vgOff", off ? "1" : "0"); } catch (e) {} };
B.vgOn = () => !!B.vg && B.vg.season(new Date()) && !B.vgOff();
// 4th of July look: on from 1 to 4 July unless switched off in Settings › Look (this browser), Kids too
B.j4 = window.BenflixJuly4;
B.j4Off = () => { try { return localStorage.getItem("benflix.j4Off") === "1"; } catch (e) { return false; } };
B.setJ4Off = (off) => { try { localStorage.setItem("benflix.j4Off", off ? "1" : "0"); } catch (e) {} };
B.j4On = () => !!B.j4 && B.j4.season(new Date()) && !B.j4Off();
// Star Wars Day look: 4 May only, Kids too, unless switched off in Settings › Look (this browser)
B.sw = window.BenflixStarWars;
B.swOff = () => { try { return localStorage.getItem("benflix.swOff") === "1"; } catch (e) { return false; } };
B.setSwOff = (off) => { try { localStorage.setItem("benflix.swOff", off ? "1" : "0"); } catch (e) {} };
B.swOn = () => !!B.sw && B.sw.season(new Date()) && !B.swOff();
// Each profile picks a side on Star Wars Day: "light" (blue laser swords) or "dark" (red), kept in this browser
B.swSides = () => { try { return JSON.parse(localStorage.getItem("benflix.swSides") || "{}") || {}; } catch (e) { return {}; } };
B.swSide = (id) => (B.swSides()[id] === "dark" ? "dark" : "light");
B.setSwSide = (id, side) => { try { const s = B.swSides(); s[id] = side; localStorage.setItem("benflix.swSides", JSON.stringify(s)); } catch (e) {} };
// The opening crawl, the first time Benflix opens in this browser on 4 May. Any key, click or tap skips it.
B.swCrawl = () => new Promise((done) => {
  const day = new Date().toDateString();
  let seen = null; try { seen = localStorage.getItem("benflix.swCrawl"); } catch (e) {}
  if (!B.swOn() || seen === day) return done();
  try { localStorage.setItem("benflix.swCrawl", day); } catch (e) {}
  const ov = document.createElement("div");
  ov.className = "swcrawl";
  ov.innerHTML = `<div class="intro">A short time ago, on a Benflix not so far away….</div><div class="big">MAY THE 4TH</div>
    <div class="crawlbox"><div class="crawltext"><p class="ep">Episode IV</p><p class="ti">A NEW NIGHT IN</p><p>It is a time of popcorn. Brave viewers, settled on their couch, have won their first free evening in weeks.</p><p>During the battle for the remote, the family found every film of the saga, waiting on Benflix.</p><p>Pursued by snacks and blankets, everyone races home to press Play….</p></div></div>`;
  document.body.appendChild(ov);
  let over = false;
  const end = () => { if (over) return; over = true; removeEventListener("keydown", skip, true); ov.remove(); done(); };
  const skip = (e) => { e.preventDefault(); e.stopPropagation(); end(); };
  addEventListener("keydown", skip, true);
  ov.addEventListener("pointerdown", end);
  setTimeout(end, 13200);
});
// Popcorn Day look: just 19 January unless switched off in Settings › Look (this browser), Kids too
B.pc = window.BenflixPopcorn;
B.pcOff = () => { try { return localStorage.getItem("benflix.pcOff") === "1"; } catch (e) { return false; } };
B.setPcOff = (off) => { try { localStorage.setItem("benflix.pcOff", off ? "1" : "0"); } catch (e) {} };
B.pcOn = () => !!B.pc && B.pc.season(new Date()) && !B.pcOff();
// the mark for each kind of Home news (html.pc .brow small[data-pc]): ticket, bucket, kernel or film reel
B.pcMark = (kind) => { const k = String(kind || "").toLowerCase(); return k.startsWith("new") ? "ticket" : k.startsWith("ready") || k.includes("is ready") ? "bucket" : k.startsWith("on the way") || k.includes("arrives") ? "kernel" : k.includes("live") ? "reel" : "puff_hull"; };
// Friday the 13th look: only on a Friday the 13th, midnight to midnight, unless switched off in Settings › Look
// (this browser). It takes over any season look that day, and Kids gets it too.
B.f13 = window.BenflixF13;
B.f13Off = () => { try { return localStorage.getItem("benflix.f13Off") === "1"; } catch (e) { return false; } };
B.setF13Off = (off) => { try { localStorage.setItem("benflix.f13Off", off ? "1" : "0"); } catch (e) {} };
B.f13On = () => !!B.f13 && B.f13.season(new Date()) && !B.f13Off();
B.f13Lucky = (eps, ready) => {
  const parts = [eps ? `${eps} new episode${eps === 1 ? "" : "s"}` : "", ready ? `${ready} request${ready === 1 ? "" : "s"} ready` : ""].filter(Boolean);
  return parts.length ? `Lucky you: ${parts.join(" and ")}` : "Lucky you: the couch is a black-cat-free zone";
};

// St. Patrick's Day look: on from 10 to 17 March unless switched off in Settings › Look (this browser), Kids too
B.sp = window.BenflixStPatricks;
B.spOff = () => { try { return localStorage.getItem("benflix.spOff") === "1"; } catch (e) { return false; } };
B.setSpOff = (off) => { try { localStorage.setItem("benflix.spOff", off ? "1" : "0"); } catch (e) {} };
B.spOn = () => !!B.sp && B.sp.season(new Date()) && !B.spOff();

// Big holidays only, clean and simple
B.holiday = () => {
  const d = new Date(), md = (d.getMonth() + 1) * 100 + d.getDate();
  if (md === 1225) return "Merry Christmas";
  if (md === 101) return "Happy New Year";
  if (md === 1031) return "Happy Halloween";
  if (md === 704) return "Happy 4th of July";
  return "";
};

// ---------- profile menu ----------
// Computers: it slides out beside the rail like a door's panel (js/nav.js B.openMePanel). Phone-sized windows and
// pages without the rail: the corner card. Requests isn't listed: it has its own spot on the rail and in More.
B.profileItems = () => [
  ["#/mine", "chart", "My watching"],
  B.lobby && B.lobby.allowed() ? ["#/lobby", "tv", "Lobby: watch together"] : null,
  ["#/settings", "gear", "Settings"],
  B.me && B.me.admin ? ["#/server", "server", "Server"] : null,
  ["#/help", "help", "Help and updates"],
  ["report", "flag", "Report a problem"],
].filter(Boolean);
// Where a profile menu line goes (shared by the slide-out and the corner card)
B.profileGo = (where) => {
  B.closeAll();
  if (where === "signout") return B.signOut();
  if (where === "report") return B.report();
  B.go(where);
};
B.openMenu = async (byKeys) => {
  if (!B.me) return;
  if (B.me.kids) return B.go("#/who");
  if (B.openMePanel && B.openMePanel(byKeys)) return;
  B.closeAll();
  B.shade();
  const c = document.createElement("div");
  c.className = "menu-card";
  const items = B.profileItems();
  c.innerHTML = `<div class="left">${B.picHtml(B.me, "", 220)}<div><div class="nm">${esc(B.me.name)}</div><div class="wt" id="wt">&nbsp;</div></div><button class="btn small" data-go="#/who">${I("swap")} Switch profile</button></div>
    <div class="list">${items.map(([href, icon, label]) => `<button data-go="${href}">${I(icon)}${esc(label)}</button>`).join("")}
      <div class="sep"></div><button class="out" data-go="signout">${I("out")}Sign out</button></div>`;
  document.body.appendChild(c);
  $("button[data-go]", c).focus();
  c.onclick = (e) => {
    const b = e.target.closest("[data-go]"); if (!b) return;
    B.profileGo(b.dataset.go);
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
  // Star Wars Day: the opening crawl first (once on 4 May)
  await B.swCrawl();
  if (!location.hash || location.hash === "#/" || location.hash === "#/who") {
    if (B.profiles().length) {
      history.replaceState(null, "", "#/who");
      await B.intro();
    }
  }
  B.render();
};
document.addEventListener("DOMContentLoaded", B.start);
