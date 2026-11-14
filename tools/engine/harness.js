// Runs the shipped GLSL film pipeline on images in headless Chromium (WebGL2 = GLSL ES 3.00).
//   node harness.js jobs.json
// jobs.json: [{ "in": "a.png", "out": "b.png", "filter": "<path to filter json>",
//              "adjust": {...}, "width": 1024, "tile": 0, "seed": 1 }]
// The filter's LUT path is resolved relative to engine/src/main/assets.
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const ROOT = path.resolve(__dirname, '../..');
const ASSETS = path.join(ROOT, 'engine/src/main/assets');

function readCube(file) {
  const data = []; let size = 0;
  for (const raw of fs.readFileSync(file, 'utf8').split('\n')) {
    const l = raw.trim();
    if (!l || l.startsWith('#') || l.startsWith('TITLE') || l.startsWith('DOMAIN')) continue;
    if (l.startsWith('LUT_3D_SIZE')) { size = parseInt(l.split(/\s+/)[1]); continue; }
    const v = l.split(/\s+/).map(Number);
    if (v.length === 3 && !v.some(isNaN)) data.push(v[0], v[1], v[2]);
  }
  if (data.length !== size * size * size * 3) throw new Error('bad cube ' + file);
  return { size, data };
}

(async () => {
  const jobs = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  const shaders = {};
  for (const f of ['common.glsl', 'quad.vert', 'prefilter.frag', 'blur.frag', 'composite.frag'])
    shaders[f] = fs.readFileSync(path.join(ASSETS, 'shaders', f), 'utf8');
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage();
  await page.addScriptTag({ content: fs.readFileSync(path.join(__dirname, 'params.js'), 'utf8') });
  await page.addScriptTag({ content: fs.readFileSync(path.join(__dirname, 'pipeline.js'), 'utf8') });
  const lutCache = {};
  for (const job of jobs) {
    const spec = JSON.parse(fs.readFileSync(job.filter, 'utf8'));
    const lutFile = path.isAbsolute(spec.lut) ? spec.lut : path.join(ASSETS, spec.lut);
    const lut = lutCache[lutFile] || (lutCache[lutFile] = readCube(lutFile));
    const image = 'data:image/png;base64,' + fs.readFileSync(job.in).toString('base64');
    const dims = await page.evaluate(async (src) => { const i = new Image(); i.src = src; await i.decode(); return [i.naturalWidth, i.naturalHeight]; }, image);
    const outW = job.width || dims[0];
    const outH = Math.round(outW * dims[1] / dims[0]);
    const png = await page.evaluate(async (j) => {
      const r = await window.runFilm(j);
      const c = document.createElement('canvas'); c.width = r.w; c.height = r.h;
      const ctx = c.getContext('2d');
      const bytes = Uint8ClampedArray.from(atob(r.rgba), ch => ch.charCodeAt(0));
      ctx.putImageData(new ImageData(bytes, r.w, r.h), 0, 0);
      return c.toDataURL('image/png').split(',')[1];
    }, { shaders, image, lut, spec, adjust: job.adjust || {}, outW, outH, tile: job.tile || 0, seed: job.seed || 1 });
    fs.writeFileSync(job.out, Buffer.from(png, 'base64'));
    process.stdout.write('.');
  }
  process.stdout.write('\n');
  await browser.close();
})().catch(e => { console.error(e.message || e); process.exit(1); });
