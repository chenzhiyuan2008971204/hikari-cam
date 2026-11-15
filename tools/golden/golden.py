"""Golden-image and image-quality tests for every look, on the shipped GLSL.

    python3 tools/golden/golden.py            compare against the stored goldens, run the quality checks
    python3 tools/golden/golden.py --update   re-record the goldens (only after a deliberate look change)
    python3 tools/golden/golden.py --sheets DIR   also write one large preview sheet per look

Every look is rendered by tools/engine/harness.js (the same shaders, LUTs and parameters the app
ships) on the bundled standard photos (app/src/main/assets/samples).

Golden comparison: the render is area-averaged to 96 px and compared with the stored golden.
Averaging removes film grain and sub-pixel GPU differences; what is left is tone and colour.
  pass: mean difference <= 1.2 levels and 99th percentile <= 5 levels (of 255)

Quality checks per look (measured on the 512 px render against the unfiltered photo):
  cast       shift of neutral greys in OKLab a/b: the look may tint, but within its design limit
  clip       new blown highlights (all channels >= 250 where the source was not)
  crush      new crushed shadows (all channels <= 1 where the source was not)
  highlight  tonal range kept in the bright part of the photo (source luma 0.75 .. 0.97)
  skin       hue shift and chroma ratio of skin on the two portraits
  banding    largest step on a smooth sky gradient and on a grey ramp, effects off
Exit code 1 when anything fails. The report is written to reports/golden/.
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile
import time

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
FILTERS = os.path.join(ROOT, "engine/src/main/assets/filters")
SAMPLES = os.path.join(ROOT, "app/src/main/assets/samples")
GOLDEN = os.path.join(HERE, "golden")
REPORT = os.path.join(ROOT, "reports/golden")
sys.path.insert(0, os.path.join(ROOT, "tools/looks"))
from colorlib import linear_to_oklab, srgb_to_linear  # noqa: E402

WIDTH = 512
SMALL = 96
MEAN_TOL, P99_TOL = 1.2, 5.0
# limits; a look that is meant to break one says so here, with the reason
LIMITS = dict(cast=0.050, clip=0.030, crush=0.030, highlight=0.30, skinHue=12.0, skinLo=0.55, skinHi=1.40, band=3.0)
ALLOW = {
    # special-effect looks: glow lifts and tints bright areas on purpose, fade lifts the blacks
    "night_bloom": dict(clip=0.08, cast=0.07), "dream1998": dict(clip=0.08, cast=0.07, highlight=0.2),
    "flash_diary": dict(clip=0.06), "ccd2000": dict(clip=0.05),
    "silver_bypass": dict(skinLo=0.3), "gold_memory": dict(cast=0.065), "tokyo_blue": dict(cast=0.065),
}
OFF = dict(grain=0, halation=0, bloom=0, vignette=0, aberration=0, softFocus=0, mist=0, edgeSoft=0, dust=0, scratches=0, leak=0, uneven=0, drift=0)


def lab(a):
    return linear_to_oklab(srgb_to_linear(a / 255.0))


def small(im):
    w, h = im.size
    s = SMALL / max(w, h)
    return im.resize((max(1, round(w * s)), max(1, round(h * s))), Image.BOX)


def run(jobs, tmp):
    p = os.path.join(tmp, "jobs.json")
    json.dump(jobs, open(p, "w"))
    subprocess.run(["node", os.path.join(ROOT, "tools/engine/harness.js"), p], check=True, stdout=subprocess.DEVNULL)


def quality(src, out, portrait, mono):
    s, o = src.astype(int), out.astype(int)
    r = {}
    r["clip"] = float(((o.min(-1) >= 250) & (s.min(-1) < 250)).mean())
    r["crush"] = float(((o.max(-1) <= 1) & (s.max(-1) > 1)).mean())
    ls, lo = lab(src.astype(float)), lab(out.astype(float))
    cs = np.hypot(ls[..., 1], ls[..., 2])
    neutral = (cs < 0.02) & (ls[..., 0] > 0.3) & (ls[..., 0] < 0.9)
    if neutral.mean() > 0.02 and not mono:
        r["cast"] = float(np.hypot((lo[..., 1] - ls[..., 1])[neutral].mean(), (lo[..., 2] - ls[..., 2])[neutral].mean()))
    ys = src @ np.array([0.2126, 0.7152, 0.0722]) / 255.0
    yo = out @ np.array([0.2126, 0.7152, 0.0722]) / 255.0
    hi = (ys > 0.75) & (ys < 0.97)
    if hi.mean() > 0.03:
        a = np.percentile(ys[hi], 95) - np.percentile(ys[hi], 5)
        b = np.percentile(yo[hi], 95) - np.percentile(yo[hi], 5)
        r["highlight"] = float(b / max(a, 1e-6))
    if portrait and not mono:
        co = np.hypot(lo[..., 1], lo[..., 2])
        h = np.degrees(np.arctan2(ls[..., 2], ls[..., 1])) % 360
        m = (h > 30) & (h < 80) & (cs > 0.03) & (cs < 0.16) & (ls[..., 0] > 0.45) & (ls[..., 0] < 0.92)
        if m.sum() > 500:
            ho = np.degrees(np.arctan2(lo[..., 2], lo[..., 1]))
            r["skinHue"] = float(np.mean(((ho[m] - h[m] + 180) % 360) - 180))
            r["skinC"] = float(co[m].mean() / cs[m].mean())
    return r


def ramps(tmp):
    x = np.linspace(0, 1, 1024)[None, :, None]
    sky = (np.array([70, 130, 215]) * (1 - x) + np.array([215, 232, 246]) * x).repeat(48, 0)
    grey = (255 * x * np.ones(3)).repeat(48, 0)
    for n, a in (("sky", sky), ("grey", grey)):
        Image.fromarray(a.round().astype(np.uint8)).save(os.path.join(tmp, f"ramp_{n}.png"))


def band(path):
    a = np.asarray(Image.open(path).convert("RGB")).astype(float).mean(0)      # column means
    k = np.ones(9) / 9
    sm = np.stack([np.convolve(a[:, c], k, "valid") for c in range(3)], 1)
    return float(np.abs(np.diff(sm, axis=0)).max() * 8)                          # step per 8 px, in levels


def sheet(title, tiles, out, tile_w=420, cols=4):
    ims = []
    for label, im in tiles:
        t = im.copy()
        t.thumbnail((tile_w, tile_w))
        ims.append((label, t))
    rows = [ims[i:i + cols] for i in range(0, len(ims), cols)]
    hs = [max(t.height for _, t in r) + 26 for r in rows]
    sh = Image.new("RGB", (cols * (tile_w + 6) + 6, sum(hs) + 44), (16, 16, 16))
    d = ImageDraw.Draw(sh)
    d.text((8, 12), title, fill=(255, 255, 255))
    y = 40
    for r, h in zip(rows, hs):
        for i, (label, t) in enumerate(r):
            x = 6 + i * (tile_w + 6)
            sh.paste(t, (x + (tile_w - t.width) // 2, y))
            d.text((x + 4, y + h - 22), label, fill=(210, 210, 210))
        y += h
    sh.save(out, quality=88)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--update", action="store_true")
    ap.add_argument("--sheets")
    ap.add_argument("--looks")
    args = ap.parse_args()
    t0 = time.time()
    index = json.load(open(os.path.join(FILTERS, "index.json")))
    ids = [x if isinstance(x, str) else x["id"] for x in (index["filters"] if isinstance(index, dict) else index)]
    ids = [i.replace(".json", "") for i in ids]
    if args.looks:
        ids = [i for i in ids if i in args.looks.split(",")]
    samples = json.load(open(os.path.join(SAMPLES, "samples.json")))
    os.makedirs(REPORT, exist_ok=True)
    fails, lines, table = [], [], {}
    with tempfile.TemporaryDirectory() as tmp:
        srcs = {}
        for s in samples:
            im = Image.open(os.path.join(SAMPLES, s["file"])).convert("RGB")
            if im.width != WIDTH:
                im = im.resize((WIDTH, round(im.height * WIDTH / im.width)), Image.LANCZOS)
            p = os.path.join(tmp, s["id"] + ".png")
            im.save(p)
            srcs[s["id"]] = (p, np.asarray(im))
        ramps(tmp)
        jobs = []
        for i in ids:
            f = os.path.join(FILTERS, i + ".json")
            for s in samples:
                jobs.append({"in": srcs[s["id"]][0], "out": os.path.join(tmp, f"{i}__{s['id']}.png"), "filter": f, "width": WIDTH, "seed": 7})
            for n in ("sky", "grey"):
                jobs.append({"in": os.path.join(tmp, f"ramp_{n}.png"), "out": os.path.join(tmp, f"{i}__ramp_{n}.png"), "filter": f, "adjust": OFF, "seed": 7})
        run(jobs, tmp)
        worst_mean = worst_p99 = 0.0
        for i in ids:
            spec = json.load(open(os.path.join(FILTERS, i + ".json")))
            mono = spec.get("traits", {}).get("mono", 0) > 0.5
            lim = dict(LIMITS, **ALLOW.get(i, {}))
            agg = {}
            tiles = []
            for s in samples:
                out = Image.open(os.path.join(tmp, f"{i}__{s['id']}.png")).convert("RGB")
                tiles.append((s["zh"], out))
                g = os.path.join(GOLDEN, i, s["id"] + ".png")
                sm = small(out)
                if args.update:
                    os.makedirs(os.path.dirname(g), exist_ok=True)
                    sm.save(g, optimize=True)
                elif not os.path.exists(g):
                    fails.append(f"{i}/{s['id']}: no golden recorded")
                else:
                    d = np.abs(np.asarray(sm).astype(float) - np.asarray(Image.open(g).convert("RGB")).astype(float))
                    m, p99 = d.mean(), np.percentile(d, 99)
                    worst_mean, worst_p99 = max(worst_mean, m), max(worst_p99, p99)
                    if m > MEAN_TOL or p99 > P99_TOL:
                        fails.append(f"{i}/{s['id']}: differs from golden (mean {m:.2f}, p99 {p99:.1f} levels)")
                q = quality(srcs[s["id"]][1], np.asarray(out), s["id"] in ("portrait", "child"), mono)
                for k, v in q.items():
                    agg.setdefault(k, []).append((v, s["id"]))
            row = {}
            def worst(k, f=max):
                if k not in agg: return None
                v, sid = f(agg[k]); row[k] = v; return v, sid
            for k in ("clip", "crush", "cast"):
                w = worst(k)
                if w and w[0] > lim[k]: fails.append(f"{i}: {k} {w[0]:.3f} on {w[1]} (limit {lim[k]})")
            w = worst("highlight", min)
            if w and w[0] < lim["highlight"]: fails.append(f"{i}: highlight range kept {w[0]:.2f} on {w[1]} (limit {lim['highlight']})")
            if "skinHue" in agg:
                v, sid = max(agg["skinHue"], key=lambda t: abs(t[0])); row["skinHue"] = v
                if abs(v) > lim["skinHue"]: fails.append(f"{i}: skin hue shift {v:+.1f} deg on {sid}")
                for v, sid in agg["skinC"]:
                    if not lim["skinLo"] <= v <= lim["skinHi"]: fails.append(f"{i}: skin chroma x{v:.2f} on {sid}")
                row["skinC"] = float(np.mean([v for v, _ in agg["skinC"]]))
            b = max(band(os.path.join(tmp, f"{i}__ramp_sky.png")), band(os.path.join(tmp, f"{i}__ramp_grey.png")))
            row["band"] = b
            # the ramps span about 2 levels per 8 px before the look; contrast may steepen that, steps may not appear
            if b > lim["band"] * 2.2: fails.append(f"{i}: banding step {b:.1f} levels per 8 px")
            table[i] = row
            if args.sheets:
                os.makedirs(args.sheets, exist_ok=True)
                sheet(f"{spec['name']}  ({i})  - real engine output on the standard photos", tiles, os.path.join(args.sheets, f"{i}.jpg"))
        if args.sheets:
            sheet("Originals (no filter)", [(s["zh"], Image.fromarray(srcs[s["id"]][1])) for s in samples], os.path.join(args.sheets, "_originals.jpg"))
        # self-test: the comparison must notice a wrong render (another look against this golden)
        if not args.update and len(ids) >= 2:
            a, b = ids[0], ids[1]
            sm = small(Image.open(os.path.join(tmp, f"{b}__sunny.png")).convert("RGB"))
            d = np.abs(np.asarray(sm).astype(float) - np.asarray(Image.open(os.path.join(GOLDEN, a, "sunny.png")).convert("RGB")).astype(float))
            caught = d.mean() > MEAN_TOL or np.percentile(d, 99) > P99_TOL
            lines.append(f"self-test (render of {b} against golden of {a} must fail): {'caught' if caught else 'NOT CAUGHT'} (mean {d.mean():.2f})")
            if not caught: fails.append("self-test: a wrong render passed the golden comparison")
    hdr = f"{'look':18} {'clip':>6} {'crush':>6} {'cast':>6} {'hl':>5} {'skinHue':>8} {'skinC':>6} {'band':>5}"
    lines.append(hdr)
    for i, r in table.items():
        f = lambda k, fmt: (fmt % r[k]) if k in r else "-"
        lines.append(f"{i:18} {f('clip', '%.3f'):>6} {f('crush', '%.3f'):>6} {f('cast', '%.3f'):>6} {f('highlight', '%.2f'):>5} {f('skinHue', '%+.1f'):>8} {f('skinC', '%.2f'):>6} {f('band', '%.1f'):>5}")
    head = [f"golden + quality: {len(ids)} looks x {len(samples)} photos = {len(ids) * len(samples)} renders, {time.time() - t0:.0f} s",
            "mode: " + ("UPDATE (goldens re-recorded)" if args.update else f"compare, tolerance mean {MEAN_TOL} / p99 {P99_TOL} levels; worst seen mean {worst_mean:.2f} / p99 {worst_p99:.1f}"),
            f"result: {'PASS' if not fails else 'FAIL (%d)' % len(fails)}"] + ["  FAIL " + x for x in fails]
    text = "\n".join(head + [""] + lines) + "\n"
    open(os.path.join(REPORT, "golden_report.txt"), "w").write(text)
    json.dump(dict(fails=fails, table=table), open(os.path.join(REPORT, "golden_report.json"), "w"), indent=1)
    print(text)
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
