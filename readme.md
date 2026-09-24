# Plataforma Inteligente para la Gestión de Cuentas por Pagar

Sistema móvil y distribuido para automatizar la captura, validación y conciliación de facturas y comprobantes de pago mediante procesamiento de texto e inferencia lógica.

1.**Descripción del Proyecto**

El proyecto automatiza la gestión de cuentas por pagar sustituyendo la revisión manual de documentos por un flujo digital inteligente:

Captura Móvil: El usuario toma una fotografía de la factura o ticket desde la aplicación móvil.

Extracción de Datos: El sistema lee la imagen y extrae automáticamente los datos clave (proveedor, fecha, monto total e ítems).

Validación y Conciliación: Se evalúa la información mediante reglas de negocio para detectar discrepancias con las órdenes de compra y calcular posibles riesgos financieros.

Alertas y Estado: La app notifica si el comprobante fue aprobado, si tiene inconsistencias o si requiere revisión manual.

2. **Tecnologías Utilizadas**

Para el desarrollo del sistema se empleará la siguiente pila tecnológica:

Android Nativo (Java): Aplicación móvil para la captura de comprobantes, envío de datos y visualización de tableros de control.

PHP: API RESTful para el procesamiento del backend, gestión de peticiones y conexión entre módulos.

MySQL (Laragon/XAMPP): Base de datos relacional para almacenar usuarios, órdenes de compra, facturas y estados de validación.

Python: Script para la extracción automática de texto desde las imágenes de los comprobantes.

C: Algoritmo/modelo compilado para el cálculo de scoring de riesgo y análisis de patrones de monto.

Prolog: Motor de reglas lógicas para la conciliación e inferencia entre facturas y órdenes de compra.

Firebase / Volley: Comunicación en tiempo real y peticiones de red entre la app móvil y el servidor.

3. **Herramientas y Entornos de Desarrollo**

Android Studio: Entorno para la programación de la app en Java.

Visual Studio Code / GCC: Entorno para la escritura de scripts en Python y compilación de módulos en C.

SWI-Prolog: Entorno de ejecución para el archivo de reglas de inferencia.

Laragon o XAMPP: Servidor local (Apache, MySQL, PHP) para la API y base de datos.

Postman: Herramienta para realizar pruebas de endpoints y peticiones HTTP.

4.**Dominios de Aplicación**

Control de Comprobantes para PyMEs: Gestión digital de notas y comprobantes en pequeños comercios.

Comprobación de Viáticos: Control automático de gastos administrativos o institucionales contra presupuestos fijados.

Recepción en Bodega / Almacén: Validación en tiempo real del comprobante entregado por el proveedor contra la orden registrada.

5. **Objetivos del Proyecto**

Objetivo General: Desarrollar un sistema móvil y distribuido que optimice la validación y conciliación de cuentas por pagar mediante herramientas informáticas locales y distribuidas.

Objetivos Específicos:

-Diseñar la arquitectura de comunicación entre Android, PHP/MySQL y los módulos de análisis.

-Implementar la captura móvil de documentos y su procesamiento automático.

-Desarrollar las reglas de conciliación en Prolog para detectar inconsistencias en los pagos.

-Crear tableros de control en la app móvil con indicadores claros para el usuario.