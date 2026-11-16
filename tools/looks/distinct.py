"""How different every look is from every other one, measured on its real output.

    python3 distinct.py            table of the closest pairs; exit 1 when two looks are nearly the same

Each look is applied to the same 1,300 colours (a 10x10x10 RGB grid plus skin, sky, foliage and grey
ramps); the distance between two looks is the mean OKLab difference x100 of their results (1 = just
visible, 2-3 = clearly different side by side). Spatial effects (grain, glow, vignette) are not
counted, so this is a lower bound on how different the looks are.
"""
import sys
import numpy as np
import look as engine
from colorlib import linear_to_oklab, srgb_to_linear
from films import FILMS, SHIP

MIN = 1.2


def colours():
    g = np.linspace(0.04, 0.96, 10)
    grid = np.array([[r, gg, b] for r in g for gg in g for b in g])
    ramp = lambda c: np.array([np.array(c) / 255 * k for k in np.linspace(0.3, 1.25, 30)]).clip(0, 1)
    extra = np.concatenate([ramp(c) for c in [(194, 150, 130), (115, 82, 68), (226, 186, 160), (110, 160, 220), (70, 110, 60), (140, 170, 80), (200, 60, 50), (240, 200, 90)]]
                           + [np.repeat(np.linspace(0, 1, 60)[:, None], 3, 1)])
    return np.concatenate([grid, extra])


def main():
    x = colours()
    lab = {k: linear_to_oklab(srgb_to_linear(np.clip(engine.apply(FILMS[k]["colour"], x), 0, 1))) for k in SHIP}
    base = linear_to_oklab(srgb_to_linear(x))
    pairs = []
    for i, a in enumerate(SHIP):
        for b in SHIP[i + 1:]:
            pairs.append((float(np.linalg.norm(lab[a] - lab[b], axis=1).mean() * 100), a, b))
    pairs.sort()
    strength = sorted((float(np.linalg.norm(lab[k] - base, axis=1).mean() * 100), k) for k in SHIP)
    print(f"{len(SHIP)} looks, {len(pairs)} pairs; closest:")
    for d, a, b in pairs[:14]:
        print(f"  {d:5.2f}  {a} / {b}")
    print("weakest looks (distance from the unfiltered photo):", ", ".join(f"{k} {d:.1f}" for d, k in strength[:6]))
    print("strongest:", ", ".join(f"{k} {d:.1f}" for d, k in strength[-4:]))
    # pairs of two older looks are reported but not judged here: those looks are frozen (their goldens are recorded)
    from pro import PRO_SHIP
    bad = [p for p in pairs if p[0] < MIN and (p[1] in PRO_SHIP or p[2] in PRO_SHIP)]
    if bad:
        sys.exit(f"{len(bad)} pair(s) closer than {MIN}: " + ", ".join(f"{a}/{b} {d:.2f}" for d, a, b in bad))


if __name__ == "__main__":
    main()
