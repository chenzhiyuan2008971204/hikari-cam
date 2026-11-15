"""Daylight Collection: eight original colour looks for outdoor daylight photography.

Japanese-camera colour philosophy, built from scratch: clean whites, skies with depth and gradient
but never fluorescent, foliage with separate yellow-green / grass / deep-green control, natural
skin. Every look is its own recipe for the OKLab engine in look.py:

  colour management   sRGB in -> linear light -> OKLab working space -> gamut map -> sRGB out
  tone mapping        filmic() curve: 18 % grey slope, soft toe, highlight shoulder (no clipping)
  white balance       luminance-preserving gains in linear light
  hsl                 12 hue bands (bands="fine"): red, skin, amber, yellow, lime (yellow-green),
                      green (grass), teal (blue-green), cyan, sky, blue, purple, magenta
  hsl_dark            a second table for dark tones only (deep foliage, deep sky)
  chroma_cap          per-band soft chroma ceiling: rich, never neon
  hl_rolloff          colour thins smoothly toward white: clouds and white walls stay clean
  split               3-way colour grading (shadows / mids / highlights)
  selective           CMYK-style selective colour per family (reds .. whites .. blacks)
  rgb_curves          independent R / G / B curves (film crossover)
  clean               near-white neutrality

No manufacturer LUTs, code or assets are used; names are Hikari's own.
"""
from filmkit import filmic, fx

S = "daylight"

DAYLIGHT = {
    "muted_chrome": dict(
        name="Muted Chrome", zh="霧鉻", series=S,
        note="紀實街拍：低飽和、硬朗的影調、深而沉穩的藍天，綠色收斂成橄欖色但不變青，白色乾淨。",
        colour=dict(
            bands="fine",
            temp=0.01,
            curve=filmic(exposure=-0.02, contrast=1.2, toe=0.012, shoulder=0.985),
            curve_chroma=0.35,
            sat=-0.12,
            hsl=dict(red=(-2, -0.06, -0.03), skin=(0, -0.06, 0.0), amber=(-2, -0.16, 0.0),
                     yellow=(-4, -0.26, 0.0), lime=(-6, -0.30, -0.01), green=(-4, -0.28, -0.03),
                     teal=(0, -0.22, -0.02), cyan=(4, -0.10, -0.02), sky=(4, -0.02, -0.05),
                     blue=(0, 0.02, -0.07), purple=(-4, -0.18, 0.0), magenta=(0, -0.2, 0.0)),
            hsl_dark=dict(green=(-5, -0.04, -0.02), lime=(-2, -0.04, -0.01), teal=(-6, 0.0, 0.0), blue=(0, 0.04, -0.03),
                          sky=(0, 0.02, -0.02)),
            chroma_cap=dict(sky=0.13, blue=0.15, cyan=0.11, green=0.09, lime=0.09, yellow=0.11),
            hl_rolloff=(0.84, 0.45),
            split=dict(shadows=(240, 0.004), highlights=(75, 0.005)),
            selective=dict(whites=(0, 0, -0.02, 0), blacks=(0.02, 0, 0, 0.02)),
            clean=0.9,
        ),
        fx=fx(grain=0.22, grain_size=0.00112, grain_chroma=0.12, grain_rough=0.4,
              halation=0.03, hal_threshold=0.9, bloom=0.0, vignette=0.12),
    ),
    "street_negative": dict(
        name="Street Negative", zh="街頭負片", series=S,
        note="街頭負片：硬反差、偏青的天空與冷調暗部、暖而不黃的高光、深沉的紅，黃綠轉向橄欖但層次分明。",
        colour=dict(
            bands="fine",
            curve=filmic(exposure=-0.03, contrast=1.24, toe=0.014, shoulder=0.98),
            curve_chroma=0.4,
            sat=-0.08,
            hsl=dict(red=(-4, 0.02, -0.04), skin=(-2, -0.08, 0.0), amber=(-2, -0.12, -0.01),
                     yellow=(-6, -0.16, -0.01), lime=(-8, -0.20, -0.02), green=(2, -0.18, -0.03),
                     teal=(0, -0.10, -0.02), cyan=(6, 0.04, 0.0), sky=(-6, 0.0, 0.0),
                     blue=(-6, -0.06, 0.02), purple=(-6, -0.2, 0.0), magenta=(-4, -0.12, 0.0)),
            hsl_dark=dict(green=(-4, -0.04, -0.02), teal=(-4, 0.0, 0.0), red=(0, 0.04, -0.02)),
            chroma_cap=dict(sky=0.12, cyan=0.11, blue=0.14, green=0.09, lime=0.10),
            hl_rolloff=(0.84, 0.4),
            split=dict(shadows=(205, 0.011), highlights=(68, 0.012)),
            rgb_curves=dict(
                r=[(0, 0), (0.25, 0.235), (0.5, 0.5), (0.75, 0.765), (1, 1)],
                g=[(0, 0), (0.25, 0.245), (0.5, 0.5), (0.75, 0.752), (1, 1)],
                b=[(0, 0), (0.25, 0.255), (0.5, 0.5), (0.75, 0.738), (1, 1)],
            ),
            selective=dict(whites=(0, 0, -0.03, 0)),
            clean=0.9,
        ),
        fx=fx(grain=0.3, grain_size=0.00128, grain_chroma=0.18, grain_rough=0.45,
              halation=0.06, hal_threshold=0.86, vignette=0.15),
    ),
    "portrait_soft": dict(
        name="Portrait Soft", zh="柔膚人像", series=S,
        note="戶外人像：柔和反差、乾淨明亮的膚色（不橘不紅）、柔和的綠與藍，高光細膩地過渡到白。",
        colour=dict(
            bands="fine",
            temp=0.02,
            curve=filmic(exposure=0.13, contrast=0.84, toe=0.02, shoulder=0.988),
            sat=-0.05,
            hsl=dict(red=(3, -0.12, 0.01), skin=(1, -0.08, 0.04), amber=(-1, -0.08, 0.03),
                     yellow=(-2, -0.14, 0.04), lime=(-2, -0.2, 0.05), green=(0, -0.2, 0.05),
                     teal=(0, -0.18, 0.03), cyan=(0, -0.1, 0.04), sky=(0, -0.08, 0.05),
                     blue=(0, -0.08, 0.03), purple=(0, -0.10, 0.0), magenta=(0, -0.12, 0.0)),
            hsl_dark=dict(green=(0, -0.06, 0.04)),
            chroma_cap=dict(skin=0.10, red=0.15, sky=0.12, blue=0.14, green=0.10, lime=0.10),
            hl_rolloff=(0.8, 0.5),
            split=dict(shadows=(235, 0.004), highlights=(62, 0.005)),
            selective=dict(reds=(0.02, 0, 0, 0)),
            clean=0.92,
        ),
        fx=fx(grain=0.16, grain_size=0.00096, grain_chroma=0.12, grain_rough=0.3,
              halation=0.04, hal_threshold=0.88, bloom=0.04, bloom_threshold=0.82, vignette=0.08),
    ),
    "vivid_landscape": dict(
        name="Vivid Landscape", zh="鮮明風景", series=S,
        note="風景：飽和而受控、天空由淺到深的漸層、黃綠／草綠／深綠層層分開，雲朵保留細節。",
        colour=dict(
            bands="fine",
            curve=filmic(exposure=-0.02, contrast=1.12, toe=0.01, shoulder=0.99),
            curve_chroma=0.45,
            sat=0.08, vibrance=0.08,
            hsl=dict(red=(-1, -0.04, 0.0), skin=(0, -0.08, 0.01), amber=(0, 0.02, 0.0),
                     yellow=(-2, 0.10, 0.01), lime=(-3, 0.12, 0.0), green=(0, 0.15, -0.02),
                     teal=(2, 0.08, -0.02), cyan=(0, 0.06, -0.01), sky=(2, 0.12, -0.03),
                     blue=(0, 0.08, -0.06), purple=(0, -0.04, 0.0), magenta=(0, -0.04, 0.0)),
            hsl_dark=dict(green=(5, 0.04, -0.03), lime=(2, 0.0, -0.02), blue=(0, 0.04, -0.03)),
            chroma_cap=dict(yellow=0.17, lime=0.15, green=0.15, teal=0.13, cyan=0.14,
                            sky=0.16, blue=0.2, skin=0.12),
            hl_rolloff=(0.8, 0.55),
            split=dict(shadows=(225, 0.006)),
            selective=dict(whites=(0, 0, -0.08, 0), neutrals=(0, 0, -0.02, 0)),
            clean=0.92,
        ),
        fx=fx(grain=0.12, grain_size=0.00096, grain_chroma=0.1, grain_rough=0.2,
              halation=0.03, hal_threshold=0.9, vignette=0.1),
    ),
    "cine_daylight": dict(
        name="Cine Daylight", zh="電影日光", series=S,
        note="電影感日光：低反差、低飽和但膚色有血色，暗部微青、高光柔和，畫面寬容度高、適合後製。",
        colour=dict(
            bands="fine",
            curve=filmic(exposure=0.02, contrast=0.86, toe=0.024, shoulder=0.988),
            sat=-0.2,
            hsl=dict(red=(0, -0.10, 0.0), skin=(0, 0.04, 0.02), amber=(0, -0.08, 0.0),
                     yellow=(-2, -0.16, 0.0), lime=(-2, -0.18, 0.0), green=(4, -0.2, -0.01),
                     teal=(0, -0.18, 0.0), cyan=(0, -0.08, 0.0), sky=(0, -0.10, 0.0),
                     blue=(0, -0.14, 0.0), purple=(0, -0.2, 0.0), magenta=(0, -0.2, 0.0)),
            hsl_dark=dict(green=(2, -0.06, 0.0)),
            chroma_cap=dict(sky=0.11, blue=0.13, green=0.08, lime=0.09),
            hl_rolloff=(0.8, 0.5),
            split=dict(shadows=(205, 0.014), mids=(215, 0.002), highlights=(70, 0.006)),
            clean=0.9,
        ),
        fx=fx(grain=0.2, grain_size=0.00112, grain_chroma=0.12, grain_rough=0.35,
              halation=0.05, hal_threshold=0.86, bloom=0.03, vignette=0.1),
    ),
    "nostalgic_summer": dict(
        name="Nostalgic Summer", zh="懷舊夏日", series=S,
        note="懷舊夏日：琥珀色的陽光、柔和的天空、溫暖的黃綠，但白色衣服與雲依然乾淨不泛黃。",
        colour=dict(
            bands="fine",
            temp=0.09,
            curve=filmic(exposure=0.05, contrast=0.98, toe=0.028, shoulder=0.985),
            sat=-0.04,
            hsl=dict(red=(2, -0.05, 0.0), skin=(0, -0.06, 0.02), amber=(0, 0.06, 0.02),
                     yellow=(-6, 0.04, 0.02), lime=(-8, -0.08, 0.03), green=(-4, -0.12, 0.02),
                     teal=(0, -0.16, 0.0), cyan=(4, -0.08, 0.02), sky=(0, -0.08, 0.03),
                     blue=(0, -0.10, 0.02), purple=(0, -0.12, 0.0), magenta=(0, -0.08, 0.0)),
            hsl_dark=dict(green=(-2, -0.06, 0.01)),
            chroma_cap=dict(amber=0.15, yellow=0.15, lime=0.12, green=0.11, sky=0.12, blue=0.15),
            hl_rolloff=(0.76, 0.6),
            split=dict(shadows=(205, 0.008), mids=(70, 0.004), highlights=(78, 0.018)),
            selective=dict(whites=(0, 0, -0.09, 0), neutrals=(0, 0, -0.01, 0)),
            clean=0.92,
        ),
        fx=fx(grain=0.3, grain_size=0.00128, grain_chroma=0.2, grain_rough=0.4,
              halation=0.08, hal_threshold=0.84, bloom=0.06, bloom_threshold=0.8, vignette=0.15),
    ),
    "clear_daylight": dict(
        name="Clear Daylight", zh="晴透日光", series=S,
        note="晴透日光：明亮通透、略冷的空氣感、乾淨的天空與純白，顏色自然清爽。",
        colour=dict(
            bands="fine",
            temp=-0.07,
            curve=filmic(exposure=0.11, contrast=1.06, toe=0.006, shoulder=0.995),
            sat=0.04,
            hsl=dict(red=(0, -0.04, 0.0), skin=(0, -0.04, 0.02), amber=(0, -0.04, 0.01),
                     yellow=(0, -0.08, 0.03), lime=(3, 0.04, 0.04), green=(0, 0.06, 0.02),
                     teal=(0, 0.02, 0.0), cyan=(-2, 0.1, 0.04), sky=(-3, 0.1, 0.04),
                     blue=(0, 0.04, 0.0), purple=(0, -0.04, 0.0), magenta=(0, -0.04, 0.0)),
            chroma_cap=dict(cyan=0.13, sky=0.15, blue=0.18, green=0.13, lime=0.13),
            hl_rolloff=(0.88, 0.5),
            split=dict(shadows=(240, 0.006)),
            selective=dict(whites=(0, 0, -0.04, 0)),
            clean=0.95,
        ),
        fx=fx(grain=0.1, grain_size=0.00096, grain_chroma=0.08, grain_rough=0.2,
              halation=0.02, hal_threshold=0.92, vignette=0.06),
    ),
    "summer400": dict(
        name="Summer 400", zh="夏日 400", series=S,
        note="夏天的彩色負片：溫暖飽滿的紅黃、青藍色的天空、明亮的綠，顆粒感明顯但膚色依然自然。",
        colour=dict(
            bands="fine",
            temp=0.1,
            curve=filmic(exposure=0.05, contrast=1.08, toe=0.016, shoulder=0.99),
            sat=0.09,
            hsl=dict(red=(0, 0.04, 0.0), skin=(1, -0.17, 0.02), amber=(0, 0.03, 0.01),
                     yellow=(-3, 0.14, 0.02), lime=(-4, 0.08, 0.01), green=(0, 0.04, -0.01),
                     teal=(0, -0.06, 0.0), cyan=(4, 0.08, 0.0), sky=(-9, 0.06, 0.01),
                     blue=(-6, 0.0, 0.0), purple=(0, -0.08, 0.0), magenta=(0, -0.06, 0.0)),
            hsl_dark=dict(green=(2, 0.0, -0.01)),
            chroma_cap=dict(red=0.19, amber=0.16, yellow=0.17, lime=0.14, green=0.14,
                            cyan=0.13, sky=0.14, blue=0.17, skin=0.12),
            hl_rolloff=(0.78, 0.6),
            split=dict(shadows=(215, 0.008), highlights=(75, 0.014)),
            rgb_curves=dict(
                r=[(0, 0), (0.25, 0.245), (0.5, 0.5), (0.75, 0.758), (1, 1)],
                b=[(0, 0), (0.25, 0.256), (0.5, 0.5), (0.75, 0.744), (1, 1)],
            ),
            selective=dict(whites=(0, 0, -0.09, 0), neutrals=(0, 0, -0.02, 0)),
            clean=0.92,
        ),
        fx=fx(grain=0.34, grain_size=0.00136, grain_chroma=0.22, grain_rough=0.45,
              halation=0.08, hal_threshold=0.84, bloom=0.05, vignette=0.14),
    ),
}

DAYLIGHT_SHIP = list(DAYLIGHT)
