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
        : `<h4 style="margin:16px 0 8px">Ask for ${esc(t)}</h4>${seasons.length > 1 ? `<div class="acts" style="justify-content:flex-start;margin-bottom:8px"><button class="chip on" data-sall>All seasons</button><button class="chip" data-slast>${esc((seasons.filter((s) => !have.has(s.seasonNumber)).slice(-1)[0] || {}).name || "Newest season")} only</button></div>` : ""}${seasons.length ? `<h4 class="dim" style="margin:8px 0 8px">Or pick seasons</h4>${seasons.map((s) => `<button class="opt ${have.has(s.seasonNumber) ? "" : "on"}" data-season="${s.seasonNumber}" ${have.has(s.seasonNumber) ? "disabled" : ""}>${esc(s.name || "Season " + s.seasonNumber)}<small>${have.has(s.seasonNumber) ? "Asked or on Benflix" : (s.episodeCount || "") + " episodes"}</small></button>`).join("")}` : ""}
        ${status === 2 || status === 3 ? `<p>${B.rqPillHtml(status === 3 ? { k: "looking", w: "Still looking" } : { k: "asked", w: "Asked · waiting for Ben" })}</p>` : ""}
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
      return `Asked for ${t}. It shows in Your requests as Asked.`;
    });
    p.addEventListener("click", (e) => {
      const b = e.target.closest("[data-season]");
      if (b && !b.disabled) b.classList.toggle("on");
      // All seasons / the newest season only (Redesign 2's one Ask for it panel)
      const all = e.target.closest("[data-sall]"), last = e.target.closest("[data-slast]");
      if (!all && !last) return;
      const open = $$("[data-season]:not([disabled])", p);
      open.forEach((x, i) => x.classList.toggle("on", !!all || i === open.length - 1));
      $$("[data-sall],[data-slast]", p).forEach((x) => x.classList.toggle("on", x === (all || last)));
    });
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

// ---------- Redesign 2 (Ben 2026-11-10): one status helper, five pills, on every tab ----------
// Asked (grey), Getting it · 62% (blue), Still looking · day 3 (amber), Couldn't find it (red), Ready (green)
B.RQ_GROUPS = [["ready", "Ready"], ["getting", "Getting it"], ["looking", "Still looking"], ["asked", "Asked"], ["cant", "Couldn't find it"]];
B.rqPillHtml = (p) => `<span class="rqpill p-${esc(p.k)}">${esc(p.w)}</span>`;
B.seerrPill = (r) => {
  const ms = r.media ? r.media.status : 0;
  if (r.status === 3 || r.status === 4) return { k: "cant", w: "Couldn't find it" };
  if (ms === 5) return { k: "ready", w: "Ready to watch" };
  if (ms === 4) return { k: "getting", w: "Getting it · partly here" };
  if (r.status === 1) return { k: "asked", w: "Asked · waiting for Ben" };
  const days = Math.floor((Date.now() - Date.parse(r.updatedAt || r.createdAt || "")) / 86400000);
  if (r.status === 2 || ms === 3) return { k: "looking", w: days >= 0 ? `Still looking · day ${days + 1}` : "Still looking" };
  return { k: "asked", w: "Asked" };
};
B.musicPill = (r) => {
  const k = r.pill || (r.state === "ready" ? "ready" : r.bad ? "cant" : /download/i.test(r.words || "") ? "getting" : /look|search|find/i.test(r.words || "") ? "looking" : "asked");
  if (k === "ready") return { k, w: "Ready to play" };
  if (k === "cant") return { k, w: "Couldn't find it" };
  if (k === "getting") return { k, w: r.pct ? `Getting it · ${r.pct}%` : "Getting it" };
  if (k === "looking") return { k, w: r.days ? `Still looking · day ${r.days + 1}` : "Still looking" };
  return { k: "asked", w: r.waiting ? "Asked · waiting for Ben" : "Asked" };
};
B.bookPill = (r) => {
  const w = r.words || "";
  // Only words (a cover's "asked" line): read them
  const s = r.state || (/ready|on benflix/i.test(w) ? "ready" : /couldn|problem/i.test(w) ? "cant" : /download/i.test(w) ? "downloading"
    : /look|search/i.test(w) ? "searching" : /waiting/i.test(w) ? "waiting" : "");
  if (s === "ready") return { k: "ready", w: "Ready to read" };
  if (s === "cant" || s === "problem" || s === "no") return { k: "cant", w: "Couldn't find it" };
  if (s === "downloading") return { k: "getting", w: "Getting it" };
  if (s === "searching") return { k: "looking", w: "Still looking" };
  if (s === "waiting") return { k: "asked", w: "Asked · waiting for Ben" };
  return { k: "asked", w: "Asked" };
};
// One request line for any kind: { key, title, sub, cover, square, pill, acts }
B.rqLine = (o) => `<div class="item"><div class="rqthumb ${o.square ? "sq" : ""}" style="${o.cover ? `background-image:url('${esc(o.cover)}')` : ""}"></div>
  <div class="grow"><div class="t">${esc(o.title)}</div>${o.sub ? `<div class="s">${esc(o.sub)}</div>` : ""}<div style="margin-top:6px">${B.rqPillHtml(o.pill)}</div></div>
  <div class="acts">${o.acts || ""}</div></div>`;
// Your requests: grouped by the five pills, Ready first. Waiting for you: a flat list with Approve / Decline.
B.rqLines = (lines, grouped, empty) => {
  if (!lines.length) return `<p class="dim">${esc(empty)}</p>`;
  if (!grouped) return `<div class="list">${lines.map(B.rqLine).join("")}</div>`;
  return B.RQ_GROUPS.map(([k, title]) => {
    const g = lines.filter((l) => l.pill.k === k);
    return g.length ? `<h3 class="section-title">${esc(title)} <span class="dim">${g.length}</span></h3><div class="list">${g.map(B.rqLine).join("")}</div>` : "";
  }).join("");
};
// The side list every tab uses: Yours first (Your requests, Waiting for you in amber for Ben), then the lists
B.rqSide = (lists, picked, mine, waiting) => `<h4>Yours</h4><button class="${picked === "yours" ? "on" : ""}" data-rqpick="yours">Your requests<span class="n">${mine}</span></button>`
  + (waiting == null ? "" : `<button class="${picked === "waiting" ? "on" : ""}" data-rqpick="waiting">Waiting for you<span class="n ${waiting ? "amber" : ""}">${waiting}</span></button>`)
  + `<h4>Lists</h4>` + lists.map((l) => `<button class="${l.id === picked ? "on" : ""}" data-rqpick="${esc(l.id)}">${esc(l.title)}</button>`).join("");
// The end of a list: titles already on Benflix never show in the lists, but can be opened here
B.rqOwned = (n, open) => (n ? `<p style="margin-top:16px"><button class="chip" data-rqowned>${open ? "Hide the ones already on Benflix" : `${n} more ${n === 1 ? "is" : "are"} already on Benflix ›`}</button></p>` : "");
B.yearsOf = (r) => (r.releaseDate || r.firstAirDate || "").slice(0, 4);

const RQ_TABS = [["movies", "Movies"], ["shows", "Shows"], ["anime", "Anime"], ["music", "Music"], ["books", "Books"], ["yours", "Your requests"]];
const detailsAnime = (d) => ((d.genres || []).some((g) => g.id === 16) || (d.genreIds || []).includes(16)) && d.originalLanguage === "ja";
B.route(/^\/requests/, async (m, nav) => {
  if (B.me.kids) return B.go("#/home");
  // Requests opens on the tab you used last (the tab order stays Movies, Shows, Anime, Music, Books)
  let tab = B.q("tab");
  if (!tab) { try { tab = localStorage.getItem("bf-rq-tab"); } catch (e) { tab = null; } }
  if (!RQ_TABS.some(([k]) => k === tab) || (tab === "books" && !B.booksTab)) tab = "movies";
  try { localStorage.setItem("bf-rq-tab", tab); } catch (e) { /* private window */ }
  B.page().innerHTML = B.top("Requests", null, { solid: false }) + `<div class="wrap" style="padding-bottom:4px">
    <div class="row" style="margin-bottom:6px;align-items:center">${RQ_TABS.filter(([k]) => k !== "books" || B.booksTab).map(([k, l]) => `<a class="chip ${k === tab ? "on" : ""}" href="#/requests?tab=${k}">${l}</a>`).join("")}
      <input class="field" id="rq" style="max-width:420px;padding:9px 16px;font-size:16px;border-radius:99px" placeholder="Search to ask for anything" value="${esc(B.q("q") || "")}">
</div></div>
    <div class="rows" id="rows">${B.spinner()}</div>`;
  B.wireTop();
  if (tab === "music") return B.musicTab(nav);
  if (tab === "books") return B.booksTab(nav);
  if (tab === "yours") return B.yoursTab(nav);
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
    const res = (r.results || []).filter((x) => (x.mediaType === "movie" || x.mediaType === "tv") && !onBenflix(x));
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
    lists = { mine: await B.seerrNamed(mine, tab), waiting: await B.seerrNamed(waiting, tab) };
  };
  // Layout C on every tab: the list on the left picks what the poster wall on the right shows
  const GENRES = tab === "movies"
    ? [["Action", 28], ["Adventure", 12], ["Comedy", 35], ["Crime", 80], ["Documentary", 99], ["Drama", 18], ["Family", 10751], ["Fantasy", 14], ["History", 36], ["Horror", 27], ["Mystery", 9648], ["Romance", 10749], ["Sci-Fi", 878], ["Thriller", 53], ["War", 10752], ["Western", 37]]
    : [["Action and adventure", 10759], ["Comedy", 35], ["Crime", 80], ["Documentary", 99], ["Drama", 18], ["Family", 10751], ["Kids", 10762], ["Mystery", 9648], ["Reality", 10764], ["Sci-Fi and fantasy", 10765], ["War and politics", 10768], ["Western", 37]];
  let walls = [], picked = null, genre = null, ownedOpen = null;
  // Top 10: a number badge on the picture's bottom corner (the name and year stay under the poster)
  const ranked = (r, i) => B.askCard(r).replace(/<\/div>\s*<div class="cap">/, `<span class="rqrank">${i + 1}</span></div><div class="cap">`);
  const drawWall = () => {
    const side = $("#rqside"), wall = $("#rqwall");
    if (!side || !wall) return;
    const special = picked === "genres" || picked === "yours" || picked === "waiting";
    const cur = special ? null : walls.find((w) => w.id === picked) || walls[0];
    const sideLists = walls.map((w) => ({ id: w.id, title: w.title })).concat(tab === "anime" ? [] : [{ id: "genres", title: genre ? "Genre: " + genre[0] : "Genres" }]);
    side.innerHTML = B.rqSide(sideLists, special ? picked : cur && cur.id, lists.mine.length, B.me.admin ? lists.waiting.length : null);
    if (picked === "yours" || picked === "waiting") {
      const admin = picked === "waiting";
      const items = lists[admin ? "waiting" : "mine"];
      wall.innerHTML = `<div class="rqhead"><b>${admin ? "Waiting for you" : "Your requests"}</b><span>${items.length}</span></div>`
        + B.rqLines(items.map((r) => B.seerrLine(r, admin)), !admin, admin ? "Nobody is waiting on you right now." : "Nothing asked for yet. Pick a poster and press Ask for it.");
      return;
    }
    if (picked === "genres") {
      wall.innerHTML = `<div class="rqhead"><b>Genres</b><span>Pick one and every list shows only that genre</span></div><div class="rqgenres">${[["All", 0]].concat(GENRES).map(([l, id]) => `<button class="chip ${(genre ? genre[1] : 0) === id ? "on" : ""}" data-rqgenre="${id}">${esc(l)}</button>`).join("")}</div>`;
      return;
    }
    if (!cur) { wall.innerHTML = `<div class="empty">Nothing to show right now. Try again in a minute.</div>`; return; }
    const open = ownedOpen === cur.id;
    const shown = open ? cur.items.concat(cur.owned) : cur.items;
    wall.innerHTML = `<div class="rqline" id="rqline">${esc(B.rqAbout(shown[0]))}</div><div class="grid">${shown.map((r, i) => cur.ranked && i < 10 ? ranked(r, i) : B.askCard(r)).join("")}</div>${B.rqOwned(cur.owned.length, open)}`;
  };
  const drawRows = async () => {
    $("#rows").innerHTML = `<div class="grid-wrap rqsplit"><nav class="rqside" id="rqside"></nav><div class="grid-body" id="rqwall">${B.spinner()}</div></div>`;
    $("#rqside").onclick = (e) => {
      const b = e.target.closest("[data-rqpick]");
      if (b) { picked = b.dataset.rqpick; drawWall(); }
    };
    const wallEl = $("#rqwall");
    wallEl.onclick = (e) => {
      if (e.target.closest("[data-rqowned]")) {
        const cur = walls.find((w) => w.id === picked) || walls[0];
        ownedOpen = cur && ownedOpen !== cur.id ? cur.id : null;
        return drawWall();
      }
      const y = e.target.closest("[data-yes]"), n = e.target.closest("[data-no]");
      if (y || n) {
        const id = (y || n).dataset.yes || (y || n).dataset.no;
        return B.busy(y || n, async () => {
          await B.jf("POST", `/JellyfinEnhanced/arr/requests/${id}/${y ? "approve" : "decline"}`).catch(() => {});
          await loadLists();
          if (B.alive(nav)) drawWall();
          return y ? "Said yes. Tower starts looking now." : "Said no.";
        });
      }
      const g = e.target.closest("[data-rqgenre]");
      if (!g) return;
      const id = +g.dataset.rqgenre;
      genre = id ? GENRES.find((x) => x[1] === id) : null;
      picked = null;
      loadWalls();
    };
    // The line above the wall: what the poster under the mouse is
    wallEl.onmouseover = (e) => {
      const c = e.target.closest("[data-id]"), line = $("#rqline");
      if (c && line && B.items[c.dataset.id]) line.textContent = B.rqAbout(B.items[c.dataset.id]);
    };
    drawWall();
    await loadWalls();
  };
  const loadWalls = async () => {
    const wall = $("#rqwall");
    if (wall && picked !== "yours" && picked !== "waiting") wall.innerHTML = B.spinner();
    const kindPath = tab === "movies" ? "movies" : "tv";
    const pg = (path, list) => list.map((n) => seerr(`${path}?page=${n}`).catch(() => null));
    // Anime: TMDB's Animation genre is mostly kids' cartoons, so only Japanese titles count as anime
    const groups = tab === "anime"
      ? [pg("/discover/tv/genre/16", [1, 2, 3, 4]), pg("/discover/movies/genre/16", [1, 2, 3]), pg("/discover/tv/genre/16", [5, 6, 7])]
      : genre
        ? [pg(`/discover/${kindPath}/genre/${genre[1]}`, [1, 2, 3, 4])]
        : [pg(`/discover/${kindPath}`, [1, 2, 3, 4, 5, 6]), pg(`/discover/${kindPath}/upcoming`, [1, 2, 3])];
    const got = await Promise.all(groups.map((g) => Promise.all(g)));
    if (!B.alive(nav)) return;
    const mt = tab === "movies" ? "movie" : "tv";
    const kindOk = (x) => (tab === "anime" ? x.originalLanguage === "ja" : !isAnime(x));
    const seen = new Set();
    const uniq = (l) => l.filter((x) => !seen.has(x.mediaType + x.id) && seen.add(x.mediaType + x.id));
    const all = (i, m) => uniq((got[i] || []).flatMap((l) => ((l && l.results) || []).map((x) => Object.assign(x, { mediaType: x.mediaType || m || mt }))).filter(kindOk));
    // Titles already on Benflix are kept aside, for "N more are already on Benflix" at the end of each list
    const wallOf = (id, title, items, isRanked) => ({ id, title, ranked: !!isRanked, items: items.filter((x) => !onBenflix(x)), owned: items.filter(onBenflix) });
    if (tab === "anime") {
      const top = all(0, "tv");
      const notOwned = top.filter((x) => !onBenflix(x));
      walls = [wallOf("top", "Top 10 anime", notOwned.slice(0, 10), true), wallOf("popular", "Popular", top.filter((x) => !notOwned.slice(0, 10).includes(x)).concat(all(2, "tv"))),
        wallOf("movies", "Anime movies", all(1, "movie"))];
    } else if (genre) {
      walls = [wallOf("top", "Top 10 in " + genre[0], all(0), true)];
    } else {
      const trend = all(0);
      const top = trend.filter((x) => !onBenflix(x)).slice(0, 10);
      walls = [wallOf("top", "Top 10 this week", top, true), wallOf("trending", "Trending", trend.filter((x) => !top.includes(x))),
        wallOf("soon", "Coming soon", all(1))];
    }
    walls = walls.filter((w) => w.items.length || w.owned.length);
    drawWall();
  };
  let t;
  $("#rq").oninput = (e) => { clearTimeout(t); t = setTimeout(() => search(e.target.value), 350); };
  if (B.q("q")) search(B.q("q")); else drawRows();
  loadLists().then(() => { if (B.alive(nav)) drawWall(); });
});
// The line above the wall: name · year · Coming date · score
B.rqAbout = (r) => {
  if (!r) return " ";
  const d = r.releaseDate || r.firstAirDate || "";
  const soon = d && Date.parse(d) > Date.now() ? "Coming " + new Date(d).toLocaleDateString(undefined, { month: "long", day: "numeric" }) : "";
  return [r.title || r.name, d.slice(0, 4), soon, r.voteAverage ? "★ " + (+r.voteAverage).toFixed(1) : ""].filter(Boolean).join(" · ");
};
// Seerr requests with their real names (and which tab they belong on)
B.seerrNamed = async (l, tab) => {
  const items = ((l && l.results) || []).filter((r) => (tab === "movies" ? r.type === "movie" : tab ? r.type === "tv" : true));
  await Promise.all(items.map(async (r) => {
    const md = r.media || (r.media = {});
    if (!md.tmdbId) return;
    const d = await B.cached(`seerr-${r.type}-${md.tmdbId}`, 3600000, () => seerr(`/${r.type}/${md.tmdbId}`)).catch(() => null);
    if (d) { md.title = d.title || d.name || md.title; md.anime = detailsAnime(d); md.poster = d.posterPath; }
  }));
  if (!tab) return items;
  return items.filter((r) => (tab === "movies" ? !r.media.anime : tab === "shows" ? !r.media.anime : r.media.anime));
};
B.seerrLine = (r, answer) => {
  const md = r.media || {};
  const who = (r.requestedBy && (r.requestedBy.displayName || r.requestedBy.jellyfinUsername)) || "Someone";
  const seasons = (r.seasons || []).map((s) => s.seasonNumber);
  const kind = r.type === "movie" ? "Movie" : seasons.length === 1 ? "Season " + seasons[0] : seasons.length ? seasons.length + " seasons" : "Show";
  return {
    title: md.title || md.name || "Name not found", sub: [B.me.admin ? who : "", kind, B.when(r.createdAt)].filter(Boolean).join(" · "),
    cover: md.poster ? tmdbImg(md.poster, "w185") : "", pill: B.seerrPill(r),
    acts: answer ? `<button class="btn small white" data-yes="${r.id}">Approve</button><button class="btn small" data-no="${r.id}">Decline</button>` : `<button class="btn small" data-ask="${esc(r.type)}:${esc(md.tmdbId)}">Open</button>`,
  };
};

// ---------- Your requests: every kind (movies, shows, songs, albums, books), grouped by status ----------
B.yoursTab = async (nav) => {
  const rows = $("#rows");
  $("#rq").style.display = "none";
  const draw = async () => {
    const me = await B.seerrMe();
    const [seerrMine, seerrWaiting, mus, bks] = await Promise.all([
      me && me.active ? seerr(`/request?take=60&skip=0&sort=added${B.me.admin ? "" : `&requestedBy=${me.jellyseerrUserId}`}`).catch(() => null) : null,
      me && me.active && B.me.admin ? seerr(`/request?take=40&skip=0&sort=added&filter=pending`).catch(() => null) : null,
      B.call("GET", "api/music/status").catch(() => null),
      B.call("GET", "api/books/requests").catch(() => null),
    ]);
    const [mine, waiting] = await Promise.all([B.seerrNamed(seerrMine), B.seerrNamed(seerrWaiting)]);
    if (!B.alive(nav)) return;
    const waitIds = new Set(waiting.map((r) => r.id));
    const lines = mine.filter((r) => !waitIds.has(r.id)).map((r) => B.seerrLine(r, false));
    const wait = waiting.map((r) => B.seerrLine(r, true));
    ((mus && mus.requests) || []).forEach((r) => {
      const o = { title: r.title, sub: [B.me.admin ? r.who : "", r.artist, r.kind].filter(Boolean).join(" · "), cover: r.cover, square: true, pill: B.musicPill(r) };
      if (B.me.admin && r.waiting) wait.push(Object.assign(o, { acts: `<button class="btn small white" data-myes="${esc(r.id)}">Approve</button><button class="btn small" data-mno="${esc(r.id)}">Decline</button>` }));
      else lines.push(Object.assign(o, { acts: r.can_retry && B.me.admin ? `<button class="btn small" data-mretry="${esc(r.id)}">Try again</button>` : "" }));
    });
    if (bks) {
      const bw = new Set((bks.waiting || []).map((r) => r.id));
      const all = (B.me.admin ? [].concat(bks.mine || [], bks.others || [], bks.mangaToAdd || []) : bks.mine || []).filter((r, i, a) => a.findIndex((x) => x.id === r.id) === i && !bw.has(r.id));
      const line = (r) => ({ title: r.name, sub: [B.me.admin ? r.who : "", r.author, r.state === "manga" ? "manga" : (r.types || []).join(" and ")].filter(Boolean).join(" · "), cover: r.cover, pill: B.bookPill(r) });
      all.forEach((r) => lines.push(Object.assign(line(r), { acts: r.state === "ready" && r.title ? `<a class="btn small" href="#/book/${enc(r.title)}">Open</a>` : "" })));
      if (B.me.admin) (bks.waiting || []).forEach((r) => wait.push(Object.assign(line(r), { acts: `<button class="btn small white" data-byes="${esc(r.id)}">Approve</button><button class="btn small" data-bno="${esc(r.id)}">Decline</button>` })));
    }
    rows.innerHTML = `<div class="wrap">${B.me.admin && wait.length ? `<h3 class="section-title">Waiting for you <span class="rqpill p-looking">${wait.length}</span></h3>${B.rqLines(wait, false, "")}` : ""}
      ${B.rqLines(lines, true, "Nothing asked for yet. Pick a poster on any tab and ask for it.")}</div>`;
  };
  rows.onclick = (e) => {
    const b = e.target.closest("[data-yes],[data-no],[data-myes],[data-mno],[data-byes],[data-bno],[data-mretry]");
    if (!b) return;
    const d = b.dataset;
    B.busy(b, async () => {
      if (d.yes || d.no) await B.jf("POST", `/JellyfinEnhanced/arr/requests/${d.yes || d.no}/${d.yes ? "approve" : "decline"}`).catch(() => {});
      if (d.myes || d.mno) await B.call("POST", `api/music/request/${d.myes || d.mno}/${d.myes ? "approve" : "decline"}`);
      if (d.byes || d.bno) await B.call("POST", "api/books/answer", { id: d.byes || d.bno, yes: !!d.byes });
      if (d.mretry) await B.call("POST", `api/music/request/${d.mretry}/retry`);
      await draw();
      return d.yes || d.myes || d.byes ? "Said yes. Tower starts looking now." : d.mretry ? "Looking again." : "Said no.";
    });
  };
  await draw();
  B.every(15000, () => draw().catch(() => {}));
};

// ---------- Music (only Ben): ask for songs and albums, playlists, the Music Drop folder, tidy-up ----------
// Talks to the helper's /api/music (benflix_music.py), which hands the downloading to Lidarr on Tower.
const music = (method, path, body) => B.call(method, "api/music" + path, body);
const songLine = (s, n) => `<div class="item">${n ? `<b class="dim" style="min-width:28px">${n}</b>` : ""}<div style="width:48px;height:48px;border-radius:6px;flex:none;background:var(--pg3) center/cover url('${esc(s.cover || "")}')"></div>
  <div class="grow"><div class="t">${esc(s.title)}</div><div class="s">${esc(s.artist)}${s.album ? " · " + esc(s.album) : ""}${s.seconds ? " · " + Math.floor(s.seconds / 60) + ":" + String(s.seconds % 60).padStart(2, "0") : ""}</div></div>
  <div class="acts">${B.previewBtn(s.id)}${s.asked || s.have ? B.rqPillHtml(s.have ? { k: "ready", w: "Ready to play" } : B.musicPill({ words: s.asked })) : `<button class="btn small white" data-msong="${s.id}" data-malbum="${s.album_id}" data-mname="${esc(s.title)}" data-maname="${esc(s.album)}">Ask for it</button>`}</div></div>`;
const albumCard = (a) => `<button class="poster" data-mopen="album:${a.id}" title="${esc(a.title)}"><div class="im" style="aspect-ratio:1;${a.cover ? `background-image:url('${esc(a.cover)}')` : ""}"></div>
  <div class="cap">${esc(a.title)}<small>${esc(a.asked || [a.year, a.type === "single" ? "Single" : a.type === "ep" ? "EP" : ""].filter(Boolean).join(" · "))}</small></div></button>`;
const genreCard = (g) => `<button class="poster" data-mgenre="${g.id}" title="${esc(g.name)}"><div class="im" style="aspect-ratio:1;${g.picture ? `background-image:url('${esc(g.picture)}')` : ""}"></div><div class="cap">${esc(g.name)}</div></button>`;
const songCard = (s, n) => `<div class="pvcard">${B.previewBtn(s.id, "on-card")}<button class="poster" ${s.asked || s.have ? "" : `data-msong="${s.id}" data-malbum="${s.album_id}" data-mname="${esc(s.title)}" data-maname="${esc(s.album)}"`} title="${esc(s.title)}"><div class="im" style="aspect-ratio:1;${s.cover ? `background-image:url('${esc(s.cover)}')` : ""}"></div>
  <div class="cap">${n ? n + ". " : ""}${esc(s.title)}<small>${esc(s.asked ? B.musicPill({ words: s.asked }).w : s.have ? "Ready to play" : s.artist)}</small></div></button></div>`;
const artistCard = (a) => `<button class="poster" data-mopen="artist:${a.id}" title="${esc(a.name)}"><div class="im" style="aspect-ratio:1;border-radius:50%;${a.picture ? `background-image:url('${esc(a.picture)}')` : ""}"></div><div class="cap">${esc(a.name)}</div></button>`;

// Redesign 2: Music sits on the same side list and wall as the other tabs. Grown-up family profiles can ask
// too; their song and album asks wait for Ben's Approve before anything downloads.
B.musicTab = async (nav) => {
  const admin = B.me.admin;
  $("#rq").placeholder = "Search any song, album or artist";
  const rows = $("#rows");
  rows.innerHTML = `<div id="mfound"></div><div class="grid-wrap rqsplit" id="msplit"><nav class="rqside" id="mside"></nav><div class="grid-body" id="mwall">${B.spinner()}</div></div>`;
  let searching = false, tidyFolder = null, disc = null, st = null, picked = null, ownedOpen = null;
  const lists = () => {
    const d = disc || {};
    const because = [];
    const seen = new Set();
    (d.because || []).forEach((b) => (b.songs || []).forEach((x) => { if (!seen.has(x.id)) { seen.add(x.id); because.push(x); } }));
    return [
      (d.songs || []).length && { id: "top", title: "Top 10 songs", songs: d.songs.slice(0, 10), ranked: true },
      (d.songs || []).length > 10 && { id: "trending", title: "Trending", songs: d.songs.slice(10) },
      because.length && { id: "because", title: "Because you have", songs: because },
      (d.new || []).length && { id: "new", title: "New this week", albums: d.new },
      (d.genres || []).length && { id: "genres", title: "Genres", genres: d.genres },
      admin && { id: "playlist", title: "Import a playlist" },
    ].filter(Boolean);
  };
  const reqLine = (r, answer) => ({
    title: r.title, sub: [admin ? r.who : "", r.artist, r.kind].filter(Boolean).join(" · "), cover: r.cover, square: true, pill: B.musicPill(r),
    acts: answer ? `<button class="btn small white" data-myes="${esc(r.id)}">Approve</button><button class="btn small" data-mno="${esc(r.id)}">Decline</button>`
      : `${r.can_retry && admin ? `<button class="btn small white" data-mretry="${r.id}">Try again</button>` : ""}<button class="btn small" data-mremove="${r.id}">${r.state === "ready" ? "Done" : "Remove"}</button>`,
  });
  const draw = () => {
    const side = $("#mside"), wall = $("#mwall");
    if (!side || !wall) return;
    const ls = lists();
    const reqs = (st && st.requests) || [];
    const waiting = admin ? reqs.filter((r) => r.waiting) : [];
    const mine = reqs.filter((r) => !admin || !r.waiting);
    const cur = picked === "yours" || picked === "waiting" ? null : ls.find((l) => l.id === picked) || ls[0];
    side.innerHTML = B.rqSide(ls, cur ? cur.id : picked, mine.length, admin ? waiting.length : null);
    const notes = ((admin && st && st.problems) || []).map((p) => `<div class="note yellow">${esc(p)}</div>`).join("");
    if (picked === "yours" || picked === "waiting") {
      const w = picked === "waiting";
      wall.innerHTML = notes + `<div class="rqhead"><b>${w ? "Waiting for you" : "Your requests"}</b><span>${w ? waiting.length : mine.length}</span></div>`
        + (w || admin ? "" : `<p class="dim">Ben says yes before anything downloads.</p>`)
        + B.rqLines((w ? waiting : mine).map((r) => reqLine(r, w)), !w, w ? "Nobody is waiting on you right now." : "Nothing asked for yet. Pick a song or album and ask for it.");
      return;
    }
    if (!disc) { wall.innerHTML = notes + B.spinner(); return; }
    if (!cur) { wall.innerHTML = notes + `<div class="empty">Nothing to show right now. Try again in a minute.</div>`; return; }
    if (cur.id === "playlist") { wall.innerHTML = notes + `<div id="mstatus">${B.spinner()}</div>`; drawTools(); return; }
    let cards = "", owned = 0;
    if (cur.songs) {
      owned = cur.songs.filter((x) => x.have).length;
      const shown = ownedOpen === cur.id ? cur.songs : cur.songs.filter((x) => !x.have);
      cards = shown.map((x, i) => {
        const c = songCard(x);
        return cur.ranked ? c.replace(/<\/div>\s*<div class="cap">/, `<span class="rqrank">${i + 1}</span></div><div class="cap">`) : c;
      }).join("");
    } else if (cur.albums) cards = cur.albums.map(albumCard).join("");
    else if (cur.genres) cards = cur.genres.map(genreCard).join("");
    wall.innerHTML = notes + `<div class="rqline" id="mline"> </div><div class="grid sq">${cards}</div>${B.rqOwned(owned, ownedOpen === cur.id)}`;
  };
  const drawDiscover = async (again) => {
    if (!disc || again) {
      try { disc = await music("GET", "/discover"); } catch (e) { if (B.alive(nav) && !disc) $("#mwall").innerHTML = `<p class="dim">Couldn't load what's popular right now. ${esc(e.message)}</p>`; return; }
    }
    if (B.alive(nav) && !searching) draw();
  };
  const loadStatus = async () => {
    try { st = await music("GET", "/status"); } catch (e) { if (B.alive(nav) && !st) B.toast(e.message); return; }
    if (B.alive(nav) && !searching && picked !== "playlist") draw();
    if (picked === "playlist") drawTools();
  };
  // Ben's music tools: playlists, the Music Drop folder and the tidy-up
  const drawTools = () => {
    const box = $("#mstatus");
    if (!box || !st) return;
    const t = st.tidy || { folders: [], lines: [], undo_files: [] };
    if (!t.folders.includes(tidyFolder)) tidyFolder = t.folders[0] || null;
    box.innerHTML = `<h3 class="section-title">Playlists</h3>
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
    $("#msplit").style.display = searching ? "none" : "";
    if (!searching) { box.innerHTML = ""; draw(); return; }
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
          <div class="acts" style="justify-content:flex-start;margin:12px 0">${d.album.asked ? B.rqPillHtml(B.musicPill({ words: d.album.asked })) : `<button class="btn white" data-malbumonly="${d.album.id}" data-maname="${esc(d.album.title)}">${admin ? "Ask for the whole album" : "Ask Ben for the whole album"}</button>`}</div>
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
    if (q.trim().length >= 2) search(q);
    loadStatus();
    drawDiscover(true);
    return admin ? `Asked for ${name}. It shows in Your requests as Asked.` : `Asked Ben for ${name}. He says yes before it downloads.`;
  };
  // One click handler for the whole page and its side panels
  const onClick = (e) => {
    if (!B.alive(nav)) return document.removeEventListener("click", onClick);
    const pick = e.target.closest("#mside [data-rqpick]");
    if (pick) { picked = pick.dataset.rqpick; return draw(); }
    if (e.target.closest("#mwall [data-rqowned]")) {
      const cur = lists().find((l) => l.id === picked) || lists()[0];
      ownedOpen = cur && ownedOpen !== cur.id ? cur.id : null;
      return draw();
    }
    const b = e.target.closest("[data-msong],[data-malbumonly],[data-mopen],[data-mgenre],[data-mretry],[data-mremove],[data-myes],[data-mno],[data-mplremove],[data-mfolder],[data-mtidy],#mplaylist");
    if (!b) return;
    const d = b.dataset;
    if (d.msong) {
      // The one Ask for it panel: hear 30 seconds, then ask for the song or the whole album
      const s = B.side(`<h3>${esc(d.mname)}</h3><p class="dim">${esc(d.maname)}</p><h4 style="margin:14px 0 8px">Ask for ${esc(d.mname)}</h4>
        ${admin ? "" : `<p class="dim">Ben says yes before it downloads.</p>`}
        <div class="acts" style="justify-content:flex-start;margin:4px 0 12px">${B.previewBtn(d.msong)}<span class="dim">Hear 30 seconds</span></div>
        <button class="opt" data-pick="song">${admin ? "Ask for this song" : "Ask Ben for this song"}</button><button class="opt" data-pick="album">${admin ? "Ask for the whole album" : "Ask Ben for the whole album"}</button>`);
      s.addEventListener("click", (ev) => {
        const one = ev.target.closest("[data-pick]");
        if (one) B.busy(one, () => (one.dataset.pick === "song" ? ask("song", d.msong, d.mname) : ask("album", d.malbum, d.maname)));
      });
    } else if (d.malbumonly) B.busy(b, () => ask("album", d.malbumonly, d.maname));
    else if (d.mopen) { const [k, id] = d.mopen.split(":"); openPage(k, id); }
    else if (d.mgenre) openGenre(d.mgenre);
    else if (d.mretry) B.busy(b, async () => { await music("POST", `/request/${d.mretry}/retry`); loadStatus(); return "Looking again."; });
    else if (d.mremove) B.busy(b, async () => { await music("POST", `/request/${d.mremove}/remove`); loadStatus(); });
    else if (d.myes || d.mno) B.busy(b, async () => { await music("POST", `/request/${d.myes || d.mno}/${d.myes ? "approve" : "decline"}`); loadStatus(); return d.myes ? "Said yes. Tower starts looking now." : "Said no."; });
    else if (d.mplremove) B.busy(b, async () => { await music("POST", `/playlist/${d.mplremove}/remove`); loadStatus(); });
    else if (d.mfolder) { tidyFolder = d.mfolder; drawTools(); }
    else if (d.mtidy) {
      B.busy(b, async () => {
        const mode = d.mtidy;
        if (mode === "apply" && !(await B.ask(`Tidy ${tidyFolder}?`, "Everything is sorted into Music/Artist/Album. Songs you have twice go to Music Leftovers, and Undo puts it all back.", "Tidy it", "Not now"))) return;
        if (mode === "undo" && !(await B.ask("Undo the last tidy-up?", "The files go back where they were.", "Undo", "Not now"))) return;
        await music("POST", "/tidy", { mode, folder: tidyFolder || "", undo: mode === "undo" ? rows.dataset.undo : "" });
        loadStatus();
      });
    } else if (b.id === "mplaylist") {
      B.askText("Bring in a playlist", "Paste the playlist's link (Share > Copy link), or songs one per line like: Queen - Bohemian Rhapsody", "", { long: true, ok: "Bring it in" }).then((link) => {
        if (!link || !link.trim()) return;
        // Ben: someone else's playlist can bring in just the songs, without a Benflix playlist
        B.ask("Make a Benflix playlist too?", "Songs you don't have yet are asked for either way. Pick Just the songs for someone else's playlist.", "Songs + playlist", "Just the songs").then((make) => {
          B.busy(null, async () => { const p = await music("POST", "/playlist", { link: link.trim(), playlist: !!make }); loadStatus(); return `Bringing in ${p.name}.`; });
        });
      });
    }
  };
  document.addEventListener("click", onClick);
  B.onLeave(() => document.removeEventListener("click", onClick));
  // The line above the wall: the song or album under the mouse
  const onOver = (e) => {
    const c = e.target.closest("#mwall .poster"), line = $("#mline");
    if (c && line) line.textContent = c.getAttribute("title") || " ";
  };
  document.addEventListener("mouseover", onOver);
  B.onLeave(() => document.removeEventListener("mouseover", onOver));
  let t;
  $("#rq").oninput = (e) => { clearTimeout(t); t = setTimeout(() => search(e.target.value), 400); };
  B.every(10000, () => { if (!searching) loadStatus(); });
  drawDiscover();
  loadStatus();
  if (B.q("q")) search(B.q("q"));
};
