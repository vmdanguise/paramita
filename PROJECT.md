# PROJECT.md — Mani · oraciones, mantras y pausa consciente

> Documento rector del proyecto. Toda decisión de producto, arquitectura o contenido debe ser coherente con este archivo.
> Si algo de acá cambia, actualizá también `PLAN.md`, la spec afectada en `spec/` y, si es una decisión estructural, agregá un ADR en `docs/adr/`.

## 1. Visión

Una APK de Android que funcione como **compañero de práctica sereno** para el día a día, anclado en las enseñanzas y recursos de [paramita.org](https://www.paramita.org/) (budismo tibetano, linaje Sakya).

La app **se prende y recita sola** oraciones y mantras, **guía meditaciones**, **fomenta el amor bondadoso (maitri)**, registra el **mérito** de forma sobria cerrando siempre con la **dedicación del mérito**, y ayuda a **frenar compras impulsivas** y a sostener el **bienestar digital**, todo bajo el refugio en **Buddha, Dharma y Sangha**.

No es un chatbot ruidoso, ni una tienda, ni un juego. Es un espacio de refugio y claridad en el teléfono.

## 2. Pilares del producto

| # | Pilar | Qué hace la app |
|---|-------|-----------------|
| P1 | Recitación automática | Oraciones y mantras por TTS en español o audio local, con repeticiones 3 / 7 / 21 / 108, modos voz / susurro / mental y campana cada N vueltas. |
| P2 | Meditaciones guiadas | Secuencias por fases: motivación, postura y respiración, práctica central, contemplación, pausa y dedicación. |
| P3 | Amor bondadoso (maitri) | Prácticas y micro-pausas de calidez hacia uno mismo, seres queridos, personas neutras y difíciles. |
| P4 | Mérito sobrio | Registro no competitivo de práctica, generosidad, frenos al impulso y conexión con la Sangha. Todo se cierra con dedicación del mérito. |
| P5 | Freno al impulso y bienestar digital | Pausa lúcida al abrir apps de compras o scroll (opt-in): respirar, nombrar la emoción, practicar algo breve y elegir con consciencia. |
| P6 | Tres Joyas | Buddha, Dharma y Sangha como ejes de navegación, con enlaces a canales oficiales de Paramita. |
| P7 | Android Auto (seguro) | En el auto solo se ofrecen **oraciones y mantras** por audio. Nunca meditaciones ni pausas de impulso. |

## 3. Límites éticos (no negociables)

Estas reglas tienen prioridad sobre cualquier pedido de funcionalidad. Detalle completo en `spec/spec-process-etica-contenido-faq.md`.

1. **Nada de logros espirituales.** Ningún texto, hito, notificación o pantalla menciona ni sugiere nirvana, iluminación, budeidad, realizaciones ni "niveles espirituales".
2. **No reemplaza a un maestro.** La enseñanza profunda, las transmisiones y las dudas doctrinales corresponden a los canales oficiales de Paramita y a un maestro o guía calificado.
3. **Cero gamificación ansiosa.** No hay rachas punitivas, puntos competitivos, rankings, culpa, alarmas rojas ni sonidos estridentes. Si un día no practicaste, la app te recibe igual.
4. **Textos litúrgicos intocables.** No se reescriben, resumen ni "mejoran" oraciones, mantras o versos. Todo contenido nuevo pasa por revisión editorial.
5. **No inventar datos de Paramita.** Ni horarios, ni URLs, ni atribuciones. Solo se enlaza a:
   - https://www.paramita.org/medita-con-nosotros/
   - https://cursos.paramita.org/
   - https://www.paramita.org/ (raíz)
6. **Autonomía del usuario.** La pausa de impulso siempre tiene la opción "Continuar con consciencia". Nada se bloquea por la fuerza.
7. **Privacidad.** La detección de apps es opt-in, solo mira qué app pasa a primer plano (`TYPE_WINDOW_STATE_CHANGED`) y **no lee** contenido de ventanas, mensajes ni contraseñas. Todo queda en el dispositivo.
8. **Conducción segura.** En Android Auto no hay textos largos, ni meditaciones, ni overlays.
9. **Asesor IA (si alguna vez existe):** opcional, acotado al catálogo, y nunca se presenta como autoridad doctrinal.

## 4. Stack tecnológico

| Área | Tecnología |
|------|------------|
| Lenguaje | Kotlin (JVM target 17) |
| UI | Jetpack Compose + Material 3, tema propio `ManiTheme` (claro y oscuro) con la paleta de paramita.org: azul `#115EAA`, celeste, dorado `#FFAE00` y crema `#FEF7E5` |
| Navegación | Navigation Compose |
| Persistencia | Room (SQLite) con KSP; `ManiDatabase` |
| Catálogo | `assets/catalog_v1.json` + `kotlinx.serialization` |
| Voz | `android.speech.tts.TextToSpeech` (es-AR, luego es-ES, luego idioma del sistema) |
| Campana | `SoundPool` con `res/raw/bell_soft.*`; si falta el archivo, `ToneGenerator` como respaldo |
| Segundo plano | `ForegroundService` tipo `mediaPlayback` (`PracticePlaybackService`) |
| Bienestar digital | `AccessibilityService` opt-in (`MindfulAppAccessibilityService`) |
| Android Auto | `androidx.car.app` (`DrivingCarAppService`). Ver el ADR-0004 sobre la alternativa Media3 |
| Corrutinas | `kotlinx-coroutines-android`, `StateFlow` |
| SDK | `minSdk 26`, `targetSdk 36`, `compileSdk 37.2` (ver ADR-0007) |
| Build | AGP 9.5 (Kotlin integrado), Kotlin 2.4, KSP 2.3, Gradle 9.9; versiones en `gradle/libs.versions.toml` |
| Identidad | Nombre **Mani** (la rueda de oración que "recita sola"); `applicationId` `ar.param.advisor` (ver ADR-0007) |

**Offline-first:** todo funciona sin conexión. Solo se abre internet cuando el usuario toca un enlace a paramita.org.

## 5. Arquitectura (vista general)

```
┌──────────────────────── UI (Compose) ─────────────────────────┐
│ RefugeHomeScreen · PracticeRunnerScreen · ImpulsePauseScreen  │
│ RefugeAndParamitaScreen · MindfulAppTargetsScreen · FaqScreen │
│                  ManiNavGraph · ManiTheme             │
└──────────────┬───────────────────────────────┬────────────────┘
               │                               │
      ManiViewModel        PracticePlaybackService.sessionState
               │                               │ (StateFlow<RunnerSnapshot>)
┌──────────────▼──────────┐       ┌────────────▼──────────────────────┐
│ data.local (Room)        │       │ service: PracticePlaybackService  │
│ DAOs · Entities          │◄──────┤ engine: PracticeRunnerEngine      │
│ data.catalog             │       │         SpeechAndBellController   │
│ CatalogMapper · Seeder   │       └────────────▲──────────────────────┘
└──────────────▲──────────┘                    │
               │                   ┌───────────┴───────────────────────┐
     assets/catalog_v1.json        │ auto: DrivingCarAppService         │
                                   │ ServiceBackedAutoRecitationCtrl    │
┌──────────────────────────────┐   │ DrivingRecitationCatalogMapper     │
│ digitalwellbeing             │   └───────────────────────────────────┘
│ MindfulAppAccessibilityService│
│ MindfulAppTriggerEvaluator    │──► ImpulsePauseActivity
│ ImpulseCooldownTracker        │
└──────────────────────────────┘
```

### Paquetes (`ar.param.advisor`)

| Paquete | Responsabilidad | Spec |
|---------|-----------------|------|
| `(raíz)` | `ManiApplication`, `MainActivity` | `spec-infrastructure-build-manifest.md` |
| `engine` | `PracticeRunnerEngine`, `SpeechAndBellController`, modelos `PracticeRunSpec`, `RunnerSnapshot` | `spec-architecture-motor-practica-tts.md` |
| `service` | `PracticePlaybackService`, notificación, acciones e intents | `spec-architecture-servicio-reproduccion.md` |
| `data.local.entity` | Entidades Room y relaciones (`PracticeWithDetails`, `SessionWithPractice`) | `spec-schema-base-datos-room.md` |
| `data.local.dao` | `PracticeDao`, `PracticeSessionDao`, `MeritAndCalmDao`, `MindfulAppTargetDao` | `spec-schema-base-datos-room.md` |
| `data.local` | `ManiDatabase`, `TypeConverters` | `spec-schema-base-datos-room.md` |
| `data.catalog` | DTOs, `CatalogMapper`, `CatalogSeeder` | `spec-data-catalogo-json.md` |
| `digitalwellbeing` | Accessibility, evaluador, cooldown | `spec-architecture-bienestar-digital-freno-impulso.md` |
| `auto` | Android Auto: filtro, controlador, pantallas | `spec-architecture-android-auto.md` |
| `ui` | `ManiViewModel`, `toPracticeRunSpec()` | `spec-design-ui-navegacion.md` |
| `ui.home` | `RefugeHomeScreen` | `spec-design-ui-navegacion.md`, `spec-design-semillas-merito.md` |
| `ui.practice` | `PracticeRunnerScreen` | `spec-design-ui-navegacion.md` |
| `ui.wellbeing` | `ImpulsePauseScreen`, `ImpulsePauseActivity`, `MindfulAppTargetsScreen` | `spec-architecture-bienestar-digital-freno-impulso.md` |
| `ui.refuge` | `RefugeAndParamitaScreen` (Tres Joyas) | `spec-design-tres-joyas-paramita.md` |
| `ui.navigation` | `ManiNavGraph`, `ManiRoutes`, `FaqScreen` | `spec-design-ui-navegacion.md` |
| `ui.theme` | `ManiTheme`, `ManiTypography` | `spec-design-ui-navegacion.md` |

## 6. Glosario

| Término | Significado en este proyecto |
|---------|------------------------------|
| **Tres Joyas** | Buddha, Dharma y Sangha. En el código: `BUDDHA`, `DHARMA`, `SANGHA`. |
| **Refugio** | Orientar la mente hacia las Tres Joyas al iniciar la práctica. |
| **Bodhicitta** | Motivación altruista de practicar para beneficio de todos los seres. |
| **Maitri** | Amor bondadoso (metta en pali). |
| **Mérito** | Bien cultivado por la práctica o la acción virtuosa. No se acumula como trofeo: se **dedica**. |
| **Dedicación del mérito** | Texto de cierre (`merit_dedication`) que orienta lo cultivado al bienestar de todos los seres. |
| **Paramitas** | Perfecciones (generosidad, ética, paciencia, esfuerzo, concentración, sabiduría). |
| **Semilla** | Hito cotidiano no competitivo que reemplaza a "logros" o "trofeos". |
| **Práctica** | Ítem del catálogo: `PRAYER`, `MANTRA`, `GUIDED_MEDITATION` o `IMPULSE_PAUSE`. |
| **Segmento** | Fase ordenada de una práctica, con texto, pausa y repeticiones. |
| **Vuelta** | Una repetición de mantra. |
| **Pausa de impulso** | Flujo breve de calma que aparece al abrir una app sensible o desde el botón de freno. |
| **App objetivo** | App que el usuario marcó como sensible (`mindful_app_targets`). |
| **Cooldown** | Minutos durante los cuales no se vuelve a mostrar la pausa para la misma app. |
| **TTS** | Text-To-Speech, síntesis de voz del sistema. |
| **DHU** | Desktop Head Unit, emulador de Android Auto. |

## 7. Estado actual

- Diseño completo en `chatIdeacion.txt` (chat de ideación con Gemini). El índice de qué hay en cada rango de líneas está en `docs/indice-chat-ideacion.md`.
- Catálogo semilla extraído a `app/src/main/assets/catalog_v1.json` (`schema_version: 2`, 8 prácticas). **Necesita revisión editorial** antes de publicar (ver `spec-process-etica-contenido-faq.md`).
- F1 lista: el esqueleto Gradle compila (`assembleDebug`, `assembleRelease`, tests y lint en verde) y abre una pantalla de bienvenida con `ManiTheme`. Sigue la F2 (datos) de `PLAN.md`.

## 8. Mapa de documentos

| Archivo | Para qué |
|---------|----------|
| `PROJECT.md` | Visión, límites, stack y arquitectura (este archivo). |
| `PLAN.md` | Fases, hitos y backlog de tareas con IDs. |
| `AGENTS.md` / `CLAUDE.md` | Reglas de trabajo para asistentes de IA. |
| `spec/` | Especificaciones por módulo (requisitos, contratos y criterios de aceptación). |
| `docs/adr/` | Decisiones de arquitectura registradas. |
| `docs/indice-chat-ideacion.md` | Dónde encontrar en el chat original cada pieza de código de referencia. |
| `docs/preguntas-abiertas.md` | Dudas que tiene que resolver una persona (Vicente o Paramita). |
| `docs/revision-contenido.md` | Registro de aprobación editorial de cada texto del catálogo. |
| `docs/creditos-audio.md` | Origen y licencia de cada archivo de audio. |
| `README.md` | Presentación breve y cómo compilar. |
| `CHANGELOG.md` | Historial de cambios. |
