# Pruebas y rendimiento de Nelo TV 0.1.0

## Alcance

No se dispone de un Skyworth TV Stick, un SoC Amlogic ni un mando físico. No se afirma compatibilidad comprobada con Netflix, Prime Video o Spotify reales. El APK abre las actividades exportadas detectadas, sin integraciones privadas ni deep links asumidos.

## Verificación de compilación

- JDK 21, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21.
- `compileSdk=35`, `targetSdk=35`, `minSdk=26`.
- `assembleDebug`, `lintDebug`, `assembleDebugAndroidTest`: completados.
- Manifiesto inspeccionado con `aapt`: HOME, LAUNCHER y LEANBACK_LAUNCHER; ningún permiso solicitado.
- Firma de depuración verificada con `apksigner`; clave privada excluida del repositorio.
- Licencias incluidas en el código fuente y en `assets/OPEN_SOURCE_LICENSES.txt` del APK.
- AndroidX y JUnit se usan exclusivamente en el APK de pruebas, no en el launcher.

El informe de lint conserva advertencias sobre cadenas en español sin recursos de traducción, orientación fija de TV, banner vectorial, atributo de retroceso para API 33+ y reglas de respaldo de Android posteriores a 11. No hay errores de lint. Son asuntos pendientes de portabilidad/localización, no prueba de ausencia de defectos.

## Pruebas instrumentadas

Los resultados y observaciones del emulador se incorporan al completar la ejecución. Se utiliza una imagen Android 11/API 30 Google APIs x86_64 con perfil de pantalla TV de 1280 × 720, D-pad y renderizado SwiftShader; no es una imagen de firmware Skyworth ni una certificación Android TV. Sin KVM, la CPU se emula por software.

Cobertura automatizada prevista en `LauncherSmokeTest`:

1. Resolución HOME, descubrimiento de apps exportadas, una entrada por paquete y exclusión del propio launcher.
2. Persistencia de favoritas y orden al recrear el almacén de preferencias.
3. Importación de imagen 2560 × 1440 con salida acotada, lectura posterior y conservación del fondo ante archivo inválido.
4. Renderizado del catálogo, eventos D-pad, menú de aplicación, cambio de favorita, ajustes y apertura/regreso desde Ajustes Android mediante una celda real del launcher.

Los archivos de evidencia indicarán cuáles se ejecutaron y su resultado; no se consideran aprobadas solo por compilar.

## Protocolo para el Skyworth

Conserva FLauncher y el launcher de fábrica. Empieza abriendo Nelo explícitamente sin cambiar HOME. Registra firmware, resolución/Hz, tamaño del catálogo y dimensiones del fondo.

```sh
./scripts/measure.sh resultados-skyworth
```

El script no cambia HOME ni modifica otros paquetes. Reinicia únicamente el proceso de Nelo para cinco lanzamientos, guarda `am start -W`, deja dos segundos entre muestras, captura PSS con `dumpsys meminfo` y solicita una sesión manual de navegación antes de extraer `gfxinfo framestats`. No borra logcat global.

- **Inicio**: reportar mediana y rango de `TotalTime`/`WaitTime`, distinguir proceso frío de caché de disco fría. El log `NeloStartup catalog_ready_ms` mide desde `onCreate` hasta el catálogo disponible, no incluye necesariamente la carga de todos los iconos. `reportFullyDrawn` se emite al programar el siguiente frame del catálogo y tampoco certifica la presentación GPU completa.
- **RAM**: comparar Total PSS y heap Java/nativo después de 30 segundos estable, después de navegar y tras 20 ciclos de apertura/regreso. El LRU de 3 MiB limita sus entradas, no todo el uso de memoria; las celdas visibles también retienen bitmaps.
- **Fluidez**: inspeccionar frames durante navegación y transiciones; a 60 Hz el presupuesto es 16,67 ms. Reportar p50/p95/p99 y proporción de frames lentos si la captura ofrece datos suficientes. No inferir FPS a partir de periodos de UI inactiva. `gfxinfo` es parcial y no mide la reproducción dentro de Netflix u otras apps.
- **Estabilidad**: 20 arranques HOME, 20 ciclos de ida/vuelta a Netflix/Prime/Spotify, mando con pulsación larga, reinicio de TV, ausencia de selector de documentos, cancelación/importación inválida, instalación/actualización/desinstalación de una app y fondo local después de reiniciar.
- **Presión de memoria**: probar con aproximadamente 1 GB real, otras apps residentes y fondos variados. Android puede matar el launcher; comprobar recuperación por HOME y conservación de preferencias. No se garantiza residencia permanente.

Criterio inicial: cero cierres inesperados o pérdida de navegación en esos ciclos; 60 FPS cuando el hardware lo permita. No se fija una cifra de PSS aprobada sin una línea base real del firmware. Comparar con FLauncher en condiciones idénticas antes de afirmar ahorro.

## Limitaciones y mejoras pendientes

- Validación física, rendimiento representativo, HDMI/overscan y comportamiento HOME del fabricante pendientes.
- Solo favoritas y orden manual; sin categorías con nombre, ocultación, widgets, recomendaciones ni integración de contenido.
- Algunas apps móviles requieren ratón; no se transforma su interfaz.
- Selección de fondo depende de un proveedor de documentos; se documenta alternativa ADB para debug.
- Sin corrección EXIF, selector interno de archivos ni copia de seguridad/exportación de preferencias.
- Primera versión de interfaz en español; falta extraer cadenas y ampliar accesibilidad/idiomas.
- Caché de iconos en RAM, no en disco; no se usa blur dinámico ni fondos animados.
- No hay benchmark comparativo de FLauncher/LTvLauncher ni validación de Android 12–15.
- Firma debug temporal: falta definir clave estable y proceso de releases de producción.
