/* Benflix web: the "Can't decide?" games at the bottom of the Movies and Shows doors.
   This or that: two posters, pick one, the winner meets the next, 8 rounds, the last one is tonight's pick.
   Mood quiz: pick moods and how long, then the best matches. Both only pick from the door they came from. */
"use strict";

(() => {
  const ROUNDS = 8;
  const MOODS = [
    ["Funny", ["Comedy"]], ["Exciting", ["Action", "Adventure", "Thriller"]], ["Scary", ["Horror"]],
    ["Feel-good", ["Family", "Romance", "Music"]], ["Mystery", ["Mystery", "Crime"]], ["Romantic", ["Romance"]],
    ["Animated", ["Animation", "Anime"]], ["Mind-bending", ["Science Fiction", "Fantasy"]], ["Surprise us", []],
  ];
  const LENGTHS = {
    movies: [["Under 2 hours", (m) => m < 120], ["Long one", (m) => m >= 120], ["Any", null]],
    shows: [["Under 30 min", (m) => m <= 30], ["Longer episodes", (m) => m > 30], ["Any", null]],
  };
  const typeOf = (door) => (door === "shows" ? "Series" : "Movie");
  const word = (door) => (door === "shows" ? "show" : "movie");
  const items = async (qs) => B.keep(((await B.get(`/Items?userId=${B.uid()}&Recursive=true&${qs}`)) || {}).Items || []);

  // The row at the bottom of each door page
  B.pickerRow = (door) => `<section class="rowx"><h3>Can't decide?</h3><div class="pickers" style="--accent:${DOORS[door].color}">
      <a class="pickt" href="#/pick/tot/${door}"><span class="mk">VS</span><span><b>This or that</b><small>Two ${door}, pick one. 8 rounds and the winner is tonight's pick.</small></span></a>
      <a class="pickt" href="#/pick/mood/${door}"><span class="mk">:)</span><span><b>Mood quiz</b><small>Everyone picks a mood and how long, then see what fits.</small></span></a>
    </div></section>`;

  const frame = (door, inner) => B.top(DOORS[door].name, DOORS[door].color) + `<div class="wrap pickpage" style="--accent:${DOORS[door].color}">${inner}</div>`;

  // ---------- This or that ----------
  B.route(/^\/pick\/tot\/(movies|shows)$/, async (m, nav) => {
    const door = m[1];
    B.page().innerHTML = frame(door, `<div class="h1">This or that</div><div id="tot">${B.spinner()}</div>`);
    B.wireTop();
    const pool = await items(`IncludeItemTypes=${typeOf(door)}&IsPlayed=false&MinCommunityRating=6&SortBy=Random&Limit=${ROUNDS + 1}&Fields=${B.FIELDS}`);
    if (!B.alive(nav)) return;
    let champ = pool[0], next = 1, round = 1;
    const box = $("#tot");
    const card = (it, side) => `<button class="totp" data-side="${side}" aria-label="${esc(it.Name)}">
        <span class="im">${B.poster(it, 600) ? `<img src="${B.poster(it, 600)}" alt="">` : `<span class="noart">${esc(it.Name)}</span>`}</span>
        <span class="cap">${esc(it.Name)}${it.ProductionYear ? ` · ${it.ProductionYear}` : ""}</span></button>`;
    const draw = () => {
      if (!champ) {
        box.innerHTML = `<p class="sub">Not enough to pick from yet. Try again later.</p><a class="btn white" href="#/${door}">Back to ${DOORS[door].name}</a>`;
        return;
      }
      if (next >= pool.length || round > ROUNDS) {
        box.innerHTML = `<div class="totwin"><span class="im">${B.poster(champ, 600) ? `<img src="${B.poster(champ, 600)}" alt="">` : ""}</span>
          <div><div class="lbl">TONIGHT'S PICK</div><div class="h1">${esc(champ.Name)}</div><p class="sub">${esc(champ.Overview || "")}</p>
          <div class="btns"><a class="btn white" href="${B.hrefOf(champ)}">Let's watch it</a><button class="btn" id="again">Play again</button></div></div></div>`;
        $("#again").onclick = () => B.render();
        const w = $(".totwin .btn.white"); if (w) w.focus({ preventScroll: true });
        return;
      }
      box.innerHTML = `<div class="lbl">ROUND ${round} OF ${ROUNDS} · WHICH ${word(door).toUpperCase()} TONIGHT?</div>
        <div class="totrow">${card(champ, "keep")}<span class="or">or</span>${card(pool[next], "new")}</div>
        <p class="sub center0">The one you keep meets a new ${word(door)} next round.</p>`;
      box.querySelectorAll(".totp").forEach((b) => {
        b.onclick = () => {
          if (b.dataset.side === "new") champ = pool[next];
          next++; round++;
          draw();
        };
      });
      const f = box.querySelector(".totp"); if (f) f.focus({ preventScroll: true });
    };
    draw();
  });

  // ---------- Mood quiz ----------
  B.route(/^\/pick\/mood\/(movies|shows)$/, async (m, nav) => {
    const door = m[1];
    B.page().innerHTML = frame(door, `<div id="quiz"></div>`);
    B.wireTop();
    const box = $("#quiz");
    const moods = new Set(), lens = new Set();
    const chips = (list, set) => list.map(([name], i) => `<button class="chip ${set.has(i) ? "on" : ""}" data-i="${i}">${esc(name)}</button>`).join("");
    const ask = (step) => {
      const list = step === 0 ? MOODS : LENGTHS[door];
      const set = step === 0 ? moods : lens;
      box.innerHTML = `<div class="lbl">QUESTION ${step + 1} OF 2</div>
        <div class="h1">${step === 0 ? "What's the group in the mood for?" : door === "shows" ? "How long are the episodes?" : "How long a movie?"}</div>
        <p class="sub">Pick as many as you like.</p><div class="qchips">${chips(list, set)}</div>
        <div class="btns"><button class="btn white" id="go">${step === 0 ? "Next" : "Show picks"}</button>${step === 1 ? `<button class="btn" id="back">Back</button>` : ""}
        <span class="dim" id="count">${set.size ? set.size + " picked" : "Nothing picked: anything goes"}</span></div>`;
      box.querySelectorAll(".qchips .chip").forEach((c) => {
        c.onclick = () => {
          const i = +c.dataset.i;
          set.has(i) ? set.delete(i) : set.add(i);
          c.classList.toggle("on", set.has(i));
          $("#count").textContent = set.size ? set.size + " picked" : "Nothing picked: anything goes";
        };
      });
      $("#go").onclick = () => (step === 0 ? ask(1) : results());
      if (step === 1) $("#back").onclick = () => ask(0);
      const f = box.querySelector(".qchips .chip"); if (f) f.focus({ preventScroll: true });
    };
    const results = async () => {
      const picked = [...moods].map((i) => MOODS[i]).filter((x) => x[1].length);
      const genres = [...new Set(picked.flatMap((x) => x[1]))];
      const title = picked.map((x) => x[0].toLowerCase()).join(" or ") || "Something for everyone";
      box.innerHTML = `<div class="qhead"><div class="h1">${esc(title[0].toUpperCase() + title.slice(1))}</div><button class="btn" id="change">Change answers</button></div><div id="res">${B.spinner()}</div>`;
      $("#change").onclick = () => ask(0);
      const found = await items(`IncludeItemTypes=${typeOf(door)}&SortBy=Random&Limit=300&Fields=${B.FIELDS}${genres.length ? `&Genres=${encodeURIComponent(genres.join("|"))}` : ""}`);
      if (!B.alive(nav)) return;
      const tests = [...lens].map((i) => LENGTHS[door][i][1]).filter(Boolean);
      const fits = (it) => { const mins = B.minutes(it.RunTimeTicks); return !mins || !tests.length || tests.some((t) => t(mins)); };
      const list = found.filter(fits).map((it) => {
        const g = new Set(it.Genres || []);
        const hits = picked.filter((x) => x[1].some((y) => g.has(y))).length;
        return { it, hits, all: picked.length > 1 && hits === picked.length };
      }).sort((a, b) => b.hits - a.hits || ((a.it.UserData || {}).Played ? 1 : 0) - ((b.it.UserData || {}).Played ? 1 : 0) || (b.it.CommunityRating || 0) - (a.it.CommunityRating || 0)).slice(0, 48);
      $("#res").innerHTML = list.length
        ? `<p class="sub">Best matches first. Ones that fit every mood say so.</p><div class="qgrid">${list.map((r) => `<div>${B.posterCard(r.it)}${r.all ? `<div class="fitall">Fits every mood</div>` : ""}</div>`).join("")}</div>`
        : `<p class="sub">Nothing matched. Try fewer answers.</p>`;
      const f = $("#res .poster"); if (f) f.focus({ preventScroll: true });
    };
    ask(0);
  });
})();
