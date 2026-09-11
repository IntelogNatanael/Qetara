# Avisos de software de terceros

Qetara utiliza software y recursos de terceros bajo sus respectivos términos. La licencia MIT de [Qetara](LICENSE) se aplica a su propio código; no sustituye las licencias de estos componentes.

El inventario base del 6 de septiembre de 2026 combina los archivos Gradle, los POM de Maven descargados para la compilación, el modelo de bibliotecas del artefacto Android **debug** y los JAR presentes en la distribución Windows generada. El [inventario verificable](licenses/dependency-inventory.json) conserva coordenadas, versiones, licencia declarada, URL del POM y SHA-256 del POM inspeccionado: 91 componentes en el modelo Android, 33 JAR de terceros en Windows y 119 coordenadas distintas entre ambos. El modelo Android incluye herramientas de inspección habilitadas para debug. No representa un inventario completo de cada biblioteca nativa incrustada, del SDK Android ni del runtime Java.

Flash reutiliza Noise Java y las bibliotecas ya incluidas. La verificación de la entrega 1.3.0 contrasta el modelo Android y los JAR Windows con este inventario; no se atribuye una nueva auditoría de licencias a un cambio de versión del producto.

El [inventario Android release del 11 de septiembre de 2026](licenses/android-release-dependency-inventory.json), generado desde el modelo de bibliotecas de lint release para Qetara 1.4.0, contiene 88 coordenadas. Los hashes SHA-256 de sus 88 POM resueltos coinciden con los del inventario anterior. Esta comprobación verifica declaraciones e identidad de los POM; no constituye una auditoría exhaustiva del código nativo incrustado ni amplía el alcance de las licencias declaradas por sus autores.

## Aplicación y bibliotecas de ejecución

| Componente | Versión observada | Uso | Aviso/licencia |
| --- | --- | --- | --- |
| AndroidX Activity | 1.13.0 | Interfaz y ciclo de vida Android | Apache 2.0 |
| AndroidX Core / Core KTX | 1.18.0 | Integración Android | Apache 2.0 |
| AndroidX Core Splashscreen | 1.2.0 | Inicio Android | Apache 2.0 |
| AndroidX AppCompat | 1.7.1 | Compatibilidad Android | Apache 2.0 |
| AndroidX Graphics Path | 1.0.1; cuatro ABI Android | Consulta nativa de segmentos de Path | Apache 2.0; AOSP 2006, 2013, 2017, 2022; [fuentes y procedencia nativa](licenses/androidx-graphics-path-1.0.1-PROVENANCE.md) |
| Jetpack Compose (BOM 2026.01.00) | UI 1.10.1; Material 3 1.4.0; iconos 1.7.8 | Interfaz Android | Apache 2.0 |
| Kotlin standard library | Android 2.2.20; Windows 2.2.10, adaptadores JDK 2.1.21 | Lenguaje y biblioteca estándar | Apache 2.0 |
| Kotlin Coroutines | Android 1.11.0; Windows 1.8.0 | Concurrencia | Apache 2.0 |
| Compose Multiplatform/Desktop | 1.8.2 | Interfaz Windows | Apache 2.0 |
| Skiko | 0.9.4.2 | Integración gráfica nativa Windows | Apache 2.0; [NOTICE de Skiko](licenses/skiko-0.9.4.2-NOTICE.txt) |
| Skia | revisión a00c390e98, paquete m132-a00c390e98-1 declarado por Skiko | Motor gráfico nativo | [Licencia BSD de Skia](licenses/skia-a00c390e98-LICENSE.txt); sus componentes conservan términos propios |
| Noise Java, `kr.jclab:noise-java` | 0.0.1 | Transporte cifrado Android y Windows | POM: Apache 2.0; código de origen: avisos MIT y dominio público preservados [aquí](licenses/noise-java-0.0.1-NOTICES.txt) |
| Noto Sans Syriac | archivos Regular y Black incluidos en design/brand/fonts | Recursos tipográficos de marca | [SIL Open Font License 1.1](design/brand/fonts/OFL.txt), Copyright 2022 The Noto Project Authors |
| Circum Icons, Klarr Agency | doce SVG exportados en design/penpot/extracted-icons | Recursos de diseño conservados en el código fuente | [MPL 2.0](licenses/Circum-Icons-MPL-2.0.txt); [procedencia por archivo](design/penpot/extracted-icons/PROVENANCE.md) |
| GitHub Invertocat | PNG idéntico en Android y PC | Botón de enlace al perfil del desarrollador | [Aviso y permiso contextual de uso](licenses/GitHub-Invertocat-NOTICE.txt); no es un recurso bajo MIT |

Las bibliotecas AndroidX, Kotlin y Compose incluyen dependencias transitivas de sus respectivas familias. Las versiones concretas del artefacto inspeccionado se detallan en el inventario; no se deducen únicamente de las versiones declaradas directamente en Gradle. También se incluyen las declaraciones Apache 2.0 de JetBrains Annotations, JSpecify y Guava ListenableFuture (esta última heredada de su POM padre).

El texto de [Apache License 2.0](licenses/Apache-2.0.txt) acompaña este proyecto. Los proyectos y sus fuentes se identifican mediante los enlaces declarados por sus POM dentro del inventario.

## Código nativo AndroidX Graphics Path

`androidx.graphics:graphics-path:1.0.1` incorpora `libandroidx.graphics.path.so` para cuatro ABI Android. La [revisión de fuentes y hashes](licenses/androidx-graphics-path-1.0.1-PROVENANCE.md) fija el árbol oficial `8a05a22af450d589ef911d772a001a49dcb05b71` enlazado desde las notas de la versión. Los archivos C++ y encabezados propios, incluidos los auxiliares `filament::math`, llevan Apache 2.0 y conservan estas atribuciones originales:

- Copyright 2022 The Android Open Source Project.
- Copyright (C) 2006 The Android Open Source Project — también en `Conic.cpp`.
- Copyright 2013 The Android Open Source Project — `math/TVecHelpers.h` y `math/vec2.h`.
- Copyright (C) 2017 The Android Open Source Project — `math/compiler.h`.

La revisión no identificó una biblioteca Skia redistribuida por este componente: sus estructuras acceden al Path del sistema Android. La configuración `-nostdlib++` y las dependencias ELF respaldan la ausencia de un runtime libc++ enlazado; la procedencia documenta también la excepción LLVM aplicable a fragmentos de encabezados compilados. No se acredita una reconstrucción nativa con bytes idénticos.

## Recursos gráficos y tipográficos

La revisión del 11 de septiembre de 2026 identificó los doce SVG exportados de Penpot con recursos de Circum Icons y conservó su MPL 2.0. La [tabla de procedencia](design/penpot/extracted-icons/PROVENANCE.md) fija las revisiones comparadas y distingue la transformación del SVG de la autoría original. No se encontraron referencias a esta carpeta desde los módulos de aplicación; forma parte del código fuente distribuido. Esta revisión no acredita los paquetes `.penpot` de referencia excluidos de Git.

Los TTF Noto Sans Syriac contienen metadatos de la versión 3.000 y su aviso OFL 1.1; su [procedencia e inventario](design/brand/fonts/PROVENANCE.md) conserva los hashes locales. La licencia completa acompaña las fuentes y también se incorpora a los avisos Android y PC.

El Invertocat se utiliza para enlazar a `https://github.com/IntelogNatanael`. El [Brand Toolkit oficial de GitHub](https://brand.github.com/foundations/logo) contempla el uso como botón hacia un perfil o proyecto GitHub. Ese permiso no es una licencia libre general del logotipo ni permite presentarlo como marca propia o atribuir respaldo de GitHub. No se conservó una URL de descarga original de los PNG; el [aviso](licenses/GitHub-Invertocat-NOTICE.txt) registra ese límite. No se afirma aceptación por una tienda de aplicaciones.

## Avisos de Noise Java

El POM oficial de `kr.jclab:noise-java:0.0.1` declara Apache 2.0. La inspección adicional del archivo de fuentes oficial encontró el aviso MIT de **Copyright (C) 2016 Southern Storm Software, Pty Ltd.** en las clases del protocolo y de criptografía. `Curve448.java` conserva avisos MIT de **Copyright (c) 2011 Stanford University** y **Copyright (c) 2014 Cryptography Research, Inc.**. RijndaelAES y NewHope conservan declaraciones de dominio público. El [archivo de avisos](licenses/noise-java-0.0.1-NOTICES.txt) reproduce esas declaraciones originales; no se ha reclasificado todo el código a partir del POM.

## Runtime Java de la distribución Windows

La distribución Windows utiliza Eclipse Temurin **21.0.12.1+1 LTS**, seleccionado desde la publicación estable oficial para Windows x64 y reducido con los módulos necesarios para Qetara. Se conserva íntegro su directorio `runtime/legal/`, que incluye los textos GPL v2, las excepciones indicadas por el proveedor y los avisos por módulo de componentes como FreeType, HarfBuzz, ICU, libpng, JPEG, zlib y otros. Esos términos pertenecen al runtime y sus componentes; no se reemplazan por la licencia MIT de Qetara ni por esta tabla. El archivo `runtime/release` identifica la versión y los módulos presentes en cada distribución concreta.

Las fuentes correspondientes acompañan la entrega en `Qetara-third-party-source`: archivo oficial completo de OpenJDK `OpenJDK21U-jdk-sources_21.0.12.1_1.tar.gz`, snapshot de los scripts Temurin, licencias, metadatos y comprobaciones SHA-256. El commit de OpenJDK es `1c417fbfc2f70ab03a565b0af0a5a3c6f5e15ad6`; el de los scripts de compilación es `e6ba7dec3d07654074559310376a3ae89da5f4ac`. Al redistribuir el runtime, se debe acompañar ese paquete de fuentes y conservar sus avisos, junto con los de `runtime/legal/`. La [publicación oficial de Temurin](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1) permite contrastar la procedencia; este enlace no sustituye el paquete de fuentes entregado.

## Herramientas de desarrollo y pruebas

Estas herramientas se usan para producir o comprobar la aplicación; no deben confundirse con componentes de ejecución añadidos a propósito a la distribución final.

| Herramienta | Versión declarada | Licencia declarada |
| --- | --- | --- |
| Gradle Wrapper / Gradle | 9.1.0 | Apache 2.0; la distribución Gradle conserva sus propios avisos |
| Android Gradle Plugin | 9.0.0 | Apache 2.0 |
| Kotlin JVM y Compose Compiler plugins | 2.2.10 | Apache 2.0 |
| Compose Gradle plugin | 1.8.2 | Apache 2.0 |
| JUnit | 4.13.2 | Eclipse Public License 1.0 |
| Kotlin Test / Kotlin Test JUnit | 2.2.10 | Apache 2.0 |
| Hamcrest Core, transitivo de JUnit | 1.3 | BSD, según su POM padre |
| Compose UI tooling | 1.10.1 | Apache 2.0; dependencia Android debug |

## Conservación y actualización

Las distribuciones deben llevar este documento y los archivos de `licenses/`, además de los avisos originales presentes en sus bibliotecas y en `runtime/legal/` cuando incluyan Java. Los recursos Noto deben conservar su archivo OFL. Al cambiar dependencias o generar otra plataforma, se deben volver a inspeccionar los artefactos realmente resueltos y los avisos nativos de esa plataforma; esta instantánea no acredita binarios que todavía no se han construido.

Fuentes adicionales de la revisión: [Skiko v0.9.4.2](https://github.com/JetBrains/skiko/tree/v0.9.4.2), [versión de Skia declarada por Skiko](https://github.com/JetBrains/skiko/blob/v0.9.4.2/skiko/gradle.properties), [fuentes publicadas de Noise Java 0.0.1](https://repo.maven.apache.org/maven2/kr/jclab/noise-java/0.0.1/noise-java-0.0.1-sources.jar).
