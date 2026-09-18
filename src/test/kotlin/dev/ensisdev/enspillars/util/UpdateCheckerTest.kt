package dev.ensisdev.enspillars.util

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UpdateChecker.compareVersions — saf sürüm karşılaştırma mantığı. */
class UpdateCheckerTest {
    private val checker = UpdateChecker(mockk(relaxed = true))

    @Test
    fun `newer latest is positive`() {
        assertTrue(checker.compareVersions("1.0.1", "1.0.0") > 0)
        assertTrue(checker.compareVersions("1.1.0", "1.0.9") > 0)
    }

    @Test
    fun `equal versions are zero`() {
        assertEquals(0, checker.compareVersions("1.0.0", "1.0.0"))
    }

    @Test
    fun `newer installed is negative`() {
        assertTrue(checker.compareVersions("1.0.0", "1.0.1") < 0)
    }
}
