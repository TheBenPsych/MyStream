"use strict";
// Benflix: decides if a file is worth shrinking. Part of server/tdarr (installed by setup-tdarr.sh).
// Output 1 = shrink it. Output 2 = leave it alone (already small, already HEVC/AV1, or an odd file type).
Object.defineProperty(exports, "__esModule", { value: true });
exports.plugin = exports.details = void 0;
var fs = require("fs");

var details = function () { return ({
  name: "Benflix: worth shrinking?",
  description: "Shrink only big files that aren't HEVC or AV1 yet. Episodes and movies have their own size limits.",
  style: { borderColor: "#e50914" },
  tags: "video",
  isStartPlugin: false,
  pType: "",
  requiresVersion: "2.11.01",
  sidebarPosition: -1,
  icon: "faQuestion",
  inputs: [
    { label: "Episodes bigger than (GB)", name: "episodeMinGB", type: "number", defaultValue: "1",
      inputUI: { type: "text" }, tooltip: "Episodes smaller than this are left alone" },
    { label: "Movies bigger than (GB)", name: "movieMinGB", type: "number", defaultValue: "2",
      inputUI: { type: "text" }, tooltip: "Movies smaller than this are left alone. A file is a movie when a folder in its path has 'movie' in the name." },
  ],
  outputs: [
    { number: 1, tooltip: "Shrink it" },
    { number: 2, tooltip: "Leave it alone" },
  ],
}); };
exports.details = details;

var num = function (v, d) { var n = Number(v); return isFinite(n) && n > 0 ? n : d; };

var plugin = function (args) {
  var inputs = args.inputs || {};
  var file = args.inputFileObj._id;
  var leave = function (why) {
    args.jobLog("Leaving it alone: " + why);
    return { outputFileObj: args.inputFileObj, outputNumber: 2, variables: args.variables };
  };
  var ext = (file.split(".").pop() || "").toLowerCase();
  if (["mkv", "mp4", "m4v"].indexOf(ext) === -1) return leave("." + ext + " files are skipped (only mkv and mp4)");
  var streams = (args.inputFileObj.ffProbeData && args.inputFileObj.ffProbeData.streams) || [];
  var video = streams.filter(function (s) {
    return s.codec_type === "video" && !(s.disposition && Number(s.disposition.attached_pic) === 1)
      && ["mjpeg", "png", "bmp", "gif"].indexOf(s.codec_name) === -1;
  });
  if (video.length !== 1) return leave("it has " + video.length + " video tracks (expected 1)");
  var codec = String(video[0].codec_name || "").toLowerCase();
  if (["hevc", "av1", "vp9"].indexOf(codec) !== -1) return leave("it's already " + codec.toUpperCase());
  var bytes = 0;
  try { bytes = fs.statSync(file).size; } catch (e) { bytes = Number(args.inputFileObj.file_size || 0) * 1e6; }
  var isMovie = /movie/i.test(file.split("/").slice(0, -1).join("/"));
  var limitGB = isMovie ? num(inputs.movieMinGB, 2) : num(inputs.episodeMinGB, 1);
  var sizeGB = bytes / 1e9;
  if (sizeGB <= limitGB) {
    return leave((isMovie ? "movie" : "episode") + " is " + sizeGB.toFixed(2) + " GB, under the " + limitGB + " GB limit");
  }
  args.jobLog("Shrinking: " + (isMovie ? "movie" : "episode") + " is " + sizeGB.toFixed(2) + " GB " + codec.toUpperCase()
    + " (limit " + limitGB + " GB)");
  return { outputFileObj: args.inputFileObj, outputNumber: 1, variables: args.variables };
};
exports.plugin = plugin;
