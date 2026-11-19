/* Benflix web: the player. Glowfin P1 bar with chapter marks, white Skip pill, Up next card,
   audio + subtitles panel, ambient pause. Plays the file as it is when the browser can, otherwise
   Jellyfin converts it on the fly (HLS, played with hls.js). */
"use strict";

const canPlay = (type) => { try { return window.MediaSource && MediaSource.isTypeSupported(type); } catch (e) { return false; } };
B.deviceProfile = (maxBitrate) => {
  const vcodecs = ["h264"];
  if (canPlay('video/mp4; codecs="hvc1.1.6.L120.90"')) vcodecs.push("hevc");
  if (canPlay('video/mp4; codecs="av01.0.05M.08"')) vcodecs.push("av1");
  if (canPlay('video/webm; codecs="vp9"')) vcodecs.push("vp9");
  return {
    MaxStreamingBitrate: maxBitrate, MaxStaticBitrate: maxBitrate, MusicStreamingTranscodingBitrate: 384000,
    DirectPlayProfiles: [
      { Container: "mp4,m4v", Type: "Video", VideoCodec: vcodecs.join(","), AudioCodec: "aac,mp3,opus,flac,alac" },
      { Container: "webm", Type: "Video", VideoCodec: "vp8,vp9,av1", AudioCodec: "vorbis,opus" },
      { Container: "mp3,aac,m4a,flac,webm,ogg,opus", Type: "Audio" },
    ],
    TranscodingProfiles: [
      { Container: "ts", Type: "Video", AudioCodec: "aac,mp3", VideoCodec: "h264", Context: "Streaming", Protocol: "hls", MaxAudioChannels: "2", MinSegments: "1", BreakOnNonKeyFrames: true },
      { Container: "aac", Type: "Audio", AudioCodec: "aac", Context: "Streaming", Protocol: "http", MaxAudioChannels: "2" },
    ],
    ContainerProfiles: [],
    CodecProfiles: [{ Type: "Video", Codec: "h264", Conditions: [{ Condition: "LessThanEqual", Property: "VideoLevel", Value: "52", IsRequired: false }, { Condition: "LessThanEqual", Property: "VideoBitDepth", Value: "8", IsRequired: false }] }],
    SubtitleProfiles: ["vtt", "srt", "subrip", "ass", "ssa"].map((f) => ({ Format: f, Method: "External" })),
  };
};
const QUALITY = { auto: 120000000, "1080": 20000000, "720": 8000000, "480": 3000000 };
// "Skip came at the wrong time?" choices in the player panel (Tower's benflix_skips.py FIX_KINDS)
const FIXES = [
  ["intro_start", "Intro starts here"], ["intro_end", "Intro ends here"],
  ["credits_start", "Credits start here"], ["credits_end", "Credits end here", "if a scene comes after"],
  ["no_intro", "No intro in this episode"], ["no_intro_show", "No intro in this whole show"],
  ["undo", "Undo my fixes for this episode"],
];
const withKey = (url) => url + (url.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token);
const SPEEDS = [0.75, 1, 1.25, 1.5, 2];
const streamUrl = (url) => "api/web/stream" + (url.startsWith("/") ? url : "/" + url);

// Watch time: adds this browser's playing time to the person's total (like the TV and phone do)
const WT = {
  ms: 0, last: 0,
  key: () => "device-web" + B.browserId().slice(0, 5),
  local: () => loadJson("benflix.wt." + B.me.id, { since: Date.now(), months: {} }),
  add(ms) {
    const l = WT.local(), month = new Date().toISOString().slice(0, 7);
    l.months[month] = (l.months[month] || 0) + ms;
    store("benflix.wt." + B.me.id, JSON.stringify(l));
    if (Date.now() - WT.last > 300000) WT.send();
  },
  send() {
    WT.last = Date.now();
    const l = WT.local();
    const v = ["since=" + l.since, ...Object.entries(l.months).map(([k, n]) => `${k}=${Math.round(n)}`)].join(";");
    B.savePrefs((c) => { c[WT.key()] = v; }, "benflix-watch-time").catch(() => {});
  },
};

B.route(/^\/play\/([\w-]+)/, async (m, nav) => {
  // Starting a movie, show, channel or game stops the music
  if (B.music) B.music.stop();
  if (B.kept) B.dropKept(); // a video still playing in the pop-out window stops
  const u = B.uid();
  const it = await B.get(`/Users/${u}/Items/${m[1]}?Fields=${B.FIELDS},Chapters,MediaSources,People,Trickplay`);
  if (!B.alive(nav)) return;
  B.lastPlayed = { id: it.Id, title: it.Type === "Episode" ? `${it.SeriesName} S${it.ParentIndexNumber} E${it.IndexNumber}` : it.Name };
  const settings = B.webSettings();
  const ud = it.UserData || {};
  let startTicks = B.q("resume") === "1" && ud.PlaybackPositionTicks && !ud.Played ? ud.PlaybackPositionTicks : 0;
  // Smart resume (same as the TV and phone): coming back after a while starts a few seconds early, so you
  // catch the sentence you were in the middle of. 5s after a few minutes, 15s after a day, 30s after a week.
  let backedUp = 0;
  if (startTicks && ud.LastPlayedDate) {
    const away = Date.now() - Date.parse(ud.LastPlayedDate);
    backedUp = !(away >= 120000) ? 0 : away >= 7 * 864e5 ? 30 : away >= 864e5 ? 15 : 5;
    startTicks = Math.max(0, startTicks - backedUp * B.TICKS);
    if (backedUp) setTimeout(() => { if (B.alive(nav)) B.toast(`Started ${backedUp} seconds back, so you don't miss anything`); }, 1800);
  }
  if (B.q("t")) startTicks = +B.q("t");
  let audioIndex = B.q("audio") != null ? +B.q("audio") : null;
  let subIndex = B.q("sub") != null ? +B.q("sub") : null;
  const isEp = it.Type === "Episode";
  const chapters = (it.Chapters || []).filter((c) => c.StartPositionTicks != null);
  const dur = (it.RunTimeTicks || 0) / B.TICKS;
  const autoplayCount = +(sessionStorage.getItem("benflix.autoplays") || 0);

  B.page().innerHTML = `<div class="player ui" id="pl">
    <video id="v" playsinline></video>
    <div class="pause-art"><div class="t">${esc(isEp ? it.SeriesName : it.Name)}</div>${isEp ? `<div style="font-size:20px;font-weight:600;margin:4px 0 8px">S${it.ParentIndexNumber} E${it.IndexNumber} · ${esc(it.Name)}</div>` : ""}<div class="ov">${esc(it.Overview || "")}</div></div>
    <div class="shadeT"></div><div class="shadeB"></div>
    <div class="center-msg" id="msg"></div>
    <div class="topline"><button class="pbtn" id="pBack" title="Back">${I("left")}</button></div>
    <div class="pcorner"><button class="skip hidden" id="pSkip"></button><div class="upnext hidden" id="pNext"></div></div>
    <div class="eprow hidden" id="pEps"></div>
    <div class="controls p1 p4">
      <div class="tline"><span class="tt">${esc(isEp ? it.SeriesName || it.Name : it.Name)}</span><span class="st">${esc(isEp ? `S${it.ParentIndexNumber} E${it.IndexNumber} · "${it.Name}"` : [it.ProductionYear, it.OfficialRating].filter(Boolean).join(" · "))}</span><span class="ends" id="pEnds"></span></div>
      <div class="srow"><span id="pTime">0:00</span>
      <div class="bar" id="pBar"><div class="track"><u id="pBuf"></u><b id="pDone"></b></div><span id="pTicks"></span><span class="knob" id="pKnob"></span><span class="tpv hidden" id="pPrev"></span><span class="hovert hidden" id="pHover"></span></div>
      <span id="pLeft"></span></div>
      <div class="row2 p1row">
        <div class="p1l">
        <button class="pbtn" id="pGear" title="Settings: quality, speed and Fix a problem">${I("gear")}</button>
        ${isEp ? `<button class="pbtn" id="pList" title="Episodes (E)">${I("eps")}</button>` : ""}
        </div>
        <div class="p1c">
        ${isEp ? `<button class="pbtn" id="pPv" title="Previous episode (P)" disabled>${I("prev")}</button>` : ""}
        <button class="pbtn" id="pBk" title="Back 10 seconds (left arrow)">${I("back10")}</button>
        <button class="pbtn play" id="pPlay" title="Play / pause (space)">${I("pause")}</button>
        <button class="pbtn" id="pFw" title="Ahead 10 seconds (right arrow)">${I("fwd10")}</button>
        ${isEp ? `<button class="pbtn" id="pNx" title="Next episode (N)">${I("next")}</button>` : ""}
        </div>
        <div class="p1r">
        <button class="pbtn" id="pSub" title="Subtitles (C or S turns them on and off)">${I("sub")}</button>
        <button class="pbtn" id="pAud" title="Audio and volume (M mutes)">${I("audio")}</button>
        <span class="extra">
          <button class="pbtn" id="pKeys" title="Keyboard shortcuts (?)">?</button>
          ${document.pictureInPictureEnabled ? `<button class="pbtn" id="pPip" title="Pop out: keeps playing in a small window on top">${I("pip")}</button>` : ""}
          <button class="pbtn" id="pFull" title="Full screen (F)">${I("full")}</button>
        </span>
        </div>
      </div>
    </div>
  </div>`;
  const pl = $("#pl"), v = $("#v");
  // Skip and Up next sit just above the controls, however tall they are (they wrap on small windows)
  const ctl = $(".controls", pl);
  const placeCorner = () => { if (ctl.isConnected) pl.style.setProperty("--pctl", `${ctl.offsetHeight + 26 + 18}px`); };
  placeCorner();
  if (window.ResizeObserver) { const ro = new ResizeObserver(placeCorner); ro.observe(ctl); B.onLeave(() => ro.disconnect()); } else window.addEventListener("resize", placeCorner);
  // Still this player? (true while it plays on in the pop-out window and you browse other pages)
  let myNav = nav;
  const myHash = location.hash;
  const away = () => !!(B.kept && B.kept.el.contains(v));
  const here = () => B.alive(myNav) || away();
  v.volume = settings.volume != null ? settings.volume : 1;
  let hls = null, info = null, source = null, playSession = null, method = "DirectPlay", stopped = false;
  let lastTick = 0, uiTimer, pausedTimer, nextShown = false, skipped = new Set();

  const msg = (t) => { if (!$("#msg")) return; $("#msg").innerHTML = t ? `<div><div class="spin"></div><div>${esc(t)}</div></div>` : ""; };
  const report = (what, extra) => B.jf("POST", `/Sessions/Playing${what}`, Object.assign({
    ItemId: it.Id, MediaSourceId: source && source.Id, PlaySessionId: playSession, PositionTicks: Math.round(v.currentTime * B.TICKS),
    IsPaused: v.paused, IsMuted: v.muted, PlayMethod: method, CanSeek: true, AudioStreamIndex: audioIndex, SubtitleStreamIndex: subIndex,
  }, extra || {})).catch(() => {});

  // Ask Jellyfin how to play it, then start
  const load = async (atSeconds) => {
    msg("Starting…");
    if (hls) { hls.destroy(); hls = null; }
    $$("track", v).forEach((t) => t.remove());
    const forceStream = audioIndex != null && source && audioIndex !== source.DefaultAudioStreamIndex;
    info = await B.jf("POST", `/Items/${it.Id}/PlaybackInfo?userId=${u}`, {
      DeviceProfile: B.deviceProfile(QUALITY[settings.quality || "auto"] || QUALITY.auto),
      StartTimeTicks: Math.round((atSeconds || 0) * B.TICKS), AudioStreamIndex: audioIndex, SubtitleStreamIndex: subIndex,
      MaxStreamingBitrate: QUALITY[settings.quality || "auto"] || QUALITY.auto,
      EnableDirectPlay: !forceStream && (settings.quality || "auto") === "auto", EnableDirectStream: true, EnableTranscoding: true, AutoOpenLiveStream: true,
    });
    if (!here()) return;
    source = (info.MediaSources || [])[0];
    if (!source) throw new Error("Jellyfin couldn't find a way to play this.");
    playSession = info.PlaySessionId;
    if (audioIndex == null) audioIndex = source.DefaultAudioStreamIndex;
    if (subIndex == null) {
      subIndex = B.autoSub(source.MediaStreams, source.DefaultSubtitleStreamIndex ?? -1, audioIndex);
      // Plain subtitles before ones that also describe sounds ("[door slams]"), when both are there
      const subs = (source.MediaStreams || []).filter((s) => s.Type === "Subtitle");
      const pick = subs.find((s) => s.Index === subIndex);
      if (pick && pick.IsHearingImpaired) {
        const plain = subs.find((s) => !s.IsHearingImpaired && !s.IsForced && (s.Language || "") === (pick.Language || ""));
        if (plain) subIndex = plain.Index;
      }
      // A turned-down track that Jellyfin was going to draw into the picture: ask again with it off
      const dflt = source.DefaultSubtitleStreamIndex ?? -1;
      if (dflt >= 0 && subIndex === -1 && /SubtitleMethod=Encode/i.test(source.TranscodingUrl || "")) return load(atSeconds);
    }
    if (source.SupportsDirectPlay && !forceStream && !source.TranscodingUrl) {
      method = "DirectPlay";
      v.src = streamUrl(`/Videos/${it.Id}/stream?static=true&mediaSourceId=${enc(source.Id)}&api_key=${enc(B.me.token)}`);
      v.currentTime = atSeconds || 0;
    } else if (source.TranscodingUrl) {
      method = /videocodec=copy|copytimestamps/i.test(source.TranscodingUrl) && !/TranscodeReasons=.*Video/i.test(source.TranscodingUrl) ? "DirectStream" : "Transcode";
      const url = streamUrl(withKey(source.TranscodingUrl));
      if (window.Hls && Hls.isSupported()) {
        hls = new Hls({ startPosition: atSeconds || 0, maxBufferLength: 40, backBufferLength: 60 });
        hls.loadSource(url);
        hls.attachMedia(v);
        hls.on(Hls.Events.ERROR, (e, d) => {
          if (!d.fatal) return;
          if (d.type === Hls.ErrorTypes.NETWORK_ERROR) hls.startLoad();
          else if (d.type === Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError();
          else msg("This one won't play in the browser. Try the TV or phone.");
        });
      } else {
        v.src = url;
        v.addEventListener("loadedmetadata", () => { v.currentTime = atSeconds || 0; }, { once: true });
      }
    } else throw new Error("This one can't play in a browser.");
    // Subtitles the browser can show itself
    (source.MediaStreams || []).filter((s) => s.Type === "Subtitle" && s.DeliveryMethod === "External" && s.DeliveryUrl).forEach((s) => {
      const t = document.createElement("track");
      t.kind = "subtitles"; t.label = s.DisplayTitle || s.Language || "Subtitles"; t.srclang = s.Language || "en";
      const vtt = streamUrl(withKey(s.DeliveryUrl.replace(/\/Stream\.\w+/, "/Stream.vtt")));
      t.src = vtt + (vtt.includes("?") ? "&" : "?") + "fresh=" + Date.now(); // new words after a fix, not the browser's old copy
      t.dataset.index = s.Index;
      t.addEventListener("load", moveCues);
      v.appendChild(t);
    });
    showSubs();
    v.play().catch(() => { msg(""); B.toast("Press play to start."); });
    report("", {});
  };
  const showSubs = () => { Array.from(v.textTracks).forEach((t, i) => { const tr = $$("track", v)[i]; t.mode = tr && +tr.dataset.index === subIndex ? "showing" : "disabled"; }); moveCues(); };

  // ---------- Subtitle timing: move the words earlier or later by hand (kept for this video in this browser) ----------
  const delayKey = "benflix-subdelay-" + it.Id;
  let subDelay = 0;
  try { subDelay = +localStorage.getItem(delayKey) || 0; } catch (e) {}
  function moveCues() {
    Array.from(v.textTracks).forEach((t) => {
      const cues = Array.from(t.cues || []).filter((c) => (c.bfAt == null ? 0 : c.bfAt) !== subDelay);
      if (!cues.length) return;
      // Browsers leave old words on screen when times change while a line is showing (they pile up),
      // so each line is taken off the track, moved, and put back
      cues.forEach((c) => {
        if (c.bfStart == null) { c.bfStart = c.startTime; c.bfEnd = c.endTime; }
        c.bfAt = subDelay;
        try { t.removeCue(c); } catch (e) {}
        c.startTime = Math.max(0, c.bfStart + subDelay);
        c.endTime = Math.max(c.startTime + 0.01, c.bfEnd + subDelay);
        try { t.addCue(c); } catch (e) {}
      });
    });
  }
  // Ben (clean-up): while the controls show, subtitles slide up to sit just above them, then back down.
  // Only plain lines that don't place themselves (styled ones with their own spot stay where they are).
  let cueLift = 0;
  const liftCue = (c) => {
    if (!("snapToLines" in c)) return;
    if (c.bfLine === undefined) { if (c.line !== "auto") { c.bfLine = null; return; } c.bfLine = "auto"; }
    if (c.bfLine === null) return;
    if (cueLift > 0) { c.snapToLines = false; c.lineAlign = "end"; c.line = Math.max(10, 100 - cueLift); }
    else { c.snapToLines = true; c.lineAlign = "start"; c.line = "auto"; }
  };
  const liftCues = () => {
    const vh = v.getBoundingClientRect().height || 1;
    const want = pl.classList.contains("ui") ? Math.min(45, ((ctl.offsetHeight + 12) / vh) * 100) : 0;
    if (Math.abs(want - cueLift) < 0.5) return;
    cueLift = want;
    Array.from(v.textTracks).forEach((t) => {
      if (t.mode !== "showing") return;
      Array.from(t.cues || []).forEach(liftCue);
      // Lines already on screen are taken off and put back, so the browser redraws them in the new spot
      Array.from(t.activeCues || []).forEach((c) => { try { t.removeCue(c); t.addCue(c); } catch (e) {} });
    });
  };
  new MutationObserver(liftCues).observe(pl, { attributes: true, attributeFilter: ["class"] });
  v.textTracks.addEventListener("addtrack", (ev) => { ev.track.addEventListener("cuechange", () => Array.from(ev.track.activeCues || []).forEach(liftCue)); });
  const delayText = () => subDelay === 0 ? "Right now: not moved" : `Right now: ${Math.abs(subDelay).toFixed(1)} seconds ${subDelay > 0 ? "later" : "earlier"}`;
  const nudgeSubs = (by) => {
    const on = ((source && source.MediaStreams) || []).find((x) => x.Type === "Subtitle" && x.Index === subIndex);
    if (on && on.IsTextSubtitleStream === false) return B.toast("These subtitles are pictures, so they can't be moved. Try the other subtitles in the list.");
    subDelay = by === 0 ? 0 : Math.round((subDelay + by) * 10) / 10;
    try { localStorage.setItem(delayKey, String(subDelay)); } catch (e) {}
    moveCues();
    const now = $(".player .panel [data-delaynow]");
    if (now) now.textContent = delayText();
    B.toast("Subtitles: " + (subDelay === 0 ? "back to normal" : `${Math.abs(subDelay).toFixed(1)} seconds ${subDelay > 0 ? "later" : "earlier"}`));
  };

  // ---------- chapters bar ----------
  const segs = chapters.length > 1 && dur ? chapters.map((c, i) => ({ name: c.Name, start: c.StartPositionTicks / B.TICKS, end: i + 1 < chapters.length ? chapters[i + 1].StartPositionTicks / B.TICKS : dur })) : [{ name: "", start: 0, end: dur || 1 }];
  // Glowfin P1: one thin line with small white chapter marks (not gaps), like the approved player sheet
  $("#pTicks").innerHTML = segs.slice(1).map((s) => `<span class="tick" style="left:${(s.start / (dur || 1)) * 100}%"></span>`).join("");
  const total = () => v.duration && isFinite(v.duration) ? Math.max(v.duration, dur) : dur || 1;
  const drawBar = () => {
    const t = v.currentTime, T = total();
    let buf = 0; try { buf = v.buffered.length ? v.buffered.end(v.buffered.length - 1) : 0; } catch (e) {}
    $("#pDone").style.width = Math.max(0, Math.min(100, (t / T) * 100)) + "%";
    $("#pBuf").style.width = Math.max(0, Math.min(100, (buf / T) * 100)) + "%";
    $("#pKnob").style.left = Math.max(0, Math.min(100, (t / T) * 100)) + "%";
    $("#pTime").textContent = B.hms(t);
    $("#pEnds").textContent = `Ends at ${B.clock(new Date(Date.now() + ((T - t) / (v.playbackRate || 1)) * 1000))}`;
    $("#pLeft").textContent = `−${B.hms(Math.max(0, T - t))}`;
  };
  const barTime = (e) => { const r = $("#pBar").getBoundingClientRect(); return Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)) * total(); };
  $("#pBar").onclick = (e) => { v.currentTime = barTime(e); poke(); };
  // Picture previews while moving along the bar (Jellyfin's "trickplay" pictures, the same ones the TV and
  // phone show). Each picture file is a grid of small frames; we show the one for that moment.
  const trick = (() => {
    const bySource = it.Trickplay || {};
    const forSource = (source && bySource[source.Id]) || Object.values(bySource)[0];
    if (!forSource) return null;
    const widths = Object.keys(forSource).map(Number).sort((a, b) => a - b);
    const w = widths.find((x) => x >= 240) || widths[widths.length - 1];
    const t = forSource[w];
    if (!t || !t.Interval || !t.TileWidth || !t.TileHeight) return null;
    const srcId = (source && bySource[source.Id]) ? source.Id : Object.keys(bySource)[0];
    return { w, h: t.Height || Math.round(w * 9 / 16), cols: t.TileWidth, rows: t.TileHeight, every: t.Interval / 1000, count: t.ThumbnailCount || 0, src: srcId };
  });
  const showPreview = (t) => {
    const p = $("#pPrev"), tp = trick();
    if (!tp) return p.classList.add("hidden");
    let n = Math.floor(t / tp.every);
    if (tp.count) n = Math.min(n, tp.count - 1);
    const per = tp.cols * tp.rows, file = Math.floor(n / per), at = n % per;
    const scale = 200 / tp.w, ww = 200, hh = Math.round(tp.h * scale);
    p.style.width = ww + "px"; p.style.height = hh + "px";
    p.style.backgroundImage = `url('${streamUrl(`/Videos/${it.Id}/Trickplay/${tp.w}/${file}.jpg?mediaSourceId=${enc(tp.src)}&api_key=${enc(B.me.token)}`)}')`;
    p.style.backgroundSize = `${tp.cols * ww}px ${tp.rows * hh}px`;
    p.style.backgroundPosition = `-${(at % tp.cols) * ww}px -${Math.floor(at / tp.cols) * hh}px`;
    const r = $("#pBar").getBoundingClientRect(), x = (t / total()) * r.width;
    p.style.left = Math.max(ww / 2, Math.min(r.width - ww / 2, x)) + "px";
    p.classList.remove("hidden");
  };
  $("#pBar").onmousemove = (e) => {
    const t = barTime(e), h = $("#pHover"), ch = segs.find((s) => t >= s.start && t < s.end);
    h.classList.remove("hidden"); h.textContent = B.hms(t) + (ch && ch.name ? " · " + ch.name : "");
    h.style.left = (t / total()) * 100 + "%";
    showPreview(t);
  };
  $("#pBar").onmouseleave = () => { $("#pHover").classList.add("hidden"); $("#pPrev").classList.add("hidden"); };

  // ---------- controls ----------
  const poke = (ms) => {
    pl.classList.add("ui");
    clearTimeout(uiTimer);
    uiTimer = setTimeout(() => { if (!v.paused && !$("#pEps:not(.hidden)") && !$(".player .panel")) pl.classList.remove("ui"); }, typeof ms === "number" ? ms : 5000);
  };
  pl.addEventListener("mousemove", poke);
  pl.addEventListener("click", (e) => { if (e.target === v || e.target.classList.contains("center-msg")) { v.paused ? v.play() : v.pause(); } poke(); });
  v.addEventListener("dblclick", () => full());
  const full = () => (document.fullscreenElement ? document.exitFullscreen() : pl.requestFullscreen().catch(() => {}));
  $("#pFull").onclick = full;
  // Pop-out player: the video keeps playing in a small window on top of other tabs and apps
  if ($("#pPip")) $("#pPip").onclick = () => {
    (document.pictureInPictureElement ? document.exitPictureInPicture() : v.requestPictureInPicture())
      .catch(() => B.toast("This browser can't pop the video out."));
  };
  // Play speed, remembered per show (or per movie)
  const speedKey = "benflix.speed." + ((isEp && it.SeriesId) || it.Id);
  const setSpeed = (r) => {
    v.defaultPlaybackRate = r; v.playbackRate = r;
    try { if (r === 1) localStorage.removeItem(speedKey); else localStorage.setItem(speedKey, String(r)); } catch (e) {}
  };
  try { const saved = +localStorage.getItem(speedKey); if (saved > 0 && saved !== 1) { v.defaultPlaybackRate = saved; v.playbackRate = saved; } } catch (e) {}
  v.addEventListener("loadedmetadata", () => { if (v.playbackRate !== v.defaultPlaybackRate) v.playbackRate = v.defaultPlaybackRate; });
  $("#pPlay").onclick = () => (v.paused ? v.play() : v.pause());
  $("#pBk").onclick = () => { v.currentTime = Math.max(0, v.currentTime - 10); poke(); };
  $("#pFw").onclick = () => { v.currentTime = Math.min(total(), v.currentTime + 10); poke(); };
  $("#pBack").onclick = () => leave();
  $("#pSub").onclick = () => openPanel("subs");
  $("#pAud").onclick = () => openPanel("audio");
  $("#pGear").onclick = () => openPanel("settings");
  $("#pKeys").onclick = () => showKeys();
  // Ben (clean-up): pressing Play puts the controls away quickly, just long enough to see it switch
  v.addEventListener("play", () => { $("#pPlay").innerHTML = I("pause"); pl.classList.remove("paused-long"); clearTimeout(pausedTimer); poke(600); report("/Progress"); });
  v.addEventListener("pause", () => {
    $("#pPlay").innerHTML = I("play"); pl.classList.add("ui"); report("/Progress");
    clearTimeout(pausedTimer); pausedTimer = setTimeout(() => { if (v.paused) pl.classList.add("paused-long"); }, 5000);
  });
  v.addEventListener("playing", () => msg(""));
  v.addEventListener("waiting", () => msg("Loading…"));
  v.addEventListener("error", () => { if (method === "DirectPlay" && !stopped) { audioIndex = audioIndex ?? 0; settings.quality = settings.quality || "auto"; forceTranscode(); } });
  const forceTranscode = () => { const at = v.currentTime; source = Object.assign({}, source, { DefaultAudioStreamIndex: -99 }); load(at).catch((e) => msg(e.message)); };

  // ---------- skip intro / credits, Up next ----------
  const series = isEp ? it.SeriesId : null;
  let segments = [];
  let alwaysSkip = { intro: new Set(), outro: new Set() };
  const plain = (id) => String(id || "").replace(/-/g, "").toLowerCase();
  B.prefs().then((p) => {
    const c = p.CustomPrefs || {};
    ["intro", "outro"].forEach((k) => { alwaysSkip[k] = new Set(String(c[k === "intro" ? "benflix.autoSkipIntro" : "benflix.autoSkipOutro"] || "").split("|").map(plain).filter(Boolean)); });
  }).catch(() => {});
  // Skip times: Tower's first (it compares every episode's sound, reads chapters, asks TheIntroDB and
  // uses fixes people made), else the server's own. Only times Tower is sure of skip by themselves.
  const fromServer = () => B.get(`/MediaSegments/${it.Id}`).then((r) => { if (!segments.tower) segments = (r.Items || []).map((s) => ({ type: s.Type, start: s.StartTicks / B.TICKS, end: s.EndTicks / B.TICKS })); }).catch(() => {});
  const useTower = (r) => {
    const list = ((r && r.segments) || []).filter((s) => s.end > s.start).map((s) => ({ type: s.type, start: s.start, end: s.end, sure: !!s.sure, fixed: s.source === "you" || s.source === "fix" }));
    segments = list;
    segments.tower = true;
  };
  // An intro is never "Skip credits": credits only count in the last quarter. Credits near the start
  // (an anime opening shows staff names, or the sources mixed up opening and ending) become the intro
  // when they're intro length, else they're dropped. Intro and recap win when kinds overlap.
  const tidySegments = (list, T) => {
    if (!list.some((s) => s.type === "Outro")) return list;
    const out = [];
    list.forEach((s) => {
      if (s.type !== "Outro") return out.push(s);
      if (T > 0 && s.start >= T * 0.75) return out.push(s);
      if (!(T > 0)) return;
      const len = s.end - s.start;
      const hasIntro = list.some((o) => o.type === "Intro" && o.start < s.end && s.start < o.end);
      if (s.start <= T * 0.35 && len >= 12 && len <= 300 && !hasIntro) out.push({ ...s, type: "Intro", sure: false });
    });
    const rank = (s) => (s.type === "Intro" || s.type === "Recap" ? 0 : s.type === "Outro" ? 2 : 1);
    return out.sort((a, b) => rank(a) - rank(b));
  };
  let towerTries = 0;
  const fromTower = () => B.call("GET", `api/skips/${plain(it.Id)}`).then((r) => {
    useTower(r);
    // Tower is still checking this season: ask again in a minute (for up to 10 minutes)
    if (r && r.ready === false && ++towerTries <= 10) setTimeout(() => { if (here()) fromTower(); }, 60000);
  }).catch(() => { if (!segments.tower) fromServer(); });
  fromServer();
  fromTower();
  const words = { Intro: "Skip intro", Recap: "Skip recap", Outro: "Skip credits", Preview: "Skip preview", Commercial: "Skip" };
  // Keep the request itself, so a second ask while the first is still loading waits for the same
  // answer (before, it got "nothing" and the player left, landing back on the episode before).
  let nextAsk = null;
  const findNext = () => {
    if (!nextAsk) nextAsk = (async () => {
      if (isEp) {
        const r = await B.get(`/Shows/${it.SeriesId}/Episodes?userId=${u}&StartItemId=${it.Id}&Limit=2&Fields=Overview`);
        const list = r.Items || [];
        const n = list[0] && list[0].Id === it.Id ? list[1] : list.find((x) => x.Id !== it.Id);
        return n || null;
      }
      if (B.q("trailer")) return null;
      const r = await B.get(`/Items/${it.Id}/Similar?userId=${u}&Limit=12`);
      return (r.Items || []).find((x) => !(x.UserData || {}).Played) || null;
    })().catch(() => { nextAsk = null; return null; });
    return nextAsk;
  };
  const autoNext = settings.autoplay !== false && (isEp || autoplayCount < 3);
  const showUpNext = async () => {
    nextShown = true;
    const n = await findNext();
    if (!n || !here() || away()) return;
    const box = $("#pNext");
    const wait = isEp ? 10 : 20;
    box.classList.remove("hidden");
    box.innerHTML = `<div class="dim" style="font-weight:700">Up next</div><div class="im" style="background-image:url('${B.thumb(n, 600)}')"></div>
      <div style="font-weight:700">${esc(n.Type === "Episode" ? `S${n.ParentIndexNumber} E${n.IndexNumber} · ${n.Name}` : n.Name)}</div>
      ${autoNext ? `<div class="cd"><i id="cdBar" style="width:100%"></i></div>` : ""}
      <div class="row"><button class="btn white small" id="nPlay">${I("play")} Play now</button><button class="btn small" id="nStay">Watch credits</button></div>`;
    $("#nPlay").onclick = () => { sessionStorage.setItem("benflix.autoplays", "0"); goNext(n); };
    $("#nStay").onclick = () => { box.classList.add("hidden"); clearInterval(cd); };
    let left = wait, cd = null;
    if (autoNext) {
      requestAnimationFrame(() => { const b = $("#cdBar"); if (b) { b.style.transition = `width ${wait}s linear`; b.style.width = "0%"; } });
      cd = setInterval(() => { if (v.paused) return; if (--left <= 0) { clearInterval(cd); sessionStorage.setItem("benflix.autoplays", String(autoplayCount + 1)); goNext(n); } }, 1000);
      B.onLeave(() => clearInterval(cd));
    }
  };
  // Swap this episode for the next one in the browser history, so Back never lands on the one before
  const goNext = (n) => { if (away()) return B.dropKept(); finish(); B.go(`#/play/${n.Id}`, { replace: true }); };
  $("#pNx") && ($("#pNx").onclick = async () => { const n = await findNext(); if (n) goNext(n); else B.toast("That was the last episode."); });
  // Previous episode (clean-up, Ben 2026-11-18): the episode before in the show, dimmed on the first one
  let prevEp = null;
  if (isEp && it.SeriesId) {
    B.get(`/Shows/${it.SeriesId}/Episodes?userId=${u}&AdjacentTo=${it.Id}`).then((r) => {
      const list = (r.Items || []).filter((x) => x.Id === it.Id || (x.ParentIndexNumber || 0) !== 0);
      const i = list.findIndex((x) => x.Id === it.Id);
      prevEp = i > 0 ? list[i - 1] : null;
      const b = $("#pPv"); if (b && prevEp) b.disabled = false;
    }).catch(() => {});
  }
  $("#pPv") && ($("#pPv").onclick = () => { if (prevEp) goNext(prevEp); else B.toast("This is the first episode."); });

  v.addEventListener("timeupdate", () => {
    if (stopped) return; // the page has gone (the bar isn't there any more)
    const t = v.currentTime;
    if (subDelay) moveCues(); // words that arrive while playing get moved too
    drawBar();
    const now = performance.now();
    if (!v.paused && lastTick) WT.add(Math.min(5000, now - lastTick));
    lastTick = now;
    const seg = tidySegments(segments, total()).find((s) => t >= s.start && t < s.end - 1 && words[s.type]);
    const sk = $("#pSkip");
    if (seg) {
      const auto = (seg.type === "Intro" || seg.type === "Recap") && series && alwaysSkip.intro.has(plain(series)) || seg.type === "Outro" && series && alwaysSkip.outro.has(plain(series));
      // One skip per kind, so a refreshed time (after a fix) doesn't jump again
      if ((auto || settings.autoskip) && seg.sure !== false && !skipped.has(seg.type)) { skipped.add(seg.type); v.currentTime = seg.end; B.toast(words[seg.type].replace("Skip", "Skipped")); return; }
      sk.textContent = words[seg.type]; sk.classList.remove("hidden"); sk.onclick = () => { skipped.add(seg.type); v.currentTime = seg.end; sk.classList.add("hidden"); };
    } else sk.classList.add("hidden");
    // Same as the TV and phone: while the Up next box is showing, it is the one thing in that corner
    // (Play now / Watch credits). Skip credits comes back if you pick Watch credits.
    if (!$("#pNext").classList.contains("hidden")) sk.classList.add("hidden");
    const outro = tidySegments(segments, total()).find((s) => s.type === "Outro");
    const T = total();
    if (!nextShown && T > 60 && ((outro && t >= outro.start) || (!outro && T - t < (isEp ? 25 : 60)))) showUpNext();
  });
  v.addEventListener("ended", async () => { if (away()) return B.dropKept(); const n = await findNext(); if (n && autoNext) goNext(n); else leave(); });
  const progress = () => { if (!v.paused) report("/Progress"); };
  B.every(10000, progress);

  // ---------- the one settings panel (Redesign 2 pl-family, same as the TV and phone) ----------
  // Subtitles, Audio, Subtitle timing and Quality first, then the "Fix a problem" list.
  const QUALITY_WORDS = { auto: "Best", "1080": "Sharp (1080p)", "720": "Good (720p)", "480": "Light (480p)" };
  const QUALITY_STEPS = ["auto", "1080", "720", "480"];
  const fixNoSound = () => { B.toast("No sound? Bringing it back…"); audioIndex = audioIndex ?? 0; forceTranscode(); };
  const fixPicture = () => {
    const now = settings.quality || "auto", next = QUALITY_STEPS[QUALITY_STEPS.indexOf(now) + 1];
    if (!next) return B.toast("That's the lightest picture already. Report a problem so Ben can look at it.");
    settings.quality = next; B.setWebSetting("quality", next);
    B.toast(`Trying a lighter picture: ${QUALITY_WORDS[next]}. Change it back in Settings > Quality.`);
    load(v.currentTime).catch((err) => msg(err.message));
  };
  // ---------- Idea Box 25 "Also counts for": someone watching with Ben on his profile keeps up too ----------
  // Only the main profile (Jellyfin only lets an admin save someone else's spot). The ticks last until the player is left.
  const alsoOk = !!(B.me && B.me.admin && !B.me.kids && (it.Type === "Movie" || it.Type === "Episode"));
  B.alsoFor = B.alsoFor || [];
  const drawAlso = async (p) => {
    const box = $("[data-alsolist]", p); if (!box) return;
    if (!B.familyUsers) B.familyUsers = await B.jf("GET", "/Users?isDisabled=false").then((list) => (list || []).filter((x) => x.Id !== B.uid())).catch(() => null);
    if (!box.isConnected) return;
    const list = B.familyUsers || [];
    box.innerHTML = list.length ? list.map((x) => `<button class="opt ${B.alsoFor.some((y) => y.id === x.Id) ? "on" : ""}" data-also="${esc(x.Id)}" data-name="${esc(x.Name || "")}">${esc(x.Name || "Someone")}</button>`).join("") : '<p class="dim">There are no other profiles on Glowfin yet.</p>';
  };
  const copyToAlso = (posTicks, len) => {
    const people = alsoOk ? B.alsoFor : [];
    if (!people.length || posTicks <= 0) return;
    const finished = len > 0 && posTicks / B.TICKS >= len * 0.9;
    people.forEach((x) => {
      const done = finished
        ? B.jf("POST", `/UserPlayedItems/${it.Id}?userId=${enc(x.id)}`)
        : B.jf("POST", `/UserItems/${it.Id}/UserData?userId=${enc(x.id)}`, { PlaybackPositionTicks: posTicks, LastPlayedDate: new Date().toISOString() });
      done.catch(() => {});
    });
  };
  // Clean-up (Ben 2026-11-18): Subtitles, Audio and Settings each open their own box, nothing repeated
  const openPanel = (kind) => {
    const old = $(".player .panel");
    if (old) { old.remove(); if (old.dataset.kind === kind) return; }
    const streams = (source && source.MediaStreams) || [];
    const audio = streams.filter((s) => s.Type === "Audio"), subs = streams.filter((s) => s.Type === "Subtitle");
    const subOn = subIndex !== -1, cur = subs.find((x) => x.Index === subIndex) || {};
    const canMove = subOn && cur.IsTextSubtitleStream !== false;
    const p = document.createElement("div");
    p.className = "panel";
    p.dataset.kind = kind;
    const head = (title) => `<button class="circle" style="float:right" data-x>${I("x")}</button><h3 style="margin:4px 0 0">${title}</h3>
      <p class="dim" style="font-size:13px;margin:2px 0 0">${esc(B.lastPlayed.title || "")}</p>`;
    const volOn = !v.muted && v.volume > 0, volPct = Math.round((v.muted ? 0 : v.volume) * 100);
    if (kind === "subs") p.innerHTML = head("Subtitles") + `
      <h4 id="psSubs">Pick subtitles</h4><button class="opt ${subIndex === -1 ? "on" : ""}" data-s="-1">Off</button>${subs.map((s) => `<button class="opt ${s.Index === subIndex ? "on" : ""}" data-s="${s.Index}">${esc(s.DisplayTitle || s.Language || "Subtitles")}${s.IsTextSubtitleStream === false ? "<small>pictures, can't be moved</small>" : ""}</button>`).join("")}
      <h4>Something off?</h4>
      <button class="opt" data-subwrong>${I("sub")} Subtitle timing<small>${!subOn ? "Turn subtitles on above first" : B.me.kids ? "Move them yourself below" : "Let Tower line them up, or move them yourself below"}</small></button>
      <div class="timing hidden">${!B.me.kids && subOn ? `<button class="opt" data-towerfix>Let Tower line them up<small>Tower matches the words to the voices</small></button>` : ""}
      ${canMove ? `<p class="dim" style="font-size:13px;margin:8px 0 8px">Or move them yourself. Words show up before people talk? Press + to make them later. Words come after? Press −. (Keys: G sooner, H later.) <b data-delaynow>${esc(delayText())}</b></p><div class="delay-row">${[[-10, "−10 s"], [-1, "−1 s"], [-0.5, "−½ s"], [0.5, "+½ s"], [1, "+1 s"], [10, "+10 s"], [0, "Reset"]].map(([d, label]) => `<button class="opt" data-delay="${d}">${label}</button>`).join("")}</div>` : subOn ? `<p class="dim" style="font-size:13px;margin:8px 0">These subtitles are pictures, so they can't be moved by hand.</p>` : ""}</div>
      ${!B.me.kids ? `<button class="opt" data-better>${I("sub")} Find other subtitles<small>${subOn ? "Bazarr deletes these and gets different ones" : "Bazarr looks for subtitles"}</small></button>` : ""}`;
    else if (kind === "audio") p.innerHTML = head("Audio") + `
      <h4 id="psAudio">Sound track</h4>${audio.map((s) => `<button class="opt ${s.Index === audioIndex ? "on" : ""}" data-a="${s.Index}">${esc(s.DisplayTitle || s.Language || "Track")}</button>`).join("") || '<p class="dim">One sound track.</p>'}
      <h4>Volume</h4>
      <div class="pvol"><button class="circle" data-mute title="Sound on / off (M)">${I(volOn ? "vol" : "mute")}</button><input type="range" min="0" max="100" step="1" value="${volPct}" style="--v:${volPct}%" aria-label="Volume" data-vol><b data-volnum>${volPct}%</b></div>`;
    else p.innerHTML = head("Settings") + `
      <h4>Quality</h4>${QUALITY_STEPS.map((k) => `<button class="opt ${(settings.quality || "auto") === k ? "on" : ""}" data-q="${k}">${QUALITY_WORDS[k]}</button>`).join("")}
      <h4>Play speed</h4><div class="delay-row">${SPEEDS.map((r) => `<button class="opt ${v.playbackRate === r ? "on" : ""}" data-speed="${r}">${r === 1 ? "Normal" : r + "×"}</button>`).join("")}</div>
      ${series ? `<h4>This show</h4><button class="opt ${alwaysSkip.intro.has(plain(series)) ? "on" : ""}" data-skip="intro">Always skip the intro</button><button class="opt ${alwaysSkip.outro.has(plain(series)) ? "on" : ""}" data-skip="outro">Always skip the credits</button>` : ""}
      ${alsoOk ? `<h4>Also counts for</h4><p class="dim" style="font-size:13px;margin:-4px 0 8px">Tick who's watching with you. When this ends, it's marked watched for them too.</p><div data-alsolist><p class="dim">Looking for family profiles…</p></div>` : ""}
      <h4>Fix a problem</h4>
      <button class="opt" data-nosound>${I("flag")} No sound<small>Glowfin brings it back</small></button>
      ${segments.tower && !B.me.kids ? `<button class="opt" data-skipwrong>${I("flag")} Skip at the wrong time<small>Mark the right spot</small></button><div class="fixlist hidden"><p class="dim" style="font-size:13px;margin:4px 0 10px">Pause at the right spot, then press one. Glowfin finds the same spot in the show's other episodes too.</p>${FIXES.map(([k, label, hint]) => `<button class="opt" data-fix="${k}">${esc(label)}${hint ? `<small>${esc(hint)}</small>` : ""}</button>`).join("")}</div>` : ""}
      <button class="opt" data-stutter>${I("flag")} Picture stutters<small>Tries a lighter picture</small></button>
      <button class="opt" data-report>${I("flag")} Report a problem<small>Ben gets a note</small></button>
      ${B.me.admin ? `<p class="dim" style="font-size:13px;margin-top:16px">${esc(method === "DirectPlay" ? "Playing the file as it is" : method === "DirectStream" ? "Repackaged on the fly (light work for the server)" : "Converted on the fly (the server is working)")}</p>` : ""}`;
    pl.appendChild(p);
    pl.classList.add("ui");
    if (alsoOk && kind === "settings") drawAlso(p);
    if (kind === "subs") { const on = $(".opt.on[data-s]", p) || $("[data-s]", p); if (on) on.focus(); }
    if (kind === "audio") { const on = $(".opt.on[data-a]", p) || $("[data-a]", p); if (on) on.focus({ preventScroll: true }); }
    // The volume slider in the Audio box (the bar has none now)
    const vr = $("[data-vol]", p);
    if (vr) {
      const draw = () => {
        const pct = Math.round((v.muted ? 0 : v.volume) * 100);
        vr.value = pct; vr.style.setProperty("--v", pct + "%");
        $("[data-volnum]", p).textContent = pct + "%";
        $("[data-mute]", p).innerHTML = I(pct === 0 ? "mute" : "vol");
      };
      let saveT;
      vr.addEventListener("input", () => {
        v.volume = vr.value / 100; if (+vr.value > 0) v.muted = false; draw();
        clearTimeout(saveT); saveT = setTimeout(() => B.setWebSetting("volume", v.volume), 300);
      });
      vr.addEventListener("change", () => vr.blur()); // so Space and the arrow keys go back to the player
      $("[data-mute]", p).onclick = (e) => { e.stopPropagation(); v.muted = !v.muted; if (!v.muted && v.volume === 0) v.volume = 0.5; draw(); };
    }
    p.onclick = async (e) => {
      if (e.target.closest("[data-x]")) return p.remove();
      const a = e.target.closest("[data-a]"), s = e.target.closest("[data-s]"), qq = e.target.closest("[data-q]"), sk = e.target.closest("[data-skip]");
      const fx = e.target.closest("[data-fix]"), dl = e.target.closest("[data-delay]");
      if (dl) return nudgeSubs(+dl.dataset.delay);
      const al = e.target.closest("[data-also]");
      if (al) {
        const id = al.dataset.also, had = B.alsoFor.some((x) => x.id === id);
        B.alsoFor = had ? B.alsoFor.filter((x) => x.id !== id) : B.alsoFor.concat([{ id, name: al.dataset.name }]);
        al.classList.toggle("on", !had);
        return;
      }
      const sp = e.target.closest("[data-speed]");
      if (sp) { setSpeed(+sp.dataset.speed); $$("[data-speed]", p).forEach((b) => b.classList.toggle("on", b === sp)); return B.toast("Play speed: " + (+sp.dataset.speed === 1 ? "normal" : sp.dataset.speed + "×")); }
      if (fx) { p.remove(); return fixSkip(fx.dataset.fix); }
      if (e.target.closest("[data-skipwrong]")) return $(".fixlist", p).classList.toggle("hidden");
      if (e.target.closest("[data-nosound]")) { p.remove(); return fixNoSound(); }
      if (e.target.closest("[data-stutter]")) { p.remove(); return fixPicture(); }
      if (e.target.closest("[data-better]")) { p.remove(); return fixSubs(true); }
      if (e.target.closest("[data-subwrong]")) {
        if (!subOn) return B.toast("Turn subtitles on at the top first.");
        return $(".timing", p).classList.toggle("hidden");
      }
      if (e.target.closest("[data-towerfix]")) { p.remove(); return fixSubs(); }
      if (e.target.closest("[data-report]")) { p.remove(); v.pause(); return B.report(B.lastPlayed); }
      if (a) { audioIndex = +a.dataset.a; p.remove(); return load(v.currentTime).catch((err) => msg(err.message)); }
      if (s) {
        const idx = +s.dataset.s, st = subs.find((x) => x.Index === idx);
        subIndex = idx; p.remove();
        if (idx === -1 || (st && st.DeliveryMethod === "External")) { showSubs(); return report("/Progress"); } // tell Tower straight away
        return load(v.currentTime).catch((err) => msg(err.message)); // picture subtitles are drawn in by the server
      }
      if (qq) { settings.quality = qq.dataset.q; B.setWebSetting("quality", qq.dataset.q); p.remove(); return load(v.currentTime).catch((err) => msg(err.message)); }
      if (sk) {
        const k = sk.dataset.skip, set = alwaysSkip[k], on = !set.has(plain(series));
        on ? set.add(plain(series)) : set.delete(plain(series));
        sk.classList.toggle("on", on);
        const dashed = (id) => id.length === 32 ? `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}` : id;
        B.savePrefs((c) => { const key = k === "intro" ? "benflix.autoSkipIntro" : "benflix.autoSkipOutro"; c[key] = [...set].map(dashed).join("|") || null; }).catch(() => {});
      }
    };
  };

  // ---------- "Skip came at the wrong time?": saved on Tower at the spot the video is at now ----------
  const fixSkip = async (kind) => {
    const at = Math.round(v.currentTime * 100) / 100;
    let r;
    try { r = await B.call("POST", `api/skips/${plain(it.Id)}/fix`, { kind, at }); } catch (err) { return B.toast(err.message); }
    useTower(r);
    // Don't jump away straight after marking where something starts (you may want to mark its end too)
    if (kind === "intro_start") skipped.add("Intro");
    if (kind === "credits_start") skipped.add("Outro");
    if (kind === "undo") skipped.clear();
    B.toast(`Saved: ${(r && r.saved) || "your fix"}. ${kind.startsWith("no_intro") || kind === "undo" ? "" : "Tower is fixing the other episodes of this show too."}`.trim());
  };

  // ---------- "Subtitles are early or late": Tower lines them up with the voices ----------
  const fixSubs = async (better) => {
    const was = ((source && source.MediaStreams) || []).find((x) => x.Index === subIndex) || {};
    let job;
    try { job = await B.call("POST", `api/subsync/${enc(it.Id)}`, { index: subIndex, better: !!better }); } catch (err) { return B.toast(err.message); }
    B.toast(job.message || "Tower is lining the subtitles up. It takes a minute or two.");
    for (let i = 0; i < 90 && here(); i++) {
      await new Promise((r) => setTimeout(r, 4000));
      try { job = await B.call("GET", `api/subsync/${enc(it.Id)}`); } catch (e) { continue; }
      if (job.state === "done" || job.state === "failed") break;
    }
    if (!here()) return;
    B.toast(job.message || "Done.");
    if (job.state !== "done" || !job.changed) return;
    const before = new Set(((source && source.MediaStreams) || []).map((x) => x.Index));
    await load(v.currentTime).catch((err) => msg(err.message));
    // Subtitles that were inside the video come back as a new choice: switch to it
    const fresh = ((source && source.MediaStreams) || []).filter((x) => x.Type === "Subtitle" && x.IsExternal && !before.has(x.Index) && (!was.Language || (x.Language || "") === was.Language));
    if (fresh.length) { subIndex = fresh[fresh.length - 1].Index; await load(v.currentTime).catch(() => {}); }
  };

  // ---------- episodes row in the player ----------
  if (isEp) $("#pList").onclick = async () => {
    const box = $("#pEps");
    if (!box.classList.contains("hidden")) return box.classList.add("hidden");
    const r = await B.get(`/Shows/${it.SeriesId}/Episodes?userId=${u}&SeasonId=${it.SeasonId}&Fields=Overview`);
    box.innerHTML = B.row(`Season ${it.ParentIndexNumber}`, (r.Items || []).map((e) => B.wideCard(e, { href: `#/play/${e.Id}`, title: `${e.IndexNumber}. ${e.Name}`, sub: e.Id === it.Id ? "Playing now" : "" })));
    box.classList.remove("hidden");
    const cur = $(`#pEps [data-id="${CSS.escape(it.Id)}"]`); if (cur) cur.scrollIntoView({ inline: "center", block: "nearest" });
  };

  // ---------- keyboard ----------
  const keys = (e) => {
    if (e.target.tagName === "INPUT" || $(".shade")) return;
    const k = e.key;
    if (k === " " || k === "k") { e.preventDefault(); v.paused ? v.play() : v.pause(); return; } // play/pause set how long the controls stay
    else if (k === "ArrowLeft") { v.currentTime = Math.max(0, v.currentTime - 10); }
    else if (k === "ArrowRight") { v.currentTime = Math.min(total(), v.currentTime + 10); }
    else if (k === "ArrowUp") { e.preventDefault(); v.volume = Math.min(1, v.volume + 0.1); B.setWebSetting("volume", v.volume); }
    else if (k === "ArrowDown") { e.preventDefault(); v.volume = Math.max(0, v.volume - 0.1); B.setWebSetting("volume", v.volume); }
    else if (k === "j") { v.currentTime = Math.max(0, v.currentTime - 10); }
    else if (k === "l") { v.currentTime = Math.min(total(), v.currentTime + 10); }
    else if (k === "f" || k === "F") full();
    else if (k === "m" || k === "M") { v.muted = !v.muted; B.toast(v.muted ? "Sound off (M turns it back on)" : "Sound on"); }
    else if (k === "s" || k === "S" || k === "c" || k === "C") toggleSubs();
    else if (k === "g" || k === "G") nudgeSubs(-0.5);
    else if (k === "h" || k === "H") nudgeSubs(0.5);
    else if (k === "Enter") { const sk = $("#pSkip:not(.hidden)"); if (sk) sk.click(); else return; }
    else if ((k === "n" || k === "N") && $("#pNx")) $("#pNx").click();
    else if ((k === "p" || k === "P") && $("#pPv")) $("#pPv").click();
    else if (k === "e" && $("#pList")) $("#pList").click();
    else if (/^[0-9]$/.test(k) && !e.ctrlKey && !e.metaKey) { v.currentTime = (total() * +k) / 10; }
    else if (k === "?" || (k === "/" && e.shiftKey)) { e.preventDefault(); showKeys(); return; }
    else if (k === "Escape" && $(".player .keys-help")) { $(".player .keys-help").remove(); return; }
    else if (k === "Escape" && !document.fullscreenElement) leave();
    else return;
    poke();
  };
  // Benflix "Keyboard shortcuts": S turns subtitles on and off (back to the last ones you had on)
  let lastSub = null;
  const toggleSubs = () => {
    const subs = ((source && source.MediaStreams) || []).filter((x) => x.Type === "Subtitle");
    if (!subs.length) return B.toast("No subtitles for this one.");
    if (subIndex !== -1) { lastSub = subIndex; subIndex = -1; showSubs(); return B.toast("Subtitles off"); }
    const pick = subs.find((x) => x.Index === lastSub) || subs.find((x) => /^(en|eng)$/i.test(x.Language || "")) || subs[0];
    subIndex = pick.Index;
    B.toast("Subtitles: " + (pick.DisplayTitle || pick.Language || "on"));
    if (pick.DeliveryMethod === "External") showSubs();
    else load(v.currentTime).catch((err) => msg(err.message)); // picture subtitles are drawn in by the server
  };
  const KEYS = [
    ["Space or K", "Play / pause"], ["← / →  or  J / L", "Back / forward 10 seconds"], ["↑ / ↓", "Louder / quieter"],
    ["F", "Full screen"], ["M", "Sound on / off"], ["C or S", "Subtitles on / off"], ["G / H", "Subtitles earlier / later"], ["Enter", "Skip intro or credits"],
    ["N / P", "Next / previous episode"], ["E", "Episodes"], ["0 to 9", "Jump to 0% to 90%"], ["Esc", "Leave the player"], ["?", "Show this list"],
  ];
  const showKeys = () => {
    const old = $(".player .keys-help");
    if (old) return old.remove();
    const box = document.createElement("div");
    box.className = "panel keys-help";
    box.innerHTML = `<button class="circle" style="float:right" data-x>${I("x")}</button><h3 style="margin:4px 0 12px">Keyboard shortcuts</h3>
      ${KEYS.map(([key, what]) => `<div class="keyrow"><kbd>${esc(key)}</kbd><span>${esc(what)}</span></div>`).join("")}`;
    box.onclick = (e) => { if (e.target.closest("[data-x]")) box.remove(); };
    pl.appendChild(box);
    pl.classList.add("ui");
  };
  document.addEventListener("keydown", keys);
  // Glowfin (W1): the main keys, shown once on this browser, then never again (? still lists them all)
  try {
    if (!localStorage.getItem("glowfin.playerKeysShown")) {
      localStorage.setItem("glowfin.playerKeysShown", "1");
      const tip = document.createElement("div");
      tip.className = "keytip";
      tip.innerHTML = [["Space", "Pause"], ["← →", "10 seconds"], ["F", "Full screen"], ["C", "Subtitles"]].map(([k, w]) => `<span><kbd>${k}</kbd>${w}</span>`).join("");
      pl.appendChild(tip);
      setTimeout(() => tip.remove(), 7000);
    }
  } catch (e) {}

  // ---------- stopping ----------
  const popped = () => !stopped && document.pictureInPictureElement === v;
  const finish = () => {
    if (stopped) return;
    stopped = true;
    const pos = Math.round(v.currentTime * B.TICKS);
    const len = v.duration > 0 ? v.duration : dur;
    report("/Stopped", { PositionTicks: pos }).then(() => copyToAlso(pos, len));
    if (method !== "DirectPlay" && B.me) B.jf("DELETE", `/Videos/ActiveEncodings?deviceId=${enc("benflix-web-" + B.me.device)}&playSessionId=${enc(playSession || "")}`).catch(() => {});
    WT.send();
    if (hls) hls.destroy();
    v.removeAttribute("src"); try { v.load(); } catch (e) {}
  };
  const leave = () => { if (!popped()) finish(); B.alsoFor = []; if (document.fullscreenElement) document.exitFullscreen().catch(() => {}); B.back(); };
  const unload = () => {
    if (stopped || !B.me) return;
    fetch("api/web/jf/Sessions/Playing/Stopped", { method: "POST", keepalive: true, headers: { "Content-Type": "application/json", "X-Benflix-Token": B.me.token, "X-Benflix-Device": B.me.device || "" },
      body: JSON.stringify({ ItemId: it.Id, MediaSourceId: source && source.Id, PlaySessionId: playSession, PositionTicks: Math.round(v.currentTime * B.TICKS) }) }).catch(() => {});
  };
  window.addEventListener("pagehide", unload);
  const quiet = () => { document.removeEventListener("keydown", keys); clearTimeout(uiTimer); clearTimeout(pausedTimer); };
  const onLeave = () => {
    quiet();
    if (popped()) return keepPlaying();
    finish(); window.removeEventListener("pagehide", unload);
  };
  B.onLeave(onLeave);

  // ---------- popped out: keep playing while you browse (like YouTube) ----------
  // Leaving the player while the video is in the pop-out window keeps this page alive but hidden.
  // Closing the pop-out stops it; "Back to tab" (or the "Back to the video" pill) brings the player back.
  const keepPlaying = () => {
    const tick = setInterval(progress, 10000);
    B.keepPage(myHash, {
      stop: () => {
        clearInterval(tick); finish(); window.removeEventListener("pagehide", unload);
        if (document.pictureInPictureElement === v) document.exitPictureInPicture().catch(() => {});
      },
      back: () => {
        clearInterval(tick);
        myNav = B.nav;
        B.every(10000, progress);
        B.onLeave(onLeave);
        document.addEventListener("keydown", keys);
        poke();
      },
      pause: () => v.pause(),
    });
  };
  v.addEventListener("leavepictureinpicture", () => {
    // The pop-out's X pauses the video first; "Back to tab" keeps it playing
    setTimeout(() => { if (away()) (v.paused ? B.dropKept() : B.restoreKept()); }, 250);
  });

  try { await load(startTicks / B.TICKS); } catch (e) { msg(""); $("#msg").innerHTML = `<div><div class="h1" style="font-size:24px">${esc(e.message)}</div></div>`; }
  poke();
});
