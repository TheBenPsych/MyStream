/* Benflix Books: the ebook reader (approved er-page). Used by Benflix in the browser and, inside a
   web view, by the TV and phone apps (web/reader.html), so every screen reads books the same way.

   - Pages turn left and right (two columns on TV and wide windows), dark by default, with the book's
     name and chapter on top and a thin lime line plus "12 min left in chapter" at the bottom.
   - Up (or a tap in the middle) shows the bar: Contents, Aa (text size, Dark / Sepia / Light, fonts,
     including an easy-to-read one), and on TV "Send to my phone".
   - Your place is saved on Tower for your profile, so it follows you to the phone and the browser.
   - At the end of the book a "What next?" card offers 3 picks.

   Each chapter loads in a frame from Tower (api/books/x/<book>/<file inside the book>), and the reader
   lays it out in columns and moves one page at a time. Only plain JavaScript. */
(function () {
  "use strict";
  const THEMES = {
    dark: { bg: "#121318", ink: "#D9DBE1", dim: "#8D909B", line: "#2A2D35" },
    sepia: { bg: "#F3E9D2", ink: "#4A3B2A", dim: "#8A7A63", line: "#DCCDAE" },
    light: { bg: "#FAFAF7", ink: "#1C1C1E", dim: "#77787D", line: "#E2E2DD" },
  };
  const FONTS = {
    book: { name: "Book's own", css: "" },
    serif: { name: "Serif", css: "Georgia, 'Noto Serif', 'Droid Serif', serif" },
    sans: { name: "Sans", css: "Outfit, 'Roboto', 'Noto Sans', sans-serif" },
    easy: { name: "Easy to read", css: "OpenDyslexic, Verdana, sans-serif" },
  };
  const SIZES = [0.8, 0.9, 1, 1.12, 1.25, 1.4, 1.6, 1.85];
  const LIME = "#B9E36A";
  const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const store = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} };
  const load = (k, d) => { try { const v = JSON.parse(localStorage.getItem(k)); return v == null ? d : v; } catch (e) { return d; } };

  function open(host, opts) {
    const base = opts.base || "";
    const id = opts.id;
    const tv = !!opts.tv;
    const app = window.BenflixApp || null;
    const settings = Object.assign({ theme: "dark", size: 2, font: "book" }, load("benflix.reader", {}));
    let book = null, chapter = 0, page = 0, pages = 1, words = 0, sizes = [], total = 1;
    let saveTimer = null, readSince = Date.now(), lastTurn = Date.now(), idleTimer = null, ended = false;
    let pendingFrag = null, pendingCpos = null, gone = false;

    try { document.cookie = "bftoken=" + encodeURIComponent(opts.token) + "; path=/; SameSite=Lax"; } catch (e) {}

    host.classList.add("rd");
    host.innerHTML = `
      <div class="rd-head"><span class="rd-name"></span></div>
      <div class="rd-stage"><iframe class="rd-frame" title="Book page" tabindex="-1"></iframe>
        <div class="rd-tap rd-l"></div><div class="rd-tap rd-m"></div><div class="rd-tap rd-r"></div></div>
      <div class="rd-foot"><div class="rd-line"><i></i></div><span class="rd-left"></span></div>
      <div class="rd-bar hidden">
        <button data-a="close">‹ Back</button>
        <button data-a="toc">Contents</button>
        <button data-a="aa">Aa</button>
        ${tv ? '<button data-a="send">Send to my phone</button>' : ""}
        <span class="rd-pct"></span>
      </div>
      <div class="rd-panel hidden"></div>
      <div class="rd-msg hidden"></div>`;
    const $ = (s) => host.querySelector(s);
    const frame = $(".rd-frame");
    const bar = $(".rd-bar"), panel = $(".rd-panel");

    function api(method, path, body) {
      return fetch(base + path, {
        method,
        headers: Object.assign({ "X-Benflix-Token": opts.token, Accept: "application/json" }, body ? { "Content-Type": "application/json" } : {}),
        body: body ? JSON.stringify(body) : undefined,
      }).then(async (r) => {
        const j = await r.json().catch(() => ({}));
        if (!r.ok) throw new Error(j.error || "Couldn't reach Tower.");
        return j;
      });
    }

    function theme() {
      const t = THEMES[settings.theme] || THEMES.dark;
      host.style.setProperty("--rd-bg", t.bg);
      host.style.setProperty("--rd-ink", t.ink);
      host.style.setProperty("--rd-dim", t.dim);
      host.style.setProperty("--rd-line", t.line);
      host.style.setProperty("--rd-lime", LIME);
      return t;
    }

    function cols() { return tv || host.clientWidth >= 1000 ? 2 : 1; }

    function msg(text, sub) {
      const m = $(".rd-msg");
      m.classList.toggle("hidden", !text);
      m.innerHTML = text ? `<div><b>${esc(text)}</b>${sub ? `<p>${esc(sub)}</p>` : ""}</div>` : "";
    }

    // ---------- laying a chapter out in pages ----------
    function geometry() {
      const st = $(".rd-stage");
      return { w: st.clientWidth, h: st.clientHeight, gap: cols() === 2 ? Math.round(st.clientWidth * 0.07) : Math.round(Math.min(48, st.clientWidth * 0.08)) };
    }

    function style() {
      const doc = frame.contentDocument;
      if (!doc || !doc.documentElement) return;
      const t = theme();
      const g = geometry();
      const n = cols();
      const colW = Math.floor((g.w - g.gap * (n - 1)) / n);
      const basePx = (tv ? 21 : host.clientWidth < 600 ? 18 : 19) * SIZES[settings.size];
      const font = FONTS[settings.font] || FONTS.book;
      let s = doc.getElementById("benflix-reader");
      if (!s) {
        s = doc.createElement("style");
        s.id = "benflix-reader";
        (doc.head || doc.documentElement).appendChild(s);
      }
      const easy = settings.font === "easy" ? `@font-face { font-family: OpenDyslexic; src: url("${new URL(base + "web/fonts/opendyslexic_regular.woff2", location.href).href}") format("woff2"); }` : "";
      const outfit = `@font-face { font-family: Outfit; src: url("${new URL(base + "web/fonts/outfit_regular.ttf", location.href).href}"); }`;
      s.textContent = `${easy}${outfit}
        html { margin: 0 !important; padding: 0 !important; height: ${g.h}px !important; overflow: hidden !important; background: ${t.bg} !important; font-size: ${basePx}px !important; }
        body { margin: 0 !important; padding: 0 !important; height: ${g.h}px !important; width: ${g.w}px !important; max-width: none !important;
          column-width: ${colW}px !important; column-gap: ${g.gap}px !important; column-fill: auto !important; column-count: auto !important;
          background: ${t.bg} !important; color: ${t.ink} !important; font-size: 1rem !important; line-height: 1.55 !important;
          ${font.css ? `font-family: ${font.css} !important;` : ""} -webkit-text-size-adjust: 100%; overflow-wrap: break-word; }
        body * { color: inherit !important; background-color: transparent !important; max-width: 100% !important; ${font.css ? `font-family: ${font.css} !important;` : ""} }
        p, li, blockquote, div { font-size: 1rem; }
        h1, h2, h3 { line-height: 1.25 !important; break-after: avoid; }
        img, svg, video { max-width: 100% !important; max-height: ${g.h - 8}px !important; height: auto; object-fit: contain; break-inside: avoid; }
        a { color: ${LIME} !important; text-decoration: none; }
        table { break-inside: avoid; }`;
    }

    function measure() {
      const doc = frame.contentDocument;
      const g = geometry();
      const step = g.w + g.gap;
      const sw = doc.body ? Math.max(doc.body.scrollWidth, doc.documentElement.scrollWidth) : g.w;
      pages = Math.max(1, Math.ceil((sw + g.gap - 4) / step));
      words = (doc.body ? doc.body.textContent : "").split(/\s+/).filter(Boolean).length;
    }

    function show(p) {
      const doc = frame.contentDocument;
      if (!doc) return;
      const g = geometry();
      page = Math.max(0, Math.min(pages - 1, p));
      const x = page * (g.w + g.gap);
      const el = doc.scrollingElement || doc.documentElement;
      el.scrollLeft = x;
      if (el.scrollLeft !== x && doc.body) doc.body.style.transform = `translateX(${-x}px)`;
      foot();
    }

    function foot() {
      const pct = progress();
      $(".rd-line i").style.width = (pct * 100).toFixed(2) + "%";
      const left = Math.max(0, Math.round((words * (1 - (page + 1) / pages)) / 230));
      $(".rd-left").textContent = pages <= 1 && !words ? "" : left < 1 ? "Less than a minute left in chapter" : `${left} min left in chapter`;
      $(".rd-pct").textContent = Math.round(pct * 100) + "%";
      const ch = chapterName();
      $(".rd-name").textContent = book ? book.name + (ch ? " · " + ch : "") : "";
    }

    function chapterName() {
      if (!book) return "";
      let best = "";
      for (const c of book.chapters || []) if (c.index <= chapter) best = c.title;
      return best;
    }

    function progress() {
      if (!book) return 0;
      const before = sizes.slice(0, chapter).reduce((a, b) => a + b, 0);
      const here = sizes[chapter] || 0;
      return Math.min(1, (before + here * (pages > 1 ? page / (pages - 1) : 1)) / total);
    }

    function loadChapter(i, where) {
      chapter = Math.max(0, Math.min(book.spine.length - 1, i));
      return new Promise((done) => {
        frame.onload = () => {
          try {
            style();
            const doc = frame.contentDocument;
            // Let the pictures and fonts settle before counting pages
            const finish = () => {
              if (gone) return;
              measure();
              let p = 0;
              if (where === "end") p = pages - 1;
              else if (typeof where === "number") p = Math.round(where * Math.max(0, pages - 1));
              if (pendingFrag) {
                const el = doc.getElementById(pendingFrag) || doc.querySelector(`[name="${pendingFrag}"]`);
                pendingFrag = null;
                if (el) {
                  const g = geometry();
                  const x = el.getBoundingClientRect().left + (doc.scrollingElement || doc.documentElement).scrollLeft;
                  p = Math.floor(x / (g.w + g.gap));
                }
              }
              show(p);
              wireFrame(doc);
              done();
            };
            const imgs = Array.from(doc.images || []).filter((im) => !im.complete);
            if (!imgs.length) setTimeout(finish, 30);
            else {
              let left = imgs.length;
              const one = () => { if (--left === 0) finish(); };
              imgs.forEach((im) => { im.addEventListener("load", one); im.addEventListener("error", one); });
              setTimeout(() => { if (left > 0) { left = 0; finish(); } }, 2500);
            }
          } catch (e) {
            msg("This page wouldn't open", e.message);
            done();
          }
        };
        frame.src = base + "api/books/x/" + id + "/" + book.spine[chapter].href.split("/").map(encodeURIComponent).join("/");
      });
    }

    function wireFrame(doc) {
      // Keys pressed while the page itself has focus, and links inside the book
      doc.addEventListener("keydown", onKey);
      doc.addEventListener("click", (e) => {
        const a = e.target.closest && e.target.closest("a[href]");
        if (!a) return;
        e.preventDefault();
        const href = a.getAttribute("href") || "";
        if (/^https?:/i.test(href)) return;
        const [file, frag] = href.split("#");
        const cur = book.spine[chapter].href;
        const target = file ? new URL(file, "http://x/" + cur).pathname.slice(1) : cur;
        const at = book.spine.findIndex((s) => s.href === decodeURIComponent(target));
        if (at >= 0) { pendingFrag = frag || null; turnTo(at, 0); }
      });
      swipes(doc);
    }

    // ---------- turning pages ----------
    let busy = false;
    async function turnTo(ch, where) {
      if (busy) return;
      busy = true;
      try { await loadChapter(ch, where); } finally { busy = false; }
      turned();
    }

    function next() {
      if (busy || !book) return;
      if (page < pages - 1) { fade(() => show(page + 1)); turned(); return; }
      if (chapter < book.spine.length - 1) return turnTo(chapter + 1, 0);
      end();
    }

    function prev() {
      if (busy || !book) return;
      if (ended) return unend();
      if (page > 0) { fade(() => show(page - 1)); turned(); return; }
      if (chapter > 0) turnTo(chapter - 1, "end");
    }

    function fade(fn) {
      frame.style.transition = "none";
      frame.style.opacity = "0.25";
      fn();
      requestAnimationFrame(() => { frame.style.transition = "opacity .14s"; frame.style.opacity = "1"; });
    }

    function turned() {
      lastTurn = Date.now();
      if (app && app.awake) try { app.awake(); } catch (e) {}
      clearTimeout(saveTimer);
      saveTimer = setTimeout(save, 1500);
    }

    function save(extra) {
      if (!book) return Promise.resolve();
      const now = Date.now();
      const secs = Math.min(600, Math.round((now - readSince) / 1000));
      readSince = now;
      const body = Object.assign({ pct: progress(), chapter, cpos: pages > 1 ? page / (pages - 1) : 0, seconds: secs }, extra || {});
      return api("POST", "api/books/place/" + id, body).catch(() => {});
    }

    // ---------- the end of the book ----------
    async function end() {
      ended = true;
      save({ pct: 1, finished: true });
      panel.classList.remove("hidden");
      panel.className = "rd-panel rd-end";
      panel.innerHTML = `<h2>You finished ${esc(book.name)}</h2><p class="rd-sub">What next?</p><div class="rd-picks">…</div>
        <div class="rd-acts"><button data-a="close" class="rd-white">Back to Books</button></div>`;
      try {
        const j = await api("GET", "api/books/next/" + id);
        const picks = j.picks || [];
        panel.querySelector(".rd-picks").innerHTML = picks.length
          ? picks.map((p) => `<button class="rd-pick" data-pick="${esc(p.id)}"><span class="rd-cv" style="background-image:url('${base}${p.cover.replace(/^\//, "")}&api_key=${encodeURIComponent(opts.token)}')"></span><b>${esc(p.name)}</b><small>${esc(p.author || "")}</small></button>`).join("")
          : `<p class="rd-sub">Nothing else on Benflix yet. Ask for more in Requests.</p>`;
      } catch (e) {
        panel.querySelector(".rd-picks").innerHTML = "";
      }
      const f = panel.querySelector("button");
      if (f) f.focus();
    }

    function unend() {
      ended = false;
      panel.classList.add("hidden");
    }

    // ---------- the bar, Contents and Aa ----------
    function showBar(on) {
      bar.classList.toggle("hidden", !on);
      if (on) { foot(); const b = bar.querySelector("button"); if (b) b.focus(); }
      else if (panel.classList.contains("hidden")) host.focus();
    }

    function toc() {
      panel.className = "rd-panel";
      const list = (book.chapters && book.chapters.length ? book.chapters : book.spine.map((s, i) => ({ title: "Part " + (i + 1), index: i })));
      panel.innerHTML = `<h3>Contents</h3><div class="rd-list">${list.map((c) => `<button data-go="${c.index}" data-frag="${esc(c.frag || "")}" class="${c.index === chapter ? "on" : ""}">${esc(c.title)}</button>`).join("")}</div>`;
      const on = panel.querySelector(".on") || panel.querySelector("button");
      if (on) { on.focus(); on.scrollIntoView({ block: "center" }); }
    }

    function aa() {
      panel.className = "rd-panel";
      const chips = (group, items, cur) => items.map(([k, label]) => `<button data-${group}="${k}" class="${k === cur ? "on" : ""}">${esc(label)}</button>`).join("");
      panel.innerHTML = `<h3>Aa</h3>
        <div class="rd-row"><span>Text size</span><button data-size="-1" aria-label="Smaller">A−</button><b>${Math.round(SIZES[settings.size] * 100)}%</b><button data-size="1" aria-label="Bigger">A+</button></div>
        <div class="rd-row"><span>Colour</span>${chips("theme", [["dark", "Dark"], ["sepia", "Sepia"], ["light", "Light"]], settings.theme)}</div>
        <div class="rd-row"><span>Font</span>${chips("font", Object.entries(FONTS).map(([k, f]) => [k, f.name]), settings.font)}</div>`;
      const f = panel.querySelector("button.on") || panel.querySelector("button");
      if (f) f.focus();
    }

    function relayout(keepAt) {
      const at = keepAt == null ? (pages > 1 ? page / (pages - 1) : 0) : keepAt;
      style();
      setTimeout(() => { measure(); show(Math.round(at * Math.max(0, pages - 1))); }, 60);
    }

    function setting(k, v) {
      settings[k] = v;
      store("benflix.reader", settings);
      theme();
      relayout();
    }

    host.addEventListener("click", (e) => {
      const b = e.target.closest("button");
      if (!b) return;
      const a = b.dataset.a;
      if (a === "close") return close();
      if (a === "toc") return panel.classList.contains("hidden") || !panel.querySelector("[data-go]") ? (panel.classList.remove("hidden"), toc()) : panel.classList.add("hidden");
      if (a === "aa") return panel.classList.contains("hidden") || !panel.querySelector("[data-size]") ? (panel.classList.remove("hidden"), aa()) : panel.classList.add("hidden");
      if (a === "send") return api("POST", "api/books/send/" + id).then((j) => toast(j.words || "Sent.")).catch((err) => toast(err.message));
      if (b.dataset.go != null) {
        panel.classList.add("hidden");
        showBar(false);
        pendingFrag = b.dataset.frag || null;
        return turnTo(+b.dataset.go, 0);
      }
      if (b.dataset.size) { setting("size", Math.max(0, Math.min(SIZES.length - 1, settings.size + +b.dataset.size))); return aa(); }
      if (b.dataset.theme) { setting("theme", b.dataset.theme); return aa(); }
      if (b.dataset.font) { setting("font", b.dataset.font); return aa(); }
      if (b.dataset.pick) {
        save();
        if (opts.onPick) return opts.onPick(b.dataset.pick);
        if (app && app.openTitle) return app.openTitle(b.dataset.pick);
      }
    });
    $(".rd-l").onclick = () => prev();
    $(".rd-r").onclick = () => next();
    $(".rd-m").onclick = () => { if (!panel.classList.contains("hidden")) panel.classList.add("hidden"); else showBar(bar.classList.contains("hidden")); };

    let toastT;
    function toast(text) {
      let t = host.querySelector(".rd-toast");
      if (!t) { t = document.createElement("div"); t.className = "rd-toast"; host.appendChild(t); }
      t.textContent = text;
      t.classList.remove("hidden");
      clearTimeout(toastT);
      toastT = setTimeout(() => t.classList.add("hidden"), 3500);
    }

    // ---------- keys (TV remote, keyboard) ----------
    function onKey(e) {
      const k = e.key;
      const inPanel = !panel.classList.contains("hidden");
      const barOn = !bar.classList.contains("hidden");
      if (k === "Escape" || k === "GoBack" || k === "BrowserBack") { e.preventDefault(); back() || close(); return; }
      if (inPanel || barOn) {
        if (k === "ArrowDown" && barOn && !inPanel) { e.preventDefault(); showBar(false); return; }
        if (k === "ArrowLeft" || k === "ArrowRight" || k === "ArrowUp" || k === "ArrowDown") {
          e.preventDefault();
          moveFocus(k, inPanel ? panel : bar);
        }
        return;
      }
      if (k === "ArrowRight" || k === "PageDown" || k === " " || k === "MediaFastForward") { e.preventDefault(); opts.rtl ? prev() : next(); }
      else if (k === "ArrowLeft" || k === "PageUp" || k === "MediaRewind") { e.preventDefault(); opts.rtl ? next() : prev(); }
      else if (k === "ArrowUp" || k === "Enter" || k === "a" || k === "A") { e.preventDefault(); showBar(true); }
      else if (k === "ArrowDown") { e.preventDefault(); }
    }

    function moveFocus(k, root) {
      const list = Array.from(root.querySelectorAll("button"));
      if (!list.length) return;
      const cur = document.activeElement;
      const i = list.indexOf(cur);
      if (i < 0) return list[0].focus();
      const r = cur.getBoundingClientRect();
      let best = null, bestD = 1e9;
      for (const b of list) {
        if (b === cur) continue;
        const q = b.getBoundingClientRect();
        const dx = q.left + q.width / 2 - (r.left + r.width / 2), dy = q.top + q.height / 2 - (r.top + r.height / 2);
        const ok = k === "ArrowRight" ? dx > 4 : k === "ArrowLeft" ? dx < -4 : k === "ArrowDown" ? dy > 4 : dy < -4;
        if (!ok) continue;
        const d = (k === "ArrowLeft" || k === "ArrowRight") ? Math.abs(dx) + Math.abs(dy) * 3 : Math.abs(dy) + Math.abs(dx) * 0.5;
        if (d < bestD) { bestD = d; best = b; }
      }
      if (best) { best.focus(); best.scrollIntoView({ block: "nearest" }); }
      else if (k === "ArrowDown" && root === bar) showBar(false);
    }

    host.tabIndex = 0;
    host.addEventListener("keydown", onKey);

    function swipes(target) {
      let x0 = null, y0 = 0, t0 = 0;
      target.addEventListener("touchstart", (e) => { const t = e.touches[0]; x0 = t.clientX; y0 = t.clientY; t0 = Date.now(); }, { passive: true });
      target.addEventListener("touchend", (e) => {
        if (x0 == null) return;
        const t = e.changedTouches[0];
        const dx = t.clientX - x0, dy = t.clientY - y0;
        x0 = null;
        if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy) && Date.now() - t0 < 800) dx < 0 ? next() : prev();
      }, { passive: true });
    }
    swipes(host);

    /** Android Back: closes a panel or the bar first. Returns true when it did something. */
    function back() {
      if (!panel.classList.contains("hidden") && !ended) { panel.classList.add("hidden"); host.focus(); return true; }
      if (!bar.classList.contains("hidden")) { showBar(false); return true; }
      return false;
    }

    function close() {
      if (gone) return;
      gone = true;
      clearInterval(idleTimer);
      window.removeEventListener("resize", onResize);
      save().finally(() => {
        if (opts.onClose) opts.onClose();
        else if (app && app.close) app.close();
      });
    }

    // The TV's screensaver comes on after 15 minutes without a page turn
    idleTimer = setInterval(() => {
      if (Date.now() - lastTurn > 15 * 60 * 1000 && app && app.idle) { try { app.idle(); } catch (e) {} lastTurn = Date.now(); }
      // Keep the place fresh every 30 seconds while reading
      if (Date.now() - readSince > 30000 && Date.now() - lastTurn < 5 * 60 * 1000) save();
    }, 30000);

    let rz;
    function onResize() { clearTimeout(rz); rz = setTimeout(() => relayout(), 150); }
    window.addEventListener("resize", onResize);
    document.addEventListener("visibilitychange", () => { if (document.hidden) save(); });

    theme();
    msg("Opening…");
    Promise.all([api("GET", "api/books/epub/" + id), api("GET", "api/books/title/" + id).catch(() => ({}))])
      .then(async ([b, t]) => {
        book = b;
        sizes = b.spine.map((s) => Math.max(1, s.size || 1));
        total = sizes.reduce((a, c) => a + c, 0) || 1;
        const pl = (t && t.place) || {};
        if (!b.spine.length) return msg("This book has no pages", "The file may be damaged. Try asking for it again.");
        msg("");
        pendingCpos = typeof pl.cpos === "number" ? pl.cpos : 0;
        const startAt = typeof opts.chapter === "number" ? opts.chapter : (pl.chapter || 0);
        if (typeof opts.chapter === "number") pendingCpos = 0;
        await loadChapter(Math.min(startAt, b.spine.length - 1), pendingCpos);
        host.focus();
        if (opts.onReady) opts.onReady();
      })
      .catch((e) => msg("Couldn't open this book", e.message));

    return { close, back, next, prev, save };
  }

  window.BookReader = { open };
})();
