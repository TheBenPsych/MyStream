#!/usr/bin/env node
// Benflix web robot: signs in to the web version (served by the Benflix helper, talking to the public
// Jellyfin demo server), then visits every page it can find and presses every button, link, dropdown and
// switch on it, once at computer size and once at phone size. For each press it notes:
//   - JavaScript errors and "Something went wrong" screens
//   - helper/Jellyfin calls that came back with an error (400 and up)
//   - presses that did nothing at all (no new page, no pop-up, no change on screen, no call to the server)
//   - pages wider than the phone screen (sideways scrolling)
// Results: <out>/web-report.json plus a screenshot of every problem.
//
// Usage: node web_sweep.js <base url> <out dir>   (needs: npm i playwright)
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const BASE = process.argv[2] || "http://127.0.0.1:8097/";
const OUT = process.argv[3] || "results/web";
const USER = process.env.AUDIT_USER || "demo";
const MINUTES = +process.env.SWEEP_MINUTES || 50;
let DEADLINE = Date.now() + MINUTES * 60000;
fs.mkdirSync(OUT, { recursive: true });

// Never press these (they sign out, delete things or change the server for everyone)
const DANGER = /sign out|log ?out|delete|remove|forget|restart|shut ?down|reset|clear |erase|install update|update now|turn off .*for everyone|approve|deny|decline|block|ban|kick|stop (all|the server)|change server|quit and save/i;
// Pages that are the same kind of thing: only visit a couple of each (e.g. two movie pages, not fifty)
const KIND = (h) => h.replace(/\?.*$/, "").replace(/\/[0-9a-f]{20,}|\/\d+/gi, "/*");
const PER_KIND = 2;

const report = { started: new Date().toISOString(), viewports: {}, problems: [] };
let shots = 0;
const save = () => fs.writeFileSync(path.join(OUT, "web-report.json"), JSON.stringify(report, null, 1));

function problem(vp, p) {
  report.problems.push(Object.assign({ size: vp }, p));
  console.log(`[${vp}] ${p.type}: ${p.where || ""} ${p.button ? "→ " + p.button : ""} ${p.text ? "— " + String(p.text).slice(0, 200) : ""}`);
  save();
}

async function snap(page, vp, name) {
  shots++;
  const file = `${String(shots).padStart(3, "0")}-${vp}-${name.replace(/[^a-z0-9]+/gi, "-").slice(0, 60)}.jpg`;
  try { await page.screenshot({ path: path.join(OUT, file), type: "jpeg", quality: 60 }); } catch (e) {}
  return file;
}

async function settle(page, ms) {
  try { await page.waitForLoadState("networkidle", { timeout: ms || 6000 }); } catch (e) {}
  await page.waitForTimeout(500);
}

async function signIn(page) {
  await page.goto(BASE, { waitUntil: "domcontentloaded" });
  await settle(page, 8000);
  if (await page.locator("#addSomeone").count()) await page.click("#addSomeone");
  await page.waitForSelector("#v", { timeout: 20000 });
  await page.fill("#v", USER);
  await page.click("button[type=submit]");
  await page.waitForSelector("#v[type=password]", { timeout: 20000 });
  await page.click("#go");
  // first-time setup / Who's watching / Home
  for (let i = 0; i < 30; i++) {
    await page.waitForTimeout(1000);
    const h = await page.evaluate(() => location.hash);
    if (/#\/home/.test(h) && (await page.locator(".door").count())) return true;
    // press on through any first-time steps
    const btn = page.locator("button:visible", { hasText: /^(Skip for now|Next|Let's go|Done|Go to Home|Looks good|No thanks|Not now)$/ }).first();
    if (await btn.count()) { await btn.click().catch(() => {}); continue; }
    const person = page.locator(".person").first();
    if (await person.count()) await person.click().catch(() => {});
  }
  return /#\/home/.test(await page.evaluate(() => location.hash));
}

// Everything on the page a person could press, with a way to find it again after the page reloads
async function pressables(page) {
  return page.evaluate(() => {
    const sel = "button, a[href], select, input[type=checkbox], input[type=radio], input[type=range], [role=button], [onclick], [data-scroll], .person, .door, .poster, .card";
    const seen = new Set();
    const out = [];
    const all = Array.from(document.querySelectorAll(sel));
    for (const el of all) {
      const r = el.getBoundingClientRect();
      const st = getComputedStyle(el);
      if (r.width < 4 || r.height < 4 || st.visibility === "hidden" || st.display === "none" || el.disabled) continue;
      const label = (el.getAttribute("aria-label") || el.getAttribute("title") || el.innerText || el.value || el.getAttribute("href") || "").trim().replace(/\s+/g, " ").slice(0, 80);
      const tag = el.tagName.toLowerCase();
      const key = tag + "|" + label + "|" + (el.getAttribute("href") || "") + "|" + (el.id || "");
      if (seen.has(key)) continue;
      seen.add(key);
      // the n-th element with this exact label, so we can find it again
      const same = all.filter((x) => x.tagName === el.tagName && ((x.getAttribute("aria-label") || x.getAttribute("title") || x.innerText || x.value || x.getAttribute("href") || "").trim().replace(/\s+/g, " ").slice(0, 80)) === label);
      out.push({ tag, label, href: el.getAttribute("href") || "", id: el.id || "", nth: same.indexOf(el), inTop: !!el.closest("header.top"), options: tag === "select" ? Array.from(el.options).map((o) => o.value) : null });
    }
    return out;
  });
}

async function locate(page, p) {
  return page.evaluateHandle((p) => {
    const sel = "button, a[href], select, input[type=checkbox], input[type=radio], input[type=range], [role=button], [onclick], [data-scroll], .person, .door, .poster, .card";
    const lab = (x) => (x.getAttribute("aria-label") || x.getAttribute("title") || x.innerText || x.value || x.getAttribute("href") || "").trim().replace(/\s+/g, " ").slice(0, 80);
    const same = Array.from(document.querySelectorAll(sel)).filter((x) => x.tagName.toLowerCase() === p.tag && lab(x) === p.label);
    return same[p.nth] || same[0] || null;
  }, p);
}

async function state(page) {
  return page.evaluate(() => {
    const pg = document.getElementById("page");
    const html = (pg ? pg.innerHTML : "") + Array.from(document.querySelectorAll(".shade, .modal, .side, .menu-card, .dropdown, #toast:not(.hidden)")).map((e) => e.outerHTML).join("");
    let h = 0;
    for (let i = 0; i < html.length; i++) h = (h * 31 + html.charCodeAt(i)) | 0;
    const v = document.querySelector("video, audio");
    return { hash: location.hash, sig: h, popup: !!document.querySelector(".shade, .modal, .side, .menu-card, .dropdown"), toast: (document.querySelector("#toast:not(.hidden)") || {}).textContent || "", text: (pg ? pg.innerText : "").slice(0, 400), playing: !!(v && !v.paused), wide: document.documentElement.scrollWidth - window.innerWidth, scrollY: window.scrollY };
  });
}

async function sweepSize(browser, vp, size, isPhone) {
  const ctx = await browser.newContext({ viewport: size, isMobile: isPhone, hasTouch: isPhone, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  const stats = { pages: 0, presses: 0, nothing: 0, errors: 0 };
  report.viewports[vp] = stats;
  let current = "";
  let calls = 0;
  let lastPress = "";
  page.on("pageerror", (e) => problem(vp, { type: "JavaScript error", where: current, button: lastPress, text: String(e.message || e).slice(0, 500) }));
  page.on("console", (m) => { if (m.type() === "error" && !/Failed to load resource|favicon|net::ERR/.test(m.text())) problem(vp, { type: "console error", where: current, button: lastPress, text: m.text().slice(0, 500) }); });
  page.on("request", (r) => { if (!/\/Images\/|\.(png|jpg|jpeg|webp|woff2?|ttf|wav|mp3|css|js)(\?|$)/.test(r.url())) calls++; });
  page.on("response", (r) => {
    const u = r.url();
    if (r.status() >= 400 && u.startsWith(BASE) && !/\/Images\//.test(u)) {
      problem(vp, { type: `server said ${r.status()}`, where: current, button: lastPress, text: r.request().method() + " " + u.replace(BASE, "/").replace(/api_key=[^&]+/, "api_key=…") });
    }
  });

  if (!(await signIn(page))) {
    problem(vp, { type: "robot could not sign in", text: await page.evaluate(() => document.body.innerText.slice(0, 400)) });
    await snap(page, vp, "signin-failed");
    await ctx.close();
    return;
  }
  await snap(page, vp, "home");

  const queue = ["#/home", "#/search", "#/movies", "#/shows", "#/live", "#/games", "#/music", "#/books", "#/requests", "#/mine", "#/settings", "#/help", "#/server", "#/songs", "#/liked", "#/upnext", "#/browse/movies/genres", "#/browse/shows/genres", "#/browse/movies/studios", "#/grid/movies", "#/grid/shows"];
  const visited = new Set();
  const kinds = {};
  const pressedOnce = new Set(); // the top bar and things that are on many pages: press once per size

  const open = async (h) => {
    current = h;
    // a real reload every time (only changing the # would keep pop-ups and music from the last press)
    await page.goto("about:blank").catch(() => {});
    await page.goto(BASE + h, { waitUntil: "domcontentloaded" }).catch(() => {});
    await settle(page);
    const s = await state(page);
    if (/Something went wrong/.test(s.text)) {
      problem(vp, { type: "page shows Something went wrong", where: h, text: s.text.slice(0, 300), shot: await snap(page, vp, "wrong-" + h) });
    }
    return s;
  };

  while (queue.length && Date.now() < DEADLINE) {
    const h = queue.shift();
    if (visited.has(h)) continue;
    const kind = KIND(h);
    kinds[kind] = (kinds[kind] || 0) + 1;
    if (kinds[kind] > PER_KIND) continue;
    visited.add(h);
    const s0 = await open(h);
    if (s0.hash !== h && !visited.has(s0.hash)) {
      // the page sent us somewhere else (e.g. a door that isn't set up on the demo server)
      report.redirects = report.redirects || {};
      report.redirects[`${vp} ${h}`] = s0.hash;
    }
    stats.pages++;
    console.log(`[${vp}] page ${h} (${stats.pages})`);
    await snap(page, vp, "page-" + h);
    if (s0.wide > 4) problem(vp, { type: "page is wider than the screen", where: h, text: `${s0.wide}px too wide`, shot: await snap(page, vp, "wide-" + h) });

    // scroll down once so rows that load late show up too
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight)).catch(() => {});
    await page.waitForTimeout(1200);
    await page.evaluate(() => window.scrollTo(0, 0)).catch(() => {});
    let list = await pressables(page);
    // posters/cards: only press a few of each row type; buttons: all of them
    const posterish = (p) => /^#\/(movie|show|album|artist|playlist|mix|person|collection|book|game|console|watch|play)\//.test(p.href);
    let posters = 0;
    list = list.filter((p) => {
      const k = (p.inTop ? "top|" : "") + p.tag + "|" + p.label + "|" + (posterish(p) ? KIND(p.href) : p.href);
      if (p.inTop || /^#\/(home|search)$/.test(p.href)) { if (pressedOnce.has(k)) return false; pressedOnce.add(k); }
      if (posterish(p)) {
        if (!visited.has(p.href) && (kinds[KIND(p.href)] || 0) < PER_KIND && !queue.includes(p.href)) queue.push(p.href);
        return posters++ < 3;
      }
      return true;
    }).slice(0, 70);

    for (const p of list) {
      if (Date.now() > DEADLINE) break;
      if (DANGER.test(p.label)) continue;
      // links to other pages: just add them to the list of pages to visit (pressing them = opening them)
      if (p.tag === "a" && p.href.startsWith("#/") && !p.inTop) {
        const k = KIND(p.href);
        if (!visited.has(p.href) && (kinds[k] || 0) < PER_KIND && !queue.includes(p.href)) queue.push(p.href);
        continue;
      }
      // fresh page for every press, so one press can't hide another
      const before = await open(h);
      if (before.hash !== s0.hash) continue;
      const el = await locate(page, p);
      if (!el || !(await el.evaluate((x) => !!x).catch(() => false))) continue;
      lastPress = p.label || p.tag;
      calls = 0;
      const winsBefore = ctx.pages().length;
      stats.presses++;
      try {
        if (p.tag === "select") {
          const opts = (p.options || []).filter((o) => o !== (null));
          const other = opts.find((o, i) => i > 0) || opts[0];
          if (other != null) await el.asElement().selectOption(other, { timeout: 4000 });
        } else if (p.tag === "input" && /range/.test(await el.evaluate((x) => x.type))) {
          await el.evaluate((x) => { x.value = String(+x.min + (x.max - x.min) / 2); x.dispatchEvent(new Event("input", { bubbles: true })); x.dispatchEvent(new Event("change", { bubbles: true })); });
        } else {
          await el.asElement().scrollIntoViewIfNeeded({ timeout: 3000 }).catch(() => {});
          if (isPhone) await el.asElement().tap({ timeout: 4000 });
          else await el.asElement().click({ timeout: 4000 });
        }
      } catch (e) {
        const covered = /intercepts pointer events|not visible|outside of the viewport/.test(e.message);
        problem(vp, { type: covered ? "button can't be pressed (something covers it or it's off screen)" : "button press failed", where: h, button: lastPress, text: (e.message.split("\n").find((l) => /intercepts|not visible|outside|detached|disabled/.test(l)) || e.message.split("\n")[0]).trim().slice(0, 300), shot: await snap(page, vp, "cant-press-" + lastPress) });
        continue;
      }
      await page.waitForTimeout(1300);
      await settle(page, 4000);
      const after = await state(page);
      const newWin = ctx.pages().length > winsBefore;
      if (newWin) { for (const x of ctx.pages().slice(winsBefore)) await x.close().catch(() => {}); }
      if (after.hash !== before.hash) {
        const k = KIND(after.hash);
        if (!visited.has(after.hash) && (kinds[k] || 0) < PER_KIND && !queue.includes(after.hash) && !/^#\/(who|signin)/.test(after.hash)) queue.push(after.hash);
        if (/^#\/(who|signin)/.test(after.hash) && !/who|profile|switch|sign/i.test(lastPress)) {
          problem(vp, { type: "press sent us back to sign-in", where: h, button: lastPress, shot: await snap(page, vp, "signed-out-" + lastPress) });
          await signIn(page);
        }
      }
      if (/Something went wrong/.test(after.text) && !/Something went wrong/.test(before.text)) {
        problem(vp, { type: "press led to Something went wrong", where: h, button: lastPress, text: after.text.slice(0, 300), shot: await snap(page, vp, "wrong-" + lastPress) });
      }
      if (after.toast && /went wrong|couldn't|can't|error|failed|not found/i.test(after.toast)) {
        problem(vp, { type: "press showed an error message", where: h, button: lastPress, text: after.toast, shot: await snap(page, vp, "toast-" + lastPress) });
      }
      if (after.wide > 4 && isPhone) problem(vp, { type: "screen got wider than the phone after a press", where: h, button: lastPress, text: `${after.wide}px`, shot: await snap(page, vp, "wide-" + lastPress) });
      const sameLink = p.tag === "a" && p.href === before.hash;
      const nothing = !sameLink && after.hash === before.hash && after.sig === before.sig && !newWin && calls === 0 && after.scrollY === before.scrollY && !after.playing && p.tag !== "select" && !/scroll/i.test(p.label);
      if (nothing) {
        stats.nothing++;
        problem(vp, { type: "press did nothing", where: h, button: lastPress, shot: await snap(page, vp, "nothing-" + lastPress) });
      } else if (after.popup && !before.popup) {
        await snap(page, vp, "popup-" + lastPress);
        // press each button inside the pop-up once (menus, dropdowns, side panels)
        const inner = await page.evaluate(() => Array.from(document.querySelectorAll(".modal button, .side button, .menu-card button, .menu-card a, .dropdown button, .side a")).filter((b) => b.getBoundingClientRect().width > 2).map((b) => (b.getAttribute("title") || b.innerText || "").trim().replace(/\s+/g, " ").slice(0, 80)));
        report.popups = report.popups || {};
        report.popups[`${vp} ${h} → ${lastPress}`] = inner;
        for (const lab of inner.slice(0, 25)) {
          if (Date.now() > DEADLINE || DANGER.test(lab) || !lab) continue;
          const k = "popup|" + lastPress + "|" + lab;
          if (pressedOnce.has(k)) continue;
          pressedOnce.add(k);
          await open(h);
          const el2 = await locate(page, p);
          if (!el2) continue;
          try { await el2.asElement().click({ timeout: 3000 }); } catch (e) { continue; }
          await page.waitForTimeout(900);
          const b2 = await state(page);
          const btn = page.locator(".modal button, .side button, .menu-card button, .menu-card a, .dropdown button, .side a").filter({ hasText: lab }).first();
          if (!(await btn.count())) continue;
          lastPress = `${p.label} → ${lab}`;
          calls = 0;
          stats.presses++;
          try { await btn.click({ timeout: 3000 }); } catch (e) {
            problem(vp, { type: "button in pop-up can't be pressed", where: h, button: lastPress, text: e.message.split("\n")[0].slice(0, 200), shot: await snap(page, vp, "cant-press-" + lab) });
            continue;
          }
          await page.waitForTimeout(1300);
          await settle(page, 4000);
          const a2 = await state(page);
          if (a2.hash !== b2.hash) {
            const kk = KIND(a2.hash);
            if (!visited.has(a2.hash) && (kinds[kk] || 0) < PER_KIND && !queue.includes(a2.hash) && !/^#\/(who|signin)/.test(a2.hash)) queue.push(a2.hash);
          }
          if (a2.hash === b2.hash && a2.sig === b2.sig && calls === 0 && ctx.pages().length === winsBefore) {
            stats.nothing++;
            problem(vp, { type: "press did nothing", where: h, button: lastPress, shot: await snap(page, vp, "nothing-" + lab) });
          }
          if (/Something went wrong/.test(a2.text)) problem(vp, { type: "press led to Something went wrong", where: h, button: lastPress, text: a2.text.slice(0, 300), shot: await snap(page, vp, "wrong-" + lab) });
          if (a2.toast && /went wrong|couldn't|can't|error|failed|not found/i.test(a2.toast)) problem(vp, { type: "press showed an error message", where: h, button: lastPress, text: a2.toast });
          for (const x of ctx.pages().slice(winsBefore)) await x.close().catch(() => {});
        }
      }
    }
    save();
  }
  report.visited = report.visited || {};
  report.visited[vp] = Array.from(visited);
  report.left = report.left || {};
  report.left[vp] = queue.slice(0, 50);
  await ctx.close();
}

(async () => {
  const browser = await chromium.launch();
  // computer first (a bit more time), then phone
  DEADLINE = Date.now() + MINUTES * 0.55 * 60000;
  await sweepSize(browser, "computer", { width: 1366, height: 820 }, false).catch((e) => problem("computer", { type: "robot error", text: e.stack }));
  DEADLINE = Date.now() + MINUTES * 0.45 * 60000;
  await sweepSize(browser, "phone", { width: 390, height: 844 }, true).catch((e) => problem("phone", { type: "robot error", text: e.stack }));
  await browser.close();
  report.finished = new Date().toISOString();
  save();
  console.log(`done: ${report.problems.length} problems`);
})();
