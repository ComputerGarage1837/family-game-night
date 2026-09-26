'use strict';
/* Poses per frame (ms = hold time). Units are pixels in the character's own space. */
const { C_IDLE: I } = require('./chibi2.js');
const f = (o, ms) => Object.assign({}, I, o, { ms });
const ANIM2 = {
  idle: [f({}, 240), f({ bob: 1 }, 240), f({ bob: 1, blink: 1 }, 160), f({}, 240)],
  walk: [
    f({ footL: [37, 84], bob: -1, offFist: [23, 56] }, 120), f({ footL: [38, 86], footR: [43, 86] }, 110), f({ footR: [48, 84], bob: -1, offFist: [19, 57] }, 120),
    f({ footR: [49, 86], footL: [31, 86] }, 110),
  ],
  attack: [
    f({ x: -2, lean: -3, bob: 1, footR: [44, 86] }, 130),
    f({ spearArm: 'thrust', spearX: 52, x: -3, lean: -3, bob: 1 }, 140),
    f({ spearArm: 'thrust', spearX: 64, x: 5, lean: 5, footR: [54, 86], footL: [36, 86] }, 70),
    f({ spearArm: 'thrust', spearX: 70, x: 8, lean: 6, footR: [56, 86], footL: [37, 86] }, 200),
    f({ spearArm: 'thrust', spearX: 58, x: 3, lean: 2 }, 130),
    f({}, 120),
  ],
  hurt: [f({ flash: 0.85, ouch: 1, lean: -7, x: -3 }, 70), f({ ouch: 1, lean: -6, x: -3 }, 230), f({ lean: -2, x: -1 }, 130)],
  death: [
    f({ flash: 0.85, ouch: 1, lean: -7, x: -3 }, 70),
    f({ ouch: 1, lean: -8, x: -4, bob: 3 }, 200),
    f({ ouch: 1, rot: -0.75, dropY: -1, fallX: 10 }, 120),
    f({ ouch: 1, rot: -1.5, dropY: -5, fallX: 34 }, 560),
    f({ ouch: 1, rot: -1.5, dropY: -5, fallX: 34, alpha: 0.5 }, 160),
    f({ ouch: 1, rot: -1.5, dropY: -5, fallX: 34, alpha: 0 }, 400),
  ],
  victory: [f({ spearArm: 'raise' }, 200), f({ spearArm: 'raise', bob: -3, footL: [33, 84], footR: [46, 84] }, 170), f({ spearArm: 'raise' }, 200), f({ spearArm: 'raise', bob: -1 }, 170)],
};
module.exports = { ANIM2 };
