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
  // The shared title page (title.js) draws icons from the main set, so lend it shuffle
  if (typeof P === "object" && !P.shuffle && ICON.shuffle) P.shuffle = ICON.shuffle;
  const icon = (name, cls) => (ICON[name] ? `<svg class="i ${cls || ""}" viewBox="0 0 24 24" aria-hidden="true">${ICON[name]}</svg>` : I(name, cls));
  B.musicIcon = icon;
  const len = (ticks) => B.hms((ticks || 0) / B.TICKS);
  const items = async (q) => ((await B.get(`/Users/${B.uid()}/Items?Recursive=true&${q}`)).Items || []);
  const cover = (it, h) => {
    if (!it) return "";
    const tags = it.ImageTags || {};
    if (tags.Primary) return B.img(it.Id, "Primary", { h: h || 360, tag: tags.Primary });
    if (it.AlbumId) return B.img(it.AlbumId, "Primary", { h: h || 360, tag: it.AlbumPrimaryImageTag });
    return "";
  };
  // When an album has no cover yet (Tower looks for one every hour), the artist's photo stands in, then a music note
  const artistArt = (it, h) => {
    const a = (it && ((it.AlbumArtists || [])[0] || (it.ArtistItems || [])[0])) || null;
    return a && a.Id ? B.img(a.Id, "Primary", { h: h || 360 }) : "";
  };
  // Cover, then the artist's photo underneath: a picture that doesn't load lets the next one show
  const artLayers = (it, h) => {
    const l = [cover(it, h), artistArt(it, h)].filter(Boolean);
    return l.length ? `background-image:${l.map((u) => `url('${u}')`).join(",")}` : "";
  };
  const showArt = (el, urls, onArt) => {
    const list = urls.filter(Boolean);
    el.classList.remove("np-noart");
    const next = () => {
      const u = list.shift();
      if (!u) {
        el.style.backgroundImage = "";
        el.classList.add("np-noart");
        return onArt && onArt("");
      }
      const im = new Image();
      im.onload = () => {
        if (el.dataset.want !== key) return;
        el.style.backgroundImage = `url('${u}')`;
        if (onArt) onArt(u);
      };
      im.onerror = () => el.dataset.want === key && next();
      im.src = u;
    };
    const key = list.join("|");
    if (el.dataset.want === key) return;
    el.dataset.want = key;
    next();
  };
  const artistOf = (s) => (s.AlbumArtist || (s.Artists || []).join(", ") || "");

  // ---------- the player (one <audio> for the whole visit) ----------
  const M = (B.music = { queue: [], index: -1, added: new Set(), thenTitle: "", liked: new Set() });
  const audio = new Audio();
  audio.preload = "auto";
  M.audio = audio;
  // Music starting pauses a video that is still playing in the pop-out window
  audio.addEventListener("play", () => { if (B.kept && B.kept.pause) B.kept.pause(); });
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
  M.playList = (songs, start, shuffle, title, place) => {
    gen++;
    // Music redesign 3: what you played from goes to the top of Jump back in and Recently played (on every screen)
    if (place && place.kind) B.musicPlayed(place);
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
  M.next = () => {
    // Skip learning (Music redesign 3): a song skipped in its first 30 seconds 3 times stays out of your mixes
    const cur = M.current();
    if (cur && pos() < 30 && B.me && !B.me.kids) B.call("POST", "api/music/me/skip", { id: cur.Id }).catch(() => {});
    if (M.index + 1 < M.queue.length) load(M.index + 1); else keepGoing(true);
  };
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
    if (M.current() === s) M.lyricsChecked = s.Id;
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
        <button class="circle mheart" data-a="like" title="Like (L)">${I("heart")}</button>${B.volume("", "circle")}</div>
        <canvas class="mbar-towers"></canvas>`;
      bar.addEventListener("click", (e) => {
        const a = e.target.closest("[data-a]");
        if (a) { e.stopPropagation(); return act(a.dataset.a); }
        if (e.target.closest("[data-open]")) openNow();
      });
      document.body.appendChild(bar);
      B.wireVolume($(".volw", bar), audio, "musicVolume");
      // Music redesign 3: on phones, swipe the mini player left for the next song, right for the one before
      swipe(bar, () => M.next(), () => M.prev(), 60);
    }
    const on = !!s;
    bar.classList.toggle("hidden", !on);
    document.body.classList.toggle("has-mbar", on);
    if (M.onDraw) M.onDraw();
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
    else if (a === "similar") { openNow(); showTab("like"); }
    else if (a === "about") { openNow(); showTab("about"); }
    else if (a === "shuffle") cycleShuffle();
    else if (a === "playon") playOn();
  };
  // Music player 2026-11-17: the About tab (the artist and the credits) inside Now playing's panel
  async function aboutInto(box) {
    const s = M.current();
    if (!s || !box) return;
    box.innerHTML = B.spinner();
    const full = await B.get(`/Users/${B.uid()}/Items/${enc(s.Id)}`).catch(() => s);
    const ar = (full.AlbumArtists || [])[0] || (full.ArtistItems || [])[0];
    const artist = ar ? await B.get(`/Users/${B.uid()}/Items/${enc(ar.Id)}`).catch(() => null) : null;
    if (M.current() !== s || !document.body.contains(box)) return;
    const ROLES = { Composer: "Written by", Lyricist: "Words by", Producer: "Produced by", Arranger: "Arranged by", Conductor: "Conducted by" };
    const credits = [];
    if ((full.Artists || []).length) credits.push(["Performed by", full.Artists.join(", ")]);
    const byRole = {};
    (full.People || []).forEach((x) => { if (ROLES[x.Type]) (byRole[x.Type] = byRole[x.Type] || []).push(x.Name); });
    Object.keys(byRole).forEach((k) => credits.push([ROLES[k], [...new Set(byRole[k])].join(", ")]));
    if (full.Album) credits.push(["Album", [full.Album, full.ProductionYear].filter(Boolean).join(" · ")]);
    if ((full.Genres || []).length) credits.push(["Style", full.Genres.slice(0, 3).join(", ")]);
    const pic = ar ? B.img(ar.Id, "Primary", { h: 200 }) : "";
    box.innerHTML = `<div class="mkick">About the artist</div><div class="mabout-ar"><span class="mabout-pic" style="${pic ? `background-image:url('${pic}')` : ""}"></span><b>${esc((ar && ar.Name) || artistOf(s))}</b>${ar ? `<a class="btn small" href="#/artist/${ar.Id}">${I("user")} Go to artist</a>` : ""}</div>
      ${artist && artist.Overview ? `<p class="dim mabout-bio">${esc(artist.Overview)}</p>` : ""}
      ${credits.length ? `<div class="mkick" style="margin-top:14px">Credits</div><div class="np-credits">${credits.map(([r, n]) => `<div class="mcredit"><small class="dim">${esc(r)}</small><div>${esc(n)}</div></div>`).join("")}</div>` : ""}`;
  }
  // Music redesign 3: Play on. Another Benflix (a TV or phone open on Benflix) plays the queue from here, straight away
  async function playOn() {
    const s = M.current();
    if (!s) return;
    const panel = B.side(`<h3>Play on</h3><div id="mplayon">${B.spinner()}</div>`);
    const list = await B.get(`/Sessions?ControllableByUserId=${B.uid()}`).catch(() => []);
    const box = $("#mplayon", panel);
    if (!box) return;
    const seen = new Set();
    const screens = (list || []).filter((x) => String(x.DeviceId || "").startsWith("benflix-") && x.SupportsRemoteControl && !seen.has(x.DeviceId) && seen.add(x.DeviceId));
    if (!screens.length) { box.innerHTML = `<p class="dim">No TV or phone found. Open Benflix on the TV (any page), then try again.</p>`; return; }
    box.innerHTML = screens.map((x) => `<button class="btn wide" data-sess="${esc(x.Id)}">${I("tv")} ${esc(x.DeviceName || "Benflix")}${x.UserId !== B.uid() && x.UserName ? ` (${esc(x.UserName)})` : ""}</button>`).join("");
    box.addEventListener("click", async (e) => {
      const b = e.target.closest("[data-sess]"); if (!b) return;
      const ids = M.queue.slice(M.index, M.index + 100).map((x) => x.Id);
      const ticks = Math.round(pos() * B.TICKS);
      try {
        await B.jf("POST", `/Sessions/${enc(b.dataset.sess)}/Playing?playCommand=PlayNow&itemIds=${ids.join(",")}&startIndex=0&startPositionTicks=${ticks}`);
        audio.pause();
        B.closeAll();
        B.toast(`Playing on ${b.textContent.trim()}`);
      } catch (err) { B.toast(`Couldn't reach ${b.textContent.trim()}`); }
    });
  }
  // Music player 2026-11-17: the Songs like this tab. Songs like the playing one that aren't on Benflix yet:
  // the round button plays 30 seconds, picking a song opens its Ask for it panel, and the list keeps its place.
  async function similarInto(box) {
    const s = M.current();
    if (!s || !box) return;
    const title = s.Name || "";
    const artist = (artistOf(s) || "").split(",")[0].trim();
    const admin = B.me.admin;
    box.innerHTML = `<p class="dim">Songs that sound like ${esc(title)} that you don't have yet. The round button plays 30 seconds. Pick one to ask for it.</p><div class="np-sim">${B.spinner()}</div>`;
    let found;
    try { found = await B.call("GET", `api/music/similar?artist=${enc(artist)}&title=${enc(title)}`); } catch (e) { const l = $(".np-sim", box); if (l) l.innerHTML = `<p class="dim">${esc(e.message)}</p>`; return; }
    if (M.current() !== s || !document.body.contains(box)) return;
    const songs = (found && found.songs) || [];
    const list = $(".np-sim", box);
    const rows = () => songs.length ? songs.map((x, i) => `<div class="np-simrow">${B.previewBtn(x.id)}<button class="opt" ${x.asked ? "disabled" : `data-sim="${i}"`}>
        <span class="np-simc" style="${x.cover ? `background-image:url('${esc(x.cover)}')` : ""}"></span>
        <span style="min-width:0"><b>${esc(x.title)}</b><small class="dim">${esc(x.asked || x.artist)}</small></span></button></div>`).join("")
      : `<p class="dim">Couldn't find songs like this one that you don't already have. Try another song.</p>`;
    list.innerHTML = rows();
    list.addEventListener("click", (ev) => {
      const one = ev.target.closest("[data-sim]");
      if (!one) return;
      const i = +one.dataset.sim, x = songs[i];
      const ask = B.side(`<h3>${esc(x.title)}</h3><p class="dim">${esc([x.artist, x.album].filter(Boolean).join(" · "))}</p><h4 style="margin:14px 0 8px">Ask for ${esc(x.title)}</h4>
        ${admin ? "" : `<p class="dim">Ben says yes before it downloads.</p>`}
        <div class="acts" style="justify-content:flex-start;margin:4px 0 12px">${B.previewBtn(x.id)}<span class="dim">Hear 30 seconds</span></div>
        <button class="opt" data-pick="song">${admin ? "Ask for this song" : "Ask Ben for this song"}</button>${x.album_id ? `<button class="opt" data-pick="album">${admin ? "Ask for the whole album" : "Ask Ben for the whole album"}</button>` : ""}`);
      ask.addEventListener("click", (e2) => {
        const p = e2.target.closest("[data-pick]");
        if (!p) return;
        const album = p.dataset.pick === "album";
        B.busy(p, async () => {
          await B.call("POST", "api/music/request", { kind: album ? "album" : "song", id: +(album ? x.album_id : x.id) });
          B.previewStop();
          const words = admin ? "Asked for" : "Asked Ben";
          songs.forEach((y) => { if (y === x || (album && y.album_id && String(y.album_id) === String(x.album_id))) y.asked = words; });
          list.innerHTML = rows();
          B.closeAll();
          return admin ? `Asked for ${album ? x.album : x.title}.` : `Asked Ben for ${album ? x.album : x.title}.`;
        });
      });
    });
  }
  setInterval(() => {
    if (!bar || !M.current()) return;
    if (!towersOn && !audio.paused && !document.hidden) towersBack();
    drawNow(false, true);
  }, 500);

  // ---------- Now playing (music player 2026-11-17, Ben's final sheet) ----------
  // The song side centred (cover, name, the logo-square towers inside the progress line, Shuffle / Previous /
  // Play / Next and volume, then Like, Play on, Stop and More as circles with a word under each), and one
  // full-height panel with Lyrics, Up next, About and Songs like this, plus the next three songs along its
  // bottom. Songs with no words open on About. Click a lyric line to play from there. A narrow window puts the
  // panel under the song (the round buttons always stay circles).
  let np, raf, npTab = "lyrics", tabMoved = false;
  const tabFor = {};
  const TABS = () => [["lyrics", "Lyrics"], ["upnext", "Up next"], ["about", "About"]].concat(B.me.kids || B.me.guest ? [] : [["like", "Songs like this"]]);
  const roundBtn = (a, ic, word, cls) => `<button class="np-rb ${cls || ""}" data-a="${a}"><span class="circle">${ic}</span><small>${word}</small></button>`;
  function openNow() {
    if (!M.current()) return;
    if (!np) {
      np = document.createElement("div");
      np.className = "np";
      np.innerHTML = `<button class="circle np-x" data-x title="Close (Esc)">${I("down")}</button>
        <div class="np-main">
          <div class="np-song">
            <div class="np-cover"></div>
            <div class="np-kick">NOW PLAYING</div><div class="np-t"></div><div class="np-a"></div>
            <div class="np-prog"><span class="np-now">0:00</span><canvas class="np-line" title="Click to jump (Shift + Left/Right: 10 seconds)"></canvas><span class="np-len">0:00</span></div>
            <div class="np-ctl"><button class="circle np-shuf" data-a="shuffle" title="Shuffle">${icon("shuffle")}</button><button class="circle" data-a="prev" title="Previous song (Left)">${icon("prev")}</button><button class="circle white big" data-a="toggle" title="Play or pause (Space)"></button><button class="circle" data-a="next" title="Next song (Right)">${I("next")}</button>${B.volume("", "circle")}</div>
            <div class="np-round">${roundBtn("like", I("heart"), "Like", "mheart")}${roundBtn("playon", I("tv"), "Play on")}${roundBtn("stop", icon("stop"), "Stop")}${roundBtn("more", I("more"), "More")}</div>
          </div>
          <div class="np-panel">
            <div class="np-tabs"></div>
            <div class="np-body"></div>
            <div class="np-upnext"></div>
          </div>
        </div>`;
      np.addEventListener("click", (e) => {
        const a = e.target.closest("[data-a]");
        if (a && a.dataset.a === "more") return M.current() && songMenu(a, M.current(), B.me.kids || B.me.guest ? null : [{ label: "Songs like this to ask for", value: "similar", run: () => showTab("like") }]);
        if (a) return act(a.dataset.a);
        if (e.target.closest("[data-x]")) return closeNow();
        const tab = e.target.closest("[data-tab]");
        if (tab) return showTab(tab.dataset.tab, true);
        const line = e.target.closest(".np-line");
        if (line && songLen()) { const r = line.getBoundingClientRect(); seek(songLen() * Math.max(0, Math.min(1, (e.clientX - r.left) / r.width))); return; }
        const jump = e.target.closest("[data-jump]");
        if (jump) { seek(+jump.dataset.jump); return; }
        const play = e.target.closest("[data-playat]");
        if (play) { load(+play.dataset.playat); return; }
        if (e.target.closest("[data-openq]")) { closeNow(); B.go("#/upnext"); }
      });
      document.body.appendChild(np);
      B.wireVolume($(".volw", np), audio, "musicVolume");
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
  }
  M.openNow = openNow;
  // Going to another page (Go to artist or Go to album in More, a link, or the browser's Back) closes Now playing,
  // which used to stay on top of the new page
  window.addEventListener("hashchange", () => closeNow());
  // Shuffle on the player: one button, Off, then Shuffle, then Smart shuffle (songs like these join in)
  let shuffleMode = 0;
  async function cycleShuffle() {
    const rest = M.queue.slice(M.index + 1);
    if (shuffleMode === 2 || !rest.length) {
      shuffleMode = 0;
      B.toast("Shuffle off");
    } else if (shuffleMode === 0) {
      shuffleMode = 1;
      M.queue = M.queue.slice(0, M.index + 1).concat(rest.sort(() => Math.random() - 0.5));
      B.toast("Shuffle on");
    } else {
      shuffleMode = 2;
      const s = M.current();
      let extra = [];
      try {
        const have = new Set(M.queue.map((x) => x.Id));
        extra = ((await B.get(`/Items/${enc(s.Id)}/InstantMix?UserId=${B.uid()}&Limit=12&${SONG_FIELDS}`)).Items || []).filter((x) => x.Type === "Audio" && !have.has(x.Id));
      } catch (e) {}
      const left = M.queue.slice(M.index + 1), mixed = [];
      left.forEach((x, i) => { mixed.push(x); if (i % 3 === 2 && extra.length) mixed.push(Object.assign({}, extra.shift(), { _key: Math.random() })); });
      M.queue = M.queue.slice(0, M.index + 1).concat(mixed, extra.map((x) => Object.assign({}, x, { _key: Math.random() })));
      B.toast(mixed.length > left.length || extra.length ? "Smart shuffle: songs like these join in" : "Shuffling (no songs like these found yet)");
    }
    draw();
  }
  function showTab(t, byHand) {
    npTab = t;
    if (byHand) tabMoved = false;
    drawNpPanel(true);
  }
  let lastLyric = -2;
  // The panel's middle: only redrawn when the tab or the song changes (lyrics just move the lit line)
  function drawNpPanel(full) {
    if (!np) return;
    const s = M.current();
    if (!s) return;
    const noWords = !M.lyrics && M.lyricsChecked === s.Id;
    // Ben: a song with no words opens on About; the next song with words goes back to Lyrics
    if (noWords && npTab === "lyrics" && !tabMoved && full !== "tab") { npTab = "about"; tabMoved = true; }
    else if (M.lyrics && tabMoved && npTab === "about") { npTab = "lyrics"; tabMoved = false; }
    $(".np-tabs", np).innerHTML = TABS().map(([k, w]) => `<button class="np-tab ${k === npTab ? "on" : ""} ${k === "lyrics" && noWords ? "faded" : ""}" data-tab="${k}">${w}</button>`).join("");
    const body = $(".np-body", np);
    if (npTab === "lyrics") {
      if (full || body.dataset.tab !== "lyrics" || body.dataset.song !== s.Id) {
        body.dataset.tab = "lyrics"; body.dataset.song = s.Id;
        lastLyric = -2;
        body.innerHTML = M.lyrics
          ? `<div class="np-words">${M.lyrics.lines.map((l, k) => M.lyrics.timed ? `<button class="np-ly" data-jump="${(l.Start || 0) / B.TICKS}" data-k="${k}" title="Play from here">${esc(l.Text)}</button>` : `<p class="np-ly" data-k="${k}">${esc(l.Text)}</p>`).join("")}</div>`
          : noWords ? `<div class="np-note"><b>No words for this song yet</b><p class="dim">Benflix looks for them every night, so they may turn up tomorrow.</p></div>` : `<p class="dim">Looking for the words…</p>`;
      }
      drawLyric();
    } else if (npTab === "upnext") {
      body.dataset.tab = "upnext"; body.dataset.song = s.Id;
      const next = M.queue.slice(M.index + 1, M.index + 13);
      body.innerHTML = (next.length ? next.map((x, k) => `<button class="np-q" data-playat="${M.index + 1 + k}"><span class="np-upcover" style="${artLayers(x, 80)}"></span><span><b>${esc(x.Name)}</b><small class="dim">${esc(artistOf(x))}</small></span></button>`).join("") : `<div class="np-note"><b>Nothing waiting</b><p class="dim">When this song ends, a mix like it starts by itself.</p></div>`)
        + `<button class="btn small" data-openq style="margin-top:10px">${I("list")} Open the queue</button>`;
    } else if (body.dataset.tab !== npTab || body.dataset.song !== s.Id) {
      body.dataset.tab = npTab; body.dataset.song = s.Id;
      if (npTab === "about") aboutInto(body); else similarInto(body);
    }
    const up = M.queue.slice(M.index + 1, M.index + 4);
    $(".np-upnext", np).innerHTML = `<div class="mkick">Up next</div><div class="np-uprow">${up.length ? up.map((x, k) => `<button class="np-q" data-playat="${M.index + 1 + k}"><span class="np-upcover" style="${artLayers(x, 80)}"></span><span><b>${esc(x.Name)}</b></span></button>`).join("") : `<span class="dim">A mix like this one starts by itself.</span>`}</div>`;
  }
  function drawLyric() {
    if (!np || npTab !== "lyrics" || !M.lyrics) return;
    const i = lyricIndex();
    if (i === lastLyric) return;
    lastLyric = i;
    const box = $(".np-words", np);
    if (!box) return;
    $$(".np-ly", box).forEach((p) => p.classList.toggle("on", +p.dataset.k === i));
    const on = $(`.np-ly[data-k="${i}"]`, box);
    // Keep the line being sung second from the top, unless you're picking a line yourself
    if (on && !box.matches(":hover") && !box.contains(document.activeElement)) {
      const prev = on.previousElementSibling || on;
      box.scrollTo({ top: prev.offsetTop - box.offsetTop, behavior: "smooth" });
    }
  }
  function drawNow(full, tick) {
    if (!np || !np.classList.contains("on")) return;
    const s = M.current();
    if (!s) return closeNow();
    if (!tick || full) {
      // A soft glow from the cover's colours (Ben's pick): the cover itself, blurred, behind everything
      showArt($(".np-cover", np), [cover(s, 700), artistArt(s, 700)], (art) => np.style.setProperty("--npart", art ? `url('${art}')` : "none"));
      $(".np-kick", np).textContent = M.thenTitle ? `NOW PLAYING · FROM ${M.thenTitle.toUpperCase()}` : "NOW PLAYING";
      $(".np-t", np).textContent = s.Name || "";
      $(".np-a", np).textContent = [artistOf(s), s.Album].filter(Boolean).join(" · ");
      $("[data-a=toggle]", np).innerHTML = audio.paused ? I("play") : I("pause");
      const liked = M.liked.has(s.Id);
      $(".np-round .mheart", np).classList.toggle("on", liked);
      $(".np-round .mheart small", np).textContent = liked ? "Liked" : "Like";
      const sh = $(".np-shuf", np);
      sh.classList.toggle("on", shuffleMode > 0);
      sh.classList.toggle("smart", shuffleMode === 2);
      sh.title = ["Shuffle is off", "Shuffle is on", "Smart shuffle is on"][shuffleMode];
      drawNpPanel(full);
    }
    const d = songLen();
    $(".np-now", np).textContent = B.hms(pos());
    $(".np-len", np).textContent = d ? B.hms(d) : "";
    drawLyric();
  }

  // The towers: the same soft squares as the logo, now inside the progress line (Ben's pick). Played part lit in
  // the door colours, amber at the bottom up to violet at the top, the rest grey. The bar at the bottom of every
  // page gets the same line, smaller.
  let ctx, analyser, data;
  // Browsers pause the sound graph when the tab sleeps; wake it or the music goes silent
  function wakeSound() { try { if (ctx && ctx.state === "suspended") ctx.resume().catch(() => {}); } catch (e) {} }
  audio.addEventListener("play", () => { wakeSound(); startTowers(); });
  // Ben 2026-11-17: moving or resizing the window could hide the page for a moment, which stopped the towers for
  // good. Start them again whenever the page shows, gets focus or changes size (and the half-second check below).
  const towersBack = () => { if (!document.hidden) { wakeSound(); if (!audio.paused) startTowers(); } };
  document.addEventListener("visibilitychange", towersBack);
  window.addEventListener("focus", towersBack);
  window.addEventListener("resize", towersBack);
  window.addEventListener("pageshow", towersBack);
  const COLORS = ["#FFB547", "#7AA7FF", "#3DD6C6", "#FF6F91", MUSIC];
  const levels = [];
  let towersOn = false;
  function startTowers() {
    if (np && np.classList.contains("on") && !B.webSettings().musicBarsOff) {
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
    }
    if (towersOn) return;
    towersOn = true;
    const N = 32;
    let quiet = 0;
    const frame = () => {
      const npOpen = np && np.classList.contains("on");
      const barOn = bar && !bar.classList.contains("hidden") && !npOpen;
      if (!M.current() || (!npOpen && !barOn) || document.hidden) { towersOn = false; return; }
      raf = requestAnimationFrame(frame);
      if (analyser && npOpen) analyser.getByteFrequencyData(data);
      const t = performance.now() / 1000;
      let any = false;
      for (let i = 0; i < N; i++) {
        let target = 0;
        if (!audio.paused) {
          if (analyser && npOpen) {
            const nyq = (ctx.sampleRate || 44100) / 2, bins = data.length;
            const f0 = 40 * Math.pow(16000 / 40, i / N), f1 = 40 * Math.pow(16000 / 40, (i + 1) / N);
            const b0 = Math.floor(f0 / nyq * bins), b1 = Math.max(b0 + 1, Math.floor(f1 / nyq * bins));
            let sum = 0; for (let b = b0; b < b1 && b < bins; b++) sum += data[b];
            target = Math.min(1, (sum / (b1 - b0) / 255) * 1.15);
          } else {
            const f = i / N, beat = Math.pow(Math.max(0, Math.sin(t * Math.PI * 2 * 1.05)), 6);
            target = Math.max(0, Math.min(1, 0.55 - 0.35 * f + 0.18 * Math.sin(t * (2.1 + f * 3) + i * 1.7) + beat * (0.35 - 0.25 * f)));
          }
        }
        levels[i] = levels[i] || 0;
        levels[i] = target > levels[i] ? levels[i] + (target - levels[i]) * 0.6 : Math.max(target, levels[i] - 0.04);
        if (levels[i] > 0.01) any = true;
      }
      const d = songLen(), f = d ? Math.min(1, pos() / d) : 0;
      if (npOpen) towerLine($(".np-line", np), f, 56);
      else if (barOn) towerLine($(".mbar-towers", bar), f, Math.max(24, Math.round((bar.clientWidth || 600) / 14)));
      // Paused and settled: draw once more and stop (play starts it again)
      quiet = audio.paused && !any ? quiet + 1 : 0;
      if (quiet > 2) { towersOn = false; cancelAnimationFrame(raf); }
    };
    frame();
  }
  const rr = (g, x, y, w, h, r) => { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.fill(); };
  function towerLine(c, progress, n) {
    if (!c) return;
    const w = (c.width = Math.max(1, c.clientWidth * devicePixelRatio)), h = (c.height = Math.max(1, c.clientHeight * devicePixelRatio));
    const g = c.getContext("2d");
    const off = B.webSettings().musicBarsOff;
    // Five rows of thin squares, one door colour each, amber at the bottom up to violet at the top
    const gap = w / n * 0.25, bw = (w - gap * (n - 1)) / n;
    const rows = Math.max(2, Math.min(COLORS.length, Math.floor(h / (4 * devicePixelRatio))));
    const gy = h * 0.08, bh = (h - gy * (rows - 1)) / rows;
    const lit = Math.floor(progress * n);
    for (let i = 0; i < n; i++) {
      const played = i < lit;
      const lv = off ? 0 : Math.sqrt(Math.min(1, levels[(i * 2) % levels.length] || 0)) * (played ? 1 : 0.6);
      const up = Math.min(rows, Math.max(1, Math.ceil(lv * rows)));
      const x = i * (bw + gap);
      for (let r = 0; r < up; r++) {
        g.fillStyle = played ? COLORS[Math.min(COLORS.length - 1, Math.floor(r * COLORS.length / rows))] : "rgba(148,151,163,.45)";
        rr(g, x, h - (r + 1) * bh - r * gy, bw, bh, Math.min(bw, bh) * 0.3);
      }
    }
  }

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
    B.items[it.Id] = it;
    return `<a class="mcard" href="${href}" data-id="${esc(it.Id)}" title="${esc(it.Name)}"><div class="im">${art ? `<img class="art" src="${art}" alt="" loading="lazy" decoding="async">` : `<div class="noart" style="--door:var(--music)"><span class="nt">${esc(it.Name)}</span></div>`}</div><div class="cap"><span class="nm">${esc(it.Name)}</span><small>${esc(sub || "")}</small></div></a>`;
  };
  const albumCard = (a) => card(a, `#/album/${a.Id}`, a.AlbumArtist || a.ProductionYear || "");
  const artistCard = (a) => card(a, `#/artist/${a.Id}`, "Artist").replace('class="mcard"', 'class="mcard round"');
  const playlistCard = (p) => card(p, `#/playlist/${p.Id}`, p.ChildCount ? `${p.ChildCount} songs` : "Playlist");
  // Mix and theme covers: up to four album covers in a square (one cover fills it, two or three repeat).
  // A mix with no covers yet (a holiday Ben is still filling) gets its own colour instead of a grey square.
  const MIX_TINTS = [["#5B3FB8", "#A78BFA"], ["#B8323F", "#2E7D4F"], ["#C2410C", "#F59E0B"], ["#1E3A8A", "#60A5FA"], ["#0F766E", "#5EEAD4"], ["#9D174D", "#F472B6"]];
  const mixTint = (key) => {
    let h = 0;
    for (const ch of String(key || "")) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    const t = { christmas: 1, halloween: 2, "cozy-fall": 2, winter: 3, summer: 4, valentines: 5 }[key];
    return MIX_TINTS[t != null ? t : h % MIX_TINTS.length];
  };
  B.mixCovers = (list) => {
    const c = (list || []).filter(Boolean).slice(0, 4);
    if (c.length === 1 || !c.length) return c;
    while (c.length < 4) c.push(c[c.length - 2] || c[0]);
    return c;
  };
  const mixCard = (m) => {
    const covers = B.mixCovers(m.covers);
    const [a, b] = mixTint(m.key);
    const art = covers.length === 1
      ? `<span class="one" style="background-image:url('${B.img(covers[0], "Primary", { h: 360 })}')"></span>`
      : covers.map((c) => `<span style="background-image:url('${B.img(c, "Primary", { h: 200 })}')"></span>`).join("");
    const look = covers.length ? "" : ` empty" style="background:linear-gradient(135deg, ${a}, ${b})`;
    return `<a class="mcard mix" href="#/mix/${enc(m.key)}"><div class="im collage${look}">${covers.length ? art : `<i>${esc(m.name)}</i>`}<b>${esc(m.name)}</b></div><div class="cap"><span class="nm">${esc(m.name)}</span><small>${esc(m.line || "")}</small></div></a>`;
  };
  const likedCard = (n) => `<a class="mcard" href="#/liked"><div class="im liked">${I("heart")}</div><div class="cap"><span class="nm">Liked songs</span><small>${n} songs</small></div></a>`;
  B.musicMixes = () => B.cached("music-mixes-" + B.me.id + new Date().toDateString(), 3600000, async () => ((await B.call("GET", "api/music/mixes")) || {}).mixes || []);
  // Themed playlists (Ben 2026-11-16): holidays in season, decades, genres, moods, throwbacks
  B.musicThemes = () => B.cached("music-themes-" + B.me.id + new Date().toDateString(), 3600000, async () => (await B.call("GET", "api/music/themes")) || { themes: [] });
  const THEME_ROWS = { season: "In season", decade: "Decades", genre: "Genres", mood: "Moods and moments", lofi: "LoFi", throwback: "Throwbacks" };
  const themeRow = (th, group) => {
    const list = (th.themes || []).filter((t) => t.group === group);
    return list.length ? B.row((th.rows || {})[group] || THEME_ROWS[group], list.map(mixCard)) : "";
  };
  const libs = () => B.cached("music-libs", 600000, async () => (await B.views()).filter((l) => l.CollectionType === "music"));
  const songRow = (s, i, opts) => {
    opts = opts || {};
    const now = M.current() && M.current().Id === s.Id;
    const fav = (s.UserData || {}).IsFavorite || M.liked.has(s.Id);
    return `<div class="srow ${now ? "now" : ""}" data-i="${i}">
      <span class="sn">${now ? '<span class="bars"><i></i><i></i><i></i></span>' : opts.cover ? (cover(s, 80) ? `<img class="sc" src="${cover(s, 80)}" alt="" loading="lazy" decoding="async"${artistArt(s, 80) ? ` data-alt="${esc(artistArt(s, 80))}" onerror="this.onerror=null;this.src=this.dataset.alt"` : ""}>` : `<span class="sc"></span>`) : esc(opts.number != null ? opts.number : i + 1)}</span>
      <span class="st"><b>${esc(s.Name)}</b>${opts.artist ? `<small>${esc(artistOf(s))}</small>` : ""}</span>
      <button class="sh ${fav ? "on" : ""}" data-heart="${esc(s.Id)}" title="Like">${I("heart")}</button>
      <span class="sl">${len(s.RunTimeTicks)}</span>
      <button class="sm" data-more="${i}" title="More">${I("more")}</button>
    </div>`;
  };
  const songMenu = (anchor, s, extra) => {
    const opts = [{ label: "Play next", value: "next" }, { label: "Add to Up next", value: "add" }, { label: "Add to a playlist", value: "pl" }, { label: M.liked.has(s.Id) || (s.UserData || {}).IsFavorite ? "Unlike" : "Like", value: "like" }];
    opts.push({ label: "Start a radio", value: "radio" });
    opts.push({ label: "Send to someone", value: "send" });
    if (s.AlbumArtists && s.AlbumArtists[0]) opts.push({ label: "Go to artist", value: "artist" });
    if (s.AlbumId) opts.push({ label: "Go to album", value: "album" });
    (extra || []).forEach((o) => opts.push(o));
    // Ben only: a bad copy (wrong song, cut off, bad sound) is deleted on Tower and a new copy is found
    if (B.me.admin) opts.push({ label: "Delete and find again", value: "redo" });
    // Admins only: a song you just don't want is deleted from Tower, Jellyfin and Lidarr for good
    if (B.me.admin) opts.push({ label: "Delete song", value: "delete" });
    B.dropdown(anchor, opts, null, async (v) => {
      if (v === "next") M.playNext(s);
      else if (v === "add") M.addToUpNext(s);
      else if (v === "like") M.like(s.Id, !(M.liked.has(s.Id) || (s.UserData || {}).IsFavorite)).then(() => { s.UserData = Object.assign(s.UserData || {}, { IsFavorite: M.liked.has(s.Id) }); });
      else if (v === "artist") B.go(`#/artist/${s.AlbumArtists[0].Id}`);
      else if (v === "album") B.go(`#/album/${s.AlbumId}`);
      else if (v === "pl") addToPlaylist(anchor, [s]);
      else if (v === "redo") redoSong(s);
      else if (v === "delete") deleteSong(s);
      else if (v === "radio") startRadio(s);
      else if (v === "send") sendTo(anchor, { kind: "song", id: s.Id, name: s.Name || "" });
      else (extra || []).forEach((o) => { if (o.value === v && o.run) o.run(); });
    });
  };
  // Music redesign 3: Send to a profile. It shows in their Home news (everyone but Kids).
  const sendTo = async (anchor, place) => {
    const j = await B.call("GET", "api/music/people").catch(() => null);
    const people = (j && j.people) || [];
    if (!people.length) return B.toast("There's no one else to send music to yet.");
    B.dropdown(anchor, people.map((x) => ({ label: x.name, value: x.id })), null, async (to) => {
      const who = people.find((x) => x.id === to);
      try {
        await B.call("POST", "api/music/me/send", Object.assign({}, place, { to, from_name: (B.me && B.me.name) || "" }));
        B.toast(`Sent to ${who ? who.name : "them"}. It shows on their Home.`);
      } catch (e) { B.toast(e.message || "Couldn't send that right now. Try again in a minute."); }
    });
  };
  B.musicSendTo = sendTo;
  // A song someone sent you (Home news): it plays, and its album opens
  B.route(/^\/song\/([\w-]+)/, async (m) => {
    const s = await B.get(`/Users/${B.uid()}/Items/${enc(m[1])}`).catch(() => null);
    if (!s || s.Type !== "Audio") return B.go("#/music");
    M.playList([s], 0, false, "Sent to you"); M.sourceId = null;
    B.go(s.AlbumId ? `#/album/${s.AlbumId}` : "#/music");
    setTimeout(openNow, 200);
  });
  // Ben 2026-11-16: this song, then songs like it from your music
  const startRadio = async (s) => {
    try {
      const j = await B.get(`/Items/${enc(s.Id)}/InstantMix?UserId=${B.uid()}&Limit=50&${SONG_FIELDS}`);
      const list = [s].concat(((j && j.Items) || []).filter((x) => x.Id !== s.Id && x.Type === "Audio"));
      M.playList(list, 0, false, `${s.Name} radio`); M.sourceId = null; openNow();
    } catch (e) { B.toast("Couldn't start a radio from that song. Try again in a minute."); }
  };
  const redoSong = async (s) => {
    const yes = await B.ask("Delete and find again?", `This deletes "${s.Name}" from Tower and finds a new copy. The bad copy won't be picked again. It shows up again in a few minutes (Requests > Music shows how it's going).`, "Delete", "Cancel");
    if (!yes) return;
    try {
      const r = await B.call("POST", "api/music/redo", { id: s.Id });
      B.toast((r && r.words) || "Deleted. Tower is finding a new copy.");
    } catch (e) { B.toast(e.message); }
  };
  const deleteSong = async (s) => {
    const yes = await B.ask("Delete this song?", `This deletes "${s.Name}" from Tower for everyone and tells Lidarr not to get it again. It can't be undone (you can still ask for it again later).`, "Delete", "Cancel");
    if (!yes) return;
    try {
      const r = await B.call("POST", "api/music/delete", { id: s.Id });
      B.toast((r && r.words) || "Deleted. It won't be downloaded again.");
      document.querySelectorAll(".srow").forEach((row) => { if (row.querySelector(`[data-heart="${s.Id}"]`)) row.remove(); });
      if (M.current() && M.current().Id === s.Id && M.next) M.next();
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
  // ---------- Music redesign 3: songs Benflix doesn't have yet (greyed, with a 30-second preview and Ask) ----------
  const songKey = (t) => String(t || "").toLowerCase().replace(/\s*[([].*?[)\]]/g, "").replace(/\s+-\s+.*(remaster|version|edit|mix|live|mono|stereo).*$/, "").replace(/[^\p{L}\p{N}]/gu, "");
  const secs = (n) => (n ? `${Math.floor(n / 60)}:${String(n % 60).padStart(2, "0")}` : "");
  const ghostRow = (c, n, withCover) => `<div class="srow ghost" data-ghost="${esc(String(c.id))}">
      <span class="sn">${withCover ? (c.cover ? `<img class="sc" src="${esc(c.cover)}" alt="" loading="lazy" decoding="async">` : `<span class="sc"></span>`) : esc(n != null ? n : "")}</span>
      <span class="st"><b>${esc(c.title)}</b><small>${withCover ? esc(c.artist) + " · " : ""}Not on Benflix</small></span>
      ${B.previewBtn(c.id)}<span class="sl">${secs(c.seconds)}</span>
      <button class="btn small mask ${c.asked ? "done" : ""}" data-askghost="${esc(String(c.id))}" ${c.asked ? "disabled" : ""}>${esc(c.asked || "Ask")}</button></div>`;
  const wireGhosts = (root, onAsked) => root.addEventListener("click", async (e) => {
    const b = e.target.closest("[data-askghost]");
    if (!b) {
      // Pressing a greyed song plays its 30-second preview
      const g = e.target.closest(".ghost");
      if (g && !e.target.closest("[data-pv]")) { const pv = g.querySelector("[data-pv]"); if (pv) pv.click(); }
      return;
    }
    e.stopPropagation();
    if (b.disabled) return;
    b.disabled = true; b.textContent = "Asking...";
    try {
      const r = await B.call("POST", "api/music/request", { kind: "song", id: +b.dataset.askghost });
      B.previewStop();
      b.textContent = (r && r.words) || "Asked ✓"; b.classList.add("done");
      if (onAsked) onAsked(+b.dataset.askghost, r);
    } catch (err) {
      b.disabled = false; b.textContent = "Ask";
      B.toast((err && err.message) || "Couldn't ask for that song. Try again in a minute.");
    }
  });
  // A little of the cover's own colour at the top of the page (Ben 2026-11-16: page tops only)
  const tintTop = (url, el) => {
    if (!url || !el) return;
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      try {
        const c = document.createElement("canvas"); c.width = c.height = 12;
        const x = c.getContext("2d"); x.drawImage(img, 0, 0, 12, 12);
        const d = x.getImageData(0, 0, 12, 12).data;
        let r = 0, g = 0, b = 0, n = 0;
        for (let i = 0; i < d.length; i += 4) { const v = Math.max(d[i], d[i + 1], d[i + 2]); if (v < 25 || v > 245) continue; r += d[i]; g += d[i + 1]; b += d[i + 2]; n++; }
        if (!n) return;
        r /= n; g /= n; b /= n;
        const k = Math.min(1, 140 / Math.max(r, g, b));
        el.style.backgroundImage = `linear-gradient(rgba(${Math.round(r * k)},${Math.round(g * k)},${Math.round(b * k)},.55), transparent 460px)`;
      } catch (e) { /* a picture from another site can't be read: no colour */ }
    };
    img.src = url;
  };
  const wireSongs = (root, songs, title, sourceId, extra, place) => {
    root.addEventListener("click", (e) => {
      const h = e.target.closest("[data-heart]");
      if (h) { e.stopPropagation(); const s = songs.find((x) => x.Id === h.dataset.heart); const on = !h.classList.contains("on"); M.like(h.dataset.heart, on).then(() => { if (s) s.UserData = Object.assign(s.UserData || {}, { IsFavorite: on }); }); return; }
      const m = e.target.closest("[data-more]");
      if (m) { e.stopPropagation(); return songMenu(m, songs[+m.dataset.more], extra && extra(+m.dataset.more)); }
      const r = e.target.closest(".srow:not(.ghost)");
      if (r && r.dataset.i != null) { M.playList(songs, +r.dataset.i, false, title, place); M.sourceId = sourceId; refreshRows(root, songs); }
    });
    // Music redesign 3: on phones, swipe a song to the right to add it to the queue
    swipe(root, null, (e) => {
      const r = e.target.closest(".srow:not(.ghost)");
      if (r && r.dataset.i != null && songs[+r.dataset.i]) M.addToUpNext(songs[+r.dataset.i]);
    }, 80);
  };
  /** A sideways swipe on a touch screen (mostly sideways, at least [minX] pixels) */
  function swipe(el, onLeft, onRight, minX) {
    let x0 = null, y0 = 0;
    el.addEventListener("touchstart", (e) => { const t = e.touches[0]; x0 = t.clientX; y0 = t.clientY; }, { passive: true });
    el.addEventListener("touchend", (e) => {
      if (x0 == null) return;
      const t = e.changedTouches[0], dx = t.clientX - x0, dy = t.clientY - y0;
      x0 = null;
      if (Math.abs(dx) < minX || Math.abs(dx) < 2 * Math.abs(dy)) return;
      const f = dx < 0 ? onLeft : onRight;
      if (f) f(e);
    }, { passive: true });
  }
  const refreshRows = (root, songs) => setTimeout(() => $$(".srow", root).forEach((r) => r.classList.toggle("now", M.current() && songs[+r.dataset.i] && M.current().Id === songs[+r.dataset.i].Id)), 50);

  // ---------- Music redesign 3: your places (Recently played, pins, Recent searches) ----------
  // Kept per profile on Tower (api/music/me), so the TV, the phones and the browser show the same.
  let me = null, meAt = 0;
  const NO_ME = { recents: [], searches: [], pins: [] };
  B.musicMe = async (force) => {
    if (!force && me && Date.now() - meAt < 30000) return me;
    try { const j = await B.call("GET", "api/music/me"); if (j && j.recents) { me = j; meAt = Date.now(); } } catch (e) { /* older Tower: works from memory */ }
    return me || (me = { recents: [], searches: [], pins: [] });
  };
  const meSave = async (path, body) => {
    try { const j = await B.call("POST", path, body); if (j && j.recents) { me = j; meAt = Date.now(); } } catch (e) { if (e && e.message && /pin/i.test(e.message)) B.toast(e.message); }
    paintSide();
  };
  B.musicPlayed = (place) => {
    const mine = me || (me = { recents: [], searches: [], pins: [] });
    mine.recents = [Object.assign({}, place, { at: Date.now() / 1000 })].concat((mine.recents || []).filter((r) => !(r.kind === place.kind && r.id === place.id)));
    meSave("api/music/me/played", place);
  };
  const keyOf = (p) => `${p.kind}:${p.id}`;
  const PIN_MAX = 4;
  const togglePin = (place) => {
    if (place.kind === "liked") return B.toast("Liked songs is always pinned first");
    const mine = me || NO_ME;
    const on = !(mine.pins || []).some((x) => keyOf(x) === keyOf(place));
    if (on && (mine.pins || []).length >= PIN_MAX) return B.toast(`You can pin ${PIN_MAX}. Unpin one first.`);
    mine.pins = on ? (mine.pins || []).concat([place]) : (mine.pins || []).filter((x) => keyOf(x) !== keyOf(place));
    B.toast(on ? `Pinned ${place.name}` : `Unpinned ${place.name}`);
    meSave("api/music/me/pin", Object.assign({}, place, { on }));
  };
  // The albums, playlists and artists behind a list of places (by id), and today's mixes and themes by key
  const resolve = async (places) => {
    const ids = [...new Set(places.filter((p) => ["album", "playlist", "artist"].includes(p.kind) && p.id).map((p) => p.id))].slice(0, 80);
    const [got, mixes, themes] = await Promise.all([
      ids.length ? items(`Ids=${enc(ids.join(","))}&Fields=ChildCount,DateCreated`).catch(() => []) : [],
      B.musicMixes().catch(() => []),
      B.musicThemes().catch(() => ({ themes: [] })),
    ]);
    const byId = {}; got.forEach((x) => { byId[x.Id] = x; });
    const byKey = {}; mixes.concat(themes.themes || []).forEach((x) => { byKey[x.key] = x; });
    return { byId, byKey };
  };
  const hrefOf = (p) => ({ album: `#/album/${p.id}`, playlist: `#/playlist/${p.id}`, artist: `#/artist/${p.id}`, mix: `#/mix/${enc(p.id)}`, liked: "#/liked", songs: "#/songs" }[p.kind] || "#/music");
  // A card for a place you played (Jump back in): null when it's gone (an old mix, a deleted playlist)
  const placeCard = (p, r, liked) => {
    if (p.kind === "mix") return r.byKey[p.id] ? mixCard(r.byKey[p.id]) : null;
    if (p.kind === "liked") return likedCard(liked);
    if (p.kind === "songs") return `<a class="mcard" href="#/songs"><div class="im ask">${icon("note")}</div><div class="cap"><span class="nm">All songs</span><small>A to Z</small></div></a>`;
    const it = r.byId[p.id];
    if (!it) return null;
    return p.kind === "album" ? albumCard(it) : p.kind === "artist" ? artistCard(it) : playlistCard(it);
  };
  // A small square picture for a place (quick picks, Your library, the left side)
  const placeArt = (p, r) => {
    if (p.kind === "liked") return `<span class="qa liked">${I("heart")}</span>`;
    if (p.kind === "songs") return `<span class="qa">${icon("note")}</span>`;
    if (p.kind === "mix") {
      const m = r.byKey[p.id]; const c = m && B.mixCovers(m.covers)[0];
      return c ? `<span class="qa" style="background-image:url('${B.img(c, "Primary", { h: 120 })}')"></span>` : `<span class="qa mixq">${icon("note")}</span>`;
    }
    const it = r.byId[p.id]; const art = it && cover(it, 120);
    return `<span class="qa ${p.kind === "artist" ? "round" : ""}" ${art ? `style="background-image:url('${art}')"` : ""}></span>`;
  };
  const quickTile = (p, r) => `<a class="mquick" href="${hrefOf(p)}" title="${esc(p.name)}">${placeArt(p, r)}<b>${esc(p.name)}</b></a>`;
  const placeOfItem = (it) => ({ kind: it.Type === "MusicAlbum" ? "album" : it.Type === "MusicArtist" ? "artist" : "playlist", id: it.Id, name: it.Name });
  const placeOfMix = (m) => ({ kind: "mix", id: m.key, name: m.name });

  // ---------- the browser's left side (approved r3-web3): the four places, then a short Your library ----------
  let sideHtml = "";
  const paintSide = async () => {
    if (B.me.kids) return;
    const mine = await B.musicMe();
    const list = [{ kind: "liked", id: "", name: "Liked songs" }].concat(mine.pins || [], (mine.recents || []).slice(0, 12));
    const seen = new Set(); const short = list.filter((p) => !seen.has(keyOf(p)) && seen.add(keyOf(p))).slice(0, 9);
    const r = await resolve(short).catch(() => ({ byId: {}, byKey: {} }));
    const pins = new Set((mine.pins || []).map(keyOf));
    sideHtml = `<div class="mside"><a class="mside-h" href="#/mlibrary">${I("list")}<span>Your library</span></a>${short.map((p) => `<a class="mside-i" href="${hrefOf(p)}">${placeArt(p, r)}<span><b>${esc(p.name)}</b><small>${p.kind === "liked" || pins.has(keyOf(p)) ? "● " : ""}${esc({ album: "Album", artist: "Artist", playlist: "Playlist", mix: "Made for you", liked: "Playlist", songs: "Every song" }[p.kind] || "")}</small></span></a>`).join("")}</div>`;
    const rail = $(".drail");
    if (rail && rail.dataset.door === "music") { const old = $(".mside", rail); if (old) old.outerHTML = sideHtml; else rail.insertAdjacentHTML("beforeend", sideHtml); }
  };
  B.railExtra = (door) => (door === "music" ? sideHtml : "");
  window.addEventListener("hashchange", () => { if (/^#\/(music|msearch|mlibrary|mthemes|songs|liked|mgrid)/.test(location.hash)) setTimeout(paintSide, 60); });

  // ---------- Music Home (approved g2-home): greeting, chips, 8 quick picks, shorter rows, one Browse row ----------
  const HOME_CHIPS = [["all", "All"], ["mixes", "Mixes"], ["playlists", "Playlists"], ["albums", "Albums"], ["artists", "Artists"], ["new", "New to you"]];
  const BROWSE = { decade: "Every decade you have", genre: "Your music by style", mood: "For any moment", lofi: "Hip hop, house, jazz and more", throwback: "Songs from years ago" };
  const browseCard = (group, title, list) => {
    const covers = B.mixCovers([...new Set(list.flatMap((t) => t.covers || []))].slice(0, 4));
    const art = covers.map((c) => `<span style="background-image:url('${B.img(c, "Primary", { h: 200 })}')"></span>`).join("");
    return `<a class="mcard mix" href="#/mthemes/${enc(group)}?name=${enc(title)}"><div class="im collage${covers.length ? "" : " empty"}">${covers.length ? art : `<i>${esc(title)}</i>`}<b>${esc(title)}</b></div><div class="cap"><span class="nm">${esc(title)}</span><small>${esc(BROWSE[group] || "")}</small></div></a>`;
  };
  B.route(/^\/music$/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home");
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage mhome"><h1 class="mhello">${esc(B.greeting())}, ${esc(B.me.name)}</h1>
      <div class="mchips">${HOME_CHIPS.map(([k, l]) => `<button class="chip ${k === "all" ? "on" : ""}" data-chip="${k}" style="--accent:var(--music)">${esc(l)}</button>`).join("")}</div>
      <div id="mquick" class="mquickgrid"></div><div id="mrows">${B.spinner()}</div></div>`;
    B.wireTop();
    const ls = await libs().catch(() => []);
    if (!B.alive(nav)) return;
    if (!ls.length) { $("#mrows").innerHTML = `<p class="dim">There's no music library on Benflix yet.</p>`; return; }
    const [mixes, pls, liked, recent, played, artists, themes, mine] = await Promise.all([
      B.musicMixes().catch(() => []),
      items(`IncludeItemTypes=Playlist&MediaTypes=Audio&SortBy=DateCreated&SortOrder=Descending&Fields=ChildCount&Limit=30`).catch(() => []),
      B.get(`/Users/${B.uid()}/Items?Recursive=true&IncludeItemTypes=Audio&Filters=IsFavorite&Limit=0`).then((j) => j.TotalRecordCount || 0).catch(() => 0),
      items(`IncludeItemTypes=MusicAlbum&SortBy=DateCreated&SortOrder=Descending&Limit=30&Fields=DateCreated`).catch(() => []),
      items(`IncludeItemTypes=MusicAlbum&SortBy=PlayCount,DatePlayed&SortOrder=Descending&Filters=IsPlayed&Limit=30`).catch(() => []),
      B.get(`/Artists/AlbumArtists?UserId=${B.uid()}&SortBy=PlayCount,SortName&SortOrder=Descending&Limit=30&Fields=PrimaryImageAspectRatio`).then((j) => j.Items || []).catch(() => []),
      B.musicThemes().catch(() => ({ themes: [] })),
      B.musicMe(true),
    ]);
    if (!B.alive(nav)) return;
    const recents = mine.recents || [];
    const r = await resolve(recents.slice(0, 40)).catch(() => ({ byId: {}, byKey: {} }));
    if (!B.alive(nav)) return;
    pls.forEach((p) => { r.byId[p.Id] = p; });
    const season = (themes.themes || []).filter((t) => t.group === "season");
    // Quick picks: Liked songs first, then what you played in the last few days, then what's in season, the mixes, your playlists
    const cut = Date.now() / 1000 - 5 * 86400;
    const live = (p) => p.kind === "liked" || p.kind === "songs" || (p.kind === "mix" ? !!r.byKey[p.id] : !!r.byId[p.id]);
    const pool = [{ kind: "liked", id: "", name: "Liked songs" }]
      .concat(recents.filter((p) => (p.at || 0) >= cut && p.kind !== "songs" && live(p)), season.map(placeOfMix), mixes.map(placeOfMix), pls.map(placeOfItem));
    const seen = new Set();
    const quick = pool.filter((p) => !seen.has(keyOf(p)) && seen.add(keyOf(p))).slice(0, 8);
    season.concat(mixes).forEach((x) => { r.byKey[x.key] = x; });
    $("#mquick").innerHTML = quick.map((p) => quickTile(p, r)).join("");
    const jump = recents.map((p) => placeCard(p, r, liked)).filter(Boolean).slice(0, 20);
    const rows = [];
    if (mixes.length) rows.push(B.row("Made for you", mixes.map(mixCard)));
    if (jump.length >= 2) rows.push(B.row("Jump back in", jump));
    rows.push(B.row("Your playlists", [likedCard(liked)].concat(pls.map(playlistCard))));
    rows.push(themeRow(themes, "season"));
    rows.push(B.row("Recently added", recent.map(albumCard), { more: "#/mgrid/recent" }));
    if (played.length) rows.push(B.row("Most played albums", played.map(albumCard)));
    rows.push(B.row("Artists", artists.map(artistCard), { more: "#/mgrid/artists" }));
    // Ben (shorter Home + one Browse row): Decades, Genres, Moods and Throwbacks are tiles at the bottom
    const groups = ["lofi", "decade", "genre", "mood", "throwback"].map((g) => [g, (themes.themes || []).filter((t) => t.group === g)]).filter(([, l]) => l.length);
    if (groups.length) rows.push(B.row("Browse", groups.map(([g, l]) => browseCard(g, (themes.rows || {})[g] || THEME_ROWS[g], l))));
    const allRows = rows.join("");
    $("#mrows").innerHTML = allRows;
    const f = $("#mquick a"); if (f) f.focus({ preventScroll: true });
    // The chips (approved x-filter): pick one and the page shows only that, as a grid
    $(".mchips").addEventListener("click", async (e) => {
      const c = e.target.closest("[data-chip]"); if (!c) return;
      $$(".mchips .chip").forEach((x) => x.classList.toggle("on", x === c));
      const k = c.dataset.chip;
      $("#mquick").hidden = k !== "all";
      if (k === "all") { $("#mrows").innerHTML = allRows; return; }
      $("#mrows").innerHTML = B.spinner();
      let cards = [];
      if (k === "mixes") cards = mixes.concat(themes.themes || []).map(mixCard);
      else if (k === "playlists") cards = [likedCard(liked)].concat(pls.map(playlistCard));
      else if (k === "albums") cards = (await items(`IncludeItemTypes=MusicAlbum&SortBy=SortName&Limit=3000`).catch(() => [])).map(albumCard);
      else if (k === "artists") cards = ((await B.get(`/Artists/AlbumArtists?UserId=${B.uid()}&SortBy=SortName&Limit=3000`).catch(() => ({}))).Items || []).map(artistCard);
      else if (k === "new") {
        const since = Date.now() - 14 * 86400000;
        cards = (await items(`IncludeItemTypes=MusicAlbum&SortBy=DateCreated&SortOrder=Descending&Limit=300&Fields=DateCreated`).catch(() => [])).filter((a) => Date.parse(a.DateCreated || 0) >= since).map(albumCard);
      }
      if (!B.alive(nav) || !c.classList.contains("on")) return;
      $("#mrows").innerHTML = cards.length ? `<div class="mgrid">${cards.join("")}</div>` : `<p class="dim">${k === "new" ? "Nothing new in the last two weeks." : "Nothing here yet."}</p>`;
    });
  });

  // Browse > Decades, Genres, Moods or Throwbacks: that group's themed playlists
  B.route(/^\/mthemes\/(\w+)/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home");
    const title = B.q("name") || THEME_ROWS[m[1]] || "Browse";
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage"><h1 class="mtitle">${esc(title)}</h1><div class="mgrid" id="mg">${B.spinner()}</div></div>`;
    B.wireTop();
    const th = await B.musicThemes().catch(() => ({ themes: [] }));
    if (!B.alive(nav)) return;
    const list = (th.themes || []).filter((t) => t.group === m[1]);
    $("#mg").innerHTML = list.length ? list.map(mixCard).join("") : `<p class="dim">Nothing here yet.</p>`;
  });

  // ---------- Your library (approved g2-library, x-recent) ----------
  const LIB_SORTS = ["Recently played", "Recently added", "A to Z", "Made by"];
  B.route(/^\/mlibrary$/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home");
    let chip = B.q("c") || "all", sort = +(B.q("s") || 0) % LIB_SORTS.length;
    let grid = false;
    try { grid = localStorage.getItem("benflix-mlib-grid") === "1"; } catch (e) { /* private window */ }
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage"><div class="mlib-head"><h1 class="mtitle">Your library</h1>
      <button class="btn" data-l="new">${I("plus")} New playlist</button><a class="btn" href="#/requests?tab=music">${I("dl")} Import</a><button class="btn" data-l="view" title="Grid or list"></button></div>
      <div class="mchips" id="lchips"></div><div class="mlib-sort"><button class="btn small" data-l="sort"></button><span class="dim">Right-click (or hold) to pin up to ${PIN_MAX}</span></div><div id="lbody">${B.spinner()}</div></div>`;
    B.wireTop();
    const [pls, mixes, liked, likedAlbums, likedArtists, mine, status] = await Promise.all([
      items(`IncludeItemTypes=Playlist&MediaTypes=Audio&SortBy=DateCreated&SortOrder=Descending&Fields=ChildCount,DateCreated`).catch(() => []),
      B.musicMixes().catch(() => []),
      B.get(`/Users/${B.uid()}/Items?Recursive=true&IncludeItemTypes=Audio&Filters=IsFavorite&Limit=0`).then((j) => j.TotalRecordCount || 0).catch(() => 0),
      items(`IncludeItemTypes=MusicAlbum&Filters=IsFavorite&SortBy=SortName&Fields=ChildCount,DateCreated&Limit=500`).catch(() => []),
      items(`IncludeItemTypes=MusicArtist&Filters=IsFavorite&SortBy=SortName&Limit=500`).catch(() => []),
      B.musicMe(true),
      B.call("GET", "api/music/status").catch(() => null),
    ]);
    if (!B.alive(nav)) return;
    const found = ((status && status.playlists) || []).filter((p) => !p.done);
    const r = await resolve((mine.pins || []).concat(mine.recents || [])).catch(() => ({ byId: {}, byKey: {} }));
    if (!B.alive(nav)) return;
    mixes.forEach((x) => { r.byKey[x.key] = x; });
    const entry = (p, line, added, madeBy) => ({ p, line, added: added || 0, madeBy });
    const itemEntry = (it, madeBy) => { r.byId[it.Id] = it; return entry(placeOfItem(it), it.Type === "Playlist" ? `Playlist${it.ChildCount ? ` · ${it.ChildCount} songs` : ""}` : it.Type === "MusicAlbum" ? `Album · ${it.AlbumArtist || ""}` : "Artist", Date.parse(it.DateCreated || 0) || 0, madeBy); };
    const extras = Object.values(r.byId).filter((it) => !pls.concat(likedAlbums, likedArtists).some((x) => x.Id === it.Id));
    const base = [entry({ kind: "liked", id: "", name: "Liked songs" }, `Playlist · ${liked} songs`, 0, 1)]
      .concat(mixes.map((x) => entry(placeOfMix(x), "Made for you", 0, 0)), pls.map((it) => itemEntry(it, 1)), likedAlbums.concat(likedArtists, extras).map((it) => itemEntry(it, 2)),
        [entry({ kind: "songs", id: "", name: "All songs" }, "Every song, A to Z", 0, 1)]);
    let allAlbums = null, allArtists = null;
    const chips = [["playlists", "Playlists"], ["albums", "Albums"], ["artists", "Artists"], ["recent", "Recently played"]].concat(found.length ? [["found", "Being found"]] : []);
    const order = (list) => {
      const pins = (me || NO_ME).pins || [];
      const liked1 = list.filter((e) => e.p.kind === "liked");
      const byKey = {}; list.forEach((e) => { byKey[keyOf(e.p)] = e; });
      const pinned = pins.map((p) => byKey[keyOf(p)]).filter((e) => e && e.p.kind !== "liked");
      const pk = new Set(pinned.map((e) => keyOf(e.p)));
      const played = {}; ((me || NO_ME).recents || []).forEach((p, i) => { played[keyOf(p)] = i; });
      let rest = list.filter((e) => e.p.kind !== "liked" && !pk.has(keyOf(e.p)));
      const name = (e) => (e.p.name || "").toLowerCase();
      if (sort === 1) rest.sort((a, b) => b.added - a.added);
      else if (sort === 2) rest.sort((a, b) => name(a).localeCompare(name(b)));
      else if (sort === 3) rest.sort((a, b) => a.madeBy - b.madeBy || name(a).localeCompare(name(b)));
      else rest.sort((a, b) => (played[keyOf(a.p)] ?? 1e9) - (played[keyOf(b.p)] ?? 1e9));
      return liked1.concat(pinned, rest);
    };
    const rowHtml = (e, i) => {
      const pinned = e.p.kind === "liked" || ((me || NO_ME).pins || []).some((x) => keyOf(x) === keyOf(e.p));
      return `<a class="mlib-row" href="${hrefOf(e.p)}" data-i="${i}">${placeArt(e.p, r)}<span><b>${esc(e.p.name)}</b><small>${pinned ? '<i class="pin">●</i> ' : ""}${esc(e.line)}</small></span></a>`;
    };
    const cardHtml = (e) => {
      if (e.p.kind === "liked") return likedCard(liked);
      if (e.p.kind === "songs") return placeCard(e.p, r, liked);
      if (e.p.kind === "mix") return r.byKey[e.p.id] ? mixCard(r.byKey[e.p.id]) : "";
      const it = r.byId[e.p.id]; if (!it) return "";
      return e.p.kind === "album" ? albumCard(it) : e.p.kind === "artist" ? artistCard(it) : playlistCard(it);
    };
    let shown = [];
    const draw = async () => {
      $("#lchips").innerHTML = chips.map(([k, l]) => `<button class="chip ${k === chip ? "on" : ""}" data-chip="${k}" style="--accent:var(--music)">${esc(l)}</button>`).join("");
      $("[data-l=sort]").textContent = `Sort: ${LIB_SORTS[sort]}`;
      $("[data-l=view]").innerHTML = grid ? `${I("list")} List` : `${I("film")} Grid`;
      $(".mlib-sort").hidden = chip === "recent" || chip === "found";
      const body = $("#lbody");
      if (chip === "recent") {
        const days = {};
        const today = new Date().toDateString(), yest = new Date(Date.now() - 86400000).toDateString();
        ((me || NO_ME).recents || []).forEach((p) => {
          const d = new Date((p.at || 0) * 1000);
          const name = d.toDateString() === today ? "Today" : d.toDateString() === yest ? "Yesterday" : d.toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" });
          (days[name] = days[name] || []).push(p);
        });
        const names = Object.keys(days);
        shown = [];
        body.innerHTML = names.length ? names.map((n) => `<h3 class="msub">${esc(n)}</h3>` + days[n].map((p) => { shown.push(entry(p, { album: "Album", artist: "Artist", playlist: "Playlist", mix: "Made for you", liked: "Playlist", songs: "Every song" }[p.kind] || "")); return rowHtml(shown[shown.length - 1], shown.length - 1); }).join("")).join("")
          : `<p class="dim">Nothing played yet. Everything you play shows up here for 3 months.</p>`;
        return;
      }
      if (chip === "found") {
        shown = [];
        body.innerHTML = found.map((p) => `<a class="mlib-row" href="#/requests?tab=music"><span class="qa">${I("dl")}</span><span><b>${esc(p.name)}</b><small>${esc(p.words || "")}</small></span></a>`).join("");
        return;
      }
      let list = base;
      if (chip === "playlists") list = base.filter((e) => ["playlist", "mix", "liked"].includes(e.p.kind));
      if (chip === "albums") {
        if (!allAlbums) { body.innerHTML = B.spinner(); allAlbums = (await items(`IncludeItemTypes=MusicAlbum&SortBy=SortName&Fields=ChildCount,DateCreated&Limit=3000`).catch(() => [])).map((it) => itemEntry(it, 2)); }
        list = allAlbums;
      }
      if (chip === "artists") {
        if (!allArtists) { body.innerHTML = B.spinner(); allArtists = ((await B.get(`/Artists/AlbumArtists?UserId=${B.uid()}&SortBy=SortName&Limit=3000`).catch(() => ({}))).Items || []).map((it) => itemEntry(it, 2)); }
        list = allArtists;
      }
      shown = order(list);
      body.innerHTML = shown.length ? (grid ? `<div class="mgrid">${shown.map(cardHtml).join("")}</div>` : `<div class="mlib-list">${shown.map(rowHtml).join("")}</div>`) : `<p class="dim">Nothing here yet.</p>`;
    };
    $(".mpage").addEventListener("click", async (e) => {
      const c = e.target.closest("[data-chip]");
      if (c) { chip = chip === c.dataset.chip ? "all" : c.dataset.chip; return draw(); }
      const l = e.target.closest("[data-l]"); if (!l) return;
      if (l.dataset.l === "sort") { sort = (sort + 1) % LIB_SORTS.length; draw(); }
      if (l.dataset.l === "view") { grid = !grid; try { localStorage.setItem("benflix-mlib-grid", grid ? "1" : "0"); } catch (err) { /* private window */ } draw(); }
      if (l.dataset.l === "new") {
        const name = await B.askText("New playlist", "What should it be called?", "");
        if (!name) return;
        try { await B.jf("POST", `/Playlists`, { Name: name, Ids: [], UserId: B.me.id, MediaType: "Audio" }); B.toast(`Made ${name}`); B.render(); } catch (err) { B.toast(err.message); }
      }
    });
    // Right-click (or a long press on a touch screen) pins or unpins
    $("#lbody").addEventListener("contextmenu", (e) => {
      const row = e.target.closest(".mlib-row[data-i]"); if (!row || chip === "recent") return;
      e.preventDefault();
      const it = shown[+row.dataset.i]; if (it) { togglePin(it.p); setTimeout(draw, 50); }
    });
    draw();
  });

  // ---------- Search inside Music (approved x-recentsearch) ----------
  B.route(/^\/msearch$/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home");
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage"><h1 class="mtitle">Search</h1>
      <input class="field msearch-box" id="mq" type="search" placeholder="What do you want to listen to?" autocomplete="off" value="${esc(B.q("q") || "")}">
      <div id="mres"></div></div>`;
    B.wireTop();
    const box = $("#mq"), res = $("#mres");
    let timer = 0, ticket = 0;
    const keep = () => { const q = box.value.trim(); if (q.length >= 2) meSave("api/music/me/search", { q }); };
    const recentHtml = async () => {
      const mine = await B.musicMe();
      const list = mine.searches || [];
      if (!B.alive(nav) || box.value.trim().length >= 2) return;
      res.innerHTML = (list.length ? `<div class="mlib-head"><h3 class="msub">Recent searches</h3><button class="btn small" data-rs="clear">Clear all</button></div>
        <div class="mlib-list">${list.map((s) => `<div class="mrs"><button class="mrs-q" data-rq="${esc(s.q)}">${I("search")} ${esc(s.q)}</button><button class="circle" data-rx="${esc(s.q)}" title="Remove">${I("x")}</button></div>`).join("")}</div>`
        : `<p class="dim">Your recent searches show here.</p>`) + `<div id="mbrowse"></div>`;
      // Browse all: In season, moods, genres and decades as colour tiles
      const th = ((await B.musicThemes().catch(() => ({}))).themes || []).slice();
      if (!B.alive(nav) || box.value.trim().length >= 2 || !$("#mbrowse")) return;
      const order = ["season", "lofi", "mood", "genre", "decade", "throwback"];
      th.sort((p, q) => (order.indexOf(p.group) + 1 || 99) - (order.indexOf(q.group) + 1 || 99));
      const COLS = ["#b83b5e", "#2e86ab", "#e07a1f", "#6a4c93", "#1b998b", "#c0392b", "#3d5a80", "#8e7d2b", "#9b2c6e", "#2d6a4f"];
      const hue = (k) => COLS[[...String(k)].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7) % COLS.length];
      if (th.length) $("#mbrowse").innerHTML = `<h3 class="msub">Browse all</h3><div class="mbrowse">${th.map((t) => `<a class="mbtile" style="background:${hue(t.key)}" href="#/mix/${enc(t.key)}">${esc(t.name)}</a>`).join("")}</div>`;
    };
    const run = async () => {
      const q = box.value.trim();
      if (q.length < 2) return recentHtml();
      const mine = ++ticket;
      const st = `searchTerm=${enc(q)}`;
      const [songs, artists, albums, pls] = await Promise.all([
        items(`${st}&IncludeItemTypes=Audio&Limit=12&${SONG_FIELDS}`).catch(() => []),
        items(`${st}&IncludeItemTypes=MusicArtist&Limit=10`).catch(() => []),
        items(`${st}&IncludeItemTypes=MusicAlbum&Limit=12`).catch(() => []),
        items(`${st}&IncludeItemTypes=Playlist&MediaTypes=Audio&Limit=8&Fields=ChildCount`).catch(() => []),
      ]);
      if (mine !== ticket || !B.alive(nav)) return;
      const none = !songs.length && !artists.length && !albums.length && !pls.length;
      // The one big Top result: an artist whose name fits, else the first song, album or playlist
      const starts = (x) => (x.Name || "").toLowerCase().startsWith(q.toLowerCase());
      const top = artists.find(starts) || songs.find(starts) || artists[0] || songs[0] || albums[0] || pls[0];
      const topTag = top && top.Type === "Audio" ? "button" : "a";
      const topHtml = top ? `<h3 class="msub">Top result</h3><${topTag} class="mtop" ${top.Type === "Audio" ? `type="button" data-topsong` : `href="#/${top.Type === "MusicArtist" ? "artist" : top.Type === "MusicAlbum" ? "album" : "playlist"}/${top.Id}"`}>
          <span class="mtop-art ${top.Type === "MusicArtist" ? "round" : ""}" style="${cover(top, 200) ? `background-image:url('${cover(top, 200)}')` : ""}"></span>
          <span><b>${esc(top.Name)}</b><small>${esc(top.Type === "MusicArtist" ? "Artist" : top.Type === "MusicAlbum" ? `Album · ${top.AlbumArtist || ""}` : top.Type === "Playlist" ? "Playlist" : `Song · ${artistOf(top)}`)}</small></span></${topTag}>` : "";
      res.innerHTML = (none ? `<p class="dim">Nothing on Benflix by that name yet.</p>` : topHtml + (songs.length ? `<h3 class="msub">Songs</h3><div class="msongs" id="ms">${songs.map((s, i) => songRow(s, i, { cover: true, artist: true })).join("")}</div>` : "")
        + B.row("Artists", artists.map(artistCard)) + B.row("Albums", albums.map(albumCard)) + B.row("Playlists", pls.map(playlistCard))) + `<div id="mwords"></div><div id="mnothere"></div>`;
      if (songs.length) wireSongs($("#ms"), songs, "Search", null);
      const tp = $("[data-topsong]"); if (tp) tp.addEventListener("click", () => { keep(); M.playList([top], 0, false, "Search"); M.sourceId = null; });
      // Music redesign 3 (lyrics search): a line you remember finds the song
      if (q.split(/\s+/).length >= 2) {
        B.call("GET", `api/music/lyricsearch?q=${enc(q)}`).then(async (j) => {
          const hits = ((j && j.songs) || []).slice(0, 6);
          const have = new Set(songs.map((x) => x.Id));
          const found = [];
          for (const h of hits) {
            const got = (await items(`searchTerm=${enc(h.title)}&IncludeItemTypes=Audio&Limit=5&${SONG_FIELDS}`).catch(() => [])).find((x) => songKey(x.Name) === songKey(h.title)
              && (!h.artist || (x.Artists || []).some((a) => a.toLowerCase().includes(h.artist.toLowerCase()) || h.artist.toLowerCase().includes(a.toLowerCase()))));
            if (got && !have.has(got.Id) && !found.some((f) => f.s.Id === got.Id)) found.push({ s: got, line: h.line });
          }
          if (mine !== ticket || !B.alive(nav) || !$("#mwords") || !found.length) return;
          const list = found.map((f) => f.s);
          $("#mwords").innerHTML = `<h3 class="msub">Songs with these words</h3><div class="msongs" id="mwl">${found.map((f, i) => songRow(f.s, i, { cover: true, artist: true }) + `<div class="mword-line dim">\u201C${esc(f.line)}\u201D</div>`).join("")}</div>`;
          wireSongs($("#mwl"), list, "Search", null);
          if (none) { const d = res.querySelector("p.dim"); if (d && /Nothing on Benflix/.test(d.textContent)) d.remove(); }
        }).catch(() => {});
      }
      // Not on Benflix yet: songs from the music catalogue for these words, with a preview and Ask
      const cat = await B.call("GET", `api/music/search?q=${enc(q)}`).catch(() => null);
      if (mine !== ticket || !B.alive(nav) || !$("#mnothere")) return;
      const out = ((cat && cat.songs) || []).filter((c) => !c.have).slice(0, 6);
      if (out.length) {
        $("#mnothere").innerHTML = `<h3 class="msub mask-h">Not on Benflix yet</h3><div class="msongs" id="mnh">${out.map((c) => ghostRow(c, null, true)).join("")}</div>`;
        wireGhosts($("#mnh"), () => keep());
      } else if (none) $("#mnothere").innerHTML = `<p class="dim"><a href="#/requests?tab=music&q=${enc(q)}">Ask for music</a> to look further.</p>`;
    };
    box.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(run, 300); });
    box.addEventListener("keydown", (e) => { if (e.key === "Enter") keep(); });
    res.addEventListener("click", (e) => {
      const q = e.target.closest("[data-rq]"); if (q) { box.value = q.dataset.rq; run(); return; }
      const x = e.target.closest("[data-rx]"); if (x) { meSave("api/music/me/search", { q: x.dataset.rx, remove: true }).then(recentHtml); return; }
      if (e.target.closest("[data-rs=clear]")) { meSave("api/music/me/search", { clear: true }).then(recentHtml); return; }
      // Opening or playing anything from the results keeps the words in Recent searches
      if (e.target.closest("a, .srow")) keep();
    });
    run();
    box.focus();
  });

  // ---------- Music redesign 3: a song you asked for came in: a corner card offers to play it next (once) ----------
  let arrivedAt = 0;
  const arrivals = async () => {
    if (!B.me || B.me.kids || Date.now() - arrivedAt < 300000) return;
    arrivedAt = Date.now();
    const key = `benflix.musicArrived.${B.uid()}`;
    let seen = null;
    try { seen = JSON.parse(localStorage.getItem(key) || "null"); } catch (e) {}
    const st = await B.call("GET", "api/music/status").catch(() => null);
    if (!st || !st.requests) return;
    const name = ((B.me && B.me.name) || "").toLowerCase();
    const mine = st.requests.filter((r) => r.state === "ready" && (r.kind || "song") === "song" && (!r.who || r.who.toLowerCase() === name));
    const fresh = mine.filter((r) => !(seen || []).includes(r.id));
    try { localStorage.setItem(key, JSON.stringify((seen || []).concat(fresh.map((r) => r.id)).slice(-300))); } catch (e) {}
    if (!seen || !fresh.length) return; // the first time, songs already in are never offered
    const r = fresh[0];
    const got = (await items(`searchTerm=${enc(r.title)}&IncludeItemTypes=Audio&Limit=8&${SONG_FIELDS}`).catch(() => [])).find((x) => songKey(x.Name) === songKey(r.title));
    if (!got) return;
    B.corner({ id: `music-arrived-${r.id}`, title: `"${r.title}" is here`, line: "Click to play it next", img: r.cover || "", run: () => M.playNext(got) });
  };
  window.addEventListener("hashchange", () => { if (/^#\/(music|msearch|mlibrary)/.test(location.hash)) arrivals(); });
  setTimeout(() => { if (/^#\/(music|msearch|mlibrary)/.test(location.hash)) arrivals(); }, 1500);

  // ---------- the Now playing panel on the right of Music pages (approved r3-web3, wide windows) ----------
  let panel;
  const onMusicPage = () => /^#\/(music|msearch|mlibrary|mthemes|mgrid|songs|liked|album|artist|playlist|mix|upnext)/.test(location.hash || "");
  const drawPanel = () => {
    const s = M.current();
    const want = !!s && onMusicPage() && window.innerWidth >= 1280 && !B.me.kids;
    document.body.classList.toggle("has-mnp", want);
    if (!want) { if (panel) panel.hidden = true; return; }
    if (!panel) {
      panel = document.createElement("aside");
      panel.className = "mnp";
      panel.addEventListener("click", (e) => {
        const a = e.target.closest("[data-p]"); if (!a) return;
        if (a.dataset.p === "open") openNow();
        if (a.dataset.p === "like") M.like();
        if (a.dataset.p === "queue") B.go("#/upnext");
        if (a.dataset.p === "x") { panelOff = true; drawPanel(); }
      });
      document.body.appendChild(panel);
    }
    if (panelOff) { panel.hidden = true; document.body.classList.remove("has-mnp"); return; }
    panel.hidden = false;
    const art = cover(s, 500);
    const next = M.queue.slice(M.index + 1, M.index + 4);
    panel.innerHTML = `<div class="mnp-top"><b>Now playing</b><button class="circle" data-p="x" title="Hide">${I("x")}</button></div>
      <button class="mnp-cover" data-p="open" title="Open Now playing" ${art ? `style="background-image:url('${art}')"` : ""}></button>
      <div class="mnp-words"><span><b>${esc(s.Name || "")}</b><small>${esc(artistOf(s))}</small></span><button class="circle mheart ${M.liked.has(s.Id) ? "on" : ""}" data-p="like" title="Like">${I("heart")}</button></div>
      <div class="mnp-card"><div class="mnp-h"><b>Next in queue</b><button class="btn small" data-p="queue">Open queue</button></div>
      ${next.length ? next.map((n) => `<div class="mnp-n"><span class="qa" style="${cover(n, 80) ? `background-image:url('${cover(n, 80)}')` : ""}"></span><span><b>${esc(n.Name || "")}</b><small>${esc(artistOf(n))}</small></span></div>`).join("") : `<p class="dim">${esc(M.thenTitle ? `Then a mix like ${M.thenTitle}` : "Then a mix like this song")}</p>`}</div>`;
  };
  let panelOff = false;
  M.onDraw = drawPanel;
  window.addEventListener("hashchange", () => setTimeout(drawPanel, 30));
  window.addEventListener("resize", () => drawPanel());

  // ---------- grids (See all, Browse by) ----------
  B.route(/^\/mgrid\/(\w+)(?:\/([\w-]+))?/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home"); // no music on Kids (like the TV and phone)
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
  // Music redesign 3 (part 4): Smart shuffle mixes in a song like these (from your music) after every 3 songs
  const smartShuffle = async (list, o) => {
    const have = new Set(list.map((x) => x.Id));
    const seeds = list.slice().sort(() => Math.random() - 0.5).slice(0, 3);
    const extra = [];
    for (const x of seeds) {
      const j = await B.get(`/Items/${enc(x.Id)}/InstantMix?UserId=${B.uid()}&Limit=15&${SONG_FIELDS}`).catch(() => null);
      ((j && j.Items) || []).forEach((y) => { if (y.Type === "Audio" && !have.has(y.Id)) { have.add(y.Id); extra.push(y); } });
    }
    const mixed = [], order = list.slice().sort(() => Math.random() - 0.5), more = extra.sort(() => Math.random() - 0.5);
    order.forEach((x, i) => { mixed.push(x); if (i % 3 === 2 && more.length) mixed.push(Object.assign({}, more.shift(), { _smart: true })); });
    M.playList(mixed, 0, false, `${o.title} · Smart shuffle`, o.place); M.sourceId = o.sourceId; openNow();
    B.toast(extra.length ? "Smart shuffle: songs like these join in after every 3 songs" : "Shuffling (no similar songs found yet)");
  };
  // Music redesign 3: Find and Sort on playlists and Liked songs, style chips on Liked songs
  const SORTS = ["Custom order", "Title", "Artist", "Album", "Recently added"];
  const STYLE_WORDS = [["Rap", ["hip hop", "hip-hop", "rap", "trap", "drill"]], ["R&B", ["r&b", "rnb", "soul", "funk"]], ["Metal", ["metal", "metalcore", "hardcore"]],
    ["Rock", ["rock", "grunge", "alternative", "punk"]], ["Country", ["country", "americana", "bluegrass"]], ["Dance", ["electronic", "edm", "dance", "house", "techno", "trance", "dubstep"]],
    ["Latin", ["latin", "reggaeton", "salsa"]], ["Indie", ["indie", "folk", "acoustic", "singer-songwriter"]], ["Pop", ["pop", "k-pop", "j-pop", "synthpop"]],
    ["Jazz", ["jazz", "blues", "swing"]], ["Classical", ["classical", "soundtrack", "score", "orchestral"]]]
    .map(([n, w]) => [n, new RegExp("(?<![a-z0-9])(" + w.map((x) => x.replace(/[&+-]/g, (c) => "\\" + c)).join("|") + ")(?![a-z0-9])")]);
  const styleOf = (x) => { const g = (x.Genres || []).join(" | ").toLowerCase(); if (!g) return null; const hit = STYLE_WORDS.find(([, rx]) => rx.test(g)); return hit ? hit[0] : null; };
  const likedStyles = (songs) => {
    const n = {}; songs.forEach((x) => { const st = styleOf(x); if (st) n[st] = (n[st] || 0) + 1; });
    return Object.keys(n).filter((k) => n[k] >= 3).sort((p, q) => n[q] - n[p]).slice(0, 6);
  };
  const collection = async (nav, o) => {
    const songs = o.songs;
    const mins = Math.round(songs.reduce((t, s) => t + (s.RunTimeTicks || 0), 0) / B.TICKS / 60);
    const discs = new Set(songs.map((s) => s.ParentIndexNumber || 1));
    const liked = o.title === "Liked songs";
    const tools = !o.album && songs.length > 1;
    const styles = liked ? likedStyles(songs) : [];
    let find = "", sortAt = 0, style = "", shown = songs;
    const toolsHtml = tools ? `<div class="mtools">
        ${styles.length ? `<div class="mchips mstyles">${["", ...styles].map((st) => `<button class="chip ${st === "" ? "on" : ""}" style="--accent:var(--music)" data-style="${esc(st)}">${esc(st || "All")}</button>`).join("")}</div>` : ""}
        <div class="mtools-row"><input class="field mfind" id="mfind" type="search" placeholder="${liked ? "Find in Liked songs" : "Find in playlist"}" autocomplete="off">
        <button class="btn small" id="msort">${I("list")} Sort: Custom order</button></div></div>` : "";
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage" id="mpg">
      <div class="mstick-wrap"><div class="mstick" hidden><b>${esc(o.title)}</b><button class="btn white small" data-stick="play">${I("play")} Play</button></div></div>
      ${hero(Object.assign({}, o, { line: `${o.line ? esc(o.line) + " · " : ""}${o.total > songs.length ? `${songs.length} of ${o.total}` : songs.length} songs · ${B.len(mins)}` }))}
      ${toolsHtml}<div class="msongs" id="ms"></div><div id="mbelow"></div></div>`;
    B.wireTop();
    const pick = () => {
      const words = find.trim().toLowerCase();
      const idx = songs.map((x, i) => i).filter((i) => {
        const x = songs[i];
        return (!words || (x.Name || "").toLowerCase().includes(words) || (x.Artists || []).some((a) => a.toLowerCase().includes(words)) || (x.Album || "").toLowerCase().includes(words)) && (!style || styleOf(x) === style);
      });
      const by = (f) => (p, q) => (f(songs[p]) < f(songs[q]) ? -1 : f(songs[p]) > f(songs[q]) ? 1 : 0);
      const sort = SORTS[sortAt];
      if (sort === "Title") idx.sort(by((x) => (x.Name || "").toLowerCase()));
      if (sort === "Artist") idx.sort(by((x) => (x.AlbumArtist || (x.Artists || [])[0] || "").toLowerCase()));
      if (sort === "Album") idx.sort(by((x) => (x.Album || "").toLowerCase() + String(x.IndexNumber || 0).padStart(4, "0")));
      if (sort === "Recently added") idx.sort(by((x) => x.DateCreated || "")).reverse();
      return idx;
    };
    const drawSongs = () => {
      const plain = !find.trim() && !sortAt && !style;
      shown = pick().map((i) => songs[i]);
      let body = "", lastDisc = null;
      shown.forEach((s, i) => {
        if (plain && discs.size > 1 && o.album && s.ParentIndexNumber !== lastDisc) { lastDisc = s.ParentIndexNumber; body += `<div class="mdisc">Disc ${esc(lastDisc || 1)}</div>`; }
        body += songRow(s, i, { number: o.album ? s.IndexNumber || i + 1 : null, cover: !o.album, artist: !o.album });
      });
      const old = $("#ms"), fresh = document.createElement("div");
      fresh.className = "msongs"; fresh.id = "ms";
      fresh.innerHTML = body || (songs.length ? `<p class="dim">${find.trim() ? `No songs here match "${esc(find.trim())}".` : "No songs in this style here yet."}</p>` : `<p class="dim">No songs here yet.</p>`);
      old.replaceWith(fresh);
      // Moving and removing songs only make sense in the playlist's own order
      wireSongs(fresh, shown, o.title, o.sourceId, plain ? o.extra : null, o.place);
    };
    drawSongs();
    if (tools) {
      let t = 0;
      $("#mfind").addEventListener("input", (e) => { clearTimeout(t); t = setTimeout(() => { find = e.target.value; drawSongs(); }, 200); });
      $("#msort").addEventListener("click", (e) => { sortAt = (sortAt + 1) % SORTS.length; e.currentTarget.innerHTML = `${I("list")} Sort: ${SORTS[sortAt]}`; drawSongs(); });
      const chips = $(".mstyles");
      if (chips) chips.addEventListener("click", (e) => {
        const c = e.target.closest("[data-style]"); if (!c) return;
        style = c.dataset.style; $$("[data-style]", chips).forEach((x) => x.classList.toggle("on", x === c)); drawSongs();
      });
    }
    const playShown = (shuffle) => { M.playList(shown, 0, shuffle, o.title, o.place); M.sourceId = o.sourceId; refreshRows($("#ms"), shown); openNow(); };
    // Once the top has scrolled away, a slim bar with the name and Play sticks to the top
    const stick = $(".mstick"), heroEl = $(".mhero");
    if (stick && heroEl && "IntersectionObserver" in window) {
      const io = new IntersectionObserver((es) => { stick.hidden = es[0].isIntersecting || !shown.length; });
      io.observe(heroEl);
      B.onLeave(() => io.disconnect());
      stick.addEventListener("click", (e) => { if (e.target.closest("[data-stick]")) playShown(false); });
    }
    if (o.art || (o.collage && o.collage[0])) tintTop(o.art || o.collage[0], $("#mpg"));
    else if (liked) $("#mpg").style.backgroundImage = "linear-gradient(rgba(108,74,190,.45), transparent 460px)";
    $(".mhero-btns").addEventListener("click", (e) => {
      const b = e.target.closest("[data-h]"); if (!b) return;
      if ((b.dataset.h === "play" || b.dataset.h === "shuffle") && !shown.length) return B.toast(liked ? "No liked songs yet. Press the heart on a song to add it here." : "There are no songs here yet.");
      // Ben: Play or Shuffle goes straight to Now playing
      if (b.dataset.h === "play") playShown(false);
      if (b.dataset.h === "shuffle") playShown(true);
      if (b.dataset.h === "heart") { const on = !b.classList.contains("on"); M.like(o.sourceId, on).then(() => b.classList.toggle("on", on)); }
      if (b.dataset.h === "more") {
        const menu = [{ label: "Add all to Up next", value: "all" }, { label: "Add all to a playlist", value: "pl" }].concat(o.menu || []);
        if (o.place && (o.place.kind === "album" || o.place.kind === "playlist")) menu.push({ label: "Send to someone", value: "send" });
        if (o.place && (o.place.kind === "playlist" || o.place.kind === "liked") && shown.length > 3) menu.unshift({ label: "Smart shuffle (adds songs like these)", value: "smart" });
        B.dropdown(b, menu, null, (v) => {
          if (v === "send") setTimeout(() => sendTo(b, o.place), 0);
          else if (v === "smart") smartShuffle(shown, o);
          else if (v === "all") shown.forEach((s) => M.addToUpNext(s));
          else if (v === "pl") addToPlaylist(b, shown);
          else (o.menu || []).forEach((x) => { if (x.value === v && x.run) x.run(); });
        });
      }
    });
    setTimeout(() => { const p = $("[data-h=play]"); if (p) p.focus({ preventScroll: true }); }, 30);
  };
  B.route(/^\/album\/([\w-]+)/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home"); // no music on Kids (like the TV and phone)
    const u = B.uid();
    const [al, songs] = await Promise.all([B.get(`/Users/${u}/Items/${m[1]}`), items(`ParentId=${enc(m[1])}&IncludeItemTypes=Audio&SortBy=ParentIndexNumber,IndexNumber,SortName&${SONG_FIELDS}`)]);
    if (!B.alive(nav)) return;
    const artist = (al.AlbumArtists || [])[0];
    // Only some songs of the album (asked for one by one): "3 of 12 songs" (waits at most 1.5 s)
    const size = await Promise.race([
      B.call("GET", `api/music/albumsize?artist=${enc(al.AlbumArtist || "")}&album=${enc(al.Name || "")}`).then((j) => j || {}).catch(() => ({})),
      new Promise((r) => setTimeout(() => r({}), 1500)),
    ]);
    const total = size.songs || 0;
    // Music redesign 3: the whole album in its real order, the songs Benflix doesn't have greyed with a preview and Ask
    const cat = total > songs.length && size.id ? await B.call("GET", `api/music/album?id=${enc(size.id)}`).then((j) => (j && j.songs) || []).catch(() => []) : [];
    const lines = [];
    if (cat.length) {
      const used = new Set(), byKey = {};
      songs.forEach((x, i) => { (byKey[songKey(x.Name)] = byKey[songKey(x.Name)] || []).push(i); });
      cat.forEach((c, n) => {
        const i = (byKey[songKey(c.title)] || []).find((k) => !used.has(k));
        if (i != null) { used.add(i); lines.push({ i }); } else if (!c.have) lines.push({ c, n: n + 1 });
      });
      songs.forEach((x, i) => { if (!used.has(i)) lines.push({ i }); });
    }
    const missing = lines.filter((l) => l.c).length;
    // Ben 2026-11-16: an album you only have some songs from can ask for the rest (Ben okays other profiles' asks)
    const rest = total > songs.length && size.id ? { label: missing ? `Ask for the other ${missing}` : B.me.admin ? "Ask for the rest" : "Ask Ben for the rest", icon: "plus", onClick: (e, el) => B.busy(el, async () => {
      await B.call("POST", "api/music/request", { kind: "album", id: +size.id });
      el.disabled = true;
      return B.me.admin ? `Asked for the rest of ${al.Name}.` : `Asked Ben for the rest of ${al.Name}.`;
    }) } : null;
    if (!B.alive(nav)) return;
    // Redesign 2: albums use the shared title page (square cover; opens on Songs)
    const mins = Math.round(songs.reduce((t, s) => t + (s.RunTimeTicks || 0), 0) / B.TICKS / 60);
    const discs = new Set(songs.map((s) => s.ParentIndexNumber || 1));
    let body = "", lastDisc = null;
    if (missing) lines.forEach((l) => { body += l.c ? ghostRow(l.c, l.n, false) : songRow(songs[l.i], l.i, { number: songs[l.i].IndexNumber || l.i + 1, cover: false, artist: false }); });
    else songs.forEach((s, i) => {
      if (discs.size > 1 && s.ParentIndexNumber !== lastDisc) { lastDisc = s.ParentIndexNumber; body += `<div class="mdisc">Disc ${esc(lastDisc || 1)}</div>`; }
      body += songRow(s, i, { number: s.IndexNumber || i + 1, cover: false, artist: false });
    });
    let liked = !!(al.UserData || {}).IsFavorite;
    const alPlace = { kind: "album", id: al.Id, name: al.Name };
    const playAll = (shuffle) => { M.playList(songs, 0, shuffle, al.Name, alPlace); M.sourceId = al.Id; refreshRows($("#ms"), songs); openNow(); };
    const none = songs.length ? "" : "No songs here yet";
    const tp = B.titlePage({
      title: al.Name,
      color: MUSIC,
      cover: { url: cover(al, 900), ratio: 1 },
      info: [songs.length === 1 ? "Single" : "Album", al.AlbumArtist, al.ProductionYear, total > songs.length ? `You have ${songs.length} of ${total} songs` : `${songs.length} songs`, songs.length ? B.len(mins) : ""],
      buttons: [
        { label: "Play", icon: "play", off: none, onClick: () => playAll(false) },
        { label: "Shuffle", icon: "shuffle", off: none, onClick: () => playAll(true) },
        rest,
        { label: liked ? "Liked" : "Like", icon: "heart", onClick: (e, el) => { liked = !liked; M.like(al.Id, liked).then(() => { el.querySelector("span").textContent = liked ? "Liked" : "Like"; }); } },
        { label: "More", icon: "more", onClick: (e, el) => B.tpMore(al.Name, [
          { label: "Add all to Up next", icon: "list", onClick: () => songs.forEach((x) => M.addToUpNext(x)) },
          { label: "Add all to a playlist", icon: "plus", onClick: () => addToPlaylist(el, songs) },
          artist ? { label: `Go to ${artist.Name}`, icon: "user", href: `#/artist/${artist.Id}` } : null,
        ]) },
      ],
      overviewFirst: false,
      open: "Songs",
      tabs: [
        { name: "Songs", id: "tpSongs", keep: true, html: `<div class="msongs" id="ms">${body || `<p class="dim">No songs here yet.</p>`}</div>` },
        { name: "More by this artist", id: "tpMoreBy", html: "" },
        { name: "Similar albums", id: "tpSimilar", html: "" },
      ],
    });
    // An album has no story: the Overview tab isn't used
    const ov = $('.tp-tab[data-tab="tpOverview"]'); if (ov) ov.remove();
    wireSongs($("#ms"), songs, al.Name, al.Id, null, alPlace);
    wireGhosts($("#ms"));
    tintTop(cover(al, 120), $(".details.tp"));
    if (artist) {
      const more = (await items(`IncludeItemTypes=MusicAlbum&AlbumArtistIds=${enc(artist.Id)}&SortBy=ProductionYear&SortOrder=Descending&Limit=20`).catch(() => [])).filter((a) => a.Id !== al.Id);
      if (more.length && B.alive(nav)) tp.pane("tpMoreBy").innerHTML = `<div class="grid tp-grid">${more.map(albumCard).join("")}</div>`;
    }
    const similar = ((await B.get(`/Items/${enc(al.Id)}/Similar?UserId=${u}&Limit=16`).catch(() => ({}))).Items || []).filter((a) => a.Type === "MusicAlbum");
    if (similar.length && B.alive(nav)) tp.pane("tpSimilar").innerHTML = `<div class="grid tp-grid">${similar.map(albumCard).join("")}</div>`;
  });
  B.route(/^\/playlist\/([\w-]+)/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home"); // no music on Kids (like the TV and phone)
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
      kick: "PLAYLIST", title: pl.Name, line: "", art, collage: art || covers.length < 2 ? null : covers.concat(covers).slice(0, 4), songs, sourceId: pl.Id, place: { kind: "playlist", id: pl.Id, name: pl.Name },
      menu: canEdit ? [{ label: "Rename", value: "rename", run: async () => { const n = await B.askText("Rename playlist", "", pl.Name); if (n) { await B.jf("POST", `/Playlists/${enc(pl.Id)}`, { Name: n }); B.render(); } } }] : [],
      extra: canEdit ? (i) => [
        { label: "Move up", value: "up", run: async () => { if (i > 0) { await B.jf("POST", `/Playlists/${enc(pl.Id)}/Items/${enc(songs[i].PlaylistItemId)}/Move/${i - 1}`); B.render(); } } },
        { label: "Move down", value: "down", run: async () => { if (i < songs.length - 1) { await B.jf("POST", `/Playlists/${enc(pl.Id)}/Items/${enc(songs[i].PlaylistItemId)}/Move/${i + 1}`); B.render(); } } },
        { label: "Remove from this playlist", value: "rm", run: async () => { await B.jf("DELETE", `/Playlists/${enc(pl.Id)}/Items?EntryIds=${enc(songs[i].PlaylistItemId)}`); B.render(); } },
      ] : null,
    });
    if (canEdit) ownAdds(nav, pl, songs);
  });

  // Music redesign 3: your own playlists end with Songs to add (songs like the ones in it that aren't on Benflix).
  // Asking for one keeps a note on Tower, and the next time the playlist opens after it's in, it joins by itself.
  const ownAdds = async (nav, pl, songs) => {
    const key = String(pl.Id).replace(/-/g, "");
    try {
      const mine = await B.musicMe(true);
      const waiting = ((mine.adds || {})[key]) || [];
      let joined = 0;
      for (const w of waiting) {
        const found = (await items(`searchTerm=${enc(w.title)}&IncludeItemTypes=Audio&Limit=10`).catch(() => [])).find((x) => songKey(x.Name) === songKey(w.title)
          && (!w.artist || (x.Artists || []).some((a) => a.toLowerCase().includes(w.artist.toLowerCase()) || w.artist.toLowerCase().includes(a.toLowerCase()))));
        if (!found) continue;
        if (!songs.some((x) => x.Id === found.Id)) { await B.jf("POST", `/Playlists/${enc(pl.Id)}/Items?Ids=${enc(found.Id)}&UserId=${B.uid()}`); joined++; }
        await meSave("api/music/me/add", { playlist: key, artist: w.artist, title: w.title, remove: true });
      }
      if (joined && B.alive(nav)) { B.toast(joined === 1 ? "A song you asked for joined this playlist" : `${joined} songs you asked for joined this playlist`); return B.render(); }
    } catch (e) { /* older Tower: no Songs to add yet */ }
    const box = $("#mbelow"); if (!box || !songs.length) return;
    box.innerHTML = `<h3 class="msub">Songs to add</h3><p class="dim">Looking for songs like these...</p>`;
    const seeds = songs.slice().sort(() => Math.random() - 0.5).slice(0, 3);
    const found = new Map();
    for (const x of seeds) {
      const got = await B.call("GET", `api/music/similar?artist=${enc(x.AlbumArtist || (x.Artists || [])[0] || "")}&title=${enc(x.Name || "")}`).catch(() => null);
      ((got && got.songs) || []).filter((c) => !c.have).forEach((c) => { if (!found.has(c.id)) found.set(c.id, c); });
    }
    if (!B.alive(nav)) return;
    const list = [...found.values()].slice(0, 10);
    if (!list.length) { box.innerHTML = ""; return; }
    box.innerHTML = `<h3 class="msub">Songs to add</h3><p class="dim">Songs like the ones here. Ask for one and it joins this playlist once it's in.${B.me.admin ? "" : " Ben okays asks first."}</p>
      <div class="msongs" id="madds">${list.map((c) => ghostRow(c, null, true)).join("")}</div>`;
    wireGhosts($("#madds"), (id) => { const c = found.get(id); if (c) meSave("api/music/me/add", { playlist: key, artist: c.artist, title: c.title }); });
  };
  // Browse by > Songs: every song, A to Z
  B.route(/^\/songs$/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home"); // no music on Kids (like the TV and phone)
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
    await collection(nav, { kick: "EVERY SONG ON BENFLIX", title: "All songs", line: "", collage: covers.length >= 2 ? covers.concat(covers).slice(0, 4) : null, songs, sourceId: null, place: { kind: "songs", id: "", name: "All songs" } });
  });
  B.route(/^\/liked$/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home"); // no music on Kids (like the TV and phone)
    const songs = await items(`IncludeItemTypes=Audio&Filters=IsFavorite&SortBy=DateCreated&SortOrder=Descending&Limit=3000&${SONG_FIELDS.replace("ChildCount", "ChildCount,Genres")}`);
    if (!B.alive(nav)) return;
    const covers = [...new Set(songs.map((s) => s.AlbumId).filter(Boolean))].slice(0, 4).map((id) => B.img(id, "Primary", { h: 300 }));
    await collection(nav, { kick: "PLAYLIST", title: "Liked songs", line: "Every song you gave a heart", collage: covers.length >= 2 ? covers.concat(covers).slice(0, 4) : null, songs, sourceId: null, place: { kind: "liked", id: "", name: "Liked songs" } });
  });
  B.route(/^\/mix\/([\w-]+)/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home"); // no music on Kids (like the TV and phone)
    const key = decodeURIComponent(m[1]);
    const mix = (await B.musicMixes()).find((x) => x.key === key) || ((await B.musicThemes().catch(() => ({}))).themes || []).find((x) => x.key === key);
    if (!mix) return B.go("#/music");
    if (mix.catalogue) return catalogueMix(nav, mix);
    const got = mix.songs.length ? await items(`Ids=${enc(mix.songs.join(","))}&${SONG_FIELDS}`) : [];
    const byId = {}; got.forEach((s) => { byId[s.Id] = s; });
    const songs = mix.songs.map((id) => byId[id]).filter(Boolean);
    if (!B.alive(nav)) return;
    const cv = B.mixCovers(mix.covers);
    const covers = cv.length > 1 ? cv.map((c) => B.img(c, "Primary", { h: 300 })) : null;
    const art = cv.length === 1 ? B.img(cv[0], "Primary", { h: 600 }) : "";
    // Ben 2026-11-16: Not for this playlist takes a song out of this made-for-you or themed playlist for good (this profile)
    const notHere = (i) => [{ label: "Not for this playlist", value: "nothere", run: async () => {
      const s = songs[i]; if (!s) return;
      try {
        await B.call("POST", "api/music/mixes/hide", { key: mix.key, id: s.Id });
        Object.keys(B.cache).filter((k) => k.startsWith("music-mixes-") || k.startsWith("music-themes-")).forEach((k) => delete B.cache[k]);
        B.toast(`Took ${s.Name} out of ${mix.name}`);
        B.render();
      } catch (e) { B.toast(e.message || "Couldn't take that song out. Try again in a minute."); }
    } }];
    await collection(nav, { kick: mix.group ? "PLAYLIST BY THEME" : "MADE FOR YOU", title: mix.name, line: mix.note ? `${mix.line} · ${mix.note}` : mix.line, art, collage: covers, songs, sourceId: null, extra: notHere, place: { kind: "mix", id: mix.key, name: mix.name } });
    if (mix.adds) themeAdds(nav, mix);
  });

  // Music redesign 3 (part 4): Discover weekly and Release radar are songs that aren't on Benflix yet. Play plays
  // their 30 second previews one after another; the heart asks for a song.
  const catalogueMix = async (nav, mix) => {
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage" id="mpg"><div class="mhero"><div class="mhero-words"><div class="mkick">MADE FOR YOU</div><h1>${esc(mix.name)}</h1><div class="dim">${esc(mix.line)}</div>
      <div class="mhero-btns"><button class="btn white" data-h="pv">${I("play")} Play previews</button><button class="btn" data-h="stop">${I("x")} Stop</button></div></div></div>
      <p class="dim">Not on Benflix yet. Hear 30 seconds of each, one after another, and press Ask for it on the ones you like.${B.me.admin ? "" : " Ben okays asks first."}</p>
      <div class="msongs" id="mcat">${B.spinner()}</div></div>`;
    B.wireTop();
    $("#mpg").style.backgroundImage = "linear-gradient(rgba(108,74,190,.45), transparent 460px)";
    const got = await B.call("GET", `api/music/theme?key=${enc(mix.key)}`).catch(() => null);
    if (!B.alive(nav)) return;
    const list = (got && got.songs) || [];
    const box = $("#mcat");
    if (!list.length) { box.innerHTML = `<p class="dim">${mix.key === "release-radar" ? "No new songs from the artists you play this week. Follow a few artists to get more here." : "Play some more music and new picks show up here next Monday."}</p>`; return; }
    // Ben 2026-11-17: a clear Ask for it button (the heart that used to ask read like a Like)
    const askBtn = (x) => x.asked ? `<span class="sl">${esc(x.asked)}</span>` : `<button class="btn small white" data-mixask="${esc(String(x.id))}">${I("plus")} Ask for it</button>`;
    box.innerHTML = list.map((x, i) => `<div class="srow"><span class="sn">${x.cover ? `<img class="sc" src="${esc(x.cover)}" alt="" loading="lazy" decoding="async">` : `<span class="sc"></span>`}</span><span class="st"><b>${esc(x.title)}</b><small>${esc([x.artist, x.album].filter(Boolean).join(" · "))}</small></span>${B.previewBtn(x.id)}<span data-slot>${askBtn(x)}</span></div>`).join("");
    const waiting = () => list.filter((x) => !x.asked).map((x) => x.id);
    $(".mhero-btns").addEventListener("click", (e) => {
      const b = e.target.closest("[data-h]"); if (!b) return;
      if (b.dataset.h === "pv") { if (!waiting().length) return B.toast("You've asked for all of these already."); B.previewAll(waiting()); }
      if (b.dataset.h === "stop") B.previewStop();
    });
    box.addEventListener("click", async (e) => {
      // data-mixask, not data-ask: data-ask is the movie/show Ask panel's (requests.js), which opened a "(400)" panel here
      const b = e.target.closest("[data-mixask]"); if (!b || b.disabled) return;
      const x = list.find((y) => String(y.id) === b.dataset.mixask); if (!x) return;
      b.disabled = true;
      try {
        const r = await B.call("POST", "api/music/request", { kind: "song", id: x.id });
        x.asked = (r && r.words) || "Asked for";
        B.previewStop();
        b.parentNode.innerHTML = `<span class="sl">${esc(x.asked)}</span>`;
        B.toast(`${x.asked}: ${x.title}`);
      } catch (err) { b.disabled = false; B.toast(/\(\d{3}\)/.test((err && err.message) || "") || !(err && err.message) ? `Couldn't ask for ${x.title} right now. Try again in a minute.` : err.message); }
    });
  };

  // Every profile but Kids (Ben okays the others' asks): Songs to add under a themed playlist. Asking for one downloads it, and Tower puts it in this theme.
  const themeAdds = async (nav, mix) => {
    const box = $("#mbelow"); if (!box) return;
    box.innerHTML = `<h3 class="msub">Songs to add</h3><p class="dim">Looking for songs that fit...</p>`;
    const got = await B.call("GET", `api/music/theme?key=${enc(mix.key)}`).catch(() => null);
    if (!B.alive(nav)) return;
    const list = (got && got.songs) || [];
    if (!list.length) { box.innerHTML = ""; return; }
    const btn = (s) => s.asked ? `<span class="sl">${esc(s.asked)}</span>` : `<button class="btn small white" data-addsong="${esc(s.id)}">${I("plus")} Ask for it</button>`;
    box.innerHTML = `<h3 class="msub">Songs to add</h3><p class="dim">Songs that fit ${esc(mix.name)}. Ask for one and it joins this playlist once it's in.${B.me.admin ? "" : " Ben okays asks first."}</p>
      <div class="msongs">${list.map((s) => `<div class="srow"><span class="sn">${s.cover ? `<img class="sc" src="${esc(s.cover)}" alt="" loading="lazy" decoding="async">` : `<span class="sc"></span>`}</span><span class="st"><b>${esc(s.title)}</b><small>${esc(s.artist)}</small></span>${B.previewBtn(s.id)}<span data-slot="${esc(s.id)}">${btn(s)}</span></div>`).join("")}</div>`;
    box.addEventListener("click", async (e) => {
      const b = e.target.closest("[data-addsong]"); if (!b) return;
      b.disabled = true;
      try {
        const r = await B.call("POST", "api/music/request", { kind: "song", id: +b.dataset.addsong, theme: mix.key });
        B.previewStop();
        b.parentNode.innerHTML = `<span class="sl">${esc((r && r.words) || "Asked for")}</span>`;
      } catch (err) {
        b.disabled = false;
        B.toast((err && err.message) || "Couldn't ask for that song. Try again in a minute.");
      }
    });
  };

  // ---------- artist page (Music redesign 3, p3-artist) ----------
  // Big photo with the name across it and how many of their songs you have. Play, Shuffle, Follow, Artist radio,
  // More (Don't play this artist in my mixes). Popular = their top 5 worldwide (missing greyed with preview + Ask),
  // Your songs by them, Discography newest first (Albums / Singles, Not on Benflix tags), Fans also like.
  B.route(/^\/artist\/([\w-]+)/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home"); // no music on Kids (like the TV and phone)
    const u = B.uid();
    const [ar, top, albums, count] = await Promise.all([
      B.get(`/Users/${u}/Items/${m[1]}`),
      items(`IncludeItemTypes=Audio&ArtistIds=${enc(m[1])}&SortBy=PlayCount,CommunityRating,SortName&SortOrder=Descending&Limit=10&${SONG_FIELDS}`),
      items(`IncludeItemTypes=MusicAlbum&AlbumArtistIds=${enc(m[1])}&SortBy=ProductionYear,SortName&SortOrder=Descending&Fields=ChildCount`),
      B.get(`/Items?UserId=${u}&IncludeItemTypes=Audio&Recursive=true&ArtistIds=${enc(m[1])}&Limit=0&EnableTotalRecordCount=true`).then((j) => j.TotalRecordCount || 0).catch(() => 0),
    ]);
    if (!B.alive(nav)) return;
    const full = albums.filter((a) => (a.ChildCount || 3) > 2), singles = albums.filter((a) => (a.ChildCount || 3) <= 2);
    let follow = !!(ar.UserData || {}).IsFavorite;
    const mine = await B.musicMe();
    let noMix = (mine.noartists || []).some((n) => n.toLowerCase() === (ar.Name || "").toLowerCase());
    const arPlace = { kind: "artist", id: ar.Id, name: ar.Name };
    const pic = cover(ar, 900);
    const counts = () => [count ? `${count} ${count === 1 ? "song" : "songs"} on Benflix` : "", follow ? "Following" : ""].filter(Boolean).join(" · ");
    const yours = top.filter((x) => ((x.UserData || {}).PlayCount || 0) > 0).slice(0, 5);
    B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage" id="mpg">
      <div class="martist" style="${pic ? `background-image:url('${pic}')` : ""}"><div class="martist-words"><div class="dim" id="mcounts">${esc(counts())}</div><h1>${esc(ar.Name)}</h1></div></div>
      <div class="mhero-btns"><button class="btn white" data-h="play">${I("play")} Play</button><button class="btn" data-h="shuffle">${icon("shuffle")} Shuffle</button>
        <button class="btn ${follow ? "on" : ""}" data-h="follow">${follow ? I("check") + " Following" : I("plus") + " Follow"}</button>
        <button class="btn" data-h="radio">${I("note")} Artist radio</button><button class="btn" data-h="more">${I("more")}</button></div>
      <h3 class="msub">Popular</h3><div class="msongs" id="ms">${top.slice(0, 5).map((s, i) => songRow(s, i, { cover: true })).join("")}</div>
      <div id="myours"></div><div id="mdisc"></div><div id="mfans"></div></div>`;
    B.wireTop();
    tintTop(cover(ar, 120), $("#mpg"));
    let popular = top.slice(0, 5);
    let cat = null;
    wireSongs($("#ms"), popular, `${ar.Name}: popular`, ar.Id, null, arPlace);
    const allSongs = () => items(`IncludeItemTypes=Audio&ArtistIds=${enc(ar.Id)}&Limit=5000&${SONG_FIELDS}`);
    $(".mhero-btns").addEventListener("click", async (e) => {
      const b = e.target.closest("[data-h]"); if (!b) return;
      if (b.dataset.h === "play" || b.dataset.h === "shuffle") {
        const all = await allSongs().catch(() => top);
        if (!all.length) return B.toast(`No songs by ${ar.Name} on Benflix yet.`);
        M.playList(all, 0, b.dataset.h === "shuffle", ar.Name, arPlace); M.sourceId = ar.Id; openNow();
      }
      if (b.dataset.h === "radio") {
        try {
          const j = await B.get(`/Items/${enc(ar.Id)}/InstantMix?UserId=${u}&Limit=60&${SONG_FIELDS}`);
          const list = ((j && j.Items) || []).filter((x) => x.Type === "Audio");
          if (!list.length) return B.toast("Couldn't start a radio for this artist yet.");
          M.playList(list, 0, false, `${ar.Name} radio`); M.sourceId = null; openNow();
        } catch (err) { B.toast("Couldn't start a radio for this artist. Try again in a minute."); }
      }
      if (b.dataset.h === "follow") {
        follow = !follow;
        await B.jf(follow ? "POST" : "DELETE", `/Users/${u}/FavoriteItems/${enc(ar.Id)}`);
        b.classList.toggle("on", follow);
        b.innerHTML = follow ? I("check") + " Following" : I("plus") + " Follow";
        $("#mcounts").textContent = counts();
        B.toast(follow ? `Following ${ar.Name}: new music shows up on Home` : `Stopped following ${ar.Name}`);
      }
      if (b.dataset.h === "more") {
        const menu = [{ label: noMix ? `Play ${ar.Name} in my mixes again` : "Don't play this artist in my mixes", value: "nomix" }, { label: "Add all to Up next", value: "all" }, { label: "Send to someone", value: "send" }];
        if (cat && cat.artist) menu.push({ label: `Ask for more by ${ar.Name}`, value: "ask" });
        B.dropdown(b, menu, null, async (v) => {
          if (v === "nomix") {
            noMix = !noMix;
            await meSave("api/music/me/noartist", { name: ar.Name, on: noMix });
            B.toast(noMix ? `${ar.Name} won't play in your mixes any more` : `${ar.Name} can play in your mixes again`);
          }
          if (v === "all") (await allSongs().catch(() => [])).forEach((x) => M.addToUpNext(x));
          if (v === "send") setTimeout(() => sendTo(b, arPlace), 0);
          if (v === "ask") B.go(`#/requests?tab=music&mopen=artist:${cat.artist.id}`);
        });
      }
    });
    setTimeout(() => { const p = $("[data-h=play]"); if (p) p.focus({ preventScroll: true }); }, 30);
    if (yours.length) {
      $("#myours").innerHTML = `<h3 class="msub">Your songs by ${esc(ar.Name)}</h3><div class="msongs" id="myrs">${yours.map((s, i) => songRow(s, i, { cover: true })).join("")}</div>`;
      wireSongs($("#myrs"), yours, `${ar.Name}: your songs`, ar.Id, null, arPlace);
    }
    // Discography, Benflix's albums first while the catalogue answers
    const discHtml = (albumsHere, singlesHere, missA, missS, on) => {
      const card = (x) => x.Id ? albumCard(x) : `<a class="poster mnot" href="#/requests?tab=music&mopen=album:${esc(String(x.id))}" title="${esc(x.title)}"><div class="im" style="aspect-ratio:1;${x.cover ? `background-image:url('${esc(x.cover)}')` : ""}"><span class="mnot-tag">Not on Benflix</span></div><div class="cap">${esc(x.title)}</div><div class="cap2">${esc(x.year || "")}</div></a>`;
      const year = (x) => +(x.ProductionYear || x.year || 0);
      const listA = albumsHere.concat(missA).sort((p, q) => year(q) - year(p)), listS = singlesHere.concat(missS).sort((p, q) => year(q) - year(p));
      if (!listA.length && !listS.length) return "";
      const showS = on === "singles" && listS.length || !listA.length;
      return `<h3 class="msub">Discography</h3>${listA.length && listS.length ? `<div class="mchips" id="mdchips"><button class="chip ${showS ? "" : "on"}" style="--accent:var(--music)" data-d="albums">Albums</button><button class="chip ${showS ? "on" : ""}" style="--accent:var(--music)" data-d="singles">Singles</button></div>` : ""}
        <div class="grid tp-grid">${(showS ? listS : listA).map(card).join("")}</div>`;
    };
    let missA = [], missS = [], dOn = "albums";
    const drawDisc = () => { $("#mdisc").innerHTML = discHtml(full, singles, missA, missS, dOn); };
    drawDisc();
    $("#mdisc").addEventListener("click", (e) => { const c = e.target.closest("[data-d]"); if (c) { dOn = c.dataset.d; drawDisc(); } });
    // The music catalogue (through Tower): Popular worldwide, the whole discography, Fans also like
    cat = await B.call("GET", `api/music/artistfind?name=${enc(ar.Name || "")}`).catch(() => null);
    if (!cat || !cat.artist || !B.alive(nav)) return;
    const all = await allSongs().catch(() => []);
    if (!B.alive(nav)) return;
    const byKey = {}; all.forEach((x) => { const k = songKey(x.Name); if (!byKey[k]) byKey[k] = x; });
    popular = [];
    const lines = (cat.songs || []).map((c) => { const have = byKey[songKey(c.title)]; if (have) popular.push(have); return have ? { have } : { c }; });
    if (lines.length) {
      const old = $("#ms"), fresh = document.createElement("div");
      fresh.className = "msongs"; fresh.id = "ms";
      fresh.innerHTML = lines.map((l, n) => (l.have ? songRow(l.have, popular.indexOf(l.have), { cover: true }) : ghostRow(l.c, n + 1, true))).join("");
      old.replaceWith(fresh);
      wireSongs(fresh, popular, `${ar.Name}: popular`, ar.Id, null, arPlace);
      wireGhosts(fresh);
    }
    const here = new Set(albums.map((a) => songKey(a.Name)));
    const miss = (cat.albums || []).filter((a) => !a.have && !here.has(songKey(a.title)));
    missS = miss.filter((a) => a.type === "single" || a.type === "ep" || (a.songs > 0 && a.songs <= 2));
    missA = miss.filter((a) => !missS.includes(a));
    drawDisc();
    const fans = cat.related || [];
    if (fans.length) {
      const local = await Promise.all(fans.slice(0, 12).map((a) => items(`searchTerm=${enc(a.name)}&IncludeItemTypes=MusicArtist&Limit=5`).then((l) => l.find((x) => (x.Name || "").toLowerCase() === a.name.toLowerCase())).catch(() => null)));
      if (!B.alive(nav)) return;
      const cards = fans.slice(0, 12).map((a, i) => ({ a, l: local[i] })).sort((p, q) => (p.l ? 0 : 1) - (q.l ? 0 : 1))
        .map(({ a, l }) => `<a class="poster" href="${l ? `#/artist/${l.Id}` : `#/requests?tab=music&mopen=artist:${esc(String(a.id))}`}" title="${esc(a.name)}"><div class="im" style="aspect-ratio:1;border-radius:50%;${a.picture ? `background-image:url('${esc(a.picture)}')` : ""}"></div><div class="cap">${esc(a.name)}</div><div class="cap2">${l ? "On Benflix" : "None yet"}</div></a>`);
      $("#mfans").innerHTML = B.row("Fans also like", cards);
    }
  });

  // ---------- Up next (approved q-full) ----------
  B.route(/^\/upnext$/, async () => {
    const draw = () => {
      const now = M.current();
      const rest = M.queue.slice(M.index + 1).map((s, k) => ({ s, i: M.index + 1 + k }));
      const added = rest.filter((x) => M.added.has(x.s._key)), then = rest.filter((x) => !M.added.has(x.s._key)).slice(0, 100);
      const row = (x, mine) => `<div class="srow" data-q="${x.i}" draggable="true"><span class="sn"><span class="sc" style="background-image:url('${cover(x.s, 80)}')"></span></span><span class="st"><b>${esc(x.s.Name)}</b><small>${esc(artistOf(x.s))}</small></span><span class="sl">${len(x.s.RunTimeTicks)}</span>
        <button class="sm" data-up="${x.i}" title="Move up">${I("up")}</button><button class="sm" data-down="${x.i}" title="Move down">${I("down")}</button><button class="sm" data-rm="${x.i}" title="Take out">${I("x")}</button></div>`;
      B.page().innerHTML = B.top("Music", MUSIC) + `<div class="mpage"><h1 class="mtitle">Queue</h1><p class="dim">Drag a song to move it, or use the arrows.</p>
        ${now ? `<h3 class="msub">Now playing</h3><div class="msongs"><div class="srow now" data-open><span class="sn"><span class="bars"><i></i><i></i><i></i></span></span><span class="st"><b>${esc(now.Name)}</b><small>${esc(artistOf(now))}</small></span><span class="sl">${len(now.RunTimeTicks)}</span></div></div>` : `<p class="dim">Nothing is playing.</p>`}
        ${added.length ? `<h3 class="msub">Next in queue</h3><div class="msongs">${added.map((x) => row(x, true)).join("")}</div>` : ""}
        ${then.length ? `<h3 class="msub">${M.thenTitle ? "Next from: " + esc(M.thenTitle) : "Next from your music"}</h3><div class="msongs">${then.map((x) => row(x, false)).join("")}</div>` : now ? `<p class="dim">Nothing waiting. When this song ends, a mix like it starts by itself.</p>` : ""}
      </div>`;
      B.wireTop();
      $(".mpage").onclick = onClick;
      // Drag and drop: a song dropped on another takes its place
      let from = null;
      $$(".srow[draggable]").forEach((r) => {
        r.addEventListener("dragstart", (e) => { from = +r.dataset.q; r.classList.add("dragging"); e.dataTransfer.effectAllowed = "move"; });
        r.addEventListener("dragend", () => r.classList.remove("dragging"));
        r.addEventListener("dragover", (e) => { e.preventDefault(); r.classList.add("dropto"); });
        r.addEventListener("dragleave", () => r.classList.remove("dropto"));
        r.addEventListener("drop", (e) => {
          e.preventDefault();
          const to = +r.dataset.q;
          if (from == null || from === to || to <= M.index) return;
          const [song] = M.queue.splice(from, 1); M.queue.splice(to, 0, song);
          from = null; draw();
        });
      });
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
