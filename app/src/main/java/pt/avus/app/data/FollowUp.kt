package pt.avus.app.data

import java.util.concurrent.TimeUnit

/** Helpers for following up on contacted homes: matching shared replies and choosing reminders. */
object FollowUp {

    private val emailRegex = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    /** Days without news after which a contact is worth a phone call. */
    const val OVERDUE_DAYS = 7L

    /**
     * Guesses which home a shared text (an email, a message…) is about: first by an email address
     * that belongs to a home, then by a home's name appearing in the text. Contacted homes win ties.
     */
    fun matchHome(text: String, subject: String?, homes: List<CareHome>, contactedIds: Set<String>): CareHome? {
        val all = "${subject.orEmpty()}\n$text"
        val addresses = emailRegex.findAll(all).map { it.value.lowercase() }.toSet()
        val ranked = homes.sortedByDescending { it.id in contactedIds }
        ranked.firstOrNull { home ->
            home.email.isNotBlank() && home.email.lowercase().split(';', ',', ' ').any { it.trim() in addresses }
        }?.let { return it }
        // By name only when the name is distinctive: 3+ meaningful words and no other home with the same name
        // ("Casa de Repouso" or "Lar de Idosos" would match far too much).
        val normalized = normalizeForSearch(all)
        val nameCounts = homes.groupingBy { normalizeForSearch(it.name) }.eachCount()
        return ranked
            .filter { home ->
                val name = normalizeForSearch(home.name)
                nameCounts[name] == 1 && significantWords(name) >= 3 && name in normalized
            }
            .maxByOrNull { it.name.length }
    }

    private val particles = setOf("de", "da", "do", "das", "dos", "e", "a", "o", "as", "os")

    private fun significantWords(name: String): Int =
        name.split(Regex("[^a-z0-9]+")).count { it.length > 1 && it !in particles }

    /** Contacts still waiting for an answer for at least [OVERDUE_DAYS]. */
    fun overdue(contacts: List<Contact>, now: Long): List<Contact> =
        contacts.filter {
            (it.status == ContactStatus.SENT || it.status == ContactStatus.NO_REPLY) &&
                now - it.updatedAt >= TimeUnit.DAYS.toMillis(OVERDUE_DAYS)
        }

    /**
     * Overdue contacts not reminded about yet. [reminded] maps homeId -> the updatedAt it was
     * reminded for, so each contact is reminded once, and again only after it changes.
     */
    fun dueForReminder(contacts: List<Contact>, reminded: Map<String, Long>, now: Long): List<Contact> =
        overdue(contacts, now).filter { reminded[it.homeId] != it.updatedAt }

    /** Adds a dated entry on top of the existing notes. */
    fun appendNote(existing: String, note: String, date: String): String {
        val entry = note.trim()
        if (entry.isEmpty()) return existing
        return if (existing.isBlank()) "$date: $entry" else "$date: $entry\n$existing"
    }
}
