---
title: UI, navegación, ViewModel y tema visual
version: 1.1
date_created: 2026-11-24
last_updated: 2026-11-24
owner: Vicente
tags: [design, ui, compose, navegacion]
---

# Introducción

Define las pantallas, rutas, el ViewModel compartido y el tema `ManiTheme`. La UI tiene que ser serena, legible y con pocas pantallas, sin ruido visual.

## 1. Propósito y alcance

Paquetes `ui`, `ui.home`, `ui.practice`, `ui.navigation`, `ui.theme` (las pantallas de bienestar y Tres Joyas tienen su propia spec). Referencia en el chat:
- PracticeRunner: líneas ~1225-1745
- Home: ~3536-3925
- ViewModel y NavGraph: ~4075-4520 y ~5570-6073
- Tema y MainActivity: ~6291-6545

## 2. Definiciones

- **Inicio / Refugio-Hoy:** pantalla principal (`home`).
- **Banner de sesión activa:** tarjeta que aparece si `sessionState.status` es PLAYING o PAUSED.

## 3. Requisitos

### Navegación
- **REQ-001:** Las rutas son exactamente las de §4.1. `startDestination = home`.
- **REQ-002:** Navegar a una práctica **no** inicia la reproducción. El usuario toca "Comenzar". Si la práctica ya está corriendo en el servicio, la pantalla muestra su estado en vivo.

### ViewModel
- **REQ-010:** Un único `ManiViewModel` con factory manual (sin Hilt en v1; ver ADR-0002). Recibe los 4 DAOs.
- **REQ-011:** El ViewModel observa `PracticePlaybackService.sessionState` directamente. **Se elimina** `syncRunnerSnapshot()` del borrador: era un puente manual propenso a desincronizarse.
- **REQ-012:** Expone `homeUiState: StateFlow<RefugeHomeUiState>`, `practicesForSelectedJewel`, `mindfulAppTargets` y `observePracticeSpec(id)`, más las acciones de §4.2.
- **REQ-013:** "Inicio del día" y "semana" se calculan **en cada emisión** (no en la construcción del ViewModel) con `java.time` y la zona horaria del sistema. Semana = últimos 7 días calendario, incluido hoy.

### PracticeRunnerScreen
- **REQ-020:** Secciones en orden:
  1. Encabezado (título, subtítulo, tradición).
  2. Progreso (segmento X de N, vuelta X de N).
  3. Tarjeta central: `heading`, `text`, `transliteration` (si hay), `meaning` (desplegable), con animación de fundido suave entre segmentos.
  4. Indicador de pausa ("Pausa contemplativa · 8 s").
  5. Selector de modo (Voz / Susurro / Mental) como `SegmentedButton`.
  6. Selector de vueltas (presets del catálogo) **solo** si es MANTRA y el estado es IDLE.
  7. Controles: Comenzar / Pausar / Reanudar / Detener.
  8. Tarjeta de dedicación del mérito al COMPLETAR o al detener.
- **REQ-021:** La tarjeta de dedicación tiene el botón "Dedicar el mérito" (marca `merit_dedicated` con el `lastFinishedSessionId`) y "Volver" (sin dedicar, sin culpa).
- **REQ-022:** Mientras la práctica corre, la pantalla puede mantenerse encendida (`keepScreenOn`) **solo** si el usuario lo activa (toggle). Por defecto está apagado para ahorrar batería y porque el servicio sigue sin pantalla.

### RefugeHomeScreen
- **REQ-030:** Bloques en orden:
  1. Encabezado "Hoy en el camino" + acceso a FAQ.
  2. Banner de sesión activa (si hay).
  3. Tarjeta de freno al impulso ("¿Sentís el impulso de comprar o scrollear sin rumbo?" → `impulse_pause`) + enlace "Configurar apps" (`mindful_targets`).
  4. Práctica del día.
  5. Accesos rápidos (hasta 6 prácticas, con favoritos primero).
  6. Tres Joyas (3 chips).
  7. Semillas de mérito (ver la spec de semillas) + "Dedicar el mérito de hoy".
- **REQ-031:** Práctica del día: rotación determinista por fecha entre las prácticas no `IMPULSE_PAUSE` (`index = epochDay % n`). El borrador fijaba siempre maitri. *(Pregunta abierta Q-06.)*

### Tema
- ~~**REQ-040 (v1.0):** paletas del chat (pergamino, piedra, azafrán apagado y salvia).~~ Motivo: Vicente pidió usar los colores de paramita.org (2026-11-24).
- **REQ-040:** `ManiTheme` claro y oscuro con la paleta del kit global de paramita.org (Elementor, relevada el 2026-11-24). Sin `dynamicColor` (identidad visual estable). Código: `ui/theme/Color.kt` y `Theme.kt`.

  | Rol (claro) | Color | Origen en paramita.org |
  |-------------|-------|------------------------|
  | `primary` | `#115EAA` azul | color primario |
  | `primaryContainer` / `on…` | `#CDECF8` / `#003D6B` | celestes y azul profundo del kit |
  | `secondary` | `#0D4277` azul marino | kit |
  | `tertiary` (acento) | `#FFAE00` dorado | color de acento |
  | `tertiaryContainer` / `on…` | `#FFE8B5` / `#662C00` | kit |
  | `background` / `surface` | `#FEF7E5` crema | kit |
  | `onBackground` | `#212121` | color de texto |
  | `error` | `#B32025` | kit (nunca para "no practicaste", CON-002) |

  Tema oscuro: superficies azul noche derivadas (`#0A1C2E`, `#0F2438`, `#1B3550`; **no** están en el sitio), `primary #2EA3F2`, `secondary #00C7E5`, `tertiary #FFC507`, texto crema.
- **REQ-041:** `ManiTypography`: sans del sistema (Roboto, la misma del sitio) para la interfaz; serif para `display`, `headline` y `titleLarge` (textos de práctica), en sintonía con el logotipo serif de Paramita.
- **REQ-043 (NUEVO):** Iconografía **original** inspirada en el sitio (formas redondeadas y simples, loto, azul y celeste con acento dorado). **No** se usa el logo de Paramita ni se copian sus formas hasta que haya permiso (Q-01). Launcher: rueda de oración (mani) dorada sobre azul `#115EAA`, ícono adaptativo con variante monocroma.
- **REQ-042:** `enableEdgeToEdge()` y manejo de insets con `Scaffold`.

### Accesibilidad
- **REQ-050:** Todos los botones de ícono tienen `contentDescription` en español.
- **REQ-051:** Contraste mínimo WCAG AA (4.5:1) en el texto de cuerpo, en los dos temas.
- **REQ-052:** La UI escala hasta `fontScale = 2.0` sin cortes (scroll vertical en todas las pantallas).
- **REQ-053:** Targets táctiles ≥ 48 dp.

### Restricciones
- **CON-001:** Todos los textos de UI van en `res/values/strings.xml` (no hardcodeados en Composables), para aplicar el test de lista negra y poder traducir a futuro.
- **CON-002:** Ningún color rojo de error para "no practicaste" ni nada parecido (ETH-010).
- **GUD-001:** Animaciones suaves (fundidos de ≥ 300 ms). Nada de rebotes ni confeti.

## 4. Contratos

### 4.1 Rutas

```kotlin
object ManiRoutes {
    const val HOME = "home"
    const val PRACTICE = "practice/{practiceId}"
    const val IMPULSE_PAUSE = "impulse_pause"
    const val MINDFUL_TARGETS = "mindful_targets"
    const val THREE_JEWELS = "three_jewels?jewel={jewel}"   // jewel opcional, default DHARMA
    const val FAQ = "faq"
    fun practiceRoute(practiceId: String) = "practice/$practiceId"
    fun threeJewelsRoute(jewel: JewelTab) = "three_jewels?jewel=${jewel.key}"
}
```

### 4.2 ViewModel (acciones)

```kotlin
fun selectJewel(jewel: JewelTab)
fun observePracticeSpec(practiceId: String): Flow<PracticeRunSpec?>
fun toggleFavorite(practiceId: String)                                   // NUEVO
fun dedicateMeritForSession(sessionId: String)                           // reemplaza dedicateMeritFromPractice
fun dedicateTodayMerit(dedicationText: String)                           // inserta GENEROSITY_MERIT (ver la spec de semillas)
fun recordImpulsePause(event: MeritAndCalmEventEntity)
fun recordSanghaConnection(event: MeritAndCalmEventEntity)
fun saveMindfulAppTarget(target: MindfulAppTargetEntity)
fun toggleMindfulAppTargetEnabled(target: MindfulAppTargetEntity, enabled: Boolean)
fun deleteMindfulAppTarget(packageName: String)
```

### 4.3 Estado de Inicio

```kotlin
data class DailySeedSummary(val seed: SeedType, val title: String, val description: String, val isCultivatedToday: Boolean)
data class RefugeHomeUiState(
    val featuredPractice: PracticeWithDetails? = null,
    val quickPractices: List<PracticeWithDetails> = emptyList(),
    val activeRunnerSnapshot: RunnerSnapshot = RunnerSnapshot(),
    val practiceMomentsThisWeek: Int = 0,      // renombrado (antes recentMeritCountThisWeek)
    val consciousPausesThisWeek: Int = 0,      // renombrado (antes recentImpulsePausesThisWeek)
    val dailySeeds: List<DailySeedSummary> = emptyList(),
    val todayMeritDedication: String = "Que cualquier claridad, pausa o acto amable de hoy beneficie a todos los seres.",
    val todayMeritAlreadyDedicated: Boolean = false
)
```

### 4.4 `toPracticeRunSpec()`

`PracticeWithDetails` → `PracticeRunSpec`: `orderedSegments` → `PracticeSegmentModel`; `mantraConfig` → `MantraRunConfig` (parseando `presetCountsJson`); `practiceType = practice.type`.

## 5. Criterios de aceptación

- **AC-001:** Dada la app recién instalada, cuando abre, entonces muestra Inicio con la práctica del día y 6 accesos rápidos en menos de 1 s después de la siembra.
- **AC-002:** Dada una práctica corriendo en segundo plano, cuando se vuelve a Inicio, entonces aparece el banner con el progreso en vivo y, al tocarlo, se navega a `practice/{id}`.
- **AC-003:** Dada una práctica completada, cuando se toca "Dedicar el mérito", entonces `practice_sessions.merit_dedicated = 1` para esa sesión.
- **AC-004:** Dado `fontScale = 2.0`, entonces ninguna pantalla corta texto ni botones.
- **AC-005:** Dado un día nuevo (cambio de fecha con la app abierta), cuando se vuelve a Inicio, entonces las semillas se recalculan para el nuevo día.
- **AC-006:** Test de UI (Compose): la ruta `three_jewels` sin argumento abre la pestaña DHARMA.
- **AC-007:** `ThemeContrastTest` (unit JVM) verifica ≥ 4.5:1 en los pares de texto de los dos esquemas (REQ-051).

## 6. Estrategia de pruebas

- Compose UI tests (`createAndroidComposeRule`) para la navegación y los estados de la pantalla de práctica.
- Tests del ViewModel con DAOs falsos y `Turbine` para los `StateFlow`.
- Screenshot tests opcionales (Roborazzi o Paparazzi) en claro, oscuro y fontScale 2.

## 7. Justificación

Un ViewModel único simplifica la v1 (pocas pantallas). Si crece, se divide por feature. Observar el servicio directamente elimina un punto de desincronización.

## 8. Dependencias

- **PLT-001:** Compose BOM, Material 3, Navigation Compose, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`.

## 9. Casos borde

- Catálogo todavía no sembrado (primer frame): Inicio muestra un estado vacío sereno ("Preparando las prácticas…"), sin spinner ansioso.
- `practiceId` inexistente en la ruta: mensaje "Esta práctica ya no está disponible" + Volver.

## 10. Validación

AC-001 a AC-006.

## 11. Relacionadas

`spec-design-semillas-merito.md`, `spec-design-tres-joyas-paramita.md`, `spec-architecture-bienestar-digital-freno-impulso.md`, `spec-architecture-servicio-reproduccion.md`.
