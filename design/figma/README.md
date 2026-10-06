# Qetara · Diseño de escritorio

## Referencia activa desde el 5 de octubre de 2026

Usar exclusivamente la cuenta **cchoquenairat@unsa.edu.pe**, equipo **dev-UNSA**.
El archivo activo es [Qetara · Diseño y experiencia](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B).
Contiene referencias editables y revisadas visualmente de la
[marca clásica](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=2-9) y de la
[confirmación por lote Flash](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=4-22).
Alcance, procedencia y límites en [la revisión actual](unsa-2026-10-05/README.md).
El archivo anterior permanecía vacío; se creó esta referencia desde los recursos
locales, sin transferir su propiedad ni utilizar la cuenta Gmail.

## Antecedentes

Iteración del 11 de septiembre de 2026, limitada a presentación Compose Desktop.

## Referencia y límite de Figma

Archivo creado: [Qetara · Escritorio](https://www.figma.com/design/FZhnKBdDPJWpPrbxn3AGQQ).
La consulta de bibliotecas se completó, pero el plan Starter agotó la cuota MCP antes
de guardar los marcos. El archivo quedó vacío: no acredita una revisión visual ni
una sincronización entre Figma y código. No se reutiliza Penpot en esta iteración.

La referencia local está en [desktop-tokens.json](desktop-tokens.json), el código
Compose y las [ocho vistas verificadas](previews/manifest.json) generadas por
`:pc:designPreview`. Los PNG se pueden importar
en Figma cuando vuelva a estar disponible. No son componentes editables de Figma.
El [vector de la marca](../../pc/src/main/resources/qetara-brand.svg) sí se puede
importar como SVG.

## Decisiones visuales

- Fondo gris verdoso suave, paneles blancos, texto azul oscuro y acento petróleo.
- Inter 4.1 incluida en la aplicación, con licencia OFL y sin descarga de fuentes
  durante la ejecución. La fuente de marca original se conserva.
- Navegación lateral persistente, compacta en ventanas pequeñas; etiquetas e iconos
  acompañan el estado seleccionado.
- Archivos y receptor en el área principal, datos de sesión en el panel secundario.
  Las columnas se apilan cuando no hay anchura suficiente.
- Jerarquía común para títulos, tarjetas, campos, acciones y estados de Flash.
- Estados vacíos breves, nombres largos truncados solo donde hay un resumen,
  mensajes completos y metadatos con contraste legible.

## Geometría de la marca

La silueta procede exactamente del vector móvil. Su caja original mide
92 × 64,7794, con proporción **1,420204571:1** y centro `(54,54)`.
La cabecera ajusta uniformemente esa silueta al 78 % del espacio asignado.
Los iconos de aplicación preservan el viewport de 108 × 108 y la escala móvil
0,65625; Windows utiliza una baldosa de esquinas redondeadas en lugar de la máscara
adaptativa que aplica un launcher Android.

Se corrigió el pivote de escala del dibujo: la transformación usa explícitamente
el origen, eliminando desplazamientos de compensación que podían recortar el logo.
PNG, ICO e ICNS de escritorio se regeneraron desde el mismo SVG. Android permanece
fuera del alcance de esta iteración.

## Comprobación

Los resultados y límites de la revisión están en
[DESKTOP_DESIGN_REVIEW-2026-09-11.md](../../docs/DESKTOP_DESIGN_REVIEW-2026-09-11.md).
Esta iteración no reemplaza las validaciones previas del APK, MSI ni receta F-Droid,
que corresponden a otros artefactos.

## Iteración móvil posterior

La GUI Android se revisó después por petición expresa. Véanse la [referencia móvil](MOBILE.md), la [galería](mobile-previews/README.md) y el [informe de validación](../../docs/MOBILE_DESIGN_REVIEW-2026-09-11.md).
