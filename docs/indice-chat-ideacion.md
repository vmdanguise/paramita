# Índice de `chatIdeacion.txt`

Chat de ideación con Gemini (7801 líneas). Contiene la visión del producto y **código borrador**: puede no compilar y la spec correspondiente siempre manda (ver `AGENTS.md` §4).

Los rangos son aproximados (±5 líneas). Para leer un rango en PowerShell:

```powershell
(Get-Content chatIdeacion.txt -Encoding UTF8)[(424-1)..(804-1)]
```

| Líneas | Contenido | Spec que lo reemplaza |
|--------|-----------|-----------------------|
| 42–115 | Visión del producto, pilares, Paramita y Tres Joyas | `PROJECT.md` |
| 133–303 | Catálogo JSON **v1** (4 prácticas; obsoleto) | `spec-data-catalogo-json.md` |
| 304–423 | Esquema SQL de referencia | `spec-schema-base-datos-room.md` |
| 424–804 | Motor: modelos, `SpeechAndBellController`, `PracticeRunnerEngine` | `spec-architecture-motor-practica-tts.md` |
| 805–1224 | Servicio de reproducción **v1** (`ACTION_SET_MODE`, `PracticeSessionCache`; obsoleto) | `spec-architecture-servicio-reproduccion.md` |
| 1225–1746 | `PracticeRunnerScreen` + `rememberPracticeServiceState` | `spec-design-ui-navegacion.md` |
| 1747–1875 | Concepto del flujo de freno al impulso | `spec-architecture-bienestar-digital-freno-impulso.md` |
| 1876–2445 | Entidades Room, relaciones y DAOs | `spec-schema-base-datos-room.md` |
| 2446–2798 | `ImpulsePauseScreen` | `spec-architecture-bienestar-digital-freno-impulso.md` |
| 2799–3200 | `RefugeAndParamitaScreen`, `JewelTab`, `ParamitaTopicCard` | `spec-design-tres-joyas-paramita.md` |
| 3201–3478 | DTOs del catálogo, `CatalogMapper`, `CatalogSeeder` | `spec-data-catalogo-json.md` |
| 3479–3535 | Sistema de semillas + FAQ (5 preguntas, ~3510–3523) | `spec-design-semillas-merito.md`, `spec-process-etica-contenido-faq.md` |
| 3536–3926 | `RefugeHomeScreen` + `RefugeHomeUiState` | `spec-design-ui-navegacion.md` |
| 3927–4074 | `ImpulsePauseActivity` + `MindfulAppTriggerEvaluator` | `spec-architecture-bienestar-digital-freno-impulso.md` |
| 4075–4525 | ViewModel y NavGraph **v1** (obsoleto) | `spec-design-ui-navegacion.md` |
| 4526–4655 | `MindfulAppAccessibilityService` + `ImpulseCooldownTracker` | `spec-architecture-bienestar-digital-freno-impulso.md` |
| 4656–5215 | `MindfulAppTargetsScreen` | `spec-architecture-bienestar-digital-freno-impulso.md` |
| 5216–5569 | Catálogo JSON **v2** (schema 2, 8 prácticas) → ya extraído a `app/src/main/assets/catalog_v1.json` | `spec-data-catalogo-json.md` |
| 5570–6074 | ViewModel y NavGraph **v2** (con semillas y FAQ) | `spec-design-ui-navegacion.md`, `spec-design-semillas-merito.md` |
| 6075–6290 | Gradle, `ParamitaApplication`, Manifest | `spec-infrastructure-build-manifest.md` |
| 6291–6545 | `ParamitaTheme`, tipografía, `MainActivity` | `spec-design-ui-navegacion.md`, `spec-infrastructure-build-manifest.md` |
| 6546–6753 | Campana con `SoundPool` + respaldo `ToneGenerator` | `spec-architecture-motor-practica-tts.md` |
| 6755–6793 | Android Auto: criterio (solo oraciones y mantras) | `spec-architecture-android-auto.md` |
| 6794–6915 | `DrivingRecitationCatalogMapper` | `spec-architecture-android-auto.md` |
| 6916–7104 | `DrivingSelectionScreen`, `DrivingPlaybackScreen` | `spec-architecture-android-auto.md` |
| 7105–7212 | Manifest con Android Auto, `automotive_app_desc.xml` | `spec-architecture-android-auto.md`, `spec-infrastructure-build-manifest.md` |
| 7214–7457 | `ServiceBackedAutoRecitationController`, `DrivingCarAppService` | `spec-architecture-android-auto.md` |
| 7458–7700 | Servicio de reproducción **v2** con `sessionState` (base canónica) | `spec-architecture-servicio-reproduccion.md` |
| 7702–7738 | `PracticeDao.getPracticeByIdOnce` | `spec-schema-base-datos-room.md` |
| 7741–7783 | Checklist final antes de compilar | `PLAN.md` (F1) |

## Bugs conocidos del borrador (ya contemplados en las specs)

| Bug | Dónde | Spec |
|-----|-------|------|
| `scope.isActive` en lugar de `coroutineContext.isActive` | Motor | Motor CON-001 |
| Carrera al inicializar `TextToSpeech` | Motor | Motor CON-003 |
| Mantra recitado una vez de más (segmento + bucle) | Motor | Motor REQ-011 |
| `resume()` reinicia desde el segmento | Motor | Motor REQ-022 |
| `persistSession` es un stub | Servicio v2 | Servicio REQ-007 |
| Constantes distintas entre las versiones v1 y v2 del servicio | Servicio | Servicio §4.1 |
| Cooldown solo en memoria | AccessibilityService | Bienestar REQ-023 |
| Tipos desconocidos → `PRAYER` en silencio | Mapper | Catálogo REQ-007 |
| Solo 3 semillas y "practicó hoy" dependiente de `GENEROSITY_MERIT` | ViewModel v2 | Semillas REQ-001, CON-001 |
| `replaceCatalogBundle` no borra `mantra_configs` huérfanos | DAO | Esquema CON-003 |
| Permiso de notificaciones pedido al abrir la app | `MainActivity` | Build REQ-009 |
