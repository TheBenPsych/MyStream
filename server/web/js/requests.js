/* Benflix web: Requests (asking for movies and shows that aren't on Benflix yet). Talks to Seerr
   through the Jellyfin Enhanced plugin on the server, the same way the TV and phone app does. */
"use strict";

const seerr = (path, method, body) => B.jf(method || "GET", "/JellyfinEnhanced/jellyseerr" + path, body);
B.seerr = seerr;
const tmdbImg = (p, w) => (p ? `https://image.tmdb.org/t/p/${w || "w342"}${p}` : "");
B.tmdbImg = tmdbImg;
B.seerrMe = () => B.cached("seerr-me", 600000, () => seerr("/user-status").catch(() => null));
// Benflix (Ben, 2026-11-16): what's really on Benflix, straight from Jellyfin ("movie:123" / "tv:456" -> Jellyfin id),
// because Seerr's own "available" often runs behind. A show that's here but missing aired episodes gets
// r._missing = true and shows again, tagged "Missing episodes".
let libTmdb = new Map();
B.libTmdb = () => B.cached("lib-tmdb", 600000, async () => {
  const r = await B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=Movie,Series&Recursive=true&Fields=ProviderIds&EnableImages=false&EnableUserData=false&EnableTotalRecordCount=false`).catch(() => ({}));
  const m = new Map();
  (r.Items || []).forEach((x) => { const t = (x.ProviderIds || {}).Tmdb; if (t) m.set((x.Type === "Movie" ? "movie:" : "tv:") + t, x.Id); });
  // Anime movies Tower moved into their show's Specials are still here (as an episode now)
  const moved = await B.call("GET", "api/specials/moved").catch(() => null);
  Object.entries((moved && moved.items) || {}).forEach(([t, id]) => { if (!m.has("movie:" + t)) m.set("movie:" + t, id); });
  libTmdb = m;
  return m;
}).then((m) => { libTmdb = m; return m; });
const onBenflix = (r) => !r._missing && ((r.mediaInfo && (r.mediaInfo.status === 5 || r.mediaInfo.status === 4)) || libTmdb.has(r.mediaType + ":" + r.id));
B.onBenflix = onBenflix;
// Aired episodes per season from Seerr's /tv/{id} (no specials, nothing unaired), like the app's ShowArrival
const airedEpisodes = (tv) => {
  const last = tv.lastEpisodeToAir || null, ls = last && last.seasonNumber >= 1 ? last.seasonNumber : null, today = new Date().toISOString().slice(0, 10);
  const out = {};
  (tv.seasons || []).forEach((s) => {
    const n = s.seasonNumber;
    if (!(n >= 1)) return;
    const listed = s.episodeCount || 0;
    const c = ls != null ? (n < ls ? listed : n === ls ? (last.episodeNumber || listed) : 0) : (s.airDate && s.airDate.slice(0, 10) <= today ? listed : 0);
    if (c > 0) out[n] = c;
  });
  return out;
};
// true = the whole show is here, false = here but missing aired episodes (cached per show for 10 minutes)
B.showComplete = (tmdbId) => B.cached("show-here-" + tmdbId, 600000, async () => {
  const tv = await seerr("/tv/" + tmdbId).catch(() => null);
  if (!tv) return true;
  const mi = tv.mediaInfo || {};
  const jf = libTmdb.get("tv:" + tmdbId) || mi.jellyfinMediaId;
  const aired = airedEpisodes(tv);
  if (!Object.keys(aired).length) return true;
  if (!jf) return mi.status === 5;
  const r = await B.get(`/Items?userId=${B.uid()}&ParentId=${jf}&IncludeItemTypes=Episode&Recursive=true&IsMissing=false&EnableImages=false&EnableUserData=false`).catch(() => null);
  if (!r) return mi.status === 5;
  const have = {};
  (r.Items || []).forEach((e) => {
    if (e.LocationType === "Virtual") return;
    const sn = e.ParentIndexNumber == null ? 1 : e.ParentIndexNumber;
    if (sn < 1 || e.IndexNumber == null) return;
    const set = have[sn] || (have[sn] = new Set());
    for (let i = e.IndexNumber; i <= Math.max(e.IndexNumber, e.IndexNumberEnd || e.IndexNumber); i++) set.add(i);
  });
  const seasonOk = Object.entries(aired).every(([n, c]) => ((have[n] && have[n].size) || 0) >= c);
  if (seasonOk) return true;
  // Anime filed with different season splits: count every episode together
  const total = (o) => Object.values(o).reduce((a, b) => a + (typeof b === "number" ? b : b.size), 0);
  return total(have) >= total(aired);
});
const asked = (r) => r.mediaInfo && (r.mediaInfo.status === 2 || r.mediaInfo.status === 3 || (r.mediaInfo.requests || []).length);
const isAnime = (r) => (r.genreIds || []).includes(16) && (r.originalLanguage === "ja" || r.mediaType === "tv");
// Glowfin new look (Ben 2026-11-22): where a title is at, as a coloured tag at the top and a small dot on its cover.
// k: nyet (not on Glowfin yet), wait (asked, waiting for Ben), way (on its way), out (not out yet), bad (missing
// episodes), here (on Glowfin). Nobody asked = no dot.
B.rqStatus = (r) => {
  const st = r.mediaInfo ? r.mediaInfo.status : 1;
  const d = r.releaseDate || r.firstAirDate || "", today = new Date().toISOString().slice(0, 10);
  const out = d && d.slice(0, 10) > today ? new Date(d.slice(0, 10) + "T12:00:00").toLocaleDateString(undefined, { month: "short", day: "numeric", year: d.slice(0, 4) === today.slice(0, 4) ? undefined : "numeric" }) : "";
  if (r._missing) return { k: "bad", w: "Missing episodes", dot: true };
  if (r._newSeason && st !== 2 && st !== 3) return { k: "nyet", w: r._newSeason, dot: false };
  if (st === 5 || (st !== 2 && st !== 3 && libTmdb.has(r.mediaType + ":" + r.id))) return { k: "here", w: "On Glowfin", dot: true };
  if (st === 4) return { k: "bad", w: r.mediaType === "tv" ? "Missing episodes" : "Partly on Glowfin", dot: true };
  if (st === 2 || (st === 1 && (r.mediaInfo && (r.mediaInfo.requests || []).length))) return { k: "wait", w: B.me && B.me.admin ? "Waiting for you" : "Waiting for Ben", dot: true };
  if (st === 3) return out ? { k: "out", w: `Out ${out} · Glowfin gets it then`, dot: true } : { k: "way", w: "On its way", dot: true };
  if (out) return { k: "out", w: "Out " + out, dot: false };
  return { k: "nyet", w: "Not on Glowfin yet", dot: false };
};
B.askCard = (r) => {
  const t = r.title || r.name || "";
  const year = (r.releaseDate || r.firstAirDate || "").slice(0, 4);
  const img = tmdbImg(r.posterPath);
  B.items["s" + r.mediaType + r.id] = r;
  const s = B.rqStatus(r);
  // Glowfin new look (browser): a small Ask button on the cover your mouse is on, so you don't have to open it first
  const canAsk = !s.dot && (s.k === "nyet" || s.k === "out") && (r.mediaType === "movie" || r.mediaType === "tv");
  return `<button class="poster askcard" data-ask="${esc(r.mediaType)}:${esc(r.id)}" data-id="s${esc(r.mediaType + r.id)}" title="${esc(t)}">
    <div class="im" ${img ? `style="background-image:url('${img}')"` : ""}>${img ? "" : `<div class="noart"><span class="nt">${esc(t)}</span></div>`}${s.dot ? `<span class="rqdot d-${s.k}" title="${esc(s.w)}"></span>` : ""}${canAsk ? `<span class="rqqask" data-qask role="button" aria-label="Ask for ${esc(t)}">${I("plus")} Ask</span>` : ""}</div>
    <div class="cap">${esc(t)}<small${r._part ? ' class="rqpart"' : ""}>${esc(r._part || year)}</small></div></button>`;
};
// Glowfin (Ben, 2026-11-21) "Finish your sets": movies missing from film series you already own part of.
// Each one says which part it is and which parts are here ("Part 3 · you have 1 and 2").
const haveWords = (nums) => {
  const runs = nums.every((n, i) => i === 0 || n === nums[i - 1] + 1);
  if (nums.length === 1) return String(nums[0]);
  if (runs && nums.length > 2) return `${nums[0]} to ${nums[nums.length - 1]}`;
  return nums.slice(0, -1).join(", ") + " and " + nums[nums.length - 1];
};
// Glowfin new look (Ben approved 2026-11-22): the Shows tab's first row. Shows you've been watching with a newer
// season out than Glowfin has, that nobody asked for yet ("Season 4 is out").
B.newSeasons = () => B.cached("new-seasons", 1800000, async () => {
  const eps = await B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=Episode&IsPlayed=true&Recursive=true&SortBy=DatePlayed&SortOrder=Descending&Limit=300&EnableImages=false&EnableUserData=false&EnableTotalRecordCount=false`).catch(() => ({}));
  const ids = [...new Set((eps.Items || []).map((x) => x.SeriesId).filter(Boolean))].slice(0, 20);
  if (!ids.length) return [];
  const shows = ((await B.get(`/Items?userId=${B.uid()}&Ids=${ids.join(",")}&Fields=ProviderIds&EnableImages=false`).catch(() => ({}))).Items) || [];
  const today = new Date().toISOString().slice(0, 10), out = [];
  let i = 0;
  const next = async () => {
    while (i < shows.length) {
      const sh = shows[i++], tmdb = sh.ProviderIds && sh.ProviderIds.Tmdb;
      if (!tmdb) continue;
      const [d, here] = await Promise.all([
        seerr(`/tv/${tmdb}`).catch(() => null),
        B.get(`/Shows/${sh.Id}/Seasons?userId=${B.uid()}&EnableImages=false`).catch(() => null),
      ]);
      if (!d || !here) continue;
      const last = Math.max(0, ...((here.Items || []).map((x) => x.IndexNumber || 0)));
      if (!last) continue;
      const st = new Map(((d.mediaInfo && d.mediaInfo.seasons) || []).map((x) => [x.seasonNumber, x.status]));
      const newer = (d.seasons || []).filter((x) => x.seasonNumber > last && x.airDate && x.airDate.slice(0, 10) <= today && (st.get(x.seasonNumber) || 1) <= 1).map((x) => x.seasonNumber).sort((a, b) => a - b);
      if (!newer.length) continue;
      out.push(Object.assign(d, { mediaType: "tv", _newSeason: newer.length === 1 ? `Season ${newer[0]} is out` : `Seasons ${newer[0]} to ${newer[newer.length - 1]} are out` }));
    }
  };
  await Promise.all([next(), next(), next(), next()]);
  return out;
});
B.finishSets = () => B.cached("finish-sets", 1800000, async () => {
  const [sets, lib] = await Promise.all([
    B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=BoxSet&Recursive=true&Fields=ProviderIds&EnableImages=false&EnableUserData=false&EnableTotalRecordCount=false&Limit=200`).catch(() => ({})),
    B.libTmdb().catch(() => new Map()),
  ]);
  const ids = [...new Set((sets.Items || []).map((x) => (x.ProviderIds || {}).Tmdb || (x.ProviderIds || {}).TmdbCollection).filter(Boolean))].slice(0, 40);
  const today = new Date().toISOString().slice(0, 10), out = [];
  let i = 0;
  const next = async () => {
    while (i < ids.length) {
      const set = await seerr("/collection/" + ids[i++]).catch(() => null);
      const parts = ((set && set.parts) || []).filter((p) => p && p.id && p.releaseDate && p.releaseDate.slice(0, 10) <= today).sort((a, b) => (a.releaseDate < b.releaseDate ? -1 : 1));
      const here = (p) => (p.mediaInfo && p.mediaInfo.status >= 4) || lib.has("movie:" + p.id);
      const have = parts.map((p, n) => (here(p) ? n + 1 : 0)).filter(Boolean);
      if (!have.length) continue;
      parts.forEach((p, n) => {
        if (here(p) || (p.mediaInfo && p.mediaInfo.status >= 2)) return;
        out.push(Object.assign({}, p, { mediaType: "movie", _part: `Part ${n + 1} · you have ${haveWords(have)}` }));
      });
    }
  };
  await Promise.all([next(), next(), next(), next()]);
  return out.slice(0, 40);
});

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
      ${d.voteAverage ? `<div class="scores"><span data-s="user"><b>${d.voteAverage.toFixed(1)}</b>TMDB</span></div>` : ""}
      <p>${esc(d.overview || "")}</p>
      ${status === 5 && jfId ? `<a class="btn white" href="#/${type === "tv" ? "show" : "movie"}/${jfId}">${I("play")} It's on Glowfin</a>`
        : `<h4 style="margin:16px 0 8px">Ask for ${esc(t)}</h4>${seasons.length > 1 ? `<div class="acts" style="justify-content:flex-start;margin-bottom:8px"><button class="chip on" data-sall>All seasons</button><button class="chip" data-severy title="All of them now, and each new season when it comes out">Every new season from now on</button><button class="chip" data-slast>${esc((seasons.filter((s) => !have.has(s.seasonNumber)).slice(-1)[0] || {}).name || "Newest season")} only</button></div>` : ""}${seasons.length ? `<h4 class="dim" style="margin:8px 0 8px">Or pick seasons</h4>${seasons.map((s) => `<button class="opt ${have.has(s.seasonNumber) ? "" : "on"}" data-season="${s.seasonNumber}" ${have.has(s.seasonNumber) ? "disabled" : ""}>${esc(s.name || "Season " + s.seasonNumber)}<small>${have.has(s.seasonNumber) ? "Asked or on Glowfin" : (s.episodeCount || "") + " episodes"}</small></button>`).join("")}` : ""}
        ${status === 2 || status === 3 ? `<p>${B.rqPillHtml(status === 3 ? { k: "looking", w: "Still looking" } : { k: "asked", w: "Asked · waiting for Ben" })}</p>` : ""}
        <div class="acts" style="margin-top:16px"><button class="btn white" id="askGo">${I("plus")} Ask for it</button>${B.reqForHtml()}</div>`}`;
    B.reqForFill(p);
    const go = $("#askGo", p);
    if (go) go.onclick = () => B.busy(go, async () => {
      const body = { mediaType: type, mediaId: +id };
      if (type === "tv") {
        const picked = $$("[data-season].on", p).map((b) => +b.dataset.season);
        if (!picked.length) throw new Error("Tick at least one season.");
        body.seasons = picked;
      }
      const forWho = B.reqForNow();
      await seerr("/request", "POST", body);
      // Glowfin new look: "Every new season from now on" also has Tower ask for each new season when it comes out
      const every = type === "tv" && $("[data-severy].on", p);
      if (every) await B.call("POST", "api/follow-show", { tmdb: +id, name: t }).catch(() => B.toast("Asked, but Tower couldn't remember to get new seasons."));
      B.closeAll();
      B.cache["seerr-mine"] = null;
      // Ben 2026-11-22: an anime show? Offer its canon and mixed movies too (they go into the show's Specials)
      if (type === "tv" && B.story) setTimeout(() => B.story.offerMovies(id, t), 400);
      return forWho ? `Asked for ${t} for ${forWho.name}. It's on their list, and they're told when it's ready.` : `Asked for ${t}. It shows in Your requests as Asked.`;
    });
    p.addEventListener("click", (e) => {
      const b = e.target.closest("[data-season]");
      if (b && !b.disabled) b.classList.toggle("on");
      // All seasons / the newest season only (Redesign 2's one Ask for it panel)
      const all = e.target.closest("[data-sall]") || e.target.closest("[data-severy]"), last = e.target.closest("[data-slast]");
      if (b && !b.disabled) $$("[data-severy]", p).forEach((x) => x.classList.toggle("on", false));
      if (!all && !last) return;
      const open = $$("[data-season]:not([disabled])", p);
      open.forEach((x, i) => x.classList.toggle("on", !!all || i === open.length - 1));
      $$("[data-sall],[data-slast],[data-severy]", p).forEach((x) => x.classList.toggle("on", x === (all || last)));
    });
  } catch (e) {
    p.innerHTML = `<button class="circle x" data-close>${I("x")}</button><h3>Couldn't open that</h3><p class="dim">${esc(B.say(e))}</p>`;
  }
};
document.addEventListener("click", (e) => {
  const c = e.target.closest("[data-ask]");
  if (!c) return;
  const [type, id] = c.dataset.ask.split(":");
  if (!/^(movie|tv)$/.test(type) || !id) return; // only movie:123 / tv:123 (never a song button)
  if (e.target.closest("[data-qask]")) return B.askPanel(type, id); // the hover Ask button: ask right away
  // Movies and shows open their full title page (js/ask.js); "Ask for it" there opens this panel
  if ((type === "movie" || type === "tv") && /^\d+$/.test(id || "")) return B.go(`#/ask/${type}/${id}`);
  B.askPanel(type, id);
});

// ---------- Redesign 2 (Ben 2026-11-10): one status helper, five pills, on every tab ----------
// Asked (grey), Getting it · 62% (blue), Still looking · day 3 (amber), Couldn't find it (red), Ready (green)
B.RQ_GROUPS = [["ready", "Ready"], ["getting", "Getting it"], ["looking", "Still looking"], ["asked", "Asked"], ["cant", "Couldn't find it"]];
B.rqPillHtml = (p) => `<span class="rqpill p-${esc(p.k)}">${esc(p.w)}</span>`;
B.seerrPill = (r) => {
  const ms = r.media ? r.media.status : 0;
  if (r.status === 3) return { k: "cant", w: "Ben said no" };
  if (r.status === 4) return { k: "cant", w: "Couldn't find it" };
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
  <div class="grow">${o.who ? `<div class="rqwho">${B.picHtml({ name: o.who }, "rqface")}<span>${esc(o.who)} asked</span></div>` : ""}<div class="t">${esc(o.title)}</div>${o.sub ? `<div class="s">${esc(o.sub)}</div>` : ""}<div style="margin-top:6px">${B.rqPillHtml(o.pill)}</div>${o.why ? `<div class="s dim" style="margin-top:4px">${esc(o.why)}</div>` : ""}</div>
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
// Glowfin R1 (Ben 2026-11-18): a tab's lists are chips across the top; the wall below shows the one picked.
// Your requests and Waiting for you sit on the page's top line and the Your requests tab.
B.rqSide = (lists, picked) => lists.map((l) => `<button class="chip ${l.id === picked ? "on" : ""}" data-rqpick="${esc(l.id)}" title="${esc(l.title)}">${esc(l.title)}</button>`).join("");
// Glowfin Top 10 rule on a wall: the first ten sit in one row, each number fully beside its poster; the rest below
B.rqGrid = (cards, ranked, cls) => (ranked && cards.length
  ? `<div class="strip rqt10">${cards.slice(0, 10).map((c, i) => `<div class="top10">${B.t10num(i + 1)}${c}</div>`).join("")}</div>`
  : "") + `<div class="grid ${cls || ""}">${(ranked ? cards.slice(10) : cards).join("")}</div>`;
// The end of a list: titles already on Benflix never show in the lists, but can be opened here
B.rqOwned = (n, open) => (n ? `<p style="margin-top:16px"><button class="chip" data-rqowned>${open ? "Hide the ones already on Glowfin" : `${n} more ${n === 1 ? "is" : "are"} already on Glowfin ›`}</button></p>` : "");
// Glowfin R1: how many asks wait for Ben's Yes or No (movies, shows, anime, music and books together)
B.rqWaitingCount = async () => {
  const me = await B.seerrMe();
  const [sw, mus, bks] = await Promise.all([
    me && me.active ? seerr(`/request?take=40&skip=0&sort=added&filter=pending`).catch(() => null) : null,
    B.call("GET", "api/music/status").catch(() => null),
    B.call("GET", "api/books/requests").catch(() => null),
  ]);
  return ((sw && sw.results) || []).length + ((mus && mus.requests) || []).filter((r) => r.waiting).length + ((bks && bks.waiting) || []).length;
};
const RQ_TABS = [["movies", "Movies"], ["shows", "Shows"], ["anime", "Anime"], ["music", "Music"], ["books", "Books"], ["manga", "Manga"], ["yours", "Your asks"]];
// Glowfin new look: the top describes the request cover you point at (name, facts, coloured status tag, story)
B.rqDescribe = (r) => {
  if (!r) return "";
  const t = r.title || r.name || "", year = (r.releaseDate || r.firstAirDate || "").slice(0, 4), s = B.rqStatus(r);
  const bits = [r.mediaType === "tv" ? (isAnime(r) ? "Anime" : "Show") : isAnime(r) ? "Anime movie" : "Movie", year, r.voteAverage ? "★ " + r.voteAverage.toFixed(1) : ""].filter(Boolean);
  const canAsk = s.k === "nyet" || (s.k === "out" && !s.dot);
  return `<div class="dt-name"><div class="t">${esc(t)}</div></div>
    <div class="meta">${bits.map((b) => `<span>${esc(String(b))}</span>`).join("")}</div><div class="dt-tag rqtag t-${esc(s.k)}">${esc(s.w)}</div>
    <div class="ov">${esc(r.overview || "")}</div><div class="rqhint">${canAsk ? "Click it to open it and ask for it" : "Asked already, so clicking just opens it"}</div>`;
};
const rqArt = (r) => tmdbImg(r && r.backdropPath, "w1280");
const detailsAnime = (d) => ((d.genres || []).some((g) => g.id === 16) || (d.genreIds || []).includes(16)) && d.originalLanguage === "ja";
B.route(/^\/requests/, async (m, nav) => {
  if (B.me.kids) return B.go("#/home");
  // Requests opens on the tab you used last (the tab order stays Movies, Shows, Anime, Music, Books)
  let tab = B.q("tab");
  if (!tab) { try { tab = localStorage.getItem("bf-rq-tab"); } catch (e) { tab = null; } }
  if (!RQ_TABS.some(([k]) => k === tab) || ((tab === "books" || tab === "manga") && !B.booksTab)) tab = "movies";
  try { localStorage.setItem("bf-rq-tab", tab); } catch (e) { /* private window */ }
  // Glowfin new look (Ben approved 2026-11-22): the tabs are see-through pills, then the search box, a round Filters
  // button (Movies and Shows) and Ben's yellow Waiting pill. Movies and Shows land straight on a row; the top
  // describes the cover you point at, with its art behind the page. For: Me lives on the Ask for it panel now.
  const canFilter = tab === "movies" || tab === "shows";
  B.page().innerHTML = B.top("Requests", null, { solid: false }) + `<nav class="ptabs rqtabs" aria-label="Requests places">${RQ_TABS.filter(([k]) => (k !== "books" && k !== "manga") || B.booksTab).map(([k, l]) => `<a class="${k === tab ? "on" : ""}" href="#/requests?tab=${k}"><span class="lg">${k === "yours" && B.me.admin ? "Manage" : l}</span><span class="sm">${k === "yours" ? (B.me.admin ? "Manage" : "Yours") : l}</span></a>`).join("")}
      <span class="rqsearch">${I("search")}<input id="rq" placeholder="${tab === "anime" ? "Search anime to ask for" : "Search to ask for anything"}" value="${esc(B.q("q") || "")}"></span>
      ${canFilter ? `<button class="rqround" id="rqfilt" title="Filters" aria-label="Filters">${I("filter")}</button>` : ""}
      ${B.me.admin && tab !== "yours" ? `<a class="rqwaitp" id="rqwait" href="#/requests?tab=yours" hidden><b id="rqwaitn"></b> waiting</a>` : ""}
    </nav>${canFilter || tab === "anime" || tab === "manga" ? B.doorTop() : tab === "music" || tab === "books" || (tab === "yours" && !B.me.admin) ? B.rqCoverTop(tab !== "music") : ""}
    <div class="rows" id="rows">${B.spinner("rows")}</div>`;
  B.wireTop();
  if (B.me.admin && tab !== "yours") B.rqWaitingCount().then((n) => { const el = $("#rqwaitn"); if (el && B.alive(nav)) { el.textContent = n; $("#rqwait").hidden = !(n > 0); } });
  if (tab === "music") return B.musicTab(nav);
  if (tab === "books") return B.booksTab(nav);
  if (tab === "manga") return B.mangaTab(nav);
  if (tab === "yours") return B.yoursTab(nav);
  const me = await B.seerrMe();
  if (!B.alive(nav)) return;
  if (!me || !me.active || !me.userFound) {
    $("#rows").innerHTML = `<div class="wrap"><div class="note">Requests aren't set up for ${esc(B.me.name)} yet. ${B.me.admin ? "Check the Jellyfin Enhanced plugin's Seerr settings in Jellyfin's Dashboard." : "Ask Ben to turn them on for you."}</div></div>`;
    return;
  }
  const search = async (text) => {
    if (!text.trim()) return drawRows();
    $("#rows").innerHTML = B.spinner("rows");
    const [r] = await Promise.all([seerr(`/search?query=${enc(text.trim())}&page=1`).catch(() => ({ results: [] })), B.libTmdb().catch(() => null)]);
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
    await B.loadReasons();
    lists = { mine: await B.seerrNamed(mine, tab), waiting: await B.seerrNamed(waiting, tab) };
  };
  // Layout C on every tab: the list on the left picks what the poster wall on the right shows
  const GENRES = tab === "movies"
    ? [["Action", 28], ["Adventure", 12], ["Comedy", 35], ["Crime", 80], ["Documentary", 99], ["Drama", 18], ["Family", 10751], ["Fantasy", 14], ["History", 36], ["Horror", 27], ["Mystery", 9648], ["Romance", 10749], ["Sci-Fi", 878], ["Thriller", 53], ["War", 10752], ["Western", 37]]
    : [["Action and adventure", 10759], ["Comedy", 35], ["Crime", 80], ["Documentary", 99], ["Drama", 18], ["Family", 10751], ["Kids", 10762], ["Mystery", 9648], ["Reality", 10764], ["Sci-Fi and fantasy", 10765], ["War and politics", 10768], ["Western", 37]];
  let walls = [], genre = null, ownedOpen = null;
  // Filters panel switches (remembered in this browser): hide what's on Glowfin (on), hide what's asked for (off)
  let filt = { owned: true, asked: false };
  try { filt = Object.assign(filt, JSON.parse(localStorage.getItem("bf-rq-filt") || "{}")); } catch (e) { /* private window */ }
  const saveFilt = () => { try { localStorage.setItem("bf-rq-filt", JSON.stringify(filt)); } catch (e) { /* private window */ } };
  // Top 10: a number badge on the picture's bottom corner (the name and year stay under the poster)
  let animeUi = null;
  // Anime's own side list opens Your requests / Waiting for you in a side panel (the new status lines)
  const openList = (which) => {
    const admin = which === "waiting", items = lists[admin ? "waiting" : "mine"];
    const p = B.side(`<h3>${admin ? "Waiting for you" : "Your requests"} (${items.length})</h3>`
      + B.rqLines(items.map((r) => B.seerrLine(r, admin)), !admin, admin ? "Nobody is waiting on you right now." : "Nothing asked for yet. Pick a poster and press Ask for it."));
    p.addEventListener("click", (e) => {
      const y = e.target.closest("[data-yes]"), n = e.target.closest("[data-no]");
      if (!y && !n) return;
      const id = (y || n).dataset.yes || (y || n).dataset.no;
      B.busy(y || n, async () => {
        await B.jf("POST", `/JellyfinEnhanced/arr/requests/${id}/${y ? "approve" : "decline"}`).catch(() => {});
        if (!y) await B.whyNo("seerr:" + id);
        await loadLists();
        if (B.alive(nav)) { if (animeUi) animeUi.drawSide(); B.closeAll(); openList(which); }
        return y ? "Said yes. Tower starts looking now." : "Said no.";
      });
    });
  };
  const drawWall = () => {
    // Anime has its own chips and walls (B.animeRequests, below)
    if (tab === "anime") { if (animeUi) animeUi.drawSide(); return; }
    const box = $("#rqrows");
    if (!box) return;
    // Glowfin R1 (Ben 2026-11-18): rows like the TV, Top 10 to ask for (numbers fully beside the posters),
    // Coming soon, Trending, then Genres. Titles already on Glowfin stay hidden; "N more" at a row's end opens them.
    const rowsHtml = walls.map((w) => {
      const open = ownedOpen === w.id || !filt.owned;
      const shown = (open ? w.items.concat(w.owned) : w.items).filter((x) => !filt.asked || !asked(x));
      const cards = w.ranked
        ? shown.slice(0, 10).map((r, i) => `<div class="top10">${B.t10num(i + 1)}${B.askCard(r)}</div>`)
        : shown.map(B.askCard);
      if (w.owned.length && filt.owned) cards.push(`<button class="poster see rqownedcard" data-rqowned="${esc(w.id)}">${open ? "Hide the ones already on Glowfin" : `${w.owned.length} more ${w.owned.length === 1 ? "is" : "are"} already on Glowfin ›`}</button>`);
      return cards.length ? B.row(w.title, cards) : "";
    }).join("");
    // (Glowfin new look: Genres and the hide switches live in the Filters panel now)
    box.innerHTML = rowsHtml || `<div class="wrap empty">Nothing to show. Try fewer filters, or again in a minute.</div>`;
    if (!described) {
      described = true;
      const first = walls[0] && walls[0].items[0];
      B.wireDescribe(first, { describe: B.rqDescribe, art: rqArt });
    }
  };
  let described = false;
  // The Filters panel (right-hand side): the two switches flip straight away, a genre fills every row with it
  const openFilters = () => {
    const draw = () => `<h3>Filters</h3><p class="dim">${tab === "movies" ? "Movies" : "Shows"} tab. Changes show straight away.</p>
      <button class="opt rqsw ${filt.owned ? "on" : ""}" data-f="owned">Hide what's already on Glowfin<small>Shows with missing episodes always stay</small></button>
      <button class="opt rqsw ${filt.asked ? "on" : ""}" data-f="asked">Hide what's already asked for<small>${filt.asked ? "On" : "Off"}</small></button>
      <h4 class="dim" style="margin:16px 0 8px">Genre</h4>
      <div class="rqchips">${[["Any", 0]].concat(GENRES).map(([l, id]) => `<button class="chip ${(genre ? genre[1] : 0) === id ? "on" : ""}" data-rqgenre="${id}">${esc(l)}</button>`).join("")}</div>`;
    const p = B.side(draw());
    p.addEventListener("click", (e) => {
      const f = e.target.closest("[data-f]"), g = e.target.closest("[data-rqgenre]");
      if (f) {
        filt[f.dataset.f] = !filt[f.dataset.f];
        saveFilt();
        drawWall();
      } else if (g) {
        const id = +g.dataset.rqgenre;
        genre = id ? GENRES.find((x) => x[1] === id) : null;
        loadWalls();
      } else return;
      p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button>` + draw();
    });
  };
  if (canFilter) $("#rqfilt").onclick = openFilters;
  const drawRows = async () => {
    if (tab === "anime") { animeUi = B.animeRequests(nav, () => lists, openList); return; }
    $("#rows").innerHTML = `<div id="rqrows">${B.spinner("rows")}</div>`;
    const box = $("#rqrows");
    box.onclick = (e) => {
      const o = e.target.closest("[data-rqowned]");
      if (o) { ownedOpen = ownedOpen === o.dataset.rqowned ? null : o.dataset.rqowned; drawWall(); }
    };
    await loadWalls();
  };
  const loadWalls = async () => {
    const wall = $("#rqrows");
    if (wall && tab !== "anime") wall.innerHTML = B.spinner("wall");
    const kindPath = tab === "movies" ? "movies" : "tv";
    const pg = (path, list) => list.map((n) => seerr(`${path}?page=${n}`).catch(() => null));
    // Anime: TMDB's Animation genre is mostly kids' cartoons, so only Japanese titles count as anime
    const groups = tab === "anime"
      ? [pg("/discover/tv/genre/16", [1, 2, 3, 4]), pg("/discover/movies/genre/16", [1, 2, 3]), pg("/discover/tv/genre/16", [5, 6, 7])]
      : genre
        ? [pg(`/discover/${kindPath}/genre/${genre[1]}`, [1, 2, 3, 4])]
        : [pg(`/discover/${kindPath}`, [1, 2, 3, 4, 5, 6]), pg(`/discover/${kindPath}/upcoming`, [1, 2, 3])];
    const got = await Promise.all(groups.map((g) => Promise.all(g)).concat([B.libTmdb()]));
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
      walls = [wallOf("top", "Top 10 to ask for this week", top, true), wallOf("soon", "Coming soon", all(1)),
        wallOf("trending", "Trending", trend.filter((x) => !top.includes(x)))];
    }
    walls = walls.filter((w) => w.items.length || w.owned.length);
    drawWall();
    checkMissing(nav);
    // Shows: "New seasons for shows you watch" goes first once it's worked out (not inside one genre)
    if (tab === "shows" && !genre) {
      B.newSeasons().then((items) => {
        if (!items.length || !B.alive(nav) || genre || walls.some((w) => w.id === "newseasons")) return;
        walls.unshift({ id: "newseasons", title: "New seasons for shows you watch", ranked: false, items, owned: [] });
        drawWall();
      }).catch(() => {});
    }
    // "Finish your sets" joins after the Top 10 once it's worked out (Movies only, not inside one genre)
    if (tab === "movies" && !genre) {
      B.finishSets().then((items) => {
        if (!items.length || !B.alive(nav) || genre || walls.some((w) => w.id === "sets")) return;
        walls.splice(Math.min(1, walls.length), 0, { id: "sets", title: "Finish your sets", ranked: false, items, owned: [] });
        drawWall();
      }).catch(() => {});
    }
  };
  // Shows already on Benflix: look each one up, and bring back the ones missing aired episodes
  const checkMissing = async (myNav) => {
    const shows = walls.flatMap((w) => w.owned.filter((x) => x.mediaType === "tv").map((x) => [w, x]));
    let i = 0, redraw = null;
    const next = async () => {
      while (i < shows.length) {
        const [w, x] = shows[i++];
        const whole = await B.showComplete(x.id).catch(() => true);
        if (!B.alive(myNav) || whole) continue;
        x._missing = true;
        w.owned = w.owned.filter((o) => o !== x);
        if (!w.items.includes(x)) w.items.push(x);
        clearTimeout(redraw);
        redraw = setTimeout(() => { if (B.alive(myNav)) drawWall(); }, 400);
      }
    };
    await Promise.all([next(), next(), next(), next()]);
  };
  let t;
  $("#rq").oninput = (e) => { clearTimeout(t); t = setTimeout(() => search(e.target.value), 350); };
  if (B.q("q")) search(B.q("q")); else drawRows();
  loadLists().then(() => { if (B.alive(nav) && tab === "anime" && animeUi) animeUi.drawSide(); });
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
    if (d) { md.title = d.title || d.name || md.title; md.anime = detailsAnime(d); md.poster = d.posterPath; md.backdrop = d.backdropPath; md.overview = d.overview; md.year = (d.releaseDate || d.firstAirDate || "").slice(0, 4); }
  }));
  if (!tab) return items;
  return items.filter((r) => (tab === "movies" ? !r.media.anime : tab === "shows" ? !r.media.anime : r.media.anime));
};
// Why Ben said no (2026-11-21): Ben can pick a ready-made reason or type his own after saying no; whoever asked
// sees it on their list. Kept on Tower, keyed "seerr:<request number>".
B.reasons = {};
B.loadReasons = () => B.call("GET", "api/asks/reasons").then((d) => { B.reasons = (d && d.reasons) || {}; }).catch(() => {});
B.NO_REASONS = ["We already have it", "Not a good fit for Glowfin", "Too big for Tower right now", "It isn't out yet", "Ask me about this one"];
B.whyNo = (key) => new Promise((done) => {
  B.closeAll();
  B.shade(() => done(""));
  const m = document.createElement("form");
  m.className = "modal";
  m.innerHTML = `<h3>Tell them why?</h3><p>They'll see this next to their request. You can skip it.</p>
    <div class="acts" style="margin-top:0">${B.NO_REASONS.map((x, i) => `<button class="btn small" type="button" data-r="${i}">${esc(x)}</button>`).join("")}</div>
    <input class="field" name="v" type="text" maxlength="140" autocomplete="off" placeholder="Or type your own reason" style="margin-top:14px">
    <div class="acts"><button class="btn white" type="submit">Send my reason</button><button class="btn" type="button" data-skip>Skip</button></div>`;
  const send = async (reason) => {
    B.closeAll();
    if (reason) { await B.call("POST", "api/asks/reason", { key, reason }).catch(() => {}); B.reasons[key] = reason; }
    done(reason || "");
  };
  m.onclick = (e) => {
    const r = e.target.closest("[data-r]");
    if (r) return send(B.NO_REASONS[+r.dataset.r]);
    if (e.target.closest("[data-skip]")) send("");
  };
  m.onsubmit = (e) => { e.preventDefault(); send(m.elements.v.value.trim()); };
  document.body.appendChild(m);
  $("button", m).focus();
});
// Auto-yes (2026-11-21, Ben only): people Ben trusts get an automatic yes for movies and shows, up to a number a week
B.autoYes = async () => {
  const d = await B.call("GET", "api/asks/trust").catch((e) => ({ err: B.say(e) }));
  const people = (d && d.people) || [];
  const p = B.side(`<h3>Auto-yes</h3><p class="dim">Tower says yes for you to movie and show requests from the people you switch on, up to their number each week. After that their requests wait for you as normal. Kids never get this.</p>`
    + (d.err ? `<p class="dim">${esc(d.err)}</p>` : people.length ? `<div class="list">${people.map((x) => `<div class="item" data-p="${esc(x.id)}"><div class="grow"><div class="t">${esc(x.name)}</div><div class="s">${x.on ? `${x.used} of ${x.weekly} used this week` : "Off: every request waits for you"}</div></div>
      <div class="acts"><button class="btn small" data-less title="Fewer">-</button><b data-n style="min-width:22px;text-align:center">${x.weekly}</b><button class="btn small" data-more title="More">+</button><button class="btn small ${x.on ? "white" : ""}" data-on>${x.on ? "On" : "Off"}</button></div></div>`).join("")}</div>` : `<p class="dim">Nobody else has a profile yet.</p>`));
  p.addEventListener("click", (e) => {
    const row = e.target.closest("[data-p]");
    if (!row || !e.target.closest("button")) return;
    const x = people.find((q) => q.id === row.dataset.p);
    if (e.target.closest("[data-on]")) x.on = !x.on;
    else if (e.target.closest("[data-more]")) x.weekly = Math.min(20, x.weekly + 1);
    else if (e.target.closest("[data-less]")) x.weekly = Math.max(1, x.weekly - 1);
    else return;
    const on = row.querySelector("[data-on]");
    on.textContent = x.on ? "On" : "Off"; on.classList.toggle("white", x.on);
    row.querySelector("[data-n]").textContent = x.weekly;
    row.querySelector(".s").textContent = x.on ? `${x.used} of ${x.weekly} used this week` : "Off: every request waits for you";
    B.call("POST", "api/asks/trust", { id: x.id, on: x.on, weekly: x.weekly }).catch((err) => B.toast(B.say(err)));
  });
};
B.seerrLine = (r, answer) => {
  const md = r.media || {};
  const who = (r.requestedBy && (r.requestedBy.displayName || r.requestedBy.jellyfinUsername)) || "Someone";
  const seasons = (r.seasons || []).map((s) => s.seasonNumber);
  const kind = r.type === "movie" ? "Movie" : seasons.length === 1 ? "Season " + seasons[0] : seasons.length ? seasons.length + " seasons" : "Show";
  return {
    title: md.title || md.name || "Name not found", sub: [B.me.admin ? who : "", kind, B.when(r.createdAt)].filter(Boolean).join(" · "),
    cover: md.poster ? tmdbImg(md.poster, "w185") : "", pill: B.seerrPill(r), who: answer && B.me.admin ? who : "",
    why: r.status === 3 && B.reasons["seerr:" + r.id] ? `Why: ${B.reasons["seerr:" + r.id]}` : "",
    acts: answer ? `<button class="btn small white" data-yes="${r.id}">Yes</button><button class="btn small" data-no="${r.id}">No</button>` : `<button class="btn small" data-ask="${esc(r.type)}:${esc(md.tmdbId)}">Open</button>`,
  };
};

// ---------- Your requests: every kind (movies, shows, songs, albums, books), grouped by status ----------
B.yoursTab = async (nav) => {
  const rows = $("#rows");
  $("#rq").style.display = "none";
  if (B.me.admin) return B.manageTab(nav);
  // Glowfin new look (Ben approved 2026-11-22): family members see Your asks as rows of covers, each with its dot,
  // and the top says where it stands (waiting for Ben, on its way, here, or Ben said no with his reason)
  let described = null;
  const famTag = (r) => {
    const md = r.media || {};
    if (md.status === 5) return { k: "here", w: "Here now on Glowfin" };
    if (r.status === 3) return { k: "bad", w: B.reasons["seerr:" + r.id] ? `Ben said no: ${B.reasons["seerr:" + r.id]}` : "Ben said no" };
    if (r.status === 4) return { k: "bad", w: "Stuck · Ben will have a look" };
    if (r.status === 1) return { k: "wait", w: `Waiting for Ben · you ${mgAgo(Date.parse(r.createdAt || "") || 0) || "asked"}` };
    if (md.status === 4) return { k: "way", w: "On its way · partly here" };
    return { k: "way", w: "On its way · Ben said yes" };
  };
  const famCard = (r) => {
    const md = r.media || {}, key = "ya:" + r.id, tag = famTag(r);
    B.items[key] = { title: md.title || md.name || "", facts: [r.type === "movie" ? "Movie" : "Show", md.year].filter(Boolean).join(" · "), tag, story: md.overview || "", cover: md.poster ? tmdbImg(md.poster, "w500") : "" };
    const img = md.poster ? tmdbImg(md.poster) : "";
    return `<button class="poster askcard" data-ask="${esc(r.type)}:${esc(md.tmdbId)}" data-id="${key}" title="${esc(B.items[key].title)}"><div class="im" ${img ? `style="background-image:url('${img}')"` : ""}>${img ? "" : `<div class="noart"><span class="nt">${esc(B.items[key].title)}</span></div>`}<span class="rqdot d-${tag.k}" title="${esc(tag.w)}"></span></div></button>`;
  };
  const otherCard = (l, i) => {
    const key = "yo:" + i, k = { ready: "here", getting: "way", looking: "way", cant: "bad" }[l.pill.k] || "wait";
    B.items[key] = { title: l.title, facts: l.sub, tag: { k, w: l.pill.w }, story: l.why || "", cover: l.cover || "" };
    const open = l.book ? `href="#/book/${enc(l.book)}"` : "";
    return `<a class="poster askcard" ${open} data-id="${key}" title="${esc(l.title)}"><div class="im" style="${l.square ? "aspect-ratio:1;" : ""}${l.cover ? `background-image:url('${esc(l.cover)}')` : ""}">${l.cover ? "" : `<div class="noart"><span class="nt">${esc(l.title)}</span></div>`}<span class="rqdot d-${k}" title="${esc(l.pill.w)}"></span></div></a>`;
  };
  const famDescribe = (it) => `<h1>${esc(it.title)}</h1><p>${esc(it.facts)}</p><span class="mtop-kick rqfam-${esc(it.tag.k)}">${esc(it.tag.w)}</span>${it.story ? `<p class="ov">${esc(it.story)}</p>` : ""}`;
  const drawRows = (mine, others) => {
    const newest = mine.slice().sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
    const here = newest.filter((r) => (r.media || {}).status === 5), asks = newest.filter((r) => (r.media || {}).status !== 5);
    const html = B.row("Your asks", asks.map(famCard)) + B.row("Here now from your asks", here.map(famCard)) + B.row("Songs and books you asked for", others.map(otherCard));
    rows.innerHTML = html || `<div class="wrap empty">Nothing asked for yet. Pick a poster on any tab and ask for it.</div>`;
    if (!described) described = B.wireRqCoverTop(famDescribe, (it) => it.cover);
    const first = $("#rows [data-id]");
    if (first && described) described(B.items[first.dataset.id]);
  };
  const draw = async () => {
    const me = await B.seerrMe();
    const [seerrMine, seerrWaiting, mus, bks] = await Promise.all([
      me && me.active ? seerr(`/request?take=60&skip=0&sort=added${B.me.admin ? "" : `&requestedBy=${me.jellyseerrUserId}`}`).catch(() => null) : null,
      me && me.active && B.me.admin ? seerr(`/request?take=40&skip=0&sort=added&filter=pending`).catch(() => null) : null,
      B.call("GET", "api/music/status").catch(() => null),
      B.call("GET", "api/books/requests").catch(() => null),
    ]);
    const [mine, waiting] = await Promise.all([B.seerrNamed(seerrMine), B.seerrNamed(seerrWaiting), B.loadReasons()]);
    if (!B.alive(nav)) return;
    const waitIds = new Set(waiting.map((r) => r.id));
    const lines = mine.filter((r) => !waitIds.has(r.id)).map((r) => B.seerrLine(r, false));
    const wait = waiting.map((r) => B.seerrLine(r, true));
    ((mus && mus.requests) || []).forEach((r) => {
      const o = { title: r.title, sub: [B.me.admin ? r.who : "", r.artist, r.kind].filter(Boolean).join(" · "), cover: r.cover, square: true, pill: B.musicPill(r), why: r.why || "" };
      if (B.me.admin && r.waiting) wait.push(Object.assign(o, { who: r.who || "", acts: `<button class="btn small white" data-myes="${esc(r.id)}">Yes</button><button class="btn small" data-mno="${esc(r.id)}">No</button>` }));
      else lines.push(Object.assign(o, { other: "music", acts: r.can_retry && B.me.admin ? `<button class="btn small" data-mretry="${esc(r.id)}">Try again</button>` : "" }));
    });
    if (bks) {
      const bw = new Set((bks.waiting || []).map((r) => r.id));
      const all = (B.me.admin ? [].concat(bks.mine || [], bks.others || []) : bks.mine || []).filter((r, i, a) => a.findIndex((x) => x.id === r.id) === i && !bw.has(r.id));
      const line = (r) => ({ title: r.name, sub: [B.me.admin ? r.who : "", r.author, (r.types || []).join(" and ")].filter(Boolean).join(" · "), cover: r.cover, pill: B.bookPill(r) });
      all.forEach((r) => lines.push(Object.assign(line(r), { other: "book", book: r.state === "ready" && r.title ? r.title : "", acts: r.state === "ready" && r.title ? `<a class="btn small" href="#/book/${enc(r.title)}">Open</a>` : "" })));
      if (B.me.admin) (bks.waiting || []).forEach((r) => wait.push(Object.assign(line(r), { who: r.who || "", acts: `<button class="btn small white" data-byes="${esc(r.id)}">Yes</button><button class="btn small" data-bno="${esc(r.id)}">No</button>` })));
    }
    if (!B.me.admin) return drawRows(mine, lines.filter((l) => l.other));
    rows.innerHTML = `<div class="wrap">${B.me.admin ? `<p style="margin:0 0 12px"><button class="btn small" data-autoyes>Auto-yes for people you trust</button></p>` : ""}${B.me.admin && wait.length ? `<h3 class="section-title">Waiting for you <span class="rqpill p-looking">${wait.length}</span></h3>${B.rqLines(wait, false, "")}` : ""}
      ${B.rqLines(lines, true, "Nothing asked for yet. Pick a poster on any tab and ask for it.")}</div>`;
  };
  rows.onclick = (e) => {
    if (e.target.closest("[data-autoyes]")) return B.autoYes();
    const b = e.target.closest("[data-yes],[data-no],[data-myes],[data-mno],[data-byes],[data-bno],[data-mretry]");
    if (!b) return;
    const d = b.dataset;
    B.busy(b, async () => {
      if (d.yes || d.no) await B.jf("POST", `/JellyfinEnhanced/arr/requests/${d.yes || d.no}/${d.yes ? "approve" : "decline"}`).catch(() => {});
      if (d.no) await B.whyNo("seerr:" + d.no);
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

// ---------- Manage (Ben only, 2026-11-21): every request from everyone, sorted by what needs doing ----------
// Five boxes (Needs you, On its way, Stuck, Ready, Said no) pick the list below. Filters by person and by kind,
// tick several to answer at once, Stop on anything that already had a yes, Changed my mind on anything Ben said no to.
const MG_LANES = [["needs", "Needs you", "Say yes or no"], ["way", "On its way", "Tower is getting these"], ["stuck", "Stuck", "Looking for 5 days or more"], ["ready", "Ready", "Arrived"], ["no", "Said no", "You turned these down"]];
const MG_KINDS = ["Movies", "Shows", "Anime", "Music", "Books"];
const MG_STUCK_DAYS = 5;
const mgAgo = (at) => {
  if (!at) return "";
  const d = Math.floor((Date.now() - at) / 86400000);
  return d < 1 ? "asked today" : d === 1 ? "asked yesterday" : `asked ${d} days ago`;
};
// One small box for Stop and for No to several: a reason (or none), and for Stop whether to delete what arrived
const mgAsk = (o) => new Promise((done) => {
  B.closeAll();
  B.shade(() => done(null));
  const m = document.createElement("form");
  m.className = "modal";
  m.innerHTML = `<h3>${esc(o.title)}</h3><p>${esc(o.text)}</p>
    ${o.reasons ? `<div class="acts mg-reasons" style="margin-top:0">${B.NO_REASONS.map((x, i) => `<button class="btn small" type="button" data-r="${i}">${esc(x)}</button>`).join("")}</div>
    <input class="field" name="v" type="text" maxlength="140" autocomplete="off" placeholder="Or type your own reason" style="margin-top:14px">` : ""}
    ${o.del ? `<label class="mg-del"><input type="checkbox" name="del"> ${esc(o.del)}</label>` : ""}
    <div class="acts"><button class="btn white" type="submit">${esc(o.yes)}</button><button class="btn" type="button" data-back>${esc(o.back)}</button></div>`;
  let picked = "";
  m.onclick = (e) => {
    const r = e.target.closest("[data-r]");
    if (r) {
      picked = picked === B.NO_REASONS[+r.dataset.r] ? "" : B.NO_REASONS[+r.dataset.r];
      $$("[data-r]", m).forEach((b) => b.classList.toggle("white", B.NO_REASONS[+b.dataset.r] === picked));
      if (picked && m.elements.v) m.elements.v.value = "";
    }
    if (e.target.closest("[data-back]")) { B.closeAll(); done(null); }
  };
  m.onsubmit = (e) => {
    e.preventDefault();
    const typed = m.elements.v ? m.elements.v.value.trim() : "";
    B.closeAll();
    done({ reason: typed || picked, del: !!(m.elements.del && m.elements.del.checked) });
  };
  document.body.appendChild(m);
  $("button", m).focus();
});
B.manageTab = async (nav) => {
  const rows = $("#rows");
  let lane = "needs", who = "", kind = "", all = [], picking = false;
  try { lane = sessionStorage.getItem("bf-mg-lane") || "needs"; } catch (e) { /* private window */ }
  if (!MG_LANES.some(([k]) => k === lane)) lane = "needs";
  const ticks = new Set(), holds = {};
  const saveReason = (key, reason) => (reason ? B.call("POST", "api/asks/reason", { key, reason }).then(() => { B.reasons[key] = reason; }).catch(() => {}) : null);
  const load = async () => {
    const me = await B.seerrMe();
    const [sr, mus, bks] = await Promise.all([
      me && me.active ? seerr(`/request?take=100&skip=0&sort=added`).catch(() => null) : null,
      B.call("GET", "api/music/status").catch(() => null),
      B.call("GET", "api/books/requests").catch(() => null),
    ]);
    const [named] = await Promise.all([B.seerrNamed(sr), B.loadReasons()]);
    const out = [];
    named.forEach((r) => {
      const md = r.media || {}, at = Date.parse(r.createdAt || "") || 0;
      const since = Date.parse(r.updatedAt || r.createdAt || "") || at;
      const days = Math.floor((Date.now() - since) / 86400000);
      const seasons = (r.seasons || []).map((x) => x.seasonNumber);
      const l = r.status === 1 ? "needs" : r.status === 3 ? "no" : md.status === 5 ? "ready" : r.status === 4 || (md.status !== 4 && days >= MG_STUCK_DAYS) ? "stuck" : "way";
      out.push({
        key: "seerr:" + r.id, src: "seerr", id: r.id, lane: l, at, title: md.title || md.name || "Name not found",
        who: (r.requestedBy && (r.requestedBy.displayName || r.requestedBy.jellyfinUsername)) || "Someone",
        kind: md.anime ? "Anime" : r.type === "movie" ? "Movies" : "Shows",
        what: r.type === "movie" ? "Movie" : seasons.length === 1 ? "Season " + seasons[0] : seasons.length ? seasons.length + " seasons" : "Show",
        cover: md.poster ? tmdbImg(md.poster, "w185") : "", type: r.type, tmdb: md.tmdbId,
        art: md.backdrop ? tmdbImg(md.backdrop, "w1280") : md.poster ? tmdbImg(md.poster, "w500") : "", story: md.overview || "", year: md.year || "",
        said: r.status === 2 || r.status === 5 || md.status === 5,
        pill: l === "stuck" ? { k: "looking", w: r.status === 4 ? "Stuck · couldn't start" : `Stuck · day ${days + 1}` } : l === "no" ? { k: "cant", w: "You said no" } : l === "needs" ? { k: "asked", w: "Waiting for you" } : B.seerrPill(r),
        why: l === "no" && B.reasons["seerr:" + r.id] ? `Why: ${B.reasons["seerr:" + r.id]}` : "",
      });
    });
    ((mus && mus.requests) || []).forEach((r) => {
      const p = B.musicPill(r);
      const l = r.waiting ? "needs" : p.k === "ready" ? "ready" : p.k === "cant" || (r.days || 0) >= MG_STUCK_DAYS ? "stuck" : "way";
      out.push({ key: "music:" + r.id, src: "music", id: r.id, lane: l, at: 0, title: r.title, who: r.who || "Ben", kind: "Music", what: [r.kind, r.artist].filter(Boolean).join(" · "),
        cover: r.cover, square: true, pill: l === "needs" ? { k: "asked", w: "Waiting for you" } : p, why: r.why || "", retry: !!r.can_retry });
    });
    if (bks) {
      const seen = new Set();
      const add = (r, waiting) => {
        if (!r || seen.has(r.id)) return;
        seen.add(r.id);
        const p = B.bookPill(r) || { k: "asked", w: "Asked" };
        const l = waiting ? "needs" : r.state === "no" ? "no" : p.k === "ready" ? "ready" : p.k === "cant" ? "stuck" : "way";
        out.push({ key: "book:" + r.id, src: "book", id: r.id, lane: l, at: 0, title: r.name, who: r.who || "Ben", kind: "Books", what: ["Book", r.author].filter(Boolean).join(" · "),
          cover: r.cover, pill: l === "needs" ? { k: "asked", w: "Waiting for you" } : l === "no" ? { k: "cant", w: "You said no" } : p, book: r.state === "ready" && r.title ? r.title : "" });
      };
      (bks.waiting || []).forEach((r) => add(r, true));
      [].concat(bks.mine || [], bks.others || []).forEach((r) => add(r, false));
    }
    all = out;
    Array.from(ticks).forEach((k) => { if (!all.some((r) => r.key === k && r.lane === "needs")) ticks.delete(k); });
  };
  const shown = () => all.filter((r) => (!who || r.who === who) && (!kind || r.kind === kind));
  const acts = (r) => {
    const open = r.src === "seerr" && r.tmdb ? `<button class="btn small" data-ask="${esc(r.type)}:${esc(r.tmdb)}">Open</button>` : r.book ? `<a class="btn small" href="#/book/${enc(r.book)}">Open</a>` : "";
    if (r.lane === "needs") return `<button class="btn small white" data-mg="yes">Yes</button><button class="btn small mg-no" data-mg="no">No</button>${open}`;
    if (r.lane === "way") return `<button class="btn small mg-no" data-mg="stop">Stop</button>${open}`;
    if (r.lane === "stuck") return `${r.src !== "music" || r.retry ? `<button class="btn small white" data-mg="again">Look again</button>` : ""}<button class="btn small mg-no" data-mg="stop">Stop</button>${open}`;
    if (r.lane === "no") return `${r.src === "seerr" ? `<button class="btn small" data-mg="undo">Changed my mind</button>` : ""}${open}`;
    return open;
  };
  const line = (r) => `<div class="item" data-key="${esc(r.key)}">${picking && r.lane === "needs" ? `<button class="mg-tick ${ticks.has(r.key) ? "on" : ""}" data-mg="tick" role="checkbox" aria-checked="${ticks.has(r.key)}" aria-label="Pick ${esc(r.title)}">${ticks.has(r.key) ? "✓" : ""}</button>` : ""}
    <div class="rqthumb ${r.square ? "sq" : ""}" style="${r.cover ? `background-image:url('${esc(r.cover)}')` : ""}"></div>
    <div class="grow"><div class="t">${esc(r.title)}</div><div class="mg-meta">${B.picHtml({ name: r.who })}<b>${esc(r.who)}</b><span>${esc([r.what, mgAgo(r.at)].filter(Boolean).map((x) => "· " + x).join(" "))}</span>${B.rqPillHtml(r.pill)}</div>
    ${r.why || holds[r.key] ? `<div class="mg-why">${esc(r.why || holds[r.key])}</div>` : ""}</div>
    <div class="acts">${acts(r)}</div></div>`;
  // Glowfin new look (Ben approved 2026-11-22): the request you're on fills the right side with its art, who asked
  // and when, and why it's stuck (wide screens; phones keep the list only)
  let onKey = null;
  const side = (r) => {
    const box = $("#mgSide");
    if (!box) return;
    if (!r) { box.innerHTML = ""; return; }
    const tagK = { needs: "wt", way: "ow", stuck: "bad", ready: "ok", no: "bad" }[r.lane];
    const tagW = r.lane === "needs" ? `${r.who} ${mgAgo(r.at) || "asked"}` : r.pill ? r.pill.w : "";
    const why = r.why || holds[r.key] || (r.lane === "stuck" && r.src === "seerr" ? "Why: no copies found yet. Tower keeps looking." : "");
    const whoLine = r.lane === "needs" ? "" : [r.who + " " + (mgAgo(r.at) || "asked"), r.said ? "you said yes" : ""].filter(Boolean).join(" · ");
    box.innerHTML = `<div class="mg-art" style="${r.art || r.cover ? `background-image:url('${esc(r.art || r.cover)}')` : ""}"></div>
      <h2>${esc(r.title)}</h2><p class="mg-facts">${esc([r.what, r.year].filter(Boolean).join(" · "))}</p>
      <span class="rqtag ${tagK}">${esc(tagW)}</span>${why ? `<p class="mg-sidewhy">${esc(why.startsWith("Why") ? why : "Why: " + why)}</p>` : ""}
      ${whoLine ? `<p class="dim">${esc(whoLine)}</p>` : ""}${r.story ? `<p class="mg-story">${esc(r.story)}</p>` : ""}`;
  };
  const draw = () => {
    const vis = shown(), list = vis.filter((r) => r.lane === lane);
    const people = Array.from(new Set(all.map((r) => r.who))).sort((a, b) => a.localeCompare(b));
    const openOf = (n) => all.filter((r) => r.who === n && (r.lane === "needs" || r.lane === "way" || r.lane === "stuck")).length;
    const kinds = MG_KINDS.filter((k) => all.some((r) => r.kind === k));
    const needs = list.filter((r) => r.lane === "needs");
    const bulk = lane !== "needs" || needs.length < 2 ? ""
      : picking ? `<div class="mg-bulk"><span>${ticks.size} picked</span><span class="sp"></span><button class="btn small white" data-mg="yesall" ${ticks.size ? "" : "disabled"}>Yes to ${ticks.size || "…"}</button><button class="btn small mg-no" data-mg="noall" ${ticks.size ? "" : "disabled"}>No to ${ticks.size || "…"}</button><button class="btn small" data-mg="pickall">Pick all</button><button class="btn small" data-mg="pickoff">Done picking</button></div>`
        : `<div class="mg-bar"><button class="btn small" data-mg="everyyes">Yes to all ${needs.length}</button><button class="btn small" data-mg="pickon">Pick several</button></div>`;
    const empty = { needs: "Nobody is waiting on you right now.", way: "Nothing is on its way right now.", stuck: "Nothing is stuck. Good.", ready: "Nothing has arrived yet.", no: "You haven't said no to anything." }[lane];
    rows.innerHTML = `<div class="wrap"><div class="mg-tiles">${MG_LANES.map(([k, t, sub]) => `<button class="mg-tile ${k} ${k === lane ? "on" : ""}" data-lane="${k}" aria-pressed="${k === lane}"><b>${vis.filter((r) => r.lane === k).length}</b><span>${t}</span><small>${sub}</small></button>`).join("")}</div>
      <div class="mg-bar mg-filters"><button class="mg-face all ${who ? "" : "on"}" data-who="">Everyone</button>${people.map((n) => `<button class="mg-face ${who === n ? "on" : ""}" data-who="${esc(n)}">${B.picHtml({ name: n })}${esc(n)} <i>${openOf(n)}</i></button>`).join("")}
        ${kinds.length > 1 ? `<span class="mg-sep"></span><button class="chip ${kind ? "" : "on"}" data-kind="">All</button>${kinds.map((k) => `<button class="chip ${kind === k ? "on" : ""}" data-kind="${k}">${k}</button>`).join("")}` : ""}
        <span class="sp"></span><button class="chip" data-autoyes>Auto-yes ›</button></div>
      ${bulk}<div class="mg-split"><div class="list mg-list">${list.map(line).join("") || `<div class="item"><div class="grow dim">${esc(who || kind ? "Nothing here with these filters." : empty)}</div></div>`}</div><aside class="mg-side" id="mgSide"></aside></div></div>`;
    side(list.find((r) => r.key === onKey) || list[0]);
    // Stuck rows say why (Radarr / Sonarr, at home only); the line arrives a moment after the row
    if (lane === "stuck") list.filter((r) => r.src === "seerr" && r.tmdb && !(r.key in holds)).slice(0, 12).forEach((r) => {
      holds[r.key] = "";
      B.call("GET", `api/admin/holdup?kind=${r.type}&tmdb=${r.tmdb}`).then((h) => {
        if (!h || !h.line || !B.alive(nav)) return;
        holds[r.key] = h.line;
        const el = rows.querySelector(`[data-key="${r.key}"] .grow`);
        if (el && !el.querySelector(".mg-why")) el.insertAdjacentHTML("beforeend", `<div class="mg-why">${esc(h.line)}</div>`);
      }).catch(() => {});
    });
  };
  const answer = async (r, yes) => {
    if (r.src === "seerr") await B.jf("POST", `/JellyfinEnhanced/arr/requests/${r.id}/${yes ? "approve" : "decline"}`).catch(() => {});
    else if (r.src === "music") await B.call("POST", `api/music/request/${r.id}/${yes ? "approve" : "decline"}`);
    else await B.call("POST", "api/books/answer", { id: r.id, yes });
  };
  const stop = async (r) => {
    if (r.src === "music") return B.call("POST", `api/music/request/${r.id}/remove`).then(() => "Stopped.");
    if (r.src === "book") return B.call("DELETE", `api/books/request/${r.id}`).then(() => "Stopped.");
    return null;
  };
  const again = async (r) => {
    if (r.src === "music") return B.call("POST", `api/music/request/${r.id}/retry`).then(() => "Looking again.");
    if (r.src === "book") return B.call("POST", `api/books/retry/${r.id}`).then(() => "Looking again.");
    const a = await B.call("POST", `api/admin/holdup/search?kind=${r.type}&tmdb=${r.tmdb}`);
    return (a && a.message) || "Looking again.";
  };
  const reload = async () => { await load(); if (B.alive(nav) && !$(".modal")) draw(); };
  const pointAt = (e) => {
    const row = e.target.closest(".mg-list [data-key]");
    if (!row || row.dataset.key === onKey) return;
    onKey = row.dataset.key;
    side(all.find((r) => r.key === onKey));
  };
  rows.addEventListener("mouseover", pointAt);
  rows.addEventListener("focusin", pointAt);
  B.onLeave(() => { rows.removeEventListener("mouseover", pointAt); rows.removeEventListener("focusin", pointAt); });
  rows.onclick = (e) => {
    if (e.target.closest("[data-autoyes]")) return B.autoYes();
    const t = e.target.closest("[data-lane]");
    if (t) { lane = t.dataset.lane; picking = false; ticks.clear(); try { sessionStorage.setItem("bf-mg-lane", lane); } catch (err) { /* private window */ } return draw(); }
    const w = e.target.closest("[data-who]");
    if (w) { who = w.dataset.who; return draw(); }
    const k = e.target.closest("[data-kind]");
    if (k) { kind = k.dataset.kind; return draw(); }
    const b = e.target.closest("[data-mg]");
    if (!b) return;
    const act = b.dataset.mg, item = b.closest("[data-key]"), r = item ? all.find((x) => x.key === item.dataset.key) : null;
    if (act === "pickon") { picking = true; return draw(); }
    if (act === "pickoff") { picking = false; ticks.clear(); return draw(); }
    if (act === "pickall") { shown().filter((x) => x.lane === "needs").forEach((x) => ticks.add(x.key)); return draw(); }
    if (act === "tick" && r) { if (ticks.has(r.key)) ticks.delete(r.key); else ticks.add(r.key); return draw(); }
    if (act === "yesall" || act === "noall" || act === "everyyes") {
      const yes = act !== "noall";
      const todo = shown().filter((x) => x.lane === "needs" && (act === "everyyes" || ticks.has(x.key)));
      if (!todo.length) return;
      return B.busy(b, async () => {
        let reason = "";
        if (!yes) {
          const a = await mgAsk({ title: `Say no to ${todo.length}?`, text: "Pick one reason for all of them, or leave it empty. Each person sees it next to their request.", reasons: true, yes: `No to ${todo.length}`, back: "Go back" });
          if (!a) return "";
          reason = a.reason;
        }
        let ok = 0;
        for (const x of todo) {
          try { await answer(x, yes); if (!yes && x.src === "seerr") await saveReason(x.key, reason); ok++; } catch (err) { /* the count below says so */ }
        }
        ticks.clear(); picking = false;
        await reload();
        return ok === todo.length ? (yes ? `Said yes to ${ok}. Tower starts looking now.` : `Said no to ${ok}.`) : `${ok} of ${todo.length} done. Try the rest again.`;
      });
    }
    if (!r) return;
    B.busy(b, async () => {
      let msg = "";
      if (act === "yes") { await answer(r, true); msg = "Said yes. Tower starts looking now."; }
      if (act === "no") { await answer(r, false); if (r.src === "seerr") await B.whyNo(r.key); msg = "Said no."; }
      if (act === "again") msg = await again(r);
      if (act === "undo") {
        const a = await B.call("POST", "api/asks/undo", { id: r.id }).catch(() => null);
        if (!a) await B.jf("POST", `/JellyfinEnhanced/arr/requests/${r.id}/approve`);
        msg = (a && a.message) || "Back to a yes. Tower starts looking now.";
      }
      if (act === "stop") {
        const seerrRow = r.src === "seerr";
        const a = await mgAsk({ title: `Stop ${r.title}?`, text: seerrRow ? `Tower stops looking and ${r.who} sees it as a no. Pick a reason for ${r.who}, or leave it empty.` : `Tower stops looking for it and it leaves the list.`,
          reasons: seerrRow, del: seerrRow ? "Also delete what already arrived" : "", yes: "Stop it", back: "Keep looking" });
        if (!a) return "";
        if (seerrRow) {
          const done = await B.call("POST", "api/asks/stop", { id: r.id, delete: a.del });
          await saveReason(r.key, a.reason);
          msg = (done && done.message) || "Stopped.";
        } else msg = await stop(r);
      }
      await reload();
      return msg;
    });
  };
  rows.innerHTML = `<div class="wrap">${B.spinner("list")}</div>`;
  await load();
  if (!B.alive(nav)) return;
  draw();
  B.every(20000, () => { if (!picking) reload().catch(() => {}); });
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

// Glowfin new look (Requests > Music and Books, Ben approved 2026-11-22): the cover you point at stands at the top
// right with its own colours glowing behind the page, its name, facts and status on the left (like the Music tab)
B.rqCoverTop = (tall) => B.coverGlowHtml() + `<section class="mtop rqctop${tall ? " tall" : ""}" id="rqTop"><div class="art"></div><div class="info"></div></section>`;
B.wireRqCoverTop = (describe, coverOf) => {
  const top = $("#rqTop");
  if (!top) return () => {};
  let timer, shown = null;
  const show = (it) => {
    if (!it || it === shown) return;
    shown = it;
    $(".info", top).innerHTML = describe(it);
    const c = coverOf(it), art = $(".art", top);
    art.classList.remove("on");
    art.innerHTML = c ? `<span class="one" style="background-image:url('${esc(c)}')"></span>` : "";
    B.coverGlow(c || null);
    requestAnimationFrame(() => art.classList.add("on"));
  };
  const pointAt = (e) => {
    const card = e.target.closest("#rows [data-id]");
    if (!card || !B.items[card.dataset.id]) return;
    clearTimeout(timer);
    timer = setTimeout(() => show(B.items[card.dataset.id]), shown ? 350 : 0);
  };
  B.page().addEventListener("mouseover", pointAt);
  B.page().addEventListener("focusin", pointAt);
  B.onLeave(() => { B.page().removeEventListener("mouseover", pointAt); B.page().removeEventListener("focusin", pointAt); });
  B.wireCoverGlow();
  return show;
};
B.rqPillsRow = (title, pills) => `<section class="rowx"><h3>${esc(title)}</h3><div class="row rqpillrow">${pills.join("")}</div></section>`;
// Redesign 2: Music sits on the same side list and wall as the other tabs. Grown-up family profiles can ask
// too; their song and album asks wait for Ben's Approve before anything downloads.
B.musicTab = async (nav) => {
  const admin = B.me.admin;
  $("#rq").placeholder = "Search any song, album or artist";
  const rows = $("#rows");
  rows.innerHTML = `<div id="mfound"></div><div id="msplit">${B.spinner("rows")}</div>`;
  let searching = false, tidyFolder = null, disc = null, st = null, picked = B.q("mlist") || null, described = null;
  // Ben 2026-11-15: Top 100, then Trending (one list per genre), then a few "Because you have <song>" lists that
  // Tower picks again every time the page opens, then More.
  const lists = () => {
    const d = disc || {};
    const songs = d.songs || [];
    const trend = (d.trending || []).map((t) => ({ id: "trend-" + t.id, title: "Trending in " + t.genre, short: t.genre, heading: "Trending", songs: t.songs || [] }));
    const because = (d.because || []).map((b, i) => ({ id: "because-" + i, title: "Because you have " + (b.song || b.artist), short: b.song || b.artist, heading: "Because you have", songs: b.songs || [] }));
    const more = trend.length || because.length ? "More" : null;
    return [
      songs.length && { id: "top", title: "Top 100 songs", songs: songs.filter((x) => !x.have).slice(0, 100).concat(songs.filter((x) => x.have)), ranked: true },
      !trend.length && songs.length > 10 && { id: "trending", title: "Trending", songs: songs.slice(10) },
      ...trend,
      ...because.filter((b) => b.songs.length),
      (d.new || []).length && { id: "new", title: "New this week", albums: d.new, heading: more },
      (d.genres || []).length && { id: "genres", title: "Genres", genres: d.genres, heading: more },
      admin && { id: "playlist", title: "Import a playlist", heading: more },
    ].filter(Boolean);
  };
  const reqLine = (r, answer) => ({
    title: r.title, sub: [admin ? r.who : "", r.artist, r.kind].filter(Boolean).join(" · "), cover: r.cover, square: true, pill: B.musicPill(r), why: r.why || "", who: answer && admin ? r.who || "" : "",
    acts: answer ? `<button class="btn small white" data-myes="${esc(r.id)}">Yes</button><button class="btn small" data-mno="${esc(r.id)}">No</button>`
      : `${r.can_retry && admin ? `<button class="btn small white" data-mretry="${r.id}">Try again</button>` : ""}<button class="btn small" data-mremove="${r.id}">${r.state === "ready" ? "Done" : "Remove"}</button>`,
  });
  // Glowfin new look (Ben approved 2026-11-22): Music lands straight on rows of square covers. Top 100 songs (the
  // first ten numbered), Trending in each genre, Because you have, New this week, Editors' picks, Artists (round),
  // then Genres and Bring in a playlist (Ben) pills, and Your music asks. Your asks and the playlist tools open in
  // place of the rows, with a Back pill.
  const tagged = (html, key, it) => { B.items[key] = it; return html.replace('<button class="poster"', `<button class="poster" data-id="${key}"`); };
  const mSong = (x) => tagged(songCard(x), "mus:" + x.id, Object.assign({ _k: "song" }, x));
  const mAlbum = (x) => tagged(albumCard(x), "mal:" + x.id, Object.assign({ _k: "album" }, x));
  const mArtist = (x) => tagged(artistCard(x), "mar:" + x.id, Object.assign({ _k: "artist" }, x));
  const describe = (it) => {
    const len = it.seconds ? `${Math.floor(it.seconds / 60)}:${String(it.seconds % 60).padStart(2, "0")}` : "";
    const facts = it._k === "song" ? [it.artist, it.album, len] : it._k === "album" ? [it.artist, it.year, it.type === "single" ? "Single" : it.type === "ep" ? "EP" : "Album"] : ["Artist"];
    const tag = it._k === "artist" ? "Click to see their songs and albums" : it.have ? "In your music" : it.asked ? B.musicPill({ words: it.asked }).w : it._k === "song" ? "Click to hear 30 seconds and ask" : "Not in your music yet";
    return `<h1>${esc(it.title || it.name || "")}</h1><p>${esc(facts.filter(Boolean).join(" · "))}</p><span class="mtop-kick">${esc(tag)}</span>`;
  };
  const coverOf = (it) => it.cover || it.picture || "";
  const draw = () => {
    const box = $("#msplit");
    if (!box) return;
    const reqs = (st && st.requests) || [];
    const waiting = admin ? reqs.filter((r) => r.waiting) : [];
    const mine = reqs.filter((r) => !admin || !r.waiting);
    const notes = ((admin && st && st.problems) || []).map((p) => `<div class="note yellow">${esc(p)}</div>`).join("");
    const back = `<div class="wrap" style="padding-bottom:0"><button class="chip" data-rqpick="">${B.I("left")} Back to music</button></div>`;
    if (picked === "yours" || picked === "waiting") {
      const w = picked === "waiting";
      box.innerHTML = back + `<div class="wrap">${notes}<div class="rqhead"><b>${w ? "Waiting for you" : "Your music asks"}</b><span>${w ? waiting.length : mine.length}</span></div>`
        + (w || admin ? "" : `<p class="dim">Ben says yes before anything downloads.</p>`)
        + B.rqLines((w ? waiting : mine).map((r) => reqLine(r, w)), !w, w ? "Nobody is waiting on you right now." : "Nothing asked for yet. Pick a song or album and ask for it.") + "</div>";
      return;
    }
    if (picked === "playlist") { box.innerHTML = back + `<div class="wrap">${notes}<div id="mstatus">${B.spinner()}</div></div>`; drawTools(); return; }
    if (!disc) { box.innerHTML = notes + B.spinner("rows"); return; }
    const songsRow = (l) => {
      const left = l.songs.filter((x) => !x.have);
      return B.row(l.title, l.ranked ? left.map((x, i) => (i < 10 ? `<div class="top10">${B.t10num(i + 1)}${mSong(x)}</div>` : mSong(x))) : left.map(mSong));
    };
    const ls = lists();
    box.innerHTML = notes + ls.filter((l) => l.songs).map(songsRow).join("")
      + B.row("New this week", (disc.new || []).map(mAlbum)) + B.row("Editors' picks", (disc.picks || []).map(mAlbum))
      + B.row("Artists", (disc.artists || []).map(mArtist))
      + ((disc.genres || []).length ? B.rqPillsRow("Genres", disc.genres.map((g) => `<button class="chip" data-mgenre="${g.id}">${esc(g.name)}</button>`)) : "")
      + (admin ? B.rqPillsRow("Bring in a playlist", [`<button class="chip on" data-rqpick="playlist">Bring in a playlist ›</button>`]) : "")
      + B.rqPillsRow("Your music asks", [`<button class="chip" data-rqpick="yours">Asked for <b>${mine.length}</b></button>`].concat(admin ? [`<button class="chip" data-rqpick="waiting">Waiting for you <b>${waiting.length}</b></button>`] : []));
    if (!described) {
      described = B.wireRqCoverTop(describe, coverOf);
      const first = $("#msplit [data-id]");
      if (first) described(B.items[first.dataset.id]);
    }
  };
  const drawDiscover = async (again) => {
    if (!disc || again) {
      const old = disc;
      try { disc = await music("GET", "/discover"); } catch (e) { if (B.alive(nav) && !disc) $("#msplit").innerHTML = `<div class="wrap"><p class="dim">Couldn't load what's popular right now. ${esc(B.say(e))}</p></div>`; return; }
      // Tower picks new "Because you have" songs on every look: after an ask keep the lists on screen as they were
      // (the list you were in used to change under you), with the song just asked for marked
      if (again && old && old.because) disc.because = old.because;
    }
    if (B.alive(nav) && !searching) draw();
  };
  const loadStatus = async () => {
    try { st = await music("GET", "/status"); } catch (e) { if (B.alive(nav) && !st) B.toast(B.say(e)); return; }
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
    box.innerHTML = `<div class="wrap">${B.spinner("list")}</div>`;
    let r;
    try { r = await music("GET", "/search?q=" + enc(text.trim())); } catch (e) { box.innerHTML = `<div class="wrap"><div class="note">${esc(B.say(e))}</div></div>`; return; }
    if (!B.alive(nav) || $("#rq").value.trim() !== text.trim()) return;
    const none = !r.songs.length && !r.albums.length && !r.artists.length;
    box.innerHTML = (r.songs.length ? `<div class="wrap"><h3 class="section-title">Songs</h3><div class="list">${r.songs.slice(0, 12).map((x) => songLine(x)).join("")}</div></div>` : "")
      + B.row("Albums", r.albums.map(albumCard)) + B.row("Artists", r.artists.map(artistCard))
      + (none ? `<div class="wrap empty">Nothing found. Try fewer words, or the artist's name.</div>` : "");
  };
  // Ben 2026-11-16: asking for a song from a Genres, album or artist panel used to close it, so he had to open it
  // again for every song. Now the panel he came from opens again, scrolled back to the same spot.
  let page = null;
  const closed = () => { page = null; };
  const fromPage = (el) => { const sd = el.closest(".side"); return sd && page ? { ...page, scroll: sd.scrollTop } : null; };
  const reopen = async (bk) => {
    if (!bk || !B.alive(nav)) return;
    const p = bk.kind === "genre" ? await openGenre(bk.id) : await openPage(bk.kind, bk.id, bk.back);
    if (p && p.isConnected) p.scrollTop = bk.scroll || 0;
  };
  // Ben 2026-11-16: an album opened from an artist's panel has a Back button to that artist
  let shownArtist = null;
  const openPage = async (kind, id, back) => {
    const p = B.side(B.spinner(), closed);
    page = { kind, id, back };
    if (kind !== "album") shownArtist = null;
    try {
      if (kind === "album") {
        const d = await music("GET", "/album?id=" + id);
        p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button>
          ${back ? `<button class="btn small" data-mback="${esc(back.id)}" style="margin-bottom:12px">${I("left")} ${esc(back.name)}</button>` : ""}
          <div style="width:180px;aspect-ratio:1;border-radius:12px;margin-bottom:14px;background:var(--pg3) center/cover url('${esc(d.album.cover || "")}')"></div>
          <h3>${esc(d.album.title)}</h3><p class="dim">${esc([d.album.artist, d.album.year].filter(Boolean).join(" · "))}</p>
          <div class="acts" style="justify-content:flex-start;margin:12px 0">${d.album.asked ? B.rqPillHtml(B.musicPill({ words: d.album.asked })) : `<button class="btn white" data-malbumonly="${d.album.id}" data-maname="${esc(d.album.title)}">${admin ? "Ask for the whole album" : "Ask Ben for the whole album"}</button>`}</div>
          <p class="dim">Or pick a song to ask for just that one.</p><div class="list">${d.songs.map((x) => songLine(x)).join("")}</div>`;
      } else {
        const d = await music("GET", "/artist?id=" + id);
        shownArtist = { id, name: d.artist.name };
        p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button><h3>${esc(d.artist.name)}</h3>
          ${d.songs.length ? `<h4 class="dim" style="margin:12px 0 8px">Best songs</h4><div class="list">${d.songs.map((x) => songLine(x)).join("")}</div>` : ""}
          <h4 class="dim" style="margin:12px 0 8px">Albums, EPs and singles</h4><div class="grid" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:12px">${d.albums.map(albumCard).join("")}</div>`;
      }
    } catch (e) {
      p.innerHTML = `<button class="circle x" data-close>${I("x")}</button><h3>Couldn't open that</h3><p class="dim">${esc(B.say(e))}</p>`;
    }
    return p;
  };
  const openGenre = async (id) => {
    const p = B.side(B.spinner(), closed);
    page = { kind: "genre", id };
    try {
      const d = await music("GET", "/genre?id=" + id);
      p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button><h3>${esc(d.genre.name)}</h3>
        ${d.songs.length ? `<h4 class="dim" style="margin:12px 0 8px">Top ${esc(d.genre.name)} songs</h4><div class="list">${d.songs.map((x, i) => songLine(x, i + 1)).join("")}</div>` : ""}
        ${[["New releases", d.new], ["Top albums", d.albums]].filter(([, l]) => l && l.length).map(([t, l]) => `<h4 class="dim" style="margin:12px 0 8px">${t}</h4><div class="grid" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:12px">${l.map(albumCard).join("")}</div>`).join("")}
        ${d.artists.length ? `<h4 class="dim" style="margin:12px 0 8px">Artists</h4><div class="grid" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:12px">${d.artists.map(artistCard).join("")}</div>` : ""}`;
    } catch (e) {
      p.innerHTML = `<button class="circle x" data-close>${I("x")}</button><h3>Couldn't open that</h3><p class="dim">${esc(B.say(e))}</p>`;
    }
    return p;
  };
  const ask = async (kind, id, name, bk) => {
    await music("POST", "/request", { kind, id: +id });
    B.previewStop();
    if (kind === "song" && disc) (disc.because || []).forEach((r) => (r.songs || []).forEach((x) => { if (String(x.id) === String(id)) x.asked = "Asked for"; }));
    B.closeAll();
    page = null;
    reopen(bk);
    const q = $("#rq").value;
    if (q.trim().length >= 2) search(q);
    loadStatus();
    drawDiscover(true);
    return admin ? `Asked for ${name}. It shows in Your requests as Asked.` : `Asked Ben for ${name}. He says yes before it downloads.`;
  };
  // One click handler for the whole page and its side panels
  const onClick = (e) => {
    if (!B.alive(nav)) return document.removeEventListener("click", onClick);
    const pick = e.target.closest("#msplit [data-rqpick]");
    if (pick) { picked = pick.dataset.rqpick || null; return draw(); }
    const b = e.target.closest("[data-msong],[data-malbumonly],[data-mopen],[data-mback],[data-mgenre],[data-mretry],[data-mremove],[data-myes],[data-mno],[data-mplremove],[data-mfolder],[data-mtidy],#mplaylist");
    if (!b) return;
    const d = b.dataset;
    if (d.msong) {
      // The one Ask for it panel: hear 30 seconds, then ask for the song or the whole album.
      // Closing it, or asking, goes back to the Genres/album/artist panel it was opened from.
      const bk = fromPage(b);
      const s = B.side(`<h3>${esc(d.mname)}</h3><p class="dim">${esc(d.maname)}</p><h4 style="margin:14px 0 8px">Ask for ${esc(d.mname)}</h4>
        ${admin ? "" : `<p class="dim">Ben says yes before it downloads.</p>`}
        <div class="acts" style="justify-content:flex-start;margin:4px 0 12px">${B.previewBtn(d.msong)}<span class="dim">Hear 30 seconds</span></div>
        <button class="opt" data-pick="song">${admin ? "Ask for this song" : "Ask Ben for this song"}</button><button class="opt" data-pick="album">${admin ? "Ask for the whole album" : "Ask Ben for the whole album"}</button>`, () => reopen(bk));
      s.addEventListener("click", (ev) => {
        const one = ev.target.closest("[data-pick]");
        if (one) B.busy(one, () => (one.dataset.pick === "song" ? ask("song", d.msong, d.mname, bk) : ask("album", d.malbum, d.maname, bk)));
      });
    } else if (d.malbumonly) { const bk = fromPage(b); B.busy(b, () => ask("album", d.malbumonly, d.maname, bk)); }
    else if (d.mopen) { const [k, id] = d.mopen.split(":"); openPage(k, id, k === "album" && b.closest(".side") && shownArtist ? shownArtist : null); }
    else if (d.mback) openPage("artist", d.mback);
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
        B.ask("Make a Glowfin playlist too?", "Songs you don't have yet are asked for either way. Pick Just the songs for someone else's playlist.", "Songs + playlist", "Just the songs").then((make) => {
          B.busy(null, async () => { const p = await music("POST", "/playlist", { link: link.trim(), playlist: !!make }); loadStatus(); return `Bringing in ${p.name}.`; });
        });
      });
    }
  };
  document.addEventListener("click", onClick);
  B.onLeave(() => document.removeEventListener("click", onClick));
  let t;
  $("#rq").oninput = (e) => { clearTimeout(t); t = setTimeout(() => search(e.target.value), 400); };
  B.every(10000, () => { if (!searching) loadStatus(); });
  drawDiscover();
  loadStatus();
  if (B.q("q")) search(B.q("q"));
  // Music redesign 3: an album or artist that isn't on Benflix, opened from an artist page (?mopen=album:123)
  const mo = /^(album|artist):(\d+)$/.exec(B.q("mopen") || "");
  if (mo) openPage(mo[1], mo[2]);
};

// ---------- Anime tab (Ben's anime Requests design, 2026-11-12) ----------
// Glowfin new look (2026-11-23): rows (This season first), see the note above draw() below.
// Lists come from AniList (first seasons only, nothing adult); a poster opens its Requests page, found by name.
// Shows already on Benflix are left out, with a small "12 already on Benflix" note.
B.animeRequests = (nav, getLists, openList) => {
  const SHOWS = ["TV", "TV_SHORT", "ONA"];
  const CARD = "id idMal format status episodes popularity averageScore meanScore isAdult genres seasonYear title { english romaji native } synonyms startDate { year } coverImage { large medium } bannerImage description(asHtml: false) nextAiringEpisode { episode } relations { edges { relationType node { id format } } }";
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
  const cleanName = (t) => String(t || "").toLowerCase().replace(/&/g, "and").replace(/[^\p{L}\p{N}]/gu, "");
  // Ben 2026-11-18: "Slime Season 2" belongs to the show "Slime" (same as the app's AniListService.seasonBase)
  const SEASON_TAIL = /[\s:,\-]*\(?\s*\b(?:(?:season|part|cour)\s*(?:\d+|[ivx]+)|\d+(?:st|nd|rd|th)\s+season|(?:second|third|fourth|fifth|sixth|final)\s+season)\s*\)?\s*$/i;
  const seasonBase = (t) => { const start = String(t || "").trim(); let b = start; for (let i = 0; i < 2; i++) b = b.replace(SEASON_TAIL, "").trim(); return b.length >= 3 && b !== start ? b : null; };
  const baseNames = (titles) => titles.map(seasonBase).filter(Boolean).map(cleanName).filter((n) => n.length >= 3);
  const al = async (query, variables) => {
    const r = await fetch("https://graphql.anilist.co", { method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify({ query, variables }) });
    if (!r.ok) throw new Error("AniList " + r.status);
    return (await r.json()).data || {};
  };
  const titlesOf = (m) => [m.title && m.title.english, m.title && m.title.romaji, m.title && m.title.native].concat(m.synonyms || []).filter((x) => x && x.trim()).filter((x, i, a) => a.indexOf(x) === i);
  const cardOf = (m) => {
    if (!m || m.isAdult || (m.genres || []).includes("Hentai")) return null;
    // Some later seasons only follow an OVA on AniList (Slime Season 2), so a "Season 2" name with anything before it counts too
    const named = titlesOf(m).some((t) => seasonBase(t));
    const later = SHOWS.includes(m.format) && ((m.relations && m.relations.edges) || []).some((e) => e.relationType === "PREQUEL" && e.node && (SHOWS.includes(e.node.format) || named));
    return { id: m.id, idMal: m.idMal, titles: titlesOf(m), format: m.format, year: (m.startDate && m.startDate.year) || m.seasonYear, score: m.averageScore || m.meanScore,
      episodes: m.episodes, next: m.nextAiringEpisode && m.nextAiringEpisode.episode, status: m.status, cover: m.coverImage && (m.coverImage.large || m.coverImage.medium), later,
      banner: m.bannerImage || null, story: String(m.description || "").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim() };
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
    trending: () => ({ key: "trending-anilist", sort: ["TRENDING_DESC"], formats: SHOWS, pages: 2 }),
    mal: () => ({ key: "trending-mal", mal: true }),
    gems: () => ({ key: "gems", sort: ["SCORE_DESC"], formats: SHOWS.concat("MOVIE"), scoreOver: 79, popUnder: 50000, popOver: 2000 }),
    dubbed: () => ({ key: "finished", status: "FINISHED", formats: SHOWS, pages: 4 }),
    movies: () => ({ key: "movies", formats: ["MOVIE"] }),
    next: () => { const n = step(NOW, 1); return { key: "next-" + n.join("-"), season: n[0], seasonYear: n[1], formats: SHOWS.concat("MOVIE") }; },
    feel: (f) => Object.assign({ key: "feel-" + f[0], formats: SHOWS.concat("MOVIE") }, f[3], { noGenres: (f[3].noGenres || []).concat("Ecchi") }),
  };
  // Glowfin new look (Ben 2026-11-22): Trending on MyAnimeList too (Jikan's top anime airing now), matched to AniList cards
  const malTrending = () => B.cached("anime-req2-trending-mal", 6 * 3600000, async () => {
    const r = await fetch("https://api.jikan.moe/v4/top/anime?filter=airing&limit=25");
    if (!r.ok) throw new Error("MyAnimeList " + r.status);
    const mal = ((await r.json()).data || []).map((x) => x.mal_id).filter(Boolean);
    if (!mal.length) return [];
    const p = (await al(`query ($ids: [Int]) { Page(page: 1, perPage: 50) { media(type: ANIME, isAdult: false, idMal_in: $ids) { ${CARD} } } }`, { ids: mal })).Page;
    const by = new Map(((p && p.media) || []).map(cardOf).filter(Boolean).map((c) => [c.idMal, c]));
    return mal.map((id) => by.get(id)).filter(Boolean);
  });
  const load = (a) => a.mal ? malTrending() : B.cached("anime-req2-" + a.key, 6 * 3600000, async () => {
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
    // shows: "al:<id>" / "n:<clean name>" -> { tmdb, series }, to check a show here for missing episodes
    const o = { ids: new Set(), mal: new Set(), names: new Set(), shows: new Map() };
    for (const l of libs) {
      const r = await B.get(`/Items?userId=${B.uid()}&ParentId=${l.Id}&IncludeItemTypes=Series,Movie&Recursive=true&Fields=ProviderIds,OriginalTitle&EnableImages=false&EnableUserData=false`).catch(() => ({}));
      (r.Items || []).forEach((x) => {
        const p = x.ProviderIds || {};
        const show = { tmdb: p.Tmdb ? +p.Tmdb : null, series: x.Type === "Series" };
        [x.Name, x.OriginalTitle].forEach((n) => { const c = cleanName(n); if (c) { o.names.add(c); if (!o.shows.has("n:" + c)) o.shows.set("n:" + c, show); } });
        if (p.AniList && !o.shows.has("al:" + p.AniList)) o.shows.set("al:" + p.AniList, show);
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
    if (baseNames(c.titles).some((n) => o.names.has(n))) return true;
    return c.later && names.some((t) => [...o.names].some((n) => n.length >= 5 && t.startsWith(n)));
  };
  // The show on Benflix a poster belongs to (only for posters has() says are here)
  const showOf = (o, c) => o && (o.shows.get("al:" + c.id) || c.titles.map(cleanName).concat(baseNames(c.titles)).map((n) => o.shows.get("n:" + n)).find(Boolean));
  // Ben 2026-11-18: shows here that are missing aired episodes come back, tagged "Missing episodes", like the rest of Requests
  const bringBackMissing = async (items, grid, isLive) => {
    const todo = items.filter((c) => c.format !== "MOVIE" && has(own, c)).map((c) => [c, showOf(own, c)]).filter(([, s]) => s && s.series && s.tmdb);
    if (!todo.length) return;
    await B.libTmdb().catch(() => null);
    let i = 0;
    const next = async () => {
      while (i < todo.length) {
        const [c, s] = todo[i++];
        const whole = await B.showComplete(s.tmdb).catch(() => true);
        if (whole || !isLive() || !grid.isConnected) continue;
        c._missing = true; c._tmdb = s.tmdb;
        const box = document.createElement("div");
        box.innerHTML = poster(c, 0);
        grid.append(...box.childNodes);
      }
    };
    await Promise.all([next(), next(), next()]);
  };

  let feel = null, must = [], leave = [], own = null, dub = null, tags = null;

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
      <div class="im" ${c.cover ? `style="background-image:url('${esc(c.cover)}')"` : ""}>${c.cover ? "" : `<div class="noart"><span class="nt">${esc(c.titles[0] || "")}</span></div>`}${c._missing ? '<span class="newp miss">Missing episodes</span>' : ""}${badge(c)}${rank ? `<span class="rqrank anb-rank">${rank}</span>` : c.format === "MOVIE" ? '<span class="anb anb-kind">Movie</span>' : ""}</div>
      <div class="cap">${esc(c.titles[0] || "")}<small>${esc(airing(c) || c.year || "")}</small></div></button>`;
  };
  // Glowfin new look (Ben approved 2026-11-22): Anime lands straight on a row, like Movies and Shows. The top
  // describes the anime you point at, with its AniList banner behind. Rows: This season, Top 10 this week, This
  // week in anime, Trending on AniList, Trending on MyAnimeList, Moods (pills, with that mood's row under them),
  // Mix and match tags, Finished and dubbed, Hidden gems, Anime movies, Coming next season, Last season.
  const anDescribe = (c) => {
    if (!c) return "";
    const bits = [c.format === "MOVIE" ? "Anime movie" : "Anime", c.year, c.format !== "MOVIE" && c.episodes ? c.episodes + " episodes" : "",
      dub && dub.size ? (c.idMal && dub.has(c.idMal) ? "Sub+Dub" : "Sub") : "", c.score ? "AniList " + c.score + "%" : ""].filter(Boolean);
    const air = airing(c);
    return `<div class="dt-name"><div class="t">${esc(c.titles[0] || "")}</div></div>
      <div class="meta">${bits.map((b) => `<span>${esc(String(b))}</span>`).join("")}</div>
      <div class="row" style="gap:8px">${c._missing ? '<div class="dt-tag rqtag t-nyet">Missing episodes</div>' : '<div class="dt-tag rqtag t-nyet">Not on Glowfin yet</div>'}${air ? `<div class="dt-tag rqtag t-way">Airing now · ${esc(air)}</div>` : ""}</div>
      <div class="ov">${esc(c.story || "")}</div><div class="rqhint">Click it to open it and ask for it</div>`;
  };
  const anArt = (c) => (c && (c.banner || c.cover)) || "";
  const card = (c, rank, caption) => {
    B.items["an:" + c.id] = c;
    const html = poster(c, 0).replace('<button class="poster ancard"', `<button class="poster ancard" data-id="an:${c.id}"`);
    const withCap = caption ? html.replace(/<small>[^<]*<\/small>/, `<small>${esc(caption)}</small>`) : html;
    return rank ? `<div class="top10">${B.t10num(rank)}${withCap}</div>` : withCap;
  };
  const rowBox = (key) => `<div id="anr-${key}">${""}</div>`;
  const fillRow = async (key, a, title, note, opts) => {
    opts = opts || {};
    const box = $("#anr-" + key);
    if (!box) return;
    box.innerHTML = `<section class="rowx"><h3>${esc(title)}</h3>${B.spinner()}</section>`;
    let items;
    try { items = await load(a); } catch (e) {
      if (box.isConnected) box.innerHTML = `<section class="rowx"><h3>${esc(title)}</h3><div class="wrap empty">These didn't load. AniList may be busy. <button class="btn small" data-an-retry>Try again</button></div></section>`;
      return;
    }
    if (!B.alive(nav) || !box.isConnected) return;
    if (opts.keep) items = items.filter(opts.keep);
    const shown = items.filter((c) => !has(own, c)), here = items.length - shown.length;
    const list = opts.ranked ? shown.slice(0, 10) : shown;
    if (opts.onShown) opts.onShown(list);
    const name = title + (note || here ? ` <span class="dim" style="font-size:.7em;font-weight:500">${esc([note, here ? `${here} already on Glowfin` : ""].filter(Boolean).join(" · "))}</span>` : "");
    box.innerHTML = list.length
      ? B.row(title, list.map((c, i) => card(c, opts.ranked ? i + 1 : 0))).replace(`<h3>${esc(title)}`, `<h3>${name}`)
      : `<section class="rowx"><h3>${name}</h3><div class="wrap empty">${here ? "You already have all of these on Glowfin." : "Nothing here right now."}</div></section>`;
    if (!described && list[0] && key === "season") { described = true; B.wireDescribe(list[0], { describe: anDescribe, art: anArt }); }
    if (here && !opts.ranked) {
      const strip = $(".strip", box);
      if (strip) bringBackMissing(items.filter((c) => has(own, c)), strip, () => B.alive(nav) && strip.isConnected);
    }
  };
  const fillWeek = async () => {
    const box = $("#anr-week");
    if (!box) return;
    const title = "This week in anime";
    box.innerHTML = `<section class="rowx"><h3>${title}</h3>${B.spinner()}</section>`;
    let all;
    try { all = await week(); } catch (e) {
      if (box.isConnected) box.innerHTML = `<section class="rowx"><h3>${title}</h3><div class="wrap empty">The calendar didn't load. AniList may be busy. <button class="btn small" data-an-retry>Try again</button></div></section>`;
      return;
    }
    if (!B.alive(nav) || !box.isConnected) return;
    const now = Date.now() / 1000, today = new Date(); today.setHours(0, 0, 0, 0);
    const seen = new Set();
    const list = all.filter((a) => !has(own, a.card) && a.at >= now - 6 * 3600).sort((x, y) => x.at - y.at).filter((a) => !seen.has(a.card.id) && seen.add(a.card.id));
    const when = (a) => {
      const d = new Date(a.at * 1000), dd = new Date(d); dd.setHours(0, 0, 0, 0);
      const n = Math.round((dd - today) / 86400000);
      const day = n === 0 ? "Today" : n === 1 ? "Tomorrow" : d.toLocaleDateString("en-US", { weekday: "short" });
      return `${day} ${d.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }).toLowerCase()} · Ep ${a.ep}`;
    };
    box.innerHTML = list.length ? B.row(title, list.map((a) => card(a.card, 0, when(a))))
      : `<section class="rowx"><h3>${title}</h3><div class="wrap empty">Nothing new this week.</div></section>`;
  };
  const recipe = (f) => { const m = (f[3].genres || []).concat(f[3].tags || []).join(" + "), n = (f[3].noGenres || []).concat(f[3].noTags || []); return n.length ? m + " · " + n.map((x) => "no " + x).join(", ") : m; };
  const feelsOk = () => FEELS.filter((f) => !tags || (f[3].tags || []).every((t) => tags.some((x) => x.name === t)));
  const drawMoods = () => {
    const box = $("#anr-moods");
    if (!box) return;
    const fs = feelsOk();
    if (!feel || !fs.includes(feel)) feel = fs[0] || null;
    box.innerHTML = `<section class="rowx"><h3>Moods</h3><div class="row an-moods" style="padding:0 var(--gutter);gap:8px;flex-wrap:wrap">${fs.map((f) => `<button class="chip ${f === feel ? "on" : ""}" data-an-feel="${esc(f[0])}" style="--c:${f[2]}">${esc(f[0])}</button>`).join("")}</div></section>`;
    if (feel) fillRow("mood", asks.feel(feel), "Mood: " + feel[0], recipe(feel));
  };
  let topIds = new Set();
  // Mix and match tags: a side panel. Click a tag to need it, again to leave it out, a third time to clear it.
  const openTags = async () => {
    const p = B.side(`<h3>Mix and match tags</h3>${B.spinner()}`);
    if (!tags) { try { tags = await tagList(); } catch (e) { tags = []; } }
    let at = 0;
    const paint = async () => {
      const mine = ++at;
      const state = (n) => must.includes(n) ? "on" : leave.includes(n) ? "out" : "";
      const chip = (n) => `<button class="chip antag ${state(n)}" data-an-tag="${esc(n)}">${leave.includes(n) ? "No " : ""}${esc(n)}</button>`;
      const groups = {};
      tags.forEach((t) => { const g = (t.category || "Other").split("-")[0]; (groups[g] = groups[g] || []).push(t.name); });
      const picked = must.length || leave.length;
      p.innerHTML = `<button class="circle x" data-close title="Close">${I("x")}</button><h3>Mix and match tags</h3><p class="dim">Click a tag to need it. Click again to leave it out. A third click clears it.</p>`
        + (picked ? `<div class="row" style="margin-bottom:10px;flex-wrap:wrap;gap:6px">${must.concat(leave).map(chip).join("")}<button class="chip" data-an-clear>Clear all</button></div><div id="anmix"></div>` : "")
        + `<details class="antags" ${picked ? "" : "open"}><summary>Genres and tags</summary><h4>Genres</h4><div class="row" style="flex-wrap:wrap;gap:6px">${GENRES.map(chip).join("")}</div>`
        + Object.keys(groups).sort().map((g) => `<h4>${esc(g)}</h4><div class="row" style="flex-wrap:wrap;gap:6px">${groups[g].sort().map(chip).join("")}</div>`).join("") + `</details>`;
      if (!picked) return;
      const box = $("#anmix", p);
      const a = { key: "mix-" + must.join(",") + "|" + leave.join(","), genres: must.filter((n) => GENRES.includes(n)), tags: must.filter((n) => !GENRES.includes(n)), noGenres: leave.filter((n) => GENRES.includes(n)), noTags: leave.filter((n) => !GENRES.includes(n)), formats: SHOWS.concat("MOVIE"), pages: 2 };
      box.innerHTML = B.spinner();
      let items;
      try { items = await load(a); } catch (e) { if (mine === at) box.innerHTML = `<div class="empty">That didn't load. AniList may be busy.</div>`; return; }
      if (mine !== at || !box.isConnected) return;
      const shown = items.filter((c) => !has(own, c)), here = items.length - shown.length;
      box.innerHTML = `<p class="dim">${shown.length} to ask for${here ? ` · ${here} already on Glowfin` : ""}</p><div class="grid">${shown.map((c) => card(c, 0)).join("")}</div>`;
    };
    paint();
    p.addEventListener("click", (e) => {
      const a = e.target.closest("[data-an]");
      if (a) { const c = B.animeCards[a.dataset.an]; if (c) open(c, a); return; }
      const g = e.target.closest("[data-an-tag]");
      if (g) {
        const n = g.dataset.anTag;
        if (must.includes(n)) { must = must.filter((x) => x !== n); leave.push(n); } else if (leave.includes(n)) leave = leave.filter((x) => x !== n); else must.push(n);
        return paint();
      }
      if (e.target.closest("[data-an-clear]")) { must = []; leave = []; paint(); }
    });
  };
  let described = false;
  const draw = () => {
    const ROWS = ["season", "top", "week", "tr-al", "tr-mal", "moods", "mood", "tags", "dubbed", "gems", "movies", "next", "last"];
    $("#rows").innerHTML = ROWS.map(rowBox).join("");
    $("#anr-tags").innerHTML = `<section class="rowx"><h3>Mix and match tags</h3><div class="row" style="padding:0 var(--gutter);gap:8px"><button class="chip on" data-an-tags>Every genre and tag ›</button></div></section>`;
    fillRow("season", asks.season(0), "This season", sLabel(SEASONS[0]) + " · airing now");
    fillRow("top", asks.top(), "Top 10 this week", "What anime fans are watching most", { ranked: true, onShown: (l) => { topIds = new Set(l.map((c) => c.id)); } })
      .then(() => fillRow("tr-al", asks.trending(), "Trending on AniList", "Right after the Top 10", { keep: (c) => !topIds.has(c.id) }));
    fillWeek();
    fillRow("tr-mal", asks.mal(), "Trending on MyAnimeList", "Top anime airing now");
    drawMoods();
    if (dub && dub.size) fillRow("dubbed", asks.dubbed(), "Finished and dubbed", "Every episode out, with English voices", { keep: (c) => c.idMal && dub.has(c.idMal) });
    fillRow("gems", asks.gems(), "Hidden gems", "Scores of 8 and up that few people have found");
    fillRow("movies", asks.movies(), "Anime movies", "");
    fillRow("next", asks.next(), "Coming next season", sLabel(step(NOW, 1)));
    fillRow("last", asks.season(1), "Last season", sLabel(SEASONS[1]));
  };
  // Opens a title's Requests page: AniList and Requests don't share numbers, so it's found by its names and year
  const open = async (c, btn) => {
    if (btn) btn.classList.add("busy");
    const want = c.isMovie || c.format === "MOVIE" ? "movie" : "tv";
    // A show here but missing episodes: straight to its own request page
    if (c._tmdb) { if (btn) btn.classList.remove("busy"); if (B.alive(nav)) B.go(`#/ask/tv/${c._tmdb}`); return; }
    const wanted = new Set(c.titles.map(cleanName).filter(Boolean));
    const baseWanted = new Set(baseNames(c.titles));
    const yearOk = (x, slack) => { const y = +((x.releaseDate || x.firstAirDate || "").slice(0, 4)); return !y || !c.year || Math.abs(y - c.year) <= slack; };
    const nameOf = (x) => cleanName(x.title || x.name || x.originalName || x.originalTitle);
    // Tower's anime id list first (AniList / MyAnimeList -> TMDB), so it never opens a look-alike like the live-action One Piece
    const al = c.id > 0 ? c.id : null, mal = c.idMal || (c.id < 0 ? -c.id : null);
    if (al || mal) {
      const ids = await B.call("GET", `api/anime/tmdb?${al ? "anilist=" + al : ""}${al && mal ? "&" : ""}${mal ? "mal=" + mal : ""}`).catch(() => null);
      const key = (ids && ((al && ids.anilist && ids.anilist[al]) || (mal && ids.mal && ids.mal[mal]))) || "";
      const [kind, tmdb] = key.split(":");
      if (tmdb && (kind === "movie" || kind === "tv")) {
        if (btn) btn.classList.remove("busy");
        if (B.alive(nav)) B.go(`#/ask/${kind}/${tmdb}`);
        return;
      }
    }
    let found = null;
    const latin = (t) => /[A-Za-z0-9]/.test(t);
    const tries = [...new Set(c.titles.filter(latin).slice(0, 3).concat(c.titles.map(seasonBase).filter((t) => t && latin(t)).slice(0, 2)))];
    for (const n of tries) {
      const r = await seerr(`/search?query=${enc(n.replace(/[:!?]/g, " ").trim())}&page=1`).catch(() => null);
      // only cartoons (TMDB genre 16), never a live-action version with the same name
      const res = ((r && r.results) || []).filter((x) => x.mediaType === want && (x.genreIds || []).includes(16));
      found = res.find((x) => wanted.has(nameOf(x)) && yearOk(x, 1))
        || res.find((x) => { const m = nameOf(x); return m.length >= 4 && [...wanted].some((w) => w.startsWith(m) || m.startsWith(w)) && yearOk(x, 2); })
        || res.find((x) => wanted.has(nameOf(x)))
        // a later season: the show it belongs to started the same year or before
        || res.find((x) => { const y = +((x.releaseDate || x.firstAirDate || "").slice(0, 4)); return baseWanted.has(nameOf(x)) && (!y || !c.year || y <= c.year); });
      if (found) break;
    }
    if (btn) btn.classList.remove("busy");
    if (!B.alive(nav)) return;
    if (found) B.go(`#/ask/${want}/${found.id}`);
    // Ben 2026-11-18: never a dead end. Search shows it with everything to ask for
    else { const n = c.titles.find(latin) || c.titles[0] || ""; B.go(`#/search?q=${enc(seasonBase(n) || n)}`); }
  };

  $("#rows").onclick = (e) => {
    const t = e.target;
    const a = t.closest("[data-an]");
    if (a) { const c = B.animeCards[a.dataset.an]; if (c) open(c, a); return; }
    const f = t.closest("[data-an-feel]");
    if (f) { feel = FEELS.find((x) => x[0] === f.dataset.anFeel) || null; return drawMoods(); }
    if (t.closest("[data-an-tags]")) return openTags();
    if (t.closest("[data-an-retry]")) {
      Object.keys(B.cache).filter((k) => k.startsWith("anime-")).forEach((k) => { delete B.cache[k]; });
      draw();
    }
  };
  // Dubs, tags and what's on Glowfin first (so owned shows stay out of every row), then the rows
  Promise.all([owned().catch(() => null), dubs().catch(() => null), tagList().catch(() => null)]).then(([o, d, t]) => {
    own = o; dub = d; tags = t;
    if (B.alive(nav)) draw();
  });
  $("#rows").innerHTML = B.spinner("rows");
  return { drawSide: () => {} };
};
