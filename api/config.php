<?php
/**
 * config.php — Configuración central del proyecto CPP.
 *
 * Mitiga el riesgo R4 del PRD: rutas heterogéneas de python/swipl/binarios
 * según la máquina → TODO lo configurable vive en este único archivo.
 *
 * Sprint 1: solo se usan las credenciales de MySQL y UPLOAD_DIR.
 * Sprint 4: se usarán las rutas de los motores externos (RF-B3).
 */

// --- Base de datos (valores por defecto de Laragon / XAMPP: root sin contraseña) ---
define('DB_HOST', '127.0.0.1');
define('DB_NAME', 'cpp');
define('DB_USER', 'root');
define('DB_PASS', '');
define('DB_CHARSET', 'utf8mb4');

// --- Almacenamiento de imágenes recibidas por la API (RF-B1) ---
define('UPLOAD_DIR', __DIR__ . '/../uploads');

// --- Umbral de riesgo configurable (MVP_CPP sección 4.1: riesgo < 40 % ⇒ aprobable) ---
define('UMBRAL_RIESGO', 40.0);

// --- Motores externos (se consumen en el Sprint 4, RF-B3) ---
// Ajustar a las rutas reales de tu máquina. Ejemplos:
//   PHP  : 'C:/Python312/python.exe'
//   SWIPL: 'C:/Program Files/swipl/bin/swipl.exe'
define('PYTHON_BIN', 'python');
define('SWIPL_BIN', 'swipl');
define('RUTA_MOTOR_OCR', __DIR__ . '/../motores/procesar_factura.py');
define('RUTA_BIN_RIESGO', __DIR__ . '/../motores/clasificador_riesgo.exe');
define('RUTA_REGLA_PROLOG', __DIR__ . '/../motores/regla.pl');
