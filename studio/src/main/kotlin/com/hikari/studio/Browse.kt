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
