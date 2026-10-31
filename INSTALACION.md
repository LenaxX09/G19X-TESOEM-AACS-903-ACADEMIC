# Guía de Instalación — CPP (Sprint 1 «Base» con Laragon + Firebase del Sprint 10)

> Corresponde a la Fase 1 de `docs/propuestas.txt` y al Sprint S1 del `MVP_CPP.md` (sección 8).
> Meta: superar el **checkpoint C1** — *"desde Postman se crea una factura (imagen dummy) y se consulta su estado"*.
> El camino SIN servidor (app actual, Sprint 10) se prepara en la **sección 8**: Firebase + php.exe, sin Apache ni MySQL.

## 1. Requisitos

| Software | Uso |
|---|---|
| Laragon (o XAMPP) | Apache + MySQL + PHP |
| Navegador / phpMyAdmin | Importar el script SQL (incluido en Laragon) |
| Postman (opcional) | Probar endpoints · Alternativa: PowerShell (sección 5) |

## 2. Publicar el proyecto en el servidor web

Copia (o clona) este repositorio dentro del `www` de Laragon para que quede:

```
C:\laragon\www\cpp\
├── api\        → endpoints PHP
├── database\   → seed.sql
└── uploads\    → imágenes recibidas (se crea sola)
```

Con Laragon iniciado, la API queda disponible en: `http://localhost/cpp/api/`

> Alternativa sin copiar: en Laragon → Menú → Preferences → Document Root, apunta directamente a la carpeta del repositorio.

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
- [x] S2 — Captura: app Android (cámara + Volley Base64) → ver `docs/SPRINT2_ANDROID.md`; la app apunta a `http://10.0.2.2/cpp/api/facturas.php` desde el emulador (o a la IP LAN del PC desde un móvil físico)
- [ ] S3 — Motores: `procesar_factura.py`, `clasificador_riesgo.c` (GCC), `regla.pl` (SWI-Prolog)
- [ ] S4 — Integración: orquestación PHP (`shell_exec`) + listado en Android

> Sugiere el commit: `git add . && git commit -m "Sprint 1: BD seed + API PHP base (RF-D*, RF-B1/B2/B5)"`

---

## 8. Firebase — camino SIN servidor del Sprint 10 (app v1)

> La app actual (S10) entra con **Firebase Auth** y guarda todo en
> Firestore: no necesita Laragon. Storage queda OMITIDO por decisión
> económica (plan Blaze — §8.4). Guía de prueba completa:
> `docs/SPRINT10_v1.md`.

### 8.1 Crear el proyecto (UNA vez)

1. Entra en <https://console.firebase.google.com> → **Agregar proyecto**
   (el real se llama `cpp-facturas`; Google Analytics es opcional).
2. Agrega una app **Android** con paquete `com.cpp.captura` y descarga
   `google-services.json`.
3. Coloca ese archivo en `android\app\google-services.json`
   (SÍ va en el repo: solo identifica el proyecto, no es secreto).
4. Crea SOLO **Firestore Database** (modo *production*; las reglas van
   en el punto 8.4). **Storage NO se crea**: en proyectos creados
   después de Oct/2024 Firebase exige el plan Blaze (tarjeta de crédito)
   solo para entrar a la sección Storage, y **no se cuenta con el medio
   económico para contratarlo** — omisión documentada en §8.4 (las
   reglas quedan versionadas en el repo, sin publicar).

### 8.2 Cuenta de servicio (para los scripts PHP — SECRETO)

1. Consola → ⚙️ **Configuración del proyecto → Cuentas de servicio** →
   **Generar nueva clave privada** → guarda el JSON como
   `api\firebase-service-account.json`.
2. Ese archivo **nunca** se commitea (ya está en `.gitignore`) y es el que
   usan `habilitar_email_auth.php`, `semilla_firebase.php` y
   `test_s10.ps1`.

### 8.3 Habilitar el login y sembrar los datos demo (solo php.exe)

```powershell
# php.exe del PATH o de Laragon (solo el intérprete, sin Apache/MySQL)
php api\habilitar_email_auth.php   # UNA vez: activa Email/Password (API v2)

# La clave demo NUNCA viaja en el repo (correcciones 22/10): la pasas TÚ
$env:CPP_DEMO_CLAVE = 'la-clave-que-configuraste'
php api\semilla_firebase.php      # usuarios demo + cuentas OC-101/102/103
```

La semilla crea `cliente@cpp.demo` y `proveedor@cpp.demo` (con TU clave),
sus perfiles `usuarios/{uid}`, la entrada `clientesPorCorreo/` del cliente
y 3 cuentas demo (una vence en 3 días ⏰ y una vencida 🚨 para el RF-M7).

> **Rota la clave vieja:** la clave demo anterior salió del código y del
> repo, pero existió — en la consola (Authentication → Usuarios → ⋮ →
> Cambiar contraseña) cámbiala en ambas cuentas demo, o bórralas y
> re-siembra con la nueva.

### 8.4 Reglas de seguridad (`firestore.rules` / `storage.rules`)

Publica las reglas que viven en la raíz del repo (correcciones 22/10):

- Consola → **Firestore Database → Reglas** → pega el contenido de
  `firestore.rules` → **Publicar**. (Gratis, sin tarjeta: esto es lo
  IMPORTANTE — protege los datos: roles, facturas, órdenes.)
- **Storage → OMITIDO POR DECISIÓN (29/10/2026)**: en proyectos creados
  después de Oct/2024 Firebase exige el plan Blaze (tarjeta de crédito)
  solo para ENTRAR a la sección Storage, y **no se cuenta con el medio
  económico para contratarlo**. `storage.rules` queda DISEÑADO y
  VERSIONADO en el repo pero SIN publicar — es una decisión documentada
  (Bitácora, entrada 11), no un olvido. Efecto real: sin Blaze el bucket
  de Storage no llega a crearse, así que las subidas de imágenes pueden
  fallar — la app NO se rompe: la factura se guarda con veredicto (sin
  foto, con aviso) y el pago con comprobante AVISA el error y la cuenta
  queda «por pagar» (nunca se marca pagada sin evidencia). El día
  que Storage se active: Consola → **Storage → Reglas** → pegar el
  contenido de `storage.rules` → **Publicar**.

Dos formas más de publicarlas, equivalentes a pegar en la consola
(correcciones 24/10 — úsalas tras editar `firestore.rules`/`storage.rules`):

- **Sin consola y sin npm**:
  `php herramientas\publicar_reglas.php` — publica por API con la
  cuenta de servicio (la misma de la semilla), valida la sintaxis
  antes de tocar nada y verifica cada release. Desde el 31/10 publica
  SOLO `firestore.rules` (Storage omitido por la decisión económica de
  arriba); acepta `solo-storage` / `ambas` para cuando Storage se
  active. **Requisito UNA vez** (descubierto
  24/10): dale a la cuenta de servicio el rol **«Firebase Rules Admin»**
  (Consola Google Cloud → IAM → Conceder acceso → pega el
  `client_email` de `api/firebase-service-account.json`). Sin ese rol el
  script SUBE los rulesets pero no puede publicar el release (403 IAM) —
  en ese caso queda la opción de pegar a mano.
- **Con Firebase CLI** (`npm i -g firebase-tools` + `firebase login` +
  `firebase use --add`, una vez): `firebase deploy --only firestore:rules`
  desde la raíz del repositorio — **así se publicó el 29/10/2026**
  (Deploy complete ✅; creó el `.firebaserc`). El comando con `,storage`
  falla mientras Storage no esté activo («Get started»/Blaze);
  `--only storage` queda para cuando se active.

**Estado (29/10/2026)**: `firestore.rules` PUBLICADO en vivo (ya no corre
el release pre-fix `4d2fad49`). Protege: el `rol` de `usuarios/{uid}` es
inmutable, `usuarios` no se puede listar (el alta de cuentas consulta
`clientesPorCorreo`) y las facturas/órdenes ajenas no se leen. Storage:
`storage.rules` sin publicar por la decisión económica de arriba — si
algún día se publica, solo acepta JPEG de usuarios autenticados.

### 8.5 Checkpoint C10 (re-ejecutable)

```powershell
$env:CPP_DEMO_CLAVE = 'la-clave-que-configuraste'   # el login T1 la usa
powershell -File .\pruebas\test_s10.ps1                 # 7 pruebas → 7/7 OK
powershell -File .\pruebas\test_s10.ps1 -Semilla        # re-siembra y verifica
```

## 9. Motores del camino servidor (Python)

```powershell
pip install -r motores\requirements.txt   # pytesseract + Pillow
```

Además instala **Tesseract-OCR** (en `C:\Program Files\Tesseract-OCR`) y
su modelo en español (`spa`). El C se compila con GCC y el Prolog corre
con SWI-Prolog; los contratos CLI de cada motor están en `MVP_CPP.md` §6.4.
