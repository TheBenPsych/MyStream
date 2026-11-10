/* Benflix web: the Live TV door. Top area describes the channel you point at; Favourites and
   Recently watched rows; then the full guide (2 hours across). Watching: banner at the bottom,
   Up/Down change channel by number, Left opens the side guide. */
"use strict";

const TYPES = [[100, "News"], [200, "Movies"], [300, "Comedy"], [400, "Drama & Crime"], [500, "Reality"], [600, "Kids & Family"], [700, "Sports"], [800, "Sci-Fi & Anime"], [900, "Classic TV"], [1100, "Documentaries & Nature"], [1200, "Food & Home"], [1300, "Holiday"]];
const typeOf = (num) => { const n = Math.floor(parseFloat(num) / 100) * 100; const t = TYPES.find((x) => x[0] === n); return t ? t[1] : "More channels"; };
B.liveTypes = TYPES;

// Favourites, recently watched and hidden types follow the person (same place the TV app keeps them)
const chPrefs = {
  async read() {
    const p = await B.prefs();
    let extras = {};
    try { extras = JSON.parse((p.CustomPrefs || {})["benflix.extras.v1"] || "{}"); } catch (e) {}
    const c = extras.channels || {};
    return { favourites: c.favourites || [], recent: c.recent || [], hidden: c.hidden || [] };
  },
  async save(prefs) {
    await B.savePrefs((c) => {
      let extras = {};
      try { extras = JSON.parse(c["benflix.extras.v1"] || "{}"); } catch (e) {}
      extras.channels = prefs;
      c["benflix.extras.v1"] = JSON.stringify(extras);
    });
  },
};
B.chPrefs = chPrefs;

B.channels = () => B.cached("channels", 60000, async () => {
  const [ch, progs] = await Promise.all([
    B.get(`/LiveTv/Channels?userId=${B.uid()}&EnableImages=true&EnableUserData=false&Fields=ChannelInfo&SortBy=SortName&AddCurrentProgram=true`),
    B.get(`/LiveTv/Programs?userId=${B.uid()}&MaxStartDate=${new Date(Date.now() + 3 * 3600000).toISOString()}&MinEndDate=${new Date().toISOString()}&Fields=Overview&EnableImages=false&EnableTotalRecordCount=false&Limit=20000`).catch(() => ({ Items: [] })),
  ]);
  const byCh = {};
  (progs.Items || []).forEach((p) => { (byCh[p.ChannelId] = byCh[p.ChannelId] || []).push(p); });
  Object.values(byCh).forEach((l) => l.sort((a, b) => new Date(a.StartDate) - new Date(b.StartDate)));
  const list = (ch.Items || []).map((c) => Object.assign(c, { _progs: byCh[c.Id] || [], _type: typeOf(c.ChannelNumber) }));
  list.sort((a, b) => parseFloat(a.ChannelNumber || 99999) - parseFloat(b.ChannelNumber || 99999));
  return list;
});
const nowOn = (c) => { const t = Date.now(); return c._progs.find((p) => new Date(p.StartDate) <= t && new Date(p.EndDate) > t) || c.CurrentProgram || null; };
const logo = (c, h) => (c.ImageTags && c.ImageTags.Primary ? B.img(c.Id, "Primary", { h: h || 120, tag: c.ImageTags.Primary }) : "");
const chCard = (c) => {
  const p = nowOn(c);
  B.items[c.Id] = c;
  return `<a class="chcard" href="#/watch/${c.Id}" data-id="${esc(c.Id)}"><div class="im">${logo(c) ? `<img src="${logo(c)}" alt="" loading="lazy">` : `<b>${esc(c.Name)}</b>`}</div>
    <div class="cap">${esc(c.ChannelNumber ? c.ChannelNumber + " · " : "")}${esc(c.Name)}<small>${esc(p ? p.Name : "")}</small></div></a>`;
};
B.chCard = chCard;
const minsLeft = (p) => Math.max(0, Math.round((new Date(p.EndDate) - Date.now()) / 60000));

B.route(/^\/live$/, async (m, nav) => {
  B.page().innerHTML = B.top("Live TV", DOORS.live.color) + `<section class="door-top" id="liveTop" style="height:auto;min-height:250px;padding-bottom:20px"><div class="info" id="liveInfo"></div></section><div class="rows" id="rows">${B.spinner()}</div>`;
  B.wireTop();
  let list, prefs;
  try { [list, prefs] = await Promise.all([B.channels(), chPrefs.read()]); }
  catch (e) { $("#rows").innerHTML = `<div class="wrap empty">Live TV isn't set up on the server yet.</div>`; return; }
  if (!B.alive(nav)) return;
  if (!list.length) { $("#rows").innerHTML = `<div class="wrap empty">No Live TV channels yet. Ben can add them in Jellyfin's Dashboard, under Live TV.</div>`; return; }
  const byName = {}; list.forEach((c) => { byName[c.Name] = c; });
  const favs = prefs.favourites.map((n) => byName[n]).filter(Boolean);
  const recent = prefs.recent.map((n) => byName[n]).filter(Boolean);
  const hidden = new Set(prefs.hidden);
  const shown = list.filter((c) => !hidden.has(c._type));
  const describe = (c) => {
    if (!$("#liveInfo")) return; // already left the page
    const p = nowOn(c), next = c._progs.find((x) => new Date(x.StartDate) > Date.now());
    $("#liveInfo").innerHTML = `<div class="meta">${esc(c.ChannelNumber || "")} · ${esc(c.Name)} · ${esc(c._type)}</div>
      <div class="t">${esc(p ? p.Name : c.Name)}</div>
      ${p ? `<div class="meta"><span>${B.clock(new Date(p.StartDate))} – ${B.clock(new Date(p.EndDate))}</span><span>${minsLeft(p)} min left</span>${next ? `<span>Next: ${esc(next.Name)} at ${B.clock(new Date(next.StartDate))}</span>` : ""}</div>` : ""}
      <div class="ov">${esc((p && p.Overview) || c.Overview || "Live channel")}</div>`;
  };
  // the guide: 2 hours across, starting now
  const start = new Date(); start.setMinutes(start.getMinutes() < 30 ? 0 : 30, 0, 0);
  const span = 2 * 3600000, t0 = start.getTime();
  const pctX = (t) => Math.max(0, Math.min(100, ((t - t0) / span) * 100));
  const guideRows = (chs) => chs.map((c) => `<div class="grow-ch"><a class="ch" href="#/watch/${c.Id}" data-id="${esc(c.Id)}"><span class="num">${esc(c.ChannelNumber || "")}</span>${logo(c, 72) ? `<img src="${logo(c, 72)}" alt="" loading="lazy">` : ""}<span class="nm">${esc(c.Name)}</span>${prefs.favourites.includes(c.Name) ? `<span class="star">★</span>` : ""}</a>
    <div class="progs">${(c._progs.length ? c._progs : [{ Name: "No guide for this channel", StartDate: new Date(t0).toISOString(), EndDate: new Date(t0 + span).toISOString(), _none: true }]).filter((p) => new Date(p.EndDate) > t0 && new Date(p.StartDate) < t0 + span).map((p) => {
      const a = pctX(new Date(p.StartDate)), b = pctX(new Date(p.EndDate)), on = new Date(p.StartDate) <= Date.now() && new Date(p.EndDate) > Date.now();
      return `<button class="prog ${on ? "now" : ""}" style="left:${a}%;width:calc(${b - a}% - 4px)" data-ch="${esc(c.Id)}" data-p="${esc(p.Id || "")}">${esc(p.Name)}<small>${esc(B.clock(new Date(p.StartDate)))}</small></button>`;
    }).join("")}</div></div>`).join("");
  const types = [...new Set(list.map((c) => c._type))];
  $("#rows").innerHTML = [
    B.row("Favourites", favs.map(chCard)) || `<section class="rowx"><h3>Favourites</h3><p class="wrap dim" style="margin:0">Star channels in the guide (press ★ beside a channel) and they show up here first.</p></section>`,
    B.row("Recently watched", recent.map(chCard)),
    `<section class="rowx"><h3>Guide</h3>
      <div class="gridbar" style="--accent:${DOORS.live.color};top:var(--bar)"><input class="search-in" id="chSearch" placeholder="Channel name or number">
      ${types.map((t) => `<button class="chip" data-type="${esc(t)}">${esc(t)}</button>`).join("")}
      <button class="chip" id="editTypes">Edit types</button></div>
      <div class="guide"><div class="ghead"><span></span><div class="times">${[0, 30, 60, 90].map((mm) => `<span style="left:${(mm / 120) * 100}%">${B.clock(new Date(t0 + mm * 60000))}</span>`).join("")}</div></div><div id="gRows">${guideRows(shown.slice(0, 120))}</div></div></section>`,
  ].join("");
  describe(favs[0] || recent[0] || shown[0] || list[0]);
  let timer;
  const point = (e) => { const el = e.target.closest("[data-id],[data-ch]"); if (!el) return; const c = list.find((x) => x.Id === (el.dataset.id || el.dataset.ch)); if (!c) return; clearTimeout(timer); timer = setTimeout(() => describe(c), 250); };
  B.page().addEventListener("mouseover", point);
  B.page().addEventListener("focusin", point);
  B.onLeave(() => { B.page().removeEventListener("mouseover", point); B.page().removeEventListener("focusin", point); });
  let filtered = shown;
  const redraw = () => { $("#gRows").innerHTML = guideRows(filtered.slice(0, 300)); };
  $("#chSearch").oninput = (e) => {
    const v = e.target.value.trim().toLowerCase();
    filtered = v ? list.filter((c) => (c.Name || "").toLowerCase().includes(v) || String(c.ChannelNumber || "").startsWith(v) || ((nowOn(c) || {}).Name || "").toLowerCase().includes(v)) : shown;
    redraw();
  };
  $("#rows").addEventListener("click", async (e) => {
    const t = e.target.closest("[data-type]");
    if (t) {
      const on = !t.classList.contains("on");
      $$("[data-type]").forEach((x) => x.classList.toggle("on", on && x === t));
      filtered = on ? shown.filter((c) => c._type === t.dataset.type) : shown;
      return redraw();
    }
    const pr = e.target.closest(".prog");
    if (pr) {
      const c = list.find((x) => x.Id === pr.dataset.ch), p = c && c._progs.find((x) => x.Id === pr.dataset.p);
      if (!c) return;
      if (!p || new Date(p.StartDate) <= Date.now()) return B.go(`#/watch/${c.Id}`);
      const fav = prefs.favourites.includes(c.Name);
      const panel = B.side(`<h3>${esc(p.Name)}</h3><p class="dim">${esc(c.Name)} · ${esc(B.clock(new Date(p.StartDate)))} – ${esc(B.clock(new Date(p.EndDate)))}</p><p>${esc(p.Overview || "")}</p>
        <button class="opt" data-a="remind">${I("bell")} Remind me</button><button class="opt" data-a="watch">${I("play")} Watch channel now</button><button class="opt" data-a="fav">${I("star")} ${fav ? "Take off favourites" : "Add to favourites"}</button>`);
      panel.onclick = async (ev) => {
        const b = ev.target.closest("[data-a]"); if (!b) return;
        B.closeAll();
        if (b.dataset.a === "watch") B.go(`#/watch/${c.Id}`);
        if (b.dataset.a === "fav") { await B.toggleFav(c, prefs); B.render(); }
        if (b.dataset.a === "remind") B.remind(c, p);
      };
    }
    if (e.target.id === "editTypes") {
      const p = B.side(`<h3>Channel types</h3><p class="dim">Hidden types don't show in the guide or when you change channels.</p>${types.map((t) => `<button class="opt ${hidden.has(t) ? "" : "on"}" data-t="${esc(t)}">${esc(t)}<small>${hidden.has(t) ? "Hidden" : "Shown"}</small></button>`).join("")}`, () => B.render());
      p.onclick = async (ev) => {
        const b = ev.target.closest("[data-t]"); if (!b) return;
        const t2 = b.dataset.t;
        hidden.has(t2) ? hidden.delete(t2) : hidden.add(t2);
        b.classList.toggle("on", !hidden.has(t2)); b.querySelector("small").textContent = hidden.has(t2) ? "Hidden" : "Shown";
        prefs.hidden = [...hidden]; await chPrefs.save(prefs).catch(() => {});
      };
    }
  });
});

B.toggleFav = async (c, prefs) => {
  prefs = prefs || (await chPrefs.read());
  const i = prefs.favourites.indexOf(c.Name);
  if (i >= 0) prefs.favourites.splice(i, 1); else prefs.favourites.push(c.Name);
  await chPrefs.save(prefs);
  B.toast(i >= 0 ? `${c.Name} taken off favourites.` : `${c.Name} added to favourites.`);
  return prefs;
};
// "Orbit starts in 0:45" corner note, a minute early, anywhere in Benflix (while this tab is open)
B.remind = (c, p) => {
  const at = new Date(p.StartDate).getTime() - 60000;
  const wait = Math.max(0, at - Date.now());
  setTimeout(() => {
    B.closeAll();
    const n = document.createElement("div");
    n.className = "modal"; n.style.cssText = "left:auto;right:24px;top:auto;bottom:24px;transform:none;width:360px";
    n.innerHTML = `<h3>${esc(p.Name)} starts soon</h3><p>On ${esc(c.Name)} at ${esc(B.clock(new Date(p.StartDate)))}</p><div class="acts"><a class="btn white" href="#/watch/${c.Id}">Go to channel</a><button class="btn" data-x>Dismiss</button></div>`;
    n.onclick = (e) => { if (e.target.closest("[data-x],a")) n.remove(); };
    document.body.appendChild(n);
  }, wait);
  B.toast(`We'll remind you a minute before ${p.Name} starts.`);
};

// ---------- watching a channel ----------
B.route(/^\/watch\/([\w-]+)/, async (m, nav) => {
  // Starting a movie, show, channel or game stops the music
  if (B.music) B.music.stop();
  const [list, prefs] = await Promise.all([B.channels(), chPrefs.read().catch(() => ({ favourites: [], recent: [], hidden: [] }))]);
  if (!B.alive(nav)) return;
  const hidden = new Set(prefs.hidden);
  const usable = list.filter((c) => !hidden.has(c._type) || c.Id === m[1]);
  const c = list.find((x) => x.Id === m[1]);
  if (!c) return B.go("#/live");
  B.lastPlayed = { id: c.Id, title: c.Name };
  // Recently watched (10)
  prefs.recent = [c.Name, ...prefs.recent.filter((n) => n !== c.Name)].slice(0, 10);
  chPrefs.save(prefs).catch(() => {});
  B.page().innerHTML = `<div class="player ui" id="pl"><video id="v" playsinline autoplay></video><div class="shadeB"></div><div class="center-msg" id="msg"></div>
    <div class="controls" id="banner"><div class="row2" style="align-items:flex-end;gap:22px">
      ${logo(c, 120) ? `<img src="${logo(c, 120)}" alt="" style="height:54px;max-width:120px;object-fit:contain">` : ""}
      <div class="grow"><div class="dim" style="font-weight:700">${esc(c.ChannelNumber || "")} · ${esc(c.Name)} ${prefs.favourites.includes(c.Name) ? "★" : ""}</div><div id="bNow" class="bnow">Tuning in…</div><div class="dim" id="bNext"></div></div>
      <button class="pbtn" id="pPause" title="Pause (space)">${I("pause")}</button>
      <button class="pbtn" id="pBroken" title="This channel is broken">${I("flag")}</button>
      <button class="pbtn" id="pGuide" title="Guide (left arrow)">${I("list")}</button>
      <button class="pbtn" id="pStar" title="Favourite (F)">${I("star")}</button>
      ${B.volume("pVol")}
      <button class="pbtn" id="pFull" title="Full screen">${I("full")}</button>
      <button class="pbtn" id="pBack" title="Back">${I("x")}</button>
    </div><div class="dim keyhint" style="font-size:13px;margin-top:8px">Up and down arrows change channel · Left opens the guide · type a number to jump</div></div></div>`;
  const pl = $("#pl"), v = $("#v");
  B.wireVolume($("#pVol").parentNode, v, "volume");
  const msg = (t) => { $("#msg").innerHTML = t ? `<div><div class="spin"></div><div>${esc(t)}</div></div>` : ""; };
  const paint = () => {
    const p = nowOn(c), next = c._progs.find((x) => new Date(x.StartDate) > Date.now());
    $("#bNow").textContent = p ? p.Name : c.Name;
    $("#bNext").textContent = p ? `${minsLeft(p)} min left${next ? ` · Next: ${next.Name}` : ""}` : "";
  };
  let hls = null, uiTimer, info = null;
  const poke = () => { pl.classList.add("ui"); clearTimeout(uiTimer); uiTimer = setTimeout(() => { if (!$(".sideguide")) pl.classList.remove("ui"); }, 5000); };
  pl.addEventListener("mousemove", poke);
  msg("Tuning in…");
  const tryUrl = (url, onFail) => {
    if (hls) { hls.destroy(); hls = null; }
    if (window.Hls && Hls.isSupported()) {
      hls = new Hls({ liveSyncDurationCount: 3 });
      hls.loadSource(url); hls.attachMedia(v);
      hls.on(Hls.Events.ERROR, (e, d) => { if (d.fatal) { if (onFail) { const f = onFail; onFail = null; f(); } else msg("This channel isn't working right now. Try another one."); } });
    } else { v.src = url; v.onerror = () => onFail && onFail(); }
    v.play().catch(() => {});
  };
  try {
    info = await B.jf("POST", `/Items/${c.Id}/PlaybackInfo?userId=${B.uid()}`, { DeviceProfile: B.deviceProfile(20000000), AutoOpenLiveStream: true, EnableDirectPlay: true, EnableDirectStream: true, EnableTranscoding: true });
    const src = (info.MediaSources || [])[0] || {};
    const viaJellyfin = () => {
      if (src.TranscodingUrl) tryUrl("api/web/stream" + src.TranscodingUrl + (src.TranscodingUrl.includes("api_key") ? "" : `&api_key=${enc(B.me.token)}`));
      else msg("This channel isn't working right now. Try another one.");
    };
    // The channel's own stream first (quick, like the TV app), then through Jellyfin if the browser can't reach it
    if (src.Path && /^https?:\/\/.*\.m3u8/i.test(src.Path) && location.protocol === "http:") tryUrl(src.Path, viaJellyfin);
    else viaJellyfin();
  } catch (e) { msg(e.message); }
  v.addEventListener("playing", () => msg(""));
  paint(); poke();
  B.every(30000, paint);
  const zap = (dir) => {
    const i = usable.findIndex((x) => x.Id === c.Id);
    const n = usable[(i + dir + usable.length) % usable.length];
    if (n) B.go(`#/watch/${n.Id}`);
  };
  const guide = () => {
    if ($(".sideguide")) return $(".sideguide").remove();
    const g = document.createElement("div");
    g.className = "sideguide";
    g.innerHTML = usable.map((x) => { const p = nowOn(x); return `<button data-go="${esc(x.Id)}" class="${x.Id === c.Id ? "on" : ""}">${logo(x, 64) ? `<img src="${logo(x, 64)}" alt="" loading="lazy">` : ""}<span><b>${esc(x.ChannelNumber || "")} ${esc(x.Name)}</b><br><span class="dim">${esc(p ? `${p.Name} · ${minsLeft(p)} min left` : "")}</span></span></button>`; }).join("");
    pl.appendChild(g);
    pl.classList.add("ui");
    g.onclick = (e) => { const b = e.target.closest("[data-go]"); if (b) B.go(`#/watch/${b.dataset.go}`); };
    const cur = $(".sideguide .on"); if (cur) cur.scrollIntoView({ block: "center" });
  };
  const full = () => (document.fullscreenElement ? document.exitFullscreen() : pl.requestFullscreen().catch(() => {}));
  // Pause live TV: it picks up where you left off (as far back as the channel keeps), like the TV app
  const pause = () => {
    if (v.paused) { v.play().catch(() => {}); $("#pPause").innerHTML = I("pause"); $("#pPause").title = "Pause (space)"; }
    else { v.pause(); $("#pPause").innerHTML = I("play"); $("#pPause").title = "Play (space)"; }
  };
  $("#pPause").onclick = pause;
  // "This channel is broken": tells Tower, whose nightly channel check swaps it for a working one
  $("#pBroken").onclick = async () => {
    if (!(await B.ask("Is this channel broken?", `Tower's nightly channel check will swap ${c.Name} for a working copy.`, "Yes, it's broken", "Cancel"))) return;
    const src = info && (info.MediaSources || [])[0];
    B.busy(null, async () => (await B.call("POST", "api/livetv/broken", { name: c.Name, number: String(c.ChannelNumber || ""), url: (src && /^https?:/.test(src.Path || "") && src.Path) || "" })).message || "Thanks! Tonight's check swaps it.");
  };
  $("#pGuide").onclick = guide;
  $("#pFull").onclick = full;
  $("#pBack").onclick = () => B.go("#/live");
  $("#pStar").onclick = async () => { await B.toggleFav(c, prefs); };
  let digits = "", digitTimer;
  const keys = (e) => {
    if (e.target.tagName === "INPUT" || $(".shade")) return;
    if (e.key === "ArrowUp") { e.preventDefault(); zap(1); }
    else if (e.key === "ArrowDown") { e.preventDefault(); zap(-1); }
    else if (e.key === "ArrowLeft") guide();
    else if (e.key === "f") $("#pStar").click();
    else if (e.key === " ") { e.preventDefault(); pause(); }
    else if (e.key === "Escape") { if ($(".sideguide")) $(".sideguide").remove(); else if (!document.fullscreenElement) B.go("#/live"); }
    else if (/^\d$/.test(e.key)) {
      digits += e.key; B.toast("Channel " + digits);
      clearTimeout(digitTimer);
      digitTimer = setTimeout(() => { const n = list.find((x) => String(x.ChannelNumber) === digits); digits = ""; if (n) B.go(`#/watch/${n.Id}`); else B.toast("No channel with that number."); }, 1200);
    } else return;
    poke();
  };
  document.addEventListener("keydown", keys);
  B.onLeave(() => {
    document.removeEventListener("keydown", keys);
    if (hls) hls.destroy();
    v.removeAttribute("src"); try { v.load(); } catch (e) {}
    const src = info && (info.MediaSources || [])[0];
    if (src && src.LiveStreamId) B.jf("POST", `/LiveStreams/Close?liveStreamId=${enc(src.LiveStreamId)}`).catch(() => {});
    if (info) B.jf("DELETE", `/Videos/ActiveEncodings?deviceId=${enc("benflix-web-" + B.me.device)}&playSessionId=${enc(info.PlaySessionId || "")}`).catch(() => {});
  });
});
