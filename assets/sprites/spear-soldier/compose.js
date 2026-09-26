'use strict';
/* Compose a frame from hand-drawn parts, add the 1px outline. Frame is 64 x 48; feet rest on y = 41. */
const { PAL, HEAD, TORSO, ARM_BACK, LEGS, ARMS, TIPS } = require('./parts.js');
const hx = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const W = 76, H = 48;
function frame(f, pal) {
  pal = Object.assign({}, PAL, pal || {});
  let buf = new Array(W * H).fill(null);
  const set = (x, y, ch) => { if (x >= 0 && y >= 0 && x < W && y < H) buf[y * W + x] = ch; };
  const stamp = (grid, x0, y0) => grid.forEach((row, y) => [...row].forEach((ch, x) => { if (ch !== '.') set(x0 + x, y0 + y, ch); }));
  const bx = 22 + (f.x || 0), by = 26 + (f.y || 0) + (f.bob || 0);
  const arm = ARMS[f.arm || 'down'], ax = bx + arm.at[0] + (f.armX || 0), ay = by + arm.at[1] + (f.armY || 0);
  const hand = [ax + arm.hand[0], ay + arm.hand[1]];
  function spear() {
    const tip = TIPS[f.spear || 'up'], d = tip.d, L1 = f.reach || 16, L2 = f.butt || 7;
    const len = Math.max(Math.abs(d[0]), Math.abs(d[1]));
    const hxp = Math.round(hand[0]), hyp = Math.round(hand[1]);
    for (let i = -L2; i <= L1; i++) {
      const x = hxp + d[0] * i, y = hyp + d[1] * i; set(x, y, 'W');
      if (d[1] === 0) set(x, y + 1, 'V'); else if (d[0] === 0) set(x + 1, y, 'V'); else set(x, y + 1, 'V');
    }
    stamp(tip.g, hxp + d[0] * (L1 + 1) - tip.a[0], hyp + d[1] * (L1 + 1) - tip.a[1]);
  }
  stamp(ARM_BACK, bx - 3 + (f.backX || 0), by + 1 + (f.backY || 0));
  if (f.spearBehind) spear();
  stamp(LEGS[f.legs || 'stand'], bx + 2 + (f.legX || 0), 26 + 9 + (f.y || 0) + (f.legY || 0));
  stamp(TORSO, bx, by);
  stamp(HEAD, bx + (f.headX || 0), by - 15 + (f.headY || 0));
  if (f.blink) { const hx0 = bx + (f.headX || 0), hy0 = by - 15 + (f.headY || 0); for (const ex of [3, 4, 10, 11]) { set(hx0 + ex, hy0 + 9, 'S'); set(hx0 + ex, hy0 + 10, 'k'); } }
  if (f.ouch) { const hx0 = bx + (f.headX || 0), hy0 = by - 15 + (f.headY || 0); for (const [x, y] of [[3, 9], [4, 10], [4, 9], [3, 10]]) set(hx0 + x, hy0 + y, (x + y) % 2 ? 'k' : 'S'); for (const [x, y] of [[10, 9], [11, 10], [11, 9], [10, 10]]) set(hx0 + x, hy0 + y, (x + y) % 2 ? 'S' : 'k'); set(hx0 + 7, hy0 + 12, 'k'); set(hx0 + 8, hy0 + 12, 'k'); }
  if (!f.noSpear && !f.spearBehind) spear();
  stamp(arm.g, ax, ay);
  // the fist wraps the shaft: redraw the fist rows on top
  if (!f.noSpear) { const hr = Math.round(hand[1]) - ay; stamp(arm.g.slice(Math.max(0, hr - 1), hr + 2), ax, ay + Math.max(0, hr - 1)); }
  if (f.rot) { // 90-degree turn for falling over (exact for pixel art)
    const nb = new Array(W * H).fill(null), cx = f.rot.cx, cy = f.rot.cy;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const ch = buf[y * W + x]; if (!ch) continue; const nx = cx + (y - cy), ny = cy - (x - cx) + (f.rot.dy || 0); if (nx >= 0 && ny >= 0 && nx < W && ny < H) nb[ny * W + nx] = ch; }
    buf = nb;
  }
  const out = new Uint8ClampedArray(W * H * 4), O = hx(pal.O);
  for (let i = 0; i < W * H; i++) if (buf[i]) { const c = hx(pal[buf[i]]); out.set([c[0], c[1], c[2], 255], i * 4); }
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (buf[y * W + x]) continue;
    if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => { const xx = x + dx, yy = y + dy; return xx >= 0 && yy >= 0 && xx < W && yy < H && buf[yy * W + xx]; })) out.set([O[0], O[1], O[2], 255], (y * W + x) * 4);
  }
  if (f.flash) for (let i = 0; i < out.length; i += 4) if (out[i + 3]) for (let c = 0; c < 3; c++) out[i + c] += (255 - out[i + c]) * f.flash;
  if (f.alpha != null) for (let i = 3; i < out.length; i += 4) out[i] = Math.round(out[i] * f.alpha);
  return out;
}
module.exports = { frame, W, H };
