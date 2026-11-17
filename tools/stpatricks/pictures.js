/*
 * Benflix St. Patrick's Day: draws the look's pictures (door charms and small touches) from the approved design
 * sheet's own drawing code, so the app and the web match the sheet exactly.
 *
 * Run it from the repository root:
 *   node tools/stpatricks/pictures.js [path to stpatricks-1-look.html]
 * (default: /mnt/project-files/benflix/themes/stpatricks-1-look.html). It writes
 * app/src/main/res/drawable-nodpi/sp_*.png and server/web/img/sp/sp_*.png.
 *
 * Door charms are drawn on the sheet's 240 x 220 door and cut out at x 116 to 248, y -6 to 154 (the charm
 * sits in the door's top right corner, see Modifier.spDoorCharm and html.sp .door::after).
 */
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const SHEET = process.argv[2] || "/mnt/project-files/benflix/themes/stpatricks-1-look.html";
const OUTS = [path.join(ROOT, "app/src/main/res/drawable-nodpi"), path.join(ROOT, "server/web/img/sp")];
const CHARM_BOX = [116, -6, 132, 160];

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.route(/^https?:/, (r) => r.abort());
  await page.setContent(fs.readFileSync(SHEET, "utf8"));
  // [name, svg drawing, viewBox or null to fit, width in pixels]
  const jobs = await page.evaluate((box) => {
    TOUCH = true; COL = "green";
    const list = Object.keys(CHARM).map((k) => [`sp_charm_${k}`, CHARM[k](), box, 396]);
    list.push(["sp_play_clover", `<g transform="rotate(14)">${clover(0, 0, 24, 4, 8, { c0: "#B8892A", c1: "#F2D27A", c2: "#C9962E", edge: "#5A400C" })}</g>`, null, 120]);
    list.push(["sp_coin", goldCoin(0, 0, 22 * 0.85), null, 96]);
    list.push(["sp_knob", `<circle cx="0" cy="0" r="19" fill="#fff"/>${clover(0, 1, 11, 3, 0, { flat: "#3DBE6C", edge: dk("#3DBE6C", 0.35), sw: 1 })}`, null, 88]);
    list.push(["sp_mark_horseshoe", noteMark("ep", 0, 0), null, 96]);
    list.push(["sp_mark_pot", noteMark("ready", 0, 0), null, 96]);
    list.push(["sp_mark_rainbow", noteMark("way", 0, 0), null, 96]);
    list.push(["sp_mark_hat", noteMark("hat", 0, 0), null, 96]);
    return list.map(([name, s, vb, px]) => {
      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.innerHTML = s; document.body.appendChild(svg);
      if (!vb) {
        // fit: a square around the drawing, with room for its shadow
        const b = svg.getBBox(); const side = Math.max(b.width, b.height) * 1.12;
        vb = [b.x + b.width / 2 - side / 2, b.y + b.height / 2 - side / 2, side, side];
      }
      svg.remove();
      return { name, s, vb, px };
    });
  }, CHARM_BOX);
  OUTS.forEach((d) => fs.mkdirSync(d, { recursive: true }));
  for (const j of jobs) {
    const h = Math.round(j.px * j.vb[3] / j.vb[2]);
    const p = await browser.newPage({ viewport: { width: j.px, height: h } });
    await p.setContent(`<!doctype html><html><head><style>html,body{margin:0;background:transparent}svg{display:block}</style></head><body><svg xmlns="http://www.w3.org/2000/svg" width="${j.px}" height="${h}" viewBox="${j.vb.join(" ")}">${j.s}</svg></body></html>`);
    const file = path.join(OUTS[0], `${j.name}.png`);
    await p.screenshot({ path: file, omitBackground: true });
    fs.copyFileSync(file, path.join(OUTS[1], `${j.name}.png`));
    await p.close();
    console.log(j.name, `${j.px}x${h}`);
  }
  await browser.close();
})();
