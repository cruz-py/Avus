package pt.avus.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.avus.app.data.CareHome
import pt.avus.app.data.Contact
import pt.avus.app.data.ContactStatus
import pt.avus.app.data.FollowUp
import java.util.concurrent.TimeUnit

class FollowUpTest {

    private val homes = listOf(
        CareHome("cs-1", "Lar Nossa Senhora da Conceição", email = "gscunheira@sapo.pt", locality = "Cunheira"),
        CareHome("cs-2", "Lar Nossa Senhora da Assunção", email = "geral@scmalter.pt"),
        CareHome("cs-3", "Casa de Repouso", email = "a@b.pt"),
    )
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test fun matchesSharedReplyByEmailThenByName() {
        val forwarded = "---------- Forwarded message ---------\nDe: Lar <GSCUNHEIRA@sapo.pt>\nTemos vaga a partir de março."
        assertEquals("cs-1", FollowUp.matchHome(forwarded, null, homes, emptySet())?.id)
        assertEquals("cs-2", FollowUp.matchHome("Resposta do Lar Nossa Senhora da Assunção: lista de espera", null, homes, emptySet())?.id)
        // Short generic names are not matched by name alone.
        assertNull(FollowUp.matchHome("A casa de repouso ligou", null, homes, emptySet()))
        assertNull(FollowUp.matchHome("Obrigado pelo contacto.", "Re: Pedido de inscrição em ERPI – Manuel", homes, emptySet()))
    }

    @Test fun remindsOnceForEachContactWaitingAWeek() {
        val now = 100 * day
        val contacts = listOf(
            Contact("cs-1", ContactStatus.SENT, now - 8 * day, now - 8 * day),            // overdue
            Contact("cs-2", ContactStatus.SENT, now - 3 * day, now - 3 * day),            // too recent
            Contact("cs-3", ContactStatus.WAITING_LIST, now - 20 * day, now - 20 * day),  // has an answer
        )
        assertEquals(listOf("cs-1"), FollowUp.dueForReminder(contacts, emptyMap(), now).map { it.homeId })
        assertEquals(emptyList<Contact>(), FollowUp.dueForReminder(contacts, mapOf("cs-1" to now - 8 * day), now))
        // Re-sent later: it becomes due again a week after the new update.
        val resent = listOf(contacts[0].copy(updatedAt = now))
        assertEquals(1, FollowUp.dueForReminder(resent, mapOf("cs-1" to now - 8 * day), now + 7 * day).size)
    }

    @Test fun appendsDatedNotesOnTop() {
        assertEquals("06/11: Visita quinta", FollowUp.appendNote("", " Visita quinta ", "06/11"))
        assertEquals("07/11: Vaga!\n06/11: Visita", FollowUp.appendNote("06/11: Visita", "Vaga!", "07/11"))
        assertEquals("anterior", FollowUp.appendNote("anterior", "  ", "07/11"))
    }

    @Test fun occupancyFromCapacityAndResidents() {
        val home = CareHome("x", "x", capacity = "40", residents = "31")
        assertEquals(9, home.freePlaces)
        assertEquals(31f / 40f, home.occupancy!!, 0.001f)
        assertEquals(0, CareHome("x", "x", capacity = "40", residents = "42").freePlaces)   // over capacity = full
        assertNull(CareHome("x", "x", capacity = "40").freePlaces)
        assertNull(CareHome("x", "x", capacity = "0", residents = "0").occupancy)
    }
}
