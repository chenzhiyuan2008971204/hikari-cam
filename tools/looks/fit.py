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
    s = {"L": np.quantile(L, Q), "cast": {}, "bands": {},
         # overall saturation: 85th percentile of chroma over all pixels (scales with a sat change)
         "sat85": float(np.quantile(C, 0.85))}
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
        # 85th percentile of chroma, not the mean: a saturation boost pulls weak pixels over the
        # colourful threshold, which drags a mean down and makes fitting run away
        o = np.argsort(C)
        cw = np.cumsum(wi[o])
        c85 = C[o][np.searchsorted(cw, 0.85 * cw[-1])]
        s["bands"][name] = (c85, np.degrees(np.arctan2(mb, ma)) % 360, wi.sum())
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


def fit_step(lk, cur, ref, damp=0.6, parts=("tone", "cast", "colour")):
    """One damped correction toward the reference statistics.

    `parts` picks what may change: a reference set whose content is unusual (flowers on black,
    a handful of images) should only fit "cast" and "colour", since its brightness histogram
    says more about the subjects than about the photographer's tone curve.
    Clean-picture guards: black point lift is capped, and the red/orange bands (skin) may
    only shift hue a little.
    """
    lk = copy.deepcopy(lk)
    if "tone" not in parts:
        cur = dict(cur, L=ref["L"])  # makes the tone step a no-op
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
    after[0] = min(after[0], 0.03)  # clean blacks
    after = np.maximum.accumulate(after)
    lk["post_curve"] = [[float(x), float(min(y, 1.0))] for x, y in zip(grid, after)]
    if "tone" not in parts:
        lk.pop("post_curve")

    # cast: add the difference in neutral a/b per range
    sab = lk.setdefault("split_ab", {})
    for k in (("shadows", "mids", "highlights") if "cast" in parts else ()):
        c, r = cur["cast"][k], ref["cast"][k]
        if c and r:
            old = sab.get(k, [0.0, 0.0])
            sab[k] = [old[0] + (r[0] - c[0]) * damp, old[1] + (r[1] - c[1]) * damp]

    # colour: overall saturation only. Per-hue saturation of a few dozen photos mostly reflects
    # their subjects (the two halves of one photographer's set differ by up to 2x per band), so
    # the per-hue character stays as hand-tuned from research and is not fitted.
    # The test photos (Kodak slides) are more saturated than ordinary photos and the references
    # are mostly print scans, so the raw saturation gap is biased toward "less saturated": the
    # fitted value may only move +-0.2 from the researched one.
    if "colour" in parts:
        base = lk.setdefault("sat_researched", lk.get("sat", 0.0))
        ratio = float(np.clip((ref["sat85"] / max(cur["sat85"], 1e-4)) ** damp, 0.8, 1.25))
        lk["sat"] = float(np.clip((1 + lk.get("sat", 0.0)) * ratio - 1, base - 0.2, base + 0.2))
    return lk


def make_clean(lk, steps=80):
    """Back off only the parts of a look that break the clean-picture limits, a little at a
    time, until it passes (or `steps` run out). Everything else in the look is kept."""
    from build import clean_check
    lk = copy.deepcopy(lk)
    for _ in range(steps):
        fails, m = clean_check(lk)
        if not fails:
            return lk, []
        pc = lk.get("post_curve")
        if "blackLv" in fails:
            if pc:
                for p in pc:
                    if p[0] <= 0.2:
                        p[1] = max(0.0, p[1] - 0.01 * (1 - p[0] / 0.2))
            if lk.get("curve"):
                x0, y0 = lk["curve"][0]
                lk["curve"][0] = (x0, max(0.0, y0 - 0.005))
            lk["black"] = tuple(max(0.0, v - 0.005) for v in lk.get("black", (0, 0, 0)))
        if "whiteLv" in fails:
            if pc:
                for p in pc:
                    if p[0] >= 0.8:
                        p[1] = p[1] + 0.35 * (p[0] - p[1]) if p[1] < p[0] else p[1]
                pc[-1][1] = 1.0
                for i in range(1, len(pc)):
                    pc[i][1] = max(pc[i][1], pc[i - 1][1])
            if lk.get("curve"):
                x1, y1 = lk["curve"][-1]
                lk["curve"][-1] = (x1, min(1.0, y1 + 0.01))
            lk["white"] = tuple(min(1.0, v + 0.01) for v in lk.get("white", (1, 1, 1)))
            lk["clean"] = min(1.0, lk.get("clean", 0.85) + 0.05)
        if "white" in fails or "grey92" in fails:
            lk["clean"] = min(1.0, lk.get("clean", 0.85) + 0.05)
            sab = lk.get("split_ab", {})
            if "highlights" in sab:
                sab["highlights"] = [v * 0.8 for v in sab["highlights"]]
            sp = lk.get("split", {})
            for key in ("highlights", "all"):
                if key in sp:
                    sp[key] = (sp[key][0], sp[key][1] * 0.8)
            if "mids" in sab:
                sab["mids"] = [v * 0.9 for v in sab["mids"]]
        if "skinHue" in fails:
            if lk.get("skin_protect", 0.7) < 1.0:
                lk["skin_protect"] = min(1.0, lk.get("skin_protect", 0.7) + 0.1)
            else:
                hsl = lk.setdefault("hsl", {})
                for band in ("red", "orange", "yellow", "magenta"):  # bands that reach skin hues
                    if band in hsl:
                        v = list(hsl[band]); v[0] *= 0.7; hsl[band] = v
                for key in ("mids", "shadows", "highlights"):
                    if key in lk.get("split_ab", {}):
                        lk["split_ab"][key] = [v * 0.85 for v in lk["split_ab"][key]]
                    if key in lk.get("split", {}):
                        lk["split"][key] = (lk["split"][key][0], lk["split"][key][1] * 0.85)
                lk["temp"] = lk.get("temp", 0) * 0.85
                lk["tint"] = lk.get("tint", 0) * 0.85
        if "shadowDetail" in fails:
            if pc:
                for p in pc:
                    if p[0] <= 0.4:
                        p[1] = p[1] + 0.012 * (1 - abs(p[0] - 0.2) / 0.2) if p[0] > 0 else p[1]
                for i in range(1, len(pc)):
                    pc[i][1] = max(pc[i][1], pc[i - 1][1])
            if lk.get("curve"):
                cv = [list(p) for p in lk["curve"]]
                for p in cv:
                    if 0 < p[0] <= 0.4:
                        p[1] = p[1] + 0.01
                for i in range(1, len(cv)):
                    cv[i][1] = max(cv[i][1], cv[i - 1][1])
                lk["curve"] = [tuple(p) for p in cv]
        if "hlDetail" in fails:
            if pc:
                for p in pc:
                    if 0.7 <= p[0] < 1.0:
                        p[1] = max(0.0, p[1] - 0.01)
            if lk.get("curve"):
                lk["curve"] = [(x, y - 0.01 if 0.7 <= x < 1.0 else y) for x, y in lk["curve"]]
        if "skinC" in fails:
            lo, hi = m["skinC"]
            hsl = lk.setdefault("hsl", {})
            for band in ("red", "orange"):
                v = list(hsl.get(band, (0, 0, 0)))
                v[1] = v[1] - 0.06 if hi > CLEAN_HI else v[1] + 0.05
                hsl[band] = v
    return lk, clean_check(lk)[0]


CLEAN_HI = 1.7


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
