/* Benflix web: Search (Redesign 2, Ben 2026-11-10: "door cards"). Typing starts in the box straight away.
   Before typing: your last 6 searches (each with a ✕, plus Clear all), Popular and Browse. While typing:
   whole-name suggestions as chips under the box, then one card per door in a 3 by 2 grid (Movies, Shows,
   Music, Live TV, Games, Books): a thin door-colour line on top, name, count, the best 3 results with bigger
   pictures in their own shape, and See all. People and the white Ask for it door sit below the grid. Doors
   with nothing are named in one small line. Kids get Movies, Shows, Live TV and Games only. */
"use strict";

const recentKey = () => "benflix.search." + B.me.id;
B.route(/^\/search/, async (m, nav) => {
  const kids = B.me.kids;
  B.page().innerHTML = B.top("Search", null) + `<div class="searchtop"><input class="field" id="sq" placeholder="Search movies, shows, music, channels, games and books" autocomplete="off" value="${esc(B.q("q") || "")}"></div>
    <div class="schips" id="schips"></div><div class="bestline" id="best"></div><div id="sres"></div>`;
  B.wireTop();
  const box = $("#sq");
  box.focus();
  const before = async () => {
    const recent = loadJson(recentKey(), []);
    $("#best").innerHTML = "";
    $("#schips").innerHTML = "";
    $("#sres").innerHTML = `${recent.length ? `<section class="rowx" id="recent"><h3>Recent searches</h3><div class="wrap row" style="padding-bottom:0">${recent.map((r) => `<span class="rchip"><button class="pill" data-recent="${esc(r)}">${esc(r)}</button><button class="pill rx" data-unrecent="${esc(r)}" title="Remove" aria-label="Remove ${esc(r)}">✕</button></span>`).join("")}<button class="pill ghost" data-clearrecent>Clear all</button></div></section>` : ""}
      <div id="popular">${B.spinner()}</div>
      <section class="rowx"><h3>Browse</h3><div class="tiles2">
        <a class="tile2" href="#/browse/movies/genres">Movie genres</a><a class="tile2" href="#/browse/shows/genres">Show genres</a>
        <a class="tile2" href="#/browse/shows/studios">Networks</a><a class="tile2" href="#/live">Channel types</a><a class="tile2" href="#/games">Consoles</a></div></section>`;
    const [mv, tv] = await Promise.all([B.trending("Movie").catch(() => []), B.trending("Series").catch(() => [])]);
    if (!B.alive(nav) || !$("#popular")) return;
    const mix = []; for (let i = 0; i < 10; i++) { if (mv[i]) mix.push(mv[i]); if (tv[i]) mix.push(tv[i]); }
    $("#popular").innerHTML = B.row("Popular in your house", mix.map((it) => B.posterCard(it)));
  };
  let games = null, seq = 0, lastAsk = [], lastDoors = [];
  const plain = (x) => (x || "").normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[(\[].*?[)\]]/g, " ").replace(/[^a-z0-9]+/g, " ").trim();
  const run = async (text) => {
    const my = ++seq;
    const t = text.trim();
    if (!t) return before();
    $("#best").innerHTML = `<span class="dim">Looking…</span>`;
    const u = B.uid(), F = B.FIELDS;
    const [movies, shows, people, chans, ask, mine, cat, shelf] = await Promise.all([
      B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Movie&Recursive=true&Limit=30&Fields=${F}`).then((r) => B.keep(r.Items)).catch(() => []),
      B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Series&Recursive=true&Limit=30&Fields=${F}`).then((r) => B.keep(r.Items)).catch(() => []),
      kids ? [] : B.get(`/Persons?userId=${u}&searchTerm=${enc(t)}&Limit=16&EnableImages=true`).then((r) => r.Items || []).catch(() => []),
      // Kids search only finds kids titles: live programmes have no kids rating
      kids ? [] : B.channels().then((l) => l.filter((c) => {
        const now = c._progs.find((p) => new Date(p.StartDate) <= Date.now() && new Date(p.EndDate) > Date.now());
        const s = t.toLowerCase();
        return (c.Name || "").toLowerCase().includes(s) || ((now && now.Name) || "").toLowerCase().includes(s);
      })).catch(() => []),
      kids ? [] : B.seerr(`/search?query=${enc(t)}&page=1`).then((r) => (r.results || []).filter((x) => (x.mediaType === "movie" || x.mediaType === "tv") && !B.onBenflix(x))).catch(() => []),
      kids ? [] : B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Audio,MusicAlbum,MusicArtist,Playlist&Recursive=true&Limit=40&Fields=ChildCount,ProductionYear`).then((r) => (r.Items || []).filter((x) => x.Type !== "Playlist" || x.MediaType !== "Video")).catch(() => []),
      kids || !B.me.admin || t.length < 2 ? null : B.call("GET", `api/music/search?q=${enc(t)}`).catch(() => null),
      kids ? [] : B.cached("books-lib", 15000, () => B.call("GET", "api/books/library")).then((l) => (l.titles || []).filter((b) => !b.notHere)).catch(() => []),
    ]);
    // Songs and albums to ask for: not already in your music
    const have = new Set(mine.map((x) => plain(x.Name) + "|" + plain(x.AlbumArtist || (x.Artists || [])[0] || "")));
    const mAsk = cat ? [...(cat.songs || []).slice(0, 12).map((x) => Object.assign({ kind: "song" }, x)), ...(cat.albums || []).slice(0, 6).map((x) => Object.assign({ kind: "album", album_id: x.id, album: x.title }, x))]
      .filter((x) => !x.have && x.asked !== "In your music" && !have.has(plain(x.title) + "|" + plain(x.artist))) : [];
    // "funny 90s movie under 2 hours": the words become filters (extras.js), searched beside the names
    const words = B.extras ? B.extras.parseWords(t) : null;
    const described = words ? await B.extras.searchWords(words).catch(() => []) : [];
        if (games === null) games = await B.call("GET", "api/games").then((g) => g.consoles.flatMap((c) => c.games.filter((x) => (B.webPlayable ? B.webPlayable(c.slug) : x.playable)).map((x) => Object.assign({ console: c.name, slug: c.slug }, x)))).catch(() => []);
    if (my !== seq || !B.alive(nav)) return;
    const gl = games.filter((g) => g.name.toLowerCase().includes(t.toLowerCase()));
    // Closest name first
    const score = (n) => { n = (n || "").toLowerCase(); const s = t.toLowerCase(); return n === s ? 0 : n.startsWith(s) ? 1 : n.includes(" " + s) ? 2 : 3; };
    [movies, shows].forEach((l) => l.sort((a, b) => score(a.Name) - score(b.Name)));
    const best = [...movies, ...shows].sort((a, b) => score(a.Name) - score(b.Name))[0];
    // Nothing has what was typed in its name: the matches are close spellings, so offer the closest one
    const low = t.toLowerCase();
    const typo = t.length >= 3 && !described.length && ![...movies, ...shows].some((x) => (x.Name || "").toLowerCase().includes(low)) ? best : null;
    // Plain-words searches say so; a typo offers the closest name
    $("#best").innerHTML = described.length ? `<span class="idea">Searching by idea</span> <b>${esc(words.label)}</b> <span class="dim">· ${described.length} found</span>`
      : typo ? `Did you mean <a href="${B.hrefOf(typo)}"><b>${esc(typo.Name)}</b></a>? <span class="dim">· ${esc([typo.Type === "Series" ? "Show" : "Movie", typo.ProductionYear].filter(Boolean).join(" · "))}</span>` : "";
    // Books, manga and audiobooks on Tower: the name, author or series has the words
    const pt = plain(t);
    const books = shelf.filter((b) => [b.name, b.author, b.series].some((x) => plain(x).includes(pt)))
      .sort((a, b) => (plain(a.name).startsWith(pt) ? 0 : 1) - (plain(b.name).startsWith(pt) ? 0 : 1) || a.name.length - b.name.length).slice(0, 40);
    // Whole-name suggestions as chips under the box: names that start with the words first
    const seen = new Set();
    const sugg = [...movies, ...shows, ...mine, ...books.map((b) => ({ Name: b.name })), ...gl.map((g) => ({ Name: g.name })), ...chans]
      .map((x) => (x.Name || "").trim()).filter((n) => { const k = plain(n); if (!k.includes(pt) || k === pt || seen.has(k)) return false; seen.add(k); return true; })
      .map((n, i) => ({ n, i, s: plain(n).startsWith(pt) ? 0 : 1 })).sort((a, b) => a.s - b.s || a.i - b.i).slice(0, 6).map((x) => x.n);
    const bold = (n) => { const at = n.toLowerCase().indexOf(t.toLowerCase()); return at < 0 ? esc(n) : `${esc(n.slice(0, at))}<b>${esc(n.slice(at, at + t.length))}</b>${esc(n.slice(at + t.length))}`; };
    $("#schips").innerHTML = sugg.map((n) => `<button class="pill sg" data-sugg="${esc(n)}"><span>${bold(n)}</span></button>`).join("");
    // One line per thing: the picture with the name and year beside it (Ben, 2026-11-09)
    const line = (href, attrs, img, name, sub, square, asked) => `<${href ? `a href="${href}"` : "button"} class="sline big" ${attrs || ""} title="${esc(name)}"><span class="pic ${square ? "sq" : ""}" ${img ? `style="background-image:url('${img}')"` : ""}></span><span class="txt"><b>${esc(name)}</b><small>${esc(sub || "")}</small>${asked ? `<em>Asked</em>` : ""}</span></${href ? "a" : "button"}>`;
    const kindOf = { Movie: "Movie", BoxSet: "Collection", Series: "Show", Episode: "Episode", Audio: "Song", MusicAlbum: "Album", MusicArtist: "Artist", Playlist: "Playlist" };
    const itemLine = (x) => {
      const music = ["Audio", "MusicAlbum", "MusicArtist", "Playlist"].includes(x.Type);
      const tag = x.ImageTags && x.ImageTags.Primary;
      const img = tag ? B.img(x.Id, "Primary", { h: 120, tag }) : x.AlbumId && music ? B.img(x.AlbumId, "Primary", { h: 120 }) : "";
      const href = x.Type === "Audio" ? (x.AlbumId ? `#/album/${x.AlbumId}` : "#/songs") : x.Type === "MusicAlbum" ? `#/album/${x.Id}` : x.Type === "MusicArtist" ? `#/artist/${x.Id}` : x.Type === "Playlist" ? `#/playlist/${x.Id}` : B.hrefOf(x);
      const sub = [kindOf[x.Type], music ? x.AlbumArtist || (x.Artists || [])[0] : x.ProductionYear].filter(Boolean).join(" · ");
      return line(href, `data-id="${esc(x.Id)}"`, img, x.Name, sub, music);
    };
    const chLine = (c) => line(`#/watch/${c.Id}`, "", c.ImageTags && c.ImageTags.Primary ? B.img(c.Id, "Primary", { h: 120, tag: c.ImageTags.Primary }) : "", c.Name, [c.ChannelNumber, "Live channel"].filter(Boolean).join(" · "), true);
    const gameLine = (g) => line(`#/game/${g.id}`, "", g.cover ? `${g.cover}?api_key=${enc(B.me.token)}` : "", g.name, g.console);
    const titleLine = (r) => { B.askCard(r); return line(null, `data-ask="${esc(r.mediaType)}:${esc(r.id)}"`, r.posterPath ? `https://image.tmdb.org/t/p/w154${r.posterPath}` : "", r.title || r.name || "", [r.mediaType === "tv" ? "Show" : "Movie", (r.releaseDate || r.firstAirDate || "").slice(0, 4)].filter(Boolean).join(" · "), false, r.mediaInfo && r.mediaInfo.status > 1); };
    const musicAskLine = (x) => line(null, `data-mask="${x.kind}:${x.id}"`, x.cover, x.title, `${x.kind === "album" ? "Album" : "Song"} · ${x.artist}`, true, !!x.asked);
    const bookPic = (u) => (u ? u.replace(/^\//, "") + (u.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token) : "");
    const bookLine = (b) => line(`#/book/${enc(b.id)}`, "", bookPic(b.cover), b.name, [({ manga: "Manga", comic: "Comic", audiobook: "Audiobook" })[b.kind] || "Book", b.author].filter(Boolean).join(" · "));
    const personLine = (x) => line(`#/person/${x.Id}`, "", x.ImageTags && x.ImageTags.Primary ? B.img(x.Id, "Primary", { h: 120, tag: x.ImageTags.Primary }) : "", x.Name, "Person");
    // Ask for it: movies, shows, songs and albums together, closest names first (movies and shows win a tie)
    const askAll = [...ask.map((x) => ({ name: x.title || x.name, html: titleLine(x) })), ...mAsk.map((x) => ({ name: x.title, html: musicAskLine(x) }))]
      .map((e, i) => ({ e, i, s: score(e.name) })).sort((a, b) => a.s - b.s || a.i - b.i).map((x) => x.e.html);
    // What the words describe joins the names (first when few names match, like the TV app)
    const withWords = (list, type) => {
      const have = new Set(list.map((x) => x.Id));
      const extra = described.filter((x) => x.Type === type && !have.has(x.Id));
      return list.length < 3 ? [...extra, ...list] : [...list, ...extra];
    };
    const doors = [
      [DOORS.movies, withWords(movies, "Movie").map(itemLine)],
      [DOORS.shows, withWords(shows, "Series").map(itemLine)],
      ...(kids ? [] : [[{ name: "Music", color: "#B69CFF" }, mine.map(itemLine)]]),
      [DOORS.live, chans.map(chLine)],
      [DOORS.games, gl.map(gameLine)],
      ...(kids ? [] : [[{ name: "Books", color: "#B9E36A" }, books.map(bookLine)]]),
    ];
    const below = kids ? [] : [[{ name: "Ask for it", color: "#F1F2F5", ask: true }, askAll], [{ name: "People", color: "#9AA0AE" }, people.map(personLine)]];
    const all = [...doors, ...below];
    const full = doors.filter((d) => d[1].length), lower = below.filter((d) => d[1].length), none = all.filter((d) => !d[1].length);
    const card = ([door, lines], max) => `<div class="dcard ${door.ask ? "askdoor" : ""}" style="--c:${door.color}"><h4>${esc(door.name)} <small class="dim">${lines.length}</small>${lines.length > max ? `<button class="seeall" data-seeall="${esc(door.name)}">See all ›</button>` : ""}</h4><div class="dl ${door.ask ? "two" : ""}">${lines.slice(0, max).join("")}</div></div>`;
    const anything = full.length || lower.length;
    lastDoors = all;
    $("#sres").innerHTML = `${anything && none.length ? `<div class="wrap dim" style="margin:0;padding-top:10px;padding-bottom:10px">Nothing in ${none.map((d) => esc(d[0].name)).join(" · ")}</div>` : ""}
    ${full.length ? `<div class="dcards">${full.map((d) => card(d, 3)).join("")}</div>` : ""}
    ${lower.length ? `<div class="dbelow">${lower.map((d) => card(d, d[0].ask ? 4 : 3)).join("")}</div>` : ""}
    ${!anything ? `<div class="wrap empty">Nothing found for “${esc(t)}”. Check the spelling${kids ? "" : ", or ask for it on the Requests page"}.</div>` : ""}`;
    lastAsk = mAsk;
  };
  const remember = () => {
    const t = box.value.trim(); if (!t) return;
    store(recentKey(), JSON.stringify([t, ...loadJson(recentKey(), []).filter((x) => x.toLowerCase() !== t.toLowerCase())].slice(0, 6)));
  };
  let timer;
  box.oninput = () => { clearTimeout(timer); timer = setTimeout(() => { history.replaceState(null, "", "#/search?q=" + enc(box.value)); run(box.value); }, 250); };
  box.onkeydown = (e) => { if (e.key === "Enter") { remember(); const f = $("#sres a, #sres button"); if (f) f.focus(); } };
  $("#schips").addEventListener("click", (e) => {
    const c = e.target.closest("[data-sugg]");
    if (!c) return;
    box.value = c.dataset.sugg; remember(); history.replaceState(null, "", "#/search?q=" + enc(box.value)); run(box.value);
  });
  $("#sres").addEventListener("click", (e) => {
    const r = e.target.closest("[data-recent]");
    if (r) { box.value = r.dataset.recent; run(box.value); return; }
    const un = e.target.closest("[data-unrecent]");
    if (un) { store(recentKey(), JSON.stringify(loadJson(recentKey(), []).filter((x) => x !== un.dataset.unrecent))); before(); return; }
    if (e.target.closest("[data-clearrecent]")) { store(recentKey(), JSON.stringify([])); before(); return; }
    const sa = e.target.closest("[data-seeall]");
    if (sa) {
      // See all: that door's whole list in a side panel
      const d = lastDoors.find((x) => x[0].name === sa.dataset.seeall);
      if (!d) return;
      const s = B.side(`<h3>${esc(d[0].name)} <small class="dim">${d[1].length}</small></h3><div class="dl seelist">${d[1].join("")}</div>`);
      s.addEventListener("click", (ev) => { if (ev.target.closest("a")) { remember(); B.closeAll(); } });
      return;
    }
    const mk = e.target.closest("[data-mask]");
    if (mk) {
      const [kind, id] = mk.dataset.mask.split(":");
      const x = lastAsk.find((y) => y.kind === kind && String(y.id) === id);
      if (!x) return;
      remember();
      const ask = async (k, aid, name) => { await B.call("POST", "api/music/request", { kind: k, id: +aid }); run(box.value); return `Asked for ${name}. Tower is getting it.`; };
      const s = B.side(`<h3>${esc(x.title)}</h3><p class="dim">${esc(x.artist)}${x.asked ? ` · Already asked for: ${esc(x.asked)}` : ""}</p>
        ${kind === "song" ? `<div class="acts" style="justify-content:flex-start;margin:4px 0 12px">${B.previewBtn(x.id)}<span class="dim">Hear 30 seconds</span></div>` : ""}
        ${kind === "song" ? `<button class="opt" data-pick="song">Ask for this song</button>${x.album_id && plain(x.album) !== plain(x.title) ? `<button class="opt" data-pick="album">Ask for the whole album (${esc(x.album)})</button>` : ""}` : `<button class="opt" data-pick="album">Ask for this album</button>`}`);
      s.addEventListener("click", (ev) => {
        const pick = ev.target.closest("[data-pick]");
        if (pick) B.busy(pick, async () => { const msg = await (pick.dataset.pick === "song" ? ask("song", x.id, x.title) : ask("album", x.album_id || x.id, x.album || x.title)); B.closeAll(); return msg; });
      });
      return;
    }
    if (e.target.closest("a,[data-ask]")) remember();
  });
  run(box.value);
});
