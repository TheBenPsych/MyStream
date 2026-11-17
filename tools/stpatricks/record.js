/*
 * Benflix St. Patrick's Day: records the Who's watching room (server/web/js/stpatricks.js) as looping videos
 * for the TV and phone app, into extras/scenes/stpatricks/<week|day>-<wide|tall>.mp4.
 *   week = 10 to 16 March (soft rain in the window)
 *   day  = 17 March (rainbow, and shamrocks drifting down)
 *
 * Run it from the repository root after changing the room:
 *   npm install playwright   (once; any recent version)
 *   node tools/stpatricks/record.js              (all four videos, a few minutes)
 *   node tools/stpatricks/record.js week-wide    (just one)
 * It needs ffmpeg. Then bump StPatricksVideos' version in the app (ui/setup/ChristmasRoom.kt) so TVs fetch the new videos.
 *
 * The room repeats every LOOP seconds, so one loop of pictures makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/stpatricks");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };
const VARIANTS = ["week", "day"];

async function record(browser, variant, shape) {
  const name = `${variant}-${shape}`;
  const [w, h] = SHAPES[shape];
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `sp-${name}-`));
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/stpatricks.js") });
  const loop = await page.evaluate(([l, v]) => { window.S = BenflixStPatricks.build(document.getElementById("s"), { layout: l, variant: v }); return BenflixStPatricks.LOOP; }, [shape, variant]);
  const frames = loop * FPS;
  for (let n = 0; n < frames; n++) {
    await page.evaluate((t) => S.at(t), n / FPS);
    await page.screenshot({ path: path.join(dir, `f${String(n).padStart(4, "0")}.png`) });
  }
  await page.close();
  fs.mkdirSync(OUT, { recursive: true });
  execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-framerate", String(FPS), "-i", path.join(dir, "f%04d.png"), "-c:v", "libx264", "-profile:v", "main", "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", "27", "-g", String(FPS * 2), "-movflags", "+faststart", "-an", path.join(OUT, `${name}.mp4`)]);
  fs.rmSync(dir, { recursive: true, force: true });
  console.log(`Recorded ${name}: ${(fs.statSync(path.join(OUT, `${name}.mp4`)).size / 1e6).toFixed(1)} MB`);
}

(async () => {
  const only = process.argv.slice(2);
  const jobs = VARIANTS.flatMap((v) => Object.keys(SHAPES).map((s) => [v, s])).filter(([v, s]) => !only.length || only.includes(`${v}-${s}`));
  const browser = await chromium.launch();
  await Promise.all(jobs.map(([v, s]) => record(browser, v, s)));
  await browser.close();
})();
