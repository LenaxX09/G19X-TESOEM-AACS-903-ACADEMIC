<?php
/**
 * proveedores.php — Sprint 6 (RF-F3): lista de proveedores para el botón
 * «🏬 Soy proveedor» de la app.
 *
 *   GET /api/proveedores.php  → { proveedores: [{proveedor, slug}], total }
 *
 * Decisión de seguridad EXPLÍCITA: es un endpoint PÚBLICO (sin token).
 * Expone únicamente los NOMBRES de los proveedores de las órdenes de
 * compra — datos que un proveedor necesita para identificarse al recibir
 * sus avisos. Nada de montos, folios ni facturas. En producción se
 * endurecería (p. ej. código por proveedor); para la demo es suficiente
 * y así el proveedor no necesita cuenta del sistema.
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/firebase.php';   // solo por fb_slug(); no usa la clave

try {
    if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
        responder(405, 'METODO_NO_PERMITIDO', 'Use GET.');
    }

    $filas = db()->query(
        'SELECT DISTINCT proveedor FROM ordenes_compra ORDER BY proveedor'
    )->fetchAll(PDO::FETCH_COLUMN);

    $proveedores = [];
    foreach ($filas as $nombre) {
        $proveedores[] = ['proveedor' => $nombre, 'slug' => fb_slug($nombre)];
    }

    responder(200, 'OK', 'Proveedores disponibles para recibir avisos.', [
        'proveedores' => $proveedores,
        'total'       => count($proveedores),
    ]);
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}
