package it.chiarella.app.pause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class PauseReminderTest {
    private val now = LocalDateTime.of(2026, 11, 17, 12, 0)

    @Test fun nextIsLaterToday() =
        assertEquals(LocalDateTime.of(2026, 11, 17, 16, 0), PauseReminder.nextAt(listOf(16 * 60, 11 * 60), now))

    @Test fun nextIsTomorrowWhenAllPassed() =
        assertEquals(LocalDateTime.of(2026, 11, 18, 9, 30), PauseReminder.nextAt(listOf(12 * 60, 9 * 60 + 30), now))

    @Test fun noTimesNoAlarm() = assertNull(PauseReminder.nextAt(emptyList(), now))
}
