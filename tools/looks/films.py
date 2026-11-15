"""The twelve Hikari film looks.

Each look has two halves:
  colour  -> a look.py recipe (OKLab engine), baked into a 33^3 LUT: filmic tone mapping, tone and
             RGB curves, per-hue HSL, saturation, 3-way colour grading / split toning, white
             balance, black point, highlight roll-off, gamut mapping
  fx      -> the engine's spatial effects, run on the GPU at any resolution: grain, halation,
             bloom, diffusion, lens and defects (engine/src/main/assets/shaders/composite.frag)

All colour is original: designed from photographic intent (what a portrait negative does to skin,
what a direct flash does to a night scene), not copied from any manufacturer's LUT.

Spatial units: fractions of the image's short side.
"""
import numpy as np

from colorlib import linear_to_srgb


from filmkit import Curve, filmic, fx  # noqa: F401


FILMS = {
    # ───────────── Film ─────────────
    "mellow400": dict(
        name="Mellow 400", zh="柔光 400", series="film",
        note="人像負片：明亮奶油色的膚色、收斂的橄欖綠、柔和偏青的天空，暖高光與冷暗部，柔和反差。",
        colour=dict(
            temp=0.06,
            curve=filmic(exposure=0.08, contrast=0.88, toe=0.02, shoulder=0.985),
            sat=-0.06,
            hsl=dict(red=(4, -0.14, 0.0), orange=(2, -0.02, 0.05), yellow=(-6, -0.08, 0.03),
                     green=(-10, -0.32, 0.04), aqua=(-6, -0.22, 0.0), blue=(-8, -0.18, 0.03)),
            sat_highlights=-0.04,
            split=dict(shadows=(205, 0.006), highlights=(70, 0.009)),
        ),
        fx=fx(grain=0.32, grain_size=0.00112, grain_chroma=0.22, grain_rough=0.35,
              halation=0.08, hal_threshold=0.86, bloom=0.07, mist=0.04,
              vignette=0.14, aberration=0.1, edge_soft=0.05),
    ),
    "gold_memory": dict(
        name="Gold Memory", zh="金色記憶", series="film",
        note="金色記憶：金黃溫暖的高光、飽滿的黃與紅、綠色偏向黃綠、帶棕色的暗部，像夏天的消費級負片。",
        colour=dict(
            temp=0.24,
            curve=filmic(exposure=0.04, contrast=1.04, toe=0.018, shoulder=0.99),
            sat=0.03, vibrance=0.03,
            hsl=dict(red=(-2, -0.04, 0.0), orange=(0, -0.14, 0.04), yellow=(-4, 0.20, 0.03),
                     green=(-12, -0.10, 0.0), aqua=(0, -0.12, 0.0), blue=(-4, -0.10, -0.02)),
            split=dict(shadows=(40, 0.008), highlights=(82, 0.026)),
        ),
        fx=fx(grain=0.42, grain_size=0.00144, grain_chroma=0.32, grain_rough=0.5,
              halation=0.12, bloom=0.1, vignette=0.22, aberration=0.12),
    ),
    "cinema400": dict(
        name="Cinema 400", zh="電影 400", series="film",
        note="電影感：青色暗部、暖色膚色與高光（青橙對比），綠色偏青，有力的反差與紅色光暈。",
        colour=dict(
            curve=filmic(exposure=-0.04, contrast=1.24, toe=0.014, shoulder=0.975),
            sat=-0.06,
            hsl=dict(red=(-2, -0.05, 0.0), orange=(0, 0.04, 0.02), yellow=(-6, -0.10, 0.0),
                     green=(10, -0.22, -0.02), aqua=(0, -0.05, -0.02), blue=(-8, -0.10, -0.02)),
            split=dict(shadows=(198, 0.018), highlights=(65, 0.011)),
        ),
        fx=fx(grain=0.3, grain_size=0.00128, grain_chroma=0.2, grain_rough=0.45,
              halation=0.25, hal_threshold=0.78, hal_radius=0.012, bloom=0.12,
              vignette=0.25, aberration=0.15),
    ),
    "vivid100": dict(
        name="Vivid 100", zh="鮮明 100", series="film",
        note="鮮明細顆粒：溫暖濃郁的紅、深邃的藍、清晰反差，白色乾淨、膚色穩定。",
        colour=dict(
            curve=filmic(contrast=1.12, toe=0.008, shoulder=0.995),
            sat=0.12, vibrance=0.06,
            hsl=dict(red=(-2, -0.12, 0.0), orange=(0, -0.14, 0.02), yellow=(0, 0.15, 0.0),
                     green=(-4, 0.12, -0.02), aqua=(0, 0.15, 0.0), blue=(-6, -0.04, -0.03),
                     purple=(0, -0.05, 0.0), magenta=(0, -0.04, 0.0)),
        ),
        fx=fx(grain=0.14, grain_size=0.00096, grain_chroma=0.15, grain_rough=0.2,
              halation=0.05, bloom=0.04, vignette=0.1),
    ),

    # ───────────── Japanese ─────────────
    "tokyo_blue": dict(
        name="Tokyo Blue", zh="東京藍", series="japanese",
        note="東京藍：明亮冷調的空氣感、偏藍的淡黑、青綠色調的植物，膚色乾淨。",
        colour=dict(
            temp=-0.08,
            curve=filmic(exposure=0.06, contrast=0.98, toe=0.02, shoulder=0.99),
            sat=-0.04,
            hsl=dict(orange=(0, 0.0, 0.03), red=(0, -0.05, 0.0), yellow=(-4, -0.04, 0.02),
                     green=(12, -0.15, 0.0), aqua=(0, 0.10, 0.02), blue=(-10, 0.12, 0.04)),
            split=dict(shadows=(212, 0.022), mids=(220, 0.004)),
        ),
        fx=fx(grain=0.24, grain_size=0.00112, grain_chroma=0.18, halation=0.06, bloom=0.06, vignette=0.12),
    ),
    "kyoto_soft": dict(
        name="Kyoto Soft", zh="京都柔", series="japanese",
        note="京都柔：非常柔和的粉彩、薄荷綠、粉霧天空，暗部帶一點淡粉，適合日常與人像。",
        colour=dict(
            temp=0.02,
            curve=filmic(exposure=0.12, contrast=0.82, toe=0.03, shoulder=0.98),
            sat=-0.15,
            hsl=dict(red=(4, -0.10, 0.02), orange=(0, 0.04, 0.04), yellow=(-4, -0.18, 0.05),
                     green=(-6, -0.22, 0.06), aqua=(0, -0.15, 0.04), blue=(-6, -0.20, 0.05)),
            split=dict(shadows=(260, 0.006), highlights=(60, 0.006)),
        ),
        fx=fx(grain=0.2, grain_size=0.00112, grain_chroma=0.15, soft_focus=0.1, mist=0.08,
              diffusion_radius=0.006, vignette=0.08, vignette_soft=0.8),
    ),
    "summer_green": dict(
        name="Summer Green", zh="夏日綠", series="japanese",
        note="夏日綠：清新鮮活的黃綠與草綠、青藍天空、明亮通透，暗部帶一點富士式的青綠。",
        colour=dict(
            temp=0.05,
            curve=filmic(exposure=0.05, contrast=1.0, toe=0.018, shoulder=0.99),
            sat=0.04,
            hsl=dict(green=(-4, 0.08, -0.03), yellow=(-6, 0.04, 0.03), aqua=(4, -0.06, 0.0),
                     orange=(0, 0.0, 0.03), blue=(-4, 0.05, 0.02)),
            sat_highlights=-0.1,
            split=dict(highlights=(85, 0.012), shadows=(170, 0.006)),
        ),
        fx=fx(grain=0.24, grain_size=0.00112, grain_chroma=0.18, bloom=0.1, bloom_threshold=0.74,
              vignette=0.1),
    ),

    # ───────────── CCD / digital ─────────────
    "ccd2000": dict(
        name="CCD 2000", zh="CCD 2000", series="ccd",
        note="CCD 2000：早期數位相機的冷調與高飽和，偏洋紅的紅與藍、硬朗的高光、細小色彩雜訊。",
        colour=dict(
            temp=-0.14,
            curve=filmic(contrast=1.14, toe=0.006, shoulder=0.99),
            sat=0.05,
            hsl=dict(red=(-4, 0.10, 0.0), blue=(-6, 0.10, 0.0), green=(6, 0.05, 0.0), aqua=(0, 0.06, 0.0)),
            split=dict(shadows=(220, 0.008)),
        ),
        fx=fx(grain=0.18, grain_size=0.0006, grain_chroma=0.6, grain_rough=0.0,
              bloom=0.03, vignette=0.05, aberration=0.25),
    ),
    "flash_diary": dict(
        name="Flash Diary", zh="閃光日記", series="ccd",
        note="閃光日記：直打閃光的強反差、深黑、明亮偏暖的膚色與鮮豔顏色。",
        colour=dict(
            temp=0.08,
            curve=filmic(exposure=0.02, contrast=1.28, toe=0.01, shoulder=0.99),
            sat=0.1,
            hsl=dict(orange=(0, 0.04, 0.03), red=(-2, 0.06, 0.0), yellow=(-4, 0.06, 0.0),
                     blue=(-6, 0.04, -0.03)),
            split=dict(highlights=(70, 0.008)),
        ),
        fx=fx(grain=0.38, grain_size=0.00144, grain_chroma=0.3, bloom=0.12, bloom_threshold=0.7,
              halation=0.05, vignette=0.35, vignette_soft=0.3, uneven=0.3),
    ),
    "compact_chrome": dict(
        name="Compact Chrome", zh="隨身機鉻", series="ccd",
        note="隨身機鉻：清脆銳利、冷暗部暖高光、深藍天空、收斂的綠。",
        colour=dict(
            curve=filmic(contrast=1.14, toe=0.01, shoulder=0.995),
            sat=0.08,
            hsl=dict(red=(0, -0.05, 0.0), orange=(0, 0.0, 0.02), green=(6, -0.10, -0.02),
                     blue=(-4, 0.05, -0.03), aqua=(0, 0.05, 0.0)),
            split=dict(shadows=(240, 0.017), highlights=(70, 0.015)),
        ),
        fx=fx(grain=0.12, grain_size=0.0008, grain_chroma=0.3, grain_rough=0.1,
              vignette=0.15, aberration=0.1),
    ),

    # ───────────── Special ─────────────
    "night_bloom": dict(
        name="Night Bloom", zh="夜光", series="special",
        note="夜光：青色暗部、橙紅色的燈光與高光暈染，綠色偏青的電影夜景感。",
        colour=dict(
            curve=filmic(exposure=0.05, contrast=1.06, toe=0.02, shoulder=0.985),
            sat=0.05,
            hsl=dict(orange=(0, 0.04, 0.02), green=(10, -0.15, 0.0), blue=(-6, 0.05, 0.0)),
            split=dict(shadows=(190, 0.016), highlights=(60, 0.01)),
        ),
        fx=fx(grain=0.38, grain_size=0.00144, grain_chroma=0.3, grain_rough=0.5,
              halation=0.85, hal_threshold=0.68, hal_radius=0.015,
              bloom=0.42, bloom_threshold=0.62, bloom_radius=0.06, vignette=0.2),
    ),
    "dream1998": dict(
        name="Dream 1998", zh="夢 1998", series="special",
        note="夢 1998：褪色的暖粉色調、抬起的淡黑、柔和反差，綠與藍收斂，帶灰塵與漏光。",
        colour=dict(
            temp=0.03,
            curve=filmic(exposure=0.06, contrast=0.9, toe=0.035, shoulder=0.975),
            sat=-0.12,
            hsl=dict(red=(4, -0.10, 0.0), green=(-12, -0.25, 0.02), blue=(-8, -0.15, 0.02),
                     yellow=(-4, -0.10, 0.02), orange=(0, 0.0, 0.03)),
            split=dict(shadows=(180, 0.01), highlights=(20, 0.01)),
        ),
        fx=fx(grain=0.3, grain_size=0.00128, grain_chroma=0.25, soft_focus=0.3, mist=0.2,
              diffusion_radius=0.008, vignette=0.2, vignette_soft=0.7,
              dust=0.3, leak=0.12, drift=0.3),
    ),
}


# Shipped in the app. The other recipes stay here for later, after these four are proven.
SHIP = ["mellow400", "gold_memory", "cinema400", "vivid100", "tokyo_blue", "kyoto_soft", "summer_green", "ccd2000", "flash_diary", "compact_chrome", "night_bloom", "dream1998"]

# Daylight Collection (daylight.py), shipped first in the app
from daylight import DAYLIGHT, DAYLIGHT_SHIP  # noqa: E402

FILMS.update(DAYLIGHT)
SHIP = DAYLIGHT_SHIP + SHIP

# Film Simulation series (sims.py)
from sims import SIMS, SIM_SHIP, SIM_STRENGTH  # noqa: E402

FILMS.update(SIMS)
SHIP = SIM_SHIP + SHIP

# Colour v2: the fine-engine recipes (film_colours.py), with each look's mood strengthened
from amplify import amplify_mood  # noqa: E402
from film_colours import C as _C2, STRENGTH as _S, DAYLIGHT_STRENGTH as _SD  # noqa: E402

for _k, _lk in _C2.items():
    FILMS[_k]["colour"] = amplify_mood(_lk, _S[_k])
for _k, _kk in {**_SD, **SIM_STRENGTH}.items():
    FILMS[_k]["colour"] = amplify_mood(FILMS[_k]["colour"], _kk, clean=True)

# Grain: mostly luminance, like real colour negative (dye clouds add only a faint colour
# variation). Independent per-channel noise at the original amounts read as digital colour noise
# on skin at 100 %; CCD 2000 keeps its colour noise on purpose.
for _k, _f in FILMS.items():
    if _k != "ccd2000":
        _f["fx"]["grain"]["chroma"] = round(_f["fx"]["grain"]["chroma"] * 0.4, 3)
for _k in ("mellow400", "portrait_soft", "kyoto_soft"):
    FILMS[_k]["fx"]["grain"]["amount"] = round(FILMS[_k]["fx"]["grain"]["amount"] * 0.8, 3)
