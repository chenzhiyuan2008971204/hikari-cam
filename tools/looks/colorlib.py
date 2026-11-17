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


def max_chroma(L, hue_ab, steps=96, iters=12):
    """Largest in-gamut chroma at lightness L along unit hue direction hue_ab (..., 2).

    Returns the OUTERMOST boundary. Near the sRGB blue corner a ray from grey leaves the gamut,
    re-enters and leaves again (OKLab bends there); taking the first exit made the result jump
    between the two as hue changed, which showed up as jagged edges in blue gradients.
    """
    L = np.asarray(L, float)
    cs = np.linspace(0.0, 0.5, steps + 1)
    best = np.zeros(L.shape)
    for c in cs[1:]:
        inside = _inside(np.concatenate([L[..., None], hue_ab * c], -1))
        best = np.where(inside, c, best)
    lo, hi = best, np.minimum(best + cs[1], 0.5)
    for _ in range(iters):
        mid = (lo + hi) / 2
        inside = _inside(np.concatenate([L[..., None], hue_ab * mid[..., None]], -1))
        lo = np.where(inside, mid, lo)
        hi = np.where(inside, hi, mid)
    return lo


GREY_CAP = 0.5  # out-of-gamut colours are pulled toward a grey no brighter than this


def gamut_excess(lin, colourful=None):
    """How far a linear-RGB colour reaches toward (<= 1) or past (> 1) the sRGB gamut, measured
    along the line from its anchor grey. The anchor is the colour's own luminance; for colourful
    pixels (weight `colourful`, 0..1) it is capped at GREY_CAP. Near-neutral colours keep their
    own luminance so whites never get darker. Closed form and continuous everywhere."""
    lin = np.asarray(lin, float)
    Y = np.clip(lin @ LUMA, 0.0, 1.0)
    w = 0.0 if colourful is None else colourful
    g = (Y - (Y - np.minimum(Y, GREY_CAP)) * w)[..., None]
    d = lin - g
    with np.errstate(divide="ignore", invalid="ignore"):
        k = np.where(d > 1e-12, (1 - g) / d, np.where(d < -1e-12, g / -d, np.inf))
    kmax = k.min(-1)
    return np.where(np.isfinite(kmax), 1.0 / np.maximum(kmax, 1e-12), 0.0), g, d


def gamut_map(lab, knee=0.97):
    """Bring OKLab colours into linear sRGB [0,1].

    Colours are pulled toward an anchor grey just enough to fit, with a soft knee from `knee` x
    the gamut edge so saturated colours keep their gradation. The anchor is the colour's own
    luminance for darker colours but at most GREY_CAP: a bright saturated colour that a look
    pushed past what sRGB can show (a lemon yellow lifted to near white) gives up a little
    brightness and keeps its colour, as film does, instead of fading to white. Closed form, so no
    numerical jitter (a constant-OKLab-L search gave jagged edges near sRGB blue).
    """
    lin = oklab_to_linear(lab)
    C = np.hypot(lab[..., 1], lab[..., 2])
    x, g, d = gamut_excess(lin, smoothstep(0.03, 0.10, C))
    span = 1 - knee
    y = np.where(x <= knee, x, knee + span * np.tanh((x - knee) / span))
    k = np.where(x > 0, y / np.maximum(x, 1e-12), 1.0)
    return np.clip(g + d * k[..., None], 0.0, 1.0)


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
