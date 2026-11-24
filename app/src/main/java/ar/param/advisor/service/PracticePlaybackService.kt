package ar.param.advisor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ar.param.advisor.MainActivity
import ar.param.advisor.ManiApplication
import ar.param.advisor.R
import ar.param.advisor.data.local.entity.PracticeSessionEntity
import ar.param.advisor.engine.PlaybackOverrides
import ar.param.advisor.engine.PracticeRunSpec
import ar.param.advisor.engine.PracticeRunnerEngine
import ar.param.advisor.engine.RecitationMode
import ar.param.advisor.engine.RunnerSnapshot
import ar.param.advisor.engine.RunnerStatus
import ar.param.advisor.engine.SpeechAndBellController
import ar.param.advisor.engine.toPracticeRunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Único dueño del motor de práctica (spec-architecture-servicio-reproduccion).
 * La UI le manda órdenes por Intent y observa [sessionState].
 */
class PracticePlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var speech: SpeechAndBellController
    private lateinit var engine: PracticeRunnerEngine
    private var loadJob: Job? = null

    private var lastNotifyAtMs = 0L
    private var lastNotifiedStatus: RunnerStatus? = null
    private var focusRequest: AudioFocusRequest? = null
    private val stopSelfRunnable = Runnable { stopSelf() }

    private val app: ManiApplication get() = application as ManiApplication

    override fun onCreate() {
        super.onCreate()
        createChannel()
        speech = SpeechAndBellController(this)
        engine = PracticeRunnerEngine(
            scope = scope,
            speech = speech,
            onSessionFinished = { spec, snapshot, completedFull -> persistSession(spec, snapshot, completedFull) },
        )
        scope.launch { engine.snapshot.collect(::onSnapshot) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PRACTICE -> handleStart(intent)
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> {
                requestAudioFocus()
                engine.resume()
            }
            ACTION_STOP -> handleStop()
            ACTION_SET_MODE -> intent.getStringExtra(EXTRA_RECITATION_MODE)
                ?.let { name -> RecitationMode.entries.firstOrNull { it.name == name } }
                ?.let(engine::setRecitationMode)
            else -> if (!engine.snapshot.value.isActive) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        handler.removeCallbacks(stopSelfRunnable)
        val practiceId = intent.getStringExtra(EXTRA_PRACTICE_ID)
        // Primero el foreground (REQ-002), antes de cualquier trabajo asíncrono.
        goForeground(buildNotification(RunnerSnapshot(status = RunnerStatus.PLAYING), title = getString(R.string.notif_preparing)))
        if (practiceId.isNullOrBlank()) {
            stopSelf()
            return
        }
        _lastFinishedSessionId.value = null
        val overrides = PlaybackOverrides(
            targetReps = intent.getIntExtra(EXTRA_TARGET_REPS, -1).takeIf { it > 0 },
            paceWpm = intent.getIntExtra(EXTRA_PACE_WPM, -1).takeIf { it > 0 },
            pauseBetweenRepsMs = intent.getLongExtra(EXTRA_PAUSE_BETWEEN_REPS_MS, -1L).takeIf { it > 0 },
            bellEveryN = intent.getIntExtra(EXTRA_BELL_EVERY_N, -1).takeIf { it >= 0 },
            mode = intent.getStringExtra(EXTRA_RECITATION_MODE)
                ?.let { name -> RecitationMode.entries.firstOrNull { it.name == name } }
                ?: RecitationMode.SPOKEN,
        )
        loadJob?.cancel()
        loadJob = scope.launch {
            val details = withContext(Dispatchers.IO) { app.practiceDao.getPracticeByIdOnce(practiceId) }
            if (details == null || details.segments.isEmpty()) {
                Log.w(TAG, "La práctica $practiceId no existe; se detiene el servicio")
                stopForegroundCompat(remove = true)
                stopSelf()
                return@launch
            }
            requestAudioFocus()
            engine.start(overrides.applyTo(details.toPracticeRunSpec()), overrides.mode)
        }
    }

    private fun handleStop() {
        loadJob?.cancel()
        engine.stop(recordAsCompleted = true)
        abandonAudioFocus()
        stopForegroundCompat(remove = true)
        stopSelf()
    }

    private fun onSnapshot(snapshot: RunnerSnapshot) {
        _sessionState.value = snapshot
        when (snapshot.status) {
            RunnerStatus.COMPLETED -> {
                abandonAudioFocus()
                notifyNow(buildCompletedNotification())
                stopForegroundCompat(remove = false)
                // Se espera a que la campana de cierre termine de sonar.
                handler.postDelayed(stopSelfRunnable, 4_000L)
            }
            RunnerStatus.PLAYING, RunnerStatus.PAUSED -> {
                val now = SystemClock.elapsedRealtime()
                if (snapshot.status != lastNotifiedStatus || now - lastNotifyAtMs >= 1_000L) {
                    notifyNow(buildNotification(snapshot))
                }
            }
            RunnerStatus.IDLE -> Unit
        }
        lastNotifiedStatus = snapshot.status
    }

    private fun notifyNow(notification: Notification) {
        lastNotifyAtMs = SystemClock.elapsedRealtime()
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Sin permiso de notificaciones", e)
            }
        }
    }

    private fun persistSession(spec: PracticeRunSpec, snapshot: RunnerSnapshot, completedFull: Boolean) {
        val endedAt = System.currentTimeMillis()
        if (snapshot.sessionStartedAtEpochMs <= 0L || endedAt - snapshot.sessionStartedAtEpochMs < MIN_SESSION_MS) return
        val session = PracticeSessionEntity(
            id = UUID.randomUUID().toString(),
            practiceId = spec.practiceId,
            startedAtEpochMs = snapshot.sessionStartedAtEpochMs,
            endedAtEpochMs = endedAt,
            completedRepetitions = snapshot.mantraRepetition,
            recitationMode = if (spec.mantraConfig != null) snapshot.recitationMode.name else "GUIDED",
            completedFull = completedFull,
            meritDedicated = false,
            note = null,
        )
        // En el scope de la aplicación: tiene que sobrevivir al servicio si se detiene enseguida.
        app.applicationScope.launch {
            runCatching { app.practiceSessionDao.insertSession(session) }
                .onSuccess { _lastFinishedSessionId.value = session.id }
                .onFailure { Log.w(TAG, "No se pudo guardar la sesión", it) }
        }
    }

    private fun goForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    private fun stopForegroundCompat(remove: Boolean) {
        ServiceCompat.stopForeground(
            this,
            if (remove) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notif_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ACTIVE_PRACTICE, true)
        }
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PracticePlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun summary(s: RunnerSnapshot): String {
        val mode = getString(modeLabelRes(s.recitationMode))
        val base = if (s.mantraTargetRepetitions > 0 && s.stage == ar.param.advisor.engine.RunnerStage.MANTRA_LOOP) {
            getString(R.string.notif_round_of, s.mantraRepetition, s.mantraTargetRepetitions, mode)
        } else {
            getString(R.string.notif_segment_of, s.segmentIndex + 1, s.totalSegments.coerceAtLeast(1))
        }
        return when {
            s.status == RunnerStatus.PAUSED -> getString(R.string.notif_paused, base)
            s.pauseRemainingSec > 0 -> getString(R.string.notif_with_pause, base, s.pauseRemainingSec)
            else -> base
        }
    }

    private fun buildNotification(s: RunnerSnapshot, title: String = s.practiceTitle): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lotus_calm)
            .setContentTitle(title)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.practiceId != null) {
            builder.setContentText(summary(s))
            if (s.status == RunnerStatus.PAUSED) {
                builder.addAction(R.drawable.ic_play_calm, getString(R.string.action_resume), actionIntent(ACTION_RESUME, 2))
            } else {
                builder.addAction(R.drawable.ic_pause_calm, getString(R.string.action_pause), actionIntent(ACTION_PAUSE, 1))
            }
            builder.addAction(R.drawable.ic_stop_calm, getString(R.string.action_stop), actionIntent(ACTION_STOP, 3))
        }
        return builder.build()
    }

    private fun buildCompletedNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lotus_calm)
            .setContentTitle(getString(R.string.notif_completed_title))
            .setContentText(getString(R.string.notif_completed_text))
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    private fun requestAudioFocus() {
        val manager = getSystemService(AudioManager::class.java) ?: return
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    engine.pause()
                }
            }, handler)
            .build()
            .also { focusRequest = it }
        manager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val request = focusRequest ?: return
        getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (engine.snapshot.value.isActive) engine.stop(recordAsCompleted = true)
        abandonAudioFocus()
        speech.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PracticePlayback"
        private const val MIN_SESSION_MS = 5_000L

        const val CHANNEL_ID = "mani_practice_playback_channel"
        const val NOTIFICATION_ID = 4101

        const val ACTION_START_PRACTICE = "ar.param.advisor.action.START_PRACTICE"
        const val ACTION_PAUSE = "ar.param.advisor.action.PAUSE"
        const val ACTION_RESUME = "ar.param.advisor.action.RESUME"
        const val ACTION_STOP = "ar.param.advisor.action.STOP"
        const val ACTION_SET_MODE = "ar.param.advisor.action.SET_MODE"

        const val EXTRA_PRACTICE_ID = "ar.param.advisor.extra.PRACTICE_ID"
        const val EXTRA_TARGET_REPS = "ar.param.advisor.extra.TARGET_REPS"
        const val EXTRA_PACE_WPM = "ar.param.advisor.extra.PACE_WPM"
        const val EXTRA_PAUSE_BETWEEN_REPS_MS = "ar.param.advisor.extra.PAUSE_BETWEEN_REPS_MS"
        const val EXTRA_BELL_EVERY_N = "ar.param.advisor.extra.BELL_EVERY_N"
        const val EXTRA_RECITATION_MODE = "ar.param.advisor.extra.RECITATION_MODE"

        private val _sessionState = MutableStateFlow(RunnerSnapshot())
        val sessionState: StateFlow<RunnerSnapshot> = _sessionState.asStateFlow()

        private val _lastFinishedSessionId = MutableStateFlow<String?>(null)
        val lastFinishedSessionId: StateFlow<String?> = _lastFinishedSessionId.asStateFlow()

        fun startPractice(context: Context, practiceId: String, overrides: PlaybackOverrides = PlaybackOverrides()) {
            val intent = Intent(context, PracticePlaybackService::class.java).apply {
                action = ACTION_START_PRACTICE
                putExtra(EXTRA_PRACTICE_ID, practiceId)
                overrides.targetReps?.let { putExtra(EXTRA_TARGET_REPS, it) }
                overrides.paceWpm?.let { putExtra(EXTRA_PACE_WPM, it) }
                overrides.pauseBetweenRepsMs?.let { putExtra(EXTRA_PAUSE_BETWEEN_REPS_MS, it) }
                overrides.bellEveryN?.let { putExtra(EXTRA_BELL_EVERY_N, it) }
                putExtra(EXTRA_RECITATION_MODE, overrides.mode.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        fun setMode(context: Context, mode: RecitationMode) {
            if (!sessionState.value.isActive) return
            context.startService(
                Intent(context, PracticePlaybackService::class.java)
                    .setAction(ACTION_SET_MODE)
                    .putExtra(EXTRA_RECITATION_MODE, mode.name),
            )
        }

        private fun send(context: Context, action: String) {
            if (!sessionState.value.isActive) return
            runCatching { context.startService(Intent(context, PracticePlaybackService::class.java).setAction(action)) }
                .onFailure { Log.w(TAG, "No se pudo enviar $action", it) }
        }

        fun modeLabelRes(mode: RecitationMode): Int = when (mode) {
            RecitationMode.SPOKEN -> R.string.mode_spoken
            RecitationMode.WHISPER -> R.string.mode_whisper
            RecitationMode.MENTAL -> R.string.mode_mental
        }
    }
}

@Composable
fun rememberPracticeServiceState(): State<RunnerSnapshot> = PracticePlaybackService.sessionState.collectAsState()
