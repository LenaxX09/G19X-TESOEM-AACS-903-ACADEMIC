# SPRINT 10 — «Sin servidor» (Firebase puro: Auth + Firestore + Storage + ML Kit)

Guía rápida del Sprint 10 de la v1.0. Lo nuevo, en una línea cada cosa:

| Qué cambió | Qué se construyó | Dónde |
|---|---|---|
| Sin Laragon para probar | Semilla y habilitación por CLI (solo `php.exe`, **sin Apache ni MySQL**): usuarios demo + cuentas | `api/semilla_firebase.php`, `api/habilitar_email_auth.php`, `test_s10.ps1` |
| Login sin IP | Sesión con **Firebase Auth** (correo+contraseña); `google-services.json` apunta al proyecto cpp-facturas. `Sesion` ya no guarda servidor ni token propio | `LoginActivity.java`, `Sesion.java` |
| Órdenes sin IP: las sube el PROVEEDOR | «➕ Subir cuenta (lo que le deben a tu negocio)» → Firestore `ordenes` {clienteCorreo, monto, fechaLimite}; el cliente la ve **al instante** (oyente en vivo) y la paga con su comprobante | `ProveedorActivity.java`, `PagosActivity.java` |
| Pipeline EN EL TELÉFONO | `Motores.java`: OCR con **ML Kit** + riesgo (fórmula del C) + conciliación (réplica de `regla.pl`, reglas 0–5) + pre-check de duplicados (RF-L3) contra Firestore | `Motores.java`, `MainActivity.java` |
| Fotos en la nube | Foto de la factura y comprobante de pago van a **Firebase Storage** (`facturas/`, `comprobantes/`); el veredicto queda en Firestore `facturas` (listado en vivo) | `FirebaseServicio.java`, `ListadoActivity.java` |

App resultante: **0.14.2-v1 (versionCode 23)** — S10.2 (20/10): paleta
**azul rey**, **sin emojis** y **formas estilo Mercado Pago** (tarjetas
planas 16dp, botones píldora). S10.1 (20/10, a pedido
del usuario): contenido visible bajo
la barra de estado (`fitsSystemWindows`) y el proveedor ya no puede subir
cuentas a correos que no tienen cuenta en la app (se verifica
`usuarios/{uid}` antes de guardar). `AdminActivity` se retira
(su CRUD estaba ligado al PHP de Laragon); regresa en el S11 migrado a
Firestore.

## 1. Preparar el entorno (UNA sola vez · SIN Laragon)

Solo se necesita:

1. `php.exe` en el PATH (o el de Laragon `C:\laragon\bin\php\*\php.exe`:
   se usa el .exe como intérprete, **sin iniciar Laragon**).
2. La clave de servicio en `C:\CPP\api\firebase-service-account.json`
   (SECRETO, fuera de git — consola de Firebase → ⚙️ Configuración del
   proyecto → Cuentas de servicio → Generar clave JSON).
3. Habilitar el login por correo (API **v2**, verificado en vivo 20/10):
   ```
   php C:\CPP\api\habilitar_email_auth.php
   ```
4. Sembrar los datos demo (re-ejecutable):
   ```
   php C:\CPP\api\semilla_firebase.php
   ```
   Crea en Auth (clave **cppdemo123**): `admin@` · `capturista@` ·
   `revisor@` · `proveedor@cpp.demo`, sus perfiles `usuarios/{uid}` y
   3 cuentas para `capturista@cpp.demo` (una vence en 3 días ⏰ y una
   VENCIDA 🚨, para las alertas del RF-M7).

## 2. Checkpoint C10 (re-ejecutable, SIN Laragon)

```powershell
powershell -File C:\CPP\test_s10.ps1            # solo verifica (7 pruebas)
powershell -File C:\CPP\test_s10.ps1 -Semilla   # re-siembra y verifica
```

- T1 Email/Password habilitado + **login demo real** (signInWithPassword
  con la Web API key del config v2).
- T2/T4a los usuarios demo existen en Auth (uid reutilizable).
- T3/T4b perfiles de Firestore (`rol` capturista / proveedor con negocio).
- T5 las 3 cuentas OC-101/102/103 pendientes para el cliente.
- T6 datos del RF-M7: 1 vencida + 1 por vencer (≤3 días).

Resultado esperado: **C10: 7/7 OK** (verificado el 20/10 contra cpp-facturas).

## 3. Probar a mano en el móvil (0.14.0-v1)

Con datos móviles/WiFi (nada de `adb reverse` ni teclear IP):

1. **Cliente** `capturista@cpp.demo / cppdemo123` → «💰 Cuentas por pagar»:
   ya están las 3 cuentas que subió el proveedor (⏰/🚨 en rojo).
2. **Proveedor** (otro teléfono, o cerrar sesión) `proveedor@cpp.demo /
   cppdemo123` → «➕ Subir cuenta» → correo del cliente + código + monto +
   fecha límite (AAAA-MM-DD).
3. En el teléfono del **cliente** la cuenta aparece SOLA (oyente en vivo).
4. **Pagar**: POR PAGAR → tocar la cuenta → «📷 Adjuntar comprobante y
   pagar» → galería → el comprobante sube a Storage y la cuenta pasa sola
   a PAGADAS (la lista del proveedor también se actualiza sola).
5. **Enviar factura**: elegir cuenta → foto/galería → «📤 Enviar factura» →
   OCR + riesgo + conciliación corren EN EL TELÉFONO y el veredicto ✅/⚠️
   se guarda en Firestore + Storage («📋 Ver facturas»).

## 4. Contratos que cambiaron (ojo al integrar)

- `Sesion`: DESAPARECIERON `servidor()`, `cabeceras()` y `sinSesion()`
  (no hay HTTP propio); queda el perfil cacheado + `salirAlLogin()`.
- Colecciones Firestore:
  - `usuarios/{uid}`: nombre, correo, rol (admin|capturista|revisor|
    proveedor), negocio.
  - `ordenes`: codigo, negocio, proveedorUid, clienteCorreo, monto,
    fechaEmision, fechaLimite, estado (pendiente|pagada), pagadaEn,
    comprobanteUrl, creado.
  - `facturas`: folio, proveedor, proveedorSlug, fecha, monto, riesgo,
    veredicto, motivo, ordenId, ordenCodigo, subidoPor, fotoUrl, creado.
  - (del S6) `notificaciones` y `proveedores` siguen para el buzón FCM.
- Storage: `facturas/<timestamp>.jpg` y `comprobantes/<ordenId>.jpg`.
- Los motores originales (`motores/*.py/.c/.pl`) quedan como REFERENCIA
  académica y para la demo con Laragon; `Motores.java` replica sus reglas
  con los mismos resultados. `regla.pl` sigue con 10 argumentos.
- Login: sin campo usuario ni IP; el correo ES el usuario (Firebase Auth).

## 5. Qué falta

- S11: CRUD de administración migrado a Firestore (AdminActivity).
- Probar el flujo completo en el Motorola físico (ideal en dos sesiones:
  una de proveedor y otra de cliente).
- RF-M8 (cola offline) — único RF pendiente del Incremento 1.
- Reglas de seguridad de Firestore/Storage: revisar en la consola que los
  usuarios autenticados puedan leer/escribir `usuarios`/`ordenes`/
  `facturas` (la semilla usa la cuenta de servicio y NO las toca);
  endurecer antes de producción (deuda v1.1).
