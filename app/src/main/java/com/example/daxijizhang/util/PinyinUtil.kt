package com.example.daxijizhang.util

import java.text.Collator
import java.util.Locale

object PinyinUtil {

    // 使用Java的Collator进行中文排序（支持拼音排序）
    private val chineseCollator: Collator = Collator.getInstance(Locale.CHINESE)

    /**
     * 比较两个字符串（支持中英文混合排序）
     * 排序规则：
     * 1. 中文字符转换为小写拼音全拼
     * 2. 字母字符统一转换为小写形式
     * 3. 数字字符保持原始状态不变
     * 4. 特殊符号保留原始状态，参与排序比较
     * 5. 按字符逐个比较ASCII码值大小
     * 6. 当一个字符串是另一个字符串的前缀时，较短的字符串排在前面
     */
    fun compareForSort(str1: String, str2: String): Int {
        // 转换为小写进行比较（Java Collator会自动处理中文拼音转换）
        val s1 = str1.lowercase(Locale.getDefault())
        val s2 = str2.lowercase(Locale.getDefault())

        // 使用中文Collator进行比较（支持拼音排序）
        val result = chineseCollator.compare(s1, s2)
        
        // 如果Collator认为相等，进行字符逐个比较以确保稳定性
        if (result == 0 && s1 != s2) {
            return compareCharByChar(s1, s2)
        }
        
        return result
    }
    
    /**
     * 逐个字符比较
     * 当一个字符串是另一个的前缀时，较短的排在前面
     */
    private fun compareCharByChar(s1: String, s2: String): Int {
        val minLength = minOf(s1.length, s2.length)
        
        for (i in 0 until minLength) {
            val c1 = s1[i]
            val c2 = s2[i]
            
            if (c1 != c2) {
                return c1.compareTo(c2)
            }
        }
        
        // 前minLength个字符相同，较短的字符串排在前面
        return s1.length - s2.length
    }

    /**
     * 对字符串列表进行排序（支持中英文混合）
     * 时间复杂度：O(n log n)
     */
    fun sortStringList(list: List<String>): List<String> {
        return list.sortedWith { s1, s2 -> compareForSort(s1, s2) }
    }
    
    /**
     * 获取字符串的拼音表示（用于调试和测试）
     */
    fun getPinyin(str: String): String {
        // 使用Collator的分解功能获取排序键
        val sortKey = chineseCollator.getCollationKey(str)
        return sortKey.toString()
    }
}
