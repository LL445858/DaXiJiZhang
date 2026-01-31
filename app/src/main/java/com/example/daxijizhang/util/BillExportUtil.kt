package com.example.daxijizhang.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.daxijizhang.R
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.PageSize
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

object BillExportUtil {

    enum class ExportFormat {
        IMAGE, PDF, CSV
    }

    enum class ExportMethod {
        SAVE_LOCAL, SHARE_APP
    }

    data class ExportData(
        val bill: Bill,
        val items: List<BillItem>
    )

    fun exportBill(
        context: Context,
        data: ExportData,
        format: ExportFormat,
        method: ExportMethod,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            when (format) {
                ExportFormat.IMAGE -> exportAsImage(context, data, method, onSuccess, onError)
                ExportFormat.PDF -> exportAsPdf(context, data, method, onSuccess, onError)
                ExportFormat.CSV -> exportAsCsv(context, data, method, onSuccess, onError)
            }
        } catch (e: Exception) {
            onError(context.getString(R.string.export_failed) + ": ${e.message}")
        }
    }

    private fun exportAsImage(
        context: Context,
        data: ExportData,
        method: ExportMethod,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val bitmap = createBillImageHighQuality(context, data)
        val fileName = "账单_${data.bill.getDisplayAddress()}_${System.currentTimeMillis()}.png"

        when (method) {
            ExportMethod.SAVE_LOCAL -> {
                saveImageToGalleryHighQuality(context, bitmap, fileName, onSuccess, onError)
            }
            ExportMethod.SHARE_APP -> {
                shareImageToAppHighQuality(context, bitmap, fileName, onSuccess, onError)
            }
        }
    }

    private fun exportAsPdf(
        context: Context,
        data: ExportData,
        method: ExportMethod,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val fileName = "账单_${data.bill.getDisplayAddress()}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        try {
            val document = Document(PageSize.A4, 50f, 50f, 50f, 50f)
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            // 创建中文字体 - 使用itext-asian库
            val chineseFont = createChineseFont()
            val chineseFontBold = createChineseFont(Font.BOLD)

            // 添加标题
            val titleFont = Font(chineseFont.baseFont, 18f, Font.BOLD)
            val title = Paragraph(data.bill.getDisplayAddress(), titleFont)
            title.alignment = Element.ALIGN_CENTER
            document.add(title)
            document.add(Paragraph("\n"))

            // 创建表格
            val table = PdfPTable(4)
            table.widthPercentage = 100f
            table.setWidths(floatArrayOf(3.5f, 2f, 2f, 2.5f))

            // 表头
            val headerFont = Font(chineseFont.baseFont, 12f, Font.BOLD)
            val headers = arrayOf("项目名称", "单价", "数量", "金额")
            headers.forEach { header ->
                val cell = PdfPCell(Phrase(header, headerFont))
                cell.horizontalAlignment = Element.ALIGN_CENTER
                cell.verticalAlignment = Element.ALIGN_MIDDLE
                cell.paddingTop = 8f
                cell.paddingBottom = 8f
                cell.backgroundColor = BaseColor(245, 245, 245)
                table.addCell(cell)
            }

            // 数据行
            val contentFont = Font(chineseFont.baseFont, 11f, Font.NORMAL)
            data.items.forEach { item ->
                addPdfCell(table, item.projectName, contentFont)
                addPdfCell(table, "¥${String.format("%.2f", item.unitPrice)}", contentFont)
                addPdfCell(table, "${item.quantity}", contentFont)
                addPdfCell(table, "¥${String.format("%.2f", item.totalPrice)}", contentFont)
            }

            // 合计行 - 修改为只在第一列显示"合计"，其他列留空
            val totalFont = Font(chineseFont.baseFont, 12f, Font.BOLD)
            
            // 第一列：合计
            val totalLabelCell = PdfPCell(Phrase("合计", totalFont))
            totalLabelCell.horizontalAlignment = Element.ALIGN_CENTER
            totalLabelCell.verticalAlignment = Element.ALIGN_MIDDLE
            totalLabelCell.paddingTop = 8f
            totalLabelCell.paddingBottom = 8f
            table.addCell(totalLabelCell)
            
            // 第二列：空
            val emptyCell1 = PdfPCell(Phrase("", totalFont))
            emptyCell1.horizontalAlignment = Element.ALIGN_CENTER
            emptyCell1.verticalAlignment = Element.ALIGN_MIDDLE
            emptyCell1.paddingTop = 8f
            emptyCell1.paddingBottom = 8f
            table.addCell(emptyCell1)
            
            // 第三列：空
            val emptyCell2 = PdfPCell(Phrase("", totalFont))
            emptyCell2.horizontalAlignment = Element.ALIGN_CENTER
            emptyCell2.verticalAlignment = Element.ALIGN_MIDDLE
            emptyCell2.paddingTop = 8f
            emptyCell2.paddingBottom = 8f
            table.addCell(emptyCell2)
            
            // 第四列：金额
            val totalAmountCell = PdfPCell(Phrase(
                "¥${String.format("%.2f", data.items.sumOf { it.totalPrice })}",
                totalFont
            ))
            totalAmountCell.horizontalAlignment = Element.ALIGN_CENTER
            totalAmountCell.verticalAlignment = Element.ALIGN_MIDDLE
            totalAmountCell.paddingTop = 8f
            totalAmountCell.paddingBottom = 8f
            table.addCell(totalAmountCell)

            document.add(table)
            document.close()

            when (method) {
                ExportMethod.SAVE_LOCAL -> {
                    saveFileToDownloads(context, file, fileName, "application/pdf", onSuccess, onError)
                }
                ExportMethod.SHARE_APP -> {
                    shareFileToApp(context, file, "application/pdf", onSuccess, onError)
                }
            }
        } catch (e: Exception) {
            onError(context.getString(R.string.export_failed) + ": ${e.message}")
        }
    }

    private fun createChineseFont(style: Int = Font.NORMAL): Font {
        return try {
            // 使用iText-asian库提供的中文字体
            val baseFont = BaseFont.createFont(
                "STSong-Light",
                "UniGB-UCS2-H",
                BaseFont.NOT_EMBEDDED
            )
            Font(baseFont, 12f, style)
        } catch (e: Exception) {
            try {
                // 备选：使用系统自带的中文字体
                val systemFontPaths = arrayOf(
                    "/system/fonts/DroidSansFallback.ttf",
                    "/system/fonts/NotoSansCJK-Regular.ttc",
                    "/system/fonts/NotoSansSC-Regular.otf"
                )
                
                for (fontPath in systemFontPaths) {
                    try {
                        val fontFile = File(fontPath)
                        if (fontFile.exists()) {
                            val baseFont = BaseFont.createFont(
                                fontPath,
                                BaseFont.IDENTITY_H,
                                BaseFont.EMBEDDED
                            )
                            return Font(baseFont, 12f, style)
                        }
                    } catch (ignored: Exception) {
                        // 继续尝试下一个字体
                    }
                }
                
                // 如果系统字体都失败，使用默认字体
                Font(Font.FontFamily.HELVETICA, 12f, style)
            } catch (e2: Exception) {
                // 如果都失败，使用默认字体
                Font(Font.FontFamily.HELVETICA, 12f, style)
            }
        }
    }

    private fun addPdfCell(table: PdfPTable, text: String, font: Font) {
        val cell = PdfPCell(Phrase(text, font))
        cell.horizontalAlignment = Element.ALIGN_CENTER
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.paddingTop = 6f
        cell.paddingBottom = 6f
        table.addCell(cell)
    }

    private fun exportAsCsv(
        context: Context,
        data: ExportData,
        method: ExportMethod,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val fileName = "账单_${data.bill.getDisplayAddress()}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        try {
            OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
                // 写入BOM，确保Excel正确识别UTF-8编码
                writer.write("\uFEFF")

                // 标题
                writer.write(data.bill.getDisplayAddress())
                writer.write("\n\n")

                // 表头
                writer.write("项目名称,单价,数量,金额\n")

                // 数据行
                data.items.forEach { item ->
                    writer.write("${escapeCsv(item.projectName)},${item.unitPrice},${item.quantity},${item.totalPrice}\n")
                }

                // 合计行
                writer.write("合计,,,${data.items.sumOf { it.totalPrice }}\n")
            }

            when (method) {
                ExportMethod.SAVE_LOCAL -> {
                    saveFileToDownloads(context, file, fileName, "text/csv", onSuccess, onError)
                }
                ExportMethod.SHARE_APP -> {
                    shareFileToApp(context, file, "text/csv", onSuccess, onError)
                }
            }
        } catch (e: Exception) {
            onError(context.getString(R.string.export_failed) + ": ${e.message}")
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * 创建高清账单图片 - 使用更高的分辨率和更好的渲染质量
     */
    private fun createBillImageHighQuality(context: Context, data: ExportData): Bitmap {
        // 使用更高的缩放因子以获得更清晰的图片
        val scaleFactor = 2.5f
        
        // 基础尺寸（以dp为单位）
        val basePadding = 40
        val baseRowHeight = 70
        val baseHeaderHeight = 90
        val baseTitleHeight = 80
        val baseColWidths = intArrayOf(220, 110, 110, 150)
        
        // 计算实际像素尺寸
        val padding = (basePadding * scaleFactor).toInt()
        val rowHeight = (baseRowHeight * scaleFactor).toInt()
        val headerHeight = (baseHeaderHeight * scaleFactor).toInt()
        val titleHeight = (baseTitleHeight * scaleFactor).toInt()
        val colWidths = baseColWidths.map { (it * scaleFactor).toInt() }.toIntArray()
        
        val tableWidth = colWidths.sum()
        val totalWidth = tableWidth + padding * 2
        val totalHeight = titleHeight + headerHeight + (data.items.size + 1) * rowHeight + padding * 2

        // 创建高分辨率Bitmap
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 白色背景
        canvas.drawColor(Color.WHITE)

        // 使用更高质量的Paint设置
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            isFilterBitmap = true
            isSubpixelText = true
        }

        // 绘制标题 - 更大的字体
        val titleTextSize = 36f * scaleFactor
        paint.apply {
            color = Color.BLACK
            textSize = titleTextSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            data.bill.getDisplayAddress(),
            totalWidth / 2f,
            padding + titleTextSize,
            paint
        )

        // 表格起始位置
        val tableTop = padding + titleHeight
        val tableLeft = padding

        // 绘制表头背景
        paint.apply {
            color = Color.parseColor("#F5F5F5")
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            tableLeft.toFloat(),
            tableTop.toFloat(),
            (tableLeft + tableWidth).toFloat(),
            (tableTop + headerHeight).toFloat(),
            paint
        )

        // 绘制表格边框和文字
        val strokeWidth = 2f * scaleFactor
        paint.apply {
            color = Color.BLACK
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
        }

        // 绘制外边框
        canvas.drawRect(
            tableLeft.toFloat(),
            tableTop.toFloat(),
            (tableLeft + tableWidth).toFloat(),
            (tableTop + headerHeight + (data.items.size + 1) * rowHeight).toFloat(),
            paint
        )

        // 绘制列分隔线
        var currentX = tableLeft
        for (i in 0 until colWidths.size - 1) {
            currentX += colWidths[i]
            canvas.drawLine(
                currentX.toFloat(),
                tableTop.toFloat(),
                currentX.toFloat(),
                (tableTop + headerHeight + (data.items.size + 1) * rowHeight).toFloat(),
                paint
            )
        }

        // 绘制行分隔线
        var currentY = tableTop + headerHeight
        for (i in 0..data.items.size) {
            canvas.drawLine(
                tableLeft.toFloat(),
                currentY.toFloat(),
                (tableLeft + tableWidth).toFloat(),
                currentY.toFloat(),
                paint
            )
            currentY += rowHeight
        }

        // 绘制表头文字 - 更大的字体
        val headerTextSize = 24f * scaleFactor
        paint.apply {
            style = Paint.Style.FILL
            textSize = headerTextSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val headers = arrayOf("项目名称", "单价", "数量", "金额")
        currentX = tableLeft
        headers.forEachIndexed { index, header ->
            canvas.drawText(
                header,
                (currentX + colWidths[index] / 2).toFloat(),
                (tableTop + headerHeight / 2 + headerTextSize / 3).toFloat(),
                paint
            )
            currentX += colWidths[index]
        }

        // 绘制数据行 - 所有列居中对齐
        val dataTextSize = 20f * scaleFactor
        paint.apply {
            textSize = dataTextSize
            typeface = Typeface.DEFAULT
        }
        data.items.forEachIndexed { rowIndex, item ->
            val rowTop = tableTop + headerHeight + rowIndex * rowHeight
            currentX = tableLeft

            val values = arrayOf(
                item.projectName,
                "¥${String.format("%.2f", item.unitPrice)}",
                "${item.quantity}",
                "¥${String.format("%.2f", item.totalPrice)}"
            )

            values.forEachIndexed { colIndex, value ->
                canvas.drawText(
                    value,
                    (currentX + colWidths[colIndex] / 2).toFloat(),
                    (rowTop + rowHeight / 2 + dataTextSize / 3).toFloat(),
                    paint
                )
                currentX += colWidths[colIndex]
            }
        }

        // 绘制合计行
        val totalRowTop = tableTop + headerHeight + data.items.size * rowHeight
        val totalTextSize = 22f * scaleFactor

        paint.apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = totalTextSize
        }

        // 第一列：合计
        canvas.drawText(
            "合计",
            (tableLeft + colWidths[0] / 2).toFloat(),
            (totalRowTop + rowHeight / 2 + totalTextSize / 3).toFloat(),
            paint
        )

        // 第四列：合计金额（居中对齐）
        canvas.drawText(
            "¥${String.format("%.2f", data.items.sumOf { it.totalPrice })}",
            (tableLeft + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] / 2).toFloat(),
            (totalRowTop + rowHeight / 2 + totalTextSize / 3).toFloat(),
            paint
        )

        return bitmap
    }

    /**
     * 保存高清图片到相册
     */
    private fun saveImageToGalleryHighQuality(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/大喜记账")
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        // 使用最高质量压缩
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "大喜记账")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = File(appDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }

            onSuccess()
        } catch (e: Exception) {
            onError(context.getString(R.string.export_failed) + ": ${e.message}")
        }
    }

    /**
     * 分享高清图片
     */
    private fun shareImageToAppHighQuality(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val cacheFile = File(context.cacheDir, fileName)
            FileOutputStream(cacheFile).use { outputStream ->
                // 使用最高质量压缩
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            shareFileToApp(context, cacheFile, "image/png", onSuccess, onError)
        } catch (e: Exception) {
            onError(context.getString(R.string.share_failed) + ": ${e.message}")
        }
    }

    private fun saveFileToDownloads(
        context: Context,
        file: File,
        fileName: String,
        mimeType: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/大喜记账")
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        file.inputStream().copyTo(outputStream)
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "大喜记账")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val destFile = File(appDir, fileName)
                file.copyTo(destFile, overwrite = true)
            }

            onSuccess()
        } catch (e: Exception) {
            onError(context.getString(R.string.export_failed) + ": ${e.message}")
        }
    }

    private fun shareFileToApp(
        context: Context,
        file: File,
        mimeType: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // 不指定特定应用，让用户选择
            }

            val chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.share_to_app))
            
            if (shareIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooserIntent)
                onSuccess()
            } else {
                onError(context.getString(R.string.no_app_to_share))
            }
        } catch (e: Exception) {
            onError(context.getString(R.string.share_failed) + ": ${e.message}")
        }
    }
}
