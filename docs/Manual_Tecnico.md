# Manual Técnico — CPP
## Plataforma Inteligente para la Gestión de Cuentas por Pagar

> **Entregable de la actividad 7 del cronograma** (Progreso.docx: «Elaborar
> manuales técnicos y de usuario»). Versión 1.0 · 24/10/2026.
> Incluye el plan de mantenimiento y soporte (actividad 8).
> Complementos: `Arquitectura.md` (diseño), `INSTALACION.md` (instalación
> detallada), `Manual_Usuario.md` (uso final) y `Bitacora.txt` (historial).

---

## 1. Propósito y audiencia

Manual para el equipo técnico: levantar el proyecto desde cero, compilar la
app, entender los módulos y sus contratos, administrar los secretos y
mantener el sistema. No requiere conocer el código de memoria: cada sección
apunta a los archivos reales del repositorio.

## 2. Requisitos del entorno

| Herramienta | Versión verificada | Uso |
|---|---|---|
| Windows + Laragon (o XAMPP) | PHP 8.1.10 · MySQL 8.0.30 · Apache | Camino servidor + intérprete `php.exe` de los scripts CLI |
| Android Studio | JBR 21 · Gradle 8.13 · AGP 8.13 | Compilar la app (Java 17, `minSdk 24`, `targetSdk 36`) |
| Python | 3.12.10 + `pytesseract>=0.3.10` + `Pillow>=10` (`motores/requirements.txt`) | Motor OCR del camino servidor |
| Tesseract-OCR | 5.4.0 + modelo `spa` (`C:\Program Files\Tesseract-OCR`) | Requerido por pytesseract |
| GCC (MinGW) | 6.3.0 (`C:\MinGW\bin\gcc.exe`) | Compilar `clasificador_riesgo.c` |
| SWI-Prolog | `C:\Program Files\swipl\bin\swipl.exe` | Motor de conciliación |
| Firebase | proyecto `cpp-facturas` | Auth/Firestore/Storage del camino principal |
| PowerShell 5.1+ | — | Checkpoints y herramientas |

## 3. Estructura del repositorio

```
<repo>\
├── readme.md (visión general) · PRD_CPP.md (requisitos) · MVP_CPP.md (alcance/sprints)
├── INSTALACION.md (guía de instalación)
├── firestore.rules · storage.rules · firebase.json   ← seguridad versionada
├── desplegar.ps1       → publica la API en el www de Laragon (raíz portable: $PSScriptRoot)
├── docs\               → TODA la documentación del proyecto:
│      Arquitectura.md · Manual_Tecnico.md · Manual_Usuario.md · Reporte_QA.md
│      Informe_Final.md · Acta_Capacitacion.md · propuestas.txt
│      Bitacora.txt (historial) · Explicacion.txt (explicación académica)
│      SPRINT2/4/5/9/10_*.md (guías por sprint) · DEMO.md (guion de demo)
├── pruebas\            → test_c5.ps1 · test_s7/s8/s9/s10.ps1 (checkpoints re-ejecutables)
├── android\            → app Android (Java) + build.cmd + gradlew.bat
├── api\                → API PHP (camino servidor) + scripts CLI de Firebase
├── database\           → seed.sql + migrar_v1.sql (respaldos NO van al repo)
├── motores\            → procesar_factura.py · clasificador_riesgo.c/.exe · regla.pl · requirements.txt · spike\
└── herramientas\       → publicar_reglas.php · repara_strings.ps1 · s10_strings_frag.xml
```

> Correcciones 29/10: la raíz se ordenó en `docs\` (documentación) y
> `pruebas\` (checkpoints). `correcciones.txt` (2ª revisión) permanece
> en la raíz a decisión del autor, fuera de los commits nuevos.

## 4. Puesta en marcha — camino PRINCIPAL (Firebase, sin Laragon)

1. **Proyecto Firebase** (`INSTALACION.md` §8.1): crear proyecto (el real:
   `cpp-facturas`), app Android `com.cpp.captura`, descargar
   `google-services.json` → `android\app\google-services.json`.
2. **Cuenta de servicio** (§8.2): consola → Configuración → Cuentas de
   servicio → clave JSON → `api\firebase-service-account.json` (SECRETO, en
   `.gitignore`).
3. **Auth por correo** (sin consola): `php api\habilitar_email_auth.php`
   (API v2, una sola vez).
4. **Reglas de seguridad** (§8.4): publicar `firestore.rules` —
   `firebase deploy --only firestore:rules` (así se publicó el 29/10) o
   pegarlo en la consola. `storage.rules` queda SIN publicar por decisión
   económica: Storage exige el plan Blaze (tarjeta) en proyectos
   post-Oct/2024 y no se cuenta con el medio para contratarlo (§8.4);
   el archivo queda diseñado y versionado para cuando Storage se active.
5. **Datos demo** (re-ejecutable):
   ```powershell
   $env:CPP_DEMO_CLAVE = 'la-clave-que-configuraste'   # NUNCA en el repo
   php api\semilla_firebase.php    # usuarios demo + 3 cuentas OC-101/102/103
   ```
6. **Checkpoint**: `powershell -File .\pruebas\test_s10.ps1 (desde la raíz)` → **C10: 7/7 OK**.
7. Instalar el APK (`§6`) y entrar con `cliente@cpp.demo` /
   `proveedor@cpp.demo`.

## 5. Puesta en marcha — camino SERVIDOR (Laragon, demo académica)

1. Copiar el repo a `C:\laragon\www\cpp` (o apuntar el Document Root a
   la carpeta del repo) e iniciar Apache+MySQL.
2. Importar `database\seed.sql` en phpMyAdmin (BD `cpp`; aplicar además
   `migrar_v1.sql` para los sprints 5-9).
3. Revisar `api\config.php` (credenciales MySQL y rutas de `python`,
   `swipl` y el binario C).
4. Compilar el binario de riesgo si falta:
   `gcc -O2 -Wall -o motores\clasificador_riesgo.exe motores\clasificador_riesgo.c`
5. `pip install -r motores\requirements.txt`.
6. Login primero (`POST api/sesion.php` → token `X-CPP-Token`); prueba el
   checkpoint C1 con PowerShell/Postman (`INSTALACION.md` §5).

Detalle completo, solución de problemas y KPIs: `INSTALACION.md` §1-§7.

## 6. Compilación de la app Android

```powershell
android\build.cmd        # usa el JBR 21 de Android Studio
# BUILD_OK → APK en android\app\build\outputs\apk\debug\app-debug.apk
```

Equivalente: `cd android ; .\gradlew.bat :app:assembleDebug`.
Versión actual: **0.19.0-v1 (versionCode 29)**. Instalar: `adb install -r
app-debug.apk` (o copiar el APK al teléfono).

## 7. Módulos y contratos internos

### 7.1 Motores CLI (camino servidor — `MVP_CPP.md` §6.4)

| Motor | Entrada | Salida (UNA línea en stdout) |
|---|---|---|
| `procesar_factura.py` | ruta de la imagen | `{"proveedor":"...","fecha":"YYYY-MM-DD","monto":1234.56,"folio":"..."}` |
| `clasificador_riesgo` | monto + promedio histórico | `%` de riesgo (UMBRAL_RIESGO = 40) |
| `regla.pl` | 10 argumentos (orden y factura) | `APROBADA` o `DISCREPANCIA:<motivo>` |

Si un motor falla, la factura queda `Pendiente` y nada se pierde (RNF-5).

### 7.2 `Motores.java` (camino principal — misma lógica en el teléfono)

- `ocr(...)` → `Campos {proveedor, fecha, monto, folio}` (ML Kit on-device).
- `clasificadorRiesgo(monto, promedio)` → misma fórmula del módulo C.
- `conciliar(orden, campos, riesgo, duplicado)` → reglas 0-5 de `regla.pl`
  (regla 0 = duplicado; el resto: montos, proveedor, fechas).
- `slug(nombre)` — espejo exacto de `fb_slug()` en PHP.

### 7.3 `FirebaseServicio` (API interna de la app)

Perfiles (`cargarPerfil`, `crearPerfilCliente/Proveedor`, `existeCorreo`),
cuentas (`guardarOrden`, `escucharOrdenesCliente/Proveedor`), facturas
(`guardarFactura` — escribe además `facturasLookup` —,
`escucharFacturas(correo)`, `historialPromedio`, `buscarDuplicado`),
imágenes (`subirFotoFactura`, `subirComprobante`) y sesión
(`vigilarSesion`, `verificarSesion`). Todas las consultas en vivo van
FILTRADAS por el dueño de los datos (las reglas de Firestore lo exigen).

### 7.4 API PHP (camino servidor)

`sesion.php` (login/logout/quién-soy, token 8 h), `autenticar.php` (401/403
por rol), `usuarios.php` y `ordenes.php` (CRUD admin), `facturas.php`
(recepción + listado), `procesar.php` (orquesta los 3 motores; idempotente),
`pagos.php` (por_pagar/pagadas; comprobar CON imagen / despagar),
`registro_proveedor.php` (público), `proveedores.php` (público),
`firebase.php` (JWT RS256 → OAuth2 → Firestore/FCM REST), `semilla_firebase.php`
y `habilitar_email_auth.php` (CLI). Respuestas JSON uniformes
(`responder.php`).

## 8. Secretos y configuración

| Secreto/config | Dónde vive | Regla |
|---|---|---|
| `api\firebase-service-account.json` | fuera de git (`.gitignore`) | solo scripts CLI; NUNCA en la app |
| Clave demo (`CPP_DEMO_CLAVE`) | variable de entorno o parámetro | nunca en código/repo; rotable en la consola |
| `google-services.json` | `android\app\` (SÍ en el repo) | solo identifica el proyecto |
| `api\config.php` | repo | credenciales locales de MySQL + rutas de motores |
| Reglas de seguridad | `firestore.rules` / `storage.rules` (repo) | publicar tras editarlas (§4.4) |

## 9. Herramientas del repo

| Script | Qué hace |
|---|---|
| `test_s10.ps1` (`-Semilla`, `-Clave`) | Checkpoint C10 (7 pruebas contra Firebase) |
| `test_c5.ps1` · `test_s7/s8/s9.ps1` | Checkpoints históricos por HTTP (Laragon) |
| `herramientas\publicar_reglas.php` | Publica/verifica las 2 reglas por API (Rules API + JWT autofirmado) |
| `desplegar.ps1` | Copia la API al `www` de Laragon |
| `herramientas\repara_strings.ps1` | Utilería de encoding de `strings.xml` |

## 10. Plan de mantenimiento y soporte (actividad 8)

**Nivel 1 — operación diaria (usuario final):** fallos de conexión (la app
avisa y reintenta), candado bloqueado («Cerrar sesión» y volver a entrar),
cuenta demo (re-siembra con `test_s10.ps1 -Semilla`).

**Nivel 2 — soporte técnico:** recompilar APK (`build.cmd`), re-publicar
reglas (`herramientas\publicar_reglas.php`), revisar la consola Firebase
(Auth: usuarios; Firestore: `usuarios`/`ordenes`/`facturas`; Storage:
imágenes), logs de build (`android\b*.log`) y checkpoints re-ejecutables.

**Nivel 3 — desarrollo/mejora:** deuda v1.1 documentada en
`Bitacora.txt` §1 (RF-M8 cola offline, verificación de correo, multipart,
token cifrado). Convención del repo: leer `Bitacora.txt` §1-§7 antes de
cambiar código y actualizarla al terminar; commits con mensaje que diga QUÉ
cambió (ej.: «reglas de Firestore/Storage y requirements de motores»).

**Mantenimiento preventivo:** rotar la clave demo en la consola tras
exposición accidental; revisar reglas al tocar colecciones; mantener
Tesseract/modelo `spa` actualizados en el equipo de demo.

## 11. Historial de versiones (hitos)

| Versión (vc) | Hito |
|---|---|
| 0.2.0-sprint2 | Captura + envío Volley (C2) |
| 0.5.0 (vc 6) | Pipeline completo con badges (C4) + hotfixes cámara/EXIF + galería (RF-M3) |
| 0.8.0-v1 (vc 11) | Login, roles y CRUD (C5) |
| 0.9.0-v1 (vc 14) | Firebase: buzón del proveedor en vivo + push (S6) |
| 0.10.0-v1 (vc 15) | Cuentas de proveedor (S7, C7) |
| 0.11.0-v1 (vc 16) | Cuentas por pagar (S8, C8) |
| 0.13.0-v1 (vc 19) | Duplicados, OCR completo, filtros, alertas (S9, C9) |
| 0.14.x-v1 (vc 21-23) | Sprint 10 «Sin servidor» (C10) + paleta/edge-to-edge |
| 0.19.0-v1 (vc 29) | S10.3 roles unificados · S10.4 recordatorios · S10.5 candado · S10.6 vencidas/interés · S10.7 sesión vigilada |

*Fin del documento — Manual_Tecnico.md v1.0.*

