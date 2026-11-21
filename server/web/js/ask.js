/* Benflix web: a movie or show that isn't on Benflix yet, on the shared title page frame (Redesign 2,
   section 1). Same buttons and tabs as the TV and phone app:
   Buttons: Ask for it (or where the ask is: waiting, on its way, Watch now), Ask for the rest, Trailer
   (greyed out when there isn't one), More.  Tabs: Overview (who asked, with Yes / No for Ben), More to
   ask for, Cast.  "Ask for it" opens the usual ask panel (with the season picker for shows). */
"use strict";

(() => {
  // ---------- Glowfin (Ben, 2026-11-21): who made it, how big a film it was, is it out, film series ----------
  // Who a studio belongs to. The film database only lists company names, so this short list of well known
  // studios is kept here. The TV and phone app has the same list (StudioOwners.kt): change both together.
  // Each line: owner | kind | name starts separated by ";" (a company matches when its name starts with one).
  const HOLLYWOOD = "Big Hollywood studio", STREAMING = "Streaming service", INDIE = "Independent studio", JAPAN = "Big Japanese studio", INDIA = "Big Indian studio", KOREA = "Big Korean studio", EUROPE = "Big European studio", CHINA = "Big Chinese studio", BRITISH_TV = "British TV";
  const OWNERS = [
    `Sony Pictures|${HOLLYWOOD}|columbia pictures;sony pictures;tristar;tri-star;screen gems;stage 6 films;affirm films;3000 pictures;destination films;left bank pictures`,
    `Sony|${JAPAN}|aniplex;crunchyroll;funimation;a-1 pictures;cloverworks`,
    `Disney|${HOLLYWOOD}|walt disney;disney;pixar;marvel studios;marvel entertainment;marvel animation;marvel television;lucasfilm;20th century;twentieth century;searchlight pictures;fox searchlight;touchstone;hollywood pictures;abc studios;abc signature;abc;fx productions;fx networks;fx;hulu;national geographic;blue sky studios`,
    `Universal|${HOLLYWOOD}|universal pictures;universal television;universal animation;universal 1440;universal content;universal international;focus features;dreamworks animation;illumination;working title;nbc;peacock;syfy;usa network;sky studios;carnival films;telemundo`,
    `Warner Bros.|${HOLLYWOOD}|warner bros;new line cinema;dc studios;dc films;dc entertainment;hbo;castle rock entertainment;cartoon network;adult swim;williams street;hanna-barbera;turner;tnt;tbs;discovery`,
    `Paramount|${HOLLYWOOD}|paramount;nickelodeon;mtv;comedy central;cbs;showtime;bet;republic pictures;skydance`,
    `Amazon|${HOLLYWOOD}|amazon;metro-goldwyn-mayer;mgm;orion pictures;united artists;prime video`,
    `Apple|${STREAMING}|apple`,
    `Netflix|${STREAMING}|netflix`,
    `Lionsgate|${HOLLYWOOD}|lionsgate;lions gate;summit entertainment;starz;entertainment one`,
    `A24|${INDIE}|a24`, `Neon|${INDIE}|neon`, `Blumhouse|${INDIE}|blumhouse`, `Legendary|${INDIE}|legendary`, `Annapurna|${INDIE}|annapurna`, `Amblin|${INDIE}|amblin`,
    `Toho|${JAPAN}|toho`, `Toei|${JAPAN}|toei`, `Kadokawa|${JAPAN}|kadokawa`, `Bandai Namco|${JAPAN}|sunrise;bandai namco`, `Nippon TV|${JAPAN}|studio ghibli;madhouse;nippon television;nippon tv`, `Sega Sammy|${JAPAN}|tms entertainment`, `Shueisha|${JAPAN}|shueisha`,
    `Yash Raj Films|${INDIA}|yash raj`, `Dharma Productions|${INDIA}|dharma productions`, `T-Series|${INDIA}|t-series`, `Red Chillies Entertainment|${INDIA}|red chillies`, `Zee|${INDIA}|zee studios;zee entertainment`, `Reliance|${INDIA}|reliance entertainment;jio studios;viacom18`, `Eros|${INDIA}|eros international`, `Hombale Films|${INDIA}|hombale`, `Lyca Productions|${INDIA}|lyca`, `Sun Pictures|${INDIA}|sun pictures`, `Excel Entertainment|${INDIA}|excel entertainment`,
    `CJ ENM|${KOREA}|cj entertainment;cj enm;studio dragon`, `Showbox|${KOREA}|showbox`,
    `Canal+|${EUROPE}|studiocanal;canal+`, `Gaumont|${EUROPE}|gaumont`, `Pathé|${EUROPE}|pathé;pathe`, `Constantin Film|${EUROPE}|constantin film`,
    `BBC|${BRITISH_TV}|bbc`, `ITV|${BRITISH_TV}|itv`, `Channel 4|${BRITISH_TV}|channel 4;film4`,
    `China Film Group|${CHINA}|china film`, `Tencent|${CHINA}|tencent`,
  ].flatMap((line) => { const [owner, kind, starts] = line.split("|"); return starts.split(";").map((st) => [st, { owner, kind }]); }).sort((a, b) => b[0].length - a[0].length);
  // { owner, kind, part } for a company name; part = true when the owner is a different, bigger company
  B.studioOwner = (company) => {
    const name = String(company || "").trim().toLowerCase();
    const hit = OWNERS.find(([st]) => name === st || name.startsWith(st + " ") || name.startsWith(st + ","));
    if (!hit) return null;
    return Object.assign({ part: !name.startsWith(hit[1].owner.toLowerCase().replace(/\.$/, "")) }, hit[1]);
  };
  const companies = (list) => { const seen = new Set(); return (list || []).filter((c) => c && c.name && !seen.has(c.name.toLowerCase()) && seen.add(c.name.toLowerCase())); };
  const mainOf = (list) => list.find((c) => B.studioOwner(c.name)) || list[0] || null;
  const dayOf = (text) => { const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(text || ""); return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null; };
  const longDay = (d) => d.toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" });
  const nearDay = (d) => (d.getFullYear() === new Date().getFullYear() ? d.toLocaleDateString("en-US", { month: "long", day: "numeric" }) : longDay(d));
  const roughly = (d) => (d.getDate() <= 10 ? "early " : d.getDate() <= 20 ? "mid " : "late ") + d.toLocaleDateString("en-US", { month: "long" });
  const trimNum = (n) => (n >= 100 || n % 1 < 0.05 ? n.toFixed(0) : n.toFixed(1));
  const money = (n) => (n >= 1e9 ? `$${trimNum(n / 1e9)} billion` : n >= 1e6 ? `$${trimNum(n / 1e6)} million` : "$" + Math.round(n).toLocaleString("en-US"));
  const countryName = (code) => { try { const n = new Intl.DisplayNames(["en"], { type: "region" }).of(code); return n && n !== code ? n : ""; } catch (e) { return ""; } };
  // Cinema and home release of a movie: { cinema, home, guess } (home is a guess, 90 days after cinemas, when TMDB has none)
  const releaseOf = (d) => {
    const cinema = dayOf(d.releaseDate);
    const homes = (((d.releases || {}).results) || []).flatMap((r) => r.release_dates || []).filter((r) => [4, 5, 6].includes(+r.type)).map((r) => dayOf(r.release_date)).filter(Boolean).sort((a, b) => a - b);
    if (homes[0]) return { cinema, home: homes[0], guess: false };
    return { cinema, home: cinema ? new Date(cinema.getTime() + 90 * 86400000) : null, guess: true };
  };
  // IMDb's awards line from OMDb, with the key Ben saved once in the app's Settings (no key = no line)
  const awardsOf = (imdbId) => B.cached("omdb-awards-" + imdbId, 86400000, async () => {
    const key = await B.cached("omdb-key", 3600000, async () => (((await B.prefs().catch(() => null)) || {}).CustomPrefs || {})["benflix.omdb.key"] || "");
    if (!key || !/^tt\d+$/.test(imdbId || "")) return "";
    const r = await fetch(`https://www.omdbapi.com/?i=${imdbId}&apikey=${encodeURIComponent(key.trim())}`).then((x) => x.json()).catch(() => null);
    const a = r && r.Awards;
    return a && a !== "N/A" ? a : "";
  }).catch(() => "");
  // The Details boxes on the right of Overview
  const factsHtml = (d, isMovie, awards) => {
    const made = companies(d.productionCompanies), nets = isMovie ? [] : companies(d.networks);
    const first = isMovie ? mainOf(made) : mainOf(nets) || mainOf(made);
    const boxes = [];
    if (first) {
      const shownOn = !isMovie && nets.length > 0, own = B.studioOwner(first.name);
      const others = made.filter((c) => c.name !== first.name).slice(0, 3).map((c) => { const n = c.originCountry && c.originCountry !== "US" ? countryName(c.originCountry) : ""; return n ? `${c.name} (${n})` : c.name; });
      const small = [own ? own.kind : "", others.length ? (shownOn ? "Made by " : "With ") + others.join(", ") : ""].filter(Boolean).join(" · ");
      boxes.push(`<div class="fact-full"><b>${shownOn ? "Shown on" : "Made by"}</b><span class="fact-big">${esc(first.name)}</span>${own && own.part ? `<span class="fact-tag">Part of ${esc(own.owner)}</span>` : ""}${small ? `<small>${esc(small)}</small>` : ""}${first.id ? `<a class="fact-link" href="#/studio/${shownOn ? "tv" : "movie"}/${first.id}?name=${encodeURIComponent(first.name)}">See everything from ${esc(first.name)} ›</a>` : ""}</div>`);
    }
    const box = (k, v, small, full) => boxes.push(`<div${full ? ' class="fact-full"' : ""}><b>${esc(k)}</b>${esc(v)}${small ? `<small>${esc(small)}</small>` : ""}</div>`);
    const today = new Date();
    if (isMovie) {
      const cinema = dayOf(d.releaseDate);
      if (cinema) box(cinema > today ? "Comes out" : "Released", longDay(cinema));
    } else {
      const first = dayOf(d.firstAirDate);
      if (first) box(first > today ? "Starts" : "First aired", longDay(first));
    }
    const lands = (d.productionCountries || []).map((c) => c.name).filter(Boolean).slice(0, 3);
    if (lands.length) box("Made in", lands.join(", "));
    if (isMovie && d.budget > 0) box("Cost to make", money(d.budget), d.budget >= 75e6 ? "Big-budget film" : d.budget <= 5e6 ? "Small film" : "");
    if (isMovie && d.revenue > 0) box("Made in cinemas", money(d.revenue));
    if (awards) { const [top, ...rest] = awards.split(/\.\s+/); box("Awards", top.replace(/\.$/, ""), rest.join(". ").replace(/\.$/, "").replace(/&/g, "and"), true); }
    return boxes.length ? `<section class="rowx"><h3>Details</h3><div class="facts">${boxes.join("")}</div></section>` : "";
  };
  // "Is it out yet?" for a movie that isn't out at home yet (nothing for older films)
  const outYetHtml = (d) => {
    const { cinema, home, guess } = releaseOf(d), today = new Date();
    if (!cinema || !home || home <= today) return "";
    const inCinemas = cinema <= today;
    return `<div class="ask-steps ask-out"><h4>Is it out yet?</h4><ol>
      <li class="${inCinemas ? "done" : "now"}"><i>${inCinemas ? "✓" : "1"}</i><div><b>In cinemas</b><span>${inCinemas ? "Since " : "From "}${esc(nearDay(cinema))}</span></div></li>
      <li class="${inCinemas ? "now" : ""}"><i>2</i><div><b>Out at home</b><span>Not yet. ${guess ? "Around " + esc(roughly(home)) : esc(nearDay(home))}.</span></div></li>
      <li><i>3</i><div><b>If you ask now</b><span>Glowfin grabs it once a good copy is out.</span></div></li></ol></div>`;
  };
  // "Is the show finished?"
  const showStatusHtml = (d) => {
    const next = dayOf((d.nextEpisodeToAir || {}).airDate), today = new Date(new Date().toDateString());
    const word = { "Returning Series": next ? "Still going" : "Still going, between seasons", Ended: "Finished", Canceled: "Cancelled", "In Production": "Not out yet", Planned: "Not out yet", Pilot: "Not out yet" }[d.status];
    if (!word) return "";
    const ns = d.numberOfSeasons, ne = d.numberOfEpisodes;
    const size = [ns ? `${ns} season${ns === 1 ? "" : "s"}${/^Still/.test(word) ? " so far" : ""}` : "", ne ? `${ne} episode${ne === 1 ? "" : "s"}` : ""].filter(Boolean).join(" · ");
    const line = word === "Finished" ? "The whole show is out, so asking for it gets all of it." : word === "Cancelled" ? "It was cancelled, so the story may stop without an ending." : /^Still going$/.test(word) ? "A season is airing now. Ask for it and new episodes show up by themselves." : /^Still/.test(word) ? "More is coming. Ask for it and new seasons show up by themselves." : "It hasn't started yet. Ask now and episodes show up as they air.";
    return `<div class="ask-steps ask-show"><h4>Is the show finished?</h4><div class="ask-chips"><span class="chip on">${esc(word)}</span>${size ? `<span class="chip">${esc(size)}</span>` : ""}${next && next >= today ? `<span class="chip">Next episode: ${esc(nearDay(next))}</span>` : ""}</div><p class="dim">${esc(line)}</p></div>`;
  };
  // "Part 2 of 4 · The Monsoon Collection", every part and where it stands, and one button for the missing ones
  const filmSet = async (d, id, box, nav, kids) => {
    const cid = d.collection && d.collection.id;
    if (!cid || !box) return;
    const [set, lib] = await Promise.all([B.seerr(`/collection/${cid}`).catch(() => null), B.libTmdb().catch(() => new Map())]);
    if (!set || !B.alive(nav) || !box.isConnected) return;
    const today = new Date();
    const parts = (set.parts || []).filter((p) => p && p.id && (p.title || p.name)).map((p) => {
      const date = dayOf(p.releaseDate), st = (p.mediaInfo && p.mediaInfo.status) || 1;
      return { id: p.id, title: p.title || p.name, date, img: p.posterPath, jf: lib.get("movie:" + p.id) || (p.mediaInfo && p.mediaInfo.jellyfinMediaId), state: st >= 4 || lib.has("movie:" + p.id) ? "here" : st >= 2 ? "asked" : !date || date > today ? "notout" : "missing" };
    }).sort((a, b) => (a.date || Infinity) - (b.date || Infinity) || a.id - b.id);
    if (parts.length < 2) return;
    const at = parts.findIndex((p) => p.id === +id), missing = parts.filter((p) => p.state === "missing");
    const tag = (p) => (p.id === +id ? "This one" : p.state === "here" ? "On Glowfin" : p.state === "asked" ? "Asked for" : p.state === "notout" ? "Not out yet" : "Not here");
    box.innerHTML = `<div class="ask-steps ask-set"><h4>${at >= 0 ? `Part ${at + 1} of ${parts.length}` : `${parts.length} movies`} · ${esc(set.name || "")}</h4>
      <div class="ask-parts">${parts.slice(0, 12).map((p) => `<a class="poster askcard" href="${p.state === "here" && p.jf ? `#/movie/${esc(p.jf)}` : `#/ask/movie/${p.id}`}" title="${esc(p.title)}"><div class="im" ${p.img ? `style="background-image:url('${B.tmdbImg(p.img)}')"` : ""}>${p.img ? "" : `<div class="noart"><span class="nt">${esc(p.title)}</span></div>`}<span class="newp ${p.state === "here" ? "miss" : "grey"}">${tag(p)}</span></div></a>`).join("")}</div>
      ${missing.length && !kids ? `<button class="btn white small" id="askSet">${I("plus")} Ask for the ${missing.length} you don't have</button>` : ""}</div>`;
    const go = $("#askSet", box);
    if (go) go.onclick = () => B.busy(go, async () => {
      let n = 0;
      for (const p of missing) { try { await B.seerr("/request", "POST", { mediaType: "movie", mediaId: p.id }); n++; } catch (e) { /* the rest are still asked for */ } }
      B.cache["seerr-mine"] = null;
      if (!n) throw new Error("Couldn't ask for those. Try again in a minute.");
      go.remove();
      $$(".ask-parts .newp.grey", box).forEach((x) => { if (x.textContent === "Not here") x.textContent = "Asked for"; });
      return n === 1 ? "Asked for 1 movie." : `Asked for ${n} movies.`;
    });
  };

  // "What's the hold-up?" (Ben only, at home): why something that got a yes hasn't arrived, from Radarr / Sonarr
  const holdUp = async (type, id, box, nav) => {
    if (!box) return;
    const q = `kind=${type}&tmdb=${id}`;
    const draw = (h, extra) => {
      box.innerHTML = `<div class="ask-steps ask-hold"><h4>What's the hold-up?</h4><p class="hold-line">${esc(h.line || "")}</p><p class="dim">${esc(h.detail || "")}</p>${extra ? `<p class="hold-line">${esc(extra.line || "")}</p><p class="dim">${esc(extra.detail || "")}</p>` : ""}
        <div class="acts" style="justify-content:flex-start;flex-wrap:wrap;margin-top:12px">${h.search ? `<button class="btn small white" data-hold="search">Look again now</button>` : ""}${h.replace ? `<button class="btn small white" data-hold="replace">Try another copy</button>` : ""}${h.copies && !extra ? `<button class="btn small" data-hold="copies">See what copies are out there</button>` : ""}</div></div>`;
      box.onclick = (e) => {
        const b = e.target.closest("[data-hold]");
        if (!b) return;
        const what = b.dataset.hold;
        B.busy(b, async () => {
          if (what === "copies") { const c = await B.call("GET", `api/admin/holdup/copies?${q}`); if (B.alive(nav)) draw(h, c); return ""; }
          const r = what === "replace" ? await B.call("POST", `api/admin/downloads/${h.replace.app}/${h.replace.id}/replace`) : await B.call("POST", `api/admin/holdup/search?${q}`);
          setTimeout(() => b.remove(), 0);
          return (r && r.message) || "Done.";
        });
      };
    };
    // Away from home (or Tower not updated yet) there is no answer: the box just stays away
    const h = await B.call("GET", `api/admin/holdup?${q}`).catch(() => null);
    if (h && h.line && B.alive(nav) && box.isConnected) draw(h);
  };

  // Everything one studio (movies) or network (shows) made that you can ask for
  B.route(/^\/studio\/(movie|tv)\/(\d+)/, async (m, nav) => {
    const [, type, id] = m, name = B.q("name") || "This studio", own = B.studioOwner(name);
    const path = type === "movie" ? `/discover/movies/studio/${id}` : `/discover/tv/network/${id}`;
    B.page().innerHTML = B.top("") + `<div class="wrap studio"><button class="chip" id="stBack">‹ Back</button><h2>${esc(name)}</h2><p class="dim" id="stNote">${esc([own && own.part ? "Part of " + own.owner : "", own ? own.kind : ""].filter(Boolean).join(" · "))}</p><div class="studio-grid" id="stGrid">${B.spinner()}</div><div class="acts" style="justify-content:center;margin:18px 0 40px"><button class="btn" id="stMore" hidden>Show more</button></div></div>`;
    B.wireTop();
    const lib = await B.libTmdb().catch(() => new Map());
    let page = 0, pages = 1, shown = 0, owned = 0, first = true;
    const seen = new Set();
    const more = async () => {
      const got = await Promise.all([1, 2].map((n) => (page + n <= pages || first ? B.seerr(`${path}?page=${page + n}`).catch(() => null) : null)));
      if (!B.alive(nav)) return;
      const grid = $("#stGrid");
      if (first) { grid.innerHTML = ""; first = false; }
      page += 2;
      got.forEach((g) => {
        if (!g) return;
        pages = g.totalPages || pages;
        (g.results || []).forEach((r) => {
          if (!r || !r.posterPath || seen.has(r.id)) return;
          seen.add(r.id);
          Object.assign(r, { mediaType: type });
          if (B.onBenflix(r)) { owned++; return; }
          shown++;
          grid.insertAdjacentHTML("beforeend", B.askCard(r));
        });
      });
      if (!shown && page >= pages) grid.innerHTML = `<div class="empty">${owned ? "You already have everything from here." : "Nothing to show from here right now."}</div>`;
      const note = [own && own.part ? "Part of " + own.owner : "", own ? own.kind : "", owned ? `${owned} already on Glowfin (hidden)` : ""].filter(Boolean).join(" · ");
      $("#stNote").textContent = note;
      $("#stMore").hidden = page >= pages;
    };
    $("#stBack").onclick = () => B.back();
    $("#stMore").onclick = () => B.busy($("#stMore"), async () => { await more(); return ""; });
    await more();
  });

  B.route(/^\/ask\/(movie|tv)\/(\d+)/, async (m, nav) => {
    const [, type, id] = m;
    B.page().innerHTML = B.top("") + B.spinner();
    B.wireTop();
    // My List for titles that aren't here yet: kept on Tower, which moves it to the real My List when it arrives
    const wishes = B.call("GET", "api/asks/wish").then((w) => (w && w.items) || []).catch(() => []);
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
    else if (status === 3) first = { label: "On its way", icon: "dl", note: "Glowfin is getting it now", onClick: () => {} };
    else first = { label: "Ask for it", icon: "plus", onClick: ask };
    const videos = (d.relatedVideos || []).filter((v) => v.site === "YouTube" && (v.type === "Trailer" || v.type === "Teaser"));
    const trailer = videos.find((v) => v.type === "Trailer") || videos[0];
    let wished = (await wishes).some((w) => w.type === type && String(w.tmdb) === String(id));
    if (!B.alive(nav)) return;
    const wish = async (e, el) => {
      wished = !wished;
      await B.call("POST", "api/asks/wish", { type, tmdb: +id, title: t, poster: d.posterPath || "", on: wished }).catch((err) => { wished = !wished; B.toast(err.message); });
      if (el) el.innerHTML = `${I(wished ? "check" : "plus")} <span>My List</span>`;
      if (wished) {
        B.toast(status > 1 ? "On your list. It shows up in My List the moment it arrives." : "On your list. Asking for it now.");
        if (status <= 1) ask();
      } else B.toast("Taken off your list.");
    };
    const buttons = [
      first,
      !here && !kids ? { id: "bWish", label: "My List", icon: wished ? "check" : "plus", onClick: wish } : null,
      status === 4 && !kids && type === "tv" ? { label: "Ask for the rest", icon: "plus", note: "Some of it isn't on Glowfin yet", onClick: ask } : null,
      { label: "Trailer", icon: "trailer", off: trailer ? "" : "No trailer for this one yet", onClick: () => B.trailer({ RemoteTrailers: [{ Url: `https://www.youtube.com/watch?v=${trailer.key}` }] }) },
      { label: "More", icon: "more", onClick: () => B.tpMore(t, [
        { label: "Open on TMDB", icon: "info", href: `https://www.themoviedb.org/${type}/${id}`, blank: true },
        d.externalIds && d.externalIds.imdbId ? { label: "Open on IMDb", icon: "info", href: `https://www.imdb.com/title/${d.externalIds.imdbId}`, blank: true } : null,
      ]) },
    ].filter(Boolean);
    // Overview strip: TMDB score, spoken language, who made it
    const makers = isMovie ? ((d.credits && d.credits.crew) || []).filter((c) => c.job === "Director").map((c) => c.name).slice(0, 2) : (d.createdBy || []).map((c) => c.name).slice(0, 2);
    // Where it's streaming right now (subscription services in the US, from TMDB through Seerr)
    const usNow = (d.watchProviders || []).find((w) => w.iso_3166_1 === "US");
    const streamingOn = [...new Set(((usNow && usNow.flatrate) || []).slice().sort((x, y) => (x.displayPriority || 99) - (y.displayPriority || 99)).map((x) => x.name).filter(Boolean))].slice(0, 4);
    const strip = B.tpStrip([
      d.voteAverage ? ["TMDB", d.voteAverage.toFixed(1)] : null,
      d.originalLanguage && d.originalLanguage !== "en" && B.langName ? ["Spoken in", B.langName(d.originalLanguage), true] : null,
      makers.length ? [isMovie ? "Director" : "Made by", makers.join(", "), true] : null,
      streamingOn.length && !kids ? ["Streaming now on", streamingOn.join(", "), true] : null,
    ]);
    // Who asked (one line each), with Yes / No for Ben on waiting asks
    const who = (r) => (r.requestedBy && (r.requestedBy.displayName || r.requestedBy.jellyfinUsername)) || "Someone";
    const asked = asks.length ? `<div class="tp-asked">${asks.slice(0, 4).map((r) => `<div class="item"><div class="grow"><div class="s">Asked by <b>${esc(who(r))}</b> · ${esc(B.when ? B.when(r.createdAt) : "")}${r.status === 1 ? " · waiting for a yes" : r.status === 3 ? " · said no" : ""}</div></div>
      ${admin && r.status === 1 ? `<div class="acts"><button class="btn small white" data-ayes="${r.id}">Yes</button><button class="btn small" data-ano="${r.id}">No</button></div>` : ""}</div>`).join("")}</div>` : "";
    // What happens next: three plain steps, lit up to where the ask is now (same as the TV and phone app)
    const at = status === 2 ? 1 : status === 3 ? 2 : 0;
    const stepList = [
      [admin ? "It gets a yes" : "Ben says yes", admin ? "Your own asks start right away." : at === 1 ? "Waiting now. Usually within a day." : "Usually within a day."],
      ["Glowfin fetches it", at === 2 ? "Getting it now. Most take an hour or two." : "It finds a good copy. Most take an hour or two."],
      ["Ready to watch", "It lands in Recently added on Home."],
    ];
    const steps = here || kids || status > 3 ? "" : `<div class="ask-steps"><h4>What happens next</h4><ol>${stepList.map((s, i) => `<li class="${at === i + 1 ? "now" : at > i + 1 ? "done" : ""}"><i>${i + 1}</i><div><b>${esc(s[0])}</b><span>${esc(s[1])}</span></div></li>`).join("")}</ol><div class="bar"><u style="width:${at === 1 ? 17 : at === 2 ? 50 : 0}%"></u></div></div>`;
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
      overviewExtra: `<div class="wrap0" style="max-width:760px;margin-top:12px">${admin && status === 3 ? '<div id="askHoldBox"></div>' : ""}${isMovie ? outYetHtml(d) : showStatusHtml(d)}<div id="askSetBox"></div>${steps}${asked}${genres ? `<div class="row" style="gap:6px;margin-top:10px;flex-wrap:wrap">${genres}</div>` : ""}</div>`,
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
    // Details on the right: who made it, release, cost, cinema takings; the awards line joins when OMDb answers
    tp.setStoryExtra(factsHtml(d, isMovie, ""));
    const imdbId = d.externalIds && d.externalIds.imdbId;
    if (imdbId) awardsOf(imdbId).then((a) => { if (a && B.alive(nav)) tp.setStoryExtra(factsHtml(d, isMovie, a)); });
    if (isMovie) filmSet(d, id, $("#askSetBox", tp.root), nav, kids).catch(() => {});
    if (admin && status === 3) holdUp(type, id, $("#askHoldBox", tp.root), nav).catch(() => {});
    tp.root.addEventListener("click", (e) => {
      const y = e.target.closest("[data-ayes]"), n = e.target.closest("[data-ano]");
      if (!y && !n) return;
      const b = y || n;
      B.busy(b, async () => {
        await B.jf("POST", `/JellyfinEnhanced/arr/requests/${(y ? y.dataset.ayes : n.dataset.ano)}/${y ? "approve" : "decline"}`);
        if (!y) await B.whyNo("seerr:" + n.dataset.ano);
        B.cache["seerr-mine"] = null;
        b.closest(".acts").innerHTML = `<span class="dim">${y ? "Said yes" : "Said no"}</span>`;
        return y ? `Said yes to ${t}.` : `Said no to ${t}.`;
      });
    });
  });
})();
