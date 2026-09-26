'use strict';
/* Chibi spear soldier, proportioned after the lumberjack reference: head about a third of the height,
   broad V-shaped torso, oversized fists, short thick legs, chunky boots. Built from shaded shapes so every
   pose can animate; face details are placed by hand. Frame 88 x 92, feet on y = 86, units = pixels. */
if (typeof module !== 'undefined') Object.assign(globalThis, require('./engine_fe.js'));
const CW = 164, CH = 112, OX = 28, OY = 18;

const C_IDLE = {
  x: 0, bob: 0, lean: 0, headTilt: 0,
  footL: [33, 86], footR: [46, 86],          // back foot, front foot
  spearArm: 'hold', spearX: 58, spearY: 0,   // 'hold' (upright at side) | 'thrust' | 'raise'
  offFist: [21, 57], blink: 0, ouch: 0,
};

function buildChibi(q) {
  const parts = [], push = (o) => (parts.push(o), o);
  const cap = (a, b, ra, rb, mat, z, g, x) => push(Object.assign({ type: 'cap', a, b, ra, rb, mat, z, g }, x));
  const ell = (c, rx, ry, r, mat, z, g, x) => push(Object.assign({ type: 'ell', c, rx, ry, rot: r, mat, z, g }, x));
  const poly = (pts, mat, z, g, x) => push(Object.assign({ type: 'poly', pts, mat, z, g }, x));
  const X = q.x, B = q.bob, lean = q.lean;
  const rot0 = q.rot || 0, rc = Math.cos(rot0), rs = Math.sin(rot0), RC = [40 + X, 86];
  const Rt = (p) => { const r = rot0 ? [RC[0] + (p[0] - RC[0]) * rc - (p[1] - RC[1]) * rs, RC[1] + (p[0] - RC[0]) * rs + (p[1] - RC[1]) * rc + (q.dropY || 0)] : p; return [r[0] + OX + (q.fallX || 0), r[1] + OY]; };
  const P = (x, y) => Rt([x + X + (86 - y) * lean * 0.012, y + B * (y < 66 ? 1 : 0)]);   // body space → frame, leaning from the feet

  // ---- boots and legs (don't bob) ----
  const hipL = P(34, 62), hipR = P(45, 62);
  const leg = (hip, foot, z, g) => {
    const knee = [(hip[0] + foot[0]) / 2, (hip[1] + foot[1]) / 2 - 1];
    cap(hip, [foot[0], foot[1] - 8], 5, 4.4, 'leather2', z, g);
    cap([foot[0], foot[1] - 10], [foot[0], foot[1] - 4], 4.8, 5.2, 'leather', z + 0.1, g + 'b');
    ell([foot[0] + 1.8, foot[1] - 2.8], 6.4, 3.6, 0, 'leather', z + 0.2, g + 'b');
    ell([foot[0] + 0.5, foot[1] - 10], 5.2, 1.4, 0, 'leather', z + 0.25, g + 'c');   // boot cuff
    return knee;
  };
  leg(hipL, Rt([q.footL[0] + X, q.footL[1]]), 1, 'legL');
  leg(hipR, Rt([q.footR[0] + X, q.footR[1]]), 1.5, 'legR');

  // ---- spear (behind the body when held upright) ----
  const spearParts = () => {
    let a, len = 60, hand;
    const ra = rot0;
    if (q.spearArm === 'thrust') { hand = P(q.spearX, 54 + q.spearY); a = 0; }
    else if (q.spearArm === 'raise') { hand = P(62, 40 + q.spearY); a = -Math.PI / 2; }
    else { hand = P(q.spearX, 56 + q.spearY); a = -Math.PI / 2; }
    a += ra; const d = [Math.cos(a), Math.sin(a)], n = [-d[1], d[0]];
    const tail = [hand[0] - d[0] * 20, hand[1] - d[1] * 20], head = [hand[0] + d[0] * (len - 20), hand[1] + d[1] * (len - 20)];
    const zS = q.spearArm === 'thrust' ? 9 : 2.5;
    cap(tail, head, 1.6, 1.6, 'woodC', zS, 'shaft', { flat: 0.2 });
    ell(head, 2.3, 2.3, 0, 'steel', zS + 0.1, 'collar');
    const tip = (u, w) => [head[0] + d[0] * u + n[0] * w, head[1] + d[1] * u + n[1] * w];
    poly([tip(0, 0), tip(5, 4.4), tip(12, 3.2), tip(19, 0), tip(12, -3.2), tip(5, -4.4)], 'steel', zS + 0.2, 'tip', { bevel: 1.6, n: [0.2, -0.3, 1] });
    return hand;
  };
  const hand = spearParts();

  // ---- back arm (viewer left) with its big fist ----
  const shL = P(25, 43);
  const fL = Rt([q.offFist[0] + X, q.offFist[1] + B]);
  cap(shL, fL, 4.6, 4.2, 'cloth', 3, 'armL');
  ell([fL[0], fL[1] + 3], 5.6, 6.2, 0, 'skin', 3.2, 'fistL');

  // ---- torso: V-shaped padded tunic, leather cross-strap, belt, skirt ----
  poly([P(23, 41), P(31, 36), P(47, 36), P(55, 41), P(53, 51), P(49, 61), P(29, 61), P(25, 51)], 'cloth', 5, 'torso',
    { bevel: 5, bevelK: 1.1, n: [0.15, -0.05, 1] });
  // quilting seams
  for (const sx of [33, 39, 45]) cap(P(sx, 39), P(sx + (sx - 39) * 0.2, 59), 0.55, 0.55, 'cloth', 5.05, 'seam', { bright: -0.18, noCast: true, noLine: true });
  cap(P(26, 41), P(51, 60), 1.9, 1.9, 'leather', 5.3, 'strap', { flat: 0.3 });
  ell(P(38.5, 50.5), 1.8, 1.8, 0, 'trim', 5.35, 'stud');
  poly([P(29, 61), P(49, 61), P(51, 68), P(27, 68)], 'cloth', 4.8, 'skirt', { bevel: 2.2, n: [0.1, 0.2, 1] });
  cap(P(28, 61), P(50, 61), 2.6, 2.6, 'leather', 5.5, 'belt', { flat: 0.35 });
  poly([P(36, 58.8), P(41.5, 58.8), P(41.5, 63.2), P(36, 63.2)], 'steel', 5.6, 'buckle', { bevel: 1.1 });
  // shoulder guards
  ell(P(25, 41.5), 6.2, 5, -0.25, 'steel', 5.8, 'paulL', { edgeW: 0.7, edgeMat: 'trim' });
  ell(P(53, 41.5), 6.2, 5, 0.25, 'steel', 6.2, 'paulR', { edgeW: 0.7, edgeMat: 'trim' });

  // ---- front arm (viewer right) holding the spear ----
  const shR = P(53, 43);
  cap(shR, [hand[0] - (q.spearArm === 'thrust' ? 5 : 0), hand[1] - 4], 4.6, 4.2, 'cloth', q.spearArm === 'thrust' ? 8.5 : 7, 'armR');
  ell([hand[0], hand[1]], 5.8, 6.2, 0, 'skin', q.spearArm === 'thrust' ? 9.5 : 7.5, 'fistR');

  // ---- head: big, with a kettle helmet ----
  const H = P(39 + q.headTilt, 23);
  ell([H[0] - 12.8, H[1] + 3], 2.6, 3.4, 0, 'skin', 7.9, 'ear');
  ell([H[0] + 12.8, H[1] + 3], 2.6, 3.4, 0, 'skin', 7.9, 'ear');
  ell(H, 13.5, 13, 0, 'skin', 8, 'head');
  ell([H[0], H[1] + 8], 11, 6, 0, 'skin', 8.02, 'head');                                  // round jaw
  ell([H[0], H[1] - 7.5], 14.6, 8.4, 0, 'steel', 8.5, 'helm', { edgeW: 0.9, edgeMat: 'steel', noCast: true });
  for (const s of [-1, 1]) cap([H[0] + s * 11.5, H[1] - 1], [H[0] + s * 12.5, H[1] + 5], 2.4, 1.2, 'hairC', 8.4, 'hair', { noCast: true });
  ell([H[0], H[1] - 1], 17.5, 3.3, 0, 'steel', 8.6, 'brim', { edgeW: 1.0, edgeMat: 'trim', noCast: true, noLine: true });
  cap([H[0], H[1] - 15.5], [H[0], H[1] - 2.5], 1.3, 1.3, 'trim', 8.55, 'ridge', { noCast: true });
  return { parts, head: H, hand };
}

// Hand-placed face: big dark eyes with a highlight, brows, nose shadow, small mouth.
function chibiFace(b, q) {
  const [hx, hy] = [Math.round(b.head[0]), Math.round(b.head[1])], ov = [];
  if (q.rot) return ov; // no face pixels when lying down
  const P = (dx, dy, rgb) => ov.push({ x: hx + dx, y: hy + dy, rgb, onlyOn: 1 });
  const K = [32, 22, 24], Wt = [255, 255, 255], BR = [96, 58, 34], N = [214, 150, 112], M = [150, 78, 64];
  for (const ex of [-7, 5]) {
    if (q.blink || q.ouch) { for (let i = 0; i < 3; i++) P(ex + i, 5, K); if (q.ouch) { P(ex, 4, K); P(ex + 2, 6, K); } }
    else { for (let y = 2; y <= 6; y++) for (let x = 0; x < 3; x++) P(ex + x, y, K); P(ex + 1, 3, Wt); }
    for (let x = -1; x < 4; x++) P(ex + x, 0, BR);
  }
  P(0, 8, N); P(-1, 8, N);
  if (q.ouch) { P(-1, 11, K); P(0, 11, K); P(1, 11, K); P(0, 12, K); } else { P(-2, 11, M); P(-1, 12, M); P(0, 12, M); P(1, 12, M); P(2, 11, M); }
  return ov;
}

function renderChibi(q, opts) {
  const b = buildChibi(q);
  const r = renderModel(b.parts, Object.assign({ W: CW, H: CH, scale: 1, faction: 'azure', dither: 0.12 }, opts, { overlays: opts && opts.mode === 'flat' ? null : chibiFace(b, q) }));
  if (q.flash) tint(r.rgba, [255, 255, 255], q.flash);
  return r;
}
if (typeof module !== 'undefined') module.exports = { buildChibi, renderChibi, C_IDLE, CW, CH };
