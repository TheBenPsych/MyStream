/* Glowfin desktop: the few things Glowfin's pages can ask the shell for. Pages see it as
   window.glowfinDesktop; in a normal browser that doesn't exist, which is how the pages tell the two apart. */
"use strict";

const { contextBridge, ipcRenderer } = require("electron");

const call = (name, ...args) => ipcRenderer.invoke("glowfin:" + name, ...args);
const api = {
  isDesktop: true,
  info: () => call("info"),
  hardware: () => call("hardware"),
  fullscreen: (on) => call("fullscreen", on),
  quit: () => call("quit"),
  reload: () => call("reload"),
  changeServer: () => call("change-server"),
  onUpdateReady: (fn) => ipcRenderer.on("glowfin:update-ready", (e, version) => { try { fn(version); } catch (err) { /* the page's problem */ } }),
};
// Only the built-in "Find Tower" screen can look around the network or change the address
if (location.protocol === "file:") {
  api.findTower = () => call("find-tower");
  api.connect = (address) => call("connect", address);
}
contextBridge.exposeInMainWorld("glowfinDesktop", api);
