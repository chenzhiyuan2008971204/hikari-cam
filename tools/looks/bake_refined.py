"""Bake LUTs of refined looks into a scratch folder for before/after comparison (no repo files touched).

    python3 bake_refined.py PARAMS.json OUT_LUT_DIR [--ids a,b]
"""
import json, os, sys
from build import bake, write_cube
from films import FILMS, BASE
from refine import refine_look

params = json.load(open(sys.argv[1])); out = sys.argv[2]
ids = sys.argv[sys.argv.index("--ids") + 1].split(",") if "--ids" in sys.argv else list(params)
os.makedirs(out, exist_ok=True)
for k in ids:
    write_cube(os.path.join(out, k + ".cube"), bake(refine_look(BASE[k], params[k]["params"])), FILMS[k]["name"])
print(len(ids), "baked ->", out)
