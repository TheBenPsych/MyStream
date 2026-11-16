/* Benflix Christmas: the Who's watching room (approved 2026-11-16, thread "Design a Christmas theme for Benflix").
 *
 * A dim, cosy room. A decorated tree twinkles in the bottom-left corner, and a toy train runs round it
 * in 3D. A snowy window on the right shows a parade passing: a snowman, a red-nosed reindeer, Santa,
 * two little elves and one very tall elf. It shows from 1 to 30 December (New Year's has the 31st and the 1st).
 *   - Presents pile up under the tree through December.
 *   - On Christmas Eve and Christmas Day, Santa's sleigh flies past the window.
 *   - From Christmas Day the presents are opened.
 *
 * The same drawing is used three ways:
 *   1. live on the web Who's watching page;
 *   2. by tools/record-xmas.js, which records it as looping videos for the TV and phone app;
 *   3. by the design sheet.
 * Everything is a function of the time t (seconds), and repeats every LOOP seconds, so a video of one
 * loop joins up with no jump.
 */
(function (G) {
  "use strict";
  const LOOP = 30;
  const TAU = Math.PI * 2;
  const f1 = (v) => (+v).toFixed(1);
  const NS = "http://www.w3.org/2000/svg";
  let OID = 0;

  // ---------- dates ----------
  /** The last day of the Christmas look (the app's ChristmasSeason.LAST_DAY too), so it never overlaps New Year's */
  const LAST_DAY = 30;
  /** 1 December to LAST_DAY */
  const season = (d) => d.getMonth() === 11 && d.getDate() <= LAST_DAY;
  /** Sleeps until Christmas (1 Dec to 23 Dec), 0 on Christmas Eve, else null */
  const sleeps = (d) => (d.getMonth() === 11 && d.getDate() <= 24 ? 25 - d.getDate() - 1 : null);
  /** Which version of the room today is. L1 to L3: presents pile up. EVE: plus the sleigh. DAY: opened, plus the sleigh. AFTER: opened */
  const variant = (d) => {
    if (d.getMonth() === 0) return "AFTER";
    const day = d.getDate();
    if (day <= 8) return "L1";
    if (day <= 16) return "L2";
    if (day <= 23) return "L3";
    if (day === 24) return "EVE";
    if (day === 25) return "DAY";
    return "AFTER";
  };
  /** The countdown line under the greeting, or "" */
  const countdown = (d) => {
    const s = sleeps(d);
    if (s == null) return "";
    if (s === 0) return "Santa comes tonight!";
    return s === 1 ? "1 sleep to Christmas" : `${s} sleeps to Christmas`;
  };

  // ---------- small helpers ----------
  function rnd(seed) { let a = seed >>> 0; return () => { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
  function mixC(a, b, t) { const pa = [1, 3, 5].map((i) => parseInt(a.substr(i, 2), 16)), pb = [1, 3, 5].map((i) => parseInt(b.substr(i, 2), 16)); return "#" + pa.map((v, i) => Math.round(v + (pb[i] - v) * t).toString(16).padStart(2, "0")).join(""); }
  const sm = (t) => t * t * (3 - 2 * t);
  const LCOL = [["#FF3B4E", "#FF8A95"], ["#FFC93B", "#FFE7A0"], ["#3BA8FF", "#A6DBFF"], ["#4BE07A", "#B5FFC8"], ["#FF6BD0", "#FFC2EE"], ["#FF9A3B", "#FFD1A0"]];

  function globalDefs(id) {
    const rg = (n, stops) => `<radialGradient id="${id}${n}" cx=".35" cy=".3" r=".8">${stops.map(([o, c]) => `<stop offset="${o}" stop-color="${c}"/>`).join("")}</radialGradient>`;
    return rg("red", [[0, "#FFB3BF"], [0.25, "#E23A55"], [0.7, "#9C1029"], [1, "#4A0812"]])
      + rg("goldb", [[0, "#FFF4C9"], [0.3, "#E8BC55"], [0.75, "#8A6118"], [1, "#4A320A"]])
      + rg("sil", [[0, "#fff"], [0.35, "#DDE5EF"], [0.8, "#8C9AAD"], [1, "#4C5868"]])
      + rg("green", [[0, "#B9F2CF"], [0.3, "#2E9E5E"], [0.8, "#11502C"], [1, "#07301A"]])
      + `<linearGradient id="${id}gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFF0B0"/><stop offset=".5" stop-color="#E9C877"/><stop offset="1" stop-color="#9C7420"/></linearGradient>`;
  }
  const ball = (id, x, y, rad, grad) => `<circle cx="${f1(x + rad * 0.25)}" cy="${f1(y + rad * 0.35)}" r="${f1(rad)}" fill="#000" opacity=".3"/><circle cx="${f1(x)}" cy="${f1(y)}" r="${f1(rad)}" fill="url(#${id}${grad})"/><ellipse cx="${f1(x - rad * 0.38)}" cy="${f1(y - rad * 0.4)}" rx="${f1(rad * 0.32)}" ry="${f1(rad * 0.18)}" transform="rotate(-40 ${f1(x - rad * 0.38)} ${f1(y - rad * 0.4)})" fill="#fff" opacity=".75"/><rect x="${f1(x - rad * 0.25)}" y="${f1(y - rad - rad * 0.28)}" width="${f1(rad * 0.5)}" height="${f1(rad * 0.32)}" rx="${f1(rad * 0.08)}" fill="url(#${id}gold)"/>`;

  /* one bough: points along +x from 0 to 1, needle teeth on both edges */
  function boughShape(r) {
    const n = 7; const top = [], bot = [];
    for (let i = 0; i <= n; i++) {
      const s = i / n, half = 0.2 * Math.sin(Math.PI * Math.min(1, s * 0.95 + 0.06)) * (1 - 0.35 * s) + 0.02;
      top.push([s, -half - (i % 2 ? 0 : 0.03 * r())]); top.push([s + 0.5 / n, -half * 0.45]);
      bot.push([s, half * 0.85 + (i % 2 ? 0.03 * r() : 0)]); bot.push([s + 0.5 / n, half * 0.4]);
    }
    const pts = [[0, 0], ...top.filter((p) => p[0] < 1), [1.04, 0], ...bot.filter((p) => p[0] < 1).reverse()];
    return "M" + pts.map((p) => p[0].toFixed(3) + " " + p[1].toFixed(3)).join(" L") + "Z";
  }

  /* a wrapped present, in 3D: front, top and side, ribbon both ways and a bow */
  function box3(x, y, w, h, d, col, rib) {
    const top = mixC(col, "#ffffff", 0.18), side = mixC(col, "#000000", 0.35), rb2 = mixC(rib, "#000000", 0.25); const k = 0.5;
    let s = `<ellipse cx="${f1(x + w / 2 + d / 2)}" cy="${f1(y + 2)}" rx="${f1(w * 0.65)}" ry="${f1(d * 0.35 + 3)}" fill="#000" opacity=".35"/>`;
    s += `<rect x="${f1(x)}" y="${f1(y - h)}" width="${f1(w)}" height="${f1(h)}" fill="${col}"/>`;
    s += `<path d="M${f1(x)} ${f1(y - h)} L${f1(x + d)} ${f1(y - h - d * k)} L${f1(x + w + d)} ${f1(y - h - d * k)} L${f1(x + w)} ${f1(y - h)}Z" fill="${top}"/>`;
    s += `<path d="M${f1(x + w)} ${f1(y - h)} L${f1(x + w + d)} ${f1(y - h - d * k)} L${f1(x + w + d)} ${f1(y - d * k)} L${f1(x + w)} ${f1(y)}Z" fill="${side}"/>`;
    const rw = Math.max(3, w * 0.13);
    s += `<rect x="${f1(x + w / 2 - rw / 2)}" y="${f1(y - h)}" width="${f1(rw)}" height="${f1(h)}" fill="${rib}"/>`;
    s += `<path d="M${f1(x + w / 2 - rw / 2)} ${f1(y - h)} L${f1(x + w / 2 - rw / 2 + d)} ${f1(y - h - d * k)} L${f1(x + w / 2 + rw / 2 + d)} ${f1(y - h - d * k)} L${f1(x + w / 2 + rw / 2)} ${f1(y - h)}Z" fill="${rib}"/>`;
    s += `<path d="M${f1(x + w + d * 0.5 - rw * 0.3)} ${f1(y - h - d * k * 0.5)} L${f1(x + w + d * 0.5 + rw * 0.3)} ${f1(y - h - d * k * 0.5)} L${f1(x + w + d * 0.5 + rw * 0.3)} ${f1(y - d * k * 0.5)} L${f1(x + w + d * 0.5 - rw * 0.3)} ${f1(y - d * k * 0.5)}Z" fill="${rb2}"/>`;
    const bx = x + w / 2 + d / 2, by = y - h - d * k / 2, bs = Math.max(6, w * 0.2);
    s += `<path d="M${f1(bx)} ${f1(by)} C${f1(bx - bs * 1.3)} ${f1(by - bs * 1.1)} ${f1(bx - bs * 1.4)} ${f1(by + bs * 0.2)} ${f1(bx)} ${f1(by)}Z M${f1(bx)} ${f1(by)} C${f1(bx + bs * 1.3)} ${f1(by - bs * 1.1)} ${f1(bx + bs * 1.4)} ${f1(by + bs * 0.2)} ${f1(bx)} ${f1(by)}Z" fill="${rib}" stroke="${rb2}" stroke-width="1"/><circle cx="${f1(bx)}" cy="${f1(by)}" r="${f1(bs * 0.28)}" fill="${rb2}"/>`;
    s += `<rect x="${f1(x)}" y="${f1(y - h)}" width="${f1(w * 0.18)}" height="${f1(h)}" fill="#fff" opacity=".08"/>`;
    return s;
  }
  /* an opened present: the box with its lid off leaning against it, tissue paper showing */
  function boxOpen(x, y, w, h, d, col, rib) {
    const side = mixC(col, "#000000", 0.35), inside = mixC(col, "#000000", 0.6), top = mixC(col, "#ffffff", 0.18); const k = 0.5;
    let s = `<ellipse cx="${f1(x + w / 2 + d / 2)}" cy="${f1(y + 2)}" rx="${f1(w * 0.7)}" ry="${f1(d * 0.35 + 3)}" fill="#000" opacity=".35"/>`;
    // inside of the box (back wall), tissue paper, then the front and side
    s += `<path d="M${f1(x)} ${f1(y - h)} L${f1(x + d)} ${f1(y - h - d * k)} L${f1(x + w + d)} ${f1(y - h - d * k)} L${f1(x + w)} ${f1(y - h)}Z" fill="${inside}"/>`;
    s += `<path d="M${f1(x + w * 0.1)} ${f1(y - h)} l${f1(w * 0.12)} ${f1(-h * 0.35)} l${f1(w * 0.12)} ${f1(h * 0.25)} l${f1(w * 0.14)} ${f1(-h * 0.3)} l${f1(w * 0.14)} ${f1(h * 0.28)} l${f1(w * 0.14)} ${f1(-h * 0.33)} l${f1(w * 0.12)} ${f1(h * 0.33)}Z" fill="#F4F1EC" opacity=".92"/>`;
    s += `<rect x="${f1(x)}" y="${f1(y - h)}" width="${f1(w)}" height="${f1(h)}" fill="${col}"/>`;
    s += `<path d="M${f1(x + w)} ${f1(y - h)} L${f1(x + w + d)} ${f1(y - h - d * k)} L${f1(x + w + d)} ${f1(y - d * k)} L${f1(x + w)} ${f1(y)}Z" fill="${side}"/>`;
    const rw = Math.max(3, w * 0.13);
    s += `<rect x="${f1(x + w / 2 - rw / 2)}" y="${f1(y - h)}" width="${f1(rw)}" height="${f1(h)}" fill="${rib}"/>`;
    // the lid on the floor, tilted against the box
    const lw = w * 1.04, lh = h * 0.28, lx = x + w + d * 0.4, ly = y + 2;
    s += `<g transform="rotate(-24 ${f1(lx)} ${f1(ly)})"><rect x="${f1(lx)}" y="${f1(ly - lh)}" width="${f1(lw)}" height="${f1(lh)}" rx="2" fill="${top}"/><rect x="${f1(lx + lw / 2 - rw / 2)}" y="${f1(ly - lh)}" width="${f1(rw)}" height="${f1(lh)}" fill="${rib}"/></g>`;
    return s;
  }
  /* a curl of loose ribbon on the floor */
  const curl = (x, y, s, col) => `<path d="M${f1(x)} ${f1(y)} q${f1(s * 0.5)} ${f1(-s * 0.6)} ${f1(s)} 0 t${f1(s)} 0 t${f1(s)} 0" stroke="${col}" stroke-width="${f1(Math.max(2, s * 0.18))}" fill="none" stroke-linecap="round"/>`;
  /* a little teddy bear sitting up, a present someone opened */
  const teddy = (x, y, s) => `<g transform="translate(${f1(x)} ${f1(y)}) scale(${(s / 40).toFixed(3)})"><ellipse cx="0" cy="2" rx="20" ry="4" fill="#000" opacity=".3"/><ellipse cx="-10" cy="-4" rx="8" ry="6" fill="#8A5A34"/><ellipse cx="10" cy="-4" rx="8" ry="6" fill="#8A5A34"/><ellipse cx="0" cy="-16" rx="14" ry="15" fill="#A46A3C"/><ellipse cx="0" cy="-12" rx="8" ry="9" fill="#D9B48A"/><circle cx="0" cy="-38" r="12" fill="#A46A3C"/><circle cx="-10" cy="-48" r="5" fill="#A46A3C"/><circle cx="10" cy="-48" r="5" fill="#A46A3C"/><circle cx="-10" cy="-48" r="2.5" fill="#D9B48A"/><circle cx="10" cy="-48" r="2.5" fill="#D9B48A"/><ellipse cx="0" cy="-34" rx="5" ry="4" fill="#D9B48A"/><circle cx="0" cy="-35" r="1.8" fill="#222"/><circle cx="-4.5" cy="-41" r="1.6" fill="#222"/><circle cx="4.5" cy="-41" r="1.6" fill="#222"/><path d="M-8 -27 Q0 -22 8 -27 L6 -23 Q0 -20 -6 -23Z" fill="#C42540"/></g>`;

  /* train pieces, drawn facing right, wheels on y=0 */
  const WHL = (x, y, r) => `<circle cx="${x}" cy="${y}" r="${r}" fill="#141414" stroke="#E9C877" stroke-width="1.6"/><g class="spk" data-x="${x}" data-y="${y}" data-r="${r}"><path d="M${x - r * 0.8} ${y} H${x + r * 0.8} M${x} ${y - r * 0.8} V${y + r * 0.8} M${x - r * 0.56} ${y - r * 0.56} L${x + r * 0.56} ${y + r * 0.56} M${x - r * 0.56} ${y + r * 0.56} L${x + r * 0.56} ${y - r * 0.56}" stroke="#C9A24F" stroke-width="${Math.max(1, r * 0.18)}"/></g><circle cx="${x}" cy="${y}" r="${r * 0.32}" fill="#C42540"/>`;
  /* each car is made of solid boxes: x0..x1 along the car, bot..top in height */
  const BOX = [
    [{ x0: -36, x1: 40, bot: 5, top: 13, roof: "#1b1b1b", end: "#1b1b1b" }, { x0: -6, x1: 36, bot: 13, top: 36, roof: "#8E1529", end: "#202020" }, { x0: -36, x1: -6, bot: 5, top: 55, roof: "#1b1b1b", end: "#7E1022" }],
    [{ x0: -26, x1: 26, bot: 5, top: 28, roof: "#262626", end: "#111" }],
    [{ x0: -32, x1: 32, bot: 5, top: 43, roof: "#0F241A", end: "#145234" }],
    [{ x0: -29, x1: 29, bot: 5, top: 26, roof: "#5A0D19", end: "#8E1529" }],
    [{ x0: -28, x1: 28, bot: 5, top: 40, roof: "#1b1b1b", end: "#8E1529" }, { x0: -12, x1: 12, bot: 40, top: 51, roof: "#1b1b1b", end: "#A81C34" }],
  ];
  const CW = 24;
  const TRAIN = [
    { len: 76, g: `<rect x="-36" y="-13" width="72" height="8" rx="2" fill="#1b1b1b"/><rect x="-6" y="-36" width="40" height="23" rx="11" fill="#C42540"/><rect x="-6" y="-36" width="40" height="7" rx="3.5" fill="#E04A60" opacity=".7"/><rect x="2" y="-36" width="3" height="23" fill="#E9C877"/><rect x="18" y="-36" width="3" height="23" fill="#E9C877"/><rect x="30" y="-35" width="8" height="21" rx="3" fill="#202020"/><path d="M12 -36 L12 -50 L9 -55 L23 -55 L20 -50 L20 -36Z" fill="#202020"/><rect x="8" y="-57" width="16" height="4" rx="1.5" fill="#E9C877"/><path d="M-2 -36 a6 6 0 0 1 12 0Z" fill="#E9C877"/><rect x="-36" y="-50" width="30" height="38" rx="2" fill="#A81C34"/><rect x="-39" y="-55" width="36" height="6" rx="2" fill="#1b1b1b"/><rect x="-31" y="-46" width="16" height="13" rx="2" fill="#FFD27A"/><path d="M36 -13 L46 -2 L36 -2Z" fill="#E9C877"/><circle cx="39" cy="-40" r="4" fill="#FFF2C2"/><circle cx="39" cy="-40" r="11" fill="#FFE7A3" opacity=".3"/>${WHL(-24, -8, 8)}${WHL(-5, -8, 8)}${WHL(14, -5, 5)}${WHL(28, -5, 5)}<rect x="-26" y="-9.5" width="34" height="3" rx="1.5" fill="#E9C877"/>` },
    { len: 54, g: `<rect x="-26" y="-28" width="52" height="20" rx="3" fill="#1b1b1b"/><path d="M-22 -28 q6 -9 12 -2 q6 -9 12 -1 q6 -9 12 -1 q5 -7 8 3Z" fill="#2a2a2a"/><rect x="-26" y="-28" width="52" height="4" fill="#E9C877"/>${WHL(-14, -5, 5.5)}${WHL(14, -5, 5.5)}` },
    { len: 66, g: `<rect x="-32" y="-38" width="64" height="30" rx="4" fill="#1E6B45"/><rect x="-35" y="-43" width="70" height="7" rx="3" fill="#0F241A"/>${[-24, -10, 4, 18].map((x) => `<rect x="${x}" y="-33" width="9" height="11" rx="1.5" fill="#FFD27A"/>`).join("")}<rect x="-32" y="-17" width="64" height="3" fill="#E9C877"/>${WHL(-20, -5, 5.5)}${WHL(20, -5, 5.5)}` },
    { len: 60, g: `<rect x="-29" y="-26" width="58" height="18" rx="3" fill="#B3192F"/><rect x="-29" y="-26" width="58" height="4" fill="#E9C877"/><rect x="-22" y="-38" width="14" height="12" fill="#E9EDF3"/><rect x="-16" y="-38" width="3" height="12" fill="#C42540"/><rect x="-5" y="-42" width="12" height="16" fill="#1E6B45"/><rect x="-0.5" y="-42" width="3" height="16" fill="#E9C877"/><rect x="10" y="-36" width="13" height="10" fill="#D4A63C"/><rect x="15" y="-36" width="3" height="10" fill="#1E6B45"/>${WHL(-18, -5, 5.5)}${WHL(18, -5, 5.5)}` },
    { len: 58, g: `<rect x="-28" y="-36" width="56" height="28" rx="4" fill="#C42540"/><rect x="-12" y="-48" width="24" height="12" rx="2" fill="#A81C34"/><rect x="-15" y="-51" width="30" height="4" rx="2" fill="#1b1b1b"/><rect x="-31" y="-40" width="62" height="5" rx="2" fill="#1b1b1b"/><rect x="-8" y="-46" width="16" height="7" rx="1" fill="#FFD27A"/><rect x="-20" y="-31" width="10" height="10" rx="1.5" fill="#FFD27A"/><rect x="10" y="-31" width="10" height="10" rx="1.5" fill="#FFD27A"/><circle cx="-30" cy="-20" r="2.5" fill="#FF3B4E"/>${WHL(-16, -5, 5.5)}${WHL(16, -5, 5.5)}` },
  ];

  /* the presents under the tree for today's version of the room */
  function presents(W, v) {
    const u = W / 230;
    const all = [
      [-W * 0.78, W * 0.11, 52 * u, 44 * u, 22 * u, "#B3192F", "#E9C877"],
      [W * 0.2, W * 0.15, 58 * u, 38 * u, 22 * u, "#E9EDF3", "#C42540"],
      [-W * 0.5, W * 0.16, 40 * u, 30 * u, 18 * u, "#1E6B45", "#E8485E"],
      [W * 0.58, W * 0.1, 40 * u, 34 * u, 18 * u, "#5A3FA0", "#E9C877"],
      [-W * 0.22, W * 0.19, 30 * u, 24 * u, 14 * u, "#D4A63C", "#B3192F"],
      [W * 0.26, W * 0.15 - 44 * u, 34 * u, 26 * u, 14 * u, "#1E6B45", "#E9C877"],
    ];
    if (v === "DAY" || v === "AFTER") {
      return boxOpen(...all[0]) + boxOpen(...all[1]) + boxOpen(...all[3])
        + curl(-W * 0.4, W * 0.2, 14 * u, "#E8485E") + curl(W * 0.02, W * 0.22, 12 * u, "#E9C877") + curl(W * 0.42, W * 0.2, 13 * u, "#4BA06E")
        + teddy(-W * 0.14, W * 0.25, 52 * u) + box3(...all[4]);
    }
    const n = v === "L1" ? 2 : v === "L2" ? 4 : 6;
    // draw back-to-front: the ones further back (smaller y) first
    return all.slice(0, n).sort((a, b) => a[1] - b[1]).map((p) => box3(...p)).join("");
  }

  /* the whole tree in local units: base at y=0, top at -H. Returns svg text; parts that move have classes */
  function treeSvg(H, seed, v) {
    const r = rnd(seed), id = "x" + OID++, W = H * 0.385; let s = "";
    s += `<defs>${globalDefs(id)}${LCOL.map(([c], k) => `<radialGradient id="${id}l${k}"><stop offset="0" stop-color="${c}" stop-opacity=".95"/><stop offset=".35" stop-color="${c}" stop-opacity=".4"/><stop offset="1" stop-color="${c}" stop-opacity="0"/></radialGradient>`).join("")}
 <radialGradient id="${id}warm" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFB46B" stop-opacity=".34"/><stop offset=".5" stop-color="#FF8A3D" stop-opacity=".1"/><stop offset="1" stop-color="#FF8A3D" stop-opacity="0"/></radialGradient>
 <radialGradient id="${id}halo"><stop offset="0" stop-color="#FFF1B8" stop-opacity=".75"/><stop offset=".3" stop-color="#FFD45E" stop-opacity=".25"/><stop offset="1" stop-color="#FFD45E" stop-opacity="0"/></radialGradient>
 <linearGradient id="${id}sk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7E1022"/><stop offset="1" stop-color="#3E0610"/></linearGradient>
 <linearGradient id="${id}rib" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E8485E"/><stop offset=".5" stop-color="#B3192F"/><stop offset="1" stop-color="#6E0C1B"/></linearGradient>
 ${[0, 1, 2, 3, 4, 5].map((k) => `<path id="${id}b${k}" d="${boughShape(rnd(seed * 31 + k))}"/>`).join("")}</defs>`;
    // warm light on the wall and floor
    s += `<ellipse cx="0" cy="${f1(-H * 0.45)}" rx="${f1(W * 2.6)}" ry="${f1(H * 0.85)}" fill="url(#${id}warm)" class="wglow"/><ellipse cx="0" cy="${f1(H * 0.06)}" rx="${f1(W * 2)}" ry="${f1(W * 0.42)}" fill="url(#${id}warm)" class="wglow"/>`;
    // track: rails and ties on the floor
    const rx = W * 1.22, ry = W * 0.27, cy = H * 0.05; let ties = "";
    for (let k = 0; k < 64; k++) { const a = k / 64 * TAU, c = Math.cos(a), sn = Math.sin(a); ties += `M${f1(c * (rx - 9))} ${f1(cy + sn * (ry - 4))} L${f1(c * (rx + 9))} ${f1(cy + sn * (ry + 4))} `; }
    s += `<ellipse cx="0" cy="${f1(cy + 3)}" rx="${f1(rx + 6)}" ry="${f1(ry + 4)}" fill="none" stroke="#000" stroke-opacity=".35" stroke-width="10"/><path d="${ties}" stroke="#4A2E1A" stroke-width="5" stroke-linecap="round"/><ellipse cx="0" cy="${f1(cy)}" rx="${f1(rx - 5)}" ry="${f1(ry - 2.5)}" fill="none" stroke="#9AA3AE" stroke-width="2.2"/><ellipse cx="0" cy="${f1(cy)}" rx="${f1(rx + 5)}" ry="${f1(ry + 2.5)}" fill="none" stroke="#9AA3AE" stroke-width="2.2"/>`;
    s += `<g class="tback"></g><g class="smokeb"></g>`;
    // tree skirt with fluffy trim
    const skx = W * 0.72, sky = W * 0.15; let fluff = "";
    for (let k = 0; k < 54; k++) { const a = k / 54 * TAU; fluff += `<circle cx="${f1(Math.cos(a) * skx)}" cy="${f1(4 + Math.sin(a) * sky)}" r="${f1(4 + r() * 3)}" fill="#F3F1EC"/>`; }
    s += `<rect x="${f1(-W * 0.06)}" y="${f1(-H * 0.07)}" width="${f1(W * 0.12)}" height="${f1(H * 0.08)}" fill="#3A2416"/><ellipse cx="0" cy="4" rx="${f1(skx)}" ry="${f1(sky)}" fill="url(#${id}sk)"/>${fluff}`;
    // the tree: dark underlayer, then hundreds of boughs, back to front
    const hw = (t) => W * (0.05 + 0.95 * t) * (0.84 + 0.16 * ((t * 6.2) % 1));
    let under = "M0 " + f1(-H * 0.99);
    for (let k = 0; k <= 40; k++) { const t = k / 40; under += ` L${f1(hw(t) * 0.9)} ${f1(-H * 0.97 + t * H * 0.95)}`; }
    for (let k = 40; k >= 0; k--) { const t = k / 40; under += ` L${f1(-hw(t) * 0.9)} ${f1(-H * 0.97 + t * H * 0.95)}`; }
    s += `<path d="${under}Z" fill="#061A10"/>`;
    const B = [];
    for (let k = 0; k < 520; k++) {
      const t = Math.pow(r(), 0.7), y = -H * 0.97 + t * H * 0.93, u = r() * 2 - 1, x = u * hw(t) * 0.93, dir = u < 0 ? -1 : 1;
      const len = (H * 0.05 + H * 0.09 * t) * (0.65 + 0.55 * r()) * (0.55 + 0.45 * Math.abs(u)), dr = 12 + 22 * r() + 12 * t, z = 1 - Math.abs(u) + r() * 0.25;
      B.push({ x, y, dir, len, dr, z, v: Math.floor(r() * 6), l: Math.min(1, 0.12 + 0.55 * r() * (0.5 + 0.5 * (1 - Math.abs(u))) + 0.15 * (1 - t)) });
    }
    B.sort((a, b) => a.z - b.z);
    s += B.map((b) => {
      const c1 = mixC("#05170D", "#1C5C39", b.l), c2 = mixC("#12402A", "#3D9A63", b.l);
      const tf = `translate(${f1(b.x)} ${f1(b.y)}) scale(${b.dir} 1) rotate(${f1(b.dr)}) scale(${f1(b.len)})`;
      return `<use href="#${id}b${b.v}" transform="${tf}" fill="${c1}"/><use href="#${id}b${(b.v + 3) % 6}" transform="${tf} translate(.08 -.03) scale(.72)" fill="${c2}"/>`;
    }).join("");
    // red velvet ribbon and gold bead garland swags
    const swag = (t0, t1, dip) => { const y0 = -H * 0.97 + t0 * H * 0.93, y1 = -H * 0.97 + t1 * H * 0.93; const x0 = -hw(t0) * 0.86, x1 = hw(t1) * 0.86; return `M${f1(x0)} ${f1(y0)} Q${f1((x0 + x1) / 2)} ${f1((y0 + y1) / 2 + H * dip)} ${f1(x1)} ${f1(y1)}`; };
    [[0.3, 0.42], [0.58, 0.7], [0.83, 0.93]].forEach(([a, b]) => { s += `<path d="${swag(a, b, 0.05)}" stroke="#000" stroke-opacity=".3" stroke-width="${f1(H * 0.026)}" fill="none" transform="translate(2 4)"/><path d="${swag(a, b, 0.05)}" stroke="url(#${id}rib)" stroke-width="${f1(H * 0.022)}" fill="none" stroke-linecap="round"/>`; });
    [[0.16, 0.24], [0.44, 0.53], [0.71, 0.8]].forEach(([a, b]) => { s += `<path d="${swag(b, a, 0.035)}" stroke="#F2D27E" stroke-width="${f1(H * 0.009)}" fill="none" stroke-dasharray="0 ${f1(H * 0.016)}" stroke-linecap="round"/>`; });
    // ornaments: glass balls, teardrops, candy canes, little gold stars
    const pl = []; const free = (x, y, d) => pl.every((p) => Math.hypot(p[0] - x, p[1] - y) > d);
    const grads = ["red", "goldb", "sil", "green", "red", "goldb"]; let orn = ""; let n = 0, tries = 0;
    while (n < 30 && tries < 3000) {
      tries++;
      const t = 0.14 + r() * 0.82, y = -H * 0.97 + t * H * 0.93, x = (r() * 2 - 1) * hw(t) * 0.78; const sz = H * (0.014 + 0.012 * r()) * (0.6 + 0.6 * t);
      if (!free(x, y, sz * 3.2)) continue;
      pl.push([x, y]); const kind = n % 7;
      if (kind === 5) {
        const d = `M${f1(x)} ${f1(y - sz * 1.2)} l0 ${f1(sz * 2.4)} m0 ${f1(-sz * 2.4)} a${f1(sz * 0.6)} ${f1(sz * 0.6)} 0 0 0 ${f1(-sz * 1.2)} 0`;
        orn += `<path d="${d}" stroke="#F3F1EC" stroke-width="${f1(sz * 0.45)}" fill="none" stroke-linecap="round"/><path d="${d}" stroke="#D11F36" stroke-width="${f1(sz * 0.45)}" fill="none" stroke-dasharray="${f1(sz * 0.3)} ${f1(sz * 0.35)}"/>`;
      } else if (kind === 6) {
        let st = ""; for (let q = 0; q < 10; q++) { const a = -Math.PI / 2 + q * Math.PI / 5, rr = q % 2 ? sz * 0.5 : sz * 1.15; st += (q ? "L" : "M") + f1(x + Math.cos(a) * rr) + " " + f1(y + Math.sin(a) * rr); }
        orn += `<path d="${st}Z" fill="#E9C877" stroke="#FFF1B8" stroke-width="1"/>`;
      } else if (kind === 3) {
        orn += `<path d="M${f1(x)} ${f1(y - sz * 1.3)} C${f1(x + sz * 1.1)} ${f1(y - sz * 0.2)} ${f1(x + sz * 0.9)} ${f1(y + sz * 0.9)} ${f1(x)} ${f1(y + sz * 1.9)} C${f1(x - sz * 0.9)} ${f1(y + sz * 0.9)} ${f1(x - sz * 1.1)} ${f1(y - sz * 0.2)} ${f1(x)} ${f1(y - sz * 1.3)}Z" fill="url(#${id}sil)"/><path d="M${f1(x)} ${f1(y - sz * 1.3)} C${f1(x + sz * 1.1)} ${f1(y - sz * 0.2)} ${f1(x + sz * 0.9)} ${f1(y + sz * 0.9)} ${f1(x)} ${f1(y + sz * 1.9)}" fill="none" stroke="#7FD2FF" stroke-width="${f1(sz * 0.3)}" opacity=".6"/><rect x="${f1(x - sz * 0.25)}" y="${f1(y - sz * 1.6)}" width="${f1(sz * 0.5)}" height="${f1(sz * 0.35)}" fill="url(#${id}gold)"/>`;
      } else {
        orn += `<path d="M${f1(x)} ${f1(y - sz * 2.2)} L${f1(x)} ${f1(y - sz)}" stroke="#E9C877" stroke-width="1"/>` + ball(id, x, y, sz, grads[n % 6]) + `<circle cx="${f1(x - sz * 0.35)}" cy="${f1(y - sz * 0.4)}" r="${f1(sz * 0.16)}" fill="#fff" class="glint" opacity="0"/>`;
      }
      n++;
    }
    s += orn;
    // light strands: a thin wire swagged round the tree with evenly spaced bulbs
    let lights = ""; const S = 8;
    for (let q = 0; q < S; q++) {
      const t0 = 0.08 + q * (0.87 / S), t1 = t0 + 0.07; const pts = [];
      for (let k = 0; k <= 60; k++) { const p = k / 60, t = t0 + (t1 - t0) * p, y = -H * 0.97 + t * H * 0.93 + Math.sin(Math.PI * p) * H * 0.03, x = (-1 + 2 * p) * hw(t) * 0.92; pts.push([x, y]); }
      lights += `<path d="M${pts.map((p) => f1(p[0]) + " " + f1(p[1])).join(" L")}" stroke="#0B2216" stroke-width="1.4" fill="none" opacity=".9"/>`;
      const nb = Math.round(4 + 12 * (t0 + 0.05));
      for (let k = 0; k < nb; k++) {
        const p = (k + 0.5) / nb, P = pts[Math.round(p * 60)], ci = (k + q * 2) % 6, rg = H * 0.042, rc = H * 0.0075;
        lights += `<g class="bulb" data-ph="${f1(k * 0.62 + q * 1.3)}"><circle class="bg" cx="${f1(P[0])}" cy="${f1(P[1] + rc)}" r="${f1(rg)}" fill="url(#${id}l${ci})"/><ellipse class="bc" cx="${f1(P[0])}" cy="${f1(P[1] + rc)}" rx="${f1(rc * 0.8)}" ry="${f1(rc * 1.15)}" fill="${LCOL[ci][0]}"/><circle cx="${f1(P[0] - rc * 0.2)}" cy="${f1(P[1] + rc * 0.6)}" r="${f1(rc * 0.38)}" fill="${LCOL[ci][1]}"/><rect x="${f1(P[0] - rc * 0.45)}" y="${f1(P[1] - rc * 0.6)}" width="${f1(rc * 0.9)}" height="${f1(rc * 0.7)}" fill="#1E3A2A"/></g>`;
      }
    }
    s += lights;
    // star topper with a soft halo
    const sy = -H * 0.99 - H * 0.025, sr = H * 0.055; let st = "";
    for (let q = 0; q < 10; q++) { const a1 = -Math.PI / 2 + q * Math.PI / 5, a2 = -Math.PI / 2 + (q + 1) * Math.PI / 5, r1 = q % 2 ? sr * 0.45 : sr, r2 = q % 2 ? sr : sr * 0.45; st += `<path d="M0 ${f1(sy)} L${f1(Math.cos(a1) * r1)} ${f1(sy + Math.sin(a1) * r1)} L${f1(Math.cos(a2) * r2)} ${f1(sy + Math.sin(a2) * r2)}Z" fill="${q % 2 ? "#E0A63A" : "#FFD86B"}"/>`; }
    s += `<circle cx="0" cy="${f1(sy)}" r="${f1(sr * 3.2)}" fill="url(#${id}halo)" class="halo"/><g class="rays" data-cy="${f1(sy)}">${[0, 1, 2, 3, 4, 5, 6, 7].map((q) => { const a = q * Math.PI / 4; return `<path d="M${f1(Math.cos(a) * sr * 1.3)} ${f1(sy + Math.sin(a) * sr * 1.3)} L${f1(Math.cos(a) * sr * (q % 2 ? 1.8 : 2.2))} ${f1(sy + Math.sin(a) * sr * (q % 2 ? 1.8 : 2.2))}" stroke="#FFE7A0" stroke-width="2" stroke-linecap="round" opacity=".22"/>`; }).join("")}</g>${st}<circle cx="${f1(-sr * 0.2)}" cy="${f1(sy - sr * 0.25)}" r="${f1(sr * 0.12)}" fill="#fff" opacity=".8"/>`;
    s += presents(W, v);
    s += `<g class="tfront"></g><g class="smokef"></g>`;
    return `<g class="xtree" data-rx="${f1(rx)}" data-ry="${f1(ry)}" data-cy="${f1(cy)}" data-u="${(H / 600 * 1.5).toFixed(3)}">${s}</g>`;
  }

  // ---------- the parade past the window, all facing left, feet on y=0 ----------
  const SNOWY = (x, ww, wh) => wh - 40 + 5 * Math.sin(x / ww * TAU + 0.6);
  function snowPath(ww, wh) { let d = `M0 ${wh}`; for (let k = 0; k <= 24; k++) { const x = k / 24 * ww; d += ` L${x.toFixed(1)} ${SNOWY(x, ww, wh).toFixed(1)}`; } return d + ` L${ww} ${wh}Z`; }
  function walkers() {
    const leg = (x, col, boot, len = 16, w = 7) => `<g class="lg" data-x="${x}" data-p="${-len}"><rect x="${x - w / 2}" y="${-len}" width="${w}" height="${len}" rx="${w / 2}" fill="${col}"/><rect x="${x - w / 2 - 3}" y="-5" width="${w + 5}" height="6" rx="3" fill="${boot}"/></g>`;
    const snowman = `<g class="wk" data-off="0" data-wad="1"><ellipse cx="0" cy="0" rx="20" ry="4" fill="#000" opacity=".2"/><circle cx="0" cy="-17" r="19" fill="#F4F7FB"/><circle cx="0" cy="-46" r="14" fill="#F4F7FB"/><circle cx="0" cy="-68" r="11" fill="#F4F7FB"/><circle cx="-4" cy="-50" r="1.8" fill="#222"/><circle cx="-4" cy="-42" r="1.8" fill="#222"/><path d="M-8 -58 Q0 -53 9 -58 L11 -53 Q0 -49 -9 -53Z" fill="#D11F36"/><rect x="2" y="-56" width="6" height="16" rx="2" fill="#D11F36"/><circle cx="-5" cy="-71" r="1.6" fill="#222"/><circle cx="2" cy="-71" r="1.6" fill="#222"/><path d="M-4 -67 L-17 -65 L-4 -64Z" fill="#FF8A2B"/><rect x="-11" y="-84" width="22" height="4" rx="1" fill="#1b1b1b"/><rect x="-7" y="-99" width="14" height="16" rx="1" fill="#1b1b1b"/><rect x="-7" y="-87" width="14" height="3" fill="#D11F36"/><path class="arm" data-px="-12" data-py="-48" d="M-12 -48 L-28 -60 M-24 -57 L-29 -52" stroke="#5A3A22" stroke-width="2.5" stroke-linecap="round" fill="none"/><path d="M12 -48 L26 -38" stroke="#5A3A22" stroke-width="2.5" stroke-linecap="round"/></g>`;
    const dleg = (x, col, ph, front) => `<g class="lg2" data-x="${x}" data-ph="${ph}" data-f="${front ? 1 : 0}"><path d="M${x - 4} -30 L${x + 4} -30 L${x + 3} -15 L${x - 2} -15Z" fill="${col}"/><g class="lo" data-kx="${x + 0.5}" data-ky="-15"><path d="M${x - 2} -15 L${x + 3} -15 L${x + 2} -4 L${x - 1} -4Z" fill="${col}"/><path d="M${x - 3} -4 L${x + 4} -4 L${x + 4} 0 L${x - 3} 0Z" fill="#2A1A0E"/></g></g>`;
    const deer = `<g class="wk" data-off="7.2" data-deer="1"><ellipse cx="2" cy="0" rx="32" ry="4" fill="#000" opacity=".2"/>${dleg(-16, "#5E3B20", Math.PI, 1)}${dleg(22, "#5E3B20", 0, 0)}<g class="dbody"><path d="M30 -38 q9 -8 7 4" fill="#E8D8C0"/><path d="M-22 -40 Q-24 -52 -8 -54 L22 -52 Q36 -50 34 -36 Q32 -24 18 -24 L-12 -24 Q-24 -26 -22 -40Z" fill="#8A5A34"/><path d="M-16 -28 Q2 -22 22 -27 Q10 -32 -14 -31Z" fill="#C9A27A"/><path d="M-14 -46 Q-6 -40 2 -46" stroke="#B3192F" stroke-width="4" fill="none" stroke-linecap="round"/><circle cx="-6" cy="-42" r="3" fill="#E9C877"/>
 <g class="dhead"><path d="M-12 -44 Q-20 -58 -26 -66 L-16 -72 Q-8 -58 -2 -48Z" fill="#8A5A34"/><ellipse cx="-24" cy="-72" rx="12" ry="10" fill="#8A5A34"/><path d="M-32 -76 Q-46 -74 -48 -66 Q-46 -61 -36 -63 Q-30 -66 -28 -70Z" fill="#A87A52"/><path d="M-17 -79 Q-8 -86 -9 -76Z" fill="#6B4426"/><circle cx="-27" cy="-75" r="1.9" fill="#1b1b1b"/><circle cx="-27.6" cy="-75.6" r=".6" fill="#fff"/>
 <path d="M-22 -81 Q-26 -96 -36 -102 M-28 -93 Q-34 -95 -36 -90 M-17 -81 Q-14 -98 -4 -104 M-15 -94 Q-9 -96 -8 -92" stroke="#D9C2A0" stroke-width="3" stroke-linecap="round" fill="none"/>
 <circle class="nose" cx="-48" cy="-66" r="8" fill="#FF3B3B" opacity=".35"/><circle cx="-48" cy="-66" r="4.4" fill="#FF2B2B"/><circle cx="-49.3" cy="-67.3" r="1.3" fill="#fff" opacity=".8"/></g></g>${dleg(-10, "#7A4E2C", 0, 1)}${dleg(28, "#7A4E2C", Math.PI, 0)}</g>`;
    const santa = `<g class="wk" data-off="9.8"><ellipse cx="0" cy="0" rx="24" ry="4" fill="#000" opacity=".2"/>${leg(-7, "#B3192F", "#1b1b1b", 20)}${leg(7, "#B3192F", "#1b1b1b", 20)}<path d="M20 -66 Q40 -70 38 -42 Q36 -24 18 -30Z" fill="#7A5230"/><path d="M20 -66 Q28 -72 30 -64" stroke="#C9A24F" stroke-width="2" fill="none"/><rect x="-19" y="-58" width="38" height="42" rx="15" fill="#C42540"/><rect x="-19" y="-24" width="38" height="7" rx="3" fill="#F4F1EA"/><rect x="-19" y="-40" width="38" height="6" fill="#1b1b1b"/><rect x="-5" y="-41" width="10" height="8" rx="1" fill="none" stroke="#E9C877" stroke-width="2"/><rect x="-2" y="-58" width="5" height="18" fill="#F4F1EA"/><circle cx="-2" cy="-68" r="11" fill="#F2C3A0"/><path d="M-14 -68 Q-14 -48 -2 -46 Q10 -48 9 -66 Q-2 -60 -14 -68Z" fill="#F4F1EA"/><circle cx="-8" cy="-70" r="1.6" fill="#222"/><circle cx="-11" cy="-65" r="3" fill="#E89A86"/><path d="M-14 -74 Q-4 -98 14 -84 L22 -88 Q12 -76 9 -75Z" fill="#C42540"/><rect x="-15" y="-78" width="26" height="6" rx="3" fill="#F4F1EA"/><circle cx="23" cy="-88" r="4" fill="#F4F1EA"/><path class="arm" data-px="-14" data-py="-52" d="M-14 -52 Q-26 -44 -24 -34" stroke="#C42540" stroke-width="8" stroke-linecap="round" fill="none"/></g>`;
    const elf = (off, gift) => `<g class="wk" data-off="${off}" data-s=".85"><ellipse cx="0" cy="0" rx="14" ry="3" fill="#000" opacity=".2"/>${leg(-5, "#D11F36", "#2E1A0E", 14)}${leg(5, "#F4F1EA", "#2E1A0E", 14)}<path d="M-13 -14 L13 -14 L9 -38 L-9 -38Z" fill="#1E8A4E"/><path d="M-13 -14 l4 -5 l4 5 l4 -5 l4 5 l4 -5 l4 5 l3 -4" stroke="#E9C877" stroke-width="2" fill="none"/><rect x="-11" y="-24" width="22" height="4" fill="#5A3A22"/><circle cx="0" cy="-46" r="9" fill="#F2C3A0"/><path d="M-9 -47 L-15 -53 L-8 -44Z M9 -47 L14 -53 L8 -44Z" fill="#F2C3A0"/><circle cx="-4" cy="-47" r="1.4" fill="#222"/><path d="M-6 -42 Q-3 -40 -1 -42" stroke="#8A3A2A" stroke-width="1.2" fill="none"/><path d="M-10 -51 Q2 -58 10 -51 L8 -54 Q14 -66 24 -60 Q16 -66 2 -60Z" fill="#1E8A4E"/><path d="M-10 -51 Q0 -55 10 -51" stroke="#D11F36" stroke-width="3" fill="none"/><circle cx="25" cy="-60" r="2.6" fill="#E9C877"/>${gift ? `<rect x="-24" y="-34" width="16" height="14" rx="1" fill="#D11F36"/><rect x="-17.5" y="-34" width="3" height="14" fill="#E9C877"/><rect x="-24" y="-28.5" width="16" height="3" fill="#E9C877"/>` : `<path class="arm" data-px="-10" data-py="-32" d="M-10 -32 L-20 -40" stroke="#1E8A4E" stroke-width="5" stroke-linecap="round"/><circle cx="-21" cy="-41" r="3" fill="#F2C3A0"/>`}</g>`;
    // a very tall elf at the back: long green coat with gold stitching, white fur collar and cuffs, wide black belt, yellow tights, curly black shoes, green hat with a yellow band and a red feather
    const tleg = (x, ph) => `<g class="lg" data-x="${x}" data-p="-72" data-ph="${ph}"><path d="M${x - 5.5} -72 L${x + 5.5} -72 L${x + 5} -40 Q${x + 6} -30 ${x + 4} -8 L${x - 4} -8 Q${x - 5} -30 ${x - 5} -40Z" fill="#F7C61C"/><path d="M${x + 1} -72 L${x + 5.5} -72 L${x + 5} -40 Q${x + 6} -30 ${x + 4} -8 L${x + 1} -8Z" fill="#C9970E" opacity=".45"/><path d="M${x + 6} -10 Q${x + 7} 1 ${x - 2} 1 L${x - 15} 1 Q${x - 22} 0 ${x - 23} -7 Q${x - 23} -12 ${x - 19} -11 Q${x - 16} -10 ${x - 18} -7 Q${x - 15} -5 ${x - 9} -8 Q${x - 4} -12 ${x + 6} -10Z" fill="#111"/><path d="M${x - 14} -4 Q${x - 6} -5 ${x + 3} -3" stroke="#444" stroke-width="1" fill="none"/></g>`;
    const fluff = (cx, cy, rx, ry, n) => { let o = ""; for (let k = 0; k < n; k++) { const a = k / n * TAU; o += `<circle cx="${(cx + Math.cos(a) * rx).toFixed(1)}" cy="${(cy + Math.sin(a) * ry).toFixed(1)}" r="${(Math.min(rx, ry) * 0.45).toFixed(1)}" fill="#F6F4EE"/>`; } return o + `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="#F6F4EE"/>`; };
    const vine = (x, y0, y1, dir) => { let o = `<path d="M${x} ${y0} V${y1}" stroke="#E3CB4E" stroke-width="1.1" opacity=".6"/>`; for (let y = y0 + 4; y < y1 - 2; y += 7) o += `<path d="M${x} ${y} q${dir * 4} -1 ${dir * 4} 3 q0 2 ${-dir * 2} 1.5" stroke="#E3CB4E" stroke-width="1" fill="none" opacity=".6"/>`; return o; };
    const tall = `<g class="wk" data-off="16.4" data-s=".78"><ellipse cx="0" cy="0" rx="22" ry="4" fill="#000" opacity=".2"/>${tleg(-6, 0)}${tleg(7, 1)}
 <g class="arm" data-px="13" data-py="-146"><path d="M13 -146 Q22 -122 20 -96" stroke="#1D6E27" stroke-width="11" stroke-linecap="round" fill="none"/>${fluff(20, -92, 7, 5, 7)}<circle cx="20" cy="-84" r="5" fill="#EFBF9A"/></g>
 <path d="M-17 -150 Q-25 -112 -29 -70 Q-29 -60 -19 -62 Q-8 -66 -1 -88 Q5 -66 16 -62 Q27 -60 28 -70 Q25 -112 17 -150Z" fill="#34A23C" stroke="#1D6E27" stroke-width="2.4"/>
 <path d="M3 -150 Q14 -150 17 -150 Q25 -112 28 -70 Q27 -60 16 -62 Q8 -66 3 -82Z" fill="#000" opacity=".13"/>
 <path d="M-1 -144 V-88" stroke="#1D6E27" stroke-width="2"/>${vine(-5, -140, -114, -1)}${vine(3, -140, -114, 1)}${vine(-5, -100, -74, -1)}${vine(3, -100, -74, 1)}
 <path d="M-26 -78 q4 -4 7 0 M-23 -70 q3 -3 6 0 M18 -76 q4 -4 7 0 M20 -68 q3 -3 6 0" stroke="#E3CB4E" stroke-width="1" fill="none" opacity=".6"/>
 <path d="M-26 -114 L26 -114 L27 -102 L-27 -102Z" fill="#111"/><rect x="-7.5" y="-115" width="15" height="14" rx="1.5" fill="none" stroke="#D8B43A" stroke-width="3"/><rect x="-1" y="-112" width="3" height="8" fill="#D8B43A"/>
 ${fluff(-9, -151, 10, 6, 9)}${fluff(9, -151, 10, 6, 9)}
 <rect x="-4.5" y="-162" width="9" height="10" fill="#E2AE88"/>
 ${[[-12, -182, 6.5], [-13, -174, 5.5], [10, -183, 6.5], [12, -174, 6], [11, -166, 5], [-6, -189, 6], [3, -190, 6]].map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="#5B3A1E"/><path d="M${x - r * 0.5} ${y} q${r * 0.5} ${-r * 0.6} ${r} 0" stroke="#7A5232" stroke-width="1.3" fill="none"/>`).join("")}
 <ellipse cx="-1" cy="-174" rx="12.5" ry="14.5" fill="#F0C29E"/>
 <ellipse cx="12" cy="-172" rx="3" ry="4.5" fill="#E2AE88"/>
 <path d="M-11 -181 Q-7 -184 -3 -182 M-1 -182 Q3 -184 7 -181" stroke="#4A2E18" stroke-width="1.8" fill="none" stroke-linecap="round"/>
 <ellipse cx="-7" cy="-176" rx="2.6" ry="3.2" fill="#fff"/><circle cx="-7.4" cy="-175.6" r="1.7" fill="#3A2A1A"/><ellipse cx="2.5" cy="-176" rx="2.6" ry="3.2" fill="#fff"/><circle cx="2.1" cy="-175.6" r="1.7" fill="#3A2A1A"/>
 <path d="M-3 -173 Q-6 -168 -2 -167" stroke="#C98B6A" stroke-width="1.4" fill="none"/>
 <path d="M-9 -163 Q-3 -159 4 -163" stroke="#9A3E3E" stroke-width="2" fill="none" stroke-linecap="round"/><circle cx="-11" cy="-166" r="2.6" fill="#F09A8A" opacity=".55"/><circle cx="6" cy="-166" r="2.6" fill="#F09A8A" opacity=".55"/>
 <path d="M-14 -188 Q-12 -206 2 -214 Q8 -204 15 -188Z" fill="#34A23C"/><path d="M2 -214 Q8 -204 15 -188 L6 -188 Q6 -204 2 -214Z" fill="#000" opacity=".15"/>
 <path d="M-15 -190 Q0 -196 16 -190 L16 -183 Q0 -189 -15 -183Z" fill="#F2C21E"/><path d="M-15 -186 Q0 -192 16 -186" stroke="#C99A10" stroke-width="1" fill="none"/>
 <path d="M12 -192 Q20 -204 28 -208 Q24 -200 14 -190Z" fill="#D9262E"/><path d="M13 -191 Q21 -201 27 -207" stroke="#8E1A1A" stroke-width=".8" fill="none"/>
 <g class="arm wave" data-px="-13" data-py="-146"><path d="M-13 -146 Q-28 -150 -34 -172" stroke="#34A23C" stroke-width="11" stroke-linecap="round" fill="none"/>${fluff(-35, -176, 6, 7, 7)}<circle cx="-36" cy="-185" r="5.5" fill="#F0C29E"/><path d="M-40 -188 l-2 -6 M-37 -190 l-1 -7 M-34 -190 l1 -6 M-31 -188 l2 -5" stroke="#F0C29E" stroke-width="2.6" stroke-linecap="round"/></g></g>`;
    return snowman + deer + santa + elf(12.1, true) + elf(13.5, false) + tall;
  }
  /* Santa's sleigh and four reindeer flying across the window sky (Christmas Eve and Christmas Day), facing left */
  function sleigh() {
    const rd = (x, lead) => `<g class="fly" data-x="${x}"><path d="M${x - 12} -4 Q${x - 14} -12 ${x - 2} -13 L${x + 12} -12 Q${x + 18} -10 ${x + 16} -3 Q${x + 14} 2 ${x + 6} 2 L${x - 6} 2 Q${x - 13} 1 ${x - 12} -4Z" fill="#7A4E2C"/><path d="M${x - 10} -8 L${x - 17} -18 L${x - 12} -21 L${x - 6} -10Z" fill="#7A4E2C"/><ellipse cx="${x - 16}" cy="-21" rx="6" ry="4.5" fill="#7A4E2C"/><path d="M${x - 15} -25 l-3 -7 M${x - 17} -29 l-4 -1 M${x - 12} -25 l1 -8 M${x - 11} -30 l3 -1" stroke="#D9C2A0" stroke-width="1.6" stroke-linecap="round"/><g class="fl"><path d="M${x - 8} 1 l-6 6 M${x + 10} 1 l6 6" stroke="#5E3B20" stroke-width="3" stroke-linecap="round"/></g>${lead ? `<circle cx="${x - 22}" cy="-20" r="7" fill="#FF3B3B" opacity=".4"/><circle cx="${x - 22}" cy="-20" r="2.8" fill="#FF2B2B"/>` : `<circle cx="${x - 22}" cy="-20" r="2" fill="#2A1A0E"/>`}</g>`;
    let s = `<g class="sleigh">`;
    for (let k = 0; k < 18; k++) s += `<circle class="dust" data-k="${k}" r="${(1 + (k % 4) * 0.6).toFixed(1)}" fill="#FFE7A0" opacity="0"/>`;
    s += `<path d="M-50 -10 L60 -10" stroke="#C9A24F" stroke-width="1.2" opacity=".8"/>`;
    s += rd(-120, true) + rd(-82, false) + rd(-44, false) + rd(-6, false);
    // the sleigh: red body with a gold curl, Santa with his sack
    s += `<path d="M30 -2 L92 -2 Q104 -2 104 -14 L104 -26 L94 -26 Q92 -14 82 -14 L40 -14 Q32 -14 30 -8Z" fill="#B3192F"/><path d="M26 4 L96 4 Q110 4 110 -6" stroke="#E9C877" stroke-width="3" fill="none" stroke-linecap="round"/><path d="M34 -6 L90 -6" stroke="#E9C877" stroke-width="2"/>`;
    s += `<path d="M78 -16 Q96 -40 104 -24 Z" fill="#7A5230"/><rect x="48" y="-34" width="22" height="22" rx="8" fill="#C42540"/><circle cx="56" cy="-40" r="7" fill="#F2C3A0"/><path d="M49 -40 Q49 -30 56 -29 Q62 -30 62 -39 Q56 -36 49 -40Z" fill="#F4F1EA"/><path d="M49 -44 Q55 -58 66 -50 L71 -53 Q64 -45 62 -45Z" fill="#C42540"/><rect x="48" y="-47" width="16" height="4" rx="2" fill="#F4F1EA"/><circle cx="71" cy="-53" r="2.5" fill="#F4F1EA"/>`;
    s += `<path class="wavea" data-px="50" data-py="-30" d="M50 -30 L38 -44" stroke="#C42540" stroke-width="5" stroke-linecap="round"/><circle class="waveh" cx="37" cy="-46" r="3" fill="#F2C3A0"/>`;
    return s + `</g>`;
  }

  /* the room: wall, floor, window (with parade), the tree scene, and a soft dark edge */
  function roomSvg(W2, H2, win, treeAt, H, seed, v) {
    const id = "r" + OID++; const fl = H2 * 0.8;
    let s = `<defs><linearGradient id="${id}w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0D0A0A"/><stop offset="1" stop-color="#191110"/></linearGradient><linearGradient id="${id}f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#21150E"/><stop offset="1" stop-color="#0C0806"/></linearGradient><radialGradient id="${id}v" cx=".5" cy=".5" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".65"/></radialGradient><linearGradient id="${id}win" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0D1A33"/><stop offset="1" stop-color="#1C2C4A"/></linearGradient></defs>`;
    s += `<rect width="${W2}" height="${H2}" fill="url(#${id}w)"/>`;
    for (let x = 0; x < W2; x += W2 / 24) s += `<rect x="${f1(x)}" y="0" width="${f1(W2 / 48)}" height="${f1(fl)}" fill="#fff" opacity=".016"/>`;
    if (win) {
      const [wx, wy, ww, wh] = win; let fl2 = ""; const r = rnd(77);
      for (let k = 0; k < 26; k++) fl2 += `<circle class="flake" cx="${f1(r() * ww)}" cy="${f1(r() * wh)}" r="${f1(1 + r() * 2)}" fill="#fff" opacity="${(0.3 + r() * 0.5).toFixed(2)}" data-n="${1 + (k % 3)}" data-d="${(r() * 6).toFixed(2)}"/>`;
      s += `<rect x="${wx - 14}" y="${wy - 14}" width="${ww + 28}" height="${wh + 28}" rx="6" fill="#2A1C14"/><svg x="${wx}" y="${wy}" width="${ww}" height="${wh}" overflow="hidden"><g class="win" data-w="${ww}" data-h="${wh}"><rect width="${ww}" height="${wh}" fill="url(#${id}win)"/>${fl2}${v === "EVE" || v === "DAY" ? sleigh() : ""}<path d="${snowPath(ww, wh)}" fill="#C9D6EA" transform="translate(0 -8)"/><g class="walkers">${walkers()}</g><path d="${snowPath(ww, wh)}" fill="#DCE6F5"/><rect width="${ww}" height="${wh}" fill="#1C2C4A" opacity=".1"/></g></svg><rect x="${wx + ww / 2 - 4}" y="${wy}" width="8" height="${wh}" fill="#2A1C14"/><rect x="${wx}" y="${wy + wh / 2 - 4}" width="${ww}" height="8" fill="#2A1C14"/><rect x="${wx - 22}" y="${wy + wh + 12}" width="${ww + 44}" height="10" rx="3" fill="#2A1C14"/>`;
    }
    s += `<rect y="${f1(fl)}" width="${W2}" height="${f1(H2 - fl)}" fill="url(#${id}f)"/>`;
    for (let k = 1; k < 6; k++) s += `<path d="M0 ${f1(fl + (H2 - fl) * k / 6)} H${W2}" stroke="#000" stroke-opacity=".25" stroke-width="2"/>`;
    s += `<rect y="${f1(fl - 10)}" width="${W2}" height="10" fill="#140D09"/>`;
    s += `<g transform="translate(${treeAt[0]} ${treeAt[1]})">${treeSvg(H, seed, v)}</g>`;
    s += `<rect width="${W2}" height="${H2}" fill="url(#${id}v)"/>`;
    return s;
  }

  /** The two shapes: wide (TVs, computers) and tall (phones) */
  const LAYOUTS = {
    wide: { w: 1920, h: 1080, win: [1560, 150, 250, 330], tree: [400, 968], H: 620, seed: 11 },
    tall: { w: 390, h: 844, win: null, tree: [195, 800], H: 230, seed: 12 },
  };

  /**
   * Builds the room into an <svg> element. Returns { at(t) } which poses everything at t seconds.
   * opts: { layout: "wide"|"tall", variant: "L1".."AFTER", align: "xMinYMax slice" }
   */
  function build(svg, opts) {
    const L = LAYOUTS[opts.layout || "wide"]; const v = opts.variant || variant(new Date());
    svg.setAttribute("viewBox", `0 0 ${L.w} ${L.h}`);
    svg.setAttribute("preserveAspectRatio", opts.align || "xMidYMax slice");
    svg.innerHTML = roomSvg(L.w, L.h, L.win, L.tree, L.H, L.seed, v);
    return pose(svg);
  }

  /* gathers every moving part, and returns at(t) */
  function pose(root) {
    const scenes = [...root.querySelectorAll(".xtree")].map((g) => {
      const rx = +g.dataset.rx, ry = +g.dataset.ry, cy = +g.dataset.cy, u = +g.dataset.u;
      const back = g.querySelector(".tback"), front = g.querySelector(".tfront");
      const cars = TRAIN.map((c, i) => {
        const e = document.createElementNS(NS, "g"); const bx = BOX[i];
        const back2 = bx.map((b) => `<rect x="${b.x0}" y="${-b.top}" width="${b.x1 - b.x0}" height="${b.top - b.bot}" fill="${b.end}"/>`).join("");
        e.innerHTML = bx.map((b, j) => `<g class="bx" data-j="${j}"><polygon class="roof" fill="${b.roof}" stroke="#000" stroke-opacity=".35" stroke-width="1"/><polygon class="endf" fill="${b.end}" stroke="#000" stroke-opacity=".35" stroke-width="1"/></g>`).join("") + `<g class="side">${back2}${c.g}</g>`;
        back.appendChild(e);
        return { e, len: c.len, side: "b", bx, boxG: [...e.querySelectorAll(".bx")].map((x) => ({ g: x, roof: x.querySelector(".roof"), endf: x.querySelector(".endf") })), sideG: e.querySelector(".side"), spk: [...e.querySelectorAll(".spk")].map((w) => ({ w, x: +w.dataset.x, y: +w.dataset.y, r: +w.dataset.r })) };
      });
      // smoke puffs: one every PUFF seconds, each lasting PUFF_LIFE seconds; puffs behind the tree go in the back layer
      const mk = (parent) => { const c = document.createElementNS(NS, "circle"); c.setAttribute("fill", "#D9D4CC"); c.setAttribute("opacity", "0"); parent.appendChild(c); return c; };
      const puffsB = [], puffsF = [];
      for (let k = 0; k < 12; k++) { puffsB.push(mk(g.querySelector(".smokeb"))); puffsF.push(mk(g.querySelector(".smokef"))); }
      return { g, rx, ry, cy, u, cars, puffsB, puffsF, bulbs: [...g.querySelectorAll(".bulb")].map((b) => ({ ph: +b.dataset.ph, bg: b.querySelector(".bg"), bc: b.querySelector(".bc") })), glints: [...g.querySelectorAll(".glint")], halo: g.querySelector(".halo"), rays: g.querySelector(".rays"), glows: [...g.querySelectorAll(".wglow")] };
    });
    const wins = [...root.querySelectorAll(".win")].map((W) => ({
      ww: +W.dataset.w, wh: +W.dataset.h,
      flakes: [...W.querySelectorAll(".flake")].map((f) => ({ f, x: +f.getAttribute("cx"), y: +f.getAttribute("cy"), n: +f.dataset.n, d: +f.dataset.d })),
      walkers: [...W.querySelectorAll(".wk")],
      sleigh: W.querySelector(".sleigh"),
    }));
    // the train's speed: one trip round the track per loop
    const SP = TAU / LOOP;
    const PUFF = 0.25, PUFF_LIFE = 2.4;
    const enginePos = (S, t) => { const a = -t * SP + 1.2; return { a, x: Math.cos(a) * S.rx, y: S.cy + Math.sin(a) * S.ry, sn: Math.sin(a), ca: Math.cos(a) }; };

    function at(t0) {
      const t = ((t0 % LOOP) + LOOP) % LOOP;
      // frequencies below are whole numbers of turns per loop, so the loop joins up
      const w = (turns) => TAU * turns / LOOP;
      scenes.forEach((S) => {
        S.bulbs.forEach((b) => { const x = 0.5 + 0.5 * Math.sin(t * w(5) - b.ph); const v = sm(x); b.bg.setAttribute("opacity", (0.35 + 0.65 * v).toFixed(3)); b.bc.setAttribute("opacity", (0.55 + 0.45 * v).toFixed(3)); });
        S.glints.forEach((g, k) => { const p = ((t / 3 + k * 0.173) % 1); g.setAttribute("opacity", p < 0.06 ? (Math.sin(p / 0.06 * Math.PI) * 0.95).toFixed(2) : "0"); });
        if (S.halo) S.halo.setAttribute("opacity", (0.85 + 0.15 * Math.sin(t * w(4))).toFixed(3));
        if (S.rays) S.rays.setAttribute("transform", `rotate(${(t * 6).toFixed(2)} 0 ${S.rays.dataset.cy})`);
        S.glows.forEach((g) => g.setAttribute("opacity", (0.9 + 0.1 * Math.sin(t * w(5))).toFixed(3)));
        // train: the ground is a circle seen at an angle, so turning at the ends shows the cars end-on
        const R = S.rx, k = S.ry / S.rx; const th = -t * SP + 1.2; let gap = 0;
        const P2 = (c, gx, gy, up) => [(c.px + c.d * gx).toFixed(1), (c.py + c.d * (gy * k - up)).toFixed(1)];
        S.cars.forEach((c, i) => {
          if (i > 0) gap += (S.cars[i - 1].len + c.len) / 2 + 5;
          const a = th + gap * S.u / R; const ca = Math.cos(a), sn = Math.sin(a);
          c.px = ca * S.rx; c.py = S.cy + sn * S.ry; c.d = (0.8 + 0.2 * (sn + 1) / 2) * S.u; c.z = sn;
          const hx = sn, hy = -ca; const vs = sn > 0 ? 1 : -1; const nx = ca * vs, ny = sn * vs;
          const bob = 0.5 * Math.sin(t * w(52) + i * 1.9) + 0.3 * Math.sin(t * w(110) + i);
          const sx = c.px + c.d * nx * CW / 2, sy = c.py + c.d * (ny * CW / 2 * k) - bob * c.d;
          c.sideG.setAttribute("transform", `matrix(${(c.d * hx).toFixed(4)} ${(c.d * hy * k).toFixed(4)} 0 ${c.d.toFixed(4)} ${sx.toFixed(1)} ${sy.toFixed(1)})`);
          const fr = hy > 0; const qx = ca * CW / 2, qy = sn * CW / 2; const pts = (x, up) => [P2(c, hx * x + qx, hy * x + qy, up), P2(c, hx * x - qx, hy * x - qy, up)];
          const order = c.bx.map((bb, j) => j).sort((p, q) => (fr ? c.bx[p].x1 - c.bx[q].x1 : c.bx[q].x0 - c.bx[p].x0));
          order.forEach((j) => {
            const bb = c.bx[j], Gb = c.boxG[j]; const xe = fr ? bb.x1 : bb.x0;
            const [p1, p2] = pts(xe, bb.bot + bob), [p3, p4] = pts(xe, bb.top + bob);
            Gb.endf.setAttribute("points", [p1, p2, p4, p3].map((p) => p.join(",")).join(" "));
            const [r1, r2] = pts(bb.x1, bb.top + bob), [r3, r4] = pts(bb.x0, bb.top + bob);
            Gb.roof.setAttribute("points", [r1, r2, r4, r3].map((p) => p.join(",")).join(" "));
            Gb.g.parentNode.insertBefore(Gb.g, c.sideG);
          });
          // wheels turn with the distance travelled (whole turns per loop for the spokes' 8-fold shape)
          c.spk.forEach((wh) => { const turns = Math.round(R * TAU / S.u / (TAU * wh.r) * 8) / 8; wh.w.setAttribute("transform", `rotate(${(t / LOOP * turns * 360).toFixed(1)} ${wh.x} ${wh.y})`); });
          const side = sn > 0 ? "f" : "b";
          if (side !== c.side) { (side === "f" ? S.g.querySelector(".tfront") : S.g.querySelector(".tback")).appendChild(c.e); c.side = side; }
        });
        ["f", "b"].forEach((sd) => S.cars.filter((c) => c.side === sd).sort((p, q) => p.z - q.z).forEach((c) => c.e.parentNode.appendChild(c.e)));
        // steady smoke from the chimney: puffs rise, grow and fade
        const live = [];
        for (let j = Math.floor((t - PUFF_LIFE) / PUFF) + 1; j * PUFF <= t; j++) live.push(j * PUFF);
        let ib = 0, iF = 0;
        live.forEach((s0) => {
          const E = enginePos(S, s0); const age = (t - s0) / PUFF_LIFE; const front = E.sn > 0;
          const d = (0.8 + 0.2 * (E.sn + 1) / 2) * S.u; const hx = E.sn, hy = -E.ca;
          const x = E.x + d * hx * 16 - age * 14 * S.u, y = E.y + d * (hy * 16 * (S.ry / S.rx) - 58) - age * 60 * S.u;
          const c = front ? S.puffsF[iF++] : S.puffsB[ib++]; if (!c) return;
          c.setAttribute("cx", x.toFixed(1)); c.setAttribute("cy", y.toFixed(1)); c.setAttribute("r", ((4 + age * 13) * S.u).toFixed(1)); c.setAttribute("opacity", (0.55 * (1 - age) * (front ? 1 : 0.5)).toFixed(3));
        });
        for (; ib < S.puffsB.length; ib++) S.puffsB[ib].setAttribute("opacity", "0");
        for (; iF < S.puffsF.length; iF++) S.puffsF[iF].setAttribute("opacity", "0");
      });
      wins.forEach((Wn) => {
        const { ww, wh } = Wn;
        Wn.flakes.forEach((F) => { const yy = (F.y + t * wh * F.n / LOOP) % wh; F.f.setAttribute("cy", yy.toFixed(1)); F.f.setAttribute("cx", (F.x + Math.sin(t * w(4) + F.d) * 4).toFixed(1)); });
        const v = (ww + 340) / 9;
        Wn.walkers.forEach((g, i) => {
          const off = +g.dataset.off, sc = 1.6 * (+(g.dataset.s || 1)); const tt = ((t + LOOP - off) % LOOP); const x = ww + 150 - tt * v;
          // feet sit a little way down into the snow wherever they are
          const y = SNOWY(x, ww, wh) + 5; const wad = !!g.dataset.wad;
          const bob = wad ? Math.abs(Math.sin(tt * 6)) * 2.5 : Math.abs(Math.sin(tt * 7)) * 1.5; const lean = wad ? Math.sin(tt * 6) * 6 : 0;
          g.setAttribute("transform", `translate(${x.toFixed(1)} ${(y - bob).toFixed(1)}) rotate(${lean.toFixed(1)}) scale(${sc})`);
          g.querySelectorAll(".lg").forEach((l, k) => { const ph = l.dataset.ph != null ? (+l.dataset.ph ? Math.PI : 0) : (k % 2 ? Math.PI : 0); const sw = Math.sin(tt * 7 + ph) * 20; l.setAttribute("transform", `rotate(${sw.toFixed(1)} ${l.dataset.x} ${l.dataset.p})`); });
          if (g.dataset.deer) {
            const ph0 = tt * 6.5;
            g.querySelectorAll(".lg2").forEach((l) => { const ph = ph0 + (+l.dataset.ph), fr = +l.dataset.f; const up = Math.sin(ph) * 24; const lift = Math.max(0, Math.cos(ph)); const knee = fr ? lift * 38 : -lift * 30; l.setAttribute("transform", `rotate(${up.toFixed(1)} ${l.dataset.x} -30) translate(0 ${(-lift * 2).toFixed(1)})`); const lo = l.querySelector(".lo"); lo.setAttribute("transform", `rotate(${knee.toFixed(1)} ${lo.dataset.kx} ${lo.dataset.ky})`); });
            g.querySelector(".dbody").setAttribute("transform", `translate(0 ${(-Math.abs(Math.sin(ph0)) * 2.2).toFixed(1)})`);
            g.querySelector(".dhead").setAttribute("transform", `rotate(${(Math.sin(ph0 * 2) * 3).toFixed(1)} -10 -46)`);
          }
          g.querySelectorAll(".arm").forEach((arm) => { const r = arm.classList.contains("wave") ? Math.sin(tt * 9) * 16 : Math.sin(tt * 7) * 12; arm.setAttribute("transform", `rotate(${r.toFixed(1)} ${arm.dataset.px} ${arm.dataset.py})`); });
          const n = g.querySelector(".nose"); if (n) { const p = 0.5 + 0.5 * Math.sin(t * w(19)); n.setAttribute("opacity", (0.35 + 0.35 * p).toFixed(2)); n.setAttribute("r", (7 + 3 * p).toFixed(1)); }
        });
        // the sleigh crosses the sky in the quiet part of the loop, after the tall elf has gone
        if (Wn.sleigh) {
          const s0 = 22, dur = 6.5, p = (t - s0) / dur;
          if (p < 0 || p > 1) Wn.sleigh.setAttribute("opacity", "0");
          else {
            const x = ww + 140 - p * (ww + 420), y = wh * 0.3 - Math.sin(p * Math.PI) * wh * 0.08;
            Wn.sleigh.setAttribute("opacity", "1"); Wn.sleigh.setAttribute("transform", `translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(-4) scale(.95)`);
            Wn.sleigh.querySelectorAll(".fl").forEach((l, k) => l.setAttribute("transform", `translate(0 ${(Math.sin(t * 14 + k) * 2).toFixed(1)})`));
            const arm = Wn.sleigh.querySelector(".wavea"); if (arm) arm.setAttribute("transform", `rotate(${(Math.sin(t * 9) * 18).toFixed(1)} 50 -30)`);
            Wn.sleigh.querySelector(".waveh").setAttribute("transform", `rotate(${(Math.sin(t * 9) * 18).toFixed(1)} 50 -30)`);
            Wn.sleigh.querySelectorAll(".dust").forEach((d) => { const k = +d.dataset.k; const age = ((t * 2 + k / 18) % 1); d.setAttribute("cx", (110 + age * 120 + (k % 3) * 6).toFixed(1)); d.setAttribute("cy", (-6 + Math.sin(k * 2.1) * 8 + age * 10).toFixed(1)); d.setAttribute("opacity", ((1 - age) * 0.9).toFixed(2)); });
          }
        }
      });
    }
    return { at };
  }

  /** Plays a built room live until the element leaves the page */
  function play(svg, scene) {
    const start = performance.now() - 4000;
    const tick = (now) => { if (!svg.isConnected) return; scene.at((now - start) / 1000); requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
  }

  G.BenflixXmas = { LOOP, LAST_DAY, season, sleeps, variant, countdown, build, play, pose, LAYOUTS };
})(typeof window !== "undefined" ? window : globalThis);
