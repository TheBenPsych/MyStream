/* Benflix web: the 6 arcade games (the same games as the TV and phone app, ui/games/*.kt), drawn on a canvas.
   Shared parts like the app's MiniGame: 3-2-1 countdown, pause (Esc or P), crumbs, "+5" pop-ups, screen
   shake and the game sounds. Keyboard, a plugged-in controller, a mouse or a finger all work.
   Kids profiles get the slower "easy" pace. */
"use strict";
(() => {
  const rnd = (a, b) => a + Math.random() * (b - a);
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const rr = (g, x, y, w, h, r) => { g.beginPath(); g.roundRect ? g.roundRect(x, y, w, h, r) : g.rect(x, y, w, h); };

  // ---------- shared drawings ----------
  const ticket = (g, x, y, s) => {
    const w = s * 3, h = s * 1.8;
    g.fillStyle = "#FFC107"; rr(g, x - w / 2, y - h / 2, w, h, s * 0.3); g.fill();
    g.fillStyle = "#07070A";
    g.beginPath(); g.arc(x - w / 2, y, s * 0.35, 0, 7); g.arc(x + w / 2, y, s * 0.35, 0, 7); g.fill();
    g.fillStyle = "#B45309"; g.fillRect(x - s * 0.08, y - h / 2 + s * 0.2, s * 0.16, h - s * 0.4);
  };
  const heart = (g, x, y, s, c) => {
    g.fillStyle = c; g.beginPath();
    g.moveTo(x, y + s * 0.9);
    g.bezierCurveTo(x - s * 1.6, y - s * 0.1, x - s * 0.7, y - s * 1.3, x, y - s * 0.4);
    g.bezierCurveTo(x + s * 0.7, y - s * 1.3, x + s * 1.6, y - s * 0.1, x, y + s * 0.9);
    g.fill();
  };
  const popcorn = (g, x, y, r) => {
    g.fillStyle = "#FFF7E0";
    [[0, 0, 1], [-0.6, 0.2, 0.7], [0.6, 0.15, 0.72], [0.1, -0.55, 0.7], [-0.3, 0.6, 0.6], [0.35, 0.6, 0.6]].forEach(([dx, dy, k]) => { g.beginPath(); g.arc(x + dx * r, y + dy * r, r * k * 0.7, 0, 7); g.fill(); });
    g.fillStyle = "#F6C453"; g.beginPath(); g.arc(x + r * 0.2, y + r * 0.1, r * 0.25, 0, 7); g.fill();
  };
  const filmStrip = (g, x, y, w, h, u, glow) => {
    if (glow) { g.fillStyle = "rgba(229,9,20,.35)"; g.fillRect(x - u, y, w + 2 * u, h); }
    g.fillStyle = "#1F1F27"; g.fillRect(x, y, w, h);
    g.fillStyle = "#3A3A46"; g.fillRect(x + w * 0.18, y, w * 0.64, h);
    g.fillStyle = "#0B0B10";
    const step = 3.2 * u;
    for (let yy = y + ((-y % step) + step) % step; yy < y + h; yy += step) { g.fillRect(x + w * 0.04, yy, w * 0.09, 1.6 * u); g.fillRect(x + w * 0.87, yy, w * 0.09, 1.6 * u); }
  };

  // ---------- the games ----------
  const GAMES = {};

  // Popcorn Pop: bounce up cups and film reels; butter launches you, phones drop away, tickets +5
  GAMES.POPCORN_POP = () => {
    let L, T, px, py, vy, climbed, top, lastX, bonus, best;
    const S = {
      touch: "steer",
      reset(w, h, u, pace) {
        S.u = u; S.pace = pace;
        L = []; T = []; climbed = 0; bonus = 0; best = 0;
        px = w / 2; py = h * 0.8; vy = -S.bounce();
        L.push({ x: w / 2 - 10 * u, y: h * 0.9, w: 20 * u, kind: "cup", vx: 0 });
        top = h * 0.9; lastX = w / 2;
        while (top > -h) add(w, h);
      },
      grav: () => 140 * S.u * S.pace * S.pace,
      bounce: () => 78 * S.u * S.pace,
      step(dt, w, h, e) {
        const u = S.u;
        px += e.steer * 70 * u * S.pace * dt;
        if (px < -3 * u) px += w + 6 * u;
        if (px > w + 3 * u) px -= w + 6 * u;
        const oldB = py + 3.2 * u;
        vy += S.grav() * dt; py += vy * dt;
        L.forEach((l) => { if (l.vx) { l.x += l.vx * dt; if (l.x < 0 || l.x + l.w > w) l.vx *= -1; } if (l.drop) l.y += 60 * u * dt; });
        if (vy > 0) {
          const nb = py + 3.2 * u;
          const hit = L.find((l) => !l.drop && oldB <= l.y && nb >= l.y && px > l.x - 2 * u && px < l.x + l.w + 2 * u);
          if (hit) {
            py = hit.y - 3.2 * u;
            vy = -S.bounce() * (hit.kind === "butter" ? 1.6 : 1);
            if (hit.kind === "phone") { hit.drop = true; e.sound("thud"); } else e.sound(hit.kind === "butter" ? "boost" : "pop");
            e.burst(px, hit.y, "#FFF7E0", 5);
          }
        }
        T = T.filter((t) => { if (Math.hypot(t.x - px, t.y - py) < 6 * u) { bonus += 5; e.pop(t.x, t.y, "+5"); e.sound("coin"); return false; } return true; });
        // Camera: keep the kernel in the top 40%
        if (py < h * 0.4) {
          const d = h * 0.4 - py;
          py += d; climbed += d; top += d;
          L.forEach((l) => { l.y += d; }); T.forEach((t) => { t.y += d; });
          e.shiftEffects(d);
        }
        L = L.filter((l) => l.y < h + 10 * u); T = T.filter((t) => t.y < h + 10 * u);
        while (top > -h) add(w, h);
        best = Math.max(best, Math.floor(climbed / (10 * u)));
        e.score = best + bonus;
        if (py - 3.2 * u > h) e.over();
      },
      draw(g, w, h) {
        const u = S.u;
        const sky = g.createLinearGradient(0, 0, 0, h); sky.addColorStop(0, "#1B0E2E"); sky.addColorStop(1, "#3B1606");
        g.fillStyle = sky; g.fillRect(0, 0, w, h);
        L.forEach((l) => {
          if (l.kind === "cup") { g.fillStyle = "#E50914"; rr(g, l.x, l.y, l.w, 3 * u, u); g.fill(); g.fillStyle = "#fff"; for (let i = 1; i < 4; i++) g.fillRect(l.x + (l.w * i) / 4 - 0.5 * u, l.y, u, 3 * u); }
          else if (l.kind === "reel") { g.fillStyle = "#9CA3AF"; rr(g, l.x, l.y, l.w, 3 * u, 1.5 * u); g.fill(); g.fillStyle = "#374151"; for (let i = 0; i < 3; i++) { g.beginPath(); g.arc(l.x + l.w * (0.2 + i * 0.3), l.y + 1.5 * u, u * 0.8, 0, 7); g.fill(); } }
          else if (l.kind === "butter") { g.fillStyle = "#FDE047"; rr(g, l.x, l.y, l.w, 3 * u, u); g.fill(); g.fillStyle = "rgba(255,255,255,.5)"; g.fillRect(l.x + u, l.y + 0.5 * u, l.w * 0.4, 0.8 * u); }
          else { g.fillStyle = l.drop ? "#334155" : "#0EA5E9"; rr(g, l.x, l.y, l.w, 3 * u, u); g.fill(); g.fillStyle = "#E0F2FE"; g.fillRect(l.x + l.w * 0.1, l.y + 0.8 * u, l.w * 0.8, 1.4 * u); }
        });
        T.forEach((t) => ticket(g, t.x, t.y, 2.2 * u));
        popcorn(g, px, py, 3.2 * u);
      },
    };
    function add(w, h) {
      const u = S.u;
      const ht = (climbed + (h * 0.9 - top)) / (10 * u);
      let d = clamp(ht / 350, 0, 1); d = d * d * (3 - 2 * d); if (S.pace < 1) d *= 0.6;
      const reach = (S.bounce() ** 2) / (2 * S.grav());
      const minG = (6 + 6 * d) * u, maxG = Math.min((10 + 9 * d) * u, reach * 0.85);
      const gap = minG + Math.random() * Math.max(0, maxG - minG);
      top -= gap;
      const r = Math.random(), bc = 0.07 - 0.03 * d, pc = S.pace < 1 || d < 0.12 ? 0 : 0.25 * d, rc = d < 0.05 ? 0 : 0.1 + 0.35 * d;
      const kind = r < bc ? "butter" : r < bc + pc ? "phone" : r < bc + pc + rc ? "reel" : "cup";
      const lw = (20 - 9 * d) * u;
      const vx = kind === "reel" ? (Math.random() < 0.5 ? 1 : -1) * (12 + 26 * d + Math.random() * (8 + 14 * d)) * u * S.pace : 0;
      const up = S.bounce() / S.grav(), down = Math.sqrt((2 * Math.max(0, reach - gap)) / S.grav());
      const side = Math.min(70 * u * S.pace * (up + down) * 0.7 + lw / 2, w / 2);
      const c = lastX + (Math.random() * 2 - 1) * side;
      const x = clamp((((c % w) + w) % w) - lw / 2, 0, w - lw);
      lastX = x + lw / 2;
      L.push({ x, y: top, w: lw, kind, vx });
      if (kind !== "reel" && Math.random() < 0.12) T.push({ x: x + lw / 2, y: top - 9 * u });
    }
    return S;
  };

  // Reel Rush: flap the Benflix B through gaps in the film strips; tickets +3; squeezes now and then
  GAMES.REEL_RUSH = () => {
    let by, vy, P, T, dist, nextAt, passed, squeeze;
    const S = {
      touch: "tap",
      reset(w, h, u, pace) { S.u = u; S.pace = pace; by = h * 0.45; vy = 0; P = []; T = []; dist = 0; nextAt = w * 0.6; passed = 0; squeeze = 0; },
      press(e) { vy = -55 * S.u * S.pace; e.sound("flap"); },
      step(dt, w, h, e) {
        const u = S.u, speed = (32 + Math.min(passed, 40) * 0.5) * u * S.pace;
        vy += 150 * u * S.pace * S.pace * dt; by += vy * dt;
        dist += speed * dt;
        P.forEach((p) => { p.x -= speed * dt; if (p.slide) { p.t += dt; p.gy = p.base + Math.sin(p.t * 1.6) * 8 * u; } });
        T.forEach((t) => { t.x -= speed * dt; });
        if (dist >= nextAt) {
          const tight = squeeze > 0;
          const gapH = (tight ? 26 : 34 - Math.min(passed, 30) * 0.15) * u * (S.pace < 1 ? 1.2 : 1);
          const last = P.length ? P[P.length - 1].gy : h / 2;
          const gy = tight ? clamp(last + rnd(-8, 8) * u, gapH / 2 + 6 * u, h - gapH / 2 - 6 * u) : rnd(gapH / 2 + 8 * u, h - gapH / 2 - 8 * u);
          const slide = !tight && passed > 12 && Math.random() < 0.3;
          P.push({ x: w + 2 * u, gy, base: gy, gapH, slide, t: 0, tight, done: false });
          if (!tight && Math.random() < 0.35) T.push({ x: w + 6 * u, y: gy });
          if (squeeze > 0) squeeze--; else if (passed > 6 && Math.random() < 0.12) squeeze = 3 + Math.min(4, Math.floor(passed / 20));
          nextAt = dist + (tight ? 24 : 34) * u;
        }
        const bx = w * 0.28, r = 3.4 * u, pw = 10 * u;
        for (const p of P) {
          if (bx + r > p.x && bx - r < p.x + pw && (by - r < p.gy - p.gapH / 2 || by + r > p.gy + p.gapH / 2)) { e.over(); return; }
          if (!p.done && p.x + pw < bx) { p.done = true; passed++; e.score += 1; e.sound("point"); }
        }
        T = T.filter((t) => { if (Math.hypot(t.x - bx, t.y - by) < 6 * u) { e.score += 3; e.pop(t.x, t.y, "+3"); e.sound("coin"); return false; } return t.x > -10 * u; });
        P = P.filter((p) => p.x > -pw);
        if (by > h - r || by < -10 * u) e.over();
      },
      draw(g, w, h) {
        const u = S.u;
        g.fillStyle = "#0B0C10"; g.fillRect(0, 0, w, h);
        g.fillStyle = "rgba(255,255,255,.05)"; for (let i = 0; i < 30; i++) g.fillRect(((i * 97 - dist * 0.2) % w + w) % w, (i * 53) % h, 2, 2);
        P.forEach((p) => { filmStrip(g, p.x, 0, 10 * u, p.gy - p.gapH / 2, u, p.tight); filmStrip(g, p.x, p.gy + p.gapH / 2, 10 * u, h, u, p.tight); });
        T.forEach((t) => ticket(g, t.x, t.y, 2 * u));
        const bx = w * 0.28, s = 7 * u;
        g.save(); g.translate(bx, by); g.rotate(clamp(vy / (120 * u), -0.5, 0.8));
        g.fillStyle = "#E50914"; rr(g, -s / 2, -s / 2, s, s, 1.4 * u); g.fill();
        g.fillStyle = "#fff"; g.font = `800 ${5.2 * u}px Outfit, Arial, sans-serif`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText("B", 0, 0.3 * u);
        g.restore();
      },
    };
    return S;
  };

  // Snack Catch: catch popcorn +1, candy +2, tickets +5; sodas and dropped snacks cost a heart
  GAMES.SNACK_CATCH = () => {
    let bx, F, lives, t, next;
    const S = {
      touch: "steer",
      reset(w, h, u, pace) { S.u = u; S.pace = pace; bx = w / 2; F = []; lives = 3; t = 0; next = 1; },
      step(dt, w, h, e) {
        const u = S.u;
        t += dt;
        bx = clamp(bx + e.steer * 85 * u * S.pace * dt, 8 * u, w - 8 * u);
        next -= dt;
        if (next <= 0) {
          const d = Math.min(1, t / 120);
          const r = Math.random();
          const kind = r < 0.18 + 0.12 * d ? "soda" : r < 0.3 + 0.12 * d ? "ticket" : r < 0.55 + 0.12 * d ? "candy" : "popcorn";
          F.push({ x: rnd(6 * u, w - 6 * u), y: -5 * u, kind, v: (28 + 34 * d + rnd(0, 10)) * u * S.pace, a: rnd(0, 6) });
          next = (1.05 - 0.6 * d) / S.pace * rnd(0.7, 1.2);
        }
        const by = h - 9 * u;
        F = F.filter((f) => {
          f.y += f.v * dt; f.a += dt * 2;
          if (f.y > by - 4 * u && f.y < by + 4 * u && Math.abs(f.x - bx) < 9 * u) {
            if (f.kind === "soda") { lose(e, f); } else {
              const pts = f.kind === "ticket" ? 5 : f.kind === "candy" ? 2 : 1;
              e.score += pts; e.pop(f.x, f.y, "+" + pts); e.sound(f.kind === "ticket" ? "coin" : "pop"); e.burst(f.x, f.y, "#FFF7E0", 4);
            }
            return false;
          }
          if (f.y > h + 4 * u) { if (f.kind !== "soda") lose(e, f); return false; }
          return true;
        });
      },
      draw(g, w, h) {
        const u = S.u;
        const bg = g.createLinearGradient(0, 0, 0, h); bg.addColorStop(0, "#14301F"); bg.addColorStop(1, "#06140C");
        g.fillStyle = bg; g.fillRect(0, 0, w, h);
        g.fillStyle = "#3F1D0B"; g.fillRect(0, h - 4 * u, w, 4 * u);
        F.forEach((f) => {
          if (f.kind === "popcorn") popcorn(g, f.x, f.y, 2.4 * u);
          else if (f.kind === "candy") { g.save(); g.translate(f.x, f.y); g.rotate(f.a); g.fillStyle = "#EC4899"; rr(g, -2.5 * u, -1.5 * u, 5 * u, 3 * u, u); g.fill(); g.fillStyle = "#F9A8D4"; g.beginPath(); g.moveTo(-2.5 * u, 0); g.lineTo(-4.5 * u, -1.6 * u); g.lineTo(-4.5 * u, 1.6 * u); g.fill(); g.beginPath(); g.moveTo(2.5 * u, 0); g.lineTo(4.5 * u, -1.6 * u); g.lineTo(4.5 * u, 1.6 * u); g.fill(); g.restore(); }
          else if (f.kind === "ticket") ticket(g, f.x, f.y, 1.8 * u);
          else { g.fillStyle = "#38BDF8"; g.beginPath(); g.moveTo(f.x - 2.4 * u, f.y - 3 * u); g.lineTo(f.x + 2.4 * u, f.y - 3 * u); g.lineTo(f.x + 1.8 * u, f.y + 3 * u); g.lineTo(f.x - 1.8 * u, f.y + 3 * u); g.fill(); g.fillStyle = "#fff"; g.fillRect(f.x + 0.4 * u, f.y - 6 * u, 0.6 * u, 3.2 * u); g.fillStyle = "rgba(56,189,248,.5)"; g.beginPath(); g.arc(f.x, f.y + 3.6 * u, 2.6 * u, 0, Math.PI); g.fill(); }
        });
        const by = h - 9 * u;
        g.fillStyle = "#fff"; g.beginPath(); g.moveTo(bx - 8 * u, by - 3 * u); g.lineTo(bx + 8 * u, by - 3 * u); g.lineTo(bx + 6 * u, by + 5 * u); g.lineTo(bx - 6 * u, by + 5 * u); g.fill();
        g.fillStyle = "#E50914"; for (let i = -2; i <= 2; i++) { g.beginPath(); g.moveTo(bx + i * 3.2 * u - 0.8 * u, by - 3 * u); g.lineTo(bx + i * 3.2 * u + 0.8 * u, by - 3 * u); g.lineTo(bx + i * 2.4 * u + 0.6 * u, by + 5 * u); g.lineTo(bx + i * 2.4 * u - 0.6 * u, by + 5 * u); g.fill(); }
        popcorn(g, bx - 3 * u, by - 3.6 * u, 1.6 * u); popcorn(g, bx + 2.5 * u, by - 3.8 * u, 1.6 * u);
        for (let i = 0; i < 3; i++) heart(g, 5 * u + i * 7 * u, 13 * u, 2.6 * u, i < lives ? "#E50914" : "rgba(255,255,255,.27)");
      },
    };
    function lose(e, f) {
      lives--; e.sound("miss"); e.shake(1.2); e.buzz();
      if (lives <= 0) e.over();
    }
    return S;
  };

  // Usher Dash: run down the aisle, jump spills, shoes and film-can stacks (hold to jump higher); tickets +5
  GAMES.USHER_DASH = () => {
    let y, vy, O, T, t, dist, next, holding, ground;
    const S = {
      touch: "tap",
      reset(w, h, u, pace) { S.u = u; S.pace = pace; ground = h * 0.78; y = ground; vy = 0; O = []; T = []; t = 0; dist = 0; next = w * 0.5; holding = false; },
      press(e) { holding = true; if (y >= ground - 0.1) { vy = -62 * S.u * S.pace; e.sound("flap"); } },
      release() { holding = false; },
      step(dt, w, h, e) {
        const u = S.u;
        t += dt;
        const speed = (38 + Math.min(t, 120) * 0.35) * u * S.pace;
        const gr = (holding && vy < 0 ? 95 : 210) * u * S.pace * S.pace;
        vy += gr * dt; y += vy * dt;
        if (y > ground) { y = ground; vy = 0; }
        dist += speed * dt;
        O.forEach((o) => { o.x -= speed * dt; }); T.forEach((k) => { k.x -= speed * dt; });
        if (dist >= next) {
          const fast = t > 45;
          const r = Math.random();
          const kind = r < 0.38 ? "spill" : r < 0.72 ? "shoe" : fast && r > 0.9 ? "tall" : "cans";
          const oh = { spill: 2, shoe: 4.5, cans: 9, tall: 13 }[kind] * u;
          const ow = { spill: 9, shoe: 6, cans: 6, tall: 6 }[kind] * u;
          O.push({ x: w + 4 * u, w: ow, h: oh, kind });
          if (Math.random() < 0.3) T.push({ x: w + 4 * u + ow / 2, y: ground - (16 + rnd(0, 8)) * u });
          next = dist + rnd(38, 70) * u * (1 + Math.min(t, 120) / 200);
        }
        const ux = w * 0.2, uw = 4 * u, uh = 11 * u;
        for (const o of O) {
          if (ux + uw / 2 > o.x + 0.8 * u && ux - uw / 2 < o.x + o.w - 0.8 * u && y > ground - o.h + 0.5 * u) { e.over(); return; }
          if (!o.done && o.x + o.w < ux) { o.done = true; e.score += 1; e.sound("point"); }
        }
        T = T.filter((k) => { if (Math.abs(k.x - ux) < 5 * u && Math.abs(k.y - (y - uh / 2)) < 7 * u) { e.score += 5; e.pop(k.x, k.y, "+5"); e.sound("coin"); return false; } return k.x > -10 * u; });
        O = O.filter((o) => o.x > -o.w - 4 * u);
      },
      draw(g, w, h) {
        const u = S.u;
        g.fillStyle = "#150A0A"; g.fillRect(0, 0, w, h);
        // seats rows going by
        g.fillStyle = "#4C1010";
        for (let i = 0; i < 14; i++) { const x = ((i * 14 * u - dist * 0.5) % (w + 14 * u) + w + 14 * u) % (w + 14 * u) - 14 * u; rr(g, x, ground - 20 * u, 11 * u, 12 * u, 2 * u); g.fill(); }
        g.fillStyle = "#7F1D1D"; g.fillRect(0, ground, w, h - ground);
        g.fillStyle = "#FDE68A"; for (let i = 0; i < 20; i++) { const x = ((i * 10 * u - dist) % (w + 10 * u) + w + 10 * u) % (w + 10 * u); g.fillRect(x, ground + 2 * u, 3 * u, 0.6 * u); }
        O.forEach((o) => {
          const top = ground - o.h;
          if (o.kind === "spill") { g.fillStyle = "#FFF7E0"; g.beginPath(); g.ellipse(o.x + o.w / 2, ground - 0.6 * u, o.w / 2, 1.4 * u, 0, 0, 7); g.fill(); popcorn(g, o.x + o.w * 0.3, ground - 1.6 * u, 1.1 * u); popcorn(g, o.x + o.w * 0.7, ground - 1.4 * u, 1 * u); }
          else if (o.kind === "shoe") { g.fillStyle = "#1E3A8A"; rr(g, o.x, top + 1.5 * u, o.w, o.h - 1.5 * u, u); g.fill(); g.fillRect(o.x, top, o.w * 0.45, o.h); g.fillStyle = "#fff"; g.fillRect(o.x, ground - 0.8 * u, o.w, 0.8 * u); }
          else { const n = Math.round(o.h / (3 * u)); for (let i = 0; i < n; i++) { g.fillStyle = i % 2 ? "#9CA3AF" : "#6B7280"; rr(g, o.x, ground - (i + 1) * (o.h / n), o.w, o.h / n - 0.3 * u, u); g.fill(); } }
        });
        T.forEach((k) => ticket(g, k.x, k.y, 1.8 * u));
        const ux = w * 0.2, run = Math.sin(dist / (3 * u)) * (y >= ground ? 1 : 0);
        g.fillStyle = "#111827"; g.fillRect(ux - 2 * u + run * u, y - 4 * u, 1.4 * u, 4 * u); g.fillRect(ux + 0.6 * u - run * u, y - 4 * u, 1.4 * u, 4 * u);
        g.fillStyle = "#B91C1C"; rr(g, ux - 2.4 * u, y - 10 * u, 4.8 * u, 6.4 * u, u); g.fill();
        g.fillStyle = "#F2C9A0"; g.beginPath(); g.arc(ux, y - 12 * u, 2 * u, 0, 7); g.fill();
        g.fillStyle = "#111827"; g.fillRect(ux - 2.2 * u, y - 14.6 * u, 4.4 * u, 1.2 * u);
        g.fillStyle = "rgba(253,230,138,.35)"; g.beginPath(); g.moveTo(ux + 2.4 * u, y - 7 * u); g.lineTo(ux + 18 * u, y - 11 * u); g.lineTo(ux + 18 * u, y - 1 * u); g.fill();
      },
    };
    return S;
  };

  // Film Can Stack: drop the sliding can; overhang is cut off; perfect drops +2, three in a row widen it
  GAMES.FILM_CAN_STACK = () => {
    let stack, cur, dir, speed, perfect, cam, fall;
    const S = {
      touch: "tap",
      reset(w, h, u, pace) {
        S.u = u; S.pace = pace;
        const cw = Math.min(w * 0.42, 60 * u);
        stack = [{ x: w / 2 - cw / 2, w: cw }];
        perfect = 0; cam = 0; fall = [];
        newCan(w);
      },
      press(e) {
        const u = S.u, last = stack[stack.length - 1];
        const left = Math.max(cur.x, last.x), right = Math.min(cur.x + cur.w, last.x + last.w);
        if (right - left <= 0) { fall.push({ x: cur.x, w: cur.w, y: 0, lvl: stack.length, v: 0 }); e.over(); return; }
        if (Math.abs(cur.x - last.x) < 1.2 * u) {
          perfect++;
          let nw = last.w;
          if (perfect % 3 === 0) nw = Math.min(nw + 6 * u, stack[0].w);
          stack.push({ x: last.x - (nw - last.w) / 2, w: nw });
          e.score += 2; e.pop(last.x + last.w / 2, 0, "Perfect +2", true); e.sound("coin");
        } else {
          perfect = 0;
          if (cur.x < left) fall.push({ x: cur.x, w: left - cur.x, lvl: stack.length, y: 0, v: 0 });
          if (cur.x + cur.w > right) fall.push({ x: right, w: cur.x + cur.w - right, lvl: stack.length, y: 0, v: 0 });
          stack.push({ x: left, w: right - left });
          e.score += 1; e.sound("thud");
        }
        newCan(e.w);
      },
      step(dt, w, h, e) {
        cur.x += dir * speed * dt;
        if (cur.x < 0) { cur.x = 0; dir = 1; }
        if (cur.x + cur.w > w) { cur.x = w - cur.w; dir = -1; }
        const ch = 6 * S.u;
        const want = Math.max(0, stack.length * ch - h * 0.5);
        cam += (want - cam) * Math.min(1, dt * 4);
        fall.forEach((f) => { f.v += 200 * S.u * dt; f.y += f.v * dt; });
        fall = fall.filter((f) => f.y < h * 2);
      },
      draw(g, w, h) {
        const u = S.u, ch = 6 * u, base = h - 6 * u + cam;
        const bg = g.createLinearGradient(0, 0, 0, h); bg.addColorStop(0, "#1E1035"); bg.addColorStop(1, "#0B0C10");
        g.fillStyle = bg; g.fillRect(0, 0, w, h);
        const can = (x, y, cw, c) => { g.fillStyle = c; rr(g, x, y, cw, ch - 0.6 * u, 1.2 * u); g.fill(); g.fillStyle = "rgba(255,255,255,.25)"; g.fillRect(x + 0.8 * u, y + 1 * u, cw - 1.6 * u, 0.7 * u); g.fillStyle = "rgba(0,0,0,.25)"; g.fillRect(x + cw / 2 - 1.2 * u, y + 1.8 * u, 2.4 * u, 2 * u); };
        stack.forEach((s, i) => can(s.x, base - (i + 1) * ch, s.w, i % 2 ? "#A78BFA" : "#C4B5FD"));
        fall.forEach((f) => can(f.x, base - (f.lvl + 1) * ch + f.y, f.w, "#6D28D9"));
        if (cur) can(cur.x, base - (stack.length + 1) * ch - 3 * u, cur.w, "#FDE68A");
      },
    };
    function newCan(w) {
      const last = stack[stack.length - 1];
      dir = Math.random() < 0.5 ? 1 : -1;
      cur = { x: dir > 0 ? 0 : w - last.w, w: last.w };
      speed = (40 + Math.min(stack.length, 40) * 2.2) * S.u * S.pace;
    }
    return S;
  };

  // Matinee Match: flip two cards at a time; pairs +10 and add seconds; clear the board for +25 and a bigger board
  GAMES.MATINEE_MATCH = () => {
    const ICONS = ["🍿", "🎬", "🎟️", "🥤", "🎥", "⭐", "🍫", "🌭", "🎞️", "👑", "🚀", "🦖", "👻", "🎩", "🧸"];
    let cards, cols, rows, open, time, lockT, level, sel, layout;
    const S = {
      touch: "cards",
      reset(w, h, u, pace) { S.u = u; S.pace = pace; level = 0; time = 45 / Math.min(1, pace) ; deal(); },
      key(k, e) {
        const n = cards.length;
        if (k === "left") sel = (sel + n - 1) % n;
        else if (k === "right") sel = (sel + 1) % n;
        else if (k === "up") sel = (sel - cols + n) % n;
        else if (k === "down") sel = (sel + cols) % n;
        else if (k === "ok") flip(sel, e);
      },
      tapAt(x, y, e) {
        if (!layout) return;
        const i = cards.findIndex((c, j) => { const r = layout(j); return x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h; });
        if (i >= 0) { sel = i; flip(i, e); }
      },
      step(dt, w, h, e) {
        time -= dt;
        if (lockT > 0) { lockT -= dt; if (lockT <= 0) { open.forEach((i) => { cards[i].up = false; }); open = []; } }
        if (time <= 0) { time = 0; e.over(); }
        cards.forEach((c) => { c.turn += ((c.up || c.done ? 1 : 0) - c.turn) * Math.min(1, dt * 10); });
      },
      draw(g, w, h) {
        const u = S.u;
        g.fillStyle = "#1A0612"; g.fillRect(0, 0, w, h);
        const pad = 2 * u, top = 12 * u;
        const cw = Math.min((w - pad * (cols + 1)) / cols, ((h - top - pad * (rows + 1)) / rows) * 0.75);
        const chh = cw / 0.75;
        const ox = (w - (cw * cols + pad * (cols - 1))) / 2, oy = top + (h - top - (chh * rows + pad * (rows - 1))) / 2;
        layout = (j) => ({ x: ox + (j % cols) * (cw + pad), y: oy + Math.floor(j / cols) * (chh + pad), w: cw, h: chh });
        cards.forEach((c, j) => {
          const r = layout(j), k = Math.abs(Math.cos(c.turn * Math.PI)), face = c.turn > 0.5;
          g.save(); g.translate(r.x + r.w / 2, r.y + r.h / 2); g.scale(Math.max(0.04, k), 1);
          g.fillStyle = face ? (c.done ? "#FCE7F3" : "#fff") : "#DB2777"; rr(g, -r.w / 2, -r.h / 2, r.w, r.h, 1.4 * u); g.fill();
          if (face) { g.font = `${r.w * 0.5}px sans-serif`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(c.icon, 0, r.h * 0.04); }
          else { g.fillStyle = "rgba(255,255,255,.25)"; g.font = `800 ${r.w * 0.4}px Outfit, Arial, sans-serif`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText("B", 0, 0); }
          g.restore();
          if (j === sel && S.keys) { g.strokeStyle = "#fff"; g.lineWidth = 0.6 * u; rr(g, r.x - u, r.y - u, r.w + 2 * u, r.h + 2 * u, 2 * u); g.stroke(); }
        });
        g.fillStyle = time < 10 ? "#F87171" : "#fff"; g.font = `700 ${4 * u}px Outfit, Arial, sans-serif`; g.textAlign = "right"; g.textBaseline = "top";
        g.fillText(`${Math.ceil(time)} s`, w - 3 * u, 3 * u);
      },
    };
    function deal() {
      const sizes = [[4, 3], [4, 4], [5, 4], [6, 4], [6, 5]];
      [cols, rows] = sizes[Math.min(level, sizes.length - 1)];
      const pairs = Math.floor((cols * rows) / 2);
      const icons = ICONS.slice().sort(() => Math.random() - 0.5).slice(0, pairs);
      cards = icons.concat(icons).sort(() => Math.random() - 0.5).map((icon) => ({ icon, up: false, done: false, turn: 0 }));
      if (cards.length < cols * rows) cards.push({ icon: "", up: false, done: true, turn: 1, blank: true });
      open = []; lockT = 0; sel = 0;
    }
    function flip(i, e) {
      const c = cards[i];
      if (!c || c.done || c.up || lockT > 0) return;
      c.up = true; open.push(i); e.sound("flip");
      if (open.length === 2) {
        const [a, b] = open.map((j) => cards[j]);
        if (a.icon === b.icon) {
          a.done = b.done = true; open = [];
          e.score += 10; time += 3; e.sound("point");
          if (cards.every((x) => x.done)) { e.score += 25; e.sound("coin"); e.pop(e.w / 2, e.h / 2, "Board cleared +25"); level++; time += 15; setTimeout(deal, 700); }
        } else { lockT = 0.8; e.sound("miss"); }
      }
    }
    return S;
  };

  // ---------- the engine ----------
  B.bgArcade = (ctx) => {
    const make = GAMES[ctx.game.key];
    if (!make) throw new Error("This game isn't in the browser yet.");
    const S = make();
    const pace = ctx.kids ? 0.8 : 1;
    const cv = document.createElement("canvas");
    cv.className = "bgcanvas";
    ctx.stage.appendChild(cv);
    const g = cv.getContext("2d");
    let W = 0, H = 0, U = 1, dpr = 1;
    let phase = "count", count = 3, last = performance.now(), raf = 0, shakeT = 0;
    const fx = []; // crumbs and pop-ups
    const held = { l: false, r: false, tl: false, tr: false };
    const E = {
      score: 0, steer: 0, get w() { return W; }, get h() { return H; },
      sound: B.bgSound,
      burst: (x, y, c, n) => { for (let i = 0; i < n; i++) fx.push({ x, y, vx: rnd(-20, 20) * U, vy: rnd(-30, -5) * U, life: 0.6, c }); },
      pop: (x, y, text, center) => fx.push({ x, y: center ? H * 0.3 : y, text, life: 1, vy: -14 * U }),
      shake: (s) => { shakeT = Math.max(shakeT, 0.25 * s); },
      buzz: () => { try { navigator.vibrate && navigator.vibrate(40); } catch (e) {} },
      shiftEffects: (d) => fx.forEach((f) => { f.y += d; }),
      over: () => {
        if (phase === "over") return;
        phase = "over"; B.bgSound("crash"); E.shake(2.2); E.buzz();
        setTimeout(() => { if (ctx.alive()) B.bgOver(ctx, "Game over", `You scored ${E.score.toLocaleString()}.`, E.score); }, 700);
      },
    };
    const size = () => {
      const r = ctx.stage.getBoundingClientRect();
      dpr = Math.min(2, window.devicePixelRatio || 1);
      // Games are played in a tall-ish box (like a phone held up) on wide screens, full width on phones
      const h = r.height, w = Math.min(r.width, ctx.game.key === "MATINEE_MATCH" || ctx.game.key === "REEL_RUSH" || ctx.game.key === "USHER_DASH" ? r.width : h * 0.75);
      cv.style.width = w + "px"; cv.style.height = h + "px";
      cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr);
      const changed = Math.abs(W - w) > 2 || Math.abs(H - h) > 2;
      W = w; H = h; U = H / 100;
      if (changed) { S.reset(W, H, U, pace); E.score = 0; }
    };
    size();
    const ro = new ResizeObserver(() => { if (phase !== "over") size(); });
    ro.observe(ctx.stage);
    B.bgSound("tick");

    const steer = () => { E.steer = (held.r || held.tr ? 1 : 0) - (held.l || held.tl ? 1 : 0); };
    const press = () => {
      if (phase === "paused") { resume(); return; }
      if (phase !== "play") return;
      if (S.press) S.press(E);
    };
    const release = () => { if (S.release) S.release(E); };
    const keyMap = { ArrowLeft: "left", a: "left", A: "left", ArrowRight: "right", d: "right", D: "right", ArrowUp: "up", w: "up", W: "up", ArrowDown: "down", s: "down", S: "down", " ": "ok", Enter: "ok" };
    const onKey = (ev) => {
      if (ev.target.closest && ev.target.closest(".bg-over, .bgbar")) return;
      const k = keyMap[ev.key];
      if (ev.key === "Escape" || ev.key === "p" || ev.key === "P") { ev.preventDefault(); phase === "paused" ? resume() : pause(); return; }
      if (!k) return;
      ev.preventDefault();
      if (S.touch === "cards") { S.keys = true; if (phase === "play") S.key(k, E); return; }
      if (k === "left") held.l = true;
      if (k === "right") held.r = true;
      steer();
      if ((k === "ok" || k === "up") && !ev.repeat) press();
    };
    const onKeyUp = (ev) => {
      const k = keyMap[ev.key]; if (!k) return;
      if (k === "left") held.l = false;
      if (k === "right") held.r = false;
      steer();
      if (k === "ok" || k === "up") release();
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("keyup", onKeyUp);
    // Finger or mouse: steer games use the left/right half, tap games anywhere, cards where you tap
    const down = (ev) => {
      ev.preventDefault();
      const r = cv.getBoundingClientRect(), x = ev.clientX - r.left, y = ev.clientY - r.top;
      if (phase === "paused") { resume(); return; }
      if (S.touch === "steer") { if (x < r.width / 2) held.tl = true; else held.tr = true; steer(); }
      else if (S.touch === "cards") { S.keys = false; if (phase === "play") S.tapAt(x, y, E); }
      else press();
    };
    const up = () => { held.tl = held.tr = false; steer(); release(); };
    cv.addEventListener("pointerdown", down);
    window.addEventListener("pointerup", up);
    cv.addEventListener("contextmenu", (e) => e.preventDefault());
    // A plugged-in controller
    let padA = false, padStart = false, padDir = "";
    const pollPad = () => {
      const pads = navigator.getGamepads ? Array.from(navigator.getGamepads()).filter(Boolean) : [];
      const p = pads[0]; if (!p) return;
      const ax = p.axes[0] || 0, ay = p.axes[1] || 0, bt = (i) => p.buttons[i] && p.buttons[i].pressed;
      const L = bt(14) || ax < -0.4, R = bt(15) || ax > 0.4, Uu = bt(12) || ay < -0.5, D = bt(13) || ay > 0.5;
      if (S.touch === "cards") {
        const dir = L ? "left" : R ? "right" : Uu ? "up" : D ? "down" : "";
        if (dir && dir !== padDir && phase === "play") { S.keys = true; S.key(dir, E); }
        padDir = dir;
      } else { held.l = L; held.r = R; steer(); }
      const a = bt(0);
      if (a && !padA) { if (S.touch === "cards") { if (phase === "play") S.key("ok", E); else if (phase === "paused") resume(); } else press(); }
      if (!a && padA) release();
      padA = a;
      const st = bt(9);
      if (st && !padStart) phase === "paused" ? resume() : pause();
      padStart = st;
    };
    const pause = () => { if (phase === "play" || phase === "count") { phase = "paused"; held.l = held.r = held.tl = held.tr = false; steer(); } };
    const resume = () => { if (phase === "paused") { phase = "count"; count = 1.99; B.bgSound("tick"); } };
    const onVis = () => { if (document.hidden) pause(); };
    document.addEventListener("visibilitychange", onVis);
    window.addEventListener("blur", pause);

    const loop = (now) => {
      raf = requestAnimationFrame(loop);
      const dt = Math.min(0.05, (now - last) / 1000); last = now;
      pollPad();
      if (phase === "count") {
        const before = Math.ceil(count); count -= dt;
        if (count <= 0) { phase = "play"; B.bgSound("go"); } else if (Math.ceil(count) !== before) B.bgSound("tick");
      } else if (phase === "play") S.step(dt, W, H, E);
      else if (phase === "over" && S.step && S.touch === "tap") { /* frozen */ }
      for (let i = fx.length - 1; i >= 0; i--) { const f = fx[i]; f.life -= dt; if (f.vx != null) { f.x += f.vx * dt; f.vy += 80 * U * dt; } f.y += f.vy * dt; if (f.life <= 0) fx.splice(i, 1); }
      shakeT = Math.max(0, shakeT - dt);
      // draw
      g.setTransform(dpr, 0, 0, dpr, 0, 0);
      if (shakeT > 0) g.translate(rnd(-1, 1) * U * shakeT * 3, rnd(-1, 1) * U * shakeT * 3);
      S.draw(g, W, H);
      fx.forEach((f) => {
        g.globalAlpha = clamp(f.life * 1.6, 0, 1);
        if (f.text) { g.fillStyle = "#FDE68A"; g.font = `800 ${4 * U}px Outfit, Arial, sans-serif`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(f.text, f.x, f.y); }
        else { g.fillStyle = f.c; g.beginPath(); g.arc(f.x, f.y, 0.7 * U, 0, 7); g.fill(); }
        g.globalAlpha = 1;
      });
      g.setTransform(dpr, 0, 0, dpr, 0, 0);
      g.fillStyle = "#fff"; g.font = `800 ${6 * U}px Outfit, Arial, sans-serif`; g.textAlign = "center"; g.textBaseline = "top";
      if (S.touch !== "cards" || true) g.fillText(E.score.toLocaleString(), W / 2, 2.5 * U);
      if (phase === "count" || phase === "paused") {
        g.fillStyle = "rgba(0,0,0,.45)"; g.fillRect(0, 0, W, H);
        g.fillStyle = "#fff"; g.textBaseline = "middle";
        if (phase === "count") { g.font = `800 ${22 * U}px Outfit, Arial, sans-serif`; g.fillText(String(Math.max(1, Math.ceil(count))), W / 2, H / 2); }
        else { g.font = `800 ${8 * U}px Outfit, Arial, sans-serif`; g.fillText("Paused", W / 2, H / 2 - 4 * U); g.font = `600 ${3.4 * U}px Outfit, Arial, sans-serif`; g.fillText("Press Esc, P or tap to keep playing", W / 2, H / 2 + 5 * U); }
      }
      ctx.setScore(`Best ${ctx.best().toLocaleString()}`);
    };
    raf = requestAnimationFrame(loop);
    B.onLeave(() => {
      cancelAnimationFrame(raf); ro.disconnect();
      document.removeEventListener("keydown", onKey); document.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("pointerup", up); document.removeEventListener("visibilitychange", onVis); window.removeEventListener("blur", pause);
    });
    // The how-to line under the game for a few seconds
    const how = document.createElement("div");
    how.className = "bg-how"; how.textContent = ctx.game.how;
    ctx.stage.appendChild(how);
    setTimeout(() => how.classList.add("gone"), 5000);
  };
})();
