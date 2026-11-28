/* Glowfin web: the Glowfin Cinema, a Lobby place (designed 2026-11-20, thread "Design the Theater lobby map").
   A whole multiplex in real sizes: a lobby, three screens with stadium seats, a projection level and 16 hiding
   places. This file runs inside web/cinema.html, which the Lobby (js/lobby.js) shows in a frame when the host
   picks "Glowfin Cinema". The Lobby asks this page where you are (GF_CINEMA.me) and tells it where everyone
   else is (GF_CINEMA.setPeople). Map: x 0-90 left to right, y 0 (back wall) to 60 (front doors), in metres. */
(() => {
  "use strict";
  window.WH_SPOTS = [];
  window.WH_TOUR = [[45, 57.5, 0], [45, 50, 0]];
  window.WH_LABELS = [
   ["Screen 1 · recliners", 15, 22, 17], ["Screen 2", 45, 22, 17], ["Screen 3", 75, 22, 17], ["Projection level (upstairs)", 45, 38, 13],
   ["Lobby", 45, 52.5, 17], ["Concessions", 43, 43, 14], ["Stock", 55, 42.6, 12], ["Drinks", 26.5, 45.4, 12], ["Manager", 16.5, 42.8, 12], ["Party room", 6, 50.4, 14],
   ["Restrooms", 84, 56, 14], ["Guest services", 66, 54.6, 12], ["Tickets", 45, 55.3, 12], ["Entrance", 45, 59, 13], ["Janitor", 65.8, 41.3, 11],
  ];
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
  scene.background = new T.Color(0x1c2029);
  scene.fog = new T.Fog(0x1c2029, 45, 140);
  const camera = new T.PerspectiveCamera(70, 16 / 9, 0.05, 260);
  camera.rotation.order = "YXZ";
  scene.add(new T.HemisphereLight(0xf6f7ff, 0x4d525b, 0.3));
  const sun = new T.DirectionalLight(0xfff4e2, 1.35); sun.position.set(16, 40, 11); scene.add(sun); scene.add(sun.target);
  sun.castShadow = true; sun.shadow.mapSize.set(4096, 4096); sun.shadow.bias = -0.0005; sun.shadow.normalBias = 0.04; sun.shadow.radius = 2.5;
  Object.assign(sun.shadow.camera, { left: -58, right: 58, top: 52, bottom: -52, near: 1, far: 120 }); sun.shadow.camera.updateProjectionMatrix();
  if (T.RoomEnvironment) { const pm = new T.PMREMGenerator(renderer); scene.environment = pm.fromScene(new T.RoomEnvironment(), 0.04).texture; }

  // ---------- groups ----------
  const G = { main: new T.Group(), ceil: new T.Group(), lid: new T.Group(), upper: new T.Group(), fx: new T.Group() };
  Object.values(G).forEach((g) => scene.add(g));

  // Map coords: x 0..90 left to right, y 0 (back wall) .. 60 (front wall), z = height in metres.
  const WX = (x) => x - 45, WZ = (y) => y - 30;
  const BX = {}, SOL = [], OCC = [];
  function bx(x1, y1, x2, y2, b, t, c, o) {
    o = o || {};
    if (x1 > x2) [x1, x2] = [x2, x1];
    if (y1 > y2) [y1, y2] = [y2, y1];
    const k = (o.k || "lit") + "|" + (o.g || "main");
    (BX[k] || (BX[k] = [])).push([x1, y1, x2, y2, b, t, c]);
    if (!o.ns) SOL.push({ x1, x2, y1, y2, b, t, st: o.st ? 1 : 0 });
    if (o.k !== "glass" && o.k !== "cur" && !o.see) OCC.push({ x1, x2, y1, y2, b, t });
  }
  const sol = (x1, y1, x2, y2, b, t, st) => SOL.push({ x1, x2, y1, y2, b, t, st: st ? 1 : 0 });
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
    paint: { tile: 1.0, rough: 0.55, metal: 0.05, edge: 0.03, dark: 0.68, tex: looksTex(128, (g, s) => { g.fillStyle = "#f4f4f4"; g.fillRect(0, 0, s, s); speck(g, s, 500, 0.07); }) },
  };
  LOOK.product.tex = LOOK.paint.tex;
  const TYPE = {};
  function initTypes() {
  C.card.forEach((c) => (TYPE[c] = "card")); C.wrap.forEach((c) => (TYPE[c] = "wrap"));
  ["#a9855c", "#8a7458", "#a47648", "#8a6a4a", "#5b3d2a", "#c99a5c", "#6b4a2e", "#9b6b43", "#6a4e34", "#8b6a4a"].forEach((c) => (TYPE[c] = "wood"));
  [C.blue, C.beam, C.steel, C.trim, "#9aa0a8", "#6d737c", "#4b5058", "#3c6fb0", "#aab1ba", "#cfd5dc", "#5b616b", "#30353d", "#6a707a", "#5f656e", "#b9c0c8", "#8a9099", "#26282c"].forEach((c) => (TYPE[c] = "metal"));
  [C.wall, C.wall2, C.white, "#dfe3e6", "#e9ecef", "#3d424b", "#dfe3e7", "#c7cad0", "#d9dde2"].forEach((c) => (TYPE[c] = "wall"));
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
          const rounded = (type === "card" || type === "wrap" || type === "paint") && Math.max(dx, dy, dz) < 2.6 && Math.min(dx, dy, dz) > 0.09;
          key = (rounded && type === "paint" && !window.WH_NOLABEL && Math.max(dx, dy, dz) < 1.3 ? "product" : type) + (rounded ? "|r" : "");
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
    const W = 1472, H = 1184, sx = W / 92, sy = H / 74; // floor plane: x -1..91, y -13..61
    const c = document.createElement("canvas"); c.width = W; c.height = H; const g = c.getContext("2d");
    g.fillStyle = "#000"; g.fillRect(0, 0, W, H);
    const src = document.createElement("canvas"); src.width = W; src.height = H; const q = src.getContext("2d");
    const draw = (blur, grow, a) => { q.clearRect(0, 0, W, H); q.fillStyle = "#fff";
      Object.keys(BX).forEach((k) => { if (!k.startsWith("lit|main") && !k.startsWith("lit|lid") && !k.startsWith("lit|upper")) return; BX[k].forEach((r) => { if (r[4] > 0.06 || r[5] < 0.25) return; q.fillRect((r[0] + 1 - grow) * sx, (r[1] + 13 - grow) * sy, (r[2] - r[0] + grow * 2) * sx, (r[3] - r[1] + grow * 2) * sy); }); });
      g.filter = `blur(${blur}px)`; g.globalAlpha = a; g.drawImage(src, 0, 0); g.filter = "none"; g.globalAlpha = 1; };
    draw(14, 0.25, 0.35); draw(4, 0.06, 0.45);
    const t = new T.CanvasTexture(c);
    const m = new T.Mesh(new T.PlaneGeometry(92, 74), new T.MeshBasicMaterial({ color: 0x000000, alphaMap: t, transparent: true, opacity: 0.85, depthWrite: false, toneMapped: false }));
    m.rotation.x = -Math.PI / 2; m.position.set(WX(45), 0.024, WZ(24)); m.renderOrder = 1; G.main.add(m);
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
  const FONT = '"Glowfin Lexend", system-ui, "Segoe UI", sans-serif';
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

  // ---------- floor ----------
  const floorTex = canvasTex(256, 256, (g) => {
    g.fillStyle = "#a3a7ad"; g.fillRect(0, 0, 256, 256);
    for (let i = 0; i < 900; i++) { g.fillStyle = `rgba(${rnd() < 0.5 ? "255,255,255" : "40,44,52"},${0.04 + rnd() * 0.05})`; g.fillRect(rnd() * 256, rnd() * 256, 2 + rnd() * 3, 2 + rnd() * 3); }
    g.strokeStyle = "rgba(50,54,62,.35)"; g.lineWidth = 2; g.strokeRect(1, 1, 254, 254);
  });
  floorTex.wrapS = floorTex.wrapT = T.RepeatWrapping; floorTex.repeat.set(23, 37);
  const floorTex2 = looksTex(1024, (g, s) => {
    g.fillStyle = "#4a3350"; g.fillRect(0, 0, s, s);
    for (let i = 0; i < 90; i++) { const x = lr() * s, y = lr() * s, r = 30 + lr() * 160; const gr = g.createRadialGradient(x, y, 0, x, y, r); const v = lr() < 0.5 ? "255,255,255" : "70,74,82"; gr.addColorStop(0, `rgba(${v},${0.04 + lr() * 0.05})`); gr.addColorStop(1, `rgba(${v},0)`); g.fillStyle = gr; g.fillRect(x - r, y - r, r * 2, r * 2); }
    speck(g, s, 9000, 0.12);
    for (let i = 0; i < 26; i++) { g.strokeStyle = `rgba(40,42,48,${0.05 + lr() * 0.08})`; g.lineWidth = 1 + lr() * 2; g.beginPath(); const x = lr() * s, y = lr() * s; g.moveTo(x, y); g.lineTo(x + (lr() - 0.5) * 140, y + (lr() - 0.5) * 30); g.stroke(); }
    g.fillStyle = "rgba(45,48,55,.55)"; g.fillRect(0, 0, s, 2); g.fillRect(0, 0, 2, s);
  });
  floorTex2.repeat.set(92 / 6, 74 / 6);
  const floor = new T.Mesh(new T.PlaneGeometry(92, 74), new T.MeshStandardMaterial({ map: floorTex2, roughness: 0.92, metalness: 0.0, envMapIntensity: 0.12 }));
  floor.receiveShadow = true;
  floor.rotation.x = -Math.PI / 2; floor.position.set(0, 0, WZ(24)); G.main.add(floor);

  // ---------- colours ----------
  const C = {
    wall: "#c7cad0", wall2: "#b9bdc4", trim: "#8a9099", blue: "#1f5fbf", beam: "#f08a24", deck: "#8a7458",
    card: ["#b8895a", "#a87b4f", "#c49a68", "#9c7146"], wrap: ["#e6e9ee", "#d8dde4", "#eef1f4"],
    brand: ["#d94f4f", "#2f6db5", "#f2c14e", "#3d9a5b", "#7b4fb5", "#e07b39", "#f4f4f4", "#1d8a8a", "#c2185b"],
    steel: "#b9c0c8", white: "#eef1f3", dark: "#2c3038", black: "#1c1e22", wood: "#9b6b43", red: "#c8202c",
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
    const win = new T.Mesh(alongX ? new T.BoxGeometry(len * 0.3, h * 0.2, th + 0.02) : new T.BoxGeometry(th + 0.02, h * 0.2, len * 0.3), new T.MeshBasicMaterial({ color: 0x3b4150 }));
    if (alongX) win.position.set(sgn * len * 0.5, h * 0.72, 0); else win.position.set(0, h * 0.72, sgn * len * 0.5);
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
  //  THE THEATER  (90 m x 60 m, 10 m to the roof): lobby at the front (y 40-60), three screens behind it (y 0-40)
  // =====================================================================
  window.WH_NOLABEL = true;
  // theater lighting: softer and warmer than the warehouse, so the glowing things do the work
  sun.intensity = 0.55; sun.color.setHex(0xffe6c8); scene.children.forEach((c) => { if (c.isHemisphereLight) { c.intensity = 0.42; c.color.setHex(0xffe9d2); c.groundColor.setHex(0x3a2630); } });
  scene.background = new T.Color(0x0c0a12); scene.fog = new T.Fog(0x0c0a12, 50, 150);
  { const ct = looksTex(512, (g, s) => { g.fillStyle = "#57182a"; g.fillRect(0, 0, s, s); let q = 4321; const r = () => { q = (q * 16807) % 2147483647; return (q - 1) / 2147483646; };
      for (let k = 0; k < 26; k++) { const x = r() * s, y = r() * s, c = ["#e8b23a", "#2c7a8c", "#8a2f52", "#c9772f"][k % 4]; g.strokeStyle = c; g.lineWidth = 5; g.globalAlpha = 0.75; g.beginPath(); g.arc(x, y, 22 + r() * 30, r() * 6, r() * 6 + 2.2); g.stroke(); [-s, s].forEach((d) => { g.beginPath(); g.arc(x + d, y, 22, 0, 2); g.stroke(); }); }
      for (let k = 0; k < 30; k++) { const x = r() * s, y = r() * s, R = 7 + r() * 7; g.fillStyle = k % 2 ? "#e8b23a" : "#f0d9a0"; g.globalAlpha = 0.85; g.beginPath(); for (let j = 0; j < 10; j++) { const a = j * Math.PI / 5, rr = j % 2 ? R * 0.45 : R; g.lineTo(x + Math.sin(a) * rr, y - Math.cos(a) * rr); } g.fill(); }
      g.globalAlpha = 1; });
    ct.repeat.set(92 / 5, 74 / 5); floor.material.map = ct; floor.material.needsUpdate = true; }
  const K = { wall: "#3a3f58", wall2: "#2c3046", red: "#8c1f2b", redD: "#6d1722", step: "#4a3555", seat: "#a3202e", seatP: "#34384a", gold: "#e8b23a", dark: "#1c1e26", wood: "#6b4a34", steel: "#b9c0c8", white: "#eef1f3", black: "#14151a", door: "#7a2733", cream: "#e9dcc0" };
  let cs = 9090; const cq = () => { cs = (cs * 16807) % 2147483647; return (cs - 1) / 2147483646; }; const cpick = (a) => a[Math.floor(cq() * a.length)];
  const fgrp = (x, y, z) => { const g = new T.Group(); g.position.set(WX(x), z, WZ(y)); G.main.add(g); return g; };
  const fpart = (g, geo, col, x, y, z) => { const m = new T.Mesh(geo, lam(col)); m.position.set(x, y, z); g.add(m); return m; };
  function plate(tex, x, y, z, ry, w, h, o) {
    o = o || {};
    const m = new T.Mesh(new T.PlaneGeometry(w, h), new T.MeshBasicMaterial({ map: tex, toneMapped: false, transparent: !!o.alpha, depthWrite: !o.alpha }));
    m.position.set(WX(x), z, WZ(y)); m.rotation.y = ry || 0; if (o.floor) m.rotation.set(-Math.PI / 2, 0, ry || 0);
    (o.g ? G[o.g] : G.main).add(m); return m;
  }
  const mesh = (geo, c, x, y, z, o) => { const m = new T.Mesh(geo, o && o.basic ? new T.MeshBasicMaterial({ color: c, wireframe: !!o.wire }) : lam(c)); m.position.set(WX(x), z, WZ(y)); G.main.add(m); return m; };
  const lintel = (x1, y1, x2, y2, b, t, col) => bx(x1, y1, x2, y2, b, t, col);
  // ---------- outer shell ----------
  wallX(-0.4, 90.4, -0.2, 0, 10, K.wall2);
  wallX(-0.4, 90.4, 60.2, 0, 10, K.wall2, [[41, 49]]);
  bx(41, 60.05, 49, 60.35, 3.0, 10, K.wall2); bx(41, 60.15, 49, 60.25, 0, 3.0, "#9fd3ff", { k: "glass" });
  [43, 45, 47].forEach((x) => bx(x - 0.04, 60.1, x + 0.04, 60.3, 0, 3.0, K.dark, { ns: 1, see: 1 }));
  wallY(-0.4, 60.4, -0.2, 0, 10, K.wall2); wallY(-0.4, 60.4, 90.2, 0, 10, K.wall2);
  bx(-0.4, -0.4, 90.4, 60.4, 10, 10.3, "#22242e", { g: "ceil", ns: 1 });
  for (let x = 5; x < 90; x += 8) for (let y = 43; y < 60; y += 5.5) bx(x - 0.5, y - 0.5, x + 0.5, y + 0.5, 9.85, 9.95, "#ffe9c4", { k: "glow", g: "ceil", ns: 1, see: 1 });
  // lobby carpet pattern and the dark auditorium floors
  bx(0, 0, 90, 40, 0, 0.012, "#2a2233", { ns: 1, see: 1 });
  // ---------- wall between the lobby and the screens, and the walls between screens ----------
  const AUD = [0, 30, 60];
  wallX(0, 90, 40, 0, 10, K.wall, AUD.map((a) => [a + 0.8, a + 2.2]));
  AUD.forEach((a, i) => { lintel(a + 0.8, 39.85, a + 2.2, 40.15, 2.3, 10, K.wall); door(a + 0.8, 39.97, a + 2.2, 40.03, 0, 2.3, K.door, "a", "n");
    const s = sign("SCREEN " + (i + 1), a + 1.5, 40.2, 2.75, 0, 1.8, { bg: "#14151a", fg: "#e8b23a", two: false }); s.scale.y = 1.4; });
  [30, 60].forEach((x) => { wallY(0, 36, x, 0, 10, K.wall2); bx(x - 0.15, 36, x + 0.15, 40, 0, 4.8, K.wall2); wallY(36, 40, x, 4.8, 10, K.wall2, [[37.4, 38.6]]); lintel(x - 0.15, 37.4, x + 0.15, 38.6, 7.0, 10, K.wall2); door(x - 0.03, 37.4, x + 0.03, 38.6, 4.8, 2.2, "#8d949e", "a", "e"); });
  // ---------- seats ----------
  function seatRow(xa, xb, ya, z, w, col) {
    const n = Math.floor((xb - xa) / w), x0 = xa + ((xb - xa) - n * w) / 2;
    for (let i = 0; i < n; i++) { const x = x0 + i * w;
      bx(x + 0.05, ya + 0.72, x + w - 0.05, ya + 1.24, z + 0.28, z + 0.46, col, { ns: 1, see: 1 });
      bx(x + 0.05, ya + 1.2, x + w - 0.05, ya + 1.38, z + 0.2, z + 1.05, col, { ns: 1 });
      bx(x - 0.03, ya + 0.74, x + 0.03, ya + 1.3, z, z + 0.62, K.dark, { ns: 1, see: 1 });
      if (w > 0.8) bx(x + 0.08, ya + 0.3, x + w - 0.08, ya + 0.72, z + 0.2, z + 0.34, col, { ns: 1, see: 1 }); }
    bx(x0 + n * w - 0.03, ya + 0.74, x0 + n * w + 0.03, ya + 1.3, z, z + 0.62, K.dark, { ns: 1, see: 1 });
    sol(x0, ya + 0.7, x0 + n * w, ya + 1.4, z, z + 1.05); return [x0, x0 + n * w];
  }
  const SCREENS = [], BEAMS = [];
  const tagCache = {};
  function rowTag(t, x, y, z, ry) { const tex = tagCache[t] || (tagCache[t] = numTex(t, "#14151a", "#e8b23a")); const m = new T.Mesh(new T.PlaneGeometry(0.2, 0.2), new T.MeshBasicMaterial({ map: tex, transparent: true, toneMapped: false })); m.position.set(WX(x), z, WZ(y)); m.rotation.y = ry; G.main.add(m); }
  function bucket(x, y, z) { bx(x, y, x + 0.17, y + 0.17, z, z + 0.22, cq() < 0.5 ? "#c8202c" : "#f4f4f4", { ns: 1, see: 1 }); bx(x + 0.01, y + 0.01, x + 0.16, y + 0.16, z + 0.22, z + 0.27, "#ffe9a8", { ns: 1, see: 1 }); }
  const wheelTex = canvasTex(128, 128, (g) => { g.fillStyle = "#1d6fb8"; g.fillRect(0, 0, 128, 128); g.strokeStyle = "#fff"; g.lineWidth = 8; g.strokeRect(6, 6, 116, 116); g.beginPath(); g.arc(60, 82, 24, 0.4, 5.4); g.stroke(); g.beginPath(); g.moveTo(64, 40); g.lineTo(64, 70); g.lineTo(88, 70); g.lineTo(98, 96); g.stroke(); g.fillStyle = "#fff"; g.beginPath(); g.arc(64, 28, 9, 0, 7); g.fill(); });
  const spillTex = canvasTex(128, 128, (g) => { for (let k = 0; k < 46; k++) { g.fillStyle = cq() < 0.7 ? "#fff4c2" : "#f2d98a"; g.beginPath(); g.arc(64 + (cq() - 0.5) * 100 * cq(), 64 + (cq() - 0.5) * 100 * cq(), 2.5 + cq() * 3, 0, 7); g.fill(); } });
  function beam(x1, y1, z1, x2, y2, z2) {
    const a = [[-0.15, -0.1], [0.15, -0.1], [0.15, 0.1], [-0.15, 0.1]], b = [[-11, -4.2], [11, -4.2], [11, 4.2], [-11, 4.2]], v = [];
    const P3 = (q, x, y, z) => [WX(x + q[0]), z + q[1], WZ(y)];
    for (let k = 0; k < 4; k++) { const k2 = (k + 1) % 4, p = [P3(a[k], x1, y1, z1), P3(a[k2], x1, y1, z1), P3(b[k2], x2, y2, z2), P3(b[k], x2, y2, z2)]; v.push(...p[0], ...p[1], ...p[2], ...p[0], ...p[2], ...p[3]); }
    const geo = new T.BufferGeometry(); geo.setAttribute("position", new T.Float32BufferAttribute(v, 3));
    const m = new T.Mesh(geo, new T.MeshBasicMaterial({ color: 0xcfe2ff, transparent: true, opacity: 0.05, blending: T.AdditiveBlending, depthWrite: false, side: T.DoubleSide, toneMapped: false })); m.renderOrder = 4; G.fx.add(m); return m;
  }
  // ---------- one auditorium ----------
  function aud(x0, n, o) {
    const x1 = x0 + 30, xi = x0 + 0.15, xe = x1 - 0.15, xs = x0 + 2.75, room = 20 + n;
    // screen wall with the exit door beside the screen, and the space behind the screen
    wallX(xi, xe, 3, 0, 10, K.black, [[x1 - 2.1, x1 - 0.95]]); lintel(x1 - 2.1, 2.85, x1 - 0.95, 3.15, 2.2, 10, K.black);
    door(x1 - 2.1, 2.97, x1 - 0.95, 3.03, 0, 2.2, "#8d949e", "a", "n", o.vest ? room : 0);
    bx(x0 + 3.8, 3.15, x0 + 26.2, 3.22, 1.0, 9.6, "#07070b", { ns: 1, see: 1 });
    SCREENS.push([x0 + 15, 3.24, 5.3]);
    // exit vestibule: two doors between the seats and the back corridor
    wallY(3.15, 5.65, x1 - 2.75, 0, 3, K.wall2); wallX(x1 - 2.9, xe, 5.5, 0, 3, K.wall2, [[x1 - 2.1, x1 - 0.95]]); lintel(x1 - 2.1, 5.35, x1 - 0.95, 5.65, 2.2, 3, K.wall2);
    bx(x1 - 2.9, 3.15, xe, 5.65, 3, 3.12, K.wall2, { g: "lid", ns: 1 });
    door(x1 - 2.1, 5.47, x1 - 0.95, 5.53, 0, 2.2, K.door, "b", "s", o.vest ? room : 0);
    sign("EXIT", x1 - 1.5, 5.7, 2.5, 0, 0.9, { bg: "#c8202c", two: false });
    // speakers behind the screen
    if (!o.noSpk) [x0 + 12, x0 + 20].forEach((x) => { bx(x, 0.3, x + 1.8, 1.9, 0, 3.4, K.black); bx(x + 0.3, 1.9, x + 1.5, 1.93, 0.6, 2.8, "#2a2d38", { ns: 1, see: 1 }); });
    // two rows on the flat floor at the front
    seatRow(x0 + 5, x1 - 5, 7.2, 0, o.w, o.col); seatRow(x0 + 5, x1 - 5, 9.4, 0, o.w, o.col);
    // entry corridor along the left wall, then the stadium
    wallY(14, 36, x0 + 2.6, 0, 6.4, K.wall2, o.hollow ? [[30, 31.1]] : null);
    bx(x0 + 2.45, 36, x0 + 2.75, 39.85, 0, 4.65, K.wall2);
    if (o.hollow) { lintel(x0 + 2.45, 30, x0 + 2.75, 31.1, 2.2, 6.4, K.wall2); door(x0 + 2.57, 30, x0 + 2.63, 31.1, 0, 2.2, "#8d949e", "a", "e", room + 10); sign("STAFF ONLY", x0 + 2.42, 30.55, 2.45, -Math.PI / 2, 0.9, { bg: "#c8202c", two: false }); }
    if (o.hollow) bx(xs, 20.3, xe, 20.5, 0, 1.85, K.dark);
    bx(xi, 14, x0 + 2.45, 39.85, 0, 0.014, K.red, { ns: 1, see: 1 });
    for (let y = 16; y < 36; y += 4) bx(x0 + 2.42, y, x0 + 2.45, y + 0.5, 0.25, 0.32, K.gold, { k: "glow", ns: 1, see: 1 });
    for (let i = 0; i < 12; i++) {
      const ya = 14 + 1.6 * i, z = 0.4 * (i + 1);
      bx(xs, ya, xe, ya + 1.6, z - 0.15, z, K.step, { st: 1 });
      bx(xs, ya, xe, ya + 0.08, z - 0.4, z - 0.15, K.dark, { ns: 1 });
      [[xs + 0.1, xs + 1.3], [xe - 1.3, xe - 0.1]].forEach(([a, b]) => bx(a, ya - 0.012, b, ya, z - 0.11, z - 0.07, K.gold, { k: "glow", ns: 1, see: 1 }));
      const sr = seatRow(xs + 1.5, xe - 1.5, ya, z, o.w, o.col);
      rowTag("ABCDEFGHJKLM"[i], sr[0] - 0.05, ya + 1.0, z + 0.45, -Math.PI / 2); rowTag("ABCDEFGHJKLM"[i], sr[1] + 0.05, ya + 1.0, z + 0.45, Math.PI / 2);
      [[xs + 0.12, xs + 1.38], [xe - 1.38, xe - 0.12]].forEach(([a, b]) => bx(a, ya + 0.02, b, ya + 1.6, z, z + 0.008, K.red, { ns: 1, see: 1 }));
      if (cq() < 0.6) bucket(sr[0] + 0.3 + cq() * (sr[1] - sr[0] - 0.6), ya + 0.35, z);
    }
    bx(xs, 33.2, xe, 36, 4.65, 4.8, K.step, { st: 1 });
    // projection level: floor, front wall with a door and the projector window
    bx(xi, 36, xe, 39.85, 4.65, 4.8, "#4b5058", { st: 1 });
    wallX(xi, xe, 36, 4.8, 10, K.wall2, [[x1 - 3.2, x1 - 2.0], [x0 + 14, x0 + 16]]);
    lintel(x1 - 3.2, 35.85, x1 - 2.0, 36.15, 7.0, 10, K.wall2); door(x1 - 3.2, 35.97, x1 - 2.0, 36.03, 4.8, 2.2, "#8d949e", "a", "n");
    bx(x0 + 14, 35.85, x0 + 16, 36.15, 4.8, 6.0, K.wall2); lintel(x0 + 14, 35.85, x0 + 16, 36.15, 7.0, 10, K.wall2); bx(x0 + 14, 35.97, x0 + 16, 36.03, 6.0, 7.0, "#9fd3ff", { k: "glass" });
    sign("PROJECTION · STAFF", x1 - 2.6, 35.8, 7.25, Math.PI, 1.1, { bg: "#30353d", two: false });
    bx(x0 + 14.4, 36.9, x0 + 15.6, 38.5, 4.8, 5.7, "#6d737c"); bx(x0 + 14.5, 36.6, x0 + 15.5, 38.4, 5.7, 6.75, "#9aa0a8", { ns: 1 }); bx(x0 + 14.85, 36.3, x0 + 15.15, 36.6, 6.2, 6.5, "#9fd3ff", { k: "glow", ns: 1, see: 1 });
    bx(x0 + 20, 39.0, x0 + 21.2, 39.8, 4.8, 6.8, "#22252e"); for (let z = 5.0; z < 6.7; z += 0.3) bx(x0 + 20.1, 38.98, x0 + 21.1, 39.0, z, z + 0.05, cpick(["#5bd18b", "#3fe0e8", "#ffd25a"]), { k: "glow", ns: 1, see: 1 });
    // red wall panels, wall speakers and sconces on the side walls
    for (let y = 6; y < 34; y += 4) { [[xi, xi + 0.06], [xe - 0.06, xe]].forEach(([a, b], s) => { if (s === 0 && y > 12) return; bx(a, y, b, y + 3, 1.2 + Math.max(0, y - 14) * 0.25, 6.4 + Math.max(0, y - 14) * 0.1, K.red, { ns: 1, see: 1 }); });
      bx(xe - 0.35, y + 1.2, xe - 0.06, y + 1.8, 5.2 + Math.max(0, y - 14) * 0.22, 5.9 + Math.max(0, y - 14) * 0.22, K.black, { ns: 1, see: 1 }); }
    for (let y = 16; y < 34; y += 4) bx(x0 + 2.75, y, x0 + 2.81, y + 3, 1.6 + (y - 14) * 0.25, 6.2, K.red, { ns: 1, see: 1 });
    // stage, curtains and valance round the screen
    bx(x0 + 3.2, 3.15, x1 - 3.2, 4.5, 0, 0.35, K.wood, { st: 1 }); bx(x0 + 3.2, 4.5, x1 - 3.2, 4.53, 0.2, 0.3, K.gold, { k: "glow", ns: 1, see: 1 });
    [[x0 + 2.2, x0 + 3.8], [x1 - 4.2, x1 - 2.95]].forEach(([a, b]) => { for (let x = a, k = 0; x < b - 0.01; x += 0.2, k++) bx(x, 3.15, Math.min(b, x + 0.2), 3.27 + (k % 2) * 0.07, 0.35, 9.2, k % 2 ? K.red : K.redD, { ns: 1, see: 1 }); });
    for (let x = x0 + 2.2, k = 0; x < x1 - 2.96; x += 0.5, k++) bx(x, 3.15, Math.min(x1 - 2.95, x + 0.5), 3.36 + (k % 2) * 0.06, 9.0 - (k % 2) * 0.15, 9.95, k % 2 ? K.red : K.redD, { ns: 1, see: 1 });
    bx(x0 + 2.2, 3.15, x1 - 2.95, 3.44, 8.86, 8.94, K.gold, { k: "glow", ns: 1, see: 1 });
    // warm wall lights, a bin by the exit, wheelchair spaces, spilled popcorn
    for (let y = 8; y < 34; y += 4) { const h = 3.4 + Math.max(0, y - 14) * 0.22; bx(xe - 0.14, y - 0.4, xe - 0.06, y - 0.1, h, h + 0.55, "#ffcf8a", { k: "glow", ns: 1, see: 1 }); if (y < 13) bx(xi + 0.06, y - 0.4, xi + 0.14, y - 0.1, h, h + 0.55, "#ffcf8a", { k: "glow", ns: 1, see: 1 }); else bx(x0 + 2.81, y - 0.4, x0 + 2.89, y - 0.1, h + 0.6, h + 1.15, "#ffcf8a", { k: "glow", ns: 1, see: 1 }); }
    bx(x1 - 3.75, 6.0, x1 - 3.2, 6.55, 0, 0.95, "#30353d"); bx(x1 - 3.65, 6.1, x1 - 3.3, 6.45, 0.95, 0.97, K.black, { ns: 1, see: 1 });
    [x0 + 6, x1 - 8].forEach((x) => plate(wheelTex, x + 1, 13, 0.018, 0, 1.6, 1.6, { floor: 1, alpha: 1 }));
    [[x0 + 9 + cq() * 10, 12.6], [x0 + 3.5, 20 + cq() * 8], [x1 - 0.9, 16 + cq() * 10]].forEach(([x, y]) => plate(spillTex, x, y, 0.02 + (y >= 14 ? 0.4 * (Math.floor((y - 14) / 1.6) + 1) : 0), cq() * 6, 1.1, 1.1, { floor: 1, alpha: 1 }));
    // the projector beam from the booth window to the screen
    BEAMS.push(beam(x0 + 15, 36.0, 6.5, x0 + 15, 3.3, 5.3));
    // projection room: platter table with film reels, a stool, a note
    bx(x0 + 8, 36.5, x0 + 10, 38.1, 5.55, 5.62, K.steel); [[x0 + 8.1, 36.6], [x0 + 9.8, 36.6], [x0 + 8.1, 38.0], [x0 + 9.8, 38.0]].forEach(([x, y]) => bx(x, y, x + 0.08, y + 0.08, 4.8, 5.55, "#6d737c", { ns: 1, see: 1 })); sol(x0 + 8, 36.5, x0 + 10, 38.1, 4.8, 5.62);
    [[x0 + 8.55, 37.3], [x0 + 9.45, 37.3]].forEach(([x, y], k) => { mesh(new T.CylinderGeometry(0.36, 0.36, 0.06, 20), k ? "#3a3f47" : "#b9c0c8", x, y, 5.66); mesh(new T.CylinderGeometry(0.08, 0.08, 0.1, 10), "#e8b23a", x, y, 5.7); });
    bx(x0 + 16.4, 37.6, x0 + 16.8, 38.0, 4.8, 5.35, "#30353d"); sol(x0 + 16.3, 37.5, x0 + 16.9, 38.1, 4.8, 5.35);
    sign("NO POPCORN NEAR THE PROJECTOR", x0 + 11, 39.84, 6.4, Math.PI, 1.5, { bg: "#ffffff", fg: "#c8202c", sub: "(We know about the popcorn.)", two: false });
    sign("SCREEN " + (n + 1), x0 + 15, 36.2 - 0.36, 9.0, Math.PI, 3.0, { bg: "#14151a", fg: "#e8b23a", two: false });
  }
  aud(0, 0, { w: 0.95, col: K.seatP, noSpk: 1 });
  aud(30, 1, { w: 0.62, col: K.seat, hollow: 1, vest: 1 });
  aud(60, 2, { w: 0.62, col: K.seat });
  // --- spot 1: behind screen 1, round two speaker stacks ---
  bx(6, 0.15, 7, 2.0, 0, 3.4, K.black); bx(3.5, 1.0, 4.5, 2.85, 0, 3.4, K.black); bx(12, 0.3, 13.8, 1.9, 0, 3.4, K.black); bx(20, 0.3, 21.8, 1.9, 0, 3.4, K.black);
  // --- spot 2: under the stadium seats in screen 2 (spare seats, cable drums and a lamp) ---
  bx(40, 33.5, 44, 34.3, 0, 1.1, K.seat); bx(40.2, 34.5, 43.8, 35.3, 0, 1.1, K.seat); bx(50, 36.5, 51.4, 37.9, 0, 0.9, K.wood); bx(36, 37, 38, 38.5, 0, 1.4, "#b8895a");
  bx(34, 32, 34.2, 32.2, 0, 3.9, "#6d737c", { ns: 1, see: 1 }); bx(46, 28, 46.2, 28.2, 0, 3.3, "#6d737c", { ns: 1, see: 1 }); bx(54, 32, 54.2, 32.2, 0, 3.9, "#6d737c", { ns: 1, see: 1 });
  // --- spot 3: the projector cabinet in booth 1 ---
  bx(6, 38.9, 6.05, 39.8, 4.8, 6.3, "#4b5058"); bx(7.35, 38.9, 7.4, 39.8, 4.8, 6.3, "#4b5058"); bx(6, 38.9, 7.4, 39.8, 6.25, 6.3, "#4b5058"); door(6.05, 38.87, 7.35, 38.93, 4.8, 1.45, "#5d6470", "a", "n", 3);
  // --- spot 4: film storage at the end of the projection level ---
  wallY(36.15, 39.85, 85, 4.8, 8, K.wall, [[37.4, 38.5]]); lintel(84.85, 37.4, 85.15, 38.5, 7.0, 8, K.wall); door(84.97, 37.4, 85.03, 38.5, 4.8, 2.2, "#8d949e", "a", "w", 4);
  bx(84.85, 36.15, 89.85, 39.85, 7.9, 8.0, K.wall, { g: "lid", ns: 1 });
  bx(86, 36.3, 89.7, 36.8, 4.8, 7.0, "#6d737c"); bx(86.6, 38.2, 88.2, 38.7, 4.8, 6.6, "#6d737c");
  for (let x = 86.1; x < 89.6; x += 0.45) [5.0, 5.6, 6.2].forEach((z) => mesh(new T.CylinderGeometry(0.19, 0.19, 0.06, 16), cpick(["#b9c0c8", "#9aa0a8", "#e8b23a"]), x + 0.2, 36.86, z + 0.2).rotation.x = Math.PI / 2);
  sign("FILM STORAGE", 84.82, 37.95, 7.3, -Math.PI / 2, 1.0, { bg: "#30353d", two: false });
  // --- spot 5: the curtain wing beside screen 3 ---
  bx(61.8, 3.15, 61.95, 5.4, 0, 8, K.redD); bx(60.9, 5.4, 61.95, 5.55, 0, 8, K.redD); bx(60.15, 4.3, 61.05, 4.45, 0, 8, K.redD);
  [0, 30].forEach((a) => bx(a + 0.15, 3.15, a + 0.5, 5.4, 0, 8, K.redD));
  // --- spot 16: the hollow bass speaker behind screen 3 ---
  bx(66, 0.3, 66.06, 1.6, 0, 1.6, K.black); bx(67.74, 0.3, 67.8, 1.6, 0, 1.6, K.black); bx(66, 0.3, 67.8, 0.36, 0, 1.6, K.black); bx(66, 0.3, 67.8, 1.6, 1.54, 1.6, K.black);
  door(66.06, 1.57, 67.74, 1.63, 0, 1.54, "#2a2d38", "a", "s", 16);
  // =====================================================================
  //  THE LOBBY (y 40-60)
  // =====================================================================
  // concessions: back counter with popcorn machines, front counter, canopy with the menu on it
  bx(36, 40.15, 50, 41.1, 0, 1.0, K.steel); [37, 42, 47].forEach((x) => { bx(x, 40.25, x + 1.6, 41.0, 1.0, 2.2, "#c8202c", { ns: 1 }); bx(x + 0.1, 41.0, x + 1.5, 41.02, 1.15, 2.05, "#ffe9a8", { k: "glow", ns: 1, see: 1 }); });
  bx(34.2, 44, 50.8, 45, 0, 1.05, K.wood); bx(34.1, 43.9, 50.9, 45.1, 1.05, 1.1, "#2a2d38", { ns: 1 }); bx(34.2, 41.2, 35.2, 44, 0, 1.05, K.wood);
  for (let x = 36; x < 50; x += 4) { bx(x, 44.2, x + 0.5, 44.6, 1.1, 1.45, "#2f3766", { ns: 1, see: 1 }); bx(x + 1.2, 44.15, x + 2.6, 44.85, 1.1, 1.5, "#e9ecef", { k: "glass", ns: 1 }); for (let k = 0; k < 4; k++) bx(x + 1.3 + k * 0.32, 44.3, x + 1.55 + k * 0.32, 44.7, 1.1, 1.3, cpick(["#ffd25a", "#ff5c8a", "#a55be0", "#5bd18b", "#6b3d1f"]), { ns: 1, see: 1 }); }
  bx(34, 40.15, 52.15, 45.6, 3.05, 3.2, "#30353d", { st: 1 });
  bx(34, 45.45, 52.15, 45.6, 3.2, 4.9, K.black); bx(34, 40.15, 34.15, 45.6, 3.2, 4.9, K.black); bx(52, 41.3, 52.15, 45.6, 3.2, 4.9, K.black);
  [[34.05, 45.3], [43, 45.3], [34.05, 41.3]].forEach(([x, y]) => bx(x, y, x + 0.25, y + 0.25, 0, 3.05, K.gold));
  bx(34.2, 45.3, 51.9, 45.42, 2.85, 3.05, K.gold, { k: "glow", ns: 1, see: 1 });
  // stock room with the steep stairs up to the canopy
  wallY(40.15, 45.15, 52, 0, 3.2, K.wall, [[42, 43.1]]); lintel(51.85, 42, 52.15, 43.1, 2.2, 3.2, K.wall); door(51.97, 42, 52.03, 43.1, 0, 2.2, "#8d949e", "a", "w", 7);
  wallY(40.15, 45.15, 58, 0, 3.2, K.wall); wallX(52, 58, 45, 0, 3.2, K.wall);
  bx(52.15, 41.3, 57.85, 44.85, 3.05, 3.2, "#30353d", { st: 1, g: "lid" });
  bx(57.85, 40.15, 58.15, 45.15, 3.2, 4.9, K.black); bx(52, 44.85, 58.15, 45.15, 3.2, 4.9, K.black);
  stairs(52.3, 40.3, 57.5, 41.2, 0, 3.2, 8, "w", "#e8a92a", true);
  bx(55.6, 43.3, 56.6, 44.85, 0, 1.9, "#d8c39a"); bx(56.6, 43.3, 57.2, 43.7, 0, 1.9, "#c9b386"); bx(52.3, 43.6, 54.4, 44.8, 0, 1.5, "#b8895a"); bx(52.3, 41.4, 53.2, 41.9, 0, 1.0, "#f2c14e");
  sign("STOCK", 51.82, 42.55, 2.45, -Math.PI / 2, 0.8, { bg: "#30353d", two: false });
  // cup boxes on the canopy (spot 15)
  bx(37.5, 41.2, 38.3, 45.45, 3.2, 4.9, "#b8895a"); bx(35.6, 40.15, 36.4, 44.3, 3.2, 4.9, "#a87b4f");
  // janitor closet (spot 9)
  wallY(40.15, 43.65, 64, 0, 3.2, K.wall); wallY(40.15, 43.65, 67.5, 0, 3.2, K.wall); wallX(64, 67.5, 43.5, 0, 3.2, K.wall, [[65.2, 66.3]]); lintel(65.2, 43.35, 66.3, 43.65, 2.2, 3.2, K.wall); door(65.2, 43.47, 66.3, 43.53, 0, 2.2, "#8d949e", "a", "s", 9);
  bx(63.85, 40.15, 67.65, 43.65, 3.2, 3.32, K.wall, { g: "lid", ns: 1 });
  bx(64.3, 40.3, 65.0, 41.0, 0, 0.5, "#f2c14e"); bx(64.6, 40.6, 64.66, 40.66, 0.5, 1.5, "#8a6a4a", { ns: 1, see: 1 }); bx(66.6, 40.3, 67.3, 42.6, 0, 1.9, "#6d737c");
  sign("JANITOR", 65.75, 43.7, 2.45, 0, 0.8, { bg: "#30353d", two: false });
  // self-serve drinks island with the hollow cupboard (spot 8)
  bx(24.6, 46, 28, 47.2, 0, 1.0, K.wood); bx(22, 46, 22.06, 47.2, 0, 1.0, K.wood); bx(22, 46, 24.6, 46.06, 0, 1.0, K.wood); bx(22, 46, 24.6, 47.2, 0.94, 1.0, K.wood); bx(23.3, 47.14, 24.6, 47.2, 0, 0.94, K.wood);
  door(22.06, 47.14, 23.3, 47.2, 0, 0.94, "#7a5a44", "a", "s", 8);
  bx(21.95, 45.95, 28.05, 47.25, 1.0, 1.05, "#2a2d38", { ns: 1 });
  [22.4, 24.0, 25.6].forEach((x) => { bx(x, 46.15, x + 1.3, 46.9, 1.05, 1.95, "#c8202c", { ns: 1 }); bx(x + 0.1, 46.9, x + 1.2, 46.92, 1.45, 1.85, "#ffe2b8", { k: "glow", ns: 1, see: 1 }); });
  bx(27.1, 46.2, 27.8, 46.9, 1.05, 1.5, "#e9ecef", { ns: 1, see: 1 });
  sign("DRINKS · HELP YOURSELF", 25, 46.6, 2.6, 0, 2.6, { bg: "#14151a", fg: "#e8b23a" });
  // ticket kiosks by the entrance
  [35.5, 38, 51.4, 53.9].forEach((x) => { bx(x, 53.7, x + 0.7, 54.2, 0, 1.25, "#2a2d38"); bx(x + 0.08, 54.2, x + 0.62, 54.22, 0.75, 1.2, "#7ad0ff", { k: "glow", ns: 1, see: 1 }); bx(x + 0.05, 53.75, x + 0.65, 54.15, 1.25, 1.5, "#c8202c", { ns: 1, see: 1 }); });
  // guest services desk and the lost and found closet (spot 12)
  bx(62, 55.6, 70, 56.6, 0, 1.05, K.wood); bx(61.9, 55.5, 70.1, 56.7, 1.05, 1.1, "#2a2d38", { ns: 1 });
  sign("GUEST SERVICES", 66, 59.8, 3.0, Math.PI, 3.2, { bg: "#14151a", fg: "#e8b23a", two: false });
  wallY(56.5, 59.85, 71, 0, 3.2, K.wall, [[57.6, 58.7]]); lintel(70.85, 57.6, 71.15, 58.7, 2.2, 3.2, K.wall); door(70.97, 57.6, 71.03, 58.7, 0, 2.2, "#8d949e", "a", "w", 12);
  wallY(56.35, 59.85, 75, 0, 3.2, K.wall); wallX(70.85, 75.15, 56.5, 0, 3.2, K.wall); bx(70.85, 56.35, 75.15, 59.85, 3.2, 3.32, K.wall, { g: "lid", ns: 1 });
  bx(71.3, 56.8, 74.7, 57.3, 0, 2.0, "#6d737c"); bx(72.2, 58.6, 73.6, 59.7, 0, 0.9, "#b8895a");
  sign("LOST & FOUND", 70.82, 58.15, 2.45, -Math.PI / 2, 0.9, { bg: "#30353d", two: false });
  // manager's office (spot 11)
  wallY(40.15, 44.65, 13, 0, 3.2, K.wall); wallY(40.15, 44.65, 20, 0, 3.2, K.wall); wallX(13, 20, 44.5, 0, 3.2, K.wall, [[15.5, 16.6]]); lintel(15.5, 44.35, 16.6, 44.65, 2.2, 3.2, K.wall);
  door(15.5, 44.47, 16.6, 44.53, 0, 2.2, "#8d949e", "a", "s", 11); bx(12.85, 40.15, 20.15, 44.65, 3.2, 3.32, K.wall, { g: "lid", ns: 1 });
  bx(14, 41, 17, 42.2, 0.7, 0.76, K.wood); bx(14, 42.14, 17, 42.2, 0, 0.7, K.wood); bx(14, 41, 14.06, 42.2, 0, 0.7, K.wood); bx(16.94, 41, 17, 42.2, 0, 0.7, K.wood);
  bx(18.6, 40.3, 19.8, 41.0, 0, 1.8, "#6d737c"); bx(15, 41.3, 15.6, 41.5, 0.76, 1.15, "#1c1e26", { ns: 1, see: 1 });
  sign("MANAGER", 16.05, 44.7, 2.45, 0, 0.8, { bg: "#30353d", two: false });
  // party room (spot 13)
  wallX(0, 12.15, 48, 0, 3.2, K.wall); wallY(48, 60, 12, 0, 3.2, K.wall, [[52, 53.2]]); lintel(11.85, 52, 12.15, 53.2, 2.2, 3.2, K.wall); door(11.97, 52, 12.03, 53.2, 0, 2.2, K.door, "a", "e", 13);
  bx(0, 47.85, 12.15, 60, 3.2, 3.32, K.wall, { g: "lid", ns: 1 });
  bx(3, 53, 9, 53.05, 0, 0.75, "#f2c14e"); bx(3, 54.95, 9, 55, 0, 0.75, "#f2c14e"); bx(8.95, 53, 9, 55, 0, 0.75, "#f2c14e"); bx(2.95, 52.95, 9.05, 55.05, 0.75, 0.8, "#f6e7b3");
  door(2.97, 53.05, 3.03, 54.95, 0, 0.75, "#f2c14e", "a", "w", 13);
  for (let x = 3.4; x < 8.8; x += 1.1) { bx(x, 52.2, x + 0.5, 52.7, 0, 0.46, "#2f6db5"); bx(x, 55.3, x + 0.5, 55.8, 0, 0.46, "#c8202c"); bx(x + 0.1, 53.6, x + 0.4, 53.9, 0.8, 0.83, "#ffffff", { ns: 1, see: 1 }); }
  bx(5.4, 53.7, 6.6, 54.3, 0.8, 1.0, "#ff9ecb", { ns: 1, see: 1 }); bx(5.7, 53.85, 6.3, 54.15, 1.0, 1.15, "#ffffff", { ns: 1, see: 1 });
  sign("PARTY ROOM", 12.2, 52.6, 2.5, Math.PI / 2, 1.2, { bg: "#a55be0", two: false });
  [[2, "#ff5c8a"], [4.5, "#3fe0e8"], [7, "#ffd25a"], [9.5, "#5bd18b"]].forEach(([x, c]) => { mesh(new T.SphereGeometry(0.2, 12, 10), c, x, 59.4, 2.4).scale.y = 1.2; bx(x - 0.005, 59.39, x + 0.005, 59.41, 0, 2.2, "#ffffff", { ns: 1, see: 1 }); });
  // restrooms with stalls (spot 10)
  wallY(46, 60, 78, 0, 3.2, K.wall, [[48.5, 49.7], [56, 57.2]]); [[48.5, 49.7], [56, 57.2]].forEach(([a, b]) => lintel(77.85, a, 78.15, b, 2.3, 3.2, K.wall));
  wallX(78, 90, 46, 0, 3.2, K.wall); wallX(78, 90, 53, 0, 3.2, K.wall); bx(77.85, 45.85, 90, 60, 3.2, 3.32, K.wall, { g: "lid", ns: 1 });
  wallY(46.15, 51, 80, 0, 3.2, K.cream); wallY(55, 59.85, 80, 0, 3.2, K.cream);
  [[46.15, 1], [53.15, -1]].forEach(([y0, dir], r) => {
    const ys = r === 0 ? [46.15, 47.75, 49.35, 50.95] : [55.05, 56.65, 58.25, 59.85];
    for (let k = 0; k < 3; k++) { const a = ys[k], b = ys[k + 1];
      if (k > 0) bx(87.5, a - 0.03, 89.85, a + 0.03, 0, 2.4, K.cream);
      bx(87.47, a, 87.53, a + 0.3, 0, 2.4, K.cream); bx(87.47, b - 0.3, 87.53, b, 0, 2.4, K.cream); door(87.47, a + 0.3, 87.53, b - 0.3, 0, 2.4, "#cfd5dc", "a", "w", r === 0 && k === 1 ? 10 : 0);
      bx(89.2, (a + b) / 2 - 0.25, 89.8, (a + b) / 2 + 0.25, 0, 0.42, K.white); bx(89.6, (a + b) / 2 - 0.25, 89.8, (a + b) / 2 + 0.25, 0.42, 0.85, K.white, { ns: 1, see: 1 }); }
    if (r === 0) bx(87.47, 50.95, 87.53, 52.85, 0, 2.4, K.cream); else bx(87.47, 53.15, 87.53, 55.05, 0, 2.4, K.cream);
    const sy = r === 0 ? 46.3 : 59.2; bx(81.5, sy, 85.5, sy + 0.5, 0.7, 0.86, K.white); sol(81.5, sy, 85.5, sy + 0.5, 0, 0.86); bx(81.5, sy + (r === 0 ? -0.13 : 0.5), 85.5, sy + (r === 0 ? 0 : 0.63), 1.1, 2.0, "#bfe6ff", { k: "glow", ns: 1, see: 1 });
  });
  sign("RESTROOMS", 77.8, 52.9, 2.75, -Math.PI / 2, 2.0, { bg: "#14151a", fg: "#e8b23a", two: false });
  // the giant movie standee (spot 14): hollow, with a flap at the back
  bx(26, 54.5, 26.06, 56.5, 0, 2.6, "#2f6db5"); bx(28.34, 54.5, 28.4, 56.5, 0, 2.6, "#2f6db5"); bx(26, 56.44, 28.4, 56.5, 0, 2.6, "#2f6db5"); bx(26, 54.5, 28.4, 56.5, 2.6, 2.66, "#24553a");
  bx(26.06, 54.5, 26.6, 54.56, 0, 2.6, "#2f6db5"); bx(27.8, 54.5, 28.34, 54.56, 0, 2.6, "#2f6db5"); bx(26.6, 54.5, 27.8, 54.56, 2.0, 2.6, "#2f6db5"); door(26.6, 54.47, 27.8, 54.53, 0, 2.0, "#27589a", "a", "n", 14);
  // sofas, usher stands, rope posts, bins
  [[14.5, 52], [14.5, 55.2]].forEach(([x, y]) => { bx(x, y, x + 4, y + 1.1, 0, 0.45, "#5b3d6b"); bx(x, y + 0.85, x + 4, y + 1.1, 0.45, 0.95, "#5b3d6b", { ns: 1 }); bx(x, y, x + 0.25, y + 1.1, 0.45, 0.7, "#4a3158", { ns: 1, see: 1 }); bx(x + 3.75, y, x + 4, y + 1.1, 0.45, 0.7, "#4a3158", { ns: 1, see: 1 }); });
  bx(15.5, 53.6, 17.5, 54.6, 0, 0.4, K.wood);
  AUD.forEach((a) => { bx(a + 2.9, 41, a + 3.5, 41.5, 0, 1.15, K.wood); bx(a + 3.0, 41.1, a + 3.4, 41.4, 1.15, 1.3, "#7ad0ff", { k: "glow", ns: 1, see: 1 }); });
  [[38.6, 51], [47, 51]].forEach(([x, y]) => { [0, 2.2, 4.4].forEach((d) => { bx(x + d - 0.05, y - 0.05, x + d + 0.05, y + 0.05, 0, 1.0, K.gold); bx(x + d - 0.18, y - 0.18, x + d + 0.18, y + 0.18, 0, 0.04, K.gold, { ns: 1, see: 1 }); }); bx(x, y - 0.02, x + 4.4, y + 0.02, 0.86, 0.92, K.red, { ns: 1, see: 1 }); sol(x, y - 0.04, x + 4.4, y + 0.04, 0, 0.95); });
  [[30.6, 46.4], [58.4, 51], [20.6, 58.6], [76.6, 44.6]].forEach(([x, y]) => bx(x, y, x + 0.6, y + 0.6, 0, 1.0, "#30353d"));
  sign("TICKETS", 45, 54, 4.4, 0, 3.4, { bg: "#14151a", fg: "#e8b23a" });
  // ---------- the three screens play the same looping "movie" ----------
  const FUN = { trailer: 0 };
  const scrC = document.createElement("canvas"); scrC.width = 512; scrC.height = 256; const scrG = scrC.getContext("2d");
  const scrTex = new T.CanvasTexture(scrC); scrTex.encoding = T.sRGBEncoding;
  function drawScreen(s) {
    const g = scrG, W = 512, H = 256;
    if (FUN.trailer > 0) { g.fillStyle = "#050508"; g.fillRect(0, 0, W, H); g.fillStyle = "#e8e2d0"; g.textAlign = "center"; g.font = `800 34px ${FONT}`; const L = ["IN A WORLD…", "WHERE EVERYONE HIDES…", "ONE SEEKER…", "HAS NO IDEA WHERE ANYBODY IS."]; g.fillText(L[Math.min(3, Math.floor((12 - FUN.trailer) / 3))], W / 2, H / 2 + 10); return; }
    const hue = (s * 14) % 360, gr = g.createLinearGradient(0, 0, W, H); gr.addColorStop(0, `hsl(${hue},60%,22%)`); gr.addColorStop(1, `hsl(${(hue + 70) % 360},65%,38%)`); g.fillStyle = gr; g.fillRect(0, 0, W, H);
    g.fillStyle = "rgba(255,255,255,.14)"; for (let k = 0; k < 5; k++) { g.beginPath(); for (let x = 0; x <= W; x += 16) g.lineTo(x, 150 + k * 22 + Math.sin(x * 0.02 + s * (0.6 + k * 0.2) + k) * 12); g.lineTo(W, H); g.lineTo(0, H); g.fill(); }
    const fx = 256 + Math.sin(s * 0.5) * 170, fy = 150 + Math.sin(s * 1.3) * 8; g.fillStyle = "#3fe0e8"; g.beginPath(); g.moveTo(fx - 26, fy); g.quadraticCurveTo(fx - 4, fy - 46, fx + 20, fy - 54); g.quadraticCurveTo(fx + 12, fy - 22, fx + 28, fy); g.fill();
    g.fillStyle = "#fff"; g.textAlign = "center"; g.font = `800 30px ${FONT}`; g.fillText("HIDE & SEEK: THE MOVIE", W / 2, 54); g.font = `600 17px ${FONT}`; g.globalAlpha = 0.85; g.fillText("Now playing on every screen", W / 2, 82); g.globalAlpha = 1;
  }
  drawScreen(0);
  { const sm = new T.MeshBasicMaterial({ map: scrTex, toneMapped: false }); SCREENS.forEach(([x, y, z]) => { const m = new T.Mesh(new T.PlaneGeometry(22, 8.4), sm); m.position.set(WX(x), z, WZ(y)); G.main.add(m); }); }
  const SCR_LIGHTS = SCREENS.map(([x, y, z]) => { const l = new T.PointLight(0x9fc4ff, 1.5, 30, 2); l.position.set(WX(x), z, WZ(y + 5)); scene.add(l); return l; });
  const LOBBY_LIGHTS = [[20, 51, 5, 0xffd9a0, 1.3, 28], [70, 51, 5, 0xffd9a0, 1.3, 28], [43, 43.4, 2.6, 0xffc24a, 1.6, 9]].map(([x, y, z, c, i, d]) => { const l = new T.PointLight(c, i, d, 2); l.position.set(WX(x), z, WZ(y)); scene.add(l); return l; });
  // ---------- movie posters along the lobby wall (all made up) ----------
  const POSTERS = [["HIDE HARDER", "This time, nobody finds anybody", "#8c1f2b", "#e8b23a"], ["SEEK NO EVIL", "They counted to 45. They should have counted higher.", "#1d3a6b", "#9fd4ff"], ["THE QUIET AISLE", "Don't. Make. A squeak.", "#1f2329", "#5bd18b"], ["BARRY: A BASS STORY", "He sings. He flaps. He knows where you are.", "#24553a", "#ffd25a"],
    ["CART WHEEL DRIFT", "One bad wheel. No brakes.", "#5b3d6b", "#ff9a3c"], ["SPILL ON AISLE FOUR", "Days since last spill: 0", "#1d6fb8", "#ffffff"], ["MOCHI: NINE NAPS", "The store cat's biggest day yet", "#7a5c9e", "#ffe2b8"], ["THE LAST KERNEL", "It was at the bottom of the bag the whole time", "#b8742a", "#fff4c2"], ["DO NOT PRESS 2", "They pressed it again", "#14151a", "#ff2b2b"]];
  [5, 8.6, 23, 27, 69.5, 73, 78.5, 82, 85.5].forEach((x, i) => { const p = POSTERS[i];
    plate(canvasTex(360, 520, (g, w, h) => { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, p[2]); gr.addColorStop(1, "#0b0d1a"); g.fillStyle = gr; g.fillRect(0, 0, w, h);
      g.fillStyle = p[3]; g.globalAlpha = 0.9; g.beginPath(); if (i % 3 === 0) g.arc(w / 2, 210, 90, 0, 7); else if (i % 3 === 1) { g.moveTo(w / 2, 110); g.lineTo(w / 2 + 100, 300); g.lineTo(w / 2 - 100, 300); } else g.ellipse(w / 2, 220, 120, 60, -0.3, 0, 7); g.fill(); g.globalAlpha = 1;
      g.textAlign = "center"; g.fillStyle = "#fff"; let sz = 46; g.font = `800 ${sz}px ${FONT}`; const ws = p[0].split(" "); const ln = []; let cur = ""; ws.forEach((wd) => { if (g.measureText(cur + " " + wd).width > w - 40 && cur) { ln.push(cur); cur = wd; } else cur = cur ? cur + " " + wd : wd; }); ln.push(cur); ln.forEach((t, k) => g.fillText(t, w / 2, 380 + k * 48));
      g.font = `600 17px ${FONT}`; g.fillStyle = p[3]; const tg = p[1]; const half = tg.length > 34 ? tg.lastIndexOf(" ", 34) : -1; if (half > 0) { g.fillText(tg.slice(0, half), w / 2, 60); g.fillText(tg.slice(half + 1), w / 2, 82); } else g.fillText(tg, w / 2, 70); }), x, 40.27, 2.3, 0, 1.75, 2.5);
    bx(x - 0.95, 40.15, x + 0.95, 40.2, 0.98, 3.62, K.gold, { ns: 1, see: 1 }); });
  plate(canvasTex(1024, 256, (g, w, h) => { g.fillStyle = "#0b0d1a"; g.fillRect(0, 0, w, h); g.fillStyle = "#e8b23a"; g.font = `800 60px ${FONT}`; g.textAlign = "center"; g.fillText("POPCORN · DRINKS · CANDY", w / 2, 90); g.font = `600 27px ${FONT}`; g.fillStyle = "#fff"; g.fillText("Small bucket: huge   ·   Medium: also huge   ·   Large: please ask for help", w / 2, 170); g.fillStyle = "#ff9a3c"; g.fillText("Refills are free. So is the regret.", w / 2, 222); }), 43, 45.67, 4.2, 0, 13.6, 1.3);
  // ---------- Mochi the cat, asleep on a recliner in the top row of screen 1 ----------
  { const cat = fgrp(15.2, 32.58, 5.27); const add = (geo, x, y, z) => { const m = new T.Mesh(geo, lam("#e08a3c")); m.position.set(x, y, z); cat.add(m); return m; };
    add(new T.SphereGeometry(0.2, 14, 10), 0, 0.12, 0).scale.set(1.25, 0.6, 0.95); add(new T.SphereGeometry(0.11, 12, 10), 0.2, 0.16, 0.08); add(new T.ConeGeometry(0.04, 0.08, 6), 0.2, 0.28, 0.03); add(new T.ConeGeometry(0.04, 0.08, 6), 0.2, 0.28, 0.13);
    add(new T.TorusGeometry(0.17, 0.03, 6, 14, Math.PI), -0.06, 0.08, 0.14).rotation.x = Math.PI / 2; FUN.cat = { g: cat, x: 15.2, y: 32.58, f: 4.8 }; }
  // ---------- the DO NOT PRESS button in the projection room of screen 2 ----------
  { bx(44.7, 39.75, 45.3, 39.85, 5.75, 6.2, "#ffd23f", { ns: 1, see: 1 }); const b = new T.Mesh(new T.CylinderGeometry(0.11, 0.11, 0.08, 18), new T.MeshBasicMaterial({ color: 0xff2b2b, toneMapped: false })); b.rotation.x = Math.PI / 2; b.position.set(WX(45), 5.97, WZ(39.71)); G.main.add(b);
    sign("DO NOT PRESS", 45, 39.84, 6.4, Math.PI, 0.9, { bg: "#1f2329", fg: "#ffd23f", two: false }); FUN.btn = { b, x: 45, y: 39.4, armed: true }; }
  // ---------- popcorn that bursts out of the middle machine ----------
  { const geo = new T.SphereGeometry(0.035, 6, 5), mat = lam("#fff4c2"), ks = []; for (let k = 0; k < 44; k++) { const m = new T.Mesh(geo, mat); m.visible = false; G.main.add(m); ks.push({ m, p: [0, 0, 0], v: [0, 0, 0] }); } FUN.pop = { ks, t: -1, x: 42.8, y: 41.7, armed: true }; }
  // ---------- the sticky patch at the front of screen 2 ----------
  plate(canvasTex(256, 256, (g) => { g.fillStyle = "rgba(60,20,10,.55)"; g.beginPath(); g.ellipse(128, 128, 112, 84, 0.3, 0, 7); g.fill(); g.fillStyle = "rgba(255,255,255,.12)"; g.beginPath(); g.ellipse(100, 104, 36, 12, 0.3, 0, 7); g.fill(); }), 45, 11.2, 0.02, 0, 2.4, 2.4, { floor: 1, alpha: 1 });
  // ================= ROUND 2: make it feel like a theater =================
  // red panelling with a gold rail along the lobby walls
  const dadoX = (a, b, y, s) => { bx(a, y, b, y + 0.05 * s, 0, 0.9, K.red, { ns: 1, see: 1 }); bx(a, y, b, y + 0.08 * s, 0.9, 0.97, K.gold, { ns: 1, see: 1 }); };
  const dadoY = (a, b, x, s) => { bx(x, a, x + 0.05 * s, b, 0, 0.9, K.red, { ns: 1, see: 1 }); bx(x, a, x + 0.08 * s, b, 0.9, 0.97, K.gold, { ns: 1, see: 1 }); };
  [[2.3, 12.8], [20.2, 30.7], [32.3, 33.9], [58.2, 60.7], [62.3, 63.8], [67.7, 89.8]].forEach(([a, b]) => dadoX(a, b, 40.15, 1));
  dadoX(13, 15.4, 44.65, 1); dadoX(16.7, 20.15, 44.65, 1); dadoX(0.15, 12, 47.85, -1); dadoY(48, 51.9, 12.15, 1); dadoY(53.3, 59.85, 12.15, 1);
  dadoY(46, 48.4, 77.85, -1); dadoY(57.3, 59.85, 77.85, -1); dadoX(78, 89.85, 45.85, -1); dadoY(40.15, 45.8, 89.85, -1); dadoY(40.15, 47.8, 0.15, 1);
  // neon strips, the big neon name and the Now Showing board on the back wall
  bx(0.15, 40.15, 89.85, 40.2, 6.0, 6.08, "#ff4fa3", { k: "glow", ns: 1, see: 1 }); bx(0.15, 40.15, 89.85, 40.2, 6.25, 6.31, "#3fe0e8", { k: "glow", ns: 1, see: 1 });
  { const n1 = sign("GLOWFIN CINEMA", 22, 40.22, 7.6, 0, 9, { bg: "#0c0a12", fg: "#3fe0e8", two: false }); const n2 = sign("GLOWFIN CINEMA", 72, 40.22, 7.6, 0, 9, { bg: "#0c0a12", fg: "#ff4fa3", two: false }); }
  plate(canvasTex(1024, 384, (g, w, h) => { g.fillStyle = "#0b0d1a"; g.fillRect(0, 0, w, h); g.fillStyle = "#e8b23a"; g.fillRect(0, 0, w, 70); g.fillStyle = "#0b0d1a"; g.font = `800 50px ${FONT}`; g.textAlign = "center"; g.fillText("NOW SHOWING", w / 2, 54);
    const rows = [["1", "HIDE HARDER", "7:00   9:30", "#9fd4ff"], ["2", "HIDE & SEEK: THE MOVIE", "All day. Every day.", "#ffd25a"], ["3", "THE QUIET AISLE", "SOLD OUT (nobody is in there)", "#ff8fa3"]];
    rows.forEach((r, i) => { const y = 140 + i * 92; g.fillStyle = "#e8b23a"; g.beginPath(); g.arc(70, y - 16, 32, 0, 7); g.fill(); g.fillStyle = "#0b0d1a"; g.font = `800 40px ${FONT}`; g.textAlign = "center"; g.fillText(r[0], 70, y - 2); g.textAlign = "left"; g.fillStyle = "#fff"; g.font = `800 42px ${FONT}`; g.fillText(r[1], 130, y); g.textAlign = "right"; g.fillStyle = r[3]; g.font = `600 30px ${FONT}`; g.fillText(r[2], w - 30, y); }); }), 45, 40.22, 7.4, 0, 14, 5.25 * 0.6);
  // hanging globe lights
  { const gm = new T.MeshBasicMaterial({ color: 0xffe2b0, toneMapped: false }), gg = new T.SphereGeometry(0.34, 14, 10); for (let x = 8; x < 86; x += 9.6) [47.5, 54].forEach((y, r) => { const xx = x + r * 4.8; if (xx > 34 && xx < 58.2 && y < 48) return; const m = new T.Mesh(gg, gm); m.position.set(WX(xx), 6.6, WZ(y)); G.ceil.add(m); bx(xx - 0.015, y - 0.015, xx + 0.015, y + 0.015, 6.9, 10, "#6d737c", { ns: 1, see: 1, g: "ceil" }); }); }
  // marquee bulbs running along the canopy (animated)
  const bulbC = document.createElement("canvas"); bulbC.width = 1024; bulbC.height = 16; const bulbG = bulbC.getContext("2d"); const bulbTex = new T.CanvasTexture(bulbC); bulbTex.encoding = T.sRGBEncoding;
  function drawBulbs(ph) { bulbG.fillStyle = "#3a0d16"; bulbG.fillRect(0, 0, 1024, 16); for (let k = 0; k < 64; k++) { const on = (k + ph) % 3 === 0; bulbG.fillStyle = on ? "#fff3c4" : "#7a5a1c"; bulbG.beginPath(); bulbG.arc(8 + k * 16, 8, on ? 6 : 4.5, 0, 7); bulbG.fill(); } bulbTex.needsUpdate = true; }
  drawBulbs(0);
  { const bm = new T.MeshBasicMaterial({ map: bulbTex, toneMapped: false }); const add = (x, y, z, ry, w) => { const m = new T.Mesh(new T.PlaneGeometry(w, 0.26), bm); m.position.set(WX(x), z, WZ(y)); m.rotation.y = ry; G.main.add(m); };
    add(43.07, 45.66, 3.33, 0, 18.1); add(33.94, 42.9, 3.33, -Math.PI / 2, 5.4); add(45, 59.94, 3.25, Math.PI, 8.2); }
  // red carpet from the doors to the counter, with a walk of fame
  bx(43.4, 45.7, 46.6, 59.85, 0, 0.015, "#a3162a", { ns: 1, see: 1 }); bx(43.4, 45.7, 43.48, 59.85, 0.015, 0.02, K.gold, { ns: 1, see: 1 }); bx(46.52, 45.7, 46.6, 59.85, 0.015, 0.02, K.gold, { ns: 1, see: 1 });
  [["MOCHI", 47.3], ["BARRY THE BASS", 49.3], ["THE RUBBER CHICKEN", 51.6], ["THE WOBBLY CART", 53.8], ["BEN", 56.2]].forEach(([nm, y], i) => plate(canvasTex(256, 256, (g) => { g.fillStyle = i === 4 ? "#ffd23f" : "#e8b23a"; g.beginPath(); for (let j = 0; j < 10; j++) { const a = j * Math.PI / 5, r = j % 2 ? 52 : 120; g.lineTo(128 + Math.sin(a) * r, 128 - Math.cos(a) * r); } g.fill(); g.fillStyle = "#57182a"; g.textAlign = "center"; let sz = 30; g.font = `800 ${sz}px ${FONT}`; while (g.measureText(nm).width > 104 && sz > 10) { sz -= 2; g.font = `800 ${sz}px ${FONT}`; } g.fillText(nm, 128, 138); }), 45, y, 0.024, 0, i === 4 ? 2.0 : 1.5, i === 4 ? 2.0 : 1.5, { floor: 1, alpha: 1 }));
  // arcade corner by the restrooms: three cabinets and a claw machine full of Mochis
  [50.2, 51.55, 52.9].forEach((y, k) => { const c = ["#2f6db5", "#a55be0", "#3d9a5b"][k], gl = ["#7ad0ff", "#ff8fd0", "#aaff9a"][k];
    bx(76.95, y, 77.8, y + 1.0, 0, 1.9, c); bx(76.6, y + 0.08, 76.95, y + 0.92, 0.8, 0.95, K.black, { ns: 1 }); sol(76.6, y + 0.08, 76.95, y + 0.92, 0, 0.95);
    bx(76.93, y + 0.14, 76.95, y + 0.86, 1.05, 1.55, gl, { k: "glow", ns: 1, see: 1 }); bx(76.9, y + 0.05, 76.95, y + 0.95, 1.62, 1.86, "#ffd25a", { k: "glow", ns: 1, see: 1 }); bx(76.68, y + 0.3, 76.74, y + 0.36, 0.95, 1.08, "#c8202c", { ns: 1, see: 1 }); });
  bx(76.8, 54.25, 77.8, 55.25, 0, 0.9, "#c8202c"); bx(76.8, 54.25, 77.8, 55.25, 1.75, 1.9, "#c8202c", { ns: 1 }); bx(76.82, 54.27, 77.78, 55.23, 0.9, 1.75, "#bfe6ff", { k: "glass", ns: 1 }); sol(76.8, 54.25, 77.8, 55.25, 0.9, 1.9);
  for (let k = 0; k < 9; k++) mesh(new T.SphereGeometry(0.11, 10, 8), "#e08a3c", 76.98 + (k % 3) * 0.3 + 0.05, 54.45 + Math.floor(k / 3) * 0.3, 1.0 + (k % 2) * 0.05);
  const claw = fgrp(77.3, 54.75, 1.68); fpart(claw, new T.CylinderGeometry(0.012, 0.012, 0.3, 6), "#9aa0a8", 0, -0.1, 0); fpart(claw, new T.ConeGeometry(0.07, 0.1, 3), "#9aa0a8", 0, -0.28, 0).rotation.x = Math.PI;
  sign("ARCADE", 77.8, 52.2, 2.2, -Math.PI / 2, 1.3, { bg: "#0c0a12", fg: "#3fe0e8", two: false });
  // potted palms
  [[33, 59.1], [57, 59.1], [76.2, 59.2], [13.2, 59.2], [89.1, 41], [89.1, 45.2], [0.9, 41], [0.9, 47.1]].forEach(([x, y]) => { bx(x - 0.28, y - 0.28, x + 0.28, y + 0.28, 0, 0.6, "#8a5a3c"); for (let k = 0; k < 7; k++) { const l = mesh(new T.ConeGeometry(0.12, 1.5, 5), k % 2 ? "#2f7a4a" : "#3d9a5b", x + Math.cos(k * 0.9) * 0.3, y + Math.sin(k * 0.9) * 0.3, 1.25); l.rotation.set(Math.sin(k * 0.9) * 0.5, 0, -Math.cos(k * 0.9) * 0.5); } });
  // cardboard cutouts
  const cutout = (x, y, ry, paint) => { const t = canvasTex(256, 448, paint); plate(t, x, y, 0.98, ry, 1.05, 1.84, { alpha: 1 }); plate(t, x, y, 0.98, ry + Math.PI, 1.05, 1.84, { alpha: 1 }); bx(x - 0.3, y - 0.2, x + 0.3, y + 0.2, 0, 0.06, K.dark, { ns: 1, see: 1 }); sol(x - 0.35, y - 0.06, x + 0.35, y + 0.06, 0, 1.8); };
  cutout(31.5, 53.4, 0, (g) => { g.fillStyle = "#ff5c8a"; g.beginPath(); g.ellipse(128, 250, 96, 150, 0, 0, 7); g.fill(); g.fillStyle = "#fff"; [96, 160].forEach((x) => { g.beginPath(); g.arc(x, 190, 22, 0, 7); g.fill(); }); g.fillStyle = "#111"; [100, 164].forEach((x) => { g.beginPath(); g.arc(x, 194, 10, 0, 7); g.fill(); }); g.strokeStyle = "#111"; g.lineWidth = 10; g.beginPath(); g.arc(200, 300, 30, 0, 7); g.stroke(); g.beginPath(); g.moveTo(180, 322); g.lineTo(150, 370); g.stroke(); g.fillStyle = "#fff"; g.font = `800 30px ${FONT}`; g.textAlign = "center"; g.fillText("THE SEEKER", 128, 60); g.font = `600 18px ${FONT}`; g.fillText("has checked the shed", 128, 430); });
  cutout(58.5, 53.4, 0, (g) => { g.fillStyle = "#1d3a6b"; g.fillRect(8, 20, 240, 420); g.fillStyle = "#ffd25a"; g.font = `800 28px ${FONT}`; g.textAlign = "center"; g.fillText("YOUR FACE HERE", 128, 70); g.fillStyle = "#e08a3c"; g.beginPath(); g.ellipse(128, 320, 80, 100, 0, 0, 7); g.fill(); g.globalCompositeOperation = "destination-out"; g.beginPath(); g.arc(128, 170, 52, 0, 7); g.fill(); g.globalCompositeOperation = "source-over"; g.strokeStyle = "#ffd25a"; g.lineWidth = 6; g.beginPath(); g.arc(128, 170, 55, 0, 7); g.stroke(); });
  // more behind the counter: candy wall, soda taps, butter pump, buckets and cups
  [35.4, 35.9, 36.4].forEach((x, k) => { bx(x, 44.25, x + 0.3, 44.55, 1.1, 1.42, k % 2 ? "#f4f4f4" : "#c8202c", { ns: 1, see: 1 }); bx(x + 0.02, 44.27, x + 0.28, 44.53, 1.42, 1.5, "#ffe9a8", { ns: 1, see: 1 }); });
  for (let x = 38.6; x < 50; x += 4) { [0, 0.22, 0.44].forEach((d, k) => bx(x + d, 44.7, x + d + 0.16, 44.86, 1.1, 1.28 + k * 0.05, k === 1 ? "#f4f4f4" : "#2f6db5", { ns: 1, see: 1 })); }
  bx(35.3, 41.3, 35.9, 43.8, 1.05, 1.9, K.steel, { ns: 1 }); [41.5, 42.1, 42.7, 43.3].forEach((y, k) => bx(35.9, y, 35.95, y + 0.4, 1.3, 1.75, ["#c8202c", "#2f6db5", "#3d9a5b", "#ff9a3c"][k], { k: "glow", ns: 1, see: 1 }));
  bx(48.9, 40.3, 49.4, 40.8, 1.0, 1.5, "#ffd25a", { ns: 1, see: 1 }); bx(49.1, 40.5, 49.2, 40.6, 1.5, 1.75, K.steel, { ns: 1, see: 1 }); sign("BUTTER", 49.15, 40.82, 1.28, 0, 0.4, { bg: "#ffd25a", fg: "#57182a", sub: "one pump is never enough", two: false });
  for (let x = 39.2; x < 49; x += 5) for (let k = 0; k < 6; k++) bx(x + (k % 3) * 0.42, 40.2, x + (k % 3) * 0.42 + 0.36, 40.26, 2.3 + Math.floor(k / 3) * 0.34, 2.58 + Math.floor(k / 3) * 0.34, cpick(["#ffd25a", "#ff5c8a", "#a55be0", "#5bd18b", "#3fe0e8"]), { ns: 1, see: 1 });
  // little things: kiosk floor arrows, door mats, bench by the screens, ticket stub boxes, spilled popcorn
  bx(41.2, 59.0, 48.8, 59.8, 0, 0.02, "#22252e", { ns: 1, see: 1 });
  [[24, 42.2], [70, 42.2]].forEach(([x, y]) => { bx(x, y, x + 3, y + 0.55, 0.4, 0.46, "#5b3d6b", { ns: 1 }); sol(x, y, x + 3, y + 0.55, 0, 0.46); [x + 0.1, x + 2.8].forEach((l) => bx(l, y + 0.1, l + 0.1, y + 0.45, 0, 0.4, K.gold, { ns: 1, see: 1 })); });
  AUD.forEach((a) => { bx(a + 3.05, 41.15, a + 3.35, 41.35, 1.3, 1.55, K.red, { ns: 1, see: 1 }); sign("TICKETS PLEASE", a + 3.2, 41.52, 0.8, 0, 0.5, { bg: "#14151a", fg: "#e8b23a", two: false }); });
  [[36, 48.2], [55, 47.5], [29, 43], [61.5, 44.5], [47.5, 52.5]].forEach(([x, y]) => plate(spillTex, x, y, 0.022, cq() * 6, 1.0, 1.0, { floor: 1, alpha: 1 }));
  // a starry ceiling in screen 1
  for (let k = 0; k < 170; k++) { const x = 1 + cq() * 28, y = 4 + cq() * 31, s = 0.03 + cq() * 0.04; bx(x, y, x + s, y + s, 9.93, 9.97, cq() < 0.8 ? "#fff6dc" : "#9fd4ff", { k: "glow", g: "ceil", ns: 1, see: 1 }); }
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
  //  THE 16 HIDING SPOTS
  // =====================================================================
  // f = floor height under the hider, s = size of the hider, enc = the hiding place itself [x1,y1,x2,y2,z1,z2]
  // (seeker spots inside enc are not counted), see = where a seeker stands for "Seeker's view"
  const SPOTS = window.WH_SPOTS;
  const COLS = ["#3FE0E8", "#7AA7FF", "#F5C04A", "#5BD18B", "#A55BE0", "#ff9a3c", "#1FA8E6", "#e3e6ff"];
  const hiders = [], pins = [];
  SPOTS.forEach((s, i) => {
    const h = friend(COLS[i % COLS.length], s.s || 1);
    h.position.set(WX(s.x), s.f, WZ(s.y)); h.rotation.y = s.face || 0;
    G.fx.add(h); hiders.push(h);
    const sp = new T.Sprite(new T.SpriteMaterial({ map: numTex(i + 1, "#3FE0E8", "#0B0D1A"), depthTest: false, transparent: true }));
    sp.scale.set(0.9, 0.9, 1); sp.position.set(WX(s.x), s.f + 1.25, WZ(s.y)); sp.renderOrder = 999; sp.visible = false;
    scene.add(sp); pins.push(sp);
  });
  // the seeker ("It") walking a loop round the store
  const it = friend("#FF5C8A", 1.15, true); G.fx.add(it); it.visible = false;
  const itRoute = [[45, 57.5], [31, 57.5], [31, 49.5], [60, 49.5], [60, 57.5]];

  // =====================================================================
  //  WALKING
  // =====================================================================
  const R = 0.28, STEP = 0.6, JUMP_V = 4.3, GRAV = 18;
  const P = { x: 45, y: 57.5, f: 0, H: 1.7, vy: 0, eye: 1.6, air: false, jf: 0 };
  let yaw = 0, pitch = 0.02, crouchHeld = false, ctrlHeld = false;
  const overlap = (b, x, y) => x + R > b.x1 && x - R < b.x2 && y + R > b.y1 && y - R < b.y2;
  // you can only stand on stairs, ladders and things at least 40 cm across (no balancing on railings or wall tops)
  const standable = (b) => b.st || Math.min(b.x2 - b.x1, b.y2 - b.y1) >= 0.4;
  // a jump can lift you onto things up to 1.25 m off the floor (tables, counters), never onto roofs or shelves
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
    for (const z of (window.WH_SLIDES || [])) if (P.f > 0.04 && P.x > z.x1 - 0.05 && P.x < z.x2 + 0.05 && P.y > z.y1 - 0.05 && P.y < z.y2) { const n = 4; for (let k = 0; k < n; k++) tryMove(P.x, P.y + 5.5 * dt / n); break; }
    const sup = supportAt(P.x, P.y, P.f);
    if (P.vy < 0) { // going up
      const nf = P.f - P.vy * dt; P.vy += GRAV * dt;
      if (hitAt(P.x, P.y, nf, P.H, null, Infinity)) P.vy = 0; else P.f = nf;
    } else if (sup < P.f - 0.001) { P.vy += GRAV * dt; P.f = Math.max(sup, P.f - P.vy * dt); if (P.f <= sup + 0.0001) { P.vy = 0; P.air = false; } }
    else { P.f = sup; P.vy = 0; P.air = false; }
    const crouch = crouchHeld || ctrlHeld;
    if (P.H < 1.7) { const up = crouch ? [0.9] : [1.7, 0.9]; for (const h of up) { if (h > P.H && !hitAt(P.x, P.y, P.f, h)) { P.H = h; break; } } }
    if (crouch && P.H > 0.9) P.H = 0.9;
    const want = P.f + (P.H > 1 ? 1.6 : P.H > 0.7 ? 0.72 : 0.45);
    P.eye += (want - P.eye) * Math.min(1, dt * (P.air ? 30 : 10));
  }
  // safety net: never end up inside something or outside the building
  const SAFE = { x: P.x, y: P.y, f: P.f };
  function inBounds(x, y) { return (x > 0.15 && x < 89.85 && y > 0.15 && y < 59.85); }
  function keepSafe() {
    if (!inBounds(P.x, P.y) || hitAt(P.x, P.y, P.f, P.H, null, P.f + STEP)) { P.x = SAFE.x; P.y = SAFE.y; P.f = SAFE.f; P.vy = 0; P.air = false; }
    else { SAFE.x = P.x; SAFE.y = P.y; SAFE.f = P.f; }
  }

  // =====================================================================
  //  MODES: walk / bird's-eye / tour
  // =====================================================================
  let mode = "walk", orbit = { a: -0.5, e: 1.0, r: 74 };
  const tourPts = window.WH_TOUR;
  let tourLen = 0; const tourCum = [0];
  for (let i = 1; i < tourPts.length; i++) { tourLen += Math.hypot(tourPts[i][0] - tourPts[i - 1][0], tourPts[i][1] - tourPts[i - 1][1]); tourCum.push(tourLen); }
  let tourD = 0;
  function tourAt(d) {
    d = ((d % tourLen) + tourLen) % tourLen;
    let i = 1; while (i < tourCum.length - 1 && tourCum[i] < d) i++;
    const a = tourPts[i - 1], b = tourPts[i], u = (d - tourCum[i - 1]) / Math.max(0.001, tourCum[i] - tourCum[i - 1]);
    return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, (a[2] || 0) + ((b[2] || 0) - (a[2] || 0)) * u];
  }
  function setVisibility() {
    const top = mode === "bird";
    G.ceil.visible = !top; G.lid.visible = !top;
  }
  const keys = {};
  let armed = false;
  stage.addEventListener("pointerdown", () => { armed = true; stage.focus({ preventScroll: true }); });
  document.addEventListener("pointerdown", (e) => { if (e.target !== stage && !e.target.closest(".pad")) armed = false; });
  const KEYMAP = { KeyW: "f", ArrowUp: "f", KeyS: "b", ArrowDown: "b", KeyA: "l", KeyD: "r", ArrowLeft: "tl", ArrowRight: "tr", KeyQ: "tl", KeyE: "tr" };
  window.addEventListener("keydown", (e) => {
    if (!armed) return;
    if (e.code === "KeyC") { crouchHeld = !crouchHeld; syncCrouch(); e.preventDefault(); return; }
    if (e.code === "Space") { keys.jump = 1; e.preventDefault(); if (mode !== "walk") setMode("walk"); return; }
    if (e.code === "ControlLeft") { ctrlHeld = true; e.preventDefault(); if (!kbLocked) showTip("Tip: in full screen, Left Ctrl is safe to hold. Outside it, Ctrl + W closes the tab, so use C there."); return; }
    if (e.ctrlKey && !kbLocked) { ctrlHeld = false; }
    const k = KEYMAP[e.code]; if (k) { keys[k] = 1; e.preventDefault(); if (mode !== "walk") setMode("walk"); }
    if (e.shiftKey) keys.run = 1;
  });
  window.addEventListener("keyup", (e) => { const k = KEYMAP[e.code]; if (k) keys[k] = 0; if (!e.shiftKey) keys.run = 0; if (e.code === "ControlLeft") ctrlHeld = false; });
  window.addEventListener("blur", () => { Object.keys(keys).forEach((k) => (keys[k] = 0)); ctrlHeld = false; });
  // ---- full screen: the whole lobby fills the screen and the keyboard is locked so shortcuts can't close the tab ----
  let kbLocked = false, tipT = 0;
  const host = () => { try { return (window.parent !== window && window.parent.GF_CINEMA_HOST) || {}; } catch (e) { return {}; } };
  const fsBtn = document.getElementById("fs"), jumpBtn = null, tipEl = document.getElementById("tip");
  function showTip(t) { if (!tipEl || performance.now() - tipT < 6000) return; tipT = performance.now(); tipEl.textContent = t; tipEl.hidden = false; setTimeout(() => (tipEl.hidden = true), 4500); }
  const lobbyEl = () => { try { return window.parent.document.getElementById("lb") || document.documentElement; } catch (e) { return document.documentElement; } };
  const isFull = () => { try { return !!(window.parent.document.fullscreenElement || document.fullscreenElement); } catch (e) { return !!document.fullscreenElement; } };
  async function enterFull() { armed = true; try { await lobbyEl().requestFullscreen({ navigationUI: "hide" }); } catch (e) {} }
  function exitFull() { try { const d = window.parent.document; if (d.fullscreenElement) d.exitFullscreen().catch(() => {}); } catch (e) {} }
  async function onFull() {
    const on = isFull();
    if (fsBtn) { fsBtn.textContent = on ? "Exit full screen" : "Full screen"; fsBtn.setAttribute("aria-pressed", String(on)); }
    kbLocked = false;
    try { const kb = window.parent.navigator.keyboard; if (kb && kb.lock) { if (on) { await kb.lock(); kbLocked = true; } else kb.unlock(); } } catch (e) {}
    resize(); kick();
  }
  document.addEventListener("fullscreenchange", onFull);
  if (fsBtn) fsBtn.addEventListener("click", () => (isFull() ? exitFull() : enterFull()));
  const grabMouse = () => { if (document.pointerLockElement !== stage) { try { const p = stage.requestPointerLock(); if (p && p.catch) p.catch(() => {}); } catch (e) {} } };
  window.addEventListener("keydown", (e) => {
    if (!armed || e.ctrlKey || e.metaKey) return;
    if (e.code === "KeyF") { e.preventDefault(); isFull() ? exitFull() : enterFull(); }
    if (e.code === "Enter") { e.preventDefault(); { const h = host(); h.chat && h.chat(); } }
    if (e.code === "KeyG") { const h = host(); h.games && h.games(); }
    if (e.code === "Digit1" || e.code === "Digit2" || e.code === "Digit3") { const h = host(); h.emote && h.emote(["wave", "clap", "laugh"][+e.code.slice(5) - 1]); }
  });
  stage.addEventListener("click", grabMouse);
  document.addEventListener("mousemove", (e) => {
    if (document.pointerLockElement !== stage) return;
    if (mode === "tour") setMode("walk");
    if (mode === "bird") return;
    yaw -= e.movementX * 0.0026; pitch = Math.max(-1.35, Math.min(1.35, pitch - e.movementY * 0.0026)); kick();
  });
  document.querySelectorAll(".pad button[data-k]").forEach((b) => {
    const k = b.dataset.k;
    const on = (e) => { e.preventDefault(); keys[k] = 1; b.classList.add("on"); armed = true; if (mode !== "walk") setMode("walk"); };
    const off = () => { keys[k] = 0; b.classList.remove("on"); };
    b.addEventListener("pointerdown", on); b.addEventListener("pointerup", off); b.addEventListener("pointerleave", off); b.addEventListener("pointercancel", off);
  });
  const crouchBtn = document.getElementById("crouch");
  function syncCrouch() { if (crouchBtn) { crouchBtn.setAttribute("aria-pressed", String(crouchHeld)); crouchBtn.classList.toggle("on", crouchHeld); } }
  if (crouchBtn) crouchBtn.addEventListener("click", (e) => { e.preventDefault(); crouchHeld = !crouchHeld; syncCrouch(); });
  // drag to look
  let drag = null;
  stage.addEventListener("pointerdown", (e) => { drag = { x: e.clientX, y: e.clientY }; stage.setPointerCapture(e.pointerId); });
  stage.addEventListener("pointermove", (e) => {
    if (!drag) return;
    const dx = e.clientX - drag.x, dy = e.clientY - drag.y; drag = { x: e.clientX, y: e.clientY };
    if (mode === "bird") { orbit.a -= dx * 0.006; orbit.e = Math.max(0.35, Math.min(1.45, orbit.e + dy * 0.004)); }
    else { if (mode === "tour") setMode("walk"); yaw -= dx * 0.0045; pitch = Math.max(-1.35, Math.min(1.35, pitch - dy * 0.0045)); }
  });
  const endDrag = () => { drag = null; };
  stage.addEventListener("pointerup", endDrag); stage.addEventListener("pointercancel", endDrag);
  stage.addEventListener("wheel", (e) => { if (mode === "bird") { orbit.r = Math.max(35, Math.min(130, orbit.r + e.deltaY * 0.05)); e.preventDefault(); } }, { passive: false });

  const modeBtns = document.querySelectorAll("[data-mode]");
  const capL = document.getElementById("capL");
  function setMode(m) {
    mode = m;
    modeBtns.forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.mode === m)));
    setVisibility();
    if (capL) capL.textContent = m === "bird" ? "Bird's-eye · roof off" : m === "tour" ? "Auto tour" : "Glowfin Cinema";
    if (m === "tour") { // start the tour from the nearest point
      let best = 0, bd = 1e9; for (let d = 0; d < tourLen; d += 1) { const p = tourAt(d), q = Math.hypot(p[0] - P.x, p[1] - P.y); if (q < bd) { bd = q; best = d; } } tourD = best;
    }
    if (m === "walk" && P._fromTour) { P._fromTour = false; }
    kick();
  }
  modeBtns.forEach((b) => b.addEventListener("click", () => setMode(b.dataset.mode)));
  const xray = document.getElementById("xray");
  if (xray) xray.addEventListener("click", () => { const on = xray.getAttribute("aria-pressed") !== "true"; xray.setAttribute("aria-pressed", String(on)); pins.forEach((p) => (p.visible = on)); kick(); });

  function lookAt(tx, ty, tz) {
    const dx = tx - P.x, dy = ty - P.y, dz = tz - (P.f + P.eye - P.f);
    yaw = Math.atan2(-dx, -dy); pitch = Math.atan2(tz - P.eye, Math.hypot(dx, dy));
  }
  // jump to a spot: as the seeker outside, or as the hider inside
  function goSpot(i, asHider) {
    const s = SPOTS[i];
    setMode("walk");
    if (asHider) {
      P.x = s.x; P.y = s.y; P.f = s.f; P.H = 0.9; crouchHeld = true; syncCrouch(); P.eye = s.f + 0.72; P.vy = 0;
      const t = s.look || [s.see[0], s.see[1], (s.see[2] || 0) + 1.2];
      yaw = Math.atan2(-(t[0] - s.x), -(t[1] - s.y)); pitch = Math.atan2(t[2] - P.eye, Math.hypot(t[0] - s.x, t[1] - s.y));
      hiders[i].visible = false;
    } else {
      crouchHeld = false; syncCrouch(); P.H = 1.7;
      P.x = s.see[0]; P.y = s.see[1]; P.f = s.see[2] || 0; P.vy = 0; P.eye = P.f + 1.6;
      hiders.forEach((h) => (h.visible = true));
      yaw = Math.atan2(-(s.x - P.x), -(s.y - P.y)); pitch = Math.atan2(s.f + 0.4 - P.eye, Math.hypot(s.x - P.x, s.y - P.y));
    }
    if (capL) capL.textContent = (asHider ? "Hiding in spot " : "Seeker looking at spot ") + (i + 1);
    armed = true; stage.focus({ preventScroll: true });
    document.querySelector(".stagebox").scrollIntoView({ behavior: "smooth", block: "center" });
    kick();
  }
  function goPlace(x, y, f, yw, label) {
    setMode("walk"); crouchHeld = false; syncCrouch(); P.H = 1.7; P.x = x; P.y = y; P.f = f; P.vy = 0; P.eye = f + 1.6; yaw = yw; pitch = -0.05;
    hiders.forEach((h) => (h.visible = true));
    if (capL) capL.textContent = label;
    armed = true; stage.focus({ preventScroll: true });
    document.querySelector(".stagebox").scrollIntoView({ behavior: "smooth", block: "center" });
    kick();
  }
  document.addEventListener("click", (e) => {
    if (e.target.closest("#goPlay")) return goPlace(57.5, 37.2, 0, 0, "Playground · walk up the yellow ladder");
    if (e.target.closest("#goPlayTop")) return goPlace(57.5, 25.0, 3.0, Math.PI - 0.3, "Playground · top deck");
    const b = e.target.closest("[data-go]"); if (!b) return;
    goSpot(+b.dataset.go - 1, b.dataset.as === "hider");
  });

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
  let last = performance.now(), frames = 0, fpsT = last, running = false, visible = true, itD = 0;
  const itLen = (() => { let l = 0; for (let i = 0; i < itRoute.length; i++) { const a = itRoute[i], b = itRoute[(i + 1) % itRoute.length]; l += Math.hypot(b[0] - a[0], b[1] - a[1]); } return l; })();
  function itAt(d) {
    d = d % itLen; let acc = 0;
    for (let i = 0; i < itRoute.length; i++) { const a = itRoute[i], b = itRoute[(i + 1) % itRoute.length], l = Math.hypot(b[0] - a[0], b[1] - a[1]); if (acc + l >= d) { const u = (d - acc) / l; return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, Math.atan2(-(b[0] - a[0]), -(b[1] - a[1]))]; } acc += l; }
    return [itRoute[0][0], itRoute[0][1], 0];
  }
  const io = new IntersectionObserver((es) => { visible = es[0].isIntersecting; if (visible) kick(); }, { threshold: 0.01 });
  io.observe(stage);
  function kick() { if (!running) { running = true; last = performance.now(); requestAnimationFrame(frame); } }
  // ---------- sound: all made live in the browser, no sound files ----------
  const SND = { on: true, ctx: null, acc: 0, px: null, py: 0, pf: 0, air: false, creakT: 0 };
  function sndStart() {
    if (!SND.on) return;
    if (SND.ctx) { if (SND.ctx.state === "suspended") SND.ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return;
    let a; try { a = SND.ctx = new AC(); } catch (e) { return; }
    const out = a.createGain(); out.gain.value = 0.9; out.connect(a.destination); SND.out = out;
    const mk = (f) => { const b = a.createBuffer(1, a.sampleRate * 2, a.sampleRate), d = b.getChannelData(0); let l = 0; for (let i = 0; i < d.length; i++) { const w = Math.random() * 2 - 1; d[i] = f ? (l = (l + 0.02 * w) / 1.02) * 3.5 : w; } return b; };
    SND.noise = mk(0); const brown = mk(1);
    const hs = a.createBufferSource(); hs.buffer = brown; hs.loop = true; const hl = a.createBiquadFilter(); hl.type = "lowpass"; hl.frequency.value = 200; const hg = a.createGain(); hg.gain.value = 0.06; hs.connect(hl); hl.connect(hg); hg.connect(out); hs.start();
    const o60 = a.createOscillator(); o60.frequency.value = 60; const g60 = a.createGain(); g60.gain.value = 0.01; o60.connect(g60); g60.connect(out); o60.start();
    const fz = a.createOscillator(); fz.type = "sawtooth"; fz.frequency.value = 120; const fl = a.createBiquadFilter(); fl.type = "lowpass"; fl.frequency.value = 320; const fg = a.createGain(); fg.gain.value = 0; fz.connect(fl); fl.connect(fg); fg.connect(out); fz.start(); SND.fridge = fg;
    SND.nextBeep = a.currentTime + 1.5; SND.nextPA = a.currentTime + 20;
  }
  function blip(f, t0, dur, vol, type, f2) { const a = SND.ctx, o = a.createOscillator(), g = a.createGain(); o.type = type || "sine"; o.frequency.setValueAtTime(f, t0); if (f2) o.frequency.exponentialRampToValueAtTime(f2, t0 + dur); g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(vol, t0 + 0.012); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur); o.connect(g); g.connect(SND.out); o.start(t0); o.stop(t0 + dur + 0.05); }
  function hiss(t0, dur, vol, type, freq, q) { const a = SND.ctx, s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain(); s.buffer = SND.noise; f.type = type; f.frequency.value = freq; f.Q.value = q || 1; g.gain.setValueAtTime(vol, t0); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur); s.connect(f); f.connect(g); g.connect(SND.out); s.start(t0, Math.random() * 1.5); s.stop(t0 + dur + 0.05); }
  const surface = () => (P.f < 0.05 ? "carpet" : (P.y < 36 ? "wood" : (P.y > 40 ? "metal" : "carpet")));
  function footstep(kind, hard) {
    const t = SND.ctx.currentTime, k = hard ? 1.5 : (keys.run ? 0.6 : 0.8);
    if (FUN.sticky) { hiss(t, 0.16, 0.22, "bandpass", 500, 2); blip(240, t + 0.05, 0.12, 0.05, "sine", 420); return; }
    if (kind === "metal") { hiss(t, 0.1, 0.3 * k, "bandpass", 2600, 3); blip(560 + Math.random() * 120, t, 0.22, 0.05 * k, "triangle"); }
    else if (kind === "wood") { hiss(t, 0.08, 0.24 * k, "lowpass", 500, 1); blip(110, t, 0.12, 0.09 * k); }
    else hiss(t, 0.07, 0.05 * k, "lowpass", 600, 1);
  }
  function creak() { const t = SND.ctx.currentTime, a = SND.ctx, o = a.createOscillator(), f = a.createBiquadFilter(), g = a.createGain(); o.type = "sawtooth"; o.frequency.setValueAtTime(120, t); o.frequency.linearRampToValueAtTime(240, t + 0.35); o.frequency.linearRampToValueAtTime(160, t + 0.5); f.type = "bandpass"; f.frequency.value = 900; f.Q.value = 6; g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.09, t + 0.05); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.55); o.connect(f); f.connect(g); g.connect(SND.out); o.start(t); o.stop(t + 0.6); }
  function squeak(vol) { const t = SND.ctx.currentTime; blip(850, t, 0.45, 0.035 * vol, "triangle", 1500); blip(1500, t + 0.25, 0.35, 0.025 * vol, "triangle", 1100); }
  function meow() { const t = SND.ctx.currentTime; blip(620, t, 0.5, 0.09, "sawtooth", 380); blip(1240, t, 0.45, 0.03, "triangle", 760); }
  function say(line, rate, pitch) { if (window.speechSynthesis && window.SpeechSynthesisUtterance && SND.on) try { const u = new SpeechSynthesisUtterance(line); u.rate = rate || 0.95; u.pitch = pitch || 0.9; u.volume = 0.7; speechSynthesis.speak(u); } catch (e) {} }
  const PA_LINES = ["Screen 2 is now seating. Screen 2 is also where you are all hiding. We can tell.", "Would the owner of the feet under the party table please pull them in.", "Reminder. The seats do not hide you. We can see your head.", "Mochi the cat is asleep in screen 1, top row. Do not sit on Mochi.", "A large popcorn has been left at the counter. It is now a medium popcorn.", "Seeker, that is the fourth time you have checked the restrooms.", "The floor in screen 2 is sticky. We do not know why. We have stopped asking.", "Please do not press the button in the projection room. You know the one.", "Hiders, the movie is not that funny. Stop laughing.", "Attention. The film in screen 3 has been playing to nobody for two hours. It is still very proud."];
  function pa() { const t = SND.ctx.currentTime; [659, 523, 392].forEach((f, i) => blip(f, t + i * 0.42, 1.1, 0.07, "triangle")); setTimeout(() => say(PA_LINES[Math.floor(Math.random() * PA_LINES.length)]), 1600); }
  let purr = null, rumble = null;
  function sndFrame(dt) {
    const a = SND.ctx; if (!a || !SND.on || a.state !== "running") return;
    const t = a.currentTime, walking = mode === "walk", px = walking ? P.x : 45, py = walking ? P.y : 50;
    // the movie: a low rumble that gets loud inside a screen and is muffled in the lobby
    if (!rumble) { const s = a.createBufferSource(); s.buffer = SND.noise; s.loop = true; const f = a.createBiquadFilter(); f.type = "lowpass"; f.frequency.value = 160; const g = a.createGain(); g.gain.value = 0; s.connect(f); f.connect(g); g.connect(SND.out); s.start(); rumble = { g, f }; SND.nextNote = t + 2; SND.nextPop = t + 1; }
    const inAud = py < 39.8, ds = Math.hypot(((px % 30) + 30) % 30 - 15, py - 3), mv = inAud ? 0.5 / (1 + (ds / 12) ** 2) : 0.03;
    rumble.g.gain.setTargetAtTime(mv, t, 0.3); rumble.f.frequency.setTargetAtTime(inAud ? 260 : 110, t, 0.3);
    if (t > SND.nextNote) { if (inAud) { const base = [98, 110, 131, 147][Math.floor(Math.random() * 4)]; [1, 1.5, 2].forEach((m, i) => blip(base * m, t + i * 0.02, 2.2, 0.05 * mv * 2, "sawtooth", base * m * 0.99)); } SND.nextNote = t + 3 + Math.random() * 4; }
    if (t > SND.nextPop) { const d = Math.hypot(px - 43, py - 42), v = 0.12 / (1 + (d / 5) ** 2); if (v > 0.004 && !inAud) hiss(t, 0.03, v, "bandpass", 2400 + Math.random() * 1500, 2); SND.nextPop = t + 0.08 + Math.random() * 0.5; }
    if (t > SND.nextPA) { pa(); SND.nextPA = t + 70 + Math.random() * 45; }
    if (!walking) { SND.px = null; return; }
    if (SND.px === null) { SND.px = P.x; SND.py = P.y; SND.pf = P.f; SND.air = P.air; }
    const moved = Math.hypot(P.x - SND.px, P.y - SND.py), kind = surface();
    if (!P.air) { SND.acc += moved; if (SND.acc > (keys.run ? 2.0 : 1.7) * (P.H < 1 ? 0.55 : 1)) { SND.acc = 0; if (FUN.sneak > 3 && !FUN.sticky) { SND.pl = !SND.pl; blip(SND.pl ? 392 : 330, t, 0.12, 0.05, "triangle"); } else footstep(kind); } }
    if (SND.air && !P.air) footstep(kind, true);
    if (!P.air && P.f > SND.pf + 0.04 && kind === "metal" && t > SND.creakT) { creak(); SND.creakT = t + 0.9; }
    SND.px = P.x; SND.py = P.y; SND.pf = P.f; SND.air = P.air;
  }
  function setSound(on) {
    SND.on = on; const b = document.getElementById("snd"); if (b) { b.setAttribute("aria-pressed", on ? "true" : "false"); b.textContent = on ? "Sound: On" : "Sound: Off"; }
    if (on) sndStart(); else { if (SND.ctx) SND.ctx.suspend(); try { window.speechSynthesis && speechSynthesis.cancel(); } catch (e) {} }
  }
  document.addEventListener("click", (e) => { if (e.target.closest("#snd")) setSound(!SND.on); });
  ["pointerdown", "keydown"].forEach((ev) => document.addEventListener(ev, (e) => { if (e.target && e.target.closest && e.target.closest("#snd")) return; sndStart(); }, { passive: true }));
  // ---------- things that move, and the jokes ----------
  let scrT = 0;
  function liveFrame(dt, now) {
    const s = now / 1000, walking = mode === "walk", px = P.x, py = P.y, ok = SND.ctx && SND.on && SND.ctx.state === "running", moving = walking && (keys.f || keys.b || keys.l || keys.r);
    scrT -= dt; if (scrT <= 0) { scrT = 0.12; drawScreen(s); scrTex.needsUpdate = true; }
    FUN.bt = (FUN.bt || 0) - dt; if (FUN.bt <= 0) { FUN.bt = 0.18; FUN.bp = ((FUN.bp || 0) + 1) % 3; drawBulbs(FUN.bp); }
    BEAMS.forEach((b, i) => (b.material.opacity = (FUN.trailer > 0 ? 0.09 : 0.045) + Math.sin(s * 7 + i * 2) * 0.008 + Math.sin(s * 23 + i) * 0.004));
    // claw machine: it always drops the cat
    const cw = walking ? Math.hypot(px - 76.3, py - 54.75) : 99; if (cw > 2.5) FUN.clawArmed = true;
    if (FUN.clawArmed && cw < 0.9) { FUN.clawArmed = false; FUN.clawT = 0; if (ok) { blip(900, SND.ctx.currentTime, 0.9, 0.05, "square", 300); blip(392, SND.ctx.currentTime + 1.5, 0.3, 0.07, "triangle", 370); blip(330, SND.ctx.currentTime + 1.85, 0.6, 0.07, "triangle", 262); } }
    if (FUN.clawT !== undefined && FUN.clawT < 3) { FUN.clawT += dt; const u = FUN.clawT; claw.position.y = 1.68 - (u < 1 ? u * 0.45 : u < 1.4 ? 0.45 : Math.max(0, 0.45 - (u - 1.4) * 0.5)); }
    // arcade jingles and the ticket scanner at each screen door
    if (ok) { FUN.arc = (FUN.arc || 0) - dt; const ad = Math.hypot(px - 76.5, py - 52), av = 0.03 / (1 + (ad / 4) ** 2); if (FUN.arc <= 0 && av > 0.003 && walking) { FUN.arc = 0.5 + Math.random() * 1.8; const n = [523, 659, 784, 1047, 880][Math.floor(Math.random() * 5)]; blip(n, SND.ctx.currentTime, 0.12, av, "square"); if (Math.random() < 0.4) blip(n * 1.5, SND.ctx.currentTime + 0.12, 0.12, av, "square"); }
      const side = py < 40 ? 1 : 0; if (FUN.side !== undefined && side !== FUN.side && walking && P.f < 1 && Math.abs(((px % 30) + 30) % 30 - 1.5) < 1) { blip(1320, SND.ctx.currentTime, 0.08, 0.06); blip(1760, SND.ctx.currentTime + 0.1, 0.12, 0.06); FUN.scans = (FUN.scans || 0) + 1; if (FUN.scans % 4 === 2 && side === 1) say("Ticket not found. Just kidding. Enjoy the movie."); } FUN.side = side; }
    const hue = ((s * 14) % 360) / 360;
    SCR_LIGHTS.forEach((l, i) => { l.visible = pretty; if (FUN.trailer > 0) l.color.setHSL((s * 1.3 + i * 0.33) % 1, 1, 0.55); else l.color.setHSL(hue, 0.5, 0.6); l.intensity = 1.5 + Math.sin(s * 3 + i) * 0.25 + (FUN.trailer > 0 ? 1.5 : 0); });
    LOBBY_LIGHTS.forEach((l) => (l.visible = pretty));
    if (FUN.trailer > 0) FUN.trailer -= dt;
    // Mochi
    const c = FUN.cat, cd = walking && Math.abs(P.f - c.f) < 1 ? Math.hypot(px - c.x, py - c.y) : 99; c.g.scale.y = 1 + Math.sin(s * 1.3) * 0.04;
    if (ok) { if (!purr) { const a = SND.ctx, o = a.createOscillator(), lf = a.createOscillator(), lg = a.createGain(), f = a.createBiquadFilter(), g = a.createGain(); o.type = "sawtooth"; o.frequency.value = 26; lf.frequency.value = 1.6; lg.gain.value = 5; lf.connect(lg); lg.connect(o.frequency); f.type = "lowpass"; f.frequency.value = 260; g.gain.value = 0; o.connect(f); f.connect(g); g.connect(SND.out); o.start(); lf.start(); purr = g; }
      purr.gain.setTargetAtTime(cd < 2.6 ? 0.16 * (1 - cd / 2.6) : 0, SND.ctx.currentTime, 0.25); if (cd < 2.4 && P.air && !FUN.meowed) { FUN.meowed = 1; meow(); } if (!P.air) FUN.meowed = 0; }
    // DO NOT PRESS: starts a very serious trailer on every screen
    const bt = FUN.btn, bd = walking && P.f > 4 ? Math.hypot(px - bt.x, py - bt.y) : 99; if (bd > 1.6) bt.armed = true;
    if (bt.armed && bd < 0.75 && FUN.trailer <= 0) { bt.armed = false; FUN.trailer = 12; if (ok) { [98, 98, 131].forEach((f, i) => blip(f, SND.ctx.currentTime + i * 0.5, 1.6, 0.12, "sawtooth", f * 0.97)); setTimeout(() => say("In a world. Where everyone hides. One seeker. Has no idea where anybody is.", 0.7, 0.3), 600); } }
    bt.b.material.color.setRGB(1, FUN.trailer > 0 ? 0.8 : 0.03, 0.03);
    // popcorn burst
    const pp = FUN.pop, pd = walking ? Math.hypot(px - pp.x, py - pp.y) : 99; if (pd > 2.5) pp.armed = true;
    if (pp.armed && pd < 1.2 && pp.t < 0) { pp.armed = false; pp.t = 0; pp.ks.forEach((k) => { k.p = [42.8 + (Math.random() - 0.5) * 0.8, 41.0, 2.1]; k.v = [(Math.random() - 0.5) * 3, 0.5 + Math.random() * 3, 1.5 + Math.random() * 3.5]; k.m.visible = true; }); if (ok) for (let k = 0; k < 16; k++) hiss(SND.ctx.currentTime + Math.random() * 0.8, 0.03, 0.2, "bandpass", 2400 + Math.random() * 1500, 2); }
    if (pp.t >= 0) { pp.t += dt; pp.ks.forEach((k) => { k.v[2] -= 9.8 * dt; k.p[0] += k.v[0] * dt; k.p[1] += k.v[1] * dt; k.p[2] += k.v[2] * dt; const fl = k.p[1] < 41.1 ? 1.04 : (k.p[1] > 44 && k.p[1] < 45 ? 1.14 : 0.035); if (k.p[2] < fl) { k.p[2] = fl; k.v[2] = Math.abs(k.v[2]) * 0.25; k.v[0] *= 0.5; k.v[1] *= 0.5; } k.m.position.set(WX(k.p[0]), k.p[2], WZ(k.p[1])); }); if (pp.t > 20) { pp.t = -1; pp.ks.forEach((k) => (k.m.visible = false)); } }
    // sticky floor and sneaking
    FUN.sticky = walking && P.f < 0.05 && Math.hypot(px - 45, py - 11.2) < 1.2;
    if (walking && P.H < 1 && moving) FUN.sneak = (FUN.sneak || 0) + dt; else FUN.sneak = 0;
    sndFrame(dt);
  }
  // ---------- the other people in this lobby ----------
  const PEOPLE = new Map();
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
    (list || []).forEach((p) => {
      const id = String(p.id); seen.add(id);
      let o = PEOPLE.get(id);
      if (o && (o.color !== p.color || o.name !== p.name)) { G.fx.remove(o.av); o = null; }
      if (!o) {
        const av = new T.Group(), body = friend(/^#[0-9a-f]{6}$/i.test(p.color || "") ? p.color : "#7AA7FF", 1.15);
        av.add(body); const tag = nameTag(String(p.name || "Someone").slice(0, 18), p.color || "#7AA7FF"); tag.position.y = 1.35; av.add(tag);
        o = { av, body, tag, name: p.name, color: p.color, x: p.x, z: p.z, h: +p.h || 0, yaw: +p.yaw || 0 }; G.fx.add(av); PEOPLE.set(id, o);
      }
      o.tx = +p.x || 0; o.tz = +p.z || 0; o.th = +p.h || 0; o.tyaw = +p.yaw || 0; o.cr = !!p.cr; o.act = p.act || "";
    });
    PEOPLE.forEach((o, id) => { if (!seen.has(id)) { G.fx.remove(o.av); PEOPLE.delete(id); } });
    kick();
  }
  function peopleFrame(dt, now) {
    const k = Math.min(1, dt * 10), t = now / 1000;
    PEOPLE.forEach((o) => {
      const moving = Math.hypot(o.tx - o.x, o.tz - o.z) > 0.03;
      o.x += (o.tx - o.x) * k; o.z += (o.tz - o.z) * k; o.h += (o.th - o.h) * k;
      let d = o.tyaw - o.yaw; d = Math.atan2(Math.sin(d), Math.cos(d)); o.yaw += d * k;
      o.av.position.set(o.x, o.h, o.z); o.av.rotation.y = o.yaw;
      const sy = o.cr ? 0.62 : 1; o.body.scale.y += (1.15 * sy - o.body.scale.y) * k; o.tag.position.y = o.cr ? 0.95 : 1.35;
      o.body.position.y = o.act === "clap" ? Math.abs(Math.sin(t * 14)) * 0.06 : moving ? Math.abs(Math.sin(t * 7)) * 0.05 : 0;
      o.body.rotation.z = o.act === "wave" ? Math.sin(t * 9) * 0.18 : o.act === "laugh" ? Math.sin(t * 22) * 0.06 : 0;
    });
    gameFrame(now);
  }
  // ---------- games (Hide and seek, Tag): Tower keeps the round, this page shows who is It and catches people ----------
  let GAME = { mode: "", state: "idle", it: [], found: [], safe: [], iAmIt: false, iAmFound: false, frozen: false }, tagCool = 0, wasFrozen = false;
  const itGeo = new T.ConeGeometry(0.16, 0.3, 4), itMat = new T.MeshBasicMaterial({ color: 0xff5c8a, toneMapped: false });
  function clearLine(ax, ay, az, bx2, by, bz) { for (const o of OCC) if (seg(ax, ay, az, bx2, by, bz, o)) return false; return true; }
  function setGame(g) {
    GAME = Object.assign({ mode: "", state: "idle", it: [], found: [], safe: [], iAmIt: false, iAmFound: false, frozen: false }, g || {});
    if (GAME.frozen && !wasFrozen && GAME.mode === "hide") { P.x = 45; P.y = 57.5; P.f = 0; P.vy = 0; P.air = false; yaw = Math.PI; pitch = 0; SAFE.x = P.x; SAFE.y = P.y; SAFE.f = 0; }
    wasFrozen = GAME.frozen; kick();
  }
  function gameFrame(now) {
    const on = GAME.state === "play" || GAME.state === "count", t = now / 1000;
    PEOPLE.forEach((o, id) => {
      const isIt = on && GAME.it.includes(id), found = GAME.found.includes(id);
      if (isIt && !o.mark) { o.mark = new T.Mesh(itGeo, itMat); o.mark.rotation.x = Math.PI; o.av.add(o.mark); }
      if (o.mark) { o.mark.visible = isIt; o.mark.position.y = (o.cr ? 1.3 : 1.7) + Math.sin(t * 4) * 0.05; o.mark.rotation.y = t * 2; }
      // a seeker never gets a name floating over somebody who is still hidden
      o.tag.visible = !(on && GAME.mode === "hide" && GAME.iAmIt && !isIt && !found);
      if (GAME.state !== "play" || !GAME.iAmIt || isIt || found || now < tagCool || GAME.safe.includes(id)) return;
      const ox = o.x + 45, oy = o.z + 30, d = Math.hypot(ox - P.x, oy - P.y);
      if (d < (GAME.mode === "hide" ? 1.6 : 1.3) && Math.abs(o.h - P.f) < 1.5 && clearLine(P.x, P.y, P.eye, ox, oy, o.h + (o.cr ? 0.3 : 0.5))) {
        tagCool = now + 700; const h = host(); h.tag && h.tag(id);
        if (SND.ctx && SND.on && SND.ctx.state === "running") { blip(660, SND.ctx.currentTime, 0.12, 0.08, "triangle"); blip(990, SND.ctx.currentTime + 0.1, 0.2, 0.08, "triangle"); }
      }
    });
  }
  // the squeak hint in Hide and seek: hiders hear their own, seekers hear the nearest one (quieter the further away)
  function hint() {
    if (!(SND.ctx && SND.on && SND.ctx.state === "running") || GAME.mode !== "hide") return;
    if (!GAME.iAmIt) { if (!GAME.iAmFound) squeak(0.8); return; }
    let best = 1e9; PEOPLE.forEach((o, id) => { if (!GAME.it.includes(id) && !GAME.found.includes(id)) best = Math.min(best, Math.hypot(o.x + 45 - P.x, o.z + 30 - P.y) + Math.abs(o.h - P.f) * 2); });
    if (best < 1e8) squeak(Math.max(0.25, 2.2 / (1 + (best / 7) ** 2)));
  }
  function frame(now) {
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    // move
    if (mode === "walk") {
      const turn = (keys.tl ? 1 : 0) - (keys.tr ? 1 : 0); yaw += turn * dt * 1.9;
      const fwd = (keys.f ? 1 : 0) - (keys.b ? 1 : 0), side = (keys.r ? 1 : 0) - (keys.l ? 1 : 0);
      if ((fwd || side) && !GAME.frozen) {
        const sp = (keys.run ? 7 : 3.6) * (P.H < 1 ? 0.55 : 1) * dt;
        const fx = -Math.sin(yaw), fy = -Math.cos(yaw), rx = Math.cos(yaw), ry = -Math.sin(yaw);
        let mx = (fx * fwd + rx * side), my = (fy * fwd + ry * side); const l = Math.hypot(mx, my); mx = mx / l * sp; my = my / l * sp;
        const n = Math.ceil(sp / 0.12);
        for (let k = 0; k < n; k++) { if (!tryMove(P.x + mx / n, P.y + my / n)) { if (!tryMove(P.x + mx / n, P.y)) tryMove(P.x, P.y + my / n); } }
      }
      if (keys.jump) { if (!GAME.frozen) jump(); keys.jump = 0; }
      physics(dt); keepSafe();
      camera.position.set(WX(P.x), P.eye, WZ(P.y)); camera.rotation.set(pitch, yaw, 0);
    } else if (mode === "tour") {
      tourD += dt * 3.3;
      const p = tourAt(tourD), q = tourAt(tourD + 3.5);
      P.x = p[0]; P.y = p[1]; P.f = p[2]; P.H = 1.7; P.eye = P.f + 1.6;
      const ty = Math.atan2(-(q[0] - p[0]), -(q[1] - p[1]));
      let d = ty - yaw; while (d > Math.PI) d -= Math.PI * 2; while (d < -Math.PI) d += Math.PI * 2; yaw += d * Math.min(1, dt * 2.2);
      pitch += ((q[2] - p[2]) * 0.15 - pitch) * Math.min(1, dt * 2);
      camera.position.set(WX(P.x), P.eye, WZ(P.y)); camera.rotation.set(pitch, yaw, 0);
    } else {
      orbit.a += dt * 0.04;
      const cx = 0, cz = WZ(25);
      camera.position.set(cx + Math.sin(orbit.a) * Math.cos(orbit.e) * orbit.r, Math.sin(orbit.e) * orbit.r, cz + Math.cos(orbit.a) * Math.cos(orbit.e) * orbit.r);
      camera.lookAt(cx, 0, cz);
    }
    // doors open when you walk up to them
    const ex = mode === "bird" ? -999 : P.x, ey = mode === "bird" ? -999 : P.y, ef = P.f;
    DOORS.forEach((d) => {
      const near = Math.hypot(d.cx - ex, d.cy - ey) < (d.kind === "lid" ? 1.5 : 1.9) && Math.abs(d.z - ef) < 2.2;
      const tgt = near ? 1 : 0; if (near && d.o < 0.05 && !d.sq) { d.sq = 1; if (SND.ctx && SND.on && SND.ctx.state === "running") squeak(d.kind === "lid" ? 0.6 : 1); } if (!near && d.o < 0.05) d.sq = 0; d.o += (tgt - d.o) * Math.min(1, dt * 5);
      if (d.kind === "lid") d.pv.rotation.x = -d.o * 1.15; else d.pv.rotation.y = d.rot * d.o * Math.PI * 0.48;
    });
    // the seeker
    itD += dt * 2.4; const ip = itAt(itD); it.position.set(WX(ip[0]), 0, WZ(ip[1])); it.rotation.y = ip[2] + Math.PI; it.position.y = Math.abs(Math.sin(itD * 3)) * 0.06;
    peopleFrame(dt, now);
    liveFrame(dt, now);
    if (shadowDirty) { renderer.shadowMap.needsUpdate = true; shadowDirty = false; }
    if (composer && pretty) composer.render(); else renderer.render(scene, camera);
    autoQuality(dt);
    drawMini();
    frames++; if (now - fpsT > 1000) { if (fpsEl) fpsEl.textContent = Math.round(frames * 1000 / (now - fpsT)); frames = 0; fpsT = now; }
    if (visible && !document.hidden) requestAnimationFrame(frame); else running = false;
  }
  document.addEventListener("visibilitychange", () => { if (!document.hidden) kick(); });

  // =====================================================================
  //  BLUEPRINT (drawn from this same 3D model, straight down)
  // =====================================================================
  const MAP = { x0: -1, x1: 91, y0: -13, y1: 61 };
  const mapC = document.getElementById("map"), mini = document.getElementById("mini");
  let mapImg = null;
  function drawBlueprint() {
    if (!mapC) return;
    const W = mapC.width, H = mapC.height, sx = W / (MAP.x1 - MAP.x0);
    const px = (x) => (x - MAP.x0) * sx, py = (y) => (y - MAP.y0) * sx;
    const save = { ceil: G.ceil.visible, lid: G.lid.visible, upper: G.upper.visible, fx: G.fx.visible, fog: scene.fog };
    G.ceil.visible = false; G.lid.visible = false; G.upper.visible = false; G.fx.visible = false; pins.forEach((p) => (p.oldV = p.visible, p.visible = false)); scene.fog = null;
    const oc = new T.OrthographicCamera(-46, 46, 37, -37, 1, 80);
    oc.up.set(0, 0, -1); oc.position.set(0, 40, WZ(24)); oc.lookAt(0, 0, WZ(24));
    const off = document.createElement("canvas"); off.width = W; off.height = H;
    let r2;
    try { r2 = new T.WebGLRenderer({ canvas: off, antialias: true, preserveDrawingBuffer: true }); } catch (e) { r2 = null; }
    const g = mapC.getContext("2d");
    g.fillStyle = "#12152A"; g.fillRect(0, 0, W, H);
    if (r2) { r2.setPixelRatio(1); r2.setSize(W, H, false); r2.setClearColor(0x2b2e33); r2.render(scene, oc); g.drawImage(off, 0, 0); r2.dispose(); try { r2.forceContextLoss(); } catch (e) { } }
    G.ceil.visible = save.ceil; G.lid.visible = save.lid; G.upper.visible = save.upper; G.fx.visible = save.fx; scene.fog = save.fog; pins.forEach((p) => (p.visible = p.oldV));
    // soften it a little so labels read
    g.fillStyle = "rgba(11,13,26,.18)"; g.fillRect(0, 0, W, H);
    const label = (t, x, y, size, col, align) => {
      g.font = `700 ${size || 15}px ${FONT}`; g.textAlign = align || "left"; g.textBaseline = "middle";
      const w = g.measureText(t).width, X = align === "center" ? px(x) - w / 2 : px(x);
      g.fillStyle = "rgba(11,13,26,.82)"; g.beginPath(); g.roundRect ? g.roundRect(X - 6, py(y) - (size || 15) * 0.75, w + 12, (size || 15) * 1.5, 6) : g.rect(X - 6, py(y) - (size || 15) * 0.75, w + 12, (size || 15) * 1.5); g.fill();
      g.fillStyle = col || "#ECEEFB"; g.fillText(t, X, py(y));
    };
    window.WH_LABELS.forEach((l) => label(l[0], l[1], l[2], l[3], l[4], "center"));
    // spot numbers with a leader line to the exact spot
    SPOTS.forEach((s, i) => {
      const lx = s.lab ? s.lab[0] : s.x, ly = s.lab ? s.lab[1] : s.y;
      g.strokeStyle = "#3FE0E8"; g.lineWidth = 2;
      if (s.lab) { g.beginPath(); g.moveTo(px(lx), py(ly)); g.lineTo(px(s.x), py(s.y)); g.stroke(); }
      g.fillStyle = "#3FE0E8"; g.beginPath(); g.arc(px(s.x), py(s.y), 3.5, 0, Math.PI * 2); g.fill();
      g.beginPath(); g.arc(px(lx), py(ly), 13, 0, Math.PI * 2); g.fillStyle = "#3FE0E8"; g.fill(); g.lineWidth = 2; g.strokeStyle = "#0B0D1A"; g.stroke();
      g.fillStyle = "#0B0D1A"; g.font = `800 14px ${FONT}`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(String(i + 1), px(lx), py(ly) + 1);
    });
    mapImg = mapC;
  }
  function drawMini() {
    if (!mini || !mapImg) return;
    const g = mini.getContext("2d"), W = mini.width, H = mini.height, s = mapImg.width / (MAP.x1 - MAP.x0);
    const span = 34, sw = span * s, sh = sw * H / W;
    let cx = (P.x - MAP.x0) * s, cy = (P.y - MAP.y0) * s;
    const ocx = cx, ocy = cy;
    cx = Math.max(sw / 2, Math.min(mapImg.width - sw / 2, cx)); cy = Math.max(sh / 2, Math.min(mapImg.height - sh / 2, cy));
    if (mode === "bird") { g.drawImage(mapImg, 0, 0, W, H); return; }
    g.fillStyle = "#12152A"; g.fillRect(0, 0, W, H);
    g.drawImage(mapImg, cx - sw / 2, cy - sh / 2, sw, sh, 0, 0, W, H);
    g.save(); g.translate(W / 2 + (ocx - cx) * W / sw, H / 2 + (ocy - cy) * W / sw); g.rotate(-yaw);
    g.fillStyle = "rgba(63,224,232,.25)"; g.beginPath(); g.moveTo(0, 0); g.arc(0, 0, 26, -Math.PI / 2 - 0.6, -Math.PI / 2 + 0.6); g.closePath(); g.fill();
    g.fillStyle = "#3FE0E8"; g.beginPath(); g.moveTo(0, -8); g.lineTo(6, 6); g.lineTo(-6, 6); g.closePath(); g.fill();
    g.restore();
    if (false) { const ip = itAt(itD), dx = ((ip[0] - MAP.x0) * s - cx) * W / sw, dy = ((ip[1] - MAP.y0) * s - cy) * W / sw; if (Math.abs(dx) < W / 2 && Math.abs(dy) < H / 2) { g.fillStyle = "#FF5C8A"; g.beginPath(); g.arc(W / 2 + dx, H / 2 + dy, 4, 0, Math.PI * 2); g.fill(); } }
  }

  // =====================================================================
  //  LINE-OF-SIGHT CHECK: can a seeker anywhere see the hider?
  // =====================================================================
  function seg(ax, ay, az, bx2, by, bz, o) {
    let t0 = 0, t1 = 1;
    const d = [bx2 - ax, by - ay, bz - az], a = [ax, ay, az], mn = [o.x1, o.y1, o.b], mx = [o.x2, o.y2, o.t];
    for (let k = 0; k < 3; k++) {
      if (Math.abs(d[k]) < 1e-9) { if (a[k] <= mn[k] || a[k] >= mx[k]) return false; }
      else { let ta = (mn[k] - a[k]) / d[k], tb = (mx[k] - a[k]) / d[k]; if (ta > tb) { const q = ta; ta = tb; tb = q; } if (ta > t0) t0 = ta; if (tb < t1) t1 = tb; if (t0 >= t1) return false; }
    }
    return t1 > 0.001 && t0 < 0.999;
  }
  // every place a player can reach (walking, climbing, crouching, jumping), found by flooding out from the entrance
  let REACHED = null;
  function reach(stepSz) {
    const S = stepSz || 0.25, JH = JUMP_V * JUMP_V / (2 * GRAV), seen = new Map(), q = [];
    const fitH = (x, y, f) => [1.7, 0.9, 0.6].find((H) => !hitAt(x, y, f, H, null, f + STEP)) || 0;
    const add = (x, y, f, from) => { const k = Math.round(x / S) + "," + Math.round(y / S) + "," + Math.round(f * 10); if (seen.has(k)) return; const h = fitH(x, y, f); if (!h) return; seen.set(k, [x, y, f, h, from]); q.push(k); };
    add(45, 57.5, 0);
    for (let qi = 0; qi < q.length; qi++) {
      const [x, y, f] = seen.get(q[qi]);
      for (const [dx, dy] of [[S, 0], [-S, 0], [0, S], [0, -S]]) {
        const nx = +(x + dx).toFixed(3), ny = +(y + dy).toFixed(3);
        if (!inBounds(nx, ny)) continue;
        for (const [up, jmp] of [[f + STEP, 0], [Math.min(f + STEP + JH, Math.max(f + STEP, 1.25)), 1]]) {
          const s2 = supportAt(nx, ny, f, up), f2 = Math.max(f, s2);
          if (hitAt(nx, ny, f2, 0.6, null, jmp ? up : f2 + STEP)) continue;
          add(nx, ny, s2, q[qi]);
        }
      }
    }
    return seen;
  }
  function viewers() {
    const out = [];
    for (let x = 0.6; x < 90; x += 1.2) for (let y = -12.2; y < 60; y += 1.2) {
      if (y < 0 && !(x > 12.8 && x < 15.2)) continue;
      if (hitAt(x, y, 0, 1.7)) continue;
      if (supportAt(x, y, 0) > 0.05) continue;
      out.push([x, y, 1.6]);
    }
    // every raised place a seeker can get to, standing or crouched
    if (!REACHED) REACHED = reach(0.25);
    const used = new Set();
    REACHED.forEach(([x, y, f, h]) => { if (f < 0.05) return; const k = Math.round(x / 0.8) + "," + Math.round(y / 0.8) + "," + Math.round(f * 4); if (used.has(k)) return; used.add(k); out.push([x, y, f + (h > 1 ? 1.6 : h > 0.7 ? 0.72 : 0.45)]); });
    return out;
  }
  function losCheck(i, openDoors) {
    const s = SPOTS[i], e = s.enc, V = viewers();
    const occ = OCC.filter((o) => !(openDoors && o.door === s.room && o.door))
      .map((o) => ({ o, d: Math.hypot((o.x1 + o.x2) / 2 - s.x, (o.y1 + o.y2) / 2 - s.y) })).sort((a, b) => a.d - b.d).map((q) => q.o);
    const sz = (s.s || 1), top = s.f + 0.78 * sz, mid = s.f + 0.45 * sz;
    const tg = [[s.x, s.y, top], [s.x, s.y, mid], [s.x + 0.2 * sz, s.y, mid], [s.x - 0.2 * sz, s.y, mid], [s.x, s.y + 0.2 * sz, mid], [s.x, s.y - 0.2 * sz, mid]];
    let n = 0, seen = 0; const where = [];
    for (const v of V) {
      if (v[0] > e[0] && v[0] < e[2] && v[1] > e[1] && v[1] < e[3] && v[2] > e[4] && v[2] < e[5]) continue;
      n++;
      let vis = false;
      for (const t of tg) { let blocked = false; for (const o of occ) { if (seg(v[0], v[1], v[2], t[0], t[1], t[2], o)) { blocked = true; break; } } if (!blocked) { vis = true; break; } }
      if (vis) { seen++; if (where.length < 6) where.push(v.map((q) => +q.toFixed(1))); }
    }
    return { n, seen, where };
  }
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
  window.WH = { reach, viewers, renderer, sun, scene, BX, sim, losCheck, goSpot, setMode, P, SPOTS, OCC, SOL, supportAt, hitAt, tryMove, step: (k, ms) => { keys[k] = 1; setTimeout(() => (keys[k] = 0), ms); }, get yaw() { return yaw; }, set yaw(v) { yaw = v; }, set pitch(v) { pitch = v; }, cam: camera, render: () => renderer.render(scene, camera) };

  // run the check quietly after load and fill in each card
  function runChecks() {
    const out = document.querySelectorAll("[data-los]");
    let i = 0;
    const next = () => {
      if (i >= SPOTS.length) { const t = document.getElementById("losAll"); if (t) t.hidden = false; return; }
      const r = losCheck(i, false), el = document.querySelector(`[data-los="${i + 1}"]`);
      if (el) { el.textContent = r.seen === 0 ? `Checked from ${r.n.toLocaleString()} places a seeker could stand: seen from none.` : `Checked from ${r.n.toLocaleString()} places: seen from ${r.seen}.`; el.classList.toggle("bad", r.seen > 0); }
      i++; setTimeout(next, 30);
    };
    if (out.length) setTimeout(next, 600);
  }

  // what the Lobby page (the page that holds this one) can ask for
  window.GF_CINEMA = {
    me: () => ({ x: +WX(P.x).toFixed(3), z: +WZ(P.y).toFixed(3), yaw: +yaw.toFixed(3), h: +P.f.toFixed(2), cr: P.H < 1 ? 1 : 0 }),
    setPeople, onFull, setGame, hint,
    begin() { armed = true; try { window.focus(); } catch (e) {} stage.focus({ preventScroll: true }); sndStart(); grabMouse(); kick(); },
    letGo() { armed = false; Object.keys(keys).forEach((k) => (keys[k] = 0)); ctrlHeld = false; if (document.pointerLockElement === stage) document.exitPointerLock(); },
    setFast(fast) { autoDropped = true; setPretty(!fast); },
    quiet() { setSound(false); },
  };
  const start = () => { drawBlueprint(); setMode("walk"); yaw = 0; kick(); { const h = host(); h.ready && h.ready(); } };
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(start, start); else start();
})();
