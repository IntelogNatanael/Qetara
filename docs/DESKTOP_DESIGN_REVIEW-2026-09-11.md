# Revisión visual de escritorio · 11 de septiembre de 2026

## Resultado y alcance

GUI Compose Desktop renovada: navegación lateral adaptable, jerarquía tipográfica
Inter, tarjetas blancas sobre fondo neutro, estados y controles coherentes,
selección múltiple más compacta, Mensajes con conexión lateral o plegable y Flash
con acción principal fija. El logo mantiene la silueta móvil y se corrigió su
centrado. Iconos de escritorio y avisos de la fuente incluidos.

No se modificó la aplicación Android en esta iteración ni se utilizaron teléfonos,
emuladores o ADB. Los cambios Android que ya había en el árbol pertenecen a la
validación anterior. Se conservaron los callbacks, validaciones y restricciones
de transferencia existentes. Los nuevos estados plegados son solo presentación.

## Comprobaciones

Comando final ejecutado con JDK 21.0.12.1+1 y dependencias locales:

```powershell
.\gradlew.bat -PqetaraDesktopOnly=true :pc:test :pc:designPreview :pc:createDistributable --offline --console=plain
```

- Compilación y distribución de escritorio: PASS.
- Pruebas: **57 correctas**, 0 fallos, 0 errores; 2 omitidas por requerir
  dispositivos/red de pruebas (`FlashEmulatorSocketTest`, `FlashLanUdpTest`).
- Ocho renders de los componentes reales: Compartir vacío/con selección y Flash
  apagado/preparado, a **1160 × 800** y **800 × 620**, densidad 1.
- Los renders usan archivos y datos ficticios, transporte Flash en memoria y
  ninguna conexión de red. Se conservaron PNG y hashes en
  [design/figma/previews](../design/figma/previews/manifest.json).
- Recorrido en Windows de la compilación nueva: Compartir, Recibir, Mensajes,
  Actividad, Ajustes, diálogo de licencias y Flash apagado. Se verificaron la
  navegación, el desplazamiento, Mostrar/Ocultar datos en Mensajes y el acceso al
  editor. Flash mantuvo la acción visible con contenido desplazable en una ventana
  próxima al mínimo: captura lógica 798 × 617, incluida la barra del sistema.
- La recepción habitual y Flash permanecieron desactivados durante el recorrido.
  La búsqueda normal que ejecuta la aplicación al iniciar no se cambió.
- Comparación estática independiente: no se perdieron callbacks ni condiciones
  de bloqueo. `git diff --check`: PASS.

## Mejoras surgidas de la revisión

- El pivote de escala del logo provocaba desplazamiento y recorte; ahora se usa
  el origen explícito y una escala uniforme, sin offsets de compensación.
- La zona de selección baja de 126 a 80 dp al tener archivos, para dejar más
  espacio al receptor y a la acción. El texto explica que otra selección añade
  archivos.
- Mensajes prioriza la conversación y conserva sus datos de conexión en una
  columna lateral o en un bloque plegable. Los mensajes completos ya no se
  truncan a dos líneas.
- Flash conserva visible el estado de recepción habitual, además de su propio
  estado, y distingue volver a Qetara de desactivar Flash.
- Se corrigió el contraste de autor/hora del chat y del texto de licencias.

Contrastes calculados con colores opacos: texto principal/blanco 14,64:1;
secundario/blanco 5,98:1; secundario/fondo 5,51:1; petróleo/fondo suave 5,54:1;
blanco/petróleo 6,21:1. Son comprobaciones de esos pares concretos, no una auditoría
completa de accesibilidad.

## Referencia y límites

[Referencia de diseño y tokens](../design/figma/README.md).
Figma permitió crear el archivo, pero agotó la cuota MCP Starter antes de guardar
marcos. No se declara alineación con una maqueta guardada en Figma. Los PNG locales
se pueden importar; no son componentes editables. El símbolo sí se conserva en SVG.

La revisión visual cubre los estados descritos; no constituye una nueva validación
de transferencias reales, UDP, aprobación Flash, MSI ni F-Droid. No se generaron
APK o MSI, no se instaló un MSI nuevo y no se publicó una versión. La distribución
para revisar está en `pc/build/compose/binaries/main/app/Qetara/` y corresponde al
árbol de trabajo de esta iteración, no a los artefactos de publicación previos.
