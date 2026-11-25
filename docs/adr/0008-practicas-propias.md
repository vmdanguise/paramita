# ADR-0008: Prácticas propias en las mismas tablas que el catálogo

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-schema-base-datos-room.md`, `spec-data-catalogo-practicas.md`, `spec-design-ui-navegacion.md`, `spec-architecture-motor-practicas.md`

## Contexto

- Vicente pidió (2026-11-24) poder sumar prácticas nuevas en modo texto que Mani lea en voz alta: oraciones y mantras, con título, texto, repeticiones, pausas y velocidad. También poder editarlas y borrarlas.
- El catálogo de 8 prácticas se queda: "no vamos a borrar funcionalidades, le agreguemos mejor".
- El motor, el servicio, Android Auto y la pausa de impulso ya saben reproducir cualquier fila de `practices` con sus segmentos.
- `replaceCatalogBundle` archivaba todas las prácticas que no estaban en el catálogo nuevo. Eso incluía las prácticas del usuario.

## Opciones consideradas

1. Tablas aparte (`user_practices`) y un motor de reproducción paralelo.
2. Las mismas tablas (`practices`, `practice_segments`, `mantra_configs`), con un prefijo de id que distinga las propias.

## Decisión

- **Opción 2.** Las prácticas propias tienen id `user_<uuid sin guiones>`. Las del catálogo siguen siendo `prac_*` (`CatalogMapper.ID_PATTERN`), así que no pueden chocar.
- Cada práctica propia tiene un solo segmento `CORE`:
  - **Oración:** `repeat_count` y `pause_after_sec` en el segmento.
  - **Mantra:** una fila en `mantra_configs` con las vueltas (`target_repetitions`), la campana cada N vueltas (`bell_every_n`) y la pausa entre vueltas (`pause_between_reps_ms`).
- La velocidad se guarda en `default_pace_wpm`.
- Si se deja vacía, la dedicación del mérito usa un texto por defecto. Toda práctica termina dedicando el mérito.
- `archiveAllExcept` ya no toca ids `user_%`. `archiveUserPractice` solo archiva ids `user_%`, así que el catálogo no se puede "borrar" desde el editor.
- Borrar una práctica propia la **archiva** (ADR-0003): sus sesiones siguen en el historial.
- Los textos del catálogo no se editan desde la app: son litúrgicos y necesitan revisión editorial.

## Consecuencias

- Las prácticas propias aparecen en "Mis prácticas" y se reproducen con el mismo motor y servicio, con notificación, susurro, pausa y reanudación.
- Por ahora no aparecen en Android Auto (F8 sigue bloqueada por Q-11). Cuando aparezcan, serán solo `PRAYER` y `MANTRA`, que son los únicos tipos que se pueden crear.
- Se puede elegir una práctica propia para el susurro de inicio (`ManiSettings.startupPracticeId`). Si se archiva, el inicio vuelve al refugio.
