/*
 * Benflix Halloween: records the Who's watching room (server/web/js/halloween.js) as looping videos for the
 * TV and phone app, into extras/scenes/halloween/<stage>-<wide|tall>.mp4.
 * Stages: whole (1 to 10 Oct), half (11 to 17 Oct), carved (from 18 Oct), night (31 Oct after 5 PM, bats).
 *
 * Run it from the repository root after changing the room:
 *   npm install playwright   (once; any recent version)
 *   node tools/halloween/record.js               (all eight videos, about 20 minutes)
 *   node tools/halloween/record.js carved wide   (just some)
 *   node tools/halloween/record.js --still       (one picture of each, to check the drawing)
 * It needs ffmpeg. Then bump HalloweenVideos' version in the app (ui/setup/ChristmasRoom.kt) so TVs fetch the new videos.
 *
 * The room repeats every LOOP seconds, so one loop of pictures makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/halloween");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };
const STAGES = ["whole", "half", "carved", "night"];

async function record(browser, stage, shape, still) {
  const [w, h] = SHAPES[shape];
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `hw-${stage}-${shape}-`));
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/halloween.js") });
  const loop = await page.evaluate(([l, st]) => { window.S = BenflixHalloween.build(document.getElementById("s"), { layout: l, stage: st }); return BenflixHalloween.LOOP; }, [shape, stage]);
  fs.mkdirSync(OUT, { recursive: true });
  if (still) {
    await page.evaluate((t) => S.at(t), 5);
    await page.screenshot({ path: path.join(os.tmpdir(), `halloween-${stage}-${shape}.png`) });
    await page.close();
    console.log(`Still ${stage}-${shape}: ${path.join(os.tmpdir(), `halloween-${stage}-${shape}.png`)}`);
    return;
  }
  const frames = loop * FPS;
  for (let n = 0; n < frames; n++) {
    await page.evaluate((t) => S.at(t), n / FPS);
    await page.screenshot({ path: path.join(dir, `f${String(n).padStart(4, "0")}.png`) });
  }
  await page.close();
  const out = path.join(OUT, `${stage}-${shape}.mp4`);
  execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-framerate", String(FPS), "-i", path.join(dir, "f%04d.png"), "-c:v", "libx264", "-profile:v", "main", "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", "27", "-g", String(FPS * 2), "-movflags", "+faststart", "-an", out]);
  fs.rmSync(dir, { recursive: true, force: true });
  console.log(`Recorded ${stage}-${shape}: ${(fs.statSync(out).size / 1e6).toFixed(1)} MB`);
}

(async () => {
  const args = process.argv.slice(2);
  const still = args.includes("--still");
  const stages = STAGES.filter((s) => !args.some((a) => STAGES.includes(a)) || args.includes(s));
  const shapes = Object.keys(SHAPES).filter((s) => !args.some((a) => a in SHAPES) || args.includes(s));
  const browser = await chromium.launch();
  const jobs = [];
  for (const st of stages) for (const sh of shapes) jobs.push([st, sh]);
  // two at a time keeps memory steady
  while (jobs.length) await Promise.all(jobs.splice(0, 2).map(([st, sh]) => record(browser, st, sh, still)));
  await browser.close();
})();
