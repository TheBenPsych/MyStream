/* Benflix web: the startup intro, signing in, Who's watching, Home with the four doors and the
   profile menu. Follows the approved redesign (setup screens, intro, Home, menu). */
"use strict";

// ---------- sounds (soft mallets: two notes when Benflix opens, four as the doors grow) ----------
B.sound = (name) => {
  if (B.webSettings().sounds === false) return;
  try { const a = new Audio(`/web/sounds/${name}.wav`); a.volume = name === "doors" ? 0.55 : 0.45; a.play().catch(() => {}); } catch (e) {}
};

// ---------- intro: logo fades in, holds, then glides up into Who's watching ----------
B.intro = () => new Promise((done) => {
  const lite = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const el = document.createElement("div");
  el.id = "intro";
  el.innerHTML = B.logo();
  document.body.appendChild(el);
  B.sound("opening");
  requestAnimationFrame(() => el.classList.add("on"));
  setTimeout(() => {
    if (!lite) el.classList.add("glide");
    setTimeout(() => { el.classList.add("done"); done(); setTimeout(() => el.remove(), 300); }, lite ? 250 : 750);
  }, 700);
});

// ---------- Who's watching ----------
B.route(/^\/who$/, async () => {
  B.me = null;
  const list = B.profiles();
  if (!list.length) return B.go("#/signin");
  const last = load("benflix.last");
  const order = list.slice().sort((a, b) => (a.kids - b.kids) || a.name.localeCompare(b.name));
  B.page().innerHTML = `<div class="who fade-in">
    ${B.logo()}
    <h1>Who's watching?</h1>
    <div class="people">${order.map((p) => `<button class="person" data-id="${esc(p.id)}">${B.picHtml(p, "", 260)}<span>${esc(p.name)}</span>${p.expired ? '<span class="gone">Sign in again</span>' : ""}</button>`).join("")}</div>
    <div class="add"><button class="linkbtn" id="addSomeone">Add someone</button></div>
  </div>`;
  const focus = $(`.person[data-id="${CSS.escape(last || "")}"]`) || $(".person");
  if (focus) focus.focus();
  $(".people").onclick = (e) => {
    const b = e.target.closest(".person"); if (!b) return;
    const p = B.profiles().find((x) => x.id === b.dataset.id);
    if (!p) return;
    if (p.expired) return B.go(`#/signin?name=${enc(p.name)}`);
    B.pick(p.id);
    sessionStorage.setItem("benflix.picked", p.id);
    B.doorsGrow = true;
    B.go("#/home");
  };
  $("#addSomeone").onclick = () => B.go("#/signin");
});

// ---------- sign in: one question per screen, then Quick Connect as the second way ----------
B.route(/^\/signin/, async () => {
  B.me = null;
  let name = B.q("name") || "";
  const back = B.profiles().length ? `<button class="linkbtn" id="toWho">Back to Who's watching</button>` : "";
  const shell = (inner) => {
    B.page().innerHTML = `<div class="signin"><div class="card fade-in">${B.logo()}${inner}<div style="margin-top:22px">${back}</div></div></div>`;
    const w = $("#toWho"); if (w) w.onclick = () => B.go("#/who");
  };
  const askName = () => {
    shell(`<h1>Sign in to Benflix</h1><p>Type your name, the one you use for Benflix on the TV.</p>
      <form id="f"><input class="field" id="v" autocomplete="username" placeholder="Your name" value="${esc(name)}">
      <div class="err" id="err"></div>
      <div class="btns"><button class="btn white" type="submit">Next</button><button class="btn" type="button" id="qc">${I("phone")} Sign in with my phone or TV</button></div></form>`);
    $("#v").focus();
    $("#f").onsubmit = (e) => {
      e.preventDefault();
      name = $("#v").value.trim();
      if (!name) { $("#v").classList.add("bad"); $("#err").textContent = "Type your name first."; return; }
      askPassword();
    };
    $("#qc").onclick = quickConnect;
  };
  const askPassword = () => {
    shell(`<h1>Hi ${esc(name)}</h1><p>Now your password. Leave it empty if you don't have one.</p>
      <form id="f"><input type="text" autocomplete="username" value="${esc(name)}" hidden><input class="field" id="v" type="password" autocomplete="current-password" placeholder="Password">
      <div class="err" id="err"></div>
      <div class="btns"><button class="btn white" type="submit" id="go">Sign in</button><button class="btn ghost" type="button" id="bk">Not ${esc(name)}?</button></div></form>`);
    $("#v").focus();
    $("#bk").onclick = askName;
    $("#f").onsubmit = async (e) => {
      e.preventDefault();
      const go = $("#go"); go.disabled = true; go.textContent = "Signing in…"; $("#err").textContent = "";
      const device = B.newDevice();
      try {
        const s = await B.call("POST", "/api/web/login", { username: name, password: $("#v").value, device }, { device });
        finish(s, device);
      } catch (err) {
        $("#v").classList.add("bad"); $("#err").textContent = err.message;
        go.disabled = false; go.textContent = "Sign in";
      }
    };
  };
  const quickConnect = async () => {
    const device = B.newDevice();
    shell(`<h1>Sign in with a code</h1><p>On a TV or phone that's already signed in to Benflix, open Settings, then Quick Connect, and type this code.</p><div id="code">${B.spinner()}</div><div class="err" id="err"></div>
      <div class="btns"><button class="btn" id="bk">Type my name instead</button></div>`);
    $("#bk").onclick = () => { stop = true; askName(); };
    let stop = false;
    try {
      const s = await B.call("POST", "/api/web/qc/start", {}, { device });
      $("#code").innerHTML = `<div class="code">${String(s.code || "").split("").map((c) => `<b>${esc(c)}</b>`).join("")}</div><p style="text-align:center" class="dim">Waiting for the code…</p>`;
      const tick = async () => {
        if (stop || !$("#code")) return;
        try {
          const c = await B.call("POST", "/api/web/qc/check", { secret: s.secret }, { device });
          if (c.ok) return finish(await B.call("POST", "/api/web/qc/finish", { secret: s.secret }, { device }), device);
          if (c.gone) { $("#err").textContent = "That code ran out. Press back and try again."; return; }
        } catch (err) { $("#err").textContent = err.message; }
        setTimeout(tick, 3000);
      };
      setTimeout(tick, 3000);
    } catch (err) { $("#code").innerHTML = ""; $("#err").textContent = err.message; }
  };
  const finish = (s, device) => {
    const p = { id: s.id, name: s.name, token: s.token, admin: !!s.admin, kids: !!s.kids, image: s.image || null, device };
    const old = B.profiles().find((x) => x.id === p.id);
    if (old && old.token !== p.token) {
      // Signed in again: the old sign-in isn't needed any more
      B.call("POST", "/api/web/jf/Sessions/Logout", undefined, { profile: old, quiet401: true }).catch(() => {});
    }
    B.addProfile(p);
    B.pick(p.id);
    sessionStorage.setItem("benflix.picked", p.id);
    B.doorsGrow = true;
    B.go("#/home");
  };
  askName();
});

// ---------- Home: greeting, news pills and the four doors ----------
B.route(/^\/home$/, async (m, nav) => {
  const grow = B.doorsGrow; B.doorsGrow = false;
  const kids = B.me.kids;
  const doors = ["movies", "shows", "live", "games"].map((k) => DOORS[k]);
  B.page().innerHTML = B.top("") + `<div class="home">
    <div class="greet">${esc(B.holiday() || B.greeting())}, ${esc(B.me.name)}</div>
    <div class="news" id="news"></div>
    <div class="doors ${grow ? "grow" : ""}" id="doors">${doors.map((d) => `<a class="door" href="#/${d.key}" style="--c:${d.color}">${I(d.icon)}<div><div class="n">${esc(d.name)}</div>${kids ? "" : `<div class="d">${esc(d.desc)}</div>`}</div></a>`).join("")}</div>
  </div>`;
  B.wireTop();
  if (grow) {
    // The doors grow out of the logo squares, one after another
    const logo = $(".top .logo .sq").getBoundingClientRect();
    $$(".door").forEach((d) => {
      const r = d.getBoundingClientRect();
      d.style.setProperty("--fx", `${logo.left - r.left}px`);
      d.style.setProperty("--fy", `${logo.top - r.top}px`);
    });
    B.sound("doors");
  }
  setTimeout(() => { const f = $(".door"); if (f && B.alive(nav)) f.focus({ preventScroll: true }); }, grow ? 800 : 0);
  B.loadNews(nav);
});

// Big holidays only, clean and simple
B.holiday = () => {
  const d = new Date(), md = (d.getMonth() + 1) * 100 + d.getDate();
  if (md === 1225) return "Merry Christmas";
  if (md === 101) return "Happy New Year";
  if (md === 1031) return "Happy Halloween";
  if (md === 704) return "Happy 4th of July";
  return "";
};

// News pills: new episodes of shows you watch, notes from the server owner, fixed problems (max 3)
B.loadNews = async (nav) => {
  const out = [];
  try {
    const p = await B.prefs();
    const c = p.CustomPrefs || {};
    const note = String(c["benflix.note"] || "");
    const i = note.indexOf("\n");
    if (i > 0) {
      const exp = +(note.slice(0, i).split("~")[1] || 0);
      if (!exp || exp > Date.now()) out.push({ text: note.slice(i + 1).split("\n")[0], color: "#F1F2F5", href: "" });
    }
    String(c["benflix.fixed"] || "").split("\n").filter(Boolean).slice(-1).forEach((l) => {
      const [ts, title] = l.split("|");
      if (Date.now() - +ts < 3 * 86400000) out.push({ text: `Ben fixed ${title}`, color: "#F1F2F5", href: "" });
    });
  } catch (e) {}
  try {
    const r = await B.get(`/Shows/NextUp?userId=${B.uid()}&limit=24&fields=DateCreated&enableResumable=false&enableRewatching=false&disableFirstEpisode=true`);
    const seen = new Set();
    (r.Items || []).filter((e) => B.isNew(e) && Date.now() - new Date(e.DateCreated) < 3 * 86400000).forEach((e) => {
      if (seen.has(e.SeriesId)) return;
      seen.add(e.SeriesId);
      out.push({ text: e.IndexNumber === 1 ? `${e.SeriesName} season ${e.ParentIndexNumber} is here` : `${e.SeriesName} has a new episode`, color: DOORS.shows.color, href: `#/show/${e.SeriesId}?ep=${e.Id}` });
    });
  } catch (e) {}
  if (!B.alive(nav) || !$("#news")) return;
  $("#news").innerHTML = out.slice(0, 3).map((n) => `<${n.href ? `a href="${n.href}"` : "span"} class="pill"><span class="d" style="background:${n.color}"></span>${esc(n.text)}</${n.href ? "a" : "span"}>`).join("");
};

// ---------- profile menu: corner card, picture + name on the left, list on the right ----------
B.openMenu = async () => {
  if (!B.me) return;
  if (B.me.kids) return B.go("#/who");
  B.closeAll();
  B.shade();
  const c = document.createElement("div");
  c.className = "menu-card";
  const items = [
    ["#/requests", "plus", "Requests"],
    ["#/mine", "chart", "My watching"],
    ["#/settings", "gear", "Settings"],
    B.me.admin ? ["#/server", "server", "Server"] : null,
    ["#/help", "help", "Help and updates"],
    ["report", "flag", "Report a problem"],
  ].filter(Boolean);
  c.innerHTML = `<div class="left">${B.picHtml(B.me, "", 220)}<div><div class="nm">${esc(B.me.name)}</div><div class="wt" id="wt">&nbsp;</div></div><button class="btn small" data-go="#/who">${I("swap")} Switch profile</button></div>
    <div class="list">${items.map(([href, icon, label]) => `<button data-go="${href}">${I(icon)}${esc(label)}${label === "Server" ? '<span class="badge hidden" id="srvBadge"></span>' : ""}</button>`).join("")}
      <div class="sep"></div><button class="out" data-go="signout">${I("out")}Sign out</button></div>`;
  document.body.appendChild(c);
  $("button[data-go]", c).focus();
  c.onclick = async (e) => {
    const b = e.target.closest("[data-go]"); if (!b) return;
    const where = b.dataset.go;
    B.closeAll();
    if (where === "signout") return B.signOut();
    if (where === "report") return B.report();
    B.go(where);
  };
  B.watchTime().then((w) => { const el = $("#wt"); if (el) el.textContent = `${B.hm(w.month)} this month`; }).catch(() => {});
};

B.signOut = async () => {
  if (!(await B.ask(`Sign ${B.me.name} out of Benflix in this browser?`, "Your lists and settings stay safe on the server. Sign back in any time.", "Sign out"))) return;
  const me = B.me;
  try { await B.jf("POST", "/Sessions/Logout", undefined, { quiet401: true }); } catch (e) {}
  B.forget(me.id);
  B.me = null;
  sessionStorage.removeItem("benflix.picked");
  B.go("#/who");
};

// Report a problem: pick what's wrong; it lands on the Server page's Problems list
B.report = (item) => {
  const kinds = ["No sound", "Keeps stopping to load", "Subtitles are wrong", "Will not play", "Something else"];
  const what = item || B.lastPlayed || null;
  const panel = B.side(`<h3>Report a problem</h3><p class="dim">Pick what's wrong. ${what ? `It's about <b>${esc(what.title)}</b>.` : ""} Ben gets it on the Server page.</p>
    ${kinds.map((k) => `<button class="opt" data-k="${esc(k)}">${esc(k)}</button>`).join("")}`);
  panel.onclick = async (e) => {
    const b = e.target.closest("[data-k]"); if (!b) return;
    let problem = b.dataset.k;
    if (problem === "Something else") {
      const t = await B.askText("What's wrong?", "A few words is fine.", "", { long: true, ok: "Send" });
      if (!t || !t.trim()) return;
      problem = t.trim();
    }
    B.closeAll();
    B.busy(null, async () => {
      const line = [Date.now(), what ? what.id : "", (what ? what.title : "Benflix web").replace(/[|\n]/g, " "), problem.replace(/[|\n]/g, " "), "Web browser"].join("|");
      await B.savePrefs((c) => {
        const lines = String(c["benflix.problems"] || "").split("\n").filter(Boolean);
        lines.push(line);
        c["benflix.problems"] = lines.slice(-20).join("\n");
      });
      return "Sent. Thanks for telling Ben.";
    });
  };
};

// ---------- start ----------
B.start = async () => {
  const picked = sessionStorage.getItem("benflix.picked");
  if (picked && B.profiles().some((p) => p.id === picked && !p.expired)) {
    B.pick(picked);
    return B.render();
  }
  if (!location.hash || location.hash === "#/" || location.hash === "#/who") {
    if (B.profiles().length) {
      history.replaceState(null, "", "#/who");
      await B.intro();
    }
  }
  B.render();
};
document.addEventListener("DOMContentLoaded", B.start);
