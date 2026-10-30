package it.chiarella.app.audio

import org.junit.Assert.*
import org.junit.Test

class SystemVolumeTest {
    private class Device(var state: SystemVolumeState) : SystemVolumeDevice {
        var writes = 0
        var limit: Int? = null
        var fail = false
        override fun read() = state
        override fun setToMaximum() {
            writes++
            if (fail) throw SecurityException("Volume non modificabile")
            state = state.copy(current = limit ?: state.maximum, muted = false)
        }
    }

    @Test fun openingOrDecliningNeverChangesTheDevice() {
        val device = Device(SystemVolumeState(4, 15))
        val check = SystemVolumeCheck(device)
        assertTrue(check.inspect()!!.needsIncrease)
        assertEquals(26, check.inspect()!!.percent)
        assertEquals(0, device.writes)
        assertEquals(4, device.state.current)
    }

    @Test fun explicitConsentRaisesAndReadsBackTheActualLevel() {
        val device = Device(SystemVolumeState(4, 15))
        val result = SystemVolumeCheck(device).raiseToMaximum()!!
        assertEquals(1, device.writes)
        assertEquals(15, result.current)
        assertFalse(result.needsIncrease)
    }

    @Test fun alreadyAtMaximumDoesNotOfferOrPerformAnotherChange() {
        val device = Device(SystemVolumeState(15, 15))
        val check = SystemVolumeCheck(device)
        assertFalse(check.inspect()!!.needsIncrease)
        check.raiseToMaximum()
        assertEquals(0, device.writes)
    }

    @Test fun systemLimitsAndDeniedChangesRemainVisibleWithoutClaimingSuccess() {
        val device = Device(SystemVolumeState(4, 15)).apply { limit = 10 }
        assertEquals(10, SystemVolumeCheck(device).raiseToMaximum()!!.current)
        device.fail = true
        assertTrue(SystemVolumeCheck(device).raiseToMaximum()!!.needsIncrease)
        assertEquals(10, device.state.current)
    }

    @Test fun fixedVolumeOnlyOffersManualInstructions() {
        val device = Device(SystemVolumeState(4, 15, canAdjust = false))
        assertTrue(SystemVolumeCheck(device).raiseToMaximum()!!.needsIncrease)
        assertEquals(0, device.writes)
    }

    @Test fun muteAtMaximumStillNeedsAnExplicitUnmute() {
        val device = Device(SystemVolumeState(15, 15, muted = true))
        val check = SystemVolumeCheck(device)
        assertTrue(check.inspect()!!.needsIncrease)
        assertEquals(0, device.writes)
        assertFalse(check.raiseToMaximum()!!.muted)
    }

    @Test fun unavailableVolumeDoesNotBlockOpeningTheApp() {
        val broken = object : SystemVolumeDevice {
            override fun read(): SystemVolumeState = throw IllegalStateException("Nessun dispositivo")
            override fun setToMaximum() = error("Non deve essere chiamato")
        }
        assertNull(SystemVolumeCheck(broken).inspect())
        assertNull(SystemVolumeCheck(broken).raiseToMaximum())
        assertNull(SystemVolumeCheck(Device(SystemVolumeState(0, 0))).inspect())
    }
}
