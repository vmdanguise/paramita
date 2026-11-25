---
title: Build (Gradle), Manifest, Application y MainActivity
version: 1.2
date_created: 2026-11-24
last_updated: 2026-11-24 (v1.2: RECORD_AUDIO para mantras propios, ADR-0009)
owner: Vicente
tags: [infrastructure, gradle, manifest, build]
---

# Introducción

Define cómo se arma y compila la APK: estructura de Gradle, dependencias, manifest unificado, `ManiApplication` y `MainActivity`.

## 1. Propósito y alcance

Proyecto Android de un solo módulo (`:app`). Referencia en el chat: líneas ~6075-6290 (gradle, Application y manifest), ~6291-6545 (tema y MainActivity) y ~7105-7210 (manifest con Auto).

## 2. Definiciones

- **Version catalog:** `gradle/libs.versions.toml`.

## 3. Requisitos

- ~~**REQ-001 (v1.0):** plugins incluyendo `org.jetbrains.kotlin.android`.~~ Motivo: AGP 9 trae Kotlin integrado y ese plugin ya no se aplica (ADR-0007).
- **REQ-001:** Kotlin DSL. Plugins: `com.android.application` (AGP 9, Kotlin integrado), `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp` (declarados en la raíz con `apply false`).
- ~~**REQ-002 (v1.0):** `com.paramita.advisor`, `targetSdk 35`, `compileSdk 35`.~~ Motivo: el paquete daba a entender una app oficial de Paramita; Play exige `targetSdk 36` desde el 2026-08-31 y las librerías actuales exigen `compileSdk` 37+ (ADR-0007).
- **REQ-002:** `namespace = applicationId = "ar.param.advisor"`, `minSdk 26`, `targetSdk 36`, `compileSdk 37.2`, Java/JVM 17.
- **REQ-003:** Versiones en `libs.versions.toml` (no hardcodeadas en `app/build.gradle.kts`). Elegidas el 2026-11-24 (últimas estables): AGP 9.5.2, Kotlin 2.4.21, KSP 2.3.14, Gradle 9.9.2, Compose BOM 2026.11.01, Room 2.8.5, Navigation 2.10.2, coroutines 1.11.0, serialization-json 1.13.0. car-app (1.7.0 en el chat) se agrega en F8. Al subir una versión, registrala en el CHANGELOG.
- **REQ-004:** KSP con el argumento `room.schemaLocation = $projectDir/schemas`.
- **REQ-005:** Release: `isMinifyEnabled = true`, `isShrinkResources = true` y `proguard-rules.pro` con las reglas de `kotlinx.serialization` (DTOs `@Serializable`).
- **REQ-006:** Dependencias de test: JUnit 4 o 5, `kotlinx-coroutines-test`, Turbine, Robolectric, `androidx.room:room-testing`, Compose UI test.
- **REQ-007:** Manifest unificado (§4.1). Los permisos son **solo** los listados.
- **REQ-008:** `ManiApplication` implementa `MindfulTargetDaoHolder` (y `ManiApplicationHolder`). Expone la DB y los DAOs como `lazy` y lanza `catalogSeeder.seedIfNeeded()` en un `applicationScope` (`SupervisorJob + Dispatchers.IO`).
- **REQ-009:** `MainActivity`: `enableEdgeToEdge()`, pide `POST_NOTIFICATIONS` en API 33+ **la primera vez que el usuario inicia una práctica** (no al abrir la app; el pedido en frío del borrador es invasivo) y monta `ManiTheme { ManiNavGraph(...) }`.
- **REQ-010:** `fallbackToDestructiveMigration` solo en `debug` (ver ADR-0003).
- **CON-001:** No se agregan permisos de red salvo que haga falta. Los enlaces abren el navegador externo, así que **no** se necesita `INTERNET` en v1.
- **CON-002:** No se pide `QUERY_ALL_PACKAGES`. Se usa `<queries>` (spec de bienestar, REQ-034).
- **CON-003:** No se pide `WAKE_LOCK` salvo que las pruebas muestren cortes con la pantalla apagada (el FGS de medios no lo necesita normalmente).
- **GUD-001:** `strings.xml` en español como idioma por defecto (`values/`).

## 4. Contratos

### 4.1 AndroidManifest.xml (objetivo)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
  <!-- v1.2 (ADR-0009): solo para grabar mantras propios; se pide al tocar "Grabar". -->
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  <uses-feature android:name="android.hardware.microphone" android:required="false" />

  <queries>
    <intent>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LAUNCHER" />
    </intent>
    <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
    <intent>
      <action android:name="android.intent.action.VIEW" />
      <data android:scheme="https" />
    </intent>
  </queries>

  <application android:name=".ManiApplication" android:allowBackup="true"
      android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round"
      android:label="@string/app_name" android:supportsRtl="true"
      android:theme="@style/Theme.Mani">

    <meta-data android:name="com.google.android.gms.car.application"
               android:resource="@xml/automotive_app_desc"
               tools:ignore="MetadataTagInsideApplicationTag" />

    <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>

    <activity android:name=".ui.wellbeing.ImpulsePauseActivity" android:exported="false"
              android:launchMode="singleTop" android:excludeFromRecents="true" android:taskAffinity="" />

    <service android:name=".service.PracticePlaybackService" android:exported="false"
             android:foregroundServiceType="mediaPlayback" />

    <service android:name=".digitalwellbeing.MindfulAppAccessibilityService" android:exported="false"
             android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
      <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>
      <meta-data android:name="android.accessibilityservice"
                 android:resource="@xml/mindful_accessibility_service" />
    </service>

    <service android:name=".auto.DrivingCarAppService" android:exported="true">
      <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.MEDIA" />
      </intent-filter>
    </service>
  </application>
</manifest>
```

`allowBackup`: *(Q-08)* el historial personal se incluiría en el backup de Google. Decidir si se excluye con `dataExtractionRules`.

### 4.2 Estructura de directorios

```
app/
  build.gradle.kts
  proguard-rules.pro
  schemas/                       # esquema exportado de Room (versionado)
  src/main/
    AndroidManifest.xml
    assets/catalog_v1.json
    java/ar/param/advisor/{engine,service,data/...,digitalwellbeing,auto,ui/...}
    res/{values,drawable,raw,xml,mipmap-*}
  src/test/                      # unit JVM
  src/androidTest/               # instrumentados
gradle/libs.versions.toml
build.gradle.kts
settings.gradle.kts
```

## 5. Criterios de aceptación

- **AC-001:** `./gradlew assembleDebug` termina OK en una máquina limpia con JDK 17 y la plataforma SDK 37.2.
- **AC-002:** `./gradlew testDebugUnitTest lint` termina OK.
- **AC-003:** `./gradlew assembleRelease` (con firma de debug o una config de prueba) genera la APK y la app arranca: la ofuscación no rompe la serialización.
- **AC-004:** `aapt dump permissions` lista solo los permisos de §4.1.
- **AC-005:** La primera apertura no pide ningún permiso.

## 6. Estrategia de pruebas

CI en GitHub Actions (`.github/workflows/android.yml`): checkout, JDK 17, cache de Gradle, `assembleDebug`, `testDebugUnitTest`, `lint`. Se suben los reportes como artefactos.

## 7. Justificación

El version catalog centraliza las versiones para que las IAs no dupliquen ni mezclen versiones. Pedir permisos en contexto mejora la confianza y la tasa de aceptación.

## 8. Dependencias

- **PLT-001:** AGP 9.5 / Gradle 9.9, JDK 17, plataforma Android SDK 37.2 (Android Studio opcional).

## 9. Casos borde

- Compilar desde el celular no es posible (se aclaró en el chat). Se necesita una PC con el SDK, o la CI.

## 10. Validación

AC-001 a AC-005.

## 11. Relacionadas

Todas las demás specs.
