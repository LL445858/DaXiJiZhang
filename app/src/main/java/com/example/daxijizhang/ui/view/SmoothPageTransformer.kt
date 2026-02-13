package com.example.daxijizhang.ui.view

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class SmoothPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        val absolutePosition = abs(position)
        
        if (absolutePosition >= 1f) {
            page.alpha = 0f
            page.translationX = 0f
            page.scaleX = 1f
            page.scaleY = 1f
            return
        }
        
        page.alpha = 1f - (absolutePosition * 0.3f)
        
        val pageWidth = page.width
        val translateX = position * -pageWidth * 0.3f
        page.translationX = translateX
        
        val scaleFactor = 0.95f.coerceAtLeast(1 - absolutePosition * 0.05f)
        page.scaleX = scaleFactor
        page.scaleY = scaleFactor
    }
}
