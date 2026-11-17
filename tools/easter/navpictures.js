/*
 * Benflix Easter on the new side menu (approved 2026-11-17, direction A "Easter tree ribbon"): draws the rail
 * icons (the Easter tree charms without their pin and ribbon), the egg that hangs by the icon you're on, the six
 * painted eggs that sway under Home's ribbon and the strip of spring grass along the bottom of Home, from the
 * Easter kit in server/web/js/easter.js, as pictures for the app (res/drawable-nodpi/ea_nav_*.png). The web draws
 * the same things live (server/web/js/holidaynav.js). The app puts them together in ui/theme/EasterNav.kt.
 *
 * Run it from the repository root after changing a drawing:
 *   npm install playwright   (once)
 *   node tools/easter/navpictures.js
 */
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "app/src/main/res/drawable-nodpi");
// The part of each charm (on its 240 x 220 door) that is the thing itself: the same boxes as holidaynav.js
const CROP = { movies: "144 34 84 102", shows: "140 28 92 106", live: "144 42 84 100", games: "128 18 116 104", music: "136 4 104 140", books: "146 10 80 130" };
const EGGS = [["#FFB8D2", "#7B5BD6", "zig"], ["#BFE6FF", "#3F7FD0", "dots"], ["#FFF1A6", "#F08A24", "stripes"], ["#C9F2C0", "#E8508A", "flowers"], ["#E3D5FF", "#4AA8C8", "waves"], ["#FFD9B8", "#5AAE52", "checks"]];
// name: [viewBox, drawing, pixels high]
const PICS = {};
Object.entries(CROP).forEach(([k, vb]) => { PICS[`ea_nav_${k}`] = [vb, `K.CHARM.${k}()`, 160]; });
// a painted egg on a pink ribbon from a pearl pin, hung by the rail icon you're on
PICS.ea_nav_mark = ["-9 -3 18 30", `'<path d="M0 0V7" stroke="#C25A86" stroke-width="1.4"/>' + K.egg(0,16.5,7.6,10.2,"#FFB8D2","#7B5BD6","zig",{nosh:true}) + '<circle cx="0" cy="0" r="2.2" fill="#F4F1EA" stroke="#B8B0A0" stroke-width=".4"/>'`, 120];
EGGS.forEach(([a, b, p], i) => { PICS[`ea_nav_egg${i}`] = ["-21 -28 42 56", `K.egg(0,0,18,24,"${a}","${b}","${p}",{nosh:true})`, 112]; });
// 600 x 72 tile of grass, tulips and eggs; it repeats along the bottom of Home
PICS.ea_nav_grass = ["0 0 600 72", `(() => {
  let seed = 3; const rnd = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280; };
  const grass = (yb, h) => { let s = ""; for (let x = 0; x < 600; x += 5) { const hh = h * (0.55 + rnd() * 0.6), lean = (rnd() - 0.5) * 14; s += '<path d="M' + x + ' ' + yb + 'q' + (lean * 0.3).toFixed(1) + ' ' + (-hh * 0.6).toFixed(1) + ' ' + lean.toFixed(1) + ' ' + (-hh).toFixed(1) + '" stroke="' + ["#3E8A45", "#5AAE52", "#7CCB6A", "#4FA052"][(x / 5) % 4] + '" stroke-width="2.6" fill="none" stroke-linecap="round"/>'; } return s; };
  const E = ${JSON.stringify(EGGS)};
  let s = '<rect x="0" y="58" width="600" height="14" fill="#24502F"/>' + grass(72, 40);
  [[60, 0], [135, 1], [210, 2], [285, 3], [360, 4], [435, 5], [510, 6], [585, 7]].forEach(([x, i]) => {
    if (i % 2) { const e = E[i % 6]; s += K.egg(x, 52, 14, 19, e[0], e[1], e[2]); }
    else s += '<path d="M' + x + ' 40V72" stroke="#3E8A45" stroke-width="3"/>' + K.tulip(x, 42, ["#FF6F91", "#FFD23F", "#FFFFFF", "#C9B2F5"][(i / 2) % 4], (i % 5 - 2) * 4, 0.9);
  });
  return s + grass(72, 22);
})()`, 144];

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent("<!doctype html><html><body style='margin:0;background:transparent'></body></html>");
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/easter.js") });
  for (const [name, [vb, code, ph]] of Object.entries(PICS)) {
    const [, , w, h] = vb.split(" ").map(Number);
    const px = Math.round(ph * w / h);
    await page.setViewportSize({ width: px, height: ph });
    await page.evaluate(([vb2, code2, px2, ph2]) => {
      const K = BenflixEaster.kit;
      // eslint-disable-next-line no-eval
      document.body.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb2}" width="${px2}" height="${ph2}" preserveAspectRatio="none" style="display:block">${K.GDEF}${eval(code2)}</svg>`;
    }, [vb, code, px, ph]);
    const png = await page.screenshot({ omitBackground: true, clip: { x: 0, y: 0, width: px, height: ph } });
    fs.writeFileSync(path.join(OUT, `${name}.png`), png);
    console.log(name, px, "x", ph);
  }
  await browser.close();
})();
