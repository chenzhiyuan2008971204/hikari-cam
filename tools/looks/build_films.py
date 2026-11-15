"""Bake the twelve film looks into engine assets.

  python3 build_films.py            writes engine/src/main/assets/luts/<id>.cube and filters/<id>.json
Each look must pass build.CLEAN_LIMITS (white/grey neutrality, black level, skin, shadow and
highlight detail) or the build stops.
"""
import json
import os
import sys

import numpy as np

import look as engine
from build import bake, clean_check, write_cube
from films import FILMS, SHIP

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "engine/src/main/assets")


HUE_ZH = dict(red="紅", orange="橙", yellow="黃", green="綠", aqua="青", blue="藍", purple="紫", magenta="洋紅",
              skin="膚色", amber="琥珀", lime="黃綠", teal="藍綠", cyan="青", sky="天藍")
SEL_ZH = dict(reds="紅", yellows="黃", greens="綠", cyans="青", blues="藍", magentas="洋紅", whites="白", neutrals="中性灰", blacks="黑")


def recipe(c, fx):
    """Readable list of what this look actually does, generated from its recipe."""
    out = []
    cp = getattr(c["curve"], "params", None)
    if cp:
        out.append(f"影調曲線：18% 灰處斜率 {cp['contrast']:.2f}（{'加' if cp['contrast'] > 1 else '減'}反差），"
                   f"黑位 {cp['toe']:.3f}、白位肩部 {cp['shoulder']:.3f}，曝光 {cp['exposure']:+.2f} EV（線性光、不裁切）")
    if c.get("mono"):
        r, g, b = c["mono"]
        out.append(f"黑白轉換：在線性光下混合通道 R {r:.2f} / G {g:.2f} / B {b:.2f}（黃濾鏡感：藍天較深、膚色較亮）")
        out.append("色彩管理：sRGB → 線性光 → 黑白混合 → 影調曲線 → sRGB；輸出 R=G=B，沒有色偏")
    if c.get("temp") or c.get("tint"):
        out.append(f"白平衡：色溫 {c.get('temp', 0):+.2f}、色調 {c.get('tint', 0):+.2f}")
    sat = [f"整體飽和 {c['sat']:+.2f}"] if c.get("sat") else []
    if c.get("vibrance"):
        sat.append(f"自然飽和 {c['vibrance']:+.2f}")
    if c.get("sat_highlights"):
        sat.append(f"高光飽和 {c['sat_highlights']:+.2f}")
    if sat:
        out.append("飽和度（OKLab）：" + "、".join(sat))
    hsl = c.get("hsl", {})
    if hsl:
        parts = [f"{HUE_ZH.get(k, k)} 色相{h:+.0f}° 飽和{s:+.2f} 亮度{l:+.2f}" for k, (h, s, l) in hsl.items()]
        out.append("分色 HSL：" + "；".join(parts))
    hd = c.get("hsl_dark", {})
    if hd:
        out.append("暗部 HSL（深綠／深藍分開處理）：" + "；".join(
            f"{HUE_ZH.get(k, k)} 色相{h:+.0f}° 飽和{s:+.2f} 亮度{l:+.2f}" for k, (h, s, l) in hd.items()))
    cc = c.get("chroma_cap", {})
    if cc:
        out.append("色度上限（不螢光）：" + "、".join(f"{HUE_ZH.get(k, k)} {v:.2f}" for k, v in cc.items()))
    if c.get("hl_rolloff"):
        k, a = c["hl_rolloff"]
        out.append(f"高光色彩滾降：亮度 {k:.2f} 起漸淡 {a:.0%}（雲與白牆乾淨不泛黃）")
    sel = c.get("selective", {})
    if sel:
        out.append("可選顏色：" + "；".join(f"{SEL_ZH.get(k, k)} C{v[0]:+.2f} M{v[1]:+.2f} Y{v[2]:+.2f} K{v[3]:+.2f}"
                                       for k, v in sel.items()))
    if c.get("rgb_curves"):
        out.append("獨立 RGB 曲線：" + "、".join(k.upper() for k in c["rgb_curves"]) + " 通道交叉")
    if c.get("bands") == "fine":
        out.append("色彩管理：sRGB → 線性光 → OKLab 工作空間 → 色域映射 → sRGB；12 色相區段")
    sp = c.get("split", {})
    if sp:
        out.append("分離色調：" + "、".join(f"{dict(shadows='暗部', mids='中間調', highlights='高光')[k]} {h:.0f}° × {a:.3f}"
                                         for k, (h, a) in sp.items()))
    out.append("保護：中性白／灰、膚色色相、色域內壓縮（不爆色）")
    g = fx["grain"]
    out.append(f"顆粒：強度 {g['amount']:.2f}、粒徑 {g['size'] * 1000:.2f}‰ 短邊、彩色 {g['chroma']:.2f}、粗糙 {g['rough']:.2f}，中間調最強")
    h, bl = fx["halation"], fx["bloom"]
    if h["amount"] > 0:
        out.append(f"Halation：{h['amount']:.2f}，門檻 {h['threshold']:.2f}，半徑 {h['radius'] * 100:.1f}% 短邊，紅橙色")
    if bl["amount"] > 0:
        out.append(f"Bloom：{bl['amount']:.2f}，門檻 {bl['threshold']:.2f}，半徑 {bl['radius'] * 100:.1f}% 短邊")
    d = fx["diffusion"]
    if d["softFocus"] > 0 or d["mist"] > 0:
        out.append(f"柔焦 {d['softFocus']:.2f}、霧化 {d['mist']:.2f}")
    return out


def spec(key, f):
    out = dict(id=key, name=f["name"], zh=f["zh"], series=f["series"], note=f["note"],
               lut=f"luts/{key}.cube", color=dict(lutAmount=1.0, exposure=0.0))
    if f.get("inspired"):
        out["inspired"] = f["inspired"]
    out.update(f["fx"])
    out["recipe"] = recipe(f["colour"], f["fx"])
    from traits import traits
    out["traits"] = traits(f)
    return out


def main():
    os.makedirs(os.path.join(ASSETS, "luts"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "filters"), exist_ok=True)
    import glob
    for old in glob.glob(os.path.join(ASSETS, "luts", "*.cube")) + glob.glob(os.path.join(ASSETS, "filters", "*.json")):
        os.remove(old)
    bad = []
    for key in SHIP:
        f = FILMS[key]
        lk = f["colour"]
        fails, m = clean_check(lk)
        status = "ok" if not fails else "FAIL " + ",".join(fails)
        if m["skinHue"] is None:  # black and white
            m = dict(m, skinHue=0.0, skinC=(0.0, 0.0))
        print(f"{key:<15} skinHue {m['skinHue']:5.1f}  skinC {m['skinC'][0]:.2f}-{m['skinC'][1]:.2f}  "
              f"black {m['blackLv']:.3f}  white {m['whiteLv']:.2f}  shadow {m['shadow']:.3f}  {status}")
        if fails:
            bad.append(key)
        write_cube(os.path.join(ASSETS, "luts", key + ".cube"), bake(lk), f["name"])
        with open(os.path.join(ASSETS, "filters", key + ".json"), "w") as fh:
            json.dump(spec(key, f), fh, ensure_ascii=False, indent=1)
    with open(os.path.join(ASSETS, "filters", "index.json"), "w") as fh:
        json.dump(SHIP, fh)
    if bad:
        sys.exit("not clean: " + ", ".join(bad))


if __name__ == "__main__":
    main()
