package com.example.daxijizhang.util

import com.example.daxijizhang.data.model.ProjectDictionary
import org.junit.Test
import org.junit.Assert.*

class ProjectDictionarySortingTest {

    @Test
    fun testBasicChineseSorting() {
        val projects = listOf(
            createProject("曲阳名邸"),
            createProject("陈帐"),
            createProject("黎明左岸")
        )
        
        println("=== 基础中文排序测试 ===")
        println("原始顺序: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后: ${sorted.map { it.name }.joinToString(", ")}")
        println("预期: 陈帐, 黎明左岸, 曲阳名邸")
        
        assertEquals("陈帐", sorted[0].name)
        assertEquals("黎明左岸", sorted[1].name)
        assertEquals("曲阳名邸", sorted[2].name)
    }
    
    @Test
    fun testPolyphonicCharacters() {
        val projects = listOf(
            createProject("重庆"),
            createProject("重量"),
            createProject("银行"),
            createProject("行走"),
            createProject("音乐"),
            createProject("快乐")
        )
        
        println("\n=== 多音字排序测试 ===")
        println("原始顺序: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后: ${sorted.map { it.name }.joinToString(", ")}")
        
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name}")
        }
    }
    
    @Test
    fun testMixedChineseEnglishNumbers() {
        val projects = listOf(
            createProject("Zoo项目"),
            createProject("Apple服务"),
            createProject("曲阳名邸"),
            createProject("陈帐"),
            createProject("123工程"),
            createProject("黎明左岸"),
            createProject("abc测试"),
            createProject("XYZ项目")
        )
        
        println("\n=== 中英文数字混合排序测试 ===")
        println("原始顺序: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后:")
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name}")
        }
    }
    
    @Test
    fun testSpecialCharacters() {
        val projects = listOf(
            createProject("项目A"),
            createProject("项目B"),
            createProject("项目(特殊)"),
            createProject("项目-测试"),
            createProject("项目_下划线"),
            createProject("项目@符号")
        )
        
        println("\n=== 特殊字符排序测试 ===")
        println("原始顺序: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后:")
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name}")
        }
    }
    
    @Test
    fun testSamePinyinDifferentCharacters() {
        val projects = listOf(
            createProject("北京"),
            createProject("背景"),
            createProject("北经"),
            createProject("上海"),
            createProject("商海")
        )
        
        println("\n=== 相同拼音不同汉字排序测试 ===")
        println("原始顺序: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后:")
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name}")
        }
    }
    
    @Test
    fun testCaseInsensitiveEnglish() {
        val projects = listOf(
            createProject("alpha"),
            createProject("Alpha"),
            createProject("ALPHA"),
            createProject("beta"),
            createProject("Beta"),
            createProject("BETA"),
            createProject("gamma"),
            createProject("Gamma")
        )
        
        println("\n=== 英文大小写排序测试 ===")
        println("原始顺序: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后:")
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name}")
        }
    }
    
    @Test
    fun testDefaultProjectsSorting() {
        val defaultProjects = ProjectDictionary.DEFAULT_PROJECTS.map { createProject(it) }
        
        println("\n=== 默认项目词典排序测试 ===")
        println("原始默认项目: ${ProjectDictionary.DEFAULT_PROJECTS.joinToString(", ")}")
        
        val sorted = defaultProjects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后:")
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name}")
        }
    }
    
    @Test
    fun testLongProjectNames() {
        val projects = listOf(
            createProject("这是一个非常长的项目名称用于测试"),
            createProject("短名"),
            createProject("中等长度的名称"),
            createProject("A"),
            createProject("一二三四五六七八九十")
        )
        
        println("\n=== 长项目名称排序测试 ===")
        println("原始顺序: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后:")
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name}")
        }
    }
    
    @Test
    fun testPrefixMatching() {
        val projects = listOf(
            createProject("地砖"),
            createProject("大地砖"),
            createProject("小地砖"),
            createProject("瓷砖"),
            createProject("地板"),
            createProject("地面")
        )
        
        println("\n=== 前缀匹配排序测试 ===")
        println("原始顺序: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后:")
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name}")
        }
    }
    
    @Test
    fun testStability() {
        val projects = listOf(
            createProject("测试A"),
            createProject("测试B"),
            createProject("测试C"),
            createProject("测试A"),
            createProject("测试B")
        )
        
        println("\n=== 排序稳定性测试 ===")
        println("原始顺序（含重复）: ${projects.map { it.name }.joinToString(", ")}")
        
        val sorted = projects.sortedWith { p1, p2 ->
            PinyinUtil.compareForSort(p1.name, p2.name)
        }
        
        println("排序后:")
        sorted.forEachIndexed { index, project ->
            println("  ${index + 1}. ${project.name} (id: ${project.id})")
        }
    }
    
    private fun createProject(name: String): ProjectDictionary {
        return ProjectDictionary(
            id = name.hashCode().toLong(),
            name = name,
            createTime = System.currentTimeMillis(),
            updateTime = System.currentTimeMillis()
        )
    }
}
