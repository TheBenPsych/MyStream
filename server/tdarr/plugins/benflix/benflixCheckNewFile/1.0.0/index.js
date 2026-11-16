"use strict";
// Benflix: checks the shrunk file before it may replace the original. Part of server/tdarr.
// Output 1 = good, go ahead. Output 2 = something is off, the original stays exactly as it was.
Object.defineProperty(exports, "__esModule", { value: true });
exports.plugin = exports.details = void 0;
var fs = require("fs");
var cp = require("child_process");

var details = function () { return ({
  name: "Benflix: check the new file",
  description: "The new file must be smaller, the same length, and still have its video and sound.",
  style: { borderColor: "#e50914" },
  tags: "video",
  isStartPlugin: false,
  pType: "",
  requiresVersion: "2.11.01",
  sidebarPosition: -1,
  icon: "faQuestion",
  inputs: [
    { label: "New file at most (% of the old size)", name: "maxPercent", type: "number", defaultValue: "90",
      inputUI: { type: "text" }, tooltip: "Not worth it if it only got a little smaller" },
    { label: "New file at least (% of the old size)", name: "minPercent", type: "number", defaultValue: "10",
      inputUI: { type: "text" }, tooltip: "Much smaller than this usually means something went wrong" },
  ],
  outputs: [
    { number: 1, tooltip: "Good" },
    { number: 2, tooltip: "Keep the original" },
  ],
}); };
exports.details = details;

var probe = function (args, file) {
  var ffprobe = "ffprobe";
  if (args.ffmpegPath && /ffmpeg$/.test(args.ffmpegPath)) {
    var guess = args.ffmpegPath.replace(/ffmpeg$/, "ffprobe");
    if (fs.existsSync(guess)) ffprobe = guess;
  }
  var out = cp.spawnSync(ffprobe, ["-v", "error", "-print_format", "json", "-show_format", "-show_streams", file],
    { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  if (out.status !== 0) throw new Error("ffprobe couldn't read the file: " + String(out.stderr || "").slice(0, 300));
  return JSON.parse(out.stdout);
};

var count = function (streams, type) {
  return streams.filter(function (s) {
    return s.codec_type === type && !(s.disposition && Number(s.disposition.attached_pic) === 1);
  }).length;
};

var plugin = function (args) {
  var inputs = args.inputs || {};
  var oldFile = args.originalLibraryFile._id;
  var newFile = args.inputFileObj._id;
  var bad = function (why) {
    args.jobLog("Keeping the original: " + why);
    try { if (newFile !== oldFile) fs.unlinkSync(newFile); } catch (e) { /* the cache gets cleaned anyway */ }
    return { outputFileObj: args.originalLibraryFile, outputNumber: 2, variables: args.variables };
  };
  if (newFile === oldFile) return bad("nothing was made");
  var oldSize = fs.statSync(oldFile).size;
  var newSize = fs.statSync(newFile).size;
  var pct = (100 * newSize) / oldSize;
  args.jobLog("New file is " + pct.toFixed(1) + "% of the old size (" + (oldSize / 1e9).toFixed(2) + " GB -> "
    + (newSize / 1e9).toFixed(2) + " GB)");
  var maxP = Number(inputs.maxPercent) > 0 ? Number(inputs.maxPercent) : 90;
  var minP = Number(inputs.minPercent) > 0 ? Number(inputs.minPercent) : 10;
  if (pct > maxP) return bad("it only got " + (100 - pct).toFixed(0) + "% smaller");
  if (pct < minP) return bad("it's suspiciously small");
  var a; var b;
  try { a = probe(args, oldFile); b = probe(args, newFile); } catch (e) { return bad(e.message); }
  var da = Number(a.format && a.format.duration) || 0;
  var db = Number(b.format && b.format.duration) || 0;
  if (!(db > 0)) return bad("the new file has no length");
  if (da > 0 && Math.abs(da - db) > Math.max(3, da * 0.01)) {
    return bad("the length changed (" + da.toFixed(0) + "s -> " + db.toFixed(0) + "s)");
  }
  if (count(b.streams, "video") !== 1) return bad("the new file's video is missing");
  if (count(a.streams, "audio") > 0 && count(b.streams, "audio") === 0) return bad("the new file has no sound");
  var hevc = b.streams.filter(function (s) { return s.codec_type === "video" && s.codec_name === "hevc"; }).length;
  if (!hevc) return bad("the new video isn't HEVC");
  args.jobLog("New file checks out");
  args.variables.user = args.variables.user || {};
  args.variables.user.benflixOldSize = String(oldSize);
  args.variables.user.benflixNewSize = String(newSize);
  return { outputFileObj: args.inputFileObj, outputNumber: 1, variables: args.variables };
};
exports.plugin = plugin;
