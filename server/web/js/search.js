/* Benflix web: Search. Before typing: recent searches, Popular and Browse. While typing: a best
   match line, then door columns (Movies, Shows, Live TV, Games, and a white Ask for it door) with
   2x2 cards each, and People underneath. Kids get the four doors only. */
"use strict";

const recentKey = () => "benflix.search." + B.me.id;
B.route(/^\/search/, async (m, nav) => {
  const kids = B.me.kids;
  B.page().innerHTML = B.top("Search", null) + `<div class="searchtop"><input class="field" id="sq" placeholder="Search movies, shows, channels, games and people" autocomplete="off" value="${esc(B.q("q") || "")}"></div>
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
  let games = null, seq = 0;
  const run = async (text) => {
    const my = ++seq;
    const t = text.trim();
    if (!t) return before();
    $("#best").innerHTML = `<span class="dim">Looking…</span>`;
    const u = B.uid(), F = B.FIELDS;
    const [movies, shows, people, chans, ask] = await Promise.all([
      B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Movie&Recursive=true&Limit=30&Fields=${F}`).then((r) => B.keep(r.Items)).catch(() => []),
      B.get(`/Items?userId=${u}&searchTerm=${enc(t)}&IncludeItemTypes=Series&Recursive=true&Limit=30&Fields=${F}`).then((r) => B.keep(r.Items)).catch(() => []),
      kids ? [] : B.get(`/Persons?userId=${u}&searchTerm=${enc(t)}&Limit=16&EnableImages=true`).then((r) => r.Items || []).catch(() => []),
      B.channels().then((l) => l.filter((c) => {
        const now = c._progs.find((p) => new Date(p.StartDate) <= Date.now() && new Date(p.EndDate) > Date.now());
        const s = t.toLowerCase();
        return (c.Name || "").toLowerCase().includes(s) || ((now && now.Name) || "").toLowerCase().includes(s);
      })).catch(() => []),
      kids ? [] : B.seerr(`/search?query=${enc(t)}&page=1`).then((r) => (r.results || []).filter((x) => (x.mediaType === "movie" || x.mediaType === "tv") && !B.onBenflix(x))).catch(() => []),
    ]);
    if (games === null) games = await B.call("GET", "api/games").then((g) => g.consoles.flatMap((c) => c.games.filter((x) => x.playable).map((x) => Object.assign({ console: c.name, slug: c.slug }, x)))).catch(() => []);
    if (my !== seq || !B.alive(nav)) return;
    const gl = games.filter((g) => g.name.toLowerCase().includes(t.toLowerCase()));
    // Closest name first
    const score = (n) => { n = (n || "").toLowerCase(); const s = t.toLowerCase(); return n === s ? 0 : n.startsWith(s) ? 1 : n.includes(" " + s) ? 2 : 3; };
    [movies, shows].forEach((l) => l.sort((a, b) => score(a.Name) - score(b.Name)));
    const best = [...movies, ...shows].sort((a, b) => score(a.Name) - score(b.Name))[0];
    $("#best").innerHTML = best ? `Best match: <b>${esc(best.Name)}</b> <span class="dim">· ${esc([best.Type === "Series" ? "Show" : "Movie", best.ProductionYear, (best.Genres || []).slice(0, 2).join(", ")].filter(Boolean).join(" · "))}</span>` : "";
    const col = (door, cards, total, seeAll, white) => `<div class="doorcol ${cards.length ? "" : "none"}"><h4 style="--c:${white ? "#F1F2F5" : door.color}"><i></i>${esc(door.name)}</h4>
      ${cards.length ? `<div class="g2">${cards.slice(0, 4).join("")}</div>${total > 4 && seeAll ? `<a class="btn small seeall" href="${seeAll}">See all ${total}</a>` : ""}` : `<div class="dim">None</div>`}</div>`;
    const gameCard = (g) => `<a class="poster" href="#/game/${g.id}" title="${esc(g.name)}"><div class="im" ${g.cover ? `style="background-image:url('${g.cover}?api_key=${enc(B.me.token)}')"` : ""}>${g.cover ? "" : `<div class="noart">${esc(g.name)}</div>`}</div><div class="cap">${esc(g.name)}<small>${esc(g.console)}</small></div></a>`;
    $("#sres").innerHTML = `<div class="doorcols" style="${kids ? "grid-template-columns:repeat(4,1fr)" : ""}">
      ${col(DOORS.movies, movies.map((x) => B.posterCard(x)), movies.length, `#/grid/movies?q=${enc(t)}&title=${enc("Movies matching " + t)}`)}
      ${col(DOORS.shows, shows.map((x) => B.posterCard(x)), shows.length, `#/grid/shows?q=${enc(t)}&title=${enc("Shows matching " + t)}`)}
      ${col(DOORS.live, chans.map(B.chCard), chans.length, "#/live")}
      ${col(DOORS.games, gl.map(gameCard), gl.length, `#/games?q=${enc(t)}`)}
      ${kids ? "" : col({ name: "Ask for it" }, ask.map(B.askCard), ask.length, `#/requests?q=${enc(t)}`, true)}
    </div>
    ${people.length ? `<section class="rowx people-row"><h3>People</h3><div class="strip">${people.map((p) => `<a class="person-c" href="#/person/${p.Id}"><span class="pic" style="${p.ImageTags && p.ImageTags.Primary ? `background-image:url('${B.img(p.Id, "Primary", { h: 240, tag: p.ImageTags.Primary })}')` : ""}">${p.ImageTags && p.ImageTags.Primary ? "" : esc(B.initials(p.Name))}</span>${esc(p.Name)}</a>`).join("")}</div></section>` : ""}
    ${!movies.length && !shows.length && !chans.length && !gl.length && !ask.length ? `<div class="wrap empty">Nothing found for “${esc(t)}”. Check the spelling${kids ? "" : ", or ask for it on the Requests page"}.</div>` : ""}`;
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
    if (e.target.closest("a,[data-ask]")) remember();
  });
  run(box.value);
});
