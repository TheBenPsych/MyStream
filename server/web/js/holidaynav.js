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
      top() {
        const sx = this.wide(), sy = 44 / 60, r = 7, step = 36 / sx, n = Math.ceil(1200 / step);
        let s = this.grad + '<style>.pcb{animation:pcchase 1.2s steps(1) infinite}@keyframes pcchase{0%{opacity:1}33%{opacity:.4}66%{opacity:1}}</style>'
          + '<linearGradient id="pcStrip" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6E1016"/><stop offset=".5" stop-color="#9E2129"/><stop offset="1" stop-color="#6E1016"/></linearGradient>'
          + `<rect width="1200" height="${60 * .78}" fill="url(#pcStrip)"/><rect y="${60 * .78 - 3 / sy}" width="1200" height="${3 / sy}" fill="#F6C744"/>`;
        for (let i = 0; i <= n; i++) {
          const x = i * step + step / 2, y = 60 * .78 / 2;
          s += `<g class="pcb" style="animation-delay:${-(i % 3) * .4}s"><ellipse cx="${x}" cy="${y}" rx="${r * 2.4 / sx}" ry="${r * 2.4 / sy}" fill="url(#pcGlow)"/>`
            + `<ellipse cx="${x}" cy="${y}" rx="${r / sx}" ry="${r / sy}" fill="url(#pcBulb)"/></g>`;
        }
        return s;
      },
      bottom() {
        const sx = this.wide(), sy = 36 / 40, h = new Date().getHours(), full = h < 12 ? .55 : h < 17 ? .78 : 1, r0 = 9;
        // one puff drawn once (radius 1), then placed many times
        let seed = 11, s = this.grad + `<defs><g id="pcP">${this.puff(0, 0, 1, 1, 1, 1)}</g></defs>`;
        const rnd = () => (seed = (seed * 9301 + 49297) % 233280) / 233280;
        for (let row = 0; row < 3; row++) {
          const count = Math.floor((1200 * sx) / (r0 * 2.1) * (1.15 - row * .3) * full);
          const y = 40 - (r0 * .55 + row * r0 * 1.15 * full) / sy;
          for (let i = 0; i < count; i++) {
            const x = rnd() * 1200, r = r0 * (.8 + rnd() * .45), yy = y - rnd() * r0 * .4 / sy, f = rnd() < .5 ? 1 : -1;
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
    make("starwars", is("swOn"), true, ["#0B0B14", "#05050A"], ["#FFE81F", "#4FC3F7", "#E53935"], "Star Wars Day pick", { pickWords: ["Star Wars"] }),
    make("stpatricks", is("spOn"), true, ["#0F2A17", "#08170D"], ["#2E9E5B", "#7BD389", "#E8C15A"], "St. Patrick's pick", { countdown: cd(() => B.sp), pickTags: ["ireland"], pickWords: ["Irish"] }),
    make("july4", is("j4On"), true, ["#101A33", "#0A0F1F"], ["#D7263D", "#FFFFFF", "#3F6FD8"], "4th of July pick", { countdown: cd(() => B.j4), pickWords: ["Independence Day"] }),
    make("newyear", is("nyOn"), true, ["#141A2E", "#0B0E1A"], ["#E8C15A", "#C9D0E0"], "New Year's pick", { countdown: cd(() => B.ny), pickTags: ["new year's eve"], pickWords: ["New Year"] }),
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
