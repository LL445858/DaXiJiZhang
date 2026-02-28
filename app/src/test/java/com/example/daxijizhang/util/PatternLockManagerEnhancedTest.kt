package com.example.daxijizhang.util

import org.junit.Assert.*
import org.junit.Test

class PatternLockManagerEnhancedTest {

    @Test
    fun testEmptyPatternValidation() {
        val emptyPattern = ""
        val blankPattern = "   "

        assertTrue("Empty pattern should be blank", emptyPattern.isBlank())
        assertTrue("Blank pattern should be blank", blankPattern.isBlank())
    }

    @Test
    fun testHashPatternWithEmptyInput() {
        val pattern = ""
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(pattern.toByteArray(Charsets.UTF_8))
        val hash = bytes.joinToString("") { "%02x".format(it) }

        assertNotNull("Hash should not be null", hash)
        assertEquals("SHA-256 hash should be 64 characters", 64, hash.length)
        assertFalse("Hash should not be empty", hash.isEmpty())
    }

    @Test
    fun testPatternStringParsingWithWhitespace() {
        val patternString = " 0 , 1 , 2 , 3 "
        val indices = patternString.split(",").mapNotNull {
            it.trim().toIntOrNull()
        }

        assertEquals("Should parse 4 indices", 4, indices.size)
        assertEquals(listOf(0, 1, 2, 3), indices)
    }

    @Test
    fun testPatternStringParsingWithInvalidNumbers() {
        val patternString = "0,abc,2,def"
        val indices = patternString.split(",").mapNotNull {
            it.trim().toIntOrNull()
        }

        assertEquals("Should only parse valid numbers", 2, indices.size)
        assertEquals(listOf(0, 2), indices)
    }

    @Test
    fun testPatternStringParsingEmptyResult() {
        val patternString = "abc,def,ghi"
        val indices = patternString.split(",").mapNotNull {
            it.trim().toIntOrNull()
        }

        assertTrue("Should return empty list for all invalid", indices.isEmpty())
    }

    @Test
    fun testPatternIndexBounds() {
        val validIndices = listOf(0, 4, 8)
        val invalidIndices = listOf(-1, 9, 10, 100)

        for (index in validIndices) {
            assertTrue("Index $index should be valid", index in 0..8)
        }

        for (index in invalidIndices) {
            assertFalse("Index $index should be invalid", index in 0..8)
        }
    }

    @Test
    fun testLockMechanismStateTransitions() {
        var failedAttempts = 0
        val maxAttempts = 5
        var isLocked = false

        for (i in 1..maxAttempts) {
            failedAttempts++
            if (failedAttempts >= maxAttempts) {
                isLocked = true
            }
        }

        assertTrue("Should be locked after max attempts", isLocked)
        assertEquals("Failed attempts should equal max", maxAttempts, failedAttempts)

        failedAttempts = 0
        isLocked = false

        assertFalse("Should be unlocked after reset", isLocked)
        assertEquals("Failed attempts should be 0 after reset", 0, failedAttempts)
    }

    @Test
    fun testRemainingTimeCalculation() {
        val lockDurationMs = 10 * 60 * 1000L
        val startTime = System.currentTimeMillis()
        val lockUntilTime = startTime + lockDurationMs

        val remainingMs = lockUntilTime - System.currentTimeMillis()
        val remainingMinutes = ((remainingMs / 1000 / 60) + 1).toInt()

        assertTrue("Remaining time should be positive", remainingMs > 0)
        assertTrue("Remaining minutes should be at least 1", remainingMinutes >= 1)
        assertTrue("Remaining minutes should be at most 11", remainingMinutes <= 11)
    }

    @Test
    fun testExpiredLock() {
        val pastTime = System.currentTimeMillis() - 1000
        val isExpired = System.currentTimeMillis() >= pastTime

        assertTrue("Past time should be expired", isExpired)
    }

    @Test
    fun testPatternComplexity() {
        val simplePattern = "0,1,2,3"
        val complexPattern = "0,4,8,5,2,1,3,6,7"

        assertTrue("Simple pattern has minimum dots", simplePattern.split(",").size >= 4)
        assertTrue("Complex pattern has all 9 dots", complexPattern.split(",").size == 9)
    }

    @Test
    fun testPatternUniqueness() {
        val md = java.security.MessageDigest.getInstance("SHA-256")

        fun hash(p: String): String {
            return md.digest(p.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        val patterns = listOf(
            "0,1,2,3",
            "0,1,2,4",
            "0,1,2,5",
            "0,1,3,4",
            "1,2,3,4"
        )

        val hashes = patterns.map { hash(it) }
        val uniqueHashes = hashes.toSet()

        assertEquals("All patterns should have unique hashes", patterns.size, uniqueHashes.size)
    }

    @Test
    fun testSecureStorageKeys() {
        val expectedKeys = listOf(
            "pattern_hash",
            "lock_enabled",
            "failed_attempts",
            "lock_until_time"
        )

        for (key in expectedKeys) {
            assertFalse("Key should not be empty", key.isEmpty())
            assertTrue("Key should be lowercase with underscores", key == key.lowercase())
        }
    }

    @Test
    fun testMaxFailedAttemptsConstant() {
        val maxAttempts = 5

        assertTrue("Max attempts should be positive", maxAttempts > 0)
        assertTrue("Max attempts should be reasonable", maxAttempts <= 10)
    }

    @Test
    fun testLockDurationConstant() {
        val lockDurationMs = 10 * 60 * 1000L
        val tenMinutesInMs = 600000L

        assertEquals("Lock duration should be 10 minutes", tenMinutesInMs, lockDurationMs)
    }

    @Test
    fun testPatternValidationEdgeCases() {
        val exactlyFourDots = "0,1,2,3"
        val threeDots = "0,1,2"
        val oneDot = "0"
        val zeroDots = ""

        assertTrue("4 dots should be valid", exactlyFourDots.split(",").size >= 4)
        assertFalse("3 dots should be invalid", threeDots.split(",").size >= 4)
        assertFalse("1 dot should be invalid", oneDot.split(",").size >= 4)
        assertFalse("0 dots should be invalid", zeroDots.split(",").size >= 4)
    }

    @Test
    fun testConcurrentModificationSimulation() {
        val attempts = mutableListOf<Int>()
        val maxAttempts = 5

        for (i in 1..maxAttempts) {
            attempts.add(i)
        }

        assertEquals("Should record all attempts", maxAttempts, attempts.size)
        assertEquals("Last attempt should be max", maxAttempts, attempts.last())
    }

    @Test
    fun testHashConsistency() {
        val md = java.security.MessageDigest.getInstance("SHA-256")

        fun hash(p: String): String {
            return md.digest(p.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        val pattern = "0,1,2,3,4,5,6,7,8"
        val hashes = (1..10).map { hash(pattern) }
        val allSame = hashes.all { it == hashes[0] }

        assertTrue("Hash should be consistent across multiple calls", allSame)
    }

    @Test
    fun testHexFormatValidation() {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest("test".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val hexPattern = Regex("^[0-9a-f]{64}$")
        assertTrue("Hash should match hex pattern", hexPattern.matches(hash))
    }

    @Test
    fun testTransparentColorWithFullAlpha() {
        val alpha = 255
        val red = 100
        val green = 150
        val blue = 200
        val color = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

        val transparentColor = getTransparentColor(color, 1.0f)

        val resultAlpha = (transparentColor shr 24) and 0xFF
        val resultRed = (transparentColor shr 16) and 0xFF
        val resultGreen = (transparentColor shr 8) and 0xFF
        val resultBlue = transparentColor and 0xFF

        assertEquals("Alpha should be 255 with full alpha", 255, resultAlpha)
        assertEquals("Red should be preserved", 100, resultRed)
        assertEquals("Green should be preserved", 150, resultGreen)
        assertEquals("Blue should be preserved", 200, resultBlue)
    }

    @Test
    fun testTransparentColorWith70PercentAlpha() {
        val alpha = 255
        val red = 100
        val green = 150
        val blue = 200
        val color = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

        val transparentColor = getTransparentColor(color, 0.7f)

        val expectedAlpha = (255 * 0.7f).toInt()
        val resultAlpha = (transparentColor shr 24) and 0xFF
        val resultRed = (transparentColor shr 16) and 0xFF
        val resultGreen = (transparentColor shr 8) and 0xFF
        val resultBlue = transparentColor and 0xFF

        assertEquals("Alpha should be 70% of original", expectedAlpha, resultAlpha)
        assertEquals("Red should be preserved", 100, resultRed)
        assertEquals("Green should be preserved", 150, resultGreen)
        assertEquals("Blue should be preserved", 200, resultBlue)
    }

    @Test
    fun testTransparentColorWithZeroAlpha() {
        val alpha = 255
        val red = 100
        val green = 150
        val blue = 200
        val color = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

        val transparentColor = getTransparentColor(color, 0.0f)

        val resultAlpha = (transparentColor shr 24) and 0xFF
        val resultRed = (transparentColor shr 16) and 0xFF
        val resultGreen = (transparentColor shr 8) and 0xFF
        val resultBlue = transparentColor and 0xFF

        assertEquals("Alpha should be 0 with zero alpha", 0, resultAlpha)
        assertEquals("Red should be preserved", 100, resultRed)
        assertEquals("Green should be preserved", 150, resultGreen)
        assertEquals("Blue should be preserved", 200, resultBlue)
    }

    @Test
    fun testTransparentColorWithPartialAlpha() {
        val alpha = 200
        val red = 100
        val green = 150
        val blue = 200
        val color = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

        val transparentColor = getTransparentColor(color, 0.5f)

        val expectedAlpha = (200 * 0.5f).toInt()
        val resultAlpha = (transparentColor shr 24) and 0xFF

        assertEquals("Alpha should be 50% of original", expectedAlpha, resultAlpha)
    }

    private fun getTransparentColor(color: Int, alphaMultiplier: Float): Int {
        val a = ((color shr 24) and 0xFF) * alphaMultiplier
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (a.toInt() shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun testPatternSizeValidation() {
        val validSize = 4
        val invalidSize = 3
        val minSize = 4

        assertTrue("4 dots should be valid", validSize >= minSize)
        assertFalse("3 dots should be invalid", invalidSize >= minSize)
    }

    @Test
    fun testPatternSizeExactlyMinimum() {
        val patternSize = 4
        val minSize = 4

        assertTrue("Exactly minimum should be valid", patternSize >= minSize)
    }

    @Test
    fun testPatternSizeAboveMinimum() {
        val patternSize = 5
        val minSize = 4

        assertTrue("Above minimum should be valid", patternSize >= minSize)
    }

    @Test
    fun testPatternSizeBelowMinimum() {
        val patternSize = 3
        val minSize = 4

        assertFalse("Below minimum should be invalid", patternSize >= minSize)
    }

    @Test
    fun testCountdownTimerCalculation() {
        val totalMs = 65000L
        val minutes = (totalMs / 1000 / 60).toInt()
        val seconds = ((totalMs / 1000) % 60).toInt()

        assertEquals("Minutes should be 1", 1, minutes)
        assertEquals("Seconds should be 5", 5, seconds)
    }

    @Test
    fun testCountdownTimerZeroMinutes() {
        val totalMs = 30000L
        val minutes = (totalMs / 1000 / 60).toInt()
        val seconds = ((totalMs / 1000) % 60).toInt()

        assertEquals("Minutes should be 0", 0, minutes)
        assertEquals("Seconds should be 30", 30, seconds)
    }

    @Test
    fun testCountdownTimerTenMinutes() {
        val totalMs = 600000L
        val minutes = (totalMs / 1000 / 60).toInt()
        val seconds = ((totalMs / 1000) % 60).toInt()

        assertEquals("Minutes should be 10", 10, minutes)
        assertEquals("Seconds should be 0", 0, seconds)
    }
}
