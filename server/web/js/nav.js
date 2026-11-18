/* Glowfin web: the menu (Glowfin base look, Ben 2026-11-18: two-tone icons, glow on the one you're on, Anime and
   Settings spots, phone bar Home / Movies / Shows / Anime / More). Built on the navigation redesign (2026-11-17).
   - Computers: an icon rail down the left of every page that opens wider with names when you point at it (or Tab
     into it). Clicking a door slides out that door's panel: a picture strip at the top, the door's places, and
     Jump back in. Requests (teal number) and your picture (white dot when there's news) sit at the bottom.
     Touch screens that are wide enough keep the rail open all the time.
   - Phone-sized windows: a bottom bar (Home, Movies, Shows, Music, More). Tap the door you're in again for its places.
   - Kids: a bigger rail with only Movies, Shows and Games, and no Requests.
   - When Glowfin opens, the rail's icons light up one after another in their door colours (in place of the doors
     growing on Home), with the same door notes.
   Everything sits in one closure: the web scripts share one scope, so nothing here is a top-level name. */

(() => {
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  // The doors' own few extra icons (Collections, Audiobooks, Guide) come from js/doors.js
  const I = (n) => (B.doorIcon ? B.doorIcon(n) : B.I(n));
  const narrow = () => matchMedia("(max-width: 760px)").matches;
  // Pages that fill the whole window (the player, a game, a book, signing in) have no menu
  const FULL = /^#\/(who|signin|play|playgame|read|lobby\/)/;
  // Glowfin menu order (ui-2): Movies, Shows, (Anime), Live TV, Music, Games, Books
  const ORDER = ["movies", "shows", "live", "music", "games", "books"];
  const TEAL = "#3DD6C6";
  // Glowfin (ui-7): Anime has its own page with its own tabs
  const ANIME = "#/anime";
  // Glowfin's two-tone menu icons (ui-2, the same shapes as the TV and phone app): a soft see-through fill
  // under each body shape, then an outline
  const GI = {
    search: [["M10.8 4.2a6.6 6.6 0 1 0 0 13.2a6.6 6.6 0 1 0 0-13.2z"], ["M15.7 15.7l4.6 4.6"]],
    home: [["M4 10.6 12 4l8 6.6V19a1 1 0 0 1-1 1h-4.6v-5.4H9.6V20H5a1 1 0 0 1-1-1z"], []],
    movies: [["M4 10.2h16v8.3a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z", "M3.7 6.9 18.6 3.3l.9 3.5-14.9 3.6z"], ["M8.3 5.8l1.8 2.9", "M13 4.7l1.8 2.9"]],
    shows: [["M5 7.6h14a2 2 0 0 1 2 2v7.6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9.6a2 2 0 0 1 2-2z"], ["M8.6 3.6 12 7l3.4-3.4"]],
    anime: [["M8.10 7.80a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z", "M12.47 10.98a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z", "M10.80 16.12a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z", "M5.40 16.12a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z", "M3.73 10.98a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z"], ["M12 10.6a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0-3.6z"]],
    live: [["M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z"], ["M8 8a5.7 5.7 0 0 0 0 8", "M16 8a5.7 5.7 0 0 1 0 8", "M5.2 5.2a9.6 9.6 0 0 0 0 13.6", "M18.8 5.2a9.6 9.6 0 0 1 0 13.6"]],
    music: [["M6.6 14.8a2.7 2.7 0 1 0 0 5.4a2.7 2.7 0 1 0 0-5.4z", "M16.6 12.8a2.7 2.7 0 1 0 0 5.4a2.7 2.7 0 1 0 0-5.4z"], ["M9.3 17.5V6.4l10-2.2v11.3"]],
    games: [["M7.6 7.4h8.8a4.5 4.5 0 0 1 4.4 3.6l.9 4.7a2.4 2.4 0 0 1-4.1 2.1L15.6 16H8.4l-2 1.8a2.4 2.4 0 0 1-4.1-2.1l.9-4.7a4.5 4.5 0 0 1 4.4-3.6z"], ["M8 10.4v3.4", "M6.3 12.1h3.4", "M15.6 11.1h.01", "M17.6 13.1h.01"]],
    books: [["M3.4 5.4h5.6a3 3 0 0 1 3 3V20a2.2 2.2 0 0 0-2.2-2H3.4z", "M20.6 5.4H15a3 3 0 0 0-3 3V20a2.2 2.2 0 0 1 2.2-2h6.4z"], []],
    requests: [["M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z"], ["M12 8.3v7.4", "M8.3 12h7.4"]],
    settings: [["M12.74 2.83L14.83 3.25L15.19 5.77L16.54 6.67L19.00 6.04L20.19 7.81L18.66 9.85L18.98 11.44L21.17 12.74L20.75 14.83L18.23 15.19L17.33 16.54L17.96 19.00L16.19 20.19L14.15 18.66L12.56 18.98L11.26 21.17L9.17 20.75L8.81 18.23L7.46 17.33L5.00 17.96L3.81 16.19L5.34 14.15L5.02 12.56L2.83 11.26L3.25 9.17L5.77 8.81L6.67 7.46L6.04 5.00L7.81 3.81L9.85 5.34L11.44 5.02Z"], ["M12 9.3a2.7 2.7 0 1 0 0 5.4a2.7 2.7 0 1 0 0-5.4z"]],
    more: [[], ["M5 12h.01", "M12 12h.01", "M19 12h.01"]],
  };
  const SHIFT = { search: [-0.25, -0.25], movies: [0.15, 0.35], shows: [0, 0.6], music: [0.4, -0.2], games: [0, -0.97], books: [0, -0.7] };
  const G = (n) => {
    const g = GI[n];
    if (!g) return I(n);
    // Moved so each drawing sits exactly in the middle of its box (looks centred in the round glow)
    const [dx, dy] = SHIFT[n] || [0, 0];
    return `<svg class="i gi" viewBox="0 0 24 24" aria-hidden="true"><g transform="translate(${dx} ${dy})">${g[0].map((d) => `<path d="${d}" class="gf"/>`).join("")}${g[0].concat(g[1]).map((d) => `<path d="${d}"${n === "more" ? ' class="gd"' : ""}/>`).join("")}</g></svg>`;
  };
  B.glowIcon = G;

  const doors = () => {
    const D = B.DOORS;
    if (B.me && B.me.kids) return ["movies", "shows", "games"].filter((k) => D[k]).map((k) => D[k]);
    return ORDER.filter((k) => D[k]).map((k) => D[k]);
  };
  // Which door the page you're on belongs to (title pages count too, so the right icon stays lit)
  const doorOf = (hash) => {
    if (/^#\/(movies$|grid\/movies|browse\/movies|movie\/)/.test(hash)) return "movies";
    if (/^#\/(shows$|grid\/shows|browse\/shows|show\/|season\/)/.test(hash)) return "shows";
    if (/^#\/live/.test(hash)) return "live";
    if (/^#\/(games$|console\/|game\/|bgame\/)/.test(hash)) return "games";
    if (/^#\/(music$|msearch|mlibrary|mthemes\/|songs$|liked$|mgrid\/|album\/|artist\/|playlist\/|mix\/|song\/|queue)/.test(hash)) return "music";
    if (/^#\/(books(\?|$)|book\/)/.test(hash)) return "books";
    return null;
  };
  const spotOf = (hash) => {
    if (/^#\/home/.test(hash)) return "home";
    if (/^#\/(anime$|browse\/anime)/.test(hash) || /^#\/grid\/(shows|movies)\?(.*&)?anime=only/.test(hash)) return "anime";
    // Ben 2026-11-18: your page and everything it opens light up your picture
    if (/^#\/(me|settings|server|help|mine|lobby)(\/|\?|$)/.test(hash)) return "me";
    if (/^#\/search/.test(hash)) return "search";
    if (/^#\/requests/.test(hash)) return "requests";
    return doorOf(hash) || "";
  };

  // ---------- the rail ----------
  let rail, panel, bar, openDoor = null, reqCount = 0;
  const railHtml = () => {
    const kids = !!(B.me && B.me.kids);
    const spot = spotOf(location.hash);
    const item = (key, icon, label, href, extra) => `<a class="rl-i ${spot === key ? "on" : ""}" data-k="${key}" href="${href || "#/" + key}" title="${esc(label)}">${G(icon)}<span class="rl-t">${esc(label)}</span>${extra || ""}</a>`;
    // Glowfin menu (ui-2): Search, Home, Movies, Shows, Anime, Live TV, Music, Games, Books, Requests, then your
    // picture at the bottom (Ben 2026-11-18: it opens the Me page, where Settings lives, so no second Settings
    // spot). Kids (K1, ui-17): Home, Movies, Shows and Games, then the picture.
    return `<a class="rl-logo" href="#/home" title="Home">${B.logo()}</a>
      ${kids ? "" : item("search", "search", "Search")}
      ${item("home", "home", "Home")}
      ${doors().map((d) => `<button type="button" class="rl-i rl-door ${spot === d.key ? "on" : ""} ${openDoor === d.key ? "open" : ""}" data-door="${d.key}" title="${esc(d.name)}">${G(d.key)}<span class="rl-t">${esc(d.name)}</span></button>${d.key === "shows" && !kids ? item("anime", "anime", "Anime", ANIME) : ""}`).join("")}
      ${kids ? "" : item("requests", "requests", "Requests", "", `<b class="rl-count" ${reqCount ? "" : "hidden"}>${reqCount}</b>`)}
      <div class="rl-gap"></div>
      <button type="button" class="rl-i rl-me ${spot === "me" ? "on" : ""}" data-k="me" title="${esc(B.me ? B.me.name : "")}">${B.me ? B.picHtml(B.me, "", 100) : ""}<span class="rl-t">${esc(B.me ? B.me.name : "")}</span></button>`;
  };
  const paint = () => {
    const hash = location.hash || "";
    const show = !!B.me && !FULL.test(hash);
    document.body.classList.toggle("nav-on", show);
    document.body.classList.toggle("nav-kids", show && !!B.me.kids);
    document.documentElement.classList.toggle("nav-phone", show && narrow());
    if (!show) { closePanel(); if (rail) rail.hidden = true; if (bar) bar.hidden = true; return; }
    if (!rail) {
      rail = document.createElement("nav");
      rail.id = "brail"; rail.className = "brail"; rail.setAttribute("aria-label", "Glowfin menu");
      document.body.appendChild(rail);
      rail.addEventListener("click", onRail);
      rail.addEventListener("keydown", (e) => {
        // Arrow keys move up and down the rail; Right opens a door's panel
        const items = $$(".rl-i, .rl-logo", rail);
        const at = items.indexOf(document.activeElement);
        if (at < 0) return;
        if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); const n = items[at + (e.key === "ArrowDown" ? 1 : -1)]; if (n) n.focus(); }
        // Ben 2026-11-18: no slide-out menus for pages any more, so Right goes into the page you're on
        if (e.key === "ArrowRight" && document.activeElement.dataset.door) { e.preventDefault(); landOnPage(true); }
      });
    }
    rail.hidden = false;
    rail.classList.toggle("kids", !!B.me.kids);
    rail.classList.toggle("always", alwaysOpen());
    const html = railHtml();
    if (rail.dataset.html !== html) {
      const had = document.activeElement && rail.contains(document.activeElement) ? (document.activeElement.dataset.door || document.activeElement.dataset.k) : null;
      rail.innerHTML = html; rail.dataset.html = html;
      if (had) { const again = $(`[data-door="${had}"], [data-k="${had}"]`, rail); if (again) again.focus({ preventScroll: true }); }
      if (lighting) $$(".rl-i", rail).forEach((x) => x.classList.add("dark"));
    }
    paintBar();
  };
  // Wide touch screens (a tablet on its side) keep the rail open with names: there's nothing to point with
  const alwaysOpen = () => matchMedia("(hover: none) and (min-width: 1100px)").matches;

  // Ben 2026-11-18: picking a page from the menu tucks the menu away for good and lands on the first card of
  // the page's first row (it used to stay open, or open itself again, while the new page loaded)
  let landing = 0;
  const landOnPage = (byKeys) => {
    if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
    if (!byKeys) return;
    const n = ++landing;
    const until = Date.now() + 4000;
    const tryIt = () => {
      if (n !== landing) return;
      const page = $("#page");
      const first = page && ($(".rowx .strip > a, .rowx .strip > button, .rowx .strip .poster", page) || $(".poster, .card", page));
      if (first && first.offsetParent !== null) { first.focus({ preventScroll: true }); return; }
      if (Date.now() < until) setTimeout(tryIt, 120);
    };
    setTimeout(tryIt, 60);
  };
  // Picking a spot with the mouse: the pointer is still over the menu, so keep it tucked until the pointer leaves
  const tuck = () => {
    if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
    if (!rail || rail.classList.contains("always")) return;
    rail.classList.add("tucked");
    rail.addEventListener("mouseleave", () => rail && rail.classList.remove("tucked"), { once: true });
  };
  const onRail = (e) => {
    const d = e.target.closest("[data-door]");
    // Glowfin (ui-7) + Ben 2026-11-18: every page has its places as tabs along its top, so it opens straight away
    // (Live TV, Music, Games and Books no longer slide out a second menu)
    if (d) { e.preventDefault(); closePanel(); B.go("#/" + d.dataset.door); landOnPage(e.detail === 0); return; }
    const k = e.target.closest("[data-k]");
    if (!k) return;
    if (k.dataset.k === "me") {
      // Ben 2026-11-18: your picture tucks the menu away and lands on the Me page's first tile (My watching),
      // with the mouse too. The menu stays tucked until the pointer leaves it.
      e.preventDefault();
      closePanel();
      tuck();
      B.openMenu(true);
      return;
    }
    closePanel();
    landOnPage(e.detail === 0 && k.dataset.k !== "search");
  };

  // ---------- the slide-out panel (only your picture's menu now: pages have tabs along their top) ----------
  const closePanel = () => {
    if (!panel) return;
    const was = openDoor;
    panel.remove(); panel = null; openDoor = null;
    document.body.classList.remove("nav-panel");
    if (rail) $$(".rl-door, .rl-me", rail).forEach((x) => x.classList.remove("open"));
    return was;
  };
  // The rail button a panel belongs to (a door, or your picture)
  const railBtn = (key) => rail && (key === "me" ? $('[data-k="me"]', rail) : $(`[data-door="${key}"]`, rail));
  // ---------- phones: the bottom bar ----------
  // Glowfin (Ben, 2026-11-18): Home, Movies, Shows, Anime and More (Kids: Games in Anime's place)
  const tabs = () => {
    const kids = !!(B.me && B.me.kids);
    const ds = doors().filter((d) => ["movies", "shows", "music", "games"].includes(d.key)).slice(0, kids ? 3 : 2);
    return [{ key: "home", label: "Home", icon: "home" }]
      .concat(ds.map((d) => ({ key: d.key, label: d.name, icon: d.key, door: true })))
      .concat(kids ? [] : [{ key: "anime", label: "Anime", icon: "anime" }])
      .concat([{ key: "more", label: "More", icon: "more" }]);
  };
  const paintBar = () => {
    if (!narrow()) { if (bar) bar.hidden = true; return; }
    if (!bar) {
      bar = document.createElement("nav");
      bar.id = "bbar"; bar.className = "bbar"; bar.setAttribute("aria-label", "Glowfin menu");
      document.body.appendChild(bar);
      bar.addEventListener("click", onTab);
    }
    bar.hidden = false;
    const spot = spotOf(location.hash);
    const list = tabs();
    const on = list.some((t) => t.key === spot) ? spot : "more";
    const html = list.map((t) => `<button type="button" class="bt ${t.key === on ? "on" : ""}" data-tab="${t.key}"><i>${G(t.icon)}</i><span>${esc(t.label)}</span></button>`).join("");
    if (bar.dataset.html !== html) { bar.innerHTML = html; bar.dataset.html = html; }
  };
  const onTab = (e) => {
    const b = e.target.closest("[data-tab]");
    if (!b) return;
    const k = b.dataset.tab;
    if (k === "home") return B.go("#/home");
    if (k === "more") return moreSheet();
    if (k === "anime") return B.go(ANIME);
    B.go("#/" + k);
  };
  const sheetRow = (href, icon, label, color, note) => `<a class="sh-row" href="${esc(href)}" style="--c:${color || "var(--ink2)"}">${G(icon)}<span>${esc(label)}</span>${note ? `<small>${esc(note)}</small>` : ""}</a>`;
  const moreSheet = () => {
    const kids = !!(B.me && B.me.kids);
    const shown = tabs().map((t) => t.key);
    const rest = doors().filter((d) => !shown.includes(d.key));
    const p = B.side(`<div class="sheet">
      ${kids ? "" : sheetRow("#/search", "search", "Search")}
      ${rest.map((d) => sheetRow("#/" + d.key, d.key, d.name)).join("")}
      ${kids ? "" : sheetRow("#/requests", "requests", "Requests", "", reqCount ? String(reqCount) : "")}
      <div class="sh-sep"></div>
      <button type="button" class="sh-row sh-me" data-me>${B.me ? B.picHtml(B.me, "", 100) : ""}<span>${esc(B.me ? B.me.name : "")}</span>${I("right")}</button>
    </div>`);
    p.classList.add("navsheet");
    p.addEventListener("click", (e) => {
      if (e.target.closest("[data-me]")) { B.closeAll(); B.openMenu(); return; }
      if (e.target.closest("a")) B.closeAll();
    });
  };

  // ---------- Requests: the teal number (requests that are ready for you) ----------
  B.setRailCount = (n) => {
    n = Math.max(0, n | 0);
    if (n === reqCount) return;
    reqCount = n;
    paint();
  };

  // ---------- start-up: the icons light up one after another (replaces the doors growing on Home) ----------
  let lighting = false;
  // Glowfin: menus stay still (no start-up light-up), so nothing runs while the page loads
  B.railLightUp = () => {};

  // ---------- keys: Esc closes the panel; M opens the menu (like ☰ on a remote) ----------
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && panel) {
      e.preventDefault(); e.stopPropagation();
      const door = closePanel();
      const back = railBtn(door);
      if (back) back.focus();
    }
  }, true);
  document.addEventListener("click", (e) => {
    if (!panel) return;
    if (panel.contains(e.target) || (rail && rail.contains(e.target))) return;
    closePanel();
  });
  // The rail tucks back in when the pointer or keyboard leaves it (the panel closes with it)
  document.addEventListener("focusin", (e) => {
    if (!panel) return;
    if (panel.contains(e.target) || (rail && rail.contains(e.target))) return;
    closePanel();
  });

  window.addEventListener("hashchange", () => { closePanel(); setTimeout(paint, 0); });
  addEventListener("resize", () => paint());
  const start = () => {
    paint();
    // Pages redraw by replacing #page; signing in and switching profile change who's watching
    const page = $("#page");
    if (page) new MutationObserver(() => paint()).observe(page, { childList: true });
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start); else start();
})();
