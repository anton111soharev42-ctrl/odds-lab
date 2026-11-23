'use strict';
/* Odds Lab engines — port of the Android (Kotlin) engines.
   Card = rank*4 + suit, rank 0..12 = 2..A, suit 0..3 = ♠ ♥ ♦ ♣. */

const RANKS = '23456789TJQKA';
const SUITS = ['♠', '♥', '♦', '♣'];
const rankOf = c => c >> 2;
const suitOf = c => c & 3;
const mkCard = (r, s) => (r << 2) | s;
const rankLabel = r => (r === 8 ? '10' : RANKS[r]);
const cardLabel = c => rankLabel(rankOf(c)) + SUITS[suitOf(c)];

/* ---------------- Hand evaluator ---------------- */
const CAT = { HIGH: 0, PAIR: 1, TWO_PAIR: 2, TRIPS: 3, STRAIGHT: 4, FLUSH: 5, FULL_HOUSE: 6, QUADS: 7, STRAIGHT_FLUSH: 8 };
const CAT_NAMES = ['Старшая карта', 'Пара', 'Две пары', 'Тройка', 'Стрит', 'Флеш', 'Фулл-хаус', 'Каре', 'Стрит-флеш'];
const PLURAL_GEN = ['двоек', 'троек', 'четвёрок', 'пятёрок', 'шестёрок', 'семёрок', 'восьмёрок', 'девяток', 'десяток', 'валетов', 'дам', 'королей', 'тузов'];

function popcount(x) {
  x -= (x >> 1) & 0x55555555;
  x = (x & 0x33333333) + ((x >> 2) & 0x33333333);
  return (((x + (x >> 4)) & 0x0f0f0f0f) * 0x01010101) >>> 24;
}
function straightHigh(m) {
  for (let h = 12; h >= 4; h--) if (((m >> (h - 4)) & 31) === 31) return h;
  if ((m & 0x100f) === 0x100f) return 3;
  return -1;
}
function packTop(prefix, pc, mask, n) {
  let v = prefix, placed = pc, taken = 0;
  for (let r = 12; taken < n && r >= 0; r--) {
    if ((mask >> r) & 1) { v = (v << 4) | (r + 1); placed++; taken++; }
  }
  while (placed < 5) { v <<= 4; placed++; }
  return v;
}
const _cnt = new Int32Array(13), _sm = new Int32Array(4);
function evalHand(cards, n) {
  _cnt.fill(0); _sm.fill(0);
  let all = 0;
  for (let i = 0; i < n; i++) {
    const c = cards[i], r = c >> 2;
    _cnt[r]++; _sm[c & 3] |= 1 << r; all |= 1 << r;
  }
  for (let s = 0; s < 4; s++) {
    if (popcount(_sm[s]) >= 5) {
      const h = straightHigh(_sm[s]);
      return h >= 0 ? (8 << 20) | packTop(h + 1, 1, 0, 0) : (5 << 20) | packTop(0, 0, _sm[s], 5);
    }
  }
  let quad = -1, t1 = -1, t2 = -1, p1 = -1, p2 = -1;
  for (let r = 12; r >= 0; r--) {
    const k = _cnt[r];
    if (k === 4) { if (quad < 0) quad = r; }
    else if (k === 3) { if (t1 < 0) t1 = r; else if (t2 < 0) t2 = r; }
    else if (k === 2) { if (p1 < 0) p1 = r; else if (p2 < 0) p2 = r; }
  }
  if (quad >= 0) return (7 << 20) | packTop(quad + 1, 1, all & ~(1 << quad), 1);
  if (t1 >= 0 && (t2 >= 0 || p1 >= 0)) return (6 << 20) | packTop(((t1 + 1) << 4) | (Math.max(t2, p1) + 1), 2, 0, 0);
  const st = straightHigh(all);
  if (st >= 0) return (4 << 20) | packTop(st + 1, 1, 0, 0);
  if (t1 >= 0) return (3 << 20) | packTop(t1 + 1, 1, all & ~(1 << t1), 2);
  if (p2 >= 0) return (2 << 20) | packTop(((p1 + 1) << 4) | (p2 + 1), 2, all & ~(1 << p1) & ~(1 << p2), 1);
  if (p1 >= 0) return (1 << 20) | packTop(p1 + 1, 1, all & ~(1 << p1), 3);
  return packTop(0, 0, all, 5);
}
const catOf = s => s >>> 20;
function describeHand(score) {
  const cat = catOf(score);
  const a = ((score >> 16) & 15) - 1, b = ((score >> 12) & 15) - 1;
  const r = rankLabel;
  switch (cat) {
    case 0: return `Старшая карта ${r(a)}`;
    case 1: return `Пара ${PLURAL_GEN[a]}`;
    case 2: return `Две пары: ${r(a)} и ${r(b)}`;
    case 3: return `Тройка ${PLURAL_GEN[a]}`;
    case 4: return `Стрит до ${r(a)}`;
    case 5: return `Флеш, старшая ${r(a)}`;
    case 6: return `Фулл-хаус: ${r(a)} на ${r(b)}`;
    case 7: return `Каре ${PLURAL_GEN[a]}`;
    default: return a === 12 ? 'Роял-флеш' : `Стрит-флеш до ${r(a)}`;
  }
}

/* ---------------- Preflop ranking (169 classes) ---------------- */
function classIndex(c1, c2) {
  const r1 = rankOf(c1), r2 = rankOf(c2), hi = Math.max(r1, r2), lo = Math.min(r1, r2);
  if (hi === lo) return hi * 13 + hi;
  return suitOf(c1) === suitOf(c2) ? hi * 13 + lo : lo * 13 + hi;
}
const combosOf = i => { const a = (i / 13) | 0, b = i % 13; return a === b ? 6 : a > b ? 4 : 12; };
function className(i) {
  const a = (i / 13) | 0, b = i % 13, hi = Math.max(a, b), lo = Math.min(a, b);
  return RANKS[hi] + RANKS[lo] + (a === b ? '' : a > b ? 's' : 'o');
}
function xorshift(seed) {
  let x = seed | 0 || 1;
  return () => { x ^= x << 13; x ^= x >>> 17; x ^= x << 5; return (x >>> 0) / 4294967296; };
}
let _pf = null;
function preflopTable() {
  if (_pf) return _pf;
  const rnd = xorshift(20261124);
  const eq = new Float64Array(169);
  const deck = new Int32Array(50), me = new Int32Array(7), op = new Int32Array(7);
  for (let idx = 0; idx < 169; idx++) {
    const a = (idx / 13) | 0, b = idx % 13, hi = Math.max(a, b), lo = Math.min(a, b);
    const h1 = mkCard(hi, 0), h2 = a === b ? mkCard(lo, 1) : a > b ? mkCard(lo, 0) : mkCard(lo, 1);
    let k = 0;
    for (let c = 0; c < 52; c++) if (c !== h1 && c !== h2) deck[k++] = c;
    me[0] = h1; me[1] = h2;
    let share = 0;
    const iters = 4000;
    for (let it = 0; it < iters; it++) {
      let n = 50;
      for (let j = 0; j < 7; j++) {
        const i = (rnd() * n) | 0, c = deck[i]; deck[i] = deck[n - 1]; deck[n - 1] = c; n--;
        if (j < 2) op[j] = c; else { me[j] = c; op[j] = c; }
      }
      const x = evalHand(me, 7), y = evalHand(op, 7);
      share += x > y ? 1 : x === y ? 0.5 : 0;
    }
    eq[idx] = share / iters;
  }
  const order = [...Array(169).keys()].sort((p, q) => eq[q] - eq[p]);
  const start = new Float64Array(169), end = new Float64Array(169);
  let cum = 0;
  for (const idx of order) { start[idx] = (cum * 100) / 1326; cum += combosOf(idx); end[idx] = (cum * 100) / 1326; }
  _pf = { eq, start, end };
  return _pf;
}
function allowedClasses(pct) {
  const t = preflopTable();
  const a = new Uint8Array(169);
  for (let i = 0; i < 169; i++) a[i] = t.start[i] < pct ? 1 : 0;
  return a;
}

/* ---------------- Equity ---------------- */
function newAcc() { return { n: 0, share: 0, shareSq: 0, win: 0, tie: 0, lose: 0, cats: new Float64Array(9) }; }
function accAdd(acc, h, best, tied) {
  acc.n++; acc.cats[catOf(h)]++;
  let s;
  if (h > best) { acc.win++; s = 1; } else if (h === best) { acc.tie++; s = 1 / (tied + 1); } else { acc.lose++; s = 0; }
  acc.share += s; acc.shareSq += s * s;
}
function accResult(acc, exact) {
  const n = Math.max(1, acc.n), mean = acc.share / n;
  const v = Math.max(0, acc.shareSq / n - mean * mean);
  return {
    equity: mean, win: acc.win / n, tie: acc.tie / n, lose: acc.lose / n, samples: acc.n, exact,
    stdErr: exact ? 0 : Math.sqrt(v / n), finalCat: Array.from(acc.cats, x => x / n),
  };
}
const tick = () => new Promise(r => setTimeout(r, 0));

/** isStale() returning true aborts (returns null). */
async function computeEquity(hero, board, opps, rangePct, isStale) {
  const allowed = rangePct >= 100 ? null : allowedClasses(rangePct);
  const dead = new Uint8Array(52);
  for (const c of hero) dead[c] = 1;
  for (const c of board) dead[c] = 1;
  const u = [];
  for (let c = 0; c < 52; c++) if (!dead[c]) u.push(c);
  const m = u.length, missing = 5 - board.length, bs = board.length;

  if (opps === 1 && missing <= 2) {
    let pairs = 0;
    for (let i = 0; i < m; i++) for (let j = i + 1; j < m; j++) if (!allowed || allowed[classIndex(u[i], u[j])]) pairs++;
    const boards = missing === 0 ? 1 : missing === 1 ? m - 2 : ((m - 2) * (m - 3)) / 2;
    if (pairs > 0 && pairs * boards <= 1.5e6) {
      const acc = newAcc();
      const me = new Int32Array(7), op = new Int32Array(7), rest = new Int32Array(m);
      me[0] = hero[0]; me[1] = hero[1];
      for (let k = 0; k < bs; k++) { me[2 + k] = board[k]; op[2 + k] = board[k]; }
      const score = () => { const h = evalHand(me, 7), o = evalHand(op, 7); accAdd(acc, h, o, h === o ? 1 : 0); };
      for (let i = 0; i < m; i++) {
        if (i % 4 === 0) { await tick(); if (isStale()) return null; }
        for (let j = i + 1; j < m; j++) {
          if (allowed && !allowed[classIndex(u[i], u[j])]) continue;
          op[0] = u[i]; op[1] = u[j];
          let rn = 0;
          for (let k = 0; k < m; k++) if (k !== i && k !== j) rest[rn++] = u[k];
          if (missing === 0) score();
          else if (missing === 1) for (let a = 0; a < rn; a++) { me[6] = op[6] = rest[a]; score(); }
          else for (let a = 0; a < rn; a++) {
            me[5] = op[5] = rest[a];
            for (let b = a + 1; b < rn; b++) { me[6] = op[6] = rest[b]; score(); }
          }
        }
      }
      return accResult(acc, true);
    }
  }

  const iters = Math.max(40000, Math.min(150000, Math.floor(360000 / (1 + opps))));
  const deck = Int32Array.from(u);
  const me = new Int32Array(7);
  me[0] = hero[0]; me[1] = hero[1];
  for (let k = 0; k < bs; k++) me[2 + k] = board[k];
  const ops = [];
  for (let o = 0; o < opps; o++) { const a = new Int32Array(7); for (let k = 0; k < bs; k++) a[2 + k] = board[k]; ops.push(a); }
  const scores = new Int32Array(opps);
  const acc = newAcc();
  const rnd = Math.random;
  for (let it = 0; it < iters; it++) {
    if (it % 5000 === 0) { await tick(); if (isStale()) return null; }
    let n = m;
    for (let o = 0; o < opps; o++) {
      let tries = 0;
      for (;;) {
        let i = (rnd() * n) | 0, t = deck[i]; deck[i] = deck[n - 1]; deck[n - 1] = t; n--;
        i = (rnd() * n) | 0; t = deck[i]; deck[i] = deck[n - 1]; deck[n - 1] = t; n--;
        const c1 = deck[n + 1], c2 = deck[n];
        if (!allowed || allowed[classIndex(c1, c2)] || ++tries > 300) { ops[o][0] = c1; ops[o][1] = c2; break; }
        n += 2;
      }
    }
    for (let k = 0; k < missing; k++) {
      const i = (rnd() * n) | 0, t = deck[i]; deck[i] = deck[n - 1]; deck[n - 1] = t; n--;
      const pos = 2 + bs + k;
      me[pos] = t;
      for (let o = 0; o < opps; o++) ops[o][pos] = t;
    }
    const h = evalHand(me, 7);
    let best = -1;
    for (let o = 0; o < opps; o++) { const s = evalHand(ops[o], 7); scores[o] = s; if (s > best) best = s; }
    let tied = 0;
    if (h === best) for (let o = 0; o < opps; o++) if (scores[o] === h) tied++;
    accAdd(acc, h, best, tied);
  }
  return accResult(acc, false);
}

async function analyzePoker(hero, board, opps, rangePct, isStale) {
  const eq = await computeEquity(hero, board, opps, rangePct, isStale);
  if (!eq) return null;
  const all = hero.concat(board);
  const out = { equity: eq, handNow: null, preflopClass: null, preflopTop: null, outs: [], outsHit: null, unseen: 52 - all.length };
  if (board.length) out.handNow = describeHand(evalHand(all, all.length));
  else {
    const idx = classIndex(hero[0], hero[1]);
    out.preflopClass = className(idx);
    out.preflopTop = preflopTable().end[idx];
  }
  if (board.length === 3 || board.length === 4) {
    const cur = catOf(evalHand(all, all.length));
    const wh = all.concat([0]), bo = board.concat([0]);
    for (let c = 0; c < 52; c++) {
      if (all.includes(c)) continue;
      wh[all.length] = c; bo[board.length] = c;
      const nc = catOf(evalHand(wh, wh.length)), bc = catOf(evalHand(bo, bo.length));
      if (nc > cur && nc > bc) out.outs.push(c);
    }
    const U = out.unseen, o = out.outs.length;
    out.outsHit = board.length === 3 ? 1 - ((U - o) * (U - o - 1)) / (U * (U - 1)) : o / U;
  }
  return out;
}

/* Positions: open = open-raise range (top % of hands), push = all-in range at 10 BB (top %). */
const POSITIONS = [
  { name: 'Ранняя', full: 'ранней позиции', open: 14, push: 13, ip: true },
  { name: 'Средняя', full: 'средней позиции', open: 20, push: 18, ip: true },
  { name: 'Поздняя', full: 'поздней позиции (CO/баттон)', open: 38, push: 32, ip: true },
  { name: 'МБ', full: 'малого блайнда', open: 35, push: 45, ip: false },
  { name: 'ББ', full: 'большого блайнда', open: 0, push: 50, ip: false },
];

const _P = x => (x * 100).toFixed(1) + '%';
const _num = x => { const a = Math.abs(x); return Math.abs(a - Math.round(a)) < 1e-9 ? a.toFixed(0) : a.toFixed(2); };
const _sg = x => (x >= 0 ? '+' : '−');

/**
 * ctx: { preflop, top (hero hand top-% or null), cls, pos (0..4), stack, bb, range }
 * pot = pot before hero acts (incl. opponents' bets), toCall = amount hero must add.
 */
function pokerDecision(eq, opps, pot, toCall, ctx = {}) {
  const bb = ctx.bb > 0 ? ctx.bb : 0;
  if (ctx.preflop && bb > 0 && ctx.top != null) return preflopDecision(eq, opps, pot, toCall, ctx);
  const stack = ctx.stack > 0 ? ctx.stack : 0;
  const chips = x => _num(x) + (bb > 0 ? ` (${(x / bb).toFixed(1)} ББ)` : '');
  const fair = 1 / (opps + 1);
  const lines = [`Средняя доля банка при ${opps + 1} игроках: ${_P(fair)}. У тебя ${_P(eq)} (×${(eq / fair).toFixed(2)}).`];
  if (stack > 0 && pot > 0) lines.push(`SPR (стек ÷ банк): ${(stack / pot).toFixed(1)}${stack / pot <= 2.5 ? ' — низкий: с хорошей рукой ты уже «привязан» к банку.' : ''}`);
  if (toCall > 0 && pot > 0) {
    const call = stack > 0 ? Math.min(toCall, stack) : toCall;
    const allIn = stack > 0 && call >= stack;
    const need = call / (pot + call), ev = eq * (pot + call) - call;
    lines.push(`Шансы банка: вкладываешь ${chips(call)} в итоговый банк ${chips(pot + call)} → нужно ≥ ${_P(need)} эквити.`);
    if (allIn) lines.push(`Колл = весь твой стек (${chips(stack)}).`);
    lines.push(`EV колла: ${_sg(ev)}${_num(ev)} фишек (${_sg(ev)}${Math.abs((ev / call) * 100).toFixed(0)}% от колла).`);
    if (ev > 0) {
      if (eq >= 0.5 && eq >= 1.8 * fair && !allIn) {
        const r = toCall * 3 + pot;
        if (stack > 0 && r >= stack * 0.6) { lines.push(`Рейз почти на весь стек — проще сразу ва-банк (${chips(stack)}).`); return { verdict: 'ВА-БАНК', tone: 'good', lines }; }
        lines.push(`Эквити сильно выше нужного — выгоднее повышать, чтобы соперники платили больше. Рейз ≈ до ${chips(r)}.`);
        return { verdict: 'РЕЙЗ', tone: 'good', lines };
      }
      lines.push(`Запас по эквити: +${_P(eq - need)} к минимально нужному.`);
      return { verdict: allIn ? 'КОЛЛ ВА-БАНК' : 'КОЛЛ', tone: 'good', lines };
    }
    const implied = call / eq - pot - call;
    lines.push(`Не хватает ${_P(need - eq)} эквити.`);
    if (!allIn && eq > 0 && isFinite(implied)) {
      lines.push(`Колл окупится, только если на следующих улицах добрать ещё ≥ ${chips(implied)} (implied odds)${stack > 0 && implied > stack - call ? ' — а у тебя за спиной меньше, так что точно пас.' : '.'}`);
    }
    return { verdict: 'ПАС', tone: 'bad', lines };
  }
  if (pot <= 0) lines.push('Введи банк (и сумму колла, если против тебя ставка) — посчитаю точное EV решения и размеры ставок.');
  if (eq >= 0.5 && eq >= 1.6 * fair) {
    if (pot > 0 && stack > 0 && stack / pot <= 2.5) { lines.push(`Ты фаворит при низком SPR — ставь ва-банк (${chips(stack)}).`); return { verdict: 'ВА-БАНК', tone: 'good', lines }; }
    lines.push(pot > 0 ? `Ты фаворит раздачи: ставь на вэлью ½–¾ банка = ${chips(pot * 0.5)} – ${chips(pot * 0.75)}.` : 'Ты фаворит раздачи: ставь на вэлью ½–¾ банка.');
    return { verdict: 'СТАВКА', tone: 'good', lines };
  }
  if (eq >= fair) {
    lines.push(pot > 0 ? `Выше среднего, но не доминируешь: чек или небольшая ставка ⅓ банка = ${chips(pot / 3)}.` : 'Выше среднего, но не доминируешь: чек или небольшая ставка.');
    return { verdict: 'ЧЕК', tone: 'mid', lines };
  }
  lines.push('Ниже средней доли: чек, а на ставку соперника — пас, если шансы банка не позволяют колл.');
  return { verdict: 'ЧЕК / ПАС', tone: 'bad', lines };
}

function preflopDecision(eq, opps, pot, toCall, ctx) {
  const bb = ctx.bb, p = POSITIONS[ctx.pos] || POSITIONS[0], top = ctx.top;
  const stack = ctx.stack > 0 ? ctx.stack : 100 * bb;
  const sBB = stack / bb;
  const chips = x => `${_num(x)} (${(x / bb).toFixed(1)} ББ)`;
  const posted = ctx.pos === 4 ? bb : ctx.pos === 3 ? bb / 2 : 0;
  const facingRaise = toCall > bb - posted + bb * 0.01;
  const potEff = pot > 0 ? pot : 1.5 * bb + (facingRaise ? toCall + posted : 0);
  const limpers = !facingRaise && pot > 0 ? Math.max(0, Math.round((pot - 1.5 * bb) / bb)) : 0;
  const lines = [
    `Рука ${ctx.cls}: топ ${top.toFixed(1)}% стартовых рук. Стек: ${chips(stack)}${ctx.stack > 0 ? '' : ' — не задан, считаю 100 ББ'}.`,
  ];
  if (pot <= 0) lines.push(`Банк не введён — оцениваю в ${chips(potEff)}.`);
  const res = (verdict, tone, extra) => { if (extra) lines.push(extra); return { verdict, tone, lines }; };

  /* ---------- Short stack: push / fold ---------- */
  if (sBB <= 15) {
    lines.push(`Короткий стек (≤ 15 ББ): играем только «ва-банк или пас» — так соперники не могут тебя пережать.`);
    if (!facingRaise) {
      const pushPct = Math.max(4, Math.min(90, p.push * Math.pow(10 / sBB, 0.6)));
      lines.push(`С ${p.full} при ${sBB.toFixed(1)} ББ пушим топ ${pushPct.toFixed(0)}% рук.`);
      if (top <= pushPct) return res('ВА-БАНК', 'good', `Ставь весь стек: ${chips(stack)}.`);
      if (ctx.pos === 4 && toCall <= 0) return res('ЧЕК', 'mid', 'Ты на большом блайнде и платить не нужно — бесплатно смотришь флоп.');
      return res('ПАС', 'bad', 'Рука слабее диапазона пуша.');
    }
    const call = Math.min(toCall, stack), need = call / (potEff + call);
    lines.push(`Нужно доставить ${chips(call)} в банк ${chips(potEff + call)} → нужно ≥ ${_P(need)} эквити, у тебя ${_P(eq)}.`);
    if (ctx.range >= 100) lines.push('⚠ Против рейза выбери диапазон соперника «Топ 15–30%» — против «любых» рук эквити завышено.');
    if (top <= 7) return res('ВА-БАНК', 'good', `Сильнейшая рука — пушим весь стек ${chips(stack)} поверх рейза.`);
    if (eq >= need) return res(call >= stack ? 'КОЛЛ ВА-БАНК' : 'ВА-БАНК', 'good', `EV ${_sg(eq * (potEff + call) - call)}${_num(eq * (potEff + call) - call)} фишек.`);
    return res('ПАС', 'bad', `Не хватает ${_P(need - eq)} эквити.`);
  }

  /* ---------- Deep stack, nobody raised ---------- */
  if (!facingRaise) {
    if (ctx.pos === 4) {
      if (top <= 12) {
        const to = Math.min(stack, (3.5 + limpers) * bb);
        return res('РЕЙЗ', 'good', `Сильная рука на ББ — повышай до ${chips(to)}, наказывая лимперов.`);
      }
      return res('ЧЕК', 'mid', 'Ты на большом блайнде, рейза не было — чекай и смотри флоп бесплатно.');
    }
    const size = (ctx.pos === 3 ? 3 : 2.5) + limpers;
    const to = Math.min(stack, size * bb);
    lines.push(`С ${p.full} открываемся рейзом с топ ${p.open}% рук.${limpers ? ` Лимперов перед тобой: ${limpers} — +1 ББ к рейзу за каждого.` : ''}`);
    if (top <= p.open) return res('РЕЙЗ', 'good', `Рейз до ${chips(to)}. Лимп (просто колл ББ) хуже — теряешь инициативу.`);
    if (limpers > 0 && top <= p.open * 1.7 && sBB >= 40) {
      return res('КОЛЛ', 'mid', `Есть лимперы и глубокий стек — можно дёшево войти (${chips(bb - posted)}) ради сета/флеша/стрита. Без попадания на флопе — сбрасывай.`);
    }
    return res('ПАС', 'bad', 'Рука вне диапазона открытия с этой позиции.');
  }

  /* ---------- Deep stack, facing a raise ---------- */
  const call = Math.min(toCall, stack);
  const need = call / (potEff + call);
  const raiseTo = toCall + posted;
  const raiseBB = raiseTo / bb;
  const threeBet = raiseBB <= 4 ? 5 : raiseBB <= 8 ? 3.5 : 2.5;
  lines.push(`Против рейза до ${chips(raiseTo)}: доставить ${chips(call)} в банк ${chips(potEff + call)} → нужно ≥ ${_P(need)} эквити, у тебя ${_P(eq)}.`);
  if (ctx.range >= 100) lines.push('⚠ Тот, кто рейзит, держит не «любые» руки — выбери диапазон «Топ 15–30%», чтобы эквити было честным.');
  if (call >= stack * 0.4) {
    if (top <= threeBet + 2 || eq >= need) return res(call >= stack ? 'КОЛЛ ВА-БАНК' : 'ВА-БАНК', 'good', 'Колл съедает большую часть стека — играем сразу весь стек.');
    return res('ПАС', 'bad', `Не хватает ${_P(need - eq)} эквити, а на кону почти весь стек.`);
  }
  if (top <= threeBet) {
    const to = raiseTo * (p.ip ? 3 : 3.7);
    if (to >= stack * 0.4) return res('ВА-БАНК', 'good', `Топ ${threeBet}% рук: ре-рейз почти на весь стек — ставь сразу ${chips(stack)}.`);
    return res('3-БЕТ', 'good', `Топ ${threeBet}% рук: ре-рейз до ${chips(to)}${p.ip ? ' (×3 от рейза)' : ' (из блайндов ×3.5–4 от рейза)'}.`);
  }
  if (eq >= need && top <= 22) return res('КОЛЛ', 'good', 'Рука в диапазоне колла, и колл выгоден уже по прямым шансам банка.');
  // Speculative hands (pairs, suited connectors / suited aces) are paid by implied odds when stacks are deep.
  const h = ctx.hero || [];
  let spec = '';
  if (h.length === 2) {
    const r1 = rankOf(h[0]), r2 = rankOf(h[1]), suited = suitOf(h[0]) === suitOf(h[1]);
    const gap = Math.abs(r1 - r2);
    if (r1 === r2) spec = 'карманная пара — ловим сет (≈ 12% на флопе)';
    else if (suited && (gap <= 2 || Math.max(r1, r2) === 12)) spec = 'одномастная рука — ловим флеш/стрит';
  }
  if (spec) {
    const ratio = (stack - call) / call;
    lines.push(`${spec[0].toUpperCase() + spec.slice(1)}. За спиной ${ratio.toFixed(0)}× колла${ratio >= 15 ? ' — достаточно для implied odds (нужно ≥ 15×).' : ' — мало, нужно ≥ 15×.'}`);
    if (ratio >= 15) return res('КОЛЛ', 'mid', 'Колл ради сильного попадания. Без сета/дро на флопе — сбрасывай на ставку.');
  }
  return res('ПАС', 'bad', eq < need ? `Не хватает ${_P(need - eq)} эквити, а рука не тянет на implied odds.` : 'Рука слабовата против рейза: часто будешь доминирован (тот же туз/король с худшим кикером).');
}

/* ---------------- Blackjack ---------------- */
// values: 1 = ace, 2..9, 10 = ten-value. shoe[v-1] = count.
const BJ_STD = [4, 4, 4, 4, 4, 4, 4, 4, 4, 16];
const BJ_ACTIONS = { STAND: 'Стоять', HIT: 'Взять карту', DOUBLE: 'Удвоить', SPLIT: 'Сплит', SURRENDER: 'Сдаться' };
const freshShoe = d => BJ_STD.map(x => x * d);
const bestTotal = (hard, ace) => (ace && hard + 10 <= 21 ? hard + 10 : hard);
const bjLabel = v => (v === 1 ? 'A' : String(v));

function standEv(pt, d) {
  if (pt > 21) return -1;
  let ev = d[5];
  for (let i = 0; i < 5; i++) { const dt = 17 + i; if (pt > dt) ev += d[i]; else if (pt < dt) ev -= d[i]; }
  return ev;
}
function dealerDist(up, shoe, rules) {
  const out = [0, 0, 0, 0, 0, 0];
  const s = shoe.slice();
  const total = s.reduce((a, b) => a + b, 0);
  const bjCard = up === 1 ? 10 : up === 10 ? 1 : 0;
  const denom = total - (bjCard ? s[bjCard - 1] : 0);
  if (denom <= 0) return out;
  const rec = (hard, ace, tot, prob) => {
    const soft = ace && hard + 10 <= 21, best = soft ? hard + 10 : hard;
    if (best > 21) { out[5] += prob; return; }
    if (best >= 17 && !(rules.h17 && soft && best === 17)) { out[best - 17] += prob; return; }
    if (tot <= 0) { out[5] += prob; return; }
    for (let v = 1; v <= 10; v++) {
      const c = s[v - 1]; if (!c) continue;
      s[v - 1]--; rec(hard + v, ace || v === 1, tot - 1, (prob * c) / tot); s[v - 1]++;
    }
  };
  for (let v = 1; v <= 10; v++) {
    if (v === bjCard) continue;
    const c = s[v - 1]; if (!c) continue;
    s[v - 1]--; rec(up + v, up === 1 || v === 1, total - 1, c / denom); s[v - 1]++;
  }
  return out;
}
function makeSolver(d) {
  const memo = new Map();
  const standOrHit = (hard, ace, s, tot, key) => {
    const best = bestTotal(hard, ace);
    if (best > 21) return -1;
    const st = standEv(best, d);
    if (best === 21) return st;
    return Math.max(st, hit(hard, ace, s, tot, key));
  };
  const hit = (hard, ace, s, tot, key) => {
    const k = hard + (ace ? 'a' : 'h') + key;
    const got = memo.get(k);
    if (got !== undefined) return got;
    if (tot <= 0) return standEv(bestTotal(hard, ace), d);
    let ev = 0;
    for (let v = 1; v <= 10; v++) {
      const c = s[v - 1]; if (!c) continue;
      const p = c / tot, nh = hard + v;
      if (nh > 21) { ev -= p; continue; }
      s[v - 1]--;
      ev += p * standOrHit(nh, ace || v === 1, s, tot - 1, key + Math.pow(32, v - 1));
      s[v - 1]++;
    }
    memo.set(k, ev);
    return ev;
  };
  const dbl = (hard, ace, s, tot) => {
    if (tot <= 0) return 2 * standEv(bestTotal(hard, ace), d);
    let ev = 0;
    for (let v = 1; v <= 10; v++) {
      const c = s[v - 1]; if (!c) continue;
      const p = c / tot, nh = hard + v;
      ev += nh > 21 ? -2 * p : 2 * p * standEv(bestTotal(nh, ace || v === 1), d);
    }
    return ev;
  };
  return { standOrHit, hit, dbl };
}
/** shoe must already exclude the player's cards and the up-card. */
function analyzeBj(player, up, shoe, rules) {
  const s = shoe.slice();
  const tot = s.reduce((a, b) => a + b, 0);
  const hard = player.reduce((a, b) => a + b, 0), ace = player.includes(1);
  const best = bestTotal(hard, ace);
  const bjCard = up === 1 ? 10 : up === 10 ? 1 : 0;
  const pDealerBj = bjCard && tot ? s[bjCard - 1] / tot : 0;
  const p10 = tot ? s[9] / tot : 0;
  const insurance = up === 1 ? 3 * p10 - 1 : null;
  const d = dealerDist(up, s, rules);
  const base = { total: best, soft: ace && hard + 10 <= 21, dealer: d, pDealerBj, p10, insurance };
  if (player.length === 2 && best === 21) {
    const ev = rules.peek && up === 10 ? rules.bjPayout : (1 - pDealerBj) * rules.bjPayout;
    return { ...base, isBj: true, bjEv: ev, evs: {}, best: null, bust: 0 };
  }
  const sv = makeSolver(d);
  const evs = {};
  evs.STAND = standEv(best, d);
  if (best < 21) evs.HIT = sv.hit(hard, ace, s, tot, 0);
  const two = player.length === 2;
  if (two && best < 21) evs.DOUBLE = sv.dbl(hard, ace, s, tot);
  if (two && player[0] === player[1]) {
    const v = player[0];
    let ev = 0;
    for (let v2 = 1; v2 <= 10; v2++) {
      const c = s[v2 - 1]; if (!c) continue;
      const p = c / tot;
      s[v2 - 1]--;
      const h = v + v2, a = v === 1 || v2 === 1;
      let hand;
      if (v === 1) hand = standEv(bestTotal(h, a), d);
      else {
        hand = sv.standOrHit(h, a, s, tot - 1, Math.pow(32, v2 - 1));
        if (rules.das) hand = Math.max(hand, sv.dbl(h, a, s, tot - 1));
      }
      ev += p * hand;
      s[v2 - 1]++;
    }
    evs.SPLIT = 2 * ev;
  }
  if (two && rules.surrender) evs.SURRENDER = -0.5;
  if (!rules.peek && pDealerBj > 0) {
    for (const a of Object.keys(evs)) {
      const risk = a === 'DOUBLE' || a === 'SPLIT' ? 2 : 1;
      evs[a] = pDealerBj * -risk + (1 - pDealerBj) * evs[a];
    }
  }
  let bust = 0;
  if (tot) for (let v = 1; v <= 10; v++) if (hard + v > 21) bust += s[v - 1] / tot;
  let bestA = null;
  for (const a of Object.keys(evs)) if (bestA === null || evs[a] > evs[bestA]) bestA = a;
  return { ...base, isBj: false, evs, best: bestA, bust };
}

/* ---------------- Hi-Lo counting ---------------- */
const hiLo = v => (v >= 2 && v <= 6 ? 1 : v >= 7 && v <= 9 ? 0 : -1);
function baseEdgePct(r) {
  let e = -0.5;
  e += ({ 1: 0.48, 2: 0.19, 4: 0.06, 8: -0.02 })[r.decks] || 0;
  if (r.h17) e -= 0.2;
  if (!r.das) e -= 0.14;
  if (r.surrender) e += 0.08;
  if (r.bjPayout < 1.4) e -= 1.39;
  if (!r.peek) e -= 0.11;
  return e;
}
function countStatus(seen, rules) {
  let rc = 0, n = 0;
  for (let v = 1; v <= 10; v++) { rc += hiLo(v) * seen[v - 1]; n += seen[v - 1]; }
  const left = Math.max(0, rules.decks * 52 - n);
  const decksLeft = Math.max(0.25, left / 52);
  const tc = rc / decksLeft;
  const edge = baseEdgePct(rules) + 0.5 * tc;
  const units = tc < 1 ? 1 : Math.min(12, Math.floor(tc));
  const kelly = edge > 0 ? edge / 1.33 : 0;
  let advice;
  if (tc <= -2) advice = 'Колода «холодная»: минимальная ставка, а лучше пропусти раздачи / смени стол.';
  else if (edge <= 0) advice = `Перевес у казино (${edge.toFixed(2)}%). Ставь минимум.`;
  else advice = `Перевес у тебя ≈ +${edge.toFixed(2)}%. Ставка ≈ ${units} ед., по Келли ≈ ${kelly.toFixed(2)}% банкролла (безопаснее ½ Келли).`;
  return { rc, left, decksLeft, tc, edge, units, kelly, advice };
}

if (typeof module !== 'undefined') {
  module.exports = { evalHand, catOf, computeEquity, analyzePoker, preflopTable, analyzeBj, freshShoe, dealerDist, countStatus, mkCard, classIndex };
}
