# Guía de Instalación — Sprint 1 "Base" (CPP)

> Corresponde a la Fase 1 de `propuestas.txt` y al Sprint S1 del `MVP_CPP.md` (sección 8).
> Meta: superar el **checkpoint C1** — *"desde Postman se crea una factura (imagen dummy) y se consulta su estado"*.

## 1. Requisitos

| Software | Uso | Estado en tu equipo |
|---|---|---|
| Laragon (o XAMPP) | Apache + MySQL + PHP | ✅ Detectado en `C:\laragon` (PHP 8.1.10) |
| Navegador / phpMyAdmin | Importar el script SQL | Incluido en Laragon |
| Postman (opcional) | Probar endpoints | Alternativa: PowerShell (sección 5) |

## 2. Publicar el proyecto en el servidor web

Copia (o clona) este repositorio dentro del `www` de Laragon para que quede:

```
C:\laragon\www\cpp\
├── api\        → endpoints PHP
├── database\   → seed.sql
└── uploads\    → imágenes recibidas (se crea sola)
```

Con Laragon iniciado, la API queda disponible en: `http://localhost/cpp/api/`

> Alternativa sin copiar: en Laragon → Menú → Preferences → Document Root, apunta directamente a `C:\CPP`.

## 3. Crear la base de datos (RF-D1..D4)

1. Inicia **Apache** y **MySQL** desde Laragon.
2. Abre phpMyAdmin: <http://localhost/phpmyadmin> (usuario `root`, contraseña vacía por defecto).
3. Pestaña **Importar** → selecciona `database\seed.sql` → **Continuar**.
4. Verifica que exista la BD `cpp` con las tablas `usuarios`, `ordenes_compra`, `facturas`, `historial_proveedor` y las 3 órdenes semilla (OC-001, OC-002, OC-003).

Por consola (alternativa):
```powershell
C:\laragon\bin\mysql\mysql-8.x-winx64\bin\mysql.exe -u root < C:\laragon\www\cpp\database\seed.sql
```

## 4. Configuración

Revisa `api\config.php` solo si tu MySQL tiene contraseña o puerto distinto. Ahí también quedarán, en el Sprint 4, las rutas de `python`, `swipl` y el binario C (mitigación riesgo R4).

> Si vas a subir fotos grandes, en `php.ini` sube `post_max_size` y `upload_max_filesize` a `16M` (la imagen viaja en Base64, que la infla ~40 %).

## 5. Pruebas del checkpoint C1

**Opción A — PowerShell (sin instalar nada):**
```powershell
# Crear una imagen dummy de prueba (un JPEG mínimo válido de 1x1 px)
[IO.File]::WriteAllBytes("$env:TEMP\dummy.jpg", [Convert]::FromBase64String(
  "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAMAwEAAhEDEQA/AJgA/9k="))

# POST: crear factura (RF-B1)
$b = @{ orden_id = 1; imagen_base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:TEMP\dummy.jpg")) } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost/cpp/api/facturas.php' -ContentType 'application/json' -Body $b | ConvertTo-Json -Depth 6

# GET: consultar facturas (RF-B2)
Invoke-RestMethod -Uri 'http://localhost/cpp/api/facturas.php' | ConvertTo-Json -Depth 6
```

**Opción B — Postman:**
- `POST http://localhost/cpp/api/facturas.php` → Body → raw → JSON:
  `{ "orden_id": 1, "imagen_base64": "<pega aquí el Base64 de cualquier foto jpg>" }`
- `GET http://localhost/cpp/api/facturas.php` (o `?id=1`, `?estado=Pendiente`)
- `GET http://localhost/cpp/api/ordenes.php` (soporte)

**Resultado esperado (C1):** el POST devuelve `201` con `estado: "Pendiente"` y el GET lista la factura. ✅

## 6. Solución de problemas

### "No tengo acceso a mi BD" / phpMyAdmin se cuelga / Access denied for root

**Causa más probable:** tener otra instalación de MySQL como servicio de Windows
(en este equipo existían `MySQL80`, `MySQL81` y `MySQL84` en `C:\Program Files\MySQL\`).
Solo UN MySQL puede usar el puerto **3306**; si el servicio externo arranca primero,
el MySQL de Laragon no puede levantar y phpMyAdmin/API fallan.

**Solución aplicada (24-28/09/2026):**
```powershell
net stop MySQL84                      # liberar el puerto 3306
Set-Service MySQL84 -StartupType Manual   # que no arranque solo al encender el PC
# luego iniciar MySQL desde Laragon (Start All)
```

**Regla de oro:** para este proyecto usa SIEMPRE el MySQL de Laragon
(`C:\laragon\bin\mysql\mysql-8.0.30-winx64`, datos en `C:\laragon\data\mysql-8`).
Si algún día necesitas el otro MySQL84, detén antes el de Laragon.

### Verificaciones rápidas
- phpMyAdmin → <http://localhost/phpmyadmin> → usuario `root`, contraseña **vacía**
- API → <http://localhost/cpp/api/ordenes.php> debe devolver JSON `"codigo":"OK"`
- Puerto ocupado → `Get-NetTCPConnection -State Listen | ? LocalPort -eq 3306`

## 7. Estado del proyecto y siguientes pasos

- [x] S1 — Base: BD + seed + API + pruebas (este entregable)
- [x] S2 — Captura: app Android (cámara + Volley Base64) → ver `SPRINT2_ANDROID.md`; la app apunta a `http://10.0.2.2/cpp/api/facturas.php` desde el emulador (o a la IP LAN del PC desde un móvil físico)
- [ ] S3 — Motores: `procesar_factura.py`, `clasificador_riesgo.c` (GCC), `regla.pl` (SWI-Prolog)
- [ ] S4 — Integración: orquestación PHP (`shell_exec`) + listado en Android

> Sugiere el commit: `git add . && git commit -m "Sprint 1: BD seed + API PHP base (RF-D*, RF-B1/B2/B5)"`
