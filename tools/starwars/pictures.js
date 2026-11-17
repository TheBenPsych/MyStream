/*
 * Benflix Star Wars Day: draws the pictures the app and the web use, from the approved design sheet's code
 * (tools/starwars/kit.js), into app/src/main/res/drawable-nodpi/sw_*.png and server/web/img/sw/.
 *   - sw_moment_<door>.png: the galaxy moment in each Home door's top-right corner (a whole 240 x 220 door,
 *     see-through except the corner, so it lines up the same on every door size)
 *   - sw_hyperspace.png: the light-speed streaks inside the loading ring
 *
 * Run it from the repository root after changing kit.js:
 *   npm install playwright   (once; any recent version)
 *   node tools/starwars/pictures.js
 */
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const APP = path.join(ROOT, "app/src/main/res/drawable-nodpi");
const WEB = path.join(ROOT, "server/web/img/sw");
const FONT = path.join(ROOT, "server/web/fonts/sw_news_cycle.ttf");
const DOORS = ["movies", "shows", "live", "games", "music", "books"];

(async () => {
  fs.mkdirSync(WEB, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 800, height: 800 }, deviceScaleFactor: 2 });
  const font = fs.readFileSync(FONT).toString("base64");
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>
    @font-face { font-family: "News Cycle"; src: url(data:font/ttf;base64,${font}) format("truetype"); font-weight: 700; }
    html, body { margin: 0; background: transparent; } svg { display: block; }</style></head><body><div id="o"></div></body></html>`);
  await page.addScriptTag({ path: path.join(__dirname, "kit.js") });
  await page.evaluate(() => document.fonts.load('700 12px "News Cycle"'));
  const shoot = async (name, svg) => {
    await page.evaluate((s) => { document.getElementById("o").innerHTML = s; }, svg);
    await page.waitForTimeout(50);
    const buf = await page.locator("#o svg").screenshot({ omitBackground: true });
    fs.writeFileSync(path.join(APP, `${name}.png`), buf);
    fs.writeFileSync(path.join(WEB, `${name}.png`), buf);
    console.log(`${name}.png`);
  };
  for (const k of DOORS) {
    const svg = await page.evaluate((k) => `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="220" viewBox="0 0 240 220">${GDEF}<defs><clipPath id="dk"><rect width="240" height="220" rx="20"/></clipPath></defs><g clip-path="url(#dk)">${MOMENT[k](240)}</g></svg>`, k);
    await shoot(`sw_moment_${k}`, svg);
  }
  // the light-speed streaks for the loading ring (the sheet's loader, without the ring itself)
  const hyper = await page.evaluate(() => {
    const cx = 100, cy = 100, r = 100; const R = rnd(44);
    let s = `<circle cx="${cx}" cy="${cy}" r="${r}" fill="#03040A"/>`;
    for (let k = 0; k < 44; k++) { const a = R() * Math.PI * 2, l0 = r * (0.08 + R() * 0.25), l1 = l0 + r * (0.3 + R() * 0.5); s += `<path d="M${f1(cx + Math.cos(a) * l0)} ${f1(cy + Math.sin(a) * l0)}L${f1(cx + Math.cos(a) * Math.min(l1, r * 0.95))} ${f1(cy + Math.sin(a) * Math.min(l1, r * 0.95))}" stroke="#fff" stroke-width="${f1((1 + R() * 2) * 2)}" stroke-linecap="round" opacity="${f1(0.4 + R() * 0.6)}"/>`; }
    return `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200">${s}</svg>`;
  });
  await shoot("sw_hyperspace", hyper);
  await browser.close();
})();
