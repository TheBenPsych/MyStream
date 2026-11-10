/* Benflix web: Music, the violet fifth door. The music page (Made for you, Your playlists, Recently added,
   Most played, Artists, Browse by), album / playlist / mix pages, artist pages, a player bar along the bottom of
   every page, and Now playing (cover, controls, lyrics and logo-square towers). Music keeps going while you
   browse; starting a movie or show stops it. Keys: Space pauses, Left / Right change song (on Now playing),
   L likes the song. */

(() => {
  const MUSIC = "#B69CFF", ON_MUSIC = "#160F26";
  B.DOORS.music = { key: "music", name: "Music", desc: "Your songs and playlists", color: MUSIC, icon: "note" };
  const I = B.I;
  const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const enc = encodeURIComponent;
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const SONG_FIELDS = "Fields=ParentId,DateCreated,RunTimeTicks,ChildCount&EnableUserData=true";
  const ICON = {
    note: '<path d="M9 18V5l11-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="17" cy="16" r="3"/>',
    shuffle: '<path d="M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/>',
    prev: '<path d="M19 4.5v15L9 12l10-7.5Z" fill="currentColor"/><path d="M5 5v14" stroke-width="2.6"/>',
    queue: '<path d="M3 6h13M3 12h13M3 18h8M18 14v7M21 17l-3 4-3-4" />',
    stop: '<rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor"/>',
  };
  const icon = (name, cls) => (ICON[name] ? `<svg class="i ${cls || ""}" viewBox="0 0 24 24" aria-hidden="true">${ICON[name]}</svg>` : I(name, cls));
  B.musicIcon = icon;
  const len = (ticks) => B.hms((ticks || 0) / B.TICKS);
  const items = async (q) => ((await B.get(`/Users/${B.uid()}/Items?Recursive=true&${q}`)).Items || []);
  const cover = (it, h) => {
    if (!it) return "";
    const tags = it.ImageTags || {};
    if (tags.Primary) return B.img(it.Id, "Primary", { h: h || 360, tag: tags.Primary });
    if (it.AlbumId) return B.img(it.AlbumId, "Primary", { h: h || 360 });
    return "";
  };
  const artistOf = (s) => (s.AlbumArtist || (s.Artists || []).join(", ") || "");

  // ---------- the player (one <audio> for the whole visit) ----------
  const M = (B.music = { queue: [], index: -1, added: new Set(), thenTitle: "", liked: new Set() });
  const audio = new Audio();
  audio.preload = "auto";
  M.audio = audio;
  let session = "";
  // When the server converts a song on the fly the browser can't tell how long it is (duration = Infinity)
  // and can't jump inside it, so the length comes from Jellyfin and a jump restarts the stream at that point.
  let offset = 0;
  const live = () => !(isFinite(audio.duration) && audio.duration > 0);
  const pos = () => offset + (audio.currentTime || 0);
  const songLen = () => {
    const s = M.queue[M.index];
    if (!live()) return audio.duration + offset;
    return s && s.RunTimeTicks ? s.RunTimeTicks / B.TICKS : 0;
  };
  M.pos = pos;
  M.len = songLen;
  const report = (what, extra) => {
    const s = M.queue[M.index];
    if (!s || !B.me) return;
    B.jf("POST", "/Sessions/Playing" + what, Object.assign({ ItemId: s.Id, PlaySessionId: session, PositionTicks: Math.round(pos() * B.TICKS), IsPaused: audio.paused, PlayMethod: "Transcode" }, extra || {})).catch(() => {});
  };
  const src = (s) => `api/web/stream/Audio/${enc(s.Id)}/universal?UserId=${B.uid()}&DeviceId=${enc("benflix-web-" + (B.me.device || ""))}&Container=mp3,aac,m4a|aac,m4b|aac,flac,webma,webm|webma,wav,ogg&TranscodingContainer=mp3&TranscodingProtocol=http&AudioCodec=mp3&MaxStreamingBitrate=320000&api_key=${enc(B.me.token)}`;
  const seek = (t) => {
    const s = M.queue[M.index];
    if (!s) return;
    const d = songLen();
    t = Math.max(0, d ? Math.min(t, d - 1) : t);
    if (!live() && offset === 0) { audio.currentTime = t; return; }
    if (!live() && t >= offset) { audio.currentTime = t - offset; return; }
    const playing = !audio.paused;
    offset = t;
    audio.src = src(s) + "&StartTimeTicks=" + Math.round(t * B.TICKS);
    if (playing) audio.play().catch(() => {});
    report("/Progress");
  };
  M.seek = seek;
  // If the server sent the whole file after a jump (it ignores the start point), move to the right spot ourselves
  audio.addEventListener("loadedmetadata", () => {
    const s = M.queue[M.index];
    const full = s && s.RunTimeTicks ? s.RunTimeTicks / B.TICKS : 0;
    if (offset > 0 && !live() && full && audio.duration > full - offset + 5) { const t = offset; offset = 0; audio.currentTime = t; }
  });
  const load = (i, play) => {
    if (!B.me) return;
    if (M.index >= 0 && M.queue[M.index] && !audio.paused) report("/Stopped");
    M.index = i;
    const s = M.queue[i];
    if (!s) return;
    session = Math.random().toString(36).slice(2);
    offset = 0;
    audio.src = src(s);
    if (play !== false) audio.play().catch(() => {});
    report("", {});
    M.lyrics = null;
    loadLyrics(s);
    checkLiked(s);
    if ("mediaSession" in navigator) {
      const art = cover(s, 512);
      navigator.mediaSession.metadata = new MediaMetadata({ title: s.Name || "", artist: artistOf(s), album: s.Album || "", artwork: art ? [{ src: art, sizes: "512x512" }] : [] });
    }
    draw();
  };
  // Bumped by Stop and by starting a new list, so a mix that arrives late can't restart or change it
  let gen = 0;
  M.playList = (songs, start, shuffle, title) => {
    gen++;
    songs = (songs || []).filter((s) => s.Type === "Audio");
    if (!songs.length) return;
    start = Math.max(0, Math.min(start || 0, songs.length - 1));
    let list = songs.slice(start);
    if (shuffle) {
      const first = start > 0 ? songs[start] : songs[Math.floor(Math.random() * songs.length)];
      const rest = songs.filter((s) => s !== first).sort(() => Math.random() - 0.5);
      list = [first].concat(rest);
    }
    // Songs you added to Up next yourself stay, right after the first song (a new album only replaces "Then plays")
    const keep = M.queue.slice(M.index + 1).filter((s) => M.added.has(s._key));
    list.forEach((s, i) => { list[i] = Object.assign({}, s, { _key: Math.random() }); });
    M.queue = [list[0]].concat(keep, list.slice(1));
    M.thenTitle = title || "";
    M.sourceId = null;
    load(0);
  };
  M.addToUpNext = (s) => {
    const song = Object.assign({}, s, { _key: Math.random() });
    M.added.add(song._key);
    if (M.index < 0) { M.queue = [song]; return load(0); }
    let at = M.index + 1;
    while (at < M.queue.length && M.added.has(M.queue[at]._key)) at++;
    M.queue.splice(at, 0, song);
    draw();
    B.toast(`Added to Up next: ${s.Name}`);
  };
  M.playNext = (s) => {
    const song = Object.assign({}, s, { _key: Math.random() });
    M.added.add(song._key);
    if (M.index < 0) { M.queue = [song]; return load(0); }
    M.queue.splice(M.index + 1, 0, song);
    draw();
    B.toast(`Plays next: ${s.Name}`);
  };
  M.next = () => { if (M.index + 1 < M.queue.length) load(M.index + 1); else keepGoing(true); };
  M.prev = () => { if (pos() > 4 || M.index === 0) seek(0); else load(M.index - 1); };
  M.toggle = () => { if (!M.queue.length) return; wakeSound(); if (audio.paused) audio.play().catch(() => {}); else audio.pause(); };
  M.stop = () => {
    if (!M.queue.length) return;
    report("/Stopped");
    audio.pause();
    audio.removeAttribute("src");
    try { audio.load(); } catch (e) {}
    gen++;
    M.queue = []; M.index = -1; M.added.clear();
    closeNow();
    draw();
  };
  M.current = () => M.queue[M.index];
  // When the list runs out, a mix like the last song keeps going (Ben: "plays to the end, then a matching mix")
  let extending = false;
  const keepGoing = async (startNow) => {
    if (extending) return;
    const last = M.queue[M.queue.length - 1];
    if (!last) return;
    extending = true;
    const mine = gen;
    try {
      const have = new Set(M.queue.map((s) => s.Id));
      const mix = ((await B.get(`/Items/${enc(M.sourceId || last.Id)}/InstantMix?UserId=${B.uid()}&Limit=60&${SONG_FIELDS}`)).Items || []).filter((s) => !have.has(s.Id) && s.Type === "Audio").slice(0, 40);
      if (mix.length && mine === gen) {
        M.queue = M.queue.concat(mix.map((s) => Object.assign({}, s, { _key: Math.random() })));
        M.thenTitle = `More like ${last.Name}`;
        if (startNow) load(M.index + 1);
        draw();
      }
    } catch (e) {}
    extending = false;
  };
  audio.addEventListener("ended", () => {
    const s = M.current();
    if (s && B.me) B.jf("POST", `/Users/${B.uid()}/PlayedItems/${enc(s.Id)}`).catch(() => {});
    report("/Stopped");
    M.next();
  });
  audio.addEventListener("play", draw);
  audio.addEventListener("pause", draw);
  setInterval(() => { if (!audio.paused && M.current()) report("/Progress"); }, 10000);
  window.addEventListener("pagehide", () => { if (M.current() && !audio.paused) report("/Stopped"); });
  if ("mediaSession" in navigator) {
    navigator.mediaSession.setActionHandler("previoustrack", () => M.prev());
    navigator.mediaSession.setActionHandler("nexttrack", () => M.next());
    navigator.mediaSession.setActionHandler("play", () => audio.play());
    navigator.mediaSession.setActionHandler("pause", () => audio.pause());
  }

  // ---------- likes ----------
  const checkLiked = async (s) => {
    try {
      const it = await B.get(`/Users/${B.uid()}/Items/${enc(s.Id)}`);
      if (it.UserData && it.UserData.IsFavorite) M.liked.add(s.Id); else M.liked.delete(s.Id);
      draw();
    } catch (e) {}
  };
  M.like = async (id, on) => {
    id = id || (M.current() && M.current().Id);
    if (!id) return;
    if (on == null) on = !M.liked.has(id);
    try {
      await B.jf(on ? "POST" : "DELETE", `/Users/${B.uid()}/FavoriteItems/${enc(id)}`);
      if (on) M.liked.add(id); else M.liked.delete(id);
      B.toast(on ? "Added to Liked songs" : "Taken out of Liked songs");
      draw();
      $$(`[data-heart="${id}"]`).forEach((h) => h.classList.toggle("on", on));
    } catch (e) { B.toast(e.message); }
  };

  // ---------- lyrics ----------
  const loadLyrics = async (s) => {
    try {
      const j = await B.get(`/Audio/${enc(s.Id)}/Lyrics`);
      if (M.current() !== s) return;
      const lines = (j.Lyrics || []).filter((l) => (l.Text || "").trim());
      M.lyrics = lines.length ? { lines, timed: lines.some((l) => l.Start != null && l.Start > 0) } : null;
    } catch (e) { M.lyrics = null; }
    drawNow(true);
  };
  const lyricIndex = () => {
    const ly = M.lyrics;
    if (!ly) return -1;
    const t = pos() * B.TICKS;
    if (ly.timed) { let i = -1; ly.lines.forEach((l, n) => { if ((l.Start || 0) <= t) i = n; }); return Math.max(0, i); }
    // Untimed words scroll slowly over the length of the song
    const d = songLen() || 1;
    return Math.min(ly.lines.length - 1, Math.floor((pos() / d) * ly.lines.length));
  };

  // ---------- the player bar (every page) ----------
  let bar;
  function draw() {
    const s = M.current();
    if (!bar) {
      bar = document.createElement("div");
      bar.className = "mbar hidden";
      bar.innerHTML = `<button class="mbar-open" data-open title="Now playing"><span class="mbar-cover"></span><span class="mbar-words"><b></b><small></small></span></button>
        <div class="mbar-ctl"><button class="circle" data-a="prev" title="Previous song">${icon("prev")}</button><button class="circle white" data-a="toggle" title="Play or pause (Space)"></button><button class="circle" data-a="next" title="Next song">${I("next")}</button>
        <button class="circle mheart" data-a="like" title="Like (L)">${I("heart")}</button></div>
        <div class="mbar-line"><i></i></div>`;
      bar.addEventListener("click", (e) => {
        const a = e.target.closest("[data-a]");
        if (a) { e.stopPropagation(); return act(a.dataset.a); }
        if (e.target.closest("[data-open]")) openNow();
      });
      document.body.appendChild(bar);
    }
    const on = !!s;
    bar.classList.toggle("hidden", !on);
    document.body.classList.toggle("has-mbar", on);
    if (!on) return drawNow();
    const art = cover(s, 120);
    $(".mbar-cover", bar).style.backgroundImage = art ? `url('${art}')` : "";
    $(".mbar-words b", bar).textContent = s.Name || "";
    $(".mbar-words small", bar).textContent = artistOf(s);
    $("[data-a=toggle]", bar).innerHTML = audio.paused ? I("play") : I("pause");
    $(".mheart", bar).classList.toggle("on", M.liked.has(s.Id));
    drawNow();
  }
  M.draw = draw;
  const act = (a) => {
    if (a === "prev") M.prev();
    else if (a === "next") M.next();
    else if (a === "toggle") M.toggle();
    else if (a === "like") M.like();
    else if (a === "stop") M.stop();
    else if (a === "queue") { closeNow(); B.go("#/upnext"); }
  };
  setInterval(() => {
    if (!bar || !M.current()) return;
    const d = songLen();
    $(".mbar-line i", bar).style.width = d ? Math.min(100, 100 * pos() / d) + "%" : "0";
    drawNow(false, true);
  }, 500);

  // ---------- Now playing (approved np-cover + w-column) ----------
  let np, raf;
  function openNow() {
    if (!M.current()) return;
    if (!np) {
      np = document.createElement("div");
      np.className = "np";
      np.innerHTML = `<canvas class="np-towers"></canvas><div class="np-fade"></div>
        <button class="circle np-x" data-x title="Close (Esc)">${I("down")}</button>
        <div class="np-main"><div class="np-cover"></div>
          <div class="np-mid"><div class="np-kick">NOW PLAYING</div><div class="np-t"></div><div class="np-a"></div>
            <div class="np-prog"><span class="np-now">0:00</span><div class="np-track"><i></i></div><span class="np-len">0:00</span></div>
            <div class="np-ctl"><button class="circle" data-a="prev" title="Previous song (Left)">${icon("prev")}</button><button class="circle white big" data-a="toggle" title="Play or pause (Space)"></button><button class="circle" data-a="next" title="Next song (Right)">${I("next")}</button></div>
            <div class="np-ctl2"><button class="btn mheart" data-a="like">${I("heart")} Like</button><button class="btn" data-a="queue">${icon("queue")} Up next</button><button class="btn" data-a="stop">${icon("stop")} Stop</button><button class="btn" data-a="more" title="More">${I("more")} More</button></div>
            <div class="np-next"></div>
          </div>
          <div class="np-words"></div>
        </div>`;
      np.addEventListener("click", (e) => {
        const a = e.target.closest("[data-a]");
        if (a && a.dataset.a === "more") return M.current() && songMenu(a, M.current());
        if (a) return act(a.dataset.a);
        if (e.target.closest("[data-x]")) return closeNow();
        const tr = e.target.closest(".np-track");
        if (tr && songLen()) { const r = tr.getBoundingClientRect(); seek(songLen() * Math.max(0, Math.min(1, (e.clientX - r.left) / r.width))); }
        const nx = e.target.closest("[data-upnext]");
        if (nx) { closeNow(); B.go("#/upnext"); }
      });
      document.body.appendChild(np);
    }
    np.classList.add("on");
    document.body.classList.add("np-open");
    drawNow(true);
    startTowers();
  }
  function closeNow() {
    if (!np) return;
    np.classList.remove("on");
    document.body.classList.remove("np-open");
    cancelAnimationFrame(raf);
  }
  M.openNow = openNow;
  let lastLyric = -2;
  function drawNow(full, tick) {
    if (!np || !np.classList.contains("on")) return;
    const s = M.current();
    if (!s) return closeNow();
    if (!tick || full) {
      const art = cover(s, 700);
      $(".np-cover", np).style.backgroundImage = art ? `url('${art}')` : "";
      $(".np-t", np).textContent = s.Name || "";
      $(".np-a", np).textContent = [artistOf(s), s.Album].filter(Boolean).join(" · ");
      $("[data-a=toggle]", np).innerHTML = audio.paused ? I("play") : I("pause");
      $(".np-ctl2 .mheart", np).classList.toggle("on", M.liked.has(s.Id));
      const n = M.queue[M.index + 1];
      $(".np-next", np).innerHTML = n
        ? `<button class="np-up" data-upnext><span class="np-upcover" style="${cover(n, 80) ? `background-image:url('${cover(n, 80)}')` : ""}"></span><span><small>UP NEXT</small>${esc([n.Name, artistOf(n)].filter(Boolean).join(" · "))}</span></button>`
        : `<div class="dim">Up next: a mix like this song</div>`;
      np.classList.toggle("nowords", !M.lyrics);
      lastLyric = -2;
    }
    const d = songLen();
    $(".np-now", np).textContent = B.hms(pos());
    $(".np-len", np).textContent = d ? B.hms(d) : "";
    $(".np-track i", np).style.width = d ? Math.min(100, 100 * pos() / d) + "%" : "0";
    if (M.lyrics) {
      const i = lyricIndex();
      if (i !== lastLyric || full) {
        lastLyric = i;
        const from = Math.max(0, i - 2);
        $(".np-words", np).innerHTML = M.lyrics.lines.slice(from, from + 5).map((l, k) => `<p class="${from + k === i ? "on" : ""}">${esc(l.Text)}</p>`).join("");
      }
    } else $(".np-words", np).innerHTML = "";
  }

  // The towers: the same soft squares as the logo, in the door colours from low (amber) to high (violet)
  let ctx, analyser, data;
  // Browsers pause the sound graph when the tab sleeps; wake it or the music goes silent
  function wakeSound() { try { if (ctx && ctx.state === "suspended") ctx.resume().catch(() => {}); } catch (e) {} }
  audio.addEventListener("play", wakeSound);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) wakeSound(); });
  const COLORS = ["#FFB547", "#7AA7FF", "#3DD6C6", "#FF6F91", MUSIC];
  function startTowers() {
    if (B.webSettings().musicBarsOff) return;
    try {
      if (!ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        ctx = new AC();
        const node = ctx.createMediaElementSource(audio);
        analyser = ctx.createAnalyser();
        analyser.fftSize = 1024;
        analyser.smoothingTimeConstant = 0.7;
        node.connect(analyser);
        analyser.connect(ctx.destination);
        data = new Uint8Array(analyser.frequencyBinCount);
      }
      if (ctx.state === "suspended") ctx.resume();
    } catch (e) { analyser = null; }
    const c = $(".np-towers", np);
    const shown = [], peaks = [];
    const frame = () => {
      if (!np.classList.contains("on")) return;
      raf = requestAnimationFrame(frame);
      const w = (c.width = c.clientWidth * devicePixelRatio), h = (c.height = c.clientHeight * devicePixelRatio);
      const g = c.getContext("2d");
      const n = w > 1400 ? 30 : 16;
      const gap = w / n * 0.22, bw = (w - gap * (n - 1)) / n, gy = bw * 0.18;
      const rows = Math.max(1, Math.floor((h + gy) / (bw + gy)));
      if (analyser) analyser.getByteFrequencyData(data);
      const t = performance.now() / 1000;
      for (let i = 0; i < n; i++) {
        let target = 0;
        if (!audio.paused) {
          if (analyser) {
            // Log-spaced bands from about 40 Hz to 16 kHz
            const nyq = (ctx.sampleRate || 44100) / 2, bins = data.length;
            const f0 = 40 * Math.pow(16000 / 40, i / n), f1 = 40 * Math.pow(16000 / 40, (i + 1) / n);
            const b0 = Math.floor(f0 / nyq * bins), b1 = Math.max(b0 + 1, Math.floor(f1 / nyq * bins));
            let sum = 0; for (let b = b0; b < b1 && b < bins; b++) sum += data[b];
            target = Math.min(1, (sum / (b1 - b0) / 255) * 1.15);
          } else {
            const f = i / n;
            target = Math.max(0, Math.min(1, 0.55 - 0.35 * f + 0.18 * Math.sin(t * (2.1 + f * 3) + i * 1.7)));
          }
        }
        shown[i] = shown[i] || 0; peaks[i] = peaks[i] || 0;
        shown[i] = target > shown[i] ? shown[i] + (target - shown[i]) * 0.6 : Math.max(target, shown[i] - 0.03);
        peaks[i] = shown[i] >= peaks[i] ? shown[i] : Math.max(0, peaks[i] - 0.008);
        const x = i * (bw + gap), col = COLORS[Math.min(COLORS.length - 1, Math.floor(i * COLORS.length / n))];
        const lit = Math.min(rows, Math.max(shown[i] > 0.02 ? 1 : 0, Math.floor(shown[i] * rows)));
        g.fillStyle = col;
        for (let r = 0; r < lit; r++) rr(g, x, h - (r + 1) * bw - r * gy, bw, bw, bw * 0.32);
        const pr = Math.floor(peaks[i] * rows);
        if (pr >= lit && pr >= 1 && pr < rows) { g.globalAlpha = 0.35; rr(g, x, h - (pr + 1) * bw - pr * gy, bw, bw, bw * 0.32); g.globalAlpha = 1; }
      }
    };
    frame();
  }
  const rr = (g, x, y, w, h, r) => { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.fill(); };

  // ---------- keys ----------
  document.addEventListener("keydown", (e) => {
    if (!M.current() || e.ctrlKey || e.metaKey || e.altKey) return;
    if (/^#\/(play|watch|playgame)\//.test(location.hash)) return;
    const t = e.target;
    if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable)) return;
    const open = np && np.classList.contains("on");
    if (e.key === " " && (open || t === document.body)) { e.preventDefault(); M.toggle(); }
    else if (e.key === "l" || e.key === "L") { e.preventDefault(); M.like(); }
    else if (open && e.key === "ArrowLeft") { e.preventDefault(); if (e.shiftKey) seek(pos() - 10); else M.prev(); }
    else if (open && e.key === "ArrowRight") { e.preventDefault(); if (e.shiftKey) seek(pos() + 10); else M.next(); }
    else if (open && e.key === "Escape") { e.preventDefault(); closeNow(); }
  });

  // ---------- pieces for the music pages ----------
  const card = (it, href, sub) => {
    const art = cover(it, 360);
    return `<a class="mcard" href="${href}" title="${esc(it.Name)}"><div class="im">${art ? `<img class="art" src="${art}" alt="" loading="lazy" decoding="async">` : icon("note")}</div><div class="cap">${esc(it.Name)}<small>${esc(sub || "")}</small></div></a>`;
  };
  const albumCard = (a) => card(a, `#/album/${a.Id}`, a.AlbumArtist || a.ProductionYear || "");
  const artistCard = (a) => card(a, `#/artist/${a.Id}`, "Artist").replace('class="mcard"', 'class="mcard round"');
  const playlistCard = (p) => card(p, `#/playlist/${p.Id}`, p.ChildCount ? `${p.ChildCount} songs` : "Playlist");
  const mixCard = (m) => {
    const covers = (m.covers || []).slice(0, 4);
    while (covers.length && covers.length < 4) covers.push(covers[covers.length % Math.max(1, covers.length)]);
    return `<a class="mcard mix" href="#/mix/${enc(m.key)}"><div class="im collage">${covers.map((c) => `<span style="background-image:url('${B.img(c, "Primary", { h: 200 })}')"></span>`).join("")}<b>${esc(m.name)}</b></div><div class="cap">${esc(m.name)}<small>${esc(m.line || "")}</small></div></a>`;
  };
  const likedCard = (n) => `<a class="mcard" href="#/liked"><div class="im liked">${I("heart")}</div><div class="cap">Liked songs<small>${n} songs</small></div></a>`;
  B.musicMixes = () => B.cached("music-mixes-" + B.me.id + new Date().toDateString(), 3600000, async () => ((await B.call("GET", "api/music/mixes")) || {}).mixes || []);
  const libs = () => B.cached("music-libs", 600000, async () => (await B.views()).filter((l) => l.CollectionType === "music"));
  const songRow = (s, i, opts) => {
    opts = opts || {};
    const now = M.current() && M.current().Id === s.Id;
    const fav = (s.UserData || {}).IsFavorite || M.liked.has(s.Id);
    return `<div class="srow ${now ? "now" : ""}" data-i="${i}">
      <span class="sn">${now ? '<span class="bars"><i></i><i></i><i></i></span>' : opts.cover ? (cover(s, 80) ? `<img class="sc" src="${cover(s, 80)}" alt="" loading="lazy" decoding="async">` : `<span class="sc"></span>`) : esc(opts.number != null ? opts.number : i + 1)}</span>
      <span class="st"><b>${esc(s.Name)}</b>${opts.artist ? `<small>${esc(artistOf(s))}</small>` : ""}</span>
      <button class="sh ${fav ? "on" : ""}" data-heart="${esc(s.Id)}" title="Like">${I("heart")}</button>
      <span class="sl">${len(s.RunTimeTicks)}</span>
      <button class="sm" data-more="${i}" title="More">${I("more")}</button>
    </div>`;
  };
  const songMenu = (anchor, s, extra) => {
    const opts = [{ label: "Play next", value: "next" }, { label: "Add to Up next", value: "add" }, { label: "Add to a playlist", value: "pl" }, { label: M.liked.has(s.Id) || (s.UserData || {}).IsFavorite ? "Unlike" : "Like", value: "like" }];
    if (s.AlbumArtists && s.AlbumArtists[0]) opts.push({ label: "Go to artist", value: "artist" });
    if (s.AlbumId) opts.push({ label: "Go to album", value: "album" });
    (extra || []).forEach((o) => opts.push(o));
    // Ben only: a bad copy (wrong song, cut off, bad sound) is deleted on Tower and a new copy is found
    if (B.me.admin) opts.push({ label: "Delete and find again", value: "redo" });
    B.dropdown(anchor, opts, null, async (v) => {
      if (v === "next") M.playNext(s);
      else if (v === "add") M.addToUpNext(s);
      else if (v === "like") M.like(s.Id, !(M.liked.has(s.Id) || (s.UserData || {}).IsFavorite)).then(() => { s.UserData = Object.assign(s.UserData || {}, { IsFavorite: M.liked.has(s.Id) }); });
      else if (v === "artist") B.go(`#/artist/${s.AlbumArtists[0].Id}`);
      else if (v === "album") B.go(`#/album/${s.AlbumId}`);
      else if (v === "pl") addToPlaylist(anchor, [s]);
      else if (v === "redo") redoSong(s);
      else (extra || []).forEach((o) => { if (o.value === v && o.run) o.run(); });
    });
  };
  const redoSong = async (s) => {
    const yes = await B.ask("Delete and find again?", `This deletes "${s.Name}" from Tower and finds a new copy. The bad copy won't be picked again. It shows up again in a few minutes (Requests > Music shows how it's going).`, "Delete", "Cancel");
    if (!yes) return;
    try {
      const r = await B.call("POST", "api/music/redo", { id: s.Id });
      B.toast((r && r.words) || "Deleted. Tower is finding a new copy.");
    } catch (e) { B.toast(e.message); }
  };
  const addToPlaylist = async (anchor, songs) => {
    try {
      const pls = await items(`IncludeItemTypes=Playlist&MediaTypes=Audio&SortBy=SortName`);
      const own = [];
      for (const p of pls) own.push({ label: p.Name, value: p.Id });
      own.push({ label: "New playlist…", value: "new" });
      B.dropdown(anchor, own, null, async (id) => {
        const ids = songs.map((s) => s.Id).join(",");
        if (id === "new") {
          const name = await B.askText("New playlist", "What should it be called?", "");
          if (!name) return;
          await B.jf("POST", `/Playlists`, { Name: name, Ids: songs.map((s) => s.Id), UserId: B.me.id, MediaType: "Audio" });
          return B.toast(`Made ${name}`);
        }
        await B.jf("POST", `/Playlists/${enc(id)}/Items?Ids=${enc(ids)}&UserId=${B.uid()}`);
        B.toast("Added to the playlist");
      });
    } catch (e) { B.toast(e.message); }
  };
  const wireSongs = (root, songs, title, sourceId, extra) => {
    root.addEventListener("click", (e) => {
      const h = e.target.closest("[data-heart]");
      if (h) { e.stopPropagation(); const s = songs.find((x) => x.Id === h.dataset.heart); const on = !h.classList.contains("on"); M.like(h.dataset.heart, on).then(() => { if (s) s.UserData = Object.assign(s.UserData || {}, { IsFavorite: on }); }); return; }
      const m = e.target.closest("[data-more]");
      if (m) { e.stopPropagation(); return songMenu(m, songs[+m.dataset.more], extra && extra(+m.dataset.more)); }
      const r = e.target.closest(".srow");
      if (r) { M.playList(songs, +r.dataset.i, false, title); M.sourceId = sourceId; refreshRows(root, songs); }
    });
  };
  const refreshRows = (root, songs) => setTimeout(() => $$(".srow", root).forEach((r) => r.classList.toggle("now", M.current() && songs[+r.dataset.i] && M.current().Id === songs[+r.dataset.i].Id)), 50);

  // ---------- the Music page ----------
  B.route(/^\/music$/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home");
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage"><div class="mhead"><div class="mkick">MUSIC</div><h1>Your songs and playlists</h1></div><div id="mrows">${B.spinner()}</div></div>`;
    B.wireTop();
    const ls = await libs().catch(() => []);
    if (!B.alive(nav)) return;
    if (!ls.length) { $("#mrows").innerHTML = `<p class="dim">There's no music library on Benflix yet.</p>`; return; }
    const [mixes, pls, liked, recent, played, artists] = await Promise.all([
      B.musicMixes().catch(() => []),
      items(`IncludeItemTypes=Playlist&MediaTypes=Audio&SortBy=DateCreated&SortOrder=Descending&Fields=ChildCount&Limit=30`).catch(() => []),
      B.get(`/Users/${B.uid()}/Items?Recursive=true&IncludeItemTypes=Audio&Filters=IsFavorite&Limit=0`).then((j) => j.TotalRecordCount || 0).catch(() => 0),
      items(`IncludeItemTypes=MusicAlbum&SortBy=DateCreated&SortOrder=Descending&Limit=30`).catch(() => []),
      items(`IncludeItemTypes=MusicAlbum&SortBy=PlayCount,DatePlayed&SortOrder=Descending&Filters=IsPlayed&Limit=30`).catch(() => []),
      B.get(`/Artists/AlbumArtists?UserId=${B.uid()}&SortBy=PlayCount,SortName&SortOrder=Descending&Limit=30&Fields=PrimaryImageAspectRatio`).then((j) => j.Items || []).catch(() => []),
    ]);
    if (!B.alive(nav)) return;
    const rows = [];
    if (mixes.length) rows.push(B.row("Made for you", mixes.map(mixCard)));
    rows.push(B.row("Your playlists", [likedCard(liked)].concat(pls.map(playlistCard), B.me.admin ? [`<a class="mcard" href="#/requests?tab=music"><div class="im ask">${I("plus")}</div><div class="cap">Ask for music<small>Songs, albums, artists</small></div></a>`] : [])));
    rows.push(B.row("Recently added", recent.map(albumCard), { more: "#/mgrid/recent" }));
    if (played.length) rows.push(B.row("Most played albums", played.map(albumCard)));
    rows.push(B.row("Artists", artists.map(artistCard), { more: "#/mgrid/artists" }));
    rows.push(`<section class="rowx"><h3>Browse by</h3><div class="mbrowse"><a href="#/songs">Songs</a><a href="#/mgrid/albums">Albums</a><a href="#/mgrid/artists">Artists</a><a href="#/mgrid/genres">Genres</a><a href="#/mgrid/playlists">Playlists</a></div></section>`);
    $("#mrows").innerHTML = rows.join("");
    const f = $("#mrows a"); if (f) f.focus({ preventScroll: true });
  });

  // ---------- grids (See all, Browse by) ----------
  B.route(/^\/mgrid\/(\w+)(?:\/([\w-]+))?/, async (m, nav) => {
    const kind = m[1], id = m[2];
    const titles = { recent: "Recently added", albums: "Albums", artists: "Artists", genres: "Genres", playlists: "Playlists", genre: B.q("name") || "Genre" };
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage"><h1 class="mtitle">${esc(titles[kind] || "Music")}</h1><div class="mgrid" id="mg">${B.spinner()}</div></div>`;
    B.wireTop();
    let html = "";
    if (kind === "artists") html = ((await B.get(`/Artists/AlbumArtists?UserId=${B.uid()}&SortBy=SortName&Limit=3000`)).Items || []).map(artistCard).join("");
    else if (kind === "genres") html = ((await B.get(`/MusicGenres?UserId=${B.uid()}&SortBy=SortName&Limit=500`)).Items || []).map((g) => `<a class="mgenre" href="#/mgrid/genre/${g.Id}?name=${enc(g.Name)}">${esc(g.Name)}</a>`).join("");
    else if (kind === "playlists") html = (await items(`IncludeItemTypes=Playlist&MediaTypes=Audio&SortBy=SortName&Fields=ChildCount`)).map(playlistCard).join("");
    else if (kind === "genre") html = (await items(`IncludeItemTypes=MusicAlbum&GenreIds=${enc(id)}&SortBy=SortName&Limit=3000`)).map(albumCard).join("");
    else html = (await items(`IncludeItemTypes=MusicAlbum&SortBy=${kind === "recent" ? "DateCreated&SortOrder=Descending" : "SortName"}&Limit=3000`)).map(albumCard).join("");
    if (!B.alive(nav)) return;
    $("#mg").innerHTML = html || `<p class="dim">Nothing here yet.</p>`;
  });

  // ---------- album, playlist, mix and Liked songs pages ----------
  const hero = (o) => `<div class="mhero">
      <div class="mhero-art ${o.collage ? "collage" : ""}" ${o.art && !o.collage ? `style="background-image:url('${o.art}')"` : ""}>${o.collage ? o.collage.map((c) => `<span style="background-image:url('${c}')"></span>`).join("") : ""}</div>
      <div class="mhero-words"><div class="mkick">${esc(o.kick)}</div><h1>${esc(o.title)}</h1><div class="dim">${o.line}</div>
        <div class="mhero-btns"><button class="btn white" data-h="play">${I("play")} Play</button><button class="btn" data-h="shuffle">${icon("shuffle")} Shuffle</button>${o.heart != null ? `<button class="btn mheart ${o.heart ? "on" : ""}" data-h="heart">${I("heart")}</button>` : ""}<button class="btn" data-h="more">${I("more")}</button></div>
      </div></div>`;
  const collection = async (nav, o) => {
    const songs = o.songs;
    const mins = Math.round(songs.reduce((t, s) => t + (s.RunTimeTicks || 0), 0) / B.TICKS / 60);
    const discs = new Set(songs.map((s) => s.ParentIndexNumber || 1));
    let body = "", lastDisc = null;
    songs.forEach((s, i) => {
      if (discs.size > 1 && o.album && s.ParentIndexNumber !== lastDisc) { lastDisc = s.ParentIndexNumber; body += `<div class="mdisc">Disc ${esc(lastDisc || 1)}</div>`; }
      body += songRow(s, i, { number: o.album ? s.IndexNumber || i + 1 : null, cover: !o.album, artist: !o.album });
    });
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage">${hero(Object.assign({}, o, { line: `${o.line ? esc(o.line) + " · " : ""}${o.total > songs.length ? `${songs.length} of ${o.total}` : songs.length} songs · ${B.len(mins)}` }))}
      <div class="msongs" id="ms">${body || `<p class="dim">No songs here yet.</p>`}</div><div id="mbelow"></div></div>`;
    B.wireTop();
    const root = $("#ms");
    wireSongs(root, songs, o.title, o.sourceId, o.extra);
    $(".mhero-btns").addEventListener("click", (e) => {
      const b = e.target.closest("[data-h]"); if (!b) return;
      // Ben: Play or Shuffle goes straight to Now playing
      if (b.dataset.h === "play") { M.playList(songs, 0, false, o.title); M.sourceId = o.sourceId; refreshRows(root, songs); openNow(); }
      if (b.dataset.h === "shuffle") { M.playList(songs, 0, true, o.title); M.sourceId = o.sourceId; refreshRows(root, songs); openNow(); }
      if (b.dataset.h === "heart") { const on = !b.classList.contains("on"); M.like(o.sourceId, on).then(() => b.classList.toggle("on", on)); }
      if (b.dataset.h === "more") {
        const menu = [{ label: "Add all to Up next", value: "all" }, { label: "Add all to a playlist", value: "pl" }].concat(o.menu || []);
        B.dropdown(b, menu, null, (v) => {
          if (v === "all") songs.forEach((s) => M.addToUpNext(s));
          else if (v === "pl") addToPlaylist(b, songs);
          else (o.menu || []).forEach((x) => { if (x.value === v && x.run) x.run(); });
        });
      }
    });
    setTimeout(() => { const p = $("[data-h=play]"); if (p) p.focus({ preventScroll: true }); }, 30);
  };
  B.route(/^\/album\/([\w-]+)/, async (m, nav) => {
    const u = B.uid();
    const [al, songs] = await Promise.all([B.get(`/Users/${u}/Items/${m[1]}`), items(`ParentId=${enc(m[1])}&IncludeItemTypes=Audio&SortBy=ParentIndexNumber,IndexNumber,SortName&${SONG_FIELDS}`)]);
    if (!B.alive(nav)) return;
    const artist = (al.AlbumArtists || [])[0];
    // Only some songs of the album (asked for one by one): "3 of 12 songs" (waits at most 1.5 s)
    const total = await Promise.race([
      B.call("GET", `api/music/albumsize?artist=${enc(al.AlbumArtist || "")}&album=${enc(al.Name || "")}`).then((j) => (j && j.songs) || 0).catch(() => 0),
      new Promise((r) => setTimeout(() => r(0), 1500)),
    ]);
    if (!B.alive(nav)) return;
    await collection(nav, {
      kick: "ALBUM", title: al.Name, line: [al.AlbumArtist, al.ProductionYear].filter(Boolean).join(" · "), art: cover(al, 900), songs, album: true, sourceId: al.Id, total,
      heart: !!(al.UserData || {}).IsFavorite,
      menu: artist ? [{ label: `Go to ${artist.Name}`, value: "artist", run: () => B.go(`#/artist/${artist.Id}`) }] : [],
    });
    // Below the songs: more by the artist, then similar albums
    const below = [];
    if (artist) {
      const more = (await items(`IncludeItemTypes=MusicAlbum&AlbumArtistIds=${enc(artist.Id)}&SortBy=ProductionYear&SortOrder=Descending&Limit=20`).catch(() => [])).filter((a) => a.Id !== al.Id);
      if (more.length) below.push(B.row(`More by ${artist.Name}`, more.map(albumCard)));
    }
    const similar = ((await B.get(`/Items/${enc(al.Id)}/Similar?UserId=${u}&Limit=16`).catch(() => ({}))).Items || []).filter((a) => a.Type === "MusicAlbum");
    if (similar.length) below.push(B.row("Similar albums", similar.map(albumCard)));
    if (B.alive(nav) && $("#mbelow")) $("#mbelow").innerHTML = below.join("");
  });
  B.route(/^\/playlist\/([\w-]+)/, async (m, nav) => {
    const u = B.uid();
    const [pl, list, perm] = await Promise.all([
      B.get(`/Users/${u}/Items/${m[1]}`),
      B.get(`/Playlists/${enc(m[1])}/Items?UserId=${u}&${SONG_FIELDS}`).then((j) => j.Items || []),
      B.get(`/Playlists/${enc(m[1])}/Users/${u}`).catch(() => null),
    ]);
    if (!B.alive(nav)) return;
    const canEdit = !!(perm && perm.CanEdit) || pl.OwnerUserId === B.me.id;
    const songs = list.filter((s) => s.Type === "Audio");
    const covers = [...new Set(songs.map((s) => s.AlbumId).filter(Boolean))].slice(0, 4).map((id) => B.img(id, "Primary", { h: 300 }));
    const art = (pl.ImageTags || {}).Primary ? cover(pl, 900) : null;
    await collection(nav, {
      kick: "PLAYLIST", title: pl.Name, line: "", art, collage: art || covers.length < 2 ? null : covers.concat(covers).slice(0, 4), songs, sourceId: pl.Id,
      menu: canEdit ? [{ label: "Rename", value: "rename", run: async () => { const n = await B.askText("Rename playlist", "", pl.Name); if (n) { await B.jf("POST", `/Playlists/${enc(pl.Id)}`, { Name: n }); B.render(); } } }] : [],
      extra: canEdit ? (i) => [
        { label: "Move up", value: "up", run: async () => { if (i > 0) { await B.jf("POST", `/Playlists/${enc(pl.Id)}/Items/${enc(songs[i].PlaylistItemId)}/Move/${i - 1}`); B.render(); } } },
        { label: "Move down", value: "down", run: async () => { if (i < songs.length - 1) { await B.jf("POST", `/Playlists/${enc(pl.Id)}/Items/${enc(songs[i].PlaylistItemId)}/Move/${i + 1}`); B.render(); } } },
        { label: "Remove from this playlist", value: "rm", run: async () => { await B.jf("DELETE", `/Playlists/${enc(pl.Id)}/Items?EntryIds=${enc(songs[i].PlaylistItemId)}`); B.render(); } },
      ] : null,
    });
  });
  // Browse by > Songs: every song, A to Z
  B.route(/^\/songs$/, async (m, nav) => {
    const raw = await items(`IncludeItemTypes=Audio&SortBy=SortName&Limit=10000&${SONG_FIELDS}`);
    if (!B.alive(nav)) return;
    // The same song saved twice (same artist, same name ignoring spaces/punctuation, length within 3s) shows once
    const seen = new Map();
    const songs = raw.filter((s) => {
      const name = (s.Name || "").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
      if (!name) return true;
      const key = (s.AlbumArtist || (s.Artists || [])[0] || "").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "") + "|" + name;
      const secs = Math.round((s.RunTimeTicks || 0) / 1e7), known = seen.get(key) || [];
      if (known.some((k) => Math.abs(k - secs) <= 3)) return false;
      seen.set(key, known.concat(secs));
      return true;
    });
    const covers = [...new Set(songs.map((s) => s.AlbumId).filter(Boolean))].sort(() => Math.random() - 0.5).slice(0, 4).map((id) => B.img(id, "Primary", { h: 300 }));
    await collection(nav, { kick: "EVERY SONG ON BENFLIX", title: "All songs", line: "", collage: covers.length >= 2 ? covers.concat(covers).slice(0, 4) : null, songs, sourceId: null });
  });
  B.route(/^\/liked$/, async (m, nav) => {
    const songs = await items(`IncludeItemTypes=Audio&Filters=IsFavorite&SortBy=DateCreated&SortOrder=Descending&Limit=3000&${SONG_FIELDS}`);
    if (!B.alive(nav)) return;
    const covers = [...new Set(songs.map((s) => s.AlbumId).filter(Boolean))].slice(0, 4).map((id) => B.img(id, "Primary", { h: 300 }));
    await collection(nav, { kick: "PLAYLIST", title: "Liked songs", line: "Every song you gave a heart", collage: covers.length >= 2 ? covers.concat(covers).slice(0, 4) : null, songs, sourceId: null });
  });
  B.route(/^\/mix\/([\w-]+)/, async (m, nav) => {
    const mix = (await B.musicMixes()).find((x) => x.key === decodeURIComponent(m[1]));
    if (!mix) return B.go("#/music");
    const got = await items(`Ids=${enc(mix.songs.join(","))}&${SONG_FIELDS}`);
    const byId = {}; got.forEach((s) => { byId[s.Id] = s; });
    const songs = mix.songs.map((id) => byId[id]).filter(Boolean);
    if (!B.alive(nav)) return;
    await collection(nav, { kick: "MADE FOR YOU", title: mix.name, line: mix.line, collage: mix.covers.concat(mix.covers).slice(0, 4).map((c) => B.img(c, "Primary", { h: 300 })), songs, sourceId: null });
  });

  // ---------- artist page ----------
  B.route(/^\/artist\/([\w-]+)/, async (m, nav) => {
    const u = B.uid();
    const [ar, top, albums] = await Promise.all([
      B.get(`/Users/${u}/Items/${m[1]}`),
      items(`IncludeItemTypes=Audio&ArtistIds=${enc(m[1])}&SortBy=PlayCount,CommunityRating,SortName&SortOrder=Descending&Limit=10&${SONG_FIELDS}`),
      items(`IncludeItemTypes=MusicAlbum&AlbumArtistIds=${enc(m[1])}&SortBy=ProductionYear,SortName&SortOrder=Descending&Fields=ChildCount`),
    ]);
    if (!B.alive(nav)) return;
    const full = albums.filter((a) => (a.ChildCount || 3) > 2), singles = albums.filter((a) => (a.ChildCount || 3) <= 2);
    const follow = !!(ar.UserData || {}).IsFavorite;
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage"><div class="mhero artist"><div class="mhero-art round" style="${cover(ar, 600) ? `background-image:url('${cover(ar, 600)}')` : ""}"></div>
      <div class="mhero-words"><div class="mkick">ARTIST</div><h1>${esc(ar.Name)}</h1><div class="dim">${albums.length} albums and singles</div>
      <div class="mhero-btns"><button class="btn white" data-h="top">${I("play")} Play top songs</button><button class="btn" data-h="shuffle">${icon("shuffle")} Shuffle</button><button class="btn ${follow ? "on" : ""}" data-h="follow">${follow ? I("check") + " Following" : I("plus") + " Follow"}</button></div></div></div>
      <h3 class="msub">Top songs</h3><div class="msongs" id="ms">${top.map((s, i) => songRow(s, i, { cover: true })).join("")}</div>
      ${B.row("Albums", full.map(albumCard))}${B.row("Singles and EPs", singles.map(albumCard))}</div>`;
    B.wireTop();
    wireSongs($("#ms"), top, `${ar.Name}: top songs`, ar.Id);
    $(".mhero-btns").addEventListener("click", async (e) => {
      const b = e.target.closest("[data-h]"); if (!b) return;
      if (b.dataset.h === "top") { M.playList(top, 0, false, `${ar.Name}: top songs`); M.sourceId = ar.Id; openNow(); }
      if (b.dataset.h === "shuffle") {
        const all = await items(`IncludeItemTypes=Audio&ArtistIds=${enc(ar.Id)}&Limit=500&${SONG_FIELDS}`);
        M.playList(all, 0, true, ar.Name); M.sourceId = ar.Id; openNow();
      }
      if (b.dataset.h === "follow") {
        const on = !b.classList.contains("on");
        await B.jf(on ? "POST" : "DELETE", `/Users/${u}/FavoriteItems/${enc(ar.Id)}`);
        b.classList.toggle("on", on);
        b.innerHTML = on ? I("check") + " Following" : I("plus") + " Follow";
        B.toast(on ? `Following ${ar.Name}: new music shows up on Home` : `Stopped following ${ar.Name}`);
      }
    });
    setTimeout(() => { const p = $("[data-h=top]"); if (p) p.focus({ preventScroll: true }); }, 30);
  });

  // ---------- Up next (approved q-full) ----------
  B.route(/^\/upnext$/, async () => {
    const draw = () => {
      const now = M.current();
      const rest = M.queue.slice(M.index + 1).map((s, k) => ({ s, i: M.index + 1 + k }));
      const added = rest.filter((x) => M.added.has(x.s._key)), then = rest.filter((x) => !M.added.has(x.s._key)).slice(0, 100);
      const row = (x, mine) => `<div class="srow" data-q="${x.i}"><span class="sn"><span class="sc" style="background-image:url('${cover(x.s, 80)}')"></span></span><span class="st"><b>${esc(x.s.Name)}</b><small>${esc(artistOf(x.s))}</small></span><span class="sl">${len(x.s.RunTimeTicks)}</span>
        ${mine ? `<button class="sm" data-up="${x.i}" title="Move up">${I("up")}</button><button class="sm" data-down="${x.i}" title="Move down">${I("down")}</button>` : ""}<button class="sm" data-rm="${x.i}" title="Take out">${I("x")}</button></div>`;
      B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage"><h1 class="mtitle">Up next</h1>
        ${now ? `<h3 class="msub">Playing now</h3><div class="msongs"><div class="srow now" data-open><span class="sn"><span class="bars"><i></i><i></i><i></i></span></span><span class="st"><b>${esc(now.Name)}</b><small>${esc(artistOf(now))}</small></span><span class="sl">${len(now.RunTimeTicks)}</span></div></div>` : `<p class="dim">Nothing is playing.</p>`}
        ${added.length ? `<h3 class="msub">Up next · songs you added</h3><div class="msongs">${added.map((x) => row(x, true)).join("")}</div>` : ""}
        ${then.length ? `<h3 class="msub">Then plays${M.thenTitle ? ": " + esc(M.thenTitle) : ""}</h3><div class="msongs">${then.map((x) => row(x, false)).join("")}</div>` : now ? `<p class="dim">Nothing waiting. When this song ends, a mix like it starts by itself.</p>` : ""}
      </div>`;
      B.wireTop();
      $(".mpage").onclick = onClick;
    };
    const onClick = (e) => {
      const t = e.target.closest("[data-up],[data-down],[data-rm],[data-q],[data-open]");
      if (!t) return;
      if (t.dataset.open != null) return openNow();
      const i = +(t.dataset.up || t.dataset.down || t.dataset.rm || t.dataset.q);
      if (t.dataset.up && i > M.index + 1) { const [s] = M.queue.splice(i, 1); M.queue.splice(i - 1, 0, s); }
      else if (t.dataset.down && i < M.queue.length - 1) { const [s] = M.queue.splice(i, 1); M.queue.splice(i + 1, 0, s); }
      else if (t.dataset.rm) M.queue.splice(i, 1);
      else if (t.dataset.q) load(i);
      draw();
    };
    draw();
    const tick = () => { if (location.hash === "#/upnext") draw(); };
    audio.addEventListener("play", tick);
    B.onLeave(() => audio.removeEventListener("play", tick));
  });
})();
