/*
 * Benflix Friday the 13th: records the Who's watching room (server/web/js/f13.js) as looping videos for the
 * TV and phone app, into extras/scenes/f13/<wide|tall>.mp4, plus a short clip of just the cat waking up and
 * yawning (wake-<wide|tall>.mp4) that the app plays on top of the room when someone picks a profile.
 *
 * Run it from the repository root after changing the room:
 *   npm install playwright   (once; any recent version)
 *   node tools/f13/record.js         (both shapes, a few minutes)
 *   node tools/f13/record.js wide    (just one)
 * It needs ffmpeg. It prints where the cat clip sits in the video: copy those numbers into F13Videos in the app
 * (ui/setup/Friday13Room.kt) if they change, and bump its version so TVs fetch the new videos.
 *
 * The room repeats every LOOP seconds, so one loop of pictures makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/f13");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };
/** the cat clip is this long (a little longer than the wake-up, so it ends awake) */
const WAKE_SECONDS = 2;

function encode(dir, file, crf) {
  execFileSync("ffmpeg", ["-loglevel", "error", "-y", "-framerate", String(FPS), "-i", path.join(dir, "f%04d.png"), "-c:v", "libx264", "-profile:v", "main", "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", String(crf), "-g", String(FPS * 2), "-movflags", "+faststart", "-an", file]);
  fs.rmSync(dir, { recursive: true, force: true });
  console.log(`Recorded ${path.basename(file)}: ${(fs.statSync(file).size / 1e6).toFixed(2)} MB`);
}

async function record(browser, shape) {
  const [w, h] = SHAPES[shape];
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@600;800&display=swap"><style>html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.evaluate(() => document.fonts.ready);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/f13.js") });
  const loop = await page.evaluate((l) => { window.S = BenflixF13.build(document.getElementById("s"), { layout: l }); return BenflixF13.LOOP; }, shape);
  fs.mkdirSync(OUT, { recursive: true });

  // the room, one whole loop
  let dir = fs.mkdtempSync(path.join(os.tmpdir(), `f13-${shape}-`));
  const frames = loop * FPS;
  for (let n = 0; n < frames; n++) {
    await page.evaluate((t) => S.at(t), n / FPS);
    await page.screenshot({ path: path.join(dir, `f${String(n).padStart(4, "0")}.png`) });
  }
  encode(dir, path.join(OUT, `${shape}.mp4`), 27);

  // the cat waking up: only the cat's corner, which is still apart from the cat itself
  const T0 = 2;
  const box = await page.evaluate(([T0, secs]) => {
    let x1 = 1e9, y1 = 1e9, x2 = -1e9, y2 = -1e9;
    const grow = () => { const b = document.querySelector(".ccat").getBoundingClientRect(); x1 = Math.min(x1, b.left); y1 = Math.min(y1, b.top); x2 = Math.max(x2, b.right); y2 = Math.max(y2, b.bottom); };
    for (let t = 0; t < BenflixF13.LOOP; t += 0.25) { S.at(t); grow(); }
    S.wake(T0);
    for (let t = T0; t <= T0 + secs; t += 0.1) { S.at(t); grow(); }
    const pad = 8, even = (v) => Math.round(v / 2) * 2;
    const x = even(Math.max(0, x1 - pad)), y = even(Math.max(0, y1 - pad));
    return { x, y, w: even(Math.min(innerWidth, x2 + pad) - x), h: Math.min(even(innerHeight - y) - (even(innerHeight - y) > innerHeight - y ? 2 : 0), even(y2 + pad - y)) };
  }, [T0, WAKE_SECONDS]);
  dir = fs.mkdtempSync(path.join(os.tmpdir(), `f13-wake-${shape}-`));
  for (let n = 0; n < WAKE_SECONDS * FPS; n++) {
    await page.evaluate((t) => S.at(t), T0 + n / FPS);
    await page.screenshot({ path: path.join(dir, `f${String(n).padStart(4, "0")}.png`), clip: { x: box.x, y: box.y, width: box.w, height: box.h } });
  }
  encode(dir, path.join(OUT, `wake-${shape}.mp4`), 14);
  console.log(`Cat clip for ${shape} (${w}x${h} video): x ${box.x}, y ${box.y}, ${box.w} x ${box.h}`);
  await page.close();
}

(async () => {
  const only = process.argv.slice(2);
  const shapes = Object.keys(SHAPES).filter((s) => !only.length || only.includes(s));
  const browser = await chromium.launch();
  await Promise.all(shapes.map((s) => record(browser, s)));
  await browser.close();
})();
