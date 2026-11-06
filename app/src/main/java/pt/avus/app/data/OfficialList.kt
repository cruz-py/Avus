package pt.avus.app.data

/**
 * The national list of ERPI bundled with the app (assets/erpi_portugal.csv), collected from the
 * Carta Social by tools/cartasocial/scrape_erpi.py and maintained by the app's publisher. It is
 * loaded on first launch and refreshed when an app update ships a newer list.
 */
object OfficialList {
    const val CSV_ASSET = "erpi_portugal.csv"
    const val META_ASSET = "erpi_portugal.properties"
    const val SOURCE = "Carta Social (DGSSS)"

    /**
     * [date] is when the list was collected (yyyy-MM-dd, shown to users); [revision] changes every
     * time the file is regenerated (e.g. same data with coordinates added), so updates are picked up.
     */
    data class Meta(val date: String, val count: Int, val revision: String = date)

    fun parseMeta(text: String): Meta {
        val props = text.lines().mapNotNull { line ->
            line.substringBefore('#').split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }.toMap()
        val date = props["date"].orEmpty()
        return Meta(date = date, count = props["count"]?.toIntOrNull() ?: 0, revision = props["generated"] ?: date)
    }

    /** What the user has done with the official list so far (persisted as "installed|removed <date> [<revision>]"). */
    data class State(val removed: Boolean, val date: String, val revision: String = date) {
        fun serialize() = (if (removed) "removed " else "installed ") + date + " " + revision

        companion object {
            fun parse(text: String?): State? {
                val parts = text?.trim()?.split(' ')?.takeIf { it.size in 2..3 } ?: return null
                val revision = parts.getOrElse(2) { parts[1] }
                return when (parts[0]) {
                    "installed" -> State(removed = false, date = parts[1], revision = revision)
                    "removed" -> State(removed = true, date = parts[1], revision = revision)
                    else -> null
                }
            }
        }
    }

    /**
     * Install on first launch, or refresh when the bundled list is newer. The list is maintained by the
     * app's publisher and users cannot remove it ("removed" states from older versions are reinstalled).
     */
    fun shouldInstall(state: State?, bundled: Meta): Boolean =
        state == null || state.removed || bundled.date > state.date || bundled.revision > state.revision

    /**
     * Merges a fresh official list into the user's homes:
     * - official homes are replaced by the new data (keeping an email the user added if the list has none);
     * - homes the user added by hand are kept as they are;
     * - any other home (official homes that left the list, homes imported from a CSV by older versions,
     *   which would duplicate official ones) is dropped, unless in [keepIds] (selected or contacted).
     */
    fun merge(current: List<CareHome>, official: List<CareHome>, keepIds: Set<String>): List<CareHome> {
        val currentById = current.associateBy { it.id }
        val fresh = official.map { new ->
            val old = currentById[new.id]
            val updated = new.copy(official = true, manual = false, email = new.email.ifBlank { old?.email.orEmpty() })
            // Fields the user corrected by hand win over the new list.
            if (old != null) updated.withUserEditsFrom(old) else updated
        }
        val freshIds = fresh.map { it.id }.toSet()
        val kept = current.filter { it.id !in freshIds && (it.manual || it.id in keepIds) }
        return (kept + fresh).sortedBy { normalizeForSearch(it.name) }
    }
}
