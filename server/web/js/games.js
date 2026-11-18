/* Benflix web: the Games door. Your RomM console games, played right in the browser with
   EmulatorJS. In-game saves are shared with the TV and phone (kept on Tower per person). */
"use strict";

// RomM console folder -> EmulatorJS system
const EJS_CORES = {
  nes: "nes", famicom: "nes", snes: "snes", sfam: "snes", n64: "n64", gb: "gb", gbc: "gb", gba: "gba", nds: "nds",
  genesis: "segaMD", megadrive: "segaMD", "genesis-slash-megadrive": "segaMD", sms: "segaMS", gamegear: "segaGG", segacd: "segaCD", sega32: "sega32x",
  psx: "psx", saturn: "segaSaturn", tg16: "pce", pce: "pce", "turbografx16--1": "pce", "turbografx-16": "pce", "pc-engine": "pce", pcengine: "pce",
  atari2600: "atari2600", "atari-2600": "atari2600", atari7800: "atari7800", "atari-7800": "atari7800", lynx: "lynx", "atari-lynx": "lynx",
  ngp: "ngp", ngpc: "ngp", "neo-geo-pocket": "ngp", "neo-geo-pocket-color": "ngp", ws: "ws", wsc: "ws", wonderswan: "ws", "wonderswan-color": "ws",
  virtualboy: "vb", "virtual-boy": "vb", arcade: "arcade", fbneo: "arcade", mame: "arcade", cps1: "arcade", cps2: "arcade", cps3: "arcade", neogeoaes: "arcade", neogeomvs: "arcade",
  coleco: "coleco", colecovision: "coleco", "3do": "3do",
  // Other folder names the TV and phone app know (games/Consoles.kt)
  fds: "nes", "sega-cd": "segaCD", ps1: "psx", playstation: "psx", a2600: "atari2600", 2600: "atari2600", a7800: "atari7800", 7800: "atari7800",
  atarilynx: "lynx", neogeopocket: "ngp", neogeopocketcolor: "ngp", wonderswancolor: "ws", vb: "vb", "neo-geo": "arcade", "nintendo-64": "n64", nintendo64: "n64",
  turbografx16: "pce", "tg-cd": "pce", "pce-cd": "pce", pcecd: "pce",
};
// Benflix: whether this console's games play in the browser (Search uses it too)
B.webPlayable = (slug) => !!EJS_CORES[slug];
// Benflix security: one fixed version of the game player, never "whatever is newest today", so a change on
// their website can't reach Benflix by itself. To update: check the newest at https://www.npmjs.com/package/@emulatorjs/emulatorjs
const EJS_DATA = "https://cdn.emulatorjs.org/4.2.3/data/";
B.gamesList = () => B.cached("games", 300000, () => B.call("GET", "api/games"));
const cover = (g) => (g.cover ? `${g.cover}?api_key=${enc(B.me.token)}` : "");
const playableHere = (c) => !!EJS_CORES[c.slug];
const gameCard = (g, sub) => {
  B.items["g" + g.id] = g;
  return `<a class="poster gm" href="#/game/${g.id}" data-id="g${g.id}" title="${esc(g.name)}"><div class="im" style="position:relative">${g.players >= 2 ? `<span class="pbadge" style="position:absolute;top:6px;left:6px;z-index:1;background:#fff;color:#000;font-size:12px;padding:2px 8px">${g.players}P</span>` : ""}${cover(g) ? `<img class="art" src="${cover(g)}" alt="" loading="lazy" decoding="async">` : `<div class="noart" style="--door:var(--games)"><span class="nt">${esc(g.name)}</span></div>`}</div><div class="cap"><span class="nm">${esc(g.name)}</span><small>${esc(sub || g.console || "")}</small></div></a>`;
};
const favKey = () => "benflix.gamefavs." + B.me.id;
const favs = () => loadJson(favKey(), []);

// Glowfin G1 (Ben's pick, Nov 2026): the same word tabs as Movies and Music: For you | Consoles | Movie games |
// Party | Scores. For you = the top area (the game you're on and what it works with), then Continue playing,
// Movie puzzles, Favourites and Arcade. Each other tab holds one kind of game.
const GAME_PLACES = ["home", "consoles", "movie", "party", "scores"];
const worksWith = (remote) => `<span class="gw-chips">${[remote ? "Remote" : "", "Controller", "Phone"].filter(Boolean).map((x) => `<span class="gw-chip">${x}</span>`).join("")}</span>`;
const describeGame = (g, kick) => `<div class="kick">${esc(kick || [g.console, g.year || ""].filter(Boolean).join(" · "))}</div><div class="t">${esc(g.name)}</div><div class="ov">Plays in your browser. Use the keyboard or plug in a controller.</div>${worksWith(false)}`;

B.route(/^\/games(?:\?.*)?$/, async (m, nav) => {
  const place = GAME_PLACES.includes(B.q("p")) ? B.q("p") : "home";
  const home = place === "home";
  B.page().innerHTML = B.top("Games", DOORS.games.color) + (home ? `<section class="door-top gtop" id="gTop"><div class="art"></div><div class="info" id="gInfo"></div></section>` : "") + `<div class="rows gpage" id="rows">${B.spinner()}</div>`;
  B.wireTop();
  const rows = $("#rows");
  // Glowfin's own games (puzzles, party games, arcade, Ember Road) don't need RomM
  if (place === "movie" || place === "party" || place === "scores") {
    if (!B.bgRows) { rows.innerHTML = ""; return; }
    try {
      rows.innerHTML = place === "scores" ? await B.bgScores() : await B.bgRows(place === "movie" ? ["puzzles", "arcade", "ember"] : ["party"]);
    } catch (e) { rows.innerHTML = `<div class="wrap empty">${esc(e.message)}</div>`; }
    if (place === "party") rows.insertAdjacentHTML("beforeend", `<div class="wrap"><p class="dim" style="max-width:62ch">Party games run on the TV. Everyone opens Glowfin on their Android phone, goes to Games and presses Join a party, then answers on the phone. 3 to 8 players.</p></div>`);
    if (B.bgWire) B.bgWire(rows, null);
    return;
  }
  let data, recent;
  try { [data, recent] = await Promise.all([B.gamesList(), B.call("GET", "api/saves/recent").catch(() => ({ games: [] }))]); }
  catch (e) {
    if (!B.alive(nav)) return;
    // For you still has Glowfin's own games when the console games can't load
    const own = home && B.bgRows ? await B.bgRows(["puzzles", "arcade"]).catch(() => "") : "";
    rows.innerHTML = `<div class="wrap" style="padding-top:24px"><div class="note">${esc(e.message)}</div><button class="btn white" onclick="B.render()">Try again</button></div>` + own;
    if (own && B.bgWire) B.bgWire(rows, $("#gInfo"));
    return;
  }
  if (!B.alive(nav)) return;
  const consoles = data.consoles.filter(playableHere).map((c) => Object.assign({}, c, { games: c.games.map((g) => Object.assign({ console: c.name, slug: c.slug }, g)) }));
  const all = consoles.flatMap((c) => c.games);
  const byId = {}; all.forEach((g) => { byId[g.id] = g; });
  const text = (B.q("q") || "").toLowerCase();
  const cont = (recent.games || []).map((r) => byId[r.id]).filter(Boolean);
  const fav = favs().map((id) => byId[id]).filter(Boolean);
  if (text) {
    rows.innerHTML = B.row(`Games matching “${B.q("q")}”`, all.filter((g) => g.name.toLowerCase().includes(text)).slice(0, 60).map((g) => gameCard(g))) || `<div class="wrap empty">No games match that.</div>`;
    return;
  }
  if (home) {
    const own = B.bgRows ? await B.bgRows(["puzzles"]).catch(() => "") : "";
    const arc = B.bgRows ? await B.bgRows(["arcade"]).catch(() => "") : "";
    if (!B.alive(nav)) return;
    rows.innerHTML = [
      B.row("Continue playing", cont.map((g) => gameCard(g, "Continue playing"))),
      own,
      B.row("Favourites", fav.map((g) => gameCard(g))),
      arc,
    ].join("");
    if (B.bgWire) B.bgWire(rows, $("#gInfo"));
    const first = cont[0] || fav[0];
    if (first) $("#gInfo").innerHTML = describeGame(first, "Continue playing · " + first.console);
  } else {
    const arcade = consoles.filter((c) => EJS_CORES[c.slug] === "arcade").flatMap((c) => c.games);
    rows.innerHTML = [
      `<section class="rowx"><h3>Consoles</h3><div class="tiles2">${consoles.map((c) => `<a class="tile2" href="#/console/${enc(c.slug)}" style="flex-direction:column;align-items:flex-start;justify-content:flex-end"><span class="tname">${esc(c.name)}</span><small class="dim" style="font-size:14px;font-weight:600">${c.games.length} games</small></a>`).join("") || '<div class="empty">No consoles that play in a browser yet.</div>'}</div></section>`,
      all.length > 1 ? `<div class="wrap"><button class="btn" id="gSpin" style="white-space:normal;max-width:100%">${I("pad")} Game roulette <span class="dim" style="font-weight:500">· spin for a random game</span></button></div>` : "",
      // Idea Box 25: the best-loved games two or more can play at once
      B.row("Games for two", all.filter((g) => g.players >= 2).sort((a, b) => (b.rating || 0) - (a.rating || 0)).slice(0, 20).map((g) => gameCard(g))),
      B.row("Arcade machines", arcade.slice(0, 30).map((g) => gameCard(g))),
    ].join("");
    if ($("#gSpin")) $("#gSpin").onclick = () => B.gameRoulette(all);
  }
  const point = (e) => { const c = e.target.closest("[data-id^='g']"); if (!c || !$("#gInfo")) return; const g = B.items[c.dataset.id]; if (g) $("#gInfo").innerHTML = describeGame(g); };
  B.page().addEventListener("mouseover", point);
  B.page().addEventListener("focusin", point);
  B.onLeave(() => { B.page().removeEventListener("mouseover", point); B.page().removeEventListener("focusin", point); });
});

// ---------- one console: A to Z grid with a search box ----------
B.route(/^\/console\/([^?]+)/, async (m, nav) => {
  const slug = decodeURIComponent(m[1]);
  const data = await B.gamesList();
  if (!B.alive(nav)) return;
  const c = data.consoles.find((x) => x.slug === slug);
  if (!c) return B.go("#/games");
  const games = c.games.map((g) => Object.assign({ console: c.name, slug }, g));
  B.page().innerHTML = B.top("Games", DOORS.games.color) + `<div class="wrap" style="padding-bottom:0"><div class="h1">${esc(c.name)}</div></div>
    <div class="gridbar" style="--accent:${DOORS.games.color}"><input class="search-in" id="cq" placeholder="Search ${esc(c.name)}">${games.some((g) => g.players >= 2) ? `<button class="chip" id="cTwo">2 players</button>` : ""}</div><div class="grid-wrap"><div class="grid-body" id="cg"></div></div>`;
  B.wireTop();
  const draw = (t) => {
    let html = "", last = null;
    games.filter((g) => (!t || g.name.toLowerCase().includes(t)) && (!twoOnly || g.players >= 2)).forEach((g) => {
      const h = /[a-z]/i.test(g.name[0]) ? g.name[0].toUpperCase() : "#";
      if (h !== last) { html += (last ? "</div>" : "") + `<div class="grid-head">${h}</div><div class="grid">`; last = h; }
      html += gameCard(g, g.year || "");
    });
    $("#cg").innerHTML = html ? html + "</div>" : `<div class="empty">No games match that.</div>`;
  };
  let twoOnly = false;
  if ($("#cTwo")) $("#cTwo").onclick = (e) => { twoOnly = !twoOnly; e.currentTarget.classList.toggle("on", twoOnly); draw($("#cq").value.toLowerCase()); };
  $("#cq").oninput = (e) => { $("#cBest") && ($("#cBest").hidden = !!e.target.value); draw(e.target.value.toLowerCase()); };
  draw("");
  // "Best on <console>": the 10 best-loved games, from RomM's ratings (only when at least 5 are rated)
  const rated = games.filter((g) => (g.rating || 0) > 0);
  if (rated.length >= 5) {
    const best = rated.sort((a, b) => b.rating - a.rating || a.name.localeCompare(b.name)).slice(0, 10);
    $(".gridbar").insertAdjacentHTML("beforebegin", `<div id="cBest">${B.row(`Best on ${c.name}`, best.map((g) => gameCard(g, g.year || "")))}</div>`);
  }
});

// ---------- Game roulette: a slot-machine roll through your games that slows down and lands on one ----------
B.gameRoulette = (all) => {
  const p = B.side(`<h3>Game roulette</h3><p class="dim">Rolling through your games…</p><div class="roulette" id="rl"></div><div class="acts" id="rlActs" style="margin-top:18px;visibility:hidden">
    <a class="btn white" id="rlPlay">${I("play")} Play</a><a class="btn" id="rlLook">${I("info")} Look at it</a><button class="btn" id="rlAgain">Spin again</button></div>`);
  const spin = () => {
    $("#rlActs", p).style.visibility = "hidden";
    let n = 0, wait = 50;
    const steps = 22 + Math.floor(Math.random() * 8);
    const tick = () => {
      if (!document.body.contains(p)) return;
      const g = all[Math.floor(Math.random() * all.length)];
      $("#rl", p).innerHTML = `<div class="rl-card ${n >= steps ? "landed" : ""}"><span class="pic" style="${cover(g) ? `background-image:url('${cover(g)}')` : ""}"></span><b>${esc(g.name)}</b><small class="dim">${esc([g.console, g.year].filter(Boolean).join(" · "))}</small></div>`;
      if (n++ < steps) { wait *= 1.12; setTimeout(tick, wait); return; }
      $("#rlPlay", p).href = `#/playgame/${g.id}`;
      $("#rlLook", p).href = `#/game/${g.id}`;
      $("#rlActs", p).style.visibility = "visible";
      $("#rlPlay", p).focus();
    };
    tick();
  };
  p.addEventListener("click", (e) => { if (e.target.closest("#rlAgain")) spin(); if (e.target.closest("#rlPlay,#rlLook")) B.closeAll(); });
  spin();
};

// ---------- a game's own page ----------
B.route(/^\/game\/(\d+)/, async (m, nav) => {
  const id = +m[1];
  const [data, info, saves] = await Promise.all([B.gamesList(), B.call("GET", `api/info/${id}`).catch(() => ({})), B.call("GET", `api/save/${id}`).catch(() => ({}))]);
  if (!B.alive(nav)) return;
  let g = null, c = null;
  data.consoles.forEach((x) => { const f = x.games.find((y) => y.id === id); if (f) { g = f; c = x; } });
  if (!g) return B.go("#/games");
  const isFav = favs().includes(id);
  const ok = !!EJS_CORES[c.slug];
  // Glowfin G1 game page: movie-page rows. Keep playing first, then Saves, the heart and More; what it works with
  // under the buttons; Your saves (+ New game) and More like this underneath.
  const kept = saves.srm || saves.state;
  const genres = info.genres || g.genres || [];
  const like = c.games.filter((x) => x.id !== id && (x.genres || []).some((gg) => genres.includes(gg))).slice(0, 20).map((x) => Object.assign({ console: c.name, slug: c.slug }, x));
  const saveCards = kept ? `<div class="gsaves"><a class="gsave" href="#/playgame/${id}"><b>${saves.srm ? "In-game save" : "Where you stopped"}</b><small>On Tower · plays on any TV, phone or here</small></a></div>` : "";
  const tp = B.titlePage({
    title: g.name,
    color: DOORS.games.color,
    rows: true,
    kicker: c.name,
    cover: { url: cover(g), ratio: 3 / 4 },
    info: [info.year || g.year || "", genres.slice(0, 2).join(", "), info.players ? `${info.players} player${info.players > 1 ? "s" : ""}` : "1 player"],
    story: info.summary || "",
    status: "Works with: Keyboard · Controller",
    buttons: [
      { label: kept ? "Keep playing" : "Play", icon: "play", href: ok ? `#/playgame/${id}` : "", off: ok ? "" : "This console doesn't play in a browser. Play it on the TV or phone.", note: "Arrow keys move. Z and X are the main buttons, Enter is Start. A controller works too." },
      kept ? { label: "Saves", icon: "list", onClick: () => { const r = $("#tpSaves"); if (r) r.scrollIntoView({ behavior: "smooth", block: "center" }); } } : null,
      { id: "gFav", label: isFav ? "Favourite" : "Add to favourites", icon: isFav ? "check" : "heart", onClick: (e, el) => {
        const l = favs(), i = l.indexOf(id);
        i >= 0 ? l.splice(i, 1) : l.unshift(id);
        store(favKey(), JSON.stringify(l));
        el.innerHTML = `${I(i >= 0 ? "heart" : "check")} <span>${i >= 0 ? "Add to favourites" : "Favourite"}</span>`;
      } },
      { label: "More", icon: "more", onClick: () => B.tpMore(g.name, [
        { label: "See all " + c.name + " games", icon: "pad", href: `#/console/${enc(c.slug)}` },
        { label: "Back to Games", icon: "left", href: "#/games" },
      ]) },
    ].filter(Boolean),
    strip: B.tpStrip([
      (info.companies || []).length ? ["Made by", info.companies.slice(0, 2).join(", "), true] : null,
      g.size ? ["MB", (g.size / 1048576).toFixed(1)] : null,
    ]),
    tabs: [
      { name: "Your saves", id: "tpSaves", html: saveCards, skip: !saveCards },
      { name: "More like this", id: "tpLike", html: like.length ? `<div class="grid">${like.map((x) => gameCard(x)).join("")}</div>` : "", skip: !like.length },
    ],
  });
});

// ---------- playing ----------
B.route(/^\/playgame\/(\d+)/, async (m, nav) => {
  // Starting a movie, show, channel or game stops the music
  if (B.music) B.music.stop();
  if (B.kept) B.dropKept(); // a video still playing in the pop-out window stops
  const id = +m[1];
  const [data, files, saves, bios] = await Promise.all([B.gamesList(), B.call("GET", `api/rom/${id}`), B.call("GET", `api/save/${id}`).catch(() => ({})), B.call("GET", "api/bios").catch(() => ({ files: [] }))]);
  if (!B.alive(nav)) return;
  let g = null, c = null;
  data.consoles.forEach((x) => { const f = x.games.find((y) => y.id === id); if (f) { g = f; c = x; } });
  const core = c && EJS_CORES[c.slug];
  if (!g || !core) return B.go(`#/game/${id}`);
  // A game made of several files (a .cue with its .bin files, or several discs) comes from Tower as one .zip
  const many = files.files.length > 1 && !/\.(chd|pbp|iso|zip|7z)$/i.test(files.start);
  const key = enc(B.me.token);
  const biosFile = core === "psx" ? (bios.files || []).find((b) => /^scph(5501|1001|5500|5502)\.bin$/i.test(b.name)) : core === "gba" ? (bios.files || []).find((b) => /^gba_bios\.bin$/i.test(b.name)) : null;
  B.page().innerHTML = `<div class="emu"><div id="game"></div><button class="btn quit" id="quit">${I("x")} Quit and save</button></div>`;
  Object.assign(window, {
    EJS_player: "#game", EJS_core: core, EJS_gameName: g.name, EJS_color: "#FF6F91", EJS_startOnLoaded: true, EJS_pathtodata: EJS_DATA,
    EJS_gameUrl: many ? `api/rom/${id}/zip?api_key=${key}` : `api/rom/${id}/file?name=${enc(files.start)}&api_key=${key}`,
    EJS_biosUrl: biosFile ? `api/bios/file?path=${enc(biosFile.path)}&api_key=${key}` : "",
    EJS_defaultOptions: { "save-state-location": "browser" },
  });
  // "Didn't start" list (Idea Box 24): if the game hasn't started after 45 seconds, Tower hears about it
  let started = false;
  const tellTower = (what) => { if (!started) B.call("POST", "api/games/problems", { id, name: g.name, console: c.name, what, device: "Browser" }).catch(() => {}); };
  const noStart = setTimeout(() => { if (B.alive(nav)) tellTower("Didn't start in the browser"); }, 45000);
  B.onLeave(() => clearTimeout(noStart));
  // Put the shared in-game save in place once the game has started
  window.EJS_onGameStart = async () => {
    started = true; clearTimeout(noStart);
    if (!saves.srm) return;
    try {
      const buf = new Uint8Array(await (await fetch(`api/save/${id}/srm`, { headers: { "X-Benflix-Token": B.me.token } })).arrayBuffer());
      const gm = window.EJS_emulator.gameManager, path = gm.getSaveFilePath();
      const dir = path.split("/").slice(0, -1).join("/");
      try { gm.FS.mkdirTree ? gm.FS.mkdirTree(dir) : gm.FS.mkdir(dir); } catch (e) {}
      gm.FS.writeFile(path, buf);
      gm.loadSaveFiles();
      B.toast("Your save from Tower is loaded.");
    } catch (e) { console.warn("Couldn't load the save", e); }
  };
  const s = document.createElement("script");
  s.src = EJS_DATA + "loader.js";
  s.onerror = () => B.toast("Couldn't load the game player. Check the internet is working.");
  document.body.appendChild(s);
  let saved = false;
  const saveNow = async () => {
    if (saved) return; saved = true;
    try {
      const gm = window.EJS_emulator && window.EJS_emulator.gameManager;
      if (!gm) return;
      const data2 = gm.getSaveFile();
      if (data2 && data2.length) {
        await fetch(`api/save/${id}/srm?time=${Date.now()}`, { method: "PUT", headers: { "X-Benflix-Token": B.me.token, "Content-Type": "application/octet-stream" }, body: data2 });
        B.toast("Saved to Tower.");
      }
    } catch (e) { console.warn("Couldn't save", e); }
  };
  $("#quit").onclick = async () => { await saveNow(); B.go(`#/game/${id}`); };
  B.onLeave(() => {
    saveNow();
    try { window.EJS_emulator && window.EJS_emulator.callEvent && window.EJS_emulator.callEvent("exit"); } catch (e) {}
    // The emulator can't be cleanly unloaded, so start the page fresh next time
    s.remove();
    setTimeout(() => { if (!/playgame/.test(location.hash)) location.reload(); }, 300);
  });
});
