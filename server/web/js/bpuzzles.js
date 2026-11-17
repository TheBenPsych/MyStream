/* Benflix web: the movie puzzles, made from what's on Tower (the same games and scoring as the app,
   ui/games/puzzles/*.kt, GuessTheMovie.kt, DailyRiddle.kt and HigherLower.kt).
   Kids profiles only get titles rated for kids (the helper filters every list it passes on). */
"use strict";
(() => {
  const shuffle = (a) => { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const pickN = (a, n) => shuffle(a).slice(0, n);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const label = (t) => t.name + (t.year ? ` (${t.year})` : "");

  // ---------- the titles every puzzle picks from (kept 30 minutes) ----------
  const idsIn = async (re) => {
    const libs = (await B.views()).filter((l) => re.test(l.Name || ""));
    const lists = await Promise.all(libs.map((l) => B.get(`/Items?userId=${B.uid()}&ParentId=${l.Id}&IncludeItemTypes=Movie,Series&Recursive=true&EnableImages=false&EnableUserData=false&Limit=3000`).catch(() => ({ Items: [] }))));
    return new Set(lists.flatMap((r) => (r.Items || []).map((x) => x.Id)));
  };
  const toTitle = (it, anime, stand) => {
    if (!it.Name) return null;
    let ch = (it.Chapters || []).map((c, i) => ({ c, i })).filter((x) => x.c.ImageTag);
    if (ch.length > 4) ch = ch.slice(1, -1);
    const chapters = shuffle(ch).slice(0, 4).map(({ c, i }) => B.img(it.Id, "Chapter", { w: 1280, tag: c.ImageTag, index: i }));
    const backs = (it.BackdropImageTags || []).map((tag, i) => ({ tag, i })).reverse().slice(0, 3).map(({ tag, i }) => B.img(it.Id, "Backdrop", { w: 1280, tag, index: i }));
    return {
      id: it.Id, name: it.Name, year: it.ProductionYear || null, series: it.Type === "Series", anime, stand,
      poster: it.ImageTags && it.ImageTags.Primary ? B.img(it.Id, "Primary", { h: 600, tag: it.ImageTags.Primary }) : "",
      backdrop: (it.BackdropImageTags || [])[0] ? { tag: it.BackdropImageTags[0] } : null,
      stills: Array.from(new Set(chapters.concat(backs))),
      overview: it.Overview || "", genres: it.Genres || [], studios: (it.Studios || []).map((s) => s.Name).filter(Boolean),
      tagline: (it.Taglines || [])[0] || "",
      actors: (it.People || []).filter((p) => p.Type === "Actor" && p.Name).slice(0, 12).map((p) => ({ id: p.Id, name: p.Name, photo: p.PrimaryImageTag ? B.img(p.Id, "Primary", { h: 900, tag: p.PrimaryImageTag }) : "" })),
      runtime: it.RunTimeTicks ? Math.round(it.RunTimeTicks / 600000000) : null,
      rating: it.CommunityRating ? Math.round(it.CommunityRating * 10) : 0,
    };
  };
  const all = () => B.cached("bgpool", 1800000, async () => {
    const [res, anime, stand] = await Promise.all([
      B.get(`/Items?userId=${B.uid()}&IncludeItemTypes=Movie,Series&Recursive=true&ImageTypes=Primary&SortBy=Random&Fields=Overview,People,Genres,Studios,Chapters,Taglines&EnableUserData=false&Limit=500`),
      idsIn(/anime/i).catch(() => new Set()),
      idsIn(/stand/i).catch(() => new Set()),
    ]);
    const seen = new Set();
    return (res.Items || []).map((it) => toTitle(it, anime.has(it.Id), stand.has(it.Id))).filter((t) => t && !seen.has(t.name.toLowerCase()) && seen.add(t.name.toLowerCase()));
  });
  const titles = async () => {
    const mode = B.me.kids ? "MIXED" : B.bgAnime();
    return (await all()).filter((t) => (mode === "ONLY" ? t.anime : mode === "NONE" ? !t.anime : true));
  };

  // ---------- shared screen pieces ----------
  const screen = (ctx, html) => { ctx.stage.innerHTML = `<div class="bgq">${html}</div>`; return $(".bgq", ctx.stage); };
  const notEnough = (ctx, text) => {
    screen(ctx, `<div class="bg-msg"><h2>Not enough to play yet</h2><p class="dim">${esc(text)}</p><button class="btn white" id="bgBack">Back</button></div>`);
    $("#bgBack").onclick = ctx.quit;
  };
  const failed = (ctx, e) => {
    console.warn(e);
    screen(ctx, `<div class="bg-msg"><h2>Couldn't load from the server</h2><p class="dim">Try again in a moment.</p><button class="btn white" id="bgRetry">Try again</button> <button class="btn" id="bgBack">Back</button></div>`);
    $("#bgRetry").onclick = ctx.again; $("#bgBack").onclick = ctx.quit;
  };
  const head = (title, line) => `<div class="bgq-head"><b>${esc(title)}</b><span id="bgLine">${esc(line)}</span></div>`;
  /** Choice buttons; number keys pick them too. Returns a function that turns them off. */
  const choices = (box, names, onPick) => {
    box.innerHTML = names.map((n, i) => `<button class="btn bg-choice" data-i="${i}"><span class="n">${i + 1}</span>${esc(n)}</button>`).join("");
    box.onclick = (e) => { const b = e.target.closest(".bg-choice"); if (b && !b.disabled) onPick(names[+b.dataset.i], b); };
    const first = $(".bg-choice", box); if (first) setTimeout(() => first.focus({ preventScroll: true }), 30);
  };
  let keyFn = null;
  document.addEventListener("keydown", (e) => {
    if (!keyFn || !/^#\/playb\//.test(location.hash)) return;
    if (/^[1-9]$/.test(e.key)) { const b = $$(".bgq .bg-choice")[+e.key - 1]; if (b && !b.disabled) { e.preventDefault(); b.click(); } }
    else if (/^Arrow/.test(e.key)) {
      const list = $$(".bgq .bg-choice:not([disabled]), .bgq .bg-slot, .bgq .bg-cell:not([disabled])");
      const i = list.indexOf(document.activeElement);
      if (i < 0) { if (list[0]) { e.preventDefault(); list[0].focus(); } return; }
      const cols = Math.max(1, Math.round(list[0].parentElement.clientWidth / (list[0].offsetWidth || 1)));
      const step = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -cols, ArrowDown: cols }[e.key];
      const n = list[i + step]; if (n) { e.preventDefault(); n.focus(); }
    }
  });
  const timers = (ctx) => { const list = []; B.onLeave(() => list.forEach(clearInterval)); return (fn, ms) => { const t = setInterval(() => { if (!ctx.alive()) clearInterval(t); else fn(); }, ms); list.push(t); return t; }; };
  const otherNames = (pool, answer, n, keep) => pickN(pool.filter((t) => t.name !== answer.name && (!keep || keep(t))), n).map((t) => t.name);

  const PUZ = {};

  // Scene by scene: 400 / 300 / 200 / 100 points for each scene shown; out of scenes ends the run
  PUZ.SCENES = async (ctx) => {
    const pool = (await titles()).filter((t) => t.stills.length >= 2);
    if (pool.length < 8) return notEnough(ctx, "Scene by scene needs at least 8 titles with pictures from inside them on Tower.");
    let deck = shuffle(pool), points = 0, round = 0;
    const next = () => {
      if (!deck.length) deck = shuffle(pool);
      const a = deck.shift(); round++;
      const scenes = a.stills.slice(0, 4);
      let shown = 0;
      const names = shuffle(otherNames(pool, a, 5).concat(a.name));
      const q = screen(ctx, `${head("Scene by scene", `Round ${round} · ${points} points · Your best ${ctx.best()}`)}<div class="bgq-pic wide"><img id="bgPic" alt=""></div><div class="bgq-say" id="bgSay">Scene 1 of ${scenes.length}: worth 400</div><div class="bgq-choices" id="bgCh"></div>`);
      const show = () => { $("#bgPic", q).src = scenes[shown]; $("#bgSay", q).textContent = `Scene ${shown + 1} of ${scenes.length}: worth ${[400, 300, 200, 100][shown]}`; };
      show();
      choices($("#bgCh", q), names, (n, b) => {
        if (n === a.name) {
          const won = [400, 300, 200, 100][shown]; points += won; B.bgSound("point");
          b.classList.add("right"); $("#bgSay", q).textContent = `Right! ${label(a)} +${won}`;
          $$(".bg-choice", q).forEach((x) => { x.disabled = true; });
          setTimeout(() => ctx.alive() && next(), 1400);
        } else {
          b.disabled = true; b.classList.add("wrong"); B.bgSound("miss");
          shown++;
          if (shown >= scenes.length) { B.bgOver(ctx, "Out of scenes!", `It was ${label(a)}.\nYou got ${round - 1} right for ${points} points.`, points); return; }
          show();
        }
      });
    };
    keyFn = true; next();
  };

  // Poster puzzle: 16 tiles flip away; 100 + 25 for each tile still covering it; one wrong guess ends the run
  PUZ.POSTER = async (ctx) => {
    const pool = (await titles()).filter((t) => t.poster);
    if (pool.length < 8) return notEnough(ctx, "Poster puzzle needs at least 8 titles with posters on Tower.");
    const every = timers(ctx);
    let deck = shuffle(pool), points = 0, round = 0, tick = null;
    const next = () => {
      if (!deck.length) deck = shuffle(pool);
      const a = deck.shift(); round++;
      const names = shuffle(otherNames(pool, a, 5).concat(a.name));
      const q = screen(ctx, `${head("Poster puzzle", `Round ${round} · ${points} points · Your best ${ctx.best()}`)}<div class="bgq-pic tall"><img src="${a.poster}" alt=""><div class="bg-tiles">${"<i></i>".repeat(16)}</div></div><div class="bgq-say" id="bgSay">Guess now for 500 points</div><div class="bgq-choices" id="bgCh"></div>`);
      const tiles = $$(".bg-tiles i", q), order = shuffle(tiles.map((_, i) => i));
      let covered = 16, done = false, waited = 0;
      const step = Math.max(450, 900 - round * 30);
      const worth = () => 100 + covered * 25;
      tick = every(() => {
        if (done) return;
        if (covered > 0) { tiles[order[16 - covered]].classList.add("off"); covered--; $("#bgSay", q).textContent = `Guess now for ${worth()} points`; B.bgSound("flip"); }
        else { waited += step; if (waited >= 4000) { done = true; clearInterval(tick); B.bgOver(ctx, "Run over", `Too slow! The whole poster was showing. It was ${label(a)}.\nYou got ${round - 1} right for ${points} points.`, points); } }
      }, step);
      choices($("#bgCh", q), names, (n, b) => {
        if (done) return;
        done = true; clearInterval(tick);
        $$(".bg-choice", q).forEach((x) => { x.disabled = true; });
        tiles.forEach((t) => t.classList.add("off"));
        if (n === a.name) {
          const won = worth(); points += won; B.bgSound("point"); b.classList.add("right");
          $("#bgSay", q).textContent = `Right! ${label(a)} +${won}`;
          setTimeout(() => ctx.alive() && next(), 1400);
        } else {
          b.classList.add("wrong"); B.bgSound("miss");
          B.bgOver(ctx, "Run over", `Not ${n}. It was ${label(a)}.\nYou got ${round - 1} right for ${points} points.`, points);
        }
      });
    };
    keyFn = true; next();
  };

  // Timeline: slot each poster into a growing line of years; one wrong spot ends the run (score = line length - 1)
  PUZ.TIMELINE = async (ctx) => {
    const pool = (await titles()).filter((t) => t.year && t.poster);
    if (pool.length < 8) return notEnough(ctx, "Timeline needs at least 8 titles with a year on Tower.");
    const deck = shuffle(pool);
    const line = [deck.shift()];
    const card = (t, year) => `<div class="bg-tl"><img src="${t.poster}" alt=""><b>${esc(t.name)}</b>${year ? `<span>${t.year}</span>` : ""}</div>`;
    const draw = (cur, msg) => {
      const q = screen(ctx, `${head("Timeline", `Line of ${line.length} · Your best ${ctx.best()}`)}<div class="bg-tl-new">${card(cur, false)}<div><div class="bgq-say">${esc(msg || "Where does it go? Pick a Here spot on the line.")}</div></div></div><div class="bg-line" id="bgLn">${line.map((t, i) => `<button class="bg-slot" data-s="${i}">Here</button>${card(t, true)}`).join("")}<button class="bg-slot" data-s="${line.length}">Here</button></div>`);
      const slots = $$(".bg-slot", q);
      setTimeout(() => { const mid = slots[Math.floor(slots.length / 2)]; if (mid) { mid.focus({ preventScroll: true }); mid.scrollIntoView({ inline: "center", block: "nearest" }); } }, 30);
      $("#bgLn", q).onclick = (e) => {
        const b = e.target.closest(".bg-slot"); if (!b) return;
        const s = +b.dataset.s;
        const before = s > 0 ? line[s - 1].year : -1e9, after = s < line.length ? line[s].year : 1e9;
        if (cur.year >= before && cur.year <= after) {
          line.splice(s, 0, cur); B.bgSound("point");
          if (!deck.length) { B.bgOver(ctx, "You placed every title!", `Your line has ${line.length} titles.`, line.length - 1); return; }
          draw(deck.shift(), `Right! ${label(cur)}`);
        } else {
          B.bgSound("miss");
          const r = line.findIndex((t) => t.year > cur.year), ri = r < 0 ? line.length : r;
          const A = line[ri - 1], Bt = line[ri];
          const where = !A ? `before ${label(Bt)}` : !Bt ? `after ${label(A)}` : `between ${label(A)} and ${label(Bt)}`;
          B.bgOver(ctx, "Run over", `${label(cur)} goes ${where}.\nYour line had ${line.length} titles.`, line.length - 1);
        }
      };
    };
    keyFn = true; draw(deck.shift());
  };

  // Who's that actor? A photo zooms out over 15 s; 100 + up to 400 for speed; misses add hints; 3 misses ends it
  PUZ.ACTOR = async (ctx) => {
    const byName = {};
    (await titles()).forEach((t) => t.actors.forEach((p) => { if (!p.photo) return; const a = byName[p.name] || (byName[p.name] = { p, titles: [] }); a.titles.push(t); }));
    const actors = Object.values(byName);
    if (actors.length < 8) return notEnough(ctx, "Who's that actor? needs at least 8 actors with photos on Tower.");
    const every = timers(ctx);
    let deck = [], points = 0, round = 0;
    const next = () => {
      if (!deck.length) { deck = shuffle(actors.filter((a) => a.titles.length >= 2)); if (!deck.length) deck = shuffle(actors); }
      const a = deck.shift(); round++;
      const names = shuffle(pickN(actors.filter((x) => x.p.name !== a.p.name), 5).map((x) => x.p.name).concat(a.p.name));
      const q = screen(ctx, `${head("Who's that actor?", `Round ${round} · ${points} points · Your best ${ctx.best()}`)}<div class="bgq-pic tall zoom"><img id="bgPic" src="${a.p.photo}" alt=""></div><div class="bgq-say" id="bgSay"></div><div class="bg-hints" id="bgHints"></div><div class="bgq-choices" id="bgCh"></div>`);
      let reveal = 0, misses = 0, out = 0, done = false;
      const hints = [];
      const worth = () => Math.round(100 + 400 * (1 - reveal));
      const paint = () => { $("#bgPic", q).style.transform = `scale(${1 + 5 * (1 - reveal) ** 2})`; $("#bgSay", q).textContent = `Who is this? Guess now for ${worth()} points`; $("#bgHints", q).innerHTML = hints.map((h) => `<span>${esc(h)}</span>`).join(""); };
      paint();
      const t = every(() => {
        if (done) return;
        if (reveal >= 1) { out += 100; if (out >= 5000) { done = true; clearInterval(t); B.bgOver(ctx, "Run over", `Too slow! It was ${a.p.name}.\nYou got ${round - 1} right for ${points} points.`, points); } }
        else { reveal = Math.min(1, reveal + 100 / 15000); paint(); }
      }, 100);
      choices($("#bgCh", q), names, (n, b) => {
        if (done) return;
        if (n === a.p.name) {
          done = true; clearInterval(t);
          const won = worth(); points += won; reveal = 1; paint(); B.bgSound("point"); b.classList.add("right");
          $("#bgSay", q).textContent = `Right! ${a.p.name} +${won}`;
          $$(".bg-choice", q).forEach((x) => { x.disabled = true; });
          setTimeout(() => ctx.alive() && next(), 1400);
        } else if (misses + 1 >= 3) {
          done = true; clearInterval(t); B.bgSound("miss");
          B.bgOver(ctx, "Run over", `Not ${n}. It was ${a.p.name}.\nYou got ${round - 1} right for ${points} points.`, points);
        } else {
          misses++; b.disabled = true; b.classList.add("wrong"); B.bgSound("miss");
          const tl = a.titles.map(label);
          hints.push(tl[hints.length] ? `Was in ${tl[hints.length]}` : `Has ${tl.length} titles on Tower`);
          reveal = Math.min(1, reveal + 0.25); paint();
        }
      });
    };
    keyFn = true; next();
  };

  // Guess the movie: 10 rounds; a blurry picture sharpens every 2 s (or a plot or cast clue); up to 1,000 for speed
  PUZ.GUESS = async (ctx) => {
    const pool = (await titles()).filter((t) => !t.series && t.backdrop);
    if (pool.length < 4) return notEnough(ctx, "Guess the movie needs at least 4 movies with pictures on Tower.");
    const every = timers(ctx);
    const rounds = pickN(pool, 10);
    let i = 0, score = 0, correct = 0;
    const BLUR = [60, 35, 20, 10, 4, 0];
    const play = () => {
      if (i >= rounds.length) { B.bgOver(ctx, "All done!", `You got ${correct} of ${rounds.length} right for ${score.toLocaleString()} points.`, score); return; }
      const m = rounds[i++];
      const plot = m.overview.length > 30 ? m.overview.split(/(?<=[.!?])\s+/)[0].replace(new RegExp(m.name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "ig"), "▇▇▇▇") : "";
      const cast = m.actors.slice(0, 3).map((a) => a.name);
      const kind = i % 3 === 2 && plot ? "plot" : i % 3 === 0 && i > 0 && cast.length >= 2 ? "cast" : "pic";
      const names = shuffle(otherNames(pool, m, 3).concat(m.name));
      const base = B.img(m.id, "Backdrop", { w: 1280, tag: m.backdrop.tag, index: 0 });
      const q = screen(ctx, `${head("Guess the movie", `Round ${i} of ${rounds.length} · ${score.toLocaleString()} points · Your best ${ctx.best().toLocaleString()}`)}${kind === "pic" ? `<div class="bgq-pic wide"><img id="bgPic" src="${base}&blur=${BLUR[0]}" alt=""></div>` : `<div class="bgq-clue">${esc(kind === "plot" ? plot : "Starring " + cast.join(", "))}</div>`}<div class="bgq-say" id="bgSay"></div><div class="bgq-choices four" id="bgCh"></div>`);
      // load every blur step now so each one swaps in at once
      if (kind === "pic") BLUR.slice(1).forEach((b) => { const im = new Image(); im.src = `${base}&blur=${b}`; });
      const started = Date.now();
      let step = 0, done = false;
      const t = every(() => { if (done || kind !== "pic" || step >= BLUR.length - 1) return; step++; $("#bgPic", q).src = `${base}${BLUR[step] ? `&blur=${BLUR[step]}` : ""}`; }, 2000);
      choices($("#bgCh", q), names, (n, b) => {
        if (done) return;
        done = true; clearInterval(t);
        $$(".bg-choice", q).forEach((x) => { x.disabled = true; if (x.textContent.slice(1) === m.name) x.classList.add("right"); });
        if (kind === "pic") $("#bgPic", q).src = base;
        if (n === m.name) {
          const pts = Math.max(100, Math.round(1000 - (Date.now() - started) / 15));
          score += pts; correct++; B.bgSound("point"); $("#bgSay", q).textContent = `Right! +${pts}`;
        } else { b.classList.add("wrong"); B.bgSound("miss"); $("#bgSay", q).textContent = `It was ${label(m)}.`; }
        setTimeout(() => ctx.alive() && play(), 1600);
      });
    };
    keyFn = true; play();
  };

  // Movie riddle: clues get easier each third of a 20 s clock (Kids 30 s); 100 + up to 400 for speed; one miss ends it
  PUZ.RIDDLE = async (ctx) => {
    const BUNDLE = /\b(collection|trilogy|quadrilogy|box ?set|double feature)\b/i;
    const pool = (await titles()).filter((t) => !t.series && !t.stand && !BUNDLE.test(t.name));
    if (pool.length < 5) return notEnough(ctx, "Movie riddle needs at least 5 movies on Tower.");
    const every = timers(ctx);
    // A title's series ("Harry Potter" in "Harry Potter and the..."), so wrong choices aren't from the same one
    const series = (n) => n.split(/[:\-–]/)[0].trim().toLowerCase().replace(/^(the|a|an) /, "").split(" ").slice(0, 2).join(" ");
    const hideIn = (m, text) => {
      const re = (s) => new RegExp(s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "ig");
      let out = m.name.length > 2 ? text.replace(re(m.name), "▇▇▇▇") : text;
      const s = m.name.split(":")[0].trim();
      if (s.length > 3 && s !== m.name) out = out.replace(re(s), "▇▇▇▇");
      return out;
    };
    const clues = (m) => {
      const g = m.genres.slice(0, 2);
      const first = `A ${m.year ? m.year + " " : ""}${g.length ? g.join(" and ").toLowerCase() + " movie" : "movie"}${m.tagline ? `:\n“${hideIn(m, m.tagline)}”` : ""}`;
      const s = (m.overview.split(/(?<=[.!?])\s+/)[0] || "");
      const cast = m.actors.slice(0, 3).map((a) => a.name);
      return [first, s.length > 20 ? `“${hideIn(m, s)}”` : null, cast.length ? "Starring " + cast.join(", ") : null].filter(Boolean);
    };
    const total = ctx.kids ? 30 : 20;
    let deck = shuffle(pool), score = 0, streak = 0;
    const next = () => {
      if (deck.length < 1) deck = shuffle(pool);
      let m = null, cl = [];
      for (let tries = 0; tries < 4 && deck.length; tries++) { const c = deck.shift(); cl = clues(c); if (cl.length >= 2) { m = c; break; } }
      if (!m) return notEnough(ctx, "Movie riddle couldn't find movies with enough details for clues.");
      let others = pickN(pool.filter((t) => t.name !== m.name && series(t.name) !== series(m.name)), 4).map((t) => t.name);
      if (others.length < 4) others = others.concat(pickN(pool.filter((t) => t.name !== m.name && !others.includes(t.name)), 4 - others.length).map((t) => t.name));
      const names = shuffle(others.concat(m.name));
      const q = screen(ctx, `${head("Movie riddle", `${streak} right · ${score.toLocaleString()} points · Your best ${ctx.best().toLocaleString()}`)}<div class="bg-clock"><i id="bgClock"></i></div><div class="bgq-clue" id="bgClues"></div><div class="bgq-say" id="bgSay"></div><div class="bgq-choices" id="bgCh"></div>`);
      const started = Date.now();
      let done = false;
      const paint = () => {
        const left = Math.max(0, total - (Date.now() - started) / 1000);
        const shown = Math.min(cl.length, 1 + Math.floor(((total - left) * cl.length) / total));
        $("#bgClues", q).innerHTML = cl.slice(0, shown).map((c) => `<p>${esc(c).replace(/\n/g, "<br>")}</p>`).join("");
        $("#bgClock", q).style.width = `${(left / total) * 100}%`;
        $("#bgSay", q).textContent = `${Math.ceil(left)} seconds · worth ${Math.round(100 + (400 * left) / total)}`;
        return left;
      };
      paint();
      const t = every(() => { if (done) return; if (paint() <= 0) { done = true; clearInterval(t); B.bgSound("miss"); B.bgOver(ctx, "Time's up!", `It was ${label(m)}.\n${streak} right in a row for ${score.toLocaleString()} points.`, score); } }, 200);
      choices($("#bgCh", q), names, (n, b) => {
        if (done) return;
        done = true; clearInterval(t);
        const left = Math.max(0, total - (Date.now() - started) / 1000);
        $$(".bg-choice", q).forEach((x) => { x.disabled = true; });
        if (n === m.name) {
          const pts = Math.round(100 + (400 * left) / total); score += pts; streak++;
          b.classList.add("right"); B.bgSound("point"); $("#bgSay", q).textContent = `Right! ${label(m)} +${pts}`;
          setTimeout(() => ctx.alive() && next(), 1600);
        } else {
          b.classList.add("wrong"); B.bgSound("miss");
          B.bgOver(ctx, "Run over", `It was ${label(m)}.\n${streak} right in a row for ${score.toLocaleString()} points.`, score);
        }
      });
    };
    keyFn = true; next();
  };

  // Higher or Lower: is the next one rated higher or lower on IMDb? Endless until the first miss (score = streak)
  PUZ.HIGHER_LOWER = async (ctx) => {
    const pool = (await titles()).filter((t) => t.rating > 0 && t.poster);
    if (pool.length < 4) return notEnough(ctx, "Higher or Lower needs at least 4 titles with an IMDb rating on Tower.");
    let deck = shuffle(pool), streak = 0;
    let a = deck.shift();
    const side = (t, showRating) => `<div class="bg-hl"><img src="${t.poster}" alt=""><b>${esc(label(t))}</b><span class="r">${showRating ? (t.rating / 10).toFixed(1) + " IMDb" : "?"}</span></div>`;
    const next = () => {
      if (!deck.length) deck = shuffle(pool.filter((t) => t !== a));
      const b = deck.shift();
      const q = screen(ctx, `${head("Higher or Lower", `Streak ${streak} · Your best ${ctx.best()}`)}<div class="bg-hls">${side(a, true)}<div class="bg-hlq" id="bgSay">Is it higher or lower?</div><div id="bgB" class="fadein">${side(b, false)}</div></div><div class="bgq-choices two" id="bgCh"></div>`);
      choices($("#bgCh", q), ["Higher", "Lower"], (n, btn) => {
        $$(".bg-choice", q).forEach((x) => { x.disabled = true; });
        $("#bgB", q).innerHTML = side(b, true);
        const right = b.rating === a.rating || (n === "Higher" ? b.rating > a.rating : b.rating < a.rating);
        if (right) {
          streak++; btn.classList.add("right"); B.bgSound("point"); $("#bgSay", q).textContent = "Right!";
          a = b; setTimeout(() => ctx.alive() && next(), 1300);
        } else {
          btn.classList.add("wrong"); B.bgSound("miss");
          setTimeout(() => ctx.alive() && B.bgOver(ctx, "Streak over", `${label(b)} is rated ${(b.rating / 10).toFixed(1)}.\nYour streak: ${streak}.`, streak), 900);
        }
      });
    };
    keyFn = true; next();
  };

  // Movie grid: 3 by 3, each square fits its row and column rule; 100 a square, 3 lives, full grid +200 and +50 a life
  PUZ.GRID = async (ctx) => {
    const pool = await titles();
    const rules = [];
    const count = (list) => { const c = {}; list.forEach((x) => { c[x] = (c[x] || 0) + 1; }); return c; };
    const ac = count(pool.flatMap((t) => t.actors.slice(0, 6).map((a) => a.name)));
    Object.keys(ac).filter((n) => ac[n] >= 3).forEach((n) => rules.push({ k: "actor", name: `Has ${n}`, f: (t) => t.actors.some((a) => a.name === n) }));
    const gc = count(pool.flatMap((t) => t.genres));
    Object.keys(gc).filter((g) => gc[g] >= 4).forEach((g) => rules.push({ k: "genre", name: g, f: (t) => t.genres.includes(g) }));
    const dc = count(pool.filter((t) => t.year).map((t) => Math.floor(t.year / 10) * 10));
    Object.keys(dc).filter((d) => dc[d] >= 4).forEach((d) => { const dd = +d; rules.push({ k: "decade", name: `Made in the ${dd >= 2000 ? dd : "’" + String(dd).slice(2)}s`, f: (t) => t.year && Math.floor(t.year / 10) * 10 === dd }); });
    const sc = count(pool.flatMap((t) => t.studios));
    Object.keys(sc).filter((s) => sc[s] >= 4).forEach((s) => rules.push({ k: "studio", name: `By ${s}`, f: (t) => t.studios.includes(s) }));
    rules.push({ k: "score", name: "IMDb 7.5 or more", f: (t) => t.rating >= 75 });
    rules.push({ k: "score", name: "IMDb under 6.0", f: (t) => t.rating >= 1 && t.rating <= 59 });
    rules.push({ k: "type", name: "A TV show", f: (t) => t.series });
    rules.push({ k: "length", name: "Over 2 hours", f: (t) => !t.series && (t.runtime || 0) > 120 });
    if (rules.length < 6) return notEnough(ctx, "Movie grid needs more movies and shows on Tower to make a board.");
    // Try random rules until every square has an answer (each kind of rule used once per side)
    let board = null, fallback = null;
    for (let i = 0; i < 400 && !board; i++) {
      const sh = shuffle(rules), rows = [], cols = [];
      for (const r of sh) { if (rows.length < 3 && !rows.some((x) => x.k === r.k)) rows.push(r); else if (cols.length < 3 && !cols.some((x) => x.k === r.k) && !rows.includes(r)) cols.push(r); if (rows.length === 3 && cols.length === 3) break; }
      if (rows.length < 3 || cols.length < 3) continue;
      const fits = rows.map((r) => cols.map((c) => pool.filter((t) => r.f(t) && c.f(t)).length));
      if (fits.every((row) => row.every((n) => n >= 2))) board = { rows, cols };
      else if (!fallback && fits.every((row) => row.every((n) => n >= 1))) fallback = { rows, cols };
    }
    board = board || fallback;
    if (!board) return notEnough(ctx, "Movie grid couldn't make a board from what's on Tower. Try again later.");
    const filled = {}, used = new Set();
    let lives = 3, points = 0;
    const draw = (msg) => {
      const q = screen(ctx, `${head("Movie grid", `${lives} ${lives === 1 ? "life" : "lives"} · ${points} points · Your best ${ctx.best()}`)}<div class="bgq-say">${esc(msg || "Pick a square, then a title that fits both rules.")}</div>
        <div class="bg-grid"><span></span>${board.cols.map((c) => `<span class="rule">${esc(c.name)}</span>`).join("")}${board.rows.map((r, ri) => `<span class="rule">${esc(r.name)}</span>${board.cols.map((c, ci) => { const f = filled[ri + "," + ci]; return f ? `<button class="bg-cell done" disabled>${f.poster ? `<img src="${f.poster}" alt="">` : ""}<b>${esc(f.name)}</b></button>` : `<button class="bg-cell" data-c="${ri},${ci}">?</button>`; }).join("")}`).join("")}</div>`);
      const first = $(".bg-cell:not([disabled])", q); if (first) setTimeout(() => first.focus({ preventScroll: true }), 30);
      $(".bg-grid", q).onclick = (e) => {
        const cell = e.target.closest(".bg-cell[data-c]"); if (!cell) return;
        const [ri, ci] = cell.dataset.c.split(",").map(Number);
        const r = board.rows[ri], c = board.cols[ci];
        const right = pool.filter((t) => r.f(t) && c.f(t) && !used.has(t.id));
        if (!right.length) { filled[ri + "," + ci] = { name: "No title left" }; return finishIfDone() || draw("Every title for that square was used. It's yours for free."); }
        const answer = right[Math.floor(Math.random() * right.length)];
        const near = shuffle(pool.filter((t) => !used.has(t.id) && t !== answer && !(r.f(t) && c.f(t)) && (r.f(t) || c.f(t)))).slice(0, 3);
        const wrong = near.concat(shuffle(pool.filter((t) => !used.has(t.id) && !r.f(t) && !c.f(t) && !near.includes(t))).slice(0, 5 - near.length));
        const opts = shuffle(wrong.concat(answer));
        const box = document.createElement("div");
        box.className = "bg-pickcell";
        box.innerHTML = `<div class="bgq-say">${esc(r.name)} · ${esc(c.name)}</div><div class="bgq-choices" id="bgCh"></div><button class="btn ghost" id="bgCancel">Pick another square</button>`;
        q.appendChild(box);
        $("#bgCancel", box).onclick = () => draw();
        choices($("#bgCh", box), opts.map((t) => label(t)), (n) => {
          const t = opts.find((x) => label(x) === n);
          used.add(t.id);
          if (r.f(t) && c.f(t)) {
            filled[ri + "," + ci] = t; points += 100; B.bgSound("point");
            if (!finishIfDone()) draw(`Right! ${label(t)} +100`);
          } else {
            lives--; B.bgSound("miss");
            const miss = !r.f(t) ? r.name : c.name;
            if (lives <= 0) { B.bgOver(ctx, "Out of lives", `${t.name} doesn't fit "${miss}".\nYou filled ${Object.keys(filled).length} squares for ${points} points.`, points); return; }
            draw(`${t.name} doesn't fit "${miss}". ${lives} ${lives === 1 ? "life" : "lives"} left.`);
          }
        });
      };
    };
    const finishIfDone = () => {
      if (Object.keys(filled).length < 9) return false;
      const total = points + 200 + lives * 50;
      B.bgOver(ctx, "Full grid!", `100 a square, 200 for the full grid and 50 for each life left.\n${total} points.`, total);
      return true;
    };
    keyFn = true; draw();
  };

  B.bgPuzzle = async (ctx) => {
    const fn = PUZ[ctx.game.key];
    if (!fn) throw new Error("This puzzle isn't in the browser yet.");
    ctx.stage.innerHTML = `<div class="bgq">${B.spinner()}</div>`;
    try { await fn(ctx); } catch (e) { if (ctx.alive()) failed(ctx, e); }
  };
})();
