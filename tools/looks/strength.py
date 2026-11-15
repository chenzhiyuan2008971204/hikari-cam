"""How strong and how distinct the looks are: mean OKLab ΔE (x100) from the original and between
every pair of looks, on the daylight test photos."""
import glob, os, sys
import numpy as np
from PIL import Image
import look as engine
from colorlib import linear_to_oklab, srgb_to_linear

HERE = os.path.dirname(os.path.abspath(__file__))


def base(n=30000):
    px = []
    for f in sorted(glob.glob(os.path.join(HERE, "photos", "daylight", "*.png"))):
        im = Image.open(f).convert("RGB"); im.thumbnail((320, 320))
        px.append(np.asarray(im, float).reshape(-1, 3) / 255)
    px = np.concatenate(px)
    return px[np.random.default_rng(1).choice(len(px), min(n, len(px)), replace=False)]


def lab(rgb):
    return linear_to_oklab(srgb_to_linear(np.clip(rgb, 0, 1)))


def report(looks, title):
    b = base(); l0 = lab(b)
    out = {k: lab(engine.apply(lk, b)) for k, lk in looks.items()}
    print(f"\n{title}: mean ΔE×100 from original (strength)")
    for k, v in out.items():
        d = np.linalg.norm(v - l0, axis=1) * 100
        print(f"  {k:<17} {d.mean():5.1f}  (L {np.abs(v[:,0]-l0[:,0]).mean()*100:4.1f}, colour {np.linalg.norm(v[:,1:]-l0[:,1:],axis=1).mean()*100:4.1f})")
    ks = list(out); pairs = []
    for i in range(len(ks)):
        for j in range(i + 1, len(ks)):
            pairs.append((np.linalg.norm(out[ks[i]] - out[ks[j]], axis=1).mean() * 100, ks[i], ks[j]))
    pairs.sort()
    print("  closest pairs:", ", ".join(f"{a}~{b2} {d:.1f}" for d, a, b2 in pairs[:5]))


if __name__ == "__main__":
    from films import FILMS
    from film_colours import C
    report({k: f["colour"] for k, f in FILMS.items() if f["series"] != "daylight"}, "film v1")
    report(C, "film v2")
    report({k: f["colour"] for k, f in FILMS.items() if f["series"] == "daylight"}, "daylight")
