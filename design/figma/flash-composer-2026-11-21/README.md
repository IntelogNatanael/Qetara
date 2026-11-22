# Flash · Compositor PC y Android · ES / EN

[Referencia editable en Figma](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=28-505) · [Captura](reference.png) · [Evidencia estructural](state.json)

Cuenta verificada: **cchoquenairat@unsa.edu.pe**. Equipo: **dev-UNSA**.
Sesión de implementación: `a1557784-71df-45bd-98c2-7c373b22b85b`.
Página nueva: `27:63`, **Flash · Compositor · 2026-11-21**. Las referencias anteriores se conservaron.

## Pantallas

| Pantalla | Nodo | Tamaño |
|---|---|---|
| PC, español | [28:511](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=28-511) | 1040 × 844 |
| Android, español | [28:586](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=28-586) | 390 × 844 |
| PC, inglés | [28:658](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=28-658) | 1040 × 844 |
| Android, inglés | [28:733](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=28-733) | 390 × 844 |

Las cuatro pantallas muestran mensaje + dos archivos, con adjuntos y actividad expandidos. Los receptores de ejemplo usan los nombres neutrales **Android 02** y **Laptop 1**. La matriz inferior muestra las acciones en los estados vacío, solo texto, solo archivos y ambos, en ES/EN.

## Especificación de interacción

- Barra compacta: **Flash activo / Flash active**, tiempo restante y **Detalles / Details**. La dirección, carpeta y datos técnicos quedan en los detalles de sesión.
- Receptor seleccionable, con **Cambiar / Change** y **Identidad por verificar / Identity not yet verified**. Elegir receptor no implica verificar su identidad.
- Editor prioritario, con **Adjuntar / Attach** y **Pegar / Paste** próximos.
- Bandeja de archivos: cantidad, tamaño agregado, nombre y tamaño por archivo, y quitar individual con objetivo táctil de 48 dp. Quitar todos sigue disponible.
- Vacío: sin acción de envío. Solo texto: **Enviar mensaje / Send message**. Solo archivos: **Enviar N archivos / Send N files**, sin botón de mensaje deshabilitado. Ambos: dos acciones independientes; no se cambia el protocolo para crear un envío mixto.
- Estado junto al compositor. Actividad secundaria compacta con **Ver texto / View text** y **Copiar / Copy**.
- Android: compositor por encima del IME y actividad desplazable. La implementación compacta muestra cantidad/tamaño + **Revisar / Review**, y ofrece eliminar cada archivo en el diálogo de lista completa. La referencia ilustra la expansión de esa lista; las dos acciones deben seguir alcanzables cuando aparece el teclado.
- PC: la implementación pliega la actividad por defecto y reserva la columna lateral de 260 dp para anchos de al menos 1040 dp. El compositor mantiene un espacio de estado de al menos 78 dp. La referencia muestra actividad expandida y no pretende igualdad píxel por píxel.
- Cada envío conserva la confirmación bilateral por lote/conexión. Se mantienen las referencias existentes [emisor 4:43](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=4-43) y [receptor 4:76](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=4-76).

## Fuentes, tema y componentes

Se revisaron `DesktopFlash.kt`, `FlashComponents.kt`, `FlashActivity.kt`, `QetaraTheme.kt` y `P2pRouteTheme.kt`. Ambas plataformas incluyen Inter; se usa la misma familia y pesos Regular, Medium y Semi Bold. Los iconos proceden de las rutas vectoriales de `WorkspaceIcons.kt`.

Se reutilizan las variables existentes de Flash para superficie, texto, fondo, primario **#0A6B77**, espaciado de 16/24 y radio 16. La colección local **Flash compositor · layout** añade solo las necesidades de 8/12, radio 8 y borde **#C3D2DC**, coherente con el tema. Esta es una referencia del tema claro frío; no prescribe cambiar la apariencia elegida por el usuario. La marca clásica **#FFF7ED / #102A43** y su geometría no se modificaron.

La búsqueda de Code Connect no encontró mapeos. El archivo no tenía bibliotecas añadidas. Se buscó en Material 3 un compositor de transferencia, botones y campo; no apareció un compositor equivalente. Se inspeccionó el campo Material 3 y usa Roboto, con estructura que no representa el compositor combinado de Qetara. Se construyeron componentes locales para mantener Inter, los tokens y los límites del producto: 6 botones por plataforma/tipo, receptor, archivo, editor, sesión, actividad y 5 iconos. Las pantallas contienen instancias; los componentes principales están fuera del panel de revisión.

## Validación y límites

El panel tiene **340 descendientes**, **136 textos Inter**, **80 instancias**, 24 vectores, 98 marcos y 2 rectángulos. La comprobación de familia devolvió cero discrepancias; hay cero nodos con relleno de imagen. Todo el contenido de interfaz es editable.

La revisión de la captura detectó un recorte inferior de PC. Se corrigió el alto a 844 y el receptor a 64 en las cuatro instancias. La captura final muestra el estado y el pie completos. Los hijos de PC terminan en y=814, dentro de 844.

Los mensajes, nombres de archivo, tamaños, tiempos y equipos son ejemplos sintéticos. Esta captura es de la referencia Figma, no una prueba de transferencia ni de la aplicación en ejecución. El comportamiento con IME está especificado; no se dibujó un teclado ni se ejecutaron builds o dispositivos como parte de este trabajo. No se crearon pantallas dark/warm ni nuevos diálogos de aprobación.

`build-reference.js` conserva el script de creación con guardia de página vacía; **no volver a ejecutarlo sobre la página poblada**. `state.json` conserva los IDs y la evidencia final.
