package com.damweeb.oryvia

import android.app.PendingIntent
import android.graphics.Bitmap
import android.media.session.MediaController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow


enum class IslandMode(val label: String) {
    NOTCH("Notch"),
    PILL("Pill"),
    PUNCH_HOLE("Punch hole"),
}

enum class MediaAction { PREVIOUS, TOGGLE, NEXT }

/** Everything the island can show while expanded. */
sealed interface IslandContent {
    data class NotificationItem(
        val key: String,
        val appName: String,
        val title: String,
        val text: String,
        val icon: Bitmap?,
        val tintIcon: Boolean,
        val intent: PendingIntent?,
        val packageName: String? = null,
    ) : IslandContent

    data class Media(
        val title: String,
        val artist: String,
        val art: Bitmap?,
        val playing: Boolean,
        val packageName: String? = null,
    ) : IslandContent

    data class Charging(
        val percent: Int,
        val charging: Boolean,
        val label: String,
    ) : IslandContent
}

data class UiState(
    val expanded: Boolean = false,
    val content: IslandContent? = null,
    val landscape: Boolean = false,
)

/**
 * Tiny in-process bus. The notification listener and the battery receiver push events here,
 * and IslandService collects them. If the service is not running, events are simply dropped.
 */
object IslandBus {
    private val _events = MutableSharedFlow<IslandContent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<IslandContent> = _events.asSharedFlow()

    /** Latest media info, kept so tapping the idle shape can bring it back. */
    val media = MutableStateFlow<IslandContent.Media?>(null)

    /** Active media controller, used by the play / pause / skip buttons. */
    @Volatile
    var controller: MediaController? = null

    fun post(content: IslandContent) {
        _events.tryEmit(content)
    }
}

