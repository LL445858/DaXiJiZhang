package com.example.daxijizhang.util

import java.text.Collator
import java.util.Locale

object PinyinUtil {

    // 使用Java的Collator进行中文排序（支持拼音排序）
    private val chineseCollator: Collator = Collator.getInstance(Locale.CHINESE)

    /**
     * 比较两个字符串（支持中英文混合排序）
     * 英文转换为小写后比较
     * 中文按拼音排序
     */
    fun compareForSort(str1: String, str2: String): Int {
        // 转换为小写进行比较
        val s1 = str1.lowercase(Locale.getDefault())
        val s2 = str2.lowercase(Locale.getDefault())

        // 使用中文Collator进行比较（支持拼音排序）
        return chineseCollator.compare(s1, s2)
    }

    /**
     * 对字符串列表进行排序（支持中英文混合）
     */
    fun sortStringList(list: List<String>): List<String> {
        return list.sortedWith { s1, s2 -> compareForSort(s1, s2) }
    }
}
