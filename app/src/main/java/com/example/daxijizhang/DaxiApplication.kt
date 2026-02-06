package com.example.daxijizhang

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.daxijizhang.util.AutoBackupManager
import com.example.daxijizhang.util.ThemeManager

class DaxiApplication : Application() {

    private val TAG = "DaxiApplication"

    override fun onCreate() {
        super.onCreate()
        // 初始化主题管理器
        ThemeManager.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }

    inner class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            Log.i(TAG, "应用进入后台，执行自动备份检查")
            performAutoBackup()
        }
    }

    private fun performAutoBackup() {
        try {
            AutoBackupManager.getInstance(this).performAutoBackupIfNeeded()
        } catch (e: Exception) {
            Log.e(TAG, "自动备份执行失败", e)
        }
    }
}
