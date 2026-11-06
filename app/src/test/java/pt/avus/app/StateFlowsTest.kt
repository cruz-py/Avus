package pt.avus.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.avus.app.data.updateSuspending

class StateFlowsTest {

    /** A change made while the (slow) merge runs must survive: the merge is redone on the new state. */
    @Test fun changeDuringSlowTransformIsNotLost() = runBlocking {
        val homes = MutableStateFlow(listOf("manual-1"))
        var attempts = 0
        val result = homes.updateSuspending { current ->
            attempts++
            if (attempts == 1) homes.value = current + "manual-2"   // user adds a home meanwhile
            current + "cs-1"                                          // "merge" the official list
        }
        assertEquals(listOf("manual-1", "manual-2", "cs-1"), result)
        assertEquals(result, homes.value)
        assertEquals(2, attempts)
    }
}
