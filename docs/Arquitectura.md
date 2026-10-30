# Documento de Arquitectura — CPP
## Plataforma Inteligente para la Gestión de Cuentas por Pagar

> **Entregable de la actividad 3 del cronograma** (Progreso.docx: «Diseño de
> arquitectura de software e Inteligencia Artificial»).
> Versión 1.0 · 24/10/2026 · App actual: **0.19.0-v1 (versionCode 29)**.
>
> Fuentes: `README.md` §2 (arquitectura de entrega), `SPRINT10_v1.md`,
> `Bitacora.txt` §4-§5, `Explicacion.txt` (PARTES 1-16), `PRD_CPP.md`,
> `INSTALACION.md` y el propio código del repositorio.

---

## 1. Propósito y alcance

Este documento describe la arquitectura del sistema CPP: sus dos caminos de
entrega, los componentes, el modelo de datos, los flujos principales, la
seguridad y las decisiones de diseño (con su justificación). Está dirigido al
equipo técnico y al revisor del proyecto; la instalación paso a paso vive en
`INSTALACION.md` y la explicación académica en `Explicacion.txt`.

## 2. Visión general del sistema

CPP automatiza la gestión de cuentas por pagar de una PyME: el **proveedor**
registra lo que el cliente le debe; el **cliente** (la empresa, UNA cuenta
por correo) ve sus cuentas al instante, las paga adjuntando un comprobante y
concilia sus facturas fotografiándolas — la app extrae los datos por OCR,
calcula un **riesgo financiero**, aplica **reglas de conciliación** (reglas
0-5) y detecta **duplicados**, dejando un veredicto auditable con la foto de
respaldo. Todo sin teclear IPs ni levantar servidores.

Las cinco tecnologías del plan (Android/Java, PHP/MySQL, Python, C y Prolog)
siguen presentes; el Sprint 10 las reorganizó en **dos caminos de entrega**
con el mismo veredicto.

## 3. Arquitectura de entrega: DOS caminos (decisión del 22/10/2026)

### 3.1 Camino PRINCIPAL — sin servidor (lo que corre la app v1)

App Android (Java) + **Firebase**, con el pipeline **EN EL TELÉFONO**:

- **Firebase Auth** (correo/contraseña) da la identidad. Un correo por
  empresa cliente; el rol (cliente|proveedor) vive en Firestore y es
  inmutable desde la app.
- **Cloud Firestore** guarda perfiles, cuentas y facturas conciliadas
  (colecciones en §5).
- **Firebase Storage** guarda las imágenes (`facturas/`, `comprobantes/`).
- **`Motores.java`** (en el teléfono): OCR con **ML Kit**, riesgo (fórmula
  del módulo C) y conciliación (réplica regla por regla de `regla.pl`,
  reglas 0-5) + pre-check de duplicados por lectura puntual al índice
  `facturasLookup`.
- Avisos **locales** con WorkManager (recordatorios de pago, S10.4); el
  buzón del proveedor es un **oyente en tiempo real** de `ordenes`.

### 3.2 Camino SERVIDOR — Laragon (demo académica de los motores originales)

`api/facturas.php` + `api/procesar.php` orquestan los tres motores
originales sobre **MySQL** (BD `cpp`): Python (`motores/procesar_factura.py`,
OCR pytesseract + Tesseract) → C (`motores/clasificador_riesgo.c`, riesgo) →
Prolog (`motores/regla.pl`, conciliación), con tablas `usuarios`,
`ordenes_compra`, `facturas`, `historial_proveedor` y `sesiones`. Mismos
contratos y mismos veredictos que el camino principal: el trío de pruebas
§9.2 (A/B/C) sale idéntico por los dos caminos; `Motores.java` queda como el
**modo sin conexión** equivalente de los motores.

### 3.3 Diagrama de cuál camino es cuál

```
                        FACTURA (foto)
                              │
        ┌─────────────────────┴──────────────────────────────┐
        │ CAMINO PRINCIPAL (sin servidor · app v1)           │ CAMINO SERVIDOR (Laragon · demo S1-S9)
        │                                                    │
        │ [App Android]                                      │ [App S2-S9 / Postman]
        │  OCR ......... ML Kit (EN EL TELÉFONO)             │      │ JSON + imagen Base64
        │  Riesgo ....... Motores.java (fórmula del C)       │      ▼
        │  Reglas ........ Motores.java (reglas 0-5 regla.pl)│ api/facturas.php → procesar.php
        │  Duplicados ... Firestore facturasLookup (RF-L3)   │      ├─ Python · procesar_factura.py
        │        │                                           │      ├─ C ....... clasificador_riesgo.c
        │        ▼                                           │      └─ Prolog .. regla.pl
        │  Firebase: Auth · Firestore · Storage              │             │
        │                                                    │             ▼
        └────────────────────────────────────────────────────┘   MySQL (facturas/ordenes/usuarios)
```

## 4. Componentes del sistema

### 4.1 App Android (`android/app/src/main/java/com/cpp/captura/`)

| Clase | Responsabilidad |
|---|---|
| `LoginActivity` | Firebase Auth: login y alta de cuentas (cliente/proveedor, S10.3) |
| `CandadoActivity` + `Candado` | Candado biométrico obligatorio: huella/PIN del teléfono (S10.5) |
| `MainActivity` | Captura: foto/galería → pipeline en el teléfono → veredicto (RF-M2/M3/M4) |
| `PagosActivity` | Cuentas por pagar: POR PAGAR / PAGADAS, pago con comprobante (RF-P1..P3) |
| `ListadoActivity` | Facturas con badges y chips de filtro (RF-M5/M6) |
| `ProveedorActivity` | El proveedor sube cuentas y decide sobre vencidas (S10.6) |
| `RecordatorioWorker` | Recordatorios locales: 3 días / 1 día / vencida (RF-M7, S10.4) |
| `Motores` | OCR ML Kit + riesgo + conciliación + slug (réplica de los motores) |
| `FirebaseServicio` | Acceso a Firebase: perfiles, ordenes, facturas, Storage, sesión vigilada (S10.7) |
| `PushServicio` | Receptor FCM (queda para la demo académica; S10 no usa push) |
| `Sesion` | Sesión persistida + expulsión al login |

Build por consola: `android\build.cmd` (Gradle 8.13, JBR 21, `minSdk 24`,
`targetSdk 36`). APK: `android\app\build\outputs\apk\debug\app-debug.apk`.

### 4.2 Firebase (proyecto `cpp-facturas`)

- **Auth**: proveedor Email/Password (habilitable por API v2 con
  `api/habilitar_email_auth.php`, sin abrir la consola).
- **Firestore** y **Storage** con **reglas de seguridad** versionadas en la
  raíz del repo (`firestore.rules`, `storage.rules`) y `firebase.json` para
  `firebase deploy` (o `herramientas/publicar_reglas.php`, que publica por
  API con la cuenta de servicio).
- La **cuenta de servicio** (Admin) solo la usan los scripts PHP CLI
  (semilla, habilitar auth, publicar reglas); la app NUNCA la ve.
  `google-services.json` (app) sí va en el repo: solo identifica el
  proyecto, no es secreto.

### 4.3 Motores de análisis (los dos caminos)

| Motor | Camino servidor (original) | Camino principal (en el teléfono) |
|---|---|---|
| OCR | `procesar_factura.py` (pytesseract + Tesseract 5.4 + modelo `spa`) | ML Kit (on-device) en `Motores.java` |
| Riesgo | `clasificador_riesgo.c` (GCC): % de riesgo vs historial del proveedor, UMBRAL_RIESGO = 40 | `Motores.clasificadorRiesgo` (misma fórmula; historial = facturas propias en Firestore) |
| Conciliación | `regla.pl` (SWI-Prolog): APROBADA / DISCREPANCIA:<motivo>, reglas 0-5 | `Motores.conciliar` (réplica de las mismas reglas 0-5) |
| Duplicados (RF-L3) | Pre-check PHP + regla 0 de Prolog | Pre-check con lecturas puntuales a `facturasLookup` (f_<folio> y t_<monto_fecha_slug>) |

**Contratos fijados ANTES de programar** (principio contract-first,
`MVP_CPP.md` §6.4): cada motor CLI escribe UNA línea JSON/valor en stdout;
si un motor falla, la factura NO se pierde (queda Pendiente, RNF-5).

## 5. Modelo de datos

### 5.1 Firestore (camino principal)

| Colección | Contenido | Quién la escribe |
|---|---|---|
| `usuarios/{uid}` | nombre, correo, rol (cliente\|proveedor), negocio, empresa | la app al registrarse (rol inmutable después) y la semilla |
| `clientesPorCorreo/{slug}` | directorio público PUNTUAL (uid + correo) para `existeCorreo()` — `usuarios` NO se puede listar | app (alta cliente) y semilla |
| `ordenes/{id}` | codigo, negocio, proveedorUid, clienteCorreo, monto, fechaEmision, fechaLimite, estado (pendiente\|pagada), pagadaEn, comprobanteUrl, creado, reabierta, interes | el proveedor (crear); el cliente SOLO los campos de pago (estado/pagadaEn/comprobanteUrl); el proveedor reabre/agrega interés |
| `facturas/{id}` | folio, proveedor, proveedorSlug, fecha, monto, riesgo, veredicto, motivo, ordenId, ordenCodigo, subidoPor, fotoUrl, creado | el cliente al conciliar (el veredicto NO se edita) |
| `facturasLookup/{id}` | índice anti-duplicados RF-L3: `f_<folio>` y `t_<monto_fecha_slug>` → facturaId, dueno | la app al guardar factura; solo lecturas puntuales |
| `clientes`, `proveedores`, `notificaciones` | (del S6, cerradas por las reglas) | solo la cuenta de servicio del camino servidor |

Storage: `facturas/<timestamp>.jpg` y `comprobantes/<ordenId>.jpg`.

### 5.2 MySQL (camino servidor, BD `cpp`)

`usuarios` (rol, negocio, hash bcrypt), `ordenes_compra`, `facturas`
(estado, folio, texto_ocr, pagada, pagada_en, ruta_imagen),
`historial_proveedor` (alimenta el riesgo) y `sesiones` (token X-CPP-Token
de 8 h). Esquema: `database/seed.sql` + `database/migrar_v1.sql`.

## 6. Flujos principales (camino sin servidor)

1. **Registro/login**: alta de cliente (UN correo por empresa) o proveedor →
   Auth + perfil `usuarios/{uid}` (+ entrada en `clientesPorCorreo` si es
   cliente). Al reabrir la app con sesión vigente: **candado** biométrico
   (S10.5) y **sesión vigilada** (S10.7: si borran la cuenta en Firebase, el
   teléfono se entera en vivo y regresa al login).
2. **Proveedor sube una cuenta**: correo del cliente (verificado contra
   `clientesPorCorreo`), código, monto y fecha límite → `ordenes` con
   estado `pendiente`.
3. **Cliente paga**: la cuenta aparece al instante (oyente en vivo); «por
   pagar» → adjunta comprobante → sube a Storage → marca
   estado/pagadaEn/comprobanteUrl (único cambio permitido al cliente).
4. **Cuenta vencida** (S10.6): el cliente YA no puede pagarla (pago
   CERRADO); el PROVEEDOR decide: **Reabrir pago** o **Agregar interés**
   (suma al monto y reabre). El recordatorio diario cambia de mensaje.
5. **Factura**: elegir cuenta → foto/galería → OCR ML Kit → riesgo (con
   historial propio por proveedorSlug) → pre-check de duplicados →
   conciliación (reglas 0-5) → veredicto + foto a Firestore/Storage →
   listado con badges y filtros.
6. **Recordatorios** (S10.4): WorkManager despierta UNA vez al día con red
   y avisa a 3 días, 1 día y vencida (un aviso por cuenta/nivel/día).

## 7. Seguridad

| Capa | Mecanismo |
|---|---|
| Identidad | Firebase Auth (correo/contraseña); la sesión vive en el teléfono |
| Autorización (datos) | `firestore.rules`: rol y correo inmutables; `usuarios` no se lista (existeCorreo usa `clientesPorCorreo`); facturas/órdenes ajenas ilegibles (las consultas van filtradas por `subidoPor` / `clienteCorreo` / `proveedorUid`, porque Firestore NO filtra resultados: rechaza la consulta completa); el cliente solo toca `estado/pagadaEn/comprobanteUrl` de SU orden (`diff().affectedKeys().hasOnly(...)`); colecciones S6 cerradas; comodín final `/{document=**}` cerrado |
| Autorización (imágenes) | `storage.rules`: solo usuarios autenticados, solo JPEG < 8 MB, sin borrar |
| Candado (app) | BiometricPrompt del sistema: huella o PIN del teléfono, obligatorio (S10.5) |
| Sesión | Vigilada (S10.7): cuenta borrada en Firebase → expulsión en vivo |
| Secretos | Cuenta de servicio y clave demo FUERA del repo (`.gitignore`); clave demo por `CPP_DEMO_CLAVE` o parámetro |
| Camino servidor | Login con token de 8 h, bcrypt, control por rol (401/403), saneo de parámetros en `shell_exec` (R3) |

> Nota de operación (24/10): la corrección del `allow update` de `ordenes`
> (paréntesis `||` → `&&`) está en el repo y su ruleset fue subido por API;
> la activación del release en la consola queda como paso del propietario
> del proyecto (ver `Reporte_QA.md` §7).

## 8. Decisiones de arquitectura (con justificación)

1. **Dos caminos de entrega** (22/10): la app v1 no depende del servidor;
   la pila PHP/MySQL+Python+C+Prolog queda como demo académica de los
   motores originales. Motivo: quitar IP/Laragon del camino productivo sin
   tirar el trabajo de los sprints 1-9.
2. **Réplica de los motores en Java** (`Motores.java`): mismos resultados
   regla por regla (verificado con el trío §9.2). Motivo: pipeline en el
   teléfono (velocidad 1-3 s y sin red para el análisis).
3. **Consultas SIEMPRE filtradas** a lo que las reglas permiten: Firestore
   no filtra resultados por sí solo (rechaza la consulta completa si un
   documento no pasa la regla). Por eso no existe `escucharOrdenesTodas` y
   `escucharFacturas` siempre filtra por `subidoPor` (correcciones 24/10).
4. **`facturasLookup` + `clientesPorCorreo`**: índices puntuales para no
   listar colecciones entre clientes (duplicados RF-L3 y verificación de
   correos) — lecturas 1-a-1, sin exponer datos ajenos.
5. **Sin orderBy en las consultas en vivo**: se ordena en Java; evita pedir
   índices compuestos en la consola.
6. **Avisos locales (WorkManager), no push**, en S10: el buzón del
   proveedor es el oyente de `ordenes`; el bloque FCM del S6 se retiró de
   la app (sus colecciones quedaron cerradas por las reglas).
7. **Slug espejo** `fb_slug()` (PHP) ≡ `slug()` (Java): sin acentos,
   minúsculas, solo letras/números — coinciden documentos y avisos.
8. **Roles unificados** (S10.3): UN rol Cliente con todas las funciones (un
   correo por empresa) + rol Proveedor; el CRUD de usuarios se retiró
   (nadie crea cuentas ajenas desde la app).
9. **Contract-first en los motores CLI** (`MVP_CPP.md` §6.4): contratos
   fijados antes de programar; fallo de motor ≠ pérdida de factura (RNF-5).
10. **UI oscura fija estilo Mercado Pago** (S10.2): sin emojis, marcas
    tipográficas (✓ / ! / ⏳), tarjetas planas 16dp, botones píldora.

## 9. Atributos de calidad (vs PRD §13)

- **Rendimiento**: veredicto completo en 1-3 s EN EL TELÉFONO (meta ≤ 30 s);
  oyentes en tiempo real (sin botón «actualizar»).
- **Precisión OCR**: 100 % de campos clave con foto original
  (~1202×1600 px); hallazgo: imágenes reenviadas por chat (~473×631)
  confunden dígitos → el manual de usuario recomienda el original.
- **Confiabilidad**: veredicto inmutable (facturas no se editan); pago solo
  con comprobante (S8.3); nada se pierde si un motor falla (RNF-5).
- **Seguridad**: §7; dos revisiones externas de las reglas superadas
  (22/10 y 24/10 — ver `Reporte_QA.md` §7).
- **Usabilidad**: candado biométrico sin configuración; alertas por color;
  chips de filtro; demo reproducible con datos sembrados.

## 10. Despliegue y entornos

| Entorno | Qué corre | Cómo se prepara |
|---|---|---|
| Teléfono del cliente (v1) | App 0.19.0-v1 + Firebase | `INSTALACION.md` §8 (proyecto, Auth, reglas, semilla) + `test_s10.ps1` |
| Demo académica (motores) | Laragon: Apache+MySQL+PHP + motores | `INSTALACION.md` §1-§5 + `desplegar.ps1` |
| Desarrollo | Android Studio (JBR 21) + php.exe CLI | `android\build.cmd`; scripts de `api/` con la cuenta de servicio |

## 11. Deuda técnica y evolución (v1.1+)

- RF-M8 (cola offline): único requisito del Incremento 1 pendiente (8/9).
- Verificación de correo al registrarse (deuda v1.1).
- Camino servidor: imagen multipart (hoy Base64) y token cifrado en prefs.
- Publicar reglas por defecto (el script `herramientas/publicar_reglas.php`
  ya lo hace por API cuando el SA tiene el rol «Firebase Rules Admin»).

*Fin del documento — Arquitectura.md v1.0 · derivado del repositorio CPP.*


