/* Glowfin desktop: the "Find Tower" screen (connect.html). */
"use strict";

const G = window.glowfinDesktop;
const $ = (id) => document.getElementById(id);
const q = new URLSearchParams(location.search);
const why = q.get("why") || "first";
const saved = q.get("server") || "";
const pretty = (u) => String(u || "").replace(/^https?:\/\//, "").replace(/:8097$/, "");
let busy = false;

function say(big, small, dots) {
  $("say").textContent = big;
  $("say").classList.toggle("dots", !!dots);
  $("more").textContent = small || "";
}
function showForm(focusId) {
  $("form").classList.remove("hidden");
  $("again").classList.toggle("hidden", !saved);
  const el = $(focusId || "addr");
  if (el) el.focus();
}
function setBusy(on) {
  busy = on;
  ["go", "find", "again"].forEach((id) => { $(id).disabled = on; });
}

async function connect(address) {
  if (busy) return;
  $("bad").textContent = "";
  setBusy(true);
  say("Connecting", pretty(address), true);
  const r = await G.connect(address).catch(() => ({ ok: false, why: "Something went wrong. Try again." }));
  if (r && r.ok) return; // the app is now opening Glowfin
  setBusy(false);
  say("Couldn't connect", "");
  $("bad").textContent = (r && r.why) || "Couldn't connect.";
  showForm("addr");
}

async function look(first) {
  if (busy) return;
  $("bad").textContent = "";
  setBusy(true);
  say("Looking for Tower on your network", "This takes a few seconds.", true);
  const found = await G.findTower().catch(() => null);
  setBusy(false);
  if (found) return connect(found);
  say(first ? "Where is Tower?" : "Couldn't find Tower", "Type Tower's address. It's the same one you use for Glowfin in a browser.");
  showForm("addr");
}

$("go").addEventListener("click", () => { const v = $("addr").value.trim(); if (v) connect(v); else $("addr").focus(); });
$("find").addEventListener("click", () => look(false));
$("again").addEventListener("click", () => connect(saved));
$("addr").addEventListener("keydown", (e) => { if (e.key === "Enter") $("go").click(); });

G.info().then((i) => { $("foot").textContent = "Glowfin for computers " + i.version + (i.gameMode ? " · Steam + X opens the keyboard" : " · F11 for full screen"); }).catch(() => {});

if (why === "first") look(true);
else if (why === "change") { say("Change Tower's address", "Now using " + pretty(saved)); $("addr").value = pretty(saved); showForm("addr"); }
else {
  say("Can't reach Tower", "Tower may be off or restarting, or this computer isn't on the home network. Tried " + pretty(saved) + ".");
  $("addr").value = pretty(saved);
  showForm("again");
  // Tower often comes back by itself (a restart, the Wi-Fi waking up): keep trying quietly
  setInterval(() => { if (!busy && document.activeElement !== $("addr")) G.connect(saved).catch(() => {}); }, 20000);
}

// A controller: stick or D-pad moves between the box and the buttons, A presses, like on the TV
(function pad() {
  let last = 0, held = "";
  const order = () => ["addr", "go", "find", "again"].map($).filter((el) => el && !el.disabled && !el.classList.contains("hidden") && !$("form").classList.contains("hidden"));
  const move = (d) => { const l = order(); if (!l.length) return; const i = l.indexOf(document.activeElement); l[Math.max(0, Math.min(l.length - 1, (i < 0 ? 0 : i + d)))].focus(); };
  const tick = () => {
    const p = Array.from((navigator.getGamepads && navigator.getGamepads()) || []).find(Boolean);
    let now = "";
    if (p) {
      const b = (n) => p.buttons[n] && p.buttons[n].pressed;
      const x = p.axes[0] || 0, y = p.axes[1] || 0;
      if (b(0)) now = "a"; else if (b(14) || b(12) || x < -0.6 || y < -0.6) now = "prev"; else if (b(15) || b(13) || x > 0.6 || y > 0.6) now = "next";
    }
    const t = Date.now();
    if (now && (now !== held || (now !== "a" && t - last > 350))) {
      last = t;
      if (now === "a") { const el = document.activeElement; if (el && el.tagName === "BUTTON") el.click(); else if (el === $("addr")) $("go").click(); }
      else move(now === "next" ? 1 : -1);
    }
    held = now;
    requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
})();
