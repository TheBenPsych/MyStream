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
        const fs = Math.min(14, Math.round(h * (top ? .3 : .34)));
        const t = (x, y, fill, s, anchor) => `<text x="${x}" y="${y}" fill="${fill}" font-size="${fs}" text-anchor="${anchor || "start"}" style="${PX};paint-order:stroke;stroke:rgba(0,0,0,.5);stroke-width:2px">${s}</text>`;
        let out = `<g class="vgh" data-k="${top ? "top" : "bottom"}" data-w="${w}"></g>`;
        if (top) {
          const band = Math.round(h * .64), y = Math.round(band / 2 + fs / 2);
          out += `<rect width="${w}" height="${band}" fill="rgba(10,6,24,.88)"/><rect y="${band}" width="${w}" height="2" fill="rgba(63,224,255,.6)"/>`;
          out += t(18, y, "#FF5FB0", "1UP") + `<text class="vgs" x="${18 + fs * 4}" y="${y}" fill="#fff" font-size="${fs}" style="${PX}">012900</text>`;
          if (w > 900) out += t(w / 2, y, "#FFD45A", 'HI-SCORE <tspan fill="#fff">091200</tspan>', "middle");
          out += t(w - 18, y, "#fff", "×12", "end") + `<circle cx="${w - 18 - fs * 3.9}" cy="${y - fs * .42}" r="${fs * .55}" fill="#FFD45A" stroke="#C48A1E" stroke-width="2"/>`;
          out += t(w - 18 - fs * 5.2, y, "#FF5FB0", "♥♥♥", "end");
        } else {
          const top0 = Math.round(h * .18), y = Math.round(top0 + (h - top0) / 2 + fs / 2);
          out += `<rect y="${top0}" width="${w}" height="${h - top0}" fill="rgba(10,6,24,.88)"/><line x1="0" x2="${w}" y1="${top0}" y2="${top0}" stroke="rgba(63,224,255,.65)" stroke-width="2" stroke-dasharray="7 5"/>`;
          out += `<g>${t(w / 2, y, "#FFD45A", "PRESS START", "middle")}<animate attributeName="opacity" values="1;0" keyTimes="0;.62" dur="1s" calcMode="discrete" repeatCount="indefinite"/></g>`;
          if (w > 620) out += t(18, y, "#AAB0C4", "LEVEL 09·12") + t(w - 18, y, "#AAB0C4", "CREDIT 01", "end");
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
    make("earthday", () => is("edOn")() && !is("eaOn")(), true, ["#12261A", "#0A160F"], ["#5BD18B", "#3FA7D6", "#8BC34A"], "Earth Day pick", { pickTags: ["nature", "environment"], pickWords: ["Earth"] }),
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
  B.holidaySvg = (art, box, cls) => (art ? `<svg class="${cls || ""}" viewBox="${box}" preserveAspectRatio="${cls && cls.includes("trim") ? "none" : "xMidYMid meet"}" aria-hidden="true">${art}</svg>` : "");
})();
