"""Quality refinement of a finished look recipe: small, per-look corrections that clean up the picture
without changing what the look is.

A look keeps its white balance, grading, split toning, saturation and contrast character. What changes
(each amount is fitted for that one look by refine_fit.py and stored in refine_params.json):

  shadow_open   steeper tone curve just above black: crushed shadow detail comes back, lifted-black
                (matte) looks keep their lift but stop looking flat
  hl_open       steeper tone curve just below white: highlights separate instead of merging
  mid_open      steeper mid tones (around 18-50 % grey) so opening the shadows does not flatten the picture
  toe           black level nudge (only where the look crushes to pure black)
  mud           how much of the look's darkening of desaturated colours is given back (grey, dirty
                colour becomes clean desaturated colour)
  skin_hue      hue offset for skin tones (and half of it on the neighbouring bands)
  green_light   lightness offset for foliage greens (heavy, black-green plants)
  green_sat     chroma offset for foliage greens
  sky_hue       hue offset for blue sky
  neon          scale of the chroma ceilings (spilled, fluorescent colour)

`refine_look(look, params)` returns a new look dict; the original is not touched.
"""
import copy

import numpy as np

KNOBS = ("shadow_open", "hl_open", "mid_open", "toe", "mud", "skin_hue", "green_light", "green_sat", "sky_hue", "neon")
RANGES = dict(shadow_open=(0.0, 1.4), hl_open=(0.0, 0.5), mid_open=(0.0, 0.5), toe=(-0.004, 0.02), mud=(0.0, 1.0), skin_hue=(-8.0, 8.0),
              green_light=(-0.1, 0.3), green_sat=(-0.12, 0.25), sky_hue=(-8.0, 8.0), neon=(0.7, 1.0))
NEUTRAL = dict(shadow_open=0.0, hl_open=0.0, mid_open=0.0, toe=0.0, mud=0.0, skin_hue=0.0, green_light=0.0, green_sat=0.0, sky_hue=0.0, neon=1.0)


def _bump_s(y):
    return np.where(y < 0.32, y * (1 - y / 0.32) ** 2, 0.0)


def _bump_h(y):
    u = 1 - y
    return np.where(u < 0.30, u * (1 - u / 0.30) ** 2, 0.0)


def _bump_m(y):
    d = y - 0.5
    return np.where(np.abs(d) < 0.2, d * (1 - (d / 0.2) ** 2) ** 2, 0.0)


def reshape_curve(points, shadow_open, hl_open, toe, mid_open=0.0):
    """Tone curve points with steeper shadow / highlight ends. Stays monotone; the 18 % grey slope is untouched."""
    pts = np.array(points, float)
    xs, ys = pts[:, 0], pts[:, 1]
    lo, hi = ys[0], ys[-1]
    t = np.clip((ys - lo) / max(hi - lo, 1e-6), 0, 1)  # curve output normalised to 0..1
    t2 = t + shadow_open * _bump_s(t) - hl_open * _bump_h(t) + mid_open * _bump_m(t)
    # monotone guard: never let the slope of the correction fall below 10 % of the original
    t2 = np.maximum.accumulate(t2)
    ys2 = lo + (hi - lo) * t2
    ys2 = ys2 + toe * (1 - t2)
    ys2 = np.maximum.accumulate(ys2)
    out = type(points)((float(a), float(b)) for a, b in zip(xs, ys2)) if hasattr(points, "params") else [(float(a), float(b)) for a, b in zip(xs, ys2)]
    if hasattr(points, "params"):
        out.params = dict(points.params)
    return out


def refine_look(lk, p):
    """A new look dict with the refinement `p` (dict of KNOBS) applied."""
    lk = copy.deepcopy(lk)
    p = dict(NEUTRAL, **p)
    if lk.get("curve") is not None and (p["shadow_open"] or p["hl_open"] or p["mid_open"] or p["toe"]):
        lk["curve"] = reshape_curve(lk["curve"], p["shadow_open"], p["hl_open"], p["toe"], p["mid_open"])
    if lk.get("mono") is not None:
        lk["refine"] = {k: p[k] for k in KNOBS if p[k] != NEUTRAL[k]}
        return lk
    for tab in ("hsl", "hsl_dark"):
        t = dict(lk.get(tab, {}))
        # give back the darkening that goes together with desaturation
        if p["mud"]:
            for n, (h, s, l) in list(t.items()):
                if l < 0:
                    t[n] = (h, s, l * (1 - p["mud"]))
                    h, s, l = t[n]
                if s < 0 and l < 0.04:
                    t[n] = (h, s, l + 0.05 * p["mud"] * min(1.0, -s / 0.2))
        def add(name, dh=0.0, ds=0.0, dl=0.0):
            h, s, l = t.get(name, (0.0, 0.0, 0.0))
            t[name] = (h + dh, s + ds, l + dl)
        if p["skin_hue"] and tab == "hsl":
            add("skin", dh=p["skin_hue"]); add("amber", dh=0.5 * p["skin_hue"]); add("red", dh=0.3 * p["skin_hue"])
        if p["sky_hue"] and tab == "hsl":
            add("sky", dh=p["sky_hue"]); add("blue", dh=0.5 * p["sky_hue"])
        if p["green_light"] or p["green_sat"]:
            w = 1.0 if tab == "hsl_dark" else 0.7   # deep greens are where plants turn into black-green
            for n, f in (("green", 1.0), ("lime", 0.8), ("teal", 0.5)):
                add(n, ds=p["green_sat"] * f, dl=p["green_light"] * f * w)
        if t:
            lk[tab] = t
    if p["neon"] != 1.0 and lk.get("chroma_cap"):
        lk["chroma_cap"] = {k: v * p["neon"] for k, v in lk["chroma_cap"].items()}
    lk["refine"] = {k: round(p[k], 4) for k in KNOBS if p[k] != NEUTRAL[k]}
    return lk
