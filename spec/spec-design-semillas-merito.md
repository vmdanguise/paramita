---
title: Sistema de semillas de mérito y dedicación (recompensas no competitivas)
version: 1.0
date_created: 2026-11-24
owner: Vicente
tags: [design, merito, recompensas, etica]
---

# Introducción

En lugar de logros o trofeos, la app usa **semillas**: recordatorios sobrios de hábitos sanos (refugio, recitación, maitri, freno consciente y Sangha) que siempre desembocan en la **dedicación del mérito**. No miden avance espiritual.

## 1. Propósito y alcance

Cálculo y presentación de las semillas en Inicio, y el flujo "Dedicar el mérito de hoy". Referencia en el chat: líneas ~3479-3524 (diseño) y ~5620-5684 (cálculo en el ViewModel).

## 2. Definiciones

- **Semilla cultivada hoy:** se cumplió su condición (§4.1) dentro del día calendario local.
- **Dedicación del día:** evento `GENEROSITY_MERIT` con `title = "Dedicación del mérito del día"`.

## 3. Requisitos

- **REQ-001:** Existen exactamente 5 semillas (§4.1). El borrador del chat calculaba solo 3; se completan las 5 del diseño.
- **REQ-002:** Cada semilla muestra: título, descripción breve, estado (cultivada hoy / "disponible"). **Nunca** muestra un estado negativo.
- **REQ-003:** Ícono de estado: brote (cultivada) o semilla (disponible), en colores suaves (salvia o piedra). Sin rojo, sin tachados.
- **REQ-004:** Debajo de las semillas va el texto de dedicación del día y el botón "Dedicar el mérito de hoy". Al tocarlo se inserta el evento de dedicación y el botón pasa a "Mérito dedicado 🙏" (deshabilitado hasta el día siguiente). Dedicar es **opcional** y está disponible aunque no haya ninguna semilla cultivada.
- **REQ-005:** Resumen semanal, en una línea y en tono neutro: "Esta semana: N momentos de práctica · M pausas conscientes". Si ambos son 0, se oculta la línea (no se muestra "0").
- **ETH-001:** Prohibido: rachas, contadores acumulados de por vida, porcentajes, niveles, comparaciones, notificaciones del tipo "¡no pierdas tu…!".
- **ETH-002:** Las semillas no generan notificaciones push.
- **ETH-003:** El texto de cada semilla incluye o enlaza a lo que **no** representa (tooltip o detalle con la columna "Qué no promete" de §4.1).
- **CON-001 (corrige el chat):** "Practicó hoy" **no** depende de `GENEROSITY_MERIT`. El borrador exigía `completedSessionsWeek > 0 && evento GENEROSITY_MERIT hoy`, que mezclaba la semana con el día y la dedicación con la práctica.

## 4. Contratos

### 4.1 Semillas

| `SeedType` | Título | Se cultiva hoy si… | Qué fomenta | Qué NO promete |
|------------|--------|--------------------|-------------|----------------|
| `REFUGE` | Semilla de Refugio | Hay una sesión hoy de una práctica con tag `refugio` **o** fase `MOTIVATION` completada | Abrir la práctica con intención clara en Buddha, Dharma y Sangha | No es realización espiritual ni logro doctrinal; es un punto de partida |
| `RECITATION` | Hilo de Vueltas y Silencio | Hay una sesión hoy de tipo `MANTRA` o `PRAYER` con `completed_repetitions ≥ 1`, o de tipo `GUIDED_MEDITATION` | Recitar o acompañar meditaciones con atención (3/7/21/108, voz, susurro o mental) | No equivale a maestría en el mantra ni a purificación garantizada |
| `MAITRI` | Corazón de Maitri | Hay una sesión hoy de una práctica con tag `maitri` o `amor_bondadoso`, **o** un evento de pausa con outcome `PRACTICED_MAITRI` | Calidez hacia uno mismo y los demás en lugar de reaccionar en automático | No implica haber erradicado toda aversión o apego |
| `OPEN_HAND` | Mano Abierta / Freno Consciente | Hay un evento hoy `IMPULSE_PURCHASE_AVOIDED` o `DIGITAL_CALM_BREAK` | Detener una compra impulsiva, cortar el scroll y practicar generosidad | No es "haber vencido el deseo para siempre", sino haber elegido lucidez en ese instante |
| `SANGHA` | Pulso de Sangha | Hay un evento hoy `SANGHA_CONNECTION` | Practicar o estudiar en vínculo con Paramita o la comunidad | No reemplaza la guía directa ni los canales oficiales |

"Sesión hoy" = fila de `practice_sessions` con `started_at_epoch_ms >= inicioDelDía` (incluye sesiones parciales: el esfuerzo cuenta).

### 4.2 Cálculo (función pura, testeable)

```kotlin
enum class SeedType { REFUGE, RECITATION, MAITRI, OPEN_HAND, SANGHA }

object SeedCalculator {
    fun computeToday(
        sessionsToday: List<SessionWithPractice>,        // con tipo y tags de la práctica
        eventsToday: List<MeritAndCalmEventEntity>,
        practiceTagsById: Map<String, List<String>>
    ): List<DailySeedSummary>
    fun weeklySummary(sessionsWeek: Int, pausesWeek: Int): String?   // null si ambos son 0
}
```

Para registrar `PRACTICED_MAITRI` en eventos de pausa, el `emotion_noticed` se mantiene y el resultado se guarda en la columna `merit_and_calm_events.outcome` (`PauseOutcome.name`). *(Decidido en T-209 / ADR-0003: la columna está en la v1 de la DB; ya no se codifica en `trigger_context`.)*

### 4.3 Dedicaciones por defecto

| Contexto | Texto |
|----------|-------|
| Día | "Que cualquier claridad, pausa o acto amable de hoy beneficie a todos los seres." |
| Sangha | "Que la práctica compartida con la Sangha fortalezca la claridad y el bien de todos los seres." |
| Pausa de impulso | "Que la serenidad y el cuidado de hoy beneficien a todos los seres." |
| Práctica | `practice.merit_dedication` del catálogo |

## 5. Criterios de aceptación

- **AC-001:** Sin actividad hoy → 5 semillas "disponibles", ninguna con estilo negativo, y sin línea semanal si la semana está vacía.
- **AC-002:** Una sesión parcial de `prac_mantra_corazon` hoy → `RECITATION` cultivada.
- **AC-003:** Una sesión de `prac_med_amor_bondadoso` → `MAITRI` y `RECITATION` cultivadas.
- **AC-004:** Una sesión de `prac_refugio_tres_joyas` → `REFUGE` y `RECITATION` cultivadas.
- **AC-005:** Un evento `DIGITAL_CALM_BREAK` → `OPEN_HAND` cultivada.
- **AC-006:** Una sesión de ayer y ninguna hoy → nada cultivado y ningún mensaje de pérdida.
- **AC-007:** "Dedicar el mérito de hoy" dos veces el mismo día → solo 1 evento insertado.

## 6. Estrategia de pruebas

Tests unitarios JVM de `SeedCalculator` con fixtures (AC-001 a AC-006) y test del ViewModel para AC-007.

## 7. Justificación

En el budismo, el mérito no es un trofeo personal: se dedica a todos los seres. Medir "niveles" alimentaría el apego al yo. Las semillas refuerzan la familiarización cotidiana (*gom*) sin presión.

## 8. Dependencias

`spec-schema-base-datos-room.md` (consultas por rango de fechas), `spec-process-etica-contenido-faq.md`.

## 9. Ejemplo de texto en pantalla

```
SEMILLAS DE MÉRITO Y CALMA COTIDIANA
🌱 Semilla de Refugio — cultivada hoy
·  Hilo de Vueltas y Silencio — disponible
🌱 Mano Abierta / Freno Consciente — cultivada hoy
...
Esta semana: 4 momentos de práctica · 2 pausas conscientes
"Que cualquier claridad, pausa o acto amable de hoy beneficie a todos los seres."
[ Dedicar el mérito de hoy ]
```

## 10. Validación

AC-001 a AC-007 y test de lista negra (ETH) sobre los strings de semillas.

## 11. Relacionadas

`spec-design-ui-navegacion.md`, `spec-process-etica-contenido-faq.md`.
