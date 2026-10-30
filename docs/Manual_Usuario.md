# Manual de Usuario — CPP
## Cuentas por Pagar, sin papeles y sin dolores de cabeza

> **Entregable de la actividad 7 del cronograma** (Progreso.docx: «Elaborar
> manuales técnicos y de usuario»). Versión 1.0 · 24/10/2026.
> App **0.19.0-v1** para Android 7.0 o superior.

---

## 1. Bienvenida

CPP es la app donde tu negocio ya no pelea con facturas de papel:

- Tu **proveedor** te registra lo que le debes desde SU teléfono.
- Tú (la empresa) **ves esas cuentas al instante** en el tuyo, con avisos
  de color cuando algo está por vencer.
- Pagas **adjuntando la foto del comprobante** y el pago queda registrado.
- Cuando recibes una factura, la **fotografías** y la app la lee (OCR),
  calcula el **riesgo**, la **concilia contra la cuenta** y te dice si está
  todo bien o hay discrepancia — todo en segundos y con la foto guardada.

No hay IPs que teclear ni servidores que encender: la app habla directo con
Firebase (la nube de Google) y el análisis ocurre en tu propio teléfono.

## 2. Antes de empezar

- **Teléfono**: Android 7.0 o superior, con cámara y conexión a internet.
- **Cuenta**: la creas tú en la app (§3.1). Para pruebas existen las cuentas
  demo `cliente@cpp.demo` y `proveedor@cpp.demo` (la clave la configura
  quien preparó el proyecto).
- **Huella o PIN**: el teléfono debe tener algúN bloqueo de pantalla
  configurado; la app lo usa como candado (§3.3).

> **Consejo de fotos**: manda SIEMPRE la foto ORIGINAL de la factura.
  Las imágenes reenviadas por chat llegan achicadas y el lector puede
  confundir dígitos.

## 3. Primeros pasos

### 3.1 Crear tu cuenta

1. Abre la app y toca **Crear cuenta**.
2. Elige el tipo: **Cliente** (tu empresa: un solo correo por empresa, con
   todas las funciones) o **Proveedor** (tu negocio cobra a través de CPP).
3. Llena correo, contraseña (mínimo 6 caracteres) y el nombre de tu
   empresa/negocio → **Crear**. Si el correo ya existe, simplemente entra
   con él.

### 3.2 Entrar

Correo + contraseña → **Entrar**. Según tu rol caes a la pantalla del
proveedor (§4) o de la empresa cliente (§5).

### 3.3 El candado (huella o PIN)

Con la sesión abierta, cada vez que vuelvas a abrir la app te pedirá la
MISMA huella con la que desbloqueas tu teléfono (o su PIN si no tiene
lector). Es obligatorio y no hay que configurar nada. Si prefieres no
dejar la sesión abierta, usa **Cerrar sesión**.

## 4. Soy PROVEEDOR (mi negocio cobra)

### 4.1 Subir una cuenta (lo que te deben)

1. Toca **Subir cuenta (lo que le deben a tu negocio)**.
2. Escribe el **correo del cliente** (debe tener cuenta en la app: se
   verifica al momento), un **código** (ej. OC-101), el **monto** y la
   **fecha límite de pago** (AAAA-MM-DD).
3. Guarda: la cuenta aparece EN EL TELÉFONO DEL CLIENTE al instante.

### 4.2 Seguir tus cuentas

Tu lista se actualiza sola: **PENDIENTE** (amarillo), **PAGADA** (verde,
con fecha) y **CERRADA** cuando la fecha límite pasó sin pago. Toca una
cuenta para ver su ficha completa.

### 4.3 Cuenta vencida: TÚ decides

Pasada la fecha límite sin pago, la cuenta se cierra y el cliente ya no
puede pagarla desde la app. Al tocarla eliges:

- **Reabrir pago** → el cliente vuelve a poder pagar tal cual, o
- **Agregar interés…** → escribes el monto acordado (ej. 150); se suma a la
  cuenta («Incluye $150.00 de interés acordado») y se reabre el pago.

## 5. Soy CLIENTE (la empresa que paga)

### 5.1 Cuentas por pagar

Entra a **Cuentas por pagar**: verás las pestañas **POR PAGAR** y
**PAGADAS**. Cada cuenta muestra negocio, monto y fecha límite, con avisos
de color:

- ⏰ **Por vencer** (amarillo): vence en 3 días o menos.
- 🚨 **Vencida** (rojo).

**Además te llega una notificación** cuando una cuenta está a 3 días, a
1 día o ya venció (una vez al día por cuenta; la primera vez Android 13+
te pedirá permiso de notificaciones: concédelo).

### 5.2 Pagar una cuenta

1. En **POR PAGAR**, toca la cuenta → **Adjuntar comprobante y pagar**.
2. Elige la foto del comprobante (cámara o galería) y confirma.
3. El comprobante sube a la nube y la cuenta pasa SOLA a **PAGADAS**; tu
   proveedor lo ve al instante.

**Cuenta vencida**: si la fecha límite pasó, la app te dirá que el pago
está **CERRADO** y que debes hablar con tu proveedor para llegar a un
acuerdo (él la reabrirá, con o sin interés, y volverás a poder pagar).

### 5.3 Enviar una factura (conciliación automática)

1. En la pantalla principal elige la **cuenta** contra la que va la factura.
2. Toca **Tomar foto** o **Elegir de galería** (foto ORIGINAL, bien enfocada).
3. Toca **Enviar factura**: la app la lee (OCR), calcula el riesgo, revisa
   si ya la habías mandado (duplicados) y la concilia contra la cuenta.
4. En segundos verás el **veredicto**:
   - **APROBADA** → todo coincide con la cuenta.
   - **CON DISCREPANCIA** → te dice el motivo exacto (monto excede la
     cuenta, proveedor no coincide, fecha fuera de rango, duplicada…).
5. La factura queda guardada con su foto en **Ver facturas**.

### 5.4 Ver facturas (tu historial)

**Ver facturas** lista todas tus facturas con su badge: **✓ Aprobada**,
**! Con Discrepancia** o **⏳ Pendiente**. Los **chips** de arriba filtran
al tocarlos (toca de nuevo para quitar el filtro).

## 6. Tu sesión y tu seguridad

- **Cerrar sesión**: desde el menú/botón correspondiente; la próxima vez
  pide correo y contraseña otra vez.
- **Candado**: mientras la sesión esté abierta, la app se bloquea con tu
  huella/PIN al reabrirla (también al entrar por una notificación).
- **Cuenta eliminada**: si quien administra el proyecto borra tu cuenta,
  la app te avisa («Tu cuenta fue eliminada: sesión cerrada») y regresa al
  login automáticamente.
- Nadie más ve tus datos: cada empresa ve solo SUS cuentas y SUS facturas;
  los comprobantes y facturas quedan asociados a tu correo.

## 7. Preguntas frecuentes

| Problema/Pregunta | Qué hacer |
|---|---|
| «Ese correo no tiene cuenta en la app» (proveedor) | El cliente debe crear su cuenta primero (§3.1) |
| La factura sale CON DISCREPANCIA | Lee el motivo: monto mayor a la cuenta, proveedor distinto o fecha fuera de rango. Corrige con el proveedor y vuelve a enviar contra la cuenta correcta |
| La foto no se lee bien | Usa la foto ORIGINAL (las de chat llegan achicadas), con buena luz y sin sombras |
| No me llegan recordatorios | Concede el permiso de notificaciones (Android 13+) y revisa que la cuenta siga POR PAGAR |
| No puedo pagar una cuenta | Si está CERRADA (vencida) habla con tu proveedor; si es otra cosa, revisa tu conexión |
| ¿Puedo editar una factura ya enviada? | No: el veredicto queda fijo para auditoría (así nadie «arregla» un rechazo) |
| Olvidé mi contraseña | Quien administra el proyecto puede resetearla desde la consola de Firebase |

## 8. Glosario rápido

- **Cuenta (orden)**: lo que el proveedor registra que le debes (código,
  monto y fecha límite).
- **Comprobante**: foto del pago con la que marcas la cuenta como pagada.
- **Factura**: documento del proveedor que fotografías para conciliar.
- **Conciliación**: comparación automática factura ↔ cuenta.
- **Riesgo**: porcentaje que indica qué tan fuera de lo normal está el
  monto para ese proveedor.
- **OCR**: lector de texto de la imagen (en tu propio teléfono).

*Fin del documento — Manual_Usuario.md v1.0. Guía técnica de demostración
paso a paso: `SPRINT10_v1.md` §3.*

