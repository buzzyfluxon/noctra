package com.noctra.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDs by preferencesDataStore("noctra_settings")

data class AppSettings(
    val onboarded: Boolean = false,
    val wakeEnabled: Boolean = true,
    val wakeHour: Int = 10,
    val wakeMinute: Int = 0,
    val intervalMin: Int = 60,
    val tone: String = "BRUTAL",
    val customSubjects: List<String> = emptyList()
)

class SettingsStore(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val wakeEnabled = booleanPreferencesKey("wake_enabled")
        val wakeHour = intPreferencesKey("wake_hour")
        val wakeMinute = intPreferencesKey("wake_minute")
        val interval = intPreferencesKey("interval_min")
        val tone = stringPreferencesKey("tone")
        val subjects = stringSetPreferencesKey("custom_subjects")
    }

    val flow: Flow<AppSettings> = context.settingsDs.data.map { p ->
        AppSettings(
            onboarded = p[K.onboarded] ?: false,
            wakeEnabled = p[K.wakeEnabled] ?: true,
            wakeHour = p[K.wakeHour] ?: 10,
            wakeMinute = p[K.wakeMinute] ?: 0,
            intervalMin = p[K.interval] ?: 60,
            tone = p[K.tone] ?: "BRUTAL",
            customSubjects = (p[K.subjects] ?: emptySet()).sorted()
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setOnboarded(v: Boolean) {
        context.settingsDs.edit { it[K.onboarded] = v }
    }

    suspend fun setWakeEnabled(v: Boolean) {
        context.settingsDs.edit { it[K.wakeEnabled] = v }
    }

    suspend fun setWakeTime(hour: Int, minute: Int) {
        context.settingsDs.edit {
            it[K.wakeHour] = hour
            it[K.wakeMinute] = minute
        }
    }

    suspend fun setInterval(min: Int) {
        context.settingsDs.edit { it[K.interval] = min }
    }

    suspend fun setTone(tone: String) {
        context.settingsDs.edit { it[K.tone] = tone }
    }

    suspend fun addSubject(name: String) {
        context.settingsDs.edit { it[K.subjects] = (it[K.subjects] ?: emptySet()) + name }
    }

    suspend fun removeSubject(name: String) {
        context.settingsDs.edit { it[K.subjects] = (it[K.subjects] ?: emptySet()) - name }
    }
}
