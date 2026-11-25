# ADR-0011: Publicación en Google Play (prueba cerrada, clave de subida y política de privacidad)

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-infrastructure-build-manifest.md` (firma del release), PLAN F9 (T-903, T-905)

## Contexto

- Vicente pidió desplegar Mani en Play Store.
- Q-01 sigue abierta: Paramita todavía no autorizó el uso de sus textos.
- La cuenta de Play es personal y nueva. Google exige una prueba cerrada con 12 testers durante 14 días seguidos antes de pedir acceso a producción.
- Las apps nuevas se suben como AAB con Play App Signing. Hace falta una clave de subida propia.
- Play exige una URL pública de política de privacidad, porque la app usa el micrófono y un servicio de accesibilidad.

## Opciones consideradas

1. Publicar directamente en producción. Descartada: lo impide Q-01 y además la cuenta personal no lo permite todavía.
2. Prueba cerrada mientras se espera Q-01, que además es el paso obligatorio.
3. Una variante de build para Play sin el servicio de accesibilidad. Queda como plan B si Google rechaza la declaración.

## Decisión

- **Opción 2.** Mani se publica solo en **prueba cerrada** hasta que Q-01 tenga respuesta.
- **Clave de subida:**
  - RSA 4096, en `%USERPROFILE%\.android\mani\mani-upload.jks`, fuera del repo.
  - Gradle la lee de `keystore.properties`, que nunca se versiona.
  - Sin ese archivo (por ejemplo en CI), el release sale sin firmar, como antes.
  - Google guarda la clave de firma de la app (Play App Signing).
- **Política de privacidad:**
  - El texto está en `docs/privacidad.html` y se publica con GitHub Pages en `https://vmdanguise.github.io/paramita/privacidad.html`.
  - Esa URL solo figura en Play Console. La app sigue abriendo únicamente las URLs de ADR-0005.
- **Accesibilidad:**
  - El servicio se mantiene y se declara.
  - No se marca `isAccessibilityTool`. En su lugar se usan la divulgación destacada que ya existe y el consentimiento.
  - Si Google rechaza la declaración, se aplica la opción 3 con un ADR nuevo, sin borrar la función.
- **Seguridad de los datos:** se declara "no se recopilan datos". Todo se procesa en el teléfono, Mani no tiene permiso de internet y el backup lo hace Android con la cuenta del usuario.
- Los textos de la ficha y el paso a paso están en `docs/play-store/README.md`.

## Consecuencias

- Hay que guardar el `.jks` y su contraseña. Si se pierden, la clave de subida se puede resetear desde Play Console, pero lleva tiempo.
- Cualquier cambio en permisos o en el uso de datos obliga a actualizar primero `docs/privacidad.html` y el formulario de Seguridad de los datos.
- El paso a producción espera dos cosas: los 14 días de prueba cerrada y la respuesta a Q-01.
