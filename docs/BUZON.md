# Buzón de voz · 0.3.0

Teléfono Lite integra el aviso de buzón de Servicios telefónicos dentro de la aplicación. El botón **Buzón de voz** muestra la cantidad pendiente cuando el operador la proporciona. Abrirlo no inicia una llamada; **Llamar al buzón** consulta el número configurado para la SIM elegida y llama únicamente tras esa pulsación.

La integración recibe eventos protegidos de Android y necesita `READ_PHONE_STATE` además del rol de teléfono predeterminado. El receptor comienza deshabilitado y se habilita al abrir la app con esas autorizaciones. No se usan servicios permanentes, alarmas ni sondeos. El emisor debe poseer `MODIFY_PHONE_STATE`; Teléfono Lite no solicita ese permiso privilegiado.

Se guardan como máximo ocho identificadores de cuenta mediante hash, con el último contador y la fecha de recepción. No se guardan números de buzón, audios, mensajes, contactos ni PendingIntent. Un aviso sin cantidad indica mensajes pendientes; nunca se interpreta como cero. Un aviso con cantidad cero retira el indicador. Las cuentas que ya no están disponibles no se muestran.

Esto no es buzón visual: no descarga, reproduce ni elimina grabaciones, no lee SMS y no modifica desvíos ni reglas del operador. El contenido se escucha llamando al buzón. El estado mostrado es el último aviso recibido, no una consulta en directo al operador. El acceso requiere una SIM disponible.

## Permisos y ciclo de vida

El receptor se reconcilia al abrir o reanudar la actividad y después de conceder permisos. Si se retira el permiso mientras el proceso no está activo, Android puede dejar de entregar eventos hasta volver a abrir y reconciliar la app; tampoco existe garantía de repetición de un aviso anterior al habilitar la integración. La pantalla distingue entre un estado no observado y un último aviso sin pendientes.

## Verificación

Compilación release y Lint release correctos. Ocho pruebas instrumentadas aisladas pasaron y comprobaron las transiciones de contador, los valores desconocidos, el aislamiento entre cuentas, la validación de eventos, el acceso y el límite de almacenamiento. Los avisos de prueba se mantienen separados de los datos de la aplicación.

En el emulador, una prueba auxiliar ejecutada como root envió el evento protegido de Android y se verificó su recepción y el contador de dos pendientes en la interfaz. Este ensayo comprueba la integración desde el broadcast, pero no simula toda la cadena desde el operador. Las PDU MWI de prueba fueron aceptadas por la consola del emulador sin llegar al framework; por tanto no se considera validada esa cadena de extremo a extremo. También pasaron las comprobaciones del diálogo sin llamada automática, su actualización al limpiar el aviso, la persistencia del estado tras reiniciar el proceso, el cierre del diálogo ante una llamada entrante con No molestar, la respuesta de esa llamada sintética y la visibilidad del teclado.

El APK firmado 0.3.0 ocupa 70.004 bytes. Se instaló en el Redmi y se verificó que coincide con el APK local, que el receptor está habilitado, que el permiso está concedido y que se abre el buzón de la SIM. Se conservaron los roles de teléfono y bloqueador, el modo de timbre y la política de No molestar. No había un aviso real pendiente al comprobarlo.

Las pruebas del emulador no realizan llamadas de red. Los resultados y capturas locales se guardan en `docs/resultados/0.3.0/`, excluidos de Git. La integración en el Redmi requiere comprobar el siguiente aviso real del operador; no se generó un mensaje de voz real desde la automatización.

## Fuentes

- [Evento de aviso del buzón en Android](https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_SHOW_VOICEMAIL_NOTIFICATION).
- [Delegación al marcador predeterminado en AOSP](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/heads/main/src/com/android/phone/NotificationMgr.java).

### Reproducir un evento protegido en el emulador

La herramienta [`VoicemailEvent.java`](../tools/fixtures/VoicemailEvent.java) envía un evento sintético para la primera cuenta SIM disponible. Requiere JDK 17, la plataforma Android 35 y `d8` de Android SDK Build Tools. La app debe estar instalada en el emulador, ser el teléfono predeterminado, tener `READ_PHONE_STATE` concedido y haberse abierto para habilitar el receptor. No inicia llamadas ni crea mensajes de voz.

Con `JAVA_HOME` y `ANDROID_HOME` configurados, ejecutar desde la raíz del proyecto. Seleccionar una versión instalada de Build Tools en `BUILD_TOOLS`; todos los comandos de `adb` apuntan expresamente a `emulator-5554`. El ensayo se detiene si no es un emulador o si `adb root` no proporciona UID 0. La propia herramienta vuelve a comprobar ambas condiciones antes de enviar el evento.

```bash
BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
ADB="$ANDROID_HOME/platform-tools/adb"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_DIR"' EXIT
"$JAVA_HOME/bin/javac" --release 17 -cp "$ANDROID_JAR" -d "$FIXTURE_DIR" tools/fixtures/VoicemailEvent.java
"$BUILD_TOOLS/d8" --min-api 28 --lib "$ANDROID_JAR" --output "$FIXTURE_DIR" "$FIXTURE_DIR/VoicemailEvent.class"
test "$("$ADB" -s emulator-5554 shell getprop ro.kernel.qemu | tr -d '\r')" = 1 || { echo 'Se requiere el emulador'; exit 1; }
"$ADB" -s emulator-5554 root
"$ADB" -s emulator-5554 wait-for-device
test "$("$ADB" -s emulator-5554 shell id -u | tr -d '\r')" = 0 || { echo 'Se requiere adb root'; exit 1; }
"$ADB" -s emulator-5554 push "$FIXTURE_DIR/classes.dex" /data/local/tmp/voicemail-fixture.dex
"$ADB" -s emulator-5554 shell CLASSPATH=/data/local/tmp/voicemail-fixture.dex app_process /system/bin VoicemailEvent 2
# Tras comprobar la interfaz, enviar cero para retirar el aviso de prueba:
"$ADB" -s emulator-5554 shell CLASSPATH=/data/local/tmp/voicemail-fixture.dex app_process /system/bin VoicemailEvent 0
"$ADB" -s emulator-5554 shell rm /data/local/tmp/voicemail-fixture.dex
```

El emulador debe usar una imagen que permita `adb root`. La ejecución altera el último estado de aviso del buzón de la app en ese emulador; el evento con cero lo limpia. Un evento aceptado por el receptor no demuestra la entrega desde la red del operador.
