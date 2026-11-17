"""Measured image quality of every shipped look, from its baked LUT (what the GPU applies).

    python3 quality.py LUT_DIR [--json OUT] [--ids a,b]

Every number compares the look's output with the unfiltered photo on the app's 14 standard photos
(plus indoor photos from the Japanese pool), so a look is judged on pictures, not on a single patch.
Lower is better for the "bad" columns; the "keep" columns only say what the look does, they are not
faults (a warm look is meant to move the hue).

  clipHi   % of pixels pushed to white that were clearly below it in the original (blown highlights)
  clipLo   % of pixels pushed to black that were clearly above it (crushed shadows)
  hlKeep   highlight detail kept: local contrast in the brightest 8 % of the original, output / input
  shKeep   shadow detail kept: same for the darkest 15 %
  neon     % of pixels whose output chroma is above a natural ceiling (over-saturated, colour spill)
  mud      % of mid-chroma pixels that lost both lightness and chroma (grey, dirty colour)
  rough    steepest local change of the LUT (OKLab per grid step, 99.9th percentile): a high value
           bands or posterises smooth gradients
  levels   distinct 8-bit output levels over a 256-step grey ramp (fewer = banding)
  mono     the grey ramp is monotonic
  skinH / skinC   hue shift (degrees) and chroma ratio of the three reference skin tones
  skyH / skyC     the same for a blue-sky patch
  grnH / grnC     the same for a foliage-green patch
"""
import glob, json, os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
sys.path.insert(0, HERE)
from colorlib import linear_to_oklab, srgb_to_linear  # noqa: E402
from build import apply_lut, SKINS  # noqa: E402


def find_lut(d, k):
    """The baked LUT of look k in folder d: compact binary (.lut, the app's assets) or .cube text."""
    for ext in (".lut", ".cube"):
        if os.path.exists(os.path.join(d, k + ext)):
            return os.path.join(d, k + ext)
    raise FileNotFoundError(os.path.join(d, k))


def load_cube(path):
    if path.endswith(".lut"):
        import lutbin
        return lutbin.read(path)
    rows, n = [], 0
    for line in open(path):
        if line.startswith("LUT_3D_SIZE"):
            n = int(line.split()[1])
        elif line[:1] in "0123456789.-":
            rows.append([float(x) for x in line.split()])
    return np.array(rows).reshape(n, n, n, 3)


def photos(size=420):
    out = {}
    for p in sorted(glob.glob(os.path.join(ROOT, "app/src/main/assets/samples/*.jpg"))):
        out[os.path.basename(p)[3:-4]] = p
    jp = json.load(open(os.path.join(ROOT, "app/src/main/assets/japan/japan.json")))
    cafes = [o["file"] for o in jp if "cafe" in o.get("tags", [])]
    for i, f in enumerate(cafes[:3]):
        out[f"indoor{i + 1}"] = os.path.join(ROOT, "app/src/main/assets/japan", f)
    res = {}
    for k, p in out.items():
        im = Image.open(p).convert("RGB")
        s = size / max(im.size)
        im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
        res[k] = np.asarray(im) / 255.0
    return res


def lab(rgb):
    return linear_to_oklab(srgb_to_linear(np.clip(rgb, 0, 1)))


def hue(l):
    return np.degrees(np.arctan2(l[..., 2], l[..., 1])) % 360


def dh(a, b):
    return (b - a + 180) % 360 - 180


def local_contrast(L, mask):
    """Std of the 3x3 neighbourhood lightness difference inside mask."""
    d = np.abs(L - np.roll(L, 1, 0)) + np.abs(L - np.roll(L, 1, 1))
    return float(d[mask].mean()) if mask.any() else 0.0


def measure(table, imgs):
    m = dict(clipHi=0.0, clipLo=0.0, neon=0.0, mud=0.0)
    hl_in = hl_out = sh_in = sh_out = 0.0
    n = 0
    per = {}
    for name, im in imgs.items():
        out = np.clip(apply_lut(table, im), 0, 1)
        a, b = lab(im), lab(out)
        mx_in, mx_out = im.max(-1), out.max(-1)
        mn_in, mn_out = im.min(-1), out.min(-1)
        ch_in, ch_out = np.hypot(a[..., 1], a[..., 2]), np.hypot(b[..., 1], b[..., 2])
        v = dict(
            clipHi=float(((mx_out >= 0.99) & (mx_in <= 0.9)).mean() * 100),
            clipLo=float(((mn_out <= 0.012) & (mn_in >= 0.07)).mean() * 100),
            neon=float((ch_out > 0.24).mean() * 100),
            mud=float(((ch_in > 0.05) & (ch_in < 0.16) & (b[..., 0] < a[..., 0] - 0.02) & (ch_out < ch_in * 0.8)).mean() * 100),
        )
        Lin, Lout = a[..., 0], b[..., 0]
        hi = Lin >= np.percentile(Lin, 92)
        lo = Lin <= np.percentile(Lin, 15)
        hl_in += local_contrast(Lin, hi); hl_out += local_contrast(Lout, hi)
        sh_in += local_contrast(Lin, lo); sh_out += local_contrast(Lout, lo)
        per[name] = v
    n = len(imgs)
    for k in ("clipHi", "clipLo", "neon", "mud"):
        m[k] = float(np.mean([per[x][k] for x in per]))
    m["hlKeep"] = hl_out / max(hl_in, 1e-9)
    m["shKeep"] = sh_out / max(sh_in, 1e-9)
    m["per"] = per

    # grey ramp: banding and monotonic
    ramp = np.repeat(np.linspace(0, 1, 256)[:, None], 3, 1)
    ro = apply_lut(table, ramp)
    luma = ro @ np.array([0.2126, 0.7152, 0.0722])
    m["levels"] = int(len({tuple(x) for x in np.round(ro * 255).astype(int)}))
    m["mono"] = bool((np.diff(luma) >= -1e-4).all())
    m["rampStep"] = float(np.abs(np.diff(luma)).max() * 255)

    # LUT roughness in OKLab per grid step
    L = lab(table)
    d = [np.linalg.norm(np.diff(L, axis=ax), axis=-1).ravel() for ax in range(3)]
    m["rough"] = float(np.percentile(np.concatenate(d), 99.9) * 100)

    # reference patches
    def patch(rgbs):
        a = lab(np.array(rgbs, float))
        o = lab(apply_lut(table, np.array(rgbs, float)))
        return float(np.mean(dh(hue(a), hue(o)))), float(np.mean(np.hypot(o[:, 1], o[:, 2]) / np.hypot(a[:, 1], a[:, 2])))
    m["skinH"], m["skinC"] = patch([np.array(v) / 255 for v in SKINS.values()])
    m["skyH"], m["skyC"] = patch([[.35, .55, .85], [.45, .65, .92], [.25, .45, .78]])
    m["grnH"], m["grnC"] = patch([[.25, .5, .18], [.35, .62, .22], [.18, .4, .15]])
    return m


def main():
    d = sys.argv[1]
    only = None
    out = None
    a = sys.argv[2:]
    if "--ids" in a: only = a[a.index("--ids") + 1].split(",")
    if "--json" in a: out = a[a.index("--json") + 1]
    imgs = photos()
    ids = [os.path.basename(f)[:-5] for f in sorted(glob.glob(os.path.join(d, "..", "filters", "*.json"))) if not f.endswith("index.json") and not f.endswith("retired.json")]
    order = json.load(open(os.path.join(d, "..", "filters", "index.json")))
    res = {}
    print(f"{'look':<18}{'clipHi':>7}{'clipLo':>7}{'hlKeep':>7}{'shKeep':>7}{'neon':>6}{'mud':>6}{'rough':>7}{'levels':>7}{'mono':>5}{'skinH':>7}{'skinC':>6}{'skyH':>6}{'skyC':>6}{'grnH':>6}{'grnC':>6}")
    for k in order:
        if only and k not in only: continue
        m = measure(load_cube(find_lut(d, k)), imgs)
        res[k] = m
        print(f"{k:<18}{m['clipHi']:7.2f}{m['clipLo']:7.2f}{m['hlKeep']:7.2f}{m['shKeep']:7.2f}{m['neon']:6.2f}{m['mud']:6.2f}{m['rough']:7.2f}{m['levels']:7d}{'Y' if m['mono'] else 'N':>5}"
              f"{m['skinH']:7.1f}{m['skinC']:6.2f}{m['skyH']:6.1f}{m['skyC']:6.2f}{m['grnH']:6.1f}{m['grnC']:6.2f}")
    if out:
        json.dump(res, open(out, "w"), indent=1)


if __name__ == "__main__":
    main()
