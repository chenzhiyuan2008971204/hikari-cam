"""Pick, for every look, the refinement variant that measures best on real photos.

    python3 refine_prune.py [--in refine_params.json] [--out refine_params.json]

refine_fit.py fits all amounts together on pixels. Here each look's fitted refinement is tried as four
variants, baked to the LUT the GPU would use and measured with quality.py on the standard photos plus
indoor shots (local contrast, not just value spread):
  full        everything the fit found
  colour      only the colour corrections (mud, skin, plant, sky, neon)
  colour+toe  colour corrections and the black-level nudge
  none        the look as it was
and the best one by one score is kept. A look whose best variant is "none" is left untouched.
"""
import json, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build import bake
from quality import measure, photos
from refine import NEUTRAL, refine_look

TONE = ("shadow_open", "hl_open", "mid_open")


def score(m, m0):
    s = 4.0 * m["clipLo"] + 5.0 * m["clipHi"] + 8.0 * m["neon"] + 1.0 * m["mud"]
    s += 100 * max(0.0, 0.95 - m["shKeep"]) + 100 * max(0.0, 0.95 - m["hlKeep"])
    # banding and noise amplification: the LUT must not get steeper than it was
    s += 12 * max(0.0, m["roughMid"] - m0["roughMid"] * 1.05) + 0.5 * max(0.0, m["rampStep"] - m0["rampStep"] * 1.08)
    s += 0.8 * max(0.0, abs(m["skinH"]) - 3.0) ** 2 if m["skinC"] > 0.05 else 0.0
    s += 100 * max(0.0, 0.6 - m["grnC"]) if m["skinC"] > 0.05 else 0.0
    return s


def main():
    from films import BASE, SHIP
    a = sys.argv[1:]
    src = a[a.index("--in") + 1] if "--in" in a else os.path.join(os.path.dirname(os.path.abspath(__file__)), "refine_params.json")
    out = a[a.index("--out") + 1] if "--out" in a else src
    fit = json.load(open(src))
    imgs = photos()
    res = {}
    for k in SHIP:
        if k not in fit: continue
        full = dict(NEUTRAL, **fit[k]["params"])
        def scaled(p, f):
            q = dict(NEUTRAL)
            for n, v in p.items():
                if n == "neon": q[n] = 1 - (1 - v) * f
                else: q[n] = v * f
            return q
        colourOnly = {n: v for n, v in full.items() if n not in TONE and n != "toe"}
        variants = {"none": dict(NEUTRAL)}
        for f in (1.0, 0.6, 0.3):
            variants[f"full x{f}"] = scaled(full, f)
            variants[f"colour x{f}"] = scaled(dict(NEUTRAL, **colourOnly), f)
            variants[f"colour+toe x{f}"] = scaled(dict(NEUTRAL, **colourOnly, toe=full["toe"]), f)
        m = {}
        for name, p in variants.items():
            lk = refine_look(BASE[k], p) if name != "none" else BASE[k]
            m[name] = measure(bake(lk), imgs)
        m0 = m["none"]
        sc = {name: score(v, m0) for name, v in m.items()}
        # a tie goes to the smaller change
        order = list(variants)[:1] + [n for f in (0.3, 0.6, 1.0) for n in (f"colour x{f}", f"colour+toe x{f}", f"full x{f}")]
        best = min(order, key=lambda n: (round(sc[n], 3), order.index(n)))
        keep = {n: round(v, 4) for n, v in variants[best].items() if v != NEUTRAL[n]}
        res[k] = dict(params=keep, variant=best, scores={n: round(v, 2) for n, v in sc.items()},
                      before={x: m0[x] for x in ("clipLo", "clipHi", "hlKeep", "shKeep", "neon", "mud", "rough", "roughMid", "skinH", "skyH", "grnH", "grnC")},
                      after={x: m[best][x] for x in ("clipLo", "clipHi", "hlKeep", "shKeep", "neon", "mud", "rough", "roughMid", "skinH", "skyH", "grnH", "grnC")},
                      fit=fit[k])
        print(f"{k:18} {best:16} none {sc['none']:6.2f} best {sc[best]:6.2f}", flush=True)
    json.dump(res, open(out, "w"), indent=1)


if __name__ == "__main__":
    main()
