package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import com.example.prayer.CalculationMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.noorDataStore: DataStore<Preferences> by preferencesDataStore(name = "noor_prefs")

data class NoorUserSettings(
    val darkMode: Boolean = true,
    val language: AppLanguage = AppLanguage.AR,
    val lastPage: Int = 1,
    val lastSurah: Int = 1,
    val lastAyah: Int = 1,
    val selectedReciterId: String = "qatami",
    val playbackSpeed: Float = 1.0f,
    val autoScrollSpeed: Float = 1.0f,
    val cityId: String = "damascus",
    val latitude: Double = 33.5138,
    val longitude: Double = 36.2765,
    val calculationMethod: CalculationMethod = CalculationMethod.DAMASCUS_SYRIA,
    val fajrOffsetMin: Int = 0,
    val dhuhrOffsetMin: Int = 0,
    val asrOffsetMin: Int = 0,
    val maghribOffsetMin: Int = 0,
    val ishaOffsetMin: Int = 0,
    val notifyFajr: Boolean = true,
    val notifyDhuhr: Boolean = true,
    val notifyAsr: Boolean = true,
    val notifyMaghrib: Boolean = true,
    val notifyIsha: Boolean = true,
    val selectedAdhanVoiceId: String = "adhan_qatami",
    val allowOnlineAudioStreaming: Boolean = false
)

class NoorPreferences(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val LAST_PAGE = intPreferencesKey("last_page")
        val LAST_SURAH = intPreferencesKey("last_surah")
        val LAST_AYAH = intPreferencesKey("last_ayah")
        val RECITER_ID = stringPreferencesKey("reciter_id")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val AUTO_SCROLL_SPEED = floatPreferencesKey("auto_scroll_speed")
        val CITY_ID = stringPreferencesKey("city_id")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val CALC_METHOD = stringPreferencesKey("calc_method")
        val FAJR_OFFSET = intPreferencesKey("fajr_offset")
        val DHUHR_OFFSET = intPreferencesKey("dhuhr_offset")
        val ASR_OFFSET = intPreferencesKey("asr_offset")
        val MAGHRIB_OFFSET = intPreferencesKey("maghrib_offset")
        val ISHA_OFFSET = intPreferencesKey("isha_offset")
        val NOTIFY_FAJR = booleanPreferencesKey("notify_fajr")
        val NOTIFY_DHUHR = booleanPreferencesKey("notify_dhuhr")
        val NOTIFY_ASR = booleanPreferencesKey("notify_asr")
        val NOTIFY_MAGHRIB = booleanPreferencesKey("notify_maghrib")
        val NOTIFY_ISHA = booleanPreferencesKey("notify_isha")
        val ADHAN_VOICE_ID = stringPreferencesKey("adhan_voice_id")
        val ALLOW_ONLINE_STREAMING = booleanPreferencesKey("allow_online_streaming")
    }

    val settings: Flow<NoorUserSettings> = context.noorDataStore.data.map { prefs ->
        NoorUserSettings(
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            lastPage = (prefs[Keys.LAST_PAGE] ?: 1).coerceIn(1, 604),
            lastSurah = prefs[Keys.LAST_SURAH] ?: 1,
            lastAyah = prefs[Keys.LAST_AYAH] ?: 1,
            selectedReciterId = prefs[Keys.RECITER_ID] ?: "qatami",
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            autoScrollSpeed = prefs[Keys.AUTO_SCROLL_SPEED] ?: 1.0f,
            cityId = prefs[Keys.CITY_ID] ?: "damascus",
            latitude = prefs[Keys.LATITUDE] ?: 33.5138,
            longitude = prefs[Keys.LONGITUDE] ?: 36.2765,
            calculationMethod = CalculationMethod.fromName(prefs[Keys.CALC_METHOD]),
            fajrOffsetMin = prefs[Keys.FAJR_OFFSET] ?: 0,
            dhuhrOffsetMin = prefs[Keys.DHUHR_OFFSET] ?: 0,
            asrOffsetMin = prefs[Keys.ASR_OFFSET] ?: 0,
            maghribOffsetMin = prefs[Keys.MAGHRIB_OFFSET] ?: 0,
            ishaOffsetMin = prefs[Keys.ISHA_OFFSET] ?: 0,
            notifyFajr = prefs[Keys.NOTIFY_FAJR] ?: true,
            notifyDhuhr = prefs[Keys.NOTIFY_DHUHR] ?: true,
            notifyAsr = prefs[Keys.NOTIFY_ASR] ?: true,
            notifyMaghrib = prefs[Keys.NOTIFY_MAGHRIB] ?: true,
            notifyIsha = prefs[Keys.NOTIFY_ISHA] ?: true,
            selectedAdhanVoiceId = prefs[Keys.ADHAN_VOICE_ID] ?: "adhan_qatami",
            allowOnlineAudioStreaming = prefs[Keys.ALLOW_ONLINE_STREAMING] ?: false
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.noorDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun saveLastPosition(page: Int, surah: Int, ayah: Int) {
        context.noorDataStore.edit {
            it[Keys.LAST_PAGE] = page.coerceIn(1, 604)
            it[Keys.LAST_SURAH] = surah
            it[Keys.LAST_AYAH] = ayah
        }
    }

    suspend fun setSelectedReciter(reciterId: String) {
        context.noorDataStore.edit { it[Keys.RECITER_ID] = reciterId }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.noorDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setAutoScrollSpeed(speed: Float) {
        context.noorDataStore.edit { it[Keys.AUTO_SCROLL_SPEED] = speed.coerceIn(0.3f, 3.5f) }
    }

    suspend fun setLocation(cityId: String, lat: Double, lon: Double) {
        context.noorDataStore.edit {
            it[Keys.CITY_ID] = cityId
            it[Keys.LATITUDE] = lat
            it[Keys.LONGITUDE] = lon
        }
    }

    suspend fun setCalculationMethod(method: CalculationMethod) {
        context.noorDataStore.edit { it[Keys.CALC_METHOD] = method.name }
    }

    suspend fun setPrayerToggle(prayer: String, enabled: Boolean) {
        context.noorDataStore.edit { prefs ->
            when (prayer.uppercase()) {
                "FAJR" -> prefs[Keys.NOTIFY_FAJR] = enabled
                "DHUHR" -> prefs[Keys.NOTIFY_DHUHR] = enabled
                "ASR" -> prefs[Keys.NOTIFY_ASR] = enabled
                "MAGHRIB" -> prefs[Keys.NOTIFY_MAGHRIB] = enabled
                "ISHA" -> prefs[Keys.NOTIFY_ISHA] = enabled
            }
        }
    }

    suspend fun setPrayerOffsets(fajr: Int, dhuhr: Int, asr: Int, maghrib: Int, isha: Int) {
        context.noorDataStore.edit {
            it[Keys.FAJR_OFFSET] = fajr
            it[Keys.DHUHR_OFFSET] = dhuhr
            it[Keys.ASR_OFFSET] = asr
            it[Keys.MAGHRIB_OFFSET] = maghrib
            it[Keys.ISHA_OFFSET] = isha
        }
    }

    suspend fun setAdhanVoice(voiceId: String) {
        context.noorDataStore.edit { it[Keys.ADHAN_VOICE_ID] = voiceId }
    }

    suspend fun setAllowOnlineAudioStreaming(allowed: Boolean) {
        context.noorDataStore.edit { it[Keys.ALLOW_ONLINE_STREAMING] = allowed }
    }
}
