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
    // Fan bunting in swags, gently swaying. Ben 2026-11-17: the greeting sits top left on the clock's line (clock
    // top right), so the bunting only hangs in the gap between them (phones: right of the greeting). Drawn for the
    // screen's own width, so the fans never stretch.
    const bunting = (W, narrow) => {
      const x0 = W * (narrow ? 0.66 : 0.34), x1 = W * (narrow ? 1 : 0.86), n = Math.max(1, Math.round((x1 - x0) / 170)), w = (x1 - x0) / n, top = 3;
      let o = "<style>.j4sw{transform-box:fill-box;transform-origin:50% 0;animation:j4sw 3.4s ease-in-out infinite}@keyframes j4sw{0%,100%{transform:rotate(-3deg)}50%{transform:rotate(3deg)}}@media (prefers-reduced-motion:reduce){.j4sw{animation:none}}</style>";
      for (let i = 0; i < n; i++) { const x = x0 + i * w; o += `<path d="M${f(x)} ${top}Q${f(x + w / 2)} ${top + 30} ${f(x + w)} ${top}" fill="none" stroke="${NV}" stroke-width="3"/><path d="M${f(x)} ${top}Q${f(x + w / 2)} ${top + 26} ${f(x + w)} ${top}" fill="none" stroke="${CR}" stroke-opacity=".6" stroke-width="1"/>`; }
      for (let i = 0; i <= n; i++) o += `<g class="j4sw" style="animation-delay:${-i * 0.45}s">${fan(x0 + i * w, top, i % 2 ? 27 : 33)}</g>`;
      return o;
    };
    // The big show on the night of the 4th: bursts blooming one after another over the right of the picture, below
    // the greeting and clock line and clear of the pick's words
    const fireworks = (W) => {
      let o = "<style>.j4fw{transform-box:fill-box;transform-origin:center;animation:j4fw 3.6s ease-out infinite}@keyframes j4fw{0%{transform:scale(.1);opacity:0}8%{opacity:1}45%{transform:scale(1);opacity:1}70%,100%{transform:scale(1.08);opacity:0}}</style>";
      ["#E0404F", "#FFFFFF", "#7FA0FF", "#FFD25A", "#E0404F"].forEach((c, i) => {
        const x = W * (0.52 + i * 0.1), y = 110 + ((i * 7) % 3) * 22, r = 34 + 8 * (i % 2);
        let rays = "";
        for (let k = 0; k < 16; k++) { const a = k * Math.PI / 8; rays += `<path d="M${f(x + Math.cos(a) * r * 0.3)} ${f(y + Math.sin(a) * r * 0.3)}L${f(x + Math.cos(a) * r)} ${f(y + Math.sin(a) * r)}" stroke="${c}" stroke-width="2.4" stroke-linecap="round"/><circle cx="${f(x + Math.cos(a) * r * 1.12)}" cy="${f(y + Math.sin(a) * r * 1.12)}" r="1.8" fill="${c}"/>`; }
        o += `<g class="j4fw" style="animation-delay:${-i * 0.75}s">${rays}</g>`;
      });
      return o;
    };
    const topTrim = (cls) => {
      const W = Math.max(320, Math.round(window.innerWidth || 1200)), narrow = W <= 760, d = now();
      const show = d.getMonth() === 6 && d.getDate() === 4 && d.getHours() >= 21;
      return `<svg class="${cls || ""}" viewBox="0 0 ${W} 44" preserveAspectRatio="xMinYMin slice" aria-hidden="true" style="overflow:visible">${bunting(W, narrow)}${show ? fireworks(W) : ""}</svg>`;
    };
    // Sparklers fizzing along the bottom of Home
    const sparklers = () => {
      let o = "<style>.j4sp{animation:j4sp .45s steps(2) infinite}.j4sp:nth-of-type(2n){animation-delay:-.22s}@keyframes j4sp{50%{opacity:.5}}@media (prefers-reduced-motion:reduce){.j4sp{animation:none}}</style>";
      const n = 16;
      for (let i = 0; i < n; i++) {
        const x = (i + 0.5) * 1200 / n, lean = (i % 2 ? 1 : -1) * 5, tx = x + lean, ty = 22; // low, so the lit tips show below the Today cards
        o += `<path d="M${f(x - lean * 0.2)} 40L${f(tx)} ${ty}" stroke="#8A8F9C" stroke-width="1.6"/><path d="M${f(x - lean * 0.2)} 40L${f(x - lean * 0.05)} 32" stroke="#5A4632" stroke-width="3"/><circle cx="${f(tx)}" cy="${ty}" r="7" fill="#FFD25A" fill-opacity=".22"/>`;
        let sp = "";
        for (let k = 0; k < 12; k++) { const a = k * Math.PI / 6 + i, r = k % 2 ? 4.5 : 7.5; sp += `<path d="M${f(tx)} ${ty}L${f(tx + Math.cos(a) * r)} ${f(ty + Math.sin(a) * r)}" stroke="${["#FFF3C4", "#FFD25A", "#fff"][k % 3]}" stroke-width="1"/>`; }
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
    // The bunting, with the big show on the 4th from 9 PM
    h.topTrim = topTrim;
    return h;
  };
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
    for (let i = 0; i < 3; i++) clouds += cloud(20 + i * 195 + h(i + 40) * 40, 9 + h(i + 80) * 3, 0.62 + h(i) * 0.12);
    let birds = "";
    [[0, 0], [-16, -5], [-16, 5], [-32, -10], [-32, 10]].forEach(([x, y], k) => {
      birds += '<path class="edn-flap" style="animation-delay:-' + f(k * 0.17) + 's" d="M' + (x - 6) + " " + y + "q3 -4 6 0q3 -4 6 0" + '" stroke="#E8F3FF" stroke-width="1.8" fill="none" stroke-linecap="round"/>';
    });
    const top = '<g><style>'
      + ".edn-drift{animation:edn-drift 40s ease-in-out infinite alternate}@keyframes edn-drift{to{transform:translateX(-20px)}}"
      + ".edn-flock{animation:edn-flock 20s linear infinite}@keyframes edn-flock{0%{transform:translate(0,42px);opacity:0}5%{opacity:1}60%{transform:translate(600px,32px);opacity:1}61%,100%{transform:translate(600px,32px);opacity:0}}"
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
    const bottom = '<g id="ednMeadow"><style>'
      + ".edn-sw{transform-box:fill-box;transform-origin:50% 100%;animation:edn-sw 4s ease-in-out infinite}.edn-sw.b{animation-delay:-1.3s}.edn-sw.c{animation-delay:-2.6s;animation-duration:5s}@keyframes edn-sw{50%{transform:skewX(-5deg)}}"
      + ".edn-bee{animation:edn-bee 24s ease-in-out infinite}@keyframes edn-bee{0%{transform:translate(40px,22px)}25%{transform:translate(330px,19px)}50%{transform:translate(620px,23px)}75%{transform:translate(900px,19px)}100%{transform:translate(1180px,22px)}}"
      + ".edn-wing{transform-box:fill-box;transform-origin:50% 100%;animation:edn-flap .15s linear infinite}@keyframes edn-flap{50%{transform:scaleY(-.6)}}"
      + "@media (prefers-reduced-motion:reduce){.edn-sw,.edn-bee,.edn-wing{animation:none}}</style>"
      + '<g class="edn-sw a">' + grass[0] + "</g>" + flowers + '<g class="edn-sw b">' + grass[1] + '</g><g class="edn-sw c">' + grass[2] + "</g>" + bee + "</g>";
    // Whole <svg>s (B.holidaySvg takes a function), so nothing is stretched or cut on any screen width (Ben
    // 2026-11-17: trims mustn't crowd the greeting or get cut off):
    // - top: the clouds and birds keep to the middle of the top edge (46% to 86% across), clear of the greeting on
    //   the left and the clock on the right
    // - bottom: the meadow is drawn at one fixed size that fits its 36px and repeated along the width, so flowers and the
    //   bee are never cropped
    const topSvg = (cls) => `<svg class="${cls || ""}" aria-hidden="true"><svg x="46%" width="40%" height="100%" viewBox="0 3 640 41" preserveAspectRatio="xMidYMin meet">${top}</svg></svg>`;
    const bottomSvg = (cls) => `<svg class="${cls || ""}" aria-hidden="true"><g transform="translate(0 -16) scale(1.3)">${bottom}<use href="#ednMeadow" x="1200"/><use href="#ednMeadow" x="2400"/><use href="#ednMeadow" x="3600"/></g></svg>`;
    return {
      top: topSvg,
      bottom: bottomSvg,
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
      return `<svg class="${cls || ""}${xday() ? " xday" : ""}" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMinYMin slice" aria-hidden="true" style="overflow:visible;top:var(--bar)">${out}</svg>`;
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
    make("earthday", () => is("edOn")() && !is("eaOn")(), true, ["#0E2C44", "#0B1A26"], ["#A8DCF7", "#A8DCF7", "#7FC8F2", "#56BDEB", "#7CD36B", "#8FD86F", "#B5E37A", "#E8F5A0", "#7CD36B"], "Earth Day pick", {
      pickTags: ["nature", "environmentalism", "wildlife", "nature documentary"], pickWords: ["Earth", "Planet"],
      icons: ed.icons, topTrim: ed.top, bottomTrim: ed.bottom,
      countdown: () => { try { const d = window.BenflixEarth ? window.BenflixEarth.day(new Date()) : 0; return d ? "Earth week · day " + d + " of 7 · Planet Earth picks on Movies and Shows" : ""; } catch (e) { return ""; } },
    }),
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
      // a slim sign along the very top (12px strip, bulbs on its edge) so it stays clear of the greeting and clock
      top() {
        const sx = this.wide(), sy = 44 / 60, r = 6.5, step = 36 / sx, n = Math.ceil(1200 / step), band = 12 / sy;
        let s = this.grad + '<style>.pcb{animation:pcchase 1.2s steps(1) infinite}@keyframes pcchase{0%{opacity:1}33%{opacity:.4}66%{opacity:1}}</style>'
          + '<linearGradient id="pcStrip" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6E1016"/><stop offset=".5" stop-color="#9E2129"/><stop offset="1" stop-color="#6E1016"/></linearGradient>'
          + `<rect width="1200" height="${band}" fill="url(#pcStrip)"/><rect y="${band - 3 / sy}" width="1200" height="${3 / sy}" fill="#F6C744"/>`;
        for (let i = 0; i <= n; i++) {
          const x = i * step + step / 2, y = band;
          s += `<g class="pcb" style="animation-delay:${-(i % 3) * .4}s"><ellipse cx="${x}" cy="${y}" rx="${r * 1.7 / sx}" ry="${r * 1.7 / sy}" fill="url(#pcGlow)"/>`
            + `<ellipse cx="${x}" cy="${y}" rx="${r / sx}" ry="${r / sy}" fill="url(#pcBulb)"/></g>`;
        }
        return s;
      },
      bottom() {
        const sx = this.wide(), sy = 36 / 40, h = new Date().getHours(), full = h < 12 ? .55 : h < 17 ? .78 : 1, r0 = 7.5;
        // one puff drawn once (radius 1), then placed many times
        let seed = 11, s = this.grad + `<defs><g id="pcP">${this.puff(0, 0, 1, 1, 1, 1)}</g></defs>`;
        const rnd = () => (seed = (seed * 9301 + 49297) % 233280) / 233280;
        for (let row = 0; row < 3; row++) {
          const count = Math.floor((1200 * sx) / (r0 * 1.5) * (1.15 - row * .3) * full);
          const y = 40 - (r0 * 1.25 + row * r0 * 1.1 * full) / sy;
          for (let i = 0; i < count; i++) {
            const x = rnd() * 1200, r = r0 * (.8 + rnd() * .45), yy = y - rnd() * r0 * .25 / sy, f = rnd() < .5 ? 1 : -1;
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
    july4(),
    make("newyear", is("nyOn"), true, ["#141A2E", "#0B0E1A"], ["#E8C15A", "#C9D0E0"], "New Year's pick", { countdown: cd(() => B.ny), pickTags: ["new year's eve"], pickWords: ["New Year"] }),
    // Thanksgiving (approved 2026-11-17, sheet nav-13b g2-swag): harvest-brown rail, the rail icons themselves in
    // gold, pumpkin orange and cranberry, a short leaf garland swagging across the top of Home with an acorn at each
    // low point and now and then a leaf drifting down, and only two pumpkins in Home's bottom-right corner.
    make("thanksgiving", is("tgOn"), false, ["#2E1E14", "#1C120B"], ["#E2702A", "#E8B04A", "#B83A1E"], "Thanksgiving pick", (() => {
      // Thanksgiving is the fourth Thursday of November
      const day = (y) => { const d = new Date(y, 10, 1); return new Date(y, 10, 1 + ((4 - d.getDay() + 7) % 7) + 21); };
      const countdown = () => {
        const n = now(); const t = day(n.getFullYear());
        const left = Math.round((new Date(t.getFullYear(), t.getMonth(), t.getDate()) - new Date(n.getFullYear(), n.getMonth(), n.getDate())) / 864e5);
        return left > 1 ? `${left} days to Thanksgiving` : left === 1 ? "Thanksgiving is tomorrow" : left === 0 ? "Happy Thanksgiving" : "";
      };
      // Icons: the usual ones, drawn in harvest colours with a warm glow
      const col = { home: "#E8B04A", movies: "#F07E36", shows: "#D9473A", live: "#D9473A", games: "#E8B04A", music: "#F07E36", books: "#E8B04A" };
      const inner = (svg) => String(svg || "").replace(/^\s*<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "");
      const usual = (k) => {
        try { return k === "home" ? B.I("home") : (B.doorIcon || B.I)((B.DOORS[k] || {}).icon || k); } catch (e) { return ""; }
      };
      const icons = {};
      Object.keys(col).forEach((k) => Object.defineProperty(icons, k, {
        enumerable: true,
        get: () => `<g stroke="${col[k]}" style="filter:drop-shadow(0 0 1.5px ${col[k]}88)">${inner(usual(k))}</g>`,
      }));
      // Art (same shapes as the app's ThanksgivingNav.kt)
      const MAPLE = "M50,0 L57,14 L66,9 L63,30 L80,17 L77,26 L95,24 L88,38 L97,44 L72,62 L76,73 L54,69 L54,96 L46,96 L46,69 L24,73 L28,62 L3,44 L12,38 L5,24 L23,26 L20,17 L37,30 L34,9 L43,14 Z";
      const OAK = "M50,2 C60,2 62,12 58,16 C68,12 74,20 66,28 C78,26 82,38 70,42 C82,44 82,58 68,58 C76,64 72,74 60,70 L54,80 L54,98 L46,98 L46,80 L40,70 C28,74 24,64 32,58 C18,58 18,44 30,42 C18,38 22,26 34,28 C26,20 32,12 42,16 C38,12 40,2 50,2 Z";
      const LEAF = [["#f0603a", "#9c2414"], ["#ffa040", "#c2561f"], ["#ffd76a", "#c88a26"], ["#c8384e", "#6e1626"], ["#d8742e", "#7a3a14"]];
      const g2 = (id, a, b, x2) => `<linearGradient id="${id}" x1="0" y1="0" x2="${x2 || 1}" y2="${x2 ? 0 : 1}"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient>`;
      const defs = `<defs>${LEAF.map(([a, b], i) => g2("tgl" + i, a, b)).join("")}${g2("tgnut", "#e0a35c", "#6e4020", 1)}${g2("tgcup", "#8a5a32", "#4a2d16")}${g2("tgpk", "#ffa24a", "#a5441a")}${g2("tgpw", "#fbf4e4", "#b5a586")}<radialGradient id="tgbr" cx=".35" cy=".35"><stop offset="0" stop-color="#fff0b0"/><stop offset=".5" stop-color="#d9a940"/><stop offset="1" stop-color="#7a5418"/></radialGradient></defs>`;
      const css = "<style>.sp-trim.top:has(.tgn){top:var(--bar);height:auto;aspect-ratio:20/1;overflow:visible}.sp-trim.bottom:has(.tgn){height:auto;aspect-ratio:30/1;overflow:visible}"
        + ".tgn .sw{animation:tgsw 4.5s ease-in-out infinite alternate}@keyframes tgsw{from{transform:rotate(-5deg)}to{transform:rotate(5deg)}}"
        + ".tgn .fall{animation:tgfall 13s linear infinite}@keyframes tgfall{0%{transform:translate(0,0) rotate(0);opacity:0}5%{opacity:1}30%{transform:translate(40px,90px) rotate(100deg)}60%{transform:translate(-10px,180px) rotate(210deg)}90%{opacity:1}100%{transform:translate(20px,260px) rotate(320deg);opacity:0}}</style>";
      let seed = 11; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
      const leaf = (x, y, rot, size, c, oak, cls, delay) => {
        const k = size / 100; const d = oak ? OAK : MAPLE;
        const veins = oak ? "M50,96 L50,8 M50,40 L68,30 M50,40 L32,30 M50,58 L70,50 M50,58 L30,50" : "M50,92 L50,8 M50,66 L84,30 M50,66 L16,30 M50,70 L78,58 M50,70 L22,58";
        return `<g class="${cls || ""}" style="transform-origin:${x}px ${y}px;animation-delay:${delay || 0}s"><g transform="translate(${x},${y}) rotate(${rot}) scale(${k}) translate(-50,-98)"><path d="${d}" fill="url(#tgl${c})" stroke="url(#tgl${c})" stroke-width="5" stroke-linejoin="round"/><path d="${veins}" stroke="rgba(255,236,190,.45)" stroke-width="2.6" fill="none" stroke-linecap="round"/></g></g>`;
      };
      const acorn = (x, y, s, rot, cls, delay) => `<g class="${cls || ""}" style="transform-origin:${x}px ${y}px;animation-delay:${delay || 0}s"><g transform="translate(${x},${y}) rotate(${rot}) scale(${s})"><path d="M0,-4 L1,-11" stroke="#4a2d16" stroke-width="2.6" stroke-linecap="round"/><path d="M-10,6 C-10,20 -4,28 0,31 C4,28 10,20 10,6 Z" fill="url(#tgnut)"/><ellipse cx="-4" cy="13" rx="2.4" ry="6" fill="rgba(255,235,200,.45)"/><path d="M-12.5,7 C-12.5,-4 12.5,-4 12.5,7 C8,9.5 -8,9.5 -12.5,7 Z" fill="url(#tgcup)"/></g></g>`;
      const tack = (x, y) => `<circle cx="${x}" cy="${y + 1}" r="5" fill="rgba(0,0,0,.35)"/><circle cx="${x}" cy="${y}" r="5" fill="url(#tgbr)"/><circle cx="${x - 1.5}" cy="${y - 1.5}" r="1.6" fill="rgba(255,250,220,.8)"/>`;
      const twine = (d) => `<path d="${d}" stroke="#7a5a32" stroke-width="3.6" fill="none"/><path d="${d}" stroke="#d2ad72" stroke-width="2.4" fill="none"/><path d="${d}" stroke="#8a6a3e" stroke-width="2.4" fill="none" stroke-dasharray="2 3"/>`;
      const pumpkin = (cx, by, w, h, white) => {
        const ribs = [[-0.36, 0.62], [0.36, 0.62], [-0.18, 0.82], [0.18, 0.82], [0, 1]];
        return `<ellipse cx="${cx}" cy="${by - 1}" rx="${w * 0.52}" ry="${h * 0.1}" fill="rgba(0,0,0,.4)"/>${ribs.map(([o, k]) => `<ellipse cx="${cx + o * w}" cy="${by - h / 2}" rx="${w * 0.3 * k}" ry="${h / 2}" fill="url(#${white ? "tgpw" : "tgpk"})" stroke="rgba(80,30,5,.35)" stroke-width="1"/>`).join("")}<path d="M${cx - 1},${by - h + 1} C${cx - 2},${by - h * 1.28} ${cx + 3},${by - h * 1.36} ${cx + w * 0.16},${by - h * 1.3}" stroke="#5a4a22" stroke-width="${Math.max(3, w * 0.08)}" fill="none" stroke-linecap="round"/>`;
      };
      // Top: viewBox 1200 x 60 kept at 20:1 by the style above, so the swag never stretches
      const top = (() => {
        seed = 11; const n = 4, seg = 1200 / n, dip = 15; let tw = "", lv = "", tk = "";
        for (let i = 0; i < n; i++) {
          const x0 = i * seg + 4, x1 = (i + 1) * seg - 4, mx = (x0 + x1) / 2;
          tw += twine(`M${x0},6 Q${mx},${6 + dip * 2} ${x1},6`); tk += tack(x0, 6) + (i === n - 1 ? tack(x1, 6) : "");
          for (let j = 1; j < 9; j++) {
            const t = j / 9, px = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * mx + t * t * x1, py = 6 + 2 * (1 - t) * t * dip * 2;
            lv += leaf(px, py, 180 + (rnd() * 44 - 22), 36 * (0.88 + rnd() * 0.24), Math.floor(rnd() * 5), j % 3 === 1, "sw", -rnd() * 5);
          }
          lv += acorn(mx, 6 + dip + 3, 0.75, 0, "sw", -1.5);
        }
        const fall = `<g class="fall" style="animation-delay:-4s">${leaf(520, 30, 180, 30, 1, false)}</g>`;
        return `${css}${defs}<g class="tgn">${tw}${lv}${tk}${fall}</g>`;
      })();
      // Bottom: only the bottom-right corner (viewBox 1200 x 40 kept at 30:1, drawn upward from the bottom edge)
      const bottom = (() => {
        // Small enough to sit in the gap under the Today cards (about 20 of the 40 units)
        const R = 1190;
        const leaves = [[R - 62, -55, 0], [R - 46, 30, 2], [R - 2, 70, 1], [R - 72, -20, 3]].map(([x, r, c], i) => leaf(x, 39, r, 13, c, i % 2 === 1)).join("");
        return `${defs}<g class="tgn">${leaves}${pumpkin(R - 22, 39.5, 30, 20)}${pumpkin(R - 48, 39.5, 18, 12.5, true)}${acorn(R - 62, 33, 0.36, 70)}</g>`;
      })();
      return {
        icons,
        topTrim: top,
        bottomTrim: bottom,
        countdown,
        pickTags: ["thanksgiving"],
        pickWords: ["Thanksgiving", "Planes, Trains", "Home for the Holidays", "Pieces of April", "Free Birds"],
      };
    })()),
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
