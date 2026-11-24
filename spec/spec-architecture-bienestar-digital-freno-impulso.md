---
title: Bienestar digital y freno al impulso (pausa lúcida + AccessibilityService opt-in)
version: 1.1
date_created: 2026-11-24
last_updated: 2026-11-24
owner: Vicente
tags: [architecture, bienestar-digital, accesibilidad, privacidad]
---

# Introducción

Cuando el usuario abre una app que **él mismo** marcó como sensible (compras, scroll) o toca el botón "Freno de impulso", la app ofrece una **pausa lúcida**: respirar, nombrar la emoción, practicar algo breve y elegir con consciencia. No bloquea, no castiga.

## 1. Propósito y alcance

Paquetes `digitalwellbeing` y `ui.wellbeing`. Referencia en el chat:
- Flujo: líneas ~1747-1874
- Pantalla: ~2446-2795
- Activity: ~3927-4073
- AccessibilityService: ~4526-4655
- Pantalla de apps objetivo: ~4656-5215

## 2. Definiciones

- **App objetivo:** fila de `mindful_app_targets`.
- **Cooldown:** minutos mínimos entre dos pausas para el mismo paquete.
- **Divulgación prominente:** pantalla propia que explica qué hace el servicio de accesibilidad antes de mandar al usuario a Ajustes (requisito de Google Play).

## 3. Requisitos

### Flujo de la pausa (ImpulsePauseScreen)
- **REQ-001:** Etapas:
  1. **Umbral de calma** (6 s, cuenta regresiva: "Inhalá y soltá los hombros · 6 s"). Se puede saltar.
  2. **Indagación:** "¿Qué está buscando tu mente en este momento?" con 4 opciones (`ImpulseEmotion`).
  3. **Monto opcional:** solo si la emoción es `IMPULSE_BUY` ("Monto estimado (opcional, ARS)" + "Solo como referencia personal; sin metas rígidas ni culpa.").
  4. **Micro-maitri:** texto breve + botón "Escuchar práctica guiada" (inicia `prac_med_amor_bondadoso` o un mantra de 7 vueltas según la emoción, ver §4.2).
  5. **Cierre:** botones "Descansar en calma" (registra el evento y cierra), "Continuar con consciencia" (registra y vuelve a la app) y "Cerrar".
  - *v1.1:* las etapas 4 y 5 se muestran juntas en una sola pantalla. "Continuar con consciencia" está visible **en todas las etapas** (no solo al final), para que nunca haya que atravesar la pausa para salir (REQ-004). "Descansar en calma" lleva al inicio del teléfono.
- **REQ-002:** Registro en `merit_and_calm_events` con `event_type = IMPULSE_PURCHASE_AVOIDED` si la emoción es `IMPULSE_BUY` y el outcome **no** es `PROCEEDED_CONSCIOUSLY`. Si no, `DIGITAL_CALM_BREAK`. Si el outcome es `PROCEEDED_CONSCIOUSLY`, también se registra `DIGITAL_CALM_BREAK` (haber hecho la pausa ya es valioso), pero sin monto.
- **REQ-003:** Al registrar se muestra la dedicación ("Que la serenidad y el cuidado de hoy beneficien a todos los seres.").
- **REQ-004:** "Continuar con consciencia" nunca pide confirmación extra ni muestra texto de culpa (SEC-001 de ética).

### Activity
- **REQ-010:** `ImpulsePauseActivity`: `launchMode="singleTop"`, `excludeFromRecents="true"`, `taskAffinity=""` (para no mezclarse con la tarea de la app principal), `exported="false"`. Extras: `EXTRA_PACKAGE_NAME`, `EXTRA_APP_DISPLAY_NAME` (ambos opcionales; sin ellos es el "freno manual").
- **REQ-011:** Se lanza con `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
- **REQ-012:** "Continuar con consciencia" hace `finish()` (el usuario vuelve a la app objetivo, que sigue debajo).
- **REQ-013:** "Escuchar práctica" inicia el servicio con la práctica y abre `MainActivity` en `practice/{id}`.

### Detección (AccessibilityService)
- **REQ-020:** Config XML: `accessibilityEventTypes="typeWindowStateChanged"`, `canRetrieveWindowContent="false"`, `notificationTimeout="250"`, `feedbackGeneric`.
- **REQ-021:** Solo se lee `event.packageName`. Se ignoran: el paquete null, el propio paquete, el launcher por defecto (`ACTION_MAIN/CATEGORY_HOME` resuelto) y `com.android.systemui`.
- **REQ-022:** `MindfulAppTriggerEvaluator.evaluateForegroundPackage(pkg, ownPkg, now)`: busca el target, exige `promptEnabled`, consulta el cooldown y, si corresponde, hace `markShown` **antes** de devolver el target (evita bucles al volver de la pausa).
- **REQ-023 (mejora sobre el chat):** El cooldown se **persiste** (SharedPreferences `impulse_cooldown_prefs`, clave = paquete, valor = epoch ms) para que sobreviva al reinicio del servicio. El borrador lo guardaba solo en memoria.
- **REQ-024:** Debounce: se cancela la evaluación previa si llega otro evento (como en el borrador).
- **REQ-025:** Si Android Auto está conectado (`CarConnection.type == CONNECTION_TYPE_PROJECTION`), no se lanzan pausas (SEC-010 de ética).
  - *v1.1:* mientras `androidx.car.app` no sea dependencia (F8), se usa `UiModeManager.currentModeType == UI_MODE_TYPE_CAR`, que cubre el modo auto del teléfono. Pasar a `CarConnection` queda en T-708.

### Configuración (MindfulAppTargetsScreen, ruta `mindful_targets`)
- **REQ-030:** Tarjeta de estado del servicio (activo / pendiente) que se reevalúa en `ON_RESUME`, con el botón "Activar en Ajustes".
- **REQ-031:** Antes de abrir los Ajustes de Accesibilidad se muestra un **diálogo de divulgación** con el texto de §4.3 y dos botones: "Entiendo, ir a Ajustes" / "Ahora no".
- **REQ-032:** Formulario: elegir de la lista de apps instaladas que se pueden lanzar (`queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)`) o escribir el paquete; categoría `SHOPPING`/`SOCIAL_SCROLL`/`OTHER`; `promptEnabled`; cooldown de 5, 15, 30 o 60 minutos o personalizado (1-1440).
- **REQ-033:** Lista de targets con switch, editar y eliminar.
- **REQ-034:** Manifest: `<queries>` con un intent `MAIN/LAUNCHER` para poder listar apps en Android 11+ **sin** `QUERY_ALL_PACKAGES`.

### Privacidad
- **SEC-001:** El servicio no lee contenido de ventanas, texto, mensajes ni contraseñas, y no guarda el historial de apps abiertas. Solo se persiste el último momento en que se mostró la pausa por paquete.
- **SEC-002:** Nada sale del dispositivo.
- **SEC-003:** Desactivar el servicio desde Ajustes detiene todo al instante. La app sigue funcionando completa sin él (freno manual).

## 4. Contratos

### 4.1 Tipos

```kotlin
enum class ImpulseEmotion(val labelEs: String) {
    ANXIETY("Inquietud o tensión"), TIREDNESS("Cansancio o saturación"),
    BOREDOM("Aburrimiento o vacío"), IMPULSE_BUY("Ganas de comprar por impulso")
}
enum class PauseOutcome { PRACTICED_MAITRI, PAUSED_AND_LEFT_APP, PROCEEDED_CONSCIOUSLY }

data class ImpulsePauseDecision(
    val packageName: String?, val displayContext: String, val emotion: ImpulseEmotion,
    val outcome: PauseOutcome, val avoidedAmountArs: Double? = null,
    val meritDedication: String = "Que la serenidad y el cuidado de hoy beneficien a todos los seres."
)

interface CooldownStore { fun lastShown(pkg: String): Long?; fun markShown(pkg: String, at: Long) }
class ImpulseCooldownTracker(store: CooldownStore) {
    fun shouldTrigger(packageName: String, cooldownMinutes: Int, nowEpochMs: Long): Boolean
    fun markShown(packageName: String, nowEpochMs: Long)
}
class MindfulAppTriggerEvaluator(targetDao: MindfulAppTargetDao, cooldownTracker: ImpulseCooldownTracker) {
    suspend fun evaluateForegroundPackage(packageName: String?, ownPackageName: String,
        nowEpochMs: Long = System.currentTimeMillis()): MindfulAppTargetEntity?
}
fun isMindfulAccessibilityServiceEnabled(context: Context): Boolean
```

### 4.2 Práctica sugerida según la emoción

| Emoción | Sugerencia |
|---------|------------|
| ANXIETY | `prac_med_amor_bondadoso` |
| TIREDNESS | `prac_calma_digital_noche` |
| BOREDOM | `prac_37_practicas_refugio_y_impermanencia` |
| IMPULSE_BUY | `prac_pausa_impulso_compra`, luego `prac_mantra_corazon` (7 vueltas) |

### 4.3 Textos

- `accessibility_service_description`: "Permite detectar únicamente cuándo abrís aplicaciones que vos mismo marcaste como sensibles (compras o scroll continuo) para ofrecerte una pausa serena de calma y amor bondadoso. No lee mensajes ni contraseñas."
- Divulgación (diálogo): "Mani usa el servicio de accesibilidad de Android **solo** para saber qué app se abre en pantalla y compararla con la lista que vos elegiste. No lee lo que escribís ni lo que se muestra, no guarda tu historial y nada sale de tu teléfono. Podés desactivarlo cuando quieras desde Ajustes."

## 5. Criterios de aceptación

- **AC-001:** Dado un target con cooldown de 1 minuto, cuando se abre esa app, entonces aparece la pausa. Si se vuelve a abrir antes de 1 minuto, no aparece.
- **AC-002:** Dado "Continuar con consciencia", cuando se vuelve a la app objetivo, entonces la pausa **no** se relanza (no hay bucle).
- **AC-003:** Dado que se reinicia el proceso durante el cooldown, entonces la pausa sigue sin aparecer hasta que vence (REQ-023).
- **AC-004:** Dado un target con `promptEnabled = false`, entonces nunca aparece la pausa.
- **AC-005:** Dada la emoción `IMPULSE_BUY` y el monto 25000, cuando se elige "Descansar en calma", entonces se inserta `IMPULSE_PURCHASE_AVOIDED` con `avoided_amount_estimate = 25000` y `currency_code = "ARS"`.
- **AC-006:** Dado el servicio desactivado, entonces la tarjeta de estado dice "pendiente" y el freno manual funciona.
- **AC-007:** Dado el diálogo de divulgación con "Ahora no", entonces no se abre Ajustes.

## 6. Estrategia de pruebas

- Unit JVM: `ImpulseCooldownTracker` (reloj inyectado, store en memoria), `MindfulAppTriggerEvaluator` (DAO falso).
- Compose UI test del flujo de `ImpulsePauseScreen`.
- Manual: activar el servicio en un dispositivo real y probar con una app de prueba (AC-001 a AC-003).

## 7. Justificación

El impulso no se resuelve prohibiendo: se aborda reconociendo la emoción y abriendo un espacio de calma. El AccessibilityService es la única forma confiable de detectar la apertura de una app en tiempo real. Por eso se acota al mínimo y se divulga con claridad.

## 8. Dependencias

- **COM-001:** Política de Google Play sobre el uso de la API de Accesibilidad (divulgación prominente, propósito declarado en Play Console). Riesgo de rechazo: ver ADR-0006 y la alternativa B-07 (UsageStats).
- **PLT-001:** `androidx.car.app` (`CarConnection`) para REQ-025.

## 9. Casos borde

- Múltiples eventos al cambiar de app → debounce + `singleTop`.
- El usuario agrega la propia app como target → se rechaza en el formulario.
- La app objetivo se desinstala → el target queda, pero no molesta. Se muestra "(no instalada)" en la lista.
- Cooldown de 0 o negativo → se normaliza a 1.

## 10. Validación

AC-001 a AC-007.

## 11. Relacionadas

`spec-process-etica-contenido-faq.md`, `spec-design-semillas-merito.md`, `docs/adr/0006-accessibility-vs-usagestats.md`.
