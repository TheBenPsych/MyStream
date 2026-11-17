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
