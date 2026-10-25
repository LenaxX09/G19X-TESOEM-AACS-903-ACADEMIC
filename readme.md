# Plataforma Inteligente para la Gestión de Cuentas por Pagar

Sistema móvil y distribuido para automatizar la captura, validación y conciliación de facturas y comprobantes de pago mediante procesamiento de texto e inferencia lógica.

1.**Descripción del Proyecto**

El proyecto automatiza la gestión de cuentas por pagar sustituyendo la revisión manual de documentos por un flujo digital inteligente:

Captura Móvil: El usuario toma una fotografía de la factura o ticket desde la aplicación móvil.

Extracción de Datos: El sistema lee la imagen y extrae automáticamente los datos clave (proveedor, fecha, monto total e ítems).

Validación y Conciliación: Se evalúa la información mediante reglas de negocio para detectar discrepancias con las órdenes de compra y calcular posibles riesgos financieros.

Alertas y Estado: La app notifica si el comprobante fue aprobado, si tiene inconsistencias o si requiere revisión manual.

## 2. Arquitectura de entrega (decidida el 22/10/2026)

La app Android ya NO depende del servidor Laragon: desde el Sprint 10 la
sesión y los datos viven en **Firebase** y el pipeline puede correr **EN EL
TELÉFONO**. La pila PHP/MySQL + Python + C + Prolog no desapareció: sigue
siendo el pipeline **SERVIDOR** de la demo académica (S1-S9), solo que ya no
es el único camino.

### Camino PRINCIPAL — sin servidor (lo que corre la app v1)

App Android (Java) + Firebase: **Auth** (correo/contraseña) · **Firestore**
(`usuarios`, `ordenes`, `facturas`, `clientesPorCorreo`, `facturasLookup`) ·
**Storage** (`facturas/`, `comprobantes/`) · **ML Kit** para el OCR en el
teléfono. El riesgo y la conciliación los calcula `Motores.java` (réplica
regla por regla de `clasificador_riesgo.c` y `regla.pl`). Es el camino de
producción de la v1: no hay IP que teclear ni MySQL que levantar
(ver `SPRINT10_v1.md`).

### Camino SERVIDOR — Laragon (demo académica de los motores originales)

`api/facturas.php` + `api/procesar.php` orquestan **Python** (OCR
pytesseract) → **C** (riesgo) → **Prolog** (conciliación) → **MySQL**.
Mismos contratos y mismos veredictos que el camino principal. Se usa con
Laragon iniciado (ver `INSTALACION.md` §2-§5) para demostrar los motores
originales; `Motores.java` queda como el **modo sin conexión** equivalente.

### Diagrama de cuál camino es cuál

```
                        FACTURA (foto)
                              │
        ┌─────────────────────┴──────────────────────────────┐
        │ CAMINO PRINCIPAL (sin servidor · app v1)           │ CAMINO SERVIDOR (Laragon · demo S1-S9)
        │                                                    │
        │ [App Android]                                      │ [App S2-S9 / Postman]
        │  OCR ......... ML Kit (EN EL TELÉFONO)             │      │ JSON + imagen Base64
        │  Riesgo ....... Motores.java (fórmula del C)       │      ▼
        │  Reglas ........ Motores.java (reglas 0-5 regla.pl)│ api/facturas.php → api/procesar.php
        │  Duplicados ... Firestore facturasLookup (RF-L3)   │      ├─ Python · procesar_factura.py (OCR)
        │        │                                           │      ├─ C ....... clasificador_riesgo.c
        │        ▼                                           │      └─ Prolog .. regla.pl (conciliación)
        │  Firebase: Auth · Firestore · Storage              │             │
        │                                                    │             ▼
        └────────────────────────────────────────────────────┘   MySQL (facturas/ordenes/usuarios)
```

El veredicto sale **idéntico** por los dos caminos (misma fórmula, mismas
reglas 0-5); la diferencia es DÓNDE corre el pipeline (teléfono vs servidor
PHP) y DÓNDE viven los datos (Firestore vs MySQL).

> Nota de integración (deuda v1.1): hoy «Enviar factura» de la app va
> SIEMPRE por el camino principal. Conectarlo también a `api/procesar.php`
> cuando haya servidor (Volley ya está en el proyecto) es el siguiente
> paso; el contrato REST de la API no cambió.

### Seguridad

- `firestore.rules` y `storage.rules` (raíz del repo) cierran Firestore y
  Storage: el `rol` de `usuarios/{uid}` es **inmutable**, no se puede
  listar `usuarios` (el proveedor consulta `clientesPorCorreo`), y las
  facturas/órdenes ajenas no se pueden leer. Las de **Firestore** son las
  obligatorias (datos reales, gratis de publicar); las de **Storage**
  (fotos) son opcionales: los proyectos nuevos piden plan Blaze/tarjeta
  para entrar a esa sección (ver `INSTALACION.md` §8.4).
- La clave de las cuentas demo NO vive en el repo: se pasa con la
  variable de entorno `CPP_DEMO_CLAVE` (ver `INSTALACION.md` §8).

### Arranque rápido en otro equipo/dispositivo (quick start)

¿Clonaste este repo en otra máquina? La app se levanta así (camino
principal, sin servidor):

1. **Requisitos**: Android Studio (con su JDK 17) y un teléfono Android
   7.0+ (físico o emulador). No hace falta Laragon/MySQL/Python para USAR
   la app.
2. **Firebase ya apuntado**: `google-services.json` viene incluido en
   `android/app/` (no es secreto), así que el clon queda hablando con el
   proyecto `cpp-facturas` tal cual. Si quieres tu PROPIO proyecto,
   sigue `INSTALACION.md` §8 y reemplázalo.
3. **Compilar el APK**:
   ```powershell
   cd android
   .\gradlew.bat :app:assembleDebug      # Windows
   # ./gradlew :app:assembleDebug        # macOS / Linux
   ```
   El APK queda en `android\app\build\outputs\apk\debug\app-debug.apk`.
4. **Instalar**: botón Run ▶ de Android Studio con el teléfono conectado
   (depuración USB activada), o copiando el APK al teléfono, o
   `adb install -r app-debug.apk`.
5. **Probar** — cualquiera de estas tres vías:
   - **Crear tus PROPIAS cuentas (cero dependencias, recomendada)**: en el
     login toca «Crear cuenta» → Cliente (tu correo + tu empresa) o
     Proveedor (tu correo + tu negocio). Entra como proveedor en un
     teléfono (o cierra sesión y vuelve) y como cliente en otro:
     proveedor «➕ Subir cuenta» al correo del cliente → el cliente la ve
     AL INSTANTE en «💰 Cuentas por pagar», la paga adjuntando la foto del
     comprobante, y en la captura elige esa cuenta, fotografía la factura
     y «Enviar factura» corre el pipeline EN EL TELÉFONO (OCR + riesgo +
     conciliación) y muestra el veredicto. No necesitas clave demo ni
     ningún secreto.
   - **Cuentas demo ya sembradas**: `cliente@cpp.demo` /
     `proveedor@cpp.demo` con la clave que configuró quien preparó
     Firebase (`CPP_DEMO_CLAVE` vive fuera del repo: pídesela). Traen las
     3 cuentas OC-101/102/103 listas (una por vencer ⏰ y una vencida 🚨
     para ver las alertas M7). Si el proyecto aún no está sembrado:
     `INSTALACION.md` §8.3.
   - **Tu propio Firebase (aislado del nuestro)**: sigue `INSTALACION.md`
     §8 completo — crear proyecto, reemplazar `google-services.json`,
     habilitar Email/Password, sembrar con TU clave, publicar las reglas
     y correr el checkpoint `test_s10.ps1`.
6. **Candado**: al reabrir la app con la sesión abierta pide la
   huella/PIN del teléfono (así se diseñó; «Cerrar sesión» es la válvula
   de escape). El teléfono de prueba debe tener ALGÚN bloqueo de
   pantalla configurado; si no lo tiene, la pantalla del candado avisa y
   solo deja reintentar o cerrar sesión.

Para el camino SERVIDOR (Laragon + MySQL + Python + C + Prolog) sigue
`INSTALACION.md` §1-§5; para el resto de pruebas guiadas, `SPRINT10_v1.md`.

## 3. Tecnologías por componente

| Componente | Camino principal (sin servidor) | Camino servidor (Laragon) |
|---|---|---|
| App móvil | Android nativo (Java), Volley, ML Kit, BiometricPrompt, WorkManager | la misma app (S2-S9 hablaba con la API) |
| Identidad/datos | Firebase Auth · Firestore · Storage | PHP (API REST) + MySQL |
| OCR | ML Kit EN EL TELÉFONO (`Motores.java`) | Python `motores/procesar_factura.py` (pytesseract) |
| Riesgo | `Motores.java` (fórmula del C) | C `motores/clasificador_riesgo.c` (GCC) |
| Conciliación | `Motores.java` (reglas 0-5) | Prolog `motores/regla.pl` (SWI-Prolog) |

## 4. Herramientas y Entornos de Desarrollo

Android Studio: Entorno para la programación de la app en Java.

Visual Studio Code / GCC: Entorno para la escritura de scripts en Python y compilación de módulos en C.

SWI-Prolog: Entorno de ejecución para el archivo de reglas de inferencia.

Laragon o XAMPP: Servidor local (Apache, MySQL, PHP) para la API y base de datos del camino servidor.

php.exe (solo el intérprete) + cuenta de servicio de Firebase: para preparar el proyecto Firebase sin iniciar Laragon (`habilitar_email_auth.php`, `semilla_firebase.php`).

Postman: Herramienta para realizar pruebas de endpoints y peticiones HTTP.

5.**Dominios de Aplicación**

Control de Comprobantes para PyMEs: Gestión digital de notas y comprobantes en pequeños comercios.

Comprobación de Viáticos: Control automático de gastos administrativos o institucionales contra presupuestos fijados.

Recepción en Bodega / Almacén: Validación en tiempo real del comprobante entregado por el proveedor contra la orden registrada.

6. **Objetivos del Proyecto**

Objetivo General: Desarrollar un sistema móvil y distribuido que optimice la validación y conciliación de cuentas por pagar mediante herramientas informáticas locales y distribuidas.

Objetivos Específicos:

-Diseñar la arquitectura de comunicación entre la app y los módulos de análisis (pipeline en el teléfono con Firebase, o API PHP con Python/C/Prolog).

-Implementar la captura móvil de documentos y su procesamiento automático.

-Desarrollar las reglas de conciliación (reglas 0-5, originalmente en Prolog) para detectar inconsistencias en los pagos.

-Crear tableros de control en la app móvil con indicadores claros para el usuario.