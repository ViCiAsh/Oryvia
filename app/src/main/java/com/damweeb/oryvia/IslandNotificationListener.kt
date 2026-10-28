package com.damweeb.oryvia

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

/**
 * Bound by the system once the user grants notification access.
 * It does two jobs: forwards new notifications, and tracks the active media session.
 */
class IslandNotificationListener : NotificationListenerService() {

    private lateinit var sessionManager: MediaSessionManager
    private val listenerComponent by lazy { ComponentName(this, IslandNotificationListener::class.java) }

    private var current: MediaController? = null
    private var lastMediaKey: String? = null
    private val seen = HashMap<String, Int>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia(announce = true)
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia(announce = true)
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers -> attach(controllers) }

    // ---------------------------------------------------------------- lifecycle

    override fun onListenerConnected() {
        sessionManager = getSystemService(MediaSessionManager::class.java)
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
        } catch (_: SecurityException) {
        }
        refreshSessions()
    }

    override fun onListenerDisconnected() {
        if (::sessionManager.isInitialized) {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        }
        current?.unregisterCallback(controllerCallback)
        current = null
        IslandBus.controller = null
        IslandBus.media.value = null
    }

    // ---------------------------------------------------------------- notifications

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName) return

        // Skip things that are not "new message" style alerts.
        if ((n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT)) != 0) return
        val category = n.category
        if (category != null && category in IGNORED_CATEGORIES) return
        val extras = n.extras
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return
        if (isSilent(sbn)) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // Apps re-post the same notification for updates, only show real changes.
        val hash = (title + "\u0000" + text).hashCode()
        if (seen[sbn.key] == hash) return
        seen[sbn.key] = hash

        val appName = appLabel(sbn.packageName)
        val (icon, tint) = loadIcon(n)

        IslandBus.post(
            IslandContent.NotificationItem(
                key = sbn.key,
                appName = appName,
                title = title.ifBlank { appName },
                text = text,
                icon = icon,
                tintIcon = tint,
                intent = n.contentIntent,
                packageName = sbn.packageName,
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
    }

    private fun isSilent(sbn: StatusBarNotification): Boolean = try {
        val ranking = NotificationListenerService.Ranking()
        if (currentRanking.getRanking(sbn.key, ranking)) {
            ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    /** Large icon if the app gave one, otherwise the small status icon (which we tint). */
    private fun loadIcon(n: Notification): Pair<Bitmap?, Boolean> {
        try {
            n.getLargeIcon()?.loadDrawable(this)?.let { return it.toBitmap(96, 96) to false }
            n.smallIcon?.loadDrawable(this)?.let { return it.toBitmap(96, 96) to true }
        } catch (_: Exception) {
        }
        return null to false
    }

    // ---------------------------------------------------------------- media

    private fun refreshSessions() {
        val sessions = try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) {
            emptyList()
        }
        attach(sessions)
    }

    private fun attach(controllers: List<MediaController>?) {
        val best = controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers?.firstOrNull()
        if (best?.sessionToken == current?.sessionToken) return

        current?.unregisterCallback(controllerCallback)
        current = best
        IslandBus.controller = best
        best?.registerCallback(controllerCallback)
        publishMedia(announce = false)
    }

    private fun publishMedia(announce: Boolean) {
        val controller = current
        val md = controller?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (controller == null || md == null || title.isNullOrBlank()) {
            IslandBus.media.value = null
            return
        }
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val info = IslandContent.Media(title, artist, art?.let { scaleDown(it) }, playing, controller.packageName)
        IslandBus.media.value = info

        // Playback state callbacks fire constantly (position updates), only announce real changes.
        val key = "$title|$artist|$playing"
        val changed = key != lastMediaKey
        lastMediaKey = key
        if (announce && changed) IslandBus.post(info)
    }

    private fun scaleDown(src: Bitmap, max: Int = 160): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= max) return src
        val ratio = max.toFloat() / longest
        return runCatching {
            Bitmap.createScaledBitmap(
                src,
                (src.width * ratio).toInt().coerceAtLeast(1),
                (src.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        }.getOrDefault(src)
    }

    private companion object {
        val IGNORED_CATEGORIES = setOf(
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_STATUS,
        )
    }
}
