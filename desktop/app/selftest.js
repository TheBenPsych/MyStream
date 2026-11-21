/* Glowfin desktop: quick checks of the address helpers (run by "npm run check" and by GitHub). */
"use strict";
const assert = require("assert");
const A = require("./address");

assert.strictEqual(A.normalize("192.168.1.105"), "http://192.168.1.105:8097");
assert.strictEqual(A.normalize(" 192.168.1.105:8097/ "), "http://192.168.1.105:8097");
assert.strictEqual(A.normalize("http://192.168.1.105:9000"), "http://192.168.1.105:9000");
assert.strictEqual(A.normalize("tower"), "http://tower:8097");
assert.strictEqual(A.normalize("tower.local"), "http://tower.local:8097");
assert.strictEqual(A.normalize("myhome.duckdns.org"), "https://myhome.duckdns.org/benflix-games");
assert.strictEqual(A.normalize("https://myhome.duckdns.org/benflix-games/"), "https://myhome.duckdns.org/benflix-games");
assert.strictEqual(A.normalize(""), "");
assert.strictEqual(A.normalize("ftp://x"), "");
assert.strictEqual(A.normalize("not an address!"), "");
assert.strictEqual(A.origin("http://192.168.1.105:8097/x"), "http://192.168.1.105:8097");
assert.strictEqual(A.origin("nope"), "");

const hosts = A.scanHosts({
  lo: [{ address: "127.0.0.1", family: "IPv4", internal: true }],
  wlan0: [{ address: "192.168.1.40", family: "IPv4", internal: false }, { address: "fe80::1", family: "IPv6", internal: false }],
  tun0: [{ address: "8.8.4.4", family: "IPv4", internal: false }],
});
assert.strictEqual(hosts.length, 253);
assert.ok(hosts.includes("192.168.1.105") && !hosts.includes("192.168.1.40") && !hosts.some((h) => h.startsWith("8.")));
console.log("Glowfin desktop checks passed");
