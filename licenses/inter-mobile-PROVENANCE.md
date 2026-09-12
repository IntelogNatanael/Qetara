# Inter 4.1 · Procedencia de los recursos Android

Comprobación local del 11 de septiembre de 2026. Los cuatro recursos Android se
copiaron desde los TTF de escritorio de Qetara. Se contrastaron sus tamaños y
SHA-256: cada par es idéntico byte a byte. Cambió únicamente el nombre externo
para usar `inter_*.ttf` bajo `app/src/main/res/font/`.

## Origen conservado

El [registro de escritorio](../pc/src/main/resources/fonts/PROVENANCE.md)
identifica los TTF estáticos sin modificar de la
[publicación oficial Inter 4.1](https://github.com/rsms/inter/releases/tag/v4.1).
El archivo de distribución es `Inter-4.1.zip`, de 33.707.794 bytes, descargado
desde `https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip`.
Su SHA-256 registrado es
`9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`.

Ese hash del ZIP es un registro local de integridad: la API de la publicación
no proporcionó un digest. Esta comprobación móvil compara los archivos locales
con los de escritorio; no declara una descarga o verificación remota nueva.

## Archivos y correspondencia

Los nombres de escritorio son también los de las entradas `extras/ttf/` del ZIP.

| Recurso Android | Archivo de escritorio/origen | Peso | Bytes | SHA-256 |
| --- | --- | ---: | ---: | --- |
| `inter_regular.ttf` | `Inter-Regular.ttf` | 400 | 411640 | `40d692fce188e4471e2b3cba937be967878f631ad3ebbbdcd587687c7ebe0c82` |
| `inter_medium.ttf` | `Inter-Medium.ttf` | 500 | 417300 | `97ad806f526e41546d46365bb3a393145f75b7b1568913db74549ad8b8dba872` |
| `inter_semibold.ttf` | `Inter-SemiBold.ttf` | 600 | 419744 | `78a843fade9d4612a5567302fb595b56976eb5fcebf4fea5a5912d638bafcde3` |
| `inter_bold.ttf` | `Inter-Bold.ttf` | 700 | 420428 | `288316099b1e0a47a4716d159098005eef7c0066921f34e3200393dbdb01947f` |

La igualdad binaria confirma que no se cambiaron nombres internos, tablas,
glifos ni subconjuntos al incorporarlos a Android.
[P2pRouteTheme.kt](../app/src/main/java/com/example/wifidrop/P2pRouteTheme.kt)
asocia esos recursos con Normal, Medium, SemiBold y Bold mediante `FontFamily`.
El cambio afecta a la tipografía de interfaz; no modifica la geometría del
símbolo de la aplicación ni reemplaza los recursos de marca Noto conservados.

## Licencia y atribución incluidas

Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter).
Inter conserva SIL Open Font License 1.1. Su texto completo está en
[licenses/Inter-OFL-1.1.txt](Inter-OFL-1.1.txt) y en
[el asset Android](../app/src/main/assets/open_source_licenses/Inter-OFL-1.1.txt).
Ambos coinciden con `pc/src/main/resources/fonts/OFL.txt`:

`262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a`

Además se incorpora el texto íntegro, incluida la atribución, como bloque Inter
al final de
[NOTICES.txt Android](../app/src/main/assets/open_source_licenses/NOTICES.txt).
[P2pOpenSourceLicensesDialog.kt](../app/src/main/java/com/example/wifidrop/P2pOpenSourceLicensesDialog.kt)
lee ese archivo agregado: conservar solo el asset de licencia individual no
bastaría para que ese diálogo incluyera el texto.

La revisión aquí registrada acredita los archivos del árbol de trabajo y los
avisos conservados. El empaquetado y la visualización del aviso dentro de la
aplicación requieren la validación del nuevo APK; no se atribuyen a esta
iteración las pruebas de la candidata anterior.
