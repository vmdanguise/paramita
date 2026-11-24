---
title: Android Auto — solo oraciones y mantras
version: 1.0
date_created: 2026-11-24
owner: Vicente
tags: [architecture, android-auto, seguridad, audio]
---

# Introducción

En el auto la app ofrece **solo oraciones (PRAYER) y mantras (MANTRA)** para escuchar o recitar con los ojos en el camino. Las meditaciones guiadas y las pausas de impulso se excluyen **por diseño**, porque invitan a cerrar los ojos o a entrar en contemplación profunda, algo incompatible con manejar.

## 1. Propósito y alcance

Paquete `auto`. Referencia en el chat:
- Criterio: líneas ~6755-6793
- Filtro: ~6794-6915
- Pantallas: ~6916-7104
- Manifest: ~7105-7211
- Controlador: ~7214-7431

## 2. Definiciones

- **DHU:** Desktop Head Unit (emulador de Android Auto).
- **Ítem de conducción (`AutoRecitationItem`):** proyección liviana de una práctica apta para el auto.

## 3. Requisitos

### Filtro (seguridad)
- **SEC-001:** `DrivingRecitationCatalogMapper.filterAndMapForDriving` deja pasar **solo** `type ∈ {PRAYER, MANTRA}`. Cualquier otro tipo, incluido uno desconocido, se excluye. Es una lista blanca, no una lista negra.
- **SEC-002:** Además se exige `ttsEnabled == true` (o un audio válido) y al menos un segmento con texto.
- **SEC-003:** Ritmo acotado: `paceWpm.coerceIn(95, 145)`, `pauseBetweenRepsMs.coerceIn(500, 2500)`.
- **SEC-004:** En el auto nunca se muestra el texto de la oración, la transliteración ni el significado. Solo el título, un subtítulo breve ("Mantra · 21 vueltas") y el progreso ("Vuelta 3 de 21").
- **SEC-005:** Mientras hay proyección a Android Auto no se lanzan pausas de impulso (ver la spec de bienestar, REQ-025).

### UI de plantillas
- **REQ-001:** `DrivingSelectionScreen` (`ListTemplate`): filas con título y subtítulo, respetando el límite de ítems del host (`ConstraintManager.getContentLimit(CONTENT_LIMIT_TYPE_LIST)`). Orden: mantras primero, luego oraciones, alfabético.
- **REQ-002:** `DrivingPlaybackScreen` (`PaneTemplate`): título, "Vuelta X de N" o "Segmento X de N", la nota "Atención plena en la conducción · Solo oraciones y mantras", y **solo** 2 acciones: Pausar/Reanudar y Detener.
- **REQ-003:** Las pantallas se refrescan con `invalidate()` cuando cambia el estado o la vuelta, como máximo 1 vez por segundo (los hosts limitan los refrescos de plantilla).
- **REQ-004:** El volumen de la campana en el auto es moderado (≤ 0.5).

### Integración
- **REQ-010:** `ServiceBackedAutoRecitationController : AutoRecitationController` lee `observeAllPracticesWithDetails()`, filtra con el mapper, manda Intents a `PracticePlaybackService` (constantes canónicas de su spec) y traduce `sessionState` a `AutoPlaybackState`.
- **REQ-011:** `DrivingCarAppService : CarAppService` crea un `Session` que instancia el controlador y lo libera en `onDestroy`.
- **REQ-012:** `createHostValidator()`: `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` **solo en debug**. En release se usa la lista de hosts permitidos (`R.array.hosts_allowlist_sample` de la librería).

### Decisión pendiente (bloqueante para F8)
- **CON-001:** Android Auto solo acepta apps de ciertas categorías. Las plantillas `androidx.car.app` con `category.MEDIA` existen, pero el camino estándar para apps de audio es una `MediaLibraryService` de **Media3**: el auto dibuja su propia UI de medios y los controles del volante funcionan automáticamente. El chat propone car-app con categoría MEDIA. Hay que resolverlo en el **ADR-0004** antes de implementar. Si se elige Media3, esta spec se adapta: los mismos ítems filtrados se exponen como `MediaItem` navegables, y SEC-001 a SEC-004 siguen vigentes.

## 4. Contratos

```kotlin
enum class DrivingPracticeKind(val wireType: String, val labelEs: String) {
    PRAYER("PRAYER", "Oración"), MANTRA("MANTRA", "Mantra")
}
data class AutoRecitationItem(
    val practiceId: String, val kind: DrivingPracticeKind, val title: String,
    val shortSubtitle: String, val spokenText: String, val paceWpm: Int,
    val targetRepetitions: Int, val bellEveryN: Int, val pauseBetweenRepsMs: Long,
    val meritDedication: String
)
enum class AutoPlaybackStatus { IDLE, PLAYING, PAUSED }
data class AutoPlaybackState(
    val status: AutoPlaybackStatus = AutoPlaybackStatus.IDLE,
    val currentItem: AutoRecitationItem? = null,
    val currentRepetition: Int = 0, val totalRepetitions: Int = 0,
    val drivingSafetyNote: String = "Atención plena en la conducción · Solo oraciones y mantras"
)
interface AutoRecitationController {
    fun currentItems(): List<AutoRecitationItem>
    fun playbackState(): AutoPlaybackState
    fun startItem(item: AutoRecitationItem)
    fun togglePauseResume()
    fun stopPlayback()
}
```

Manifest:

```xml
<meta-data android:name="com.google.android.gms.car.application"
           android:resource="@xml/automotive_app_desc" />
<service android:name=".auto.DrivingCarAppService" android:exported="true">
  <intent-filter>
    <action android:name="androidx.car.app.CarAppService" />
    <category android:name="androidx.car.app.category.MEDIA" />  <!-- sujeto a ADR-0004 -->
  </intent-filter>
</service>
```

`res/xml/automotive_app_desc.xml`: `<automotiveApp><uses name="template" /></automotiveApp>`.

## 5. Criterios de aceptación

- **AC-001 (test unitario, obligatorio):** Dado el catálogo v2 (8 prácticas), `filterAndMapForDriving` devuelve exactamente `prac_mantra_corazon`, `prac_refugio_tres_joyas` y `prac_dedicatoria_merito_y_sangha`.
- **AC-002:** Dada una práctica de tipo `GUIDED_MEDITATION` con tag `mantra`, no aparece en el auto.
- **AC-003:** Dado `default_pace_wpm = 200`, el ítem tiene `paceWpm = 145`.
- **AC-004:** En el DHU, seleccionar el mantra inicia el audio y la pantalla muestra "Vuelta 1 de 21". Pausar y Reanudar funcionan.
- **AC-005:** En el DHU no hay más de 2 acciones en la pantalla de reproducción.

## 6. Estrategia de pruebas

- Unit JVM del mapper (AC-001 a AC-003), que corre en la CI sí o sí.
- Manual con el DHU (AC-004 y AC-005), con el checklist en `docs/`.

## 7. Justificación

La seguridad vial está por encima de cualquier funcionalidad. Una lista blanca de tipos garantiza que el contenido nuevo del catálogo no se filtre al auto por accidente.

## 8. Dependencias

- **PLT-001:** `androidx.car.app:app` 1.7.x (o Media3 session, según el ADR-0004).
- **COM-001:** Car App Quality Guidelines de Google Play (categoría de medios).

## 9. Casos borde

- El catálogo no tiene ítems aptos → `ListTemplate` con el mensaje "No hay oraciones o mantras disponibles".
- Se inicia la reproducción en el teléfono con una meditación y se conecta el auto → el auto muestra el estado, pero no ofrece reanudar meditaciones *(Q-07: ¿se pausa automáticamente al conectar?)*.

## 10. Validación

AC-001 a AC-005.

## 11. Relacionadas

`spec-architecture-servicio-reproduccion.md`, `spec-data-catalogo-json.md`, `docs/adr/0004-android-auto-media3-vs-carapp.md`.
