# Verificación de Nelo TV Launcher 0.2.0

Actualización de estabilización: [resultados y límites](STABILITY_0.2.0.md).
Los resultados siguientes corresponden a la sesión anterior del 20 de octubre,
no certifican el nuevo commit ni la firma de nuevos runners.

Fecha de trabajo: 20 de octubre de 2026, zona America/Tegucigalpa.

## Resultado y alcance

Se entrega código implementado y APK debug 0.2.0, no solo una propuesta. Compilación y lint pasan; las cuatro pruebas de integración del runtime Android en JVM pasan. Hay imágenes del código de vistas implementado revisadas visualmente, con su procedencia claramente identificada.

**No se certifica todavía ejecución completa, rendimiento ni estabilidad física en el Skyworth.** No hubo acceso al dispositivo. El usuario reportó que 0.1.0 ya se instaló y abrió en su Skyworth; ese dato es una observación del usuario, no una prueba física hecha por este agente.

## Compilación y actualización

Herramientas: JDK completo Temurin 17.0.12, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, compile/target SDK 35 y min SDK 26. Para el entorno se utilizó compilación Kotlin en proceso; el producto no necesita ese ajuste para ejecutarse.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest :fixtures:assembleDebug
```

- APK principal generado; APK de pruebas y fixture compilados por separado.
- Firma verificada con `apksigner`.
- Paquete estable `tv.nelo.launcher`; versionCode 2, versionName 0.2.0.
- Certificado SHA-256 idéntico al APK 0.1.0 publicado: `657ec9a97b2e399a172e266f5946bcce1142a1f00d7eb8fd1a307e4733b8078f`.
- Actualización compatible con `pm install -r` para esa versión firmada. No hace falta desinstalar ni limpiar datos. No se ha ejecutado la actualización en el TV físico.
- Preferencias `launcher` y claves JSON de favoritas/orden conservadas. El fondo `files/wallpaper.jpg` de 0.1.0 se reconoce como foto heredada sin moverlo o borrarlo.
- El manifiesto conserva HOME/LEANBACK/LAUNCHER. No hay INTERNET, MANAGE_EXTERNAL_STORAGE, permisos de escritura, servicios ni BOOT_COMPLETED. Únicamente se declara READ_EXTERNAL_STORAGE hasta API 32, solicitado de forma opcional por el explorador de carpetas, nunca al arrancar ni al usar la bandeja.

El informe de lint conserva 13 advertencias y **0 errores**: cadenas de interfaz pendientes de recursos de traducción, orientación fija de las tres pantallas TV, commit de preferencias intencional en trabajador, banner vectorial, configuración de respaldo en Android posterior a 11 y un atributo de retroceso para API 33+. No se desactivó la comprobación de errores para obtener este resultado.

## Pruebas ejecutadas: runtime Android API 30

Robolectric 4.14.1 ejecuta el código de vistas/framework Android y Skia nativo en JVM. No emula el kernel, el gestor de tareas completo ni la GPU del Amlogic. Los catálogos de aplicaciones son datos de pruebas, no aplicaciones comerciales instaladas.

| Prueba | Resultado y comprobaciones |
|---|---|
| `homeRendersNativeViewsAndKeepsFocusAfterResume` | Aprobada: catálogo y favoritas, tarjetas Android reales, selección y un único borde de enfoque, ruta del Intent de apertura, ocultación de una app y recuperación del catálogo, conservación de selección al pausar/reanudar, ausencia de permiso de Internet. Produce el render de inicio. |
| `prepareDoesNotApplyAndGallerySurvivesStoreRecreation` | Aprobada: PNG de 1920 × 1080 reducido a límites, preparar no aplica, reconocimiento del JPEG heredado, copia JPEG persistida al recrear el almacén, archivo inválido sin alterar el fondo actual y restauración sin borrar galería. |
| `wallpaperUiPreviewsBeforeApplyingAndFiltersDocumentStubs` | Aprobada: un DocumentsStub registrado es descartado, aparece alternativa de exploración; vista previa real con Cancelar conserva el fondo, Aplicar crea copia y la pantalla lee el nuevo fondo. Produce el render de configuración. |
| `internalInboxBrowsesWebpWithoutStoragePermission` | Aprobada: lectura de WEBP, explorador interno con lectura de almacenamiento denegada, selección que devuelve su archivo y decodificación posterior, sin depender de un proveedor del sistema. |

Se detectó durante la revisión un doble borde de enfoque por reciclado y se corrigió en `AppAdapter`; la primera prueba exige que solo una tarjeta tenga borde. Las imágenes de prueba se rasterizan antes de entregarlas al catálogo simulado para evitar una limitación de dibujos personalizados del runtime al cambiar de hilo; no cambia el código ni los iconos del producto.

El mensaje nativo de decodificador que puede aparecer en stderr corresponde al caso de archivo deliberadamente inválido. La prueba comprueba su rechazo y la conservación del fondo.

Evidencias finales: [JUnit XML](evidence/0.2.0/unit-tests.xml), [compilación](evidence/0.2.0/build-checks.txt), [lint](evidence/0.2.0/lint.xml), [firma](evidence/0.2.0/signature.txt) y [manifiesto](evidence/0.2.0/apk-badging.txt).

## Emulador e instrumentación: pendientes

Se descargó una imagen oficial **Android TV 11/API 30 x86**, con perfil 1280 × 720, D-pad y aproximadamente 1 GB configurado. El host no tiene `/dev/kvm`. Se intentó emulación por software con Emulator 37.2.12 y con 31.3.10, un núcleo y SwiftShader. El transporte ADB permaneció `offline`; la captura de display no produjo una imagen válida. Los registros de arranque están en [la evidencia del entorno](evidence/0.2.0/emulator-status.txt).

Por tanto, **no se presenta como aprobada ninguna instalación, navegación, regreso desde otro proceso, reinicio ni prueba prolongada de 0.2.0 en emulador**. Las cuatro pruebas instrumentadas de `LauncherSmokeTest` compilan y están disponibles para un entorno estable con el APK externo `fixtures/`, pero no se consideran ejecutadas. No se alteró FLauncher, el launcher predeterminado, componentes del sistema ni Google Play Services.

Las imágenes en [screenshots/](screenshots/README.md) proceden del runtime de pruebas descrito arriba. No se etiquetan como capturas del emulador o del dispositivo. [Cambios y comparación visual](DESIGN_0.2.0.md).

## Rendimiento

No hay datos representativos de PSS, inicio, latencia del mando o FPS en el TV. No se infieren de los tiempos de JUnit ni del host. 60 FPS sigue siendo un objetivo, no un resultado medido.

Límites verificables de diseño: caché de arte LRU de 3 MiB, iconos 128 × 128, banners 320 × 180, fotos como máximo 1280 × 720, máximo 24 copias optimizadas, GridView reciclable y fila de favoritas acotada. Estas cifras no son el PSS total: las vistas visibles retienen bitmaps y una vista previa puede coexistir con el fondo actual. Imágenes con alpha pueden requerir ARGB; no se promete RGB565 en todos los casos.

No se usan desenfoques dinámicos, servicios ni alarmas. El temporizador de rotación se cancela al pausar HOME. Las animaciones cancelan su predecesora y usan escala/alpha/translationZ, con 155 ms de enfoque y pulsación de 70 ms. Esas duraciones son parámetros de animación, **no mediciones de respuesta del mando**.

Para el Skyworth, `scripts/measure.sh` captura cinco inicios de proceso, PSS antes/después de navegación, `gfxinfo framestats` y logcat filtrado. No cambia HOME ni modifica otros paquetes. Mantener la misma resolución, fondo y catálogo, dejar estabilizar la memoria y comparar mediana/rango de inicio y frames activos con presupuesto 16,67 ms a 60 Hz. No deducir FPS de la UI inactiva. La latencia de extremo a extremo del mando y HDMI requiere trazas o medición física adicional.

## Matriz manual pendiente en el TV

1. Actualizar 0.1.0 mediante `push` + `pm install -r`; abrir explícitamente; conservar favorita/orden y fondo anteriores.
2. D-pad lento/rápido, mantener OK, Menú, Atrás, páginas de favoritas y desplazamiento de todas las apps.
3. Abrir Netflix, Prime Video, Spotify y volver 20 veces; comprobar selección y ausencia de bloqueos. Cuentas y DRM no se prueban con datos sintéticos.
4. Ocultar y mostrar apps, instalar/actualizar/quitar una app y regresar a Nelo; nunca tocar paquetes del sistema.
5. Probar selector real, DocumentsStub, permiso de lectura concedido/denegado y bandeja por ADB; JPG/JPEG/PNG/WEBP, archivo corrupto, fuente eliminada, foto vertical y rotación EXIF.
6. Cancelar/aplicar fotos, galería llena, eliminar una copia, fondo predeterminado y rotación con dos fotos; verificar que se pausa fuera del launcher.
7. Reiniciar TV, uso sin Internet, 30 minutos de navegación, memoria bajo presión y medición de frames a la resolución real.

## Limitaciones restantes

Validación física y pruebas instrumentadas completas pendientes; fidelidad del renderer GPU, mando, overscan y firmware no certificada. Android 12–15 no se declara probado. El explorador público respeta almacenamiento con ámbito y puede no acceder a algunas carpetas USB; la bandeja está disponible. No hay recorte manual ni exportación de galería; el recorte es central y automático. UI en español; traducciones y accesibilidad ampliada pendientes. No hay integración de contenido ni recomendaciones comerciales. Falta un proceso de firma/release de producción: se entrega APK debug con la firma original de esta sesión.
