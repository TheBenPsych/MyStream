/*
 * Benflix Popcorn Day: records the Who's watching popcorn room (server/web/js/popcorn.js) as looping videos
 * for the TV and phone app, into extras/scenes/popcorn/<am|pm|eve>-<wide|tall>.mp4 (the heap grows through the day).
 *
 * Run it from the repository root after changing the room:
 *   npm install playwright   (once; any recent version)
 *   node tools/popcorn/record.js            (all six videos, a few minutes)
 *   node tools/popcorn/record.js eve-wide   (just one)
 *   node tools/popcorn/record.js --still    (one picture of each into build/popcorn-stills, to check the drawing)
 * It needs ffmpeg. Then bump PopcornVideos' version in the app (ui/setup/ChristmasRoom.kt) so TVs fetch the new videos.
 *
 * The room repeats every LOOP seconds, so one loop of pictures makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/popcorn");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };
const DAYS = ["am", "pm", "eve"];
const font = (name, file) => `@font-face{font-family:"${name}";src:url(data:font/ttf;base64,${fs.readFileSync(path.join(ROOT, "server/web/fonts", file)).toString("base64")}) format("truetype")}`;
const FONTS = font("Benflix Xmas", "xmas_berkshire_swash.ttf") + font("Outfit", "outfit_bold.ttf");

async function open(browser, shape, day) {
  const [w, h] = SHAPES[shape];
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${FONTS}html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/popcorn.js") });
  await page.evaluate(() => document.fonts.ready);
  const loop = await page.evaluate(([l, d]) => { window.S = BenflixPopcorn.build(document.getElementById("s"), { layout: l, day: d }); return BenflixPopcorn.LOOP; }, [shape, day]);
  return { page, loop };
}

async function record(browser, shape, day) {
  const name = `${day}-${shape}`;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `popcorn-${name}-`));
  const { page, loop } = await open(browser, shape, day);
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
  const args = process.argv.slice(2);
  const still = args.includes("--still");
  const only = args.filter((a) => !a.startsWith("--"));
  const jobs = [];
  for (const d of DAYS) for (const s of Object.keys(SHAPES)) if (!only.length || only.includes(`${d}-${s}`)) jobs.push([s, d]);
  const browser = await chromium.launch();
  if (still) {
    const dir = path.join(ROOT, "build/popcorn-stills");
    fs.mkdirSync(dir, { recursive: true });
    for (const [s, d] of jobs) {
      const { page } = await open(browser, s, d);
      await page.evaluate(() => S.at(5.2));
      await page.screenshot({ path: path.join(dir, `${d}-${s}.png`) });
      await page.close();
    }
  } else {
    for (let i = 0; i < jobs.length; i += 3) await Promise.all(jobs.slice(i, i + 3).map(([s, d]) => record(browser, s, d)));
  }
  await browser.close();
})();
