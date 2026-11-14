// Filter spec + user adjustments -> shader uniforms. Mirrors engine/.../FilmParams.kt; the Kotlin
// unit test compares both against fixtures written by gen_fixtures.js.
(function (root) {
  const or = (v, d) => (v === undefined || v === null ? d : v);
  const FilmParams = {
    chainSize(radius, aspect) {
      const r = Math.max(radius, 1e-4);
      const short = Math.min(512, Math.max(48, Math.round(4 / r)));
      const w = aspect >= 1 ? Math.round(short * aspect) : short;
      const h = aspect >= 1 ? short : Math.round(short / aspect);
      const sigma = Math.min(6, Math.max(0.5, r * short));
      return { w, h, sigma };
    },
    effective(spec, adj) {
      const a = adj || {};
      const g = spec.grain, b = spec.bloom, h = spec.halation, d = spec.diffusion, l = spec.lens, x = spec.defects;
      return {
        bloomRadius: b.radius, bloomThreshold: b.threshold,
        halationRadius: h.radius, halationThreshold: h.threshold,
        diffusionRadius: d.radius,
        uniforms: {
          uStrength: or(a.strength, 1), uLutAmount: or(spec.color.lutAmount, 1),
          uExposure: or(a.exposure, 0) + or(spec.color.exposure, 0),
          uTemp: or(a.temperature, 0), uTint: or(a.tint, 0),
          uContrast: or(a.contrast, 0), uSaturation: or(a.saturation, 0), uFade: or(a.fade, 0),
          uBloom: or(a.bloom, b.amount), uHalation: or(a.halation, h.amount), uHalationTint: h.tint,
          uSoftFocus: or(a.softFocus, d.softFocus), uMist: or(a.mist, d.mist),
          uVignette: or(a.vignette, l.vignette), uVignetteSoft: l.vignetteSoft,
          uAberration: or(a.aberration, l.aberration), uEdgeSoft: or(a.edgeSoft, l.edgeSoft),
          uGrain: or(a.grain, g.amount), uGrainSize: or(a.grainSize, g.size),
          uGrainChroma: g.chroma, uGrainRough: g.rough,
          uDust: or(a.dust, x.dust), uScratches: or(a.scratches, x.scratches), uLeak: or(a.leak, x.leak),
          uLeakColor: x.leakColor, uUneven: or(a.uneven, x.uneven), uDrift: or(a.drift, x.drift),
        },
      };
    },
  };
  root.FilmParams = FilmParams;
  if (typeof module !== 'undefined') module.exports = FilmParams;
})(typeof window !== 'undefined' ? window : globalThis);
