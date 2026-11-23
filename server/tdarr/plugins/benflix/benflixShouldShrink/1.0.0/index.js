"use strict";
// Benflix: decides if a file is worth shrinking. Part of server/tdarr (installed by setup-tdarr.sh).
// Output 1 = shrink it. Output 2 = leave it alone (already in the size goal or smaller, shrunk before, AV1/VP9,
// HDR, 4K, odd file type, disk too full). Size goals: Ben 2026-11-22 (episodes 400-800 MB, movies 0.8-1.5 GB).
Object.defineProperty(exports, "__esModule", { value: true });
exports.plugin = exports.details = void 0;
var fs = require("fs");

var details = function () { return ({
  name: "Benflix: worth shrinking?",
  description: "Shrink only files bigger than the size goal. Episodes and movies have their own goals.",
  style: { borderColor: "#e50914" },
  tags: "video",
  isStartPlugin: false,
  pType: "",
  requiresVersion: "2.11.01",
  sidebarPosition: -1,
  icon: "faQuestion",
  inputs: [
    { label: "Top of the episode goal (MB)", name: "episodeMaxMB", type: "number", defaultValue: "800",
      inputUI: { type: "text" }, tooltip: "Episodes this size or smaller are left alone" },
    { label: "Top of the movie goal (MB)", name: "movieMaxMB", type: "number", defaultValue: "1500",
      inputUI: { type: "text" }, tooltip: "Movies this size or smaller are left alone. A file is a movie when a folder in its path has 'movie' in the name." },
    { label: "HEVC files lose at most (%)", name: "hevcMaxCut", type: "number", defaultValue: "50",
      inputUI: { type: "text" }, tooltip: "0 = never shrink files that are already HEVC" },
    { label: "Keep this much disk free (GB)", name: "minFreeGB", type: "number", defaultValue: "300",
      inputUI: { type: "text" }, tooltip: "Backups of originals need room. Below this, files are left for another night." },
    { label: "Backup folder", name: "backupFolder", type: "string", defaultValue: "/backup", inputUI: { type: "text" }, tooltip: "" },
    { label: "Files to keep as they are", name: "keepList", type: "string", defaultValue: "/backup/benflix-keep.txt",
      inputUI: { type: "text" }, tooltip: "One path per line. Files Ben put back from Harbor's Shrinker page are never shrunk again." },
    { label: "Shrink log", name: "shrinkLog", type: "string", defaultValue: "/backup/shrink-log.txt",
      inputUI: { type: "text" }, tooltip: "Files in the log were shrunk before and are never shrunk twice" },
  ],
  outputs: [
    { number: 1, tooltip: "Shrink it" },
    { number: 2, tooltip: "Leave it alone" },
  ],
}); };
exports.details = details;

var num = function (v, d) { var n = Number(v); return isFinite(n) && n > 0 ? n : d; };

// Was this file shrunk before? (Ben 2026-11-22: "leave them as they are", shrinking twice loses the most)
var shrunkBefore = function (args, file, logPath) {
  var tags = (args.inputFileObj.ffProbeData && args.inputFileObj.ffProbeData.format && args.inputFileObj.ffProbeData.format.tags) || {};
  if (Object.keys(tags).some(function (k) { return /^benflix$/i.test(k) && /shrunk/i.test(String(tags[k])); })) return true;
  try {
    var log = fs.readFileSync(String(logPath || "/backup/shrink-log.txt"), "utf8");
    return log.indexOf("  " + file + "\n") !== -1 || log.slice(-file.length - 2) === "  " + file;
  } catch (e) { return false; }
};

var plugin = function (args) {
  var inputs = args.inputs || {};
  var file = args.inputFileObj._id;
  var leave = function (why) {
    args.jobLog("Leaving it alone: " + why);
    return { outputFileObj: args.inputFileObj, outputNumber: 2, variables: args.variables };
  };
  // Put back from Harbor's Shrinker page: never shrink that file again (2026-11-22)
  try {
    var keep = fs.readFileSync(String(inputs.keepList || "/backup/benflix-keep.txt"), "utf8").split("\n");
    if (keep.indexOf(file) !== -1) return leave("you put the original back, so it stays as it is");
  } catch (e) { /* no list yet */ }
  var ext = (file.split(".").pop() || "").toLowerCase();
  if (["mkv", "mp4", "m4v"].indexOf(ext) === -1) return leave("." + ext + " files are skipped (only mkv and mp4)");
  var streams = (args.inputFileObj.ffProbeData && args.inputFileObj.ffProbeData.streams) || [];
  var video = streams.filter(function (s) {
    return s.codec_type === "video" && !(s.disposition && Number(s.disposition.attached_pic) === 1)
      && ["mjpeg", "png", "bmp", "gif"].indexOf(s.codec_name) === -1;
  });
  if (video.length !== 1) return leave("it has " + video.length + " video tracks (expected 1)");
  var codec = String(video[0].codec_name || "").toLowerCase();
  if (["av1", "vp9"].indexOf(codec) !== -1) return leave("it's already " + codec.toUpperCase());
  var hevcCut = Number(inputs.hevcMaxCut);
  if (!isFinite(hevcCut)) hevcCut = 50;
  if (codec === "hevc" && hevcCut <= 0) return leave("it's already HEVC");
  if (shrunkBefore(args, file, inputs.shrinkLog)) return leave("it was shrunk before (never twice)");
  // HDR and Dolby Vision pictures could lose their colours, so they're left alone (2026-11-21, all folders)
  var v0 = video[0];
  var transfer = String(v0.color_transfer || "").toLowerCase();
  if (transfer === "smpte2084" || transfer === "arib-std-b67") return leave("it's HDR");
  if ((v0.side_data_list || []).some(function (d) { return /dovi|dolby/i.test(String(d.side_data_type || "")); })) {
    return leave("it's Dolby Vision");
  }
  var h = Number(v0.height) || 0, w = Number(v0.width) || 0;
  if (h > 1200 || w > 2600) return leave("it's 4K");
  var bytes = 0;
  try { bytes = fs.statSync(file).size; } catch (e) { bytes = Number(args.inputFileObj.file_size || 0) * 1e6; }
  var isMovie = /movie/i.test(file.split("/").slice(0, -1).join("/"));
  var limitMB = isMovie ? num(inputs.movieMaxMB, 1500) : num(inputs.episodeMaxMB, 800);
  var sizeMB = bytes / 1e6;
  var sizeGB = bytes / 1e9;
  if (sizeMB <= limitMB) {
    return leave((isMovie ? "movie" : "episode") + " is " + Math.round(sizeMB) + " MB, already in the goal or smaller (top " + limitMB + " MB)");
  }
  // An HEVC file may lose only part of its size: not worth it when that can't get it meaningfully smaller
  if (codec === "hevc" && hevcCut < 15) return leave("it's HEVC and may only lose " + hevcCut + "%");
  // Originals are kept for a few days, so there must be room for them
  try {
    var st = fs.statfsSync(String(inputs.backupFolder || "/backup"));
    var freeGB = st.bavail * st.bsize / 1e9;
    if (freeGB < num(inputs.minFreeGB, 300)) return leave("only " + Math.round(freeGB) + " GB free for backups, trying another night");
  } catch (e) { /* older Node: no free-space check */ }
  args.jobLog("Shrinking: " + (isMovie ? "movie" : "episode") + " is " + sizeGB.toFixed(2) + " GB " + codec.toUpperCase()
    + " (goal tops out at " + limitMB + " MB)");
  return { outputFileObj: args.inputFileObj, outputNumber: 1, variables: args.variables };
};
exports.plugin = plugin;
