---
title: Formato del catálogo JSON, mapper y siembra
version: 1.0
date_created: 2026-11-24
owner: Vicente
tags: [data, catalogo, json, seed]
---

# Introducción

El catálogo de prácticas viaja en `app/src/main/assets/catalog_v1.json`. Esta spec define su formato, las reglas de validación, el mapeo a Room (`CatalogMapper`) y la siembra en el arranque (`CatalogSeeder`).

## 1. Propósito y alcance

Paquete `data.catalog`. Referencia en el chat: formato v1 en las líneas ~129-303, mapper y seeder en ~3201-3475, catálogo v2 en ~5216-5568.

## 2. Definiciones

- **schema_version:** versión del **contenido** del catálogo (entero creciente). No es la versión de la DB de Room.
- **Bundle:** el objeto JSON raíz.

## 3. Requisitos

- **REQ-001:** El parseo usa `kotlinx.serialization` con `Json { ignoreUnknownKeys = true }`.
- **REQ-002:** `CatalogSeeder.seedIfNeeded(forceReload = false): Result<Int>` se ejecuta en `Dispatchers.IO`, nunca en el hilo principal. Devuelve la cantidad de prácticas sembradas (0 si no hacía falta).
- **REQ-003:** Se siembra si (a) la tabla `practices` está vacía, o (b) `bundle.schema_version > prefs[seeded_schema_version]`, o (c) `forceReload`.
- **REQ-004:** SharedPreferences `paramita_catalog_prefs`, clave `seeded_schema_version`. Se escribe **solo** después de un `replaceCatalogBundle` exitoso.
- **REQ-005:** Se conservan los favoritos: antes de reemplazar, se leen con `practiceDao.getFavoriteIds()` y se pasan al mapper.
- **REQ-006:** La normalización del mapper acepta alias tolerantes:
  - tipo: `ORACION`/`ORACIÓN` → `PRAYER`; `MEDITATION`/`MEDITACION`/`MEDITACIÓN` → `GUIDED_MEDITATION`; `PAUSE` → `IMPULSE_PAUSE`.
  - fase: `BREATH` → `POSTURE_BREATH`.
  - joya: `DARMA` → `DHARMA`.
- **REQ-007 (cambio respecto del chat):** Ante un **valor desconocido** de tipo o fase, el mapper **rechaza la práctica** y registra un log de advertencia. El borrador del chat convertía a `PRAYER` o `CORE` en silencio, lo que podría mostrar una meditación en Android Auto. Las joyas desconocidas se descartan.
- **REQ-008:** Validaciones por práctica (si falla una, se rechaza esa práctica y no todo el bundle):
  - `id` no vacío, único, con patrón `^prac_[a-z0-9_]+$`.
  - Al menos 1 segmento, con `order` únicos y > 0.
  - `merit_dedication` no vacío (toda práctica cierra con dedicación).
  - Si `type == MANTRA`, entonces `mantra_config != null` y `target_repetitions` está en `preset_counts`, o al menos es > 0.
  - `paramita_ref.url_path` empieza con `/`.
- **REQ-009:** Si el JSON está mal formado, `seedIfNeeded` devuelve `Result.failure`, no toca la DB y la app sigue con el catálogo anterior.
- **CON-001:** Los textos litúrgicos del JSON se copian **literalmente** (ETH-020). El mapper solo hace `trim()`.
- **CON-002:** `generated_at` es informativo; no se usa para lógica.
- **GUD-001:** Cada cambio de contenido sube `schema_version` y deja una entrada en `CHANGELOG.md` y en `docs/revision-contenido.md`.

## 4. Contrato JSON

```jsonc
{
  "schema_version": 2,                 // int, obligatorio
  "source": "paramita.org",            // string
  "generated_at": "2026-11-19",        // ISO date, informativo
  "practices": [
    {
      "id": "prac_mantra_corazon",     // obligatorio, único
      "type": "MANTRA",                // PRAYER | MANTRA | GUIDED_MEDITATION | IMPULSE_PAUSE
      "title": "string",
      "subtitle": "string | null",
      "tradition": "string",
      "jewel_focus": ["BUDDHA" | "DHARMA" | "SANGHA"],
      "tags": ["string"],              // snake_case
      "estimated_minutes": 5,
      "audio_asset_uri": "asset:///audio/x.mp3 | null",
      "tts_enabled": true,
      "default_pace_wpm": 110,
      "segments": [
        {
          "order": 1,
          "phase": "MOTIVATION | POSTURE_BREATH | CORE | CONTEMPLATION | PAUSE | DEDICATION",
          "heading": "string",
          "text": "string",            // texto litúrgico o guía
          "transliteration": "string | null",
          "meaning": "string | null",
          "pause_after_sec": 2,
          "repeat_count": 1
        }
      ],
      "mantra_config": {               // obligatorio si type == MANTRA, si no null
        "target_repetitions": 21,
        "preset_counts": [3, 7, 21, 108],
        "recitation_modes": ["SPOKEN", "WHISPER", "MENTAL"],
        "bell_every_n": 7,
        "pause_between_reps_ms": 900
      },
      "merit_dedication": "string",    // obligatorio
      "paramita_ref": { "section": "string", "topic": "string", "url_path": "/medita-con-nosotros/" }
    }
  ]
}
```

### Mapeo DTO → entidad

| JSON | Entidad.campo |
|------|---------------|
| `id` | `PracticeEntity.id` |
| `paramita_ref.section/topic/url_path` | `paramitaSection/paramitaTopic/paramitaUrlPath` |
| `segments[i]` | `PracticeSegmentEntity(id = "{id}_seg_{order}")` |
| `mantra_config.preset_counts` | `MantraConfigEntity.presetCountsJson` (JSON string) |
| `tags[]`, `jewel_focus[]` | `PracticeTagEntity`, `PracticeJewelFocusEntity` |
| (calculado) | `updatedAtEpochMs = now`, `isFavorite = id in existingFavorites` |

### Contenido actual (schema 2)

| id | type | segmentos |
|----|------|-----------|
| `prac_refugio_tres_joyas` | PRAYER | 1 |
| `prac_mantra_corazon` | MANTRA | 1 |
| `prac_med_amor_bondadoso` | GUIDED_MEDITATION | 3 |
| `prac_37_practicas_refugio_y_impermanencia` | GUIDED_MEDITATION | 3 |
| `prac_37_practicas_generosidad_y_paciencia` | GUIDED_MEDITATION | 2 |
| `prac_dedicatoria_merito_y_sangha` | PRAYER | 1 |
| `prac_pausa_impulso_compra` | IMPULSE_PAUSE | 2 |
| `prac_calma_digital_noche` | GUIDED_MEDITATION | 2 |

⚠️ El contenido fue generado por una IA en el chat. Antes de publicarlo hay que contrastarlo con traducciones autorizadas por Paramita (pregunta abierta Q-01). En particular: la traducción de las 37 Prácticas y el texto del refugio.

⚠️ `prac_mantra_corazon` referencia `asset:///audio/mantra_prajnaparamita_sample.mp3`, que no existe. Si falta el archivo, el motor tiene que caer a TTS sin error (ver la spec del motor).

## 5. Criterios de aceptación

- **AC-001:** Dado el primer arranque, cuando termina `seedIfNeeded`, entonces hay 8 prácticas en Room y `seeded_schema_version == 2`.
- **AC-002:** Dado un segundo arranque con la misma versión, entonces `seedIfNeeded` devuelve 0 y no escribe en la DB.
- **AC-003:** Dado un favorito marcado y un JSON con `schema_version: 3`, cuando se siembra, entonces el favorito se conserva.
- **AC-004:** Dado un JSON con una práctica de `type: "FOO"`, entonces esa práctica se rechaza y las demás se siembran.
- **AC-005:** Dado un JSON corrupto, entonces `Result.isFailure` y la DB queda intacta.
- **AC-006:** Test JVM: el `catalog_v1.json` del repo pasa todas las validaciones de REQ-008.

## 6. Estrategia de pruebas

- Unit JVM: `CatalogMapper` con fixtures en `src/test/resources/`.
- Unit JVM: validador del asset real (lee `src/main/assets/catalog_v1.json`).
- Instrumentado: `CatalogSeeder` con DB en memoria y SharedPreferences de prueba.

## 7. Justificación

Un catálogo versionado en assets permite trabajar offline y actualizar contenido sin migrar la DB. Rechazar valores desconocidos en lugar de adivinarlos evita clasificar mal prácticas con implicancias de seguridad (Android Auto).

## 8. Dependencias

- **PLT-001:** `kotlinx-serialization-json` 1.8.x y el plugin de serialización.
- **DAT-001:** Contenido de paramita.org, revisado por una persona.

## 9. Casos borde

- `repeat_count: 0` → se normaliza a 1.
- `pause_after_sec` negativo → 0.
- `bell_every_n: 0` → sin campana intermedia.
- `tags` con mayúsculas → se guardan en minúscula.

## 10. Validación

AC-001 a AC-006 en verde.

## 11. Relacionadas

`spec-schema-base-datos-room.md`, `spec-process-etica-contenido-faq.md`, `spec-architecture-android-auto.md`.
