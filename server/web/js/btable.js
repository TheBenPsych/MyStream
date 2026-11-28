/* Glowfin web: the Table games (the same as the app's Games > Table tab, ui/games/table/*.kt).
   Checkers, Chess, Four in a Row, Five Dice and Solitaire, drawn with plain page parts.
   Mouse, touch or keyboard (arrows + Enter, Esc for the menu). Play a friend on the same device, or Glowfin. */
"use strict";
(() => {
  const css = `
  .tb-wrap{width:100%;max-width:1100px;padding:8px 16px 20px;display:flex;gap:28px;align-items:center;justify-content:center;flex-wrap:wrap;overflow-y:auto}
  .tb-side{flex:1 1 240px;max-width:340px;display:flex;flex-direction:column;gap:10px}
  .tb-side h2{margin:0;font-size:26px}
  .tb-tag{display:flex;align-items:center;gap:10px;padding:8px 12px;border-radius:12px;border:2px solid transparent}
  .tb-tag.on{background:rgba(255,255,255,.1);border-color:var(--c)}
  .tb-tag i{width:16px;height:16px;border-radius:50%;background:var(--c);flex:none}
  .tb-tag small{display:block;color:var(--ink2)}
  .tb-say{font-weight:600}.tb-say.bad{color:#F87171}
  .tb-board{display:grid;grid-template-columns:repeat(8,1fr);grid-template-rows:repeat(8,1fr);width:min(72vh,92vw,600px);aspect-ratio:1;border:6px solid #0B3640;border-radius:6px;user-select:none}
  .tb-sq{position:relative;display:grid;place-items:center;cursor:pointer}
  .tb-sq.l{background:#C9D3EC}.tb-sq.d{background:#3B5A8C}
  .tb-sq.last::before{content:"";position:absolute;inset:0;background:rgba(255,216,74,.3)}
  .tb-sq.pick::before{content:"";position:absolute;inset:0;background:rgba(255,216,74,.6)}
  .tb-sq.check{background:#E2607A}
  .tb-sq.cur::after{content:"";position:absolute;inset:0;border:4px solid #fff}
  .tb-dot{position:absolute;width:26%;height:26%;border-radius:50%;background:rgba(0,0,0,.35);z-index:2}
  .tb-pc{position:relative;width:78%;height:78%;border-radius:50%;border:3px solid rgba(255,255,255,.55);display:grid;place-items:center;font-size:min(4vh,30px);color:#14172B;z-index:1}
  .tb-cp{position:relative;z-index:1;font-size:min(6.4vh,52px);line-height:1;font-family:"DejaVu Sans","Segoe UI Symbol",sans-serif}
  .tb-cp.w{color:#fff;text-shadow:0 0 4px #000,0 0 2px #000}.tb-cp.b{color:#14172B;text-shadow:0 0 3px rgba(255,255,255,.5)}
  .tb-four{display:flex;flex-direction:column;gap:6px;user-select:none}
  .tb-four .top,.tb-four .row{display:grid;grid-template-columns:repeat(7,min(10vh,12vw,64px))}
  .tb-four .frame{background:#2346A8;border-radius:16px;padding:6px}
  .tb-four .cell{aspect-ratio:1;display:grid;place-items:center;cursor:pointer}
  .tb-four .cell i{width:80%;height:80%;border-radius:50%;background:#0B0D1A}
  .tb-four .cell i.p1{background:#3FE0E8}.tb-four .cell i.p2{background:#FFC94D}.tb-four .cell i.win{box-shadow:0 0 0 4px #fff}
  .tb-four .top .cell i{background:transparent}.tb-four .top .cell.cur i{background:var(--c)}
  .tb-dice{display:flex;gap:min(12px,2.4vw);justify-content:center}
  .tb-die{width:min(9vh,15vw,64px);aspect-ratio:1;border-radius:18%;background:#fff;box-sizing:border-box;cursor:pointer;position:relative;flex:none}
  .tb-die b{position:absolute;width:19%;height:19%;border-radius:50%;transform:translate(-50%,-50%);background:#14172B}
  .tb-die.kept{background:#FFF3C4;box-shadow:0 0 0 4px #FFC94D}.tb-die.blank{opacity:.35}.tb-die.cur{outline:4px solid #3FE0E8;outline-offset:2px}
  .tb-die.kept::after{content:"Kept";position:absolute;bottom:-22px;left:0;right:0;text-align:center;color:#FFC94D;font-size:13px;font-weight:700}
  .tb-roll{margin-top:22px}.tb-roll.cur{box-shadow:0 0 0 3px #3FE0E8}
  .tb-sheet{border-collapse:collapse;background:rgba(255,255,255,.06);border-radius:12px;font-size:14px}
  .tb-sheet td,.tb-sheet th{padding:4px 8px;text-align:left;white-space:nowrap}
  .tb-sheet tr.box{cursor:pointer}.tb-sheet tr.box:hover,.tb-sheet tr.cur{background:rgba(255,255,255,.18)}
  .tb-sheet th.on{color:#3FE0E8}.tb-sheet .pv{color:rgba(255,201,77,.85)}.tb-sheet .dim{color:var(--ink2)}
  .tb-sol{background:#0F4A56;border-radius:14px;padding:12px;user-select:none;width:min(100%,780px)}
  .tb-sol .tr,.tb-sol .cols{display:grid;grid-template-columns:repeat(7,1fr);gap:min(1.6vw,12px)}
  .tb-sol .cols{margin-top:14px;min-height:50vh}
  .tb-sol .col{position:relative;cursor:pointer}
  .tb-card{width:100%;aspect-ratio:5/7;border-radius:9%;background:#fff;position:relative;box-sizing:border-box;cursor:pointer;font-weight:700;color:#14172B}
  .tb-card.red{color:#D7263D}.tb-card.back{background:linear-gradient(135deg,#1FA3B5,#233C8C);border:2px solid rgba(255,255,255,.5)}
  .tb-card.empty{background:transparent;border:2px solid rgba(255,255,255,.28)}
  .tb-card .r{position:absolute;left:8%;top:3%;font-size:min(2.6vw,20px)}.tb-card .s{position:absolute;inset:0;display:grid;place-items:center;font-size:min(5vw,38px);padding-top:18%;font-family:"DejaVu Sans","Segoe UI Symbol",sans-serif}
  .tb-card.pick{background:#FFF3C4;box-shadow:0 0 0 3px #FFD84A}.tb-card.cur{box-shadow:0 0 0 4px #3FE0E8}
  .tb-sol .col .tb-card{position:absolute;left:0;right:0}
  .tb-setup{margin:auto;display:flex;flex-direction:column;gap:12px;align-items:center;text-align:center;max-width:460px;padding:16px}
  .tb-setup .lv{display:flex;gap:8px;flex-wrap:wrap;justify-content:center}.tb-setup .lv .btn.on{background:#fff;color:#000}
  .tb-step{display:flex;align-items:center;gap:12px}.tb-step b{min-width:28px;text-align:center;font-size:20px}
  .tb-menu{position:absolute;top:10px;right:16px;z-index:2}
  `;
  let styled = false;
  const style = () => { if (styled) return; styled = true; const s = document.createElement("style"); s.textContent = css; document.head.appendChild(s); };
  const pick = (a) => a[Math.floor(Math.random() * a.length)];
  const shuffle = (a) => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };

  // ======================================================= rules (ports of the app's Kotlin engines)

  // ---------- Checkers (American rules; player 1 at the bottom moves first; 1/2 = p1 piece/king, -1/-2 = p2) ----------
  class Checkers {
    constructor(b, turn, quiet) {
      this.board = b || Array.from({ length: 64 }, (_, sq) => (!Checkers.dark(sq) ? 0 : (sq >> 3) <= 2 ? -1 : (sq >> 3) >= 5 ? 1 : 0));
      this.turn = turn || 1; this.quiet = quiet || 0;
    }
    static dark(sq) { return ((sq >> 3) + (sq & 7)) % 2 === 1; }
    copy() { return new Checkers(this.board.slice(), this.turn, this.quiet); }
    sign(p) { return p === 1 ? 1 : -1; }
    dirs(piece) { return piece === 1 ? [[-1, -1], [-1, 1]] : piece === -1 ? [[1, -1], [1, 1]] : [[-1, -1], [-1, 1], [1, -1], [1, 1]]; }
    moves(player) {
      const s = this.sign(player || this.turn), jumps = [], steps = [], b = this.board;
      for (let sq = 0; sq < 64; sq++) {
        if (b[sq] * s <= 0) continue;
        this.jumpsFrom(sq, b[sq], b.slice(), [sq], [], jumps);
        for (const [dr, dc] of this.dirs(b[sq])) {
          const r = (sq >> 3) + dr, c = (sq & 7) + dc;
          if (r >= 0 && r < 8 && c >= 0 && c < 8 && b[r * 8 + c] === 0) steps.push({ path: [sq, r * 8 + c], jumped: [] });
        }
      }
      return jumps.length ? jumps : steps;
    }
    jumpsFrom(sq, piece, b, path, jumped, out) {
      let any = false;
      for (const [dr, dc] of this.dirs(piece)) {
        const r = sq >> 3, c = sq & 7, lr = r + 2 * dr, lc = c + 2 * dc;
        if (lr < 0 || lr > 7 || lc < 0 || lc > 7) continue;
        const mid = (r + dr) * 8 + c + dc, land = lr * 8 + lc;
        if (b[mid] * piece >= 0 || b[land] !== 0) continue;
        any = true;
        const nb = b.slice(); nb[sq] = 0; nb[mid] = 0;
        const crowned = (piece === 1 && lr === 0) || (piece === -1 && lr === 7);
        nb[land] = crowned ? piece * 2 : piece;
        if (crowned) out.push({ path: [...path, land], jumped: [...jumped, mid] });
        else this.jumpsFrom(land, piece, nb, [...path, land], [...jumped, mid], out);
      }
      if (!any && jumped.length) out.push({ path, jumped });
    }
    play(m) {
      const b = this.board, from = m.path[0], to = m.path[m.path.length - 1], piece = b[from];
      b[from] = 0; m.jumped.forEach((j) => { b[j] = 0; });
      const row = to >> 3;
      b[to] = (piece === 1 && row === 0) || (piece === -1 && row === 7) ? piece * 2 : piece;
      this.quiet = m.jumped.length ? 0 : this.quiet + 1;
      this.turn = 3 - this.turn;
    }
    result() { return !this.moves(this.turn).length ? 3 - this.turn : this.quiet >= 80 ? 3 : 0; }
    count(p) { const s = this.sign(p); return this.board.filter((v) => v * s > 0).length; }
    score(player) {
      let v = 0;
      this.board.forEach((p, sq) => { if (!p) return; const row = sq >> 3; const w = p === 1 ? 100 + (7 - row) * 3 : p === -1 ? 100 + row * 3 : 165; v += p > 0 ? w : -w; });
      return player === 1 ? v : -v;
    }
    bestMove(level) {
      const all = this.moves();
      if (!all.length) return null;
      if (all.length === 1) return all[0];
      if (level <= 1 && Math.random() < 1 / 3) return pick(all);
      const depth = level === 1 ? 2 : level === 2 ? 4 : 6;
      let best = -Infinity, picks = [];
      for (const m of shuffle(all.slice())) {
        const n = this.copy(); n.play(m);
        const v = -n.search(depth - 1, -1e6, 1e6);
        if (v > best) { best = v; picks = [m]; } else if (v === best) picks.push(m);
      }
      return picks[0] || all[0];
    }
    search(depth, alpha, beta) {
      const all = this.moves();
      if (!all.length) return -100000 - depth;
      if (depth <= 0) return this.score(this.turn);
      for (const m of all) {
        const n = this.copy(); n.play(m);
        const v = -n.search(depth - 1, -beta, -alpha);
        if (v >= beta) return v;
        if (v > alpha) alpha = v;
      }
      return alpha;
    }
  }

  // ---------- Chess (full rules; row 0 = black's back row; white positive: 1 P 2 N 3 B 4 R 5 Q 6 K) ----------
  const KN = [[-2, -1], [-2, 1], [-1, -2], [-1, 2], [1, -2], [1, 2], [2, -1], [2, 1]];
  const KG = [[-1, -1], [-1, 0], [-1, 1], [0, -1], [0, 1], [1, -1], [1, 0], [1, 1]];
  const DIAG = [[-1, -1], [-1, 1], [1, -1], [1, 1]], STR = [[-1, 0], [1, 0], [0, -1], [0, 1]];
  const VAL = [0, 100, 320, 330, 500, 900, 0];
  const from = (m) => m & 63, to = (m) => (m >>> 6) & 63, promo = (m) => (m >>> 12) & 15;
  class Chess {
    constructor() {
      const back = [4, 2, 3, 5, 6, 3, 2, 4];
      this.board = Array.from({ length: 64 }, (_, sq) => { const r = sq >> 3, c = sq & 7; return r === 0 ? -back[c] : r === 1 ? -1 : r === 6 ? 1 : r === 7 ? back[c] : 0; });
      this.side = 1; this.castle = 15; this.ep = -1; this.half = 0; this.undos = []; this.played = []; this.seen = {};
      this.seen[this.key()] = 1;
    }
    copy() { const c = new Chess(); c.board = this.board.slice(); c.side = this.side; c.castle = this.castle; c.ep = this.ep; c.half = this.half; c.seen = Object.assign({}, this.seen); return c; }
    key() { return this.board.join(",") + "|" + this.side + "|" + this.castle + "|" + this.ep; }
    legalMoves() { const out = []; this.pseudo(out); const me = this.side; return out.filter((m) => { this.make(m); const ok = !this.attacked(this.kingOf(me), -me); this.unmake(); return ok; }); }
    pseudo(out) {
      const s = this.side, b = this.board;
      for (let sq = 0; sq < 64; sq++) {
        const p = b[sq]; if (p * s <= 0) continue;
        const r = sq >> 3, c = sq & 7;
        switch (Math.abs(p)) {
          case 1: this.pawn(sq, r, c, s, out); break;
          case 2: for (const [dr, dc] of KN) this.add(sq, r + dr, c + dc, s, out); break;
          case 3: this.slide(sq, r, c, s, DIAG, out); break;
          case 4: this.slide(sq, r, c, s, STR, out); break;
          case 5: this.slide(sq, r, c, s, DIAG, out); this.slide(sq, r, c, s, STR, out); break;
          case 6: for (const [dr, dc] of KG) this.add(sq, r + dr, c + dc, s, out); this.castles(sq, s, out); break;
        }
      }
    }
    add(f, r, c, s, out) { if (r < 0 || r > 7 || c < 0 || c > 7) return; const t = r * 8 + c; if (this.board[t] * s > 0) return; out.push(f | (t << 6)); }
    slide(f, r0, c0, s, dirs, out) {
      for (const [dr, dc] of dirs) {
        let r = r0 + dr, c = c0 + dc;
        while (r >= 0 && r < 8 && c >= 0 && c < 8) { const t = r * 8 + c, v = this.board[t]; if (v * s > 0) break; out.push(f | (t << 6)); if (v) break; r += dr; c += dc; }
      }
    }
    pawn(sq, r, c, s, out) {
      const b = this.board, dir = s === 1 ? -1 : 1, startRow = s === 1 ? 6 : 1, last = s === 1 ? 0 : 7, one = r + dir;
      if (one < 0 || one > 7) return;
      const push = (t, flag = 0) => { if ((t >> 3) === last) for (const pr of [5, 2, 4, 3]) out.push(sq | (t << 6) | (pr << 12) | (flag << 16)); else out.push(sq | (t << 6) | (flag << 16)); };
      if (b[one * 8 + c] === 0) { push(one * 8 + c); const two = r + 2 * dir; if (r === startRow && b[two * 8 + c] === 0) out.push(sq | ((two * 8 + c) << 6) | (4 << 16)); }
      for (const dc of [-1, 1]) { const cc = c + dc; if (cc < 0 || cc > 7) continue; const t = one * 8 + cc; if (b[t] * s < 0) push(t); if (t === this.ep) out.push(sq | (t << 6) | (1 << 16)); }
    }
    castles(sq, s, out) {
      const row = s === 1 ? 7 : 0, b = this.board;
      if (sq !== row * 8 + 4 || this.attacked(sq, -s)) return;
      const sb = s === 1 ? 1 : 4, lb = s === 1 ? 2 : 8, o = row * 8;
      if (this.castle & sb && !b[o + 5] && !b[o + 6] && b[o + 7] === 4 * s && !this.attacked(o + 5, -s) && !this.attacked(o + 6, -s)) out.push(sq | ((o + 6) << 6) | (2 << 16));
      if (this.castle & lb && !b[o + 3] && !b[o + 2] && !b[o + 1] && b[o] === 4 * s && !this.attacked(o + 3, -s) && !this.attacked(o + 2, -s)) out.push(sq | ((o + 2) << 6) | (2 << 16));
    }
    attacked(sq, by) {
      if (sq < 0) return false;
      const b = this.board, r = sq >> 3, c = sq & 7, pr = by === 1 ? r + 1 : r - 1;
      if (pr >= 0 && pr < 8) { if (c > 0 && b[pr * 8 + c - 1] === by) return true; if (c < 7 && b[pr * 8 + c + 1] === by) return true; }
      for (const [dr, dc] of KN) { const rr = r + dr, cc = c + dc; if (rr >= 0 && rr < 8 && cc >= 0 && cc < 8 && b[rr * 8 + cc] === 2 * by) return true; }
      for (const [dr, dc] of KG) { const rr = r + dr, cc = c + dc; if (rr >= 0 && rr < 8 && cc >= 0 && cc < 8 && b[rr * 8 + cc] === 6 * by) return true; }
      const ray = (dr, dc, kind) => { let rr = r + dr, cc = c + dc; while (rr >= 0 && rr < 8 && cc >= 0 && cc < 8) { const p = b[rr * 8 + cc]; if (p) return p === kind * by || p === 5 * by; rr += dr; cc += dc; } return false; };
      for (const [dr, dc] of DIAG) if (ray(dr, dc, 3)) return true;
      for (const [dr, dc] of STR) if (ray(dr, dc, 4)) return true;
      return false;
    }
    kingOf(s) { return this.board.indexOf(6 * s); }
    inCheck() { return this.attacked(this.kingOf(this.side), -this.side); }
    make(m) {
      const b = this.board, f = from(m), t = to(m), flag = m >>> 16, moved = b[f];
      let captured = b[t];
      b[f] = 0;
      if (flag & 1) { const cs = (f >> 3) * 8 + (t & 7); captured = b[cs]; b[cs] = 0; }
      this.undos.push({ m, moved, captured, castle: this.castle, ep: this.ep, half: this.half });
      const pr = promo(m);
      b[t] = pr ? pr * this.side : moved;
      if (flag & 2) { const row = t >> 3; if ((t & 7) === 6) { b[row * 8 + 5] = b[row * 8 + 7]; b[row * 8 + 7] = 0; } else { b[row * 8 + 3] = b[row * 8]; b[row * 8] = 0; } }
      for (const sq of [f, t]) this.castle &= sq === 60 ? ~3 : sq === 63 ? ~1 : sq === 56 ? ~2 : sq === 4 ? ~12 : sq === 7 ? ~4 : sq === 0 ? ~8 : 15;
      this.ep = flag & 4 ? (f + t) / 2 : -1;
      this.half = Math.abs(moved) === 1 || captured ? 0 : this.half + 1;
      this.side = -this.side;
    }
    unmake() {
      const u = this.undos.pop(), b = this.board, f = from(u.m), t = to(u.m), flag = u.m >>> 16;
      this.side = -this.side;
      b[f] = u.moved;
      if (flag & 1) { b[t] = 0; b[(f >> 3) * 8 + (t & 7)] = u.captured; } else b[t] = u.captured;
      if (flag & 2) { const row = t >> 3; if ((t & 7) === 6) { b[row * 8 + 7] = b[row * 8 + 5]; b[row * 8 + 5] = 0; } else { b[row * 8] = b[row * 8 + 3]; b[row * 8 + 3] = 0; } }
      this.castle = u.castle; this.ep = u.ep; this.half = u.half;
    }
    play(m) { this.make(m); this.played.push(m); const k = this.key(); this.seen[k] = (this.seen[k] || 0) + 1; }
    takeBack() { if (!this.played.length || !this.undos.length) return false; const k = this.key(); this.seen[k] = (this.seen[k] || 1) - 1; this.unmake(); this.played.pop(); return true; }
    state() {
      const ms = this.legalMoves();
      if (!ms.length) return this.inCheck() ? "CHECKMATE" : "STALEMATE";
      if (this.half >= 100) return "FIFTY";
      if ((this.seen[this.key()] || 0) >= 3) return "REPEAT";
      const left = this.board.filter((p) => p && Math.abs(p) !== 6);
      if (!left.length || (left.length === 1 && [2, 3].includes(Math.abs(left[0])))) return "MATERIAL";
      return "PLAYING";
    }
    bestMove(level) {
      const all = this.legalMoves();
      if (!all.length) return null;
      if (all.length === 1) return all[0];
      const depth = level === 1 ? 1 : level === 2 ? 2 : 3;
      this.nodes = 0;
      const scored = shuffle(all.slice()).map((m) => { this.make(m); const v = -this.search(depth - 1, -1e6, 1e6); this.unmake(); return [m, v]; });
      const top = Math.max(...scored.map((x) => x[1]));
      const slack = level <= 1 ? 110 : 0;
      return pick(scored.filter((x) => x[1] >= top - slack))[0];
    }
    order(list) { const b = this.board; return list.sort((x, y) => (Math.abs(b[to(y)]) * 10 - Math.abs(b[from(y)])) - (Math.abs(b[to(x)]) * 10 - Math.abs(b[from(x)]))); }
    search(depth, alpha, beta) {
      this.nodes++;
      if (depth <= 0 || this.nodes > 60000) return this.quiet(alpha, beta, 4);
      const list = []; this.pseudo(list); this.order(list);
      let any = false; const me = this.side;
      for (const m of list) {
        this.make(m);
        if (this.attacked(this.kingOf(me), -me)) { this.unmake(); continue; }
        any = true;
        const v = -this.search(depth - 1, -beta, -alpha);
        this.unmake();
        if (v >= beta) return v;
        if (v > alpha) alpha = v;
      }
      if (!any) return this.attacked(this.kingOf(me), -me) ? -90000 - depth : 0;
      return alpha;
    }
    quiet(alpha, beta, left) {
      this.nodes++;
      const stand = this.eval();
      if (stand >= beta || left <= 0) return stand;
      if (stand > alpha) alpha = stand;
      const list = []; this.pseudo(list);
      const caps = this.order(list.filter((m) => this.board[to(m)] !== 0));
      const me = this.side;
      for (const m of caps) {
        this.make(m);
        if (this.attacked(this.kingOf(me), -me)) { this.unmake(); continue; }
        const v = -this.quiet(-beta, -alpha, left - 1);
        this.unmake();
        if (v >= beta) return v;
        if (v > alpha) alpha = v;
      }
      return alpha;
    }
    eval() {
      let v = 0;
      for (let sq = 0; sq < 64; sq++) {
        const p = this.board[sq]; if (!p) continue;
        const r = sq >> 3, c = sq & 7, centre = 3 - Math.floor(Math.min(Math.abs(2 * r - 7), Math.abs(2 * c - 7)) / 2);
        const a = Math.abs(p);
        const bonus = a === 1 ? (p > 0 ? 6 - r : r - 1) * 6 + (c >= 2 && c <= 5 ? 4 : 0) : a === 2 || a === 3 ? centre * 6 : a === 5 ? centre * 2 : 0;
        v += (p > 0 ? 1 : -1) * (VAL[a] + bonus);
      }
      return v * this.side;
    }
  }
  const sqName = (sq) => "abcdefgh"[sq & 7] + (8 - (sq >> 3));

  // ---------- Four in a Row (7 x 6, cells row by row from the top) ----------
  const LINES = [[0, 1], [1, 0], [1, 1], [1, -1]], ORDER = [3, 2, 4, 1, 5, 0, 6], WINDOW = [0, 1, 6, 40, 0];
  class FourRow {
    constructor(cells, turn) { this.cells = cells || new Array(42).fill(0); this.turn = turn || 1; }
    copy() { return new FourRow(this.cells.slice(), this.turn); }
    canDrop(c) { return c >= 0 && c < 7 && this.cells[c] === 0; }
    drop(c) { if (!this.canDrop(c)) return -1; for (let r = 5; r >= 0; r--) if (!this.cells[r * 7 + c]) { this.cells[r * 7 + c] = this.turn; this.turn = 3 - this.turn; return r * 7 + c; } return -1; }
    winLine() {
      for (let r = 0; r < 6; r++) for (let c = 0; c < 7; c++) {
        const p = this.cells[r * 7 + c]; if (!p) continue;
        for (const [dr, dc] of LINES) {
          const line = [0, 1, 2, 3].map((i) => [r + dr * i, c + dc * i]);
          if (line.every(([rr, cc]) => rr >= 0 && rr < 6 && cc >= 0 && cc < 7 && this.cells[rr * 7 + cc] === p)) return line.map(([rr, cc]) => rr * 7 + cc);
        }
      }
      return null;
    }
    result() { const w = this.winLine(); if (w) return this.cells[w[0]]; return [0, 1, 2, 3, 4, 5, 6].some((c) => this.canDrop(c)) ? 0 : 3; }
    bestColumn(level) {
      const open = [0, 1, 2, 3, 4, 5, 6].filter((c) => this.canDrop(c));
      if (!open.length) return -1;
      const depth = level === 1 ? 2 : level === 2 ? 4 : 6;
      if (level <= 1 && Math.random() < 0.25) return pick(open);
      let best = -Infinity, picks = [];
      for (const c of open.sort((a, b) => Math.abs(a - 3) - Math.abs(b - 3))) {
        const n = this.copy(); n.drop(c);
        const v = -n.search(depth - 1, -1e6, 1e6);
        if (v > best) { best = v; picks = [c]; } else if (v === best) picks.push(c);
      }
      return level <= 1 ? pick(picks) : picks[0];
    }
    search(depth, alpha, beta) {
      const res = this.result();
      if (res === 1 || res === 2) return -100000 - depth;
      if (res === 3) return 0;
      if (depth <= 0) return this.score(this.turn);
      for (const c of ORDER) {
        if (!this.canDrop(c)) continue;
        const n = this.copy(); n.drop(c);
        const v = -n.search(depth - 1, -beta, -alpha);
        if (v >= beta) return v;
        if (v > alpha) alpha = v;
      }
      return alpha;
    }
    score(me) {
      let v = 0;
      for (let r = 0; r < 6; r++) { const p = this.cells[r * 7 + 3]; v += p === me ? 4 : p ? -4 : 0; }
      for (let r = 0; r < 6; r++) for (let c = 0; c < 7; c++) for (const [dr, dc] of LINES) {
        let mine = 0, theirs = 0, ok = true;
        for (let i = 0; i < 4; i++) { const rr = r + dr * i, cc = c + dc * i; if (rr < 0 || rr > 5 || cc < 0 || cc > 6) { ok = false; break; } const p = this.cells[rr * 7 + cc]; if (p === me) mine++; else if (p) theirs++; }
        if (!ok || (mine && theirs)) continue;
        v += WINDOW[mine] - WINDOW[theirs];
      }
      return v;
    }
  }

  // ---------- Five Dice ----------
  const BOXES = [
    ["Ones", 1], ["Twos", 2], ["Threes", 3], ["Fours", 4], ["Fives", 5], ["Sixes", 6],
    ["Three alike", "3K"], ["Four alike", "4K"], ["Full house", "FH"], ["Small run", "SR"], ["Big run", "BR"], ["Five alike", "5K"], ["Chance", "CH"],
  ];
  const boxScore = (i, dice) => {
    const counts = [0, 0, 0, 0, 0, 0, 0]; dice.forEach((d) => counts[d]++);
    const sum = dice.reduce((a, b) => a + b, 0), k = BOXES[i][1];
    const run = (n) => { for (let s = 1; s <= 7 - n; s++) { let ok = true; for (let x = s; x < s + n; x++) if (!counts[x]) ok = false; if (ok) return true; } return false; };
    if (typeof k === "number") return counts[k] * k;
    return k === "3K" ? (counts.some((c) => c >= 3) ? sum : 0) : k === "4K" ? (counts.some((c) => c >= 4) ? sum : 0) : k === "FH" ? (counts.includes(3) && counts.includes(2) ? 25 : 0)
      : k === "SR" ? (run(4) ? 30 : 0) : k === "BR" ? (run(5) ? 40 : 0) : k === "5K" ? (counts.includes(5) ? 50 : 0) : sum;
  };
  const upper = (p) => p.sheet.slice(0, 6).reduce((a, b) => a + (b || 0), 0);
  const total = (p) => p.sheet.reduce((a, b) => a + (b || 0), 0) + (upper(p) >= 63 ? 35 : 0);
  const done = (p) => p.sheet.every((v) => v !== null);
  class FiveDice {
    constructor(players) { this.players = players.map((x) => ({ ...x, sheet: new Array(13).fill(null) })); this.dice = [1, 1, 1, 1, 1]; this.held = [false, false, false, false, false]; this.rolls = 0; this.current = 0; }
    get player() { return this.players[this.current]; }
    get over() { return this.players.every(done); }
    canRoll() { return this.rolls < 3 && !this.over; }
    roll() { if (!this.canRoll()) return; for (let i = 0; i < 5; i++) if (this.rolls === 0 || !this.held[i]) this.dice[i] = 1 + Math.floor(Math.random() * 6); this.rolls++; }
    toggle(i) { if (this.rolls >= 1 && this.rolls <= 2) this.held[i] = !this.held[i]; }
    canScore(i) { return this.rolls > 0 && this.player.sheet[i] === null; }
    score(i) {
      if (!this.canScore(i)) return false;
      this.player.sheet[i] = boxScore(i, this.dice); this.rolls = 0; this.held = [false, false, false, false, false];
      if (!this.over) do { this.current = (this.current + 1) % this.players.length; } while (done(this.players[this.current]));
      return true;
    }
    glowfinHolds() {
      const counts = [0, 0, 0, 0, 0, 0, 0]; this.dice.forEach((d) => counts[d]++);
      const open = (i) => this.player.sheet[i] === null;
      const has = new Set(this.dice);
      const start = [1, 2, 3].find((s) => [0, 1, 2, 3].every((x) => has.has(s + x)));
      if (start && (open(10) || open(9))) { const want = new Set([0, 1, 2, 3].map((x) => start + x)); return this.dice.map((d) => (want.has(d) ? (want.delete(d), true) : false)); }
      let face = 6; for (let f = 6; f >= 1; f--) if (counts[f] * 10 + f > counts[face] * 10 + face) face = f;
      return this.dice.map((d) => d === face);
    }
    glowfinBox() {
      let best = -1, bestV = -Infinity;
      for (let i = 0; i < 13; i++) {
        if (this.player.sheet[i] !== null) continue;
        const s = boxScore(i, this.dice);
        const v = i < 6 ? s * 1.6 - (i + 1) * 2 : i === 12 ? s * 0.55 : i === 11 && s === 0 ? -8 : s === 0 ? -2 - (i + 6) * 0.1 : s;
        if (v > bestV) { bestV = v; best = i; }
      }
      return best;
    }
  }

  // ---------- Solitaire (Klondike, turn one) ----------
  const SUITS = ["♣", "♦", "♥", "♠"];
  const rankName = (r) => ({ 1: "A", 11: "J", 12: "Q", 13: "K" })[r] || String(r);
  const isRed = (c) => c.s === 1 || c.s === 2;
  class Solitaire {
    constructor() {
      const deck = shuffle([0, 1, 2, 3].flatMap((s) => Array.from({ length: 13 }, (_, i) => ({ r: i + 1, s }))));
      this.columns = Array.from({ length: 7 }, () => []); this.hidden = new Array(7).fill(0);
      for (let c = 0; c < 7; c++) { for (let i = 0; i <= c; i++) this.columns[c].push(deck.pop()); this.hidden[c] = c; }
      this.stock = deck; this.waste = []; this.homes = [[], [], [], []]; this.moves = 0; this.history = [];
    }
    save() { this.history.push(JSON.stringify([this.stock, this.waste, this.homes, this.columns, this.hidden])); if (this.history.length > 200) this.history.shift(); }
    undo() { const s = this.history.pop(); if (!s) return; [this.stock, this.waste, this.homes, this.columns, this.hidden] = JSON.parse(s); this.moves++; }
    draw() { if (!this.stock.length && !this.waste.length) return; this.save(); if (!this.stock.length) { this.stock = this.waste.reverse(); this.waste = []; } else this.waste.push(this.stock.pop()); this.moves++; }
    pickUp(p, i) {
      if (p.k === "w") return this.waste.length ? [this.waste[this.waste.length - 1]] : [];
      if (p.k === "h") { const h = this.homes[p.i]; return h.length ? [h[h.length - 1]] : []; }
      const col = this.columns[p.i], idx = i == null || i < 0 ? col.length - 1 : i;
      return idx < this.hidden[p.i] || idx < 0 || idx >= col.length ? [] : col.slice(idx);
    }
    canPlace(cards, p) {
      const f = cards[0]; if (!f) return false;
      if (p.k === "h") { if (cards.length !== 1) return false; const h = this.homes[p.i], t = h[h.length - 1]; return t ? t.s === f.s && f.r === t.r + 1 : f.r === 1; }
      if (p.k === "c") { const col = this.columns[p.i], t = col[col.length - 1]; return t ? isRed(t) !== isRed(f) && f.r === t.r - 1 : f.r === 13; }
      return false;
    }
    move(p, i, dest) {
      if (p.k === dest.k && p.i === dest.i) return false;
      const cards = this.pickUp(p, i);
      if (!this.canPlace(cards, dest)) return false;
      this.save();
      if (p.k === "w") this.waste.pop();
      else if (p.k === "h") this.homes[p.i].pop();
      else { const col = this.columns[p.i]; col.splice(col.length - cards.length); if (this.hidden[p.i] >= col.length && col.length) this.hidden[p.i] = col.length - 1; if (!col.length) this.hidden[p.i] = 0; }
      (dest.k === "h" ? this.homes[dest.i] : this.columns[dest.i]).push(...cards);
      this.moves++;
      return true;
    }
    sendHome(p, i) { if (this.pickUp(p, i).length !== 1) return false; for (let h = 0; h < 4; h++) if (this.move(p, i, { k: "h", i: h })) return true; return false; }
    won() { return this.homes.every((h) => h.length === 13); }
    canFinish() { return !this.won() && !this.stock.length && !this.waste.length && this.hidden.every((h) => h === 0); }
    finishStep() { for (let c = 0; c < 7; c++) if (this.columns[c].length && this.sendHome({ k: "c", i: c })) return true; return false; }
  }

  // ======================================================= screens

  const tag = (name, color, on, extra) => `<div class="tb-tag${on ? " on" : ""}" style="--c:${color}"><i></i><div><b>${esc(name)}</b>${extra ? `<small>${esc(extra)}</small>` : ""}</div></div>`;
  const P1 = "#3FE0E8", P2 = "#E2607A", GOLD = "#FFC94D";

  B.bgTable = (ctx) => {
    style();
    const g = ctx.game, stage = ctx.stage;
    const me = (B.me && B.me.name ? String(B.me.name).trim().split(/\s+/)[0] : "") || "Player 1";
    stage.style.alignItems = "center";
    let keyHandler = null;
    const onKey = (e) => { if (!ctx.alive()) { document.removeEventListener("keydown", onKey, true); return; } if (keyHandler && keyHandler(e)) { e.preventDefault(); e.stopPropagation(); } };
    document.addEventListener("keydown", onKey, true);
    if (B.onLeave) B.onLeave(() => document.removeEventListener("keydown", onKey, true));
    const dir = (e) => ({ ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] })[e.key];
    const ok = (e) => e.key === "Enter" || e.key === " ";

    // A card over the game: the menu or the end of the game
    const overlay = (title, lines, buttons) => {
      const box = document.createElement("div");
      box.className = "bg-over";
      box.innerHTML = `<div class="bg-card"><h2>${esc(title)}</h2>${lines.map((l) => `<p>${esc(l)}</p>`).join("")}<div class="acts">${buttons.map((b, i) => `<button class="btn${i === 0 ? " white" : ""}" data-i="${i}">${esc(b[0])}</button>`).join("")}</div></div>`;
      stage.appendChild(box);
      const saved = keyHandler;
      keyHandler = (e) => { if (e.key === "Escape") { close(); return true; } return false; };
      const close = () => { box.remove(); keyHandler = saved; };
      box.querySelectorAll("button").forEach((b) => { b.onclick = () => { close(); buttons[+b.dataset.i][1](); }; });
      setTimeout(() => { const b = box.querySelector("button"); if (b) b.focus(); }, 50);
      return close;
    };
    const restart = () => ctx.again();
    const menu = (title, extra) => overlay(title, [], [["Keep playing", () => {}], ...(extra || []), ["New game", restart], ["Leave", ctx.quit]]);

    // ---------- who's playing ----------
    const setup = (start) => {
      if (!g.versus) return start({ people: 1, glowfin: 0, level: 2 });
      const dice = g.game === "FIVE_DICE";
      let people = 1, glowfin = 1, level = 2;
      const draw = () => {
        stage.innerHTML = `<div class="tb-setup"><h2>${esc(g.title)}</h2><p class="dim">Who's playing?</p>${dice
          ? `<div class="tb-step"><span>People</span><button class="btn small" data-a="p-">−</button><b>${people}</b><button class="btn small" data-a="p+">+</button></div>
             <div class="tb-step"><span>Glowfin players</span><button class="btn small" data-a="g-">−</button><b>${glowfin}</b><button class="btn small" data-a="g+">+</button></div>`
          : ""}<div class="lv">${["Easy", "Medium", "Hard"].map((l, i) => `<button class="btn${level === i + 1 ? " on" : ""}" data-a="l${i + 1}">${l}</button>`).join("")}</div>
          <button class="btn white" data-a="go">${dice ? "Start" : "Play Glowfin"}</button>${dice ? "" : `<button class="btn" data-a="two">Two people</button>`}<button class="btn" data-a="back">Back</button></div>`;
        stage.querySelectorAll("[data-a]").forEach((b) => {
          b.onclick = () => {
            const a = b.dataset.a;
            if (a === "p-") people = Math.max(1, people - 1);
            else if (a === "p+") people = Math.min(6 - glowfin, people + 1);
            else if (a === "g-") glowfin = Math.max(people > 1 ? 0 : 0, glowfin - 1);
            else if (a === "g+") glowfin = Math.min(6 - people, glowfin + 1);
            else if (a[0] === "l") level = +a[1];
            else if (a === "go") return start({ people, glowfin: dice ? glowfin : 1, level });
            else if (a === "two") return start({ people: 2, glowfin: 0, level });
            else if (a === "back") return ctx.quit();
            draw();
            const again = stage.querySelector(`[data-a="${a}"]`); if (again) again.focus();
          };
        });
        const first = stage.querySelector('[data-a="go"]'); if (first) first.focus();
      };
      keyHandler = null;
      draw();
    };

    // Timers from a finished game never touch a newer one
    const token = {};
    stage.tbToken = token;
    const later = (fn, ms) => setTimeout(() => { if (ctx.alive() && stage.isConnected && stage.tbToken === token) fn(); }, ms);

    // ---------- Checkers ----------
    const checkers = (seats) => {
      const game = new Checkers(), history = [];
      const vs = seats.glowfin > 0, names = [me, vs ? "Glowfin" : "Player 2"];
      let cursor = 40, picked = -1, last = null, thinking = false, over = false;
      const glowfinTurn = () => vs && game.turn === 2 && !game.result();
      const play = (m) => { history.push(game.copy()); game.play(m); last = m; picked = -1; draw(); };
      const act = (sq) => {
        if (thinking || over || glowfinTurn()) return;
        cursor = sq;
        const moves = game.moves();
        if (moves.some((m) => m.path[0] === sq)) picked = picked === sq ? -1 : sq;
        else if (picked >= 0) { const m = moves.find((x) => x.path[0] === picked && x.path[x.path.length - 1] === sq); if (m) return play(m); picked = -1; }
        draw();
      };
      const draw = () => {
        const moves = game.moves(), res = game.result();
        const targets = new Set(picked >= 0 ? moves.filter((m) => m.path[0] === picked).map((m) => m.path[m.path.length - 1]) : []);
        const jumps = moves.some((m) => m.jumped.length);
        stage.innerHTML = `<div class="tb-wrap"><div class="tb-side"><h2>Checkers</h2>${tag(names[0], P1, game.turn === 1 && !res, `${game.count(1)} pieces`)}${tag(names[1], P2, game.turn === 2 && !res, `${game.count(2)} pieces`)}
          <div class="tb-say">${glowfinTurn() ? "Glowfin is thinking…" : picked >= 0 ? "Now pick where it goes." : jumps ? `${esc(names[game.turn - 1])}: you must jump.` : `${esc(names[game.turn - 1])}'s move. Pick a piece.`}</div>
          <div class="dim">Esc opens the menu.</div><button class="btn small" id="tbMenu">Menu</button></div>
          <div class="tb-board">${game.board.map((p, sq) => `<div class="tb-sq ${Checkers.dark(sq) ? "d" : "l"}${picked === sq ? " pick" : ""}${last && last.path.includes(sq) ? " last" : ""}${cursor === sq ? " cur" : ""}" data-sq="${sq}">${p ? `<div class="tb-pc" style="background:${p > 0 ? P1 : P2}">${Math.abs(p) === 2 ? "♚" : ""}</div>` : ""}${targets.has(sq) ? '<i class="tb-dot"></i>' : ""}</div>`).join("")}</div></div>`;
        stage.querySelectorAll("[data-sq]").forEach((el) => { el.onclick = () => act(+el.dataset.sq); });
        $("#tbMenu").onclick = openMenu;
        if (res && !over) {
          over = true;
          overlay(res === 3 ? "It's a draw" : `${names[res - 1]} wins!`, [res === 3 ? "Too many moves with no jumps." : `No moves left for ${names[2 - res]}.`], [["Play again", restart], ["Leave", ctx.quit]]);
        } else if (glowfinTurn() && !thinking) {
          thinking = true;
          later(() => { const m = game.copy().bestMove(seats.level); thinking = false; if (m) play(m); }, 450);
        }
      };
      const openMenu = () => menu("Checkers", history.length ? [["Take back a move", () => {
        const back = vs && history.length >= 2 ? 2 : 1;
        for (let i = 0; i < back; i++) { const s = history.pop(); if (s) { game.board = s.board; game.turn = s.turn; game.quiet = s.quiet; } }
        last = null; picked = -1; draw();
      }]] : []);
      keyHandler = (e) => { const d = dir(e); if (d) { cursor = Math.min(7, Math.max(0, (cursor >> 3) + d[1])) * 8 + Math.min(7, Math.max(0, (cursor & 7) + d[0])); draw(); return true; } if (ok(e)) { act(cursor); return true; } if (e.key === "Escape") { openMenu(); return true; } return false; };
      draw();
    };

    // ---------- Chess ----------
    const GLYPH = " ♟♞♝♜♛♚";
    const chess = (seats) => {
      const game = new Chess(), vs = seats.glowfin > 0, names = [me, vs ? "Glowfin" : "Player 2"];
      let cursor = 52, picked = -1, thinking = false, over = false;
      const glowfinTurn = () => vs && game.side === -1 && game.state() === "PLAYING";
      const play = (m) => { game.play(m); picked = -1; draw(); };
      const act = (sq) => {
        if (thinking || over || glowfinTurn()) return;
        cursor = sq;
        const moves = game.legalMoves();
        if (moves.some((m) => from(m) === sq)) picked = picked === sq ? -1 : sq;
        else if (picked >= 0) {
          const ms = moves.filter((m) => from(m) === picked && to(m) === sq).sort((a, b) => promo(b) - promo(a));
          if (ms.length) return play(ms[0]);
          picked = -1;
        }
        draw();
      };
      const draw = () => {
        const moves = game.legalMoves(), state = game.state(), check = game.inCheck(), turn = game.side === 1 ? 0 : 1;
        const targets = new Set(picked >= 0 ? moves.filter((m) => from(m) === picked).map(to) : []);
        const lm = game.played[game.played.length - 1], lastSq = lm != null ? [from(lm), to(lm)] : [];
        const king = check ? game.kingOf(game.side) : -1;
        stage.innerHTML = `<div class="tb-wrap"><div class="tb-side"><h2>Chess</h2>${tag(names[1], "#1E2238", turn === 1 && state === "PLAYING", "Black")}${tag(names[0], "#FFFFFF", turn === 0 && state === "PLAYING", "White")}
          <div class="tb-say${check ? " bad" : ""}">${glowfinTurn() ? "Glowfin is thinking…" : check ? `${esc(names[turn])}: your king is in check!` : picked >= 0 ? "Now pick where it goes." : `${esc(names[turn])}'s move.`}</div>
          ${lm != null ? `<div class="dim">Last move: ${sqName(from(lm))} → ${sqName(to(lm))}</div>` : ""}<div class="dim">Esc opens the menu.</div><button class="btn small" id="tbMenu">Menu</button></div>
          <div class="tb-board" style="border-color:#1E2238">${game.board.map((p, sq) => `<div class="tb-sq ${sq === king ? "check" : ((sq >> 3) + (sq & 7)) % 2 ? "d" : "l"}${picked === sq ? " pick" : ""}${lastSq.includes(sq) ? " last" : ""}${cursor === sq ? " cur" : ""}" data-sq="${sq}">${p ? `<span class="tb-cp ${p > 0 ? "w" : "b"}">${GLYPH[Math.abs(p)]}&#xFE0E;</span>` : ""}${targets.has(sq) ? '<i class="tb-dot"></i>' : ""}</div>`).join("")}</div></div>`;
        stage.querySelectorAll("[data-sq]").forEach((el) => { el.onclick = () => act(+el.dataset.sq); });
        $("#tbMenu").onclick = openMenu;
        if (state !== "PLAYING" && !over) {
          over = true;
          const why = { STALEMATE: "No legal moves, but no check: stalemate.", FIFTY: "50 moves each with no capture or pawn move.", REPEAT: "The same position came up three times.", MATERIAL: "Not enough pieces left to checkmate." }[state] || "Well played.";
          overlay(state === "CHECKMATE" ? `Checkmate! ${names[1 - turn]} wins` : "It's a draw", [why], [["Play again", restart], ["Leave", ctx.quit]]);
        } else if (glowfinTurn() && !thinking) {
          thinking = true;
          later(() => { const m = game.copy().bestMove(seats.level); thinking = false; if (m != null) play(m); }, 450);
        }
      };
      const openMenu = () => menu("Chess", game.played.length ? [["Take back a move", () => { game.takeBack(); if (vs && game.side === -1) game.takeBack(); picked = -1; draw(); }]] : []);
      keyHandler = (e) => { const d = dir(e); if (d) { const r = Math.min(7, Math.max(0, (cursor >> 3) + d[1])), c = Math.min(7, Math.max(0, (cursor & 7) + d[0])); cursor = r * 8 + c; draw(); return true; } if (ok(e)) { act(cursor); return true; } if (e.key === "Escape") { openMenu(); return true; } return false; };
      draw();
    };

    // ---------- Four in a Row ----------
    const four = (seats) => {
      const game = new FourRow(), history = [], vs = seats.glowfin > 0, names = [me, vs ? "Glowfin" : "Player 2"], colors = [P1, GOLD];
      let column = 3, thinking = false, over = false;
      const glowfinTurn = () => vs && game.turn === 2 && !game.result();
      const drop = (c) => { if (!game.canDrop(c)) return; history.push(game.copy()); game.drop(c); draw(); };
      const act = (c) => { if (thinking || over || glowfinTurn()) return; column = c; drop(c); };
      const draw = () => {
        const res = game.result(), line = new Set(game.winLine() || []);
        stage.innerHTML = `<div class="tb-wrap"><div class="tb-side"><h2>Four in a Row</h2>${tag(names[0], colors[0], game.turn === 1 && !res)}${tag(names[1], colors[1], game.turn === 2 && !res)}
          <div class="tb-say">${glowfinTurn() ? "Glowfin is thinking…" : `${esc(names[game.turn - 1])}: pick a column.`}</div><div class="dim">Esc opens the menu.</div><button class="btn small" id="tbMenu">Menu</button></div>
          <div class="tb-four" style="--c:${colors[game.turn - 1]}"><div class="top">${[0, 1, 2, 3, 4, 5, 6].map((c) => `<div class="cell${c === column && !res && !glowfinTurn() ? " cur" : ""}" data-c="${c}"><i></i></div>`).join("")}</div>
          <div class="frame">${[0, 1, 2, 3, 4, 5].map((r) => `<div class="row">${[0, 1, 2, 3, 4, 5, 6].map((c) => { const v = game.cells[r * 7 + c]; return `<div class="cell" data-c="${c}"><i class="${v ? "p" + v : ""}${line.has(r * 7 + c) ? " win" : ""}"></i></div>`; }).join("")}</div>`).join("")}</div></div></div>`;
        stage.querySelectorAll("[data-c]").forEach((el) => { el.onclick = () => act(+el.dataset.c); el.onmouseenter = () => { if (column !== +el.dataset.c && !glowfinTurn() && !over) { column = +el.dataset.c; stage.querySelectorAll(".top .cell").forEach((t, i) => t.classList.toggle("cur", i === column)); } }; });
        $("#tbMenu").onclick = openMenu;
        if (res && !over) {
          over = true;
          overlay(res === 3 ? "It's a draw" : `${names[res - 1]} wins!`, [res === 3 ? "The frame is full." : "Four in a row."], [["Play again", restart], ["Leave", ctx.quit]]);
        } else if (glowfinTurn() && !thinking) {
          thinking = true;
          later(() => { const c = game.copy().bestColumn(seats.level); thinking = false; if (c >= 0) drop(c); }, 450);
        }
      };
      const openMenu = () => menu("Four in a Row", history.length ? [["Take back a move", () => {
        const back = vs && history.length >= 2 ? 2 : 1;
        for (let i = 0; i < back; i++) { const s = history.pop(); if (s) { game.cells = s.cells; game.turn = s.turn; } }
        draw();
      }]] : []);
      keyHandler = (e) => { const d = dir(e); if (d && d[0]) { column = Math.min(6, Math.max(0, column + d[0])); draw(); return true; } if (ok(e) || (d && d[1] > 0)) { act(column); return true; } if (e.key === "Escape") { openMenu(); return true; } return false; };
      draw();
    };

    // ---------- Five Dice ----------
    const PIPS = { 1: [4], 2: [0, 8], 3: [0, 4, 8], 4: [0, 2, 6, 8], 5: [0, 2, 4, 6, 8], 6: [0, 2, 3, 5, 6, 8] };
    const fiveDice = (seats) => {
      const players = [...Array.from({ length: Math.max(1, seats.people) }, (_, i) => ({ name: i ? `Player ${i + 1}` : me, glowfin: false })),
        ...Array.from({ length: seats.glowfin }, (_, i) => ({ name: seats.glowfin === 1 ? "Glowfin" : `Glowfin ${i + 1}`, glowfin: true }))];
      const game = new FiveDice(players);
      let zone = "roll", spot = 0, busy = false, over = false;
      const glowfinTurn = () => !game.over && game.player.glowfin;
      const act = () => {
        if (over || glowfinTurn()) return;
        if (zone === "dice") game.toggle(spot);
        else if (zone === "roll") { game.roll(); if (!game.canRoll()) { zone = "sheet"; spot = Math.max(0, [...Array(13).keys()].find((i) => game.canScore(i)) ?? 0); } }
        else if (game.score(spot)) zone = "roll";
        draw();
      };
      const glowfinPlay = () => {
        busy = true;
        const step = () => {
          if (!ctx.alive()) return;
          if (game.rolls === 0) { game.roll(); draw(); return later(step, 900); }
          if (game.canRoll()) { const keep = game.glowfinHolds(); if (!keep.every(Boolean)) { game.held = keep; draw(); return later(() => { game.roll(); draw(); later(step, 900); }, 700); } }
          game.score(game.glowfinBox()); busy = false; draw();
        };
        later(step, 600);
      };
      const draw = () => {
        const p = game.player, gt = glowfinTurn(), canRoll = game.canRoll() && !gt;
        stage.innerHTML = `<div class="tb-wrap"><div class="tb-side" style="align-items:center;text-align:center;max-width:420px"><h2>Five Dice</h2>
          <div class="tb-say">${game.over ? "Game over" : gt ? `${esc(p.name)} is playing…` : `${esc(p.name)}'s turn`}</div>
          <div class="tb-dice">${game.dice.map((d, i) => `<div class="tb-die${game.held[i] && game.rolls ? " kept" : ""}${!game.rolls ? " blank" : ""}${zone === "dice" && spot === i ? " cur" : ""}" data-d="${i}">${game.rolls ? PIPS[d].map((k) => `<b style="left:${24 + (k % 3) * 26}%;top:${24 + Math.floor(k / 3) * 26}%"></b>`).join("") : ""}</div>`).join("")}</div>
          <button class="btn ${canRoll ? "white" : ""} tb-roll${zone === "roll" ? " cur" : ""}" id="tbRoll" ${canRoll ? "" : "disabled"}>${gt ? "Glowfin is rolling" : game.rolls === 0 ? "Roll" : game.canRoll() ? `Roll again (${3 - game.rolls} left)` : "Pick a box"}</button>
          <div class="dim">${gt ? "Watch Glowfin pick its dice." : game.rolls === 0 ? "Roll up to 3 times. Keep the dice you like." : "Click a die to keep it. Then pick a box on the sheet."}</div>
          <button class="btn small" id="tbMenu">Menu</button></div>
          <table class="tb-sheet"><tr><th></th>${game.players.map((x, i) => `<th class="${i === game.current && !game.over ? "on" : ""}">${esc(x.name)}</th>`).join("")}</tr>
          ${BOXES.map(([label], bi) => `<tr class="box${zone === "sheet" && spot === bi ? " cur" : ""}" data-b="${bi}"><td>${label}</td>${game.players.map((x, pi) => { const f = x.sheet[bi]; const pv = pi === game.current && !game.over && !gt && f === null && game.rolls > 0; return `<td class="${f === null ? "pv" : ""}">${f !== null ? f : pv ? boxScore(bi, game.dice) : ""}</td>`; }).join("")}</tr>${bi === 5 ? `<tr><td class="dim">Bonus (63+)</td>${game.players.map((x) => `<td class="dim">${upper(x) >= 63 ? 35 : upper(x) + "/63"}</td>`).join("")}</tr>` : ""}`).join("")}
          <tr><td><b>Total</b></td>${game.players.map((x) => `<td><b style="color:${GOLD}">${total(x)}</b></td>`).join("")}</tr></table></div>`;
        stage.querySelectorAll("[data-d]").forEach((el) => { el.onclick = () => { zone = "dice"; spot = +el.dataset.d; act(); }; });
        stage.querySelectorAll("[data-b]").forEach((el) => { el.onclick = () => { zone = "sheet"; spot = +el.dataset.b; act(); }; });
        $("#tbRoll").onclick = () => { zone = "roll"; act(); };
        $("#tbMenu").onclick = openMenu;
        if (game.over && !over) {
          over = true;
          const top = Math.max(...game.players.map(total)), win = game.players.filter((x) => total(x) === top);
          overlay(win.length > 1 ? "It's a tie!" : `${win[0].name} wins!`, game.players.slice().sort((a, b) => total(b) - total(a)).map((x) => `${x.name}: ${total(x)}`), [["Play again", restart], ["Leave", ctx.quit]]);
        } else if (gt && !busy) glowfinPlay();
      };
      const openMenu = () => menu("Five Dice");
      keyHandler = (e) => {
        const d = dir(e);
        if (d) {
          if (zone === "dice") { if (d[1] > 0) zone = "roll"; else if (d[0] > 0 && spot === 4) { zone = "sheet"; spot = 0; } else if (d[0]) spot = Math.min(4, Math.max(0, spot + d[0])); }
          else if (zone === "roll") { if (d[1] < 0) { zone = "dice"; spot = 0; } else { zone = "sheet"; spot = Math.max(0, [...Array(13).keys()].find((i) => game.canScore(i)) ?? 0); } }
          else if (d[0] < 0) zone = "roll"; else if (d[1]) spot = Math.min(12, Math.max(0, spot + d[1]));
          draw(); return true;
        }
        if (ok(e)) { act(); return true; }
        if (e.key === "Escape") { openMenu(); return true; }
        return false;
      };
      draw();
    };

    // ---------- Solitaire ----------
    const solitaire = () => {
      const game = new Solitaire();
      let cur = { top: false, slot: 0, i: 0 }, picked = null, finishing = false, over = false;
      const pileAt = (c) => (c.top && c.slot === 1 ? [{ k: "w" }, -1] : c.top && c.slot >= 3 ? [{ k: "h", i: c.slot - 3 }, -1] : !c.top ? [{ k: "c", i: c.slot }, c.i] : null);
      const same = (a, b) => a.k === b.k && a.i === b.i;
      const act = (c) => {
        if (finishing || over) return;
        cur = c;
        if (c.top && c.slot === 0) { picked = null; game.draw(); return draw(); }
        const here = pileAt(c); if (!here) return;
        if (!picked) { if (game.pickUp(here[0], here[1]).length) picked = here; return draw(); }
        const held = picked, isSame = same(held[0], here[0]);
        let moved;
        if (isSame) {
          moved = game.sendHome(held[0], held[1]);
          if (!moved) { const cards = game.pickUp(held[0], held[1]); for (let col = 0; col < 7 && !moved; col++) if (game.canPlace(cards, { k: "c", i: col })) moved = game.move(held[0], held[1], { k: "c", i: col }); }
        } else moved = game.move(held[0], held[1], here[0]);
        picked = null;
        if (!moved && !isSame && game.pickUp(here[0], here[1]).length) picked = here;
        draw();
      };
      const cardHtml = (card, up, cls, data) => !card ? `<div class="tb-card empty ${cls}" ${data}></div>` : !up ? `<div class="tb-card back ${cls}" ${data}></div>` : `<div class="tb-card${isRed(card) ? " red" : ""} ${cls}" ${data}><span class="r">${rankName(card.r)}${SUITS[card.s]}&#xFE0E;</span><span class="s">${SUITS[card.s]}&#xFE0E;</span></div>`;
      const draw = () => {
        const isCur = (c) => cur.top === c.top && cur.slot === c.slot && (c.top || cur.i === c.i);
        const top = [0, 1, 2, 3, 4, 5, 6].map((s) => {
          const c = { top: true, slot: s }, cc = isCur(c) ? " cur" : "", d = `data-t="${s}"`;
          if (s === 0) return cardHtml(game.stock[game.stock.length - 1], false, cc, d);
          if (s === 1) return cardHtml(game.waste[game.waste.length - 1], true, cc + (picked && picked[0].k === "w" ? " pick" : ""), d);
          if (s === 2) return "<div></div>";
          const h = game.homes[s - 3];
          return cardHtml(h[h.length - 1], true, cc + (picked && picked[0].k === "h" && picked[0].i === s - 3 ? " pick" : ""), d);
        }).join("");
        const cols = game.columns.map((col, ci) => {
          const hid = game.hidden[ci];
          const cards = col.map((card, i) => {
            const up = i >= hid, held = picked && picked[0].k === "c" && picked[0].i === ci && i >= picked[1];
            return cardHtml(card, up, (held ? " pick" : "") + (!cur.top && cur.slot === ci && cur.i === i ? " cur" : ""), `data-c="${ci}" data-i="${i}" style="top:calc(${col.slice(0, i).reduce((a, _, k) => a + (k >= hid ? 26 : 11), 0)} * var(--u))"`);
          }).join("");
          return `<div class="col" data-col="${ci}">${col.length ? "" : cardHtml(null, true, !cur.top && cur.slot === ci ? " cur" : "", "")}${cards}</div>`;
        }).join("");
        stage.innerHTML = `<div class="tb-wrap" style="flex-direction:column;gap:8px"><div style="display:flex;gap:12px;align-items:center;width:min(100%,780px)"><b>Solitaire</b><span class="dim" style="flex:1">${finishing ? "Finishing by itself…" : picked ? "Now pick where it goes. Click it again to send it up." : "Click the deck to turn a card. Esc opens the menu."}</span><span class="dim">Moves: ${game.moves}</span><button class="btn small" id="tbMenu">Menu</button></div>
          <div class="tb-sol"><div class="tr">${top}</div><div class="cols">${cols}</div></div></div>`;
        // Squeeze long columns so they fit
        const sol = stage.querySelector(".tb-sol"), w = sol.querySelector(".tr > *").getBoundingClientRect().width || 80;
        const maxH = Math.max(w * 1.4, window.innerHeight - sol.getBoundingClientRect().top - w * 1.4 - 60);
        const longest = Math.max(...game.columns.map((col, ci) => col.slice(0, -1).reduce((a, _, k) => a + (k >= game.hidden[ci] ? 26 : 11), 0)));
        const u = Math.min(w * 1.4 / 100, longest ? (maxH - w * 1.4) / longest : 9);
        sol.style.setProperty("--u", `${Math.max(1.2, u)}px`);
        sol.querySelector(".cols").style.minHeight = `${w * 1.4 + longest * Math.max(1.2, u) + 8}px`;
        stage.querySelectorAll("[data-t]").forEach((el) => { el.onclick = () => act({ top: true, slot: +el.dataset.t }); el.ondblclick = () => { if (+el.dataset.t === 1 && game.sendHome({ k: "w" })) { picked = null; draw(); } }; });
        stage.querySelectorAll("[data-c]").forEach((el) => { el.onclick = (e) => { e.stopPropagation(); act({ top: false, slot: +el.dataset.c, i: +el.dataset.i }); }; el.ondblclick = (e) => { e.stopPropagation(); if (game.sendHome({ k: "c", i: +el.dataset.c }, +el.dataset.i)) { picked = null; draw(); } }; });
        stage.querySelectorAll("[data-col]").forEach((el) => { el.onclick = () => act({ top: false, slot: +el.dataset.col, i: Math.max(0, game.columns[+el.dataset.col].length - 1) }); });
        $("#tbMenu").onclick = openMenu;
        if (game.won() && !over) { over = true; overlay("You won!", [`Every card is home in ${game.moves} moves.`], [["Play again", restart], ["Leave", ctx.quit]]); }
        else if (game.canFinish() && !finishing) {
          finishing = true; picked = null;
          const step = () => { if (game.finishStep()) { draw(); later(step, 110); } else { finishing = false; draw(); } };
          later(step, 200);
        }
      };
      const openMenu = () => menu("Solitaire", game.history.length ? [["Undo", () => { game.undo(); picked = null; draw(); }]] : []);
      keyHandler = (e) => {
        const d = dir(e);
        if (d) {
          if (cur.top) {
            if (d[0]) { let s = Math.min(6, Math.max(0, cur.slot + d[0])); if (s === 2) s = d[0] > 0 ? 3 : 1; cur = { top: true, slot: s }; }
            else if (d[1] > 0) cur = { top: false, slot: cur.slot, i: Math.max(0, game.columns[cur.slot].length - 1) };
          } else if (d[0]) { const s = Math.min(6, Math.max(0, cur.slot + d[0])); cur = { top: false, slot: s, i: Math.max(0, game.columns[s].length - 1) }; }
          else if (d[1] < 0) cur = cur.i - 1 >= game.hidden[cur.slot] && cur.i - 1 >= 0 ? { ...cur, i: cur.i - 1 } : { top: true, slot: cur.slot === 2 ? 1 : cur.slot };
          else cur = { ...cur, i: Math.min(Math.max(0, game.columns[cur.slot].length - 1), cur.i + 1) };
          draw(); return true;
        }
        if (ok(e)) { act(cur); return true; }
        if (e.key === "Escape") { if (picked) { picked = null; draw(); } else openMenu(); return true; }
        return false;
      };
      draw();
      const resize = () => { if (!ctx.alive()) return window.removeEventListener("resize", resize); draw(); };
      window.addEventListener("resize", resize);
    };

    const run = { CHECKERS: checkers, CHESS: chess, FOUR_ROW: four, FIVE_DICE: fiveDice, SOLITAIRE: solitaire }[g.game];
    if (!run) throw new Error("That table game isn't here yet.");
    setup(run);
  };

  // The rules on their own, for tests
  B.tableRules = { Checkers, Chess, FourRow, FiveDice, Solitaire, boxScore };
})();
