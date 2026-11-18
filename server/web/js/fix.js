/* Benflix web: "Fix poster and details" for the main profile (admin only).
   Fix a wrong match (pick the right film or show), change the title, year, story and age rating, pick a different poster or background picture from the
   ones Jellyfin can find online, or ask Jellyfin to look the whole thing up again. Jellyfin itself checks
   that only an admin can save these. */
"use strict";
(() => {
  const enc = encodeURIComponent;
  const KINDS = [
    { type: "Primary", img: "Primary", label: "Poster", tall: true },
    { type: "Seasons", img: "Primary", label: "Season posters", tall: true, seasons: true },
    { type: "Backdrop", img: "Backdrop", label: "Background", tall: false },
    { type: "Logo", img: "Logo", label: "Title logo", tall: false },
  ];

  B.fixItem = async (start) => {
    if (!B.me || !B.me.admin) return;
    let it;
    try { it = await B.get(`/Users/${B.uid()}/Items/${start.Id}`); } catch (e) { return B.toast(e.message); }
    let tab = (start && start.fixTab) || "details";
    // Seasons only for shows; title logos not for seasons or collections
    // Albums, artists, episodes, people, books and channels (Redesign 2: Pick a picture on every card) have just their one picture
    const titleKind = /^(Movie|Series|Season|BoxSet)$/.test(it.Type);
    const kinds = () => KINDS.filter((k) => (!titleKind ? k.type === "Primary" : k.seasons ? it.Type === "Series" : k.type !== "Logo" || (it.Type !== "Season" && it.Type !== "BoxSet")));
    // "Fix wrong match" (Jellyfin's Identify): search the movie and show sites and pick the right one
    const canMatch = it.Type === "Movie" || it.Type === "Series";
    const p = B.side(`<div id="fixBody">${B.spinner()}</div>`, () => B.render());
    const body = () => p.querySelector("#fixBody");
    const tabs = () => `<h3>Fix ${esc(it.Name)}</h3><p class="dim">Changes show on every TV, phone and browser.</p>
      <div class="row" style="gap:8px;margin:14px 0 18px;flex-wrap:wrap">${[["details", "Details"], ...(canMatch ? [["match", "Fix wrong match"]] : []), ...kinds().map((k) => [k.type, k.label]), ["again", "Look it up again"]]
        .map(([id, label]) => `<button class="chip ${tab === id ? "on" : ""}" data-tab="${id}">${esc(label)}</button>`).join("")}</div>`;

    const drawDetails = () => {
      body().innerHTML = tabs() + `<form id="fixForm">
        <label class="dim">Title</label><input class="field" name="Name" value="${esc(it.Name || "")}" style="margin:4px 0 12px">
        <label class="dim">Year</label><input class="field" name="ProductionYear" inputmode="numeric" value="${esc(it.ProductionYear || "")}" style="margin:4px 0 12px">
        <label class="dim">Age rating (like PG-13 or TV-14)</label><input class="field" name="OfficialRating" value="${esc(it.OfficialRating || "")}" style="margin:4px 0 12px">
        <label class="dim">Genres (with commas between)</label><input class="field" name="Genres" value="${esc((it.Genres || []).join(", "))}" style="margin:4px 0 12px">
        <label class="dim">Story</label><textarea class="field" name="Overview" style="margin:4px 0 12px;min-height:140px">${esc(it.Overview || "")}</textarea>
        <label class="row" style="gap:10px;margin:4px 0 16px"><input type="checkbox" name="lock" ${(it.LockData ? "checked" : "")}> Keep my changes (Jellyfin won't overwrite them later)</label>
        <div class="acts"><button class="btn white" type="submit">${I("check")} Save</button></div></form>`;
      p.querySelector("#fixForm").onsubmit = (e) => {
        e.preventDefault();
        const f = e.target;
        const year = parseInt(f.ProductionYear.value, 10);
        B.busy(f.querySelector("[type=submit]"), async () => {
          // Jellyfin wants the whole item back with the changes in it
          const full = await B.get(`/Items/${it.Id}?userId=${B.uid()}&Fields=Overview,Genres,Tags,Studios,People,ProviderIds,Taglines,LockedFields,ProductionLocations,DateCreated,PremiereDate,Path`)
            .then((r) => (r && r.Items ? r.Items[0] : r)).catch(() => null) || it;
          const out = Object.assign({}, full, {
            Name: f.Name.value.trim() || full.Name,
            ProductionYear: isNaN(year) ? null : year,
            OfficialRating: f.OfficialRating.value.trim() || null,
            Genres: f.Genres.value.split(",").map((g) => g.trim()).filter(Boolean),
            Overview: f.Overview.value.trim(),
            LockData: f.lock.checked,
          });
          if (out.Genres.length) out.GenreItems = out.Genres.map((g) => ({ Name: g }));
          await B.jf("POST", `/Items/${it.Id}`, out);
          it = Object.assign(it, out);
          return "Saved.";
        });
      };
    };

    // "Try it on first": the picture there now sits big at the top; clicking a choice puts it there,
    // and "Use this" keeps it. English first, then pictures with no words, then other languages.
    let season = null, seasons = null, source = "", preview = null, list = [];
    const drawPictures = async (kind) => {
      body().innerHTML = tabs() + `<p class="dim">Looking online…</p>${B.spinner()}`;
      if (kind.seasons && !seasons) {
        seasons = await B.get(`/Shows/${it.Id}/Seasons?userId=${B.uid()}`).then((r) => (r && r.Items) || []).catch(() => []);
        season = season || (seasons[0] && seasons[0].Id);
      }
      const target = kind.seasons ? season : it.Id;
      if (!target) { if (tab === kind.type) body().innerHTML = tabs() + `<p class="dim">This show has no seasons in Glowfin yet.</p>`; return; }
      let now = null;
      try {
        [list, now] = await Promise.all([B.posters.choices(target, kind.img), B.get(`/Users/${B.uid()}/Items/${target}`)]);
      } catch (e) { if (tab === kind.type) body().innerHTML = tabs() + `<p class="dim">${esc(e.message)}</p>`; return; }
      if (tab !== kind.type) return;
      preview = null; source = "";
      const nowTag = kind.img === "Backdrop" ? (now.BackdropImageTags || [])[0] : (now.ImageTags || {})[kind.img];
      const nowUrl = nowTag ? B.img(target, kind.img, { h: kind.tall ? 900 : 540, tag: nowTag }) : "";
      const sources = [...new Set(list.map((x) => x.ProviderName || "Online"))];
      const draw = () => {
        const shown = list.filter((x) => !source || (x.ProviderName || "Online") === source);
        const pv = preview ? preview.Url : nowUrl;
        body().innerHTML = tabs()
          + (kind.seasons ? `<div class="row" style="gap:8px;margin:0 0 14px;flex-wrap:wrap">${seasons.map((x) => `<button class="chip ${x.Id === season ? "on" : ""}" data-season="${x.Id}">${esc(x.Name || "Season " + x.IndexNumber)}</button>`).join("")}</div>` : "")
          + `<div class="fix-try ${kind.tall ? "tall" : ""}">
              <div class="fix-big">${pv ? `<img src="${esc(pv)}" alt="" style="object-fit:${kind.img === "Logo" ? "contain" : "cover"}">` : `<span class="dim">None yet</span>`}</div>
              <div class="fix-side"><p class="dim">${esc(preview ? B.posters.describe(preview) : nowUrl ? "The one there now" : "There's none yet")}</p>
              <button class="btn white" id="fixUse" ${preview ? "" : "disabled"}>${I("check")} Use this ${esc(kind.label.toLowerCase())}</button></div></div>`
          + (sources.length > 1 ? `<div class="row" style="gap:8px;margin:14px 0;flex-wrap:wrap">${["", ...sources].map((x) => `<button class="chip ${x === source ? "on" : ""}" data-src="${esc(x)}">${esc(x || "Everywhere")}</button>`).join("")}</div>` : `<div style="height:14px"></div>`)
          + (shown.length
            ? `<div class="fix-grid ${kind.tall ? "tall" : ""}">${shown.map((x) =>
                `<button class="fix-pic ${x === preview ? "on" : ""}" data-i="${list.indexOf(x)}" title="${esc(B.posters.describe(x))}"><img loading="lazy" src="${esc(x.ThumbnailUrl || x.Url)}" alt="" style="object-fit:${kind.img === "Logo" ? "contain" : "cover"}"><span>${esc(x.ProviderName || "")}${x.Language ? " · " + esc(x.Language.toUpperCase()) : ""}</span></button>`).join("")}</div>`
            : `<p class="dim">Jellyfin couldn't find any online. For shows, turning on the TheTVDB plugin in Jellyfin finds more (Dashboard, Plugins, Catalog).</p>`);
        p.querySelectorAll("[data-season]").forEach((b) => { b.onclick = () => { season = b.dataset.season; drawPictures(kind); }; });
        p.querySelectorAll("[data-src]").forEach((b) => { b.onclick = () => { source = b.dataset.src; draw(); }; });
        p.querySelectorAll(".fix-pic").forEach((b) => { b.onclick = () => { preview = list[+b.dataset.i]; draw(); const u = $("#fixUse"); if (u) u.focus(); }; });
        const use = $("#fixUse");
        if (use) use.onclick = () => B.busy(use, async () => {
          await B.posters.use(target, kind.img, preview);
          B.cacheBust = Date.now();
          drawPictures(kind);
          return `New ${kind.label.toLowerCase()} saved. It can take a minute to show everywhere.`;
        });
      };
      draw();
    };

    const drawMatch = (name, year, results) => {
      const kind = it.Type === "Series" ? "Series" : "Movie";
      body().innerHTML = tabs() + `<p>Showing the wrong ${kind === "Series" ? "show" : "film"}? Search for the right one and pick it. Story, cast and pictures all switch over.</p>
        <form id="matchForm" class="row" style="gap:8px;flex-wrap:wrap;margin:12px 0">
          <input class="field" name="name" value="${esc(name)}" placeholder="Name" style="flex:1 1 220px">
          <input class="field" name="year" value="${esc(year || "")}" placeholder="Year" inputmode="numeric" style="width:90px">
          <button class="btn white" type="submit">${I("search")} Search</button></form>
        <div id="matchList">${results === undefined ? "" : !results ? B.spinner() : !results.length ? `<p class="dim">Nothing found. Try fewer words or no year.</p>`
          : results.map((r, i) => `<button class="opt" data-match="${i}" style="display:flex;gap:12px;align-items:center;text-align:left">
              ${r.ImageUrl ? `<img src="${esc(r.ImageUrl)}" alt="" style="width:54px;height:80px;object-fit:cover;border-radius:6px;flex:none">` : ""}
              <span><b>${esc(r.Name)}</b>${r.ProductionYear ? ` (${esc(r.ProductionYear)})` : ""}<small>${esc(r.SearchProviderName || "")}${r.Overview ? " · " + esc(String(r.Overview).slice(0, 140)) + (String(r.Overview).length > 140 ? "…" : "") : ""}</small></span></button>`).join("")}</div>`;
      p.querySelector("#matchForm").onsubmit = async (e) => {
        e.preventDefault();
        const f = e.target;
        const n = f.name.value.trim(), y = parseInt(f.year.value, 10);
        if (!n) return;
        drawMatch(n, isNaN(y) ? "" : y, null);
        try {
          const found = await B.jf("POST", `/Items/RemoteSearch/${kind}`, { ItemId: it.Id, SearchInfo: { Name: n, Year: isNaN(y) ? null : y } });
          if (tab === "match") drawMatch(n, isNaN(y) ? "" : y, found || []);
        } catch (err) { if (tab === "match") { drawMatch(n, y, []); B.toast(err.message); } }
      };
      p.querySelectorAll("[data-match]").forEach((b) => {
        b.onclick = () => B.busy(b, async () => {
          await B.jf("POST", `/Items/RemoteSearch/Apply/${it.Id}?replaceAllImages=true`, results[+b.dataset.match]);
          return "Switched. Give it a minute, then open the page again.";
        });
      });
    };

    const drawAgain = () => {
      body().innerHTML = tabs() + `<p>Wrong film or show, or everything looks muddled? Jellyfin can look it all up again.</p>
        <button class="opt" data-again="missing">${I("restart")} Fill in what's missing<small>Keeps what's there</small></button>
        <button class="opt" data-again="all">${I("restart")} Replace everything<small>Story, cast and pictures. Pictures you picked yourself stay.</small></button>
        <p class="dim" style="margin-top:14px">If it keeps picking the wrong one, the file or folder name is the problem. Rename it like <b>Movie Name (2010)</b> and it will match next time.</p>`;
      p.querySelectorAll("[data-again]").forEach((b) => {
        b.onclick = () => B.busy(b, async () => {
          const all = b.dataset.again === "all";
          const kept = all && (await B.posters.kept().catch(() => new Set())).has(it.Id);
          await B.jf("POST", `/Items/${it.Id}/Refresh?metadataRefreshMode=FullRefresh&imageRefreshMode=FullRefresh&replaceAllMetadata=${all}&replaceAllImages=${all && !kept}`);
          return "Looking it up again. Give it a minute, then open the page again.";
        });
      });
    };

    const draw = () => {
      if (tab === "details") drawDetails();
      else if (tab === "match") drawMatch(it.Name || "", it.ProductionYear || "", undefined);
      else if (tab === "again") drawAgain();
      else drawPictures(KINDS.find((k) => k.type === tab) || KINDS[0]);
    };
    p.addEventListener("click", (e) => {
      const t = e.target.closest("[data-tab]");
      if (!t) return;
      tab = t.dataset.tab;
      draw();
    });
    draw();
  };
  // ---------- Shared poster helpers (Change poster here, Fix posters on the Server page) ----------
  // Pictures come from TMDB, TheTVDB and Fanart.tv through Jellyfin. Benflix never contacts ThePosterDB.
  // "kept" = titles whose pictures you picked yourself (Refresh keeps them), "skip" = "Leave it" in the fixer.
  // They live in the main profile's display preferences, shared with the TV and phone app.
  const PREFS = () => `/DisplayPreferences/benflix-posters?userId=${B.uid()}&client=Wholphin`;
  const readPrefs = () => B.get(PREFS()).catch(() => null);
  const setOf = (prefs, key) => new Set(((prefs && prefs.CustomPrefs && prefs.CustomPrefs[key]) || "").split(",").filter(Boolean));
  const addTo = async (key, id) => {
    const prefs = (await readPrefs()) || { Id: "benflix-posters", CustomPrefs: {} };
    const now = setOf(prefs, key); now.add(id);
    prefs.CustomPrefs = Object.assign({}, prefs.CustomPrefs, { [key]: [...now].join(",") });
    await B.jf("POST", PREFS(), prefs);
  };
  const LANG = (x, img) => {
    const l = String(x.Language || "").toLowerCase();
    if (img === "Backdrop") return !l ? 0 : l === "en" ? 1 : 2;
    return l === "en" ? 0 : !l ? 1 : 2;
  };
  const langName = (l) => {
    if (!l) return "No words";
    if (String(l).toLowerCase() === "en") return "English";
    try { return new Intl.DisplayNames(["en"], { type: "language" }).of(l) || l; } catch (e) { return l; }
  };
  B.posters = {
    describe: (x) => [x.ProviderName || "Online", x.Width && x.Height ? `${x.Width} × ${x.Height}` : "", langName(x.Language)].filter(Boolean).join(" · "),
    choices: async (id, img) => {
      const r = await B.get(`/Items/${id}/RemoteImages?type=${img}&limit=300&includeAllLanguages=true`);
      return ((r && r.Images) || []).filter((x) => x.Url).sort((a, b) =>
        LANG(a, img) - LANG(b, img) || (b.VoteCount || 0) - (a.VoteCount || 0) || (b.CommunityRating || 0) - (a.CommunityRating || 0) || (b.Width || 0) - (a.Width || 0));
    },
    // Backgrounds can be more than one and Jellyfin adds the new one at the end, so the old ones come off after
    use: async (id, img, choice) => {
      let before = 0;
      if (img === "Backdrop") before = ((await B.get(`/Items/${id}/Images`)) || []).filter((x) => x.ImageType === "Backdrop").length;
      await B.jf("POST", `/Items/${id}/RemoteImages/Download?type=${img}&imageUrl=${enc(choice.Url)}`);
      for (let i = before - 1; i >= 0; i--) await B.jf("DELETE", `/Items/${id}/Images/Backdrop/${i}`).catch(() => null);
      await addTo("kept", id).catch(() => null);
    },
    kept: async () => setOf(await readPrefs(), "kept"),
    skipped: async () => setOf(await readPrefs(), "skip"),
    leave: (id) => addTo("skip", id),
  };

  // Idea Box 25: "Kids profile" for one movie or show (Ben only). Let it through, hide it, or follow its age rating,
  // kept as Jellyfin's custom rating (it passes down to every season and episode), and put it on the Kids home.
  const KIDS_OK = ["G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG", "U", "UC", "ALL", "APPROVED", "0", "6", "7", "FSK-0", "FSK-6"];
  const PICK = "benflix-kids-pick";
  const ruleOf = (c) => { const r = String(c || "").trim().toUpperCase(); return r === "PG" || r === "TV-PG" ? "let" : r === "R" || r === "TV-MA" ? "hide" : "follow"; };
  B.kidsChoice = async (start) => {
    if (!B.me || !B.me.admin || B.me.kids || !start) return;
    const id = (start.Type === "Episode" || start.Type === "Season") && start.SeriesId ? start.SeriesId : start.Id;
    const p = B.side(`<div id="kcBody">${B.spinner()}</div>`);
    const body = () => p.querySelector("#kcBody");
    const load = () => B.get(`/Items/${id}?userId=${B.uid()}&Fields=Overview,Genres,Tags,Studios,People,ProviderIds,Taglines,LockedFields,ProductionLocations,DateCreated,PremiereDate,Path`)
      .then((r) => (r && r.Items ? r.Items[0] : r));
    let it;
    try { it = await load(); } catch (e) { B.closeAll(); return B.toast(e.message); }
    if (!it || !body()) return;
    const show = it.Type === "Series";
    const draw = () => {
      const rule = ruleOf(it.CustomRating), picked = (it.Tags || []).some((t) => t.toLowerCase() === PICK);
      const rating = it.OfficialRating ? `rated ${it.OfficialRating}` : "no rating";
      const ok = KIDS_OK.includes(String(it.OfficialRating || "").toUpperCase());
      const opt = (key, label, line) => `<button class="opt ${rule === key ? "on" : ""}" data-rule="${key}">${I(rule === key ? "check" : "more")} ${esc(label)}<small>${esc(line)}</small></button>`;
      body().innerHTML = `<h3>Kids profile</h3><p class="dim">${esc(it.Name)}</p>
        ${opt("let", "Let it through", "Kids and Big kids can see it")}
        ${opt("hide", "Hide it", "Kids and Big kids never see it")}
        ${opt("follow", "Follow its age rating", `It's ${rating}, so Kids ${ok ? "can" : "can't"} see it`)}
        <h4 class="dim" style="margin:16px 0 8px">Kids home</h4>
        <button class="opt" data-pick="${picked ? "off" : "on"}">${I(picked ? "check" : "plus")} ${picked ? "Take off the Kids home" : "Put on the Kids home"}<small>The "Picked for you" row</small></button>`;
    };
    draw();
    const save = async (rule, pick) => {
      const full = await load();
      const out = Object.assign({}, full);
      if (rule) out.CustomRating = rule === "let" ? (show ? "TV-PG" : "PG") : rule === "hide" ? (show ? "TV-MA" : "R") : null;
      if (pick != null) out.Tags = pick ? [...new Set([...(full.Tags || []), PICK])] : (full.Tags || []).filter((t) => t.toLowerCase() !== PICK);
      await B.jf("POST", `/Items/${id}`, out);
      it = Object.assign(it, { CustomRating: out.CustomRating, Tags: out.Tags || it.Tags });
    };
    p.onclick = (e) => {
      const b = e.target.closest("[data-rule],[data-pick]"); if (!b) return;
      B.busy(b, async () => {
        if (b.dataset.rule) {
          const r = b.dataset.rule;
          await save(r, null);
          draw();
          return r === "let" ? `Kids and Big kids can now see ${it.Name}.` : r === "hide" ? `${it.Name} is now hidden from Kids and Big kids.` : `${it.Name} follows its age rating again.`;
        }
        const on = b.dataset.pick === "on";
        const r = ruleOf(it.CustomRating);
        // A title Kids can't see yet is let through too, so it really shows up on the Kids home
        const letIn = on && r !== "let" && (r === "hide" || !KIDS_OK.includes(String(it.OfficialRating || "").toUpperCase()));
        await save(letIn ? "let" : null, on);
        draw();
        return !on ? `${it.Name} is off the Kids home.` : letIn ? `${it.Name} is on the Kids home. It wasn't rated for Kids, so it's let through too.` : `${it.Name} is on the Kids home.`;
      });
    };
  };
})();
