# Auditoría de versiones y preparación de F-Droid — 23 de septiembre de 2026

## Resultado

**El número 1.4.0 no identifica una única entrega de Qetara.** Al comenzar la
auditoría, Android y PC declaraban 1.4.0 en las fuentes, pero el PC instalado
conservaba la candidata del 11 de septiembre y existía un APK móvil posterior
con el mismo nombre de versión y código 7. No había un teléfono conectado por
ADB para verificar la instalación móvil actual.

Durante esta revisión las fuentes de trabajo pasaron a **1.4.1, código Android
8**, para distinguir la siguiente entrega. Esto identifica desarrollo nuevo;
no actualiza instalaciones ni convierte los paquetes antiguos en 1.4.1. La
receta F-Droid conserva deliberadamente su commit histórico real hasta que
exista una nueva candidata cerrada y validada.

## Evidencia observada hoy

| Objeto | Versión/identidad | Alcance de la comprobación |
|---|---|---|
| HEAD al iniciar | `cd1ebd845e049adc2030277d841e7e0467d1bcd5` | `git rev-parse HEAD`; cinco commits posteriores al código de RC1 |
| Fuentes iniciales | 1.4.0 / Android 7 | `gradle.properties`; Android y PC leen la versión común desde el proyecto raíz |
| Fuentes durante la revisión | 1.4.1 / Android 8 | Cambio local en `gradle.properties`; aún no representa un commit de entrega |
| Tag local `v1.4.0-rc.1` | `588a92f2617815b5744eeb91a1da463c5c685f90` | Tag anotado resuelto a ese commit; no se movió |
| Propuesta F-Droid | 1.4.0 / Android 7, commit `588a92f…` | YAML y propiedades del commit coherentes entre sí; no describen las fuentes nuevas |
| PC instalado | 1.4.0, candidata RC1 | Registro Windows y versión del EXE; EXE, JAR de PC y JAR del protocolo coinciden con el portable RC1 por SHA-256 |
| APK local Flash final, 12 sep | 1.4.0 / Android 7 | Manifest leído con `aapt2`; firma comprobada con `apksigner` y hash contrastado con el informe anterior |
| Móvil instalado hoy | No determinado | `adb devices -l` no enumeró dispositivos; no se instaló ni abrió una aplicación |
| GitHub | Repositorio `PRIVATE`; release Latest `v1.3.1` | Consulta autenticada de `gh repo view` y `gh release list`; no existe una release 1.4.0 en esa lista |

El MSI instalado está en `%LOCALAPPDATA%/Qetara`. Se leyeron archivos y registro;
no se ejecutó el programa, no se sustituyó su instalación y no se probó hardware.
La lista de GitHub describe releases del repositorio privado, no disponibilidad
pública. Una consulta web anónima no devolvió la página; la conclusión de
privacidad procede de la API autenticada, que sí respondió explícitamente.

### Hashes comprobados

| Archivo | SHA-256 |
|---|---|
| EXE instalado y EXE dentro del portable RC1 | `e01a1447b0120f5a1c72d686ffd2e145e40695dc001f971144d89835775562b7` |
| JAR de PC instalado y dentro del portable RC1 | `29dae3ad90f880da97f93eb8f473ffb588b17b963f58a01c7b4e14e846b7a0db` |
| JAR del protocolo instalado y dentro del portable RC1 | `4bbbe4867d7c96783307f4f56f5e967fc966fb0761a2bba68fe9554fb7981cfa` |
| APK firmado RC1 | `5d9e28651ffdaf0216d0a82c049b287851906065bbc713eefa77d793670e22f6` |
| APK local móvil Flash final del 12 sep | `16f5ab6d3ceffbf5ac14428075b94c61c9fa4ac7343d8f2b00437de5f4e0900a` |
| Certificado del APK Flash final | `5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b` |

El APK móvil final está en
`.local/flash-ux-2026-09-12/Qetara-flash-ux-final.apk`; el portable comparado es
`entregas/Candidata-1.4.0-rc.1/Windows/Qetara-1.4.0-windows-portable.zip`.
Su procedencia de entrega registra el commit de aplicación `588a92f…`.

`META-INF/version-control-info.textproto` dentro del APK RC1 nombra `588a92f…`.
El mismo archivo del APK Flash final nombra
`d7be5c5b57a656072e8c1f8b4c56cc0ca7edcfea`, aunque la documentación registra
cambios Flash posteriores preparados antes de `cd1ebd8`. La etiqueta VCS por sí
sola no demuestra un árbol limpio ni que todos los bytes procedan de ese commit.
Por eso no se atribuye al APK Flash final una reconstrucción limpia de HEAD.

## Paridad y compatibilidad

`app` y `pc` dependen del mismo módulo `protocol`. Su protocolo habitual es
**WDRP 4** y Flash usa **versión 1**. `git diff 588a92f HEAD -- protocol` no
mostró cambios al iniciar esta auditoría. El código del transporte compartido
era, por tanto, idéntico entre esa candidata y HEAD; las fuentes de ambas
interfaces sí cambiaron. La comparación inicial de entradas de producción/build
detectó 38 rutas Android y 22 rutas PC distintas desde RC1.

Esto respalda compatibilidad del protocolo compartido, pero no acredita una
transferencia entre las instalaciones actuales. Tampoco implica igualdad de
funciones: el emparejamiento automático con directorio de confianza y Wi-Fi
Direct corresponden a Android; escritorio utiliza código y PIN manuales para
la sesión habitual. Véase [el contrato de transporte](PROTOCOL.md).

## F-Droid: estado vigente y evidencia histórica

La [validación del 11 de septiembre](FDROID_VALIDATION-1.4.0.md) está respaldada
por registros locales conservados: `readmeta`, `rewritemeta`, lint, escáner de
fuentes, build y escáner del APK finalizaron correctamente con fdroidserver
2.4.5. Se releyeron los registros y su resumen; **no se repitió hoy esa build**.
El ensayo utilizó un origen Git local, herramientas configuradas en WSL y el
commit `588a92f…`. No fue el servidor oficial ni su CI.

Los requisitos siguientes se contrastaron con documentación oficial consultada
el 23 de septiembre de 2026:

- Las fuentes deben ser públicas, la aplicación y sus dependencias deben cumplir
  los criterios FLOSS, y el proceso de construcción debe usar herramientas
  libres. Proceder de Maven no demuestra por sí solo una licencia admisible.
  Qetara mantiene MIT y repositorios Google Maven/Maven Central; los inventarios
  y avisos existentes requieren revisión de nuevo sobre la candidata final.
  [Política de inclusión](https://f-droid.org/docs/Inclusion_Policy/).
- La receta necesita un commit completo real y versiones que coincidan con el
  APK. El versionado indirecto en `gradle.properties` exige `UpdateCheckData`
  si se activan comprobaciones automáticas por tags. Aquí permanecen
  desactivadas durante la preparación.
  [Referencia de metadata](https://f-droid.org/docs/Build_Metadata_Reference/).
- Para conservar la clave del desarrollador, F-Droid necesita el APK de
  referencia, su clave esperada y una reconstrucción que permita verificar
  la firma copiada. Falta la URL pública versionada de la nueva candidata;
  `AllowedAPKSigningKeys` solo no completa ese proceso.
  [Compilaciones reproducibles](https://f-droid.org/docs/Reproducible_Builds/).
- La reproducibilidad es una práctica recomendada, no un requisito universal
  de inclusión. F-Droid puede firmar builds con su propia clave, pero cambiar
  la clave afecta a la actualización de instalaciones existentes. La decisión
  de Qetara sigue siendo conservar la clave del desarrollador. La revisión y
  aceptación de una propuesta corresponden a F-Droid.
  [Guía de envío](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

La preparación previa incluye licencia MIT, avisos de terceros, inventario de
dependencias, Gradle 9.1.0 con checksum, Build Tools 36.0.0 y firma por variables
de entorno. Esta auditoría no actualizó dependencias ni extendió la aprobación
histórica de escáneres a los archivos modificados.

### Recurso GitHub Invertocat

La revisión inicial encontró el mismo PNG Invertocat en Android y PC, con un
aviso que reconocía permiso contextual y falta de URL de descarga original.
El [Brand Toolkit de GitHub](https://brand.github.com/foundations/logo) permite
usarlo como botón a un perfil/proyecto, pero reserva sus derechos y restringe
modificaciones. No ofrece una licencia libre general del dibujo.

La política vigente de F-Droid admite licencias menos restrictivas para recursos
no funcionales redistribuibles y legales. Por ello, el logo no demuestra por sí
solo un rechazo automático. Sustituirlo por un icono de código libre y conservar
el enlace elimina la dependencia de ese permiso contextual y la incertidumbre
de procedencia. Esta propuesta se trasladó a la integración de Android y PC;
los binarios RC1 siguen siendo históricos y contienen el recurso anterior.

## Preparación añadida y comprobaciones de esta auditoría

- [`scripts/audit-release.ps1`](../scripts/audit-release.ps1) produce JSON sin
  compilar ni tocar instalaciones. Compara versión, código Android, commit y
  entradas de aplicación/build con la receta. `-RequireCurrentRecipe` devuelve
  1 cuando hay divergencias; el resultado actual es deliberadamente 1 porque
  las fuentes 1.4.1/8 no son la receta histórica 1.4.0/7. Un 0 solo confirma
  coherencia local; no significa aprobación F-Droid o publicación preparada.
- El README y las notas de mantenimiento de F-Droid distinguen la propuesta
  histórica y el procedimiento para cerrar la siguiente candidata. No se
  inventó un commit ni una URL y se conservó la huella de firma.
- Se añadió ficha de texto en inglés y español en
  `fastlane/metadata/android/{en-US,es-ES}`. Nombres, resúmenes y descripciones
  pasan límites de 30/80/4000 caracteres; las descripciones tienen HTML
  estructuralmente válido. No se añadieron capturas ni iconos de otra versión.
- Se ejecutó el guard con PowerShell normal y Windows PowerShell 5 en modo
  estricto; la receta anterior se rechaza como vigente. `ruamel.yaml` del
  entorno F-Droid aislado pudo leer el YAML actualizado y verificar su pin
  histórico. Esto es parseo de YAML, no una nueva ejecución de `fdroid lint`.
- `git diff --check` no detectó errores de espacios en los cambios auditados.

## Pendientes concretos

1. Cerrar las modificaciones de la nueva candidata y generar los paquetes de
   Android y PC con su versión y procedencia comunes. Repetir las verificaciones
   de esa revisión; no reasignar los hashes o resultados de RC1.
2. Comprobar qué APK está instalado cuando el teléfono esté disponible y
   verificar el par final en los dispositivos previstos. Hoy se acredita el
   desfase del PC instalado; la instalación móvil actual permanece desconocida.
3. Preparar icono, capturas y notas por código de versión de la candidata final.
   Las fichas de texto ya están disponibles para revisión.
4. Cuando exista un commit final, actualizar la receta con ese commit y repetir
   escáneres, build limpia y comparación de firma. El guard debe pasar antes de
   presentar esa receta como correspondiente a las nuevas fuentes.
5. Publicar las fuentes y el APK de referencia cuando se autorice esa acción;
   validar sus URL y someter la propuesta a fdroiddata. En esta auditoría no se
   cambió la visibilidad, publicó una release, creó un tag ni envió una solicitud.

Las comprobaciones integradas posteriores de interfaz, compilación y pruebas
de la revisión 1.4.1 deben registrarse en su informe propio. Este documento
conserva la evidencia de versiones observada y los límites de la auditoría de
distribución.
