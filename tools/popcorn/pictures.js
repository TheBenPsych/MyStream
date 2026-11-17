/*
 * Benflix Popcorn Day: draws the door snacks and the small popcorn touches (from server/web/js/popcorn.js, the
 * approved sheet's drawings) as PNG pictures for the app (res/drawable-nodpi/pc_*.png) and web (server/web/img/pc/).
 *
 * Run it from the repository root after changing a drawing:
 *   npm install playwright   (once)
 *   node tools/popcorn/pictures.js
 */
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUTS = [path.join(ROOT, "app/src/main/res/drawable-nodpi"), path.join(ROOT, "server/web/img/pc")];
const BUTTER = "#F6C744";
// name: [viewBox, drawing, pixels wide]. Snacks stand on y=0, centred on x=0, about 100 tall.
const SNACK_BOX = "-58 -118 116 122";
const PICS = {
  pc_snack_movies: [SNACK_BOX, "K.bucket('butter',4)", 348],
  pc_snack_shows: [SNACK_BOX, "K.microBag('butter',5)", 348],
  pc_snack_live: [SNACK_BOX, "K.machine('butter',6)", 348],
  pc_snack_games: [SNACK_BOX, "K.bowl('plain',7)", 348],
  pc_snack_music: [SNACK_BOX, "K.tin('caramel',8)", 348],
  pc_snack_books: [SNACK_BOX, "K.theatreBox('butter',9)", 348],
  pc_puff: ["-36 -36 72 72", "K.puff(0,0,64,'butter',3,{nohull:true})", 144],
  pc_puff_hull: ["-36 -36 72 72", "K.puff(0,0,64,'butter',3)", 144],
  pc_kernel: ["-20 -20 40 40", "K.kernel(0,0,32,20)", 96],
  pc_ticket: ["-26 -26 52 52", `K.ticket(0,0,44,'${BUTTER}')`, 104],
  pc_bucket: ["-46 -104 92 108", "K.bucket('butter',21)", 184],
  pc_reel: ["-20 -20 40 40", `K.reel(0,0,18,'${BUTTER}')`, 96],
};

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent("<!doctype html><html><body style='margin:0;background:transparent'></body></html>");
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/popcorn.js") });
  OUTS.forEach((o) => fs.mkdirSync(o, { recursive: true }));
  for (const [name, [vb, code, px]] of Object.entries(PICS)) {
    const [, , w, h] = vb.split(" ").map(Number);
    const ph = Math.round(px * h / w);
    await page.setViewportSize({ width: px, height: ph });
    await page.evaluate(([vb2, code2, px2, ph2]) => {
      const K = BenflixPopcorn.kit;
      // eslint-disable-next-line no-eval
      document.body.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb2}" width="${px2}" height="${ph2}" style="display:block">${K.GDEF}${eval(code2)}</svg>`;
    }, [vb, code, px, ph]);
    const png = await page.screenshot({ omitBackground: true, clip: { x: 0, y: 0, width: px, height: ph } });
    OUTS.forEach((o) => fs.writeFileSync(path.join(o, `${name}.png`), png));
    console.log(name, px, "x", ph);
  }
  await browser.close();
})();
