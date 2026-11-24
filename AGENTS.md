# AGENTS.md — Reglas para asistentes de IA

Este archivo vale para cualquier asistente (Claude, Codex, Gemini, Copilot, etc.) que trabaje en este repositorio. Leelo completo antes de tocar algo.

## 1. Orden de lectura obligatorio

1. `PROJECT.md`: visión, pilares, límites éticos y arquitectura.
2. `PLAN.md`: buscá la tarea (`T-xxx`) que vas a hacer y su spec.
3. La spec de esa tarea, en `spec/` (índice en `spec/README.md`).
4. `spec/spec-process-etica-contenido-faq.md`: **siempre**, es transversal.
5. Si necesitás código de referencia: `docs/indice-chat-ideacion.md` → el rango de líneas de `chatIdeacion.txt`.

**Precedencia ante conflictos:** spec de ética > `PROJECT.md` > spec del módulo > ADR > código del chat.

## 2. Idioma y tono

- Documentación, commits, comentarios y textos de UI **en español**. Identificadores de código en inglés (como en el chat).
- UI con voseo rioplatense, cálida y sin urgencia ("Si querés…", "Podés…").
- Los textos visibles van en `res/values/strings.xml`, nunca hardcodeados en Composables.

## 3. Límites éticos (no negociables)

- ❌ Nada que sugiera iluminación, nirvana, budeidad, "niveles espirituales" ni purificación garantizada.
- ❌ Nada de rachas que se pierden, puntos, rankings, insignias ni mensajes de culpa.
- ❌ No modificar textos litúrgicos del catálogo (`segments[].text`, `transliteration`, `merit_dedication`) sin aprobación editorial registrada en `docs/revision-contenido.md`.
- ❌ No inventar horarios, maestros, cursos, URLs ni atribuciones. URLs permitidas:
  - `https://www.paramita.org/`
  - `https://www.paramita.org/medita-con-nosotros/`
  - `https://cursos.paramita.org/`
- ❌ Android Auto: **solo** `PRAYER` y `MANTRA`. Nunca meditaciones guiadas ni pausas de impulso, ni textos largos en pantalla.
- ✅ Toda práctica y todo hito terminan invitando a **dedicar el mérito**.
- ✅ La pausa de impulso siempre permite "Continuar con consciencia" sin fricción.
- ✅ Nada sale del dispositivo. Sin analíticas de terceros.

Si una tarea choca con alguno de estos puntos, **frená y preguntá** en lugar de buscarle la vuelta.

## 4. Sobre el código del chat (`chatIdeacion.txt`)

- Es un **borrador** generado por otra IA: puede no compilar, usar APIs viejas o tener bugs.
- Cada spec indica en qué se aparta del chat (marcas "corrige el chat", "NUEVO", "mejora sobre el chat"). **La spec manda.**
- Hay dos versiones del servicio de reproducción. La canónica es la de `spec-architecture-servicio-reproduccion.md` §4.1.
- No copies bloques grandes sin entenderlos. Verificá las versiones de las librerías contra las estables actuales.

## 5. Flujo de trabajo por tarea

1. Marcá la tarea como `[~]` en `PLAN.md`.
2. Implementá cumpliendo los REQ/CON/SEC/ETH de la spec.
3. Escribí los tests que piden los criterios de aceptación (AC) de la spec.
4. Corré `./gradlew testDebugUnitTest lint` (y `assembleDebug` si tocaste el build o el manifest).
5. Marcá la tarea como `[x]` **solo si** pasan los AC. Si quedó algo pendiente, dejala en `[~]` con una nota o creá una tarea nueva.
6. Agregá una entrada en `CHANGELOG.md` (sección `[Sin publicar]`).
7. Si tomaste una decisión estructural, creá un ADR en `docs/adr/` (numeración correlativa, plantilla en `docs/adr/README.md`).
8. Si surgió una duda que tiene que resolver una persona, agregala a `docs/preguntas-abiertas.md` con un ID `Q-xx` y **no la asumas resuelta**.

## 6. Cambios en specs

- Si la implementación demuestra que una spec está mal, **actualizá la spec** (subí `version`, agregá `last_updated`) en el mismo cambio y explicalo en el CHANGELOG.
- No borres requisitos en silencio. Tachalos (`~~REQ-00X~~`) con el motivo.

## 7. Convenciones de código

- Kotlin, paquete raíz `ar.param.advisor` (ver la tabla de paquetes en `PROJECT.md`).
- Coroutines + `StateFlow`. Nada de `GlobalScope` ni `runBlocking` en código de producción.
- Room: consultas reactivas con `Flow`, `@Transaction` en las relaciones.
- Inyección manual por `ManiApplication` (sin Hilt en v1, ver ADR-0002).
- Tests: unit JVM en `src/test`, instrumentados en `src/androidTest`.
- Commits: `tipo(ámbito): descripción` en español. Ejemplo: `feat(motor): bucle de mantra con campana cada N`. Referenciá la tarea: `(T-303)`.

## 8. Qué NO hacer

- No agregar permisos que no estén en el manifest de `spec-infrastructure-build-manifest.md` §4.1 sin un ADR.
- No agregar dependencias de red, analíticas ni SDKs de terceros sin un ADR.
- No cambiar `fallbackToDestructiveMigration` en release (borraría el historial del usuario).
- No hacer scraping de paramita.org.
- No borrar `chatIdeacion.txt` (es la fuente del diseño).
