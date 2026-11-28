/* Glowfin web: the Table card games (the same rules as the app's ui/games/table/Hearts.kt, Spades.kt, CrazyEights.kt,
   Rummy.kt, GoFish.kt and Holdem.kt). On the web you sit at the bottom of the table and Glowfin plays every other seat.
   Mouse, touch or keyboard (arrows + Enter; Esc for the menu). */
"use strict";
(() => {
  // ======================================================= cards
  const SUITS = ["♣", "♦", "♥", "♠"], SUIT_NAMES = ["clubs", "diamonds", "hearts", "spades"];
  const rankName = (r) => ({ 1: "A", 11: "J", 12: "Q", 13: "K" })[r] || String(r);
  const high = (c) => (c.r === 1 ? 14 : c.r);
  const label = (c) => rankName(c.r) + SUITS[c.s];
  const same = (a, b) => a.r === b.r && a.s === b.s;
  const shuffle = (a) => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const deck = () => shuffle([0, 1, 2, 3].flatMap((s) => Array.from({ length: 13 }, (_, i) => ({ r: i + 1, s }))));
  const SUIT_ORDER = [0, 1, 3, 2];
  const sortHand = (h) => h.sort((a, b) => SUIT_ORDER[a.s] - SUIT_ORDER[b.s] || high(a) - high(b));
  const range = (n) => Array.from({ length: n }, (_, i) => i);
  const minBy = (a, f) => a.reduce((best, x) => (best === undefined || f(x) < f(best) ? x : best), undefined);
  const maxBy = (a, f) => a.reduce((best, x) => (best === undefined || f(x) > f(best) ? x : best), undefined);
  const sum = (a, f) => a.reduce((t, x) => t + (f ? f(x) : x), 0);
  const btn = (id, text, cards) => ({ id, label: text, cards: cards || 0 });
  const trickWinner = (trick, trump) => maxBy(trick, ([, c]) => (c.s === trump ? 200 : c.s === trick[0][1].s ? 100 : 0) + high(c))[0];
  const followPrompt = (trick) => (trick.length ? `Your turn: follow ${SUIT_NAMES[trick[0][1].s]} if you can` : "Your lead: play a card");
  const removeCards = (h, cards) => { for (const c of cards) { const i = h.findIndex((x) => same(x, c)); if (i >= 0) h.splice(i, 1); } };

  // ======================================================= Hearts
  class Hearts {
    constructor(names, target) {
      this.names = names; this.seats = 4; this.target = target || 50;
      this.hands = range(4).map(() => []); this.scores = [0, 0, 0, 0]; this.taken = [0, 0, 0, 0];
      this.handNo = 0; this.trick = []; this.message = ""; this.deal();
    }
    get passDir() { return this.handNo % 4; }
    passTo(s) { return this.passDir === 0 ? (s + 1) % 4 : this.passDir === 1 ? (s + 3) % 4 : (s + 2) % 4; }
    deal() {
      const d = deck();
      this.hands.forEach((h, i) => { h.length = 0; h.push(...d.slice(i * 13, i * 13 + 13)); sortHand(h); });
      this.taken = [0, 0, 0, 0]; this.passed = [null, null, null, null]; this.trick = []; this.broken = false; this.firstTrick = true;
      if (this.passDir === 3) this.startPlay(); else this.phase = "pass";
      this.message = this.passDir === 3 ? "No passing this hand." : "Everyone passes 3 cards to the " + ["left", "right", "player across"][this.passDir] + ".";
    }
    startPlay() { this.phase = "play"; this.leader = this.hands.findIndex((h) => h.some((c) => c.r === 2 && c.s === 0)); this.turnSeat = this.leader; }
    hand(s) { return this.hands[s]; }
    points(c) { return c.s === 2 || (c.s === 3 && c.r === 12); }
    worth(c) { return c.s === 2 ? 1 : c.s === 3 && c.r === 12 ? 13 : 0; }
    legal(s) {
      const h = this.hands[s], all = range(h.length);
      if (!this.trick.length) {
        if (this.firstTrick) return all.filter((i) => h[i].r === 2 && h[i].s === 0);
        if (!this.broken) { const n = all.filter((i) => h[i].s !== 2); if (n.length) return n; }
        return all;
      }
      const led = this.trick[0][1].s, follow = all.filter((i) => h[i].s === led);
      if (follow.length) return follow;
      if (this.firstTrick) { const clean = all.filter((i) => !this.points(h[i])); if (clean.length) return clean; }
      return all;
    }
    turn(s) {
      if (this.phase === "pass") return this.passed[s] ? null : { prompt: `Pick 3 cards to pass to ${this.names[this.passTo(s)]}`, playable: range(this.hands[s].length), buttons: [btn("pass", "Pass these 3", 3)], pickMany: true };
      if (this.phase === "play" && s === this.turnSeat) return { prompt: followPrompt(this.trick), playable: this.legal(s), buttons: [btn("play", "Play", 1)] };
      return null;
    }
    press(s, b, picked) {
      const t = this.turn(s); if (!t) return false;
      if (b === "pass" && this.phase === "pass") {
        const idx = [...new Set(picked)];
        if (idx.length !== 3 || idx.some((i) => i < 0 || i >= this.hands[s].length)) return false;
        this.passed[s] = idx.map((i) => this.hands[s][i]);
        removeCards(this.hands[s], this.passed[s]);
        if (this.passed.every(Boolean)) {
          for (let k = 0; k < 4; k++) this.hands[this.passTo(k)].push(...this.passed[k]);
          this.hands.forEach(sortHand); this.startPlay();
          this.message = `Cards passed. ${this.names[this.leader]} has the 2♣ and starts.`;
        }
        return true;
      }
      if (b === "play" && this.phase === "play") {
        if (picked.length !== 1 || !t.playable.includes(picked[0])) return false;
        const card = this.hands[s].splice(picked[0], 1)[0];
        if (card.s === 2) this.broken = true;
        this.trick.push([s, card]);
        if (this.trick.length === 4) {
          const w = trickWinner(this.trick, -1), pts = sum(this.trick, (x) => this.worth(x[1]));
          this.phase = "trickDone"; this.message = `${this.names[w]} takes the trick` + (pts > 0 ? ` (+${pts})` : "");
        } else { this.turnSeat = (s + 1) % 4; this.message = `${this.names[s]} played ${label(card)}`; }
        return true;
      }
      return false;
    }
    get waiting() { return this.phase === "trickDone" || this.phase === "handDone"; }
    advance() {
      if (this.phase === "trickDone") {
        const w = trickWinner(this.trick, -1);
        this.taken[w] += sum(this.trick, (x) => this.worth(x[1]));
        this.trick = []; this.firstTrick = false; this.leader = w; this.turnSeat = w;
        if (this.hands.every((h) => !h.length)) {
          const moon = this.taken.indexOf(26);
          if (moon >= 0) { for (let k = 0; k < 4; k++) if (k !== moon) this.scores[k] += 26; this.message = `${this.names[moon]} shot the moon! Everyone else gets 26.`; }
          else { for (let k = 0; k < 4; k++) this.scores[k] += this.taken[k]; this.message = "Hand over: " + range(4).map((k) => `${this.names[k]} +${this.taken[k]}`).join(", "); }
          this.phase = "handDone";
        } else { this.phase = "play"; this.message = `${this.names[w]} leads`; }
      } else if (this.phase === "handDone") {
        if (this.scores.some((x) => x >= this.target)) { this.phase = "over"; this.message = "Game over"; } else { this.handNo++; this.deal(); }
      }
    }
    get over() { return this.phase === "over"; }
    seatLine(s) { return `${this.scores[s]} points` + (this.phase !== "pass" && this.taken[s] > 0 ? ` · +${this.taken[s]} this hand` : ""); }
    center() { return { played: new Map(this.trick), label: this.phase === "pass" ? "Passing cards" : this.broken ? "Hearts are broken" : `First to ${this.target} loses` }; }
    results() { return range(4).sort((a, b) => this.scores[a] - this.scores[b]).map((k) => `${this.names[k]}: ${this.scores[k]} points`); }
    winners() { const low = Math.min(...this.scores); return range(4).filter((k) => this.scores[k] === low); }
    glowfin(s) {
      const t = this.turn(s); if (!t) return null;
      const h = this.hands[s];
      if (this.phase === "pass") {
        const danger = (c) => (c.s === 3 && c.r === 12 ? 100 : c.s === 3 && high(c) >= 13 ? 90 + high(c) : c.s === 2 ? 40 + high(c) : high(c));
        return ["pass", range(h.length).sort((a, b) => danger(h[b]) - danger(h[a])).slice(0, 3)];
      }
      const legal = t.playable;
      let pick;
      if (!this.trick.length) pick = minBy(legal, (i) => high(h[i]) + (h[i].s === 2 ? 20 : 0));
      else {
        const led = this.trick[0][1].s, top = Math.max(...this.trick.filter((x) => x[1].s === led).map((x) => high(x[1])));
        const trickPoints = this.trick.some((x) => this.points(x[1]));
        if (h[legal[0]].s === led) {
          const under = legal.filter((i) => high(h[i]) < top);
          if (under.length) pick = maxBy(under, (i) => high(h[i]));
          else if (this.trick.length === 3 && !trickPoints) pick = maxBy(legal.filter((i) => !(h[i].s === 3 && h[i].r === 12)), (i) => high(h[i])) ?? minBy(legal, (i) => high(h[i]));
          else pick = minBy(legal, (i) => high(h[i]));
        } else pick = maxBy(legal, (i) => { const c = h[i]; return c.s === 3 && c.r === 12 ? 1000 : c.s === 2 ? 100 + high(c) : c.s === 3 && high(c) > 12 ? 90 + high(c) : high(c); });
      }
      return ["play", [pick]];
    }
  }

  // ======================================================= Spades
  class Spades {
    constructor(names, target) {
      this.names = names; this.seats = 4; this.target = target || 300;
      this.hands = range(4).map(() => []); this.teamScore = [0, 0]; this.bags = [0, 0]; this.dealer = 3; this.message = ""; this.deal();
    }
    teamName(t) { return `${this.names[t]} and ${this.names[t + 2]}`; }
    deal() {
      const d = deck();
      this.hands.forEach((h, i) => { h.length = 0; h.push(...d.slice(i * 13, i * 13 + 13)); sortHand(h); });
      this.bids = [-1, -1, -1, -1]; this.tricks = [0, 0, 0, 0]; this.trick = []; this.broken = false;
      this.dealer = (this.dealer + 1) % 4; this.turnSeat = (this.dealer + 1) % 4; this.phase = "bid";
      this.message = `${this.names[this.turnSeat]} bids first.`;
    }
    hand(s) { return this.hands[s]; }
    legal(s) {
      const h = this.hands[s], all = range(h.length);
      if (!this.trick.length) { if (!this.broken) { const o = all.filter((i) => h[i].s !== 3); if (o.length) return o; } return all; }
      const f = all.filter((i) => h[i].s === this.trick[0][1].s);
      return f.length ? f : all;
    }
    bidName(b) { return b === 0 ? "Nil" : String(b); }
    turn(s) {
      if (s !== this.turnSeat) return null;
      if (this.phase === "bid") {
        const p = (s + 2) % 4;
        return { prompt: "How many tricks will you win?" + (this.bids[p] >= 0 ? ` (${this.names[p]} said ${this.bidName(this.bids[p])})` : ""), playable: [], buttons: [btn("bid0", "Nil"), ...range(13).map((i) => btn(`bid${i + 1}`, String(i + 1)))] };
      }
      if (this.phase === "play") return { prompt: followPrompt(this.trick), playable: this.legal(s), buttons: [btn("play", "Play", 1)] };
      return null;
    }
    press(s, b, picked) {
      const t = this.turn(s); if (!t) return false;
      if (this.phase === "bid" && b.startsWith("bid")) {
        const n = +b.slice(3); if (!(n >= 0 && n <= 13)) return false;
        this.bids[s] = n; this.message = `${this.names[s]} bids ${this.bidName(n)}`; this.turnSeat = (s + 1) % 4;
        if (this.bids.every((x) => x >= 0)) { this.phase = "play"; this.turnSeat = (this.dealer + 1) % 4; }
        return true;
      }
      if (this.phase === "play" && b === "play") {
        if (picked.length !== 1 || !t.playable.includes(picked[0])) return false;
        const card = this.hands[s].splice(picked[0], 1)[0];
        if (card.s === 3) this.broken = true;
        this.trick.push([s, card]);
        if (this.trick.length === 4) { this.phase = "trickDone"; this.message = `${this.names[trickWinner(this.trick, 3)]} takes the trick`; }
        else { this.turnSeat = (s + 1) % 4; this.message = `${this.names[s]} played ${label(card)}`; }
        return true;
      }
      return false;
    }
    get waiting() { return this.phase === "trickDone" || this.phase === "handDone"; }
    advance() {
      if (this.phase === "trickDone") {
        const w = trickWinner(this.trick, 3);
        this.tricks[w]++; this.trick = []; this.turnSeat = w;
        if (this.hands.every((h) => !h.length)) { this.scoreHand(); this.phase = "handDone"; } else { this.phase = "play"; this.message = `${this.names[w]} leads`; }
      } else if (this.phase === "handDone") {
        const top = Math.max(...this.teamScore);
        if ((top >= this.target && this.teamScore[0] !== this.teamScore[1]) || this.teamScore.some((x) => x <= -200)) { this.message = "Game over"; this.phase = "over"; } else this.deal();
      }
    }
    scoreHand() {
      const lines = [];
      for (let t = 0; t < 2; t++) {
        let gained = 0, bid = 0, won = 0;
        for (const p of [t, t + 2]) {
          if (this.bids[p] === 0) { gained += this.tricks[p] === 0 ? 100 : -100; this.bags[t] += this.tricks[p]; if (this.tricks[p] > 0) gained += this.tricks[p]; }
          else { bid += this.bids[p]; won += this.tricks[p]; }
        }
        if (bid > 0) { if (won >= bid) { gained += bid * 10 + (won - bid); this.bags[t] += won - bid; } else gained -= bid * 10; }
        if (this.bags[t] >= 10) { this.bags[t] -= 10; gained -= 100; }
        this.teamScore[t] += gained;
        lines.push(`${this.teamName(t)} ${gained >= 0 ? "+" : ""}${gained}`);
      }
      this.message = "Hand over: " + lines.join(", ");
    }
    get over() { return this.phase === "over"; }
    seatLine(s) { return (this.bids[s] >= 0 ? `bid ${this.bidName(this.bids[s])} · won ${this.tricks[s]}` : "bidding…") + ` · team ${this.teamScore[s % 2]}`; }
    center() { return { played: new Map(this.trick), label: `${this.teamName(0)}: ${this.teamScore[0]}  ·  ${this.teamName(1)}: ${this.teamScore[1]}  ·  first to ${this.target}` }; }
    results() { return [0, 1].sort((a, b) => this.teamScore[b] - this.teamScore[a]).map((t) => `${this.teamName(t)}: ${this.teamScore[t]}`); }
    winners() { const t = this.teamScore[0] >= this.teamScore[1] ? 0 : 1; return [t, t + 2]; }
    glowfin(s) {
      const t = this.turn(s); if (!t) return null;
      const h = this.hands[s];
      if (this.phase === "bid") {
        let est = 0;
        for (let k = 0; k < 4; k++) {
          const suit = h.filter((c) => c.s === k), len = suit.length;
          if (k === 3) { est += suit.filter((c) => high(c) >= 13).length; est += Math.max(0, len - 3) * 0.9; if (suit.some((c) => high(c) === 12) && len >= 3) est += 0.5; }
          else { if (suit.some((c) => high(c) === 14)) est += 1; if (suit.some((c) => high(c) === 13) && len >= 2 && len <= 5) est += 0.8; if (len <= 1 && h.filter((c) => c.s === 3).length >= 3) est += 0.6; }
        }
        return [`bid${Math.min(13, Math.max(1, Math.floor(est)))}`, []];
      }
      const legal = t.playable, me = s % 2;
      const teamBid = sum([me, me + 2], (p) => (this.bids[p] > 0 ? this.bids[p] : 0)), teamWon = sum([me, me + 2], (p) => this.tricks[p]);
      const nil = this.bids[s] === 0, want = !nil && teamWon < teamBid;
      const power = (c) => high(c) + (c.s === 3 ? 20 : 0);
      let pick;
      if (!this.trick.length) pick = want ? maxBy(legal, (i) => power(h[i])) : minBy(legal, (i) => power(h[i]));
      else {
        const w = trickWinner(this.trick, 3), winCard = this.trick.find((x) => x[0] === w)[1], led = this.trick[0][1].s;
        const beats = (c) => (c.s === winCard.s ? high(c) > high(winCard) : c.s === 3) && (c.s === led || c.s === 3);
        const winners = legal.filter((i) => beats(h[i])), partner = w === (s + 2) % 4;
        if (want && !partner && winners.length) pick = minBy(winners, (i) => power(h[i]));
        else if (nil) pick = maxBy(legal.filter((i) => !beats(h[i])), (i) => power(h[i])) ?? minBy(legal, (i) => power(h[i]));
        else pick = minBy(legal, (i) => power(h[i]));
      }
      return ["play", [pick]];
    }
  }

  // ======================================================= Crazy Eights
  class CrazyEights {
    constructor(names) {
      this.names = names; this.seats = names.length; this.hands = range(this.seats).map(() => []);
      this.deck = deck(); this.pile = []; this.drew = false; this.choosing = false; this.winner = -1;
      const each = this.seats === 2 ? 7 : 5;
      for (let k = 0; k < each; k++) for (const h of this.hands) h.push(this.deck.pop());
      this.hands.forEach(sortHand);
      let first = -1; for (let i = this.deck.length - 1; i >= 0; i--) if (this.deck[i].r !== 8) { first = i; break; }
      this.pile.push(this.deck.splice(first, 1)[0]);
      this.suit = this.top.s; this.turnSeat = Math.floor(Math.random() * this.seats);
      this.message = `${this.names[this.turnSeat]} goes first`;
    }
    get top() { return this.pile[this.pile.length - 1]; }
    hand(s) { return this.hands[s]; }
    fits(c) { return c.r === 8 || c.s === this.suit || c.r === this.top.r; }
    refill() {
      if (this.deck.length) return true;
      if (this.pile.length <= 1) return false;
      const keep = this.pile.pop(); this.deck = shuffle(this.pile); this.pile = [keep]; return true;
    }
    turn(s) {
      if (this.winner >= 0 || s !== this.turnSeat) return null;
      if (this.choosing) return { prompt: "Pick the new suit", playable: [], buttons: range(4).map((k) => btn(`suit${k}`, `${SUITS[k]} ${SUIT_NAMES[k][0].toUpperCase()}${SUIT_NAMES[k].slice(1)}`)) };
      const h = this.hands[s], ok = range(h.length).filter((i) => this.fits(h[i]));
      const canDraw = !this.drew && (this.deck.length > 0 || this.pile.length > 1);
      return {
        prompt: !ok.length ? (canDraw ? "Nothing fits. Draw a card." : "Nothing fits. Pass.") : `Match ${SUITS[this.suit]} or ${rankName(this.top.r)}, or play an 8`,
        playable: ok,
        buttons: [btn("play", "Play", 1), ...(canDraw ? [btn("draw", "Draw")] : [btn("pass", "Pass")])],
      };
    }
    press(s, b, picked) {
      const t = this.turn(s); if (!t || !t.buttons.some((x) => x.id === b)) return false;
      if (b === "play") {
        if (picked.length !== 1 || !t.playable.includes(picked[0])) return false;
        const c = this.hands[s].splice(picked[0], 1)[0];
        this.pile.push(c); this.suit = c.s; this.message = `${this.names[s]} played ${label(c)}`;
        if (!this.hands[s].length) { this.winner = s; this.message = `${this.names[s]} is out of cards!`; return true; }
        if (c.r === 8) { this.choosing = true; return true; }
        this.next();
      } else if (b === "draw") {
        if (!this.refill()) return false;
        this.hands[s].push(this.deck.pop()); sortHand(this.hands[s]); this.drew = true; this.message = `${this.names[s]} drew a card`;
      } else if (b === "pass") { this.message = `${this.names[s]} passed`; this.next(); }
      else { this.suit = +b.slice(4); this.choosing = false; this.message = `${this.names[s]} changed the suit to ${SUITS[this.suit]}`; this.next(); }
      return true;
    }
    next() { this.drew = false; this.turnSeat = (this.turnSeat + 1) % this.seats; }
    get waiting() { return false; }
    advance() {}
    get over() { return this.winner >= 0; }
    seatLine(s) { return `${this.hands[s].length} cards`; }
    center() { return { shared: [this.top], deck: this.deck.length, label: this.suit !== this.top.s ? `The suit is now ${SUITS[this.suit]}` : "" }; }
    results() { return range(this.seats).sort((a, b) => this.hands[a].length - this.hands[b].length).map((k) => `${this.names[k]}: ${this.hands[k].length} cards left`); }
    winners() { return [this.winner]; }
    glowfin(s) {
      const t = this.turn(s); if (!t) return null;
      const h = this.hands[s];
      if (this.choosing) return [`suit${maxBy(range(4), (k) => h.filter((c) => c.s === k).length)}`, []];
      if (t.playable.length) {
        const plain = t.playable.filter((i) => h[i].r !== 8);
        const pick = maxBy(plain, (i) => h.filter((c) => c.s === h[i].s).length * 20 + h[i].r) ?? t.playable[0];
        return ["play", [pick]];
      }
      return [t.buttons.some((x) => x.id === "draw") ? "draw" : "pass", []];
    }
  }

  // ======================================================= Rummy
  const isMeld = (cards) => {
    if (cards.length < 3 || new Set(cards.map((c) => c.r * 4 + c.s)).size !== cards.length) return false;
    if (cards.every((c) => c.r === cards[0].r)) return cards.length <= 4;
    if (cards.some((c) => c.s !== cards[0].s)) return false;
    const r = cards.map((c) => c.r).sort((a, b) => a - b);
    return r.every((x, i) => i === 0 || x === r[i - 1] + 1);
  };
  const findMeld = (cards) => {
    let best = null;
    for (let s = 0; s < 4; s++) {
      const ranks = [...new Set(cards.filter((c) => c.s === s).map((c) => c.r))].sort((a, b) => a - b);
      let i = 0;
      while (i < ranks.length) {
        let j = i;
        while (j + 1 < ranks.length && ranks[j + 1] === ranks[j] + 1) j++;
        if (j - i + 1 >= 3 && (!best || j - i + 1 > best.length)) best = ranks.slice(i, j + 1).map((r) => cards.find((c) => c.s === s && c.r === r));
        i = j + 1;
      }
    }
    if (best) return best;
    const groups = {};
    for (const c of cards) (groups[c.r] = groups[c.r] || []).push(c);
    return Object.values(groups).find((g) => g.length >= 3) || null;
  };
  class Rummy {
    constructor(names) {
      this.names = names; this.seats = names.length; this.hands = range(this.seats).map(() => []);
      this.deck = deck(); this.pile = []; this.melds = []; this.laid = new Array(this.seats).fill(0);
      this.drawn = false; this.tookUp = null; this.winner = -1; this.reshuffles = 0;
      const each = this.seats === 2 ? 10 : 7;
      for (let k = 0; k < each; k++) for (const h of this.hands) h.push(this.deck.pop());
      this.hands.forEach((h) => this.sortRun(h));
      this.pile.push(this.deck.pop());
      this.turnSeat = Math.floor(Math.random() * this.seats);
      this.message = `${this.names[this.turnSeat]} goes first`;
    }
    sortRun(h) { h.sort((a, b) => SUIT_ORDER[a.s] - SUIT_ORDER[b.s] || a.r - b.r); }
    value(c) { return c.r >= 10 ? 10 : c.r; }
    hand(s) { return this.hands[s]; }
    turn(s) {
      if (this.winner >= 0 || s !== this.turnSeat) return null;
      const h = this.hands[s], top = this.pile[this.pile.length - 1];
      if (!this.drawn) return { prompt: "Take a card from the deck or the discard pile", playable: [], buttons: [btn("deck", "Take from the deck"), ...(top ? [btn("pile", `Take the ${label(top)}`)] : [])] };
      return { prompt: "Lay down sets or runs, add to the table, then discard one", playable: range(h.length), buttons: [btn("lay", "Lay down", -1), btn("add", "Add to the table", 1), btn("discard", "Discard", 1)], pickMany: true };
    }
    press(s, b, picked) {
      if (!this.turn(s)) return false;
      const h = this.hands[s];
      if (b === "deck") {
        if (this.drawn) return false;
        if (!this.deck.length) {
          if (this.pile.length <= 1 || ++this.reshuffles > 3) {
            this.winner = minBy(range(this.seats), (k) => sum(this.hands[k], (c) => this.value(c)));
            this.message = `The deck ran out. ${this.names[this.winner]} has the fewest points left and wins!`;
            return true;
          }
          const keep = this.pile.pop(); this.deck = this.pile.reverse(); this.pile = [keep];
        }
        h.push(this.deck.pop()); this.sortRun(h); this.drawn = true; this.tookUp = null; this.message = `${this.names[s]} took from the deck`;
      } else if (b === "pile") {
        if (this.drawn || !this.pile.length) return false;
        const c = this.pile.pop(); h.push(c); this.sortRun(h); this.drawn = true; this.tookUp = c; this.message = `${this.names[s]} took the ${label(c)}`;
      } else if (b === "lay") {
        if (!this.drawn) return false;
        const cards = [...new Set(picked)].map((i) => h[i]).filter(Boolean);
        if (cards.length < 3 || !isMeld(cards)) return false;
        removeCards(h, cards);
        const m = cards.slice().sort((a, b2) => a.r - b2.r);
        this.melds.push(m); this.laid[s] += cards.length;
        this.message = `${this.names[s]} laid down ${m.map(label).join(" ")}`;
        this.checkOut(s);
      } else if (b === "add") {
        if (!this.drawn || picked.length !== 1) return false;
        const c = h[picked[0]]; if (!c) return false;
        const m = this.melds.find((x) => isMeld([...x, c])); if (!m) return false;
        removeCards(h, [c]); m.push(c); m.sort((a, b2) => a.r - b2.r); this.laid[s]++;
        this.message = `${this.names[s]} added the ${label(c)}`;
        this.checkOut(s);
      } else if (b === "discard") {
        if (!this.drawn || picked.length !== 1) return false;
        const c = h[picked[0]]; if (!c) return false;
        if (this.tookUp && same(c, this.tookUp) && h.length > 1) return false;
        removeCards(h, [c]); this.pile.push(c); this.message = `${this.names[s]} discarded the ${label(c)}`;
        if (!this.checkOut(s)) { this.drawn = false; this.tookUp = null; this.turnSeat = (this.turnSeat + 1) % this.seats; }
      } else return false;
      return true;
    }
    checkOut(s) { if (this.hands[s].length) return false; this.winner = s; this.message = `${this.names[s]} is out and scores ${this.points(s)}!`; return true; }
    points(s) { return sum(range(this.seats).filter((k) => k !== s), (k) => sum(this.hands[k], (c) => this.value(c))); }
    get waiting() { return false; }
    advance() {}
    get over() { return this.winner >= 0; }
    seatLine(s) { return `${this.hands[s].length} cards` + (this.laid[s] > 0 ? ` · ${this.laid[s]} laid down` : ""); }
    center() { return { shared: this.pile.length ? [this.pile[this.pile.length - 1]] : [], deck: this.deck.length, melds: this.melds }; }
    results() { return [`${this.names[this.winner]} wins ${this.points(this.winner)} points`, ...range(this.seats).filter((k) => k !== this.winner).map((k) => `${this.names[k]}: ${this.hands[k].length} cards left (${sum(this.hands[k], (c) => this.value(c))})`)]; }
    winners() { return [this.winner]; }
    glowfin(s) {
      if (!this.turn(s)) return null;
      const h = this.hands[s];
      if (!this.drawn) {
        const top = this.pile[this.pile.length - 1];
        const m = top && findMeld([...h, top]);
        const useful = top && ((m && m.includes(top)) || this.melds.some((x) => isMeld([...x, top])));
        return [useful ? "pile" : "deck", []];
      }
      const m = findMeld(h);
      if (m) return ["lay", m.map((c) => h.indexOf(c))];
      if (h.length > 1) for (let i = 0; i < h.length; i++) if (this.melds.some((x) => isMeld([...x, h[i]]))) return ["add", [i]];
      const close = (c) => h.some((x) => x !== c && (x.r === c.r || (x.s === c.s && Math.abs(x.r - c.r) <= 2)));
      const choices = range(h.length).filter((i) => !(this.tookUp && same(h[i], this.tookUp)) || h.length === 1);
      return ["discard", [maxBy(choices, (i) => this.value(h[i]) + (close(h[i]) ? -20 : 0) + Math.random())]];
    }
  }

  // ======================================================= Go Fish
  const rankWord = (r) => ({ 1: "aces", 11: "jacks", 12: "queens", 13: "kings", 6: "sixes" })[r] || `${r}s`;
  class GoFish {
    constructor(names) {
      this.names = names; this.seats = names.length; this.hands = range(this.seats).map(() => []);
      this.deck = deck(); this.books = range(this.seats).map(() => []); this.asked = range(this.seats).map(() => new Set());
      const each = this.seats <= 3 ? 7 : 5;
      for (let k = 0; k < each; k++) for (const h of this.hands) h.push(this.deck.pop());
      for (let s = 0; s < this.seats; s++) this.makeBooks(s);
      this.hands.forEach((h) => this.sortRank(h));
      this.turnSeat = Math.floor(Math.random() * this.seats);
      this.refill(this.turnSeat);
      this.message = `${this.names[this.turnSeat]} asks first`;
    }
    sortRank(h) { h.sort((a, b) => a.r - b.r || a.s - b.s); }
    makeBooks(s) {
      const count = {};
      for (const c of this.hands[s]) count[c.r] = (count[c.r] || 0) + 1;
      const made = Object.keys(count).filter((r) => count[r] === 4).map(Number);
      for (const r of made) { this.hands[s] = this.hands[s].filter((c) => c.r !== r); this.books[s].push(r); this.asked.forEach((a) => a.delete(r)); }
      return made;
    }
    refill(s) { if (!this.hands[s].length && this.deck.length) this.hands[s].push(this.deck.pop()); }
    hand(s) { return this.hands[s]; }
    others(s) { return range(this.seats).filter((k) => k !== s && this.hands[k].length); }
    turn(s) {
      if (this.over || s !== this.turnSeat) return null;
      const targets = this.others(s);
      if (!this.hands[s].length || !targets.length) return { prompt: "Nothing to ask for. Pass.", playable: [], buttons: [btn("pass", "Pass")] };
      return { prompt: "Pick a card, then who to ask", playable: range(this.hands[s].length), buttons: targets.map((k) => btn(`ask${k}`, `Ask ${this.names[k]}`, 1)) };
    }
    press(s, b, picked) {
      const t = this.turn(s); if (!t) return false;
      if (b === "pass" && t.buttons.some((x) => x.id === "pass")) { this.message = `${this.names[s]} has nothing to ask for`; this.nextTurn(); return true; }
      if (!b.startsWith("ask")) return false;
      const who = +b.slice(3);
      if (!this.others(s).includes(who) || picked.length !== 1 || !this.hands[s][picked[0]]) return false;
      const rank = this.hands[s][picked[0]].r;
      this.asked[s].add(rank);
      const got = this.hands[who].filter((c) => c.r === rank);
      if (got.length) {
        this.hands[who] = this.hands[who].filter((c) => c.r !== rank); this.asked[who].delete(rank);
        this.hands[s].push(...got);
        this.message = `${this.names[who]} gave ${this.names[s]} ${got.length} ${got.length === 1 ? rankWord(rank).slice(0, -1) : rankWord(rank)}`;
        this.afterGain(s); this.refill(who);
        if (!this.over) { this.refill(s); if (!this.hands[s].length) this.nextTurn(); }
        return true;
      }
      if (!this.deck.length) { this.message = `${this.names[who]}: "Go fish!" The pond is empty.`; this.nextTurn(); return true; }
      const drawn = this.deck.pop();
      this.hands[s].push(drawn);
      if (drawn.r === rank) {
        this.message = `Go fish! ${this.names[s]} caught the one they wanted and goes again`;
        this.afterGain(s);
        if (!this.over) { this.refill(s); if (!this.hands[s].length) this.nextTurn(); }
      } else { this.message = `${this.names[who]}: "Go fish!"`; this.afterGain(s); this.nextTurn(); }
      return true;
    }
    afterGain(s) { const made = this.makeBooks(s); this.sortRank(this.hands[s]); if (made.length) this.message += `. ${this.names[s]} made a book of ${made.map(rankWord).join(" and ")}!`; }
    nextTurn() {
      if (this.over) return;
      for (let k = 0; k < this.seats; k++) { this.turnSeat = (this.turnSeat + 1) % this.seats; this.refill(this.turnSeat); if (this.hands[this.turnSeat].length) return; }
    }
    get waiting() { return false; }
    advance() {}
    get over() { return sum(this.books, (b) => b.length) === 13; }
    seatLine(s) { return `${this.hands[s].length} cards · ${this.books[s].length} books`; }
    center() { return { deck: this.deck.length, label: "The pond" }; }
    results() { return range(this.seats).sort((a, b) => this.books[b].length - this.books[a].length).map((k) => `${this.names[k]}: ${this.books[k].length} books`); }
    winners() { const top = Math.max(...this.books.map((b) => b.length)); return range(this.seats).filter((k) => this.books[k].length === top); }
    glowfin(s) {
      const t = this.turn(s); if (!t) return null;
      if (t.buttons.some((x) => x.id === "pass")) return ["pass", []];
      const h = this.hands[s], targets = this.others(s);
      for (let i = 0; i < h.length; i++) { const holder = targets.find((k) => this.asked[k].has(h[i].r)); if (holder !== undefined) return [`ask${holder}`, [i]]; }
      const count = {};
      for (const c of h) count[c.r] = (count[c.r] || 0) + 1;
      const rank = +maxBy(Object.keys(count), (r) => count[r] + Math.random());
      return [`ask${targets[Math.floor(Math.random() * targets.length)]}`, [h.findIndex((c) => c.r === rank)]];
    }
  }

  // ======================================================= Hold'em (pretend chips only)
  const five = (c) => {
    const r = c.map(high).sort((a, b) => b - a), flush = c.every((x) => x.s === c[0].s), distinct = [...new Set(r)];
    let straightTop = 0;
    if (distinct.length === 5) { if (r[0] - r[4] === 4) straightTop = r[0]; else if (r.join() === "14,5,4,3,2") straightTop = 5; }
    const g = {};
    for (const x of r) g[x] = (g[x] || 0) + 1;
    const groups = Object.keys(g).map(Number).sort((a, b) => g[b] - g[a] || b - a);
    const counts = groups.map((k) => g[k]), order = groups.flatMap((k) => new Array(g[k]).fill(k));
    const cat = straightTop && flush ? 8 : counts[0] === 4 ? 7 : counts[0] === 3 && counts[1] === 2 ? 6 : flush ? 5 : straightTop ? 4 : counts[0] === 3 ? 3 : counts[0] === 2 && counts[1] === 2 ? 2 : counts[0] === 2 ? 1 : 0;
    const kick = straightTop && (cat === 8 || cat === 4) ? [straightTop, 0, 0, 0, 0] : order;
    return kick.reduce((acc, v) => acc * 16 + v, cat);
  };
  const best = (cards) => {
    const n = cards.length;
    if (n <= 5) return five(cards);
    let top = 0;
    if (n === 6) { for (let i = 0; i < 6; i++) top = Math.max(top, five(cards.filter((_, j) => j !== i))); return top; }
    for (let a = 0; a < n; a++) for (let b = a + 1; b < n; b++) top = Math.max(top, five(cards.filter((_, i) => i !== a && i !== b)));
    return top;
  };
  const handName = (score) => ["a high card", "a pair", "two pair", "three of a kind", "a straight", "a flush", "a full house", "four of a kind", "a straight flush"][Math.floor(score / 16 ** 5)];
  class Holdem {
    constructor(names, start) {
      this.names = names; this.seats = names.length; const n = this.seats;
      this.chips = new Array(n).fill(start || 1000); this.holes = range(n).map(() => []); this.board = []; this.deck = [];
      this.inHand = new Array(n).fill(false); this.folded = new Array(n).fill(false); this.bet = new Array(n).fill(0); this.put = new Array(n).fill(0); this.needs = new Array(n).fill(false);
      this.dealer = -1; this.toAct = 0; this.currentBet = 0; this.minRaise = 0; this.handNo = 0; this.shown = new Map(); this.phase = "bet"; this.message = "";
      this.startHand();
    }
    get bigBlind() { return 20 << Math.min(4, Math.floor(this.handNo / 10)); }
    active() { return range(this.seats).filter((s) => this.inHand[s] && !this.folded[s]); }
    nextFrom(from, test) { for (let k = 1; k <= this.seats; k++) { const s = (from + k) % this.seats; if (test(s)) return s; } return from; }
    startHand() {
      const alive = range(this.seats).filter((s) => this.chips[s] > 0);
      if (alive.length <= 1) { this.phase = "over"; this.message = `${this.names[alive[0] ?? 0]} has every chip!`; return; }
      this.deck = deck(); this.board = []; this.shown = new Map();
      for (let s = 0; s < this.seats; s++) { this.holes[s] = []; this.inHand[s] = this.chips[s] > 0; this.folded[s] = false; this.bet[s] = 0; this.put[s] = 0; }
      this.dealer = this.nextFrom(this.dealer < 0 ? this.seats - 1 : this.dealer, (s) => this.inHand[s]);
      for (let k = 0; k < 2; k++) for (let s = 0; s < this.seats; s++) if (this.inHand[s]) this.holes[s].push(this.deck.pop());
      this.holes.forEach((h) => h.sort((a, b) => high(b) - high(a)));
      const sb = alive.length === 2 ? this.dealer : this.nextFrom(this.dealer, (s) => this.inHand[s]);
      const bb = this.nextFrom(sb, (s) => this.inHand[s]);
      this.pay(sb, this.bigBlind / 2); this.pay(bb, this.bigBlind);
      this.currentBet = this.bigBlind; this.minRaise = this.bigBlind;
      for (let s = 0; s < this.seats; s++) this.needs[s] = this.inHand[s] && this.chips[s] > 0;
      this.toAct = this.nextFrom(bb, (s) => this.needs[s]);
      this.phase = "bet"; this.message = `New hand. Blinds ${this.bigBlind / 2} and ${this.bigBlind}.`;
      this.handNo++;
      this.settleIfNoBetting();
    }
    pay(s, amount) { const a = Math.min(amount, this.chips[s]); this.chips[s] -= a; this.bet[s] += a; this.put[s] += a; }
    get pot() { return sum(this.put); }
    hand(s) { return this.holes[s]; }
    turn(s) {
      if (this.phase !== "bet" || s !== this.toAct || !this.needs[s]) return null;
      const owe = this.currentBet - this.bet[s], buttons = [btn("fold", "Fold")];
      buttons.push(owe <= 0 ? btn("check", "Check") : btn("call", `Call ${Math.min(owe, this.chips[s])}`));
      const most = this.bet[s] + this.chips[s], minTo = this.currentBet + this.minRaise;
      if (most > minTo) {
        buttons.push(btn("raise", `Raise to ${minTo}`));
        const potTo = this.currentBet + this.pot + Math.max(owe, 0);
        if (potTo > minTo && potTo < most) buttons.push(btn("raisepot", `Raise to ${potTo}`));
      }
      if (this.chips[s] > Math.max(owe, 0)) buttons.push(btn("allin", `All in (${most})`));
      return { prompt: owe > 0 ? `${owe} to call. Pot ${this.pot}.` : `Your move. Pot ${this.pot}.`, playable: [], buttons };
    }
    press(s, b) {
      const t = this.turn(s); if (!t || !t.buttons.some((x) => x.id === b)) return false;
      const owe = this.currentBet - this.bet[s];
      if (b === "fold") { this.folded[s] = true; this.message = `${this.names[s]} folds`; }
      else if (b === "check") this.message = `${this.names[s]} checks`;
      else if (b === "call") { this.pay(s, owe); this.message = `${this.names[s]} calls` + (this.chips[s] === 0 ? " and is all in" : ""); }
      else {
        const to = b === "raise" ? this.currentBet + this.minRaise : b === "raisepot" ? this.currentBet + this.pot + Math.max(owe, 0) : this.bet[s] + this.chips[s];
        this.pay(s, to - this.bet[s]);
        if (this.bet[s] > this.currentBet) {
          this.minRaise = Math.max(this.minRaise, this.bet[s] - this.currentBet); this.currentBet = this.bet[s];
          for (let k = 0; k < this.seats; k++) if (k !== s && this.inHand[k] && !this.folded[k] && this.chips[k] > 0) this.needs[k] = true;
        }
        this.message = this.chips[s] === 0 ? `${this.names[s]} is all in with ${this.bet[s]}` : `${this.names[s]} raises to ${this.bet[s]}`;
      }
      this.needs[s] = false;
      this.afterAction(s);
      return true;
    }
    afterAction(s) {
      const left = this.active();
      if (left.length === 1) { const w = left[0], won = this.pot; this.chips[w] += won; this.put.fill(0); this.message = `${this.names[w]} wins ${won}`; this.phase = "handDone"; return; }
      if (this.needs.some(Boolean)) { this.toAct = this.nextFrom(s, (k) => this.needs[k]); return; }
      this.nextStreet();
    }
    nextStreet() {
      this.bet.fill(0); this.currentBet = 0; this.minRaise = this.bigBlind;
      if (this.board.length === 0) for (let k = 0; k < 3; k++) this.board.push(this.deck.pop());
      else if (this.board.length < 5) this.board.push(this.deck.pop());
      else return this.showdown();
      for (let s = 0; s < this.seats; s++) this.needs[s] = this.inHand[s] && !this.folded[s] && this.chips[s] > 0;
      this.toAct = this.nextFrom(this.dealer, (s) => this.needs[s]);
      this.settleIfNoBetting();
    }
    settleIfNoBetting() {
      const canBet = this.needs.filter(Boolean).length, owed = range(this.seats).some((s) => this.needs[s] && this.bet[s] < this.currentBet);
      if (canBet === 0 || (canBet === 1 && !owed)) { this.needs.fill(false); this.nextStreet(); }
    }
    showdown() {
      const left = this.active();
      this.shown = new Map(left.map((s) => [s, this.holes[s].slice()]));
      const score = new Map(left.map((s) => [s, best([...this.holes[s], ...this.board])]));
      const levels = [...new Set(this.put.filter((x) => x > 0))].sort((a, b) => a - b);
      let prev = 0; const won = new Array(this.seats).fill(0);
      for (const level of levels) {
        const amount = sum(range(this.seats), (k) => Math.min(this.put[k], level) - Math.min(this.put[k], prev));
        const eligible = left.filter((k) => this.put[k] >= level);
        if (eligible.length) {
          const top = Math.max(...eligible.map((k) => score.get(k))), w = eligible.filter((k) => score.get(k) === top);
          w.forEach((k) => { won[k] += Math.floor(amount / w.length); });
          won[w[0]] += amount % w.length;
        } else won[maxBy(left, (k) => this.put[k])] += amount;
        prev = level;
      }
      for (let s = 0; s < this.seats; s++) this.chips[s] += won[s];
      this.put.fill(0);
      this.message = range(this.seats).filter((s) => won[s] > 0).map((s) => `${this.names[s]} wins ${won[s]} with ${handName(score.get(s))}`).join(", ");
      this.phase = "showdown";
    }
    get waiting() { return this.phase === "showdown" || this.phase === "handDone"; }
    advance() { if (this.waiting) this.startHand(); }
    get over() { return this.phase === "over"; }
    seatLine(s) {
      const base = !this.inHand[s] && this.chips[s] === 0 ? "out of chips" : this.folded[s] ? `${this.chips[s]} chips · folded` : this.inHand[s] && this.chips[s] === 0 ? `all in (${this.put[s]})` : `${this.chips[s]} chips` + (this.bet[s] > 0 ? ` · bet ${this.bet[s]}` : "");
      return base + (s === this.dealer ? " · dealer" : "");
    }
    center() { return { shared: this.board, label: `Pot ${this.pot}  ·  blinds ${this.bigBlind / 2}/${this.bigBlind}`, reveal: this.shown }; }
    results() { return range(this.seats).sort((a, b) => this.chips[b] - this.chips[a]).map((s) => `${this.names[s]}: ${this.chips[s]} chips`); }
    winners() { const top = Math.max(...this.chips); return range(this.seats).filter((s) => this.chips[s] === top); }
    equity(s, opponents, tries) {
      if (opponents <= 0) return 1;
      const known = [...this.holes[s], ...this.board];
      const rest = [0, 1, 2, 3].flatMap((su) => range(13).map((i) => ({ r: i + 1, s: su }))).filter((c) => !known.some((k) => same(k, c)));
      let win = 0;
      for (let t = 0; t < tries; t++) {
        const d = shuffle(rest.slice());
        let k = 5 - this.board.length;
        const full = [...this.board, ...d.slice(0, k)], mine = best([...this.holes[s], ...full]);
        let beaten = false, ties = 0;
        for (let o = 0; o < opponents; o++) { const theirs = best([d[k], d[k + 1], ...full]); k += 2; if (theirs > mine) beaten = true; else if (theirs === mine) ties++; }
        if (!beaten) win += 1 / (ties + 1);
      }
      return win / tries;
    }
    glowfin(s) {
      const t = this.turn(s); if (!t) return null;
      const ids = t.buttons.map((x) => x.id), p = this.equity(s, this.active().length - 1, 120);
      const owe = this.currentBet - this.bet[s], potOdds = owe <= 0 ? 0 : owe / (this.pot + owe), mood = Math.random() * 0.08;
      const choice = p + mood > 0.82 && ids.includes("raisepot") ? "raisepot"
        : p + mood > 0.82 && ids.includes("allin") && this.chips[s] < this.bigBlind * 8 ? "allin"
          : p + mood > 0.68 && ids.includes("raise") ? "raise"
            : owe <= 0 ? "check"
              : p + mood >= potOdds + 0.05 ? (ids.includes("call") ? "call" : "allin") : "fold";
      return [choice, []];
    }
  }

  const GAMES = {
    HEARTS: { make: (n) => new Hearts(n), min: 4, max: 4 },
    SPADES: { make: (n) => new Spades(n), min: 4, max: 4 },
    CRAZY_EIGHTS: { make: (n) => new CrazyEights(n), min: 2, max: 6, start: 4 },
    RUMMY: { make: (n) => new Rummy(n), min: 2, max: 4, start: 3 },
    GO_FISH: { make: (n) => new GoFish(n), min: 2, max: 5, start: 4 },
    HOLDEM: { make: (n) => new Holdem(n), min: 2, max: 8, start: 6, grownUps: true },
  };

  // ======================================================= the table on the page
  const css = `
  .ct{width:100%;height:100%;display:flex;flex-direction:column;gap:8px;padding:6px 12px 12px;box-sizing:border-box;user-select:none}
  .ct-felt{position:relative;flex:1;min-height:240px;background:#0F4A56;border:6px solid #0B3640;border-radius:min(120px,18vw)}
  .ct-seat{position:absolute;transform:translate(-50%,-50%);width:min(170px,30vw);padding:4px 6px;border-radius:10px;background:rgba(0,0,0,.35);border:2px solid transparent;box-sizing:border-box;text-align:center}
  .ct-seat.on{background:rgba(255,255,255,.2);border-color:#3FE0E8}
  .ct-seat b{display:block;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.ct-seat small{display:block;font-size:12px;color:var(--ink2);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .ct-seat .rv{display:flex;gap:3px;justify-content:center;margin-top:4px}.ct-seat .rv .tb-card{width:30px}
  .ct-mid{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:56%;display:flex;flex-direction:column;align-items:center;gap:6px;text-align:center}
  .ct-mid .msg{font-weight:600;font-size:14px}.ct-mid .lbl{color:#FFC94D;font-size:13px}
  .ct-row{display:flex;gap:6px;align-items:center;justify-content:center;flex-wrap:wrap}
  .ct-mid .tb-card,.ct-trick .tb-card{width:min(52px,9vw)}
  .ct-deck{display:flex;flex-direction:column;align-items:center;font-size:12px;color:var(--ink2)}
  .ct-meld{position:relative;height:calc(min(30px,6vw) * 1.4);flex:none}.ct-meld .tb-card{position:absolute;top:0;width:min(30px,6vw)}
  .ct-trick{position:absolute;transform:translate(-50%,-50%)}
  .ct-hand{background:rgba(0,0,0,.4);border-radius:14px;padding:8px 12px}
  .ct-hand .say{font-weight:600;margin-bottom:6px}.ct-hand .say.dim{font-weight:400;color:var(--ink2)}
  .ct-cards{display:flex;flex-wrap:wrap;gap:4px;padding-top:8px}
  .ct-cards .tb-card{width:min(58px,11vw);transition:transform .12s}.ct-cards .tb-card.up{transform:translateY(-8px)}.ct-cards .tb-card.no{opacity:.42}
  .ct-btns{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.ct-btns .btn.ready{background:#fff;color:#000}.ct-btns .btn.cur{box-shadow:0 0 0 3px #3FE0E8}
  .ct-cards .tb-card.cur{box-shadow:0 0 0 4px #3FE0E8}
  `;
  let styled = false;
  const style = () => { if (styled) return; styled = true; const s = document.createElement("style"); s.textContent = css; document.head.appendChild(s); };
  const cardHtml = (c, cls, data) => (!c ? `<div class="tb-card back ${cls || ""}" ${data || ""}></div>` : `<div class="tb-card${c.s === 1 || c.s === 2 ? " red" : ""} ${cls || ""}" ${data || ""}><span class="r">${rankName(c.r)}${SUITS[c.s]}&#xFE0E;</span><span class="s">${SUITS[c.s]}&#xFE0E;</span></div>`);

  const play = (ctx) => {
    style();
    const g = ctx.game, stage = ctx.stage, rule = GAMES[g.game];
    const me = (B.me && B.me.name ? String(B.me.name).trim().split(/\s+/)[0] : "") || "You";
    if (rule.grownUps && B.me && B.me.kids) { stage.innerHTML = `<div class="tb-setup"><h2>${esc(g.title)}</h2><p>This one isn't on Kids profiles.</p><button class="btn" id="ctBack">Back</button></div>`; $("#ctBack").onclick = ctx.quit; return; }
    stage.style.alignItems = "stretch";
    let keyHandler = null;
    const onKey = (e) => { if (!ctx.alive()) { document.removeEventListener("keydown", onKey, true); return; } if (keyHandler && keyHandler(e)) { e.preventDefault(); e.stopPropagation(); } };
    document.addEventListener("keydown", onKey, true);
    if (B.onLeave) B.onLeave(() => document.removeEventListener("keydown", onKey, true));
    const token = {};
    stage.tbToken = token;
    const later = (fn, ms) => setTimeout(() => { if (ctx.alive() && stage.isConnected && stage.tbToken === token) fn(); }, ms);

    const overlay = (title, lines, buttons) => {
      const box = document.createElement("div");
      box.className = "bg-over";
      box.innerHTML = `<div class="bg-card"><h2>${esc(title)}</h2>${lines.map((l) => `<p>${esc(l)}</p>`).join("")}<div class="acts">${buttons.map((b, i) => `<button class="btn${i === 0 ? " white" : ""}" data-i="${i}">${esc(b[0])}</button>`).join("")}</div></div>`;
      stage.appendChild(box);
      const saved = keyHandler;
      const close = () => { box.remove(); keyHandler = saved; };
      keyHandler = (e) => { if (e.key === "Escape") { close(); return true; } return false; };
      box.querySelectorAll("button").forEach((b) => { b.onclick = () => { close(); buttons[+b.dataset.i][1](); }; });
      setTimeout(() => { const b = box.querySelector("button"); if (b) b.focus(); }, 50);
    };

    // ---------- seats ----------
    let seatCount = rule.start || rule.max;
    const lobby = () => {
      keyHandler = null;
      const names = [me, ...range(seatCount - 1).map((i) => (seatCount === 2 ? "Glowfin" : `Glowfin ${i + 1}`))];
      stage.innerHTML = `<div class="tb-setup"><h2>${esc(g.title)}</h2><p class="dim">You sit at the bottom. Glowfin plays the other seats.${g.game === "SPADES" ? " Your partner sits across from you." : ""}</p>
        ${rule.min < rule.max ? `<div class="tb-step"><span>Seats</span><button class="btn small" data-a="-">−</button><b>${seatCount}</b><button class="btn small" data-a="+">+</button></div>` : ""}
        <p>${names.map(esc).join(" · ")}</p>
        <p class="dim">On the TV app, friends can join with their phones and hold their own cards.</p>
        <button class="btn white" data-a="go">Start</button><button class="btn" data-a="back">Back</button></div>`;
      stage.querySelectorAll("[data-a]").forEach((b) => {
        b.onclick = () => {
          const a = b.dataset.a;
          if (a === "-") seatCount = Math.max(rule.min, seatCount - 1);
          else if (a === "+") seatCount = Math.min(rule.max, seatCount + 1);
          else if (a === "go") return start(names);
          else return ctx.quit();
          lobby();
          const again = stage.querySelector(`[data-a="${a}"]`); if (again) again.focus();
        };
      });
      const first = stage.querySelector('[data-a="go"]'); if (first) first.focus();
    };

    // ---------- the table ----------
    const start = (names) => {
      const game = rule.make(names), n = names.length;
      let picked = [], row = 0, spot = 0, busy = false, done = false;
      const spotAt = (i, k) => { const a = Math.PI / 2 + (i * 2 * Math.PI) / n; return [50 + 41 * k * Math.cos(a), 50 + 40 * k * Math.sin(a)]; };
      const press = (id) => { if (game.press(0, id, picked.slice().sort((a, b) => a - b))) { picked = []; row = 0; spot = 0; draw(); } };
      const tap = (i) => {
        const t = game.turn(0);
        if (!t || !t.playable.includes(i)) return;
        picked = picked.includes(i) ? picked.filter((x) => x !== i) : t.pickMany ? [...picked, i] : [i];
        row = 0; spot = i;
        draw();
      };
      const openMenu = () => overlay(g.title, [], [["Keep playing", () => {}], ["New game", () => { stage.tbToken = {}; ctx.again(); }], ["Leave", ctx.quit]]);
      const draw = () => {
        const t = game.turn(0), hand = game.hand(0), c = game.center(), turnSeat = range(n).find((s) => game.turn(s));
        const buttons = t ? t.buttons : [];
        if (row === 1 && !buttons.length) row = 0;
        if (t && !t.playable.length && buttons.length) row = 1;
        spot = Math.max(0, Math.min(spot, (row === 0 ? hand.length : buttons.length) - 1));
        const seatsHtml = names.map((nm, i) => {
          const [x0, y] = spotAt(i, 1), x = Math.min(84, Math.max(16, x0)), rv = c.reveal && c.reveal.get(i);
          return `<div class="ct-seat${i === turnSeat ? " on" : ""}" style="left:${x}%;top:${y}%"><b>${esc(nm)}${i ? " ✦" : ""}</b><small>${esc(game.seatLine(i))}</small>${rv && i ? `<div class="rv">${rv.map((k) => cardHtml(k)).join("")}</div>` : ""}</div>`;
        }).join("");
        const trick = c.played ? [...c.played.entries()].map(([s, card]) => { const [x, y] = spotAt(s, 0.5); return `<div class="ct-trick" style="left:${x}%;top:${y}%">${cardHtml(card)}</div>`; }).join("") : "";
        const mid = `<div class="ct-mid">${game.message ? `<div class="msg">${esc(game.message)}</div>` : ""}
          ${(c.shared && c.shared.length) || c.deck >= 0 ? `<div class="ct-row">${c.deck >= 0 ? `<div class="ct-deck">${cardHtml(null)}<span>${c.deck} left</span></div>` : ""}${(c.shared || []).map((k) => cardHtml(k)).join("")}</div>` : ""}
          ${c.melds && c.melds.length ? `<div class="ct-row">${c.melds.map((m) => `<div class="ct-meld" style="width:calc(min(30px,6vw) + ${(m.length - 1) * 12}px)">${m.map((k, j) => cardHtml(k, "", `style="left:${j * 12}px"`)).join("")}</div>`).join("")}</div>` : ""}
          ${c.label ? `<div class="lbl">${esc(c.label)}</div>` : ""}</div>`;
        const say = t ? t.prompt : turnSeat !== undefined ? `Waiting for ${names[turnSeat]}…` : "";
        const cards = hand.map((k, i) => cardHtml(k, `${picked.includes(i) ? "up" : ""}${t && !t.playable.includes(i) && t.playable.length ? " no" : ""}${row === 0 && spot === i && t ? " cur" : ""}`, `data-i="${i}"`)).join("");
        const btns = buttons.map((b, i) => { const ready = b.cards === 0 || (b.cards === -1 && picked.length) || b.cards === picked.length; return `<button class="btn small${ready ? " ready" : ""}${row === 1 && spot === i ? " cur" : ""}" data-b="${esc(b.id)}">${esc(b.label)}</button>`; }).join("");
        stage.innerHTML = `<div class="ct"><div style="display:flex;align-items:center;gap:10px"><b style="flex:1">${esc(g.title)}</b><button class="btn small" id="ctMenu">Menu</button></div>
          <div class="ct-felt">${mid}${trick}${seatsHtml}</div>
          <div class="ct-hand"><div class="say${t ? "" : " dim"}">${esc(say)}</div><div class="ct-cards">${cards}</div>${btns ? `<div class="ct-btns">${btns}</div>` : ""}</div></div>`;
        stage.querySelectorAll("[data-i]").forEach((el) => { el.onclick = () => tap(+el.dataset.i); });
        stage.querySelectorAll("[data-b]").forEach((el) => { el.onclick = () => press(el.dataset.b); });
        $("#ctMenu").onclick = openMenu;
        tick();
      };
      // Glowfin's seats, the pause after a trick, and the end
      const tick = () => {
        if (busy || done) return;
        if (game.over) {
          done = true;
          const w = game.winners().map((s) => names[s]);
          overlay(w.length === 1 ? `${w[0]} wins!` : `${w.join(" and ")} win!`, game.results(), [["Play again", () => start(names)], ["Change seats", lobby], ["Leave", ctx.quit]]);
          return;
        }
        if (game.waiting) { busy = true; later(() => { busy = false; game.advance(); picked = []; draw(); }, 1600); return; }
        const bot = range(n).find((s) => s > 0 && game.turn(s));
        if (bot !== undefined) {
          busy = true;
          later(() => { busy = false; const ch = game.glowfin(bot); if (ch) game.press(bot, ch[0], ch[1]); draw(); }, game instanceof Holdem ? 900 : 750);
        }
      };
      keyHandler = (e) => {
        const t = game.turn(0), hand = game.hand(0), buttons = t ? t.buttons : [];
        const d = ({ ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] })[e.key];
        if (d) {
          if (d[1]) { row = Math.max(t && !t.playable.length ? 1 : 0, Math.min(buttons.length ? 1 : 0, row + d[1])); spot = 0; }
          else spot += d[0];
          draw(); return true;
        }
        if (e.key === "Enter" || e.key === " ") {
          if (!t) return true;
          if (row === 0 && hand.length && t.playable.length) { tap(spot); if (!t.pickMany && buttons.length) { row = 1; spot = 0; draw(); } }
          else if (buttons[spot]) press(buttons[spot].id);
          return true;
        }
        if (e.key === "Escape") { openMenu(); return true; }
        return false;
      };
      draw();
    };
    lobby();
  };

  B.cardTable = { games: Object.keys(GAMES), play };
  // The rules on their own, for tests
  B.cardRules = { Hearts, Spades, CrazyEights, Rummy, GoFish, Holdem, best, isMeld };
})();
