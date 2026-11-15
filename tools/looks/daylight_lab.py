"""Daylight Filter Lab: the eight Daylight Collection looks on the same daylight test set, plus
measured checks for each requirement (sky, foliage, skin, whites).

  python3 daylight_lab.py                 checks + comparison sheets into preview/daylight/
  python3 daylight_lab.py --photos DIR    use another folder of test photos
  python3 daylight_lab.py --checks-only   numbers only

The test photos live in photos/daylight/ (not committed): 01_sky_clouds, 02_green_trees,
03_japan_street, 04_building_concrete, 05_white, 06_portrait, 07_backlight (+ extras x*).
The synthetic patches below are the pass/fail tests; the photos are for looking at.
"""
import argparse
import glob
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import look as engine
from build import bake, clean_check
from colorlib import linear_to_oklab, srgb_to_linear
from daylight import DAYLIGHT

HERE = os.path.dirname(os.path.abspath(__file__))


def lab(rgb255):
    return linear_to_oklab(srgb_to_linear(np.asarray(rgb255, float) / 255))


def lch(l):
    return l[..., 0], np.hypot(l[..., 1], l[..., 2]), np.degrees(np.arctan2(l[..., 2], l[..., 1])) % 360


def hdiff(a, b):
    return np.abs((a - b + 180) % 360 - 180)


# measured-style daylight colours (sRGB 0..255)
SKY = [(196, 220, 238), (150, 190, 228), (110, 160, 215), (72, 122, 196), (44, 86, 168)]  # horizon -> zenith
CYAN_SKY = [(130, 200, 225), (90, 175, 215)]
CLOUD = [(250, 250, 252), (232, 234, 238), (205, 210, 220), (175, 182, 196)]  # lit top -> shaded underside
GREENS = dict(yellow_green=(165, 180, 60), grass=(96, 148, 52), foliage=(62, 112, 48),
              deep_green=(28, 62, 30), blue_green=(40, 92, 72))
SKINS = dict(sun_light=(236, 196, 170), shade=(205, 170, 156), golden=(242, 182, 134),
             east_asian=(226, 186, 158), deep=(140, 96, 72), overcast=(214, 182, 168))
WHITES = dict(paper=(250, 250, 250), shirt_shade=(226, 231, 240), wall_sun=(242, 239, 230),
              concrete=(178, 176, 172), cloud_grey=(214, 216, 220))


def checks(lk):
    """{name: (value, limit, ok)} for one look's colour recipe."""
    r = {}
    run = lambda cols: lch(linear_to_oklab(srgb_to_linear(engine.apply(lk, np.array(cols, float) / 255))))
    L0, C0, H0 = lch(lab(SKY))
    L1, C1, H1 = run(SKY)
    r["sky: max chroma (no neon)"] = (C1.max(), 0.17, C1.max() <= 0.17)
    r["sky: hue stays blue 205-268"] = (f"{H1.min():.0f}-{H1.max():.0f}", "205-268", H1.min() >= 205 and H1.max() <= 268)
    steps0 = np.hypot(np.diff(L0), np.diff(C0))
    steps1 = np.hypot(np.diff(L1), np.diff(C1))
    r["sky: gradient steps kept"] = ((steps1 / steps0).min(), 0.7, (steps1 / steps0).min() >= 0.7)
    _, Cc0, Hc0 = lch(lab(CYAN_SKY)); _, Cc1, Hc1 = run(CYAN_SKY)
    _, Cb1, Hb1 = run(SKY[3:])
    sep = hdiff(Hc1.mean(), Hb1.mean()) / hdiff(Hc0.mean(), lch(lab(SKY[3:]))[2].mean())
    r["sky: cyan vs deep blue separation"] = (sep, 0.6, sep >= 0.6)

    Lc0, Cl0, _ = lch(lab(CLOUD)); Lc1, Cl1, _ = run(CLOUD)
    detail = (np.diff(Lc1) / np.diff(Lc0)).min()
    r["clouds: tonal detail kept"] = (detail, 0.7, detail >= 0.7)
    r["clouds: neutral (max C)"] = (Cl1.max(), 0.03, Cl1.max() <= 0.03)

    g0 = {k: lch(lab([v])) for k, v in GREENS.items()}
    g1 = {k: run([v]) for k, v in GREENS.items()}
    gC = max(float(v[1][0]) for v in g1.values())
    r["greens: max chroma (no neon)"] = (gC, 0.16, gC <= 0.16)
    main = ["yellow_green", "grass", "foliage"]
    h1 = [float(g1[k][2][0]) for k in main]; h0 = [float(g0[k][2][0]) for k in main]
    spread = (h1[2] - h1[0]) / (h0[2] - h0[0])
    r["greens: yellow-green / grass / foliage spread"] = (spread, 0.6, spread >= 0.6)
    dl = (g1["foliage"][0][0] - g1["deep_green"][0][0]) / (g0["foliage"][0][0] - g0["deep_green"][0][0])
    r["greens: deep green separate from foliage"] = (dl, 0.75, dl >= 0.75)
    notcyan = max(float(g1[k][2][0]) for k in main)
    r["greens: not turned cyan (hue)"] = (notcyan, 160, notcyan <= 160)
    muddy = min(float(g1[k][1][0] / g0[k][1][0]) for k in GREENS)
    r["greens: not muddy (chroma ratio)"] = (muddy, 0.55, muddy >= 0.55)
    yb = min(float(g1[k][2][0]) for k in ["grass", "foliage"])
    r["greens: not dirty yellow (hue)"] = (yb, 118, yb >= 118)

    s0 = {k: lch(lab([v])) for k, v in SKINS.items()}
    s1 = {k: run([v]) for k, v in SKINS.items()}
    sh = max(float(hdiff(s1[k][2][0], s0[k][2][0])) for k in SKINS)
    r["skin: hue shift"] = (sh, 8, sh <= 8)
    sc = max(float(s1[k][1][0] / s0[k][1][0]) for k in SKINS)
    r["skin: not over-orange (chroma ratio)"] = (sc, 1.12, sc <= 1.12)
    hue = [float(s1[k][2][0]) for k in SKINS]
    r["skin: hue range 35-75"] = (f"{min(hue):.0f}-{max(hue):.0f}", "35-75", min(hue) >= 35 and max(hue) <= 75)
    d0 = hdiff(s0["shade"][2][0], s0["golden"][2][0]); d1 = hdiff(s1["shade"][2][0], s1["golden"][2][0])
    r["skin: shade vs golden light kept"] = (float(d1 / d0), 0.6, d1 / d0 >= 0.6)

    w0 = {k: lch(lab([v])) for k, v in WHITES.items()}
    w1 = {k: run([v]) for k, v in WHITES.items()}
    r["white paper: neutral C"] = (float(w1["paper"][1][0]), 0.012, w1["paper"][1][0] <= 0.012)
    lvl = float(w1["paper"][0][0] / w0["paper"][0][0])
    r["white paper: not grey (L ratio)"] = (lvl, 0.985, lvl >= 0.985)
    out = engine.apply(lk, np.array(list(WHITES.values()), float) / 255)
    ob = lab(out * 255)
    ib = lab(np.array(list(WHITES.values()), float))
    # a yellow cast = a white ending up more yellow than it was and yellow in absolute terms
    # (a bluish white in shade turning neutral is cleaning, not a cast)
    yel = float((ob[:, 2] - np.maximum(ib[:, 2], 0)).max())
    r["whites: no yellow cast (b shift)"] = (yel, 0.012, yel <= 0.012)
    fails, m = clean_check(lk)
    r["engine clean check"] = (",".join(fails) or "ok", "ok", not fails)
    return r


def photo_checks(lk, photos):
    """Checks on the real sky photo: sky chroma, cloud texture."""
    r = {}
    p = os.path.join(photos, "01_sky_clouds.png")
    if not os.path.exists(p):
        return r
    img = np.asarray(Image.open(p).convert("RGB"), float)[::2, ::2] / 255
    out = engine.apply(lk, img)
    L0, C0, H0 = lch(linear_to_oklab(srgb_to_linear(img)))
    L1, C1, H1 = lch(linear_to_oklab(srgb_to_linear(out)))
    blue = (H0 > 220) & (H0 < 275) & (C0 > 0.05)
    c99 = float(np.percentile(C1[blue], 99))
    r["photo sky: 99th pct chroma"] = (c99, 0.19, c99 <= 0.19)
    cloud = L0 > 0.85
    tex = float(L1[cloud].std() / L0[cloud].std())
    r["photo clouds: texture kept"] = (tex, 0.8, tex >= 0.8)
    clip = float((out[cloud].min(-1) > 0.995).mean() - (img[cloud].min(-1) > 0.995).mean())
    r["photo clouds: no new clipping"] = (clip, 0.02, clip <= 0.02)
    return r


def sheet(photos, out_dir, keys):
    """One large sheet per scene: original + all looks, 3 x 3, labelled."""
    os.makedirs(out_dir, exist_ok=True)
    try:
        font = ImageFont.truetype(next(iter(glob.glob("/usr/share/fonts/**/*CJK*.tt*", recursive=True) + glob.glob("/usr/share/fonts/**/DejaVuSans.ttf", recursive=True))), 30)
    except OSError:
        font = ImageFont.load_default()
    tables = {k: bake(DAYLIGHT[k]["colour"]) for k in keys}
    from build import apply_lut
    files = sorted(glob.glob(os.path.join(photos, "*.png")) + glob.glob(os.path.join(photos, "*.jpg")))
    for f in files:
        im = Image.open(f).convert("RGB")
        im.thumbnail((720, 720))
        w, h = im.size
        tiles = [("Original", im)]
        arr = np.asarray(im, float) / 255
        for k in keys:
            o = apply_lut(tables[k], arr)
            tiles.append((DAYLIGHT[k]["name"], Image.fromarray((np.clip(o, 0, 1) * 255 + 0.5).astype(np.uint8))))
        cols = 3
        rows = (len(tiles) + cols - 1) // cols
        pad = 50
        S = Image.new("RGB", (cols * w, rows * (h + pad)), (18, 18, 18))
        d = ImageDraw.Draw(S)
        for i, (name, t) in enumerate(tiles):
            x, y = (i % cols) * w, (i // cols) * (h + pad)
            S.paste(t, (x, y + pad))
            d.text((x + 12, y + 8), name, fill=(240, 240, 240), font=font)
        S.save(os.path.join(out_dir, os.path.splitext(os.path.basename(f))[0] + ".jpg"), quality=90)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--photos", default=os.path.join(HERE, "photos", "daylight"))
    ap.add_argument("--out", default=os.path.join(HERE, "preview", "daylight"))
    ap.add_argument("--only")
    ap.add_argument("--checks-only", action="store_true")
    a = ap.parse_args()
    keys = a.only.split(",") if a.only else list(DAYLIGHT)
    bad = 0
    for k in keys:
        lk = DAYLIGHT[k]["colour"]
        res = {**checks(lk), **photo_checks(lk, a.photos)}
        nf = [n for n, v in res.items() if not v[2]]
        bad += len(nf)
        print(f"\n{DAYLIGHT[k]['name']} ({k}): {'PASS' if not nf else 'FAIL ' + str(len(nf))}")
        for n, (v, lim, ok) in res.items():
            vs = f"{v:.3f}" if isinstance(v, float) else str(v)
            print(f"  {'  ' if ok else '✗ '}{n:<46}{vs:>12}   limit {lim}")
    if not a.checks_only:
        sheet(a.photos, a.out, keys)
        print("\nsheets:", a.out)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
