package com.example.daxijizhang.data.repository

import com.example.daxijizhang.data.dao.ProjectDictionaryDao
import com.example.daxijizhang.data.model.ProjectDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProjectDictionaryRepository(private val projectDictionaryDao: ProjectDictionaryDao) {

    fun getAllProjects(): Flow<List<ProjectDictionary>> = projectDictionaryDao.getAllProjects()

    suspend fun getAllProjectsSync(): List<ProjectDictionary> = withContext(Dispatchers.IO) {
        projectDictionaryDao.getAllProjectsSync()
    }

    suspend fun getProjectById(id: Long): ProjectDictionary? = withContext(Dispatchers.IO) {
        projectDictionaryDao.getProjectById(id)
    }

    suspend fun getProjectByName(name: String): ProjectDictionary? = withContext(Dispatchers.IO) {
        projectDictionaryDao.getProjectByName(name)
    }

    suspend fun searchProjects(query: String): List<ProjectDictionary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            emptyList()
        } else {
            projectDictionaryDao.searchProjects(query)
        }
    }

    suspend fun getAllProjectNames(): List<String> = withContext(Dispatchers.IO) {
        projectDictionaryDao.getAllProjectNames()
    }

    suspend fun insertProject(project: ProjectDictionary): Long = withContext(Dispatchers.IO) {
        projectDictionaryDao.insertProject(project)
    }

    suspend fun insertProjects(projects: List<ProjectDictionary>): List<Long> = withContext(Dispatchers.IO) {
        projectDictionaryDao.insertProjects(projects)
    }

    suspend fun updateProject(project: ProjectDictionary) = withContext(Dispatchers.IO) {
        projectDictionaryDao.updateProject(project)
    }

    suspend fun addOrGetProject(name: String): Long = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isNotEmpty()) {
            val existingProject = projectDictionaryDao.getProjectByName(trimmedName)
            if (existingProject != null) {
                existingProject.id
            } else {
                val newProject = ProjectDictionary(name = trimmedName)
                projectDictionaryDao.insertProject(newProject)
            }
        } else {
            0L
        }
    }

    suspend fun deleteProject(project: ProjectDictionary) = withContext(Dispatchers.IO) {
        projectDictionaryDao.deleteProject(project)
    }

    suspend fun deleteProjectById(id: Long) = withContext(Dispatchers.IO) {
        projectDictionaryDao.deleteProjectById(id)
    }

    suspend fun deleteAllProjects() = withContext(Dispatchers.IO) {
        projectDictionaryDao.deleteAllProjects()
    }

    suspend fun getProjectCount(): Int = withContext(Dispatchers.IO) {
        projectDictionaryDao.getProjectCount()
    }

    suspend fun isProjectNameExists(name: String): Boolean = withContext(Dispatchers.IO) {
        projectDictionaryDao.isProjectNameExists(name) > 0
    }

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
