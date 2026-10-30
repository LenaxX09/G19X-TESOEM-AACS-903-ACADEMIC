# SPRINT 4 — Integración y Demo · guía rápida (30/09/2026)

> CIERRE DEL MVP: **foto → OCR (Python) → riesgo (C) → conciliación (Prolog)
> → MySQL → veredicto visible en el móvil, sin intervención manual y en ~1-3 s.**

## 1. Preparar el servidor (una sola vez)

1. Laragon iniciado (Apache + **mysqld de Laragon**, nunca los servicios MySQL8x).
2. Importar `database\seed.sql` si la BD está vacía (phpMyAdmin, root sin contraseña).
3. Publicar el backend: `powershell -File C:\CPP\desplegar.ps1`
   (sincroniza `api\`, `database\` y `motores\` —incluido el .exe— a `C:\laragon\www\cpp`).
4. Prueba rápida: abrir `http://localhost/cpp/api/ordenes.php` → JSON con 3 órdenes.

## 2. La API nueva del Sprint 4

| Endpoint | Qué hace |
|---|---|
| `GET/POST procesar.php?id=N` | Ejecuta el pipeline sobre ESA factura (idempotente) |
| `GET/POST procesar.php` | Procesa TODAS las «Pendientes» (reintentos en bloque) |
| `GET facturas.php` | Listado con todo lo que muestran los badges |

Fallos (la factura SIEMPRE queda «Pendiente», RNF-5):
- `500 MOTOR_*_FALLO` — un motor reventó (detalle en `datos.salida_motor`).
- `422 OCR_INCOMPLETO` — el OCR no leyó monto/fecha/proveedor (`datos.campos_faltantes`).

## 3. Demo en el móvil (checkpoint C4 — guion MVP §9.2)

APK: `android\app\build\outputs\apk\debug\app-debug.apk` (0.4.0-sprint4).
Emulador → Servidor `10.0.2.2` · móvil físico → IP LAN del PC (+ firewall puerto 80).

| # | Acción | Resultado esperado |
|---|---|---|
| 1 | Foto factura A (Papelería $4,850) eligiendo OC-001 → «Enviar factura» | «✅ APROBADA · riesgo 27.3 % · pipeline ~1.2 s» |
| 2 | Foto factura B (Papelería $6,200) contra OC-001 | «⚠️ CON DISCREPANCIA · MONTO_EXCEDE_ORDEN · riesgo 53.0 %» |
| 3 | Foto de OTRO proveedor contra esa orden (factura_04 vs OC-002) | «⚠️ CON DISCREPANCIA · PROVEEDOR_NO_COINCIDE» |
| 4 | Botón «📋 Ver facturas» | Lista con badges ✅ ⚠️ ⏳ (H4: se entiende sin explicar) |
| 5 | (Opcional) Foto borrosa que el OCR no lea | ⏳ queda «Pendiente» → «↻ Actualizar» reintenta |

Fotos sintéticas listas (mostrar en pantalla o imprimir): `motores\spike\fotos\`
— `factura_01.jpg` (A) · `factura_06.jpg` (B) · `factura_04.jpg` (C).
Regenerar B: `python motores\spike\generar_fotos_demo.py`

## 4. Demo sin móvil (todo por HTTP / PowerShell)

```powershell
$b64  = [Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\CPP\motores\spike\fotos\factura_01.jpg'))
$body = @{orden_id=1; imagen_base64=$b64} | ConvertTo-Json -Compress
$id   = (Invoke-RestMethod 'http://localhost/cpp/api/facturas.php' -Method Post `
          -Body $body -ContentType 'application/json').datos.factura_id
Invoke-RestMethod "http://localhost/cpp/api/procesar.php?id=$id"   # veredicto
Invoke-RestMethod 'http://localhost/cpp/api/facturas.php'          # listado/badges
```

## 5. Notas y aprendizajes del sprint

- KPI-1: `duracion_pipeline_s` ≈ 1.0-1.5 s (límite 30 s); `segundos_desde_creacion`
  se mide con el reloj de MySQL (`TIMESTAMPDIFF`) para evitar desfases de zona horaria.
- Tras `desplegar.ps1`, OPcache de Apache puede servir el PHP viejo 2-3 s; si
  ocurre, reintentar pasados unos segundos (o reiniciar Apache).
- El .exe del riesgo NO está en git: si se clona el repo, recompilar ANTES de
  desplegar → `gcc -O2 -Wall -o clasificador_riesgo.exe clasificador_riesgo.c`.
- Los scripts `.ps1` del proyecto se escriben en ASCII puro (PowerShell 5.1 lee
  sin BOM como ANSI y los caracteres "→/·" rompen el parser).
