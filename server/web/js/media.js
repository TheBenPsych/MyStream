/* Benflix web: the Movies and Shows doors. Top area that describes what you point at, rows of
   posters, the movie and show pages, the All movies / All shows grid, collections and people. */
"use strict";

// ---------- top area: describes the poster you point at, picture fades in from the right ----------
B.describe = (it) => {
  if (!it) return "";
  const ud = it.UserData || {};
  const bits = [];
  if (it.Type === "Episode") bits.push(`S${it.ParentIndexNumber || 0} E${it.IndexNumber || 0}`);
  if (it.ProductionYear) bits.push(it.Type === "Series" && it.EndDate && it.Status === "Ended" ? `${it.ProductionYear}–${new Date(it.EndDate).getFullYear()}` : it.ProductionYear);
  if (it.Type === "Series") {
    if (it.Status === "Ended") bits.push("Ended");
    else if (it.AirDays && it.AirDays.length) bits.push(`New episodes on ${it.AirDays[0]}s`);
    if (it.ChildCount) bits.push(`${it.ChildCount} season${it.ChildCount === 1 ? "" : "s"}`);
  } else if (it.RunTimeTicks) bits.push(B.len(B.minutes(it.RunTimeTicks)));
  if (it.OfficialRating) bits.push(it.OfficialRating);
  if (it.Genres && it.Genres.length) bits.push(it.Genres.slice(0, 3).join(", "));
  let kw = "";
  if (ud.PlaybackPositionTicks && it.RunTimeTicks) {
    const left = B.minutes(it.RunTimeTicks - ud.PlaybackPositionTicks);
    kw = `<div class="kwbar"><span class="line"><i style="width:${Math.min(100, ud.PlayedPercentage || 0)}%"></i></span>${B.len(left)} left</div>`;
  }
  const title = it.Type === "Episode" ? `${it.SeriesName}` : it.Name;
  return `<div class="t">${esc(title)}</div>${it.Type === "Episode" ? `<div class="meta" style="color:var(--ink)">${esc(it.Name)}</div>` : ""}
    <div class="meta">${bits.map((b) => `<span>${esc(b)}</span>`).join("")}</div>${B.scores(it)}
    <div class="ov">${esc(it.Overview || "")}</div>${kw}`;
};
B.wireDescribe = (first) => {
  const top = $("#doorTop");
  if (!top) return;
  let timer, shown = null;
  const show = (it) => {
    if (!it || it === shown) return;
    shown = it;
    $(".info", top).innerHTML = B.describe(it);
    const src = B.backdrop(it, 1600);
    const art = $(".art", top);
    art.classList.remove("on");
    if (src) {
      const img = new Image();
      img.onload = () => { if (shown === it) { art.style.backgroundImage = `url('${src}')`; art.classList.add("on"); } };
      img.src = src;
    }
  };
  const pointAt = (e) => {
    const card = e.target.closest("[data-id]");
    if (!card || !B.items[card.dataset.id]) return;
    clearTimeout(timer);
    timer = setTimeout(() => show(B.items[card.dataset.id]), 350);
  };
  B.page().addEventListener("mouseover", pointAt);
  B.page().addEventListener("focusin", pointAt);
  B.onLeave(() => { B.page().removeEventListener("mouseover", pointAt); B.page().removeEventListener("focusin", pointAt); });
  show(first);
};
const doorTop = () => `<section class="door-top" id="doorTop"><div class="art"></div><div class="info"></div></section>`;

// ---------- top 10: what's trending (from Requests), on the server; otherwise the best rated lately ----------
B.trending = async (type) => {
  const kind = type === "Movie" ? "movies" : "tv";
  let ids = [];
  try {
    const pages = await B.cached("trend-" + kind, 1800000, () => Promise.all([1, 2, 3].map((p) => B.get(`/JellyfinEnhanced/jellyseerr/discover/${kind === "movies" ? "movies" : "tv"}?page=${p}&sortBy=popularity.desc`).catch(() => null))));
    pages.forEach((p) => ((p && p.results) || []).forEach((r) => { const id = r.mediaInfo && (r.mediaInfo.jellyfinMediaId || r.mediaInfo.jellyfinMediaId4k); if (id && !ids.includes(id)) ids.push(id); }));
  } catch (e) {}
  const anime = await B.animeIds(type);
  let items = [];
  if (ids.length) {
    const r = await B.get(`/Items?userId=${B.uid()}&Ids=${ids.slice(0, 40).join(",")}&Fields=${B.FIELDS}`);
    const by = {}; (r.Items || []).forEach((x) => { by[x.Id] = x; });
    items = ids.map((id) => by[id]).filter((x) => x && !anime.has(x.Id));
  }
  if (items.length < 6) {
    const y = new Date().getFullYear();
    const r = await B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=${type}&Recursive=true&Years=${y},${y - 1}&SortBy=CommunityRating&SortOrder=Descending&Limit=40&Fields=${B.FIELDS}&ImageTypes=Primary`);
    items = (r.Items || []).filter((x) => !anime.has(x.Id));
  }
  return B.keep(items.slice(0, 10));
};
// Ids of anime titles (anything in a library with "anime" in its name)
B.animeIds = async (type) => B.cached("animeIds-" + type, 600000, async () => {
  const libs = (await B.libs(type === "Movie" ? "movies" : "shows")).filter(B.isAnimeLib);
  const set = new Set();
  for (const l of libs) {
    const r = await B.get(`/Items?userId=${B.uid()}&ParentId=${l.Id}&IncludeItemTypes=${type}&Recursive=true&EnableImages=false&EnableUserData=false`);
    (r.Items || []).forEach((x) => set.add(x.Id));
  }
  return set;
});
const q = (path) => B.get(path).then((r) => B.keep((r && r.Items) || r || []));

// ---------- Movies door ----------
B.route(/^\/movies$/, async (m, nav) => {
  B.page().innerHTML = B.top("Movies", DOORS.movies.color) + doorTop() + `<div class="rows" id="rows">${B.spinner()}</div>`;
  B.wireTop();
  const u = B.uid(), F = B.FIELDS;
  const libs = await B.libs("movies");
  const animeLibs = libs.filter(B.isAnimeLib), standLibs = libs.filter(B.isStandLib);
  const [resume, mine, fresh, top, recent] = await Promise.all([
    q(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Movie&Limit=20&Fields=${F}`).catch(() => q(`/Users/${u}/Items/Resume?IncludeItemTypes=Movie&Limit=20&Fields=${F}`)),
    q(`/Items?userId=${u}&IncludeItemTypes=Movie&Recursive=true&Filters=IsFavorite&SortBy=DateCreated&SortOrder=Descending&Limit=30&Fields=${F}`),
    q(`/Items?userId=${u}&IncludeItemTypes=Movie&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=30&Fields=${F}`),
    B.trending("Movie").catch(() => []),
    q(`/Items?userId=${u}&IncludeItemTypes=Movie&Recursive=true&Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=1&Fields=${F}`),
  ]);
  if (!B.alive(nav)) return;
  const because = recent[0] ? await q(`/Items/${recent[0].Id}/Similar?userId=${u}&Limit=20&Fields=${F}`).catch(() => []) : [];
  const anime = [], stand = [];
  for (const l of animeLibs) anime.push(...(await q(`/Items?userId=${u}&ParentId=${l.Id}&IncludeItemTypes=Movie&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=24&Fields=${F}`)));
  for (const l of standLibs) stand.push(...(await q(`/Items?userId=${u}&ParentId=${l.Id}&IncludeItemTypes=Movie&Recursive=true&SortBy=Random&Limit=24&Fields=${F}`)));
  if (!B.alive(nav)) return;
  const kids = B.me.kids;
  $("#rows").innerHTML = [
    B.row("Keep watching", resume.map((it) => B.posterCard(it, { progress: true }))),
    B.row("My List", mine.map((it) => B.posterCard(it)), { seeAll: "#/grid/movies?mine=1&title=My%20List" }),
    B.row("New on Benflix", fresh.map((it) => B.posterCard(it, { markNew: true })), { seeAll: "#/grid/movies?title=New%20on%20Benflix" }),
    kids ? "" : B.row("Top 10 movies", B.top10(top)),
    recent[0] ? B.row(`Because you watched ${recent[0].Name}`, because.map((it) => B.posterCard(it))) : "",
    B.row("Anime movies", anime.map((it) => B.posterCard(it)), { seeAll: "#/grid/movies?anime=only&title=Anime%20movies" }),
    kids ? "" : B.row("Stand-up", stand.map((it) => B.posterCard(it))),
    `<section class="rowx"><h3>Browse by</h3><div class="tiles2">
      <a class="tile2" href="#/browse/movies/genres">Genres</a><a class="tile2" href="#/grid/movies?type=BoxSet&title=Collections">Collections</a>
      <a class="tile2" href="#/browse/movies/studios">Studios</a><a class="tile2" href="#/grid/movies?title=All%20movies">All movies</a></div></section>`,
    B.pickerRow ? B.pickerRow("movies") : "",
  ].join("");
  B.wireDescribe(resume[0] || mine[0] || fresh[0]);
  const f = $("#rows .poster"); if (f) f.focus({ preventScroll: true });
});

// ---------- Shows door ----------
B.route(/^\/shows$/, async (m, nav) => {
  B.page().innerHTML = B.top("Shows", DOORS.shows.color) + doorTop() + `<div class="rows" id="rows">${B.spinner()}</div>`;
  B.wireTop();
  const u = B.uid(), F = B.FIELDS;
  const libs = await B.libs("shows");
  const animeLibs = libs.filter(B.isAnimeLib);
  const [next, resume, mine, newShows, latestEps, top] = await Promise.all([
    q(`/Shows/NextUp?userId=${u}&Limit=40&Fields=${F}&enableResumable=true&enableRewatching=false`),
    q(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Episode&Limit=20&Fields=${F}`).catch(() => []),
    q(`/Items?userId=${u}&IncludeItemTypes=Series&Recursive=true&Filters=IsFavorite&SortBy=DateCreated&SortOrder=Descending&Limit=30&Fields=${F}`),
    q(`/Items?userId=${u}&IncludeItemTypes=Series&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=30&Fields=${F}`),
    q(`/Items?userId=${u}&IncludeItemTypes=Episode&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=80&Fields=DateCreated,SeriesId,UserData&Filters=IsUnplayed`),
    B.trending("Series").catch(() => []),
  ]);
  if (!B.alive(nav)) return;
  // Keep watching: halfway-through episodes first, then the next episode of shows you're watching
  const seen = new Set(), kw = [];
  [...resume, ...next].forEach((ep) => { if (!seen.has(ep.SeriesId)) { seen.add(ep.SeriesId); kw.push(ep); } });
  const newCount = {};
  latestEps.filter((e) => B.isNew(e) && seen.has(e.SeriesId)).forEach((e) => { newCount[e.SeriesId] = (newCount[e.SeriesId] || 0) + 1; });
  // Shows with new episodes jump to the front
  kw.sort((a, b) => (newCount[b.SeriesId] ? 1 : 0) - (newCount[a.SeriesId] ? 1 : 0));
  const seriesIds = kw.map((e) => e.SeriesId);
  const series = seriesIds.length ? await q(`/Items?userId=${u}&Ids=${seriesIds.join(",")}&Fields=${F}`) : [];
  const sById = {}; series.forEach((s) => { sById[s.Id] = s; });
  const newEpShows = Object.keys(newCount).map((id) => sById[id]).filter(Boolean);
  const recentSeries = kw[0] ? sById[kw[0].SeriesId] : null;
  const because = recentSeries ? await q(`/Items/${recentSeries.Id}/Similar?userId=${u}&Limit=20&Fields=${F}`).catch(() => []) : [];
  const anime = [];
  for (const l of animeLibs) anime.push(...(await q(`/Items?userId=${u}&ParentId=${l.Id}&IncludeItemTypes=Series&Recursive=true&SortBy=DateLastContentAdded&SortOrder=Descending&Limit=24&Fields=${F}`)));
  if (!B.alive(nav)) return;
  const kwCard = (ep) => {
    const s = sById[ep.SeriesId];
    B.items[ep.Id] = ep;
    const card = B.posterCard(s || ep, { href: `#/show/${ep.SeriesId}?ep=${ep.Id}`, sub: `S${ep.ParentIndexNumber} E${ep.IndexNumber}`, noTick: true, newCount: newCount[ep.SeriesId] });
    return card.replace(`data-id="${esc((s || ep).Id)}"`, `data-id="${esc(ep.Id)}"`);
  };
  $("#rows").innerHTML = [
    B.row("Keep watching", kw.map(kwCard)),
    B.row("My List", mine.map((it) => B.posterCard(it)), { seeAll: "#/grid/shows?mine=1&title=My%20List" }),
    B.row("New episodes", newEpShows.map((s) => B.posterCard(s, { newCount: newCount[s.Id] }))),
    B.row("New shows", newShows.map((it) => B.posterCard(it, { markNew: true })), { seeAll: "#/grid/shows?sort=newshow&title=New%20shows" }),
    B.me.kids ? "" : B.row("Top 10 shows", B.top10(top)),
    recentSeries ? B.row(`Because you watched ${recentSeries.Name}`, because.map((it) => B.posterCard(it))) : "",
    B.row("Anime", anime.map((it) => B.posterCard(it)), { seeAll: "#/grid/shows?anime=only&title=Anime" }),
    `<section class="rowx"><h3>Browse by</h3><div class="tiles2">
      <a class="tile2" href="#/browse/shows/genres">Genres</a><a class="tile2" href="#/browse/shows/studios">Networks</a><a class="tile2" href="#/grid/shows?title=All%20shows">All shows</a></div></section>`,
    B.pickerRow ? B.pickerRow("shows") : "",
  ].join("");
  B.wireDescribe(kw[0] || mine[0] || newShows[0]);
  const f = $("#rows .poster"); if (f) f.focus({ preventScroll: true });
});

// ---------- Genres / Studios / Networks tiles ----------
B.route(/^\/browse\/(movies|shows)\/(genres|studios)$/, async (m, nav) => {
  const door = DOORS[m[1]], type = m[1] === "movies" ? "Movie" : "Series";
  const label = m[2] === "genres" ? "Genres" : m[1] === "shows" ? "Networks" : "Studios";
  B.page().innerHTML = B.top(door.name, door.color) + `<div class="wrap"><div class="h1">${label}</div><div id="t">${B.spinner()}</div></div>`;
  B.wireTop();
  const r = await B.get(`/${m[2] === "genres" ? "Genres" : "Studios"}?userId=${B.uid()}&IncludeItemTypes=${type}&Recursive=true&SortBy=SortName`);
  if (!B.alive(nav)) return;
  const key = m[2] === "genres" ? "genre" : "studio";
  $("#t").innerHTML = `<div class="grid" style="grid-template-columns:repeat(auto-fill,minmax(200px,1fr))">${(r.Items || []).map((g) => `<a class="tile2" style="width:auto" href="#/grid/${m[1]}?${key}=${enc(g.Name)}&title=${enc(g.Name)}">${esc(g.Name)}</a>`).join("") || '<div class="empty">Nothing here yet.</div>'}</div>`;
});

// ---------- the All movies / All shows grid ----------
const SORTS = {
  movies: [["added", "Newest added"], ["az", "A to Z"], ["year", "Release year"], ["score", "Score"], ["length", "Length"]],
  shows: [["episode", "Newest episode"], ["newshow", "Newest show"], ["az", "A to Z"], ["year", "Year"], ["score", "Score"]],
};
B.route(/^\/grid\/(movies|shows)/, async (m, nav) => {
  const doorKey = m[1], door = DOORS[doorKey];
  const type = B.q("type") || (doorKey === "movies" ? "Movie" : "Series");
  const title = B.q("title") || (doorKey === "movies" ? "All movies" : "All shows");
  const saved = B.webSettings()["grid-" + doorKey] || {};
  const st = {
    sort: B.q("sort") || (B.q("title") ? (doorKey === "movies" ? "added" : "episode") : saved.sort || (doorKey === "movies" ? "added" : "episode")),
    genre: B.q("genre") || "", studio: B.q("studio") || "", anime: B.q("anime") || (B.q("title") ? "all" : saved.anime || "all"),
    watched: "all", text: "", mine: B.q("mine") === "1",
  };
  if (B.q("q")) st.text = B.q("q");
  B.page().innerHTML = B.top(door.name, door.color) + `<div class="wrap" style="padding-bottom:0"><div class="h1">${esc(title)}</div></div>
    <div class="gridbar" style="--accent:${door.color}">
      <button class="chip on" id="gSort"></button>
      ${type === "BoxSet" ? "" : `<button class="chip ${st.genre ? "on" : ""}" id="gGenre">${esc(st.genre || "Genre")}</button>
      <button class="chip ${st.anime !== "all" ? "on" : ""}" id="gAnime"></button>
      ${doorKey === "shows" ? `<button class="chip" id="gWatched"></button>` : ""}`}
      <input class="search-in" id="gText" placeholder="Search ${esc(door.name.toLowerCase())}" value="${esc(st.text)}">
    </div>
    <div class="slim" id="slim">&nbsp;</div>
    <div class="grid-wrap"><div class="grid-body" id="gBody">${B.spinner()}</div><div class="letters hidden" id="letters"></div></div>`;
  B.wireTop();
  const labels = { all: "Anime: All", only: "Anime only", hide: "Hide anime" };
  const wlabels = { all: "All shows", notstarted: "Not started", watching: "Watching", finished: "Finished" };
  const paintChips = () => {
    $("#gSort").textContent = "Sort: " + SORTS[doorKey].find((s) => s[0] === st.sort)[1];
    if ($("#gAnime")) { $("#gAnime").textContent = labels[st.anime]; $("#gAnime").classList.toggle("on", st.anime !== "all"); }
    if ($("#gWatched")) { $("#gWatched").textContent = wlabels[st.watched]; $("#gWatched").classList.toggle("on", st.watched !== "all"); }
    if ($("#gGenre")) { $("#gGenre").textContent = st.genre || "Genre"; $("#gGenre").classList.toggle("on", !!st.genre); }
  };
  if (!SORTS[doorKey].some((s) => s[0] === st.sort)) st.sort = SORTS[doorKey][0][0];
  paintChips();
  const remember = () => { if (!B.q("title")) B.setWebSetting("grid-" + doorKey, { sort: st.sort, anime: st.anime }); };

  // Everything in the door, once, with which items are anime
  const all = await B.cached(`grid-${doorKey}-${type}-${st.mine}-${st.studio}`, 300000, async () => {
    const libs = await B.libs(doorKey);
    const F = "Genres,ProductionYear,DateCreated,DateLastContentAdded,CommunityRating,CriticRating,RunTimeTicks,OfficialRating,Overview,ChildCount,Status,EndDate,SortName";
    const extra = (st.mine ? "&Filters=IsFavorite" : "") + (st.studio ? `&Studios=${enc(st.studio)}` : "");
    const out = [];
    if (type === "BoxSet") {
      const r = await B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=BoxSet&Recursive=true&Fields=${F}${extra}`);
      return B.keep(r.Items || []);
    }
    for (const l of libs) {
      const r = await B.get(`/Items?userId=${B.uid()}&ParentId=${l.Id}&IncludeItemTypes=${type}&Recursive=true&Fields=${F}${extra}`);
      (r.Items || []).forEach((it) => { it._anime = B.isAnimeLib(l); out.push(it); });
    }
    return B.keep(out);
  });
  if (!B.alive(nav)) return;
  const genres = [...new Set(all.flatMap((it) => it.Genres || []))].sort();
  const now = Date.now();
  const sorters = {
    added: (a, b) => new Date(b.DateCreated) - new Date(a.DateCreated),
    newshow: (a, b) => new Date(b.DateCreated) - new Date(a.DateCreated),
    episode: (a, b) => new Date(b.DateLastContentAdded || b.DateCreated) - new Date(a.DateLastContentAdded || a.DateCreated),
    az: (a, b) => (a.SortName || a.Name).localeCompare(b.SortName || b.Name),
    year: (a, b) => (b.ProductionYear || 0) - (a.ProductionYear || 0),
    score: (a, b) => (b.CommunityRating || 0) - (a.CommunityRating || 0),
    length: (a, b) => (b.RunTimeTicks || 0) - (a.RunTimeTicks || 0),
  };
  const heading = (it) => {
    if (st.sort === "az") { const c = (it.SortName || it.Name || "#").charAt(0).toUpperCase(); return /[A-Z]/.test(c) ? c : "#"; }
    if (st.sort === "year") return it.ProductionYear ? String(it.ProductionYear) : "Year unknown";
    if (st.sort === "added" || st.sort === "newshow" || st.sort === "episode") {
      const d = new Date(st.sort === "episode" ? it.DateLastContentAdded || it.DateCreated : it.DateCreated);
      const days = (now - d) / 86400000;
      if (days < 7) return "This week";
      if (days < 31) return "Earlier this month";
      return d.toLocaleDateString([], { month: "long", year: "numeric" });
    }
    if (st.sort === "score") return it.CommunityRating ? `${Math.floor(it.CommunityRating)} and up` : "No score";
    return "";
  };
  let shown = [], drawn = 0;
  const draw = () => {
    const text = st.text.trim().toLowerCase();
    shown = all.filter((it) =>
      (!st.genre || (it.Genres || []).includes(st.genre)) &&
      (st.anime === "all" || (st.anime === "only" ? it._anime : !it._anime)) &&
      (!text || (it.Name || "").toLowerCase().includes(text)) &&
      (st.watched === "all" || (() => {
        const ud = it.UserData || {};
        if (st.watched === "finished") return ud.Played;
        if (st.watched === "notstarted") return !ud.Played && (ud.UnplayedItemCount == null || ud.UnplayedItemCount === it.RecursiveItemCount || !ud.PlayedPercentage) && !ud.LastPlayedDate;
        return !ud.Played && !!ud.LastPlayedDate;
      })())
    ).sort(sorters[st.sort]);
    drawn = 0;
    $("#gBody").innerHTML = shown.length ? "" : `<div class="empty">Nothing matches. Try another genre, or clear the search box.</div>`;
    more();
    const L = $("#letters");
    L.classList.toggle("hidden", st.sort !== "az");
    if (st.sort === "az") L.innerHTML = [..."#ABCDEFGHIJKLMNOPQRSTUVWXYZ"].map((c) => `<button data-l="${c}">${c}</button>`).join("");
  };
  const more = () => {
    const body = $("#gBody"); if (!body) return;
    const next = shown.slice(drawn, drawn + 120);
    if (!next.length) return;
    let html = "", last = drawn ? heading(shown[drawn - 1]) : null, open = false;
    next.forEach((it) => {
      const h = heading(it);
      if (h !== last) { if (open) html += "</div>"; html += `<div class="grid-head" data-h="${esc(h)}">${esc(h)}</div><div class="grid">`; open = true; last = h; }
      else if (!open) { html += `<div class="grid">`; open = true; }
      html += B.posterCard(it, { markNew: true });
    });
    if (open) html += "</div>";
    body.insertAdjacentHTML("beforeend", html);
    drawn += next.length;
  };
  const onScroll = () => { if (window.innerHeight + window.scrollY > document.body.scrollHeight - 900) more(); };
  window.addEventListener("scroll", onScroll);
  B.onLeave(() => window.removeEventListener("scroll", onScroll));
  // Slim line: what the poster you point at is
  // (#page stays the same element on every page, so take this away again when you leave)
  const onPoint = (e) => {
    const c = e.target.closest(".poster[data-id]"); if (!c || !$("#slim")) return;
    const it = B.items[c.dataset.id]; if (!it) return;
    const bits = [it.ProductionYear, it.RunTimeTicks && it.Type === "Movie" ? B.len(B.minutes(it.RunTimeTicks)) : it.ChildCount ? `${it.ChildCount} seasons` : "", it.Status === "Ended" ? "Ended" : it.Status === "Continuing" ? "Airing" : "", (it.Genres || []).slice(0, 2).join(", "), it.CommunityRating ? `${it.CommunityRating.toFixed(1)} IMDb` : ""].filter(Boolean);
    $("#slim").innerHTML = `<b>${esc(it.Name)}</b> · ${esc(bits.join(" · "))}`;
  };
  B.page().addEventListener("mouseover", onPoint);
  B.onLeave(() => B.page().removeEventListener("mouseover", onPoint));
  $("#letters").onclick = (e) => {
    const b = e.target.closest("[data-l]"); if (!b) return;
    while (!$(`.grid-head[data-h="${b.dataset.l}"]`) && drawn < shown.length) more();
    const h = $(`.grid-head[data-h="${b.dataset.l}"]`);
    if (h) window.scrollTo({ top: h.getBoundingClientRect().top + window.scrollY - 150, behavior: "smooth" });
  };
  $("#gSort").onclick = (e) => B.dropdown(e.currentTarget, SORTS[doorKey].map(([v, l]) => ({ value: v, label: l })), st.sort, (v) => { st.sort = v; remember(); paintChips(); draw(); });
  if ($("#gGenre")) $("#gGenre").onclick = (e) => B.dropdown(e.currentTarget, [{ value: "", label: "Every genre" }, ...genres.map((g) => ({ value: g, label: g }))], st.genre, (v) => { st.genre = v; paintChips(); draw(); });
  if ($("#gAnime")) $("#gAnime").onclick = () => { st.anime = { all: "only", only: "hide", hide: "all" }[st.anime]; remember(); paintChips(); draw(); };
  if ($("#gWatched")) $("#gWatched").onclick = () => { st.watched = { all: "notstarted", notstarted: "watching", watching: "finished", finished: "all" }[st.watched]; paintChips(); draw(); };
  let tt;
  $("#gText").oninput = (e) => { clearTimeout(tt); tt = setTimeout(() => { st.text = e.target.value; draw(); }, 200); };
  draw();
});

// ---------- buttons shared by the movie and show pages ----------
B.toggleList = async (it, btn) => {
  const on = !(it.UserData && it.UserData.IsFavorite);
  await B.jf(on ? "POST" : "DELETE", `/UserFavoriteItems/${it.Id}?userId=${B.uid()}`).catch(() => B.jf(on ? "POST" : "DELETE", `/Users/${B.uid()}/FavoriteItems/${it.Id}`));
  it.UserData = Object.assign({}, it.UserData, { IsFavorite: on });
  if (btn) btn.innerHTML = `${I(on ? "check" : "plus")} My List`;
  B.toast(on ? "Added to My List." : "Taken off My List.");
};
B.rate = (it) => {
  const ud = it.UserData || {};
  const cur = ud.Likes === false ? "no" : ud.Likes === true ? (ud.Rating >= 9 ? "love" : "like") : "";
  const p = B.side(`<h3>Rate ${esc(it.Name)}</h3><p class="dim">Benflix uses this to pick things for you.</p>
    <button class="opt ${cur === "no" ? "on" : ""}" data-r="no">Not for me</button><button class="opt ${cur === "like" ? "on" : ""}" data-r="like">I like it</button><button class="opt ${cur === "love" ? "on" : ""}" data-r="love">Love it</button>
    ${cur ? `<button class="opt" data-r="clear">Take my rating off</button>` : ""}`);
  p.onclick = (e) => {
    const b = e.target.closest("[data-r]"); if (!b) return;
    B.closeAll();
    const r = b.dataset.r;
    B.busy(null, async () => {
      if (r === "clear") await B.jf("DELETE", `/UserItems/${it.Id}/Rating?userId=${B.uid()}`).catch(() => B.jf("DELETE", `/Users/${B.uid()}/Items/${it.Id}/Rating`));
      else await B.jf("POST", `/UserItems/${it.Id}/Rating?userId=${B.uid()}&likes=${r !== "no"}`).catch(() => B.jf("POST", `/Users/${B.uid()}/Items/${it.Id}/Rating?likes=${r !== "no"}`));
      it.UserData = Object.assign({}, it.UserData, { Likes: r === "clear" ? null : r !== "no" });
      if (r === "love" || r === "like") await B.savePrefs((c) => { const map = loadJsonStr(c["benflix.loved"]); if (r === "love") map[it.Id] = 1; else delete map[it.Id]; c["benflix.loved"] = Object.keys(map).length ? JSON.stringify(map) : null; }).catch(() => {});
      return r === "clear" ? "Rating taken off." : "Thanks, saved.";
    });
  };
};
function loadJsonStr(s) { try { return JSON.parse(s) || {}; } catch (e) { return {}; } }
B.markWatched = async (it, played) => {
  await B.jf(played ? "POST" : "DELETE", `/UserPlayedItems/${it.Id}?userId=${B.uid()}`).catch(() => B.jf(played ? "POST" : "DELETE", `/Users/${B.uid()}/PlayedItems/${it.Id}`));
  it.UserData = Object.assign({}, it.UserData, { Played: played });
};
B.trailer = async (it) => {
  try {
    const local = it.LocalTrailerCount ? await B.get(`/Items/${it.Id}/LocalTrailers?userId=${B.uid()}`) : [];
    if (local && local.length) return B.go(`#/play/${local[0].Id}?trailer=1`);
  } catch (e) {}
  const yt = (it.RemoteTrailers || []).map((t) => t.Url).find((u) => /youtu/.test(u || ""));
  if (!yt) return B.toast("No trailer for this one yet.");
  const id = (yt.match(/(?:v=|youtu\.be\/|embed\/)([\w-]{11})/) || [])[1];
  B.closeAll(); B.shade();
  const mdl = document.createElement("div");
  mdl.className = "modal"; mdl.style.width = "min(1100px, calc(100vw - 32px))"; mdl.style.padding = "0"; mdl.style.overflow = "hidden";
  mdl.innerHTML = id ? `<iframe style="display:block;width:100%;aspect-ratio:16/9;border:0" src="https://www.youtube-nocookie.com/embed/${id}?autoplay=1" allow="autoplay; fullscreen" allowfullscreen></iframe>` : `<p style="padding:20px"><a href="${esc(yt)}" target="_blank" rel="noopener">Open the trailer</a></p>`;
  document.body.appendChild(mdl);
};
B.peopleRow = (it) => {
  const cast = (it.People || []).filter((p) => p.Type === "Actor").slice(0, 20);
  if (!cast.length) return "";
  return `<section class="rowx people-row"><h3>Cast</h3><div class="strip">${cast.map((p) => `<a class="person-c" href="#/person/${p.Id}"><span class="pic" style="${p.PrimaryImageTag ? `background-image:url('${B.img(p.Id, "Primary", { h: 240, tag: p.PrimaryImageTag })}')` : ""}">${p.PrimaryImageTag ? "" : esc(B.initials(p.Name))}</span>${esc(p.Name)}<small>${esc(p.Role || "")}</small></a>`).join("")}</div></section>`;
};
B.facts = async (it) => {
  const f = [];
  if (it.Studios && it.Studios.length) f.push(["Studio", it.Studios.map((s) => s.Name).slice(0, 2).join(", ")]);
  if (it.PremiereDate) f.push(["Released", B.day(it.PremiereDate)]);
  if (it.Genres && it.Genres.length) f.push(["Genres", it.Genres.join(", ")]);
  if (it.ProductionLocations && it.ProductionLocations.length) f.push(["Country", it.ProductionLocations[0]]);
  const ms = (it.MediaSources || [])[0];
  if (ms && B.me.admin) {
    const v = (ms.MediaStreams || []).find((s) => s.Type === "Video");
    f.push(["File", (ms.Path || "").split("/").pop()]);
    if (ms.Size) f.push(["Size", (ms.Size / 1073741824).toFixed(1) + " GB"]);
    if (v) f.push(["Video", [v.Width && v.Height ? `${v.Width}×${v.Height}` : "", (v.Codec || "").toUpperCase(), v.VideoRangeType && v.VideoRangeType !== "SDR" ? v.VideoRangeType : ""].filter(Boolean).join(" · ")]);
  }
  if (!f.length) return "";
  return `<section class="rowx"><h3>Details</h3><div class="facts">${f.map(([k, v]) => `<div><b>${esc(k)}</b>${esc(v)}</div>`).join("")}</div></section>`;
};
B.audioLine = (it) => {
  const ms = (it.MediaSources || [])[0];
  const a = ms && (ms.MediaStreams || []).filter((s) => s.Type === "Audio");
  if (!a || !a.length) return "";
  return [...new Set(a.map((s) => s.Language ? (s.DisplayLanguage || s.Language) : ""))].filter(Boolean).slice(0, 4).join(", ") || (a[0].DisplayTitle || "");
};
const langName = (code) => { try { return new Intl.DisplayNames(["en"], { type: "language" }).of(code); } catch (e) { return code; } };
B.langName = langName;

// ---------- "Did you know" on movie and show pages ----------
// One fact about this title at a time from Tower (hand-checked first, then Wikipedia), fading to the next
// every 10 seconds, with small dots. Nothing shows when the title has no facts.
B.didYouKnow = async (it, color, nav) => {
  const box = $("#dyk");
  if (!box) return;
  const got = await B.call("GET", `/api/facts/${it.Id}`).catch(() => null);
  const facts = ((got && got.facts) || []).map((f) => f.text).filter(Boolean);
  if (!facts.length || !B.alive(nav)) return;
  let i = 0;
  const dots = Math.min(facts.length, 8);
  const draw = () => {
    box.innerHTML = `<b style="color:${color}">DID YOU KNOW</b><p class="fade-in">${esc(facts[i])}</p>` +
      (facts.length > 1 ? `<div class="dots">${Array.from({ length: dots }, (_, d) => `<i class="${d === i % dots ? "on" : ""}"></i>`).join("")}</div>` : "");
  };
  box.style.borderColor = color;
  box.hidden = false;
  draw();
  if (facts.length < 2) return;
  const timer = setInterval(() => {
    if (!B.alive(nav) || !document.body.contains(box)) return clearInterval(timer);
    i = (i + 1) % facts.length;
    draw();
  }, 10000);
};

// ---------- movie page ----------
B.route(/^\/movie\/([\w-]+)/, async (m, nav) => {
  const it = await B.get(`/Users/${B.uid()}/Items/${m[1]}?Fields=${B.FIELDS},People,Studios,MediaSources,ProductionLocations,RemoteTrailers,LocalTrailerCount,Chapters`).catch(() => B.get(`/Items/${m[1]}?userId=${B.uid()}&Fields=${B.FIELDS},People,MediaSources,RemoteTrailers`));
  if (!B.alive(nav)) return;
  B.items[it.Id] = it;
  const ud = it.UserData || {};
  const resume = ud.PlaybackPositionTicks > 0;
  const len = B.minutes(it.RunTimeTicks);
  const ends = new Date(Date.now() + (len - B.minutes(ud.PlaybackPositionTicks || 0)) * 60000);
  const directors = (it.People || []).filter((p) => p.Type === "Director").map((p) => p.Name);
  const stars = (it.People || []).filter((p) => p.Type === "Actor").slice(0, 3).map((p) => p.Name);
  B.page().innerHTML = B.top("") + `<div class="details">
    <div class="hero" style="background-image:url('${B.backdrop(it, 1920)}')"><div class="hero-in">
      <div class="main">
        <h1 class="t">${esc(it.Name)}</h1>
        <div class="meta">${[it.ProductionYear, len ? B.len(len) : "", it.OfficialRating ? `<span class="rate">${esc(it.OfficialRating)}</span>` : "", len ? `Ends at ${B.clock(ends)}` : ""].filter(Boolean).map((x) => `<span>${String(x).startsWith("<") ? x : esc(x)}</span>`).join("")}</div>
        ${B.scores(it)}
        <div class="ov">${esc(it.Overview || "")}</div>
        ${resume ? `<div class="kwbar"><span class="line"><i style="width:${ud.PlayedPercentage || 0}%"></i></span>${B.len(B.minutes(it.RunTimeTicks - ud.PlaybackPositionTicks))} left</div>` : ""}
        <div class="btns">
          <a class="btn white big" href="#/play/${it.Id}${resume ? "?resume=1" : ""}">${I("play")} ${resume ? "Resume" : "Play"}</a>
          ${it.LocalTrailerCount || (it.RemoteTrailers || []).length ? `<button class="btn big" id="bTrailer">${I("trailer")} Trailer</button>` : ""}
          <button class="btn big" id="bList">${I(ud.IsFavorite ? "check" : "plus")} My List</button>
          ${B.me.kids ? "" : `<button class="btn big" id="bRate">${I("thumb")} Rate</button>`}
          <button class="btn big" id="bMore">${I("more")} More</button>
        </div>
      </div>
      <div class="side-col">
        ${directors.length ? `<div><b>Director</b><span>${esc(directors.join(", "))}</span></div>` : ""}
        ${stars.length ? `<div><b>Starring</b><span>${esc(stars.join(", "))}</span></div>` : ""}
        ${B.audioLine(it) ? `<div><b>Audio</b><span>${esc(B.audioLine(it))}</span></div>` : ""}
        ${(it.Genres || []).length ? `<div><b>Genres</b><span>${esc(it.Genres.slice(0, 3).join(", "))}</span></div>` : ""}
        <div class="dyk" id="dyk" hidden></div>
      </div>
    </div></div>
    <div class="below" id="below">${B.spinner()}</div>
  </div>`;
  B.wireTop();
  $(".btns .btn").focus({ preventScroll: true });
  $("#bList").onclick = (e) => B.toggleList(it, e.currentTarget);
  if ($("#bRate")) $("#bRate").onclick = () => B.rate(it);
  if ($("#bTrailer")) $("#bTrailer").onclick = () => B.trailer(it);
  $("#bMore").onclick = () => movieMore(it);
  B.didYouKnow(it, DOORS.movies.color, nav);
  const similar = await q(`/Items/${it.Id}/Similar?userId=${B.uid()}&Limit=20&Fields=${B.FIELDS}`).catch(() => []);
  if (!B.alive(nav)) return;
  $("#below").innerHTML = B.row("More like this", similar.map((x) => B.posterCard(x))) + B.peopleRow(it) + (await B.facts(it));
});
const movieMore = (it) => {
  const ud = it.UserData || {};
  const ids = it.ProviderIds || {};
  const p = B.side(`<h3>${esc(it.Name)}</h3><p class="dim">More choices</p>
    <button class="opt" data-a="start">${I("restart")} Start over</button>
    <button class="opt" data-a="tracks">${I("sub")} Audio, subtitles and version</button>
    <button class="opt" data-a="watched">${I("check")} ${ud.Played ? "Mark as not watched" : "Mark as watched"}</button>
    <button class="opt" data-a="report">${I("flag")} Report a problem</button>
    ${B.me.admin ? `<button class="opt" data-a="fix">${I("swap")} Fix poster and details</button>` : ""}
    ${ids.Imdb ? `<a class="opt" href="https://www.imdb.com/title/${esc(ids.Imdb)}" target="_blank" rel="noopener">${I("info")} Open on IMDb</a>` : ""}`);
  p.onclick = async (e) => {
    const b = e.target.closest("[data-a]"); if (!b) return;
    B.closeAll();
    if (b.dataset.a === "start") B.go(`#/play/${it.Id}`);
    if (b.dataset.a === "tracks") B.trackPicker(it, (a, s) => B.go(`#/play/${it.Id}?resume=1&audio=${a}&sub=${s}`));
    if (b.dataset.a === "watched") B.busy(null, async () => { await B.markWatched(it, !ud.Played); B.render(); return ud.Played ? "Marked as not watched." : "Marked as watched."; });
    if (b.dataset.a === "report") B.report({ id: it.Id, title: it.Name });
    if (b.dataset.a === "fix") B.fixItem(it);
  };
};
// Audio + subtitles panel before playing
B.trackPicker = (it, done) => {
  const ms = (it.MediaSources || [])[0] || {};
  const streams = ms.MediaStreams || [];
  const audio = streams.filter((s) => s.Type === "Audio"), subs = streams.filter((s) => s.Type === "Subtitle");
  let a = ms.DefaultAudioStreamIndex ?? (audio[0] && audio[0].Index), s = ms.DefaultSubtitleStreamIndex ?? -1;
  const draw = () => `<h3>Audio and subtitles</h3>
    <h4 class="dim" style="margin:16px 0 8px">Audio</h4>${audio.map((x) => `<button class="opt ${x.Index === a ? "on" : ""}" data-a="${x.Index}">${esc(x.DisplayTitle || x.Language || "Track " + x.Index)}</button>`).join("") || '<p class="dim">Only one sound track.</p>'}
    <h4 class="dim" style="margin:16px 0 8px">Subtitles</h4><button class="opt ${s === -1 ? "on" : ""}" data-s="-1">Off</button>${subs.map((x) => `<button class="opt ${x.Index === s ? "on" : ""}" data-s="${x.Index}">${esc(x.DisplayTitle || x.Language || "Subtitle " + x.Index)}</button>`).join("")}
    <div class="acts" style="margin-top:18px"><button class="btn white" data-go>${I("play")} Play</button></div>`;
  const p = B.side(draw());
  p.onclick = (e) => {
    const x = e.target.closest("[data-a],[data-s],[data-go]"); if (!x) return;
    if (x.dataset.a != null) a = +x.dataset.a;
    if (x.dataset.s != null) s = +x.dataset.s;
    if (x.hasAttribute("data-go")) { B.closeAll(); return done(a, s); }
    p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button>` + draw();
  };
};

// ---------- show page: seasons + episodes ----------
B.route(/^\/show\/([\w-]+)/, async (m, nav) => {
  const u = B.uid();
  const [it, seasons, nextUp] = await Promise.all([
    B.get(`/Users/${u}/Items/${m[1]}?Fields=${B.FIELDS},People,Studios,RemoteTrailers,LocalTrailerCount`),
    B.get(`/Shows/${m[1]}/Seasons?userId=${u}&Fields=ChildCount`).then((r) => r.Items || []),
    B.get(`/Shows/NextUp?userId=${u}&SeriesId=${m[1]}&Limit=1&enableResumable=true&Fields=${B.FIELDS}`).then((r) => (r.Items || [])[0]).catch(() => null),
  ]);
  if (!B.alive(nav)) return;
  B.items[it.Id] = it;
  const ud = it.UserData || {};
  const wantEp = B.q("ep"), wantSeason = B.q("season");
  let target = wantEp ? await B.get(`/Users/${u}/Items/${wantEp}?Fields=${B.FIELDS}`).catch(() => null) : null;
  if (!target) target = nextUp;
  const seasonId = wantSeason || (target && target.SeasonId) || (seasons.find((s) => s.IndexNumber > 0) || seasons[0] || {}).Id;
  const resumeLabel = target ? `${target.UserData && target.UserData.PlaybackPositionTicks ? "Resume" : ud.PlayedPercentage || ud.UnplayedItemCount < it.RecursiveItemCount ? "Play" : "Play"} S${target.ParentIndexNumber} E${target.IndexNumber}` : "Play";
  const bits = [it.ProductionYear ? (it.Status === "Ended" && it.EndDate ? `${it.ProductionYear}–${new Date(it.EndDate).getFullYear()}` : it.ProductionYear) : "", seasons.filter((s) => s.IndexNumber > 0).length ? `${seasons.filter((s) => s.IndexNumber > 0).length} seasons` : "", it.OfficialRating ? `<span class="rate">${esc(it.OfficialRating)}</span>` : "", it.Status === "Ended" ? "Ended" : it.AirDays && it.AirDays.length ? `New episodes on ${it.AirDays[0]}s` : ""].filter(Boolean);
  const stars = (it.People || []).filter((p) => p.Type === "Actor").slice(0, 3).map((p) => p.Name);
  B.page().innerHTML = B.top("") + `<div class="details">
    <div class="hero" style="background-image:url('${B.backdrop(it, 1920)}');min-height:82vh"><div class="hero-in">
      <div class="main">
        <h1 class="t">${esc(it.Name)}</h1>
        ${it.OriginalTitle && it.OriginalTitle !== it.Name ? `<div class="t2">${esc(it.OriginalTitle)}</div>` : ""}
        <div class="meta">${bits.map((x) => `<span>${String(x).startsWith("<") ? x : esc(x)}</span>`).join("")}</div>
        ${B.scores(it)}
        <div class="ov">${esc(it.Overview || "")}</div>
        <div class="btns">
          ${target ? `<a class="btn white big" href="#/play/${target.Id}?resume=1">${I("play")} ${esc(resumeLabel)}</a>` : ""}
          ${it.LocalTrailerCount || (it.RemoteTrailers || []).length ? `<button class="btn big" id="bTrailer">${I("trailer")} Trailer</button>` : ""}
          <button class="btn big" id="bList">${I(ud.IsFavorite ? "check" : "plus")} My List</button>
          ${B.me.kids ? "" : `<button class="btn big" id="bRate">${I("thumb")} Rate</button>`}
          <button class="btn big" id="bMore">${I("more")} More</button>
        </div>
      </div>
      <div class="side-col">
        ${it.Studios && it.Studios.length ? `<div><b>Network</b><span>${esc(it.Studios[0].Name)}</span></div>` : ""}
        ${stars.length ? `<div><b>Starring</b><span>${esc(stars.join(", "))}</span></div>` : ""}
        ${(it.Genres || []).length ? `<div><b>Genres</b><span>${esc(it.Genres.slice(0, 3).join(", "))}</span></div>` : ""}
        <div class="dyk" id="dyk" hidden></div>
      </div>
    </div></div>
    <div class="below">
      <div class="seasons" id="seasons">${seasons.map((s) => `<button class="chip ${s.Id === seasonId ? "on" : ""}" style="--accent:${DOORS.shows.color}" data-season="${s.Id}">${esc(s.IndexNumber === 0 ? "Specials" : s.Name)}</button>`).join("")}</div>
      <div class="seasons" id="groups"></div>
      <div id="eps">${B.spinner()}</div>
      <div id="below2"></div>
    </div>
  </div>`;
  B.wireTop();
  $(".btns .btn").focus({ preventScroll: true });
  B.didYouKnow(it, DOORS.shows.color, nav);
  $("#bList").onclick = (e) => B.toggleList(it, e.currentTarget);
  if ($("#bRate")) $("#bRate").onclick = () => B.rate(it);
  if ($("#bTrailer")) $("#bTrailer").onclick = () => B.trailer(it);
  $("#bMore").onclick = () => {
    const p = B.side(`<h3>${esc(it.Name)}</h3><p class="dim">More choices</p>
      <button class="opt" data-a="all">${I("check")} ${ud.Played ? "Mark the whole show as not watched" : "Mark the whole show as watched"}</button>
      <button class="opt" data-a="report">${I("flag")} Report a problem</button>
      ${B.me.admin ? `<button class="opt" data-a="fix">${I("swap")} Fix poster and details</button>` : ""}`);
    p.onclick = (e) => {
      const b = e.target.closest("[data-a]"); if (!b) return;
      B.closeAll();
      if (b.dataset.a === "all") B.busy(null, async () => { await B.markWatched(it, !ud.Played); B.render(); return "Done."; });
      if (b.dataset.a === "report") B.report({ id: it.Id, title: it.Name });
      if (b.dataset.a === "fix") B.fixItem(it);
    };
  };
  let episodes = [], group = 0;
  const drawEps = () => {
    const now = Date.now();
    const list = episodes.length > 50 ? episodes.slice(group * 25, group * 25 + 25) : episodes;
    $("#groups").innerHTML = episodes.length > 50 ? Array.from({ length: Math.ceil(episodes.length / 25) }, (_, i) => `<button class="chip ${i === group ? "on" : ""}" style="--accent:${DOORS.shows.color}" data-group="${i}">${i * 25 + 1}–${Math.min(episodes.length, i * 25 + 25)}</button>`).join("") : "";
    $("#eps").innerHTML = B.row("Episodes", list.map((e) => {
      const soon = e.PremiereDate && new Date(e.PremiereDate) > now && e.LocationType === "Virtual";
      const special = e.ParentIndexNumber === 0;
      const sub = soon ? `Coming ${B.day(e.PremiereDate)}` : [special ? "Special" : `E${e.IndexNumber}`, e.RunTimeTicks ? B.len(B.minutes(e.RunTimeTicks)) : "", B.isNew(e) && !(e.UserData || {}).Played ? "New" : ""].filter(Boolean).join(" · ");
      return B.wideCard(e, { href: `#/play/${e.Id}?resume=1`, title: `${special ? "" : e.IndexNumber + ". "}${e.Name}`, sub, story: true, soon, tag: special ? "SPECIAL" : "" });
    }), { id: "epRow" });
    const t = target && $(`#epRow [data-id="${CSS.escape(target.Id)}"]`);
    if (t) { t.scrollIntoView({ inline: "start", block: "nearest" }); $("#epRow .strip").scrollLeft -= 48; }
  };
  const loadSeason = async (sid) => {
    $("#eps").innerHTML = B.spinner();
    episodes = await q(`/Shows/${it.Id}/Episodes?userId=${u}&SeasonId=${sid}&Fields=Overview,PremiereDate,RunTimeTicks,DateCreated,UserData&EnableImages=true`);
    group = 0;
    if (target) { const i = episodes.findIndex((e) => e.Id === target.Id); if (i >= 0 && episodes.length > 50) group = Math.floor(i / 25); }
    if (B.alive(nav)) drawEps();
  };
  $("#seasons").onclick = (e) => {
    const b = e.target.closest("[data-season]"); if (!b) return;
    $$("#seasons .chip").forEach((c) => c.classList.toggle("on", c === b));
    loadSeason(b.dataset.season);
  };
  $("#groups").onclick = (e) => { const b = e.target.closest("[data-group]"); if (!b) return; group = +b.dataset.group; drawEps(); };
  // Right-click an episode: Mark watched, Mark all before as watched, Play from start
  $("#eps").addEventListener("contextmenu", (e) => {
    const c = e.target.closest(".wide[data-id]"); if (!c) return;
    e.preventDefault();
    const ep = B.items[c.dataset.id], idx = episodes.findIndex((x) => x.Id === ep.Id), played = (ep.UserData || {}).Played;
    B.dropdown(c, [{ value: "w", label: played ? "Mark as not watched" : "Mark as watched" }, { value: "before", label: "Mark all before as watched" }, { value: "start", label: "Play from start" }], null, (v) => {
      if (v === "start") return B.go(`#/play/${ep.Id}`);
      B.busy(null, async () => {
        const list = v === "w" ? [ep] : episodes.slice(0, idx);
        for (const x of list) await B.markWatched(x, v === "w" ? !played : true);
        drawEps();
        return "Done.";
      });
    });
  });
  if (seasonId) await loadSeason(seasonId); else $("#eps").innerHTML = "";
  const similar = await q(`/Items/${it.Id}/Similar?userId=${u}&Limit=20&Fields=${B.FIELDS}`).catch(() => []);
  if (!B.alive(nav)) return;
  $("#below2").innerHTML = B.row("More like this", similar.map((x) => B.posterCard(x))) + B.peopleRow(it) + (await B.facts(it));
});

// ---------- collection page ----------
B.route(/^\/collection\/([\w-]+)/, async (m, nav) => {
  const u = B.uid();
  const [it, kids] = await Promise.all([B.get(`/Users/${u}/Items/${m[1]}?Fields=${B.FIELDS}`), q(`/Items?userId=${u}&ParentId=${m[1]}&SortBy=ProductionYear,SortName&Fields=${B.FIELDS}`)]);
  if (!B.alive(nav)) return;
  B.page().innerHTML = B.top("Movies", DOORS.movies.color) + `<div class="details"><div class="hero" style="background-image:url('${B.backdrop(it, 1920)}');min-height:60vh"><div class="hero-in"><div class="main">
    <h1 class="t">${esc(it.Name)}</h1><div class="meta"><span>${kids.length} movies</span></div><div class="ov">${esc(it.Overview || "")}</div>
    ${kids[0] ? `<div class="btns"><a class="btn white big" href="#/movie/${(kids.find((k) => !(k.UserData || {}).Played) || kids[0]).Id}">${I("play")} Start with ${esc((kids.find((k) => !(k.UserData || {}).Played) || kids[0]).Name)}</a></div>` : ""}
    </div></div></div><div class="below wrap"><div class="grid">${kids.map((k) => B.posterCard(k)).join("")}</div></div></div>`;
  B.wireTop();
});

// ---------- person page: photo, name and story on top, then their movies and shows ----------
B.route(/^\/person\/([\w-]+)/, async (m, nav) => {
  const u = B.uid();
  const [p, movies, shows] = await Promise.all([
    B.get(`/Users/${u}/Items/${m[1]}?Fields=Overview,PremiereDate,ProductionLocations`),
    q(`/Items?userId=${u}&PersonIds=${m[1]}&IncludeItemTypes=Movie&Recursive=true&SortBy=ProductionYear&SortOrder=Descending&Fields=${B.FIELDS}`),
    q(`/Items?userId=${u}&PersonIds=${m[1]}&IncludeItemTypes=Series&Recursive=true&SortBy=ProductionYear&SortOrder=Descending&Fields=${B.FIELDS}`),
  ]);
  if (!B.alive(nav)) return;
  B.page().innerHTML = B.top("") + `<div class="wrap" style="display:flex;gap:32px;align-items:flex-end;padding-top:20px;flex-wrap:wrap">
    <span class="pic" style="width:180px;height:180px;font-size:60px;background-image:url('${B.img(p.Id, "Primary", { h: 400 })}')"></span>
    <div style="flex:1;min-width:260px"><div class="h1">${esc(p.Name)}</div>${p.PremiereDate ? `<div class="dim">Born ${esc(B.day(p.PremiereDate))}${p.ProductionLocations && p.ProductionLocations[0] ? " · " + esc(p.ProductionLocations[0]) : ""}</div>` : ""}
    <p class="dim" style="max-width:820px;display:-webkit-box;-webkit-line-clamp:4;-webkit-box-orient:vertical;overflow:hidden">${esc(p.Overview || "")}</p></div></div>
    <div class="rows" style="padding-top:20px">${B.row("Movies", movies.map((x) => B.posterCard(x))) || ""}${B.row("Shows", shows.map((x) => B.posterCard(x))) || ""}
    ${!movies.length && !shows.length ? '<div class="wrap empty">Nothing with them on Benflix yet.</div>' : ""}
    ${B.me.kids ? "" : `<div class="wrap"><a class="btn" href="#/requests?q=${enc(p.Name)}">${I("plus")} Ask for more with ${esc(p.Name)}</a></div>`}</div>`;
  B.wireTop();
});
