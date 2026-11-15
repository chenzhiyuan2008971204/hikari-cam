"""All shipped looks side by side on one photo (colour only), for judging the whole set."""
import glob, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from films import FILMS, SHIP
from build import bake, apply_lut

font = ImageFont.truetype(glob.glob("/usr/share/fonts/**/DejaVuSans.ttf", recursive=True)[0], 20)
out = sys.argv[1] if len(sys.argv) > 1 else "preview/all20"
os.makedirs(out, exist_ok=True)
tables = {k: bake(FILMS[k]["colour"], 25) for k in SHIP}
for f in sys.argv[2:] or ["03_japan_street", "x3_portrait_red", "02_green_trees", "x2_beach_skin", "01_sky_clouds", "06_portrait"]:
    im = Image.open(f"photos/daylight/{f}.png").convert("RGB"); im.thumbnail((330, 330))
    w, h = im.size; a = np.asarray(im, float) / 255
    tiles = [("Original", im)] + [(FILMS[k]["name"], Image.fromarray((np.clip(apply_lut(tables[k], a), 0, 1) * 255 + .5).astype(np.uint8))) for k in SHIP]
    cols = 7; rows = (len(tiles) + cols - 1) // cols; pad = 28
    S = Image.new("RGB", (cols * w, rows * (h + pad)), (18, 18, 18)); d = ImageDraw.Draw(S)
    for i, (n, t) in enumerate(tiles):
        x, y = (i % cols) * w, (i // cols) * (h + pad); S.paste(t, (x, y + pad)); d.text((x + 6, y + 4), n, fill=(235, 235, 235), font=font)
    S.save(f"{out}/{f}.jpg", quality=88)
