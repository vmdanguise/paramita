---
title: Servicio de reproducción en segundo plano (PracticePlaybackService)
version: 1.0
date_created: 2026-11-24
owner: Vicente
tags: [architecture, service, foreground, notificaciones]
---

# Introducción

`PracticePlaybackService` es un ForegroundService de tipo `mediaPlayback` que hospeda el `PracticeRunnerEngine` para que la práctica siga con la pantalla bloqueada o al cambiar de app. Es el **único dueño** del motor: la UI del teléfono y Android Auto le mandan órdenes por Intent y observan `sessionState`.

## 1. Propósito y alcance

Paquete `service`. Referencia en el chat: v1 en las líneas ~805-1224 y v2 (canónica) en ~7458-7700.

⚠️ El chat tiene **dos versiones incompatibles** de las constantes. Esta spec fija la versión canónica (§4.1), basada en la v2, y le suma `ACTION_SET_MODE` y `EXTRA_RECITATION_MODE` de la v1. `PracticeSessionCache` (v1) **se elimina**: el servicio resuelve la práctica por ID con `PracticeDao.getPracticeByIdOnce`.

## 2. Definiciones

- **FGS:** Foreground Service.
- **sessionState:** `StateFlow<RunnerSnapshot>` estático (companion) que replica el snapshot del motor.

## 3. Requisitos

- **REQ-001:** Manifest: `android:foregroundServiceType="mediaPlayback"`, `exported="false"`. Permisos `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` y `POST_NOTIFICATIONS`.
- **REQ-002:** En `ACTION_START_PRACTICE` se llama a `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` **antes de** cualquier trabajo asíncrono (Android exige que ocurra a los pocos segundos de `startForegroundService`).
- **REQ-003:** Luego se lee `getPracticeByIdOnce(practiceId)` en IO, se mapea con `toPracticeRunSpec()`, se aplican los overrides de los extras (`EXTRA_TARGET_REPS`, `EXTRA_PACE_WPM`, `EXTRA_PAUSE_BETWEEN_REPS_MS`, `EXTRA_BELL_EVERY_N`, `EXTRA_RECITATION_MODE`) y se llama a `engine.start(...)`. Si la práctica no existe: `stopSelf()` y log.
- **REQ-004:** `ACTION_PAUSE`, `ACTION_RESUME`, `ACTION_STOP` y `ACTION_SET_MODE` delegan en el motor. `ACTION_STOP` usa `stop(recordAsCompleted = true)` (sesión parcial), quita la notificación y llama a `stopSelf()`.
- **REQ-005:** Notificación:
  - Canal `mani_practice_playback_channel`, `IMPORTANCE_LOW`, sin sonido ni vibración, `setOnlyAlertOnce(true)`, `setOngoing(true)` mientras PLAYING o PAUSED.
  - Texto: título de la práctica + resumen ("Vuelta 7 de 21 · Voz clara", "Segmento 2 de 3 · Pausa 8 s").
  - Acciones: Pausar/Reanudar (alternan) y Detener. Al tocar se abre `MainActivity` (`SINGLE_TOP | CLEAR_TOP`).
  - Ícono `ic_lotus_calm` (vector).
  - Se actualiza como máximo 1 vez por segundo (throttling). Las cuentas regresivas no generan más de 1 notify/s.
- **REQ-006:** Al completar, la notificación cambia a "Práctica completa · Que el mérito beneficie a todos los seres", deja de ser ongoing y se llama a `stopForeground(STOP_FOREGROUND_DETACH)`.
- **REQ-007 (`persistSession`, hoy es un stub):** el callback `onSessionFinished` del motor:
  - inserta en `practice_sessions` (`id = UUID`, `practice_id`, `started_at = snapshot.sessionStartedAtEpochMs`, `ended_at = now`, `completed_repetitions = snapshot.mantraRepetition`, `recitation_mode` = nombre del modo o `GUIDED` si no hay mantra, `completed_full`, `merit_dedicated = 0`).
  - Publica el `sessionId` en `lastFinishedSessionId: StateFlow<String?>` para que la UI marque `merit_dedicated` con `markMeritDedicated(sessionId)` cuando el usuario dedica.
  - No registra sesiones de menos de 5 s ni sin ninguna locución (inicios accidentales).
- **REQ-008:** El servicio tiene su propio `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`, que se cancela en `onDestroy`, donde también se llama a `speech.release()`.
- **REQ-009:** `onBind` devuelve `null` (el estado se observa por `sessionState`). El `LocalBinder` de la v1 se descarta.
- **REQ-010:** Audio focus: se pide `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` al iniciar. Si se pierde el foco por una llamada, se pausa. *(No está en el chat; lo exige el comportamiento de una app de audio decente y Android Auto.)*
- **CON-001:** El servicio es la única instancia del motor. La UI **no** crea un `PracticeRunnerEngine` propio.
- **CON-002:** El servicio no se reinicia solo tras ser matado (`START_NOT_STICKY`). Reanudar sin contexto podría sonar de golpe.
- **SEC-001:** Los `PendingIntent` usan `FLAG_IMMUTABLE`.

## 4. Contratos

### 4.1 Constantes canónicas

```kotlin
companion object {
    const val CHANNEL_ID = "mani_practice_playback_channel"   // v2 del chat
    const val NOTIFICATION_ID = 4101

    const val ACTION_START_PRACTICE = "ar.param.advisor.action.START_PRACTICE"
    const val ACTION_PAUSE          = "ar.param.advisor.action.PAUSE"
    const val ACTION_RESUME         = "ar.param.advisor.action.RESUME"
    const val ACTION_STOP           = "ar.param.advisor.action.STOP"
    const val ACTION_SET_MODE       = "ar.param.advisor.action.SET_MODE"

    const val EXTRA_PRACTICE_ID           = "ar.param.advisor.extra.PRACTICE_ID"
    const val EXTRA_TARGET_REPS           = "ar.param.advisor.extra.TARGET_REPS"
    const val EXTRA_PACE_WPM              = "ar.param.advisor.extra.PACE_WPM"
    const val EXTRA_PAUSE_BETWEEN_REPS_MS = "ar.param.advisor.extra.PAUSE_BETWEEN_REPS_MS"  // Long
    const val EXTRA_BELL_EVERY_N          = "ar.param.advisor.extra.BELL_EVERY_N"
    const val EXTRA_RECITATION_MODE       = "ar.param.advisor.extra.RECITATION_MODE"        // RecitationMode.name

    val sessionState: StateFlow<RunnerSnapshot>
    val lastFinishedSessionId: StateFlow<String?>

    fun startPractice(context: Context, practiceId: String, overrides: PlaybackOverrides = PlaybackOverrides())
    fun pause(context: Context); fun resume(context: Context); fun stop(context: Context)
    fun setMode(context: Context, mode: RecitationMode)
}

data class PlaybackOverrides(
    val targetReps: Int? = null, val paceWpm: Int? = null,
    val pauseBetweenRepsMs: Long? = null, val bellEveryN: Int? = null,
    val mode: RecitationMode = RecitationMode.SPOKEN
)

@Composable fun rememberPracticeServiceState(): State<RunnerSnapshot>
```

Los extras ausentes o con valores ≤ 0 (salvo `BELL_EVERY_N`, donde 0 significa "sin campana") se ignoran y se usa el valor del catálogo.

`START_PRACTICE` se envía con `ContextCompat.startForegroundService`. Las demás acciones con `startService` (el servicio ya está en primer plano). Si no hay sesión activa, se ignoran.

## 5. Criterios de aceptación

- **AC-001:** Dado un mantra iniciado, cuando se bloquea la pantalla durante 2 minutos, entonces el conteo sigue avanzando y la notificación lo refleja.
- **AC-002:** Dada una sesión activa, cuando se toca "Pausar" en la notificación, entonces el TTS se corta en menos de 500 ms y el botón pasa a "Reanudar".
- **AC-003:** Dada una práctica completada, entonces existe 1 fila en `practice_sessions` con `completed_full = 1`.
- **AC-004:** Dado un "Detener" en la vuelta 5 de 21, entonces existe 1 fila con `completed_full = 0` y `completed_repetitions = 5`.
- **AC-005:** Las actualizaciones de la notificación no producen sonido ni vibración (comprobación manual).
- **AC-006:** Dado Android 14+, el servicio arranca sin `MissingForegroundServiceTypeException`.
- **AC-007:** Dado `EXTRA_TARGET_REPS = 7` sobre un mantra de 21 en el catálogo, entonces `mantraTargetRepetitions == 7`.

## 6. Estrategia de pruebas

- Robolectric o instrumentado con `ServiceTestRule` para el ruteo de acciones y extras.
- Tests del callback de persistencia con una DB en memoria.
- Pruebas manuales en Android 8, 13 y 14 (FGS types, permiso de notificaciones).

## 7. Justificación

Un único dueño del motor evita estados divergentes entre el teléfono, la notificación y el auto. Resolver por ID desde Room elimina la caché en memoria de la v1, que se perdía si el proceso moría.

## 8. Dependencias

- **PLT-001:** `androidx.core` (`ServiceCompat`, `NotificationCompat`).
- **PLT-002:** Room (`PracticeDao`, `PracticeSessionDao`) a través de `ManiApplication`.

## 9. Casos borde

- Permiso de notificaciones denegado (API 33+): el FGS funciona igual, pero la notificación no se ve. La UI muestra los controles en pantalla.
- Dos `START_PRACTICE` seguidos: el segundo reemplaza al primero (REQ-020 del motor).
- El proceso muere durante una sesión: no se registra nada. Aceptado.

## 10. Validación

AC-001 a AC-007.

## 11. Relacionadas

`spec-architecture-motor-practica-tts.md`, `spec-architecture-android-auto.md`, `spec-design-ui-navegacion.md`.
