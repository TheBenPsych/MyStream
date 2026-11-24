/* Glowfin new look (Ben picked 2026-11-22): Search opens nearly empty (the box, your recent searches and a
   Browse genres and moods pill; nothing else loads). Type anywhere on the page, or press /, and the words go into
   the box. Kind pills sit beside the box (Everything, Movies 6, ...), the top describes the result you point at
   (art, name, facts, 2-line story, score pill, like Home) and the cards are just cover art. With nothing on
   Glowfin, the Ask for it rows come first with a yellow "Not on Glowfin yet" tag.
   Older notes: Benflix web: Search. Typing starts in the box straight away. Before typing: your last 6 searches (each with a ✕,
   plus Clear all), Popular and Browse. While typing: whole-name suggestions as chips under the box, then rows like
   Home (Ben 2026-11-18): Movies, Shows, Anime, Music, Live TV, Games, Books, Ask for it (movies and shows, then music)
   and People, each a row of poster cards with See all at the end. Rows with nothing are named in one small line.
   Kids get Movies, Shows, Anime, Live TV and Games only. */
"use strict";

const recentKey = () => "benflix.search." + B.me.id;
// Design check (Ben 2026-11-24): Back from a result lands on the same results and the same card straight away,
// instead of "Looking…" while everything is asked for again. The last results are kept for 10 minutes.
let lastShown = null;
B.route(/^\/search/, async (m, nav) => {
  const kids = B.me.kids;
  B.page().innerHTML = B.top("Search", null) + `<div class="srch"><div class="searchtop sx-top"><label class="sx-box">${I("search")}<input class="field" id="sq" placeholder="Search movies, shows, music, books and people" autocomplete="off" value="${esc(B.q("q") || "")}"></label>
    <nav class="ptabs sx-kinds" id="skinds" aria-label="Kinds"></nav><span class="sx-tip dim">Just start typing anywhere, or press <kbd>/</kbd></span></div>
    <div class="schips" id="schips"></div><div class="bestline" id="best"></div>${B.doorTop()}<div class="rows" id="sres"></div></div>`;
  B.wireTop();
  const box = $("#sq");
  box.focus();
  const dtop = $("#doorTop");
  dtop.hidden = !box.value.trim();
  // The top describes the result you point at: library titles like Home, things to ask for like Requests
  const tmdbArt = (r) => (r && r.backdropPath ? `https://image.tmdb.org/t/p/w1280${r.backdropPath}` : "");
  const plainDescribe = (x) => `<div class="dt-name"><div class="t">${esc(x.name)}</div></div><div class="meta">${x.sub ? `<span>${esc(x.sub)}</span>` : ""}</div>${x.ask ? `<div class="dt-tag sx-ask">${x.asked ? "Asked for, on its way" : "Not on Glowfin yet · click to ask for it"}</div>` : ""}`;
  B.wireDescribe(null, {
    describe: (it) => (it._plain ? plainDescribe(it._plain) : it.mediaType ? B.rqDescribe(it) : B.describe(it)),
    art: (it) => (it._plain ? "" : it.mediaType ? tmdbArt(it) : B.backdrop(it, 1600)),
  });
  // Type anywhere on the page (or press /) and the words go into the box
  const typeAnywhere = (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    const t = e.target, inField = t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable);
    if (inField || $(".side, .modal")) return;
    if (e.key === "/") { e.preventDefault(); box.focus(); box.select(); return; }
    if (e.key.length === 1 && /\S/.test(e.key)) box.focus();
  };
  document.addEventListener("keydown", typeAnywhere);
  B.onLeave(() => document.removeEventListener("keydown", typeAnywhere));
  let kind = null;
  const before = async () => {
    const recent = loadJson(recentKey(), []);
    $("#best").innerHTML = "";
    $("#schips").innerHTML = "";
    $("#skinds").innerHTML = "";
    dtop.hidden = true;
    // Opens nearly empty: recent searches as pills (each with ✕) and the Browse genres and moods pill
    $("#sres").innerHTML = `<div class="wrap row sx-recent">${recent.length ? `<span class="dim sx-lab">Recent</span>` : ""}${recent.map((r) => `<span class="rchip"><button class="pill" data-recent="${esc(r)}">${esc(r)}</button><button class="pill rx" data-unrecent="${esc(r)}" title="Remove" aria-label="Remove ${esc(r)}">✕</button></span>`).join("")}<button class="pill ghost" data-browse>Browse genres and moods</button></div>
      <div class="wrap dim sx-empty">Results show here as you type</div>`;
  };
  // Browse genres and moods: moods, genres, channel types, consoles and Watch in order on their own view
  const browse = () => {
    $("#sres").innerHTML = `<section class="rowx"><h3>Browse genres and moods</h3><div class="tiles2">
        <a class="tile2" href="#/browse/movies/genres">Movie genres</a><a class="tile2" href="#/browse/shows/genres">Show genres</a>
        <a class="tile2" href="#/live">Channel types</a><a class="tile2" href="#/games">Consoles</a></div></section>
      <div class="wrap"><button class="pill ghost" data-unbrowse>‹ Back to Search</button></div>`;
    const f = $("#sres .tile2"); if (f) f.focus();
  };
  let games = null, seq = 0, lastAsk = [], lastDoors = [], plainSeq = 0;
  const plain = (x) => (x || "").normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[(\[].*?[)\]]/g, " ").replace(/[^a-z0-9]+/g, " ").trim();
  const run = async (text) => {
    const my = ++seq;
    const t = text.trim();
    if (!t) return before();
    dtop.hidden = false;
    $("#best").innerHTML = `<span class="dim">Looking…</span>`;
    const u = B.uid(), F = B.FIELDS;
    // Search speed (Ben 2026-11-21): everything is asked for at once. What Tower itself knows (movies, shows, music,
    // channels, books, people) is shown the moment it answers; Ask for it, plain-words results and the games list come
    // from slower places and join the rows when they arrive. Before, nothing showed until the slowest one finished.
    const NC = "&EnableTotalRecordCount=false";
    const words = B.extras ? B.extras.parseWords(t) : null;
    const slow = Promise.all([
      kids ? [] : Promise.all([B.seerr(`/search?query=${enc(t)}&page=1`), B.libTmdb().catch(() => null)]).then(([r]) => (r.results || []).filter((x) => (x.mediaType === "movie" || x.mediaType === "tv") && !B.onBenflix(x))).catch(() => []),
      kids || t.length < 2 ? null : B.call("GET", `api/music/search?q=${enc(t)}`).catch(() => null),
      // "funny 90s movie under 2 hours": the words become filters (extras.js), searched beside the names
      words ? B.extras.searchWords(words).catch(() => []) : [],
      games !== null ? games : B.call("GET", "api/games").then((g) => g.consoles.flatMap((c) => c.games.filter((x) => (B.webPlayable ? B.webPlayable(c.slug) : x.playable)).map((x) => Object.assign({ console: c.name, slug: c.slug }, x)))).catch(() => []).then((l) => (games = l)),
    ]);
    const quick = await Promise.all([
      B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Movie&Recursive=true&Limit=30&Fields=${F}${NC}`).then((r) => B.keep(r.Items)).catch(() => []),
      B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Series&Recursive=true&Limit=30&Fields=${F}${NC}`).then((r) => B.keep(r.Items)).catch(() => []),
      kids ? [] : B.get(`/Persons?userId=${u}&searchTerm=${enc(t)}&Limit=16&EnableImages=true`).then((r) => r.Items || []).catch(() => []),
      // Kids search only finds kids titles: live programmes have no kids rating
      kids ? [] : B.channels().then((l) => l.filter((c) => {
        const now = c._progs.find((p) => new Date(p.StartDate) <= Date.now() && new Date(p.EndDate) > Date.now());
        const s = t.toLowerCase();
        return (c.Name || "").toLowerCase().includes(s) || ((now && now.Name) || "").toLowerCase().includes(s);
      })).catch(() => []),
      kids ? [] : B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Audio,MusicAlbum,MusicArtist,Playlist&Recursive=true&Limit=40&Fields=ChildCount,ProductionYear${NC}`).then((r) => (r.Items || []).filter((x) => x.Type !== "Playlist" || x.MediaType !== "Video")).catch(() => []),
      kids ? [] : B.cached("books-lib", 15000, () => B.call("GET", "api/books/library")).then((l) => (l.titles || []).filter((b) => !b.notHere)).catch(() => []),
    ]);
    if (my !== seq || !B.alive(nav)) return;
    let painted = null;
    // [more] is what the slower places found, or null while they are still looking
    const paint = (more) => {
    const [movies, shows, people, chans, mine, shelf] = quick;
    const [ask, cat, described] = more || [[], null, []];
    // Songs and albums to ask for: not already in your music
    const have = new Set(mine.map((x) => plain(x.Name) + "|" + plain(x.AlbumArtist || (x.Artists || [])[0] || "")));
    const mAsk = cat ? [...(cat.songs || []).slice(0, 12).map((x) => Object.assign({ kind: "song" }, x)), ...(cat.albums || []).slice(0, 6).map((x) => Object.assign({ kind: "album", album_id: x.id, album: x.title }, x))]
      .filter((x) => !x.have && x.asked !== "In your music" && !have.has(plain(x.title) + "|" + plain(x.artist))) : [];
    const gl = (games || []).filter((g) => g.name.toLowerCase().includes(t.toLowerCase()));
    // Closest name first
    const score = (n) => { n = (n || "").toLowerCase(); const s = t.toLowerCase(); return n === s ? 0 : n.startsWith(s) ? 1 : n.includes(" " + s) ? 2 : 3; };
    [movies, shows].forEach((l) => l.sort((a, b) => score(a.Name) - score(b.Name)));
    const best = [...movies, ...shows].sort((a, b) => score(a.Name) - score(b.Name))[0];
    // Nothing has what was typed in its name: the matches are close spellings, so offer the closest one
    const low = t.toLowerCase();
    const typo = t.length >= 3 && !described.length && ![...movies, ...shows].some((x) => (x.Name || "").toLowerCase().includes(low)) ? best : null;
    // Plain-words searches say so; a typo offers the closest name
    if (more) $("#best").innerHTML = described.length ? `<span class="idea">Searching by idea</span> <b>${esc(words.label)}</b> <span class="dim">· ${described.length} found</span>`
      : typo ? `Did you mean <a href="${B.hrefOf(typo)}"><b>${esc(typo.Name)}</b></a>? <span class="dim">· ${esc([typo.Type === "Series" ? "Show" : "Movie", typo.ProductionYear].filter(Boolean).join(" · "))}</span>` : "";
    // Books and audiobooks on Tower: the name, author or series has the words
    const pt = plain(t);
    const books = shelf.filter((b) => [b.name, b.author, b.series].some((x) => plain(x).includes(pt)))
      .sort((a, b) => (plain(a.name).startsWith(pt) ? 0 : 1) - (plain(b.name).startsWith(pt) ? 0 : 1) || a.name.length - b.name.length).slice(0, 40);
    // Whole-name suggestions as chips under the box: the exact name first (Ben 2026-11-19: "Mulan" only offered
    // "Mulan II"), then names that start with the words
    const seen = new Set();
    const sugg = [...movies, ...shows, ...mine, ...books.map((b) => ({ Name: b.name })), ...gl.map((g) => ({ Name: g.name })), ...chans]
      .map((x) => (x.Name || "").trim()).filter((n) => { const k = plain(n); if (!k.includes(pt) || seen.has(k)) return false; seen.add(k); return true; })
      .map((n, i) => ({ n, i, s: plain(n) === pt ? 0 : plain(n).startsWith(pt) ? 1 : 2 })).sort((a, b) => a.s - b.s || a.i - b.i).slice(0, 6).map((x) => x.n);
    const bold = (n) => { const at = n.toLowerCase().indexOf(t.toLowerCase()); return at < 0 ? esc(n) : `${esc(n.slice(0, at))}<b>${esc(n.slice(at, at + t.length))}</b>${esc(n.slice(at + t.length))}`; };
    $("#schips").innerHTML = sugg.map((n) => `<button class="pill sg" data-sugg="${esc(n)}"><span>${bold(n)}</span></button>`).join("");
    // Ben 2026-11-18: rows like Home. Each thing is a poster card in its row (squares for music and channels, circles
    // for people) and the same thing as one line in the See all list
    const line = (href, attrs, img, name, sub, square, asked, round) => {
      // Not a library title: the top still names it when you point at it
      if (!/data-id=/.test(attrs || "")) {
        const key = "sx" + (++plainSeq);
        B.items[key] = { _plain: { name, sub, ask: /data-mask=/.test(attrs || ""), asked } };
        attrs = `${attrs || ""} data-id="${key}"`;
      }
      const tag = href ? `a href="${href}"` : "button";
      const end = href ? "a" : "button";
      const isAsk = /data-(m)?ask=/.test(attrs || "");
      const shape = round ? "rd" : square ? "sq" : "";
      return {
        c: `<${tag} class="poster scard ${shape}" ${attrs || ""} title="${esc(name)}"><div class="im">${img ? `<img class="art" src="${img}" alt="" loading="lazy" decoding="async">` : `<div class="noart"><span class="nt">${esc(name)}</span></div>`}${isAsk ? `<span class="askp">${asked ? "Asked" : "Ask"}</span>` : ""}</div><div class="cap"><span class="nm">${esc(name)}</span><small>${esc(sub || "")}</small></div></${end}>`,
        l: `<${tag} class="sline big" ${attrs || ""} title="${esc(name)}"><span class="pic ${square ? "sq" : ""}" ${img ? `style="background-image:url('${img}')"` : ""}></span><span class="txt"><b>${esc(name)}</b><small>${esc(sub || "")}</small>${asked ? `<em>Asked</em>` : ""}</span></${end}>`,
      };
    };
    const kindOf = { Movie: "Movie", BoxSet: "Collection", Series: "Show", Episode: "Episode", Audio: "Song", MusicAlbum: "Album", MusicArtist: "Artist", Playlist: "Playlist" };
    const itemLine = (x) => {
      B.items[x.Id] = x;
      const music = ["Audio", "MusicAlbum", "MusicArtist", "Playlist"].includes(x.Type);
      const tag = x.ImageTags && x.ImageTags.Primary;
      const img = tag ? B.img(x.Id, "Primary", { h: 360, tag }) : x.AlbumId && music ? B.img(x.AlbumId, "Primary", { h: 360 }) : "";
      const href = x.Type === "Audio" ? (x.AlbumId ? `#/album/${x.AlbumId}` : "#/songs") : x.Type === "MusicAlbum" ? `#/album/${x.Id}` : x.Type === "MusicArtist" ? `#/artist/${x.Id}` : x.Type === "Playlist" ? `#/playlist/${x.Id}` : B.hrefOf(x);
      const sub = [kindOf[x.Type], music ? x.AlbumArtist || (x.Artists || [])[0] : x.ProductionYear].filter(Boolean).join(" · ");
      return line(href, `data-id="${esc(x.Id)}"`, img, x.Name, sub, music);
    };
    const chLine = (c) => line(`#/watch/${c.Id}`, "", c.ImageTags && c.ImageTags.Primary ? B.img(c.Id, "Primary", { h: 300, tag: c.ImageTags.Primary }) : "", c.Name, [c.ChannelNumber, "Live channel"].filter(Boolean).join(" · "), true);
    const gameLine = (g) => line(`#/game/${g.id}`, "", g.cover ? `${g.cover}?api_key=${enc(B.me.token)}` : "", g.name, g.console);
    const titleLine = (r) => { B.askCard(r); return line(null, `data-ask="${esc(r.mediaType)}:${esc(r.id)}" data-id="s${esc(r.mediaType + r.id)}"`, r.posterPath ? `https://image.tmdb.org/t/p/w342${r.posterPath}` : "", r.title || r.name || "", [r.mediaType === "tv" ? "Show" : "Movie", (r.releaseDate || r.firstAirDate || "").slice(0, 4)].filter(Boolean).join(" · "), false, r.mediaInfo && r.mediaInfo.status > 1); };
    const musicAskLine = (x) => line(null, `data-mask="${x.kind}:${x.id}"`, x.cover, x.title, `${x.kind === "album" ? "Album" : "Song"} · ${x.artist}`, true, !!x.asked);
    const bookPic = (u) => (u ? u.replace(/^\//, "") + (u.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token) : "");
    const bookLine = (b) => line(`#/book/${enc(b.id)}`, "", bookPic(b.cover), b.name, [b.kind === "audiobook" ? "Audiobook" : b.kind === "manga" ? "Manga" : "Book", b.author].filter(Boolean).join(" · "));
    const personLine = (x) => line(`#/person/${x.Id}`, "", x.ImageTags && x.ImageTags.Primary ? B.img(x.Id, "Primary", { h: 300, tag: x.ImageTags.Primary }) : "", x.Name, "Person", false, false, true);
    // Ask for it (Ben 2026-11-18): movies, shows and anime always first, songs and albums after. Closest names first inside each group
    const closestFirst = (list) => list.map((e, i) => ({ e, i, s: score(e.name) })).sort((a, b) => a.s - b.s || a.i - b.i).map((x) => x.e.html);
    const askTitles = closestFirst(ask.map((x) => ({ name: x.title || x.name, html: titleLine(x) })));
    const askMusic = closestFirst(mAsk.map((x) => ({ name: x.title, html: musicAskLine(x) })));
    // What the words describe joins the names (first when few names match, like the TV app)
    const withWords = (list, type) => {
      const have = new Set(list.map((x) => x.Id));
      const extra = described.filter((x) => x.Type === type && !have.has(x.Id));
      return list.length < 3 ? [...extra, ...list] : [...list, ...extra];
    };
    // Glowfin: anime shows and movies get their own row, like the Anime page
    const anime = (x) => /anime/i.test(x.Path || "") || (x.Genres || []).some((g) => /^anime$/i.test(g));
    const mv = withWords(movies, "Movie"), sh = withWords(shows, "Series");
    // Ben 2026-11-18: one row per kind, always in this order. Movies and shows to ask for sit above music to ask for
    const rows = [
      [DOORS.movies, mv.filter((x) => !anime(x)).map(itemLine)],
      [DOORS.shows, sh.filter((x) => !anime(x)).map(itemLine)],
      [{ name: "Anime", color: DOORS.shows.color, quiet: true }, [...sh, ...mv].filter(anime).map(itemLine)],
      ...(kids ? [] : [[{ name: "Music", color: "#7AA7FF" }, mine.map(itemLine)]]),
      [DOORS.live, chans.map(chLine)],
      [games === null ? Object.assign({ wait: true }, DOORS.games) : DOORS.games, gl.map(gameLine)],
      ...(kids ? [] : [
        [{ name: "Books", color: "#7AA7FF" }, books.map(bookLine)],
        [{ name: "Ask for it: movies and shows", short: "Ask for it", color: "#F1F2F5", wait: !more }, askTitles],
        [{ name: "Ask for it: music", color: "#7AA7FF", quiet: true }, askMusic],
        [{ name: "People", color: "#9AA0AE" }, people.map(personLine)],
      ]),
    ];
    let full = rows.filter((d) => d[1].length);
    const none = rows.filter((d) => !d[1].length && !d[0].quiet && !d[0].wait);
    // Nothing on Glowfin (people aside): the Ask for it rows come first, and say so
    const isAsk = (d) => /^Ask for it/.test(d[0].name);
    const askOnly = more && full.some(isAsk) && full.every((d) => isAsk(d) || d[0].name === "People");
    if (askOnly) full = [...full.filter(isAsk), ...full.filter((d) => !isAsk(d))];
    // The kind pills beside the box (both Ask for it rows share one)
    const pillOf = (d) => (isAsk(d) ? "Ask for it" : d[0].name);
    const kinds = [];
    full.forEach((d) => { const k = kinds.find((x) => x.k === pillOf(d)); if (k) k.n += d[1].length; else kinds.push({ k: pillOf(d), n: d[1].length }); });
    if (kind && !kinds.some((x) => x.k === kind)) kind = null;
    $("#skinds").innerHTML = kinds.length > 1 ? [`<a href="#" data-kind="" class="${kind ? "" : "on"}">Everything</a>`, ...kinds.map((x) => `<a href="#" data-kind="${esc(x.k)}" class="${kind === x.k ? "on" : ""}">${esc(x.k)} ${x.n}</a>`)].join("") : "";
    const MAX = 10;
    const row = ([door, list]) => `<section class="rowx srow${/^Ask for it/.test(door.name) ? " sx-askrow" : ""}" data-n="${esc(door.name)}" data-k="${esc(/^Ask for it/.test(door.name) ? "Ask for it" : door.name)}" style="--c:${/^Ask for it/.test(door.name) ? "#F2B33D" : door.color}"${kind && kind !== (/^Ask for it/.test(door.name) ? "Ask for it" : door.name) ? " hidden" : ""}><h3><i class="bar"></i>${esc(door.name)} <small class="dim">${list.length}</small>${list.length > MAX ? `<button class="more" data-seeall="${esc(door.name)}">See all ›</button>` : ""}</h3>
      <button class="arrow l" data-scroll="-1" aria-label="Scroll left">${I("left")}</button>
      <div class="strip">${list.slice(0, MAX).map((x) => x.c).join("")}${list.length > MAX ? `<button class="poster see" data-seeall="${esc(door.name)}">See all</button>` : ""}</div>
      <button class="arrow r" data-scroll="1" aria-label="Scroll right">${I("right")}</button></section>`;
    // Still waiting and nothing to show yet: what's on screen stays until there is
    if (!more && !full.length) return;
    lastDoors = rows;
    const html = `${askOnly ? `<div class="wrap dim sx-none">Nothing on Glowfin called “${esc(t)}”, so Ask for it comes first</div>` : ""}${!askOnly && full.length && none.length ? `<div class="wrap dim" style="margin:0;padding-top:10px;padding-bottom:10px">Nothing in ${none.map((d) => esc(d[0].short || d[0].name)).join(" · ")}</div>` : ""}
    ${full.map(row).join("")}
    ${!full.length ? `<div class="wrap empty">Nothing found for “${esc(t)}”. Check the spelling${kids ? "" : ", or ask for it on the Requests page"}.</div>` : ""}`;
    lastAsk = mAsk;
    if (html === painted) return;
    // The second look keeps each row where you scrolled it, and the card you were on
    const res = $("#sres"), at = {}, on = painted !== null && res.contains(document.activeElement) ? document.activeElement.getAttribute("href") : null;
    if (painted !== null) res.querySelectorAll(".srow").forEach((r) => { const st = r.querySelector(".strip"); if (st) at[r.dataset.n] = st.scrollLeft; });
    res.innerHTML = html;
    if (painted !== null) {
      res.querySelectorAll(".srow").forEach((r) => { const st = r.querySelector(".strip"); if (st && at[r.dataset.n]) st.scrollLeft = at[r.dataset.n]; });
      const back = on && [...res.querySelectorAll("a")].find((a) => a.getAttribute("href") === on);
      if (back) back.focus({ preventScroll: true });
    }
    painted = html;
    // The top describes the first result until you point at another
    dtop.hidden = !full.length;
    const first = res.querySelector(".srow:not([hidden]) .strip [data-id]");
    if (first) first.dispatchEvent(new Event("mouseover", { bubbles: true }));
    };
    // The slower places already answered (usual on a second search): one look, not two
    const ready = await Promise.race([slow, new Promise((r) => setTimeout(r, 0, null))]);
    if (my !== seq || !B.alive(nav)) return;
    paint(ready);
    if (ready) return;
    const rest = await slow;
    if (my !== seq || !B.alive(nav)) return;
    paint(rest);
  };
  const remember = () => {
    const t = box.value.trim(); if (!t) return;
    store(recentKey(), JSON.stringify([t, ...loadJson(recentKey(), []).filter((x) => x.toLowerCase() !== t.toLowerCase())].slice(0, 6)));
  };
  let timer;
  box.oninput = () => { clearTimeout(timer); timer = setTimeout(() => { history.replaceState(null, "", "#/search?q=" + enc(box.value)); run(box.value); }, 250); };
  box.onkeydown = (e) => { if (e.key === "Enter") { remember(); const f = $("#sres .strip a, #sres .strip button"); if (f) f.focus(); } };
  $("#schips").addEventListener("click", (e) => {
    const c = e.target.closest("[data-sugg]");
    if (!c) return;
    box.value = c.dataset.sugg; remember(); history.replaceState(null, "", "#/search?q=" + enc(box.value)); run(box.value);
  });
  $("#skinds").addEventListener("click", (e) => {
    const a = e.target.closest("[data-kind]");
    if (!a) return;
    e.preventDefault();
    kind = a.dataset.kind || null;
    $$("#skinds a").forEach((x) => x.classList.toggle("on", x === a));
    $$("#sres .srow").forEach((r) => { r.hidden = !!kind && r.dataset.k !== kind; });
  });
  $("#sres").addEventListener("click", (e) => {
    if (e.target.closest("[data-browse]")) { browse(); return; }
    if (e.target.closest("[data-unbrowse]")) { before(); return; }
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
      const s = B.side(`<h3>${esc(d[0].name)} <small class="dim">${d[1].length}</small></h3><div class="dl seelist">${d[1].map((x) => x.l).join("")}</div>`);
      s.addEventListener("click", (ev) => { if (ev.target.closest("a")) { remember(); B.closeAll(); } });
      return;
    }
    const mk = e.target.closest("[data-mask]");
    if (mk) {
      const [kind, id] = mk.dataset.mask.split(":");
      const x = lastAsk.find((y) => y.kind === kind && String(y.id) === id);
      if (!x) return;
      remember();
      const ask = async (k, aid, name) => { await B.call("POST", "api/music/request", { kind: k, id: +aid }); run(box.value); return B.me.admin ? `Asked for ${name}. Tower is getting it.` : `Asked Ben for ${name}. He says yes before it downloads.`; };
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
    const link = e.target.closest("#sres a[href]");
    if (link) lastShown = { q: box.value, rows: $("#sres").innerHTML, best: $("#best").innerHTML, kinds: $("#skinds").innerHTML, href: link.getAttribute("href"), at: Date.now() };
  });
  const kept = lastShown;
  lastShown = null;
  if (kept && kept.q === box.value && box.value.trim() && Date.now() - kept.at < 600000) {
    dtop.hidden = false;
    $("#sres").innerHTML = kept.rows;
    $("#best").innerHTML = kept.best;
    $("#skinds").innerHTML = kept.kinds;
    const back = Array.from(document.querySelectorAll("#sres a[href]")).find((a) => a.getAttribute("href") === kept.href);
    if (back) back.focus();
    return;
  }
  run(box.value);
});
