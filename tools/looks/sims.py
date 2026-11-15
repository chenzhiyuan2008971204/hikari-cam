"""Film Simulation series: seven original looks that complete the set of classic film-simulation
characters (the other five characters already ship in the Daylight Collection).

Inspired by the colour philosophy of Japanese film simulations; the recipes, names and LUTs are
Hikari's own, not a copy of any manufacturer's algorithm.

  character (inspiration)            Hikari look
  standard slide (Provia)            Standard Chrome      <- here
  vivid slide (Velvia)               Vivid Landscape      (daylight.py)
  soft slide (Astia)                 Soft Chrome          <- here
  documentary chrome (Classic Chrome) Muted Chrome        (daylight.py)
  street negative (Classic Neg)      Street Negative      (daylight.py)
  nostalgic negative (Nostalgic Neg) Nostalgic Summer     (daylight.py)
  true-colour negative (Reala Ace)   True Negative        <- here
  cinema (Eterna)                    Cine Daylight        (daylight.py)
  bleach bypass (Eterna BB)          Silver Bypass        <- here
  studio negative, hard (Pro Neg Hi) Studio Hi            <- here
  studio negative, soft (Pro Neg Std) Studio Std          <- here
  fine-grain mono (Acros)            Mono Fine            <- here
"""
from filmkit import filmic, fx

S = "sim"

SIMS = {
    # the all-rounder: neutral, slightly crisp, faithful colour with a clean blue sky
    "standard_chrome": dict(
        name="Standard Chrome", zh="標準正片", series=S, inspired="Provia",
        note="全能標準：忠實自然的顏色、適中的反差、乾淨的藍天與白，任何題材都適用。",
        colour=dict(
            bands="fine",
            curve=filmic(exposure=0.02, contrast=1.1, toe=0.008, shoulder=0.992), curve_chroma=0.45,
            sat=0.05,
            hsl=dict(red=(0, 0.02, 0.0), skin=(0, -0.03, 0.01), yellow=(-1, 0.02, 0.0), lime=(-1, 0.02, 0.0),
                     green=(1, 0.04, -0.01), cyan=(0, 0.04, 0.0), sky=(1, 0.06, -0.02), blue=(0, 0.04, -0.03)),
            chroma_cap=dict(sky=0.16, blue=0.19, green=0.15, lime=0.15, skin=0.12),
            hl_rolloff=(0.86, 0.45), selective=dict(whites=(0, 0, -0.03, 0)), clean=0.94,
        ),
        fx=fx(grain=0.1, grain_size=0.00096, grain_chroma=0.04, grain_rough=0.2, halation=0.02, hal_threshold=0.92, vignette=0.06),
    ),
    # soft slide: gentle tone, soft highlights, vivid but tender colour, flattering skin
    "soft_chrome": dict(
        name="Soft Chrome", zh="柔和正片", series=S, inspired="Astia",
        note="柔和正片：柔軟的影調與高光、顏色鮮明卻不刺眼，膚色柔潤，適合人像與日常。",
        colour=dict(
            bands="fine", temp=0.02,
            curve=filmic(exposure=0.08, contrast=0.94, toe=0.016, shoulder=0.988), curve_chroma=0.4,
            sat=0.06, vibrance=0.06,
            hsl=dict(red=(2, -0.04, 0.01), skin=(1, -0.06, 0.04), amber=(0, 0.0, 0.02), yellow=(-2, 0.02, 0.02),
                     lime=(-2, 0.0, 0.02), green=(0, 0.02, 0.01), cyan=(0, 0.04, 0.02), sky=(-2, 0.06, 0.03),
                     blue=(0, 0.02, 0.02), magenta=(0, 0.04, 0.01)),
            chroma_cap=dict(skin=0.11, sky=0.14, blue=0.17, green=0.14, lime=0.14, red=0.18),
            hl_rolloff=(0.82, 0.5), split=dict(shadows=(250, 0.004), highlights=(50, 0.004)),
            selective=dict(whites=(0, 0, -0.03, 0)), clean=0.93,
        ),
        fx=fx(grain=0.1, grain_size=0.00096, grain_chroma=0.04, grain_rough=0.2, bloom=0.03, bloom_threshold=0.84, vignette=0.06),
    ),
    # true-colour negative: accurate hue, lower contrast, long tonal range, natural sat
    "true_negative": dict(
        name="True Negative", zh="真實負片", series=S, inspired="Reala Ace",
        note="真實負片：色相準確、反差柔和、寬廣的階調，暗部與高光都留有細節，像肉眼看到的樣子。",
        colour=dict(
            bands="fine",
            curve=filmic(exposure=0.05, contrast=0.96, toe=0.014, shoulder=0.99), curve_chroma=0.4,
            sat=-0.02,
            hsl=dict(skin=(0, -0.04, 0.02), yellow=(-1, -0.04, 0.01), lime=(0, -0.04, 0.01), green=(1, -0.04, 0.0),
                     sky=(0, 0.02, 0.0), blue=(0, -0.02, 0.0)),
            chroma_cap=dict(sky=0.15, blue=0.18, green=0.14, lime=0.14, skin=0.12),
            hl_rolloff=(0.84, 0.5), split=dict(shadows=(220, 0.004)), clean=0.95,
        ),
        fx=fx(grain=0.18, grain_size=0.00104, grain_chroma=0.06, grain_rough=0.3, halation=0.03, hal_threshold=0.9, vignette=0.08),
    ),
    # bleach bypass: high contrast, strongly reduced colour, silvery highlights, dense blacks
    "silver_bypass": dict(
        name="Silver Bypass", zh="銀殘留", series=S, inspired="Eterna Bleach Bypass",
        note="銀殘留：高反差、低飽和、帶銀灰光澤的高光與厚重的黑，戲劇性的電影質感。",
        colour=dict(
            bands="fine",
            curve=filmic(exposure=-0.02, contrast=1.42, toe=0.01, shoulder=0.975), curve_chroma=0.2,
            sat=-0.42,
            hsl=dict(skin=(0, 0.32, 0.0), amber=(0, 0.15, 0.0), red=(0, 0.1, -0.02), green=(4, -0.1, -0.02), sky=(0, -0.05, -0.03), blue=(0, -0.05, -0.04)),
            chroma_cap=dict(sky=0.09, blue=0.1, green=0.07, lime=0.07),
            hl_rolloff=(0.78, 0.6), split=dict(shadows=(215, 0.008), highlights=(80, 0.004)),
            clean=0.92, allow=dict(skin_chroma=(0.6, 1.7)),
        ),
        fx=fx(grain=0.36, grain_size=0.00136, grain_chroma=0.04, grain_rough=0.5, halation=0.05, hal_threshold=0.86, vignette=0.18),
    ),
    # studio negative, hard: punchy tone for controlled light, restrained colour, exact skin
    "studio_hi": dict(
        name="Studio Hi", zh="棚拍硬調", series=S, inspired="Pro Neg Hi",
        note="棚拍硬調：俐落的反差、收斂的色彩與準確立體的膚色，適合人像、街頭人物與商品。",
        colour=dict(
            bands="fine", temp=0.03,
            curve=filmic(exposure=0.03, contrast=1.2, toe=0.01, shoulder=0.988), curve_chroma=0.3,
            sat=-0.06,
            hsl=dict(red=(2, -0.08, 0.0), skin=(0, 0.02, 0.02), amber=(0, -0.06, 0.0), yellow=(-2, -0.14, 0.0),
                     lime=(-2, -0.18, 0.0), green=(0, -0.18, -0.01), teal=(0, -0.14, 0.0), cyan=(0, -0.08, 0.0),
                     sky=(0, 0.0, 0.01), blue=(0, -0.02, 0.0), purple=(0, -0.12, 0.0), magenta=(0, -0.1, 0.0)),
            chroma_cap=dict(skin=0.115, sky=0.13, blue=0.15, green=0.1, lime=0.1),
            hl_rolloff=(0.84, 0.45), split=dict(shadows=(230, 0.005)), clean=0.94,
        ),
        fx=fx(grain=0.14, grain_size=0.00096, grain_chroma=0.04, grain_rough=0.25, vignette=0.08),
    ),
    # studio negative, soft: very gentle gradation, low sat, soft shadows for skin
    "studio_std": dict(
        name="Studio Std", zh="棚拍柔調", series=S, inspired="Pro Neg Std",
        note="棚拍柔調：非常柔和的階調、低飽和、柔軟的陰影，膚色細膩，保留最大的後製空間。",
        colour=dict(
            bands="fine",
            curve=filmic(exposure=0.06, contrast=0.88, toe=0.018, shoulder=0.988), curve_chroma=0.3,
            sat=-0.14,
            hsl=dict(red=(2, -0.08, 0.0), skin=(0, 0.0, 0.03), amber=(0, -0.06, 0.01), yellow=(-2, -0.14, 0.01),
                     lime=(-2, -0.16, 0.01), green=(0, -0.16, 0.0), teal=(0, -0.12, 0.0), sky=(0, -0.06, 0.01),
                     blue=(0, -0.08, 0.0), purple=(0, -0.1, 0.0), magenta=(0, -0.1, 0.0)),
            chroma_cap=dict(skin=0.11, sky=0.12, blue=0.14, green=0.1, lime=0.1),
            hl_rolloff=(0.8, 0.5), split=dict(shadows=(240, 0.003), highlights=(60, 0.003)), clean=0.94,
        ),
        fx=fx(grain=0.12, grain_size=0.00096, grain_chroma=0.04, grain_rough=0.25, vignette=0.06),
    ),
    # fine-grain mono: rich mid-tone separation, deep blacks, crisp fine grain; yellow-filter mix
    "mono_fine": dict(
        name="Mono Fine", zh="細緻黑白", series=S, inspired="Acros",
        note="細緻黑白：豐富的中間調層次、深邃的黑、細緻銳利的顆粒；天空略深、膚色明亮（黃濾鏡感）。",
        colour=dict(
            mono=(0.36, 0.52, 0.12),
            curve=filmic(exposure=0.02, contrast=1.22, toe=0.008, shoulder=0.99),
            clean=1.0,
        ),
        fx=fx(grain=0.3, grain_size=0.00096, grain_chroma=0.0, grain_rough=0.35, halation=0.0, vignette=0.12),
    ),
}
SIM_SHIP = list(SIMS)
# how far each look's mood is strengthened (same mechanism as the other series)
SIM_STRENGTH = dict(standard_chrome=1.5, soft_chrome=1.6, true_negative=1.3, silver_bypass=1.0,
                    studio_hi=1.4, studio_std=1.5)
