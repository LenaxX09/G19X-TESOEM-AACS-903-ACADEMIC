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

// --- Sprint 5 (v1.0, RF-M1/RF-B6): sesiones con token X-CPP-Token ---
// TTL de la sesión en horas; al expirar la app recibe 401 y vuelve al login.
define('SESION_TTL_HORAS', 8);

// --- Sprint 9 (v1.0, RF-M7): vencimiento de cuentas por pagar -------------
// Fecha límite de pago = fecha del documento de la factura (o la de subida
// si el OCR no la leyó) + PAGO_DIAS_LIMITE días. Si faltan menos de
// ALERTA_VENCE_EN_DIAS días (o ya pasó), la app pinta la cuenta en ROJO.
define('PAGO_DIAS_LIMITE', 30);
define('ALERTA_VENCE_EN_DIAS', 3);

// --- Sprint 6 (v1.0, Firebase): push FCM + escritura en Firestore ---
// Clave de servicio del proyecto cpp-facturas. Es un SECRETO: NO va en git
// (.gitignore la excluye). Si falta, la API sigue funcionando igual —
// solo deja de notificar (best-effort).
define('FIREBASE_SA', __DIR__ . '/firebase-service-account.json');

// --- Motores externos (se consumen en el Sprint 4, RF-B3) ---
// Rutas REALES verificadas el 30/09/2026 en este equipo (Sprint 3, spike OCR):
//   Python 3.12.10 (winget) · SWI-Prolog (C:\Program Files\swipl) · GCC MinGW.
// NOTA S4: al usarlas en shell_exec, envolver SIEMPRE entre comillas dobles
// porque contienen espacios, además de escapeshellarg para los argumentos.
define('PYTHON_BIN', 'C:/Users/acaba/AppData/Local/Programs/Python/Python312/python.exe');
define('SWIPL_BIN', 'C:/Program Files/swipl/bin/swipl.exe');
define('GCC_BIN', 'C:/MinGW/bin/gcc.exe'); // para compilar clasificador_riesgo.c en S3
define('RUTA_MOTOR_OCR', __DIR__ . '/../motores/procesar_factura.py');
define('RUTA_BIN_RIESGO', __DIR__ . '/../motores/clasificador_riesgo.exe');
define('RUTA_REGLA_PROLOG', __DIR__ . '/../motores/regla.pl');
