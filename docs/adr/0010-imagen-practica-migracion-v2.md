# ADR-0010: Imagen por práctica, símbolos auspiciosos y base de datos v2

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Specs afectadas:** `spec-schema-base-datos-room.md`, `spec-design-ui-navegacion.md`

## Contexto

- Vicente pidió elegir una imagen de la galería para cada práctica propia, y mostrar imágenes auspiciosas en cuatro lugares:
  - en Inicio,
  - durante la práctica,
  - con la dedicación,
  - en la pausa de impulso.
- `practices` no tenía dónde guardar una imagen. Cambiar el esquema obliga a migrar, y en release la migración nunca puede ser destructiva (ADR-0003).

## Decisión

- **DB v2:** `practices.image_uri TEXT` nullable, con `AutoMigration(1 → 2)` de Room. Solo agrega una columna, así que no se pierde ningún dato.
  - El esquema queda exportado en `app/schemas/.../2.json`.
  - `UserPracticeDbTest` arma una base v1 real a partir de `1.json` y verifica que se migra.
- **Elegir la imagen:**
  - Se usa el Photo Picker (`PickVisualMedia`), que no pide permisos de almacenamiento.
  - La imagen se achica a 1024 px como máximo y se copia como JPEG a `filesDir/practice_images/`, para que no dependa de la galería.
- **Símbolos auspiciosos:**
  - Son los 8 símbolos auspiciosos (Ashtamangala), dibujados de cero como vectores (`auspicious_*.xml`). No usan imágenes de terceros.
  - Se muestran:
    - en Inicio, uno por día;
    - en la práctica, rotando si no tiene imagen propia;
    - en la dedicación;
    - en la pausa de impulso, donde reemplazan al loto que respira.
  - Cada símbolo tiene su nombre en español para TalkBack.
- **Ícono de la app:** nuevo diseño propio, "la joya en el loto", sobre un fondo teal profundo (`#2F5A70`). Sigue sin el logo de Paramita (Q-01).

## Consecuencias

- Cualquier cambio de esquema futuro debe sumar una migración 2 → 3 con su test.
- Las imágenes viajan en el backup de Google junto con los demás datos (Q-08).
