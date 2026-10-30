# SPRINT 5 — Acceso y roles · guía rápida (09/10/2026)

> Primer sprint de la **v1.0** (MVP §13, Incremento 1). Antes de entrar a la app
> hay que **iniciar sesión**; cada rol ve solo lo suyo; el admin gestiona
> usuarios y órdenes desde el móvil **sin tocar phpMyAdmin**.
>
> Requisitos cubiertos: **RF-M1** (login) · **RF-B6** (autenticación/rol) ·
> **RF-B4 completo** (CRUD usuarios y órdenes). Checkpoint: **C5 superado**
> (15/15 pruebas, incluida la regresión §9.2 YA autenticada).

## 1. Preparar el servidor (una sola vez)

1. Laragon: **Apache + el mysqld de Laragon** (nunca los servicios MySQL8x).
2. Si la BD viene del MVP, aplica la migración (no borra datos):
   `mysql -u root < database\migrar_v1.sql` — sube `password_hash` a
   VARCHAR(255) y crea la tabla `sesiones`. Si partes de cero, importa
   `database\seed.sql` (ya trae el esquema v1.0).
3. Publica el backend: `powershell -File .\desplegar.ps1`
4. Prueba rápida: `GET http://localhost/cpp/api/facturas.php` → **401
   NO_AUTENTICADO** (¡eso es lo correcto ahora!).

## 2. Lo nuevo de la API

| Endpoint | Qué hace | Quién |
|---|---|---|
| `POST sesion.php {usuario,password}` | Login → `{token, expira_en, usuario{rol}}` | público |
| `GET sesion.php` | ¿quién soy? / valida el token guardado | autenticado |
| `DELETE sesion.php` | Logout (borra la sesión) | autenticado |
| `GET/POST usuarios.php` | Listar / `accion:crear\|actualizar` (rol, activo, password) | **admin** |
| `POST ordenes.php` | `accion:crear\|actualizar` (código, proveedor, monto, fecha, estado) | **admin** |
| `GET facturas.php` · `POST facturas.php` | Igual que antes, pero autenticado · envío: capturista/admin | auth / roles |
| `procesar.php` | Igual que antes, autenticado | autenticado |

**El token**: cabecera `X-CPP-Token` (o `?token=` para pruebas rápidas).
Vigencia 8 h (`SESION_TTL_HORAS` en `config.php`). Errores nuevos: `401
NO_AUTENTICADO` · `403 ROL_NO_PERMITIDO` · `409 USUARIO_DUPLICADO` · `409
ULTIMO_ADMIN` · `409 CODIGO_DUPLICADO`.

**Contraseñas**: los usuarios del seed siguen con SHA-256 y `sesion.php`
acepta AMBOS formatos; los creados por el CRUD nacen con **bcrypt**
(`password_hash`). La "baja" de usuarios es lógica (`activo=0`) y nunca se
puede dejar el sistema sin un admin activo.

## 3. La app (0.8.0-v1, versionCode 11)

- **LoginActivity** (nueva pantalla de entrada): servidor + usuario +
  contraseña → guarda token/rol en `SharedPreferences` (clase `Sesion`).
- **MainActivity**: muestra «👤 nombre (rol) · servidor»; TODAS las peticiones
  viajan con el token; un 401 regresa al login. El **revisor** ve bloqueado
  «Enviar» con explicación (es consulta). Botones nuevos: «⚙️ Administración»
  (solo admin) y «🚪 Cerrar sesión».
- **AdminActivity** (nueva, solo admin): Spinner Usuarios/Órdenes + lista +
  «＋ Nuevo»; tocar una fila abre el diálogo de edición (nombre, rol,
  contraseña, activo / código, proveedor, monto, fecha, abierta-cerrada).
- APK: `android\app\build\outputs\apk\debug\app-debug.apk` — móvil físico →
  IP LAN del PC (mismo flujo de siempre).

Usuarios semilla (contraseña **demo1234**): `admin` · `capturista` · `revisor`.

## 4. Checkpoint C5 (script listo: `test_c5.ps1`)

`powershell -ExecutionPolicy Bypass -File .\pruebas\test_c5.ps1` — 15 pruebas:
logins de los 3 roles, clave mala, sin token, 403 por rol (capturista al CRUD,
revisor enviando), crear usuario + duplicado, crear orden, login del usuario
nuevo (bcrypt), protección del último admin, **regresión del trío §9.2
autenticada** (A ✅ 27.3 · B ⚠️ 53.0 MONTO_EXCEDE · C ⚠️
PROVEEDOR_NO_COINCIDE) y logout con token muerto. Resultado del 09/10:
**15/15 OK**. Nota: el script crea `c5demo`/`OC-C5` y luego hay que limpiarlos
(si se re-corre) — ver Bitácora.

## 5. Notas y decisiones del sprint

- **Deuda honesta (v1.1)**: token en prefs sin cifrar
  (EncryptedSharedPreferences requiere dependencia nueva) y `?token=` como
  atajo de pruebas (quitar en producción).
- El `GET ordenes.php` queda abierto a cualquier rol autenticado: la app lo
  necesita para el spinner de captura.
- `procesar.php` es para cualquier rol autenticado: el pipeline es automático,
  no una decisión de revisión.
- No se tocó NADA del pipeline (Python/C/Prolog): la regresión lo demuestra.
- Falta para v1.0 (siguientes sprints): RF-L3 duplicados, RF-D5 completo,
  RF-M6 filtros, RF-M7 alertas de vencimiento, RF-M8 cola offline.
