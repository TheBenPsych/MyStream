/* Benflix web: the Lobby (designed 2026-11-13, thread "Design a 3D watch-together lobby").
   A first-person 3D room. Everyone who joins shows up as a character in their profile colour, the big TV
   plays one movie or episode for everybody at the same moment, and you can walk to the pool table, sit on
   the sofa and chat. Three rooms: Living room, Home theater and Rooftop (the host picks).

   - Tower's room helper (server/benflix_lobby.py) keeps who's where, the chat, the TV and pool shots.
     Each browser sends its spot about 8 times a second and gets everyone else's back.
   - The movie: each browser streams its own copy from Tower (same as the normal player) and paints it onto
     the TV. Every few seconds it checks where the TV should be and gently catches up.
   - 3D drawing uses three.js (web/vendor/three.min.js), loaded only when a lobby opens.

   Everything lives inside this function or on B.lobby so nothing clashes with the other files. */
"use strict";

(() => {
  const L = (B.lobby = {});
  const ROOM_NAMES = { den: "Living room", theater: "Home theater", roof: "Rooftop" };
  const STYLE_NAMES = { pebble: "Pebble", facet: "Usher", glow: "Headphones" };
  const KIDS_RATINGS = ["G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG", "APPROVED"];
  L.kidsOk = (rating) => KIDS_RATINGS.includes(String(rating || "").toUpperCase());
  const api = (method, path, body) => B.call(method, "api/lobby/" + path, body);
  const lookKey = () => "benflix.lobby.look." + (B.me ? B.me.id : "");
  const myLook = () => Object.assign({ style: "pebble", color: B.picOf(B.me).color, quality: "auto", fullSound: false }, loadJson(lookKey(), {}));
  const saveLook = (l) => store(lookKey(), JSON.stringify(l));

  // ---------- opening a lobby (from a title page's More menu, or the Lobby page) ----------
  L.open = async (opts) => {
    opts = opts || {};
    const body = { room: opts.room };
    if (opts.item) Object.assign(body, { item: opts.item.Id, title: L.titleOf(opts.item), kids_ok: L.kidsOk(opts.item.OfficialRating), pos: opts.pos || 0 });
    const lob = await api("POST", "open", body);
    if (opts.item) sessionStorage.setItem("benflix.lobby.autoplay", lob.code);
    B.go("#/lobby/" + lob.code);
  };
  L.titleOf = (it) => (it.Type === "Episode" ? `${it.SeriesName} · S${it.ParentIndexNumber} E${it.IndexNumber} · ${it.Name}` : it.Name);

  // Home: a tap on the shoulder when someone else has a lobby open
  L.homeNudge = async (nav) => {
    const got = await api("GET", "list").catch(() => null);
    if (!got || !B.alive(nav)) return;
    got.lobbies.filter((l) => !l.mine && l.people.length).forEach((l) => B.corner({
      id: "lobby-" + l.code,
      title: `${l.host_name} opened the Lobby`,
      line: l.title ? `Watching ${l.title}` : `${l.people.length} inside`,
      href: "#/lobby/" + l.code,
    }));
  };

  // ---------- the Lobby page: open lobbies, open your own, or type a code ----------
  B.route(/^\/lobby$/, async (m, nav) => {
    B.page().innerHTML = B.top("Lobby") + `<div class="lb-list"><div class="h1">The Lobby</div>
      <p class="sub">A 3D room where everyone walks around together, watches the same thing on the big TV, plays pool and chats.</p>
      <div id="lbOpen">${B.spinner()}</div></div>`;
    B.wireTop();
    const got = await api("GET", "list");
    if (!B.alive(nav)) return;
    const look = myLook();
    const lobs = got.lobbies;
    const card = (l) => `<a class="lb-card" href="#/lobby/${esc(l.code)}"><b>${esc(l.mine ? "Your lobby" : l.host_name + "'s lobby")}</b>
      <span>${esc(ROOM_NAMES[l.room] || "")} · ${l.people.length ? esc(l.people.join(", ")) : "nobody inside yet"}</span>
      <span class="dim">${l.title ? "On the TV: " + esc(l.title) : "Nothing on the TV yet"}</span><span class="lb-code">${esc(l.code)}</span></a>`;
    $("#lbOpen").innerHTML = `
      ${lobs.length ? `<h3>Open now</h3><div class="lb-cards">${lobs.map(card).join("")}</div>` : `<p class="dim">No lobbies are open right now.</p>`}
      ${B.me.kids ? "" : `<h3>Open your own</h3><div class="lb-rooms">${Object.entries(ROOM_NAMES).map(([k, n]) => `<button class="chip" data-room="${k}">${esc(n)}</button>`).join("")}</div>
        <p class="dim" style="font-size:14px">Tip: on any movie or show page, More &gt; Watch together opens the lobby with it ready on the TV.</p>`}
      <h3>Have a code?</h3><form class="lb-code-form" id="lbCode"><input id="lbCodeIn" maxlength="4" autocomplete="off" placeholder="ABCD" aria-label="Lobby code"><button class="btn white">Join</button></form>
      <h3>Your character</h3><div class="lb-rooms" id="lbStyle">${Object.entries(STYLE_NAMES).map(([k, n]) => `<button class="chip ${look.style === k ? "on" : ""}" data-style="${k}">${esc(n)}</button>`).join("")}</div>`;
    $("#lbOpen").onclick = (e) => {
      const r = e.target.closest("[data-room]");
      if (r) return B.busy(r, () => L.open({ room: r.dataset.room }));
      const s = e.target.closest("[data-style]");
      if (s) { const l = myLook(); l.style = s.dataset.style; saveLook(l); $$("#lbStyle .chip").forEach((c) => c.classList.toggle("on", c === s)); }
    };
    $("#lbCode").onsubmit = (e) => { e.preventDefault(); const c = $("#lbCodeIn").value.trim().toUpperCase(); if (/^[A-Z]{4}$/.test(c)) B.go("#/lobby/" + c); else B.toast("Codes are 4 letters."); };
  });

  // ---------- three.js, loaded the first time a lobby opens ----------
  let threeLoading = null;
  const loadThree = () => window.THREE ? Promise.resolve() : (threeLoading = threeLoading || new Promise((ok, no) => {
    const s = document.createElement("script");
    s.src = "web/vendor/three.min.js";
    s.onload = ok;
    s.onerror = () => { threeLoading = null; no(new Error("The 3D part of the lobby didn't load. Try again.")); };
    document.head.appendChild(s);
  }));

  B.route(/^\/lobby\/([A-Za-z]{4})/, async (m, nav) => {
    const code = m[1].toUpperCase();
    B.page().innerHTML = `<div class="center"><div>${B.spinner()}<p class="dim">Opening the lobby…</p></div></div>`;
    await loadThree();
    // First answer from Tower: are we allowed in?
    const first = await api("POST", code + "/sync", { me: { x: 0, z: 0, yaw: 0, sit: -1 }, chat_since: 0, pool_since: 0 });
    if (!B.alive(nav)) { api("POST", code + "/leave").catch(() => {}); return; }
    room(code, first, nav);
  });

  // =====================================================================================================
  // The room itself
  // =====================================================================================================
  function room(code, first, nav) {
    const T = THREE;
    const look = myLook();
    const isTouch = matchMedia("(pointer: coarse)").matches || ("ontouchstart" in window && navigator.maxTouchPoints > 0) || /Android|iPhone|iPad|Mobile/i.test(navigator.userAgent);
    const weak = isTouch || (navigator.hardwareConcurrency || 8) <= 4;
    const kids = !!B.me.kids;
    let closed = false;
    const cleanups = [];
    const on = (target, type, fn, opts) => { target.addEventListener(type, fn, opts); cleanups.push(() => target.removeEventListener(type, fn, opts)); };

    B.page().innerHTML = `<div class="lb" id="lb">
      <div class="lb-stage" id="lbStage" tabindex="0" aria-label="The lobby. W A S D to walk, the mouse to look.">
        <canvas id="lbC"></canvas>
        <video id="lbV" playsinline></video>
        <div class="lb-hud tl"><button class="lb-pill lb-btn" id="lbBack" title="Leave the lobby">${I("left")} Leave</button><div class="lb-pill" id="lbRoom"></div><div class="lb-pill" id="lbNow"></div></div>
        <div class="lb-hud tr"><button class="lb-pill lb-btn" id="lbTvBtn" title="The TV (T)">${I("tv")} TV</button><button class="lb-pill lb-btn" id="lbGear" title="Lobby settings">${I("gear")}</button><span class="lb-pill" id="lbFps">-- fps</span></div>
        <div class="lb-dot" id="lbDot" hidden></div>
        <div class="lb-sub" id="lbSub" hidden></div>
        <div class="lb-prompt" id="lbPrompt" hidden></div>
        <div class="lb-toast" id="lbToast" hidden></div>
        <div class="lb-chat" id="lbChat"><input id="lbChatIn" maxlength="140" placeholder="Say something, then press Enter" hidden aria-label="Chat message"></div>
        <div class="lb-touch" id="lbTouch" hidden><div class="lb-stick" id="lbStick"><i id="lbKnob"></i></div>
          <div class="lb-tbtns"><button id="lbTUse">Use</button><button id="lbTChat">Chat</button></div></div>
        <div class="lb-start" id="lbStart"><div class="lb-card-in">
          <div class="lb-big" id="lbStartTitle">${esc(first.host ? "Your lobby" : first.host_name + "'s lobby")} · code ${esc(code)}</div>
          <button class="btn white" id="lbEnter">Walk in</button>
          <div class="lb-keys">${isTouch ? "<span>Left thumb: walk</span><span>Drag the right side: look</span><span>Use: sit or play</span>"
            : "<span><kbd>W A S D</kbd>walk</span><span><kbd>Mouse</kbd>look</span><span><kbd>Shift</kbd>hurry</span><span><kbd>E</kbd>sit / use</span><span><kbd>T</kbd>the TV</span><span><kbd>F</kbd>movie fills the screen</span><span><kbd>Enter</kbd>chat</span><span><kbd>1 2 3</kbd>wave, clap, laugh</span><span><kbd>Esc</kbd>free the mouse</span>"}</div>
        </div></div>
      </div>
    </div>`;
    const stage = $("#lbStage"), canvas = $("#lbC"), video = $("#lbV");
    if (isTouch) $("#lbTouch").hidden = false;

    // ---------- small helpers ----------
    const mat = (color, o) => new T.MeshStandardMaterial(Object.assign({ color, roughness: 0.8, metalness: 0 }, o || {}));
    const glowMat = (color, k) => new T.MeshStandardMaterial({ color: 0x000000, emissive: color, emissiveIntensity: k == null ? 1 : k, roughness: 1 });
    const box = (parent, w, h, d, mt, x, y, z, ry) => { const me = new T.Mesh(new T.BoxGeometry(w, h, d), mt); me.position.set(x, y, z); if (ry) me.rotation.y = ry; parent.add(me); return me; };
    const cyl = (parent, rt, rb, h, mt, x, y, z, seg) => { const me = new T.Mesh(new T.CylinderGeometry(rt, rb, h, seg || 20), mt); me.position.set(x, y, z); parent.add(me); return me; };
    const roundRect = (g, x, y, w, h, r) => { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); };
    const canvasTex = (w, h, draw) => { const c = document.createElement("canvas"); c.width = w; c.height = h; const g = c.getContext("2d"); draw(g, w, h); const t = new T.CanvasTexture(c); t.encoding = T.sRGBEncoding; t.anisotropy = 4; return t; };
    const FONT = 'Outfit, system-ui, sans-serif';
    const labelSprite = (text, color) => {
      const tex = canvasTex(256, 64, (g, w) => {
        g.font = `600 30px ${FONT}`; const tw = Math.min(w - 8, g.measureText(text).width + 34);
        g.fillStyle = "rgba(11,12,16,0.78)"; roundRect(g, (w - tw) / 2, 8, tw, 48, 24); g.fill();
        g.fillStyle = color; g.beginPath(); g.arc((w - tw) / 2 + 20, 32, 7, 0, Math.PI * 2); g.fill();
        g.fillStyle = "#F1F2F5"; g.textBaseline = "middle"; g.fillText(text, (w - tw) / 2 + 34, 33);
      });
      const s = new T.Sprite(new T.SpriteMaterial({ map: tex, depthWrite: false, transparent: true })); s.scale.set(0.9, 0.225, 1); return s;
    };
    const bubbleSprite = (text) => {
      const words = text.length > 46 ? text.slice(0, 44) + "…" : text;
      const tex = canvasTex(512, 96, (g, w) => {
        g.font = `500 30px ${FONT}`; const tw = Math.min(w - 8, g.measureText(words).width + 40);
        g.fillStyle = "rgba(241,242,245,0.95)"; roundRect(g, (w - tw) / 2, 8, tw, 64, 20); g.fill();
        g.beginPath(); g.moveTo(w / 2 - 10, 70); g.lineTo(w / 2, 88); g.lineTo(w / 2 + 10, 70); g.fill();
        g.fillStyle = "#0B0C10"; g.textBaseline = "middle"; g.textAlign = "center"; g.fillText(words, w / 2, 41);
      });
      const s = new T.Sprite(new T.SpriteMaterial({ map: tex, depthWrite: false, transparent: true })); s.scale.set(1.9, 0.36, 1); return s;
    };

    // ---------- characters (Pebble, Usher, Headphones) ----------
    function makeAvatar(style, color, name) {
      const g = new T.Group(); const col = new T.Color(color);
      const body = new T.Group(); g.add(body);
      const parts = { body, glow: [] };
      if (style === "facet") {
        const bm = mat(col, { flatShading: true, roughness: 0.7 }), dark = mat(0x262932, { flatShading: true });
        cyl(body, 0.2, 0.32, 0.72, bm, 0, 0.86, 0, 6).rotation.y = Math.PI / 6;
        box(body, 0.13, 0.5, 0.15, dark, -0.11, 0.25, 0); box(body, 0.13, 0.5, 0.15, dark, 0.11, 0.25, 0);
        const head = new T.Mesh(new T.IcosahedronGeometry(0.2, 1), mat(0xE4E5EA, { flatShading: true })); head.position.y = 1.45; body.add(head);
        const capm = mat(col.clone().multiplyScalar(0.7), { flatShading: true });
        cyl(body, 0.17, 0.2, 0.1, capm, 0, 1.62, 0, 8); box(body, 0.22, 0.025, 0.14, capm, 0, 1.58, 0.17);
        const band = new T.Mesh(new T.TorusGeometry(0.205, 0.018, 6, 8), glowMat(col, 1.1)); band.rotation.x = Math.PI / 2; band.position.y = 1.58; body.add(band); parts.glow.push(band);
        cyl(body, 0.07, 0.09, 0.45, bm, -0.33, 0.95, 0, 6).rotation.z = 0.18; cyl(body, 0.07, 0.09, 0.45, bm, 0.33, 0.95, 0, 6).rotation.z = -0.18;
      } else if (style === "glow") {
        const dark = mat(0x23262E, { roughness: 0.5, metalness: 0.1 });
        cyl(body, 0.15, 0.3, 1.05, dark, 0, 0.53, 0, 28);
        const sh = new T.Mesh(new T.SphereGeometry(0.15, 20, 12, 0, Math.PI * 2, 0, Math.PI / 2), dark); sh.position.y = 1.05; body.add(sh);
        const chest = new T.Mesh(new T.TorusGeometry(0.205, 0.016, 8, 40), glowMat(col, 1.3)); chest.rotation.x = Math.PI / 2; chest.position.y = 0.72; body.add(chest); parts.glow.push(chest);
        cyl(body, 0.06, 0.07, 0.14, dark, 0, 1.13, 0, 12);
        const head = new T.Mesh(new T.SphereGeometry(0.19, 28, 20), mat(0xD9DBE1, { roughness: 0.35 })); head.position.y = 1.36; body.add(head);
        const hp = new T.Mesh(new T.TorusGeometry(0.215, 0.022, 8, 30, Math.PI), glowMat(col, 1.3)); hp.position.y = 1.38; body.add(hp); parts.glow.push(hp);
        const cupM = mat(0x16181E, { roughness: 0.4 });
        cyl(body, 0.075, 0.075, 0.07, cupM, 0.2, 1.37, 0, 20).rotation.z = Math.PI / 2;
        cyl(body, 0.075, 0.075, 0.07, cupM, -0.2, 1.37, 0, 20).rotation.z = Math.PI / 2;
      } else {
        const bm = mat(col.clone().lerp(new T.Color(0x2A2D35), 0.35), { roughness: 0.55 });
        cyl(body, 0.26, 0.26, 0.62, bm, 0, 0.78, 0, 24);
        const s1 = new T.Mesh(new T.SphereGeometry(0.26, 24, 16), bm); s1.position.y = 0.47; body.add(s1);
        const s2 = new T.Mesh(new T.SphereGeometry(0.26, 24, 16), bm); s2.position.y = 1.09; body.add(s2);
        const head = new T.Mesh(new T.SphereGeometry(0.2, 28, 20), mat(0xE9EAEE, { roughness: 0.45 })); head.position.y = 1.56; body.add(head);
        const len = Math.PI * 0.95;
        const visor = new T.Mesh(new T.SphereGeometry(0.207, 28, 12, Math.PI / 2 - len / 2, len, Math.PI * 0.36, Math.PI * 0.26), mat(0x0B0C10, { roughness: 0.15, metalness: 0.5, side: T.DoubleSide }));
        visor.position.y = 1.56; body.add(visor);
        const line = new T.Mesh(new T.TorusGeometry(0.209, 0.008, 8, 40, len), glowMat(col, 1.4));
        line.rotation.x = Math.PI / 2; line.rotation.z = Math.PI / 2 - len / 2; line.position.y = 1.535; body.add(line); parts.glow.push(line);
        const feet = mat(0x1D2027);
        box(body, 0.16, 0.1, 0.22, feet, -0.11, 0.05, 0.03); box(body, 0.16, 0.1, 0.22, feet, 0.11, 0.05, 0.03);
        cyl(body, 0.07, 0.07, 0.25, bm, -0.11, 0.2, 0, 12); cyl(body, 0.07, 0.07, 0.25, bm, 0.11, 0.2, 0, 12);
      }
      if (name) { const s = labelSprite(name, color); s.position.y = style === "glow" ? 1.85 : 1.98; g.add(s); parts.tag = s; }
      g.userData = parts; return g;
    }

    // ---------- screens: the big TV (movie or "nothing on"), and the lobby board ----------
    const idleCanvas = document.createElement("canvas"); idleCanvas.width = 640; idleCanvas.height = 360;
    const idleTex = new T.CanvasTexture(idleCanvas); idleTex.encoding = T.sRGBEncoding;
    const drawIdle = (line1, line2) => {
      const g = idleCanvas.getContext("2d");
      const grd = g.createLinearGradient(0, 0, 640, 360); grd.addColorStop(0, "#16181E"); grd.addColorStop(1, "#0B0C10");
      g.fillStyle = grd; g.fillRect(0, 0, 640, 360);
      ["#FFB547", "#7AA7FF", "#3DD6C6", "#FF6F91"].forEach((c, i) => { g.fillStyle = c; roundRect(g, 284 + (i % 2) * 38, 70 + Math.floor(i / 2) * 38, 34, 34, 6); g.fill(); });
      g.textAlign = "center"; g.fillStyle = "#F1F2F5"; g.font = `700 30px ${FONT}`; g.fillText(line1, 320, 220);
      g.fillStyle = "#A7A9B0"; g.font = `500 18px ${FONT}`; g.fillText(line2 || "", 320, 256);
      idleTex.needsUpdate = true;
    };
    const videoTex = new T.VideoTexture(video); videoTex.encoding = T.sRGBEncoding;
    const boardCanvas = document.createElement("canvas"); boardCanvas.width = 640; boardCanvas.height = 400;
    const boardTex = new T.CanvasTexture(boardCanvas); boardTex.encoding = T.sRGBEncoding;
    const drawBoard = () => {
      const g = boardCanvas.getContext("2d");
      g.fillStyle = "#16181E"; g.fillRect(0, 0, 640, 400);
      g.fillStyle = "#F1F2F5"; g.font = `800 34px ${FONT}`; g.fillText("Lobby " + code, 28, 58);
      g.fillStyle = "#A7A9B0"; g.font = `500 18px ${FONT}`; g.fillText(tv.title ? "On the TV: " + tv.title.slice(0, 48) : "Nothing on the TV yet", 28, 92);
      g.fillText("Here now", 28, 140);
      const names = [{ name: B.me.name + " (you)", color: look.color }].concat(Object.values(others).map((o) => ({ name: o.name, color: o.color })));
      names.slice(0, 8).forEach((p, i) => { g.fillStyle = p.color; g.beginPath(); g.arc(40 + (i % 2) * 290, 172 + Math.floor(i / 2) * 36, 8, 0, Math.PI * 2); g.fill(); g.fillStyle = "#F1F2F5"; g.font = `600 22px ${FONT}`; g.fillText(p.name.slice(0, 18), 58 + (i % 2) * 290, 180 + Math.floor(i / 2) * 36); });
      g.fillStyle = "#7B7E86"; g.font = `500 16px ${FONT}`; g.fillText("Shared browser and arcade games are coming in a later update", 28, 378);
      boardTex.needsUpdate = true;
    };

    // ---------- floors and walls ----------
    const texCache = {};
    const woodTex = (base, rep) => {
      const key = "wood" + base + rep; if (texCache[key]) return texCache[key];
      const t = canvasTex(512, 512, (g, w, h) => {
        g.fillStyle = base; g.fillRect(0, 0, w, h);
        for (let r = 0; r < 8; r++) { const off = (r % 2) * 128 + (r * 37) % 90; for (let c = -1; c < 3; c++) { const x = c * 256 + off; const l = 0.85 + ((r * 7 + c * 13) % 10) / 40; g.fillStyle = `rgba(0,0,0,${0.18 - l * 0.12})`; g.fillRect(x, r * 64, 254, 62); g.fillStyle = "rgba(0,0,0,.35)"; g.fillRect(x + 254, r * 64, 2, 64); } g.fillStyle = "rgba(0,0,0,.4)"; g.fillRect(0, r * 64 + 62, w, 2); }
      });
      t.wrapS = t.wrapT = T.RepeatWrapping; t.repeat.set(rep, rep); texCache[key] = t; return t;
    };
    const carpetTex = (base, dot) => {
      const t = canvasTex(256, 256, (g, w, h) => { g.fillStyle = base; g.fillRect(0, 0, w, h); g.fillStyle = dot; for (let y = 0; y < h; y += 32) for (let x = 0; x < w; x += 32) { g.beginPath(); g.arc(x + 16 + ((y / 32) % 2) * 16, y + 16, 2.5, 0, Math.PI * 2); g.fill(); } });
      t.wrapS = t.wrapT = T.RepeatWrapping; t.repeat.set(10, 14); return t;
    };
    const windowsTex = () => canvasTex(128, 256, (g, w, h) => { g.fillStyle = "#0c0f18"; g.fillRect(0, 0, w, h); for (let y = 6; y < h; y += 14) for (let x = 6; x < w; x += 14) { if (Math.random() < 0.38) { g.fillStyle = Math.random() < 0.7 ? "rgba(255,214,150,0.9)" : "rgba(170,200,255,0.8)"; g.fillRect(x, y, 7, 8); } } });

    // ---------- furniture ----------
    const addSeat = (s, x, z, y) => s.seats.push({ x, z, y });
    function sofa(g, s, x, z, ry, w, color) {
      const grp = new T.Group(); grp.position.set(x, 0, z); grp.rotation.y = ry; g.add(grp);
      const m = mat(color, { roughness: 0.95 }), m2 = mat(new T.Color(color).multiplyScalar(0.8), { roughness: 0.95 });
      box(grp, w, 0.42, 0.95, m2, 0, 0.21, 0); box(grp, w, 0.6, 0.22, m, 0, 0.62, 0.38);
      box(grp, 0.22, 0.62, 0.95, m, -w / 2 - 0.11, 0.31, 0); box(grp, 0.22, 0.62, 0.95, m, w / 2 + 0.11, 0.31, 0);
      const n = Math.max(1, Math.round(w / 1.05));
      for (let i = 0; i < n; i++) { const lx = -w / 2 + (w / n) * (i + 0.5); box(grp, w / n - 0.04, 0.12, 0.75, m, lx, 0.47, -0.06); }
      const half = w / 2 + 0.22, c = Math.cos(ry), sn = Math.sin(ry);
      const ex = Math.abs(c) * half + Math.abs(sn) * 0.5, ez = Math.abs(sn) * half + Math.abs(c) * 0.5;
      s.obstacles.push([x - ex, x + ex, z - ez, z + ez]);
      for (let i = 0; i < n; i++) { const lx = -w / 2 + (w / n) * (i + 0.5); addSeat(s, x + lx * c + -0.05 * sn, z - lx * sn + -0.05 * c, 0.5); }
    }
    function recliner(g, s, x, z, y) {
      const grp = new T.Group(); grp.position.set(x, y, z); g.add(grp);
      const m = mat(0x3a2a33, { roughness: 0.7 }), m2 = mat(0x2a1e25, { roughness: 0.7 });
      box(grp, 0.9, 0.45, 0.9, m2, 0, 0.225, 0); box(grp, 0.9, 0.8, 0.22, m, 0, 0.75, 0.42); box(grp, 0.12, 0.65, 0.9, m2, -0.51, 0.33, 0); box(grp, 0.12, 0.65, 0.9, m2, 0.51, 0.33, 0);
      box(grp, 0.78, 0.12, 0.7, m, 0, 0.5, -0.04); cyl(grp, 0.05, 0.05, 0.04, glowMat(0x3DD6C6, 0.7), 0.51, 0.67, -0.25, 12);
      s.obstacles.push([x - 0.6, x + 0.6, z - 0.5, z + 0.55]); addSeat(s, x, z, y + 0.5);
    }
    function beanbag(g, s, x, z, color) {
      const b = new T.Mesh(new T.SphereGeometry(0.5, 20, 14), mat(color, { roughness: 1 })); b.scale.set(1, 0.6, 1); b.position.set(x, 0.28, z); g.add(b);
      s.obstacles.push([x - 0.4, x + 0.4, z - 0.4, z + 0.4]); addSeat(s, x, z, 0.38);
    }
    function lamp(g, x, z) {
      cyl(g, 0.02, 0.02, 1.5, mat(0x111111, { metalness: 0.6 }), x, 0.75, z, 6); cyl(g, 0.2, 0.3, 0.35, glowMat(0xffd9a8, 1.2), x, 1.6, z, 16);
      const l = new T.PointLight(0xffd0a0, 0.5, 5, 2); l.position.set(x, 1.6, z); g.add(l);
    }
    function pendant(g, x, y, z) {
      box(g, 1.8, 0.14, 0.32, mat(0x1a1714, { roughness: 0.5 }), x, y + 0.08, z); box(g, 1.7, 0.02, 0.26, glowMat(0xffd59a, 0.9), x, y, z);
      const pl = new T.PointLight(0xffd59a, 1.2, 6, 2); pl.position.set(x, y - 0.2, z); g.add(pl);
    }
    function poolTable(g, x, z, y, felt) {
      const grp = new T.Group(); grp.position.set(x, y, z); g.add(grp);
      const wood = mat(0x4a2e1e, { roughness: 0.5 });
      const hx = 1.15, hz = 0.6, top = 0.8;
      box(grp, hx * 2, 0.04, hz * 2, mat(felt, { roughness: 1 }), 0, top - 0.02, 0);
      box(grp, hx * 2 + 0.3, 0.12, 0.15, wood, 0, top + 0.02, hz + 0.075); box(grp, hx * 2 + 0.3, 0.12, 0.15, wood, 0, top + 0.02, -hz - 0.075);
      box(grp, 0.15, 0.12, hz * 2, wood, hx + 0.075, top + 0.02, 0); box(grp, 0.15, 0.12, hz * 2, wood, -hx - 0.075, top + 0.02, 0);
      box(grp, hx * 2 + 0.2, 0.25, hz * 2 + 0.2, wood, 0, top - 0.17, 0);
      for (const [lx, lz] of [[-1, -0.5], [1, -0.5], [-1, 0.5], [1, 0.5]]) box(grp, 0.14, 0.55, 0.14, wood, lx, 0.28, lz);
      const pockets = [[-hx, -hz], [0, -hz], [hx, -hz], [-hx, hz], [0, hz], [hx, hz]];
      pockets.forEach(([px, pz]) => { const p = new T.Mesh(new T.CircleGeometry(0.075, 16), mat(0x050505)); p.rotation.x = -Math.PI / 2; p.position.set(px, top + 0.001, pz); grp.add(p); });
      const r = 0.05, balls = [];
      const cols = [0xFFB547, 0x7AA7FF, 0xFF6F91, 0xA98BFF, 0xF07A3A, 0x3DD6C6, 0x8a3030, 0x111111, 0xFFB547, 0x7AA7FF, 0xFF6F91, 0xA98BFF, 0xF07A3A, 0x3DD6C6, 0x8a3030];
      const geo = new T.SphereGeometry(r, 16, 12);
      const cue = new T.Mesh(geo, mat(0xf5f3ec, { roughness: 0.25 })); grp.add(cue); balls.push({ m: cue, x: 0, z: 0, vx: 0, vz: 0, in: false, cue: true });
      for (let i = 0; i < 15; i++) { const mm = new T.Mesh(geo, mat(cols[i], { roughness: 0.25 })); grp.add(mm); balls.push({ m: mm, x: 0, z: 0, vx: 0, vz: 0, in: false }); }
      const pool = { grp, balls, hx, hz, r, top, pockets, wx: x, wz: z, acc: 0, moving: false };
      rackBalls(pool); return pool;
    }
    function arcade(g, s, x, z, ry, y) {
      y = y || 0; const grp = new T.Group(); grp.position.set(x, y, z); grp.rotation.y = ry; g.add(grp);
      box(grp, 0.75, 1.8, 0.7, mat(0x1D2027, { roughness: 0.6 }), 0, 0.9, 0); box(grp, 0.78, 0.25, 0.75, glowMat(0xFF6F91, 1.2), 0, 1.92, 0.02);
      const scr = canvasTex(256, 200, (gg) => { gg.fillStyle = "#0B0C10"; gg.fillRect(0, 0, 256, 200); gg.fillStyle = "#FF6F91"; gg.font = `800 26px ${FONT}`; gg.fillText("Benflix", 20, 50); gg.fillText("Games", 20, 80); gg.fillStyle = "#A7A9B0"; gg.font = `500 16px ${FONT}`; gg.fillText("Coming soon", 20, 130); });
      const sm = new T.Mesh(new T.PlaneGeometry(0.6, 0.47), new T.MeshBasicMaterial({ map: scr })); sm.position.set(0, 1.35, 0.356); grp.add(sm);
      box(grp, 0.72, 0.06, 0.3, mat(0x262932), 0, 0.98, 0.45);
      const c = Math.cos(ry), sn = Math.sin(ry);
      s.obstacles.push([x - 0.5, x + 0.5, z - 0.5, z + 0.5]);
      s.use.push({ x: x + sn * 0.8, z: z + c * 0.8, r: 1.3, label: "<kbd>E</kbd> The arcade", act: () => toast("The arcade will play your Benflix console games together in a later update.") });
    }
    function popcorn(g, s, x, z, y) {
      const grp = new T.Group(); grp.position.set(x, y, z); g.add(grp);
      box(grp, 0.7, 0.9, 0.6, mat(0xb3202a, { roughness: 0.5 }), 0, 0.45, 0);
      box(grp, 0.62, 0.6, 0.52, new T.MeshStandardMaterial({ color: 0xfff1c0, emissive: 0xffd27a, emissiveIntensity: 0.6, transparent: true, opacity: 0.85 }), 0, 1.2, 0);
      box(grp, 0.72, 0.12, 0.62, mat(0xb3202a), 0, 1.56, 0);
      s.obstacles.push([x - 0.45, x + 0.45, z - 0.4, z + 0.4]);
      s.use.push({ x, z: z - 0.8, r: 1.2, label: "<kbd>E</kbd> Grab some popcorn", act: () => { emote("popcorn"); toast("Popcorn! Everyone sees you grab some."); } });
    }
    function board(g, s, x, y, z, ry, stand) {
      const grp = new T.Group(); grp.position.set(x, y, z); grp.rotation.y = ry; g.add(grp);
      box(grp, 2.5, 1.62, 0.06, mat(0x0a0a0a), 0, 0, -0.02);
      const scr = new T.Mesh(new T.PlaneGeometry(2.4, 1.5), new T.MeshBasicMaterial({ map: boardTex })); scr.position.z = 0.015; grp.add(scr);
      if (stand) { cyl(grp, 0.05, 0.05, y, mat(0x1a1c22), -1.1, -y / 2, -0.06, 8); cyl(grp, 0.05, 0.05, y, mat(0x1a1c22), 1.1, -y / 2, -0.06, 8); s.obstacles.push([x - 0.9, x + 0.9, z - 0.9, z + 0.9]); }
    }
    function darts(g, s, x, y, z, ry) {
      const grp = new T.Group(); grp.position.set(x, y, z); grp.rotation.y = ry; g.add(grp);
      const t = canvasTex(256, 256, (gg) => { const rings = ["#111", "#e8e1d0", "#111", "#c23b3b", "#111", "#2f6b4f", "#c23b3b"]; [120, 100, 80, 60, 40, 20, 8].forEach((rr, i) => { gg.fillStyle = rings[i]; gg.beginPath(); gg.arc(128, 128, rr, 0, Math.PI * 2); gg.fill(); }); });
      grp.add(new T.Mesh(new T.CircleGeometry(0.3, 32), new T.MeshStandardMaterial({ map: t })));
      const c = Math.cos(ry), sn = Math.sin(ry);
      s.use.push({ x: x + sn * 2.2, z: z + c * 2.2, r: 1.4, label: "<kbd>E</kbd> Darts", act: () => toast("Darts come in a later update.") });
    }

    // ---------- the three rooms ----------
    const BUILD = {
      den() {
        const s = { bounds: [-5.6, 5.6, -6.6, 6.6], obstacles: [], seats: [], use: [], floor: () => 0, spawn: [0, 5.6], bg: 0x0b0c10 };
        const g = s.group = new T.Group();
        const W = 12, D = 14, H = 3.2;
        const floor = new T.Mesh(new T.PlaneGeometry(W, D), mat(0xffffff, { map: woodTex("#6b4a34", 3), roughness: 0.7 })); floor.rotation.x = -Math.PI / 2; g.add(floor);
        const walls = new T.Mesh(new T.BoxGeometry(W, H, D), mat(0x3a3430, { side: T.BackSide, roughness: 0.95 })); walls.position.y = H / 2; g.add(walls);
        box(g, 6.4, H, 0.1, mat(0x1d1a19, { roughness: 0.6 }), 0, H / 2, -D / 2 + 0.06);
        box(g, 3.86, 2.22, 0.08, mat(0x0a0a0a, { roughness: 0.3 }), 0, 1.75, -6.86);
        s.tv = { w: 3.7, h: 2.08, x: 0, y: 1.75, z: -6.81 };
        box(g, 3.4, 0.45, 0.5, mat(0x2a2420), 0, 0.225, -6.6); s.obstacles.push([-1.7, 1.7, -7, -6.3]);
        const rug = new T.Mesh(new T.PlaneGeometry(5.4, 3.6), mat(0x4a3b5c, { roughness: 1 })); rug.rotation.x = -Math.PI / 2; rug.position.set(0, 0.012, -3.4); g.add(rug);
        box(g, 1.6, 0.06, 0.8, mat(0x6d4c35, { roughness: 0.5 }), 0, 0.42, -3.6); box(g, 1.5, 0.38, 0.7, mat(0x3a2a20), 0, 0.2, -3.6);
        box(g, 0.18, 0.025, 0.06, mat(0x111111), 0.3, 0.465, -3.5); s.obstacles.push([-0.85, 0.85, -4.05, -3.15]);
        s.use.push({ x: 0.3, z: -3.5, r: 1.2, label: "<kbd>E</kbd> Pick up the remote", act: () => tvPanel() });
        sofa(g, s, 0, -1.4, Math.PI, 3.2, 0x5a6b8c); sofa(g, s, -3.6, -3.4, -Math.PI / 2, 2.4, 0x5a6b8c); sofa(g, s, 3.6, -3.4, Math.PI / 2, 1.2, 0x8c6a5a);
        lamp(g, -4.8, -5.8); lamp(g, 4.8, -5.8);
        box(g, 0.4, 2.2, 2.2, mat(0x2a2420), 5.75, 1.1, -0.6); s.obstacles.push([5.5, 6, -1.7, 0.5]);
        for (let r = 0; r < 4; r++) for (let b = 0; b < 9; b++) box(g, 0.25, 0.3 + (b % 3) * 0.04, 0.12 + (b % 2) * 0.05, mat([0x7AA7FF, 0xFFB547, 0x3DD6C6, 0xFF6F91, 0x8a8f99][(r + b) % 5], { roughness: 0.9 }), 5.62, 0.35 + r * 0.52, -1.5 + b * 0.2);
        for (const z of [2.5, 5]) { box(g, 0.05, 1.5, 1.6, glowMat(0x1a2a4a, 0.8), -5.97, 1.7, z); box(g, 0.08, 1.6, 0.06, mat(0x241f1c), -5.94, 1.7, z); }
        s.pool = poolTable(g, 0, 3.6, 0, 0x2f6b4f); s.obstacles.push([-1.55, 1.55, 2.75, 4.45]);
        pendant(g, 0, 2.38, 3.6);
        arcade(g, s, -5.2, 5.6, Math.PI / 2);
        board(g, s, -5.94, 1.7, 0, Math.PI / 2);
        darts(g, s, 5.94, 1.7, 4.5, -Math.PI / 2);
        g.add(new T.HemisphereLight(0xffe7cc, 0x221a14, 0.35));
        const ceil = new T.PointLight(0xffe2b8, 0.55, 14, 2); ceil.position.set(0, 3, -1); g.add(ceil); s.room = [ceil];
        return s;
      },
      theater() {
        const s = { bounds: [-5.6, 5.6, -8.4, 8.6], obstacles: [], seats: [], use: [], spawn: [0, 7.8], bg: 0x050507 };
        const g = s.group = new T.Group();
        const W = 12, D = 18, H = 5.6;
        s.floor = (x, z) => (z > 1.0 ? 0.8 : z > -1.6 ? 0.4 : 0);
        const floor = new T.Mesh(new T.PlaneGeometry(W, D), mat(0xffffff, { map: carpetTex("#2a1a24", "#4a2a3a"), roughness: 1 })); floor.rotation.x = -Math.PI / 2; g.add(floor);
        const walls = new T.Mesh(new T.BoxGeometry(W, H, D), mat(0x1a1418, { side: T.BackSide, roughness: 1 })); walls.position.y = H / 2; g.add(walls);
        const rm = mat(0x2a1a24, { roughness: 1 });
        box(g, W, 0.4, 2.6, rm, 0, 0.2, -0.3); box(g, W, 0.8, 7.6, rm, 0, 0.4, 4.8);
        box(g, W, 0.03, 0.03, glowMat(0xffb547, 0.8), 0, 0.41, -1.6); box(g, W, 0.03, 0.03, glowMat(0xffb547, 0.8), 0, 0.81, 1.0);
        box(g, 7.4, 3.6, 0.1, mat(0x050505), 0, 2.7, -8.92);
        s.tv = { w: 7.0, h: 3.94, x: 0, y: 2.75, z: -8.85 };
        for (const sx of [-1, 1]) for (let i = 0; i < 6; i++) box(g, 0.24, 5, 0.25 + (i % 2) * 0.12, mat(0x6b1a2a, { roughness: 0.9 }), sx * (3.9 + i * 0.22), 2.5, -8.7);
        s.obstacles.push([-6, -3.8, -9, -8.4], [3.8, 6, -9, -8.4]);
        const starG = new T.BufferGeometry(); const pts = []; for (let i = 0; i < 380; i++) pts.push((Math.random() - 0.5) * W, H - 0.02, (Math.random() - 0.5) * D);
        starG.setAttribute("position", new T.Float32BufferAttribute(pts, 3)); g.add(new T.Points(starG, new T.PointsMaterial({ color: 0xffffff, size: 0.035 })));
        [-3.2, -0.3, 2.3].forEach((z, r) => { for (let i = 0; i < 4; i++) recliner(g, s, -3.3 + i * 2.2, z, [0, 0.4, 0.8][r]); });
        for (const z of [-6, -2, 2, 6]) for (const sx of [-1, 1]) box(g, 0.06, 0.5, 0.25, glowMat(0xc8803a, 0.55), sx * 5.96, 3.1, z);
        s.pool = poolTable(g, -0.6, 6.4, 0.8, 0x6b2a3a); s.obstacles.push([-2.15, 0.95, 5.55, 7.25]);
        pendant(g, -0.6, 3.38, 6.4);
        popcorn(g, s, 4.8, 8.1, 0.8);
        arcade(g, s, -5.2, 7.6, Math.PI / 2, 0.8);
        board(g, s, 5.94, 2.4, 5.4, -Math.PI / 2);
        g.add(new T.HemisphereLight(0xffd6c0, 0x100808, 0.22));
        const wash = new T.PointLight(0xffc89a, 0.5, 18, 2); wash.position.set(0, 4.5, 0); g.add(wash); s.room = [wash];
        return s;
      },
      roof() {
        const s = { bounds: [-7.4, 7.4, -7.4, 7.4], obstacles: [], seats: [], use: [], floor: () => 0, spawn: [0, 6.6], bg: 0x0a1020, sky: true };
        const g = s.group = new T.Group();
        const floor = new T.Mesh(new T.PlaneGeometry(16, 16), mat(0xffffff, { map: woodTex("#4a4038", 4), roughness: 0.85 })); floor.rotation.x = -Math.PI / 2; g.add(floor);
        const base = new T.Mesh(new T.PlaneGeometry(26, 26), mat(0x22252c)); base.rotation.x = -Math.PI / 2; base.position.y = -0.01; g.add(base);
        const rail = mat(0x30333b, { metalness: 0.6, roughness: 0.4 });
        for (const [w, d, x, z] of [[16, 0.08, 0, -8], [16, 0.08, 0, 8], [0.08, 16, -8, 0], [0.08, 16, 8, 0]]) { box(g, w, 0.06, d, rail, x, 1.05, z); box(g, w, 0.04, d, rail, x, 0.55, z); }
        for (let i = -8; i <= 8; i += 2) for (const [x, z] of [[i, -8], [i, 8], [-8, i], [8, i]]) cyl(g, 0.03, 0.03, 1.05, rail, x, 0.52, z, 6);
        const wt = windowsTex(); wt.wrapS = wt.wrapT = T.RepeatWrapping; wt.repeat.set(1.5, 3);
        const skyM = new T.MeshStandardMaterial({ color: 0x0d1018, emissive: 0xffffff, emissiveMap: wt, emissiveIntensity: 0.4, roughness: 1 });
        for (let i = 0; i < 70; i++) {
          const a = (i / 70) * Math.PI * 2 + Math.random() * 0.05, r = 45 + Math.random() * 45, h = 10 + Math.random() * 34, w = 4 + Math.random() * 6;
          box(g, w, h, w, skyM, Math.cos(a) * r, h / 2 - 22, Math.sin(a) * r).rotation.y = -a;
        }
        const starG = new T.BufferGeometry(); const pts = []; for (let i = 0; i < 600; i++) { const th = Math.random() * Math.PI * 2, ph = Math.random() * 1.2; pts.push(Math.cos(th) * Math.sin(ph) * 120, Math.cos(ph) * 120, Math.sin(th) * Math.sin(ph) * 120); }
        starG.setAttribute("position", new T.Float32BufferAttribute(pts, 3)); g.add(new T.Points(starG, new T.PointsMaterial({ color: 0xffffff, size: 0.5, sizeAttenuation: true, fog: false })));
        const moon = new T.Mesh(new T.SphereGeometry(3, 24, 16), new T.MeshBasicMaterial({ color: 0xf3ead8, fog: false })); moon.position.set(-60, 45, -70); g.add(moon);
        const post = mat(0x1a1c22, { metalness: 0.5 }); cyl(g, 0.08, 0.08, 4.2, post, -2.6, 2.1, -7, 10); cyl(g, 0.08, 0.08, 4.2, post, 2.6, 2.1, -7, 10);
        box(g, 5.2, 3.0, 0.1, mat(0x0a0a0a), 0, 2.4, -7.05); s.tv = { w: 4.9, h: 2.76, x: 0, y: 2.4, z: -6.99 }; s.obstacles.push([-2.8, 2.8, -7.3, -6.8]);
        [[-2.4, -3.2, 0xFFB547], [-0.8, -3.0, 0x7AA7FF], [0.8, -3.0, 0x3DD6C6], [2.4, -3.2, 0xFF6F91]].forEach(([x, z, c]) => beanbag(g, s, x, z, c));
        sofa(g, s, 0, -0.6, Math.PI, 3.2, 0x5c6470);
        cyl(g, 0.6, 0.7, 0.4, mat(0x3a3530, { roughness: 1 }), -4.6, 0.2, -2.2, 16); cyl(g, 0.45, 0.45, 0.05, glowMat(0xff7a2a, 2), -4.6, 0.42, -2.2, 16);
        const fl = new T.PointLight(0xff8a3a, 1.4, 7, 2); fl.position.set(-4.6, 0.9, -2.2); g.add(fl); s.flicker = fl; s.obstacles.push([-5.3, -3.9, -2.9, -1.5]);
        const wood = mat(0x5a4636, { roughness: 0.8 });
        for (const [x, z] of [[1.2, 2.4], [5.8, 2.4], [1.2, 5.8], [5.8, 5.8]]) { box(g, 0.15, 3, 0.15, wood, x, 1.5, z); s.obstacles.push([x - 0.15, x + 0.15, z - 0.15, z + 0.15]); }
        for (let i = 0; i < 7; i++) box(g, 4.9, 0.1, 0.1, wood, 3.5, 3.0, 2.4 + i * 0.57);
        s.pool = poolTable(g, 3.5, 4.1, 0, 0x2a4f7a); s.obstacles.push([1.95, 5.05, 3.25, 4.95]);
        const bulbM = glowMat(0xffd59a, 2.2), bulbG = new T.SphereGeometry(0.05, 8, 6);
        for (let k = 0; k < 4; k++) {
          const wire = [];
          for (let i = 0; i <= 12; i++) { const x = -7 + i * (14 / 12), z = -6 + k * 3.8, sag = Math.sin((i / 12) * Math.PI) * 0.5; const b = new T.Mesh(bulbG, bulbM); b.position.set(x, 3.6 - sag, z); g.add(b); wire.push(x, 3.66 - sag, z); }
          const wg = new T.BufferGeometry(); wg.setAttribute("position", new T.Float32BufferAttribute(wire, 3)); g.add(new T.Line(wg, new T.LineBasicMaterial({ color: 0x2a2a2a })));
        }
        const sl = new T.PointLight(0xffd59a, 0.9, 16, 2); sl.position.set(0, 3.2, 0); g.add(sl);
        const pl = new T.PointLight(0xffd59a, 1.2, 6, 2); pl.position.set(3.5, 2.6, 4.1); g.add(pl);
        arcade(g, s, -6.6, 5.4, Math.PI / 2);
        board(g, s, -4.2, 1.6, 6.2, Math.PI * 0.75, true);
        g.add(new T.HemisphereLight(0x8aa0d0, 0x1a1410, 0.4));
        const moonL = new T.DirectionalLight(0xbfd0ff, 0.35); moonL.position.set(-6, 10, -7); g.add(moonL); s.room = [sl];
        return s;
      },
    };

    // ---------- pool: a fixed time step, so every browser plays a shot exactly the same way ----------
    const POOL_STEP = 1 / 240;
    function rackBalls(pool) {
      const r = pool.r, b = pool.balls; let k = 1;
      b[0].x = -pool.hx * 0.5; b[0].z = 0;
      for (let row = 0; row < 5; row++) for (let i = 0; i <= row; i++) { const ball = b[k++]; ball.x = pool.hx * 0.45 + row * r * 1.75; ball.z = (i - row / 2) * r * 2.02; }
      b.forEach((ball) => { ball.vx = ball.vz = 0; ball.in = false; ball.m.visible = true; ball.m.position.set(ball.x, pool.top + r, ball.z); });
    }
    function poolTick(pool) {
      const r = pool.r, b = pool.balls, h = POOL_STEP;
      for (const o of b) {
        if (o.in) continue;
        o.x += o.vx * h; o.z += o.vz * h;
        const sp = Math.hypot(o.vx, o.vz);
        if (sp > 0) { const ns = Math.max(0, sp - (0.35 + sp * 0.25) * h); o.vx *= ns / sp; o.vz *= ns / sp; }
        if (o.x < -pool.hx + r) { o.x = -pool.hx + r; o.vx = -o.vx * 0.8; } if (o.x > pool.hx - r) { o.x = pool.hx - r; o.vx = -o.vx * 0.8; }
        if (o.z < -pool.hz + r) { o.z = -pool.hz + r; o.vz = -o.vz * 0.8; } if (o.z > pool.hz - r) { o.z = pool.hz - r; o.vz = -o.vz * 0.8; }
        for (const [px, pz] of pool.pockets) if (Math.hypot(o.x - px, o.z - pz) < 0.09) { o.in = true; o.vx = o.vz = 0; break; }
      }
      for (let i = 0; i < b.length; i++) for (let j = i + 1; j < b.length; j++) {
        const A = b[i], Bb = b[j]; if (A.in || Bb.in) continue;
        const dx = Bb.x - A.x, dz = Bb.z - A.z, d = Math.hypot(dx, dz);
        if (d > 0 && d < 2 * r) {
          const nx = dx / d, nz = dz / d, ov = (2 * r - d) / 2; A.x -= nx * ov; A.z -= nz * ov; Bb.x += nx * ov; Bb.z += nz * ov;
          const rel = (A.vx - Bb.vx) * nx + (A.vz - Bb.vz) * nz;
          if (rel > 0) { const k = rel * 0.96; A.vx -= k * nx; A.vz -= k * nz; Bb.vx += k * nx; Bb.vz += k * nz; }
        }
      }
      let moving = false;
      for (const o of b) { if (o.in) continue; if (Math.hypot(o.vx, o.vz) > 0.01) moving = true; else o.vx = o.vz = 0; }
      if (!moving && b[0].in) { b[0].in = false; b[0].x = -pool.hx * 0.5; b[0].z = 0; }
      pool.moving = moving;
    }
    function stepPool(pool, dt) {
      if (!pool.moving) return;
      pool.acc += dt;
      let n = 0;
      while (pool.acc >= POOL_STEP && n++ < 120) { pool.acc -= POOL_STEP; poolTick(pool); if (!pool.moving) { pool.acc = 0; break; } }
      for (const o of pool.balls) { o.m.visible = !o.in; o.m.position.set(o.x, pool.top + pool.r, o.z); }
    }
    const poolSnapshot = (pool) => pool.balls.map((o) => [+o.x.toFixed(4), +o.z.toFixed(4), o.in ? 1 : 0]);
    function poolApply(pool, shot) {
      shot.balls.forEach((s, i) => { const o = pool.balls[i]; o.x = s[0]; o.z = s[1]; o.in = !!s[2]; o.vx = o.vz = 0; });
      pool.balls[0].vx = shot.push[0]; pool.balls[0].vz = shot.push[1];
      pool.moving = true; pool.acc = 0;
      for (const o of pool.balls) { o.m.visible = !o.in; o.m.position.set(o.x, pool.top + pool.r, o.z); }
    }

    // ---------- the 3D world ----------
    const renderer = new T.WebGLRenderer({ canvas, antialias: !weak, powerPreference: "high-performance" });
    renderer.outputEncoding = T.sRGBEncoding; renderer.toneMapping = T.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05;
    const camera = new T.PerspectiveCamera(70, 16 / 9, 0.05, 300); camera.rotation.order = "YXZ";
    let scene = null, world = null, roomKey = null;
    const player = { x: 0, z: 0, y: 1.6, yaw: 0, pitch: -0.05, sit: -1 };
    const others = {}; // account id -> { av, name, color, style, x, z, yaw, tx, tz, tyaw, sit, act, bubble, bubbleUntil }
    let tv = { item: "", title: "", kids_ok: true, playing: false, pos: 0, n: -1, by: "" };

    const disposeScene = () => {
      if (!scene) return;
      Object.values(others).forEach((o) => scene.remove(o.av)); // people move to the next room as they are
      const keep = new Set([idleTex, videoTex, boardTex]);
      scene.traverse((o) => {
        if (o.geometry) o.geometry.dispose();
        (o.material ? (Array.isArray(o.material) ? o.material : [o.material]) : []).forEach((m) => {
          ["map", "emissiveMap"].forEach((k) => { if (m[k] && !keep.has(m[k]) && !Object.values(texCache).includes(m[k])) m[k].dispose(); });
          m.dispose();
        });
      });
    };
    function buildWorld(key) {
      disposeScene();
      roomKey = key in BUILD ? key : "den";
      scene = new T.Scene();
      world = BUILD[roomKey]();
      scene.background = new T.Color(world.bg);
      if (world.sky) scene.fog = new T.Fog(world.bg, 40, 110);
      scene.add(world.group);
      const t = world.tv;
      world.tvMesh = new T.Mesh(new T.PlaneGeometry(t.w, t.h), new T.MeshBasicMaterial({ map: idleTex, toneMapped: false }));
      world.tvMesh.position.set(t.x, t.y, t.z); scene.add(world.tvMesh);
      world.tvLight = new T.PointLight(0xffaa66, 1.0, roomKey === "theater" ? 14 : 9, 2); world.tvLight.position.set(t.x, t.y, t.z + 1.8); scene.add(world.tvLight);
      player.x = world.spawn[0]; player.z = world.spawn[1]; player.yaw = 0; player.pitch = -0.05; player.sit = -1;
      player.y = world.floor(player.x, player.z) + 1.6;
      Object.values(others).forEach((o) => scene.add(o.av));
      $("#lbRoom").innerHTML = `<b>${esc(ROOM_NAMES[roomKey])}</b> · code ${esc(code)}`;
      showTvPicture();
      applyQuality();
      drawBoard();
    }
    const showTvPicture = () => {
      if (!world) return;
      const useVideo = tv.item && loadedItem === tv.item && video.readyState >= 2;
      world.tvMesh.material.map = useVideo ? videoTex : idleTex; world.tvMesh.material.needsUpdate = true;
      if (!useVideo) drawIdle(tv.item ? (loadingMsg || "Getting it ready…") : "Nothing on yet", tv.item ? tv.title : (first.host ? "Press T to pick what everyone watches" : `${first.host_name} picks what's on`));
      $("#lbNow").innerHTML = tv.item ? `On the TV: <b>${esc(tv.title)}</b>${tv.playing ? "" : " · paused"}` : "Nothing on the TV yet";
    };

    // ---------- quality: Lite on phones and small computers (like Benflix's lite mode) ----------
    let fpsCap = 60;
    function applyQuality() {
      const q = look.quality === "auto" ? (weak ? "lite" : "full") : look.quality;
      const dpr = window.devicePixelRatio || 1;
      renderer.setPixelRatio(q === "lite" ? Math.min(dpr, 1) * 0.75 : Math.min(dpr, 2));
      fpsCap = q === "lite" ? 30 : 60;
      if (world) world.group.traverse((o) => { if (o.isPointLight) o.visible = q === "full" || o.intensity > 1.1; });
      stage.dataset.q = q;
      resize();
    }
    function resize() { const r = stage.getBoundingClientRect(); renderer.setSize(r.width, r.height, false); camera.aspect = r.width / Math.max(1, r.height); camera.updateProjectionMatrix(); }
    on(window, "resize", resize);

    // ---------- moving into furniture and walls ----------
    function collide(x, z, rad) {
      const bd = world.bounds; x = Math.max(bd[0], Math.min(bd[1], x)); z = Math.max(bd[2], Math.min(bd[3], z));
      for (const o of world.obstacles) {
        const cx = Math.max(o[0], Math.min(o[1], x)), cz = Math.max(o[2], Math.min(o[3], z)), dx = x - cx, dz = z - cz, d2 = dx * dx + dz * dz;
        if (d2 < rad * rad) {
          if (d2 > 1e-6) { const d = Math.sqrt(d2); x = cx + (dx / d) * rad; z = cz + (dz / d) * rad; }
          else { const pen = [x - o[0], o[1] - x, z - o[2], o[3] - z], mn = Math.min(...pen); if (mn === pen[0]) x = o[0] - rad; else if (mn === pen[1]) x = o[1] + rad; else if (mn === pen[2]) z = o[2] - rad; else z = o[3] + rad; }
        }
      }
      return [x, z];
    }

    // ---------- chat ----------
    const chatEl = $("#lbChat"), chatIn = $("#lbChatIn");
    const addChat = (name, color, text) => {
      const mm = document.createElement("div"); mm.className = "m";
      if (name) { const b = document.createElement("b"); b.style.color = color || "#F1F2F5"; b.textContent = name; mm.appendChild(b); } else mm.classList.add("sys");
      mm.appendChild(document.createTextNode(text));
      chatEl.insertBefore(mm, chatIn);
      const all = $$(".m", chatEl); if (all.length > 5) all[0].remove();
      setTimeout(() => { mm.style.opacity = "0"; setTimeout(() => mm.remove(), 700); }, 10000);
    };
    let toastTimer = 0;
    const toast = (text) => { const el = $("#lbToast"); if (!el) return; el.textContent = text; el.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => { el.hidden = true; }, 5000); };
    const say = (text) => { text = String(text || "").trim(); if (!text) return; api("POST", code + "/say", { text }).catch((e) => toast(e.message)); };
    const openChat = () => {
      if (locked && document.exitPointerLock) document.exitPointerLock();
      if (kids) {
        const p = B.side(`<h3>Say something</h3><div class="lb-phrases">${(first.kids_phrases || L.kidsPhrases || []).map((t) => `<button class="opt" data-say="${esc(t)}">${esc(t)}</button>`).join("")}</div>`);
        p.onclick = (e) => { const b = e.target.closest("[data-say]"); if (b) { say(b.dataset.say); B.closeAll(); } };
        return;
      }
      chatIn.hidden = false; chatIn.focus();
    };
    const typing = () => document.activeElement === chatIn;

    // ---------- emotes: 1 wave, 2 clap, 3 laugh (everyone sees them) ----------
    let myAct = "", myActUntil = 0;
    const emote = (a) => { myAct = a; myActUntil = performance.now() + 2200; };
    const animateAct = (body, act, t) => {
      body.rotation.z = 0; body.position.y = 0;
      if (act === "wave") body.rotation.z = Math.sin(t * 9) * 0.18;
      else if (act === "clap") body.position.y = Math.abs(Math.sin(t * 14)) * 0.06;
      else if (act === "laugh") { body.rotation.z = Math.sin(t * 22) * 0.06; body.position.y = Math.abs(Math.sin(t * 11)) * 0.04; }
      else if (act === "popcorn") body.position.y = Math.abs(Math.sin(t * 6)) * 0.05;
    };

    // ---------- the movie on the TV ----------
    let hls = null, loadedItem = "", loadingMsg = "", playSession = null, sourceId = null, method = "DirectPlay";
    let tvRef = { pos: 0, t: performance.now() }; // where the TV should be, and when we heard
    const streamUrl = (url) => "api/web/stream" + (url.startsWith("/") ? url : "/" + url);
    const withKey = (url) => url + (url.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token);
    const report = (what) => {
      if (!loadedItem) return Promise.resolve();
      return B.jf("POST", `/Sessions/Playing${what}`, { ItemId: loadedItem, MediaSourceId: sourceId, PlaySessionId: playSession, PositionTicks: Math.round(video.currentTime * B.TICKS), IsPaused: video.paused, PlayMethod: method, CanSeek: true }).catch(() => {});
    };
    async function loadMovie(itemId) {
      if (hls) { hls.destroy(); hls = null; }
      if (loadedItem) report("/Stopped");
      video.removeAttribute("src"); $$("track", video).forEach((t) => t.remove());
      loadedItem = ""; loadingMsg = "Getting it ready…"; showTvPicture();
      if (!itemId) { video.load(); return; }
      try {
        const it = await B.get(`/Users/${B.uid()}/Items/${itemId}?Fields=MediaSources`);
        const info = await B.jf("POST", `/Items/${itemId}/PlaybackInfo?userId=${B.uid()}`, {
          DeviceProfile: B.deviceProfile(20000000), MaxStreamingBitrate: 20000000, StartTimeTicks: 0,
          EnableDirectPlay: true, EnableDirectStream: true, EnableTranscoding: true, AutoOpenLiveStream: true,
        });
        if (closed || tv.item !== itemId) return;
        const source = (info.MediaSources || [])[0];
        if (!source) throw new Error("Jellyfin couldn't find a way to play this.");
        playSession = info.PlaySessionId; sourceId = source.Id;
        if (source.SupportsDirectPlay && !source.TranscodingUrl) {
          method = "DirectPlay";
          video.src = streamUrl(`/Videos/${itemId}/stream?static=true&mediaSourceId=${enc(source.Id)}&api_key=${enc(B.me.token)}`);
        } else if (source.TranscodingUrl) {
          method = "Transcode";
          const url = streamUrl(withKey(source.TranscodingUrl));
          if (window.Hls && Hls.isSupported()) {
            hls = new Hls({ maxBufferLength: 40, backBufferLength: 60 });
            hls.loadSource(url); hls.attachMedia(video);
            hls.on(Hls.Events.ERROR, (e, d) => { if (!d.fatal) return; if (d.type === Hls.ErrorTypes.NETWORK_ERROR) hls.startLoad(); else if (d.type === Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError(); });
          } else video.src = url;
        } else throw new Error("This one can't play in a browser.");
        // Your own subtitles (shown under the TV for you only), when your profile has them on
        const sub = (source.MediaStreams || []).find((s) => s.Index === source.DefaultSubtitleStreamIndex && s.DeliveryMethod === "External" && s.DeliveryUrl);
        if (sub) {
          const tr = document.createElement("track"); tr.kind = "subtitles"; tr.srclang = sub.Language || "en";
          tr.src = streamUrl(withKey(sub.DeliveryUrl.replace(/\/Stream\.\w+/, "/Stream.vtt")));
          video.appendChild(tr);
          const tt = video.textTracks[0]; if (tt) { tt.mode = "hidden"; tt.oncuechange = () => { const c = tt.activeCues && tt.activeCues[0]; const el = $("#lbSub"); if (!el) return; el.textContent = c ? c.text.replace(/<[^>]+>/g, "") : ""; el.hidden = !c; }; }
        }
        loadedItem = itemId; loadingMsg = "";
        B.lastPlayed = { id: it.Id, title: L.titleOf(it) };
        video.addEventListener("loadeddata", () => { showTvPicture(); report(""); }, { once: true });
      } catch (e) {
        loadingMsg = "This one won't play here"; showTvPicture(); toast(e.message);
      }
    }
    const wantPos = () => tvRef.pos + (tv.playing ? (performance.now() - tvRef.t) / 1000 : 0);
    function keepInStep() {
      if (!loadedItem || video.readyState < 1) return;
      const want = wantPos(), diff = want - video.currentTime;
      if (Math.abs(diff) > 2 || (!tv.playing && Math.abs(diff) > 0.3)) { try { video.currentTime = want; } catch (e) {} video.playbackRate = 1; }
      else if (tv.playing && Math.abs(diff) > 0.15) video.playbackRate = diff > 0 ? 1.05 : 0.95;
      else video.playbackRate = 1;
      if (tv.playing && video.paused && started) video.play().catch(() => {});
      if (!tv.playing && !video.paused) video.pause();
      // Movie sound: full near the TV, a bit quieter at the back of the room (unless "always full")
      const d = world ? Math.hypot(player.x - world.tv.x, player.z - world.tv.z) : 0;
      video.volume = look.fullSound || player.sit >= 0 ? 1 : Math.max(0.35, Math.min(1, 1 - (d - 5) / 14));
    }
    const tvSend = (action, extra) => api("POST", code + "/tv", Object.assign({ action }, extra || {})).then((t) => { gotTv(t, 0); }).catch((e) => toast(e.message));
    function gotTv(t, rtt) {
      const changed = t.item !== tv.item;
      tv = t;
      tvRef = { pos: t.pos + (t.playing ? rtt / 2000 : 0), t: performance.now() };
      if (changed) loadMovie(t.item);
      showTvPicture(); drawBoard();
    }
    async function tvPanel() {
      if (locked && document.exitPointerLock) document.exitPointerLock();
      const may = mayControl && !kids;
      const p = B.side(`<h3>The TV</h3>
        ${tv.item ? `<p>On now: <b>${esc(tv.title)}</b></p>` : `<p class="dim">Nothing on yet.</p>`}
        ${tv.item && mayControl ? `<div class="acts"><button class="btn white" data-tv="${tv.playing ? "pause" : "play"}">${I(tv.playing ? "pause" : "play")} ${tv.playing ? "Pause" : "Play"} for everyone</button>
          <button class="btn" data-tv="back">Back 10s</button><button class="btn" data-tv="fwd">Forward 30s</button></div>` : ""}
        ${tv.item && video.readyState >= 2 ? `<div class="acts"><button class="btn" data-full>${I("full")} Movie fills my screen (F)</button></div>` : ""}
        ${may ? `<h4 class="dim" style="margin:18px 0 8px">Put something on</h4><input class="lb-find" id="lbFind" placeholder="Find a movie or show" aria-label="Find a movie or show"><div id="lbFound">${B.spinner()}</div>` : `<p class="dim">${esc(first.host_name)} picks what's on${mayControl ? "" : " and presses play"}.</p>`}`);
      p.addEventListener("click", (e) => {
        const b = e.target.closest("[data-tv],[data-pick],[data-full]"); if (!b) return;
        if (b.hasAttribute("data-full")) { B.closeAll(); return fillScreen(true); }
        if (b.dataset.tv === "back" || b.dataset.tv === "fwd") return tvSend("seek", { pos: Math.max(0, wantPos() + (b.dataset.tv === "back" ? -10 : 30)) });
        if (b.dataset.tv) { B.closeAll(); return tvSend(b.dataset.tv, { pos: wantPos() }); }
        if (b.dataset.pick) { B.closeAll(); pickTitle(b.dataset.pick); }
      });
      if (!may) return;
      const row = (it) => `<button class="opt lb-found" data-pick="${esc(it.Id)}">${esc(it.Type === "Episode" ? L.titleOf(it) : it.Name)}${it.ProductionYear ? ` <span class="dim">${it.ProductionYear}</span>` : ""}</button>`;
      const resume = await B.get(`/Users/${B.uid()}/Items/Resume?Limit=8&MediaTypes=Video&Fields=OfficialRating`).then((r) => r.Items || []).catch(() => []);
      const found = $("#lbFound"); if (!found) return;
      found.innerHTML = resume.length ? `<p class="dim" style="font-size:14px">Keep watching</p>${resume.map(row).join("")}` : "";
      let timer;
      $("#lbFind").oninput = (e) => {
        clearTimeout(timer);
        timer = setTimeout(async () => {
          const q = e.target.value.trim(); if (!q) return;
          const got = await B.get(`/Users/${B.uid()}/Items?SearchTerm=${enc(q)}&IncludeItemTypes=Movie,Series,Episode&Recursive=true&Limit=12&Fields=OfficialRating,ProductionYear`).then((r) => r.Items || []).catch(() => []);
          if ($("#lbFound")) $("#lbFound").innerHTML = got.map(row).join("") || '<p class="dim">Nothing found.</p>';
        }, 300);
      };
      $("#lbFind").focus();
    }
    async function pickTitle(id) {
      let it = await B.get(`/Users/${B.uid()}/Items/${id}?Fields=OfficialRating`).catch(() => null);
      if (it && it.Type === "Series") {
        const next = await B.get(`/Shows/NextUp?userId=${B.uid()}&SeriesId=${id}&Limit=1&enableResumable=true`).then((r) => (r.Items || [])[0]).catch(() => null)
          || await B.get(`/Shows/${id}/Episodes?userId=${B.uid()}&Limit=1`).then((r) => (r.Items || [])[0]).catch(() => null);
        if (!next) return toast("No episodes of that one yet.");
        it = Object.assign(next, { OfficialRating: next.OfficialRating || it.OfficialRating });
      }
      if (!it) return toast("Couldn't find that one.");
      const pos = it.UserData && it.UserData.PlaybackPositionTicks && !it.UserData.Played ? it.UserData.PlaybackPositionTicks / B.TICKS : 0;
      await tvSend("set", { item: it.Id, title: L.titleOf(it), kids_ok: L.kidsOk(it.OfficialRating), pos });
      tvSend("play", { pos });
    }
    // F: the movie fills your screen while you stay in the lobby
    let full = false;
    const fillScreen = (want) => { full = want == null ? !full : want; $("#lb").classList.toggle("lb-full", full); if (full && locked && document.exitPointerLock) document.exitPointerLock(); };

    // ---------- settings (gear): your character, room (host), who can pause, quality ----------
    function gearPanel() {
      if (locked && document.exitPointerLock) document.exitPointerLock();
      const opt = (k, v, label, onIt) => `<button class="opt ${onIt ? "on" : ""}" data-${k}="${v}">${esc(label)}</button>`;
      const draw = () => `<h3>Lobby settings</h3>
        ${hostNow ? `<h4 class="dim">Room (moves everyone)</h4>${Object.entries(ROOM_NAMES).map(([k, n]) => opt("room", k, n, k === roomKey)).join("")}
          <h4 class="dim">Who can pause and skip</h4>${opt("remote", "0", "Only me", !anyoneRemote)}${opt("remote", "1", "Anyone in the lobby", anyoneRemote)}` : ""}
        <h4 class="dim">Your character</h4>${Object.entries(STYLE_NAMES).map(([k, n]) => opt("style", k, n, k === look.style)).join("")}
        <div class="lb-swatches">${["#FFB547", "#7AA7FF", "#3DD6C6", "#FF6F91", "#B69CFF", "#B9E36A"].map((c) => `<button class="lb-sw ${c === look.color ? "on" : ""}" data-color="${c}" style="background:${c}" aria-label="Colour"></button>`).join("")}</div>
        <h4 class="dim">Movie sound</h4>${opt("sound", "0", "Quieter at the back of the room", !look.fullSound)}${opt("sound", "1", "Always full", look.fullSound)}
        <h4 class="dim">Drawing quality</h4>${opt("q", "auto", "Auto", look.quality === "auto")}${opt("q", "full", "Full", look.quality === "full")}${opt("q", "lite", "Lite (older computers)", look.quality === "lite")}`;
      const p = B.side(draw());
      p.onclick = (e) => {
        const b = e.target.closest("[data-room],[data-remote],[data-style],[data-color],[data-sound],[data-q]"); if (!b) return;
        const d = b.dataset;
        if (d.room) api("POST", code + "/settings", { room: d.room }).catch((er) => toast(er.message));
        if (d.remote) { anyoneRemote = d.remote === "1"; api("POST", code + "/settings", { anyone_remote: anyoneRemote }).catch((er) => toast(er.message)); }
        if (d.style) look.style = d.style;
        if (d.color) look.color = d.color;
        if (d.sound) look.fullSound = d.sound === "1";
        if (d.q) { look.quality = d.q; applyQuality(); }
        saveLook(look); drawBoard();
        if (d.room) { B.closeAll(); return; }
        p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button>` + draw();
      };
    }

    // ---------- input ----------
    const keys = {};
    let started = false, locked = false, dragging = false, lastX = 0, lastY = 0;
    const lockMouse = () => { if (isTouch) return; try { const pr = canvas.requestPointerLock && canvas.requestPointerLock(); if (pr && pr.catch) pr.catch(() => {}); } catch (e) {} };
    const begin = () => {
      started = true; $("#lbStart").hidden = true; $("#lbDot").hidden = false;
      stage.focus({ preventScroll: true }); lockMouse();
      if (tv.playing && loadedItem) video.play().catch(() => {});
    };
    $("#lbEnter").onclick = begin;
    on(document, "pointerlockchange", () => {
      locked = document.pointerLockElement === canvas;
      if (!locked && started && !isTouch && !typing() && !$(".side") && !full) { $("#lbStartTitle").textContent = "Paused · walk back in any time"; $("#lbEnter").textContent = "Keep walking"; $("#lbStart").hidden = false; $("#lbDot").hidden = true; }
    });
    on(canvas, "click", () => { if (started && !locked) lockMouse(); });
    on(document, "mousemove", (e) => { if (locked) look2(e.movementX, e.movementY); });
    on(canvas, "mousedown", (e) => { if (!locked) { dragging = true; lastX = e.clientX; lastY = e.clientY; } });
    on(window, "mouseup", () => { dragging = false; });
    on(window, "mousemove", (e) => { if (dragging && !locked) { look2((e.clientX - lastX) * 1.4, (e.clientY - lastY) * 1.4); lastX = e.clientX; lastY = e.clientY; } });
    function look2(dx, dy) { player.yaw -= dx * 0.0022; player.pitch = Math.max(-1.3, Math.min(1.3, player.pitch - dy * 0.0022)); }
    const MOVE = ["KeyW", "KeyA", "KeyS", "KeyD", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Space"];
    on(window, "keydown", (e) => {
      if (!started || $(".side")) return;
      if (typing()) {
        if (e.key === "Enter") { say(chatIn.value); chatIn.value = ""; chatIn.hidden = true; stage.focus({ preventScroll: true }); lockMouse(); }
        else if (e.key === "Escape") { chatIn.hidden = true; stage.focus({ preventScroll: true }); }
        return;
      }
      if (!(locked || stage.contains(document.activeElement))) return;
      if (MOVE.includes(e.code)) e.preventDefault();
      keys[e.code] = true;
      if (e.code === "KeyE") useNearest();
      if (e.code === "KeyR" && world.pool && !world.pool.moving) { rackBalls(world.pool); world.pool.moving = false; api("POST", code + "/pool", { balls: poolSnapshot(world.pool), push: [0, 0] }).then((r) => { poolN = r.n; }).catch(() => {}); toast("Balls racked again."); }
      if (e.code === "KeyT") tvPanel();
      if (e.code === "KeyF") fillScreen();
      if (e.code === "KeyP" && mayControl && tv.item) tvSend(tv.playing ? "pause" : "play", { pos: wantPos() });
      if (e.code === "Digit1") emote("wave"); if (e.code === "Digit2") emote("clap"); if (e.code === "Digit3") emote("laugh");
      if (e.key === "Enter") { e.preventDefault(); openChat(); }
    });
    on(window, "keyup", (e) => { keys[e.code] = false; });
    on(window, "blur", () => { Object.keys(keys).forEach((k) => { keys[k] = false; }); });
    $("#lbBack").onclick = () => B.go("#/lobby");
    $("#lbTvBtn").onclick = () => tvPanel();
    $("#lbGear").onclick = () => gearPanel();
    $("#lbTUse").onclick = () => useNearest();
    $("#lbTChat").onclick = () => openChat();
    on(chatIn, "blur", () => { if (isTouch && chatIn.value.trim()) { say(chatIn.value); chatIn.value = ""; chatIn.hidden = true; } });
    video.addEventListener("dblclick", () => fillScreen(false));

    // Touch: left thumb stick walks, dragging anywhere else looks around
    const touchMove = { x: 0, y: 0 }; let stickId = null, lookId = null, lookX = 0, lookY = 0;
    const stick = $("#lbStick"), knob = $("#lbKnob");
    const moveStick = (t) => { const r = stick.getBoundingClientRect(); let dx = t.clientX - (r.left + r.width / 2), dy = t.clientY - (r.top + r.height / 2); const mg = Math.hypot(dx, dy), max = r.width / 2; if (mg > max) { dx *= max / mg; dy *= max / mg; } knob.style.transform = `translate(${dx}px,${dy}px)`; touchMove.x = dx / max; touchMove.y = dy / max; };
    on(stage, "touchstart", (e) => {
      if (!started) return;
      for (const t of e.changedTouches) {
        if (t.target.closest && t.target.closest(".lb-tbtns, input, .lb-hud")) return;
        const r = stick.getBoundingClientRect();
        if (stickId === null && t.clientX < r.right + 40 && t.clientY > r.top - 40) { stickId = t.identifier; moveStick(t); }
        else if (lookId === null) { lookId = t.identifier; lookX = t.clientX; lookY = t.clientY; }
      }
      e.preventDefault();
    }, { passive: false });
    on(stage, "touchmove", (e) => {
      for (const t of e.changedTouches) {
        if (t.identifier === stickId) moveStick(t);
        else if (t.identifier === lookId) { look2((t.clientX - lookX) * 2.2, (t.clientY - lookY) * 2.2); lookX = t.clientX; lookY = t.clientY; }
      }
      e.preventDefault();
    }, { passive: false });
    const endTouch = (e) => { for (const t of e.changedTouches) { if (t.identifier === stickId) { stickId = null; touchMove.x = touchMove.y = 0; knob.style.transform = ""; } if (t.identifier === lookId) lookId = null; } };
    on(stage, "touchend", endTouch); on(stage, "touchcancel", endTouch);

    // ---------- E: sit, shoot, use ----------
    let nearest = null;
    const seatTaken = (i) => Object.values(others).some((o) => o.sit === i);
    function findNearest() {
      if (player.sit >= 0) return { label: isTouch ? "Move to stand up" : "Move or press <kbd>E</kbd> to stand up", act: null };
      let best = null, bd = 1e9;
      const p = world.pool;
      if (p) { const lx = player.x - p.wx, lz = player.z - p.wz; const d = Math.hypot(Math.max(0, Math.abs(lx) - (p.hx + 0.2)), Math.max(0, Math.abs(lz) - (p.hz + 0.2))); if (d < 0.9) { best = { label: p.moving ? "Wait for the balls to stop" : "<kbd>E</kbd> Shoot where you look · <kbd>R</kbd> Rack again", act: shoot }; bd = d; } }
      world.seats.forEach((s, i) => { const d = Math.hypot(player.x - s.x, player.z - s.z); if (d < 1.25 && d < bd) { bd = d; best = seatTaken(i) ? { label: "Someone is sitting here", act: null } : { label: "<kbd>E</kbd> Sit down", act: () => sit(i) }; } });
      for (const u of world.use) { const d = Math.hypot(player.x - u.x, player.z - u.z); if (d < u.r && d < bd) { bd = d; best = u; } }
      return best;
    }
    function useNearest() { if (player.sit >= 0) return stand(); if (nearest && nearest.act) nearest.act(); }
    function sit(i) { const s = world.seats[i]; player.sit = i; player.yaw = Math.atan2(-(world.tv.x - s.x), -(world.tv.z - s.z)); player.pitch = 0.02; }
    function stand() { const s = world.seats[player.sit]; player.sit = -1; if (!s) return; const r = collide(s.x - Math.sin(player.yaw) * 0.9, s.z - Math.cos(player.yaw) * 0.9, 0.3); player.x = r[0]; player.z = r[1]; }
    function shoot() {
      const p = world.pool; if (p.moving) return; const cue = p.balls[0];
      let dx = -Math.sin(player.yaw), dz = -Math.cos(player.yaw);
      const tx = p.wx + cue.x - player.x, tz = p.wz + cue.z - player.z, tl = Math.hypot(tx, tz);
      if (tl > 0.01 && (dx * tx + dz * tz) / tl > 0.6) { dx = tx / tl; dz = tz / tl; }
      const shot = { balls: poolSnapshot(p), push: [+(dx * 3.6).toFixed(4), +(dz * 3.6).toFixed(4)] };
      poolApply(p, shot);
      api("POST", code + "/pool", shot).then((r) => { poolN = r.n; }).catch(() => {});
    }

    // ---------- talking to Tower: send my spot, get everyone else's ----------
    let chatN = 0, poolN = 0, mayControl = first.may_control, hostNow = first.host, anyoneRemote = first.anyone_remote, syncing = false, fails = 0;
    async function sync() {
      if (closed || syncing) return;
      syncing = true;
      const sent = performance.now();
      try {
        const a = myActUntil > sent ? myAct : "";
        const d = await api("POST", code + "/sync", { me: { x: player.sit >= 0 ? world.seats[player.sit].x : player.x, z: player.sit >= 0 ? world.seats[player.sit].z : player.z, yaw: player.yaw, sit: player.sit, style: look.style, color: look.color, act: a }, chat_since: chatN, pool_since: poolN });
        if (closed) return;
        fails = 0;
        const rtt = performance.now() - sent;
        if (d.room !== roomKey) { buildWorld(d.room); if (chatN) toast(`Everyone moved to the ${ROOM_NAMES[d.room].toLowerCase()}.`); }
        mayControl = d.may_control; hostNow = d.host; anyoneRemote = d.anyone_remote; first.host_name = d.host_name;
        $("#lbTvBtn").hidden = !mayControl && !tv.item;
        // Chat (on first arrival only the last few lines)
        const fresh = chatN ? d.chat : d.chat.slice(-3);
        fresh.forEach((c) => {
          addChat(c.name, c.color, c.text);
          const o = others[c.uid]; if (o) { if (o.bubble) o.av.remove(o.bubble); o.bubble = bubbleSprite(c.text); o.bubble.position.y = 2.3; o.av.add(o.bubble); o.bubbleUntil = performance.now() + 5000; }
        });
        chatN = d.chat_n;
        // People
        const seen = new Set();
        d.people.forEach((p) => {
          seen.add(p.id);
          let o = others[p.id];
          if (o && (o.style !== p.style || o.color !== p.color)) { scene.remove(o.av); o = null; }
          if (!o) { o = others[p.id] = Object.assign(others[p.id] || {}, { av: makeAvatar(p.style, p.color, p.name), x: p.x, z: p.z, yaw: p.yaw }); scene.add(o.av); }
          Object.assign(o, { name: p.name, style: p.style, color: p.color, tx: p.x, tz: p.z, tyaw: p.yaw, sit: p.sit, act: p.act });
        });
        Object.keys(others).forEach((id) => { if (!seen.has(id)) { scene.remove(others[id].av); delete others[id]; } });
        // The TV
        if (d.tv.n !== tv.n || d.tv.item !== tv.item) gotTv(d.tv, rtt);
        else if (Math.abs((d.tv.pos + (d.tv.playing ? rtt / 2000 : 0)) - wantPos()) > 0.25) tvRef = { pos: d.tv.pos + (d.tv.playing ? rtt / 2000 : 0), t: performance.now() };
        // Pool: somebody else's shot plays the same here
        if (d.pool) { if (poolN && d.pool.uid !== B.me.id && world.pool && d.pool.balls) poolApply(world.pool, d.pool); else if (!poolN && world.pool && d.pool.balls) { poolApply(world.pool, d.pool); for (let i = 0; i < 240 * 30 && world.pool.moving; i++) poolTick(world.pool); world.pool.moving = true; stepPool(world.pool, 0); world.pool.moving = false; } poolN = d.pool.n; }
        if (seen.size !== boardCount) { boardCount = seen.size; drawBoard(); }
      } catch (e) {
        if (closed) return;
        if (e.status === 404 || e.status === 403) { closed = true; showGone(e.message); }
        else if (++fails === 5) toast("Lost touch with Tower. Trying again…");
      } finally { syncing = false; }
    }
    let boardCount = -1;
    const syncTimer = setInterval(sync, 120);
    const progressTimer = setInterval(() => { if (loadedItem && !video.paused) report("/Progress"); }, 20000);
    const showGone = (why) => {
      $("#lbStart").hidden = false; $("#lbDot").hidden = true;
      $("#lbStartTitle").textContent = why || "This lobby has closed.";
      $("#lbEnter").textContent = "Back to the Lobby page"; $("#lbEnter").onclick = () => B.go("#/lobby");
      if (document.exitPointerLock && locked) document.exitPointerLock();
      video.pause();
    };

    // ---------- every frame ----------
    let last = performance.now(), frames = 0, fpsT = 0, raf = 0, visible = true;
    const t0 = performance.now();
    const io = new IntersectionObserver((en) => { visible = en[0].isIntersecting; }); io.observe(stage);
    function frame(now) {
      raf = requestAnimationFrame(frame);
      if (!visible || document.hidden || closed && !scene) { last = now; return; }
      const elapsed = now - last; if (elapsed < 1000 / fpsCap - 2) return;
      const dt = Math.min(0.05, elapsed / 1000); last = now; const t = (now - t0) / 1000;
      frames++; fpsT += elapsed; if (fpsT > 500) { $("#lbFps").textContent = Math.round((frames * 1000) / fpsT) + " fps"; frames = 0; fpsT = 0; }
      if (started && !typing() && !full) {
        let f = 0, s = 0;
        if (keys.KeyW || keys.ArrowUp) f += 1; if (keys.KeyS || keys.ArrowDown) f -= 1; if (keys.KeyD || keys.ArrowRight) s += 1; if (keys.KeyA || keys.ArrowLeft) s -= 1;
        f -= touchMove.y; s += touchMove.x;
        const mg = Math.hypot(f, s);
        if (mg > 0.08) {
          if (player.sit >= 0) stand();
          const run = keys.ShiftLeft || keys.ShiftRight || (mg > 0.97 && isTouch);
          const sp = ((run ? 4.6 : 2.6) * dt * Math.min(1, mg)) / Math.max(1, mg);
          const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw), rx = Math.cos(player.yaw), rz = -Math.sin(player.yaw);
          const r = collide(player.x + (fx * f + rx * s) * sp, player.z + (fz * f + rz * s) * sp, 0.3); player.x = r[0]; player.z = r[1];
        }
      }
      const seat = player.sit >= 0 ? world.seats[player.sit] : null;
      const px = seat ? seat.x : player.x, pz = seat ? seat.z : player.z;
      const targetY = seat ? seat.y + 0.62 : world.floor(player.x, player.z) + 1.6;
      player.y += (targetY - player.y) * Math.min(1, dt * 10);
      camera.position.set(px, player.y, pz); camera.rotation.set(player.pitch, player.yaw, 0);
      // Other people glide to where Tower last saw them
      const k = Math.min(1, dt * 10);
      Object.values(others).forEach((o) => {
        const st = o.sit >= 0 ? world.seats[o.sit] : null;
        if (st) { o.x = st.x; o.z = st.z; } else { o.x += (o.tx - o.x) * k; o.z += (o.tz - o.z) * k; }
        let dy = o.tyaw - o.yaw; dy = Math.atan2(Math.sin(dy), Math.cos(dy)); o.yaw += dy * k;
        const moving = !st && Math.hypot(o.tx - o.x, o.tz - o.z) > 0.03;
        o.av.position.set(o.x, st ? st.y - 0.45 : world.floor(o.x, o.z), o.z);
        o.av.rotation.y = st ? Math.atan2(world.tv.x - st.x, world.tv.z - st.z) : o.yaw + Math.PI;
        o.av.userData.body.rotation.x = st ? -0.12 : 0;
        if (o.act) animateAct(o.av.userData.body, o.act, t);
        else { o.av.userData.body.position.y = moving ? Math.abs(Math.sin(t * 7)) * 0.04 : 0; o.av.userData.body.rotation.z = moving ? Math.sin(t * 7) * 0.04 : 0; }
        if (o.bubble && now > o.bubbleUntil) { o.av.remove(o.bubble); o.bubble.material.map.dispose(); o.bubble.material.dispose(); o.bubble = null; }
      });
      if (world.flicker) world.flicker.intensity = 1.2 + Math.sin(t * 13) * 0.15 + Math.sin(t * 7.3) * 0.2;
      // Lights dim while the movie plays
      const dim = tv.playing && loadedItem ? 0.35 : 1;
      (world.room || []).forEach((l) => { l.userData.base = l.userData.base || l.intensity; l.intensity += (l.userData.base * dim - l.intensity) * Math.min(1, dt * 2); });
      world.tvLight.intensity = loadedItem && video.readyState >= 2 ? 1.1 : 0.5;
      if (world.pool) stepPool(world.pool, dt);
      if (Math.floor(t * 4) !== Math.floor((t - dt) * 4)) keepInStep();
      if (started) {
        nearest = findNearest(); const pr = $("#lbPrompt");
        if (nearest && !full) { pr.innerHTML = isTouch ? nearest.label.replace(/<kbd>E<\/kbd>/g, "<kbd>Use</kbd>").replace(/ · <kbd>R<\/kbd> Rack again/, "") : nearest.label; pr.hidden = false; } else pr.hidden = true;
      }
      renderer.render(scene, camera);
    }

    buildWorld(first.room);
    gotTv(first.tv, 0);
    if (first.host && sessionStorage.getItem("benflix.lobby.autoplay") === code && first.tv.item && !first.tv.playing) {
      sessionStorage.removeItem("benflix.lobby.autoplay");
      tvSend("play", { pos: first.tv.pos });
    }
    raf = requestAnimationFrame(frame);
    sync();

    B.onLeave(() => {
      closed = true;
      clearInterval(syncTimer); clearInterval(progressTimer); cancelAnimationFrame(raf); io.disconnect();
      cleanups.forEach((f) => f());
      if (document.exitPointerLock && document.pointerLockElement) document.exitPointerLock();
      report("/Stopped");
      if (hls) hls.destroy();
      video.pause(); video.removeAttribute("src");
      api("POST", code + "/leave").catch(() => {});
      disposeScene(); renderer.dispose();
    });
  }
})();
