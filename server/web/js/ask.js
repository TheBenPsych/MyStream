/* Benflix web: a movie or show that isn't on Benflix yet, on the shared title page frame (Redesign 2,
   section 1). Same buttons and tabs as the TV and phone app:
   Buttons: Ask for it (or where the ask is: waiting, on its way, Watch now), Ask for the rest, Trailer
   (greyed out when there isn't one), More.  Tabs: Overview (who asked, with Yes / No for Ben), More to
   ask for, Cast.  "Ask for it" opens the usual ask panel (with the season picker for shows). */
"use strict";

(() => {
  B.route(/^\/ask\/(movie|tv)\/(\d+)/, async (m, nav) => {
    const [, type, id] = m;
    B.page().innerHTML = B.top("") + B.spinner();
    B.wireTop();
    let d;
    try { d = await B.seerr(`/${type}/${id}`); } catch (e) {
      B.page().innerHTML = B.top("") + `<div class="wrap"><div class="note">Couldn't open that one. ${esc(e.message || "")}</div></div>`;
      B.wireTop();
      return;
    }
    if (!B.alive(nav)) return;
    const isMovie = type === "movie";
    const t = d.title || d.name || "";
    const year = (d.releaseDate || d.firstAirDate || "").slice(0, 4);
    const status = d.mediaInfo ? d.mediaInfo.status : 1;
    const jfId = d.mediaInfo && d.mediaInfo.jellyfinMediaId;
    const kids = !!(B.me && B.me.kids);
    const admin = !!(B.me && B.me.admin);
    const here = status === 5 || status === 4;
    const pending = ((d.mediaInfo && d.mediaInfo.requests) || []).filter((r) => r.status === 1);
    const asks = (d.mediaInfo && d.mediaInfo.requests) || [];
    const ask = () => B.askPanel(type, id);
    // The first button says where the ask is
    let first;
    if (here && jfId) first = { label: "Watch now", icon: "play", href: `#/${isMovie ? "movie" : "show"}/${jfId}` };
    else if (kids) first = { label: "Ask for it", icon: "plus", off: "Ask a grown-up to ask for this one" };
    else if (status === 2) first = { label: admin ? "Waiting for you" : "Asked · waiting for Ben", icon: "bell", note: admin ? "Say yes or no in Overview" : "Ben has to say yes first", onClick: () => {} };
    else if (status === 3) first = { label: "On its way", icon: "dl", note: "Benflix is getting it now", onClick: () => {} };
    else first = { label: "Ask for it", icon: "plus", onClick: ask };
    const videos = (d.relatedVideos || []).filter((v) => v.site === "YouTube" && (v.type === "Trailer" || v.type === "Teaser"));
    const trailer = videos.find((v) => v.type === "Trailer") || videos[0];
    const buttons = [
      first,
      status === 4 && !kids && type === "tv" ? { label: "Ask for the rest", icon: "plus", note: "Some of it isn't on Benflix yet", onClick: ask } : null,
      { label: "Trailer", icon: "trailer", off: trailer ? "" : "No trailer for this one yet", onClick: () => B.trailer({ RemoteTrailers: [{ Url: `https://www.youtube.com/watch?v=${trailer.key}` }] }) },
      { label: "More", icon: "more", onClick: () => B.tpMore(t, [
        { label: "Open on TMDB", icon: "info", href: `https://www.themoviedb.org/${type}/${id}`, blank: true },
        d.externalIds && d.externalIds.imdbId ? { label: "Open on IMDb", icon: "info", href: `https://www.imdb.com/title/${d.externalIds.imdbId}`, blank: true } : null,
      ]) },
    ].filter(Boolean);
    // Overview strip: TMDB score, spoken language, who made it
    const makers = isMovie ? ((d.credits && d.credits.crew) || []).filter((c) => c.job === "Director").map((c) => c.name).slice(0, 2) : (d.createdBy || []).map((c) => c.name).slice(0, 2);
    const strip = B.tpStrip([
      d.voteAverage ? ["TMDB", d.voteAverage.toFixed(1)] : null,
      d.originalLanguage && d.originalLanguage !== "en" && B.langName ? ["Spoken in", B.langName(d.originalLanguage), true] : null,
      makers.length ? [isMovie ? "Director" : "Made by", makers.join(", "), true] : null,
    ]);
    // Who asked (one line each), with Yes / No for Ben on waiting asks
    const who = (r) => (r.requestedBy && (r.requestedBy.displayName || r.requestedBy.jellyfinUsername)) || "Someone";
    const asked = asks.length ? `<div class="tp-asked">${asks.slice(0, 4).map((r) => `<div class="item"><div class="grow"><div class="s">Asked by <b>${esc(who(r))}</b> · ${esc(B.when ? B.when(r.createdAt) : "")}${r.status === 1 ? " · waiting for a yes" : r.status === 3 ? " · said no" : ""}</div></div>
      ${admin && r.status === 1 ? `<div class="acts"><button class="btn small white" data-ayes="${r.id}">Yes</button><button class="btn small" data-ano="${r.id}">No</button></div>` : ""}</div>`).join("")}</div>` : "";
    const genres = (d.genres || []).slice(0, 4).map((g) => `<span class="chip">${esc(g.name)}</span>`).join("");
    const cast = ((d.credits && d.credits.cast) || []).slice(0, 20);
    const tp = B.titlePage({
      title: t,
      original: d.originalTitle || d.originalName,
      color: isMovie ? DOORS.movies.color : DOORS.shows.color,
      picture: d.backdropPath ? B.tmdbImg(d.backdropPath, "w1280") : "",
      info: [year, isMovie ? "Movie" : "Show", d.runtime ? B.len(d.runtime) : "", !isMovie && d.numberOfSeasons ? `${d.numberOfSeasons} season${d.numberOfSeasons === 1 ? "" : "s"}` : ""],
      buttons,
      status: pending.length && admin ? "Someone asked for this. Say yes or no in Overview." : "",
      story: d.overview || "",
      strip,
      overviewExtra: `<div class="wrap0" style="max-width:760px;margin-top:12px">${asked}${genres ? `<div class="row" style="gap:6px;margin-top:10px;flex-wrap:wrap">${genres}</div>` : ""}</div>`,
      tabs: [
        { name: "More to ask for", id: "tpAskMore", load: async (pane) => {
          const [rec, sim] = await Promise.all([
            B.seerr(`/${type}/${id}/similar?page=1`).catch(() => null),
            B.seerr(`/${type}/${id}/recommendations?page=1`).catch(() => null),
          ]);
          const seen = new Set([+id]);
          const pick = (r) => ((r && r.results) || []).filter((x) => x && x.posterPath && !seen.has(x.id) && seen.add(x.id)).map((x) => Object.assign({ mediaType: type }, x)).slice(0, 20);
          const a = pick(sim), b = pick(rec);
          pane.insertAdjacentHTML("afterbegin", B.row("More like this", a.map((r) => B.askCard(r))) + B.row("Recommended", b.map((r) => B.askCard(r))));
        } },
        { name: "Cast", id: "tpCast", html: cast.length ? `<section class="rowx people-row"><h3>Cast</h3><div class="strip">${cast.map((p) => `<a class="person-c" href="#/search?q=${encodeURIComponent(p.name)}"><span class="pic" style="${p.profilePath ? `background-image:url('${B.tmdbImg(p.profilePath, "w185")}')` : ""}">${p.profilePath ? "" : esc(B.initials(p.name))}</span>${esc(p.name)}<small>${esc(p.character || "")}</small></a>`).join("")}</div></section>` : "" },
      ],
    });
    tp.setStoryExtra(`<p class="dim">${esc([d.releaseDate ? "Out " + d.releaseDate : d.firstAirDate ? "First aired " + d.firstAirDate : "", (d.productionCompanies || []).slice(0, 2).map((c) => c.name).join(", ")].filter(Boolean).join(" · "))}</p>`);
    tp.root.addEventListener("click", (e) => {
      const y = e.target.closest("[data-ayes]"), n = e.target.closest("[data-ano]");
      if (!y && !n) return;
      const b = y || n;
      B.busy(b, async () => {
        await B.jf("POST", `/JellyfinEnhanced/arr/requests/${(y ? y.dataset.ayes : n.dataset.ano)}/${y ? "approve" : "decline"}`);
        B.cache["seerr-mine"] = null;
        b.closest(".acts").innerHTML = `<span class="dim">${y ? "Said yes" : "Said no"}</span>`;
        return y ? `Said yes to ${t}.` : `Said no to ${t}.`;
      });
    });
  });
})();
