package com.example.daxijizhang.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_dictionary",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class ProjectDictionary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
) {
    companion object {
        val DEFAULT_PROJECTS = listOf(
            "卫生间地",
            "卫生间墙",
            "厨房地",
            "厨房墙",
            "阳台地",
            "阳台墙",
            "阳台梁",
            "窗边",
            "窗角",
            "楼梯",
            "防水",
            "刷背胶",
            "柱角",
            "脚线",
            "包水管",
            "隔音棉",
            "刷防水",
            "灶台",
            "垃圾清理",
            "淋浴房",
            "壁龛",
            "杂活"
        )
    }
}
