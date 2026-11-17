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
      live: `<g>${heart(3, 0, 26, "url(#vlFoil)", 'stroke="#8E0F2E" stroke-width=".6"')}<ellipse cx="10.5" cy="7.5" rx="2.6" ry="3.6" fill="#fff" opacity=".55" transform="rotate(-30 10.5 7.5)"/><path d="M15.2 22.6l.8 1.4.8-1.4z" fill="#B11F3F"/><animateTransform attributeName="transform" type="translate" values="0 -.7;0 .7;0 -.7" dur="3.9s" repeatCount="indefinite"/></g><path d="M16 24c-2 2 2 3 0 5s1.5 2 .5 3" stroke="#F4E9D8" stroke-width=".9" fill="none"/>`,
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

    // Home's trims are stretched to the page's width; the style keeps them in shape (only while this one shows)
    const keep = `<style>.sp-trim.top:has(.vl-top){height:auto;width:max(100%,900px);aspect-ratio:20/1}.sp-trim.bottom:has(.vl-bot){height:auto;aspect-ratio:30/1}</style>`;
    let top = `${keep}<g class="vl-top"><path d="M0 4 Q110 34 220 4 Q330 34 440 4 Q550 34 660 4 Q770 34 880 4 Q990 34 1100 4 Q1210 34 1320 4" stroke="#C9A47A" stroke-width="1.6" fill="none"/>`;
    const cols = [ROSE, PALE, BLUSH, DEEP];
    let k = 0;
    for (let i = 0; i < 6; i++) {
      for (let j = 1; j < 6; j++) {
        const f = j / 6, x = i * 220 + 220 * f, y = 4 + 2 * (1 - f) * f * 30;
        const tilt = ((k * 37) % 15) - 7;
        top += `<g transform="translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(${tilt})"><g><path d="M0 0v4" stroke="#C9A47A" stroke-width="1.2"/>${heart(-12, 2, 24, cols[k % 4], 'stroke="rgba(0,0,0,.18)" stroke-width=".8"')}<animateTransform attributeName="transform" type="rotate" values="-4;4;-4" dur="${(4 + (k % 3) * 0.6).toFixed(1)}s" begin="-${(k * 0.4).toFixed(1)}s" repeatCount="indefinite"/></g></g>`;
        k++;
      }
    }
    top += "</g>";
    // bottom: viewBox 1200 x 40
    const bottom = `${keep}${defs}<g class="vl-bot"><rect x="0" y="13" width="1200" height="6" fill="url(#vlSatin)"/><path d="M0 14.5h1200" stroke="#FFB0BD" stroke-width=".8" opacity=".6"/>`
      + `<g transform="translate(1100 16) scale(.5)"><path d="M-4 4l-22 30 10-2 4 8 16-30z" fill="${DEEP}"/><path d="M4 4l20 30-10-2-4 8-14-30z" fill="${DEEP}"/><path d="M0 0c-30-26-62-18-58 4s34 18 58-4z" fill="url(#vlSatin)" stroke="#8E0F2E" stroke-width="2.4"/><path d="M0 0c30-26 62-18 58 4s-34 18-58-4z" fill="url(#vlSatin)" stroke="#8E0F2E" stroke-width="2.4"/><rect x="-9" y="-9" width="18" height="18" rx="5" fill="${ROSE}" stroke="#8E0F2E" stroke-width="2.4"/></g></g>`;
    const countdown = () => {
      const v = B.val; const d = now();
      if (!v) return "";
      return v.variant(d) === "day" ? "Happy Valentine’s Day" : v.countdown(d);
    };
    return { icons, topTrim: top, bottomTrim: bottom, countdown };
  })();

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
    make("valentine", is("valOn"), false, ["#3A1226", "#200A15"], ["#E8405E", "#A8284F"], "Valentine's pick", { icons: VAL.icons, topTrim: VAL.topTrim, bottomTrim: VAL.bottomTrim, countdown: VAL.countdown, pickTags: ["valentine's day"], pickWords: ["Valentine", "Princess Bride", "Notting Hill", "Harry Met Sally"] }),
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
