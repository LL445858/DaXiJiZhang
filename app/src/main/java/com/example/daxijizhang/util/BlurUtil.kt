package com.example.daxijizhang.util

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

object BlurUtil {

    /**
     * 对Bitmap应用高斯模糊效果
     * @param context 上下文
     * @param bitmap 原始图片
     * @param radius 模糊半径（0-25，对应0-100的模糊值）
     * @return 模糊后的Bitmap
     */
    fun blurBitmap(context: Context, bitmap: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) {
            return bitmap
        }

        var inputBitmap = bitmap
        var outputBitmap: Bitmap? = null
        var renderScript: RenderScript? = null
        var inputAllocation: Allocation? = null
        var outputAllocation: Allocation? = null
        var blurScript: ScriptIntrinsicBlur? = null

        try {
            renderScript = RenderScript.create(context)
            
            inputAllocation = Allocation.createFromBitmap(
                renderScript,
                inputBitmap,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT
            )
            
            outputBitmap = Bitmap.createBitmap(
                inputBitmap.width,
                inputBitmap.height,
                inputBitmap.config ?: Bitmap.Config.ARGB_8888
            )
            
            outputAllocation = Allocation.createFromBitmap(
                renderScript,
                outputBitmap,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT
            )
            
            blurScript = ScriptIntrinsicBlur.create(
                renderScript,
                Element.U8_4(renderScript)
            )
            
            blurScript.setRadius(radius.toFloat())
            blurScript.setInput(inputAllocation)
            blurScript.forEach(outputAllocation)
            
            outputAllocation!!.copyTo(outputBitmap)
            
        } catch (e: Exception) {
            e.printStackTrace()
            if (outputBitmap == null) {
                outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            }
        } finally {
            outputAllocation?.destroy()
            inputAllocation?.destroy()
            blurScript?.destroy()
            renderScript?.destroy()
        }
        
        return outputBitmap!!
    }

    /**
     * 将模糊值（0-100）转换为RenderScript模糊半径（0-25）
     */
    fun convertBlurValueToRadius(blurValue: Int): Int {
        return (blurValue / 100f * 25f).toInt()
    }
}
