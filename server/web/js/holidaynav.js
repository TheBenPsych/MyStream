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

  // Most important first: when two are on at once the first wins (Friday the 13th takes over any season look,
  // Earth Day comes after Easter week)
  const ALL = [
    make("friday13", is("f13On"), true, ["#141414", "#0A0A0A"], ["#9BE15D", "#C9D0E0"], "Friday the 13th pick", { pickWords: ["Friday the 13th"], pickTags: ["slasher"] }),
    make("videogames", is("vgOn"), true, ["#1A1030", "#0E0819"], ["#FF6F91", "#3DD6C6", "#FFE14D", "#7AA7FF"], "Game Day pick", { pickTags: ["video game"], pickWords: ["Game"] }),
    make("earthday", () => is("edOn")() && !is("eaOn")(), true, ["#12261A", "#0A160F"], ["#5BD18B", "#3FA7D6", "#8BC34A"], "Earth Day pick", { pickTags: ["nature", "environment"], pickWords: ["Earth"] }),
    make("halloween", is("hwOn"), true, ["#24140A", "#120A05"], ["#FF8A1F", "#8E44AD", "#7CFC00"], "Halloween pick", { countdown: cd(() => B.hw), pickTags: ["halloween"], pickWords: ["Halloween"] }),
    make("popcorn", is("pcOn"), true, ["#2A220E", "#17130A"], ["#F5D76E", "#D7263D", "#FFFFFF"], "Popcorn Day pick"),
    make("starwars", is("swOn"), true, ["#0B0B14", "#05050A"], ["#FFE81F", "#4FC3F7", "#E53935"], "Star Wars Day pick", { pickWords: ["Star Wars"] }),
    make("stpatricks", is("spOn"), true, ["#0F2A17", "#08170D"], ["#2E9E5B", "#7BD389", "#E8C15A"], "St. Patrick's pick", { countdown: cd(() => B.sp), pickTags: ["ireland"], pickWords: ["Irish"] }),
    make("july4", is("j4On"), true, ["#101A33", "#0A0F1F"], ["#D7263D", "#FFFFFF", "#3F6FD8"], "4th of July pick", { countdown: cd(() => B.j4), pickWords: ["Independence Day"] }),
    make("newyear", is("nyOn"), true, ["#141A2E", "#0B0E1A"], ["#E8C15A", "#C9D0E0"], "New Year's pick", { countdown: cd(() => B.ny), pickTags: ["new year's eve"], pickWords: ["New Year"] }),
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
      const css = "<style>.sp-trim.top:has(.tgn){height:auto;aspect-ratio:20/1;overflow:visible}.sp-trim.bottom:has(.tgn){height:auto;aspect-ratio:30/1;overflow:visible}"
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
        const R = 1188;
        const leaves = [[R - 96, -55, 0], [R - 70, 30, 2], [R - 4, 70, 1], [R - 112, -20, 3]].map(([x, r, c], i) => leaf(x, 37, r, 22, c, i % 2 === 1)).join("");
        return `${defs}<g class="tgn">${leaves}${pumpkin(R - 36, 39, 50, 34)}${pumpkin(R - 76, 39, 30, 21, true)}${acorn(R - 98, 28, 0.6, 70)}</g>`;
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
    make("valentine", is("valOn"), false, ["#2A0F1A", "#170810"], ["#FF5C8A", "#D7263D", "#FFB3C6"], "Valentine's pick", { countdown: cd(() => B.val), pickTags: ["valentine's day"], pickWords: ["Valentine"] }),
    make("easter", is("eaOn"), true, ["#1F2433", "#121522"], ["#F7B2D9", "#B8E0D2", "#FFE29A", "#C3B1E1"], "Easter pick", { countdown: cd(() => B.ea), pickTags: ["easter"], pickWords: ["Easter"] }),
    make("christmas", is("xmasOn"), false, ["#0F2A1D", "#0B1912"], ["#D7263D", "#2E9E5B", "#E8C15A"], "Christmas pick", { countdown: cd(() => B.xmas), pickTags: ["christmas", "santa claus"], pickWords: ["Christmas", "Santa", "Grinch"] }),
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
  B.holidaySvg = (art, box, cls) => (art ? `<svg class="${cls || ""}" viewBox="${box}" preserveAspectRatio="${cls && cls.includes("trim") ? "none" : "xMidYMid meet"}" aria-hidden="true">${art}</svg>` : "");
})();
