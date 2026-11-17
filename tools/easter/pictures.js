/*
 * Benflix Easter: draws the door charms, the hidden hunt eggs and the small Easter touches (from
 * server/web/js/easter.js, the approved sheets' drawings) as PNG pictures for the app
 * (res/drawable-nodpi/ea_*.png) and the web (server/web/img/ea/).
 *
 * Run it from the repository root after changing a drawing:
 *   npm install playwright   (once)
 *   node tools/easter/pictures.js
 */
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUTS = [path.join(ROOT, "app/src/main/res/drawable-nodpi"), path.join(ROOT, "server/web/img/ea")];
// Charms are drawn on a 240 x 220 door: this box (x 124 to 244, y 0 to 156) is the same in the app and web
const CHARM_BOX = "124 0 120 156";
const HUNT_BOX = "-30 -52 60 56";
// name: [viewBox, drawing, pixels wide]
const PICS = {
  ea_charm_movies: [CHARM_BOX, "K.CHARM.movies()", 360],
  ea_charm_shows: [CHARM_BOX, "K.CHARM.shows()", 360],
  ea_charm_live: [CHARM_BOX, "K.CHARM.live()", 360],
  ea_charm_games: [CHARM_BOX, "K.CHARM.games()", 360],
  ea_charm_music: [CHARM_BOX, "K.CHARM.music()", 360],
  ea_charm_books: [CHARM_BOX, "K.CHARM.books()", 360],
  ea_hunt_movies: [HUNT_BOX, "K.huntEgg('movies')", 180],
  ea_hunt_shows: [HUNT_BOX, "K.huntEgg('shows')", 180],
  ea_hunt_live: [HUNT_BOX, "K.huntEgg('live')", 180],
  ea_hunt_games: [HUNT_BOX, "K.huntEgg('games')", 180],
  ea_hunt_music: [HUNT_BOX, "K.huntEgg('music')", 180],
  ea_hunt_books: [HUNT_BOX, "K.huntEgg('books')", 180],
  ea_ears: ["-60 -98 120 100", "K.touches.ears()", 240],
  ea_knob: ["-26 -26 52 52", "K.touches.knob()", 104],
  ea_daisy: ["-26 -26 52 52", "K.touches.daisy()", 104],
  ea_loader: ["-70 -62 140 124", "K.touches.loader()", 280],
  ea_mark_ep: ["-22 -22 44 44", "K.touches.ep()", 96],
  ea_mark_ready: ["-22 -22 44 44", "K.touches.ready()", 96],
  ea_mark_way: ["-22 -22 44 44", "K.touches.way()", 96],
  ea_mark_live: ["-23 -18 46 46", "K.touches.live()", 96],
};

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent("<!doctype html><html><body style='margin:0;background:transparent'></body></html>");
  await page.addScriptTag({ path: path.join(ROOT, "server/web/js/easter.js") });
  OUTS.forEach((o) => fs.mkdirSync(o, { recursive: true }));
  for (const [name, [vb, code, px]] of Object.entries(PICS)) {
    const [, , w, h] = vb.split(" ").map(Number);
    const ph = Math.round(px * h / w);
    await page.setViewportSize({ width: px, height: ph });
    await page.evaluate(([vb2, code2, px2, ph2]) => {
      const K = BenflixEaster.kit;
      // eslint-disable-next-line no-eval
      document.body.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb2}" width="${px2}" height="${ph2}" style="display:block">${K.GDEF}${eval(code2)}</svg>`;
    }, [vb, code, px, ph]);
    const png = await page.screenshot({ omitBackground: true, clip: { x: 0, y: 0, width: px, height: ph } });
    OUTS.forEach((o) => fs.writeFileSync(path.join(o, `${name}.png`), png));
    console.log(name, px, "x", ph);
  }
  await browser.close();
})();
