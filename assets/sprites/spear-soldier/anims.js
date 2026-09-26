'use strict';
/* Animations: each frame is a pose plus how long it holds (ms). */
const I = { reach: 20 };
const F = +(typeof process !== 'undefined' && process.env && process.env.FALL || 9);
const ANIM = {
  idle: [
    { ...I, ms: 220 }, { ...I, ms: 220, bob: 1 }, { ...I, ms: 220, bob: 1, blink: 1 }, { ...I, ms: 220 },
  ],
  walk: [
    { ...I, legs: 'walkA', bob: -1, ms: 130 }, { ...I, legs: 'stand', ms: 110 },
    { ...I, legs: 'walkB', bob: -1, ms: 130 }, { ...I, legs: 'stand', ms: 110 },
  ],
  attack: [
    { ...I, x: -1, headX: -1, backX: -1, bob: 1, ms: 120 },
    { arm: 'fwd', spear: 'fwd', armX: -5, reach: 14, x: -2, headX: -1, bob: 1, ms: 140 },
    { arm: 'fwd', spear: 'fwd', legs: 'lunge', reach: 15, x: 3, headX: 1, ms: 70 },
    { arm: 'fwd', spear: 'fwd', legs: 'lunge', armX: 1, reach: 17, x: 4, headX: 1, ms: 180, hit: true },
    { arm: 'fwd', spear: 'fwd', armX: -2, reach: 15, x: 1, ms: 120 },
    { ...I, ms: 120 },
  ],
  hurt: [
    { ...I, flash: 0.85, ouch: 1, x: -2, headX: -1, ms: 70 },
    { ...I, ouch: 1, x: -2, headX: -1, spear: 'diag', reach: 14, ms: 220 },
    { ...I, x: -1, ms: 120 },
  ],
  death: [
    { ...I, flash: 0.85, ouch: 1, x: -2, headX: -1, ms: 70 },
    { ouch: 1, legs: 'kneel', y: 0, bob: 4, headY: 1, spear: 'diag', reach: 14, x: -2, ms: 260 },
    { ouch: 1, noSpear: 1, x: -2, rot: { cx: 30, cy: 41, dy: -F }, ms: 520 },
    { ouch: 1, noSpear: 1, x: -2, rot: { cx: 30, cy: 41, dy: -F }, alpha: 0.5, ms: 160 },
    { ouch: 1, noSpear: 1, x: -2, rot: { cx: 30, cy: 41, dy: -F }, alpha: 0, ms: 400 },
  ],
  victory: [
    { arm: 'up', spear: 'up', reach: 12, bob: 0, ms: 200 }, { arm: 'up', spear: 'up', reach: 12, bob: -2, legY: 0, ms: 160 },
    { arm: 'up', spear: 'up', reach: 12, bob: 0, ms: 200 }, { arm: 'up', spear: 'up', reach: 12, bob: -1, ms: 160 },
  ],
};
const RED = { B: '#d85a4e', b: '#b23c38', v: '#7c2426', Y: '#f2d27a', y: '#c89a3a', h: '#9aa0aa', H: '#cdd2d9', d: '#6c7280', D: '#4a4e5a' };
if (typeof module !== 'undefined') module.exports = { ANIM, RED };
