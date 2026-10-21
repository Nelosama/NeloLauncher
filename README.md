# Nelo TV Launcher

Launcher nativo Kotlin para Android TV: aplicaciones locales, favoritas y fondo personal, sin anuncios, permisos de red ni telemetría. Primera versión 0.1.0, orientada a Android 11 y mandos D-pad. [Investigación y decisión técnica](docs/RESEARCH.md) · [Pruebas y límites](docs/TEST_REPORT.md).

## Compilar

Abre este directorio en Android Studio y deja que sincronice Gradle. Instala Android SDK 35 y Build Tools 34.0.0. JDK 17 o 21, Gradle wrapper 8.9, Android Gradle Plugin 8.7.3 y Kotlin 2.0.21. No se necesita Flutter.

```sh
./gradlew assembleDebug lintDebug assembleDebugAndroidTest
```

APK generado: `app/build/outputs/apk/debug/app-debug.apk`. El APK publicado en `dist/` es de depuración y está firmado con una clave temporal de desarrollo; no sirve como firma definitiva para distribución. Una recompilación en otro equipo tendrá otra firma: para actualizar sin perder preferencias, usa la misma clave local. No se incluyen claves privadas.

## Instalar por ADB sin sustituir el launcher existente

Activa las opciones de desarrollador y la depuración ADB en el TV. Autoriza tu equipo desde la pantalla del dispositivo. Si usas ADB por red, usa la dirección y puerto que muestre tu TV; no todos los firmware exponen el mismo puerto.

```sh
adb connect DIRECCION_DEL_TV:5555
adb devices
adb install -r dist/nelo-tv-0.1.0-debug.apk
adb shell am start -W -n tv.nelo.launcher/.MainActivity
```

Primero comprueba navegación, apertura y regreso desde tus aplicaciones. Mantén instalado y probado FLauncher como alternativa; esta entrega no lo instala automáticamente. No deshabilites ni desinstales el launcher de fábrica.

Para elegir el inicio, abre Nelo → Ajustes → Elegir launcher de inicio. En firmware compatible también puedes abrir el selector:

```sh
adb shell am start -a android.settings.HOME_SETTINGS
```

Si el fabricante no muestra selector pero admite el comando estándar, puedes establecerlo explícitamente después de las pruebas:

```sh
adb shell cmd package set-home-activity tv.nelo.launcher/.MainActivity
adb shell input keyevent KEYCODE_HOME
```

Algunos firmware fuerzan su launcher o capturan HOME: declarar la categoría HOME no garantiza poder reemplazarlo. No se modifica ninguna aplicación del sistema.

## Recuperación

Guarda antes el componente del launcher original y comprueba que puedes volver a abrirlo:

```sh
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm list packages
```

Para volver, usa Ajustes de Android → Aplicaciones predeterminadas → Inicio, o `cmd package set-home-activity COMPONENTE_ORIGINAL` con el componente que guardaste. Si FLauncher está instalado con el identificador habitual, puedes abrirlo sin cambiar el inicio:

```sh
adb shell monkey -p me.efesser.flauncher -c android.intent.category.LEANBACK_LAUNCHER 1
```

Comprueba el identificador real del APK de FLauncher que instales. Como último recurso, desinstalar **solo Nelo** elimina sus preferencias y su selección como HOME; el comportamiento del selector posterior depende del firmware:

```sh
adb uninstall tv.nelo.launcher
```

No se proporciona ningún comando para deshabilitar paquetes del sistema.

## Uso con mando

- Flechas: moverse entre controles y aplicaciones. OK: abrir o confirmar.
- Mantener OK o pulsar Menú sobre una aplicación: añadir/quitar favorita, mover antes/después o ver información.
- Aplicaciones / Favoritas: cambiar de vista. El orden personalizado se guarda y se comparte entre ambas vistas.
- Atrás: salir de diálogos, volver desde Favoritas o enfocar la navegación superior; no cierra HOME.
- Ajustes: fondo local, fondo original, animaciones, refresco, ajustes Android y selector de HOME.

Se detecta una entrada por paquete, priorizando LEANBACK_LAUNCHER y usando LAUNCHER como alternativa. Netflix, Prime Video y Spotify se abren si están instaladas, habilitadas y publican una actividad lanzable. No se simulan aplicaciones ni se incluye contenido de streaming; las cuentas, DRM y navegación interna pertenecen a cada app. Las aplicaciones móviles pueden necesitar ratón aunque Nelo funcione con mando.

## Fondos locales

Ajustes → Elegir fondo local utiliza el selector de documentos del sistema. Se guarda una copia reducida dentro de Nelo; no necesita permiso general de almacenamiento ni conservar acceso al documento original. La resolución máxima decodificada se limita a 1280 × 720, con muestreo por potencias de dos; ciertas imágenes pueden quedar por debajo de ese tamaño. No se corrige automáticamente la orientación EXIF en esta versión.

En TV sin selector de documentos instala un proveedor compatible o, para esta versión **debug**, prepara una imagen JPEG de hasta 1280 × 720 en tu equipo y usa:

```sh
adb shell am force-stop tv.nelo.launcher
adb shell run-as tv.nelo.launcher mkdir -p files
adb exec-in run-as tv.nelo.launcher sh -c 'cat > files/wallpaper.jpg' < fondo.jpg
adb shell am start -n tv.nelo.launcher/.MainActivity
```

La copia mediante `run-as` solo funciona con APK debuggable. `fondo.jpg` debe ser un archivo local tuyo. Los fondos se muestran con recorte central y una capa oscura estática; no se usan desenfoques dinámicos.

## Rendimiento y privacidad

GridView recicla celdas; iconos rasterizados a 128 × 128 con LRU de 3 MiB; fondo acotado; un ejecutor de trabajo serial; animación de escala de 130 ms; preferencias locales. No hay servicio residente, arranque en BOOT_COMPLETED, acceso a Internet, estadísticas remotas ni permisos solicitados. El receptor de paquetes solo está registrado mientras la actividad está visible. Se refresca también al volver de otras aplicaciones y se libera el fondo cuando Android notifica que la UI está oculta.

60 FPS es un objetivo pendiente de validar en el Skyworth, no una garantía. [Protocolo de medición reproducible](docs/TEST_REPORT.md).

Código propio: MIT. Conserva `LICENSE`, `NOTICE` y `licenses/` al redistribuir. Los recursos visuales son propios; no incluye arte ni código de Apple o de los launchers evaluados.
