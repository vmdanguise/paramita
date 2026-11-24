---
title: Sección Tres Joyas (Buddha, Dharma, Sangha) y vínculo con paramita.org
version: 1.0
date_created: 2026-11-24
owner: Vicente
tags: [design, contenido, paramita, tres-joyas]
---

# Introducción

`RefugeAndParamitaScreen` presenta las Tres Joyas como pestañas, con una reflexión breve, tarjetas de temas que enlazan a los canales oficiales de Paramita y las prácticas locales relacionadas.

## 1. Propósito y alcance

Paquete `ui.refuge`, ruta `three_jewels?jewel={jewel}`. Referencia en el chat: líneas ~2799-3200.

## 2. Definiciones

- **JewelTab:** enum de pestañas con textos.
- **ParamitaTopicCard:** tarjeta de tema con enlace externo y una práctica local opcional.

## 3. Requisitos

- **REQ-001:** Tres pestañas (`FilterChip` o `TabRow`): Buddha, Dharma y Sangha. Por defecto, Dharma.
- **REQ-002:** Cada pestaña muestra: subtítulo, reflexión (`reflectionEs`), tarjetas de temas de esa joya y la lista de prácticas locales con `jewel_focus` que la incluya (botón "Practicar").
- **REQ-003:** Las tarjetas abren la URL en el navegador externo (o Custom Tabs) **solo al tocarlas**. Debajo del botón se ve el dominio de destino.
- **REQ-004:** Si una tarjeta tiene `linkedPracticeId`, también ofrece "Practicar en la app".
- **REQ-005:** En la pestaña Sangha hay una tarjeta "¿Participaste hoy de una meditación guiada, estudio o encuentro con la comunidad?" con el botón "Registrar conexión y dedicar mérito", que inserta `SANGHA_CONNECTION`. Es una acción **manual y voluntaria**: abrir un enlace no registra nada automáticamente.
- **REQ-006:** No se muestran horarios fijos. Los avisos de formato son genéricos ("Consultar programación y horarios vigentes en Paramita").
- **CON-001:** Todas las URLs pertenecen a la lista blanca (`spec-process-etica-contenido-faq.md` CON-001).
- **CON-002:** Los títulos de cursos o temas tienen que existir realmente en paramita.org. Los del borrador (por ejemplo, "El Buddha hoy y Libertad Interior") **deben verificarse** (Q-02). Si no se pueden verificar, se usa un título genérico ("Cursos de Paramita").
- **GUD-001:** Los textos de reflexión son de la app (no litúrgicos), pero igual se someten a revisión (Q-02).

## 4. Contratos

```kotlin
enum class JewelTab(val key: String, val titleEs: String, val subtitleEs: String, val reflectionEs: String) {
    BUDDHA("BUDDHA", "Buddha", "La cualidad despierta y el ejemplo vivo", "…"),
    DHARMA("DHARMA", "Dharma", "La comprensión de causa, efecto y camino", "…"),
    SANGHA("SANGHA", "Sangha", "Comunidad de práctica, estudio y apoyo", "…");
    companion object { fun fromKey(key: String?): JewelTab = entries.firstOrNull { it.key == key } ?: DHARMA }
}

data class ParamitaTopicCard(
    val id: String, val jewel: JewelTab, val categoryLabel: String, val title: String,
    val summary: String, val scheduleOrFormatHint: String? = null,
    val url: String, val linkedPracticeId: String? = null
)

@Composable fun RefugeAndParamitaScreen(
    initialJewel: JewelTab,
    practicesForJewel: List<PracticeWithDetails>,
    onJewelSelected: (JewelTab) -> Unit,
    onOpenPractice: (String) -> Unit,
    onRecordSanghaConnection: (MeritAndCalmEventEntity) -> Unit,
    onNavigateBack: () -> Unit
)
```

Los textos completos de `reflectionEs` y las 4 tarjetas iniciales están en el chat (líneas ~2826-2850 y ~3154-3195) y se copian a `strings.xml`.

| Tarjeta | Joya | URL | Práctica vinculada |
|---------|------|-----|--------------------|
| `topic_buddha_hoy` | BUDDHA | https://cursos.paramita.org/ | `prac_refugio_tres_joyas` |
| `topic_dharma_paramitas` | DHARMA | https://cursos.paramita.org/ | `prac_med_amor_bondadoso` |
| `topic_dharma_corazon` | DHARMA | https://www.paramita.org/medita-con-nosotros/ | `prac_mantra_corazon` |
| `topic_sangha_encuentros` | SANGHA | https://www.paramita.org/medita-con-nosotros/ | `prac_refugio_tres_joyas` |

## 5. Criterios de aceptación

- **AC-001:** `three_jewels?jewel=SANGHA` abre la pestaña Sangha. Un valor inválido o ausente abre Dharma.
- **AC-002:** En la pestaña Buddha se listan `prac_refugio_tres_joyas`, `prac_med_amor_bondadoso`, `prac_37_practicas_refugio_y_impermanencia` y `prac_dedicatoria_merito_y_sangha` (según el `jewel_focus` del catálogo v2).
- **AC-003:** Tocar "Registrar conexión" inserta 1 evento `SANGHA_CONNECTION` y muestra "Conexión con la Sangha registrada y mérito dedicado."
- **AC-004:** Sin conexión a internet, la pantalla funciona completa. Tocar un enlace muestra el error del navegador, no un crash.
- **AC-005:** El test de URLs (lista blanca) pasa.

## 6. Estrategia de pruebas

Compose UI test con la DB sembrada. Test unitario de `JewelTab.fromKey`.

## 7. Justificación

Las Tres Joyas son el eje del refugio budista. Enlazar sin hacer scraping mantiene a Paramita como fuente autorizada y evita información desactualizada.

## 8. Dependencias

- **EXT-001:** paramita.org y cursos.paramita.org (enlaces salientes).

## 9. Casos borde

- Joya sin prácticas locales: se oculta la sección "Prácticas locales" (no se muestra una lista vacía).
- No hay navegador instalado: `ActivityNotFoundException` capturada, se muestra un Snackbar "No se encontró un navegador".

## 10. Validación

AC-001 a AC-005.

## 11. Relacionadas

`spec-process-etica-contenido-faq.md`, `spec-design-semillas-merito.md`.
