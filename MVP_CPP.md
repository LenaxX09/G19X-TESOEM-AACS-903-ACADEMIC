# MVP — Plataforma Inteligente para la Gestión de Cuentas por Pagar (CPP)

> **Definición del Producto Mínimo Viable (Minimum Viable Product)**
> Derivado de `PRD_CPP.md` v1.0. Toda referencia de requisitos (RF-xx / RNF-xx) corresponde a las secciones 6 y 7 del PRD.

| Campo | Detalle |
|---|---|
| **Producto** | Plataforma Inteligente para la Gestión de Cuentas por Pagar |
| **Código del proyecto** | CPP (Cuentas Por Pagar) |
| **Versión del documento** | 1.0 |
| **Fecha** | 23/09/2026 |
| **Estado** | Borrador — pendiente de aprobación |
| **Documento base** | `PRD_CPP.md` v1.0 |

---

## 1. Definición e hipótesis del MVP

### 1.1 Qué es el MVP

El MVP de CPP es **el flujo end-to-end mínimo** que demuestra el valor central del producto:

> **Fotografiar una factura desde la app Android y obtener automáticamente su estado de conciliación (Aprobada / Con Discrepancia)**, pasando por OCR en Python, scoring de riesgo en C y reglas de conciliación en Prolog, con PHP + MySQL como orquestador y almacén — todo en entorno local (Laragon/XAMPP) y sin intervención manual.

El MVP conserva las cinco tecnologías del PRD porque el valor diferencial del producto es precisamente el pipeline multinivel; lo que se recorta son las funcionalidades periféricas (login/roles, alertas, offline, filtros avanzados, duplicados).

### 1.2 Hipótesis a validar

| # | Hipótesis | Cómo se valida |
|---|---|---|
| H1 | El OCR (`pytesseract` / `EasyOCR`) extrae los campos clave (proveedor, fecha, monto, folio) con precisión suficiente en fotos de móvil | Set de 10 facturas reales; meta ≥ 80 % de campos correctos (KPI-2, sección 10) |
| H2 | La orquestación local PHP → Python → C → Prolog completa el pipeline en tiempo aceptable | Marcas de tiempo en BD; meta ≤ 30 s por factura (KPI-1) |
| H3 | Las reglas Prolog clasifican correctamente facturas coincidentes vs. discrepantes contra la orden de compra | Escenarios sembrados con resultado esperado conocido (guion de demo, sección 9.2) |
| H4 | Una lista simple con badges de estado es suficiente para que el usuario entienda el resultado sin capacitación | Observación de 2–3 usuarios durante la demo, sin explicación previa |

**Criterio de éxito global del MVP:** una factura real, fotografiada en vivo, se procesa end-to-end sin intervención manual y su estado se visualiza en la app en ≤ 30 s.

## 2. Objetivos del MVP

| # | Objetivo |
|---|---|
| O-MVP1 | Demostrar el flujo completo: foto → OCR → riesgo → conciliación → estado visible en Android |
| O-MVP2 | Validar la integración técnica de las cinco tecnologías (Java, PHP/MySQL, Python, C, Prolog) |
| O-MVP3 | Obtener retroalimentación temprana de usuarios reales (PyME / bodega) sobre el flujo y el tablero |
| O-MVP4 | Dejar base de código, seed de datos y guía de instalación reproducibles para las iteraciones v1.x |

## 3. Alcance del MVP

El PRD define **28 requisitos funcionales**. El MVP cubre **22** (18 en versión plena + 4 simplificados) y difiere **6** (matriz completa en la sección 12).

### 3.1 Requisitos incluidos en versión plena (18)

| RF | Descripción | Fase PRD |
|---|---|---|
| RF-M2 | Captura de foto con la cámara (`MediaStore.ACTION_IMAGE_CAPTURE`) | F2 |
| RF-M5 | Consulta y visualización del estado de facturas | F4 |
| RF-B1 | Endpoint de recepción de imagen (guardar archivo + factura `Pendiente`) | F1 |
| RF-B2 | Endpoint de consulta de facturas | F1 |
| RF-B3 | Orquestación PHP → Python/C/Prolog y actualización de MySQL | F4 |
| RF-B5 | Respuestas JSON interpretables por la app | F1 |
| RF-D1–RF-D4 | Las 3 tablas (`usuarios`, `ordenes_compra`, `facturas`) con integridad FK | F1 |
| RF-O1–RF-O3 | Script OCR CLI: `procesar_factura.py <imagen>` → datos estructurados | F3 |
| RF-R1–RF-R3 | Binario C CLI: `clasificador_riesgo <monto> <historial>` → % de riesgo y detección de alzas | F3 |
| RF-L1–RF-L2 | `regla.pl` con hechos/reglas y predicado `conciliar(IdOrden)` → Aprobado/Inconsistencia | F3 |

### 3.2 Requisitos simplificados en el MVP (4)

| RF | Versión PRD | Versión MVP | Deuda técnica |
|---|---|---|---|
| RF-M4 | Envío Volley Base64 **o** multipart | Solo **Base64** | multipart → v1.1 |
| RF-M6 | RecyclerView filtrable por estado | RecyclerView simple con badge de estado (sin filtros) | filtros/chips → v1.0 |
| RF-B4 | CRUD de usuarios y órdenes | Datos semilla por script SQL (phpMyAdmin), sin endpoints de administración | CRUD → v1.0 |
| RF-D5 | Persistencia completa de datos OCR | Solo columnas `folio` y `riesgo` en `facturas` | datos OCR completos → v1.0 |

### 3.3 Requisitos diferidos (6)

| RF | Funcionalidad diferida | Motivo del recorte |
|---|---|---|
| RF-M1 + RF-B6 | Login y control de acceso por rol | No valida H1–H4; el MVP usa usuario semilla |
| RF-M3 | Selección desde galería | La cámara basta para el happy path |
| RF-M7 | Alertas de vencimiento (< 3 días) | Requiere `fecha_vencimiento` y caso de uso aparte |
| RF-M8 | Cola offline (Volley/Firebase) | Complejidad alta; el MVP opera solo online |
| RF-L3 | Detección de duplicados | Diferenciador valioso, pero no necesario para validar la conciliación base |

> Nota de estados: en el MVP el flujo termina en **Aprobada** o **Con Discrepancia**; el estado `Rechazada` queda reservado a la revisión manual que llega con login/roles (v1.0).

## 4. Flujo del MVP (happy path)

1. El capturista abre la app y toca **"Tomar foto"** → se abre la cámara (`MediaStore.ACTION_IMAGE_CAPTURE`, RF-M2).
2. La app codifica la imagen en **Base64** y la envía por **Volley** (`POST`) junto con el `orden_id` de referencia (RF-M4).
3. La API PHP guarda la imagen en el servidor y crea la factura en MySQL con estado **`Pendiente`** (RF-B1).
4. PHP ejecuta el pipeline de forma secuencial (RF-B3):
   1. `python3 procesar_factura.py <ruta_imagen>` → JSON con proveedor, fecha, monto y folio (RF-O1–O3).
   2. `./clasificador_riesgo <monto> <historial>` → porcentaje de riesgo (RF-R1–R3).
   3. `swipl -g "conciliar(<idOrden>)." -t halt regla.pl` → `APROBADA` / `DISCREPANCIA:<motivo>` (RF-L1–L2).
5. PHP actualiza la factura en MySQL con los datos extraídos, el riesgo y el **estado final** (RF-B5).
6. La app consulta la lista y muestra la factura con su badge de estado (RF-M5 / RF-M6 simple).

### 4.1 Regla de decisión del MVP

| Condición (se cumplen todas) | Estado resultante |
|---|---|
| Proveedor coincide con la orden **∧** monto ≤ `monto_autorizado` **∧** riesgo < 40 % | **Aprobada** |
| Cualquier otra combinación | **Con Discrepancia** (con motivo registrado) |

> El umbral de riesgo (40 %) es un parámetro configurable. El estado `Rechazada` no se alcanza en el MVP (reservado a revisión manual, v1.0).

### 4.2 Fuera del happy path (mínimo viable)

- **Fallo de un módulo** (OCR/C/Prolog) o timeout: la factura permanece `Pendiente`, se registra el error y puede reintentarse — cumple RNF-5 del PRD (no se pierde la factura).
- **Imagen ilegible** (OCR sin campos): la factura queda `Con Discrepancia` con motivo "OCR sin datos".

## 5. Historias de usuario del MVP

| ID | Como… | Quiero… | Para… | Criterios de aceptación |
|---|---|---|---|---|
| HU-01 | Capturista | tomar una foto de la factura y enviarla con un toque | no teclear datos manualmente | 1) La cámara se abre desde la pantalla principal. 2) El envío retorna un `id` de factura (JSON). 3) La factura aparece como `Pendiente` en la lista. |
| HU-02 | Sistema (Python) | leer la imagen y extraer proveedor, fecha, monto y folio | alimentar la validación sin captura manual | 1) Corre por CLI: `python3 procesar_factura.py <imagen>`. 2) Devuelve una línea JSON con los 4 campos. 3) ≥ 80 % de campos correctos en el set de prueba. |
| HU-03 | Sistema (C) | calcular un % de riesgo con el monto y el historial del proveedor | detectar cobros anómalos | 1) Corre por CLI: `./clasificador_riesgo <monto> <historial>`. 2) Imprime el porcentaje. 3) Monto muy superior al promedio histórico ⇒ riesgo alto. |
| HU-04 | Sistema (Prolog) | conciliar la factura contra su orden de compra | decidir Aprobada/Discrepancia automáticamente | 1) `conciliar(IdOrden)` imprime `APROBADA` o `DISCREPANCIA:<motivo>`. 2) Clasifica correctamente los escenarios sembrados (9.2). |
| HU-05 | Revisor | ver la lista de facturas con su estado | saber cuáles requieren atención | 1) RecyclerView con folio, proveedor, monto, riesgo y badge de estado. 2) Al refrescar se actualizan los estados. 3) Colores distinguibles (verde / ámbar / gris). |

## 6. Arquitectura y componentes del MVP

### 6.1 Diagrama

```
[Android (Java)] ──(1. foto Base64 vía Volley)──> [PHP API] ──(2. guarda imagen + factura)──> [MySQL]
                                                        │
                                                        ├──(3. shell_exec)──> [Python OCR] ──> JSON {proveedor, fecha, monto, folio}
                                                        ├──(4. shell_exec)──> [C riesgo] ─────> % riesgo
                                                        └──(5. swipl)──────> [Prolog] ───────> APROBADA / DISCREPANCIA
                                                        │
                                                        └──(6. actualiza estado)──> [MySQL] <──consulta── [Android (lista)]
```

### 6.2 Endpoints de la API (MVP)

| Método | Endpoint | Función | RF |
|---|---|---|---|
| POST | `/api/facturas.php` | Recibe imagen Base64 + `orden_id`; guarda imagen; crea factura `Pendiente`; dispara el pipeline; devuelve JSON con `id` y `estado` | B1, B3, B5 |
| GET | `/api/facturas.php` | Lista facturas con estado y riesgo (alimenta el RecyclerView) | B2, B5 |
| GET | `/api/facturas.php?id={id}` | Detalle de una factura | B2, B5 |

> `usuarios` y `ordenes_compra` se siembran con script SQL (simplificación de RF-B4).

### 6.3 Pantallas de la app (MVP)

| Pantalla | Contenido |
|---|---|
| **Captura** (`MainActivity`) | Botón "Tomar foto", vista previa de la imagen, botón "Enviar" con indicador de progreso |
| **Listado** (`ListadoActivity`) | `RecyclerView` con tarjetas: folio, proveedor, monto, riesgo (%) y badge de estado |

### 6.4 Contratos de salida de los módulos (stdout, una línea)

| Módulo | Salida esperada |
|---|---|
| Python `procesar_factura.py` | `{"proveedor":"...","fecha":"YYYY-MM-DD","monto":1234.56,"folio":"..."}` |
| C `clasificador_riesgo` | `37.5` (porcentaje de riesgo) |
| Prolog `regla.pl` | `APROBADA` o `DISCREPANCIA:<motivo>` |

> Los contratos se fijan **antes** de programar los módulos (principio contract-first): permiten probar cada módulo en paralelo y con stubs.

### 6.5 RNF del PRD aplicables al MVP

| RNF | Aplicación en el MVP |
|---|---|
| RNF-1 | Pipeline ≤ 30 s por factura (KPI-1) |
| RNF-4 | Saneo/cast de parámetros en `shell_exec` desde el día 1 |
| RNF-5 | El fallo de un módulo no pierde la factura (queda `Pendiente`) |
| RNF-6 | Ejecutable en Laragon/XAMPP; binario compilable con GCC |
| RNF-2, RNF-3, RNF-8 | Relajados: aplican en versión plena a partir de v1.0 |

## 7. Modelo de datos mínimo

| Tabla | Columnas en el MVP | Origen |
|---|---|---|
| `usuarios` | id, nombre, rol, credenciales | RF-D1 (solo datos semilla) |
| `ordenes_compra` | id, proveedor, monto_autorizado, fecha_emision | RF-D2 (solo datos semilla) |
| `facturas` | id, orden_id (FK → ordenes_compra.id), proveedor, monto_facturado, fecha, estado, ruta_imagen **+ folio, riesgo, creado_en, procesado_en** | RF-D3 / RF-D4 + extensión mínima de RF-D5 |

Notas:

- `folio` y `riesgo` son la versión mínima de RF-D5: imprescindibles para conciliar y para mostrar el scoring.
- `creado_en` / `procesado_en` permiten medir el KPI de tiempo del pipeline por factura.
- Estados posibles en el MVP: `Pendiente`, `Aprobada`, `Con Discrepancia` (`Rechazada` reservada a la revisión manual post-MVP).

## 8. Plan de sprints y checkpoints (4 semanas, alineado al PRD sección 11)

| Sprint | Semana | Enfoque | Entregables | Definition of Done (checkpoint) |
|---|---|---|---|---|
| **S1 — Base** | 1 | Entorno Laragon/XAMPP; BD + script seed; API base en PHP; pruebas Postman; **spike OCR** (elegir `pytesseract` vs `EasyOCR` con 5 fotos) | Script SQL (3 tablas + datos semilla); endpoints POST/GET | **C1:** desde Postman se crea una factura (imagen dummy) y se consulta su estado |
| **S2 — Captura** | 2 | App Android: cámara + envío Volley en Base64 | APK de captura y envío | **C2:** una foto real tomada en el móvil se guarda en el servidor y aparece como `Pendiente` en MySQL |
| **S3 — Motores** | 3 | `procesar_factura.py`, `clasificador_riesgo` (GCC), `regla.pl`; pruebas CLI de cada módulo | Los 3 módulos cumplen los contratos de la sección 6.4 | **C3:** cada módulo probado por CLI devuelve salidas correctas con entradas reales |
| **S4 — Integración** | 4 | Orquestación PHP (`shell_exec`); listado en Android; pulido de la demo | Pipeline end-to-end + app con listado | **C4 (demo MVP):** el guion de la sección 9.2 se ejecuta sin errores |

> Los módulos de S3 pueden desarrollarse en paralelo con S2 gracias a los contratos de la sección 6.4 (cada uno se prueba de forma aislada por CLI).

## 9. Criterios de aceptación del MVP

### 9.1 Checklist

- [ ] El script seed crea las 3 tablas con al menos 1 orden de compra e historial de proveedor de prueba.
- [ ] La app toma una foto y la envía (Base64, Volley) junto con el `orden_id`.
- [ ] El endpoint guarda la imagen y registra la factura como `Pendiente`.
- [ ] Python extrae los 4 campos (JSON por stdout).
- [ ] El binario C devuelve el porcentaje de riesgo.
- [ ] Prolog imprime `APROBADA` / `DISCREPANCIA:<motivo>`.
- [ ] PHP encadena los tres módulos y actualiza `facturas` (datos + riesgo + estado).
- [ ] El listado Android refleja el estado final con badge y riesgo.
- [ ] La falla de un módulo deja la factura en `Pendiente` (no se pierde).
- [ ] El guion de demo (9.2) se ejecuta completo sin intervención técnica.

### 9.2 Guion de demo (≈ 10 minutos)

**Preparación:** sembrar la orden OC-001 (proveedor "Papelería Central", `monto_autorizado` 5,000.00) y el historial del proveedor para el módulo C.

| # | Acción | Resultado esperado |
|---|---|---|
| 1 | Fotografía de la factura A: Papelería Central, 4,850.00 | Estado **Aprobada** en ≤ 30 s, con riesgo bajo |
| 2 | Fotografía de la factura B: Papelería Central, 6,200.00 (excede la orden) | **Con Discrepancia**, motivo "monto excede autorizado" |
| 3 | Fotografía de la factura C: proveedor distinto al de la OC-001 | **Con Discrepancia**, motivo "proveedor no coincide" |
| 4 | Mostrar el listado con las tres facturas y sus badges | El usuario identifica el resultado sin explicación previa (valida H4) |
| 5 | (Opcional) Simular fallo momentáneo del pipeline | La factura queda `Pendiente` y se completa al reintentar |

## 10. Métricas de validación y aprendizaje

### 10.1 KPIs del MVP

| KPI | Meta | Cómo se mide |
|---|---|---|
| KPI-1 Tiempo del pipeline (`creado_en` → `procesado_en`) | ≤ 30 s en el 100 % de las facturas de la demo | Columnas de marca de tiempo en `facturas` |
| KPI-2 Precisión OCR (proveedor, fecha, monto, folio) | ≥ 80 % de campos correctos en 10 facturas de prueba | Comparación manual contra los documentos |
| KPI-3 Conciliación correcta | 100 % de los escenarios sembrados (9.2) | Guion de demo |
| KPI-4 Estabilidad de la demo | 0 errores / crashes en el guion completo | Registro de la sesión |

### 10.2 Aprendizajes esperados (decisiones que habilita el MVP)

| Pregunta abierta | Decisión que se tomará |
|---|---|
| ¿`pytesseract` o `EasyOCR`? | Spike en S1 → elección definitiva en S2 |
| ¿Base64 soporta imágenes de tamaño típico sin degradar KPI-1? | Si solo la subida supera 10 s → priorizar multipart en v1.1 |
| ¿Es razonable el umbral de riesgo de 40 %? | Ajuste con los escenarios sembrados del guion de demo |
| ¿Basta el badge de estado o se necesitan filtros? | Retroalimentación de usuarios en la demo (H4) |

## 11. Riesgos del MVP

| # | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| RM1 | OCR impreciso con las fotos del set de demo | Alto | Guiar el encuadre en la app; set fijo de facturas de prueba nítidas; edición de campos llega en v1.0 |
| RM2 | La integración de 5 tecnologías tarda más de lo previsto | Alto | Contratos de salida fijados desde el inicio (sección 6.4): cada módulo se prueba en paralelo y con stubs |
| RM3 | Latencia acumulada del pipeline > 30 s | Medio | Medir por tramo con timestamps; optimizar primero el módulo más lento |
| RM4 | Inyección de comandos vía `shell_exec` | Alto | Cast numérico y validación estricta de parámetros (RNF-4 del PRD) |
| RM5 | Rutas de `python3` / `swipl` / binarios distintas por máquina | Medio | Archivo `config.php` central de rutas + guía de instalación |
| RM6 | Falta de historial real para alimentar el módulo C | Medio | Script seed con historial sintético por proveedor |

## 12. Matriz de trazabilidad PRD → MVP

| RF | Nombre corto | Estado en el MVP | Destino |
|---|---|---|---|
| RF-M1 | Login | Diferido | v1.0 |
| RF-M2 | Cámara | Incluido | — |
| RF-M3 | Galería | Diferido | v1.0 |
| RF-M4 | Envío Volley | Simplificado (solo Base64) | multipart → v1.1 |
| RF-M5 | Consulta de estado | Incluido | — |
| RF-M6 | RecyclerView filtrable | Simplificado (sin filtros) | v1.0 |
| RF-M7 | Alertas de vencimiento | Diferido | v1.0 |
| RF-M8 | Cola offline | Diferido | v1.0 |
| RF-B1 | Recepción de imagen | Incluido | — |
| RF-B2 | Consulta de facturas | Incluido | — |
| RF-B3 | Orquestación | Incluido | — |
| RF-B4 | CRUD usuarios/órdenes | Simplificado (seed SQL) | v1.0 |
| RF-B5 | Respuestas JSON | Incluido | — |
| RF-B6 | Autenticación por rol | Diferido | v1.0 |
| RF-D1 | Tabla `usuarios` | Incluido | — |
| RF-D2 | Tabla `ordenes_compra` | Incluido | — |
| RF-D3 | Tabla `facturas` | Incluido | — |
| RF-D4 | Integridad FK | Incluido | — |
| RF-D5 | Persistencia OCR | Simplificado (folio + riesgo) | v1.0 |
| RF-O1 | Script OCR CLI | Incluido | — |
| RF-O2 | pytesseract / EasyOCR | Incluido | — |
| RF-O3 | Datos estructurados | Incluido | — |
| RF-R1 | `clasificador_riesgo` CLI | Incluido | — |
| RF-R2 | % de riesgo | Incluido | — |
| RF-R3 | Detección de alza | Incluido | — |
| RF-L1 | `regla.pl` | Incluido | — |
| RF-L2 | `conciliar(IdOrden)` | Incluido | — |
| RF-L3 | Duplicados | Diferido | v1.0 |

**Resumen:** 18 incluidos · 4 simplificados · 6 diferidos (28 RF del PRD, sección 6).

## 13. Roadmap post-MVP

| Incremento | Alcance | Requisitos |
|---|---|---|
| **Incremento 1 — completa la v1.0 del PRD** | Login y roles, galería, filtros del tablero, alertas de vencimiento (< 3 días), detección de duplicados, cola offline, CRUD completo y datos OCR completos | RF-M1, M3, M6 (completo), M7, M8, B4 (completo), B6, D5 (completo), L3 |
| **Incremento 2 — v1.1 (backlog)** | Envío multipart, pre-procesado avanzado de imagen, revisión manual in-app (aprobar/rechazar), reportes/exportación, hardening (logs, reintentos, configuración) | Mejoras continuas |

> **Regla de avance:** ningún incremento post-MVP se inicia sin que el MVP haya superado el guion de demo (sección 9.2) y recogido la retroalimentación de usuarios (H4).

---

## 14. Estado de la v1 (actualización del 29/10/2026)

> Esta sección NO reescribe el MVP original (que quedó cerrado el 01/10):
> declara dónde vive HOY cada función prometida. El documento original
> describía un sistema donde PHP orquestaba todo en un servidor local.

### 14.1 Por qué cambió la arquitectura

El 22/10 el producto cambió de rumbo con dos decisiones del autor: (1)
**las pruebas ya no deben depender de Laragon** y (2) **las órdenes las
sube el proveedor** a un correo de cliente, sin IPs. Desde el Sprint 10 la
app habla **directo con Firebase** (Auth/Firestore/Storage) y el pipeline
corre **EN EL TELÉFONO** (`Motores.java`: ML Kit para OCR + réplicas
exactas de la fórmula del C y de las reglas del Prolog). La pila
PHP+MySQL+Python+C+Prolog quedó como **camino servidor** (demo académica
de los motores originales) y sigue re-ejecutable. Diseño completo y
justificación de las 10 decisiones: `docs/Arquitectura.md`.

### 14.2 Función por función: qué camino la cubre y dónde está

| Función prometida | Camino | Archivo principal | Estado |
|---|---|---|---|
| Captura por cámara (RF-M2) | Teléfono | `android/…/MainActivity.java` | ✔ |
| Elegir de galería (RF-M3) | Teléfono | `MainActivity.java` | ✔ (adelantado del plan) |
| Envío de imagen (RF-M4) | Teléfono (JPEG→Storage) | `FirebaseServicio.java` | ✔ simplificado (multipart → v1.1) |
| Listado con badges (RF-M5/H4) | Teléfono | `ListadoActivity.java` | ✔ |
| Filtros del tablero (RF-M6) | Teléfono | `ListadoActivity.java` (chips) | ✔ |
| Alertas de vencimiento (RF-M7) | Teléfono | `RecordatorioWorker.java` + `PagosActivity` | ✔ |
| Cola offline (RF-M8) | — | — | ⏳ **pendiente** (único RF del Incremento 1) |
| API REST (RF-B1..B3, B5) | Servidor | `api/facturas.php`, `api/procesar.php` | ✔ (demo académica) |
| CRUD usuarios/órdenes (RF-B4) | Servidor | `api/usuarios.php`, `api/ordenes.php` | ✔ |
| Login y roles (RF-M1, RF-B6) | Teléfono (Firebase Auth) · Servidor (S5: token 8 h) | `LoginActivity.java` · `api/sesion.php` | ✔ |
| Base de datos (RF-D1..D4) | Ambos (MySQL servidor · Firestore teléfono) | `database/seed.sql` | ✔ |
| OCR de campos (RF-O*) | Teléfono (ML Kit) · Servidor (pytesseract) | `Motores.java` · `motores/procesar_factura.py` | ✔ (mismas regex del spike) |
| Riesgo (RF-R*) | Teléfono · Servidor (C/GCC) | `Motores.java` · `motores/clasificador_riesgo.c` | ✔ (misma fórmula) |
| Conciliación reglas 0-5 (RF-L1/L2) | Teléfono · Servidor (Prolog) | `Motores.java` · `motores/regla.pl` | ✔ (cláusula por cláusula) |
| Duplicados (RF-L3) | Teléfono | `FirebaseServicio.java` (`facturasLookup` por dueño y proveedor, 29/10) | ✔ |
| Datos OCR completos (RF-D5) | Ambos | `procesar_factura.py` (texto crudo) | ✔ |
| Avisos y cuenta de proveedor (RF-F1..F7) | Teléfono | Firebase + `ProveedorActivity` | ✔ |
| Cuentas por pagar / comprobante (RF-P1..P3) | Teléfono | `PagosActivity.java` | ✔ (pago solo con foto, S8.3) |

Verificación vigente: 10/10 checkpoints superados; el **C9 fue re-corrido
el 29/10 contra el código actual: 14/14 OK** (`pruebas\test_s9.ps1`,
commit base `66de852`), y el guion de demo de los DOS caminos con la misma
factura está en `docs/DEMO.md`.

### 14.3 Pendiente declarado

1. **RF-M8 (cola offline)** — único requisito del Incremento 1 sin entregar.
2. **«Enviar factura» de la app NO usa `api/procesar.php`**: va SIEMPRE
   por el camino principal (Firebase + pipeline en el teléfono). El
   camino servidor se demuestra con `docs/DEMO.md` y `pruebas\test_s9.ps1`;
   conectar el botón también al servidor (Volley ya está) queda como
   deuda v1.1.
3. El CRUD de administración (AdminActivity) NO se migró a Firestore: se
   descartó en S10.3 (una cuenta por empresa; el registro público solo
   crea el rol más pequeño). El CRUD completo vive en el camino servidor.
4. Reglas de seguridad: `firestore.rules` PUBLICADO en vivo (29/10,
   `firebase deploy --only firestore:rules`); `storage.rules` queda sin
   publicar por decisión económica — Storage exige el plan Blaze
   (tarjeta de crédito) y no se cuenta con el medio para contratarlo
   (ver `docs/Reporte_QA.md` §7 e `INSTALACION.md` §8.4).

---

*Fin del documento — MVP_CPP v1.0 · derivado de `PRD_CPP.md` (que a su vez proviene de `docs/propuestas.txt` y `readme.md`)*
