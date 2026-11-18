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

  // opts: { title, original, color, picture, cover: { url, ratio }, info: [..], rating, infoExtra,
  //         buttons: [{ id, label, icon, href, off, note, onClick }], status,
  //         story, strip, overviewExtra, tabs: [{ name, id, html, load(pane), keep }], open }
  B.titlePage = (opts) => {
    // The "where am I" line names this page by its title
    const pageName = opts.title || "";
    B.placeName = pageName;
    const buttons = (opts.buttons || []).filter(Boolean).slice(0, MAX_BUTTONS);
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
      const attrs = `${b.id ? `id="${b.id}"` : ""} data-tpb="${i}" ${b.off ? `aria-disabled="true"` : ""}`;
      const inner = `${I(b.icon || "play")} <span>${esc(b.label)}</span>`;
      return b.href && !b.off ? `<a class="${cls}" ${attrs} href="${b.href}">${inner}</a>` : `<button class="${cls}" ${attrs}>${inner}</button>`;
    };
    const meta = [opts.rating ? `<span class="rate">${esc(opts.rating)}</span>` : "", ...(opts.info || []).filter(Boolean).map((x) => `<span>${esc(x)}</span>`)].filter(Boolean).join("");
    const pic = opts.cover
      ? `<div class="tp-cover ${opts.cover.ratio >= 1 ? "sq" : ""}" style="${opts.cover.url ? `background-image:url('${opts.cover.url}')` : ""}"></div>`
      : "";
    // The whole story on the left, Details (studio, released, the file for you) on the right; one under the other on phones
    const overview = `${rows ? `<h3 class="tp-h">${esc(about.name)}</h3>` : ""}<div class="tp-ov"><div class="tp-ovl">${rows ? (opts.aboutStory ? `<div class="ov tp-about">${esc(opts.aboutStory)}</div>` : "") : `<div class="ov">${esc(opts.story || "")}</div>`}
      <div class="scores tp-strip">${opts.strip || ""}</div>${opts.overviewExtra || ""}</div><div class="tp-ovr" id="tpOvDetails"></div></div>`;
    B.page().innerHTML = B.top("") + `<div class="details tp ${rows ? "tp-rows" : ""}" style="--door:${opts.color || "var(--ink)"}">
      <div class="tp-hero ${opts.cover ? "covered" : ""}" style="${opts.picture ? `background-image:url('${opts.picture}')` : ""}">${pic}
        <div class="main">
          <div class="tp-name"><h1 class="t">${esc(opts.title || "")}</h1>${opts.original && opts.original !== opts.title ? `<div class="t2">${esc(opts.original)}</div>` : ""}<i class="tp-line"></i></div>
          <div class="meta">${meta}${rows && opts.scores ? `<span class="tp-scores">${opts.scores}</span>` : ""}${opts.infoExtra || ""}</div>
          ${rows && opts.story ? `<div class="tp-story">${esc(opts.story)}</div>` : ""}
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
    // Buttons: greyed-out ones say why; the rest run their action
    const why = $("#tpWhy");
    const say = (b) => { why.textContent = b ? b.off || b.note || opts.status || "" : opts.status || ""; why.classList.toggle("off", !!(b && b.off)); };
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
      const refreshRows = () => tabs.forEach((t) => { const pane = $("#" + t.id); if (pane) pane.hidden = !t.keep && empty(pane); });
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

  // "Director <b>Denis Villeneuve</b>" style pieces for the slim strip
  // [label, value] puts the value first ("8.5 IMDb"); [label, value, true] puts the label first ("Director Denis Villeneuve")
  B.tpStrip = (parts) => parts.filter((p) => p && (p[0] || p[1])).map(([label, value, labelFirst]) => {
    const v = value != null && value !== "" ? `<b>${esc(value)}</b>` : "";
    return `<span class="${labelFirst ? "lf" : ""}">${labelFirst ? `${esc(label || "")} ${v}` : `${v}${esc(label || "")}`}</span>`;
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
