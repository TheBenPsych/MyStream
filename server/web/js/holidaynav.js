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

  /* ---------- 4th of July (approved 2026-11-17, sheet july4-nav-1-directions; the app's ui/theme/July4Nav.kt) ----------
     The rail and phone-tab icons in red, white and blue taken in turn, a twinkling gold star beside the one you're
     on, pleated fan bunting in swags across the top of Home, lit sparklers along the bottom, bigger fireworks over
     the bunting on the 4th from 9 PM, and "It's the 4th!" on the day. Kids too. */
  const july4 = () => {
    const RD = "#E0404F", RDK = "#9E1E2C", NV = "#24366E", CR = "#F4F0E8", CRS = "#CFC7B8";
    const f = (n) => Math.round(n * 10) / 10;
    const starD = (x, y, r, ri = 0.42) => { let d = ""; for (let k = 0; k < 10; k++) { const a = -Math.PI / 2 + k * Math.PI / 5, rr = k % 2 ? r * ri : r; d += (k ? "L" : "M") + f(x + Math.cos(a) * rr) + " " + f(y + Math.sin(a) * rr); } return d + "Z"; };
    const fan = (cx, top, r) => {
      let o = "";
      for (let i = 0; i < 10; i++) {
        const a0 = i * Math.PI / 10, a1 = (i + 1) * Math.PI / 10, am = (a0 + a1) / 2, c = i % 2 ? CR : RD;
        const P = (a) => `${f(cx + Math.cos(a) * r)} ${f(top + Math.sin(a) * r)}`;
        o += `<path d="M${cx} ${top}L${P(a0)}L${P(am)}Z" fill="${c}"/><path d="M${cx} ${top}L${P(am)}L${P(a1)}Z" fill="${c === RD ? RDK : CRS}"/>`;
      }
      const r2 = r * 0.45;
      o += `<path d="M${f(cx - r2)} ${top}A${f(r2)} ${f(r2)} 0 0 0 ${f(cx + r2)} ${top}Z" fill="${NV}" stroke="${CR}" stroke-width="${f(r * 0.07)}"/>`;
      [[-0.55, 0.35], [0, 0.6], [0.55, 0.35]].forEach(([dx, dy]) => { o += `<path d="${starD(cx + dx * r2 * 0.8, top + dy * r2 * 0.8, r2 * 0.24)}" fill="#fff"/>`; });
      return o + `<rect x="${f(cx - r - 1)}" y="${f(top - r * 0.08)}" width="${f(2 * r + 2)}" height="${f(r * 0.16)}" rx="${f(r * 0.08)}" fill="${NV}"/><circle cx="${cx}" cy="${top + 1}" r="2.6" fill="#E9C46A"/>`;
    };
    // Fan bunting in swags (gently swaying); the trim is stretched to the width, so the fans are drawn a bit wide
    const bunting = () => {
      const n = 8, w = 1200 / n, top = 3;
      let o = "<style>.j4sw{transform-box:fill-box;transform-origin:50% 0;animation:j4sw 3.4s ease-in-out infinite}@keyframes j4sw{0%,100%{transform:rotate(-3deg)}50%{transform:rotate(3deg)}}@media (prefers-reduced-motion:reduce){.j4sw{animation:none}}</style>";
      for (let i = 0; i < n; i++) { const x = i * w; o += `<path d="M${x} ${top}Q${x + w / 2} ${top + 30} ${x + w} ${top}" fill="none" stroke="${NV}" stroke-width="4"/><path d="M${x} ${top}Q${x + w / 2} ${top + 26} ${x + w} ${top}" fill="none" stroke="${CR}" stroke-opacity=".6" stroke-width="1.4"/>`; }
      // The trim is stretched wider than tall, so each fan is drawn narrower to stay round on screen
      for (let i = 0; i <= n; i++) o += `<g transform="translate(${i * w} ${top}) scale(.62 1) translate(${-i * w} ${-top})"><g class="j4sw" style="animation-delay:${-i * 0.45}s">${fan(i * w, top, i % 2 ? 44 : 52)}</g></g>`;
      return o;
    };
    // The big show on the night of the 4th: bursts blooming one after another over the bunting
    const fireworks = () => {
      let o = "<style>.j4fw{transform-box:fill-box;transform-origin:center;animation:j4fw 3.6s ease-out infinite}@keyframes j4fw{0%{transform:scale(.1);opacity:0}8%{opacity:1}45%{transform:scale(1);opacity:1}70%,100%{transform:scale(1.08);opacity:0}}</style>";
      ["#E0404F", "#FFFFFF", "#7FA0FF", "#FFD25A", "#E0404F"].forEach((c, i) => {
        const x = 130 + i * 235, y = 46 + (i % 2) * 8, r = 34;
        let rays = "";
        for (let k = 0; k < 16; k++) { const a = k * Math.PI / 8; rays += `<path d="M${f(x + Math.cos(a) * r * 0.3)} ${f(y + Math.sin(a) * r * 0.3)}L${f(x + Math.cos(a) * r)} ${f(y + Math.sin(a) * r)}" stroke="${c}" stroke-width="2.4" stroke-linecap="round"/><circle cx="${f(x + Math.cos(a) * r * 1.12)}" cy="${f(y + Math.sin(a) * r * 1.12)}" r="1.8" fill="${c}"/>`; }
        o += `<g class="j4fw" style="animation-delay:${-i * 0.75}s">${rays}</g>`;
      });
      return o;
    };
    // Sparklers fizzing along the bottom of Home
    const sparklers = () => {
      let o = "<style>.j4sp{animation:j4sp .45s steps(2) infinite}.j4sp:nth-of-type(2n){animation-delay:-.22s}@keyframes j4sp{50%{opacity:.5}}@media (prefers-reduced-motion:reduce){.j4sp{animation:none}}</style>";
      const n = 16;
      for (let i = 0; i < n; i++) {
        const x = (i + 0.5) * 1200 / n, lean = (i % 2 ? 1 : -1) * 5, tx = x + lean, ty = 11;
        o += `<path d="M${f(x - lean * 0.2)} 40L${f(tx)} ${ty}" stroke="#8A8F9C" stroke-width="1.6"/><path d="M${f(x - lean * 0.2)} 40L${f(x - lean * 0.05)} 32" stroke="#5A4632" stroke-width="3"/><circle cx="${f(tx)}" cy="${ty}" r="9" fill="#FFD25A" fill-opacity=".22"/>`;
        let sp = "";
        for (let k = 0; k < 12; k++) { const a = k * Math.PI / 6 + i, r = k % 2 ? 6 : 10; sp += `<path d="M${f(tx)} ${ty}L${f(tx + Math.cos(a) * r)} ${f(ty + Math.sin(a) * r)}" stroke="${["#FFF3C4", "#FFD25A", "#fff"][k % 3]}" stroke-width="1"/>`; }
        o += `<g class="j4sp">${sp}</g><circle cx="${f(tx)}" cy="${ty}" r="2.6" fill="#FFF9E0"/>`;
      }
      return o;
    };
    // A twinkling gold star beside the icon you're on (drawn a bit bigger than its little box so it reads from the couch)
    const star = `<style>.rl-mark svg,.bt-mark svg{overflow:visible}</style><g><animateTransform attributeName="transform" type="rotate" values="-10 6 9;10 6 9;-10 6 9" dur="2.2s" repeatCount="indefinite"/><circle cx="6" cy="9" r="11" fill="#FFD25A" fill-opacity=".22"/><path d="${starD(6, 9, 11)}" fill="#FFD25A"/><path d="${starD(6, 9, 5.4)}" fill="#FFF3C4"/></g>`;
    // The usual icons in red, white and blue. While the rail lights up at start-up they follow the light-up colours.
    const COL = { home: "#FF5A68", movies: "#FFFFFF", shows: "#7FA0FF", live: "#FF5A68", games: "#FFFFFF", music: "#7FA0FF", books: "#FF5A68", search: "#FFFFFF", requests: "#7FA0FF", more: "#FFFFFF" };
    const NAME = { home: "home", search: "search", requests: "plus", more: "more" };
    const usual = (key) => {
      const n = NAME[key] || (B.DOORS && B.DOORS[key] && B.DOORS[key].icon) || key;
      let svg = "";
      try { svg = B.doorIcon ? B.doorIcon(n) : B.I(n); } catch (e) { svg = ""; }
      return String(svg).replace(/^<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "");
    };
    const icons = {};
    Object.keys(COL).forEach((k) => Object.defineProperty(icons, k, { enumerable: true, get: () =>
      `<style>.rl-i.dark .j4i,.rl-i.lit .j4i{stroke:currentColor!important;color:inherit!important}</style><g class="j4i" style="stroke:${COL[k]};color:${COL[k]}">${usual(k)}</g>` }));
    const h = make("july4", is("j4On"), true, ["#14214A", "#0B1430"], ["#E0404F", "#FFFFFF", "#7FA0FF"], "4th of July pick", {
      mark: star,
      bottomTrim: sparklers(),
      icons,
      countdown: () => { const d = now(); if (d.getMonth() === 6 && d.getDate() === 4 && is("j4On")()) return "It's the 4th!"; return cd(() => B.j4)(); },
      pickWords: ["Independence Day", "Sandlot", "Jaws", "1776", "Yankee Doodle", "National Treasure"],
    });
    // The bunting, with the big show over it on the 4th from 9 PM
    Object.defineProperty(h, "topTrim", { enumerable: true, get: () => { const d = now(); return bunting() + (d.getMonth() === 6 && d.getDate() === 4 && d.getHours() >= 21 ? fireworks() : ""); } });
    return h;
  };

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
    july4(),
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
