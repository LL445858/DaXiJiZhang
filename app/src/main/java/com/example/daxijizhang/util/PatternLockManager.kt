package com.example.daxijizhang.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest

object PatternLockManager {
    private const val TAG = "PatternLockManager"
    private const val PREFS_NAME = "pattern_lock"
    private const val KEY_PATTERN_HASH = "pattern_hash"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCK_UNTIL_TIME = "lock_until_time"
    private const val MAX_FAILED_ATTEMPTS = 5
    private const val LOCK_DURATION_MS = 10 * 60 * 1000L

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getPrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("PatternLockManager not initialized")
    }

    fun isLockEnabled(): Boolean {
        return try {
            getPrefs().getBoolean(KEY_LOCK_ENABLED, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read lock enabled status", e)
            false
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        try {
            getPrefs().edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save lock enabled status", e)
        }
    }

    fun hasPattern(): Boolean {
        return try {
            val hash = getPrefs().getString(KEY_PATTERN_HASH, null)
            !hash.isNullOrEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check pattern existence", e)
            false
        }
    }

    fun savePattern(pattern: String): Boolean {
        if (pattern.isBlank()) {
            Log.w(TAG, "Cannot save empty pattern")
            return false
        }

        return try {
            val hash = hashPattern(pattern)
            if (hash.isEmpty()) {
                Log.e(TAG, "Failed to generate pattern hash")
                return false
            }
            getPrefs().edit()
                .putString(KEY_PATTERN_HASH, hash)
                .putBoolean(KEY_LOCK_ENABLED, true)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .remove(KEY_LOCK_UNTIL_TIME)
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save pattern", e)
            false
        }
    }

    fun clearPattern() {
        try {
            getPrefs().edit()
                .remove(KEY_PATTERN_HASH)
                .putBoolean(KEY_LOCK_ENABLED, false)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .remove(KEY_LOCK_UNTIL_TIME)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear pattern", e)
        }
    }

    fun verifyPattern(pattern: String): Boolean {
        if (isLocked()) {
            return false
        }

        if (pattern.isBlank()) {
            return false
        }

        return try {
            val storedHash = getPrefs().getString(KEY_PATTERN_HASH, null)
            if (storedHash.isNullOrEmpty()) {
                return false
            }

            val inputHash = hashPattern(pattern)
            if (inputHash.isEmpty()) {
                return false
            }

            val isValid = storedHash == inputHash

            if (isValid) {
                resetFailedAttempts()
            } else {
                incrementFailedAttempts()
            }

            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify pattern", e)
            false
        }
    }

    fun verifyPatternWithoutCounting(pattern: String): Boolean {
        if (pattern.isBlank()) {
            return false
        }

        return try {
            val storedHash = getPrefs().getString(KEY_PATTERN_HASH, null)
            if (storedHash.isNullOrEmpty()) {
                return false
            }
            val inputHash = hashPattern(pattern)
            if (inputHash.isEmpty()) {
                return false
            }
            storedHash == inputHash
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify pattern without counting", e)
            false
        }
    }

    private fun hashPattern(pattern: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(pattern.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash pattern", e)
            ""
        }
    }

    fun getFailedAttempts(): Int {
        return try {
            getPrefs().getInt(KEY_FAILED_ATTEMPTS, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read failed attempts", e)
            0
        }
    }

    fun getRemainingFailedAttempts(): Int {
        return MAX_FAILED_ATTEMPTS - getFailedAttempts()
    }

    private fun incrementFailedAttempts() {
        val attempts = getFailedAttempts() + 1
        getPrefs().edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            lockForDuration()
        }
    }

    fun resetFailedAttempts() {
        getPrefs().edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .remove(KEY_LOCK_UNTIL_TIME)
            .apply()
    }

    private fun lockForDuration() {
        val lockUntilTime = System.currentTimeMillis() + LOCK_DURATION_MS
        getPrefs().edit().putLong(KEY_LOCK_UNTIL_TIME, lockUntilTime).apply()
    }

    fun isLocked(): Boolean {
        val lockUntilTime = getLockUntilTime()
        if (lockUntilTime <= 0) {
            return false
        }

        if (System.currentTimeMillis() >= lockUntilTime) {
            resetFailedAttempts()
            return false
        }

        return true
    }

    fun getLockUntilTime(): Long {
        return try {
            getPrefs().getLong(KEY_LOCK_UNTIL_TIME, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read lock until time", e)
            0
        }
    }

    fun getRemainingLockTimeMs(): Long {
        val lockUntilTime = getLockUntilTime()
        if (lockUntilTime <= 0) {
            return 0
        }

        val remainingMs = lockUntilTime - System.currentTimeMillis()
        return if (remainingMs > 0) remainingMs else 0
    }

    fun getRemainingLockTimeMinutes(): Int {
        val remainingMs = getRemainingLockTimeMs()
        if (remainingMs <= 0) {
            return 0
        }
        return ((remainingMs / 1000 / 60) + 1).toInt()
    }
}
