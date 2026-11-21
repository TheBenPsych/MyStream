/* Glowfin desktop: the shell updates itself. GitHub builds a new download whenever the desktop folder
   changes and puts it on the "desktop-latest" release; this checks there when the app opens and a few times a
   day, downloads quietly, and swaps it in the next time the app is closed. (Glowfin's pages don't need this:
   they come from Tower and are always the newest.) */
"use strict";

const { app } = require("electron");

exports.start = (getWindow) => {
  if (!app.isPackaged) return; // running from the code folder while building it
  // A Linux copy that isn't the AppImage or the .deb can't replace itself
  let updater;
  try { updater = require("electron-updater").autoUpdater; } catch (e) { return; }
  updater.autoDownload = true;
  updater.autoInstallOnAppQuit = true;
  updater.allowPrerelease = false;
  updater.logger = null;
  updater.on("error", () => { /* no internet, or GitHub is busy: try again later */ });
  updater.on("update-downloaded", (info) => {
    const w = getWindow();
    if (w && !w.isDestroyed()) w.webContents.send("glowfin:update-ready", info && info.version);
  });
  const check = () => updater.checkForUpdates().catch(() => {});
  setTimeout(check, 15000);
  setInterval(check, 6 * 60 * 60 * 1000);
};
