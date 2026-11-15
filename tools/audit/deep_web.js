#!/usr/bin/env node
// Benflix "deep check" for the web version, against the private test Jellyfin from test_server.sh.
//   1. Tricky files: plays each test video, checks the picture really moves (video time goes up) and how
//      the server sends it (direct or converted).
//   2. Sound and subtitles: Settings (gear) > Spanish sound, Spanish subtitles; the server should see it.
//   3. Resume: after watching a while, the title page offers Resume.
//   4. Next episode: an episode near its end moves on to the next one by itself.
//   5. Bad Wi-Fi: Tower freezes (docker pause) for 30 s mid-movie, then comes back; does it carry on?
//   6. Kids: signed in as the Kids user, only the G-rated cartoon shows (doors and Search).
//   7. Cut-off words on every page at normal size and Easy mode (25% bigger), computer and phone size.
//   8. Speed: seconds for every page to finish loading.
// Results: <out>/deep-web.json plus pictures.
// Usage: node deep_web.js <base url> <out dir>   (Jellyfin on 127.0.0.1:8096)
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const { chromium } = require("playwright");
const { look, settle } = require("./journeys_web.js");

const BASE = process.argv[2] || "http://127.0.0.1:8097/";
const OUT = process.argv[3] || "results/deep-web";
const JF = "http://127.0.0.1:8096";
fs.mkdirSync(OUT, { recursive: true });
const report = { files: {}, timings: {}, pages: {}, problems: [], steps: [] };
const save = () => fs.writeFileSync(path.join(OUT, "deep-web.json"), JSON.stringify(report, null, 1));
let shots = 0;
const TITLES = ["Test Pattern One", "Big Color Test", "Surround Test", "Kids Cartoon Test"];

function problem(where, type, text) {
  report.problems.push({ where, type, text });
  console.log(`[${where}] PROBLEM ${type}: ${text}`);
  save();
}
async function shot(page, name) {
  shots++;
  const file = `${String(shots).padStart(3, "0")}-${name.replace(/[^a-z0-9]+/gi, "-").slice(0, 60)}.jpg`;
  try { await page.screenshot({ path: path.join(OUT, file), type: "jpeg", quality: 65 }); } catch (e) {}
  report.steps.push({ name, file });
  return file;
}
function docker(...args) {
  try { execFileSync("docker", args, { timeout: 120000 }); console.log("docker", args.join(" ")); return true; } catch (e) { console.log("docker failed", args, String(e).slice(0, 200)); return false; }
}

// ---- the test server, asked directly ----
let token = null;
const AUTH = 'MediaBrowser Client="deep-robot", Device="runner", DeviceId="deep-robot", Version="1"';
async function api(p, method, body) {
  if (!token && !p.startsWith("/Users/AuthenticateByName")) {
    const r = await api("/Users/AuthenticateByName", "POST", { Username: "demo", Pw: "" });
    token = r.AccessToken;
  }
  try {
    const r = await fetch(JF + p, { method: method || "GET", headers: { "Content-Type": "application/json", "X-Emby-Authorization": AUTH + (token ? `, Token="${token}"` : "") }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(15000) });
    const t = await r.text();
    return t ? JSON.parse(t) : {};
  } catch (e) { return { error: String(e).slice(0, 200) }; }
}
async function playInfo() {
  const s = await api("/Sessions?ActiveWithinSeconds=120");
  if (!Array.isArray(s)) return null;
  const x = s.find((z) => z.DeviceId !== "deep-robot" && z.NowPlayingItem);
  if (!x) return null;
  const ps = x.PlayState || {}, t = x.TranscodingInfo || {};
  return { item: x.NowPlayingItem.Name, seconds: Math.round((ps.PositionTicks || 0) / 1e6) / 10, method: ps.PlayMethod, audio_index: ps.AudioStreamIndex, subtitle_index: ps.SubtitleStreamIndex, converting_because: t.TranscodeReasons, video: t.VideoCodec, audio: t.AudioCodec };
}
async function itemByName(name) {
  const r = await api(`/Items?Recursive=true&SearchTerm=${encodeURIComponent(name)}&IncludeItemTypes=Movie,Series,Episode&Fields=UserData`);
  return ((r && r.Items) || []).find((i) => i.Name === name) || null;
}
async function episodes() {
  const r = await api("/Items?Recursive=true&IncludeItemTypes=Episode&SortBy=IndexNumber");
  return (r && r.Items) || [];
}

async function signIn(page, user) {
  await page.goto(BASE, { waitUntil: "domcontentloaded" });
  await settle(page, 8000);
  if (await page.locator("#addSomeone").count()) await page.click("#addSomeone");
  await page.waitForSelector("#nm", { timeout: 30000 });
  await page.fill("#nm", user);
  await page.click("#go");
  for (let i = 0; i < 40; i++) {
    await page.waitForTimeout(1000);
    const h = await page.evaluate(() => location.hash);
    if (/#\/home/.test(h) && (await page.locator(".door").count())) return true;
    const btn = page.locator("button:visible", { hasText: /^(Skip for now|Next|Let's go|Done|Go to Home|Looks good|No thanks|Not now)$/ }).first();
    if (await btn.count()) { await btn.click().catch(() => {}); continue; }
  }
  return /#\/home/.test(await page.evaluate(() => location.hash));
}

async function video(page) {
  return page.evaluate(() => {
    const v = document.querySelector("video");
    if (!v) return null;
    return { t: Math.round(v.currentTime * 10) / 10, paused: v.paused, ready: v.readyState, error: v.error && v.error.message, w: v.videoWidth, h: v.videoHeight };
  }).catch(() => null);
}
async function waitPlaying(page, label, secs) {
  const start = Date.now();
  while (Date.now() - start < (secs || 40) * 1000) {
    const v = await video(page);
    if (v && v.t > 0.5 && !v.paused) return Math.round((Date.now() - start) / 100) / 10;
    await page.waitForTimeout(500);
  }
  return null;
}
async function play(page, id, label, resume) {
  await page.goto(BASE + `#/play/${id}${resume ? "?resume=1" : ""}`);
  const t = await waitPlaying(page, label);
  report.timings[`press Play to picture moving: ${label}`] = t;
  if (t === null) problem("computer", "the video never started playing", `${label}: ${JSON.stringify(await video(page))} ${await page.evaluate(() => document.body.innerText.slice(0, 200)).catch(() => "")}`);
  return t;
}

async function trickyFiles(page) {
  for (const name of TITLES) {
    const it = await itemByName(name);
    if (!it) { problem("computer", "test title missing on the server", name); continue; }
    const s = Date.now();
    await page.goto(BASE + `#/movie/${it.Id}`); await settle(page, 8000);
    report.timings[`title page: ${name}`] = Math.round((Date.now() - s) / 100) / 10;
    await shot(page, `file-${name}-title`);
    const info = { start: await play(page, it.Id, name) };
    await page.waitForTimeout(3000);
    await shot(page, `file-${name}-playing`);
    const a = await video(page); await page.waitForTimeout(10000); const b = await video(page);
    Object.assign(info, { first: a, second: b, server: await playInfo(), moves: !!(a && b && b.t - a.t > 5) });
    if (info.start !== null && !info.moves) problem("computer", "video stuck (time not moving)", `${name}: ${JSON.stringify(a)} -> ${JSON.stringify(b)}`);
    report.files[name] = info; save();
    await page.goBack().catch(() => {}); await settle(page);
  }
}

async function soundAndSubs(page) {
  const it = await itemByName("Test Pattern One");
  if (!it || (await play(page, it.Id, "Test Pattern One (switching)")) === null) return;
  await page.waitForTimeout(4000);
  const before = await playInfo();
  await page.mouse.move(600, 400);
  await page.click("#pGear").catch(() => problem("computer", "no Settings (gear) button on the player", ""));
  await page.waitForTimeout(800);
  await shot(page, "switch-settings-panel");
  const spanishAudio = page.locator("button[data-a]", { hasText: /Spanish|Español|spa/i }).first();
  if (await spanishAudio.count()) { await spanishAudio.click(); await page.waitForTimeout(7000); } else problem("computer", "no Spanish sound choice in the player Settings", "");
  await shot(page, "switch-audio-spanish");
  const afterAudio = await playInfo();
  await page.mouse.move(600, 420);
  await page.click("#pGear").catch(() => {});
  await page.waitForTimeout(800);
  const spanishSub = page.locator("button[data-s]", { hasText: /Spanish|Español|spa/i }).first();
  if (await spanishSub.count()) { await spanishSub.click(); await page.waitForTimeout(12000); } else problem("computer", "no Spanish subtitle choice in the player Settings", "");
  await shot(page, "switch-subtitles-spanish");
  const afterSubs = await playInfo();
  const shown = await page.evaluate(() => Array.from(document.querySelectorAll("video track, .subs, #pSubsText")).map((e) => e.label || e.innerText || "").join(" | ")).catch(() => "");
  report.switching = { before, afterAudio, afterSubs, shown, video: await video(page) };
  if (before && afterAudio && before.audio_index === afterAudio.audio_index) problem("computer", "switching the sound to Spanish didn't change it on the server", JSON.stringify([before, afterAudio]));
  if (before && afterSubs && before.subtitle_index === afterSubs.subtitle_index) problem("computer", "switching subtitles to Spanish didn't change them on the server", JSON.stringify([before, afterSubs]));
  const a = await video(page); await page.waitForTimeout(6000); const b = await video(page);
  if (!(a && b && b.t > a.t + 3)) problem("computer", "video stuck after switching sound/subtitles", JSON.stringify([a, b]));
  await page.goBack().catch(() => {}); await settle(page);
}

async function resume(page) {
  const it = await itemByName("Test Pattern One");
  if (!it) return;
  await play(page, it.Id, "Test Pattern One (resume)");
  await page.waitForTimeout(60000); // Jellyfin only remembers places past 5% of the video
  await page.goto(BASE + `#/movie/${it.Id}`); await settle(page, 8000);
  await page.waitForTimeout(2000);
  await shot(page, "resume-title-page");
  const saved = Math.round((((await itemByName("Test Pattern One")) || {}).UserData || {}).PlaybackPositionTicks / 1e7 || 0);
  const resumeBtn = await page.locator("button:visible, a:visible", { hasText: /Resume|Keep watching|Continue/i }).count();
  report.resume = { savedSeconds: saved, resumeButton: resumeBtn > 0 };
  if (saved < 10) problem("computer", "watching a while didn't save the place on the server", `saved ${saved}s`);
  else if (!resumeBtn) problem("computer", "title page shows no Resume after watching part of it", "");
  await page.goto(BASE + "#/home"); await settle(page, 8000);
  await shot(page, "resume-home");
}

async function nextEpisode(page) {
  const eps = await episodes();
  if (eps.length < 2) return problem("computer", "test show episodes missing", String(eps.length));
  await play(page, eps[0].Id, "Test Show E1");
  await page.evaluate(() => { const v = document.querySelector("video"); if (v) v.currentTime = Math.max(0, (v.duration || 75) - 8); });
  const seen = [];
  const end = Date.now() + 70000;
  let shotUpNext = false;
  while (Date.now() < end) {
    const h = await page.evaluate(() => location.hash);
    const p = await playInfo();
    const label = `${h} ${p ? p.item : ""}`;
    if (!seen.includes(label)) seen.push(label);
    if (!shotUpNext && (await page.locator("#pNext:not(.hidden)").count())) { shotUpNext = true; await shot(page, "episode-up-next"); }
    if (!h.includes(eps[0].Id) && h.includes("/play/")) break;
    await page.waitForTimeout(2000);
  }
  await page.waitForTimeout(4000);
  await shot(page, "episode-after-end");
  const moved = await page.evaluate(() => location.hash);
  report.nextEpisode = { seen, now: moved, video: await video(page) };
  if (!moved.includes(eps[1].Id)) problem("computer", "the next episode didn't start by itself", JSON.stringify(seen));
  await page.goto(BASE + "#/home"); await settle(page);
}

async function badWifi(page) {
  const it = await itemByName("Surround Test");
  if (!it || (await play(page, it.Id, "Surround Test (bad Wi-Fi)")) === null) return;
  await page.waitForTimeout(5000);
  await shot(page, "wifi-before");
  docker("pause", "jellyfin");
  for (const at of [8, 30]) { await page.waitForTimeout(at === 8 ? 8000 : 22000); await shot(page, `wifi-frozen-${at}s`); }
  const during = await video(page);
  docker("unpause", "jellyfin");
  let back = null; const t0 = Date.now();
  while (Date.now() - t0 < 60000) {
    const a = await video(page); await page.waitForTimeout(3000); const b = await video(page);
    if (a && b && b.t > a.t + 1.5 && !b.paused) { back = Math.round((Date.now() - t0) / 100) / 10; break; }
  }
  await shot(page, "wifi-back");
  report.badWifi = { during, secondsToCarryOn: back, after: await video(page), text: await page.evaluate(() => document.body.innerText.slice(0, 300)).catch(() => "") };
  if (back === null) problem("computer", "after Tower came back, the movie didn't carry on by itself", JSON.stringify(report.badWifi));
  // Tower off while browsing
  await page.goto(BASE + "#/home"); await settle(page);
  docker("stop", "jellyfin");
  for (const door of ["movies", "shows", "search"]) {
    await page.goto(BASE + `#/${door}`); await page.waitForTimeout(9000);
    await shot(page, `tower-off-${door}`);
    const text = await page.evaluate(() => document.body.innerText.replace(/\s+/g, " ").slice(0, 300)).catch(() => "");
    report[`towerOff-${door}`] = text;
    if (text.length < 20) problem("computer", "blank page with Tower off", door);
  }
  docker("start", "jellyfin");
  for (let i = 0; i < 60; i++) { const r = await api("/System/Info/Public"); if (r && r.Version) break; await new Promise((r) => setTimeout(r, 2000)); }
  token = null;
  await page.goto(BASE + "#/movies"); await settle(page, 15000);
  await page.waitForTimeout(3000);
  await shot(page, "tower-back-on-movies");
  const ok = await page.locator(".poster:visible").count();
  report.towerBackOn = { posters: ok };
  if (!ok) problem("computer", "Movies stayed empty after Tower came back on", "");
}

// every page, at normal words and Easy mode: cut-off words, covered buttons, sideways scroll, speed
async function everyPage(page, vp, isPhone, tag) {
  const it = await itemByName("Test Pattern One"), show = await itemByName("Test Show");
  const pages = ["home", "movies", "shows", "live", "music", "games", "books", "requests", "search", "search?q=test", "settings", "settings/you", "help", "mine", "who"]
    .concat(it ? [`movie/${it.Id}`] : []).concat(show ? [`show/${show.Id}`] : []);
  for (const p of pages) {
    const s = Date.now();
    await page.goto(BASE + "#/" + p);
    // ready = no spinner left and some words on the page (checked every 0.1 s, up to 15 s)
    let secs = null;
    while (Date.now() - s < 15000) {
      const ready = await page.evaluate(() => !document.querySelector(".spin") && document.body.innerText.trim().length > 40).catch(() => false);
      if (ready) { secs = Math.round((Date.now() - s) / 100) / 10; break; }
      await page.waitForTimeout(100);
    }
    await settle(page, 4000);
    await page.evaluate(() => scrollTo(0, 0)); await page.waitForTimeout(300);
    const where = await page.evaluate(() => { const p = document.querySelector(".top .place"); return p ? p.innerText.replace(/\s+/g, " ").trim() : ""; }).catch(() => "");
    report.timings[`${vp} ${tag} page ${p}`] = secs;
    const file = await shot(page, `${vp}-${tag}-${p}`);
    const seen = await look(page, isPhone).catch((e) => ({ error: String(e) }));
    report.pages[`${vp} ${tag} ${p}`] = Object.assign({ file, seconds: secs, where }, seen);
    if (secs === null) problem(vp, "page still loading after 15 seconds", `${tag} ${p}`);
    if (seen.wide > 4) problem(vp, "sideways scrolling", `${tag} ${p}: ${seen.wide}px`);
    const broken = await page.evaluate(() => /Something went wrong/.test(document.body.innerText)).catch(() => false);
    if (broken) problem(vp, "Something went wrong screen", `${tag} ${p}`);
    save();
  }
}

async function setEasy(page, on) {
  await page.evaluate((on) => { B.setWebSetting("easy", on); B.applyLook(); }, on).catch((e) => problem("robot", "could not set Easy mode", String(e)));
}

async function kids(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1366, height: 768 } });
  const page = await ctx.newPage();
  if (!(await signIn(page, "Kids"))) { await shot(page, "kids-no-sign-in"); problem("kids", "robot", "could not sign in as Kids"); return ctx.close(); }
  await shot(page, "kids-home");
  const found = {};
  for (const p of ["movies", "shows", "search?q=Test"]) {
    await page.goto(BASE + "#/" + p); await settle(page, 8000);
    await page.waitForTimeout(1500);
    await shot(page, `kids-${p}`);
    const text = await page.evaluate(() => document.body.innerText + " " + Array.from(document.querySelectorAll("[title], img[alt]")).map((e) => e.title || e.alt).join(" "));
    found[p] = TITLES.concat(["Test Show"]).filter((t) => text.includes(t));
  }
  report.kids = found;
  const bad = Array.from(new Set(Object.values(found).flat().filter((t) => t !== "Kids Cartoon Test")));
  if (bad.length) problem("kids", "Kids can see grown-up titles", bad.join(", "));
  await ctx.close();
}

(async () => {
  // Google Chrome (like people use) plays H.264/AAC; the plain Chromium build can't, so it's only the fallback
  const opts = { args: ["--autoplay-policy=no-user-gesture-required"] };
  let browser;
  try { browser = await chromium.launch(Object.assign({ channel: "chrome" }, opts)); report.browser = "chrome"; } catch (e) { browser = await chromium.launch(opts); report.browser = "chromium"; }
  const ctx = await browser.newContext({ viewport: { width: 1366, height: 768 } });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => problem("computer", "javascript error", String(e).slice(0, 300)));
  try {
    const s = Date.now();
    if (!(await signIn(page, "demo"))) { await shot(page, "no-sign-in"); problem("computer", "robot", "could not sign in"); }
    report.timings["sign in to Home"] = Math.round((Date.now() - s) / 100) / 10;
    for (const [name, fn] of [["tricky files", trickyFiles], ["sound and subtitles", soundAndSubs], ["resume", resume], ["next episode", nextEpisode], ["bad wifi", badWifi]]) {
      console.log("===", name);
      try { await fn(page); } catch (e) { problem("computer", "robot error", `${name}: ${String(e).slice(0, 300)}`); }
    }
    await everyPage(page, "computer", false, "normal");
    await setEasy(page, true);
    await everyPage(page, "computer", false, "easy");
    await setEasy(page, false);
  } catch (e) { problem("computer", "robot crashed", String(e).slice(0, 500)); }
  await ctx.close();
  try {
    const pctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    const ph = await pctx.newPage();
    ph.on("pageerror", (e) => problem("phone", "javascript error", String(e).slice(0, 300)));
    if (await signIn(ph, "demo")) {
      await everyPage(ph, "phone", true, "normal");
      await setEasy(ph, true);
      await everyPage(ph, "phone", true, "easy");
    } else problem("phone", "robot", "could not sign in");
    await pctx.close();
  } catch (e) { problem("phone", "robot crashed", String(e).slice(0, 500)); }
  try { await kids(browser); } catch (e) { problem("kids", "robot crashed", String(e).slice(0, 500)); }
  await browser.close();
  save();
})();
