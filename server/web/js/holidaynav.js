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
    // St. Patrick's Day: charms for the door icons, bunting and a shamrock patch on Home (same as the app's StPatricksNav.kt)
    make("stpatricks", is("spOn"), true, ["#0E2B1D", "#081710"], ["#3DBE6C", "#E2B33C", "#F3F0E4"], "St. Patrick's pick", Object.assign({ countdown: cd(() => B.sp), pickTags: ["ireland"], pickWords: ["Irish", "Ireland", "Brooklyn", "The Quiet Man", "The Commitments", "Sing Street", "The Secret of Kells", "Song of the Sea", "Wolfwalkers"] }, (() => {
      const G = "#E2B33C", GD = "#8A6414", CL = "#3DBE6C", CLL = "#5FD889", EM = "#16603A", EMD = "#0B3A22", CR = "#F3F0E4";
      let seed = 7;
      const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
      const leaf = (fill, vein) => `<path d="M0 0C-7 -4 -11 -12 -6 -16.5C-2.5 -19 0 -16 0 -13.5C0 -16 2.5 -19 6 -16.5C11 -12 7 -4 0 0Z" fill="${fill}" stroke="${EMD}" stroke-width=".9"/>${vein ? '<path d="M0 -1.5V-12M-3.5 -14q-2 1.5 -2.5 4" stroke="#fff" stroke-opacity=".4" stroke-width=".85" fill="none" stroke-linecap="round"/>' : ""}`;
      // a shamrock whose stem meets the ground at x,y (the outer group places it, the inner one sways)
      const shamrock = (x, y, s, lean, fill, vein, sway) => `<g transform="translate(${x.toFixed(1)} ${y}) scale(${s.toFixed(2)})"><g class="${sway ? "spn-grass" : ""}" style="animation-delay:-${(rnd() * 4).toFixed(2)}s"><g transform="rotate(${lean.toFixed(1)})"><path d="M0 0C2 -8 -1 -14 0 -20" stroke="${EM}" stroke-width="2" fill="none" stroke-linecap="round"/><g transform="translate(0 -20)">${[0, 120, 240].map((a) => `<g transform="rotate(${a})">${leaf(fill, vein)}</g>`).join("")}<circle r="1.6" fill="${EM}"/></g></g></g></g>`;
      const css = "<style>.spn-sway{transform-box:fill-box;transform-origin:50% 0;animation:spnSway 3.6s ease-in-out infinite}@keyframes spnSway{0%,100%{transform:rotate(-3deg)}50%{transform:rotate(3deg)}}.spn-grass{transform-box:fill-box;transform-origin:50% 100%;animation:spnGrass 4.4s ease-in-out infinite}@keyframes spnGrass{0%,100%{transform:rotate(-2.5deg)}50%{transform:rotate(2.5deg)}}html.sp .sp-trim.top{right:auto;max-width:none;height:max(34px,5vw);width:calc(max(34px,5vw) * 20)}html.sp .sp-trim.bottom{max-width:none;height:max(30px,3.4vw);width:calc(max(30px,3.4vw) * 30)}html.sp .home{overflow-x:clip}</style>";
      // Bunting: green, white and gold cloth flags in swags across the top (1200 x 60). The trims keep their shape on
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
      // Shamrock patch along the bottom (drawn 1920 x 64, shrunk to the kit's 1200 x 40). Each shamrock and flower is
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
        for (let x = 4; x < W; x += 12 + rnd() * 8) back += put("spnBack", x, 64, (0.55 + rnd() * 0.5) * 0.9, -25 + rnd() * 50, false);
        for (let x = 40; x < W; x += 90 + rnd() * 80) fl += put("spnFlower", x, 66, 0.55 + rnd() * 0.25, 0, true);
        for (let x = 10; x < W; x += 16 + rnd() * 12) front += put("spnFront", x, 66, 0.6 + rnd() * 0.55, -20 + rnd() * 40, true);
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
