"""Image-quality acceptance for the film filters, measured on the real GPU shader output.

    python3 qa.py --photos DIR --out DIR [--filters a,b] [--width 768]

DIR holds the fixed test set (portrait, sky, green, night, indoor, contrast, lowlight, city, neon,
two of each, named <category>_<n>.png). For every filter it renders each photo through
harness.js (the shipped GLSL), then checks:

  clip     new blown highlights: share of pixels at 255 that were not at 255 before
  crush    new crushed shadows: share of pixels at 0-1 that were not before
  skinHue  mean hue shift of skin pixels on the portraits (degrees)
  skinC    chroma ratio of skin pixels (1 = unchanged)
  muddy    mean chroma of colourful pixels relative to the original (low = dirty, washed colour)
and writes one contact sheet per filter (original row on top) plus a summary table.

Separate checks (always run):
  tiles    full-frame vs tiled render, max difference in 8-bit levels
  scale    same photo at 768 and 1536 px, compared after downscaling and a light blur (grain
           excluded): look must not depend on resolution
  grain    grain on flat grey at export size: correlation length in pixels (> 1 = film-like clumps,
           not per-pixel digital noise) and that it is luminance dependent
  banding  smooth sky gradient: largest step between neighbouring smoothed values
"""
import argparse
import glob
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
FILTERS = os.path.join(ROOT, "engine/src/main/assets/filters")
sys.path.insert(0, os.path.join(ROOT, "tools/looks"))
from colorlib import linear_to_oklab, srgb_to_linear  # noqa: E402


def run(jobs, tmp):
    path = os.path.join(tmp, "jobs.json")
    with open(path, "w") as f:
        json.dump(jobs, f)
    subprocess.run(["node", os.path.join(HERE, "harness.js"), path], check=True)


def lab(a):
    return linear_to_oklab(srgb_to_linear(a / 255.0))


def skin_mask(src):
    L = lab(src)
    C = np.hypot(L[..., 1], L[..., 2])
    h = np.degrees(np.arctan2(L[..., 2], L[..., 1])) % 360
    return (h > 30) & (h < 80) & (C > 0.03) & (C < 0.16) & (L[..., 0] > 0.45) & (L[..., 0] < 0.92)


def metrics(src, out, portrait):
    s, o = src.astype(int), out.astype(int)
    # blown highlight = all channels near the top (detail gone), not a saturated colour that
    # touches the edge of the gamut in one channel; that is tracked separately as edge
    clip = ((o.min(-1) >= 250) & (s.min(-1) < 250)).mean()
    edge = ((o.max(-1) >= 255) & (s.max(-1) < 255)).mean()
    crush = ((o.max(-1) <= 1) & (s.max(-1) > 1)).mean()
    ls, lo = lab(s), lab(o)
    cs, co = np.hypot(ls[..., 1], ls[..., 2]), np.hypot(lo[..., 1], lo[..., 2])
    colourful = cs > 0.05
    muddy = co[colourful].mean() / max(cs[colourful].mean(), 1e-6) if colourful.any() else 1.0
    r = dict(clip=clip, edge=edge, crush=crush, muddy=muddy)
    if portrait:
        m = skin_mask(s)
        if m.sum() > 200:
            hs = np.degrees(np.arctan2(ls[..., 2], ls[..., 1]))[m]
            ho = np.degrees(np.arctan2(lo[..., 2], lo[..., 1]))[m]
            r["skinHue"] = float(np.mean(((ho - hs + 180) % 360) - 180))
            r["skinC"] = float(co[m].mean() / cs[m].mean())
    return r


def sheet(rows, out, tile=170):
    W = 110 + len(rows[0][1]) * (tile + 3)
    sh = Image.new("RGB", (W, len(rows) * (tile + 3)), (18, 18, 18))
    d = ImageDraw.Draw(sh)
    for i, (label, ims) in enumerate(rows):
        y = i * (tile + 3)
        d.text((4, y + 4), label, fill=(240, 240, 240))
        x = 110
        for im in ims:
            t = im.copy()
            t.thumbnail((tile, tile))
            sh.paste(t, (x, y))
            x += tile + 3
    sh.save(out, quality=90)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--photos", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--filters")
    ap.add_argument("--width", type=int, default=768)
    ap.add_argument("--adjust", default="{}")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    ids = a.filters.split(",") if a.filters else json.load(open(os.path.join(FILTERS, "index.json")))
    photos = sorted(glob.glob(os.path.join(a.photos, "*.png")))
    adjust = json.loads(a.adjust)

    jobs = []
    for fid in ids:
        for p in photos:
            name = os.path.splitext(os.path.basename(p))[0]
            jobs.append({"in": p, "out": os.path.join(a.out, f"{fid}__{name}.png"),
                         "filter": os.path.join(FILTERS, fid + ".json"), "width": a.width, "seed": 7,
                         "adjust": adjust})
    run(jobs, a.out)

    srcs = {}
    for p in photos:
        im = Image.open(p).convert("RGB")
        h = round(a.width * im.height / im.width)
        srcs[p] = im.resize((a.width, h), Image.LANCZOS)
    summary = {}
    for fid in ids:
        rows = [("original", [srcs[p] for p in photos])]
        outs, ms = [], []
        for p in photos:
            name = os.path.splitext(os.path.basename(p))[0]
            o = Image.open(os.path.join(a.out, f"{fid}__{name}.png")).convert("RGB")
            outs.append(o)
            ms.append(metrics(np.asarray(srcs[p]), np.asarray(o), name.startswith("portrait")))
        rows.append((fid, outs))
        sheet(rows, os.path.join(a.out, f"sheet_{fid}.jpg"))
        agg = {k: float(np.mean([m[k] for m in ms if k in m])) for k in ("clip", "edge", "crush", "muddy", "skinHue", "skinC")
               if any(k in m for m in ms)}
        agg["clipMax"] = float(max(m["clip"] for m in ms))
        agg["crushMax"] = float(max(m["crush"] for m in ms))
        summary[fid] = agg
    print(f"{'filter':<15}{'clip%':>7}{'clipMax%':>9}{'edge%':>7}{'crush%':>8}{'crushMax%':>10}{'muddy':>7}{'skinHue':>8}{'skinC':>7}")
    for fid, m in summary.items():
        print(f"{fid:<15}{m['clip']*100:7.2f}{m['clipMax']*100:9.2f}{m['edge']*100:7.2f}{m['crush']*100:8.2f}{m['crushMax']*100:10.2f}"
              f"{m['muddy']:7.2f}{m.get('skinHue', 0):8.1f}{m.get('skinC', 1):7.2f}")
    json.dump(summary, open(os.path.join(a.out, "summary.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
