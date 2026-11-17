"""Check whether a look actually lands on a photographer's real work, and calibrate it.

    python3 verify.py REFROOT [--photos DIR] [--looks a,b] [--write]

REFROOT holds one folder of reference photographs per look key (e.g. REFROOT/leiter/*.jpg).
For each look the references are split in two: half A is used to calibrate (fit.py), half B is
held out and only used for scoring, so the score is not just the fit memorising its inputs.

Distance to the held-out half (smaller is closer):
  tone    mean gap of the brightness percentiles, in % of the display range
  cast    mean gap of the neutral colour cast in shadows / mids / highlights (OKLab x1000)
  sat     gap of overall saturation (% difference)
  self    the photographer's own half A vs half B: the noise floor of this comparison

Each row compares three things against held-out half B:
  orig    the untouched test photos (how far plain photos are from the style)
  look    the current hand-tuned look
  fitted  the look calibrated on half A
`closed` is how much of the orig->reference gap the final look removes. With --write the fitted
look is saved to fitted/<key>.json (picked up by build.py) when it is closer than the hand-tuned
look and still passes the clean-picture checks.
"""
import argparse
import json
import os

import numpy as np

import look as engine
from build import LOOKS, HERE, clean_report, load_photos
from fit import BANDS, fit_step, make_clean, pixels, stats, to_disp

# Sets whose subjects dominate their brightness histogram: calibrate colour only.
COLOUR_ONLY = {"araki", "ninagawa", "mccurry"}
# Sets whose colour cast flips between scenes (green day / blue night in Chungking Express):
# the average cast is a colour no scene has, so keep the researched cast and fit the rest.
NO_CAST = {"wkw"}
# Reference sets that mix too many different works to define one look (stills from 20+ Iwai
# films): report the numbers but keep the researched look.
NO_FIT = {"iwai"}


def distance(s, r):
    tone = np.mean([abs(to_disp(x) - to_disp(y)) for x, y in zip(s["L"], r["L"])]) * 100
    cast = np.mean([np.hypot(s["cast"][k][0] - r["cast"][k][0], s["cast"][k][1] - r["cast"][k][1])
                    for k in s["cast"] if s["cast"][k] and r["cast"][k]] or [0]) * 1000
    sat = abs(np.log(max(s["sat85"], 1e-4) / max(r["sat85"], 1e-4))) * 100
    return np.array([tone, cast, sat])


def total(d, mono):
    # equal-ish weights; colour is meaningless for B&W
    return d[0] + d[1] * 0.5 + (0 if mono else d[2] * 0.3)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("refroot")
    ap.add_argument("--photos", default=os.path.join(HERE, "photos"))
    ap.add_argument("--looks")
    ap.add_argument("--iters", type=int, default=4)
    ap.add_argument("--write", action="store_true")
    a = ap.parse_args()

    test = pixels(load_photos(a.photos, 384), n=80000)
    keys = a.looks.split(",") if a.looks else [k for k in LOOKS if os.path.isdir(os.path.join(a.refroot, k))]
    rows = []
    all_fitted = {}
    for k in keys:
        lk = LOOKS[k]
        mono = lk.get("mono") is not None
        refs = load_photos(os.path.join(a.refroot, k), 384)
        A, B = refs[0::2], refs[1::2]
        rA, rB = stats(pixels(A)), stats(pixels(B))
        d_self = distance(rA, rB)  # how far the photographer's own two halves are apart
        d_orig = distance(stats(test), rB)
        d_look = distance(stats(engine.apply(lk, test)), rB)

        parts = ("cast", "colour") if k in COLOUR_ONLY else ("tone", "cast") if mono else ("tone", "cast", "colour")
        if k in NO_CAST:
            parts = tuple(p for p in parts if p != "cast")
        fitted = lk
        for _ in range(a.iters):
            fitted = fit_step(fitted, stats(engine.apply(fitted, test)), rA, parts=parts)
        fitted, still = make_clean(fitted)
        d_fit = distance(stats(engine.apply(fitted, test)), rB)

        t_o, t_l, t_f = total(d_orig, mono), total(d_look, mono), total(d_fit, mono)
        clean_ok = not clean_report({k: fitted}, quiet=True)
        use_fit = t_f < t_l and clean_ok and k not in NO_FIT
        best = t_f if use_fit else t_l
        closed = (t_o - best) / t_o * 100 if t_o > 0 else 0
        rows.append((k, len(refs), d_self, d_orig, d_look, d_fit, closed, use_fit, clean_ok, parts))
        all_fitted[k] = fitted
        if a.write and use_fit:
            os.makedirs(os.path.join(HERE, "fitted"), exist_ok=True)
            with open(os.path.join(HERE, "fitted", k + ".json"), "w") as f:
                json.dump(fitted, f, ensure_ascii=False, indent=1, default=float)

    print(f"{'look':<11}{'refs':>5} {'self tone/cast/sat':>19} {'orig':>17} {'look':>17} {'fitted':>17}  closed  used")
    fmt = lambda d: f"{d[0]:5.1f}/{d[1]:5.1f}/{d[2]:5.1f}"
    for k, n, s_, o, l, f, c, u, ok, parts in rows:
        note = "fitted" if u else ("look (fit not clean)" if not ok else "look")
        print(f"{k:<11}{n:>5} {fmt(s_):>19} {fmt(o):>17} {fmt(l):>17} {fmt(f):>17}  {c:5.0f}%  {note}"
              + ("  [colour-only]" if parts == ("cast", "colour") else ""))
    print("\nclean check of the fitted versions:")
    clean_report(all_fitted)


if __name__ == "__main__":
    main()
