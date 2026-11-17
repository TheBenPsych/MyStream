/*
 * Benflix Earth Day: draws the living things on the doors and the small Earth Day touches (from
 * tools/earthday/kit.js, the approved sheet's drawings) as PNG pictures for the app (res/drawable-nodpi/ed_*.png)
 * and the web (server/web/img/ed/).
 *
 * Run it from the repository root after changing a drawing:
 *   npm install playwright   (once)
 *   node tools/earthday/pictures.js
 * It prints where each door's picture sits on a 240 x 220 door. Those numbers are in EdLook.CHARM_BOX
 * (ui/theme/EarthDaySeason.kt) and the html.ed .door::after rules (server/web/css/benflix.css).
 */
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const OUTS = [path.join(ROOT, "app/src/main/res/drawable-nodpi"), path.join(ROOT, "server/web/img/ed")];
// pixels per drawing unit (a door is 240 units wide, so the pictures stay sharp on a 4K TV)
const SCALE = 2.5;
// name: [drawing, fixed viewBox or null to crop to the drawing, scale]
const PICS = {
  ed_life_movies: ["K.CHARM.movies()", null, SCALE],
  ed_life_shows: ["K.CHARM.shows()", null, SCALE],
  ed_life_live: ["K.CHARM.live()", null, SCALE],
  ed_life_games: ["K.CHARM.games()", null, SCALE],
  ed_life_music: ["K.CHARM.music()", null, SCALE],
  ed_life_books: ["K.CHARM.books()", null, SCALE],
  // the sprout growing up out of Play's top edge: its stem stands on y = 0
  ed_sprout: ["K.sprout(0,0,46)", "-46 -70 92 72", 3],
  ed_bloom: ["K.bloom(0,0,20)", "-22 -22 44 44", 4],
  ed_bud: ["K.bud(0,0,12)", "-14 -16 28 36", 5],
  ed_leaf: ["K.leaf(0,0,-40,24,13,K.GR[0],K.GR[1],{veins:false})", "-2 -18 22 20", 6],
  ed_globe: ["K.globe(0,0,50,.3)", "-52 -52 104 104", 2],
  // the turning Earth in the loading ring: the sea, one tile of land that slides across, and the shading on top
  ed_globe_sea: ["K.globeSea()", "-52 -52 104 104", 2],
  ed_globe_land: ["K.globeLand()", "-50 -50 100 100", 2],
  ed_globe_shade: ["K.globeShade()", "-52 -52 104 104", 2],
};

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent("<!doctype html><html><body style='margin:0;background:transparent'></body></html>");
  await page.addScriptTag({ path: path.join(__dirname, "kit.js") });
  await page.evaluate(() => {
    const K = window.EarthKit;
    // The globe in layers, so the land can turn inside the ball (the same shapes as K.globe)
    const parts = (r) => {
      const g = K.globe(0, 0, r, 0);
      const doc = new DOMParser().parseFromString(`<svg xmlns="http://www.w3.org/2000/svg">${g}</svg>`, "image/svg+xml");
      return doc;
    };
    K.globeSea = () => {
      const d = parts(50);
      d.querySelectorAll("g[clip-path]").forEach((n) => n.remove());
      [...d.documentElement.children].filter((n) => n.tagName === "circle" && /d\)$/.test(n.getAttribute("fill"))).forEach((n) => n.remove());
      [...d.documentElement.children].filter((n) => n.tagName === "ellipse").forEach((n) => n.remove());
      return d.documentElement.innerHTML;
    };
    K.globeLand = () => {
      const d = parts(50);
      const land = d.querySelector("g[clip-path] path");
      return `<path d="${land.getAttribute("d")}" transform="scale(50)" fill="#5DBB4E" stroke="#2E7A2C" stroke-width="0.024"/>`;
    };
    K.globeShade = () => {
      const d = parts(50);
      const g = d.querySelector("g[clip-path]");
      g.querySelectorAll("path").forEach((p, i) => { if (i < 2) p.remove(); });
      const sea = [...d.documentElement.children].find((n) => n.tagName === "circle" && /o\)$/.test(n.getAttribute("fill")));
      if (sea) sea.remove();
      return d.documentElement.innerHTML;
    };
  });
  OUTS.forEach((o) => fs.mkdirSync(o, { recursive: true }));
  for (const [name, [code, fixed, scale]] of Object.entries(PICS)) {
    let vb = fixed;
    if (!vb) {
      vb = await page.evaluate((c) => {
        const K = window.EarthKit;
        // eslint-disable-next-line no-eval
        document.body.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-60 -60 360 340" width="360" height="340">${K.GDEF}<g id="m">${eval(c)}</g></svg>`;
        const b = document.getElementById("m").getBBox();
        const pad = 6;
        const x = Math.floor(b.x - pad), y = Math.floor(b.y - pad);
        return `${x} ${y} ${Math.ceil(b.x + b.width + pad) - x} ${Math.ceil(b.y + b.height + pad) - y}`;
      }, code);
    }
    const [, , w, h] = vb.split(" ").map(Number);
    const px = Math.round(w * scale), ph = Math.round(h * scale);
    await page.setViewportSize({ width: px, height: ph });
    await page.evaluate(([c, vb2, px2, ph2]) => {
      const K = window.EarthKit;
      // eslint-disable-next-line no-eval
      document.body.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb2}" width="${px2}" height="${ph2}" style="display:block">${K.GDEF}${eval(c)}</svg>`;
    }, [code, vb, px, ph]);
    const png = await page.screenshot({ omitBackground: true, clip: { x: 0, y: 0, width: px, height: ph } });
    OUTS.forEach((o) => fs.writeFileSync(path.join(o, `${name}.png`), png));
    console.log(name.padEnd(16), `${px} x ${ph}`, fixed ? "" : `door box: x ${vb.split(" ")[0]}, y ${vb.split(" ")[1]}, w ${w}, h ${h}`);
  }
  await browser.close();
})();
