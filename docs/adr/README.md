# Registro de decisiones de arquitectura (ADR)

Un ADR documenta una decisión estructural: contexto, opciones, decisión y consecuencias. No se borran: si una decisión cambia, se crea un ADR nuevo que **reemplaza** al anterior y se marca el viejo como `Reemplazado por ADR-XXXX`.

| ADR | Título | Estado |
|-----|--------|--------|
| [0001](0001-offline-first-room-catalogo-json.md) | Offline-first con Room y catálogo JSON en assets | Aceptado |
| [0002](0002-motor-foreground-service-tts.md) | Motor en un Foreground Service con TTS del sistema; sin Hilt en v1 | Aceptado |
| [0003](0003-migraciones-room.md) | Política de migraciones de Room y protección del historial | Propuesto (decidir en T-209) |
| [0004](0004-android-auto-media3-vs-carapp.md) | Android Auto: plantillas car-app vs. Media3 `MediaLibraryService` | **Pendiente** (bloquea F8) |
| [0005](0005-lista-blanca-urls.md) | Lista blanca de URLs externas | Aceptado |
| [0006](0006-accessibility-vs-usagestats.md) | Detección de apps: AccessibilityService opt-in vs. UsageStats | Aceptado con riesgo |
| [0007](0007-identidad-mani-paquete-sdk.md) | Identidad "Mani", paquete `ar.param.advisor` y base de build (AGP 9 / SDK 37.2) | Aceptado |

## Plantilla

```markdown
# ADR-XXXX: Título

- **Estado:** Propuesto | Aceptado | Rechazado | Reemplazado por ADR-YYYY
- **Fecha:** AAAA-MM-DD
- **Specs afectadas:** ...

## Contexto
## Opciones consideradas
## Decisión
## Consecuencias
```
