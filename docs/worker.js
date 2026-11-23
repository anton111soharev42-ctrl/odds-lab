'use strict';
importScripts('engines.js');

let current = 0;
// Warm up the preflop ranking so the first range calculation is instant.
setTimeout(() => preflopTable(), 0);

onmessage = async (e) => {
  const { id, hero, board, opps, range } = e.data;
  current = id;
  const r = await analyzePoker(hero, board, opps, range, () => current !== id);
  if (r && current === id) postMessage({ id, r });
};
