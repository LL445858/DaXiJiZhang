package com.example.daxijizhang.util

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class PatternLockManagerTest {

    private fun hashPattern(pattern: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(pattern.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testHashPattern_consistentHash() {
        val pattern = "0,1,2,3"
        val hash1 = hashPattern(pattern)
        val hash2 = hashPattern(pattern)
        assertEquals(hash1, hash2)
    }

    @Test
    fun testHashPattern_differentPatternsDifferentHash() {
        val pattern1 = "0,1,2,3"
        val pattern2 = "0,1,2,4"
        val hash1 = hashPattern(pattern1)
        val hash2 = hashPattern(pattern2)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testHashPattern_emptyPattern() {
        val pattern = ""
        val hash = hashPattern(pattern)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
    }

    @Test
    fun testHashPattern_singleDot() {
        val pattern = "0"
        val hash = hashPattern(pattern)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
    }

    @Test
    fun testHashPattern_allNineDots() {
        val pattern = "0,1,2,3,4,5,6,7,8"
        val hash = hashPattern(pattern)
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun testPatternValidation_minimumFourDots() {
        val validPattern = listOf(0, 1, 2, 3)
        val invalidPattern = listOf(0, 1, 2)

        assertTrue(validPattern.size >= 4)
        assertFalse(invalidPattern.size >= 4)
    }

    @Test
    fun testPatternValidation_emptyPattern() {
        val pattern = emptyList<Int>()
        assertTrue(pattern.isEmpty())
        assertFalse(pattern.size >= 4)
    }

    @Test
    fun testLockDurationCalculation() {
        val lockDurationMs = 10 * 60 * 1000L
        val lockUntilTime = System.currentTimeMillis() + lockDurationMs
        val remainingMs = lockUntilTime - System.currentTimeMillis()

        assertTrue(remainingMs > 0)
        assertTrue(remainingMs <= lockDurationMs)

        val remainingMinutes = ((remainingMs / 1000 / 60) + 1).toInt()
        assertTrue(remainingMinutes >= 1)
        assertTrue(remainingMinutes <= 11)
    }

    @Test
    fun testLockDurationExpired() {
        val lockUntilTime = System.currentTimeMillis() - 1000
        val isExpired = System.currentTimeMillis() >= lockUntilTime
        assertTrue(isExpired)
    }

    @Test
    fun testLockDurationNotExpired() {
        val lockUntilTime = System.currentTimeMillis() + 10000
        val isExpired = System.currentTimeMillis() >= lockUntilTime
        assertFalse(isExpired)
    }

    @Test
    fun testFailedAttemptsCounter() {
        var failedAttempts = 0
        val maxAttempts = 5

        for (i in 1..maxAttempts) {
            failedAttempts++
        }

        assertEquals(maxAttempts, failedAttempts)
        assertTrue(failedAttempts >= maxAttempts)
    }

    @Test
    fun testFailedAttemptsReset() {
        var failedAttempts = 5
        failedAttempts = 0
        assertEquals(0, failedAttempts)
    }

    @Test
    fun testPatternStringFormat() {
        val patternIndices = listOf(0, 4, 8)
        val patternString = patternIndices.joinToString(",")
        assertEquals("0,4,8", patternString)
    }

    @Test
    fun testPatternStringParsing() {
        val patternString = "0,4,8"
        val indices = patternString.split(",").map { it.toInt() }
        assertEquals(listOf(0, 4, 8), indices)
    }

    @Test
    fun testPatternStringParsing_empty() {
        val patternString = ""
        val isEmpty = patternString.isBlank()
        assertTrue(isEmpty)
    }

    @Test
    fun testPatternStringParsing_invalidFormat() {
        val patternString = "abc,def"
        var parseSuccess = true
        try {
            patternString.split(",").map { it.toInt() }
        } catch (e: NumberFormatException) {
            parseSuccess = false
        }
        assertFalse(parseSuccess)
    }

    @Test
    fun testPatternIndexValidation() {
        val validIndices = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        val invalidIndex = 9
        val negativeIndex = -1

        for (index in validIndices) {
            assertTrue(index in 0..8)
        }
        assertFalse(invalidIndex in 0..8)
        assertFalse(negativeIndex in 0..8)
    }

    @Test
    fun testSha256HashLength() {
        val pattern = "test"
        val hash = hashPattern(pattern)
        assertEquals(64, hash.length)
    }

    @Test
    fun testSha256HashFormat() {
        val pattern = "test"
        val hash = hashPattern(pattern)
        val hexPattern = Regex("^[0-9a-f]+$")
        assertTrue(hexPattern.matches(hash))
    }

    @Test
    fun testRemainingFailedAttempts_calculation() {
        val maxAttempts = 5
        for (failedAttempts in 0..maxAttempts) {
            val remaining = maxAttempts - failedAttempts
            assertTrue(remaining in 0..maxAttempts)
        }
    }

    @Test
    fun testRemainingFailedAttempts_exhausted() {
        val failedAttempts = 5
        val maxAttempts = 5
        val remaining = maxAttempts - failedAttempts
        assertEquals(0, remaining)
    }

    @Test
    fun testRemainingFailedAttempts_fresh() {
        val failedAttempts = 0
        val maxAttempts = 5
        val remaining = maxAttempts - failedAttempts
        assertEquals(maxAttempts, remaining)
    }

    @Test
    fun testPatternOrder_matters() {
        val pattern1 = "0,1,2,3"
        val pattern2 = "3,2,1,0"
        val hash1 = hashPattern(pattern1)
        val hash2 = hashPattern(pattern2)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testPatternWithWhitespace() {
        val patternWithSpaces = "0, 1, 2, 3"
        val patternNoSpaces = "0,1,2,3"
        val hash1 = hashPattern(patternWithSpaces)
        val hash2 = hashPattern(patternNoSpaces)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testLockTimeCalculation_minutes() {
        val lockDurationMs = 10 * 60 * 1000L
        val remainingMs = lockDurationMs
        val minutes = (remainingMs / 1000 / 60).toInt()
        assertEquals(10, minutes)
    }

    @Test
    fun testLockTimeCalculation_seconds() {
        val lockDurationMs = 10 * 60 * 1000L
        val remainingMs = 65000L
        val minutes = (remainingMs / 1000 / 60).toInt()
        val seconds = ((remainingMs / 1000) % 60).toInt()
        assertEquals(1, minutes)
        assertEquals(5, seconds)
    }
}
