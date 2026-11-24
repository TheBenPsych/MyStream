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
    { key: "POSTER", title: "Poster puzzle", short: "Tiles flip off a poster. Guess early for more points.", about: "A poster hides under 16 tiles that flip away one by one, but never all of them. Guess as soon as you know it.", steps: ["Watch the tiles flip away.", "Pick the title whenever you know it.", "Fewer tiles flip as you go. One wrong guess ends the run."], minutes: "About 3 min", board: "POSTER_PUZZLE", ranked: "most points in a run", colors: C("#A16207", "#422006"), icon: "list", kids: true },
    { key: "GRID", title: "Movie grid", short: "Fill a 3 by 3 board with titles that fit both rules.", about: "Every square needs a movie or show from Tower that fits its row rule and its column rule.", steps: ["Pick a square.", "Pick the title that fits both rules.", "3 lives. A full grid earns a bonus."], minutes: "About 5 min", board: "MOVIE_GRID", ranked: "most points on a grid", colors: C("#4338CA", "#1E1B4B"), icon: "list", kids: false },
    { key: "TIMELINE", title: "Timeline", short: "Put each poster in the right spot by release year.", about: "Posters arrive one at a time. Slot each one into the right place on a growing line of years.", steps: ["Look at the new poster.", "Move along the line and press Here.", "One wrong spot ends the run."], minutes: "About 3 min", board: "TIMELINE", ranked: "longest line", colors: C("#0F766E", "#042F2E"), icon: "swap", kids: true },
    { key: "ACTOR", title: "Who's that actor?", short: "A zoomed-in photo zooms out until you know them.", about: "An actor's photo starts zoomed right in on their eyes and slowly zooms out.", steps: ["Watch the photo zoom out.", "Pick their name from 6.", "Misses add hints. Three misses ends the run."], minutes: "About 3 min", board: "WHOS_THAT_ACTOR", ranked: "most points in a run", colors: C("#9333EA", "#3B0764"), icon: "user", kids: false },
    { key: "QUOTE", title: "Quote it", short: "A famous line from a movie or show. Where's it from?", about: "A famous line from a movie or show on Tower. Name where it's from. Who says it shows partway through.", steps: ["Read the famous line.", "Who says it shows as the clock runs.", "Pick the title. One wrong guess ends the run."], minutes: "About 3 min", board: "QUOTE_IT", ranked: "most points in a run", colors: C("#0891B2", "#164E63"), icon: "sub", kids: false },
    { key: "ROLE", title: "Who played them?", short: "A character's name. Pick who played them from 4 photos.", about: "You get a character and the movie they're in. Pick the actor who played them from 4 photos.", steps: ["Read the character's name.", "Pick the actor from 4 photos.", "One wrong pick ends the run."], minutes: "About 3 min", board: "WHO_PLAYED", ranked: "most points in a run", colors: C("#7C3AED", "#2E1065"), icon: "user", kids: false },
    { key: "BLITZ", title: "60-second blitz", short: "True or false, as many as you can in 60 seconds.", about: "True or false lines about what's on Tower. Answer as many as you can in 60 seconds.", steps: ["Read the line.", "True or false?", "A wrong answer costs 3 seconds."], minutes: "1 min", board: "BLITZ", ranked: "most right in a blitz", colors: C("#EAB308", "#713F12"), icon: "star", kids: false },
    { key: "SEASON", title: "Which season?", short: "A scene from a show. Which season is it from?", about: "A real scene from one of your shows. Guess which season it's from.", steps: ["Look at the scene.", "Pick the season.", "One wrong guess ends the run."], minutes: "About 3 min", board: "WHICH_SEASON", ranked: "most points in a run", colors: C("#2563EB", "#172554"), icon: "tv", kids: false },
    { key: "GUESS", title: "Guess the movie", short: "A blurry picture gets sharper. Guess it fast!", about: "A blurry picture from a movie gets sharper and sharper. Guess it fast for more points.", steps: ["Watch the picture sharpen.", "Pick the movie.", "Faster answers score more."], minutes: "About 3 min", board: "GUESS_THE_MOVIE", ranked: "most points", colors: C("#0E7490", "#1E3A8A"), icon: "info", kids: false },
    { key: "RIDDLE", title: "Movie riddle", short: "Guess the movie from its clues before time runs out.", about: "Guess the movie from its clues. Clues get easier as the clock runs down, and it keeps going until you miss.", steps: ["Read the clues.", "Pick from 5 movies.", "Fast answers score more. One miss ends the run."], minutes: "About 3 min", board: "MOVIE_RIDDLE_POINTS", ranked: "most points in a run", colors: C("#C2410C", "#6D28D9"), icon: "help", kids: false },
    { key: "HIGHER_LOWER", title: "Higher or Lower", short: "Is the next one rated higher or lower on IMDb?", about: "Two posters. Is the second one rated higher or lower on IMDb? Keep your streak going.", steps: ["Look at the first rating.", "Higher or lower?", "One wrong answer ends the streak."], minutes: "About 2 min", board: "HIGHER_LOWER_STREAK", ranked: "best streak", colors: C("#15803D", "#7F1D1D"), icon: "chart", kids: false },
  ];
  const ARCADE = [
    { key: "POPCORN_POP", title: "Popcorn Pop", short: "Bounce a popcorn kernel up a tower of soda cups and film reels. Grab the tickets!", how: "Arrow keys or A and D to steer. Phone: hold the left or right side.", colors: C("#F59E0B", "#7C2D12") },
    { key: "REEL_RUSH", title: "Reel Rush", short: "Fly the Glowfin B through the gaps in the film strips.", how: "Space, Enter or Up to flap. Phone: tap anywhere.", colors: C("#E50914", "#3B0A0A") },
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
    { key: "CAPTION", title: "Caption this", short: "Write a funny caption for a real scene, then vote.", colors: C("#F43F5E", "#4C0519"), kids: true },
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
  // Each Glowfin game's own cover (web/img/games, the same pictures as the TV and phone app)
  const art = (g) => `web/img/games/${String(g.key || "surprise").toLowerCase()}.png`;
  B.bgCover = art;
  const tile = (g, sub) => `<a class="poster bgm" href="#/bgame/${g.key}" data-bg="${g.key}" title="${esc(g.title)}"><div class="im"><img class="art" src="${art(g)}" alt=""></div><div class="cap"><span class="nm">${esc(g.title)}</span><small>${esc(sub)}</small></div></a>`;
  const badge = (board) => { const b = B.bgBest(board); return b > 0 ? `Best ${b.toLocaleString()}` : "New"; };

  const describe = (g) => {
    const t = g.board ? top3(g.board) : [];
    const best = g.board ? B.bgBest(g.board) : 0;
    const kick = [g.kind === "arcade" ? "Arcade · Play alone" : g.kind === "party" ? "Party · 3 to 8 players" : "Movie puzzle", best ? `Your best ${best.toLocaleString()}` : ""].filter(Boolean).join(" · ");
    const works = g.kind === "arcade" ? ["Remote", "Controller", "Phone"] : g.kind === "party" ? ["Phone"] : ["Remote", "Phone"];
    return `<div class="kick">${esc(kick)}</div><div class="t">${esc(g.title)}</div><div class="ov">${esc(g.about || g.short)}</div>${t.length ? `<div class="bg-top3">${t.map((s, i) => `<span>${i + 1}. ${esc(s.name)} ${s.score.toLocaleString()}</span>`).join("")}</div>` : ""}<span class="gw-chips">${works.map((x) => `<span class="gw-chip">${x}</span>`).join("")}</span>`;
  };

  /** Glowfin's own rows for the Games tabs (js/games.js): any of "puzzles", "party", "arcade", "ember", as HTML */
  B.bgRows = async (keys) => {
    await loadServer();
    const puzzles = PUZZLES.filter((g) => !kids() || g.kids);
    const party = PARTY.filter((g) => !kids() || g.kids);
    const parts = {
      puzzles: () => B.row("Movie puzzles", puzzles.map((g) => tile(g, badge(g.board)))),
      party: () => B.row("Party · needs phones", party.map((g) => tile(g, "Needs the TV and phones"))),
      arcade: () => B.row("Arcade", [...ARCADE.map((g) => tile(g, badge(g.key))), `<a class="poster bgm" href="#/playb/surprise" title="Surprise me"><div class="im"><img class="art" src="${art({ key: "SURPRISE" })}" alt=""></div><div class="cap"><span class="nm">Surprise me</span><small>Random</small></div></a>`]),
      ember: () => `<div class="wrap"><a class="bg-ember" href="#/bgame/EMBER"><b>Ember Road</b><span>Glowfin's own adventure game. Plays on the TV with your phone.</span></a></div>`,
    };
    return (keys || Object.keys(parts)).map((k) => (parts[k] ? parts[k]() : "")).join("");
  };
  /** The top area follows the Glowfin game you point at (For you) */
  B.bgWire = (box, infoBox) => {
    if (!infoBox) return;
    const point = (e) => { const c = e.target.closest("[data-bg]"); if (!c) return; const g = find(c.dataset.bg); if (g) infoBox.innerHTML = describe(g); };
    box.addEventListener("mouseover", point);
    box.addEventListener("focusin", point);
    if (!infoBox.innerHTML.trim()) { const f = box.querySelector("[data-bg]"); const g = f && find(f.dataset.bg); if (g) infoBox.innerHTML = describe(g); }
  };
  /** The Scores tab: every game's board, all time: the top 5 and your best */
  B.bgScores = async () => {
    await loadServer();
    const games = [...PUZZLES.filter((g) => !kids() || g.kids).map((g) => [g.board, g.title, g.key]), ...ARCADE.map((g) => [g.key, g.title, g.key])];
    const card = ([board, title, key]) => {
      const top = ((server && server.board[board]) || []).slice().sort((a, b) => b.score - a.score).slice(0, 5);
      const mine = B.bgBest(board);
      return `<a class="gscore" href="#/bgame/${key}"><b>${esc(title)}</b>${top.length ? `<ol>${top.map((e) => `<li><span>${esc(e.name.toUpperCase())}</span><span>${e.score.toLocaleString()}</span></li>`).join("")}</ol>` : `<span class="dim">No scores yet</span>`}<small>${mine ? `Your best ${mine.toLocaleString()}` : "You haven't played yet"}</small></a>`;
    };
    return `<section class="rowx"><h3>Leaderboards · all time</h3><div class="gscores">${games.map(card).join("")}</div></section>`;
  };

  // ---------- a game's page ----------
  B.route(/^\/bgame\/([A-Z_0-9]+)/, async (m, nav) => {
    const key = m[1];
    if (key === "EMBER") {
      B.titlePage({
        title: "Ember Road", color: DOORS.games.color, cover: { url: art({ key: "EMBER" }), ratio: 2 / 3 },
        info: ["Adventure", "1 player"],
        buttons: [{ label: "Play on the TV", icon: "tv", off: "Ember Road plays on the TV, with your Android phone as the controller. Open Games on the TV to play it." }],
        story: "Glowfin's own adventure game: walk the road, make choices and keep the fire going. It plays on the TV, with your phone in your hand.",
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
    // Glowfin game page (2026-11-18): the cover blurred behind the top, title on the left, the cover on the right,
    // then rows: How to play, Leaderboard, More Glowfin games, About this game
    const kindName = g.kind === "arcade" ? "Glowfin arcade" : party ? "Glowfin party game" : "Glowfin movie puzzle";
    const steps = (g.steps || []).filter(Boolean);
    const howHtml = steps.length || (g.kind === "puzzle" && !kids())
      ? `<h3 class="tp-h">How to play</h3><div class="bg-how">${steps.map((st, i) => `<div class="bg-step"><b>${i + 1}</b><span>${esc(st)}</span></div>`).join("")}${g.kind === "puzzle" && !kids() ? `<div class="bg-step bg-anime"><span class="dim">Anime in the questions</span><button class="chip" id="bgAnime"></button></div>` : ""}</div>`
      : "";
    const boardHtml = g.board
      ? `<h3 class="tp-h">Leaderboard · all time</h3><div class="bg-board">${t.length ? t.map((sc, i) => `<div><span><b class="m${i + 1}">${i + 1}</b>${esc(sc.name)}</span><b>${sc.score.toLocaleString()}</b></div>`).join("") : `<div><span class="dim">No scores yet. Be the first!</span></div>`}<div class="you"><span>Your best</span><b>${best ? best.toLocaleString() : "None yet"}</b></div><small class="dim">Ranked by ${esc(g.ranked || "most points")}</small></div>`
      : "";
    const sameKind = g.kind === "arcade" ? ARCADE.map((x) => find(x.key)) : party ? PARTY.map((x) => find(x.key)) : PUZZLES.map((x) => find(x.key));
    const more = sameKind.filter((x) => x && x.key !== g.key && (!kids() || x.kind === "arcade" || x.kids));
    B.titlePage({
      title: g.title,
      color: DOORS.games.color,
      rows: true,
      kicker: kindName,
      cover: { url: art(g), ratio: 2 / 3 },
      info: [g.kind === "arcade" ? "Arcade" : party ? "Party" : "Movie puzzle", party ? "Needs phones · 3 to 8 players" : "1 player", g.minutes || "", best ? `Your best ${best.toLocaleString()}` : ""],
      story: g.about || g.short,
      status: party ? "Plays on the TV · everyone answers on their Android phone" : g.kind === "arcade" ? "Works with: Keyboard · Controller · Touch" : "Works with: Mouse · Keyboard · Touch",
      buttons: [
        party
          ? { label: "Host on the TV", icon: "tv", off: "Party games run on the TV, and everyone answers on their own Android phone. Start it from Games on the TV." }
          : { label: best ? "Play again" : "Play", icon: "play", href: `#/playb/${g.key}`, note: g.kind === "arcade" ? g.how : "Click an answer, or use the arrow keys and Enter. Number keys pick a choice too." },
        g.board ? { label: "Leaderboard", icon: "chart", onClick: () => { const r = $("#tpBoard"); if (r) r.scrollIntoView({ behavior: "smooth", block: "center" }); } } : null,
      ].filter(Boolean),
      tabs: [
        { name: "How to play", id: "tpHow", html: howHtml, skip: !howHtml },
        { name: "Leaderboard · all time", id: "tpBoard", html: boardHtml, skip: !boardHtml },
        { name: g.kind === "arcade" ? "More Glowfin arcade" : party ? "More party games" : "More movie puzzles", id: "tpLike", html: more.length ? `<h3 class="tp-h">${g.kind === "arcade" ? "More Glowfin arcade" : party ? "More party games" : "More movie puzzles"}</h3><div class="bg-more">${more.map((x) => tile(x, x.kind === "arcade" ? "Arcade" : x.kind === "party" ? "Party" : "Movie puzzle")).join("")}</div>` : "", skip: !more.length },
      ],
      aboutStory: g.about || g.short,
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
      stage.innerHTML = `<div class="bg-msg"><h2>That game didn't start</h2><p class="dim">${esc(B.say(e))}</p><button class="btn white" id="bgBack">Back</button></div>`;
      $("#bgBack").onclick = quit;
    }
  });

  /**
   * The end-of-run card every game shows (Ben's pick: the better end screen): the score big, a NEW BEST
   * label, the posters of the titles you got right with what each earned (opts.run), and "The one that got
   * you" (opts.answer) with Open and My List. opts.local = { best, newBest } for scores kept off the board.
   */
  B.bgOver = (ctx, title, body, score, opts) => {
    const o = opts || {};
    const newBest = o.local ? o.local.newBest : ctx.submit(score);
    const best = o.local ? o.local.best : ctx.best();
    const box = document.createElement("div");
    box.className = "bg-over";
    const run = (o.run || []).slice(-8);
    const a = o.answer;
    box.innerHTML = `<div class="bg-card${run.length ? " wide" : ""}"><div class="bg-end-l">${newBest ? `<div class="bg-newbest">NEW BEST!</div>` : ""}<div class="bg-big">${score.toLocaleString()}</div><h2>${esc(newBest ? `New best: ${score.toLocaleString()}!` : title)}</h2><p>${esc(body).replace(/\n/g, "<br>")}</p><p class="dim">Your best: ${best.toLocaleString()}</p><div class="acts"><button class="btn white" id="bgAgain">${I("restart")} Play again</button>${o.other ? `<button class="btn" id="bgOther">${esc(o.other[0])}</button>` : ""}<button class="btn" id="bgDone">Done</button></div>${a ? `<div class="bg-gotyou">${a.poster ? `<img src="${a.poster}" alt="">` : ""}<div><small>The one that got you</small><b>${esc(a.name)}${a.year ? ` (${a.year})` : ""}</b><span><button class="btn small" id="bgOpen">Open ${a.series ? "show" : "movie"}</button><button class="btn small" id="bgList">${I("plus")} My List</button></span></div></div>` : ""}</div>${run.length ? `<div class="bg-end-r"><small>Your run</small><div class="bg-runs">${run.map((x) => `<span>${x.t.poster ? `<img src="${x.t.poster}" alt="${esc(x.t.name)}">` : `<i>${esc(x.t.name)}</i>`}<em>${esc(x.badge)}</em></span>`).join("")}</div></div>` : ""}</div>`;
    ctx.stage.appendChild(box);
    $("#bgAgain", box).onclick = o.again || ctx.again;
    $("#bgDone", box).onclick = ctx.quit;
    if (o.other) $("#bgOther", box).onclick = () => { box.remove(); o.other[1](); };
    if (a) {
      $("#bgOpen", box).onclick = () => B.go(`#/${a.series ? "show" : "movie"}/${a.id}`);
      $("#bgList", box).onclick = async (e) => {
        const b = e.currentTarget;
        try { await B.jf("POST", `/UserFavoriteItems/${a.id}?userId=${B.uid()}`); b.innerHTML = `${I("check")} On My List`; b.disabled = true; } catch (err) { console.warn(err); }
      };
    }
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
