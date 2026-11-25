# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Versionado semántico.
Los cambios de contenido del catálogo indican el `schema_version`.

## [Sin publicar]

## [0.4.0] - 2026-11-24 · prácticas propias y voz

Todo lo anterior se mantiene: las 8 prácticas del catálogo, el susurro de inicio, el freno al impulso y los enlaces.

### Agregado
- **Mis prácticas** (ADR-0008): crear, editar y borrar oraciones y mantras propios.
  - Campos: título, tipo, texto que Mani lee en voz alta, repeticiones o vueltas, pausa, campana cada N vueltas, velocidad y dedicación del mérito (con un texto por defecto).
  - Borrar una práctica la archiva: su historial se conserva.
- **Mantras grabados con tu voz** (ADR-0009): se graban dentro de la app o se elige un audio del teléfono. Suenan en lugar de la voz sintética, con las vueltas, pausas y velocidad elegidas. El permiso de micrófono se pide solo al tocar "Grabar".
- **Imagen por práctica**, elegida de la galería con el selector de fotos (sin permisos). Se copia a la carpeta privada de la app.
- **Voz de las prácticas:** voz en español instalada (solo voces sin internet), tono, volumen y ecualización de 3 bandas (graves, medios y agudos), con estilos Natural, Cálida, Clara, Suave y Profunda. Botón "Probar la voz". Los cambios valen desde la próxima frase, aunque haya una práctica sonando.
- **Velocidad:** se guarda en cada práctica propia y se puede cambiar antes de empezar o mientras suena (0,5× a 1,6×). En las grabaciones cambia la velocidad sin cambiar el tono.
- **Símbolos auspiciosos** (los 8 Ashtamangala, dibujados de cero, ADR-0010):
  - uno por día en Inicio;
  - rotando durante la práctica si no tiene imagen propia;
  - en la dedicación del mérito;
  - en la pausa de impulso, donde reemplazan al loto que respira.
- **Meditación guiada:** tarjeta en Inicio que avisa que estamos trabajando en esto.
- Se puede elegir que una práctica propia suene en susurro al abrir la app. Si se borra, vuelve el refugio.

### Cambiado
- Nuevo ícono propio, "la joya en el loto", sobre fondo teal profundo. Sigue sin el logo de Paramita (Q-01).
- Base de datos v2: columna `practices.image_uri`, con migración automática que no borra nada.
- Corregido: actualizar el catálogo ya no archiva las prácticas propias.

## [0.3.1] - 2026-11-24

### Cambiado
- Susurro rehecho con LPC: en cada fragmento de 25 ms se estima la forma de la boca y se la excita con soplido en lugar de voz. Suena a susurro real, sin el ruido granulado de la versión 0.3.0.
- "No volver a preguntar durante" ahora incluye 1 minuto.

## [0.3.0] - 2026-11-24 — freno al impulso

### Agregado (F7 — freno al impulso)
- Pausa lúcida (`ImpulsePauseActivity`): respiración de 6 s, "¿Qué está buscando tu mente?", monto opcional si es una compra por impulso, reflexión breve con práctica sugerida y dedicación del mérito. "Continuar con consciencia" está visible en todos los pasos y no pide confirmación.
- Pantalla "Pausa al abrir apps": elegís qué apps instaladas (Mercado Libre, Facebook, etc., que aparecen primero si están instaladas) te ofrecen la pausa y cada cuánto. Aviso claro antes de ir a Ajustes → Accesibilidad.
- `MindfulAppAccessibilityService` opt-in: solo mira el nombre del paquete en primer plano, sin leer contenido. Cooldown por app que sobrevive a reinicios; no se dispara en modo auto.
- Cada pausa se registra en el dispositivo (`merit_and_calm_events`, con `outcome`).
- Spec de bienestar digital v1.1.

### Cambiado
- El modo susurro ahora suena como un susurro de verdad: la frase del TTS se procesa en el teléfono para quitarle la vibración de la voz (envolvente espectral con fase aleatoria, sin graves). Si falla, vuelve a la voz suave anterior.

## [0.2.0] - 2026-11-24 · primera versión usable

### Agregado (F2 · datos)
- Room `ManiDatabase` v1 (`mani.db`, 8 tablas) con el esquema exportado a `app/schemas/`. 4 DAOs. `replaceCatalogBundle` solo hace upsert y archiva lo que falta (nunca borra historial).
- `CatalogMapper`: alias de tipos y fases, rechaza tipos o fases desconocidos, valida según REQ-008 y conserva los favoritos. `CatalogSeeder` lee el catálogo y lo siembra fuera del hilo principal; si el JSON está roto, conserva la base anterior.
- `ManiApplication` (inyección manual) y `ManiSettings`.

### Agregado (F3 · motor)
- `PracticeRunnerEngine`: segmentos, repeticiones y pausas contemplativas; bucle de mantra con campana cada N, sin recitar una vuelta de más (REQ-011); pausa y reanuda en la misma etapa (REQ-022); modos voz clara, susurro y mental.
- `SpeechAndBellController`: TTS del sistema (es-AR → es-ES), espera real a que termine cada frase y una campana sintetizada en código (sin audio de terceros, Q-04 sigue abierta).

### Agregado (F4 · servicio)
- `PracticePlaybackService` (foreground `mediaPlayback`): sigue sonando con la pantalla bloqueada. Notificación silenciosa con Pausar/Reanudar/Detener, foco de audio (se pausa ante una llamada) y registro de sesiones (no se guardan las de menos de 5 s).
- El permiso de notificaciones se pide la primera vez que tocás "Comenzar", nunca al abrir.

### Agregado (F5 · UI)
- Inicio: práctica del día (rotación diaria, la propuesta de Q-06), continuar la sesión en curso, tarjeta de pausa de impulso, momentos de la semana sin rachas ni culpa, enlaces de la lista blanca de paramita.org.
- Pantalla de práctica: texto, transliteración, significado, progreso, modo de recitación, vueltas (3/7/21/108), mantener la pantalla encendida y la dedicación del mérito al terminar o detener.
- Preguntas frecuentes con el texto aprobado de la spec de ética, más avisos de independencia, revisión editorial y privacidad.
- **Al abrir la app se recita en susurro la oración de refugio** (pedido de Vicente). Se puede desactivar desde Inicio. No interrumpe una práctica que ya está sonando.
- 20 tests nuevos: motor (AC-001..AC-005), mapper, el catálogo real y la DB más la siembra con Robolectric (SDK 35).

### Cambiado
- Catálogo `schema_version` 3: los textos guía pasan a voseo (Q-03). No hay cambios de contenido.
- `spec-schema-base-datos-room.md` v1.1: columna `merit_and_calm_events.outcome` (T-209). ADR-0003 aceptado.
- `spec-process-etica-contenido-faq.md` v1.1: GUD-001 con voseo también en el catálogo.
- Q-08: el historial entra en el backup de Google (`allowBackup="true"`).

## [0.1.0] - 2026-11-24

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
