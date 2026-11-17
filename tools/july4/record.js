/*
 * Benflix 4th of July: records the Who's watching cookout room (server/web/js/july4.js) as looping videos
 * for the TV and phone app, into extras/scenes/july4/<day|night>-<wide|tall>.mp4.
 * "day" is 1 to 4 July, "night" is the big fireworks show from 9 PM on the 4th. Phones have no window, so
 * they only need day-tall.
 *
 * Run it from the repository root after changing the room:
 *   npm install playwright   (once; any recent version)
 *   node tools/july4/record.js               (all three videos)
 *   node tools/july4/record.js night-wide    (just one)
 * It needs ffmpeg. After re-recording, bump JulyVideos' version in the app (ui/setup/ChristmasRoom.kt) so
 * TVs fetch the new videos.
 *
 * The room repeats every LOOP seconds, so one loop of pictures makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/july4");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };
const VIDEOS = ["day-wide", "night-wide", "day-tall"];

async function record(browser, name) {
  const [variant, shape] = name.split("-");
  const [w, h] = SHAPES[shape];
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `july4-${name}-`));
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/july4.js") });
  const loop = await page.evaluate(([l, v]) => { window.S = BenflixJuly4.build(document.getElementById("s"), { layout: l, variant: v }); return BenflixJuly4.LOOP; }, [shape, variant]);
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
  const names = VIDEOS.filter((v) => !args.length || args.includes(v));
  const browser = await chromium.launch();
  await Promise.all(names.map((n) => record(browser, n)));
  await browser.close();
})();
