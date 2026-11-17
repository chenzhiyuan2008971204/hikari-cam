package com.hikari.data

/**
 * Typed view of the settings table. Unknown or missing keys fall back to the defaults here, so
 * the app works on a fresh install and after upgrades.
 */
data class UserSettings(
    val jpegQuality: Int = 97,
    /** ask the camera for no sharpening and its best noise reduction (natural detail) */
    val naturalDetail: Boolean = true,
    /** also keep the unfiltered camera JPEG (Pictures/Hikari/Originals) */
    val saveOriginal: Boolean = true,
    /** also save a DNG when the camera supports RAW */
    val saveRaw: Boolean = false,
    val grid: Boolean = false,
    /** "3:4", "2:3", "9:16" or "1:1" (portrait width:height) */
    val aspect: String = "3:4",
    val lastLook: String = "mellow400",
    val cameraAdjustJson: String = "",
    /** composition guide id over the camera preview, "" = none */
    val composition: String = "",
    /** print "5 月 · 12:08pm · note" on photos */
    val dateStamp: Boolean = false,
    /** the user's own words after the time, e.g. "晴 / Hokkaido" */
    val stampNote: String = "",
    /** play the bundled test photos instead of the real camera (testing without a phone camera) */
    val virtualCamera: Boolean = false,
    /** which bundled photo the filter thumbnails are rendered from */
    val thumbScene: String = "auto",
    /** show the three recommended looks for what the camera sees */
    val recommend: Boolean = true,
    /** the filter category shown last: "all", "fav" or a series id */
    val filterCategory: String = "all",
    /** favourite and recently used frame ids, comma separated (newest first) */
    val frameFavs: String = "",
    val frameRecents: String = "",
    /** the timestamp look used for the camera's date stamp (encoded StampSpec), "" = classic */
    val stampSpec: String = "",
    /** the camera recipe in use: 0 = none (the camera's own settings), 1..7 = C1..C7 */
    val recipeSlot: Int = 0,
) {
    fun toRows(): List<Setting> = listOf(
        Setting("filterCategory", filterCategory), Setting("frameFavs", frameFavs), Setting("frameRecents", frameRecents), Setting("stampSpec", stampSpec),
        Setting("recipeSlot", recipeSlot.toString()),
        Setting("jpegQuality", jpegQuality.toString()),
        Setting("naturalDetail", naturalDetail.toString()),
        Setting("saveOriginal", saveOriginal.toString()),
        Setting("saveRaw", saveRaw.toString()),
        Setting("grid", grid.toString()),
        Setting("aspect", aspect),
        Setting("lastLook", lastLook),
        Setting("cameraAdjust", cameraAdjustJson),
        Setting("composition", composition),
        Setting("dateStamp", dateStamp.toString()),
        Setting("stampNote", stampNote),
        Setting("virtualCamera", virtualCamera.toString()),
        Setting("thumbPhoto", thumbScene),
        Setting("recommend", recommend.toString()),
    )

    companion object {
        val ASPECTS = listOf("3:4", "2:3", "9:16", "1:1")

        fun from(rows: List<Setting>): UserSettings {
            val m = rows.associate { it.key to it.value }
            val d = UserSettings()
            return UserSettings(
                jpegQuality = m["jpegQuality"]?.toIntOrNull()?.coerceIn(70, 100) ?: d.jpegQuality,
                naturalDetail = m["naturalDetail"]?.toBooleanStrictOrNull() ?: d.naturalDetail,
                saveOriginal = m["saveOriginal"]?.toBooleanStrictOrNull() ?: d.saveOriginal,
                saveRaw = m["saveRaw"]?.toBooleanStrictOrNull() ?: d.saveRaw,
                grid = m["grid"]?.toBooleanStrictOrNull() ?: d.grid,
                aspect = m["aspect"]?.takeIf { it in ASPECTS } ?: d.aspect,
                lastLook = m["lastLook"] ?: d.lastLook,
                cameraAdjustJson = m["cameraAdjust"] ?: d.cameraAdjustJson,
                composition = m["composition"] ?: d.composition,
                dateStamp = m["dateStamp"]?.toBooleanStrictOrNull() ?: d.dateStamp,
                stampNote = m["stampNote"] ?: d.stampNote,
                virtualCamera = m["virtualCamera"]?.toBooleanStrictOrNull() ?: d.virtualCamera,
                thumbScene = m["thumbPhoto"] ?: d.thumbScene,   // new key: everyone starts on "a photo that suits each look"
                recommend = m["recommend"]?.toBooleanStrictOrNull() ?: d.recommend,
                filterCategory = m["filterCategory"] ?: d.filterCategory,
                frameFavs = m["frameFavs"] ?: d.frameFavs,
                frameRecents = m["frameRecents"] ?: d.frameRecents,
                stampSpec = m["stampSpec"] ?: d.stampSpec,
                recipeSlot = m["recipeSlot"]?.toIntOrNull()?.takeIf { it in 0..7 } ?: d.recipeSlot,
            )
        }

        /** width / height of a portrait aspect string */
        fun ratio(aspect: String): Float {
            val (w, h) = aspect.split(':').map { it.toFloat() }
            return w / h
        }
    }
}
