# SPRINT 10 — «Sin servidor» (Firebase puro: Auth + Firestore + Storage + ML Kit)

Guía rápida del Sprint 10 de la v1.0. Lo nuevo, en una línea cada cosa:

| Qué cambió | Qué se construyó | Dónde |
|---|---|---|
| Sin Laragon para probar | Semilla y habilitación por CLI (solo `php.exe`, **sin Apache ni MySQL**): usuarios demo + cuentas | `api/semilla_firebase.php`, `api/habilitar_email_auth.php`, `test_s10.ps1` |
| Recordatorios de pago | **WorkManager** despierta al teléfono UNA VEZ AL DÍA (con restricción de red) y revisa las cuentas `ordenes` del correo de la empresa; avisa a **3 días**, **1 día antes** y **VENCIDA** con notificación local (canal «Recordatorios de pago») que abre Cuentas por pagar. Anti-spam: un aviso por cuenta/nivel/día | `RecordatorioWorker.java`, `MainActivity.java` |
| Candado obligatorio | Al abrir la app con sesión vigente pide **la HUELLA con la que se desbloquea el teléfono** (BiometricPrompt del sistema, `BIOMETRIC_WEAK \| DEVICE_CREDENTIAL`); si el teléfono no tiene lector/huella, pide su **PIN/patrón de bloqueo**. Sin elección ni configuración: usa lo que el teléfono ya tiene. Cubre también entrar por notificación. Escape: «Cerrar sesión» | `Candado.java`, `CandadoActivity.java` |
| Login sin IP | Sesión con **Firebase Auth** (correo+contraseña); `google-services.json` apunta al proyecto cpp-facturas. `Sesion` ya no guarda servidor ni token propio | `LoginActivity.java`, `Sesion.java` |
| Órdenes sin IP: las sube el PROVEEDOR | «➕ Subir cuenta (lo que le deben a tu negocio)» → Firestore `ordenes` {clienteCorreo, monto, fechaLimite}; el cliente la ve **al instante** (oyente en vivo) y la paga con su comprobante | `ProveedorActivity.java`, `PagosActivity.java` |
| Pipeline EN EL TELÉFONO | `Motores.java`: OCR con **ML Kit** + riesgo (fórmula del C) + conciliación (réplica de `regla.pl`, reglas 0–5) + pre-check de duplicados (RF-L3) contra Firestore | `Motores.java`, `MainActivity.java` |
| Fotos en la nube | Foto de la factura y comprobante de pago van a **Firebase Storage** (`facturas/`, `comprobantes/`); el veredicto queda en Firestore `facturas` (listado en vivo) | `FirebaseServicio.java`, `ListadoActivity.java` |

App resultante: **0.19.0-v1 (versionCode 29)** — S10.7 (21/10):
**sesión vigilada** — si BORRAN la cuenta en Firebase (Auth o perfil
`usuarios/{uid}`), el teléfono se entera EN VIVO (oyente de Firestore
sobre el perfil + AuthStateListener) y vuelve al login con «Tu cuenta
fue eliminada: sesión cerrada», aunque la app siguiera abierta y
aunque estés en otra pantalla (la pila entera se limpia). El alta con
Google se descartó a pedido del usuario. S10.6 (21/10):
**cuenta vencida → pago CERRADO** — pasada la fecha límite (y sin pagar),
el cliente YA NO puede pagarla desde la app: tocarla solo explica que
debe **comunicarse con su proveedor** para llegar a un acuerdo. En la
lista del PROVEEDOR la cuenta sale «CERRADA · toca para decidir» y ÉL
decide: **Reabrir pago** (el cliente vuelve a poder pagar tal cual) o
**Agregar interés…** (suma $ al monto —queda «Incluye $X de interés
acordado»— y reabre). El recordatorio diario también cambia de mensaje:
«venció: pago cerrado» en vez del aviso normal. S10.5.1 (21/10):
**candado con la huella/PIN del teléfono** — al abrir la app con sesión
vigente, el BiometricPrompt del sistema pide la MISMA huella con la que se
desbloquea el celular (o su PIN si no hay lector), sin elegir nada; el
diálogo de elección anterior no dejaba tocar las opciones y se retiró
(verificado en el Motorola: el prompt aparece, cancela y reintenta).
S10.5 (21/10): candado obligatorio. S10.4 (21/10):
**recordatorios de pago** — cuando una cuenta de la empresa está a 3 días
de vencer, a 1 día o ya venció, el teléfono lanza una notificación que
abre Cuentas por pagar (WorkManager + notificación local, SIN servidor;
verificado en el Motorola físico). S10.3 (21/10): **tema
oscuro FIJO** (la paleta que antes solo salía con el teléfono en modo
noche es ahora la única: la app se ve igual en cualquier teléfono) y
**roles unificados**: los viejos admin/capturista/revisor se volvieron
UN rol **Cliente** — cada EMPRESA cliente se registra con UN solo correo
y esa cuenta tiene TODAS las funciones (capturar, revisar, pagar); nadie
puede crear más usuarios y los pagos quedan atados a ese correo. S10.2
(20/10): paleta azul rey, sin emojis y formas estilo Mercado Pago
(tarjetas planas 16dp, botones píldora). S10.1 (20/10, a pedido del
usuario): contenido visible bajo la barra de estado (`fitsSystemWindows`)
y el proveedor ya no puede subir cuentas a correos que no tienen cuenta
en la app (se verifica `usuarios/{uid}` antes de guardar).
`AdminActivity` se retira DEFINITIVAMENTE (S10.3: sin CRUD de usuarios).

## 1. Preparar el entorno (UNA sola vez · SIN Laragon)

Solo se necesita:

1. `php.exe` en el PATH (o el de Laragon `C:\laragon\bin\php\*\php.exe`:
   se usa el .exe como intérprete, **sin iniciar Laragon**).
2. La clave de servicio en `api\firebase-service-account.json`
   (SECRETO, fuera de git — consola de Firebase → ⚙️ Configuración del
   proyecto → Cuentas de servicio → Generar clave JSON).
3. Habilitar el login por correo (API **v2**, verificado en vivo 20/10):
   ```
   php api\habilitar_email_auth.php
   ```
4. Sembrar los datos demo (re-ejecutable) — la clave demo NUNCA viaja en
   el repo (correcciones 22/10): pásala por variable de entorno (o
   parámetro):
   ```
   $env:CPP_DEMO_CLAVE = 'la-clave-que-configuraste'
   php api\semilla_firebase.php
   ```
   Crea en Auth (con la clave que configuraste): `cliente@cpp.demo` (la EMPRESA
   cliente, con su empresa en el perfil) y `proveedor@cpp.demo`; BORRA
   los roles viejos admin/capturista/revisor, escribe sus perfiles
   `usuarios/{uid}` y sube 3 cuentas para `cliente@cpp.demo`
   (una vence en 3 días ⏰ y una VENCIDA 🚨, para las alertas del RF-M7).

## 2. Checkpoint C10 (re-ejecutable, SIN Laragon)

```powershell
# define antes la clave demo (no vive en el repo): $env:CPP_DEMO_CLAVE = '…'
powershell -File .\pruebas\test_s10.ps1            # solo verifica (7 pruebas)
powershell -File .\pruebas\test_s10.ps1 -Semilla   # re-siembra y verifica
```

- T1 Email/Password habilitado + **login demo real** (signInWithPassword
  con la Web API key del config v2).
- T2/T4a los usuarios demo existen en Auth (uid reutilizable).
- T3/T4b perfiles de Firestore (`rol` cliente con su empresa / proveedor
  con negocio).
- T5 las 3 cuentas OC-101/102/103 pendientes para el cliente.
- T6 datos del RF-M7: 1 vencida + 1 por vencer (≤3 días).

Resultado esperado: **C10: 7/7 OK** (verificado el 20/10 contra cpp-facturas).

## 3. Probar a mano en el móvil (0.14.0-v1)

Con datos móviles/WiFi (nada de `adb reverse` ni teclear IP):

1. **Cliente** `cliente@cpp.demo / la clave que configuraste` → «💰 Cuentas por pagar»:
   ya están las 3 cuentas que subió el proveedor (⏰/🚨 en rojo).
2. **Proveedor** (otro teléfono, o cerrar sesión) `proveedor@cpp.demo /
   la clave que configuraste` → «➕ Subir cuenta» → correo del cliente + código + monto +
   fecha límite (AAAA-MM-DD).
3. En el teléfono del **cliente** la cuenta aparece SOLA (oyente en vivo).
4. **Pagar**: POR PAGAR → tocar la cuenta → «📷 Adjuntar comprobante y
   pagar» → galería → el comprobante sube a Storage y la cuenta pasa sola
   a PAGADAS (la lista del proveedor también se actualiza sola). ⚠️ Con
   Storage omitido (Blaze — INSTALACION §8.4) la subida puede fallar: la
   app AVISA y la cuenta queda «por pagar» (sin evidencia no hay pago);
   anota qué pasó al probarlo en el teléfono.
5. **Enviar factura**: elegir cuenta → foto/galería → «📤 Enviar factura» →
   OCR + riesgo + conciliación corren EN EL TELÉFONO y el veredicto ✅/⚠️
   se guarda en Firestore («📋 Ver facturas»). La foto va a Storage; si no
   está disponible, la factura se guarda SIN imagen y con aviso (31/10).
6. **Recordatorios de pago** (S10.4): con la sesión de `cliente@cpp.demo`
   abierta, al entrar a la pantalla principal deben llegar DOS avisos —
   «OC-102 vence en 3 días» y «OC-103 está VENCIDA» (OC-101 está a 10 días
   y NO avisa). Tocar uno abre «💰 Cuentas por pagar». El aviso se repite
   al día siguiente si sigue pendiente; ya no avisa si se pagó. La primera
   vez, Android 13+ pide permiso de notificaciones (concederlo).
7. **Candado obligatorio** (S10.5.1): al abrir la app con sesión vigente
   sale el BiometricPrompt del SISTEMA pidiendo la HUELLA con la que
   desbloqueas tu teléfono (si el teléfono no tuviera lector/huella, pide
   su PIN/patrón de bloqueo). No hay nada que elegir ni configurar. Si
   cancelas, la pantalla queda con «Volver a intentar» y «Cerrar sesión»;
   el botón ATRÁS manda la app al fondo sin liberar la sesión. También
   bloquea entrar tocando una notificación de recordatorio.
8. **Cuenta vencida → pago CERRADO** (S10.6): con la sesión del
   **cliente**, OC-103 (vencida) sale en ROJO «CERRADA · vencida hace N
   día(s)»; tocarla NO ofrece pagar: explica que hay que hablar con el
   proveedor. Cambiar a la sesión del **proveedor**: su OC-103 sale
   «CERRADA · toca para decidir» → tocarla → probar las DOS rutas del
   acuerdo: (a) «Reabrir pago» → en el cliente vuelve a poder pagar; (b)
   «Agregar interés…» (p. ej. 150) → el monto sube a $2450 y en el
   cliente la fila dice «PAGO REABIERTO» y el detalle «Incluye $150.00
   de interés acordado». Re-sembrar (`test_s10.ps1 -Semilla`) para
   repetir la demo desde cero.
9. **Sesión vigilada** (S10.7): con la sesión del cliente ABIERTA, borra
   su usuario en la consola (Authentication → Usuarios → ⋮ → Eliminar y
   también el doc `usuarios/{uid}` en Firestore): en segundos el teléfono
   muestra «Tu cuenta fue eliminada: sesión cerrada» y regresa al login
   (aunque estés en otra pantalla: la pila entera se limpia).

## 4. Contratos que cambiaron (ojo al integrar)

- `Sesion`: DESAPARECIERON `servidor()`, `cabeceras()` y `sinSesion()`
  (no hay HTTP propio); queda el perfil cacheado + `salirAlLogin()`.
- `FirebaseServicio` (correcciones 24/10): se retiró el bloque del S6
  (`registrarCliente`/`registrarProveedor`/`escuchar`/`Noti`/
  `proveedorGuardado`: escribía en `clientes`/`proveedores`/
  `notificaciones`, colecciones que las reglas cierran) y
  `escucharOrdenesTodas`; `escucharFacturas(correo, …)` ya no recibe el
  parámetro «todas» — SIEMPRE filtra por `subidoPor`, igual que
  `historialPromedio` (las reglas no dejan leer facturas ajenas y
  Firestore no filtra resultados: la consulta sin where se rechaza
  completa).
- Colecciones Firestore:
  - `usuarios/{uid}`: nombre, correo, rol (cliente|proveedor), negocio y
    empresa (para el cliente, su empresa; para el proveedor, su negocio).
    S10.3: UNA cuenta (UN correo) por empresa cliente — no se crean más
    usuarios desde la app.
  - `ordenes`: codigo, negocio, proveedorUid, clienteCorreo, monto,
    fechaEmision, fechaLimite, estado (pendiente|pagada), pagadaEn,
    comprobanteUrl, creado. S10.6: `reabierta` (true cuando el proveedor
    reabrió una vencida) e `interes` (acumulado $ agregado por acuerdo;
    `monto` ya lo incluye).
  - `facturas`: folio, proveedor, proveedorSlug, fecha, monto, riesgo,
    veredicto, motivo, ordenId, ordenCodigo, subidoPor, fotoUrl, creado.
  - `clientesPorCorreo/{slug}`: directorio público PUNTUAL (uid + correo)
    que se escribe al crear el perfil del cliente (app y semilla);
    `existeCorreo()` lo consulta 1-a-1 porque las reglas NO dejan listar
    `usuarios`.
  - `facturasLookup/{id}`: índice anti-duplicados (RF-L3) que escribe
    `guardarFactura`. Correcciones 29/10: la llave incluye al DUEÑO y al
    proveedor — por folio (`f_<dueño>_<proveedor>_<folio>`) y por terna
    (`t_<dueño>_<monto>_<fecha>_<proveedor>`), construidas SIEMPRE con
    `FirebaseServicio.llaveFolio()/llaveTerna()` (escribir y buscar usan
    el mismo método). Solo lecturas PUNTUALES del dueño (la regla permite
    el `get` aunque el documento no exista, para que «no hay duplicado»
    no llegue como PERMISSION_DENIED). Un folio repetido en OTRA empresa
    ya NO es duplicado; las facturas de prueba con la llave vieja
    (`f_<folio>` / `t_<monto_fecha_slug>`) se re-siembran sin más.
  - (del S6, cerradas por las reglas) `clientes`, `proveedores` y
    `notificaciones` solo las toca la cuenta de servicio del camino
    servidor. El buzón FCM del S6 se retiró de la app (correcciones
    24/10): el proveedor vive de `ordenes` en tiempo real y los avisos
    S10 son locales (RecordatorioWorker).
- Storage: `facturas/<timestamp>.jpg` y
  `comprobantes/<ordenId>/<timestamp>.jpg` (correcciones 29/10: un
  archivo NUEVO por pago — storage.rules permite create y CIERRA update,
  así nadie reemplaza el comprobante de otra persona).
- Los motores originales (`motores/*.py/.c/.pl`) quedan como REFERENCIA
  académica y para la demo con Laragon; `Motores.java` replica sus reglas
  con los mismos resultados. `regla.pl` sigue con 10 argumentos.
- Login: sin campo usuario ni IP; el correo ES el usuario (Firebase Auth).

## 5. Qué falta

- ~~S11: CRUD de administración migrado a Firestore (AdminActivity)~~ —
  DESCARTADO en el S10.3: el rol único Cliente cubre todo y NO se agregan
  usuarios (un correo por empresa).
- Probar el flujo completo en el Motorola físico (ideal en dos sesiones:
  una de proveedor y otra de cliente).
- RF-M8 (cola offline) — único RF pendiente del Incremento 1.
- ~~Reglas de seguridad de Firestore/Storage~~ HECHO (22-24/10):
  `firestore.rules`/`storage.rules` en la raíz. El `allow update` de
  `ordenes` se corrigió (correcciones 24/10: decía `autenticado() || …`
  y el `||` suelto dejaba editar CUALQUIER orden a cualquiera con sesión;
  ahora `autenticado() && (proveedor dueño || cliente con hasOnly)`).
  Publicación: `firestore.rules` publicado EN VIVO el 29/10 con
  `firebase deploy --only firestore:rules` (Firebase CLI, cuenta dueña).
  `storage.rules` queda SIN publicar por decisión económica: Storage
  exige el plan Blaze (tarjeta) en proyectos post-Oct/2024 y no se
  cuenta con el medio para contratarlo (INSTALACION.md §8.4, Bitácora
  entrada 11). Queda como deuda v1.1 la verificación de
  correo al registrarse.
