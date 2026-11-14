// The film pipeline in WebGL2, mirroring engine/src/main/java/.../FilmPipeline.kt pass for pass.
// Runs the shipped shader files unchanged (engine/src/main/assets/shaders), so tests here exercise
// the exact GLSL the phone runs. Executed inside the page by harness.js.
window.runFilm = async function (job) {
  const { shaders, image, lut, spec, adjust, outW, outH, tile, seed } = job;
  const canvas = document.createElement('canvas');
  canvas.width = 16; canvas.height = 16;
  const gl = canvas.getContext('webgl2', { preserveDrawingBuffer: true, antialias: false });
  if (!gl) throw new Error('no webgl2');

  const header = '#version 300 es\n';
  function compile(type, src) {
    const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s) + '\n' + src.split('\n').map((l, i) => (i + 1) + ': ' + l).join('\n'));
    return s;
  }
  function program(frag) {
    const p = gl.createProgram();
    gl.attachShader(p, compile(gl.VERTEX_SHADER, header + shaders['quad.vert']));
    gl.attachShader(p, compile(gl.FRAGMENT_SHADER, header + shaders['common.glsl'] + '\n' + shaders[frag]));
    gl.bindAttribLocation(p, 0, 'aPos');
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
    return p;
  }
  const P = { pre: program('prefilter.frag'), blur: program('blur.frag'), comp: program('composite.frag') };

  const quad = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, quad);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([0, 0, 1, 0, 0, 1, 1, 1]), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0);
  gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);

  // source image: row 0 = top at t = 0, mipmapped (as the Android export path)
  const img = new Image(); img.src = image;
  await img.decode();
  const W = img.naturalWidth, H = img.naturalHeight, aspect = W / H;
  const src = gl.createTexture();
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, src);
  gl.pixelStorei(gl.UNPACK_COLORSPACE_CONVERSION_WEBGL, gl.NONE);
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, img);
  gl.generateMipmap(gl.TEXTURE_2D);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  for (const w of [gl.TEXTURE_WRAP_S, gl.TEXTURE_WRAP_T]) gl.texParameteri(gl.TEXTURE_2D, w, gl.CLAMP_TO_EDGE);

  // colour LUT (.cube: red fastest) -> 3D texture, width = red
  const n = lut.size;
  const lutTex = gl.createTexture();
  gl.activeTexture(gl.TEXTURE4); gl.bindTexture(gl.TEXTURE_3D, lutTex);
  gl.texImage3D(gl.TEXTURE_3D, 0, gl.RGB16F, n, n, n, 0, gl.RGB, gl.FLOAT, new Float32Array(lut.data));
  for (const f of [gl.TEXTURE_MIN_FILTER, gl.TEXTURE_MAG_FILTER]) gl.texParameteri(gl.TEXTURE_3D, f, gl.LINEAR);
  for (const w of [gl.TEXTURE_WRAP_S, gl.TEXTURE_WRAP_T, gl.TEXTURE_WRAP_R]) gl.texParameteri(gl.TEXTURE_3D, w, gl.CLAMP_TO_EDGE);

  // Render targets are created on a scratch texture unit, never on a unit a pass samples from.
  function target(w, h) {
    const t = gl.createTexture();
    gl.activeTexture(gl.TEXTURE7);
    gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    for (const w2 of [gl.TEXTURE_WRAP_S, gl.TEXTURE_WRAP_T]) gl.texParameteri(gl.TEXTURE_2D, w2, gl.CLAMP_TO_EDGE);
    const fb = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
    return { t, fb, w, h };
  }
  const U = (p, name) => gl.getUniformLocation(p, name);
  function srcMap(p) {
    gl.uniform2f(U(p, 'uSrcO'), 0, 0); gl.uniform2f(U(p, 'uSrcX'), 1, 0); gl.uniform2f(U(p, 'uSrcY'), 0, 1);
    gl.uniform1i(U(p, 'uSrc'), 0);
  }
  function draw(p, t, tileRect) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, t.fb);
    gl.viewport(0, 0, t.w, t.h);
    gl.uniform4f(U(p, 'uTile'), ...tileRect);
    gl.uniform1f(U(p, 'uFlipY'), 0);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }
  const e = window.FilmParams.effective(spec, adjust);

  // glow chains, sized from their radius (identical at every output resolution)
  function chain(mode, radius, threshold, unit) {
    const g = window.FilmParams.chainSize(radius, aspect);
    const a = target(g.w, g.h), b = target(g.w, g.h);
    gl.useProgram(P.pre); srcMap(P.pre);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, src);
    gl.uniform1i(U(P.pre, 'uMode'), mode); gl.uniform1f(U(P.pre, 'uThreshold'), threshold);
    gl.uniform2f(U(P.pre, 'uTexel'), 1 / g.w, 1 / g.h);
    draw(P.pre, a, [0, 0, 1, 1]);
    gl.useProgram(P.blur); gl.uniform1i(U(P.blur, 'uGlow'), 1); gl.uniform1f(U(P.blur, 'uSigma'), g.sigma);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, a.t); gl.uniform2f(U(P.blur, 'uStep'), 1 / g.w, 0); draw(P.blur, b, [0, 0, 1, 1]);
    gl.bindTexture(gl.TEXTURE_2D, b.t); gl.uniform2f(U(P.blur, 'uStep'), 0, 1 / g.h); draw(P.blur, a, [0, 0, 1, 1]);
    gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, a.t);
  }
  chain(0, e.bloomRadius, e.bloomThreshold, 1);
  chain(1, e.halationRadius, e.halationThreshold, 2);
  chain(2, e.diffusionRadius, 0, 3);

  // composite, whole frame or in tiles (as the full-resolution export does)
  const out = new Uint8Array(outW * outH * 4);
  const ts = tile || Math.max(outW, outH);
  const p = P.comp;
  gl.useProgram(p); srcMap(p);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, src);
  gl.uniform1i(U(p, 'uBloomTex'), 1); gl.uniform1i(U(p, 'uHaloTex'), 2); gl.uniform1i(U(p, 'uDiffTex'), 3);
  gl.uniform1i(U(p, 'uLut'), 4);
  const sc = (n - 1) / n, off = 0.5 / n;
  gl.uniform3f(U(p, 'uLutScale'), sc, sc, sc); gl.uniform3f(U(p, 'uLutOffset'), off, off, off);
  gl.uniform1f(U(p, 'uAspect'), aspect);
  gl.uniform1f(U(p, 'uOutShortPx'), Math.min(outW, outH));
  gl.uniform2f(U(p, 'uOutSizePx'), outW, outH);
  gl.uniform1f(U(p, 'uSeed'), seed); gl.uniform1f(U(p, 'uGrainSeed'), seed);
  gl.uniform1f(U(p, 'uOutputBGRA'), 0);
  for (const [k, v] of Object.entries(e.uniforms)) {
    const loc = U(p, k);
    if (Array.isArray(v)) gl.uniform3f(loc, v[0], v[1], v[2]); else gl.uniform1f(loc, v);
  }
  for (let y0 = 0; y0 < outH; y0 += ts) for (let x0 = 0; x0 < outW; x0 += ts) {
    const tw = Math.min(ts, outW - x0), th = Math.min(ts, outH - y0);
    const t = target(tw, th);
    draw(p, t, [x0 / outW, y0 / outH, tw / outW, th / outH]);
    const buf = new Uint8Array(tw * th * 4);
    gl.readPixels(0, 0, tw, th, gl.RGBA, gl.UNSIGNED_BYTE, buf);
    for (let r = 0; r < th; r++) out.set(buf.subarray(r * tw * 4, (r + 1) * tw * 4), ((y0 + r) * outW + x0) * 4);
    gl.deleteFramebuffer(t.fb); gl.deleteTexture(t.t);
  }
  let bin = ''; const CH = 0x8000;
  for (let i = 0; i < out.length; i += CH) bin += String.fromCharCode.apply(null, out.subarray(i, i + CH));
  return { w: outW, h: outH, rgba: btoa(bin) };
};
