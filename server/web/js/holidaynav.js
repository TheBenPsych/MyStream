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
    // Wide and cut to the screen (slice), so the eggs keep their shape at any width: 2 units = 1 pixel. A little
    // taller than the usual trim box, so the eggs and tulips are big enough to see from the couch
    const wide = (h, art) => `<svg viewBox="0 0 4800 ${h}" preserveAspectRatio="xMinYMin slice" style="height:${h / 2}px" aria-hidden="true">${plain(art)}</svg>`;
    let top = sway, rope = "M0 8";
    const step = 300, pins = [];
    for (let x = 0; x < 4800; x += step) { rope += `Q${x + step / 2} ${8 + 56} ${x + step} 8`; pins.push(x); }
    top += `<path d="${rope}" stroke="#C25A86" stroke-width="9" fill="none"/><path d="${rope}" stroke="#F28DB2" stroke-width="6" fill="none"/><path d="${rope}" stroke="#FFD0E2" stroke-width="1.6" fill="none" transform="translate(0 -1.5)"/>`;
    pins.forEach((x, i) => {
      const m = x + step / 2, e = EGGS[i % 6];
      top += `<g class="eaSw" style="animation-delay:${(-i * 0.7).toFixed(1)}s"><path d="M${m} 36V52" stroke="#C25A86" stroke-width="3"/>${K.egg(m, 90, 26, 36, e[0], e[1], e[2])}</g>`;
      top += `<circle cx="${x}" cy="9" r="6" fill="#F4F1EA" stroke="#B8B0A0"/><circle cx="${x - 2}" cy="7" r="2" fill="#fff"/>`;
    });
    // Spring grass with tulips and eggs half hidden in it
    let seed = 3;
    const rnd = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280; };
    const grass = (yb, h) => { let s = ""; for (let x = 0; x < 4800; x += 5) { const hh = h * (0.55 + rnd() * 0.6), lean = (rnd() - 0.5) * 18; s += `<path d="M${x} ${yb}q${(lean * 0.3).toFixed(1)} ${(-hh * 0.6).toFixed(1)} ${lean.toFixed(1)} ${(-hh).toFixed(1)}" stroke="${["#3E8A45", "#5AAE52", "#7CCB6A", "#4FA052"][(x / 5) % 4]}" stroke-width="2.6" fill="none" stroke-linecap="round"/>`; } return s; };
    let bottom = `<rect x="0" y="90" width="4800" height="18" fill="#24502F"/>` + grass(108, 60);
    for (let i = 0, x = 80; x < 4800; i++, x += 190) {
      if (i % 2) { const e = EGGS[i % 6]; bottom += K.egg(x, 78, 22, 29, e[0], e[1], e[2]); }
      else bottom += `<path d="M${x} 56V108" stroke="#3E8A45" stroke-width="4"/>` + K.tulip(x, 60, ["#FF6F91", "#FFD23F", "#FFFFFF", "#C9B2F5"][(i / 2) % 4], (i % 5 - 2) * 4, 1.5);
    }
    bottom += grass(108, 30);
    return Object.assign(base, { icons, mark, topTrim: wide(132, top), bottomTrim: wide(108, bottom) });
  }

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
    make("thanksgiving", is("tgOn"), false, ["#2A1A0E", "#170E08"], ["#E07A2E", "#C9A227", "#8B3A1E"], "Thanksgiving pick", { pickTags: ["thanksgiving"], pickWords: ["Thanksgiving"] }),
    make("valentine", is("valOn"), false, ["#2A0F1A", "#170810"], ["#FF5C8A", "#D7263D", "#FFB3C6"], "Valentine's pick", { countdown: cd(() => B.val), pickTags: ["valentine's day"], pickWords: ["Valentine"] }),
    // Easter (approved 2026-11-17, thread "Fit Easter theme to new navigation", direction A "Easter tree ribbon"):
    // meadow-green rail, the door icons become the Easter tree charms, a painted egg on a ribbon hangs by the icon
    // you're on, a pink satin ribbon swags across the top of Home with painted eggs swaying, and spring grass with
    // tulips and eggs runs along the bottom. Kids too. The drawings come from easter.js (BenflixEaster.kit).
    make("easter", is("eaOn"), true, ["#122019", "#0B150F"], ["#FFB8D2", "#BFE6FF", "#FFF1A6", "#C9F2C0", "#E3D5FF", "#FFD9B8"], "Easter pick", easterNav()),
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
  // Art that is already a whole <svg> (Easter's trims keep their shape at any width) only gets the class
  B.holidaySvg = (art, box, cls) => (art && art.startsWith("<svg") ? art.replace("<svg", `<svg class="${cls || ""}"`) : art ? `<svg class="${cls || ""}" viewBox="${box}" preserveAspectRatio="${cls && cls.includes("trim") ? "none" : "xMidYMid meet"}" aria-hidden="true">${art}</svg>` : "");
})();
