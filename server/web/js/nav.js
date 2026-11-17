/* Benflix web: the navigation redesign (approved 2026-11-17, same as the TV and phone app).
   - Computers: an icon rail down the left of every page that opens wider with names when you point at it (or Tab
     into it). Clicking a door slides out that door's panel: a picture strip at the top, the door's places, and
     Jump back in. Requests (teal number) and your picture (white dot when there's news) sit at the bottom.
     Touch screens that are wide enough keep the rail open all the time.
   - Phone-sized windows: a bottom bar (Home, Movies, Shows, Music, More). Tap the door you're in again for its places.
   - Kids: a bigger rail with only Movies, Shows and Games, and no Requests.
   - When Benflix opens, the rail's icons light up one after another in their door colours (in place of the doors
     growing on Home), with the same door notes.
   Everything sits in one closure: the web scripts share one scope, so nothing here is a top-level name. */

(() => {
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  // The doors' own few extra icons (Collections, Audiobooks, Guide) come from js/doors.js
  const I = (n) => (B.doorIcon ? B.doorIcon(n) : B.I(n));
  // A holiday can swap in its own icon set (js/holidaynav.js icons)
  const HI = (key, n) => (B.holidayIcon ? B.holidayIcon(key, I(n)) : I(n));
  const narrow = () => matchMedia("(max-width: 760px)").matches;
  // Pages that fill the whole window (the player, a game, a book, signing in) have no menu
  const FULL = /^#\/(who|signin|play|playgame|game\/|read|lobby\/)/;
  const ORDER = ["movies", "shows", "live", "games", "music", "books"];
  const TEAL = "#3DD6C6";

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
    if (/^#\/(games$|console\/|game\/)/.test(hash)) return "games";
    if (/^#\/(music$|msearch|mlibrary|mthemes\/|songs$|liked$|mgrid\/|album\/|artist\/|playlist\/|mix\/|song\/|queue)/.test(hash)) return "music";
    if (/^#\/(books(\?|$)|book\/)/.test(hash)) return "books";
    return null;
  };
  const spotOf = (hash) => {
    if (/^#\/home/.test(hash)) return "home";
    if (/^#\/search/.test(hash)) return "search";
    if (/^#\/requests/.test(hash)) return "requests";
    return doorOf(hash) || "";
  };

  // ---------- the rail ----------
  let rail, panel, bar, openDoor = null, reqCount = 0;
  const railHtml = () => {
    const kids = !!(B.me && B.me.kids);
    const spot = spotOf(location.hash);
    // Holiday marks (js/holidaynav.js): the decoration hangs beside the icon you're on, each door has its item
    const h = B.holidayNav ? B.holidayNav() : null;
    const mark = (here) => (h && h.mark && here ? `<i class="rl-mark">${B.holidaySvg(h.mark, "0 0 12 18")}</i>` : "");
    const item = (key, icon, label, color, extra) => `<a class="rl-i ${spot === key ? "on" : ""}" data-k="${key}" href="#/${key}" style="--c:${color || "var(--ink)"}" title="${esc(label)}">${HI(key, icon)}${mark(spot === key)}<span class="rl-t">${esc(label)}</span>${extra || ""}</a>`;
    return `<a class="rl-logo" href="#/home" title="Home">${B.logo()}</a>
      ${item("home", "home", "Home")}
      ${item("search", "search", "Search")}
      ${doors().map((d) => `<button type="button" class="rl-i rl-door ${spot === d.key ? "on" : ""} ${openDoor === d.key ? "open" : ""}" data-door="${d.key}" style="--c:${d.color}" title="${esc(d.name)}" aria-haspopup="true">${HI(d.key, d.icon)}${mark(spot === d.key)}<span class="rl-t">${esc(d.name)}</span></button>`).join("")}
      <div class="rl-gap"></div>
      ${kids ? "" : item("requests", "plus", "Requests", TEAL, `<b class="rl-count" ${reqCount ? "" : "hidden"}>${reqCount}</b>`)}
      <button type="button" class="rl-i rl-me" data-k="me" title="${esc(B.me ? B.me.name : "")}">${B.me ? B.picHtml(B.me, "", 100) : ""}${B.newsDot ? '<i class="rl-dot"></i>' : ""}<span class="rl-t">${esc(B.me ? B.me.name : "")}</span></button>`;
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
      rail.id = "brail"; rail.className = "brail"; rail.setAttribute("aria-label", "Benflix menu");
      document.body.appendChild(rail);
      rail.addEventListener("click", onRail);
      rail.addEventListener("keydown", (e) => {
        // Arrow keys move up and down the rail; Right opens a door's panel
        const items = $$(".rl-i, .rl-logo", rail);
        const at = items.indexOf(document.activeElement);
        if (at < 0) return;
        if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); const n = items[at + (e.key === "ArrowDown" ? 1 : -1)]; if (n) n.focus(); }
        if (e.key === "ArrowRight" && document.activeElement.dataset.door) { e.preventDefault(); openPanel(document.activeElement.dataset.door, true); }
      });
    }
    rail.hidden = false;
    rail.classList.toggle("kids", !!B.me.kids);
    rail.classList.toggle("always", alwaysOpen());
    // Holiday marks: the rail takes the holiday's colours
    const h = B.holidayNav ? B.holidayNav() : null;
    rail.classList.toggle("holiday", !!h);
    if (h) { rail.style.setProperty("--hr1", h.rail[0]); rail.style.setProperty("--hr2", h.rail[1]); }
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
    closePanel();
    if (k.dataset.k === "me") { e.preventDefault(); B.openMenu(); }
  };

  // ---------- a door's panel ----------
  const closePanel = () => {
    if (!panel) return;
    const was = openDoor;
    panel.remove(); panel = null; openDoor = null;
    document.body.classList.remove("nav-panel");
    if (rail) $$(".rl-door", rail).forEach((x) => x.classList.remove("open"));
    return was;
  };
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
    panel.innerHTML = `<div class="np-strip" id="npStrip"><b>${esc(d.name)}</b></div>
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
      const items = $$("a", panel);
      const at = items.indexOf(document.activeElement);
      if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); const n = items[at + (e.key === "ArrowDown" ? 1 : -1)]; if (n) n.focus(); }
      if (e.key === "ArrowLeft") { e.preventDefault(); const back = $(`[data-door="${door}"]`, rail); closePanel(); if (back) back.focus(); }
    });
    if (byKeys) { const first = $("a", panel); if (first) first.focus(); }
    fillPanel(door);
  };
  // The picture strip (one of your door's pictures, a new one each day) and Jump back in (up to 3)
  const artCache = {};
  const fillPanel = async (door) => {
    const u = B.uid();
    const day = new Date().toDateString();
    const types = { movies: "Movie", shows: "Series", music: "MusicArtist" }[door];
    const art = types ? (artCache[door] && artCache[door].day === day ? artCache[door].url : null) : null;
    const strip = () => $("#npStrip");
    const setArt = (url) => { const s = strip(); if (s && url && openDoor === door) { s.style.backgroundImage = `url('${url}')`; s.classList.add("art"); } };
    if (art) setArt(art);
    else if (types) {
      B.get(`/Items?userId=${u}&IncludeItemTypes=${types}&Recursive=true&ImageTypes=Backdrop&SortBy=SortName&Limit=1&StartIndex=0&EnableTotalRecordCount=true&EnableImages=false`)
        .then(async (r) => {
          const total = r.TotalRecordCount || 0;
          if (!total) return;
          const start = new Date();
          const n = Math.floor((start - new Date(start.getFullYear(), 0, 0)) / 864e5) % total;
          const pick = await B.get(`/Items?userId=${u}&IncludeItemTypes=${types}&Recursive=true&ImageTypes=Backdrop&SortBy=SortName&Limit=1&StartIndex=${n}`);
          const it = (pick.Items || [])[0];
          if (!it) return;
          const url = B.img(it.Id, "Backdrop", { w: 640, index: 0 });
          artCache[door] = { day, url };
          setArt(url);
        }).catch(() => {});
    }
    const get = (p) => B.get(p).then((r) => B.keep(r.Items || [])).catch(() => []);
    let items = [];
    if (door === "movies") items = await get(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Movie&Limit=3&Fields=${B.FIELDS}`);
    else if (door === "shows") items = (await get(`/UserItems/Resume?userId=${u}&IncludeItemTypes=Episode&Limit=6&Fields=${B.FIELDS}`)).filter((e) => !B.watchedEp(e)).slice(0, 3);
    else if (door === "music") items = await get(`/Items?userId=${u}&IncludeItemTypes=MusicAlbum&Recursive=true&SortBy=DatePlayed&SortOrder=Descending&Filters=IsPlayed&Limit=3`);
    const box = $("#npBack");
    if (!box || openDoor !== door || !items.length) return;
    box.innerHTML = `<h4>Jump back in</h4><div class="np-posters">${items.map((it) => {
      const sq = it.Type === "MusicAlbum";
      const img = it.Type === "Episode" ? B.img(it.SeriesId, "Primary", { h: 240 }) : B.poster(it, 240);
      const name = it.Type === "Episode" ? it.SeriesName : it.Name;
      return `<a href="${esc(B.hrefOf(it))}" class="${sq ? "sq" : ""}" title="${esc(name)}"><span style="background-image:url('${esc(img)}')"></span><small>${esc(name)}</small></a>`;
    }).join("")}</div>`;
  };

  // ---------- phones: the bottom bar ----------
  const tabs = () => {
    const ds = doors().filter((d) => ["movies", "shows", "music", "games"].includes(d.key)).slice(0, 3);
    return [{ key: "home", label: "Home", icon: "home", color: "var(--ink)" }]
      .concat(ds.map((d) => ({ key: d.key, label: d.name, icon: d.icon, color: d.color, door: true })))
      .concat([{ key: "more", label: "More", icon: "more", color: "var(--ink)" }]);
  };
  const paintBar = () => {
    if (!narrow()) { if (bar) bar.hidden = true; return; }
    if (!bar) {
      bar = document.createElement("nav");
      bar.id = "bbar"; bar.className = "bbar"; bar.setAttribute("aria-label", "Benflix menu");
      document.body.appendChild(bar);
      bar.addEventListener("click", onTab);
    }
    bar.hidden = false;
    const spot = spotOf(location.hash);
    const list = tabs();
    const on = list.some((t) => t.key === spot) ? spot : "more";
    const h = B.holidayNav ? B.holidayNav() : null;
    const deco = (t) => (h && h.mark && t.key === on ? `<b class="bt-mark">${B.holidaySvg(h.mark, "0 0 12 18")}</b>` : "");
    const html = list.map((t) => `<button type="button" class="bt ${t.key === on ? "on" : ""}" data-tab="${t.key}" style="--c:${t.color}"><i>${HI(t.key, t.icon)}${t.key === "more" && B.newsDot ? '<b class="rl-dot"></b>' : ""}${deco(t)}</i><span>${esc(t.label)}</span></button>`).join("");
    if (bar.dataset.html !== html) { bar.innerHTML = html; bar.dataset.html = html; }
  };
  const onTab = (e) => {
    const b = e.target.closest("[data-tab]");
    if (!b) return;
    const k = b.dataset.tab;
    if (k === "home") return B.go("#/home");
    if (k === "more") return moreSheet();
    // Approved rx-phonepanel: tapping the door you're already in opens its places
    if (doorOf(location.hash || "") === k) return placesSheet(k);
    B.go("#/" + k);
  };
  const sheetRow = (href, icon, label, color, note) => `<a class="sh-row" href="${esc(href)}" style="--c:${color || "var(--ink2)"}">${I(icon)}<span>${esc(label)}</span>${note ? `<small>${esc(note)}</small>` : ""}</a>`;
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
      ${rest.map((d) => sheetRow("#/" + d.key, d.icon, d.name, d.color)).join("")}
      ${sheetRow("#/search", "search", "Search")}
      ${kids ? "" : sheetRow("#/requests", "plus", "Requests", TEAL, reqCount ? String(reqCount) : "")}
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
  B.railLightUp = () => {
    if (!rail || rail.hidden) return;
    const icons = $$(".rl-i", rail);
    lighting = true;
    icons.forEach((x) => { x.classList.remove("lit"); x.classList.add("dark"); });
    B.sound("doors");
    // Top to bottom, the door notes in step: Home and Search first, then each door
    // While a holiday is on the icons light up in its colours (approved hq-start A)
    const h = B.holidayNav ? B.holidayNav() : null;
    if (h) icons.forEach((x, i) => x.style.setProperty("--lc", h.lights[i % h.lights.length]));
    icons.forEach((x, i) => setTimeout(() => { x.classList.remove("dark"); x.classList.add("lit"); setTimeout(() => x.classList.remove("lit"), 700); }, 120 + i * 140));
    setTimeout(() => { lighting = false; }, 160 + icons.length * 140);
  };

  // ---------- keys: Esc closes the panel; M opens the menu (like ☰ on a remote) ----------
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && panel) {
      e.preventDefault(); e.stopPropagation();
      const door = closePanel();
      const back = rail && $(`[data-door="${door}"]`, rail);
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
