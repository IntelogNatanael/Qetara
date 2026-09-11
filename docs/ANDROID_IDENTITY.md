# Identidad y firma Android

Decisión de preparación pública, 11 de septiembre de 2026.

Qetara 1.4.0 utiliza `io.github.intelognatanael.qetara`, basado en la cuenta GitHub `IntelogNatanael`. Este espacio de nombres no requiere comprar un dominio. No se ha reservado `experiencelab.com` ni se atribuye al proyecto su propiedad.

La versión privada anterior utiliza `com.example.wifidrop`. Android considera que son dos aplicaciones diferentes, aunque compartan nombre visible y certificado. Pueden coexistir; no se trasladan automáticamente preferencias, historial, claves de emparejamiento ni archivos privados. Antes de retirar la anterior, exporta los archivos que quieras conservar. No se automatiza su desinstalación.

El `namespace` y los paquetes Kotlin conservan `com.example.wifidrop`. Son nombres internos de clases. El identificador instalado y la autoridad `${applicationId}.fileprovider` son los nuevos; las pruebas instrumentadas deben ejecutarse contra `io.github.intelognatanael.qetara.test/com.example.wifidrop.ReceivedFileOpenInstrumentation`.

Se conserva la clave de distribución existente. La huella SHA-256 del certificado esperada, verificada en la entrega 1.3.1, es:

```text
5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b
```

La huella es pública; no permite firmar aplicaciones. El almacén y sus contraseñas permanecen fuera del repositorio y de los paquetes de entrega. Para las siguientes actualizaciones de la nueva aplicación se mantendrán este identificador y certificado, aumentando `qetaraVersionCode`.

El procedimiento de la candidata compila primero sin credenciales y firma una copia del APK en un proceso separado mediante `apksigner`, con contraseñas pasadas por variables de entorno (`env:`), nunca como argumentos literales. Se comprueban la firma y su huella después de firmar. El responsable debe conservar una copia de seguridad recuperable de la clave; esta preparación no certifica una copia de seguridad que no se haya comprobado.

Para F-Droid se propone conservar la firma del desarrollador mediante reconstrucción reproducible. Su servicio debe reconstruir el APK y validar la firma copiada; no recibe nuestra clave privada. La propuesta no demuestra por sí sola reproducibilidad ni aceptación en el catálogo. Si no se consigue reproducir, la alternativa de firma de F-Droid requerirá otra decisión antes de publicar porque afecta a las actualizaciones entre canales.

Referencias: [identificador y namespace Android](https://developer.android.com/build/configure-app-module), [apksigner y contraseñas por entorno](https://developer.android.com/tools/apksigner), [reconstrucción y firma en F-Droid](https://f-droid.org/docs/Reproducible_Builds/).
