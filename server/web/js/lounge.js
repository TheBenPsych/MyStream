/* Glowfin web: the Harbor Lounge, a Lobby place (designed 2026-11-20, thread "Design the Harbor Lounge").
   A two-floor boathouse on the harbor at night, in real sizes: a 10 m screen with a big U sofa, pool, air hockey,
   a giant four in a row, darts, shuffleboard, a jukebox, a fireplace, an aquarium wall, a snack bar, a loft with
   a slide, and outside a deck, a pier with fishing and a coin viewer. Walk up to a game and press E to play.
   62 seats, one person each. This file runs inside web/lounge.html, which the Lobby (js/lobby.js) shows in a
   frame when the host picks "Harbor Lounge". The Lobby asks this page where you are (GF_LOUNGE.me) and tells it
   where everyone else is (GF_LOUNGE.setPeople). Map: x 0-36 left to right, y 0 (back wall) to 20 (front
   windows), the deck to y 25.5 and the pier to y 33, in metres. The checks (boxCheck, reachCheck, seatCheck,
   walkCheck) are kept on window.WH so they can be run from the browser console. */
(() => {
  "use strict";
  const T = window.THREE;
  if (T && !T.__look) {
    T.__look = 1;
    const sh = T.Color.prototype.setHex, ss = T.Color.prototype.setStyle;
    T.Color.prototype.setHex = function (h) { sh.call(this, h); return this.convertSRGBToLinear(); };
    T.Color.prototype.setStyle = function (v) { if (/^#[0-9a-f]{6}$/i.test(v)) return this.setHex(parseInt(v.slice(1), 16)); ss.call(this, v); return this.convertSRGBToLinear(); };
    const Std = T.MeshStandardMaterial;
    T.MeshLambertMaterial = function (p) { return new Std(Object.assign({ roughness: 0.6, metalness: 0.02, envMapIntensity: 0.35 }, p)); };
  }
  const stage = document.getElementById("stage");
  const fpsEl = document.getElementById("fps");
  if (!T || !stage) { const n = document.getElementById("noGL"); if (n) n.hidden = false; return; }
  let renderer;
  try { renderer = new T.WebGLRenderer({ canvas: stage, antialias: true, powerPreference: "high-performance" }); }
  catch (e) { document.getElementById("noGL").hidden = false; return; }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
  renderer.outputEncoding = T.sRGBEncoding; renderer.toneMapping = T.ACESFilmicToneMapping; renderer.toneMappingExposure = 0.9;
  renderer.shadowMap.enabled = true; renderer.shadowMap.type = T.PCFSoftShadowMap; renderer.shadowMap.autoUpdate = false;
  const scene = new T.Scene();
  scene.background = new T.Color(0x070b1c);
  scene.fog = new T.Fog(0x070b1c, 60, 240);
  const camera = new T.PerspectiveCamera(70, 16 / 9, 0.05, 260);
  camera.rotation.order = "YXZ";
  scene.add(new T.HemisphereLight(0xc9d2ff, 0x3a2c22, 0.42));
  const sun = new T.DirectionalLight(0xffe9d2, 0.95); sun.position.set(-9, 30, 16); scene.add(sun); scene.add(sun.target);
  sun.castShadow = true; sun.shadow.mapSize.set(4096, 4096); sun.shadow.bias = -0.0005; sun.shadow.normalBias = 0.04; sun.shadow.radius = 2.5;
  Object.assign(sun.shadow.camera, { left: -30, right: 30, top: 30, bottom: -30, near: 1, far: 90 }); sun.shadow.camera.updateProjectionMatrix();
  if (T.RoomEnvironment) { const pm = new T.PMREMGenerator(renderer); scene.environment = pm.fromScene(new T.RoomEnvironment(), 0.04).texture; }

  // ---------- groups ----------
  const G = { main: new T.Group(), ceil: new T.Group(), lid: new T.Group(), upper: new T.Group(), fx: new T.Group(), out: new T.Group() };
  Object.values(G).forEach((g) => scene.add(g));

  // Map coords: x 0..36 left to right, y 0 (back wall, big screen) .. 20 (harbor windows), deck and pier beyond. z = height in metres.
  const WX = (x) => x - 18, WZ = (y) => y - 13;
  const BX = {}, SOL = [], OCC = [], ALLB = [], PARTS = [];
  function bx(x1, y1, x2, y2, b, t, c, o) {
    o = o || {};
    if (x1 > x2) [x1, x2] = [x2, x1];
    if (y1 > y2) [y1, y2] = [y2, y1];
    const k = (o.k || "lit") + "|" + (o.g || "main");
    (BX[k] || (BX[k] = [])).push([x1, y1, x2, y2, b, t, c]);
    ALLB.push({ x1, y1, x2, y2, b, t, c, k: o.k || "lit", g: o.g || "main", nm: o.nm || "", ok: o.ok || 0 });
    if (!o.ns) SOL.push({ x1, x2, y1, y2, b, t, st: o.st ? 1 : (o.nost ? -1 : 0) });
    if (o.k !== "glass" && o.k !== "cur" && !o.see) OCC.push({ x1, x2, y1, y2, b, t });
  }
  const sol = (x1, y1, x2, y2, b, t, st) => SOL.push({ x1, x2, y1, y2, b, t, st: st === -1 ? -1 : st ? 1 : 0 });
  // round or odd-shaped parts (spheres, cylinders) are listed here with their box outline so the floating / overlap check sees them too
  const reg = (x1, y1, x2, y2, b, t, nm, ok) => PARTS.push({ x1: Math.min(x1, x2), y1: Math.min(y1, y2), x2: Math.max(x1, x2), y2: Math.max(y1, y2), b, t, nm: nm || "part", ok: ok || 0, k: "part", g: "main" });
  const UNIT = new T.BoxGeometry(1, 1, 1);
  const ROUND = T.RoundedBoxGeometry ? new T.RoundedBoxGeometry(1, 1, 1, 2, 0.06) : UNIT;
  // ---------- surface looks: each kind of thing gets its own texture, mapped in real metres so nothing stretches ----------
  let lseed = 99; const lr = () => { lseed = (lseed * 16807) % 2147483647; return (lseed - 1) / 2147483646; };
  function looksTex(sz, paint) { const c = document.createElement("canvas"); c.width = c.height = sz; paint(c.getContext("2d"), sz); const t = new T.CanvasTexture(c); t.wrapS = t.wrapT = T.RepeatWrapping; t.anisotropy = 8; t.encoding = T.sRGBEncoding; return t; }
  const speck = (g, s, n, a) => { for (let i = 0; i < n; i++) { const v = lr() < 0.5 ? 255 : 0; g.fillStyle = `rgba(${v},${v},${v},${a * lr()})`; g.fillRect(lr() * s, lr() * s, 1 + lr() * 2, 1 + lr() * 2); } };
  const LOOK = {
    card: { tile: 0.9, rough: 0.92, metal: 0, edge: 0.03, dark: 0.62, tape: 1, tex: looksTex(256, (g, s) => { g.fillStyle = "#f2f2f2"; g.fillRect(0, 0, s, s); for (let y = 0; y < s; y += 4) { g.fillStyle = `rgba(0,0,0,${0.03 + lr() * 0.03})`; g.fillRect(0, y, s, 1.5); } speck(g, s, 1400, 0.10); g.fillStyle = "rgba(0,0,0,.18)"; g.fillRect(s * 0.12, s * 0.2, s * 0.32, s * 0.16); g.fillRect(s * 0.12, s * 0.42, s * 0.2, s * 0.05); g.strokeStyle = "rgba(0,0,0,.15)"; g.lineWidth = 3; g.strokeRect(s * 0.62, s * 0.58, s * 0.24, s * 0.24); }) },
    wrap: { tile: 1.6, rough: 0.28, metal: 0, edge: 0.05, dark: 0.7, tex: looksTex(256, (g, s) => { g.fillStyle = "#f4f4f4"; g.fillRect(0, 0, s, s); for (let i = 0; i < 40; i++) { g.strokeStyle = `rgba(${lr() < 0.5 ? "255,255,255" : "0,0,0"},${0.05 + lr() * 0.08})`; g.lineWidth = 2 + lr() * 8; const y = lr() * s; g.beginPath(); g.moveTo(0, y); g.bezierCurveTo(s * 0.3, y + 20 * (lr() - 0.5), s * 0.7, y + 30 * (lr() - 0.5), s, y + 10 * (lr() - 0.5)); g.stroke(); } }) },
    wood: { tile: 1.2, rough: 0.85, metal: 0, edge: 0.025, dark: 0.55, tex: looksTex(256, (g, s) => { g.fillStyle = "#efefef"; g.fillRect(0, 0, s, s); for (let i = 0; i < 160; i++) { g.strokeStyle = `rgba(60,30,10,${0.04 + lr() * 0.1})`; g.lineWidth = 0.5 + lr() * 1.8; const y = lr() * s; g.beginPath(); g.moveTo(0, y); for (let x = 0; x <= s; x += 32) g.lineTo(x, y + Math.sin(x * 0.05 + i) * 2); g.stroke(); } for (let y = 0; y < s; y += s / 6) { g.fillStyle = "rgba(30,15,5,.35)"; g.fillRect(0, y, s, 2.5); } speck(g, s, 300, 0.12); }) },
    metal: { tile: 1.0, rough: 0.42, metal: 0.35, edge: 0.012, dark: 0.7, tex: looksTex(128, (g, s) => { g.fillStyle = "#f0f0f0"; g.fillRect(0, 0, s, s); for (let y = 0; y < s; y++) { g.fillStyle = `rgba(0,0,0,${lr() * 0.05})`; g.fillRect(0, y, s, 1); } speck(g, s, 200, 0.12); }) },
    wall: { tile: 2.4, rough: 0.9, metal: 0, edge: 0.04, dark: 0.82, tex: looksTex(256, (g, s) => { g.fillStyle = "#f3f3f3"; g.fillRect(0, 0, s, s); speck(g, s, 2500, 0.08); g.fillStyle = "rgba(0,0,0,.16)"; const bh = s / 12; for (let r = 0; r < 12; r++) { g.fillRect(0, r * bh, s, 1.5); const off = r % 2 ? s / 6 : 0; for (let x = off; x < s; x += s / 3) g.fillRect(x, r * bh, 1.5, bh); } }) },
    product: { tile: 1.0, rough: 0.45, metal: 0.02, edge: 0.025, dark: 0.7, label: 1, tex: null },
    fabric: { tile: 0.5, rough: 0.95, metal: 0, edge: 0.05, dark: 0.66, tex: looksTex(128, (g, s) => { g.fillStyle = "#f0f0f0"; g.fillRect(0, 0, s, s); for (let y = 0; y < s; y += 2) { g.fillStyle = `rgba(0,0,0,${0.03 + lr() * 0.04})`; g.fillRect(0, y, s, 1); } for (let x = 0; x < s; x += 2) { g.fillStyle = `rgba(255,255,255,${0.02 + lr() * 0.04})`; g.fillRect(x, 0, 1, s); } speck(g, s, 500, 0.06); }) },
    felt: { tile: 0.4, rough: 1.0, metal: 0, edge: 0.012, dark: 0.8, tex: looksTex(128, (g, s) => { g.fillStyle = "#f2f2f2"; g.fillRect(0, 0, s, s); speck(g, s, 3000, 0.05); }) },
    stone: { tile: 1.4, rough: 0.95, metal: 0, edge: 0.04, dark: 0.7, tex: looksTex(256, (g, s) => { g.fillStyle = "#ececec"; g.fillRect(0, 0, s, s); let y = 0; while (y < s) { const h = 22 + lr() * 18; let x = -lr() * 40; while (x < s) { const w = 36 + lr() * 50; g.fillStyle = `rgba(${lr() < 0.5 ? "255,255,255" : "0,0,0"},${0.05 + lr() * 0.12})`; g.fillRect(x + 2, y + 2, w - 4, h - 4); g.fillStyle = "rgba(0,0,0,.35)"; g.fillRect(x, y, w, 2); g.fillRect(x, y, 2, h); x += w; } y += h; } speck(g, s, 1500, 0.1); }) },
    glossy: { tile: 1.0, rough: 0.18, metal: 0.05, edge: 0.015, dark: 0.75, tex: null },
    paint: { tile: 1.0, rough: 0.55, metal: 0.05, edge: 0.03, dark: 0.68, tex: looksTex(128, (g, s) => { g.fillStyle = "#f4f4f4"; g.fillRect(0, 0, s, s); speck(g, s, 500, 0.07); }) },
  };
  LOOK.product.tex = LOOK.paint.tex; LOOK.glossy.tex = LOOK.paint.tex;
  const TYPE = {};
  function initTypes() {
  C.woods.forEach((c) => (TYPE[c] = "wood"));
  C.metals.forEach((c) => (TYPE[c] = "metal"));
  C.fabrics.forEach((c) => (TYPE[c] = "fabric"));
  C.stones.forEach((c) => (TYPE[c] = "stone"));
  C.glossies.forEach((c) => (TYPE[c] = "glossy"));
  [C.felt].forEach((c) => (TYPE[c] = "felt"));
  C.card.forEach((c) => (TYPE[c] = "card"));
  }
  const MATS = {};
  function lookMat(type, emis) {
    const ck = type + (emis ? "|e" : "");
    if (MATS[ck]) return MATS[ck];
    const L = LOOK[type];
    const m = new T.MeshStandardMaterial({ color: 0xffffff, map: L.tex, roughness: L.rough, metalness: L.metal, envMapIntensity: 0.35, emissive: emis ? 0x2a2e36 : 0x000000 });
    m.onBeforeCompile = (sh) => {
      sh.uniforms.uTile = { value: L.tile }; sh.uniforms.uEdge = { value: L.edge }; sh.uniforms.uDark = { value: L.dark }; sh.uniforms.uTape = { value: L.tape ? 1 : 0 }; sh.uniforms.uLabel = { value: L.label ? 1 : 0 };
      sh.vertexShader = "varying vec3 vLoc; varying vec3 vScl; varying vec3 vNl; varying vec2 vSeed;\n" + sh.vertexShader.replace("#include <begin_vertex>", `#include <begin_vertex>
        vLoc = position; vNl = normal;
        #ifdef USE_INSTANCING
          vScl = vec3(length(instanceMatrix[0].xyz), length(instanceMatrix[1].xyz), length(instanceMatrix[2].xyz)); vSeed = fract(instanceMatrix[3].xz * 0.371);
        #else
          vScl = vec3(1.0); vSeed = vec2(0.0);
        #endif`);
      sh.fragmentShader = "uniform float uTile; uniform float uEdge; uniform float uDark; uniform float uTape; uniform float uLabel; varying vec3 vLoc; varying vec3 vScl; varying vec3 vNl; varying vec2 vSeed;\n" + sh.fragmentShader
        .replace("#include <map_fragment>", `
        vec3 an = abs(vNl); vec3 pm = vLoc * vScl; vec3 dd = max((0.5 - abs(vLoc)) * vScl, 0.0); vec2 tuv; float ed; bool top = false;
        if (an.x >= an.y && an.x >= an.z) { tuv = pm.zy; ed = min(dd.y, dd.z); }
        else if (an.y >= an.z) { tuv = pm.xz; ed = min(dd.x, dd.z); top = vNl.y > 0.0; }
        else { tuv = pm.xy; ed = min(dd.x, dd.y); }
        vec4 tx = texture2D(map, tuv / uTile + vSeed);
        diffuseColor.rgb *= tx.rgb;
        if (uTape > 0.5 && top && abs(pm.x) < 0.035 && vScl.x > 0.3) diffuseColor.rgb = vec3(0.55, 0.42, 0.25);
        if (uLabel > 0.5 && !top && vNl.y > -0.5 && vScl.y > 0.18 && abs(pm.y + 0.02 * vScl.y) < 0.22 * vScl.y) { diffuseColor.rgb = mix(diffuseColor.rgb, vec3(0.92), 0.82); if (abs(pm.y) < 0.05 * vScl.y && abs(tuv.x) < 0.3 * max(vScl.x, vScl.z)) diffuseColor.rgb *= 0.35; }
        diffuseColor.rgb *= mix(uDark, 1.0, smoothstep(0.0, uEdge, ed));`)
        .replace("#include <roughnessmap_fragment>", "#include <roughnessmap_fragment>\n roughnessFactor *= mix(1.15, 0.85, tx.g);");
    };
    m.customProgramCacheKey = () => "look-" + ck;
    return (MATS[ck] = m);
  }
  function flush() {
    const dummy = new T.Object3D(), col = new T.Color(); initTypes();
    Object.keys(BX).forEach((k) => {
      const [kind, grp] = k.split("|"), L = BX[k];
      const parts = {};
      L.forEach((r) => {
        let key = kind;
        if (kind === "lit") {
          const type = TYPE[r[6]] || "paint", dx = r[2] - r[0], dy = r[5] - r[4], dz = r[3] - r[1];
          const rounded = (type === "card" || type === "paint" || type === "fabric" || type === "glossy") && Math.max(dx, dy, dz) < 2.6 && Math.min(dx, dy, dz) > 0.09;
          key = (C.labels.includes(r[6]) ? "product" : type) + (rounded ? "|r" : "");
        }
        (parts[key] || (parts[key] = [])).push(r);
      });
      Object.keys(parts).forEach((pk) => {
        const list = parts[pk];
        let mat;
        if (kind === "glow") mat = new T.MeshBasicMaterial({ color: 0xffffff, toneMapped: false });
        else if (kind === "glass") mat = new T.MeshStandardMaterial({ color: 0xffffff, transparent: true, opacity: 0.18, roughness: 0.04, metalness: 0.1, depthWrite: false });
        else if (kind === "cur") mat = new T.MeshStandardMaterial({ color: 0xffffff, transparent: true, opacity: 0.4, roughness: 0.3, depthWrite: false });
        else mat = lookMat(pk.split("|")[0], grp === "ceil");
        const m = new T.InstancedMesh(pk.endsWith("|r") ? ROUND : UNIT, mat, list.length);
        list.forEach((r, i) => {
          dummy.position.set(WX((r[0] + r[2]) / 2), (r[4] + r[5]) / 2, WZ((r[1] + r[3]) / 2));
          dummy.scale.set(Math.max(r[2] - r[0], 0.01), Math.max(r[5] - r[4], 0.01), Math.max(r[3] - r[1], 0.01));
          dummy.updateMatrix(); m.setMatrixAt(i, dummy.matrix);
          m.setColorAt(i, col.set(r[6]));
        });
        if (m.instanceColor) m.instanceColor.needsUpdate = true;
        if (kind === "glass" || kind === "cur") m.renderOrder = 2;
        if (kind === "lit") { m.castShadow = grp !== "ceil"; m.receiveShadow = true; }
        G[grp].add(m);
      });
    });
    contactShadows();
  }
  // soft dark patches on the floor under everything that stands on it (cheap "ambient occlusion")
  function contactShadows() {
    const W = 1520, H = 1400, sx = W / 38, sy = H / 35; // floor plane: x -1..37, y -1..34
    const c = document.createElement("canvas"); c.width = W; c.height = H; const g = c.getContext("2d");
    g.fillStyle = "#000"; g.fillRect(0, 0, W, H);
    const src = document.createElement("canvas"); src.width = W; src.height = H; const q = src.getContext("2d");
    const draw = (blur, grow, a) => { q.clearRect(0, 0, W, H); q.fillStyle = "#fff";
      Object.keys(BX).forEach((k) => { if (!k.startsWith("lit|main") && !k.startsWith("lit|lid") && !k.startsWith("lit|upper")) return; BX[k].forEach((r) => { if (r[4] > 0.06 || r[4] < -0.05 || r[5] < 0.25) return; q.fillRect((r[0] + 1 - grow) * sx, (r[1] + 1 - grow) * sy, (r[2] - r[0] + grow * 2) * sx, (r[3] - r[1] + grow * 2) * sy); }); });
      g.filter = `blur(${blur}px)`; g.globalAlpha = a; g.drawImage(src, 0, 0); g.filter = "none"; g.globalAlpha = 1; };
    draw(14, 0.25, 0.35); draw(4, 0.06, 0.45);
    const t = new T.CanvasTexture(c);
    const m = new T.Mesh(new T.PlaneGeometry(38, 35), new T.MeshBasicMaterial({ color: 0x000000, alphaMap: t, transparent: true, opacity: 0.85, depthWrite: false, toneMapped: false }));
    m.rotation.x = -Math.PI / 2; m.position.set(WX(18), 0.016, WZ(16.5)); m.renderOrder = 1; G.main.add(m);
  }

  // seeded random
  let seed = 7;
  const rnd = () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646; };
  const pick = (a) => a[Math.floor(rnd() * a.length)];

  // ---------- textures and signs ----------
  function canvasTex(w, h, paint) {
    const c = document.createElement("canvas"); c.width = w; c.height = h;
    paint(c.getContext("2d"), w, h);
    const t = new T.CanvasTexture(c); t.anisotropy = 4; t.encoding = T.sRGBEncoding; return t;
  }
  const FONT = '"Glowfin Sora", system-ui, "Segoe UI", sans-serif';
  function signTex(text, bg, fg, sub) {
    return canvasTex(1024, 256, (g, w, h) => {
      g.fillStyle = bg; g.fillRect(0, 0, w, h);
      g.fillStyle = fg; g.textAlign = "center"; g.textBaseline = "middle";
      let size = sub ? 112 : 132; g.font = `800 ${size}px ${FONT}`;
      while (g.measureText(text).width > w - 80 && size > 40) { size -= 6; g.font = `800 ${size}px ${FONT}`; }
      g.fillText(text, w / 2, sub ? h * 0.4 : h / 2 + 4);
      if (sub) { g.font = `600 44px ${FONT}`; g.globalAlpha = 0.85; g.fillText(sub, w / 2, h * 0.8); }
    });
  }
  // a sign plane; ry=0 faces the front of the store (+y). two=true adds a back face.
  function sign(text, x, y, z, ry, w, o) {
    o = o || {};
    const h = w / 4, tex = signTex(text, o.bg || "#0f3d8a", o.fg || "#ffffff", o.sub);
    const mat = new T.MeshBasicMaterial({ map: tex, toneMapped: false });
    const grp = new T.Group(); grp.position.set(WX(x), z, WZ(y)); grp.rotation.y = ry || 0;
    const a = new T.Mesh(new T.PlaneGeometry(w, h), mat); a.position.z = 0.03; grp.add(a);
    if (o.two !== false) { const b = new T.Mesh(new T.PlaneGeometry(w, h), mat); b.rotation.y = Math.PI; b.position.z = -0.03; grp.add(b); }
    (o.g ? G[o.g] : G.main).add(grp); return grp;
  }
  function numTex(n, bg, fg) {
    return canvasTex(128, 128, (g) => {
      g.fillStyle = bg; g.beginPath(); g.arc(64, 64, 60, 0, Math.PI * 2); g.fill();
      g.fillStyle = fg; g.font = `800 66px ${FONT}`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(String(n), 64, 68);
    });
  }

  // ---------- floor: warm oak planks inside, weathered deck boards outside ----------
  const planks = (base, dark, s, rows, wob) => looksTex(1024, (g) => {
    g.fillStyle = base; g.fillRect(0, 0, s, s); const rh = s / rows;
    for (let r = 0; r < rows; r++) { let x = -lr() * s * 0.5; while (x < s) { const w = s * (0.35 + lr() * 0.45); const v = lr();
      g.fillStyle = `rgba(${v < 0.5 ? "255,240,220" : "40,20,8"},${0.04 + lr() * 0.09})`; g.fillRect(x, r * rh, w, rh);
      for (let k = 0; k < 9; k++) { g.strokeStyle = `rgba(50,25,10,${0.05 + lr() * 0.08})`; g.lineWidth = 0.6 + lr() * 1.4; const yy = r * rh + lr() * rh; g.beginPath(); g.moveTo(x, yy); for (let xx = x; xx <= x + w; xx += 24) g.lineTo(xx, yy + Math.sin(xx * 0.03 + k) * wob); g.stroke(); }
      g.fillStyle = dark; g.fillRect(x, r * rh, 2.5, rh); x += w; }
      g.fillStyle = dark; g.fillRect(0, r * rh, s, 2.5); }
    speck(g, s, 4000, 0.07); });
  const floorTex2 = planks("#b07a48", "rgba(40,20,8,.55)", 1024, 10, 1.6); floorTex2.repeat.set(36 / 4.8, 20 / 4.8);
  const floor = new T.Mesh(new T.PlaneGeometry(36, 20), new T.MeshStandardMaterial({ map: floorTex2, roughness: 0.42, metalness: 0.0, envMapIntensity: 0.4 }));
  floor.receiveShadow = true; floor.rotation.x = -Math.PI / 2; floor.position.set(WX(18), 0, WZ(10)); G.main.add(floor);
  const deckTex = planks("#8c7a66", "rgba(20,14,8,.7)", 1024, 7, 0.8); deckTex.rotation = Math.PI / 2; deckTex.repeat.set(5.5 / 3.2, 34 / 3.2);
  const deckFloor = new T.Mesh(new T.PlaneGeometry(34, 5.35), new T.MeshStandardMaterial({ map: deckTex, roughness: 0.8, envMapIntensity: 0.2 }));
  deckFloor.receiveShadow = true; deckFloor.rotation.x = -Math.PI / 2; deckFloor.position.set(WX(18), 0.002, WZ(22.825)); G.main.add(deckFloor);
  const pierTex = planks("#857462", "rgba(20,14,8,.7)", 512, 5, 0.8); pierTex.repeat.set(1, 3);
  const pierFloor = new T.Mesh(new T.PlaneGeometry(2.0, 7.5), new T.MeshStandardMaterial({ map: pierTex, roughness: 0.85, envMapIntensity: 0.2 }));
  pierFloor.receiveShadow = true; pierFloor.rotation.x = -Math.PI / 2; pierFloor.position.set(WX(27), 0.002, WZ(29.25)); G.main.add(pierFloor);

  // ---------- colours ----------
  const C = {
    navy: "#1f2750", navy2: "#2a3366", oak: "#a87b4f", oak2: "#7a5236", walnut: "#5b3d2a", trim: "#3a2a1c",
    felt: "#1f7a5c", brass: "#c9a45a", steel: "#aab1ba", dark: "#20232b", black: "#16181d", white: "#eef1f3",
    sofa: "#2f3a78", sofa2: "#3a4690", stone: "#8a8079", stone2: "#6f6660",
    card: ["#b8895a", "#a87b4f"],
    woods: ["#a87b4f", "#7a5236", "#5b3d2a", "#3a2a1c", "#9b6b43", "#c99a5c", "#8a6a4a", "#6b4a2e", "#b58a5a", "#d2b48c", "#4a3424", "#8c7a66", "#6e5e4e"],
    metals: ["#aab1ba", "#c9a45a", "#20232b", "#16181d", "#6d737c", "#4b5058", "#30353d", "#b8c0c8", "#9aa0a8", "#8a6d3b"],
    fabrics: ["#2f3a78", "#3a4690", "#7a3b5e", "#c25b4a", "#3d7a6b", "#d9c7a7", "#5b4a8a", "#e0b04a", "#2b6f8f", "#8f3b3b", "#4a5a8a"],
    stones: ["#8a8079", "#6f6660", "#5d5550"],
    glossies: ["#c8202c", "#1d6fb8", "#e8e8ea", "#2a1f55", "#0e3a52", "#7a1f2a"],
    labels: [],
    brand: ["#d94f4f", "#2f6db5", "#f2c14e", "#3d9a5b", "#7b4fb5", "#e07b39", "#f4f4f4", "#1d8a8a", "#c2185b"],
  };

  // ---------- generic helpers ----------
  // straight wall with gaps [[from,to]] along its length, from b to t. thick = 0.3
  function wallX(x1, x2, y, b, t, col, gaps, o) {
    let p = x1; (gaps || []).slice().sort((m, n) => m[0] - n[0]).forEach(([a, c]) => { if (a > p) bx(p, y - 0.15, a, y + 0.15, b, t, col, o); p = Math.max(p, c); });
    if (x2 > p) bx(p, y - 0.15, x2, y + 0.15, b, t, col, o);
  }
  function wallY(y1, y2, x, b, t, col, gaps, o) {
    let p = y1; (gaps || []).slice().sort((m, n) => m[0] - n[0]).forEach(([a, c]) => { if (a > p) bx(x - 0.15, p, x + 0.15, a, b, t, col, o); p = Math.max(p, c); });
    if (y2 > p) bx(x - 0.15, p, x + 0.15, y2, b, t, col, o);
  }
  // stairs: rect footprint, rising from (side) "from" to "to" (n/s/e/w), from z0 to z1
  function stairs(x1, y1, x2, y2, z0, z1, n, dir, col, open) {
    for (let i = 0; i < n; i++) {
      const h = z0 + (z1 - z0) * (i + 1) / n, a = i / n, b = (i + 1) / n;
      let r;
      if (dir === "n") r = [x1, y2 - (y2 - y1) * b, x2, y2 - (y2 - y1) * a];
      else if (dir === "s") r = [x1, y1 + (y2 - y1) * a, x2, y1 + (y2 - y1) * b];
      else if (dir === "e") r = [x1 + (x2 - x1) * a, y1, x1 + (x2 - x1) * b, y2];
      else r = [x2 - (x2 - x1) * b, y1, x2 - (x2 - x1) * a, y2];
      bx(r[0], r[1], r[2], r[3], h - 0.08, h, col || "#6d737c", { ns: 1 });
      if (!open) bx(r[0], r[1], r[2], r[3], z0, h - 0.08, "#4b5058", { ns: 1, see: 1 });
      else { const cy = (r[1] + r[3]) / 2, cx = (r[0] + r[2]) / 2, ax = dir === "n" || dir === "s";
        if (ax) { bx(r[0], cy - 0.03, r[0] + 0.06, cy + 0.03, z0, h - 0.08, col || "#6d737c", { ns: 1, see: 1 }); bx(r[2] - 0.06, cy - 0.03, r[2], cy + 0.03, z0, h - 0.08, col || "#6d737c", { ns: 1, see: 1 }); }
        else { bx(cx - 0.03, r[1], cx + 0.03, r[1] + 0.06, z0, h - 0.08, col || "#6d737c", { ns: 1, see: 1 }); bx(cx - 0.03, r[3] - 0.06, cx + 0.03, r[3], z0, h - 0.08, col || "#6d737c", { ns: 1, see: 1 }); } }
      sol(r[0], r[1], r[2], r[3], z0, h, 1);
    }
  }
  const rail = (x1, y1, x2, y2, z, col) => { bx(x1, y1, x2, y2, z + 0.95, z + 1.02, col || "#f2c14e", { ns: 1, see: 1 }); };

  // ---------- pallet racking ----------
  // runs along whichever side is longer. levels: beams at 2.0 / 4.0 and a solid top deck at 5.85.
  function rack(x1, y1, x2, y2, o) {
    o = o || {};
    const alongY = (y2 - y1) > (x2 - x1);
    const L0 = alongY ? y1 : x1, L1 = alongY ? y2 : x1 + (x2 - x1), D0 = alongY ? x1 : y1, D1 = alongY ? x2 : y2;
    const R = (l0, d0, l1, d1, b, t, c, oo) => alongY ? bx(d0, l0, d1, l1, b, t, c, oo) : bx(l0, d0, l1, d1, b, t, c, oo);
    const top = o.top || 5.85, n = Math.max(1, Math.round((L1 - L0) / 2.7)), bay = (L1 - L0) / n;
    for (let i = 0; i <= n; i++) {
      const l = L0 + i * bay, a = Math.max(L0, l - 0.06), b = Math.min(L1, l + 0.06);
      R(a, D0, b, D0 + 0.1, 0, top, C.blue, { ns: 1 }); R(a, D1 - 0.1, b, D1, 0, top, C.blue, { ns: 1 });
    }
    [2.0, 4.0].forEach((z) => { if (z < top) { R(L0, D0 - 0.02, L1, D0 + 0.08, z - 0.15, z, C.beam, { ns: 1 }); R(L0, D1 - 0.08, L1, D1 + 0.02, z - 0.15, z, C.beam, { ns: 1 }); } });
    R(L0, D0 - 0.02, L1, D0 + 0.08, top - 0.15, top - 0.05, C.beam, { ns: 1 }); R(L0, D1 - 0.08, L1, D1 + 0.02, top - 0.15, top - 0.05, C.beam, { ns: 1 });
    R(L0, D0, L1, D1, top - 0.08, top, C.deck, { ns: 1 });
    // stock: ground pallets, level 2 cases, level 3 shrink-wrapped
    const lv = [[0, 2.0 - 0.15], [2.0, 4.0 - 0.15], [4.0, top - 0.15]].filter((v) => v[0] < top - 0.3);
    for (let i = 0; i < n; i++) {
      const l = L0 + i * bay;
      lv.forEach(([z0, z1], li) => {
        const half = (bay - 0.16) / 2;
        for (let k = 0; k < 2; k++) {
          const s = l + 0.08 + k * half, e = s + half - 0.04;
          const hh = Math.min(z1 - z0 - 0.12, (li === 0 ? 1.35 : 1.25) + rnd() * 0.4);
          const col = li === 2 ? pick(C.wrap) : (rnd() < 0.45 ? pick(C.brand) : pick(C.card));
          R(s, D0 + 0.12, e, D1 - 0.12, z0 + 0.12, z0 + 0.12 + hh, col, { ns: 1 });
          R(s, D0, e, D1, z0, z0 + 0.12, "#a9855c", { ns: 1, see: 1 });
        }
      });
    }
    sol(alongY ? D0 : L0, alongY ? L0 : D0, alongY ? D1 : L1, alongY ? L1 : D1, 0, top);
    // overstock on the top deck (skipped where o.gap says)
    if (o.over !== false) {
      for (let i = 0; i < n * 2; i++) {
        const s = L0 + 0.1 + i * (bay / 2), e = s + bay / 2 - 0.2;
        if (o.gap && e > o.gap[0] && s < o.gap[1]) continue;
        if (rnd() < 0.12 && !o.full) continue;
        R(s, D0 + 0.05, e, D1 - 0.05, top, top + 1.05 + rnd() * 0.4, pick(C.wrap), {});
      }
    }
  }

  // ---------- doors (swing open when you walk up; seekers have to open them) ----------
  const DOORS = [];
  function door(x1, y1, x2, y2, b, h, col, hinge, to, room) {
    const alongX = (x2 - x1) > (y2 - y1), len = alongX ? x2 - x1 : y2 - y1, th = Math.max(0.05, alongX ? y2 - y1 : x2 - x1);
    const hx = alongX ? (hinge === "a" ? x1 : x2) : (x1 + x2) / 2, hy = alongX ? (y1 + y2) / 2 : (hinge === "a" ? y1 : y2);
    const sgn = hinge === "a" ? 1 : -1;
    let rot;
    if (alongX) rot = hinge === "a" ? (to === "n" ? 1 : -1) : (to === "s" ? 1 : -1);
    else rot = hinge === "a" ? (to === "e" ? 1 : -1) : (to === "w" ? 1 : -1);
    const pv = new T.Group(); pv.position.set(WX(hx), b, WZ(hy));
    const geo = alongX ? new T.BoxGeometry(len, h, th) : new T.BoxGeometry(th, h, len);
    const p = new T.Mesh(geo, new T.MeshLambertMaterial({ color: col }));
    if (alongX) p.position.set(sgn * len / 2, h / 2, 0); else p.position.set(0, h / 2, sgn * len / 2);
    pv.add(p);
    // small window strip so doors read as doors
    const win = new T.Mesh(alongX ? new T.BoxGeometry(len * 0.74, h * 0.74, th + 0.02) : new T.BoxGeometry(th + 0.02, h * 0.74, len * 0.74), new T.MeshStandardMaterial({ color: 0x9fc4ee, transparent: true, opacity: 0.28, roughness: 0.08, metalness: 0.2 }));
    if (alongX) win.position.set(sgn * len * 0.5, h * 0.54, 0); else win.position.set(0, h * 0.54, sgn * len * 0.5);
    if (h > 1.6) pv.add(win);
    G.main.add(pv);
    const occ = { x1, x2, y1, y2, b, t: b + h, door: room || 0 };
    OCC.push(occ);
    DOORS.push({ pv, rot, cx: (x1 + x2) / 2, cy: (y1 + y2) / 2, z: b, o: 0, kind: "door", room: room || 0 });
  }
  // a lid hinged on its back (north) edge that lifts up
  function lid(x1, y1, x2, y2, z, col, room) {
    const pv = new T.Group(); pv.position.set(WX(x1 + (x2 - x1) / 2), z, WZ(y1));
    const p = new T.Mesh(new T.BoxGeometry(x2 - x1, 0.1, y2 - y1), new T.MeshLambertMaterial({ color: col }));
    p.position.set(0, 0.05, (y2 - y1) / 2); pv.add(p); G.main.add(pv);
    OCC.push({ x1, x2, y1, y2, b: z, t: z + 0.1, door: room });
    DOORS.push({ pv, cx: (x1 + x2) / 2, cy: (y1 + y2) / 2, z, o: 0, kind: "lid", room });
  }

  // ---------- sea friend ----------
  const matCache = {};
  const lam = (c, e) => { const k = c + "|" + (e || 0); return matCache[k] || (matCache[k] = new T.MeshLambertMaterial({ color: c, emissive: e || 0 })); };
  const SPH = new T.SphereGeometry(1, 16, 12);
  function friend(col, s, it) {
    const g = new T.Group();
    const body = new T.Mesh(SPH, lam(col, it ? 0x6a1030 : 0)); body.scale.set(0.3, 0.36, 0.29); body.position.y = 0.37; g.add(body);
    const belly = new T.Mesh(SPH, lam("#f3efe4")); belly.scale.set(0.2, 0.22, 0.12); belly.position.set(0, 0.3, -0.2); g.add(belly);
    const fin = new T.Mesh(new T.ConeGeometry(0.1, 0.28, 8), lam(col)); fin.position.set(0, 0.76, 0.04); fin.rotation.x = 0.35; g.add(fin);
    const eyeW = new T.MeshBasicMaterial({ color: 0xffffff }), eyeB = new T.MeshBasicMaterial({ color: 0x111111 });
    [-0.1, 0.1].forEach((x) => {
      const e = new T.Mesh(SPH, eyeW); e.scale.setScalar(0.065); e.position.set(x, 0.48, -0.25); g.add(e);
      const p = new T.Mesh(SPH, eyeB); p.scale.setScalar(0.035); p.position.set(x, 0.48, -0.305); g.add(p);
    });
    if (it) { const ring = new T.Mesh(new T.RingGeometry(0.42, 0.55, 28), new T.MeshBasicMaterial({ color: 0xff5c8a, transparent: true, opacity: 0.7, side: T.DoubleSide })); ring.rotation.x = -Math.PI / 2; ring.position.y = 0.02; g.add(ring); }
    g.scale.setScalar(s || 1);
    return g;
  }


  // =====================================================================
  //  THE HARBOR LOUNGE  (36 m x 20 m, 7.5 m to the ceiling, loft at 3.2 m, deck and pier outside)
  // =====================================================================
  const LF = 3.2; // loft floor height
  const NS = { ns: 1, see: 1 }, NOST = { nost: 1 };
  const mesh = (geo, c, x, y, z, o) => { o = o || {}; const m = new T.Mesh(geo, o.basic ? new T.MeshBasicMaterial({ color: c, toneMapped: !o.glow, transparent: !!o.op, opacity: o.op || 1, depthWrite: !o.op }) : (o.two ? new T.MeshLambertMaterial({ color: c, side: T.DoubleSide }) : lam(c, o.e))); m.position.set(WX(x), z, WZ(y)); (o.g ? G[o.g] : G.main).add(m); return m; };
  const AREA_LIGHTS = [], KEY_LIGHTS = [];
  const plight = (c, i, d, x, y, z, key) => { const l = new T.PointLight(c, i, d, 2); l.position.set(WX(x), z, WZ(y)); scene.add(l); (key ? KEY_LIGHTS : AREA_LIGHTS).push(l); return l; };
  const board = (w, h, paint) => canvasTex(w, h, paint);
  function plate(tex, x, y, z, ry, w, h, o) {
    o = o || {};
    const mat = new T.MeshBasicMaterial({ map: tex, toneMapped: !!o.tone, transparent: !!o.alpha, depthWrite: !o.alpha, blending: o.add ? T.AdditiveBlending : T.NormalBlending });
    const m = new T.Mesh(new T.PlaneGeometry(w, h), mat); m.position.set(WX(x), z, WZ(y)); m.rotation.y = ry || 0;
    if (o.floor) m.rotation.set(-Math.PI / 2, 0, ry || 0);
    (o.g ? G[o.g] : G.main).add(m); return m;
  }
  const lines = (g, list, x, y0, dy, font, col) => { g.fillStyle = col; g.font = font; list.forEach((t, i) => g.fillText(t, x, y0 + i * dy)); };
  // facing helpers for wall plates: back wall faces +y (ry 0), left wall faces +x, right wall faces -x, front wall (inside) faces -y
  const FACE = { s: 0, e: Math.PI / 2, w: -Math.PI / 2, n: Math.PI };
  const poleSign = (text, x, y, z, w, ry, o) => { bx(x - 0.02, y - 0.02, x + 0.02, y + 0.02, 0.03, z - w / 8, "#30353d", NS); mesh(new T.CylinderGeometry(0.15, 0.15, 0.03, 18), "#30353d", x, y, 0.015); reg(x - 0.15, y - 0.15, x + 0.15, y + 0.15, 0, 0.03, "sign base"); return sign(text, x, y, z, ry || 0, w, o); };

  // ---------------- the shell ----------------
  // back wall (big screen), with the double door to the rest of the Lobby at x 30.6-32.4
  wallX(-0.15, 36.15, 0, 0, 1.1, C.oak2, [[30.6, 32.4]]);
  wallX(-0.15, 36.15, 0, 1.1, 7.5, C.navy, [[30.6, 32.4]]);
  bx(30.6, -0.15, 32.4, 0.15, 2.4, 7.5, C.navy);
  // left and right walls: wood panelling to 1.1 m, navy above
  wallY(0.15, 19.85, 0, 0, 1.1, C.oak2); wallY(0.15, 19.85, 0, 1.1, 7.5, C.navy);
  wallY(0.15, 19.85, 36, 0, 1.1, C.oak2); wallY(0.15, 19.85, 36, 1.1, 7.5, C.navy);
  // front wall: floor-to-ceiling harbor windows with French doors to the deck at x 22.6-25.0
  bx(-0.15, 19.85, 0.15, 20.15, 0, 7.5, C.navy); bx(35.85, 19.85, 36.15, 20.15, 0, 7.5, C.navy);
  bx(0.15, 19.85, 35.85, 20.15, 6.3, 7.5, C.navy);
  bx(0.15, 19.85, 22.44, 20.15, 0, 0.45, C.oak2); bx(25.16, 19.85, 35.85, 20.15, 0, 0.45, C.oak2);
  bx(22.44, 19.85, 22.6, 20.15, 0, 6.3, C.walnut); bx(25.0, 19.85, 25.16, 20.15, 0, 6.3, C.walnut);
  bx(22.6, 19.85, 25.0, 20.15, 2.6, 2.72, C.walnut); bx(22.6, 19.97, 25.0, 20.03, 2.72, 6.3, "#cfe4ff", { k: "glass" });
  const winPosts = [];
  for (let k = 1; k <= 8; k++) winPosts.push(0.15 + k * (22.29 / 9));
  for (let k = 1; k <= 3; k++) winPosts.push(25.16 + k * (10.69 / 4));
  winPosts.forEach((x) => bx(x - 0.08, 19.85, x + 0.08, 20.15, 0.45, 6.3, C.walnut));
  { const edges = [0.15, ...winPosts.slice(0, 8).flatMap((x) => [x - 0.08, x + 0.08]), 22.44, 25.16, ...winPosts.slice(8).flatMap((x) => [x - 0.08, x + 0.08]), 35.85];
    for (let i = 0; i < edges.length; i += 2) bx(edges[i], 19.97, edges[i + 1], 20.03, 0.45, 6.3, "#cfe4ff", { k: "glass" }); }
  door(22.6, 19.97, 23.8, 20.03, 0, 2.6, "#5b3d2a", "a", "s", 1);
  door(23.8, 19.97, 25.0, 20.03, 0, 2.6, "#5b3d2a", "b", "s", 1);
  // ceiling, timber trusses and purlins (hidden in bird's-eye)
  bx(-0.15, -0.15, 36.15, 20.15, 7.5, 7.7, "#3a2a1c", { g: "ceil", ns: 1 });
  const TRUSS = [4, 8, 12, 16, 20, 24, 28, 32];
  TRUSS.forEach((x) => bx(x - 0.12, 0.15, x + 0.12, 19.85, 6.95, 7.5, C.walnut, { g: "ceil", ns: 1 }));
  [5, 10, 15].forEach((y) => { const xs = [y === 15 ? 1.05 : 0.15, ...TRUSS.flatMap((x) => [x - 0.12, x + 0.12]), 35.85]; for (let i = 0; i < xs.length; i += 2) bx(xs[i], y - 0.08, xs[i + 1], y + 0.08, 7.25, 7.5, C.trim, { g: "ceil", ns: 1 }); });
  // pendant lamps between the trusses
  const PENDANTS = [[10, 17.2, 3.4], [14.2, 11.2, 3.6], [23.8, 11.2, 3.6], [30, 6.0, 3.4], [30, 15.4, 3.0], [10, 11.2, 3.4], [6, 17.2, 3.4], [34, 3.0, 3.6]];
  PENDANTS.forEach(([x, y, z]) => {
    bx(x - 0.01, y - 0.01, x + 0.01, y + 0.01, z + 0.32, 7.5, "#16181d", { g: "ceil", ns: 1 });
    const sh = mesh(new T.CylinderGeometry(0.09, 0.26, 0.32, 20, 1, true), "#1f2750", x, y, z + 0.16, { two: 1 }); G.main.remove(sh); G.ceil.add(sh);
    const bl = mesh(new T.SphereGeometry(0.07, 12, 10), 0xffe2b0, x, y, z + 0.06, { basic: 1, glow: 1 }); G.main.remove(bl); G.ceil.add(bl);
    reg(x - 0.26, y - 0.26, x + 0.26, y + 0.26, z, z + 0.32, "pendant lamp");
  });

  // ---------------- the loft ("the Crow's Nest") over the darts corner ----------------
  bx(0.15, 0.15, 8.15, 7.15, 2.95, LF, C.oak, { nm: "loft floor" });
  bx(0.15, 6.85, 8.15, 7.15, 2.65, 2.95, C.walnut, { nm: "loft beam" }); bx(7.85, 0.15, 8.15, 6.85, 2.65, 2.95, C.walnut, { nm: "loft beam" });
  [[7.85, 6.85], [3.85, 6.85], [7.85, 3.35]].forEach(([x, y]) => bx(x, y, x + 0.3, y + 0.3, 0, 2.65, C.walnut, { nm: "loft post" }));
  // loft railings: solid lower panel, glass, wooden cap; a gap for the slide
  const loftRail = (x1, y1, x2, y2) => { bx(x1, y1, x2, y2, LF, LF + 0.5, C.walnut, { ns: 1 }); bx(x1, y1, x2, y2, LF + 0.5, LF + 0.92, "#cfe4ff", { k: "glass", ns: 1 }); bx(x1, y1, x2, y2, LF + 0.92, LF + 1.02, C.oak, { ns: 1 }); sol(x1, y1, x2, y2, LF, LF + 1.1); };
  loftRail(1.35, 7.0, 5.5, 7.12); loftRail(6.5, 7.0, 8.1, 7.12); loftRail(7.98, 0.15, 8.1, 7.0);
  // slide gate
  bx(5.5, 7.0, 5.6, 7.12, LF, LF + 1.5, "#1FA8E6"); bx(6.4, 7.0, 6.5, 7.12, LF, LF + 1.5, "#1FA8E6"); bx(5.5, 7.0, 6.5, 7.12, LF + 1.5, LF + 1.62, "#1FA8E6", NS);
  sign("WHEEE", 6.0, 7.0, LF + 1.84, 0, 0.9, { bg: "#1FA8E6", fg: "#0B0D1A" });
  // stairs up the left wall (wood, closed underneath), handrail on the open side
  { const n = 14, y1 = 7.15, y2 = 12.35, d = (y2 - y1) / n;
    for (let i = 0; i < n; i++) { const h = LF * (i + 1) / n, a = y2 - d * (i + 1), b = y2 - d * i;
      bx(0.15, a, 1.35, b, h - 0.05, h, C.oak, { ns: 1 }); bx(0.15, a, 1.35, b, 0, h - 0.05, C.oak2, { ns: 1 }); sol(0.15, a, 1.35, b, 0, h, 1);
      bx(1.29, a, 1.35, a + 0.06, h, h + 0.92, C.walnut, NS); bx(1.29, a, 1.35, b, h + 0.92, h + 0.98, C.oak, NS); sol(1.35, a, 1.45, b, 0, h + 1.0); } }
  // the cupboard under the stairs
  bx(1.35, 9.5, 1.38, 10.3, 0, 1.3, C.walnut, NS); bx(1.38, 9.62, 1.41, 9.7, 0.6, 0.7, C.brass, NS);
  sign("SPARE PUCKS, DARTS & DIGNITY", 1.39, 9.9, 1.05, FACE.e, 0.7, { bg: "#f4f1e8", fg: "#3a2a1c", two: false });
  // the slide from the loft down to the fireplace side
  const SLIDES = window.WH_SLIDES = [];
  function ramp(x1, y1, x2, y2, z1, n, col, o) {
    o = o || {};
    for (let i = 0; i < n; i++) { const a = y1 + (y2 - y1) * i / n, b = y1 + (y2 - y1) * (i + 1) / n, h = Math.max(0.03, z1 * (1 - (i + 0.5) / n)); sol(x1, a, x2, b, 0, h, 1); if (o.walls) { sol(x1 - 0.1, a, x1, b, 0, h + 0.4); sol(x2, a, x2 + 0.1, b, 0, h + 0.4); } }
    const L = Math.hypot(y2 - y1, z1), th = Math.atan2(z1, y2 - y1);
    const m = mesh(new T.BoxGeometry(x2 - x1, 0.06, L), col, (x1 + x2) / 2, (y1 + y2) / 2, z1 / 2 + 0.03); m.rotation.x = th;
    if (o.lips) [x1 - 0.05, x2 + 0.05].forEach((sx) => { const q = mesh(new T.BoxGeometry(0.08, 0.32, L), col, sx, (y1 + y2) / 2, z1 / 2 + 0.17); q.rotation.x = th; });
    if (o.slide) SLIDES.push({ x1, y1, x2, y2 });
    reg(x1 - 0.1, y1, x2 + 0.1, y2, 0, z1 + 0.34, "slide", 1);
  }
  ramp(5.6, 7.15, 6.4, 12.55, LF, 18, "#1FA8E6", { slide: 1, lips: 1, walls: 1 });
  [[8.4, 2.58], [10.4, 1.4]].forEach(([y, h]) => { bx(5.47, y - 0.04, 5.55, y + 0.04, 0, h, "#aab1ba", NS); bx(6.45, y - 0.04, 6.53, y + 0.04, 0, h, "#aab1ba", NS); });
  bx(5.3, 12.55, 6.7, 13.7, 0, 0.012, "#1d6fb8", NS);
  sign("FASTEST WAY DOWN", 6.0, 7.13, LF + 0.3, 0, 0.8, { bg: "#ffd23f", fg: "#1f2329", sub: "Feet first please" });

  // loft furniture
  [[2.0, 2.0, "#e0b04a"], [3.7, 1.5, "#c25b4a"], [2.1, 4.5, "#3d7a6b"]].forEach(([x, y, c]) => { const m = mesh(new T.SphereGeometry(1, 20, 14), c, x, y, LF + 0.3); m.scale.set(0.5, 0.3, 0.5); reg(x - 0.5, y - 0.5, x + 0.5, y + 0.5, LF, LF + 0.6, "bean bag"); sol(x - 0.38, y - 0.38, x + 0.38, y + 0.38, LF, LF + 0.42, -1); });
  bx(4.6, 0.15, 7.6, 0.5, LF, LF + 0.9, C.oak2); // low bookcase
  for (let x = 4.68; x < 7.5; x += 0.07 + rnd() * 0.04) { const w = 0.04 + rnd() * 0.03; bx(x, 0.18, x + w, 0.46, LF + 0.9, LF + 0.9 + 0.18 + rnd() * 0.1, pick(["#c8202c", "#1d6fb8", "#e0b04a", "#3d9a5b", "#7b4fb5", "#f4f4f4"]), NS); x += w; }
  { const big = friend("#3FE0E8", 1.9); big.position.set(WX(5.4), LF, WZ(3.8)); big.rotation.y = -0.6; G.main.add(big); reg(4.85, 3.25, 5.95, 4.35, LF, LF + 1.7, "giant plush fin"); sol(4.95, 3.35, 5.85, 4.25, LF, LF + 1.3); }
  // telescope pointed at the big screen
  { const tx = 7.2, ty = 6.3; [[0, 0.22], [2.1, 0.22], [4.2, 0.22]].forEach(([a]) => { const l = mesh(new T.CylinderGeometry(0.015, 0.015, 1.25, 6), "#30353d", tx + Math.sin(a) * 0.22, ty + Math.cos(a) * 0.22, LF + 0.6); l.rotation.set(Math.cos(a) * 0.35, 0, -Math.sin(a) * 0.35); });
    const tube = mesh(new T.CylinderGeometry(0.06, 0.08, 0.9, 14), "#c9a45a", tx, ty - 0.1, LF + 1.3); tube.rotation.x = Math.PI / 2 - 0.25; tube.rotation.z = 0.0; reg(tx - 0.35, ty - 0.6, tx + 0.35, ty + 0.35, LF, LF + 1.45, "telescope"); }
  sign("FOR THE BACK ROW", 7.85, 6.2, LF + 0.32, FACE.w, 0.6, { bg: "#ffffff", fg: "#1f2750", two: false });
  sign("THE CROW'S NEST", 4.2, 0.16, LF + 2.6, 0, 2.6, { bg: "#1f2750", fg: "#ffd23f", sub: "No crows allowed", two: false });
  // fairy lights along the loft rail
  for (let x = 1.5; x < 8.0; x += 0.32) { if (x > 5.4 && x < 6.6) continue; mesh(new T.SphereGeometry(0.025, 8, 6), 0xffd9a0, x, 7.06, LF + 1.06, { basic: 1, glow: 1 }); }
  bx(1.35, 7.05, 5.5, 7.07, LF + 1.02, LF + 1.03, "#16181d", NS); bx(6.5, 7.05, 8.1, 7.07, LF + 1.02, LF + 1.03, "#16181d", NS);

  // ---------------- under the loft: darts and the games table ----------------
  // dartboard cabinet with both doors open (chalkboards inside the doors)
  bx(2.3, 0.15, 3.7, 0.22, 1.0, 2.45, C.walnut, { nm: "dart cabinet" });
  bx(2.3, 0.22, 2.34, 0.92, 1.0, 2.45, C.walnut, { nm: "cabinet door" }); bx(3.66, 0.22, 3.7, 0.92, 1.0, 2.45, C.walnut, { nm: "cabinet door" });
  const DART = { x: 3.0, y: 0.25, z: 1.73, r: 0.225 };
  reg(2.775, 0.22, 3.225, 0.27, 1.505, 1.955, "dartboard");
  bx(4.0, 0.15, 5.0, 0.19, 1.2, 2.1, "#2c3a34", { nm: "chalkboard" });
  bx(2.45, 2.6, 3.55, 2.66, 0, 0.008, C.brass, NS); // the oche (throw line)
  bx(2.3, 0.3, 3.7, 2.6, 0, 0.008, "#30353d", NS); // dart mat
  // board games shelf on the left wall
  bx(0.15, 1.2, 0.19, 5.6, 0, 2.2, C.oak2); bx(0.19, 1.2, 0.55, 1.25, 0, 2.2, C.oak2); bx(0.19, 5.55, 0.55, 5.6, 0, 2.2, C.oak2);
  [0, 0.55, 1.1, 1.65, 2.16].forEach((z) => bx(0.19, 1.25, 0.55, 5.55, z, z + 0.04, C.oak, { ns: 1 }));
  sol(0.15, 1.2, 0.55, 5.6, 0, 2.2);
  [0.04, 0.59, 1.14, 1.69].forEach((z) => { let y = 1.28; while (y < 5.4) { const w = 0.3 + rnd() * 0.18; if (y + w > 5.52) break; let zz = z; const n = 1 + Math.floor(rnd() * 4); for (let k = 0; k < n; k++) { const h = 0.05 + rnd() * 0.05; if (zz + h > z + 0.48) break; bx(0.22 + rnd() * 0.03, y, 0.52, y + w, zz, zz + h, pick(["#c8202c", "#1d6fb8", "#e0b04a", "#3d9a5b", "#7b4fb5", "#f4f4f4", "#e07b39", "#1d8a8a"]), NS); zz += h; } y += w + 0.02; } });
  sign("BOARD GAMES · PUT THEM BACK", 0.2, 3.4, 2.45, FACE.e, 1.6, { bg: "#1f2750", fg: "#ffd23f", two: false });
  // card table with four chairs and a game of checkers going
  bx(5.2, 2.6, 6.6, 4.0, 0.72, 0.76, C.walnut, { nm: "card table", nost: 1 });
  [[5.25, 2.65], [6.47, 2.65], [5.25, 3.87], [6.47, 3.87]].forEach(([x, y]) => bx(x, y, x + 0.08, y + 0.08, 0, 0.72, C.walnut, NS));
  sol(5.2, 2.6, 6.6, 4.0, 0, 0.76, -1);
  bx(5.6, 3.0, 6.2, 3.6, 0.76, 0.772, "#e8e8ea", NS);
  for (let i = 0; i < 6; i++) for (let j = 0; j < 6; j++) if ((i + j) % 2 === 0) bx(5.6 + i * 0.1, 3.0 + j * 0.1, 5.7 + i * 0.1, 3.1 + j * 0.1, 0.772, 0.774, "#16181d", NS);
  [[5.65, 3.05, "#c8202c"], [5.85, 3.05, "#c8202c"], [6.05, 3.15, "#c8202c"], [5.75, 3.45, "#f4f4f4"], [5.95, 3.55, "#f4f4f4"], [6.15, 3.45, "#f4f4f4"]].forEach(([x, y, c]) => { mesh(new T.CylinderGeometry(0.035, 0.035, 0.018, 16), c, x, y, 0.783); reg(x - 0.035, y - 0.035, x + 0.035, y + 0.035, 0.774, 0.792, "checker"); });
  const chair = (x, y, face, col) => { // face: the side the sitter looks toward
    bx(x - 0.21, y - 0.21, x + 0.21, y + 0.21, 0.42, 0.46, col, { ns: 1 });
    [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(([a, b]) => bx(x + a * 0.21 - (a > 0 ? 0.04 : 0), y + b * 0.21 - (b > 0 ? 0.04 : 0), x + a * 0.21 + (a < 0 ? 0.04 : 0), y + b * 0.21 + (b < 0 ? 0.04 : 0), 0, 0.42, col, NS));
    const bk = { n: [x - 0.21, y + 0.17, x + 0.21, y + 0.21], s: [x - 0.21, y - 0.21, x + 0.21, y - 0.17], e: [x - 0.21, y - 0.21, x - 0.17, y + 0.21], w: [x + 0.17, y - 0.21, x + 0.21, y + 0.21] }[face];
    bx(bk[0], bk[1], bk[2], bk[3], 0.46, 0.92, col, { ns: 1 }); sol(x - 0.21, y - 0.21, x + 0.21, y + 0.21, 0, 0.92, -1);
  };
  chair(4.85, 3.3, "e", "#6b4a2e"); chair(6.95, 3.3, "w", "#6b4a2e"); chair(5.9, 2.25, "s", "#6b4a2e"); chair(5.9, 4.35, "n", "#6b4a2e");
  // floor lamp and soft lights under the loft
  bx(7.35, 0.55, 7.41, 0.61, 0.03, 1.55, "#30353d", NS); mesh(new T.CylinderGeometry(0.16, 0.16, 0.03, 18), "#30353d", 7.38, 0.58, 0.015); reg(7.22, 0.42, 7.54, 0.74, 0, 0.03, "lamp base");
  mesh(new T.CylinderGeometry(0.14, 0.22, 0.3, 18, 1, true), "#d9c7a7", 7.38, 0.58, 1.7, { two: 1 }); reg(7.16, 0.36, 7.6, 0.8, 1.55, 1.85, "lamp shade");
  [[2.6, 3.4], [5.6, 4.6]].forEach(([x, y]) => bx(x, y, x + 0.8, y + 0.3, 2.94, 2.95, "#fff2d8", { k: "glow", ns: 1 }));
  plight(0xffd8a8, 0.9, 7, 4.2, 3.2, 2.5);

  // ---------------- the fireplace corner ----------------
  bx(0.15, 13.6, 1.05, 14.5, 0, 7.5, C.stone, { nm: "chimney" }); bx(0.15, 16.1, 1.05, 17.0, 0, 7.5, C.stone, { nm: "chimney" });
  bx(0.15, 14.5, 1.05, 16.1, 1.2, 7.5, C.stone, { nm: "chimney" });
  bx(0.15, 14.5, 0.45, 16.1, 0, 1.2, "#2a2422", { nm: "firebox back" });
  bx(0.45, 14.5, 1.05, 16.1, 0, 0.12, C.stone2, { nm: "hearth" }); bx(1.05, 13.4, 1.75, 17.2, 0, 0.12, C.stone2, { nm: "hearth" });
  bx(1.05, 13.5, 1.35, 17.1, 1.5, 1.62, C.walnut, { nm: "mantel", nost: 1 });
  bx(1.05, 14.5, 1.08, 16.1, 0.12, 1.0, "#16181d", { k: "cur" });
  for (let y = 14.75; y < 15.9; y += 0.35) bx(0.5, y, 1.0, y + 0.03, 0.12, 0.18, "#16181d", NS);
  const LOGS = [];
  [[15.05, 0.24, 0.08], [15.45, 0.24, -0.1], [15.25, 0.36, 0.0]].forEach(([y, z, r]) => { const m = mesh(new T.CylinderGeometry(0.06, 0.065, 0.5, 10), "#4a3424", 0.75, y, z); m.rotation.set(0, r, Math.PI / 2); LOGS.push(m); });
  reg(0.5, 14.95, 1.0, 15.55, 0.18, 0.42, "logs");
  const flameTex = [0, 1, 2].map((k) => canvasTex(128, 192, (g, w, h) => { const gr = g.createRadialGradient(w / 2, h * 0.85, 4, w / 2, h * 0.7, h * 0.65); gr.addColorStop(0, "rgba(255,245,200,1)"); gr.addColorStop(0.3, "rgba(255,170,60,.9)"); gr.addColorStop(0.7, "rgba(230,70,20,.45)"); gr.addColorStop(1, "rgba(120,20,0,0)"); g.fillStyle = gr; g.beginPath(); g.moveTo(w * 0.15, h); g.quadraticCurveTo(w * (0.1 + k * 0.1), h * 0.4, w * 0.5, h * 0.05); g.quadraticCurveTo(w * (0.9 - k * 0.1), h * 0.45, w * 0.85, h); g.fill(); }));
  const FLAMES = [[15.0, 0], [15.3, 1], [15.6, 2]].map(([y, k]) => { const p = plate(flameTex[k], 0.78, y, 0.48, FACE.e, 0.36, 0.56, { alpha: 1, add: 1 }); return p; });
  const fireL = plight(0xff8a3c, 2.4, 9, 1.6, 15.3, 0.9, true);
  bx(1.05, 14.6, 1.1, 16.0, 2.3, 2.9, C.walnut, { nm: "swordfish plaque" });
  { const g = new T.Group(); g.position.set(WX(1.2), 2.6, WZ(15.3)); G.main.add(g); const blue = lam("#2b5f8f"), sil = lam("#aab1ba");
    const body = new T.Mesh(SPH, blue); body.scale.set(0.07, 0.11, 0.42); g.add(body);
    const sw = new T.Mesh(new T.ConeGeometry(0.02, 0.55, 8), sil); sw.rotation.x = -Math.PI / 2; sw.position.z = -0.68; g.add(sw);
    const tail = new T.Mesh(new T.ConeGeometry(0.12, 0.18, 4), blue); tail.rotation.x = Math.PI / 2; tail.position.z = 0.48; tail.scale.set(0.3, 1, 1); g.add(tail);
    const fin = new T.Mesh(new T.ConeGeometry(0.06, 0.16, 4), blue); fin.position.set(0, 0.14, -0.05); fin.scale.set(0.3, 1, 1.4); g.add(fin);
    const eye = new T.Mesh(SPH, new T.MeshBasicMaterial({ color: 0x111111 })); eye.scale.setScalar(0.015); eye.position.set(0.065, 0.03, -0.3); g.add(eye);
    reg(1.1, 14.3, 1.28, 16.3, 2.42, 2.78, "Steve the swordfish"); }
  plate(board(512, 96, (g, w, h) => { g.fillStyle = "#c9a45a"; g.fillRect(0, 0, w, h); g.fillStyle = "#3a2a1c"; g.font = `800 34px ${FONT}`; g.textAlign = "center"; g.fillText("STEVE", w / 2, 40); g.font = `600 22px ${FONT}`; g.fillText("Caught by nobody. Bought at a yard sale.", w / 2, 76); }), 1.101, 15.3, 2.36, FACE.e, 0.6, 0.11);
  // the high score board for every Lounge game, over the fire
  bx(1.05, 13.9, 1.09, 16.7, 3.4, 4.9, "#16181d", { nm: "high score board" });
  const hsC = document.createElement("canvas"); hsC.width = 1024; hsC.height = 560; const hsTex = new T.CanvasTexture(hsC); hsTex.encoding = T.sRGBEncoding;
  plate(hsTex, 1.092, 15.3, 4.15, FACE.e, 2.7, 1.45);
  sign("DO NOT ROAST THE REMOTE", 1.06, 14.05, 1.0, FACE.e, 0.75, { bg: "#ffffff", fg: "#c8202c", two: false });
  // marshmallow jar on the mantel
  mesh(new T.CylinderGeometry(0.07, 0.07, 0.2, 16), "#e8f4ff", 1.2, 16.75, 1.72, { basic: 1, op: 0.45 }); mesh(new T.CylinderGeometry(0.072, 0.072, 0.03, 16), "#c8202c", 1.2, 16.75, 1.835);
  for (let k = 0; k < 7; k++) mesh(new T.CylinderGeometry(0.022, 0.022, 0.03, 10), "#fff8ec", 1.17 + (k % 3) * 0.03, 16.72 + (k % 2) * 0.05, 1.64 + Math.floor(k / 3) * 0.035);
  reg(1.13, 16.68, 1.27, 16.82, 1.62, 1.85, "marshmallow jar");
  sign("MARSHMALLOWS · TAKE ONE", 1.36, 16.75, 1.58, FACE.e, 0.5, { bg: "#ffffff", fg: "#7a3b5e", two: false });
  // armchairs, side table, rug, log basket, fire tools
  bx(1.8, 13.3, 4.4, 17.3, 0, 0.01, "#8f3b3b", NS);
  const armchair = (y) => {
    bx(2.45, y - 0.45, 3.35, y + 0.45, 0, 0.42, "#7a3b5e"); bx(3.35, y - 0.45, 3.6, y + 0.45, 0, 1.0, "#7a3b5e");
    bx(2.45, y - 0.45, 3.35, y - 0.3, 0.42, 0.62, "#7a3b5e", { ns: 1 }); bx(2.45, y + 0.3, 3.35, y + 0.45, 0.42, 0.62, "#7a3b5e", { ns: 1 });
    bx(2.45, y - 0.3, 3.35, y + 0.3, 0.42, 0.52, "#d9c7a7", { ns: 1 });
  };
  armchair(14.2); armchair(16.4);
  // a two-seat sofa facing the fire
  bx(4.9, 14.5, 5.65, 16.3, 0, 0.42, "#7a3b5e", { nm: "fire sofa" }); bx(5.65, 14.5, 5.9, 16.3, 0, 1.0, "#7a3b5e", { nm: "fire sofa back" });
  bx(4.9, 14.5, 5.65, 14.65, 0.42, 0.62, "#7a3b5e", { ns: 1 }); bx(4.9, 16.15, 5.65, 16.3, 0.42, 0.62, "#7a3b5e", { ns: 1 });
  bx(4.9, 14.65, 5.65, 15.39, 0.42, 0.52, "#d9c7a7", { ns: 1 }); bx(4.9, 15.41, 5.65, 16.15, 0.42, 0.52, "#d9c7a7", { ns: 1 });
  mesh(new T.CylinderGeometry(0.25, 0.25, 0.03, 22), C.walnut, 3.0, 15.3, 0.565); bx(2.97, 15.27, 3.03, 15.33, 0.04, 0.55, C.walnut, NS); mesh(new T.CylinderGeometry(0.18, 0.18, 0.04, 18), C.walnut, 3.0, 15.3, 0.02);
  reg(2.75, 15.05, 3.25, 15.55, 0.55, 0.58, "side table top"); reg(2.82, 15.12, 3.18, 15.48, 0, 0.04, "side table base"); sol(2.8, 15.1, 3.2, 15.5, 0, 0.58, -1);
  mesh(new T.CylinderGeometry(0.04, 0.035, 0.09, 14), "#1FA8E6", 2.92, 15.25, 0.625); reg(2.88, 15.21, 2.96, 15.29, 0.58, 0.67, "mug");
  bx(1.2, 17.35, 1.7, 17.85, 0, 0.4, "#9b6b43", { nm: "log basket" });
  [[17.45, 0.46], [17.6, 0.46], [17.75, 0.46], [17.52, 0.57]].forEach(([y, z]) => { const m = mesh(new T.CylinderGeometry(0.055, 0.055, 0.46, 10), "#6b4a2e", 1.45, y, z); m.rotation.z = Math.PI / 2; });
  reg(1.22, 17.39, 1.68, 17.81, 0.4, 0.63, "logs");
  bx(1.36, 12.96, 1.44, 13.04, 0.03, 0.9, "#16181d", NS); mesh(new T.CylinderGeometry(0.13, 0.13, 0.03, 16), "#16181d", 1.4, 13.0, 0.015); reg(1.27, 12.87, 1.53, 13.13, 0, 0.03, "tool stand"); bx(1.3, 12.97, 1.5, 13.03, 0.9, 0.93, "#16181d", NS);
  [[1.315, 0.5], [1.485, 0.55]].forEach(([x, z]) => bx(x - 0.008, 12.985, x + 0.008, 13.015, z, 0.9, "#30353d", NS));

  // ---------------- the big screen ----------------
  bx(13.6, 0.15, 24.4, 0.35, 0.6, 6.85, "#0b0c10", { nm: "big screen" });
  const SCR = document.createElement("canvas"); SCR.width = 1280; SCR.height = 720; const scrTex = new T.CanvasTexture(SCR); scrTex.encoding = T.sRGBEncoding;
  const screen = plate(scrTex, 19, 0.352, 3.725, 0, 10.4, 5.85);
  bx(13.6, 0.35, 24.4, 0.37, 0.6, 0.62, "#3FE0E8", { k: "glow", ns: 1 });
  const scrL = plight(0x7aa7ff, 1.6, 14, 19, 3.2, 3.2, true);
  bx(14.6, 0.15, 23.4, 0.7, 0, 0.55, C.walnut, { nm: "media console", nost: 1 });
  for (let x = 14.8; x < 23.2; x += 2.1) bx(x, 0.7, x + 1.9, 0.715, 0.08, 0.47, C.oak2, NS);
  bx(16.5, 0.375, 21.5, 0.5, 0.55, 0.66, "#16181d", { nm: "soundbar" });
  bx(14.9, 0.375, 15.4, 0.6, 0.55, 0.62, "#e8e8ea", { nm: "game console" });
  bx(22.4, 0.375, 22.9, 0.6, 0.55, 0.85, "#20232b", { nm: "Glowfin Tower mini" }); bx(22.42, 0.6, 22.88, 0.61, 0.7, 0.72, "#3FE0E8", { k: "glow", ns: 1 });
  mesh(new T.CylinderGeometry(0.11, 0.09, 0.22, 16), "#c25b4a", 23.15, 0.475, 0.66); reg(23.04, 0.375, 23.26, 0.585, 0.55, 0.77, "plant pot");
  for (let k = 0; k < 6; k++) { const l = mesh(new T.ConeGeometry(0.05, 0.4, 6), "#3d9a5b", 23.15 + Math.cos(k) * 0.05, 0.475 + Math.sin(k) * 0.05, 0.95); l.rotation.set(Math.sin(k) * 0.4, 0, Math.cos(k) * 0.4); }
  reg(23.0, 0.375, 23.3, 0.6, 0.77, 1.17, "plant");
  [[12.55, 13.25], [24.75, 25.45]].forEach(([a, b]) => { bx(a, 0.25, b, 0.85, 0, 1.55, "#16181d", { nm: "tower speaker" }); [0.4, 0.85, 1.3].forEach((z) => { mesh(new T.CircleGeometry(z > 1 ? 0.12 : 0.2, 24), "#30353d", (a + b) / 2, 0.852, z, { two: 1 }); }); });
  // movie poster frames on the back wall
  const posterTex = (title, sub, c1, c2) => board(512, 768, (g, w, h) => { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, c1); gr.addColorStop(1, c2); g.fillStyle = gr; g.fillRect(0, 0, w, h); g.fillStyle = "rgba(255,255,255,.08)"; for (let i = 0; i < 40; i++) { g.beginPath(); g.arc(Math.random() * w, Math.random() * h * 0.6, 1 + Math.random() * 2, 0, 7); g.fill(); } g.fillStyle = "#ECEEFB"; g.textAlign = "center"; g.font = `800 64px ${FONT}`; title.split("\n").forEach((t, i) => g.fillText(t, w / 2, h * 0.7 + i * 70)); g.font = `600 26px ${FONT}`; g.fillStyle = "rgba(236,238,251,.75)"; g.fillText(sub, w / 2, h * 0.92); });
  [[9.4, "THE LONG\nLOW TIDE", "Tonight in the Lounge", "#1f2750", "#3a1f4f"], [11.6, "GERALD\nPUFFS UP", "A pufferfish story", "#0e3a52", "#1f7a5c"]].forEach(([x, t, s, a, c]) => { bx(x - 0.85, 0.15, x + 0.85, 0.2, 1.6, 4.1, C.walnut, { nm: "poster frame" }); plate(posterTex(t, s, a, c), x, 0.201, 2.85, 0, 1.6, 2.4); });
  // the sofa: a big U facing the screen
  bx(14.0, 1.5, 24.0, 7.8, 0, 0.01, "#2b6f8f", NS);
  bx(15.4, 5.6, 22.6, 6.6, 0, 0.42, C.sofa, { nm: "sofa" }); bx(14.0, 6.6, 24.0, 7.0, 0, 0.95, C.sofa, { nm: "sofa back" });
  bx(14.4, 3.8, 15.4, 6.6, 0, 0.42, C.sofa, { nm: "sofa" }); bx(14.0, 3.8, 14.4, 6.6, 0, 0.95, C.sofa, { nm: "sofa back" }); bx(14.0, 3.55, 15.4, 3.8, 0, 0.68, C.sofa, { nm: "sofa arm" });
  bx(22.6, 3.8, 23.6, 6.6, 0, 0.42, C.sofa, { nm: "sofa" }); bx(23.6, 3.8, 24.0, 6.6, 0, 0.95, C.sofa, { nm: "sofa back" }); bx(22.6, 3.55, 24.0, 3.8, 0, 0.68, C.sofa, { nm: "sofa arm" });
  [[15.4, 17.8], [17.8, 20.2], [20.2, 22.6]].forEach(([a, b]) => { bx(a + 0.01, 5.6, b - 0.01, 6.38, 0.42, 0.56, C.sofa2, { nm: "cushion" }); bx(a + 0.01, 6.38, b - 0.01, 6.6, 0.42, 0.92, C.sofa2, { ns: 1, nm: "back cushion" }); });
  [[3.8, 5.2], [5.2, 6.6]].forEach(([a, b]) => { bx(14.58, a + 0.01, 15.4, b - 0.01, 0.42, 0.56, C.sofa2, { nm: "cushion" }); bx(14.4, a + 0.01, 14.58, b - 0.01, 0.42, 0.92, C.sofa2, { ns: 1, nm: "back cushion" }); bx(22.6, a + 0.01, 23.42, b - 0.01, 0.42, 0.56, C.sofa2, { nm: "cushion" }); bx(23.42, a + 0.01, 23.6, b - 0.01, 0.42, 0.92, C.sofa2, { ns: 1, nm: "back cushion" }); });
  [[15.6, "#e0b04a"], [21.9, "#c25b4a"]].forEach(([x, c]) => bx(x, 6.16, x + 0.5, 6.38, 0.56, 0.98, c, { ns: 1, nm: "pillow" }));
  bx(14.6, 3.86, 14.82, 4.36, 0.56, 0.96, "#3d7a6b", { ns: 1, nm: "pillow" });
  // coffee table with popcorn and an empty remote holder
  bx(17.6, 3.9, 20.4, 4.9, 0.38, 0.44, C.oak, { nm: "coffee table", nost: 1 });
  [[17.66, 3.96], [20.28, 3.96], [17.66, 4.78], [20.28, 4.78]].forEach(([x, y]) => bx(x, y, x + 0.06, y + 0.06, 0, 0.38, C.oak2, NS));
  bx(17.72, 3.96, 20.28, 4.84, 0.1, 0.13, C.oak2, NS); sol(17.6, 3.9, 20.4, 4.9, 0, 0.44, -1);
  mesh(new T.CylinderGeometry(0.2, 0.13, 0.15, 22), "#c8202c", 18.5, 4.4, 0.515); mesh(new T.SphereGeometry(0.19, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), "#ffe9a8", 18.5, 4.4, 0.585).scale.y = 0.5; reg(18.3, 4.2, 18.7, 4.6, 0.44, 0.68, "popcorn bowl");
  bx(19.3, 4.2, 19.7, 4.5, 0.44, 0.47, "#30353d", { nm: "remote holder" });
  sign("REMOTE: MISSING SINCE TUESDAY", 19.5, 4.49, 0.56, 0, 0.42, { bg: "#ffffff", fg: "#c8202c" }); bx(19.48, 4.46, 19.52, 4.48, 0.47, 0.51, "#30353d", NS);
  bx(18.9, 4.55, 19.2, 4.8, 0.44, 0.452, "#7ab8d8", NS); bx(19.9, 4.1, 20.2, 4.35, 0.44, 0.452, "#7ab8d8", NS);
  [[16.3, 2.4, "#e0b04a"], [21.7, 2.4, "#3d7a6b"]].forEach(([x, y, c]) => { const m = mesh(new T.SphereGeometry(1, 20, 14), c, x, y, 0.3); m.scale.set(0.52, 0.3, 0.52); reg(x - 0.52, y - 0.52, x + 0.52, y + 0.52, 0, 0.6, "bean bag"); sol(x - 0.4, y - 0.4, x + 0.4, y + 0.4, 0, 0.42, 1); });
  // the raised back row with a drinks ledge and stools
  bx(13.6, 8.2, 24.4, 10.4, 0, 0.4, C.oak, { nm: "raised back row" });
  bx(13.6, 8.18, 24.4, 8.2, 0.05, 0.08, "#3FE0E8", { k: "glow", ns: 1 });
  bx(14.4, 10.4, 23.6, 10.8, 0, 0.2, C.oak, { nm: "step" });
  bx(14.0, 8.4, 24.0, 8.55, 0.4, 1.4, C.walnut, { nm: "ledge panel" }); bx(14.0, 8.25, 24.0, 8.75, 1.4, 1.45, C.oak, { nm: "drinks ledge", nost: 1 });
  const stool = (x, y, z0, h, col) => { mesh(new T.CylinderGeometry(0.19, 0.19, 0.06, 20), col || "#2a1f55", x, y, z0 + h - 0.03); bx(x - 0.03, y - 0.03, x + 0.03, y + 0.03, z0 + 0.03, z0 + h - 0.06, "#aab1ba", NS); mesh(new T.CylinderGeometry(0.2, 0.2, 0.03, 20), "#aab1ba", x, y, z0 + 0.015); mesh(new T.TorusGeometry(0.15, 0.012, 6, 20), "#aab1ba", x, y, z0 + h * 0.45).rotation.x = Math.PI / 2; reg(x - 0.19, y - 0.19, x + 0.19, y + 0.19, z0 + h - 0.06, z0 + h, "stool seat"); reg(x - 0.2, y - 0.2, x + 0.2, y + 0.2, z0, z0 + 0.03, "stool base"); sol(x - 0.18, y - 0.18, x + 0.18, y + 0.18, z0, z0 + h, -1); };
  [15.0, 16.6, 18.2, 19.8, 21.4, 23.0].forEach((x) => stool(x, 9.25, 0.4, 0.72));
  mesh(new T.CylinderGeometry(0.09, 0.09, 0.22, 16), "#e8f4ff", 23.6, 8.5, 1.56, { basic: 1, op: 0.45 }); reg(23.51, 8.41, 23.69, 8.59, 1.45, 1.67, "puck jar");
  for (let k = 0; k < 5; k++) mesh(new T.CylinderGeometry(0.04, 0.04, 0.012, 14), "#d94f4f", 23.6, 8.5, 1.46 + k * 0.014);
  sign("LOST PUCKS FOUND UNDER THE SOFA: 47", 23.6, 8.3, 1.78, 0, 0.6, { bg: "#ffffff", fg: "#1f2750" }); bx(23.58, 8.28, 23.62, 8.32, 1.45, 1.71, "#30353d", NS);

  // ---------------- the pool table ----------------
  const POOL = { x1: 17.73, y1: 13.55, x2: 20.27, y2: 14.85, z: 0.78 };
  [[17.75, 13.55], [20.11, 13.55], [17.75, 14.71], [20.11, 14.71]].forEach(([x, y]) => bx(x, y, x + 0.14, y + 0.14, 0, 0.6, C.walnut, { nm: "pool leg" }));
  bx(17.58, 13.4, 20.42, 15.0, 0.6, 0.76, C.walnut, { nm: "pool table", nost: 1 });
  bx(17.73, 13.55, 20.27, 14.85, 0.76, 0.78, C.felt, { nm: "felt", nost: 1 });
  [[17.73, 18.93], [19.07, 20.27]].forEach(([a, b]) => { bx(a, 13.4, b, 13.55, 0.76, 0.84, "#5b3d2a", { nm: "rail", nost: 1 }); bx(a, 14.85, b, 15.0, 0.76, 0.84, "#5b3d2a", { nm: "rail", nost: 1 }); });
  bx(17.58, 13.55, 17.73, 14.85, 0.76, 0.84, "#5b3d2a", { nm: "rail", nost: 1 }); bx(20.27, 13.55, 20.42, 14.85, 0.76, 0.84, "#5b3d2a", { nm: "rail", nost: 1 });
  [[17.58, 13.4], [20.27, 13.4], [17.58, 14.85], [20.27, 14.85]].forEach(([x, y]) => bx(x, y, x + 0.15, y + 0.15, 0.76, 0.82, "#16181d", { nm: "pocket", nost: 1 }));
  [13.4, 14.85].forEach((y) => bx(18.93, y, 19.07, y + 0.15, 0.76, 0.8, "#16181d", { nm: "pocket", nost: 1 }));
  for (let k = 1; k < 8; k++) { if (k === 4) continue; const x = 17.73 + k * 0.3175; [13.47, 14.92].forEach((y) => bx(x - 0.01, y - 0.01, x + 0.01, y + 0.01, 0.84, 0.842, "#f4f1e8", NS)); }
  [[18.0, 13.42], [20.0, 14.93]].forEach(([x, y]) => bx(x, y, x + 0.025, y + 0.025, 0.84, 0.865, "#2f6db5", { nm: "chalk" }));
  // the lamp over the table
  bx(18.0, 14.05, 20.0, 14.35, 1.75, 1.95, "#1f7a5c", { ns: 1, nm: "pool lamp" }); bx(18.08, 14.1, 19.92, 14.3, 1.74, 1.75, "#fff2d8", { k: "glow", ns: 1 });
  [18.45, 19.55].forEach((x) => bx(x - 0.01, 14.19, x + 0.01, 14.21, 1.95, 7.5, C.brass, { ns: 1 }));
  const poolL = plight(0xffe7c2, 2.2, 6.5, 19, 14.2, 1.55, true);
  // cue rack at the end of the back row
  bx(24.6, 9.0, 25.1, 9.6, 0, 0.08, C.walnut, { nm: "cue rack" }); bx(24.85, 9.0, 24.95, 9.6, 0.08, 1.5, C.walnut, { nm: "cue rack" });
  for (let y = 9.08; y < 9.55; y += 0.12) bx(24.95, y, 24.975, y + 0.025, 0.08, 1.45, "#d2b48c", { nm: "cue" });
  bx(24.6, 8.4, 25.1, 8.7, 0, 0.012, "#16181d", NS);
  sign("HOUSE RULE: THE 8 BALL IS SHY", 24.98, 9.3, 1.75, FACE.e, 0.7, { bg: "#ffffff", fg: "#1f7a5c" }); bx(24.85, 9.28, 24.95, 9.32, 1.5, 1.63, C.walnut, NS);

  // ---------------- air hockey ----------------
  const AH = { x1: 26.08, y1: 10.68, x2: 27.12, y2: 12.82, z: 0.789, gx1: 26.38, gx2: 26.82 };
  [[26.05, 10.65], [27.03, 10.65], [26.05, 12.73], [27.03, 12.73]].forEach(([x, y]) => bx(x, y, x + 0.12, y + 0.12, 0, 0.55, "#30353d", { nm: "air hockey leg" }));
  bx(26.0, 10.6, 27.2, 12.9, 0.55, 0.78, "#1d6fb8", { nm: "air hockey table", nost: 1 });
  bx(26.08, 10.68, 27.12, 12.82, 0.78, 0.785, "#e8e8ea", { nm: "air hockey surface", nost: 1 });
  bx(26.0, 10.6, 26.08, 12.9, 0.78, 0.86, "#e8e8ea", { nm: "rail", nost: 1 }); bx(27.12, 10.6, 27.2, 12.9, 0.78, 0.86, "#e8e8ea", { nm: "rail", nost: 1 });
  [[10.6, 10.68], [12.82, 12.9]].forEach(([a, b]) => { bx(26.08, a, 26.38, b, 0.78, 0.86, "#e8e8ea", { nm: "rail", nost: 1 }); bx(26.82, a, 27.12, b, 0.78, 0.86, "#e8e8ea", { nm: "rail", nost: 1 }); });
  plate(board(256, 512, (g, w, h) => { g.fillStyle = "#cfd9e8"; g.fillRect(0, 0, w, h); g.fillStyle = "rgba(40,60,90,.3)"; for (let x = 8; x < w; x += 16) for (let y = 8; y < h; y += 16) { g.beginPath(); g.arc(x, y, 1.5, 0, 7); g.fill(); } g.strokeStyle = "#d94f4f"; g.lineWidth = 6; g.beginPath(); g.moveTo(0, h / 2); g.lineTo(w, h / 2); g.stroke(); g.beginPath(); g.arc(w / 2, h / 2, 50, 0, 7); g.stroke(); g.strokeStyle = "#1d6fb8"; [0, h].forEach((y) => { g.beginPath(); g.arc(w / 2, y, 70, 0, 7); g.stroke(); }); }), 26.6, 11.75, 0.7885, 0, 1.04, 2.14, { floor: 1, tone: 1 });
  bx(26.1, 11.6, 27.1, 11.9, 2.3, 2.7, "#16181d", { ns: 1, nm: "air hockey scoreboard" });
  [26.3, 26.9].forEach((x) => bx(x - 0.01, 11.74, x + 0.01, 11.76, 2.7, 7.5, "#16181d", { ns: 1 }));
  const ahC = document.createElement("canvas"); ahC.width = 512; ahC.height = 192; const ahTex = new T.CanvasTexture(ahC); ahTex.encoding = T.sRGBEncoding;
  plate(ahTex, 26.6, 11.901, 2.5, 0, 0.96, 0.36); plate(ahTex, 26.6, 11.599, 2.5, Math.PI, 0.96, 0.36);

  // ---------------- giant four-in-a-row (took the place of foosball: it plays well with a mouse) ----------------
  const C4 = { x1: 25.9, z1: 0.45, d: 0.2, y: 15.062 };
  bx(25.8, 14.94, 27.4, 15.06, 0.35, 1.75, "#1d4fb8", { nm: "four-in-a-row board", nost: 1 });
  [[25.68, 25.8], [27.4, 27.52]].forEach(([a, b]) => { bx(a, 14.6, b, 15.4, 0, 0.08, "#16306f", { nm: "four-in-a-row foot", nost: 1 }); bx(a, 14.94, b, 15.06, 0.08, 1.85, "#16306f", { nm: "four-in-a-row post", nost: 1 }); });
  bx(25.8, 14.94, 27.4, 15.06, 1.75, 1.85, "#16306f", { ns: 1, nm: "four-in-a-row top" });
  const c4C = document.createElement("canvas"); c4C.width = 800; c4C.height = 700; const c4Tex = new T.CanvasTexture(c4C); c4Tex.encoding = T.sRGBEncoding; c4Tex.anisotropy = 4;
  plate(c4Tex, 26.6, 15.062, 1.05, 0, 1.6, 1.4); plate(c4Tex, 26.6, 14.938, 1.05, Math.PI, 1.6, 1.4);
  bx(25.9, 15.5, 27.3, 15.9, 0, 0.008, "#16306f", NS);
  sign("FOUR IN A ROW", 26.6, 15.0, 2.02, 0, 1.2, { bg: "#ffd23f", fg: "#16306f" }); bx(26.58, 14.98, 26.62, 15.02, 1.85, 1.87, "#16306f", NS);

  // ---------------- darts board and the jukebox ----------------
  bx(26.6, 0.15, 27.8, 0.85, 0, 1.55, "#2a1f55", { nm: "jukebox" });
  { const top = mesh(new T.CylinderGeometry(0.6, 0.6, 0.7, 24, 1, false, -Math.PI / 2, Math.PI), "#2a1f55", 27.2, 0.5, 1.55); top.rotation.x = Math.PI / 2; top.position.y = 1.55; top.scale.set(1, 1, 1); reg(26.6, 0.15, 27.8, 0.85, 1.55, 2.15, "jukebox top"); }
  bx(26.6, 0.85, 26.68, 0.87, 0.2, 1.5, "#A55BE0", { k: "glow", ns: 1 }), bx(27.72, 0.85, 27.8, 0.87, 0.2, 1.5, "#3FE0E8", { k: "glow", ns: 1 });
  { const arch = new T.Mesh(new T.TorusGeometry(0.56, 0.035, 8, 28, Math.PI), new T.MeshBasicMaterial({ color: 0x3fe0e8, toneMapped: false })); arch.position.set(WX(27.2), 1.55, WZ(0.87)); G.main.add(arch); const a2 = new T.Mesh(new T.TorusGeometry(0.47, 0.03, 8, 28, Math.PI), new T.MeshBasicMaterial({ color: 0xa55be0, toneMapped: false })); a2.position.set(WX(27.2), 1.55, WZ(0.87)); G.main.add(a2); }
  const jukeC = document.createElement("canvas"); jukeC.width = 384; jukeC.height = 512; const jukeTex = new T.CanvasTexture(jukeC); jukeTex.encoding = T.sRGBEncoding;
  plate(jukeTex, 27.2, 0.872, 1.0, 0, 1.0, 1.32);
  const jukeL = plight(0xa55be0, 0.0, 6, 27.2, 1.6, 1.6);

  // ---------------- doors to the rest of the Lobby ----------------
  bx(30.6, -0.04, 31.5, 0.04, 0, 2.4, C.walnut, { nm: "lobby door" }); bx(31.5, -0.04, 32.4, 0.04, 0, 2.4, C.walnut, { nm: "lobby door" });
  bx(30.5, 0.15, 30.6, 0.2, 0, 2.5, C.oak, { nm: "door frame" }); bx(32.4, 0.15, 32.5, 0.2, 0, 2.5, C.oak, { nm: "door frame" }); bx(30.5, 0.15, 32.5, 0.2, 2.5, 2.6, C.oak, { nm: "door frame" });
  [31.05, 31.95].forEach((x) => { mesh(new T.CircleGeometry(0.17, 24), "#7aa7ff", x, 0.045, 1.6, { basic: 1 }); mesh(new T.TorusGeometry(0.18, 0.025, 8, 24), C.brass, x, 0.05, 1.6); });
  [31.42, 31.58].forEach((x) => bx(x - 0.02, 0.04, x + 0.02, 0.08, 1.0, 1.25, C.brass, { nm: "handle" }));
  sign("SCREENING ROOM · ROOFTOP DECK", 31.5, 0.2, 2.95, 0, 2.2, { bg: "#1f2750", fg: "#ECEEFB", sub: "Next rooms in the Lobby", two: false });
  bx(30.8, 0.2, 32.2, 1.4, 0, 0.012, "#3a2a1c", NS);
  plate(board(512, 256, (g, w, h) => { g.fillStyle = "#3a2a1c"; g.fillRect(0, 0, w, h); g.fillStyle = "#c9a45a"; g.font = `800 64px ${FONT}`; g.textAlign = "center"; g.fillText("AHOY", w / 2, 110); g.font = `600 34px ${FONT}`; g.fillText("Wipe your fins", w / 2, 175); }), 31.5, 0.8, 0.013, 0, 1.3, 1.1, { floor: 1, tone: 1 });
  // neon sign over the doors
  const neon = (text, col, w, h, sub) => board(1024, 256, (g, W, H) => { g.clearRect(0, 0, W, H); g.textAlign = "center"; g.textBaseline = "middle"; let fs = sub ? 120 : 140; g.font = `800 ${fs}px ${FONT}`; while (g.measureText(text).width > W - 110 && fs > 40) { fs -= 4; g.font = `800 ${fs}px ${FONT}`; } g.shadowColor = col; g.shadowBlur = 40; g.fillStyle = col; g.fillText(text, W / 2, sub ? H * 0.42 : H / 2); g.shadowBlur = 10; g.fillStyle = "#ffffff"; g.globalAlpha = 0.55; g.fillText(text, W / 2, sub ? H * 0.42 : H / 2); g.globalAlpha = 1; if (sub) { g.font = `600 54px ${FONT}`; g.shadowBlur = 20; g.fillStyle = col; g.fillText(sub, W / 2, H * 0.84); } });
  plate(neon("HARBOR LOUNGE", "#3FE0E8", 0, 0, "est. tonight"), 30.8, 0.155, 4.6, 0, 6.2, 1.55, { alpha: 1 });
  plate(neon("SNACKS & SODAS", "#ff8ad8"), 35.848, 16.0, 3.0, FACE.w, 3.6, 0.9, { alpha: 1 });

  // ---------------- the aquarium wall ----------------
  const AQ = { x1: 35.02, x2: 35.83, y1: 1.02, y2: 11.98, z1: 0.72, z2: 3.38 };
  bx(35.0, 1.0, 35.85, 12.0, 0, 0.6, C.walnut, { nm: "aquarium stand" });
  bx(35.02, 1.02, 35.83, 11.98, 0.6, 0.72, "#e3cf9e", { nm: "sand" });
  bx(35.0, 1.0, 35.02, 12.0, 0.6, 3.4, "#cfefff", { k: "glass", nm: "aquarium glass" });
  bx(35.02, 1.0, 35.85, 1.02, 0.6, 3.4, "#cfefff", { k: "glass", nm: "aquarium glass" }); bx(35.02, 11.98, 35.85, 12.0, 0.6, 3.4, "#cfefff", { k: "glass", nm: "aquarium glass" });
  bx(35.0, 1.0, 35.85, 12.0, 3.4, 3.7, C.walnut, { nm: "aquarium hood" });
  bx(35.1, 1.1, 35.75, 11.9, 3.39, 3.4, "#bfefff", { k: "glow", ns: 1 });
  plate(board(1024, 256, (g, w, h) => { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, "#1b7fb0"); gr.addColorStop(1, "#06263f"); g.fillStyle = gr; g.fillRect(0, 0, w, h); g.fillStyle = "rgba(160,230,255,.12)"; for (let i = 0; i < 14; i++) { g.beginPath(); g.moveTo(i * 80, 0); g.lineTo(i * 80 + 40, 0); g.lineTo(i * 80 - 60, h); g.lineTo(i * 80 - 90, h); g.fill(); } g.fillStyle = "#0d3b52"; for (let i = 0; i < 30; i++) { g.beginPath(); g.ellipse(Math.random() * w, h, 20 + Math.random() * 50, 30 + Math.random() * 60, 0, Math.PI, 0); g.fill(); } }), 35.848, 6.5, 2.05, FACE.w, 10.96, 2.66, { tone: 1 });
  { const water = new T.Mesh(new T.PlaneGeometry(10.96, 2.66), new T.MeshBasicMaterial({ color: 0x2aa8d8, transparent: true, opacity: 0.12, depthWrite: false })); water.position.set(WX(35.03), 2.05, WZ(6.5)); water.rotation.y = FACE.w; water.renderOrder = 2; G.main.add(water); }
  // rocks, coral, plants, a treasure chest, a tiny shipwreck and the missing 8 ball
  [[2.0, 2.7, 0.3], [4.4, 5.0, 0.22], [7.6, 8.1, 0.35], [10.9, 11.6, 0.26]].forEach(([a, b, h]) => bx(35.25, a, 35.78, b, 0.72, 0.72 + h, C.stone2, { nm: "rock" }));
  const CORAL = ["#ff7a8a", "#ffb24a", "#c77dff", "#ff5c8a", "#ffd23f"];
  [1.6, 3.4, 6.0, 9.4, 10.4].forEach((y, i) => { for (let k = 0; k < 3; k++) { const h = 0.25 + rnd() * 0.35, c = mesh(new T.CylinderGeometry(0.015, 0.035, h, 6), CORAL[(i + k) % 5], 35.5 + (k - 1) * 0.08, y + (k - 1) * 0.06, 0.72 + h / 2); c.rotation.set((k - 1) * 0.3, 0, (k - 1) * 0.25); } reg(35.38, y - 0.12, 35.62, y + 0.12, 0.72, 1.32, "coral"); });
  const PLANTS = [];
  for (let k = 0; k < 18; k++) { const y = 1.3 + k * 0.6 + rnd() * 0.2, h = 0.5 + rnd() * 0.9; const p = mesh(new T.PlaneGeometry(0.12, h), "#2f9a5a", 35.7 - rnd() * 0.2, y, 0.72 + h / 2, { two: 1 }); p.rotation.y = FACE.w + (rnd() - 0.5); PLANTS.push({ m: p, ph: rnd() * 6, h }); }
  reg(35.45, 1.2, 35.75, 11.9, 0.72, 2.3, "water plants", 1);
  bx(35.4, 6.2, 35.75, 6.8, 0.72, 0.92, "#8a6a4a", { nm: "treasure chest" }); bx(35.385, 6.2, 35.4, 6.8, 0.75, 0.9, C.brass, NS);
  const chestLid = new T.Group(); chestLid.position.set(WX(35.75), 0.92, WZ(6.5)); G.main.add(chestLid);
  { const l = new T.Mesh(new T.CylinderGeometry(0.175, 0.175, 0.6, 14, 1, false, 0, Math.PI), lam("#8a6a4a")); l.rotation.set(0, 0, Math.PI / 2); l.rotation.order = "ZYX"; l.position.set(-0.175, 0, 0); const lg = new T.Group(); lg.add(l); l.rotation.set(Math.PI / 2, 0, Math.PI / 2); chestLid.add(lg); reg(35.4, 6.2, 35.75, 6.8, 0.92, 1.1, "chest lid", 1); }
  const coin = mesh(new T.SphereGeometry(0.12, 10, 8), 0xffd23f, 35.57, 6.5, 0.9, { basic: 1, glow: 1 }); coin.scale.y = 0.25; coin.visible = false;
  mesh(new T.SphereGeometry(0.028, 16, 12), "#16181d", 35.35, 9.0, 0.748); reg(35.322, 8.972, 35.378, 9.028, 0.72, 0.776, "the missing 8 ball");
  { const w = mesh(new T.BoxGeometry(0.3, 0.16, 0.7), "#6b4a2e", 35.55, 10.9, 0.8); w.rotation.set(0.25, 0, 0.35); const m2 = mesh(new T.CylinderGeometry(0.012, 0.012, 0.45, 6), "#6b4a2e", 35.55, 10.8, 1.05); m2.rotation.z = 0.4; reg(35.38, 10.5, 35.72, 11.3, 0.72, 1.28, "tiny shipwreck", 1); }
  sign("NO DIVING", 35.4, 4.0, 0.86, FACE.w, 0.3, { bg: "#ffffff", fg: "#c8202c" }); bx(35.4, 3.99, 35.41, 4.01, 0.72, 0.82, "#30353d", NS);
  sign("PLEASE DON'T TAP THE GLASS", 34.99, 6.5, 3.55, FACE.w, 2.2, { bg: "#3a2a1c", fg: "#c9a45a", sub: "They're trying to watch the movie", two: false });
  const BUB = [];
  { const bg = new T.SphereGeometry(0.012, 6, 4), bm = new T.MeshBasicMaterial({ color: 0xd9f6ff, transparent: true, opacity: 0.7 }); for (let k = 0; k < 60; k++) { const m = new T.Mesh(bg, bm); const y = [2.4, 6.5, 8.8][k % 3] + (rnd() - 0.5) * 0.12; m.position.set(WX(35.45 + rnd() * 0.25), 0.72 + rnd() * 2.6, WZ(y)); G.main.add(m); BUB.push({ m, y, sp: 0.25 + rnd() * 0.35 }); } }
  const aqL = plight(0x3fd0ff, 1.8, 10, 33.6, 6.5, 2.2);
  // the fish: they swim back and forth, and come up to the glass to look at you
  const FISH = [];
  { const fcol = ["#ffb24a", "#ff5c8a", "#3FE0E8", "#ffd23f", "#A55BE0", "#ECEEFB", "#5bd18b", "#ff7a3c", "#7aa7ff", "#ffb24a", "#ff5c8a", "#ffd23f", "#3FE0E8"];
    fcol.forEach((c, i) => { const g = new T.Group(); const s = 0.6 + rnd() * 0.7; const b = new T.Mesh(SPH, lam(c)); b.scale.set(0.035 * s, 0.05 * s, 0.09 * s); g.add(b); const t = new T.Mesh(new T.ConeGeometry(0.04 * s, 0.07 * s, 4), lam(c)); t.rotation.x = -Math.PI / 2; t.position.z = 0.11 * s; t.scale.x = 0.3; g.add(t); const e = new T.Mesh(SPH, new T.MeshBasicMaterial({ color: 0x111111 })); e.scale.setScalar(0.011 * s); e.position.set(0.03 * s, 0.015 * s, -0.05 * s); g.add(e); const e2 = e.clone(); e2.position.x = -0.03 * s; g.add(e2); G.main.add(g);
      FISH.push({ g, tail: t, x: 35.3 + rnd() * 0.4, y: 1.5 + rnd() * 10, z: 1.0 + rnd() * 2.0, vy: (rnd() < 0.5 ? -1 : 1) * (0.2 + rnd() * 0.3), ph: rnd() * 6, s }); }); }
  // two benches facing the tank
  [[2.6, 5.0], [7.8, 10.2]].forEach(([a, b]) => { bx(32.2, a, 32.7, b, 0.36, 0.45, "#2b6f8f", { nm: "tank bench" }); [[a, a + 0.1], [b - 0.1, b]].forEach(([c, d]) => bx(32.25, c, 32.65, d, 0, 0.36, C.walnut, { nm: "tank bench leg", ns: 1 })); sol(32.2, a, 32.7, b, 0, 0.36); });
  // Gerald the pufferfish (he puffs up if you jump next to the glass)
  const GERALD = { g: new T.Group(), puff: 0, x: 35.45, y: 6.4, z: 1.2 };
  { const g = GERALD.g; const b = new T.Mesh(SPH, lam("#e8c86a")); b.scale.setScalar(0.07); g.add(b); GERALD.body = b; for (let k = 0; k < 18; k++) { const sp = new T.Mesh(new T.ConeGeometry(0.008, 0.03, 4), lam("#b89a4a")); const a = k * 2.4, e = Math.acos(1 - 2 * ((k + 0.5) / 18)); sp.position.set(Math.sin(e) * Math.cos(a) * 0.07, Math.cos(e) * 0.07, Math.sin(e) * Math.sin(a) * 0.07); sp.lookAt(sp.position.clone().multiplyScalar(2)); sp.rotateX(Math.PI / 2); b.add(sp); }
    [0.03, -0.03].forEach((zz) => { const e = new T.Mesh(SPH, new T.MeshBasicMaterial({ color: 0x111111 })); e.scale.setScalar(0.013); e.position.set(-0.06, 0.02, zz); g.add(e); }); g.position.set(WX(GERALD.x), GERALD.z, WZ(GERALD.y)); G.main.add(g); }
  plate(board(512, 128, (g, w, h) => { g.fillStyle = "#3a2a1c"; g.fillRect(0, 0, w, h); g.fillStyle = "#c9a45a"; g.font = `800 40px ${FONT}`; g.textAlign = "center"; g.fillText("GERALD", w / 2, 52); g.font = `600 26px ${FONT}`; g.fillText("Head of security. Easily startled.", w / 2, 96); }), 34.995, 6.5, 0.4, FACE.w, 0.8, 0.2);

  // ---------------- the snack bar ----------------
  bx(32.0, 12.8, 32.7, 18.6, 0, 1.05, "#5b3d2a", { nm: "bar", nost: 1 }); bx(31.82, 12.75, 32.75, 18.65, 1.05, 1.1, "#3a2a1c", { nm: "bar top", nost: 1 });
  bx(31.98, 12.8, 32.0, 18.6, 0.12, 0.16, C.brass, NS); bx(31.98, 12.8, 32.0, 18.6, 0.92, 0.95, "#3FE0E8", { k: "glow", ns: 1 });
  bx(35.25, 12.8, 35.85, 19.6, 0, 0.9, C.walnut, { nm: "back bar" }); bx(35.2, 12.8, 35.85, 19.6, 0.9, 0.95, "#3a2a1c", { nm: "back bar top", nost: 1 });
  bx(35.23, 13.05, 35.25, 14.95, 0.08, 0.82, "#d9f0ff", { k: "glow", ns: 1 });
  for (let y = 13.15; y < 14.9; y += 0.12) [0.15, 0.42].forEach((z) => bx(35.238, y, 35.249, y + 0.07, z, z + 0.2, pick(["#c8202c", "#1d6fb8", "#ffd23f", "#3d9a5b", "#ff8ad8"]), NS));
  [1.6, 2.2].forEach((z) => (z < 2 ? [[12.9, 15.7], [16.5, 19.4]] : [[12.9, 19.4]]).forEach(([a, b]) => bx(35.6, a, 35.85, b, z, z + 0.04, C.oak, { nm: "shelf" })));
  [1.64, 2.24].forEach((z) => { for (let y = 13.05; y < 19.3; y += 0.22) { if (z < 2 && y > 15.6 && y < 16.6) continue; const kind = rnd(); if (kind < 0.55) { const h = 0.16 + rnd() * 0.12; mesh(new T.CylinderGeometry(0.035, 0.035, h, 10), pick(["#c8202c", "#1d6fb8", "#3d9a5b", "#ffd23f", "#ff8ad8"]), 35.72, y, z + h / 2); reg(35.685, y - 0.035, 35.755, y + 0.035, z, z + h, "soda bottle"); } else if (kind < 0.85) { mesh(new T.CylinderGeometry(0.06, 0.06, 0.18, 14), "#e8f4ff", 35.72, y, z + 0.09, { basic: 1, op: 0.5 }); mesh(new T.SphereGeometry(0.05, 10, 8), pick(["#ff5c8a", "#ffd23f", "#5bd18b", "#a55be0"]), 35.72, y, z + 0.06); reg(35.66, y - 0.06, 35.78, y + 0.06, z, z + 0.18, "candy jar"); } } });
  // popcorn machine
  bx(35.3, 15.8, 35.8, 16.4, 0.95, 1.15, "#c8202c", { nm: "popcorn machine" });
  bx(35.3, 15.8, 35.32, 16.4, 1.15, 1.75, "#ffffff", { k: "glass", nm: "popcorn glass" }); bx(35.78, 15.8, 35.8, 16.4, 1.15, 1.75, "#ffffff", { k: "glass", nm: "popcorn glass" });
  bx(35.32, 15.8, 35.78, 15.82, 1.15, 1.75, "#ffffff", { k: "glass", nm: "popcorn glass" }); bx(35.32, 16.38, 35.78, 16.4, 1.15, 1.75, "#ffffff", { k: "glass", nm: "popcorn glass" });
  bx(35.28, 15.78, 35.82, 16.42, 1.75, 1.82, "#c8202c", { nm: "popcorn roof" });
  bx(35.32, 15.82, 35.78, 16.38, 1.15, 1.3, "#ffe9a8", { nm: "popcorn" });
  bx(35.54, 16.09, 35.56, 16.11, 1.66, 1.75, "#aab1ba", NS); mesh(new T.CylinderGeometry(0.09, 0.07, 0.1, 14), "#aab1ba", 35.55, 16.1, 1.61); reg(35.46, 16.01, 35.64, 16.19, 1.56, 1.66, "kettle");
  const POP = []; { const pg = new T.SphereGeometry(0.014, 6, 5), pm = lam("#fff4cc"); for (let k = 0; k < 24; k++) { const m = new T.Mesh(pg, pm); m.visible = false; G.main.add(m); POP.push({ m, t: -1, x: 0, y: 0, z: 0, vx: 0, vy: 0, vz: 0 }); } }
  const popL = plight(0xffd27a, 0.9, 3.5, 35.2, 16.1, 1.5);
  // soda fountain and cups
  bx(35.35, 17.0, 35.8, 17.6, 0.95, 1.45, "#aab1ba", { nm: "soda fountain" });
  [17.12, 17.3, 17.48].forEach((y) => bx(35.31, y - 0.025, 35.35, y + 0.025, 1.18, 1.26, "#16181d", { nm: "nozzle" }));
  for (let k = 0; k < 4; k++) { mesh(new T.CylinderGeometry(0.045, 0.035, 0.12, 12), "#ffffff", 35.5, 17.85, 1.01 + k * 0.1); } reg(35.455, 17.805, 35.545, 17.895, 0.95, 1.35, "cups");
  sign("FREE POPCORN · ALWAYS", 35.6, 16.1, 2.05, FACE.w, 0.9, { bg: "#c8202c", fg: "#ffffff", sub: "No seagulls", two: false }); bx(35.84, 16.08, 35.85, 16.12, 1.95, 2.2, "#30353d", NS);
  [13.4, 14.6, 15.8, 17.0, 18.2].forEach((y) => stool(31.35, y, 0, 0.76, "#7a1f2a"));
  // Captain Pickles the parrot on his perch at the end of the bar
  bx(32.27, 18.42, 32.33, 18.48, 1.1, 1.62, C.brass, { nm: "perch pole" }); bx(32.08, 18.43, 32.52, 18.47, 1.62, 1.65, C.brass, { nm: "perch" });
  const PARROT = { g: new T.Group(), x: 32.3, y: 18.45, cd: 8, t: 0 };
  { const g = PARROT.g, green = lam("#3fbf5a"), red = lam("#e8443a"), yel = lam("#ffd23f");
    const b = new T.Mesh(SPH, green); b.scale.set(0.08, 0.13, 0.08); b.position.y = 0.14; g.add(b);
    const h = new T.Mesh(SPH, green); h.scale.setScalar(0.065); h.position.set(0, 0.29, -0.01); g.add(h); PARROT.head = h;
    const bk = new T.Mesh(new T.ConeGeometry(0.025, 0.06, 8), yel); bk.rotation.x = -Math.PI / 2 - 0.5; bk.position.set(0, 0.28, -0.075); g.add(bk);
    [-0.07, 0.07].forEach((x) => { const w = new T.Mesh(SPH, red); w.scale.set(0.02, 0.1, 0.06); w.position.set(x, 0.14, 0.01); g.add(w); });
    const tl = new T.Mesh(new T.ConeGeometry(0.03, 0.2, 6), red); tl.position.set(0, 0.02, 0.07); tl.rotation.x = 2.6; g.add(tl);
    [0.025, -0.025].forEach((x) => { const e = new T.Mesh(SPH, new T.MeshBasicMaterial({ color: 0x111111 })); e.scale.setScalar(0.012); e.position.set(x + Math.sign(x) * 0.03, 0.3, -0.04); g.add(e); });
    g.position.set(WX(PARROT.x), 1.65, WZ(PARROT.y)); g.rotation.y = -Math.PI / 2 + 0.4; G.main.add(g); }
  sign("CAPTAIN PICKLES", 32.0, 18.45, 1.25, FACE.w, 0.5, { bg: "#ffffff", fg: "#3fbf5a", sub: "He repeats things", two: false });

  // ---------------- the shuffleboard table under the windows ----------------
  const SB = { x1: 6.3, x2: 12.8, y1: 18.3, y2: 18.8, z: 0.8 };
  [6.4, 9.55, 12.7].forEach((x) => [[18.28, 18.4], [18.7, 18.82]].forEach(([a, b]) => bx(x - 0.06, a, x + 0.06, b, 0, 0.6, C.walnut, { nm: "shuffleboard leg" })));
  bx(6.3, 18.2, 12.8, 18.9, 0.6, 0.78, C.walnut, { nm: "shuffleboard table", nost: 1 });
  bx(6.3, 18.3, 12.8, 18.8, 0.78, 0.8, "#d2b48c", { nm: "shuffleboard top", nost: 1 });
  bx(6.2, 18.2, 12.9, 18.3, 0.78, 0.86, C.oak2, { nm: "rail", nost: 1 }); bx(6.2, 18.8, 12.9, 18.9, 0.78, 0.86, C.oak2, { nm: "rail", nost: 1 });
  bx(6.2, 18.3, 6.3, 18.8, 0.6, 0.72, "#16181d", { nm: "gutter", nost: 1 }); bx(12.8, 18.3, 12.9, 18.8, 0.6, 0.72, "#16181d", { nm: "gutter", nost: 1 });
  bx(6.2, 18.2, 6.3, 18.3, 0.6, 0.78, C.walnut, { nm: "corner" }); bx(6.2, 18.8, 6.3, 18.9, 0.6, 0.78, C.walnut, { nm: "corner" }); bx(12.8, 18.2, 12.9, 18.3, 0.6, 0.78, C.walnut, { nm: "corner" }); bx(12.8, 18.8, 12.9, 18.9, 0.6, 0.78, C.walnut, { nm: "corner" });
  plate(board(2048, 160, (g, w, h) => { const sx = w / 6.5; g.fillStyle = "rgba(0,0,0,0)"; g.clearRect(0, 0, w, h); g.fillStyle = "rgba(60,30,10,.75)"; g.fillRect(w - 0.15 * sx, 0, 3, h); g.fillRect(w - 0.6 * sx, 0, 3, h); g.fillRect(w - 1.8 * sx, 0, 4, h); g.fillRect(1.8 * sx, 0, 4, h); g.font = `800 60px ${FONT}`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText("3", w - 0.075 * sx, h / 2); g.fillText("2", w - 0.375 * sx, h / 2); g.fillText("1", w - 1.2 * sx, h / 2); g.font = `700 34px ${FONT}`; g.fillText("FOUL LINE", 1.8 * sx + 120, h / 2); }), 9.55, 18.55, 0.801, 0, 6.5, 0.5, { floor: 1, alpha: 1, tone: 1 });
  sign("PLEASE DON'T SIT ON THE TABLE", 13.6, 18.55, 1.5, FACE.w, 0.9, { bg: "#ffffff", fg: "#1f2750", sub: "It is not a bench. We have benches." }); bx(13.58, 18.53, 13.62, 18.57, 0.03, 1.38, "#30353d", NS); mesh(new T.CylinderGeometry(0.15, 0.15, 0.03, 16), "#30353d", 13.6, 18.55, 0.015);
  reg(13.45, 18.4, 13.75, 18.7, 0, 0.03, "sign base");

  // ---------------- window seat cushions and plants inside ----------------
  [[1.2, 4.6], [27.0, 29.6], [31.0, 34.8]].forEach(([a, b]) => { bx(a, 19.35, b, 19.85, 0, 0.42, C.oak2, { nm: "window bench" }); bx(a + 0.02, 19.37, b - 0.02, 19.83, 0.42, 0.52, "#2b6f8f", { nm: "bench cushion" }); });
  [[0.7, 19.3], [14.2, 19.3], [21.9, 19.3]].forEach(([x, y]) => { mesh(new T.CylinderGeometry(0.22, 0.17, 0.45, 16), "#c25b4a", x, y, 0.225); reg(x - 0.22, y - 0.22, x + 0.22, y + 0.22, 0, 0.45, "planter"); for (let k = 0; k < 7; k++) { const l = mesh(new T.ConeGeometry(0.07, 0.8, 6), "#2f8a4a", x + Math.cos(k * 0.9) * 0.08, y + Math.sin(k * 0.9) * 0.08, 0.8); l.rotation.set(Math.sin(k * 0.9) * 0.35, 0, Math.cos(k * 0.9) * 0.35); } reg(x - 0.3, y - 0.3, x + 0.3, y + 0.3, 0.45, 1.25, "plant"); sol(x - 0.22, y - 0.22, x + 0.22, y + 0.22, 0, 1.0, -1); });

  // ---------------- outside: deck, pier, the boat and the harbor ----------------
  bx(1.0, 20.15, 35.0, 25.5, -0.3, 0, "#6e5e4e", { ns: 1, see: 1, g: "out" });
  for (let x = 1.2; x < 35; x += 3.4) bx(x - 0.12, 25.26, x + 0.12, 25.5, -1.6, -0.3, "#4a3424", { ns: 1, g: "out" });
  bx(26.0, 25.5, 28.0, 33.0, -0.3, 0, "#6e5e4e", { ns: 1, see: 1, g: "out" });
  for (let y = 27; y < 33.1; y += 2) [26.1, 27.9].forEach((x) => bx(x - 0.12, y - 0.12, x + 0.12, y + 0.12, -1.6, -0.3, "#4a3424", { ns: 1, g: "out" }));
  // deck railing: posts, a top rail and two steel cables; you can't climb it
  const railRun = (x1, y1, x2, y2) => { const alongX = x2 - x1 > y2 - y1, L = alongX ? x2 - x1 : y2 - y1, n = Math.max(1, Math.round(L / 2)), st = L / n;
    for (let i = 0; i <= n; i++) { const p = (alongX ? x1 : y1) + i * st; const a = Math.max(alongX ? x1 : y1, p - 0.05), b = Math.min(alongX ? x2 : y2, p + 0.05); if (alongX) bx(a, y1, b, y2, 0, 1.0, C.walnut, { ns: 1, nm: "rail post" }); else bx(x1, a, x2, b, 0, 1.0, C.walnut, { ns: 1, nm: "rail post" }); }
    if (alongX) bx(x1, y1, x2, y2, 1.0, 1.08, C.oak, { ns: 1, nm: "top rail" }); else bx(x1, y1, x2, y2, 1.0, 1.08, C.oak, { ns: 1, nm: "top rail" });
    for (let i = 0; i < n; i++) { const a = (alongX ? x1 : y1) + i * st + 0.05, b = (alongX ? x1 : y1) + (i + 1) * st - 0.05; [0.35, 0.68].forEach((z) => { if (alongX) bx(a, (y1 + y2) / 2 - 0.006, b, (y1 + y2) / 2 + 0.006, z, z + 0.012, "#aab1ba", NS); else bx((x1 + x2) / 2 - 0.006, a, (x1 + x2) / 2 + 0.006, b, z, z + 0.012, "#aab1ba", NS); }); }
    sol(x1, y1, x2, y2, 0, 1.2); };
  railRun(1.0, 25.4, 26.0, 25.5); railRun(28.0, 25.4, 35.0, 25.5); railRun(1.0, 20.15, 1.1, 25.4); railRun(34.9, 20.15, 35.0, 25.4);
  railRun(25.9, 25.5, 26.0, 33.0); railRun(28.0, 25.5, 28.1, 33.0); railRun(26.0, 32.9, 28.0, 33.0);
  // deck chairs, a little table, a bench, a lifebuoy, lamps and the coin telescope
  const deckChair = (x, y) => { bx(x - 0.35, y - 0.4, x + 0.35, y + 0.3, 0.3, 0.36, "#e8e8ea", { ns: 1, nm: "deck chair" }); [[-0.35, -0.4], [0.27, -0.4], [-0.35, 0.22], [0.27, 0.22]].forEach(([a, b]) => bx(x + a, y + b, x + a + 0.08, y + b + 0.08, 0, 0.3, "#e8e8ea", NS)); bx(x - 0.35, y - 0.5, x + 0.35, y - 0.4, 0.3, 1.0, "#e8e8ea", { ns: 1, nm: "deck chair back" }); bx(x - 0.45, y - 0.4, x - 0.35, y + 0.3, 0.3, 0.6, "#e8e8ea", { ns: 1 }); bx(x + 0.35, y - 0.4, x + 0.45, y + 0.3, 0.3, 0.6, "#e8e8ea", { ns: 1 }); sol(x - 0.45, y - 0.5, x + 0.45, y + 0.3, 0, 1.0, -1); };
  deckChair(5.0, 23.4); deckChair(7.4, 23.4); deckChair(14.4, 23.4); deckChair(16.8, 23.4); deckChair(10.0, 23.4); deckChair(11.8, 23.4); deckChair(19.6, 23.4); deckChair(21.2, 23.4);
  [[6.2, 23.5], [15.6, 23.5]].forEach(([x, y]) => { mesh(new T.CylinderGeometry(0.28, 0.28, 0.04, 20), "#e8e8ea", x, y, 0.52); bx(x - 0.03, y - 0.03, x + 0.03, y + 0.03, 0.03, 0.5, "#e8e8ea", NS); mesh(new T.CylinderGeometry(0.18, 0.18, 0.03, 16), "#e8e8ea", x, y, 0.015); reg(x - 0.28, y - 0.28, x + 0.28, y + 0.28, 0.5, 0.54, "deck table"); reg(x - 0.18, y - 0.18, x + 0.18, y + 0.18, 0, 0.03, "deck table base"); sol(x - 0.25, y - 0.25, x + 0.25, y + 0.25, 0, 0.54, -1); });
  bx(3.0, 20.15, 6.0, 20.6, 0, 0.42, C.walnut, { nm: "deck bench" });
  { const t = mesh(new T.TorusGeometry(0.3, 0.07, 10, 24), "#ff5c3a", 20.0, 20.22, 1.7); [0, 1, 2, 3].forEach((k) => { const s = mesh(new T.TorusGeometry(0.3, 0.072, 10, 6, 0.4), "#ffffff", 20.0, 20.225, 1.7); s.rotation.z = k * Math.PI / 2 - 0.2; }); reg(19.63, 20.15, 20.37, 20.29, 1.33, 2.07, "lifebuoy"); bx(19.97, 20.15, 20.03, 20.2, 2.07, 2.13, "#aab1ba", NS); }
  sign("NO SWIMMING", 11.0, 25.38, 1.35, FACE.n, 1.0, { bg: "#ffffff", fg: "#c8202c", sub: "The fish asked for privacy", two: false }); bx(10.98, 25.42, 11.02, 25.44, 1.08, 1.22, "#30353d", NS);
  [[3, 20.15], [10, 20.15], [17, 20.15], [30, 20.15]].forEach(([x, y]) => { bx(x - 0.08, y, x + 0.08, y + 0.1, 2.6, 2.9, "#16181d", { ns: 1, nm: "wall lamp" }); mesh(new T.SphereGeometry(0.07, 12, 8), 0xffd9a0, x, y + 0.12, 2.75, { basic: 1, glow: 1 }); reg(x - 0.07, y + 0.1, x + 0.07, y + 0.19, 2.68, 2.82, "lamp bulb"); });
  const deckL = plight(0xffc890, 1.2, 12, 18, 22.5, 2.6);
  // string lights over the deck: from the wall down to the railing posts
  { const bulbs = [], wire = lam("#16181d");
    for (let k = 0; k < 8; k++) { const x0 = 3 + k * 4.2, x1 = x0 + 2.1; const a = new T.Vector3(WX(x0), 3.6, WZ(20.2)), b = new T.Vector3(WX(x1), 1.9, WZ(25.45)); const mid = a.clone().lerp(b, 0.5); mid.y -= 0.55; const curve = new T.QuadraticBezierCurve3(a, mid, b);
      const tube = new T.Mesh(new T.TubeGeometry(curve, 16, 0.006, 4, false), wire); G.main.add(tube);
      for (let i = 1; i < 9; i++) { const p = curve.getPoint(i / 9); const m = new T.Mesh(new T.SphereGeometry(0.035, 8, 6), new T.MeshBasicMaterial({ color: [0xffd9a0, 0xa8f4ff, 0xffb0e8][i % 3], toneMapped: false })); m.position.copy(p); G.main.add(m); bulbs.push(m); } }
    [3, 7.2, 11.4, 15.6, 19.8, 24, 28.2, 32.4].forEach((x) => { bx(x - 0.03, 20.15, x + 0.03, 20.22, 3.55, 3.65, "#16181d", { ns: 1 }); });
    [5.1, 9.3, 13.5, 17.7, 21.9, 26.1, 30.3, 34.5].forEach((x) => { const xx = Math.min(34.9, x); bx(xx - 0.04, 25.41, xx + 0.04, 25.49, 1.08, 1.95, "#16181d", { ns: 1, nm: "light pole" }); });
    reg(1, 20.2, 35, 25.5, 1.8, 3.7, "string lights", 1); }
  // pier lamps, the fishing spot, bollards and the coin telescope
  [[25.95, 28.6], [28.05, 31.4]].forEach(([x, y]) => { bx(x - 0.05, y - 0.05, x + 0.05, y + 0.05, 1.08, 2.6, "#16181d", { ns: 1, nm: "pier lamp" }); bx(x - 0.12, y - 0.12, x + 0.12, y + 0.12, 2.6, 2.9, "#16181d", { ns: 1, nm: "lantern" }); bx(x - 0.1, y - 0.1, x + 0.1, y + 0.1, 2.62, 2.88, "#ffe2b0", { k: "glow", ns: 1, ok: 1 }); });
  const pierL = plight(0xffd9a0, 1.0, 8, 27, 30.6, 2.6);
  mesh(new T.CylinderGeometry(0.16, 0.13, 0.3, 14), "#1d6fb8", 26.4, 32.5, 0.15); reg(26.24, 32.34, 26.56, 32.66, 0, 0.3, "bait bucket"); sol(26.24, 32.34, 26.56, 32.66, 0, 0.3, -1);
  bx(27.55, 32.35, 27.85, 32.75, 0, 0.18, "#2f8a4a", { nm: "tackle box" }); bx(27.55, 32.35, 27.85, 32.75, 0.18, 0.2, "#256f3a", { nm: "tackle box lid" });
  sign("GONE FISHING", 26.4, 32.92, 1.25, FACE.n, 0.6, { bg: "#ffffff", fg: "#1d6fb8", sub: "Results not guaranteed", two: false }); bx(26.38, 32.92, 26.42, 32.95, 1.08, 1.17, "#30353d", NS);
  // coin telescope on the deck
  { const x = 33.6, y = 24.6; bx(x - 0.06, y - 0.06, x + 0.06, y + 0.06, 0.03, 1.1, "#2b6f8f", { nm: "viewer post" }); mesh(new T.CylinderGeometry(0.2, 0.2, 0.03, 16), "#2b6f8f", x, y, 0.015); reg(x - 0.2, y - 0.2, x + 0.2, y + 0.2, 0, 0.03, "viewer base");
    const hd = mesh(new T.BoxGeometry(0.3, 0.26, 0.42), "#2b6f8f", x, y, 1.23); reg(x - 0.15, y - 0.21, x + 0.15, y + 0.21, 1.1, 1.36, "viewer head"); [-0.07, 0.07].forEach((dx) => { const e = mesh(new T.CylinderGeometry(0.045, 0.045, 0.12, 12), "#16181d", x + dx, y - 0.24, 1.25); e.rotation.x = Math.PI / 2; }); sol(x - 0.2, y - 0.25, x + 0.2, y + 0.25, 0, 1.36, -1);
    sign("HARBOR VIEWER · 25¢", x, y + 0.215, 1.23, 0, 0.3, { bg: "#ffd23f", fg: "#1f2329", sub: "Free tonight", two: false }); }
  // the boat, the water, the far shore, the lighthouse, the moon and stars (all in the "out" group, not checked)
  const BOAT = new T.Group(); BOAT.position.set(WX(29.9), -0.75, WZ(29.5)); G.out.add(BOAT);
  { const hull = new T.Mesh(new T.BoxGeometry(1.6, 0.6, 3.6), lam("#eef1f3")); hull.position.y = 0.2; BOAT.add(hull); const prow = new T.Mesh(new T.CylinderGeometry(0.8, 0.8, 0.6, 3, 1, false, 0, Math.PI), lam("#eef1f3")); prow.position.set(0, 0.2, 1.8); prow.rotation.y = Math.PI; prow.scale.set(1, 1, 1.2); BOAT.add(prow);
    const stripe = new T.Mesh(new T.BoxGeometry(1.62, 0.08, 3.62), lam("#1d6fb8")); stripe.position.y = 0.36; BOAT.add(stripe); const cab = new T.Mesh(new T.BoxGeometry(1.1, 0.7, 1.2), lam("#1f2750")); cab.position.set(0, 0.85, -0.4); BOAT.add(cab); const win = new T.Mesh(new T.BoxGeometry(1.12, 0.22, 0.6), new T.MeshBasicMaterial({ color: 0xffd9a0, toneMapped: false })); win.position.set(0, 0.95, -0.4); BOAT.add(win);
    const nm = new T.Mesh(new T.PlaneGeometry(1.6, 0.25), new T.MeshBasicMaterial({ map: signTex("S.S. SNACK ATTACK", "#eef1f3", "#1f2750"), toneMapped: false })); nm.position.set(-0.81, 0.18, 0); nm.rotation.y = -Math.PI / 2; BOAT.add(nm); }
  { const r = new T.Mesh(new T.TubeGeometry(new T.QuadraticBezierCurve3(new T.Vector3(WX(28.05), 0.5, WZ(28.4)), new T.Vector3(WX(28.6), -0.2, WZ(28.4)), new T.Vector3(WX(29.1), -0.3, WZ(28.3))), 10, 0.012, 4), lam("#d9c7a7")); G.out.add(r); }
  const waterTex = canvasTex(512, 512, (g, w, h) => { g.fillStyle = "#808080"; g.fillRect(0, 0, w, h); for (let i = 0; i < 900; i++) { g.fillStyle = `rgba(${Math.random() < 0.5 ? "255,255,255" : "0,0,0"},${0.05 + Math.random() * 0.12})`; g.beginPath(); g.ellipse(Math.random() * w, Math.random() * h, 6 + Math.random() * 30, 2 + Math.random() * 5, 0, 0, 7); g.fill(); } });
  waterTex.wrapS = waterTex.wrapT = T.RepeatWrapping; waterTex.repeat.set(30, 30); waterTex.encoding = T.LinearEncoding;
  const WATER = new T.Mesh(new T.PlaneGeometry(300, 220), new T.MeshStandardMaterial({ color: 0x0b2440, roughness: 0.22, metalness: 0.4, bumpMap: waterTex, bumpScale: 0.06, envMapIntensity: 0.08 }));
  WATER.rotation.x = -Math.PI / 2; WATER.position.set(WX(18), -0.82, WZ(20.2 + 110)); G.out.add(WATER);
  { const sand = new T.Mesh(new T.PlaneGeometry(300, 40), lam("#2a2a33")); sand.rotation.x = -Math.PI / 2; sand.position.set(WX(18), -0.84, WZ(0)); G.out.add(sand); }
  // the town across the water: rows of lit windows
  { const geo = new T.BoxGeometry(1, 1, 1), win = new T.MeshBasicMaterial({ color: 0xffffff, toneMapped: false }), blk = lam("#101426"); const bld = new T.InstancedMesh(geo, blk, 70), lit = new T.InstancedMesh(geo, win, 420); const d = new T.Object3D(), c = new T.Color(); let li = 0;
    for (let i = 0; i < 70; i++) { const x = -70 + i * 2.2 + Math.random(), w = 1.6 + Math.random() * 1.4, h = 3 + Math.random() * 14, y = 95 + Math.random() * 10; d.position.set(WX(x), h / 2 - 0.8, WZ(y)); d.scale.set(w, h, 2); d.updateMatrix(); bld.setMatrixAt(i, d.matrix);
      for (let k = 0; k < 6 && li < 420; k++) { if (Math.random() < 0.35) continue; d.position.set(WX(x + (Math.random() - 0.5) * w * 0.7), 0.5 + Math.random() * (h - 1.2), WZ(y - 1.02)); d.scale.set(0.28, 0.32, 0.05); d.updateMatrix(); lit.setMatrixAt(li, d.matrix); lit.setColorAt(li, c.set(Math.random() < 0.7 ? 0xffd9a0 : 0x9fd4ff)); li++; } }
    lit.count = li; if (lit.instanceColor) lit.instanceColor.needsUpdate = true; G.out.add(bld); G.out.add(lit); }
  // lighthouse on a rock, with a beam that sweeps round
  const LH = { x: -16, y: 62 };
  { const rock = new T.Mesh(new T.ConeGeometry(6, 3, 9), lam("#2a2a33")); rock.position.set(WX(LH.x), 0.2, WZ(LH.y)); G.out.add(rock);
    for (let k = 0; k < 5; k++) { const s = new T.Mesh(new T.CylinderGeometry(1.4 - k * 0.12, 1.5 - k * 0.12, 2.2, 20), lam(k % 2 ? "#c8202c" : "#eef1f3")); s.position.set(WX(LH.x), 2.6 + k * 2.2, WZ(LH.y)); G.out.add(s); }
    const lamp = new T.Mesh(new T.SphereGeometry(0.7, 14, 10), new T.MeshBasicMaterial({ color: 0xfff2c0, toneMapped: false })); lamp.position.set(WX(LH.x), 13.6, WZ(LH.y)); G.out.add(lamp);
    const cap = new T.Mesh(new T.ConeGeometry(1.1, 1.2, 12), lam("#16181d")); cap.position.set(WX(LH.x), 14.8, WZ(LH.y)); G.out.add(cap); }
  const BEAM = new T.Group(); BEAM.position.set(WX(LH.x), 13.6, WZ(LH.y)); G.out.add(BEAM);
  { const cone = new T.Mesh(new T.ConeGeometry(3.2, 60, 24, 1, true), new T.MeshBasicMaterial({ color: 0xfff2c0, transparent: true, opacity: 0.07, side: T.DoubleSide, depthWrite: false, blending: T.AdditiveBlending, toneMapped: false })); cone.rotation.z = Math.PI / 2; cone.position.x = 30; BEAM.add(cone); }
  { const moon = new T.Mesh(new T.SphereGeometry(5, 24, 16), new T.MeshBasicMaterial({ color: 0xf4f1e0, toneMapped: false })); moon.position.set(WX(60), 34, WZ(150)); G.out.add(moon);
    const sp = []; for (let i = 0; i < 700; i++) { const a = Math.random() * Math.PI, e = 0.08 + Math.random() * 1.2, r = 180; sp.push(Math.cos(a) * Math.cos(e) * r + WX(18), Math.sin(e) * r * 0.6, Math.sin(a) * Math.cos(e) * r + WZ(30)); }
    const sg = new T.BufferGeometry(); sg.setAttribute("position", new T.Float32BufferAttribute(sp, 3)); G.out.add(new T.Points(sg, new T.PointsMaterial({ color: 0xffffff, size: 0.7, sizeAttenuation: true, fog: false }))); }
  // moon glitter on the water
  { const gl = canvasTex(64, 512, (g, w, h) => { for (let i = 0; i < 260; i++) { g.fillStyle = `rgba(255,250,220,${Math.random() * 0.8})`; g.fillRect(w / 2 + (Math.random() - 0.5) * w * (i / 260), Math.random() * h, 2 + Math.random() * 6, 1.5); } }); const m = new T.Mesh(new T.PlaneGeometry(18, 120), new T.MeshBasicMaterial({ map: gl, transparent: true, opacity: 0.55, depthWrite: false, toneMapped: false, blending: T.AdditiveBlending })); m.rotation.x = -Math.PI / 2; m.position.set(WX(46), -0.8, WZ(95)); m.rotation.z = -0.25; G.out.add(m); window.LG_GLIT = m; }

  // ---------------- Gary the seagull (sits on a railing post, flies off when you come close) ----------------
  const GULL_SPOTS = [[1.05, 22.8], [9.9, 25.45], [20.1, 25.45], [34.95, 22.8], [28.05, 29.0], [26.0, 32.95]];
  const GULL = { g: new T.Group(), i: 1, fly: 0, from: null, to: null, cd: 0 };
  { const g = GULL.g, wht = lam("#f4f4f4"), gry = lam("#9aa0a8"), yel = lam("#ffb24a");
    const b = new T.Mesh(SPH, wht); b.scale.set(0.09, 0.09, 0.17); b.position.y = 0.12; g.add(b);
    const h = new T.Mesh(SPH, wht); h.scale.setScalar(0.06); h.position.set(0, 0.22, -0.13); g.add(h);
    const bk = new T.Mesh(new T.ConeGeometry(0.018, 0.08, 6), yel); bk.rotation.x = -Math.PI / 2; bk.position.set(0, 0.21, -0.21); g.add(bk);
    GULL.wings = [-1, 1].map((sd) => { const w = new T.Mesh(new T.BoxGeometry(0.22, 0.012, 0.12), gry); w.position.set(sd * 0.13, 0.15, 0.01); g.add(w); return w; });
    [0.025, -0.025].forEach((x) => { const e = new T.Mesh(SPH, new T.MeshBasicMaterial({ color: 0x111111 })); e.scale.setScalar(0.01); e.position.set(x + Math.sign(x) * 0.02, 0.24, -0.16); g.add(e); });
    G.fx.add(g); const s = GULL_SPOTS[GULL.i]; g.position.set(WX(s[0]), 1.08, WZ(s[1])); g.rotation.y = Math.PI; }

  // ---------------- Dusty the robot vacuum ----------------
  const DUSTY = { g: new T.Group(), x: 11.5, y: 15.5, a: 0.6, stuck: 0, t: 0, face: null };
  { const g = DUSTY.g; const b = new T.Mesh(new T.CylinderGeometry(0.17, 0.17, 0.08, 24), lam("#20232b")); b.position.y = 0.05; g.add(b); const t = new T.Mesh(new T.CylinderGeometry(0.12, 0.12, 0.012, 20), lam("#3FE0E8")); t.position.y = 0.095; g.add(t);
    const fc = document.createElement("canvas"); fc.width = fc.height = 64; DUSTY.fc = fc; const ft = new T.CanvasTexture(fc); DUSTY.ft = ft; const f = new T.Mesh(new T.CircleGeometry(0.1, 20), new T.MeshBasicMaterial({ map: ft, transparent: true, toneMapped: false })); f.rotation.x = -Math.PI / 2; f.position.y = 0.102; g.add(f); G.fx.add(g); }

  // ---------------- the sea friends hanging out ----------------
  const NPC = [];
  [["#7AA7FF", 16.8, 6.0, 0.56, 0, "sofa"], ["#A55BE0", 19.0, 6.0, 0.56, 0, "sofa"], ["#5BD18B", 21.2, 6.0, 0.56, 0, "sofa"], ["#F5C04A", 2.2, 4.5, LF + 0.42, 0.5, "loft"], ["#FF5C8A", 30.9, 15.2, 0, Math.PI / 2 + 0.2, "bar"], ["#ECEEFB", 8.0, 22.9, 0, 0.0, "deck"]].forEach(([c, x, y, f, r, where]) => {
    const h = friend(c, 0.95); h.position.set(WX(x), f, WZ(y)); h.rotation.y = r; G.fx.add(h); NPC.push({ h, x, y, f, where, base: r, name: { "#7AA7FF": "Blue", "#A55BE0": "Violet", "#5BD18B": "Green", "#F5C04A": "Goldie", "#FF5C8A": "Rosie", "#ECEEFB": "Pearl" }[c] }); sol(x - 0.3, y - 0.3, x + 0.3, y + 0.3, f, f + 0.9, -1);
  });

  // ---------------- every seat in the Lounge: one person per seat ----------------
  const SEATS = [];
  const seat = (x, y, z, yaw, what, o) => SEATS.push(Object.assign({ x, y, z, yaw, what, f: 0 }, o || {}));
  [15.9, 16.8, 17.9, 19.0, 20.1, 21.2, 22.1].forEach((x) => seat(x, 6.0, 0.56, 0, "the sofa", { sofa: 1 }));
  [4.6, 5.6].forEach((y) => { seat(15.0, y, 0.56, -0.7, "the sofa", { sofa: 1 }); seat(23.0, y, 0.56, 0.7, "the sofa", { sofa: 1 }); });
  seat(16.3, 2.4, 0.42, 0, "a bean bag", { sofa: 1 }); seat(21.7, 2.4, 0.42, 0, "a bean bag", { sofa: 1 });
  [[2.0, 2.0], [3.7, 1.5], [2.1, 4.5]].forEach(([x, y]) => seat(x, y, LF + 0.42, -Math.PI / 2, "a loft bean bag", { f: LF }));
  [15.0, 16.6, 18.2, 19.8, 21.4, 23.0].forEach((x) => seat(x, 9.25, 1.12, 0, "a back row stool", { f: 0.4, sofa: 1 }));
  [13.4, 14.6, 15.8, 17.0, 18.2].forEach((y) => seat(31.35, y, 0.76, -Math.PI / 2, "a snack bar stool"));
  [14.2, 16.4].forEach((y) => seat(2.9, y, 0.52, Math.PI / 2, "an armchair by the fire"));
  [14.95, 15.75].forEach((y) => seat(5.25, y, 0.52, Math.PI / 2, "the fire sofa"));
  seat(4.85, 3.3, 0.46, -Math.PI / 2, "the card table"); seat(6.95, 3.3, 0.46, Math.PI / 2, "the card table"); seat(5.9, 2.25, 0.46, Math.PI, "the card table"); seat(5.9, 4.35, 0.46, 0, "the card table");
  [1.7, 2.9, 4.1, 27.5, 28.3, 29.1, 31.6, 32.6, 33.6, 34.4].forEach((x) => seat(x, 19.6, 0.52, 0, "a window seat"));
  [3.0, 3.8, 4.6, 8.2, 9.0, 9.8].forEach((y) => seat(32.45, y, 0.45, -Math.PI / 2, "the aquarium bench"));
  [5.0, 7.4, 10.0, 11.8, 14.4, 16.8, 19.6, 21.2].forEach((x) => seat(x, 23.35, 0.36, Math.PI, "a deck chair"));
  [3.6, 4.5, 5.4].forEach((x) => seat(x, 20.4, 0.42, Math.PI, "the deck bench"));
  // the sea friends already have seats: those are taken
  NPC.forEach((n) => { let best = null, bd = 0.5; SEATS.forEach((q) => { const d = Math.hypot(q.x - n.x, q.y - n.y); if (d < bd && Math.abs(q.f - (n.f > 2 ? LF : 0)) < 1) { bd = d; best = q; } }); if (best) best.by = n.name; });

  // =====================================================================
  //  THE LOUNGE GAMES (walk up, press E). In the preview you play the computer; in the real Lobby friends take the other side.
  // =====================================================================
  scene.add(camera);
  const hudEl = document.getElementById("hud"), promptEl = document.getElementById("prompt"), playBtn = document.getElementById("playBtn");
  const ray = new T.Raycaster(), ndc = new T.Vector2(0, 0);
  stage.addEventListener("pointermove", (e) => { const r = stage.getBoundingClientRect(); ndc.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1); });
  // where the mouse points, on a flat level (z) or on a wall facing +y at map y
  function onLevel(z) { ray.setFromCamera(ndc, camera); const o = ray.ray.origin, d = ray.ray.direction; if (Math.abs(d.y) < 1e-6) return null; const t = (z - o.y) / d.y; if (t < 0) return null; return [o.x + d.x * t + 18, o.z + d.z * t + 13]; }
  function onWallY(y) { ray.setFromCamera(ndc, camera); const o = ray.ray.origin, d = ray.ray.direction; if (Math.abs(d.z) < 1e-6) return null; const t = (WZ(y) - o.z) / d.z; if (t < 0) return null; return [o.x + d.x * t + 18, o.y + d.y * t]; }
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  let game = null;
  const camGoal = { p: new T.Vector3(), t: new T.Vector3(), fov: 70 }, camNow = { p: new T.Vector3(), t: new T.Vector3(), init: false };
  function setCam(p, t, fov) { camGoal.p.set(WX(p[0]), p[2], WZ(p[1])); camGoal.t.set(WX(t[0]), t[2], WZ(t[1])); camGoal.fov = fov || 70; }
  const hintEl = document.querySelector(".hint");
  function hud(title, score, help) { if (!hudEl) return; hudEl.hidden = false; if (hintEl) hintEl.style.visibility = "hidden"; hudEl.innerHTML = `<b>${title}</b>${score ? `<span>${score}</span>` : ""}<small>${help || ""} · <kbd>E</kbd> or <kbd>Esc</kbd> to stand up</small>`; }
  const HS = { pool: null, air: [0, 0], four: [0, 0], darts: null, shuffle: 0, fish: 0, mallow: 0, quiz: 0 };
  function drawScores() {
    const g = hsC.getContext("2d"), w = hsC.width, h = hsC.height; g.fillStyle = "#16181d"; g.fillRect(0, 0, w, h);
    g.fillStyle = "#c9a45a"; g.fillRect(0, 0, w, 86); g.fillStyle = "#16181d"; g.font = `800 52px ${FONT}`; g.textAlign = "center"; g.fillText("LOUNGE HIGH SCORES", w / 2, 62);
    const rows = [["Pool · clear the table", HS.pool ? HS.pool + " shots" : "—"], ["Air hockey · wins v computer", HS.air[0] + " – " + HS.air[1]], ["Four in a row · wins v computer", HS.four[0] + " – " + HS.four[1]], ["Darts 301 · fewest darts", HS.darts ? HS.darts + " darts" : "—"], ["Shuffleboard · best round", HS.shuffle + " pts"], ["Fishing · things caught", String(HS.fish)], ["Perfect marshmallows", String(HS.mallow)], ["Movie quiz · best", HS.quiz + " / 8"]];
    g.textAlign = "left"; g.font = `600 36px ${FONT}`; rows.forEach(([a, b], i) => { g.fillStyle = "#ECEEFB"; g.fillText(a, 46, 140 + i * 54); g.fillStyle = "#3FE0E8"; g.textAlign = "right"; g.fillText(b, w - 46, 140 + i * 54); g.textAlign = "left"; });
    hsTex.needsUpdate = true;
  }
  drawScores();
  let parrotNext = null; // something the parrot will say next time
  const say = (t) => { parrotNext = t; };
  const sndOn = () => SND.ctx && SND.on && SND.ctx.state === "running";
  const now = () => SND.ctx.currentTime;
  function clack(v) { if (!sndOn()) return; const t = now(); blip(1900 + Math.random() * 400, t, 0.05, 0.08 * v, "triangle"); hiss(t, 0.03, 0.25 * v, "highpass", 3000, 1); }
  function thud(v) { if (!sndOn()) return; hiss(now(), 0.08, 0.35 * v, "lowpass", 380, 1); }
  function buzzer() { if (!sndOn()) return; const t = now(); blip(220, t, 0.6, 0.1, "sawtooth", 200); blip(330, t, 0.6, 0.05, "square", 300); }
  function ding() { if (!sndOn()) return; const t = now(); [880, 1175, 1568].forEach((f, i) => blip(f, t + i * 0.09, 0.5, 0.07, "triangle")); }
  function plonk() { if (!sndOn()) return; const t = now(); blip(160, t, 0.18, 0.12, "sine", 90); hiss(t + 0.05, 0.25, 0.2, "bandpass", 900, 2); }

  // ---------------- POOL (clear the table in as few shots as you can) ----------------
  const BR = 0.0286, POOLC = { cx: 19, cy: 14.2 };
  const BCOL = ["#f4f1e8", "#f2c14e", "#1d6fb8", "#d94f4f", "#6b3fa0", "#ff8a3c", "#2f8a4a", "#8a1f2a", "#16181d"];
  const ballTex = (n) => canvasTex(128, 64, (g, w, h) => { const c = n === 0 ? "#f4f1e8" : BCOL[n > 8 ? n - 8 : n]; g.fillStyle = n > 8 ? "#f4f1e8" : c; g.fillRect(0, 0, w, h); if (n > 8) { g.fillStyle = c; g.fillRect(0, h * 0.28, w, h * 0.44); } if (n) [w * 0.25, w * 0.75].forEach((x) => { g.fillStyle = "#f4f1e8"; g.beginPath(); g.arc(x, h / 2, 11, 0, 7); g.fill(); g.fillStyle = "#16181d"; g.font = `800 14px ${FONT}`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(String(n), x, h / 2 + 1); }); });
  const BGEO = new T.SphereGeometry(BR, 20, 14);
  const BALLS = [];
  for (let n = 0; n < 16; n++) { const m = new T.Mesh(BGEO, new T.MeshStandardMaterial({ map: ballTex(n), roughness: 0.18, metalness: 0.0, envMapIntensity: 0.6 })); m.castShadow = true; G.main.add(m); BALLS.push({ n, m, x: 0, y: 0, vx: 0, vy: 0, in: false }); }
  const POCKETS = [[17.75, 13.57], [19.0, 13.55], [20.25, 13.57], [17.75, 14.83], [19.0, 14.85], [20.25, 14.83]];
  const PS = { shots: 0, power: 0, charge: -1, aim: 0, kAim: 0, msg: "Aim with the mouse, hold the button, let go to shoot.", wait: 0, events: [] };
  function rack() {
    const fx = POOL.x1 + (POOL.x2 - POOL.x1) * 0.75, order = [1, 9, 2, 10, 8, 3, 11, 4, 12, 5, 13, 6, 14, 7, 15]; let k = 0;
    const rest = order.filter((n) => n !== 8); for (let i = rest.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [rest[i], rest[j]] = [rest[j], rest[i]]; }
    for (let r = 0; r < 5; r++) for (let c = 0; c <= r; c++) { const n = r === 2 && c === 1 ? 8 : rest[k++]; const b = BALLS[n]; b.x = fx + r * BR * 1.74; b.y = POOLC.cy + (c - r / 2) * BR * 2.02; b.vx = b.vy = 0; b.in = false; b.m.visible = true; }
    const q = BALLS[0]; q.x = POOL.x1 + (POOL.x2 - POOL.x1) * 0.25; q.y = POOLC.cy; q.vx = q.vy = 0; q.in = false; q.m.visible = true; PS.shots = 0;
    BALLS.forEach(placeBall);
  }
  const _ax = new T.Vector3();
  function placeBall(b) { b.m.position.set(WX(b.x), POOL.z + BR, WZ(b.y)); }
  rack();
  const cue = new T.Group(); { const c = new T.Mesh(new T.CylinderGeometry(0.006, 0.012, 1.45, 10), lam("#d2b48c")); c.rotation.z = Math.PI / 2; c.position.x = -0.76; cue.add(c); const tip = new T.Mesh(new T.CylinderGeometry(0.0062, 0.0062, 0.02, 8), lam("#2f6db5")); tip.rotation.z = Math.PI / 2; tip.position.x = -0.03; cue.add(tip); const bt = new T.Mesh(new T.CylinderGeometry(0.0125, 0.0125, 0.4, 10), lam("#16181d")); bt.rotation.z = Math.PI / 2; bt.position.x = -1.3; cue.add(bt); } cue.visible = false; G.fx.add(cue);
  const aimLine = new T.LineSegments(new T.BufferGeometry().setFromPoints([new T.Vector3(), new T.Vector3(), new T.Vector3(), new T.Vector3()]), new T.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.75, depthTest: false })); aimLine.visible = false; aimLine.frustumCulled = false; aimLine.renderOrder = 998; G.fx.add(aimLine);
  const ghost = new T.Mesh(new T.RingGeometry(BR * 0.82, BR, 24), new T.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.8, depthTest: false, side: T.DoubleSide })); ghost.rotation.x = -Math.PI / 2; ghost.visible = false; ghost.renderOrder = 998; G.fx.add(ghost);
  const moving = (list) => list.some((b) => !b.in && Math.hypot(b.vx, b.vy) > 0.004);
  function poolStep(dt) {
    const sub = 10, h = dt / sub, L = BALLS;
    for (let s = 0; s < sub; s++) {
      for (const b of L) { if (b.in) continue; const sp = Math.hypot(b.vx, b.vy); if (sp > 0) { const ns = Math.max(0, sp - (0.32 + sp * 0.12) * h); b.vx *= ns / sp; b.vy *= ns / sp; } b.x += b.vx * h; b.y += b.vy * h;
        for (const [px, py] of POCKETS) if (Math.hypot(b.x - px, b.y - py) < 0.052) { b.in = true; b.vx = b.vy = 0; b.m.visible = false; PS.events.push(b.n); plonk(); break; }
        if (b.in) continue;
        if (b.x < POOL.x1 + BR) { b.x = POOL.x1 + BR; b.vx = Math.abs(b.vx) * 0.75; thud(Math.min(1, Math.abs(b.vx))); } if (b.x > POOL.x2 - BR) { b.x = POOL.x2 - BR; b.vx = -Math.abs(b.vx) * 0.75; thud(Math.min(1, Math.abs(b.vx))); }
        if (b.y < POOL.y1 + BR) { b.y = POOL.y1 + BR; b.vy = Math.abs(b.vy) * 0.75; thud(Math.min(1, Math.abs(b.vy))); } if (b.y > POOL.y2 - BR) { b.y = POOL.y2 - BR; b.vy = -Math.abs(b.vy) * 0.75; thud(Math.min(1, Math.abs(b.vy))); } }
      for (let i = 0; i < L.length; i++) { const a = L[i]; if (a.in) continue; for (let j = i + 1; j < L.length; j++) { const b = L[j]; if (b.in) continue; const dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy); if (d < BR * 2 && d > 1e-6) { const nx = dx / d, ny = dy / d, rv = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny; if (rv > 0) { const k = rv * 0.97; a.vx -= k * nx; a.vy -= k * ny; b.vx += k * nx; b.vy += k * ny; if (rv > 0.05) clack(Math.min(1, rv / 2)); } const o = (BR * 2 - d) / 2; a.x -= nx * o; a.y -= ny * o; b.x += nx * o; b.y += ny * o; } } }
    }
    for (const b of L) { if (b.in) continue; const sp = Math.hypot(b.vx, b.vy); if (sp > 1e-4) { _ax.set(b.vy, 0, -b.vx).normalize(); _ax.set(-b.vy, 0, b.vx).normalize(); b.m.rotateOnWorldAxis(_ax.set(b.vy / sp, 0, -b.vx / sp), -sp * dt / BR); } placeBall(b); }
  }
  // You stand behind the cue ball and look down the cue, like other pool games. Moving the mouse walks you round the ball.
  PS.aim = 0; PS.tilt = 0.32; PS.side = false; PS.watch = 0;
  document.addEventListener("mousemove", (e) => { if (game !== POOLG) return; PS.aim += (e.movementX || 0) * 0.0032; PS.tilt = clamp(PS.tilt + (e.movementY || 0) * 0.0025, 0.1, 1.0); });
  let touchX = null;
  stage.addEventListener("touchmove", (e) => { if (game !== POOLG || !e.touches[0]) return; const x = e.touches[0].clientX; if (touchX !== null) PS.aim += (x - touchX) * 0.006; touchX = x; }, { passive: true });
  stage.addEventListener("touchend", () => { touchX = null; });
  const poolLock = () => { try { if (document.pointerLockElement !== stage && stage.requestPointerLock && matchMedia("(pointer: fine)").matches) stage.requestPointerLock(); } catch (e) {} };
  const POOLG = {
    name: "Pool", start() {
      PS.msg = "Move the mouse to aim. Hold the button for power, let go to shoot."; PS.charge = -1; PS.side = false; PS.tilt = 0.32;
      const q = BALLS[0]; PS.aim = Math.atan2(q.y - P.y, q.x - P.x); // start looking the way you walked up
      if (!isFinite(PS.aim)) PS.aim = 0; poolLock();
    },
    end() { cue.visible = false; aimLine.visible = false; ghost.visible = false; if (document.pointerLockElement === stage && !isFull()) document.exitPointerLock();
      // you end up standing where your cue was
      const q = BALLS[0], ax = Math.cos(PS.aim), ay = Math.sin(PS.aim); for (let d = 0.8; d < 2.6; d += 0.1) { const x = q.x - ax * d, y = q.y - ay * d; if (inBounds(x, y) && !hitAt(x, y, 0, 1.7, null, STEP) && supportAt(x, y, 0, STEP) < 0.05) { P.x = x; P.y = y; P.f = 0; SAFE.x = x; SAFE.y = y; SAFE.f = 0; yaw = Math.atan2(-ax, -ay); pitch = -0.35; break; } } },
    update(dt) {
      const q = BALLS[0], still = !moving(BALLS);
      if (still && PS.events.length) { const ev = PS.events; PS.events = []; const left = BALLS.filter((b) => b.n && b.n !== 8 && !b.in).length;
        if (ev.includes(8) && left > 0) { PS.msg = "The 8 ball went in early. It's shy, remember? Racking again."; PS.wait = 2.2; say("Squawk! The eight ball! Again!"); }
        else if (ev.includes(8) && left === 0) { PS.msg = `Table cleared in ${PS.shots} shots!`; ding(); if (!HS.pool || PS.shots < HS.pool) { HS.pool = PS.shots; drawScores(); } say("Squawk! Pool shark! Pool shark!"); PS.wait = 3; }
        else if (ev.includes(0)) { PS.msg = "Scratch! The cue ball goes back on the spot."; q.in = false; q.m.visible = true; q.x = POOL.x1 + (POOL.x2 - POOL.x1) * 0.25; q.y = POOLC.cy; while (BALLS.some((b) => b !== q && !b.in && Math.hypot(b.x - q.x, b.y - q.y) < BR * 2.1)) q.y += BR * 2.2; placeBall(q); }
        else PS.msg = `Nice. ${15 - BALLS.filter((b) => b.n && !b.in).length} down, ${BALLS.filter((b) => b.n && !b.in).length} to go.`; }
      if (PS.wait > 0) { PS.wait -= dt; if (PS.wait <= 0) rack(); }
      // fine aim with A / D (hold Shift for tiny steps)
      const fine = (keys.run ? 0.12 : 0.5) * dt; if (keys.l || keys.tl) PS.aim -= fine; if (keys.r || keys.tr) PS.aim += fine;
      if (PS.charge >= 0) { PS.charge += dt; PS.power = (1 - Math.cos(PS.charge * Math.PI / 1.1)) / 2; }
      const ax = Math.cos(PS.aim), ay = Math.sin(PS.aim), zb = POOL.z + BR;
      const show = still && !q.in && PS.wait <= 0; cue.visible = show; aimLine.visible = show; ghost.visible = false;
      if (show) {
        const back = 0.035 + (PS.charge >= 0 ? PS.power * 0.24 : Math.sin(performance.now() / 420) * 0.012 + 0.012);
        cue.rotation.order = "YZX"; cue.rotation.set(0, -PS.aim, 0.07); cue.position.set(WX(q.x - ax * back), zb + 0.012, WZ(q.y - ay * back));
        // the guide: where the cue ball goes, the ball it meets first, and where that ball heads
        let tHit = 1e9, hitB = null; for (const b of BALLS) { if (b === q || b.in) continue; const rx = b.x - q.x, ry = b.y - q.y, t = rx * ax + ry * ay, pp = Math.abs(rx * ay - ry * ax); if (t > 0 && pp < BR * 2) { const th = t - Math.sqrt(BR * BR * 4 - pp * pp); if (th < tHit) { tHit = th; hitB = b; } } }
        let tRail = 1e9; if (ax > 1e-6) tRail = Math.min(tRail, (POOL.x2 - BR - q.x) / ax); if (ax < -1e-6) tRail = Math.min(tRail, (POOL.x1 + BR - q.x) / ax); if (ay > 1e-6) tRail = Math.min(tRail, (POOL.y2 - BR - q.y) / ay); if (ay < -1e-6) tRail = Math.min(tRail, (POOL.y1 + BR - q.y) / ay);
        const tEnd = Math.max(0, Math.min(tHit, tRail)), gx = q.x + ax * tEnd, gy = q.y + ay * tEnd, a = aimLine.geometry.attributes.position;
        a.setXYZ(0, WX(q.x), zb, WZ(q.y)); a.setXYZ(1, WX(gx), zb, WZ(gy));
        if (hitB && tHit <= tRail) { const dx = hitB.x - gx, dy = hitB.y - gy, dl = Math.hypot(dx, dy) || 1; a.setXYZ(2, WX(hitB.x), zb, WZ(hitB.y)); a.setXYZ(3, WX(hitB.x + dx / dl * 0.35), zb, WZ(hitB.y + dy / dl * 0.35)); } else { a.setXYZ(2, WX(gx), zb, WZ(gy)); a.setXYZ(3, WX(gx), zb, WZ(gy)); }
        a.needsUpdate = true; ghost.visible = true; ghost.position.set(WX(gx), zb, WZ(gy));
      }
      poolStep(dt);
      // the camera: just behind and above the cue; it lifts to watch the balls roll, then comes back down behind the cue ball
      PS.watch += ((still ? 0 : 1) - PS.watch) * Math.min(1, dt * 3);
      if (PS.side) setCam([19, 15.85, 1.68], [19, 14.2, 0.78], 58);
      else { const bk = 0.62 + PS.tilt * 0.5 + PS.watch * 0.55, up = 0.16 + PS.tilt * 0.85 + PS.watch * 0.5; setCam([q.x - ax * bk, q.y - ay * bk, POOL.z + up], [q.x + ax * (0.55 - PS.tilt * 0.3), q.y + ay * (0.55 - PS.tilt * 0.3), POOL.z], 58); }
      hud("Pool · clear the table", `${PS.shots} shots · ${BALLS.filter((b) => b.n && b.in).length} of 15 down${PS.charge >= 0 ? " · power " + Math.round(PS.power * 100) + "%" : ""}`, PS.msg + " <kbd>V</kbd> side view · <kbd>R</kbd> rack");
    },
    down() { poolLock(); if (!moving(BALLS) && PS.wait <= 0 && !BALLS[0].in) PS.charge = 0; },
    up() { if (PS.charge < 0) return; const q = BALLS[0], v = 0.3 + PS.power * 4.2; q.vx = Math.cos(PS.aim) * v; q.vy = Math.sin(PS.aim) * v; PS.charge = -1; PS.power = 0; PS.shots++; PS.msg = ""; if (sndOn()) { blip(700, now(), 0.04, 0.1, "triangle"); hiss(now(), 0.03, 0.3, "bandpass", 2200, 2); } },
    key(code, dn) { if (code === "KeyR" && dn) { rack(); PS.msg = "Racked again."; } if (code === "KeyV" && dn) PS.side = !PS.side; if (code === "Space") { if (dn && PS.charge < 0) this.down(); if (!dn) this.up(); } },
  };

  // ---------------- AIR HOCKEY (first to 7 against the computer) ----------------
  const AHM = { R: 0.04, PR: 0.05, mid: (AH.y1 + AH.y2) / 2 };
  const puck = mesh(new T.CylinderGeometry(AHM.R, AHM.R, 0.012, 24), 0x3fe0e8, 26.6, 11.75, AH.z + 0.006, { basic: 1, glow: 1 }); G.main.remove(puck); G.fx.add(puck);
  const paddle = (c) => { const g = new T.Group(); const a = new T.Mesh(new T.CylinderGeometry(AHM.PR, AHM.PR, 0.025, 24), lam(c)); a.position.y = 0.0125; g.add(a); const k = new T.Mesh(new T.CylinderGeometry(0.018, 0.022, 0.05, 14), lam(c)); k.position.y = 0.05; g.add(k); G.fx.add(g); return g; };
  const pMe = paddle("#1FA8E6"), pCpu = paddle("#d94f4f");
  const AHS = { p: { x: 26.6, y: 11.75, vx: 0, vy: 0 }, me: { x: 26.6, y: 12.5, vx: 0, vy: 0 }, cpu: { x: 26.6, y: 11.0, vx: 0, vy: 0 }, s: [0, 0], msg: "", over: 0 };
  function ahBoard() { const g = ahC.getContext("2d"), w = ahC.width, h = ahC.height; g.fillStyle = "#0b0c10"; g.fillRect(0, 0, w, h); g.font = `800 92px ${FONT}`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillStyle = "#1FA8E6"; g.fillText(String(AHS.s[0]), w * 0.25, h * 0.56); g.fillStyle = "#d94f4f"; g.fillText(String(AHS.s[1]), w * 0.75, h * 0.56); g.fillStyle = "#ECEEFB"; g.font = `700 30px ${FONT}`; g.fillText("YOU", w * 0.25, 26); g.fillText("CPU", w * 0.75, 26); g.fillText("FIRST TO 7", w / 2, h - 22); ahTex.needsUpdate = true; }
  ahBoard();
  function ahPlace() { puck.position.set(WX(AHS.p.x), AH.z + 0.006, WZ(AHS.p.y)); pMe.position.set(WX(AHS.me.x), AH.z, WZ(AHS.me.y)); pCpu.position.set(WX(AHS.cpu.x), AH.z, WZ(AHS.cpu.y)); }
  ahPlace();
  function ahServe(toMe) { AHS.p = { x: 26.6, y: toMe ? 12.15 : 11.35, vx: 0, vy: 0 }; }
  function ahHit(pd) { const p = AHS.p, dx = p.x - pd.x, dy = p.y - pd.y, d = Math.hypot(dx, dy), m = AHM.R + AHM.PR; if (d < m && d > 1e-6) { const nx = dx / d, ny = dy / d, rv = (p.vx - pd.vx) * nx + (p.vy - pd.vy) * ny; if (rv < 0) { p.vx -= 1.9 * rv * nx; p.vy -= 1.9 * rv * ny; clack(Math.min(1, -rv / 3)); } p.x = pd.x + nx * m; p.y = pd.y + ny * m; const sp = Math.hypot(p.vx, p.vy); if (sp > 5) { p.vx *= 5 / sp; p.vy *= 5 / sp; } } }
  const AIRG = {
    name: "Air hockey", start() { if (AHS.over || (AHS.s[0] === 0 && AHS.s[1] === 0)) { AHS.s = [0, 0]; AHS.over = 0; ahServe(true); } AHS.msg = "Move the mouse to move your blue paddle. First to 7 wins."; ahBoard(); setCam([26.6, 13.45, 1.95], [26.6, 11.75, 0.78], 56); },
    end() {},
    update(dt) {
      const sub = 6, h = dt / sub, p = AHS.p, me = AHS.me, cpu = AHS.cpu, c = onLevel(AH.z);
      const tx = c ? clamp(c[0], AH.x1 + AHM.PR, AH.x2 - AHM.PR) : me.x, ty = c ? clamp(c[1], AHM.mid + AHM.PR, AH.y2 - AHM.PR) : me.y;
      const cpuTx = p.y < AHM.mid + 0.3 ? clamp(p.x + (p.x - 26.6) * 0.15, AH.x1 + AHM.PR, AH.x2 - AHM.PR) : 26.6 + (p.x - 26.6) * 0.5, cpuTy = p.y < AHM.mid + 0.05 && p.vy < 0.6 ? clamp(p.y - 0.07, AH.y1 + AHM.PR, AHM.mid - AHM.PR) : AH.y1 + 0.22;
      for (let s = 0; s < sub; s++) {
        let mx = tx - me.x, my = ty - me.y, md = Math.hypot(mx, my), ms = 6 * h; if (md > ms) { mx *= ms / md; my *= ms / md; } me.vx = mx / h; me.vy = my / h; me.x += mx; me.y += my;
        let cx = cpuTx - cpu.x, cy = cpuTy - cpu.y, cd = Math.hypot(cx, cy), cs = 1.55 * h; if (cd > cs) { cx *= cs / cd; cy *= cs / cd; } cpu.vx = cx / h; cpu.vy = cy / h; cpu.x += cx; cpu.y += cy;
        p.vx *= 1 - 0.18 * h; p.vy *= 1 - 0.18 * h; p.x += p.vx * h; p.y += p.vy * h;
        if (p.x < AH.x1 + AHM.R) { p.x = AH.x1 + AHM.R; p.vx = Math.abs(p.vx) * 0.9; clack(0.3); } if (p.x > AH.x2 - AHM.R) { p.x = AH.x2 - AHM.R; p.vx = -Math.abs(p.vx) * 0.9; clack(0.3); }
        const inGoal = p.x > AH.gx1 + AHM.R * 0.3 && p.x < AH.gx2 - AHM.R * 0.3;
        if (p.y < AH.y1 + AHM.R) { if (inGoal) { AHS.s[0]++; buzzer(); AHS.msg = "Goal! You scored."; ahServe(false); ahBoard(); break; } p.y = AH.y1 + AHM.R; p.vy = Math.abs(p.vy) * 0.9; clack(0.3); }
        if (p.y > AH.y2 - AHM.R) { if (inGoal) { AHS.s[1]++; buzzer(); AHS.msg = "The computer scored."; ahServe(true); ahBoard(); break; } p.y = AH.y2 - AHM.R; p.vy = -Math.abs(p.vy) * 0.9; clack(0.3); }
        ahHit(me); ahHit(cpu);
      }
      if (!AHS.over && (AHS.s[0] >= 7 || AHS.s[1] >= 7)) { AHS.over = 1; const won = AHS.s[0] > AHS.s[1]; HS.air[won ? 0 : 1]++; drawScores(); AHS.msg = won ? "You win! Press E to stand up, or click to play again." : "The computer wins this one. Click to play again."; if (won) ding(); say(won ? "Squawk! Air hockey champion!" : "Squawk! Lost to a computer! Lost to a computer!"); }
      ahPlace(); hud("Air hockey", `You ${AHS.s[0]} · ${AHS.s[1]} Computer`, AHS.msg);
    },
    down() { if (AHS.over) { AHS.s = [0, 0]; AHS.over = 0; ahServe(true); ahBoard(); AHS.msg = "New game. First to 7."; } }, up() {}, key() {},
  };

  // ---------------- FOUR IN A ROW (you are aqua, the computer is yellow) ----------------
  const FR = { g: [], turn: 0, hover: -1, over: 0, win: null, drop: null, think: 0, msg: "", w: [0, 0] };
  function frNew() { FR.g = Array.from({ length: 7 }, () => []); FR.turn = 0; FR.over = 0; FR.win = null; FR.drop = null; FR.msg = "Your go. Point at a column and click."; }
  function frWin(g, p) { const at = (c, r) => (c >= 0 && c < 7 && r >= 0 && r < 6 ? g[c][r] : undefined); for (let c = 0; c < 7; c++) for (let r = 0; r < 6; r++) { if (at(c, r) !== p) continue; for (const [dc, dr] of [[1, 0], [0, 1], [1, 1], [1, -1]]) { const line = [[c, r]]; for (let k = 1; k < 4; k++) { if (at(c + dc * k, r + dr * k) !== p) break; line.push([c + dc * k, r + dr * k]); } if (line.length === 4) return line; } } return null; }
  function frPick() { const free = [0, 1, 2, 3, 4, 5, 6].filter((c) => FR.g[c].length < 6), tryP = (p) => free.find((c) => { FR.g[c].push(p); const w = frWin(FR.g, p); FR.g[c].pop(); return w; });
    let c = tryP(1); if (c === undefined) c = tryP(0);
    if (c === undefined) { const safe = free.filter((k) => { FR.g[k].push(1); let bad = false; if (FR.g[k].length < 6) { FR.g[k].push(0); bad = !!frWin(FR.g, 0); FR.g[k].pop(); } FR.g[k].pop(); return !bad; }), pool = safe.length ? safe : free; pool.sort((m, n) => Math.abs(m - 3) - Math.abs(n - 3) + (Math.random() - 0.5) * 2.2); c = pool[0]; }
    return c; }
  function frDraw() { const g = c4C.getContext("2d"), W = c4C.width, H = c4C.height, u = W / 1.6; g.fillStyle = "#1d4fb8"; g.fillRect(0, 0, W, H);
    const cx = (c) => (0.1 + (c + 0.5) * 0.2) * u, cy = (r) => H - (0.1 + (r + 0.5) * 0.2) * u, col = ["#3FE0E8", "#ffd23f"];
    if (FR.hover >= 0 && !FR.over && FR.turn === 0 && !FR.drop) { g.fillStyle = "rgba(255,255,255,.12)"; g.fillRect(cx(FR.hover) - 0.1 * u, 0, 0.2 * u, H); }
    for (let c = 0; c < 7; c++) for (let r = 0; r < 6; r++) { g.fillStyle = "#0b1c4a"; g.beginPath(); g.arc(cx(c), cy(r), 0.082 * u, 0, 7); g.fill(); const v = FR.g[c][r]; if (v !== undefined && !(FR.drop && FR.drop.c === c && FR.drop.r === r)) { g.fillStyle = col[v]; g.beginPath(); g.arc(cx(c), cy(r), 0.078 * u, 0, 7); g.fill(); g.strokeStyle = "rgba(0,0,0,.25)"; g.lineWidth = 5; g.beginPath(); g.arc(cx(c), cy(r), 0.055 * u, 0, 7); g.stroke(); } }
    if (FR.drop) { g.fillStyle = col[FR.drop.p]; g.beginPath(); g.arc(cx(FR.drop.c), Math.min(cy(FR.drop.r), FR.drop.y), 0.078 * u, 0, 7); g.fill(); }
    if (FR.win) { g.strokeStyle = "#ffffff"; g.lineWidth = 12; g.lineCap = "round"; g.beginPath(); g.moveTo(cx(FR.win[0][0]), cy(FR.win[0][1])); g.lineTo(cx(FR.win[3][0]), cy(FR.win[3][1])); g.stroke(); }
    c4Tex.needsUpdate = true; }
  function frPlay(c, p) { if (FR.g[c].length >= 6) return false; FR.g[c].push(p); FR.drop = { c, r: FR.g[c].length - 1, p, y: 0, v: 0 }; return true; }
  frNew(); frDraw();
  const FOURG = {
    name: "Four in a row", start() { if (FR.over) frNew(); setCam([26.6, 17.0, 1.35], [26.6, 15.06, 1.05], 58); frDraw(); },
    end() { FR.hover = -1; frDraw(); },
    update(dt) {
      const c = onWallY(C4.y); const h = c && c[1] > 0.3 && c[1] < 1.9 ? Math.floor((c[0] - C4.x1) / C4.d) : -1; FR.hover = h >= 0 && h < 7 ? h : -1;
      if (FR.drop) { FR.drop.v += 5200 * dt; FR.drop.y += FR.drop.v * dt; const u = c4C.width / 1.6, ty = c4C.height - (0.1 + (FR.drop.r + 0.5) * 0.2) * u;
        if (FR.drop.y >= ty) { const p = FR.drop.p; FR.drop = null; clack(0.7); const w = frWin(FR.g, p);
          if (w) { FR.win = w; FR.over = 1; FR.w[p]++; HS.four[p]++; drawScores(); FR.msg = p === 0 ? "Four in a row! You win. Click to play again." : "The computer got four. Click to play again."; if (p === 0) ding(); else buzzer(); say(p === 0 ? "Squawk! Four in a row!" : "Squawk! Beaten by a computer!"); }
          else if (FR.g.every((col) => col.length >= 6)) { FR.over = 1; FR.msg = "A draw. The board is full. Click to play again."; }
          else { FR.turn = 1 - p; if (FR.turn === 1) { FR.think = 0.6 + Math.random() * 0.5; FR.msg = "The computer is thinking..."; } else FR.msg = "Your go."; } } }
      else if (!FR.over && FR.turn === 1) { FR.think -= dt; if (FR.think <= 0) frPlay(frPick(), 1); }
      frDraw(); hud("Four in a row", `You ${FR.w[0]} · ${FR.w[1]} Computer`, FR.msg + " Keys <kbd>1</kbd> to <kbd>7</kbd> work too.");
    },
    down() { if (FR.over) { frNew(); return; } if (FR.turn !== 0 || FR.drop || FR.hover < 0) return; if (!frPlay(FR.hover, 0)) FR.msg = "That column is full."; },
    up() {}, key(code, dn) { if (!dn) return; const m = /^(Digit|Numpad)([1-7])$/.exec(code); if (m && !FR.over && FR.turn === 0 && !FR.drop) { if (!frPlay(+m[2] - 1, 0)) FR.msg = "That column is full."; } },
  };

  // ---------------- DARTS (301, three darts a turn) ----------------
  const DORDER = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
  const dbTex = canvasTex(1024, 1024, (g, w) => { const c = w / 2, s = w / 2 / 0.225; g.fillStyle = "#16181d"; g.beginPath(); g.arc(c, c, c, 0, 7); g.fill();
    const ring = (r1, r2, cols) => { for (let i = 0; i < 20; i++) { const a0 = (-90 - 9 + i * 18) * Math.PI / 180, a1 = a0 + 18 * Math.PI / 180; g.fillStyle = cols[i % 2]; g.beginPath(); g.arc(c, c, r2 * s, a0, a1); g.arc(c, c, r1 * s, a1, a0, true); g.closePath(); g.fill(); } };
    ring(0.016, 0.099, ["#16181d", "#f1e6c8"]); ring(0.099, 0.107, ["#c8202c", "#2f8a4a"]); ring(0.107, 0.162, ["#16181d", "#f1e6c8"]); ring(0.162, 0.17, ["#c8202c", "#2f8a4a"]);
    g.fillStyle = "#2f8a4a"; g.beginPath(); g.arc(c, c, 0.016 * s, 0, 7); g.fill(); g.fillStyle = "#c8202c"; g.beginPath(); g.arc(c, c, 0.0064 * s, 0, 7); g.fill();
    g.strokeStyle = "#c9ced8"; g.lineWidth = 2; for (let i = 0; i < 20; i++) { const a = (-90 - 9 + i * 18) * Math.PI / 180; g.beginPath(); g.moveTo(c + Math.cos(a) * 0.016 * s, c + Math.sin(a) * 0.016 * s); g.lineTo(c + Math.cos(a) * 0.17 * s, c + Math.sin(a) * 0.17 * s); g.stroke(); }
    g.fillStyle = "#ECEEFB"; g.font = `700 54px ${FONT}`; g.textAlign = "center"; g.textBaseline = "middle"; DORDER.forEach((n, i) => { const a = (-90 + i * 18) * Math.PI / 180; g.fillText(String(n), c + Math.cos(a) * 0.197 * s, c + Math.sin(a) * 0.197 * s); }); });
  { const m = new T.Mesh(new T.CylinderGeometry(DART.r, DART.r, 0.05, 48), new T.MeshStandardMaterial({ color: 0x16181d })); m.rotation.x = Math.PI / 2; m.position.set(WX(DART.x), DART.z, WZ(0.245)); G.main.add(m); plate(dbTex, DART.x, 0.2715, DART.z, 0, DART.r * 2, DART.r * 2, { alpha: 1, tone: 1 }); }
  const dartC = document.createElement("canvas"); dartC.width = 512; dartC.height = 460; const dartTex = new T.CanvasTexture(dartC); dartTex.encoding = T.sRGBEncoding; plate(dartTex, 4.5, 0.192, 1.65, 0, 0.98, 0.88);
  const wallC = document.createElement("canvas"); wallC.width = 256; wallC.height = 512; const wallTex = new T.CanvasTexture(wallC); wallTex.encoding = T.sRGBEncoding; plate(wallTex, 2.342, 0.57, 1.72, FACE.e, 0.66, 1.3);
  bx(2.34, 0.25, 2.341, 0.89, 1.08, 2.37, "#2c3a34", NS);
  const DS = { score: 301, start: 301, thrown: 0, total: 0, wall: 112, msg: "Point at the board and click to throw. Get from 301 to exactly 0.", list: [], darts: [], wait: 0, t: 0, hist: [] };
  function chalk() { const g = wallC.getContext("2d"), w = wallC.width, h = wallC.height; g.fillStyle = "#2c3a34"; g.fillRect(0, 0, w, h); g.fillStyle = "rgba(240,240,230,.9)"; g.font = `700 30px ${FONT}`; g.textAlign = "center"; ["DARTS THAT", "HIT THE", "WALL"].forEach((t, i) => g.fillText(t, w / 2, 70 + i * 36)); g.font = `800 96px ${FONT}`; g.fillText(String(DS.wall), w / 2, 290); g.font = `600 24px ${FONT}`; g.fillText("(please stop)", w / 2, 340); for (let k = 0; k < 9; k++) { g.fillRect(40 + k * 20, 400, 4, 50); } g.save(); g.translate(40, 425); g.rotate(-0.4); g.fillRect(-6, 0, 190, 4); g.restore(); wallTex.needsUpdate = true;
    const d = dartC.getContext("2d"), W = dartC.width, H = dartC.height; d.fillStyle = "#2c3a34"; d.fillRect(0, 0, W, H); d.fillStyle = "rgba(240,240,230,.92)"; d.textAlign = "center"; d.font = `700 40px ${FONT}`; d.fillText("301", W / 2, 54); d.font = `800 120px ${FONT}`; d.fillText(String(DS.score), W / 2, 190); d.font = `600 30px ${FONT}`; d.fillText(`Turn ${Math.floor(DS.total / 3) + 1} · dart ${DS.thrown + 1} of 3`, W / 2, 250); d.textAlign = "left"; DS.hist.slice(-4).forEach((t, i) => d.fillText(t, 40, 310 + i * 38)); dartTex.needsUpdate = true; }
  chalk();
  const dartMesh = () => { const g = new T.Group(); const b = new T.Mesh(new T.CylinderGeometry(0.004, 0.006, 0.09, 8), lam("#c9ced8")); b.rotation.x = Math.PI / 2; g.add(b); const f = new T.Mesh(new T.ConeGeometry(0.02, 0.04, 4), lam("#1FA8E6")); f.rotation.x = Math.PI / 2; f.position.z = 0.06; g.add(f); g.visible = false; G.fx.add(g); return g; };
  for (let k = 0; k < 3; k++) DS.darts.push({ g: dartMesh(), t: -1 });
  const cross = new T.Mesh(new T.RingGeometry(0.008, 0.013, 24), new T.MeshBasicMaterial({ color: 0x3fe0e8, toneMapped: false, depthTest: false, transparent: true })); cross.renderOrder = 999; cross.visible = false; G.fx.add(cross);
  function dartScore(x, z) { const dx = x - DART.x, dz = z - DART.z, r = Math.hypot(dx, dz); if (r > DART.r) return { pts: 0, wall: true, t: "Missed the board" }; if (r > 0.17) return { pts: 0, t: "Off the scoring ring" };
    if (r < 0.0064) return { pts: 50, t: "BULLSEYE! 50" }; if (r < 0.016) return { pts: 25, t: "Outer bull, 25" }; const deg = (Math.atan2(dz, dx) * 180 / Math.PI + 360) % 360, idx = Math.floor((((90 - deg) + 9) % 360 + 360) % 360 / 18), n = DORDER[idx];
    if (r > 0.099 && r < 0.107) return { pts: n * 3, t: `Treble ${n}, ${n * 3}` }; if (r > 0.162) return { pts: n * 2, t: `Double ${n}, ${n * 2}` }; return { pts: n, t: String(n) }; }
  const DARTG = {
    name: "Darts", start() { setCam([3.0, 2.62, 1.6], [3.0, 0.27, 1.73], 24); cross.visible = true; DS.msg = DS.score === 0 ? "You checked out! Click to start a new game." : "Point at the board and click to throw. Get from 301 to exactly 0."; },
    end() { cross.visible = false; },
    update(dt) { DS.t += dt; const c = onWallY(0.27); const wob = 0.022; const ax = c ? c[0] : DART.x, az = c ? c[1] : DART.z; DS.aim = [ax + wob * (Math.sin(DS.t * 2.1) + 0.5 * Math.sin(DS.t * 5.3)), az + wob * (Math.cos(DS.t * 1.7) + 0.5 * Math.sin(DS.t * 4.1))];
      cross.position.set(WX(DS.aim[0]), DS.aim[1], WZ(0.275)); cross.rotation.set(0, 0, 0);
      DS.darts.forEach((d) => { if (d.t < 0 || d.t >= 1) return; d.t = Math.min(1, d.t + dt / 0.22); const u = d.t; d.g.position.set(WX(d.sx + (d.tx - d.sx) * u), d.sz + (d.tz - d.sz) * u + Math.sin(u * Math.PI) * 0.12, WZ(d.sy + (d.ty - d.sy) * u)); d.g.rotation.set(0.15 - u * 0.25, 0, 0);
        if (d.t >= 1) { const r = dartScore(d.tx, d.tz); if (r.wall) { DS.wall++; d.g.position.set(WX(d.tx), d.tz, WZ(0.235)); } if (sndOn()) { blip(r.wall ? 140 : 220, now(), 0.06, 0.12, "triangle"); hiss(now(), 0.04, 0.3, "lowpass", 800, 1); } if (r.pts === 50 && sndOn()) ding();
          const left = DS.score - r.pts; let line = r.t; if (left < 0 || left === 1) { line += " · Bust! Back to " + DS.start; DS.score = DS.start; DS.thrown = 2; } else { DS.score = left; if (left === 0) { line += " · CHECKOUT!"; const n = DS.total + 1; if (!HS.darts || n < HS.darts) { HS.darts = n; drawScores(); } ding(); say("Squawk! Darts wizard!"); } }
          if (r.wall) say("Squawk! Hit the wall! Hit the wall!"); DS.hist.push(line); DS.msg = line; DS.thrown++; DS.total++; if (DS.thrown >= 3 || DS.score === 0) { DS.wait = 1.4; DS.thrown = 0; DS.start = DS.score; } chalk(); } });
      if (DS.wait > 0) { DS.wait -= dt; if (DS.wait <= 0) DS.darts.forEach((d) => { d.g.visible = false; d.t = -1; }); }
      hud("Darts · 301", `${DS.score} left · ${DS.total} darts`, DS.msg); },
    down() { if (DS.wait > 0) return; if (DS.score === 0) { DS.score = DS.start = 301; DS.total = 0; DS.hist = []; DS.msg = "New game of 301."; chalk(); return; } const d = DS.darts.find((q) => q.t < 0); if (!d || !DS.aim) return; d.t = 0; d.g.visible = true; d.sx = 3.0 + 0.15; d.sy = 2.3; d.sz = 1.45; d.tx = DS.aim[0]; d.ty = 0.29; d.tz = DS.aim[1]; if (sndOn()) hiss(now(), 0.12, 0.1, "highpass", 2500, 1); },
    up() {}, key(code, dn) { if (code === "Space" && dn) this.down(); },
  };

  // ---------------- SHUFFLEBOARD (4 pucks a round, slide them to the far end) ----------------
  const SR = 0.03, SPK = [];
  for (let k = 0; k < 4; k++) { const g = new T.Group(); const a = new T.Mesh(new T.CylinderGeometry(SR, SR, 0.02, 24), lam(k % 2 ? "#d94f4f" : "#1d6fb8")); a.position.y = 0.01; g.add(a); const t = new T.Mesh(new T.CylinderGeometry(SR * 0.7, SR * 0.7, 0.003, 20), lam("#aab1ba")); t.position.y = 0.0215; g.add(t); g.visible = false; G.fx.add(g); SPK.push({ g, x: 0, y: 0, vx: 0, vy: 0, out: false, used: false }); }
  const SS = { round: 1, n: 0, charge: -1, power: 0, aimY: 18.55, msg: "", wait: 0, camX: 5.6, slideG: null, last: 0 };
  function sbPlace(p) { p.g.position.set(WX(p.x), SB.z, WZ(p.y)); }
  function sbReset() { SPK.forEach((p) => { p.used = false; p.out = false; p.g.visible = false; p.vx = p.vy = 0; }); SS.n = 0; }
  function sbPoints(p) { if (p.out || !p.used) return 0; if (p.x < 8.1) return 0; if (p.x > SB.x2) return 4; if (p.x >= SB.x2 - 0.15) return 3; if (p.x >= SB.x2 - 0.6) return 2; if (p.x >= SB.x2 - 1.8) return 1; return 0; }
  const SHUFG = {
    name: "Shuffleboard", start() { SS.msg = "Move the mouse to line up, hold the button for power, let go to slide. Hanging over the end scores 4."; SS.camX = 5.6; },
    end() {},
    update(dt) {
      const c = onLevel(SB.z); if (c && SS.n < 4 && SS.charge < 0 || (c && SS.charge >= 0)) SS.aimY = clamp(c[1], SB.y1 + SR, SB.y2 - SR);
      if (SS.charge >= 0) { SS.charge += dt; SS.power = (1 - Math.cos(SS.charge * Math.PI / 1.2)) / 2; }
      let lead = null, anyMove = false;
      SPK.forEach((p) => { if (!p.used || p.out) return; const sp = Math.hypot(p.vx, p.vy); if (sp > 0.003) { anyMove = true; const ns = Math.max(0, sp - 0.6 * dt); p.vx *= ns / sp; p.vy *= ns / sp; p.x += p.vx * dt; p.y += p.vy * dt; if (!lead || p.x > lead.x) lead = p; }
        if (p.y < SB.y1 + SR) { p.y = SB.y1 + SR; p.vy = Math.abs(p.vy) * 0.5; } if (p.y > SB.y2 - SR) { p.y = SB.y2 - SR; p.vy = -Math.abs(p.vy) * 0.5; }
        if (p.x > SB.x2 + SR * 0.4) { p.out = true; p.vx = p.vy = 0; p.g.position.y = 0.62; p.g.position.x = WX(12.85); thud(0.6); if (sndOn()) blip(90, now(), 0.2, 0.1, "sine", 60); SS.msg = "Into the gutter. It went for a swim."; } else sbPlace(p); });
      for (let i = 0; i < 4; i++) for (let j = i + 1; j < 4; j++) { const a = SPK[i], b = SPK[j]; if (!a.used || !b.used || a.out || b.out) continue; const dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy); if (d < SR * 2 && d > 1e-6) { const nx = dx / d, ny = dy / d, rv = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny; if (rv > 0) { const k = rv * 0.95; a.vx -= k * nx; a.vy -= k * ny; b.vx += k * nx; b.vy += k * ny; clack(Math.min(1, rv)); } const o = (SR * 2 - d) / 2; a.x -= nx * o; a.y -= ny * o; b.x += nx * o; b.y += ny * o; } }
      // the puck sliding makes a soft hiss
      if (sndOn()) { if (!SS.slideG) { const a = SND.ctx, s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain(); s.buffer = SND.noise; s.loop = true; f.type = "bandpass"; f.frequency.value = 2400; f.Q.value = 0.8; g.gain.value = 0; s.connect(f); f.connect(g); g.connect(SND.out); s.start(); SS.slideG = g; } SS.slideG.gain.setTargetAtTime(anyMove ? 0.05 : 0, now(), 0.05); }
      const goal = lead ? clamp(lead.x - 1.4, 5.6, 11.4) : (SS.wait > 0 || (SS.n === 4 && !anyMove) ? 11.0 : 5.6); SS.camX += (goal - SS.camX) * Math.min(1, dt * 2.5);
      setCam([SS.camX, 18.55, 1.45 + (SS.camX - 5.6) * 0.05], [SS.camX + 3.2, 18.55, 0.8], 55);
      if (!anyMove && SS.n === 4 && SS.wait <= 0 && !SS.scored) { const pts = SPK.reduce((s, p) => s + sbPoints(p), 0); SS.scored = true; SS.msg = `Round ${SS.round}: ${pts} points.` + (SPK.some((p) => sbPoints(p) === 4) ? " A hanger! Lovely." : pts === 0 ? " Tough table tonight." : ""); if (pts > HS.shuffle) { HS.shuffle = pts; drawScores(); } if (pts >= 8) { ding(); say("Squawk! Shuffle master!"); } SS.wait = 3; }
      if (SS.wait > 0) { SS.wait -= dt; if (SS.wait <= 0) { sbReset(); SS.round++; SS.scored = false; SS.msg = "New round. Four pucks."; } }
      // the next puck waits at your end
      const nx = SPK[SS.n]; if (SS.n < 4 && nx && !anyMove && SS.wait <= 0) { nx.g.visible = true; nx.x = SB.x1 + 0.12 - (SS.charge >= 0 ? SS.power * 0.06 : 0); nx.y = SS.aimY; sbPlace(nx); }
      hud("Shuffleboard", `Round ${SS.round} · puck ${Math.min(4, SS.n + 1)} of 4${SS.charge >= 0 ? " · power " + Math.round(SS.power * 100) + "%" : ""}`, SS.msg || "Hold the button for power, let go to slide.");
    },
    down() { if (SS.n < 4 && SS.wait <= 0 && !SPK.some((p) => p.used && !p.out && Math.hypot(p.vx, p.vy) > 0.003)) SS.charge = 0; },
    up() { if (SS.charge < 0) return; const p = SPK[SS.n]; p.used = true; p.vx = 1.4 + SS.power * 2.4; p.vy = (Math.random() - 0.5) * 0.04; SS.charge = -1; SS.power = 0; SS.n++; SS.msg = ""; if (sndOn()) hiss(now(), 0.06, 0.2, "bandpass", 1800, 1); },
    key(code, dn) { if (code === "Space") { if (dn && SS.charge < 0) this.down(); if (!dn) this.up(); } },
  };

  // ---------------- THE JUKEBOX (four songs made right here in the page) ----------------
  const SONGS = [
    { name: "Low Tide Lullaby", bpm: 74, root: 57, minor: 0, prog: [[0, 4, 7], [9, 12, 16], [5, 9, 12], [7, 11, 14]], mel: "5-3-2-1-3---5-6-5-3-2-1-----", bass: "x.......x.......", kick: "x.......x.......", snare: "........x.......", hat: "..x...x...x...x." },
    { name: "Bass Boat Boogie", bpm: 116, root: 52, minor: 0, swing: 0.18, prog: [[0, 4, 7, 10], [0, 4, 7, 10], [5, 9, 12, 15], [0, 4, 7, 10], [7, 11, 14, 17], [5, 9, 12, 15], [0, 4, 7, 10], [7, 11, 14, 17]], mel: "1-3-5-6-8-6-5-3-", walk: [0, 4, 7, 9, 10, 9, 7, 4], kick: "x...x...x...x...", snare: "....x.......x...", hat: "x.x.x.x.x.x.x.x." },
    { name: "Seagull Shuffle", bpm: 100, root: 55, minor: 0, prog: [[9, 12, 16], [5, 9, 12], [0, 4, 7], [7, 11, 14]], mel: "3-5-6-5-3---2-3-5-3-2-1-----", bass: "x..x..x.x..x..x.", kick: "x.....x.x.......", snare: "....x.......x...", hat: "x.xxx.xxx.xxx.xx" },
    { name: "Lighthouse Disco", bpm: 122, root: 50, minor: 1, prog: [[0, 3, 7], [8, 12, 15], [3, 7, 10], [10, 14, 17]], mel: "5-5-4-3-----3-4-5-7-5-4-3---", bass: "x.x.x.x.x.x.x.x.", oct: 1, kick: "x...x...x...x...", snare: "....x.......x...", hat: "..x...x...x...x." },
  ];
  const MUS = { i: -1, step: 0, next: 0, gain: null, beat: 0 };
  const SCALE = [[0, 2, 4, 5, 7, 9, 11], [0, 2, 3, 5, 7, 8, 10]];
  const mf = (m) => 440 * Math.pow(2, (m - 69) / 12);
  function mnote(f, t, d, v, type, out) { const a = SND.ctx, o = a.createOscillator(), g = a.createGain(); o.type = type; o.frequency.value = f; g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(v, t + 0.015); g.gain.exponentialRampToValueAtTime(0.0001, t + d); o.connect(g); g.connect(out); o.start(t); o.stop(t + d + 0.05); }
  function mdrum(kind, t, out) { const a = SND.ctx; if (kind === "k") { const o = a.createOscillator(), g = a.createGain(); o.frequency.setValueAtTime(130, t); o.frequency.exponentialRampToValueAtTime(42, t + 0.14); g.gain.setValueAtTime(0.5, t); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.2); o.connect(g); g.connect(out); o.start(t); o.stop(t + 0.25); } else { const s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain(); s.buffer = SND.noise; f.type = kind === "s" ? "bandpass" : "highpass"; f.frequency.value = kind === "s" ? 1800 : 7000; g.gain.setValueAtTime(kind === "s" ? 0.28 : 0.07, t); g.gain.exponentialRampToValueAtTime(0.0001, t + (kind === "s" ? 0.16 : 0.04)); s.connect(f); f.connect(g); g.connect(out); s.start(t, Math.random()); s.stop(t + 0.2); } }
  function musicFrame() {
    if (MUS.i < 0 || !sndOn()) return; const a = SND.ctx, S = SONGS[MUS.i];
    if (!MUS.gain) { MUS.gain = a.createGain(); MUS.gain.gain.value = 0; MUS.gain.connect(SND.out); }
    const lx = mode === "bird" ? 18 : P.x, ly = mode === "bird" ? 10 : P.y, d = Math.hypot(lx - 27.2, ly - 1.0), outside = ly > 20.1 ? 0.35 : 1, up = P.f > 2 ? 0.7 : 1;
    MUS.gain.gain.setTargetAtTime(0.32 * outside * up / (1 + d * d * 0.004), a.currentTime, 0.2);
    const sp = 60 / S.bpm / 4; if (MUS.next < a.currentTime) MUS.next = a.currentTime + 0.05;
    while (MUS.next < a.currentTime + 0.12) {
      const s = MUS.step, bar = Math.floor(s / 16), st = s % 16, ch = S.prog[bar % S.prog.length], t = MUS.next + (S.swing && st % 2 ? sp * S.swing : 0), out = MUS.gain, rt = S.root;
      if (st === 0) ch.forEach((n) => mnote(mf(rt + 12 + n), t, sp * 15, 0.035, "triangle", out));
      if (S.walk) { if (st % 2 === 0) mnote(mf(rt - 12 + ch[0] + S.walk[(st / 2) % 8]), t, sp * 1.8, 0.16, "sawtooth", out); }
      else if (S.bass[st] === "x") mnote(mf(rt - 12 + ch[0] + (S.oct && st % 4 === 2 ? 12 : 0)), t, sp * 1.6, 0.17, "sawtooth", out);
      if (st % 2 === 0) { const c = S.mel[(Math.floor(s / 2)) % S.mel.length]; if (c >= "1" && c <= "9") { const deg = +c - 1, sc = SCALE[S.minor]; mnote(mf(rt + 24 + sc[deg % 7] + 12 * Math.floor(deg / 7)), t, sp * 3, 0.06, "square", out); } }
      if (S.kick[st] === "x") mdrum("k", t, out); if (S.snare[st] === "x") mdrum("s", t, out); if (S.hat[st] === "x") mdrum("h", t, out);
      if (st % 4 === 0) MUS.beat = t;
      MUS.step++; MUS.next += sp;
    }
  }
  function jukeScreen() { const g = jukeC.getContext("2d"), w = jukeC.width, h = jukeC.height; const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, "#3a1f6f"); gr.addColorStop(1, "#12152a"); g.fillStyle = gr; g.fillRect(0, 0, w, h); g.fillStyle = "#ffd23f"; g.font = `800 34px ${FONT}`; g.textAlign = "center"; g.fillText("HARBOR JUKEBOX", w / 2, 50); g.font = `600 24px ${FONT}`; g.fillStyle = "#ECEEFB"; g.fillText(MUS.i < 0 ? "Press E to play a song" : "NOW PLAYING", w / 2, 92);
    SONGS.forEach((s, i) => { g.fillStyle = i === MUS.i ? "#3FE0E8" : "rgba(236,238,251,.7)"; g.font = `${i === MUS.i ? 800 : 600} 28px ${FONT}`; g.fillText((i + 1) + ". " + s.name, w / 2, 160 + i * 62); });
    g.fillStyle = "rgba(236,238,251,.55)"; g.font = `600 20px ${FONT}`; g.fillText("In the real Lobby: songs from", w / 2, h - 66); g.fillText("your Glowfin Music library", w / 2, h - 38); jukeTex.needsUpdate = true; }
  jukeScreen();
  function jukeNext() { MUS.i = MUS.i + 1 >= SONGS.length ? -1 : MUS.i + 1; MUS.step = 0; MUS.next = 0; jukeScreen(); if (MUS.i === 2) say("Squawk! This is my song!"); return MUS.i < 0 ? "Jukebox off." : "Now playing: " + SONGS[MUS.i].name; }

  // ---------------- THE BIG SCREEN: movie quiz, or watch a short ----------------
  const QUIZ = [
    ["A clownfish dad crosses the ocean to find his son. Which movie?", ["Finding Nemo", "Shark Tale", "Moana", "Luca"], 0],
    ["Which movie has the line \"You're gonna need a bigger boat\"?", ["Titanic", "Jaws", "Waterworld", "Free Willy"], 1],
    ["In Moana, what is the name of the shape-shifting demigod?", ["Tamatoa", "Hei Hei", "Maui", "Te Ka"], 2],
    ["Which Pixar movie is about a rat who wants to be a chef?", ["Up", "Cars", "Brave", "Ratatouille"], 3],
    ["In Toy Story, what kind of toy is Rex?", ["A dinosaur", "A cowboy", "A spaceman", "A piggy bank"], 0],
    ["Which ship sinks in the 1997 movie with Leonardo DiCaprio?", ["Poseidon", "Titanic", "Black Pearl", "Nautilus"], 1],
    ["Captain Jack Sparrow sails in which movie series?", ["Hook", "Treasure Planet", "Pirates of the Caribbean", "Peter Pan"], 2],
    ["What colour is Shrek?", ["Blue", "Orange", "Purple", "Green"], 3],
  ];
  const SCRS = { mode: "idle", q: 0, pick: -1, t: 0, score: 0, friends: [0, 0, 0], reveal: 0, movieT: 0 };
  const FCOL = ["#7AA7FF", "#A55BE0", "#5BD18B"], FNAME = ["Blue", "Violet", "Green"];
  function drawScreen(dt) {
    const g = SCR.getContext("2d"), w = SCR.width, h = SCR.height; SCRS.t += dt;
    const sea = (glow) => { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, "#0b0d1a"); gr.addColorStop(1, glow || "#1f2750"); g.fillStyle = gr; g.fillRect(0, 0, w, h); g.strokeStyle = "rgba(63,224,232,.25)"; g.lineWidth = 3; for (let k = 0; k < 4; k++) { g.beginPath(); for (let x = 0; x <= w; x += 20) g.lineTo(x, h - 120 + k * 26 + Math.sin(x * 0.012 + SCRS.t * (1 + k * 0.3) + k) * 10); g.stroke(); } };
    const btn = (x, y, bw, bh, t, on, col) => { g.fillStyle = on ? (col || "#3FE0E8") : "rgba(26,30,56,.92)"; g.beginPath(); g.roundRect ? g.roundRect(x, y, bw, bh, 18) : g.rect(x, y, bw, bh); g.fill(); g.strokeStyle = "rgba(63,224,232,.5)"; g.lineWidth = 2; g.stroke(); g.fillStyle = on ? "#0B0D1A" : "#ECEEFB"; g.font = `700 34px ${FONT}`; g.textAlign = "left"; g.textBaseline = "middle"; g.fillText(t, x + 28, y + bh / 2); };
    g.textBaseline = "alphabetic";
    if (SCRS.mode === "idle" || SCRS.mode === "menu") { sea(); g.textAlign = "center"; g.fillStyle = "#ECEEFB"; g.font = `800 92px ${FONT}`; g.fillText("Glowfin", w / 2, 220); g.font = `600 38px ${FONT}`; g.fillStyle = "#A3A9CC"; g.fillText("Harbor Lounge · big screen", w / 2, 280);
      btn(w / 2 - 360, 340, 720, 84, "1   Movie quiz · everyone on the sofa plays", SCRS.mode === "menu" && SCRS.pick === 0); btn(w / 2 - 360, 440, 720, 84, "2   Watch a short: The Lighthouse Cat", SCRS.mode === "menu" && SCRS.pick === 1);
      g.textAlign = "center"; g.font = `600 28px ${FONT}`; g.fillStyle = "#7A80A8"; g.fillText(SCRS.mode === "menu" ? "Press 1 or 2, or click a button" : "Sit on the sofa and press E to pick", w / 2, 580); }
    else if (SCRS.mode === "quiz") { sea("#2a1f55"); const Q = QUIZ[SCRS.q]; g.textAlign = "left"; g.fillStyle = "#3FE0E8"; g.font = `700 30px ${FONT}`; g.fillText(`Question ${SCRS.q + 1} of ${QUIZ.length}`, 80, 80); g.fillStyle = "#ECEEFB"; g.font = `800 44px ${FONT}`;
      const words = Q[0].split(" "); let line = "", ly = 150; words.forEach((wd) => { if (g.measureText(line + wd).width > w - 160) { g.fillText(line, 80, ly); line = ""; ly += 56; } line += wd + " "; }); g.fillText(line, 80, ly);
      Q[1].forEach((a, i) => { const x = 80 + (i % 2) * 570, y = 300 + Math.floor(i / 2) * 110; const right = SCRS.reveal > 0 && i === Q[2], wrong = SCRS.reveal > 0 && i === SCRS.pick && i !== Q[2]; btn(x, y, 540, 90, `${i + 1}  ${a}`, right || wrong || (SCRS.reveal <= 0 && i === SCRS.pick), right ? "#5BD18B" : wrong ? "#FF5C8A" : "#3FE0E8"); });
      if (SCRS.reveal <= 0) { const left = Math.max(0, 1 - SCRS.qt / 15); g.fillStyle = "rgba(63,224,232,.8)"; g.fillRect(80, 545, (w - 160) * left, 10); }
      g.textAlign = "left"; g.font = `700 30px ${FONT}`; g.fillStyle = "#3FE0E8"; g.fillText(`You ${SCRS.score}`, 80, 620); FNAME.forEach((n, i) => { g.fillStyle = FCOL[i]; g.fillText(`${n} ${SCRS.friends[i]}`, 300 + i * 200, 620); }); }
    else if (SCRS.mode === "done") { sea("#2a1f55"); g.textAlign = "center"; g.fillStyle = "#ECEEFB"; g.font = `800 70px ${FONT}`; g.fillText("Final scores", w / 2, 170); const all = [["You", SCRS.score, "#3FE0E8"], ...FNAME.map((n, i) => [n, SCRS.friends[i], FCOL[i]])].sort((a, b) => b[1] - a[1]); all.forEach(([n, s, c], i) => { g.fillStyle = c; g.font = `700 46px ${FONT}`; g.fillText(`${i + 1}.  ${n}  ${s}`, w / 2, 270 + i * 70); }); g.fillStyle = "#7A80A8"; g.font = `600 28px ${FONT}`; g.fillText("Click or press 1 to play again · 2 for the short", w / 2, 620); }
    else if (SCRS.mode === "movie") { const t = SCRS.movieT += dt, T2 = t % 44; const sky = g.createLinearGradient(0, 0, 0, h); sky.addColorStop(0, "#070b1c"); sky.addColorStop(0.65, "#1f2f66"); sky.addColorStop(0.66, "#0b2440"); sky.addColorStop(1, "#06121f"); g.fillStyle = sky; g.fillRect(0, 0, w, h);
      g.fillStyle = "#fff"; for (let k = 0; k < 60; k++) { const sx = (k * 197) % w, sy = (k * 89) % (h * 0.55); g.globalAlpha = 0.3 + 0.7 * Math.abs(Math.sin(t * 0.7 + k)); g.fillRect(sx, sy, 2, 2); } g.globalAlpha = 1;
      g.fillStyle = "#f4f1e0"; g.beginPath(); g.arc(w * 0.8, 120, 46, 0, 7); g.fill();
      const lx = w * 0.28; g.fillStyle = "#2a2a33"; g.beginPath(); g.moveTo(lx - 150, h * 0.66); g.lineTo(lx + 150, h * 0.66); g.lineTo(lx + 70, h * 0.58); g.lineTo(lx - 80, h * 0.6); g.fill(); for (let k = 0; k < 5; k++) { g.fillStyle = k % 2 ? "#c8202c" : "#eef1f3"; g.fillRect(lx - 34 + k * 3, h * 0.58 - (k + 1) * 46, 68 - k * 6, 46); } g.fillStyle = "#fff2c0"; g.beginPath(); g.arc(lx, h * 0.58 - 250, 18, 0, 7); g.fill();
      const ba = t * 0.9; g.fillStyle = "rgba(255,242,192,.12)"; g.beginPath(); g.moveTo(lx, h * 0.58 - 250); g.lineTo(lx + Math.cos(ba) * 900, h * 0.58 - 250 + Math.sin(ba) * 140 - 60); g.lineTo(lx + Math.cos(ba) * 900, h * 0.58 - 250 + Math.sin(ba) * 140 + 60); g.fill();
      // the cat on the lighthouse balcony
      const cx = lx + 26, cy = h * 0.58 - 232, sleepy = T2 > 22 && T2 < 34; g.fillStyle = "#0b0c10"; g.beginPath(); g.ellipse(cx, cy, 16, 11, 0, 0, 7); g.fill(); g.beginPath(); g.arc(cx + 13, cy - 10 + (sleepy ? 6 : 0), 8, 0, 7); g.fill(); g.beginPath(); g.moveTo(cx + 8, cy - 16 + (sleepy ? 6 : 0)); g.lineTo(cx + 11, cy - 24 + (sleepy ? 6 : 0)); g.lineTo(cx + 14, cy - 16 + (sleepy ? 6 : 0)); g.fill(); g.beginPath(); g.moveTo(cx + 14, cy - 16 + (sleepy ? 6 : 0)); g.lineTo(cx + 18, cy - 24 + (sleepy ? 6 : 0)); g.lineTo(cx + 20, cy - 14 + (sleepy ? 6 : 0)); g.fill(); g.strokeStyle = "#0b0c10"; g.lineWidth = 4; g.beginPath(); g.moveTo(cx - 14, cy); g.quadraticCurveTo(cx - 30, cy + 20 + Math.sin(t * 3) * 6, cx - 22, cy + 34); g.stroke();
      // a little boat crosses, then a whale says hello
      const bxp = ((T2 - 4) / 18) * (w + 300) - 150; if (T2 > 4 && T2 < 22) { g.fillStyle = "#eef1f3"; g.beginPath(); g.moveTo(bxp - 60, h * 0.69); g.lineTo(bxp + 60, h * 0.69); g.lineTo(bxp + 40, h * 0.72); g.lineTo(bxp - 45, h * 0.72); g.fill(); g.fillStyle = "#ffd9a0"; g.fillRect(bxp - 18, h * 0.66, 30, 14); }
      if (T2 > 34 && T2 < 40) { const u = (T2 - 34) / 6, wy = h * 0.72 - Math.sin(u * Math.PI) * 120; g.fillStyle = "#2b5f8f"; g.beginPath(); g.ellipse(w * 0.62, wy, 90, 36, -0.3 + u * 0.6, 0, 7); g.fill(); g.fillStyle = "rgba(255,255,255,.7)"; for (let k = 0; k < 9; k++) g.fillRect(w * 0.62 - 60 + k * 14, wy - 40 - Math.sin(u * Math.PI) * 30 - (k % 3) * 10, 4, 4); }
      g.fillStyle = "rgba(63,224,232,.3)"; g.lineWidth = 3; g.strokeStyle = "rgba(63,224,232,.3)"; for (let k = 0; k < 3; k++) { g.beginPath(); for (let x = 0; x <= w; x += 20) g.lineTo(x, h * 0.7 + k * 30 + Math.sin(x * 0.01 + t * (1 + k * 0.2)) * 6); g.stroke(); }
      const sub = T2 < 4 ? ["THE LIGHTHOUSE CAT", "A Glowfin Lounge short"] : T2 < 12 ? ["Every night, the cat keeps watch."] : T2 < 22 ? ["A boat goes by. The cat approves of the boat."] : T2 < 34 ? ["The cat has a little nap. Nobody tell the boats."] : T2 < 40 ? ["A whale says hello. The cat pretends not to notice."] : ["Directed by Gerald. Gerald is a pufferfish."];
      g.textAlign = "center"; g.fillStyle = "#ECEEFB"; g.font = `${sub.length > 1 ? 800 : 600} ${sub.length > 1 ? 78 : 38}px ${FONT}`; g.fillText(sub[0], w / 2, sub.length > 1 ? h * 0.42 : h - 50); if (sub[1]) { g.font = `600 32px ${FONT}`; g.fillText(sub[1], w / 2, h * 0.42 + 56); } }
    scrTex.needsUpdate = true;
    // the room light follows the picture
    scrL.color.setHSL(SCRS.mode === "movie" ? 0.62 : SCRS.mode === "quiz" ? 0.75 : 0.6, 0.6, 0.6); scrL.intensity = SCRS.mode === "movie" ? 1.2 : 1.6;
  }
  function quizStart() { SCRS.mode = "quiz"; SCRS.q = 0; SCRS.score = 0; SCRS.friends = [0, 0, 0]; SCRS.pick = -1; SCRS.reveal = 0; SCRS.qt = 0; }
  function quizAnswer(i) { if (SCRS.mode !== "quiz" || SCRS.reveal > 0) return; SCRS.pick = i; SCRS.reveal = 2.2; const Q = QUIZ[SCRS.q]; if (i === Q[2]) { SCRS.score++; ding(); } else if (sndOn()) blip(180, now(), 0.3, 0.08, "sawtooth", 140); SCRS.friends = SCRS.friends.map((s, k) => s + (Math.random() < [0.55, 0.7, 0.4][k] ? 1 : 0)); NPC.forEach((n) => { if (n.where === "sofa") n.hop = 0.5; }); }
  function quizFrame(dt) { if (SCRS.mode !== "quiz") return; if (SCRS.reveal > 0) { SCRS.reveal -= dt; if (SCRS.reveal <= 0) { SCRS.q++; SCRS.pick = -1; SCRS.qt = 0; if (SCRS.q >= QUIZ.length) { SCRS.mode = "done"; if (SCRS.score > HS.quiz) { HS.quiz = SCRS.score; drawScores(); } say(SCRS.score >= 6 ? "Squawk! Movie genius!" : "Squawk! Watch more movies!"); } } } else { SCRS.qt += dt; if (SCRS.qt > 15) quizAnswer(-1); } }
  const SOFAG = {
    name: "Big screen", start() { const q = this.seat || SEATS.find((z) => z.sofa && !z.by); setCam([q.x, q.y + 0.05, q.z + 0.6], [q.x * 0.3 + 19 * 0.7, 0.35, 3.2], 80); if (SCRS.mode === "idle") SCRS.mode = "menu"; },
    end() { if (SCRS.mode === "menu") SCRS.mode = "idle"; this.seat = null; },
    update(dt) { const c = onWallY(0.352); SCRS.hover = null; if (c) { const u = (c[0] - 13.8) / 10.4 * 1280, v = (6.65 - c[1]) / 5.85 * 720; SCRS.hover = [u, v]; if (SCRS.mode === "menu") SCRS.pick = v > 340 && v < 424 && Math.abs(u - 640) < 360 ? 0 : v > 440 && v < 524 && Math.abs(u - 640) < 360 ? 1 : -1; if (SCRS.mode === "quiz" && SCRS.reveal <= 0) { const col = u > 80 && u < 620 ? 0 : u > 650 && u < 1190 ? 1 : -1, row = v > 300 && v < 390 ? 0 : v > 410 && v < 500 ? 1 : -1; SCRS.pick = col >= 0 && row >= 0 ? row * 2 + col : -1; } }
      hud("Big screen", SCRS.mode === "quiz" ? `Question ${SCRS.q + 1} of 8 · you ${SCRS.score}` : SCRS.mode === "movie" ? "Now showing: The Lighthouse Cat" : "Pick something", SCRS.mode === "quiz" ? "Press 1 to 4 or click an answer. The sofa friends play too." : SCRS.mode === "movie" ? "Press 1 for the quiz, M for the menu." : "Press 1 or 2, or click a button."); },
    down() { if (SCRS.mode === "menu") { if (SCRS.pick === 0) quizStart(); else if (SCRS.pick === 1) { SCRS.mode = "movie"; SCRS.movieT = 0; } } else if (SCRS.mode === "quiz") { if (SCRS.pick >= 0) quizAnswer(SCRS.pick); } else if (SCRS.mode === "done") quizStart(); },
    up() {}, key(code, dn) { if (!dn) return; const n = { Digit1: 0, Digit2: 1, Digit3: 2, Digit4: 3, Numpad1: 0, Numpad2: 1, Numpad3: 2, Numpad4: 3 }[code]; if (code === "KeyM") { SCRS.mode = "menu"; return; } if (n === undefined) return; if (SCRS.mode === "quiz") quizAnswer(n); else if (n === 0) quizStart(); else if (n === 1) { SCRS.mode = "movie"; SCRS.movieT = 0; } },
  };

  // ---------------- FISHING off the end of the pier ----------------
  const CATCH = [["A small fish", "You named it Kevin and let it go.", 5], ["An old boot", "Size 11. Left foot. Still damp.", 4], ["A TV remote", "Not the Lounge one. Somebody else is missing this.", 2], ["A rubber duck", "It squeaks. It is now the Lounge mascot.", 3], ["A message in a bottle", "It says: \"Stop fishing and come play air hockey.\"", 3], ["Gerald's cousin Darren", "Darren puffed up and swam off in a huff.", 2], ["A huge tuna", "Far too big for the bucket. You let it go before it noticed.", 2], ["Mostly seaweed", "Some of it is a sock.", 4], ["A treasure map", "X marks... the aquarium. Suspicious.", 1]];
  const rod = new T.Group(); { const r = new T.Mesh(new T.CylinderGeometry(0.006, 0.012, 1.3, 8), lam("#3a2a1c")); r.position.set(0, 0.55, 0); rod.add(r); const reel = new T.Mesh(new T.CylinderGeometry(0.03, 0.03, 0.03, 12), lam("#aab1ba")); reel.rotation.z = Math.PI / 2; reel.position.set(0.03, 0.1, 0); rod.add(reel); rod.position.set(0.28, -0.42, -0.6); rod.rotation.set(-0.9, 0, -0.15); rod.visible = false; camera.add(rod); }
  const bob = new T.Group(); { const a = new T.Mesh(new T.SphereGeometry(0.035, 12, 8), lam("#ff3b3b")); bob.add(a); const b = new T.Mesh(new T.SphereGeometry(0.036, 12, 8, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2), lam("#f4f4f4")); bob.add(b); bob.visible = false; G.fx.add(bob); }
  const fline = new T.Line(new T.BufferGeometry().setFromPoints([new T.Vector3(), new T.Vector3()]), new T.LineBasicMaterial({ color: 0xdfe8ff, transparent: true, opacity: 0.6 })); fline.visible = false; G.fx.add(fline); fline.frustumCulled = false;
  const FSH = { st: "idle", t: 0, x: 27, y: 36, msg: "", wait: 0 };
  const _tip = new T.Vector3();
  const FISHG = {
    name: "Fishing", start() { setCam([27.0, 32.4, 1.75], [27.0, 37.0, -0.2], 70); rod.visible = true; FSH.st = "idle"; FSH.msg = "Click (or Space) to cast. Wait for a bite, then click fast."; },
    end() { rod.visible = false; bob.visible = false; fline.visible = false; FSH.st = "idle"; },
    update(dt) { FSH.t += dt; const water = -0.8;
      if (FSH.st === "cast") { const u = Math.min(1, FSH.t / 0.7); bob.position.set(WX(27 + (FSH.x - 27) * u), 1.4 + (water - 1.4) * u + Math.sin(u * Math.PI) * 1.5, WZ(32.9 + (FSH.y - 32.9) * u)); if (u >= 1) { FSH.st = "wait"; FSH.t = 0; FSH.wait = 2.5 + Math.random() * 4.5; if (sndOn()) splashS(0.4); } }
      else if (FSH.st === "wait") { bob.position.y = water + 0.02 + Math.sin(FSH.t * 2.2) * 0.01; if (FSH.t > FSH.wait) { FSH.st = "bite"; FSH.t = 0; FSH.msg = "BITE! Click now!"; if (sndOn()) { blip(300, now(), 0.15, 0.1, "sine", 120); splashS(0.6); } } }
      else if (FSH.st === "bite") { bob.position.y = water - 0.05 + Math.sin(FSH.t * 30) * 0.03; if (FSH.t > 1.3) { FSH.st = "idle"; FSH.msg = "It got away. It was definitely huge. Click to cast again."; bob.visible = false; } }
      else if (FSH.st === "reel") { const u = Math.min(1, FSH.t / 0.8); bob.position.set(WX(FSH.x + (27 - FSH.x) * u), water + u * 2.0, WZ(FSH.y + (32.9 - FSH.y) * u)); if (u >= 1) { FSH.st = "idle"; bob.visible = false; } }
      if (bob.visible) { rod.updateMatrixWorld(); _tip.set(0, 1.2, 0); rod.localToWorld(_tip); const a = fline.geometry.attributes.position; a.setXYZ(0, _tip.x, _tip.y, _tip.z); a.setXYZ(1, bob.position.x, bob.position.y, bob.position.z); a.needsUpdate = true; fline.visible = true; } else fline.visible = false;
      rod.rotation.x = -0.9 + (FSH.st === "cast" ? -Math.sin(Math.min(1, FSH.t / 0.35) * Math.PI) * 0.8 : 0) + (FSH.st === "bite" ? Math.sin(FSH.t * 30) * 0.05 : 0);
      hud("Fishing", `Caught tonight: ${HS.fish}`, FSH.msg); },
    down() { if (FSH.st === "idle") { FSH.st = "cast"; FSH.t = 0; FSH.x = 26.2 + Math.random() * 1.6; FSH.y = 35 + Math.random() * 2.5; bob.visible = true; FSH.msg = "Waiting for a bite..."; if (sndOn()) hiss(now(), 0.3, 0.1, "highpass", 3000, 1); }
      else if (FSH.st === "bite") { let tot = 0; CATCH.forEach((c) => (tot += c[2])); let r = Math.random() * tot, c = CATCH[0]; for (const q of CATCH) { r -= q[2]; if (r <= 0) { c = q; break; } }
        const gullNear = Math.hypot(GULL_SPOTS[GULL.i][0] - 27, GULL_SPOTS[GULL.i][1] - 31) < 6 && Math.random() < 0.35;
        if (gullNear && c[0].match(/fish|tuna/i)) { FSH.msg = `${c[0]}! ...and Gary the seagull stole it. Classic Gary.`; gullFly(true); } else { FSH.msg = `You caught: ${c[0]}. ${c[1]}`; HS.fish++; drawScores(); ding(); }
        if (c[0] === "An old boot") say("Squawk! Nice boot!"); FSH.st = "reel"; FSH.t = 0; }
      else if (FSH.st === "wait") { FSH.msg = "Too early! Wait for the bobber to dip."; } },
    up() {}, key(code, dn) { if (code === "Space" && dn) this.down(); },
  };
  function splashS(v) { const t = now(); hiss(t, 0.4, 0.3 * v, "bandpass", 1300, 0.7); blip(200, t, 0.15, 0.08 * v, "sine", 80); }

  // ---------------- MARSHMALLOWS by the fire ----------------
  const mallow = new T.Group(); { const st = new T.Mesh(new T.CylinderGeometry(0.004, 0.006, 0.9, 6), lam("#8a6a4a")); st.rotation.x = Math.PI / 2; st.position.z = -0.35; mallow.add(st); const m = new T.Mesh(new T.CylinderGeometry(0.03, 0.03, 0.045, 14), new T.MeshStandardMaterial({ color: 0xfff8ec, roughness: 0.8 })); m.rotation.x = Math.PI / 2; m.position.z = -0.8; mallow.add(m); MALLOW_M = m; const fl = new T.Mesh(new T.ConeGeometry(0.03, 0.09, 10), new T.MeshBasicMaterial({ color: 0xff8a3c, transparent: true, opacity: 0.85, toneMapped: false })); fl.position.set(0, 0.06, -0.8); fl.visible = false; mallow.add(fl); MALLOW_F = fl; mallow.position.set(0.12, -0.32, -0.1); mallow.rotation.set(-0.25, 0.1, 0); mallow.visible = false; camera.add(mallow); }
  var MALLOW_M, MALLOW_F;
  const MS = { t: 0, msg: "" };
  const MALL = [[2.5, "Raw", "Still cold in the middle. Brave."], [5.0, "Golden", "Perfect golden brown. Steve the swordfish approves."], [7.0, "Toasty", "Nice and toasty. A solid 8 out of 10."], [9.5, "ON FIRE", "You ate a flaming marshmallow. Bold move."], [99, "Charcoal", "That's charcoal. Captain Pickles is laughing at you."]];
  const MALLG = {
    name: "Marshmallows", start() { setCam([2.25, 15.3, 1.25], [0.6, 15.3, 0.55], 70); mallow.visible = true; MS.t = 0; MS.msg = "Hold it over the fire. Click (or Space) to eat it when it looks right."; },
    end() { mallow.visible = false; },
    update(dt) { MS.t += dt; const st = MALL.findIndex((m) => MS.t < m[0]); const c = [[1, 0.97, 0.92], [0.93, 0.72, 0.38], [0.62, 0.38, 0.18], [0.35, 0.18, 0.08], [0.06, 0.05, 0.05]][st]; MALLOW_M.material.color.setRGB(c[0], c[1], c[2]); MALLOW_F.visible = st === 3; MALLOW_F.scale.y = 1 + Math.sin(MS.t * 20) * 0.2;
      mallow.position.y = -0.32 + Math.sin(MS.t * 1.3) * 0.01; hud("Marshmallows", `It's: ${MALL[st][1]}`, MS.msg); },
    down() { const st = MALL.findIndex((m) => MS.t < m[0]); MS.msg = MALL[st][2] + " Here's another one."; if (st === 1) { HS.mallow++; drawScores(); ding(); } if (st === 4) say("Squawk! Charcoal! Charcoal!"); MS.t = 0; if (sndOn()) hiss(now(), 0.15, 0.15, "lowpass", 600, 1); },
    up() {}, key(code, dn) { if (code === "Space" && dn) this.down(); },
  };

  // ---------------- the coin telescope on the deck ----------------
  const VIEW = { a: 0, e: 0 };
  const VIEWG = {
    name: "Harbor viewer", start() { VIEW.a = -0.92; VIEW.e = 0.12; document.getElementById("scope").hidden = false; },
    end() { document.getElementById("scope").hidden = true; },
    update(dt) { const c = ndc; VIEW.a += (-0.92 - c.x * 0.9 - VIEW.a) * Math.min(1, dt * 3); VIEW.e += (0.12 + c.y * 0.2 - VIEW.e) * Math.min(1, dt * 3); const p = [33.6, 24.95, 1.3]; setCam(p, [p[0] + Math.sin(VIEW.a) * 40, p[1] + Math.cos(VIEW.a) * 40, p[2] + Math.tan(VIEW.e) * 40], 12); hud("Harbor viewer", "Free tonight. Normally 25¢.", "Move the mouse to look round: the lighthouse, the town, the moon."); },
    down() {}, up() {}, key() {},
  };

  // ---------------- where each game is played from ----------------
  const STATIONS = [
    { g: POOLG, x: 19.0, y: 14.2, r: 2.45, f: 0, label: "Play pool", n: 1, px: 19.0, py: 15.9 },
    { g: AIRG, x: 26.6, y: 13.45, r: 1.1, f: 0, label: "Play air hockey", n: 2 },
    { g: FOURG, x: 26.6, y: 15.95, r: 1.0, f: 0, label: "Play four in a row", n: 3 },
    { g: DARTG, x: 3.0, y: 2.9, r: 1.0, f: 0, label: "Throw darts", n: 4 },
    { g: SHUFG, x: 5.6, y: 18.55, r: 1.0, f: 0, label: "Play shuffleboard", n: 5 },
    { g: null, x: 27.2, y: 1.4, r: 1.2, f: 0, label: "Change the song", n: 6, act: () => jukeNext() },
    { g: SOFAG, x: 19.0, y: 5.25, r: 0, f: 0, label: "Sit and use the big screen", n: 7 },
    { g: FISHG, x: 27.0, y: 32.3, r: 1.0, f: 0, label: "Go fishing", n: 8 },
    { g: MALLG, x: 2.05, y: 15.3, r: 0.9, f: 0, label: "Toast a marshmallow", n: 9 },
    { g: VIEWG, x: 33.6, y: 24.0, r: 0.9, f: 0, label: "Look through the viewer", n: 10 },
  ];
  // ---------------- sitting down: one person per seat ----------------
  const SITG = {
    name: "Sitting", start() { const q = this.seat; this.t = 0; hud("Sitting on " + q.what, "", "Move the mouse to look around."); },
    end() { this.seat = null; },
    update(dt) { const q = this.seat, a = q.yaw - ndc.x * 1.5, e = ndc.y * 0.7, ex = q.x, ey = q.y, ez = q.z + 0.62; setCam([ex, ey, ez], [ex - Math.sin(a) * 4, ey - Math.cos(a) * 4, ez + Math.tan(e) * 4], 72); hud("Sitting on " + q.what, "", "Move the mouse to look around."); },
    down() {}, up() {}, key() {},
  };
  function sitDown(q) { if (q.by) { showMsg(`${q.by} is sitting there. Pick another seat.`); return; } const g = q.sofa ? SOFAG : SITG; g.seat = q; startGame({ g }); }
  let nearSeat = null;
  let nearSt = null;
  function startGame(st) {
    if (st.act) { const t = st.act(); showMsg(t); return; }
    game = st.g; game.st = st; if (document.pointerLockElement) document.exitPointerLock(); camNow.init = false; game.start(); kick();
    if (promptEl) promptEl.hidden = true; if (playBtn) playBtn.hidden = true;
  }
  function stopGame() { if (!game) return; game.end(); game = null; if (hudEl) hudEl.hidden = true; if (hintEl) hintEl.style.visibility = ""; camera.fov = 70; camera.updateProjectionMatrix(); kick(); }
  function showMsg(t) { if (!tipEl) return; tipEl.textContent = t; tipEl.hidden = false; clearTimeout(showMsg.h); showMsg.h = setTimeout(() => (tipEl.hidden = true), 3200); }
  function stationFrame() {
    nearSt = null; nearSeat = null;
    if (!game && mode === "walk") { let bd = 1e9; STATIONS.forEach((s) => { const d = Math.hypot(P.x - s.x, P.y - s.y); if (d < s.r && Math.abs(P.f - s.f) < 0.7 && d < bd) { bd = d; nearSt = s; } });
      // the seat you are looking at wins over a seat that is merely close
      const fx = -Math.sin(yaw), fy = -Math.cos(yaw); let sd = 1e9; SEATS.forEach((q) => { const dx = q.x - P.x, dy = q.y - P.y, d = Math.hypot(dx, dy); if (d > 1.15 || Math.abs(P.f - q.f) > 0.75) return; const sc = d - 0.35 * (d > 0.05 ? (dx * fx + dy * fy) / d : 1); if (sc < sd) { sd = sc; nearSeat = q; } });
      if (nearSeat && nearSt && nearSt.g !== POOLG && bd < 0.6) nearSeat = null; else if (nearSeat) nearSt = null; }
    const lab = nearSeat ? (nearSeat.by ? `${nearSeat.by} is sitting here` : nearSeat.sofa ? "Sit and use the big screen" : "Sit down") : nearSt ? nearSt.label : "";
    if (promptEl) { promptEl.hidden = !lab; if (lab) promptEl.innerHTML = nearSeat && nearSeat.by ? lab : `<kbd>E</kbd> ${lab}`; }
    if (playBtn) { playBtn.hidden = !lab || !!(nearSeat && nearSeat.by); if (lab) playBtn.textContent = lab; }
  }
  if (playBtn) playBtn.addEventListener("click", (e) => { e.preventDefault(); armed = true; if (nearSeat) sitDown(nearSeat); else if (nearSt) startGame(nearSt); });
  stage.addEventListener("pointerdown", (e) => { if (game) { e.stopImmediatePropagation(); armed = true; stage.focus({ preventScroll: true }); game.down(); } }, true);
  window.addEventListener("pointerup", () => { if (game) game.up(); });
  window.addEventListener("keydown", (e) => {
    if (!armed) return;
    if (e.code === "KeyE" || (e.code === "Escape" && game)) { e.preventDefault(); if (game) stopGame(); else if (nearSeat) sitDown(nearSeat); else if (nearSt) startGame(nearSt); return; }
    if (game) { if (["Space", "KeyR", "KeyM", "KeyV"].includes(e.code) || /^(Digit|Numpad)[1-7]$/.test(e.code)) { e.preventDefault(); if (!e.repeat) game.key(e.code, true); } }
  }, true);
  window.addEventListener("keyup", (e) => { if (game) game.key(e.code, false); }, true);
  function gameCam(dt) {
    if (game && game.update) game.update(dt);
    if (!camNow.init) { camNow.p.copy(camera.position); const d = new T.Vector3(); camera.getWorldDirection(d); camNow.t.copy(camera.position).add(d.multiplyScalar(3)); camNow.init = true; }
    const k = Math.min(1, dt * 5); camNow.p.lerp(camGoal.p, k); camNow.t.lerp(camGoal.t, k);
    camera.position.copy(camNow.p); camera.lookAt(camNow.t); if (Math.abs(camera.fov - camGoal.fov) > 0.05) { camera.fov += (camGoal.fov - camera.fov) * k; camera.updateProjectionMatrix(); }
  }
  flush();
  // ---------- picture quality: "Pretty" adds glow (bloom) and soft shadows; drops to "Fast" by itself on a slow computer ----------
  let pretty = true, composer = null, bloom = null, shadowDirty = true, slowT = 0, autoDropped = false;
  scene.traverse((o) => { if (o.isMesh && !o.isInstancedMesh && o.material && o.material.isMeshStandardMaterial && !o.material.transparent) { o.castShadow = true; o.receiveShadow = true; } });
  G.ceil.traverse((o) => { o.castShadow = false; });
  try {
    if (T.EffectComposer && T.UnrealBloomPass) {
      const rt = renderer.capabilities.isWebGL2 && T.WebGLMultisampleRenderTarget ? new T.WebGLMultisampleRenderTarget(16, 16, { format: T.RGBAFormat, encoding: T.sRGBEncoding }) : new T.WebGLRenderTarget(16, 16, { format: T.RGBAFormat, encoding: T.sRGBEncoding });
      composer = new T.EffectComposer(renderer, rt);
      composer.addPass(new T.RenderPass(scene, camera));
      bloom = new T.UnrealBloomPass(new T.Vector2(256, 256), 0.45, 0.45, 0.97); composer.addPass(bloom);
    }
  } catch (e) { composer = null; }
  function setPretty(on) {
    pretty = on; renderer.shadowMap.enabled = on; shadowDirty = true;
    scene.traverse((o) => { if (o.material) [].concat(o.material).forEach((m) => (m.needsUpdate = true)); });
    const b = document.getElementById("look"); if (b) { b.setAttribute("aria-pressed", on ? "true" : "false"); b.textContent = on ? "Look: Pretty" : "Look: Fast"; }
    kick && kick();
  }
  function autoQuality(dt) {
    if (!pretty || autoDropped) return;
    if (dt > 1 / 28) slowT += dt; else slowT = Math.max(0, slowT - dt * 0.5);
    if (slowT > 4) { autoDropped = true; setPretty(false); }
  }
  document.addEventListener("click", (e) => { if (e.target.closest("#look")) { autoDropped = true; setPretty(!pretty); } });

  // =====================================================================
  //  WALKING (same rules as the Warehouse: no phasing, no escaping, jump onto low things only)
  // =====================================================================
  const R = 0.28, STEP = 0.6, JUMP_V = 4.3, GRAV = 18;
  const SPAWN = { x: 31.5, y: 1.7 };
  const P = { x: SPAWN.x, y: SPAWN.y, f: 0, H: 1.7, vy: 0, eye: 1.6, air: false, jf: 0 };
  let yaw = Math.atan2(13.5, -8.4), pitch = -0.04, crouchHeld = false, ctrlHeld = false;
  const overlap = (b, x, y) => x + R > b.x1 && x - R < b.x2 && y + R > b.y1 && y - R < b.y2;
  // you can stand on stairs, ramps and things at least 40 cm across; game tables, railings and shelves are marked "not for standing"
  const standable = (b) => b.st === 1 || (b.st !== -1 && Math.min(b.x2 - b.x1, b.y2 - b.y1) >= 0.4);
  const lim = (feet) => (P.air ? Math.min(feet + STEP, Math.max(P.jf + STEP, 1.25)) : feet + STEP);
  let GRID = null, gridN = 0; const GB = 2;
  function near(x, y) {
    if (!GRID || gridN !== SOL.length) {
      GRID = new Map(); gridN = SOL.length;
      SOL.forEach((b) => { for (let i = Math.floor((b.x1 - R) / GB); i <= Math.floor((b.x2 + R) / GB); i++) for (let j = Math.floor((b.y1 - R + 20) / GB); j <= Math.floor((b.y2 + R + 20) / GB); j++) { const k = i * 1000 + j; (GRID.get(k) || GRID.set(k, []).get(k)).push(b); } });
    }
    return GRID.get(Math.floor(x / GB) * 1000 + Math.floor((y + 20) / GB)) || [];
  }
  function supportAt(x, y, feet, L) {
    const top = (L === undefined ? lim(feet) : L) + 1e-6; let best = 0;
    for (const b of near(x, y)) if (b.t <= top && b.t > best && standable(b) && overlap(b, x, y)) best = b.t;
    return best;
  }
  function hitAt(x, y, feet, H, ex, L) {
    const top = (L === undefined ? lim(feet) : L) + 1e-6;
    for (const b of near(x, y)) {
      if (b.b < feet + H - 0.02 && (b.t > top || (!standable(b) && b.t > feet + 0.05)) && overlap(b, x, y)) {
        if (ex && b.b < ex.f + ex.H - 0.02 && (b.t > ex.f + STEP || (!standable(b) && b.t > ex.f + 0.05)) && overlap(b, ex.x, ex.y)) continue;
        return true;
      }
    }
    return false;
  }
  function tryMove(nx, ny) {
    if (!inBounds(nx, ny)) return false;
    const heights = crouchHeld || ctrlHeld ? [0.9, 0.6] : [1.7, 0.9, 0.6];
    const sup = supportAt(nx, ny, P.f), f2 = Math.max(P.f, sup);
    for (const H of heights) {
      if (!hitAt(nx, ny, f2, H, P)) { P.x = nx; P.y = ny; P.H = H; if (f2 > P.f) { P.f = f2; P.vy = 0; P.air = false; } return true; }
    }
    return false;
  }
  function jump() {
    if (P.air || P.vy !== 0 || Math.abs(supportAt(P.x, P.y, P.f) - P.f) > 0.02) return;
    P.air = true; P.jf = P.f; P.vy = -JUMP_V;
  }
  function physics(dt) {
    // the slide carries you down to the floor (it runs from the loft towards the front of the room)
    for (const z of (window.WH_SLIDES || [])) if (P.f > 0.04 && P.x > z.x1 - 0.05 && P.x < z.x2 + 0.05 && P.y > z.y1 - 0.05 && P.y < z.y2) { const n = 4; for (let k = 0; k < n; k++) tryMove(P.x, P.y + 5.5 * dt / n); break; }
    const sup = supportAt(P.x, P.y, P.f);
    if (P.vy < 0) { const nf = P.f - P.vy * dt; P.vy += GRAV * dt; if (hitAt(P.x, P.y, nf, P.H, null, Infinity)) P.vy = 0; else P.f = nf; }
    else if (sup < P.f - 0.001) { P.vy += GRAV * dt; P.f = Math.max(sup, P.f - P.vy * dt); if (P.f <= sup + 0.0001) { P.vy = 0; P.air = false; } }
    else { P.f = sup; P.vy = 0; P.air = false; }
    const crouch = crouchHeld || ctrlHeld;
    if (P.H < 1.7) { const up = crouch ? [0.9] : [1.7, 0.9]; for (const h of up) { if (h > P.H && !hitAt(P.x, P.y, P.f, h)) { P.H = h; break; } } }
    if (crouch && P.H > 0.9) P.H = 0.9;
    const want = P.f + (P.H > 1 ? 1.6 : P.H > 0.7 ? 0.72 : 0.45);
    P.eye += (want - P.eye) * Math.min(1, dt * (P.air ? 30 : 10));
  }
  // inside the Lounge, the deck doorway, the deck and the pier. Nowhere else.
  function inBounds(x, y) {
    return (x > 0.15 && x < 35.85 && y > 0.15 && y < 19.85) || (x > 22.6 && x < 25.0 && y >= 19.8 && y <= 20.2) || (x > 1.0 && x < 35.0 && y > 20.15 && y < 25.5) || (x > 26.0 && x < 28.0 && y >= 25.4 && y < 33.0);
  }
  const SAFE = { x: P.x, y: P.y, f: P.f };
  function keepSafe() {
    if (!inBounds(P.x, P.y) || hitAt(P.x, P.y, P.f, P.H, null, P.f + STEP)) { P.x = SAFE.x; P.y = SAFE.y; P.f = SAFE.f; P.vy = 0; P.air = false; }
    else { SAFE.x = P.x; SAFE.y = P.y; SAFE.f = P.f; }
  }

  // =====================================================================
  //  MODES: walk / auto tour / bird's-eye
  // =====================================================================
  let mode = "walk", orbit = { a: -0.6, e: 0.95, r: 34 };
  const tourPts = [[31.5, 1.7], [29.6, 3.4], [29.2, 8.4], [25.3, 9.2], [25.3, 13.6], [23.2, 16.4], [19.0, 16.6], [14.6, 16.6], [12.6, 13.6], [10.6, 10.2], [9.4, 7.8], [8.9, 4.2], [7.6, 5.3], [3.6, 5.3], [3.0, 3.6], [3.6, 5.3], [7.6, 5.6], [9.0, 6.0], [9.6, 9.4], [7.4, 12.9], [4.25, 13.0], [4.25, 17.5], [8.6, 17.5], [14.6, 17.4], [20.8, 18.2], [23.8, 18.8], [23.8, 21.8], [27.0, 23.0], [27.0, 31.6], [27.0, 24.6], [33.0, 23.4], [30.8, 21.6], [23.8, 21.6], [23.8, 18.6], [28.6, 17.4], [30.3, 14.0], [30.3, 9.0], [31.2, 6.4], [33.6, 6.4], [33.6, 2.2], [31.5, 1.7]];
  let tourLen = 0; const tourCum = [0];
  for (let i = 1; i < tourPts.length; i++) { tourLen += Math.hypot(tourPts[i][0] - tourPts[i - 1][0], tourPts[i][1] - tourPts[i - 1][1]); tourCum.push(tourLen); }
  let tourD = 0;
  function tourAt(d) {
    d = ((d % tourLen) + tourLen) % tourLen;
    let i = 1; while (i < tourCum.length - 1 && tourCum[i] < d) i++;
    const a = tourPts[i - 1], b = tourPts[i], u = (d - tourCum[i - 1]) / Math.max(0.001, tourCum[i] - tourCum[i - 1]);
    return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u];
  }
  function setVisibility() { G.ceil.visible = mode !== "bird"; }
  const keys = {};
  let armed = false;
  stage.addEventListener("pointerdown", () => { armed = true; stage.focus({ preventScroll: true }); });
  document.addEventListener("pointerdown", (e) => { if (e.target !== stage && !e.target.closest(".pad") && !e.target.closest("#playBtn")) armed = false; });
  const KEYMAP = { KeyW: "f", ArrowUp: "f", KeyS: "b", ArrowDown: "b", KeyA: "l", KeyD: "r", ArrowLeft: "tl", ArrowRight: "tr" };
  window.addEventListener("keydown", (e) => {
    if (!armed) return;
    if (game) { const k = KEYMAP[e.code]; if (k) { keys[k] = 1; e.preventDefault(); } return; }
    if (e.code === "KeyC") { crouchHeld = !crouchHeld; syncCrouch(); e.preventDefault(); return; }
    if (e.code === "Space") { keys.jump = 1; e.preventDefault(); if (mode !== "walk") setMode("walk"); return; }
    if (e.code === "ControlLeft") { ctrlHeld = true; e.preventDefault(); if (!kbLocked) showTip("Tip: in full screen, Left Ctrl is safe to hold. Outside it, Ctrl + W closes the tab, so use C there."); return; }
    if (e.ctrlKey && !kbLocked) ctrlHeld = false;
    const k = KEYMAP[e.code]; if (k) { keys[k] = 1; e.preventDefault(); if (mode !== "walk") setMode("walk"); }
    if (e.shiftKey) keys.run = 1;
  });
  window.addEventListener("keyup", (e) => { const k = KEYMAP[e.code]; if (k) keys[k] = 0; if (!e.shiftKey) keys.run = 0; if (e.code === "ControlLeft") ctrlHeld = false; });
  window.addEventListener("blur", () => { Object.keys(keys).forEach((k) => (keys[k] = 0)); ctrlHeld = false; });
  // ---- full screen: the view fills the screen, the mouse turns you, and the keyboard is locked so shortcuts can't close the tab ----
  let kbLocked = false, tipT = 0;
  const fsBtn = document.getElementById("fs"), jumpBtn = document.getElementById("jumpBtn"), tipEl = document.getElementById("tip");
  function showTip(t) { if (!tipEl || performance.now() - tipT < 6000) return; tipT = performance.now(); tipEl.textContent = t; tipEl.hidden = false; setTimeout(() => (tipEl.hidden = true), 4500); }
  const host = () => { try { return (window.parent !== window && window.parent.GF_LOUNGE_HOST) || {}; } catch (e) { return {}; } };
  const lobbyEl = () => { try { return window.parent.document.getElementById("lb") || document.documentElement; } catch (e) { return document.documentElement; } };
  const isFull = () => { try { return !!(window.parent.document.fullscreenElement || document.fullscreenElement); } catch (e) { return !!document.fullscreenElement; } };
  async function enterFull() { armed = true; try { await lobbyEl().requestFullscreen({ navigationUI: "hide" }); } catch (e) {} }
  function exitFull() { try { const d = window.parent.document; if (d.fullscreenElement) d.exitFullscreen().catch(() => {}); } catch (e) {} try { if (document.fullscreenElement) document.exitFullscreen().catch(() => {}); } catch (e) {} }
  async function onFull() {
    const on = isFull();
    if (fsBtn) { fsBtn.textContent = on ? "Exit full screen" : "Full screen"; fsBtn.setAttribute("aria-pressed", String(on)); }
    kbLocked = false;
    try { const kb = window.parent.navigator.keyboard; if (kb && kb.lock) { if (on) { await kb.lock(); kbLocked = true; } else kb.unlock(); } } catch (e) {}
    resize(); kick();
  }
  document.addEventListener("fullscreenchange", onFull);
  if (fsBtn) fsBtn.addEventListener("click", () => (isFull() ? exitFull() : enterFull()));
  if (jumpBtn) jumpBtn.addEventListener("pointerdown", (e) => { e.preventDefault(); armed = true; keys.jump = 1; if (mode !== "walk") setMode("walk"); kick(); });
  // the mouse turns you once you click into the view (Esc frees it again); games use the mouse their own way
  const grabMouse = () => { if (game || document.pointerLockElement === stage) return; try { const p = stage.requestPointerLock(); if (p && p.catch) p.catch(() => {}); } catch (e) {} };
  stage.addEventListener("click", grabMouse);
  window.addEventListener("keydown", (e) => {
    if (!armed || e.ctrlKey || e.metaKey) return;
    if (e.code === "KeyF") { e.preventDefault(); isFull() ? exitFull() : enterFull(); return; }
    if (game) return;
    if (e.code === "Enter") { e.preventDefault(); const h = host(); if (h.chat) h.chat(); return; }
    if (e.code === "Digit1" || e.code === "Digit2" || e.code === "Digit3") { const h = host(); if (h.emote) h.emote(["wave", "clap", "laugh"][+e.code.slice(5) - 1]); }
  });
  document.addEventListener("mousemove", (e) => {
    if (document.pointerLockElement !== stage || game) return;
    if (mode === "tour") setMode("walk");
    if (mode === "bird") return;
    yaw -= e.movementX * 0.0026; pitch = Math.max(-1.35, Math.min(1.35, pitch - e.movementY * 0.0026)); kick();
  });
  document.querySelectorAll(".pad button[data-k]").forEach((b) => {
    const k = b.dataset.k;
    const on = (e) => { e.preventDefault(); keys[k] = 1; b.classList.add("on"); armed = true; if (mode !== "walk" && !game) setMode("walk"); };
    const off = () => { keys[k] = 0; b.classList.remove("on"); };
    b.addEventListener("pointerdown", on); b.addEventListener("pointerup", off); b.addEventListener("pointerleave", off); b.addEventListener("pointercancel", off);
  });
  const crouchBtn = document.getElementById("crouch");
  function syncCrouch() { if (crouchBtn) { crouchBtn.setAttribute("aria-pressed", String(crouchHeld)); crouchBtn.classList.toggle("on", crouchHeld); } }
  if (crouchBtn) crouchBtn.addEventListener("click", (e) => { e.preventDefault(); crouchHeld = !crouchHeld; syncCrouch(); });
  let drag = null;
  stage.addEventListener("pointerdown", (e) => { drag = { x: e.clientX, y: e.clientY }; try { stage.setPointerCapture(e.pointerId); } catch (q) {} });
  stage.addEventListener("pointermove", (e) => {
    if (!drag || game) return;
    const dx = e.clientX - drag.x, dy = e.clientY - drag.y; drag = { x: e.clientX, y: e.clientY };
    if (mode === "bird") { orbit.a -= dx * 0.006; orbit.e = Math.max(0.35, Math.min(1.45, orbit.e + dy * 0.004)); }
    else { if (mode === "tour") setMode("walk"); yaw -= dx * 0.0045; pitch = Math.max(-1.35, Math.min(1.35, pitch - dy * 0.0045)); }
  });
  const endDrag = () => { drag = null; };
  stage.addEventListener("pointerup", endDrag); stage.addEventListener("pointercancel", endDrag);
  stage.addEventListener("wheel", (e) => { if (mode === "bird") { orbit.r = Math.max(16, Math.min(60, orbit.r + e.deltaY * 0.03)); e.preventDefault(); } }, { passive: false });

  const modeBtns = document.querySelectorAll("[data-mode]");
  const capL = document.getElementById("capL");
  function setMode(m) {
    if (game && m !== "walk") stopGame();
    mode = m;
    modeBtns.forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.mode === m)));
    setVisibility();
    if (capL) capL.textContent = m === "bird" ? "Bird's-eye · roof off" : m === "tour" ? "Auto tour" : "Harbor Lounge";
    if (m === "tour") { let best = 0, bd = 1e9; for (let d = 0; d < tourLen; d += 0.5) { const p = tourAt(d), q = Math.hypot(p[0] - P.x, p[1] - P.y); if (q < bd) { bd = q; best = d; } } tourD = best; }
    kick();
  }
  modeBtns.forEach((b) => b.addEventListener("click", () => setMode(b.dataset.mode)));
  // "Take me there" buttons
  const PLACES = {
    door: [31.5, 1.7, 0, Math.atan2(13.5, -8.4), "Front door from the Lobby"],
    screen: [19.0, 8.0, 0, 0, "The big screen and the sofa"],
    pool: [19.0, 16.2, 0, 0, "Pool table · press E"],
    games: [25.3, 13.4, 0, -Math.PI / 2, "Air hockey and four in a row"],
    darts: [3.0, 3.0, 0, 0, "Darts under the loft · press E"],
    fire: [3.4, 15.3, 0, Math.PI / 2, "The fireplace"],
    loft: [4.5, 5.0, LF, Math.PI, "Crow's Nest loft · the slide is on your left"],
    tank: [33.6, 6.4, 0, -Math.PI / 2, "The aquarium wall · jump near Gerald"],
    bar: [30.6, 15.6, 0, -Math.PI / 2, "Snack bar and Captain Pickles"],
    juke: [27.2, 1.6, 0, 0, "Jukebox · press E"],
    shuffle: [5.6, 18.55, 0, -Math.PI / 2, "Shuffleboard · press E"],
    deck: [23.8, 22.6, 0, Math.PI, "The deck"],
    pier: [27.0, 31.8, 0, Math.PI, "End of the pier · press E to fish"],
  };
  function goPlace(x, y, f, yw, label) {
    if (game) stopGame();
    setMode("walk"); crouchHeld = false; syncCrouch(); P.H = 1.7; P.x = x; P.y = y; P.f = f; P.vy = 0; P.air = false; P.eye = f + 1.6; yaw = yw; pitch = -0.08;
    SAFE.x = x; SAFE.y = y; SAFE.f = f;
    if (capL) capL.textContent = label;
    armed = true; stage.focus({ preventScroll: true });
    kick();
  }
  document.addEventListener("click", (e) => { const b = e.target.closest("[data-place]"); if (!b) return; const p = PLACES[b.dataset.place]; if (p) goPlace(...p); });

  // =====================================================================
  //  LOOP
  // =====================================================================
  function resize() {
    const w = stage.clientWidth, h = stage.clientHeight || w * 9 / 16;
    renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
    if (composer) { const pr = renderer.getPixelRatio(); composer.setSize(w, h); if (bloom) bloom.resolution.set(w * pr, h * pr); }
  }
  window.addEventListener("resize", () => { resize(); kick(); });
  resize();
  let last = performance.now(), frames = 0, fpsT = last, running = false, visible = true;
  const io = new IntersectionObserver((es) => { visible = es[0].isIntersecting; if (visible) kick(); }, { threshold: 0.01 });
  io.observe(stage);
  function kick() { if (!running) { running = true; last = performance.now(); requestAnimationFrame(frame); } }
  function frame(now) {
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    if (game) gameCam(dt);
    else if (mode === "walk") {
      const turn = (keys.tl ? 1 : 0) - (keys.tr ? 1 : 0); yaw += turn * dt * 1.9;
      const fwd = (keys.f ? 1 : 0) - (keys.b ? 1 : 0), side = (keys.r ? 1 : 0) - (keys.l ? 1 : 0);
      if (fwd || side) {
        const sp = (keys.run ? 7 : 3.6) * (P.H < 1 ? 0.55 : 1) * dt;
        const fx = -Math.sin(yaw), fy = -Math.cos(yaw), rx = Math.cos(yaw), ry = -Math.sin(yaw);
        let mx = fx * fwd + rx * side, my = fy * fwd + ry * side; const l = Math.hypot(mx, my); mx = mx / l * sp; my = my / l * sp;
        const n = Math.ceil(sp / 0.12);
        for (let k = 0; k < n; k++) { if (!tryMove(P.x + mx / n, P.y + my / n)) { if (!tryMove(P.x + mx / n, P.y)) tryMove(P.x, P.y + my / n); } }
      }
      if (keys.jump) { jump(); keys.jump = 0; }
      physics(dt); keepSafe();
      camera.position.set(WX(P.x), P.eye, WZ(P.y)); camera.rotation.set(pitch, yaw, 0);
    } else if (mode === "tour") {
      tourD += dt * 1.6;
      const p = tourAt(tourD), q = tourAt(tourD + 2.5);
      P.x = p[0]; P.y = p[1]; P.f = 0; P.H = 1.7; P.eye = 1.6;
      const ty = Math.atan2(-(q[0] - p[0]), -(q[1] - p[1]));
      let d = ty - yaw; while (d > Math.PI) d -= Math.PI * 2; while (d < -Math.PI) d += Math.PI * 2; yaw += d * Math.min(1, dt * 1.8);
      pitch += (-0.06 - pitch) * Math.min(1, dt * 2);
      camera.position.set(WX(P.x), P.eye, WZ(P.y)); camera.rotation.set(pitch, yaw, 0);
    } else {
      orbit.a += dt * 0.05;
      const cx = WX(18), cz = WZ(15);
      camera.position.set(cx + Math.sin(orbit.a) * Math.cos(orbit.e) * orbit.r, Math.sin(orbit.e) * orbit.r, cz + Math.cos(orbit.a) * Math.cos(orbit.e) * orbit.r);
      camera.lookAt(cx, 0, cz);
    }
    // doors open when you walk up to them
    const ex = mode === "bird" ? -999 : P.x, ey = mode === "bird" ? -999 : P.y, ef = P.f;
    DOORS.forEach((d) => {
      const nr = Math.hypot(d.cx - ex, d.cy - ey) < 1.9 && Math.abs(d.z - ef) < 2.2;
      const tgt = nr ? 1 : 0; if (nr && d.o < 0.05 && !d.sq) { d.sq = 1; if (sndOn()) squeak(1); } if (!nr && d.o < 0.05) d.sq = 0; d.o += (tgt - d.o) * Math.min(1, dt * 5);
      if (d.kind === "lid") d.pv.rotation.x = -d.o * 1.15; else d.pv.rotation.y = d.rot * d.o * Math.PI * 0.48;
    });
    stationFrame();
    liveFrame(dt, now);
    peopleFrame(dt, now);
    if (shadowDirty) { renderer.shadowMap.needsUpdate = true; shadowDirty = false; }
    if (composer && pretty) composer.render(); else renderer.render(scene, camera);
    autoQuality(dt);
    drawMini();
    frames++; if (now - fpsT > 1000) { if (fpsEl) fpsEl.textContent = Math.round(frames * 1000 / (now - fpsT)); frames = 0; fpsT = now; }
    if (visible && !document.hidden) requestAnimationFrame(frame); else running = false;
  }
  document.addEventListener("visibilitychange", () => { if (!document.hidden) kick(); });

  // ---------- sound: all made live in the browser, no sound files ----------
  const SND = { on: true, ctx: null, acc: 0, px: null, py: 0, pf: 0, air: false };
  function sndStart() {
    if (!SND.on) return;
    if (SND.ctx) { if (SND.ctx.state === "suspended") SND.ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return;
    let a; try { a = SND.ctx = new AC(); } catch (e) { return; }
    const out = a.createGain(); out.gain.value = 0.9; out.connect(a.destination); SND.out = out;
    const mk = (f) => { const b = a.createBuffer(1, a.sampleRate * 2, a.sampleRate), d = b.getChannelData(0); let l = 0; for (let i = 0; i < d.length; i++) { const w = Math.random() * 2 - 1; d[i] = f ? (l = (l + 0.02 * w) / 1.02) * 3.5 : w; } return b; };
    SND.noise = mk(0); const brown = mk(1);
    const loop = (buf, type, freq, vol) => { const s = a.createBufferSource(); s.buffer = buf; s.loop = true; const f = a.createBiquadFilter(); f.type = type; f.frequency.value = freq; const g = a.createGain(); g.gain.value = vol; s.connect(f); f.connect(g); g.connect(out); s.start(0, Math.random()); return g; };
    loop(brown, "lowpass", 220, 0.035); // room tone
    SND.waves = loop(brown, "lowpass", 520, 0); // the harbour
    SND.fire = loop(brown, "bandpass", 300, 0); // the fire's low roar
    SND.tank = loop(SND.noise, "bandpass", 3800, 0); // aquarium filter
    SND.nextCrack = 0; SND.nextGull = a.currentTime + 12; SND.nextHorn = a.currentTime + 45; SND.nextBub = 0; SND.nextPop = 0;
  }
  function blip(f, t0, dur, vol, type, f2) { const a = SND.ctx, o = a.createOscillator(), g = a.createGain(); o.type = type || "sine"; o.frequency.setValueAtTime(f, t0); if (f2) o.frequency.exponentialRampToValueAtTime(f2, t0 + dur); g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(vol, t0 + 0.012); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur); o.connect(g); g.connect(SND.out); o.start(t0); o.stop(t0 + dur + 0.05); }
  function hiss(t0, dur, vol, type, freq, q) { const a = SND.ctx, s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain(); s.buffer = SND.noise; f.type = type; f.frequency.value = freq; f.Q.value = q || 1; g.gain.setValueAtTime(vol, t0); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur); s.connect(f); f.connect(g); g.connect(SND.out); s.start(t0, Math.random() * 1.5); s.stop(t0 + dur + 0.05); }
  const RUGS = [[14.6, 1.6, 23.4, 8.0], [1.6, 13.4, 4.6, 17.2]];
  const surface = () => (P.y > 20.1 ? "deck" : P.f > 0.3 ? "wood" : RUGS.some((r) => P.x > r[0] && P.x < r[2] && P.y > r[1] && P.y < r[3]) ? "rug" : "floor");
  function footstep(kind, hard) {
    const t = SND.ctx.currentTime, k = hard ? 1.5 : (keys.run ? 0.6 : 0.8);
    if (kind === "rug") hiss(t, 0.06, 0.05 * k, "lowpass", 500, 1);
    else if (kind === "deck") { hiss(t, 0.08, 0.16 * k, "lowpass", 520, 1); blip(120, t, 0.12, 0.08 * k); }
    else if (kind === "wood") { hiss(t, 0.07, 0.2 * k, "lowpass", 700, 1); blip(170, t, 0.09, 0.07 * k); }
    else { hiss(t, 0.06, 0.13 * k, "lowpass", 900, 1); blip(210, t, 0.06, 0.04 * k); }
  }
  function squeak(vol) { const t = SND.ctx.currentTime; blip(850, t, 0.45, 0.035 * vol, "triangle", 1500); blip(1500, t + 0.25, 0.35, 0.025 * vol, "triangle", 1100); }
  function setSound(on) {
    SND.on = on; const b = document.getElementById("snd"); if (b) { b.setAttribute("aria-pressed", on ? "true" : "false"); b.textContent = on ? "Sound: On" : "Sound: Off"; }
    if (on) sndStart(); else { if (SND.ctx) SND.ctx.suspend(); try { window.speechSynthesis && speechSynthesis.cancel(); } catch (e) {} }
  }
  document.addEventListener("click", (e) => { if (e.target.closest("#snd")) setSound(!SND.on); });
  ["pointerdown", "keydown"].forEach((ev) => document.addEventListener(ev, (e) => { if (e.target && e.target.closest && e.target.closest("#snd")) return; sndStart(); }, { passive: true }));

  // =====================================================================
  //  THE LOUNGE IS ALIVE: fire, fish, Gerald, bubbles, popcorn, the parrot, Gary, Dusty, the boat, the lighthouse
  // =====================================================================
  const FUN = { remote: 0, chestT: 9, chest: 0, horn: 0 };
  const PARROT_LINES = ["Squawk! Who took the remote?", "Squawk! Free popcorn! Free popcorn!", "Squawk! Don't tap the glass!", "Squawk! Gerald is watching you.", "Squawk! Pickles wants a cracker. Pickles wants a sofa.", "Squawk! Gary is a thief!", "Squawk! The eight ball is shy.", "Squawk! Pretty bird. Pretty bird. That's me.", "Squawk! Mind the slide!", "Squawk! Not the jukebox again!"];
  function squawk() { if (!sndOn()) return; const t = now(); blip(1400, t, 0.12, 0.06, "sawtooth", 2200); blip(2000, t + 0.1, 0.16, 0.05, "sawtooth", 1300); }
  function parrotSpeak(line) {
    squawk(); PARROT.t = 1.6;
    if (!SND.on || !window.speechSynthesis || !window.SpeechSynthesisUtterance) return;
    setTimeout(() => { try { const u = new SpeechSynthesisUtterance(line.replace(/^Squawk! /, "")); u.pitch = 2; u.rate = 1.15; u.volume = 0.8; speechSynthesis.cancel(); speechSynthesis.speak(u); } catch (e) {} }, 250);
    showMsg("Captain Pickles: “" + line + "”");
  }
  function gullCall() { if (!sndOn()) return; const t = now(); [0, 0.32, 0.6].forEach((at, i) => blip(1500 - i * 120, t + at, 0.28, 0.04, "sawtooth", 900)); }
  function foghorn() { if (!sndOn()) return; const t = now(); blip(82, t, 3.2, 0.12, "sawtooth", 78); blip(123, t, 3.2, 0.05, "sawtooth", 117); }
  // Gary flies to another post (or right away with your fish)
  function gullFly(steal) {
    const from = GULL_SPOTS[GULL.i]; let i = GULL.i; while (i === GULL.i) i = Math.floor(Math.random() * GULL_SPOTS.length);
    GULL.from = from; GULL.to = GULL_SPOTS[i]; GULL.i = i; GULL.fly = 0.0001; GULL.steal = !!steal; gullCall();
  }
  function dustyFace(mood) { const g = DUSTY.fc.getContext("2d"); g.fillStyle = "#3FE0E8"; g.fillRect(0, 0, 64, 64); g.fillStyle = "#0B0D1A"; g.beginPath(); g.arc(22, 26, 5, 0, 7); g.arc(42, 26, 5, 0, 7); g.fill(); g.lineWidth = 4; g.strokeStyle = "#0B0D1A"; g.beginPath(); if (mood === "sad") g.arc(32, 50, 10, Math.PI * 1.15, Math.PI * 1.85); else g.arc(32, 36, 10, Math.PI * 0.15, Math.PI * 0.85); g.stroke(); DUSTY.ft.needsUpdate = true; DUSTY.mood = mood; }
  dustyFace("happy");
  let scrAcc = 0;
  function liveFrame(dt, ms) {
    const s = ms / 1000, walking = mode === "walk" && !game, px = P.x, py = P.y, ok = sndOn();
    AREA_LIGHTS.forEach((l) => (l.visible = pretty));
    // fire
    FLAMES.forEach((f, i) => { f.scale.set(1 + Math.sin(s * 7 + i) * 0.08, 1 + Math.sin(s * 9.3 + i * 2) * 0.14 + Math.sin(s * 23 + i) * 0.05, 1); f.position.y = 0.48 + f.scale.y * 0.02; });
    fireL.intensity = 2.2 + Math.sin(s * 11) * 0.25 + Math.sin(s * 23.7) * 0.15 + (Math.random() - 0.5) * 0.2;
    // the big screen (redrawn often while you watch it, slowly otherwise)
    scrAcc += dt; const scrEvery = game === SOFAG || SCRS.mode === "movie" || SCRS.mode === "quiz" ? 1 / 30 : 1 / 8; if (scrAcc >= scrEvery) { drawScreen(scrAcc); scrAcc = 0; }
    quizFrame(dt);
    // aquarium: plants sway, bubbles rise, fish swim (and come to the glass to look at you), Gerald puffs up if you jump next to him
    PLANTS.forEach((p) => (p.m.rotation.z = Math.sin(s * 1.1 + p.ph) * 0.18));
    BUB.forEach((b) => { b.m.position.y += b.sp * dt; if (b.m.position.y > 3.32) b.m.position.y = 0.75; b.m.position.x = WX(35.45) + Math.sin(s * 3 + b.sp * 20) * 0.03 + (b.m.position.x - WX(35.45)) * 0.999; });
    const byTank = walking && px > 32.4 && py > 0.6 && py < 12.4 && P.f < 0.5;
    FISH.forEach((f) => {
      const watch = byTank && Math.abs(f.y - py) < 2.6;
      if (watch) { f.y += Math.sign(py - f.y) * Math.min(Math.abs(py - f.y), dt * 0.5); f.x += (35.12 - f.x) * Math.min(1, dt * 1.5); f.z += (Math.min(3.1, Math.max(0.95, P.eye - 0.3)) - f.z) * Math.min(1, dt * 0.8); }
      else { f.y += f.vy * dt; if (f.y < 1.25 || f.y > 11.75) { f.vy *= -1; f.y = Math.max(1.25, Math.min(11.75, f.y)); } f.x += (35.35 + Math.sin(s * 0.3 + f.ph) * 0.2 - f.x) * Math.min(1, dt * 0.5); f.z = Math.max(0.9, Math.min(3.2, f.z + Math.sin(s * 0.5 + f.ph) * dt * 0.08)); }
      f.g.position.set(WX(f.x), f.z, WZ(f.y)); { const want = watch ? Math.PI / 2 : (f.vy > 0 ? Math.PI : 0); let d = want - f.g.rotation.y; while (d > Math.PI) d -= Math.PI * 2; while (d < -Math.PI) d += Math.PI * 2; f.g.rotation.y += d * Math.min(1, dt * 4); } f.tail.rotation.y = Math.sin(s * 9 + f.ph) * 0.5;
    });
    const nearG = walking && px > 32.6 && Math.abs(py - GERALD.y) < 2.2;
    if (nearG && P.air && GERALD.puff < 0.3) { GERALD.puff = 1; if (ok) { blip(300, now(), 0.35, 0.08, "sine", 900); hiss(now(), 0.3, 0.1, "bandpass", 1800, 2); } showMsg("You startled Gerald. Gerald is now a balloon."); }
    GERALD.puff = Math.max(0, GERALD.puff - dt * 0.12); const ps = 1 + Math.min(1, GERALD.puff * 1.6) * 1.3; GERALD.body.scale.setScalar(0.07 * ps);
    GERALD.g.position.y = GERALD.z + Math.sin(s * 1.3) * 0.05; GERALD.g.position.x = WX(GERALD.x) + Math.sin(s * 0.4) * 0.05;
    // the treasure chest opens every so often and lets out a big burst of bubbles
    FUN.chestT -= dt; if (FUN.chestT <= 0) { FUN.chest = 3; FUN.chestT = 22 + Math.random() * 10; BUB.forEach((b, i) => { if (i % 3 === 1) b.m.position.set(WX(35.55 + (Math.random() - 0.5) * 0.2), 0.95 + Math.random() * 0.15, WZ(6.5 + (Math.random() - 0.5) * 0.3)); }); if (ok && byTank) [0, 0.1, 0.2, 0.35].forEach((at) => blip(500 + Math.random() * 400, now() + at, 0.12, 0.03, "sine", 1400)); }
    FUN.chest = Math.max(0, FUN.chest - dt); const lidA = FUN.chest > 0 ? Math.min(1, FUN.chest, 3 - FUN.chest) : 0; chestLid.rotation.z = -lidA * 0.9; coin.visible = lidA > 0.4;
    // popcorn machine
    POP.forEach((p) => { if (p.t < 0) { if (Math.random() < dt * 1.6) { p.t = 0; p.x = 35.3 + (Math.random() - 0.5) * 0.1; p.y = 16.1 + (Math.random() - 0.5) * 0.1; p.z = 1.5; p.vx = (Math.random() - 0.5) * 0.6; p.vy = (Math.random() - 0.5) * 0.6; p.vz = 0.8 + Math.random() * 0.6; p.m.visible = true; if (ok && walking && Math.hypot(px - 35.3, py - 16.1) < 6 && Math.random() < 0.6) blip(900 + Math.random() * 500, now(), 0.03, 0.02, "triangle"); } return; }
      p.t += dt; p.vz -= 6 * dt; p.x = Math.max(35.08, Math.min(35.52, p.x + p.vx * dt)); p.y = Math.max(15.88, Math.min(16.32, p.y + p.vy * dt)); p.z += p.vz * dt; if (p.z < 1.27) { p.z = 1.27; p.vz = 0; p.vx *= 0.5; p.vy *= 0.5; } if (p.t > 4) { p.t = -1; p.m.visible = false; } p.m.position.set(WX(p.x), p.z, WZ(p.y)); });
    // Captain Pickles: bobs his head, says something when you come over
    PARROT.t = Math.max(0, PARROT.t - dt); PARROT.head.position.y = 0.29 + (PARROT.t > 0 ? Math.abs(Math.sin(s * 14)) * 0.03 : Math.sin(s * 2) * 0.006);
    PARROT.cd -= dt; const dP = walking ? Math.hypot(px - PARROT.x, py - PARROT.y) : 99;
    if (parrotNext && (dP < 9 || game) && PARROT.cd < 6) { parrotSpeak(parrotNext); parrotNext = null; PARROT.cd = 8; }
    else if (dP < 2.6 && PARROT.cd <= 0) { parrotSpeak(PARROT_LINES[Math.floor(Math.random() * PARROT_LINES.length)]); PARROT.cd = 14; }
    // Gary the seagull
    GULL.cd -= dt; const gs = GULL_SPOTS[GULL.i];
    if (!GULL.fly && walking && py > 19.8 && Math.hypot(px - gs[0], py - gs[1]) < 2.2 && GULL.cd <= 0) { GULL.cd = 4; gullFly(false); }
    if (GULL.fly) { GULL.fly += dt / (GULL.steal ? 4 : 2.6); const u = Math.min(1, GULL.fly), a = GULL.from, b = GULL.to; GULL.g.position.set(WX(a[0] + (b[0] - a[0]) * u), 1.08 + Math.sin(u * Math.PI) * (GULL.steal ? 6 : 2.4), WZ(a[1] + (b[1] - a[1]) * u)); GULL.g.rotation.y = Math.atan2(-(b[0] - a[0]), -(b[1] - a[1])); GULL.wings.forEach((w, i) => (w.rotation.z = (i ? -1 : 1) * Math.sin(s * 22) * 0.7)); if (u >= 1) { GULL.fly = 0; GULL.wings.forEach((w) => (w.rotation.z = 0)); GULL.g.rotation.y = Math.PI; } }
    else GULL.g.position.y = 1.08 + (Math.sin(s * 0.7) > 0.97 ? 0.02 : 0);
    // Dusty the robot vacuum: wanders, bumps into things, sometimes gets stuck and looks sad
    DUSTY.t -= dt;
    if (DUSTY.stuck > 0) { DUSTY.stuck -= dt; DUSTY.a += Math.sin(s * 6) * dt * 2; if (DUSTY.stuck <= 0) dustyFace("happy"); else if (ok && Math.random() < dt * 0.8 && walking && Math.hypot(px - DUSTY.x, py - DUSTY.y) < 6) blip(660, now(), 0.15, 0.03, "square", 440); }
    else { const sp = 0.35 * dt, nx = DUSTY.x - Math.sin(DUSTY.a) * sp, ny = DUSTY.y - Math.cos(DUSTY.a) * sp, you = walking && Math.hypot(nx - px, ny - py) < 0.5;
      const blocked = you || !(nx > 0.5 && nx < 35.5 && ny > 0.5 && ny < 19.5) || near(nx, ny).some((b) => b.b < 0.12 && nx + 0.18 > b.x1 && nx - 0.18 < b.x2 && ny + 0.18 > b.y1 && ny - 0.18 < b.y2);
      if (blocked) { DUSTY.a += 1.2 + Math.random() * 2; if (ok && walking && Math.hypot(px - DUSTY.x, py - DUSTY.y) < 5) blip(you ? 1200 : 420, now(), 0.06, 0.03, "square"); if (you) showMsg("Dusty: “Boop. Excuse me.”"); if (Math.random() < 0.06) { DUSTY.stuck = 6; dustyFace("sad"); } }
      else { DUSTY.x = nx; DUSTY.y = ny; } }
    DUSTY.g.position.set(WX(DUSTY.x), 0, WZ(DUSTY.y)); DUSTY.g.rotation.y = DUSTY.a;
    // the sea friends: little bobs, and a hop when they answer a quiz question
    NPC.forEach((n, i) => { n.hop = Math.max(0, (n.hop || 0) - dt); n.h.position.y = n.f + Math.abs(Math.sin(n.hop * 6)) * 0.12 * (n.hop > 0 ? 1 : 0); n.h.rotation.y = n.base + Math.sin(s * 0.5 + i) * 0.15; n.h.scale.y = 0.95 * (1 + Math.sin(s * 1.6 + i) * 0.015); });
    // outside: the boat bobs, the water moves, the lighthouse turns
    BOAT.position.y = -0.75 + Math.sin(s * 0.9) * 0.05; BOAT.rotation.z = Math.sin(s * 0.7) * 0.03; BOAT.rotation.x = Math.sin(s * 0.55 + 1) * 0.02;
    waterTex.offset.set(s * 0.004, s * 0.006); BEAM.rotation.y = s * 0.45;
    if (window.LG_GLIT) window.LG_GLIT.material.opacity = 0.45 + Math.sin(s * 1.7) * 0.1;
    // the jukebox lights dance to the beat
    if (MUS.i >= 0) { const b = Math.max(0, 1 - (SND.ctx ? (SND.ctx.currentTime - MUS.beat) * 3 : 1)); jukeL.intensity = pretty ? 0.4 + b * 1.4 : 0; jukeL.color.setHSL((s * 0.08) % 1, 0.7, 0.6); } else jukeL.intensity = 0;
    // found the remote!
    if (game === SOFAG && !FUN.remote) { FUN.remote = 1; setTimeout(() => showMsg("You found the TV remote down the side of the sofa. It's been there since Tuesday."), 900); say("Squawk! The remote! The remote!"); }
    musicFrame();
    sndFrame(dt);
  }
  function sndFrame(dt) {
    const a = SND.ctx; if (!a || !SND.on || a.state !== "running") return;
    const t = a.currentTime, bird = mode === "bird", lx = bird ? 18 : P.x, ly = bird ? 12 : P.y, out = ly > 20.1;
    // waves louder outside, the fire louder close to it, the aquarium filter by the tank
    const dFire = Math.hypot(lx - 1.0, ly - 15.3), dTank = Math.max(0, 35 - lx);
    SND.waves.gain.setTargetAtTime((out ? 0.22 : 0.05) * (0.8 + 0.2 * Math.sin(t * 0.6)), t, 0.4);
    SND.fire.gain.setTargetAtTime(0.12 / (1 + dFire * dFire * 0.15), t, 0.3);
    SND.tank.gain.setTargetAtTime(ly < 13 ? 0.012 / (1 + dTank * dTank * 0.3) : 0, t, 0.3);
    if (t > SND.nextCrack) { const v = 0.25 / (1 + dFire * dFire * 0.2); if (v > 0.004) hiss(t, 0.02 + Math.random() * 0.04, v, "highpass", 2000 + Math.random() * 3000, 1); SND.nextCrack = t + 0.08 + Math.random() * 0.5; }
    if (t > SND.nextBub) { if (dTank < 4 && ly < 13) blip(400 + Math.random() * 600, t, 0.08, 0.012 / (1 + dTank), "sine", 1200 + Math.random() * 800); SND.nextBub = t + 0.3 + Math.random() * 1.2; }
    if (t > SND.nextGull) { if (out || Math.random() < 0.3) gullCall(); SND.nextGull = t + 14 + Math.random() * 20; }
    if (t > SND.nextHorn) { foghorn(); SND.nextHorn = t + 90 + Math.random() * 60; }
    if (bird || mode !== "walk" || game) { SND.px = null; return; }
    if (SND.px === null) { SND.px = P.x; SND.py = P.y; SND.pf = P.f; SND.air = P.air; }
    const moved = Math.hypot(P.x - SND.px, P.y - SND.py), kind = surface();
    // about 2 steps a second walking and 3.5 running
    if (!P.air) { SND.acc += moved; if (SND.acc > (keys.run ? 2.0 : 1.7) * (P.H < 1 ? 0.55 : 1)) { SND.acc = 0; footstep(kind); } }
    if (SND.air && !P.air) footstep(kind, true);
    let onSlide = false; for (const z of (window.WH_SLIDES || [])) if (P.f > 0.3 && P.x > z.x1 - 0.05 && P.x < z.x2 + 0.05 && P.y > z.y1 - 0.05 && P.y < z.y2) onSlide = true;
    if (onSlide && !SND.sl) { blip(500, t, 0.9, 0.06, "sine", 1300); hiss(t, 0.9, 0.12, "bandpass", 1800, 0.8); if (!FUN.slid) { FUN.slid = 1; say("Squawk! Wheee! Wheee!"); } } SND.sl = onSlide;
    SND.px = P.x; SND.py = P.y; SND.pf = P.f; SND.air = P.air;
  }

  // =====================================================================
  //  BLUEPRINT (drawn from this same 3D model, straight down) and the mini map
  // =====================================================================
  const MAP = { x0: -1, x1: 37, y0: -1, y1: 34 };
  const LABELS = [["Big screen", 19, 1.3, 15], ["Sofa", 19, 6.3, 13], ["Bar stools", 19, 9.3, 12], ["Pool", 19, 12.8, 13], ["Air hockey", 28.4, 11.7, 12], ["Four in a row", 28.7, 15.0, 12], ["Jukebox", 27.2, 1.6, 12],
    ["Crow's Nest loft (upstairs)", 4.2, 0.9, 13, "#F5C04A"], ["Darts", 3.0, 3.6, 12], ["Card table", 5.6, 5.4, 11], ["Stairs", 1.7, 9.7, 11], ["Slide", 6.0, 10.0, 11], ["Fireplace", 3.0, 13.0, 12], ["Shuffleboard", 9.6, 17.6, 12],
    ["Aquarium wall", 33.4, 7.0, 12], ["Snack bar", 33.6, 13.2, 12], ["Front door", 31.5, -0.4, 12, "#3FE0E8"], ["Deck", 12, 23.2, 14], ["Pier", 29.6, 30.0, 13], ["Fishing spot", 27, 33.4, 11], ["Boat", 31.8, 27.4, 11], ["Viewer", 33.6, 25.2, 11]];
  const mapC = document.getElementById("map"), mini = document.getElementById("mini");
  let mapImg = null;
  function drawBlueprint() {
    if (!mapC) return;
    const W = mapC.width, H = mapC.height, sx = W / (MAP.x1 - MAP.x0);
    const px = (x) => (x - MAP.x0) * sx, py = (y) => (y - MAP.y0) * sx;
    const save = { ceil: G.ceil.visible, fog: scene.fog };
    G.ceil.visible = false; scene.fog = null;
    const hw = (MAP.x1 - MAP.x0) / 2, hh = (MAP.y1 - MAP.y0) / 2;
    const oc = new T.OrthographicCamera(-hw, hw, hh, -hh, 1, 80);
    oc.up.set(0, 0, -1); oc.position.set(WX((MAP.x0 + MAP.x1) / 2), 40, WZ((MAP.y0 + MAP.y1) / 2)); oc.lookAt(WX((MAP.x0 + MAP.x1) / 2), 0, WZ((MAP.y0 + MAP.y1) / 2));
    const off = document.createElement("canvas"); off.width = W; off.height = H;
    let r2; try { r2 = new T.WebGLRenderer({ canvas: off, antialias: true, preserveDrawingBuffer: true }); } catch (e) { r2 = null; }
    const g = mapC.getContext("2d"); g.fillStyle = "#12152A"; g.fillRect(0, 0, W, H);
    if (r2) { r2.setPixelRatio(1); r2.setSize(W, H, false); r2.outputEncoding = T.sRGBEncoding; r2.toneMapping = T.ACESFilmicToneMapping; r2.toneMappingExposure = 1.5; r2.setClearColor(0x0b2440); r2.render(scene, oc); g.drawImage(off, 0, 0); r2.dispose(); try { r2.forceContextLoss(); } catch (e) {} }
    G.ceil.visible = save.ceil; scene.fog = save.fog;
    g.fillStyle = "rgba(11,13,26,.12)"; g.fillRect(0, 0, W, H);
    g.setLineDash([8, 6]); g.strokeStyle = "#F5C04A"; g.lineWidth = 2; g.strokeRect(px(0.15), py(0.15), px(8.15) - px(0.15), py(7.15) - py(0.15)); g.setLineDash([]);
    LABELS.forEach(([t, x, y, size, col]) => {
      g.font = `700 ${size}px ${FONT}`; g.textAlign = "left"; g.textBaseline = "middle"; const w = g.measureText(t).width, X = px(x) - w / 2;
      g.fillStyle = "rgba(11,13,26,.82)"; g.beginPath(); g.roundRect ? g.roundRect(X - 6, py(y) - size * 0.75, w + 12, size * 1.5, 6) : g.rect(X - 6, py(y) - size * 0.75, w + 12, size * 1.5); g.fill();
      g.fillStyle = col || "#ECEEFB"; g.fillText(t, X, py(y));
    });
    // a little "press E" dot at every game
    STATIONS.forEach((st) => { g.fillStyle = "#3FE0E8"; g.beginPath(); g.arc(px(st.x), py(st.y), 9, 0, 7); g.fill(); g.fillStyle = "#0B0D1A"; g.font = `800 11px ${FONT}`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(String(st.n), px(st.x), py(st.y) + 1); });
    mapImg = mapC;
  }
  function drawMini() {
    if (!mini || !mapImg) return;
    const g = mini.getContext("2d"), W = mini.width, H = mini.height, s = mapImg.width / (MAP.x1 - MAP.x0);
    if (mode === "bird") { g.fillStyle = "#12152A"; g.fillRect(0, 0, W, H); const k = Math.min(W / mapImg.width, H / mapImg.height); g.drawImage(mapImg, (W - mapImg.width * k) / 2, (H - mapImg.height * k) / 2, mapImg.width * k, mapImg.height * k); return; }
    const span = 16, sw = span * s, sh = sw * H / W;
    let cx = (P.x - MAP.x0) * s, cy = (P.y - MAP.y0) * s; const ocx = cx, ocy = cy;
    cx = Math.max(sw / 2, Math.min(mapImg.width - sw / 2, cx)); cy = Math.max(sh / 2, Math.min(mapImg.height - sh / 2, cy));
    g.fillStyle = "#12152A"; g.fillRect(0, 0, W, H);
    g.drawImage(mapImg, cx - sw / 2, cy - sh / 2, sw, sh, 0, 0, W, H);
    g.save(); g.translate(W / 2 + (ocx - cx) * W / sw, H / 2 + (ocy - cy) * W / sw); g.rotate(-yaw);
    g.fillStyle = "rgba(63,224,232,.25)"; g.beginPath(); g.moveTo(0, 0); g.arc(0, 0, 26, -Math.PI / 2 - 0.6, -Math.PI / 2 + 0.6); g.closePath(); g.fill();
    g.fillStyle = "#3FE0E8"; g.beginPath(); g.moveTo(0, -8); g.lineTo(6, 6); g.lineTo(-6, 6); g.closePath(); g.fill();
    g.restore();
  }

  // =====================================================================
  //  THE CHECKS (run live in this page)
  // =====================================================================
  // 1. nothing floating, nothing merged into something else
  function boxCheck() {
    const L = ALLB.filter((b) => b.g !== "out").map((b) => ({ x1: b.x1, y1: b.y1, x2: b.x2, y2: b.y2, b: b.b, t: b.t, nm: b.nm || b.c, ok: b.ok, k: b.k })).concat(PARTS.map((p) => ({ ...p, part: 1 })));
    const flat = (a) => Math.min(a.x2 - a.x1, a.y2 - a.y1, a.t - a.b) <= 0.012;
    const CS = 2, grid = new Map();
    L.forEach((a, i) => { for (let x = Math.floor(a.x1 / CS); x <= Math.floor(a.x2 / CS); x++) for (let y = Math.floor(a.y1 / CS); y <= Math.floor(a.y2 / CS); y++) { const k = x * 1000 + y; (grid.get(k) || grid.set(k, []).get(k)).push(i); } });
    const ov = (a, b) => [Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1), Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1), Math.min(a.t, b.t) - Math.max(a.b, b.b)];
    const adj = L.map(() => []), merged = [];
    L.forEach((a, i) => {
      const seen = new Set();
      for (let x = Math.floor(a.x1 / CS); x <= Math.floor(a.x2 / CS); x++) for (let y = Math.floor(a.y1 / CS); y <= Math.floor(a.y2 / CS); y++) for (const j of grid.get(x * 1000 + y) || []) {
        if (j <= i || seen.has(j)) continue; seen.add(j); const b = L[j], o = ov(a, b);
        if (o[0] > -0.003 && o[1] > -0.003 && o[2] > -0.003 && o.filter((q) => q > 0.003).length >= 2) { adj[i].push(j); adj[j].push(i); }
        if (o[0] > 0.004 && o[1] > 0.004 && o[2] > 0.004 && !flat(a) && !flat(b) && !a.ok && !b.ok) merged.push([a.nm, b.nm, o.map((q) => +q.toFixed(3)), [+((Math.max(a.x1, b.x1) + Math.min(a.x2, b.x2)) / 2).toFixed(2), +((Math.max(a.y1, b.y1) + Math.min(a.y2, b.y2)) / 2).toFixed(2), +Math.max(a.b, b.b).toFixed(2)]]);
      }
    });
    const held = L.map((a) => a.b <= 0.005), q = []; held.forEach((h, i) => h && q.push(i));
    for (let qi = 0; qi < q.length; qi++) for (const j of adj[q[qi]]) if (!held[j]) { held[j] = true; q.push(j); }
    const floating = L.map((a, i) => [a, i]).filter(([a, i]) => !held[i] && !a.ok).map(([a]) => [a.nm, [a.x1, a.y1, a.b].map((v) => +v.toFixed(2))]);
    return { n: L.length, floating, merged };
  }
  // 2. everywhere a player can get to: flood out from the front door
  let REACHED = null;
  function reach(stepSz) {
    const S = stepSz || 0.25, JH = JUMP_V * JUMP_V / (2 * GRAV), seen = new Map(), q = [];
    const fitH = (x, y, f) => [1.7, 0.9, 0.6].find((H) => !hitAt(x, y, f, H, null, f + STEP)) || 0;
    const add = (x, y, f) => { const k = Math.round(x / S) + "," + Math.round(y / S) + "," + Math.round(f * 10); if (seen.has(k)) return; const h = fitH(x, y, f); if (!h) return; seen.set(k, [x, y, f, h]); q.push(k); };
    add(SPAWN.x, SPAWN.y, 0);
    for (let qi = 0; qi < q.length; qi++) {
      const [x, y, f] = seen.get(q[qi]);
      for (const [dx, dy] of [[S, 0], [-S, 0], [0, S], [0, -S]]) {
        const nx = +(x + dx).toFixed(3), ny = +(y + dy).toFixed(3);
        if (!inBounds(nx, ny)) continue;
        for (const [up, jmp] of [[f + STEP, 0], [Math.min(f + STEP + JH, Math.max(f + STEP, 1.25)), 1]]) {
          const s2 = supportAt(nx, ny, f, up), f2 = Math.max(f, s2);
          if (hitAt(nx, ny, f2, 0.6, null, jmp ? up : f2 + STEP)) continue;
          add(nx, ny, s2);
        }
      }
    }
    return seen;
  }
  function reachCheck() {
    if (!REACHED) REACHED = reach(0.25);
    let top = 0, edge = 0, tops = {}; const edges = [];
    REACHED.forEach(([x, y, f]) => { if (f > top) top = f; tops[f.toFixed(2)] = (tops[f.toFixed(2)] || 0) + 1; if ([[0.3, 0], [-0.3, 0], [0, 0.3], [0, -0.3]].some(([dx, dy]) => !inBounds(x + dx, y + dy) && !hitAt(x + dx, y + dy, f, 0.6, null, f + STEP))) { edge++; if (edges.length < 8) edges.push([x, y, f]); } });
    const st = STATIONS.map((s) => { let ok = false; REACHED.forEach(([x, y, f]) => { if (!ok && Math.hypot(x - (s.px || s.x), y - (s.py || s.y)) < 0.35 && Math.abs(f - s.f) < 0.3) ok = true; }); return [s.label, ok]; });
    const loftOK = [...REACHED.values()].some(([x, y, f]) => Math.abs(f - LF) < 0.01);
    return { n: REACHED.size, top: +top.toFixed(2), edge, edges, stations: st, loftOK, tops };
  }
  // 3. walk tests: the real walking code follows these paths
  function sim(path) {
    P.x = path[0][0]; P.y = path[0][1]; P.f = path[0][2] || 0; P.H = 1.7; P.vy = 0; crouchHeld = false;
    for (let i = 1; i < path.length; i++) {
      const [tx, ty] = path[i]; let guard = 0;
      while (Math.hypot(tx - P.x, ty - P.y) > 0.04 && guard++ < 5000) {
        const d = Math.hypot(tx - P.x, ty - P.y), st = Math.min(0.08, d);
        if (!tryMove(P.x + (tx - P.x) / d * st, P.y + (ty - P.y) / d * st)) return { ok: false, seg: i, at: [P.x, P.y, P.f, P.H].map((q) => +q.toFixed(2)) };
        physics(0.03);
      }
      for (let k = 0; k < 30; k++) physics(0.03);
    }
    return { ok: true, at: [P.x, P.y, P.f, P.H].map((q) => +q.toFixed(2)) };
  }
  const WALKS = {
    "Door to the pier and back": [[31.5, 1.7], [29.4, 3.4], [29.2, 8.4], [25.3, 9.2], [25.3, 13.6], [23.8, 18.6], [23.8, 22.0], [27.0, 23.2], [27.0, 32.4]],
    "Up the stairs to the loft": [[2.0, 13.0], [0.75, 12.8], [0.75, 6.6], [3.0, 5.0]],
    "Down the slide": [[6.0, 6.6, LF], [6.0, 7.4], [6.0, 13.4]],
    "The whole auto tour": tourPts,
  };
  function walkCheck() { const s = { x: P.x, y: P.y, f: P.f, H: P.H }; const out = Object.entries(WALKS).map(([k, p]) => { const r = sim(p); if (k === "Up the stairs to the loft" && Math.abs(P.f - LF) > 0.05) r.ok = false; if (k === "Down the slide" && P.f > 0.05) r.ok = false; return [k, r]; }); Object.assign(P, s); P.vy = 0; SAFE.x = P.x; SAFE.y = P.y; SAFE.f = P.f; return out; }
  // 4. seats: enough of them, none shared, all reachable
  function seatCheck() { if (!REACHED) REACHED = reach(0.25); let minD = 1e9; SEATS.forEach((a, i) => SEATS.forEach((b, j) => { if (j > i && Math.abs(a.f - b.f) < 1) minD = Math.min(minD, Math.hypot(a.x - b.x, a.y - b.y)); }));
    const R2 = [...REACHED.values()], cant = SEATS.filter((q) => !R2.some(([x, y, f]) => Math.hypot(x - q.x, y - q.y) < 1.1 && Math.abs(f - q.f) < 0.75)).map((q) => [q.what, q.x, q.y]);
    return { n: SEATS.length, taken: SEATS.filter((q) => q.by).length, minD: +minD.toFixed(2), cant }; }
  window.WH = { seatCheck, SEATS, sitDown, FR, PS, boxCheck, reach, reachCheck, walkCheck, sim, renderer, scene, P, SOL, ALLB, PARTS, STATIONS, supportAt, hitAt, tryMove, inBounds, goPlace, setMode, startGame, stopGame, get game() { return game; }, step: (k, ms) => { keys[k] = 1; setTimeout(() => (keys[k] = 0), ms); }, get yaw() { return yaw; }, set yaw(v) { yaw = v; }, set pitch(v) { pitch = v; }, cam: camera, SCRS, BALLS, HS, render: () => renderer.render(scene, camera) };
  function runChecks() {
    const put = (id, html, bad) => { const el = document.getElementById(id); if (el) { el.innerHTML = html; el.classList.toggle("bad", !!bad); } };
    setTimeout(() => {
      const b = boxCheck();
      put("chkFloat", b.floating.length === 0 ? `Checked ${b.n.toLocaleString()} pieces: <b>none floating</b>. Every piece sits on the floor or on something that does.` : `Checked ${b.n.toLocaleString()} pieces: <b>${b.floating.length} floating</b> (${b.floating.slice(0, 4).map((f) => f[0]).join(", ")}).`, b.floating.length);
      put("chkMerge", b.merged.length === 0 ? `Checked every pair of pieces: <b>none pushed inside another</b>.` : `<b>${b.merged.length} pieces pushed into each other</b> (${b.merged.slice(0, 4).map((m) => m[0] + " + " + m[1]).join(", ")}).`, b.merged.length);
      setTimeout(() => {
        const r = reachCheck(), miss = r.stations.filter((s) => !s[1]);
        put("chkReach", `Flooded out from the front door: <b>${r.n.toLocaleString()}</b> places a player can stand. Highest is <b>${r.top} m</b> (the loft floor is ${LF} m), so nobody gets onto shelves, railings or the roof. Places right against the edge of the map: <b>${r.edge}</b>.`, r.edge > 0 || r.top > LF + 0.01);
        put("chkGames", miss.length === 0 ? `All <b>${r.stations.length}</b> games and seats can be walked to.` : `<b>${miss.length}</b> can't be reached: ${miss.map((m) => m[0]).join(", ")}.`, miss.length);
        const sc = seatCheck();
        put("chkSeats", `<b>${sc.n}</b> seats. The closest two are <b>${sc.minD} m</b> apart, so no two people share a spot. ${sc.taken} are taken by the sea friends in this preview, and the game says so if you try them. ${sc.cant.length ? "<b>" + sc.cant.length + " can't be reached.</b>" : "All of them can be walked to."}`, sc.cant.length || sc.minD < 0.5);
        const w = walkCheck();
        put("chkWalk", w.map(([k, r]) => `${r.ok ? "✓" : "✗"} ${k}`).join("<br>"), w.some(([, r]) => !r.ok));
        window.WH.results = { box: b, reach: r, walks: w, seats: sc };
      }, 60);
    }, 500);
  }

  // ---------- the other people in this lobby (the Lobby page tells us where they are) ----------
  const PEOPLE = new Map();
  SEATS.forEach((q) => (q.npc = q.by)); // seats the sea friends sit in stay theirs
  function nameTag(text, color) {
    const tex = canvasTex(256, 64, (g, w) => {
      g.font = `600 30px ${FONT}`; const tw = Math.min(w - 8, g.measureText(text).width + 34);
      g.fillStyle = "rgba(11,12,16,0.78)"; g.beginPath(); g.rect((w - tw) / 2, 8, tw, 48); g.fill();
      g.fillStyle = color; g.beginPath(); g.arc((w - tw) / 2 + 20, 32, 7, 0, Math.PI * 2); g.fill();
      g.fillStyle = "#F1F2F5"; g.textBaseline = "middle"; g.textAlign = "left"; g.fillText(text, (w - tw) / 2 + 34, 33);
    });
    const s = new T.Sprite(new T.SpriteMaterial({ map: tex, depthWrite: false, transparent: true })); s.scale.set(1.4, 0.35, 1); return s;
  }
  function setPeople(list) {
    const seen = new Set();
    SEATS.forEach((q) => (q.by = q.npc));
    (list || []).forEach((p) => {
      const id = String(p.id); seen.add(id);
      const col = /^#[0-9a-f]{6}$/i.test(p.color || "") ? p.color : "#7AA7FF";
      let o = PEOPLE.get(id);
      if (o && (o.color !== col || o.name !== p.name)) { G.fx.remove(o.av); o = null; }
      if (!o) {
        const av = new T.Group(), body = friend(col, 1.15);
        av.add(body); const tag = nameTag(String(p.name || "Someone").slice(0, 18), col); tag.position.y = 1.35; av.add(tag);
        o = { av, body, tag, name: p.name, color: col, x: +p.x || 0, z: +p.z || 0, h: +p.h || 0, yaw: +p.yaw || 0 }; G.fx.add(av); PEOPLE.set(id, o);
      }
      o.tx = +p.x || 0; o.tz = +p.z || 0; o.th = +p.h || 0; o.tyaw = +p.yaw || 0; o.cr = !!p.cr; o.act = p.act || "";
      // one person per seat: a seat somebody is in says so, and nobody else can sit there
      const q = Number.isInteger(p.sit) && p.sit >= 0 ? SEATS[p.sit] : null;
      o.seat = q && !q.npc ? q : null; if (o.seat) o.seat.by = String(p.name || "Someone").slice(0, 18);
    });
    PEOPLE.forEach((o, id) => { if (!seen.has(id)) { G.fx.remove(o.av); PEOPLE.delete(id); } });
    kick();
  }
  function peopleFrame(dt, ms) {
    const k = Math.min(1, dt * 10), t = ms / 1000;
    PEOPLE.forEach((o) => {
      const q = o.seat;
      const tx = q ? WX(q.x) : o.tx, tz = q ? WZ(q.y) : o.tz, th = q ? Math.max(0, q.z - 0.05) : o.th, tyaw = q ? q.yaw : o.tyaw;
      const moving = !q && Math.hypot(tx - o.x, tz - o.z) > 0.03;
      o.x += (tx - o.x) * k; o.z += (tz - o.z) * k; o.h += (th - o.h) * k;
      let d = tyaw - o.yaw; d = Math.atan2(Math.sin(d), Math.cos(d)); o.yaw += d * k;
      o.av.position.set(o.x, o.h, o.z); o.av.rotation.y = o.yaw;
      const sy = o.cr && !q ? 0.62 : 1; o.body.scale.y += (1.15 * sy - o.body.scale.y) * k; o.tag.position.y = o.cr && !q ? 0.95 : 1.35;
      o.body.position.y = o.act === "clap" ? Math.abs(Math.sin(t * 14)) * 0.06 : moving ? Math.abs(Math.sin(t * 7)) * 0.05 : 0;
      o.body.rotation.z = o.act === "wave" ? Math.sin(t * 9) * 0.18 : o.act === "laugh" ? Math.sin(t * 22) * 0.06 : 0;
    });
  }
  // which seat you are in (so nobody else sits on you)
  const mySeat = () => { const q = game && (game === SITG || game === SOFAG) ? game.seat : null; const i = q ? SEATS.indexOf(q) : -1; return i; };

  // what the Lobby page (the page that holds this one) can ask for
  window.GF_LOUNGE = {
    me: () => ({ x: +WX(P.x).toFixed(3), z: +WZ(P.y).toFixed(3), yaw: +yaw.toFixed(3), h: +P.f.toFixed(2), cr: P.H < 1 ? 1 : 0, sit: mySeat() }),
    setPeople, onFull,
    setGame() {}, hint() {}, // Hide and seek and Tag are played in the Cinema
    begin() { armed = true; try { window.focus(); } catch (e) {} stage.focus({ preventScroll: true }); sndStart(); grabMouse(); kick(); },
    letGo() { armed = false; Object.keys(keys).forEach((k) => (keys[k] = 0)); ctrlHeld = false; if (game) stopGame(); if (document.pointerLockElement === stage) document.exitPointerLock(); },
    setFast(fast) { autoDropped = true; setPretty(!fast); },
    quiet() { setSound(false); },
  };
  const start = () => { drawBlueprint(); setMode("walk"); kick(); { const h = host(); if (h.ready) h.ready(); } };
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(start, start); else start();
})();
