// Separable Gaussian, 15 taps. uStep = one texel along the blur direction (image coordinates).
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uGlow;
uniform vec2 uStep;
uniform float uSigma; // in texels
void main() {
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (int i = -7; i <= 7; i++) {
        float x = float(i);
        float w = exp(-0.5 * x * x / (uSigma * uSigma));
        acc += w * glowDecode(texture(uGlow, vUv + uStep * x));
        wsum += w;
    }
    outColor = glowEncode(acc / wsum);
}
