#!/usr/bin/env node
// Benflix "normal person" web robot. Instead of pressing every button (web_sweep.js does that), it walks the
// everyday trips a person makes: Home -> a door -> a movie -> Play -> back, a show's episodes, Search,
// the profile menu and Settings. It takes a picture of every step at computer size and phone size so a
// person (or Claude) can look at them, and it notes things a person would trip over:
//   - words cut off with "..." or hidden by their box (ground rule 3: words never cut)
//   - buttons covered by something else (you'd press the wrong thing)
//   - buttons too small to tap on a phone (under 40 px)
//   - sideways scrolling on a phone
//   - Back not going where you'd expect (not back to the same page / same place on the page)
// Results: <out>/journeys-web.json plus the pictures.
//
// Usage: node journeys_web.js <base url> <out dir>   (needs: npm i playwright)
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const BASE = process.argv[2] || "http://127.0.0.1:8097/";
const OUT = process.argv[3] || "results/web";
const USER = process.env.AUDIT_USER || "demo";
fs.mkdirSync(OUT, { recursive: true });

const report = { started: new Date().toISOString(), steps: [], problems: [] };
let shots = 0;
const save = () => fs.writeFileSync(path.join(OUT, "journeys-web.json"), JSON.stringify(report, null, 1));

async function settle(page, ms) {
  try { await page.waitForLoadState("networkidle", { timeout: ms || 6000 }); } catch (e) {}
  await page.waitForTimeout(800);
}

// What a person would trip over on the screen right now
async function look(page, isPhone) {
  return page.evaluate((isPhone) => {
    const vis = (el) => {
      const r = el.getBoundingClientRect();
      const st = getComputedStyle(el);
      return r.width > 2 && r.height > 2 && st.visibility !== "hidden" && st.display !== "none" && +st.opacity > 0.05 &&
        r.bottom > 0 && r.right > 0 && r.top < innerHeight && r.left < innerWidth;
    };
    const lab = (el) => (el.getAttribute("aria-label") || el.getAttribute("title") || el.innerText || "").trim().replace(/\s+/g, " ").slice(0, 60);
    const out = { cut: [], covered: [], small: [], wide: document.documentElement.scrollWidth - innerWidth };
    // cut-off words: text boxes whose words don't fit (ellipsis or clipped)
    for (const el of document.querySelectorAll("body *")) {
      if (!vis(el) || !el.childNodes.length) continue;
      const own = Array.from(el.childNodes).some((n) => n.nodeType === 3 && n.textContent.trim().length > 2);
      if (!own) continue;
      const st = getComputedStyle(el);
      const clips = st.overflow !== "visible" || st.textOverflow === "ellipsis" || st.webkitLineClamp !== "none";
      if (clips && (el.scrollWidth > el.clientWidth + 2 || el.scrollHeight > el.clientHeight + 3)) {
        // poster names under posters are allowed one line; still list them, marked as captions
        out.cut.push((el.closest(".cap") ? "[poster name] " : "") + lab(el));
      }
    }
    // covered buttons: the middle of a button is under something else that isn't part of it
    const sel = "button, a[href], select, input, [role=button], .door, .poster";
    for (const el of document.querySelectorAll(sel)) {
      if (!vis(el)) continue;
      const r = el.getBoundingClientRect();
      const x = Math.min(innerWidth - 1, Math.max(0, r.left + r.width / 2));
      const y = Math.min(innerHeight - 1, Math.max(0, r.top + r.height / 2));
      if (r.top < 0 || r.bottom > innerHeight) continue; // half off the screen: scrolling fixes it
      const top = document.elementFromPoint(x, y);
      if (top && top !== el && !el.contains(top) && !top.contains(el)) {
        const cover = top.closest("header, nav, .bar, .shade, .modal, .side, [class]") || top;
        out.covered.push(`${lab(el) || el.tagName} under ${cover.tagName.toLowerCase()}.${(cover.className || "").toString().split(" ")[0]}`);
      }
      if (isPhone && (r.width < 40 || r.height < 40) && !el.closest(".cap")) out.small.push(`${lab(el) || el.tagName} (${Math.round(r.width)}x${Math.round(r.height)})`);
    }
    const uniq = (a) => Array.from(new Set(a)).slice(0, 25);
    out.cut = uniq(out.cut);
    out.covered = uniq(out.covered);
    out.small = uniq(out.small);
    return out;
  }, isPhone);
}

async function step(page, vp, name, isPhone, extra) {
  shots++;
  const file = `${String(shots).padStart(3, "0")}-${vp}-${name.replace(/[^a-z0-9]+/gi, "-").slice(0, 50)}.jpg`;
  try { await page.screenshot({ path: path.join(OUT, file), type: "jpeg", quality: 65 }); } catch (e) {}
  let seen = {};
  try { seen = await look(page, isPhone); } catch (e) { seen = { error: String(e) }; }
  const s = Object.assign({ size: vp, step: name, file, hash: await page.evaluate(() => location.hash).catch(() => ""), scrollY: await page.evaluate(() => scrollY).catch(() => 0) }, seen, extra || {});
  const broken = await page.evaluate(() => /Something went wrong/.test(document.body.innerText) ? document.body.innerText.slice(0, 300) : "").catch(() => "");
  if (broken) problem(vp, "Something went wrong screen", `${name}: ${broken.replace(/\s+/g, " ")}`);
  report.steps.push(s);
  console.log(`[${vp}] ${name} ${s.hash} cut:${(s.cut || []).length} covered:${(s.covered || []).length} small:${(s.small || []).length} wide:${s.wide}`);
  save();
  return s;
}

function problem(vp, type, text) {
  report.problems.push({ size: vp, type, text });
  console.log(`[${vp}] PROBLEM ${type}: ${text}`);
  save();
}

async function signIn(page) {
  await page.goto(BASE, { waitUntil: "domcontentloaded" });
  await settle(page, 8000);
  if (await page.locator("#addSomeone").count()) await page.click("#addSomeone");
  // Redesign 2 sign-in: one form (Name + Password + Sign in); older one-question-at-a-time screens as a fallback
  await page.waitForSelector("#nm, #v", { timeout: 30000 });
  if (await page.locator("#nm").count()) {
    await page.fill("#nm", USER);
    await page.click("#go");
  } else {
    await page.fill("#v", USER);
    await page.click("button[type=submit]");
    await page.waitForSelector("#v[type=password]", { timeout: 20000 });
    await page.click("#go");
  }
  for (let i = 0; i < 40; i++) {
    await page.waitForTimeout(1000);
    const h = await page.evaluate(() => location.hash);
    if (/#\/home/.test(h) && (await page.locator(".door").count())) return true;
    const btn = page.locator("button:visible", { hasText: /^(Skip for now|Next|Let's go|Done|Go to Home|Looks good|No thanks|Not now)$/ }).first();
    if (await btn.count()) { await btn.click().catch(() => {}); continue; }
    const person = page.locator(".person").first();
    if (await person.count()) await person.click().catch(() => {});
  }
  return /#\/home/.test(await page.evaluate(() => location.hash));
}

async function clickText(page, rx) {
  const el = page.locator("button:visible, a:visible, [role=button]:visible, .door:visible", { hasText: rx }).first();
  if (!(await el.count())) return false;
  await el.click({ timeout: 5000 }).catch(() => {});
  await settle(page);
  return true;
}

async function trip(browser, vp, size, isPhone) {
  const ctx = await browser.newContext({ viewport: size, isMobile: isPhone, hasTouch: isPhone, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => problem(vp, "javascript error", String(e).slice(0, 300)));
  if (!(await signIn(page).catch((e) => { problem(vp, "sign-in", String(e).slice(0, 200)); return false; }))) { await step(page, vp, "could-not-sign-in", isPhone); problem(vp, "robot", "could not sign in"); return; }
  await settle(page, 8000);
  await step(page, vp, "home", isPhone);
  for (let i = 1; i <= 3; i++) { await page.mouse.wheel(0, size.height * 0.8); await page.waitForTimeout(700); await step(page, vp, `home-scroll-${i}`, isPhone); }
  await page.evaluate(() => scrollTo(0, 0));

  // 1. A movie: Movies door -> scroll a little -> open a poster -> look -> Play -> Back -> Back
  for (const door of [["movies", /^\s*Movies\s*$/i], ["shows", /^\s*Shows\s*$/i]]) {
    await page.goto(BASE + "#/home"); await settle(page);
    if (!(await clickText(page, door[1]))) await page.goto(BASE + "#/" + door[0]);
    await settle(page, 8000);
    await step(page, vp, `${door[0]}-door`, isPhone);
    await page.mouse.wheel(0, size.height * 0.6); await page.waitForTimeout(800);
    const doorY = await page.evaluate(() => scrollY);
    await step(page, vp, `${door[0]}-door-scrolled`, isPhone);
    const poster = page.locator(".poster:visible").nth(1);
    if (!(await poster.count())) { problem(vp, "no posters", door[0]); continue; }
    const name = await poster.getAttribute("title");
    await poster.click().catch(() => {});
    await settle(page, 8000);
    await step(page, vp, `${door[0]}-title-page`, isPhone, { opened: name });
    await page.mouse.wheel(0, size.height * 0.7); await page.waitForTimeout(800);
    await step(page, vp, `${door[0]}-title-page-scrolled`, isPhone);
    await page.evaluate(() => scrollTo(0, 0));
    // the tabs under the buttons (Overview / Episodes / More like this ...)
    const tabs = await page.locator(".tabs button:visible, [role=tab]:visible").allInnerTexts().catch(() => []);
    for (const t of tabs.slice(0, 5)) {
      await clickText(page, new RegExp("^\\s*" + t.trim().replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\s*$"));
      await step(page, vp, `${door[0]}-tab-${t.trim()}`, isPhone);
    }
    if (door[0] === "shows") {
      const ep = page.locator(".ep:visible, .episode:visible, [data-ep]:visible").first();
      if (await ep.count()) { await ep.click().catch(() => {}); await settle(page); await step(page, vp, "shows-episode-picked", isPhone); }
    }
    const titleHash = await page.evaluate(() => location.hash);
    if (await clickText(page, /^\s*(Play|Resume|Play from start|Watch)\b/i)) {
      await page.waitForTimeout(8000);
      await step(page, vp, `${door[0]}-playing`, isPhone);
      await page.mouse.move(size.width / 2, size.height / 2);
      await page.waitForTimeout(600);
      await step(page, vp, `${door[0]}-player-controls`, isPhone);
      await page.goBack().catch(() => {}); await settle(page);
      const back1 = await step(page, vp, `${door[0]}-back-from-player`, isPhone);
      if (back1.hash !== titleHash) problem(vp, "Back from the player", `went to ${back1.hash}, expected the title page ${titleHash}`);
    } else problem(vp, "no Play button on first screen", `${door[0]}: ${name}`);
    await page.goBack().catch(() => {}); await settle(page);
    const back2 = await step(page, vp, `${door[0]}-back-to-door`, isPhone);
    if (!back2.hash.includes(door[0]) && !back2.hash.includes("home")) problem(vp, "Back from a title page", `went to ${back2.hash}`);
    else if (Math.abs((back2.scrollY || 0) - doorY) > 150) problem(vp, "Back loses your place", `${door[0]} door was scrolled to ${doorY}, came back at ${back2.scrollY}`);
  }

  // 2. Search: type, look at results, open one, come back (are the words still there?)
  await page.goto(BASE + "#/home"); await settle(page);
  if (!(await clickText(page, /^\s*Search\s*$/i))) await page.goto(BASE + "#/search");
  await settle(page);
  await step(page, vp, "search-empty", isPhone);
  const box = page.locator("#sq");
  if (await box.count()) {
    await box.type("the", { delay: 120 });
    await page.waitForTimeout(2500);
    await step(page, vp, "search-typed", isPhone);
    const hit = page.locator(".poster:visible").first();
    if (await hit.count()) {
      await hit.click().catch(() => {}); await settle(page);
      await step(page, vp, "search-opened-result", isPhone);
      await page.goBack().catch(() => {}); await settle(page);
      const s = await step(page, vp, "search-back", isPhone);
      const v = await page.locator("#sq").inputValue().catch(() => "");
      if (v !== "the") problem(vp, "Back from a search result", `the search words were "${v}" instead of "the"`);
    }
  }

  // 3. The other doors, quickly
  for (const d of [["live", /^\s*Live TV\s*$/i], ["music", /^\s*Music\s*$/i], ["games", /^\s*Games\s*$/i], ["books", /^\s*Books\s*$/i], ["requests", /^\s*Requests\s*$/i]]) {
    await page.goto(BASE + "#/home"); await settle(page);
    if (!(await clickText(page, d[1]))) await page.goto(BASE + "#/" + d[0]);
    await settle(page, 8000);
    await step(page, vp, `${d[0]}-door`, isPhone);
    await page.mouse.wheel(0, size.height * 0.7); await page.waitForTimeout(700);
    await step(page, vp, `${d[0]}-door-scrolled`, isPhone);
  }

  // 4. Profile menu -> Settings -> Back
  await page.goto(BASE + "#/home"); await settle(page);
  // The profile picture: top bar on old layouts, the bottom of the side rail since the redesign.
  // On a phone it sits behind More.
  let me = page.locator('#meBtn:visible, .top .me:visible, [data-k="me"]:visible').first();
  if (!(await me.count()) && isPhone && (await clickText(page, /^\s*More\s*$/i))) {
    await page.waitForTimeout(600);
    me = page.locator('[data-k="me"]:visible, .sheet a[href="#/me"]:visible').first();
  }
  if (await me.count()) {
    await me.click().catch(() => {}); await page.waitForTimeout(800);
    await step(page, vp, "profile-menu", isPhone);
    if (await clickText(page, /^\s*Settings\s*$/i)) {
      await step(page, vp, "settings", isPhone);
      await page.mouse.wheel(0, size.height * 0.8); await page.waitForTimeout(700);
      await step(page, vp, "settings-scrolled", isPhone);
      await page.goBack().catch(() => {}); await settle(page);
      await step(page, vp, "settings-back", isPhone);
    }
    await page.keyboard.press("Escape");
  } else problem(vp, "robot", "could not find the profile picture button");

  // 5. Keyboard only (a computer person who uses arrow keys / Tab): does the focus show?
  if (!isPhone) {
    await page.goto(BASE + "#/home"); await settle(page);
    for (let i = 1; i <= 6; i++) { await page.keyboard.press("Tab"); await page.waitForTimeout(250); }
    const f = await page.evaluate(() => {
      const el = document.activeElement;
      if (!el || el === document.body) return { none: true };
      const st = getComputedStyle(el);
      return { label: (el.innerText || el.getAttribute("aria-label") || "").trim().slice(0, 40), ring: st.outlineStyle !== "none" || st.boxShadow !== "none" };
    });
    await step(page, vp, "keyboard-tab-6", isPhone, { focus: f });
    if (f.none || !f.ring) problem(vp, "keyboard focus hard to see", JSON.stringify(f));
  }
  // flag repeats: same cut/covered/small text across many steps once each
  const once = (k) => Array.from(new Set(report.steps.filter((s) => s.size === vp).flatMap((s) => s[k] || [])));
  report[`${vp}-cut`] = once("cut");
  report[`${vp}-covered`] = once("covered");
  report[`${vp}-small`] = once("small");
  const wide = report.steps.filter((s) => s.size === vp && s.wide > 4).map((s) => `${s.step} (${s.wide}px)`);
  if (wide.length) problem(vp, "sideways scrolling", wide.join(", "));
  save();
  await ctx.close();
}

module.exports = { look, settle, signIn };

if (require.main === module) (async () => {
  const browser = await chromium.launch();
  try {
    await trip(browser, "computer", { width: 1366, height: 768 }, false);
  } catch (e) { problem("computer", "robot crashed", String(e).slice(0, 500)); }
  try {
    await trip(browser, "phone", { width: 390, height: 844 }, true);
  } catch (e) { problem("phone", "robot crashed", String(e).slice(0, 500)); }
  await browser.close();
  save();
})();
