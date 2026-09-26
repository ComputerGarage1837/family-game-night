// Render every animation into a sprite sheet PNG (one row per animation) for each team.
const C = require('./chibi2.js'); const { ANIM2 } = require('./chibi2_anims.js'); const { writePNG, sheet } = require('./png.js'); const fs = require('fs');
const names = Object.keys(ANIM2), cols = Math.max(...names.map((n) => ANIM2[n].length));
const meta = { frameW: C.CW, frameH: C.CH, foot: [68, 104], anims: {} };
for (const team of ['azure', 'crimson']) {
  const SW = C.CW * cols, SH = C.CH * names.length, buf = new Uint8ClampedArray(SW * SH * 4);
  names.forEach((n, r) => ANIM2[n].forEach((q, c) => {
    const px = C.renderChibi(q, { mode: 'pixel', faction: team }).rgba;
    if (q.alpha != null) for (let i = 3; i < px.length; i += 4) px[i] = Math.round(px[i] * q.alpha);
    for (let y = 0; y < C.CH; y++) for (let x = 0; x < C.CW; x++) { const s = (y * C.CW + x) * 4, d = ((r * C.CH + y) * SW + c * C.CW + x) * 4; for (let k = 0; k < 4; k++) buf[d + k] = px[s + k]; }
    meta.anims[n] = { row: r, ms: ANIM2[n].map((q2) => q2.ms), hit: n === 'attack' ? 3 : undefined };
  }));
  writePNG(`chibi2_${team}.png`, SW, SH, buf);
  if (team === 'azure') sheet('chibi2_sheet_view.png', [{ rgba: buf, W: SW, H: SH }], 1, [40, 30, 26]);
}
fs.writeFileSync('chibi2_meta.json', JSON.stringify(meta));
console.log(names.map((n) => n + ':' + ANIM2[n].length).join(' '));
