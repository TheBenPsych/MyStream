/* Glowfin web: Books (ebooks and audiobooks, from Tower's own folders; manga removed by Ben 2026-11-18).
   The same tabs as the TV (For you, Books, Audiobooks, Series, Want to read), book pages, the ebook reader
   (js/reader.js, keys: arrows / Page Up / Page Down / Space), audiobooks in a bar along the bottom (every button:
   Back 15, Pause, Ahead 30, Speed, Sleep, Chapters, volume, Stop), and Requests > Books.
   Everything comes from the helper's /api/books. Your place is saved per profile, so it follows you to the TV and phone. */

(() => {
  const LIME = "#3FE0E8", ON_LIME = "#05222A"; // Glowfin aqua (B1)
  const ICON = {
    book: '<path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5Z"/><path d="M4 19a2 2 0 0 1 2-2h13"/>',
    ears: '<path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="4" height="7" rx="1.5"/><rect x="17" y="14" width="4" height="7" rx="1.5"/>',
    b15: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><text x="12" y="15.5" text-anchor="middle" font-size="7" font-weight="700" fill="currentColor" stroke="none">15</text>',
    f30: '<path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/><text x="12" y="15.5" text-anchor="middle" font-size="7" font-weight="700" fill="currentColor" stroke="none">30</text>',
    moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z"/>',
    chapters: '<path d="M8 6h12M8 12h12M8 18h12"/><circle cx="4" cy="6" r="1.2" fill="currentColor"/><circle cx="4" cy="12" r="1.2" fill="currentColor"/><circle cx="4" cy="18" r="1.2" fill="currentColor"/>',
  };
  // The shared title page (title.js) draws icons with the main set, so lend it the headphones
  if (typeof P === "object" && !P.ears) P.ears = ICON.ears;
  const I = (name, cls) => (ICON[name] ? `<svg class="i ${cls || ""}" viewBox="0 0 24 24" aria-hidden="true">${ICON[name]}</svg>` : B.I(name, cls));
  B.DOORS.books = { key: "books", name: "Books", desc: "Read and listen", color: LIME, icon: "book" };
  B.bookIcon = I;
  const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const enc = encodeURIComponent;
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const api = (method, path, body) => B.call(method, "api/books/" + path, body);
  // Pictures and files from the helper: the sign-in rides in the address (an <img> can't send a header)
  const pic = (u) => (u ? u.replace(/^\//, "") + (u.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token) : "");
  const hm = (s) => { const m = Math.round((s || 0) / 60); return m < 60 ? `${m}m` : `${Math.floor(m / 60)}h${m % 60 ? ` ${m % 60}m` : ""}`; };
  const clock = (s) => { s = Math.max(0, Math.floor(s || 0)); const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60; return (h ? h + ":" + String(m).padStart(2, "0") : m) + ":" + String(x).padStart(2, "0"); };
  const kindWord = (k) => (k === "audiobook" ? "Audiobook" : "Book");

  // ---------- cards ----------
  const card = (t, href) => {
    const tag = t.newCount > 0 ? `<span class="tag" style="background:${LIME};color:${ON_LIME}">+${t.newCount}</span>` : t.new ? '<span class="tag">NEW</span>' : "";
    const bar = t.status === "reading" && t.progress > 0.005 ? `<span class="bk-bar"><i style="width:${Math.max(3, t.progress * 100)}%"></i></span>` : "";
    const done = t.status === "done" ? `<span class="bk-tick">${B.I("check")}</span>` : "";
    const ears = t.kind === "audiobook" ? `<span class="bk-ears">${I("ears")}</span>` : "";
    return `<a class="poster bk" href="${href || (t.notHere ? "#/requests?tab=books" : "#/book/" + enc(t.id))}"><div class="im">${t.cover ? `<img class="art" src="${pic(t.cover)}" alt="" loading="lazy" decoding="async">` : `<div class="noart" style="--door:var(--books)"><span class="nt">${esc(t.name)}</span></div>`}${tag}${ears}${done}${bar}</div>
      <div class="cap"><span class="nm">${esc(t.name)}</span><small>${esc(t.notHere ? "Not on Glowfin yet" : t.line || "")}</small></div></a>`;
  };
  const grid = (tiles) => tiles.length ? `<div class="bk-grid">${tiles.map((t) => card(t)).join("")}</div>` : "";

  // ---------- the Books page (Glowfin B1): tabs For you | Books | Audiobooks | Series | Want to read ----------
  // For you: a top area about the book you're on (its cover fades in on the right), then Keep going (books and
  // audiobooks together), New for you, the top book lists and the rest. The other tabs are grids with chips.
  const PLACES = [["home", "For you"], ["book", "Books"], ["audiobook", "Audiobooks"], ["series", "Series"], ["want", "Want to read"]];
  const CHIPS = [["all", "All"], ["not", "Not started"], ["reading", "In progress"], ["done", "Finished"]];
  const SORTS = [["added", "Recently added"], ["name", "A to Z"], ["author", "Author"], ["read", "Last read"]];
  const sortName = (s) => String(s || "").toLowerCase().replace(/^(the|a|an) /, "");
  // For you's rows: Keep going first, then New for you, then the top lists and the rest (Want to read has its own tab)
  B.booksForYou = (rows) => {
    const kept = (rows || []).filter((r) => r.key !== "want" && r.tiles && r.tiles.length)
      .map((r) => (r.key === "deck" ? Object.assign({}, r, { title: "Keep going" }) : r));
    const at = (k) => (k === "deck" ? 0 : k === "new" ? 1 : 2);
    return kept.map((r, i) => [r, i]).sort((a, b) => at(a[0].key) - at(b[0].key) || a[1] - b[1]).map(([r]) => r);
  };
  // "Keep reading · 64%" over the name in the top area
  const kickOf = (t) => {
    if (t.status === "reading") return (t.kind === "audiobook" ? "Keep listening" : "Keep reading") + " · " + Math.round((t.progress || 0) * 100) + "%";
    if (t.status === "done") return "Finished";
    return t.new ? "New for you" : kindWord(t.kind);
  };
  const topLine = (t) => [t.author, t.series ? (t.seriesIndex ? "Book " + String(t.seriesIndex).replace(/\.0$/, "") + " of " : "") + t.series : "", t.linked ? "ebook and audiobook" : t.kind === "audiobook" ? "audiobook" : "ebook"].filter(Boolean).join(" · ");

  B.route(/^\/books(?:\?.*)?$/, async (m, nav) => {
    const place = PLACES.some(([k]) => k === B.q("p")) ? B.q("p") : "home", chip = B.q("c") || "all", sort = B.q("s") || "added", group = B.q("g");
    B.page().innerHTML = B.top("Books", LIME) + `<div class="mpage bkpage">${place === "home" ? '<section class="mtop bktop" id="bkTop"><div class="art"></div><div class="info"></div></section>' : ""}<div class="bk-main" id="bkMain">${B.spinner()}</div></div>`;
    B.wireTop();
    let home;
    try {
      home = await B.cached("books-home", 15000, () => api("GET", "home"));
    } catch (e) {
      if (!B.alive(nav)) return;
      $("#bkMain").innerHTML = `<div class="h1">Couldn't reach the Books helper on Tower</div><p class="sub">${esc(e.message)}</p><p class="sub">If Books is new, Tower's Glowfin helper needs its setup run once more.</p>`;
      return;
    }
    if (!B.alive(nav)) return;
    const counts = home.counts || {};
    if (B.railCounts) B.railCounts("books", counts);
    const link = (o) => "#/books?" + new URLSearchParams(Object.assign({ p: place, c: chip, s: sort }, o)).toString();
    const main = $("#bkMain");
    if (home.empty) {
      if ($("#bkTop")) $("#bkTop").remove();
      main.innerHTML = `<div class="h1">No books on Glowfin yet</div><p class="sub">Put books or audiobooks in Tower's ${esc((home.folders && Object.values(home.folders).flat().join(", ")) || "Media/Books and Audiobooks folders")}, and they show up here within 10 minutes.</p>
        ${home.requests && !B.me.kids ? '<a class="btn white" href="#/requests?tab=books">Ask for a book</a>' : ""}`;
      return;
    }
    if (place === "home") {
      const rows = B.booksForYou(home.rows);
      main.innerHTML = rows.map((r) => B.row(r.title, r.tiles.map((t) => card(t)))).join("") +
        (home.requests && !B.me.kids ? '<div style="padding-top:8px"><a class="btn" href="#/requests?tab=books">' + B.I("plus") + " Ask for a book</a></div>" : "");
      const all = {};
      rows.forEach((r) => r.tiles.forEach((t) => { all[t.id] = t; }));
      wireBooksTop(all, rows[0] && rows[0].tiles[0]);
      const f = $("#bkMain .poster"); if (f) f.focus({ preventScroll: true });
      return;
    }
    if (place === "series") {
      const list = await B.cached("books-series", 60000, async () => (await api("GET", "series")).series || []);
      if (!B.alive(nav)) return;
      const g = group != null ? list.find((x) => x.name === group) : null;
      if (g) {
        main.innerHTML = `<a class="btn small" href="${link({ g: "" }).replace(/&?g=/, "")}">${B.I("left")} Series</a><div class="h1">${esc(g.name)}</div><p class="sub">${esc(g.line || "")}</p>${grid(g.titles)}`;
        return;
      }
      main.innerHTML = list.length ? `<div class="bk-grid">${list.map((x) => card({ id: x.name, name: x.name, cover: x.cover, line: x.line, kind: x.kind }, link({ g: x.name }))).join("")}</div>` : `<p class="sub">Books in a series show up here.</p>`;
      return;
    }
    const lib = await B.cached("books-lib", 15000, () => api("GET", "library"));
    if (!B.alive(nav)) return;
    let tiles = (lib.titles || []).filter((t) => (place === "want" ? t.want : t.kind === place));
    if (place === "want") tiles = tiles.concat(lib.asked || []);
    if (chip !== "all") tiles = tiles.filter((t) => t.status === chip);
    const by = {
      added: (a, b) => b.added - a.added,
      name: (a, b) => sortName(a.name).localeCompare(sortName(b.name)),
      author: (a, b) => sortName(a.author).localeCompare(sortName(b.author)) || (a.seriesIndex || 0) - (b.seriesIndex || 0),
      read: (a, b) => (b.lastAt || 0) - (a.lastAt || 0),
    }[sort] || ((a, b) => b.added - a.added);
    tiles.sort(by);
    main.innerHTML = `<div class="row bk-chips">${CHIPS.map(([k, l]) => `<a class="chip ${k === chip ? "on" : ""}" style="--accent:${LIME}" href="${link({ c: k })}">${l}</a>`).join("")}
        <select class="field" id="bkSort" style="max-width:200px;padding:7px 12px;border-radius:99px">${SORTS.map(([k, l]) => `<option value="${k}" ${k === sort ? "selected" : ""}>${l}</option>`).join("")}</select></div>
      ${grid(tiles) || `<p class="sub">${place === "want" ? "Nothing in Want to read yet. Press Want to read on any book's page." : "Nothing here with this chip."}</p>`}`;
    $("#bkSort").onchange = (e) => B.go(link({ s: e.target.value }));
  });

  // The top area on For you: what the book you're on is, with its cover fading in on the right. It waits a moment
  // after you stop moving, so passing along a row stays smooth.
  function wireBooksTop(byId, first) {
    const top = $("#bkTop");
    if (!top) return;
    let timer, shown = null;
    const show = (t) => {
      if (!t || t === shown) return;
      shown = t;
      $(".info", top).innerHTML = `<div class="mtop-kick">${esc(kickOf(t))}</div><h1>${esc(t.name)}</h1><p>${esc(topLine(t))}</p>`;
      const art = $(".art", top);
      art.classList.remove("on");
      art.innerHTML = t.cover ? `<span class="one" style="background-image:url('${esc(pic(t.cover))}')"></span>` : "";
      requestAnimationFrame(() => art.classList.add("on"));
    };
    const pointAt = (e) => {
      const a = e.target.closest("#bkMain .poster.bk");
      if (!a) return;
      const id = decodeURIComponent((a.getAttribute("href") || "").replace(/^#\/book\//, ""));
      clearTimeout(timer);
      timer = setTimeout(() => show(byId[id]), shown ? 350 : 0);
    };
    B.page().addEventListener("mouseover", pointAt);
    B.page().addEventListener("focusin", pointAt);
    B.onLeave(() => { B.page().removeEventListener("mouseover", pointAt); B.page().removeEventListener("focusin", pointAt); });
    show(first);
  }
  // ---------- a book's page ----------
  B.route(/^\/book\/([^?]+)/, async (m, nav) => {
    const id = decodeURIComponent(m[1]);
    B.page().innerHTML = B.top("Books", LIME) + `<div id="bk">${B.spinner()}</div>`;
    B.wireTop();
    const t = await api("GET", "title/" + enc(id));
    if (!B.alive(nav)) return;
    const audio = t.kind === "audiobook";
    const place = t.place || {};
    // Glowfin B1: the D1 title page. Cover on the right; Read / Listen (the other way you have it gets its own
    // button), Want to read, More. Under the buttons: About this book in big words, then In this series, More by
    // the author and More like this, one row after another.
    let main = null;
    if (audio) {
      main = { label: place.pos ? "Keep listening" : "Listen", icon: "ears", onClick: () => BK.play(t), note: t.secondsLeft && t.secondsLeft < t.seconds ? hm(t.secondsLeft) + " left" : "" };
    } else if (t.readable) {
      main = t.file === ".pdf"
        ? { label: "Open the PDF", icon: "book", onClick: () => window.open(`api/books/file/${enc(t.id)}?api_key=${enc(B.me.token)}`, "_blank", "noopener") }
        : { label: place.pct > 0.005 && t.status !== "done" ? "Keep reading " + Math.round(place.pct * 100) + "%" : "Read", icon: "book", href: `#/books/read/${enc(t.id)}` };
    }
    if (!main) main = { label: "Read", icon: "book", off: !t.readable && !audio ? "This kind of file can't be opened in Glowfin. EPUB and PDF can." : "Nothing to open yet" };
    const series = t.series ? [...(t.moreBy || []), ...(t.moreLike || [])].filter((x, i, all) => x.series === t.series && x.id !== t.id && all.findIndex((y) => y.id === x.id) === i) : [];
    const grid = (list) => (list.length ? `<div class="grid tp-grid">${list.map((x) => card(x)).join("")}</div>` : "");
    const strip = B.tpStrip([...(t.scores || []).map((s) => [s.site, (+s.score).toFixed(1)]), t.narrator ? ["Read by", t.narrator, true] : null]);
    const notes = [t.missing ? `<p class="sub" style="color:#E8D44D">Tower is missing ${t.missing} part${t.missing === 1 ? "" : "s"} of this audiobook.</p>` : "",
      audio && (t.chapters || []).length > 1 ? `<h4 style="margin:12px 0 8px">${t.chapters.length} chapters</h4><div class="row" style="flex-wrap:wrap;gap:8px">${t.chapters.slice(0, 80).map((c, i) => `<button class="chip" data-ch="${i}">${i + 1}. ${esc(c.title)}${c.seconds ? " · " + hm(c.seconds) : ""}</button>`).join("")}</div>` : ""].join("");
    const tp = B.titlePage({
      title: t.name,
      color: LIME,
      rows: true,
      aboutFirst: true,
      aboutName: "About this book",
      aboutStory: t.desc || "",
      cover: { url: t.cover ? pic(t.cover.replace("w=320", "w=640")) : "", ratio: 2 / 3 },
      info: [kindWord(t.kind), t.author, t.year || "", audio && t.seconds ? hm(t.seconds) : "", !audio && t.minutes ? "About " + hm(t.minutes * 60) + " to read" : "", t.series ? t.series + (t.seriesIndex ? " #" + t.seriesIndex : "") : ""],
      // "Listening is at the same spot" (in aqua under the buttons)
      status: t.placeLine || "",
      buttons: [
        main,
        t.other ? (t.other.kind === "audiobook" ? { label: "Listen", icon: "ears", onClick: async () => BK.play(await api("GET", "title/" + enc(t.other.id))) } : { label: "Read", icon: "book", href: `#/books/read/${enc(t.other.id)}` }) : null,
        { label: t.want ? "On Want to read" : "Want to read", icon: t.want ? "check" : "plus", onClick: async () => { await api("POST", "want", { id: t.id, want: !t.want }); B.cache = {}; B.render(); } },
        { label: "More", icon: "more", onClick: () => B.tpMore(t.name, [
          { label: t.status === "done" ? "Mark not finished" : "Mark finished", icon: "check", onClick: async () => { await api("POST", "mark", { id: t.id, done: t.status !== "done" }); B.cache = {}; B.render(); } },
          t.kind === "book" && t.readable && t.file === ".epub" ? { label: "Download", icon: "dl", href: `api/books/file/${enc(t.id)}?api_key=${enc(B.me.token)}` } : null,
        ]) },
      ].filter(Boolean),
      strip,
      overviewExtra: notes ? `<div class="wrap0" style="max-width:760px;margin-top:10px">${notes}</div>` : "",
      tabs: [
        { name: "In this series", id: "tpSeries", html: grid(series) },
        { name: "More by " + (t.author || "this author"), id: "tpBy", html: grid(t.moreBy || []) },
        { name: "More like this", id: "tpLike", html: grid(t.moreLike || []) },
      ],
    });
    if (t.placeLine) { const w = $("#tpWhy"); if (w) w.classList.add("bk-place"); }
    tp.root.addEventListener("click", (e) => {
      const ch = e.target.closest("[data-ch]");
      if (ch) BK.play(t, +ch.dataset.ch);
    });
    api("POST", "opened/" + enc(t.id)).catch(() => {});
  });

  // ---------- the ebook reader (full page) ----------
  B.route(/^\/books\/read\/([^?]+)/, async (m, nav) => {
    const id = decodeURIComponent(m[1]);
    if (B.music) B.music.stop();
    B.page().innerHTML = `<div id="bkReader" style="position:fixed;inset:0;z-index:90"></div>`;
    document.body.classList.add("reading");
    const r = window.BookReader.open($("#bkReader"), {
      id, token: B.me.token, tv: false, base: "",
      onClose: () => B.back(),
      onPick: (pick) => B.go("#/book/" + enc(pick)),
    });
    const keys = (e) => { if (e.key === "Escape" && !r.back()) r.close(); };
    document.addEventListener("keydown", keys);
    B.onLeave(() => { document.removeEventListener("keydown", keys); document.body.classList.remove("reading"); r.save(); B.cache = {}; });
  });

  // ---------- audiobooks: a bar along the bottom of every page ----------
  const audio = new Audio();
  audio.preload = "auto";
  const BK = (B.books = { now: null, file: 0, listened: 0 });
  let bar, lastSave = 0;
  BK.play = (t, chapter) => {
    if (B.music) B.music.stop();
    const files = t.fileSeconds && t.fileSeconds.length ? t.fileSeconds : [t.seconds || 0];
    const starts = files.reduce((a, s, i) => (a.push(i ? a[i - 1] + files[i - 1] : 0), a), []);
    BK.now = { t, starts, chapters: t.chapters || [] };
    const p = t.place || {};
    let file = 0, at = 0;
    if (chapter != null && BK.now.chapters[chapter]) { file = BK.now.chapters[chapter].file || 0; at = BK.now.chapters[chapter].start || 0; }
    else if (p.pos) { file = Math.min(p.file || 0, files.length - 1); at = Math.max(0, (p.cpos != null ? p.cpos : p.pos - starts[file]) - 3); }
    BK.load(file, at);
    audio.playbackRate = +(load("benflix.bk.speed." + t.id) || 1);
    draw();
  };
  const load = (k) => { try { return localStorage.getItem(k); } catch (e) { return null; } };
  BK.load = (file, at) => {
    BK.file = file;
    audio.src = `api/books/file/${enc(BK.now.t.id)}/${file}?api_key=${enc(B.me.token)}`;
    audio.addEventListener("loadedmetadata", function once() { audio.removeEventListener("loadedmetadata", once); if (at) audio.currentTime = at; }, { once: true });
    audio.play().catch(() => {});
    if ("mediaSession" in navigator) {
      const t = BK.now.t;
      navigator.mediaSession.metadata = new MediaMetadata({ title: t.name, artist: t.author || "", album: t.name, artwork: t.cover ? [{ src: pic(t.cover), sizes: "320x480" }] : [] });
    }
  };
  const pos = () => (BK.now ? (BK.now.starts[BK.file] || 0) + (audio.currentTime || 0) : 0);
  const chapterAt = (p) => { const c = BK.now.chapters; let i = 0; c.forEach((x, j) => { if ((x.at || 0) <= p + 0.5) i = j; }); return i; };
  BK.save = (finished) => {
    if (!BK.now || !B.me) return;
    const p = pos();
    const body = { pos: p, file: BK.file, cpos: audio.currentTime || 0, chapter: chapterAt(p), pct: BK.now.t.seconds ? Math.min(1, p / BK.now.t.seconds) : 0, seconds: Math.round(BK.listened), finished: !!finished };
    BK.listened = 0; lastSave = Date.now();
    api("POST", "place/" + enc(BK.now.t.id), body).catch(() => {});
  };
  BK.stop = () => { if (!BK.now) return; BK.save(); audio.pause(); BK.now = null; draw(); };
  BK.seek = (d) => {
    if (!BK.now) return;
    const target = Math.max(0, pos() + d);
    let f = 0; BK.now.starts.forEach((s, i) => { if (s <= target) f = i; });
    if (f === BK.file) audio.currentTime = target - BK.now.starts[f]; else BK.load(f, target - BK.now.starts[f]);
  };
  audio.addEventListener("ended", () => {
    if (!BK.now) return;
    if (BK.file + 1 < BK.now.starts.length) BK.load(BK.file + 1, 0);
    else { BK.save(true); BK.now = null; draw(); }
  });
  audio.addEventListener("play", () => { if (B.music && B.music.audio && !B.music.audio.paused) B.music.audio.pause(); draw(); });
  audio.addEventListener("pause", () => { BK.save(); draw(); });
  setInterval(() => {
    if (!BK.now) return;
    if (!audio.paused) BK.listened += 1;
    if (!audio.paused && Date.now() - lastSave > 15000) BK.save();
    if (sleepAt && Date.now() >= sleepAt && !audio.paused) { sleepAt = 0; fade(); }
    draw(true);
  }, 1000);
  let sleepAt = 0;
  const fade = () => {
    let v = 1;
    const t = setInterval(() => { v -= 0.05; audio.volume = Math.max(0, v); if (v <= 0) { clearInterval(t); audio.pause(); audio.volume = 1; } }, 500);
  };
  // Music starting (or a movie) stops the audiobook
  document.addEventListener("play", (e) => { if (e.target !== audio && BK.now && !audio.paused && e.target.tagName) audio.pause(); }, true);

  function draw(tick) {
    if (!bar) {
      bar = document.createElement("div");
      bar.className = "mbar bk-mbar hidden";
      bar.innerHTML = `<button class="mbar-open" data-open title="Open the book"><span class="mbar-cover"></span><span class="mbar-words"><b></b><small></small></span></button>
        <div class="mbar-ctl"><button class="circle" data-a="b15" title="Back 15 seconds">${I("b15")}</button><button class="circle white" data-a="toggle" title="Play or pause"></button><button class="circle" data-a="f30" title="Ahead 30 seconds">${I("f30")}</button>
        <select class="field bk-speed" title="Speed">${[0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3].map((s) => `<option value="${s}">${s}x</option>`).join("")}</select>
        <button class="circle" data-a="sleep" title="Sleep timer">${I("moon")}</button><button class="circle" data-a="chapters" title="Chapters">${I("chapters")}</button>${B.volume("", "circle")}<button class="circle" data-a="stop" title="Stop">${B.I("x")}</button></div>
        <div class="mbar-line"><i></i></div>`;
      bar.addEventListener("click", (e) => {
        const a = e.target.closest("[data-a]");
        if (a) {
          e.stopPropagation();
          const k = a.dataset.a;
          if (k === "toggle") audio.paused ? audio.play().catch(() => {}) : audio.pause();
          else if (k === "b15") BK.seek(-15);
          else if (k === "f30") BK.seek(30);
          else if (k === "stop") BK.stop();
          else if (k === "sleep") {
            // Each press moves to the next choice: 15, 30, 45, 60 minutes, then off
            const left = sleepAt ? Math.ceil((sleepAt - Date.now()) / 60000) : 0;
            const next = [15, 30, 45, 60].find((x) => x > left + 1) || 0;
            sleepAt = next ? Date.now() + next * 60000 : 0;
            a.classList.toggle("on", !!sleepAt);
            B.toast(sleepAt ? `Sleep timer: ${next} minutes` : "Sleep timer off");
          } else if (k === "chapters") chapterList(a);
          return;
        }
        if (e.target.closest("[data-open]") && BK.now) B.go("#/book/" + enc(BK.now.t.id));
      });
      $(".bk-speed", bar).onchange = (e) => { audio.playbackRate = +e.target.value; try { localStorage.setItem("benflix.bk.speed." + BK.now.t.id, e.target.value); } catch (x) {} };
      document.body.appendChild(bar);
      B.wireVolume($(".volw", bar), audio, "bookVolume");
    }
    const on = !!BK.now;
    bar.classList.toggle("hidden", !on);
    document.body.classList.toggle("has-bkbar", on);
    if (!on) return;
    const t = BK.now.t, p = pos();
    if (!tick) {
      $(".mbar-cover", bar).style.backgroundImage = t.cover ? `url('${pic(t.cover)}')` : "";
      $(".bk-speed", bar).value = String(audio.playbackRate || 1);
    }
    $(".mbar-words b", bar).textContent = t.name;
    const c = BK.now.chapters[chapterAt(p)];
    $(".mbar-words small", bar).textContent = (c ? c.title + " · " : "") + clock(p) + " of " + clock(t.seconds) + (sleepAt ? " · sleep in " + Math.ceil((sleepAt - Date.now()) / 60000) + "m" : "");
    $("[data-a=toggle]", bar).innerHTML = audio.paused ? B.I("play") : B.I("pause");
    $(".mbar-line i", bar).style.width = (t.seconds ? (p / t.seconds) * 100 : 0) + "%";
  }
  // Chapters: a small list over the bar; the one you're in is lit, and picking one jumps there
  function chapterList(btn) {
    const old = $(".bk-chlist");
    if (old) { old.remove(); return; }
    if (!BK.now || !BK.now.chapters.length) { B.toast("This audiobook has no chapters"); return; }
    const at = chapterAt(pos());
    const box = document.createElement("div");
    box.className = "bk-chlist";
    box.innerHTML = `<b>Chapters</b>` + BK.now.chapters.map((c, i) => `<button class="${i === at ? "on" : ""}" data-chi="${i}"><span>${esc(c.title || "Chapter " + (i + 1))}</span><small>${c.seconds ? hm(c.seconds) : ""}</small></button>`).join("");
    box.addEventListener("click", (e) => {
      const b = e.target.closest("[data-chi]");
      if (!b) return;
      const c = BK.now && BK.now.chapters[+b.dataset.chi];
      if (c) BK.seek((c.at || 0) - pos());
      box.remove();
    });
    document.body.appendChild(box);
    const on = $("button.on", box); if (on) on.scrollIntoView({ block: "center" });
    setTimeout(() => document.addEventListener("click", function off(e) { if (!box.contains(e.target) && e.target !== btn) { box.remove(); document.removeEventListener("click", off); } }), 0);
  }
  window.addEventListener("beforeunload", () => { if (BK.now && !audio.paused) BK.save(); });

  // ---------- Requests > Books ----------
  // Redesign 2 (Ben 2026-11-10): Books sits on the same side list and wall as the other Requests tabs.
  // Book of the week is the first list (its cover in the line above the wall, then the NYT Fiction list).
  B.booksTab = async (nav) => {
    const rows = $("#rows");
    const input = $("#rq");
    const admin = B.me.admin;
    input.placeholder = "Search any book or author";
    rows.innerHTML = `<div id="bfound"></div><div class="grid-wrap rqsplit" id="bsplit"><nav class="rqside" id="bside"></nav><div class="grid-body" id="bwall">${B.spinner()}</div></div>`;
    let d = null, req = null, picked = null, ownedOpen = null, results = { books: [] }, t;
    const bookLists = () => {
      if (!d) return [];
      const nyt = (d.rows || []).find((r) => (r.items || []).length && /Fiction/.test(r.title) && !/Nonfiction/.test(r.title));
      const out = [];
      if (d.hero) out.push({ id: "botw", title: "Book of the week", items: nyt ? nyt.items : d.top10 || [], ranked: true });
      if ((d.top10 || []).length) out.push({ id: "top10", title: "Top 10", items: d.top10, ranked: true });
      (d.rows || []).forEach((r) => {
        if ((r.genres || []).length) out.push({ id: "genres", title: "Genres", genres: r.genres });
        else if ((r.items || []).length) out.push({ id: r.key, title: r.title, items: r.items, ranked: r.ranked });
      });
      return out;
    };
    const reqLine = (r, answer) => ({
      title: r.name, sub: [admin ? r.who : "", r.author, (r.types || []).join(" and ")].filter(Boolean).join(" · "), cover: r.cover, pill: B.bookPill(r), who: answer && admin ? r.who || "" : "",
      acts: answer ? `<button class="btn small white" data-yes="${esc(r.id)}">Yes</button><button class="btn small" data-no="${esc(r.id)}">No</button>`
        : `${r.state === "ready" && r.title ? `<a class="btn small" href="#/book/${enc(r.title)}">Open</a>` : ""}${r.canRetry ? `<button class="btn small" data-retry="${esc(r.id)}">Look again</button>` : ""}<button class="btn small ghost" data-forget="${esc(r.id)}">${B.I("x")}</button>`,
    });
    const draw = () => {
      const side = $("#bside"), wall = $("#bwall");
      if (!side || !wall) return;
      const ls = bookLists();
      const r = req || { mine: [], waiting: [], others: [], problems: [] };
      const waitIds = new Set((r.waiting || []).map((x) => x.id));
      const mine = (admin ? [].concat(r.mine || [], r.others || []) : r.mine || []).filter((x, i, a) => a.findIndex((y) => y.id === x.id) === i && !waitIds.has(x.id));
      const cur = picked === "yours" || picked === "waiting" ? null : ls.find((l) => l.id === picked) || ls[0];
      side.innerHTML = B.rqSide(ls, cur ? cur.id : picked, mine.length, admin ? (r.waiting || []).length : null);
      const notes = ((admin && r.problems) || []).map((p) => `<div class="note">${esc(p)}</div>`).join("")
        + (admin && d && (d.missing || []).length ? `<div class="note">Some lists are hidden until you add the ${d.missing.join(" and ")} key on the Server page. <a href="#/server/tools/booklists">Add it</a></div>` : "");
      if (picked === "yours" || picked === "waiting") {
        const w = picked === "waiting";
        wall.innerHTML = notes + `<div class="rqhead"><b>${w ? "Waiting for you" : "Your requests"}</b><span>${w ? r.waiting.length : mine.length}</span></div>`
          + B.rqLines((w ? r.waiting : mine).map((x) => reqLine(x, w)), !w, w ? "Nobody is waiting on you right now." : "Nothing asked for yet. Click a cover to see the book and ask for it.");
        return;
      }
      if (!d) { wall.innerHTML = notes + B.spinner(); return; }
      if (!cur) { wall.innerHTML = notes + `<p class="sub">Tower is gathering the book lists. Try again in a few minutes.</p>`; return; }
      if (cur.genres) {
        wall.innerHTML = notes + `<div class="rqhead"><b>Genres</b></div><div class="rqgenres">${cur.genres.map((g) => `<a class="bq-genre" href="#/bookgenre/${enc(g.slug)}?name=${enc(g.name)}" style="background:${esc(g.color)}">${esc(g.name)}</a>`).join("")}<a class="bq-genre all" href="#/bookgenre/all">All genres</a></div>`;
        return;
      }
      (cur.items || []).forEach((c) => { BQ.cards[c.key] = c; });
      const owned = cur.items.filter((c) => c.have).length;
      const shown = ownedOpen === cur.id ? cur.items : cur.items.filter((c) => !c.have);
      const hero = cur.id === "botw" && d.hero;
      if (hero) BQ.cards[hero.key] = hero;
      const line = hero
        ? `<div class="rqline bq-line"><a class="cv" href="${aboutHref(hero)}" style="${hero.cover ? `background-image:url('${esc(outPic(hero.cover))}')` : ""}"></a><div class="grow"><div class="kick" style="color:${LIME}">BOOK OF THE WEEK · #1 NYT FICTION</div>
          <b>${esc([hero.name, hero.author, hero.year, hero.line].filter(Boolean).join(" · "))}</b></div>${hero.have ? "" : hero.asked ? B.rqPillHtml(B.bookPill({ state: "", words: hero.asked })) : `<button class="btn small white" data-bq="${esc(hero.key)}">${B.I("plus")} ${admin ? "Ask for it" : "Ask Ben"}</button>`}</div>`
        : `<div class="rqline" id="bline"> </div>`;
      wall.innerHTML = notes + line + `${B.rqGrid(shown.map((c) => BQ.card(c, false)), !!cur.ranked)}${B.rqOwned(owned, ownedOpen === cur.id)}`;
    };
    const lists = async () => {
      try { req = await api("GET", "requests"); } catch (e) { if (!req) B.toast(e.message); }
      if (B.alive(nav) && !input.value.trim()) draw();
    };
    const shelves = async () => {
      try { d = await api("GET", "discover"); } catch (e) { if (B.alive(nav)) $("#bwall").innerHTML = `<div class="note">Couldn't load the book lists. ${esc(e.message)}</div>`; return; }
      if (B.alive(nav)) draw();
    };
    const found = (f) => `<div class="item"><span class="bk-mini" style="${f.cover ? `background-image:url('${esc(f.cover)}')` : ""}"></span><div class="grow"><div class="t">${esc(f.name)}</div><div class="s">${esc([f.author, f.year, f.score ? "Open Library " + (+f.score).toFixed(1) : ""].filter(Boolean).join(" · "))}</div></div>
      <div class="acts">${f.have ? `<a class="btn small" href="#/book/${enc(f.have)}">Ready to read</a>` : f.asked ? B.rqPillHtml(B.bookPill({ state: "", words: f.asked })) : `<button class="btn small white" data-askfor="${esc(f.key)}">Ask for it</button>`}</div></div>`;
    const search = async (text) => {
      const box = $("#bfound");
      const on = text.trim().length >= 2;
      $("#bsplit").style.display = on ? "none" : "";
      if (!on) { box.innerHTML = ""; return draw(); }
      box.innerHTML = B.spinner();
      results = await api("GET", "search?q=" + enc(text.trim())).catch((e) => ({ books: [], error: e.message }));
      if (!B.alive(nav) || input.value.trim() !== text.trim()) return;
      box.innerHTML = (results.books.length ? `<section class="rowx"><h3>Books</h3><div class="wrap" style="padding-bottom:0"><div class="list">${results.books.map((f) => found(f)).join("")}</div></div></section>` : "") ||
        `<div class="wrap empty">${esc(results.error || "Nothing found. Try the author's name, or fewer words.")}</div>`;
    };
    // The one Ask for it panel: Ebook and audiobook (first), Ebook, or Audiobook. Asking adds it to Want to read too.
    const askPanel = (f) => {
      const p = B.side(`<h3>${esc(f.name)}</h3><p class="dim">${esc(f.author || "")}</p><h4 style="margin:14px 0 8px">Ask for ${esc(f.name)}</h4>
        <p class="dim">Also adds it to Want to read${admin ? "" : ". Ben says yes first"}.</p>
        <button class="opt" data-types="ebook,audiobook">Ebook and audiobook</button><button class="opt" data-types="ebook">Ebook</button><button class="opt" data-types="audiobook">Audiobook</button>`);
      p.addEventListener("click", (ev) => {
        const o = ev.target.closest("[data-types]");
        if (!o) return;
        B.busy(o, async () => {
          const r = await api("POST", "request", { key: f.key, name: f.name, author: f.author, year: f.year, cover: f.cover, types: o.dataset.types ? o.dataset.types.split(",") : [] });
          B.closeAll();
          search(input.value);
          lists();
          return r.words || "Asked for it.";
        });
      });
    };
    input.oninput = () => { clearTimeout(t); t = setTimeout(() => search(input.value), 450); };
    rows.onclick = async (e) => {
      const pick = e.target.closest("#bside [data-rqpick]");
      if (pick) { picked = pick.dataset.rqpick; return draw(); }
      if (e.target.closest("[data-rqowned]")) {
        const cur = bookLists().find((l) => l.id === picked) || bookLists()[0];
        ownedOpen = cur && ownedOpen !== cur.id ? cur.id : null;
        return draw();
      }
      const b = e.target.closest("button");
      if (!b) return;
      if (b.dataset.bq != null) return BQ.quick(b, () => shelves());
      if (b.dataset.askfor != null) {
        const f = results.books.find((x) => x.key === b.dataset.askfor);
        if (f) askPanel(f);
        return;
      }
      await B.busy(b, async () => {
        if (b.dataset.yes) await api("POST", "answer", { id: b.dataset.yes, yes: true });
        if (b.dataset.no) await api("POST", "answer", { id: b.dataset.no, yes: false });
        if (b.dataset.retry) await api("POST", "retry/" + enc(b.dataset.retry));
        if (b.dataset.forget) await api("DELETE", "request/" + enc(b.dataset.forget));
        lists();
      });
    };
    // The line above the wall: the book under the mouse
    rows.onmouseover = (e) => {
      const c = e.target.closest("#bwall .poster"), line = $("#bline");
      if (c && line) line.textContent = (c.querySelector(".cap") || c).textContent.trim() || " ";
    };
    shelves();
    if (input.value.trim()) search(input.value);
    await lists();
    B.every(10000, () => { if (!input.value.trim()) lists().catch(() => {}); });
  };

  // ---------- Requests > Books: the rows, genre pages and a book's details page ----------
  // Covers from NYT / Hardcover / Open Library are outside pictures: no sign-in in their address
  const outPic = (u) => (!u ? "" : /^https?:/.test(u) ? u : pic(u));
  const BQ = {};
  const aboutHref = (c) => c.have ? "#/book/" + enc(c.have) : `#/bookask?key=${enc(c.key)}&name=${enc(c.name || "")}&author=${enc(c.author || "")}${c.audioOnly ? "&audio=1" : ""}`;
  BQ.card = (c, ranked) => {
    const tag = ranked && c.rank ? `<span class="tag" style="background:${LIME};color:${ON_LIME}">#${c.rank}</span>` : "";
    const ears = c.audioOnly ? `<span class="bk-ears">${I("ears")}</span>` : "";
    const under = (c.asked && B.bookPill ? B.bookPill({ state: "", words: c.asked }).w : c.asked) || c.award || c.line || c.author || "";
    return `<a class="poster bk" href="${aboutHref(c)}"><div class="im">${c.cover ? `<img class="art" src="${esc(outPic(c.cover))}" alt="" loading="lazy" decoding="async">` : `<div class="noart" style="--door:var(--books)"><span class="nt">${esc(c.name)}</span></div>`}${tag}${ears}</div>
      <div class="cap"><span class="nm">${esc(c.name)}</span><small${c.asked ? ` style="color:${LIME};font-weight:700"` : ""}>${esc(under)}</small></div></a>`;
  };
  const genreTile = (g) => `<a class="bq-genre" href="#/bookgenre/${enc(g.slug)}?name=${enc(g.name)}" style="background:${esc(g.color)}">${esc(g.name)}</a>`;
  BQ.page = (d, inGenre) => {
    const admin = B.me.admin;
    let h = "";
    if (admin && (d.missing || []).length && !inGenre) {
      h += `<div class="wrap"><div class="note">Some rows are hidden until you add the ${d.missing.join(" and ")} key on the Server page. <a href="#/server/tools/booklists">Add it</a></div></div>`;
    }
    const hero = d.hero;
    if (hero) {
      const btn = hero.have ? `<a class="btn white big" href="#/book/${enc(hero.have)}">${B.I("check")} On Glowfin</a>`
        : hero.asked ? `<span class="btn big" style="color:${LIME}">${esc(hero.asked)}</span>`
          : `<button class="btn white big" data-bq="${esc(hero.key)}">${B.I("plus")} ${admin ? "Ask for it" : "Ask Ben"}</button>`;
      h += `<div class="wrap"><div class="bq-hero"><a class="cv" href="${aboutHref(hero)}" style="${hero.cover ? `background-image:url('${esc(outPic(hero.cover))}')` : ""}"></a><div class="grow">
        <div class="kick" style="color:${LIME}">${inGenre ? "PICK OF THE WEEK" : "BOOK OF THE WEEK"}</div><div class="t">${esc(hero.name)}</div>
        <div class="meta">${esc([hero.author, hero.line].filter(Boolean).join(" · "))}</div>${hero.desc ? `<p class="ov">${esc(hero.desc)}</p>` : ""}
        <div class="btns">${btn}<a class="btn big" href="${aboutHref(hero)}">${I("book")} About this book</a></div></div></div></div>`;
      BQ.cards[hero.key] = hero;
    }
    const remember = (list) => (list || []).forEach((c) => { BQ.cards[c.key] = c; });
    remember(d.top10);
    if ((d.top10 || []).length) {
      h += B.row(inGenre ? `Top 10 in ${d.name}` : "Top 10 books right now", d.top10.slice(0, 10).map((c, i) => `<div class="top10 bq-t10"><span class="num ${i === 9 ? "ten" : ""}">${i + 1}</span>${BQ.card(c)}</div>`));
    }
    (d.rows || []).forEach((r) => {
      if ((r.genres || []).length) {
        h += B.row(r.title, r.genres.map(genreTile).concat([`<a class="bq-genre all" href="#/bookgenre/all">All genres</a>`]));
      } else {
        remember(r.items);
        h += B.row(r.title, (r.items || []).map((c) => BQ.card(c, r.ranked)));
      }
    });
    if (!h) h = `<div class="wrap"><p class="sub">Tower is gathering the book lists. Try again in a few minutes.</p></div>`;
    return h;
  };
  BQ.cards = {};
  // "Ask for it" on Book of the week (Both, or Audiobook on audiobooks)
  BQ.quick = (b, after) => B.busy(b, async () => {
    const c = BQ.cards[b.dataset.bq];
    if (!c) return "";
    const r = await api("POST", "request", { key: c.key, name: c.name, author: c.author, year: c.year, cover: c.cover, types: c.audioOnly ? ["audiobook"] : ["ebook", "audiobook"] });
    if (after) after();
    return r.words || "Asked for it.";
  });

  B.route(/^\/bookgenre\/(\w+)/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home");
    const slug = m[1];
    B.page().innerHTML = B.top("Requests", null, { solid: false }) + `<div class="wrap" style="padding-bottom:6px"><a class="chip" href="#/requests?tab=books">${B.I("left")} Books requests</a>
      <div class="h1" style="margin-top:12px">${esc(B.q("name") || (slug === "all" ? "All genres" : ""))}</div></div><div class="rows" id="bqg">${B.spinner()}</div>`;
    B.wireTop();
    const el = $("#bqg");
    const draw = async () => {
      try {
        if (slug === "all") {
          const g = await api("GET", "discover/genres");
          if (B.alive(nav)) el.innerHTML = `<div class="wrap"><div class="bq-genres">${(g.genres || []).map(genreTile).join("")}</div></div>`;
          return;
        }
        const d = await api("GET", "discover/genre/" + enc(slug));
        if (!B.alive(nav)) return;
        el.innerHTML = BQ.page(d, true);
      } catch (e) {
        if (B.alive(nav)) el.innerHTML = `<div class="wrap"><div class="note">${esc(e.message)}</div></div>`;
      }
    };
    el.onclick = (e) => { const b = e.target.closest("[data-bq]"); if (b) BQ.quick(b, draw); };
    draw();
  });

  B.route(/^\/bookask/, async (m, nav) => {
    if (B.me.kids) return B.go("#/home");
    const key = B.q("key") || "", audioOnly = B.q("audio") === "1";
    B.page().innerHTML = B.top("Requests", null, { solid: false }) + `<div class="wrap" style="padding-bottom:0"><a class="chip" href="#/requests?tab=books">${B.I("left")} Books requests</a></div><div id="bqa">${B.spinner()}</div>`;
    B.wireTop();
    const el = $("#bqa");
    let a, pick = audioOnly ? "audiobook" : "both";
    const draw = async () => {
      try {
        a = await api("GET", `about?key=${enc(key)}&name=${enc(B.q("name") || "")}&author=${enc(B.q("author") || "")}`);
      } catch (e) {
        if (B.alive(nav)) el.innerHTML = `<div class="wrap"><div class="note">${esc(e.message)}</div></div>`;
        return;
      }
      if (!B.alive(nav)) return;
      const admin = B.me.admin;
      const scores = [a.score ? `<span><b>${(+a.score).toFixed(1)}</b>${esc(a.scoreFrom || "Score")} /5</span>` : "",
        a.nyt && a.nyt.rank ? `<span><b>#${a.nyt.rank}</b>NYT ${esc(a.nyt.list || "")}${a.nyt.weeks > 1 ? `, ${a.nyt.weeks} weeks` : ""}</span>` : "",
        a.award ? `<span>${esc(a.award)}</span>` : ""].join("");
      const chips = (audioOnly ? [["audiobook", "Audiobook"]] : [["ebook", "Ebook"], ["audiobook", "Audiobook"], ["both", "Both"]])
        .map(([k, l]) => `<button class="chip ${pick === k ? "on" : ""}" data-pick="${k}">${l}</button>`).join("");
      const ask = a.have ? `<a class="btn white big" href="#/book/${enc(a.have)}">${B.I("check")} On Glowfin</a>`
        : a.asked ? `<span class="btn big" style="color:${LIME}">${esc(a.asked)}</span>`
          : `${chips}<button class="btn white big" data-act="ask">${B.I("plus")} ${admin ? "Ask for it" : "Ask Ben"}</button>`;
      const s = a.series;
      const missing = s ? s.books.filter((b) => !b.have && !b.asked && !b.isThis) : [];
      const missWords = missing.length === 1 ? "book " + missing[0].pos : missing.length === 2 ? `books ${missing[0].pos} and ${missing[1].pos}` : `the other ${missing.length} books`;
      const seriesRow = s && s.books.length > 1 ? `<section class="rowx"><h3>${esc(s.name)} · ${s.books.length} books ${missing.length ? `<button class="btn small" data-act="series">${B.I("plus")} ${admin ? "Ask for" : "Ask Ben for"} ${missWords}</button>` : ""}</h3>
        <button class="arrow l" data-scroll="-1" aria-label="Scroll left">${B.I("left")}</button><div class="strip">${s.books.map((b) => {
          const img = b.cover ? `background-image:url('${esc(outPic(b.cover))}')` : "";
          const href = b.have ? "#/book/" + enc(b.have) : b.isThis ? "" : `#/bookask?key=${enc(b.key)}&name=${enc(b.name)}&author=${enc(b.author || "")}`;
          return `<a class="poster bk" ${href ? `href="${href}"` : ""}><div class="im" style="${img}">${b.cover ? "" : `<span class="bk-name">${esc(b.name)}</span>`}${b.have ? `<span class="bk-tick">${B.I("check")}</span>` : ""}</div>
            <div class="cap" ${b.isThis ? `style="color:${LIME}"` : ""}><span class="nm">${b.pos}. ${esc(b.name)}</span><small${b.have || b.asked ? ` style="color:${LIME}"` : ""}>${esc(b.have ? "On Glowfin" : b.asked || (b.isThis ? "This book" : ""))}</small></div></a>`;
        }).join("")}</div><button class="arrow r" data-scroll="1" aria-label="Scroll right">${B.I("right")}</button></section>` : "";
      const back = `<div class="back"><b style="color:${LIME}">${esc((a.name || "").toUpperCase())}</b><p>${esc((a.back && a.back.blurb) || "No blurb yet.")}</p>${((a.back && a.back.quotes) || []).slice(0, 2).map((q) => `<p class="q">“${esc(q.text)}”</p>`).join("")}</div>`;
      el.innerHTML = `<div class="wrap bq-top"><div><button class="bq-flip" data-act="flip" title="Turn the cover over"><div class="front" style="${a.cover ? `background-image:url('${esc(outPic(a.cover))}')` : ""}">${a.cover ? "" : `<span class="bk-name">${esc(a.name)}</span>`}</div>${back}</button>
          <div class="dim" style="font-size:13px;margin-top:8px;text-align:center">Click the cover to turn it over</div></div>
        <div class="grow"><div class="kick" style="color:${LIME}">${audioOnly ? "AUDIOBOOK" : "BOOK"}</div><div class="h1" style="font-size:40px;margin:4px 0">${esc(a.name)}</div>
          <div class="meta">${esc([a.author, a.year, (a.genres || []).slice(0, 2).join(", ")].filter(Boolean).join(" · "))}</div>
          ${scores ? `<div class="scores">${scores}</div>` : ""}
          <div class="btns" style="flex-wrap:wrap">${ask}${a.have ? "" : `<button class="btn big" data-act="want">${B.I(a.want ? "check" : "plus")} ${a.want ? "On Want to read" : "Want to read"}</button>`}</div>
          <h3 style="margin:22px 0 8px">About this book</h3><p class="bq-about">${esc(a.desc || "No description yet.")}</p></div></div>
        ${seriesRow}
        ${(a.facts || []).length ? `<div class="wrap"><div class="bq-facts">${a.facts.map((f) => `<div class="bq-fact"><small>${esc(f.label)}</small>${esc(f.value)}</div>`).join("")}</div></div>` : ""}
        ${B.row("More by " + String(a.author || "this author").split(",")[0], (a.byAuthor || []).map((c) => BQ.card(c)))}
        ${B.row("More like this", (a.likeThis || []).map((c) => BQ.card(c)))}`;
    };
    el.onclick = async (e) => {
      const p = e.target.closest("[data-pick]");
      if (p) { pick = p.dataset.pick; el.querySelectorAll("[data-pick]").forEach((x) => x.classList.toggle("on", x === p)); return; }
      const b = e.target.closest("[data-act]");
      if (!b) return;
      const act = b.dataset.act;
      if (act === "flip") return b.classList.toggle("on");
      await B.busy(b, async () => {
        let words = "";
        if (act === "ask") words = (await api("POST", "request", { key: a.key, name: a.name, author: a.author, year: a.year, cover: a.cover, types: pick === "both" ? ["ebook", "audiobook"] : [pick] })).words;
        if (act === "series") {
          for (const x of missing()) await api("POST", "request", { key: x.key, name: x.name, author: x.author, cover: x.cover, types: ["ebook", "audiobook"] });
          words = "Asked for them.";
        }
        if (act === "want") words = (await api("POST", "want", { id: a.key, want: !a.want, name: a.name, author: a.author, cover: a.cover, kind: "book" })).want ? "Added to Want to read." : "Taken off Want to read.";
        await draw();
        return words;
      });
    };
    const missing = () => (a && a.series ? a.series.books.filter((b) => !b.have && !b.asked && !b.isThis) : []);
    draw();
  });
})();
