# Rediseño visual 0.2.0

La base tecnológica sigue siendo Kotlin, vistas Android del framework, GridView, un ejecutor serial, preferencias locales y caché LRU. No se migró a Flutter, Compose ni una base GPL. Se reutilizan y amplían catálogo, orden/favoritas, lanzamiento, límites de imágenes y licencias.

## Transformación

| 0.1.0 | 0.2.0 |
|---|---|
| Cabecera grande, eslogan, pestañas Aplicaciones/Favoritas y contador | Cabecera discreta con nelo, reloj opcional y Configuración; título contextual de la app enfocada |
| Iconos cuadrados pequeños dentro de celdas con texto permanente | Tarjetas panorámicas uniformes con banner nativo si existe; nombre bajo la selección |
| Favoritas como filtro de la cuadrícula | Fila dedicada sobre un panel oscuro translúcido estático, más cuadrícula de aplicaciones |
| Escala 1.04 con retorno inmediato | Escala 1.075, ida y vuelta de 155 ms, elevación de 12 dp y borde claro |
| Lanzamiento inmediato sin reacción visual | Respuesta de pulsación de 70 ms antes de abrir; opción de desactivar animaciones |
| Fondo elegido y aplicado directamente | Configuración propia, vista previa, galería persistente, bandeja sin permisos, explorador y rotación opcional |

## Relación con las referencias

Se adoptan las proporciones panorámicas, el espacio entre tarjetas, la fila agrupada de favoritas, el fondo inmersivo y la selección que destaca sobre las vecinas. Se mantiene identidad propia: tipografía del sistema, marca nelo, gradientes originales y contenido únicamente local.

No se reproduce la promoción de Foundation, ningún panel comercial, los iconos de Apple ni su desenfoque. El panel de favoritas usa transparencia estática; las sombras provienen del renderer Android. El arte de Netflix, Prime Video o Spotify solo puede aparecer si sus propias aplicaciones instaladas lo publican.

## Navegación y memoria

La fila muestra como máximo cinco tarjetas (cuatro o tres en anchuras menores). Izquierda/derecha cambia de página al alcanzar un extremo; abajo entra en la columna correspondiente de la cuadrícula. Arriba vuelve hacia Favoritas o Configuración. El componente seleccionado se guarda al salir y volver, incluso si se refresca el catálogo.

Antes de cada cambio de enfoque se cancela la animación anterior; solo una tarjeta conserva borde y etiqueta. Se detectó y corrigió un caso de doble borde al reciclar vistas, y existe una prueba de regresión. Las animaciones usan propiedades de escala, alpha y translationZ; no fuerzan un nuevo layout por frame.

Banners y logos se cargan fuera del hilo principal, en caché de 3 MiB. Las fotos se reducen antes de guardarse. La rotación usa un callback cancelable únicamente mientras HOME está visible, sin servicio, alarma ni trabajo en segundo plano.

## Validación visual

Los archivos y el origen de las imágenes se describen en [screenshots/README.md](screenshots/README.md). Se revisaron imágenes producidas por la jerarquía real de vistas Android a 1920 × 1080 en el runtime de pruebas API 30; no son un montaje de las referencias ni una captura de firmware Skyworth.

La composición, el tamaño de las tarjetas y el enfoque se han inspeccionado en ese render. La percepción de elevación del renderer GPU, el movimiento en un televisor, el overscan y la respuesta del mando físico siguen pendientes. No se declara equivalencia visual o de fluidez con tvOS.

## Corrección de estabilización en alturas pequeñas

El render adicional de 1280 × 720 con 640 × 360 dp reveló que la cabecera,
favoritas y márgenes dejaban recortada la tarjeta enfocada de la cuadrícula.
Para alturas menores de 400 dp se compactan tamaños y espacios, conservando
estructura y navegación. La prueba API 30 ahora exige que la tarjeta seleccionada
quepa completa verticalmente. Esto valida geometría en JVM, no GPU ni TV físico.
