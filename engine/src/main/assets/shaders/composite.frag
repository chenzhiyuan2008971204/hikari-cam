// The film look, one pixel at a time. Order follows the physical process:
//   light hits the lens (vignette, aberration, softness) -> the film (halation, bloom, leaks,
//   uneven exposure) -> development / colour science (3D LUT, filmic) -> print (user grade,
//   dust, scratches) -> grain -> dither to 8 bits.
// Spatial parameters are fractions of the image's short side, so the look is the same at any
// output resolution; uOutShortPx says how many output pixels that short side covers.
in vec2 vUv;
out vec4 outColor;

uniform sampler2D uBloomTex;
uniform sampler2D uHaloTex;
uniform sampler2D uDiffTex;
uniform sampler3D uLut;
uniform vec3 uLutScale;
uniform vec3 uLutOffset;

uniform float uAspect;      // image width / height
uniform float uOutShortPx;  // output pixels across the image's short side
uniform vec2 uOutSizePx;    // output pixels across the whole image (not the tile)
uniform float uSeed;        // per image: dust, scratches, leaks
uniform float uGrainSeed;   // per frame on the live camera, = uSeed for stills
uniform float uStrength;    // filter strength 0..1
uniform float uOutputBGRA;  // 1: write BGRA so a little-endian int read is Android ARGB

// colour
uniform float uLutAmount;
uniform float uExposure;
uniform float uTemp;
uniform float uTint;
uniform float uContrast;
uniform float uSaturation;
uniform float uFade;
uniform float uHighlights;  // -1..1 tone regions, applied before development
uniform float uShadows;
uniform float uBlacks;
uniform float uWhites;
uniform float uSharpness;   // 0..1 capture sharpening (luminance only, halo-limited)
// glow
uniform float uBloom;
uniform float uHalation;
uniform vec3 uHalationTint;
uniform float uSoftFocus;
uniform float uMist;
// lens
uniform float uVignette;
uniform float uVignetteSoft;
uniform float uAberration;
uniform float uEdgeSoft;
// grain
uniform float uGrain;
uniform float uGrainSize;
uniform float uGrainChroma;
uniform float uGrainRough;
// defects
uniform float uDust;
uniform float uScratches;
uniform float uLeak;
uniform vec3 uLeakColor;
uniform float uUneven;
uniform float uDrift;

// Global output pixel index (not the fragment position: tiles of a big export would repeat it).
ivec2 pixelId(vec2 uv) { return ivec2(floor(uv * uOutSizePx)); }

vec2 toAspect(vec2 uv) { return uAspect >= 1.0 ? vec2(uv.x * uAspect, uv.y) : vec2(uv.x, uv.y / uAspect); }

// distance from the centre, 1.0 at the corners
float radial(vec2 uv) {
    vec2 d = toAspect(uv - 0.5);
    vec2 corner = toAspect(vec2(0.5));
    return length(d) / length(corner);
}

vec3 sampleLens(vec2 uv, float ca) {
    if (ca <= 0.0) return srcLinear(uv);
    vec2 d = uv - 0.5;
    float r = srcLinear(0.5 + d * (1.0 + ca)).r;
    float g = srcLinear(uv).g;
    float b = srcLinear(0.5 + d * (1.0 - ca)).b;
    return vec3(r, g, b);
}

// One grain octave. cellPx = size of a grain cell in output pixels. When a cell is smaller than
// a pixel the pattern cannot be drawn; averaging k*k cells per pixel lowers the deviation by k,
// so the preview gets pixel-sized noise at the amplitude the downscaled full-size export has.
float grainOctave(vec2 imgPos, float cellImg, float cellPx, float seed) {
    if (cellPx >= 1.0) return valueNoise(imgPos / cellImg, seed);
    vec3 r = rand3(pixelId(vUv), seed + 0.37);
    // sum of uniforms ~ gaussian, unit deviation
    return ((r.x + r.y + r.z) - 1.5) * 2.0 * cellPx;
}

float grainField(vec2 imgPos, float size, float seed) {
    float px = size * uOutShortPx;
    float a = grainOctave(imgPos, size, px, seed);
    float b = grainOctave(imgPos + 17.1, size * 2.1, px * 2.1, seed + 1.0);
    float c = grainOctave(imgPos + 41.7, size * 4.3, px * 4.3, seed + 2.0);
    // rough grain: more weight on the coarse clumps
    float w1 = mix(0.80, 0.55, uGrainRough);
    float w2 = mix(0.17, 0.33, uGrainRough);
    float w3 = 1.0 - w1 - w2;
    return (a * w1 + b * w2 + c * w3) / sqrt(w1 * w1 + w2 * w2 + w3 * w3);
}

// Grain is strongest in the mid tones, weaker in deep shadows and bright highlights.
float grainResponse(float y) {
    return 0.18 + 0.82 * smoothstep(0.02, 0.28, y) * (1.0 - 0.65 * smoothstep(0.55, 1.0, y));
}

float dust(vec2 imgPos, float density, float seed) {
    if (density <= 0.0) return 0.0;
    float cell = 0.035;
    vec2 g = imgPos / cell;
    ivec2 c0 = ivec2(floor(g));
    float v = 0.0;
    for (int j = -1; j <= 1; j++)
        for (int i = -1; i <= 1; i++) {
            ivec2 c = c0 + ivec2(i, j);
            vec3 r = rand3(c, seed + 11.0);
            if (r.x > density * 0.18) continue;
            vec3 r2 = rand3(c, seed + 12.0);
            vec2 centre = (vec2(c) + r2.xy) * cell;
            float rad = mix(0.0006, 0.0022, r2.z);
            // slightly irregular specks: stretch along a random direction
            float ang = r.y * 6.2832;
            vec2 d = imgPos - centre;
            d = vec2(cos(ang) * d.x + sin(ang) * d.y, -sin(ang) * d.x + cos(ang) * d.y);
            d.x *= mix(1.0, 2.6, r.z);
            v = max(v, (1.0 - smoothstep(rad * 0.6, rad, length(d))) * mix(0.35, 0.8, r2.x));
        }
    return v;
}

float scratches(vec2 uv, float amount, float seed) {
    if (amount <= 0.0) return 0.0;
    float v = 0.0;
    for (int k = 0; k < 3; k++) {
        vec3 r = rand3(ivec2(k, 7), seed + 21.0);
        if (r.z > amount) continue;
        float x = r.x + 0.004 * sin(uv.y * 9.0 + r.y * 20.0);
        float y0 = r.y * 0.6;
        float len = 0.25 + 0.5 * rand3(ivec2(k, 9), seed).x;
        float along = smoothstep(y0, y0 + 0.03, uv.y) * (1.0 - smoothstep(y0 + len - 0.03, y0 + len, uv.y));
        float wpx = 0.00035 * uAspect;
        v = max(v, along * (1.0 - smoothstep(wpx * 0.4, wpx, abs(uv.x - x))) * 0.6);
    }
    return v;
}

vec3 lightLeak(vec2 uv, float amount, float seed) {
    if (amount <= 0.0) return vec3(0.0);
    vec3 r = rand3(ivec2(3, 5), seed + 31.0);
    // from one edge, a soft elongated wash
    vec2 p = toAspect(uv);
    vec2 edge = r.x < 0.5 ? toAspect(vec2(r.x < 0.25 ? -0.05 : 1.05, 0.2 + 0.6 * r.y))
                          : toAspect(vec2(0.2 + 0.6 * r.y, r.x < 0.75 ? -0.05 : 1.05));
    float d = length((p - edge) * vec2(1.0, 0.6 + r.z));
    float core = exp(-d * d * 16.0);
    float halo = exp(-d * d * 5.0) * 0.18;
    return uLeakColor * (core + halo) * amount;
}

vec3 sampleLut(vec3 c) {
    return texture(uLut, clamp(c, 0.0, 1.0) * uLutScale + uLutOffset).rgb;
}

void main() {
    vec2 uv = vUv;
    float s = clamp(uStrength, 0.0, 1.0);
    // How each part follows the strength slider. Colour mixes linearly; glow and lens energy too;
    // grain keeps its texture longer (square root) so half strength still reads as film; defects
    // fade faster so they never dominate a subtle setting.
    float sColour = s, sGlow = s, sLens = pow(s, 1.2), sGrain = sqrt(s), sDefect = s * s;

    float rr = radial(uv);
    vec3 lin = sampleLens(uv, uAberration * sLens * 0.004 * rr * rr);

    // capture sharpening: luminance detail against the 4 neighbours at a radius fixed in image
    // units (so preview and export agree), soft-limited so edges never grow halos
    if (uSharpness > 0.0) {
        float rad = 0.00075;
        vec2 o = uAspect >= 1.0 ? vec2(rad / uAspect, rad) : vec2(rad, rad * uAspect);
        float yc = dot(toDisplay(lin), LUMA);
        float yn = (dot(toDisplay(srcLinear(uv + vec2(o.x, 0.0))), LUMA) + dot(toDisplay(srcLinear(uv - vec2(o.x, 0.0))), LUMA)
                  + dot(toDisplay(srcLinear(uv + vec2(0.0, o.y))), LUMA) + dot(toDisplay(srcLinear(uv - vec2(0.0, o.y))), LUMA)) * 0.25;
        float d = yc - yn;
        d = 0.08 * d / (0.08 + abs(d));           // soft limit: big edges get less
        float y2 = clamp(yc + d * uSharpness * 1.6, 0.0, 1.0);
        lin *= clamp(pow(max(y2, 1e-4) / max(yc, 1e-4), 2.2), 0.6, 1.6);
    }

    // white balance and exposure in linear light (user controls, not scaled by strength)
    vec3 wb = vec3(1.0 + 0.10 * uTemp + 0.03 * uTint, 1.0 - 0.06 * uTint, 1.0 - 0.10 * uTemp + 0.03 * uTint);
    wb /= dot(wb, LUMA);
    lin *= wb * exp2(uExposure);

    // softness: centre-weighted soft focus, plus extra softness towards the edges of the lens
    vec3 diff = glowDecode(texture(uDiffTex, uv)) * wb * exp2(uExposure);
    float soft = clamp(uSoftFocus * sGlow + uEdgeSoft * sLens * smoothstep(0.35, 1.0, rr), 0.0, 1.0);
    lin = mix(lin, diff, soft * 0.6);
    lin += diff * uMist * sGlow * 0.35;

    // film: halation (red), bloom (colour), light leak and uneven exposure
    float halo = glowDecode(texture(uHaloTex, uv)).r;
    // the gain grows with the setting (1.6 at the low end, 4.0 at full), so the upper half of the slider is clearly visible
    vec3 glow = uHalationTint * halo * uHalation * sGlow * (1.6 + 2.4 * uHalation)
              + glowDecode(texture(uBloomTex, uv)) * uBloom * sGlow * 1.2;
    // Add the glow without ever passing white and without bleaching colour: the added light is
    // scaled as a whole by the room left under the pixel's brightest channel, so a saturated
    // highlight (a sunset) keeps its hue instead of its other channels catching up to white.
    // A pixel with no glow is left exactly as it was.
    float room = max(1.0 - max(lin.r, max(lin.g, lin.b)), 0.0);
    float gmax = max(glow.r, max(glow.g, glow.b));
    lin += glow * (room / (room + gmax + 1e-5));
    vec2 imgPos = toAspect(uv);
    lin *= 1.0 + uUneven * sDefect * 0.18 * valueNoise(imgPos * 1.6, uSeed + 5.0) / 3.6;
    lin += lightLeak(uv, uLeak * sDefect, uSeed) * (1.0 - 0.5 * dot(lin, LUMA));

    // optical vignette: cos^4-like falloff, softness controls how far in it starts
    float v = smoothstep(mix(0.55, 0.15, uVignetteSoft), 1.15, rr);
    lin *= 1.0 - uVignette * sLens * 0.75 * v * v;

    // development: the filter's colour science
    vec3 disp = toDisplay(lin);

    // tone regions (user): shadows / highlights / black point / white point on luminance, before
    // the look's curve so the film character stays on top. Amplitudes keep the mapping monotone.
    if (uHighlights != 0.0 || uShadows != 0.0 || uBlacks != 0.0 || uWhites != 0.0) {
        float y = clamp(dot(disp, LUMA), 0.0, 1.0);
        float y2 = y + uShadows * 0.11 * 6.75 * y * (1.0 - y) * (1.0 - y)
                     + uHighlights * 0.11 * 6.75 * y * y * (1.0 - y)
                     + uBlacks * 0.08 * (1.0 - y) * (1.0 - y) * (1.0 - y)
                     + uWhites * 0.08 * y * y * y;
        y2 = clamp(y2, 0.0, 1.0);
        vec3 add = disp + (y2 - y);
        vec3 gain = disp * (y2 / max(y, 1e-3));
        disp = clamp(mix(add, gain, smoothstep(0.02, 0.12, y)), 0.0, 1.0);
    }
    vec3 graded = sampleLut(disp);
    disp = mix(disp, graded, uLutAmount * sColour);

    // user grade on the print: contrast keeps black and white fixed, saturation keeps luminance
    if (uContrast != 0.0) {
        float k = clamp(uContrast, -0.9, 2.0);
        vec3 d = disp - 0.5;
        disp = 0.5 + d * (1.0 + k) / (1.0 + k * abs(2.0 * d));
    }
    if (uSaturation != 0.0) {
        float y = dot(disp, LUMA);
        disp = clamp(y + (disp - y) * (1.0 + uSaturation), 0.0, 1.0);
    }
    if (uFade > 0.0) disp = mix(disp, 0.06 + disp * 0.94, uFade) + uFade * 0.05 * (1.0 - disp) * (1.0 - disp);

    // slow colour drift across the frame, like an uneven print
    if (uDrift > 0.0) {
        vec2 q = imgPos * 1.2;
        disp += uDrift * sDefect * 0.025 * vec3(valueNoise(q, uSeed + 7.0), 0.0, valueNoise(q, uSeed + 8.0)) / 3.6;
    }

    // dust and scratches on the negative print light
    float specks = max(dust(imgPos, uDust * sDefect, uSeed), scratches(uv, uScratches * sDefect, uSeed));
    disp = mix(disp, vec3(0.97, 0.96, 0.93), specks);

    // grain
    if (uGrain > 0.0) {
        float size = max(uGrainSize, 0.0002);
        float y = dot(disp, LUMA);
        float g = grainField(imgPos, size, uGrainSeed);
        vec3 chroma = vec3(0.0);
        if (uGrainChroma > 0.0) {
            chroma = vec3(grainField(imgPos + 3.3, size * 0.9, uGrainSeed + 4.0),
                          grainField(imgPos + 6.6, size * 0.9, uGrainSeed + 5.0),
                          grainField(imgPos + 9.9, size * 0.9, uGrainSeed + 6.0));
            chroma -= dot(chroma, vec3(1.0 / 3.0));
        }
        float amp = uGrain * sGrain * 0.075 * grainResponse(y);
        disp += amp * (g + uGrainChroma * chroma);
    }

    // triangular dither: removes 8-bit banding in skies and soft gradients
    vec3 r = rand3(pixelId(uv), uGrainSeed + 91.0);
    disp += (r.x + r.y - 1.0) / 255.0;

    disp = clamp(disp, 0.0, 1.0);
    outColor = uOutputBGRA > 0.5 ? vec4(disp.bgr, 1.0) : vec4(disp, 1.0);
}
