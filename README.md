# Mani · oraciones, mantras y pausa consciente

App Android de apoyo a la práctica budista cotidiana, inspirada en [Paramita](https://www.paramita.org/) (budismo tibetano, tradición Sakya).

- 🙏 Recita sola oraciones y mantras (TTS en español, contador de vueltas, campana suave).
- 🧘 Acompaña meditaciones guiadas y el cultivo de *maitri* (amor bondadoso).
- 🌱 Registra "semillas" de mérito sin rachas ni competencia, y siempre cierra con la dedicación del mérito.
- ✋ Ofrece una pausa lúcida ante compras impulsivas y scroll sin rumbo.
- 💎 Acerca las Tres Joyas (Buddha, Dharma, Sangha) con enlaces a los canales oficiales.
- 🚗 En Android Auto: solo oraciones y mantras, nunca meditaciones.

> La app **no reemplaza** a un maestro ni a los canales oficiales de Paramita.

## Estado

Fases **F0** (documentación y specs) y **F1** (esqueleto Android que compila) completas. Ver [PLAN.md](PLAN.md).

## Documentación

| Archivo | Para qué |
|---------|----------|
| [PROJECT.md](PROJECT.md) | Visión, límites éticos, stack y arquitectura |
| [PLAN.md](PLAN.md) | Fases y tareas |
| [spec/](spec/README.md) | Especificaciones por módulo |
| [docs/adr/](docs/adr/README.md) | Decisiones de arquitectura |
| [docs/preguntas-abiertas.md](docs/preguntas-abiertas.md) | Dudas pendientes |
| [AGENTS.md](AGENTS.md) | Reglas para asistentes de IA |
| [CHANGELOG.md](CHANGELOG.md) | Historial |

## Compilar

Requisitos: JDK 17 y Android SDK con la plataforma **37.2** (`sdkmanager "platforms;android-37.2"`). Android Studio es opcional. El wrapper baja Gradle 9.9.2 solo.

```bash
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest lint
```

En Windows: `.\gradlew.bat assembleDebug`. Para instalarla en un celular con depuración USB: `adb install app/build/outputs/apk/debug/app-debug.apk`.

No se puede compilar desde el celular. Hace falta una PC o la CI de GitHub Actions.

## Licencia

Por definir (ver `docs/preguntas-abiertas.md`, Q-10). El contenido litúrgico pertenece a sus fuentes y se usa con permiso pendiente de confirmación (Q-01).
