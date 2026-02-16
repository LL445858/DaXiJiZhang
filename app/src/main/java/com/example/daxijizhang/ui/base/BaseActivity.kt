package com.example.daxijizhang.ui.base

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import com.example.daxijizhang.DaxiApplication
import com.example.daxijizhang.R
import com.example.daxijizhang.util.ThemeManager

abstract class BaseActivity : AppCompatActivity(), DaxiApplication.OnFontScaleChangeListener {

    private val TAG = "BaseActivity"
    private var backInvokedCallback: Any? = null

    override fun attachBaseContext(newBase: Context) {
        val scaledContext = applyFontScaleToContext(newBase)
        super.attachBaseContext(scaledContext)
    }

    private fun applyFontScaleToContext(context: Context): Context {
        val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        val scale = try {
            val percent = prefs.getFloat("font_size_percent", 100f)
            (percent / 100f).coerceIn(0.5f, 1.5f)
        } catch (e: Exception) {
            1.0f
        }

        val configuration = Configuration(context.resources.configuration)
        configuration.fontScale = scale
        
        val newContext = context.createConfigurationContext(configuration)
        
        ThemeManager.setFontScale(scale)
        
        return newContext
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBackPressHandler()
        setupForwardTransition()
        (application as? DaxiApplication)?.addFontScaleListener(this)
    }

    override fun onFontScaleChanged(scale: Float) {
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBackCallback()
        (application as? DaxiApplication)?.removeFontScaleListener(this)
    }

    private fun unregisterBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            @Suppress("UNCHECKED_CAST")
            val callback = backInvokedCallback as? android.window.OnBackInvokedCallback
            if (callback != null) {
                try {
                    onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to unregister back callback", e)
                }
            }
        }
    }

    private fun setupBackPressHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = object : android.window.OnBackInvokedCallback {
                override fun onBackInvoked() {
                    finishWithAnimation()
                }
            }
            backInvokedCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback
            )
            Log.d(TAG, "Registered predictive back gesture callback for ${this::class.simpleName}")
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithAnimation()
                }
            })
        }
    }

    private fun setupForwardTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    private fun finishWithAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        finish()
    }

    protected fun startActivityWithForwardAnimation(intent: android.content.Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startActivity(intent)
        } else {
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }
    }

    protected fun finishWithSlideAnimation() {
        finishWithAnimation()
    }
}
