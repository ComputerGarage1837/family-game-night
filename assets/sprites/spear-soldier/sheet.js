const { frame, W, H } = require('./compose.js'); const { ANIM, RED } = require('./anims.js'); const { sheet, writePNG } = require('../png.js');
const pal = process.argv[2] === 'red' ? RED : null;
const rows = Object.entries(ANIM);
const cols = Math.max(...rows.map(([, f]) => f.length));
const SW = W * cols, SH = H * rows.length, buf = new Uint8ClampedArray(SW * SH * 4);
rows.forEach(([name, frames], r) => frames.forEach((f, c) => { const px = frame(f, pal); for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const s = (y * W + x) * 4, d = ((r * H + y) * SW + c * W + x) * 4; for (let k = 0; k < 4; k++) buf[d + k] = px[s + k]; } }));
writePNG(process.argv[3] || 'soldier_sheet.png', SW, SH, buf);
sheet(process.argv[4] || 'soldier_sheet_big.png', [{ rgba: buf, W: SW, H: SH }], 3, [44, 34, 30]);
console.log(rows.map(([n, f]) => n + ':' + f.length).join(' '));
