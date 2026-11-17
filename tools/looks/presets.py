"""Photographer-inspired looks, clean edition.

Interpretations of each photographer's typical published colour (film stock, printing, exposure
habits), not copies of any photo. Research notes are next to each look. Tuned by eye on test
photos; run `python3 fit.py` with reference images to measure and close the remaining gap.

Clean picture rules (checked by build.py, see CLEAN_LIMITS there):
  - no grain, light leaks, halation, bloom or vignette baked into the preset (add them with the
    app's sliders if wanted)
  - blacks stay black-ish (lift <= ~0.04), whites stay white and neutral (engine `clean` pass)
  - skin keeps its hue: the look may change brightness and saturation of skin but not turn it
    green, grey or magenta

Units (see look.py):
  curve       [(in, out)] in display gray values 0..1
  hsl         band -> (hue shift deg, saturation delta, lightness delta)
              bands: red orange yellow green aqua blue purple magenta (skin ~ red/orange)
              in OKLab hue order, so a negative shift on green moves it toward yellow,
              a positive shift toward aqua; negative on blue moves it toward aqua/cyan
  split       range -> (OKLab hue deg, amount); hue 60 warm/orange, 100 yellow, 140 green,
              200 cyan/teal, 250 blue, 300 lavender, 330 magenta, 15 pink-red;
              amount ~0.01 subtle, 0.02 clear
  fx          shader effects (empty for clean looks)
"""

LOOKS = {
    # ───────────── 日系名家 ─────────────
    "kawauchi": dict(
        name="川內倫子", subtitle="Kawauchi", category="JAPAN",
        # Rolleiflex 6x6; "flooded with light", muted pastel palette, light bluish tint, low
        # contrast, lighter blues and darker greens (SFMOMA, Aperture, CPH Mag)
        exposure=0.35, temp=-0.06,
        curve=[(0, 0.02), (0.25, 0.32), (0.5, 0.62), (0.75, 0.85), (1, 1.0)],
        sat=-0.20,
        hsl=dict(red=(-4, 0.05, 0.03), orange=(0, 0.15, 0.05), yellow=(0, -0.3, 0.06),
                 green=(10, -0.15, -0.06), aqua=(0, -0.1, 0.06), blue=(-8, -0.05, 0.10)),
        sat_highlights=-0.12,
        split=dict(shadows=(235, 0.012), mids=(230, 0.008)),
    ),
    "hamada": dict(
        name="濱田英明", subtitle="Hamada", category="JAPAN",
        # Pentax 67II + Kodak Portra 400, family daylight: life-like soft colour, peach skin,
        # fresh pale greens, gentle contrast
        exposure=0.3, temp=0.04,
        curve=[(0, 0.025), (0.25, 0.28), (0.5, 0.56), (0.75, 0.80), (1, 0.99)],
        sat=-0.10,
        hsl=dict(red=(3, -0.08, 0.04), orange=(2, 0.05, 0.08), yellow=(-6, -0.25, 0.08),
                 green=(-10, -0.28, 0.10), aqua=(-6, -0.2, 0.04), blue=(-10, -0.2, 0.06)),
        sat_highlights=-0.15,
        split=dict(shadows=(200, 0.008), highlights=(75, 0.006)),
    ),
    "ninagawa": dict(
        name="蜷川實花", subtitle="Ninagawa", category="JAPAN",
        # "Ninagawa colour": hyper-saturated flowers, goldfish, pinks and reds, dreamy shallow focus
        exposure=0.1, tint=0.12,
        curve=[(0, 0.0), (0.25, 0.22), (0.5, 0.52), (0.75, 0.80), (1, 1.0)],
        sat=0.32, vibrance=0.2,
        hsl=dict(red=(-8, 0.30, -0.02), orange=(-4, -0.12, 0.04), yellow=(-4, 0.1, 0.02),
                 green=(14, 0.15, -0.04), aqua=(6, 0.25, 0), blue=(-10, 0.25, -0.02),
                 purple=(10, 0.3, 0), magenta=(-6, 0.35, 0.02)),
        split=dict(shadows=(330, 0.010)),
    ),
    "okuyama": dict(
        name="奧山由之", subtitle="Okuyama", category="JAPAN",
        # film throughout (incl. Pocari Sweat campaigns): "beautiful and fragile", soft and
        # nostalgic; clean version keeps the cyan-green softness without the rough texture
        exposure=0.2, temp=-0.04, tint=-0.04,
        curve=[(0, 0.025), (0.3, 0.33), (0.6, 0.64), (0.85, 0.87), (1, 0.99)],
        sat=-0.12,
        hsl=dict(red=(4, -0.08, 0.02), orange=(0, 0.0, 0.04), green=(-8, -0.2, 0.04),
                 blue=(-12, -0.15, 0.04)),
        split=dict(shadows=(185, 0.020), mids=(175, 0.004), highlights=(70, 0.006)),
    ),
    "kawashima": dict(
        name="川島小鳥", subtitle="Kawashima", category="UNVERIFIED",
        # Mirai-chan (Sado island), Taiwan series Myojo: bright snapshots, rosy cheeks, warm
        # light, gentle pastel colour that stays lively
        exposure=0.3, temp=0.08, tint=0.06,
        curve=[(0, 0.02), (0.25, 0.28), (0.5, 0.57), (0.75, 0.82), (1, 1.0)],
        sat=0.02, vibrance=0.15,
        hsl=dict(red=(-4, 0.10, 0.03), orange=(-2, 0.08, 0.06), yellow=(-4, -0.1, 0.06),
                 green=(-6, -0.15, 0.06), aqua=(0, 0.05, 0.04), blue=(-8, 0.0, 0.06),
                 magenta=(-4, 0.15, 0.03)),
        split=dict(shadows=(250, 0.006), highlights=(40, 0.008)),
    ),
    "ishida": dict(
        name="石田真澄", subtitle="Ishida", category="UNVERIFIED",
        # youth and everyday light on film: pale, airy 透明感, soft colour, quiet cool air
        exposure=0.3, temp=-0.08,
        curve=[(0, 0.02), (0.25, 0.30), (0.5, 0.60), (0.75, 0.85), (1, 1.0)],
        sat=-0.16,
        hsl=dict(red=(0, 0.0, 0.03), orange=(2, 0.15, 0.07), yellow=(-4, -0.3, 0.06),
                 green=(8, -0.25, 0.04), aqua=(0, 0.05, 0.06), blue=(-10, 0.05, 0.06)),
        sat_highlights=-0.10,
        split=dict(shadows=(210, 0.012), mids=(200, 0.006)),
    ),
    "yokonami": dict(
        name="横浪修", subtitle="Yokonami", category="UNVERIFIED",
        # Assembly / 100 Children: flat even light, bright and orderly, cool neutral, natural skin
        exposure=0.28, temp=-0.06,
        curve=[(0, 0.02), (0.25, 0.28), (0.5, 0.56), (0.75, 0.81), (1, 1.0)],
        sat=-0.10,
        hsl=dict(orange=(0, 0.0, 0.05), green=(-6, -0.2, 0.04), yellow=(-4, -0.2, 0.04),
                 blue=(-4, -0.05, 0.04)),
        split=dict(shadows=(240, 0.006)),
    ),
    "araki": dict(
        name="荒木經惟", subtitle="Araki", category="UNVERIFIED",
        # direct flash colour, flowers and Tokyo: vivid, hard, magenta reds, greenish shadows
        exposure=0.05, tint=-0.04,
        curve=[(0, 0.0), (0.15, 0.05), (0.4, 0.35), (0.7, 0.76), (0.9, 0.96), (1, 1.0)],
        sat=0.22,
        hsl=dict(red=(-10, 0.28, -0.02), orange=(-4, 0.05, 0.04), yellow=(6, 0.1, 0),
                 green=(12, 0.1, -0.04), aqua=(0, 0.1, 0), blue=(-8, 0.1, -0.04),
                 magenta=(0, 0.3, 0)),
        split=dict(shadows=(165, 0.014)),
    ),
    "moriyama": dict(
        name="森山大道", subtitle="Moriyama", category="JAPAN",
        # are-bure-boke, pushed Tri-X printed hard: deep blacks, bright whites, high contrast.
        # Clean edition: no grain baked in (add with the grain slider)
        mono=(0.45, 0.45, 0.10), exposure=-0.05,
        curve=[(0, 0), (0.12, 0.01), (0.35, 0.20), (0.5, 0.45), (0.65, 0.74), (0.85, 0.97), (1, 1)],
    ),
    "ueda": dict(
        name="植田正治", subtitle="Ueda", category="JAPAN",
        # Tottori sand-dune staging: soft airy greys, gentle contrast, faintly warm paper
        mono=(0.3, 0.55, 0.15), exposure=0.12,
        curve=[(0, 0.03), (0.25, 0.28), (0.5, 0.55), (0.75, 0.80), (1, 0.99)],
        split=dict(all=(75, 0.006)),
    ),

    # ───────────── 歐美名家 ─────────────
    "leiter": dict(
        name="Saul Leiter", subtitle="Leiter", category="MASTERS",
        # Kodachrome, often expired: muted hues "veering slightly towards the lavender", soft
        # greys and browns with a small saturated red or yellow accent (NYT, exhibition text)
        exposure=-0.05, temp=0.02,
        curve=[(0, 0.02), (0.25, 0.19), (0.5, 0.46), (0.75, 0.75), (1, 0.98)],
        sat=-0.38,
        hsl=dict(red=(4, 0.70, -0.02), orange=(0, 0.10, 0.0), yellow=(-6, 0.35, 0.0),
                 green=(-16, -0.35, -0.04), aqua=(16, -0.25, -0.02), blue=(14, -0.25, -0.02)),
        split=dict(shadows=(300, 0.012), mids=(305, 0.004), highlights=(80, 0.006)),
    ),
    "eggleston": dict(
        name="William Eggleston", subtitle="Eggleston", category="MASTERS",
        # dye-transfer prints: "clear and vivid rendition of colour", startling reds (Red
        # Ceiling), dense greens, deep skies, warm Southern air
        exposure=0.0, temp=0.08,
        curve=[(0, 0.01), (0.25, 0.21), (0.5, 0.49), (0.75, 0.78), (1, 1.0)],
        sat=0.2,
        hsl=dict(red=(-4, 0.30, -0.04), orange=(0, 0.05, 0), yellow=(-4, 0.1, -0.02),
                 green=(4, 0.2, -0.10), aqua=(10, 0.1, -0.04), blue=(6, 0.25, -0.06)),
        sat_shadows=0.15,
        split=dict(shadows=(340, 0.010)),
    ),
    "meyerowitz": dict(
        name="Joel Meyerowitz", subtitle="Meyerowitz", category="UNVERIFIED",
        # Cape Light, 8x10 Deardorff: subdued, contemplative colour, luminous; "glowing yellows,
        # stormy blues and evening purples", soft blue skies into rose
        exposure=0.2, temp=0.03,
        curve=[(0, 0.02), (0.25, 0.27), (0.5, 0.55), (0.75, 0.80), (1, 1.0)],
        sat=-0.10,
        hsl=dict(orange=(0, 0.0, 0.05), red=(6, -0.05, 0.04), yellow=(-2, 0.15, 0.06),
                 blue=(10, -0.05, 0.02), aqua=(0, -0.1, 0.04), green=(-8, -0.25, 0.02),
                 purple=(0, 0.15, 0.02)),
        split=dict(shadows=(270, 0.010), highlights=(70, 0.008)),
    ),
    "shore": dict(
        name="Stephen Shore", subtitle="Shore", category="UNVERIFIED",
        # Uncommon Places, 8x10 colour negative: plain daylight, slightly faded, yellowish
        # whites, cyan skies, everyday neutrality
        exposure=0.08, temp=0.06,
        curve=[(0, 0.03), (0.25, 0.25), (0.5, 0.52), (0.75, 0.78), (1, 0.99)],
        sat=-0.08,
        hsl=dict(red=(6, -0.05, 0), yellow=(-6, 0.05, 0.02), green=(-12, -0.2, 0),
                 blue=(-16, -0.05, 0.04), aqua=(0, 0.05, 0)),
        split=dict(shadows=(220, 0.006), mids=(95, 0.008), highlights=(95, 0.010)),
    ),
    "webb": dict(
        name="Alex Webb", subtitle="Webb", category="UNVERIFIED",
        # Kodachrome in hard tropical sun, exposed for highlights so shadows go deep: intense
        # colour blocks against near-black shadow
        exposure=-0.05, temp=0.05,
        curve=[(0, 0.0), (0.18, 0.08), (0.35, 0.24), (0.6, 0.66), (0.85, 0.93), (1, 1.0)],
        sat=0.28,
        hsl=dict(red=(-2, 0.2, 0), orange=(0, 0.2, 0.02), yellow=(-6, 0.2, 0.02),
                 green=(-6, 0.1, -0.02), blue=(-6, 0.3, -0.04), aqua=(4, 0.2, 0)),
        sat_shadows=-0.2,
    ),
    "haas": dict(
        name="Ernst Haas", subtitle="Haas", category="UNVERIFIED",
        # Kodachrome pioneer (New York in Color 1952-62): vivid, painterly, backlit glow, warm
        # reds and yellows, deep saturated blues
        exposure=0.05, temp=0.06,
        curve=[(0, 0.0), (0.2, 0.13), (0.5, 0.50), (0.8, 0.86), (1, 1.0)],
        sat=0.22,
        hsl=dict(red=(-2, 0.25, -0.02), orange=(0, 0.15, 0.02), yellow=(-6, 0.25, 0.04),
                 green=(-6, 0.0, -0.04), aqua=(4, 0.15, -0.02), blue=(-4, 0.3, -0.06),
                 purple=(0, 0.2, -0.02)),
        split=dict(shadows=(250, 0.008), highlights=(75, 0.006)),
    ),
    "herzog": dict(
        name="Fred Herzog", subtitle="Herzog", category="MASTERS",
        # 1950s-60s Vancouver Kodachrome street: candy reds, teal-cyan blues, cool shadows
        exposure=0.05,
        curve=[(0, 0.02), (0.25, 0.22), (0.5, 0.50), (0.75, 0.77), (1, 0.99)],
        sat=0.05,
        hsl=dict(red=(-6, 0.4, -0.02), orange=(-4, 0.0, 0), yellow=(-6, 0.05, 0),
                 green=(16, -0.2, -0.02), aqua=(0, 0.2, 0), blue=(-22, 0.15, 0)),
        split=dict(shadows=(200, 0.010)),
    ),
    "mccurry": dict(
        name="Steve McCurry", subtitle="McCurry", category="UNVERIFIED",
        # Kodachrome 64 reportage: warm, rich reds and ochres, deep blue, firm contrast
        exposure=0.0, temp=0.10,
        curve=[(0, 0.0), (0.2, 0.14), (0.5, 0.50), (0.8, 0.86), (1, 1.0)],
        sat=0.18,
        hsl=dict(red=(-2, 0.25, -0.04), orange=(-2, 0.15, -0.02), yellow=(-8, 0.1, -0.02),
                 green=(-12, -0.05, -0.06), aqua=(8, 0.15, -0.04), blue=(-6, 0.3, -0.08)),
        split=dict(shadows=(240, 0.006)),
    ),
    "crewdson": dict(
        name="Gregory Crewdson", subtitle="Crewdson", category="UNVERIFIED",
        # staged twilight: blue-cyan ambient, warm practical lights, quiet desaturation
        exposure=-0.05, temp=-0.12,
        curve=[(0, 0.01), (0.25, 0.20), (0.5, 0.47), (0.75, 0.77), (1, 0.99)],
        sat=-0.08,
        hsl=dict(orange=(4, 0.15, 0.02), yellow=(-6, 0.1, 0.02), green=(16, -0.3, -0.04),
                 blue=(-10, 0.1, -0.02)),
        split=dict(shadows=(225, 0.020), mids=(210, 0.008), highlights=(65, 0.010)),
    ),
    "hido": dict(
        name="Todd Hido", subtitle="Hido", category="UNVERIFIED",
        # foggy suburbs at night: muted cyan-green air, warm windows; clean edition keeps the
        # mood without the haze lift
        exposure=-0.05,
        curve=[(0, 0.03), (0.3, 0.28), (0.6, 0.58), (0.85, 0.84), (1, 0.97)],
        sat=-0.30,
        hsl=dict(orange=(0, 0.3, 0.02), yellow=(-4, 0.1, 0), green=(10, -0.2, 0)),
        split=dict(shadows=(185, 0.018), mids=(175, 0.003)),
    ),
    "parr": dict(
        name="Martin Parr", subtitle="Parr", category="UNVERIFIED",
        # ring flash in daylight: garish saturated colour, bright and punchy, slightly cool
        exposure=0.12, temp=-0.12, tint=0.04,
        curve=[(0, 0.0), (0.2, 0.16), (0.5, 0.54), (0.8, 0.90), (1, 1.0)],
        sat=0.3, vibrance=0.2,
        hsl=dict(red=(-4, 0.25, 0), orange=(0, -0.05, 0.04), yellow=(0, 0.1, 0.04),
                 green=(-14, 0.35, 0.02), blue=(-12, 0.35, 0), aqua=(0, 0.35, 0.02),
                 magenta=(-6, 0.35, 0.02)),
    ),
    "lindbergh": dict(
        name="Peter Lindbergh", subtitle="Lindbergh", category="UNVERIFIED",
        # cinematic fashion B&W: soft mid contrast, rich black, faintly cool
        mono=(0.4, 0.45, 0.15), exposure=0.05,
        curve=[(0, 0.01), (0.15, 0.07), (0.5, 0.50), (0.85, 0.89), (1, 0.99)],
        split=dict(all=(230, 0.004)),
    ),

    # ───────────── 電影・夜（導演 / 電影攝影的色調）─────────────
    "iwai": dict(
        name="岩井俊二", subtitle="Iwai", category="UNVERIFIED",
        # Love Letter era (cinematographer Noboru Shinoda): cold blue-white air, pale colour
        exposure=0.3, temp=-0.30,
        curve=[(0, 0.02), (0.25, 0.30), (0.5, 0.59), (0.75, 0.84), (1, 1.0)],
        sat=-0.22,
        hsl=dict(orange=(4, 0.25, 0.05), red=(4, 0.0, 0.02), green=(16, -0.3, 0.04),
                 blue=(-6, 0.0, 0.04)),
        sat_highlights=-0.15,
        split=dict(shadows=(240, 0.022), mids=(230, 0.010)),
    ),
    "shinkai": dict(
        name="新海誠", subtitle="Shinkai", category="UNVERIFIED",
        # anime-background light: saturated cyan-blue sky, clean blue shadows, bright air
        exposure=0.12, temp=-0.08,
        curve=[(0, 0.01), (0.25, 0.25), (0.5, 0.55), (0.75, 0.82), (1, 1.0)],
        sat=0.15, vibrance=0.3,
        hsl=dict(blue=(-14, 0.5, 0.04), aqua=(10, 0.4, 0.04), green=(14, 0.1, 0.02),
                 orange=(0, 0.05, 0.05), yellow=(-8, -0.1, 0.06)),
        split=dict(shadows=(258, 0.024)),
    ),
    "wkw": dict(
        name="王家衛", subtitle="Wong Kar-wai", category="CINEMA",
        # Christopher Doyle palette: green-teal shadows, hot reds and amber
        exposure=-0.02,
        curve=[(0, 0.0), (0.15, 0.07), (0.5, 0.49), (0.8, 0.84), (1, 0.99)],
        sat=0.15,
        hsl=dict(red=(-4, 0.3, -0.02), orange=(0, 0.15, 0.0), yellow=(-10, 0.1, -0.02),
                 green=(20, 0.1, -0.04), aqua=(0, 0.2, -0.02), blue=(-20, 0.0, -0.04)),
        split=dict(shadows=(165, 0.026), highlights=(80, 0.008)),
    ),
    "fanho": dict(
        name="何藩", subtitle="Fan Ho", category="CINEMA",
        # 1950s Hong Kong light and smoke: deep blacks, glowing highlights, warm silver
        mono=(0.35, 0.5, 0.15), exposure=0.0,
        curve=[(0, 0.0), (0.2, 0.06), (0.45, 0.36), (0.7, 0.76), (0.9, 0.96), (1, 1)],
        split=dict(all=(70, 0.006)),
    ),
}


# Looks that passed verification against the photographer's real work (see VERIFICATION.md).
# Only these ship in the app; the rest stay here for reference and future calibration.
# Value: the note shown in the app next to the comparison image.
SHIP = {
    "kawauchi": "以 28 張川內倫子作品校正、另一半作品驗證：明暗、色偏、飽和度都落在她自己作品之間的差距範圍內。",
    "hamada": "以 47 張濱田英明作品校正、另一半作品驗證：明暗、色偏、飽和度都落在他自己作品之間的差距範圍內。",
    "okuyama": "以 42 張奧山由之作品校正、另一半作品驗證：明暗、色偏、飽和度都落在他自己作品之間的差距範圍內。",
    "moriyama": "以森山大道作品校正、另一半作品驗證：黑白明暗與色調落在他自己作品之間的差距範圍內（參考作品較少，9 張）。",
    "ueda": "以 38 張植田正治作品校正、另一半作品驗證：黑白明暗與色調落在他自己作品之間的差距範圍內。",
    "leiter": "以 25 張 Saul Leiter 彩色作品校正、另一半作品驗證：明暗、色偏、飽和度都落在他自己作品之間的差距範圍內。",
    "eggleston": "以 18 張 Eggleston 作品校正、另一半作品驗證：明暗、色偏對上；飽和度保留染印法的鮮豔，不跟著翻拍照片降低。",
    "herzog": "以 26 張 Fred Herzog 作品校正、另一半作品驗證：明暗、色偏對上；飽和度保留 Kodachrome 的鮮豔，不跟著翻拍照片降低。",
    "fanho": "以 57 張何藩作品校正、另一半作品驗證：黑白明暗與暖色銀鹽調落在他自己作品之間的差距範圍內。",
}
