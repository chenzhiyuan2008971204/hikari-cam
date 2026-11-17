package com.hikari.studio

import java.time.LocalDateTime

/** How a date is written. [numeric] formats can be shown with the seven-segment LED digits. */
class DateFormat(val id: String, val label: String, val hasTime: Boolean, val f: (LocalDateTime, Boolean) -> String)

object DateFormats {
    private val MON = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private fun p(v: Int) = v.toString().padStart(2, '0')
    private fun yy(t: LocalDateTime) = p(t.year % 100)
    private fun hm(t: LocalDateTime, s: Boolean) = "${p(t.hour)}:${p(t.minute)}" + if (s) ":${p(t.second)}" else ""
    val all = listOf(
        DateFormat("ymd_dot", "YYYY.MM.DD", false) { t, _ -> "${t.year}.${p(t.monthValue)}.${p(t.dayOfMonth)}" },
        DateFormat("ymd_slash", "YYYY/MM/DD", false) { t, _ -> "${t.year}/${p(t.monthValue)}/${p(t.dayOfMonth)}" },
        DateFormat("yymd_dot", "YY.MM.DD", false) { t, _ -> "${yy(t)}.${p(t.monthValue)}.${p(t.dayOfMonth)}" },
        DateFormat("mdy_slash", "MM/DD/YYYY", false) { t, _ -> "${p(t.monthValue)}/${p(t.dayOfMonth)}/${t.year}" },
        DateFormat("dmy_dot", "DD.MM.YYYY", false) { t, _ -> "${p(t.dayOfMonth)}.${p(t.monthValue)}.${t.year}" },
        DateFormat("ymd_dash", "YYYY-MM-DD", false) { t, _ -> "${t.year}-${p(t.monthValue)}-${p(t.dayOfMonth)}" },
        DateFormat("hm", "HH:mm", true) { t, _ -> hm(t, false) },
        DateFormat("hms", "HH:mm:ss", true) { t, _ -> hm(t, true) },
        DateFormat("ymd_dot_hm", "YYYY.MM.DD HH:mm", true) { t, s -> "${t.year}.${p(t.monthValue)}.${p(t.dayOfMonth)} ${hm(t, s)}" },
        DateFormat("ymd_slash_hms", "YYYY/MM/DD HH:mm:ss", true) { t, _ -> "${t.year}/${p(t.monthValue)}/${p(t.dayOfMonth)} ${hm(t, true)}" },
        // the way compact cameras printed dates on the negative
        DateFormat("retro_yy_m_d", "'YY MM DD（復古）", false) { t, _ -> "'${yy(t)} ${p(t.monthValue)} ${p(t.dayOfMonth)}" },
        DateFormat("retro_d_m_yy", "DD MM 'YY（復古）", false) { t, _ -> "${p(t.dayOfMonth)} ${p(t.monthValue)} '${yy(t)}" },
        DateFormat("mon_d_y", "NOV 16 2026", false) { t, _ -> "${MON[t.monthValue - 1]} ${p(t.dayOfMonth)} ${t.year}" },
        DateFormat("d_mon_y", "16 NOV 2026", false) { t, _ -> "${p(t.dayOfMonth)} ${MON[t.monthValue - 1]} ${t.year}" },
        DateFormat("y_m_d_space", "2026 11 16", false) { t, _ -> "${t.year} ${p(t.monthValue)} ${p(t.dayOfMonth)}" },
    )
    fun get(id: String) = all.firstOrNull { it.id == id } ?: all[0]
    fun format(id: String, t: LocalDateTime, seconds: Boolean = false) = get(id).f(t, seconds)
}

enum class TimeSource(val zh: String) { EXIF("拍攝時間（EXIF）"), CREATED("照片建立時間"), NOW("目前時間"), CUSTOM("自訂日期時間") }

/** What is known about when a photo was made. */
class PhotoTimes(val exif: LocalDateTime?, val created: LocalDateTime?, val now: LocalDateTime)

class ResolvedTime(val time: LocalDateTime?, val source: TimeSource,
                   /** set when nothing can be shown until the user decides */
                   val needsChoice: String? = null)

object Times {
    /**
     * The time a stamp shows. It is exactly the source the user chose: when that source has no value
     * (no EXIF capture time, say) the result is empty with an explanation - it never falls back to
     * another clock on its own, because a wrong date on a photo is worse than none.
     */
    fun resolve(source: TimeSource, t: PhotoTimes, custom: LocalDateTime?): ResolvedTime = when (source) {
        TimeSource.EXIF -> if (t.exif != null) ResolvedTime(t.exif, source)
            else ResolvedTime(null, source, "這張照片沒有 EXIF 拍攝時間。請改選其他時間來源（照片建立時間、目前時間或自訂），時間戳才會顯示。")
        TimeSource.CREATED -> if (t.created != null) ResolvedTime(t.created, source)
            else ResolvedTime(null, source, "讀不到這張照片的建立時間，請改選其他時間來源。")
        TimeSource.NOW -> ResolvedTime(t.now, source)
        TimeSource.CUSTOM -> if (custom != null) ResolvedTime(custom, source) else ResolvedTime(null, source, "請設定自訂的日期與時間。")
    }
    /** EXIF "yyyy:MM:dd HH:mm:ss" (DateTimeOriginal); null when absent or not a real date */
    fun parseExif(s: String?): LocalDateTime? {
        val m = Regex("""^(\d{4})[:\-](\d{2})[:\-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})""").find(s?.trim() ?: return null) ?: return null
        val (y, mo, d, h, mi, se) = m.destructured
        if (y.toInt() < 1900) return null
        return runCatching { LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), se.toInt()) }.getOrNull()
    }
}

enum class StampPos(val zh: String) { BL("左下"), BR("右下"), TL("左上"), TR("右上"), BC("中下"), CUSTOM("自訂") }
enum class StampContent(val zh: String) { DATE("日期"), TEXT("自訂文字"), DATE_TEXT("日期＋文字"), PLACE_DATE("地點＋日期") }

/** Everything the user can set about a stamp; plain values so it can be stored with the edit. */
data class StampSpec(
    val enabled: Boolean = false,
    val style: String = "digital_orange",
    val content: StampContent = StampContent.DATE,
    val format: String = "retro_yy_m_d",
    val seconds: Boolean = false,
    val source: TimeSource = TimeSource.EXIF,
    /** ISO local date-time for TimeSource.CUSTOM */
    val custom: String = "",
    val text: String = "",
    /** typed by the user (or filled from the location they allowed) */
    val place: String = "",
    val font: String = "led",
    /** height of the text as a fraction of the photo's short side */
    val size: Float = 0.034f,
    val weight: Int = 700,
    val color: Int = 0xFFFF8A1E.toInt(),
    val opacity: Float = 0.95f,
    val spacing: Float = 0.06f,
    /** 0 = no box behind the text */
    val bg: Int = 0,
    /** distance from the photo edge as a fraction of the short side */
    val margin: Float = 0.045f,
    val pos: StampPos = StampPos.BR,
    val x: Float = 0.5f, val y: Float = 0.9f,
    val rotation: Float = 0f,
) {
    fun customTime(): LocalDateTime? = runCatching { LocalDateTime.parse(custom) }.getOrNull()
    fun encode(): String = listOf(enabled, style, content.name, format, seconds, source.name, esc(custom), esc(text), esc(place), font, size, weight, color,
        opacity, spacing, bg, margin, pos.name, x, y, rotation).joinToString("|")
    companion object {
        private fun esc(s: String) = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", " ")
        private fun un(s: String) = s.replace("\\p", "|").replace("\\\\", "\\")
        fun decode(s: String?): StampSpec = runCatching {
            val p = s!!.split("|")
            StampSpec(p[0].toBoolean(), p[1], StampContent.valueOf(p[2]), p[3], p[4].toBoolean(), TimeSource.valueOf(p[5]), un(p[6]), un(p[7]), un(p[8]), p[9],
                p[10].toFloat(), p[11].toInt(), p[12].toInt(), p[13].toFloat(), p[14].toFloat(), p[15].toInt(), p[16].toFloat(), StampPos.valueOf(p[17]),
                p[18].toFloat(), p[19].toFloat(), p[20].toFloat())
        }.getOrDefault(StampSpec())
    }
}

/** A ready-made look for the stamp; picking one sets the visual fields and keeps the user's content and time source. */
class StampStyle(val id: String, val zh: String, val apply: (StampSpec) -> StampSpec)

object StampStyles {
    private fun c(v: Long) = v.toInt()
    val all = listOf(
        StampStyle("digital_orange", "復古數位相機橘色") { it.copy(font = "led", color = c(0xFFFF8A1E), weight = 700, size = 0.034f, spacing = 0.06f, bg = 0, opacity = 0.95f, format = "retro_yy_m_d", pos = StampPos.BR, rotation = 0f) },
        StampStyle("led_red", "紅色 LED 數位日期") { it.copy(font = "led", color = c(0xFFFF2A1A), weight = 700, size = 0.038f, spacing = 0.08f, bg = 0, opacity = 0.95f, format = "ymd_dot", pos = StampPos.BR, rotation = 0f) },
        StampStyle("film_classic", "經典底片日期") { it.copy(font = "mono", color = c(0xFFFFB347), weight = 700, size = 0.03f, spacing = 0.12f, bg = 0, opacity = 0.9f, format = "retro_d_m_yy", pos = StampPos.BR, rotation = 0f) },
        StampStyle("japan", "日系相機日期") { it.copy(font = "sans", color = c(0xFFFFFFFF), weight = 400, size = 0.026f, spacing = 0.18f, bg = 0, opacity = 0.9f, format = "ymd_dot", pos = StampPos.BL, rotation = 0f) },
        StampStyle("min_white", "極簡白色文字") { it.copy(font = "sans", color = c(0xFFFFFFFF), weight = 400, size = 0.022f, spacing = 0.1f, bg = 0, opacity = 1f, format = "ymd_dash", pos = StampPos.BC, rotation = 0f) },
        StampStyle("min_black", "極簡黑色文字") { it.copy(font = "sans", color = c(0xFF111111), weight = 400, size = 0.022f, spacing = 0.1f, bg = 0, opacity = 1f, format = "ymd_dash", pos = StampPos.BC, rotation = 0f) },
        StampStyle("retro_yellow", "復古黃色文字") { it.copy(font = "mono", color = c(0xFFFFD84A), weight = 700, size = 0.036f, spacing = 0.04f, bg = 0, opacity = 0.95f, format = "mdy_slash", pos = StampPos.BR, rotation = 0f) },
        StampStyle("cinema", "電影資訊文字") { it.copy(font = "condensed", color = c(0xFFFFFFFF), weight = 700, size = 0.026f, spacing = 0.22f, bg = c(0x73000000), opacity = 1f, format = "d_mon_y", pos = StampPos.BL, rotation = 0f) },
        StampStyle("roll_no", "膠卷編號風格") { it.copy(font = "mono", color = c(0xFFFF9A3C), weight = 700, size = 0.024f, spacing = 0.2f, bg = 0, opacity = 0.9f, format = "y_m_d_space", pos = StampPos.TL, rotation = 0f) },
        StampStyle("mini_info", "迷你相機資訊") { it.copy(font = "sans", color = c(0xFFFFFFFF), weight = 400, size = 0.017f, spacing = 0.06f, bg = c(0x59000000), opacity = 1f, format = "ymd_dot_hm", pos = StampPos.TR, rotation = 0f) },
    )
    fun get(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

object Stamps {
    /** The words on the stamp, or null when there is nothing to show (no time known for a date stamp, empty text). */
    fun text(s: StampSpec, time: LocalDateTime?, info: String = "", number: Int = 0): String? {
        val date = time?.let { DateFormats.format(s.format, it, s.seconds) }
        val base = when (s.content) {
            StampContent.DATE -> date
            StampContent.TEXT -> s.text.trim().ifEmpty { null }
            StampContent.DATE_TEXT -> if (date == null) null else listOf(date, s.text.trim()).filter { it.isNotEmpty() }.joinToString("  ")
            StampContent.PLACE_DATE -> if (date == null) null else listOf(s.place.trim(), date).filter { it.isNotEmpty() }.joinToString("  ")
        } ?: return null
        return when (s.style) {
            "roll_no" -> "▸ ${number.coerceAtLeast(1).toString().padStart(2, '0')}A   $base"
            "mini_info" -> listOf(base, info).filter { it.isNotEmpty() }.joinToString("  ·  ")
            else -> base
        }
    }

    /** Baseline anchor of the stamp inside the photo rectangle: x, y, alignment. */
    fun anchor(s: StampSpec, photo: R): Triple<Float, Float, Int> {
        val short = minOf(photo.w, photo.h); val m = s.margin * short; val size = s.size * short
        return when (s.pos) {
            StampPos.BL -> Triple(photo.x + m, photo.bottom - m, -1)
            StampPos.BR -> Triple(photo.right - m, photo.bottom - m, 1)
            StampPos.BC -> Triple(photo.x + photo.w / 2, photo.bottom - m, 0)
            StampPos.TL -> Triple(photo.x + m, photo.y + m + size, -1)
            StampPos.TR -> Triple(photo.right - m, photo.y + m + size, 1)
            StampPos.CUSTOM -> Triple(photo.x + s.x.coerceIn(0f, 1f) * photo.w, photo.y + s.y.coerceIn(0f, 1f) * photo.h, 0)
        }
    }

    /** Drawing steps for the stamp on a photo that occupies [photo] in the output. */
    fun prims(s: StampSpec, text: String?, photo: R): List<Prim> {
        if (!s.enabled || text.isNullOrEmpty()) return emptyList()
        val short = minOf(photo.w, photo.h); val size = s.size * short
        val (ax, ay, align) = anchor(s, photo)
        val color = withAlpha(s.color, s.opacity)
        // LED digits are our own polygons (same on every device); they cannot be rotated or boxed by the text backend
        if (s.font == "led" && SevenSeg.supports(text) && s.rotation == 0f) {
            val w = SevenSeg.width(text, size, s.spacing)
            val x = when (align) { -1 -> ax; 0 -> ax - w / 2; else -> ax - w }
            val out = ArrayList<Prim>()
            if (s.bg != 0) out += Fill(x - size * 0.3f, ay - size * 1.25f, w + size * 0.6f, size * 1.5f, s.bg, size * 0.12f)
            out += SevenSeg.polys(text, x, ay, size, color, s.spacing)
            return out
        }
        val font = if (s.font == "led") "mono" else s.font
        return listOf(TextP(text, ax, ay, size, font, s.weight, color, align, s.spacing, s.rotation, s.bg, size * 0.3f))
    }
}
