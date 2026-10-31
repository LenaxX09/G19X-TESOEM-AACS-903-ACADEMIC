# DEMO — Los dos caminos, la misma factura, el mismo veredicto (29/10/2026)

> Guion corto para demostrar el sistema EN VIVO: el **camino principal**
> (teléfono + Firebase, el de producción) y el **camino servidor**
> (Laragon + Python + C + Prolog, la demo académica de los motores
> originales). Ambos aplican las mismas reglas 0-5 y el mismo cálculo de
> riesgo, así que el veredicto sale **idéntico**.

## 0. La factura de la demo

`motores\spike\fotos\factura_01.jpg` — Papelería Central · $4,850.00 ·
folio F-1001 · 2026-09-20. Contra una orden de Papelería por ~$3,941.67 de
historial (alza +23.1 %) el resultado esperado en AMBOS caminos es:

```
APROBADA · riesgo 27.3 %  (riesgo < 40 ⇒ aprobable)
```

Extras del trío clásico (mismo guion §9.2 del MVP): `factura_06.jpg`
(Papelería $6,200) → `DISCREPANCIA: MONTO_EXCEDE_ORDEN · 53.0 %` y
`factura_04.jpg` (Papelería $3,680 contra la orden de Ferretería) →
`DISCREPANCIA: PROVEEDOR_NO_COINCIDE`.

## 1. Camino TELÉFONO (principal — Firebase, sin servidor)

Requisitos: APK instalado (compilar: `cd android ; .\build.cmd`), teléfono
con datos/Wi-Fi, dos cuentas (`cliente@cpp.demo` y `proveedor@cpp.demo`, o
las creadas en el registro; semilla: `php api\semilla_firebase.php`).

1. **Proveedor** (teléfono o segunda sesión): entra y toca «➕ Subir
   cuenta» → correo del cliente + monto $4,850 + fecha límite.
2. **Cliente**: entra — la cuenta aparece AL INSTANTE en
   «💰 Cuentas por pagar» (oyente en tiempo real).
3. Cliente → «📷 Enviar factura» → elige ESA cuenta → fotografía
   `factura_01.jpg` (o muéstrala en pantalla y fotografíala) → «Enviar».
4. El pipeline corre **EN EL TELÉFONO**: ML Kit lee el texto,
   `Motores.java` calcula el riesgo (fórmula del C) y concilia (reglas del
   Prolog) → panel **✅ APROBADA · riesgo 27.3 %**.
5. Cliente paga: toca la cuenta → «📷 Adjuntar comprobante y pagar» →
   foto del comprobante → pasa sola a **PAGADAS** (sin evidencia no hay
   pago). ⚠️ Storage está omitido por decisión económica (plan Blaze —
   `INSTALACION.md` §8.4): si la subida de la foto falla en vivo,
   muestra el AVISO de la app (la cuenta queda «por pagar»: el pago no
   se marca sin comprobante) y sigue con el paso 6 — el veredicto del
   pipeline (paso 4) NO depende de Storage y la factura queda guardada.
6. (Opcional) Repite con `factura_06.jpg` → ⚠️ MONTO_EXCEDE_ORDEN, y
   reenvía `factura_01.jpg` → ⚠️ FACTURA_DUPLICADA (regla 0, RF-L3).

## 2. Camino SERVIDOR (Laragon — Python OCR → C riesgo → Prolog)

Requisitos: Laragon (Apache + **el MySQL de Laragon**, nunca los servicios
MySQL8x de Windows), BD `cpp` sembrada (`database\seed.sql` por phpMyAdmin)
y los binarios `python`, `swipl` y `gcc` en el PATH (o definidos en
`CPP_PYTHON_BIN` / `CPP_SWIPL_BIN` / `CPP_GCC_BIN` — `api\config.php` ya
no lleva rutas de equipo).

```powershell
# 1) Arrancar servicios (si Laragon no está corriendo)
#    Laragon → Start All.  Consola directa:
#    C:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysqld.exe --defaults-file=C:\laragon\bin\mysql\mysql-8.0.30-winx64\my.ini
#    C:\laragon\bin\Apache\httpd-2.4.54-win64-VS16\bin\httpd.exe

# 2) (Solo si se clonó el repo) recompilar el motor C y publicar el backend
cd motores ; gcc -O2 -Wall -o clasificador_riesgo.exe clasificador_riesgo.c ; cd ..
powershell -File .\desplegar.ps1          # api + database + motores → www\cpp

# 3) Login (S5: todo autenticado) y subida de la factura
$tok = (Invoke-RestMethod 'http://localhost/cpp/api/sesion.php' -Method Post `
         -Body (@{usuario='capturista';password='demo1234'}|ConvertTo-Json) `
         -ContentType 'application/json').datos.token
$b64  = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\motores\spike\fotos\factura_01.jpg"))
$id   = (Invoke-RestMethod 'http://localhost/cpp/api/facturas.php' -Method Post `
          -Headers @{'X-CPP-Token'=$tok} `
          -Body (@{orden_id=1;imagen_base64=$b64}|ConvertTo-Json) `
          -ContentType 'application/json').datos.factura_id

# 4) El pipeline: PHP enciende Python (OCR) → C (riesgo) → Prolog (regla)
Invoke-RestMethod "http://localhost/cpp/api/procesar.php?id=$id" -Headers @{'X-CPP-Token'=$tok}
```

Respuesta esperada (resumida):

```json
{ "codigo": "OK",
  "datos": { "estado": "Aprobada", "riesgo": 27.3,
             "duracion_pipeline_s": 1.0-1.5 } }
```

`factura_06.jpg` contra la orden 1 → `DISCREPANCIA:MONTO_EXCEDE_ORDEN` con
`riesgo 53.0`; la misma foto dos veces → `FACTURA_DUPLICADA` (regla 0).
Los fallos NO pierden la factura: queda «Pendiente» (RNF-5).

## 3. Verificación re-ejecutable (checkpoint C9, 14 pruebas)

Corrido contra el código actual el **29/10/2026** (commit base `66de852` +
correcciones 29/10: rutas portables, reglas por dueño) — resultado
**14/14 OK**, desde la nueva carpeta del repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\test_s9.ps1
```

Cubre: duplicados por folio y terna (T1), texto OCR persistido (T2),
vencimientos (T3), pago con comprobante obligatorio (T4/T5), doble pago
(T6), despagar (T7), regla.pl por CLI con 10/9 argumentos (T8) y el OCR
con campo `texto` (T9). La regresión completa del trío está en
`.\pruebas\test_c5.ps1` (15/15; exige BD limpia para el trío exacto).

## 4. Notas de la demo

- Tras `desplegar.ps1`, OPcache puede servir PHP viejo 2-3 s: reintentar.
- Los `.ps1` van en ASCII puro (PowerShell 5.1 lee sin BOM como ANSI).
- El `.exe` del riesgo NO está en git: en un clon, recompilar (paso 2).
- El mismo veredicto por los dos caminos es el argumento central:
  los motores originales (académicos) y su réplica en el teléfono
  (`Motores.java`) aplican exactamente las mismas reglas.
