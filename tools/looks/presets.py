"""Photographer-inspired looks.

These are interpretations of each photographer's typical published colour (film stock, printing,
exposure habits), not copies of any photo. Values are tuned by eye on test photos; run
`python3 fit.py` with reference images to measure and close the remaining gap.

Units (see look.py):
  curve       [(in, out)] in display gray values 0..1
  hsl         band -> (hue shift deg, saturation delta, lightness delta)
              bands: red orange yellow green aqua blue purple magenta (skin ~ red/orange)
  split       range -> (OKLab hue deg, amount); hue 60 warm/orange, 100 yellow, 140 green,
              200 cyan/teal, 250 blue, 330 magenta, 15 pink-red; amount ~0.01 subtle, 0.03 strong
  black/white output levels per channel (black > 0 = matte / faded blacks)
  fx          shader effects: grain vignette halation bloom leak (0..1)
"""

LOOKS = {
    # ───────────── 日系 ─────────────
    "hamada": dict(
        name="濱田英明", subtitle="Hamada", category="JAPAN",
        # Pentax 67 + Portra 400, overexposed: soft daylight, pale greens, peach skin, quiet shadows
        exposure=0.35, temp=0.05,
        curve=[(0, 0.06), (0.25, 0.30), (0.5, 0.57), (0.75, 0.80), (1, 0.96)],
        sat=-0.12,
        hsl=dict(red=(3, -0.08, 0.04), orange=(2, 0.08, 0.10), yellow=(-6, -0.25, 0.08),
                 green=(-10, -0.30, 0.12), aqua=(-6, -0.25, 0.04), blue=(-10, -0.25, 0.06)),
        sat_highlights=-0.15,
        split=dict(shadows=(195, 0.012), highlights=(75, 0.010)),
        fx=dict(grain=0.18),
    ),
    "kawauchi": dict(
        name="川內倫子", subtitle="Kawauchi", category="JAPAN",
        # Rolleiflex 6x6, generous overexposure: luminous, near-white highlights, pastel, faint pink/cyan
        exposure=0.6, temp=-0.05,
        curve=[(0, 0.12), (0.25, 0.42), (0.5, 0.68), (0.75, 0.87), (1, 0.98)],
        sat=-0.28,
        hsl=dict(red=(-6, 0.0, 0.04), orange=(-4, -0.05, 0.06), green=(10, -0.35, 0.06),
                 blue=(-8, -0.2, 0.06), yellow=(0, -0.35, 0.06)),
        sat_highlights=-0.4,
        split=dict(shadows=(220, 0.014), highlights=(350, 0.010)),
        fx=dict(bloom=0.15, grain=0.12),
    ),
    "ninagawa": dict(
        name="蜷川實花", subtitle="Ninagawa", category="JAPAN",
        # saturated reds, pinks and magentas, candy colours, glossy highlights
        exposure=0.1, tint=0.15,
        curve=[(0, 0.0), (0.25, 0.21), (0.5, 0.52), (0.75, 0.80), (1, 1.0)],
        sat=0.35, vibrance=0.2,
        hsl=dict(red=(-10, 0.30, -0.02), orange=(-6, 0.1, 0.04), yellow=(-4, 0.1, 0.02),
                 green=(16, 0.15, -0.04), aqua=(6, 0.25, 0), blue=(-10, 0.25, -0.02),
                 purple=(10, 0.3, 0), magenta=(-6, 0.35, 0.02)),
        split=dict(shadows=(330, 0.012), highlights=(10, 0.008)),
        fx=dict(),
    ),
    "okuyama": dict(
        name="奧山由之", subtitle="Okuyama", category="JAPAN",
        # dreamy faded film: milky blacks, cyan-green cast, warm flare in highlights
        exposure=0.2, temp=-0.05, tint=-0.15,
        curve=[(0, 0.14), (0.3, 0.36), (0.6, 0.64), (0.85, 0.85), (1, 0.93)],
        sat=-0.15,
        hsl=dict(red=(4, -0.1, 0.02), green=(-10, -0.2, 0.04), blue=(-12, -0.2, 0.04),
                 orange=(0, -0.05, 0.04)),
        split=dict(shadows=(185, 0.032), mids=(170, 0.014), highlights=(70, 0.014)),
        fx=dict(grain=0.25, bloom=0.2, leak=0.2),
    ),
    "yokonami": dict(
        name="横浪修", subtitle="Yokonami", category="JAPAN",
        # clean, bright, flat even light; gentle cool neutrality, natural skin
        exposure=0.3, temp=-0.08,
        curve=[(0, 0.05), (0.25, 0.29), (0.5, 0.56), (0.75, 0.80), (1, 1.0)],
        sat=-0.12,
        hsl=dict(orange=(0, 0.0, 0.05), green=(-6, -0.25, 0.04), yellow=(-4, -0.25, 0.04),
                 blue=(-4, -0.1, 0.04)),
        split=dict(shadows=(240, 0.008)),
        fx=dict(),
    ),
    "iwai": dict(
        name="岩井俊二", subtitle="Iwai", category="JAPAN",
        # Love Letter era: cold blue-white air, bloomed highlights, pale colour
        exposure=0.4, temp=-0.55,
        curve=[(0, 0.07), (0.25, 0.32), (0.5, 0.60), (0.75, 0.84), (1, 0.99)],
        sat=-0.3,
        hsl=dict(orange=(4, 0.05, 0.05), red=(4, -0.1, 0.02), green=(16, -0.3, 0.04),
                 blue=(-6, 0.0, 0.04)),
        sat_highlights=-0.4,
        split=dict(shadows=(240, 0.028), mids=(230, 0.016), highlights=(215, 0.010)),
        fx=dict(bloom=0.3, grain=0.15),
    ),
    "araki": dict(
        name="荒木經惟", subtitle="Araki", category="JAPAN",
        # direct flash on consumer film: hot warm colour, deep reds, contrasty, a bit gaudy
        exposure=0.05, temp=0.0, tint=-0.05,
        curve=[(0, 0.0), (0.15, 0.04), (0.4, 0.34), (0.7, 0.76), (0.9, 0.96), (1, 1.0)],
        sat=0.25,
        hsl=dict(red=(-10, 0.3, -0.02), orange=(-4, 0.05, 0.04), yellow=(6, 0.1, 0),
                 green=(12, 0.1, -0.04), aqua=(0, 0.1, 0), blue=(-8, 0.1, -0.04),
                 magenta=(0, 0.3, 0)),
        split=dict(shadows=(165, 0.020), highlights=(40, 0.006)),
        fx=dict(grain=0.25, vignette=0.25),
    ),
    "shinkai": dict(
        name="新海誠", subtitle="Shinkai", category="JAPAN",
        # anime-background light: saturated cyan-blue sky, glowing warm highlights, clean blue shadows
        exposure=0.15,
        curve=[(0, 0.04), (0.25, 0.26), (0.5, 0.55), (0.75, 0.82), (1, 1.0)],
        sat=0.18, vibrance=0.3,
        temp=-0.1,
        hsl=dict(blue=(-14, 0.5, 0.04), aqua=(10, 0.4, 0.04), green=(14, 0.1, 0.02),
                 orange=(0, 0.05, 0.05), yellow=(-8, -0.1, 0.06)),
        split=dict(shadows=(258, 0.030), mids=(240, 0.006), highlights=(75, 0.010)),
        fx=dict(bloom=0.3),
    ),
    "moriyama": dict(
        name="森山大道", subtitle="Moriyama", category="JAPAN",
        # pushed Tri-X printed hard: crushed blacks, burnt whites, rough grain
        mono=(0.45, 0.45, 0.10), exposure=-0.1,
        curve=[(0, 0), (0.15, 0.0), (0.35, 0.18), (0.5, 0.45), (0.65, 0.75), (0.85, 0.98), (1, 1)],
        fx=dict(grain=0.7, vignette=0.35),
    ),
    "ueda": dict(
        name="植田正治", subtitle="Ueda", category="JAPAN",
        # sand-dune stagecraft: soft, airy grays, gentle warm paper, no hard blacks
        mono=(0.3, 0.55, 0.15), exposure=0.15,
        curve=[(0, 0.08), (0.25, 0.30), (0.5, 0.55), (0.75, 0.79), (1, 0.96)],
        split=dict(all=(75, 0.010)),
        fx=dict(grain=0.2, vignette=0.15),
    ),

    # ───────────── 攝影名家 ─────────────
    "leiter": dict(
        name="Saul Leiter", subtitle="Leiter", category="MASTERS",
        # expired Kodachrome in snow and rain: muted earth tones, deep shadows, red accents
        exposure=-0.1, temp=0.1,
        curve=[(0, 0.03), (0.25, 0.18), (0.5, 0.45), (0.75, 0.74), (1, 0.94)],
        sat=-0.35,
        hsl=dict(red=(4, 0.65, -0.02), orange=(6, 0.0, 0.0), yellow=(-8, -0.2, -0.02),
                 green=(-18, -0.35, -0.04), aqua=(10, -0.2, -0.02), blue=(8, -0.25, -0.02)),
        split=dict(shadows=(60, 0.012), highlights=(80, 0.022)),
        fx=dict(grain=0.3, vignette=0.3),
    ),
    "eggleston": dict(
        name="William Eggleston", subtitle="Eggleston", category="MASTERS",
        # dye-transfer prints: dense saturated reds and greens, warm air, deep skies
        exposure=0.0, temp=0.12,
        curve=[(0, 0.02), (0.25, 0.20), (0.5, 0.49), (0.75, 0.78), (1, 0.98)],
        sat=0.2,
        hsl=dict(red=(-4, 0.3, -0.04), orange=(0, 0.05, 0), yellow=(-4, 0.1, -0.02),
                 green=(4, 0.2, -0.10), aqua=(10, 0.1, -0.04), blue=(6, 0.25, -0.06)),
        split=dict(shadows=(340, 0.014), highlights=(80, 0.014)),
        sat_shadows=0.15,
        fx=dict(grain=0.15),
    ),
    "mccurry": dict(
        name="Steve McCurry", subtitle="McCurry", category="MASTERS",
        # Kodachrome 64: warm, rich reds and ochres, deep cyan-blue, firm contrast
        exposure=0.0, temp=0.12,
        curve=[(0, 0.0), (0.2, 0.14), (0.5, 0.50), (0.8, 0.86), (1, 1.0)],
        sat=0.18,
        hsl=dict(red=(-2, 0.25, -0.04), orange=(-2, 0.15, -0.02), yellow=(-8, 0.1, -0.02),
                 green=(-12, -0.05, -0.06), aqua=(8, 0.15, -0.04), blue=(-6, 0.3, -0.08)),
        split=dict(shadows=(240, 0.006), highlights=(75, 0.010)),
        fx=dict(grain=0.12, vignette=0.2),
    ),
    "herzog": dict(
        name="Fred Herzog", subtitle="Herzog", category="MASTERS",
        # 1950s Kodachrome street: candy reds, teal-cyan blues, slightly cool shadows, soft prints
        exposure=0.05,
        curve=[(0, 0.03), (0.25, 0.22), (0.5, 0.50), (0.75, 0.77), (1, 0.96)],
        sat=0.05,
        hsl=dict(red=(-6, 0.4, -0.02), orange=(-4, 0.0, 0), yellow=(-6, 0.05, 0),
                 green=(16, -0.2, -0.02), aqua=(0, 0.2, 0), blue=(-22, 0.15, 0)),
        split=dict(shadows=(200, 0.012), highlights=(80, 0.010)),
        fx=dict(grain=0.2),
    ),
    "shore": dict(
        name="Stephen Shore", subtitle="Shore", category="MASTERS",
        # 1970s large format colour neg: mild fade, yellowish whites, cyan skies, everyday light
        exposure=0.1, temp=0.08,
        curve=[(0, 0.06), (0.25, 0.26), (0.5, 0.52), (0.75, 0.77), (1, 0.95)],
        sat=-0.08,
        hsl=dict(red=(6, -0.05, 0), yellow=(-6, 0.05, 0.02), green=(-12, -0.2, 0),
                 blue=(-16, -0.05, 0.04), aqua=(0, 0.05, 0)),
        split=dict(shadows=(220, 0.008), highlights=(95, 0.020)),
        black=(0.03, 0.03, 0.05),
        fx=dict(grain=0.1),
    ),
    "meyerowitz": dict(
        name="Joel Meyerowitz", subtitle="Meyerowitz", category="MASTERS",
        # Cape Light: 8x10 colour, luminous soft pastels, peach and powder blue
        exposure=0.25, temp=0.04,
        curve=[(0, 0.05), (0.25, 0.29), (0.5, 0.56), (0.75, 0.80), (1, 0.98)],
        sat=-0.12,
        hsl=dict(orange=(0, 0.0, 0.05), red=(6, -0.1, 0.04), blue=(-6, -0.1, 0.06),
                 aqua=(0, -0.1, 0.04), green=(-8, -0.25, 0.04)),
        split=dict(shadows=(240, 0.010), highlights=(50, 0.012)),
        fx=dict(grain=0.08),
    ),
    "wkw": dict(
        name="王家衛", subtitle="Wong Kar-wai", category="MASTERS",
        # Christopher Doyle palette: green-teal shadows, hot reds and amber, crushed blacks
        exposure=-0.05,
        curve=[(0, 0.0), (0.15, 0.06), (0.5, 0.48), (0.8, 0.84), (1, 0.97)],
        sat=0.15,
        hsl=dict(red=(-4, 0.3, -0.02), orange=(4, 0.15, 0.0), yellow=(-10, 0.1, -0.02),
                 green=(20, 0.1, -0.04), aqua=(0, 0.2, -0.02), blue=(-20, 0.0, -0.04)),
        split=dict(shadows=(165, 0.030), mids=(150, 0.010), highlights=(80, 0.016)),
        fx=dict(grain=0.3, halation=0.2, vignette=0.25),
    ),
    "fanho": dict(
        name="何藩", subtitle="Fan Ho", category="MASTERS",
        # 1950s Hong Kong light and smoke: deep blacks, glowing highlights, warm silver
        mono=(0.35, 0.5, 0.15), exposure=0.0,
        curve=[(0, 0.0), (0.2, 0.06), (0.45, 0.36), (0.7, 0.76), (0.9, 0.96), (1, 1)],
        split=dict(all=(70, 0.008)),
        fx=dict(grain=0.25, bloom=0.2, vignette=0.3),
    ),
    "webb": dict(
        name="Alex Webb", subtitle="Webb", category="MASTERS",
        # Kodachrome in hard tropical sun: black shadows, saturated walls, bright colour blocks
        exposure=0.0, temp=0.06,
        curve=[(0, 0.0), (0.18, 0.03), (0.35, 0.22), (0.6, 0.66), (0.85, 0.93), (1, 1.0)],
        sat=0.3,
        hsl=dict(red=(-2, 0.2, 0), orange=(0, 0.2, 0.02), yellow=(-6, 0.2, 0.02),
                 green=(-6, 0.1, -0.02), blue=(-6, 0.3, -0.04), aqua=(4, 0.2, 0)),
        sat_shadows=-0.2,
        fx=dict(grain=0.12, vignette=0.15),
    ),
    "crewdson": dict(
        name="Gregory Crewdson", subtitle="Crewdson", category="MASTERS",
        # staged twilight: blue-cyan ambient, warm practical lights, quiet desaturation
        exposure=-0.1, temp=-0.15,
        curve=[(0, 0.02), (0.25, 0.20), (0.5, 0.47), (0.75, 0.76), (1, 0.97)],
        sat=-0.1,
        hsl=dict(orange=(4, 0.15, 0.02), yellow=(-6, 0.1, 0.02), green=(16, -0.3, -0.04),
                 blue=(-10, 0.1, -0.02)),
        split=dict(shadows=(225, 0.024), mids=(210, 0.010), highlights=(65, 0.022)),
        fx=dict(vignette=0.3, bloom=0.1),
    ),
    "hido": dict(
        name="Todd Hido", subtitle="Hido", category="MASTERS",
        # foggy night suburbs: hazy lifted blacks, cyan-green ambient, desaturated
        exposure=-0.05, tint=-0.1,
        curve=[(0, 0.10), (0.3, 0.30), (0.6, 0.58), (0.85, 0.82), (1, 0.92)],
        sat=-0.35,
        hsl=dict(orange=(6, 0.2, 0.02), yellow=(-4, 0.1, 0), green=(10, -0.2, 0)),
        split=dict(shadows=(185, 0.024), mids=(170, 0.012), highlights=(75, 0.014)),
        fx=dict(grain=0.3, vignette=0.25, bloom=0.15),
    ),
    "parr": dict(
        name="Martin Parr", subtitle="Parr", category="MASTERS",
        # ring flash in daylight: garish saturated colour, bright punchy, slightly cool
        exposure=0.15, temp=-0.15, tint=0.05,
        curve=[(0, 0.0), (0.2, 0.16), (0.5, 0.54), (0.8, 0.90), (1, 1.0)],
        sat=0.3, vibrance=0.2,
        hsl=dict(red=(-4, 0.25, 0), orange=(0, -0.05, 0.04), yellow=(0, 0.1, 0.04),
                 green=(-14, 0.35, 0.02), blue=(-12, 0.35, 0), aqua=(0, 0.35, 0.02),
                 magenta=(-6, 0.35, 0.02)),
        split=dict(highlights=(250, 0.006)),
        fx=dict(vignette=0.1),
    ),
    "lindbergh": dict(
        name="Peter Lindbergh", subtitle="Lindbergh", category="MASTERS",
        # cinematic fashion B&W: soft mid contrast, rich black, slightly cool, fine grain
        mono=(0.4, 0.45, 0.15), exposure=0.05,
        curve=[(0, 0.02), (0.15, 0.08), (0.5, 0.50), (0.85, 0.88), (1, 0.97)],
        split=dict(all=(230, 0.004)),
        fx=dict(grain=0.3, vignette=0.2),
    ),
}
