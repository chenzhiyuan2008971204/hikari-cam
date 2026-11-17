"""Fit the quality refinement (refine.py) of every shipped look, one look at a time.

    python3 refine_fit.py [--ids a,b] [--out refine_params.json] [--workers N]

For each look the fit starts from the look as it is today and searches its own refinement amounts
(coordinate search over refine.KNOBS) that minimise a cost made of

  quality defects, measured on pixels of 8 test photographs (daylight landscape, blue sky, plants,
  building, portrait, overcast, night street, indoor):
      crushed shadows, blown highlights, lost shadow / highlight separation, dirty (grey, darkened)
      colour, over-saturated colour, skin hue and lightness, heavy dark-green foliage, banding;
  style drift from the look as it is today (the look must stay the same look):
      average colour distance, mean lightness, mean chroma, the colour cast of shadows, mids and
      highlights, contrast.

The defect targets are the same for every look, the amounts that reach them are not: a look that
already has clean shadows gets no shadow correction, one that crushes them gets as much as its style
allows. Results (amounts, the measured defects before and after, the style drift) go to the json.
"""
import json, multiprocessing as mp, os, sys, time
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import look as engine  # noqa: E402
from colorlib import linear_to_oklab, srgb_to_linear  # noqa: E402
from refine import KNOBS, NEUTRAL, RANGES, refine_look  # noqa: E402
from quality import photos  # noqa: E402
from build import SKINS  # noqa: E402

SCENES = ("sunny", "foliage", "portrait", "overcast", "nightstreet", "indoor1", "city", "seaside")
_X = None


def pixels():
    global _X
    if _X is None:
        ims = photos(110)
        _X = np.concatenate([ims[s].reshape(-1, 3) for s in SCENES if s in ims])
    return _X


def lab(rgb):
    return linear_to_oklab(srgb_to_linear(np.clip(rgb, 0, 1)))


def hue(l):
    return np.degrees(np.arctan2(l[..., 2], l[..., 1])) % 360


def dhue(a, b):
    return (b - a + 180) % 360 - 180


def spread(v):
    return float(np.percentile(v, 90) - np.percentile(v, 10)) if len(v) > 20 else 0.0


class Ref:
    """Input-side facts that do not change between evaluations."""
    def __init__(self, X):
        self.X = X
        self.a = lab(X)
        self.C = np.hypot(self.a[:, 1], self.a[:, 2])
        self.H = hue(self.a)
        L = self.a[:, 0]
        self.mx, self.mn = X.max(1), X.min(1)
        self.dark = L <= np.percentile(L, 15)
        self.bright = L >= np.percentile(L, 92)
        self.skin = (self.H > 38) & (self.H < 72) & (self.C > 0.04) & (self.C < 0.14) & (L > 0.4) & (L < 0.92)
        self.green = (self.H > 105) & (self.H < 170) & (self.C > 0.05)
        self.sky = (self.H > 225) & (self.H < 265) & (self.C > 0.05) & (L > 0.5)
        self.band = [L < 0.35, (L >= 0.35) & (L < 0.7), L >= 0.7]
        self.skinP = np.array([np.array(v) / 255 for v in SKINS.values()])
        self.skinPl = lab(self.skinP)


def metrics(ref, out):
    b = lab(out)
    a = ref.a
    Cb = np.hypot(b[:, 1], b[:, 2])
    m = dict(
        clipLo=float(((out.min(1) <= 0.012) & (ref.mn >= 0.07)).mean() * 100),
        clipHi=float(((out.max(1) >= 0.99) & (ref.mx <= 0.9)).mean() * 100),
        shKeep=spread(b[ref.dark, 0]) / max(spread(a[ref.dark, 0]), 1e-6),
        hlKeep=spread(b[ref.bright, 0]) / max(spread(a[ref.bright, 0]), 1e-6),
        neon=float((Cb > 0.24).mean() * 100),
        mud=float(((ref.C > 0.05) & (ref.C < 0.16) & (b[:, 0] < a[:, 0] - 0.02) & (Cb < ref.C * 0.8)).mean() * 100),
    )
    m["skinH"] = float(np.abs(dhue(ref.H[ref.skin], hue(b)[ref.skin])).mean()) if ref.skin.any() else 0.0
    m["skinL"] = float((b[ref.skin, 0] - a[ref.skin, 0]).mean()) if ref.skin.any() else 0.0
    m["greenL"] = float((b[ref.green, 0] - a[ref.green, 0]).mean()) if ref.green.any() else 0.0
    m["greenC"] = float((Cb[ref.green] / ref.C[ref.green]).mean()) if ref.green.any() else 1.0
    m["skyH"] = float(np.abs(dhue(ref.H[ref.sky], hue(b)[ref.sky])).mean()) if ref.sky.any() else 0.0
    return m, b


def style_drift(ref, b0, b, mono=False):
    """How far the look moved from its current self (OKLab, x100 for distances)."""
    d = dict(
        dE=float(np.linalg.norm(b - b0, axis=1).mean() * 100),
        dL=float(b[:, 0].mean() - b0[:, 0].mean()),
        dC=0.0 if mono else float(np.hypot(b[:, 1], b[:, 2]).mean() / max(np.hypot(b0[:, 1], b0[:, 2]).mean(), 1e-6) - 1),
        dContrast=float(b[:, 0].std() / max(b0[:, 0].std(), 1e-6) - 1),
    )
    cast = 0.0
    for m in ref.band:
        m = m & ~ref.skin   # skin is corrected on purpose; the cast of everything else must not move
        if m.any():
            cast = max(cast, float(np.hypot(*(b[m, 1:].mean(0) - b0[m, 1:].mean(0)))))
    d["cast"] = cast
    return d


def cost(m, d, mono, monotone):
    c = 3.0 * m["clipLo"] + 5.0 * m["clipHi"]
    c += 90 * max(0.0, 0.95 - m["shKeep"]) + 90 * max(0.0, 0.96 - m["hlKeep"])
    c += 8.0 * m["neon"]
    if not mono:
        c += 1.1 * m["mud"]
        c += 1.5 * max(0.0, m["skinH"] - 2.5) ** 2
        c += 160 * max(0.0, -m["skinL"] - 0.04)
        c += 120 * max(0.0, -m["greenL"] - 0.05)
        c += 30 * max(0.0, 0.62 - m["greenC"])
    if not monotone:
        c += 200
    # style: the look must stay the look
    c += 9.0 * max(0.0, d["dE"] - 2.6) ** 1.5 + 0.15 * d["dE"]
    c += 700 * max(0.0, abs(d["dL"]) - 0.018)
    c += 200 * max(0.0, abs(d["dC"]) - 0.07)
    c += 300 * max(0.0, abs(d["dContrast"]) - 0.035)
    c += 3000 * max(0.0, d["cast"] - 0.008)
    return c


def ramp_monotone(lk):
    r = np.repeat(np.linspace(0, 1, 256)[:, None], 3, 1)
    y = engine.apply(lk, r) @ engine.LUMA
    return bool((np.diff(y) >= -1e-4).all())


def clean_ok(lk):
    from build import clean_check
    return not clean_check(lk)[0]


def fit_one(args):
    key, lk0 = args
    ref = Ref(pixels())
    mono = lk0.get("mono") is not None
    out0 = engine.apply(lk0, ref.X)
    m0, b0 = metrics(ref, out0)
    knobs = [k for k in KNOBS if not (mono and k in ("mud", "skin_hue", "green_light", "green_sat", "sky_hue", "neon"))]
    cur = dict(NEUTRAL)
    steps = {k: (RANGES[k][1] - RANGES[k][0]) / 4 for k in knobs}

    def evaluate(p):
        lk = refine_look(lk0, p)
        out = engine.apply(lk, ref.X)
        m, b = metrics(ref, out)
        return cost(m, style_drift(ref, b0, b, mono), mono, ramp_monotone(lk)) + (0 if clean_ok(lk) else 500), m

    best, _ = evaluate(cur)
    for sweep in range(9):
        improved = False
        for k in knobs:
            for sgn in (1, -1):
                trial = dict(cur)
                trial[k] = float(np.clip(cur[k] + sgn * steps[k], *RANGES[k]))
                if trial[k] == cur[k]:
                    continue
                c, _ = evaluate(trial)
                if c < best - 1e-6:
                    best, cur, improved = c, trial, True
                    break
        if not improved:
            steps = {k: v / 2 for k, v in steps.items()}
    lk1 = refine_look(lk0, cur)
    out1 = engine.apply(lk1, ref.X)
    m1, b1 = metrics(ref, out1)
    return key, dict(params={k: round(v, 4) for k, v in cur.items() if v != NEUTRAL[k]}, before=m0, after=m1,
                     drift=style_drift(ref, b0, b1, mono), cost0=cost(m0, style_drift(ref, b0, b0, mono), mono, True), cost1=best)


def main():
    from films import BASE, SHIP
    a = sys.argv[1:]
    ids = a[a.index("--ids") + 1].split(",") if "--ids" in a else list(SHIP)
    out = a[a.index("--out") + 1] if "--out" in a else os.path.join(HERE, "refine_params.json")
    workers = int(a[a.index("--workers") + 1]) if "--workers" in a else 4
    res = json.load(open(out)) if os.path.exists(out) and "--ids" in a else {}
    t = time.time()
    with mp.Pool(workers) as pool:
        for key, r in pool.imap_unordered(fit_one, [(k, BASE[k]) for k in ids]):
            res[key] = r
            print(f"{key:<18} cost {r['cost0']:6.1f} -> {r['cost1']:6.1f}  dE {r['drift']['dE']:.2f}  {r['params']}", flush=True)
    json.dump({k: res[k] for k in sorted(res, key=lambda k: SHIP.index(k) if k in SHIP else 999)}, open(out, "w"), indent=1)
    print(f"{len(ids)} looks in {time.time() - t:.0f}s -> {out}")


if __name__ == "__main__":
    main()
