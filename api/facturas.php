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

// Sprint 5 (RF-B6): la consulta de facturas exige sesión iniciada. El envío
// (POST) está reservado a capturista/admin y, desde el Sprint 7 (RF-F6), al
// proveedor — pero SOLO contra órdenes de su propio negocio. Revisor: lectura.
require_once __DIR__ . '/autenticar.php';

$metodo = $_SERVER['REQUEST_METHOD'];

try {
    if ($metodo === 'POST') {
        requerirRol(['capturista', 'admin', 'proveedor']);
        recibirFactura();
    } elseif ($metodo === 'GET') {
        requerirAutenticacion();
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

    // Sprint 7 (RF-F6): un proveedor solo puede facturar contra órdenes de SU
    // negocio. Se compara por SLUG (fb_slug: sin acentos/mayúsculas/espacios)
    // para que «Papeleria Central» == «Papelería Central»: hallazgo de la
    // prueba del 15/10 — el texto exacto rechinaba con las tildes.
    $yo = usuarioActual();
    if (($yo['rol'] ?? '') === 'proveedor') {
        require_once __DIR__ . '/firebase.php';          // solo por fb_slug()
        if (fb_slug($orden['proveedor'])
                !== fb_slug(trim((string)($yo['negocio'] ?? '')))) {
            responder(403, 'ORDEN_DE_OTRO_PROVEEDOR',
                "La orden {$orden['codigo']} pertenece a «{$orden['proveedor']}», "
                . 'no a tu negocio.');
        }
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
    $stmt = db()->prepare("INSERT INTO facturas (orden_id, estado, ruta_imagen, subida_por)
                          VALUES (:orden_id, 'Pendiente', :ruta, :subida)");
    $stmt->execute([':orden_id' => $ordenId, ':ruta' => 'uploads/' . $nombreArchivo,
                    ':subida' => (int)($yo['id'] ?? 0)]);
    $facturaId = (int)db()->lastInsertId();

    // Sprint 6 (RF-F4): notificar al proveedor (Firestore en tiempo real +
    // push FCM). Best-effort: si Firebase falla, la factura YA está guardada.
    require_once __DIR__ . '/firebase.php';
    fb_notificar_factura($facturaId, $orden['codigo'], $orden['proveedor']);

    responder(201, 'OK', 'Factura registrada como Pendiente.', [
        'factura_id' => $facturaId,
        'estado'     => 'Pendiente',
        'orden'      => $orden,
        'ruta_imagen' => 'uploads/' . $nombreArchivo,
        'nota'       => 'Pipeline listo (Sprint 4): llamar a /api/procesar.php?id='
                      . $facturaId . ' para ejecutar OCR → riesgo → conciliación (RF-B3).',
    ]);
}
// ============================================================================
// GET — RF-B2: listado y detalle de facturas
// ============================================================================
function listarFacturas(): void
{
    // Sprint 7 (RF-F7): el proveedor solo ve las facturas de SU negocio.
    $yo      = usuarioActual();
    $esProv  = ($yo['rol'] ?? '') === 'proveedor';
    $negocio = trim((string)($yo['negocio'] ?? ''));

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
        if ($esProv) {
            require_once __DIR__ . '/firebase.php';
            if (fb_slug((string)$factura['orden_proveedor']) !== fb_slug($negocio)) {
                responder(403, 'FACTURA_DE_OTRO_PROVEEDOR',
                    'Esa factura pertenece a otro proveedor.');
            }
        }
        responder(200, 'OK', 'Factura encontrada.', ['factura' => $factura]);
    }

    // Listado (con filtro opcional por estado)
    $estadosValidos = ['Pendiente', 'Aprobada', 'Con Discrepancia'];
    $estado = isset($_GET['estado']) ? (string)$_GET['estado'] : null;

    $sql = 'SELECT f.id, f.orden_id, o.codigo AS orden_codigo, o.proveedor AS orden_proveedor,
                   f.proveedor_detectado, f.folio, f.monto_facturado, f.riesgo, f.estado,
                   f.motivo_discrepancia, f.creado_en, f.procesado_en,
                   f.pagada, f.pagada_en, f.ruta_imagen, f.subida_por
            FROM facturas f
            JOIN ordenes_compra o ON o.id = f.orden_id';
    $condiciones = [];
    $params = [];
    if ($esProv) {
        // (el filtro por negocio se aplica en PHP, tras el fetch)
    }
    if ($estado !== null) {
        if (!in_array($estado, $estadosValidos, true)) {
            responder(400, 'ERROR_VALIDACION',
                'estado debe ser uno de: ' . implode(', ', $estadosValidos) . '.');
        }
        $condiciones[] = 'f.estado = :estado';
        $params[':estado'] = $estado;
    }
    // Sprint 8 (RF-P1): filtro de pago ?pagada=0|1
    if (isset($_GET['pagada'])) {
        $condiciones[] = 'f.pagada = :pagada';
        $params[':pagada'] = ((int)$_GET['pagada'] === 1) ? 1 : 0;
    }
    if ($condiciones) {
        $sql .= ' WHERE ' . implode(' AND ', $condiciones);
    }
    $sql .= ' ORDER BY f.id DESC';

    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    $facturas = $stmt->fetchAll();

    // Sprint 7 (RF-F7): al proveedor se le filtra EN PHP por slug (tolera
    // acentos: «Papeleria Central» == «Papelería Central»).
    // S8.2: capturista y revisor ven SOLO las facturas que ELLOS subieron
    // (sus propias cuentas); el admin sigue viendo todo (gestiona el sistema).
    if ($esProv) {
        require_once __DIR__ . '/firebase.php';
        $facturas = array_values(array_filter($facturas,
            function ($f) use ($negocio) {
                return fb_slug((string)$f['orden_proveedor']) === fb_slug($negocio);
            }));
    } elseif (($yo['rol'] ?? '') !== 'admin') {
        $miId = (int)($yo['id'] ?? 0);
        $facturas = array_values(array_filter($facturas,
            function ($f) use ($miId) {
                return (int)$f['subida_por'] === $miId;
            }));
    }

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

