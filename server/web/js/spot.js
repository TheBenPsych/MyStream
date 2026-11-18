/* Benflix web: the new Home's spotlight (navigation redesign, approved cf-q-land A, same as the TV and phone app).
   Six picks, one at a time over the whole top of Home: a big still picture that zooms in slowly, why it's picked
   ("Because you watched Dune"), the name, a short line, Play and More info, and dots. It slides to the next pick
   every 10 seconds until you press or click anything. Play within a second of a slide plays the pick you were
   looking at. Right arrow past More info goes to the next pick; on phones you swipe.
   Picks: Ben's picks on Kids; otherwise "Because you watched ..." movies and shows mixed with New on Benflix.
   They're kept for a few hours so Home opens straight away, then made fresh.
   Everything sits in one closure: the web scripts share one scope, so nothing here is a top-level name. */

(() => {
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const PICKS = 6;
  const SLIDE_MS = 10000;
  const FRESH_MS = 3 * 3600000;
  const key = () => "benflix.homepicks." + (B.me ? B.me.id : "");
  const saved = () => { try { return JSON.parse(localStorage.getItem(key()) || "null"); } catch (e) { return null; } };
  const save = (v) => { try { localStorage.setItem(key(), JSON.stringify(v)); } catch (e) {} };

  const lineOf = (it) => [
    it.ProductionYear,
    it.OfficialRating,
    it.Type === "Series" ? (it.ChildCount ? `${it.ChildCount} season${it.ChildCount === 1 ? "" : "s"}` : "") : it.RunTimeTicks ? B.len(B.minutes(it.RunTimeTicks)) : "",
    (it.Genres || [])[0],
  ].filter(Boolean).join(" · ");
  const toPick = (it, why) => ({
    id: it.Id, type: it.Type, name: it.Name, why, line: lineOf(it),
    about: String(it.Overview || "").slice(0, 260),
    pic: B.backdrop(it, 1600), small: B.backdrop(it, 96),
  });

  // Make six picks (Kids: Ben's picks for the Kids home)
  const build = () => buildPlain();
  const buildPlain = async () => {
    const u = B.uid(), F = B.FIELDS;
    const get = (p) => B.get(p).then((r) => r.Items || r || []).catch(() => []);
    const good = (it) => it && (it.BackdropImageTags || []).length && !((it.UserData || {}).Played);
    if (B.me.kids) {
      const list = (await get(`/Items?userId=${u}&Tags=benflix-kids-pick&IncludeItemTypes=Movie,Series&Recursive=true&ImageTypes=Backdrop&SortBy=Random&Limit=12&Fields=${F}`)).filter((it) => (it.BackdropImageTags || []).length);
      const more = list.length >= PICKS ? [] : await get(`/Items?userId=${u}&IncludeItemTypes=Movie,Series&Recursive=true&ImageTypes=Backdrop&SortBy=Random&Limit=12&Fields=${F}`);
      return list.concat(more).filter((it, i, a) => a.findIndex((x) => x.Id === it.Id) === i).slice(0, PICKS).map((it) => toPick(it, "Picked for you"));
    }
    const [lastMovie, lastShow, newest] = await Promise.all([
      get(`/Items?userId=${u}&IncludeItemTypes=Movie&Recursive=true&Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=1`),
      get(`/Items?userId=${u}&IncludeItemTypes=Series&Recursive=true&SortBy=DatePlayed&SortOrder=Descending&Limit=1&Filters=IsResumable,IsPlayed`).then((l) => (l.length ? l : get(`/Shows/NextUp?userId=${u}&Limit=1`).then((n) => (n[0] ? [{ Id: n[0].SeriesId, Name: n[0].SeriesName }] : [])))),
      get(`/Items?userId=${u}&IncludeItemTypes=Movie,Series&Recursive=true&Filters=IsUnplayed&ImageTypes=Backdrop&SortBy=DateCreated&SortOrder=Descending&Limit=12&Fields=${F}`),
    ]);
    const like = async (it) => (it ? (await get(`/Items/${it.Id}/Similar?userId=${u}&Limit=12&Fields=${F}`)).filter(good).map((x) => toPick(x, `Because you watched ${it.Name}`)) : []);
    const [m, s] = await Promise.all([like(lastMovie[0]), like(lastShow[0])]);
    const fresh = newest.filter(good).map((x) => toPick(x, "New on Benflix"));
    // Movies and shows take turns, with something new every third pick
    const out = [], seen = new Set();
    const take = (list) => { while (list.length) { const p = list.shift(); if (!seen.has(p.id)) { seen.add(p.id); out.push(p); return; } } };
    for (let i = 0; out.length < PICKS && (m.length || s.length || fresh.length) && i < 30; i++) take(i % 3 === 2 ? fresh : i % 3 === 0 ? (m.length ? m : s) : (s.length ? s : m));
    return out.slice(0, PICKS);
  };

  // ---------- drawing ----------
  B.spotHtml = () => `<section class="spot" id="spot" aria-roledescription="carousel" aria-label="Picks for you">
      <div class="sp-pics" id="spPics"></div><div class="sp-shade"></div>
      <div class="sp-info" id="spInfo"></div>
    </section>`;

  let picks = [], at = 0, timer = null, stopped = false, lastSlide = 0, prevAt = 0, nav0 = 0;
  const show = (n, quiet) => {
    if (!picks.length) return;
    prevAt = at;
    at = (n + picks.length) % picks.length;
    if (!quiet) lastSlide = Date.now();
    const p = picks[at];
    const box = $("#spPics");
    if (box) {
      const layer = document.createElement("div");
      layer.className = "sp-pic";
      layer.style.backgroundImage = `url('${p.pic}'), url('${p.small}')`;
      box.appendChild(layer);
      requestAnimationFrame(() => layer.classList.add("on"));
      // The old picture fades out under the new one
      const old = $$(".sp-pic", box);
      old.slice(0, -1).forEach((x) => { x.classList.add("off"); setTimeout(() => x.remove(), 900); });
    }
    const info = $("#spInfo");
    if (!info) return;
    const focusedPlay = document.activeElement && document.activeElement.matches && document.activeElement.matches("#spPlay");
    const focusedInfo = document.activeElement && document.activeElement.matches && document.activeElement.matches("#spMore");
    info.innerHTML = `<small class="sp-why">${esc(p.why)}</small>
      <h2 class="sp-name">${esc(p.name)}</h2>
      <div class="sp-line">${esc(p.line)}</div>
      ${p.about ? `<p class="sp-about">${esc(p.about)}</p>` : ""}
      <div class="sp-btns"><button type="button" class="btn white" id="spPlay">${B.I("play")} Play</button><a class="btn" id="spMore" href="${esc(p.type === "Series" ? `#/show/${p.id}` : `#/movie/${p.id}`)}">${B.I("info")} More info</a></div>
      <div class="sp-dots" role="tablist">${picks.map((x, i) => `<button type="button" class="${i === at ? "on" : ""}" data-dot="${i}" aria-label="${esc(x.name)}"></button>`).join("")}</div>`;
    if (focusedPlay) $("#spPlay").focus({ preventScroll: true });
    if (focusedInfo) $("#spMore").focus({ preventScroll: true });
  };
  const stop = () => { stopped = true; if (timer) clearInterval(timer); timer = null; };
  const play = (p) => {
    if (p.type !== "Series") return B.go(`#/play/${p.id}?resume=1`);
    B.seriesNextEp(p.id)
      .then((ep) => B.go(ep ? `#/play/${ep.Id}?resume=1` : `#/show/${p.id}`))
      .catch(() => B.go(`#/show/${p.id}`));
  };

  B.loadSpot = async (nav) => {
    nav0 = nav;
    stopped = false; at = 0;
    if (timer) clearInterval(timer);
    const box = $("#spot");
    if (!box) return;
    const old = saved();
    picks = old && old.list && old.list.length ? old.list : [];
    if (picks.length) show(0, true);
    if (!old || Date.now() - old.at > FRESH_MS || !picks.length) {
      const fresh = await build().catch(() => []);
      if (!B.alive(nav)) return;
      if (fresh.length) {
        save({ at: Date.now(), list: fresh });
        if (!picks.length) { picks = fresh; show(0, true); }
      }
    }
    if (!picks.length) { box.classList.add("empty"); return; }
    // Slides every 10 seconds until anything is pressed (not when the computer asks for less motion)
    if (!matchMedia("(prefers-reduced-motion: reduce)").matches) {
      timer = setInterval(() => {
        if (!B.alive(nav0) || !$("#spot")) { clearInterval(timer); timer = null; return; }
        if (!stopped && !document.hidden) show(at + 1);
      }, SLIDE_MS);
    }
    box.onclick = (e) => {
      const dot = e.target.closest("[data-dot]");
      if (dot) { stop(); show(+dot.dataset.dot); return; }
      if (e.target.closest("#spPlay")) {
        // Play just after a slide: you meant the one you were looking at
        const p = Date.now() - lastSlide < 1000 ? picks[prevAt] : picks[at];
        stop();
        play(p);
        return;
      }
      if (e.target.closest("#spMore")) stop();
    };
    box.addEventListener("keydown", (e) => {
      stop();
      if (e.key === "ArrowRight" && document.activeElement && document.activeElement.id === "spMore") { e.preventDefault(); show(at + 1); $("#spPlay").focus({ preventScroll: true }); }
      if (e.key === "ArrowLeft" && document.activeElement && document.activeElement.id === "spPlay" && at > 0) { e.preventDefault(); show(at - 1); $("#spPlay").focus({ preventScroll: true }); }
    });
    // Phones: swipe the picture
    let x0 = null;
    box.addEventListener("touchstart", (e) => { x0 = e.touches[0].clientX; }, { passive: true });
    box.addEventListener("touchend", (e) => {
      if (x0 == null) return;
      const dx = e.changedTouches[0].clientX - x0; x0 = null;
      if (Math.abs(dx) > 50) { stop(); show(at + (dx < 0 ? 1 : -1)); }
    });
  };
  // Anything pressed anywhere on Home stops the sliding
  document.addEventListener("keydown", () => { if ($("#spot")) stop(); });
  document.addEventListener("pointerdown", (e) => { if ($("#spot") && !(e.target.closest && e.target.closest("[data-dot]"))) stop(); });
  B.spotFocus = () => { const p = $("#spPlay"); if (p) p.focus({ preventScroll: true }); return !!p; };
})();
