#!/usr/bin/env node
// Glowfin games robot for the web version. Opens every game the browser has (the movie puzzles and the
// arcade games), at TV size (960x540, like Ben's TCL), computer size and phone size, plays each one for a
// while by clicking answers / pressing keys like a person would, and pictures it. It notes:
//   - javascript errors and "That game didn't start" / "Couldn't load" screens
//   - a game screen that stops changing (stuck)
//   - words cut off or covered, sideways scrolling (the same checks as the journeys robot)
//   - Play again on the end card not starting a new run
// Results: <out>/games-web.json plus the pictures.
//
// Usage: node games_web.js <base url> <out dir>   (needs: npm i playwright)
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");
const { look, settle, signIn } = require("./journeys_web.js");

const BASE = process.argv[2] || "http://127.0.0.1:8097/";
const OUT = process.argv[3] || "results/web";
fs.mkdirSync(OUT, { recursive: true });

const PUZZLES = ["SCENES", "POSTER", "GRID", "TIMELINE", "ACTOR", "QUOTE", "ROLE", "BLITZ", "SEASON", "GUESS", "RIDDLE", "HIGHER_LOWER"];
const ARCADE = ["POPCORN_POP", "REEL_RUSH", "SNACK_CATCH", "USHER_DASH", "FILM_CAN_STACK", "MATINEE_MATCH"];
const PARTY = ["QUIZ", "FAKE_PLOT", "DRAW", "CLOSEST", "CAPTION"];
const report = { started: new Date().toISOString(), games: [], problems: [] };
let shots = 0;
let cur = null;
const save = () => fs.writeFileSync(path.join(OUT, "games-web.json"), JSON.stringify(report, null, 1));

function problem(vp, game, type, text) {
  report.problems.push({ size: vp, game, type, text });
  console.log(`[${vp}] PROBLEM ${game} ${type}: ${text}`);
  save();
}

async function shot(page, vp, name, isPhone) {
  shots++;
  const file = `${String(shots).padStart(3, "0")}-${vp}-${cur.game}-${name}`.replace(/[^a-z0-9-]+/gi, "-").slice(0, 70) + ".jpg";
  try { await page.screenshot({ path: path.join(OUT, file), type: "jpeg", quality: 70 }); } catch (e) {}
  let seen = {};
  try { seen = await look(page, isPhone); } catch (e) { seen = { error: String(e) }; }
  const bad = await page.evaluate(() => {
    const t = document.body.innerText;
    const m = t.match(/That game didn't start[^\n]*\n?[^\n]*|Couldn't load from Tower|Something went wrong/);
    return m ? m[0] : "";
  }).catch(() => "");
  if (bad) problem(vp, cur.game, "error screen", `${name}: ${bad.replace(/\s+/g, " ").slice(0, 200)}`);
  if (seen.wide > 4) problem(vp, cur.game, "sideways scrolling", `${name}: ${seen.wide}px wider than the screen`);
  // only the game's own words count here (the side menu is checked by the journeys robot)
  const mine = (a) => (a || []).filter((x) => !/^\[poster name\]/.test(x));
  if (mine(seen.cut).length) problem(vp, cur.game, "words cut off", `${name}: ${mine(seen.cut).slice(0, 4).join(" | ")}`);
  if (mine(seen.covered).length) problem(vp, cur.game, "button covered", `${name}: ${mine(seen.covered).slice(0, 4).join(" | ")}`);
  cur.shots.push({ name, file, cut: seen.cut, covered: seen.covered, small: seen.small });
  save();
}

// What's on the game screen, without numbers (clocks tick even when nothing else moves)
const words = (page) => page.evaluate(() => {
  const s = document.querySelector("#bgStage") || document.body;
  return (s.innerText || "").replace(/\d+/g, "#").slice(0, 2000) + "|" + Array.from(s.querySelectorAll("img")).map((i) => i.src).join(",");
}).catch(() => "");

async function clickAnswer(page) {
  const sel = "#bgStage .bgq-choices button:not([disabled]), #bgStage .bg-slot, #bgStage .bg-cell:not(.done):not([disabled]), #bgStage .bg-mode:not([disabled]), #bgStage .bg-choice:not([disabled]), #bgStage .bg-card button:not([disabled]):not(#bgDone):not(#bgOpen):not(#bgList)";
  const els = page.locator(sel);
  const n = await els.count().catch(() => 0);
  if (!n) return false;
  const el = els.nth(Math.floor(Math.random() * n));
  await el.click({ timeout: 2000 }).catch(() => {});
  return true;
}

async function playPuzzle(page, vp, key, isPhone, size) {
  cur = { size: vp, game: key, kind: "puzzle", shots: [], notes: [] };
  report.games.push(cur);
  await page.goto(BASE + `#/bgame/${key}`);
  await settle(page, 8000);
  await shot(page, vp, "game-page", isPhone);
  if (isPhone) { await page.mouse.wheel(0, size.height * 0.8); await page.waitForTimeout(700); await shot(page, vp, "game-page-scrolled", isPhone); }
  await page.goto(BASE + `#/playb/${key}`);
  await page.waitForTimeout(3000);
  const t0 = Date.now();
  const at = [3, 14, 32];
  let last = "", same = Date.now(), over = false, again = false;
  while (Date.now() - t0 < 50000) {
    const t = (Date.now() - t0) / 1000;
    if (at.length && t >= at[0]) { at.shift(); await shot(page, vp, `playing-${Math.round(t)}s`, isPhone); }
    const txt = await page.evaluate(() => (document.querySelector("#bgStage") || document.body).innerText).catch(() => "");
    if (/Not enough to play yet|Couldn't load from Tower|That game didn't start/.test(txt)) {
      await shot(page, vp, "message", isPhone);
      cur.notes.push(txt.replace(/\s+/g, " ").slice(0, 160));
      break;
    }
    if (await page.locator("#bgAgain:visible").count()) {
      if (!over) { over = true; await page.waitForTimeout(500); await shot(page, vp, "run-over", isPhone); cur.notes.push(`a run ended after ${Math.round(t)} s`); }
      if (!again) {
        again = true;
        await page.click("#bgAgain").catch(() => {});
        await page.waitForTimeout(3000);
        await shot(page, vp, "play-again", isPhone);
        if (await page.locator("#bgAgain:visible").count()) problem(vp, key, "Play again did nothing", "the end card was still showing");
        continue;
      }
      break;
    }
    const w = await words(page);
    if (w !== last) { last = w; same = Date.now(); } else if (Date.now() - same > 25000) {
      await shot(page, vp, "looks-stuck", isPhone);
      problem(vp, key, "looks stuck", "nothing on the game screen changed for 25 s while clicking answers");
      break;
    }
    if (!(await clickAnswer(page))) {
      // keyboard players: arrows + Enter
      await page.keyboard.press(["ArrowRight", "ArrowDown", "ArrowLeft"][Math.floor(Math.random() * 3)]).catch(() => {});
      await page.keyboard.press("Enter").catch(() => {});
    }
    await page.waitForTimeout(1300);
  }
  if (!over) await shot(page, vp, "after-50s", isPhone);
}

async function playArcade(page, vp, key, isPhone, size) {
  cur = { size: vp, game: key, kind: "arcade", shots: [], notes: [] };
  report.games.push(cur);
  await page.goto(BASE + `#/bgame/${key}`);
  await settle(page, 8000);
  await shot(page, vp, "game-page", isPhone);
  await page.goto(BASE + `#/playb/${key}`);
  await page.waitForTimeout(2500);
  const t0 = Date.now();
  const at = [2, 10, 22];
  while (Date.now() - t0 < 30000) {
    const t = (Date.now() - t0) / 1000;
    if (at.length && t >= at[0]) { at.shift(); await shot(page, vp, `playing-${Math.round(t)}s`, isPhone); }
    if (isPhone) {
      const x = [0.2, 0.5, 0.8][Math.floor(Math.random() * 3)] * size.width;
      const y = [0.35, 0.55, 0.75][Math.floor(Math.random() * 3)] * size.height;
      await page.touchscreen.tap(x, y).catch(() => {});
    } else {
      const k = ["ArrowLeft", "ArrowRight", "Space", "Enter", "ArrowUp", "ArrowDown"][Math.floor(Math.random() * 6)];
      await page.keyboard.down(k).catch(() => {});
      await page.waitForTimeout(150 + Math.random() * 300);
      await page.keyboard.up(k).catch(() => {});
    }
    if (await page.locator("#bgAgain:visible").count()) {
      await shot(page, vp, "game-over", isPhone);
      await page.click("#bgAgain").catch(() => {});
      await page.waitForTimeout(1500);
    }
    await page.waitForTimeout(250);
  }
  await shot(page, vp, "after-30s", isPhone);
  await page.keyboard.press("Escape").catch(() => {});
  await page.waitForTimeout(800);
  await shot(page, vp, "escape-pressed", isPhone);
}

async function run(browser, vp, size, isPhone) {
  const ctx = await browser.newContext({ viewport: size, isMobile: isPhone, hasTouch: isPhone, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => problem(vp, cur ? cur.game : "start", "javascript error", String(e).slice(0, 300)));
  page.on("console", (m) => { if (m.type() === "error" && !/Failed to load resource|favicon|net::ERR/.test(m.text())) problem(vp, cur ? cur.game : "start", "console error", m.text().slice(0, 300)); });
  cur = { size: vp, game: "START", kind: "sign in", shots: [], notes: [] };
  if (!(await signIn(page).catch((e) => { problem(vp, "START", "sign-in", String(e).slice(0, 200)); return false; }))) {
    report.games.push(cur);
    await shot(page, vp, "could-not-sign-in", isPhone);
    return;
  }
  cur = { size: vp, game: "GAMES_PAGE", kind: "Games page", shots: [], notes: [] };
  report.games.push(cur);
  await page.goto(BASE + "#/games"); await settle(page, 8000);
  await shot(page, vp, "games", isPhone);
  for (let i = 1; i <= 3; i++) { await page.mouse.wheel(0, size.height * 0.8); await page.waitForTimeout(700); await shot(page, vp, `games-scroll-${i}`, isPhone); }
  for (const k of PUZZLES) { try { await playPuzzle(page, vp, k, isPhone, size); } catch (e) { problem(vp, k, "robot crashed", String(e).slice(0, 300)); } }
  for (const k of ARCADE) { try { await playArcade(page, vp, k, isPhone, size); } catch (e) { problem(vp, k, "robot crashed", String(e).slice(0, 300)); } }
  // party games play on the TV: their pages should say so
  for (const k of PARTY) {
    cur = { size: vp, game: k, kind: "party page", shots: [], notes: [] };
    report.games.push(cur);
    await page.goto(BASE + `#/bgame/${k}`); await settle(page, 6000);
    await shot(page, vp, "game-page", isPhone);
  }
  await ctx.close();
}

if (require.main === module) (async () => {
  const browser = await chromium.launch();
  const sizes = [["tv", { width: 960, height: 540 }, false], ["computer", { width: 1366, height: 768 }, false], ["phone", { width: 390, height: 844 }, true]];
  for (const [vp, size, isPhone] of sizes) {
    try { await run(browser, vp, size, isPhone); } catch (e) { problem(vp, "robot", "robot crashed", String(e).slice(0, 500)); }
  }
  await browser.close();
  save();
})();
