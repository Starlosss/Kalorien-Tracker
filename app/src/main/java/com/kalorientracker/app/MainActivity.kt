package com.kalorientracker.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.settings.AppSettings
import com.kalorientracker.app.data.settings.SettingsRepository
import com.kalorientracker.app.ui.navigation.AppNavHost
import com.kalorientracker.app.ui.theme.KalorienTheme
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.ViewHaptics
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * Pins the whole activity to German. Every string in this app is German and every number and
     * date it formats itself already asks for [Locale.GERMANY] explicitly (see `Format`,
     * `ChartLabels`). Framework components that carry their own text do not: on a phone set to
     * English, the Material date range picker in the statistics screen came up with English
     * weekday initials, an English date format and a week starting on Sunday, inside an otherwise
     * German dialog. Setting the locale on the base context covers those too.
     */
    // Lint flags this for app bundles with language splits: pinning a locale there can leave the
    // strings for it undownloaded. This app ships as a single sideloaded APK with every string
    // compiled in, so the pin is safe. It stops being safe the day the app is published as an AAB.
    @Suppress("AppBundleLocaleChanges")
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocales(LocaleList(Locale.GERMANY))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {
    /** Null until the stored settings are read, so the first frame never shows the wrong start screen. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
private fun AppRoot(viewModel: MainViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val view = LocalView.current
    val current = settings
    val haptics = remember(view, current?.hapticsEnabled) { ViewHaptics(view, current?.hapticsEnabled ?: true) }
    KalorienTheme(animationsEnabled = current?.animationsEnabled ?: true, haptics = haptics) {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            if (current != null) {
                AppNavHost(onboardingCompleted = current.onboardingCompleted)
            }
        }
    }
}
