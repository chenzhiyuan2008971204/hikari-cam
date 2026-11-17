// Builds a low-resolution glow source from the image.
//   mode 0: bloom     - highlights above uThreshold, keeping their colour
//   mode 1: halation  - bright luminance only; tinted red later, like light bouncing off the film base
//   mode 2: diffusion - the whole image, for soft focus and mist
in vec2 vUv;
out vec4 outColor;
uniform int uMode;
uniform float uThreshold;
uniform vec2 uTexel; // one output texel in image coordinates
void main() {
    // 2x2 area average; a mipmapped source also gets the right level from the derivatives.
    vec3 c = 0.25 * (srcLinear(vUv + uTexel * vec2(-0.25, -0.25)) + srcLinear(vUv + uTexel * vec2(0.25, -0.25))
                   + srcLinear(vUv + uTexel * vec2(-0.25, 0.25)) + srcLinear(vUv + uTexel * vec2(0.25, 0.25)));
    if (uMode == 2) { outColor = glowEncode(c); return; }
    float y = dot(c, LUMA);
    // soft knee so the glow starts gradually instead of switching on at a hard edge
    float knee = 0.12;
    float w = clamp((y - uThreshold + knee) / (2.0 * knee), 0.0, 1.0);
    float over = max(y - uThreshold, 0.0) + knee * w * w;
    float k = over / max(y, 1e-4);
    outColor = uMode == 0 ? glowEncode(c * k) : glowEncode(vec3(y * k));
}
