# Guía Sprint 2 "Captura" — App Android (CPP)

> Corresponde al **Sprint S2 del `MVP_CPP.md`** (sección 8) y a los requisitos
> **RF-M2** (cámara con `MediaStore.ACTION_IMAGE_CAPTURE`) y **RF-M4 simplificado**
> (envío Volley en Base64). La lista con badges (RF-M5 completo) llega en S4.
>
> **Meta (checkpoint C2):** una foto real tomada en el móvil se guarda en el
> servidor y aparece como `Pendiente` en MySQL.

## 1. Qué contiene

```
android\
├── app\src\main\java\com\cpp\captura\MainActivity.java   → cámara + órdenes + envío
├── app\src\main\res\layout\activity_main.xml             → pantalla Captura (MVP §6.3)
├── app\src\main\AndroidManifest.xml                      → INTERNET, CAMERA, cleartext, FileProvider
├── build.cmd                                             → compila por consola (usa el JBR 21 de Android Studio)
└── gradlew.bat                                           → wrapper de Gradle 8.13
```

Flujo de la app:

1. Al abrir, descarga las órdenes con `GET /api/ordenes.php` y llena el spinner.
2. **📷 Tomar foto** abre la cámara (permiso en tiempo de ejecución, foto vía
   `FileProvider` en `cache/`, sin permisos de almacenamiento).
3. **📤 Enviar factura** comprime la foto (JPEG 85, lado mayor ≤ 1600 px →
   ~200–600 KB, muy por debajo del límite de 8 MB de la API), la codifica en
   Base64 y hace `POST /api/facturas.php { orden_id, imagen_base64 }`.
4. Muestra `✅ Factura Nº X registrada como «Pendiente»` con el id devuelto.

## 2. Ejecutar desde Android Studio

1. Android Studio → **Open** → carpeta `android` (usa Gradle 8.13 + AGP 8.13, JDK: su JBR 21).
2. Elige un emulador (API 30+) o tu móvil con **Depuración USB**.
3. Run ▶ sobre `MainActivity`.

### ¿Qué IP pongo en el campo "Servidor"?

| Dónde corre la app | IP a usar | Por qué |
|---|---|---|
| Emulador de Android Studio | `10.0.2.2` (ya es el valor por defecto) | Es el alias del `localhost` del PC anfitrión |
| Móvil físico (misma WiFi) | La IP LAN del PC: `ipconfig` → *IPv4* (ej. `192.168.1.20`) | El móvil debe alcanzar tu PC por red |

La IP queda guardada (no hay que re-teclearla en cada uso) y se puede cambiar
en cualquier momento + botón **↻ Órdenes** para reconectar.

### Móvil físico: permitir Apache en el Firewall

Si la app no conecta desde el móvil, crea una regla de entrada para el puerto 80
(PowerShell como **administrador**):

```powershell
netsh advfirewall firewall add rule name="CPP Apache 80" dir=in action=allow protocol=TCP localport=80
```

## 3. Compilar el APK por consola

```powershell
cd android
.\build.cmd <hash>        # el hash sale de: dir "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin"
# o directamente:
.\gradlew.bat :app:assembleDebug
```

APK resultante: `android\app\build\outputs\apk\debug\app-debug.apk`
(instalable con `adb install -r app-debug.apk`).

## 4. Prueba del checkpoint C2 (5 minutos)

1. Laragon **iniciado** (Apache + MySQL) y BD `cpp` importada (Sprint 1).
2. Abre la app → deben aparecer las 3 órdenes semilla (OC-001, OC-002, OC-003).
3. Elige la orden → **📷 Tomar foto** → fotografía una **factura real** → ✓.
4. **📤 Enviar factura** → espera el mensaje verde `✅ Factura Nº … «Pendiente»`.
5. Verifica en el servidor:
   ```powershell
   Invoke-RestMethod 'http://localhost/cpp/api/facturas.php?estado=Pendiente'
   # y en C:\laragon\www\cpp\uploads\ debe existir la imagen recibida
   ```

**C2 superado** cuando la foto real aparece como archivo en `uploads/` y la fila
`estado='Pendiente'` existe en MySQL (phpMyAdmin → tabla `facturas`).

## 5. Problemas comunes

| Síntoma | Causa / solución |
|---|---|
| "No se pudo conectar…" | Laragon detenido, IP equivocada, o firewall (sección 2) |
| Foto enorme / envío lento | Ya mitigado: la app reescala a ≤ 1600 px antes de enviar |
| `IMAGEN_MUY_GRANTE` / HTTP 413 | No debería ocurrir (límite API 8 MB); si ocurre, revisa `php.ini → post_max_size/upload_max_filesize ≥ 16M` |
| Camera cierra y no muestra preview | Se canceló la captura o el almacenamiento está lleno; vuelve a tomar la foto |
| `ERROR_VALIDACION: orden_id…` | El spinner estaba vacío al enviar: reconecta con ↻ Órdenes |

## 6. Estado

- [x] S2 — Captura: app Android (cámara + Volley Base64) — *este entregable*
- [ ] S3 — Motores: `procesar_factura.py`, `clasificador_riesgo.c` (GCC), `regla.pl` (SWI-Prolog)
- [ ] S4 — Integración: orquestación PHP (`shell_exec`) + listado en Android

> Sugiere el commit: `git add . && git commit -m "Sprint 2: app Android captura + envío Volley (RF-M2, RF-M4)"`
