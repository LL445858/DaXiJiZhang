package com.example.daxijizhang.util

import org.junit.Test
import org.junit.Assert.*

class UnicodeSortTest {

    @Test
    fun testUnicodeCodePoints() {
        val chars = listOf('曲', '陈', '黎')
        
        println("=== Unicode码点分析 ===")
        chars.forEach { c ->
            println("'$c' -> Unicode: U+${Integer.toHexString(c.code).uppercase()} (${c.code})")
        }
        
        println("\n=== Unicode码点排序 ===")
        val sortedByUnicode = chars.sortedBy { it.code }
        sortedByUnicode.forEach { print("$it ") }
        println()
        
        println("\n=== 拼音排序 ===")
        val pinyinOrder = listOf("曲阳名邸", "陈帐", "黎明左岸")
        pinyinOrder.forEach { print("$it ") }
        println()
        
        println("\n=== SQLite ORDER BY communityName ASC 模拟 ===")
        val communityNames = listOf("曲阳名邸", "陈帐", "黎明左岸")
        val sqliteSorted = communityNames.sortedBy { it }
        println("SQLite排序结果: ${sqliteSorted.joinToString(", ")}")
        
        println("\n=== PinyinUtil排序 ===")
        val pinyinSorted = PinyinUtil.sortStringList(communityNames)
        println("PinyinUtil排序结果: ${pinyinSorted.joinToString(", ")}")
    }
}
