"use strict";
// Benflix: copies the ORIGINAL file into the backup folder before it gets replaced. Part of server/tdarr.
// Tower deletes backups by itself after a few days (setup-tdarr.sh sets that up).
Object.defineProperty(exports, "__esModule", { value: true });
exports.plugin = exports.details = void 0;
var fs = require("fs");
var path = require("path");

var details = function () { return ({
  name: "Benflix: keep a backup of the original",
  description: "Copies the original into the backup folder, same folders as in Media.",
  style: { borderColor: "#e50914" },
  tags: "",
  isStartPlugin: false,
  pType: "",
  requiresVersion: "2.11.01",
  sidebarPosition: -1,
  icon: "faArrowRight",
  inputs: [
    { label: "Backup folder", name: "backupFolder", type: "string", defaultValue: "/backup",
      inputUI: { type: "text" }, tooltip: "Empty = no backups" },
    { label: "Media folder in Tdarr", name: "mediaRoot", type: "string", defaultValue: "/media",
      inputUI: { type: "text" }, tooltip: "" },
  ],
  outputs: [{ number: 1, tooltip: "Continue" }],
}); };
exports.details = details;

var plugin = async function (args) {
  var inputs = args.inputs || {};
  var folder = String(inputs.backupFolder || "").replace(/\/+$/, "");
  if (!folder) {
    args.jobLog("Backups are off");
    return { outputFileObj: args.inputFileObj, outputNumber: 1, variables: args.variables };
  }
  var mediaRoot = String(inputs.mediaRoot || "/media").replace(/\/+$/, "");
  var original = args.originalLibraryFile._id;
  var rel = original.indexOf(mediaRoot + "/") === 0 ? original.slice(mediaRoot.length + 1) : path.basename(original);
  var dest = path.join(folder, rel);
  await fs.promises.mkdir(path.dirname(dest), { recursive: true });
  // A copy gets a fresh date, so "older than 3 days" counts from today, not from when it was downloaded
  await fs.promises.copyFile(original, dest + ".part");
  await fs.promises.rename(dest + ".part", dest);
  var now = new Date();
  await fs.promises.utimes(dest, now, now);
  var a = fs.statSync(original).size;
  var b = fs.statSync(dest).size;
  if (a !== b) throw new Error("Backup copy is incomplete, stopping so the original stays");
  args.jobLog("Backup saved: " + dest);
  return { outputFileObj: args.inputFileObj, outputNumber: 1, variables: args.variables };
};
exports.plugin = plugin;
