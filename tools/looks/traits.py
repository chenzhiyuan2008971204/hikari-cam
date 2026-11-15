"""Measured traits of every look, for the filter recommender (vision/Recommend.kt).

Each number comes from the look's real colour output on fixed colours, not from its description.
build_films.py writes them into the filter json as "traits".
"""
import numpy as np

import look as engine
from colorlib import linear_to_oklab, srgb_to_linear

SKY = np.array([(150, 190, 228), (110, 160, 215), (72, 122, 196)], float) / 255
GREEN = np.array([(165, 180, 60), (96, 148, 52), (62, 112, 48)], float) / 255
SKIN = np.array([(236, 196, 170), (205, 170, 156), (226, 186, 158), (140, 96, 72)], float) / 255
MIX = np.array([(196, 60, 50), (230, 180, 60), (96, 148, 52), (72, 122, 196), (150, 90, 160), (120, 200, 200), (226, 186, 158)], float) / 255


def _lch(rgb):
    l = linear_to_oklab(srgb_to_linear(rgb))
    return l[..., 0], np.hypot(l[..., 1], l[..., 2]), np.degrees(np.arctan2(l[..., 2], l[..., 1])) % 360, l


def _hd(a, b):
    return (a - b + 180) % 360 - 180


def traits(f):
    lk, fxv = f["colour"], f["fx"]
    ap = lambda x: engine.apply(lk, x)
    g = lambda v: np.array([[v, v, v]], float)
    mono = lk.get("mono") is not None
    # tone: slope around mid grey, shadows, highlights
    y = lambda v: float((ap(g(v)) @ engine.LUMA)[0])
    slope = (y(0.56) - y(0.36)) / 0.2
    hl = (y(0.95) - y(0.80)) / 0.15
    L0, C0, H0, lab0 = _lch(MIX); L1, C1, H1, lab1 = _lch(ap(MIX))
    grey = _lch(ap(g(0.5)))[3][0]
    out = dict(
        contrast=slope - 1.0,
        sat=float(C1.mean() / C0.mean() - 1.0),
        warmth=float(grey[2] * 100),
        shadowLift=y(0.08) - 0.08,
        hlSoft=float(np.clip(1.0 - hl, 0, 1)),
        fade=float(ap(g(0.0)).max()),
        mono=1.0 if mono else 0.0,
        glow=float(fxv["halation"]["amount"] + fxv["bloom"]["amount"] + fxv["diffusion"]["mist"]),
        grain=float(fxv["grain"]["amount"]),
    )
    for name, pal in (("sky", SKY), ("green", GREEN), ("skin", SKIN)):
        l0, c0, h0, _ = _lch(pal); l1, c1, h1, _ = _lch(ap(pal))
        out[name + "Sat"] = float(c1.mean() / c0.mean())
        out[name + "L"] = float((l1 - l0).mean())
        out[name + ("Hue" if name == "skin" else "Shift")] = 0.0 if mono else float(_hd(h1, h0).mean())
    return {k: round(float(v), 4) for k, v in out.items()}


if __name__ == "__main__":
    from films import FILMS, SHIP
    keys = ["contrast", "sat", "warmth", "skySat", "skyL", "greenSat", "greenShift", "skinSat", "skinHue", "shadowLift", "hlSoft", "glow"]
    print(f"{'look':<17}" + "".join(f"{k:>11}" for k in keys))
    for k in SHIP:
        t = traits(FILMS[k])
        print(f"{k:<17}" + "".join(f"{t[x]:>11.3f}" for x in keys))
