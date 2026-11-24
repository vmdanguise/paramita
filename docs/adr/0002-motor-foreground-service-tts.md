# ADR-0002: Motor en un Foreground Service con TTS del sistema; sin Hilt en v1

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-architecture-motor-practica-tts.md`, `spec-architecture-servicio-reproduccion.md`, `spec-design-ui-navegacion.md`

## Contexto

El requisito central es que la app "se prenda y recite sola" durante minutos, con la pantalla bloqueada, y que se pueda controlar desde la notificación y desde Android Auto.

## Opciones consideradas

**Ejecución:**
1. Motor dentro del ViewModel: muere al salir de la app. ❌
2. WorkManager: no sirve para audio continuo e interactivo. ❌
3. **Foreground Service `mediaPlayback`, dueño único del motor**, con estado expuesto por `StateFlow` estático. ✅

**Voz:**
1. Audios grabados: mejor calidad, pero requieren grabaciones con licencia para cada texto.
2. **TTS del sistema (es-AR → es-ES → default):** cubre todo el catálogo desde el día 1. ✅ Los audios grabados quedan como mejora (B-02, `audio_asset_uri`).

**Inyección de dependencias:**
1. Hilt: estándar, pero suma complejidad de build y de anotaciones para una app chica.
2. **Manual vía `ManiApplication`** (DB y DAOs `lazy`) + factory de ViewModel. ✅ Se revisa si la app crece.

## Decisión

Foreground Service + TTS del sistema + DI manual. El motor depende de la interfaz `SpeechPort` para poder testearlo sin Android.

## Consecuencias

- ✅ Funciona en segundo plano y desde Android Auto con la misma instancia.
- ✅ Motor testeable con tiempo virtual (`kotlinx-coroutines-test`).
- ⚠️ La calidad de voz depende del motor TTS instalado. Hay que avisar si falta el español.
- ⚠️ Estado estático (`companion object`): aceptable con una sola instancia de servicio. Hay que limpiarlo en `onDestroy`.
