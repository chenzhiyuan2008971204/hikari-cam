"""Measure the gap between a look and real reference photos, and optionally fit the look to them.

    python3 fit.py hamada refs/hamada/              report only
    python3 fit.py hamada refs/hamada/ --write      also write fitted/hamada.json (build.py uses it)

Put 10-30 representative photos by the photographer in the reference folder (screenshots are fine,
avoid ones with borders/text). The look is applied to the neutral test photos in --photos and the
two sets are compared statistically in OKLab:

  tone        lightness percentiles (black point, shadows, mids, highlights, white point)
  cast        average a/b of near-neutral pixels in shadows, mids, highlights (split toning)
  colour      mean chroma and mean hue of each of the 8 hue bands (saturation / hue shifts)

Tone and cast transfer well between different subjects. Per-hue colour depends on what is in the
pictures (a set full of sky has more blue), so those corrections are damped and clamped; check
the preview sheet afterwards.
"""
import argparse
import copy
import glob
import json
import os

import numpy as np
from PIL import Image

import look as engine
from build import LOOKS, HERE, load_photos
from colorlib import linear_to_oklab, srgb_to_linear

Q = [0.01, 0.05, 0.15, 0.30, 0.50, 0.70, 0.85, 0.95, 0.99]
BANDS = list(engine.ANCHORS)


def pixels(images, n=60000, seed=0):
    rng = np.random.default_rng(seed)
    px = np.concatenate([im.reshape(-1, 3) for im in images])
    if len(px) > n:
        px = px[rng.choice(len(px), n, replace=False)]
    return px


def stats(rgb):
    lab = linear_to_oklab(srgb_to_linear(rgb))
    L, a, b = lab[:, 0], lab[:, 1], lab[:, 2]
    C = np.hypot(a, b)
    h = np.degrees(np.arctan2(b, a)) % 360
    s = {"L": np.quantile(L, Q), "cast": {}, "bands": {}}
    neutral = C < 0.05
    for name, lo, hi in (("shadows", 0, 0.45), ("mids", 0.45, 0.78), ("highlights", 0.78, 1.01)):
        m = neutral & (L >= lo) & (L < hi)
        s["cast"][name] = (a[m].mean(), b[m].mean(), int(m.sum())) if m.sum() > 200 else None
    w = engine._hue_weights(h)
    colourful = C > 0.03
    for i, name in enumerate(BANDS):
        wi = w[:, i] * colourful
        if wi.sum() < 300:
            s["bands"][name] = None
            continue
        ma, mb = (wi * a).sum() / wi.sum(), (wi * b).sum() / wi.sum()
        s["bands"][name] = ((wi * C).sum() / wi.sum(), np.degrees(np.arctan2(mb, ma)) % 360, wi.sum())
    return s


def to_disp(L):
    from colorlib import linear_to_srgb
    return float(linear_to_srgb(np.array([max(L, 0) ** 3]))[0])


def report(name, cur, ref):
    print(f"\n== {name}: look on test photos  vs  reference ==")
    print("tone (display value at percentile)")
    for q, x, y in zip(Q, cur["L"], ref["L"]):
        d = to_disp(y) - to_disp(x)
        flag = "  <-- " + ("brighter" if d > 0 else "darker") if abs(d) > 0.04 else ""
        print(f"  p{int(q * 100):>2}  look {to_disp(x):.2f}   ref {to_disp(y):.2f}{flag}")
    print("cast of neutrals (a: +magenta/-green, b: +yellow/-blue)")
    for k in ("shadows", "mids", "highlights"):
        c, r = cur["cast"][k], ref["cast"][k]
        if c and r:
            print(f"  {k:<10} look a{c[0]:+.3f} b{c[1]:+.3f}   ref a{r[0]:+.3f} b{r[1]:+.3f}")
    print("colour bands (mean chroma, mean hue)")
    for k in BANDS:
        c, r = cur["bands"][k], ref["bands"][k]
        if c and r:
            print(f"  {k:<8} chroma {c[0]:.3f} -> {r[0]:.3f} ({(r[0] / c[0] - 1) * 100:+.0f}%)"
                  f"   hue {c[1]:5.1f} -> {r[1]:5.1f}")


def score(cur, ref):
    t = np.mean(np.abs(np.array(cur["L"]) - np.array(ref["L"])))
    c = np.mean([np.hypot(cur["cast"][k][0] - ref["cast"][k][0], cur["cast"][k][1] - ref["cast"][k][1])
                 for k in cur["cast"] if cur["cast"][k] and ref["cast"][k]] or [0])
    return t, c


def fit_step(lk, cur, ref, damp=0.6):
    lk = copy.deepcopy(lk)
    # tone: g maps the look's current percentiles onto the reference's; compose with any
    # earlier correction so the stored curve stays a single monotone mapping
    xs = [0.0] + [to_disp(v) for v in cur["L"]] + [1.0]
    ys = [to_disp(ref["L"][0]) * 0.5] + [to_disp(v) for v in ref["L"]] + [max(to_disp(ref["L"][-1]), 0.9)]
    ys = np.maximum.accumulate(np.array(ys))
    grid = np.linspace(0, 1, 17)
    prev = lk.get("post_curve")
    before = np.interp(grid, [p[0] for p in prev], [p[1] for p in prev]) if prev else grid
    after = np.interp(before, xs, ys)
    after = np.maximum.accumulate(after + np.arange(17) * 1e-4)
    lk["post_curve"] = [[float(x), float(min(y, 1.0))] for x, y in zip(grid, after)]

    # cast: add the difference in neutral a/b per range
    sab = lk.setdefault("split_ab", {})
    for k in ("shadows", "mids", "highlights"):
        c, r = cur["cast"][k], ref["cast"][k]
        if c and r:
            old = sab.get(k, [0.0, 0.0])
            sab[k] = [old[0] + (r[0] - c[0]) * damp, old[1] + (r[1] - c[1]) * damp]

    # colour: per band chroma ratio and hue difference, damped and clamped
    hsl = {k: list(v) for k, v in lk.get("hsl", {}).items()}
    for k in BANDS:
        c, r = cur["bands"][k], ref["bands"][k]
        if not (c and r):
            continue
        dh, ds, dl = hsl.get(k, [0, 0, 0])
        ratio = np.clip((r[0] / max(c[0], 1e-4)) ** damp, 0.7, 1.4)
        ds = float(np.clip((1 + ds) * ratio - 1, -0.9, 1.0))
        dhue = ((r[1] - c[1] + 180) % 360) - 180
        dh = float(np.clip(dh + np.clip(dhue * damp * 0.5, -6, 6), -30, 30))
        hsl[k] = [dh, ds, dl]
    lk["hsl"] = hsl
    return lk


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("look")
    ap.add_argument("refs")
    ap.add_argument("--photos", default=os.path.join(HERE, "photos"))
    ap.add_argument("--iters", type=int, default=4)
    ap.add_argument("--write", action="store_true")
    a = ap.parse_args()

    lk = LOOKS[a.look]
    refs = load_photos(a.refs, 512)
    test = load_photos(a.photos, 512)
    if not refs or not test:
        raise SystemExit("need images in both the reference folder and --photos")
    ref = stats(pixels(refs))
    tp = pixels(test)
    cur = stats(engine.apply(lk, tp))
    report(a.look, cur, ref)
    print("\nscore before: tone %.3f  cast %.4f" % score(cur, ref))
    if not a.write:
        return
    fitted = lk
    for _ in range(a.iters):
        fitted = fit_step(fitted, cur, ref)
        cur = stats(engine.apply(fitted, tp))
    print("score after:  tone %.3f  cast %.4f" % score(cur, ref))
    report(a.look + " (fitted)", cur, ref)
    os.makedirs(os.path.join(HERE, "fitted"), exist_ok=True)
    out = os.path.join(HERE, "fitted", a.look + ".json")
    with open(out, "w") as f:
        json.dump(fitted, f, ensure_ascii=False, indent=1, default=float)
    print("\nwrote", out, "- run build.py to bake it and check the preview")


if __name__ == "__main__":
    main()
