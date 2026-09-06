# Contribuir a Qetara

Gracias por ayudar a que compartir entre dispositivos resulte más sencillo.

## Preparar el entorno

Instala JDK 17 o superior y Android SDK plataforma 36. Clona el repositorio, configura ANDROID_HOME y ejecuta scripts/verify.ps1 en Windows. En otros sistemas, usa las tareas equivalentes de Gradle indicadas en README.md.

Trabaja en una rama propia. No incluyas claves de firma, local.properties, archivos recibidos, registros con datos personales ni directorios build/. Las referencias externas de diseño sin licencia verificada se mantienen fuera del repositorio.

## Proponer un cambio

Describe la tarea que hoy resulta difícil, un ejemplo concreto y el comportamiento que esperas. Para informar un fallo, incluye versión, plataforma, pasos y resultado. Usa archivos sintéticos al compartir evidencias; oculta códigos de sesión, PIN, nombres de equipos y contenido personal.

Una contribución debe:

- Conservar los flujos existentes, salvo que explique y justifique una migración.
- Presentar progreso, fallos y cancelaciones con estados reales del motor.
- Ofrecer textos claros, navegación por teclado cuando corresponde y controles accesibles.
- Añadir pruebas de comportamiento para cambios de protocolo, almacenamiento o lógica de negocio.
- Compilar, pasar las comprobaciones pertinentes y documentar cualquier prueba que no pudo ejecutarse.

No se exige una prueba automatizada para cada cambio de texto o espaciado. Sí se exige comprobar visualmente los cambios de interfaz.

## Revisar y abrir una propuesta

Ejecuta la verificación completa y revisa el diff. Adjunta capturas de las pantallas afectadas y explica las pruebas de transferencia que ejecutaste. Mantén la propuesta centrada en un problema revisable.

Al contribuir, aceptas distribuir tu aportación bajo la licencia MIT del proyecto y confirmas que tienes derecho a compartirla. Conserva las atribuciones y licencias de cualquier componente de terceros.
