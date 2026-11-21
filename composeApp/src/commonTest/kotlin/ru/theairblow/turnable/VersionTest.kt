package ru.theairblow.turnable

import ru.theairblow.turnable.service.MIN_CORE_VERSION
import ru.theairblow.turnable.service.compareVersions
import ru.theairblow.turnable.service.isSupportedCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun comparesReleaseTagsWithAndWithoutVPrefix() {
        assertEquals(0, compareVersions("v1.0.0", "1.0"))
        assertTrue(compareVersions("v1.2.3", "1.2.2") > 0)
        assertTrue(compareVersions("v0.10.2", "v0.9.9") > 0)
        assertTrue(compareVersions("1.0.0", "v1.0.1") < 0)
    }

    @Test
    fun rejectsCoresOlderThanTheMinimum() {
        assertTrue(isSupportedCore(MIN_CORE_VERSION))
        assertFalse(isSupportedCore("v0.0.1"))
    }
}
