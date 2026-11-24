# ADR-0001: Offline-first con Room y catálogo JSON en assets

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-schema-base-datos-room.md`, `spec-data-catalogo-json.md`

## Contexto

La app se usa en momentos de práctica (de noche, en el auto, sin señal) y maneja datos personales sensibles (hábitos, emociones, compras evitadas). Paramita no expone una API de contenido.

## Opciones consideradas

1. **Backend propio + sync:** contenido actualizable al instante, pero requiere servidor, cuentas y privacidad de datos en la nube.
2. **Scraping de paramita.org:** frágil, sin permiso explícito y con riesgo de mostrar contenido fuera de contexto.
3. **Catálogo JSON empaquetado en assets + Room local:** funciona offline, privado por diseño, y el contenido se actualiza con cada versión de la app.

## Decisión

Opción 3. El catálogo vive en `assets/catalog_v1.json`, versionado con `schema_version`, y se siembra en Room al arrancar. Los datos personales solo existen en el dispositivo.

## Consecuencias

- ✅ Sin permiso `INTERNET` en v1. Privacidad fuerte.
- ✅ Revisión editorial por PR: cada cambio de contenido es un diff visible.
- ⚠️ Actualizar contenido requiere publicar una versión nueva (aceptable; sync remoto en el backlog B-06, que requerirá un ADR propio).
- ⚠️ El seeder debe preservar favoritos e historial (ver ADR-0003).
