/* Benflix web: holiday marks for the new navigation (Ben 2026-11-17: "LOVE all 3", so a holiday dresses the rail,
   each door's item and Home together). The same kit as the app's ui/theme/HolidayNav.kt, one entry per holiday.
   The rail (nav.js), Home's picks (spot.js) and Home's countdown card (start.js) read the one that's on, so
   holiday threads only ever touch their own entry here.

   Each entry:
     key           short name, the same as the app's
     on()          true while the holiday's look is on (its own dates and Settings switch)
     kids          false when Kids profiles don't get it
     rail          [top, bottom] colours of the rail
     lights        start-up: the rail icons light up in these colours, taken in turn
     icons         { key: SVG } the main rail touch (Ben 2026-11-17: "only rail icons ... we can color them"): the
                   holiday's own icons, in its own colours, in place of the usual rail and phone-tab icons. viewBox
                   0 0 24 24 (use currentColor for the door's own colour). Keys: home, search, requests, more and the
                   doors (movies, shows, live, games, music, books). Leave out a key to keep its usual icon. Nothing
                   else sits beside the names.
     mark          optional SVG hanging beside the rail icon you're on (phones: the chosen tab), viewBox 0 0 12 18
     topTrim       SVG along the top edge of Home's picture (viewBox 0 0 1200 60, stretched)
     bottomTrim    SVG along the bottom of Home (viewBox 0 0 1200 40, stretched)
     countdown()   the first Today card's words ("8 days to Christmas"), or "" when there's none
     pickWhy       the reason line on the holiday's first carousel pick
     pickTags / pickWords   how to find that pick: Jellyfin tags, and whole words in the title
   The trims below are placeholder (beads in the holiday's colours) and there are no icons yet: each holiday thread
   draws its own.
   Any art may move (SVG <animate> or a <style> with @keyframes inside it), lite mode too (Ben's rule). In the app,
   art gets the seconds since it showed and the entry sets moving = true.
   Everything sits in one closure: the web scripts share one scope, so nothing here is a top-level name. */

(() => {
  const now = () => new Date();
  const cd = (obj) => () => { try { return (obj() && obj().countdown && obj().countdown(now())) || ""; } catch (e) { return ""; } };
  const trim = (colors, hanging) => {
    let out = hanging ? '<path d="M0 22 Q300 30 600 22 T1200 22" stroke="#fff" stroke-opacity=".25" fill="none" stroke-width="1.5"/>' : "";
    for (let i = 0; i < 26; i++) {
      const x = 23 + i * 46;
      const y = hanging ? 30 + Math.sin(i * 0.9) * 4 : 30;
      out += `<circle cx="${x}" cy="${y}" r="6" fill="${colors[i % colors.length]}" fill-opacity="${hanging ? 1 : 0.7}"/>`;
    }
    return out;
  };
  const make = (key, on, kids, rail, lights, pickWhy, extra) => Object.assign({
    key, on, kids, rail, lights, pickWhy,
    mark: "",
    topTrim: trim(lights, true),
    bottomTrim: trim(lights, false),
    countdown: () => "",
    pickTags: [],
    pickWords: [],
    icons: {},
  }, extra || {});
  const is = (name) => () => !!(B[name] && B[name]());

  /* Easter's drawings, made from the approved Easter kit (easter.js loads first) */
  function easterNav() {
    const base = { countdown: cd(() => B.ea), pickTags: ["easter", "easter bunny"], pickWords: ["Easter", "Peter Rabbit"] };
    const K = window.BenflixEaster && window.BenflixEaster.kit;
    if (!K) return base;
    const EGGS = [["#FFB8D2", "#7B5BD6", "zig"], ["#BFE6FF", "#3F7FD0", "dots"], ["#FFF1A6", "#F08A24", "stripes"], ["#C9F2C0", "#E8508A", "flowers"], ["#E3D5FF", "#4AA8C8", "waves"], ["#FFD9B8", "#5AAE52", "checks"]];
    // svg.i draws lines with currentColor and no fill: put the drawing's own defaults back
    const plain = (s) => `<g fill="#000" stroke="none" stroke-width="1" stroke-linecap="butt" stroke-linejoin="miter">${s}</g>`;
    // the part of each charm (on its 240 x 220 door) that is the thing itself, without the pin and ribbon
    const CROP = { movies: "144 34 84 102", shows: "140 28 92 106", live: "144 42 84 100", games: "128 18 116 104", music: "136 4 104 140", books: "146 10 80 130" };
    const icons = {};
    Object.keys(CROP).forEach((k) => { icons[k] = plain(`<svg x="0" y="0" width="24" height="24" viewBox="${CROP[k]}">${K.CHARM[k]()}</svg>`); });
    const sway = "<style>.eaSw{transform-box:fill-box;transform-origin:50% 0;animation:eaSw 4.5s ease-in-out infinite}@keyframes eaSw{0%,100%{transform:rotate(-6deg)}50%{transform:rotate(6deg)}}</style>";
    // A painted egg on a pink ribbon, hanging from a pearl pin (viewBox 0 0 12 18)
    const mark = plain(`${sway}<g class="eaSw"><path d="M6 1.4V5.6" stroke="#C25A86" stroke-width=".9"/>${K.egg(6, 11.4, 4.6, 6.2, "#FFB8D2", "#7B5BD6", "zig", { nosh: true })}</g><circle cx="6" cy="1.4" r="1.4" fill="#F4F1EA" stroke="#B8B0A0" stroke-width=".3"/>`);
    // Wide and cut to the screen (slice), so the eggs keep their shape at any width: 2 units = 1 pixel. Ben
    // 2026-11-17: the greeting now sits top left on the clock's line, so the ribbon stays in a thin band along the
    // very top with its eggs ending above the greeting's letters, and the grass stays inside its own box so it
    // never covers the news cards. Phones put the greeting at the top of the picture: eggs there hang only on the
    // right, past the words
    const wide = (h, art) => (cls) => `<svg class="${cls || ""}" viewBox="0 0 4800 ${h}" preserveAspectRatio="xMinYMin slice" style="height:${h / 2}px" aria-hidden="true">${plain(art)}</svg>`;
    let top = sway.replace("</style>", "@media (max-width:700px){.eaL{display:none}}</style>"), rope = "M0 6";
    const step = 300, pins = [];
    for (let x = 0; x < 4800; x += step) { rope += `Q${x + step / 2} ${6 + 24} ${x + step} 6`; pins.push(x); }
    top += `<path d="${rope}" stroke="#C25A86" stroke-width="9" fill="none"/><path d="${rope}" stroke="#F28DB2" stroke-width="6" fill="none"/><path d="${rope}" stroke="#FFD0E2" stroke-width="1.6" fill="none" transform="translate(0 -1.5)"/>`;
    pins.forEach((x, i) => {
      const m = x + step / 2, e = EGGS[i % 6];
      top += `<g class="eaSw${m < 560 ? " eaL" : ""}" style="animation-delay:${(-i * 0.7).toFixed(1)}s"><path d="M${m} 18V25" stroke="#C25A86" stroke-width="2.4"/>${K.egg(m, 39, 11, 15, e[0], e[1], e[2])}</g>`;
      top += `<circle cx="${x}" cy="6" r="5" fill="#F4F1EA" stroke="#B8B0A0"/><circle cx="${x - 1.6}" cy="4.4" r="1.6" fill="#fff"/>`;
    });
    // Spring grass with tulips and eggs half hidden in it
    let seed = 3;
    const rnd = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280; };
    const grass = (yb, h) => { let s = ""; for (let x = 0; x < 4800; x += 5) { const hh = h * (0.55 + rnd() * 0.6), lean = (rnd() - 0.5) * 18; s += `<path d="M${x} ${yb}q${(lean * 0.3).toFixed(1)} ${(-hh * 0.6).toFixed(1)} ${lean.toFixed(1)} ${(-hh).toFixed(1)}" stroke="${["#3E8A45", "#5AAE52", "#7CCB6A", "#4FA052"][(x / 5) % 4]}" stroke-width="2.6" fill="none" stroke-linecap="round"/>`; } return s; };
    let bottom = `<rect x="0" y="58" width="4800" height="14" fill="#24502F"/>` + grass(72, 40);
    for (let i = 0, x = 60; x < 4800; i++, x += 150) {
      if (i % 2) { const e = EGGS[i % 6]; bottom += K.egg(x, 46, 17, 23, e[0], e[1], e[2]); }
      else bottom += `<path d="M${x} 32V72" stroke="#3E8A45" stroke-width="3.4"/>` + K.tulip(x, 36, ["#FF6F91", "#FFD23F", "#FFFFFF", "#C9B2F5"][(i / 2) % 4], (i % 5 - 2) * 4, 1.2);
    }
    bottom += grass(72, 22);
    return Object.assign(base, { icons, mark, topTrim: wide(56, top), bottomTrim: wide(72, bottom) });
  }
  // New Year's (Ben 2026-11-17, "Midnight Fringe"): the rail and phone-tab icons are the New Year party charms,
  // a gold and silver foil fringe across the top of Home, confetti along the bottom. At midnight the fringe flashes
  // and fresh confetti falls for half a minute; from New Year's Day the pick is a fresh-start film.
  const NY = (() => {
    const G = "#E8B84A", GL = "#F7D98A", GD = "#B98F2E", GE = "#7A5A16", S = "#C9D1DE", SD = "#8E97A8", K = "#1B1B1F";
    const f = (n) => +n.toFixed(2);
    const star = (cx, cy, o, i) => {
      const p = [];
      for (let k = 0; k < 10; k++) { const r = k % 2 ? i : o, a = -Math.PI / 2 + k * Math.PI / 5; p.push([f(cx + r * Math.cos(a)), f(cy + r * Math.sin(a))]); }
      let s = `<path d="M${p.map((q) => q.join(" ")).join("L")}Z" fill="${G}"/>`;
      for (let k = 0; k < 10; k += 2) {
        s += `<path d="M${cx} ${cy}L${p[k].join(" ")}L${p[(k + 1) % 10].join(" ")}Z" fill="${GD}"/>`;
        s += `<path d="M${cx} ${cy}L${p[k].join(" ")}L${p[(k + 9) % 10].join(" ")}Z" fill="${GL}" fill-opacity=".7"/>`;
      }
      return s + `<path d="M${p.map((q) => q.join(" ")).join("L")}Z" fill="none" stroke="${GE}" stroke-width=".55"/>`;
    };
    const spark = (x, y, r, dur) => `<path d="M${x} ${y - r}Q${x} ${y} ${x + r} ${y}Q${x} ${y} ${x} ${y + r}Q${x} ${y} ${x - r} ${y}Q${x} ${y} ${x} ${y - r}Z" fill="#fff"><animate attributeName="opacity" values="1;.2;1" dur="${dur}s" repeatCount="indefinite"/></path>`;
    const balloon = (cx, cy, rx, ry, c, e) => `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="${c}" stroke="${e}" stroke-width=".5"/><ellipse cx="${f(cx - rx * .37)}" cy="${f(cy - ry * .37)}" rx="${f(rx * .25)}" ry="${f(ry * .31)}" fill="#fff" fill-opacity=".55"/><path d="M${f(cx - .9)} ${f(cy + ry + .9)}L${f(cx + .9)} ${f(cy + ry + .9)}L${cx} ${f(cy + ry - .2)}Z" fill="${c}"/>`;
    const g = (inner) => `<g stroke="none" stroke-linecap="round">${inner}</g>`;
    // the watch face's ticks
    let ticks = "";
    for (let h = 0; h < 12; h++) {
      const a = h * Math.PI / 6, r1 = h % 3 ? 5.9 : 5.3;
      ticks += `<line x1="${f(12 + r1 * Math.sin(a))}" y1="${f(14 - r1 * Math.cos(a))}" x2="${f(12 + 6.6 * Math.sin(a))}" y2="${f(14 - 6.6 * Math.cos(a))}" stroke="${K}" stroke-width="${h % 3 ? .4 : .7}"/>`;
    }
    // the mirror ball's tiles
    const TL = ["#D7DDE8", "#AEB7C6", "#6E788A", "#E9EEF5", "#9CC3E6", "#BFC7D4"];
    let tiles = "";
    for (let row = 0, y = 5; y < 23; y += 1.85, row++) for (let col = 0, x = 3 + (row % 2) * .9; x < 21; x += 1.85, col++) tiles += `<rect x="${f(x)}" y="${f(y)}" width="1.6" height="1.6" fill="${TL[(row * 7 + col * 3 + row * col) % 6]}"/>`;
    // the rosette's pleats
    let pleats = "";
    for (let k = 0; k < 18; k++) {
      const a0 = 2 * Math.PI * k / 18 - Math.PI / 2, a1 = 2 * Math.PI * (k + 1) / 18 - Math.PI / 2, am = (a0 + a1) / 2;
      pleats += `<path d="M12 12L${f(12 + 11.4 * Math.cos(a0))} ${f(12 + 11.4 * Math.sin(a0))}L${f(12 + 10.5 * Math.cos(am))} ${f(12 + 10.5 * Math.sin(am))}L${f(12 + 11.4 * Math.cos(a1))} ${f(12 + 11.4 * Math.sin(a1))}Z" fill="${k % 2 ? K : G}"${k % 2 ? ` stroke="${GD}" stroke-width=".35"` : ""}/>`;
    }
    const bell = (cx, cy, tilt) => `<g transform="translate(${cx} ${cy}) rotate(${tilt} 0 -5)"><g><animateTransform attributeName="transform" type="rotate" values="-6 0 -5;6 0 -5;-6 0 -5" dur="2.6s" repeatCount="indefinite"/><path d="M-1.3 -5C-3.8 -4.6 -3.8 -1 -4 1.6L-5.2 4.2L5.2 4.2L4 1.6C3.8 -1 3.8 -4.6 1.3 -5Z" fill="${G}" stroke="${GE}" stroke-width=".45"/><line x1="-2.4" y1="-3" x2="-2.9" y2="2.4" stroke="${GL}" stroke-width=".8"/><circle cx="0" cy="5.1" r="1.1" fill="${GD}"/></g></g>`;
    const icons = {
      home: g(star(12, 13, 10.5, 4.4) + spark(20.5, 3.8, 2.6, 2.8)),
      books: g(`<g><animateTransform attributeName="transform" type="rotate" values="-5 12 1.5;5 12 1.5;-5 12 1.5" dur="4.8s" repeatCount="indefinite"/><line x1="12" y1="1.5" x2="12" y2="7" stroke="${G}" stroke-width=".9"/><circle cx="12" cy="1.5" r="1.5" fill="${GL}" stroke="${GE}" stroke-width=".4"/>${star(12, 15, 8.6, 3.7)}</g>`),
      movies: g(`<g stroke="#EDEDED" stroke-width=".45"><line x1="7.5" y1="13.6" x2="12.5" y2="22.5"/><line x1="16.8" y1="12.2" x2="12.5" y2="22.5"/><line x1="12.5" y1="17" x2="12.5" y2="22.5"/></g><g><animateTransform attributeName="transform" type="translate" values="0 0;0 -.7;0 0" dur="3.6s" repeatCount="indefinite"/>${balloon(7.5, 7.6, 5.2, 6, G, GE)}</g><g><animateTransform attributeName="transform" type="translate" values="0 -.6;0 .2;0 -.6" dur="3.2s" repeatCount="indefinite"/>${balloon(16.8, 6.6, 4.7, 5.6, S, SD)}</g>${star(12.5, 14.2, 3.9, 1.7)}<circle cx="12.5" cy="22.5" r="1.3" fill="${G}"/>`),
      shows: g(`<line x1="12" y1="0" x2="12" y2="3" stroke="${G}" stroke-width=".9"/><circle cx="12" cy="2.4" r="1.5" fill="none" stroke="${G}" stroke-width=".7"/><rect x="10.6" y="3.4" width="2.8" height="2.2" fill="${G}"/><circle cx="12" cy="14" r="9.4" fill="${GD}"/><circle cx="12" cy="14" r="8.8" fill="${G}"/><circle cx="12" cy="14" r="7.2" fill="#FBF4E2"/>${ticks}<line x1="12" y1="14" x2="11.86" y2="10" stroke="${K}" stroke-width="1.1"/><line x1="12" y1="14" x2="11.4" y2="8.2" stroke="${K}" stroke-width=".7"/><line x1="12" y1="14" x2="12" y2="8" stroke="#D7263D" stroke-width=".35"><animateTransform attributeName="transform" type="rotate" from="0 12 14" to="360 12 14" dur="60s" repeatCount="indefinite"/></line><circle cx="12" cy="14" r=".7" fill="${K}"/>`),
      live: g(`<line x1="12" y1="0" x2="12" y2="3.2" stroke="${G}" stroke-width=".7"/><rect x="10.5" y="3" width="3" height="2" fill="${G}"/><clipPath id="nyBall"><circle cx="12" cy="14" r="9"/></clipPath><circle cx="12" cy="14" r="9" fill="${SD}"/><g clip-path="url(#nyBall)">${tiles}</g><circle cx="12" cy="14" r="9" fill="none" stroke="${K}" stroke-opacity=".6" stroke-width=".5"/>${spark(8.2, 10.2, 2.6, 2.4)}${spark(16.4, 17.4, 1.8, 3.1)}`),
      games: g(`<g><animateTransform attributeName="transform" type="rotate" values="-4 12 12;4 12 12;-4 12 12" dur="7.8s" repeatCount="indefinite"/>${pleats}</g><circle cx="12" cy="12" r="5" fill="${K}"/><circle cx="12" cy="12" r="4.4" fill="none" stroke="${G}" stroke-width=".9"/>${star(12, 12.2, 3, 1.25)}`),
      music: g(`<line x1="12" y1="4.5" x2="7.6" y2="9" stroke="${G}" stroke-width=".8"/><line x1="12" y1="4.5" x2="16.4" y2="9" stroke="${G}" stroke-width=".8"/>${bell(7.3, 14.6, -14)}${bell(16.7, 14.6, 14)}<ellipse cx="9.3" cy="3.3" rx="2.5" ry="1.7" fill="none" stroke="${G}" stroke-width=".8"/><ellipse cx="14.7" cy="3.3" rx="2.5" ry="1.7" fill="none" stroke="${G}" stroke-width=".8"/><circle cx="12" cy="3.3" r="1.3" fill="${G}"/>`),
    };
    // steady 0..1 numbers so the fringe and confetti look the same every time
    const h = (i, s) => { let x = Math.imul(i, 374761393) + Math.imul(s, 668265263); x = Math.imul(x ^ (x >>> 13), 1274126177); return ((x ^ (x >>> 16)) & 0xffff) / 65535; };
    const bursting = () => { const d = new Date(); return d.getMonth() === 0 && d.getDate() === 1 && d.getHours() === 0 && d.getMinutes() === 0 && d.getSeconds() < 30; };
    // Short (about 16px of the 44px strip) so it stays above the greeting, now on the clock's line
    const fringe = (burst) => {
      const cols = [G, GL, S, GD, "#fff"];
      let s = `<style>.nyf{animation:nyf 3.4s ease-in-out infinite}.nyf.b{animation-duration:.25s}@keyframes nyf{50%{opacity:.72}}</style><rect width="1200" height="4" fill="${G}"/>`;
      for (let i = 0; i < 380; i++) {
        const x = f(i * 3.16 + h(i, 1)), len = f(22 * (.6 + .4 * h(i, 2))), sw = f(Math.sin(i * .37) * 1.4);
        s += `<path class="nyf${burst ? " b" : ""}" style="animation-delay:-${f(h(i, 3) * 3.4)}s" d="M${x} 2Q${f(x + sw * .3)} ${f(len / 2)} ${f(x + sw)} ${len}L${f(x + sw + 2.3)} ${f(len - .6)}Q${f(x + 2.3)} ${f(len / 2)} ${f(x + 2.3)} 2Z" fill="${cols[Math.min(4, Math.floor(h(i, 3) * 5))]}"/>`;
      }
      return s;
    };
    const confetti = (burst) => {
      const cols = [G, S, GL, "#7FA6FF", "#fff"];
      const piece = (i, x, y) => (i % 4 === 0
        ? `<circle cx="${x}" cy="${y}" r="${f(2 + h(i, 6) * 1.2)}" fill="${cols[i % 5]}"/>`
        : `<rect x="${f(x - 4)}" y="${f(y - 1.6)}" width="${f(6.5 + h(i, 8) * 3)}" height="3.2" fill="${cols[i % 5]}" transform="rotate(${f(h(i, 7) * 180)} ${x} ${y})"/>`);
      let s = "";
      for (let i = 0; i < 170; i++) s += piece(i, f(h(i, 4) * 1200), f(40 * (.35 + .6 * h(i, 5))));
      if (burst) for (let i = 0; i < 90; i++) s += `<g><animateTransform attributeName="transform" type="translate" values="0 -40;0 40" dur="${f(1.6 + h(i, 11))}s" begin="-${f(h(i, 10) * 2)}s" repeatCount="15"/>${piece(i + 1, f(h(i, 9) * 1200), 0)}</g>`;
      return s;
    };
    const eve = ["New Year", "Harry Met Sally", "The Apartment", "Trading Places", "Hudsucker"];
    const fresh = ["About Time", "Groundhog Day", "Walter Mitty", "Yes Man", "Julie & Julia", "Soul", "Inside Out"];
    // Getters, so the midnight burst and the New Year's Day pick are worked out each time Home draws
    return (entry) => Object.defineProperties(entry, {
      icons: { value: icons, enumerable: true },
      topTrim: { get: () => fringe(bursting()), enumerable: true },
      bottomTrim: { get: () => confetti(bursting()), enumerable: true },
      pickWords: { get: () => (new Date().getMonth() === 0 ? fresh : eve), enumerable: true },
      pickTags: { get: () => (new Date().getMonth() === 0 ? [] : ["new year's eve"]), enumerable: true },
    });
  })();

  /* ---------- 4th of July (approved 2026-11-17, sheet july4-nav-1-directions; the app's ui/theme/July4Nav.kt) ----------
     The rail and phone-tab icons in red, white and blue taken in turn, a twinkling gold star beside the one you're
     on, pleated fan bunting in swags across the top of Home, lit sparklers along the bottom, bigger fireworks over
     the bunting on the 4th from 9 PM, and "It's the 4th!" on the day. Kids too. */
  const july4 = () => {
    const RD = "#E0404F", RDK = "#9E1E2C", NV = "#24366E", CR = "#F4F0E8", CRS = "#CFC7B8";
    const f = (n) => Math.round(n * 10) / 10;
    const starD = (x, y, r, ri = 0.42) => { let d = ""; for (let k = 0; k < 10; k++) { const a = -Math.PI / 2 + k * Math.PI / 5, rr = k % 2 ? r * ri : r; d += (k ? "L" : "M") + f(x + Math.cos(a) * rr) + " " + f(y + Math.sin(a) * rr); } return d + "Z"; };
    const fan = (cx, top, r) => {
      let o = "";
      for (let i = 0; i < 10; i++) {
        const a0 = i * Math.PI / 10, a1 = (i + 1) * Math.PI / 10, am = (a0 + a1) / 2, c = i % 2 ? CR : RD;
        const P = (a) => `${f(cx + Math.cos(a) * r)} ${f(top + Math.sin(a) * r)}`;
        o += `<path d="M${cx} ${top}L${P(a0)}L${P(am)}Z" fill="${c}"/><path d="M${cx} ${top}L${P(am)}L${P(a1)}Z" fill="${c === RD ? RDK : CRS}"/>`;
      }
      const r2 = r * 0.45;
      o += `<path d="M${f(cx - r2)} ${top}A${f(r2)} ${f(r2)} 0 0 0 ${f(cx + r2)} ${top}Z" fill="${NV}" stroke="${CR}" stroke-width="${f(r * 0.07)}"/>`;
      [[-0.55, 0.35], [0, 0.6], [0.55, 0.35]].forEach(([dx, dy]) => { o += `<path d="${starD(cx + dx * r2 * 0.8, top + dy * r2 * 0.8, r2 * 0.24)}" fill="#fff"/>`; });
      return o + `<rect x="${f(cx - r - 1)}" y="${f(top - r * 0.08)}" width="${f(2 * r + 2)}" height="${f(r * 0.16)}" rx="${f(r * 0.08)}" fill="${NV}"/><circle cx="${cx}" cy="${top + 1}" r="2.6" fill="#E9C46A"/>`;
    };
    // Fan bunting in swags, gently swaying. Ben 2026-11-17: the greeting sits top left on the clock's line (clock
    // top right), so the bunting only hangs in the gap between them (phones: right of the greeting). Drawn for the
    // screen's own width, so the fans never stretch.
    const bunting = (W, narrow) => {
      const x0 = W * (narrow ? 0.66 : 0.34), x1 = W * (narrow ? 1 : 0.86), n = Math.max(1, Math.round((x1 - x0) / 170)), w = (x1 - x0) / n, top = 3;
      let o = "<style>.j4sw{transform-box:fill-box;transform-origin:50% 0;animation:j4sw 3.4s ease-in-out infinite}@keyframes j4sw{0%,100%{transform:rotate(-3deg)}50%{transform:rotate(3deg)}}@media (prefers-reduced-motion:reduce){.j4sw{animation:none}}</style>";
      for (let i = 0; i < n; i++) { const x = x0 + i * w; o += `<path d="M${f(x)} ${top}Q${f(x + w / 2)} ${top + 30} ${f(x + w)} ${top}" fill="none" stroke="${NV}" stroke-width="3"/><path d="M${f(x)} ${top}Q${f(x + w / 2)} ${top + 26} ${f(x + w)} ${top}" fill="none" stroke="${CR}" stroke-opacity=".6" stroke-width="1"/>`; }
      for (let i = 0; i <= n; i++) o += `<g class="j4sw" style="animation-delay:${-i * 0.45}s">${fan(x0 + i * w, top, i % 2 ? 27 : 33)}</g>`;
      return o;
    };
    // The big show on the night of the 4th: bursts blooming one after another over the right of the picture, below
    // the greeting and clock line and clear of the pick's words
    const fireworks = (W) => {
      let o = "<style>.j4fw{transform-box:fill-box;transform-origin:center;animation:j4fw 3.6s ease-out infinite}@keyframes j4fw{0%{transform:scale(.1);opacity:0}8%{opacity:1}45%{transform:scale(1);opacity:1}70%,100%{transform:scale(1.08);opacity:0}}</style>";
      ["#E0404F", "#FFFFFF", "#7FA0FF", "#FFD25A", "#E0404F"].forEach((c, i) => {
        const x = W * (0.52 + i * 0.1), y = 110 + ((i * 7) % 3) * 22, r = 34 + 8 * (i % 2);
        let rays = "";
        for (let k = 0; k < 16; k++) { const a = k * Math.PI / 8; rays += `<path d="M${f(x + Math.cos(a) * r * 0.3)} ${f(y + Math.sin(a) * r * 0.3)}L${f(x + Math.cos(a) * r)} ${f(y + Math.sin(a) * r)}" stroke="${c}" stroke-width="2.4" stroke-linecap="round"/><circle cx="${f(x + Math.cos(a) * r * 1.12)}" cy="${f(y + Math.sin(a) * r * 1.12)}" r="1.8" fill="${c}"/>`; }
        o += `<g class="j4fw" style="animation-delay:${-i * 0.75}s">${rays}</g>`;
      });
      return o;
    };
    const topTrim = (cls) => {
      const W = Math.max(320, Math.round(window.innerWidth || 1200)), narrow = W <= 760, d = now();
      const show = d.getMonth() === 6 && d.getDate() === 4 && d.getHours() >= 21;
      return `<svg class="${cls || ""}" viewBox="0 0 ${W} 44" preserveAspectRatio="xMinYMin slice" aria-hidden="true" style="overflow:visible">${bunting(W, narrow)}${show ? fireworks(W) : ""}</svg>`;
    };
    // Sparklers fizzing along the bottom of Home
    const sparklers = () => {
      let o = "<style>.j4sp{animation:j4sp .45s steps(2) infinite}.j4sp:nth-of-type(2n){animation-delay:-.22s}@keyframes j4sp{50%{opacity:.5}}@media (prefers-reduced-motion:reduce){.j4sp{animation:none}}</style>";
      const n = 16;
      for (let i = 0; i < n; i++) {
        const x = (i + 0.5) * 1200 / n, lean = (i % 2 ? 1 : -1) * 5, tx = x + lean, ty = 22; // low, so the lit tips show below the Today cards
        o += `<path d="M${f(x - lean * 0.2)} 40L${f(tx)} ${ty}" stroke="#8A8F9C" stroke-width="1.6"/><path d="M${f(x - lean * 0.2)} 40L${f(x - lean * 0.05)} 32" stroke="#5A4632" stroke-width="3"/><circle cx="${f(tx)}" cy="${ty}" r="7" fill="#FFD25A" fill-opacity=".22"/>`;
        let sp = "";
        for (let k = 0; k < 12; k++) { const a = k * Math.PI / 6 + i, r = k % 2 ? 4.5 : 7.5; sp += `<path d="M${f(tx)} ${ty}L${f(tx + Math.cos(a) * r)} ${f(ty + Math.sin(a) * r)}" stroke="${["#FFF3C4", "#FFD25A", "#fff"][k % 3]}" stroke-width="1"/>`; }
        o += `<g class="j4sp">${sp}</g><circle cx="${f(tx)}" cy="${ty}" r="2.6" fill="#FFF9E0"/>`;
      }
      return o;
    };
    // A twinkling gold star beside the icon you're on (drawn a bit bigger than its little box so it reads from the couch)
    const star = `<style>.rl-mark svg,.bt-mark svg{overflow:visible}</style><g><animateTransform attributeName="transform" type="rotate" values="-10 6 9;10 6 9;-10 6 9" dur="2.2s" repeatCount="indefinite"/><circle cx="6" cy="9" r="11" fill="#FFD25A" fill-opacity=".22"/><path d="${starD(6, 9, 11)}" fill="#FFD25A"/><path d="${starD(6, 9, 5.4)}" fill="#FFF3C4"/></g>`;
    // The usual icons in red, white and blue. While the rail lights up at start-up they follow the light-up colours.
    const COL = { home: "#FF5A68", movies: "#FFFFFF", shows: "#7FA0FF", live: "#FF5A68", games: "#FFFFFF", music: "#7FA0FF", books: "#FF5A68", search: "#FFFFFF", requests: "#7FA0FF", more: "#FFFFFF" };
    const NAME = { home: "home", search: "search", requests: "plus", more: "more" };
    const usual = (key) => {
      const n = NAME[key] || (B.DOORS && B.DOORS[key] && B.DOORS[key].icon) || key;
      let svg = "";
      try { svg = B.doorIcon ? B.doorIcon(n) : B.I(n); } catch (e) { svg = ""; }
      return String(svg).replace(/^<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "");
    };
    const icons = {};
    Object.keys(COL).forEach((k) => Object.defineProperty(icons, k, { enumerable: true, get: () =>
      `<style>.rl-i.dark .j4i,.rl-i.lit .j4i{stroke:currentColor!important;color:inherit!important}</style><g class="j4i" style="stroke:${COL[k]};color:${COL[k]}">${usual(k)}</g>` }));
    const h = make("july4", is("j4On"), true, ["#14214A", "#0B1430"], ["#E0404F", "#FFFFFF", "#7FA0FF"], "4th of July pick", {
      mark: star,
      bottomTrim: sparklers(),
      icons,
      countdown: () => { const d = now(); if (d.getMonth() === 6 && d.getDate() === 4 && is("j4On")()) return "It's the 4th!"; return cd(() => B.j4)(); },
      pickWords: ["Independence Day", "Sandlot", "Jaws", "1776", "Yankee Doodle", "National Treasure"],
    });
    // The bunting, with the big show on the 4th from 9 PM
    h.topTrim = topTrim;
    return h;
  };
  // Earth Day (approved by Ben 2026-11-17, sheet "Earth Day, put together"): an ocean blue rail whose icons turn into
  // Earth Day icons in sea-to-land colours (grey like the others while the rail is still dark at start-up), clouds
  // and birds along the top of Home, and a short meadow with flowers and a bee along the bottom. The app's twin is
  // ui/theme/EarthDayNav.kt.
  const ed = (() => {
    const DIM = "<style>.rl-i.dark .edn-o{stroke:currentColor}</style>";
    const h = (i) => { const x = Math.sin(i * 12.9898 + 78.233) * 43758.547; return x - Math.floor(x); };
    const f = (n) => Math.round(n * 10) / 10;
    const cloud = (x, y, s) => '<g transform="translate(' + f(x) + " " + f(y) + ") scale(" + f(s * 100) / 100 + ')">'
      + '<g fill="#C9D9E6"><circle cx="2" cy="16" r="10"/><circle cx="20" cy="17" r="11"/><circle cx="40" cy="17" r="10"/><circle cx="56" cy="15" r="8"/></g>'
      + '<g fill="#F2F7FB"><circle cx="2" cy="12" r="10"/><circle cx="18" cy="4" r="15"/><circle cx="36" cy="6" r="13"/><circle cx="20" cy="14" r="10"/><circle cx="40" cy="13" r="10"/><circle cx="55" cy="12" r="8"/></g></g>';
    let clouds = "";
    for (let i = 0; i < 3; i++) clouds += cloud(20 + i * 195 + h(i + 40) * 40, 9 + h(i + 80) * 3, 0.62 + h(i) * 0.12);
    let birds = "";
    [[0, 0], [-16, -5], [-16, 5], [-32, -10], [-32, 10]].forEach(([x, y], k) => {
      birds += '<path class="edn-flap" style="animation-delay:-' + f(k * 0.17) + 's" d="M' + (x - 6) + " " + y + "q3 -4 6 0q3 -4 6 0" + '" stroke="#E8F3FF" stroke-width="1.8" fill="none" stroke-linecap="round"/>';
    });
    const top = '<g><style>'
      + ".edn-drift{animation:edn-drift 40s ease-in-out infinite alternate}@keyframes edn-drift{to{transform:translateX(-20px)}}"
      + ".edn-flock{animation:edn-flock 20s linear infinite}@keyframes edn-flock{0%{transform:translate(0,42px);opacity:0}5%{opacity:1}60%{transform:translate(600px,32px);opacity:1}61%,100%{transform:translate(600px,32px);opacity:0}}"
      + ".edn-flap{transform-box:fill-box;transform-origin:50% 100%;animation:edn-flap .6s ease-in-out infinite}@keyframes edn-flap{50%{transform:scaleY(-.6)}}"
      + "@media (prefers-reduced-motion:reduce){.edn-drift,.edn-flock,.edn-flap{animation:none}}</style>"
      + '<g class="edn-drift">' + clouds + '</g><g class="edn-flock">' + birds + "</g></g>";
    const grassC = ["#2F7A34", "#3E9440", "#4FA846", "#6BC35A"];
    let grass = ["", "", ""];
    for (let i = 0, x = -4; x < 1206; i++, x += 4) {
      const tall = 7 + h(i) * 10, b = h(i + 500) * 6 - 3;
      grass[i % 3] += '<path d="M' + f(x) + " 41Q" + f(x + b * 0.3) + " " + f(40 - tall * 0.6) + " " + f(x + b) + " " + f(40 - tall) + "Q" + f(x + 2 + b * 0.3) + " " + f(40 - tall * 0.5) + " " + f(x + 4) + ' 41Z" fill="' + grassC[Math.floor(h(i + 900) * 4)] + '"/>';
    }
    let flowers = "";
    for (let i = 0, x = 18; x < 1200; i++, x += 38 + h(i + 1700) * 24) {
      const y = 40 - (14 + h(i + 1300) * 5), k = i % 4;
      let head = "";
      if (k === 0) { for (let p = 0; p < 10; p++) head += '<ellipse cx="' + f(x) + '" cy="' + f(y - 3.4) + '" rx="1.2" ry="3" fill="#fff" transform="rotate(' + p * 36 + " " + f(x) + " " + f(y) + ')"/>'; head += '<circle cx="' + f(x) + '" cy="' + f(y) + '" r="2" fill="#F4C542"/>'; }
      else if (k === 1) head = '<circle cx="' + f(x) + '" cy="' + f(y) + '" r="4.6" fill="#E8453C"/><circle cx="' + f(x - 1) + '" cy="' + f(y - 1.4) + '" r="2.8" fill="#FF6A55"/><circle cx="' + f(x) + '" cy="' + f(y) + '" r="1.5" fill="#2A1A1A"/>';
      else if (k === 2) { for (let p = 0; p < 8; p++) head += '<path d="M' + f(x) + " " + f(y) + "l" + f(Math.cos(p * 0.785) * 4.6) + " " + f(Math.sin(p * 0.785) * 4.6) + '" stroke="#4C7BE0" stroke-width="2" stroke-linecap="round"/>'; head += '<circle cx="' + f(x) + '" cy="' + f(y) + '" r="1.5" fill="#2B4FA8"/>'; }
      else { for (let p = 0; p < 5; p++) head += '<circle cx="' + f(x + Math.cos(p * 1.256) * 2.3) + '" cy="' + f(y + Math.sin(p * 1.256) * 2.3) + '" r="2.1" fill="#FFD23F"/>'; head += '<circle cx="' + f(x) + '" cy="' + f(y) + '" r="1.2" fill="#E89A1A"/>'; }
      flowers += '<path d="M' + f(x) + " 41L" + f(x) + " " + f(y) + '" stroke="#2F7A34" stroke-width="1.3"/>' + head;
    }
    const bee = '<g class="edn-bee"><ellipse class="edn-wing" cx="-0.5" cy="-4.4" rx="3" ry="2.2" fill="#E8F6FF" opacity=".85"/><ellipse cx="0" cy="0" rx="4.4" ry="3.1" fill="#F4C542"/><path d="M-1.2 -3v6M1.6 -2.8v5.6" stroke="#222" stroke-width="1.3"/></g>';
    const bottom = '<g id="ednMeadow"><style>'
      + ".edn-sw{transform-box:fill-box;transform-origin:50% 100%;animation:edn-sw 4s ease-in-out infinite}.edn-sw.b{animation-delay:-1.3s}.edn-sw.c{animation-delay:-2.6s;animation-duration:5s}@keyframes edn-sw{50%{transform:skewX(-5deg)}}"
      + ".edn-bee{animation:edn-bee 24s ease-in-out infinite}@keyframes edn-bee{0%{transform:translate(40px,22px)}25%{transform:translate(330px,19px)}50%{transform:translate(620px,23px)}75%{transform:translate(900px,19px)}100%{transform:translate(1180px,22px)}}"
      + ".edn-wing{transform-box:fill-box;transform-origin:50% 100%;animation:edn-flap .15s linear infinite}@keyframes edn-flap{50%{transform:scaleY(-.6)}}"
      + "@media (prefers-reduced-motion:reduce){.edn-sw,.edn-bee,.edn-wing{animation:none}}</style>"
      + '<g class="edn-sw a">' + grass[0] + "</g>" + flowers + '<g class="edn-sw b">' + grass[1] + '</g><g class="edn-sw c">' + grass[2] + "</g>" + bee + "</g>";
    // Whole <svg>s (B.holidaySvg takes a function), so nothing is stretched or cut on any screen width (Ben
    // 2026-11-17: trims mustn't crowd the greeting or get cut off):
    // - top: the clouds and birds keep to the middle of the top edge (46% to 86% across), clear of the greeting on
    //   the left and the clock on the right
    // - bottom: the meadow is drawn at one fixed size that fits its 36px and repeated along the width, so flowers and the
    //   bee are never cropped
    const topSvg = (cls) => `<svg class="${cls || ""}" aria-hidden="true"><svg x="46%" width="40%" height="100%" viewBox="0 3 640 41" preserveAspectRatio="xMidYMin meet">${top}</svg></svg>`;
    const bottomSvg = (cls) => `<svg class="${cls || ""}" aria-hidden="true"><g transform="translate(0 -16) scale(1.3)">${bottom}<use href="#ednMeadow" x="1200"/><use href="#ednMeadow" x="2400"/><use href="#ednMeadow" x="3600"/></g></svg>`;
    return {
      top: topSvg,
      bottom: bottomSvg,
      icons: {
        home: DIM + '<path d="M3 11l9-7 9 7" class="edn-o" stroke="#A8DCF7"/><path d="M5 10v10h14V10" class="edn-o" stroke="#A8DCF7"/><path d="M12 20v-3.75" stroke="#5DB64E" stroke-width="1.6" fill="none"/><path d="M12 16.75c-3.25 0 -4.5 -2 -4.5 -4.25 2.75 0 4.5 1.5 4.5 4.25z" fill="#7CD36B" stroke="none"/><path d="M12 16.75c3.25 0 4.5 -2 4.5 -4.25 -2.75 0 -4.5 1.5 -4.5 4.25z" fill="#4FA846" stroke="none"/>',
        movies: DIM + '<rect x="3" y="9" width="18" height="11" rx="1.8" class="edn-o" stroke="#7FC8F2"/><path d="M3 9l16.5 -4.2 0.8 2.9L4 12" class="edn-o" stroke="#7FC8F2"/><path d="M8.5 7.6l2 2.6M13.5 6.3l2 2.6" class="edn-o" stroke="#7FC8F2"/><path d="M12 18.5v-3" stroke="#5DB64E" stroke-width="1.6" fill="none"/><path d="M12 15.9c-2.6 0 -3.6 -1.6 -3.6 -3.4 2.2 0 3.6 1.2 3.6 3.4z" fill="#7CD36B" stroke="none"/><path d="M12 15.9c2.6 0 3.6 -1.6 3.6 -3.4 -2.2 0 -3.6 1.2 -3.6 3.4z" fill="#4FA846" stroke="none"/>',
        shows: DIM + '<rect x="2.5" y="7" width="17" height="12" rx="2" class="edn-o" stroke="#56BDEB"/><path d="M7 22h8" class="edn-o" stroke="#56BDEB"/><g transform="translate(18.5 6.2) rotate(15)" stroke="none"><path d="M0 0C-1 -4 -5 -5 -5.5 -2C-5.6 0 -3 1 0 0z" fill="#F08A24"/><path d="M0 0C1 -4 5 -5 5.5 -2C5.6 0 3 1 0 0z" fill="#F08A24"/><path d="M0 0C-1 2.5 -4 3.4 -4 1.4z" fill="#E06A10"/><path d="M0 0C1 2.5 4 3.4 4 1.4z" fill="#E06A10"/><path d="M0 -2.5V2" stroke="#2A1A10" stroke-width="1"/></g>',
        live: DIM + '<path d="M7.8 7.8a6 6 0 0 0 0 8.4M16.2 7.8a6 6 0 0 1 0 8.4M4.6 4.6a10.5 10.5 0 0 0 0 14.8M19.4 4.6a10.5 10.5 0 0 1 0 14.8" class="edn-o" stroke="#7CD36B"/><circle cx="12" cy="12" r="3.2" fill="#2A74AE" stroke="none"/><path d="M10.4 10.6c1-.6 2 0 1.6 1-.5 1 .8 1.4 0.2 2.4-1 .2-2.2-.6-2.3-1.7z" fill="#7CD36B" stroke="none"/>',
        games: DIM + '<path d="M7.2 6.8h9.6c2.4 0 4 1.5 4.6 3.9l1 5c.5 2.6-1.1 4.2-3.1 4.2-1.4 0-2.3-.8-3-1.9l-.9-1.4H8.6l-.9 1.4c-.7 1.1-1.6 1.9-3 1.9-2 0-3.6-1.6-3.1-4.2l1-5c.6-2.4 2.2-3.9 4.6-3.9z" class="edn-o" stroke="#8FD86F"/><path d="M7 10.4v4M5 12.4h4" class="edn-o" stroke="#8FD86F"/><circle cx="16.4" cy="12.3" r="2.6" fill="#E0281E" stroke="none"/><path d="M16.4 9.8v5" stroke="#1a1a1a" stroke-width=".8"/><circle cx="15.4" cy="11.6" r=".55" fill="#1a1a1a" stroke="none"/><circle cx="17.5" cy="12.9" r=".55" fill="#1a1a1a" stroke="none"/><circle cx="16.4" cy="9.9" r=".9" fill="#1a1a1a" stroke="none"/>',
        music: DIM + '<path d="M9 18V5" class="edn-o" stroke="#B5E37A"/><circle cx="6" cy="18" r="3" class="edn-o" stroke="#B5E37A"/><path d="M9 5c4-1.6 8-1 11 2.2-3.6 1.6-7.8 1.6-11 0z" fill="#7CD36B" stroke="none"/><path d="M9.5 5.6c3 .3 6.2.9 9.6 1.6" stroke="#2F7A34" stroke-width=".8"/>',
        books: DIM + '<path d="M4 5a2 2 0 0 1 2 -2h13v16H6a2 2 0 0 0 -2 2z" class="edn-o" stroke="#E8F5A0"/><path d="M4 21V5" class="edn-o" stroke="#E8F5A0"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(0 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(36 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(72 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(108 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(144 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(180 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(216 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(252 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(288 12 10.4)"/><ellipse cx="12" cy="7.6" rx=".9" ry="2.3" fill="#fff" stroke="none" transform="rotate(324 12 10.4)"/><circle cx="12" cy="10.4" r="1.3" fill="#F4C542" stroke="none"/><path d="M12 11.7v5" stroke="#4FA846" stroke-width="1.1"/>',
      },
    };
  })();

  // ---------- Christmas art (approved "Classic big bulbs", sheet nav-13-christmas) ----------
  // Door icons are small full-colour drawings on a 48 grid (drawn at half size into the 24 box); a small gold
  // snowflake marks the icon you're on (no hanging bulb: it didn't attach to every kind of button); big glass
  // bulbs hang across the top of Home and soft snow lies along the bottom.
  const XMAS = (() => {
    const g = (art) => `<g transform="scale(.5)">${art}</g>`;
    const PAD = "M7.2 4.8h9.6c2.4 0 4 1.5 4.6 3.9l1.3 6.6c.5 2.6-1.1 4.4-3.1 4.4-1.4 0-2.3-.8-3-1.9l-1.1-1.8H8.5l-1.1 1.8c-.7 1.1-1.6 1.9-3 1.9-2 0-3.6-1.8-3.1-4.4l1.3-6.6c.6-2.4 2.2-3.9 4.6-3.9z";
    const BELL = "M0 0c-6.5 0-8.5 5.5-8.5 12l-3 6.5h23l-3-6.5c0-6.5-2-12-8.5-12z";
    const icons = {
      movies: g('<g transform="rotate(-12 8 16)"><rect x="6" y="8" width="36" height="7.5" rx="1.6" fill="#1b1b1f"/><path d="M12 8l5 7.5M20 8l5 7.5M28 8l5 7.5M36 8l5 7.5" stroke="#f4f4f4" stroke-width="2.8"/></g><rect x="7" y="18" width="34" height="24" rx="3" fill="#C8102E"/><rect x="7" y="35" width="34" height="7" rx="3" fill="#9E0B23"/><rect x="21" y="18" width="6" height="24" fill="#F2C14E"/><rect x="7" y="27" width="34" height="5" fill="#F2C14E"/><circle cx="24" cy="29.5" r="3.4" fill="#FFE08A"/>'),
      shows: g('<path d="M16 40l-3 5M32 40l3 5" stroke="#145234" stroke-width="3.2" stroke-linecap="round"/><rect x="5" y="14" width="38" height="26" rx="4" fill="#1F7A4D"/><rect x="9" y="18" width="30" height="18" rx="2" fill="#0D2A1B"/><path d="M24 20.5v13M18.4 23.7l11.2 6.6M18.4 30.3l11.2-6.6" stroke="#E8F4FF" stroke-width="1.9" stroke-linecap="round"/><path d="M27 15c2-9 10-12 15-8-3 1-5 4-5 8z" fill="#C8102E"/><rect x="25" y="12.5" width="15" height="5" rx="2.5" fill="#fff"/><circle cx="43" cy="7.5" r="3.2" fill="#fff"/>'),
      live: g('<path d="M24 19v8" stroke="#F2C14E" stroke-width="2.6"/><circle cx="24" cy="29" r="3.8" fill="#F2C14E"/><path d="M17 22a10 10 0 000 14M31 22a10 10 0 010 14M11.5 16.5a18 18 0 000 25M36.5 16.5a18 18 0 010 25" stroke="#2FA36B" stroke-width="3.4" fill="none" stroke-linecap="round"/><circle cx="9.3" cy="29" r="2.4" fill="#FF3B3B"/><circle cx="38.7" cy="29" r="2.4" fill="#3FA9FF"/><circle cx="14.4" cy="29" r="2" fill="#FFC93C"/><circle cx="33.6" cy="29" r="2" fill="#FF3B3B"/><path d="M24 2.5l2.7 5.7 6.2.7-4.6 4.2 1.3 6.1-5.6-3.1-5.6 3.1 1.3-6.1-4.6-4.2 6.2-.7z" fill="#FFD447"/>'),
      games: g(`<clipPath id="xmasPadClip"><path transform="scale(2)" d="${PAD}"/></clipPath><g clip-path="url(#xmasPadClip)"><rect width="48" height="48" fill="#fff"/><path d="M-8 30L22-8M2 36L36-8M14 44L48 2M26 50L58 10M38 54L62 26" stroke="#D7263D" stroke-width="4.5"/></g><path transform="scale(2)" d="${PAD}" fill="none" stroke="#8E0E22" stroke-width=".45"/><rect x="11.5" y="17" width="5" height="12" rx="1.2" fill="#1F7A4D"/><rect x="8" y="20.5" width="12" height="5" rx="1.2" fill="#1F7A4D"/><circle cx="33" cy="19.5" r="2.5" fill="#1F7A4D"/><circle cx="37" cy="24.8" r="2.5" fill="#1F7A4D"/>`),
      music: g([[16, -16, "#E9B53B"], [32, 16, "#F2C14E"]].map(([x, d, c]) => `<g transform="rotate(${d} ${x} 18) translate(${x} 12)"><path d="${BELL}" fill="${c}"/><path d="M-6 12c0-5 1.5-9 5-10" stroke="#FFF0BE" stroke-width="2" fill="none" stroke-linecap="round"/><circle cy="20.5" r="3" fill="#B8862A"/></g>`).join("") + '<path d="M24 9c-6-7-12-3-7 2zM24 9c6-7 12-3 7 2z" fill="#C8102E"/><path d="M24 9l-4 9M24 9l4 9" stroke="#A50C25" stroke-width="2.4" stroke-linecap="round"/><circle cx="24" cy="9.5" r="2.8" fill="#E3223F"/>'),
      books: g('<path d="M9 8a3 3 0 013-3h26v34H12a3 3 0 00-3 3z" fill="#1F7A4D"/><path d="M9 42a3 3 0 013-3h26v4H12z" fill="#F3EBDD"/><path d="M9 42V8" stroke="#145234" stroke-width="2"/><path d="M31 5v22l3.5-3 3.5 3V5z" fill="#C8102E"/><path d="M14 19c3-5 8-5 10-1-3.5 0-5 2.2-5 4.5-1.8-2-3.6-3-5-3.5zM26 19c-1-5 2.5-8 7-6.5-2.6 1.6-3 4-2.2 6-2-.6-3.6-.2-4.8.5z" fill="#3DBB6E"/><circle cx="23.5" cy="23" r="2.4" fill="#E3223F"/><circle cx="27" cy="24.5" r="2.4" fill="#C8102E"/><circle cx="24.6" cy="26.8" r="2.2" fill="#E3223F"/>'),
    };
    // a small gold snowflake (viewBox 0 0 12 18), twinkling softly
    let flake = '<circle cx="6" cy="12" r="6" fill="#FFD976" opacity=".25"/>';
    for (let i = 0; i < 6; i++) {
      const a = (i * 60 - 90) * Math.PI / 180, x = 6 + Math.cos(a) * 5.2, y = 12 + Math.sin(a) * 5.2;
      flake += `<path d="M6 12L${x.toFixed(2)} ${y.toFixed(2)}" stroke="#FFD976" stroke-width=".9" stroke-linecap="round"/>`;
    }
    const mark = `<g class="hol-tw">${flake}</g>`;
    // big glass bulbs on a dark wire: drawn for the screen's own width, so they never stretch
    // Christmas Day itself: the lights twinkle faster, all colours at once
    const xday = () => { const d = new Date(); return d.getMonth() === 11 && d.getDate() === 25; };
    const C9 = ["#FF3B3B", "#2FD06B", "#FFC93C", "#3FA9FF", "#FF8A2B"];
    const bulb = (x, y, c, k) => `<g transform="translate(${x.toFixed(1)} ${y.toFixed(1)})"><circle class="hol-tw" style="animation-delay:${((k * 0.37) % 2.4).toFixed(2)}s" cy="15" r="13" fill="${c}" opacity=".45" filter="url(#xmasGlow)"/><rect x="-2.6" width="5.2" height="6.4" rx="1.2" fill="#1c3524"/><path d="M0 5C-5.4 8-6.1 16 0 23.5 6.1 16 5.4 8 0 5z" fill="${c}"/><path d="M0 5C-5.4 8-6.1 16 0 23.5 6.1 16 5.4 8 0 5z" fill="url(#xmasGlass)"/><ellipse cx="-2" cy="11" rx="1" ry="2.6" fill="#fff" opacity=".55"/></g>`;
    const topTrim = (cls) => {
      const W = Math.max(600, Math.round(window.innerWidth || 1200)), H = 44, n = Math.max(1, Math.round(W / 150)), step = W / n, top = 3, sag = 10;
      let out = '<defs><filter id="xmasGlow" x="-1" y="-1" width="3" height="3"><feGaussianBlur stdDeviation="4"/></filter><radialGradient id="xmasGlass" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#fff" stop-opacity=".45"/><stop offset=".4" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".35"/></radialGradient></defs>';
      let wire = "";
      for (let i = 0; i < n; i++) wire += `M${(i * step).toFixed(1)} ${top}Q${((i + 0.5) * step).toFixed(1)} ${top + sag * 2} ${((i + 1) * step).toFixed(1)} ${top}`;
      out += `<path d="${wire}" stroke="#16251b" stroke-width="1.8" fill="none"/>`;
      let k = 0;
      for (let i = 0; i < n; i++) [0.17, 0.5, 0.83].forEach((t) => { out += bulb(i * step + step * t, top + sag * 4 * t * (1 - t) - 1, C9[k % 5], k); k++; });
      for (let i = 0; i <= n; i++) out += `<circle cx="${(i * step).toFixed(1)}" cy="${top}" r="2.2" fill="#2a3b2f"/>`;
      return `<svg class="${cls || ""}${xday() ? " xday" : ""}" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMinYMin slice" aria-hidden="true" style="overflow:visible;top:var(--bar)">${out}</svg>`;
    };
    // soft snow drifts (fine to stretch)
    const drift = (W, base, amp, seed) => {
      const pts = [];
      for (let i = 0; i <= 24; i++) { const x = W * i / 24; pts.push([x, base - amp * (0.55 + 0.45 * Math.sin(x / W * Math.PI * 3 + seed)) - amp * 0.35 * Math.sin(x / W * Math.PI * 9 + seed * 2)]); }
      let d = `M0 40L0 ${pts[0][1].toFixed(1)}`;
      for (let i = 1; i < pts.length; i++) { const [x0, y0] = pts[i - 1], [x1, y1] = pts[i]; d += `Q${(x0 + (x1 - x0) / 2).toFixed(1)} ${(Math.min(y0, y1) - amp * 0.25).toFixed(1)} ${x1.toFixed(1)} ${y1.toFixed(1)}`; }
      return d + `L${W} 40Z`;
    };
    let snow = '<defs><linearGradient id="xmasSnow" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset=".55" stop-color="#e9f2fb"/><stop offset="1" stop-color="#bcd3ea"/></linearGradient><linearGradient id="xmasSnow2" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#dfeaf6"/><stop offset="1" stop-color="#a9c2dc"/></linearGradient></defs>';
    snow += `<path d="${drift(1200, 22, 9, 1.3)}" fill="url(#xmasSnow2)" opacity=".9"/><path d="${drift(1200, 31, 8, 0.2)}" fill="url(#xmasSnow)"/>`;
    for (let i = 0; i < 30; i++) snow += `<circle cx="${((i * 137) % 997) / 997 * 1200}" cy="${(25 + ((i * 61) % 31) / 31 * 13).toFixed(1)}" r="1.2" fill="#fff" opacity=".8"/>`;
    return { icons, mark, topTrim, bottomTrim: snow };
  })();

  // Most important first: when two are on at once the first wins (Friday the 13th takes over any season look,
  // Earth Day comes after Easter week)

  // ---------- Valentine's Day: "Love tokens" (Ben 2026-11-17, sheet valentines-3-navigation; same as the app's
  // ui/theme/ValentineNav.kt). The six love tokens are the door icons; Home, Search, Requests and More stay their
  // usual shapes in rose; paper hearts on twine sway across the top of Home, a satin ribbon and bow along the bottom.
  const VAL = (() => {
    const ROSE = "#E8405E", BLUSH = "#F7A3BA", PALE = "#FFE6EA", DEEP = "#C2264A";
    const HP = "M12 21.3C6.2 17.1 2.5 13.6 2.5 9.3 2.5 6.3 4.8 4 7.6 4c1.8 0 3.4.9 4.4 2.4C13 4.9 14.6 4 16.4 4c2.8 0 5.1 2.3 5.1 5.3 0 4.3-3.7 7.8-9.5 12z";
    const heart = (x, y, s, fill, extra) => `<path d="${HP}" transform="translate(${x} ${y}) scale(${(s / 24).toFixed(4)})" fill="${fill}" ${extra || ""}/>`;
    const defs = `<defs><linearGradient id="vlGold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFE9A3"/><stop offset=".45" stop-color="#E8C15A"/><stop offset="1" stop-color="#9C6E1E"/></linearGradient><radialGradient id="vlFoil" cx=".3" cy=".25" r=".9"><stop offset="0" stop-color="#FFC2CC"/><stop offset=".35" stop-color="#F0506B"/><stop offset=".8" stop-color="#B11F3F"/><stop offset="1" stop-color="${ROSE}"/></radialGradient><linearGradient id="vlSatin" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FF7A90"/><stop offset=".35" stop-color="${ROSE}"/><stop offset="1" stop-color="#9E1838"/></linearGradient></defs>`;
    // the drawings are 32 units; the rail's icons are 24, drawn a little big so they read from the couch
    const big = (inner) => `${defs}<g transform="translate(12 12) scale(.82) translate(-16 -16)">${inner}</g>`;
    const T = {
      movies: `<g transform="rotate(-14 16 16)"><path d="M3 9h26v4.3a2.7 2.7 0 0 0 0 5.4V23H3v-4.3a2.7 2.7 0 0 0 0-5.4z" fill="${ROSE}" stroke="#FFD5DC" stroke-width=".8"/><path d="M21.5 10v12" stroke="#FFE3E8" stroke-width="1" stroke-dasharray="1.4 1.2"/>${heart(7.2, 10.6, 10, "#fff")}<path d="M23.5 13.5h3.5M23.5 16h3.5M23.5 18.5h3.5" stroke="#FFE3E8" stroke-width="1"/></g>`,
      shows: `<path d="M6 1.5 16 12 26 1.5" stroke="#E8C15A" stroke-width="1.4" stroke-dasharray="1.6 .9" fill="none"/>${heart(4, 9, 24, "url(#vlGold)", 'stroke="#8A5D12" stroke-width=".9"')}<path d="M16 15v13" stroke="#8A5D12" stroke-width=".7" opacity=".7"/>${heart(9.5, 14, 13, "none", 'stroke="#FFF2C4" stroke-width="1.4" opacity=".9"')}<circle cx="16" cy="12.6" r="1.6" fill="url(#vlGold)" stroke="#8A5D12" stroke-width=".6"/>`,
      live: `<g>${heart(3, 0, 26, "url(#vlFoil)", 'stroke="#8E0F2E" stroke-width=".6"')}<ellipse cx="10.5" cy="7.5" rx="2.6" ry="3.6" fill="#fff" opacity=".55" transform="rotate(-30 10.5 7.5)"/><path d="M15.2 22.6l.8 1.4.8-1.4z" fill="#B11F3F"/></g><path d="M16 24c-2 2 2 3 0 5s1.5 2 .5 3" stroke="#F4E9D8" stroke-width=".9" fill="none"/>`,
      games: `<g transform="rotate(10 16 16)"><rect x="7" y="2.5" width="18" height="26" rx="2.4" fill="#FFFDF7" stroke="#D9CFC2" stroke-width=".8"/>${heart(9.5, 9.5, 13, ROSE)}<path d="M9.4 9 L11 3.6 L12.6 9 M10 7.2 H12 M22.6 23.4 L21 28.8 L19.4 23.4 M22 25.2H20" stroke="${ROSE}" stroke-width="1.1" stroke-linecap="round" fill="none"/></g>`,
      music: `${heart(1, 1.5, 30, "#B5173A")}${heart(5, 5.5, 22, "none", 'stroke="#7A0E28" stroke-width=".8"')}${heart(8.5, 9, 15, "none", 'stroke="#7A0E28" stroke-width=".8"')}<path d="M7 9.5c1.5-2.5 4-3 6-1.5" stroke="#fff" stroke-width="1.1" opacity=".45" fill="none"/><circle cx="16" cy="15.5" r="4" fill="#FFE3E8"/><circle cx="16" cy="15.5" r="1" fill="#2a0a14"/>`,
      books: `<g transform="rotate(-8 16 16)"><rect x="2.5" y="7" width="27" height="18" rx="1.6" fill="#F6EBDD" stroke="#CDBBA5" stroke-width=".7"/><path d="M2.8 7.6 16 17.5 29.2 7.6" stroke="#CDBBA5" stroke-width=".9" fill="none"/><path d="M2.8 24.4 12.5 15M29.2 24.4 19.5 15" stroke="#E0D2BF" stroke-width=".7"/><path d="M16 12.2c2.7 0 5 1.8 5 4.6s-2.3 5-5 5-5.2-2-5-5 2.3-4.6 5-4.6z" fill="#B11F3F"/>${heart(12.6, 13.6, 6.8, "#7E0F2B")}</g>`,
    };
    const icons = {};
    Object.keys(T).forEach((k) => { icons[k] = big(T[k]); });
    const line = (d) => `<path d="${d}" fill="none" stroke="${ROSE}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>`;
    icons.home = line("M3 11l9-7 9 7M5 10v10h14V10M10 20v-5h4v5");
    icons.search = line("M17 11a6 6 0 1 1-12 0 6 6 0 0 1 12 0zM20 20l-4.5-4.5");
    icons.requests = line("M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6");
    icons.more = `<g fill="${ROSE}"><circle cx="5" cy="12" r="1.4"/><circle cx="12" cy="12" r="1.4"/><circle cx="19" cy="12" r="1.4"/></g>`;

    // Home's trims are drawn in real pixels (whole SVGs that crop instead of stretching), and kept shallow so the
    // greeting on the clock's line stays clear (Ben 2026-11-17): the top string hangs within the top ~20 px, the
    // ribbon sits low with its bow at the right-hand end.
    const cols = [ROSE, PALE, BLUSH, DEEP];
    const topTrim = (cls) => {
      let out = "";
      const span = 120, sag = 3;
      for (let i = 0; i < 24; i++) out += `${i ? "" : "M0 1 "}Q${i * span + span / 2} ${1 + sag * 2} ${(i + 1) * span} 1 `;
      out = `<path d="${out}" stroke="#C9A47A" stroke-width="1.4" fill="none"/>`;
      let k = 0;
      for (let i = 0; i < 24; i++) {
        for (let j = 1; j < 4; j++) {
          const f = j / 4, x = i * span + span * f, y = 1 + 2 * (1 - f) * f * sag * 2;
          const tilt = ((k * 37) % 13) - 6;
          out += `<g transform="translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(${tilt})"><g><path d="M0 0v3" stroke="#C9A47A" stroke-width="1"/>${heart(-8, 1.5, 16, cols[k % 4], 'stroke="rgba(0,0,0,.18)" stroke-width=".6"')}<animateTransform attributeName="transform" type="rotate" values="-5;5;-5" dur="${(4 + (k % 3) * 0.6).toFixed(1)}s" begin="-${(k * 0.4).toFixed(1)}s" repeatCount="indefinite"/></g></g>`;
          k++;
        }
      }
      return `<svg class="${cls || ""}" viewBox="0 0 2880 44" preserveAspectRatio="xMinYMin slice" aria-hidden="true" style="overflow:visible">${out}</svg>`;
    };
    const bottomTrim = (cls) => `<svg class="${cls || ""}" viewBox="0 0 2880 36" preserveAspectRatio="xMaxYMax slice" aria-hidden="true" style="overflow:visible">${defs}`
      + `<rect x="0" y="11" width="2880" height="7" fill="url(#vlSatin)"/><path d="M0 12.5h2880" stroke="#FFB0BD" stroke-width=".8" opacity=".6"/>`
      + `<g transform="translate(1100 16) scale(.5)"><path d="M-4 4l-22 30 10-2 4 8 16-30z" fill="${DEEP}"/><path d="M4 4l20 30-10-2-4 8-14-30z" fill="${DEEP}"/><path d="M0 0c-30-26-62-18-58 4s34 18 58-4z" fill="url(#vlSatin)" stroke="#8E0F2E" stroke-width="2.4"/><path d="M0 0c30-26 62-18 58 4s-34 18-58-4z" fill="url(#vlSatin)" stroke="#8E0F2E" stroke-width="2.4"/><rect x="-9" y="-9" width="18" height="18" rx="5" fill="${ROSE}" stroke="#8E0F2E" stroke-width="2.4"/></g>`.replace('translate(1100 16) scale(.5)', 'translate(2790 14) scale(.5)') + "</svg>";
    const countdown = () => {
      const v = B.val; const d = now();
      if (!v) return "";
      return v.variant(d) === "day" ? "Happy Valentine’s Day" : v.countdown(d);
    };
    return { icons, topTrim, bottomTrim, countdown };
  })();

  const ALL = [
    make("friday13", is("f13On"), true, ["#141414", "#0A0A0A"], ["#9BE15D", "#C9D0E0"], "Friday the 13th pick", { pickWords: ["Friday the 13th"], pickTags: ["slasher"] }),
    // Video Games Day (12 Sep, Kids too): arcade score screen on Home, full-colour pixel icons, blinking arrow by the
    // icon you're on (Ben 2026-11-17, themes/videogames-3-navigation.html + videogames-4-rail-icons.html). Same art
    // as the app's ui/theme/VideoGamesNav.kt.
    make("videogames", is("vgOn"), true, ["#1E1238", "#170E2C"], ["#3FE0FF", "#FFFFFF"], "Game night pick", (() => {
      const PIX = {
        home: ["....RR......", "...RRRR.CC..", "..RRRRRR.C..", ".RRRRRRRR...", "RRRRRRRRRR..", ".WWWWWWWW...", ".WBBWWWDDW..", ".WBBWWWDDW..", ".WWWWWWDDW..", ".WWWWWWDDW..", "GGGGGGGGGGGG", "............"],
        movies: ["............", ".WKWKWKWKWK.", ".KWKWKWKWKW.", "............", ".AAAAAAAAAA.", ".AKKKKKKKKA.", ".AKAAAAAAKA.", ".AKAAAAAAKA.", ".AKKKKKKKKA.", ".AAAAAAAAAA.", ".AA......AA.", "............"],
        shows: ["..K......K..", "...K....K...", "....K..K....", ".SSSSSSSSSS.", ".SCCCCCCCSS.", ".SCWCCCCCSY.", ".SCCCCCCCSS.", ".SCCCCCCCSY.", ".SCCCCCCCSS.", ".SSSSSSSSSS.", "..SS....SS..", "............"],
        live: ["............", "T....RR....T", ".T...RR...T.", "T.T..TT..T.T", ".T...TT...T.", "T...TTTT...T", "....T..T....", "....TTTT....", "...T....T...", "...TTTTTT...", "..T......T..", ".TT......TT."],
        games: ["............", "............", "..PPP..PPP..", ".PPPPPPPPPP.", "PPWPPPPPYPPP", "PWWWPPPBPRPP", "PPWPPPPPLPPP", "PPPPPPPPPPPP", "PPPP....PPPP", ".PP......PP.", "............", "............"],
        music: ["............", "....MMMMMMMM", "....MMMMMMMM", "....M......M", "....M......M", "....M......M", "....M......M", "..MMM....MMM", ".MMMM...MMMM", ".MMMM...MMMM", "..MM.....MM.", "............"],
        books: ["............", ".LLLL..LLLL.", "LWWWWLLWWWWL", "LWKKWLLWKKWL", "LWWWWLLWWWWL", "LWKKWLLWKKWL", "LWWWWLLWWWWL", "LWKKWLLWKKWL", "LWWWWLLWWWWL", "LLLLLLLLLLLL", ".....LL.....", "............"],
        search: ["............", "...WWWW.....", "..W....W....", ".W......W...", ".W......W...", ".W......W...", ".W......W...", "..W....W....", "...WWWWWW...", "........WW..", ".........WW.", "..........W."],
        requests: ["............", ".....YY.....", ".....YY.....", "..Y..YY..Y..", "...Y.YY.Y...", "YYYY.YY.YYYY", "...Y.YY.Y...", "..Y..YY..Y..", ".....YY.....", ".....YY.....", "............", "............"],
        more: ["............", "............", "............", "............", "............", ".WW..WW..WW.", ".WW..WW..WW.", "............", "............", "............", "............", "............"],
      };
      const INK = { R: "#FF5F6D", W: "#F4F1FF", B: "#3FE0FF", D: "#9A5B2E", C: "#B8B8C8", G: "#5BD86A", K: "#22183C", A: "#FFB547", S: "#7AA7FF", Y: "#FFD45A", T: "#3DD6C6", M: "#B69CFF", L: "#B9E36A", P: "#FF6F91" };
      // Grey while the rail waits to light up, a white flash as it lights (vi-q-start A), then its own colours.
      // Inline SVG styles apply to the whole page, so every rule is tied to this holiday's own classes.
      const STYLE = "<style>.rl-i.dark .vgi rect:not(.k){fill:currentColor}.rl-i.lit .vgi rect:not(.k){fill:#fff}" +
        ".rl-mark:has(.vgm){top:12px;left:0;width:14px;height:21px}.bt-mark:has(.vgm){top:5px;left:3px}</style>";
      const icon = (rows) => {
        let r = "";
        rows.forEach((row, y) => [...row].forEach((ch, x) => {
          if (ch !== ".") r += `<rect${ch === "K" ? ' class="k"' : ""} x="${x * 2}" y="${y * 2}" width="2.05" height="2.05" fill="${INK[ch]}"/>`;
        }));
        return `${STYLE}<g class="vgi" shape-rendering="crispEdges" stroke="none">${r}</g>`;
      };
      const icons = {};
      Object.keys(PIX).forEach((k) => { icons[k] = icon(PIX[k]); });
      // The blinking ▶ pointing at the icon you're on
      const ARROW = ["X.....", "XX....", "XXX...", "XXXX..", "XXXX..", "XXX...", "XX....", "X....."];
      let arrow = "";
      ARROW.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === "X") arrow += `<rect x="${0.4 + x * 1.25}" y="${5 + y * 1.25}" width="1.3" height="1.3"/>`; }));
      const mark = `<g class="vgm" shape-rendering="crispEdges"><g fill="#06222B" transform="translate(.7 .7)">${arrow}</g><g fill="#3FE0FF">${arrow}<animate attributeName="opacity" values="1;.3" keyTimes="0;.7" dur="1s" calcMode="discrete" repeatCount="indefinite"/></g></g>`;
      // Home's score screen. The trims are stretched to the page's width, which would squash the letters, so they
      // start as an empty marker and are drawn here at the trim's real size (and again if the window changes size).
      const PX = "font-family:'Press Start 2P',monospace";
      const hud = (svg) => {
        const w = Math.round(svg.getBoundingClientRect().width), h = Math.round(svg.getBoundingClientRect().height);
        if (!w || !h) return;
        svg.setAttribute("viewBox", `0 0 ${w} ${h}`);
        const top = svg.querySelector(".vgh").dataset.k === "top";
        // Phones keep the greeting at the top of the picture, so there the score moves to the bottom strip
        const narrow = w < 600;
        const fs = 11;
        const t = (x, y, fill, s, anchor) => `<text x="${x}" y="${y}" fill="${fill}" font-size="${fs}" text-anchor="${anchor || "start"}" style="${PX};paint-order:stroke;stroke:rgba(0,0,0,.5);stroke-width:2px">${s}</text>`;
        const score = (x, y) => t(x, y, "#FF5FB0", "1UP") + `<text class="vgs" x="${x + fs * 4}" y="${y}" fill="#fff" font-size="${fs}" style="${PX}">012900</text>`;
        const blink = (inner) => `<g>${inner}<animate attributeName="opacity" values="1;0" keyTimes="0;.62" dur="1s" calcMode="discrete" repeatCount="indefinite"/></g>`;
        let out = `<g class="vgh" data-k="${top ? "top" : "bottom"}" data-w="${w}"></g>`;
        if (top && narrow) {
          out += `<rect width="${w}" height="4" fill="rgba(10,6,24,.88)"/><line x1="0" x2="${w}" y1="4" y2="4" stroke="rgba(63,224,255,.7)" stroke-width="2" stroke-dasharray="7 5"/>`;
        } else if (top) {
          // A slim bar along the very top edge, above the greeting and clock's line, so it never crowds them
          const band = 18, y = 13;
          out += `<rect width="${w}" height="${band}" fill="rgba(10,6,24,.88)"/><rect y="${band - 2}" width="${w}" height="2" fill="rgba(63,224,255,.6)"/>`;
          out += score(18, y);
          if (w > 900) out += t(w / 2, y, "#FFD45A", 'HI-SCORE <tspan fill="#fff">091200</tspan>', "middle");
          out += t(w - 18, y, "#fff", "×12", "end") + `<circle cx="${w - 18 - fs * 3.9}" cy="${y - fs * .42}" r="${fs * .5}" fill="#FFD45A" stroke="#C48A1E" stroke-width="1.5"/>`;
          out += t(w - 18 - fs * 5.2, y, "#FF5FB0", "♥♥♥", "end");
        } else {
          // A 20px strip along the very bottom, below the floating Today cards
          const top0 = h - 20, y = h - 6;
          out += `<rect y="${top0}" width="${w}" height="20" fill="rgba(10,6,24,.88)"/><line x1="0" x2="${w}" y1="${top0}" y2="${top0}" stroke="rgba(63,224,255,.65)" stroke-width="2" stroke-dasharray="7 5"/>`;
          if (narrow) out += score(16, y) + blink(t(w - 16, y, "#FFD45A", "PRESS START", "end"));
          else out += blink(t(w / 2, y, "#FFD45A", "PRESS START", "middle")) + t(16, y, "#AAB0C4", "LEVEL 09·12") + t(w - 16, y, "#AAB0C4", "CREDIT 01", "end");
        }
        svg.innerHTML = out;
      };
      const fit = () => document.querySelectorAll("svg.sp-trim .vgh").forEach((g) => {
        const svg = g.ownerSVGElement;
        if (svg && String(Math.round(svg.getBoundingClientRect().width)) !== g.dataset.w) hud(svg);
      });
      let score = 12900;
      try {
        new MutationObserver(() => { if (document.querySelector("svg.sp-trim .vgh:not([data-w])")) fit(); }).observe(document.documentElement, { childList: true, subtree: true });
        addEventListener("resize", fit);
        setInterval(() => {
          const s = document.querySelectorAll("svg.sp-trim .vgs");
          if (!s.length) return;
          score = (score + 50) % 1000000;
          s.forEach((x) => { x.textContent = String(score).padStart(6, "0"); });
        }, 400);
      } catch (e) { /* older browsers: the trims just stay empty */ }
      return {
        icons,
        mark,
        topTrim: '<g class="vgh" data-k="top"></g>',
        bottomTrim: '<g class="vgh" data-k="bottom"></g>',
        countdown: () => (is("vgOn")() || /videogames/.test(location.search + (localStorage.getItem("benflix.holidayPreview") || "")) ? "Today is Video Games Day · Player 1 ready" : ""),
        pickTags: ["video game", "video games"],
        pickWords: ["Game", "Arcade", "Pixels", "Tron", "Wreck-It Ralph", "Free Guy", "Player One", "Scott Pilgrim"],
      };
    })()),
    make("earthday", () => is("edOn")() && !is("eaOn")(), true, ["#0E2C44", "#0B1A26"], ["#A8DCF7", "#A8DCF7", "#7FC8F2", "#56BDEB", "#7CD36B", "#8FD86F", "#B5E37A", "#E8F5A0", "#7CD36B"], "Earth Day pick", {
      pickTags: ["nature", "environmentalism", "wildlife", "nature documentary"], pickWords: ["Earth", "Planet"],
      icons: ed.icons, topTrim: ed.top, bottomTrim: ed.bottom,
      countdown: () => { try { const d = window.BenflixEarth ? window.BenflixEarth.day(new Date()) : 0; return d ? "Earth week · day " + d + " of 7 · Planet Earth picks on Movies and Shows" : ""; } catch (e) { return ""; } },
    }),
    make("halloween", is("hwOn"), true, ["#24140A", "#120A05"], ["#FF8A1F", "#8E44AD", "#7CFC00"], "Halloween pick", { countdown: cd(() => B.hw), pickTags: ["halloween"], pickWords: ["Halloween"] }),
    // Popcorn Day (approved 2026-11-17, Ben: LOVE Cinema marquee): cinema-red rail with butter and red icons, a
    // popped piece beside the icon you're on, chasing bulbs across the top of Home, a heap of popcorn along the
    // bottom that grows through the day, hours left as the first Today card, a popcorn movie as the first pick
    ((pc) => {
      const h = make("popcorn", is("pcOn"), true, ["#5A1218", "#1E0C0E"], [pc.BUT, pc.RED], "Popcorn Day pick", pc.extra());
      // the trims are drawn for the window's width when Home asks, so the bulbs and popcorn stay round
      Object.defineProperty(h, "topTrim", { enumerable: true, get: () => pc.top() });
      Object.defineProperty(h, "bottomTrim", { enumerable: true, get: () => pc.bottom() });
      return h;
    })({
      BUT: "#F6C744", RED: "#FF5A52",
      // one popped piece at (x, y), radius r, squashed back into a circle when the trim is stretched (sx, sy)
      puff(x, y, r, sx, sy, flip) {
        const L = [[-.42, .12, .55], [.44, .1, .55], [0, -.36, .6], [.05, .4, .52]];
        let s = `<ellipse cx="${x}" cy="${y + r * .25 / sy}" rx="${r * .95 / sx}" ry="${r * .95 / sy}" fill="#000" fill-opacity=".28"/>`;
        for (const [a, b, k] of L) s += `<ellipse cx="${x + a * r * flip / sx}" cy="${y + b * r / sy}" rx="${k * r / sx}" ry="${k * r / sy}" fill="url(#pcPuff)"/>`;
        return s + `<ellipse cx="${x - .18 * r * flip / sx}" cy="${y - .42 * r / sy}" rx="${.16 * r / sx}" ry="${.16 * r / sy}" fill="#fff" fill-opacity=".7"/>`;
      },
      grad: '<defs><radialGradient id="pcPuff" cx=".4" cy=".35" r=".75"><stop offset=".45" stop-color="#FFF4D6"/><stop offset="1" stop-color="#E2A93A"/></radialGradient>'
        + '<radialGradient id="pcBulb" cx=".35" cy=".35" r=".8"><stop offset="0" stop-color="#FFF8DA"/><stop offset=".55" stop-color="#F6C744"/><stop offset="1" stop-color="#C48A12"/></radialGradient>'
        + '<radialGradient id="pcGlow"><stop offset="0" stop-color="#F6C744" stop-opacity=".75"/><stop offset="1" stop-color="#F6C744" stop-opacity="0"/></radialGradient></defs>',
      // how stretched Home's trims are right now (they fill the picture's width, 44px and 36px tall)
      wide() { return Math.max(320, (window.innerWidth || 1200) - (matchMedia("(max-width: 760px)").matches ? 0 : 88)) / 1200; },
      // a slim sign along the very top (12px strip, bulbs on its edge) so it stays clear of the greeting and clock
      top() {
        const sx = this.wide(), sy = 44 / 60, r = 6.5, step = 36 / sx, n = Math.ceil(1200 / step), band = 12 / sy;
        let s = this.grad + '<style>.pcb{animation:pcchase 1.2s steps(1) infinite}@keyframes pcchase{0%{opacity:1}33%{opacity:.4}66%{opacity:1}}</style>'
          + '<linearGradient id="pcStrip" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6E1016"/><stop offset=".5" stop-color="#9E2129"/><stop offset="1" stop-color="#6E1016"/></linearGradient>'
          + `<rect width="1200" height="${band}" fill="url(#pcStrip)"/><rect y="${band - 3 / sy}" width="1200" height="${3 / sy}" fill="#F6C744"/>`;
        for (let i = 0; i <= n; i++) {
          const x = i * step + step / 2, y = band;
          s += `<g class="pcb" style="animation-delay:${-(i % 3) * .4}s"><ellipse cx="${x}" cy="${y}" rx="${r * 1.7 / sx}" ry="${r * 1.7 / sy}" fill="url(#pcGlow)"/>`
            + `<ellipse cx="${x}" cy="${y}" rx="${r / sx}" ry="${r / sy}" fill="url(#pcBulb)"/></g>`;
        }
        return s;
      },
      bottom() {
        const sx = this.wide(), sy = 36 / 40, h = new Date().getHours(), full = h < 12 ? .55 : h < 17 ? .78 : 1, r0 = 7.5;
        // one puff drawn once (radius 1), then placed many times
        let seed = 11, s = this.grad + `<defs><g id="pcP">${this.puff(0, 0, 1, 1, 1, 1)}</g></defs>`;
        const rnd = () => (seed = (seed * 9301 + 49297) % 233280) / 233280;
        for (let row = 0; row < 3; row++) {
          const count = Math.floor((1200 * sx) / (r0 * 1.5) * (1.15 - row * .3) * full);
          const y = 40 - (r0 * 1.25 + row * r0 * 1.1 * full) / sy;
          for (let i = 0; i < count; i++) {
            const x = rnd() * 1200, r = r0 * (.8 + rnd() * .45), yy = y - rnd() * r0 * .25 / sy, f = rnd() < .5 ? 1 : -1;
            s += `<use href="#pcP" transform="translate(${x.toFixed(1)} ${yy.toFixed(1)}) scale(${(f * r / sx).toFixed(3)} ${(r / sy).toFixed(3)})"/>`;
          }
        }
        return s;
      },
      // the usual icon's lines, in butter or red with a soft glow
      icon(n, c) {
        const svg = (B.doorIcon || B.I)(n) || "";
        const inner = svg.replace(/^\s*<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "");
        return `<g style="color:${c};stroke:${c};filter:drop-shadow(0 0 2px ${c})">${inner}</g>`;
      },
      extra() {
        const pc = this, BUT = this.BUT, RED = this.RED;
        const ICONS = { home: ["home", BUT], search: ["search", RED], movies: ["film", RED], shows: ["tv", BUT], live: ["live", RED], games: ["pad", BUT], music: ["note", RED], books: ["book", BUT], requests: ["plus", BUT], more: ["more", BUT] };
        const icons = {};
        // the web's own icon names come from core.js, which loads first, so each is drawn when the rail asks
        for (const [k, [n, c]] of Object.entries(ICONS)) Object.defineProperty(icons, k, { enumerable: true, get: () => pc.icon((B.DOORS[k] && B.DOORS[k].icon) || n, c) });
        const out = {
          icons,
          mark: `${this.grad}${this.puff(6, 10, 6, 1, 1, 1)}`,
          countdown: () => {
            const d = now();
            if (d.getMonth() !== 0 || d.getDate() !== 19) return "";
            const left = 24 - d.getHours();
            return left <= 1 ? "Last hour of Popcorn Day · grab a bowl" : `${left} hours of Popcorn Day left · grab a bowl`;
          },
          countdownKind: "Popcorn Day",
          pickTags: ["blockbuster"],
          pickWords: ["Jurassic Park", "Back to the Future", "Ghostbusters", "Raiders of the Lost Ark", "Jaws"],
        };
        return out;
      },
    }),
    // Star Wars Day (Ben 2026-11-17, sheet starwars-7-icons): the saga drawings replace the rail and tab icons, each
    // in its own colour; a short lit laser sword in the profile's side colour marks where you are; Home stays plain
    // (no trims) with the "May the 4th" card; start-up icons ignite in the side colour. Same drawings as the app's
    // ui/theme/StarWarsNav.kt.
    (() => {
      const blade = () => (B.me && B.swSide && B.swSide(B.me.id) === "dark" ? "#FF2A2A" : "#4FB8FF");
      const g = (c, d) => `<path class="swp" d="${d}" stroke="${c}" stroke-opacity=".3" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path class="swp" d="${d}" stroke="${c}" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`;
      const e = {
        topTrim: "",
        bottomTrim: "",
        pickTags: ["star wars"],
        pickWords: ["Star Wars"],
        countdown: () => { const left = 24 - new Date().getHours(); return `May the 4th · today only, ${left} ${left === 1 ? "hour" : "hours"} left`; },
        icons: {
          home: g("#E2C296", "M3 19.5h18M5 19.5v-5a7 7 0 0 1 14 0v5M10 19.5v-3a2 2 0 0 1 4 0v3M7.5 11.5h2"),
          movies: g("#FFE81F", "M5 20.5L9 3.5h6l4 17zM7.4 15.5h9.2M8.3 11.5h7.4M9.1 7.5h5.8"),
          shows: g("#FF9A3D", "M5 8a3 3 0 1 0 6 0a3 3 0 1 0-6 0zM13.5 6a2 2 0 1 0 4 0a2 2 0 1 0-4 0zM2 19c5-5.5 11-5.5 20-2.5M14 16.4a2.4 2.4 0 0 1 4.8 0"),
          live: g("#6CCBFF", "M6 9.5a6 6 0 1 0 12 0a6 6 0 1 0-12 0zM9.4 9.5a2.6 6 0 1 0 5.2 0a2.6 6 0 1 0-5.2 0zM6 9.5h12M8 21h8M10 21l.8-3.5h2.4L14 21"),
          games: g("#4FB8FF", "M6.5 17.5L18.5 5.5") + g("#FF3B3B", "M17.5 17.5L5.5 5.5") + g("#B8BEC8", "M3.5 20.5l3-3M20.5 20.5l-3-3"),
          music: g("#DCEBFF", "M12 12L4 6M12 12L3 13.5M12 12l-6 8M12 12l8-7M12 12l9 2M12 12l5 8M12 12V3M12 12h.01"),
          books: g("#FF3B3B", "M12 3l7 4v8l-7 4-7-4V7zM5 7l7 4 7-4M12 11v8"),
        },
      };
      // the side's colour is read each time the rail is drawn (set after make(), which copies plain values)
      const o = make("starwars", is("swOn"), true, ["#0B0B14", "#05050A"], ["#4FB8FF"], "May the 4th pick", e);
      Object.defineProperty(o, "mark", { enumerable: true, get: () => { const c = blade(); return `<rect x="1.4" y="0" width="9.2" height="12.6" rx="4.6" fill="${c}" opacity=".35"/><rect x="4.2" y=".7" width="3.6" height="11.8" rx="1.8" fill="${c}"/><rect x="5.3" y="1.4" width="1.4" height="10.6" rx=".7" fill="#fff"/><rect x="3.2" y="12.6" width="5.6" height="5.4" rx=".7" fill="#B8BEC8"/><rect x="3.2" y="14" width="5.6" height=".8" fill="#3A3D44"/><rect x="3.2" y="15.8" width="5.6" height=".8" fill="#3A3D44"/>`; } });
      Object.defineProperty(o, "lights", { enumerable: true, get: () => [blade()] });
      return o;
    })(),
    // St. Patrick's Day: charms for the door icons, bunting and a shamrock patch on Home (same as the app's StPatricksNav.kt)
    make("stpatricks", is("spOn"), true, ["#0E2B1D", "#081710"], ["#3DBE6C", "#E2B33C", "#F3F0E4"], "St. Patrick's pick", Object.assign({ countdown: cd(() => B.sp), pickTags: ["ireland"], pickWords: ["Irish", "Ireland", "Brooklyn", "The Quiet Man", "The Commitments", "Sing Street", "The Secret of Kells", "Song of the Sea", "Wolfwalkers"] }, (() => {
      const G = "#E2B33C", GD = "#8A6414", CL = "#3DBE6C", CLL = "#5FD889", EM = "#16603A", EMD = "#0B3A22", CR = "#F3F0E4";
      let seed = 7;
      const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
      const leaf = (fill, vein) => `<path d="M0 0C-7 -4 -11 -12 -6 -16.5C-2.5 -19 0 -16 0 -13.5C0 -16 2.5 -19 6 -16.5C11 -12 7 -4 0 0Z" fill="${fill}" stroke="${EMD}" stroke-width=".9"/>${vein ? '<path d="M0 -1.5V-12M-3.5 -14q-2 1.5 -2.5 4" stroke="#fff" stroke-opacity=".4" stroke-width=".85" fill="none" stroke-linecap="round"/>' : ""}`;
      // a shamrock whose stem meets the ground at x,y (the outer group places it, the inner one sways)
      const shamrock = (x, y, s, lean, fill, vein, sway) => `<g transform="translate(${x.toFixed(1)} ${y}) scale(${s.toFixed(2)})"><g class="${sway ? "spn-grass" : ""}" style="animation-delay:-${(rnd() * 4).toFixed(2)}s"><g transform="rotate(${lean.toFixed(1)})"><path d="M0 0C2 -8 -1 -14 0 -20" stroke="${EM}" stroke-width="2" fill="none" stroke-linecap="round"/><g transform="translate(0 -20)">${[0, 120, 240].map((a) => `<g transform="rotate(${a})">${leaf(fill, vein)}</g>`).join("")}<circle r="1.6" fill="${EM}"/></g></g></g></g>`;
      const css = "<style>.spn-sway{transform-box:fill-box;transform-origin:50% 0;animation:spnSway 3.6s ease-in-out infinite}@keyframes spnSway{0%,100%{transform:rotate(-3deg)}50%{transform:rotate(3deg)}}.spn-grass{transform-box:fill-box;transform-origin:50% 100%;animation:spnGrass 4.4s ease-in-out infinite}@keyframes spnGrass{0%,100%{transform:rotate(-2.5deg)}50%{transform:rotate(2.5deg)}}.sp-trim.top:has(.spn-sway){right:auto;max-width:none;height:max(34px,5vw);width:calc(max(34px,5vw) * 20)}.sp-trim.bottom:has(.spn-grass){max-width:none;height:max(30px,3.4vw);width:calc(max(30px,3.4vw) * 30)}.home:has(.spn-grass){overflow-x:clip}@media (min-width:761px){.sp-trim.top:has(.spn-sway){top:var(--bar)}}</style>";
      // Bunting: green, white and gold cloth flags in swags across the top (1200 x 60), hung just below the greeting and
      // clock line on wide screens so it never crowds them. The trims keep their shape on
      // every screen: they grow with the screen's width and the far end is cut off rather than squashed
      const bunting = () => {
        const W = 1200, swags = 3, sw = W / swags, cols = [EM, CR, G];
        let out = css + `<defs><linearGradient id="spnShade" x1="0" x2="1"><stop offset="0" stop-color="#fff" stop-opacity=".18"/><stop offset=".5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".28"/></linearGradient></defs>`;
        let k = 0;
        for (let s = 0; s < swags; s++) {
          const x0 = s * sw, sag = 18;
          out += `<path d="M${x0} 5Q${x0 + sw / 2} ${5 + sag * 2} ${x0 + sw} 5" stroke="#D9CFB2" stroke-width="1.6" fill="none"/>`;
          const n = Math.round(sw / 32);
          for (let i = 0; i < n; i++) {
            const t = (i + 0.5) / n, x = x0 + t * sw, y = 5 + sag * 4 * t * (1 - t), ang = Math.atan2(sag * 4 * (1 - 2 * t), sw) * 57.3;
            const c = cols[k++ % 3], white = c === CR;
            out += `<g transform="translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(${ang.toFixed(1)})"><g class="spn-sway" style="animation-delay:-${((i * 0.37 + s) % 3.6).toFixed(2)}s"><path d="M-13 0L13 0L0 34Z" fill="${c}"/><path d="M-13 0L13 0L0 34Z" fill="url(#spnShade)"/><path d="M-13 0H13V3.5H-13Z" fill="#000" fill-opacity=".22"/><path d="M-10.5 6H10.5" stroke="${white ? "#B9AE8C" : "#fff"}" stroke-opacity="${white ? 1 : 0.4}" stroke-width=".8" stroke-dasharray="2 1.6"/>${white ? `<g transform="translate(0 21) scale(.4)">${shamrock(0, 0, 1, 0, CL, false, false)}</g>` : ""}</g></g>`;
          }
          out += `<circle cx="${x0}" cy="5" r="3" fill="${G}" stroke="${GD}"/>`;
        }
        return out + `<circle cx="${W}" cy="5" r="3" fill="${G}" stroke="${GD}"/>`;
      };
      // Shamrock patch along the bottom (drawn 1920 x 64, shrunk to the kit's 1200 x 40), kept low so the news cards
      // floating just above it never hide the tops. Each shamrock and flower is
      // drawn once and reused, and only the front row moves, so old TVs' browsers keep up.
      const patch = () => {
        const W = 1920;
        const stem = `<path d="M0 0C2 -8 -1 -14 0 -20" stroke="${EM}" stroke-width="2" fill="none" stroke-linecap="round"/>`;
        const head = (fill, vein) => `${stem}<g transform="translate(0 -20)">${[0, 120, 240].map((a) => `<g transform="rotate(${a})">${leaf(fill, vein)}</g>`).join("")}<circle r="1.6" fill="${EM}"/></g>`;
        let pet = "";
        for (let i = 0; i < 22; i++) {
          const a = i * 2.4, r = (i % 3 + 2) * 1.3, px = (Math.cos(a) * r).toFixed(1), py = (Math.sin(a) * r * 0.9).toFixed(1);
          pet += `<ellipse cx="${px}" cy="${py}" rx="1.5" ry="2.6" transform="rotate(${(a * 57).toFixed(0)} ${px} ${py})" fill="${i % 5 ? "#F7F5EE" : "#F2D9DF"}"/>`;
        }
        const defs = `<defs><linearGradient id="spnLeaf" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${EM}"/><stop offset="1" stop-color="${CLL}"/></linearGradient><g id="spnBack">${head("#1F6E3E", false)}</g><g id="spnFront">${head("url(#spnLeaf)", true)}</g><g id="spnFlower"><path d="M0 0C2 -10 -1 -20 0 -28" stroke="${EM}" stroke-width="1.6" fill="none"/><g transform="translate(0 -31)"><circle r="5.6" fill="#C9C3B0"/>${pet}</g></g></defs>`;
        const put = (id, x, y, sc, lean, move) => `<g transform="translate(${x.toFixed(0)} ${y}) scale(${sc.toFixed(2)})">${move ? `<g class="spn-grass" style="animation-delay:-${(rnd() * 4).toFixed(1)}s">` : "<g>"}<use href="#${id}" transform="rotate(${lean.toFixed(0)})"/></g></g>`;
        let back = "", front = "", fl = "";
        for (let x = 4; x < W; x += 12 + rnd() * 8) back += put("spnBack", x, 64, (0.45 + rnd() * 0.35) * 0.9, -25 + rnd() * 50, false);
        for (let x = 40; x < W; x += 90 + rnd() * 80) fl += put("spnFlower", x, 66, 0.5 + rnd() * 0.25, 0, true);
        for (let x = 10; x < W; x += 16 + rnd() * 12) front += put("spnFront", x, 66, 0.5 + rnd() * 0.4, -20 + rnd() * 40, true);
        return css + defs + `<g transform="scale(.625)">${back}${fl}${front}</g>`;
      };
      const charm = (door) => `<image href="web/img/sp/sp_icon_${door}.png" x="-3" y="-3" width="30" height="30"/>`;
      return {
        icons: {
          home: `<path d="M3 11l9-7 9 7M5 10v10h14V10" stroke="${CL}" fill="none"/><path d="M10 20v-5h4v5" stroke="${G}" fill="none"/>`,
          search: `<circle cx="11" cy="11" r="6" stroke="${G}" fill="none"/><path d="M15.5 15.5L20 20" stroke="${G}" stroke-width="2.2"/>`,
          movies: charm("movies"), shows: charm("shows"), live: charm("live"), games: charm("games"), music: charm("music"), books: charm("books"),
        },
        topTrim: bunting(),
        bottomTrim: patch(),
      };
    })())),
    july4(),
    NY(make("newyear", is("nyOn"), true, ["#16224A", "#0B1230"], ["#E8B84A", "#C9D1DE"], "New Year pick", { countdown: cd(() => B.ny) })),
    // Thanksgiving (approved 2026-11-17, sheet nav-13b g2-swag): harvest-brown rail, the rail icons themselves in
    // gold, pumpkin orange and cranberry, a short leaf garland swagging across the top of Home with an acorn at each
    // low point and now and then a leaf drifting down, and only two pumpkins in Home's bottom-right corner.
    make("thanksgiving", is("tgOn"), false, ["#2E1E14", "#1C120B"], ["#E2702A", "#E8B04A", "#B83A1E"], "Thanksgiving pick", (() => {
      // Thanksgiving is the fourth Thursday of November
      const day = (y) => { const d = new Date(y, 10, 1); return new Date(y, 10, 1 + ((4 - d.getDay() + 7) % 7) + 21); };
      const countdown = () => {
        const n = now(); const t = day(n.getFullYear());
        const left = Math.round((new Date(t.getFullYear(), t.getMonth(), t.getDate()) - new Date(n.getFullYear(), n.getMonth(), n.getDate())) / 864e5);
        return left > 1 ? `${left} days to Thanksgiving` : left === 1 ? "Thanksgiving is tomorrow" : left === 0 ? "Happy Thanksgiving" : "";
      };
      // Icons: the usual ones, drawn in harvest colours with a warm glow
      const col = { home: "#E8B04A", movies: "#F07E36", shows: "#D9473A", live: "#D9473A", games: "#E8B04A", music: "#F07E36", books: "#E8B04A" };
      const inner = (svg) => String(svg || "").replace(/^\s*<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "");
      const usual = (k) => {
        try { return k === "home" ? B.I("home") : (B.doorIcon || B.I)((B.DOORS[k] || {}).icon || k); } catch (e) { return ""; }
      };
      const icons = {};
      Object.keys(col).forEach((k) => Object.defineProperty(icons, k, {
        enumerable: true,
        get: () => `<g stroke="${col[k]}" style="filter:drop-shadow(0 0 1.5px ${col[k]}88)">${inner(usual(k))}</g>`,
      }));
      // Art (same shapes as the app's ThanksgivingNav.kt)
      const MAPLE = "M50,0 L57,14 L66,9 L63,30 L80,17 L77,26 L95,24 L88,38 L97,44 L72,62 L76,73 L54,69 L54,96 L46,96 L46,69 L24,73 L28,62 L3,44 L12,38 L5,24 L23,26 L20,17 L37,30 L34,9 L43,14 Z";
      const OAK = "M50,2 C60,2 62,12 58,16 C68,12 74,20 66,28 C78,26 82,38 70,42 C82,44 82,58 68,58 C76,64 72,74 60,70 L54,80 L54,98 L46,98 L46,80 L40,70 C28,74 24,64 32,58 C18,58 18,44 30,42 C18,38 22,26 34,28 C26,20 32,12 42,16 C38,12 40,2 50,2 Z";
      const LEAF = [["#f0603a", "#9c2414"], ["#ffa040", "#c2561f"], ["#ffd76a", "#c88a26"], ["#c8384e", "#6e1626"], ["#d8742e", "#7a3a14"]];
      const g2 = (id, a, b, x2) => `<linearGradient id="${id}" x1="0" y1="0" x2="${x2 || 1}" y2="${x2 ? 0 : 1}"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient>`;
      const defs = `<defs>${LEAF.map(([a, b], i) => g2("tgl" + i, a, b)).join("")}${g2("tgnut", "#e0a35c", "#6e4020", 1)}${g2("tgcup", "#8a5a32", "#4a2d16")}${g2("tgpk", "#ffa24a", "#a5441a")}${g2("tgpw", "#fbf4e4", "#b5a586")}<radialGradient id="tgbr" cx=".35" cy=".35"><stop offset="0" stop-color="#fff0b0"/><stop offset=".5" stop-color="#d9a940"/><stop offset="1" stop-color="#7a5418"/></radialGradient></defs>`;
      const css = "<style>.sp-trim.top:has(.tgn){top:var(--bar);height:auto;aspect-ratio:20/1;overflow:visible}.sp-trim.bottom:has(.tgn){height:auto;aspect-ratio:30/1;overflow:visible}"
        + ".tgn .sw{animation:tgsw 4.5s ease-in-out infinite alternate}@keyframes tgsw{from{transform:rotate(-5deg)}to{transform:rotate(5deg)}}"
        + ".tgn .fall{animation:tgfall 13s linear infinite}@keyframes tgfall{0%{transform:translate(0,0) rotate(0);opacity:0}5%{opacity:1}30%{transform:translate(40px,90px) rotate(100deg)}60%{transform:translate(-10px,180px) rotate(210deg)}90%{opacity:1}100%{transform:translate(20px,260px) rotate(320deg);opacity:0}}</style>";
      let seed = 11; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
      const leaf = (x, y, rot, size, c, oak, cls, delay) => {
        const k = size / 100; const d = oak ? OAK : MAPLE;
        const veins = oak ? "M50,96 L50,8 M50,40 L68,30 M50,40 L32,30 M50,58 L70,50 M50,58 L30,50" : "M50,92 L50,8 M50,66 L84,30 M50,66 L16,30 M50,70 L78,58 M50,70 L22,58";
        return `<g class="${cls || ""}" style="transform-origin:${x}px ${y}px;animation-delay:${delay || 0}s"><g transform="translate(${x},${y}) rotate(${rot}) scale(${k}) translate(-50,-98)"><path d="${d}" fill="url(#tgl${c})" stroke="url(#tgl${c})" stroke-width="5" stroke-linejoin="round"/><path d="${veins}" stroke="rgba(255,236,190,.45)" stroke-width="2.6" fill="none" stroke-linecap="round"/></g></g>`;
      };
      const acorn = (x, y, s, rot, cls, delay) => `<g class="${cls || ""}" style="transform-origin:${x}px ${y}px;animation-delay:${delay || 0}s"><g transform="translate(${x},${y}) rotate(${rot}) scale(${s})"><path d="M0,-4 L1,-11" stroke="#4a2d16" stroke-width="2.6" stroke-linecap="round"/><path d="M-10,6 C-10,20 -4,28 0,31 C4,28 10,20 10,6 Z" fill="url(#tgnut)"/><ellipse cx="-4" cy="13" rx="2.4" ry="6" fill="rgba(255,235,200,.45)"/><path d="M-12.5,7 C-12.5,-4 12.5,-4 12.5,7 C8,9.5 -8,9.5 -12.5,7 Z" fill="url(#tgcup)"/></g></g>`;
      const tack = (x, y) => `<circle cx="${x}" cy="${y + 1}" r="5" fill="rgba(0,0,0,.35)"/><circle cx="${x}" cy="${y}" r="5" fill="url(#tgbr)"/><circle cx="${x - 1.5}" cy="${y - 1.5}" r="1.6" fill="rgba(255,250,220,.8)"/>`;
      const twine = (d) => `<path d="${d}" stroke="#7a5a32" stroke-width="3.6" fill="none"/><path d="${d}" stroke="#d2ad72" stroke-width="2.4" fill="none"/><path d="${d}" stroke="#8a6a3e" stroke-width="2.4" fill="none" stroke-dasharray="2 3"/>`;
      const pumpkin = (cx, by, w, h, white) => {
        const ribs = [[-0.36, 0.62], [0.36, 0.62], [-0.18, 0.82], [0.18, 0.82], [0, 1]];
        return `<ellipse cx="${cx}" cy="${by - 1}" rx="${w * 0.52}" ry="${h * 0.1}" fill="rgba(0,0,0,.4)"/>${ribs.map(([o, k]) => `<ellipse cx="${cx + o * w}" cy="${by - h / 2}" rx="${w * 0.3 * k}" ry="${h / 2}" fill="url(#${white ? "tgpw" : "tgpk"})" stroke="rgba(80,30,5,.35)" stroke-width="1"/>`).join("")}<path d="M${cx - 1},${by - h + 1} C${cx - 2},${by - h * 1.28} ${cx + 3},${by - h * 1.36} ${cx + w * 0.16},${by - h * 1.3}" stroke="#5a4a22" stroke-width="${Math.max(3, w * 0.08)}" fill="none" stroke-linecap="round"/>`;
      };
      // Top: viewBox 1200 x 60 kept at 20:1 by the style above, so the swag never stretches
      const top = (() => {
        seed = 11; const n = 4, seg = 1200 / n, dip = 15; let tw = "", lv = "", tk = "";
        for (let i = 0; i < n; i++) {
          const x0 = i * seg + 4, x1 = (i + 1) * seg - 4, mx = (x0 + x1) / 2;
          tw += twine(`M${x0},6 Q${mx},${6 + dip * 2} ${x1},6`); tk += tack(x0, 6) + (i === n - 1 ? tack(x1, 6) : "");
          for (let j = 1; j < 9; j++) {
            const t = j / 9, px = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * mx + t * t * x1, py = 6 + 2 * (1 - t) * t * dip * 2;
            lv += leaf(px, py, 180 + (rnd() * 44 - 22), 36 * (0.88 + rnd() * 0.24), Math.floor(rnd() * 5), j % 3 === 1, "sw", -rnd() * 5);
          }
          lv += acorn(mx, 6 + dip + 3, 0.75, 0, "sw", -1.5);
        }
        const fall = `<g class="fall" style="animation-delay:-4s">${leaf(520, 30, 180, 30, 1, false)}</g>`;
        return `${css}${defs}<g class="tgn">${tw}${lv}${tk}${fall}</g>`;
      })();
      // Bottom: only the bottom-right corner (viewBox 1200 x 40 kept at 30:1, drawn upward from the bottom edge)
      const bottom = (() => {
        // Small enough to sit in the gap under the Today cards (about 20 of the 40 units)
        const R = 1190;
        const leaves = [[R - 62, -55, 0], [R - 46, 30, 2], [R - 2, 70, 1], [R - 72, -20, 3]].map(([x, r, c], i) => leaf(x, 39, r, 13, c, i % 2 === 1)).join("");
        return `${defs}<g class="tgn">${leaves}${pumpkin(R - 22, 39.5, 30, 20)}${pumpkin(R - 48, 39.5, 18, 12.5, true)}${acorn(R - 62, 33, 0.36, 70)}</g>`;
      })();
      return {
        icons,
        topTrim: top,
        bottomTrim: bottom,
        countdown,
        pickTags: ["thanksgiving"],
        pickWords: ["Thanksgiving", "Planes, Trains", "Home for the Holidays", "Pieces of April", "Free Birds"],
      };
    })()),
    make("valentine", is("valOn"), false, ["#3A1226", "#200A15"], ["#E8405E", "#A8284F"], "Valentine's pick", { icons: VAL.icons, topTrim: VAL.topTrim, bottomTrim: VAL.bottomTrim, countdown: VAL.countdown, pickTags: ["valentine's day"], pickWords: ["Valentine", "Princess Bride", "Notting Hill", "Harry Met Sally"] }),
    // Easter (approved 2026-11-17, thread "Fit Easter theme to new navigation", direction A "Easter tree ribbon"):
    // meadow-green rail, the door icons become the Easter tree charms, a painted egg on a ribbon hangs by the icon
    // you're on, a pink satin ribbon swags across the top of Home with painted eggs swaying, and spring grass with
    // tulips and eggs runs along the bottom. Kids too. The drawings come from easter.js (BenflixEaster.kit).
    make("easter", is("eaOn"), true, ["#122019", "#0B150F"], ["#FFB8D2", "#BFE6FF", "#FFF1A6", "#C9F2C0", "#E3D5FF", "#FFD9B8"], "Easter pick", easterNav()),
    // Christmas: Classic big bulbs (approved 2026-11-17), art below (XMAS), the same as the app's ChristmasNav.kt
    make("christmas", is("xmasOn"), false, ["#0F2A1D", "#0B1912"], ["#D7263D", "#2E9E5B", "#E8C15A"], "Christmas pick", { countdown: () => { const d = new Date(); return d.getMonth() === 11 && d.getDate() === 25 ? "Merry Christmas!" : cd(() => B.xmas)(); }, pickTags: ["christmas", "santa claus"], pickWords: ["Christmas", "Santa", "Grinch", "Elf", "Home Alone", "Polar Express", "Klaus"], icons: XMAS.icons, mark: XMAS.mark, topTrim: XMAS.topTrim, bottomTrim: XMAS.bottomTrim }),
  ];

  B.HOLIDAY_NAV = ALL;
  // For trying a look on any day: ?holiday=christmas in the address, or localStorage "benflix.holidayPreview"
  const preview = () => {
    try { return new URLSearchParams(location.search).get("holiday") || localStorage.getItem("benflix.holidayPreview") || ""; } catch (e) { return ""; }
  };
  /** The holiday that's on right now for this profile, or null on an ordinary day */
  B.holidayNav = () => {
    const p = preview();
    if (p) { const h = ALL.find((x) => x.key === p); if (h) return h; }
    const kids = !!(B.me && B.me.kids);
    return ALL.find((h) => { try { return (!kids || h.kids) && h.on(); } catch (e) { return false; } }) || null;
  };
  /** The holiday's SVG, ready to put in a page */
  // The icon for a rail item or phone tab: the holiday's own one when it has it, else the usual one
  B.holidayIcon = (key, usual) => {
    const h = B.holidayNav ? B.holidayNav() : null;
    const art = h && h.icons && h.icons[key];
    return art ? `<svg class="i hol-i" viewBox="0 0 24 24" aria-hidden="true">${art}</svg>` : usual;
  };
  // A holiday can also give a function that draws its own whole <svg> (Christmas sizes its bulbs to the screen)
  B.holidaySvg = (art, box, cls) => (typeof art === "function" ? art(cls) : art ? `<svg class="${cls || ""}" viewBox="${box}" preserveAspectRatio="${cls && cls.includes("trim") ? "none" : "xMidYMid meet"}" aria-hidden="true">${art}</svg>` : "");
})();
