'use strict';
/* Sprite renderer v2. Characters are built from simple 3D-ish primitives (tapered capsules,
   ellipsoids, bevelled polygons) on a posable skeleton, in "model units". Each frame is:
     1. rasterised with 3x3 sub-samples per output pixel,
     2. lit per sample (diffuse + specular + rim + cast shadows between parts),
     3. resolved to pixels (coverage, majority part/material, averaged light),
     4. snapped to hue-shifted colour ramps, cleaned of stray pixels, given occlusion lines
        and a coloured "selective" outline, then hand-placed face pixels on top.
   No image files are used anywhere. */

// ---------- math ----------
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const lerp = (a, b, t) => a + (b - a) * t;
const add = (a, b) => [a[0] + b[0], a[1] + b[1]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1]];
const mul = (a, k) => [a[0] * k, a[1] * k];
const mix2 = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t)];
const len2 = (a) => Math.hypot(a[0], a[1]);
const unit = (a) => { const l = len2(a) || 1; return [a[0] / l, a[1] / l]; };
const rot = (v, a) => { const c = Math.cos(a), s = Math.sin(a); return [v[0] * c - v[1] * s, v[0] * s + v[1] * c]; };
const T = (o, a, v) => add(o, rot(v, a));
const dirOf = (a) => [Math.cos(a), Math.sin(a)];
const norm3 = (n) => { const l = Math.hypot(n[0], n[1], n[2]) || 1; return [n[0] / l, n[1] / l, n[2] / l]; };
const smooth = (t) => t * t * (3 - 2 * t);
const TAU = Math.PI * 2;
function hexRGB(h) { const n = parseInt(h.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
function hash2(x, y) { let h = (x * 374761393 + y * 668265263) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }

function ik(root, target, l1, l2, bend) {
  let d = sub(target, root);
  let len = len2(d);
  const maxL = (l1 + l2) * 0.999;
  if (len > maxL) { d = mul(d, maxL / len); target = add(root, d); len = maxL; }
  len = Math.max(len, Math.abs(l1 - l2) + 0.01);
  const a = (l1 * l1 - l2 * l2 + len * len) / (2 * len);
  const h = Math.sqrt(Math.max(0, l1 * l1 - a * a));
  const u = mul(d, 1 / len);
  return { mid: add(add(root, mul(u, a)), mul([-u[1] * bend, u[0] * bend], h)), end: target };
}

// ---------- colour ramps ----------
function hsv(h, s, v) { h = (((h % 360) + 360) % 360) / 60; const i = Math.floor(h) % 6, f = h - Math.floor(h), p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f)); const c = [[v, t, p], [q, v, p], [p, v, t], [p, q, v], [t, p, v], [v, p, q]][i]; return c.map((x) => Math.round(x * 255)); }
function toward(h, target, k) { const d = ((target - h + 540) % 360) - 180; return h + d * k; }
// Hue-shifted ramp: shadows drift toward violet-blue, highlights toward warm yellow.
function genRamp(h, s, n, o = {}) {
  const v0 = o.v0 == null ? 0.09 : o.v0, v1 = o.v1 == null ? 0.97 : o.v1;
  const cool = o.cool == null ? 0.3 : o.cool, warm = o.warm == null ? 0.25 : o.warm, fade = o.fade == null ? 0.45 : o.fade, g = o.gamma || 0.9;
  const out = [];
  for (let i = 0; i < n; i++) {
    const t = i / (n - 1);
    const hh = t < 0.5 ? toward(h, o.coolTo || 245, cool * (1 - t * 2)) : toward(h, o.warmTo || 52, warm * (t * 2 - 1));
    const ss = s * (0.8 + 0.35 * Math.sin(Math.PI * Math.min(1, t * 1.25))) * (1 - fade * t * t * t);
    out.push(hsv(hh, clamp(ss, 0, 1), clamp(v0 + (v1 - v0) * Math.pow(t, g), 0, 1)));
  }
  return out;
}

const BASE_RAMPS = {
  steel:    genRamp(220, 0.2, 8, { v0: 0.07, v1: 1, fade: 0.9, cool: 0.15, warm: 0.3 }),
  mail:     genRamp(225, 0.14, 7, { v0: 0.07, v1: 0.78 }),
  skin:     genRamp(20, 0.58, 7, { v0: 0.16, v1: 0.95, cool: 0.14, coolTo: 330, warm: 0.18, fade: 0.35 }),
  hair:     genRamp(18, 0.72, 7, { v0: 0.07, v1: 0.88, cool: 0.15, coolTo: 330, fade: 0.35 }),
  leather:  genRamp(26, 0.55, 7, { v0: 0.06, v1: 0.62, fade: 0.3 }),
  leather2: genRamp(20, 0.45, 6, { v0: 0.05, v1: 0.5 }),
  grip:     genRamp(15, 0.55, 6, { v0: 0.06, v1: 0.6 }),
  under:    genRamp(228, 0.3, 6, { v0: 0.06, v1: 0.52 }),
  gem:      genRamp(350, 0.85, 5, { v0: 0.2, v1: 1 }),
  wood:     genRamp(26, 0.55, 6, { v0: 0.08, v1: 0.66 }),
  straw:    genRamp(44, 0.5, 7, { v0: 0.14, v1: 0.9, fade: 0.6 }),
  burlap:   genRamp(34, 0.38, 7, { v0: 0.1, v1: 0.78 }),
  rope:     genRamp(36, 0.42, 6, { v0: 0.1, v1: 0.76 }),
  paint:    genRamp(4, 0.75, 6, { v0: 0.14, v1: 0.93, coolTo: 300 }),
  white:    genRamp(45, 0.08, 6, { v0: 0.18, v1: 1 }),
  hairC:    genRamp(24, 0.6, 6, { v0: 0.08, v1: 0.6 }),
  woodC:    genRamp(28, 0.6, 6, { v0: 0.12, v1: 0.78 }),
  hairB:    genRamp(212, 0.62, 7, { v0: 0.08, v1: 0.9, cool: 0.1, warm: 0.12, fade: 0.4 }),
  hairP:    genRamp(246, 0.62, 7, { v0: 0.12, v1: 0.93, cool: 0.08, warm: 0.08, fade: 0.45 }),
  hairK:    genRamp(228, 0.3, 6, { v0: 0.02, v1: 0.3 }),
  skinT:    genRamp(24, 0.62, 7, { v0: 0.12, v1: 0.88, cool: 0.12, coolTo: 330, fade: 0.3 }),
  cyan:     genRamp(196, 0.8, 7, { v0: 0.1, v1: 0.96, warm: 0.04, fade: 0.35 }),
  green:    genRamp(135, 0.6, 6, { v0: 0.07, v1: 0.72 }),
  goldc:    genRamp(46, 0.85, 7, { v0: 0.16, v1: 0.99, fade: 0.4 }),
  royal:    genRamp(224, 0.86, 7, { v0: 0.08, v1: 0.92, warm: 0.05, fade: 0.3 }),
  dsteel:   genRamp(215, 0.14, 7, { v0: 0.05, v1: 0.78, fade: 0.9 }),
  bronze:   genRamp(38, 0.72, 6, { v0: 0.1, v1: 0.9, fade: 0.5 }),
  bracer:   genRamp(150, 0.1, 7, { v0: 0.1, v1: 0.96, fade: 0.9 }),
};

const FACTIONS = {
  azure: {
    label: 'Azure',
    cloth:  genRamp(224, 0.8, 7, { v0: 0.08, v1: 0.86, warm: 0.1, fade: 0.25, gamma: 1.05 }),
    cape:   genRamp(355, 0.8, 7, { v0: 0.08, v1: 0.86, coolTo: 300, cool: 0.2, fade: 0.25, gamma: 1.05 }),
    lining: genRamp(40, 0.6, 6, { v0: 0.1, v1: 0.75 }),
    trim:   genRamp(43, 0.78, 7, { v0: 0.12, v1: 1, fade: 0.7 }),
    sigil:  genRamp(45, 0.1, 6, { v0: 0.2, v1: 1 }),
  },
  crimson: {
    label: 'Crimson',
    cloth:  genRamp(354, 0.76, 7, { v0: 0.09, v1: 0.92, coolTo: 300, cool: 0.2 }),
    cape:   genRamp(230, 0.18, 7, { v0: 0.05, v1: 0.55 }),
    lining: genRamp(354, 0.6, 6, { v0: 0.1, v1: 0.8, coolTo: 300 }),
    trim:   genRamp(36, 0.62, 7, { v0: 0.1, v1: 0.93, fade: 0.7 }),
    sigil:  genRamp(36, 0.62, 6, { v0: 0.12, v1: 0.98, fade: 0.7 }),
  },
  lord: {
    label: 'Lord',
    cloth:  genRamp(40, 0.1, 7, { v0: 0.14, v1: 0.99, fade: 0.6 }),
    cape:   genRamp(222, 0.8, 7, { v0: 0.08, v1: 0.86, warm: 0.1, fade: 0.25, gamma: 1.05 }),
    lining: genRamp(40, 0.6, 6, { v0: 0.1, v1: 0.75 }),
    trim:   genRamp(43, 0.78, 7, { v0: 0.12, v1: 1, fade: 0.7 }),
    sigil:  genRamp(222, 0.7, 6, { v0: 0.15, v1: 0.9 }),
  },
  onyx: {
    label: 'Onyx',
    cloth:  genRamp(255, 0.28, 7, { v0: 0.04, v1: 0.5 }),
    cape:   genRamp(285, 0.62, 7, { v0: 0.08, v1: 0.85, coolTo: 250 }),
    lining: genRamp(210, 0.08, 6, { v0: 0.2, v1: 0.95 }),
    trim:   genRamp(215, 0.12, 7, { v0: 0.12, v1: 1, fade: 0.9 }),
    sigil:  genRamp(285, 0.5, 6, { v0: 0.2, v1: 0.95 }),
  },
};

const MATS = {
  steel:    { metal: true, spec: 0.85, shin: 16 },
  mail:     { metal: true, spec: 0.35, shin: 6, noise: 0.07 },
  skin:     { amb: 0.36, spec: 0.08, shin: 12, soft: 0.5 },
  hair:     { amb: 0.22, spec: 0.3, shin: 14 },
  leather:  { amb: 0.24, spec: 0.16, shin: 10 },
  leather2: { amb: 0.24, spec: 0.1, shin: 10 },
  grip:     { amb: 0.24 },
  under:    { amb: 0.24 },
  cloth:    { amb: 0.2, spec: 0.04, shin: 8, gain: 0.88 },
  cape:     { amb: 0.16, gain: 0.92 },
  lining:   { amb: 0.14 },
  trim:     { metal: true, spec: 0.8, shin: 10 },
  sigil:    { amb: 0.42 },
  gem:      { amb: 0.35, spec: 1.2, shin: 20 },
  wood:     { amb: 0.26, noise: 0.04 },
  straw:    { amb: 0.3, noise: 0.05, stripe: 0.2 },
  burlap:   { amb: 0.28, noise: 0.06, weave: 0.1 },
  rope:     { amb: 0.28 },
  paint:    { amb: 0.3 },
  white:    { amb: 0.4 },
  hairC:    { amb: 0.25, spec: 0.2, shin: 10 },
  woodC:    { amb: 0.28, spec: 0.1, shin: 8 },
  hairB:    { amb: 0.22, spec: 0.32, shin: 14 },
  hairP:    { amb: 0.24, spec: 0.35, shin: 14 },
  hairK:    { amb: 0.2, spec: 0.5, shin: 16 },
  skinT:    { amb: 0.34, spec: 0.1, shin: 12, soft: 0.5 },
  cyan:     { amb: 0.24, spec: 0.15, shin: 10 },
  green:    { amb: 0.22, spec: 0.05 },
  goldc:    { amb: 0.24, spec: 0.12, shin: 10 },
  royal:    { amb: 0.22, spec: 0.12, shin: 10 },
  dsteel:   { metal: true, spec: 0.75, shin: 14 },
  bronze:   { metal: true, spec: 0.6, shin: 10 },
  bracer:   { metal: true, spec: 0.7, shin: 12 },
};
const MAT_KEYS = Object.keys(MATS);
const MAT_ID = Object.fromEntries(MAT_KEYS.map((k, i) => [k, i]));

function rampsFor(factionKey) {
  const f = FACTIONS[factionKey] || FACTIONS.azure;
  const out = Object.assign({}, BASE_RAMPS);
  for (const k of ['cloth', 'cape', 'lining', 'trim', 'sigil']) out[k] = f[k];
  return MAT_KEYS.map((k) => out[k]);
}

// ---------- primitives (all in model units) ----------
let EDGE_HIT = false;

function sampleCap(p, x, y) {
  const ax = p.a[0], ay = p.a[1], dx = p.b[0] - ax, dy = p.b[1] - ay;
  const L2 = dx * dx + dy * dy || 1e-6;
  let t = ((x - ax) * dx + (y - ay) * dy) / L2; t = t < 0 ? 0 : t > 1 ? 1 : t;
  const r = p.ra + (p.rb - p.ra) * t;
  const ox = x - (ax + dx * t), oy = y - (ay + dy * t), d2 = ox * ox + oy * oy;
  if (d2 > r * r) return null;
  if (p.edgeW && r - Math.sqrt(d2) < p.edgeW) EDGE_HIT = true;
  const nx = ox / r, ny = oy / r;
  const n = [nx, ny, Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny))];
  if (p.bands) { const s = Math.sin(t * Math.sqrt(L2) * p.bands[0] + (nx + ny) * 2); n[0] += dy / Math.sqrt(L2) * s * p.bands[1]; n[1] -= dx / Math.sqrt(L2) * s * p.bands[1]; }
  return n;
}

function sampleEll(p, x, y) {
  const a = p.rot || 0, c = Math.cos(a), s = Math.sin(a);
  const lx0 = x - p.c[0], ly0 = y - p.c[1];
  const lx = lx0 * c + ly0 * s, ly = -lx0 * s + ly0 * c;
  const qx = lx / p.rx, qy = ly / p.ry, q2 = qx * qx + qy * qy;
  if (q2 > 1) return null;
  if (p.edgeW && (1 - Math.sqrt(q2)) * Math.min(p.rx, p.ry) < p.edgeW) EDGE_HIT = true;
  return [qx * c - qy * s, qx * s + qy * c, Math.sqrt(1 - q2)];
}

function samplePoly(p, x, y) {
  const pts = p.pts; let inside = false, minD = 1e9, cx = 0, cy = 0;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const xi = pts[i][0], yi = pts[i][1], xj = pts[j][0], yj = pts[j][1];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  if (!inside) return null;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const xi = pts[i][0], yi = pts[i][1], xj = pts[j][0], yj = pts[j][1];
    const ex = xi - xj, ey = yi - yj, l2 = ex * ex + ey * ey || 1e-6;
    let t = ((x - xj) * ex + (y - yj) * ey) / l2; t = t < 0 ? 0 : t > 1 ? 1 : t;
    const px = xj + ex * t, py = yj + ey * t, d = (x - px) * (x - px) + (y - py) * (y - py);
    if (d < minD) { minD = d; cx = px; cy = py; }
  }
  const d = Math.sqrt(minD);
  const n = p.n ? [p.n[0], p.n[1], p.n[2]] : [0, 0, 1];
  if (p.nf) { const m = p.nf(x, y); n[0] += m[0]; n[1] += m[1]; n[2] += m[2]; }
  const bev = p.bevel == null ? 1.4 : p.bevel;
  if (bev > 0 && d < bev && d > 1e-4) {
    const k = (1 - d / bev) * (p.bevelK == null ? 1 : p.bevelK);
    n[0] += ((cx - x) / d) * k; n[1] += ((cy - y) / d) * k;
  }
  if (p.edgeW && d < p.edgeW) EDGE_HIT = true;
  return n;
}

function bboxOf(p) {
  if (p.type === 'cap') {
    const r = Math.max(p.ra, p.rb);
    return [Math.min(p.a[0], p.b[0]) - r, Math.min(p.a[1], p.b[1]) - r, Math.max(p.a[0], p.b[0]) + r, Math.max(p.a[1], p.b[1]) + r];
  }
  if (p.type === 'ell') { const r = Math.max(p.rx, p.ry); return [p.c[0] - r, p.c[1] - r, p.c[0] + r, p.c[1] + r]; }
  let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
  for (const q of p.pts) { x0 = Math.min(x0, q[0]); y0 = Math.min(y0, q[1]); x1 = Math.max(x1, q[0]); y1 = Math.max(y1, q[1]); }
  return [x0, y0, x1, y1];
}
const SAMPLERS = { cap: sampleCap, ell: sampleEll, poly: samplePoly };

// ---------- lighting ----------
const LIGHT_R = norm3([0.45, -0.72, 0.55]), LIGHT_L = [-LIGHT_R[0], LIGHT_R[1], LIGHT_R[2]];
let LIGHT = LIGHT_R;
function lightAt(m, n, x, y) {
  const d = n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2];
  const rx = 2 * n[2] * n[0], ry = 2 * n[2] * n[1], rz = 2 * n[2] * n[2] - 1;
  const rl = Math.max(0, rx * LIGHT[0] + ry * LIGHT[1] + rz * LIGHT[2]);
  let I;
  if (m.metal) {
    I = 0.1 + 0.52 * Math.max(0, d) + 0.24 * Math.max(0, -n[1]) + m.spec * Math.pow(rl, m.shin);
  } else {
    const amb = m.amb == null ? 0.26 : m.amb, wrap = m.soft || 0.18;
    I = amb + (1 - amb) * Math.max(0, (d + wrap) / (1 + wrap)) + (m.spec || 0) * Math.pow(rl, m.shin || 10);
  }
  if (m.gain) I *= m.gain;
  I += 0.16 * Math.max(0, -0.85 * n[0] - 0.35 * n[1] - 0.2 * n[2]); // cool rim from behind
  if (m.noise) I += (hash2(Math.floor(x * 1.5), Math.floor(y * 1.5)) - 0.5) * 2 * m.noise;
  if (m.stripe) I += m.stripe * (hash2(Math.floor(x * 0.7), Math.floor(y * 2.2)) - 0.5) * 2;
  if (m.weave) I += m.weave * ((Math.floor(x * 1.5) + Math.floor(y * 1.5)) & 1 ? 1 : -1);
  return I;
}

const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5].map((v) => (v + 0.5) / 16);
const N4 = [[1, 0], [-1, 0], [0, 1], [0, -1]];
let SCRATCH = null;
function scratch(n) {
  if (!SCRATCH || SCRATCH.n < n) SCRATCH = { n, zb: new Float32Array(n), pid: new Int16Array(n), inten: new Float32Array(n), mat: new Uint8Array(n) };
  return SCRATCH;
}

/* Render parts (model units) into a W x H RGBA buffer at `scale` output pixels per unit.
   opts.mode: 'pixel' (final) | 'lit' (smooth light, no palette) | 'flat' (shapes only) */
function renderModel(parts, opts) {
  const S = opts.scale || 1, SS = opts.ss || 3, W = opts.W, H = opts.H, mode = opts.mode || 'pixel';
  const ramps = rampsFor(opts.faction);
  LIGHT = opts.lightFromLeft ? LIGHT_L : LIGHT_R;
  const HW = W * SS, HH = H * SS, NH = HW * HH, inv = 1 / (S * SS);
  const buf = scratch(NH);
  const zb = buf.zb, pid = buf.pid, inten = buf.inten, mat = buf.mat;
  zb.fill(-1e9, 0, NH); pid.fill(-1, 0, NH);

  // Front-most parts first, so hidden samples are rejected before any shape maths.
  const order = parts.map((p, i) => i).sort((a, b) => parts[b].z - parts[a].z || b - a);
  for (const i of order) {
    const p = parts[i], f = SAMPLERS[p.type], bb = bboxOf(p), mid = MAT_ID[p.mat], eid = p.edgeMat ? MAT_ID[p.edgeMat] : mid;
    const x0 = Math.max(0, Math.floor(bb[0] * S * SS)), y0 = Math.max(0, Math.floor(bb[1] * S * SS));
    const x1 = Math.min(HW - 1, Math.ceil(bb[2] * S * SS)), y1 = Math.min(HH - 1, Math.ceil(bb[3] * S * SS));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const k = y * HW + x;
      if (p.z <= zb[k]) continue;
      const mx = (x + 0.5) * inv, my = (y + 0.5) * inv;
      EDGE_HIT = false;
      let n = f(p, mx, my);
      if (!n) continue;
      if (p.flat) n = [n[0] * (1 - p.flat), n[1] * (1 - p.flat), n[2] + p.flat];
      n = norm3(n);
      const m = EDGE_HIT ? eid : mid;
      zb[k] = p.z; pid[k] = i; mat[k] = m;
      inten[k] = lightAt(MATS[MAT_KEYS[m]], n, mx, my) + (p.bright || 0);
    }
  }

  // Cast shadows: a part further forward, up-light of this sample, blocks the light.
  if (mode !== 'flat') {
    const steps = [[0.9, 0.16], [1.9, 0.1]];
    for (const [dist, k] of steps) {
      const sx = Math.round(LIGHT[0] * dist * S * SS * 1.2), sy = Math.round(LIGHT[1] * dist * S * SS * 1.2);
      for (let y = 0; y < HH; y++) {
        const yy = y + sy; if (yy < 0 || yy >= HH) continue;
        for (let x = 0; x < HW; x++) {
          const i0 = y * HW + x; if (pid[i0] < 0) continue;
          const xx = x + sx; if (xx < 0 || xx >= HW) continue;
          const j = yy * HW + xx, q = pid[j]; if (q < 0) continue;
          const o = parts[q], me = parts[pid[i0]];
          if (o.g !== me.g && zb[j] > zb[i0] + 0.25 && !o.noCast && !me.noRecv) inten[i0] -= k;
        }
      }
    }
  }

  // Resolve sub-samples to output pixels.
  const N = W * H;
  const ocov = new Float32Array(N), opid = new Int16Array(N).fill(-1), omat = new Uint8Array(N), oint = new Float32Array(N), oz = new Float32Array(N).fill(-1e9);
  const sp = new Int32Array(SS * SS), sn = new Int32Array(SS * SS), si = new Float32Array(SS * SS), half = SS * SS / 2;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let u = 0, cov = 0;
    for (let sy = 0; sy < SS; sy++) {
      let k = (y * SS + sy) * HW + x * SS;
      for (let sx = 0; sx < SS; sx++, k++) {
        if (pid[k] < 0) continue;
        cov++;
        const key = pid[k] * 64 + mat[k];
        let j = 0; while (j < u && sp[j] !== key) j++;
        if (j === u) { sp[u] = key; sn[u] = 0; si[u] = 0; u++; }
        sn[j]++; si[j] += inten[k];
      }
    }
    if (cov < (opts.soft ? 1 : half)) continue;
    let b = 0;
    for (let j = 1; j < u; j++) if (sn[j] > sn[b] || (sn[j] === sn[b] && parts[sp[j] >> 6].z > parts[sp[b] >> 6].z)) b = j;
    const k = y * W + x, p = sp[b] >> 6;
    opid[k] = p; omat[k] = sp[b] & 63; oint[k] = si[b] / sn[b]; oz[k] = parts[p].z;
    ocov[k] = cov / (SS * SS);
  }

  const out = new Uint8ClampedArray(N * 4);
  const setc = (buf, k, c) => { buf[k * 4] = c[0]; buf[k * 4 + 1] = c[1]; buf[k * 4 + 2] = c[2]; buf[k * 4 + 3] = 255; };
  if (mode === 'flat') {
    for (let k = 0; k < N; k++) if (opid[k] >= 0) { const r = ramps[omat[k]]; setc(out, k, r[Math.floor(r.length / 2) - (opid[k] % 2)]); }
    return { rgba: out, pid: opid, parts };
  }
  if (mode === 'lit') {
    for (let k = 0; k < N; k++) if (opid[k] >= 0) {
      const r = ramps[omat[k]], v = clamp(oint[k], 0, 1) * (r.length - 1), i0 = Math.floor(v), i1 = Math.min(r.length - 1, i0 + 1), t = v - i0;
      setc(out, k, [lerp(r[i0][0], r[i1][0], t), lerp(r[i0][1], r[i1][1], t), lerp(r[i0][2], r[i1][2], t)]);
    }
    applyOverlays(out, W, H, opts.overlays, opid);
    if (opts.soft) {
      // Soft, painted edges: partial coverage becomes alpha; a faint dark rim replaces the hard outline.
      for (let k = 0; k < N; k++) if (opid[k] >= 0) out[k * 4 + 3] = Math.round(255 * Math.min(1, ocov[k] * 1.35));
      if (opts.rim) {
        const src = new Uint8ClampedArray(out);
        for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
          const k = y * W + x; if (src[k * 4 + 3] > 200) continue;
          let best = -1;
          for (const [dx, dy] of N4) { const xx = x + dx, yy = y + dy; if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue; const q = yy * W + xx; if (src[q * 4 + 3] > 200 && (best < 0 || oz[q] > oz[best])) best = q; }
          if (best < 0) continue;
          const a = src[k * 4 + 3] / 255, rim = opts.rim, c = ramps[omat[best]][0];
          out[k * 4] = lerp(c[0] * 0.6, src[k * 4], a); out[k * 4 + 1] = lerp(c[1] * 0.6, src[k * 4 + 1], a); out[k * 4 + 2] = lerp(c[2] * 0.6, src[k * 4 + 2], a);
          out[k * 4 + 3] = Math.max(src[k * 4 + 3], 255 * rim);
        }
      }
    }
    return { rgba: out, pid: opid, parts };
  }

  // Quantise to ramps with light ordered dithering.
  const dither = opts.dither == null ? 0.2 : opts.dither;
  const sh = new Int8Array(N).fill(-1);
  for (let k = 0; k < N; k++) if (opid[k] >= 0) {
    const n = ramps[omat[k]].length, x = k % W, y = (k / W) | 0;
    sh[k] = clamp(Math.floor(oint[k] * n + (BAYER[(y & 3) * 4 + (x & 3)] - 0.5) * dither), 0, n - 1);
  }
  // Clean-up: a lone pixel whose 4 neighbours share one shade of the same material takes that shade.
  const same = (a, b) => opid[a] === opid[b] && omat[a] === omat[b];
  for (let pass = 0; pass < 2; pass++) for (let y = 1; y < H - 1; y++) for (let x = 1; x < W - 1; x++) {
    const k = y * W + x; if (opid[k] < 0) continue;
    const nb = [k - 1, k + 1, k - W, k + W];
    if (!nb.every((j) => same(j, k))) continue;
    const s0 = sh[nb[0]];
    if (s0 !== sh[k] && nb.every((j) => sh[j] === s0)) sh[k] = s0;
  }
  // Occlusion lines where a different part group sits in front.
  const sh2 = Int8Array.from(sh);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const k = y * W + x; if (opid[k] < 0) continue;
    const me = parts[opid[k]];
    for (const [dx, dy] of N4) {
      const xx = x + dx, yy = y + dy; if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
      const q = opid[yy * W + xx]; if (q < 0) continue;
      const o = parts[q];
      if (o.g !== me.g && o.z > me.z + 0.05 && !o.noLine) { sh2[k] = Math.max(0, sh[k] - (me.lineK || 2)); break; }
    }
  }
  for (let k = 0; k < N; k++) if (opid[k] >= 0) setc(out, k, ramps[omat[k]][sh2[k]]);
  // Selective outline: dark on shadowed edges, a deep tint of the material on lit edges.
  const oc = new Uint8ClampedArray(out);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const k = y * W + x; if (opid[k] >= 0) continue;
    let best = -1;
    for (const [dx, dy] of N4) {
      const xx = x + dx, yy = y + dy; if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
      const q = yy * W + xx; if (opid[q] < 0) continue;
      if (best < 0 || oz[q] > oz[best]) best = q;
    }
    if (best < 0) continue;
    const r = ramps[omat[best]], lit = sh[best] >= r.length - 2 ? 1 : 0;
    const c = r[lit], f = lit ? 0.8 : 0.5;
    setc(oc, k, [c[0] * f, c[1] * f, c[2] * f * 1.05 + 4]);
  }
  applyOverlays(oc, W, H, opts.overlays, opid);
  return { rgba: oc, pid: opid, parts };
}

function applyOverlays(buf, W, H, overlays, pid) {
  if (!overlays) return;
  for (const o of overlays) {
    const x = Math.round(o.x), y = Math.round(o.y);
    if (x < 0 || y < 0 || x >= W || y >= H) continue;
    if (o.onlyOn && pid[y * W + x] < 0) continue;
    const k = (y * W + x) * 4;
    buf[k] = o.rgb[0]; buf[k + 1] = o.rgb[1]; buf[k + 2] = o.rgb[2]; buf[k + 3] = 255;
  }
}

function tint(buf, rgb, k) {
  for (let i = 0; i < buf.length; i += 4) if (buf[i + 3]) {
    buf[i] = lerp(buf[i], rgb[0], k); buf[i + 1] = lerp(buf[i + 1], rgb[1], k); buf[i + 2] = lerp(buf[i + 2], rgb[2], k);
  }
}

// ---------- the knight ----------
const READY = {
  ox: 0, px: 43, py: 57, spine: 0.06, neck: 0,
  footN: [34, 84], footF: [55, 84],
  handN: [52, 58], swordA: -0.6, swordZ: 9.8,
  handF: [58, 46], shieldA: 0.04,
  cape: 0, wind: 0.5, hair: 0, blink: 0, flash: 0, mouth: 0,
};

function star(c, n, rOut, rIn, sx, rotA) {
  const pts = [];
  for (let i = 0; i < n * 2; i++) { const r = i % 2 ? rIn : rOut, a = (i / (n * 2)) * TAU - Math.PI / 2 + (rotA || 0); pts.push(add(c, [Math.cos(a) * r * sx, Math.sin(a) * r])); }
  return pts;
}

function buildKnight(q) {
  const P = [q.px, q.py], sp = q.spine;
  const parts = [];
  const push = (o) => { parts.push(o); return o; };
  const cap = (a, b, ra, rb, mat, z, g, x) => push(Object.assign({ type: 'cap', a, b, ra, rb, mat, z, g }, x));
  const ell = (c, rx, ry, r, mat, z, g, x) => push(Object.assign({ type: 'ell', c, rx, ry, rot: r, mat, z, g }, x));
  const poly = (pts, mat, z, g, x) => push(Object.assign({ type: 'poly', pts, mat, z, g }, x));
  const local = (x, y) => rot(sub([x, y], P), -sp);

  const N = T(P, sp, [0.6, -21.5]);
  const hA = sp + q.neck;
  const H = T(N, hA, [1.4, -6.6]);
  const SN = T(P, sp, [-5.4, -19.2]);
  const SF = T(P, sp, [4.4, -19.8]);
  const HN = T(P, sp * 0.3, [-3, 1]);
  const HF = T(P, sp * 0.3, [3.4, 0]);
  const w = q.wind, cph = q.cape, HM = q.lord ? 'hairB' : 'hair';

  // --- cape, behind everything, with its lining showing along the trailing edge
  const hem = (i, bx, by) => [P[0] + bx - w * (2 + i * 0.7) + Math.sin(cph + i * 1.3) * 1.1, P[1] + by - w * (1.4 - i * 0.28) + Math.cos(cph * 0.8 + i) * 0.8];
  const capeTop = Math.min(SN[1], SF[1]) - 1.5;
  const c1 = add(SN, [-4.2 - w * 1.4, 7.5]), c2 = add(SN, [-8.5 - w * 3, 19 - w * 0.6]);
  const h0 = hem(0, -22, 18);
  poly([
    add(SF, [1.5, -1.2]), add(N, [0, -2.2]), add(SN, [-1.8, -0.8]), c1, c2,
    h0, hem(1, -17.5, 21.2), hem(2, -12, 20), hem(3, -6, 21.5), hem(4, 1, 19.5),
    [P[0] + 5, P[1] + 3], add(SF, [3, 7]),
  ], 'cape', 0, 'cape', {
    n: [-0.25, 0, 1], bevel: 1.2,
    nf: (x, y) => { const k = clamp((y - capeTop) / 26, 0.12, 1); return [0.85 * Math.sin(x * 0.62 - y * 0.12 + cph * 0.7) * k, 0, 0]; },
  });

  // --- hair tail behind the neck
  const hs = Math.sin(q.hair) * 0.9 - w * 0.9;
  if (!q.helm) {
    cap(T(H, hA, [-5.2, 1.6]), T(H, hA, [-8.4 + hs, 9.5]), 2.4, 0.7, HM, 4.6, 'hairback');
    cap(T(H, hA, [-4.4, 3.6]), T(H, hA, [-6.4 + hs * 0.7, 11.8]), 1.8, 0.5, HM, 4.62, 'hairback');
  }

  // --- far (shield) arm
  const aF = ik(SF, q.lord ? add(q.handF, [-5, 10]) : q.handF, 8.6, 8, 1);
  cap(SF, aF.mid, 2.4, 2.1, 'mail', 1, 'armF');
  cap(aF.mid, aF.end, 2.2, 2.1, 'steel', 1.1, 'armF');
  ell(SF, 3.8, 3.4, 0, 'steel', 4.7, 'pauldF', { edgeW: 0.55, edgeMat: 'trim' });

  // --- legs (IK keeps the feet planted)
  const leg = (hip, foot, zb, g) => {
    const L = ik(hip, foot, 14, 14, -1);
    const td = unit(sub(L.mid, hip)), front = [-td[1], td[0]];
    const fr = front[0] < 0 ? mul(front, -1) : front; // the side that faces forward (+x)
    const sd = unit(sub(L.end, L.mid)), sfront = sd[1] > 0 ? [sd[1], -sd[0]] : [-sd[1], sd[0]];
    cap(hip, L.mid, 3.6, 2.8, 'under', zb, g);
    cap(add(mix2(hip, L.mid, 0.18), mul(fr, 0.9)), add(mix2(hip, L.mid, 0.9), mul(fr, 0.7)), 2.7, 2.2, 'steel', zb + 0.1, g + 'c', { flat: 0.15 });
    cap(L.mid, L.end, 2.7, 2.15, 'leather', zb + 0.2, g + 'b');
    cap(mix2(L.mid, L.end, 0.1), mix2(L.mid, L.end, 0.3), 3.15, 3.0, 'leather2', zb + 0.22, g + 'cuff');
    const st = mix2(L.mid, L.end, 0.68), sp2 = [-sd[1], sd[0]];
    cap(add(st, mul(sp2, 2.4)), add(st, mul(sp2, -2.4)), 0.6, 0.6, 'leather2', zb + 0.23, g + 's', { noCast: true });
    ell(add(st, mul(sfront, 2.1)), 0.8, 0.7, 0, 'trim', zb + 0.24, g + 's2', { noCast: true });
    ell(add(L.mid, mul(sfront, 0.5)), 2.2, 2.0, 0, 'steel', zb + 0.3, g + 'k');
    ell(add(L.mid, add(mul(sfront, -1.3), [0, 0.2])), 1.5, 1.9, 0, 'steel', zb + 0.29, g + 'k', { edgeW: 0.45, edgeMat: 'trim' });
    cap(L.end, add(L.end, [5.8, 2.3]), 2.35, 1.6, 'leather', zb + 0.25, g + 'b');
    cap(add(L.end, [-1.4, 2.3]), add(L.end, [6.6, 3.1]), 0.75, 0.7, 'leather2', zb + 0.26, g + 'b', { flat: 0.4 });
    return L;
  };
  leg(HF, q.footF, 2, 'legF');
  leg(HN, q.footN, 4, 'legN');

  // --- torso
  poly([
    [-4.6, -23.2], [3.4, -23.4], [6.4, -20.6], [7.0, -15], [5.2, -7], [5.4, -1],
    [-5.6, -0.6], [-5.4, -7], [-7.2, -14], [-7.6, -20],
  ].map((v) => T(P, sp, v)), 'steel', 5, 'torso', {
    n: [0.2, 0, 1], bevel: 1.8, bevelK: 0.8,
    nf: (x, y) => { const l = local(x, y); return [((l[0] - 1.4) / 7) * 1.05, ((l[1] + 15) / 12) * 0.35, 0]; },
  });
  // Faulds: two rows of hip plates under the tabard.
  cap(T(P, sp, [-6.3, 3.2]), T(P, sp, [5.4, 2.8]), 1.9, 1.9, 'steel', 5.6, 'fauld2', { flat: 0.25, edgeW: 0.4, edgeMat: 'trim' });
  cap(T(P, sp, [-6.6, 0.8]), T(P, sp, [5.8, 0.4]), 2.1, 2.1, 'steel', 5.62, 'fauld1', { flat: 0.25 });
  // Neck, gorget, cape clasp.
  cap(T(P, sp, [0.6, -21]), T(H, hA, [-0.4, 4.6]), 2.4, 2.3, 'skin', 4.9, 'neck');
  ell(T(P, sp, [0.2, -21.8]), 5.3, 2.5, sp, 'steel', 5.4, 'gorget', { edgeW: 0.5, edgeMat: 'trim' });

  // --- tabard with trim, front slit and sun sigil
  const sway = Math.sin(cph * 0.9) * 0.5 - w * 0.5;
  poly([
    T(P, sp, [-1.6, -22.2]), T(P, sp, [4.4, -22.4]), T(P, sp, [5.2, -4.2]),
    [P[0] + 6.5 + sway, P[1] + 10.6], [P[0] + 2.4 + sway, P[1] + 11.4], [P[0] + 1.5 + sway * 0.7, P[1] + 8.4],
    [P[0] + 0.6 + sway, P[1] + 11.4], [P[0] - 3.5 + sway, P[1] + 10.6], T(P, sp, [-3, -4.2]),
  ], 'cloth', 6, 'tabard', {
    n: [0.3, 0, 1], bevel: 1.2, edgeW: 0.8, edgeMat: 'trim',
    nf: (x, y) => {
      const l = local(x, y);
      const fold = l[1] > -3 ? 0.45 * Math.sin(l[0] * 1.25 + 0.6 + sway) * clamp((l[1] + 3) / 12, 0, 1) : 0;
      return [((l[0] - 1.2) / 5) * 0.7 + fold, 0, 0];
    },
  });
  const sg = T(P, sp, [1.5, -13.2]);
  if (!q.lord) { poly(star(sg, 8, 3.3, 1.5, 0.85, sp), 'trim', 6.1, 'sun', { bevel: 0.6 }); ell(sg, 1.45, 1.6, sp, 'sigil', 6.12, 'sunc'); }
  else poly([add(sg, [0, -3.2]), add(sg, [2.2, 0]), add(sg, [0, 3.2]), add(sg, [-2.2, 0])], 'sigil', 6.1, 'sun', { bevel: 0.6 });
  // Belt, buckle, pouch.
  cap(T(P, sp, [-5.8, -1.4]), T(P, sp, [5.8, -1.8]), 1.6, 1.6, 'leather', 6.5, 'belt', { flat: 0.3 });
  ell(T(P, sp, [1.8, -1.6]), 1.7, 1.5, sp, 'trim', 6.6, 'buckle', { edgeW: 0.5, edgeMat: 'trim' });
  cap(T(P, sp, [2.6, -0.6]), T(P, sp, [3.0 + sway * 0.4, 3.4]), 0.7, 0.6, 'leather', 6.55, 'beltend');
  ell(T(P, sp, [-5.2, 1.4]), 2.0, 2.5, sp, 'leather2', 6.45, 'pouch', { edgeW: 0.4, edgeMat: 'leather' });

  // --- head
  ell(H, 5.3, 6.1, hA, 'skin', 8, 'head');
  cap(T(H, hA, [-1.2, 2.4]), T(H, hA, [3.4, 4.5]), 3.3, 1.6, 'skin', 8.01, 'head');
  ell(T(H, hA, [5.1, 0.9]), 1.1, 1.45, hA, 'skin', 8.02, 'head');
  ell(T(H, hA, [-2.1, 1.0]), 1.3, 1.9, hA, 'skin', 8.6, 'ear');
  if (q.helm) {
    // Closed great helm with a slit visor and a plume that trails in the wind.
    const hp = (v) => T(H, hA, v);
    poly([[-6.2, 5.6], [-7.0, 1.0], [-6.6, -4.4], [-4.4, -7.6], [-0.6, -8.8], [3.2, -8.0], [5.8, -5.2], [6.8, -1.0], [6.6, 3.8], [4.6, 6.8], [-1.0, 7.2], [-4.6, 6.8]].map(hp),
      'steel', 8.7, 'helm', { n: [0.15, 0, 1], bevel: 2.4, bevelK: 1.1, edgeW: 0, nf: (x, y) => { const l = rot(sub([x, y], H), -hA); return [l[0] / 7 * 0.8, l[1] / 9 * 0.5, 0]; } });
    cap(hp([0.6, -0.6]), hp([6.6, -1.0]), 0.75, 0.6, 'under', 8.72, 'visor', { noCast: true });
    cap(hp([0.4, -8.6]), hp([3.4, -2.2]), 0.55, 0.55, 'trim', 8.71, 'ridge', { noCast: true, flat: 0.2 });
    cap(hp([-1.2, 2.6]), hp([5.6, 2.2]), 0.3, 0.3, 'under', 8.72, 'visor', { noCast: true });
    const pl = hs * 1.2 - w * 1.2;
    cap(hp([0.6, -9.2]), hp([-7.5 + pl, -8.2]), 1.8, 0.9, 'cloth', 8.66, 'plume');
    cap(hp([-2.5, -9.0]), hp([-10 + pl * 1.4, -4.2]), 1.6, 0.5, 'cloth', 8.65, 'plume');
    cap(hp([-4.5, -8.0]), hp([-10.5 + pl * 1.6, 0.2]), 1.3, 0.4, 'cloth', 8.64, 'plume');
  }
  const hc = T(H, hA, [-1.2, -2.6]);
  const hairNf = (x, y) => { const dx = (x - hc[0]) / 8, dy = (y - hc[1]) / 8; return [dx + 0.18 * Math.sin((y - hc[1]) * 1.9 + (x - hc[0]) * 0.9), dy, Math.sqrt(Math.max(0, 1 - dx * dx - dy * dy))]; };
  if (!q.helm) poly([
    [-5.2, 5.8], [-7.0, 2.8], [-7.6, -1.2], [-7.0, -5.0], [-4.9, -7.7], [-3.2, -8.8], [-1.2, -9.3], [0.8, -9.3], [2.6, -8.8], [4.0, -7.9], [5.0, -6.7],
    [6.3, -4.3], [5.6, -2.3], [4.3, -3.7], [3.4, -1.9], [2.2, -3.6], [1.0, -1.8], [-0.3, -3.4],
    [-2.2, -2.9], [-3.3, -0.6], [-3.4, 2.8], [-4.2, 4.4],
  ].map((v) => T(H, hA, v)), HM, 8.5, 'hair', { n: [0, 0, 0.35], bevel: 1.1, bevelK: 0.6, nf: hairNf });
  // Circlet and a few loose locks for volume.
  const lock = (a, b, ra, rb) => q.helm || cap(T(H, hA, a), T(H, hA, b), ra, rb, HM, 8.56, 'lock', { lineK: 1 });
  if (q.lord) {
    cap(T(H, hA, [-3.4, -3.0]), T(H, hA, [5.8, -3.6]), 0.55, 0.5, 'trim', 8.58, 'circlet', { noCast: true });
    ell(T(H, hA, [4.6, -3.6]), 0.9, 0.9, 0, 'gem', 8.59, 'circlet', { noCast: true });
  }
  lock([3.2, -7.6], [-5.6, -5.8], 1.5, 0.8);
  lock([2.9, -7.0], [5.8, -2.4], 1.3, 0.4);

  // --- shield on the far arm, with the same sun device as the tabard
  if (!q.lord) {
  const S = add(q.handF, rot([2.6, 1.2], q.shieldA));
  const heater = [[-6.2, -9.2], [0, -9.9], [6.2, -9.2], [6.3, -2], [4.9, 4.4], [0, 10.4], [-4.9, 4.4], [-6.3, -2]];
  const shp = (v) => add(S, rot([v[0] * 0.82, v[1]], q.shieldA));
  poly(heater.map(shp), 'cloth', 6.8, 'shield', {
    bevel: 2, bevelK: 0.7, edgeW: 1.2, edgeMat: 'steel',
    nf: (x, y) => { const l = rot(sub([x, y], S), -q.shieldA); return [l[0] / 6 * 0.75 + 0.35, l[1] / 11 * 0.35 - 0.1, 1]; },
  });
  const sc = shp([0, -0.6]);
  poly(star(sc, 8, 4.4, 2.0, 0.82, q.shieldA), 'trim', 6.85, 'shieldSun', { bevel: 0.7, n: [0.3, 0, 1] });
  ell(sc, 1.9 * 0.82, 2.0, q.shieldA, 'sigil', 6.87, 'shieldSunC', { n: [0.3, 0, 1] });
  }

  // --- near (sword) arm
  const aN = ik(SN, q.handN, 8.6, 8, 1);
  const fd = unit(sub(aN.end, aN.mid));
  cap(SN, aN.mid, 2.6, 2.3, 'mail', 9, 'armN');
  cap(aN.mid, add(aN.end, mul(fd, -1)), 2.4, 2.25, 'steel', 9.3, 'vamb');
  ell(aN.mid, 2.2, 2.1, 0, 'steel', 9.35, 'couter');
  ell(add(aN.mid, mul(unit(sub(aN.mid, mix2(SN, aN.end, 0.5))), 1.4)), 1.6, 1.3, 0, 'steel', 9.34, 'couter', { edgeW: 0.4, edgeMat: 'trim' });
  cap(add(aN.end, mul(fd, -2.6)), add(aN.end, mul(fd, -0.8)), 2.85, 2.75, 'steel', 9.95, 'gaunt', { edgeW: 0.45, edgeMat: 'trim' });
  ell(aN.end, 2.5, 2.3, 0, 'steel', 10, 'fist');
  // Pauldron: three lames with a gilded rim.
  if (!q.lord) { ell(T(SN, sp, [-0.9, 5.4]), 3.0, 1.9, sp - 0.3, 'steel', 9.44, 'paul3'); ell(T(SN, sp, [-0.6, 3.4]), 3.8, 2.5, sp - 0.25, 'steel', 9.46, 'paul2'); }
  ell(T(SN, sp, [-0.3, 0.4]), 4.7, 3.9, sp - 0.2, 'steel', 9.5, 'paul1', { edgeW: 0.55, edgeMat: 'trim' });
  ell(T(P, sp, [-2.6, -22.1]), 1.25, 1.25, 0, 'trim', 9.55, 'clasp');
  ell(T(P, sp, [-2.6, -22.1]), 0.6, 0.6, 0, 'gem', 9.56, 'clasp');

  // --- sword: gem pommel, wrapped grip, curved quillons, fullered blade
  const d = dirOf(q.swordA), pp = [-d[1], d[0]];
  const hN = q.handN;
  ell(add(hN, mul(d, -4.5)), 1.5, 1.5, 0, 'trim', 9.8, 'pommel');
  ell(add(hN, mul(d, -4.5)), 0.7, 0.7, 0, 'gem', 9.81, 'pommel');
  cap(add(hN, mul(d, -3.7)), add(hN, mul(d, 1.9)), 1.0, 1.0, 'grip', 9.7, 'grip', { bands: [2.4, 0.9] });
  const gq = add(hN, mul(d, 2.7));
  cap(gq, add(add(gq, mul(pp, 4.2)), mul(d, 0.6)), 1.05, 0.8, 'trim', 10.2, 'guard');
  cap(gq, add(add(gq, mul(pp, -4.2)), mul(d, 0.6)), 1.05, 0.8, 'trim', 10.2, 'guard');
  ell(gq, 1.35, 1.35, 0, 'trim', 10.21, 'guard');
  const b0 = add(hN, mul(d, 3.3)), b1 = add(hN, mul(d, 27.5)), tip = add(hN, mul(d, 31));
  poly([add(b0, mul(pp, 1.5)), add(b1, mul(pp, 1.2)), tip, add(b1, mul(pp, -1.2)), add(b0, mul(pp, -1.5))], 'steel', q.swordZ > 5 ? 10.1 : q.swordZ, 'blade', {
    bevel: 0, n: [0.25, -0.35, 1], noCast: true,
    nf: (x, y) => {
      const s = (x - hN[0]) * pp[0] + (y - hN[1]) * pp[1], along = (x - hN[0]) * d[0] + (y - hN[1]) * d[1];
      const inFuller = Math.abs(s) < 0.42 && along < 22;
      const k = inFuller ? (s > 0 ? -0.7 : 0.7) : (s > 0 ? 0.55 : -0.55);
      return [pp[0] * k, pp[1] * k, 0];
    },
  });

  return { parts, head: H, headA: hA, pelvis: P, tip, hand: hN };
}

// Face pixels, drawn after the pixel pass. Offsets are output pixels from the head centre.
// Chars: k lash/pupil, w eye white, i iris, b brow, n skin shadow, m mouth, l lip.
const FACE = {
  1: { rows: ['bb.b.', 'kk.k.', 'kiwi.', '....n', '.....', '..mm.'], ox: 1, oy: -1,
       blink: ['bb.b.', '.....', 'kk.k.'], shut: 3 },
  1.5: { rows: [
    '..bbb..bb.',
    '.kkkk..kk.',
    '.wiik..ik.',
    '..ii...i..',
    '.........n',
    '........nn',
    '..........',
    '.....mm...',
    '.....l....',
  ], ox: 0, oy: -2,
  blink: ['..bbb..bb.', '..........', '.kkkk..kk.', '..nn....n.'], shut: 4 },
};
const FACE_RGB = { k: [26, 14, 24], w: [238, 232, 224], i: [70, 110, 170], b: [74, 36, 20], n: [176, 104, 78], m: [120, 50, 48], l: [206, 128, 108] };

function faceOverlay(b, q, S) {
  const f = FACE[S]; if (!f || q.helm) return [];
  const hc = mul(b.head, S), ov = [];
  const rows = q.blink ? f.blink.concat(f.rows.slice(f.shut)) : f.rows;
  rows.forEach((row, y) => [...row].forEach((ch, x) => {
    if (ch === '.') return;
    let c = FACE_RGB[ch];
    if (q.mouth && ch === 'l') c = FACE_RGB.m;
    ov.push({ x: Math.round(hc[0]) + f.ox + x, y: Math.round(hc[1]) + f.oy + y, rgb: c });
  }));
  return ov;
}

function renderKnight(q, opts) {
  const S = opts.scale || 1.5, size = Math.round(96 * S);
  const b = buildKnight(q);
  const r = renderModel(b.parts, Object.assign({ W: size, H: size }, opts, { scale: S, overlays: opts.mode === 'flat' ? null : faceOverlay(b, q, S) }));
  if (q.flash) tint(r.rgba, [255, 255, 255], q.flash);
  return Object.assign(r, { build: b, size });
}


// ---------- animation (every pose is a pure function of time, so frames can be cached) ----------
const STEP = { swordZ: 1, blink: 1, mouth: 1 };
function mixPose(a, b, u) {
  const o = {};
  for (const k in a) {
    const va = a[k], vb = b[k] == null ? va : b[k];
    if (STEP[k] || (typeof va !== 'number' && !Array.isArray(va))) o[k] = u < 1 ? va : vb;
    else if (Array.isArray(va)) o[k] = [lerp(va[0], vb[0], u), lerp(va[1], vb[1], u)];
    else o[k] = lerp(va, vb, u);
  }
  return o;
}
function track(base, keys) { let prev = Object.assign({}, base); return keys.map((k) => (prev = Object.assign({}, prev, k))); }
function sampleTrack(F, t) {
  if (t <= F[0].t) return Object.assign({}, F[0]);
  for (let i = 0; i < F.length - 1; i++) if (t < F[i + 1].t) {
    const u = (t - F[i].t) / (F[i + 1].t - F[i].t);
    return mixPose(F[i], F[i + 1], F[i + 1].lin ? u : smooth(u));
  }
  return Object.assign({}, F[F.length - 1]);
}
// Cape and hair trail behind movement: derived from how fast the body is travelling.
function withDrag(F, t, q, base) {
  const dt = 0.09, p = sampleTrack(F, Math.max(0, t - dt));
  const vx = (q.ox - p.ox) / dt, vy = (q.py - p.py) / dt;
  q.wind = base + clamp(vx / 110, -0.5, 1.9) + clamp(-vy / 60, -0.3, 0.6);
  q.cape = t * 7; q.hair = t * 8;
  return q;
}

const R = READY;
const ATTACK = track(R, [
  { t: 0 },
  { t: 0.1, py: 59.5, spine: 0.18, handN: [44, 58], swordA: 0.1, handF: [57, 48] },
  { t: 0.34, ox: 58, py: 58, spine: 0.32, handN: [41, 56], swordA: 0.35, handF: [58, 47] },
  { t: 0.46, ox: 62, py: 55, spine: -0.12, neck: -0.08, handN: [33, 27], swordA: -2.7, swordZ: 0.8, handF: [60, 42], footF: [58, 84], mouth: 1 },
  { t: 0.54, ox: 64, py: 60.5, spine: 0.28, neck: -0.05, handN: [61, 53], swordA: 0.6, swordZ: 9.8, handF: [53, 49], footF: [61, 84] },
  { t: 0.78, ox: 64, py: 60, spine: 0.24, handN: [60, 54], swordA: 0.66, mouth: 0 },
  { t: 0.92, ox: 58, py: 57, spine: 0.08, neck: 0, handN: [50, 57], swordA: -0.2, footF: [55, 84] },
  { t: 1.18, ox: 0, handN: R.handN, swordA: R.swordA, handF: R.handF, spine: R.spine, lin: 1 },
  { t: 1.4 },
]);
const HURT = track(R, [
  { t: 0 },
  { t: 0.07, ox: -5, py: 58.5, spine: -0.26, neck: -0.22, handN: [46, 58], swordA: -0.2, handF: [55, 44], shieldA: -0.25, mouth: 1, blink: 1 },
  { t: 0.34, ox: -4, py: 58, spine: -0.18, neck: -0.12 },
  { t: 0.7, ox: 0, py: R.py, spine: R.spine, neck: 0, handN: R.handN, swordA: R.swordA, handF: R.handF, shieldA: R.shieldA, mouth: 0, blink: 0 },
  { t: 0.8 },
]);
const VICTORY = track(R, [
  { t: 0 },
  { t: 0.12, py: 58.5, spine: 0.12, handN: [48, 56], swordA: -1.0 },
  { t: 0.36, py: 55.5, spine: -0.06, neck: -0.14, handN: [36, 15], swordA: -1.6, handF: [56, 48], shieldA: -0.1, footN: [36, 84], footF: [53, 84] },
  { t: 2.6 },
]);

const ANIMS = {
  ready: { dur: 1.5, fps: 8, loop: true, pose: (t) => {
    const ph = (t / 1.5) * TAU, b = 0.5 - 0.5 * Math.cos(ph);
    return Object.assign({}, R, {
      py: R.py + b * 0.9, handN: [R.handN[0], R.handN[1] + b * 0.8], handF: [R.handF[0], R.handF[1] + b * 0.6],
      swordA: R.swordA + 0.025 * Math.sin(ph), cape: ph, wind: 0.5 + 0.25 * Math.sin(ph), hair: ph,
    });
  } },
  attack: { dur: 1.4, fps: 16, pose: (t) => {
    const q = sampleTrack(ATTACK, t);
    if (t > 0.1 && t < 0.34) { // run cycle
      const f = ((t - 0.1) / 0.24) * TAU * 1.5;
      q.footN = [R.footN[0] + 7 * Math.sin(f), R.footN[1] - 3.5 * Math.max(0, Math.cos(f))];
      q.footF = [R.footF[0] - 7 * Math.sin(f), R.footF[1] - 3.5 * Math.max(0, -Math.cos(f))];
      q.py -= 1.2 * Math.abs(Math.sin(f));
    }
    if (t > 0.92 && t < 1.18) q.py -= 7 * Math.sin((Math.PI * (t - 0.92)) / 0.26);
    return withDrag(ATTACK, t, q, 0.5);
  } },
  hurt: { dur: 0.8, fps: 16, pose: (t) => Object.assign(withDrag(HURT, t, sampleTrack(HURT, t), 0.5), { flash: t < 0.07 ? 0.7 : t < 0.13 ? 0.3 : 0 }) },
  victory: { dur: 2.6, fps: 12, pose: (t) => {
    const q = sampleTrack(VICTORY, t);
    if (t > 0.36) { const b = 0.5 - 0.5 * Math.cos((t - 0.36) * 4); q.py += b * 0.8; q.handN = [q.handN[0], q.handN[1] + b * 0.8]; }
    q.wind = 0.9 + 0.5 * Math.sin(t * 3); q.cape = t * 4; q.hair = t * 5;
    return q;
  } },
};
// Frame index for an animation at time t (stepped like hand-made sprite animation).
function frameOf(name, t) {
  const A = ANIMS[name];
  const n = Math.round(A.dur * A.fps);
  const i = Math.floor(t * A.fps);
  return A.loop ? ((i % n) + n) % n : clamp(i, 0, n - 1);
}

// ---------- training dummy ----------
const DUMMY_BASE = [24, 76];
function buildDummy(wobble) {
  const B = DUMMY_BASE;
  const P = (x, y) => T(B, wobble, [x - B[0], y - B[1]]);
  const parts = [];
  const cap = (a, b, ra, rb, mat, z, g, x) => parts.push(Object.assign({ type: 'cap', a: P(...a), b: P(...b), ra, rb, mat, z, g }, x));
  const ell = (c, rx, ry, mat, z, g, x) => parts.push(Object.assign({ type: 'ell', c: P(...c), rx, ry, rot: wobble, mat, z, g }, x));
  cap([24, 78], [24, 23], 1.9, 1.6, 'wood', 1, 'post', { bands: [0.9, 0.25] });
  cap([10.5, 36.5], [37.5, 35.5], 1.4, 1.4, 'wood', 2, 'bar');
  // straw spilling from the arm ends and neck
  for (const [x, y, s] of [[9.5, 36.5, -1], [38.5, 35.5, 1]]) {
    cap([x, y], [x + s * 3.2, y - 1.4], 1.3, 0.3, 'straw', 2.6, 'tuft');
    cap([x, y], [x + s * 3.4, y + 1.3], 1.3, 0.3, 'straw', 2.61, 'tuft');
    cap([x, y], [x + s * 2.2, y + 3.0], 1.1, 0.3, 'straw', 2.62, 'tuft');
  }
  const sack = [];
  for (let i = 0; i < 24; i++) {
    const a = (i / 24) * TAU, c = Math.cos(a), s = Math.sin(a);
    const rx = 9.4 - (s > 0 ? s * 1.6 : 0), ry = s > 0 ? 12.2 : 11.6;
    sack.push([24 + Math.sign(c) * Math.pow(Math.abs(c), 0.7) * rx, 47.6 + Math.sign(s) * Math.pow(Math.abs(s), 0.8) * ry]);
  }
  parts.push({ type: 'poly', pts: sack.map((v) => P(...v)), mat: 'burlap', z: 3, g: 'body',
    bevel: 5, bevelK: 1.3, n: [0.2, 0, 1], nf: (x, y) => [0.25 * Math.sin(y * 0.9 + x * 0.3), 0, 0] });
  cap([19, 34.5], [21.5, 30.5], 1.0, 0.3, 'straw', 3.2, 'neckstraw');
  cap([26, 34.5], [28, 30.8], 1.0, 0.3, 'straw', 3.2, 'neckstraw');
  ell([24.5, 25.5], 6.4, 6.6, 'burlap', 3.5, 'headd');
  cap([24.4, 20], [24.9, 15.6], 1.7, 0.6, 'burlap', 3.4, 'knot');
  cap([22.8, 19.4], [26.2, 19.2], 0.7, 0.7, 'rope', 3.45, 'knotrope');
  cap([15.6, 41.8], [24, 43], 0.95, 0.95, 'rope', 4, 'rope');
  cap([24, 43], [32.6, 41.4], 0.95, 0.95, 'rope', 4, 'rope');
  cap([17.4, 55.2], [24, 56.6], 0.95, 0.95, 'rope', 4, 'rope');
  cap([24, 56.6], [30.8, 55.4], 0.95, 0.95, 'rope', 4, 'rope');
  cap([19.6, 31.4], [29.6, 31.2], 0.9, 0.9, 'rope', 4, 'rope');
  ell([25.2, 48.5], 4.6, 4.8, 'paint', 3.6, 't1', { flat: 0.5 });
  ell([25.2, 48.5], 2.9, 3.0, 'white', 3.7, 't2', { flat: 0.5 });
  ell([25.2, 48.5], 1.3, 1.4, 'paint', 3.8, 't3', { flat: 0.5 });
  return parts;
}
function renderDummy(wobble, flash, S, mode) {
  const W = Math.round(48 * S), H = Math.round(82 * S);
  const parts = buildDummy(wobble);
  const ov = [];
  const X = [34, 22, 16];
  const eye = (cx, cy) => {
    const c = mul(T(DUMMY_BASE, wobble, [cx - DUMMY_BASE[0], cy - DUMMY_BASE[1]]), S), r = Math.round(S);
    for (let i = -r; i <= r; i++) { ov.push({ x: Math.round(c[0]) + i, y: Math.round(c[1]) + i, rgb: X }); ov.push({ x: Math.round(c[0]) + i, y: Math.round(c[1]) - i, rgb: X }); }
  };
  eye(22.6, 25.4); eye(27.6, 25.2);
  for (let i = 0; i < 4; i++) { const c = mul([21.8 + i * 1.9, 29], S); ov.push({ x: Math.round(c[0]), y: Math.round(c[1]), rgb: X }); }
  const r = renderModel(parts, { W, H, scale: S, mode: mode || 'pixel', faction: 'azure', overlays: ov });
  if (flash) tint(r.rgba, [255, 255, 255], flash);
  return Object.assign(r, { W, H });
}

// ---------- battlefield ----------
function mulberry(a) { return () => { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
function vnoise(x, y) {
  const xi = Math.floor(x), yi = Math.floor(y), xf = smooth(x - xi), yf = smooth(y - yi);
  const a = hash2(xi, yi), b = hash2(xi + 1, yi), c = hash2(xi, yi + 1), d = hash2(xi + 1, yi + 1);
  return lerp(lerp(a, b, xf), lerp(c, d, xf), yf);
}
function fbm(x, y) { return vnoise(x, y) * 0.55 + vnoise(x * 2.1, y * 2.1) * 0.3 + vnoise(x * 4.3, y * 4.3) * 0.15; }
const SKY = (() => {
  const k = ['#141c3c', '#1d2b56', '#293f72', '#37548c', '#4b6ea3', '#6889b4', '#8ca7c2', '#b3c4cf'].map(hexRGB), out = [];
  for (let i = 0; i < 15; i++) { const v = (i / 14) * 7, a = Math.floor(v), b = Math.min(7, a + 1), t = v - a; out.push([0, 1, 2].map((j) => Math.round(lerp(k[a][j], k[b][j], t)))); }
  return out;
})();
const GRASS = ['#1b3020', '#243f27', '#2e4f2f', '#3a6038', '#477141', '#56824a', '#689253', '#7fa35f'].map(hexRGB);

/* Background at W x H; the art is laid out on a 224 x 126 grid and scaled. */
function renderBattlefield(W, H) {
  const out = new Uint8ClampedArray(W * H * 4), k = W / 224;
  const put = (x, y, c) => { const i = (y * W + x) * 4; out[i] = c[0]; out[i + 1] = c[1]; out[i + 2] = c[2]; out[i + 3] = 255; };
  const HOR = 66 * k;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const bay = BAYER[(y & 3) * 4 + (x & 3)], u = x / k, v = y / k;
    if (y < HOR) {
      let t = Math.pow(y / HOR, 1.25) * (SKY.length - 1);
      const cl = fbm(u * 0.035 + 3, v * 0.09) - (1 - y / HOR) * 0.12;
      if (cl > 0.52) t += (cl - 0.52) * 9;
      let c = SKY[clamp(Math.floor(t + (bay - 0.5) * 0.8), 0, SKY.length - 1)];
      const rg = 1 - Math.abs(vnoise(u * 0.045, 3.3) * 2 - 1), rg2 = 1 - Math.abs(vnoise(u * 0.11, 8.1) * 2 - 1);
      const mtn = 54 - rg * rg * 16 - rg2 * 4 - fbm(u * 0.02, 1.7) * 6;
      const hill = 57 + fbm(u * 0.05 + 9, 4.2) * 7;
      const trees = 62 + fbm(u * 0.35 + 20, 2) * 3.2;
      if (v > mtn) {
        const kk = (v - mtn) / 12, lit = vnoise(u * 0.3, v * 0.15) > 0.5 && kk < 0.8;
        c = hexRGB(kk + bay * 0.2 < 0.3 ? (lit ? '#8a9ebb' : '#7489a8') : kk + bay * 0.3 < 0.8 ? '#64799a' : '#586d8e');
        if (mtn < 43 && v - mtn < (44 - mtn) * 0.45 + bay) c = hexRGB(lit || bay > 0.6 ? '#d9e2ec' : '#adbdd0');
      }
      if (v > hill) c = hexRGB((v - hill) + bay * 2 < 2 ? '#52746f' : '#456461');
      if (v > trees) c = hexRGB(fbm(u * 0.5, v * 0.5) + bay * 0.2 > 0.62 ? '#32513c' : '#243f2c');
      put(x, y, c);
    } else {
      const depth = (y - HOR) / (H - HOR);
      const z = 40 / (v - 66 + 4);
      const stripe = Math.floor(z * 3 + u * 0.004 * (1 - depth)) % 2 ? 0.35 : 0;
      const n = fbm(u * (0.18 - depth * 0.08), v * 0.45) * 2.2;
      const t = 6.6 - depth * 4.2 + stripe + n - 1.1;
      const ax = (u - 112) / 106, ay = (v - 113) / 11.5, worn = 1 - (ax * ax + ay * ay);
      let c = GRASS[clamp(Math.floor(t + bay - 0.5), 0, GRASS.length - 1)];
      if (worn > 0 && worn * 1.6 + fbm(u * 0.3, v * 0.6) - 0.55 > bay) c = hexRGB(t + bay > 4.3 ? '#857752' : t + bay > 3.2 ? '#716544' : '#5d5338');
      put(x, y, c);
    }
  }
  const rnd = mulberry(7);
  for (let i = 0; i < 520 * k * k; i++) {
    const y = Math.floor(HOR + 2 + Math.pow(rnd(), 0.8) * (H - HOR - 2)), x = Math.floor(rnd() * W);
    const depth = (y - HOR) / (H - HOR), hgt = Math.round((depth > 0.55 ? 2 : 1) * k);
    if (rnd() < 0.04 && depth < 0.7) { put(x, y, hexRGB(rnd() < 0.5 ? '#efe7c8' : '#e6c24e')); continue; }
    for (let j = 0; j < hgt; j++) if (y - j >= HOR) put(x, y - j, GRASS[clamp(Math.round(6 - depth * 3 + (rnd() < 0.5 ? 1 : -1)), 0, 7)]);
  }
  return out;
}

// ---------- compositing helpers ----------
const DIGITS = [
  '.###.#...##..###.#.###..##...#.###.', '..#...##....#....#....#....#...###.', '.###.#...#....#...#...#...#...#####',
  '####.....#....#.###.....#....#####.', '...#...##..#.#.#..#.#####...#....#.', '######....####.....#....##...#.###.',
  '..##..#...#....####.#...##...#.###.', '#####....#...#...#...#....#....#...', '.###.#...##...#.###.#...##...#.###.',
  '.###.#...##...#.####....#...#..##..',
];
function drawNumber(buf, W, H, str, cx, cy, fill, edge) {
  const cw = 6, x0 = Math.round(cx - (str.length * cw - 1) / 2), y0 = Math.round(cy);
  const on = (x, y) => {
    const i = Math.floor((x - x0) / cw), gx = x - x0 - i * cw, gy = y - y0;
    if (i < 0 || i >= str.length || gx > 4 || gy < 0 || gy > 6) return false;
    return DIGITS[+str[i]][gy * 5 + gx] === '#';
  };
  for (let y = y0 - 1; y <= y0 + 8; y++) for (let x = x0 - 1; x <= x0 + str.length * cw; x++) {
    if (x < 0 || y < 0 || x >= W || y >= H) continue;
    const k = (y * W + x) * 4;
    if (on(x, y)) { const c = y - y0 < 3 ? fill[0] : fill[1]; buf[k] = c[0]; buf[k + 1] = c[1]; buf[k + 2] = c[2]; }
    else if (on(x + 1, y) || on(x - 1, y) || on(x, y + 1) || on(x, y - 1) || on(x - 1, y - 1) || on(x, y - 2)) { buf[k] = edge[0]; buf[k + 1] = edge[1]; buf[k + 2] = edge[2]; }
  }
}
function blit(dst, DW, DH, src, SWd, SHd, ox, oy) {
  ox = Math.round(ox); oy = Math.round(oy);
  for (let y = 0; y < SHd; y++) {
    const dy = y + oy; if (dy < 0 || dy >= DH) continue;
    for (let x = 0; x < SWd; x++) {
      const dx = x + ox; if (dx < 0 || dx >= DW) continue;
      const s = (y * SWd + x) * 4; if (!src[s + 3]) continue;
      const d = (dy * DW + dx) * 4; dst[d] = src[s]; dst[d + 1] = src[s + 1]; dst[d + 2] = src[s + 2]; dst[d + 3] = 255;
    }
  }
}
function groundShadow(buf, W, cx, cy, rx, ry, k) {
  for (let y = Math.floor(cy - ry); y <= cy + ry; y++) for (let x = Math.floor(cx - rx); x <= cx + rx; x++) {
    const u = (x + 0.5 - cx) / rx, v = (y + 0.5 - cy) / ry, d = u * u + v * v; if (d > 1 || x < 0 || x >= W) continue;
    const f = d > 0.6 && BAYER[(y & 3) * 4 + (x & 3)] < 0.5 ? (1 + k) / 2 : k;
    const i = (y * W + x) * 4; buf[i] *= f; buf[i + 1] *= f; buf[i + 2] *= f * 1.06;
  }
}
function fillPoly(buf, W, H, pts, rgb, alphaTest) {
  let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
  for (const q of pts) { x0 = Math.min(x0, q[0]); y0 = Math.min(y0, q[1]); x1 = Math.max(x1, q[0]); y1 = Math.max(y1, q[1]); }
  const probe = { pts, bevel: 0 };
  for (let y = Math.max(0, Math.floor(y0)); y <= Math.min(H - 1, y1); y++) for (let x = Math.max(0, Math.floor(x0)); x <= Math.min(W - 1, x1); x++) {
    if (!samplePoly(probe, x + 0.5, y + 0.5)) continue;
    if (alphaTest && !alphaTest(x, y)) continue;
    const k = (y * W + x) * 4; buf[k] = rgb[0]; buf[k + 1] = rgb[1]; buf[k + 2] = rgb[2];
  }
}

if (typeof module !== 'undefined') module.exports = { T, rot, sub, mix2, unit, ik, clamp, lerp, star, TAU, tint, faceOverlay, renderKnight, buildKnight, renderModel, renderDummy, renderBattlefield, READY, FACTIONS, ANIMS, frameOf, sampleTrack, ATTACK, blit, groundShadow, drawNumber, fillPoly, BAYER, dirOf, add, mul };
