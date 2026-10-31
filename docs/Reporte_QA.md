# Reporte de QA — CPP
## Plataforma Inteligente para la Gestión de Cuentas por Pagar

> **Entregable de la actividad 6 del cronograma** (Progreso.docx: «Validar
> integración, seguridad, rendimiento y precisión»). Versión 1.0 · 24/10/2026.
> Evidencia verificable en el repo: `test_*.ps1`, `Bitacora.txt` (§2-§3),
> `SPRINT*_v1.md` y `Explicacion.txt`.

---

## 1. Resumen ejecutivo

El sistema se validó con **10 checkpoints** (uno por sprint, re-ejecutables
por script), pruebas de integración por HTTP, pruebas **end-to-end en
emulador y en un teléfono físico** (Motorola edge 50 fusion, Android 16),
regresiones del flujo crítico (trío §9.2) y **dos revisiones externas de
seguridad** sobre el repositorio (22/10 y 24/10) cuyas observaciones fueron
corregidas en su totalidad. Resultado global:

| Métrica | Resultado |
|---|---|
| Checkpoints superados | **10/10** (C1-C4, C5 15/15, S6 verificado, C7 15/15, C8 12/12, C9 14/14, C10 7/7) |
| Requisitos del Incremento 1 | **8/9** (solo RF-M8 offline pendiente) |
| Regresión del flujo crítico (trío A/B/C) | **Exacta** en servidor y autenticada; mismo veredicto EN EL TELÉFONO |
| KPI tiempo de procesamiento | **1-3 s** en teléfono físico (meta ≤ 30 s) |
| KPI precisión OCR | **100 %** de campos clave con foto original |
| Defectos de seguridad detectados y corregidos | 2 rondas de revisión externa → 100 % atendidas |

## 2. Estrategia de pruebas

1. **Por capas**: motores CLI por separado (C3) → API PHP por HTTP con
   PowerShell/Postman (C1, C5, C7-C9) → integración completa (C4) → app
   end-to-end (C2, S6, demostraciones en móvil físico).
2. **Checkpoints re-ejecutables**: cada sprint cierra con un script
   (`test_c5.ps1`, `test_s7/s8/s9.ps1`, `test_s10.ps1`) que cualquier
   revisor puede correr de nuevo.
3. **Casos sembrados**: datos diseñados para forzar cada veredicto
   (aprobada, monto excede, proveedor no coincide, duplicada, vencida,
   por vencer) — el trío §9.2 de `MVP_CPP.md`.
4. **Regresión**: el trío A/B/C se re-corrió tras cada sprint mayor
   (incluida la versión autenticada de C5) para garantizar que nada nuevo
   rompiera el pipeline.
5. **Pruebas en dispositivo real**: emulador (API 30/36) + Motorola físico;
   capturas y evidencias asentadas en `Bitacora.txt` §3.
6. **Revisión externa de código y seguridad**: un agente lector revisó el
   repo (2 rondas: 22/10 y 24/10); sus hallazgos y correcciones en §7.

## 3. Resultados por checkpoint

| CP | Fecha | Alcance validado | Resultado |
|---|---|---|---|
| C1 | 23/09 | API base: `POST facturas.php` → 201 «Pendiente» + `GET` lista (RF-B1/B2/B5, RF-D1..D4) | **OK** (PowerShell + Postman) |
| C2 | 28/09 | App Android: cámara/galería → envío Volley (RF-M2/M3/M4) | **OK** (emulador) |
| C3 | 28/09 | Los 3 motores por separado y sumados, con sus contratos | **OK** |
| C4 | 30/09 | `procesar.php` orquesta Python→C→Prolog; badges en el móvil (RF-B3/M5); **guion §9.2 EN VIVO en el Motorola** | **OK** — trío exacto |
| C5 | 09/10 | Login + token 8 h + roles (401/403) + CRUD (RF-M1/B4/B6) | **15/15** + regresión §9.2 AUTENTICADA exacta |
| S6 | 15/10 | Buzón del proveedor en vivo (Firestore) + push FCM (RF-F1..F4) | **Verificado** en emulador y Motorola (buzón histórico, doc en vivo ~5 s, push con texto exacto) |
| C7 | 15/10 | Cuentas de proveedor: registro público, filtro por negocio, 403 orden ajena (RF-F5..F7) | **15/15** |
| C8 | 16/10 | Cuentas por pagar: por_pagar/pagadas, foto del comprobante (RF-P1..P3) | **12/12** |
| C9 | 17/10 | Duplicados (RF-L3), OCR completo (RF-D5), filtros (RF-M6), alertas de vencimiento (RF-M7) | **14/14** |
| C10 | 20/10 | Firebase puro SIN Laragon: Auth Email/Password, perfiles, 3 cuentas demo con 1 vencida + 1 por vencer | **7/7** |

Verificaciones posteriores sobre C10 (re-ejecutable):
`powershell -File test_s10.ps1` → 7/7 OK (20/10, proyecto `cpp-facturas`).

## 4. Casos de prueba representativos

### 4.1 Trío de conciliación (regresión del flujo crítico, guion §9.2)

| Caso | Entrada | Esperado | Obtenido |
|---|---|---|---|
| A | Factura $27.30 dentro de la orden | `APROBADA`, riesgo bajo | **✅ APROBADA · riesgo 27.3** |
| B | Factura que excede el monto autorizado | `DISCREPANCIA: MONTO_EXCEDE` | **⚠️ MONTO_EXCEDE · riesgo 53.0** |
| C | Proveedor distinto al de la orden | `DISCREPANCIA: PROVEEDOR_NO_COINCIDE` | **⚠️ PROVEEDOR_NO_COINCIDE ✔** (+ `FECHA_FUERA_DE_RANGO`, RNF-5) |

Ejecutado: por HTTP sin auth (C4), **autenticado por rol** (C5), en el
teléfono físico (demo C4) y replicado por `Motores.java` en el teléfono
(Sprint 10) — **mismo veredicto en todos los caminos**.

### 4.2 Duplicados (RF-L3)

- Misma factura por **folio** → detectada (regla 0 / `facturasLookup f_<folio>`).
- Misma **terna monto+fecha+proveedor** sin folio → detectada
  (`t_<monto_fecha_slug>`).
- Casos sembrados en C9: **100 % detectados** (14/14).

### 4.3 Alertas y recordatorios (RF-M7 / S10.4)

- Datos sembrados: OC-102 vence en 3 días, OC-103 VENCIDA, OC-101 a 10 días.
- Resultado: ⏰/🚨 en rojo en la lista, **notificación local** por «vence en
  3 días» y «está VENCIDA», y **sin aviso** para la de 10 días. Anti-spam:
  un aviso por cuenta/nivel/día. T6 de C10: 7/7.

### 4.4 Seguridad funcional (C5)

Login correcto → token; rol insuficiente → **401/403** exactos; CRUD de
usuarios/órdenes solo admin (con protección ULTIMO_ADMIN); peticiones sin
token rechazadas. En S10 el equivalente lo exigen las **reglas de
Firestore/Storage** (§7).

### 4.5 Pago con comprobante (RF-P2/S8.3)

El pago REQUIERE la foto del comprobante (sin foto no hay pago); al pagar,
la cuenta pasa sola a PAGADAS en el teléfono del cliente y del proveedor
(oyentes en tiempo real). C8 12/12 + demo completa en emulador.

### 4.6 S10.4-S10.7 (prueba guiada paso a paso)

Guion completo en `SPRINT10_v1.md` §3 (9 pasos): recordatorios, candado
biométrico (aparece, cancela y reintenta — verificado en el Motorola),
cuenta vencida → cerrada → reabrir/interés, y sesión vigilada (borrar la
cuenta en la consola → expulsión en vivo en segundos).

## 5. Trazabilidad requisitos → evidencia

| Requisito (PRD) | Estado | Evidencia |
|---|---|---|
| RF-M1 login | ✔ | C5 (15/15); S10: Firebase Auth (C10 T1 login REAL) |
| RF-M2 cámara | ✔ | C2, demo C4 en Motorola |
| RF-M3 galería | ✔ | C2 + demo (adelantado en 0.5.0) |
| RF-M4 envío de imagen | ✔ | C2/C4 (Volley+Base64); S10: pipeline en el teléfono |
| RF-M5 estado de facturas | ✔ | C4 (badges), S10 listado en vivo |
| RF-M6 filtros por estado | ✔ | C9 14/14 (chips) |
| RF-M7 alertas de vencimiento | ✔ | C9 (rojo ≤3 días) + S10.4 recordatorios (C10 T6) |
| RF-M8 cola offline | ✖ pendiente | Deuda v1.1 (Bitacora §1) — único RF del Incremento 1 sin cerrar (8/9) |
| RF-B1..B6 (API) | ✔ | C1, C4, C5 (15/15 con 401/403 y CRUD) |
| RF-D1..D4 (BD) | ✔ | C1 (3 tablas + seed + FK) |
| RF-L3 duplicados | ✔ | C9 14/14 (folio y terna; en S10 vía `facturasLookup`) |
| RF-D5 OCR completo | ✔ | C9 (texto OCR guardado y visible en el detalle) |
| RF-F1..F4 (Firebase avisos) | ✔ | S6 verificado en emulador + Motorola (15/10) |
| RF-F5..F7 (cuentas proveedor) | ✔ | C7 15/15 |
| RF-P1..P3 (pagos) | ✔ | C8 12/12 + S8.3 comprobante obligatorio |
| S10.3-S10.7 | ✔ | C10 7/7 + guion `SPRINT10_v1.md` §3 |

## 6. KPIs del PRD (§13) vs medido

| KPI | Meta | Medido |
|---|---|---|
| Tiempo subida → estado final | ≤ 30 s | **1-3 s** en el teléfono físico (pipeline on-device) |
| Precisión OCR (campos clave) | ≥ 80 % | **100 %** con foto original (~1202×1600); hallazgo: fotos de chat (~473×631) confunden dígitos → aviso en `Manual_Usuario.md` |
| Duplicados detectados (casos sembrados) | 100 % | **100 %** (C9, folio y terna) |
| Conciliación sin intervención manual | ≥ 70 % | Caso A aprobado directo; B/C resueltos con motivo accionable (guion §9.2) |
| Pérdida de capturas offline | 0 % | Pendiente con RF-M8 (no hay cola offline aún; sin conexión la app avisa y no envía) |

## 7. Pruebas de seguridad y revisiones externas

**Tres rondas de revisión del repositorio** por un agente lector externo
(`correcciones2.docx` conserva la tercera):

- **1ª revisión → correcciones 22/10**: reglas de Firestore/Storage nuevas
  (rol inmutable, `usuarios` sin listar, facturas/órdenes ajenas ilegibles,
  Storage solo JPEG < 8 MB); consultas alineadas (proveedor escucha
  filtrado, `historialPromedio` por `subidoPor`); clave demo fuera del
  código y del repo (env `CPP_DEMO_CLAVE`/parámetro); limpieza (.gitignore
  de logs/secretos/respaldos, 31 logs fuera, scripts a `herramientas/`);
  fix del candado que no re-bloqueaba en segundo plano.
- **2ª revisión → correcciones 24/10**: **fix crítico del `allow update`
  de `ordenes`** (era `autenticado() || …`: cualquier usuario con sesión
  podía editar cualquier orden; ahora `autenticado() && (dueño || cliente
  con hasOnly)`); retiro del bloque S6 muerto que escribía en colecciones
  cerradas; `escucharOrdenesTodas` eliminada y `escucharFacturas` sin
  «todas»; `firebase.json` + `herramientas/publicar_reglas.php`;
  documentación de colecciones y contratos (`SPRINT10_v1.md` §4).
- **3ª revisión → correcciones 29/10** (6 puntos): `storage.rules` con
  **comprobantes inmutables** (`comprobantes/<ordenId>/<timestamp>.jpg`:
  create sí, update no — nadie reemplaza el comprobante ajeno) y la app
  sube un archivo NUEVO por pago; `firestore.rules` exige que el
  **proveedor no cambie `proveedorUid`/`clienteCorreo`** de una orden y que
  el **cliente solo ponga `estado` en `pagada|pendiente`**;
  **duplicados POR DUEÑO** (`facturasLookup` con llaves
  `f_<dueño>_<proveedor>_<folio>` / `t_<dueño>_<monto>_<fecha>_<proveedor>`,
  legibles solo por su dueño, `get` permitido cuando el documento no
  existe); **rutas portables** (`api/config.php` con env vars, `.ps1` con
  `$PSScriptRoot`, `.cmd` con `%~dp0`, `desplegar.ps1` con `CPP_WWW` —
  verificado con `git grep`: 0 rutas de equipo en `*.ps1/*.php/*.cmd`);
  raíz ordenada en `docs/` y `pruebas/` + sección «Uso de IA» en el
  readme; `MVP_CPP.md` actualizado con el estado real de la v1 y
  `DEMO.md` con el guion de los dos caminos + **C9 re-corrido 14/14 el
  29/10** (commit base `66de852` + correcciones del día).
- **Publicación de reglas — estado final (29/10 pm)**: `firestore.rules`
  PUBLICADO EN VIVO con el Firebase CLI (`firebase deploy --only
  firestore:rules`, cuenta dueña del proyecto → **Deploy complete ✅**):
  el release pre-fix `4d2fad49` ya NO corre — el riesgo conocido queda
  CERRADO (Bitácora, entrada 11). `storage.rules` queda SIN publicar
  por **decisión económica documentada**: activar Storage exige el plan
  Blaze (tarjeta de crédito) en proyectos creados después de Oct/2024 y
  el equipo **no cuenta con el medio económico para contratarlo**
  (proyecto académico). El archivo queda diseñado y versionado en el
  repo, listo para el día que Storage se active (consola → Storage →
  Reglas → pegar y Publicar, o `php herramientas\publicar_reglas.php
  solo-storage` dándole antes el rol «Firebase Rules Admin» a la cuenta
  de servicio en IAM). Omisión declarada, no un olvido
  (`INSTALACION.md` §8.4).
- **Secretos**: cuenta de servicio y clave demo fuera de git
  (verificado con `git ls-files`); instrucción de rotación de la clave demo
  vieja en `INSTALACION.md` §8.3.

## 8. Defectos detectados y corregidos (historial)

| Cuándo | Defecto | Corrección |
|---|---|---|
| 01/10 | Cámara «invisible» en móviles reales (Android 11+ ocultaba la app de cámara) | `<queries>` en el manifest (0.4.1) |
| 01/10 | Fotos reescaladas mal (inSampleSize salta en potencias de 2) | Reescalado EXACTO a 1600 px (0.4.2) |
| 01/10 | Fotos verticales llegaban acostadas | Rotación EXIF (0.4.3) |
| 14/10 | Crash en TODAS las peticiones autenticadas (headers de Volley inmutables) | HashMap copia en 7 puntos (0.8.2), detectado con emulador API 36 |
| 22/10 | Candado no re-bloqueaba al volver de segundo plano | Conteo de pantallas vivas en `App` + revisión en `onResume` |
| 22/10 | Comentarios con doble encoding (UTF-8) en 2 archivos Java | Reparación de encoding |
| 24/10 | Regla Firestore con `||` suelto (escalada de permisos en `ordenes`) | Paréntesis corregidos + ruleset subido (§7) |
| 24/10 | Código muerto S6 escribiendo en colecciones cerradas | Bloque retirado (decisión: cambiar código, no abrir reglas) |
| 29/10 | Comprobantes de Storage reemplazables por cualquier cuenta (`update` abierto) | `comprobantes/<ordenId>/<timestamp>.jpg` + create-only en `storage.rules` |
| 29/10 | Duplicados GLOBALES: el folio de otra empresa marcaba «duplicada» la factura ajena | Llaves de `facturasLookup` POR DUEÑO y lectura restringida al dueño |

## 9. Estado final y pendientes

**Cerrado**: 10/10 checkpoints; 8/9 RF del Incremento 1; ambos caminos
funcionales; reglas de seguridad corregidas y versionadas; documentación
formal completa (`Arquitectura.md`, `Manual_Tecnico.md`,
`Manual_Usuario.md`, este reporte).

**Pendientes (declarados, no ocultos)**:

1. RF-M8 (cola offline) — único RF del Incremento 1 pendiente.
2. **Publicar `storage.rules`** cuando Storage se active: exige el plan
   Blaze (tarjeta de crédito) y no se cuenta con el medio económico
   para contratarlo — omisión documentada (§7, `INSTALACION.md` §8.4,
   Bitácora entrada 11); el archivo queda versionado en el repo.
   `firestore.rules` ya está publicado en vivo (29/10).
3. Probar el flujo de duplicados por dueño en el teléfono (2 cuentas de
   cliente) y la vuelta completa proveedor↔cliente en el Motorola físico
   (guion `SPRINT10_v1.md` §3 en dos sesiones) — con las reglas ya
   activas; si algo da PERMISSION_DENIED, revisar la regla antes de
   abrirla.
4. Deuda v1.1: verificación de correo al registrarse; multipart; token
   cifrado (camino servidor).

## 10. Cómo re-ejecutar las pruebas

```powershell
# Camino principal (sin Laragon; necesita api\firebase-service-account.json y la clave demo)
$env:CPP_DEMO_CLAVE = '…' ; powershell -File .\pruebas\test_s10.ps1        # C10: 7/7

# Camino servidor (con Laragon: Apache + MySQL + BD cpp restaurada)
powershell -File .\pruebas\test_c5.ps1                                      # 15/15
powershell -File .\pruebas\test_s9.ps1                                      # 14/14

# Compilación y sintaxis
android\build.cmd                                                 # BUILD_OK
php -l herramientas\publicar_reglas.php                           # sin errores
```

*Fin del documento — Reporte_QA.md v1.0.*


