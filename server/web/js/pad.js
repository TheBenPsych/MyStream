/* Glowfin web: controller mode. Plug in or pair a game controller (Xbox, PlayStation, a Steam Deck's own
   buttons) and the whole of Glowfin works from it, like a remote on the TV:
     stick or D-pad  move the highlight          A  pick            B  back
     Y  search       Start  the menu             LB / RB  tab left and right
     LT / RT (or the right stick)  scroll the page
   Typing boxes open an on-screen keyboard. In the video player A is play and pause, left and right skip
   10 seconds, and up, down or X moves onto the buttons (Live TV: up and down change the channel). Console games and the Lobby read the controller
   themselves, so Glowfin steps aside there: hold Start + Select for a second to leave a game.
   The mouse, touch and keyboard keep working the whole time. Arrow keys on a keyboard move the highlight
   too once a controller has been used (or in the Glowfin app on a Steam Deck). */
(function () {
  "use strict";
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const html = document.documentElement;
  const FOCUSABLE = 'a[href], button, input:not([type="hidden"]), select, textarea, [tabindex]:not([tabindex="-1"]), [role="button"]';
  // Pages that read the controller themselves
  const GAME = /^#\/(playgame|lobby\/)/;
  const PLAYER = /^#\/(play|watch)\//;
  const READER = /^#\/read\//;
  const hash = () => location.hash || "";

  // One of Glowfin's own arcade games is on screen and not showing its "game over" card
  const arcadeOwns = () => /^#\/playb\//.test(hash()) && !!$("#page canvas") && !$(".bg-over") && !$(".dropdown, .modal, .side, .menu-card");
  let on = false;        // controller mode is showing (a controller button or stick was used last)
  let quietUntil = 0;    // a pop-up just opened or closed: don't let a held button act twice

  // ---------- turning the mode on and off ----------
  function setOn(v) {
    if (on === v) return;
    on = v;
    html.classList.toggle("pad-on", v);
    if (!v) { const f = $(".pad-focus"); if (f) f.classList.remove("pad-focus"); }
  }
  // The mouse takes over again the moment it is really moved or clicked
  let lastMouse = null;
  window.addEventListener("mousemove", (e) => {
    if (!on) return;
    if (lastMouse && Math.abs(e.screenX - lastMouse[0]) + Math.abs(e.screenY - lastMouse[1]) > 12) setOn(false);
    lastMouse = [e.screenX, e.screenY];
  }, true);
  window.addEventListener("mousedown", (e) => { if (on && e.isTrusted) setOn(false); }, true);

  // ---------- finding what can be picked ----------
  function visible(el) {
    if (!el.isConnected || el.disabled || el.hidden) return false;
    if (el.closest("[hidden], [inert], .hidden, [aria-hidden='true']")) return false;
    const r = el.getBoundingClientRect();
    if (r.width < 4 || r.height < 4) return false;
    const st = getComputedStyle(el);
    if (st.visibility === "hidden" || st.display === "none" || +st.opacity === 0) return false;
    // (the side menu ignores the mouse in controller mode, but the controller can still go there)
    if (st.pointerEvents === "none" && !el.closest(".brail")) return false;
    return true;
  }
  // A pop-up keeps the highlight inside itself until it closes
  function scope() {
    const osk = $(".osk");
    if (osk) return osk;
    const tops = $$(".bg-over, .dropdown, .modal, .side, .menu-card, .player .panel, .keys-help, .sideguide").filter(visible);
    if (tops.length) return tops[tops.length - 1];
    if (PLAYER.test(hash()) && $(".player")) return $(".player");
    return document.body;
  }
  function candidates(root) {
    const inShade = root === document.body && $(".shade");
    return $$(FOCUSABLE, root).filter((el) => visible(el) && !(inShade && !el.closest(".dropdown, .modal, .side, .menu-card")));
  }
  // Parts of a row that are scrolled out of sight still count; things hidden behind a closed edge don't
  function clipped(el, r) {
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      const st = getComputedStyle(p);
      if (st.overflowX === "visible" && st.overflowY === "visible") continue;
      const pr = p.getBoundingClientRect();
      const scrollX = /auto|scroll/.test(st.overflowX), scrollY = /auto|scroll/.test(st.overflowY);
      if (!scrollX && st.overflowX !== "visible" && (r.right <= pr.left + 2 || r.left >= pr.right - 2)) return true;
      if (!scrollY && st.overflowY !== "visible" && (r.bottom <= pr.top + 2 || r.top >= pr.bottom - 2)) return true;
    }
    return false;
  }

  // ---------- moving the highlight ----------
  function focusEl(el) {
    if (!el) return false;
    const old = $(".pad-focus");
    if (old && old !== el) old.classList.remove("pad-focus");
    try { el.focus({ preventScroll: true, focusVisible: true }); } catch (e) { el.focus(); }
    if (document.activeElement !== el) return false;
    el.classList.add("pad-focus");
    remember(el);
    el.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "smooth" });
    // The top of a page should come back into view with its first row
    if (el.getBoundingClientRect().top < 260 && window.scrollY < 400 && !el.closest(".side, .modal, .dropdown, .brail, .player")) window.scrollTo({ top: 0, behavior: "smooth" });
    return true;
  }
  document.addEventListener("focusout", (e) => { if (e.target && e.target.classList) e.target.classList.remove("pad-focus"); }, true);
  document.addEventListener("focusin", (e) => { if (on && e.target && e.target.classList && e.target !== document.body) e.target.classList.add("pad-focus"); }, true);

  // Where the highlight was on each page, so Back lands on the poster you came from; and what opened a pop-up,
  // so closing it goes back to that button
  const spots = new Map();
  let opener = null, lastHref = "";
  function remember(el) {
    if (el.closest(".osk, .dropdown, .modal, .side, .menu-card")) return;
    opener = el;
    if (el.closest(".brail, .top")) return;
    lastHref = el.tagName === "A" ? el.getAttribute("href") || "" : "";
    // Things with no name of their own (an On/Off button in Settings) are remembered as "the 3rd button of that kind"
    const kind = el.tagName.toLowerCase() + Array.from(el.classList).filter((c) => !/^(pad-focus|on|active|off)$/.test(c)).map((c) => "." + CSS.escape(c)).join("");
    let nth = -1;
    if (!el.id && !el.dataset.id && !el.getAttribute("href")) { try { nth = $$(kind).indexOf(el); } catch (e) { nth = -1; } }
    spots.set(hash(), { id: el.dataset.id || "", href: el.getAttribute("href") || "", dom: el.id || "", kind, nth });
    if (spots.size > 60) spots.delete(spots.keys().next().value);
  }
  function recall() {
    const sp = spots.get(hash());
    if (!sp) return null;
    const pick = (sel) => { try { return $$(sel).find((el) => visible(el) && !el.closest(".brail, .top")); } catch (e) { return null; } };
    if (sp.nth >= 0) { try { const el = $$(sp.kind)[sp.nth]; return el && visible(el) ? el : null; } catch (e) { return null; } }
    return (sp.dom && pick("#" + CSS.escape(sp.dom))) || (sp.id && pick(`[data-id="${CSS.escape(sp.id)}"]`)) || (sp.href && pick(`a[href="${CSS.escape(sp.href)}"]`)) || null;
  }
  // The highlighted thing vanished (the page redrew, a pop-up closed): put the highlight somewhere sensible
  function reland() {
    if (current()) return true;
    const root = scope();
    if (root !== document.body) return focusEl(candidates(root)[0]);
    if (opener && opener.isConnected && visible(opener)) return focusEl(opener);
    return focusEl(recall() || firstInPage());
  }
  function firstInPage() {
    const root = scope();
    const list = candidates(root);
    if (root !== document.body) return list[0];
    const main = list.filter((el) => !el.closest(".brail, .top, #toast"));
    const inView = (el) => { const r = el.getBoundingClientRect(); return r.bottom > 0 && r.top < innerHeight && r.right > 0 && r.left < innerWidth; };
    return main.find(inView) || main[0] || list[0];
  }
  function current() {
    const a = document.activeElement;
    const root = scope();
    return a && a !== document.body && root.contains(a) && visible(a) ? a : null;
  }
  function move(dir) {
    const from = current();
    if (!from) return reland();
    const a = from.getBoundingClientRect();
    const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
    const horiz = dir === "left" || dir === "right";
    const fromRail = !!from.closest(".brail");
    let best = null, bestScore = Infinity;
    candidates(scope()).forEach((el) => {
      if (el === from || el.contains(from) || from.contains(el)) return;
      // The side menu is its own column: only Left goes into it, and only Right comes out of it
      const inRail = !!el.closest(".brail");
      if (inRail !== fromRail && !(inRail ? dir === "left" : dir === "right")) return;
      const r = el.getBoundingClientRect();
      const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      // It has to really be on that side
      const ahead = dir === "left" ? a.left - r.right : dir === "right" ? r.left - a.right : dir === "up" ? a.top - r.bottom : r.top - a.bottom;
      const along = dir === "left" ? ax - cx : dir === "right" ? cx - ax : dir === "up" ? ay - cy : cy - ay;
      if (along <= 1 || ahead < -Math.min(horiz ? a.width : a.height, horiz ? r.width : r.height) * 0.5) return;
      // How far off to the side: nothing when the two overlap on the other axis
      const off = horiz ? Math.max(0, Math.max(r.top - a.bottom, a.top - r.bottom)) : Math.max(0, Math.max(r.left - a.right, a.left - r.right));
      const overlap = off === 0;
      if (clipped(el, r)) return;
      const gap = Math.max(0, ahead);
      // Straight ahead wins; something off to the side has to be a lot closer to be chosen instead
      // (sideways: the thing level with you; up and down: the thing whose left edge lines up, so a wide box
      // above a row of posters drops onto the first poster, not the middle one)
      let score = horiz
        ? gap + off * 4 + (overlap ? 0 : 400) + Math.abs(cy - ay)
        : gap + off * 2.5 + (overlap ? 0 : 60) + Math.min(Math.abs(r.left - a.left), Math.abs(cx - ax)) * 0.3;
      // Going sideways stays in the same row: the end of a row is the end, it never hops to the row below
      if (horiz && !overlap && off > Math.min(a.height, r.height) * 0.75) return;
      if (score < bestScore) { bestScore = score; best = el; }
    });
    // Left off the edge of a page opens the side menu on the page you're in, not on whatever icon is level with you
    if (best && best.closest(".brail") && !from.closest(".brail")) best = $(".brail .rl-i.on") || best;
    if (best) return focusEl(best);
    // Nothing that way: scroll the page a little, so text below the last button can still be read
    if (!horiz && scope() === document.body) window.scrollBy({ top: dir === "down" ? 240 : -240, behavior: "smooth" });
    return false;
  }

  // ---------- pressing things ----------
  function key(name, target) {
    const t = target || document.activeElement || document.body;
    const code = name === " " ? "Space" : name.length === 1 ? "Key" + name.toUpperCase() : name;
    const e = new KeyboardEvent("keydown", { key: name, code, bubbles: true, cancelable: true });
    t.dispatchEvent(e);
    t.dispatchEvent(new KeyboardEvent("keyup", { key: name, code, bubbles: true }));
    return e.defaultPrevented;
  }
  const isText = (el) => !!el && ((el.tagName === "INPUT" && /^(text|search|password|email|url|tel|number|)$/i.test(el.getAttribute("type") || "")) || el.tagName === "TEXTAREA");
  function press() {
    const el = current();
    if (!el) return reland();
    if (isText(el)) return osk.open(el);
    if (el.tagName === "SELECT") return pickFrom(el);
    if (el.tagName === "INPUT" && el.type === "range") return; // left and right change it
    if (key("Enter", el)) return;
    el.click();
  }
  // A drop-down box: shown as Glowfin's own list, since a controller can't open the system one
  function pickFrom(sel) {
    const B = window.B;
    const options = Array.from(sel.options).filter((o) => !o.disabled).map((o) => ({ label: o.textContent.trim(), value: o.value }));
    if (!B || !B.dropdown || !options.length) return;
    B.dropdown(sel, options, sel.value, (v) => { sel.value = v; sel.dispatchEvent(new Event("input", { bubbles: true })); sel.dispatchEvent(new Event("change", { bubbles: true })); focusEl(sel); });
  }
  function slide(el, dir) {
    const before = el.value;
    dir > 0 ? el.stepUp() : el.stepDown();
    if (el.value !== before) { el.dispatchEvent(new Event("input", { bubbles: true })); el.dispatchEvent(new Event("change", { bubbles: true })); }
  }
  function back() {
    if (osk.el) return osk.close();
    const B = window.B;
    if ($(".dropdown, .modal, .side, .menu-card, .shade, .keys-help, .sideguide, .player .panel")) { key("Escape", document.activeElement || document.body); quietUntil = Date.now() + 250; return; }
    const a = document.activeElement;
    if (/^#\/(home|who|signin)/.test(hash()) || !hash()) { const m = $(".brail .rl-i.on") || $(".brail .rl-i"); if (m && !(a && a.closest && a.closest(".brail"))) focusEl(m); return; }
    if (B && B.back) B.back(); else history.back();
  }
  function tab(dir) {
    // The word tabs at the top of a page (For you | Consoles | ...), or the chips of a filter bar
    const g = tabGroup();
    if (!g) return;
    const items = $$("a, button", g).filter(visible);
    if (items.length < 2) return;
    let i = items.findIndex((x) => x.classList.contains("on") || x.classList.contains("active") || x.getAttribute("aria-selected") === "true");
    if (i < 0) i = 0;
    const n = items[(i + dir + items.length) % items.length];
    n.click();
    // The page usually redraws for the new tab: stay on the tabs, on the one now showing
    tabUntil = Date.now() + 1500;
    setTimeout(landOnTab, 60);
  }
  let tabUntil = 0;
  function tabGroup() { return $$(".ptabs, .tabs, .tp-tabs, .seg, [role='tablist']").filter(visible)[0]; }
  function landOnTab() {
    const g = tabGroup();
    if (!on || !g) return false;
    const items = $$("a, button", g).filter(visible);
    return focusEl(items.find((x) => x.classList.contains("on") || x.classList.contains("active") || x.getAttribute("aria-selected") === "true") || items[0]);
  }

  // ---------- the on-screen keyboard ----------
  const osk = {
    el: null, target: null, shift: false, sym: false,
    rows() {
      const L = this.sym
        ? ["1234567890", "@#$%&*-+=/", "()!?:;'\",.", "_~<>[]{}\\|"]
        : ["1234567890", "qwertyuiop", "asdfghjkl'", "zxcvbnm,.-"];
      return L.map((r) => r.split("").map((c) => (this.shift && !this.sym ? c.toUpperCase() : c)));
    },
    open(input) {
      this.close(true);
      this.target = input; this.shift = false; this.sym = false;
      const d = document.createElement("div");
      d.className = "osk";
      d.setAttribute("role", "dialog");
      d.setAttribute("aria-label", "Keyboard");
      d.addEventListener("click", (e) => { const b = e.target.closest("button"); if (b) this.hit(b.dataset.k); });
      d.addEventListener("mousedown", (e) => e.preventDefault()); // a mouse or finger on a key doesn't steal the box
      document.body.appendChild(d);
      this.el = d;
      this.draw();
      focusEl($('[data-k="g"]', d) || $("button", d));
      quietUntil = Date.now() + 250;
    },
    draw(keep) {
      const t = this.target;
      const shown = t.type === "password" ? "•".repeat(t.value.length) : t.value;
      const label = t.getAttribute("placeholder") || t.getAttribute("aria-label") || (t.labels && t.labels[0] && t.labels[0].textContent) || "";
      const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
      this.el.innerHTML = `<div class="osk-in"><small>${esc(label)}</small><div class="osk-val">${esc(shown)}<i></i></div></div>`
        + this.rows().map((r) => `<div class="osk-row">${r.map((c) => `<button type="button" data-k="${esc(c)}">${esc(c)}</button>`).join("")}</div>`).join("")
        + `<div class="osk-row osk-last"><button type="button" data-k="shift" class="${this.shift ? "on" : ""}">Shift</button><button type="button" data-k="sym">${this.sym ? "abc" : "#+="}</button><button type="button" data-k="space" class="wide">Space <small>Y</small></button><button type="button" data-k="del">Delete <small>X</small></button><button type="button" data-k="done" class="go">Done <small>Start</small></button></div>`;
      if (keep) focusEl($(`[data-k="${CSS.escape(keep)}"]`, this.el) || $("button", this.el));
    },
    put(v) {
      const t = this.target;
      const max = t.maxLength > 0 ? t.maxLength : Infinity;
      t.value = String(v).slice(0, max);
      t.dispatchEvent(new Event("input", { bubbles: true }));
      const box = $(".osk-val", this.el);
      if (box) box.firstChild ? (box.firstChild.nodeValue = t.type === "password" ? "•".repeat(t.value.length) : t.value) : box.insertAdjacentText("afterbegin", t.type === "password" ? "•".repeat(t.value.length) : t.value);
    },
    hit(k) {
      if (!this.el || !k) return;
      const t = this.target;
      if (k === "shift") { this.shift = !this.shift; return this.draw("shift"); }
      if (k === "sym") { this.sym = !this.sym; return this.draw("sym"); }
      if (k === "del") return this.put(t.value.slice(0, -1));
      if (k === "space") return this.put(t.value + " ");
      if (k === "done") return this.done();
      this.put(t.value + k);
      if (this.shift && !this.sym) { this.shift = false; this.draw(k.toLowerCase()); }
    },
    done() {
      const t = this.target;
      this.close();
      t.dispatchEvent(new Event("change", { bubbles: true }));
      // Enter in the box: searches, signs in, or saves, whatever the page does with it
      if (!key("Enter", t) && t.form) { if (t.form.requestSubmit) t.form.requestSubmit(); else t.form.submit(); }
    },
    close(silent) {
      if (!this.el) return;
      this.el.remove(); this.el = null;
      const t = this.target; this.target = null;
      if (!silent && t && t.isConnected) focusEl(t);
      quietUntil = Date.now() + 250;
    },
  };

  // ---------- the video player ----------
  function inPlayerButtons() { const a = document.activeElement; return !!(a && a !== document.body && a.closest && a.closest(".player") && a.tagName !== "VIDEO"); }
  function wakePlayer() { const pl = $(".player"); if (pl) pl.dispatchEvent(new MouseEvent("mousemove", { bubbles: true })); }
  function playerAct(a) {
    const pl = $(".player");
    if (!pl) return false;
    if ($(".dropdown, .modal, .side, .menu-card, .player .panel, .keys-help, .sideguide", document) || osk.el) return false; // a list is open: normal moving and picking
    const onButtons = inPlayerButtons();
    wakePlayer();
    if (onButtons) {
      if (a === "back") { document.activeElement.blur(); return true; }
      return false; // move between the buttons and press them as usual
    }
    if (a === "ok" || a === "start") { key(" ", document.body); return true; }
    if (a === "x") { focusEl($("#pPlay") || candidates(pl)[0]); return true; }
    // Live TV: up and down change the channel, left opens the guide (X goes onto the buttons)
    if (/^#\/watch\//.test(hash())) {
      if (a === "up" || a === "down" || a === "left") { key(a === "up" ? "ArrowUp" : a === "down" ? "ArrowDown" : "ArrowLeft", document.body); return true; }
      if (a === "right") return true;
      if (a === "y") { key("f", document.body); return true; }
    }
    if (a === "left" || a === "right") { key(a === "left" ? "ArrowLeft" : "ArrowRight", document.body); return true; }
    if (a === "up" || a === "down") { focusEl($("#pPlay") || candidates(pl)[0]); return true; }
    if (a === "back") { key("Escape", document.body); return true; }
    if (a === "y") { key("s", document.body); return true; }
    if (a === "lb") { const b = $("#pPv:not([disabled])"); if (b) b.click(); return true; }
    if (a === "rb") { const b = $("#pNx:not([disabled])"); if (b) b.click(); return true; }
    if (a === "lt" || a === "rt") { key(a === "lt" ? "ArrowDown" : "ArrowUp", document.body); return true; }
    return false;
  }

  // ---------- one press of anything ----------
  function act(a) {
    if (Date.now() < quietUntil && (a === "ok" || a === "back")) return;
    if (osk.el) {
      if (a === "x") return osk.hit("del");
      if (a === "y") return osk.hit("space");
      if (a === "start") return osk.done();
      if (a === "lb") return osk.hit("shift");
      if (a === "rb") return osk.hit("sym");
    } else if (PLAYER.test(hash())) {
      if (playerAct(a)) return;
    } else if (READER.test(hash()) && !$(".dropdown, .modal, .side, .menu-card")) {
      // The book reader turns pages with the arrow keys already
      const k = { left: "ArrowLeft", right: "ArrowRight", up: "ArrowUp", down: "ArrowDown", lb: "ArrowLeft", rb: "ArrowRight" }[a];
      if (k) { key(k, document.activeElement && document.activeElement !== document.body ? document.activeElement : $("#bkReader") || document.body); return; }
      if (a === "back") { key("Escape", document.body); return; }
    }
    if (a === "left" || a === "right") {
      const el = current();
      if (el && el.tagName === "INPUT" && el.type === "range") return slide(el, a === "right" ? 1 : -1);
      // Pages with their own arrow keys (the rail, a puzzle's answers) get first go
      if (el && key(a === "left" ? "ArrowLeft" : "ArrowRight", el)) { reclass(); return; }
      return move(a);
    }
    if (a === "up" || a === "down") {
      const el = current();
      if (el && !isText(el) && key(a === "up" ? "ArrowUp" : "ArrowDown", el)) { reclass(); return; }
      return move(a);
    }
    if (a === "ok") return press();
    if (a === "back") return back();
    if (a === "y") { if (window.B && window.B.me && !/^#\/(who|signin|search)/.test(hash())) window.B.go("#/search"); return; }
    if (a === "start") { const m = $(".brail .rl-i.on") || $(".brail .rl-i"); if (m && visible(m)) focusEl(m); return; }
    if (a === "lb") return tab(-1);
    if (a === "rb") return tab(1);
  }
  // After a page's own arrow keys moved the highlight, the ring follows it
  function reclass() { const a = document.activeElement; if (on && a && a !== document.body) { const o = $(".pad-focus"); if (o && o !== a) o.classList.remove("pad-focus"); a.classList.add("pad-focus"); a.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "smooth" }); } }

  // ---------- reading the controller ----------
  const BTN = { 0: "ok", 1: "back", 2: "x", 3: "y", 4: "lb", 5: "rb", 9: "start", 12: "up", 13: "down", 14: "left", 15: "right" };
  const REPEATS = { up: 1, down: 1, left: 1, right: 1, x: 1 };
  const held = {};   // action -> { since, last }
  let leaveSince = 0, polling = false, told = false, lostSince = 0;
  function poll() {
    const pads = Array.from((navigator.getGamepads && navigator.getGamepads()) || []).filter((p) => p && p.connected);
    if (!pads.length) { polling = false; return; }
    requestAnimationFrame(poll);
    if (document.hidden || !document.hasFocus()) return;
    const now = performance.now();
    const down = {};
    let scroll = 0, start = false, select = false;
    pads.forEach((p) => {
      p.buttons.forEach((b, i) => { if (b && (b.pressed || b.value > 0.5)) { if (BTN[i]) down[BTN[i]] = true; if (i === 9) start = true; if (i === 8) select = true; if (i === 6) down.lt = true; if (i === 7) down.rt = true; } });
      const x = p.axes[0] || 0, y = p.axes[1] || 0;
      if (Math.abs(x) > 0.6 || Math.abs(y) > 0.6) { if (Math.abs(x) > Math.abs(y)) down[x < 0 ? "left" : "right"] = true; else down[y < 0 ? "up" : "down"] = true; }
      const ry = p.axes[3] || 0;
      if (Math.abs(ry) > 0.25) scroll += ry;
    });
    // A game is running: it has the controller. Holding Start + Select for a second leaves it.
    // Glowfin's own arcade games read the controller too (A plays, Start pauses). B leaves the game.
    if (arcadeOwns()) {
      if (!on) setOn(true);
      if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();
      const quit = $("#bgQuit");
      if (down.back && !held.back && quit) { held.back = { since: now, last: now }; quit.click(); } else if (!down.back) delete held.back;
      ["ok", "up", "down", "left", "right", "start", "x", "y", "lb", "rb"].forEach((k) => { if (down[k]) held[k] = { since: now, last: now + 400 }; else delete held[k]; });
      return;
    }
    if (GAME.test(hash())) {
      Object.keys(held).forEach((k) => delete held[k]);
      if (start && select) { if (!leaveSince) leaveSince = now; else if (now - leaveSince > 1000) { leaveSince = 0; if (window.B && window.B.back) window.B.back(); else history.back(); } } else leaveSince = 0;
      return;
    }
    const any = Object.keys(down).length > 0 || scroll !== 0;
    if (any && !on) {
      setOn(true);
      if (!told && window.B && window.B.toast && window.B.me) { told = true; window.B.toast("Controller ready. A picks, B goes back, Y searches."); }
      // The first press only wakes controller mode and shows where you are
      Object.keys(down).forEach((k) => { held[k] = { since: now, last: now + 400 }; });
      if (!current()) focusEl(firstInPage());
      return;
    }
    // Nothing is highlighted any more (the page redrew under it): find a new home after a moment
    if (on && !PLAYER.test(hash()) && !current()) { if (!lostSince) lostSince = now; else if (now - lostSince > 350) { lostSince = 0; reland(); } } else lostSince = 0;
    // Triggers and the right stick scroll whatever is under the highlight (or the page)
    if (!PLAYER.test(hash()) && !osk.el) {
      const s = scroll + (down.rt ? 1 : 0) - (down.lt ? 1 : 0);
      if (s) { const box = scroller(); box.scrollBy(0, s * 22); }
    }
    Object.keys(BTN).map((i) => BTN[i]).concat(["lt", "rt"]).forEach((a) => {
      if (!down[a]) { delete held[a]; return; }
      const h = held[a];
      if (!h) { held[a] = { since: now, last: now }; act(a); return; }
      if (REPEATS[a] && now - h.since > 420 && now - h.last > 110) { h.last = now; act(a); }
      else if (PLAYER.test(hash()) && (a === "lt" || a === "rt") && now - h.last > 250) { h.last = now; act(a); }
    });
  }
  function scroller() {
    const s = scope();
    if (s !== document.body) return s;
    return document.scrollingElement || document.documentElement;
  }
  function startPolling() { if (!polling) { polling = true; requestAnimationFrame(poll); } }
  window.addEventListener("gamepadconnected", startPolling);
  startPolling(); // one may already be plugged in

  // ---------- arrow keys on a keyboard, once controller mode is on (or always in the Steam Deck app) ----------
  let always = false;
  if (window.glowfinDesktop && window.glowfinDesktop.info) window.glowfinDesktop.info().then((i) => { always = !!(i && i.gameMode); }).catch(() => {});
  document.addEventListener("keydown", (e) => {
    if (!e.isTrusted || e.defaultPrevented || e.ctrlKey || e.metaKey || e.altKey) return;
    if (!(on || always) || GAME.test(hash()) || PLAYER.test(hash()) || READER.test(hash()) || /^#\/playb\//.test(hash())) return;
    const dir = { ArrowLeft: "left", ArrowRight: "right", ArrowUp: "up", ArrowDown: "down" }[e.key];
    if (!dir) return;
    const t = e.target;
    if (osk.el && !osk.el.contains(t)) return;
    if (t && (t.isContentEditable || t.tagName === "SELECT" || (isText(t) && (dir === "left" || dir === "right")) || (t.tagName === "INPUT" && t.type === "range" && (dir === "left" || dir === "right")))) return;
    e.preventDefault();
    setOn(true);
    move(dir);
  });

  // A new page: put the highlight on its first thing, like the TV does
  window.addEventListener("hashchange", () => {
    if (osk.el) osk.close(true);
    if (!on) return;
    let tries = 0;
    const land = () => {
      if (!on || GAME.test(hash()) || PLAYER.test(hash()) || arcadeOwns()) return; // a video starts with nothing picked: A is play and pause
      const a = document.activeElement;
      if (Date.now() < tabUntil) { if (!landOnTab() && ++tries < 12) setTimeout(land, 250); return; }
      // Coming back to a page: the poster you left from, even when the page picked its first one by itself
      // A list down the side of a page (Settings, Server): picking a line redraws the page, stay on that line
      const same = lastHref && lastHref === hash() && $$(`a[href="${CSS.escape(lastHref)}"]`).find((x) => visible(x) && !x.closest(".brail, .top"));
      const was = same || recall();
      if (was) { if (was !== a) focusEl(was); return; }
      if (current() && !(a.closest && a.closest(".brail, .top"))) return;
      const f = firstInPage();
      if (f && !f.closest(".brail")) focusEl(f); else if (++tries < 12) setTimeout(land, 250);
    };
    setTimeout(land, 250);
  });

  window.GlowfinPad = { isOn: () => on, keyboard: osk, move, act, spots };
})();
