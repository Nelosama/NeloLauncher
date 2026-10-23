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

Compilación completa local **aprobada** con JDK 17.0.12, Gradle 8.9,
AGP 8.7.3, Kotlin 2.0.21 y SDK 35; las cinco tareas solicitadas terminaron
con BUILD SUCCESSFUL (108 tareas, incluidas las ya actualizadas).
Comando: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :fixtures:assembleDebug`.
El entorno necesitó proxy Java/truststore y Kotlin en proceso, ajustes externos
al repositorio. APK principal, instrumentación y fixture generados.

**6 pruebas, 0 fallos, 0 errores, 0 omitidas**, runtime API 30 (Android 11).
Las cuatro pruebas originales se conservan; se añade inicio/enfoque a 720p
y rotación que omite foto dañada más persistencia de favoritas, orden,
ocultas y ajustes. Las pruebas de inicio a 1080p/720p comprueban la geometría
vertical completa de la tarjeta seleccionada y recuperación al reanudar.
Se inspeccionó el nuevo render 720p en docs/screenshots; es evidencia JVM.
No se ejecutó instrumentación en dispositivo.

Lint: **0 errores, 5 advertencias**, frente a las 13 históricas; sin baseline,
supresiones ni desactivación de checks. Evidencias:
[build](evidence/stability/build-checks.txt),
[JUnit](evidence/stability/unit-tests.xml),
[lint](evidence/stability/lint.xml),
[firma nueva](evidence/stability/local-signature.txt),
[firma 0.1.0](evidence/stability/original-signature.txt).

Durante la ampliación de tests hubo una compilación fallida por declaración
duplicada en la prueba; se eliminó el bloque repetido antes de esta validación.
Los mensajes de decodificador en stderr corresponden a los archivos corruptos
que las pruebas rechazan intencionalmente.

PR: https://github.com/Nelosama/NeloLauncher/pull/1.
El primer pipeline corregido pasó SDK, cinco tareas y artefactos:
https://github.com/Nelosama/NeloLauncher/actions/runs/39888738536.
La ejecución del código final se verifica en
https://github.com/Nelosama/NeloLauncher/actions/runs/39889066996.

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
Resultado final local: cinco advertencias, cero errores.

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

Diagnóstico ampliado, comparación real con Actions y recuperación de clave: [APK_SIGNING.md](APK_SIGNING.md).
