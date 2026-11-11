# Cambios

## 0.3.0 · 10 de noviembre de 2026

- Aviso del buzón del operador integrado en la aplicación, con contador cuando el sistema lo proporciona y estado mínimo por cuenta.
- Acceso al número de buzón de la SIM mediante una acción explícita; abrir el buzón no inicia una llamada.
- Recepción de eventos protegidos de Android con el rol de teléfono predeterminado y el permiso `READ_PHONE_STATE`. No descarga audios, no lee SMS y no modifica desvíos.
- Integración comprobada con eventos sintéticos en emulador; queda pendiente comprobar el próximo aviso real del operador en el teléfono físico. El indicador depende de los avisos recibidos y no se garantiza su retirada en todos los dispositivos.

## 0.2.1 · 10 de noviembre de 2026

- Tarjeta de llamada entrante con identidad y acciones Responder/Rechazar en la pantalla principal.
- Acceso Volver a la llamada para una llamada en curso.
- Actualización de notificaciones al comenzar una llamada entrante, evitando repetir avisos por cambios de nombre o detalles.
- Identificación del llamante para las reglas de contactos de Android, respetando la presentación de números privados.

## 0.2.0 · 10 de noviembre de 2026

- Diseño en blanco, negro y grises con pestañas Recientes y Teclado.
- Historial de las últimas 100 llamadas, consultado sin copiar ni modificar el registro de Android.
- Controles de llamada agrupados y botón Colgar visible con el teclado abierto.
- Integración comprobada con el bloqueador independiente Silencio Llamadas.

## 0.1.0 · 10 de noviembre de 2026

- Primera versión: llamadas celulares entrantes y salientes, micrófono, altavoz y tonos DTMF.
- Integración con Contactos Lite y Android Telecom.
- Canal de notificaciones silencioso y respeto de No molestar.
