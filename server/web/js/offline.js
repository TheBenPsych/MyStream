/* Glowfin web: downloads for watching with no connection. Only in the Glowfin app for computers (Linux,
   Steam Deck, Windows); a normal browser tab can't keep files, so there these buttons don't show.
   A movie or episode page's More menu gets "Download to this computer", a show's gets "Download this season",
   and the Me page gets a Downloads tile. Tower turns the video into a format every computer plays (the same
   way it does for the browser player) and the app saves it piece by piece. When Tower can't be reached, the
   app's "Can't reach Tower" screen offers "Watch your downloads". */
(function () {
  "use strict";
  const G = window.glowfinDesktop;
  const D = G && G.downloads;
  const can = () => !!(D && B.me);
  // Picture quality of the saved copy (bits per second asked of Tower)
  const QUALITIES = [
    ["good", "Good", "Looks sharp, a sensible size. About 3.5 GB for a two hour movie.", 8000000],
    ["best", "Best", "As sharp as Tower has it, up to 1080p. About 9 GB for a two hour movie.", 20000000],
    ["small", "Small", "Fine on a small screen, quick to save. About 1.5 GB for a two hour movie.", 3000000],
  ];
  const abs = (rel) => new URL(rel, location.href).href;
  const withKey = (url) => url + (url.includes("?") ? "&" : "?") + "api_key=" + enc(B.me.token);
  let known = [];   // what the app has, kept fresh by its change notes
  const refresh = async () => { known = can() ? await D.list().catch(() => []) : []; return known; };
  const have = (id) => known.find((m) => m.id === id);
  if (D) {
    D.onChange((m) => {
      if (m) { const i = known.findIndex((x) => x.id === m.id); if (i >= 0) known[i] = m; else known.unshift(m); if (m.state === "done") B.toast(`${m.name} is downloaded.`); else if (m.state === "failed" && m.error !== "Stopped.") B.toast(`${m.name} couldn't be downloaded. ${m.error}`); }
      else refresh();
      if (/^#\/downloads/.test(location.hash)) draw();
    });
  }

  function pickQuality(title) {
    return new Promise((done) => {
      let picked = false;
      const p = B.side(`<h3>Download ${esc(title)}</h3><p class="dim">Saved on this computer to watch with no connection. Tower makes the copy, so a movie takes a few minutes.</p>
        ${QUALITIES.map(([k, n, line]) => `<button class="opt" data-q="${k}" style="flex-direction:column;align-items:flex-start;gap:2px;margin-top:10px"><span>${n}</span><small class="dim" style="font-weight:500">${line}</small></button>`).join("")}`, () => { if (!picked) done(null); });
      p.addEventListener("click", (e) => { const b = e.target.closest("[data-q]"); if (!b) return; picked = true; B.closeAll(); done(b.dataset.q); });
    });
  }

  // Asks Tower how to send this video as pieces every computer can play, and hands the job to the app
  async function begin(it, quality) {
    const q = QUALITIES.find((x) => x[0] === quality) || QUALITIES[0];
    const device = (B.me.device || "web") + "-dl"; // its own name, so watching something else doesn't stop it
    const profile = B.deviceProfile(q[3]);
    profile.DirectPlayProfiles = []; // always as pieces, never the raw file
    const ask = (sub) => B.jf("POST", `/Items/${it.Id}/PlaybackInfo?userId=${B.uid()}`, {
      DeviceProfile: profile, StartTimeTicks: 0, MaxStreamingBitrate: q[3], SubtitleStreamIndex: sub,
      EnableDirectPlay: false, EnableDirectStream: true, EnableTranscoding: true, AutoOpenLiveStream: false,
    }, { device });
    let info = await ask(undefined);
    let source = (info.MediaSources || [])[0];
    if (!source) throw new Error("Tower couldn't find a way to send this one.");
    // The same subtitle choice the player would make (English for other languages, and so on)
    const streams = source.MediaStreams || [];
    const sub = B.autoSub ? B.autoSub(streams, source.DefaultSubtitleStreamIndex ?? -1, source.DefaultAudioStreamIndex) : -1;
    if (sub !== (source.DefaultSubtitleStreamIndex ?? -1)) { info = await ask(sub); source = (info.MediaSources || [])[0] || source; }
    if (!source.TranscodingUrl) throw new Error("Tower couldn't make a copy of this one.");
    const burned = /SubtitleMethod=Encode/i.test(source.TranscodingUrl);
    const subs = (source.MediaStreams || []).filter((s) => s.Type === "Subtitle" && s.DeliveryMethod === "External" && s.DeliveryUrl && /vtt|srt|subrip/i.test(s.DeliveryUrl + " " + (s.Codec || "")))
      .map((s) => ({ url: abs("api/web/stream" + withKey(s.DeliveryUrl.replace(/\.(srt|subrip|ass|ssa)(\?|$)/i, ".vtt$2"))), label: (B.subLabel ? B.subLabel(s, source.MediaStreams) : "") || s.DisplayTitle || s.Language || "Subtitles", lang: (s.Language || "").slice(0, 3), on: s.Index === sub }));
    const ep = it.Type === "Episode";
    return D.start({
      id: it.Id, kind: ep ? "episode" : "movie", name: it.Name, show: ep ? it.SeriesName || "" : "", showId: ep ? it.SeriesId || "" : "",
      season: ep ? it.ParentIndexNumber || 0 : 0, episode: ep ? it.IndexNumber || 0 : 0, year: it.ProductionYear || 0,
      minutes: Math.round((it.RunTimeTicks || 0) / B.TICKS / 60), about: it.Overview || "", rating: it.OfficialRating || "", quality: q[1],
      user: B.me.name, userId: B.me.id, kids: !!B.me.kids, burned,
      url: abs("api/web/stream" + withKey(source.TranscodingUrl)),
      posterUrl: abs(B.img(ep && it.SeriesId ? it.SeriesId : it.Id, "Primary", { h: 600 })),
      subs,
    });
  }

  B.offline = {
    can,
    // "Download to this computer" for one movie or episode
    async one(it) {
      if (!can()) return;
      await refresh();
      const had = have(it.Id);
      if (had && had.state === "done") return B.toast(`${it.Name} is already downloaded. It's in Me, Downloads.`);
      if (had && (had.state === "queued" || had.state === "downloading")) return B.toast(`${it.Name} is already downloading.`);
      const q = await pickQuality(it.Name);
      if (!q) return;
      await B.busy(null, async () => { await begin(it, q); return `Downloading ${it.Name}. See how it's going in Me, Downloads.`; });
    },
    // "Download this season": every episode of it that isn't here yet
    async season(show, seasonId, seasonName) {
      if (!can()) return;
      await refresh();
      const r = await B.get(`/Shows/${show.Id}/Episodes?userId=${B.uid()}${seasonId ? `&SeasonId=${enc(seasonId)}` : ""}&Fields=Overview`);
      const eps = (r.Items || []).filter((e) => e.LocationType !== "Virtual" && !(have(e.Id) && have(e.Id).state !== "failed"));
      if (!eps.length) return B.toast("Every episode of it is already downloaded or downloading.");
      const q = await pickQuality(`${eps.length} episode${eps.length === 1 ? "" : "s"} of ${show.Name}${seasonName ? ", " + seasonName : ""}`);
      if (!q) return;
      await B.busy(null, async () => {
        let n = 0;
        for (const e of eps) { try { await begin(e, q); n++; } catch (err) { /* one bad episode doesn't stop the rest */ } }
        return n ? `Downloading ${n} episode${n === 1 ? "" : "s"}, one after another. See how it's going in Me, Downloads.` : "Tower couldn't make copies of those.";
      });
    },
    // A line for a More menu, or nothing in a normal browser
    menu(it) {
      if (!can() || !it || !/^(Movie|Episode|Video)$/.test(it.Type)) return null;
      return { label: "Download to this computer", icon: "dl", onClick: () => B.offline.one(it).catch((e) => B.toast(B.say(e))) };
    },
  };

  // ---------- Me > Downloads ----------
  const size = (b) => (b >= 1073741824 ? (b / 1073741824).toFixed(1) + " GB" : Math.max(1, Math.round((b || 0) / 1048576)) + " MB");
  const stateLine = (m) => (m.state === "done" ? `${m.quality || "Downloaded"} · ${size(m.bytes)}` : m.state === "downloading" ? `Downloading · ${m.progress || 0}%` : m.state === "queued" ? "Waiting its turn" : `Didn't finish. ${m.error || ""}`);
  const title = (m) => (m.kind === "episode" ? `${m.show} · S${m.season} E${m.episode} · ${m.name}` : m.name);
  function draw() {
    const box = $("#dlList");
    if (!box) return;
    if (!known.length) { box.innerHTML = B.emptyPage("dl", "Nothing downloaded yet", "Open a movie or an episode, pick More, then Download to this computer. It's kept here to watch with no connection.", `<a class="btn white" href="#/movies">Go to Movies</a>`); return; }
    const total = known.filter((m) => m.state === "done").reduce((n, m) => n + (m.bytes || 0), 0);
    box.innerHTML = `<p class="dim" style="margin:0 0 14px">${known.filter((m) => m.state === "done").length} ready to watch with no connection · ${size(total)} on this computer</p>`
      + known.map((m) => `<div class="setrow dlrow" data-dl="${m.id}"><div class="grow"><div class="t">${esc(title(m))}</div><small class="dim" style="display:block;margin-top:2px">${esc(stateLine(m))}${m.user && m.userId !== B.me.id ? ` · ${esc(m.user)}` : ""}</small>${m.state === "downloading" ? `<div class="dlbar"><i style="width:${m.progress || 0}%"></i></div>` : ""}</div>
        ${m.state === "done" ? `<button class="btn small white" data-a="play">Play</button>` : ""}${m.state === "failed" ? `<button class="btn small white" data-a="again">Try again</button>` : ""}<button class="btn small" data-a="remove">${m.state === "done" || m.state === "failed" ? "Delete" : "Stop"}</button></div>`).join("");
  }
  B.route(/^\/downloads/, async (m, nav) => {
    if (!can()) return B.go("#/me");
    B.page().innerHTML = B.top("Downloads", null) + `<div class="wrap" style="max-width:900px"><div class="h1">Downloads</div><p class="dim" style="margin:4px 0 20px">Kept on this computer. When Tower can't be reached, Glowfin offers "Watch your downloads".</p><div id="dlList">${B.spinner("list")}</div></div>`;
    B.wireTop();
    await refresh();
    if (!B.alive(nav)) return;
    draw();
    const click = async (e) => {
      const b = e.target.closest("[data-a]"); if (!b) return;
      const id = b.closest("[data-dl]").dataset.dl, it = have(id);
      if (!it) return;
      if (b.dataset.a === "play") return D.open(id);
      if (b.dataset.a === "remove") {
        if (it.state === "done" && !(await B.ask(`Delete ${it.name} from this computer?`, "It stays on Tower. You can download it again any time.", "Delete", "Keep"))) return;
        await D.remove(id); await refresh(); return draw();
      }
      if (b.dataset.a === "again") {
        try { const full = await B.get(`/Users/${B.uid()}/Items/${id}`); await begin(full, (QUALITIES.find((x) => x[1] === it.quality) || QUALITIES[0])[0]); B.toast(`Downloading ${it.name} again.`); }
        catch (err) { B.toast(err.message || "Couldn't start it."); }
      }
    };
    B.page().addEventListener("click", click);
    B.onLeave(() => B.page().removeEventListener("click", click));
  });
  refresh();
})();
