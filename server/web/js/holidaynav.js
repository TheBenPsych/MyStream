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
    for (let i = 0; i < 7; i++) clouds += cloud(i * 190 + h(i + 40) * 60 - 20, 7 + h(i + 80) * 3, 0.55 + h(i) * 0.15);
    let birds = "";
    [[0, 0], [-16, -5], [-16, 5], [-32, -10], [-32, 10]].forEach(([x, y], k) => {
      birds += '<path class="edn-flap" style="animation-delay:-' + f(k * 0.17) + 's" d="M' + (x - 6) + " " + y + "q3 -4 6 0q3 -4 6 0" + '" stroke="#E8F3FF" stroke-width="1.8" fill="none" stroke-linecap="round"/>';
    });
    const top = '<g data-fit="xMidYMin slice"><style>'
      + ".edn-drift{animation:edn-drift 40s ease-in-out infinite alternate}@keyframes edn-drift{to{transform:translateX(-40px)}}"
      + ".edn-flock{animation:edn-flock 24s linear infinite}@keyframes edn-flock{0%{transform:translate(-60px,20px)}75%{transform:translate(1260px,12px)}100%{transform:translate(1260px,12px)}}"
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
    const bottom = '<g data-fit="xMidYMax slice"><style>'
      + ".edn-sw{transform-box:fill-box;transform-origin:50% 100%;animation:edn-sw 4s ease-in-out infinite}.edn-sw.b{animation-delay:-1.3s}.edn-sw.c{animation-delay:-2.6s;animation-duration:5s}@keyframes edn-sw{50%{transform:skewX(-5deg)}}"
      + ".edn-bee{animation:edn-bee 24s ease-in-out infinite}@keyframes edn-bee{0%{transform:translate(40px,22px)}25%{transform:translate(330px,19px)}50%{transform:translate(620px,23px)}75%{transform:translate(900px,19px)}100%{transform:translate(1180px,22px)}}"
      + ".edn-wing{transform-box:fill-box;transform-origin:50% 100%;animation:edn-flap .15s linear infinite}@keyframes edn-flap{50%{transform:scaleY(-.6)}}"
      + "@media (prefers-reduced-motion:reduce){.edn-sw,.edn-bee,.edn-wing{animation:none}}</style>"
      + '<g class="edn-sw a">' + grass[0] + "</g>" + flowers + '<g class="edn-sw b">' + grass[1] + '</g><g class="edn-sw c">' + grass[2] + "</g>" + bee + "</g>";
    return {
      top,
      bottom,
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

  // Most important first: when two are on at once the first wins (Friday the 13th takes over any season look,
  // Earth Day comes after Easter week)
  const ALL = [
    make("friday13", is("f13On"), true, ["#141414", "#0A0A0A"], ["#9BE15D", "#C9D0E0"], "Friday the 13th pick", { pickWords: ["Friday the 13th"], pickTags: ["slasher"] }),
    make("videogames", is("vgOn"), true, ["#1A1030", "#0E0819"], ["#FF6F91", "#3DD6C6", "#FFE14D", "#7AA7FF"], "Game Day pick", { pickTags: ["video game"], pickWords: ["Game"] }),
    make("earthday", () => is("edOn")() && !is("eaOn")(), true, ["#0E2C44", "#0B1A26"], ["#A8DCF7", "#A8DCF7", "#7FC8F2", "#56BDEB", "#7CD36B", "#8FD86F", "#B5E37A", "#E8F5A0", "#7CD36B"], "Earth Day pick", {
      pickTags: ["nature", "environmentalism", "wildlife", "nature documentary"], pickWords: ["Earth", "Planet"],
      icons: ed.icons, topTrim: ed.top, bottomTrim: ed.bottom,
      countdown: () => { try { const d = window.BenflixEarth ? window.BenflixEarth.day(new Date()) : 0; return d ? "Earth week · day " + d + " of 7 · Planet Earth picks on Movies and Shows" : ""; } catch (e) { return ""; } },
    }),
    make("halloween", is("hwOn"), true, ["#24140A", "#120A05"], ["#FF8A1F", "#8E44AD", "#7CFC00"], "Halloween pick", { countdown: cd(() => B.hw), pickTags: ["halloween"], pickWords: ["Halloween"] }),
    make("popcorn", is("pcOn"), true, ["#2A220E", "#17130A"], ["#F5D76E", "#D7263D", "#FFFFFF"], "Popcorn Day pick"),
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
  // Art can ask not to be stretched with data-fit="xMidYMax slice" (round clouds stay round on wide screens)
  const fit = (art) => (/data-fit="([^"]+)"/.exec(art) || [])[1];
  B.holidaySvg = (art, box, cls) => (art ? `<svg class="${cls || ""}" viewBox="${box}" preserveAspectRatio="${fit(art) || (cls && cls.includes("trim") ? "none" : "xMidYMid meet")}" aria-hidden="true">${art}</svg>` : "");
})();
