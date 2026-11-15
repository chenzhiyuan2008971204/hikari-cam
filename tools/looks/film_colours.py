"""Colour recipes v2 for the twelve Hikari film looks, on the fine 12-band engine.

Each look has a clear intent (written as `note` in films.py) and the same clean-picture guards as
the Daylight Collection: chroma ceilings, highlight colour roll-off, clean whites, protected skin.
"""
from filmkit import filmic

C = {
    # Portrait negative: bright, creamy peach skin, muted olive greens, soft teal-cyan sky,
    # warm cream highlights, cool shadows, gentle contrast
    "mellow400": dict(
        bands="fine", temp=0.09,
        curve=filmic(exposure=0.14, contrast=0.9, toe=0.02, shoulder=0.985), curve_chroma=0.35,
        sat=-0.04,
        hsl=dict(red=(6, -0.12, 0.0), skin=(2, -0.03, 0.05), amber=(0, -0.02, 0.03), yellow=(-6, -0.12, 0.02),
                 lime=(-10, -0.3, 0.03), green=(-12, -0.32, 0.03), teal=(-6, -0.25, 0.0), cyan=(-2, -0.12, 0.03),
                 sky=(-8, -0.18, 0.04), blue=(-10, -0.22, 0.03), purple=(-6, -0.2, 0.0), magenta=(0, -0.15, 0.0)),
        hsl_dark=dict(green=(-4, -0.08, 0.02)),
        chroma_cap=dict(sky=0.11, blue=0.13, green=0.08, lime=0.09, teal=0.08),
        hl_rolloff=(0.8, 0.45),
        split=dict(shadows=(215, 0.012), highlights=(75, 0.012)),
        selective=dict(whites=(0, 0, -0.02, 0)), clean=0.9,
    ),
    # Consumer gold: golden highlights, saturated yellows and reds, greens pulled toward yellow,
    # muted teal blues, warm brown shadows
    "gold_memory": dict(
        bands="fine", temp=0.18, tint=0.02,
        curve=filmic(exposure=0.05, contrast=1.06, toe=0.022, shoulder=0.988), curve_chroma=0.45,
        sat=0.04,
        hsl=dict(red=(2, 0.06, 0.0), skin=(2, -0.02, 0.02), amber=(0, 0.1, 0.01), yellow=(-4, 0.18, 0.01),
                 lime=(-12, -0.04, 0.01), green=(-10, -0.12, 0.0), teal=(0, -0.2, 0.0), cyan=(10, -0.06, 0.0),
                 sky=(8, -0.08, 0.03), blue=(-6, -0.15, 0.0), purple=(0, -0.15, 0.0), magenta=(0, -0.08, 0.0)),
        chroma_cap=dict(yellow=0.18, amber=0.17, red=0.19, sky=0.12, blue=0.14, skin=0.12),
        hl_rolloff=(0.8, 0.4),
        split=dict(shadows=(45, 0.012), highlights=(68, 0.02)),
        rgb_curves=dict(b=[(0, 0), (0.25, 0.25), (0.5, 0.497), (0.75, 0.742), (1, 0.99)]),
        clean=0.75, allow=dict(lightgray_chroma=5.0, white_level=0.93, black_level=0.055),
    ),
    # Cinema: teal shadows, warm skin and highlights, greens toward teal, firm contrast
    "cinema400": dict(
        bands="fine",
        curve=filmic(exposure=-0.03, contrast=1.18, toe=0.012, shoulder=0.98), curve_chroma=0.4,
        sat=-0.04,
        hsl=dict(red=(4, -0.06, 0.0), skin=(-2, 0.06, 0.01), amber=(-2, 0.02, 0.0), yellow=(-8, -0.2, 0.0),
                 lime=(8, -0.25, -0.01), green=(14, -0.2, -0.02), teal=(0, 0.02, -0.02), cyan=(0, 0.0, -0.01),
                 sky=(-6, -0.06, -0.02), blue=(-4, -0.1, -0.02), purple=(-8, -0.2, 0.0), magenta=(-6, -0.2, 0.0)),
        hsl_dark=dict(teal=(0, 0.06, -0.02), blue=(-6, 0.0, -0.02)),
        chroma_cap=dict(teal=0.11, cyan=0.12, sky=0.12, blue=0.14, green=0.09),
        hl_rolloff=(0.82, 0.4),
        split=dict(shadows=(205, 0.03), highlights=(65, 0.016)),
        skin_protect=0.9, clean=0.88, allow=dict(white_level=0.94, skin_chroma=(0.6, 1.7)),
    ),
    # Fine-grain saturated: deep blues, rich reds, clean whites, calm skin
    "vivid100": dict(
        bands="fine", temp=0.05,
        curve=filmic(contrast=1.14, toe=0.006, shoulder=0.995), curve_chroma=0.5,
        sat=0.18, vibrance=0.08,
        hsl=dict(red=(-2, 0.1, -0.01), skin=(0, -0.08, 0.01), amber=(0, 0.08, 0.0), yellow=(0, 0.14, 0.0),
                 lime=(0, 0.1, 0.0), green=(2, 0.12, -0.02), teal=(0, 0.08, -0.02), cyan=(0, 0.1, -0.02),
                 sky=(2, 0.16, -0.04), blue=(0, 0.12, -0.08), purple=(0, 0.04, 0.0), magenta=(0, 0.0, 0.0)),
        hsl_dark=dict(blue=(0, 0.04, -0.03), green=(4, 0.02, -0.02)),
        chroma_cap=dict(red=0.2, yellow=0.18, lime=0.16, green=0.16, teal=0.14, cyan=0.15, sky=0.17, blue=0.21, skin=0.12),
        hl_rolloff=(0.86, 0.4),
        split=dict(highlights=(60, 0.008), shadows=(30, 0.004)),
        clean=0.92,
    ),
    # Tokyo airy cool: bright, cool-cyan air, blue-lifted blacks, teal-leaning greens, clean skin
    "tokyo_blue": dict(
        bands="fine", temp=-0.09, tint=-0.015,
        curve=filmic(exposure=0.15, contrast=0.92, toe=0.014, shoulder=0.99), curve_chroma=0.35,
        skin_protect=0.95,
        sat=-0.1,
        hsl=dict(red=(0, -0.12, 0.0), skin=(5, 0.1, 0.04), amber=(0, -0.08, 0.02), yellow=(0, -0.15, 0.02),
                 lime=(8, -0.2, 0.03), green=(10, -0.15, 0.02), teal=(0, 0.0, 0.02), cyan=(0, 0.1, 0.03),
                 sky=(-6, 0.06, 0.05), blue=(0, -0.05, 0.03), purple=(0, -0.15, 0.0), magenta=(0, -0.12, 0.0)),
        chroma_cap=dict(cyan=0.12, sky=0.13, blue=0.14, green=0.1, teal=0.1),
        hl_rolloff=(0.82, 0.45),
        split=dict(shadows=(215, 0.02), mids=(210, 0.006)),
        allow=dict(black_level=0.065),
        rgb_curves=dict(b=[(0, 0.014), (0.25, 0.262), (0.5, 0.51), (0.75, 0.755), (1, 1)]),
        clean=0.9,
    ),
    # Kyoto pastel: very soft, mint greens, powdery sky, faint pink lift in the shadows
    "kyoto_soft": dict(
        bands="fine", temp=0.02,
        curve=filmic(exposure=0.2, contrast=0.82, toe=0.034, shoulder=0.985), curve_chroma=0.3,
        sat=-0.16,
        hsl=dict(red=(4, -0.15, 0.02), skin=(0, -0.04, 0.05), amber=(0, -0.1, 0.03), yellow=(-2, -0.2, 0.04),
                 lime=(6, -0.25, 0.06), green=(8, -0.25, 0.06), teal=(0, -0.12, 0.04), cyan=(0, 0.0, 0.05),
                 sky=(-4, -0.1, 0.06), blue=(0, -0.2, 0.04), purple=(0, -0.1, 0.03), magenta=(0, 0.0, 0.02)),
        chroma_cap=dict(sky=0.1, blue=0.12, green=0.08, lime=0.08),
        hl_rolloff=(0.78, 0.5),
        split=dict(shadows=(350, 0.008), highlights=(95, 0.004)),
        clean=0.92, allow=dict(black_level=0.065),
    ),
    # Fresh summer: lively yellow-greens, cyan sky, bright, green-teal shadows like Fuji negative
    "summer_green": dict(
        bands="fine", temp=0.02,
        curve=filmic(exposure=0.08, contrast=1.02, toe=0.016, shoulder=0.99), curve_chroma=0.4,
        sat=0.04,
        hsl=dict(red=(0, -0.04, 0.0), skin=(0, -0.04, 0.03), amber=(0, -0.02, 0.01), yellow=(-2, -0.04, 0.02),
                 lime=(6, 0.16, 0.05), green=(4, 0.14, 0.03), teal=(0, 0.06, 0.0), cyan=(0, 0.1, 0.02),
                 sky=(-4, 0.06, 0.04), blue=(0, 0.0, 0.0), purple=(0, -0.08, 0.0), magenta=(0, -0.06, 0.0)),
        hsl_dark=dict(green=(4, 0.0, -0.01)),
        chroma_cap=dict(lime=0.15, green=0.15, teal=0.12, cyan=0.13, sky=0.14, blue=0.17),
        hl_rolloff=(0.84, 0.45),
        split=dict(shadows=(178, 0.006), mids=(120, 0.004), highlights=(95, 0.008)),
        clean=0.9,
    ),
    # Early digicam: cool, punchy, magenta-leaning reds and blues, hard highlights, blue shadows
    "ccd2000": dict(
        bands="fine", temp=-0.12, tint=0.03,
        curve=filmic(contrast=1.2, toe=0.004, shoulder=0.995), curve_chroma=0.5,
        sat=0.1,
        hsl=dict(red=(-4, 0.12, 0.0), skin=(-2, 0.02, 0.02), amber=(0, 0.0, 0.0), yellow=(4, -0.05, 0.0),
                 lime=(6, 0.0, 0.0), green=(8, 0.04, 0.0), teal=(0, 0.06, 0.0), cyan=(0, 0.08, 0.0),
                 sky=(4, 0.08, 0.0), blue=(6, 0.1, -0.02), purple=(0, 0.06, 0.0), magenta=(0, 0.04, 0.0)),
        chroma_cap=dict(red=0.21, sky=0.16, blue=0.2, green=0.15, skin=0.13),
        split=dict(shadows=(250, 0.012)),
        clean=0.92,
    ),
    # Direct flash at night: hard contrast, deep blacks, bright warm skin, punchy colour
    "flash_diary": dict(
        bands="fine", temp=0.04,
        curve=filmic(exposure=0.03, contrast=1.3, toe=0.006, shoulder=0.99), curve_chroma=0.45,
        sat=0.1,
        hsl=dict(red=(0, 0.08, 0.0), skin=(0, 0.0, 0.04), amber=(0, 0.04, 0.02), yellow=(-2, 0.06, 0.0),
                 green=(0, 0.0, 0.0), sky=(0, 0.04, 0.0), blue=(-4, 0.04, -0.03), magenta=(0, 0.04, 0.0)),
        chroma_cap=dict(red=0.21, skin=0.13),
        split=dict(highlights=(70, 0.008), shadows=(250, 0.006)),
        clean=0.9,
    ),
    # Compact chrome: crisp, cool shadows and warm highlights, deep blue, controlled greens
    "compact_chrome": dict(
        bands="fine",
        curve=filmic(contrast=1.16, toe=0.008, shoulder=0.995), curve_chroma=0.45,
        sat=0.06,
        hsl=dict(red=(0, 0.06, 0.0), skin=(0, -0.02, 0.02), amber=(0, 0.02, 0.01), yellow=(-2, 0.0, 0.0),
                 lime=(4, -0.08, 0.0), green=(6, -0.1, -0.02), teal=(0, 0.04, 0.0), cyan=(0, 0.06, 0.0),
                 sky=(0, 0.06, -0.02), blue=(0, 0.06, -0.05), purple=(0, -0.04, 0.0), magenta=(0, -0.04, 0.0)),
        chroma_cap=dict(sky=0.15, blue=0.19, green=0.13),
        hl_rolloff=(0.86, 0.35),
        split=dict(shadows=(235, 0.02), highlights=(65, 0.016)),
        clean=0.9,
    ),
    # Night cinema: teal shadows, warm orange-red highlights and lights, green to teal
    "night_bloom": dict(
        bands="fine",
        curve=filmic(exposure=0.05, contrast=1.1, toe=0.016, shoulder=0.985), curve_chroma=0.4,
        sat=0.02,
        hsl=dict(red=(0, 0.06, 0.0), skin=(0, -0.02, 0.02), amber=(0, 0.06, 0.02), yellow=(-6, -0.06, 0.0),
                 lime=(10, -0.2, 0.0), green=(12, -0.2, 0.0), teal=(0, 0.04, 0.0), cyan=(0, 0.04, 0.0),
                 sky=(-4, 0.0, 0.0), blue=(-6, 0.04, 0.0), magenta=(0, 0.04, 0.0)),
        chroma_cap=dict(teal=0.12, green=0.1, red=0.2),
        split=dict(shadows=(195, 0.025), highlights=(58, 0.016)),
        clean=0.88,
    ),
    # Faded dream: lifted warm-pink blacks, soft contrast, muted greens and blues, rosy highlights
    "dream1998": dict(
        bands="fine", temp=0.04, tint=0.04,
        curve=filmic(exposure=0.1, contrast=0.86, toe=0.034, shoulder=0.97), curve_chroma=0.3,
        sat=-0.15,
        hsl=dict(red=(4, -0.1, 0.0), skin=(0, -0.04, 0.03), amber=(0, -0.04, 0.02), yellow=(-4, -0.15, 0.02),
                 lime=(-10, -0.3, 0.02), green=(-10, -0.3, 0.02), teal=(-4, -0.25, 0.0), cyan=(-4, -0.15, 0.02),
                 sky=(-6, -0.2, 0.03), blue=(-6, -0.2, 0.02), purple=(0, -0.05, 0.0), magenta=(0, 0.04, 0.0)),
        chroma_cap=dict(sky=0.1, blue=0.12, green=0.08),
        hl_rolloff=(0.78, 0.5),
        split=dict(shadows=(340, 0.012), highlights=(60, 0.012)),
        clean=0.85, allow=dict(white_level=0.925, black_level=0.072),
    ),
}


# How much each look's mood (grading, tone curve, white balance, per-hue colour) is strengthened,
# chosen by eye on real photos (preview/v3) and checked against the clean-picture guards.
STRENGTH = dict(mellow400=2.2, gold_memory=2.0, cinema400=2.0, vivid100=1.8, tokyo_blue=2.0,
                kyoto_soft=1.6, summer_green=2.4, ccd2000=2.0, flash_diary=2.0, compact_chrome=2.0,
                night_bloom=2.0, dream1998=2.0)
DAYLIGHT_STRENGTH = dict(muted_chrome=1.3, street_negative=1.2, portrait_soft=1.8, vivid_landscape=2.6,
                         cine_daylight=1.6, nostalgic_summer=1.2, clear_daylight=2.6, summer400=1.2)
