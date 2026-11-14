"""The look model: everything a colour look can do that is a pure per-pixel colour transform.

A look is a plain dict (see presets.py). `apply(look, rgb)` maps display sRGB in [0,1] to
display sRGB in [0,1]. Spatial effects (grain, vignette, halation, bloom, leaks) are not part of
the LUT; the app's shader does those, driven by the preset's `fx` values.

Pipeline (each step is optional):
  1. exposure / white balance        linear light
  2. mono channel mixer              B&W from weighted linear RGB
  3. per-hue hue / sat / lightness   OKLab, 8 hue anchors with smooth partition of unity
  4. saturation, vibrance            OKLab chroma
  5. tone curve                      on lightness, points given in display (gray sRGB) values
  6. saturation by brightness        desaturate shadows / highlights like film
  7. split toning                    OKLab a/b offsets for shadows, mids, highlights
  8. gamut map to sRGB               soft pull toward same-luminance grey (closed form)
  9. per-channel RGB curves          film crossover, in display values
 10. output levels                   black point lift (fade) and white cap, per channel
Near-white is kept neutral by `clean` (0..1, default 0.85) so toned looks still have clean whites.
"""
import numpy as np

from colorlib import (LUMA, gamut_excess, gamut_map, linear_to_oklab, linear_to_srgb, pchip,
                      smoothstep, srgb_to_linear)

# OKLab hue angles (degrees) of the eight bands. Skin sits between red and orange (~45-65).
ANCHORS = {
    "red": 25, "orange": 58, "yellow": 100, "green": 138,
    "aqua": 195, "blue": 248, "purple": 292, "magenta": 335,
}
_NAMES = list(ANCHORS)
_ANG = np.array([ANCHORS[n] for n in _NAMES], float)


def _hue_weights(h):
    """Per-anchor weights (..., 8) that sum to 1, smooth across the circle."""
    w = np.zeros(h.shape + (8,))
    for i in range(8):
        a0, a1 = _ANG[i], _ANG[(i + 1) % 8]
        span = (a1 - a0) % 360
        t = ((h - a0) % 360) / span
        inside = t <= 1
        s = np.where(inside, smoothstep(0, 1, t), 0)
        w[..., i] += np.where(inside, 1 - s, 0)
        w[..., (i + 1) % 8] += s
    return w


def _disp_to_L(v):
    """Display gray value -> OKLab L."""
    return np.cbrt(srgb_to_linear(np.asarray(v, float)))


def _tone(points):
    xs, ys = zip(*points)
    return pchip(_disp_to_L(xs), _disp_to_L(ys))


def _ab(hue_deg, amount):
    r = np.radians(hue_deg)
    return np.array([np.cos(r), np.sin(r)]) * amount


def apply(look, rgb):
    rgb = np.asarray(rgb, float)
    lin = srgb_to_linear(rgb)

    # 1. exposure and white balance (luminance preserving gains)
    lin = lin * 2.0 ** look.get("exposure", 0.0)
    t, g = look.get("temp", 0.0), look.get("tint", 0.0)
    if t or g:
        gains = np.array([1 + 0.12 * t + 0.04 * g, 1 - 0.08 * g, 1 - 0.12 * t + 0.04 * g])
        gains /= gains @ LUMA
        lin = lin * gains

    lab = linear_to_oklab(np.clip(lin, 0, None))
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]

    # 2. black and white: luminance from a channel mixer (like a coloured lens filter)
    mono = look.get("mono")
    if mono is not None:
        w = np.array(mono, float)
        w = w / w.sum()
        L = np.cbrt(np.clip(lin @ w, 0, None))
        a = np.zeros_like(L)
        b = np.zeros_like(L)

    C = np.hypot(a, b)
    # how close each input colour already is to the edge of the sRGB gamut (1 = on the edge)
    C_in = C.copy()
    edge = gamut_excess(np.clip(lin, 0, None))[0]
    h = np.degrees(np.arctan2(b, a)) % 360

    # 3. per-hue adjustments: name -> (hue shift deg, sat delta, lightness delta)
    hsl = look.get("hsl", {})
    if hsl and mono is None:
        tab = np.array([hsl.get(n, (0, 0, 0)) for n in _NAMES], float)
        w = _hue_weights(h)
        dh, ds, dl = (w @ tab[:, 0]), (w @ tab[:, 1]), (w @ tab[:, 2])
        colourful = smoothstep(0.015, 0.08, C)
        h = (h + dh * colourful) % 360
        C = C * np.clip(1 + ds, 0, None)
        L = L + dl * colourful * 0.25

    # 4. saturation and vibrance
    C = C * (1 + look.get("sat", 0.0))
    vib = look.get("vibrance", 0.0)
    if vib:
        C = C * np.clip(1 + vib * (1 - C / 0.22), 0.2, None)

    # 5. tone curve on lightness; chroma follows partially so contrast keeps colour density
    L0 = L.copy()
    curve = look.get("curve")
    if curve:
        L = _tone(curve)(L)
        k = look.get("curve_chroma", 0.5)
        ratio = np.where(L0 > 1e-4, L / np.maximum(L0, 1e-4), 1)
        C = C * np.clip(ratio, 0.3, 2.0) ** k

    # 6. saturation by brightness
    sh = 1 - smoothstep(0.30, 0.62, L)
    hl = smoothstep(0.70, 0.97, L)
    C = C * (1 + look.get("sat_shadows", 0.0) * sh) * (1 + look.get("sat_highlights", 0.0) * hl)
    C = np.clip(C, 0, None)

    # all saturation changes above (hue bands, sat, vibrance, curve, by-brightness) are in C now;
    # saturation boosts fade out for colours already near the gamut edge (pure LED blues, neon):
    # pushing them further only gets compressed back, and around the blue corner that compression
    # turns smooth hue gradients into hard steps. Desaturation is not affected.
    if look.get("gamut_protect", True):
        m = C / np.maximum(C_in, 1e-9)
        room = 1 - smoothstep(0.55, 1.0, edge)
        C = np.where(m > 1, C_in * (1 + (m - 1) * room), C)

    a = C * np.cos(np.radians(h))
    b = C * np.sin(np.radians(h))
    a0, b0 = a, b  # colour before toning, so the clean pass can tell toning from real colour

    # 7. split toning: (hue deg, chroma amount) for each range
    mid = np.clip(1 - sh - hl, 0, 1)
    sh = sh * smoothstep(0.0, 0.25, L)  # toning fades out toward pure black: blacks stay clean
    # skin protection: toning is weakened on skin-coloured pixels so faces keep their hue
    hd = (h - 55 + 180) % 360 - 180
    skin = np.exp(-(hd / 20) ** 2) * smoothstep(0.008, 0.035, C) * (1 - smoothstep(0.14, 0.22, C))
    keep = 1 - look.get("skin_protect", 0.7) * skin
    for key, wgt in (("shadows", sh), ("mids", mid), ("highlights", hl)):
        if key in look.get("split", {}):
            da, db = _ab(*look["split"][key])
            a = a + da * wgt * keep
            b = b + db * wgt * keep
    if "all" in look.get("split", {}):
        da, db = _ab(*look["split"]["all"])
        a, b = a + da * keep, b + db * keep
    # raw a/b offsets written by fit.py
    for key, wgt in (("shadows", sh), ("mids", mid), ("highlights", hl)):
        if key in look.get("split_ab", {}):
            da, db = look["split_ab"][key]
            a = a + da * wgt * keep
            b = b + db * wgt * keep

    # correction curve written by fit.py (display values), after everything else tonal
    if look.get("post_curve"):
        L = _tone(look["post_curve"])(L)

    # clean whites: near white, toning is removed and near-grey pixels are made neutral (paper,
    # sky glare, shirts). Saturated bright colours (lemon yellow, neon) keep their colour.
    clean = look.get("clean", 0.85)
    if clean:
        nw = clean * smoothstep(0.92, 1.0, L)
        neutral = 1 - smoothstep(0.03, 0.09, C)
        a = a0 * (1 - nw * neutral) + (a - a0) * (1 - nw)
        b = b0 * (1 - nw * neutral) + (b - b0) * (1 - nw)

    # 8. back to sRGB without hue skew
    lin = gamut_map(np.stack([L, a, b], -1))
    out = linear_to_srgb(lin)

    # 9. per-channel curves in display values
    for i, ch in enumerate("rgb"):
        pts = look.get("rgb_curves", {}).get(ch)
        if pts:
            xs, ys = zip(*pts)
            out[..., i] = pchip(xs, ys)(out[..., i])

    # 10. output levels (fade / matte and highlight cap)
    black = np.array(look.get("black", (0, 0, 0)), float)
    white = np.array(look.get("white", (1, 1, 1)), float)
    out = black + (white - black) * out
    return np.clip(out, 0, 1)
