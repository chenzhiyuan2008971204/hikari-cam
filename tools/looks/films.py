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


class Curve(list):
    """Curve points that also remember the parameters they came from (for the recipe text)."""
    params: dict = {}


def filmic(contrast=1.0, toe=0.0, shoulder=1.0, exposure=0.0, n=33):
    """Filmic tone curve as display-value points for look.py's `curve`.

    - exposure (stops) brightens or darkens with x*k / (1 + (k-1)*x) in linear light: mid tones move
      by about `exposure` stops while white stays white, so nothing clips.
    - contrast is the slope at 18% grey. The curve is f(t) = c*t / (1 + (c-1)*t) on each side of
      grey, so the slope eases to 1/c towards black and white: a soft toe and a soft shoulder
      (highlight roll-off) instead of clipping. f(0)=0 and f(1)=1, monotone for any c > 0.
    - toe lifts black (keeps shadow detail visible), shoulder is the display value white rolls to.
    """
    xs = np.linspace(0, 1, n)
    lin = np.where(xs <= 0.04045, xs / 12.92, ((xs + 0.055) / 1.055) ** 2.4)
    k = 2.0 ** exposure
    lin = lin * k / (1 + (k - 1) * lin)
    x = linear_to_srgb(lin)
    p = float(linear_to_srgb(np.array([0.18]))[0])  # 18% grey in display values
    c = contrast
    f = lambda t: c * t / (1 + (c - 1) * t)
    y = np.where(x >= p, p + (1 - p) * f(np.clip((x - p) / (1 - p), 0, 1)),
                 p - p * f(np.clip((p - x) / p, 0, 1)))
    y = toe + (shoulder - toe) * y
    out = Curve((float(a), float(b)) for a, b in zip(xs, y))
    out.params = dict(contrast=contrast, toe=toe, shoulder=shoulder, exposure=exposure)
    return out


def fx(grain=0.3, grain_size=0.00128, grain_chroma=0.25, grain_rough=0.4,
       halation=0.0, hal_threshold=0.82, hal_radius=0.012, hal_tint=(1.0, 0.32, 0.12),
       bloom=0.0, bloom_threshold=0.78, bloom_radius=0.045,
       soft_focus=0.0, mist=0.0, diffusion_radius=0.006,
       vignette=0.15, vignette_soft=0.6, aberration=0.0, edge_soft=0.0,
       dust=0.0, scratches=0.0, leak=0.0, leak_color=(1.0, 0.45, 0.18), uneven=0.0, drift=0.0):
    return dict(
        grain=dict(amount=grain, size=grain_size, chroma=grain_chroma, rough=grain_rough),
        halation=dict(amount=halation, threshold=hal_threshold, radius=hal_radius, tint=list(hal_tint)),
        bloom=dict(amount=bloom, threshold=bloom_threshold, radius=bloom_radius),
        diffusion=dict(softFocus=soft_focus, mist=mist, radius=diffusion_radius),
        lens=dict(vignette=vignette, vignetteSoft=vignette_soft, aberration=aberration, edgeSoft=edge_soft),
        defects=dict(dust=dust, scratches=scratches, leak=leak, leakColor=list(leak_color),
                     uneven=uneven, drift=drift),
    )


FILMS = {
    # ───────────── Film ─────────────
    "mellow400": dict(
        name="Mellow 400", zh="柔光 400", series="film",
        note="人像負片：柔和膚色、溫暖高光、自然低飽和、柔和反差、細緻顆粒。",
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
        note="金黃高光、暖色調，飽滿但不刺眼的顏色，像夏天的消費級彩色負片。",
        colour=dict(
            temp=0.24,
            curve=filmic(exposure=0.04, contrast=1.04, toe=0.018, shoulder=0.99),
            sat=0.06, vibrance=0.1,
            hsl=dict(red=(-2, 0.08, 0.0), orange=(0, 0.04, 0.03), yellow=(-4, 0.20, 0.03),
                     green=(-12, -0.10, 0.0), aqua=(0, -0.12, 0.0), blue=(-4, -0.10, -0.02)),
            split=dict(shadows=(40, 0.008), highlights=(82, 0.026)),
        ),
        fx=fx(grain=0.42, grain_size=0.00144, grain_chroma=0.32, grain_rough=0.5,
              halation=0.12, bloom=0.1, vignette=0.22, aberration=0.12),
    ),
    "cinema400": dict(
        name="Cinema 400", zh="電影 400", series="film",
        note="電影負片：有力的反差、深邃但保有細節的暗部、自然膚色、柔和的高光過渡與紅色光暈。",
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
        note="細顆粒高飽和正片風格：色彩分離度高、細節清晰，紅色與藍色的飽和度受控不溢出。",
        colour=dict(
            curve=filmic(contrast=1.12, toe=0.008, shoulder=0.995),
            sat=0.16, vibrance=0.14,
            hsl=dict(red=(-2, -0.06, 0.0), orange=(0, 0.05, 0.0), yellow=(0, 0.15, 0.0),
                     green=(-4, 0.12, -0.02), aqua=(0, 0.15, 0.0), blue=(-6, -0.04, -0.03),
                     purple=(0, -0.05, 0.0), magenta=(0, -0.04, 0.0)),
        ),
        fx=fx(grain=0.14, grain_size=0.00096, grain_chroma=0.15, grain_rough=0.2,
              halation=0.05, bloom=0.04, vignette=0.1),
    ),

    # ───────────── Japanese ─────────────
    "tokyo_blue": dict(
        name="Tokyo Blue", zh="東京藍", series="japanese",
        note="藍綠色陰影、乾淨的白、自然膚色、清透的天空。",
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
        note="柔和對比、低飽和、淡雅的顏色與自然的高光。",
        colour=dict(
            temp=0.02,
            curve=filmic(exposure=0.12, contrast=0.82, toe=0.03, shoulder=0.98),
            sat=-0.24,
            hsl=dict(red=(4, -0.10, 0.02), orange=(0, 0.04, 0.04), yellow=(-4, -0.25, 0.05),
                     green=(-6, -0.30, 0.06), aqua=(0, -0.15, 0.04), blue=(-6, -0.20, 0.05)),
            split=dict(shadows=(260, 0.006), highlights=(60, 0.006)),
        ),
        fx=fx(grain=0.2, grain_size=0.00112, grain_chroma=0.15, soft_focus=0.1, mist=0.08,
              diffusion_radius=0.006, vignette=0.08, vignette_soft=0.8),
    ),
    "summer_green": dict(
        name="Summer Green", zh="夏日綠", series="japanese",
        note="綠色層次豐富而不螢光、自然的植被與柔和的陽光。",
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
        note="早期數位相機：清晰反差、略冷的色調、較硬的高光肩部與細小的色彩雜訊。",
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
        note="直打閃光的夜生活：高反差、輕微暖色、閃光的明暗落差與高光微光。",
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
        note="數位隨身機：緊實的色彩、清晰的細節、冷暖分離。",
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
        note="夜景電影感：紅色高光暈染與柔和光暈，暗部保留層次。",
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
        note="柔焦與細緻顆粒、柔和高光、懷舊的顏色，帶一點點灰塵、漏光和色彩漂移。",
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
SHIP = ["mellow400", "cinema400", "tokyo_blue", "night_bloom"]
