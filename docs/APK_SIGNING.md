# Firma y actualización sin pérdida de datos

Estado: bloqueo de distribución del código estabilizado; PR #1 abierto, NO MERGE.
El usuario aclaró que no tiene confirmada la ubicación del entorno original;
podría ser una sesión anterior de Codex o Windows. La ruta candidata es
C:\Users\Nelo\.android\debug.keystore, todavía sin certificado comprobado. No se afirma que la clave se haya perdido definitivamente.

## Diagnóstico y comparación

En cc22b74 (0.1.0), Gradle no definía signingConfigs ni clave de release:
assembleDebug usó la firma debug predeterminada de AGP. El informe histórico
registra una compilación local y excluye la clave privada del repositorio.
El workflow tampoco restauraba un keystore desde Secrets. El run fallido
39553469307 no tiene artefactos (consulta directa); el APK 0.1.0 está versionado
desde cc22b74 y su firma v2 se registra en la evidencia de ese commit. No hay
evidencia de que ese APK proceda de Actions ni del equipo Windows del usuario. Los APK históricos
comparten certificado; cada nuevo entorno/runner generó su propia clave debug.
Que todos tengan CN=Android Debug y alias androiddebugkey no implica identidad.

| APK | versionCode | SHA-256 del certificado |
|---|---:|---|
| dist/0.1.0 histórico | 1 | 657ec9a97b2e399a172e266f5946bcce1142a1f00d7eb8fd1a307e4733b8078f |
| dist/0.2.0 histórico | 2 | 657ec9a97b2e399a172e266f5946bcce1142a1f00d7eb8fd1a307e4733b8078f |
| 0.2.0 local estabilizado | 2 | 3e57da43ea5bc41778ceb2ff516e36460632309963fc73a555250873ce3288b1 |
| Actions 39889217868 | 2 | f0d75818391647505683d00c7e038c341cc10baaa9e9a681e4dd9febf75e4b44 |

Se descargó y verificó el artefacto 12474545695 del run aprobado, no se dedujo
su firma de la configuración. Identidad, hash del archivo, paquete y minSdk de
los cuatro APK: [APK-IDENTITY.json](evidence/signing/APK-IDENTITY.json).
SHA-256 del APK principal de ese run:
`6aaecb8e7995291c961fdfc61c259ce6fdeb528aa3f0984e30be8922f72547f8`.
SHA-256 del ZIP completo, distinto del hash del APK:
`4e927eadde91be00f4f42af0e297bfbfdbbfadc3fd2408302061742d2c4a5afe`.

Solo se encontró /home/agent/.android/debug.keystore en los directorios accesibles
/home/agent, /workspace y /opt; su certificado es el local 3e57…, no el original.
El historial de Git accesible no contiene keystores. El entorno no tiene secretos
de firma configurados. No se inspeccionaron los valores de GitHub Secrets: sus
valores no son recuperables mediante la API. No hay restauración de Secrets en
los workflows históricos. Esto no prueba que no exista una copia fuera del entorno.

No se puede derivar la clave privada del certificado público ni del APK. Una
rotación de firma válida también requiere la clave anterior para autorizar el
linaje. Cambiar el nombre del certificado o generar otra clave con el mismo alias
no permite actualizar. No se han modificado ni falsificado certificados.

## Recuperar y comprobar la clave original

En el entorno donde se construyó 0.1.0, buscar el archivo predeterminado:
Linux/macOS: ~/.android/debug.keystore; Windows: %USERPROFILE%\\.android\\debug.keystore.
También revisar ANDROID_USER_HOME o una signingConfig personalizada si existían.
No ejecutar una compilación que regenere una clave ausente antes de preservar
los archivos originales. No pegar el archivo, Base64 ni contraseñas en chat,
logs, commits, issues o comentarios del PR.

Copiar el keystore a almacenamiento privado con permisos restrictivos y respaldo
seguro. Consultar solo su certificado con `keytool -list -v -keystore RUTA`
(introducir la contraseña en el prompt privado). Debe existir una PrivateKeyEntry
y coincidir exactamente con 657ec9…078f. Un certificado exportado sin clave privada
no basta. No modificar el keystore ni regenerarlo si la contraseña no funciona.

## Compilar y firmar cuando se recupere la clave

Se conservan paquete tv.nelo.launcher y versionName 0.2.0/versionCode 2 durante
validación: 2 es estrictamente mayor que 1. Antes de distribuir otro APK posterior
a un 0.2.0 ya instalado, incrementar versionCode (al menos 3), actualizar su nombre
de versión y repetir checks. No se publica un APK nuevo compatible sin la clave.

Con Java 17, SDK 35, Build Tools 34.0.0, Gradle 8.9:

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :fixtures:assembleDebug
# NELO_KEY_ALIAS, NELO_STORE_PASSWORD y NELO_KEY_PASSWORD deben estar ya
# inyectadas en el entorno por un gestor seguro; no escribir valores aquí.
python3 scripts/sign_compatible_apk.py \
  --keystore /ruta/privada/original.keystore \
  --input app/build/outputs/apk/debug/app-debug.apk \
  --output work/nelo-tv-0.2.0-compatible.apk
python3 scripts/apk_identity.py compare \
  dist/nelo-tv-0.1.0-debug.apk work/nelo-tv-0.2.0-compatible.apk
python3 scripts/apk_identity.py manifest --output-dir work/identity \
  work/nelo-tv-0.2.0-compatible.apk
```

El script reemplaza las firmas del APK de entrada usando la clave proporcionada,
con firmas v2/v3 y sin v4. No modifica la clave ni el certificado. Publica el archivo
de salida únicamente si apksigner valida en API 30 y el certificado coincide con
la referencia. Rechaza paquete distinto, versionCode no creciente y minSdk >30.
No sobrescribe archivos existentes. Las contraseñas se pasan a apksigner por
referencia de entorno y no se imprimen sus diagnósticos de firma.
Esto es reproducibilidad del proceso y de la identidad de firma; no se promete
un APK bit a bit idéntico entre máquinas.

El firmador se verificó con una pareja de APK de prueba firmados exclusivamente
con la clave local; la prueba de clave incorrecta frente a 0.1.0 rechazó la salida.
Esas muestras están solo en work y no son APK compatibles para entregar al usuario.

Para GitHub, cuando se recupere y verifique la clave, guardar en un Environment
protegido NELO_KEYSTORE_B64, NELO_KEY_ALIAS, NELO_STORE_PASSWORD, NELO_KEY_PASSWORD.
Base64 es transporte, no cifrado. Limitar la firma a un workflow revisado, con
aprobación del Environment; nunca pull_request ni pull_request_target ejecutando
código de un PR no confiable. Decodificar sin imprimir en RUNNER_TEMP con umask 077,
invocar el firmador, verificar identidad/hash y retirar el archivo temporal en
finally/always. Subir solo APK e identidad, nunca keystore ni entorno completo.
**No se han creado Secrets ni activado firma remota:** aún falta verificar la clave.
El procedimiento local permite validar el APK del PR antes de merge.

## Identificación de Actions

CI conserva las seis pruebas y todas las tareas originales. Tras compilar,
scripts/apk_identity.py genera build/apk-identity/APK-IDENTITY.json y SHA256SUMS
para el APK principal. Incluye source_commit (head real del PR), build_commit
(commit de integración que GitHub prueba), run ID, paquete, versión, minSdk,
certificado y SHA-256 del archivo. El nombre del artefacto incluye github.sha.
Un SHA-256 identifica bytes; no demuestra compatibilidad de firma por sí solo.
Desde la raíz del artefacto extraído: `sha256sum -c build/apk-identity/SHA256SUMS`.
En Windows: `Get-FileHash RUTA_APK -Algorithm SHA256` y comparar el valor completo.
Los artefactos debug ordinarios siguen siendo de validación, no actualizadores
compatibles mientras no se recupere la clave original.

## Preservación de datos y alternativas

1. Preferida: recuperar la clave y actualizar con mismo paquete/certificado,
   versión creciente y pm install -r. Es el camino Android que preserva el sandbox;
   todavía exige prueba de actualización y comprobación de datos en un entorno
   autorizado. No se ha ejecutado instalación ni retención real en este trabajo.
2. El APK histórico dist/0.2.0 tiene firma compatible, code 2 >1 y minSdk 26,
   comprobados estáticamente. No incluye las correcciones del PR; no confundirlo
   con una compilación estabilizada. No se instala ni se recomienda como sustituto
   silencioso del APK corregido.
3. Al ser 0.1.0 debuggable, ADB run-as podría permitir exportar preferencias y
   archivos privados SIN desinstalar. Hay que comprobar que el firmware admite
   run-as y verificar una copia/restauración en un dispositivo de prueba antes de
   considerarla una migración segura. No se ha validado en Skyworth y no se ofrece
   un restaurador automático. Un tar puede ser inconsistente si la app escribe
   mientras se copia; no aceptar una copia como válida solo porque exista el archivo.
   Proteger el respaldo: puede contener fotos y nombres de aplicaciones privadas.
4. Datos relevantes según el código: shared_prefs/launcher.xml (favoritas, orden,
   ocultas, reloj, animaciones, rotación), shared_prefs/wallpapers.xml (foto actual y
   etiquetas), files/wallpaper.jpg heredado y files/wallpapers/*.jpg. La bandeja
   externa Pictures/Inbox se respalda por separado; no confundirla con la galería.
5. adb backup no es una alternativa garantizada: 0.1.0 declara allowBackup=false,
   su soporte depende del firmware y no se validó. Cambiar esa declaración en un
   APK nuevo no permite instalarlo con otra firma. El almacenamiento con ámbito
   tampoco da acceso automático a preferencias de otra app.
6. Un paquete paralelo mantiene intacta la instalación anterior, pero no hereda
   sus datos; requiere exportación/importación verificadas. No se implementa aquí
   ni se cambia tv.nelo.launcher. No usar root, desbloqueo o desinstalación como vía
   de migración. Ninguna alternativa queda certificada como sin pérdida.

## Prueba autorizada futura en Skyworth

No se ha ejecutado ADB contra el TV ni cambiado HOME/FLauncher.
Antes de autorizar una instalación: preservar clave y respaldo verificado,
comprobar el APK instalado real (no suponer que coincide con dist), comparar
certificado, paquete, versión y SHA-256 con el candidato firmado. Se puede obtener
la ruta con `adb -s SERIAL shell pm path tv.nelo.launcher` y copiar el base.apk al
PC para la comparación; estos comandos se proporcionan como instrucciones, no
se han ejecutado. Usar SERIAL explícito para no seleccionar otro dispositivo.

Solo tras comprobaciones y autorización, usar `adb -s SERIAL install -r APK`,
sin -d, uninstall, pm clear, cambio de HOME ni intervención de otros paquetes.
Si Android devuelve incompatibilidad, detenerse y conservar instalación/datos.
Registrar antes/después favoritas, orden, ocultas, reloj/animaciones, fondo,
galería y rotación; probar D-pad a 720p/1080p y regreso desde apps externas.
La validación estática y los renders JVM no sustituyen esa prueba.

Comprobación de solo lectura en PowerShell para la ruta candidata (no instala nada):

```powershell
$keytool = (Get-Command keytool.exe -ErrorAction SilentlyContinue).Source
if (-not $keytool) { $keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" }
if (-not (Test-Path $keytool)) { throw "No se encontró keytool; localizar Java/Android Studio." }
& $keytool -list -v -keystore "C:\Users\Nelo\.android\debug.keystore" -alias androiddebugkey
```

Introducir la contraseña solo en su prompt privado. Compartir únicamente la huella
SHA-256 y si existe PrivateKeyEntry; si no existe el archivo o no coincide,
conservarlo sin cambios y buscar el entorno/copia anterior.

## Estado guardado para continuar

La validación local final de las cinco tareas terminó con BUILD SUCCESSFUL.
Las seis pruebas pasan con 0 fallos/errores/omitidas; las pruebas de inicio
720p/1080p ahora envían eventos D-pad derecha/izquierda y comprueban selección
y tarjeta completa. Lint permanece con 0 errores y 5 advertencias justificadas.
Resultados en evidence/signing/build-validation.txt y unit-tests.xml.

Pendiente: el usuario debe comprobar en Windows la huella del keystore candidato.
No hay clave original confirmada, APK estabilizado compatible, Secrets creados,
instalación real ni respaldo/restauración validados. La firma remota no se activa.
Tras publicar este commit debe comprobarse su nuevo run de Actions, incluida la
generación de APK-IDENTITY.json/SHA256SUMS. El run 39889217868 anterior está verde.
El PR #1 sigue abierto, NO MERGE. No hubo cambios en el Skyworth.
