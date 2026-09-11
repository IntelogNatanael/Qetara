# Procedencia de los SVG exportados

Revisión documental y geométrica: 11 de septiembre de 2026. No se modificaron
las formas ni se inspeccionó un archivo activo de Penpot.

El README histórico identifica la exportación desde `Qetara Mobile Design System`.
La comparación con el [repositorio de Circum Icons](https://github.com/Klarr-Agency/Circum-Icons)
identificó los doce recursos de la tabla. La página oficial de
[Penpot sobre Circum](https://penpot.app/penpothub/libraries-templates/circum-icons-pack)
también enlaza el paquete de esa biblioteca. El proyecto upstream identifica
a Klarr Agency como autor y distribuye el código SVG bajo MPL 2.0; no se
atribuye la autoría de los dibujos a quien realizó la exportación en Penpot.

| Archivo local | Fuente upstream fijada |
| --- | --- |
| chat.svg | [svg/chat_1.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/chat_1.svg) |
| check.svg | [svg/circle_check.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/circle_check.svg) |
| folder.svg | [svg/folder_on.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/folder_on.svg) |
| info.svg | [svg/circle_info.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/circle_info.svg) |
| lock.svg | [svg/lock.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/lock.svg) |
| repeat.svg | [svg/repeat.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/a2924cb1ee37b9fa39ef023a36f1c884b3492e9b/svg/repeat.svg) |
| send.svg | [svg/paperplane.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/paperplane.svg) |
| settings.svg | [svg/settings.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/settings.svg) |
| unlock.svg | [svg/unlock.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/unlock.svg) |
| warning.svg | [svg/warning.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/warning.svg) |
| wifi-off.svg | [svg/wifi_off.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/wifi_off.svg) |
| wifi-on.svg | [svg/wifi_on.svg](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/svg/wifi_on.svg) |

La exportación añade grupos y metadatos de Penpot y convierte comandos de
trazado en coordenadas absolutas con otra precisión. La auditoría comparó
361 extremos de segmentos en orden, con tolerancia de 0,004 unidades de un
viewBox de 24 × 24; todos se encontraron en sus SVG exportados correspondientes.
Esto respalda la identificación de las fuentes, pero no afirma identidad
byte a byte ni una prueba completa de equivalencia de curvas.

La fuente `repeat.svg` corresponde a la revisión inicial indicada: una revisión
upstream posterior cambió el trazado. Las revisiones fijadas son las usadas
para la comparación; no se afirma conocer el commit exacto de la descarga
original realizada antes de crear este repositorio.

Licencia: [Mozilla Public License 2.0, texto completo](../../../licenses/Circum-Icons-MPL-2.0.txt),
conservado desde el [LICENSE upstream](https://github.com/Klarr-Agency/Circum-Icons/blob/cec1364b5199f55e946a9a8360385a958b98cc60/LICENSE).
Los SVG exportados son modificaciones de esos recursos y se mantienen bajo
MPL 2.0. Conserva este aviso, el texto de licencia y las fuentes modificables
al redistribuirlos. Esto no cambia la licencia del código propio de Qetara.

No se encontraron referencias a esta carpeta desde los módulos Gradle de la
aplicación. La revisión cubre estos doce SVG; no acredita las bibliotecas
`.penpot` de referencia excluidas de Git ni otros recursos que se incorporen
más adelante.
