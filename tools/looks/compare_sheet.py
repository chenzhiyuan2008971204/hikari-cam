"""Before/after contact sheets of the shipped looks, from baked LUTs (colour only).

    python3 compare_sheet.py BEFORE_LUTS AFTER_LUTS OUT.jpg id1,id2,... [scene,scene,...] [tile_px]

One row per look: for each scene the original-LUT result next to the new one. With AFTER_LUTS = "-"
only the BEFORE result is drawn.
"""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from quality import find_lut, load_cube, photos
from build import apply_lut

SCENES = {"sunny": "白天藍天", "foliage": "綠色植物", "portrait": "人像", "overcast": "陰天", "nightstreet": "夜景", "indoor1": "室內", "city": "建築", "seaside": "海邊"}


def main():
    b_dir, a_dir, out, ids = sys.argv[1:5]
    scenes = sys.argv[5].split(",") if len(sys.argv) > 5 else ["sunny", "foliage", "portrait", "overcast", "nightstreet", "indoor1"]
    T = int(sys.argv[6]) if len(sys.argv) > 6 else 200
    ids = ids.split(",")
    imgs = photos(T * 2)
    F = ImageFont.truetype("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", 15)
    Fs = ImageFont.truetype("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", 12)
    two = a_dir != "-"
    H = round(T * 0.75)
    col_w = T * (2 if two else 1) + 6
    sh = Image.new("RGB", (130 + col_w * len(scenes), 22 + (len(ids) + 1) * (H + 4)), (24, 24, 26)); d = ImageDraw.Draw(sh)
    for c, s in enumerate(scenes):
        d.text((130 + c * col_w + 4, 3), SCENES.get(s, s) + ("  前｜後" if two else ""), fill=(200, 200, 200), font=Fs)

    def tile(im, table):
        o = im if table is None else np.clip(apply_lut(table, im), 0, 1)
        t = Image.fromarray((o * 255 + .5).astype(np.uint8))
        r = T / t.width
        t = t.resize((T, round(t.height * r)), Image.LANCZOS)
        if t.height > H: t = t.crop((0, (t.height - H) // 2, T, (t.height - H) // 2 + H))
        elif t.height < H: t = t.resize((T, H))
        return t
    rows = [None] + ids
    for r, k in enumerate(rows):
        y = 22 + r * (H + 4)
        if k is None:
            d.text((6, y + 4), "原圖", fill="white", font=F)
            for c, s in enumerate(scenes):
                sh.paste(tile(imgs[s], None), (130 + c * col_w, y))
            continue
        d.text((6, y + 4), k[:16], fill="white", font=Fs)
        tb = load_cube(find_lut(b_dir, k))
        ta = load_cube(find_lut(a_dir, k)) if two else None
        for c, s in enumerate(scenes):
            x = 130 + c * col_w
            sh.paste(tile(imgs[s], tb), (x, y))
            if two: sh.paste(tile(imgs[s], ta), (x + T, y))
    sh.save(out, quality=86)
    print(sh.size)


if __name__ == "__main__":
    main()
