/* Glowfin for computers (Linux, Steam Deck, Windows).
   This is a thin shell: its own window, icon and full screen around the browser version of Glowfin that
   Tower serves (server/web in this repo). Every page comes from Tower, so new features and fixes show up
   here by themselves. The shell only adds what a browser tab can't do: finding Tower, full screen like a
   game, looking at this computer's hardware, and updating itself. */
"use strict";

const { app, BrowserWindow, ipcMain, shell, session, desktopCapturer, Menu, screen } = require("electron");
const path = require("path");
const fs = require("fs");
const os = require("os");
const http = require("http");
const https = require("https");
const A = require("./address");
const D = require("./downloads");

// The offline library gets its own kind of address (must be set up before the app starts)
D.registerScheme();

const CONFIG = path.join(app.getPath("userData"), "config.json");
const readConfig = () => {
  try { const c = JSON.parse(fs.readFileSync(CONFIG, "utf8")); return c && typeof c === "object" ? c : {}; } catch (e) { return {}; }
};
let config = readConfig();
const saveConfig = () => {
  try { fs.mkdirSync(path.dirname(CONFIG), { recursive: true }); fs.writeFileSync(CONFIG, JSON.stringify(config, null, 2)); } catch (e) { /* a full or read-only disk: keep going */ }
};

// A Steam Deck in Gaming Mode (or any PC started in Steam's Big Picture session) wants full screen like a game
const GAME_MODE = process.env.SteamDeck === "1" || process.env.XDG_CURRENT_DESKTOP === "gamescope" || !!process.env.GAMESCOPE_WAYLAND_DISPLAY;
const WANT_FULL = GAME_MODE || process.argv.includes("--fullscreen");

// Tower is reached with plain http at home. Browsers switch off some features there (sharing your screen,
// the microphone, keeping files), so tell the engine this one address is trusted. Must happen before start.
if (config.server) {
  const o = A.origin(config.server);
  if (o && o.startsWith("http://")) app.commandLine.appendSwitch("unsafely-treat-insecure-origin-as-secure", o);
}
// Videos and game sounds start without a click first (a controller press isn't a "click" to the engine)
app.commandLine.appendSwitch("autoplay-policy", "no-user-gesture-required");

let win = null;
const serverOrigin = () => (config.server ? A.origin(config.server) : "");
const isOurs = (url) => { const o = serverOrigin(); return !!o && A.origin(url) === o; };
// The shell's own pages: the "Find Tower" screen and the offline library
const isLocalPage = (url) => { const u = String(url || ""); return u.startsWith("file://") || u.startsWith("glowfin-offline://app/"); };

// ---------- talking to Tower ----------
// Asks an address "are you Glowfin?" (Tower's helper answers /health with ok: true)
function probe(base, ms) {
  return new Promise((resolve) => {
    let done = false;
    const finish = (v) => { if (!done) { done = true; resolve(v); } };
    let u;
    try { u = new URL(base.replace(/\/+$/, "") + "/health"); } catch (e) { return finish(false); }
    const req = (u.protocol === "https:" ? https : http).get(u, { timeout: ms || 5000, headers: { Accept: "application/json" } }, (res) => {
      let body = "";
      res.setEncoding("utf8");
      res.on("data", (d) => { body += d; if (body.length > 20000) req.destroy(); });
      res.on("end", () => { try { const j = JSON.parse(body); finish(!!(j && j.ok && (j.web_app || j.web))); } catch (e) { finish(false); } });
    });
    req.on("timeout", () => req.destroy());
    req.on("error", () => finish(false));
  });
}

// Looks through this computer's home network for Tower (anything answering as Glowfin on port 8097)
async function findTower() {
  const hosts = A.scanHosts(os.networkInterfaces());
  let found = null, i = 0;
  const worker = async () => {
    while (!found && i < hosts.length) {
      const h = hosts[i++];
      if (await probe(`http://${h}:8097`, 1500)) found = found || `http://${h}:8097`;
    }
  };
  await Promise.all(Array.from({ length: 48 }, worker));
  return found;
}

// ---------- the window ----------
function showConnect(why) {
  if (!win || win.isDestroyed()) return;
  win.loadFile(path.join(__dirname, "connect.html"), { query: { why: why || "first", server: config.server || "", downloads: D.hasAny() ? "1" : "" } });
}

async function openGlowfin() {
  if (!config.server) return showConnect("first");
  if (await probe(config.server, 6000)) return win.loadURL(config.server + "/");
  showConnect("offline");
}

function createWindow() {
  const b = config.bounds || {};
  const area = screen.getPrimaryDisplay().workAreaSize;
  win = new BrowserWindow({
    width: Math.min(b.width || 1280, area.width), height: Math.min(b.height || 800, area.height),
    x: b.x, y: b.y, minWidth: 640, minHeight: 400,
    backgroundColor: "#0B0D1A", show: false, autoHideMenuBar: true, title: "Glowfin",
    fullscreen: WANT_FULL || !!config.fullscreen,
    icon: path.join(__dirname, "icon.png"),
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false, sandbox: true, spellcheck: false },
  });
  Menu.setApplicationMenu(null);
  win.once("ready-to-show", () => { win.show(); if (b.maximized && !win.isFullScreen()) win.maximize(); });

  const remember = () => {
    if (!win || win.isDestroyed()) return;
    config.fullscreen = win.isFullScreen();
    if (!win.isFullScreen() && !win.isMinimized()) config.bounds = Object.assign(win.isMaximized() ? (config.bounds || {}) : win.getBounds(), { maximized: win.isMaximized() });
  };
  win.on("close", () => { remember(); saveConfig(); });
  win.on("page-title-updated", (e) => e.preventDefault());

  // Glowfin's own pages stay in this window; anything else (a link to another website) opens in the normal browser
  const outside = (url) => { if (/^https?:\/\//i.test(url)) shell.openExternal(url); };
  win.webContents.on("will-navigate", (e, url) => { if (!isOurs(url) && !isLocalPage(url)) { e.preventDefault(); outside(url); } });
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (isOurs(url)) return { action: "allow", overrideBrowserWindowOptions: { autoHideMenuBar: true, backgroundColor: "#0B0D1A", webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false, sandbox: true } } };
    outside(url);
    return { action: "deny" };
  });
  // Tower went away while the app was opening: show the friendly screen instead of a blank one
  win.webContents.on("did-fail-load", (e, code, desc, url, isMainFrame) => { if (isMainFrame && code !== -3 && !isLocalPage(url)) showConnect("offline"); });

  // Keys that work everywhere: F11 or Alt+Enter = full screen, F5 or Ctrl+R = reload, Ctrl+Q = quit
  win.webContents.on("before-input-event", (e, input) => {
    if (input.type !== "keyDown") return;
    const k = input.key, ctrl = input.control || input.meta;
    if (k === "F11" || (k === "Enter" && input.alt)) { e.preventDefault(); win.setFullScreen(!win.isFullScreen()); }
    else if (k === "F5" || (ctrl && (k === "r" || k === "R"))) { e.preventDefault(); openGlowfin(); }
    else if (ctrl && (k === "q" || k === "Q")) { e.preventDefault(); app.quit(); }
    else if (ctrl && (k === "=" || k === "+")) { e.preventDefault(); win.webContents.setZoomLevel(Math.min(3, win.webContents.getZoomLevel() + 0.5)); }
    else if (ctrl && k === "-") { e.preventDefault(); win.webContents.setZoomLevel(Math.max(-3, win.webContents.getZoomLevel() - 0.5)); }
    else if (ctrl && k === "0") { e.preventDefault(); win.webContents.setZoomLevel(0); }
  });
  openGlowfin();
}

// Starts the app again. The Linux download (an AppImage) unpacks itself into a temporary folder that goes
// away when the app closes, so it has to be started again from the downloaded file itself, not from that folder.
function restartApp() {
  const args = process.argv.slice(1).filter((a) => a !== "--relaunch");
  if (process.env.APPIMAGE) app.relaunch({ execPath: process.env.APPIMAGE, args });
  else app.relaunch({ args });
  app.exit(0);
}

// ---------- what the pages may ask the shell ----------
// Only Glowfin's own pages (Tower's address) and the built-in "Find Tower" screen get an answer
const trusted = (e) => { const u = e.senderFrame && e.senderFrame.url; return isOurs(u) || isLocalPage(u); };
const localOnly = (e) => isLocalPage(e.senderFrame && e.senderFrame.url);
const handle = (name, guard, fn) => ipcMain.handle(name, (e, ...args) => { if (!guard(e)) throw new Error("Not allowed"); return fn(e, ...args); });

handle("glowfin:info", trusted, () => ({ version: app.getVersion(), platform: process.platform, arch: process.arch, gameMode: GAME_MODE, server: config.server || "" }));
handle("glowfin:fullscreen", trusted, (e, on) => { if (win) win.setFullScreen(on === undefined ? !win.isFullScreen() : !!on); return !!(win && win.isFullScreen()); });
handle("glowfin:quit", trusted, () => app.quit());
handle("glowfin:reload", trusted, () => openGlowfin());
handle("glowfin:change-server", trusted, () => showConnect("change"));
// This computer's parts, so Games can say which consoles will run well here (like the TV and phone apps do)
handle("glowfin:hardware", trusted, async () => {
  const cpus = os.cpus() || [];
  let gpu = null;
  try { const g = await app.getGPUInfo("basic"); const d = (g.gpuDevice || []).find((x) => x.active) || (g.gpuDevice || [])[0]; if (d) gpu = { vendorId: d.vendorId, deviceId: d.deviceId }; } catch (err) { /* no graphics info: fine */ }
  return {
    platform: process.platform, arch: process.arch, steamDeck: process.env.SteamDeck === "1",
    cpu: (cpus[0] && cpus[0].model || "").trim(), cores: cpus.length, mhz: Math.max(0, ...cpus.map((c) => c.speed || 0)),
    ramGb: Math.round(os.totalmem() / 1073741824 * 10) / 10, gpu,
  };
});
// Downloads to watch with no connection (downloads.js). Only Tower's own pages can start one.
const fromTower = (e) => isOurs(e.senderFrame && e.senderFrame.url);
handle("glowfin:dl-list", trusted, () => D.list());
handle("glowfin:dl-start", fromTower, (e, job) => D.start(job));
handle("glowfin:dl-remove", trusted, (e, id) => D.remove(id));
handle("glowfin:dl-position", trusted, (e, id, seconds, done) => D.setPosition(id, seconds, done));
handle("glowfin:dl-open", trusted, (e, id) => { if (win) win.loadURL(D.PAGE + (/^[0-9a-f]{32}$/.test(String(id || "")) ? "#/play/" + id : "#/")); });
handle("glowfin:find-tower", localOnly, () => findTower());
handle("glowfin:connect", localOnly, async (e, typed) => {
  const base = A.normalize(typed);
  if (!base) return { ok: false, why: "That doesn't look like an address. Try something like 192.168.1.105" };
  if (!(await probe(base, 7000))) return { ok: false, why: "Nothing answered as Glowfin at " + base.replace(/^https?:\/\//, "") + ". Check Tower is on and this computer is on the same Wi-Fi or network." };
  const changed = A.origin(base) !== serverOrigin();
  config.server = base;
  saveConfig();
  // A new address has to be marked as trusted before the engine starts, so start the app again
  if (changed && base.startsWith("http://")) { restartApp(); return { ok: true }; }
  win.loadURL(base + "/");
  return { ok: true };
});

// ---------- start ----------
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on("second-instance", () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
  app.whenReady().then(() => {
    const ses = session.defaultSession;
    // Glowfin's pages may use full screen, the mouse for the 3D Lobby, sound and screen sharing. Nothing else may.
    const OK = ["fullscreen", "pointerLock", "media", "display-capture", "persistent-storage", "clipboard-sanitized-write", "keyboardLock", "speaker-selection", "window-management"];
    ses.setPermissionRequestHandler((wc, permission, cb, details) => cb(OK.includes(permission) && (isOurs(details.requestingUrl || wc.getURL()) || isLocalPage(wc.getURL()))));
    ses.setPermissionCheckHandler((wc, permission, requestingOrigin) => OK.includes(permission) && (isOurs(requestingOrigin) || isLocalPage(requestingOrigin) || !requestingOrigin));
    // Sharing your screen (the Lobby): the system's own picker where there is one, else the whole screen
    ses.setDisplayMediaRequestHandler((request, cb) => {
      desktopCapturer.getSources({ types: ["screen", "window"] }).then((sources) => (sources.length ? cb({ video: sources[0] }) : cb({}))).catch(() => cb({}));
    }, { useSystemPicker: true });
    D.init({ allowed: isOurs, notify: (m) => BrowserWindow.getAllWindows().forEach((w) => { if (!w.isDestroyed()) w.webContents.send("glowfin:dl-changed", m); }) });
    createWindow();
    require("./update").start(() => win);
  });
  app.on("window-all-closed", () => app.quit());
}
