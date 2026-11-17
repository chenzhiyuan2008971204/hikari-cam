// Shared helpers. Every shader is assembled as: platform header + common.glsl + pass body.
// The platform header is "#version 300 es" plus, for the live camera, the external-image
// extension and "#define SOURCE_OES". The exact same files run on Android (GLES 3.0) and in the
// WebGL2 test harness (tools/engine), so what is tested is what ships.
precision highp float;
precision highp int;
precision highp sampler3D;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 toLinear(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

vec3 toDisplay(vec3 c) {
    c = max(c, 0.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

// pcg3d hash: well distributed, no visible periodicity (Jarzynski & Olano 2020).
uvec3 pcg3d(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v;
}

// Three uniform random numbers in [0,1) for an integer cell and a seed.
vec3 rand3(ivec2 cell, float seed) {
    uvec3 h = pcg3d(uvec3(uvec2(cell + 32768), uint(seed * 977.0) + 1u));
    return vec3(h) * (1.0 / 4294967296.0);
}

// Value noise with C2 interpolation, roughly zero mean and unit standard deviation.
float valueNoise(vec2 p, float seed) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    ivec2 c = ivec2(i);
    float a = rand3(c, seed).x;
    float b = rand3(c + ivec2(1, 0), seed).x;
    float d = rand3(c + ivec2(0, 1), seed).x;
    float e = rand3(c + ivec2(1, 1), seed).x;
    return (mix(mix(a, b, u.x), mix(d, e, u.x), u.y) - 0.5) * 3.6;
}

// Glow textures are 8-bit RGBA everywhere (no float render target needed on any device), so
// linear values are stored square-root encoded for precision in the dark end.
vec4 glowEncode(vec3 c) { return vec4(sqrt(clamp(c, 0.0, 1.0)), 1.0); }
vec3 glowDecode(vec4 t) { return t.rgb * t.rgb; }

// Maps image coordinates (0..1, origin top-left) to the source texture.
uniform vec2 uSrcO;
uniform vec2 uSrcX;
uniform vec2 uSrcY;
vec2 srcCoord(vec2 uv) { return uSrcO + uv.x * uSrcX + uv.y * uSrcY; }

#ifdef SOURCE_OES
precision highp samplerExternalOES;
uniform samplerExternalOES uSrc;
#else
uniform sampler2D uSrc;
#endif
vec3 srcLinear(vec2 uv) { return toLinear(texture(uSrc, srcCoord(uv)).rgb); }
