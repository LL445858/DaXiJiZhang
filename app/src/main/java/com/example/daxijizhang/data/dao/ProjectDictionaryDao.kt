package com.example.daxijizhang.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.daxijizhang.data.model.ProjectDictionary
import kotlinx.coroutines.flow.Flow

/**
 * 项目词典数据访问对象
 */
@Dao
interface ProjectDictionaryDao {

    /**
     * 获取所有项目词典条目，按使用频率降序排列
     */
    @Query("SELECT * FROM project_dictionary ORDER BY usageCount DESC, updateTime DESC")
    fun getAllProjects(): Flow<List<ProjectDictionary>>

    /**
     * 获取所有项目词典条目（同步）
     */
    @Query("SELECT * FROM project_dictionary ORDER BY usageCount DESC, updateTime DESC")
    suspend fun getAllProjectsSync(): List<ProjectDictionary>

    /**
     * 根据ID获取项目词典条目
     */
    @Query("SELECT * FROM project_dictionary WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectDictionary?

    /**
     * 根据名称获取项目词典条目
     */
    @Query("SELECT * FROM project_dictionary WHERE name = :name")
    suspend fun getProjectByName(name: String): ProjectDictionary?

    /**
     * 搜索项目名称（用于智能提示）
     * 返回名称包含查询字符串但不完全匹配的项目，按使用频率降序排列
     * 例如：输入"地砖"时显示"大地砖"，不显示"地砖"
     */
    @Query("SELECT * FROM project_dictionary WHERE name LIKE '%' || :query || '%' AND name != :query ORDER BY usageCount DESC, updateTime DESC")
    suspend fun searchProjects(query: String): List<ProjectDictionary>

    /**
     * 获取所有项目名称列表（仅名称）
     */
    @Query("SELECT name FROM project_dictionary ORDER BY usageCount DESC")
    suspend fun getAllProjectNames(): List<String>

    /**
     * 插入项目词典条目
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectDictionary): Long

    /**
     * 批量插入项目词典条目
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjects(projects: List<ProjectDictionary>): List<Long>

    /**
     * 更新项目词典条目
     */
    @Update
    suspend fun updateProject(project: ProjectDictionary)

    /**
     * 增加使用频率
     */
    @Query("UPDATE project_dictionary SET usageCount = usageCount + 1, updateTime = :updateTime WHERE id = :id")
    suspend fun incrementUsageCount(id: Long, updateTime: Long = System.currentTimeMillis())

    /**
     * 根据名称增加使用频率
     */
    @Query("UPDATE project_dictionary SET usageCount = usageCount + 1, updateTime = :updateTime WHERE name = :name")
    suspend fun incrementUsageCountByName(name: String, updateTime: Long = System.currentTimeMillis())

    /**
     * 删除项目词典条目
     */
    @Delete
    suspend fun deleteProject(project: ProjectDictionary)

    /**
     * 根据ID删除项目词典条目
     */
    @Query("DELETE FROM project_dictionary WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    /**
     * 清空所有项目词典条目
     */
    @Query("DELETE FROM project_dictionary")
    suspend fun deleteAllProjects()

    /**
     * 获取项目词典条目数量
     */
    @Query("SELECT COUNT(*) FROM project_dictionary")
    suspend fun getProjectCount(): Int

    /**
     * 检查项目名称是否已存在
     */
    @Query("SELECT COUNT(*) FROM project_dictionary WHERE name = :name")
    suspend fun isProjectNameExists(name: String): Int
}
