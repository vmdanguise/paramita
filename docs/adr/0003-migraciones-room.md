# ADR-0003: Política de migraciones de Room y protección del historial

- **Estado:** Propuesto. Se decide en T-209, antes de la primera release.
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-schema-base-datos-room.md`, `spec-data-catalogo-json.md`, `spec-infrastructure-build-manifest.md`

## Contexto

El borrador del chat usa `fallbackToDestructiveMigration(dropAllTables = true)`, que borra **toda** la base (historial, semillas, apps objetivo) ante cualquier cambio de esquema. Además, `practice_sessions.practice_id` tiene `ON DELETE CASCADE`: si una práctica se quita del catálogo, se pierde su historial.

## Opciones consideradas

**Migraciones:**
1. Destructiva siempre. ❌ Inaceptable con datos de usuario.
2. **Destructiva solo en debug; migraciones explícitas (`Migration` o `AutoMigration`) en release**, con el esquema exportado en `app/schemas/` y tests con `MigrationTestHelper`. ✅

**Prácticas retiradas del catálogo:**
- A. **Soft delete:** columna `practices.is_archived`; `replaceCatalogBundle` solo hace upsert y archiva las que faltan.
- B. FK `ON DELETE SET NULL` con `practice_id` nullable en sesiones; se muestra "Práctica retirada" en el historial.

**Outcome de la pausa de impulso** (lo necesitan las semillas):
- C. Codificarlo en `trigger_context` (`"...|outcome=PRACTICED_MAITRI"`). Sin cambio de esquema, pero frágil.
- D. Columna `outcome TEXT` en `merit_and_calm_events`.

## Decisión (propuesta)

- Migraciones: opción 2.
- Prácticas retiradas: **A** (ya reflejada en la spec del esquema, v1 incluye `is_archived`).
- Outcome: **D**, si se hace antes de la primera release (se agrega directamente en v1, sin migración). Si no, C como paso intermedio.

Vicente o quien implemente T-209 confirma y cambia el estado a "Aceptado".

## Consecuencias

- ✅ El historial del usuario sobrevive a actualizaciones de la app y del catálogo.
- ⚠️ Cada cambio de esquema posterior a la release requiere una migración + un test.
