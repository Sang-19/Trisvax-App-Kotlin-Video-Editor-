package com.trisvax.studio.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "trisvax_preferences")

class PreferencesManager(private val context: Context) {
    private val lastCategoryKey = stringPreferencesKey("last_project_category")

    val lastCategoryFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[lastCategoryKey] ?: "ALL"
    }

    suspend fun saveLastCategory(category: String) {
        context.dataStore.edit { prefs ->
            prefs[lastCategoryKey] = category
        }
    }
}
