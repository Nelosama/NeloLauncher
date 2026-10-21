# Imágenes de la implementación 0.2.0

**Procedencia:** render de la jerarquía real de vistas Android de Nelo en Robolectric 4.14.1, API 30, Canvas/Skia nativo, resolución 1920 × 1080. No son capturas del Skyworth ni de un emulador Android TV funcionando.

- [Inicio](home-native-api30.png): catálogo sintético de pruebas y pictogramas originales, inyectados exclusivamente por `src/test`. Permite revisar la composición, la fila de favoritas, el tamaño de las tarjetas y el enfoque único. El APK del producto no incluye esas aplicaciones ficticias ni esos iconos.
- [Configuración de fondos](wallpaper-native-api30.png): pantalla real de configuración después de aplicar una imagen local azul creada por la prueba. Esa fotografía tampoco se incluye como fondo permanente del producto.

El reloj usa el tiempo del runtime de pruebas. Las imágenes se generan ejecutando `./gradlew :app:testDebugUnitTest`, en `app/build/outputs/native-view-renders/`. Son renders del código implementado, no una maqueta HTML, una interfaz pintada encima de una referencia ni un montaje de tvOS.

La elevación GPU, las animaciones en movimiento, el firmware y el mando requieren validación en Android TV real o en un emulador estable. No se atribuyen a estas imágenes mediciones de FPS, memoria o latencia.
