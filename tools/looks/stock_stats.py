"""Colour statistics of film-stock reference photos vs a digital baseline (fine 12-band model).

  python3 stock_stats.py REFS_DIR [--looks]   prints, per stock: tone percentiles, neutral casts in
  shadows/mids/highlights, chroma and hue per band; with --looks also each Hikari look applied
  to the baseline photos, so the gap to its stock can be read directly.
"""
import glob
import os
import sys

import numpy as np
from PIL import Image

import look as engine
from colorlib import linear_to_oklab, srgb_to_linear

HERE = os.path.dirname(os.path.abspath(__file__))
Q = [0.02, 0.1, 0.3, 0.5, 0.7, 0.9, 0.98]
NAMES = list(engine.FINE)
ANG = np.array([engine.FINE[k] for k in NAMES], float)


def load(files, n=40000, size=512):
    px = []
    for f in files:
        try:
            im = Image.open(f).convert("RGB")
        except Exception:
            continue
        im.thumbnail((size, size))
        a = np.asarray(im, float) / 255
        # drop scan borders: rows/cols that are nearly uniform black/white
        h, w, _ = a.shape
        a = a[int(h * 0.04):int(h * 0.96), int(w * 0.04):int(w * 0.96)]
        px.append(a.reshape(-1, 3))
    px = np.concatenate(px)
    rng = np.random.default_rng(0)
    return px[rng.choice(len(px), min(n, len(px)), replace=False)]


def stats(rgb):
    lab = linear_to_oklab(srgb_to_linear(np.clip(rgb, 0, 1)))
    L, a, b = lab[:, 0], lab[:, 1], lab[:, 2]
    C = np.hypot(a, b)
    h = np.degrees(np.arctan2(b, a)) % 360
    out = {"L": np.quantile(L, Q), "sat85": float(np.quantile(C, 0.85)), "cast": {}, "band": {}}
    neutral = C < 0.04
    for name, lo, hi in (("sh", 0, 0.42), ("mid", 0.42, 0.75), ("hi", 0.75, 1.01)):
        m = neutral & (L >= lo) & (L < hi)
        out["cast"][name] = (float(a[m].mean()), float(b[m].mean())) if m.sum() > 150 else None
    w = engine._band_weights(h, ANG)
    col = C > 0.03
    for i, k in enumerate(NAMES):
        wi = w[:, i] * col
        if wi.sum() < 200:
            out["band"][k] = None
            continue
        ang = np.degrees(np.arctan2((np.sin(np.radians(h)) * wi).sum(), (np.cos(np.radians(h)) * wi).sum())) % 360
        out["band"][k] = (float((C * wi).sum() / wi.sum()), float(ang), float(wi.sum() / len(h)))
    return out


def fmt(s):
    c = " ".join(f"{k}:{'%+.3f/%+.3f' % v if v else '   -    '}" for k, v in s["cast"].items())
    return f"L {' '.join('%.2f' % x for x in s['L'])}  sat85 {s['sat85']:.3f}  cast {c}"


def bands(s):
    return "  ".join(f"{k}:{v[0]:.3f}@{v[1]:.0f}" if v else f"{k}:-" for k, v in s["band"].items())


def main():
    refs = sys.argv[1]
    base_files = sorted(glob.glob(os.path.join(HERE, "photos", "daylight", "*.png")))
    base = load(base_files)
    sb = stats(base)
    print("BASELINE (digital test set)\n  " + fmt(sb) + "\n  " + bands(sb))
    for d in sorted(glob.glob(os.path.join(refs, "*"))):
        files = glob.glob(os.path.join(d, "*.jpg"))
        if len(files) < 4:
            continue
        s = stats(load(files))
        print(f"\n{os.path.basename(d)} ({len(files)} photos)\n  " + fmt(s) + "\n  " + bands(s))
    if "--looks" in sys.argv:
        from films import FILMS
        for k, f in FILMS.items():
            if f["series"] == "daylight":
                continue
            s = stats(engine.apply(f["colour"], base))
            print(f"\nLOOK {k}\n  " + fmt(s))


if __name__ == "__main__":
    main()
