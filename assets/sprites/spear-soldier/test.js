const { frame, W, H } = require('./compose.js'); const { sheet } = require('../png.js');
const frames = JSON.parse(process.argv[2] || '[{}]');
sheet(process.argv[3] || 'chibi.png', frames.map((f) => ({ rgba: frame(f), W, H })), +(process.argv[4] || 8), [44, 34, 30]);
