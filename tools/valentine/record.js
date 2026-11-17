/*
 * Benflix Valentine's Day: records the Who's watching room (server/web/js/valentine.js) as looping videos
 * for the TV and phone app, into extras/scenes/valentine/<name>-<wide|tall>.mp4:
 *   c13 .. c1  1 to 13 February, with that many chocolates left in the box (one goes each day)
 *   day        14 February: the last chocolate, and rose petals falling all day
 *
 * Run it from the repository root after changing the room:
 *   npm install playwright   (once; any recent version)
 *   node tools/valentine/record.js         (everything, about 10 minutes)
 *   node tools/valentine/record.js wide    (just the TV shape)
 * It needs ffmpeg. Then bump ValentineVideos' version in the app (ui/setup/ChristmasRoom.kt) so TVs fetch the new videos.
 *
 * To keep it quick, the room is recorded once with an empty box, and each day's chocolates are laid over it
 * (they don't move). The room repeats every LOOP seconds, so one loop makes a video that joins up with no jump.
 */
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "extras/scenes/valentine");
const FPS = 25;
const SHAPES = { wide: [1280, 720], tall: [540, 1170] };
const ENCODE = ["-c:v", "libx264", "-profile:v", "main", "-pix_fmt", "yuv420p", "-preset", "slow", "-crf", "27", "-g", String(FPS * 2), "-movflags", "+faststart", "-an"];
const ff = (args) => execFileSync("ffmpeg", ["-loglevel", "error", "-y", ...args]);

async function roomPage(browser, shape) {
  const [w, h] = SHAPES[shape];
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#000;overflow:hidden}svg{display:block;width:100vw;height:100vh}</style></head><body><svg id="s" xmlns="http://www.w3.org/2000/svg"></svg></body></html>`);
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/valentine.js") });
  return page;
}

/** one whole loop of the room into a video */
async function loop(browser, shape, variant, chocolates, out) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `valentine-${shape}-${variant}-`));
  const page = await roomPage(browser, shape);
  const secs = await page.evaluate(([l, v, c]) => { window.S = BenflixValentine.build(document.getElementById("s"), { layout: l, variant: v, chocolates: c }); return BenflixValentine.LOOP; }, [shape, variant, chocolates]);
  for (let n = 0; n < secs * FPS; n++) {
    await page.evaluate((t) => S.at(t), n / FPS);
    await page.screenshot({ path: path.join(dir, `f${String(n).padStart(4, "0")}.png`) });
  }
  await page.close();
  ff(["-framerate", String(FPS), "-i", path.join(dir, "f%04d.png"), ...ENCODE, out]);
  fs.rmSync(dir, { recursive: true, force: true });
}

/** a still of the room at t = 0 with this many chocolates */
async function still(browser, shape, chocolates, out) {
  const page = await roomPage(browser, shape);
  await page.evaluate(([l, c]) => { BenflixValentine.build(document.getElementById("s"), { layout: l, variant: "season", chocolates: c }).at(0); }, [shape, chocolates]);
  await page.screenshot({ path: out });
  await page.close();
}

async function record(browser, shape) {
  fs.mkdirSync(OUT, { recursive: true });
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), `valentine-${shape}-`));
  const base = path.join(tmp, "base.mp4");
  await Promise.all([loop(browser, shape, "season", 0, base), loop(browser, shape, "day", 1, path.join(OUT, `day-${shape}.mp4`))]);
  await still(browser, shape, 0, path.join(tmp, "empty.png"));
  for (let c = 1; c <= 13; c++) {
    // the chocolates alone: the pixels that differ from the empty box
    const full = path.join(tmp, `full${c}.png`), layer = path.join(tmp, `layer${c}.png`);
    await still(browser, shape, c, full);
    ff(["-i", full, "-i", path.join(tmp, "empty.png"), "-filter_complex", "[0][1]blend=all_mode=difference,format=gray,geq=lum='if(gt(lum(X,Y),3),255,0)'[m];[0]format=rgba[c];[c][m]alphamerge", layer]);
    ff(["-i", base, "-i", layer, "-filter_complex", "[0][1]overlay=0:0", ...ENCODE, path.join(OUT, `c${c}-${shape}.mp4`)]);
  }
  fs.rmSync(tmp, { recursive: true, force: true });
  console.log(`Recorded ${shape}`);
}

(async () => {
  const only = process.argv.slice(2);
  const shapes = Object.keys(SHAPES).filter((s) => !only.length || only.includes(s));
  const browser = await chromium.launch();
  await Promise.all(shapes.map((s) => record(browser, s)));
  await browser.close();
  for (const f of fs.readdirSync(OUT).sort()) console.log(f, (fs.statSync(path.join(OUT, f)).size / 1e6).toFixed(2), "MB");
})();
