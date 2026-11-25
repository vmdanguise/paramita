# Publicar Mani en Google Play

Procedimiento acordado con Vicente el 2026-11-24 (ADR-0011). Todo lo que se hace en la web de Play Console lo hace Vicente; acá están los textos listos para copiar.

**Restricción:** mientras Q-01 (autorización de Paramita para usar sus textos) siga abierta, Mani se publica **solo en prueba cerrada**. No se pasa a producción ni a prueba abierta sin la respuesta registrada en `docs/preguntas-abiertas.md`.

## 0. Lo que ya está hecho (en el repo)

- Clave de subida: `%USERPROFILE%\.android\mani\mani-upload.jks` (alias `mani-upload`, RSA 4096). La contraseña está en `keystore.properties`, en la raíz del repo, que **no** se versiona.
  - Huella SHA-256: `A8:0E:CA:4C:EE:9D:10:88:BA:15:7F:5D:D8:3C:71:1E:DB:4C:02:E2:3B:CA:C5:46:9E:67:2B:E1:64:5E:06:0B`
  - **Guardá una copia del `.jks` y de `keystore.properties` en tu gestor de contraseñas y en otro lugar seguro.** Si se pierden, Google puede resetear la clave de subida, pero tarda.
- `.\gradlew.bat bundleRelease` genera `app\build\outputs\bundle\release\app-release.aab` firmado con esa clave.
- Política de privacidad: `docs/privacidad.html`, publicada en https://vmdanguise.github.io/paramita/privacidad.html
- Gráficos: `graficos/icono-512.png` y `graficos/grafico-destacado-1024x500.png`.

## 1. Cuenta de desarrollador

1. Entrá a https://play.google.com/console y creá una cuenta **personal** (USD 25, una sola vez).
2. Completá la verificación de identidad y la verificación del teléfono Android que pide la consola.

## 2. Crear la app

- **Nombre:** `Mani · oraciones y mantras`
- **Idioma predeterminado:** Español (Latinoamérica) – es-419
- **App o juego:** App · **Gratis o pagada:** Gratis (una app gratuita no se puede pasar a pagada después)
- Aceptá las declaraciones. El paquete `ar.param.advisor` queda registrado cuando subas el primer AAB y es **permanente**.

## 3. Contenido de la app (Política → Contenido de la app)

| Sección | Respuesta |
|---|---|
| Política de privacidad | `https://vmdanguise.github.io/paramita/privacidad.html` |
| Anuncios | No contiene anuncios |
| Acceso a la app | Toda la funcionalidad está disponible sin restricciones (no hay cuentas) |
| Clasificación de contenido | Cuestionario IARC: categoría "Otro tipo de app" (no es juego, ni red social, ni noticias). Respondé **No** a violencia, sexo, lenguaje, drogas, apuestas, contenido generado por usuarios compartido e interacción entre usuarios. No comparte ubicación. No permite compras. |
| Público objetivo | **18 años o más** (evita la política de Familias). La app no está diseñada para niños. |
| App de noticias | No |
| Apps de salud | No es una app de salud ni de dispositivo médico. Si la consola lo pregunta, no marques ninguna categoría de salud. |
| Programa de apps gubernamentales | No |
| Funciones financieras | Ninguna |
| Seguridad de los datos | Ver §3.1 |
| Servicios en primer plano | Ver §3.2 |
| API de accesibilidad | Ver §3.3 |

### 3.1 Seguridad de los datos

- ¿Tu app recopila o comparte alguno de los tipos de datos del usuario obligatorios? → **No.**

Motivo (para vos, no se carga en la consola): todo se procesa y guarda solo en el teléfono (excepción "procesamiento en el dispositivo"). Mani no tiene permiso de internet. La copia de seguridad de Google la hace Android con la cuenta del usuario, no Mani.

Resultado en la ficha: "No se recopilan datos" y "No se comparten datos con terceros".

### 3.2 Servicio en primer plano: `mediaPlayback`

- **Funcionalidad:** "Reproducción de oraciones y mantras en voz alta. El usuario toca 'Empezar' en una práctica y Mani la lee con la voz del teléfono (o con la grabación del usuario), repite las vueltas y toca la campana. Sigue sonando con la pantalla apagada y se controla desde la notificación."
- **Impacto si se demora:** "La práctica no empieza a sonar cuando el usuario la pidió; tiene que volver a tocar 'Empezar'."
- **Impacto si se interrumpe:** "La recitación se corta a mitad de una oración o mantra y se pierde el conteo de repeticiones en curso."
- **Caso de uso:** Reproducción de contenido multimedia (Media playback).
- **Video:** ver §6.

### 3.3 API de accesibilidad (formulario de declaración de permisos)

Mani **no** se declara herramienta de accesibilidad (`isAccessibilityTool` no está puesto). Por eso le corresponden la divulgación destacada y el consentimiento, que ya están en la app: el aviso "Antes de ir a Ajustes" se muestra antes de abrir Ajustes de accesibilidad.

- **Funcionalidad principal que usa la API:** "Pausa consciente al abrir apps. El usuario elige en Mani qué apps (por ejemplo, tiendas o redes) le ofrecen una pausa breve antes de usarlas. El servicio solo lee el nombre del paquete de la ventana que pasa a primer plano (TYPE_WINDOW_STATE_CHANGED) y, si coincide con la lista del usuario, abre la pausa. La pausa siempre permite 'Continuar con consciencia' con un toque."
- **Qué datos usa:** "Solo el nombre del paquete de la app en primer plano. canRetrieveWindowContent=false: no lee texto, contenido de pantalla ni contraseñas. No se guarda qué apps se abren y nada sale del teléfono."
- **Por qué no alcanza otra API:** "UsageStatsManager exige un permiso especial más amplio (historial de uso de todas las apps) y consulta periódica, que gasta batería y llega tarde. El servicio de accesibilidad con un solo tipo de evento es lo mínimo para ofrecer la pausa en el momento en que se abre la app (ADR-0006)."
- **Video:** ver §6.

**Riesgo:** Google puede rechazar este uso. Si pasa, no se borra la función: se arma una variante de build para Play sin el servicio y se registra en un ADR nuevo.

### 3.4 Micrófono

No hay formulario aparte, pero si la consola o el revisor preguntan: "El micrófono se usa solo cuando el usuario toca 'Grabar' para guardar un mantra con su voz. El audio queda en la carpeta privada de la app y nunca se sube."

## 4. Ficha de la tienda (Crecimiento → Presencia en la tienda → Ficha principal)

**Nombre (≤ 30):**
```
Mani · oraciones y mantras
```

**Descripción breve (≤ 80):**
```
Oraciones y mantras que suenan solos, con pausas serenas. Funciona sin internet.
```

**Descripción completa (≤ 4000):**
```
Mani es una rueda de oración en tu teléfono: elegís una práctica, tocás "Empezar" y Mani la recita por vos, con la pantalla apagada si querés.

• Oraciones y mantras que suenan solos. Mani lee en voz alta, repite las vueltas, cuenta las repeticiones y toca una campana suave cada tantas vueltas.
• Tus propias prácticas. Escribí una oración o un mantra, elegí cuántas veces se repite, la pausa, la velocidad y una imagen de tu galería.
• Mantras con tu voz. Grabalos en la app y suenan en lugar de la voz sintética.
• La voz a tu gusto. Elegí la voz en español de tu teléfono, el tono, el volumen y la ecualización (graves, medios y agudos).
• Velocidad ajustable antes de empezar o mientras suena.
• Modos de recitación: en voz alta, en susurro o en silencio, siguiendo el conteo.
• Pausa al abrir apps (opcional). Si querés, Mani te ofrece unos segundos para respirar antes de abrir las apps que vos elijas, como tiendas o redes. Siempre podés seguir con un toque.
• Cada práctica termina con la dedicación del mérito.

Sin cuentas, sin publicidad, sin analíticas y sin internet: tus prácticas, grabaciones e historial quedan en tu teléfono.

Mani no reemplaza a un maestro ni a una comunidad de práctica. Es un proyecto independiente y no es una app oficial de ninguna organización.
```

**Gráficos:**
- Ícono: `graficos/icono-512.png`
- Gráfico destacado: `graficos/grafico-destacado-1024x500.png`
- Capturas de teléfono: **mínimo 2** (recomendado 4 a 8), entre 320 y 3840 px, proporción hasta 2:1. Sacalas de tu teléfono con la 0.4.1 instalada: Inicio, una práctica sonando, el editor de "Nueva práctica", la pantalla de Voz y la pausa al abrir apps.

**Categoría:** Estilo de vida · **Etiquetas:** meditación, espiritualidad (las que ofrezca la consola).
**Datos de contacto:** un email tuyo (obligatorio; se muestra en la ficha).

## 5. Prueba cerrada (obligatoria para cuentas personales nuevas)

1. **Pruebas → Prueba cerrada → Crear pista** (por ejemplo "Amigos").
2. **Testers:** creá una lista de emails o un Grupo de Google. Hacen falta **al menos 12 personas** con cuenta de Google y Android.
3. **Crear versión:** subí `app-release.aab`. La primera vez, la consola ofrece **Play App Signing**: aceptá "Dejar que Google administre y proteja la clave de firma de la app". Nuestra clave queda como clave de subida.
4. Notas de la versión (es-419):
   ```
   Primera versión de prueba: prácticas propias, mantras con tu voz, ajustes de voz y velocidad, símbolos auspiciosos y pausa al abrir apps.
   ```
5. Enviá a revisión. La revisión puede tardar varios días, más por el servicio de accesibilidad.
6. Cuando se apruebe, mandá a los testers el enlace de suscripción. **Los 12 tienen que quedar suscriptos 14 días seguidos.** Si alguien se sale antes, no cuenta.
7. Pasados los 14 días, en el Panel aparece **"Solicitar acceso a producción"**. **No lo pidas hasta que Q-01 esté respondida.**

## 6. Videos para las declaraciones

Grabá la pantalla del teléfono (grabador de Android) y subí los videos a YouTube como **no listados**.

1. **Reproducción en primer plano (≈ 30 s):** abrir Mani → tocar una práctica → "Empezar" → se escucha la voz → bloquear la pantalla → se sigue escuchando → mostrar la notificación con pausa/continuar.
2. **Accesibilidad (≈ 60 s):** abrir Mani → "Pausa al abrir apps" → marcar una app → tocar activar → se ve el aviso "Antes de ir a Ajustes" → "Entiendo, ir a Ajustes" → activar Mani en Accesibilidad → volver → abrir la app marcada → aparece la pausa → tocar "Continuar con consciencia" → se abre la app normalmente.

## 7. Cada actualización

1. Subí `versionCode` (siempre mayor que el anterior) y `versionName` en `app/build.gradle.kts`.
2. `.\gradlew.bat testDebugUnitTest lintDebug bundleRelease`
3. Subí el AAB a la misma pista y escribí las notas de la versión.
4. Si cambia el uso de datos o de permisos: actualizá `docs/privacidad.html` (con fecha) **antes** y revisá "Seguridad de los datos".
