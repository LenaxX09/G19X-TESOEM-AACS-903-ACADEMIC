<?php
/**
 * facturas.php — Endpoint principal de facturas (Sprint 1).
 *
 *   POST /api/facturas.php                      → RF-B1: recepción de imagen.
 *        Cuerpo JSON: { "orden_id": 1, "imagen_base64": "..." }
 *        Guarda la imagen en /uploads y registra la factura como "Pendiente".
 *
 *   GET  /api/facturas.php                      → RF-B2: listado (más recientes primero).
 *        Filtro opcional: ?estado=Pendiente|Aprobada|Con Discrepancia
 *
 *   GET  /api/facturas.php?id=5                 → RF-B2: detalle de una factura.
 *
 * La orquestación del pipeline (RF-B3: Python → C → Prolog) llega en Sprint 4.
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';

$metodo = $_SERVER['REQUEST_METHOD'];

try {
    if ($metodo === 'POST') {
        recibirFactura();
    } elseif ($metodo === 'GET') {
        listarFacturas();
    } else {
        responder(405, 'METODO_NO_PERMITIDO', 'Use GET o POST.');
    }
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}

// ============================================================================
// POST — RF-B1: recepción de imagen + registro de factura "Pendiente"
// ============================================================================
function recibirFactura(): void
{
    $entrada = json_decode(file_get_contents('php://input'), true);
    if (!is_array($entrada)) {
        responder(400, 'ERROR_VALIDACION', 'Cuerpo JSON inválido o vacío.');
    }

    // --- Validación estricta de parámetros (mitiga R3: inyección de comandos) ---
    $ordenId = filter_var($entrada['orden_id'] ?? null, FILTER_VALIDATE_INT,
        ['options' => ['min_range' => 1]]);
    if ($ordenId === false || $ordenId === null) {
        responder(400, 'ERROR_VALIDACION', 'orden_id es obligatorio y debe ser entero positivo.');
    }

    // La orden debe existir (integridad referencial RF-D4)
    $stmt = db()->prepare('SELECT id, codigo, proveedor, monto_autorizado
                           FROM ordenes_compra WHERE id = :id');
    $stmt->execute([':id' => $ordenId]);
    $orden = $stmt->fetch();
    if (!$orden) {
        responder(422, 'ORDEN_NO_ENCONTRADA', "No existe la orden de compra con id=$ordenId.");
    }

    $imagenBase64 = trim((string)($entrada['imagen_base64'] ?? ''));
    if ($imagenBase64 === '') {
        responder(400, 'ERROR_VALIDACION', 'imagen_base64 es obligatoria (RF-M4: envío en Base64).');
    }

    // Quitar prefijo data URI si viene: "data:image/jpeg;base64,...."
    if (strpos($imagenBase64, 'data:') === 0) {
        $imagenBase64 = substr($imagenBase64, strpos($imagenBase64, ',') + 1);
    }

    // Límite: ~8 MB decodificados (Base64 los infla ~33-40 %)
    if (strlen($imagenBase64) > 8 * 1024 * 1024 * 1.4) {
        responder(413, 'IMAGEN_MUY_GRANDE', 'La imagen excede el límite de 8 MB.');
    }

    $binario = base64_decode($imagenBase64, true);
    if ($binario === false) {
        responder(400, 'ERROR_VALIDACION', 'imagen_base64 no es Base64 válido.');
    }

    // Tipo real por firma de bytes (no se confía en lo que declara el cliente)
    $tipo = detectarTipoImagen($binario);
    if ($tipo === null) {
        responder(415, 'FORMATO_NO_SOPORTADO', 'La imagen debe ser JPEG o PNG.');
    }

    // --- Guardar archivo ---
    if (!is_dir(UPLOAD_DIR)) {
        mkdir(UPLOAD_DIR, 0775, true);
    }
    $nombreArchivo = 'factura_' . date('Ymd_His') . '_' . bin2hex(random_bytes(4)) . '.' . $tipo;
    if (file_put_contents(UPLOAD_DIR . DIRECTORY_SEPARATOR . $nombreArchivo, $binario) === false) {
        responder(500, 'ERROR_ARCHIVO', 'No se pudo guardar la imagen en el servidor.');
    }

    // --- Registrar factura en estado Pendiente (RF-B1) ---
    $stmt = db()->prepare("INSERT INTO facturas (orden_id, estado, ruta_imagen)
                           VALUES (:orden_id, 'Pendiente', :ruta)");
    $stmt->execute([':orden_id' => $ordenId, ':ruta' => 'uploads/' . $nombreArchivo]);
    $facturaId = (int)db()->lastInsertId();

    responder(201, 'OK', 'Factura registrada como Pendiente.', [
        'factura_id' => $facturaId,
        'estado'     => 'Pendiente',
        'orden'      => $orden,
        'ruta_imagen' => 'uploads/' . $nombreArchivo,
        'nota'       => 'El pipeline OCR → riesgo → conciliación (RF-B3) se activa en el Sprint 4.',
    ]);
}
// ============================================================================
// GET — RF-B2: listado y detalle de facturas
// ============================================================================
function listarFacturas(): void
{
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT,
        ['options' => ['min_range' => 1]]);

    // Detalle de una factura
    if ($id !== null && $id !== false) {
        $stmt = db()->prepare(
            'SELECT f.*, o.codigo AS orden_codigo, o.proveedor AS orden_proveedor,
                    o.monto_autorizado
             FROM facturas f
             JOIN ordenes_compra o ON o.id = f.orden_id
             WHERE f.id = :id');
        $stmt->execute([':id' => $id]);
        $factura = $stmt->fetch();
        if (!$factura) {
            responder(404, 'NO_ENCONTRADO', "No existe la factura con id=$id.");
        }
        responder(200, 'OK', 'Factura encontrada.', ['factura' => $factura]);
    }

    // Listado (con filtro opcional por estado)
    $estadosValidos = ['Pendiente', 'Aprobada', 'Con Discrepancia'];
    $estado = isset($_GET['estado']) ? (string)$_GET['estado'] : null;

    $sql = 'SELECT f.id, f.orden_id, o.codigo AS orden_codigo, o.proveedor AS orden_proveedor,
                   f.proveedor_detectado, f.folio, f.monto_facturado, f.riesgo, f.estado,
                   f.motivo_discrepancia, f.creado_en, f.procesado_en
            FROM facturas f
            JOIN ordenes_compra o ON o.id = f.orden_id';
    $params = [];
    if ($estado !== null) {
        if (!in_array($estado, $estadosValidos, true)) {
            responder(400, 'ERROR_VALIDACION',
                'estado debe ser uno de: ' . implode(', ', $estadosValidos) . '.');
        }
        $sql .= ' WHERE f.estado = :estado';
        $params[':estado'] = $estado;
    }
    $sql .= ' ORDER BY f.id DESC';

    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    $facturas = $stmt->fetchAll();

    responder(200, 'OK', 'Listado de facturas.', [
        'facturas' => $facturas,
        'total'    => count($facturas),
    ]);
}

// ============================================================================
// Utilidad: detecta el tipo de imagen por su firma de bytes
// ============================================================================
function detectarTipoImagen(string $binario): ?string
{
    if (substr($binario, 0, 3) === "\xFF\xD8\xFF") {
        return 'jpg';
    }
    if (substr($binario, 0, 8) === "\x89PNG\r\n\x1A\n") {
        return 'png';
    }
    return null;
}

