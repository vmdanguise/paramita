# ADR-0007: Identidad "Mani", paquete `ar.param.advisor` y base de build (AGP 9 / SDK 37.2)

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-infrastructure-build-manifest.md` (REQ-001/002/003), `spec-design-ui-navegacion.md` (REQ-040/041/043), `spec-architecture-servicio-reproduccion.md` (constantes), `spec-schema-base-datos-room.md` (nombre de la DB)

## Contexto

- Vicente eligió el nombre **Mani** (la rueda de oración que "recita sola") y pidió usar los colores e iconografía de paramita.org.
- El `applicationId` no se puede cambiar después de publicar en Google Play. `com.paramita.advisor` daba a entender una app oficial de Paramita, y eso no está aprobado (Q-01).
- Al crear el proyecto (2026-11-24):
  - Google Play exige `targetSdk` ≥ 36 para apps nuevas desde el 2026-08-31.
  - Las últimas estables de Compose (1.13), core (1.19), Navigation (2.10) y Lifecycle (2.11) exigen `compileSdk` ≥ 37.x.
  - AGP 9 integra Kotlin y ya no usa el plugin `org.jetbrains.kotlin.android`.

## Opciones consideradas

1. Paquete: `ar.vicente.mani`, `com.paramita.mani`, `com.paramita.advisor`, `ar.param.advisor`.
2. SDK: quedarse en 35 con librerías viejas, o `compileSdk 37.2` + `targetSdk 36` con librerías actuales.
3. Marca: usar el logo de Paramita, o paleta del sitio + iconografía original.

## Decisión

- `applicationId = namespace = "ar.param.advisor"` (elegido por Vicente). Prefijo de clases `Mani*` (`ManiApplication`, `ManiDatabase`, `ManiTheme`, `ManiViewModel`, `ManiNavGraph`, `ManiRoutes`). Base de datos `mani.db`, canal de notificación `mani_practice_playback_channel`, acciones/extras con prefijo `ar.param.advisor.`.
- `minSdk 26`, `targetSdk 36`, `compileSdk 37.2`; AGP 9.5.2, Kotlin 2.4.21, KSP 2.3.14, Gradle 9.9.2.
- Paleta tomada del kit de Elementor de paramita.org; íconos (loto, rueda de oración, controles) **dibujados de cero**. No se incluye el logo de Paramita.

## Consecuencias

- Para compilar hace falta la plataforma `android-37.2` en el SDK (documentado en `README.md`).
- Si Paramita adopta la app de forma oficial, el paquete queda como está (no es un problema publicarlo desde otra cuenta). El nombre visible y los íconos se pueden cambiar sin romper nada.
- Usar el logo o la marca "Paramita" en la tienda sigue bloqueado por Q-01.
- Subir `targetSdk` a 37 queda para cuando Play lo exija, con revisión de los cambios de comportamiento.
