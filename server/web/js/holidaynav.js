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

  // ---------- Halloween and Friday the 13th art (approved 2026-11-17, sheets spooky-nav-1 and spooky-nav-2) ----------
  // The same drawings as the app's SpookyNav.kt. Icons are the approved door charms cut square with a thin pale
  // outline (img/hw/hwnav_*, img/f13/f13nav_*), drawn a little bigger than the line icons so they read from the couch.
  // Trims draw their own whole <svg> at the screen's width, so pumpkins, crows and the moon never stretch.
  const SPOOKY = (() => {
    const pic = (src) => `<image href="${src}" x="-3.6" y="-3.6" width="31.2" height="31.2" preserveAspectRatio="xMidYMid meet"/>`;
    const DOORS = ["movies", "shows", "live", "games", "music", "books"];
    const W = () => Math.max(600, Math.round(window.innerWidth || 1200));
    const wrap = (cls, w, h, body) => `<svg class="${cls || ""}" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMinYMin slice" aria-hidden="true" style="overflow:visible">${body}</svg>`;

    // Halloween: the approved charms, Kids get the friendly ones (approved hn-q-kids A)
    const hwIcons = {};
    DOORS.forEach((d) => Object.defineProperty(hwIcons, d, { enumerable: true, get: () => pic(`web/img/hw/hwnav_${B.me && B.me.kids ? "kids_" : ""}${d}.png`) }));
    // a little spider on its silk, bobbing (viewBox 0 0 12 18)
    let legs = "";
    for (const s of [-1, 1]) for (let k = 0; k < 4; k++) { const a = 8.5 + k * 1.3; legs += `<path d="M${6 + s * 1.5} ${a}L${6 + s * 4.6} ${(a - 1.2 + k * .9).toFixed(1)}" stroke="#1A1420" stroke-width=".9" stroke-linecap="round"/>`; }
    const hwMark = `<line x1="6" y1="-14" x2="6" y2="9" stroke="#E6E6F0" stroke-opacity=".9" stroke-width=".5"/><g>${legs}<ellipse cx="6" cy="11.7" rx="2.3" ry="2.7" fill="#1A1420"/><circle cx="6" cy="8.4" r="1.5" fill="#1A1420"/><circle cx="5.4" cy="8.2" r=".45" fill="#FF8A2A"/><circle cx="6.6" cy="8.2" r=".45" fill="#FF8A2A"/><circle cx="6" cy="11.6" r=".7" fill="#C0392B"/><animateTransform attributeName="transform" type="translate" values="0 0;0 4;0 0" dur="3s" repeatCount="indefinite"/></g>`;
    const jack = (x, y, w, k) => {
      const h = w * .78, f = (n) => n.toFixed(1);
      return `<g transform="translate(${f(x)} ${f(y)})"><circle r="${f(w * .95)}" fill="url(#hwGlow)" class="hwtw" style="animation-delay:${((k * .37) % 2.2).toFixed(2)}s"/><ellipse rx="${f(w / 2)}" ry="${f(h / 2)}" fill="#C85A10"/><ellipse cx="${f(-w * .14)}" rx="${f(w * .22)}" ry="${f(h * .48)}" fill="#F07A1E"/><ellipse cx="${f(w * .14)}" rx="${f(w * .22)}" ry="${f(h * .48)}" fill="#F59A3E"/><ellipse rx="${f(w * .14)}" ry="${f(h / 2)}" fill="#F07A1E"/><path d="M0 ${f(-h * .46)}L${f(w * .08)} ${f(-h * .68)}" stroke="#3E6B2A" stroke-width="${f(w * .09)}" stroke-linecap="round"/><g fill="#FFE07A"><path d="M${f(-w * .29)} ${f(-h * .02)}h${f(w * .18)}l${f(-w * .09)} ${f(-h * .2)}zM${f(w * .11)} ${f(-h * .02)}h${f(w * .18)}l${f(-w * .09)} ${f(-h * .2)}z"/><path d="M${f(-w * .3)} ${f(h * .1)}Q0 ${f(h * .42)} ${f(w * .3)} ${f(h * .1)}L${f(w * .18)} ${f(h * .16)}L${f(w * .08)} ${f(h * .1)}L${f(-w * .02)} ${f(h * .2)}L${f(-w * .12)} ${f(h * .1)}L${f(-w * .2)} ${f(h * .18)}z"/></g></g>`;
    };
    const web = (r) => {
      let o = "";
      for (let i = 0; i <= 6; i++) { const a = i * Math.PI / 12; o += `<path d="M0 0L${(r * Math.cos(a)).toFixed(1)} ${(r * Math.sin(a)).toFixed(1)}"/>`; }
      for (let ring = 1; ring <= 3; ring++) {
        const rr = r * ring / 3.4;
        let d = `M${rr} 0`;
        for (let i = 1; i <= 6; i++) { const a = i * Math.PI / 12, m = (i - .5) * Math.PI / 12; d += `Q${(rr * .82 * Math.cos(m)).toFixed(1)} ${(rr * .82 * Math.sin(m)).toFixed(1)} ${(rr * Math.cos(a)).toFixed(1)} ${(rr * Math.sin(a)).toFixed(1)}`; }
        o += `<path d="${d}"/>`;
      }
      return `<g stroke="#E6E6F0" stroke-opacity=".55" stroke-width="1.2" fill="none">${o}</g>`;
    };
    const hwDefs = '<defs><style>.hwtw{animation:hwtw 2.2s ease-in-out infinite}@keyframes hwtw{50%{opacity:.45}}</style><radialGradient id="hwGlow"><stop offset="0" stop-color="#F07A1E" stop-opacity=".45"/><stop offset="1" stop-color="#F07A1E" stop-opacity="0"/></radialGradient></defs>';
    const hwTop = (cls) => {
      const w = W(), n = Math.max(2, Math.round(w / 110)), gap = w / n, top = 2, sag = 12, pw = 22;
      let wire = `M0 ${top}`;
      for (let i = 0; i < n; i++) wire += `Q${((i + .5) * gap).toFixed(1)} ${top + sag * 2} ${((i + 1) * gap).toFixed(1)} ${top}`;
      let o = hwDefs + `<path d="${wire}" stroke="#2A1D14" stroke-width="2" fill="none"/>`;
      for (let i = 0; i < n; i++) { const x = (i + .5) * gap; o += `<path d="M${x.toFixed(1)} ${top + sag}v4" stroke="#2A1D14" stroke-width="1.5"/>` + jack(x, top + sag + 4 + pw * .4, pw, i); }
      o += web(64) + `<g transform="translate(${w} 0) scale(-1 1)">${web(64)}</g>`;
      return wrap(cls, w, 44, o);
    };
    const LEAF = "M10 1l2 5 4-2-1 5 5 1-4 3 2 4-5-1-1 4-2-3-2 3-1-4-5 1 2-4-4-3 5-1-1-5 4 2z";
    const hwBottom = (cls) => {
      const w = W(), h = 36, cols = ["#C2410C", "#E8A13A", "#8C2F14", "#F07A1E", "#B45309"];
      let o = hwDefs + `<defs><linearGradient id="hwBot" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4A2A78" stop-opacity="0"/><stop offset="1" stop-color="#4A2A78" stop-opacity=".55"/></linearGradient></defs><rect width="${w}" height="${h}" fill="url(#hwBot)"/>`;
      const count = Math.max(3, Math.floor(w / 60));
      for (let i = 0; i < count; i++) { const x = (i + .5) * w / count + (i % 2 ? 8 : -8); o += `<g transform="translate(${(x - 9).toFixed(1)} ${h - 19}) rotate(${i * 47} 9 9) scale(.9)"><path d="${LEAF}" fill="${cols[i % 5]}"/></g>`; }
      const jacks = Math.min(5, Math.max(2, Math.round(w / 300)));
      for (let i = 0; i < jacks; i++) o += jack((i + .5) * w / jacks + 20, h - 34 * .42, 34, i + 3);
      return wrap(cls, w, h, o);
    };

    // Friday the 13th: the approved lucky charms (Kids too), crows on the wire, a crooked fence in mist
    const f13Icons = {};
    DOORS.forEach((d) => { f13Icons[d] = pic(`web/img/f13/f13nav_${d}.png`); });
    const CROW = "M4 17q3-6 11-7 4-5 9-4.5 4 .8 5.5 4l6.5 1.2-6.6 1.8q-.4 5-5.4 8l-2.6 3.4h-2.2l.8-3q-6 .8-10.6-1.6L1.5 23l3.6-5.6z";
    const crowArt = `<path d="${CROW}" fill="none" stroke="#B9A6FF" stroke-opacity=".3" stroke-width="3.2"/><path d="${CROW}" fill="#16121E" stroke="#D6CCF0" stroke-width="1.1" stroke-linejoin="round"/><path d="M8 17l12 1" stroke="#2C2636" stroke-width="1.2"/><circle cx="27.4" cy="8.6" r="1.1" fill="#A6E36A"/><path d="M19.5 25.5V30M22.5 25.5V30" stroke="#8A80A0"/>`;
    const crow = (x, feet, w, flip, cls) => { const s = w / 40; return `<g transform="translate(${(x - 20 * s).toFixed(1)} ${(feet - 30 * s).toFixed(1)}) scale(${s.toFixed(3)})"><g class="${cls || ""}"><g${flip ? ' transform="translate(40 0) scale(-1 1)"' : ""}>${crowArt}</g></g></g>`; };
    const f13Mark = `<g transform="translate(-3.2 -2) scale(.45)"><g>${crowArt}<animateTransform attributeName="transform" type="translate" values="0 0;0 0;0 -7;0 0;0 0" keyTimes="0;.7;.75;.8;1" dur="5s" repeatCount="indefinite"/></g></g>`;
    const f13Top = (cls) => {
      const w = W(), h = 44, top = h * .78, sag = h * .15, y = (f) => top + sag * 4 * f * (1 - f), mr = 20, mx = w * .66, my = 22;
      let o = `<defs><radialGradient id="f13Moon"><stop offset="0" stop-color="#F4E7B8" stop-opacity=".45"/><stop offset="1" stop-color="#F4E7B8" stop-opacity="0"/></radialGradient><style>.f13hop{animation:f13hop 5s infinite}@keyframes f13hop{0%,70%,80%,100%{transform:translateY(0)}75%{transform:translateY(-8px)}}.f13turn{animation:f13turn 6s steps(1) infinite;transform-origin:20px 0}@keyframes f13turn{0%,60%{transform:none}61%,100%{transform:scaleX(-1)}}</style></defs>`;
      o += `<circle cx="${mx.toFixed(1)}" cy="${my}" r="${mr * 2}" fill="url(#f13Moon)"/><circle cx="${mx.toFixed(1)}" cy="${my}" r="${mr}" fill="#F4E7B8"/><circle cx="${(mx - mr * .38).toFixed(1)}" cy="${(my - mr * .22).toFixed(1)}" r="${mr * .2}" fill="#E2D29C"/><circle cx="${(mx + mr * .28).toFixed(1)}" cy="${(my + mr * .3).toFixed(1)}" r="${mr * .28}" fill="#E2D29C"/><circle cx="${(mx + mr * .34).toFixed(1)}" cy="${(my - mr * .5).toFixed(1)}" r="${mr * .11}" fill="#E2D29C"/>`;
      const wire = `M0 ${top}Q${w / 2} ${top + sag * 2} ${w} ${top}`;
      o += `<path d="${wire}" stroke="#0D0B10" stroke-width="3" fill="none"/><path d="${wire}" stroke="#8A80A0" stroke-width="1" fill="none"/>`;
      [.1, .26, .44, .62, .84].forEach((f, i) => { o += crow(w * f, y(f) + 1, 46, i % 2 === 1 && i !== 3, i === 1 ? "f13hop" : i === 3 ? "f13turn" : ""); });
      return wrap(cls, w, h, o);
    };
    const f13Bottom = (cls) => {
      const w = W(), h = 36, pw = 12, n = Math.ceil(w / pw) + 1;
      let o = `<defs><radialGradient id="f13Mist"><stop offset="0" stop-color="#C8C3DC" stop-opacity=".35"/><stop offset="1" stop-color="#C8C3DC" stop-opacity="0"/></radialGradient><style>.f13mist{animation:f13mist 12s ease-in-out infinite alternate}@keyframes f13mist{to{transform:translateX(30px)}}</style></defs>`;
      o += `<g class="f13mist"><ellipse cx="${w * .2}" cy="${h}" rx="${w * .3}" ry="${h * .9}" fill="url(#f13Mist)"/><ellipse cx="${w * .75}" cy="${h}" rx="${w * .3}" ry="${h * .9}" fill="url(#f13Mist)"/></g>`;
      o += `<rect y="${(h * .42).toFixed(1)}" width="${w}" height="2.5" fill="#3A3342"/><rect y="${(h * .78).toFixed(1)}" width="${w}" height="2.5" fill="#3A3342"/>`;
      for (let i = 0; i < n; i++) {
        const ph = h * (.55 + ((i * 7) % 11) / 11 * .4), r = ((i * 13) % 9) - 4, x = i * pw + pw / 2, a = pw * .42;
        o += `<path transform="rotate(${r} ${x} ${h})" d="M${(x - a).toFixed(1)} ${h}V${(h - ph).toFixed(1)}L${x} ${(h - ph - pw * .25).toFixed(1)}L${(x + a).toFixed(1)} ${(h - ph).toFixed(1)}V${h}z" fill="#2F2838" stroke="#7A6F8C" stroke-width=".8"/>`;
      }
      return wrap(cls, w, h, o);
    };
    return { hw: { icons: hwIcons, mark: hwMark, topTrim: hwTop, bottomTrim: hwBottom }, f13: { icons: f13Icons, mark: f13Mark, topTrim: f13Top, bottomTrim: f13Bottom } };
  })();

  // Most important first: when two are on at once the first wins (Friday the 13th takes over any season look,
  // Earth Day comes after Easter week)
  const ALL = [
    // Friday the 13th: Crows on the wire (approved 2026-11-17), night purple with green highlights; the first card is
    // the lucky line (a one-day holiday has nothing to count down); the pick is from Spooky but fun, never slashers
    make("friday13", is("f13On"), true, ["#1D1530", "#0D0A16"], ["#A6E36A", "#C9B8FF"], "Spooky but fun", Object.assign({ countdownKind: "Lucky you", countdown: () => B.f13LastLucky || "Spooky but fun films are on Movies today", pickWords: ["Ghostbusters", "Addams", "Goosebumps", "Scooby-Doo", "Beetlejuice", "Casper", "Coraline", "Hotel Transylvania", "Monster House", "ParaNorman", "Haunted Mansion", "Frankenweenie", "Corpse Bride", "Gremlins"], pickTags: [] }, SPOOKY.f13)),
    make("videogames", is("vgOn"), true, ["#1A1030", "#0E0819"], ["#FF6F91", "#3DD6C6", "#FFE14D", "#7AA7FF"], "Game Day pick", { pickTags: ["video game"], pickWords: ["Game"] }),
    make("earthday", () => is("edOn")() && !is("eaOn")(), true, ["#12261A", "#0A160F"], ["#5BD18B", "#3FA7D6", "#8BC34A"], "Earth Day pick", { pickTags: ["nature", "environment"], pickWords: ["Earth"] }),
    // Halloween: The charms come along (approved 2026-11-17), art above (SPOOKY), the same as the app's SpookyNav.kt
    make("halloween", is("hwOn"), true, ["#24123A", "#150B22"], ["#F07A1E", "#B07CFF"], "Halloween pick", Object.assign({ countdown: cd(() => B.hw), pickTags: ["halloween"], pickWords: ["Halloween"] }, SPOOKY.hw)),
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
  // A holiday can also give a function that draws its own whole <svg> (Christmas sizes its bulbs to the screen)
  B.holidaySvg = (art, box, cls) => (typeof art === "function" ? art(cls) : art ? `<svg class="${cls || ""}" viewBox="${box}" preserveAspectRatio="${cls && cls.includes("trim") ? "none" : "xMidYMid meet"}" aria-hidden="true">${art}</svg>` : "");
})();
