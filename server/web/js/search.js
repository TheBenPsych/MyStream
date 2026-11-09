/* Benflix web: Search. Before typing: recent searches, Popular and Browse. While typing: a best
   match line, then the doors side by side like Home (Movies, Shows, Live TV, Games, Music, a white Ask
   for it door with movies, shows, songs and albums that aren't here yet, and People). Each door is a list
   you scroll down: picture, name and year on each line. Doors with nothing are named in one small line.
   Kids get the four doors only (no music, no asking). On phones the doors stack. */
"use strict";

const recentKey = () => "benflix.search." + B.me.id;
B.route(/^\/search/, async (m, nav) => {
  const kids = B.me.kids;
  B.page().innerHTML = B.top("Search", null) + `<div class="searchtop"><input class="field" id="sq" placeholder="Search movies, shows, channels, games, music and people" autocomplete="off" value="${esc(B.q("q") || "")}"></div>
    <div class="bestline" id="best"></div><div id="sres"></div>`;
  B.wireTop();
  const box = $("#sq");
  box.focus();
  const before = async () => {
    const recent = loadJson(recentKey(), []);
    $("#best").innerHTML = "";
    $("#sres").innerHTML = `${recent.length ? `<section class="rowx"><h3>Recent searches</h3><div class="wrap row" style="padding-bottom:0">${recent.map((r) => `<button class="pill" data-recent="${esc(r)}">${esc(r)}</button>`).join("")}</div></section>` : ""}
      <div id="popular">${B.spinner()}</div>
      <section class="rowx"><h3>Browse</h3><div class="tiles2">
        <a class="tile2" href="#/browse/movies/genres">Movie genres</a><a class="tile2" href="#/browse/shows/genres">Show genres</a>
        <a class="tile2" href="#/browse/shows/studios">Networks</a><a class="tile2" href="#/live">Channel types</a><a class="tile2" href="#/games">Consoles</a></div></section>`;
    const [mv, tv] = await Promise.all([B.trending("Movie").catch(() => []), B.trending("Series").catch(() => [])]);
    if (!B.alive(nav) || !$("#popular")) return;
    const mix = []; for (let i = 0; i < 10; i++) { if (mv[i]) mix.push(mv[i]); if (tv[i]) mix.push(tv[i]); }
    $("#popular").innerHTML = B.row("Popular in your house", mix.map((it) => B.posterCard(it)));
  };
  let games = null, seq = 0, lastAsk = [];
  const plain = (x) => (x || "").normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[(\[].*?[)\]]/g, " ").replace(/[^a-z0-9]+/g, " ").trim();
  const run = async (text) => {
    const my = ++seq;
    const t = text.trim();
    if (!t) return before();
    $("#best").innerHTML = `<span class="dim">Looking…</span>`;
    const u = B.uid(), F = B.FIELDS;
    const [movies, shows, people, chans, ask, mine, cat] = await Promise.all([
      B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Movie&Recursive=true&Limit=30&Fields=${F}`).then((r) => B.keep(r.Items)).catch(() => []),
      B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Series&Recursive=true&Limit=30&Fields=${F}`).then((r) => B.keep(r.Items)).catch(() => []),
      kids ? [] : B.get(`/Persons?userId=${u}&searchTerm=${enc(t)}&Limit=16&EnableImages=true`).then((r) => r.Items || []).catch(() => []),
      B.channels().then((l) => l.filter((c) => {
        const now = c._progs.find((p) => new Date(p.StartDate) <= Date.now() && new Date(p.EndDate) > Date.now());
        const s = t.toLowerCase();
        return (c.Name || "").toLowerCase().includes(s) || ((now && now.Name) || "").toLowerCase().includes(s);
      })).catch(() => []),
      kids ? [] : B.seerr(`/search?query=${enc(t)}&page=1`).then((r) => (r.results || []).filter((x) => (x.mediaType === "movie" || x.mediaType === "tv") && !B.onBenflix(x))).catch(() => []),
      kids ? [] : B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Audio,MusicAlbum,MusicArtist,Playlist&Recursive=true&Limit=40&Fields=ChildCount,ProductionYear`).then((r) => (r.Items || []).filter((x) => x.Type !== "Playlist" || x.MediaType !== "Video")).catch(() => []),
      kids || !B.me.admin || t.length < 2 ? null : B.call("GET", `api/music/search?q=${enc(t)}`).catch(() => null),
    ]);
    // Songs and albums to ask for: not already in your music
    const have = new Set(mine.map((x) => plain(x.Name) + "|" + plain(x.AlbumArtist || (x.Artists || [])[0] || "")));
    const mAsk = cat ? [...(cat.songs || []).slice(0, 12).map((x) => Object.assign({ kind: "song" }, x)), ...(cat.albums || []).slice(0, 6).map((x) => Object.assign({ kind: "album", album_id: x.id, album: x.title }, x))]
      .filter((x) => x.asked !== "In your music" && !have.has(plain(x.title) + "|" + plain(x.artist))) : [];
    if (games === null) games = await B.call("GET", "api/games").then((g) => g.consoles.flatMap((c) => c.games.filter((x) => x.playable).map((x) => Object.assign({ console: c.name, slug: c.slug }, x)))).catch(() => []);
    if (my !== seq || !B.alive(nav)) return;
    const gl = games.filter((g) => g.name.toLowerCase().includes(t.toLowerCase()));
    // Closest name first
    const score = (n) => { n = (n || "").toLowerCase(); const s = t.toLowerCase(); return n === s ? 0 : n.startsWith(s) ? 1 : n.includes(" " + s) ? 2 : 3; };
    [movies, shows].forEach((l) => l.sort((a, b) => score(a.Name) - score(b.Name)));
    const best = [...movies, ...shows].sort((a, b) => score(a.Name) - score(b.Name))[0];
    $("#best").innerHTML = best ? `Best match: <b>${esc(best.Name)}</b> <span class="dim">· ${esc([best.Type === "Series" ? "Show" : "Movie", best.ProductionYear, (best.Genres || []).slice(0, 2).join(", ")].filter(Boolean).join(" · "))}</span>` : "";
    // One line per thing: the picture with the name and year beside it (Ben, 2026-11-09)
    const line = (href, attrs, img, name, sub, square, asked) => `<${href ? `a href="${href}"` : "button"} class="sline" ${attrs || ""} title="${esc(name)}"><span class="pic ${square ? "sq" : ""}" ${img ? `style="background-image:url('${img}')"` : ""}></span><span class="txt"><b>${esc(name)}</b><small>${esc(sub || "")}</small>${asked ? `<em>Asked</em>` : ""}</span></${href ? "a" : "button"}>`;
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
    const personLine = (x) => line(`#/person/${x.Id}`, "", x.ImageTags && x.ImageTags.Primary ? B.img(x.Id, "Primary", { h: 120, tag: x.ImageTags.Primary }) : "", x.Name, "Person");
    // Ask for it: movies, shows, songs and albums together, closest names first (movies and shows win a tie)
    const askAll = [...ask.map((x) => ({ name: x.title || x.name, html: titleLine(x) })), ...mAsk.map((x) => ({ name: x.title, html: musicAskLine(x) }))]
      .map((e, i) => ({ e, i, s: score(e.name) })).sort((a, b) => a.s - b.s || a.i - b.i).map((x) => x.e.html);
    const doors = [
      [DOORS.movies, movies.map(itemLine)],
      [DOORS.shows, shows.map(itemLine)],
      [DOORS.live, chans.map(chLine)],
      [DOORS.games, gl.map(gameLine)],
      ...(kids ? [] : [[{ name: "Music", color: "#B69CFF" }, mine.map(itemLine)], [{ name: "Ask for it", color: "#F1F2F5", ask: true }, askAll], [{ name: "People", color: "#9AA0AE" }, people.map(personLine)]]),
    ];
    const full = doors.filter((d) => d[1].length), none = doors.filter((d) => !d[1].length);
    $("#sres").innerHTML = `${full.length && none.length ? `<div class="wrap dim" style="margin:0 0 6px">Nothing in ${none.map((d) => esc(d[0].name)).join(" · ")}</div>` : ""}
    ${full.length ? `<div class="doorcols lists" style="grid-template-columns:repeat(${full.length},minmax(0,1fr))">${full.map(([door, lines]) => `<div class="doorcol ${door.ask ? "askdoor" : ""}"><h4 style="--c:${door.color}"><i></i>${esc(door.name)} <small class="dim">${lines.length}</small></h4><div class="dlist">${lines.join("")}</div></div>`).join("")}</div>` : ""}
    ${!full.length ? `<div class="wrap empty">Nothing found for “${esc(t)}”. Check the spelling${kids ? "" : ", or ask for it on the Requests page"}.</div>` : ""}`;
    lastAsk = mAsk;
  };
  const remember = () => {
    const t = box.value.trim(); if (!t) return;
    store(recentKey(), JSON.stringify([t, ...loadJson(recentKey(), []).filter((x) => x.toLowerCase() !== t.toLowerCase())].slice(0, 6)));
  };
  let timer;
  box.oninput = () => { clearTimeout(timer); timer = setTimeout(() => { history.replaceState(null, "", "#/search?q=" + enc(box.value)); run(box.value); }, 250); };
  box.onkeydown = (e) => { if (e.key === "Enter") { remember(); const f = $("#sres a, #sres button"); if (f) f.focus(); } };
  $("#sres").addEventListener("click", (e) => {
    const r = e.target.closest("[data-recent]");
    if (r) { box.value = r.dataset.recent; run(box.value); return; }
    const mk = e.target.closest("[data-mask]");
    if (mk) {
      const [kind, id] = mk.dataset.mask.split(":");
      const x = lastAsk.find((y) => y.kind === kind && String(y.id) === id);
      if (!x) return;
      remember();
      const ask = async (k, aid, name) => { await B.call("POST", "api/music/request", { kind: k, id: +aid }); run(box.value); return `Asked for ${name}. Tower is getting it.`; };
      const s = B.side(`<h3>${esc(x.title)}</h3><p class="dim">${esc(x.artist)}${x.asked ? ` · Already asked for: ${esc(x.asked)}` : ""}</p>
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
