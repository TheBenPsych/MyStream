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

  // ---------- Christmas art (approved "Classic big bulbs", sheet nav-13-christmas) ----------
  // Door icons are small full-colour drawings on a 48 grid (drawn at half size into the 24 box); a small gold
  // snowflake marks the icon you're on (no hanging bulb: it didn't attach to every kind of button); big glass
  // bulbs hang across the top of Home and soft snow lies along the bottom.
  const XMAS = (() => {
    const g = (art) => `<g transform="scale(.5)">${art}</g>`;
    const PAD = "M7.2 4.8h9.6c2.4 0 4 1.5 4.6 3.9l1.3 6.6c.5 2.6-1.1 4.4-3.1 4.4-1.4 0-2.3-.8-3-1.9l-1.1-1.8H8.5l-1.1 1.8c-.7 1.1-1.6 1.9-3 1.9-2 0-3.6-1.8-3.1-4.4l1.3-6.6c.6-2.4 2.2-3.9 4.6-3.9z";
    const BELL = "M0 0c-6.5 0-8.5 5.5-8.5 12l-3 6.5h23l-3-6.5c0-6.5-2-12-8.5-12z";
    const icons = {
      movies: g('<g transform="rotate(-12 8 16)"><rect x="6" y="8" width="36" height="7.5" rx="1.6" fill="#1b1b1f"/><path d="M12 8l5 7.5M20 8l5 7.5M28 8l5 7.5M36 8l5 7.5" stroke="#f4f4f4" stroke-width="2.8"/></g><rect x="7" y="18" width="34" height="24" rx="3" fill="#C8102E"/><rect x="7" y="35" width="34" height="7" rx="3" fill="#9E0B23"/><rect x="21" y="18" width="6" height="24" fill="#F2C14E"/><rect x="7" y="27" width="34" height="5" fill="#F2C14E"/><circle cx="24" cy="29.5" r="3.4" fill="#FFE08A"/>'),
      shows: g('<path d="M16 40l-3 5M32 40l3 5" stroke="#145234" stroke-width="3.2" stroke-linecap="round"/><rect x="5" y="14" width="38" height="26" rx="4" fill="#1F7A4D"/><rect x="9" y="18" width="30" height="18" rx="2" fill="#0D2A1B"/><path d="M24 20.5v13M18.4 23.7l11.2 6.6M18.4 30.3l11.2-6.6" stroke="#E8F4FF" stroke-width="1.9" stroke-linecap="round"/><path d="M27 15c2-9 10-12 15-8-3 1-5 4-5 8z" fill="#C8102E"/><rect x="25" y="12.5" width="15" height="5" rx="2.5" fill="#fff"/><circle cx="43" cy="7.5" r="3.2" fill="#fff"/>'),
      live: g('<path d="M24 19v8" stroke="#F2C14E" stroke-width="2.6"/><circle cx="24" cy="29" r="3.8" fill="#F2C14E"/><path d="M17 22a10 10 0 000 14M31 22a10 10 0 010 14M11.5 16.5a18 18 0 000 25M36.5 16.5a18 18 0 010 25" stroke="#2FA36B" stroke-width="3.4" fill="none" stroke-linecap="round"/><circle cx="9.3" cy="29" r="2.4" fill="#FF3B3B"/><circle cx="38.7" cy="29" r="2.4" fill="#3FA9FF"/><circle cx="14.4" cy="29" r="2" fill="#FFC93C"/><circle cx="33.6" cy="29" r="2" fill="#FF3B3B"/><path d="M24 2.5l2.7 5.7 6.2.7-4.6 4.2 1.3 6.1-5.6-3.1-5.6 3.1 1.3-6.1-4.6-4.2 6.2-.7z" fill="#FFD447"/>'),
      games: g(`<clipPath id="xmasPadClip"><path transform="scale(2)" d="${PAD}"/></clipPath><g clip-path="url(#xmasPadClip)"><rect width="48" height="48" fill="#fff"/><path d="M-8 30L22-8M2 36L36-8M14 44L48 2M26 50L58 10M38 54L62 26" stroke="#D7263D" stroke-width="4.5"/></g><path transform="scale(2)" d="${PAD}" fill="none" stroke="#8E0E22" stroke-width=".45"/><rect x="11.5" y="17" width="5" height="12" rx="1.2" fill="#1F7A4D"/><rect x="8" y="20.5" width="12" height="5" rx="1.2" fill="#1F7A4D"/><circle cx="33" cy="19.5" r="2.5" fill="#1F7A4D"/><circle cx="37" cy="24.8" r="2.5" fill="#1F7A4D"/>`),
      music: g([[16, -16, "#E9B53B"], [32, 16, "#F2C14E"]].map(([x, d, c]) => `<g transform="rotate(${d} ${x} 18) translate(${x} 12)"><path d="${BELL}" fill="${c}"/><path d="M-6 12c0-5 1.5-9 5-10" stroke="#FFF0BE" stroke-width="2" fill="none" stroke-linecap="round"/><circle cy="20.5" r="3" fill="#B8862A"/></g>`).join("") + '<path d="M24 9c-6-7-12-3-7 2zM24 9c6-7 12-3 7 2z" fill="#C8102E"/><path d="M24 9l-4 9M24 9l4 9" stroke="#A50C25" stroke-width="2.4" stroke-linecap="round"/><circle cx="24" cy="9.5" r="2.8" fill="#E3223F"/>'),
      books: g('<path d="M9 8a3 3 0 013-3h26v34H12a3 3 0 00-3 3z" fill="#1F7A4D"/><path d="M9 42a3 3 0 013-3h26v4H12z" fill="#F3EBDD"/><path d="M9 42V8" stroke="#145234" stroke-width="2"/><path d="M31 5v22l3.5-3 3.5 3V5z" fill="#C8102E"/><path d="M14 19c3-5 8-5 10-1-3.5 0-5 2.2-5 4.5-1.8-2-3.6-3-5-3.5zM26 19c-1-5 2.5-8 7-6.5-2.6 1.6-3 4-2.2 6-2-.6-3.6-.2-4.8.5z" fill="#3DBB6E"/><circle cx="23.5" cy="23" r="2.4" fill="#E3223F"/><circle cx="27" cy="24.5" r="2.4" fill="#C8102E"/><circle cx="24.6" cy="26.8" r="2.2" fill="#E3223F"/>'),
    };
    // a small gold snowflake (viewBox 0 0 12 18), twinkling softly
    let flake = '<circle cx="6" cy="12" r="6" fill="#FFD976" opacity=".25"/>';
    for (let i = 0; i < 6; i++) {
      const a = (i * 60 - 90) * Math.PI / 180, x = 6 + Math.cos(a) * 5.2, y = 12 + Math.sin(a) * 5.2;
      flake += `<path d="M6 12L${x.toFixed(2)} ${y.toFixed(2)}" stroke="#FFD976" stroke-width=".9" stroke-linecap="round"/>`;
    }
    const mark = `<g class="hol-tw">${flake}</g>`;
    // big glass bulbs on a dark wire: drawn for the screen's own width, so they never stretch
    // Christmas Day itself: the lights twinkle faster, all colours at once
    const xday = () => { const d = new Date(); return d.getMonth() === 11 && d.getDate() === 25; };
    const C9 = ["#FF3B3B", "#2FD06B", "#FFC93C", "#3FA9FF", "#FF8A2B"];
    const bulb = (x, y, c, k) => `<g transform="translate(${x.toFixed(1)} ${y.toFixed(1)})"><circle class="hol-tw" style="animation-delay:${((k * 0.37) % 2.4).toFixed(2)}s" cy="15" r="13" fill="${c}" opacity=".45" filter="url(#xmasGlow)"/><rect x="-2.6" width="5.2" height="6.4" rx="1.2" fill="#1c3524"/><path d="M0 5C-5.4 8-6.1 16 0 23.5 6.1 16 5.4 8 0 5z" fill="${c}"/><path d="M0 5C-5.4 8-6.1 16 0 23.5 6.1 16 5.4 8 0 5z" fill="url(#xmasGlass)"/><ellipse cx="-2" cy="11" rx="1" ry="2.6" fill="#fff" opacity=".55"/></g>`;
    const topTrim = (cls) => {
      const W = Math.max(600, Math.round(window.innerWidth || 1200)), H = 44, n = Math.max(1, Math.round(W / 150)), step = W / n, top = 3, sag = 10;
      let out = '<defs><filter id="xmasGlow" x="-1" y="-1" width="3" height="3"><feGaussianBlur stdDeviation="4"/></filter><radialGradient id="xmasGlass" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#fff" stop-opacity=".45"/><stop offset=".4" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".35"/></radialGradient></defs>';
      let wire = "";
      for (let i = 0; i < n; i++) wire += `M${(i * step).toFixed(1)} ${top}Q${((i + 0.5) * step).toFixed(1)} ${top + sag * 2} ${((i + 1) * step).toFixed(1)} ${top}`;
      out += `<path d="${wire}" stroke="#16251b" stroke-width="1.8" fill="none"/>`;
      let k = 0;
      for (let i = 0; i < n; i++) [0.17, 0.5, 0.83].forEach((t) => { out += bulb(i * step + step * t, top + sag * 4 * t * (1 - t) - 1, C9[k % 5], k); k++; });
      for (let i = 0; i <= n; i++) out += `<circle cx="${(i * step).toFixed(1)}" cy="${top}" r="2.2" fill="#2a3b2f"/>`;
      return `<svg class="${cls || ""}${xday() ? " xday" : ""}" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMinYMin slice" aria-hidden="true" style="overflow:visible">${out}</svg>`;
    };
    // soft snow drifts (fine to stretch)
    const drift = (W, base, amp, seed) => {
      const pts = [];
      for (let i = 0; i <= 24; i++) { const x = W * i / 24; pts.push([x, base - amp * (0.55 + 0.45 * Math.sin(x / W * Math.PI * 3 + seed)) - amp * 0.35 * Math.sin(x / W * Math.PI * 9 + seed * 2)]); }
      let d = `M0 40L0 ${pts[0][1].toFixed(1)}`;
      for (let i = 1; i < pts.length; i++) { const [x0, y0] = pts[i - 1], [x1, y1] = pts[i]; d += `Q${(x0 + (x1 - x0) / 2).toFixed(1)} ${(Math.min(y0, y1) - amp * 0.25).toFixed(1)} ${x1.toFixed(1)} ${y1.toFixed(1)}`; }
      return d + `L${W} 40Z`;
    };
    let snow = '<defs><linearGradient id="xmasSnow" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset=".55" stop-color="#e9f2fb"/><stop offset="1" stop-color="#bcd3ea"/></linearGradient><linearGradient id="xmasSnow2" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#dfeaf6"/><stop offset="1" stop-color="#a9c2dc"/></linearGradient></defs>';
    snow += `<path d="${drift(1200, 22, 9, 1.3)}" fill="url(#xmasSnow2)" opacity=".9"/><path d="${drift(1200, 31, 8, 0.2)}" fill="url(#xmasSnow)"/>`;
    for (let i = 0; i < 30; i++) snow += `<circle cx="${((i * 137) % 997) / 997 * 1200}" cy="${(25 + ((i * 61) % 31) / 31 * 13).toFixed(1)}" r="1.2" fill="#fff" opacity=".8"/>`;
    return { icons, mark, topTrim, bottomTrim: snow };
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
    make("newyear", is("nyOn"), true, ["#141A2E", "#0B0E1A"], ["#E8C15A", "#C9D0E0"], "New Year's pick", { countdown: cd(() => B.ny), pickTags: ["new year's eve"], pickWords: ["New Year"] }),
    make("thanksgiving", is("tgOn"), false, ["#2A1A0E", "#170E08"], ["#E07A2E", "#C9A227", "#8B3A1E"], "Thanksgiving pick", { pickTags: ["thanksgiving"], pickWords: ["Thanksgiving"] }),
    make("valentine", is("valOn"), false, ["#2A0F1A", "#170810"], ["#FF5C8A", "#D7263D", "#FFB3C6"], "Valentine's pick", { countdown: cd(() => B.val), pickTags: ["valentine's day"], pickWords: ["Valentine"] }),
    make("easter", is("eaOn"), true, ["#1F2433", "#121522"], ["#F7B2D9", "#B8E0D2", "#FFE29A", "#C3B1E1"], "Easter pick", { countdown: cd(() => B.ea), pickTags: ["easter"], pickWords: ["Easter"] }),
    // Christmas: Classic big bulbs (approved 2026-11-17), art below (XMAS), the same as the app's ChristmasNav.kt
    make("christmas", is("xmasOn"), false, ["#0F2A1D", "#0B1912"], ["#D7263D", "#2E9E5B", "#E8C15A"], "Christmas pick", { countdown: () => { const d = new Date(); return d.getMonth() === 11 && d.getDate() === 25 ? "Merry Christmas!" : cd(() => B.xmas)(); }, pickTags: ["christmas", "santa claus"], pickWords: ["Christmas", "Santa", "Grinch", "Elf", "Home Alone", "Polar Express", "Klaus"], icons: XMAS.icons, mark: XMAS.mark, topTrim: XMAS.topTrim, bottomTrim: XMAS.bottomTrim }),
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
