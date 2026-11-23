package com.trisvax.studio.ui.projects

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trisvax.studio.TrisvaxApplication
import com.trisvax.studio.database.ProjectEntity
import com.trisvax.studio.models.ProjectCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val selectedCategory: ProjectCategory = ProjectCategory.ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val totalCount: Int = 0,
    val totalStorageBytes: Long = 0L
)

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TrisvaxApplication
    private val repository = app.projectRepository
    private val prefs = app.preferencesManager

    private val searchQuery = MutableStateFlow("")
    private val isSearchActive = MutableStateFlow(false)
    private val selectedCategory = MutableStateFlow(ProjectCategory.ALL)

    init {
        viewModelScope.launch {
            prefs.lastCategoryFlow.collect { saved ->
                runCatching { ProjectCategory.valueOf(saved) }
                    .getOrNull()
                    ?.let { selectedCategory.value = it }
            }
        }
    }

    val uiState: StateFlow<ProjectsUiState> = combine(
        repository.observeProjects(),
        selectedCategory,
        searchQuery,
        isSearchActive
    ) { allProjects, category, query, searchOpen ->
        val filtered = allProjects.filter { project ->
            val categoryMatch = category == ProjectCategory.ALL ||
                project.category.equals(category.name, ignoreCase = true)
            val queryMatch = query.isBlank() ||
                project.name.contains(query.trim(), ignoreCase = true)
            categoryMatch && queryMatch
        }
        ProjectsUiState(
            projects = filtered,
            selectedCategory = category,
            searchQuery = query,
            isSearchActive = searchOpen,
            totalCount = allProjects.size,
            totalStorageBytes = allProjects.sumOf { it.storageSizeBytes }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ProjectsUiState()
    )

    fun selectCategory(category: ProjectCategory) {
        selectedCategory.value = category
        viewModelScope.launch { prefs.saveLastCategory(category.name) }
    }

    fun toggleSearch(active: Boolean) {
        isSearchActive.value = active
        if (!active) searchQuery.value = ""
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            repository.renameProject(projectId, newName)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun createEmptyProject(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.createEmptyProject("New project")
            onCreated(id)
        }
    }

    fun createProjectFromUris(uris: List<Uri>, onCreated: (String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val id = repository.createProjectFromUris(uris)
            onCreated(id)
        }
    }
}
