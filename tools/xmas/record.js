/*
 * Benflix Christmas: records the Who's watching room (server/web/js/xmas.js) as looping videos for the
 * TV and phone app, into extras/scenes/xmas/<VARIANT>-<shape>.mp4.
 *
 * Run it from the repository root after changing the room:
 *   npm install playwright   (once; any recent version)
 *   node tools/xmas/record.js            (all twelve videos, about 15 minutes)
 *   node tools/xmas/record.js EVE-wide   (just one)
 * It needs ffmpeg. Then bump XmasRoom.VERSION in the app so TVs fetch the new videos.
 *
 * The room repeats every LOOP seconds, so one loop of pictures makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/xmas");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };
const VARIANTS = ["L1", "L2", "L3", "EVE", "DAY", "AFTER"];

async function record(browser, variant, shape) {
  const [w, h] = SHAPES[shape];
  const name = `${variant}-${shape}`;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `xmas-${name}-`));
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/xmas.js") });
  const loop = await page.evaluate(([v, l]) => { window.S = BenflixXmas.build(document.getElementById("s"), { layout: l, variant: v }); return BenflixXmas.LOOP; }, [variant, shape]);
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
  const jobs = [];
  for (const v of VARIANTS) for (const s of Object.keys(SHAPES)) if (!only.length || only.includes(`${v}-${s}`)) jobs.push([v, s]);
  const browser = await chromium.launch();
  // three at a time
  const run = async () => { while (jobs.length) { const [v, s] = jobs.shift(); await record(browser, v, s); } };
  await Promise.all([run(), run(), run()]);
  await browser.close();
})();
