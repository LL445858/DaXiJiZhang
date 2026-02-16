package com.example.daxijizhang.util

import org.junit.Test
import org.junit.Assert.*

class PinyinUtilTest {

    @Test
    fun testChineseSorting() {
        val list = listOf("曲阳名邸", "陈帐", "黎明左岸")
        
        println("=== 原始列表 ===")
        list.forEach { println(it) }
        
        val sorted = PinyinUtil.sortStringList(list)
        
        println("\n=== 排序后列表 ===")
        sorted.forEach { println(it) }
        
        println("\n=== 拼音表示 ===")
        list.forEach { 
            println("$it -> ${PinyinUtil.getPinyin(it)}")
        }
        
        println("\n=== 逐对比较结果 ===")
        println("'陈帐' vs '黎明左岸': ${PinyinUtil.compareForSort("陈帐", "黎明左岸")}")
        println("'陈帐' vs '曲阳名邸': ${PinyinUtil.compareForSort("陈帐", "曲阳名邸")}")
        println("'黎明左岸' vs '曲阳名邸': ${PinyinUtil.compareForSort("黎明左岸", "曲阳名邸")}")
    }
    
    @Test
    fun testCollatorBehavior() {
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINESE)
        
        println("=== Java Collator 直接比较 ===")
        println("'陈帐' vs '黎明左岸': ${collator.compare("陈帐", "黎明左岸")}")
        println("'陈帐' vs '曲阳名邸': ${collator.compare("陈帐", "曲阳名邸")}")
        println("'黎明左岸' vs '曲阳名邸': ${collator.compare("黎明左岸", "曲阳名邸")}")
        
        println("\n=== CollationKey 分析 ===")
        listOf("陈帐", "黎明左岸", "曲阳名邸").forEach { str ->
            val key = collator.getCollationKey(str)
            println("$str -> ${key.toByteArray().toList()}")
        }
    }
    
    @Test
    fun testExpectedSorting() {
        val list = listOf("曲阳名邸", "陈帐", "黎明左岸")
        val sorted = PinyinUtil.sortStringList(list)
        
        println("\n预期顺序: 陈帐 < 黎明左岸 < 曲阳名邸")
        println("实际顺序: ${sorted.joinToString(", ")}")
        
        assertEquals("陈帐", sorted[0])
        assertEquals("黎明左岸", sorted[1])
        assertEquals("曲阳名邸", sorted[2])
    }
}
