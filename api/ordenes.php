<?php
/**
 * ordenes.php — Consulta de órdenes de compra (apoyo al checkpoint C1 y a la app).
 *
 *   GET /api/ordenes.php            → listado de órdenes (para elegir orden_id).
 *   GET /api/ordenes.php?id=1       → detalle + historial promedio del proveedor
 *                                     (los datos que consumirá clasificador_riesgo en C).
 *
 * El CRUD completo (RF-B4) llega en v1.0; en el MVP las órdenes se siembran por SQL.
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';

try {
    if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
        responder(405, 'METODO_NO_PERMITIDO', 'Use GET.');
    }
    listarOrdenes();
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}

function listarOrdenes(): void
{
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT,
        ['options' => ['min_range' => 1]]);

    $sql = "SELECT o.id, o.codigo, o.proveedor, o.monto_autorizado, o.fecha_emision, o.estado,
                   (SELECT COUNT(h.id) FROM historial_proveedor h
                     WHERE h.proveedor = o.proveedor) AS historial_registros,
                   (SELECT AVG(h.monto) FROM historial_proveedor h
                     WHERE h.proveedor = o.proveedor) AS historial_promedio
            FROM ordenes_compra o";

    if ($id !== null && $id !== false) {
        $stmt = db()->prepare($sql . ' WHERE o.id = :id');
        $stmt->execute([':id' => $id]);
        $orden = $stmt->fetch();
        if (!$orden) {
            responder(404, 'NO_ENCONTRADO', "No existe la orden de compra con id=$id.");
        }
        responder(200, 'OK', 'Orden encontrada.', ['orden' => $orden]);
    }

    $estado = isset($_GET['estado']) ? (string)$_GET['estado'] : null;
    $params = [];
    if ($estado !== null && in_array($estado, ['abierta', 'cerrada'], true)) {
        $sql .= ' WHERE o.estado = :estado';
        $params[':estado'] = $estado;
    }
    $sql .= ' ORDER BY o.id';

    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    $ordenes = $stmt->fetchAll();

    responder(200, 'OK', 'Listado de órdenes de compra.', [
        'ordenes' => $ordenes,
        'total'   => count($ordenes),
    ]);
}
