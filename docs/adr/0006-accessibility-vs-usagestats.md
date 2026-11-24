# ADR-0006: Detección de apps — AccessibilityService opt-in vs. UsageStats

- **Estado:** Aceptado con riesgo (revisar antes de publicar en Play, T-903)
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-architecture-bienestar-digital-freno-impulso.md`, `spec-infrastructure-build-manifest.md`

## Contexto

Para ofrecer la pausa de impulso cuando el usuario abre una app de compras o de scroll, hay que detectar la app en primer plano.

## Opciones consideradas

1. **AccessibilityService** (`typeWindowStateChanged`, solo `packageName`, sin leer contenido).
   - ✅ Detección inmediata y confiable.
   - ⚠️ Google Play restringe el uso de la API de Accesibilidad para fines que no son de accesibilidad: exige una divulgación prominente, un formulario de declaración y puede rechazar la app.
2. **UsageStatsManager** (`PACKAGE_USAGE_STATS`) + sondeo periódico.
   - ✅ Permiso menos sensible (igual es especial y requiere ir a Ajustes).
   - ⚠️ Con latencia (sondeo cada N segundos), consume más batería y no es tiempo real.
3. **Solo el freno manual** (botón en la app, Quick Settings Tile B-04).
   - ✅ Sin permisos especiales.
   - ⚠️ Depende de que el usuario se acuerde en el momento del impulso.

## Decisión

- La **v1** implementa la opción 1, opt-in y con divulgación, **más** la opción 3, que funciona siempre sin permisos.
- La app debe ser 100 % usable sin el servicio de accesibilidad.
- Si Play rechaza la declaración, se cambia a la opción 2 (backlog B-07) sin tocar la UI de la pausa: el `MindfulAppTriggerEvaluator` es independiente de la fuente del evento.

## Consecuencias

- ⚠️ Riesgo de revisión en Play. Hay que preparar el texto de declaración y un video de demostración.
- ✅ Diseño desacoplado: cambiar la fuente de detección no afecta la pausa ni el cooldown.
