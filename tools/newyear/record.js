/*
 * Benflix New Year's: records the Who's watching party room (server/web/js/newyear.js) as looping videos
 * for the TV and phone app, into extras/scenes/newyear/<eve|day>-<year>-<wide|tall>.mp4.
 * "eve" is 27 to 31 December (bottle corked), "day" is New Year's Day (opened, confetti falling all day).
 * The year is on the balloons, so each new year needs its own videos.
 *
 * Run it from the repository root after changing the room (or once a year for the next year):
 *   npm install playwright   (once; any recent version)
 *   node tools/newyear/record.js              (all four videos for the coming year)
 *   node tools/newyear/record.js 2028         (all four for 2028)
 *   node tools/newyear/record.js 2028 day wide   (just one)
 * It needs ffmpeg. After re-recording a year that's already there, bump NewYearVideos' version in the app
 * (ui/setup/ChristmasRoom.kt) so TVs fetch the new videos.
 *
 * The room repeats every LOOP seconds, so one loop of pictures makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/newyear");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };
const VARIANTS = ["eve", "day"];

async function record(browser, year, variant, shape) {
  const [w, h] = SHAPES[shape];
  const name = `${variant}-${year}-${shape}`;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `newyear-${name}-`));
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/newyear.js") });
  const loop = await page.evaluate(([l, v, y]) => { window.S = BenflixNewYear.build(document.getElementById("s"), { layout: l, variant: v, year: y }); return BenflixNewYear.LOOP; }, [shape, variant, year]);
  const frames = loop * FPS;
  for (let n = 0; n < frames; n++) {
    await page.evaluate((t) => S.at(t), n / FPS);
    await page.screenshot({ path: path.join(dir, `f${String(n).padStart(4, "0")}.png`) });
  }
  await page.close();
  fs.mkdirSync(OUT, { recursive: true });
  const out = path.join(OUT, `${name}.mp4`);
  execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-framerate", String(FPS), "-i", path.join(dir, "f%04d.png"), "-c:v", "libx264", "-profile:v", "main", "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", "27", "-g", String(FPS * 2), "-movflags", "+faststart", "-an", out]);
  fs.rmSync(dir, { recursive: true, force: true });
  console.log(`Recorded ${name}: ${(fs.statSync(out).size / 1e6).toFixed(1)} MB`);
}

(async () => {
  const args = process.argv.slice(2);
  const now = new Date();
  // the coming New Year (in January, the one just begun)
  const year = +(args.find((a) => /^\d{4}$/.test(a)) || (now.getMonth() === 0 ? now.getFullYear() : now.getFullYear() + 1));
  const variants = VARIANTS.filter((v) => !args.some((a) => VARIANTS.includes(a)) || args.includes(v));
  const shapes = Object.keys(SHAPES).filter((s) => !args.some((a) => a in SHAPES) || args.includes(s));
  const browser = await chromium.launch();
  await Promise.all(variants.flatMap((v) => shapes.map((s) => record(browser, year, v, s))));
  await browser.close();
})();
