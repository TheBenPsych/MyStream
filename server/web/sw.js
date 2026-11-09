/* Benflix web: the small helper that lets a browser install Benflix like an app (its own window and
   icon). It keeps nothing offline: every page and video still comes straight from Tower. */
"use strict";
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));
self.addEventListener("fetch", () => {});
