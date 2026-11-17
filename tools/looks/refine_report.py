"""Writes FILTER_UPGRADE_REPORT.md from refine_params.json (what was changed, and the measured before / after).

    python3 refine_report.py OUT.md
"""
import json, os, statistics as st, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
ZH = dict(shadow_open="暗部層次展開", hl_open="高光層次展開", mid_open="中間調反差補償", toe="黑位微調", mud="髒色還原", skin_hue="膚色色相",
          green_light="植物亮度", green_sat="植物飽和", sky_hue="天空色相", neon="色度上限")
SER = dict(sim="Film Simulation", daylight="Daylight", film="Film", japanese="Japanese 日系", ccd="CCD", special="Special",
           fuji="Fujifilm 底片模擬", cinema="Cinema 電影色彩", pro="Pro 專業攝影", vintage="Vintage 復古底片")


def fmt(p):
    out = []
    for k, v in p.items():
        out.append(f"{ZH[k]} ×{v:.2f}" if k == "neon" else f"{ZH[k]} {v:+.3g}")
    return "、".join(out) if out else "—"


def main():
    r = json.load(open(os.path.join(HERE, "refine_params.json")))
    idx = json.load(open(os.path.join(ROOT, "engine/src/main/assets/filters/index.json")))
    meta = {k: json.load(open(os.path.join(ROOT, f"engine/src/main/assets/filters/{k}.json"))) for k in idx}
    mono = {"fj_acros", "fj_mono"}
    changed = [k for k in idx if r[k]["variant"] != "none"]
    L = ["# FILTER_UPGRADE_REPORT — 現有濾鏡品質升級", ""]
    L += ["## 結果一覽", "",
          f"- 原有濾鏡總數：**{len(idx)}** 款（名稱、分類、順序、風格定位、顆粒／光暈／暗角等空間特效：全部逐位元不變，由測試檢查）。",
          f"- 實際升級（LUT 有改動）：**{len(changed)}** 款；其餘 {len(idx) - len(changed)} 款經逐一量測與嘗試後，沒有任何修正能在不犧牲其他項目下改善，所以**維持原狀**，沒有為了「看起來有改」而硬調。",
          "- 做法：先在 14 張標準照＋3 張室內照上量測每款的缺點，再為每款**各自**擬合一組修正量（黑位壓死、髒色、膚色色相、植物亮度／飽和、天空色相、色度外溢），",
          "  再把每款的修正做成 100%／60%／30% 三種強度與「只改顏色」「顏色＋黑位」「全部」共 10 個版本，烘成實際上 GPU 用的 LUT，用真實照片量測，挑分數最好的一個（分數包含：死黑、爆白、暗部／高光局部反差、髒色、色度外溢、膚色色相、LUT 陡峭度、階調單調）；沒有一個比原版好至少 0.4 分的就維持原版（差距小於這個門檻的改動肉眼分不出來，不算升級）。", ""]
    mm = lambda m, w: st.mean((abs(r[k][w][m]) if m == 'skinH' else r[k][w][m]) for k in idx if k not in mono)
    L += ["## 量測結果（62 款平均，黑白兩款不計膚色／髒色）", "", "| 項目 | 升級前 | 升級後 | 說明 |", "|---|---:|---:|---|"]
    for m, name, note in [("clipLo", "死黑像素 %", "原本明明有層次卻被壓成純黑的像素比例"), ("clipHi", "爆白像素 %", "原本不亮卻被推成純白"),
                          ("shKeep", "暗部局部反差保留", "暗部細節（1 = 與原圖一樣）"), ("hlKeep", "高光局部反差保留", "高光細節"),
                          ("mud", "髒色像素 %", "中度飽和的顏色同時變暗又變灰"), ("neon", "色度外溢 %", "色度超過自然上限"),
                          ("roughMid", "LUT 陡峭度（亮部以上）", "越高越容易出現色階斷層"), ("skinH", "膚色色相偏移 °", "三個參考膚色的平均偏移")]:
        L.append(f"| {name} | {mm(m, 'before'):.3f} | {mm(m, 'after'):.3f} | {note} |")
    L += ["", "## 每款濾鏡", "", "| 濾鏡 | 分類 | 結果 | 修改內容 | 死黑 % | 髒色 % | 暗部保留 | 膚色° |", "|---|---|---|---|---:|---:|---:|---:|"]
    for k in idx:
        v = r[k]; b, a = v["before"], v["after"]
        res = "維持原狀" if v["variant"] == "none" else "已升級（" + v["variant"].replace("colour", "顏色").replace("full", "全部").replace("toe", "黑位").replace("x", "強度 ×") + "）"
        L.append(f"| {meta[k]['name']} | {SER.get(meta[k]['series'], meta[k]['series'])} | {res} | {fmt(v['params'])} | {b['clipLo']:.2f}→{a['clipLo']:.2f} | {b['mud']:.1f}→{a['mud']:.1f} | {b['shKeep']:.2f}→{a['shKeep']:.2f} | "
                 f"{'—' if k in mono else f'{abs(b['skinH']):.1f}→{abs(a['skinH']):.1f}'} |")
    open(sys.argv[1], "w").write("\n".join(L) + "\n")
    print(len(changed), "changed of", len(idx))


if __name__ == "__main__":
    main()
