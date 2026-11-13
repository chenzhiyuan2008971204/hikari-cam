"""Colour math for the look engine: sRGB transfer, OKLab, gamut mapping, monotone curves.

All functions take and return float arrays shaped (..., 3) unless noted.
"""
import numpy as np


def srgb_to_linear(c):
    c = np.asarray(c, dtype=np.float64)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def linear_to_srgb(c):
    c = np.clip(c, 0.0, None)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055)


_M1 = np.array([[0.4122214708, 0.5363325363, 0.0514459929],
                [0.2119034982, 0.6806995451, 0.1073969566],
                [0.0883024619, 0.2817188376, 0.6299787005]])
_M2 = np.array([[0.2104542553, 0.7936177850, -0.0040720468],
                [1.9779984951, -2.4285922050, 0.4505937099],
                [0.0259040371, 0.7827717662, -0.8086757660]])
_M2I = np.linalg.inv(_M2)
_M1I = np.linalg.inv(_M1)


def linear_to_oklab(c):
    lms = c @ _M1.T
    return np.cbrt(lms) @ _M2.T


def oklab_to_linear(lab):
    lms = (lab @ _M2I.T) ** 3
    return lms @ _M1I.T


LUMA = np.array([0.2126, 0.7152, 0.0722])


def max_chroma(L, hue_ab, iters=16):
    """Largest in-gamut chroma at lightness L along unit hue direction hue_ab (..., 2)."""
    lo = np.zeros(L.shape)
    hi = np.full(L.shape, 0.5)
    for _ in range(iters):
        mid = (lo + hi) / 2
        inside = _inside(np.concatenate([L[..., None], hue_ab * mid[..., None]], -1))
        lo = np.where(inside, mid, lo)
        hi = np.where(inside, hi, mid)
    return lo


def gamut_map(lab, knee=0.9):
    """Bring OKLab colours into linear sRGB [0,1] at constant L and hue.

    Chroma is compressed smoothly from `knee` x the gamut boundary instead of being clipped, so
    saturated colours keep their gradation (and LUT interpolation stays accurate near the edge).
    """
    L = np.clip(lab[..., 0], 0.0, 1.0)
    ab = lab[..., 1:]
    C = np.hypot(ab[..., 0], ab[..., 1])
    unit = ab / np.maximum(C, 1e-9)[..., None]
    cmax = np.maximum(max_chroma(L, unit), 1e-6)
    x = C / cmax
    span = 1 - knee
    y = np.where(x <= knee, x, knee + span * np.tanh((x - knee) / span))
    out = oklab_to_linear(np.concatenate([L[..., None], unit * (y * cmax)[..., None]], -1))
    return np.clip(out, 0.0, 1.0)


def _inside(lab, eps=1e-5):
    rgb = oklab_to_linear(lab)
    return np.all((rgb >= -eps) & (rgb <= 1 + eps), axis=-1)


def pchip(xs, ys):
    """Monotone cubic (Fritsch-Carlson) through control points; returns f(x) clamped to the ends."""
    xs = np.asarray(xs, float)
    ys = np.asarray(ys, float)
    h = np.diff(xs)
    d = np.diff(ys) / h
    m = np.zeros_like(ys)
    m[0], m[-1] = d[0], d[-1]
    for i in range(1, len(xs) - 1):
        if d[i - 1] * d[i] <= 0:
            m[i] = 0
        else:
            w1, w2 = 2 * h[i] + h[i - 1], h[i] + 2 * h[i - 1]
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])

    def f(x):
        x = np.clip(np.asarray(x, float), xs[0], xs[-1])
        i = np.clip(np.searchsorted(xs, x) - 1, 0, len(xs) - 2)
        t = (x - xs[i]) / h[i]
        t2, t3 = t * t, t * t * t
        return ((2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h[i] * m[i]
                + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h[i] * m[i + 1])
    return f


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)
