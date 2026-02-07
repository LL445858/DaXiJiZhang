package com.example.daxijizhang.data.repository

import com.example.daxijizhang.data.dao.ProjectDictionaryDao
import com.example.daxijizhang.data.model.ProjectDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 项目词典仓库类
 * 提供项目词典数据的访问和管理
 */
class ProjectDictionaryRepository(private val projectDictionaryDao: ProjectDictionaryDao) {

    /**
     * 获取所有项目词典条目
     */
    fun getAllProjects(): Flow<List<ProjectDictionary>> = projectDictionaryDao.getAllProjects()

    /**
     * 获取所有项目词典条目（同步）
     */
    suspend fun getAllProjectsSync(): List<ProjectDictionary> = withContext(Dispatchers.IO) {
        projectDictionaryDao.getAllProjectsSync()
    }

    /**
     * 根据ID获取项目词典条目
     */
    suspend fun getProjectById(id: Long): ProjectDictionary? = withContext(Dispatchers.IO) {
        projectDictionaryDao.getProjectById(id)
    }

    /**
     * 根据名称获取项目词典条目
     */
    suspend fun getProjectByName(name: String): ProjectDictionary? = withContext(Dispatchers.IO) {
        projectDictionaryDao.getProjectByName(name)
    }

    /**
     * 搜索项目（智能提示功能）
     * @param query 查询字符串
     * @return 匹配的项目列表，按使用频率降序排列
     */
    suspend fun searchProjects(query: String): List<ProjectDictionary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            emptyList()
        } else {
            projectDictionaryDao.searchProjects(query)
        }
    }

    /**
     * 获取所有项目名称
     */
    suspend fun getAllProjectNames(): List<String> = withContext(Dispatchers.IO) {
        projectDictionaryDao.getAllProjectNames()
    }

    /**
     * 插入项目词典条目
     */
    suspend fun insertProject(project: ProjectDictionary): Long = withContext(Dispatchers.IO) {
        projectDictionaryDao.insertProject(project)
    }

    /**
     * 批量插入项目词典条目
     */
    suspend fun insertProjects(projects: List<ProjectDictionary>): List<Long> = withContext(Dispatchers.IO) {
        projectDictionaryDao.insertProjects(projects)
    }

    /**
     * 更新项目词典条目
     */
    suspend fun updateProject(project: ProjectDictionary) = withContext(Dispatchers.IO) {
        projectDictionaryDao.updateProject(project)
    }

    /**
     * 增加使用频率
     */
    suspend fun incrementUsageCount(id: Long) = withContext(Dispatchers.IO) {
        projectDictionaryDao.incrementUsageCount(id)
    }

    /**
     * 根据名称增加使用频率
     * 如果项目不存在，则自动创建
     */
    suspend fun incrementUsageCountByName(name: String) = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isNotEmpty()) {
            val existingProject = projectDictionaryDao.getProjectByName(trimmedName)
            if (existingProject != null) {
                projectDictionaryDao.incrementUsageCountByName(trimmedName)
            } else {
                // 如果不存在，创建新项目
                val newProject = ProjectDictionary(name = trimmedName, usageCount = 1)
                projectDictionaryDao.insertProject(newProject)
            }
        }
    }

    /**
     * 删除项目词典条目
     */
    suspend fun deleteProject(project: ProjectDictionary) = withContext(Dispatchers.IO) {
        projectDictionaryDao.deleteProject(project)
    }

    /**
     * 根据ID删除项目词典条目
     */
    suspend fun deleteProjectById(id: Long) = withContext(Dispatchers.IO) {
        projectDictionaryDao.deleteProjectById(id)
    }

    /**
     * 清空所有项目词典条目
     */
    suspend fun deleteAllProjects() = withContext(Dispatchers.IO) {
        projectDictionaryDao.deleteAllProjects()
    }

    /**
     * 获取项目词典条目数量
     */
    suspend fun getProjectCount(): Int = withContext(Dispatchers.IO) {
        projectDictionaryDao.getProjectCount()
    }

    /**
     * 检查项目名称是否已存在
     */
    suspend fun isProjectNameExists(name: String): Boolean = withContext(Dispatchers.IO) {
        projectDictionaryDao.isProjectNameExists(name) > 0
    }

    /**
     * 初始化默认项目词典数据
     */
    suspend fun initializeDefaultProjects() = withContext(Dispatchers.IO) {
        val count = projectDictionaryDao.getProjectCount()
        if (count == 0) {
            val defaultProjects = ProjectDictionary.DEFAULT_PROJECTS.map { name ->
                ProjectDictionary(name = name)
            }
            projectDictionaryDao.insertProjects(defaultProjects)
        }
    }
}
