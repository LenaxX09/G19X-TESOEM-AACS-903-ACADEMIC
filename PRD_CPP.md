# PRD — Plataforma Inteligente para la Gestión de Cuentas por Pagar (CPP)

> **Documento de Requisitos del Producto (Product Requirements Document)**
> Generado a partir de `docs/propuestas.txt` (estrategia técnica y plan por fases) y `readme.md` (descripción general del proyecto).

| Campo | Detalle |
|---|---|
| **Producto** | Plataforma Inteligente para la Gestión de Cuentas por Pagar |
| **Código del proyecto** | CPP (Cuentas Por Pagar) |
| **Versión del PRD** | 1.0 |
| **Fecha** | 23/09/2026 |
| **Estado** | Borrador — pendiente de aprobación |
| **Documentos base** | `docs/propuestas.txt`, `readme.md` |

---

## 1. Resumen ejecutivo

La **Plataforma Inteligente para la Gestión de Cuentas por Pagar (CPP)** es un sistema móvil y distribuido que automatiza la captura, extracción, validación y conciliación de facturas y comprobantes de pago, sustituyendo la revisión manual de documentos por un flujo digital inteligente:

1. **Captura móvil:** el usuario toma una fotografía de la factura o ticket desde la aplicación Android.
2. **Extracción de datos:** el sistema lee la imagen y extrae automáticamente los datos clave (proveedor, fecha, monto total, folio e ítems).
3. **Validación y conciliación:** se evalúa la información mediante reglas de negocio para detectar discrepancias con las órdenes de compra y calcular riesgos financieros.
4. **Alertas y estado:** la app notifica si el comprobante fue aprobado, tiene inconsistencias o requiere revisión manual.

El sistema integra cinco tecnologías (Android/Java, PHP/MySQL, Python, C y Prolog) coordinadas por una API RESTful en PHP sobre Laragon/XAMPP, con la app Android como cliente y las funcionalidades diferenciadoras de detección de duplicados, alertas de vencimiento, análisis de anomalías y sincronización offline.

## 2. Problema y oportunidad

**Problema.** La gestión de cuentas por pagar en PyMEs, bodegas y áreas administrativas se realiza de forma manual: se revisan comprobantes a mano, se concilian contra órdenes de compra con hojas de cálculo y los errores (facturas duplicadas, montos anómalos, vencimientos perdidos) se detectan tarde.

**Oportunidad.** Automatizar el flujo con una solución local (Laragon/XAMPP) que combine OCR (Python), análisis numérico compilado (C) e inferencia lógica (Prolog), sin depender de servicios cloud propietarios para el procesamiento.

**Dominios de aplicación:**

| Dominio | Aplicación |
|---|---|
| Control de comprobantes para PyMEs | Gestión digital de notas y comprobantes en pequeños comercios |
| Comprobación de viáticos | Control automático de gastos administrativos o institucionales contra presupuestos fijados |
| Recepción en bodega / almacén | Validación en tiempo real del comprobante del proveedor contra la orden registrada |

## 3. Objetivos

### 3.1 Objetivo general

Desarrollar un sistema móvil y distribuido que optimice la validación y conciliación de cuentas por pagar mediante herramientas informáticas locales y distribuidas.

### 3.2 Objetivos específicos

| # | Objetivo | Módulo responsable |
|---|---|---|
| OE-1 | Diseñar la arquitectura de comunicación entre Android, PHP/MySQL y los módulos de análisis | Global |
| OE-2 | Implementar la captura móvil de documentos y su procesamiento automático | Android + Python |
| OE-3 | Calcular scoring de riesgo y detectar anomalías en montos por proveedor | C |
| OE-4 | Desarrollar las reglas de conciliación en Prolog para detectar inconsistencias y duplicados | Prolog |
| OE-5 | Crear tableros de control en la app móvil con indicadores claros para el usuario | Android |

## 4. Alcance

### 4.1 Dentro del alcance (v1.0)

- App Android nativa (Java): cámara/galería, envío de imágenes, tableros y alertas.
- API RESTful en PHP (sobre Laragon/XAMPP) que orquesta todo el flujo.
- Base de datos MySQL: `usuarios`, `ordenes_compra`, `facturas`.
- Script Python de OCR (`pytesseract` / `EasyOCR`) para extracción de datos.
- Ejecutable en C para scoring de riesgo y análisis de anomalías de montos.
- Archivo de reglas Prolog (SWI-Prolog) para conciliación factura ↔ orden y duplicados.
- Tablero con alertas de vencimiento (< 3 días) y filtros por estado.
- Sincronización offline/online con cola de facturas pendientes (Volley / Firebase).

### 4.2 Fuera del alcance (v1.0)

- Publicación en Google Play y despliegue del backend en la nube pública.
- Facturación electrónica firmada digitalmente o integración con organismos fiscales.
- Pagos reales o integración bancaria.
- Clientes iOS o panel web de administración.
- Entrenamiento de modelos de ML propios (el OCR usa librerías existentes).
- Multiidioma (v1.0 solo español).

## 5. Usuarios y roles

| Rol | Descripción | Acciones principales |
|---|---|---|
| **Capturista / Colaborador** | Usuario de la app que fotografía comprobantes | Tomar/seleccionar foto, enviar factura, consultar estado, trabajar sin conexión |
| **Revisor / Contabilidad** | Valida discrepancias marcadas por el sistema | Consultar tableros, revisar facturas "Con Discrepancia", aprobar o rechazar |
| **Administrador** | Gestiona el sistema | Administrar usuarios y roles, registrar órdenes de compra, consultar indicadores |

> El campo `usuarios.rol` materializa estos perfiles; el control de acceso por rol se define en RF-B6.

## 6. Requisitos funcionales

Prioridad: **A** = Alta (imprescindible), **M** = Media (deseable), **B** = Baja (diferible). La fase refiere al plan de la sección 11.

### 6.1 App móvil (Android / Java)

| ID | Requisito | Prioridad | Fase |
|---|---|---|---|
| RF-M1 | Inicio de sesión contra la API con credenciales de `usuarios` | A | F2 |
| RF-M2 | Capturar foto con la cámara del dispositivo (`MediaStore.ACTION_IMAGE_CAPTURE`) | A | F2 |
| RF-M3 | Seleccionar imagen desde la galería como alternativa a la cámara | M | F2 |
| RF-M4 | Enviar imagen al servidor vía Volley (`POST`, codificada en Base64 o multipart) junto a metadatos básicos | A | F2 |
| RF-M5 | Consultar y visualizar el estado de las facturas (Aprobada / Con Discrepancia / Pendiente / Rechazada) | A | F4 |
| RF-M6 | Tablero con lista filtrable por estado usando `RecyclerView` | A | F4 |
| RF-M7 | Alerta temprana de vencimientos: marcar en rojo cuentas por pagar cuya fecha límite venza en menos de 3 días | A | F4 |
| RF-M8 | Modo offline: encolar capturas sin conexión y procesarlas al recuperar señal (Volley / Firebase) | M | F4 |

### 6.2 API / Backend (PHP)

| ID | Requisito | Prioridad | Fase |
|---|---|---|---|
| RF-B1 | Endpoint de recepción de imagen: guardar archivo y registrar la factura en MySQL con estado inicial `Pendiente` | A | F1 |
| RF-B2 | Endpoint de consulta de facturas (individual y filtrada por estado) | A | F1 |
| RF-B3 | Orquestación: invocar a Python, C y Prolog desde PHP (`exec()` / `shell_exec()`) y actualizar MySQL con el resultado | A | F4 |
| RF-B4 | Endpoints CRUD para `usuarios` y `ordenes_compra` | A | F1 |
| RF-B5 | Respuestas JSON con código de resultado interpretables por la app | A | F1 |
| RF-B6 | Autenticación y control de acceso por rol | M | F2 |

### 6.3 Base de datos (MySQL)

| ID | Requisito | Prioridad | Fase |
|---|---|---|---|
| RF-D1 | Tabla `usuarios` (ID, nombre, rol, credenciales) | A | F1 |
| RF-D2 | Tabla `ordenes_compra` (ID, proveedor, monto_autorizado, fecha_emision) | A | F1 |
| RF-D3 | Tabla `facturas` (ID, orden_id, proveedor, monto_facturado, fecha, estado, ruta_imagen) | A | F1 |
| RF-D4 | Integridad referencial `facturas.orden_id → ordenes_compra.id` | A | F1 |
| RF-D5 | Persistencia de datos extraídos por OCR (folio, proveedor detectado) para habilitar detección de duplicados | M | F3 |

### 6.4 Procesamiento de imagen (Python — OCR)

| ID | Requisito | Prioridad | Fase |
|---|---|---|---|
| RF-O1 | Script `procesar_factura.py` que reciba la ruta de la imagen como argumento de línea de comandos | A | F3 |
| RF-O2 | Extraer texto de la imagen mediante `pytesseract` o `EasyOCR` | A | F3 |
| RF-O3 | Devolver datos estructurados (proveedor, fecha, monto, folio) para los módulos posteriores | A | F3 |

### 6.5 Motor de riesgo (C)

| ID | Requisito | Prioridad | Fase |
|---|---|---|---|
| RF-R1 | Ejecutable `clasificador_riesgo` que reciba como parámetros el monto y el historial del proveedor | A | F3 |
| RF-R2 | Devolver un porcentaje de riesgo o anomalía | A | F3 |
| RF-R3 | Detectar si el promedio de cobro de un proveedor sufrió un aumento repentino sin justificación | A | F3 |

### 6.6 Conciliación lógica (Prolog)

| ID | Requisito | Prioridad | Fase |
|---|---|---|---|
| RF-L1 | Archivo `regla.pl` con hechos y reglas que comparen los datos de la factura (extraídos por Python) con la orden de compra registrada en MySQL | A | F3 |
| RF-L2 | Predicado `conciliar(IdOrden)` ejecutable vía `swipl -g ... -t halt` que retorne Aprobado / Inconsistencia | A | F3 |
| RF-L3 | Detección de duplicados: mismo folio o misma combinación (monto + fecha + proveedor) ya registrada previamente | A | F3 |

### 6.7 Flujo de estados de la factura

```
Pendiente ──(pipeline: OCR + riesgo + conciliación)──┬──> Aprobada
                                                     ├──> Con Discrepancia ──(revisión manual)──> Aprobada / Rechazada
                                                     └──> Rechazada
```

## 7. Requisitos no funcionales

| ID | Categoría | Requisito |
|---|---|---|
| RNF-1 | Rendimiento | El pipeline completo (subida + OCR + riesgo + conciliación) ≤ 30 s por factura en entorno local |
| RNF-2 | Rendimiento | Endpoints de consulta con respuesta ≤ 500 ms en red local |
| RNF-3 | Usabilidad | Captura y envío de una factura en ≤ 4 interacciones desde la pantalla principal |
| RNF-4 | Seguridad | Credenciales con hash, validación de entradas y saneo estricto de parámetros en `shell_exec` |
| RNF-5 | Confiabilidad | El fallo de un módulo (OCR/C/Prolog) no debe perder la factura: permanece `Pendiente` y se reintenta |
| RNF-6 | Portabilidad | El backend corre en Laragon o XAMPP sin cambios; el binario C es compilable con GCC |
| RNF-7 | Mantenibilidad | Contratos de integración documentados (sección 8.3) y módulos desacoplados por responsabilidad |
| RNF-8 | Usabilidad | Tableros legibles con codificación por color para estados y vencimientos |

## 8. Arquitectura del sistema

### 8.1 Flujo de comunicación

```
[Android (Java)]
       │ (1. Sube foto vía Volley)
       ▼
[PHP API en Laragon] ──── (2. Guarda imagen y registra datos básicos en MySQL)
       │
       ├─── (3. Ejecuta script) ──> [Python (OCR/Lectura)]
       │                                     │ (Retorna datos extraídos)
       │                                     ▼
       ├─── (4. Ejecuta binario) ─> [Modelo en C (Puntuación de Riesgo)]
       │                                     │ (Retorna nivel de riesgo)
       │                                     ▼
       └─── (5. Invocación) ──────> [Prolog (Reglas de Conciliación)]
                                             │ (Retorna Aprobado/Inconsistencia)
                                             ▼
[Android (Java)] <── (6. Recibe respuesta/Alertas) ── [PHP (Actualiza MySQL)]
```

### 8.2 Responsabilidades por módulo

| Módulo | Tecnología | Responsabilidad |
|---|---|---|
| App móvil | Android (Java) | Captura de comprobantes, envío, tableros, alertas, cola offline |
| API | PHP | Recepción de peticiones, orquestación de módulos, persistencia |
| Base de datos | MySQL | Usuarios, órdenes de compra, facturas y estados de validación |
| OCR | Python | Lectura de la imagen y extracción de datos |
| Motor de riesgo | C | Scoring de riesgo y análisis de patrones de monto |
| Conciliación | Prolog | Reglas de inferencia: coincidencia con orden y duplicados |

### 8.3 Contratos de integración (invocación desde PHP)

| Integración | Comando de referencia |
|---|---|
| PHP ↔ Python | `$resultadoTexto = shell_exec("python3 procesar_factura.py ruta/imagen.jpg");` |
| PHP ↔ C | `$scoring = shell_exec("./clasificador_riesgo " . $monto . " " . $historial);` |
| PHP ↔ Prolog | `$validacion = shell_exec("swipl -g 'conciliar(" . $idOrden . ")." -t halt regla.pl");` |

**Convenciones:**
- Cada módulo devuelve una salida de texto estructurada e interpretable por PHP (se recomienda una línea JSON simple).
- PHP valida y sanea todos los parámetros antes de componer los comandos (evitar inyección de comandos, ver RNF-4).
- Las rutas de los ejecutables (`python3`, `clasificador_riesgo`, `swipl`) se centralizan en un archivo de configuración.

## 9. Modelo de datos (MySQL)

### 9.1 `usuarios`

| Campo | Tipo sugerido | Descripción |
|---|---|---|
| id | INT PK AUTO_INCREMENT | Identificador del usuario |
| nombre | VARCHAR(100) | Nombre del usuario |
| rol | ENUM | Rol de acceso (capturista / revisor / admin) |
| credenciales | VARCHAR(255) | Usuario y contraseña (almacenada como hash) |

### 9.2 `ordenes_compra`

| Campo | Tipo sugerido | Descripción |
|---|---|---|
| id | INT PK AUTO_INCREMENT | Identificador de la orden |
| proveedor | VARCHAR(150) | Proveedor autorizado |
| monto_autorizado | DECIMAL(12,2) | Monto máximo autorizado en la orden |
| fecha_emision | DATE | Fecha de emisión de la orden |

### 9.3 `facturas`

| Campo | Tipo sugerido | Descripción |
|---|---|---|
| id | INT PK AUTO_INCREMENT | Identificador de la factura |
| orden_id | INT FK → ordenes_compra.id | Orden de compra asociada |
| proveedor | VARCHAR(150) | Proveedor de la factura |
| monto_facturado | DECIMAL(12,2) | Monto facturado (registrado/extraído) |
| fecha | DATE | Fecha de la factura |
| estado | ENUM | `Pendiente`, `Aprobada`, `Rechazada`, `Con Discrepancia` |
| ruta_imagen | VARCHAR(255) | Ruta del archivo de imagen en el servidor |

**Extensiones previstas en la Fase 3** (para habilitar las funcionalidades diferenciadoras): `folio` y datos OCR extraídos (duplicados), `riesgo` (scoring del módulo C) y `fecha_vencimiento` (alertas de vencimiento).

## 10. Funcionalidades diferenciadoras

1. **Detección automática de duplicados (Prolog):** comprobar si una factura con el mismo folio o la combinación (monto + fecha + proveedor) ya fue registrada previamente.
2. **Alerta temprana de vencimientos (Android):** tablero que marca en rojo las cuentas por pagar cuya fecha límite esté a menos de 3 días de vencer.
3. **Análisis de anomalías en montos (C):** evaluar si el promedio de cobro de un proveedor determinado ha sufrido un aumento repentino sin justificación.
4. **Sincronización offline / online (Android + Volley/Firebase):** permitir capturar fotos sin conexión; al recuperar la señal, la cola de facturas pendientes se procesa automáticamente.

## 11. Plan de implementación por fases (4 semanas)

| Fase | Semana | Alcance | Entregables | Criterio de aceptación |
|---|---|---|---|---|
| **F1. Entorno y Base de Datos** | 1 | Configurar Laragon/XAMPP (Apache + MySQL); diseñar BD en phpMyAdmin; API básica en PHP; pruebas con Postman | Script SQL de las 3 tablas; endpoints de inserción/consulta | Endpoints probados en Postman con inserción y consulta de registros en MySQL |
| **F2. Captura móvil y HTTP** | 2 | Proyecto Android en Java; módulo de cámara (`MediaStore.ACTION_IMAGE_CAPTURE`) o galería; envío con Volley (Base64 o multipart) | APK funcional de captura y envío | La foto llega al servidor y se registra una fila `Pendiente` en `facturas` |
| **F3. Procesamiento y validación** | 3 | Script Python OCR (`pytesseract`/`EasyOCR`); ejecutable C de evaluación de riesgo; reglas `.pl` de conciliación | `procesar_factura.py`, `clasificador_riesgo` (binario), `regla.pl` | Cada módulo probado individualmente con entradas reales y salidas correctas |
| **F4. Integración y tableros** | 4 | Orquestación en PHP (`exec()`/`shell_exec()`); tableros Android (`RecyclerView`) con filtros por estado | Pipeline end-to-end; app con tableros y alertas | Una factura subida se procesa completa (OCR → riesgo → conciliación) y su estado se refleja en la app |

**Hitos de validación:** al cierre de cada fase se demuestra el flujo acumulado; el hito final (fin de F4) es la demo completa: foto → OCR → scoring → conciliación → estado visible en Android.

## 12. Entorno de desarrollo y herramientas

| Herramienta | Uso en el proyecto |
|---|---|
| Android Studio | Programación de la app móvil en Java |
| Laragon / XAMPP | Servidor local (Apache, MySQL, PHP) para la API y la base de datos |
| phpMyAdmin | Creación y administración de la base de datos MySQL |
| Visual Studio Code / GCC | Escritura de scripts Python y compilación del módulo en C |
| SWI-Prolog | Entorno de ejecución del archivo de reglas de inferencia |
| Postman | Pruebas de endpoints y peticiones HTTP |
| Volley / Firebase | Peticiones de red y sincronización offline/online |

## 13. Métricas de éxito (KPIs)

| KPI | Meta |
|---|---|
| Tiempo de procesamiento por factura (subida → estado final) | ≤ 30 s |
| Precisión de extracción OCR (campos clave correctos) | ≥ 80 % en set de prueba |
| Duplicados detectados en pruebas controladas | 100 % de los casos sembrados |
| Facturas conciliadas sin intervención manual | ≥ 70 % del set de prueba |
| Pérdida de capturas en modo offline | 0 % |

## 14. Riesgos y mitigaciones

| # | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R1 | OCR con baja precisión según la calidad de la foto | Alto | Guía de encuadre en la app; pre-procesamiento de imagen en Python; permitir edición de campos antes de conciliar |
| R2 | Latencia al encadenar tres procesos desde PHP | Medio | Procesamiento por factura con estados intermedios en BD; la app consulta el estado de forma asíncrona |
| R3 | Inyección de comandos vía `shell_exec` | Alto | Validación y saneo estricto de parámetros; cast numérico de montos e IDs |
| R4 | Rutas heterogéneas de `python3`, `swipl` y binarios según máquina | Medio | Archivo de configuración central de rutas + guía de instalación |
| R5 | Pérdida de datos capturados sin conexión | Medio | Persistencia local de la cola de pendientes y reintentos automáticos |
| R6 | Cronograma de 4 semanas ajustado | Medio | Priorización A/M/B de la sección 6; los requisitos "M" y "B" son diferibles sin romper el flujo principal |

## 15. Criterios de aceptación global (Definition of Done)

- [ ] Las 3 tablas (`usuarios`, `ordenes_compra`, `facturas`) creadas en MySQL con integridad referencial y datos semilla.
- [ ] API PHP probada en Postman: recepción de imagen, registro de factura y consultas.
- [ ] La app Android captura o selecciona una foto y la envía vía Volley al servidor.
- [ ] Python extrae proveedor, fecha, monto y folio desde la imagen subida.
- [ ] El binario C devuelve un porcentaje de riesgo a partir del monto y el historial del proveedor.
- [ ] Prolog resuelve `conciliar(IdOrden)` y detecta duplicados por folio y por (monto + fecha + proveedor).
- [ ] PHP orquesta los tres módulos (`exec`/`shell_exec`) y actualiza el estado en MySQL.
- [ ] Android muestra el tablero filtrable por estado y alerta en rojo vencimientos < 3 días.
- [ ] La cola offline se procesa correctamente al recuperar la conexión.
- [ ] Documentación de instalación y ejecución del proyecto completo disponible en el repositorio.

## 16. Glosario

| Término | Definición |
|---|---|
| **OCR** | Optical Character Recognition; tecnología para leer texto a partir de imágenes |
| **Conciliación** | Comparación factura ↔ orden de compra para aprobar o marcar inconsistencias |
| **Folio** | Número identificador impreso en la factura |
| **Scoring de riesgo** | Porcentaje de riesgo/anomalía calculado por el módulo en C |
| **Volley** | Librería de Android para peticiones HTTP |
| **Base64 / multipart** | Formatos de codificación de imagen en peticiones `POST` |
| **Laragon / XAMPP** | Distribuciones locales de Apache + MySQL + PHP |
| **SWI-Prolog** | Intérprete de Prolog usado como motor de reglas |

---

*Fin del documento — PRD_CPP v1.0 · derivado de `propuestas.txt` y `readme.md`*
