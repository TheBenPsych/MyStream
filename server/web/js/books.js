/* Benflix web: Books, the lime sixth door (manga, comics, ebooks and audiobooks, from Tower's own folders).
   The same side menu as the TV (Home, Manga, Comics, Books, Audiobooks, Series, Authors, Want to read,
   Finished), book pages, the ebook reader (js/reader.js, keys: arrows / Page Up / Page Down / Space), a page
   reader for manga and comics, audiobooks in a bar along the bottom, and Requests > Books.
   Everything comes from the helper's /api/books. Your place is saved per profile, so it follows you to the TV and phone. */

(() => {
  const LIME = "#B9E36A", ON_LIME = "#141C05";
  const ICON = {
    book: '<path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5Z"/><path d="M4 19a2 2 0 0 1 2-2h13"/>',
    ears: '<path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="4" height="7" rx="1.5"/><rect x="17" y="14" width="4" height="7" rx="1.5"/>',
    b15: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><text x="12" y="15.5" text-anchor="middle" font-size="7" font-weight="700" fill="currentColor" stroke="none">15</text>',
    f30: '<path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/><text x="12" y="15.5" text-anchor="middle" font-size="7" font-weight="700" fill="currentColor" stroke="none">30</text>',
    moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z"/>',
  };
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
  const kindWord = (k) => ({ manga: "Manga", comic: "Comic", audiobook: "Audiobook" }[k] || "Book");

  // ---------- cards ----------
  const card = (t, href) => {
    const tag = t.newCount > 0 ? `<span class="tag" style="background:${LIME};color:${ON_LIME}">+${t.newCount}</span>` : t.new ? '<span class="tag">NEW</span>' : "";
    const bar = t.status === "reading" && t.progress > 0.005 ? `<span class="bk-bar"><i style="width:${Math.max(3, t.progress * 100)}%"></i></span>` : "";
    const done = t.status === "done" ? `<span class="bk-tick">${B.I("check")}</span>` : "";
    const ears = t.kind === "audiobook" ? `<span class="bk-ears">${I("ears")}</span>` : "";
    return `<a class="poster bk" href="${href || "#/book/" + enc(t.id)}"><div class="im">${t.cover ? `<img class="art" src="${pic(t.cover)}" alt="" loading="lazy" decoding="async">` : `<div class="noart" style="--door:var(--books)">${esc(t.name)}</div>`}${tag}${ears}${done}${bar}</div>
      <div class="cap"><span class="nm">${esc(t.name)}</span><small>${esc(t.notHere ? "Not on Benflix yet" : t.line || "")}</small></div></a>`;
  };
  const grid = (tiles) => tiles.length ? `<div class="bk-grid">${tiles.map((t) => card(t)).join("")}</div>` : "";

  // ---------- the Books page: side menu + Home rows or a section grid ----------
  const PLACES = [["home", "Home"], ["manga", "Manga"], ["comic", "Comics"], ["book", "Books"], ["audiobook", "Audiobooks"], ["series", "Series"], ["authors", "Authors"], ["want", "Want to read"], ["finished", "Finished"]];
  const CHIPS = [["all", "All"], ["not", "Not started"], ["reading", "In progress"], ["done", "Finished"]];
  const SORTS = [["added", "Recently added"], ["name", "A to Z"], ["author", "Author"], ["read", "Last read"]];
  const sortName = (s) => String(s || "").toLowerCase().replace(/^(the|a|an) /, "");

  B.route(/^\/books(?:\?.*)?$/, async (m, nav) => {
    const place = B.q("p") || "home", chip = B.q("c") || "all", sort = B.q("s") || "added", group = B.q("g");
    B.page().innerHTML = B.top("Books", LIME) + `<div class="bk-page"><nav class="bk-side" id="bkSide"></nav><div class="bk-main" id="bkMain">${B.spinner()}</div></div>`;
    B.wireTop();
    let home;
    try {
      home = await B.cached("books-home", 15000, () => api("GET", "home"));
    } catch (e) {
      if (!B.alive(nav)) return;
      $("#bkMain").innerHTML = `<div class="wrap"><div class="h1">Couldn't reach the Books helper on Tower</div><p class="sub">${esc(e.message)}</p><p class="sub">If Books is new, Tower's Benflix helper needs its setup run once more.</p></div>`;
      return;
    }
    if (!B.alive(nav)) return;
    const counts = home.counts || {};
    const shown = PLACES.filter(([k]) => (k !== "manga" || counts.manga) && (k !== "comic" || counts.comic));
    const link = (o) => "#/books?" + new URLSearchParams(Object.assign({ p: place, c: chip, s: sort }, o)).toString();
    $("#bkSide").innerHTML = shown.map(([k, l]) => `<a class="${k === place ? "on" : ""}" href="${link({ p: k, c: "all" })}">${esc(l)}${counts[k] ? `<small>${counts[k]}</small>` : ""}</a>`).join("");
    const main = $("#bkMain");
    if (home.empty) {
      main.innerHTML = `<div class="wrap"><div class="h1">No books on Benflix yet</div><p class="sub">Put books, audiobooks or manga in Tower's ${esc((home.folders && Object.values(home.folders).flat().join(", ")) || "Media/Books, Audiobooks and Manga folders")}, and they show up here within 10 minutes.</p>
        ${home.requests && !B.me.kids ? '<a class="btn white" href="#/requests?tab=books">Ask for a book</a>' : ""}</div>`;
      return;
    }
    if (place === "home") {
      main.innerHTML = (home.rows || []).map((r) => B.row(r.title, r.tiles.map((t) => card(t)))).join("") +
        (home.requests && !B.me.kids ? '<div class="wrap" style="padding-top:8px"><a class="btn" href="#/requests?tab=books">' + B.I("plus") + " Ask for a book</a></div>" : "");
      return;
    }
    if (place === "series" || place === "authors") {
      const list = await B.cached("books-" + place, 60000, async () => (await api("GET", place))[place] || []);
      if (!B.alive(nav)) return;
      const g = group != null ? list.find((x) => x.name === group) : null;
      if (g) {
        main.innerHTML = `<div class="wrap" style="padding-bottom:0"><a class="btn small" href="${link({ g: "" }).replace(/&?g=/, "")}">${B.I("left")} ${place === "series" ? "Series" : "Authors"}</a><div class="h1">${esc(g.name)}</div><p class="sub">${esc(g.line || "")}</p></div><div class="wrap">${grid(g.titles)}</div>`;
        return;
      }
      main.innerHTML = `<div class="wrap"><div class="h1">${place === "series" ? "Series" : "Authors"}</div>` +
        (list.length ? `<div class="bk-grid">${list.map((x) => card({ id: x.name, name: x.name, cover: x.cover, line: x.line, kind: x.kind }, link({ g: x.name }))).join("")}</div>` : `<p class="sub">Nothing here yet.</p>`) + "</div>";
      return;
    }
    const lib = await B.cached("books-lib", 15000, () => api("GET", "library"));
    if (!B.alive(nav)) return;
    let tiles = (lib.titles || []).filter((t) => place === "want" ? t.want : place === "finished" ? t.status === "done" : t.kind === place);
    if (chip !== "all") tiles = tiles.filter((t) => t.status === chip);
    const by = {
      added: (a, b) => b.added - a.added,
      name: (a, b) => sortName(a.name).localeCompare(sortName(b.name)),
      author: (a, b) => sortName(a.author).localeCompare(sortName(b.author)) || (a.seriesIndex || 0) - (b.seriesIndex || 0),
      read: (a, b) => (b.lastAt || 0) - (a.lastAt || 0),
    }[sort];
    tiles.sort(by);
    const title = (PLACES.find(([k]) => k === place) || [0, "Books"])[1];
    main.innerHTML = `<div class="wrap"><div class="h1">${esc(title)}</div>
      <div class="row" style="margin:8px 0 18px;flex-wrap:wrap;gap:8px">${place === "finished" ? "" : CHIPS.map(([k, l]) => `<a class="chip ${k === chip ? "on" : ""}" style="--accent:${LIME}" href="${link({ c: k })}">${l}</a>`).join("")}
        <select class="field" id="bkSort" style="max-width:200px;padding:7px 12px;border-radius:99px">${SORTS.map(([k, l]) => `<option value="${k}" ${k === sort ? "selected" : ""}>${l}</option>`).join("")}</select></div>
      ${grid(tiles) || `<p class="sub">${place === "want" ? "Nothing in Want to read yet. Press Want to read on any book's page." : "Nothing here with this chip."}</p>`}</div>`;
    $("#bkSort").onchange = (e) => B.go(link({ s: e.target.value }));
  });

  // ---------- a book's page ----------
  B.route(/^\/book\/([^?]+)/, async (m, nav) => {
    const id = decodeURIComponent(m[1]);
    B.page().innerHTML = B.top("Books", LIME) + `<div id="bk">${B.spinner()}</div>`;
    B.wireTop();
    const t = await api("GET", "title/" + enc(id));
    if (!B.alive(nav)) return;
    const manga = t.kind === "manga" || t.kind === "comic", audio = t.kind === "audiobook";
    const place = t.place || {};
    const bits = [t.author, t.narrator ? "Read by " + t.narrator : "", t.year || "",
      audio && t.seconds ? (t.secondsLeft && t.secondsLeft < t.seconds ? hm(t.secondsLeft) + " left" : hm(t.seconds)) : "",
      !audio && !manga && t.minutes ? "About " + hm(t.minutes * 60) + " to read" : ""].filter(Boolean);
    let main = "";
    if (manga) {
      const nv = (t.volumes || []).find((v) => v.id === t.next);
      if (nv) main = `<a class="btn white big" href="#/books/pages/${enc(nv.id)}">${I("book")} ${nv.progress > 0 && !nv.done ? "Keep reading" : "Read"} ${esc(nv.name)}</a>`;
    } else if (audio) {
      main = `<button class="btn white big" data-a="listen">${I("ears")} ${place.pos ? "Keep listening" : "Listen"}</button>`;
    } else if (t.readable) {
      main = t.file === ".pdf"
        ? `<a class="btn white big" href="api/books/file/${enc(t.id)}?api_key=${enc(B.me.token)}" target="_blank" rel="noopener">${I("book")} Open the PDF</a>`
        : `<a class="btn white big" href="#/books/read/${enc(t.id)}">${I("book")} ${place.pct > 0.005 && t.status !== "done" ? "Keep reading " + Math.round(place.pct * 100) + "%" : "Read"}</a>`;
    }
    const other = t.other ? (t.other.kind === "audiobook" ? `<button class="btn big" data-a="other">${I("ears")} Listen</button>` : `<a class="btn big" href="#/books/read/${enc(t.other.id)}">${I("book")} Read</a>`) : "";
    $("#bk").innerHTML = `<div class="details"><div class="hero bk-hero"><div class="hero-in"><div class="main">
        <div class="kick" style="color:${LIME}">${kindWord(t.kind).toUpperCase()}</div>
        <div class="t">${esc(t.name)}</div>
        <div class="meta">${bits.map(esc).join(" · ")}</div>
        ${(t.scores || []).length ? `<div class="scores">${t.scores.map((s) => `<span><b>${(+s.score).toFixed(1)}</b>${esc(s.site)}</span>`).join("")}</div>` : ""}
        ${t.desc ? `<p class="ov bk-desc">${esc(t.desc)}</p>` : ""}
        ${t.missing ? `<p class="sub" style="color:#E8D44D">Tower is missing ${t.missing} part${t.missing === 1 ? "" : "s"} of this audiobook.</p>` : ""}
        ${t.placeLine ? `<p class="sub">${esc(t.placeLine)}</p>` : ""}
        ${!t.readable && !audio && !manga ? `<p class="sub" style="color:#E8D44D">This kind of file can't be opened in Benflix. EPUB and PDF can.</p>` : ""}
        <div class="btns">${main}${other}
          <button class="btn big" data-a="want">${B.I(t.want ? "check" : "plus")} ${t.want ? "On Want to read" : "Want to read"}</button>
          ${manga ? "" : `<button class="btn big" data-a="done">${B.I("check")} ${t.status === "done" ? "Finished" : "Mark finished"}</button>`}
          ${t.kind === "book" && t.readable && t.file === ".epub" ? `<a class="btn big" href="api/books/file/${enc(t.id)}?api_key=${enc(B.me.token)}" download>${B.I("dl")} Download</a>` : ""}</div>
      </div><div class="bk-cover" style="${t.cover ? `background-image:url('${pic(t.cover.replace("w=320", "w=640"))}')` : ""}"></div></div></div>
      ${manga && (t.volumes || []).length ? `<section class="rowx"><h3>${t.volumes.length} volume${t.volumes.length === 1 ? "" : "s"}${t.totalVols > t.volumes.length ? " of " + t.totalVols : ""} <small class="dim">· right-click a volume to mark it read</small></h3><div class="strip">${t.volumes.map((v) => card({ id: v.id, name: v.name, cover: v.cover || t.cover, status: v.done ? "done" : v.progress > 0 ? "reading" : "not", progress: v.progress, line: v.current && !v.done ? "Reading now" : "", new: v.new }, "#/books/pages/" + enc(v.id)).replace('<a class="poster bk"', `<a class="poster bk" data-vol="${esc(v.id)}"`)).join("")}</div></section>` : ""}
      ${audio && (t.chapters || []).length > 1 ? `<section class="rowx"><h3>${t.chapters.length} chapters</h3><div class="wrap" style="padding-bottom:0"><div class="row" style="flex-wrap:wrap;gap:8px">${t.chapters.slice(0, 80).map((c, i) => `<button class="chip" data-ch="${i}">${i + 1}. ${esc(c.title)}${c.seconds ? " · " + hm(c.seconds) : ""}</button>`).join("")}</div></div></section>` : ""}
      ${B.row("More by " + (t.author || "this author"), (t.moreBy || []).map((x) => card(x)))}
      ${B.row("More like this", (t.moreLike || []).map((x) => card(x)))}
    </div>`;
    $("#bk").onclick = async (e) => {
      const a = e.target.closest("[data-a]"), ch = e.target.closest("[data-ch]");
      if (ch) return BK.play(t, +ch.dataset.ch);
      if (!a) return;
      const act = a.dataset.a;
      if (act === "listen") return BK.play(t);
      if (act === "other") return BK.play(await api("GET", "title/" + enc(t.other.id)));
      if (act === "want") { await api("POST", "want", { id: t.id, want: !t.want }); B.cache = {}; return B.render(); }
      if (act === "done") { await api("POST", "mark", { id: t.id, done: t.status !== "done" }); B.cache = {}; return B.render(); }
    };
    $("#bk").oncontextmenu = async (e) => {
      const v = e.target.closest("[data-vol]");
      if (!v) return;
      e.preventDefault();
      const before = confirm("Mark this volume as read.\n\nOK = this one and every one before it\nCancel = just this one");
      await api("POST", "mark", { id: v.dataset.vol, done: true, before });
      B.cache = {};
      B.render();
    };
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

  // ---------- the page reader (manga and comics) ----------
  B.route(/^\/books\/pages\/([^?]+)/, async (m, nav) => {
    const id = decodeURIComponent(m[1]);
    B.page().innerHTML = `<div class="bk-pages" id="bkp" tabindex="0">${B.spinner()}</div>`;
    const v = await api("GET", "vol/" + enc(id));
    if (!B.alive(nav)) return;
    if (v.pdf) {
      $("#bkp").innerHTML = `<div class="center"><div><div class="h1">${esc(v.name)}</div><p class="sub">This volume is a PDF.</p><a class="btn white" target="_blank" rel="noopener" href="api/books/file/${enc(id)}?api_key=${enc(B.me.token)}">Open the PDF</a> <button class="btn" onclick="B.back()">Back</button></div></div>`;
      return;
    }
    const mode = (v.mode && v.mode.mode) || "page";
    let rtl = ((v.mode && v.mode.dir) || (v.rtl ? "rtl" : "ltr")) === "rtl";
    let page = Math.min(Math.max(0, (v.place && v.place.page) || 0), v.pages - 1), since = Date.now();
    const src = (n) => `api/books/vol/${enc(id)}/p/${n}?w=1600&api_key=${enc(B.me.token)}`;
    const save = (finished) => {
      const secs = Math.round((Date.now() - since) / 1000); since = Date.now();
      api("POST", "place/" + enc(id), { page, pages: v.pages, pct: finished ? 1 : page / v.pages, seconds: Math.min(secs, 3000), finished: !!finished }).catch(() => {});
    };
    let saveT;
    const box = $("#bkp");
    const draw = () => {
      if (page >= v.pages) {
        save(true);
        box.innerHTML = `<div class="center"><div>${v.next ? `<div class="kick" style="color:${LIME}">NEXT VOLUME</div><div class="h1">${esc(v.next.name)}</div><a class="btn white" href="#/books/pages/${enc(v.next.id)}">Read it</a>` : `<div class="h1">You finished ${esc(v.name)}</div>`} <button class="btn" onclick="B.go('#/book/${enc(v.title)}')">Back to the book</button></div></div>`;
        return;
      }
      if (mode === "scroll") {
        box.innerHTML = `<div class="bk-strip">${Array.from({ length: v.pages }, (_, n) => `<img loading="lazy" src="${src(n)}" alt="Page ${n + 1}" data-n="${n}">`).join("")}<button class="btn white" style="margin:30px auto;display:block" data-end>Done</button></div>`;
        const img = box.querySelector(`[data-n="${page}"]`);
        if (img) img.scrollIntoView();
        return;
      }
      const two = mode === "spread" && page > 0 && page + 1 < v.pages;
      const shown = two ? [page, page + 1] : [page];
      box.innerHTML = `<div class="bk-spread ${rtl ? "rtl" : ""}">${shown.map((n) => `<img src="${src(n)}" alt="Page ${n + 1}">`).join("")}</div>
        <div class="bk-pbar"><button class="btn small" data-back>${B.I("left")} Back</button><span>${esc(v.series)} · ${esc(v.name)} · page ${page + 1} of ${v.pages}</span>
        <button class="chip" data-dir>${rtl ? "Right to left" : "Left to right"}</button></div>`;
      if (page + 1 < v.pages) { const pre = new Image(); pre.src = src(page + (two ? 2 : 1)); }
      clearTimeout(saveT); saveT = setTimeout(() => save(false), 1500);
    };
    const step = (d) => {
      const two = mode === "spread" && page > 0;
      page = Math.max(0, Math.min(v.pages, page + d * (two ? 2 : 1)));
      draw();
    };
    box.onclick = (e) => {
      if (e.target.closest("[data-back]")) return B.go("#/book/" + enc(v.title));
      if (e.target.closest("[data-end]")) { page = v.pages; return draw(); }
      if (e.target.closest("[data-dir]")) { rtl = !rtl; api("POST", "place/" + enc(id), { readerMode: { mode, dir: rtl ? "rtl" : "ltr" } }).catch(() => {}); return draw(); }
      if (mode === "scroll" || e.target.closest("a,button")) return;
      const left = e.clientX < window.innerWidth / 2;
      step(left === rtl ? 1 : -1);
    };
    const keys = (e) => {
      if (mode === "scroll") return;
      if (e.key === "ArrowRight") step(rtl ? -1 : 1);
      else if (e.key === "ArrowLeft") step(rtl ? 1 : -1);
      else if (e.key === " " || e.key === "PageDown") step(1);
      else if (e.key === "PageUp") step(-1);
      else if (e.key === "Escape") B.go("#/book/" + enc(v.title));
      else return;
      e.preventDefault();
    };
    document.addEventListener("keydown", keys);
    if (mode === "scroll") {
      const watch = setInterval(() => {
        const imgs = $$("img[data-n]", box);
        const top = imgs.find((im) => im.getBoundingClientRect().bottom > 80);
        if (top && +top.dataset.n !== page) { page = +top.dataset.n; save(false); }
      }, 2000);
      B.onLeave(() => clearInterval(watch));
    }
    B.onLeave(() => { document.removeEventListener("keydown", keys); if (page < v.pages) save(false); B.cache = {}; });
    draw();
    api("POST", "opened/" + enc(v.title)).catch(() => {});
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
        <button class="circle" data-a="sleep" title="Sleep timer">${I("moon")}</button><button class="circle" data-a="stop" title="Stop">${B.I("x")}</button></div>
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
            const m = prompt("Sleep timer: minutes (15, 30, 45 or 60). Leave empty to turn it off.", "30");
            sleepAt = m && +m > 0 ? Date.now() + +m * 60000 : 0;
            B.toast(sleepAt ? `Sleep timer: ${+m} minutes` : "Sleep timer off");
          }
          return;
        }
        if (e.target.closest("[data-open]") && BK.now) B.go("#/book/" + enc(BK.now.t.id));
      });
      $(".bk-speed", bar).onchange = (e) => { audio.playbackRate = +e.target.value; try { localStorage.setItem("benflix.bk.speed." + BK.now.t.id, e.target.value); } catch (x) {} };
      document.body.appendChild(bar);
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
  window.addEventListener("beforeunload", () => { if (BK.now && !audio.paused) BK.save(); });

  // ---------- Requests > Books ----------
  B.booksTab = async (nav) => {
    const rows = $("#rows");
    const input = $("#rq");
    input.placeholder = "Search any book, author or manga";
    const pill = (r) => `<span class="word-${r.state === "ready" ? "ok" : r.state === "cant" || r.state === "problem" || r.state === "no" ? "bad" : "check"}">${esc(r.words || r.state)}</span>`;
    const reqList = (title, list, answer) => list && list.length ? `<section class="rowx"><h3>${esc(title)}</h3><div class="wrap" style="padding-bottom:0"><div class="list">${list.map((r) => `<div class="item"><div class="grow"><div class="t">${esc(r.name)}</div>
      <div class="s">${esc([r.author, r.state === "manga" ? "Manga" : (r.types || []).join(" + "), B.me.admin && r.who ? "asked by " + r.who : ""].filter(Boolean).join(" · "))} · ${pill(r)}${r.problem ? " · " + esc(r.problem) : ""}</div></div>
      <div class="acts">${answer ? `<button class="btn small white" data-yes="${esc(r.id)}">Yes</button><button class="btn small" data-no="${esc(r.id)}">No</button>` : ""}${r.state === "ready" && r.title ? `<a class="btn small" href="#/book/${enc(r.title)}">Open</a>` : ""}${r.canRetry ? `<button class="btn small" data-retry="${esc(r.id)}">Look again</button>` : ""}${answer ? "" : `<button class="btn small ghost" data-forget="${esc(r.id)}">${B.I("x")}</button>`}</div></div>`).join("")}</div></div></section>` : "";
    const lists = async () => {
      const r = await api("GET", "requests");
      if (!B.alive(nav) || input.value.trim()) return;
      rows.innerHTML = (r.problems || []).map((p) => `<div class="wrap"><div class="note">${esc(p)}</div></div>`).join("") +
        (B.me.admin ? reqList("Waiting for you", r.waiting, true) : "") + `<div id="bqRows">${shelvesHtml || B.spinner()}</div>` +
        (reqList(B.me.admin ? "What you asked for" : "My books", r.mine) || `<div class="wrap"><p class="sub">Click a cover to see the book and ask for it. You'll get a note on Home when it's here.</p></div>`) +
        (B.me.admin ? reqList("Manga to add", r.mangaToAdd) + reqList("Everyone else's", r.others) : "");
    };
    // The approved rows (Book of the week, Top 10, your series and authors, NYT, Trending, genres, awards, audiobooks)
    let shelvesHtml = "";
    const shelves = async () => {
      try {
        shelvesHtml = BQ.page(await api("GET", "discover"), false);
      } catch (e) {
        shelvesHtml = `<div class="wrap"><div class="note">Couldn't load the book lists. ${esc(e.message)}</div></div>`;
      }
      const el = $("#bqRows");
      if (B.alive(nav) && el) el.innerHTML = shelvesHtml;
    };
    shelves();
    const found = (f, manga) => `<div class="item"><span class="bk-mini" style="${f.cover ? `background-image:url('${esc(f.cover)}')` : ""}"></span><div class="grow"><div class="t">${esc(f.name)}</div><div class="s">${esc([f.author, f.year, f.volumes ? f.volumes + " volumes" : "", f.score ? (manga ? "AniList " : "Open Library ") + (+f.score).toFixed(1) : ""].filter(Boolean).join(" · "))}</div></div>
      <div class="acts">${f.have ? `<a class="btn small" href="#/book/${enc(f.have)}">On Benflix</a>` : f.asked ? `<span class="dim">${esc(f.asked)}</span>` : manga ? `<button class="btn small white" data-ask="${esc(f.key)}" data-types="">Ask for it</button>` :
        `<select class="field" data-kind="${esc(f.key)}" style="padding:6px 10px;border-radius:8px;width:auto"><option value="ebook,audiobook">Both</option><option value="ebook">Ebook</option><option value="audiobook">Audiobook</option></select><button class="btn small white" data-ask="${esc(f.key)}">Ask for it</button>`}</div></div>`;
    let results = { books: [], manga: [] }, t;
    const search = async (text) => {
      if (text.trim().length < 2) return lists();
      rows.innerHTML = B.spinner();
      results = await api("GET", "search?q=" + enc(text.trim())).catch((e) => ({ books: [], manga: [], error: e.message }));
      if (!B.alive(nav) || input.value.trim() !== text.trim()) return;
      rows.innerHTML = (results.books.length ? `<section class="rowx"><h3>Books</h3><div class="wrap" style="padding-bottom:0"><div class="list">${results.books.map((f) => found(f, false)).join("")}</div></div></section>` : "") +
        (results.manga.length ? `<section class="rowx"><h3>Manga</h3><div class="wrap" style="padding-bottom:0"><div class="list">${results.manga.map((f) => found(f, true)).join("")}</div></div></section>` : "") ||
        `<div class="wrap empty">${esc(results.error || "Nothing found. Try the author's name, or fewer words.")}</div>`;
    };
    input.oninput = () => { clearTimeout(t); t = setTimeout(() => search(input.value), 450); };
    rows.onclick = async (e) => {
      const b = e.target.closest("button");
      if (!b) return;
      if (b.dataset.bq != null) return BQ.quick(b, () => shelves());
      await B.busy(b, async () => {
        if (b.dataset.ask != null) {
          const f = results.books.concat(results.manga).find((x) => x.key === b.dataset.ask);
          const sel = $(`[data-kind="${CSS.escape(b.dataset.ask)}"]`, rows);
          const types = sel ? sel.value.split(",") : [];
          const r = await api("POST", "request", { key: f.key, name: f.name, author: f.author, year: f.year, cover: f.cover, types });
          search(input.value);
          return r.words || "Asked for it.";
        }
        if (b.dataset.yes) await api("POST", "answer", { id: b.dataset.yes, yes: true });
        if (b.dataset.no) await api("POST", "answer", { id: b.dataset.no, yes: false });
        if (b.dataset.retry) await api("POST", "retry/" + enc(b.dataset.retry));
        if (b.dataset.forget) await api("DELETE", "request/" + enc(b.dataset.forget));
        lists();
      });
    };
    if (input.value.trim()) search(input.value); else await lists();
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
    const under = c.asked || c.award || c.line || c.author || "";
    return `<a class="poster bk" href="${aboutHref(c)}"><div class="im">${c.cover ? `<img class="art" src="${esc(outPic(c.cover))}" alt="" loading="lazy" decoding="async">` : `<div class="noart" style="--door:var(--books)">${esc(c.name)}</div>`}${tag}${ears}</div>
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
      const btn = hero.have ? `<a class="btn white big" href="#/book/${enc(hero.have)}">${B.I("check")} On Benflix</a>`
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
      const ask = a.have ? `<a class="btn white big" href="#/book/${enc(a.have)}">${B.I("check")} On Benflix</a>`
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
            <div class="cap" ${b.isThis ? `style="color:${LIME}"` : ""}><span class="nm">${b.pos}. ${esc(b.name)}</span><small${b.have || b.asked ? ` style="color:${LIME}"` : ""}>${esc(b.have ? "On Benflix" : b.asked || (b.isThis ? "This book" : ""))}</small></div></a>`;
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
