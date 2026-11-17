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
    const fringe = (burst) => {
      const cols = [G, GL, S, GD, "#fff"];
      let s = `<style>.nyf{animation:nyf 3.4s ease-in-out infinite}.nyf.b{animation-duration:.25s}@keyframes nyf{50%{opacity:.72}}</style><rect width="1200" height="4" fill="${G}"/>`;
      for (let i = 0; i < 380; i++) {
        const x = f(i * 3.16 + h(i, 1)), len = f(60 * (.6 + .4 * h(i, 2))), sw = f(Math.sin(i * .37) * 1.4);
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
    NY(make("newyear", is("nyOn"), true, ["#16224A", "#0B1230"], ["#E8B84A", "#C9D1DE"], "New Year pick", { countdown: cd(() => B.ny) })),
    make("thanksgiving", is("tgOn"), false, ["#2A1A0E", "#170E08"], ["#E07A2E", "#C9A227", "#8B3A1E"], "Thanksgiving pick", { pickTags: ["thanksgiving"], pickWords: ["Thanksgiving"] }),
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
