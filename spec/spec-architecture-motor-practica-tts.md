---
title: Motor de práctica (PracticeRunnerEngine), TTS y campana
version: 1.1
date_created: 2026-11-24
last_updated: 2026-11-24 (v1.1: grabaciones propias, velocidad en vivo y ajustes de voz)
owner: Vicente
tags: [architecture, engine, tts, audio]
---

# Introducción

El motor es lo que hace que la app "se prenda y recite sola": recorre los segmentos, repite mantras, habla con TTS, respeta pausas, toca la campana y expone su estado como `StateFlow<RunnerSnapshot>`. No depende de la UI.

## 1. Propósito y alcance

Paquete `engine`. Referencia en el chat: modelos y motor en las líneas ~424-803, campana con SoundPool en ~6546-6750.

## 2. Definiciones

- **Spec de ejecución (`PracticeRunSpec`):** representación inmutable de una práctica lista para correr.
- **Snapshot:** estado observable del motor en un instante.
- **Vuelta:** una repetición del mantra.
- **Hito de ciclo:** la vuelta en la que `rep % bellEveryN == 0`.

## 3. Requisitos

### Modelos
- **REQ-001:** Los modelos son exactamente los de §4.1 (nombres de campo incluidos), porque los consumen el servicio, la UI y Android Auto.

### Secuencia de ejecución
- **REQ-010:** Prácticas sin `mantraConfig`: para cada segmento en orden, para cada `segRep` de 1 a `repeatCount`, se habla `text` y luego hay una cuenta regresiva de `pauseAfterSec` actualizando `pauseRemainingSec` cada segundo.
- **REQ-011 (aclara el chat):** Prácticas con `mantraConfig`:
  1. Se ejecutan normalmente los segmentos cuya fase **no** es `CORE` y que están antes del segmento mantra (por ejemplo, la motivación).
  2. El **segmento mantra** es el último segmento con fase `CORE` (si no hay ninguno, el último segmento). **No** se ejecuta en el bucle de segmentos: se recita solo en el bucle de vueltas, de 1 a `targetRepetitions`. Así se corrige el borrador, que recitaba el mantra una vez de más.
  3. Después del bucle se ejecutan los segmentos posteriores (por ejemplo, la dedicación).
- **REQ-012:** En el bucle de mantra se habla `transliteration ?: text`. Después de cada vuelta se espera `pauseBetweenRepsMs`. Si `bellEveryN > 0 && rep % bellEveryN == 0 && rep < target`, se toca la campana de hito.
- **REQ-013:** En modo `MENTAL` no se habla. Se espera `mentalRepDurationMs` (por defecto 2600 ms) + `pauseBetweenRepsMs`. La campana sigue sonando.
- **REQ-014:** Al terminar, se toca la campana de cierre, el estado pasa a `COMPLETED` y se llama **una sola vez** a `onSessionFinished(spec, completedReps, completedFull = true)`.

### Controles
- **REQ-020:** `start(spec, mode)` detiene cualquier ejecución previa **sin** registrarla como completa y arranca desde cero.
- **REQ-021:** `pause()` solo actúa si el estado es `PLAYING`: cancela el job, corta el TTS y pasa a `PAUSED` conservando `segmentIndex`, `segmentRepetition` y `mantraRepetition`.
- **REQ-022:** `resume()` solo actúa si el estado es `PAUSED`. Continúa en la **misma fase** en que se pausó: si se pausó en el bucle de mantra, retoma en la vuelta `mantraRepetition + 1` sin repetir segmentos. Para eso el snapshot incluye `stage` (§4.1).
- **REQ-023:** `stop(recordAsCompleted)` cancela y vuelve a `IDLE`. Si `recordAsCompleted` es true y la sesión **no** fue reportada todavía, llama a `onSessionFinished(..., completedFull = false)`. Nunca se reporta dos veces la misma sesión (flag `sessionReported`).
- **REQ-024:** `setRecitationMode(mode)` se aplica desde la **próxima** locución. Si es `MENTAL`, corta la locución en curso.

### Voz (SpeechAndBellController)
- **REQ-030:** El motor depende de la interfaz `SpeechPort` (§4.2), no de la clase concreta, para poder testearse.
- **REQ-031:** Idioma TTS: `es-AR` si está disponible, si no `es-ES`, si no `Locale.getDefault()`.
- **REQ-032:** `speechRate = (wpm / 120f).coerceIn(0.72f, 1.25f)`.
- **REQ-033:** `speakAndAwait` suspende hasta `onDone` u `onError` del `UtteranceProgressListener`. Es cancelable: cancelar corta el TTS. Si el TTS no está listo, espera la inicialización con un timeout de 5 s y, si no llega, continúa en silencio (no bloquea la práctica).
- **REQ-034:** `WHISPER`: pitch 0.86 y volumen 0.5 (`KEY_PARAM_VOLUME`). El borrador solo cambiaba el pitch; el TTS de Android no susurra de verdad, así que el efecto es "voz baja".
- **REQ-035:** Campana: `SoundPool` con `R.raw.bell_soft` (cargado al iniciar). Si falta el recurso o falla la carga, se usa `ToneGenerator` (`TONE_PROP_BEEP`, volumen bajo, 150 ms). Campana de hito: volumen 0.6; de cierre: 0.8.
- ~~**REQ-036 (v1.0):** *(fase futura, B-02)*.~~ Motivo: implementado en v0.4.0 para las grabaciones propias (ADR-0009).
- **REQ-036:** Si `audioAssetUri` es una ruta local (`/…`) que existe, el segmento CORE principal se reproduce con esa grabación (`MediaPlayer`, velocidad con `PlaybackParams` y sin cambiar el tono) en lugar del TTS. Si falta o falla, se usa TTS sin error. Los `asset:///` del catálogo se ignoran hasta tener audios licenciados (Q-04).
- **REQ-037:** `release()` libera el TTS y el SoundPool. La llama el servicio en `onDestroy`.
- **REQ-038 (NUEVO v1.1):** `PracticeRunnerEngine.setPace(wpm)` cambia la velocidad durante la práctica (desde la próxima frase) y la expone en `RunnerSnapshot.paceWpm`. El servicio la recibe con `ACTION_SET_PACE`. El rango de `setPaceFromWpm` es de 0,5× a 1,6× (antes 0,72× a 1,25×).
- **REQ-039 (NUEVO v1.1):** Ajustes de voz globales (`VoiceSettings` en `ManiSettings`): voz TTS sin red, tono, volumen y ecualización de 3 bandas con biquads (graves 220 Hz en estante, medios 1,5 kHz en campana, agudos 4,5 kHz en estante, ±12 dB, con limitador). Con la EQ activa, la frase se sintetiza a archivo y se procesa como el susurro.

### Restricciones
- **CON-001:** Los chequeos de cancelación usan `coroutineContext.ensureActive()` / `currentCoroutineContext().isActive`, **no** `scope.isActive` (bug del borrador: `scope` sigue activo aunque el job se cancele).
- **CON-002:** El estado se muta solo con `MutableStateFlow.update {}`.
- **CON-003:** En el borrador, la inicialización de `TextToSpeech` usa `tts` dentro de su propio callback de construcción. Hay que inicializar con `lateinit` o hacer la elección de idioma después del callback, para evitar acceder a un objeto a medio construir.
- **GUD-001:** No usar `delay` arbitrarios para "esperar el TTS"; siempre usar el callback.

## 4. Contratos

### 4.1 Modelos

```kotlin
enum class RunnerStatus { IDLE, PLAYING, PAUSED, COMPLETED }
enum class RunnerStage { SEGMENTS_BEFORE, MANTRA_LOOP, SEGMENTS_AFTER, DONE }   // NUEVO (REQ-022)

enum class RecitationMode(val labelEs: String) {
    SPOKEN("Voz clara"), WHISPER("Susurro sereno"), MENTAL("Recitación mental / pausa")
}

data class PracticeSegmentModel(
    val id: String, val order: Int, val phase: String, val heading: String, val text: String,
    val transliteration: String? = null, val meaning: String? = null,
    val pauseAfterSec: Int = 3, val repeatCount: Int = 1
)

data class MantraRunConfig(
    val targetRepetitions: Int = 21, val bellEveryN: Int = 7,
    val pauseBetweenRepsMs: Long = 900L, val recitationMode: RecitationMode = RecitationMode.SPOKEN,
    val presetCounts: List<Int> = listOf(3, 7, 21, 108)
)

data class PracticeRunSpec(
    val practiceId: String, val title: String, val defaultPaceWpm: Int = 120,
    val meritDedication: String, val segments: List<PracticeSegmentModel>,
    val mantraConfig: MantraRunConfig? = null,
    val practiceType: String = "PRAYER"                   // NUEVO: lo usa Auto y la UI
)

data class RunnerSnapshot(
    val status: RunnerStatus = RunnerStatus.IDLE,
    val stage: RunnerStage = RunnerStage.SEGMENTS_BEFORE,
    val practiceId: String? = null, val practiceTitle: String = "",
    val segmentIndex: Int = 0, val totalSegments: Int = 0,
    val currentSegment: PracticeSegmentModel? = null,
    val segmentRepetition: Int = 1, val segmentTotalRepetitions: Int = 1,
    val mantraRepetition: Int = 0, val mantraTargetRepetitions: Int = 0,
    val recitationMode: RecitationMode = RecitationMode.SPOKEN,
    val pauseRemainingSec: Int = 0, val meritDedication: String = "",
    val sessionStartedAtEpochMs: Long = 0L                // NUEVO: para persistir la sesión
)
```

### 4.2 Puerto de voz

```kotlin
interface SpeechPort {
    fun setPaceFromWpm(wpm: Int)
    suspend fun speakAndAwait(text: String, mode: RecitationMode)
    fun ringBell(isCycleMilestone: Boolean)
    fun stopSpeaking()
    fun release()
}
class SpeechAndBellController(context: Context) : SpeechPort
```

### 4.3 Motor

```kotlin
class PracticeRunnerEngine(
    scope: CoroutineScope,
    speech: SpeechPort,
    clock: () -> Long = System::currentTimeMillis,
    mentalRepDurationMs: Long = 2600L,
    onSessionFinished: suspend (spec: PracticeRunSpec, snapshot: RunnerSnapshot, completedFull: Boolean) -> Unit
) {
    val snapshot: StateFlow<RunnerSnapshot>
    fun start(spec: PracticeRunSpec, initialMode: RecitationMode = RecitationMode.SPOKEN)
    fun pause(); fun resume(); fun stop(recordAsCompleted: Boolean = false)
    fun setRecitationMode(mode: RecitationMode)
}
```

## 5. Criterios de aceptación

- **AC-001:** Dado un mantra con `target = 7`, `bellEveryN = 3` y 1 segmento CORE, cuando corre con un `FakeSpeechPort`, entonces se llama a `speakAndAwait` exactamente 7 veces y `ringBell(true)` 2 veces (en las vueltas 3 y 6), además de 1 `ringBell(false)` al final.
- **AC-002:** Dada la meditación de maitri (3 segmentos, repeticiones 1, 3 y 1), entonces se hablan 5 locuciones y la suma de pausas es 12 + 15·3 + 8 = 65 s (tiempo virtual).
- **AC-003:** Dada una pausa en la vuelta 4 de 21, cuando se reanuda, entonces la siguiente locución corresponde a la vuelta 5 y no se repite ningún segmento previo.
- **AC-004:** Dado el modo `MENTAL`, entonces `speakAndAwait` no se llama durante el bucle y el tiempo virtual por vuelta es `2600 + pauseBetweenRepsMs`.
- **AC-005:** Dada una práctica completada, cuando luego se llama a `stop(recordAsCompleted = true)`, entonces `onSessionFinished` se invocó **una sola vez** en total.
- **AC-006:** Dado un TTS que nunca inicializa, entonces la práctica avanza igual (timeout de 5 s, en silencio).
- **AC-007:** Dado que falta `res/raw/bell_soft`, entonces `ringBell` no lanza excepción.

## 6. Estrategia de pruebas

- `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `advanceTimeBy`) para el tiempo virtual.
- `FakeSpeechPort` que registra llamadas y responde enseguida.
- Pruebas manuales en dispositivo: voz es-AR, velocidad y campana.

## 7. Justificación

Separar motor, voz y servicio permite testear la lógica sin Android, reutilizar el motor desde Compose y desde Android Auto, y cambiar el TTS por audio grabado más adelante.

## 8. Dependencias

- **PLT-001:** `android.speech.tts.TextToSpeech` (motor de voz del sistema; puede faltar el paquete de español, ver casos borde).
- **INF-001:** `res/raw/bell_soft.ogg` con licencia libre (registrar en `docs/creditos-audio.md`).

## 9. Casos borde

- Dispositivo sin voz en español: se usa `Locale.getDefault()` y la UI muestra una sola vez un aviso suave con enlace a los ajustes de TTS.
- `targetRepetitions` elegido por el usuario (preset) distinto al del catálogo: se pasa en `MantraRunConfig` y gana sobre el catálogo.
- Segmentos vacíos (`text` en blanco): se saltan sin pausa.
- `start()` llamado mientras se está en PLAYING: reinicia sin reportar la sesión previa como completa. *(Pregunta abierta Q-05: ¿se registra como sesión parcial?)*

## 10. Validación

AC-001 a AC-007 automatizados (excepto AC-006 y AC-007, que pueden ser instrumentados).

## 11. Relacionadas

`spec-architecture-servicio-reproduccion.md`, `spec-architecture-android-auto.md`, `spec-design-ui-navegacion.md`.
