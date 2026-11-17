"""Engine-level acceptance tests on the real shader output (harness.js).

    python3 engine_tests.py --photo city.png --tmp DIR

  scale    a photo rendered at 600 and 1200 px wide: after downscaling the big one and a light blur
           (grain is randomness, not look), the two must match -> the look does not depend on
           output resolution (preview vs full-size export)
  grain    flat grey at export size: deviation and correlation length; film grain must be clumps
           (correlation > 1 px), not per-pixel digital noise; strongest in the mid tones
  banding  a smooth sky gradient: largest step between neighbouring columns (dither must hide 8-bit
           steps)
  tiles    full frame vs 256 px tiles (the export path), max difference
Exits non-zero when a check fails.
"""
import argparse
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
F = os.path.join(ROOT, "engine/src/main/assets/filters")


def run(jobs, tmp):
    p = os.path.join(tmp, "engine_jobs.json")
    json.dump(jobs, open(p, "w"))
    subprocess.run(["node", os.path.join(HERE, "harness.js"), p], check=True)


def load(p):
    return np.asarray(Image.open(p).convert("RGB"), float)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--photo", required=True)
    ap.add_argument("--tmp", required=True)
    a = ap.parse_args()
    os.makedirs(a.tmp, exist_ok=True)
    t = lambda n: os.path.join(a.tmp, n)

    # synthetic inputs
    grey = np.full((1600, 2400, 3), 118, np.uint8)
    Image.fromarray(grey).save(t("grey.png"))
    ramp = np.tile(np.linspace(0, 255, 2400)[None, :, None], (400, 1, 3)).astype(np.uint8)
    Image.fromarray(ramp).save(t("ramp.png"))
    x = np.linspace(0, 1, 1600)[None, :]
    sky = np.stack([90 + 120 * x, 150 + 80 * x, 220 + 30 * x], -1).repeat(300, 0)
    Image.fromarray(np.clip(sky, 0, 255).astype(np.uint8)).save(t("sky.png"))

    nb = os.path.join(F, "night_bloom.json")
    mellow = os.path.join(F, "mellow400.json")
    tokyo = os.path.join(F, "tokyo_blue.json")
    nograin = {"grain": 0}
    jobs = [
        {"in": a.photo, "out": t("s600.png"), "filter": nb, "width": 600, "seed": 3, "adjust": nograin},
        {"in": a.photo, "out": t("s1200.png"), "filter": nb, "width": 1200, "seed": 3, "adjust": nograin},
        {"in": t("grey.png"), "out": t("g.png"), "filter": mellow, "width": 2400, "seed": 5,
         "adjust": {"vignette": 0, "halation": 0, "bloom": 0}},
        {"in": t("ramp.png"), "out": t("r.png"), "filter": mellow, "width": 2400, "seed": 5,
         "adjust": {"vignette": 0, "halation": 0, "bloom": 0}},
        {"in": t("sky.png"), "out": t("sky_out.png"), "filter": tokyo, "width": 1600, "seed": 5,
         "adjust": {"grain": 0, "vignette": 0}},
        {"in": a.photo, "out": t("full.png"), "filter": nb, "width": 1000, "seed": 9},
        {"in": a.photo, "out": t("tiled.png"), "filter": nb, "width": 1000, "seed": 9, "tile": 256},
    ]
    run(jobs, a.tmp)
    ok = True

    small = Image.open(t("s600.png")).convert("RGB")
    big = Image.open(t("s1200.png")).convert("RGB").resize(small.size, Image.BOX)
    blur = lambda im: np.asarray(im.filter(ImageFilter.GaussianBlur(1.5)), float)
    d = np.abs(blur(small) - blur(big))
    print(f"scale    600 vs 1200 px: mean diff {d.mean():.2f}, 99th pct {np.quantile(d, .99):.1f} levels")
    ok &= d.mean() < 1.5 and np.quantile(d, .99) < 8

    g = load(t("g.png"))[..., 1]
    g = g - g.mean()
    std = g.std()
    corr = lambda k: np.mean(g[:, :-k] * g[:, k:]) / g.var()
    c1, c2, c4 = corr(1), corr(2), corr(4)
    print(f"grain    std {std:.2f} levels, correlation lag1 {c1:.2f} lag2 {c2:.2f} lag4 {c4:.2f}")
    ok &= 1.0 < std < 12 and c1 > 0.3

    r = load(t("r.png"))[..., 1]
    base = np.asarray(Image.open(t("r.png")).convert("RGB").filter(ImageFilter.GaussianBlur(6)), float)[..., 1]
    n = r - base
    cols = np.linspace(0, r.shape[1], 9).astype(int)
    stds = [n[:, cols[i]:cols[i + 1]].std() for i in range(8)]
    print("grain    by brightness (dark -> bright): " + " ".join(f"{s:.1f}" for s in stds))
    ok &= max(stds[2:6]) > stds[0] and max(stds[2:6]) > stds[-1]

    sk = load(t("sky_out.png")).mean(0)
    steps = np.abs(np.diff(sk, axis=0)).max()
    print(f"banding  largest step between neighbouring columns of a smooth sky: {steps:.2f} levels")
    ok &= steps < 2.0

    td = np.abs(load(t("full.png")) - load(t("tiled.png")))
    print(f"tiles    full vs tiled export: max {td.max():.0f} levels, differing pixels {(td.max(-1) > 0).mean() * 100:.3f}%")
    ok &= td.max() <= 2

    print("ALL PASS" if ok else "FAILED")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
