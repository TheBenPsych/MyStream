/* Glowfin desktop: tidying up a typed Tower address, and working out which addresses on the home
   network to look at when finding Tower. Kept apart from main.js so it can be tested without a window. */
"use strict";

// "192.168.1.105" -> "http://192.168.1.105:8097"; "myhome.duckdns.org" -> "https://myhome.duckdns.org/benflix-games"
function normalize(typed) {
  let s = String(typed || "").trim().replace(/\s+/g, "");
  if (!s) return "";
  const hadScheme = /^https?:\/\//i.test(s);
  if (!hadScheme && /^[a-z][a-z0-9+.-]*:\/\//i.test(s)) return ""; // some other kind of link
  if (!hadScheme) s = "http://" + s;
  let u;
  try { u = new URL(s); } catch (e) { return ""; }
  if (!/^https?:$/.test(u.protocol) || !/^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$/i.test(u.hostname)) return "";
  const host = u.hostname;
  const atHome = /^\d{1,3}(\.\d{1,3}){3}$/.test(host) || !host.includes(".") || /\.(local|lan|home|internal)$/i.test(host);
  let p = u.pathname.replace(/\/+$/, "");
  if (atHome) {
    // At home Tower's Glowfin helper listens on port 8097
    if (!u.port) u.port = "8097";
  } else if (!hadScheme) {
    // An internet name: goes through the secure door, where Glowfin lives under /benflix-games
    u.protocol = "https:";
    if (!p) p = "/benflix-games";
  }
  return u.protocol + "//" + u.host + p;
}

function origin(url) {
  try { return new URL(url).origin; } catch (e) { return ""; }
}

// Every address on this computer's home networks (192.168.1.1 to .254 and so on), nearest guesses first
function scanHosts(interfaces) {
  const out = [];
  const seen = new Set();
  Object.values(interfaces || {}).forEach((list) => (list || []).forEach((n) => {
    if (!n || n.internal || (n.family !== "IPv4" && n.family !== 4)) return;
    const m = /^(\d+)\.(\d+)\.(\d+)\.(\d+)$/.exec(n.address || "");
    if (!m) return;
    const a = +m[1], b = +m[2];
    const home = a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31);
    if (!home) return; // never go looking around the open internet
    const net = `${m[1]}.${m[2]}.${m[3]}.`;
    if (seen.has(net)) return;
    seen.add(net);
    for (let i = 1; i <= 254; i++) if (i !== +m[4]) out.push(net + i);
  }));
  return out;
}

module.exports = { normalize, origin, scanHosts };
