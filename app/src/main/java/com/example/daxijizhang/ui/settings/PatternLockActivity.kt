package com.example.daxijizhang.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityPatternLockBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.ui.view.PatternLockView
import com.example.daxijizhang.util.PatternLockManager
import com.example.daxijizhang.util.ThemeManager

class PatternLockActivity : BaseActivity() {

    private lateinit var binding: ActivityPatternLockBinding

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SET = "set"
        const val MODE_VERIFY = "verify"
        const val MODE_VERIFY_DISABLE = "verify_disable"
        const val RESULT_SUCCESS = 100
        const val RESULT_CANCELLED = 101

        private const val STATE_FIRST_PATTERN = "first_pattern"
        private const val STATE_IS_CONFIRMING = "is_confirming"

        fun createSetIntent(context: Context): Intent {
            return Intent(context, PatternLockActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_SET)
            }
        }

        fun createVerifyIntent(context: Context): Intent {
            return Intent(context, PatternLockActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_VERIFY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        fun createVerifyDisableIntent(context: Context): Intent {
            return Intent(context, PatternLockActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_VERIFY_DISABLE)
            }
        }
    }

    private var mode: String = MODE_SET
    private var firstPattern: String? = null
    private var isConfirmingPattern = false
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatternLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SET

        savedInstanceState?.let {
            firstPattern = it.getString(STATE_FIRST_PATTERN)
            isConfirmingPattern = it.getBoolean(STATE_IS_CONFIRMING, false)
        }

        if (mode == MODE_VERIFY) {
            setupStatusBarForVerifyMode()
        }

        setupStatusBarPadding()
        initViews()
        setupPatternListener()
        setupBackPressHandler()
        applyThemeColor()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FIRST_PATTERN, firstPattern)
        outState.putBoolean(STATE_IS_CONFIRMING, isConfirmingPattern)
    }

    private fun setupStatusBarForVerifyMode() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        window.apply {
            statusBarColor = if (isDarkMode) {
                Color.BLACK
            } else {
                ContextCompat.getColor(this@PatternLockActivity, R.color.background)
            }

            WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = !isDarkMode
            }

            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        binding.statusBarPlaceholder.setBackgroundColor(
            if (isDarkMode) Color.BLACK
            else ContextCompat.getColor(this, R.color.background)
        )
    }

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.statusBarPlaceholder.updateLayoutParams {
                height = statusBarInsets.top
            }

            binding.contentContainer.setPadding(
                binding.contentContainer.paddingLeft,
                binding.contentContainer.paddingTop,
                binding.contentContainer.paddingRight,
                navigationBarInsets.bottom
            )

            windowInsets
        }
    }

    private fun initViews() {
        binding.btnCancel.visibility = android.view.View.GONE

        when (mode) {
            MODE_SET -> {
                binding.toolbar.visibility = android.view.View.VISIBLE
                binding.toolbar.title = getString(R.string.app_lock)
                binding.tvTitle.visibility = android.view.View.GONE
                updateHintForSetMode()
            }
            MODE_VERIFY -> {
                binding.toolbar.visibility = android.view.View.GONE
                binding.tvTitle.visibility = android.view.View.VISIBLE
                binding.tvTitle.text = getString(R.string.app_locked_title)
                binding.tvHint.text = getString(R.string.please_draw_pattern)
                checkLockStatus()
            }
            MODE_VERIFY_DISABLE -> {
                binding.toolbar.visibility = android.view.View.VISIBLE
                binding.toolbar.title = getString(R.string.app_lock)
                binding.tvTitle.visibility = android.view.View.GONE
                binding.tvHint.text = getString(R.string.draw_pattern)
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            handleCancel()
        }
    }

    private fun updateHintForSetMode() {
        binding.tvHint.text = if (isConfirmingPattern && firstPattern != null) {
            getString(R.string.draw_pattern_again)
        } else {
            getString(R.string.draw_pattern)
        }
    }

    private fun checkLockStatus() {
        if (mode == MODE_VERIFY && PatternLockManager.isLocked()) {
            startLockCountdown()
        } else {
            updateRemainingAttempts()
        }
    }

    private fun updateRemainingAttempts() {
        if (mode == MODE_VERIFY) {
            val remaining = PatternLockManager.getRemainingFailedAttempts()
            if (remaining < 5) {
                binding.tvHint.text = getString(R.string.pattern_error_remaining, remaining)
            } else {
                binding.tvHint.text = getString(R.string.please_draw_pattern)
            }
        }
    }

    private fun startLockCountdown() {
        binding.patternLockView.isEnabled = false
        countDownTimer?.cancel()

        val lockUntilTime = PatternLockManager.getLockUntilTime()
        val remainingMs = lockUntilTime - System.currentTimeMillis()

        if (remainingMs <= 0) {
            PatternLockManager.resetFailedAttempts()
            binding.patternLockView.isEnabled = true
            binding.tvHint.text = getString(R.string.please_draw_pattern)
            return
        }

        countDownTimer = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000 / 60).toInt()
                val seconds = ((millisUntilFinished / 1000) % 60).toInt()
                binding.tvHint.text = getString(R.string.pattern_locked_time, minutes, seconds)
            }

            override fun onFinish() {
                PatternLockManager.resetFailedAttempts()
                binding.patternLockView.isEnabled = true
                binding.tvHint.text = getString(R.string.please_draw_pattern)
            }
        }.start()
    }

    private fun setupPatternListener() {
        binding.patternLockView.setOnPatternListener(object : PatternLockView.OnPatternListener {
            override fun onPatternStart() {
                binding.tvHint.visibility = android.view.View.VISIBLE
                binding.patternLockView.setShowError(false)
            }

            override fun onPatternProgress(pattern: List<PatternLockView.Dot>) {
            }

            override fun onPatternComplete(pattern: List<PatternLockView.Dot>) {
                handlePatternComplete(pattern)
            }

            override fun onPatternCleared() {
                when (mode) {
                    MODE_SET -> {
                        if (!isConfirmingPattern) {
                            binding.tvHint.text = getString(R.string.draw_pattern)
                        }
                    }
                    MODE_VERIFY -> {
                        updateRemainingAttempts()
                    }
                    MODE_VERIFY_DISABLE -> {
                        binding.tvHint.text = getString(R.string.draw_pattern)
                    }
                }
            }
        })
    }

    private fun handlePatternComplete(pattern: List<PatternLockView.Dot>) {
        val patternString = binding.patternLockView.getPatternString()
        val patternSize = pattern.size

        when (mode) {
            MODE_SET -> handleSetPattern(patternSize, patternString)
            MODE_VERIFY -> handleVerifyPattern(patternString)
            MODE_VERIFY_DISABLE -> handleVerifyDisablePattern(patternString)
        }
    }

    private fun handleSetPattern(patternSize: Int, patternString: String) {
        if (!isConfirmingPattern) {
            if (patternSize < 4) {
                binding.tvHint.text = getString(R.string.pattern_too_short)
                binding.patternLockView.setShowError(true)
                return
            }
            firstPattern = patternString
            isConfirmingPattern = true
            binding.tvHint.text = getString(R.string.draw_pattern_again)
            binding.patternLockView.clearPattern()
        } else {
            if (patternString == firstPattern) {
                PatternLockManager.savePattern(patternString)
                setResult(RESULT_SUCCESS)
                finish()
            } else {
                binding.tvHint.text = getString(R.string.pattern_not_match)
                binding.patternLockView.setShowError(true)
                firstPattern = null
                isConfirmingPattern = false
            }
        }
    }

    private fun handleVerifyPattern(patternString: String) {
        try {
            if (PatternLockManager.isLocked()) {
                startLockCountdown()
                return
            }

            if (PatternLockManager.verifyPattern(patternString)) {
                countDownTimer?.cancel()
                val intent = Intent(this, com.example.daxijizhang.MainActivity::class.java)
                intent.putExtra(com.example.daxijizhang.MainActivity.EXTRA_SKIP_PATTERN_VERIFY, true)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            } else {
                binding.tvHint.text = getString(R.string.pattern_error)
                binding.patternLockView.setShowError(true)

                if (PatternLockManager.isLocked()) {
                    binding.patternLockView.isEnabled = false
                    binding.patternLockView.postDelayed({
                        startLockCountdown()
                    }, 1000)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PatternLockActivity", "Error verifying pattern", e)
            binding.tvHint.text = getString(R.string.pattern_error)
            binding.patternLockView.setShowError(true)
        }
    }

    private fun handleVerifyDisablePattern(patternString: String) {
        try {
            if (PatternLockManager.verifyPatternWithoutCounting(patternString)) {
                PatternLockManager.clearPattern()
                setResult(RESULT_SUCCESS)
                finish()
            } else {
                binding.tvHint.text = getString(R.string.pattern_error)
                binding.patternLockView.setShowError(true)
            }
        } catch (e: Exception) {
            android.util.Log.e("PatternLockActivity", "Error verifying disable pattern", e)
            binding.tvHint.text = getString(R.string.pattern_error)
            binding.patternLockView.setShowError(true)
        }
    }

    private fun handleCancel() {
        countDownTimer?.cancel()
        setResult(RESULT_CANCELLED)
        finish()
    }

    private fun setupBackPressHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                handleCancel()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleCancel()
                }
            })
        }
    }

    private fun applyThemeColor() {
        binding.patternLockView.updateThemeColor()
    }

    override fun onResume() {
        super.onResume()
        binding.patternLockView.updateThemeColor()
        if (mode == MODE_VERIFY) {
            checkLockStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
