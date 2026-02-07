package com.example.daxijizhang.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 项目词典实体类
 * 用于存储用户自定义的装修项目名称，支持智能提示功能
 */
@Entity(
    tableName = "project_dictionary",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["usageCount"])
    ]
)
data class ProjectDictionary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val usageCount: Int = 0,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 默认项目词典数据
         */
        val DEFAULT_PROJECTS = listOf(
            "地砖",
            "墙砖",
            "地板",
            "墙面漆",
            "吊顶",
            "橱柜",
            "卫浴",
            "灯具",
            "开关插座",
            "门窗",
            "窗帘",
            "家具",
            "家电",
            "水电改造",
            "防水工程",
            "泥瓦工程",
            "木工工程",
            "油漆工程",
            "美缝",
            "踢脚线"
        )
    }
}
