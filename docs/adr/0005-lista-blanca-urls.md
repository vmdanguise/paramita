# ADR-0005: Lista blanca de URLs externas

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-process-etica-contenido-faq.md` (CON-001), `spec-design-tres-joyas-paramita.md`

## Contexto

El chat de ideación propuso enlaces y títulos de cursos que no fueron verificados. Una app que remite a una comunidad real no puede publicar URLs inventadas ni rotas.

## Decisión

La app solo puede contener estas URLs, **verificadas manualmente por Vicente**:

| URL | Uso | Verificada |
|-----|-----|------------|
| `https://www.paramita.org/` | Sitio principal | Indicada por Vicente (2026-11) |
| `https://www.paramita.org/medita-con-nosotros/` | Encuentros y meditación en comunidad | Indicada por Vicente (2026-11) |
| `https://cursos.paramita.org/` | Cursos | Indicada por Vicente (2026-11) |

Un test automático (spec de ética, §6) extrae todas las URLs de `src/main` y falla si aparece alguna fuera de esta tabla.

## Procedimiento para agregar una URL

1. Una persona la abre y confirma que existe y corresponde a Paramita.
2. Se agrega una fila a esta tabla con la fecha.
3. Se actualiza la lista del test.

Las IAs **no pueden** agregar filas por su cuenta.

## Consecuencias

- ✅ Sin enlaces rotos ni inventados.
- ⚠️ Los enlaces son genéricos (página de cursos, no de un curso puntual) hasta que se verifiquen URLs específicas.
