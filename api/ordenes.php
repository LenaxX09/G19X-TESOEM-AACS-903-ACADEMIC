<?php
/**
 * ordenes.php — Órdenes de compra (Sprint 1: consulta · Sprint 5: CRUD, RF-B4).
 *
 *   GET  /api/ordenes.php            → listado de órdenes (para elegir orden_id).
 *   GET  /api/ordenes.php?id=1       → detalle + historial promedio del proveedor
 *                                       (los datos que consume clasificador_riesgo).
 *   POST /api/ordenes.php            → cuerpo JSON { "accion": "..." } (SOLO admin):
 *     · crear       { codigo, proveedor, monto_autorizado, fecha_emision }
 *                    → 201 · 409 CODIGO_DUPLICADO si el código ya existe.
 *     · actualizar  { id, codigo?, proveedor?, monto_autorizado?, fecha_emision?,
 *                     estado? ('abierta'|'cerrada') }
 *                    → 200. «Cerrar» una orden = su baja lógica: las facturas
 *                      existentes conservan su FK y su historial (no hay DELETE
 *                      físico porque rompería la integridad RF-D4).
 *
 * Acceso (RF-B6): GET → cualquier rol autenticado (la app lo necesita para el
 * spinner) · POST → solo admin.
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/autenticar.php';

try {
    requerirAutenticacion();

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        listarOrdenes();
    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        requerirRol(['admin']);
        accionOrden();
    } else {
        responder(405, 'METODO_NO_PERMITIDO', 'Use GET o POST.');
    }
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}


function listarOrdenes(): void
{
    // Sprint 7 (RF-F7): el proveedor SOLO ve las órdenes de su negocio.
    $yo      = usuarioActual();
    $esProv  = ($yo['rol'] ?? '') === 'proveedor';
    $negocio = trim((string)($yo['negocio'] ?? ''));

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
        if ($esProv) {
            // Comparación por SLUG: «Papeleria Central» == «Papelería Central».
            require_once __DIR__ . '/firebase.php';
            if (fb_slug((string)$orden['proveedor']) !== fb_slug($negocio)) {
                responder(403, 'ORDEN_DE_OTRO_PROVEEDOR',
                    "La orden $id pertenece a «{$orden['proveedor']}», no a tu negocio.");
            }
        }
        responder(200, 'OK', 'Orden encontrada.', ['orden' => $orden]);
    }

    $estado      = isset($_GET['estado']) ? (string)$_GET['estado'] : null;
    $condiciones = [];
    $params      = [];
    if ($estado !== null && in_array($estado, ['abierta', 'cerrada'], true)) {
        $condiciones[] = 'o.estado = :estado';
        $params[':estado'] = $estado;
    }
    if ($condiciones) {
        $sql .= ' WHERE ' . implode(' AND ', $condiciones);
    }
    $sql .= ' ORDER BY o.id';

    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    $ordenes = $stmt->fetchAll();

    // Sprint 7 (RF-F7): al proveedor se le filtra EN PHP por slug (tolera
    // acentos/mayúsculas: «Ferreteria» == «Ferretería»).
    if ($esProv) {
        require_once __DIR__ . '/firebase.php';
        $ordenes = array_values(array_filter($ordenes,
            function ($o) use ($negocio) {
                return fb_slug((string)$o['proveedor']) === fb_slug($negocio);
            }));
    }

    responder(200, 'OK', 'Listado de órdenes de compra.', [
        'ordenes' => $ordenes,
        'total'   => count($ordenes),
    ]);
}

// ============================================================================
// POST — crear / actualizar órdenes (Sprint 5, RF-B4 · solo admin)
// ============================================================================
function accionOrden(): void
{
    $entrada = json_decode(file_get_contents('php://input'), true);
    if (!is_array($entrada)) {
        responder(400, 'ERROR_VALIDACION', 'Cuerpo JSON inválido o vacío.');
    }
    $accion = (string)($entrada['accion'] ?? '');
    if ($accion === 'crear') {
        crearOrden($entrada);
    }
    if ($accion === 'actualizar') {
        actualizarOrden($entrada);
    }
    responder(400, 'ERROR_VALIDACION',
        "accion debe ser 'crear' o 'actualizar' (llegó: '$accion').");
}

function crearOrden(array $e): void
{
    $codigo  = trim((string)($e['codigo'] ?? ''));
    $monto   = filter_var($e['monto_autorizado'] ?? null, FILTER_VALIDATE_FLOAT);
    $fecha   = (string)($e['fecha_emision'] ?? '');
    $estado  = (string)($e['estado'] ?? 'abierta');

    if (!preg_match('/^[A-Za-z0-9-]{2,20}$/', $codigo)) {
        responder(400, 'ERROR_VALIDACION',
            'codigo es obligatorio (2-20 caracteres: letras, números o guion, p. ej. OC-004).');
    }
    $proveedor = trim((string)($e['proveedor'] ?? ''));
    if ($proveedor === '' || mb_strlen($proveedor) > 100) {
        responder(400, 'ERROR_VALIDACION', 'proveedor es obligatorio (máx. 100 caracteres).');
    }
    if ($monto === false || $monto <= 0) {
        responder(400, 'ERROR_VALIDACION', 'monto_autorizado debe ser un número mayor a 0.');
    }
    if (!esFechaValida($fecha)) {
        responder(400, 'ERROR_VALIDACION', 'fecha_emision debe tener formato YYYY-MM-DD válido.');
    }
    if (!in_array($estado, ['abierta', 'cerrada'], true)) {
        responder(400, 'ERROR_VALIDACION', "estado debe ser 'abierta' o 'cerrada'.");
    }

    $stmt = db()->prepare('SELECT id FROM ordenes_compra WHERE codigo = :c');
    $stmt->execute([':c' => $codigo]);
    if ($stmt->fetch()) {
        responder(409, 'CODIGO_DUPLICADO', "La orden '$codigo' ya existe.");
    }

    $stmt = db()->prepare(
        'INSERT INTO ordenes_compra (codigo, proveedor, monto_autorizado, fecha_emision, estado)
         VALUES (:codigo, :proveedor, :monto, :fecha, :estado)');
    $stmt->execute([':codigo' => strtoupper($codigo), ':proveedor' => $proveedor,
                    ':monto' => number_format($monto, 2, '.', ''),
                    ':fecha' => $fecha, ':estado' => $estado]);
    $id = (int)db()->lastInsertId();

    responder(201, 'OK', "Orden $codigo creada.", ['orden_id' => $id]);
}

function actualizarOrden(array $e): void
{
    $id = filter_var($e['id'] ?? null, FILTER_VALIDATE_INT,
        ['options' => ['min_range' => 1]]);
    if ($id === false || $id === null) {
        responder(400, 'ERROR_VALIDACION', 'id es obligatorio y debe ser entero positivo.');
    }

    $stmt = db()->prepare('SELECT id, codigo FROM ordenes_compra WHERE id = :id');
    $stmt->execute([':id' => $id]);
    if (!$stmt->fetch()) {
        responder(404, 'NO_ENCONTRADO', "No existe la orden de compra con id=$id.");
    }

    $sets   = [];
    $params = [':id' => $id];

    if (array_key_exists('codigo', $e)) {
        $codigo = trim((string)$e['codigo']);
        if (!preg_match('/^[A-Za-z0-9-]{2,20}$/', $codigo)) {
            responder(400, 'ERROR_VALIDACION', 'codigo inválido (2-20 letras/números/guion).');
        }
        $stmt = db()->prepare('SELECT id FROM ordenes_compra WHERE codigo = :c AND id <> :id');
        $stmt->execute([':c' => $codigo, ':id' => $id]);
        if ($stmt->fetch()) {
            responder(409, 'CODIGO_DUPLICADO', "Otra orden ya usa el código '$codigo'.");
        }
        $sets[] = 'codigo = :codigo';
        $params[':codigo'] = strtoupper($codigo);
    }
    if (array_key_exists('proveedor', $e)) {
        $proveedor = trim((string)$e['proveedor']);
        if ($proveedor === '' || mb_strlen($proveedor) > 100) {
            responder(400, 'ERROR_VALIDACION', 'proveedor no puede quedar vacío (máx. 100).');
        }
        $sets[] = 'proveedor = :proveedor';
        $params[':proveedor'] = $proveedor;
    }
    if (array_key_exists('monto_autorizado', $e)) {
        $monto = filter_var($e['monto_autorizado'], FILTER_VALIDATE_FLOAT);
        if ($monto === false || $monto <= 0) {
            responder(400, 'ERROR_VALIDACION', 'monto_autorizado debe ser un número mayor a 0.');
        }
        $sets[] = 'monto_autorizado = :monto';
        $params[':monto'] = number_format($monto, 2, '.', '');
    }
    if (array_key_exists('fecha_emision', $e)) {
        if (!esFechaValida((string)$e['fecha_emision'])) {
            responder(400, 'ERROR_VALIDACION', 'fecha_emision debe ser YYYY-MM-DD válida.');
        }
        $sets[] = 'fecha_emision = :fecha';
        $params[':fecha'] = (string)$e['fecha_emision'];
    }
    if (array_key_exists('estado', $e)) {
        if (!in_array((string)$e['estado'], ['abierta', 'cerrada'], true)) {
            responder(400, 'ERROR_VALIDACION', "estado debe ser 'abierta' o 'cerrada'.");
        }
        $sets[] = 'estado = :estado';
        $params[':estado'] = (string)$e['estado'];
    }
    if (!$sets) {
        responder(400, 'ERROR_VALIDACION',
            'Nada que actualizar: envía codigo, proveedor, monto_autorizado, fecha_emision o estado.');
    }

    db()->prepare('UPDATE ordenes_compra SET ' . implode(', ', $sets) . ' WHERE id = :id')
        ->execute($params);

    responder(200, 'OK', "Orden $id actualizada.");
}

/** Fecha YYYY-MM-DD real (checkdate), no solo una regex bonita. */
function esFechaValida(string $fecha): bool
{
    if (preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', $fecha, $m) !== 1) {
        return false;
    }
    return checkdate((int)$m[2], (int)$m[3], (int)$m[1]);
}
