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
};
const EJS_DATA = "https://cdn.emulatorjs.org/stable/data/";
B.gamesList = () => B.cached("games", 300000, () => B.call("GET", "api/games"));
const cover = (g) => (g.cover ? `${g.cover}?api_key=${enc(B.me.token)}` : "");
const playableHere = (c) => !!EJS_CORES[c.slug];
const gameCard = (g, sub) => {
  B.items["g" + g.id] = g;
  return `<a class="poster" href="#/game/${g.id}" data-id="g${g.id}" title="${esc(g.name)}"><div class="im" ${cover(g) ? `style="background-image:url('${cover(g)}')"` : ""}>${cover(g) ? "" : `<div class="noart">${esc(g.name)}</div>`}</div><div class="cap">${esc(g.name)}<small>${esc(sub || g.console || "")}</small></div></a>`;
};
const favKey = () => "benflix.gamefavs." + B.me.id;
const favs = () => loadJson(favKey(), []);

const describeGame = (g) => `<div class="t">${esc(g.name)}</div><div class="meta">${[g.console, g.year || "", (g.genres || []).join(", ")].filter(Boolean).map((x) => `<span>${esc(x)}</span>`).join("")}</div><div class="ov">Plays in your browser. Use the keyboard or plug in a controller.</div>`;

B.route(/^\/games$/, async (m, nav) => {
  B.page().innerHTML = B.top("Games", DOORS.games.color) + `<section class="door-top" id="gTop" style="height:auto;min-height:250px;padding-bottom:16px"><div class="art"></div><div class="info" id="gInfo"></div></section><div class="rows" id="rows">${B.spinner()}</div>`;
  B.wireTop();
  let data, recent;
  try { [data, recent] = await Promise.all([B.gamesList(), B.call("GET", "api/saves/recent").catch(() => ({ games: [] }))]); }
  catch (e) { $("#gTop").remove(); $("#rows").innerHTML = `<div class="wrap" style="padding-top:24px"><div class="note">${esc(e.message)}</div><button class="btn white" onclick="B.render()">Try again</button></div>`; return; }
  if (!B.alive(nav)) return;
  const consoles = data.consoles.filter(playableHere).map((c) => Object.assign({}, c, { games: c.games.map((g) => Object.assign({ console: c.name, slug: c.slug }, g)) }));
  const all = consoles.flatMap((c) => c.games);
  const byId = {}; all.forEach((g) => { byId[g.id] = g; });
  const text = (B.q("q") || "").toLowerCase();
  const cont = (recent.games || []).map((r) => byId[r.id]).filter(Boolean);
  const fav = favs().map((id) => byId[id]).filter(Boolean);
  const arcade = consoles.filter((c) => EJS_CORES[c.slug] === "arcade").flatMap((c) => c.games);
  $("#rows").innerHTML = text ? B.row(`Games matching “${B.q("q")}”`, all.filter((g) => g.name.toLowerCase().includes(text)).slice(0, 60).map((g) => gameCard(g))) || `<div class="wrap empty">No games match that.</div>` : [
    B.row("Continue playing", cont.map((g) => gameCard(g))),
    B.row("Favourites", fav.map((g) => gameCard(g))),
    `<section class="rowx"><h3>Consoles</h3><div class="tiles2">${consoles.map((c) => `<a class="tile2" href="#/console/${enc(c.slug)}" style="flex-direction:column;align-items:flex-start;justify-content:flex-end">${esc(c.name)}<small class="dim" style="font-size:14px;font-weight:600">${c.games.length} games</small></a>`).join("") || '<div class="empty">No consoles that play in a browser yet.</div>'}</div></section>`,
    B.row("Arcade", arcade.slice(0, 30).map((g) => gameCard(g))),
    ...consoles.filter((c) => EJS_CORES[c.slug] !== "arcade").slice(0, 6).map((c) => B.row(c.name, c.games.slice(0, 24).map((g) => gameCard(g, c.year)), { seeAll: `#/console/${enc(c.slug)}` })),
    `<div class="wrap dim" style="font-size:14px">Benflix's own games (puzzles, party games and Ember Road) are on the TV and phone app.</div>`,
  ].join("");
  const first = cont[0] || fav[0] || all[0];
  if (first) $("#gInfo").innerHTML = describeGame(first);
  const point = (e) => { const c = e.target.closest("[data-id^='g']"); if (!c) return; const g = B.items[c.dataset.id]; if (g) $("#gInfo").innerHTML = describeGame(g); };
  B.page().addEventListener("mouseover", point);
  B.page().addEventListener("focusin", point);
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
    <div class="gridbar" style="--accent:${DOORS.games.color}"><input class="search-in" id="cq" placeholder="Search ${esc(c.name)}"></div><div class="grid-wrap"><div class="grid-body" id="cg"></div></div>`;
  B.wireTop();
  const draw = (t) => {
    let html = "", last = null;
    games.filter((g) => !t || g.name.toLowerCase().includes(t)).forEach((g) => {
      const h = /[a-z]/i.test(g.name[0]) ? g.name[0].toUpperCase() : "#";
      if (h !== last) { html += (last ? "</div>" : "") + `<div class="grid-head">${h}</div><div class="grid">`; last = h; }
      html += gameCard(g, g.year || "");
    });
    $("#cg").innerHTML = html ? html + "</div>" : `<div class="empty">No games match that.</div>`;
  };
  $("#cq").oninput = (e) => draw(e.target.value.toLowerCase());
  draw("");
});

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
  B.page().innerHTML = B.top("Games", DOORS.games.color) + `<div class="details"><div class="hero" style="min-height:76vh;background:linear-gradient(90deg,var(--tray) 40%,rgba(11,12,16,.6)),url('${cover(g)}') right center/contain no-repeat"><div class="hero-in"><div class="main">
    <h1 class="t">${esc(g.name)}</h1><div class="meta">${[c.name, info.year || g.year || "", (info.genres || g.genres || []).join(", "), info.players ? `${info.players} players` : ""].filter(Boolean).map((x) => `<span>${esc(x)}</span>`).join("")}</div>
    <div class="ov">${esc(info.summary || "")}</div>
    <div class="dim" style="font-size:15px;margin-top:6px">Arrow keys move. Z and X are the main buttons, Enter is Start. A plugged-in controller works too.</div>
    <div class="btns">${ok ? `<a class="btn white big" href="#/playgame/${id}">${I("play")} ${saves.srm || saves.state ? "Keep playing" : "Play"}</a>` : `<span class="note">This console doesn't play in a browser. Play it on the TV or phone.</span>`}
      <button class="btn big" id="gFav">${I(isFav ? "check" : "star")} Favourite</button></div></div>
    <div class="side-col">${(info.companies || []).length ? `<div><b>Made by</b><span>${esc(info.companies.join(", "))}</span></div>` : ""}<div><b>Saves</b><span>${saves.srm ? "In-game save on Tower" : "No save yet"}</span></div>${g.size ? `<div><b>Size</b><span>${(g.size / 1048576).toFixed(1)} MB</span></div>` : ""}</div>
  </div></div></div>`;
  B.wireTop();
  $("#gFav").onclick = () => {
    const l = favs(), i = l.indexOf(id);
    i >= 0 ? l.splice(i, 1) : l.unshift(id);
    store(favKey(), JSON.stringify(l));
    $("#gFav").innerHTML = `${I(i >= 0 ? "star" : "check")} Favourite`;
  };
});

// ---------- playing ----------
B.route(/^\/playgame\/(\d+)/, async (m, nav) => {
  // Starting a movie, show, channel or game stops the music
  if (B.music) B.music.stop();
  const id = +m[1];
  const [data, files, saves, bios] = await Promise.all([B.gamesList(), B.call("GET", `api/rom/${id}`), B.call("GET", `api/save/${id}`).catch(() => ({})), B.call("GET", "api/bios").catch(() => ({ files: [] }))]);
  if (!B.alive(nav)) return;
  let g = null, c = null;
  data.consoles.forEach((x) => { const f = x.games.find((y) => y.id === id); if (f) { g = f; c = x; } });
  const core = c && EJS_CORES[c.slug];
  if (!g || !core) return B.go(`#/game/${id}`);
  if (files.files.length > 1 && !/\.(m3u|chd|pbp|iso|zip|7z)$/i.test(files.start)) {
    B.page().innerHTML = B.top("Games", DOORS.games.color) + `<div class="center"><div><div class="h1">This one needs the TV or phone</div><p class="sub">${esc(g.name)} is made of several files, which the browser can't load yet.</p><a class="btn white" href="#/game/${id}">Back</a></div></div>`;
    return B.wireTop();
  }
  const key = enc(B.me.token);
  const biosFile = core === "psx" ? (bios.files || []).find((b) => /^scph(5501|1001|5500|5502)\.bin$/i.test(b.name)) : core === "gba" ? (bios.files || []).find((b) => /^gba_bios\.bin$/i.test(b.name)) : null;
  B.page().innerHTML = `<div class="emu"><div id="game"></div><button class="btn quit" id="quit">${I("x")} Quit and save</button></div>`;
  Object.assign(window, {
    EJS_player: "#game", EJS_core: core, EJS_gameName: g.name, EJS_color: "#FF6F91", EJS_startOnLoaded: true, EJS_pathtodata: EJS_DATA,
    EJS_gameUrl: `api/rom/${id}/file?name=${enc(files.start)}&api_key=${key}`,
    EJS_biosUrl: biosFile ? `api/bios/file?path=${enc(biosFile.path)}&api_key=${key}` : "",
    EJS_defaultOptions: { "save-state-location": "browser" },
  });
  // Put the shared in-game save in place once the game has started
  window.EJS_onGameStart = async () => {
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
