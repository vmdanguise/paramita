# PLAN.md — Plan de trabajo de Mani

> Plan vivo. Las IAs y las personas marcan avances acá.
> Estados: `[ ]` pendiente · `[~]` en curso · `[x]` hecho · `[!]` bloqueado (con motivo).
> Cada tarea tiene un ID (`T-xxx`) y referencia la spec que define su "hecho". Para trabajar en una tarea, leé primero su spec.
> Código de referencia: `chatIdeacion.txt` (las líneas exactas están en `docs/indice-chat-ideacion.md`). El código del chat es un **borrador**: puede tener errores de compilación o de API, así que hay que verificarlo.

## Resumen de fases

| Fase | Objetivo | Hito verificable |
|------|----------|------------------|
| F0 | Documentación y gobierno del proyecto | Este set de archivos existe y está consistente |
| F1 ✅ | Esqueleto Android que compila | `./gradlew assembleDebug` OK y la app abre una pantalla vacía con `ManiTheme` |
| F2 | Datos: Room y catálogo | En el primer arranque se siembran las 8 prácticas en Room; hay tests de DAO y mapper |
| F3 | Motor de práctica y TTS | Un mantra de 7 vueltas corre en test con un controlador falso; suena la campana |
| F4 | Servicio en segundo plano | La práctica sigue con la pantalla bloqueada; la notificación permite pausar y detener |
| F5 | UI principal y navegación | Inicio → Práctica → Dedicación → Inicio funciona de punta a punta |
| F6 | Semillas de mérito, Tres Joyas y FAQ | Las semillas reflejan la actividad del día sin rachas; la FAQ y las Tres Joyas enlazan bien |
| F7 | Bienestar digital y freno al impulso | Abrir una app objetivo lanza la pausa, respeta el cooldown y no entra en bucle |
| F8 | Android Auto | En el DHU aparecen solo PRAYER y MANTRA; funcionan Pausar/Reanudar y Detener |
| F9 | Calidad, contenido y release | Checklist ético aprobado, revisión editorial del catálogo y APK release firmada |

---

## F0 — Documentación y gobierno

- [x] **T-001** Crear `PROJECT.md`, `PLAN.md`, `AGENTS.md`, `CLAUDE.md`, `README.md`, `.gitignore`.
- [x] **T-002** Crear las specs en `spec/` (ver índice en `spec/README.md`).
- [x] **T-003** Extraer `catalog_v1.json` (schema 2) del chat a `app/src/main/assets/`.
- [x] **T-004** ADRs iniciales en `docs/adr/`.
- [x] **T-005** Inicializar repo git, primer commit y remoto: https://github.com/vmdanguise/paramita (rama `main`, público).
- [ ] **T-006** Que Vicente responda `docs/preguntas-abiertas.md` (las de prioridad alta antes de F2).

## F1 — Esqueleto Android (spec: `spec-infrastructure-build-manifest.md`)

- [x] **T-101** Crear el proyecto Gradle (Kotlin DSL): `settings.gradle.kts`, `build.gradle.kts` raíz con plugins `apply false`, `gradle/libs.versions.toml` (version catalog), wrapper (Gradle 9.9.2).
- [x] **T-102** `app/build.gradle.kts` con Compose, Serialization, KSP y Room (`room.schemaLocation`).
- [x] **T-103** `AndroidManifest.xml` base: permisos FGS, `POST_NOTIFICATIONS`, `MainActivity`. `aapt dump permissions` verificado (AC-004).
- [x] **T-104** `ManiTheme` + `ManiTypography` con la paleta de paramita.org (spec UI REQ-040) y test de contraste AA.
- [x] **T-105** `MainActivity` con `enableEdgeToEdge` y una bienvenida serena. El permiso de notificaciones **no** se pide al abrir (AC-005); se pide al iniciar la primera práctica → queda para **T-406**.
- [x] **T-106** Recursos: `strings.xml` en español, íconos `ic_lotus_calm`, `ic_play_calm`, `ic_pause_calm`, `ic_stop_calm` (vectoriales, originales), launcher adaptativo (rueda de oración).
- [x] **T-107** CI mínima (GitHub Actions) en `.github/workflows/android.yml`: primera corrida en verde (build, tests, lint y APK).
- [x] **T-108** AC-003: `assembleRelease` firmado con la clave de debug abre bien en el celular de Vicente (release v0.1.0, 2026-11-24).

## F2 — Datos (specs: `spec-schema-base-datos-room.md`, `spec-data-catalogo-json.md`)

- [x] **T-201** Entidades Room (8 tablas). Las listas se guardan como texto JSON y se parsean en `toPracticeRunSpec()` (no hizo falta `TypeConverter`).
- [x] **T-202** Relaciones `PracticeWithDetails` (con `orderedSegments`, `mantraConfig`, `tags`, `jewels`) y `SessionWithPractice`.
- [x] **T-203** DAOs: `PracticeDao` (incluye `getPracticeByIdOnce`, `replaceCatalogBundle`), `PracticeSessionDao`, `MeritAndCalmDao`, `MindfulAppTargetDao`.
- [x] **T-204** `ManiDatabase` v1 + exportar el esquema a `app/schemas/`.
- [x] **T-205** DTOs del catálogo + `CatalogMapper` (normalización y conservación de favoritos).
- [x] **T-206** `CatalogSeeder` (`seeded_schema_version` en SharedPreferences, sin bloquear el hilo principal).
- [x] **T-207** `ManiApplication` (DB lazy, holders, siembra en `onCreate`).
- [x] **T-208** Tests: mapper (unit), DAOs (instrumentado o Robolectric), seeder (conserva favoritos al subir de versión).
- [x] **T-209** Decidir la política de migraciones antes de la primera release (ver ADR-0003): FK de sesiones (`is_archived` vs. `SET NULL`) y columna `outcome` en eventos.

## F3 — Motor (spec: `spec-architecture-motor-practica-tts.md`)

- [x] **T-301** Modelos `RunnerStatus`, `RecitationMode`, `PracticeSegmentModel`, `MantraRunConfig`, `PracticeRunSpec`, `RunnerSnapshot`.
- [x] **T-302** Interfaz `SpeechPort` + `SpeechAndBellController` real (TTS es-AR→es-ES, `speakAndAwait` suspendible). La campana se sintetiza en código con `AudioTrack` (sin audio de terceros), con respaldo `ToneGenerator`.
- [x] **T-303** `PracticeRunnerEngine`: segmentos, repeticiones, bucle de mantra, campana cada N, pausa, reanudación y stop.
- [x] **T-304** Corregir los bugs conocidos del borrador (ver spec: CON-001 `scope.isActive` vs. `coroutineContext.isActive`, CON-003 carrera en la inicialización de TTS, REQ-011 mantra recitado una vez de más, REQ-022 reanudación en la misma etapa).
- [x] **T-305** Mapper `PracticeWithDetails.toPracticeRunSpec()`.
- [x] **T-306** Tests unitarios del motor con `kotlinx-coroutines-test` y un `SpeechPort` falso.
- [~] **T-307** Mientras Q-04 siga abierta, la campana es sintetizada (propia, sin licencia de terceros). Si se elige un `bell_soft.ogg`, documentarlo en `docs/creditos-audio.md`.

## F4 — Servicio (spec: `spec-architecture-servicio-reproduccion.md`)

- [x] **T-401** `PracticePlaybackService` (FGS `mediaPlayback`) con `ACTION_*`, `EXTRA_*` y `sessionState` estático.
- [x] **T-402** Notificación serena (`setOnlyAlertOnce`), acciones Pausar/Reanudar/Detener, canal de baja importancia.
- [x] **T-403** `persistSession`: inserta en `practice_sessions` (las sesiones de menos de 5 s no se guardan) y publica `lastFinishedSessionId`.
- [x] **T-404** `rememberPracticeServiceState()` para Compose.
- [ ] **T-405** *(pendiente en el celular de Vicente)* Prueba manual: bloquear la pantalla, cambiar de app y usar las acciones de la notificación.
- [x] **T-406** Pedir `POST_NOTIFICATIONS` (API 33+) la primera vez que se inicia una práctica, nunca al abrir (spec build REQ-009).

## F5 — UI y navegación (spec: `spec-design-ui-navegacion.md`)

- [x] **T-501** `ManiViewModel` + factory.
- [~] **T-502** `ManiNavGraph` con `home`, `practice/{practiceId}` y `faq`. `impulse_pause`, `mindful_targets` y `three_jewels` llegan en F6/F7 (la pausa de impulso ya se puede hacer como práctica guiada).
- [~] **T-503** `RefugeHomeScreen`: práctica del día (rotación diaria, propuesta de Q-06), continuar sesión, tarjeta de pausa de impulso, FAQ, enlaces de la lista blanca y "empezar con el refugio en susurro". Faltan semillas y Tres Joyas (F6).
- [x] **T-504** `PracticeRunnerScreen` (texto, transliteración, significado, contador, modos, presets y dedicación).
- [~] **T-505** Accesibilidad: botones de 52 dp, roles semánticos, scroll en todas las pantallas y contraste AA testeado. Falta la revisión con TalkBack y la fuente al 200 % en un teléfono.

## F6 — Semillas, Tres Joyas y FAQ (specs: `spec-design-semillas-merito.md`, `spec-design-tres-joyas-paramita.md`, `spec-process-etica-contenido-faq.md`)

- [ ] **T-601** Calcular `DailySeedSummary` con las 5 semillas definidas (hoy el chat solo calcula 3).
- [ ] **T-602** Corregir la regla "practicó hoy" (el borrador depende de `GENEROSITY_MERIT`; ver spec).
- [ ] **T-603** Flujo "Dedicar el mérito del día".
- [ ] **T-604** `RefugeAndParamitaScreen` con `JewelTab` y `ParamitaTopicCard`, solo con URLs permitidas.
- [ ] **T-605** Botón manual "Registrar conexión y dedicar mérito" en la pestaña Sangha, que inserta `SANGHA_CONNECTION`. Abrir un enlace **no** registra nada (spec Tres Joyas, REQ-005).
- [ ] **T-606** `FaqScreen` con las 5 preguntas aprobadas (texto exacto en la spec).
- [ ] **T-607** Test de "lista negra" de palabras (iluminación, nirvana, nivel, racha perdida, etc.) sobre `strings.xml` y el catálogo.

## F7 — Bienestar digital (spec: `spec-architecture-bienestar-digital-freno-impulso.md`)

- [ ] **T-701** `ImpulseEmotion`, `PauseOutcome`, `ImpulsePauseDecision`, `ImpulseCooldownTracker`.
- [ ] **T-702** `ImpulsePauseScreen` (5 etapas, spec REQ-001) + registro en `merit_and_calm_events`.
- [ ] **T-703** `ImpulsePauseActivity` (singleTop, `excludeFromRecents`, extras de paquete).
- [ ] **T-704** `MindfulAppTriggerEvaluator` + `MindfulAppAccessibilityService` + `res/xml/mindful_accessibility_service.xml`.
- [ ] **T-705** `MindfulAppTargetsScreen` (alta, edición, cooldown, estado del servicio y botón a Ajustes).
- [ ] **T-706** Divulgación prominente antes de mandar al usuario a Ajustes de Accesibilidad (política de Google Play).
- [ ] **T-707** Persistir el cooldown entre reinicios del proceso (hoy vive en memoria; ver spec).
- [ ] **T-708** No disparar la pausa mientras se maneja (Android Auto conectado), si es detectable.

## F8 — Android Auto (spec: `spec-architecture-android-auto.md`)

- [ ] **T-801** `DrivingRecitationCatalogMapper` (solo PRAYER y MANTRA, ritmo acotado).
- [ ] **T-802** `AutoRecitationController` + `ServiceBackedAutoRecitationController`.
- [ ] **T-803** `DrivingSelectionScreen` (ListTemplate) y `DrivingPlaybackScreen` (PaneTemplate).
- [ ] **T-804** `DrivingCarAppService` + `automotive_app_desc.xml` + manifest.
- [ ] **T-805** Resolver ADR-0004 (categoría `MEDIA` con car-app vs. `MediaLibraryService` de Media3) **antes** de implementar.
- [ ] **T-806** Pruebas en el DHU con un catálogo mixto.

## F9 — Calidad y release

- [ ] **T-901** Revisión editorial del catálogo con fuentes de Paramita (ver `docs/preguntas-abiertas.md`).
- [ ] **T-902** Checklist ético completo (`spec-process-etica-contenido-faq.md` §10).
- [ ] **T-903** Política de privacidad (Accessibility y datos locales) para Play Store.
- [ ] **T-904** R8/ProGuard: reglas para `kotlinx.serialization` y Room.
- [ ] **T-905** Firma, `versionCode` y `versionName`, APK y AAB release.
- [ ] **T-906** Pruebas en dispositivo real (Android 8, 13 y 14+).

## Backlog (sin fase asignada)

- [ ] **B-01** Más contenido: más versos de las 37 Prácticas, más oraciones y mantras (previa autorización y revisión).
- [ ] **B-02** Audios grabados (`audio_asset_uri`) además del TTS.
- [ ] **B-03** Asesor IA opcional, acotado al catálogo (requiere ADR propio y spec ética).
- [ ] **B-04** Quick Settings Tile "Freno de impulso".
- [ ] **B-05** Exportar e importar el historial personal (backup local).
- [ ] **B-06** Sincronizar el catálogo remoto (versionado por `schema_version`).
- [ ] **B-07** Alternativa con `UsageStatsManager` + WorkManager para quien no quiera dar permiso de Accessibility.
