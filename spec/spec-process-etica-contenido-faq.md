---
title: Límites éticos, revisión de contenido y FAQ
version: 1.1
last_updated: 2026-11-24
date_created: 2026-11-24
owner: Vicente
tags: [process, etica, contenido, faq]
---

# Introducción

Esta spec es **transversal y tiene la máxima precedencia**. Define qué puede decir y hacer la app y qué no, cómo se incorpora contenido al catálogo y el texto exacto de la sección de Preguntas Frecuentes.

## 1. Propósito y alcance

Se aplica a todo texto visible (UI, notificaciones, catálogo, descripciones de la tienda, `strings.xml`), a todo mecanismo de motivación (semillas, contadores) y a cualquier funcionalidad futura (por ejemplo, un asesor IA). Está dirigida a desarrolladores y asistentes de IA que generen código o contenido.

## 2. Definiciones

- **Texto litúrgico:** oración, mantra, verso o dedicatoria tomado de una fuente de la tradición.
- **Texto de app:** microcopy propio de la app (botones, ayudas, títulos de semillas).
- **Canales oficiales:** paramita.org y cursos.paramita.org.
- **Gamificación ansiosa:** mecánicas que generan presión, comparación o culpa (rachas que se pierden, puntos, rankings, niveles).

## 3. Requisitos, restricciones y guías

### Doctrina y autoridad
- **ETH-001:** Ningún texto puede afirmar ni sugerir que el uso de la app produce nirvana, iluminación, budeidad, realizaciones, purificación garantizada o "niveles espirituales".
- **ETH-002:** La app debe aclarar (FAQ y "Acerca de") que no reemplaza a un maestro, lama ni a los canales oficiales.
- **ETH-003:** Un eventual asesor IA (backlog B-03) debe limitarse a recomendar prácticas **del catálogo**, no debe responder preguntas doctrinales profundas y debe derivar a los canales oficiales. Requiere un ADR antes de implementarse.

### Motivación sana
- **ETH-010:** Prohibido: rachas que se "pierden", puntos, rankings, comparación con otros, niveles, insignias de superioridad, sonidos estridentes y colores de alarma (rojo intenso) para fallas.
- **ETH-011:** Si el usuario no practicó o cedió a un impulso, la app no muestra mensajes de culpa. El tono es de bienvenida ("La práctica se retoma desde donde estás").
- **ETH-012:** Todo hito o semilla termina en una invitación a **dedicar el mérito**.
- **ETH-013:** Los montos de compras evitadas son **opcionales**, nunca se muestran como meta ni como el dato principal.

### Contenido
- **ETH-020:** Los textos litúrgicos del catálogo no se modifican (ni redacción, ni puntuación significativa) sin aprobación editorial registrada en `docs/revision-contenido.md`.
- **ETH-021:** Toda práctica nueva requiere: fuente identificada, `paramita_ref` válido y revisión humana. Una IA **puede proponer** contenido, pero lo marca como `"_review": "PENDIENTE"` en un borrador y **no lo agrega** directamente al catálogo publicado.
- **ETH-022:** Prohibido inventar horarios de encuentros, nombres de maestros, URLs o atribuciones.
- **CON-001:** URLs permitidas (lista blanca): `https://www.paramita.org/`, `https://www.paramita.org/medita-con-nosotros/`, `https://cursos.paramita.org/`. Para agregar otra se necesita verificación manual y un registro en el ADR-0005.

### Autonomía y privacidad
- **SEC-001:** La pausa de impulso siempre ofrece "Continuar con consciencia" sin fricción extra.
- **SEC-002:** No se envían datos personales fuera del dispositivo. No hay analíticas de terceros sin un ADR y consentimiento explícito.
- **SEC-003:** El AccessibilityService es opt-in, con divulgación previa en lenguaje claro (ver `spec-architecture-bienestar-digital-freno-impulso.md`).

### Seguridad al conducir
- **SEC-010:** Con Android Auto no se ofrecen meditaciones guiadas ni pausas de impulso, y no se muestran textos largos.

### Tono
- **GUD-001:** Español rioplatense neutro y cálido. En la UI se usa voseo ("respirá", "elegí"). Los textos guía del catálogo también usan voseo (Q-03, respondida por Vicente el 2026-11-24); en los textos litúrgicos solo se adapta la forma verbal, nunca el contenido.
- **GUD-002:** Frases breves y verbos suaves ("Si querés...", "Podés..."). Nada de imperativos de urgencia.

## 4. Interfaces y contratos

### 4.1 Lista negra de términos (test automático, T-607)

Términos que **no** pueden aparecer en `strings.xml` ni en los campos `title`, `subtitle`, `heading` o `merit_dedication` del catálogo, salvo en la lista de excepciones:

```
iluminación, iluminado, nirvana, budeidad, nivel espiritual, nivel \d, racha perdida,
perdiste tu racha, puntos, ranking, ganaste, desbloqueaste, logro desbloqueado,
garantizado, purificación garantizada, eres un bodhisattva, sos un bodhisattva
```

Excepciones permitidas: texto litúrgico en `segments[].text` (por ejemplo, "que alcance el despertar en beneficio de todos los seres" es una aspiración, no una promesa) y las respuestas de la FAQ que **niegan** esos conceptos.

### 4.2 FAQ — texto aprobado (pantalla `FaqScreen`, ruta `faq`)

1. **¿Esta app reemplaza a un maestro, un lama o los canales oficiales de Paramita?**
   No. La APK es una herramienta de apoyo cotidiano para organizar oraciones, contar repeticiones de mantras, acompañar meditaciones guiadas, fomentar el amor bondadoso y ayudarte a frenar impulsos en el teléfono. Las enseñanzas profundas, las dudas doctrinales, las transmisiones o el seguimiento espiritual corresponden a los canales oficiales de paramita.org y al acompañamiento de un maestro o guía calificado.
2. **¿Por qué no hay "logros" de nivel espiritual o despertar?**
   Porque convertir la práctica budista en medallas de superioridad o etapas de "iluminación" alimenta el mismo apego y orgullo que la práctica busca soltar. Los hitos de la app son solo recordatorios de hábito saludable, amor bondadoso y generosidad, siempre orientados a la dedicatoria del mérito.
3. **¿Cómo ayuda realmente a dejar de comprar por impulso o calmar el uso del celular?**
   No actúa como un bloqueo punitivo, sino como una pausa lúcida: te da unos segundos para respirar, notar qué emoción hay detrás del impulso (cansancio, ansiedad, aburrimiento, búsqueda de consuelo rápido), cultivar maitri y elegir conscientemente si querés soltar la pantalla o continuar con claridad.
4. **¿Funciona sin conexión a internet?**
   Sí. El catálogo, la base local, el contador de repeticiones, la voz en español y la reproducción en segundo plano funcionan sin conexión, y los enlaces a paramita.org solo se abren cuando vos decidís visitarlos.
5. **¿Qué pasa si un día me distraigo, compro algo impulsivamente o no medito?**
   Nada se rompe ni se penaliza. La práctica se retoma desde donde estás, con amabilidad y sin culpa: observar el tropiezo con lucidez ya es parte del aprendizaje.

Preguntas candidatas (requieren aprobación antes de sumarse): "¿Qué permisos usa la app y por qué?", "¿Puedo usarla en el auto?" (respuesta: solo oraciones y mantras, nunca meditaciones).

## 5. Criterios de aceptación

- **AC-001:** Dado el APK, cuando se ejecuta el test de lista negra (§4.1), entonces no hay coincidencias fuera de las excepciones.
- **AC-002:** Dada la pantalla FAQ, cuando se abre, entonces muestra las 5 preguntas con el texto de §4.2.
- **AC-003:** Dado cualquier enlace en la app, cuando se inspecciona el código, entonces su URL pertenece a la lista blanca CON-001.
- **AC-004:** Dado un día sin actividad, cuando el usuario abre Inicio, entonces no ve ningún mensaje de pérdida, culpa ni contador reiniciado.
- **AC-005:** Dada una práctica completada, cuando termina, entonces se ofrece la dedicación del mérito antes de volver.

## 6. Estrategia de pruebas

- Test unitario JVM que parsea `res/values/strings.xml` y `assets/catalog_v1.json` y aplica las regex de §4.1.
- Test unitario que extrae URLs (`https?://[^\s"]+`) de `src/main` y las valida contra la lista blanca.
- Revisión manual con el checklist §10 antes de cada release.

## 7. Justificación

En el budismo, el mérito no es un trofeo sino algo que se dedica. Una gamificación competitiva reforzaría el apego al yo, que es justo lo que la práctica busca soltar. La autoridad doctrinal corresponde al linaje y a sus maestros. Inventar horarios o URLs genera desinformación sobre una comunidad real.

## 8. Dependencias

- **EXT-001:** paramita.org (solo enlaces salientes; sin scraping).
- **COM-001:** Política de Google Play sobre AccessibilityService (divulgación prominente).

## 9. Ejemplos

| ❌ No | ✅ Sí |
|-------|-------|
| "¡Nivel 3 de Bodhisattva desbloqueado!" | "Hoy sembraste una Semilla de Refugio. ¿Querés dedicar el mérito?" |
| "Perdiste tu racha de 14 días 😢" | "Bienvenido de nuevo. La práctica se retoma desde donde estás." |
| "Ahorraste $45.000 este mes" (como titular) | "Pausas conscientes esta semana: 4" (monto opcional y secundario) |
| "Encuentro de la Sangha: martes 20 h" (inventado) | "Ver encuentros en paramita.org →" |

## 10. Checklist ético de release

- [ ] Test de lista negra en verde.
- [ ] Test de URLs en verde.
- [ ] FAQ igual a §4.2.
- [ ] Catálogo con revisión editorial registrada en `docs/revision-contenido.md`.
- [ ] Divulgación de Accessibility revisada.
- [ ] Android Auto sin meditaciones (test AC de la spec de Auto).
- [ ] Notificaciones sin sonido ni vibración repetitiva.

## 11. Relacionadas

- `spec-design-semillas-merito.md`, `spec-design-tres-joyas-paramita.md`, `spec-architecture-bienestar-digital-freno-impulso.md`, `spec-architecture-android-auto.md`.
