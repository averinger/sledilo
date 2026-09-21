package com.violetbit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsageViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = SettingsStorage(app)

    private val _apps = MutableStateFlow<List<AppUsage>>(emptyList())
    val apps: StateFlow<List<AppUsage>> = _apps

    private val _hasPerm = MutableStateFlow(false)
    val hasPerm: StateFlow<Boolean> = _hasPerm

    private val _settings = MutableStateFlow(storage.load())
    val settings: StateFlow<AppSettings> = _settings

    init {
        viewModelScope.launch {
            while (true) { refresh(); delay(30_000) }
        }
    }

    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            _hasPerm.value = UsageTracker.hasPermission(ctx)
            if (_hasPerm.value) _apps.value = UsageTracker.getTodayUsage(ctx)
        }
    }

    suspend fun getHourly(pkg: String): LongArray = withContext(Dispatchers.IO) {
        UsageTracker.getHourlyUsage(getApplication(), pkg)
    }

    fun setThreshold(hours: Int) {
        val s = _settings.value.copy(thresholdHours = hours)
        _settings.value = s; storage.save(s)
    }

    fun setUseCustomPhrases(use: Boolean) {
        val s = _settings.value.copy(useCustomPhrases = use)
        _settings.value = s; storage.save(s)
    }

    fun setNoProfanity(no: Boolean) {
        val s = _settings.value.copy(noProfanity = no)
        _settings.value = s; storage.save(s)
    }

    fun addPhrase(phrase: String) {
        if (phrase.isBlank()) return
        val s = _settings.value.copy(customPhrases = _settings.value.customPhrases + phrase.trim())
        _settings.value = s; storage.save(s)
    }

    fun removePhrase(phrase: String) {
        val s = _settings.value.copy(customPhrases = _settings.value.customPhrases.filterNot { it == phrase })
        _settings.value = s; storage.save(s)
    }
}
