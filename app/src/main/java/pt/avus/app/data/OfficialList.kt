package pt.avus.app.data

/**
 * The national list of ERPI bundled with the app (assets/erpi_portugal.csv), collected from the
 * Carta Social by tools/cartasocial/scrape_erpi.py. It is loaded on first launch and refreshed
 * when an app update ships a newer list, unless the user removed it.
 */
object OfficialList {
    const val CSV_ASSET = "erpi_portugal.csv"
    const val META_ASSET = "erpi_portugal.properties"
    const val SOURCE = "Carta Social (DGSSS)"

    /** [date] is when the list was collected (yyyy-MM-dd). */
    data class Meta(val date: String, val count: Int)

    fun parseMeta(text: String): Meta {
        val props = text.lines().mapNotNull { line ->
            line.substringBefore('#').split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }.toMap()
        return Meta(date = props["date"].orEmpty(), count = props["count"]?.toIntOrNull() ?: 0)
    }

    /** What the user has done with the official list so far (persisted as "installed|removed <date>"). */
    data class State(val removed: Boolean, val date: String) {
        fun serialize() = (if (removed) "removed " else "installed ") + date

        companion object {
            fun parse(text: String?): State? {
                val parts = text?.trim()?.split(' ', limit = 2)?.takeIf { it.size == 2 } ?: return null
                return when (parts[0]) {
                    "installed" -> State(removed = false, date = parts[1])
                    "removed" -> State(removed = true, date = parts[1])
                    else -> null
                }
            }
        }
    }

    /** Install on first launch, or refresh when the bundled list is newer — never after the user removed it. */
    fun shouldInstall(state: State?, bundled: Meta): Boolean =
        state == null || (!state.removed && bundled.date > state.date)

    /**
     * Merges a fresh official list into the user's homes:
     * - official homes are replaced by the new data (keeping an email the user added if the list has none);
     * - homes the user added or imported are kept as they are;
     * - official homes no longer in the list are dropped, unless in [keepIds] (selected or contacted).
     */
    fun merge(current: List<CareHome>, official: List<CareHome>, keepIds: Set<String>): List<CareHome> {
        val currentById = current.associateBy { it.id }
        val fresh = official.map { new ->
            val old = currentById[new.id]
            new.copy(
                official = true,
                manual = false,
                email = new.email.ifBlank { old?.email.orEmpty() },
            )
        }
        val freshIds = fresh.map { it.id }.toSet()
        val kept = current.filter { it.id !in freshIds && (!it.official || it.id in keepIds) }
        return (kept + fresh).sortedBy { normalizeForSearch(it.name) }
    }
}
