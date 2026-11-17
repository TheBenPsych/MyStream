/*
 * Benflix Earth Day: records the Who's watching room (server/web/js/earthday.js) as looping videos for the TV and
 * phone app, into extras/scenes/earthday/day<1-7>-<wide|tall>.mp4 (the seedling grows each day, and on day 1 the
 * new tree is being planted by the path).
 *
 * Run it from the repository root after changing the room:
 *   npm install playwright   (once; any recent version)
 *   node tools/earthday/record.js              (all 14 videos, about half an hour)
 *   node tools/earthday/record.js 1 wide       (just day 1, wide)
 * It needs ffmpeg. Then bump EarthVideos' version in the app (ui/setup/ChristmasRoom.kt) so TVs fetch the new videos.
 *
 * The room repeats every LOOP seconds, so one loop of pictures makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/earthday");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };

async function record(browser, day, shape) {
  const [w, h] = SHAPES[shape];
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `earth-${day}-${shape}-`));
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/earthday.js") });
  const loop = await page.evaluate(([l, d]) => { window.S = BenflixEarth.build(document.getElementById("s"), { layout: l, day: d }); return BenflixEarth.LOOP; }, [shape, day]);
  const frames = loop * FPS;
  for (let n = 0; n < frames; n++) {
    await page.evaluate((t) => S.at(t), n / FPS);
    await page.screenshot({ path: path.join(dir, `f${String(n).padStart(4, "0")}.png`) });
  }
  await page.close();
  fs.mkdirSync(OUT, { recursive: true });
  const out = path.join(OUT, `day${day}-${shape}.mp4`);
  execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-framerate", String(FPS), "-i", path.join(dir, "f%04d.png"), "-c:v", "libx264", "-profile:v", "main", "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", "27", "-g", String(FPS * 2), "-movflags", "+faststart", "-an", out]);
  fs.rmSync(dir, { recursive: true, force: true });
  console.log(`Recorded day ${day} ${shape}: ${(fs.statSync(out).size / 1e6).toFixed(1)} MB`);
}

(async () => {
  const args = process.argv.slice(2);
  const days = args.filter((a) => /^[1-7]$/.test(a)).map(Number);
  const shapes = args.filter((a) => SHAPES[a]);
  const jobs = [];
  for (const d of days.length ? days : [1, 2, 3, 4, 5, 6, 7]) for (const s of shapes.length ? shapes : Object.keys(SHAPES)) jobs.push([d, s]);
  const browser = await chromium.launch();
  // a few at a time
  const next = async () => { const j = jobs.shift(); if (!j) return; await record(browser, ...j); return next(); };
  await Promise.all([next(), next(), next(), next()]);
  await browser.close();
})();
