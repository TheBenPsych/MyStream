/* Benflix web: the player. Classic bottom bar cut into chapters, white Skip pill, Up next card,
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
const withKey = (url) => url + (url.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token);
const streamUrl = (url) => "/api/web/stream" + (url.startsWith("/") ? url : "/" + url);

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
  const u = B.uid();
  const it = await B.get(`/Users/${u}/Items/${m[1]}?Fields=${B.FIELDS},Chapters,MediaSources,People`);
  if (!B.alive(nav)) return;
  B.lastPlayed = { id: it.Id, title: it.Type === "Episode" ? `${it.SeriesName} S${it.ParentIndexNumber} E${it.IndexNumber}` : it.Name };
  const settings = B.webSettings();
  const ud = it.UserData || {};
  let startTicks = B.q("resume") === "1" && ud.PlaybackPositionTicks && !ud.Played ? ud.PlaybackPositionTicks : 0;
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
    <div class="topline"><button class="pbtn" id="pBack" title="Back">${I("left")}</button>
      <div><div class="tt">${esc(isEp ? `${it.SeriesName} · S${it.ParentIndexNumber} E${it.IndexNumber} · ${it.Name}` : it.Name)}</div>${it.OfficialRating ? `<div class="rating">Rated ${esc(it.OfficialRating)}</div>` : ""}</div></div>
    <button class="skip hidden" id="pSkip"></button>
    <div class="upnext hidden" id="pNext"></div>
    <div class="eprow hidden" id="pEps"></div>
    <div class="controls">
      <div class="chap" id="pChap"></div>
      <div class="bar" id="pBar"><div class="segs" id="pSegs"></div><span class="knob" id="pKnob"></span><span class="hovert hidden" id="pHover"></span></div>
      <div class="row2">
        <button class="pbtn play" id="pPlay" title="Play / pause (space)">${I("pause")}</button>
        <button class="pbtn" id="pBk" title="Back 10 seconds (left arrow)">${I("back10")}</button>
        <button class="pbtn" id="pFw" title="Forward 10 seconds (right arrow)">${I("fwd10")}</button>
        ${isEp ? `<button class="pbtn" id="pNx" title="Next episode">${I("next")}</button>` : ""}
        <span class="time" id="pTime">0:00</span>
        <span class="grow"></span>
        <span class="time" id="pEnds"></span>
        ${isEp ? `<button class="pbtn" id="pList" title="Episodes">${I("list")}</button>` : ""}
        <button class="pbtn" id="pSub" title="Audio and subtitles">${I("sub")}</button>
        <button class="pbtn" id="pVol" title="Sound on / off (M)">${I("vol")}</button>
        <button class="pbtn" id="pFull" title="Full screen (F)">${I("full")}</button>
      </div>
    </div>
  </div>`;
  const pl = $("#pl"), v = $("#v");
  v.volume = settings.volume != null ? settings.volume : 1;
  let hls = null, info = null, source = null, playSession = null, method = "DirectPlay", stopped = false;
  let lastTick = 0, uiTimer, pausedTimer, nextShown = false, skipped = new Set();

  const msg = (t) => { $("#msg").innerHTML = t ? `<div><div class="spin"></div><div>${esc(t)}</div></div>` : ""; };
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
    if (!B.alive(nav)) return;
    source = (info.MediaSources || [])[0];
    if (!source) throw new Error("Jellyfin couldn't find a way to play this.");
    playSession = info.PlaySessionId;
    if (audioIndex == null) audioIndex = source.DefaultAudioStreamIndex;
    if (subIndex == null) subIndex = source.DefaultSubtitleStreamIndex ?? -1;
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
      t.src = streamUrl(withKey(s.DeliveryUrl.replace(/\/Stream\.\w+/, "/Stream.vtt")));
      t.dataset.index = s.Index;
      v.appendChild(t);
    });
    showSubs();
    v.play().catch(() => { msg(""); B.toast("Press play to start."); });
    report("", {});
  };
  const showSubs = () => { Array.from(v.textTracks).forEach((t, i) => { const tr = $$("track", v)[i]; t.mode = tr && +tr.dataset.index === subIndex ? "showing" : "disabled"; }); };

  // ---------- chapters bar ----------
  const segs = chapters.length > 1 && dur ? chapters.map((c, i) => ({ name: c.Name, start: c.StartPositionTicks / B.TICKS, end: i + 1 < chapters.length ? chapters[i + 1].StartPositionTicks / B.TICKS : dur })) : [{ name: "", start: 0, end: dur || 1 }];
  $("#pSegs").innerHTML = segs.map((s) => `<i style="flex:${Math.max(0.001, s.end - s.start)}"><u></u><b></b></i>`).join("");
  const total = () => v.duration && isFinite(v.duration) ? Math.max(v.duration, dur) : dur || 1;
  const drawBar = () => {
    const t = v.currentTime, T = total();
    let buf = 0; try { buf = v.buffered.length ? v.buffered.end(v.buffered.length - 1) : 0; } catch (e) {}
    $$("#pSegs i").forEach((el, i) => {
      const s = segs[i], w = s.end - s.start || 1;
      el.querySelector("b").style.width = Math.max(0, Math.min(100, ((t - s.start) / w) * 100)) + "%";
      el.querySelector("u").style.width = Math.max(0, Math.min(100, ((buf - s.start) / w) * 100)) + "%";
    });
    $("#pKnob").style.left = (t / T) * 100 + "%";
    $("#pTime").textContent = `${B.hms(t)} / ${B.hms(T)}`;
    $("#pEnds").textContent = `Ends at ${B.clock(new Date(Date.now() + (T - t) * 1000))}`;
    const ch = segs.find((s) => t >= s.start && t < s.end);
    $("#pChap").textContent = ch ? ch.name || "" : "";
  };
  const barTime = (e) => { const r = $("#pBar").getBoundingClientRect(); return Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)) * total(); };
  $("#pBar").onclick = (e) => { v.currentTime = barTime(e); poke(); };
  $("#pBar").onmousemove = (e) => {
    const t = barTime(e), h = $("#pHover"), ch = segs.find((s) => t >= s.start && t < s.end);
    h.classList.remove("hidden"); h.textContent = B.hms(t) + (ch && ch.name ? " · " + ch.name : "");
    h.style.left = (t / total()) * 100 + "%";
  };
  $("#pBar").onmouseleave = () => $("#pHover").classList.add("hidden");

  // ---------- controls ----------
  const poke = () => {
    pl.classList.add("ui");
    clearTimeout(uiTimer);
    uiTimer = setTimeout(() => { if (!v.paused && !$("#pEps:not(.hidden)") && !$(".player .panel")) pl.classList.remove("ui"); }, 5000);
  };
  pl.addEventListener("mousemove", poke);
  pl.addEventListener("click", (e) => { if (e.target === v || e.target.classList.contains("center-msg")) { v.paused ? v.play() : v.pause(); } poke(); });
  v.addEventListener("dblclick", () => full());
  const full = () => (document.fullscreenElement ? document.exitFullscreen() : pl.requestFullscreen().catch(() => {}));
  $("#pFull").onclick = full;
  $("#pPlay").onclick = () => (v.paused ? v.play() : v.pause());
  $("#pBk").onclick = () => { v.currentTime = Math.max(0, v.currentTime - 10); poke(); };
  $("#pFw").onclick = () => { v.currentTime = Math.min(total(), v.currentTime + 10); poke(); };
  $("#pVol").onclick = () => { v.muted = !v.muted; $("#pVol").innerHTML = I(v.muted ? "mute" : "vol"); };
  $("#pBack").onclick = () => leave();
  $("#pSub").onclick = () => openPanel();
  v.addEventListener("play", () => { $("#pPlay").innerHTML = I("pause"); pl.classList.remove("paused-long"); clearTimeout(pausedTimer); poke(); report("/Progress"); });
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
  B.get(`/MediaSegments/${it.Id}`).then((r) => { segments = (r.Items || []).map((s) => ({ type: s.Type, start: s.StartTicks / B.TICKS, end: s.EndTicks / B.TICKS })); }).catch(() => {});
  const words = { Intro: "Skip intro", Recap: "Skip recap", Outro: "Skip credits", Preview: "Skip preview", Commercial: "Skip" };
  let nextItem;
  const findNext = async () => {
    if (nextItem !== undefined) return nextItem;
    nextItem = null;
    try {
      if (isEp) {
        const r = await B.get(`/Shows/${it.SeriesId}/Episodes?userId=${u}&StartItemId=${it.Id}&Limit=2&Fields=Overview`);
        nextItem = (r.Items || [])[1] || null;
      } else if (!B.q("trailer")) {
        const r = await B.get(`/Items/${it.Id}/Similar?userId=${u}&Limit=12`);
        nextItem = (r.Items || []).find((x) => !(x.UserData || {}).Played) || null;
      }
    } catch (e) {}
    return nextItem;
  };
  const autoNext = settings.autoplay !== false && (isEp || autoplayCount < 3);
  const showUpNext = async () => {
    nextShown = true;
    const n = await findNext();
    if (!n || !B.alive(nav)) return;
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
  const goNext = (n) => { finish(); B.go(`#/play/${n.Id}`); };
  $("#pNx") && ($("#pNx").onclick = async () => { const n = await findNext(); if (n) goNext(n); else B.toast("That was the last episode."); });

  v.addEventListener("timeupdate", () => {
    const t = v.currentTime;
    drawBar();
    const now = performance.now();
    if (!v.paused && lastTick) WT.add(Math.min(5000, now - lastTick));
    lastTick = now;
    const seg = segments.find((s) => t >= s.start && t < s.end - 1 && words[s.type]);
    const sk = $("#pSkip");
    if (seg) {
      const auto = (seg.type === "Intro" || seg.type === "Recap") && series && alwaysSkip.intro.has(plain(series)) || seg.type === "Outro" && series && alwaysSkip.outro.has(plain(series));
      if ((auto || settings.autoskip) && !skipped.has(seg)) { skipped.add(seg); v.currentTime = seg.end; B.toast(words[seg.type].replace("Skip", "Skipped")); return; }
      sk.textContent = words[seg.type]; sk.classList.remove("hidden"); sk.onclick = () => { skipped.add(seg); v.currentTime = seg.end; sk.classList.add("hidden"); };
    } else sk.classList.add("hidden");
    const outro = segments.find((s) => s.type === "Outro");
    const T = total();
    if (!nextShown && T > 60 && ((outro && t >= outro.start) || (!outro && T - t < (isEp ? 25 : 60)))) showUpNext();
  });
  v.addEventListener("ended", async () => { const n = await findNext(); if (n && autoNext) goNext(n); else leave(); });
  B.every(10000, () => { if (!v.paused) report("/Progress"); });

  // ---------- audio + subtitles panel ----------
  const openPanel = () => {
    if ($(".player .panel")) return $(".player .panel").remove();
    const streams = (source && source.MediaStreams) || [];
    const audio = streams.filter((s) => s.Type === "Audio"), subs = streams.filter((s) => s.Type === "Subtitle");
    const p = document.createElement("div");
    p.className = "panel";
    p.innerHTML = `<button class="circle" style="float:right" data-x>${I("x")}</button><h3 style="margin:4px 0 0">Audio and subtitles</h3>
      <h4>Audio</h4>${audio.map((s) => `<button class="opt ${s.Index === audioIndex ? "on" : ""}" data-a="${s.Index}">${esc(s.DisplayTitle || s.Language || "Track")}</button>`).join("") || '<p class="dim">One sound track.</p>'}
      <h4>Subtitles</h4><button class="opt ${subIndex === -1 ? "on" : ""}" data-s="-1">Off</button>${subs.map((s) => `<button class="opt ${s.Index === subIndex ? "on" : ""}" data-s="${s.Index}">${esc(s.DisplayTitle || s.Language || "Subtitles")}</button>`).join("")}
      <h4>Quality</h4>${Object.keys(QUALITY).map((k) => `<button class="opt ${(settings.quality || "auto") === k ? "on" : ""}" data-q="${k}">${k === "auto" ? "Best (no limit)" : k + "p"}</button>`).join("")}
      ${series ? `<h4>This show</h4><button class="opt ${alwaysSkip.intro.has(plain(series)) ? "on" : ""}" data-skip="intro">Always skip the intro</button><button class="opt ${alwaysSkip.outro.has(plain(series)) ? "on" : ""}" data-skip="outro">Always skip the credits</button>` : ""}
      <h4>Something wrong?</h4><button class="opt" data-report>${I("flag")} Report a problem</button>
      ${B.me.admin ? `<p class="dim" style="font-size:13px;margin-top:16px">${esc(method === "DirectPlay" ? "Playing the file as it is" : method === "DirectStream" ? "Repackaged on the fly (light work for the server)" : "Converted on the fly (the server is working)")}</p>` : ""}`;
    pl.appendChild(p);
    pl.classList.add("ui");
    p.onclick = async (e) => {
      if (e.target.closest("[data-x]")) return p.remove();
      const a = e.target.closest("[data-a]"), s = e.target.closest("[data-s]"), qq = e.target.closest("[data-q]"), sk = e.target.closest("[data-skip]");
      if (e.target.closest("[data-report]")) { p.remove(); v.pause(); return B.report(B.lastPlayed); }
      if (a) { audioIndex = +a.dataset.a; p.remove(); return load(v.currentTime).catch((err) => msg(err.message)); }
      if (s) {
        const idx = +s.dataset.s, st = subs.find((x) => x.Index === idx);
        subIndex = idx; p.remove();
        if (idx === -1 || (st && st.DeliveryMethod === "External")) return showSubs();
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
    if (k === " " || k === "k") { e.preventDefault(); v.paused ? v.play() : v.pause(); }
    else if (k === "ArrowLeft") { v.currentTime = Math.max(0, v.currentTime - 10); }
    else if (k === "ArrowRight") { v.currentTime = Math.min(total(), v.currentTime + 10); }
    else if (k === "ArrowUp") { e.preventDefault(); v.volume = Math.min(1, v.volume + 0.1); B.setWebSetting("volume", v.volume); }
    else if (k === "ArrowDown") { e.preventDefault(); v.volume = Math.max(0, v.volume - 0.1); B.setWebSetting("volume", v.volume); }
    else if (k === "f") full();
    else if (k === "m") $("#pVol").click();
    else if (k === "s") { const sk = $("#pSkip:not(.hidden)"); if (sk) sk.click(); }
    else if (k === "Escape" && !document.fullscreenElement) leave();
    else return;
    poke();
  };
  document.addEventListener("keydown", keys);

  // ---------- stopping ----------
  const finish = () => {
    if (stopped) return;
    stopped = true;
    const pos = Math.round(v.currentTime * B.TICKS);
    report("/Stopped", { PositionTicks: pos });
    if (method !== "DirectPlay" && B.me) B.jf("DELETE", `/Videos/ActiveEncodings?deviceId=${enc("benflix-web-" + B.me.device)}&playSessionId=${enc(playSession || "")}`).catch(() => {});
    WT.send();
    if (hls) hls.destroy();
    v.removeAttribute("src"); try { v.load(); } catch (e) {}
  };
  const leave = () => { finish(); if (document.fullscreenElement) document.exitFullscreen().catch(() => {}); B.back(); };
  const unload = () => {
    if (stopped || !B.me) return;
    fetch("/api/web/jf/Sessions/Playing/Stopped", { method: "POST", keepalive: true, headers: { "Content-Type": "application/json", "X-Benflix-Token": B.me.token, "X-Benflix-Device": B.me.device || "" },
      body: JSON.stringify({ ItemId: it.Id, MediaSourceId: source && source.Id, PlaySessionId: playSession, PositionTicks: Math.round(v.currentTime * B.TICKS) }) }).catch(() => {});
  };
  window.addEventListener("pagehide", unload);
  B.onLeave(() => { finish(); document.removeEventListener("keydown", keys); window.removeEventListener("pagehide", unload); clearTimeout(uiTimer); clearTimeout(pausedTimer); });

  try { await load(startTicks / B.TICKS); } catch (e) { msg(""); $("#msg").innerHTML = `<div><div class="h1" style="font-size:24px">${esc(e.message)}</div></div>`; }
  poke();
});
