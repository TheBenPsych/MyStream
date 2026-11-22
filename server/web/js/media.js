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
    kw = `<div class="kwbar">${I("play")}<b>Resume</b><span class="line"><i style="width:${Math.min(100, ud.PlayedPercentage || 0)}%"></i></span>${B.len(left)} left</div>`;
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
    // Like Wholphin (Ben 2026-11-18): another episode of the same show has the same picture, so it just stays.
    // A new picture: the old one fades away, a short wait, then the new one fades in.
    if (src === art.dataset.src) return;
    art.dataset.src = src || "";
    const hadPicture = art.classList.contains("on");
    art.classList.remove("on");
    if (src) {
      const img = new Image();
      const startedAt = Date.now();
      img.onload = () => {
        const wait = hadPicture ? Math.max(0, 500 - (Date.now() - startedAt)) : 0;
        setTimeout(() => { if (shown === it && art.dataset.src === src) { art.style.backgroundImage = `url('${src}')`; art.classList.add("on"); } }, wait);
      };
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
  // Ben 2026-11-18 (Wholphin style): the top area stays locked; only the rows scroll, under it
  const rows = top.parentElement && top.parentElement.querySelector(":scope > .rows");
  const fit = () => { if (rows) rows.style.height = Math.max(200, window.innerHeight - rows.getBoundingClientRect().top - (window.scrollY || 0)) + "px"; };
  if (rows) {
    document.body.classList.add("locked-top");
    fit();
    window.addEventListener("resize", fit);
    // (phones: the top area's height follows its words, so fit again when it changes)
    if (window.ResizeObserver) { const ro = new ResizeObserver(fit); ro.observe(top); B.onLeave(() => ro.disconnect()); }
    // The row you move to with the keyboard jumps up whole, name and all, right under the top area
    rows.addEventListener("focusin", (e) => { const r = e.target.closest(".rowx"); if (r) rows.scrollTo({ top: r.offsetTop - 4, behavior: "smooth" }); });
  }
  B.onLeave(() => {
    B.page().removeEventListener("mouseover", pointAt); B.page().removeEventListener("focusin", pointAt);
    window.removeEventListener("resize", fit); document.body.classList.remove("locked-top");
  });
  show(first);
};
const doorTop = () => `<section class="door-top" id="doorTop"><div class="art"></div><div class="info"></div></section>`;
B.doorTop = doorTop;

// ---------- top 10: what's trending (from Requests), on the server; otherwise the best rated lately ----------
B.trending = async (type, onlyAnime) => {
  const kind = type === "Movie" ? "movies" : "tv";
  let ids = [];
  try {
    const pages = await B.cached("trend-" + kind, 1800000, () => Promise.all([1, 2, 3].map((p) => B.get(`/JellyfinEnhanced/jellyseerr/discover/${kind === "movies" ? "movies" : "tv"}?page=${p}&sortBy=popularity.desc`).catch(() => null))));
    pages.forEach((p) => ((p && p.results) || []).forEach((r) => { const id = r.mediaInfo && (r.mediaInfo.jellyfinMediaId || r.mediaInfo.jellyfinMediaId4k); if (id && !ids.includes(id)) ids.push(id); }));
  } catch (e) {}
  const anime = await B.animeIds(type);
  const keep = (id) => (onlyAnime ? anime.has(id) : !anime.has(id));
  let items = [];
  if (ids.length) {
    const r = await B.get(`/Items?userId=${B.uid()}&Ids=${ids.slice(0, 40).join(",")}&Fields=${B.FIELDS}`);
    const by = {}; (r.Items || []).forEach((x) => { by[x.Id] = x; });
    items = ids.map((id) => by[id]).filter((x) => x && keep(x.Id));
  }
  // Fewer than ten trending titles are on the server: fill the rest with the best rated of this year and
  // last, then the best rated of any year, so the row always reaches 10
  const fill = async (years) => {
    const r = await B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=${type}&Recursive=true${years}&SortBy=CommunityRating&SortOrder=Descending&Limit=${onlyAnime ? 200 : 60}&Fields=${B.FIELDS}&ImageTypes=Primary`).catch(() => null);
    const seen = new Set(items.map((x) => x.Id));
    items = items.concat(((r && r.Items) || []).filter((x) => keep(x.Id) && !seen.has(x.Id)));
  };
  if (items.length < 10) {
    const y = new Date().getFullYear();
    // Top 10 anime: the best rated anime of any year (like the TV app)
    await fill(onlyAnime ? "" : `&Years=${y},${y - 1}`);
    if (items.length < 10 && !onlyAnime) await fill("");
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

// ---------- Glowfin (ui-7): Movies, Shows and Anime "For you" rows ----------
// Rows of one kind from some libraries
const fromLibs = async (libs, type, sort, extra) => {
  const u = B.uid(), F = B.FIELDS, out = [];
  const lists = await Promise.all(libs.map((l) => q(`/Items?userId=${u}&ParentId=${l.Id}&IncludeItemTypes=${type}&Recursive=true&SortBy=${sort}&SortOrder=Descending&Limit=20&ImageTypes=Primary&Fields=${F}${extra || ""}`).catch(() => [])));
  lists.forEach((l) => out.push(...l));
  return out;
};
const newestFirst = (key) => (a, b) => String(b[key] || "").localeCompare(String(a[key] || ""));
// "Because you watched": like the last one of this kind watched, kept to [keep]
const becauseRow = async (type, keep) => {
  const u = B.uid(), F = B.FIELDS;
  const played = await q(`/Items?userId=${u}&IncludeItemTypes=${type === "Series" ? "Episode" : "Movie"}&Recursive=true&Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=20&Fields=SeriesId,SeriesName`).catch(() => []);
  const seen = new Set();
  for (const p of played) {
    const id = type === "Series" ? p.SeriesId : p.Id, name = type === "Series" ? p.SeriesName : p.Name;
    if (!id || seen.has(id) || !keep(id)) continue;
    seen.add(id);
    if (seen.size > 4) break;
    const like = (await q(`/Items/${id}/Similar?userId=${u}&Limit=20&Fields=${F}`).catch(() => [])).filter((x) => x.Type === type && keep(x.Id));
    if (like.length >= 5) return { name, items: like };
  }
  return null;
};
// "Because you loved": like one this person pressed "Love it" on (liked and in My List); a different one each visit
const lovedRow = async (type, keep) => {
  const u = B.uid(), F = B.FIELDS;
  const favs = await q(`/Items?userId=${u}&IncludeItemTypes=${type}&Recursive=true&Filters=IsFavorite&SortBy=Random&Limit=40&EnableUserData=true`).catch(() => []);
  // "Love it" pressed in the browser is kept with the profile's settings; pressed in the app it's liked + My List
  const ids = Object.keys(loadJsonStr(((await B.prefs().catch(() => ({}))).CustomPrefs || {})["benflix.loved"])).slice(0, 40);
  const marked = ids.length ? await q(`/Items?userId=${u}&Ids=${ids.join(",")}&IncludeItemTypes=${type}`).catch(() => []) : [];
  const seenLoved = new Set();
  const loved = favs.filter((x) => x.UserData && x.UserData.Likes === true).concat(marked)
    .filter((x) => x.Type === type && keep(x.Id) && !seenLoved.has(x.Id) && seenLoved.add(x.Id))
    .sort(() => Math.random() - 0.5).slice(0, 4);
  for (const p of loved) {
    const like = (await q(`/Items/${p.Id}/Similar?userId=${u}&Limit=20&Fields=${F}`).catch(() => [])).filter((x) => x.Type === type && keep(x.Id));
    if (like.length >= 5) return { name: p.Name, items: like };
  }
  return null;
};
// Small tags for a title page: picture size, HDR, sound, English captions (from the file itself)
B.qualityChips = (it) => {
  const ms = (it.MediaSources || [])[0];
  const st = (ms && ms.MediaStreams) || it.MediaStreams || [];
  const v = st.find((x) => x.Type === "Video"), out = [];
  if (v) {
    const w = v.Width || 0, h = v.Height || 0;
    out.push(w >= 3800 || h >= 2000 ? "4K" : w >= 1900 || h >= 1000 ? "1080p" : w >= 1260 || h >= 700 ? "720p" : "SD");
    const r = String(v.VideoRangeType || "").toUpperCase();
    if (r.startsWith("DOVI")) out.push("Dolby Vision"); else if (r.startsWith("HDR") || r === "HLG") out.push("HDR");
  }
  const au = st.filter((x) => x.Type === "Audio");
  if (au.length) {
    const ch = Math.max(...au.map((x) => x.Channels || 0));
    if (au.some((x) => /atmos/i.test(`${x.DisplayTitle || ""} ${x.Profile || ""} ${x.Title || ""}`))) out.push("Atmos");
    else if (ch >= 8) out.push("7.1"); else if (ch >= 6) out.push("5.1"); else if (ch === 2) out.push("Stereo");
  }
  if (st.some((x) => x.Type === "Subtitle" && /^en/i.test(x.Language || ""))) out.push("CC");
  return out;
};
B.logoUrl = (it) => (it.ImageTags && it.ImageTags.Logo ? B.img(it.Id, "Logo", { tag: it.ImageTags.Logo }) + "&maxHeight=220&format=Png" : it.ParentLogoItemId && it.ParentLogoImageTag ? B.img(it.ParentLogoItemId, "Logo", { tag: it.ParentLogoImageTag }) + "&maxHeight=220&format=Png" : "");
// "Trending, not on Glowfin": what's trending in Requests that isn't here yet. Movies and Shows leave anime out;
// kind "anime" is anime shows and anime movies together (Japanese titles in TMDB's Animation genre)
B.trendingMissing = async (kind) => {
  try {
    const anime = kind === "anime";
    const kinds = anime ? ["tv", "movies"] : kind ? [kind] : ["movies", "tv"];
    const path = (k) => (anime ? `/discover/${k}/genre/16?page=1` : `/discover/${k}?page=1&sortBy=popularity.desc`);
    const lists = await Promise.all(kinds.map((k) => B.cached("trendmiss-" + (anime ? "anime-" : "") + k, 1800000, async () => {
      const pages = anime ? [path(k), path(k).replace("page=1", "page=2")] : [path(k)];
      const got = await Promise.all(pages.map((u) => B.get("/JellyfinEnhanced/jellyseerr" + u).catch(() => null)));
      return { results: got.flatMap((p) => (p && p.results) || []) };
    })));
    const per = lists.map((p, i) => ((p && p.results) || []).filter((r) => {
      if (r.mediaInfo && (r.mediaInfo.jellyfinMediaId || r.mediaInfo.status === 5)) return false;
      const isAnime = (r.genreIds || []).includes(16) && r.originalLanguage === "ja";
      return anime ? isAnime : !isAnime;
    }).map((r) => ({ r, kind: kinds[i] })));
    // Anime: two shows then one movie, so both show up near the front
    const out = [];
    if (anime) {
      const [a, b] = per;
      let i = 0, j = 0;
      while (i < a.length || j < b.length) { if (i < a.length) out.push(a[i++]); if (i < a.length) out.push(a[i++]); if (j < b.length) out.push(b[j++]); }
    } else per.forEach((l) => out.push(...l));
    const seen = new Set();
    return out.filter((x) => !seen.has(x.kind + x.r.id) && seen.add(x.kind + x.r.id)).slice(0, 20);
  } catch (e) { return []; }
};
// Request cards (the same as Requests): pressing one opens its Ask for it page
const missingCards = (list) => list.map(({ r, kind }) => B.askCard(Object.assign({ mediaType: kind === "movies" ? "movie" : "tv" }, r)));
const pageRows = (html) => { $("#rows").innerHTML = html.join(""); };

B.route(/^\/movies$/, async (m, nav) => {
  B.page().innerHTML = B.top("Movies", DOORS.movies.color) + doorTop() + `<div class="rows" id="rows">${B.spinner("rows")}</div>`;
  B.wireTop();
  const u = B.uid(), F = B.FIELDS;
  const libs = await B.libs("movies");
  const main = libs.filter((l) => !B.isAnimeLib(l) && !B.isStandLib(l)), standLibs = libs.filter(B.isStandLib);
  const anime = await B.animeIds("Movie");
  const notAnime = (x) => !anime.has(x.Id);
  const fewer = B.me.kids;
  const [resume, fresh, top, because, released, best, stand, missing, loved] = await Promise.all([
    q(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Movie&Limit=20&Fields=${F}`).catch(() => []),
    fromLibs(main.concat(standLibs), "Movie", "DateCreated").then((l) => l.sort(newestFirst("DateCreated"))),
    fewer ? [] : B.trending("Movie").catch(() => []),
    fewer ? null : becauseRow("Movie", (id) => !anime.has(id)).catch(() => null),
    fewer ? [] : fromLibs(main.concat(standLibs), "Movie", "PremiereDate").then((l) => l.sort(newestFirst("PremiereDate"))),
    fewer ? [] : fromLibs(main, "Movie", "CommunityRating", "&IsPlayed=false").then((l) => l.sort((a, b) => (b.CommunityRating || 0) - (a.CommunityRating || 0))),
    fewer ? [] : fromLibs(standLibs, "Movie", "Random", "&IsPlayed=false"),
    fewer ? [] : B.trendingMissing("movies"),
    fewer ? null : lovedRow("Movie", (id) => !anime.has(id)).catch(() => null),
  ]);
  if (!B.alive(nav)) return;
  const keep = resume.filter(notAnime);
  pageRows([
    B.row("Keep watching", keep.map((it) => B.posterCard(it, { progress: true }))),
    B.row("Top 10 movies this week", B.top10(top)),
    because ? B.row(`Because you watched ${because.name}`, because.items.map((it) => B.posterCard(it))) : "",
    loved && !(because && because.name === loved.name) ? B.row(`Because you loved ${loved.name}`, loved.items.map((it) => B.posterCard(it))) : "",
    B.row("Recently added", fresh.slice(0, 20).map((it) => B.posterCard(it, { markNew: true })), { seeAll: "#/grid/movies?anime=hide&title=All%20movies" }),
    B.row("Recently released", released.slice(0, 20).map((it) => B.posterCard(it))),
    B.row("Top rated you haven't seen", best.slice(0, 20).map((it) => B.posterCard(it))),
    B.row("Stand-up", stand.map((it) => B.posterCard(it))),
    B.row("Trending, not on Glowfin", missingCards(missing)),
  ]);
  B.wireDescribe(keep[0] || top[0] || fresh[0]);
  const f = $("#rows .poster"); if (f) f.focus({ preventScroll: true });
});

B.route(/^\/shows$/, async (m, nav) => {
  B.page().innerHTML = B.top("Shows", DOORS.shows.color) + doorTop() + `<div class="rows" id="rows">${B.spinner("rows")}</div>`;
  B.wireTop();
  const u = B.uid(), F = B.FIELDS;
  const libs = await B.libs("shows");
  const main = libs.filter((l) => !B.isAnimeLib(l));
  const anime = await B.animeIds("Series");
  const notAnime = (ep) => !anime.has(ep.SeriesId || ep.Id);
  const fewer = B.me.kids;
  const [resume, next, latestEps, fresh, because, best, missing, top, loved] = await Promise.all([
    q(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Episode&Limit=20&Fields=${F}`).then((l) => l.filter((e) => !B.watchedEp(e))).catch(() => []),
    q(`/Shows/NextUp?userId=${u}&Limit=40&Fields=${F}&enableResumable=false&enableRewatching=false`).then((l) => B.fixNextUp(l, F)).then(B.keep).catch(() => []),
    q(`/Items?userId=${u}&IncludeItemTypes=Episode&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=80&Fields=DateCreated,SeriesId,UserData&Filters=IsUnplayed`).catch(() => []),
    fromLibs(main, "Series", "DateCreated").then((l) => l.sort(newestFirst("DateCreated"))),
    fewer ? null : becauseRow("Series", (id) => !anime.has(id)).catch(() => null),
    fewer ? [] : fromLibs(main, "Series", "CommunityRating", "&IsPlayed=false").then((l) => l.sort((a, b) => (b.CommunityRating || 0) - (a.CommunityRating || 0))),
    fewer ? [] : B.trendingMissing("tv"),
    fewer ? [] : B.trending("Series").catch(() => []),
    fewer ? null : lovedRow("Series", (id) => !anime.has(id)).catch(() => null),
  ]);
  if (!B.alive(nav)) return;
  const one = (list) => { const seen = new Set(); return list.filter((e) => notAnime(e) && !seen.has(e.SeriesId) && seen.add(e.SeriesId)); };
  const cont = one(resume), nextUp = one(next);
  const watching = new Set(nextUp.map((e) => e.SeriesId));
  const newCount = {};
  latestEps.filter((e) => B.isNew(e) && watching.has(e.SeriesId)).forEach((e) => { newCount[e.SeriesId] = (newCount[e.SeriesId] || 0) + 1; });
  const ids = [...new Set(cont.concat(nextUp).map((e) => e.SeriesId).concat(Object.keys(newCount)))];
  const series = ids.length ? await q(`/Items?userId=${u}&Ids=${ids.join(",")}&Fields=${F}`).catch(() => []) : [];
  if (!B.alive(nav)) return;
  const sById = {}; series.forEach((x) => { sById[x.Id] = x; });
  const epCard = (ep) => {
    const sh = sById[ep.SeriesId];
    B.items[ep.Id] = ep;
    const card = B.posterCard(sh || ep, { href: `#/show/${ep.SeriesId}?ep=${ep.Id}`, sub: `S${ep.ParentIndexNumber} E${ep.IndexNumber}`, noTick: true, progress: true, newCount: newCount[ep.SeriesId] });
    return card.replace(`data-id="${esc((sh || ep).Id)}"`, `data-id="${esc(ep.Id)}"`);
  };
  pageRows([
    B.row("Keep watching", cont.map(epCard)),
    B.row("Top 10 shows this week", B.top10(top)),
    B.row("Up next", nextUp.map(epCard)),
    fewer ? "" : B.row("New episodes", Object.keys(newCount).map((id) => sById[id]).filter(Boolean).map((sh) => B.posterCard(sh, { newCount: newCount[sh.Id] }))),
    because ? B.row(`Because you watched ${because.name}`, because.items.map((it) => B.posterCard(it))) : "",
    loved && !(because && because.name === loved.name) ? B.row(`Because you loved ${loved.name}`, loved.items.map((it) => B.posterCard(it))) : "",
    B.row("Recently added", fresh.slice(0, 20).map((it) => B.posterCard(it, { markNew: true })), { seeAll: "#/grid/shows?anime=hide&sort=newshow&title=All%20shows" }),
    B.row("Top rated you haven't seen", best.slice(0, 20).map((it) => B.posterCard(it))),
    B.row("Trending, not on Glowfin", missingCards(missing)),
  ]);
  B.wireDescribe(cont[0] || nextUp[0] || fresh[0]);
  const f = $("#rows .poster"); if (f) f.focus({ preventScroll: true });
});

// Glowfin (ui-7, Ben's pick A): Anime's own page. Anime shows and movies together, no requests tab
B.route(/^\/anime$/, async (m, nav) => {
  B.page().innerHTML = B.top("Anime", DOORS.shows.color) + doorTop() + `<div class="rows" id="rows">${B.spinner("rows")}</div>`;
  B.wireTop();
  const u = B.uid(), F = B.FIELDS;
  const showLibs = (await B.libs("shows")).filter(B.isAnimeLib), movieLibs = (await B.libs("movies")).filter(B.isAnimeLib);
  const [showIds, movieIds] = await Promise.all([B.animeIds("Series"), B.animeIds("Movie")]);
  const [resumeM, resumeE, next, airing, top, because, gems, films, freshShows, missing] = await Promise.all([
    q(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Movie&Limit=20&Fields=${F}`).catch(() => []),
    q(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Episode&Limit=20&Fields=${F}`).then((l) => l.filter((e) => !B.watchedEp(e))).catch(() => []),
    q(`/Shows/NextUp?userId=${u}&Limit=40&Fields=${F}&enableResumable=false&enableRewatching=false`).then((l) => B.fixNextUp(l, F)).then(B.keep).catch(() => []),
    fromLibs(showLibs, "Series", "DateLastContentAdded", "&SeriesStatus=Continuing"),
    B.trending("Series", true).catch(() => []),
    becauseRow("Series", (id) => showIds.has(id)).catch(() => null),
    fromLibs(showLibs, "Series", "Random", "&IsPlayed=false&MinCommunityRating=7.5"),
    fromLibs(movieLibs, "Movie", "DateCreated").then((l) => l.sort(newestFirst("DateCreated"))),
    fromLibs(showLibs, "Series", "DateCreated"),
    B.me.kids ? [] : B.trendingMissing("anime"),
  ]);
  if (!B.alive(nav)) return;
  const seen = new Set();
  const cont = resumeM.filter((x) => movieIds.has(x.Id)).concat(resumeE.concat(next).filter((e) => showIds.has(e.SeriesId)))
    .filter((x) => { const k = x.SeriesId || x.Id; if (seen.has(k)) return false; seen.add(k); return true; });
  const card = (it) => (it.Type === "Episode"
    ? B.posterCard(Object.assign({}, it, { Name: it.SeriesName }), { href: B.hrefOf(it), sub: `S${it.ParentIndexNumber} E${it.IndexNumber}`, noTick: true, progress: true })
    : B.posterCard(it, { progress: true }));
  const fresh = freshShows.concat(films).sort(newestFirst("DateCreated")).slice(0, 20);
  pageRows([
    B.row("Keep watching", cont.map(card)),
    top.length >= 3 ? B.row("Top 10 anime", B.top10(top)) : "",
    B.row("Airing this season", airing.map((it) => B.posterCard(it))),
    because ? B.row(`Because you watched ${because.name}`, because.items.map((it) => B.posterCard(it))) : "",
    B.row("Hidden gem anime", gems.map((it) => B.posterCard(it))),
    B.row("Anime movies", films.slice(0, 20).map((it) => B.posterCard(it)), { seeAll: "#/grid/movies?anime=only&title=Anime%20movies" }),
    B.row("Recently added", fresh.map((it) => B.posterCard(it, { markNew: true }))),
    B.row("Trending, not on Glowfin", missingCards(missing)),
  ]);
  B.wireDescribe(cont[0] || airing[0] || fresh[0]);
  const f = $("#rows .poster"); if (f) f.focus({ preventScroll: true });
});

// The Anime page's Genres and tags tab: genres found in the anime libraries
B.route(/^\/browse\/anime\/genres$/, async (m, nav) => {
  B.page().innerHTML = B.top("Anime", DOORS.shows.color) + `<div class="wrap"><div class="h1">Genres and tags</div><div id="t">${B.spinner("wall")}</div></div>`;
  B.wireTop();
  const libs = (await B.libs("shows")).filter(B.isAnimeLib);
  const names = new Set();
  for (const l of libs) {
    const r = await B.get(`/Genres?userId=${B.uid()}&ParentId=${l.Id}&IncludeItemTypes=Series&Recursive=true&SortBy=SortName`).catch(() => ({}));
    (r.Items || []).forEach((g) => names.add(g.Name));
  }
  if (!B.alive(nav)) return;
  $("#t").innerHTML = `<div class="grid" style="grid-template-columns:repeat(auto-fill,minmax(200px,1fr))">${[...names].sort().map((g) => `<a class="tile2" style="width:auto" href="#/grid/shows?anime=only&genre=${enc(g)}&title=${enc(g)}">${esc(g)}</a>`).join("") || '<div class="empty">Nothing here yet.</div>'}</div>`;
});

// ---------- Genres / Studios / Networks tiles ----------
B.route(/^\/browse\/(movies|shows)\/(genres|studios)$/, async (m, nav) => {
  const door = DOORS[m[1]], type = m[1] === "movies" ? "Movie" : "Series";
  const label = m[2] === "genres" ? "Genres" : m[1] === "shows" ? "Networks" : "Studios";
  B.page().innerHTML = B.top(door.name, door.color) + `<div class="wrap"><div class="h1">${label}</div><div id="t">${B.spinner("wall")}</div></div>`;
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
      ${B.q("anime") ? "" : `<button class="chip ${st.anime !== "all" ? "on" : ""}" id="gAnime"></button>`}
      ${doorKey === "shows" ? `<button class="chip" id="gWatched"></button>` : ""}`}
      <input class="search-in" id="gText" placeholder="Search ${esc(door.name.toLowerCase())}" value="${esc(st.text)}">
    </div>
    <div class="slim" id="slim">&nbsp;</div>
    <div class="grid-wrap${st.anime === "only" ? " big-cards" : ""}"><div class="grid-body" id="gBody">${B.spinner("wall")}</div><div class="letters hidden" id="letters"></div></div>`;
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

  // My List also holds titles that aren't on Glowfin yet (2026-11-21): a row on top with where each ask is.
  // When one arrives Tower puts it on the real My List by itself.
  if (st.mine && type !== "BoxSet" && !B.me.kids) {
    B.call("GET", "api/asks/wish").then((w) => {
      const mine = ((w && w.items) || []).filter((x) => x.type === (type === "Movie" ? "movie" : "tv"));
      const bar = $(".gridbar");
      if (!mine.length || !B.alive(nav) || !bar || $("#wishRow")) return;
      bar.insertAdjacentHTML("beforebegin", `<div id="wishRow">${B.row("On your list, not on Glowfin yet", mine.map((x) => B.askCard({ mediaType: x.type, id: x.tmdb, title: x.title, posterPath: x.poster })))}</div>`);
    }).catch(() => {});
  }

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
  const p = B.side(`<h3>Rate ${esc(it.Name)}</h3><p class="dim">Glowfin uses this to pick things for you.</p>
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
  mdl.innerHTML = id ? `<iframe style="display:block;width:100%;aspect-ratio:16/9;border:0" src="https://www.youtube-nocookie.com/embed/${id}?autoplay=1" allow="autoplay; fullscreen" allowfullscreen></iframe><p style="margin:0;padding:10px 16px;text-align:right"><a href="https://www.youtube.com/watch?v=${id}" target="_blank" rel="noopener">Stuttering? Open on YouTube</a></p>` : `<p style="padding:20px"><a href="${esc(yt)}" target="_blank" rel="noopener">Open the trailer</a></p>`;
  document.body.appendChild(mdl);
};
B.peopleRow = (it) => {
  const cast = (it.People || []).filter((p) => p.Type === "Actor").slice(0, 20);
  if (!cast.length) return "";
  return `<section class="rowx people-row"><h3>Cast and crew</h3><div class="strip">${cast.map((p) => `<a class="person-c" href="#/person/${p.Id}"><span class="pic" style="${p.PrimaryImageTag ? `background-image:url('${B.img(p.Id, "Primary", { h: 240, tag: p.PrimaryImageTag })}')` : ""}">${p.PrimaryImageTag ? "" : esc(B.initials(p.Name))}</span>${esc(p.Name)}<small>${esc(p.Role || "")}</small></a>`).join("")}</div></section>`;
};
B.facts = async (it) => {
  const f = [];
  const show = it.Type === "Series";
  if (it.Studios && it.Studios.length) f.push([show ? "Network" : "Studio", it.Studios.map((s) => s.Name).slice(0, 2).join(", ")]);
  if (it.PremiereDate) f.push([show ? "First aired" : "Released", B.day(it.PremiereDate)]);
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
  return `<section class="rowx"><h3>Details</h3><div class="facts">${f.map(([k, v]) => `<div${k === "File" ? ` class="fact-file"` : ""}><b>${esc(k)}</b>${esc(v)}</div>`).join("")}</div></section>`;
};
B.audioLine = (it) => {
  const ms = (it.MediaSources || [])[0];
  const a = ms && (ms.MediaStreams || []).filter((s) => s.Type === "Audio");
  if (!a || !a.length) return "";
  // "und" (and "mis", "zxx", "mul") mean the file never said which language, so they are left out
  const unknown = (c) => !c || ["und", "undefined", "unk", "mis", "zxx", "mul", "qaa"].includes(String(c).toLowerCase());
  return [...new Set(a.map((s) => (unknown(s.Language) ? "" : s.DisplayLanguage || langName(s.Language))))].filter((x) => x && !unknown(x)).slice(0, 4).join(", ");
};
const langName = (code) => { try { return new Intl.DisplayNames(["en"], { type: "language" }).of(code); } catch (e) { return code; } };
B.langName = langName;

// ---------- movie page (Redesign 2 title page: js/title.js) ----------
B.route(/^\/movie\/([\w-]+)/, async (m, nav) => {
  const it = await B.get(`/Users/${B.uid()}/Items/${m[1]}?Fields=${B.FIELDS},People,Studios,MediaSources,ProductionLocations,RemoteTrailers,LocalTrailerCount,Chapters`).catch(() => B.get(`/Items/${m[1]}?userId=${B.uid()}&Fields=${B.FIELDS},People,MediaSources,RemoteTrailers`));
  if (!B.alive(nav)) return;
  B.items[it.Id] = it;
  const ud = it.UserData || {};
  const resume = ud.PlaybackPositionTicks > 0 && !ud.Played;
  const len = B.minutes(it.RunTimeTicks);
  const directors = (it.People || []).filter((p) => p.Type === "Director").map((p) => p.Name).slice(0, 2);
  const hasTrailer = !!(it.LocalTrailerCount || (it.RemoteTrailers || []).length);
  const scores = B.me.kids ? [] : [it.CommunityRating ? ["IMDb", (+it.CommunityRating).toFixed(1)] : null, it.CriticRating ? ["Rotten Tomatoes", Math.round(it.CriticRating) + "%"] : null];
  const audio = B.audioLine(it).split(",")[0];
  const tp = B.titlePage({
    title: it.Name,
    original: it.OriginalTitle,
    color: DOORS.movies.color,
    logo: B.logoUrl(it),
    tagline: (it.Taglines || [])[0],
    chips: B.qualityChips(it),
    picture: B.backdrop(it, 1920),
    rating: it.OfficialRating,
    info: [it.ProductionYear, len ? B.len(len) : "", (it.Genres || []).slice(0, 2).join(" · ")],
    buttons: [
      { label: resume ? "Resume" : "Play", icon: "play", href: `#/play/${it.Id}${resume ? "?resume=1" : ""}`, note: resume ? `${B.len(B.minutes(it.RunTimeTicks - ud.PlaybackPositionTicks))} left` : "" },
      { label: "Trailer", icon: "trailer", off: hasTrailer ? "" : "No trailer for this one yet", onClick: () => B.trailer(it) },
      { id: "bList", label: "My List", icon: ud.IsFavorite ? "check" : "plus", onClick: (e, el) => B.toggleList(it, el) },
      B.me.kids ? null : { label: "Rate", icon: "thumb", onClick: () => B.rate(it) },
      { label: "More", icon: "more", onClick: () => movieMore(it) },
    ].filter(Boolean),
    // Glowfin D1: story + scores up top, then rows: Cast and crew, More like this (+ collection), Ask for similar
    rows: true,
    story: it.Overview,
    scores: B.tpStrip(scores),
    strip: B.tpStrip([audio ? [audio] : null, directors.length ? ["Director", directors.join(", "), true] : null]),
    tabs: [
      { name: "Cast", id: "tpCast", html: B.peopleRow(it) },
      { name: "Reviews", id: "tpReviews", load: (pane) => B.reviewsRow(pane, it, "movie", nav) },
      { name: "More like this", id: "below", load: async (pane) => {
        const similar = await q(`/Items/${it.Id}/Similar?userId=${B.uid()}&Limit=20&Fields=${B.FIELDS}`).catch(() => []);
        if (B.alive(nav)) pane.insertAdjacentHTML("afterbegin", B.row("More like this", similar.map((x) => B.posterCard(x))));
      } },
      { name: "Ask for similar", id: "tpAsk", skip: !!B.me.kids },
      { name: "Parents' guide", id: "tpParents" },
    ],
  });
  // Details (studio, release date, the file for you) under the whole story
  B.facts(it).then((html) => tp.setStoryExtra(html));
  // In this collection, Ask for similar, anime extras and the parents' guide (extras.js)
  if (B.extras) B.extras.movie(it, nav, { more: tp.pane("below"), ask: tp.pane("tpAsk"), parents: tp.pane("tpParents"), chars: tp.pane("tpCast") });
});
// Play on TV (2026-11-21): send a movie or episode from the browser to a TV that has Glowfin open, at the same spot
B.playOnTv = async (it) => {
  const sessions = await B.get(`/Sessions?controllableByUserId=${B.uid()}`).catch(() => []);
  const mine = String(B.me.device || "");
  const tvs = (Array.isArray(sessions) ? sessions : []).filter((x) => String(x.DeviceId || "").startsWith("benflix-") && x.DeviceId !== mine && x.SupportsRemoteControl)
    .filter((x, i, a) => a.findIndex((y) => y.DeviceId === x.DeviceId) === i);
  const ud = it.UserData || {};
  const ticks = ud.PlaybackPositionTicks > 0 && !ud.Played ? ud.PlaybackPositionTicks : 0;
  const p = B.side(`<h3>Play on TV</h3>` + (tvs.length
    ? `<p class="dim">Pick a TV. ${ticks ? "It picks up where you left off." : "It starts playing there."}</p>${tvs.map((x, i) => `<button class="opt" data-tv="${i}">${I("tv")} ${esc(x.DeviceName || "TV")}<small>${esc(x.UserName || "")}</small></button>`).join("")}`
    : `<p class="dim">No TV has Glowfin open right now. Open Glowfin on the TV, then try again.</p>`));
  p.onclick = (e) => {
    const b = e.target.closest("[data-tv]"); if (!b) return;
    const tv = tvs[+b.dataset.tv];
    B.closeAll();
    B.busy(null, async () => {
      await B.jf("POST", `/Sessions/${tv.Id}/Playing?playCommand=PlayNow&itemIds=${it.Id}${ticks ? `&startPositionTicks=${ticks}` : ""}`);
      return `Playing on ${tv.DeviceName || "the TV"}.`;
    });
  };
};
const movieMore = (it) => {
  const ud = it.UserData || {};
  const ids = it.ProviderIds || {};
  const resume = ud.PlaybackPositionTicks > 0 && !ud.Played;
  B.tpMore(it.Name, [
    { label: ud.Played ? "Mark as not watched" : "Mark as watched", icon: "check", onClick: () => B.busy(null, async () => { await B.markWatched(it, !ud.Played); B.render(); return ud.Played ? "Marked as not watched." : "Marked as watched."; }) },
    resume ? { label: "Play from the start", icon: "restart", onClick: () => B.go(`#/play/${it.Id}`) } : null,
    B.me.kids ? null : { label: "Play on TV", icon: "tv", onClick: () => B.playOnTv(it) },
    !B.lobby || !B.lobby.allowed() ? null : { label: "Watch together in the Lobby", icon: "tv", onClick: () => B.busy(null, () => B.lobby.open({ item: it, pos: resume ? ud.PlaybackPositionTicks / B.TICKS : 0 })) },
    { label: "Audio, subtitles and version", icon: "sub", onClick: () => B.trackPicker(it, (a, s) => B.go(`#/play/${it.Id}?resume=1&audio=${a}&sub=${s}`)) },
    B.offline ? B.offline.menu(it) : null,
    { label: "Report a problem", icon: "flag", onClick: () => B.report({ id: it.Id, title: it.Name }) },
    ids.Imdb ? { label: "Open on IMDb", icon: "info", href: `https://www.imdb.com/title/${esc(ids.Imdb)}`, blank: true } : null,
    B.me.admin ? { label: "Pick a poster", icon: "swap", youOnly: true, onClick: () => B.fixItem(it) } : null,
    B.me.admin && !B.me.kids && B.kidsChoice ? { label: "Kids profile", icon: "star", youOnly: true, onClick: () => B.kidsChoice(it) } : null,
  ]);
};
// Audio + subtitles panel before playing
B.trackPicker = (it, done) => {
  const ms = (it.MediaSources || [])[0] || {};
  const streams = ms.MediaStreams || [];
  const audio = streams.filter((s) => s.Type === "Audio");
  let a = ms.DefaultAudioStreamIndex ?? (audio[0] && audio[0].Index), s = B.autoSub(streams, ms.DefaultSubtitleStreamIndex ?? -1, a);
  let subs = B.menuSubs(streams, s);
  const draw = () => `<h3>Audio and subtitles</h3>
    <h4 class="dim" style="margin:16px 0 8px">Audio</h4>${audio.map((x) => `<button class="opt ${x.Index === a ? "on" : ""}" data-a="${x.Index}">${esc(x.DisplayTitle || x.Language || "Track " + x.Index)}</button>`).join("") || '<p class="dim">Only one sound track.</p>'}
    <h4 class="dim" style="margin:16px 0 8px">Subtitles</h4><button class="opt ${s === -1 ? "on" : ""}" data-s="-1">Off</button>${subs.map((x) => `<button class="opt ${x.Index === s ? "on" : ""}" data-s="${x.Index}">${esc(B.subLabel(x, streams))}</button>`).join("")}
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

// Idea Box 25 "Catch me up": back on a show after three weeks or more (and not finished)? A button next to Play shows
// the stories of the last three episodes watched, never anything past them. Same as the TV and phone.
B.catchUp = async (show, target, nav) => {
  if ((show.UserData || {}).Played) return;
  const u = B.uid();
  const list = await B.get(`/Items?userId=${u}&ParentId=${show.Id}&Recursive=true&IncludeItemTypes=Episode&SortBy=DatePlayed&SortOrder=Descending&Fields=Overview&EnableImages=false&Limit=8`)
    .then((r) => (r.Items || []).filter((e) => e.UserData && e.UserData.LastPlayedDate)).catch(() => []);
  if (!list.length || !B.alive(nav)) return;
  if (Date.now() - new Date(list[0].UserData.LastPlayedDate) < 21 * 86400000) return;
  const eps = list.filter((e) => e.UserData.Played).slice(0, 3).reverse();
  const first = $(".tp .btns > :first-child");
  if (!eps.length || !first) return;
  const short = (t) => { const x = String(t || "").trim(); if (!x) return "No story for this one."; const two = x.split(/(?<=[.!?])\s+/).slice(0, 2).join(" "); return two.length > 220 ? two.slice(0, 220) + "…" : two; };
  first.insertAdjacentHTML("afterend", `<button class="btn big" id="bCatch">${I("info")} <span>Catch me up</span></button>`);
  $("#bCatch").onclick = () => {
    const p = B.side(`<h3>Where you left off</h3><p class="dim">${esc(show.Name)}</p>
      ${eps.map((e) => `<div class="opt" style="display:block;cursor:default"><b>${esc(e.ParentIndexNumber === 0 ? "Special" : `S${e.ParentIndexNumber} E${e.IndexNumber}`)} · ${esc(e.Name || "")}</b><small style="display:block;white-space:normal">${esc(short(e.Overview))}</small></div>`).join("")}
      ${target ? `<div class="acts" style="margin-top:16px"><a class="btn white" href="#/play/${target.Id}?resume=1">${I("play")} Play S${target.ParentIndexNumber} E${target.IndexNumber}</a></div>` : ""}`);
    p.onclick = (e) => { if (e.target.closest("a.btn")) B.closeAll(); };
  };
};

// ---------- show page: seasons + episodes ----------
B.route(/^\/show\/([\w-]+)/, async (m, nav) => {
  const u = B.uid();
  const [it, seasons, nextUp] = await Promise.all([
    B.get(`/Users/${u}/Items/${m[1]}?Fields=${B.FIELDS},People,Studios,RemoteTrailers,LocalTrailerCount`),
    B.get(`/Shows/${m[1]}/Seasons?userId=${u}&Fields=ChildCount`).then((r) => r.Items || []),
    B.nextEpisode(m[1], B.FIELDS).then((ep) => ep || B.get(`/Shows/NextUp?userId=${u}&SeriesId=${m[1]}&Limit=1&enableResumable=true&Fields=${B.FIELDS}`).then((r) => (r.Items || [])[0])).catch(() => null),
  ]);
  if (!B.alive(nav)) return;
  B.items[it.Id] = it;
  const ud = it.UserData || {};
  const wantEp = B.q("ep"), wantSeason = B.q("season");
  // The episode in the address (?ep=, from the card you clicked) only picks which season row opens, and only while it's
  // still unwatched. Play always follows where you really are in the show, so coming back or refreshing never sticks
  // on the episode you first landed on.
  let landed = wantEp ? await B.get(`/Users/${u}/Items/${wantEp}?Fields=${B.FIELDS}`).catch(() => null) : null;
  if (landed && (landed.UserData || {}).Played) landed = null;
  const target = nextUp || landed;
  const focus = landed || target;
  if (wantEp && !landed && B.alive(nav)) history.replaceState(null, "", `#/show/${m[1]}${wantSeason ? `?season=${wantSeason}` : ""}`);
  const seasonId = wantSeason || (focus && focus.SeasonId) || (seasons.find((s) => s.IndexNumber > 0) || seasons[0] || {}).Id;
  const resumeLabel = target ? `${target.UserData && target.UserData.PlaybackPositionTicks && !target.UserData.Played ? "Resume" : "Play"} S${target.ParentIndexNumber} E${target.IndexNumber}` : "Play";
  const bits = [it.ProductionYear ? (it.Status === "Ended" && it.EndDate ? `${it.ProductionYear}–${new Date(it.EndDate).getFullYear()}` : it.ProductionYear) : "", seasons.filter((s) => s.IndexNumber > 0).length ? `${seasons.filter((s) => s.IndexNumber > 0).length} seasons` : "", it.OfficialRating ? `<span class="rate">${esc(it.OfficialRating)}</span>` : "", it.Status === "Ended" ? "Ended" : it.AirDays && it.AirDays.length ? `New episodes on ${it.AirDays[0]}s` : ""].filter(Boolean);
  const creators = (it.People || []).filter((p) => p.Type === "Creator").map((p) => p.Name).slice(0, 2);
  const maker = creators.length ? ["Created by", creators.join(", "), true] : it.Studios && it.Studios.length ? ["Network", it.Studios[0].Name, true] : null;
  const hasTrailer = !!(it.LocalTrailerCount || (it.RemoteTrailers || []).length);
  const scores = B.me.kids ? [] : [it.CommunityRating ? ["IMDb", (+it.CommunityRating).toFixed(1)] : null, it.CriticRating ? ["Rotten Tomatoes", Math.round(it.CriticRating) + "%"] : null];
  const plainBits = bits.filter((x) => !String(x).startsWith("<"));
  const tp = B.titlePage({
    title: it.Name,
    original: it.OriginalTitle,
    color: DOORS.shows.color,
    logo: B.logoUrl(it),
    tagline: (it.Taglines || [])[0],
    picture: B.backdrop(it, 1920),
    rating: it.OfficialRating,
    info: [...plainBits.map(String), (it.Genres || [])[0]],
    buttons: [
      target ? { label: resumeLabel, icon: "play", href: `#/play/${target.Id}?resume=1` } : { label: "Play", icon: "play", off: "No episodes on Glowfin yet" },
      { label: "Trailer", icon: "trailer", off: hasTrailer ? "" : "No trailer for this one yet", onClick: () => B.trailer(it) },
      { id: "bList", label: "My List", icon: ud.IsFavorite ? "check" : "plus", onClick: (e, el) => B.toggleList(it, el) },
      { label: "More", icon: "more", id: "bMore" },
    ],
    // Glowfin D1: story + scores up top, then rows: seasons and episodes, Cast and crew, More like this, Ask for similar
    rows: true,
    story: it.Overview,
    scores: B.tpStrip(scores),
    strip: B.tpStrip([maker]),
    tabs: [
      { name: "Episodes", id: "tpEpisodes", keep: true, html: `<div class="seasons" id="seasons">${seasons.map((s) => `<button class="chip ${s.Id === seasonId ? "on" : ""}" style="--accent:${DOORS.shows.color}" data-season="${s.Id}">${esc(s.IndexNumber === 0 ? "Specials" : s.Name)}</button>`).join("")}</div>
        <div class="seasons" id="groups"></div><div id="eps">${B.spinner("list")}</div>` },
      { name: "Cast", id: "tpCast", html: B.peopleRow(it) },
      { name: "Reviews", id: "tpReviews", load: (pane) => B.reviewsRow(pane, it, "tv", nav) },
      { name: "More like this", id: "below2", load: async (pane) => {
        const similar = await q(`/Items/${it.Id}/Similar?userId=${u}&Limit=20&Fields=${B.FIELDS}`).catch(() => []);
        if (B.alive(nav)) pane.insertAdjacentHTML("afterbegin", B.row("More like this", similar.map((x) => B.posterCard(x))));
      } },
      { name: "Ask for similar", id: "tpAsk", skip: !!B.me.kids },
      { name: "Characters and voices", id: "tpChars" },
      { name: "Parents' guide", id: "tpParents" },
    ],
  });
  B.facts(it).then((html) => tp.setStoryExtra(html));
  B.catchUp(it, target, nav);
  $("#bMore").onclick = () => B.tpMore(it.Name, [
    { label: ud.Played ? "Mark the whole show as not watched" : "Mark the whole show as watched", icon: "check", onClick: () => B.busy(null, async () => { await B.markWatched(it, !ud.Played); B.render(); return "Done."; }) },
    B.me.kids ? null : { label: "Rate", icon: "thumb", onClick: () => B.rate(it) },
    // Glowfin app for computers: the season showing now, saved to watch with no connection
    B.offline && B.offline.can() ? { label: "Download this season", icon: "dl", onClick: () => { const on = $("#seasons .chip.on"); B.offline.season(it, on ? on.dataset.season : "", on ? on.textContent.trim() : "").catch((err) => B.toast(err.message)); } } : null,
    B.me.kids || !target ? null : { label: `Play S${target.ParentIndexNumber} E${target.IndexNumber} on TV`, icon: "tv", onClick: () => B.playOnTv(target) },
    !B.lobby || !B.lobby.allowed() || !target ? null : { label: `Watch S${target.ParentIndexNumber} E${target.IndexNumber} together in the Lobby`, icon: "tv", onClick: () => B.busy(null, () => B.lobby.open({ item: Object.assign({ OfficialRating: it.OfficialRating }, target, target.OfficialRating ? {} : { OfficialRating: it.OfficialRating }), pos: target.UserData && target.UserData.PlaybackPositionTicks && !target.UserData.Played ? target.UserData.PlaybackPositionTicks / B.TICKS : 0 })) },
    { label: "Report a problem", icon: "flag", onClick: () => B.report({ id: it.Id, title: it.Name }) },
    B.me.admin ? { label: "Pick a poster", icon: "swap", youOnly: true, onClick: () => B.fixItem(it) } : null,
    B.me.admin && !B.me.kids && B.kidsChoice ? { label: "Kids profile", icon: "star", youOnly: true, onClick: () => B.kidsChoice(it) } : null,
  ]);
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
    const t = focus && $(`#epRow [data-id="${CSS.escape(focus.Id)}"]`);
    if (t) { t.scrollIntoView({ inline: "start", block: "nearest" }); $("#epRow .strip").scrollLeft -= 48; }
  };
  const loadSeason = async (sid) => {
    $("#eps").innerHTML = B.spinner("list");
    episodes = await q(`/Shows/${it.Id}/Episodes?userId=${u}&SeasonId=${sid}&Fields=Overview,PremiereDate,RunTimeTicks,DateCreated,UserData&EnableImages=true`);
    group = 0;
    if (focus) { const i = episodes.findIndex((e) => e.Id === focus.Id); if (i >= 0 && episodes.length > 50) group = Math.floor(i / 25); }
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
    B.dropdown(c, [{ value: "w", label: played ? "Mark as not watched" : "Mark as watched" }, { value: "before", label: "Mark all before as watched" }, { value: "start", label: "Play from start" }].concat(B.offline && B.offline.can() ? [{ value: "dl", label: "Download to this computer" }] : []), null, (v) => {
      if (v === "start") return B.go(`#/play/${ep.Id}`);
      if (v === "dl") return B.offline.one(Object.assign({ SeriesName: it.Name, SeriesId: it.Id }, ep)).catch((err) => B.toast(err.message));
      B.busy(null, async () => {
        const list = v === "w" ? [ep] : episodes.slice(0, idx);
        for (const x of list) await B.markWatched(x, v === "w" ? !played : true);
        drawEps();
        return "Done.";
      });
    });
  });
  if (seasonId) await loadSeason(seasonId); else $("#eps").innerHTML = "";
  // Newer seasons to ask for, Ask for similar, anime extras and the parents' guide (extras.js)
  if (B.alive(nav) && B.extras) B.extras.show(it, nav, { more: tp.pane("below2"), ask: tp.pane("tpAsk"), parents: tp.pane("tpParents"), chars: tp.pane("tpChars"), episodes: tp.pane("tpEpisodes") });
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
    B.get(`/Users/${u}/Items/${m[1]}?Fields=Overview,PremiereDate,EndDate,ProductionLocations,ProviderIds`),
    q(`/Items?userId=${u}&PersonIds=${m[1]}&IncludeItemTypes=Movie&Recursive=true&SortBy=ProductionYear&SortOrder=Descending&Fields=${B.FIELDS}`),
    q(`/Items?userId=${u}&PersonIds=${m[1]}&IncludeItemTypes=Series&Recursive=true&SortBy=ProductionYear&SortOrder=Descending&Fields=${B.FIELDS}`),
  ]);
  if (!B.alive(nav)) return;
  // Redesign 2 actor page: their picture where the cover goes, then tabs Movies / Shows / Not on Benflix yet
  const grid = (list) => (list.length ? `<div class="grid tp-grid">${list.map((x) => B.posterCard(x)).join("")}</div>` : "");
  const fav = !!(p.UserData && p.UserData.IsFavorite);
  const tp = B.titlePage({
    title: p.Name,
    color: DOORS.movies.color,
    cover: { url: p.ImageTags && p.ImageTags.Primary ? B.img(p.Id, "Primary", { h: 600 }) : "", ratio: 2 / 3 },
    info: [p.PremiereDate ? "Born " + B.day(p.PremiereDate) : "", p.EndDate ? "Died " + B.day(p.EndDate) : "", (p.ProductionLocations || [])[0] || ""],
    buttons: [
      { id: "pFav", label: fav ? "Favourite" : "Add to favourites", icon: "heart", onClick: async (e, el) => {
        const on = !(p.UserData && p.UserData.IsFavorite);
        await B.jf(on ? "POST" : "DELETE", `/Users/${u}/FavoriteItems/${p.Id}`).catch(() => {});
        p.UserData = Object.assign({}, p.UserData, { IsFavorite: on });
        el.querySelector("span").textContent = on ? "Favourite" : "Add to favourites";
      } },
      { label: "About them", icon: "info", off: p.Overview ? "" : "No life story for them yet", onClick: () => B.side(`<h3>${esc(p.Name)}</h3><p style="line-height:1.6">${esc(p.Overview || "")}</p>`) },
    ],
    tabs: [
      { name: "Movies", id: "tpMovies", html: grid(movies) },
      { name: "Shows", id: "tpShows", html: grid(shows) },
      { name: "Not on Glowfin yet", id: "tpAsk", html: "", skip: !!B.me.kids },
    ],
  });
  // The Overview tab isn't used on actor pages: their story is behind About them
  const ov = $('.tp-tab[data-tab="tpOverview"]'); if (ov) ov.remove();
  if (!movies.length && !shows.length) tp.pane("tpMovies").insertAdjacentHTML("afterbegin", '<div class="wrap empty">Nothing with them on Glowfin yet.</div>');
  tp.refresh();
  const first = $(".tp-tab:not([hidden])"); if (first) first.click();
  // Everything of theirs that isn't on Benflix yet (extras.js)
  if (B.extras && !B.me.kids) B.extras.person(p, nav, tp.pane("tpAsk")).catch(() => {});
});

// "What people said" (2026-11-21): two short reviews from TMDb on a movie or show's page, cut to three lines;
// press one to read it all. Tower fetches them (never for Kids), and sends none until Ben adds the free key.
B.reviewsRow = async (pane, it, kind, nav) => {
  const tmdb = it.ProviderIds && it.ProviderIds.Tmdb;
  if (!tmdb || (B.me && B.me.kids)) return;
  const d = await B.call("GET", `api/reviews?type=${kind}&tmdb=${encodeURIComponent(tmdb)}`).catch(() => null);
  const list = (d && d.reviews) || [];
  if (!list.length || !B.alive(nav)) return;
  const head = (r) => (r.score != null ? `${r.score} / 10 · ` : "") + r.author;
  pane.insertAdjacentHTML("afterbegin", `<section class="rowx"><h3>What people said</h3><div class="strip rev-strip">${list.map((r, i) => `<button class="rev-card" data-rev="${i}"><b>${esc(head(r))}</b><span>${esc(r.text)}</span><small>Read all</small></button>`).join("")}</div></section>`);
  pane.addEventListener("click", (e) => {
    const b = e.target.closest("[data-rev]");
    if (!b) return;
    const r = list[+b.dataset.rev];
    B.side(`<h3>${esc(head(r))}</h3><p class="dim">A TMDb member's review</p><div style="white-space:pre-wrap;line-height:1.55">${esc(r.text)}</div>`);
  });
};
