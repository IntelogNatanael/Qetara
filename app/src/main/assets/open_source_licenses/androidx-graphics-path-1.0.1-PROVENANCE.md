# Procedencia nativa de AndroidX Graphics Path 1.0.1

Revisión documental y de artefactos del 11 de septiembre de 2026. Componente: `androidx.graphics:graphics-path:1.0.1`. Esta revisión identifica fuentes, avisos y dependencias observables de sus cuatro bibliotecas nativas; no certifica una reconstrucción con bytes idénticos ni sustituye la inspección del APK final.

## Fuentes oficiales fijadas

Las [notas oficiales de AndroidX](https://developer.android.com/jetpack/androidx/releases/graphics#graphics-path-1.0.1) sitúan la publicación en el 1 de mayo de 2024 y describen mejoras de las opciones del compilador. Su [enlace de cambios de la versión](https://android.googlesource.com/platform/frameworks/support/+log/4fcd99eacd92d7c73fb1d3580fd423ed7704a98a..8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path) termina en la revisión `8a05a22af450d589ef911d772a001a49dcb05b71`. Se inspeccionó ese árbol, no la rama actual.

- [Directorio C++ completo](https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/src/main/cpp/), incluidos sus tres archivos `math/`.
- [Configuración Gradle](https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/build.gradle).
- [Licencia Apache 2.0 del repositorio](https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/LICENSE.txt).
- [AAR oficial](https://dl.google.com/dl/android/maven2/androidx/graphics/graphics-path/1.0.1/graphics-path-1.0.1.aar), [fuentes publicadas](https://dl.google.com/dl/android/maven2/androidx/graphics/graphics-path/1.0.1/graphics-path-1.0.1-sources.jar) y [metadatos Gradle](https://dl.google.com/dl/android/maven2/androidx/graphics/graphics-path/1.0.1/graphics-path-1.0.1.module).

El JAR de fuentes publicado contiene cuatro archivos Kotlin y no incluye C++. Por ello se revisaron las fuentes nativas del árbol oficial fijado. Los metadatos Gradle publicados permiten contrastar tamaño y SHA-256 del AAR y del JAR de fuentes; ambos coinciden. `versionMetadata.json` enumera la incorporación de clases/métodos a la API; no contiene una atestación del commit de compilación. Los archivos descargados de Gitiles se contrastaron además con los identificadores Git blob de sus directorios.

## Avisos identificados

Los tres archivos compilados (`Conic.cpp`, `PathIterator.cpp`, `pathway.cpp`) y los siete encabezados propios llevan Apache License 2.0. Las líneas originales de atribución, conservadas aquí sin reasignar autoría, son:

- `Copyright 2022 The Android Open Source Project` — archivos principales.
- `Copyright (C) 2006 The Android Open Source Project` — también en `Conic.cpp`.
- `Copyright 2013 The Android Open Source Project` — `math/TVecHelpers.h` y `math/vec2.h`.
- `Copyright (C) 2017 The Android Open Source Project` — `math/compiler.h`.

Los encabezados matemáticos usan el espacio de nombres `filament::math`; su licencia observada en este árbol es Apache 2.0. El texto [Apache 2.0](Apache-2.0.txt) ya acompaña a Qetara. No se encontró un archivo NOTICE adicional en el directorio del módulo, `cpp/`, `math/`, el AAR o su `classes.jar`.

## Skia y biblioteca estándar de C++

`Path.h` declara únicamente los campos necesarios para acceder a las estructuras de `android.graphics.Path`; su comentario menciona las estructuras Skia del sistema. `CMakeLists.txt` compila solo los tres archivos propios indicados, sin importar fuentes o enlazar una biblioteca Skia. No se identificó una distribución adicional de Skia por este componente. Los avisos Skia de la distribución Windows corresponden a Skiko y conservan su alcance propio.

`build.gradle` usa `-nostdlib++`, `-fno-exceptions` y `-fno-rtti`. Las cuatro ELF tienen exclusivamente `libm.so`, `libdl.so` y `libc.so` en `DT_NEEDED`; el AAR no incluye `libc++_shared.so`. Esto respalda que no se distribuye un runtime libc++ por esta biblioteca. No permite afirmar que ningún fragmento de encabezado C++ haya quedado compilado: se incluyen, entre otros, `<cmath>`, `<new>` y `<type_traits>`.

La cadena de compilador de las cuatro ELF identifica Android Clang 14.0.7, build 9352603, basado en r450784d1, revisión LLVM `4c603efb0cca074e9238af8b4106c30add4418f6`. La [licencia libc++ de esa revisión](https://android.googlesource.com/toolchain/llvm-project/+/4c603efb0cca074e9238af8b4106c30add4418f6/libcxx/LICENSE.TXT) incluye Apache 2.0 con la excepción LLVM para fragmentos incorporados a objetos al compilar. Esa excepción dispensa las condiciones 4(a), 4(b) y 4(d) para dichos fragmentos. No se identificó una necesidad de aviso libc++ adicional por el uso observado; no se está redistribuyendo su código fuente ni una biblioteca de runtime independiente.

## Identidad de artefactos

| Archivo | Bytes | SHA-256 |
| --- | ---: | --- |
| `graphics-path-1.0.1.aar` | 39380 | `8ca4032b6d79b351f0b59ad4b580eddbb9423e1652f7c958830687f1eee2ec03` |
| `graphics-path-1.0.1-sources.jar` | 9908 | `9f1b5995b9577a8876525c3411ebb2a49f9ef0e875f6aec3059e807596dc6ca6` |
| `graphics-path-1.0.1.module` | 4016 | `3f6fc7e96f8a1fd21045da7f2e332aef528aa1f56b6455fb8f25043aafa0e1b8` |
| `graphics-path-1.0.1-versionMetadata.json` | 2032 | `6f54e010c759a56cfe1a2eeb1a0edfd494184791e7b5f370b3ce064e293c2ce1` |

Las propiedades del AAR declaran `minCompileSdk=34`, formato AAR 1.0 y sin core-library desugaring. No contienen una correspondencia criptográfica entre binarios y commit fuente.

| ABI | Bytes de libandroidx.graphics.path.so | SHA-256 |
| --- | ---: | --- |
| `arm64-v8a` | 10096 | `41e9a793c43a0f4fddb19e33f346bace464f30f888ba7b9eaf96294ea115bfb6` |
| `armeabi-v7a` | 7252 | `41399eba6fc2a60f6f14642375c1824f3cf25eb8fec7397d753730a3ceda3e2b` |
| `x86` | 9284 | `eb0570b41fd3bff25d8204a967c03bd7550719e768b791f680cc40cbe35f29af` |
| `x86_64` | 10760 | `4e56c996f13670e70082658de7880c4020eabf4f25e43387f88ed78a713fc9f0` |

## Identidad de fuentes y licencias inspeccionadas

Los hashes siguientes corresponden a los bytes originales decodificados de Gitiles, sin reformatearlos.

| Archivo | SHA-256 |
| --- | --- |
| `src/main/cpp/CMakeLists.txt` | `87a8967586e828d2b1cc6c5745d09f30466036bffbcd780b378c75239d90b173` |
| `src/main/cpp/Conic.cpp` | `49b3da93aa43480cf9cd481590be48705cb9340c52e5d09635efbd0ece07be83` |
| `src/main/cpp/Conic.h` | `cd315ad7c8d030c72e96893e9a0feec463d72d2066209a203dedcf0925398a6f` |
| `src/main/cpp/libandroidx.graphics.path.map` | `bc338abd36eeb27059572903ad167cf4231a36ca92be4eae3574501aa83f0d8c` |
| `src/main/cpp/math/compiler.h` | `d53e85383580e5dbf81cbcb24426b0fe1bf419b4837540242dbdca3f1df4cf66` |
| `src/main/cpp/math/TVecHelpers.h` | `1b0d6b06c5bbd6b438f9d8440ac78cf93fb8a713761badc59a4412b6457c1014` |
| `src/main/cpp/math/vec2.h` | `09e1a8eb3f8fdf9c1071500f1a4933f67caeb6d0f765d5c44ba968370de9fcb1` |
| `src/main/cpp/Path.h` | `4ee841338063d8c6f71ff4c655ecf52559f6c6df9af79835388dae1ebcd8b982` |
| `src/main/cpp/PathIterator.cpp` | `3705946b43bf894e013f46ccc940f9923f43285340f8fc2128f95c59238ec7f8` |
| `src/main/cpp/PathIterator.h` | `a16e99b0273fb7205607eb0cf51fc7be518a55f3446a304f92ecab353e77be6f` |
| `src/main/cpp/pathway.cpp` | `4be68d6fdb6376e95560f7c49a79aa0cd97218d758cf11ea3230ac9124485800` |
| `src/main/cpp/scalar.h` | `b1d2bba80a4c9272d84473b6f09f700972b016f7579842d3a87bdbbdb2af1edc` |
| `build.gradle` | `dc6b0dfaa8393825da05eac9f6cd2ace91e7a85e6bcf6e80adb256caa25a339a` |
| `AndroidX LICENSE.txt` | `809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0` |
| `LLVM libcxx/LICENSE.TXT` | `539dd7aed86e8a4f12cbdd0e6c50c189c7d74847e4fecc64ce2c6ee3a01da38b` |

No se modificaron dependencias ni se reconstruyeron estas bibliotecas nativas. Una actualización de versión, ABI o proveedor requiere volver a contrastar estos avisos y artefactos.
