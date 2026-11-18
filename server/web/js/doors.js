/* Benflix web: the side menu inside every door (Redesign 2, section 6, approved sm-rail). In a browser it is
   always open on the left, with the door's own sections (the section you're on sits on a pill in the door's
   colour). On a phone-sized window the same sections are chips under the top bar. It replaces the old
   "Browse by" tiles, and the Books door's own side menu. Movies, Shows, Music and Books sections are their own
   pages; Live TV and Games sections are parts of the one long page, so those scroll to it.
   Everything sits in one closure: the web scripts share one scope, so nothing here is a top-level name. */

(() => {
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  // Server isn't a door: its menu (Redesign 2, approved sv2-glance) uses a white pill
  // Glowfin (Ben 2026-11-18): every area uses the same blue (the old Benflix colour per area is gone)
  const COLORS = { movies: "#7AA7FF", shows: "#7AA7FF", live: "#7AA7FF", games: "#7AA7FF", music: "#7AA7FF", books: "#7AA7FF", server: "#F1F2F5" };
  const ON = { movies: "#0F1521", shows: "#0F1521", live: "#0F1521", games: "#0F1521", music: "#0F1521", books: "#0F1521", server: "#0B0C10" };
  const ICON = {
    home: '<path d="M3 11.5 12 4l9 7.5M5 10v10h14V10"/>',
    image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 16-5-5-9 9"/>',
    ears: '<path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="4" height="7" rx="1.5"/><rect x="17" y="14" width="4" height="7" rx="1.5"/>',
    cal: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  };
  const I = (name) => (ICON[name] ? `<svg class="i" viewBox="0 0 24 24" aria-hidden="true">${ICON[name]}</svg>` : B.I(name));
  B.doorIcon = I;
  // Books counts, filled in once the Books page has asked Tower
  let bookCounts = null;

  // Each section: [label, icon, link, how to tell it's the one you're on]. A link starting "jump:" scrolls
  // to that row on the same page instead (Live TV, Games).
  const menus = () => {
    const kids = !!(B.me && B.me.kids);
    const grid = (door, q) => `#/grid/${door}?${q}`;
    const has = (k) => new URLSearchParams(location.hash.split("?")[1] || "").get(k);
    const gridOf = (door) => location.hash.startsWith(`#/grid/${door}`);
    const plain = (door) => gridOf(door) && !has("type") && !has("mine") && !has("anime") && !has("genre") && !has("studio");
    const movies = [
      ["Home", "home", "#/movies", () => location.hash === "#/movies"],
      ["All movies", "film", grid("movies", "title=All%20movies"), () => plain("movies")],
      ["Genres", "list", "#/browse/movies/genres", () => location.hash === "#/browse/movies/genres" || (gridOf("movies") && !!has("genre"))],
      kids ? null : ["Collections", "image", grid("movies", "type=BoxSet&title=Collections"), () => gridOf("movies") && has("type") === "BoxSet"],
      kids ? null : ["Studios", "flag", "#/browse/movies/studios", () => location.hash === "#/browse/movies/studios" || (gridOf("movies") && !!has("studio"))],
      ["My List", "plus", grid("movies", "mine=1&title=My%20List"), () => gridOf("movies") && has("mine") === "1"],
      kids ? null : ["Anime", "star", grid("movies", "anime=only&title=Anime%20movies"), () => gridOf("movies") && has("anime") === "only"],
    ];
    const shows = [
      ["Home", "home", "#/shows", () => location.hash === "#/shows"],
      ["All shows", "tv", grid("shows", "title=All%20shows"), () => plain("shows") || (gridOf("shows") && has("sort") === "newshow")],
      ["Genres", "list", "#/browse/shows/genres", () => location.hash === "#/browse/shows/genres" || (gridOf("shows") && !!has("genre"))],
      kids ? null : ["Networks", "flag", "#/browse/shows/studios", () => location.hash === "#/browse/shows/studios" || (gridOf("shows") && !!has("studio"))],
      ["My List", "plus", grid("shows", "mine=1&title=My%20List"), () => gridOf("shows") && has("mine") === "1"],
      kids ? null : ["Anime", "star", grid("shows", "anime=only&title=Anime"), () => gridOf("shows") && has("anime") === "only"],
    ];
    const live = [
      ["Home", "home", "#/live", () => true],
      ["Favourites", "star", "jump:Favourites"],
      ["Recently watched", "restart", "jump:Recently watched"],
      ["Guide", "cal", "jump:Guide"],
    ];
    // Glowfin G1: the same places as the Games tabs
    const games = [
      ["For you", "home", "#/games", () => location.hash === "#/games"],
      ["Consoles", "pad", "#/games?p=consoles", () => has("p") === "consoles" || location.hash.startsWith("#/console/")],
      ["Movie games", "film", "#/games?p=movie", () => has("p") === "movie"],
      ["Party", "phone", "#/games?p=party", () => has("p") === "party"],
      ["Scores", "chart", "#/games?p=scores", () => has("p") === "scores"],
    ];
    // Music redesign 3 (approved g2-places): the same four places as the TV and phones
    const music = [
      ["Home", "home", "#/music", () => location.hash === "#/music"],
      ["Search", "search", "#/msearch", () => location.hash.startsWith("#/msearch")],
      ["Your library", "list", "#/mlibrary", () => location.hash.startsWith("#/mlibrary") || location.hash.startsWith("#/mgrid/") || location.hash === "#/songs" || location.hash === "#/liked"],
      ["Ask for music", "plus", "#/requests?tab=music", () => false],
    ];
    const place = () => has("p") || "home";
    const c = bookCounts || {};
    // Glowfin B1: the same places as the Books tabs
    const books = [
      ["home", "For you", "home"], ["book", "Books", "book"], ["audiobook", "Audiobooks", "ears"],
      ["series", "Series", "list"], ["want", "Want to read", "star"],
    ].map(([k, l, i]) => [l, i, k === "home" ? "#/books" : `#/books?p=${k}`, () => place() === k, c[k]]);
    return { movies, shows, live, games, music: kids ? null : music, books, server: null };
  };

  // Which door (if any) the page you're on belongs to: only the door pages themselves, not title pages
  const doorOf = (hash) => {
    if (/^#\/(movies$|grid\/movies|browse\/movies)/.test(hash)) return "movies";
    if (/^#\/(shows$|grid\/shows|browse\/shows)/.test(hash)) return "shows";
    if (/^#\/live$/.test(hash)) return "live";
    if (/^#\/(games(\?|$)|console\/)/.test(hash)) return "games";
    if (/^#\/(music$|msearch|mlibrary|mthemes\/|songs$|liked$|mgrid\/)/.test(hash)) return "music";
    if (/^#\/books(\?|$)/.test(hash)) return "books";
    if (/^#\/server(\/|$)/.test(hash)) return "server";
    return null;
  };

  const html = (door) => {
    const list = (menus()[door] || []).filter(Boolean);
    if (list.length < 2) return "";
    const anyOn = list.slice(1).some((s) => s[3] && s[3]());
    return `<nav class="drail" data-door="${esc(door)}" aria-label="${esc(door)} sections" style="--door:${COLORS[door]};--ondoor:${ON[door]}">${list.map((s, i) => {
      const [label, icon, link, isOn, count] = s;
      const on = i === 0 ? !anyOn : !!(isOn && isOn());
      const jump = link.startsWith("jump:");
      return `<a class="${on ? "on" : ""}" href="${jump ? "#" : esc(link)}" ${jump ? `data-jump="${esc(link.slice(5))}"` : ""}>${I(icon)}<span>${esc(label)}</span>${count ? `<small>${esc(count)}</small>` : ""}</a>`;
    }).join("")}</nav>`;
  };

  // Navigation redesign: a door's places live in its panel off the rail now (js/nav.js), so this menu stays only on
  // the Server page (it keeps its own menu, like the TV). Music has its tabs along the top (Glowfin M1).
  B.doorPlaces = (door) => (menus()[door] || []).filter(Boolean);
  const wanted = (door) => (door === "server" ? html(door) : "");

  // A place that's a row on the door's page (Live TV, Games): go to the door, then scroll to that row
  B.doorJump = (door, label, tries) => {
    const page = $("#page");
    const name = String(label).toLowerCase();
    if (location.hash !== "#/" + door) { B.go("#/" + door); setTimeout(() => B.doorJump(door, label, 1), 400); return; }
    const row = $$("section.rowx h3, .rowx h3", page).find((h) => h.textContent.trim().toLowerCase().startsWith(name));
    if (!row) {
      // The rows may still be loading in
      if (tries && tries < 10) { setTimeout(() => B.doorJump(door, label, tries + 1), 300); return; }
      if (B.toast) B.toast(`Nothing in ${label} yet`);
      return;
    }
    const top = row.getBoundingClientRect().top + window.scrollY - (parseInt(getComputedStyle(document.documentElement).getPropertyValue("--bar")) || 76) - 12;
    window.scrollTo({ top, behavior: "smooth" });
    const first = row.closest("section, .rowx").querySelector("a, button");
    if (first) setTimeout(() => first.focus({ preventScroll: true }), 350);
  };

  // Puts the menu just under the top bar of the page (again after the page redraws itself)
  const paint = () => {
    const page = $("#page");
    if (!page) return;
    const door = B.me ? doorOf(location.hash || "") : null;
    const old = $(".drail", page);
    const want = door ? wanted(door) : "";
    document.body.classList.toggle("has-rail", !!want);
    if (!want) { if (old) old.remove(); return; }
    if (old && old.outerHTML === want) return;
    const box = document.createElement("div");
    box.innerHTML = want;
    const nav = box.firstElementChild;
    if (old) old.replaceWith(nav);
    else {
      const top = $(":scope > .top", page);
      if (top) top.after(nav); else page.prepend(nav);
    }
  };

  // The Books page tells the menu how many of each there are
  B.railCounts = (door, counts) => {
    if (door !== "books") return;
    const same = JSON.stringify(counts || {}) === JSON.stringify(bookCounts || {});
    bookCounts = counts || {};
    if (!same) paint();
  };

  window.addEventListener("hashchange", () => setTimeout(paint, 0));
  const start = () => {
    const page = $("#page");
    if (!page) return;
    // Pages redraw themselves by replacing everything in #page: put the menu back each time
    new MutationObserver(() => { const door = B.me ? doorOf(location.hash || "") : null; if (!$(":scope > .drail", page) || document.body.classList.contains("has-rail") !== !!(door && wanted(door))) paint(); })
      .observe(page, { childList: true });
    paint();
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start); else start();
})();
