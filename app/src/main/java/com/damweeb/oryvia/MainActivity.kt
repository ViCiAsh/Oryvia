package com.damweeb.oryvia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository(applicationContext)
        setContent {
            IslandTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) { App(repo) }
                }
            }
        }
    }
}

@Composable
private fun App(repo: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var onboarding by remember { mutableStateOf(!Perms.requiredGranted(context)) }

    // null until DataStore has loaded the saved settings
    val loaded by repo.flow.collectAsState(initial = null)
    val settings = loaded ?: return

    // Start or stop the overlay service whenever the switch or the onboarding state changes.
    LaunchedEffect(settings.enabled, onboarding) {
        if (onboarding) return@LaunchedEffect
        if (settings.enabled && Perms.overlay(context)) {
            IslandService.start(context)
        } else {
            IslandService.stop(context)
        }
    }

    if (onboarding) {
        OnboardingScreen(onDone = { onboarding = false })
    } else {
        SettingsScreen(
            s = settings,
            update = { change -> scope.launch { repo.update { it.change() } } },
            onOpenSetup = { onboarding = true },
        )
    }
}
