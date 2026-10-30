# Acta de Capacitación — CPP
## Plataforma Inteligente para la Gestión de Cuentas por Pagar

> **Entregable de la actividad 9 del cronograma** (Progreso.docx: «Capacitar
> administradores y usuarios financieros»). Versión 1.0 · 24/10/2026.
> Material base: `Manual_Usuario.md` v1.0 (usuario final) y
> `Manual_Tecnico.md` v1.0 (administración). Guion práctico de demostración:
> `SPRINT10_v1.md` §3 (verificado paso a paso en un teléfono físico).

---

## 1. Datos generales

| Campo | Detalle |
|---|---|
| Sistema | CPP — app Android 0.19.0-v1 (versionCode 29) |
| Modalidad | Demostración práctica guiada + manual de usuario entregado |
| Dispositivo de práctica | Motorola edge 50 fusion (Android 16); emulador API 36 |
| Duración estimada | 45 min (administrador) · 25 min por rol de usuario |
| Material que se entrega | `Manual_Usuario.md` (impreso/PDF), cuentas demo (`cliente@cpp.demo`, `proveedor@cpp.demo`) |

## 2. Perfiles capacitados y contenido

### 2.1 Usuario CLIENTE (la empresa que paga) — 25 min

| # | Tema | Práctica verificada |
|---|---|---|
| 1 | Crear cuenta / entrar (§3 del manual) | Alta con correo+contraseña; UN correo por empresa |
| 2 | Candado biométrico (§3.3) | Reapertura con sesión abierta → huella/PIN del teléfono |
| 3 | Cuentas por pagar (§5.1) | Chips POR PAGAR / PAGADAS; alertas por color (por vencer ⏰, vencida 🚨) |
| 4 | Pagar con comprobante (§5.2) | Foto del comprobante → cuenta pasa a PAGADAS |
| 5 | Enviar factura (§5.3) | Foto ORIGINAL → OCR → riesgo → conciliación → veredicto en 1-3 s |
| 6 | Ver facturas y filtros (§5.4) | Badges ✓ Aprobada / ! Discrepancia / ⏳ Pendiente |
| 7 | Recordatorios (S10.4) | Notificación a 3 días / 1 día / vencida; permiso Android 13+ |

### 2.2 Usuario PROVEEDOR (el negocio que cobra) — 25 min

| # | Tema | Práctica verificada |
|---|---|---|
| 1 | Crear cuenta de proveedor | Alta con nombre del negocio |
| 2 | Subir cuenta al cliente (§4.1) | Correo del cliente verificado al momento; código, monto, fecha límite |
| 3 | Seguimiento en vivo (§4.2) | La lista se actualiza sola cuando el cliente paga |
| 4 | Cuenta vencida (§4.3) | Reabrir pago o agregar interés acordado |

### 2.3 ADMINISTRADOR del proyecto — 45 min

| # | Tema | Referencia práctica |
|---|---|---|
| 1 | Preparar Firebase (Auth, Firestore, Storage) | `INSTALACION.md` §8.1-§8.2 |
| 2 | Sembrar cuentas demo y re-sembrar | `INSTALACION.md` §8.3 · `test_s10.ps1 -Semilla` |
| 3 | Publicar reglas de seguridad (Firestore) | `INSTALACION.md` §8.4 · `firebase deploy --only firestore:rules` |
| 4 | Verificar el sistema (C10) | `test_s10.ps1` → 7/7 |
| 5 | Consola: resetear clave / borrar cuenta | Sesión vigilada: el teléfono expulsa al usuario en vivo (S10.7) |
| 6 | Soporte nivel 1-2-3 y mantenimiento | `Manual_Tecnico.md` §10 |

## 3. Método y evaluación

- **Método**: demostración en vivo con las cuentas demo sembradas
  (OC-101 por vencer, OC-102 a 3 días, OC-103 vencida), siguiendo el guion
  `SPRINT10_v1.md` §3; luego cada capacitado repite el flujo de SU rol solo.
- **Criterio de acreditación**: el capacitado completa el flujo de su rol sin
  ayuda (cliente: pagar + conciliar una factura; proveedor: subir una cuenta;
  administrador: re-sembrar y pasar `test_s10.ps1`).
- **Retroalimentación**: dudas frecuentes resueltas con la tabla de preguntas
  frecuentes del `Manual_Usuario.md` §7.

## 4. Constancia

| Rol capacitado | Nombre | Fecha | Acreditado (sí/no) | Firma |
|---|---|---|---|---|
| Cliente (usuario financiero) | ______________ | ____/____/2026 | ____ | ________ |
| Proveedor | ______________ | ____/____/2026 | ____ | ________ |
| Administrador | ______________ | ____/____/2026 | ____ | ________ |
| Imparte la capacitación | ______________ | ____/____/2026 | — | ________ |

> El flujo demostrado fue verificado de punta a punta en dispositivo físico
> (evidencia en `Bitacora.txt` §3 y `Reporte_QA.md` §3, checkpoints C2-S10).

*Fin del documento — Acta_Capacitacion.md v1.0.*
