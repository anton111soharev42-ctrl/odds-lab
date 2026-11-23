'use strict';
/* Odds Lab — web UI (PWA). Engines live in engines.js; poker equity runs in worker.js. */

const KEY = 'oddslab.v1';
function defaults() {
  return {
    tab: 0,
    poker: { slots: [null, null, null, null, null, null, null], sel: 0, pr: null, ps: null, opps: 1, range: 100, pot: '', call: '' },
    bj: { rules: { decks: 6, h17: false, das: true, surrender: true, bjPayout: 1.5, peek: true }, player: [], up: null, toDealer: false, useCounter: true, rulesOpen: false },
    seen: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    history: [],
  };
}
let S;
try { S = Object.assign(defaults(), JSON.parse(localStorage.getItem(KEY) || 'null') || {}); } catch (e) { S = defaults(); }
function save() { try { localStorage.setItem(KEY, JSON.stringify(S)); } catch (e) { /* private mode */ } }

const main = document.getElementById('main');
const P = (x, d = 1) => (x * 100).toFixed(d) + '%';
const evText = ev => (ev >= 0 ? '+' : '−') + Math.abs(ev * 100).toFixed(1) + '%';
const fmtInt = n => n.toLocaleString('ru-RU');
const bar = (label, frac, val, color) =>
  `<div class="bar"><span>${label}</span><i><b style="width:${Math.max(0, Math.min(1, frac)) * 100}%;background:${color}"></b></i><em>${val}</em></div>`;
const kv = (k, v, cls = '') => `<div class="kv"><span>${k}</span><span class="${cls}">${v}</span></div>`;
const sw = (act, label, on) => `<button class="switch ${on ? 'on' : ''}" data-act="${act}"><span>${label}</span><i></i></button>`;
const C = { good: 'var(--good)', bad: 'var(--bad)', gold: 'var(--gold)' };

/* ================= Poker ================= */
const worker = new Worker('worker.js');
let pokerResult = null, computing = false, reqId = 0, debounce = null;
worker.onmessage = (e) => {
  if (e.data.id !== reqId) return;
  pokerResult = e.data.r; computing = false;
  if (S.tab === 0) renderPokerResults();
};
function pokerInputs() {
  const s = S.poker.slots;
  const board = s.slice(2).filter(c => c !== null);
  return { hero: [s[0], s[1]], board, ok: s[0] !== null && s[1] !== null && !(board.length >= 1 && board.length <= 2) };
}
function schedulePoker() {
  clearTimeout(debounce);
  const inp = pokerInputs();
  reqId++;
  if (!inp.ok) { pokerResult = null; computing = false; return; }
  computing = true;
  const id = reqId;
  debounce = setTimeout(() => worker.postMessage({ id, hero: inp.hero, board: inp.board, opps: S.poker.opps, range: S.poker.range }), 120);
}
function usedElsewhere(c) { const p = S.poker; return p.slots.some((x, i) => i !== p.sel && x === c); }
function place(c) {
  const p = S.poker;
  p.slots[p.sel] = c; p.pr = null; p.ps = null;
  for (let k = 1; k <= 7; k++) { const i = (p.sel + k) % 7; if (p.slots[i] === null) { p.sel = i; break; } }
  schedulePoker();
}
function slotHtml(i) {
  const p = S.poker, c = p.slots[i], sel = p.sel === i ? ' sel' : '';
  if (c === null) return `<button class="slot empty${sel}" data-act="slot" data-v="${i}"></button>`;
  const s = suitOf(c);
  return `<button class="slot full${sel} c${s}" data-act="slot" data-v="${i}">${rankLabel(rankOf(c))}<span class="s">${SUITS[s]}</span></button>`;
}
function renderPoker() {
  const p = S.poker;
  const rankEnabled = r => (p.ps !== null ? !usedElsewhere(mkCard(r, p.ps)) : [0, 1, 2, 3].some(s => !usedElsewhere(mkCard(r, s))));
  const suitEnabled = s => (p.pr !== null ? !usedElsewhere(mkCard(p.pr, s)) : true);
  const rc = p.ps !== null ? ` c${p.ps}` : '';
  const rb = r => `<button class="btn${rc}${p.pr === r ? ' on' : ''}" data-act="rank" data-v="${r}" ${rankEnabled(r) ? '' : 'disabled'}>${rankLabel(r)}</button>`;
  main.innerHTML = `
  <section class="panel">
    <div class="slots">
      <div class="hdr" style="grid-column:1/3">Твои карты</div><div></div>
      <div class="hdr" style="grid-column:4/10">Стол: флоп · тёрн · ривер</div>
      ${slotHtml(0)}${slotHtml(1)}<div></div>${slotHtml(2)}${slotHtml(3)}${slotHtml(4)}<div></div>${slotHtml(5)}${slotHtml(6)}
    </div>
    <div class="hint" style="margin-top:10px">1) ранг 2) масть — в любом порядке. Тап по выбранной карте — убрать её.</div>
    <div class="grid g7">${[12, 11, 10, 9, 8, 7, 6].map(rb).join('')}</div>
    <div class="grid g7">${[5, 4, 3, 2, 1, 0].map(rb).join('')}<button class="btn red" data-act="pdel">⌫</button></div>
    <div class="grid g4" style="margin-top:7px">${[0, 1, 2, 3].map(s => `<button class="btn suit c${s}${p.ps === s ? ' on' : ''}" data-act="suit" data-v="${s}" ${suitEnabled(s) ? '' : 'disabled'}>${SUITS[s]}</button>`).join('')}</div>
    <div class="grid g2" style="margin-top:7px"><button class="btn small" data-act="pnew">Новая раздача</button><button class="btn small" data-act="pboard">Очистить стол</button></div>
  </section>
  <section class="panel">
    <div class="title">Ситуация</div>
    <div class="stepper"><span class="name">Соперников в раздаче</span>
      <button class="btn" data-act="opp" data-v="-1" ${p.opps <= 1 ? 'disabled' : ''}>−</button><span class="val">${p.opps}</span>
      <button class="btn" data-act="opp" data-v="1" ${p.opps >= 9 ? 'disabled' : ''}>+</button></div>
    <div class="lbl" style="margin-top:10px">Какие руки у соперников</div>
    <div class="grid g4">${[[100, 'Любые'], [50, 'Топ 50%'], [30, 'Топ 30%'], [15, 'Топ 15%']].map(([v, l]) => `<button class="btn small${p.range === v ? ' on' : ''}" data-act="range" data-v="${v}">${l}</button>`).join('')}</div>
    <div class="hint">Кто доставляет ставки, обычно держит руки сильнее случайных — сужай диапазон против тайтовых игроков.</div>
    <div class="inputs">
      <label>Банк сейчас<input id="pot" inputmode="decimal" value="${p.pot}" placeholder="0"></label>
      <label>Нужно доставить<input id="call" inputmode="decimal" value="${p.call}" placeholder="0"></label>
    </div>
    <div class="hint">Банк — вместе со ставкой соперника.</div>
  </section>
  <div id="pres"></div>`;
  for (const id of ['pot', 'call']) {
    document.getElementById(id).addEventListener('input', (e) => {
      const v = e.target.value.replace(/[^0-9.,]/g, '');
      if (v !== e.target.value) e.target.value = v;
      S.poker[id] = v; save(); renderPokerResults();
    });
  }
  renderPokerResults();
}
function renderPokerResults() {
  const el = document.getElementById('pres');
  if (!el) return;
  const p = S.poker, inp = pokerInputs(), r = pokerResult;
  const nb = inp.board.length;
  if (p.slots[0] === null || p.slots[1] === null) { el.innerHTML = `<section class="panel dimc">Введи свои две карты — расчёт запустится сам.</section>`; return; }
  if (nb >= 1 && nb <= 2) { el.innerHTML = `<section class="panel dimc">Флоп — это сразу 3 карты. Добавь остальные.</section>`; return; }
  if (!r) { el.innerHTML = `<section class="panel dimc"><span class="spin"></span> Считаю…</section>`; return; }
  const e = r.equity;
  const num = s => parseFloat(String(s).replace(',', '.')) || 0;
  const d = pokerDecision(e.equity, p.opps, num(p.pot), num(p.call));
  let h = `<section class="panel"><div class="title">Шансы ${computing ? '<span class="spin"></span>' : ''}</div>
    <div class="big">${P(e.equity)}</div><div class="hint" style="margin:0 0 8px">эквити — твоя доля банка в среднем</div>
    ${bar('Выигрыш', e.win, P(e.win), C.good)}${bar('Делёж', e.tie, P(e.tie), C.gold)}${bar('Проигрыш', e.lose, P(e.lose), C.bad)}
    <div class="hint">${e.exact ? `Точный перебор всех вариантов: ${fmtInt(e.samples)}` : `Монте-Карло: ${fmtInt(e.samples)} раздач, погрешность ±${P(e.stdErr * 1.96, 2)} (95%)`}</div>
    ${r.handNow ? kv('Сейчас у тебя', r.handNow, 'mid') : ''}
    ${r.preflopClass ? kv('Стартовая рука', r.preflopClass) + kv('Место среди 169 рук', `топ ${r.preflopTop.toFixed(1)}%`) : ''}
  </section>
  <section class="panel"><div class="title">Решение</div>
    <div class="verdict ${d.tone}">${d.verdict}</div><ul class="lines">${d.lines.map(l => `<li>${l}</li>`).join('')}</ul></section>`;
  if (r.outsHit !== null) {
    h += `<section class="panel"><div class="title">Ауты (улучшающие карты)</div>
      ${kv('Аутов', `${r.outs.length} из ${r.unseen}`)}${kv(nb === 3 ? 'Поймать к риверу' : 'Поймать на ривере', P(r.outsHit), 'mid')}
      <div class="chips">${r.outs.map(c => `<span class="chip c${suitOf(c)}">${cardLabel(c)}</span>`).join('')}</div>
      <div class="hint">Аут — карта, повышающая твою комбинацию на уровень выше доски. Эквити выше уже учитывает всё точно.</div></section>`;
  }
  if (nb < 5) {
    h += `<section class="panel"><div class="title">Чем закончишь к риверу</div>`;
    for (let c = 8; c >= 0; c--) if (e.finalCat[c] >= 0.0005) h += bar(CAT_NAMES[c], e.finalCat[c], P(e.finalCat[c]), C.gold);
    h += `</section>`;
  }
  el.innerHTML = h;
}

/* ================= Blackjack ================= */
const remaining = v => S.bj.rules.decks * BJ_STD[v - 1] - S.seen[v - 1];
const cardsLeft = () => { let n = 0; for (let v = 1; v <= 10; v++) n += Math.max(0, remaining(v)); return n; };
function addSeen(v) { S.seen[v - 1]++; S.history.push(v); }
function bjAddCard(v) {
  const b = S.bj;
  if (b.toDealer || (b.player.length >= 2 && b.up === null)) { b.up = v; b.toDealer = false; } else if (b.player.length < 8) b.player.push(v);
}
function valuePad(act, sub) {
  return [[1, 2, 3, 4, 5], [6, 7, 8, 9, 10]].map(row => `<div class="grid g5">${row.map(v =>
    `<button class="btn" data-act="${act}" data-v="${v}" style="height:50px;${v === 10 ? 'font-size:14px' : 'font-size:21px'}">${v === 10 ? '10·J·Q·K' : bjLabel(v)}${sub ? `<small>${sub(v)}</small>` : ''}</button>`).join('')}</div>`).join('');
}
function bjCompute() {
  const b = S.bj, r = b.rules;
  if (b.player.length < 2 || b.up === null) return null;
  const fresh = freshShoe(r.decks), counter = BJ_STD.map((x, i) => r.decks * x - S.seen[i]);
  for (const v of b.player.concat([b.up])) { fresh[v - 1]--; counter[v - 1]--; }
  if (fresh.some(x => x < 0)) return { error: `Столько таких карт нет в ${r.decks}-колодном шузе.` };
  const bad = counter.some(x => x < 0), use = b.useCounter && S.history.length > 0;
  const basic = analyzeBj(b.player, b.up, fresh, r);
  const counted = use && !bad ? analyzeBj(b.player, b.up, counter, r) : null;
  return { basic, counted, error: use && bad ? 'В счётчике уже выбыли все такие карты — проверь ввод. Показываю расчёт без счётчика.' : null };
}
function renderBj() {
  const b = S.bj, r = b.rules;
  const hard = b.player.reduce((a, x) => a + x, 0), soft = b.player.includes(1) && hard + 10 <= 21;
  const dealerSel = b.toDealer || (b.player.length >= 2 && b.up === null);
  let h = `<section class="panel">
    <button class="rulesHead" data-act="rulesOpen"><span class="title">Правила стола</span>
      <span class="dimc" style="font-size:13px">${r.decks}к · ${r.h17 ? 'H17' : 'S17'} · ${r.bjPayout > 1.4 ? '3:2' : '6:5'} ${b.rulesOpen ? '▲' : '▼'}</span></button>`;
  if (b.rulesOpen) {
    h += `<div class="lbl" style="margin-top:10px">Колод в шузе</div>
      <div class="grid g5">${[1, 2, 4, 6, 8].map(d => `<button class="btn small${r.decks === d ? ' on' : ''}" data-act="decks" data-v="${d}">${d}</button>`).join('')}</div>
      ${sw('r_h17', 'Дилер берёт на мягких 17 (H17)', r.h17)}${sw('r_das', 'Удвоение после сплита (DAS)', r.das)}
      ${sw('r_surrender', 'Можно сдаться (surrender)', r.surrender)}${sw('r_bj', 'Блэкджек платит 3:2 (выкл = 6:5)', r.bjPayout > 1.4)}
      ${sw('r_peek', 'Дилер проверяет блэкджек (peek)', r.peek)}`;
  }
  h += `</section><section class="panel">
    <div class="hand">
      <div><div class="lbl">Дилер</div><button class="bjc${b.up !== null ? ' full' : ''}${dealerSel ? ' sel' : ''}" data-act="bjdealer">${b.up !== null ? bjLabel(b.up) : dealerSel ? '▼' : ''}</button></div>
      <div style="flex:1"><div class="lbl">Ты${b.player.length ? ' · ' + (soft ? 'мягкие ' + (hard + 10) : hard) : ''}</div>
        <div class="row">${b.player.map((v, i) => `<button class="bjc full" data-act="bjrm" data-v="${i}">${bjLabel(v)}</button>`).join('')}
        ${b.player.length < 8 ? `<button class="bjc${!dealerSel ? ' sel' : ''}" data-act="bjme">${!dealerSel ? '▼' : ''}</button>` : ''}</div></div>
    </div>
    <div class="hint" style="margin:6px 0">Карты идут: 2 тебе → 1 дилеру → дальше тебе. Тап по карте — удалить, тап по слоту дилера — направить карту ему.</div>
    ${valuePad('bjv')}
    <div class="grid" style="grid-template-columns:1fr 1.4fr;margin-top:7px"><button class="btn small" data-act="bjclear">Очистить руку</button><button class="btn small gold" data-act="bjcommit">Раунд сыгран → в счётчик</button></div>
    ${sw('usecounter', `Учитывать выбывшие карты из счётчика (${S.history.length})`, b.useCounter)}
  </section>`;
  const res = bjCompute();
  if (!res) h += `<section class="panel dimc">Введи свои 2 карты и открытую карту дилера.</section>`;
  else {
    if (res.error) h += `<section class="panel bad">${res.error}</section>`;
    if (res.basic) h += bjResults(res.basic, res.counted);
  }
  main.innerHTML = h;
}
function dealerSection(a, up) {
  let h = `<section class="panel"><div class="title">Дилер с картой ${bjLabel(up)}: чем закончит</div>`;
  if (a.pDealerBj > 0) h += kv('Блэкджек у дилера (до проверки)', P(a.pDealerBj));
  h += bar('Перебор', a.dealer[5], P(a.dealer[5]), C.good);
  for (let i = 0; i < 5; i++) h += bar(String(17 + i), a.dealer[i], P(a.dealer[i]), C.bad);
  if (a.pDealerBj > 0) h += `<div class="hint">Распределение — при условии, что блэкджека у дилера нет.</div>`;
  return h + `</section>`;
}
function bjResults(basic, counted) {
  const m = counted || basic, up = S.bj.up;
  if (m.isBj) {
    let h = `<section class="panel"><div class="title">Блэкджек!</div>${kv('Ожидаемый выигрыш', evText(m.bjEv), 'good')}`;
    if (up === 1) h += kv('Вероятность BJ у дилера', P(m.p10)) + `<div class="warn"><b>${1 > m.bjEv ? 'Бери «even money» (+100% гарантированно)' : 'Откажись от even money — EV выше без него'}</b></div>`;
    return h + `</section>` + dealerSection(m, up);
  }
  const maxEv = Math.max(...Object.values(m.evs));
  let h = `<section class="panel"><div class="title">Лучшее действие</div>
    <div class="verdict good" style="font-size:32px">${BJ_ACTIONS[m.best].toUpperCase()}</div>
    <div>EV ${evText(m.evs[m.best])} от ставки</div>`;
  if (counted && basic.best !== counted.best) {
    h += `<div class="warn">⚠ Отклонение по счёту: по базовой стратегии — «${BJ_ACTIONS[basic.best]}», но с учётом вышедших карт выгоднее «${BJ_ACTIONS[counted.best]}».</div>`;
  }
  h += `<div style="margin-top:8px">`;
  for (const a of Object.keys(BJ_ACTIONS)) {
    if (!(a in m.evs)) continue;
    const ev = m.evs[a];
    h += `<div class="evrow"><span class="n ${a === m.best ? 'good' : ''}" style="${a === m.best ? 'font-weight:700' : ''}">${BJ_ACTIONS[a]}</span>
      <b class="${ev >= 0 ? 'good' : 'bad'}">${evText(ev)}</b><span class="d">${a === m.best ? '' : '(' + evText(ev - maxEv) + ')'}</span></div>`;
    if (counted && a in basic.evs) h += `<div class="evsub">без счётчика: ${evText(basic.evs[a])}</div>`;
  }
  h += `</div>${kv('Перебор, если взять карту', P(m.bust), m.bust > 0.5 ? 'bad' : '')}
    <div class="hint">EV в % от исходной ставки. Расчёт composition-dependent: учитывает реальный состав шуза${counted ? ' с учётом счётчика' : ''}; сплит — без пересплита.</div></section>`;
  if (up === 1) {
    const ins = m.insurance;
    h += `<section class="panel"><div class="title">Страховка</div>${kv('Шанс, что у дилера десятка', P(m.p10))}${kv('EV страховки', evText(ins), ins > 0 ? 'good' : 'bad')}
      <div class="${ins > 0 ? 'good' : ''}" style="font-weight:700;margin-top:4px">${ins > 0 ? 'Страхуйся: доля десяток в шузе выше 1/3.' : 'Не страхуйся: нужна доля десяток > 33.3%.'}</div></section>`;
  }
  return h + dealerSection(m, up);
}

/* ================= Counter ================= */
function renderCounter() {
  const r = S.bj.rules, st = countStatus(S.seen, r), left = cardsLeft(), total = r.decks * 52;
  const sg = (x, d) => (x > 0 ? '+' : x < 0 ? '−' : '') + Math.abs(x).toFixed(d);
  let h = `<section class="panel">
    <div class="count2"><div><div class="lbl">Текущий счёт</div><div class="v">${sg(st.rc, 0)}</div></div>
      <div><div class="lbl">Истинный счёт</div><div class="v ${st.tc >= 1 ? 'good' : st.tc <= -1 ? 'bad' : 'mid'}">${sg(st.tc, 1)}</div></div></div>
    ${kv('Осталось карт', `${left} из ${total} (${st.decksLeft.toFixed(1)} колод)`)}
    ${kv('Пройдено шуза', P(1 - left / total, 0))}
    ${kv('Твой перевес (оценка)', (st.edge >= 0 ? '+' : '') + st.edge.toFixed(2) + '%', st.edge > 0 ? 'good' : 'bad')}
    ${kv('Ставка', st.units + ' ед.', 'mid')}
    <div style="font-size:13.5px;margin-top:4px">${st.advice}</div></section>
  <section class="panel"><div class="title">Отметь вышедшую карту</div>
    ${valuePad('seen', v => { const t = hiLo(v); return (t > 0 ? '+1' : t < 0 ? '−1' : '0') + ' · ост. ' + Math.max(0, remaining(v)); })}
    <div class="grid g2" style="margin-top:7px"><button class="btn small" data-act="undo" ${S.history.length ? '' : 'disabled'}>↶ Отменить</button><button class="btn small red" data-act="newshoe">Новый шуз</button></div>
    ${S.history.length ? `<div class="hint" style="margin-top:6px">Последние: ${S.history.slice(-14).map(bjLabel).join(' ')}</div>` : ''}
  </section>
  <section class="panel"><div class="title">Шанс следующей карты</div>`;
  for (const v of [10, 1, 9, 8, 7, 6, 5, 4, 3, 2]) {
    const p = left > 0 ? Math.max(0, remaining(v)) / left : 0, base = BJ_STD[v - 1] / 52;
    h += bar(v === 10 ? '10-ки' : bjLabel(v), p / 0.4, P(p), p > base * 1.03 ? C.good : p < base * 0.97 ? C.bad : C.gold);
  }
  h += `<div class="hint">Зелёный — карт больше нормы, красный — меньше. Много тузов и десяток = выгодно игроку.</div></section>
  <section class="panel"><div class="title">Как пользоваться</div><div style="font-size:13.5px">
    Hi-Lo: 2–6 = +1, 7–9 = 0, 10/картинки/туз = −1. Отмечай все открытые карты на столе (свои, чужие, дилера).
    Истинный счёт = текущий ÷ оставшиеся колоды; каждый +1 даёт игроку ≈ +0.5% перевеса.
    Вкладка «Блэкджек» использует этот шуз для точного расчёта решений.</div></section>
  <p class="foot">Odds Lab · для обучения и разбора раздач</p>`;
  main.innerHTML = h;
}

/* ================= Shell ================= */
function render() {
  document.querySelectorAll('#nav button').forEach((b, i) => b.classList.toggle('on', i === S.tab));
  if (S.tab === 0) renderPoker(); else if (S.tab === 1) renderBj(); else renderCounter();
}
document.addEventListener('click', (e) => {
  const t = e.target.closest('[data-act]');
  if (!t || t.disabled) return;
  const act = t.dataset.act, v = t.dataset.v !== undefined ? Number(t.dataset.v) : null;
  const p = S.poker, b = S.bj;
  switch (act) {
    case 'tab': S.tab = v; window.scrollTo(0, 0); break;
    case 'slot':
      if (p.sel === v && p.slots[v] !== null) { p.slots[v] = null; schedulePoker(); } else p.sel = v;
      p.pr = null; p.ps = null; break;
    case 'rank': if (p.ps !== null) place(mkCard(v, p.ps)); else p.pr = p.pr === v ? null : v; break;
    case 'suit': if (p.pr !== null) place(mkCard(p.pr, v)); else p.ps = p.ps === v ? null : v; break;
    case 'pdel': p.slots[p.sel] = null; p.pr = null; p.ps = null; schedulePoker(); break;
    case 'pnew': p.slots = [null, null, null, null, null, null, null]; p.sel = 0; p.pr = null; p.ps = null; schedulePoker(); break;
    case 'pboard': for (let i = 2; i < 7; i++) p.slots[i] = null; p.sel = 2; schedulePoker(); break;
    case 'opp': p.opps = Math.max(1, Math.min(9, p.opps + v)); schedulePoker(); break;
    case 'range': p.range = v; schedulePoker(); break;
    case 'rulesOpen': b.rulesOpen = !b.rulesOpen; break;
    case 'decks': b.rules.decks = v; break;
    case 'r_h17': b.rules.h17 = !b.rules.h17; break;
    case 'r_das': b.rules.das = !b.rules.das; break;
    case 'r_surrender': b.rules.surrender = !b.rules.surrender; break;
    case 'r_bj': b.rules.bjPayout = b.rules.bjPayout > 1.4 ? 1.2 : 1.5; break;
    case 'r_peek': b.rules.peek = !b.rules.peek; break;
    case 'bjv': bjAddCard(v); break;
    case 'bjdealer': if (b.up !== null) b.up = null; else b.toDealer = !b.toDealer; break;
    case 'bjme': b.toDealer = false; break;
    case 'bjrm': b.player.splice(v, 1); break;
    case 'bjclear': b.player = []; b.up = null; b.toDealer = false; break;
    case 'bjcommit': b.player.forEach(addSeen); if (b.up !== null) addSeen(b.up); b.player = []; b.up = null; b.toDealer = false; break;
    case 'usecounter': b.useCounter = !b.useCounter; break;
    case 'seen': addSeen(v); break;
    case 'undo': { const x = S.history.pop(); if (x) S.seen[x - 1] = Math.max(0, S.seen[x - 1] - 1); break; }
    case 'newshoe': S.seen = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]; S.history = []; break;
    default: return;
  }
  save();
  const y = window.scrollY;
  render();
  if (act !== 'tab') window.scrollTo(0, y);
});

schedulePoker();
render();
if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
