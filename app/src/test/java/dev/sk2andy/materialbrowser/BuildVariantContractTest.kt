package dev.sk2andy.materialbrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildVariantContractTest {
    @Test
    fun `user certificate trust matches build type`() {
        val expected = when (BuildConfig.BUILD_TYPE) {
            "debug", "release", "localRelease" -> false
            "userCaDebug", "userCaRelease" -> true
            else -> error("Unknown build type: ${BuildConfig.BUILD_TYPE}")
        }

        assertEquals(expected, BuildConfig.TRUST_USER_CERTIFICATES)
    }
}
