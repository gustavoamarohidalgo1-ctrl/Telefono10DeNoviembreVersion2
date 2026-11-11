# Verificación · 0.3.0

Validación realizada el 10 de noviembre de 2026. Aplicación `pe.gustavo.telefonolite`, versión de paquete 4.

## Compilación y entrega

| Comprobación | Resultado |
| --- | --- |
| Release con R8 y reducción de recursos | Correcto |
| Lint release | 0 errores, 10 advertencias |
| Firma del APK | Verificada |
| APK publicado | 70 004 bytes — 70,0 KB decimales |
| Dependencias de producción externas | Ninguna |
| Permiso de Internet | No solicitado |

Las advertencias de Lint corresponden a textos sin localización, un constructor de vista programática y comprobaciones/configuración de versiones. El tamaño del APK no representa el espacio instalado ni la memoria RAM utilizada.

La [versión publicada](https://github.com/gustavoamarohidalgo1-ctrl/Telefono10DeNoviembreVersion2/releases/tag/v0.3.0) incluye el APK firmado y un archivo SHA-256 para comprobar su integridad. La clave de firma permanece privada.

## Pruebas de 0.3.0

Ocho pruebas instrumentadas aisladas de `VoicemailInboxTest` pasaron en Android 16/API 36: contador nuevo y limpiado, contador desconocido, aislamiento entre cuentas, validación de eventos y rol, permiso revocado, habilitación del receptor y almacenamiento limitado a ocho cuentas.

Las siete comprobaciones de interfaz del [informe del buzón](tests/voicemail-v0.3.0.json) pasaron sobre el APK firmado:

- Indicador de dos mensajes pendientes recibido mediante un evento protegido sintético.
- Apertura del diálogo sin iniciar una llamada.
- Actualización del diálogo cuando llega un aviso con contador cero.
- Persistencia del estado limpiado al reiniciar el proceso.
- Cierre del diálogo ante una llamada entrante con No molestar activo, dejando Responder y Rechazar visibles.
- Respuesta de esa llamada sintética y acceso a Colgar.
- Teclado de marcación y botón Llamar visibles.

Se restauraron los ajustes del emulador y este quedó sin llamadas activas. Las capturas publicadas corresponden al emulador y contienen exclusivamente datos sintéticos.

El evento se inyectó con un fixture privilegiado en un emulador. La prueba mediante PDU MWI no logró entregar el mensaje al framework, aunque la consola lo aceptó. Por ello **no está validada la cadena completa desde el operador hasta la app**. El siguiente aviso real del operador en el Redmi sigue pendiente de comprobación. Véanse los límites y el procedimiento en [Buzón de voz](BUZON.md).

## Pruebas de 0.2.1

Las cinco comprobaciones del [informe del emulador](tests/incoming-v0.2.1.json) pasaron en Android 16/API 36:

- Tarjeta de llamada entrante en la pantalla principal con No molestar activo: responder abre los controles; una llamada activa muestra Volver a la llamada.
- Rechazar desde la tarjeta retira la llamada de la interfaz.
- Aviso superior sobre Inicio cuando No molestar está desactivado.
- Pantalla de respuesta con el dispositivo bloqueado.
- Canal de alta importancia, sin sonido, vibración ni permiso para saltarse No molestar.

La revisión del aviso superior utiliza el estado de SystemUI y una captura, porque UIAutomator omite esa ventana en el emulador. Se revisaron visualmente las pantallas. Todas las llamadas y contactos de las capturas son sintéticos.

## Historial y bloqueador

En 0.2.0 se comprobaron la lectura del historial con y sin permiso, los números privados sin acción y la selección de una fila sin llamar automáticamente. La lista consulta como máximo 100 registros y cancela sus consultas al dejar de estar visible.

También se comprobó la integración con Silencio Llamadas 1.1.0 en emulador. Con bloquear todos activo, se rechazaron llamadas de un contacto y de un número no guardado antes de llegar a la interfaz. Con el bloqueador desactivado, se recibió la llamada. Teléfono Lite ocupa el rol de teléfono; el bloqueador conserva el rol de filtrado.

La versión 0.2.1 conserva estos componentes y añade la tarjeta explícita de respuesta, junto con ajustes en la actualización de las notificaciones.

## Comprobación en un teléfono

0.3.0 se instaló y abrió en un Redmi Note 14 4G con Android 16. Se comprobó la apertura del buzón de la SIM, el permiso de estado del teléfono y el receptor habilitado. No había un aviso real del buzón activo en ese momento. Se comprobó la coincidencia del APK instalado y que se conservaran los roles de teléfono/bloqueador y los ajustes de silencio y No molestar. No se efectuó una nueva llamada de red automatizada en esta versión. La prueba manual de audio en ambos sentidos y altavoz se realizó en 0.1.0.

No molestar puede ocultar los avisos flotantes o la pantalla completa incluso con las notificaciones habilitadas. La app respeta esa política; la tarjeta dentro de la pantalla principal permite responder cuando esta se encuentra abierta. No se han probado llamadas reales de emergencia, todas las rutas Bluetooth ni todos los escenarios de múltiples SIM y conferencia.

## Reproducir

Configura primero un emulador aislado, la app predeterminada y los permisos. Añade `platform-tools` del SDK al PATH. Los scripts comprueban que el dispositivo sea un emulador antes de realizar cambios.

```sh
./tools/build.sh
python3 tools/check-incoming-emulator.py --serial emulator-5554
python3 tools/check-recents-emulator.py --serial emulator-5554
python3 tools/smoke-emulator.py --serial emulator-5554
```

Las pruebas de llamadas normales requieren desactivar el bloqueador únicamente en el emulador. La prueba de integración `tools/check-blocker-emulator.py` requiere instalar el bloqueador, asignarle su rol, crear el contacto sintético 5550191 y activar bloquear todos; restaura ese interruptor al finalizar. No se necesita incluir datos de un teléfono personal.

## Referencias

- [InCallService](https://developer.android.com/reference/android/telecom/InCallService).
- [Notificaciones de llamadas](https://developer.android.com/develop/ui/views/notifications/call-style).
- [Canales de notificación](https://developer.android.com/develop/ui/views/notifications/channels).
- [Filtrado de llamadas](https://developer.android.com/reference/android/telecom/CallScreeningService).
