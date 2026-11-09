/* Benflix web: Requests (asking for movies and shows that aren't on Benflix yet). Talks to Seerr
   through the Jellyfin Enhanced plugin on the server, the same way the TV and phone app does. */
"use strict";

const seerr = (path, method, body) => B.jf(method || "GET", "/JellyfinEnhanced/jellyseerr" + path, body);
B.seerr = seerr;
const tmdbImg = (p, w) => (p ? `https://image.tmdb.org/t/p/${w || "w342"}${p}` : "");
B.tmdbImg = tmdbImg;
B.seerrMe = () => B.cached("seerr-me", 600000, () => seerr("/user-status").catch(() => null));
const onBenflix = (r) => r.mediaInfo && (r.mediaInfo.status === 5 || r.mediaInfo.status === 4);
B.onBenflix = onBenflix;
const asked = (r) => r.mediaInfo && (r.mediaInfo.status === 2 || r.mediaInfo.status === 3 || (r.mediaInfo.requests || []).length);
const isAnime = (r) => (r.genreIds || []).includes(16) && (r.originalLanguage === "ja" || r.mediaType === "tv");
B.askCard = (r) => {
  const t = r.title || r.name || "";
  const year = (r.releaseDate || r.firstAirDate || "").slice(0, 4);
  const img = tmdbImg(r.posterPath);
  B.items["s" + r.mediaType + r.id] = r;
  return `<button class="poster askcard" data-ask="${esc(r.mediaType)}:${esc(r.id)}" data-id="s${esc(r.mediaType + r.id)}" title="${esc(t)}">
    <div class="im" ${img ? `style="background-image:url('${img}')"` : ""}>${img ? "" : `<div class="noart">${esc(t)}</div>`}${asked(r) ? '<span class="newp">Asked</span>' : ""}</div>
    <div class="cap">${esc(t)}<small>${esc(year)}</small></div></button>`;
};

// The "Ask for it" side panel (shows always let you pick seasons)
B.askPanel = async (type, id) => {
  const p = B.side(B.spinner());
  try {
    const d = await seerr(`/${type}/${id}`);
    const t = d.title || d.name;
    const year = (d.releaseDate || d.firstAirDate || "").slice(0, 4);
    const seasons = type === "tv" ? (d.seasons || []).filter((s) => s.seasonNumber > 0) : [];
    const have = new Set(((d.mediaInfo && d.mediaInfo.seasons) || []).filter((s) => s.status >= 2).map((s) => s.seasonNumber));
    const status = d.mediaInfo ? d.mediaInfo.status : 1;
    const jfId = d.mediaInfo && d.mediaInfo.jellyfinMediaId;
    p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button>
      ${d.backdropPath ? `<div style="aspect-ratio:16/9;border-radius:12px;background:#222 center/cover url('${tmdbImg(d.backdropPath, "w780")}');margin-bottom:16px"></div>` : ""}
      <h3>${esc(t)}</h3><p class="dim">${esc([year, type === "tv" ? "Show" : "Movie", d.runtime ? B.len(d.runtime) : "", (d.genres || []).slice(0, 3).map((g) => g.name).join(", "), d.originalLanguage && d.originalLanguage !== "en" ? "Spoken in " + B.langName(d.originalLanguage) : ""].filter(Boolean).join(" · "))}</p>
      ${d.voteAverage ? `<div class="scores"><span><b>${d.voteAverage.toFixed(1)}</b>TMDB</span></div>` : ""}
      <p>${esc(d.overview || "")}</p>
      ${status === 5 && jfId ? `<a class="btn white" href="#/${type === "tv" ? "show" : "movie"}/${jfId}">${I("play")} It's on Benflix</a>`
        : `${seasons.length ? `<h4 class="dim" style="margin:16px 0 8px">Seasons</h4>${seasons.map((s) => `<button class="opt ${have.has(s.seasonNumber) ? "" : "on"}" data-season="${s.seasonNumber}" ${have.has(s.seasonNumber) ? "disabled" : ""}>${esc(s.name || "Season " + s.seasonNumber)}<small>${have.has(s.seasonNumber) ? "Asked or on Benflix" : (s.episodeCount || "") + " episodes"}</small></button>`).join("")}` : ""}
        ${status === 2 || status === 3 ? `<p class="dim">Already asked for. ${status === 3 ? "It's on its way." : "Waiting for a yes."}</p>` : ""}
        <div class="acts" style="margin-top:16px"><button class="btn white" id="askGo">${I("plus")} Ask for it</button></div>`}`;
    const go = $("#askGo", p);
    if (go) go.onclick = () => B.busy(go, async () => {
      const body = { mediaType: type, mediaId: +id };
      if (type === "tv") {
        const picked = $$("[data-season].on", p).map((b) => +b.dataset.season);
        if (!picked.length) throw new Error("Tick at least one season.");
        body.seasons = picked;
      }
      await seerr("/request", "POST", body);
      B.closeAll();
      B.cache["seerr-mine"] = null;
      return `Asked for ${t}. You'll see it in Your requests.`;
    });
    p.addEventListener("click", (e) => { const b = e.target.closest("[data-season]"); if (b && !b.disabled) b.classList.toggle("on"); });
  } catch (e) {
    p.innerHTML = `<button class="circle x" data-close>${I("x")}</button><h3>Couldn't open that</h3><p class="dim">${esc(e.message)}</p>`;
  }
};
document.addEventListener("click", (e) => {
  const c = e.target.closest("[data-ask]");
  if (!c) return;
  const [type, id] = c.dataset.ask.split(":");
  B.askPanel(type, id);
});

const reqWords = (r) => {
  const ms = r.media ? r.media.status : 0;
  if (r.status === 3) return ["Said no", "bad"];
  if (ms === 5) return ["On Benflix", "ok"];
  if (ms === 4) return ["Part of it is on Benflix", "ok"];
  if (r.status === 1) return ["Waiting for a yes", "check"];
  if (ms === 3) return ["On its way", "check"];
  return ["Asked", "check"];
};

B.route(/^\/requests/, async (m, nav) => {
  if (B.me.kids) return B.go("#/home");
  const tab = B.q("tab") || "movies";
  B.page().innerHTML = B.top("Requests", null, { solid: false }) + `<div class="wrap" style="padding-bottom:6px"><div class="h1">Ask for something new</div>
    <p class="sub">${tab === "music" ? "Songs and albums for your Music folder. Ask, and Tower finds them." : "Movies and shows that aren't on Benflix yet. Ask, and Ben's server finds them."}</p>
    <div class="row" style="margin-bottom:10px">${[["movies", "Movies"], ["shows", "Shows"], ["anime", "Anime"]].concat(B.me.admin ? [["music", "Music"]] : []).map(([k, l]) => `<a class="chip ${k === tab ? "on" : ""}" href="#/requests?tab=${k}">${l}</a>`).join("")}
      <input class="field" id="rq" style="max-width:420px;padding:9px 16px;font-size:16px;border-radius:99px" placeholder="Search for something to ask for" value="${esc(B.q("q") || "")}"></div></div>
    <div class="rows" id="rows">${B.spinner()}</div>`;
  B.wireTop();
  if (tab === "music" && B.me.admin) return B.musicTab(nav);
  const me = await B.seerrMe();
  if (!B.alive(nav)) return;
  if (!me || !me.active || !me.userFound) {
    $("#rows").innerHTML = `<div class="wrap"><div class="note">Requests aren't set up for ${esc(B.me.name)} yet. ${B.me.admin ? "Check the Jellyfin Enhanced plugin's Seerr settings in Jellyfin's Dashboard." : "Ask Ben to turn them on for you."}</div></div>`;
    return;
  }
  const kind = tab === "movies" ? "movie" : "tv";
  const search = async (text) => {
    if (!text.trim()) return drawRows();
    $("#rows").innerHTML = B.spinner();
    const r = await seerr(`/search?query=${enc(text.trim())}&page=1`).catch(() => ({ results: [] }));
    if (!B.alive(nav)) return;
    const res = (r.results || []).filter((x) => x.mediaType === "movie" || x.mediaType === "tv");
    const mv = res.filter((x) => x.mediaType === "movie" && !isAnime(x)), tv = res.filter((x) => x.mediaType === "tv" && !isAnime(x)), an = res.filter(isAnime);
    const people = (r.results || []).filter((x) => x.mediaType === "person");
    $("#rows").innerHTML = [B.row("Movies", mv.map(B.askCard)), B.row("Shows", tv.map(B.askCard)), B.row("Anime", an.map(B.askCard)),
      people.length ? `<section class="rowx"><h3>People</h3><div class="wrap dim" style="padding-top:0">${people.slice(0, 8).map((p) => esc(p.name)).join(", ")}</div></section>` : ""].join("") || `<div class="wrap empty">Nothing found for “${esc(text)}”. Check the spelling, or try the original name.</div>`;
  };
  const drawRows = async () => {
    $("#rows").innerHTML = B.spinner();
    const base = tab === "anime" ? "/discover/tv/genre/16" : `/discover/${tab === "movies" ? "movies" : "tv"}`;
    const [mine, waiting, trending, upcoming, popular2] = await Promise.all([
      seerr(`/request?take=40&skip=0&sort=added&requestedBy=${me.jellyseerrUserId}`).catch(() => null),
      B.me.admin ? seerr(`/request?take=40&skip=0&sort=added&filter=pending`).catch(() => null) : null,
      seerr(`${base}?page=1`).catch(() => null),
      tab === "anime" ? seerr(`${base}?page=2`).catch(() => null) : seerr(`/discover/${tab === "movies" ? "movies" : "tv"}/upcoming?page=1`).catch(() => null),
      seerr(`${base}?page=3`).catch(() => null),
    ]);
    if (!B.alive(nav)) return;
    // Request lists only carry the TMDB number, so look up each title
    await Promise.all([mine, waiting].flatMap((l) => ((l && l.results) || []).slice(0, 12)).map(async (r) => {
      const md = r.media || {};
      if (md.title || md.name || !md.tmdbId) return;
      const d = await B.cached(`seerr-${r.type}-${md.tmdbId}`, 3600000, () => seerr(`/${r.type}/${md.tmdbId}`)).catch(() => null);
      if (d) md.title = d.title || d.name;
    }));
    if (!B.alive(nav)) return;
    const notHere = (l) => ((l && l.results) || []).filter((x) => !onBenflix(x) && (tab === "anime" || !isAnime(x)));
    const reqRow = (title, list, admin) => {
      const items = ((list && list.results) || []).filter((r) => (r.type === kind) || tab === "anime" && r.type === "tv");
      if (!items.length) return "";
      return `<section class="rowx"><h3>${esc(title)}</h3><div class="wrap" style="padding-bottom:0"><div class="list">${items.slice(0, 12).map((r) => {
        const md = r.media || {}, [word, lvl] = reqWords(r);
        return `<div class="item"><div class="grow"><div class="t">${esc(md.title || md.name || md.externalServiceSlug || ("TMDB " + md.tmdbId))}</div><div class="s">${admin ? esc((r.requestedBy && (r.requestedBy.displayName || r.requestedBy.jellyfinUsername)) || "Someone") + " asked · " : ""}<span class="word-${lvl}">${esc(word)}</span> · ${esc(B.when(r.createdAt))}</div></div>
          <div class="acts">${admin ? `<button class="btn small white" data-yes="${r.id}">Yes</button><button class="btn small" data-no="${r.id}">No</button>` : `<button class="btn small" data-ask="${esc(r.type)}:${esc(md.tmdbId)}">Open</button>`}</div></div>`;
      }).join("")}</div></div></section>`;
    };
    $("#rows").innerHTML = [
      B.me.admin ? reqRow("Waiting for you", waiting, true) : "",
      reqRow("Your requests", mine, false),
      B.row(tab === "anime" ? "Popular anime" : "Trending", notHere(trending).map(B.askCard)),
      B.row(tab === "anime" ? "More anime" : "Coming soon", notHere(upcoming).map(B.askCard)),
      B.row("More to ask for", notHere(popular2).map(B.askCard)),
    ].join("");
  };
  $("#rows").addEventListener("click", (e) => {
    const y = e.target.closest("[data-yes]"), n = e.target.closest("[data-no]");
    if (!y && !n) return;
    const id = (y || n).dataset.yes || (y || n).dataset.no;
    B.busy(y || n, async () => { await B.jf("POST", `/JellyfinEnhanced/arr/requests/${id}/${y ? "approve" : "decline"}`).catch(() => {}); drawRows(); return y ? "Said yes. It's on its way." : "Said no."; });
  });
  let t;
  $("#rq").oninput = (e) => { clearTimeout(t); t = setTimeout(() => search(e.target.value), 350); };
  if (B.q("q")) search(B.q("q")); else drawRows();
});

// ---------- Music (only Ben): ask for songs and albums, playlists, the Music Drop folder, tidy-up ----------
// Talks to the helper's /api/music (benflix_music.py), which hands the downloading to Lidarr on Tower.
const music = (method, path, body) => B.call(method, "/api/music" + path, body);
const songLine = (s, n) => `<div class="item">${n ? `<b class="dim" style="min-width:28px">${n}</b>` : ""}<div style="width:48px;height:48px;border-radius:6px;flex:none;background:var(--pg3) center/cover url('${esc(s.cover || "")}')"></div>
  <div class="grow"><div class="t">${esc(s.title)}</div><div class="s">${esc(s.artist)}${s.album ? " · " + esc(s.album) : ""}${s.seconds ? " · " + Math.floor(s.seconds / 60) + ":" + String(s.seconds % 60).padStart(2, "0") : ""}</div></div>
  <div class="acts">${s.asked || s.have ? `<span class="dim">${esc(s.asked || "In your music")}</span>` : `<button class="btn small white" data-msong="${s.id}" data-malbum="${s.album_id}" data-mname="${esc(s.title)}" data-maname="${esc(s.album)}">Ask for it</button>`}</div></div>`;
const albumCard = (a) => `<button class="poster" data-mopen="album:${a.id}" title="${esc(a.title)}"><div class="im" style="aspect-ratio:1;${a.cover ? `background-image:url('${esc(a.cover)}')` : ""}"></div>
  <div class="cap">${esc(a.title)}<small>${esc(a.asked || [a.year, a.type === "single" ? "Single" : a.type === "ep" ? "EP" : ""].filter(Boolean).join(" · "))}</small></div></button>`;
const genreCard = (g) => `<button class="poster" data-mgenre="${g.id}" title="${esc(g.name)}"><div class="im" style="aspect-ratio:1;${g.picture ? `background-image:url('${esc(g.picture)}')` : ""}"></div><div class="cap">${esc(g.name)}</div></button>`;
// Top songs, New releases, Top albums, Top artists (and Genres on the main page)
const discoverHtml = (d, title, allSongs) => {
  const songs = allSongs ? d.songs : d.songs.slice(0, 10);
  return (d.songs.length ? `<div class="wrap"><h3 class="section-title">${esc(title)}</h3><div class="list">${songs.map((x, i) => songLine(x, i + 1)).join("")}</div>
      ${!allSongs && d.songs.length > 10 ? `<p><button class="btn" data-mallsongs>See all ${d.songs.length}</button></p>` : ""}</div>` : "")
    + B.row("New releases", (d.new || []).map(albumCard)) + B.row("Top albums", (d.albums || []).map(albumCard))
    + B.row("Top artists", (d.artists || []).map(artistCard)) + (d.genres ? B.row("Genres", d.genres.map(genreCard)) : "");
};
const artistCard = (a) => `<button class="poster" data-mopen="artist:${a.id}" title="${esc(a.name)}"><div class="im" style="aspect-ratio:1;border-radius:50%;${a.picture ? `background-image:url('${esc(a.picture)}')` : ""}"></div><div class="cap">${esc(a.name)}</div></button>`;

B.musicTab = async (nav) => {
  $("#rq").placeholder = "Search any song, album or artist";
  const rows = $("#rows");
  rows.innerHTML = `<div id="mfound"></div><div id="mdisc"></div><div class="wrap" id="mstatus">${B.spinner()}</div>`;
  let searching = false, tidyFolder = null, disc = null, allSongs = false;
  const drawDiscover = async (again) => {
    if (!disc || again) {
      try { disc = await music("GET", "/discover"); } catch (e) { if (B.alive(nav) && !disc) $("#mdisc").innerHTML = `<div class="wrap"><p class="dim">Couldn't load what's popular right now. ${esc(e.message)}</p></div>`; return; }
    }
    if (B.alive(nav)) $("#mdisc").innerHTML = discoverHtml(disc, "Top songs right now", allSongs);
  };
  const drawStatus = async () => {
    let st;
    try { st = await music("GET", "/status"); } catch (e) { if (B.alive(nav)) $("#mstatus").innerHTML = `<div class="note">${esc(e.message)}</div>`; return; }
    if (!B.alive(nav) || searching) return;
    const t = st.tidy || { folders: [], lines: [], undo_files: [] };
    if (!t.folders.includes(tidyFolder)) tidyFolder = t.folders[0] || null;
    $("#mstatus").innerHTML = `${(st.problems || []).map((p) => `<div class="note yellow">${esc(p)}</div>`).join("")}
      <h3 class="section-title">Your music requests${st.requests.length ? ` (${st.requests.length})` : ""}</h3>
      ${st.requests.length ? `<div class="list">${st.requests.map((r) => `<div class="item"><div style="width:48px;height:48px;border-radius:6px;flex:none;background:var(--pg3) center/cover url('${esc(r.cover || "")}')"></div>
        <div class="grow"><div class="t">${esc(r.title)}</div><div class="s">${esc(r.artist)}${r.kind === "album" ? " · Album" : ""} · <span class="${r.bad ? "word-bad" : r.state === "ready" ? "word-ok" : "word-check"}">${esc(r.words)}</span></div>
        <div class="bar"><i class="${r.bad ? "bad" : ""}" style="width:${Math.round(r.bar * 100)}%"></i></div></div>
        <div class="acts">${r.can_retry ? `<button class="btn small white" data-mretry="${r.id}">Try again</button>` : ""}<button class="btn small" data-mremove="${r.id}">${r.state === "ready" ? "Done" : "Remove"}</button></div></div>`).join("")}</div>`
        : `<p class="dim">Nothing asked for yet. Search a song or album above.</p>`}
      <h3 class="section-title">Playlists</h3>
      <p class="dim">Paste a Spotify, YouTube Music or Deezer playlist link. Every song you don't have yet is asked for, and the same playlist is made in your music as they arrive.</p>
      <p><button class="btn white" id="mplaylist">Bring in a playlist</button></p>
      ${(st.playlists || []).length ? `<div class="list">${st.playlists.map((p) => `<div class="item"><div class="grow"><div class="t">${esc(p.name)}</div><div class="s">${esc(p.words)}</div><div class="bar"><i style="width:${Math.round(p.bar * 100)}%"></i></div></div>
        <div class="acts"><button class="btn small" data-mplremove="${p.id}">${p.done ? "Done" : "Remove"}</button></div></div>`).join("")}</div>` : ""}
      <h3 class="section-title">Music Drop folder</h3>
      <p class="dim">Put music files in <b>${esc(st.drop_folder)}</b>. Every few minutes they're sorted into Music by artist and album, with covers. Lossless files become 320 MP3s.</p>
      ${(st.drop_lines || []).length ? `<pre class="log">${esc(st.drop_lines.slice(-8).join("\n"))}</pre>` : ""}
      <h3 class="section-title" style="margin-top:22px">Tidy up old music</h3>
      <p class="dim">Sorts a folder (like the music SoulSync got) into Music/Artist/Album/01 - Song, fixes names, adds covers and puts songs you have twice in Music Leftovers. Press Check first to see what would move.</p>
      <div class="row" style="margin-bottom:10px">${t.folders.map((f) => `<button class="chip ${f === tidyFolder ? "on" : ""}" data-mfolder="${esc(f)}">${esc(f)}</button>`).join("")}</div>
      ${t.running ? `<p class="dim">${t.mode === "preview" ? "Checking…" : "Tidying…"}</p>` : tidyFolder ? `<div class="acts" style="justify-content:flex-start;margin-bottom:10px"><button class="btn" data-mtidy="preview">Check first</button><button class="btn white" data-mtidy="apply">Tidy it</button>${t.undo_files.length ? `<button class="btn" data-mtidy="undo">Undo last tidy-up</button>` : ""}</div>` : ""}
      ${t.result ? `<p>${esc(t.result)}</p>` : ""}${t.lines.length ? `<pre class="log">${esc(t.lines.slice(-200).join("\n"))}</pre>` : ""}`;
    rows.dataset.undo = t.undo_files[t.undo_files.length - 1] || "";
  };
  const search = async (text) => {
    const box = $("#mfound");
    searching = text.trim().length >= 2;
    $("#mstatus").style.display = searching ? "none" : "";
    $("#mdisc").style.display = searching ? "none" : "";
    if (!searching) { box.innerHTML = ""; drawStatus(); return; }
    box.innerHTML = `<div class="wrap">${B.spinner()}</div>`;
    let r;
    try { r = await music("GET", "/search?q=" + enc(text.trim())); } catch (e) { box.innerHTML = `<div class="wrap"><div class="note">${esc(e.message)}</div></div>`; return; }
    if (!B.alive(nav) || $("#rq").value.trim() !== text.trim()) return;
    const none = !r.songs.length && !r.albums.length && !r.artists.length;
    box.innerHTML = (r.songs.length ? `<div class="wrap"><h3 class="section-title">Songs</h3><div class="list">${r.songs.slice(0, 12).map((x) => songLine(x)).join("")}</div></div>` : "")
      + B.row("Albums", r.albums.map(albumCard)) + B.row("Artists", r.artists.map(artistCard))
      + (none ? `<div class="wrap empty">Nothing found. Try fewer words, or the artist's name.</div>` : "");
  };
  const openPage = async (kind, id) => {
    const p = B.side(B.spinner());
    try {
      if (kind === "album") {
        const d = await music("GET", "/album?id=" + id);
        p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button>
          <div style="width:180px;aspect-ratio:1;border-radius:12px;margin-bottom:14px;background:var(--pg3) center/cover url('${esc(d.album.cover || "")}')"></div>
          <h3>${esc(d.album.title)}</h3><p class="dim">${esc([d.album.artist, d.album.year].filter(Boolean).join(" · "))}</p>
          <div class="acts" style="justify-content:flex-start;margin:12px 0">${d.album.asked ? `<span class="dim">${esc(d.album.asked)}</span>` : `<button class="btn white" data-malbumonly="${d.album.id}" data-maname="${esc(d.album.title)}">Ask for the whole album</button>`}</div>
          <p class="dim">Or pick a song to ask for just that one.</p><div class="list">${d.songs.map((x) => songLine(x)).join("")}</div>`;
      } else {
        const d = await music("GET", "/artist?id=" + id);
        p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button><h3>${esc(d.artist.name)}</h3>
          ${d.songs.length ? `<h4 class="dim" style="margin:12px 0 8px">Best songs</h4><div class="list">${d.songs.map((x) => songLine(x)).join("")}</div>` : ""}
          <h4 class="dim" style="margin:12px 0 8px">Albums, EPs and singles</h4><div class="grid" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:12px">${d.albums.map(albumCard).join("")}</div>`;
      }
    } catch (e) {
      p.innerHTML = `<button class="circle x" data-close>${I("x")}</button><h3>Couldn't open that</h3><p class="dim">${esc(e.message)}</p>`;
    }
  };
  const openGenre = async (id) => {
    const p = B.side(B.spinner());
    try {
      const d = await music("GET", "/genre?id=" + id);
      p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button><h3>${esc(d.genre.name)}</h3>
        ${d.songs.length ? `<h4 class="dim" style="margin:12px 0 8px">Top ${esc(d.genre.name)} songs</h4><div class="list">${d.songs.map((x, i) => songLine(x, i + 1)).join("")}</div>` : ""}
        ${[["New releases", d.new], ["Top albums", d.albums]].filter(([, l]) => l && l.length).map(([t, l]) => `<h4 class="dim" style="margin:12px 0 8px">${t}</h4><div class="grid" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:12px">${l.map(albumCard).join("")}</div>`).join("")}
        ${d.artists.length ? `<h4 class="dim" style="margin:12px 0 8px">Artists</h4><div class="grid" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:12px">${d.artists.map(artistCard).join("")}</div>` : ""}`;
    } catch (e) {
      p.innerHTML = `<button class="circle x" data-close>${I("x")}</button><h3>Couldn't open that</h3><p class="dim">${esc(e.message)}</p>`;
    }
  };
  const ask = async (kind, id, name) => {
    await music("POST", "/request", { kind, id: +id });
    B.closeAll();
    const q = $("#rq").value;
    if (q.trim().length >= 2) search(q); else drawStatus();
    drawDiscover(true);
    return `Asked for ${name}. It shows under Your music requests.`;
  };
  // One click handler for the whole page and its side panels
  const onClick = (e) => {
    if (!B.alive(nav)) return document.removeEventListener("click", onClick);
    const b = e.target.closest("[data-msong],[data-malbumonly],[data-mopen],[data-mgenre],[data-mallsongs],[data-mretry],[data-mremove],[data-mplremove],[data-mfolder],[data-mtidy],#mplaylist");
    if (!b) return;
    const d = b.dataset;
    if (d.msong) {
      const s = B.side(`<h3>${esc(d.mname)}</h3><p class="dim">${esc(d.maname)}</p>
        <button class="opt" data-pick="song">Just this song</button><button class="opt" data-pick="album">The whole album</button>`);
      s.addEventListener("click", (ev) => {
        const pick = ev.target.closest("[data-pick]");
        if (pick) B.busy(pick, () => (pick.dataset.pick === "song" ? ask("song", d.msong, d.mname) : ask("album", d.malbum, d.maname)));
      });
    } else if (d.malbumonly) B.busy(b, () => ask("album", d.malbumonly, d.maname));
    else if (d.mopen) { const [k, id] = d.mopen.split(":"); openPage(k, id); }
    else if (d.mgenre) openGenre(d.mgenre);
    else if (b.hasAttribute("data-mallsongs")) { allSongs = true; drawDiscover(); }
    else if (d.mretry) B.busy(b, async () => { await music("POST", `/request/${d.mretry}/retry`); drawStatus(); return "Looking again."; });
    else if (d.mremove) B.busy(b, async () => { await music("POST", `/request/${d.mremove}/remove`); drawStatus(); });
    else if (d.mplremove) B.busy(b, async () => { await music("POST", `/playlist/${d.mplremove}/remove`); drawStatus(); });
    else if (d.mfolder) { tidyFolder = d.mfolder; drawStatus(); }
    else if (d.mtidy) {
      B.busy(b, async () => {
        const mode = d.mtidy;
        if (mode === "apply" && !(await B.ask(`Tidy ${tidyFolder}?`, "Everything is sorted into Music/Artist/Album. Songs you have twice go to Music Leftovers, and Undo puts it all back.", "Tidy it", "Not now"))) return;
        if (mode === "undo" && !(await B.ask("Undo the last tidy-up?", "The files go back where they were.", "Undo", "Not now"))) return;
        await music("POST", "/tidy", { mode, folder: tidyFolder || "", undo: mode === "undo" ? rows.dataset.undo : "" });
        drawStatus();
      });
    } else if (b.id === "mplaylist") {
      B.askText("Bring in a playlist", "Paste the playlist's link (Share > Copy link), or songs one per line like: Queen - Bohemian Rhapsody", "", { long: true, ok: "Bring it in" }).then((link) => {
        if (!link || !link.trim()) return;
        B.busy(null, async () => { const p = await music("POST", "/playlist", { link: link.trim() }); drawStatus(); return `Bringing in ${p.name}.`; });
      });
    }
  };
  document.addEventListener("click", onClick);
  B.onLeave(() => document.removeEventListener("click", onClick));
  let t;
  $("#rq").oninput = (e) => { clearTimeout(t); t = setTimeout(() => search(e.target.value), 400); };
  B.every(10000, () => { if (!searching) drawStatus(); });
  drawDiscover();
  if (B.q("q")) search(B.q("q")); else drawStatus();
};
