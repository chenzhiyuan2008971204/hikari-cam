"""The big look library: film-simulation set, cinema stocks, Japanese styles, professional looks, vintage film.

Every look is its own recipe (tone curve, white balance, per-hue HSL, colour grading, RGB crossover,
chroma limits, highlight roll-off, grain / halation / bloom / vignette). The names say which classic
film or style the look takes after; the recipes are original work tuned by eye and by the checks in
build.py - no manufacturer LUT, profile or measurement data is used, and none of them is an official
simulation.
"""
from filmkit import filmic, fx

CAP = dict(sky=0.15, blue=0.17, green=0.13, lime=0.13, skin=0.125)


def rgb(r=0.0, g=0.0, b=0.0, rs=0.0, gs=0.0, bs=0.0):
    """R/G/B crossover: push the upper (r, g, b) and lower (rs, gs, bs) quarter-tones of each channel."""
    c = lambda hi, lo: [(0, 0), (0.25, 0.25 + lo), (0.5, 0.5), (0.75, 0.75 + hi), (1, 1)]
    return dict(r=c(r, rs), g=c(g, gs), b=c(b, bs))


def mk(name, zh, series, insp, note, c=1.0, ex=0.0, toe=0.01, sh=0.99, sat=0.0, vib=0.0, temp=0.0, tint=0.0, hsl=None, split=None,
       curves=None, cap=None, roll=(0.84, 0.45), mono=None, clean=0.93, allow=None, cc=0.3, satHL=0.0, **f):
    col = dict(bands="fine", curve=filmic(exposure=ex, contrast=c, toe=toe, shoulder=sh), curve_chroma=cc, clean=clean, hl_rolloff=roll)
    if mono: col = dict(mono=mono, curve=filmic(exposure=ex, contrast=c, toe=toe, shoulder=sh), clean=1.0)
    else:
        col["chroma_cap"] = dict(CAP, **(cap or {}))
        if sat: col["sat"] = sat
        if vib: col["vibrance"] = vib
        if satHL: col["sat_highlights"] = satHL
        if temp: col["temp"] = temp
        if tint: col["tint"] = tint
        if hsl: col["hsl"] = hsl
        if curves: col["rgb_curves"] = curves
    if split: col["split"] = split
    if allow: col["allow"] = allow
    return dict(name=name, zh=zh, series=series, inspired=insp, note=note, colour=col, fx=fx(**f))


F, C, J, P, V = "fuji", "cinema", "japanese", "pro", "vintage"
PRO = {
    # ------------------------------------------------------------- Fujifilm-style film simulations
    "fj_provia": mk("Provia", "Provia 標準", F, "Provia", "標準正片：中性白平衡、適中反差、乾淨準確的顏色，什麼題材都適用。", c=1.18, ex=0.02, sat=0.02, temp=0.02, tint=0.015,
        hsl=dict(sky=(1, 0.06, -0.03), blue=(2, 0.06, -0.04), green=(2, 0.03, -0.01), skin=(0, 0.02, 0.01), red=(0, 0.04, 0.0)), split=dict(shadows=(225, 0.003)),
        grain=0.14, grain_size=0.001, grain_chroma=0.08, vignette=0.08),
    "fj_velvia": mk("Velvia", "Velvia 鮮豔", F, "Velvia", "鮮豔正片：濃郁的藍天與綠葉、深沉的暗部與強烈反差，風景專用。", c=1.26, ex=-0.03, toe=0.006, sat=0.14, vib=0.08,
        hsl=dict(sky=(-4, 0.14, -0.04), blue=(-3, 0.12, -0.05), green=(-3, 0.14, -0.03), lime=(-2, 0.1, -0.01), red=(2, 0.1, -0.02), yellow=(0, 0.08, 0.0), skin=(1, -0.04, 0.0), magenta=(0, 0.08, 0.0)),
        cap=dict(sky=0.185, blue=0.2, green=0.165, lime=0.16), curves=rgb(b=-0.006, bs=0.006), roll=(0.8, 0.55), allow=dict(shadow_detail=0.05),
        grain=0.12, grain_size=0.00092, grain_chroma=0.1, vignette=0.14),
    "fj_astia": mk("Astia", "Astia 柔和", F, "Astia", "柔和正片：反差柔順、膚色通透，顏色依然飽滿，適合人像與日常。", c=0.9, ex=0.08, toe=0.016, sat=0.1, vib=0.06,
        hsl=dict(skin=(1, 0.03, 0.03), red=(-2, 0.08, 0.01), sky=(-5, 0.12, 0.0), magenta=(0, 0.08, 0.01), green=(0, 0.0, 0.01), yellow=(-1, -0.02, 0.01)), split=dict(highlights=(40, 0.003)), roll=(0.86, 0.4),
        grain=0.13, grain_size=0.001, grain_chroma=0.06, vignette=0.06),
    "fj_classic_chrome": mk("Classic Chrome", "Classic Chrome 經典", F, "Classic Chrome", "紀實風格：壓低的飽和、偏青的深藍天、厚實的暗部，冷靜而有分量。", c=1.18, ex=-0.02, sat=-0.2,
        hsl=dict(sky=(-9, -0.1, -0.05), blue=(-8, -0.1, -0.05), cyan=(-6, -0.1, -0.02), green=(-6, -0.18, -0.03), lime=(-5, -0.2, -0.01), yellow=(-2, -0.14, 0.0), red=(3, -0.06, -0.02), skin=(0, 0.1, 0.0), magenta=(0, -0.2, 0.0), purple=(0, -0.2, 0.0)),
        split=dict(shadows=(200, 0.006), highlights=(70, 0.004)), roll=(0.8, 0.55), grain=0.2, grain_size=0.00112, grain_chroma=0.06, vignette=0.14),
    "fj_classic_neg": mk("Classic Negative", "Classic Negative 經典負片", F, "Classic Negative", "街拍負片：硬朗的反差、暗部偏青綠、高光偏暖，紅色濃、綠色沉。", c=1.24, ex=0.0, toe=0.012, sat=-0.12,
        hsl=dict(red=(-3, 0.12, -0.03), skin=(-1, 0.06, 0.0), amber=(-2, -0.04, 0.0), yellow=(-4, -0.2, 0.0), lime=(-6, -0.24, -0.02), green=(-10, -0.22, -0.04), teal=(-6, -0.08, -0.02), sky=(-6, -0.12, -0.02), blue=(-6, -0.1, -0.04)),
        split=dict(shadows=(170, 0.011), highlights=(55, 0.008)), curves=rgb(r=0.012, b=-0.014, rs=-0.01, gs=0.006, bs=0.006), roll=(0.78, 0.6),
        grain=0.26, grain_size=0.0012, grain_chroma=0.1, halation=0.05, vignette=0.16),
    "fj_nostalgic_neg": mk("Nostalgic Neg.", "Nostalgic Neg. 懷舊負片", F, "Nostalgic Neg", "彩色新攝影的味道：琥珀色的高光、飽滿的暗部顏色、柔和的階調。", c=0.98, ex=0.05, toe=0.018, sat=-0.04, temp=0.1,
        hsl=dict(amber=(0, 0.1, 0.01), skin=(1, 0.04, 0.01), red=(2, 0.06, -0.01), yellow=(-3, 0.04, 0.0), green=(-6, -0.1, -0.01), sky=(-5, -0.14, 0.0), blue=(-4, -0.08, -0.02)),
        split=dict(highlights=(48, 0.014), shadows=(250, 0.004)), curves=rgb(r=0.01, b=-0.018), roll=(0.82, 0.3), allow=dict(white_chroma=3.5, lightgray_chroma=4.5),
        grain=0.22, grain_size=0.00116, grain_chroma=0.12, halation=0.06, vignette=0.12),
    "fj_eterna": mk("Eterna", "Eterna 電影", F, "Eterna", "電影負片：很低的反差與飽和、寬廣的暗部層次，留給後製的柔和影調。", c=0.82, ex=0.04, toe=0.03, sh=0.97, sat=-0.26,
        hsl=dict(skin=(0, 0.1, 0.0), sky=(-4, -0.04, -0.01), green=(4, -0.08, 0.0), red=(0, -0.04, 0.0)), split=dict(shadows=(195, 0.006), highlights=(60, 0.003)), roll=(0.8, 0.4),
        allow=dict(black_level=0.06, white_level=0.93), grain=0.2, grain_size=0.00108, grain_chroma=0.05, halation=0.04, vignette=0.1),
    "fj_bleach": mk("Eterna Bleach Bypass", "Eterna Bleach Bypass 銀殘留", F, "Eterna Bleach Bypass", "銀殘留：高反差、極低飽和、銀灰色的高光，冷硬的戲劇感。", c=1.5, ex=-0.05, toe=0.006, sh=0.965, sat=-0.4, temp=-0.08, tint=-0.04, cc=0.2,
        hsl=dict(skin=(0, 0.36, 0.0), amber=(0, 0.16, 0.0), red=(0, 0.12, -0.02), sky=(0, -0.04, -0.04), green=(6, -0.1, -0.02)), cap=dict(sky=0.085, blue=0.095, green=0.065, lime=0.065),
        split=dict(shadows=(188, 0.016), highlights=(75, 0.005)), roll=(0.76, 0.62), clean=0.92, allow=dict(skin_chroma=(0.6, 1.7)),
        grain=0.38, grain_size=0.0014, grain_chroma=0.03, grain_rough=0.5, halation=0.05, vignette=0.2),
    "fj_acros": mk("Acros", "Acros 細緻黑白", F, "Acros", "細緻黑白（紅濾鏡感）：天空壓得很深、膚色明亮、黑很深，銳利的細顆粒。", c=1.32, ex=0.0, toe=0.004, mono=(0.5, 0.42, 0.08),
        grain=0.3, grain_size=0.00092, grain_chroma=0.0, grain_rough=0.35, vignette=0.12),
    "fj_mono": mk("Monochrome", "Monochrome 標準黑白", F, "Monochrome", "標準黑白：中性的灰階轉換、溫和的反差，平實耐看。", c=1.06, ex=0.03, toe=0.014, mono=(0.2126, 0.7152, 0.0722),
        grain=0.16, grain_size=0.00104, grain_chroma=0.0, vignette=0.06),
    "fj_proneg_hi": mk("PRO Neg. Hi", "PRO Neg. Hi 人像硬調", F, "Pro Neg Hi", "棚拍人像：俐落反差、收斂色彩、立體而準確的膚色。", c=1.32, ex=0.04, sat=-0.1, temp=-0.02,
        hsl=dict(skin=(1, 0.06, 0.04), red=(2, -0.08, 0.0), yellow=(-2, -0.14, 0.0), lime=(-2, -0.18, 0.0), green=(0, -0.18, -0.01), sky=(0, 0.0, 0.01), purple=(0, -0.12, 0.0), magenta=(0, -0.1, 0.0)),
        cap=dict(skin=0.115, sky=0.13, blue=0.15, green=0.1, lime=0.1), split=dict(shadows=(215, 0.009), highlights=(45, 0.004)), grain=0.15, grain_size=0.001, grain_chroma=0.05, vignette=0.1),
    "fj_proneg_std": mk("PRO Neg. Std.", "PRO Neg. Std. 人像柔調", F, "Pro Neg Std", "柔光人像：很柔的反差、淡雅的顏色、平滑的膚色過渡。", c=0.84, ex=0.08, toe=0.024, sat=-0.1, temp=0.05,
        hsl=dict(skin=(0, 0.06, 0.03), red=(1, -0.06, 0.0), green=(0, -0.14, 0.0), lime=(0, -0.14, 0.0), sky=(0, -0.06, 0.02)), cap=dict(skin=0.11, sky=0.12, green=0.095, lime=0.095),
        split=dict(highlights=(40, 0.008), shadows=(30, 0.004)), roll=(0.88, 0.35), allow=dict(black_level=0.055), grain=0.12, grain_size=0.00096, grain_chroma=0.04, vignette=0.05),
    # ------------------------------------------------------------- cinema and film stocks
    "ci_2383": mk("Kodak 2383", "2383 電影拷貝片", C, "2383 print film", "電影院拷貝片：濃密的黑、暖黃的高光、偏青的暗部與厚重的膚色。", c=1.3, ex=-0.04, toe=0.012, sh=0.975, sat=-0.02, temp=0.05,
        hsl=dict(skin=(-1, 0.08, -0.01), red=(-2, 0.06, -0.03), sky=(-12, -0.02, -0.05), blue=(-10, -0.04, -0.06), green=(-8, -0.12, -0.04), yellow=(-3, 0.04, 0.0)),
        split=dict(shadows=(195, 0.014), highlights=(58, 0.012)), curves=rgb(r=0.012, g=0.004, b=-0.02, rs=-0.012, bs=0.012), roll=(0.76, 0.55), allow=dict(white_chroma=3.5, lightgray_chroma=4.5, shadow_detail=0.05),
        grain=0.24, grain_size=0.00116, grain_chroma=0.1, halation=0.1, hal_threshold=0.84, vignette=0.18),
    "ci_portra400": mk("Kodak Portra 400", "Portra 400 人像負片", C, "Portra 400", "人像負片：溫潤的膚色、柔和的高光、淡淡的暖調與很寬的階調。", c=0.95, ex=0.1, toe=0.018, sat=-0.05, temp=0.06,
        hsl=dict(skin=(1, 0.04, 0.03), amber=(0, 0.04, 0.02), red=(2, -0.04, 0.0), green=(-8, -0.14, 0.02), lime=(-6, -0.1, 0.02), sky=(-6, -0.1, 0.03), blue=(-4, -0.08, 0.0)),
        split=dict(highlights=(42, 0.008), shadows=(160, 0.004)), curves=rgb(r=0.006, b=-0.008), roll=(0.86, 0.3), allow=dict(white_chroma=3.0, lightgray_chroma=4.0),
        grain=0.22, grain_size=0.00112, grain_chroma=0.12, halation=0.05, vignette=0.08),
    "ci_gold200": mk("Kodak Gold 200", "Gold 200 金色負片", C, "Gold 200", "陽光負片：金黃的暖調、飽滿的紅與黃、帶點綠的陰影，假日快照的顏色。", c=1.1, ex=0.05, toe=0.016, sat=0.06, temp=0.12,
        hsl=dict(yellow=(-4, 0.12, 0.01), amber=(-2, 0.1, 0.0), red=(2, 0.08, -0.01), skin=(0, 0.04, 0.0), green=(-4, -0.04, -0.02), sky=(-3, -0.06, -0.01)),
        split=dict(highlights=(52, 0.014), shadows=(140, 0.006)), curves=rgb(r=0.012, b=-0.022, gs=0.006), allow=dict(white_chroma=4.0, lightgray_chroma=5.0),
        grain=0.28, grain_size=0.00128, grain_chroma=0.16, halation=0.08, vignette=0.14),
    "ci_ektar100": mk("Kodak Ektar 100", "Ektar 100 高彩負片", C, "Ektar 100", "高彩細粒負片：乾淨鮮明的顏色、偏青的藍天、扎實的反差，適合風景與旅行。", c=1.2, ex=0.02, sat=0.16, vib=0.06, temp=0.03,
        hsl=dict(sky=(-8, 0.12, -0.03), blue=(-6, 0.1, -0.04), cyan=(-6, 0.12, 0.0), red=(-3, 0.16, -0.02), green=(2, 0.08, -0.02), yellow=(0, 0.06, 0.0), skin=(2, -0.06, 0.0)),
        cap=dict(sky=0.18, blue=0.195, green=0.155, lime=0.15), split=dict(shadows=(215, 0.004)), roll=(0.82, 0.5), grain=0.1, grain_size=0.00088, grain_chroma=0.08, vignette=0.1),
    "ci_800t": mk("CineStill 800T", "800T 鎢絲燈電影片", C, "800T tungsten film", "夜晚燈光片：偏冷的藍青底色、燈光周圍的紅色光暈、霓虹更艷。", c=1.12, ex=0.06, toe=0.02, sat=0.02, temp=-0.14, tint=0.02,
        hsl=dict(red=(-2, 0.14, 0.0), amber=(-4, 0.08, 0.0), skin=(3, 0.14, 0.01), sky=(-6, 0.04, -0.02), teal=(-4, 0.1, 0.0), green=(-10, -0.06, -0.02)),
        split=dict(shadows=(205, 0.012), highlights=(20, 0.004)), curves=rgb(b=0.012, bs=0.012, rs=-0.008), roll=(0.8, 0.3), allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.06),
        grain=0.36, grain_size=0.0014, grain_chroma=0.18, halation=0.42, hal_threshold=0.74, hal_radius=0.02, hal_tint=(1.0, 0.2, 0.1), bloom=0.12, vignette=0.16),
    "ci_vista": mk("Agfa Vista", "Vista 鮮豔負片", C, "Vista 200", "歐系負片：偏洋紅的暖紅、飽和的原色、暗部帶一點紫，鮮明活潑。", c=1.14, ex=0.03, sat=0.08, tint=0.03,
        hsl=dict(red=(-5, 0.14, -0.01), magenta=(-4, 0.1, 0.0), skin=(-2, 0.02, 0.0), yellow=(2, 0.06, 0.0), green=(4, 0.04, -0.02), sky=(2, 0.06, -0.01), blue=(4, 0.06, -0.02)),
        split=dict(shadows=(285, 0.007), highlights=(40, 0.005)), curves=rgb(r=0.008, g=-0.006), grain=0.26, grain_size=0.00124, grain_chroma=0.16, halation=0.05, vignette=0.12),
    "ci_fuji400": mk("Fuji 400", "Fuji 400 綠調負片", C, "400H / Superia", "日系負片：帶青綠的陰影、清涼的綠與藍、乾淨偏冷的白。", c=1.04, ex=0.08, toe=0.016, sat=-0.03, temp=-0.04, tint=-0.03,
        hsl=dict(green=(8, 0.04, 0.02), lime=(6, 0.0, 0.02), teal=(4, 0.08, 0.0), sky=(-4, 0.02, 0.02), skin=(2, -0.02, 0.02), red=(2, -0.04, 0.0), yellow=(4, -0.08, 0.01)),
        split=dict(shadows=(165, 0.012), highlights=(190, 0.003)), curves=rgb(g=0.006, gs=0.008, rs=-0.006), roll=(0.86, 0.4), allow=dict(white_chroma=3.0, lightgray_chroma=4.0),
        grain=0.24, grain_size=0.00116, grain_chroma=0.12, halation=0.04, vignette=0.08),
    "ci_teal": mk("Cinematic Teal", "電影青橙", C, "teal & orange grade", "青橙調色：暗部與藍綠往青色集中、膚色與暖光往橙色集中，互補對比。", c=1.2, ex=0.0, sat=-0.04,
        hsl=dict(sky=(-16, 0.04, -0.03), blue=(-22, 0.0, -0.04), cyan=(-6, 0.08, -0.01), green=(-22, -0.16, -0.03), lime=(-14, -0.18, -0.01), skin=(-2, 0.1, 0.0), amber=(-3, 0.12, 0.0), red=(4, 0.02, -0.02), purple=(-20, -0.1, 0.0)),
        split=dict(shadows=(192, 0.018), highlights=(45, 0.01)), curves=rgb(r=0.01, b=-0.012, rs=-0.014, bs=0.012), roll=(0.8, 0.5), allow=dict(white_chroma=3.0, lightgray_chroma=4.0),
        grain=0.18, grain_size=0.00108, grain_chroma=0.06, halation=0.06, vignette=0.2),
    "ci_warm": mk("Warm Cinema", "暖調電影", C, "warm grade", "暖調電影：琥珀色的整體光線、柔和的黑、略降的飽和，像黃昏的室內戲。", c=1.08, ex=0.02, toe=0.022, sh=0.98, sat=-0.1, temp=0.16,
        hsl=dict(skin=(0, 0.04, 0.0), sky=(-6, -0.2, 0.0), blue=(-4, -0.2, -0.02), green=(-10, -0.16, -0.02), red=(2, 0.04, -0.01)), split=dict(shadows=(30, 0.008), highlights=(50, 0.012)),
        curves=rgb(r=0.012, b=-0.02, bs=-0.008), roll=(0.8, 0.3), allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.06), grain=0.22, grain_size=0.0012, grain_chroma=0.08, halation=0.1, bloom=0.06, vignette=0.2),
    "ci_cold": mk("Cold Cinema", "冷調電影", C, "cold grade", "冷調電影：鋼藍色的陰影、去飽和的暖色、偏硬的反差，懸疑片的氣氛。", c=1.22, ex=-0.03, sat=-0.2, temp=-0.12,
        hsl=dict(skin=(2, 0.2, 0.0), amber=(2, 0.04, 0.0), red=(4, -0.06, -0.02), yellow=(6, -0.2, 0.0), green=(10, -0.14, -0.03), sky=(4, 0.0, -0.03), blue=(4, 0.04, -0.04)),
        split=dict(shadows=(222, 0.016), highlights=(205, 0.005)), curves=rgb(b=0.01, bs=0.014, rs=-0.012), roll=(0.78, 0.5), allow=dict(white_chroma=3.5, lightgray_chroma=4.5),
        grain=0.22, grain_size=0.00116, grain_chroma=0.05, vignette=0.22),
    "ci_vintage": mk("Vintage Cinema", "復古電影", C, "aged print", "老拷貝：褪色的黑、偏黃綠的高光、洋紅的陰影與明顯的顆粒，七零年代電影的質地。", c=1.02, ex=0.03, toe=0.045, sh=0.955, sat=-0.14, temp=0.08, tint=-0.02,
        hsl=dict(red=(4, -0.04, 0.0), skin=(2, 0.06, 0.0), yellow=(4, 0.04, 0.0), green=(-6, -0.14, 0.0), sky=(-10, -0.2, 0.0), blue=(-8, -0.2, 0.0)),
        split=dict(shadows=(320, 0.012), highlights=(72, 0.016)), curves=rgb(r=0.01, g=0.008, b=-0.026, bs=0.016), roll=(0.78, 0.3),
        allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.075, white_level=0.92), grain=0.44, grain_size=0.0015, grain_chroma=0.2, grain_rough=0.55, halation=0.12, vignette=0.26, dust=0.12, scratches=0.06),
    # ------------------------------------------------------------- Japanese photography styles
    "jp_blue": mk("Japanese Blue", "日系藍", J, "", "日系藍調：白平衡偏冷、天空與陰影是透明的水藍，整體明亮清爽。", c=0.94, ex=0.14, toe=0.02, sat=-0.06, temp=-0.12,
        hsl=dict(sky=(-4, 0.06, 0.04), blue=(-6, 0.02, 0.03), cyan=(0, 0.08, 0.03), green=(8, -0.1, 0.03), skin=(3, 0.14, 0.03), yellow=(4, -0.14, 0.02)),
        split=dict(shadows=(212, 0.012), highlights=(205, 0.004)), roll=(0.88, 0.35), allow=dict(white_chroma=3.5, lightgray_chroma=4.5, black_level=0.055), grain=0.14, grain_size=0.001, grain_chroma=0.06, vignette=0.04),
    "jp_green": mk("Japanese Green", "日系綠", J, "", "日系綠調：青翠偏冷的綠、帶綠的陰影、柔和的反差，森林與夏日的空氣感。", c=0.96, ex=0.1, toe=0.02, sat=-0.04, tint=-0.05,
        hsl=dict(green=(10, 0.06, 0.03), lime=(12, 0.02, 0.03), yellow=(10, -0.08, 0.02), teal=(4, 0.08, 0.02), skin=(3, 0.06, 0.03), sky=(-2, -0.04, 0.03), red=(2, -0.06, 0.0)),
        split=dict(shadows=(150, 0.012), highlights=(120, 0.003)), curves=rgb(g=0.008, gs=0.008), roll=(0.87, 0.35), allow=dict(white_chroma=3.0, lightgray_chroma=4.0, black_level=0.055),
        grain=0.16, grain_size=0.00104, grain_chroma=0.08, vignette=0.05),
    "jp_pastel": mk("Soft Pastel", "柔和粉彩", J, "", "粉彩：很低的反差、提亮的暗部、顏色變淡變粉，像水彩一樣輕。", c=0.78, ex=0.16, toe=0.045, sh=0.985, sat=-0.2, vib=0.06, temp=-0.05, tint=0.05,
        hsl=dict(red=(-4, -0.04, 0.04), magenta=(0, 0.04, 0.04), skin=(0, 0.1, 0.04), sky=(0, 0.0, 0.05), green=(6, -0.12, 0.05), yellow=(0, -0.08, 0.04)),
        split=dict(shadows=(300, 0.006), highlights=(30, 0.005)), roll=(0.9, 0.3), allow=dict(black_level=0.075, shadow_detail=0.03), grain=0.1, grain_size=0.00096, grain_chroma=0.05, bloom=0.08, soft_focus=0.06, vignette=0.0),
    "jp_airy": mk("Airy White", "空氣感白", J, "", "空氣感：高調明亮、白色乾淨通透、陰影很淺，逆光與室內窗光最合適。", c=0.9, ex=0.3, toe=0.024, sat=-0.06, temp=-0.05, tint=-0.02,
        hsl=dict(skin=(1, 0.06, 0.04), sky=(0, -0.06, 0.05), green=(4, -0.12, 0.05), yellow=(0, -0.1, 0.03), red=(0, -0.04, 0.02)), split=dict(shadows=(215, 0.005)),
        roll=(0.9, 0.5), allow=dict(black_level=0.065), grain=0.08, grain_size=0.00092, grain_chroma=0.04, bloom=0.12, bloom_threshold=0.72, mist=0.05, vignette=0.0),
    "jp_faded_summer": mk("Faded Summer", "褪色夏天", J, "", "褪色的夏天：被曬淡的顏色、偏黃綠的暖光、霧霧的黑，回憶裡的暑假。", c=0.9, ex=0.1, toe=0.05, sh=0.97, sat=-0.16, temp=0.08, tint=-0.03,
        hsl=dict(green=(-6, -0.1, 0.03), lime=(-4, -0.06, 0.03), sky=(-8, -0.1, 0.03), yellow=(-2, 0.04, 0.02), skin=(1, 0.06, 0.02)), split=dict(shadows=(95, 0.008), highlights=(60, 0.012)),
        curves=rgb(b=-0.016, bs=0.01), roll=(0.84, 0.3), allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.075, white_level=0.93), grain=0.26, grain_size=0.0012, grain_chroma=0.14, halation=0.06, bloom=0.05, vignette=0.08),
    "jp_tokyo_night": mk("Tokyo Night", "東京夜", J, "", "東京的夜：深藍青的暗部、霓虹的洋紅與青更鮮明、燈光帶柔和光暈。", c=1.2, ex=0.0, toe=0.016, sat=0.06, temp=-0.03, tint=0.08,
        hsl=dict(magenta=(-4, 0.16, 0.0), red=(-6, 0.1, 0.0), purple=(0, 0.12, 0.0), cyan=(0, 0.14, 0.0), teal=(-4, 0.12, 0.0), sky=(-6, 0.06, -0.03), skin=(2, 0.1, 0.0), green=(-14, -0.1, -0.02)),
        cap=dict(sky=0.17, blue=0.19), split=dict(shadows=(268, 0.018), highlights=(330, 0.008)), curves=rgb(bs=0.014, rs=-0.006), roll=(0.78, 0.3), allow=dict(white_chroma=3.5, lightgray_chroma=4.5, black_level=0.055),
        grain=0.3, grain_size=0.0013, grain_chroma=0.14, halation=0.3, hal_threshold=0.76, hal_radius=0.018, bloom=0.14, vignette=0.2),
    "jp_kyoto_film": mk("Kyoto Film", "京都底片", J, "", "京都的底片：沉穩的暖褐、苔綠、朱紅，中低反差，古寺與巷弄的顏色。", c=1.0, ex=0.03, toe=0.022, sat=-0.1, temp=0.07,
        hsl=dict(red=(6, 0.1, -0.02), amber=(2, 0.04, -0.01), green=(-12, -0.12, -0.03), lime=(-10, -0.14, -0.02), yellow=(-4, -0.06, 0.0), sky=(-6, -0.16, 0.0), skin=(0, 0.02, 0.0)),
        split=dict(shadows=(110, 0.007), highlights=(45, 0.009)), curves=rgb(r=0.008, b=-0.012), roll=(0.82, 0.35), allow=dict(white_chroma=3.5, lightgray_chroma=4.5, black_level=0.055),
        grain=0.26, grain_size=0.0012, grain_chroma=0.12, halation=0.05, vignette=0.14),
    "jp_clean_day": mk("Clean Daylight", "乾淨日光", J, "", "乾淨日光：中性偏亮、顏色清澈不濃、白就是白，日常記錄的基準色。", c=1.0, ex=0.18, sat=-0.02, vib=0.04, temp=-0.05,
        hsl=dict(sky=(-2, 0.04, 0.02), green=(4, -0.04, 0.02), skin=(1, 0.02, 0.02), yellow=(0, -0.06, 0.01)), roll=(0.88, 0.5), grain=0.08, grain_size=0.00092, grain_chroma=0.04, vignette=0.02),
    "jp_milky": mk("Milky Color", "牛奶色", J, "", "牛奶色：像加了牛奶的柔白，黑被提成灰、顏色偏奶油暖，甜而不膩。", c=0.86, ex=0.08, toe=0.04, sh=0.965, sat=-0.2, temp=0.11,
        hsl=dict(skin=(0, 0.08, 0.03), red=(0, -0.02, 0.02), sky=(0, -0.06, 0.02), green=(4, -0.12, 0.03), yellow=(-2, 0.0, 0.02)), split=dict(shadows=(40, 0.008), highlights=(48, 0.006)),
        roll=(0.78, 0.3), allow=dict(white_chroma=3.5, lightgray_chroma=4.5, black_level=0.075, shadow_detail=0.03), grain=0.12, grain_size=0.001, grain_chroma=0.06, bloom=0.1, mist=0.08, vignette=0.0),
    "jp_nostalgia": mk("Soft Nostalgia", "柔和懷舊", J, "", "柔和懷舊：淡淡的洋紅暖調、柔焦的高光、低反差，舊相簿的溫度。", c=0.9, ex=0.08, toe=0.036, sh=0.975, sat=-0.1, temp=0.06, tint=0.04,
        hsl=dict(red=(-2, 0.04, 0.02), skin=(-1, 0.04, 0.02), green=(-8, -0.16, 0.02), sky=(-4, -0.14, 0.03), yellow=(-4, 0.0, 0.02)), split=dict(shadows=(335, 0.008), highlights=(38, 0.01)),
        curves=rgb(r=0.008, g=-0.006, b=-0.008), roll=(0.84, 0.3), allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.07), grain=0.24, grain_size=0.00116, grain_chroma=0.12, halation=0.08, bloom=0.08, soft_focus=0.05, vignette=0.1),
    # ------------------------------------------------------------- professional looks
    "pr_natural": mk("Clean Natural", "乾淨自然", P, "", "乾淨自然：輕微的階調整理與一點暖意，顏色忠實、膚色健康，適合當作交件的基準。", c=1.12, ex=0.07, toe=0.014, sat=-0.08, vib=0.18, temp=0.13, hsl=dict(skin=(0, 0.0, 0.03), sky=(2, 0.04, 0.02), green=(3, -0.04, 0.02)), roll=(0.9, 0.5),
        grain=0.0, vignette=0.0),
    "pr_rich": mk("Rich Color", "濃郁色彩", P, "", "濃郁色彩：加深顏色的密度而不是亮度，原色飽滿、暗部厚實。", c=1.14, ex=-0.02, sat=0.12, vib=0.1, temp=0.05,
        hsl=dict(red=(-2, 0.12, -0.04), sky=(-2, 0.1, -0.04), blue=(0, 0.08, -0.04), green=(0, 0.08, -0.03), yellow=(0, 0.06, -0.01), skin=(0, -0.02, 0.0), magenta=(0, 0.08, -0.02)),
        cap=dict(sky=0.175, blue=0.19, green=0.15, lime=0.145), roll=(0.82, 0.5), grain=0.06, grain_size=0.0009, vignette=0.08),
    "pr_deep": mk("Deep Contrast", "深邃反差", P, "", "深邃反差：很強的反差與深黑，顏色不跟著變艷，畫面有重量。", c=1.42, ex=-0.02, toe=0.004, sat=-0.06, cc=0.1,
        hsl=dict(skin=(0, 0.06, 0.0)), split=dict(shadows=(228, 0.005)), roll=(0.78, 0.6), allow=dict(shadow_detail=0.045), grain=0.12, grain_size=0.001, grain_chroma=0.04, vignette=0.16),
    "pr_portrait": mk("Soft Portrait", "柔膚人像", P, "", "柔膚人像：柔和的反差、明亮平滑的膚色、背景的綠與藍稍微收斂。", c=0.9, ex=0.1, toe=0.018, sat=-0.04,
        hsl=dict(skin=(1, 0.0, 0.05), amber=(0, -0.04, 0.03), red=(0, -0.06, 0.02), green=(0, -0.16, -0.01), lime=(0, -0.14, 0.0), sky=(0, -0.08, 0.0), yellow=(-2, -0.1, 0.02)),
        cap=dict(skin=0.11), split=dict(highlights=(36, 0.005)), roll=(0.88, 0.35), grain=0.06, grain_size=0.0009, grain_chroma=0.03, soft_focus=0.05, bloom=0.05, vignette=0.1),
    "pr_editorial": mk("Editorial", "雜誌編輯", P, "", "雜誌風：偏低的飽和、清脆的反差、微冷的中性色，時尚專題的冷靜質感。", c=1.26, ex=0.02, sat=-0.28, temp=-0.07,
        hsl=dict(skin=(0, 0.14, 0.01), red=(0, 0.06, -0.02), green=(0, -0.14, -0.02), sky=(0, -0.08, -0.02), yellow=(0, -0.12, 0.0)), split=dict(shadows=(220, 0.006), highlights=(55, 0.003)),
        roll=(0.82, 0.55), grain=0.14, grain_size=0.001, grain_chroma=0.03, vignette=0.12),
    "pr_street": mk("Urban Street", "城市街頭", P, "", "城市街頭：硬朗的反差、去掉多餘的顏色、保留紅與黃的重點，水泥與柏油有質感。", c=1.3, ex=-0.02, toe=0.01, sat=-0.24,
        hsl=dict(red=(0, 0.2, -0.02), amber=(0, 0.1, 0.0), yellow=(0, 0.1, 0.0), skin=(0, 0.16, 0.0), green=(0, -0.16, -0.03), sky=(-6, -0.1, -0.04), blue=(-6, -0.1, -0.04)),
        split=dict(shadows=(205, 0.008), highlights=(50, 0.004)), roll=(0.78, 0.55), allow=dict(shadow_detail=0.05), grain=0.28, grain_size=0.00124, grain_chroma=0.05, grain_rough=0.45, vignette=0.18),
    "pr_arch": mk("Architecture", "建築", P, "", "建築：中性準確的白與灰、清楚的邊線反差、天空稍深，材質與線條優先。", c=1.18, ex=0.03, sat=-0.08,
        hsl=dict(sky=(-4, 0.02, -0.05), blue=(-4, 0.0, -0.05), green=(0, -0.12, -0.01), yellow=(0, -0.1, 0.0), red=(0, -0.04, 0.0)), roll=(0.86, 0.6), clean=0.97, grain=0.0, vignette=0.04),
    "pr_landscape": mk("Landscape", "風景", P, "", "風景：加深的藍天、層次分明的綠、清楚的遠景反差，不過艷。", c=1.2, ex=0.0, sat=0.04, vib=0.08, temp=-0.04,
        hsl=dict(sky=(-6, 0.1, -0.08), blue=(-4, 0.08, -0.08), green=(-8, 0.06, -0.04), lime=(-8, 0.02, -0.02), yellow=(-2, 0.04, 0.0), skin=(0, -0.02, 0.0), teal=(0, 0.06, -0.01)),
        cap=dict(sky=0.17, blue=0.185, green=0.15, lime=0.145), roll=(0.82, 0.55), grain=0.05, grain_size=0.0009, vignette=0.1),
    "pr_golden": mk("Golden Hour", "黃金時刻", P, "", "黃金時刻：把斜陽的金色推出來，高光暖、陰影保留一點冷藍做對比。", c=1.08, ex=0.04, toe=0.014, sat=0.04, temp=0.2,
        hsl=dict(amber=(-2, 0.12, 0.01), yellow=(-6, 0.1, 0.01), skin=(0, 0.02, 0.01), red=(2, 0.06, 0.0), sky=(0, -0.04, -0.02)), split=dict(highlights=(50, 0.016), shadows=(235, 0.016)),
        curves=rgb(r=0.012, b=-0.02, bs=0.008), roll=(0.82, 0.25), allow=dict(white_chroma=4.0, lightgray_chroma=5.0), grain=0.1, grain_size=0.00096, grain_chroma=0.06, halation=0.08, bloom=0.1, vignette=0.14),
    "pr_blue_hour": mk("Blue Hour", "藍色時刻", P, "", "藍色時刻：日落後的靛藍天光、保留燈火的暖橙，冷暖並存。", c=1.12, ex=0.03, toe=0.016, sat=0.02, temp=-0.1,
        hsl=dict(sky=(4, 0.1, -0.03), blue=(4, 0.1, -0.03), purple=(0, 0.08, 0.0), amber=(0, 0.14, 0.0), skin=(2, 0.14, 0.0), yellow=(-4, 0.08, 0.0), green=(8, -0.1, -0.02)),
        cap=dict(sky=0.17, blue=0.19), split=dict(shadows=(240, 0.014), highlights=(35, 0.006)), curves=rgb(bs=0.012), roll=(0.8, 0.35), allow=dict(white_chroma=3.5, lightgray_chroma=4.5),
        grain=0.14, grain_size=0.00104, grain_chroma=0.06, halation=0.1, bloom=0.08, vignette=0.14),
    "pr_night": mk("Night Photography", "夜間攝影", P, "", "夜間：提亮暗部看得見細節、壓住燈光不過曝、中性的色彩，乾淨的夜景。", c=0.92, ex=0.34, toe=0.012, sh=0.97, sat=0.1, temp=0.03,
        hsl=dict(amber=(0, -0.06, 0.0), yellow=(0, -0.08, 0.0), skin=(1, 0.0, 0.0), sky=(0, 0.06, 0.0), blue=(0, 0.04, 0.0)), split=dict(shadows=(225, 0.005)), roll=(0.74, 0.6),
        grain=0.1, grain_size=0.001, grain_chroma=0.04, halation=0.06, vignette=0.1),
    # ------------------------------------------------------------- vintage film
    "vt_90s": mk("90s Film", "九零年代底片", V, "", "九零年代沖印：偏暖偏洋紅的相紙色、微微褪色的黑、飽滿的紅與膚色。", c=1.08, ex=0.05, toe=0.03, sh=0.975, sat=0.02, temp=0.07, tint=0.04,
        hsl=dict(red=(-2, 0.1, 0.0), skin=(-1, 0.06, 0.01), yellow=(-4, 0.04, 0.0), green=(-6, -0.1, -0.01), sky=(-6, -0.08, 0.0)), split=dict(shadows=(300, 0.008), highlights=(46, 0.012)),
        curves=rgb(r=0.01, b=-0.016, bs=0.008), allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.065), grain=0.32, grain_size=0.0013, grain_chroma=0.18, halation=0.08, vignette=0.16, dust=0.06),
    "vt_2000s": mk("2000s Digital", "千禧年數位相機", V, "", "早期數位相機：偏硬的反差、過艷的原色、偏冷的自動白平衡、容易爆的高光。", c=1.28, ex=0.04, toe=0.004, sat=0.12, temp=-0.06,
        hsl=dict(sky=(4, 0.12, 0.0), blue=(6, 0.1, 0.0), green=(8, 0.1, 0.0), red=(-4, 0.12, 0.0), skin=(-3, 0.04, 0.0), yellow=(4, 0.06, 0.0)), cap=dict(sky=0.18, blue=0.195, green=0.16, lime=0.155),
        roll=(0.9, 0.25), allow=dict(shadow_detail=0.05, highlight_sep=0.05), grain=0.2, grain_size=0.0008, grain_chroma=0.5, grain_rough=0.2, aberration=0.12, vignette=0.06),
    "vt_disposable": mk("Disposable Camera", "即可拍", V, "", "即可拍：塑膠鏡頭的暗角與邊緣模糊、閃燈般的硬反差、偏綠的陰影與濃重顆粒。", c=1.22, ex=0.06, toe=0.026, sat=0.04, temp=0.04, tint=-0.03,
        hsl=dict(red=(0, 0.1, 0.0), yellow=(-4, 0.06, 0.0), green=(-4, -0.04, -0.02), sky=(-8, -0.06, -0.02), skin=(0, 0.04, 0.0)), split=dict(shadows=(130, 0.012), highlights=(50, 0.008)),
        curves=rgb(r=0.01, b=-0.018, gs=0.008), allow=dict(white_chroma=3.5, lightgray_chroma=4.5, black_level=0.06), grain=0.46, grain_size=0.0016, grain_chroma=0.22, grain_rough=0.6,
        halation=0.1, vignette=0.4, vignette_soft=0.5, edge_soft=0.35, aberration=0.2, dust=0.1, leak=0.06),
    "vt_instant": mk("Instant Film", "拍立得底片", V, "", "拍立得：低反差、偏青綠的陰影、奶油色的高光、柔軟不銳利的畫面。", c=0.86, ex=0.08, toe=0.055, sh=0.96, sat=-0.12, temp=0.04, tint=-0.04,
        hsl=dict(sky=(-10, -0.06, 0.02), blue=(-12, -0.08, 0.0), green=(4, -0.1, 0.02), red=(4, 0.04, 0.0), skin=(2, 0.08, 0.02), yellow=(2, 0.02, 0.02)), split=dict(shadows=(178, 0.014), highlights=(62, 0.014)),
        curves=rgb(b=-0.02, bs=0.014, gs=0.006), roll=(0.8, 0.3), allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.075, white_level=0.92), grain=0.2, grain_size=0.00124, grain_chroma=0.1,
        soft_focus=0.12, bloom=0.08, vignette=0.22, uneven=0.12),
    "vt_faded": mk("Faded Film", "褪色底片", V, "", "褪色底片：黑與白都被時間磨掉、顏色只剩一半、整體偏暖黃。", c=0.84, ex=0.06, toe=0.06, sh=0.955, sat=-0.3, temp=0.08,
        hsl=dict(skin=(0, 0.2, 0.0), red=(2, 0.06, 0.0), sky=(-6, -0.06, 0.0), green=(-4, -0.06, 0.0)), split=dict(shadows=(48, 0.01), highlights=(58, 0.012)), curves=rgb(b=-0.02, bs=0.012),
        roll=(0.78, 0.3), allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.075, white_level=0.92, shadow_detail=0.03), grain=0.3, grain_size=0.00128, grain_chroma=0.1, dust=0.14, scratches=0.08, vignette=0.14, drift=0.1),
    "vt_grainy": mk("Grainy Film", "粗粒底片", V, "", "粗粒高感光：粗大明顯的顆粒、推沖造成的高反差與偏色的陰影。", c=1.34, ex=0.08, toe=0.02, sat=-0.08, tint=0.02,
        hsl=dict(skin=(0, 0.08, 0.0), red=(0, 0.06, -0.02), green=(-4, -0.1, -0.02), sky=(-4, -0.06, -0.03)), split=dict(shadows=(262, 0.012), highlights=(48, 0.005)), curves=rgb(bs=0.012, gs=-0.006),
        roll=(0.8, 0.45), allow=dict(shadow_detail=0.05, black_level=0.055), grain=0.7, grain_size=0.002, grain_chroma=0.24, grain_rough=0.75, halation=0.06, vignette=0.2),
    "vt_warm": mk("Warm Vintage", "暖調復古", V, "", "暖調復古：濃厚的橙褐色調、柔和的反差、深而不死的陰影，七零年代的家庭相片。", c=1.02, ex=0.02, toe=0.03, sh=0.97, sat=-0.06, temp=0.2,
        hsl=dict(amber=(-4, 0.1, 0.0), red=(4, 0.06, -0.02), yellow=(-8, 0.06, 0.0), green=(-14, -0.16, -0.03), sky=(-10, -0.3, 0.0), blue=(-8, -0.3, -0.02), skin=(0, 0.0, 0.0)),
        split=dict(shadows=(28, 0.012), highlights=(46, 0.016)), curves=rgb(r=0.014, g=0.002, b=-0.03), roll=(0.8, 0.25), allow=dict(white_chroma=4.0, lightgray_chroma=5.0, black_level=0.065, white_level=0.93),
        grain=0.34, grain_size=0.00136, grain_chroma=0.16, halation=0.1, vignette=0.24, dust=0.08),
    "vt_flash": mk("Retro Flash", "復古閃燈", V, "", "復古直打閃燈：前景亮而硬、背景快速變暗、顏色濃、白色帶一點冷。", c=1.36, ex=0.12, toe=0.006, sat=0.1, temp=-0.03,
        hsl=dict(red=(-2, 0.12, 0.0), skin=(-2, 0.06, 0.02), yellow=(0, 0.06, 0.0), sky=(0, 0.04, -0.04), green=(0, 0.02, -0.03)), cap=dict(skin=0.135), split=dict(shadows=(240, 0.008)),
        roll=(0.86, 0.4), allow=dict(shadow_detail=0.045, highlight_sep=0.05), grain=0.24, grain_size=0.0011, grain_chroma=0.14, bloom=0.14, bloom_threshold=0.8, vignette=0.46, vignette_soft=0.45, aberration=0.08),
}
PRO_SHIP = list(PRO)
# How far each look's mood (grading, curve, white balance, per-hue shifts) is strengthened, with the
# same mechanism as the older series: as strong as the target, but never past the point where the
# cleanliness checks (neutral whites, skin hue and chroma, shadow and highlight detail) stop passing.
PRO_TARGET = dict(pr_natural=1.6, jp_clean_day=2.0, pr_arch=1.5, pr_night=1.5, pr_portrait=1.6)
PRO_DEFAULT_TARGET = 2.1
