package com.trisvax.studio

import android.app.Application
import com.trisvax.studio.data.MusicAssetCatalog
import com.trisvax.studio.data.PreferencesManager
import com.trisvax.studio.database.TrisvaxDatabase
import com.trisvax.studio.repository.ProjectRepository

class TrisvaxApplication : Application() {
    val database: TrisvaxDatabase by lazy { TrisvaxDatabase.getInstance(this) }
    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(this, database.projectDao())
    }
    val musicAssetCatalog: MusicAssetCatalog by lazy { MusicAssetCatalog(this) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
}
