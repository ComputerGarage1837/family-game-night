'use strict';
/* Spear soldier, hand-placed pixels. Parts are drawn without their outer outline; the composer adds a
   1px dark outline around the whole silhouette, like the reference. */
const PAL = {
  h: '#c9d1dc', H: '#eef2f7', d: '#8a94a6', D: '#5c6476',            // steel helmet
  S: '#f4c8a0', s: '#dca078', t: '#b26e50', k: '#2a1c1a', w: '#ffffff', // skin, eyes
  B: '#4f7fd6', b: '#3a62b8', v: '#27418a', Y: '#f2cc58', y: '#c8962e', // tunic + emblem
  L: '#7a4e2c', l: '#55341c', G: '#d8b050',                             // belt, buckle
  P: '#6a5646', p: '#4a3a2e', K: '#5a3a24', q: '#3a2416',             // trousers, boots
  n: '#7a4a28', N: '#5a3218',                                          // hair
  W: '#b88450', V: '#8a5a32', T: '#e8eef6', U: '#a8b2c2',              // spear shaft + tip
  O: '#2a1c1a',
};
const HEAD = [
  '.....hhhhhh.....',
  '...hhHHHHhhhh...',
  '..hHHHHHhhhhhd..',
  '.hHHHHhhhhhhhdd.',
  '.hhHhhhhhhhhhdd.',
  'hhhhhhhhhhhhhddd',
  'DddddddddddddDDD',
  '.nSSSSSSSSSSSsn.',
  '.SSSSSSSSSSSSss.',
  '.SSkkSSSSSkkSss.',
  '.SSkwSSSSSkwSss.',
  '.SSSSSSSSSSSSss.',
  '.sSSSSSttSSSsss.',
  '..ssSSSSSSSsss..',
  '...sssssssss....',
];
const TORSO = [
  '...bBBBBBBBbb...',
  '..BBBBBBBBBBbv..',
  '.BBBBBBYBBBBbbv.',
  '.BBBBBYyYBBBbbv.',
  '.BBBBBBYBBBBbbv.',
  '.BBBBBBBBBBbbbv.',
  '.LLLLLLGGLLLLll.',
  '.BBBBBBBBBBBbbv.',
  '.BBBBBBvBBBBbbv.',
  '..BBBBv.vBBbbv..',
];
const ARM_BACK = ['.bb.', 'bBBb', 'bBBb', 'bBbv', '.vv.', 'sSSs', 'SSSs', 'ssss'];
const ARM_FRONT = ['.BB.', 'BBBb', 'BBBb', 'BBbv', '.vv.', 'SSSs', 'SSSS', 'sSSs', '.ss.'];
const LEGS = {
  stand: ['.PPPp..PPPp.', '.PPPp..PPpp.', '.PPpp..PPpp.', '.PPpp..PPpp.', '.KKKq..KKKq.', 'KKKKq.KKKKq.'],
  walkA: ['.PPPp..PPPp.', '.PPPp...PPp.', 'PPPp....PPp.', 'PPpp....PPp.', 'KKKq....KKq.', 'KKKKq..KKKq.'],
  walkB: ['..PPPpPPPp..', '..PPp.PPpp..', '..PPp.PPp...', '..PPp.PPp...', '..KKq.KKq...', '.KKKq.KKKq..'],
  lunge: ['.PPPp..PPPp.', 'PPPp.....PPp', 'PPp......PPp', 'PPp.......PP', 'KKq......KKq', 'KKKq....KKKq'],
  kneel: ['.PPPPPPPPPp.', 'KKKqPPPPpp..', 'KKKKq.KKKq..'],
};
if (typeof module !== 'undefined') module.exports = { PAL, HEAD, TORSO, ARM_BACK, ARM_FRONT, LEGS };
// Front arm variants and where the fist centre sits inside each grid.
const ARMS = {
  down: { g: ['.BB.', 'BBBb', 'BBBb', 'BBbv', '.vv.', 'SSSs', 'SSSS', 'sSSs', '.ss.'], hand: [1.5, 6.5], at: [13, 1] },
  fwd:  { g: ['.BBb.SSs.', 'BBBbSSSSs', 'bBbvSSSSs', '.vv..sss.'], hand: [6, 1.5], at: [12, 2] },
  up:   { g: ['.ss.', 'sSSs', 'SSSS', 'SSSs', '.vv.', 'BBbv', 'BBBb', 'BBBb', '.BB.'], hand: [1.5, 2], at: [13, -7] },
};
// Spear tips, drawn pointing up / up-right / right; anchor = where the shaft meets the tip.
const TIPS = {
  up:   { g: ['..T..', '.TTU.', '.TTU.', 'TTTUU', '.TTU.', '..U..', '.GGG.'], a: [2, 6], d: [0, -1] },
  diag: { g: ['....TTT', '...TTTT', '..TTTTU', '..TTTU.', '.GTUU..', 'GG.U...', 'G......'], a: [0, 6], d: [1, -1] },
  fwd:  { g: ['...T...', '.G.TT..', '.GTTTTT', '.GTUUUU', '.G.UU..', '...U...'], a: [1, 3], d: [1, 0] },
};
if (typeof module !== 'undefined') Object.assign(module.exports, { ARMS, TIPS });
