"""Shared helpers for look recipes: filmic tone curve and spatial-effect settings."""
import numpy as np

from colorlib import linear_to_srgb


class Curve(list):
    """Curve points that also remember the parameters they came from (for the recipe text)."""
    params: dict = {}


def filmic(contrast=1.0, toe=0.0, shoulder=1.0, exposure=0.0, n=33):
    """Filmic tone curve as display-value points for look.py's `curve`.

    - exposure (stops) brightens or darkens with x*k / (1 + (k-1)*x) in linear light: mid tones move
      by about `exposure` stops while white stays white, so nothing clips.
    - contrast is the slope at 18% grey. The curve is f(t) = c*t / (1 + (c-1)*t) on each side of
      grey, so the slope eases to 1/c towards black and white: a soft toe and a soft shoulder
      (highlight roll-off) instead of clipping. f(0)=0 and f(1)=1, monotone for any c > 0.
    - toe lifts black (keeps shadow detail visible), shoulder is the display value white rolls to.
    """
    xs = np.linspace(0, 1, n)
    lin = np.where(xs <= 0.04045, xs / 12.92, ((xs + 0.055) / 1.055) ** 2.4)
    k = 2.0 ** exposure
    lin = lin * k / (1 + (k - 1) * lin)
    x = linear_to_srgb(lin)
    p = float(linear_to_srgb(np.array([0.18]))[0])  # 18% grey in display values
    c = contrast
    f = lambda t: c * t / (1 + (c - 1) * t)
    y = np.where(x >= p, p + (1 - p) * f(np.clip((x - p) / (1 - p), 0, 1)),
                 p - p * f(np.clip((p - x) / p, 0, 1)))
    y = toe + (shoulder - toe) * y
    out = Curve((float(a), float(b)) for a, b in zip(xs, y))
    out.params = dict(contrast=contrast, toe=toe, shoulder=shoulder, exposure=exposure)
    return out


def fx(grain=0.3, grain_size=0.00128, grain_chroma=0.25, grain_rough=0.4,
       halation=0.0, hal_threshold=0.82, hal_radius=0.012, hal_tint=(1.0, 0.32, 0.12),
       bloom=0.0, bloom_threshold=0.78, bloom_radius=0.045,
       soft_focus=0.0, mist=0.0, diffusion_radius=0.006,
       vignette=0.15, vignette_soft=0.6, aberration=0.0, edge_soft=0.0,
       dust=0.0, scratches=0.0, leak=0.0, leak_color=(1.0, 0.45, 0.18), uneven=0.0, drift=0.0):
    return dict(
        grain=dict(amount=grain, size=grain_size, chroma=grain_chroma, rough=grain_rough),
        halation=dict(amount=halation, threshold=hal_threshold, radius=hal_radius, tint=list(hal_tint)),
        bloom=dict(amount=bloom, threshold=bloom_threshold, radius=bloom_radius),
        diffusion=dict(softFocus=soft_focus, mist=mist, radius=diffusion_radius),
        lens=dict(vignette=vignette, vignetteSoft=vignette_soft, aberration=aberration, edgeSoft=edge_soft),
        defects=dict(dust=dust, scratches=scratches, leak=leak, leakColor=list(leak_color),
                     uneven=uneven, drift=drift),
    )
