package com.example.daxijizhang.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.daxijizhang.data.model.ProjectDictionary
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDictionaryDao {

    @Query("SELECT * FROM project_dictionary ORDER BY updateTime DESC")
    fun getAllProjects(): Flow<List<ProjectDictionary>>

    @Query("SELECT * FROM project_dictionary ORDER BY updateTime DESC")
    suspend fun getAllProjectsSync(): List<ProjectDictionary>

    @Query("SELECT * FROM project_dictionary WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectDictionary?

    @Query("SELECT * FROM project_dictionary WHERE name = :name")
    suspend fun getProjectByName(name: String): ProjectDictionary?

    @Query("SELECT * FROM project_dictionary WHERE name LIKE '%' || :query || '%' AND name != :query ORDER BY updateTime DESC")
    suspend fun searchProjects(query: String): List<ProjectDictionary>

    @Query("SELECT name FROM project_dictionary ORDER BY updateTime DESC")
    suspend fun getAllProjectNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectDictionary): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjects(projects: List<ProjectDictionary>): List<Long>

    @Update
    suspend fun updateProject(project: ProjectDictionary)

    @Delete
    suspend fun deleteProject(project: ProjectDictionary)

    @Query("DELETE FROM project_dictionary WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("DELETE FROM project_dictionary")
    suspend fun deleteAllProjects()

    @Query("SELECT COUNT(*) FROM project_dictionary")
    suspend fun getProjectCount(): Int

    @Query("SELECT COUNT(*) FROM project_dictionary WHERE name = :name")
    suspend fun isProjectNameExists(name: String): Int
}
