"use strict";
// Benflix: writes one line per shrunk file to shrink-log.txt in the backup folder, for the daily check.
Object.defineProperty(exports, "__esModule", { value: true });
exports.plugin = exports.details = void 0;
var fs = require("fs");

var details = function () { return ({
  name: "Benflix: write it in the shrink log",
  description: "One line per file: when, which file, old size, new size.",
  style: { borderColor: "#e50914" },
  tags: "",
  isStartPlugin: false,
  pType: "",
  requiresVersion: "2.11.01",
  sidebarPosition: -1,
  icon: "faArrowRight",
  inputs: [
    { label: "Log file", name: "logFile", type: "string", defaultValue: "/backup/shrink-log.txt",
      inputUI: { type: "text" }, tooltip: "" },
  ],
  outputs: [{ number: 1, tooltip: "Continue" }],
}); };
exports.details = details;

var plugin = function (args) {
  var u = (args.variables && args.variables.user) || {};
  var a = Number(u.benflixOldSize || 0) / 1e9;
  var b = Number(u.benflixNewSize || 0) / 1e9;
  var d = new Date();
  var pad = function (n) { return (n < 10 ? "0" : "") + n; };
  var when = d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + " " + pad(d.getHours()) + ":" + pad(d.getMinutes());
  var score = u.benflixScore ? ", picture " + u.benflixScore : "";
  var line = when + "  " + a.toFixed(2) + " GB -> " + b.toFixed(2) + " GB  (saved " + (a - b).toFixed(2) + " GB" + score + ")  "
    + args.originalLibraryFile._id + "\n";
  try { fs.appendFileSync(String((args.inputs && args.inputs.logFile) || "/backup/shrink-log.txt"), line); } catch (e) {
    args.jobLog("Couldn't write the shrink log: " + e.message);
  }
  return { outputFileObj: args.inputFileObj, outputNumber: 1, variables: args.variables };
};
exports.plugin = plugin;
