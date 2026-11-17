"""Preview sheets of the look library by category: every look on the same six standard photos.

    python3 sheet_library.py OUT_DIR [series ...]

Colour only (the baked recipe through look.py); grain, glow and vignette are added by the GPU engine,
whose own previews are written by tools/golden/golden.py --sheets.
"""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import look as engine
from films import FILMS, SHIP

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
S = os.path.join(ROOT, "app/src/main/assets/samples")
PHOTOS = ["01_sunny", "05_portrait", "03_foliage", "02_overcast", "10_child", "14_nightstreet"]
ZH = dict(fuji="Fujifilm 底片模擬", cinema="Cinema 電影色彩", japanese="Japanese 日系", pro="Pro 專業攝影", vintage="Vintage 復古底片",
          sim="Film Simulation", daylight="Daylight", film="Film", ccd="CCD", special="Special")


def font(n):
    for f in ("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",):
        if os.path.exists(f): return ImageFont.truetype(f, n)
    return ImageFont.load_default()


def main():
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    want = sys.argv[2:] or sorted({FILMS[k]["series"] for k in SHIP}, key=lambda s: list(ZH).index(s))
    H = 210
    src = []
    for p in PHOTOS:
        im = Image.open(os.path.join(S, p + ".jpg")).convert("RGB"); im = im.resize((round(im.width * H / im.height), H), Image.LANCZOS); src.append(np.asarray(im) / 255.0)
    W = sum(a.shape[1] for a in src) + 6 * len(src)
    for series in want:
        keys = [k for k in SHIP if FILMS[k]["series"] == series]
        sh = Image.new("RGB", (W + 250, 50 + (len(keys) + 1) * (H + 6)), (22, 22, 24)); d = ImageDraw.Draw(sh)
        d.text((10, 10), f"{ZH.get(series, series)}  ·  {len(keys)} 款  ·  同一組標準照片", fill=(255, 255, 255), font=font(24))
        for r, k in enumerate([None] + keys):
            y = 50 + r * (H + 6); x = 250
            d.text((10, y + 70), "原圖" if k is None else FILMS[k]["name"], fill=(255, 255, 255), font=font(20))
            if k: d.text((10, y + 100), FILMS[k]["zh"], fill=(170, 170, 170), font=font(15))
            for a in src:
                o = a if k is None else np.clip(engine.apply(FILMS[k]["colour"], a.reshape(-1, 3)).reshape(a.shape), 0, 1)
                sh.paste(Image.fromarray((o * 255 + 0.5).astype(np.uint8)), (x, y)); x += a.shape[1] + 6
        sh.save(os.path.join(out, f"looks_{series}.jpg"), quality=86)
        print(series, len(keys), sh.size)


if __name__ == "__main__":
    main()
