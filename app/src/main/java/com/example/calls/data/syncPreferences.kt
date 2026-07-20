package com.example.calls.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "call_sync_prefs")

object SyncPreferences {
    private val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
    private val UPLOADER_NAME = stringPreferencesKey("uploader_name")
    private val SIM_ACCOUNT_ID = stringPreferencesKey("sim_account_id")
    fun getLastSyncMillis(context: Context): Flow<Long> {
        return context.dataStore.data.map { prefs ->
            prefs[LAST_SYNC_MILLIS] ?: 0L
        }
    }

    suspend fun setLastSyncMillis(context: Context, millis: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SYNC_MILLIS] = millis
        }
    }
    fun getUploaderName(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            prefs[UPLOADER_NAME]
        }
    }
    suspend fun setUploaderName(context: Context, name: String) {
        context.dataStore.edit { prefs ->
            prefs[UPLOADER_NAME] = name
        }
    }
    fun getSimAccountId(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            prefs[SIM_ACCOUNT_ID]
        }
    }
    suspend fun setSimAccountId(context: Context, id: String) {
        context.dataStore.edit { prefs ->
            prefs[SIM_ACCOUNT_ID] = id
        }
    }
}