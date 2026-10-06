# Qetara · UNSA · 5 de octubre de 2026

Cuenta de trabajo exclusiva: **cchoquenairat@unsa.edu.pe**. Equipo educativo: **dev-UNSA**.
Archivo activo: [Qetara · Diseño y experiencia](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B).

El archivo anterior, asociado a Gmail, estaba vacío. Se creó un archivo nuevo en
el equipo elegido y se incorporaron referencias desde los recursos del proyecto;
no se transfirió la propiedad del archivo antiguo ni se utilizó la cuenta Gmail
para esta iteración.

## Referencias editables verificadas

- [Marca clásica](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=2-9):
  componente vectorial reutilizable, fondo `#FFF7ED` y símbolo `#102A43`, ligados a
  variables. SVG original de PC, viewport 108 × 108, escala interna 0.65625 y
  relación de la silueta 1.420204571:1. Android restaura el fondo clásico y los
  diez recursos raster históricos; su máscara adaptativa sigue siendo del sistema.
- [Confirmación por lote Flash](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=4-22):
  componentes emisor/receptor, cantidad y tamaño total, nombres de todos los
  archivos, verificación bilateral y aprobación limitada a ese lote/conexión.
  Los próximos lotes requieren una nueva confirmación. Los ejemplos de archivos,
  dirección y código son sintéticos.

La revisión visual corrigió el ajuste de alturas de la marca. La captura final
presentó símbolo y textos completos. El panel de Flash contiene texto, marcos,
una casilla y dos instancias editables, sin imágenes raster; tipografías Inter y Roboto Mono.
En escritorio se conserva la casilla de comparación y el botón inicialmente deshabilitado.
Los componentes principales permanecen fuera de los paneles de revisión.
El modal real conserva su paleta de plataforma/tema y el desplazamiento del
contenido cuando falta espacio; el panel de Figma muestra el contenido expandido.

Capturas conservadas: [marca](brand-reference.png) y [lote Flash](flash-reference.png).

Se inspeccionaron las bibliotecas disponibles y el kit Material 3: existen
diálogos, botones, variables de superficie y estilos de texto. No se encontraron
mapeos Code Connect ni componentes Qetara publicados. Se usaron componentes
locales porque el contenido de consentimiento, los tokens propios, Inter y las
variantes Compose de Android/escritorio no corresponden directamente al diálogo
genérico del kit. No se afirma que no existan bibliotecas.

## Estado y límites

[state.json](state.json) registra IDs, variables, componentes y evidencia
estructural. Los scripts de creación conservan la especificación; **no deben
repetirse sobre un archivo poblado**. Sus comprobaciones detienen recreaciones
accidentales. Las pantallas históricas completas no se han migrado a Figma.

Estos paneles son referencias de diseño editables, no capturas de la aplicación
ni evidencia de transferencias ejecutadas. Las [capturas de la aplicación](verified-ui/README.md)
y el [informe de validación](../../../docs/FLASH_BATCH_REVIEW-2026-10-05.md)
documentan por separado la comprobación de interfaz y transporte.
