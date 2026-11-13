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
    <div class="im" ${img ? `style="background-image:url('${img}')"` : ""}>${img ? "" : `<div class="noart"><span class="nt">${esc(t)}</span></div>`}${asked(r) ? '<span class="newp">Asked</span>' : ""}</div>
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
  // Movies and shows open their full title page (js/ask.js); "Ask for it" there opens this panel
  if ((type === "movie" || type === "tv") && /^\d+$/.test(id || "")) return B.go(`#/ask/${type}/${id}`);
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

// Benflix (compact Requests, Ben 2026-11-10): posters come first. One slim line on top (door chips, search,
// Your requests and Waiting for you as small buttons that open a side list), then Top 10 and the other rows.
const detailsAnime = (d) => ((d.genres || []).some((g) => g.id === 16) || (d.genreIds || []).includes(16)) && d.originalLanguage === "ja";
B.route(/^\/requests/, async (m, nav) => {
  if (B.me.kids) return B.go("#/home");
  const tab = B.q("tab") || "movies";
  const media = tab !== "music" && tab !== "books";
  B.page().innerHTML = B.top("Requests", null, { solid: false }) + `<div class="wrap" style="padding-bottom:4px">
    <div class="row" style="margin-bottom:6px;align-items:center">${[["movies", "Movies"], ["shows", "Shows"], ["anime", "Anime"]].concat(B.me.admin ? [["music", "Music"]] : []).concat(B.booksTab ? [["books", "Books"]] : []).map(([k, l]) => `<a class="chip ${k === tab ? "on" : ""}" href="#/requests?tab=${k}">${l}</a>`).join("")}
      <input class="field" id="rq" style="max-width:420px;padding:9px 16px;font-size:16px;border-radius:99px" placeholder="${tab === "anime" ? "Search anime to ask for" : "Search for something to ask for"}" value="${esc(B.q("q") || "")}">
</div>
    ${media ? "" : `<p class="sub" style="margin:0 0 6px">${tab === "music" ? "Songs and albums for your Music folder. Ask, and Tower finds them." : "Books, audiobooks and manga. Ask, and Tower finds them."}</p>`}</div>
    <div class="rows" id="rows">${B.spinner()}</div>`;
  B.wireTop();
  if (tab === "music" && B.me.admin) return B.musicTab(nav);
  if (tab === "books" && B.booksTab) return B.booksTab(nav);
  const me = await B.seerrMe();
  if (!B.alive(nav)) return;
  if (!me || !me.active || !me.userFound) {
    $("#rows").innerHTML = `<div class="wrap"><div class="note">Requests aren't set up for ${esc(B.me.name)} yet. ${B.me.admin ? "Check the Jellyfin Enhanced plugin's Seerr settings in Jellyfin's Dashboard." : "Ask Ben to turn them on for you."}</div></div>`;
    return;
  }
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
  // Your requests / Waiting for you: only this tab's kind (anime requests sit on Anime, not Shows), with real names
  let lists = { mine: [], waiting: [] };
  const loadLists = async () => {
    const [mine, waiting] = await Promise.all([
      seerr(`/request?take=40&skip=0&sort=added&requestedBy=${me.jellyseerrUserId}`).catch(() => null),
      B.me.admin ? seerr(`/request?take=40&skip=0&sort=added&filter=pending`).catch(() => null) : null,
    ]);
    const pick = async (l) => {
      const items = ((l && l.results) || []).filter((r) => tab === "movies" ? r.type === "movie" : true);
      await Promise.all(items.map(async (r) => {
        const md = r.media || (r.media = {});
        if (!md.tmdbId) return;
        const d = await B.cached(`seerr-${r.type}-${md.tmdbId}`, 3600000, () => seerr(`/${r.type}/${md.tmdbId}`)).catch(() => null);
        if (d) { md.title = d.title || d.name || md.title; md.anime = detailsAnime(d); }
      }));
      return items.filter((r) => tab === "movies" ? !r.media.anime : tab === "shows" ? r.type === "tv" && !r.media.anime : r.media.anime);
    };
    lists = { mine: await pick(mine), waiting: await pick(waiting) };
  };
  const reqItem = (r, admin) => {
    const md = r.media || {}, [word, lvl] = reqWords(r);
    return `<div class="item"><div class="grow"><div class="t">${esc(md.title || md.name || "Name not found")}</div><div class="s">${admin ? esc((r.requestedBy && (r.requestedBy.displayName || r.requestedBy.jellyfinUsername)) || "Someone") + " asked · " : ""}<span class="word-${lvl}">${esc(word)}</span> · ${esc(B.when(r.createdAt))}</div></div>
      <div class="acts">${admin ? `<button class="btn small white" data-yes="${r.id}">Yes</button><button class="btn small" data-no="${r.id}">No</button>` : `<button class="btn small" data-ask="${esc(r.type)}:${esc(md.tmdbId)}">Open</button>`}</div></div>`;
  };
  const drawChips = () => {
    drawWall();
    const box = $("#rqmine");
    if (!box) return;
    box.innerHTML = `<button class="chip" data-rqlist="mine">Your requests ${lists.mine.length}</button>`
      + (B.me.admin ? ` <button class="chip ${lists.waiting.length ? "on" : ""}" data-rqlist="waiting">Waiting for you ${lists.waiting.length}</button>` : "");
    box.onclick = (e) => { const b = e.target.closest("[data-rqlist]"); if (b) openList(b.dataset.rqlist); };
  };
  const openList = (which) => {
    const items = lists[which], admin = which === "waiting";
    const p = B.side(`<h3>${admin ? "Waiting for you" : "Your requests"} (${items.length})</h3>
      ${items.length ? `<div class="list">${items.map((r) => reqItem(r, admin)).join("")}</div>` : `<p class="dim">${admin ? "Nobody is waiting on you right now." : "Nothing asked for yet. Pick a poster and press Ask for it."}</p>`}`);
    p.addEventListener("click", (e) => {
      const y = e.target.closest("[data-yes]"), n = e.target.closest("[data-no]");
      if (!y && !n) return;
      const id = (y || n).dataset.yes || (y || n).dataset.no;
      B.busy(y || n, async () => {
        await B.jf("POST", `/JellyfinEnhanced/arr/requests/${id}/${y ? "approve" : "decline"}`).catch(() => {});
        await loadLists();
        if (B.alive(nav)) { drawChips(); B.closeAll(); openList(which); }
        return y ? "Said yes. It's on its way." : "Said no.";
      });
    });
  };
  // Layout C (Ben 2026-11-10): the list on the left picks what the poster wall on the right shows
  const GENRES = tab === "movies"
    ? [["Action", 28], ["Adventure", 12], ["Comedy", 35], ["Crime", 80], ["Documentary", 99], ["Drama", 18], ["Family", 10751], ["Fantasy", 14], ["History", 36], ["Horror", 27], ["Mystery", 9648], ["Romance", 10749], ["Sci-Fi", 878], ["Thriller", 53], ["War", 10752], ["Western", 37]]
    : [["Action and adventure", 10759], ["Comedy", 35], ["Crime", 80], ["Documentary", 99], ["Drama", 18], ["Family", 10751], ["Kids", 10762], ["Mystery", 9648], ["Reality", 10764], ["Sci-Fi and fantasy", 10765], ["War and politics", 10768], ["Western", 37]];
  let walls = [], picked = null, genre = null;
  // Top 10: a number badge on the picture's bottom corner (the name and year stay under the poster)
  const ranked = (r, i) => B.askCard(r).replace(/<\/div>\s*<div class="cap">/, `<span class="rqrank">${i + 1}</span></div><div class="cap">`);
  let animeUi = null;
  const drawWall = () => {
    // Anime has its own side list and walls (B.animeRequests, below)
    if (tab === "anime") { if (animeUi) animeUi.drawSide(); return; }
    const side = $("#rqside"), wall = $("#rqwall");
    if (!side || !wall) return;
    const cur = picked === "genres" ? null : walls.find((w) => w.id === picked) || walls[0];
    side.innerHTML = `<h4>Lists</h4>` + walls.map((w) => `<button class="${w === cur ? "on" : ""}" data-rqpick="${esc(w.id)}">${esc(w.title)}<span class="n">${w.items.length}</span></button>`).join("")
      + (tab === "anime" ? "" : `<button class="${picked === "genres" ? "on" : ""}" data-rqpick="genres">${genre ? "Genre: " + esc(genre[0]) : "Genres"}</button>`)
      + `<h4>Yours</h4><button data-rqlist="mine">Your requests<span class="n">${lists.mine.length}</span></button>`
      + (B.me.admin ? `<button data-rqlist="waiting">Waiting for you${lists.waiting.length ? `<span class="n">${lists.waiting.length} <span class="dot"></span></span>` : '<span class="n">0</span>'}</button>` : "");
    if (picked === "genres") {
      wall.innerHTML = `<div class="rqhead"><b>Genres</b><span>Pick one and every list shows only that genre</span></div><div class="rqgenres">${[["All", 0]].concat(GENRES).map(([l, id]) => `<button class="chip ${(genre ? genre[1] : 0) === id ? "on" : ""}" data-rqgenre="${id}">${esc(l)}</button>`).join("")}</div>`;
      return;
    }
    wall.innerHTML = cur ? `<div class="rqhead"><b>${esc(cur.title)}</b><span>${cur.items.length} to ask for</span></div><div class="grid">${cur.items.map((r, i) => cur.ranked && i < 10 ? ranked(r, i) : B.askCard(r)).join("")}</div>` : `<div class="empty">Nothing to show right now. Try again in a minute.</div>`;
  };
  const drawRows = async () => {
    if (tab === "anime") { animeUi = B.animeRequests(nav, () => lists, openList); return; }
    $("#rows").innerHTML = `<div class="grid-wrap rqsplit"><nav class="rqside" id="rqside"></nav><div class="grid-body" id="rqwall">${B.spinner()}</div></div>`;
    $("#rqside").onclick = (e) => {
      const l = e.target.closest("[data-rqlist]");
      if (l) return openList(l.dataset.rqlist);
      const b = e.target.closest("[data-rqpick]");
      if (b) { picked = b.dataset.rqpick; drawWall(); }
    };
    $("#rqwall").onclick = (e) => {
      const g = e.target.closest("[data-rqgenre]");
      if (!g) return;
      const id = +g.dataset.rqgenre;
      genre = id ? GENRES.find((x) => x[1] === id) : null;
      picked = null;
      loadWalls();
    };
    drawWall();
    await loadWalls();
  };
  const loadWalls = async () => {
    const wall = $("#rqwall");
    if (wall) wall.innerHTML = B.spinner();
    const kindPath = tab === "movies" ? "movies" : "tv";
    // Anime: TMDB's Animation genre is mostly kids' cartoons, so only Japanese titles count as anime
    const pg = (path, list) => list.map((n) => seerr(`${path}?page=${n}`).catch(() => null));
    // Anime: TMDB's Animation genre is mostly kids' cartoons, so only Japanese titles count as anime
    const groups = tab === "anime"
      ? [pg("/discover/tv/genre/16", [1, 2, 3, 4]), pg("/discover/movies/genre/16", [1, 2, 3]), pg("/discover/tv/genre/16", [5, 6, 7])]
      : genre
        ? [pg(`/discover/${kindPath}/genre/${genre[1]}`, [1, 2, 3, 4])]
        : [pg(`/discover/${kindPath}`, [1, 2, 3]), pg(`/discover/${kindPath}/upcoming`, [1, 2, 3]), pg(`/discover/${kindPath}`, [4, 5, 6])];
    const got = await Promise.all(groups.map((g) => Promise.all(g)));
    if (!B.alive(nav)) return;
    const notHere = (l, mt) => ((l && l.results) || []).map((x) => Object.assign(x, { mediaType: x.mediaType || mt }))
      .filter((x) => !onBenflix(x) && (tab === "anime" ? x.originalLanguage === "ja" : !isAnime(x)));
    const seen = new Set();
    const uniq = (l) => l.filter((x) => !seen.has(x.mediaType + x.id) && seen.add(x.mediaType + x.id));
    const mt = tab === "movies" ? "movie" : "tv";
    const all = (i, m) => uniq((got[i] || []).flatMap((l) => notHere(l, m || mt)));
    // Each wall is long (40 to 60 posters); the Top 10 are the first ten of the first wall, with numbers
    if (tab === "anime") {
      walls = [{ id: "top", title: "Top 10 and popular", items: all(0, "tv"), ranked: true }, { id: "movies", title: "Anime movies", items: all(1, "movie") },
        { id: "more", title: "More anime", items: all(2, "tv") }];
    } else if (genre) {
      walls = [{ id: "top", title: "Top 10 in " + genre[0], items: all(0), ranked: true }];
    } else {
      walls = [{ id: "top", title: "Top 10 and trending", items: all(0), ranked: true }, { id: "soon", title: "Coming soon", items: all(1) },
        { id: "more", title: "More to ask for", items: all(2) }];
    }
    walls = walls.filter((w) => w.items.length);
    drawWall();
  };
  let t;
  $("#rq").oninput = (e) => { clearTimeout(t); t = setTimeout(() => search(e.target.value), 350); };
  if (B.q("q")) search(B.q("q")); else drawRows();
  drawChips();
  loadLists().then(() => { if (B.alive(nav)) drawChips(); });
});

// ---------- Music (only Ben): ask for songs and albums, playlists, the Music Drop folder, tidy-up ----------
// Talks to the helper's /api/music (benflix_music.py), which hands the downloading to Lidarr on Tower.
const music = (method, path, body) => B.call(method, "api/music" + path, body);
const songLine = (s, n) => `<div class="item">${n ? `<b class="dim" style="min-width:28px">${n}</b>` : ""}<div style="width:48px;height:48px;border-radius:6px;flex:none;background:var(--pg3) center/cover url('${esc(s.cover || "")}')"></div>
  <div class="grow"><div class="t">${esc(s.title)}</div><div class="s">${esc(s.artist)}${s.album ? " · " + esc(s.album) : ""}${s.seconds ? " · " + Math.floor(s.seconds / 60) + ":" + String(s.seconds % 60).padStart(2, "0") : ""}</div></div>
  <div class="acts">${B.previewBtn(s.id)}${s.asked || s.have ? `<span class="dim">${esc(s.asked || "In your music")}</span>` : `<button class="btn small white" data-msong="${s.id}" data-malbum="${s.album_id}" data-mname="${esc(s.title)}" data-maname="${esc(s.album)}">Ask for it</button>`}</div></div>`;
const albumCard = (a) => `<button class="poster" data-mopen="album:${a.id}" title="${esc(a.title)}"><div class="im" style="aspect-ratio:1;${a.cover ? `background-image:url('${esc(a.cover)}')` : ""}"></div>
  <div class="cap">${esc(a.title)}<small>${esc(a.asked || [a.year, a.type === "single" ? "Single" : a.type === "ep" ? "EP" : ""].filter(Boolean).join(" · "))}</small></div></button>`;
const genreCard = (g) => `<button class="poster" data-mgenre="${g.id}" title="${esc(g.name)}"><div class="im" style="aspect-ratio:1;${g.picture ? `background-image:url('${esc(g.picture)}')` : ""}"></div><div class="cap">${esc(g.name)}</div></button>`;
// Top songs, New releases, Top albums, Top artists (and Genres on the main page)
const songCard = (s, n) => `<div class="pvcard">${B.previewBtn(s.id, "on-card")}<button class="poster" ${s.asked || s.have ? "" : `data-msong="${s.id}" data-malbum="${s.album_id}" data-mname="${esc(s.title)}" data-maname="${esc(s.album)}"`} title="${esc(s.title)}"><div class="im" style="aspect-ratio:1;${s.cover ? `background-image:url('${esc(s.cover)}')` : ""}"></div>
  <div class="cap">${n ? n + ". " : ""}${esc(s.title)}<small>${esc(s.asked || (s.have ? "In your music" : s.artist))}</small></div></button></div>`;
const discoverHtml = (d, title, allSongs) => {
  const songs = allSongs ? d.songs : d.songs.slice(0, 10);
  return (d.songs.length ? `<div class="wrap"><h3 class="section-title">${esc(allSongs ? title : "Top 10 songs right now")}</h3><div class="list">${songs.map((x, i) => songLine(x, i + 1)).join("")}</div>
      ${!allSongs && d.songs.length > 10 ? `<p><button class="btn" data-mallsongs>See all ${d.songs.length}</button></p>` : ""}</div>` : "")
    + (allSongs ? "" : B.row("Trending now", d.songs.slice(10, 40).map((x, i) => songCard(x, i + 11))))
    + B.row("New releases", (d.new || []).map(albumCard))
    + (d.because || []).map((b) => B.row("Because you have " + b.artist, b.songs.map((x) => songCard(x)))).join("")
    + B.row("Editors' picks", (d.picks || []).map(albumCard)) + B.row("Top albums", (d.albums || []).map(albumCard))
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
        <div class="acts" style="justify-content:flex-start;margin:4px 0 12px">${B.previewBtn(d.msong)}<span class="dim">Hear 30 seconds</span></div>
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
        // Ben: someone else's playlist can bring in just the songs, without a Benflix playlist
        B.ask("Make a Benflix playlist too?", "Songs you don't have yet are asked for either way. Pick Just the songs for someone else's playlist.", "Songs + playlist", "Just the songs").then((make) => {
          B.busy(null, async () => { const p = await music("POST", "/playlist", { link: link.trim(), playlist: !!make }); drawStatus(); return `Bringing in ${p.name}.`; });
        });
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

// ---------- Anime tab (Ben's anime Requests design, 2026-11-12) ----------
// Requests layout C with anime ways in: This season (opens here), Top 10 this week, This week's calendar,
// Find by feel, Mix and match tags, Hidden gems, Finished and dubbed, Movies, Coming next season.
// Lists come from AniList (first seasons only, nothing adult); a poster opens its Requests page, found by name.
// Shows already on Benflix are left out, with a small "12 already on Benflix" note.
B.animeRequests = (nav, getLists, openList) => {
  const SHOWS = ["TV", "TV_SHORT", "ONA"];
  const CARD = "id idMal format status episodes popularity averageScore meanScore isAdult genres seasonYear title { english romaji native } synonyms startDate { year } coverImage { large medium } nextAiringEpisode { episode } relations { edges { relationType node { id format } } }";
  const LIST_Q = `query ($page: Int, $sort: [MediaSort], $season: MediaSeason, $seasonYear: Int, $status: MediaStatus, $formats: [MediaFormat], $genres: [String], $tags: [String], $noGenres: [String], $noTags: [String], $scoreOver: Int, $popUnder: Int, $popOver: Int) {
    Page(page: $page, perPage: 50) { pageInfo { hasNextPage }
      media(type: ANIME, isAdult: false, sort: $sort, season: $season, seasonYear: $seasonYear, status: $status, format_in: $formats, genre_in: $genres, genre_not_in: $noGenres, tag_in: $tags, tag_not_in: $noTags, minimumTagRank: 50, averageScore_greater: $scoreOver, popularity_lesser: $popUnder, popularity_greater: $popOver) { ${CARD} } } }`;
  const WEEK_Q = `query ($page: Int, $from: Int, $to: Int) { Page(page: $page, perPage: 50) { pageInfo { hasNextPage }
    airingSchedules(airingAt_greater: $from, airingAt_lesser: $to, sort: TIME) { episode airingAt media { ${CARD} } } } }`;
  const GENRES = ["Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"];
  const FEELS = [["Action", "Fights, powers", "#FF6F91", { genres: ["Action"] }], ["Isekai", "Sent to another world", "#B69CFF", { tags: ["Isekai"] }],
    ["Cosy", "Calm, slice of life", "#B9E36A", { genres: ["Slice of Life"], tags: ["Iyashikei"], noGenres: ["Horror"], noTags: ["Gore"] }],
    ["Romance", "Love stories", "#FF8FB1", { genres: ["Romance"] }], ["Sports", "Teams and matches", "#FFB547", { genres: ["Sports"] }],
    ["Mecha", "Giant robots", "#7AA7FF", { genres: ["Mecha"] }], ["Mystery", "Puzzles, twists", "#3DD6C6", { genres: ["Mystery"] }],
    ["Horror", "Creepy, dark", "#9B7B7B", { genres: ["Horror"] }], ["Comedy", "Just funny", "#F2D65B", { genres: ["Comedy"] }], ["Fantasy", "Magic, quests", "#8FB8FF", { genres: ["Fantasy"] }]];
  const SIDE = [["season", "This season"], ["top", "Top 10 this week"], ["week", "This week"], ["feel", "Find by feel"], ["tags", "Mix and match tags"],
    ["gems", "Hidden gems"], ["dubbed", "Finished and dubbed"], ["movies", "Movies"], ["next", "Coming next season"]];
  const cleanName = (t) => String(t || "").toLowerCase().replace(/&/g, "and").replace(/[^\p{L}\p{N}]/gu, "");
  const al = async (query, variables) => {
    const r = await fetch("https://graphql.anilist.co", { method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify({ query, variables }) });
    if (!r.ok) throw new Error("AniList " + r.status);
    return (await r.json()).data || {};
  };
  const titlesOf = (m) => [m.title && m.title.english, m.title && m.title.romaji, m.title && m.title.native].concat(m.synonyms || []).filter((x) => x && x.trim()).filter((x, i, a) => a.indexOf(x) === i);
  const cardOf = (m) => {
    if (!m || m.isAdult || (m.genres || []).includes("Hentai")) return null;
    const later = SHOWS.includes(m.format) && ((m.relations && m.relations.edges) || []).some((e) => e.relationType === "PREQUEL" && e.node && SHOWS.includes(e.node.format));
    return { id: m.id, idMal: m.idMal, titles: titlesOf(m), format: m.format, year: (m.startDate && m.startDate.year) || m.seasonYear, score: m.averageScore || m.meanScore,
      episodes: m.episodes, next: m.nextAiringEpisode && m.nextAiringEpisode.episode, status: m.status, cover: m.coverImage && (m.coverImage.large || m.coverImage.medium), later };
  };
  const seasonOf = (d) => { const mo = d.getMonth() + 1; return [mo === 12 || mo <= 2 ? "WINTER" : mo <= 5 ? "SPRING" : mo <= 8 ? "SUMMER" : "FALL", mo === 12 ? d.getFullYear() + 1 : d.getFullYear()]; };
  const ORDER = ["WINTER", "SPRING", "SUMMER", "FALL"];
  const step = ([s, y], n) => { let i = ORDER.indexOf(s) + n, yy = y; while (i < 0) { i += 4; yy--; } while (i > 3) { i -= 4; yy++; } return [ORDER[i], yy]; };
  const sLabel = ([s, y]) => s.charAt(0) + s.slice(1).toLowerCase() + " " + y;
  const NOW = seasonOf(new Date());
  const SEASONS = [NOW, step(NOW, -1), step(NOW, -2)];
  const asks = {
    season: (i) => ({ key: "season-" + SEASONS[i].join("-"), season: SEASONS[i][0], seasonYear: SEASONS[i][1], formats: SHOWS }),
    top: () => ({ key: "top", sort: ["TRENDING_DESC"], formats: SHOWS.concat("MOVIE"), pages: 1 }),
    gems: () => ({ key: "gems", sort: ["SCORE_DESC"], formats: SHOWS.concat("MOVIE"), scoreOver: 79, popUnder: 50000, popOver: 2000 }),
    dubbed: () => ({ key: "finished", status: "FINISHED", formats: SHOWS, pages: 4 }),
    movies: () => ({ key: "movies", formats: ["MOVIE"] }),
    next: () => { const n = step(NOW, 1); return { key: "next-" + n.join("-"), season: n[0], seasonYear: n[1], formats: SHOWS.concat("MOVIE") }; },
    feel: (f) => Object.assign({ key: "feel-" + f[0], formats: SHOWS.concat("MOVIE") }, f[3], { noGenres: (f[3].noGenres || []).concat("Ecchi") }),
  };
  const load = (a) => B.cached("anime-req-" + a.key, 6 * 3600000, async () => {
    const out = [];
    for (let page = 1; page <= (a.pages || 2); page++) {
      const v = { page, sort: a.sort || ["POPULARITY_DESC"], noGenres: (a.noGenres || []).concat("Hentai") };
      ["season", "seasonYear", "status", "formats", "genres", "tags", "noTags", "scoreOver", "popUnder", "popOver"].forEach((k) => { if (a[k] != null && !(Array.isArray(a[k]) && !a[k].length)) v[k] = a[k]; });
      const p = (await al(LIST_Q, v)).Page;
      if (!p) throw new Error("AniList had no answer");
      (p.media || []).map(cardOf).filter((c) => c && (a.keepLater || !c.later)).forEach((c) => out.push(c));
      if (!p.pageInfo || !p.pageInfo.hasNextPage) break;
    }
    const seen = new Set();
    return out.filter((c) => !seen.has(c.id) && seen.add(c.id));
  });
  const week = () => B.cached("anime-req-week", 3 * 3600000, async () => {
    const now = Math.floor(Date.now() / 1000), out = [];
    for (let page = 1; page <= 8; page++) {
      const p = (await al(WEEK_Q, { page, from: now - 12 * 3600, to: now + 7 * 86400 })).Page;
      if (!p) { if (page === 1) throw new Error("AniList had no answer"); break; }
      (p.airingSchedules || []).forEach((s) => { const c = cardOf(s.media); if (c) out.push({ card: c, ep: s.episode, at: s.airingAt }); });
      if (!p.pageInfo || !p.pageInfo.hasNextPage) break;
    }
    return out;
  });
  const dubs = () => B.cached("anime-dubs", 86400000, async () => {
    const r = await fetch("https://raw.githubusercontent.com/MAL-Dubs/MAL-Dubs/main/data/dubInfo.json");
    if (!r.ok) throw new Error("dub list " + r.status);
    return new Set((await r.json()).dubbed || []);
  });
  const tagList = () => B.cached("anime-tags", 86400000, async () => ((await al("query { MediaTagCollection { name category isAdult } }", {})).MediaTagCollection || []).filter((t) => !t.isAdult));
  // What's already on Benflix: everything in the anime libraries, by AniList / MyAnimeList number and by name
  const owned = () => B.cached("anime-owned", 600000, async () => {
    const libs = (await B.views()).filter(B.isAnimeLib);
    const o = { ids: new Set(), mal: new Set(), names: new Set() };
    for (const l of libs) {
      const r = await B.get(`/Items?userId=${B.uid()}&ParentId=${l.Id}&IncludeItemTypes=Series,Movie&Recursive=true&Fields=ProviderIds,OriginalTitle&EnableImages=false&EnableUserData=false`).catch(() => ({}));
      (r.Items || []).forEach((x) => {
        [x.Name, x.OriginalTitle].forEach((n) => { const c = cleanName(n); if (c) o.names.add(c); });
        const p = x.ProviderIds || {};
        if (p.AniList) o.ids.add(+p.AniList);
        if (p.MyAnimeList || p.Mal) o.mal.add(+(p.MyAnimeList || p.Mal));
      });
    }
    return o;
  });
  const has = (o, c) => {
    if (!o) return false;
    if (o.ids.has(c.id) || (c.idMal && o.mal.has(c.idMal))) return true;
    const names = c.titles.map(cleanName).filter(Boolean);
    if (names.some((n) => o.names.has(n))) return true;
    return c.later && names.some((t) => [...o.names].some((n) => n.length >= 5 && t.startsWith(n)));
  };

  let pick = "season", seasonI = 0, feel = null, must = [], leave = [], own = null, dub = null, tags = null;
  const wall = () => $("#rqwall"), side = () => $("#rqside");
  const drawSide = () => {
    const l = getLists();
    side().innerHTML = `<h4>Anime</h4><button data-an-search>Search anime</button>` + SIDE.map(([k, t]) => `<button class="${k === pick ? "on" : ""}" data-an-pick="${k}">${esc(t)}</button>`).join("")
      + `<h4>Yours</h4><button data-rqlist="mine">Your requests<span class="n">${l.mine.length}</span></button>`
      + (B.me.admin ? `<button data-rqlist="waiting">Waiting for you${l.waiting.length ? `<span class="n">${l.waiting.length} <span class="dot"></span></span>` : '<span class="n">0</span>'}</button>` : "");
  };
  const badge = (c) => {
    const bits = [];
    if (c.score) bits.push(`<span class="anb anb-score">${(c.score / 10).toFixed(1)}</span>`);
    if (dub && dub.size) bits.push(`<span class="anb anb-dub">${c.idMal && dub.has(c.idMal) ? "Sub+Dub" : "Sub"}</span>`);
    return bits.join("");
  };
  const airing = (c) => {
    if (c.status !== "RELEASING" || !c.next || c.next < 2) return "";
    return c.episodes ? `Ep ${c.next - 1} of ${c.episodes}` : `Ep ${c.next - 1} out`;
  };
  B.animeCards = B.animeCards || {};
  const poster = (c, rank) => {
    B.animeCards[c.id] = c;
    return `<button class="poster ancard" data-an="${c.id}" title="${esc(c.titles[0] || "")}">
      <div class="im" ${c.cover ? `style="background-image:url('${esc(c.cover)}')"` : ""}>${c.cover ? "" : `<div class="noart"><span class="nt">${esc(c.titles[0] || "")}</span></div>`}${badge(c)}${rank ? `<span class="rqrank anb-rank">${rank}</span>` : c.format === "MOVIE" ? '<span class="anb anb-kind">Movie</span>' : ""}</div>
      <div class="cap">${esc(c.titles[0] || "")}<small>${esc(airing(c) || c.year || "")}</small></div></button>`;
  };
  const head = (title, note, extra) => `${extra || ""}<div class="rqhead"><b>${esc(title)}</b><span>${esc(note || "")}</span></div>`;
  const showWall = async (a, title, note, opts) => {
    opts = opts || {};
    const w = wall();
    w.innerHTML = head(title, note, opts.top) + B.spinner();
    let items;
    try { items = await load(a); } catch (e) {
      if (!B.alive(nav) || wall() !== w) return;
      w.innerHTML = head(title, note, opts.top) + `<div class="empty">These didn't load. AniList may be busy. <button class="btn small" data-an-retry>Try again</button></div>`;
      return;
    }
    if (!B.alive(nav) || wall() !== w || w.dataset.at !== opts.at) return;
    if (opts.keep) items = items.filter(opts.keep);
    const shown = items.filter((c) => !has(own, c)), here = items.length - shown.length;
    w.innerHTML = head(title, [note, here ? `${here} already on Benflix` : ""].filter(Boolean).join(" · "), opts.top)
      + (shown.length ? `<div class="grid">${shown.map((c, i) => poster(c, opts.ranked && i < 10 ? i + 1 : 0)).join("")}</div>` : `<div class="empty">${here ? "You already have all of these on Benflix." : "Nothing here right now."}</div>`);
  };
  const dayKey = (d) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
  const showWeek = async () => {
    const w = wall(), at = w.dataset.at;
    w.innerHTML = head("This week in anime", "New episodes by day, for shows you don't have yet") + B.spinner();
    let all;
    try { all = await week(); } catch (e) {
      if (wall() === w && w.dataset.at === at) w.innerHTML = head("This week in anime", "") + `<div class="empty">The calendar didn't load. AniList may be busy. <button class="btn small" data-an-retry>Try again</button></div>`;
      return;
    }
    if (!B.alive(nav) || w.dataset.at !== at) return;
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const days = [0, 1, 2, 3, 4, 5, 6].map((n) => new Date(today.getTime() + n * 86400000));
    const by = {};
    let here = new Set();
    all.forEach((a) => {
      if (has(own, a.card)) { here.add(a.card.id); return; }
      const k = dayKey(new Date(a.at * 1000));
      const l = by[k] || (by[k] = []);
      if (!l.some((x) => x.card.id === a.card.id)) l.push(a);
    });
    const name = (d, i) => i === 0 ? "Today" : i === 1 ? "Tomorrow" : d.toLocaleDateString("en-US", { weekday: "short" }) + " " + d.getDate();
    const time = (a) => new Date(a.at * 1000).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }).toLowerCase();
    const row = (a) => { B.animeCards[a.card.id] = a.card; return `<div class="anep"><button class="anep-open" data-an="${a.card.id}"><span class="pic" ${a.card.cover ? `style="background-image:url('${esc(a.card.cover)}')"` : ""}></span><span class="t"><b>${esc(a.card.titles[0] || "")}</b><small>Ep ${a.ep} · ${esc(time(a))}${a.card.score ? " · AniList " + (a.card.score / 10).toFixed(1) : ""}</small></span></button><button class="btn small white" data-an="${a.card.id}">Ask</button></div>`; };
    const note = ["New episodes by day, for shows you don't have yet", here.size ? `${here.size} already on Benflix` : ""].filter(Boolean).join(" · ");
    let dayI = 0;
    const draw = () => {
      const phone = window.innerWidth <= 700;
      w.innerHTML = head("This week in anime", note) + (phone
        ? `<div class="row an-days">${days.map((d, i) => `<button class="chip ${i === dayI ? "on" : ""}" data-an-day="${i}">${name(d, i)}</button>`).join("")}</div><div class="anlist">${(by[dayKey(days[dayI])] || []).map(row).join("") || '<div class="empty">Nothing new that day.</div>'}</div>`
        : `<div class="ancal">${days.map((d, i) => `<div class="anday"><h4 class="${i === 0 ? "on" : ""}">${name(d, i)}</h4>${(by[dayKey(d)] || []).map(row).join("") || '<p class="dim">Nothing new</p>'}</div>`).join("")}</div>`);
    };
    draw();
    w.onclickday = (i) => { dayI = i; draw(); };
  };
  const showFeels = () => {
    wall().innerHTML = head("What are you in the mood for?", "") + `<div class="anfeels">${FEELS.filter((f) => !tags || (f[3].tags || []).every((t) => tags.some((x) => x.name === t))).map((f) => `<button class="anfeel" data-an-feel="${esc(f[0])}" style="--c:${f[2]}"><b>${esc(f[0])}</b><span>${esc(f[1])}</span></button>`).join("")}</div>`;
  };
  const recipe = (f) => { const m = (f[3].genres || []).concat(f[3].tags || []).join(" + "), n = (f[3].noGenres || []).concat(f[3].noTags || []); return n.length ? m + " · " + n.map((x) => "no " + x).join(", ") : m; };
  const showTags = async () => {
    const w = wall(), at = w.dataset.at;
    w.innerHTML = head("Mix and match", "Click a tag to need it. Click again to leave it out. A third click clears it.") + B.spinner();
    if (!tags) { try { tags = await tagList(); } catch (e) { tags = []; } }
    if (!B.alive(nav) || w.dataset.at !== at) return;
    const state = (n) => must.includes(n) ? "on" : leave.includes(n) ? "out" : "";
    const chip = (n) => `<button class="chip antag ${state(n)}" data-an-tag="${esc(n)}">${leave.includes(n) ? "No " : ""}${esc(n)}</button>`;
    const groups = {};
    tags.forEach((t) => { const g = (t.category || "Other").split("-")[0]; (groups[g] = groups[g] || []).push(t.name); });
    const picked = must.length || leave.length;
    w.innerHTML = head("Mix and match", "Click a tag to need it. Click again to leave it out. A third click clears it.")
      + (picked ? `<div class="row" style="margin-bottom:10px">${must.concat(leave).map(chip).join("")}<button class="chip" data-an-clear>Clear all</button></div><div id="anmix"></div>` : "")
      + `<details class="antags" ${picked ? "" : "open"}><summary>Genres and tags</summary><h4>Genres</h4><div class="row">${GENRES.map(chip).join("")}</div>`
      + Object.keys(groups).sort().map((g) => `<h4>${esc(g)}</h4><div class="row">${groups[g].sort().map(chip).join("")}</div>`).join("") + `</details>`;
    if (picked) {
      const box = $("#anmix", w);
      const fake = document.createElement("div");
      const a = { key: "mix-" + must.join(",") + "|" + leave.join(","), genres: must.filter((n) => GENRES.includes(n)), tags: must.filter((n) => !GENRES.includes(n)), noGenres: leave.filter((n) => GENRES.includes(n)), noTags: leave.filter((n) => !GENRES.includes(n)), formats: SHOWS.concat("MOVIE"), pages: 2 };
      box.innerHTML = B.spinner();
      let items;
      try { items = await load(a); } catch (e) { box.innerHTML = `<div class="empty">That didn't load. AniList may be busy.</div>`; return; }
      if (w.dataset.at !== at || !box.isConnected) return;
      const shown = items.filter((c) => !has(own, c)), here = items.length - shown.length;
      fake.innerHTML = `<p class="dim">${shown.length} to ask for${here ? ` · ${here} already on Benflix` : ""}</p><div class="grid">${shown.map((c) => poster(c, 0)).join("")}</div>`;
      box.replaceChildren(...fake.childNodes);
    }
  };
  const draw = () => {
    drawSide();
    const w = wall();
    w.dataset.at = String(Date.now() + Math.random());
    const at = w.dataset.at;
    if (pick === "season") {
      const pills = `<div class="row" style="margin-bottom:10px">${SEASONS.map((s, i) => `<button class="chip ${i === seasonI ? "on" : ""}" data-an-season="${i}">${sLabel(s)}</button>`).join("")}</div>`;
      return showWall(asks.season(seasonI), sLabel(SEASONS[seasonI]), seasonI === 0 ? "Airing now" : seasonI === 1 ? "Last season" : "", { top: pills, at });
    }
    if (pick === "top") return showWall(asks.top(), "Top 10 this week", "What anime fans are watching most", { ranked: true, at });
    if (pick === "week") return showWeek();
    if (pick === "feel") {
      if (!feel) return showFeels();
      return showWall(asks.feel(feel), feel[0], recipe(feel), { top: `<div class="row" style="margin-bottom:10px"><button class="chip" data-an-feels>All feels</button></div>`, at });
    }
    if (pick === "tags") return showTags();
    if (pick === "gems") return showWall(asks.gems(), "Hidden gems", "Scores of 8 and up that few people have found", { at });
    if (pick === "movies") return showWall(asks.movies(), "Movies", "", { at });
    if (pick === "next") return showWall(asks.next(), "Coming next season", sLabel(step(NOW, 1)), { at });
    if (pick === "dubbed") {
      if (!dub || !dub.size) {
        w.innerHTML = head("Finished and dubbed", "") + `<div class="empty">Couldn't get the list of dubbed anime. <button class="btn small" data-an-retry>Try again</button></div>`;
        return;
      }
      return showWall(asks.dubbed(), "Finished and dubbed", "Every episode out, with English voices", { keep: (c) => c.idMal && dub.has(c.idMal), at });
    }
  };
  // Opens a title's Requests page: AniList and Requests don't share numbers, so it's found by its names and year
  const open = async (c, btn) => {
    if (btn) btn.classList.add("busy");
    const want = c.isMovie || c.format === "MOVIE" ? "movie" : "tv";
    const wanted = new Set(c.titles.map(cleanName).filter(Boolean));
    const yearOk = (x, slack) => { const y = +((x.releaseDate || x.firstAirDate || "").slice(0, 4)); return !y || !c.year || Math.abs(y - c.year) <= slack; };
    const nameOf = (x) => cleanName(x.title || x.name || x.originalName || x.originalTitle);
    let found = null;
    for (const n of c.titles.filter((t) => /[A-Za-z0-9]/.test(t)).slice(0, 3)) {
      const r = await seerr(`/search?query=${enc(n.replace(/[:!?]/g, " ").trim())}&page=1`).catch(() => null);
      const res = ((r && r.results) || []).filter((x) => x.mediaType === want);
      found = res.find((x) => wanted.has(nameOf(x)) && yearOk(x, 1))
        || res.find((x) => { const m = nameOf(x); return m.length >= 4 && [...wanted].some((w) => w.startsWith(m) || m.startsWith(w)) && yearOk(x, 2); })
        || res.find((x) => wanted.has(nameOf(x)));
      if (found) break;
    }
    if (btn) btn.classList.remove("busy");
    if (!B.alive(nav)) return;
    if (found) B.go(`#/ask/${want}/${found.id}`);
    else B.toast(`Couldn't find ${c.titles[0] || "that"} in Requests yet. Try Search anime.`);
  };

  $("#rows").innerHTML = `<div class="grid-wrap rqsplit"><nav class="rqside" id="rqside"></nav><div class="grid-body" id="rqwall">${B.spinner()}</div></div>`;
  side().onclick = (e) => {
    const l = e.target.closest("[data-rqlist]");
    if (l) return openList(l.dataset.rqlist);
    if (e.target.closest("[data-an-search]")) { const q = $("#rq"); if (q) { q.placeholder = "Search anime to ask for"; q.focus(); } return; }
    const b = e.target.closest("[data-an-pick]");
    if (b) { pick = b.dataset.anPick; if (pick === "feel") feel = null; draw(); }
  };
  wall().onclick = (e) => {
    const t = e.target;
    const a = t.closest("[data-an]");
    if (a) { const c = B.animeCards[a.dataset.an]; if (c) open(c, a); return; }
    const s = t.closest("[data-an-season]");
    if (s) { seasonI = +s.dataset.anSeason; return draw(); }
    const f = t.closest("[data-an-feel]");
    if (f) { feel = FEELS.find((x) => x[0] === f.dataset.anFeel) || null; return draw(); }
    if (t.closest("[data-an-feels]")) { feel = null; return draw(); }
    const d = t.closest("[data-an-day]");
    if (d && wall().onclickday) return wall().onclickday(+d.dataset.anDay);
    const g = t.closest("[data-an-tag]");
    if (g) {
      const n = g.dataset.anTag;
      if (must.includes(n)) { must = must.filter((x) => x !== n); leave.push(n); } else if (leave.includes(n)) leave = leave.filter((x) => x !== n); else must.push(n);
      return draw();
    }
    if (t.closest("[data-an-clear]")) { must = []; leave = []; return draw(); }
    if (t.closest("[data-an-retry]")) {
      Object.keys(B.cache).filter((k) => k.startsWith("anime-")).forEach((k) => { delete B.cache[k]; });
      if (pick === "dubbed") dubs().then((x) => { dub = x; draw(); }).catch(() => draw()); else draw();
    }
  };
  draw();
  // Dubs and what's on Benflix load beside the first list; the wall is drawn again when they're in
  Promise.all([owned().catch(() => null), dubs().catch(() => null), tagList().catch(() => null)]).then(([o, d, t]) => {
    own = o; dub = d; tags = t;
    if (B.alive(nav) && wall()) draw();
  });
  return { drawSide: () => { if (side()) drawSide(); } };
};
