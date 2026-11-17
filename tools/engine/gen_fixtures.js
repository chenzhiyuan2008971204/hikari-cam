// Writes engine/src/test/resources/params_fixtures.json: what params.js computes for every bundled
// filter under a few adjustments. FilmParamsTest checks the Kotlin FilmParams gives the same.
const fs = require('fs');
const path = require('path');
const FP = require('./params.js');
const ROOT = path.resolve(__dirname, '../..');
const F = path.join(ROOT, 'engine/src/main/assets/filters');
const ids = JSON.parse(fs.readFileSync(path.join(F, 'index.json'), 'utf8'));
const adjusts = [{}, { aberration: 0, softFocus: 0.3, mist: 0, edgeSoft: 0.2, dust: 0.5, scratches: 0, leak: 0.4, uneven: 0, drift: 0.1 }, { strength: 0.5, grain: 0.1, grainSize: 0.002, halation: 0.3, bloom: 0.2, vignette: 0.4,
  fade: 0.2, exposure: 0.3, temperature: -0.4, tint: 0.2, contrast: 0.25, saturation: -0.3,
  highlights: -0.5, shadows: 0.4, blacks: 0.1, whites: -0.2, sharpness: 0.35 }];
const out = [];
for (const id of ids) {
  const spec = JSON.parse(fs.readFileSync(path.join(F, id + '.json'), 'utf8'));
  for (const adjust of adjusts) {
    const e = FP.effective(spec, adjust);
    out.push({ id, adjust, effective: e,
      chains: [1.6, 0.75].map(aspect => ({ aspect, bloom: FP.chainSize(e.bloomRadius, aspect),
        halation: FP.chainSize(e.halationRadius, aspect), diffusion: FP.chainSize(e.diffusionRadius, aspect) })) });
  }
}
fs.writeFileSync(path.join(ROOT, 'engine/src/test/resources/params_fixtures.json'), JSON.stringify(out, null, 1));
console.log('fixtures', out.length);
