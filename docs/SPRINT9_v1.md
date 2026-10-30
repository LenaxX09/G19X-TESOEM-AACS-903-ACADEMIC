# SPRINT 9 — «Inteligencia del tablero» (RF-L3 · RF-D5 · RF-M6 · RF-M7)

Guía rápida del Sprint 9 de la v1.0. Lo nuevo, en una línea cada cosa:

| Requisito | Qué se construyó | Dónde |
|---|---|---|
| RF-L3 Duplicados | Pre-check PHP (mismo folio o monto+fecha+proveedor por slug) + REGLA 0 de regla.pl (10.º argumento si/no) → `DISCREPANCIA:FACTURA_DUPLICADA` con `duplicada_de` | `api/procesar.php`, `motores/regla.pl` |
| RF-D5 OCR completo | El OCR devuelve `texto` (crudo, contrato aditivo) y se persiste en `facturas.texto_ocr` (MEDIUMTEXT); visible en el detalle | `motores/procesar_factura.py`, migrar §7, `api/procesar.php` |
| RF-M6 Filtros | Chips ✅/⚠️/⏳ clicables del listado: tap = filtrar, tap de nuevo = quitar (chip activo sólido; conteos del total) | `ListadoActivity.java` |
| RF-M7 Alertas | `pagos.php` calcula `fecha_limite` / `dias_restantes` / `vence_pronto` (fecha del documento +30 días, config) y la app pinta en ROJO lo que vence en ≤3 días o ya venció | `api/pagos.php`, `PagosActivity.java` |
| FIX S8.3 | La app ya no envía `pagar` seco (el API lo rechazaba): botón «📷 Adjuntar comprobante y pagar» → galería → ≤1600 px JPEG 85 → `comprobar` con `imagen_base64` | `PagosActivity.java` |

App resultante: **0.13.1-v1 (versionCode 20)** — desde el ajuste S9.1, en
POR PAGAR no se muestra ninguna foto: la cuenta se paga adjuntando el
comprobante y pasa sola a PAGADAS.

## 1. Preparar el entorno

1. Laragon: arrancar **MySQL** y **Apache** (si no están, ver Bitacora §6).
2. Aplicar la migración si viene de una BD anterior al S9 (los ALTER ya
   aplicados fallan con error inofensivo; si prefiere, aplique solo el §7):
   ```
   mysql -u root cpp < database\migrar_v1.sql
   ```
3. Publicar el backend: `powershell -File .\desplegar.ps1`
4. Compilar el APK: `cd android && build.cmd` (usa el JBR 21).

## 2. Probar el checkpoint C9 (re-ejecutable)

```powershell
powershell -File .\pruebas\test_s9.ps1
```

**Importante:** la detección de duplicados es GLOBAL. Para que la regresión
del trío §9.2 salga exacta (test_c5.ps1) la base debe estar limpia:

```powershell
# respaldo + limpieza (como en la demo del 01/10)
mysqldump -u root cpp facturas > database\backup.sql
mysql   -u root cpp -e "TRUNCATE TABLE facturas;"
powershell -File .\pruebas\test_c5.ps1   # trío exacto: 27.3 / 53.0 MONTO_EXCEDE / PROVEEDOR_NO_COINCIDE
powershell -File .\pruebas\test_s9.ps1   # 14 pruebas
```

## 3. Probar a mano en el móvil (0.13.0-v1)

1. **Filtros (RF-M6):** «📋 Ver facturas» → tocar el chip ⚠️ → solo
   discrepancias y el chip queda relleno → volver a tocarlo → todas.
2. **Duplicados (RF-L3):** enviar la MISMA foto dos veces contra la misma
   orden → la segunda llega «Con Discrepancia · FACTURA_DUPLICADA» con el
   consejo 💡 de revisar la original.
3. **Vencimientos (RF-M7):** «💰 Cuentas por pagar» → lo que vence en ≤3
   días sale en ROJO (⏰/🚨); al tocar una factura se ve «📅 Límite de pago».
4. **Pago con comprobante (S8.3/S9.1):** en POR PAGAR, tocar una factura →
   diálogo SIN foto, solo datos → «📷 Adjuntar comprobante y pagar» →
   elegir la foto del comprobante → el servidor la valida y la cuenta
   pasa sola a PAGADAS (sin comprobante no se puede marcar pagada).

## 4. Contratos que cambiaron (ojo al integrar)

- `regla.pl`: **10 argumentos** (nuevo último: `si|no` duplicado). Con 9
  responde `DISCREPANCIA:ARGUMENTOS_INVALIDOS`.
- OCR (`procesar_factura.py`): el JSON gana `"texto"` (aditivo).
- `pagos.php` POST: solo `comprobar` (con `imagen_base64`) y `despagar`.
- `pagos.php` GET: cada fila de `por_pagar`/`pagadas` trae `fecha_limite`,
  `dias_restantes` y `vence_pronto`.

## 5. Configuración nueva (api/config.php)

- `PAGO_DIAS_LIMITE` = 30 — días desde la fecha del documento (o de subida)
  hasta la fecha límite de pago.
- `ALERTA_VENCE_EN_DIAS` = 3 — umbral de la alerta roja (RF-M7).

## 6. Qué falta

- Instalar la 0.13.0-v1 en el Motorola y dar una vuelta visual.
- RF-M8 (cola offline) — único RF pendiente del Incremento 1.
- Retro H4 con usuarios ajenos (SPRINT4_DEMO.md §3).
- Deuda v1.1: token sin cifrar en prefs, atajo `?token=`, registro de
  proveedor sin verificación, límites de intentos de login.
