"""Scale a look's character (its departure from neutral) by k, keeping its direction.

Scaled: white balance, saturation/vibrance, per-hue HSL (both tables), split toning amounts,
tone curve contrast / exposure / toe / shoulder, RGB curve offsets. Kept as is: chroma ceilings,
highlight roll-off, clean whites, selective colour on whites (the guards).
"""
import copy

from filmkit import filmic


def amplify(lk, k):
    lk = copy.deepcopy(lk)
    for key in ("temp", "tint", "sat", "vibrance", "sat_shadows", "sat_highlights"):
        if key in lk:
            lk[key] = lk[key] * k
    for tab in ("hsl", "hsl_dark"):
        if tab in lk:
            lk[tab] = {n: (h * k, s * k, l * k) for n, (h, s, l) in lk[tab].items()}
    if "split" in lk:
        lk["split"] = {n: (h, a * k) for n, (h, a) in lk["split"].items()}
    cp = getattr(lk.get("curve"), "params", None)
    if cp:
        lk["curve"] = filmic(exposure=cp["exposure"] * k, contrast=1 + (cp["contrast"] - 1) * k,
                             toe=cp["toe"] * min(k, 1.3), shoulder=1 - (1 - cp["shoulder"]) * min(k, 1.3))
    if "rgb_curves" in lk:
        lk["rgb_curves"] = {c: [(x, x + (y - x) * k) for x, y in pts] for c, pts in lk["rgb_curves"].items()}
    return lk


def amplify_mood(lk, k, clean=False):
    """Stronger character mostly from mood: colour grading and tone curve scale by k, white balance
    by sqrt(k), per-hue HSL by k**0.4 (skin band untouched), saturation kept.

    clean=True (daylight looks): the highlight shoulder and highlight grading are not scaled
    (whites stay white and bright) and no band is desaturated beyond -0.35 (no muddy greens)."""
    lk = copy.deepcopy(lk)
    kw = k ** 0.5
    kh = k ** 0.4
    for key in ("temp", "tint"):
        if key in lk:
            lk[key] = lk[key] * kw
    for tab in ("hsl", "hsl_dark"):
        if tab in lk:
            lk[tab] = {n: ((h, s, l) if n == "skin" else
                           (h * kh, max(s * kh, min(s, -0.35)) if clean else s * kh, l * kh)) for n, (h, s, l) in lk[tab].items()}
    if "split" in lk:
        lk["split"] = {n: (h, a if (clean and n == "highlights") else a * k) for n, (h, a) in lk["split"].items()}
    cp = getattr(lk.get("curve"), "params", None)
    if cp:
        lk["curve"] = filmic(exposure=cp["exposure"] * (1 if clean else kw), contrast=1 + (cp["contrast"] - 1) * k,
                             toe=cp["toe"] * min(k, 1.4),
                             shoulder=cp["shoulder"] if clean else 1 - (1 - cp["shoulder"]) * min(k, 1.4))
    if "rgb_curves" in lk:
        lk["rgb_curves"] = {c: [(x, x + (y - x) * k) for x, y in pts] for c, pts in lk["rgb_curves"].items()}
    return lk
