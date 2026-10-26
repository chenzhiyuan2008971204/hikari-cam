package com.hikari.cam.filter

/** The 20 built-in looks. Tuned as starting points; every value can be tweaked in the app. */
object Presets {

    val ORIGINAL = Filter("original", "原圖", "Original", Category.AIR, FilterParams.NEUTRAL)

    val all: List<Filter> = listOf(
        ORIGINAL,

        // ── 空氣感：bright, low contrast, airy pastel ──
        Filter(
            "toumei", "透明感", "Toumei", Category.AIR,
            FilterParams(
                exposure = 0.25f, contrast = -0.18f, saturation = -0.15f, temperature = -0.12f,
                highlights = -0.15f, shadows = 0.25f, fade = 0.12f,
                shadowTint = Rgb(-0.02f, 0.02f, 0.05f), gain = Rgb(0.98f, 1f, 1.02f),
            )
        ),
        Filter(
            "mint", "薄荷", "Mint", Category.AIR,
            FilterParams(
                exposure = 0.2f, contrast = -0.15f, saturation = -0.2f, temperature = -0.08f,
                tint = -0.12f, shadows = 0.2f, fade = 0.15f,
                shadowTint = Rgb(-0.03f, 0.04f, 0.03f), highlightTint = Rgb(0f, 0.02f, 0.01f),
            )
        ),
        Filter(
            "seiku", "晴空", "Seiku", Category.AIR,
            FilterParams(
                exposure = 0.15f, contrast = -0.05f, saturation = 0.05f, vibrance = 0.2f,
                temperature = -0.2f, highlights = -0.2f, shadows = 0.15f, fade = 0.06f,
                shadowTint = Rgb(-0.02f, 0f, 0.05f),
            )
        ),
        Filter(
            "sakura", "櫻", "Sakura", Category.AIR,
            FilterParams(
                exposure = 0.22f, contrast = -0.12f, saturation = -0.1f, temperature = 0.03f,
                tint = 0.14f, highlights = -0.1f, shadows = 0.2f, fade = 0.1f,
                highlightTint = Rgb(0.03f, 0f, 0.02f), bloom = 0.15f,
            )
        ),
        Filter(
            "milk", "牛奶", "Milk", Category.AIR,
            FilterParams(
                exposure = 0.3f, contrast = -0.22f, saturation = -0.32f, temperature = 0.06f,
                highlights = -0.25f, shadows = 0.3f, fade = 0.25f, bloom = 0.1f,
            )
        ),
        Filter(
            "natsu", "夏日", "Natsu", Category.AIR,
            FilterParams(
                exposure = 0.15f, contrast = 0.05f, saturation = 0.08f, vibrance = 0.25f,
                temperature = -0.05f, tint = -0.05f, highlights = -0.2f, shadows = 0.15f,
                fade = 0.05f, shadowTint = Rgb(-0.03f, 0.02f, 0.05f), gamma = Rgb(1f, 1.04f, 1.02f),
            )
        ),
        Filter(
            "gogo", "午後", "Gogo", Category.AIR,
            FilterParams(
                exposure = 0.12f, contrast = -0.1f, saturation = -0.05f, temperature = 0.22f,
                highlights = -0.2f, shadows = 0.15f, fade = 0.12f,
                highlightTint = Rgb(0.03f, 0.015f, -0.02f), bloom = 0.2f, leak = 0.18f,
            )
        ),

        // ── 底片：film emulations with grain ──
        Filter(
            "portra", "人像", "Portra 400", Category.FILM,
            FilterParams(
                exposure = 0.1f, contrast = -0.06f, saturation = -0.06f, temperature = 0.12f,
                highlights = -0.15f, shadows = 0.1f, fade = 0.08f,
                lift = Rgb(0.02f, 0.01f, 0.0f), gamma = Rgb(1.03f, 1f, 0.97f),
                highlightTint = Rgb(0.02f, 0.01f, -0.01f), grain = 0.25f,
            )
        ),
        Filter(
            "gold", "金黃", "Gold 200", Category.FILM,
            FilterParams(
                exposure = 0.05f, contrast = 0.1f, saturation = 0.15f, temperature = 0.28f,
                highlights = -0.12f, fade = 0.05f, highlightTint = Rgb(0.04f, 0.025f, -0.04f),
                shadowTint = Rgb(0.01f, 0f, -0.02f), grain = 0.3f, vignette = 0.15f,
            )
        ),
        Filter(
            "superia", "富士綠", "Superia", Category.FILM,
            FilterParams(
                contrast = 0.1f, saturation = 0.05f, temperature = -0.04f, tint = -0.06f,
                highlights = -0.1f, fade = 0.06f,
                shadowTint = Rgb(-0.02f, 0.03f, 0.01f), highlightTint = Rgb(0.02f, 0f, 0.02f),
                gamma = Rgb(0.98f, 1.03f, 1f), grain = 0.3f,
            )
        ),
        Filter(
            "pro400h", "粉彩", "Pro 400H", Category.FILM,
            FilterParams(
                exposure = 0.18f, contrast = -0.12f, saturation = -0.12f, temperature = -0.05f,
                highlights = -0.18f, shadows = 0.15f, fade = 0.14f,
                shadowTint = Rgb(-0.03f, 0.02f, 0.04f), highlightTint = Rgb(0.02f, 0.01f, 0f),
                grain = 0.22f,
            )
        ),
        Filter(
            "instant", "拍立得", "Instant", Category.FILM,
            FilterParams(
                exposure = 0.08f, contrast = -0.12f, saturation = -0.12f, temperature = 0.1f,
                highlights = -0.25f, fade = 0.3f,
                shadowTint = Rgb(-0.02f, 0.03f, 0.04f), highlightTint = Rgb(0.03f, 0.02f, -0.01f),
                grain = 0.2f, vignette = 0.3f, bloom = 0.12f,
            )
        ),
        Filter(
            "utsurun", "即可拍", "Disposable", Category.FILM,
            FilterParams(
                exposure = 0.05f, contrast = 0.22f, saturation = 0.2f, temperature = 0.1f,
                highlights = -0.1f, fade = 0.08f, highlightTint = Rgb(0.02f, 0.02f, -0.02f),
                grain = 0.45f, vignette = 0.45f, leak = 0.3f,
            )
        ),
        Filter(
            "showa", "昭和", "Showa", Category.FILM,
            FilterParams(
                exposure = 0.05f, contrast = -0.05f, saturation = -0.38f, temperature = 0.3f,
                tint = 0.04f, highlights = -0.2f, fade = 0.3f,
                gamma = Rgb(1.02f, 1f, 0.92f), grain = 0.4f, vignette = 0.35f,
            )
        ),

        // ── 電影・夜：cinematic and night ──
        Filter(
            "tokyo", "東京夜", "Tokyo Night", Category.CINEMA,
            FilterParams(
                contrast = 0.15f, saturation = 0.1f, temperature = -0.25f, tint = 0.06f,
                highlights = -0.15f, shadows = 0.05f, fade = 0.05f,
                shadowTint = Rgb(-0.03f, 0.02f, 0.06f), highlightTint = Rgb(0.03f, -0.01f, 0.03f),
                halation = 0.3f, grain = 0.2f,
            )
        ),
        Filter(
            "cinestill", "霓虹", "800T", Category.CINEMA,
            FilterParams(
                contrast = 0.12f, saturation = 0.08f, temperature = -0.35f, tint = 0.03f,
                highlights = -0.1f, fade = 0.04f, shadowTint = Rgb(-0.02f, 0.01f, 0.05f),
                halation = 0.65f, grain = 0.3f,
            )
        ),
        Filter(
            "tealorange", "青橙", "Teal & Orange", Category.CINEMA,
            FilterParams(
                contrast = 0.15f, saturation = 0.08f, highlights = -0.12f, shadows = 0.05f,
                shadowTint = Rgb(-0.05f, 0.02f, 0.06f), highlightTint = Rgb(0.06f, 0.02f, -0.05f),
                vignette = 0.2f,
            )
        ),
        Filter(
            "drama", "日劇", "Drama", Category.CINEMA,
            FilterParams(
                exposure = 0.08f, contrast = -0.06f, saturation = -0.22f, temperature = 0.04f,
                tint = -0.06f, highlights = -0.22f, shadows = 0.1f, fade = 0.15f,
                shadowTint = Rgb(-0.01f, 0.02f, 0.02f), bloom = 0.15f, vignette = 0.2f,
            )
        ),
        Filter(
            "moriyama", "森山", "Moriyama B&W", Category.CINEMA,
            FilterParams(
                exposure = -0.1f, contrast = 0.5f, mono = 1f, shadows = -0.1f,
                grain = 0.6f, vignette = 0.4f,
            )
        ),
        Filter(
            "silver", "銀鹽", "Silver B&W", Category.CINEMA,
            FilterParams(
                exposure = 0.05f, contrast = 0.1f, mono = 1f, highlights = -0.15f,
                shadows = 0.1f, fade = 0.15f, highlightTint = Rgb(0.01f, 0.005f, -0.01f),
                grain = 0.3f, vignette = 0.15f,
            )
        ),
    )
}
