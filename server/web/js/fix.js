/* Benflix web: "Fix poster and details" for the main profile (admin only).
   Change the title, year, story and age rating, pick a different poster or background picture from the
   ones Jellyfin can find online, or ask Jellyfin to look the whole thing up again. Jellyfin itself checks
   that only an admin can save these. */
"use strict";
(() => {
  const enc = encodeURIComponent;
  const KINDS = [
    { type: "Primary", label: "Poster", tall: true },
    { type: "Backdrop", label: "Background", tall: false },
    { type: "Logo", label: "Title logo", tall: false },
  ];

  B.fixItem = async (start) => {
    if (!B.me || !B.me.admin) return;
    let it;
    try { it = await B.get(`/Users/${B.uid()}/Items/${start.Id}`); } catch (e) { return B.toast(e.message); }
    let tab = "details";
    const p = B.side(`<div id="fixBody">${B.spinner()}</div>`, () => B.render());
    const body = () => p.querySelector("#fixBody");
    const tabs = () => `<h3>Fix ${esc(it.Name)}</h3><p class="dim">Changes show on every TV, phone and browser.</p>
      <div class="row" style="gap:8px;margin:14px 0 18px;flex-wrap:wrap">${[["details", "Details"], ...KINDS.map((k) => [k.type, k.label]), ["again", "Look it up again"]]
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

    const drawPictures = async (kind) => {
      body().innerHTML = tabs() + `<p class="dim">Looking for ${esc(kind.label.toLowerCase())} pictures…</p>${B.spinner()}`;
      let list = [];
      try {
        const r = await B.get(`/Items/${it.Id}/RemoteImages?type=${kind.type}&limit=40&includeAllLanguages=${kind.type === "Backdrop"}`);
        list = (r && r.Images) || [];
      } catch (e) { if (tab === kind.type) body().innerHTML = tabs() + `<p class="dim">${esc(e.message)}</p>`; return; }
      if (tab !== kind.type) return;
      body().innerHTML = tabs() + (list.length
        ? `<p class="dim">Click one to use it. ${list.length} found.</p><div class="fix-grid ${kind.tall ? "tall" : ""}">${list.map((x, i) =>
            `<button class="fix-pic" data-i="${i}" title="${esc([x.ProviderName, x.Language, x.Width && x.Height ? x.Width + "×" + x.Height : ""].filter(Boolean).join(" · "))}"><img loading="lazy" src="${esc(x.ThumbnailUrl || x.Url)}" alt=""><span>${esc(x.ProviderName || "")}${x.Language ? " · " + esc(x.Language) : ""}</span></button>`).join("")}</div>`
        : `<p class="dim">Jellyfin couldn't find any ${esc(kind.label.toLowerCase())} pictures online for this one. Try "Look it up again" first.</p>`);
      p.querySelectorAll(".fix-pic").forEach((b) => {
        b.onclick = () => {
          const x = list[+b.dataset.i];
          B.busy(b, async () => {
            await B.jf("POST", `/Items/${it.Id}/RemoteImages/Download?type=${kind.type}&imageUrl=${enc(x.Url)}${x.ProviderName ? "&providerName=" + enc(x.ProviderName) : ""}`);
            p.querySelectorAll(".fix-pic").forEach((o) => o.classList.toggle("on", o === b));
            B.cacheBust = Date.now();
            return `New ${kind.label.toLowerCase()} saved. It can take a minute to show everywhere.`;
          });
        };
      });
    };

    const drawAgain = () => {
      body().innerHTML = tabs() + `<p>Wrong film or show, or everything looks muddled? Jellyfin can look it all up again.</p>
        <button class="opt" data-again="missing">${I("restart")} Fill in what's missing<small>Keeps what's there</small></button>
        <button class="opt" data-again="all">${I("restart")} Replace everything<small>Story, cast and pictures</small></button>
        <p class="dim" style="margin-top:14px">If it keeps picking the wrong one, the file or folder name is the problem. Rename it like <b>Movie Name (2010)</b> and it will match next time.</p>`;
      p.querySelectorAll("[data-again]").forEach((b) => {
        b.onclick = () => B.busy(b, async () => {
          const all = b.dataset.again === "all";
          await B.jf("POST", `/Items/${it.Id}/Refresh?metadataRefreshMode=FullRefresh&imageRefreshMode=FullRefresh&replaceAllMetadata=${all}&replaceAllImages=${all}`);
          return "Looking it up again. Give it a minute, then open the page again.";
        });
      });
    };

    const draw = () => {
      if (tab === "details") drawDetails();
      else if (tab === "again") drawAgain();
      else drawPictures(KINDS.find((k) => k.type === tab));
    };
    p.addEventListener("click", (e) => {
      const t = e.target.closest("[data-tab]");
      if (!t) return;
      tab = t.dataset.tab;
      draw();
    });
    draw();
  };
})();
