"use strict";
// Benflix: sets up the shrink for one file. Part of server/tdarr (installed by setup-tdarr.sh).
// Goes right after "FFmpeg Command Start". It
//  - keeps English plus the title's original language for sound (asks Radarr/Sonarr which language that is;
//    anime falls back to Japanese), and never removes the last sound track,
//  - keeps English subtitles (and untagged ones), removes the rest,
//  - drops cover pictures and data tracks, keeps fonts and chapters,
//  - re-encodes the video to HEVC on the Intel Arc card with Quick Sync (10-bit stays 10-bit), sized to land
//    inside Ben's size goal (2026-11-22: episodes 400-800 MB, movies 0.8-1.5 GB, aiming near the top), with
//    safety floors that always win: never under the bottom of the goal, never under about 12 MB a minute for
//    1080p, and an HEVC file loses at most half. See sizePlan below.
Object.defineProperty(exports, "__esModule", { value: true });
exports.plugin = exports.details = void 0;
var fs = require("fs");
var http = require("http");
var https = require("https");

var details = function () { return ({
  name: "Benflix: prepare the shrink",
  description: "Keep English + original language, English subtitles, and encode the video to HEVC on the GPU.",
  style: { borderColor: "#e50914" },
  tags: "video,audio,subtitle",
  isStartPlugin: false,
  pType: "",
  requiresVersion: "2.11.01",
  sidebarPosition: -1,
  icon: "faArrowRight",
  inputs: [
    { label: "Episode goal from (MB)", name: "episodeMinMB", type: "number", defaultValue: "400", inputUI: { type: "text" }, tooltip: "" },
    { label: "Episode goal to (MB)", name: "episodeMaxMB", type: "number", defaultValue: "800", inputUI: { type: "text" }, tooltip: "" },
    { label: "Movie goal from (MB)", name: "movieMinMB", type: "number", defaultValue: "800", inputUI: { type: "text" }, tooltip: "" },
    { label: "Movie goal to (MB)", name: "movieMaxMB", type: "number", defaultValue: "1500", inputUI: { type: "text" }, tooltip: "" },
    { label: "Aim", name: "aim", type: "string", defaultValue: "top",
      inputUI: { type: "dropdown", options: ["top", "middle", "bottom"] }, tooltip: "Where in the goal to aim. Top = best picture." },
    { label: "Picture floor (MB a minute, 1080p)", name: "floorMBMin", type: "number", defaultValue: "12",
      inputUI: { type: "text" }, tooltip: "Never smaller than this per minute. 720p gets 60%, SD 40%." },
    { label: "HEVC files lose at most (%)", name: "hevcMaxCut", type: "number", defaultValue: "50", inputUI: { type: "text" }, tooltip: "" },
    { label: "Encoder", name: "encoder", type: "string", defaultValue: "qsv",
      inputUI: { type: "dropdown", options: ["qsv", "cpu"] }, tooltip: "qsv = Intel GPU (normal). cpu = libx265 (slow, testing only)." },
    { label: "GPU device", name: "device", type: "string", defaultValue: "/dev/dri/renderD128",
      inputUI: { type: "text" }, tooltip: "The GPU inside the Tdarr container" },
    { label: "Remove other languages", name: "trimLanguages", type: "boolean", defaultValue: "true",
      inputUI: { type: "switch" }, tooltip: "Keep English + original language sound and English subtitles" },
    { label: "Radarr address", name: "radarrUrl", type: "string", defaultValue: "", inputUI: { type: "text" }, tooltip: "" },
    { label: "Radarr key", name: "radarrKey", type: "string", defaultValue: "", inputUI: { type: "text" }, tooltip: "" },
    { label: "Sonarr address", name: "sonarrUrl", type: "string", defaultValue: "", inputUI: { type: "text" }, tooltip: "" },
    { label: "Sonarr key", name: "sonarrKey", type: "string", defaultValue: "", inputUI: { type: "text" }, tooltip: "" },
    { label: "Media folder in Tdarr", name: "mediaRoot", type: "string", defaultValue: "/media", inputUI: { type: "text" }, tooltip: "" },
    { label: "Same folder in Radarr/Sonarr", name: "arrRoot", type: "string", defaultValue: "/data", inputUI: { type: "text" }, tooltip: "" },
  ],
  outputs: [{ number: 1, tooltip: "Continue" }],
}); };
exports.details = details;

// Radarr/Sonarr language name -> the tags files use for it
var LANGS = {
  english: ["eng", "en"], japanese: ["jpn", "ja", "jp", "jap"], french: ["fre", "fra", "fr"],
  spanish: ["spa", "es"], german: ["ger", "deu", "de"], italian: ["ita", "it"], korean: ["kor", "ko"],
  chinese: ["chi", "zho", "zh", "cmn", "yue"], mandarin: ["chi", "zho", "zh", "cmn"], cantonese: ["chi", "zho", "zh", "yue"],
  portuguese: ["por", "pt"], russian: ["rus", "ru"], hindi: ["hin", "hi"], swedish: ["swe", "sv"],
  danish: ["dan", "da"], norwegian: ["nor", "nob", "nno", "no", "nb"], dutch: ["dut", "nld", "nl"],
  flemish: ["dut", "nld", "nl"], polish: ["pol", "pl"], thai: ["tha", "th"], turkish: ["tur", "tr"],
  tagalog: ["tgl", "fil", "tl"], filipino: ["fil", "tgl", "tl"], indonesian: ["ind", "id"],
  arabic: ["ara", "ar"], hebrew: ["heb", "he"], finnish: ["fin", "fi"], czech: ["cze", "ces", "cs"],
  hungarian: ["hun", "hu"], greek: ["gre", "ell", "el"], icelandic: ["ice", "isl", "is"],
  tamil: ["tam", "ta"], telugu: ["tel", "te"], malayalam: ["mal", "ml"], bengali: ["ben", "bn"],
  vietnamese: ["vie", "vi"], persian: ["per", "fas", "fa"], ukrainian: ["ukr", "uk"], romanian: ["rum", "ron", "ro"],
};
var UNTAGGED = ["", "und", "unk", "unknown", "mis", "zxx", "mul"];

var num = function (v, d) { var n = Number(v); return isFinite(n) && n > 0 ? n : d; };

var langOf = function (s) { return String((s.tags && (s.tags.language || s.tags.LANGUAGE)) || "").toLowerCase().trim(); };

var getJson = function (url, key) {
  return new Promise(function (resolve, reject) {
    var lib = url.indexOf("https:") === 0 ? https : http;
    var req = lib.get(url, { headers: { "X-Api-Key": key }, timeout: 30000 }, function (res) {
      var chunks = [];
      res.on("data", function (c) { chunks.push(c); });
      res.on("end", function () {
        if (res.statusCode !== 200) return reject(new Error("HTTP " + res.statusCode));
        try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8"))); } catch (e) { reject(e); }
      });
    });
    req.on("timeout", function () { req.destroy(new Error("timed out")); });
    req.on("error", reject);
  });
};

// The whole Radarr movie list / Sonarr show list, kept for 30 minutes so a night of episodes asks once
var arrList = async function (args, kind, url, key) {
  var cache = "/temp/benflix-" + kind + "-list.json";
  try {
    var st = fs.statSync(cache);
    if (Date.now() - st.mtimeMs < 30 * 60 * 1000) return JSON.parse(fs.readFileSync(cache, "utf8"));
  } catch (e) { /* no cache yet */ }
  var path = kind === "radarr" ? "/api/v3/movie" : "/api/v3/series";
  var all = await getJson(url.replace(/\/+$/, "") + path, key);
  var small = all.map(function (x) { return { path: x.path, lang: x.originalLanguage && x.originalLanguage.name }; });
  try { fs.writeFileSync(cache, JSON.stringify(small)); } catch (e) { /* fine */ }
  return small;
};

var originalLanguage = async function (args, inputs) {
  var file = args.originalLibraryFile ? args.originalLibraryFile._id : args.inputFileObj._id;
  var isMovie = /movie/i.test(file.split("/").slice(0, -1).join("/"));
  var kind = isMovie ? "radarr" : "sonarr";
  var url = String(inputs[kind + "Url"] || "");
  var key = String(inputs[kind + "Key"] || "");
  var mediaRoot = String(inputs.mediaRoot || "/media").replace(/\/+$/, "");
  var arrRoot = String(inputs.arrRoot || "/data").replace(/\/+$/, "");
  if (url && key) {
    try {
      var arrPath = file.indexOf(mediaRoot + "/") === 0 ? arrRoot + file.slice(mediaRoot.length) : file;
      var list = await arrList(args, kind, url, key);
      var hit = list.filter(function (x) { return x.path && arrPath.indexOf(x.path.replace(/\/+$/, "") + "/") === 0; })
        .sort(function (a, b) { return b.path.length - a.path.length; })[0];
      if (hit && hit.lang) {
        args.jobLog("Original language from " + kind + ": " + hit.lang);
        return String(hit.lang).toLowerCase();
      }
      args.jobLog("Couldn't find this title in " + kind);
    } catch (e) {
      args.jobLog("Couldn't ask " + kind + " for the original language: " + e.message);
    }
  }
  if (/anime/i.test(file)) {
    args.jobLog("Original language: Japanese (it's in an anime folder)");
    return "japanese";
  }
  return "";
};

// The old video's bitrate in kbps: the track's own number, else the MKV tag, else the whole file minus sound
var videoKbps = function (args, v, streams) {
  var tagBps = function (s) {
    var t = s.tags || {};
    var keys = Object.keys(t).filter(function (k) { return /^BPS(-eng)?$/i.test(k); });
    return keys.length ? Number(t[keys[0]]) : 0;
  };
  var bps = Number(v.bit_rate) || tagBps(v);
  if (bps > 0) return bps / 1000;
  var probe = (args.inputFileObj && args.inputFileObj.ffProbeData) || {};
  var fmt = probe.format || {};
  var dur = Number(fmt.duration) || 0;
  var total = Number(fmt.bit_rate) || 0;
  if (!total && dur > 0) {
    try { total = fs.statSync(args.inputFileObj._id).size * 8 / dur; } catch (e) { total = 0; }
  }
  if (!total) return 0;
  var other = 0;
  streams.forEach(function (s) {
    if (s.codec_type === "audio") other += Number(s.bit_rate) || tagBps(s) || 192000;
  });
  return Math.max(total - other, total * 0.5) / 1000;
};

var AIMS = { top: 0.875, middle: 0.5, bottom: 0.125 };

// Works out the size the new file should be, in MB, and why. Pure, so it can be tested on its own.
//   o: { isMovie, minutes, height, width, sizeMB, codec, srcVideoKbps, audioKbps, goal: {epMin, epMax, mvMin, mvMax},
//        aim, floorMBMin, hevcMaxCut }
// Returns { targetMB, videoKbps, why: [...] }
var sizePlan = function (o) {
  var why = [];
  var min = o.isMovie ? o.goal.mvMin : o.goal.epMin;
  var max = o.isMovie ? o.goal.mvMax : o.goal.epMax;
  var f = AIMS[o.aim] !== undefined ? AIMS[o.aim] : AIMS.top;
  var target = min + (max - min) * f;
  why.push("aim " + Math.round(target) + " MB (goal " + min + "-" + max + " MB)");
  // Safety 1: never under the bottom of the goal
  // Safety 2: the picture floor per minute (by size of the picture)
  var h = o.height || 1080, w = o.width || 1920;
  var scale = (h > 800 || w > 1400) ? 1 : (h > 500 || w > 1000) ? 0.6 : 0.4;
  var floor = Math.max(min, (o.floorMBMin || 12) * scale * (o.minutes || 0));
  if (floor > target) { target = floor; why.push("raised to " + Math.round(floor) + " MB by the picture floor (" + Math.round(o.minutes) + " min)"); }
  // Safety 3: an HEVC file loses at most hevcMaxCut %
  if (o.codec === "hevc") {
    var least = o.sizeMB * (1 - (o.hevcMaxCut || 50) / 100);
    if (least > target) { target = least; why.push("raised to " + Math.round(least) + " MB: HEVC loses at most " + (o.hevcMaxCut || 50) + "%"); }
  }
  // Must be a real saving
  if (target > o.sizeMB * 0.85) { target = o.sizeMB * 0.85; why.push("capped at 85% of the old size"); }
  var secs = (o.minutes || 0) * 60;
  var totalKbps = secs > 0 ? target * 8000 / secs : 0;
  // Sound and subtitles are copied as they are; the video gets the rest (1% for the container)
  var videoKbps = Math.round(totalKbps * 0.99 - (o.audioKbps || 0));
  // The picture itself never gets less than 85% of the floor, however much room the sound takes
  var lowest = Math.round((o.floorMBMin || 12) * scale * 8000 / 60 * 0.85);
  if (videoKbps < lowest) {
    videoKbps = lowest;
    target = (lowest + (o.audioKbps || 0)) / 0.99 * secs / 8000;
    why.push("raised to " + Math.round(target) + " MB so the picture keeps " + lowest + " kbps");
  }
  if (o.srcVideoKbps > 0 && videoKbps > o.srcVideoKbps * 0.85) {
    videoKbps = Math.round(o.srcVideoKbps * 0.85);
    target = Math.max(min, (videoKbps + (o.audioKbps || 0)) / 0.99 * secs / 8000);
  }
  return { targetMB: Math.round(target), videoKbps: videoKbps, why: why };
};
exports.sizePlan = sizePlan;

// A movie is a file in a folder named "movie", or a long special: a Season 00 / Specials file of 40 minutes
// or more, like the Pokemon movies filed with the show (Ben 2026-11-22). Same 40 minutes the app uses.
var isMovieFile = function (file, secs) {
  var dir = file.split("/").slice(0, -1).join("/");
  if (/movie/i.test(dir)) return true;
  var special = /\/(season 0+|specials?)(\/|$)/i.test(dir) || /\bS00E\d+/i.test(file.split("/").pop());
  return special && Number(secs) >= 40 * 60;
};

// A surround track worth making smaller: 6+ channels and over 384 kbps (lossless ones often have no bitrate).
var SURROUND_KBPS = 384;
var bigSurround = function (s, tagged) {
  if (!(Number(s.channels) >= 6)) return false;
  var codec = String(s.codec_name || "").toLowerCase();
  if (["truehd", "mlp", "flac", "alac"].indexOf(codec) !== -1 || /^pcm_/.test(codec)) return true;
  var bps = Number(s.bit_rate) || Number(tagged) || 0;
  if (!bps) return codec === "dts";
  return bps > SURROUND_KBPS * 1000 * 1.05;
};

var plugin = async function (args) {
  var inputs = args.inputs || {};
  var cmd = args.variables && args.variables.ffmpegCommand;
  if (!cmd || !cmd.init) throw new Error("Put 'FFmpeg Command Start' before this step");
  var streams = cmd.streams;

  // Covers, data tracks and extra pictures go; fonts and chapters stay
  streams.forEach(function (s) {
    if (s.codec_type === "data") { s.removed = true; args.jobLog("Removing data track " + s.index); }
    if (s.codec_type === "attachment" && s.disposition && Number(s.disposition.attached_pic) === 1) {
      s.removed = true; args.jobLog("Removing cover picture " + s.index);
    }
  });

  if (String(inputs.trimLanguages) !== "false") {
    var orig = await originalLanguage(args, inputs);
    var keepAudio = ["eng", "en", "english"].concat(UNTAGGED);
    var knownOrig = true;
    if (orig && orig !== "english") {
      if (LANGS[orig]) keepAudio = keepAudio.concat(LANGS[orig], [orig]);
      else knownOrig = false;
    } else if (!orig) {
      knownOrig = false;
    }
    var audio = streams.filter(function (s) { return s.codec_type === "audio"; });
    if (!knownOrig) {
      args.jobLog("Original language unknown, keeping every sound track");
    } else {
      var kept = audio.filter(function (s) { return keepAudio.indexOf(langOf(s)) !== -1; });
      if (kept.length === 0) {
        args.jobLog("No English or original-language sound track found, keeping every sound track");
      } else {
        audio.forEach(function (s) {
          if (kept.indexOf(s) === -1) { s.removed = true; args.jobLog("Removing sound track " + s.index + " (" + langOf(s) + ")"); }
        });
      }
    }
    var keepSubs = ["eng", "en", "english"].concat(UNTAGGED);
    streams.filter(function (s) { return s.codec_type === "subtitle"; }).forEach(function (s) {
      if (keepSubs.indexOf(langOf(s)) === -1) { s.removed = true; args.jobLog("Removing subtitles " + s.index + " (" + langOf(s) + ")"); }
    });
  }

  // The video: aim for a bitrate (not just a quality level) so nothing ends up tiny.
  // Ben 2026-11-19: quality 24 made 1.4 GB episodes ~0.15 GB; he wants ~400 MB+ per episode.
  var device = String(inputs.device || "/dev/dri/renderD128");
  var useCpu = String(inputs.encoder) === "cpu";
  var video = streams.filter(function (s) { return s.codec_type === "video" && !s.removed; });
  if (video.length !== 1) throw new Error("Expected one video track, found " + video.length);
  var v = video[0];
  var tenBit = /10|12/.test(String(v.pix_fmt || "")) || Number(v.bits_per_raw_sample) >= 10;
  var srcKbps = videoKbps(args, v, streams);
  var probe = (args.inputFileObj && args.inputFileObj.ffProbeData) || {};
  var secs = Number((probe.format || {}).duration) || Number(v.duration) || 0;
  if (!(secs > 0)) throw new Error("Couldn't tell how long the video is");
  var tagBps = function (s) {
    var t = s.tags || {};
    var k = Object.keys(t).filter(function (x) { return /^BPS(-eng)?$/i.test(x); })[0];
    return k ? Number(t[k]) : 0;
  };
  // Movies: big surround tracks become 5.1 EAC3 at 384 kbps, the size streaming services use, so a long
  // movie with two 5.1 tracks still fits its goal (Ben 2026-11-23). Stereo and small tracks are copied.
  var file0 = args.originalLibraryFile ? args.originalLibraryFile._id : args.inputFileObj._id;
  if (isMovieFile(file0, secs)) {
    streams.forEach(function (s) {
      if (s.removed || s.codec_type !== "audio" || !bigSurround(s, tagBps(s))) return;
      s.outputArgs.push("-c:{outputIndex}", "eac3", "-b:{outputIndex}", SURROUND_KBPS + "k", "-ac:{outputIndex}", "6");
      s.bit_rate = SURROUND_KBPS * 1000;
      args.jobLog("Sound track " + s.index + " (" + String(s.codec_name).toUpperCase() + " " + s.channels
        + " channels) -> 5.1 EAC3 at " + SURROUND_KBPS + " kbps");
    });
  }
  var audioKbps = 0;
  streams.forEach(function (s) {
    if (s.removed) return;
    if (s.codec_type === "audio") audioKbps += (Number(s.bit_rate) || tagBps(s) || 384000) / 1000;
    if (s.codec_type === "subtitle") audioKbps += (Number(s.bit_rate) || tagBps(s) || 2000) / 1000;
  });
  var file = args.originalLibraryFile ? args.originalLibraryFile._id : args.inputFileObj._id;
  var sizeMB = 0;
  try { sizeMB = fs.statSync(file).size / 1e6; } catch (e) { sizeMB = Number(args.inputFileObj.file_size || 0); }
  var plan = sizePlan({
    isMovie: isMovieFile(file, secs),
    minutes: secs / 60, height: Number(v.height) || 1080, width: Number(v.width) || 1920, sizeMB: sizeMB,
    codec: String(v.codec_name || "").toLowerCase(), srcVideoKbps: srcKbps, audioKbps: audioKbps,
    goal: { epMin: num(inputs.episodeMinMB, 400), epMax: num(inputs.episodeMaxMB, 800), mvMin: num(inputs.movieMinMB, 800), mvMax: num(inputs.movieMaxMB, 1500) },
    aim: String(inputs.aim || "top"), floorMBMin: num(inputs.floorMBMin, 12), hevcMaxCut: num(inputs.hevcMaxCut, 50),
  });
  var target = plan.videoKbps;
  args.jobLog("Size plan: " + Math.round(sizeMB) + " MB -> about " + plan.targetMB + " MB: " + plan.why.join("; "));
  args.variables.user = args.variables.user || {};
  args.variables.user.benflixPlanMB = String(plan.targetMB);
  args.variables.user.benflixMovie = isMovieFile(file, secs) ? "1" : "";
  var maxrate = Math.round(target * 1.5) + "k", bufsize = Math.round(target * 3) + "k";
  if (useCpu) {
    v.outputArgs.push("-c:{outputIndex}", "libx265", "-b:{outputIndex}", target + "k", "-maxrate:{outputIndex}", maxrate,
      "-bufsize:{outputIndex}", bufsize, "-preset", "fast", "-pix_fmt:{outputIndex}", tenBit ? "yuv420p10le" : "yuv420p");
  } else {
    cmd.overallInputArguments.push("-init_hw_device", "vaapi=va:" + device, "-init_hw_device", "qsv=qs@va",
      "-filter_hw_device", "qs");
    v.outputArgs.push("-filter:{outputIndex}", (tenBit ? "format=p010le" : "format=nv12") + ",hwupload=extra_hw_frames=64",
      "-c:{outputIndex}", "hevc_qsv", "-b:{outputIndex}", target + "k", "-maxrate:{outputIndex}", maxrate,
      "-bufsize:{outputIndex}", bufsize, "-preset:{outputIndex}", "slow",
      "-profile:{outputIndex}", tenBit ? "main10" : "main");
  }
  if (cmd.container === "mp4" || cmd.container === "m4v") v.outputArgs.push("-tag:{outputIndex}", "hvc1");
  args.jobLog("Video: " + String(v.codec_name).toUpperCase() + (tenBit ? " 10-bit" : "") + " -> HEVC on "
    + (useCpu ? "the CPU" : "the GPU") + ", " + Math.round(srcKbps) + " kbps -> " + target + " kbps");
  // Marks the file as shrunk, so it is never shrunk twice
  cmd.overallOuputArguments.push("-metadata", "BENFLIX=shrunk");
  cmd.overallOuputArguments.push("-max_muxing_queue_size", "9999");
  cmd.shouldProcess = true;
  return { outputFileObj: args.inputFileObj, outputNumber: 1, variables: args.variables };
};
exports.plugin = plugin;
exports.isMovieFile = isMovieFile;
exports.bigSurround = bigSurround;
