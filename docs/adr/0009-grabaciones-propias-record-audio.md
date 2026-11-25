# ADR-0009: Mantras grabados por el usuario y permiso `RECORD_AUDIO`

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-infrastructure-build-manifest.md` §4.1, `spec-architecture-motor-practicas.md` (REQ-036, B-02)

## Contexto

- Vicente propuso poder sumar mantras grabados con la propia voz, que suenen en lugar de la voz sintética.
- La columna `practices.audio_asset_uri` ya existía para audio (B-02), pero ningún código la usaba.
- Grabar con el micrófono necesita `RECORD_AUDIO`, que no estaba en el manifest de §4.1.

## Opciones consideradas

1. Solo importar audios ya grabados con otra app (sin permiso nuevo).
2. Grabar dentro de Mani con `MediaRecorder` (pide `RECORD_AUDIO`), y además permitir importar.

## Decisión

- **Opción 2.** Se agrega `android.permission.RECORD_AUDIO` y `uses-feature android.hardware.microphone required=false`, para que la app se pueda instalar en equipos sin micrófono.
- El permiso se pide **solo al tocar "Grabar"**, nunca al abrir la app. Si se niega, se puede importar un audio del teléfono con `GetContent`, que no pide permisos.
- La grabación es AAC en `.m4a`, en `filesDir/practice_recordings/`. Lo importado se copia a la misma carpeta, hasta 30 MB.
- Nada sale del teléfono. Los archivos que se reemplazan o se descartan se borran, y solo se borra lo que está dentro de las carpetas de la app.
- En `PracticeRunSpec`, la grabación queda asignada al último segmento `CORE`. El motor pasa `audioPath` a `SpeechPort.speakAndAwait`.
- `SpeechAndBellController` reproduce el archivo con `MediaPlayer`:
  - La velocidad elegida se aplica con `PlaybackParams`, sin cambiar el tono.
  - El volumen sale de los ajustes de voz.
  - Si el archivo falta o falla, usa el TTS con el texto.
- Los `asset:///` del catálogo se ignoran por ahora. No hay audios licenciados (Q-04).

## Consecuencias

- Play Console va a pedir declarar el uso del micrófono: "grabar mantras propios, sin subirlos a ningún lado".
- `allowBackup="true"` incluye `filesDir`, así que las grabaciones viajan en el backup de Google del usuario, como el resto de sus datos (Q-08).
- Con una grabación, el modo "Mental" sigue siendo silencio, y el modo susurro baja el volumen de la grabación a la mitad.
