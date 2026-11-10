/* Benflix web: the shared title page (Redesign 2, section 1, approved 2026-11-10). The same frame as the
   TV and phone app: a still picture (no trailer behind it), the title with a short line in the door's
   colour, the info line, up to 5 buttons (greyed-out ones are faded, can still be reached, and say why),
   then tabs. A tab opens on click and only loads the first time it's opened; empty tabs are hidden.
   Overview = 2 lines of story + More (the whole story in a side panel) + one slim strip.
   On phone-size windows the picture sits across the top and a slim Play bar shows once Play scrolls away.

   Everything lives on B.titlePage so nothing clashes with other files (they share one scope). */
"use strict";

(() => {
  const MAX_BUTTONS = 5;

  // opts: { title, original, color, picture, cover: { url, ratio }, info: [..], rating, infoExtra,
  //         buttons: [{ id, label, icon, href, off, note, onClick }], status,
  //         story, strip, overviewExtra, tabs: [{ name, id, html, load(pane), keep }], open }
  B.titlePage = (opts) => {
    const buttons = (opts.buttons || []).slice(0, MAX_BUTTONS);
    const tabs = [{ name: "Overview", id: "tpOverview", keep: true }, ...(opts.tabs || [])].filter((t) => !t.skip);
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
    const overview = `<div class="ov">${esc(opts.story || "")}</div>${opts.story ? `<button class="tp-more" id="tpStory">More</button>` : ""}
      <div class="scores tp-strip">${opts.strip || ""}</div>${opts.overviewExtra || ""}`;
    B.page().innerHTML = B.top("") + `<div class="details tp" style="--door:${opts.color || "var(--ink)"}">
      <div class="tp-hero ${opts.cover ? "covered" : ""}" style="${opts.picture ? `background-image:url('${opts.picture}')` : ""}">${pic}
        <div class="main">
          <div class="tp-name"><h1 class="t">${esc(opts.title || "")}</h1>${opts.original && opts.original !== opts.title ? `<div class="t2">${esc(opts.original)}</div>` : ""}<i class="tp-line"></i></div>
          <div class="meta">${meta}${opts.infoExtra || ""}</div>
          <div class="btns">${buttons.map(btn).join("")}</div>
          <div class="tp-why" id="tpWhy">${esc(opts.status || "")}</div>
          <div class="tp-tabs" role="tablist">${tabs.map((t) => `<button role="tab" class="tp-tab" data-tab="${t.id}">${esc(t.name)}</button>`).join("")}</div>
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
    // The whole story in the side panel
    const st = $("#tpStory");
    let storyExtra = "";
    if (st) st.onclick = () => B.side(`<h3>The story</h3><p class="dim">${esc(opts.title || "")}</p><p style="line-height:1.6">${esc(opts.story || "")}</p>${storyExtra}`);
    // Phones: the slim Play bar once Play has scrolled away
    const first = $(".tp .btns [data-tpb='0']"), bar = $("#tpBar");
    if (first && bar && "IntersectionObserver" in window) {
      const io = new IntersectionObserver(([en]) => { bar.hidden = en.isIntersecting || window.innerWidth > 700; });
      io.observe(first);
      B.onLeave(() => io.disconnect());
    }
    const opening = tabs.find((t) => t.name === opts.open) || tabs[0];
    refresh();
    show(opening);
    if (first) first.focus({ preventScroll: true });
    return {
      root,
      pane: (id) => $("#" + id),
      open: (name) => show(tabs.find((t) => t.name === name || t.id === name)),
      refresh,
      // More facts (studio, release date, the file) shown under the whole story
      setStoryExtra: (html) => { storyExtra = html || ""; },
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
