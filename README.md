# Nelo TV Launcher 0.2.0

Launcher Kotlin para Android TV 11, con una pantalla inmersiva inspirada en la composición de tvOS y recursos gráficos propios. Continúa el proyecto 0.1.0: mismo paquete `tv.nelo.launcher`, mismas preferencias y compatibilidad con su fondo local.

[Descargar APK 0.2.0](https://github.com/Nelosama/NeloLauncher/raw/refs/heads/main/dist/nelo-tv-0.2.0-debug.apk) · [Firma y SHA-256](dist/SHA256SUMS) · [Cambios visuales](docs/DESIGN_0.2.0.md) · [Resultados y límites](docs/TEST_REPORT.md)

## Qué cambia

- Fondo a pantalla completa y tarjetas panorámicas; se prefieren los banners publicados por las apps instaladas. Para apps sin banner se usa su icono real.
- Fila de favoritas con hasta cinco tarjetas por página, junto a una cuadrícula reciclable. Flecha derecha/izquierda pasa de página sin crear cientos de vistas.
- Enfoque único con escala suave, elevación y borde claro; nombre bajo la tarjeta enfocada. Animaciones cancelables, sin cola acumulativa.
- Favoritas, orden, ocultación y recuperación de apps, reloj opcional y configuración desde el mando.
- Galería privada de hasta 24 fotografías, vista previa antes de aplicar, JPG/JPEG/PNG/WEBP, copia optimizada, fondo estático predeterminado y rotación opcional.
- Explorador de imágenes propio para TV sin selector, más una bandeja local que funciona sin permiso de almacenamiento. Se filtra `DocumentsStub`.

Sin publicidad, recomendaciones comerciales, Internet, telemetría, servicios residentes ni modificaciones del sistema. FLauncher permanece independiente como respaldo. La selección del launcher predeterminado corresponde al usuario.

## Instalar sobre 0.1.0 en Windows

Solo el APK histórico 0.2.0 de dist/ comparte firma con el 0.1.0 publicado; no contiene las correcciones del PR #1. Los APK locales y de Actions del PR tienen firmas diferentes y no deben usarse para actualizar esa instalación. Véase [diagnóstico y recuperación de firma](docs/APK_SIGNING.md). El código de versión sube de 1 a 2. No desinstales la versión anterior ni borres sus datos.

Los siguientes comandos son únicamente para un candidato cuya firma ya se haya comparado con el APK instalado y con autorización para modificar el TV. No ejecutarlos con el APK de Actions incompatible. En PowerShell, con Platform Tools y el TV conectado/autorizado:

```powershell
$adb = "C:\platform-tools\adb.exe"  # Ajusta esta ruta
$tv = "IP_DEL_TV:5555"              # Usa la dirección y puerto de tu TV
& $adb connect $tv
& $adb devices
& $adb -s $tv push "C:\Users\Nelo\Downloads\nelo-tv-0.2.0-debug.apk" /data/local/tmp/nelo-tv-0.2.0-debug.apk
& $adb -s $tv shell pm install -r /data/local/tmp/nelo-tv-0.2.0-debug.apk
& $adb -s $tv shell am start -W -n tv.nelo.launcher/.MainActivity
```

Se utiliza transferencia e instalación por separado porque este método funcionó para la 0.1.0 según el registro proporcionado por el usuario. Si ya tienes ADB en PATH, puedes usar `adb` directamente. `adb install -r archivo.apk` sigue siendo una alternativa.

Estos comandos abren únicamente Nelo: **no cambian el launcher predeterminado, no deshabilitan componentes y no alteran Google Play Services**. Conserva y prueba FLauncher. Cambiar HOME, si lo deseas después de probar, se hace manualmente desde los ajustes del TV.

Si aparece `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, detente: ese APK anterior tiene otra firma. No uses `uninstall` ni `pm clear`. Una compilación de GitHub Actions o de otro equipo puede tener otra clave debug. La actualización sin pérdida requiere la clave de esa instalación o una variante con paquete distinto para probar por separado. La clave privada de desarrollo no se publica.

## Navegar con el mando

- Flechas: cambiar de tarjeta y entre favoritas, aplicaciones y Configuración. En el extremo de Favoritas, izquierda/derecha cambia de página.
- OK: abrir una aplicación o confirmar. Mantener OK o pulsar Menú: favorita, mover antes/después, ocultar o información de Android.
- Atrás: cerrar diálogos, volver de exploración o subir a Favoritas/Configuración. No cierra HOME.
- Ocultar no desinstala: Configuración → Aplicaciones ocultas permite mostrar una o todas de nuevo.
- El enfoque se conserva por componente al regresar de otra aplicación. El orden se comparte entre la cuadrícula y las favoritas.

Netflix, Prime Video y Spotify se detectan y abren mediante sus actividades exportadas si están instaladas y habilitadas. No se simulan sus iconos, cuentas, DRM ni contenido. Las apps móviles pueden necesitar ratón dentro de su propia interfaz.

## Tus fotografías

Configuración → Fondo de pantalla → Añadir fotografía ofrece:

1. **Selector del sistema**: admite proveedores reales y evita el componente provisional `DocumentsStub`.
2. **Explorar carpetas del TV**: navegador propio, manejable con D-pad. En Android 11 solicita lectura de almacenamiento únicamente al elegir esta opción y después de explicar su uso. No solicita acceso de escritura ni gestión de todos los archivos. Algunas carpetas quedan limitadas por el almacenamiento con ámbito de Android.
3. **Bandeja de Nelo**: siempre disponible, sin permisos. Puedes copiar imágenes por ADB:

```powershell
& $adb -s $tv shell mkdir -p /sdcard/Android/data/tv.nelo.launcher/files/Pictures/Inbox
& $adb -s $tv push "C:\Users\Nelo\Pictures\foto.jpg" /sdcard/Android/data/tv.nelo.launcher/files/Pictures/Inbox/
```

Después abre la bandeja desde Nelo y elige la foto. La vista previa permite **Aplicar** o **Cancelar**. Cancelar conserva el fondo anterior. La copia optimizada se guarda dentro de Nelo, por lo que borrar o mover el archivo original no rompe el fondo.

Mi galería permite elegir otra foto o eliminar **su copia en Nelo**; nunca se elimina el original. Restaurar fondo predeterminado conserva la galería y desactiva la rotación. Para rotar, añade al menos dos fotos y elige 5, 15, 30 o 60 minutos. La rotación se pausa al salir de Nelo y no despierta el dispositivo.

Las imágenes se ajustan a un máximo de 1280 × 720 para limitar RAM; el inicio se dibuja a la resolución de la pantalla, incluido 1080p. Se conserva la proporción y se usa recorte central, sin deformación. En Android 11 el decodificador del sistema aplica la orientación de la imagen. Android 8 tiene una ruta de compatibilidad que requiere validación adicional de EXIF.

## Compilar y probar

Abre el repositorio en Android Studio. Requiere JDK completo 17 o 21, SDK 35, Build Tools 34.0.0, Gradle 8.9, AGP 8.7.3 y Kotlin 2.0.21. No necesita Flutter.

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :fixtures:assembleDebug
```

APK principal: `app/build/outputs/apk/debug/app-debug.apk`.

`fixtures/` es una pequeña app externa de pruebas, separada del producto; no está dentro del APK de Nelo. Robolectric, AndroidX Test y JUnit son dependencias exclusivas de pruebas. Para ejecutar las pruebas instrumentadas en un emulador ya autorizado:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r fixtures/build/outputs/apk/debug/fixtures-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class tv.nelo.launcher.LauncherSmokeTest tv.nelo.launcher.test/androidx.test.runner.AndroidJUnitRunner
```

No ejecutes estas pruebas automatizadas sobre tus preferencias personales sin revisión: cambian temporalmente favoritas, fotos y orden para comprobar la persistencia, y restauran su estado cuando terminan normalmente.

## Validación y rendimiento

Consulta [el informe](docs/TEST_REPORT.md) para distinguir pruebas del runtime Android en JVM, intentos de emulador y pruebas físicas pendientes. Las imágenes en `docs/screenshots/` identifican su procedencia; no se presentan renders de pruebas como capturas del Skyworth.

Objetivo de 60 FPS **sin garantía ni medición física todavía**. Caché LRU de arte de apps: 3 MiB; iconos de 128 × 128 o banners de 320 × 180. Escala/elevación mediante propiedades; sin blur dinámico. GridView recicla vistas y la fila de favoritas está acotada. El ejecutor serial, el receptor de paquetes y el temporizador no crean un servicio permanente.

Para medir el dispositivo sin cambiar su HOME:

```sh
./scripts/measure.sh resultados-skyworth
```

Código propio bajo MIT. Conserva `LICENSE`, `NOTICE` y `licenses/` al redistribuir. La [evaluación de FLauncher/LTvLauncher](docs/RESEARCH.md) y los [resultados históricos de 0.1.0](docs/TEST_REPORT_0.1.0.md) permanecen disponibles.

## Estabilización de CI 0.2.0

La rama `codex/fix-nelo-launcher-ci-stability` corrige la instalación del SDK:
`setup-android@v3` recibía implícitamente `tools platform-tools`; ahora se
declaran platform-tools, API 35 y Build Tools 34.0.0, sin el paquete obsoleto
`tools`. Se conservan las cinco tareas de validación y se publican también
APK de instrumentación, fixture y reportes cuando falla una comprobación.

Java 17, Gradle 8.9, AGP 8.7.3 y Kotlin 2.0.21 se conservan. Los APK debug
de cada runner pueden usar certificados diferentes: comparar el certificado
con 0.1.0 antes de intentar actualizar. No desinstalar ni borrar datos para
resolver una firma incompatible. Véase [informe de estabilización](docs/STABILITY_0.2.0.md).
