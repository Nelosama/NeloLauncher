# Referencias de diseño

Se analizaron las dos imágenes de Apple TV/tvOS aportadas en el chat como referencia de composición: fila superior agrupada, tarjetas panorámicas grandes y regulares, etiqueta bajo la selección, escala de enfoque, profundidad y fondo a pantalla completa.

No se incorporan esas fotografías, logotipos, iconos ni fondos al APK. La pantalla es una jerarquía de vistas Android funcionales. El fondo predeterminado Aurora se dibuja con gradientes propios; las fotografías adicionales son elegidas por el usuario y el arte de aplicaciones procede de los paquetes instalados.

Los renders de pruebas usan pictogramas originales y nombres ficticios exclusivamente desde `src/test`, nunca desde el producto. Véase `docs/screenshots/README.md` para su procedencia exacta.
