package com.damweeb.oryvia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hosts the island. A Service has no lifecycle that Compose understands, so this class
 * implements LifecycleOwner / SavedStateRegistryOwner / ViewModelStoreOwner itself and
 * attaches them to the ComposeView that is added straight to the WindowManager.
 */
class IslandService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings = MutableStateFlow(IslandSettings())
    private val ui = MutableStateFlow(UiState())
    private val overlayParams by lazy {
        createLayoutParams().also { applyPosition(it, settings.value) }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var repository: SettingsRepository
    private var overlay: ComposeView? = null
    private var hideJob: Job? = null

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        startAsForeground()

        if (!Perms.overlay(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WindowManager::class.java)
        repository = SettingsRepository(this)
        ui.value = UiState(
            landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        )

        addOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        registerBatteryReceiver()
        observe()
        rebindListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ui.update {
            it.copy(landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(batteryReceiver) }
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- foreground notification

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Island service", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Dynamic Island is running")
            .setContentText("Tap to open settings")
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------------------------------------------------------------- overlay window

    private fun addOverlay() {
        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setContent {
            val s by settings.collectAsState()
            val u by ui.collectAsState()
            IslandRoot(
                ui = u,
                s = s,
                onIdleTap = { onIdleTap() },
                onIdleLongPress = { openApp() },
                onExpandedTap = { onExpandedTap() },
                onExpandedLongPress = { collapse() },
                onMediaAction = { onMediaAction(it) },
                onDismiss = { collapse() },
            )
        }
        overlay = view
        windowManager.addView(view, overlayParams)
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        lp.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.title = "DynamicIsland"
        return lp
    }

    /**
     * The window is WRAP_CONTENT, so it is exactly as big as the island and never blocks touches
     * around it. With a centred gravity the window grows on both sides (notch / pill). With a
     * left gravity pinned to the camera it grows left to right (punch hole).
     */
    private fun applyPosition(lp: WindowManager.LayoutParams, s: IslandSettings) {
        val d = resources.displayMetrics.density
        val slop = (TOUCH_SLOP_DP * d).roundToInt()
        when (s.mode) {
            IslandMode.PUNCH_HOLE -> {
                lp.gravity = Gravity.TOP or Gravity.LEFT
                lp.x = (s.punchXDp * d).roundToInt() - slop
                lp.y = (s.punchYDp * d).roundToInt() - slop
            }
            IslandMode.NOTCH -> {
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.x = (s.offsetXDp * d).roundToInt()
                lp.y = -slop
            }
            IslandMode.PILL -> {
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.x = (s.offsetXDp * d).roundToInt()
                lp.y = (s.offsetYDp * d).roundToInt() - slop
            }
        }
    }

    // ---------------------------------------------------------------- state + events

    private fun observe() {
        scope.launch {
            repository.flow.collect { s ->
                settings.value = s
                val view = overlay ?: return@collect
                applyPosition(overlayParams, s)
                runCatching { windowManager.updateViewLayout(view, overlayParams) }
            }
        }
        scope.launch {
            IslandBus.events.collect { content ->
                val s = settings.value
                val allowed = when (content) {
                    is IslandContent.NotificationItem -> s.showNotifications
                    is IslandContent.Media -> s.showMedia
                    is IslandContent.Charging -> s.showCharging
                }
                if (allowed) show(content)
            }
        }
        // Keep an expanded media card in sync (play/pause icon, late album art).
        scope.launch {
            IslandBus.media.collect { media ->
                if (media != null && ui.value.content is IslandContent.Media) {
                    ui.update { it.copy(content = media) }
                }
            }
        }
    }

    private fun show(content: IslandContent) {
        ui.update { it.copy(expanded = true, content = content) }
        scheduleHide()
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay((settings.value.hideDelaySec * 1000).toLong())
            ui.update { it.copy(expanded = false) }
        }
    }

    private fun collapse() {
        hideJob?.cancel()
        ui.update { it.copy(expanded = false) }
    }

    // ---------------------------------------------------------------- gestures

    private fun onIdleTap() {
        val content: IslandContent = when (val last = ui.value.content) {
            is IslandContent.Media -> IslandBus.media.value ?: last
            null -> IslandBus.media.value ?: batteryNow()
            else -> last
        }
        show(content)
    }

    private fun onExpandedTap() {
        when (val c = ui.value.content) {
            is IslandContent.NotificationItem -> openSource(c.intent, c.packageName)
            // sessionActivity is the player screen the music app registered
            is IslandContent.Media ->
                openSource(IslandBus.controller?.sessionActivity, c.packageName)
            else -> Unit // charging has nothing to open, it just closes
        }
        collapse()
    }

    /** Tries the app's own intent first, then falls back to launching the app by package name. */
    private fun openSource(intent: PendingIntent?, packageName: String?) {
        if (intent != null && runCatching { intent.send() }.isSuccess) return
        if (packageName.isNullOrBlank()) return
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun onMediaAction(action: MediaAction) {
        val controller = IslandBus.controller ?: return
        val controls = controller.transportControls
        when (action) {
            MediaAction.PREVIOUS -> controls.skipToPrevious()
            MediaAction.NEXT -> controls.skipToNext()
            MediaAction.TOGGLE ->
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controls.pause()
                } else {
                    controls.play()
                }
        }
        scheduleHide() // keep the island open while the user is interacting
    }

    // ---------------------------------------------------------------- charging

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = batteryNow()
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED ->
                    IslandBus.post(now.copy(charging = true, label = "Charging"))
                Intent.ACTION_POWER_DISCONNECTED ->
                    IslandBus.post(now.copy(charging = false, label = "Unplugged"))
            }
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun batteryNow(): IslandContent.Charging {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
        return IslandContent.Charging(percent, plugged, if (plugged) "Charging" else "Battery")
    }

    /** realme UI likes to kill listeners, ask the system to bind ours again. */
    private fun rebindListener() {
        if (Perms.notificationAccess(this)) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(this, IslandNotificationListener::class.java)
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "island_service"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, IslandService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IslandService::class.java))
        }
    }
}
