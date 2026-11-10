/* Benflix web: pieces the TV and phone app already had on movie, show and people pages, brought to
   the browser (2026-11-10 "Find features missing from web"):
     - "Part 1 of 3" + In this collection (movies in a collection)
     - Ask for more like this (titles like this one that aren't on Benflix yet, from Requests)
     - Parents' guide (IMDb's guide, through Tower)
     - Season 4 is out. Ask for it (once you've watched everything of a show)
     - Anime extras: AniList score, English and Japanese names, next episode countdown, fan tags,
       characters and their voices
     - Not on Benflix yet (everything of an actor's or director's that isn't here, ask for many at once)
     - Search in plain words ("funny 90s movie under 2 hours")
   Everything here waits for the page's own parts first, and quietly shows nothing when a source
   can't be reached. Kids never see the asking parts. */
"use strict";

(() => {
  const X = (B.extras = {});
  const requestsOn = async () => {
    if (!B.me || B.me.kids) return false;
    const me = await B.seerrMe().catch(() => null);
    return !!(me && me.active && me.userFound);
  };
  const tmdbOf = (it) => {
    const p = it.ProviderIds || {};
    const k = Object.keys(p).find((x) => x.toLowerCase() === "tmdb");
    return k ? +p[k] || null : null;
  };
  const idOf = (it, name) => {
    const p = it.ProviderIds || {};
    const k = Object.keys(p).find((x) => x.toLowerCase() === name.toLowerCase());
    return k ? p[k] : null;
  };
  // Seerr: 1 unknown, 2 asked for, 3 on its way, 4 some of it here, 5 here, 6 blocked
  const statusOf = (r) => (r && r.mediaInfo && r.mediaInfo.status) || 1;
  const add = (box, html) => { if (box && html) box.insertAdjacentHTML("beforeend", html); };
  const addFirst = (box, html) => { if (box && html) box.insertAdjacentHTML("afterbegin", html); };

  // ---------- In this collection ----------
  // Which collection each movie is in, worked out once a day (the same way the TV app does it)
  const COLL_KEY = () => "benflix.collections." + B.me.id + (B.me.kids ? ".k" : "");
  const collections = async () => {
    try {
      const saved = JSON.parse(localStorage.getItem(COLL_KEY()) || "null");
      if (saved && Date.now() - saved.at < 86400000) return saved.map;
    } catch (e) {}
    const sets = ((await B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=BoxSet&Recursive=true&EnableImages=false&EnableUserData=false&Limit=600`)).Items || []);
    const map = {};
    for (let i = 0; i < sets.length; i += 8) {
      await Promise.all(sets.slice(i, i + 8).map(async (s) => {
        const kids = ((await B.get(`/Items?userId=${B.uid()}&ParentId=${s.Id}&IncludeItemTypes=Movie&EnableImages=false&EnableUserData=false&Fields=ProductionYear&SortBy=ProductionYear,SortName`).catch(() => ({}))).Items || []);
        if (kids.length < 2) return;
        kids.forEach((k, n) => { if (!map[k.Id]) map[k.Id] = [s.Id, s.Name, n + 1, kids.length]; });
      }));
    }
    try { localStorage.setItem(COLL_KEY(), JSON.stringify({ at: Date.now(), map })); } catch (e) {}
    return map;
  };
  X.collection = async (it, nav, box) => {
    const hit = (await collections().catch(() => ({})))[it.Id];
    if (!hit || !B.alive(nav)) return;
    const [setId, name, part, total] = hit;
    // "Part 1 of 3 · The Dark Knight Collection" at the end of the info line
    const meta = $(".details .meta");
    if (meta) meta.insertAdjacentHTML("beforeend", `<a class="partline" href="#/collection/${esc(setId)}">Part ${part} of ${total} · ${esc(name)}</a>`);
    const kids = await B.get(`/Items?userId=${B.uid()}&ParentId=${setId}&IncludeItemTypes=Movie&SortBy=ProductionYear,SortName&Fields=${B.FIELDS}`).then((r) => B.keep(r.Items)).catch(() => []);
    if (!B.alive(nav)) return;
    add(box || $("#below"), B.row(`In this collection`, kids.map((k) => B.posterCard(k)), { id: "inColl" }));
  };

  // ---------- Ask for more like this ----------
  X.askMore = async (it, nav, box) => {
    const tmdb = tmdbOf(it);
    if (!tmdb || !(await requestsOn())) return;
    const kind = it.Type === "Movie" ? "movie" : "tv";
    const [rec, sim] = await Promise.all([
      B.seerr(`/${kind}/${tmdb}/recommendations?page=1`).catch(() => null),
      B.seerr(`/${kind}/${tmdb}/similar?page=1`).catch(() => null),
    ]);
    if (!B.alive(nav)) return;
    const seen = new Set();
    const list = [...((rec && rec.results) || []), ...((sim && sim.results) || [])].filter((r) => {
      if (!r || r.id === tmdb || seen.has(r.id) || !r.posterPath) return false;
      seen.add(r.id);
      const s = statusOf(r);
      return s !== 4 && s !== 5 && s !== 6 && !(r.mediaInfo && r.mediaInfo.jellyfinMediaId);
    }).map((r) => Object.assign({ mediaType: kind }, r)).slice(0, 20);
    // Right after More like this
    const more = box && [...box.querySelectorAll(".rowx h3")].find((h) => h.textContent.startsWith("More like this"));
    const html = B.row("Ask for more like this", list.map((r) => B.askCard(r)));
    if (more) more.closest(".rowx").insertAdjacentHTML("afterend", html); else add(box, html);
  };

  // ---------- Parents' guide ----------
  X.parents = async (it, nav, box) => {
    const imdb = idOf(it, "Imdb");
    if (!imdb || !/^tt\d+$/.test(imdb)) return;
    const got = await B.call("GET", `api/facts/parents/${imdb}`).catch(() => null);
    const lines = (got && got.lines) || [];
    if (!lines.length || !B.alive(nav)) return;
    const colour = (r) => (r >= 3 ? "var(--bad)" : r === 2 ? "var(--warn)" : "var(--ok)");
    add(box, `<section class="rowx"><h3>Parents' guide</h3><div class="wrap parents">
      ${lines.map((l) => `<div><span class="dim">${esc(l.label)}</span><i>${[0, 1, 2].map((d) => `<b style="background:${d < l.rank ? colour(l.rank) : "var(--line)"}"></b>`).join("")}</i>${esc(l.level)}</div>`).join("")}
      <small class="dim3">From IMDb's parents guide</small></div></section>`);
  };

  // ---------- Season 4 is out. Ask for it ----------
  X.nextSeason = async (it, nav) => {
    const tmdb = tmdbOf(it);
    if (!tmdb || !(await requestsOn())) return;
    const d = await B.seerr(`/tv/${tmdb}`).catch(() => null);
    if (!d || !B.alive(nav)) return;
    const today = new Date().toISOString().slice(0, 10);
    // "New episode Thursday" for shows still airing
    const next = d.nextEpisodeToAir && d.nextEpisodeToAir.airDate;
    if (next && next >= today && !/New episodes on|New episode/.test($(".details .meta") ? $(".details .meta").textContent : "")) {
      const when = new Date(next + "T12:00:00");
      const days = Math.round((when - new Date(today + "T12:00:00")) / 86400000);
      const word = days === 0 ? "today" : days === 1 ? "tomorrow" : days < 7 ? when.toLocaleDateString([], { weekday: "long" }) : B.day(next);
      $(".details .meta") && $(".details .meta").insertAdjacentHTML("beforeend", `<span>New episode ${esc(word)}</span>`);
    }
    // Only once everything on Benflix has been watched
    if (((it.UserData || {}).UnplayedItemCount || 0) > 0) return;
    const here = ((await B.get(`/Shows/${it.Id}/Seasons?userId=${B.uid()}`).catch(() => ({}))).Items || []).map((s) => s.IndexNumber || 0).filter((n) => n > 0);
    const last = Math.max(0, ...here);
    if (!last || !B.alive(nav)) return;
    const st = {}; ((d.mediaInfo && d.mediaInfo.seasons) || []).forEach((s) => { st[s.seasonNumber] = s.status || 1; });
    const newer = (d.seasons || []).filter((s) => s.seasonNumber > last && s.airDate && s.airDate.slice(0, 10) <= today && (st[s.seasonNumber] || 1) <= 1).map((s) => s.seasonNumber).sort((a, b) => a - b);
    if (!newer.length) return;
    const words = newer.length === 1 ? `Season ${newer[0]} is out` : `Seasons ${newer[0]} to ${newer[newer.length - 1]} are out`;
    // At the end of the Episodes tab
    const btns = $("#tpEpisodes") || $(".details .btns");
    if (!btns) return;
    btns.insertAdjacentHTML(btns.id === "tpEpisodes" ? "beforeend" : "afterend", `<div class="nextask wrap0"><span>${esc(words)}. They aren't on Benflix yet.</span><button class="btn white" id="askNext">${I("plus")} Ask for ${newer.length === 1 ? "it" : "them"}</button></div>`);
    $("#askNext").onclick = (e) => B.busy(e.currentTarget, async () => {
      await B.seerr("/request", "POST", { mediaType: "tv", mediaId: tmdb, seasons: newer });
      $(".nextask").remove();
      B.cache["seerr-mine"] = null;
      return `Asked for ${newer.length === 1 ? "Season " + newer[0] : newer.length + " seasons"}. You'll see it in Your requests.`;
    });
  };

  // ---------- Anime extras (AniList) ----------
  const ANI_FIELDS = `id idMal format averageScore meanScore genres title { english romaji native } synonyms
    tags { name rank isMediaSpoiler isGeneralSpoiler isAdult } nextAiringEpisode { episode airingAt }
    characters(sort: [ROLE, RELEVANCE, ID], perPage: 16) { edges { role node { name { full } image { medium } }
      japanese: voiceActors(language: JAPANESE, sort: [RELEVANCE, ID]) { name { full } }
      english: voiceActors(language: ENGLISH, sort: [RELEVANCE, ID]) { name { full } } } }`;
  const aniAsk = async (query, variables) => {
    const r = await fetch("https://graphql.anilist.co", { method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify({ query, variables }) });
    if (!r.ok) throw new Error("AniList " + r.status);
    return (await r.json()).data;
  };
  const clean = (t) => String(t || "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
  const aniInfo = (it) => B.cached("anilist-" + it.Id, 86400000, async () => {
    const saved = (() => { try { return JSON.parse(localStorage.getItem("benflix.anilist." + it.Id) || "null"); } catch (e) { return null; } })();
    if (saved && Date.now() - saved.at < 3 * 86400000 && !(saved.m && saved.m.nextAiringEpisode && saved.m.nextAiringEpisode.airingAt * 1000 < Date.now())) return saved.m;
    const al = idOf(it, "AniList"), mal = idOf(it, "MyAnimeList") || idOf(it, "Mal");
    let m = null;
    if (al || mal) {
      const d = await aniAsk(`query ($id: Int, $idMal: Int) { Media(id: $id, idMal: $idMal, type: ANIME) { ${ANI_FIELDS} } }`, al ? { id: +al } : { idMal: +mal });
      m = d && d.Media;
    } else {
      const d = await aniAsk(`query ($q: String) { Page(perPage: 5) { media(search: $q, type: ANIME, isAdult: false, sort: SEARCH_MATCH) { ${ANI_FIELDS} startDate { year } } } }`, { q: it.Name });
      const want = clean(it.Name), wantMovie = it.Type === "Movie";
      const list = (d && d.Page && d.Page.media) || [];
      m = list.find((x) => [x.title.english, x.title.romaji, ...(x.synonyms || [])].some((n) => clean(n) === want) && ((x.format === "MOVIE") === wantMovie)) ||
        list.find((x) => (x.format === "MOVIE") === wantMovie && (!it.ProductionYear || !x.startDate || !x.startDate.year || Math.abs(x.startDate.year - it.ProductionYear) <= 1)) || null;
    }
    try { localStorage.setItem("benflix.anilist." + it.Id, JSON.stringify({ at: Date.now(), m })); } catch (e) {}
    return m;
  });
  X.anime = async (it, nav, box, charBox) => {
    const ids = await B.animeIds(it.Type === "Movie" ? "Movie" : "Series").catch(() => new Set());
    if (!ids.has(it.Id)) return;
    const m = await aniInfo(it).catch(() => null);
    if (!m || !B.alive(nav)) return;
    // English name with the Japanese (romaji) one under it
    const t = $(".details h1.t");
    const romaji = m.title && m.title.romaji;
    if (t && romaji && clean(romaji) !== clean(it.Name) && !$(".details .t2")) t.insertAdjacentHTML("afterend", `<div class="t2">${esc(romaji)}</div>`);
    // AniList score on the scores line
    const score = m.averageScore || m.meanScore;
    if (score && !B.me.kids) {
      const s = $(".details .scores");
      const html = `<span><b>${score}%</b>AniList</span>${m.idMal ? `<a href="https://myanimelist.net/anime/${m.idMal}" target="_blank" rel="noopener" class="dim" style="align-self:center">MyAnimeList ↗</a>` : ""}`;
      if (s) s.insertAdjacentHTML("beforeend", html);
      else { const meta = $(".details .meta"); if (meta) meta.insertAdjacentHTML("afterend", `<div class="scores">${html}</div>`); }
    }
    // Episode 9 out Saturday 5:30 PM · in 2 days
    const na = m.nextAiringEpisode;
    if (na && na.airingAt * 1000 > Date.now()) {
      const at = new Date(na.airingAt * 1000), days = Math.floor((at - Date.now()) / 86400000);
      const meta = $(".details .meta");
      if (meta) meta.insertAdjacentHTML("beforeend", `<span>Episode ${na.episode} out ${esc(at.toLocaleDateString([], { weekday: "long" }))} ${esc(B.clock(at))} · ${days < 1 ? "very soon" : days === 1 ? "in 1 day" : `in ${days} days`}</span>`);
    }
    // Up to 5 fan tags
    const tags = (m.tags || []).filter((x) => x.rank >= 60 && !x.isMediaSpoiler && !x.isGeneralSpoiler && !x.isAdult).slice(0, 5).map((x) => x.name);
    const ov = $(".details .ov");
    if (tags.length && ov) ov.insertAdjacentHTML("afterend", `<div class="anitags">${tags.map((x) => `<span>${esc(x)}</span>`).join("")}</div>`);
    // Characters and their voices (Japanese, and English when there's a dub)
    const chars = ((m.characters && m.characters.edges) || []).filter((e) => e.node && e.node.name);
    if (chars.length && (charBox || box)) {
      const html = `<section class="rowx people-row"><h3>Characters and voices</h3><div class="strip">${chars.map((e) => {
        const ja = (e.japanese || [])[0], en = (e.english || [])[0];
        const img = e.node.image && e.node.image.medium;
        return `<div class="person-c"><span class="pic" style="${img ? `background-image:url('${esc(img)}')` : ""}">${img ? "" : esc(B.initials(e.node.name.full))}</span>${esc(e.node.name.full)}<small>${esc([ja && ja.name.full, en && "English: " + en.name.full].filter(Boolean).join(" · "))}</small></div>`;
      }).join("")}</div></section>`;
      if (charBox) add(charBox, html);
      else {
        const cast = box.querySelector(".people-row");
        if (cast) cast.insertAdjacentHTML("afterend", html); else add(box, html);
      }
    }
  };

  // Everything below a movie's or show's page, once its own rows are in
  // Each part goes into its own tab on the title page (panes: more, parents, chars)
  X.movie = (it, nav, panes) => {
    const p = panes || {};
    const box = p.more || $("#below");
    X.collection(it, nav, box).catch(() => {});
    X.askMore(it, nav, box).catch(() => {});
    X.anime(it, nav, box, p.chars).catch(() => {});
    X.parents(it, nav, p.parents || box).catch(() => {});
  };
  X.show = (it, nav, panes) => {
    const p = panes || {};
    const box = p.more || $("#below2");
    X.nextSeason(it, nav).catch(() => {});
    X.askMore(it, nav, box).catch(() => {});
    X.anime(it, nav, box, p.chars).catch(() => {});
    X.parents(it, nav, p.parents || box).catch(() => {});
  };

  // ---------- Not on Benflix yet (actor and director pages) ----------
  X.person = async (p, nav) => {
    const tmdb = tmdbOf(p);
    if (!tmdb || !(await requestsOn())) return;
    const d = await B.seerr(`/person/${tmdb}/combined_credits`).catch(() => null);
    if (!d || !B.alive(nav)) return;
    const today = new Date().toISOString().slice(0, 10);
    const SKIP = [10763, 10767, 10764]; // news, talk shows, reality
    const seen = new Set();
    const list = [...(d.cast || []).map((c) => [c, c.character]), ...(d.crew || []).filter((c) => c.job === "Director").map((c) => [c, "Director"])]
      .filter(([c, role]) => {
        const date = (c.mediaType === "tv" ? c.firstAirDate : c.releaseDate) || "";
        const key = c.mediaType + c.id;
        if (!date || date.slice(0, 10) > today || seen.has(key)) return false;
        if ((c.genreIds || []).some((g) => SKIP.includes(g))) return false;
        if (role && /^(self|himself|herself|themselves|narrator)\b/i.test(role)) return false;
        if (statusOf(c) > 1) return false;
        seen.add(key);
        return true;
      }).map(([c]) => c).sort((a, b) => (b.popularity || 0) - (a.popularity || 0)).slice(0, 60);
    if (!list.length) return;
    const btn = $(".rows a.btn[href^='#/requests?q=']");
    const wrap = btn ? btn.closest(".wrap") : null;
    const html = B.row(`Not on Benflix yet (${list.length})`, list.filter((c) => c.posterPath).slice(0, 30).map((c) => B.askCard(c))) +
      `<div class="wrap"><button class="btn" id="askRest">${I("plus")} Ask for several at once</button></div>`;
    if (wrap) wrap.insertAdjacentHTML("beforebegin", html); else add($(".rows"), html);
    $("#askRest").onclick = () => {
      const picked = new Set();
      const s = B.side(`<h3>Ask for everything by ${esc(p.Name)}</h3><p class="dim">Tick the ones you want. They're all asked for at once.</p>
        <div class="acts" style="justify-content:flex-start;margin:0 0 10px"><button class="btn" data-all>Tick all</button></div>
        ${list.map((c, n) => `<button class="opt" data-n="${n}">${esc(c.title || c.name)}<small>${esc([(c.releaseDate || c.firstAirDate || "").slice(0, 4), c.mediaType === "tv" ? "Show" : "Movie"].filter(Boolean).join(" · "))}</small></button>`).join("")}
        <div class="acts" style="position:sticky;bottom:-28px;background:#16181E;padding:12px 0"><button class="btn white" data-go>${I("plus")} Ask for <span id="askN">0</span></button></div>`);
      const count = () => { $("#askN", s).textContent = picked.size; };
      s.addEventListener("click", (e) => {
        const o = e.target.closest("[data-n]");
        if (o) { const n = +o.dataset.n; picked.has(n) ? picked.delete(n) : picked.add(n); o.classList.toggle("on", picked.has(n)); count(); return; }
        if (e.target.closest("[data-all]")) { list.forEach((_, n) => picked.add(n)); $$("[data-n]", s).forEach((b) => b.classList.add("on")); count(); return; }
        const go = e.target.closest("[data-go]");
        if (go) B.busy(go, async () => {
          if (!picked.size) throw new Error("Tick at least one.");
          let asked = 0;
          for (const n of picked) {
            const c = list[n];
            try {
              if (c.mediaType === "tv") {
                const t = await B.seerr(`/tv/${c.id}`);
                const seasons = (t.seasons || []).map((x) => x.seasonNumber).filter((x) => x > 0);
                if (!seasons.length) continue;
                await B.seerr("/request", "POST", { mediaType: "tv", mediaId: c.id, seasons });
              } else await B.seerr("/request", "POST", { mediaType: "movie", mediaId: c.id });
              asked++;
            } catch (e2) {}
          }
          B.closeAll();
          B.cache["seerr-mine"] = null;
          return asked === 1 ? "Asked for 1 title." : `Asked for ${asked} titles.`;
        });
      });
    };
  };

  // ---------- Search in plain words ----------
  const GENRE_WORDS = {
    funny: "Comedy", comedy: "Comedy", comedies: "Comedy", hilarious: "Comedy", scary: "Horror", horror: "Horror", spooky: "Horror",
    romantic: "Romance", romance: "Romance", action: "Action", animated: "Animation", cartoon: "Animation", cartoons: "Animation",
    family: "Family", kids: "Family", scifi: "Science Fiction", "sci-fi": "Science Fiction", space: "Science Fiction", fantasy: "Fantasy",
    magic: "Fantasy", mystery: "Mystery", detective: "Mystery", crime: "Crime", war: "War", western: "Western", westerns: "Western",
    documentary: "Documentary", documentaries: "Documentary", thriller: "Thriller", thrillers: "Thriller", suspense: "Thriller",
    drama: "Drama", sad: "Drama", adventure: "Adventure", musical: "Music", history: "History", historical: "History",
  };
  const FILLER = new Set("a an the with about from in of and or some something me find i want to watch good best great that is are for like starring stars featuring by where has have any one please really very set on".split(" "));
  const NUM = { one: 1, two: 2, three: 3, an: 1, a: 1 };
  const DECADES = { fifties: 1950, sixties: 1960, seventies: 1970, eighties: 1980, nineties: 1990 };
  const lenWords = (m) => (m % 60 === 0 && m >= 60 ? (m === 60 ? "1 hour" : `${m / 60} hours`) : `${m} minutes`);
  X.parseWords = (text) => {
    const w = String(text || "").toLowerCase().replace(/’/g, "'").replace(/[^a-z0-9' -]/g, " ").split(/\s+/).filter(Boolean);
    const q = { genres: [], types: [], anime: false, years: null, max: null, min: null, newest: false, rest: [], labels: [] };
    if (w.length < 2) return null;
    const now = new Date().getFullYear();
    for (let i = 0; i < w.length; i++) {
      const x = w[i], next = w[i + 1];
      if (["under", "less", "shorter", "over", "longer", "more"].includes(x)) {
        let j = i + 1; if (w[j] === "than") j++;
        const amount = w[j] != null ? (parseInt(w[j], 10) || NUM[w[j]]) : null, unit = w[j + 1];
        if (amount && unit && (unit.startsWith("hour") || unit.startsWith("min"))) {
          const mins = unit.startsWith("hour") ? amount * 60 : amount;
          if (["under", "less", "shorter"].includes(x)) { q.max = mins; q.labels.push("under " + lenWords(mins)); } else { q.min = mins; q.labels.push("over " + lenWords(mins)); }
          i = j + 1; continue;
        }
      }
      if (x === "short") { q.max = 95; q.labels.push("short"); continue; }
      if (x === "long") { q.min = 150; q.labels.push("long"); continue; }
      const dm = /^'?(\d{2}|\d{4})'?s$/.exec(x);
      let decade = dm ? +dm[1] : null;
      if (decade != null) decade = decade >= 1900 ? decade - (decade % 10) : decade >= 30 ? 1900 + decade - (decade % 10) : 2000 + decade - (decade % 10);
      else decade = DECADES[x] || null;
      if (decade) { q.years = [decade, decade + 9]; q.labels.push(decade + "s"); continue; }
      const yr = /^\d{4}$/.test(x) ? +x : null;
      if (yr && yr >= 1920 && yr <= now + 1) { q.years = [yr, yr]; q.labels.push(String(yr)); continue; }
      if (["new", "newest", "recent", "latest"].includes(x)) { q.newest = true; q.years = [now - 2, now]; q.labels.push("new"); continue; }
      if (["old", "classic", "classics"].includes(x)) { q.years = [1920, 1979]; q.labels.push("older"); continue; }
      if (x === "anime" || x === "animes") { q.anime = true; q.labels.push("Anime"); continue; }
      if (["movie", "movies", "film", "films"].includes(x)) { if (!q.types.includes("Movie")) q.types.push("Movie"); continue; }
      if (["show", "shows", "series", "tv"].includes(x) && !(x === "show" && next === "me")) { if (!q.types.includes("Series")) q.types.push("Series"); continue; }
      if (x === "sci" && next === "fi") { q.genres.push("Science Fiction"); i++; continue; }
      const g = GENRE_WORDS[x];
      if (g) { if (!q.genres.includes(g)) q.genres.push(g); } else if (!FILLER.has(x)) q.rest.push(x);
    }
    const understood = q.genres.length || q.types.length || q.anime || q.years || q.max || q.min || q.newest;
    if (!understood) return null;
    const typeLabel = q.types.length === 1 ? (q.types[0] === "Movie" ? "Movies" : "Shows") : null;
    q.label = [...q.genres, ...(typeLabel ? [typeLabel] : []), ...q.labels].join(" · ");
    return q;
  };
  X.searchWords = async (q) => {
    const u = B.uid();
    const types = (q.types.length ? q.types : ["Movie", "Series"]).join(",");
    let parents = [null];
    if (q.anime) {
      parents = (await B.views()).filter((v) => /anime/i.test(v.Name)).map((v) => v.Id);
      if (!parents.length) return [];
    }
    let person = null;
    if (q.rest.length >= 2) {
      const name = q.rest.join(" ");
      const ps = ((await B.get(`/Persons?userId=${u}&searchTerm=${enc(name)}&Limit=3`).catch(() => ({}))).Items || []);
      const hit = ps.find((x) => (x.Name || "").toLowerCase() === name);
      person = hit ? hit.Id : null;
    }
    const tags = !person && q.rest.length ? [...new Set(q.rest.flatMap((t) => [t, t.replace(/s$/, ""), t.charAt(0).toUpperCase() + t.slice(1)]))] : null;
    const years = q.years ? Array.from({ length: q.years[1] - q.years[0] + 1 }, (_, n) => q.years[0] + n).join(",") : "";
    const sort = q.newest ? "SortBy=PremiereDate&SortOrder=Descending" : "SortBy=CommunityRating,SortName&SortOrder=Descending,Ascending";
    const out = [];
    for (const parent of parents) {
      const url = `/Items?userId=${u}&Recursive=true&IncludeItemTypes=${types}${parent ? `&ParentId=${parent}` : ""}${q.genres.length ? `&Genres=${enc(q.genres.join("|"))}` : ""}${years ? `&Years=${years}` : ""}${person ? `&PersonIds=${person}` : ""}${tags ? `&Tags=${enc(tags.join("|"))}` : ""}&${sort}&Limit=60&Fields=${B.FIELDS}`;
      out.push(...(((await B.get(url).catch(() => ({}))).Items) || []));
    }
    const seen = new Set();
    return B.keep(out.filter((x) => {
      if (seen.has(x.Id)) return false;
      seen.add(x.Id);
      if ((q.max || q.min) && x.Type === "Movie") {
        const mins = B.minutes(x.RunTimeTicks);
        if (!mins || (q.max && mins > q.max) || (q.min && mins < q.min)) return false;
      }
      return true;
    }).slice(0, 60));
  };
})();
