package com.hikari.studio

/** One filter as the category browser sees it. Ids are the filters' own, never changed here. */
class LookItem(val id: String, val name: String, val zh: String, val series: String)
class LookCategory(val id: String, val title: String, val count: Int)
enum class LookSort(val zh: String) { DEFAULT("預設順序"), NAME("名稱"), RECENT("最近使用") }

/** Category browsing: one category at a time, in the app's own category order. */
object Browse {
    const val ALL = "all"; const val FAV = "fav"
    /** "all" and "favourites" first, then every existing category that has filters, in the given order */
    fun categories(order: List<Pair<String, String>>, items: List<LookItem>, favs: Set<String>): List<LookCategory> =
        listOf(LookCategory(ALL, "全部濾鏡", items.size), LookCategory(FAV, "收藏", items.count { it.id in favs })) +
            order.map { (id, title) -> LookCategory(id, title, items.count { it.series == id }) }.filter { it.count > 0 }

    /** The filters of exactly one category (never mixed), narrowed by the search words, in the chosen order. */
    fun list(items: List<LookItem>, category: String, favs: Set<String>, query: String = "", sort: LookSort = LookSort.DEFAULT, recents: List<String> = emptyList()): List<LookItem> {
        val inCat = when (category) { ALL -> items; FAV -> items.filter { it.id in favs }; else -> items.filter { it.series == category } }
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val hit = if (words.isEmpty()) inCat else inCat.filter { i -> val hay = "${i.name} ${i.zh} ${i.id}".lowercase(); words.all { it in hay } }
        return when (sort) {
            LookSort.DEFAULT -> hit
            LookSort.NAME -> hit.sortedBy { it.name.lowercase() }
            LookSort.RECENT -> hit.sortedBy { val k = recents.indexOf(it.id); if (k < 0) Int.MAX_VALUE else k }
        }
    }
    /** a stored category that no longer exists falls back to "all" */
    fun valid(category: String, cats: List<LookCategory>) = if (cats.any { it.id == category }) category else ALL

    fun csvAdd(csv: String, id: String, max: Int = 24) = (listOf(id) + csv.split(",").filter { it.isNotEmpty() && it != id }).take(max).joinToString(",")
    fun csvToggle(csv: String, id: String) = csv.split(",").filter { it.isNotEmpty() }.let { if (id in it) it - id else listOf(id) + it }.joinToString(",")
    fun csvList(csv: String) = csv.split(",").filter { it.isNotEmpty() }
}

/** Which standard photo shows a look at its best: a portrait look on a face, a night look on lights, and so on. */
object LookDemo {
    private val rules = listOf(
        listOf("night", "800t", "bloom", "neon", "blue_hour", "tokyo_night", "flash") to "nightstreet",
        listOf("portrait", "proneg", "pro neg", "astia", "portra", "pastel", "milky", "airy", "studio", "soft", "nostalg", "skin") to "portrait",
        listOf("child", "instant", "disposable", "90s", "2000s", "ccd", "diary", "dream") to "child",
        listOf("landscape", "velvia", "ektar", "vivid", "rich", "green", "foliage", "fuji400", "fuji 400") to "foliage",
        listOf("arch", "mono", "acros", "bleach", "bypass", "deep", "editorial", "concrete") to "concrete",
        listOf("street", "urban", "classic", "chrome", "2383", "teal", "cold", "eterna", "cine", "kyoto") to "overcast",
        listOf("golden", "warm", "gold", "summer", "beach", "faded", "vintage", "mellow") to "beach",
        listOf("blue", "clean", "daylight", "provia", "natural", "sky", "clear") to "sunny",
    )
    /** a photo of the bundled demo pool (assets/japan) with what it shows: night, sakura, shrine ... */
    data class Photo(val file: String, val tags: Set<String>)

    // which kinds of photo show a look at its best, most suitable first
    private val photoRules = listOf(
        listOf("night", "800t", "bloom", "neon", "blue_hour", "tokyo_night", "flash") to listOf("night"),
        listOf("mono", "acros", "silver", "bleach", "bypass", "true_negative") to listOf("shrine", "alley", "city", "street"),
        listOf("portrait", "proneg", "pro neg", "astia", "portra", "pastel", "milky", "airy", "studio", "soft", "nostalg", "dream") to listOf("sakura", "cafe", "flower", "food"),
        listOf("instant", "disposable", "90s", "2000s", "ccd", "diary", "compact") to listOf("food", "alley", "street", "rail"),
        listOf("landscape", "velvia", "ektar", "vivid", "rich", "green", "foliage", "fuji400", "fuji 400") to listOf("green", "garden", "mountain", "sea"),
        listOf("arch", "deep", "editorial", "concrete") to listOf("city", "shrine", "alley"),
        listOf("kyoto") to listOf("shrine", "alley", "garden"),
        listOf("street", "urban", "classic", "chrome", "2383", "teal", "cold", "eterna", "cine") to listOf("street", "rail", "alley", "city"),
        listOf("golden", "warm", "gold", "summer", "beach", "faded", "vintage", "mellow", "grain") to listOf("sea", "autumn", "street", "food"),
        listOf("blue", "clean", "daylight", "provia", "natural", "sky", "clear", "standard") to listOf("sea", "mountain", "house", "street"),
    )

    /** kinds of photo that suit a look, most suitable first */
    fun wants(id: String, name: String): List<String> {
        val hay = "$id ${name.lowercase()}"
        return photoRules.firstOrNull { (words, _) -> words.any { it in hay } }?.second ?: listOf("street", "house")
    }

    /**
     * One photo per look (look id → file): each look gets a photo of the kind that shows its
     * character, and no photo is used twice while the pool lasts. Deterministic for the same input.
     */
    fun assign(looks: List<Pair<String, String>>, photos: List<Photo>): Map<String, String> {
        if (photos.isEmpty()) return emptyMap()
        val used = HashMap<String, Int>()
        val out = LinkedHashMap<String, String>()
        val ruleUse = HashMap<List<String>, Int>()
        for ((id, name) in looks) {
            // looks that share a rule take turns at its kinds of photo, so a run of similar looks
            // shows ramen, an alley, a platform ... rather than four bowls of ramen
            val base = wants(id, name)
            val turn = ruleUse.merge(base, 1, Int::plus)!! - 1
            val want = base.indices.map { base[(it + turn) % base.size] }
            fun score(p: Photo): Int {
                // night photos are kept for night looks: a daylight look is not judged on a dark street
                val rank = if ("night" in p.tags && "night" !in want) want.size + 2
                    else want.indexOfFirst { it in p.tags }.let { if (it < 0) want.size + 1 else it }
                return (used[p.file] ?: 0) * 100 + rank
            }
            val pick = photos.minWith(compareBy<Photo>({ score(it) }, { photos.indexOf(it) }))
            used[pick.file] = (used[pick.file] ?: 0) + 1
            out[id] = pick.file
        }
        return out
    }

    /** sample id for a look, from its id and name; "sunny" when nothing matches */
    fun sample(id: String, name: String): String {
        val hay = "$id ${name.lowercase()}"
        return rules.firstOrNull { (words, _) -> words.any { it in hay } }?.second ?: "sunny"
    }
}
