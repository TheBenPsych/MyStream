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
    <p class="sub">Movies and shows that aren't on Benflix yet. Ask, and Ben's server finds them.</p>
    <div class="row" style="margin-bottom:10px">${[["movies", "Movies"], ["shows", "Shows"], ["anime", "Anime"]].map(([k, l]) => `<a class="chip ${k === tab ? "on" : ""}" href="#/requests?tab=${k}">${l}</a>`).join("")}
      <input class="field" id="rq" style="max-width:420px;padding:9px 16px;font-size:16px;border-radius:99px" placeholder="Search for something to ask for" value="${esc(B.q("q") || "")}"></div></div>
    <div class="rows" id="rows">${B.spinner()}</div>`;
  B.wireTop();
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
