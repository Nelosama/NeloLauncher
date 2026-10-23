# Estabilización 0.2.0 — 22 de octubre de 2026

Base: a355ab0bbc41c539e2fe513952b40b8761c55e62.
Rama: codex/fix-nelo-launcher-ci-stability. No modificar main ni hacer merge automático.

## Causa y corrección

El [run original](https://github.com/Nelosama/NeloLauncher/actions/runs/39553469307)
falló en setup-android antes de Gradle. El log del job 119067679036 muestra
`sdkmanager tools` y `Failed to find package 'tools'`.
El action.yml de v3 declara por defecto `tools platform-tools`.
Se sobrescribe packages explícitamente con paquetes vigentes y se fija la
versión de command-line tools. Se mantienen todas las tareas y abortOnError.
Los artefactos incluyen resultados XML, ambos APK auxiliares y reportes aun al fallar.

Cambios adicionales: recursos para las cadenas detectadas por lint; reglas de
extracción Android 12+ que excluyen preferencias y archivos privados de copia
cloud y transferencia; comprobación del resultado de commit al borrar metadatos.
Sin nuevas dependencias, permisos ni cambios de HOME, paquete o versión.

Defecto verificado a 720p con 640×360 dp: las alturas fijas dejaban solo una
fracción de la tarjeta seleccionada visible. Para alturas inferiores a 400 dp
se reduce cabecera, título, espacios y altura de celda; se conserva la estructura,
la paginación y el diseño habitual. La prueba exige que la tarjeta seleccionada
quepa verticalmente entre los paddings de la cuadrícula.

## Validación

En curso: compilación completa local con JDK 17.0.12, Gradle 8.9, AGP 8.7.3,
Kotlin 2.0.21 y SDK 35. El entorno requiere proxy Java y truststore del sistema;
son ajustes externos al repositorio.
La suite se amplía a resolución 1280×720 con tarjeta enfocada completa, recuperación del enfoque y a
rotación con una foto dañada, persistencia y eliminación de metadatos.
El informe final registrará resultados y enlaces de Actions.

## Diseño y rendimiento

Se inspeccionaron los dos renders existentes de docs/screenshots a 1920×1080:
tarjetas panorámicas, espaciado uniforme, un borde de selección y texto legible
sobre el fondo de prueba. No validan contraste sobre todas las fotografías,
GPU, overscan o mando físico. La cuadrícula es desplazable; una segunda fila
parcial no implica contenido inaccesible. No se rediseña la composición.
No se afirma equivalencia con tvOS.

Revisión de código: LRU 3 MiB y bitmaps limitados, carga de arte/fotos en
ejecutor, GridView reciclable, favoritos limitados por página, animaciones
cancelables, callbacks de rotación retirados al pausar y limpieza al destruir.
No hay mediciones nuevas de PSS, FPS ni retorno desde apps comerciales.
La cola de carga puede acumular trabajo bajo navegación rápida; medir en el
Skyworth antes de introducir cambios de concurrencia sin evidencia.

## Advertencias justificadas

Se mantienen sin supresiones generales: tres DiscouragedApi por orientación
landscape de pantallas exclusivamente TV; UnusedAttribute por el opt-out de
Back predictivo API 33+ (ignorado en Android 11); VectorRaster por banner TV
320×180 dp. La orientación y el tamaño del banner preservan compatibilidad TV.
Las APIs Kotlin obsoletas de memoria y Back siguen siendo necesarias para
Android 11; no se cambia ese comportamiento para eliminar diagnósticos.
No se declara traducción completa: solo se extraen las cadenas señaladas.
El número definitivo de lint se registra al finalizar la compilación.

## Firma y distribución

La clave privada original no está en el repositorio. Los APK históricos en
dist no se sustituyen. Un APK generado con la clave debug del entorno/runner
NO puede actualizar 0.1.0 en este entorno: certificado local
3e57da43ea5bc41778ceb2ff516e36460632309963fc73a555250873ce3288b1,
distinto del original. Se comprobó con apksigner en ambos archivos.
Comparar SHA-256 del certificado con
657ec9a97b2e399a172e266f5946bcce1142a1f00d7eb8fd1a307e4733b8078f.
No desinstalar ni limpiar datos. Conservar 0.2.0/versionCode 2 para validación;
el próximo APK distribuible debe incrementar el número de versión y utilizar
la clave compatible, o resolver explícitamente la migración de firma.

## Pendiente en Skyworth

Android TV 11/API 30 Amlogic ~1 GB: actualización preservando datos con firma
compatible; D-pad y menús, favoritas/reordenamiento/ocultación, regreso desde
Netflix/Prime Video/Spotify, recuperación tras presión de memoria, fotos y
rotación en pausa/reanudación, proveedores reales y USB, contraste con fotos,
720p/1080p, reinicio y 30 minutos de uso. Ejecutar scripts/measure.sh para PSS,
inicio y frames. No hay acceso físico ni instrumentación ejecutada en este trabajo.

## Decisión

NO MERGE hasta disponer de pipeline completo verde y revisión de las evidencias.
Las pruebas físicas pendientes limitan cualquier afirmación de estabilidad final.
