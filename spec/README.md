# Índice de especificaciones

Cada spec define **requisitos, contratos y criterios de aceptación** de un módulo. Una tarea de `PLAN.md` está "hecha" solo si cumple los criterios de su spec.

Convención de nombres: `spec-[schema|data|architecture|design|process|infrastructure]-<tema>.md`.
Prefijos de requisitos: `REQ` funcional · `ETH` ético · `SEC` seguridad y privacidad · `CON` restricción · `GUD` guía · `PAT` patrón · `AC` criterio de aceptación.

| Spec | Módulo | Fases |
|------|--------|-------|
| [spec-infrastructure-build-manifest.md](spec-infrastructure-build-manifest.md) | Gradle, Manifest, Application, MainActivity | F1 |
| [spec-schema-base-datos-room.md](spec-schema-base-datos-room.md) | Esquema SQLite, entidades y DAOs | F2 |
| [spec-data-catalogo-json.md](spec-data-catalogo-json.md) | Formato `catalog_v1.json`, mapper y seeder | F2 |
| [spec-architecture-motor-practica-tts.md](spec-architecture-motor-practica-tts.md) | `PracticeRunnerEngine`, TTS y campana | F3 |
| [spec-architecture-servicio-reproduccion.md](spec-architecture-servicio-reproduccion.md) | `PracticePlaybackService` | F4 |
| [spec-design-ui-navegacion.md](spec-design-ui-navegacion.md) | Pantallas, rutas, ViewModel y tema | F5 |
| [spec-design-semillas-merito.md](spec-design-semillas-merito.md) | Sistema de semillas y dedicación del mérito | F6 |
| [spec-design-tres-joyas-paramita.md](spec-design-tres-joyas-paramita.md) | Buddha, Dharma, Sangha y enlaces a paramita.org | F6 |
| [spec-process-etica-contenido-faq.md](spec-process-etica-contenido-faq.md) | Límites éticos, revisión de contenido y FAQ | Transversal |
| [spec-architecture-bienestar-digital-freno-impulso.md](spec-architecture-bienestar-digital-freno-impulso.md) | Pausa de impulso y AccessibilityService | F7 |
| [spec-architecture-android-auto.md](spec-architecture-android-auto.md) | Android Auto (solo oraciones y mantras) | F8 |

**Precedencia ante conflictos:** `spec-process-etica-contenido-faq.md` > `PROJECT.md` > spec del módulo > código del chat.
