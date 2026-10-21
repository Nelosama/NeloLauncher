# Evaluación técnica — 20 de octubre de 2026 (America/Tegucigalpa)

La evaluación se hizo mediante repositorios y archivos reales. No se ha accedido a un Skyworth físico.

| Base | Evidencia consultada | Tecnología y licencia | Mantenimiento observado |
|---|---|---|---|
| FLauncher original | [GitLab](https://gitlab.com/flauncher/flauncher), `pubspec.yaml`, `LICENSE`, feed de commits de master | Flutter 3.7.5, Dart 2.x, canal Android; GPLv3. El pubspec incluye Firebase Analytics, Crashlytics y Remote Config. Eso demuestra dependencias, no por sí solo tráfico activo. | Último commit publicado por el feed de master: `4930cd738a5ab32967953fe8b12790bc4b452872`, 3 marzo 2023. No implica que todos los forks estén inactivos. |
| FLauncher fork | [osrosal/flauncher](https://github.com/osrosal/flauncher), commit `eeaaf69a27f2c28b9b969fc9f2e0b3c2e86c6f67` | Flutter 3.24.5, Dart >=3.4.3, GPLv3; sin Firebase en el pubspec consultado. | Último commit consultado: 26 julio 2025. |
| LTvLauncher | [leanbitlab-org/LtvLauncher](https://github.com/leanbitlab-org/LtvLauncher), `LICENSE`, `README.md`, `pubspec.yaml`, historial | Derivado del fork anterior; Flutter 3.24.5, Dart >=3.4.3, Drift/SQLite, Provider, preferencias y canal Android. GPLv3. Version declarada `2026.10.03+8112`. | HEAD `7dbb14d0e269b42d33c5ef0374ece8c3c7aedb45`: actualización automática de insignias. Separando esas actualizaciones, se observaron cambios funcionales y de estabilidad del 3 octubre 2026 (`a235465`, `d2494c4`, `b239010`). |

## Decisión

Implementación propia en Kotlin con vistas Android del framework y GridView reciclable. Sin Flutter, Compose, servicios, receptores permanentes, bases de datos, red ni SDK de seguimiento. El único runtime añadido es la biblioteca estándar Kotlin.

FLauncher/LTvLauncher proporcionarían más funciones desde el principio, especialmente categorías y configuración. Adaptarlos es razonable si se prioriza esa cobertura. Para un dispositivo de aproximadamente 1 GB y una primera versión pequeña, preferimos controlar directamente la resolución de imágenes, el caché y el trabajo en segundo plano. Esto es una decisión arquitectónica: no se ha medido un ahorro comparativo ni probado que Flutter sea incapaz de alcanzar 60 FPS.

Se utilizan APIs nativas disponibles en Android 8+; el destino principal es Android 11. `compileSdk=35` y `targetSdk=35` permiten herramientas y reglas recientes conservando compatibilidad con Android 11. El registro del receptor usa la variante explícita en API 33+. Las versiones posteriores a Android 11 necesitan su propia matriz de pruebas antes de declararlas soportadas.

## Licencias

No se copió código, diseño gráfico ni recursos de las bases evaluadas. Nelo tiene código nuevo bajo MIT; conserva `LICENSE`, `NOTICE` y la licencia Apache 2.0 del runtime Kotlin y del wrapper Gradle. Los iconos de aplicaciones se obtienen de los APK instalados: no se redistribuyen logotipos de terceros.

Si en una etapa posterior se incorpora código GPLv3 de FLauncher/LTvLauncher, habrá que revisar la licencia de la obra derivada, conservar avisos y copyright, identificar cambios y entregar el código fuente correspondiente, incluidos archivos de compilación, bajo los términos GPLv3 aplicables. No basta con citar el repositorio ni cambiar el nombre. Esta entrega evita esa incorporación.

## Límites de la investigación

Las fechas y versiones corresponden a los archivos y ramas indicados; no son una auditoría completa de incidencias, seguridad ni de todas las releases. La actividad automática de GitHub no se interpreta como mantenimiento funcional. La elección final de rendimiento debe validarse con la misma carga de aplicaciones y fondo en el Skyworth.
