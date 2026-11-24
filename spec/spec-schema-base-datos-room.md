---
title: Esquema de base de datos local (SQLite / Room)
version: 1.1
date_created: 2026-11-24
last_updated: 2026-11-24
owner: Vicente
tags: [schema, room, sqlite, datos]
---

# Introducción

Define el esquema `ManiDatabase` v1: 8 tablas, entidades Room, relaciones y DAOs. El esquema separa el **contenido del catálogo** (reemplazable al actualizar) del **estado personal del usuario** (que nunca se pisa).

## 1. Propósito y alcance

Paquetes `data.local`, `data.local.entity`, `data.local.dao`. Referencia en el chat: SQL en las líneas ~304-422, entidades y DAOs en ~1876-2440, `getPracticeByIdOnce` en ~7702-7737.

## 2. Definiciones

- **Contenido de catálogo:** `practices` (salvo `is_favorite`), `practice_segments`, `mantra_configs`, `practice_tags`, `practice_jewel_focus`.
- **Datos personales:** `practice_sessions`, `merit_and_calm_events`, `mindful_app_targets` y la columna `practices.is_favorite`.
- **Epoch ms:** milisegundos desde 1970-01-01 UTC.

## 3. Requisitos

- **REQ-001:** Nombre del archivo: `mani.db`. Clase `ManiDatabase`, `version = 1`, `exportSchema = true` (`app/schemas/`).
- **REQ-002:** Los enums se guardan como `TEXT` con los valores exactos de §4.2 y se validan en el mapper (Room no aplica `CHECK`).
- **REQ-003:** Las listas JSON (`preset_counts_json`, `recitation_modes_json`) se guardan como `TEXT` JSON y se convierten con `TypeConverters` o en el mapper.
- **REQ-004:** `PracticeWithDetails` expone `practice`, `segments`, `mantraConfig`, `tags: List<String>`, `jewels: List<String>` y la propiedad derivada `orderedSegments` (ordenados por `segmentOrder`).
- **REQ-005:** Todas las consultas que devuelven relaciones llevan `@Transaction`.
- **REQ-006:** Los IDs de segmento son deterministas: `"{practiceId}_seg_{order}"`.
- **REQ-007:** Los IDs de sesiones y eventos son `UUID.randomUUID().toString()`.
- **CON-001:** **Ninguna actualización del catálogo puede borrar datos personales.** Hoy `practice_sessions.practice_id` tiene `ON DELETE CASCADE`: si se eliminara una práctica del catálogo se perdería el historial. Por lo tanto:
  - `replaceCatalogBundle` **no borra** filas de `practices`; solo hace upsert. Las prácticas retiradas se marcan con `is_archived = 1` (columna nueva, ver §4.1) en lugar de borrarse.
  - Alternativa aceptable: cambiar la FK de sesiones a `ON DELETE SET NULL` con `practice_id` nullable. Decidir en T-209 y registrar en el ADR-0003.
- **CON-002:** El upsert de `practices` desde el catálogo debe **conservar `is_favorite`** (el mapper recibe `existingFavorites`).
- **CON-003:** `replaceCatalogBundle` también debe borrar el `mantra_configs` de una práctica que dejó de tener `mantra_config` en el JSON (el borrador del chat no lo hace).
- **CON-004:** Durante el desarrollo se permite `fallbackToDestructiveMigration(dropAllTables = true)`. **Prohibido en release** (borraría el historial del usuario). Antes de la primera release se cambia a migraciones explícitas.
- **GUD-001:** Los contadores de UI se calculan con consultas agregadas (`COUNT`, `SUM`) que devuelven `Flow`, no cargando listas completas.

## 4. Contratos

### 4.1 Tablas (SQL de referencia)

```sql
CREATE TABLE practices (
  id TEXT PRIMARY KEY NOT NULL,
  type TEXT NOT NULL,                       -- PracticeType
  title TEXT NOT NULL,
  subtitle TEXT,
  tradition TEXT NOT NULL DEFAULT 'Budismo Tibetano · Sakya (Paramita)',
  estimated_minutes INTEGER NOT NULL DEFAULT 5,
  audio_asset_uri TEXT,
  tts_enabled INTEGER NOT NULL DEFAULT 1,
  default_pace_wpm INTEGER NOT NULL DEFAULT 120,
  merit_dedication TEXT NOT NULL,
  paramita_section TEXT,
  paramita_topic TEXT,
  paramita_url_path TEXT,
  is_favorite INTEGER NOT NULL DEFAULT 0,
  is_archived INTEGER NOT NULL DEFAULT 0,   -- NUEVO (CON-001)
  updated_at_epoch_ms INTEGER NOT NULL
);

CREATE TABLE practice_segments (
  id TEXT PRIMARY KEY NOT NULL,             -- "{practiceId}_seg_{order}"
  practice_id TEXT NOT NULL REFERENCES practices(id) ON DELETE CASCADE,
  segment_order INTEGER NOT NULL,
  phase TEXT NOT NULL,                      -- SegmentPhase
  heading TEXT NOT NULL,
  text TEXT NOT NULL,
  transliteration TEXT,
  meaning TEXT,
  pause_after_sec INTEGER NOT NULL DEFAULT 3,
  repeat_count INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_segments_practice_order ON practice_segments(practice_id, segment_order);

CREATE TABLE mantra_configs (
  practice_id TEXT PRIMARY KEY NOT NULL REFERENCES practices(id) ON DELETE CASCADE,
  target_repetitions INTEGER NOT NULL DEFAULT 21,
  preset_counts_json TEXT NOT NULL DEFAULT '[3,7,21,108]',
  recitation_modes_json TEXT NOT NULL DEFAULT '["SPOKEN","WHISPER","MENTAL"]',
  bell_every_n INTEGER NOT NULL DEFAULT 7,
  pause_between_reps_ms INTEGER NOT NULL DEFAULT 900
);

CREATE TABLE practice_tags (
  practice_id TEXT NOT NULL REFERENCES practices(id) ON DELETE CASCADE,
  tag TEXT NOT NULL,
  PRIMARY KEY (practice_id, tag)
);

CREATE TABLE practice_jewel_focus (
  practice_id TEXT NOT NULL REFERENCES practices(id) ON DELETE CASCADE,
  jewel TEXT NOT NULL,                      -- Jewel
  PRIMARY KEY (practice_id, jewel)
);

CREATE TABLE practice_sessions (
  id TEXT PRIMARY KEY NOT NULL,
  practice_id TEXT NOT NULL REFERENCES practices(id),   -- ver CON-001
  started_at_epoch_ms INTEGER NOT NULL,
  ended_at_epoch_ms INTEGER,
  completed_repetitions INTEGER NOT NULL DEFAULT 0,
  recitation_mode TEXT,                     -- SPOKEN | WHISPER | MENTAL | GUIDED
  completed_full INTEGER NOT NULL DEFAULT 0,
  merit_dedicated INTEGER NOT NULL DEFAULT 0,
  note TEXT
);
CREATE INDEX idx_sessions_started_at ON practice_sessions(started_at_epoch_ms DESC);

CREATE TABLE merit_and_calm_events (
  id TEXT PRIMARY KEY NOT NULL,
  event_type TEXT NOT NULL,                 -- MeritEventType
  title TEXT NOT NULL,
  trigger_context TEXT,
  avoided_amount_estimate REAL,             -- opcional (ETH-013)
  currency_code TEXT,                       -- ej. "ARS"
  emotion_noticed TEXT,                     -- ImpulseEmotion.name o texto libre
  dedication_note TEXT,
  outcome TEXT,                             -- PauseOutcome.name (NUEVO, ADR-0003 opción D)
  created_at_epoch_ms INTEGER NOT NULL
);
CREATE INDEX idx_merit_events_created_at ON merit_and_calm_events(created_at_epoch_ms DESC);

CREATE TABLE mindful_app_targets (
  package_name TEXT PRIMARY KEY NOT NULL,
  display_name TEXT NOT NULL,
  category TEXT NOT NULL,                   -- AppCategory
  prompt_enabled INTEGER NOT NULL DEFAULT 1,
  cooldown_minutes INTEGER NOT NULL DEFAULT 15
);
```

### 4.2 Enums

| Enum | Valores |
|------|---------|
| `PracticeType` | `PRAYER`, `MANTRA`, `GUIDED_MEDITATION`, `IMPULSE_PAUSE` |
| `SegmentPhase` | `MOTIVATION`, `POSTURE_BREATH`, `CORE`, `CONTEMPLATION`, `PAUSE`, `DEDICATION` |
| `Jewel` | `BUDDHA`, `DHARMA`, `SANGHA` |
| `RecitationMode` (sesión) | `SPOKEN`, `WHISPER`, `MENTAL`, `GUIDED` (en sesiones sin mantra) |
| `MeritEventType` | `GENEROSITY_MERIT`, `IMPULSE_PURCHASE_AVOIDED`, `DIGITAL_CALM_BREAK`, `SANGHA_CONNECTION` |
| `AppCategory` | `SHOPPING`, `SOCIAL_SCROLL`, `OTHER` |

### 4.3 DAOs (firmas obligatorias)

```kotlin
@Dao interface PracticeDao {
  @Transaction @Query("SELECT * FROM practices WHERE is_archived = 0 ORDER BY title ASC")
  fun observeAllPracticesWithDetails(): Flow<List<PracticeWithDetails>>
  @Transaction fun observePracticesByType(type: String): Flow<List<PracticeWithDetails>>
  @Transaction fun observePracticeById(practiceId: String): Flow<PracticeWithDetails?>
  @Transaction suspend fun getPracticeByIdOnce(practiceId: String): PracticeWithDetails?
  @Transaction fun observePracticesByJewel(jewel: String): Flow<List<PracticeWithDetails>>
  @Transaction fun searchPractices(query: String): Flow<List<PracticeWithDetails>>
  suspend fun getFavoriteIds(): List<String>                       // NUEVO: lo usa el seeder
  suspend fun countPractices(): Int                                // NUEVO: lo usa el seeder
  suspend fun setFavorite(practiceId: String, isFavorite: Boolean)
  @Transaction suspend fun replaceCatalogBundle(
      practices: List<PracticeEntity>, segments: List<PracticeSegmentEntity>,
      mantraConfigs: List<MantraConfigEntity>, tags: List<PracticeTagEntity>,
      jewels: List<PracticeJewelFocusEntity>)
}

@Dao interface PracticeSessionDao {
  fun observeRecentSessions(limit: Int = 50): Flow<List<SessionWithPractice>>
  suspend fun getLatestSession(): PracticeSessionEntity?
  suspend fun getLatestSessionForPractice(practiceId: String): PracticeSessionEntity?  // NUEVO
  fun observeRepetitionsSince(sinceEpochMs: Long): Flow<Int>
  fun observeCompletedSessionsSince(sinceEpochMs: Long): Flow<Int>
  fun observeSessionsCountSince(sinceEpochMs: Long): Flow<Int>                        // NUEVO (semillas)
  suspend fun insertSession(session: PracticeSessionEntity)
  suspend fun updateSession(session: PracticeSessionEntity)
  suspend fun markMeritDedicated(sessionId: String)
}

@Dao interface MeritAndCalmDao {
  fun observeEvents(limit: Int = 100): Flow<List<MeritAndCalmEventEntity>>
  fun observeEventsByType(eventType: String): Flow<List<MeritAndCalmEventEntity>>
  fun observeEventCountSince(eventType: String, sinceEpochMs: Long): Flow<Int>
  fun observeEventsSince(sinceEpochMs: Long): Flow<List<MeritAndCalmEventEntity>>     // NUEVO
  fun observeAvoidedAmountSince(sinceEpochMs: Long): Flow<Double>
  suspend fun insertEvent(event: MeritAndCalmEventEntity)
}

@Dao interface MindfulAppTargetDao {
  fun observeTargets(): Flow<List<MindfulAppTargetEntity>>
  suspend fun getEnabledTargetsOnce(): List<MindfulAppTargetEntity>
  suspend fun getTargetByPackage(packageName: String): MindfulAppTargetEntity?
  suspend fun upsertTarget(target: MindfulAppTargetEntity)
  suspend fun upsertTargets(targets: List<MindfulAppTargetEntity>)
  suspend fun updateTargetRule(packageName: String, enabled: Boolean, cooldownMinutes: Int)
  suspend fun deleteTarget(packageName: String)
}
```

Los métodos marcados como `NUEVO` no están en el chat. Se agregan para cumplir los requisitos de otras specs.

## 5. Criterios de aceptación

- **AC-001:** Dada una DB con 1 favorito y 3 sesiones, cuando se ejecuta `replaceCatalogBundle` con un catálogo nuevo, entonces el favorito y las 3 sesiones siguen existiendo.
- **AC-002:** Dada una práctica que sale del JSON, cuando se re-siembra, entonces queda `is_archived = 1`, no aparece en `observeAllPracticesWithDetails()` y sus sesiones siguen existiendo.
- **AC-003:** Dado un mantra cuyo `mantra_config` pasa a `null`, cuando se re-siembra, entonces la fila de `mantra_configs` se elimina.
- **AC-004:** `getPracticeByIdOnce("prac_mantra_corazon")` devuelve 1 segmento y `mantraConfig.targetRepetitions == 21`.
- **AC-005:** `orderedSegments` siempre viene ordenado ascendente por `segmentOrder`, aunque se inserten desordenados.
- **AC-006:** El esquema exportado `app/schemas/.../1.json` se versiona en git.

## 6. Estrategia de pruebas

- Tests instrumentados con `Room.inMemoryDatabaseBuilder` (o Robolectric en JVM).
- `kotlinx-coroutines-test` + `Flow.first()` para las consultas reactivas.
- Datos de prueba: construidos en Kotlin (builders) o a partir de `catalog_v1.json`.
- `MigrationTestHelper` desde la v2 de la DB en adelante.

## 7. Justificación

Separar segmentos de prácticas permite que una oración tenga 1 bloque, una meditación 5 fases y una pausa 2 pasos con la misma estructura. Separar catálogo y datos personales permite actualizar contenido sin tocar el historial. Los campos `paramita_*` mantienen la trazabilidad con la fuente sin hacer scraping.

## 8. Dependencias

- **PLT-001:** Room 2.7.x con KSP.
- **DAT-001:** `assets/catalog_v1.json` (ver `spec-data-catalogo-json.md`).

## 9. Casos borde

- Práctica sin segmentos: válida para la DB, pero el motor la marca COMPLETED al instante. El seeder debe rechazarla (ver la spec del catálogo).
- `cooldown_minutes <= 0`: se normaliza a 1 al leerse (`coerceAtLeast(1)`).
- `avoided_amount_estimate` negativo: se rechaza en la UI.

## 10. Validación

Tests AC-001 a AC-005 en verde y esquema exportado coincidente con §4.1.

## 11. Relacionadas

`spec-data-catalogo-json.md`, `spec-design-semillas-merito.md`, `docs/adr/0003-migraciones-room.md`.
