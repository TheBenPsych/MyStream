/* Benflix web: "Fix poster and details" for the main profile (admin only).
   Change the title, year, story and age rating, pick a different poster or background picture from the
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
    const p = B.side(`<div id="fixBody">${B.spinner()}</div>`, () => B.render());
    const body = () => p.querySelector("#fixBody");
    const tabs = () => `<h3>Fix ${esc(it.Name)}</h3><p class="dim">Changes show on every TV, phone and browser.</p>
      <div class="row" style="gap:8px;margin:14px 0 18px;flex-wrap:wrap">${[["details", "Details"], ...kinds().map((k) => [k.type, k.label]), ["again", "Look it up again"]]
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
      if (!target) { if (tab === kind.type) body().innerHTML = tabs() + `<p class="dim">This show has no seasons in Benflix yet.</p>`; return; }
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
})();
