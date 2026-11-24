# ADR-0004: Android Auto — plantillas car-app vs. Media3 `MediaLibraryService`

- **Estado:** **Pendiente.** Bloquea la implementación de F8 (T-805).
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-architecture-android-auto.md`, `spec-architecture-servicio-reproduccion.md`, `spec-infrastructure-build-manifest.md`

## Contexto

Vicente pidió Android Auto "para escuchar mantras y oraciones, no meditaciones". El chat propone `androidx.car.app` (`CarAppService`, `ListTemplate` y `PaneTemplate`) con la categoría `androidx.car.app.category.MEDIA`.

Android Auto solo admite ciertas categorías de apps. Para apps de **audio**, el camino establecido es exponer una `MediaBrowserService` / `MediaLibraryService` (Media3): el auto dibuja su propia interfaz de medios y los controles del volante y el asistente de voz funcionan solos. El soporte de plantillas car-app para la categoría de medios es más reciente y tiene requisitos propios. **Antes de decidir hay que verificar la documentación vigente** de Android for Cars y las Car App Quality Guidelines.

## Opciones consideradas

1. **car-app con plantillas (propuesta del chat).**
   - ✅ Ya hay código borrador; control fino del texto mostrado.
   - ⚠️ Hay que confirmar que la categoría MEDIA con plantillas es aceptada en Play para este caso, y el nivel de API del host requerido.
   - ⚠️ Los controles del volante pueden requerir además una `MediaSession`.
2. **Media3 `MediaLibraryService` + `MediaSession`.**
   - ✅ Camino estándar para audio. Controles del volante, la notificación de medios y el asistente de voz salen gratis.
   - ✅ La `MediaSession` también mejora la experiencia en el teléfono (pantalla de bloqueo, auriculares Bluetooth).
   - ⚠️ El TTS no es un `Player` de Media3: requiere un `SimpleBasePlayer` que envuelva el motor, o un adaptador.
   - ⚠️ Hay que reescribir la parte de pantallas del chat (el filtro y el controlador se reutilizan).
3. **Ambas:** `MediaSession` siempre (para el teléfono) + car-app solo si hace falta una UI propia.

## Decisión

_Pendiente._ Recomendación preliminar: **opción 2**, envolviendo `PracticeRunnerEngine` en un `SimpleBasePlayer`. Los requisitos SEC-001 a SEC-005 de la spec de Auto (lista blanca de tipos, ritmo acotado, sin texto largo) se mantienen en cualquier opción.

## Consecuencias (si se elige la opción 2)

- Se actualiza la spec de Auto (§3 UI y §4 manifest) y la del servicio (pasa a ser también el host de `MediaSession`).
- Cambia la dependencia `androidx.car.app` por `androidx.media3:media3-session`.
- `CarConnection` (spec de bienestar, REQ-025) puede seguir usándose desde `androidx.car.app` o reemplazarse por otra detección.
