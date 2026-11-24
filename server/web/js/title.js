/* Benflix web: the shared title page (Redesign 2, section 1, approved 2026-11-10). The same frame as the
   TV and phone app: a still picture (no trailer behind it), the title with a short line in the door's
   colour, the info line, up to 5 buttons (greyed-out ones are faded, can still be reached, and say why),
   then tabs. A tab opens on click and only loads the first time it's opened; empty tabs are hidden.
   Overview = the whole story + one slim strip on the left, Details on the right (stacked on phones).
   On phone-size windows the picture sits across the top and a slim Play bar shows once Play scrolls away.

   Everything lives on B.titlePage so nothing clashes with other files (they share one scope). */
"use strict";

(() => {
  const MAX_BUTTONS = 5;
  // The rows Kids doesn't see on a title page
  const KIDS_HIDE = ["tpCast", "tpChars", "tpAsk", "tpReviews"];

  // opts: { title, original, color, picture, cover: { url, ratio }, info: [..], rating, infoExtra,
  //         buttons: [{ id, label, icon, href, off, note, onClick }], status,
  //         story, strip, overviewExtra, tabs: [{ name, id, html, load(pane), keep }], open }
  B.titlePage = (opts) => {
    // The "where am I" line names this page by its title
    const pageName = opts.title || "";
    B.placeName = pageName;
    const buttons = (opts.buttons || []).filter(Boolean).slice(0, MAX_BUTTONS);
    // Glowfin for Kids (K1, ui-17): just Play, big and round (Episodes is the row under it). No age box, cast,
    // parents' guide or asking. The other buttons stay on the page, hidden, so nothing that looks for them breaks.
    const kids = !!(B.me && B.me.kids);
    const kidsHide = (id) => kids && KIDS_HIDE.includes(id);
    // Glowfin D1 (Ben 2026-11-18): opts.rows = no tab line, the story sits under the info line and every tab is a
    // row underneath, one after another (Overview becomes "About" near the end)
    const rows = !!opts.rows;
    // Books (B1): opts.aboutFirst puts "About this book" (opts.aboutName) right under the buttons, story in big text
    const about = { name: opts.aboutName || "About", id: "tpOverview", keep: true };
    const tabs = rows
      ? (opts.aboutFirst ? [about, ...(opts.tabs || [])] : [...(opts.tabs || []), about]).filter((t) => !t.skip)
      : [{ name: "Overview", id: "tpOverview", keep: true }, ...(opts.tabs || [])].filter((t) => !t.skip);
    if (opts.overviewFirst === false) {
      const o = tabs.shift();
      tabs.splice(1, 0, o);
    }
    const btn = (b, i) => {
      const cls = `btn big ${i === 0 ? "white" : ""} ${b.off ? "off" : ""}`;
      const attrs = `${b.id ? `id="${b.id}"` : ""} data-tpb="${i}" ${b.off ? `aria-disabled="true"` : ""} ${kids && i > 0 ? "hidden" : ""}`;
      // Resume (Ben's pick): a thin progress line inside the button on every screen (drawn over its bottom edge, so the
      // button keeps its size); phones also show the time left inside it, where the button is full width anyway
      const inner = `${I(b.icon || "play")} <span>${esc(b.label)}</span>${b.left ? `<span class="tp-left">${esc(b.left)}</span>` : ""}${b.pct ? `<i class="tp-prog"><i style="width:${Math.max(2, Math.min(100, b.pct))}%"></i></i>` : ""}`;
      return b.href && !b.off ? `<a class="${cls}" ${attrs} href="${b.href}">${inner}</a>` : `<button class="${cls}" ${attrs}>${inner}</button>`;
    };
    const chips = kids ? [] : (opts.chips || []).filter(Boolean);
    // Glowfin details pages ("fresh" pick, 2026-11-22): on rows pages the age rating moves into the score pill
    const meta = [opts.rating && !kids && !rows ? `<span class="rate">${esc(opts.rating)}</span>` : "", ...(opts.info || []).filter(Boolean).map((x) => `<span>${esc(x)}</span>`)].filter(Boolean).join("");
    // Covers (games, books, albums, people): the cover blurred big behind everything (no more tinted box), and
    // the cover itself on the right at its own real shape (never cropped). Title and words stay on the left.
    const coverUrl = opts.cover && opts.cover.url ? String(opts.cover.url).replace(/"/g, "%22") : "";
    const pic = opts.cover
      ? `${coverUrl ? `<div class="tp-blur" style="background-image:url(&quot;${esc(coverUrl)}&quot;)"></div><img class="tp-cover ${opts.cover.ratio >= 1 ? "sq" : ""}" src="${esc(coverUrl)}" alt="">` : ""}`
      : "";
    // The whole story on the left, Details (studio, released, the file for you) on the right; one under the other on phones
    const overview = `${rows ? `<h3 class="tp-h">${esc(about.name)}</h3>` : ""}<div class="tp-ov"><div class="tp-ovl">${rows ? (opts.aboutStory ? `<div class="ov tp-about">${esc(opts.aboutStory)}</div>` : "") : `<div class="ov">${esc(opts.story || "")}</div>`}
      <div class="scores tp-strip">${opts.strip || ""}</div>${opts.overviewExtra || ""}</div><div class="tp-ovr" id="tpOvDetails"></div></div>`;
    B.page().innerHTML = B.top("") + `<div class="details tp ${rows ? "tp-rows" : ""}" style="--door:${opts.color || "var(--ink)"}">
      <div class="tp-hero ${opts.cover ? "covered" : ""}" style="${opts.picture ? `background-image:url('${opts.picture}')` : ""}">${pic}
        <div class="main">
          <div class="tp-name">${opts.kicker ? `<span class="tp-kick">${esc(opts.kicker)}</span>` : ""}${opts.logo ? `<img class="tp-logo" id="tpLogo" src="${esc(opts.logo)}" alt="${esc(opts.title || "")}" hidden>` : ""}<h1 class="t">${esc(opts.title || "")}</h1>${opts.original && opts.original !== opts.title ? `<div class="t2">${esc(opts.original)}</div>` : opts.tagline && !kids ? `<div class="t2 tp-tag">${esc(opts.tagline)}</div>` : ""}<i class="tp-line"></i></div>
          <div class="meta">${meta}${opts.infoExtra || ""}</div>
          ${rows && !kids && (opts.rating || opts.scores || opts.matchId) ? B.scoreIcons(opts.rating, opts.scores, opts.matchId, "tp-icons") : ""}
          ${chips.length ? `<div class="tp-chips">${chips.map((c) => `<span>${esc(c)}</span>`).join("")}</div>` : ""}
          ${rows ? `<button type="button" class="tp-story tp-open" id="tpStory">${opts.story ? `<span class="tp-st">${esc(opts.story)}</span>` : ""}<b>Story and details ›</b></button>` : ""}
          <div class="btns">${buttons.map(btn).join("")}</div>
          <div class="tp-why" id="tpWhy">${esc(opts.status || "")}</div>
          <div class="tp-tabs" role="tablist" ${rows ? "hidden" : ""}>${tabs.map((t) => `<button role="tab" class="tp-tab" data-tab="${t.id}">${esc(t.name)}</button>`).join("")}</div>
        </div>
      </div>
      <div class="tp-panes">${tabs.map((t) => `<div class="tp-pane" id="${t.id}" hidden>${t.id === "tpOverview" ? overview : t.html || ""}</div>`).join("")}</div>
      ${buttons[0] ? `<div class="tp-bar" id="tpBar" hidden><b>${esc(opts.title || "")}</b><span class="dim">${esc(buttons[0].note || opts.status || "")}</span>${btn(Object.assign({}, buttons[0], { id: "" }), 0)}</div>` : ""}
    </div>`;
    B.wireTop();
    const root = $(".details.tp");
    // Logo art instead of the typed name, only when it loads and has a sensible shape (very long or very tall
    // logos, common with anime, keep the typed name)
    // Ben 2026-11-24 (missing logos): no logo, or one that won't load, asks Tower to find one. Tower says if Jellyfin
    // has a working one, or puts on the best English one from TMDB / TheTVDB / Fanart.tv (never replacing a good one).
    const findLogo = () => {
      if (!rows || !opts.logoId || B._logoAsked?.has(opts.logoId)) return;
      (B._logoAsked = B._logoAsked || new Set()).add(opts.logoId);
      B.call("POST", `api/posters/logo?id=${encodeURIComponent(opts.logoId)}`).then((r) => {
        if (!r || !r.has || !document.body.contains(root)) return;
        const url = B.img(opts.logoId, "Logo", { tag: r.tag || "" }) + "&maxHeight=220&format=Png";
        if (url === opts.logo) return;
        const name = root.querySelector(".tp-name .t");
        if (!name) return;
        const img = document.createElement("img");
        img.className = "tp-logo"; img.alt = opts.title || ""; img.hidden = true;
        img.addEventListener("load", () => { if (!img.naturalWidth) return img.remove(); img.hidden = false; const l = B.tpLight(img); if (l != null && l < 0.3) img.classList.add("tp-logo-white"); name.classList.add("tp-hid"); });
        img.addEventListener("error", () => img.remove());
        name.before(img); img.src = url;
      }).catch(() => {});
    };
    const logo = $("#tpLogo");
    if (!logo) findLogo();
    if (logo) {
      const fit = () => {
        // Ben 2026-11-23 (logo plan): details pages show the full logo in any shape, shrunk to fit the fixed box
        if (!logo.naturalWidth || !logo.naturalHeight) { logo.remove(); return findLogo(); }
        logo.hidden = false;
        // A logo drawn in a dark colour is drawn in white, like the rest of the words (Ben 2026-11-22)
        if (rows) { const l = B.tpLight(logo); if (l != null && l < 0.3) logo.classList.add("tp-logo-white"); }
        const h1 = root.querySelector(".tp-name .t"); if (h1) h1.classList.add("tp-hid");
      };
      if (logo.complete && logo.naturalWidth) fit(); else { logo.addEventListener("load", fit); logo.addEventListener("error", () => { logo.remove(); findLogo(); }); }
    }
    // The story you can open: the full story plus the details (About) in a tall panel from the right
    const storyBtn = $("#tpStory");
    if (storyBtn) {
      storyBtn.addEventListener("click", () => {
        const det = $("#tpOverview");
        const box = det ? det.cloneNode(true) : null;
        if (box) { box.removeAttribute("id"); box.hidden = false; box.querySelectorAll("[id]").forEach((x) => x.removeAttribute("id")); const h = box.querySelector(".tp-h"); if (h) h.remove(); }
        B.side(`<h3>Story and details</h3><p class="dim">${esc(opts.title || "")}</p>${opts.story ? `<p class="tp-full">${esc(opts.story)}</p>` : ""}<div class="tp-sidedet">${box ? box.innerHTML : ""}</div>`);
      });
    }
    // A very bright picture is turned down a little and the words get a faint edge (Ben 2026-11-22)
    if (rows && opts.picture && !opts.cover) {
      const im = new Image();
      im.onload = () => { const l = B.tpLight(im); if (l != null && l > 0.62) root.classList.add("tp-bright"); };
      im.src = opts.picture;
    }
    // Going down the page: the top shrinks to a small title (the art dims with a plain fade)
    if (rows) {
      const hero = root.querySelector(".tp-hero");
      const onScroll = () => { if (!document.body.contains(root)) return removeEventListener("scroll", onScroll); root.classList.toggle("tp-down", scrollY > hero.offsetHeight * .45); };
      addEventListener("scroll", onScroll, { passive: true });
    }
    // Buttons: greyed-out ones say why; the rest run their action
    const why = $("#tpWhy");
    // Phones show Resume's time left inside the button itself, so the line under the buttons doesn't repeat it
    const say = (b) => { why.textContent = b ? b.off || (b.left && matchMedia("(max-width: 700px)").matches ? "" : b.note) || opts.status || "" : opts.status || ""; why.classList.toggle("off", !!(b && b.off)); };
    root.addEventListener("click", (e) => {
      const el = e.target.closest("[data-tpb]"); if (!el) return;
      const b = buttons[+el.dataset.tpb];
      if (b.off) { e.preventDefault(); say(b); return; }
      if (b.onClick) { e.preventDefault(); b.onClick(e, el); }
    });
    $$(".tp .btns [data-tpb]").forEach((el) => {
      const b = buttons[+el.dataset.tpb];
      el.addEventListener("mouseenter", () => say(b));
      el.addEventListener("focus", () => say(b));
      el.addEventListener("mouseleave", () => say(null));
      el.addEventListener("blur", () => say(null));
    });
    // Tabs: load the first time, hide empty ones
    const loaded = new Set();
    const empty = (pane) => !pane.textContent.trim() && !pane.querySelector("img,[style*='background-image']");
    const refresh = () => {
      tabs.forEach((t) => {
        const pane = $("#" + t.id), b = $(`.tp-tab[data-tab="${t.id}"]`);
        if (!pane || !b) return;
        b.hidden = !t.keep && !t.load && empty(pane);
      });
      const on = $(".tp-tab.on");
      if (on && on.hidden) show(tabs.find((t) => !$(`.tp-tab[data-tab="${t.id}"]`).hidden));
    };
    const show = (t) => {
      if (!t) return;
      if (rows) {
        // Rows: every row shows at once; open() scrolls to it
        const pane = $("#" + t.id);
        if (pane) pane.scrollIntoView({ block: "start", behavior: "smooth" });
        return;
      }
      $$(".tp-tab").forEach((b) => b.classList.toggle("on", b.dataset.tab === t.id));
      $$(".tp-pane").forEach((p) => { p.hidden = p.id !== t.id; });
      if (t.load && !loaded.has(t.id)) {
        loaded.add(t.id);
        const pane = $("#" + t.id);
        if (empty(pane)) pane.insertAdjacentHTML("afterbegin", B.spinner());
        Promise.resolve(t.load(pane)).catch(() => {}).then(() => {
          [...pane.children].filter((c) => c.classList.contains("spin")).forEach((c) => c.remove());
          t.load = null;
          refresh();
        });
      }
    };
    $(".tp-tabs").addEventListener("click", (e) => {
      const b = e.target.closest("[data-tab]"); if (!b) return;
      show(tabs.find((t) => t.id === b.dataset.tab));
    });
    // Anything added to a pane later (extras.js) shows its tab
    const watch = new MutationObserver(() => refresh());
    $$(".tp-pane").forEach((p) => watch.observe(p, { childList: true, subtree: true }));
    B.onLeave(() => watch.disconnect());
    // Phones: the slim Play bar once Play has scrolled away
    const first = $(".tp .btns [data-tpb='0']"), bar = $("#tpBar");
    if (first && bar && "IntersectionObserver" in window) {
      const io = new IntersectionObserver(([en]) => { bar.hidden = en.isIntersecting || window.innerWidth > 700; });
      io.observe(first);
      B.onLeave(() => io.disconnect());
    }
    const opening = tabs.find((t) => t.name === opts.open) || tabs[0];
    if (rows) {
      // Every row shows and loads now (each only once); an empty one hides itself
      watch.disconnect();
      const refreshRows = () => tabs.forEach((t) => { const pane = $("#" + t.id); if (pane) pane.hidden = kidsHide(t.id) || (!t.keep && empty(pane)); });
      const watchRows = new MutationObserver(() => refreshRows());
      $$(".tp-pane").forEach((p) => watchRows.observe(p, { childList: true, subtree: true }));
      B.onLeave(() => watchRows.disconnect());
      tabs.filter((t) => t.load).forEach((t) => {
        const go = t.load;
        t.load = null;
        Promise.resolve(go($("#" + t.id))).catch(() => {}).then(refreshRows);
      });
      refreshRows();
    } else {
      refresh();
      show(opening);
    }
    if (first) first.focus({ preventScroll: true });
    return {
      root,
      pane: (id) => $("#" + id),
      open: (name) => show(tabs.find((t) => t.name === name || t.id === name)),
      refresh,
      // More facts (studio, release date, the file) shown beside the whole story in Overview
      setStoryExtra: (html) => { const box = $("#tpOvDetails"); if (box) box.innerHTML = html || ""; },
    };
  };

  // How light a picture is, 0 to 1, from a tiny copy (only the parts you can see). Null when the browser won't say.
  B.tpLight = (img) => {
    try {
      const c = document.createElement("canvas"); c.width = 32; c.height = 32;
      const x = c.getContext("2d", { willReadFrequently: true }); x.drawImage(img, 0, 0, 32, 32);
      const d = x.getImageData(0, 0, 32, 32).data;
      let sum = 0, w = 0;
      for (let i = 0; i < d.length; i += 4) { const a = d[i + 3] / 255; if (a < 0.25) continue; sum += (0.2126 * d[i] + 0.7152 * d[i + 1] + 0.0722 * d[i + 2]) / 255 * a; w += a; }
      return w ? sum / w : null;
    } catch (e) { return null; }
  };
  // "Director <b>Denis Villeneuve</b>" style pieces for the slim strip
  // [label, value] puts the value first ("8.5 IMDb"); [label, value, true] puts the label first ("Director Denis Villeneuve")
  B.tpStrip = (parts) => parts.filter((p) => p && (p[0] || p[1])).map(([label, value, labelFirst]) => {
    const v = value != null && value !== "" ? `<b>${esc(value)}</b>` : "";
    const s = !labelFirst && v && B.scoreKey ? B.scoreKey(label) : "";
    return `<span class="${labelFirst ? "lf" : ""}"${s ? ` data-s="${s}"` : ""}>${labelFirst ? `${esc(label || "")} ${v}` : `${v}${esc(label || "")}`}</span>`;
  }).join("");
  // The More panel: one list, admin-only lines marked "You only"
  B.tpMore = (title, items, onPick) => {
    const list = items.filter(Boolean);
    const p = B.side(`<h3>More</h3><p class="dim">${esc(title)}</p>` +
      list.map((x, n) => (x.href
        ? `<a class="opt" href="${x.href}" ${x.blank ? `target="_blank" rel="noopener"` : ""}>${I(x.icon || "more")} ${esc(x.label)}${x.youOnly ? `<small>You only</small>` : ""}</a>`
        : `<button class="opt" data-n="${n}">${I(x.icon || "more")} ${esc(x.label)}${x.youOnly ? `<small>You only</small>` : ""}</button>`)).join(""));
    p.onclick = (e) => {
      const b = e.target.closest("[data-n]"); if (!b) return;
      B.closeAll();
      const x = list[+b.dataset.n];
      if (x.onClick) x.onClick();
      if (onPick) onPick(x);
    };
    return p;
  };
})();
