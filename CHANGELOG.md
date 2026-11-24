# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado semántico.
Los cambios de contenido del catálogo indican el `schema_version`.

## [Sin publicar]

### Agregado (F1 · esqueleto Android)
- Proyecto Gradle de un módulo: wrapper Gradle 9.9.2, `gradle/libs.versions.toml`, `settings.gradle.kts`, `app/build.gradle.kts`, `proguard-rules.pro` (reglas de `kotlinx.serialization`).
- Versiones elegidas (últimas estables al 2026-11-24): AGP 9.5.2, Kotlin 2.4.21, KSP 2.3.14, Compose BOM 2026.11.01, core-ktx 1.19.1, activity-compose 1.13.0, lifecycle 2.11.1, Navigation 2.10.2, Room 2.8.5, coroutines 1.11.0, serialization-json 1.13.0, JUnit 4.13.2, Turbine 1.2.1, Robolectric 4.18.1.
- `AndroidManifest.xml` base con solo los 3 permisos de la spec; `MainActivity` con bienvenida y sin pedir permisos al abrir.
- `ManiTheme` / `ManiTypography` con la paleta de paramita.org; test `ThemeContrastTest` (WCAG AA en claro y oscuro).
- Íconos vectoriales originales (loto, play, pausa, stop) y launcher adaptativo (rueda de oración dorada sobre azul Paramita, con variante monocroma).
- CI: `.github/workflows/android.yml` (`assembleDebug`, `testDebugUnitTest`, `lintDebug`, sube reportes y la APK).
- ADR-0007: identidad Mani, paquete y base de build.
- Repositorio git en https://github.com/vmdanguise/paramita, con `.gitattributes` (LF en todo salvo `.bat`; `gradlew` ejecutable).

### Cambiado
- El proyecto pasa a llamarse **Mani** ("Mani · oraciones, mantras y pausa consciente").
- Paquete `com.paramita.advisor` → `ar.param.advisor`. Prefijo de clases `Paramita*` → `Mani*` (`ManiApplication`, `ManiDatabase`, `ManiTheme`, `ManiTypography`, `ManiViewModel`, `ManiNavGraph`, `ManiRoutes`). DB `mani.db`, canal `mani_practice_playback_channel`. Specs, `PROJECT.md`, `PLAN.md` y `AGENTS.md` actualizados.
- `spec-infrastructure-build-manifest.md` v1.1: `targetSdk 36` (requisito de Play), `compileSdk 37.2` (lo exigen las librerías actuales) y AGP 9 sin el plugin `kotlin.android`.
- `spec-design-ui-navegacion.md` v1.1: REQ-040/041 con la paleta y tipografía de paramita.org (reemplazan pergamino/azafrán/salvia del chat); nuevo REQ-043 de iconografía original; nuevo AC-007.

### Agregado (F0)
- F0: documentación de gobierno del proyecto (`PROJECT.md`, `PLAN.md`, `AGENTS.md`, `CLAUDE.md`, `README.md`, `.gitignore`).
- 11 especificaciones en `spec/`, derivadas de `chatIdeacion.txt`.
- ADRs 0001 a 0006 en `docs/adr/`.
- `docs/indice-chat-ideacion.md`, `docs/preguntas-abiertas.md`, `docs/revision-contenido.md`, `docs/creditos-audio.md`.
- Catálogo semilla `app/src/main/assets/catalog_v1.json` (**schema_version 2**, 8 prácticas, **pendiente de revisión editorial**).

### Decisiones respecto del borrador del chat
- Constantes canónicas del servicio de reproducción (se unifican la v1 y la v2).
- El mapper del catálogo rechaza tipos o fases desconocidos en lugar de convertirlos a `PRAYER`/`CORE`.
- Se definen 5 semillas (el chat calculaba 3) y se corrige la regla "practicó hoy".
- El cooldown del freno de impulso se persiste.
- La actualización del catálogo no puede borrar el historial personal (`is_archived`).
