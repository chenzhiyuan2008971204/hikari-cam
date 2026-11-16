package com.hikari.studio

import com.hikari.studio.FrameCat.*

/** The frame library. Every design is original; none reproduces a manufacturer's border, logo or trade dress. */
object Frames {
    private fun c(v: Long) = v.toInt()
    private val FILMK = c(0xFF0E0E0E); private val ORANGE = c(0xFFFF8A1E); private val AMBER = c(0xFFE9A23B)
    private val CREAM = c(0xFFF6F0E2); private val PAPER = c(0xFFF3EFE7); private val KRAFT = c(0xFFC8A77C); private val IVORY = c(0xFFFBF8F1)
    private val RED = c(0xFFD6402B); private val GREY = c(0xFF8C8C8C); private val LIGHT = c(0xFFE7E5E0)

    val all: List<FrameDef> = listOf(
        // ------------------------------------------------------------------ Film & Analog
        FrameDef("film35", "35mm Negative", "35mm 底片框", FILM, "city", "35mm 底片 齒孔 negative film") {
            margins(3f, 11f, 3f, 11f); paper(FILMK)
            sprockets(2.2f * u, 3.4f * u, c(0xFFEDEDED)); sprockets(ch - 5.6f * u, 3.4f * u, c(0xFFEDEDED))
            text("HIKARI 400", px + 2 * u, py - 1.6f * u, 2.2f * u, "mono", 700, ORANGE, -1, 0.2f)
            text("▸ ${m.number.toString().padStart(2, '0')}A", px + pw - 2 * u, py - 1.6f * u, 2.2f * u, "mono", 700, ORANGE, 1, 0.1f)
            text(date("y_m_d_space"), px + pw - 2 * u, ch - 7.4f * u, 2f * u, "mono", 700, ORANGE, 1, 0.2f)
        },
        FrameDef("film35_vertical", "35mm Vertical Strip", "直式 35mm 片條", FILM, "portrait", "35mm 直式 齒孔") {
            margins(11f, 3f, 11f, 3f); paper(FILMK)
            sprocketsV(2.2f * u, 3.4f * u, c(0xFFEDEDED)); sprocketsV(cw - 5.6f * u, 3.4f * u, c(0xFFEDEDED))
            text("HIKARI 200  ▸ ${m.number}", px - 1.4f * u, py + ph - 2 * u, 2.1f * u, "mono", 700, ORANGE, -1, 0.2f, -90f)
            text(date("ymd_dot"), px + pw + 3.2f * u, py + 2 * u, 2f * u, "mono", 700, ORANGE, -1, 0.2f, 90f)
        },
        FrameDef("film120", "120 Medium Format", "120 中片幅", FILM, "seaside", "120 中片幅 medium format") {
            margins(4f, 9f, 4f, 9f); paper(c(0xFF151312))
            text("HIKARI PRO 160", px, py - 3 * u, 2.4f * u, "sans", 700, AMBER, -1, 0.3f)
            poly(AMBER, px + pw / 2 - 1.5f * u, py - 5.2f * u, px + pw / 2 + 1.5f * u, py - 5.2f * u, px + pw / 2, py - 2.6f * u)
            text("${m.number}", px + pw / 2 + 3 * u, py - 3 * u, 2.6f * u, "sans", 700, AMBER)
            text("120", px + pw, py - 3 * u, 2.4f * u, "sans", 700, AMBER, 1, 0.3f)
            text(date("d_mon_y"), px, ch - 3.4f * u, 2f * u, "sans", 400, AMBER, -1, 0.25f)
            text(info(), px + pw, ch - 3.4f * u, 2f * u, "sans", 400, AMBER, 1, 0.1f)
        },
        FrameDef("film135_strip", "135 Film Strip", "135 膠卷片條", FILM, "sunny", "135 膠卷 片條 相鄰") {
            margins(15f, 10f, 15f, 10f); paper(FILMK)
            behind { fill(0f, py, px - 2.5f * u, ph, c(0xFF2A2623)); fill(px + pw + 2.5f * u, py, cw, ph, c(0xFF2A2623)) }
            sprockets(2f * u, 3.2f * u, c(0xFFE8E8E8)); sprockets(ch - 5.2f * u, 3.2f * u, c(0xFFE8E8E8))
            for ((i, x) in listOf(px - 9 * u, px + pw / 2, px + pw + 9 * u).withIndex())
                text("${m.number - 1 + i}", x, ch - 6.6f * u, 2f * u, "mono", 700, ORANGE, 0)
            text("SAFETY FILM", px + 3 * u, py - 1.4f * u, 1.8f * u, "mono", 400, ORANGE, -1, 0.35f)
        },
        FrameDef("contact_sheet", "Contact Sheet", "接觸印樣", FILM, "portrait", "接觸印樣 contact 紅筆 選片") {
            margins(9f, 9f, 9f, 15f); paper(c(0xFFF4F2EC)); noise(0.02f)
            behind { fill(0f, py - 5.5f * u, cw, ph + 11 * u, FILMK) }
            sprockets(py - 4.4f * u, 2.4f * u, c(0xFFF4F2EC)); sprockets(py + ph + 2f * u, 2.4f * u, c(0xFFF4F2EC))
            stroke(px - 1.6f * u, py - 1.6f * u, pw + 3.2f * u, ph + 3.2f * u, RED, 0.7f * u, 3 * u)
            text(caption("✓ select"), px + pw, ch - 5 * u, 4.2f * u, "hand", 700, RED, 1)
            text("ROLL ${m.number.toString().padStart(3, '0')}", px, ch - 5 * u, 2f * u, "mono", 400, c(0xFF555555), -1, 0.2f)
            text(date("ymd_dot"), px, ch - 2.2f * u, 1.8f * u, "mono", 400, c(0xFF777777), -1, 0.2f)
        },
        FrameDef("analog_info", "Analog Data Back", "類比相機資訊框", FILM, "nightstreet", "資料機背 data back 資訊") {
            margins(3f, 3f, 3f, 12f); paper(FILMK)
            led(date("retro_yy_m_d"), px + pw, ch - 4.2f * u, 4f * u, ORANGE, 1)
            text(info(), px, ch - 4.6f * u, 2.3f * u, "mono", 400, c(0xFFCFCFCF), -1, 0.08f)
            text(m.camera.ifEmpty { null }, px, ch - 8.2f * u, 1.8f * u, "mono", 700, c(0xFF8A8A8A), -1, 0.3f)
        },
        FrameDef("slide_mount", "Slide Mount", "幻燈片片框", FILM, "foliage", "幻燈片 正片 slide mount") {
            margins(15f, 19f, 15f, 19f); paper(c(0xFFECEAE4)); noise(0.03f); radius = 2.4f * u
            stroke(1.5f * u, 1.5f * u, cw - 3 * u, ch - 3 * u, c(0xFFD2CFC6), 0.5f * u, 4 * u)
            text("HIKARI CHROME", px, py - 6 * u, 2.6f * u, "sans", 700, c(0xFF8A8478), -1, 0.4f)
            oval(px + pw - 3 * u, py - 8.4f * u, 3 * u, 3 * u, RED)
            text(date("mon_d_y"), px, ch - 7 * u, 2.2f * u, "mono", 400, c(0xFF6E695F), -1, 0.2f)
            text("No. ${m.number.toString().padStart(2, '0')}", px + pw, ch - 7 * u, 2.2f * u, "mono", 400, c(0xFF6E695F), 1, 0.2f)
        },
        FrameDef("negative_sleeve", "Negative Sleeve", "底片保存頁", FILM, "overcast", "底片袋 保存頁 sleeve") {
            margins(6f, 12f, 6f, 9f); paper(c(0xFFE9E6DC))
            behind { fill(1.5f * u, py - 4 * u, cw - 3 * u, ph + 8 * u, FILMK) }
            sprockets(py - 3.2f * u, 2f * u, c(0xFFE9E6DC), 2 * u, cw - 2 * u); sprockets(py + ph + 1.2f * u, 2f * u, c(0xFFE9E6DC), 2 * u, cw - 2 * u)
            line(0f, py - 5.2f * u, cw, py - 5.2f * u, c(0x55FFFFFF), 0.3f * u); line(0f, py + ph + 5.2f * u, cw, py + ph + 5.2f * u, c(0x55FFFFFF), 0.3f * u)
            fill(3 * u, 1.6f * u, 30 * u, 4.6f * u, WHITE, 0.4f * u)
            text("No. ${m.number.toString().padStart(4, '0')}   ${date("ymd_dot") ?: ""}", 4.2f * u, 4.9f * u, 2.1f * u, "mono", 400, c(0xFF333333), -1, 0.1f)
        },
        FrameDef("film_leader", "Film Leader", "片頭漏光", FILM, "backlight", "片頭 漏光 leader light leak") {
            margins(13f, 6f, 3f, 6f); paper(FILMK)
            behind { grad(0f, 0f, px - 1.5f * u, ch, c(0xFFFF7A1A), c(0xFF3A0E00), false) }
            text("START ▸", px - 3 * u, ch - 4 * u, 2.4f * u, "mono", 700, WHITE, -1, 0.3f, -90f)
            text(date("ymd_slash"), px + pw, ch - 2.2f * u, 1.9f * u, "mono", 700, ORANGE, 1, 0.2f)
        },
        FrameDef("super8", "Super 8 Frame", "Super 8 小畫幅", FILM, "child", "super8 8mm 單孔") {
            margins(13f, 4f, 4f, 4f); paper(c(0xFF121212)); radius = 3 * u
            fill(4 * u, ch / 2 - 4.5f * u, 5 * u, 9 * u, c(0xFFF2F2F2), 1.2f * u)
            text("S8", 6.5f * u, ch - 4 * u, 2.2f * u, "mono", 700, AMBER, 0)
        },
        FrameDef("retro_print", "Retro Lab Print", "復古沖印相紙", FILM, "beach", "復古相紙 沖印 毛邊 deckle") {
            margins(6f, 6f, 6f, 6f); paper(c(0xFFF2EAD6)); noise(0.035f)
            torn(c(0xFFE2D9C2), 0.9f * u)
            text(date("mon_d_y"), px + pw, ch - 2.3f * u, 1.9f * u, "mono", 700, c(0xFFC56A2A), 1, 0.25f)
        },
        FrameDef("print_round", "Rounded Lab Print", "圓角沖印相紙", FILM, "seaside", "圓角 相紙 70s") {
            margins(5f, 5f, 5f, 8f); paper(IVORY); radius = 3.2f * u; noise(0.015f)
            text(date("mon_d_y")?.let { "·  $it  ·" }, cw / 2, ch - 3.2f * u, 1.9f * u, "mono", 400, GREY, 0, 0.3f)
        },
        FrameDef("darkroom_proof", "Darkroom Proof", "暗房樣張", FILM, "contrast", "暗房 樣張 手寫 裁切記號") {
            margins(7f, 7f, 7f, 17f); paper(c(0xFF1B1B1B))
            corners(px - 2 * u, py - 2 * u, pw + 4 * u, ph + 4 * u, 3.5f * u, WHITE, 0.25f * u)
            text(title("proof #${m.number}"), px, ch - 8 * u, 5 * u, "hand", 700, c(0xFFF2EFE6))
            text(date("ymd_dot"), px, ch - 3.2f * u, 2f * u, "mono", 400, c(0xFFA8A8A8), -1, 0.2f)
            text(info(), px + pw, ch - 3.2f * u, 2f * u, "mono", 400, c(0xFFA8A8A8), 1)
        },
        FrameDef("edge_code", "Edge Code", "片邊碼", FILM, "concrete", "片邊碼 DX 條碼 edge") {
            margins(3f, 7f, 3f, 7f); paper(FILMK)
            var x = px; while (x < px + pw - 20 * u) { text("HIKARI  5063", x, py - 2.4f * u, 1.7f * u, "mono", 700, AMBER, -1, 0.3f); x += 34 * u }
            barcode(px, ch - 5 * u, 22 * u, 3 * u, AMBER)
            text("${m.number}  ▸  ${m.number}A", px + pw, ch - 2.6f * u, 1.9f * u, "mono", 700, AMBER, 1, 0.2f)
        },
        FrameDef("pano_film", "Panorama Film", "寬景底片", FILM, "sunny", "寬景 panorama 全景", 65f / 24f) {
            margins(3f, 10f, 3f, 10f); paper(FILMK)
            sprockets(2f * u, 3f * u, c(0xFFEDEDED)); sprockets(ch - 5f * u, 3f * u, c(0xFFEDEDED))
            text("PANORAMA", px, py - 1.4f * u, 1.9f * u, "mono", 700, ORANGE, -1, 0.5f)
            text(date("y_m_d_space"), px + pw, py - 1.4f * u, 1.9f * u, "mono", 700, ORANGE, 1, 0.2f)
        },
        // ------------------------------------------------------------------ Minimal
        FrameDef("min_white", "Clean White", "極簡白框", MINIMAL, "sunny", "白框 極簡 white") { margins(5f, 5f, 5f, 5f); paper(WHITE) },
        FrameDef("min_black", "Clean Black", "極簡黑框", MINIMAL, "night", "黑框 極簡 black") {
            margins(5f, 5f, 5f, 9f); paper(c(0xFF0A0A0A))
            text(date("ymd_dot"), cw / 2, ch - 3.6f * u, 1.5f * u, "sans", 400, c(0xFF8A8A8A), 0, 0.5f)
        },
        FrameDef("hairline", "Hairline", "超細線框", MINIMAL, "white", "細線 hairline") {
            margins(9f, 9f, 9f, 9f); paper(WHITE); stroke(px - 2.2f * u, py - 2.2f * u, pw + 4.4f * u, ph + 4.4f * u, c(0xFF9A9A9A), 0.14f * u)
        },
        FrameDef("big_mat", "Gallery Mat", "大留白卡紙", MINIMAL, "foliage", "大留白 卡紙 mat") {
            margins(22f, 22f, 22f, 26f); paper(c(0xFFFAF9F6)); stroke(px - 0.6f * u, py - 0.6f * u, pw + 1.2f * u, ph + 1.2f * u, c(0xFFDAD7D0), 0.6f * u)
        },
        FrameDef("asym_left", "Asymmetric Left", "不對稱留白", MINIMAL, "portrait", "不對稱 留白") {
            margins(30f, 8f, 8f, 8f); paper(WHITE)
            text(title("Untitled"), 8 * u, py + 5 * u, 3.2f * u, "serif", 400, c(0xFF1A1A1A))
            text(date("ymd_dot"), 8 * u, py + 9.5f * u, 1.7f * u, "sans", 400, GREY, -1, 0.2f)
            line(8 * u, py + 12.5f * u, 20 * u, py + 12.5f * u, c(0xFF1A1A1A), 0.18f * u)
        },
        FrameDef("bottom_heavy", "Bottom Weighted", "下方留白", MINIMAL, "seaside", "下留白 置中") {
            margins(8f, 8f, 8f, 24f); paper(WHITE)
            text(title(""), cw / 2, ch - 12.5f * u, 2.6f * u, "sans", 400, c(0xFF222222), 0, 0.3f)
            text(date("ymd_dash"), cw / 2, ch - 7.5f * u, 1.7f * u, "sans", 400, GREY, 0, 0.3f)
        },
        FrameDef("top_title", "Top Title", "上方標題留白", MINIMAL, "city", "標題 上留白") {
            margins(6f, 21f, 6f, 6f); paper(WHITE)
            text(title("TITLE"), px, py - 6 * u, 6.5f * u, "sans", 700, c(0xFF111111), -1, -0.02f)
            text(date("d_mon_y"), px + pw, py - 6 * u, 1.8f * u, "sans", 400, GREY, 1, 0.25f)
        },
        FrameDef("double_line", "Double Rule", "雙線框", MINIMAL, "concrete", "雙線 框線") {
            margins(10f, 10f, 10f, 10f); paper(c(0xFFFDFDFB))
            stroke(px - 2f * u, py - 2f * u, pw + 4f * u, ph + 4f * u, c(0xFF222222), 0.35f * u)
            stroke(px - 3.4f * u, py - 3.4f * u, pw + 6.8f * u, ph + 6.8f * u, c(0xFF222222), 0.12f * u)
        },
        FrameDef("corner_marks", "Corner Marks", "角落記號", MINIMAL, "contrast", "角落 記號 裁切") {
            margins(10f, 10f, 10f, 10f); paper(WHITE); corners(px - 3 * u, py - 3 * u, pw + 6 * u, ph + 6 * u, 4 * u, c(0xFF222222), 0.2f * u)
        },
        FrameDef("soft_shadow", "Floating Print", "浮起陰影", MINIMAL, "white", "陰影 浮起") {
            margins(12f, 11f, 12f, 14f); paper(c(0xFFEDEBE7)); shadow(px, py + 1.2f * u, pw, ph, 3.5f * u, c(0x55000000))
        },
        FrameDef("inset_line", "Inset Keyline", "內嵌細線", MINIMAL, "beach", "內框 細線 無留白") {
            margins(0f, 0f, 0f, 0f); paper(WHITE); stroke(3 * u, 3 * u, pw - 6 * u, ph - 6 * u, c(0xE6FFFFFF), 0.22f * u)
        },
        FrameDef("float_caption", "Caption Rule", "圖說細線", MINIMAL, "overcast", "圖說 細線 caption") {
            margins(10f, 10f, 10f, 18f); paper(WHITE)
            line(px, py + ph + 5 * u, px + pw, py + ph + 5 * u, c(0xFFBDBDBD), 0.12f * u)
            text(title("Untitled"), px, py + ph + 10 * u, 2.3f * u, "sans", 700, c(0xFF1A1A1A))
            text(date("ymd_dot"), px + pw, py + ph + 10 * u, 1.9f * u, "sans", 400, GREY, 1, 0.15f)
        },
        FrameDef("side_stripe", "Side Margin", "側邊直排日期", MINIMAL, "portrait", "側邊 直排") {
            margins(6f, 6f, 20f, 6f); paper(WHITE)
            text(date("ymd_dot"), px + pw + 8 * u, py, 2f * u, "sans", 400, c(0xFF333333), -1, 0.4f, 90f)
            line(px + pw + 14 * u, py, px + pw + 14 * u, py + ph, c(0xFFCCCCCC), 0.12f * u)
        },
        FrameDef("grey_card", "Grey Card", "中性灰卡", MINIMAL, "child", "灰卡 中性灰") {
            margins(9f, 9f, 9f, 15f); paper(c(0xFF7B7B7B)); stroke(px - 0.5f * u, py - 0.5f * u, pw + u, ph + u, WHITE, 0.5f * u)
            // a grey step wedge, as on a calibration card
            for (i in 0 until 9) { val v = 255 * i / 8; fill(px + i * 4.2f * u, ch - 8 * u, 4.2f * u, 3 * u, (0xFF shl 24) or (v shl 16) or (v shl 8) or v) }
        },
        FrameDef("wide_top_bottom", "Letter Margin", "上下大留白", MINIMAL, "sunny", "上下留白") {
            margins(4f, 20f, 4f, 20f); paper(c(0xFFFBFBF9))
            text(date("y_m_d_space"), cw / 2, ch - 8 * u, 1.6f * u, "sans", 400, GREY, 0, 0.6f)
        },
        // ------------------------------------------------------------------ Japanese
        FrameDef("jp_print", "Nichijou Print", "日系相紙框", JAPANESE, "overcast", "日系 相紙 日常") {
            margins(7f, 7f, 7f, 10f); paper(c(0xFFFBF9F4)); noise(0.012f)
            text(date("ymd_dot"), px + pw, ch - 3.6f * u, 1.7f * u, "sans", 400, c(0xFF9A948A), 1, 0.35f)
        },
        FrameDef("jp_retro_date", "Showa Date", "日系復古日期框", JAPANESE, "beach", "日系 復古 日期 橘色") {
            margins(6f, 6f, 6f, 14f); paper(WHITE)
            led(date("retro_yy_m_d"), px + pw, ch - 5 * u, 3.4f * u, ORANGE, 1)
        },
        FrameDef("jp_blank", "Yohaku", "日系留白（余白）", JAPANESE, "white", "余白 留白 日系") {
            margins(18f, 26f, 18f, 34f); paper(c(0xFFFDFCF9))
            text(title(""), cw / 2, ch - 17 * u, 2.2f * u, "serif", 400, c(0xFF55504A), 0, 0.5f)
            text(date("ymd_dot"), cw / 2, ch - 12 * u, 1.5f * u, "sans", 400, c(0xFFA39C92), 0, 0.5f)
        },
        FrameDef("jp_cream", "Milk Cream", "清新奶油色框", JAPANESE, "foliage", "奶油色 清新 圓角") {
            margins(9f, 9f, 9f, 9f); paper(c(0xFFF7EFDC)); radius = 2.2f * u; noise(0.02f)
        },
        FrameDef("jp_magazine", "Kurashi Magazine", "日系雜誌排版", JAPANESE, "child", "日系 雜誌 生活") {
            margins(10f, 25f, 10f, 12f); paper(c(0xFFFBFAF6))
            text(title("日々"), px, py - 9 * u, 8.5f * u, "serif", 400, c(0xFF2B2926))
            line(px, py - 5 * u, px + pw, py - 5 * u, c(0xFF2B2926), 0.14f * u)
            text("vol. ${m.number.toString().padStart(2, '0')}", px + pw, py - 9 * u, 1.9f * u, "serif", 400, c(0xFF7A746B), 1, 0.2f, 0f, true)
            text(date("ymd_dot"), px, ch - 5 * u, 1.7f * u, "sans", 400, c(0xFF7A746B), -1, 0.3f)
            text(caption("ふつうの一日"), px + pw, ch - 5 * u, 1.9f * u, "serif", 400, c(0xFF7A746B), 1, 0.2f)
        },
        FrameDef("jp_vertical", "Tategaki", "日系直書標題", JAPANESE, "city", "直書 縱排 日系") {
            margins(7f, 7f, 20f, 7f); paper(c(0xFFFAF8F2))
            val s = title("光の記録"); var y = py + 4.2f * u
            for (chr in s) { text(chr.toString(), px + pw + 11 * u, y, 3.6f * u, "serif", 400, c(0xFF2B2926), 0); y += 5.2f * u }
            text(date("ymd_dot"), px + pw + 5.2f * u, py + ph, 1.5f * u, "sans", 400, c(0xFF8E877C), -1, 0.3f, -90f)
        },
        FrameDef("jp_seal", "Hanko Seal", "日系落款印章", JAPANESE, "seaside", "印章 落款 朱印") {
            margins(8f, 8f, 8f, 20f); paper(c(0xFFFCFBF7))
            fill(px + pw - 7 * u, ch - 13 * u, 7 * u, 7 * u, c(0xFFC8372D), 0.9f * u)
            text(title("光").take(1), px + pw - 3.5f * u, ch - 7.6f * u, 5f * u, "serif", 700, WHITE, 0)
            text(caption(""), px, ch - 9 * u, 2.4f * u, "serif", 400, c(0xFF3A3632), -1, 0.2f)
            text(date("ymd_dot"), px, ch - 5 * u, 1.6f * u, "sans", 400, c(0xFF8E877C), -1, 0.3f)
        },
        FrameDef("jp_note", "Film Note", "日系手寫便箋", JAPANESE, "portrait", "手寫 便箋 日系") {
            margins(8f, 8f, 8f, 22f); paper(c(0xFFF8F3E6)); noise(0.02f)
            text(caption("today, soft light."), px + 1 * u, ch - 11 * u, 4.2f * u, "casual", 400, c(0xFF4A453D))
            line(px, ch - 8.4f * u, px + pw * 0.62f, ch - 8.4f * u, c(0xFFB9B09E), 0.14f * u)
            text(date("ymd_dot"), px + pw, ch - 4 * u, 1.6f * u, "sans", 400, c(0xFF9A9284), 1, 0.3f)
        },
        FrameDef("jp_diary", "Day Number", "日系日記大日期", JAPANESE, "sunny", "日記 日期 大數字") {
            margins(7f, 7f, 7f, 26f); paper(c(0xFFFDFCF8))
            val d = m.date.takeIf { o.showDate }
            text(d?.dayOfMonth?.toString()?.padStart(2, '0'), px, ch - 6 * u, 15 * u, "sans", 400, c(0xFF2B2926), -1, -0.04f)
            text(d?.let { DateFormats.format("mon_d_y", it).take(3) + "  " + it.year }, px + 22 * u, ch - 13 * u, 2.2f * u, "sans", 400, c(0xFF6E685F), -1, 0.4f)
            text(caption(""), px + 22 * u, ch - 8 * u, 2.2f * u, "serif", 400, c(0xFF6E685F), -1, 0.1f)
        },
        FrameDef("jp_blush", "Sakura Paper", "日系淡粉相紙", JAPANESE, "child", "淡粉 櫻 柔和 陰影") {
            margins(11f, 11f, 11f, 13f); paper(c(0xFFF6E9E4)); radius = 1.4f * u; shadow(px, py + u, pw, ph, 3 * u, c(0x33583A30), 1.4f * u)
        },
        FrameDef("jp_postcard", "Ehagaki Postcard", "日系明信片", JAPANESE, "seaside", "明信片 郵票 地址線") {
            margins(7f, 7f, 44f, 7f); paper(c(0xFFFBF8EF)); noise(0.015f)
            val x0 = px + pw + 6 * u
            stroke(cw - 15 * u, py, 9 * u, 11 * u, c(0xFF9A9284), 0.16f * u)
            text("POST CARD", x0, py + 4 * u, 1.9f * u, "serif", 400, c(0xFF6E685F), -1, 0.5f)
            for (i in 0 until 4) line(x0, py + ph * 0.45f + i * 6.5f * u, cw - 7 * u, py + ph * 0.45f + i * 6.5f * u, c(0xFFB9B09E), 0.14f * u)
            text(caption(""), x0, py + ph * 0.45f - 1.2f * u, 2.6f * u, "casual", 400, c(0xFF4A453D))
            text(date("ymd_dot"), x0, py + ph - 1 * u, 1.6f * u, "sans", 400, c(0xFF9A9284), -1, 0.3f)
        },
        // ------------------------------------------------------------------ Cinematic
        FrameDef("cine_bars", "Scope Letterbox", "電影寬黑框", CINEMATIC, "nightstreet", "電影 黑邊 2.39 letterbox", 2.39f) { margins(0f, 12f, 0f, 12f); paper(BLACK) },
        FrameDef("cine_subtitle", "Subtitle", "電影字幕區", CINEMATIC, "portrait", "字幕 電影 subtitle", 2.39f) {
            margins(0f, 13f, 0f, 13f); paper(BLACK)
            text(caption("— and then the light changed."), cw / 2, ch - 5.4f * u, 2.9f * u, "sans", 400, c(0xFFF4F4F4), 0, 0.02f)
        },
        FrameDef("cine_still", "Production Still", "劇照資訊框", CINEMATIC, "city", "劇照 still 資訊", 2.0f) {
            margins(0f, 9f, 0f, 14f); paper(c(0xFF050505))
            text(title("UNTITLED").uppercase(), 4 * u, ch - 7.2f * u, 3.4f * u, "condensed", 700, WHITE, -1, 0.3f)
            text(caption("STILL ${m.number.toString().padStart(3, '0')}"), 4 * u, ch - 3 * u, 1.8f * u, "condensed", 400, c(0xFF9A9A9A), -1, 0.4f)
            text(date("d_mon_y"), cw - 4 * u, ch - 7.2f * u, 2f * u, "condensed", 400, c(0xFFCFCFCF), 1, 0.3f)
        },
        FrameDef("cine_slate", "Clapper Slate", "場記板", CINEMATIC, "contrast", "場記板 slate clapper") {
            margins(0f, 0f, 0f, 27f); paper(c(0xFF101010))
            val y0 = py + ph; val w = cw
            var x = 0f; var k = 0; while (x < w) { poly(if (k % 2 == 0) WHITE else c(0xFF101010), x, y0, x + 6 * u, y0, x + 9 * u, y0 + 5 * u, x + 3 * u, y0 + 5 * u); x += 6 * u; k++ }
            line(0f, y0 + 5 * u, w, y0 + 5 * u, WHITE, 0.2f * u); line(0f, y0 + 16 * u, w, y0 + 16 * u, WHITE, 0.2f * u)
            line(w / 3, y0 + 16 * u, w / 3, ch, WHITE, 0.2f * u); line(w * 2 / 3, y0 + 16 * u, w * 2 / 3, ch, WHITE, 0.2f * u)
            text("PROD.", 3 * u, y0 + 8.5f * u, 1.5f * u, "condensed", 400, c(0xFF9A9A9A), -1, 0.3f)
            text(title("HIKARI").uppercase(), 3 * u, y0 + 14 * u, 4.2f * u, "hand", 700, WHITE)
            for ((i, lab) in listOf("SCENE" to "${m.number}", "TAKE" to "1", "DATE" to (date("ymd_dot") ?: "")).withIndex()) {
                val xx = w * i / 3 + 3 * u
                text(lab.first, xx, y0 + 19.5f * u, 1.5f * u, "condensed", 400, c(0xFF9A9A9A), -1, 0.3f)
                text(lab.second, xx, y0 + 25 * u, 3.6f * u, "hand", 700, WHITE)
            }
        },
        FrameDef("cine_timecode", "Timecode Dailies", "電影膠片資訊框", CINEMATIC, "night", "時間碼 timecode dailies", 1.85f) {
            margins(0f, 10f, 0f, 10f); paper(BLACK)
            val d = m.date.takeIf { o.showDate }
            led(d?.let { "%02d:%02d:%02d:%02d".format(it.hour, it.minute, it.second, m.number % 24) }, 4 * u, ch - 3 * u, 3.2f * u, c(0xFFFFFFFF))
            text("KEYKODE  KU 22 9611 ${1000 + m.number}", cw - 4 * u, ch - 3.4f * u, 1.7f * u, "mono", 400, c(0xFFAFAFAF), 1, 0.15f)
            text("A-CAM   ROLL ${m.number.toString().padStart(3, '0')}", 4 * u, 6 * u, 1.7f * u, "mono", 400, c(0xFFAFAFAF), -1, 0.2f)
            text(date("ymd_dash"), cw - 4 * u, 6 * u, 1.7f * u, "mono", 400, c(0xFFAFAFAF), 1, 0.2f)
        },
        FrameDef("cine_academy", "Academy Pillarbox", "學院比例直黑邊", CINEMATIC, "portrait", "學院 1.37 pillarbox", 1.375f) {
            margins(15f, 4f, 15f, 4f); paper(BLACK)
            text("1.37 : 1", 7.5f * u, ch - 5 * u, 1.6f * u, "mono", 400, c(0xFF777777), 0, 0.2f)
        },
        FrameDef("cine_poster", "One-Sheet Poster", "電影海報", CINEMATIC, "nightstreet", "海報 poster 片名") {
            margins(8f, 8f, 8f, 36f); paper(c(0xFF0B0B0C))
            text(title("THE LIGHT").uppercase(), cw / 2, ch - 22 * u, 9 * u, "serif", 700, c(0xFFF2EFE6), 0, 0.18f)
            text(caption("A FILM BY HIKARI"), cw / 2, ch - 15 * u, 2.1f * u, "condensed", 400, c(0xFFB8B4AA), 0, 0.6f)
            text("DIRECTOR OF PHOTOGRAPHY  ·  EDITOR  ·  MUSIC  ·  PRODUCTION DESIGN", cw / 2, ch - 9 * u, 1.5f * u, "condensed", 400, c(0xFF7E7B74), 0, 0.3f)
            text(date("mon_d_y"), cw / 2, ch - 4.5f * u, 2f * u, "condensed", 700, c(0xFFB8B4AA), 0, 0.5f)
        },
        FrameDef("cine_white", "White Letterbox", "白色寬銀幕", CINEMATIC, "seaside", "白邊 寬銀幕 letterbox", 2.39f) {
            margins(0f, 14f, 0f, 14f); paper(WHITE)
            text(title(""), 4 * u, 8.5f * u, 2f * u, "sans", 700, c(0xFF1A1A1A), -1, 0.4f)
            text(date("ymd_dot"), cw - 4 * u, ch - 6 * u, 1.6f * u, "sans", 400, GREY, 1, 0.3f)
        },
        FrameDef("cine_viewfinder", "Camera Viewfinder", "攝影機觀景窗", CINEMATIC, "city", "觀景窗 REC viewfinder", 1.85f) {
            margins(0f, 0f, 0f, 0f); paper(BLACK)
            corners(5 * u, 5 * u, pw - 10 * u, ph - 10 * u, 7 * u, c(0xE6FFFFFF), 0.3f * u)
            line(pw / 2 - 2.5f * u, ph / 2, pw / 2 + 2.5f * u, ph / 2, c(0xCCFFFFFF), 0.2f * u); line(pw / 2, ph / 2 - 2.5f * u, pw / 2, ph / 2 + 2.5f * u, c(0xCCFFFFFF), 0.2f * u)
            oval(8 * u, 8 * u, 2.4f * u, 2.4f * u, c(0xFFFF2A1A)); text("REC", 12 * u, 10.2f * u, 2.4f * u, "mono", 700, WHITE, -1, 0.15f)
            val d = m.date.takeIf { o.showDate }
            text(d?.let { "%02d:%02d:%02d".format(it.hour, it.minute, it.second) }, pw - 8 * u, 10.2f * u, 2.4f * u, "mono", 700, WHITE, 1, 0.1f)
            text(info(), 8 * u, ph - 8 * u, 2f * u, "mono", 400, WHITE, -1, 0.1f)
        },
        FrameDef("cine_ticks", "Scope Ruler", "寬銀幕刻度", CINEMATIC, "sunny", "刻度 ruler 寬銀幕", 2.39f) {
            margins(0f, 11f, 0f, 11f); paper(c(0xFF080808))
            var x = 2 * u; var i = 0
            while (x < cw) { val h = if (i % 5 == 0) 3 * u else 1.5f * u; line(x, py - 1 * u, x, py - 1 * u - h, c(0xFF8A8A8A), 0.14f * u); line(x, py + ph + u, x, py + ph + u + h, c(0xFF8A8A8A), 0.14f * u); x += 2 * u; i++ }
            text("2.39 : 1", cw / 2, ch - 3 * u, 1.6f * u, "mono", 400, c(0xFF8A8A8A), 0, 0.4f)
        },
        FrameDef("cine_lower_third", "Lower Third", "片名字卡", CINEMATIC, "portrait", "字卡 lower third 片名", 2.0f) {
            margins(0f, 0f, 0f, 0f); paper(BLACK)
            grad(0f, ph * 0.62f, pw, ph * 0.38f, c(0x00000000), c(0xD9000000), true)
            fill(6 * u, ph - 17 * u, 0.5f * u, 10 * u, c(0xFFE8C36A))
            text(title("Chapter One"), 9 * u, ph - 11.5f * u, 4.6f * u, "serif", 400, WHITE)
            text(caption(date("d_mon_y") ?: ""), 9 * u, ph - 7.4f * u, 1.9f * u, "condensed", 400, c(0xFFD8D8D8), -1, 0.4f)
        },
        // ------------------------------------------------------------------ Instant
        FrameDef("instant_classic", "Instant Classic", "拍立得框", INSTANT, "portrait", "拍立得 instant 白框") {
            margins(5.5f, 5.5f, 5.5f, 23f); paper(c(0xFFFCFBF8)); noise(0.012f)
        },
        FrameDef("instant_caption", "Instant Handwritten", "拍立得手寫", INSTANT, "child", "拍立得 手寫 caption") {
            margins(5.5f, 5.5f, 5.5f, 23f); paper(c(0xFFFCFBF8)); noise(0.012f)
            text(caption("good day ♥"), cw / 2, ch - 9 * u, 6 * u, "hand", 700, c(0xFF2F2C28), 0)
            text(date("ymd_dot"), cw / 2, ch - 3.6f * u, 1.7f * u, "sans", 400, GREY, 0, 0.3f)
        },
        FrameDef("instant_square", "Square Instant", "方形相紙", INSTANT, "beach", "方形 相紙 square", 1f) {
            margins(6.5f, 6.5f, 6.5f, 25f); paper(c(0xFFFBFAF6)); noise(0.012f)
        },
        FrameDef("instant_retro", "Rainbow Instant", "復古拍立得彩條", INSTANT, "sunny", "復古 拍立得 彩虹") {
            margins(6f, 6f, 6f, 24f); paper(c(0xFFF4ECD8)); noise(0.03f)
            val cols = listOf(c(0xFFE8452C), c(0xFFF29A2E), c(0xFFF5D547), c(0xFF4FB06D), c(0xFF3B7FC4))
            for ((i, col) in cols.withIndex()) fill(px + i * 2.6f * u, ch - 9 * u, 2.6f * u, 1.6f * u, col)
            text(date("mon_d_y"), px + pw, ch - 7.6f * u, 1.8f * u, "mono", 700, c(0xFF6B5E45), 1, 0.2f)
        },
        FrameDef("instant_thick", "Thick Base", "厚底白框", INSTANT, "foliage", "厚底 白框") {
            margins(10f, 10f, 10f, 36f); paper(WHITE)
            text(title(""), cw / 2, ch - 17 * u, 3.2f * u, "sans", 700, c(0xFF1A1A1A), 0, 0.4f)
            text(date("ymd_dot"), cw / 2, ch - 11 * u, 1.7f * u, "sans", 400, GREY, 0, 0.4f)
        },
        FrameDef("instant_shadow", "Print on Table", "相紙陰影", INSTANT, "seaside", "陰影 相紙 桌面") {
            margins(13f, 12f, 13f, 30f); paper(c(0xFFD9D4CA)); noise(0.02f)
            shadow(px - 5 * u, py - 5 * u + 1.5f * u, pw + 10 * u, ph + 23 * u, 4 * u, c(0x66000000), 0.6f * u)
            behind { fill(px - 5 * u, py - 5 * u, pw + 10 * u, ph + 23 * u, c(0xFFFCFBF8), 0.6f * u) }
            text(caption(""), cw / 2, py + ph + 11 * u, 5 * u, "hand", 700, c(0xFF2F2C28), 0)
        },
        FrameDef("instant_wide", "Wide Instant", "寬幅拍立得", INSTANT, "sunny", "寬幅 wide instant", 1.6f) {
            margins(5f, 6f, 5f, 21f); paper(c(0xFFFBFAF6)); noise(0.012f)
            text(date("ymd_dot"), px + pw, ch - 8 * u, 1.8f * u, "sans", 400, GREY, 1, 0.3f)
        },
        FrameDef("instant_mini", "Mini Instant", "迷你拍立得", INSTANT, "portrait", "迷你 mini 直式", 0.74f) {
            margins(7f, 9f, 7f, 26f); paper(c(0xFFFDFCFA)); radius = 0.8f * u
            stroke(0.8f * u, 0.8f * u, cw - 1.6f * u, ch - 1.6f * u, c(0xFFE5E2DA), 0.3f * u, 3 * u)
        },
        FrameDef("instant_black", "Black Instant", "黑色拍立得", INSTANT, "night", "黑色 拍立得 銀線") {
            margins(5.5f, 5.5f, 5.5f, 23f); paper(c(0xFF141414))
            stroke(px - 0.5f * u, py - 0.5f * u, pw + u, ph + u, c(0xFFB9B9B9), 0.14f * u)
            text(caption(""), cw / 2, ch - 9 * u, 5.4f * u, "hand", 700, c(0xFFEDEAE2), 0)
            text("No.${m.number.toString().padStart(3, '0')}", px, ch - 3.4f * u, 1.6f * u, "mono", 400, c(0xFF8A8A8A), -1, 0.2f)
        },
        FrameDef("instant_tape", "Taped Instant", "膠帶拍立得", INSTANT, "child", "膠帶 紙膠帶 牆面") {
            margins(14f, 16f, 14f, 30f); paper(KRAFT); noise(0.04f)
            shadow(px - 5 * u, py - 5 * u + u, pw + 10 * u, ph + 22 * u, 3 * u, c(0x55000000))
            behind { fill(px - 5 * u, py - 5 * u, pw + 10 * u, ph + 22 * u, c(0xFFFCFBF8)) }
            tape(cw / 2, py - 5 * u, 20 * u, 5.5f * u, -4f, c(0xCCF3E3A8))
            text(caption(""), cw / 2, py + ph + 10 * u, 5 * u, "hand", 700, c(0xFF2F2C28), 0)
        },
        FrameDef("instant_label", "Date Label", "拍立得日期標籤", INSTANT, "beach", "日期 標籤 拍立得") {
            margins(5.5f, 5.5f, 5.5f, 23f); paper(c(0xFFFCFBF8))
            val d = date("ymd_dot")
            if (d != null) { fill(px, ch - 12 * u, 26 * u, 6 * u, ORANGE, 0.8f * u); text(d, px + 13 * u, ch - 7.8f * u, 2.6f * u, "mono", 700, WHITE, 0, 0.12f) }
            text(caption(""), px + pw, ch - 7.8f * u, 4.4f * u, "hand", 700, c(0xFF2F2C28), 1)
        },
        // ------------------------------------------------------------------ Editorial
        FrameDef("mag_cover", "Magazine Cover", "封面式排版", EDITORIAL, "portrait", "封面 雜誌 cover") {
            margins(0f, 0f, 0f, 0f); paper(BLACK)
            grad(0f, 0f, pw, 30 * u, c(0x99000000), c(0x00000000), true)
            text(title("HIKARI").uppercase(), pw / 2, 19 * u, 17 * u, "serif", 700, WHITE, 0, 0.06f)
            text("ISSUE ${m.number.toString().padStart(2, '0')}", 5 * u, 25 * u, 1.9f * u, "sans", 700, WHITE, -1, 0.4f)
            text(date("mon_d_y"), pw - 5 * u, 25 * u, 1.9f * u, "sans", 700, WHITE, 1, 0.4f)
            grad(0f, ph - 30 * u, pw, 30 * u, c(0x00000000), c(0xB3000000), true)
            text(caption("The quiet light issue"), 5 * u, ph - 8 * u, 4f * u, "serif", 400, WHITE, -1, 0f, 0f, true)
        },
        FrameDef("mag_spread", "Feature Spread", "攝影雜誌跨頁", EDITORIAL, "city", "跨頁 專題 spread") {
            margins(7f, 7f, 46f, 7f); paper(c(0xFFFBFAF7))
            val x0 = px + pw + 6 * u
            text("FEATURE", x0, py + 2 * u, 1.6f * u, "sans", 700, RED, -1, 0.5f)
            text(title("On Light"), x0, py + 11 * u, 6.2f * u, "serif", 700, c(0xFF161616))
            line(x0, py + 14.5f * u, x0 + 12 * u, py + 14.5f * u, c(0xFF161616), 0.3f * u)
            text(caption("Photographed in available light."), x0, py + 20 * u, 2.1f * u, "serif", 400, c(0xFF4A4A4A), -1, 0f, 0f, true)
            for (i in 0 until 7) line(x0, py + 27 * u + i * 3.4f * u, cw - 7 * u - (if (i == 6) 12 * u else 0f), py + 27 * u + i * 3.4f * u, c(0xFFCFCBC2), 0.5f * u)
            text(info(), x0, py + ph - 4 * u, 1.6f * u, "mono", 400, GREY)
            text(date("d_mon_y"), x0, py + ph, 1.6f * u, "sans", 400, GREY, -1, 0.3f)
        },
        FrameDef("fig_caption", "Figure Caption", "編輯圖說", EDITORIAL, "foliage", "圖說 figure 編輯") {
            margins(7f, 7f, 7f, 17f); paper(WHITE)
            text("Fig. ${m.number.toString().padStart(2, '0')}", px, py + ph + 6 * u, 2.2f * u, "serif", 700, c(0xFF1A1A1A), -1, 0f, 0f, true)
            text(caption("Untitled study."), px + 12 * u, py + ph + 6 * u, 2.2f * u, "serif", 400, c(0xFF3A3A3A))
            text(listOfNotNull(date("ymd_dot"), info()).joinToString("   ·   ").ifEmpty { null }, px, py + ph + 11 * u, 1.6f * u, "sans", 400, GREY, -1, 0.1f)
        },
        FrameDef("title_block", "Headline Block", "照片標題框", EDITORIAL, "concrete", "標題 headline 大字") {
            margins(6f, 30f, 6f, 6f); paper(c(0xFFF6F4EF))
            text(title("CONCRETE").uppercase(), px, py - 9 * u, 11 * u, "condensed", 700, c(0xFF121212), -1, 0.02f)
            fill(px, py - 5.4f * u, 9 * u, 1.1f * u, RED)
            text(caption("A photo essay"), px + 11 * u, py - 4.6f * u, 2f * u, "sans", 400, c(0xFF4A4A4A), -1, 0.2f)
            text(date("ymd_dot"), px + pw, py - 4.6f * u, 1.8f * u, "sans", 400, GREY, 1, 0.2f)
        },
        FrameDef("portfolio", "Portfolio Plate", "作品集展示框", EDITORIAL, "white", "作品集 portfolio plate") {
            margins(11f, 15f, 11f, 15f); paper(WHITE)
            line(px, py - 6 * u, px + pw, py - 6 * u, c(0xFF1A1A1A), 0.14f * u); line(px, py + ph + 6 * u, px + pw, py + ph + 6 * u, c(0xFF1A1A1A), 0.14f * u)
            text("PORTFOLIO", px, py - 8 * u, 1.7f * u, "sans", 700, c(0xFF1A1A1A), -1, 0.6f)
            text("${m.number.toString().padStart(2, '0')} / 12", px + pw, py - 8 * u, 1.7f * u, "sans", 400, c(0xFF1A1A1A), 1, 0.3f)
            text(title("Untitled"), px, py + ph + 10.5f * u, 2.2f * u, "serif", 400, c(0xFF1A1A1A), -1, 0f, 0f, true)
            text(date("ymd_dot"), px + pw, py + ph + 10.5f * u, 1.7f * u, "sans", 400, GREY, 1, 0.3f)
        },
        FrameDef("gallery_label", "Museum Label", "展覽作品標籤", EDITORIAL, "seaside", "展覽 標籤 museum") {
            margins(13f, 13f, 13f, 32f); paper(c(0xFFF3F1EC)); shadow(px, py + u, pw, ph, 2.5f * u, c(0x44000000))
            val x0 = px + pw - 34 * u; val y0 = py + ph + 8 * u
            behind { fill(x0, y0, 34 * u, 17 * u, WHITE) }
            text(title("Untitled"), x0 + 2.5f * u, y0 + 5 * u, 2.4f * u, "sans", 700, c(0xFF1A1A1A))
            text(m.date.takeIf { o.showDate }?.year?.toString(), x0 + 2.5f * u, y0 + 9 * u, 1.9f * u, "sans", 400, c(0xFF4A4A4A))
            text(caption("Digital photograph"), x0 + 2.5f * u, y0 + 13 * u, 1.7f * u, "sans", 400, GREY, -1, 0f, 0f, true)
        },
        FrameDef("index_card", "Archive Index", "檔案索引卡", EDITORIAL, "overcast", "檔案 索引 archive") {
            margins(34f, 6f, 6f, 6f); paper(c(0xFFF4F0E4)); noise(0.02f)
            text("ARCHIVE", 6 * u, py + 3 * u, 2f * u, "mono", 700, c(0xFF2A2A2A), -1, 0.4f)
            line(6 * u, py + 5 * u, px - 6 * u, py + 5 * u, c(0xFF2A2A2A), 0.2f * u)
            val rows = listOfNotNull("NO." to m.number.toString().padStart(4, '0'), date("ymd_dash")?.let { "DATE" to it }, m.camera.ifEmpty { null }?.let { "CAM" to it },
                info()?.let { "EXP" to it }, m.place.ifEmpty { null }?.let { "LOC" to it })
            for ((i, kv) in rows.withIndex()) {
                text(kv.first, 6 * u, py + 10 * u + i * 6.4f * u, 1.5f * u, "mono", 400, GREY, -1, 0.2f)
                text(kv.second, 6 * u, py + 12.8f * u + i * 6.4f * u, 1.8f * u, "mono", 700, c(0xFF2A2A2A))
            }
        },
        FrameDef("newspaper", "Broadsheet", "報紙頭版", EDITORIAL, "city", "報紙 頭版 newspaper") {
            margins(6f, 24f, 6f, 11f); paper(c(0xFFF1EDE2)); noise(0.03f)
            text(title("The Daily Light"), cw / 2, py - 11 * u, 8 * u, "serif", 700, c(0xFF161616), 0)
            line(px, py - 8 * u, px + pw, py - 8 * u, c(0xFF161616), 0.5f * u); line(px, py - 7 * u, px + pw, py - 7 * u, c(0xFF161616), 0.14f * u)
            text(date("d_mon_y"), px, py - 3.4f * u, 1.7f * u, "serif", 400, c(0xFF333333), -1, 0.2f)
            text("VOL. ${m.number}   ·   LATE EDITION", px + pw, py - 3.4f * u, 1.7f * u, "serif", 400, c(0xFF333333), 1, 0.2f)
            line(px, py - 1.6f * u, px + pw, py - 1.6f * u, c(0xFF161616), 0.14f * u)
            text(caption("Light returns to the city."), px, ch - 5 * u, 2.3f * u, "serif", 400, c(0xFF2A2A2A), -1, 0f, 0f, true)
        },
        FrameDef("swiss_grid", "Swiss Grid", "瑞士風格網格", EDITORIAL, "concrete", "瑞士 網格 swiss 紅方塊") {
            margins(11f, 11f, 11f, 20f); paper(WHITE)
            fill(px, py + ph + 6 * u, 6 * u, 6 * u, c(0xFFE2231A))
            text(m.number.toString().padStart(2, '0'), px + 9 * u, py + ph + 12 * u, 7 * u, "sans", 700, c(0xFF111111), -1, -0.03f)
            text(title("Photographie"), px + pw, py + ph + 8.5f * u, 2.2f * u, "sans", 700, c(0xFF111111), 1)
            text(date("ymd_dot"), px + pw, py + ph + 12 * u, 1.7f * u, "sans", 400, c(0xFF111111), 1, 0.1f)
            for (i in 1..3) line(px + pw * i / 4, 3 * u, px + pw * i / 4, 7 * u, c(0xFF111111), 0.14f * u)
        },
        FrameDef("color_story", "Colour Story", "色票故事", EDITORIAL, "sunny", "色票 palette 配色") {
            margins(7f, 7f, 7f, 21f); paper(c(0xFFFCFBF8))
            chips(px, py + ph + 5 * u, 8 * u, 1.2f * u)
            for (i in 0 until 5) text("#%06X".format(m.palette[i] and 0xFFFFFF), px + i * 9.2f * u, py + ph + 16 * u, 1.3f * u, "mono", 400, GREY)
            text(title("Colour Story"), px + pw, py + ph + 9 * u, 2.2f * u, "serif", 400, c(0xFF1A1A1A), 1, 0f, 0f, true)
            text(date("ymd_dot"), px + pw, py + ph + 13 * u, 1.6f * u, "sans", 400, GREY, 1, 0.2f)
        },
        FrameDef("exhibition", "Exhibition Poster", "展覽海報", EDITORIAL, "white", "展覽 海報 exhibition") {
            margins(8f, 8f, 8f, 42f); paper(c(0xFFF4F2EC))
            text(title("LIGHT / FORM").uppercase(), px, py + ph + 15 * u, 10 * u, "condensed", 700, c(0xFF111111), -1, 0.01f)
            text(caption("Photographs"), px, py + ph + 22 * u, 3 * u, "serif", 400, c(0xFF111111), -1, 0f, 0f, true)
            line(px, py + ph + 26 * u, px + pw, py + ph + 26 * u, c(0xFF111111), 0.3f * u)
            text(date("d_mon_y"), px, py + ph + 31 * u, 2f * u, "sans", 700, c(0xFF111111), -1, 0.2f)
            text(m.place.ifEmpty { "HIKARI GALLERY" }, px + pw, py + ph + 31 * u, 2f * u, "sans", 400, c(0xFF111111), 1, 0.2f)
        },
        // ------------------------------------------------------------------ Creative
        FrameDef("retro_color", "Retro Pop", "復古彩色框", CREATIVE, "contrast", "復古 彩色 pop 芥末") {
            margins(9f, 9f, 9f, 9f); paper(c(0xFFD9A441))
            stroke(px - 3.2f * u, py - 3.2f * u, pw + 6.4f * u, ph + 6.4f * u, c(0xFFF6EBCF), 1.1f * u)
            for ((x, y) in listOf(3.5f * u to 3.5f * u, cw - 3.5f * u to 3.5f * u, 3.5f * u to ch - 3.5f * u, cw - 3.5f * u to ch - 3.5f * u)) oval(x - u, y - u, 2 * u, 2 * u, c(0xFFB5432E))
        },
        FrameDef("torn_paper", "Torn Paper", "撕紙邊框", CREATIVE, "foliage", "撕紙 毛邊 torn") {
            margins(9f, 9f, 9f, 12f); paper(c(0xFFFBF8F0)); noise(0.03f); torn(KRAFT, 2.6f * u)
            text(caption(""), cw / 2, ch - 5.4f * u, 3.6f * u, "hand", 700, c(0xFF3A342B), 0)
        },
        FrameDef("paper_texture", "Cotton Rag", "紙張紋理框", CREATIVE, "seaside", "紙張 紋理 棉紙 texture") {
            margins(12f, 12f, 12f, 14f); paper(c(0xFFEFE9DB)); noise(0.07f)
            stroke(px - 0.8f * u, py - 0.8f * u, pw + 1.6f * u, ph + 1.6f * u, c(0x33000000), 0.2f * u)
            text(date("ymd_dot"), cw / 2, ch - 5 * u, 1.6f * u, "serif", 400, c(0xFF7A7263), 0, 0.4f, 0f, true)
        },
        FrameDef("journal", "Travel Journal", "手帳風格", CREATIVE, "beach", "手帳 旅行 紙膠帶 journal") {
            margins(12f, 14f, 12f, 28f); paper(KRAFT); noise(0.05f)
            behind { fill(px - 2 * u, py - 2 * u, pw + 4 * u, ph + 4 * u, c(0xFFFBF8F0)) }
            for ((x, y, d) in listOf(Triple(px - 1 * u, py - 1 * u, -42f), Triple(px + pw + u, py - u, 42f), Triple(px - u, py + ph + u, 42f), Triple(px + pw + u, py + ph + u, -42f)))
                tape(x, y, 11 * u, 3.6f * u, d, c(0xCC9FC5BE))
            fill(px, py + ph + 8 * u, pw * 0.5f, 3 * u, c(0x99E7B5A5))
            text(caption("day ${m.number} — wandering"), px + u, py + ph + 16 * u, 4.6f * u, "hand", 700, c(0xFF3A2F22))
            text(date("ymd_dot"), px + pw, py + ph + 21 * u, 1.9f * u, "mono", 400, c(0xFF5E4E3A), 1, 0.2f)
        },
        FrameDef("collage", "Paper Collage", "拼貼風格", CREATIVE, "child", "拼貼 色塊 collage") {
            margins(14f, 14f, 14f, 14f); paper(c(0xFFF2EEE4))
            behind {
                fill(4 * u, 6 * u, cw * 0.55f, ch * 0.5f, m.palette[1]); fill(cw * 0.5f, ch * 0.45f, cw * 0.46f, ch * 0.5f, m.palette[3])
                oval(cw - 26 * u, 3 * u, 22 * u, 22 * u, m.palette[2])
                fill(px - 1.2f * u, py - 1.2f * u, pw + 2.4f * u, ph + 2.4f * u, WHITE)
            }
            tape(px + 8 * u, py - 1 * u, 14 * u, 4 * u, -8f, c(0xCCF1E2A6))
        },
        FrameDef("geometric", "Diagonal Bands", "幾何斜帶", CREATIVE, "concrete", "幾何 斜帶 geometric") {
            margins(10f, 10f, 10f, 10f); paper(c(0xFF1C1F2A))
            behind {
                poly(c(0xFFE9B44C), 0f, 0f, 26 * u, 0f, 0f, 26 * u); poly(c(0xFFE4572E), 30 * u, 0f, 38 * u, 0f, 0f, 38 * u, 0f, 30 * u)
                poly(c(0xFF76B7B2), cw, ch, cw - 26 * u, ch, cw, ch - 26 * u); poly(c(0xFFE9B44C), cw - 30 * u, ch, cw - 38 * u, ch, cw, ch - 38 * u, cw, ch - 30 * u)
            }
        },
        FrameDef("blur_bg", "Blurred Backdrop", "模糊背景延伸", CREATIVE, "nightstreet", "模糊 背景 延伸 blur") {
            margins(12f, 12f, 12f, 12f); paper(BLACK); blurBg = true; radius = 2 * u
            shadow(px, py + 1.4f * u, pw, ph, 4 * u, c(0x88000000), 2 * u)
        },
        FrameDef("gradient_palette", "Palette Gradient", "照片配色漸層", CREATIVE, "sunny", "漸層 配色 gradient") {
            margins(11f, 11f, 11f, 16f); paper(m.palette[0]); radius = 1.6f * u
            behind { grad(0f, 0f, cw, ch, m.palette[1], m.palette[3], true) }
            chips(cw / 2 - 9.6f * u, ch - 9 * u, 3 * u, 1.2f * u, true)
        },
        FrameDef("postage", "Postage Stamp", "郵票齒孔", CREATIVE, "seaside", "郵票 齒孔 postage") {
            margins(9f, 9f, 9f, 17f); paper(c(0xFFFBF7EC)); val out = c(0xFF2F4858)
            val d = 3.2f * u
            var x = d / 2; while (x < cw) { oval(x - d / 2, -d / 2, d, d, out); oval(x - d / 2, ch - d / 2, d, d, out); x += d * 1.45f }
            var y = d / 2; while (y < ch) { oval(-d / 2, y - d / 2, d, d, out); oval(cw - d / 2, y - d / 2, d, d, out); y += d * 1.45f }
            stroke(px - 1.4f * u, py - 1.4f * u, pw + 2.8f * u, ph + 2.8f * u, c(0xFF2F4858), 0.2f * u)
            text(title("HIKARI POST").uppercase(), px, ch - 6.5f * u, 2.4f * u, "serif", 700, c(0xFF2F4858), -1, 0.3f)
            text("${(m.number % 9 + 1) * 10}", px + pw, ch - 6 * u, 5 * u, "serif", 700, c(0xFF2F4858), 1)
        },
        FrameDef("ticket", "Admit One", "票根", CREATIVE, "night", "票根 ticket 入場券") {
            margins(6f, 6f, 32f, 6f); paper(c(0xFFF3E4C4)); noise(0.03f)
            val x0 = px + pw + 5 * u
            var y = 2 * u; while (y < ch) { oval(x0 - 0.5f * u, y, u, u, c(0xFF8A7650)); y += 2.4f * u }
            text("ADMIT ONE", x0 + 12.5f * u, 4 * u, 3.4f * u, "condensed", 700, c(0xFF7A2E22), -1, 0.35f, 90f)
            barcode(x0 + 4 * u, ch - 14 * u, 18 * u, 8 * u, c(0xFF3A2F22))
            text("No. ${m.number.toString().padStart(5, '0')}", x0 + 4 * u, ch - 3 * u, 1.7f * u, "mono", 700, c(0xFF3A2F22), -1, 0.15f)
            text(date("ymd_dot"), x0 + 4 * u, ch - 17 * u, 1.7f * u, "mono", 400, c(0xFF3A2F22), -1, 0.15f)
        },
        FrameDef("graph_paper", "Graph Paper", "方格紙剪貼", CREATIVE, "foliage", "方格紙 剪貼 貼紙") {
            margins(12f, 12f, 12f, 16f); paper(c(0xFFF7FAF8))
            behind {
                var x = 0f; while (x < cw) { line(x, 0f, x, ch, c(0x4078B7A5), 0.1f * u); x += 3 * u }
                var y = 0f; while (y < ch) { line(0f, y, cw, y, c(0x4078B7A5), 0.1f * u); y += 3 * u }
                fill(px - 1.4f * u, py - 1.4f * u, pw + 2.8f * u, ph + 2.8f * u, WHITE)
            }
            oval(px + pw - 9 * u, py + ph - 5 * u, 13 * u, 13 * u, c(0xFFF2C14E))
            text(m.number.toString().padStart(2, '0'), px + pw - 2.5f * u, py + ph + 3.6f * u, 5 * u, "sans", 700, c(0xFF2A2A2A), 0)
        },
        FrameDef("neon", "Neon Outline", "霓虹外框", CREATIVE, "nightstreet", "霓虹 neon 外框") {
            margins(10f, 10f, 10f, 10f); paper(c(0xFF0B0B12))
            stroke(px - 3 * u, py - 3 * u, pw + 6 * u, ph + 6 * u, c(0x66FF3D9A), 1.6f * u, 2 * u)
            stroke(px - 3 * u, py - 3 * u, pw + 6 * u, ph + 6 * u, c(0xFFFF7AC0), 0.4f * u, 2 * u)
            stroke(px - 5.4f * u, py - 5.4f * u, pw + 10.8f * u, ph + 10.8f * u, c(0xFF38E8FF), 0.25f * u, 3 * u)
        },
        FrameDef("label_maker", "Label Maker", "打標機標籤", CREATIVE, "overcast", "打標機 標籤 embossed") {
            margins(6f, 6f, 6f, 19f); paper(c(0xFFEDEBE6))
            val s = title(date("ymd_dot") ?: "HIKARI").uppercase()
            fill(px, ch - 12.5f * u, (s.length * 2.9f + 4f) * u, 6.4f * u, c(0xFF1D1D1F), 0.5f * u)
            text(s, px + 2 * u, ch - 8 * u, 3.2f * u, "mono", 700, WHITE, -1, 0.3f)
        },
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String?): FrameDef? = byId[id]
    fun inCategory(cat: FrameCat) = all.filter { it.cat == cat }
    /** frames whose name or keywords contain every word of [q] */
    fun search(q: String, within: List<FrameDef> = all): List<FrameDef> {
        val words = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return within
        return within.filter { f -> val hay = "${f.name} ${f.zh} ${f.keywords} ${f.id}".lowercase(); words.all { it in hay } }
    }
}
