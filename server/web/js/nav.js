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
  const FULL = /^#\/(who|signin|play|playgame|game\/|read|lobby\/)/;
  // Glowfin menu order (ui-2): Movies, Shows, (Anime), Live TV, Music, Games, Books
  const ORDER = ["movies", "shows", "live", "music", "games", "books"];
  const TEAL = "#3DD6C6";
  const ANIME = "#/grid/shows?anime=only&title=Anime";
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
  const G = (n) => {
    const g = GI[n];
    if (!g) return I(n);
    return `<svg class="i gi" viewBox="0 0 24 24" aria-hidden="true">${g[0].map((d) => `<path d="${d}" class="gf"/>`).join("")}${g[0].concat(g[1]).map((d) => `<path d="${d}"${n === "more" ? ' class="gd"' : ""}/>`).join("")}</svg>`;
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
    if (/^#\/grid\/shows\?(.*&)?anime=only/.test(hash)) return "anime";
    if (/^#\/settings/.test(hash)) return "settings";
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
    // picture and Settings at the bottom
    return `<a class="rl-logo" href="#/home" title="Home">${B.logo()}</a>
      ${item("search", "search", "Search")}
      ${item("home", "home", "Home")}
      ${doors().map((d) => `<button type="button" class="rl-i rl-door ${spot === d.key ? "on" : ""} ${openDoor === d.key ? "open" : ""}" data-door="${d.key}" title="${esc(d.name)}" aria-haspopup="true">${G(d.key)}<span class="rl-t">${esc(d.name)}</span></button>${d.key === "shows" && !kids ? item("anime", "anime", "Anime", ANIME) : ""}`).join("")}
      ${kids ? "" : item("requests", "requests", "Requests", "", `<b class="rl-count" ${reqCount ? "" : "hidden"}>${reqCount}</b>`)}
      <div class="rl-gap"></div>
      <button type="button" class="rl-i rl-me" data-k="me" title="${esc(B.me ? B.me.name : "")}">${B.me ? B.picHtml(B.me, "", 100) : ""}${B.newsDot ? '<i class="rl-dot"></i>' : ""}<span class="rl-t">${esc(B.me ? B.me.name : "")}</span></button>
      ${item("settings", "settings", "Settings")}`;
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
        if (e.key === "ArrowRight" && document.activeElement.dataset.door) { e.preventDefault(); openPanel(document.activeElement.dataset.door, true); }
        if (e.key === "ArrowRight" && document.activeElement.dataset.k === "me" && openDoor !== "me") { e.preventDefault(); B.openMenu(true); }
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

  const onRail = (e) => {
    const d = e.target.closest("[data-door]");
    if (d) { e.preventDefault(); openPanel(d.dataset.door, e.detail === 0); return; }
    const k = e.target.closest("[data-k]");
    if (!k) return;
    if (k.dataset.k === "me") {
      e.preventDefault();
      if (openDoor === "me") { closePanel(); return; }
      B.openMenu(e.detail === 0);
      return;
    }
    closePanel();
  };

  // ---------- a door's panel ----------
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
  const placesOf = (door) => {
    const list = (B.doorPlaces ? B.doorPlaces(door) : []).filter(Boolean);
    return list.length ? list : [["Home", "home", "#/" + door]];
  };
  const openPanel = (door, byKeys) => {
    if (openDoor === door) { closePanel(); return; }
    closePanel();
    const d = B.DOORS[door];
    if (!d) return;
    if (narrow()) return placesSheet(door);
    openDoor = door;
    $$(".rl-door", rail).forEach((x) => x.classList.toggle("open", x.dataset.door === door));
    panel = document.createElement("div");
    panel.className = "npanel"; panel.style.setProperty("--c", d.color);
    panel.setAttribute("role", "dialog"); panel.setAttribute("aria-label", d.name);
    const places = placesOf(door);
    panel.innerHTML = `<div class="np-title"><b>${esc(d.name)}</b></div>
      <div class="np-list">${places.map(([label, icon, link, , count], i) => `<a href="${link.startsWith("jump:") ? "#/" + door : esc(link)}" ${link.startsWith("jump:") ? `data-jump="${esc(link.slice(5))}"` : ""}>${I(icon)}<span>${esc(i === 0 ? d.name + " home" : label)}</span>${count ? `<small>${esc(count)}</small>` : ""}</a>`).join("")}</div>
      <div class="np-back" id="npBack"></div>`;
    document.body.appendChild(panel);
    document.body.classList.add("nav-panel");
    panel.addEventListener("click", (e) => {
      const a = e.target.closest("a");
      if (!a) return;
      const jump = a.dataset.jump;
      closePanel();
      if (rail) rail.blur && document.activeElement && document.activeElement.blur();
      if (jump) { e.preventDefault(); B.doorJump ? B.doorJump(door, jump) : B.go("#/" + door); }
    });
    panel.addEventListener("keydown", (e) => {
      // Jump back in is a row: Left and Right move along it first, and Left only goes back to the rail from its first poster
      const list = $$(".np-list a", panel);
      const posters = $$(".np-posters a", panel);
      const p = posters.indexOf(document.activeElement);
      if (p >= 0) {
        if (e.key === "ArrowRight") { e.preventDefault(); if (posters[p + 1]) posters[p + 1].focus(); return; }
        if (e.key === "ArrowLeft" && p > 0) { e.preventDefault(); posters[p - 1].focus(); return; }
        if (e.key === "ArrowUp") { e.preventDefault(); if (list.length) list[list.length - 1].focus(); return; }
        if (e.key === "ArrowDown") { e.preventDefault(); return; }
      }
      const at = list.indexOf(document.activeElement);
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        const n = e.key === "ArrowDown" ? (list[at + 1] || (at >= 0 && posters[0])) : list[at - 1];
        if (n) n.focus();
      }
      if (e.key === "ArrowLeft") { e.preventDefault(); const back = railBtn(door); closePanel(); if (back) back.focus(); }
    });
    if (byKeys) { const first = $("a", panel); if (first) first.focus(); }
  };
  // ---------- your picture's panel: the profile menu, sliding out beside the rail like a door's panel ----------
  // Returns false when there's no rail to slide out from (phone-sized windows, full-window pages): the corner card shows instead
  B.openMePanel = (byKeys) => {
    if (!B.me || !rail || rail.hidden || narrow()) return false;
    closePanel();
    B.closeAll();
    openDoor = "me";
    const me = railBtn("me");
    if (me) me.classList.add("open");
    panel = document.createElement("div");
    panel.className = "npanel np-me";
    panel.setAttribute("role", "dialog"); panel.setAttribute("aria-label", B.me.name);
    panel.innerHTML = `<div class="np-who">${B.picHtml(B.me, "", 220)}<b>${esc(B.me.name)}</b><small id="npWt">&nbsp;</small>
        <button type="button" class="btn small" data-go="#/who">${I("swap")} Switch profile</button></div>
      <div class="np-list">${B.profileItems().map(([where, icon, label]) => `<button type="button" data-go="${esc(where)}">${I(icon)}<span>${esc(label)}</span></button>`).join("")}</div>
      <div class="np-list np-end"><button type="button" class="np-out" data-go="signout">${I("out")}<span>Sign out</span></button></div>`;
    document.body.appendChild(panel);
    document.body.classList.add("nav-panel");
    panel.addEventListener("click", (e) => {
      const b = e.target.closest("[data-go]");
      if (!b) return;
      closePanel();
      B.profileGo(b.dataset.go);
    });
    panel.addEventListener("keydown", (e) => {
      const items = $$("button", panel);
      const at = items.indexOf(document.activeElement);
      if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); const n = items[at + (e.key === "ArrowDown" ? 1 : -1)]; if (n) n.focus(); }
      if (e.key === "ArrowLeft") { e.preventDefault(); const back = railBtn("me"); closePanel(); if (back) back.focus(); }
    });
    if (byKeys) { const first = $("button", panel); if (first) first.focus(); }
    if (B.watchTime) B.watchTime().then((w) => { const el = $("#npWt"); if (el && openDoor === "me") el.textContent = `${B.hm(w.month)} this month`; }).catch(() => {});
    return true;
  };
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
    const html = list.map((t) => `<button type="button" class="bt ${t.key === on ? "on" : ""}" data-tab="${t.key}"><i>${G(t.icon)}${t.key === "more" && B.newsDot ? '<b class="rl-dot"></b>' : ""}</i><span>${esc(t.label)}</span></button>`).join("");
    if (bar.dataset.html !== html) { bar.innerHTML = html; bar.dataset.html = html; }
  };
  const onTab = (e) => {
    const b = e.target.closest("[data-tab]");
    if (!b) return;
    const k = b.dataset.tab;
    if (k === "home") return B.go("#/home");
    if (k === "more") return moreSheet();
    if (k === "anime") return B.go(ANIME);
    // Approved rx-phonepanel: tapping the door you're already in opens its places
    if (doorOf(location.hash || "") === k) return placesSheet(k);
    B.go("#/" + k);
  };
  const sheetRow = (href, icon, label, color, note) => `<a class="sh-row" href="${esc(href)}" style="--c:${color || "var(--ink2)"}">${G(icon)}<span>${esc(label)}</span>${note ? `<small>${esc(note)}</small>` : ""}</a>`;
  const placesSheet = (door) => {
    const d = B.DOORS[door];
    const places = placesOf(door);
    const p = B.side(`<h3 style="color:${d.color}">${esc(d.name)}</h3><div class="sheet">${places.map(([label, icon, link], i) => (link.startsWith("jump:")
      ? `<a class="sh-row" href="#/${door}" data-jump="${esc(link.slice(5))}">${I(icon)}<span>${esc(label)}</span></a>`
      : sheetRow(link, icon, i === 0 ? d.name + " home" : label))).join("")}</div>`);
    p.classList.add("navsheet");
    p.addEventListener("click", (e) => {
      const a = e.target.closest("a"); if (!a) return;
      B.closeAll();
      if (a.dataset.jump) { e.preventDefault(); B.doorJump ? B.doorJump(door, a.dataset.jump) : B.go("#/" + door); }
    });
  };
  const moreSheet = () => {
    const kids = !!(B.me && B.me.kids);
    const shown = tabs().map((t) => t.key);
    const rest = doors().filter((d) => !shown.includes(d.key));
    const p = B.side(`<div class="sheet">
      ${sheetRow("#/search", "search", "Search")}
      ${rest.map((d) => sheetRow("#/" + d.key, d.key, d.name)).join("")}
      ${kids ? "" : sheetRow("#/requests", "requests", "Requests", "", reqCount ? String(reqCount) : "")}
      ${sheetRow("#/settings", "settings", "Settings")}
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
