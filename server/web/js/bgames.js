/* Benflix web: Benflix's own games in the browser (the same ones as the TV and phone app).
   - Movie puzzles made from what's on Tower (js/bpuzzles.js) and the 6 arcade games (js/barcade.js)
   - Each game has its own page (how to play, your best, the top 3) and plays full screen
   - Best scores go into the same Jellyfin profile setting the app uses (benflix_scores), so a best here
     counts on the Leaderboards too. Party games and Ember Road need the TV and phone app. */
"use strict";
(() => {
  const C = (a, b) => [a, b];
  // The same games, names, words and colours as the app (ui/games/MovieGames.kt and GamesViewModel.kt)
  const PUZZLES = [
    { key: "SCENES", title: "Scene by scene", short: "Guess the movie from a still scene. Each miss shows an easier one.", about: "Guess the movie or show from a still picture taken from it. Each wrong guess shows a new, easier scene.", steps: ["Look at the scene.", "Pick the title from 6 names.", "Wrong? An easier scene shows. Out of scenes ends the run."], minutes: "About 3 min", board: "SCENE_BY_SCENE", ranked: "most points in a run", colors: C("#BE123C", "#4C0519"), icon: "film", kids: true },
    { key: "POSTER", title: "Poster puzzle", short: "Tiles flip off a poster. Guess early for more points.", about: "A poster hides under 16 tiles that flip away one by one. Guess as soon as you know it.", steps: ["Watch the tiles flip away.", "Pick the title whenever you know it.", "One wrong guess ends the run."], minutes: "About 3 min", board: "POSTER_PUZZLE", ranked: "most points in a run", colors: C("#A16207", "#422006"), icon: "list", kids: true },
    { key: "GRID", title: "Movie grid", short: "Fill a 3 by 3 board with titles that fit both rules.", about: "Every square needs a movie or show from Tower that fits its row rule and its column rule.", steps: ["Pick a square.", "Pick the title that fits both rules.", "3 lives. A full grid earns a bonus."], minutes: "About 5 min", board: "MOVIE_GRID", ranked: "most points on a grid", colors: C("#4338CA", "#1E1B4B"), icon: "list", kids: false },
    { key: "TIMELINE", title: "Timeline", short: "Put each poster in the right spot by release year.", about: "Posters arrive one at a time. Slot each one into the right place on a growing line of years.", steps: ["Look at the new poster.", "Move along the line and press Here.", "One wrong spot ends the run."], minutes: "About 3 min", board: "TIMELINE", ranked: "longest line", colors: C("#0F766E", "#042F2E"), icon: "swap", kids: true },
    { key: "ACTOR", title: "Who's that actor?", short: "A zoomed-in photo zooms out until you know them.", about: "An actor's photo starts zoomed right in on their eyes and slowly zooms out.", steps: ["Watch the photo zoom out.", "Pick their name from 6.", "Misses add hints. Three misses ends the run."], minutes: "About 3 min", board: "WHOS_THAT_ACTOR", ranked: "most points in a run", colors: C("#9333EA", "#3B0764"), icon: "user", kids: false },
    { key: "GUESS", title: "Guess the movie", short: "A blurry picture gets sharper. Guess it fast!", about: "A blurry picture from a movie gets sharper and sharper. Guess it fast for more points.", steps: ["Watch the picture sharpen.", "Pick the movie.", "Faster answers score more."], minutes: "About 3 min", board: "GUESS_THE_MOVIE", ranked: "most points", colors: C("#0E7490", "#1E3A8A"), icon: "info", kids: false },
    { key: "RIDDLE", title: "Movie riddle", short: "Guess the movie from its clues before time runs out.", about: "Guess the movie from its clues. Clues get easier as the clock runs down, and it keeps going until you miss.", steps: ["Read the clues.", "Pick from 5 movies.", "Fast answers score more. One miss ends the run."], minutes: "About 3 min", board: "MOVIE_RIDDLE_POINTS", ranked: "most points in a run", colors: C("#C2410C", "#6D28D9"), icon: "help", kids: false },
    { key: "HIGHER_LOWER", title: "Higher or Lower", short: "Is the next one rated higher or lower on IMDb?", about: "Two posters. Is the second one rated higher or lower on IMDb? Keep your streak going.", steps: ["Look at the first rating.", "Higher or lower?", "One wrong answer ends the streak."], minutes: "About 2 min", board: "HIGHER_LOWER_STREAK", ranked: "best streak", colors: C("#15803D", "#7F1D1D"), icon: "chart", kids: false },
  ];
  const ARCADE = [
    { key: "POPCORN_POP", title: "Popcorn Pop", short: "Bounce a popcorn kernel up a tower of soda cups and film reels. Grab the tickets!", how: "Arrow keys or A and D to steer. Phone: hold the left or right side.", colors: C("#F59E0B", "#7C2D12") },
    { key: "REEL_RUSH", title: "Reel Rush", short: "Fly the Benflix B through the gaps in the film strips.", how: "Space, Enter or Up to flap. Phone: tap anywhere.", colors: C("#E50914", "#3B0A0A") },
    { key: "SNACK_CATCH", title: "Snack Catch", short: "Catch the falling snacks in your popcorn bucket. Watch out for spilled sodas!", how: "Arrow keys or A and D to move. Phone: hold the left or right side.", colors: C("#16A34A", "#052E16") },
    { key: "USHER_DASH", title: "Usher Dash", short: "Run down the aisle and jump over popcorn spills and lost shoes.", how: "Space, Enter or Up to jump (hold for a higher jump). Phone: tap anywhere.", colors: C("#2563EB", "#172554") },
    { key: "FILM_CAN_STACK", title: "Film Can Stack", short: "Drop sliding film cans to build the tallest tower. Line them up perfectly!", how: "Space or Enter to drop the can. Phone: tap anywhere.", colors: C("#9333EA", "#2E1065") },
    { key: "MATINEE_MATCH", title: "Matinee Match", short: "Flip the cards and find the matching movie pairs before time runs out.", how: "Click a card, or arrow keys and Enter. Phone: tap a card.", colors: C("#DB2777", "#500724") },
  ];
  const PARTY = [
    { key: "QUIZ", title: "Quiz night", short: "10 quick rounds. Everyone answers on their Android phone.", colors: C("#E50914", "#450A0A") },
    { key: "FAKE_PLOT", title: "Fake plot", short: "Write a fake plot, then spot the real one.", colors: C("#EA580C", "#431407") },
    { key: "DRAW", title: "Draw the movie", short: "Draw a secret movie on your Android phone. Others guess.", colors: C("#0284C7", "#082F49") },
    { key: "CLOSEST", title: "Closest wins", short: "Guess the year, length or score. Closest wins.", colors: C("#65A30D", "#1A2E05"), kids: true },
  ];
  const kids = () => !!(B.me && B.me.kids);
  const find = (key) => {
    const p = PUZZLES.find((g) => g.key === key);
    if (p) return Object.assign({ kind: "puzzle", board: p.board }, p);
    const a = ARCADE.find((g) => g.key === key);
    if (a) return Object.assign({ kind: "arcade", board: a.key, ranked: "most points", steps: [a.how], about: a.short, minutes: "A minute or two", icon: "pad" }, a);
    const y = PARTY.find((g) => g.key === key);
    if (y) return Object.assign({ kind: "party", about: y.short, steps: [], icon: "phone" }, y);
    return null;
  };

  // ---------- best scores (this browser + the profile setting the app uses) ----------
  const localKey = () => "benflix.bgbest." + (B.me ? B.me.id : "");
  const localBests = () => loadJson(localKey(), {});
  let server = null; // { mine: {key: score}, board: {key: [{name, score}]} }
  const decodeMine = (v) => {
    const out = {};
    String(v || "").split(",").forEach((p) => { const [k, s] = p.split("="); if (k && +s > 0) out[k] = +s; });
    return out;
  };
  const decodeBoard = (v) => {
    const out = {};
    String(v || "").split(";").forEach((part) => {
      const i = part.indexOf(":"); if (i < 1) return;
      const game = part.slice(0, i);
      if (game.endsWith("_ids")) return;
      out[game] = part.slice(i + 1).split("|").map((e) => { const [n, s] = e.split("="); return n && +s >= 0 ? { name: n, score: +s } : null; }).filter(Boolean);
    });
    return out;
  };
  const loadServer = async () => {
    if (server) return server;
    try {
      const p = await B.prefs();
      const c = (p && p.CustomPrefs) || {};
      server = { mine: decodeMine(c.benflix_scores), board: decodeBoard(c.benflix_scoreboard) };
    } catch (e) { server = { mine: {}, board: {} }; }
    return server;
  };
  B.bgBest = (board) => Math.max(localBests()[board] || 0, (server && server.mine[board]) || 0);
  /** Saves a run's score; says whether it was a new best */
  B.bgSubmit = (board, score) => {
    const before = B.bgBest(board);
    if (!(score > before)) return false;
    const l = localBests(); l[board] = score; store(localKey(), JSON.stringify(l));
    if (server) server.mine[board] = score;
    // Kids don't go on the Leaderboards (same as the app)
    if (!kids()) {
      B.savePrefs((c) => {
        const mine = decodeMine(c.benflix_scores);
        mine[board] = Math.max(mine[board] || 0, score);
        c.benflix_scores = Object.keys(mine).map((k) => `${k}=${mine[k]}`).join(",");
      }).catch((e) => console.warn("Couldn't save the score", e));
    }
    return true;
  };
  const top3 = (board) => ((server && server.board[board]) || []).slice().sort((a, b) => b.score - a.score).slice(0, 3);

  // ---------- tiles ----------
  // A drawn tile in the game's colours (also the picture on its page)
  const art = (g, tall) => {
    const [a, b] = g.colors;
    const words = g.title.split(" ");
    const lines = []; let cur = "";
    words.forEach((w) => { if ((cur + " " + w).trim().length > 11 && cur) { lines.push(cur); cur = w; } else cur = (cur + " " + w).trim(); });
    if (cur) lines.push(cur);
    const h = tall ? 300 : 300, w = 200;
    const text = lines.slice(0, 3).map((l, i) => `<text x="16" y="${h - 22 - (lines.length - 1 - i) * 26}" font-family="Outfit,Arial,sans-serif" font-weight="700" font-size="23" fill="#fff">${esc(l)}</text>`).join("");
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs><rect width="${w}" height="${h}" fill="url(#g)"/><circle cx="160" cy="52" r="70" fill="#fff" opacity=".08"/><circle cx="40" cy="120" r="34" fill="#fff" opacity=".06"/>${text}</svg>`;
    return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
  };
  const tile = (g, sub) => `<a class="poster bgm" href="#/bgame/${g.key}" data-bg="${g.key}" title="${esc(g.title)}"><div class="im"><img class="art" src="${art(g)}" alt=""></div><div class="cap"><span class="nm">${esc(g.title)}</span><small>${esc(sub)}</small></div></a>`;
  const badge = (board) => { const b = B.bgBest(board); return b > 0 ? `Best ${b.toLocaleString()}` : "New"; };

  const describe = (g) => {
    const t = g.board ? top3(g.board) : [];
    const best = g.board ? B.bgBest(g.board) : 0;
    return `<div class="t">${esc(g.title)}</div><div class="meta">${[g.kind === "arcade" ? "Arcade · Play alone" : g.kind === "party" ? "Party · Needs the TV and phones" : "Movie puzzle", best ? `Your best ${best.toLocaleString()}` : ""].filter(Boolean).map((x) => `<span>${esc(x)}</span>`).join("")}</div><div class="ov">${esc(g.about || g.short)}</div>${t.length ? `<div class="bg-top3">${t.map((s, i) => `<span>${["🥇", "🥈", "🥉"][i]} ${esc(s.name)} ${s.score.toLocaleString()}</span>`).join("")}</div>` : ""}`;
  };

  /** The Benflix rows on the Games page (js/games.js puts them above your console games) */
  B.bgRows = async (box, infoBox) => {
    await loadServer();
    const puzzles = PUZZLES.filter((g) => !kids() || g.kids);
    const party = PARTY.filter((g) => !kids() || g.kids);
    box.innerHTML = [
      B.row("Movie puzzles", puzzles.map((g) => tile(g, badge(g.board)))),
      B.row("Party", party.map((g) => tile(g, "Needs the TV and phones"))),
      B.row("Arcade", [...ARCADE.map((g) => tile(g, badge(g.key))), `<a class="poster bgm" href="#/playb/surprise" title="Surprise me"><div class="im"><img class="art" src="${art({ title: "Surprise me", colors: ["#3A3A44", "#16161B"] })}" alt=""></div><div class="cap"><span class="nm">Surprise me</span><small>Random</small></div></a>`]),
      `<div class="wrap"><a class="bg-ember" href="#/bgame/EMBER"><b>Ember Road</b><span>Benflix's own adventure game. Plays on the TV with your phone.</span></a></div>`,
    ].join("");
    if (infoBox && !infoBox.innerHTML.trim() && puzzles[0]) infoBox.innerHTML = describe(find(puzzles[0].key));
    const point = (e) => { const c = e.target.closest("[data-bg]"); if (!c) return; const g = find(c.dataset.bg); if (g && infoBox) infoBox.innerHTML = describe(g); };
    box.addEventListener("mouseover", point);
    box.addEventListener("focusin", point);
  };

  // ---------- a game's page ----------
  B.route(/^\/bgame\/([A-Z_0-9]+)/, async (m, nav) => {
    const key = m[1];
    if (key === "EMBER") {
      B.titlePage({
        title: "Ember Road", color: DOORS.games.color, cover: { url: art({ title: "Ember Road", colors: ["#B45309", "#1C1917"] }), ratio: 2 / 3 },
        info: ["Adventure", "1 player"],
        buttons: [{ label: "Play on the TV", icon: "tv", off: "Ember Road plays on the TV, with your Android phone as the controller. Open Games on the TV to play it." }],
        story: "Benflix's own adventure game: walk the road, make choices and keep the fire going. It plays on the TV, with your phone in your hand.",
      });
      return;
    }
    const g = find(key);
    if (!g || (kids() && g.kind !== "arcade" && !g.kids)) return B.go("#/games");
    await loadServer();
    if (!B.alive(nav)) return;
    const best = g.board ? B.bgBest(g.board) : 0;
    const t = g.board ? top3(g.board) : [];
    const party = g.kind === "party";
    B.titlePage({
      title: g.title,
      color: DOORS.games.color,
      cover: { url: art(g), ratio: 2 / 3 },
      info: [g.kind === "arcade" ? "Arcade" : party ? "Party" : "Movie puzzle", party ? "Needs phones" : "1 player", g.minutes || ""],
      buttons: [
        party
          ? { label: "Host on the TV", icon: "tv", off: "Party games run on the TV, and everyone answers on their own Android phone. Start it from Games on the TV." }
          : { label: best ? "Play again" : "Play", icon: "play", href: `#/playb/${g.key}`, note: g.kind === "arcade" ? g.how : "Click an answer, or use the arrow keys and Enter. Number keys pick a choice too." },
      ],
      story: g.about || g.short,
      strip: B.tpStrip([
        best ? ["Your best", best.toLocaleString(), true] : null,
        g.ranked ? [`Ranked by ${g.ranked}`, ""] : null,
      ]),
      overviewExtra: `${(g.steps || []).length ? `<ol class="bg-steps">${g.steps.map((s) => `<li>${esc(s)}</li>`).join("")}</ol>` : ""}${t.length ? `<div class="bg-top3"><b>Top 3</b>${t.map((s, i) => `<span>${["🥇", "🥈", "🥉"][i]} ${esc(s.name)} ${s.score.toLocaleString()}</span>`).join("")}</div>` : ""}${g.kind === "puzzle" && !kids() ? `<div class="bg-anime"><span class="dim">Anime</span> <button class="chip" id="bgAnime"></button></div>` : ""}`,
    });
    const a = $("#bgAnime");
    if (a) {
      const draw = () => { a.textContent = ({ MIXED: "Mixed in", ONLY: "Anime only", NONE: "No anime" })[B.bgAnime()]; };
      a.onclick = () => { B.bgSetAnime({ MIXED: "ONLY", ONLY: "NONE", NONE: "MIXED" }[B.bgAnime()]); draw(); };
      draw();
    }
  });
  // The anime switch on the movie puzzles (kept per profile in this browser)
  B.bgAnime = () => (B.webSettings().bgAnime || "MIXED");
  B.bgSetAnime = (v) => B.setWebSetting("bgAnime", v);

  // ---------- playing (full screen) ----------
  B.route(/^\/playb\/([A-Za-z_0-9]+)/, async (m, nav) => {
    if (B.music) B.music.stop();
    if (B.kept) B.dropKept();
    let key = m[1];
    if (key === "surprise") key = ARCADE[Math.floor(Math.random() * ARCADE.length)].key;
    const g = find(key);
    if (!g || g.kind === "party") return B.go("#/games");
    await loadServer();
    if (!B.alive(nav)) return;
    B.page().innerHTML = `<div class="bgplay" style="--g1:${g.colors[0]};--g2:${g.colors[1]}"><div class="bgbar"><button class="btn small" id="bgQuit">${I("left")} ${esc(g.title)}</button><span class="bgscore" id="bgScore"></span></div><div class="bgstage" id="bgStage"></div></div>`;
    const stage = $("#bgStage");
    const quit = () => B.go(`#/bgame/${g.key}`);
    $("#bgQuit").onclick = quit;
    const ctx = {
      game: g, stage, nav, kids: kids(),
      alive: () => B.alive(nav),
      setScore: (html) => { const s = $("#bgScore"); if (s) s.innerHTML = html; },
      best: () => B.bgBest(g.board),
      submit: (score) => B.bgSubmit(g.board, score),
      quit,
      again: () => B.render(),
    };
    try {
      if (g.kind === "arcade") B.bgArcade(ctx);
      else B.bgPuzzle(ctx);
    } catch (e) {
      console.warn(e);
      stage.innerHTML = `<div class="bg-msg"><h2>That game didn't start</h2><p class="dim">${esc(e.message || "")}</p><button class="btn white" id="bgBack">Back</button></div>`;
      $("#bgBack").onclick = quit;
    }
  });

  /** The end-of-run card every game shows */
  B.bgOver = (ctx, title, body, score) => {
    const newBest = ctx.submit(score);
    const best = ctx.best();
    const box = document.createElement("div");
    box.className = "bg-over";
    box.innerHTML = `<div class="bg-card"><h2>${esc(newBest ? `New best: ${score.toLocaleString()}!` : title)}</h2><p>${esc(body).replace(/\n/g, "<br>")}</p><p class="dim">Your best: ${best.toLocaleString()}</p><div class="acts"><button class="btn white" id="bgAgain">${I("restart")} Play again</button><button class="btn" id="bgDone">Done</button></div></div>`;
    ctx.stage.appendChild(box);
    $("#bgAgain", box).onclick = ctx.again;
    $("#bgDone", box).onclick = ctx.quit;
    setTimeout(() => { const b = $("#bgAgain", box); if (b) b.focus(); }, 300);
    return newBest;
  };

  // Game sounds (the same little sounds as the app)
  const SFX = {};
  B.bgSound = (name) => {
    if (B.webSettings().bgMute) return;
    try {
      const a = SFX[name] || (SFX[name] = new Audio(`web/sounds/game_${name}.wav`));
      const c = a.cloneNode(); c.volume = 0.5; c.play().catch(() => {});
    } catch (e) {}
  };
})();
