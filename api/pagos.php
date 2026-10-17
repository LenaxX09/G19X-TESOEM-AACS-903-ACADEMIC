<?php
/**
 * pagos.php — Sprint 8 (RF-P1..P3): cuentas por pagar del CLIENTE.
 *
 * Modelo de negocio (aclarado por el usuario el 15-16/10): el CLIENTE
 * (capturista/admin/revisor de la empresa) sube las facturas que debe y
 * aquí ve SOLO SUS PROPIAS cuentas — lo que le falta por pagar y lo que
 * ya pagó —, no toda la actividad: cada cliente tiene múltiples cuentas
 * y atiende las suyas. Al PROVEEDOR le llegan los avisos push (S6/S7).
 *
 *   GET  /api/pagos.php   → { por_pagar, total_por_pagar, pagadas, total_pagadas }
 *        TODO filtrado por subida_por = quien consulta (lo propio).
 *        · por_pagar = SUS facturas con veredicto (Aprobada o Con
 *          Discrepancia resuelta) aún sin pagar; la «Pendiente» no entra.
 *        · pagadas   = las suyas ya pagadas (con pagada_en y ruta_imagen
 *                      para revisar la FOTO clasificada en la app).
 *
 *   POST /api/pagos.php   { "accion": "comprobar"|"despagar", "id": N }
 *        · comprobar → SUBE LA FOTO del comprobante de pago (imagen_base64,
 *          como las facturas) y con eso la factura queda PAGADA: sin
 *          evidencia no hay pago (RF-P2, S8.3). Solo facturas PROPIAS y
 *          con veredicto.
 *        · despagar → devuelve una pagada a «por pagar» (borra su
 *          comprobante vigente).
 *        Acceso (RF-B6): capturista/admin; revisor consulta.
 *        Acceso (RF-B6): capturista/admin — revisor y proveedor, 403.
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/autenticar.php';

try {
    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        requerirAutenticacion();
        listarPagos();
    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        requerirRol(['capturista', 'admin']);
        accionPago();
    } else {
        responder(405, 'METODO_NO_PERMITIDO', 'Use GET o POST.');
    }
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}

// ============================================================================
// GET — cuentas por pagar / pagadas
// ============================================================================
function listarPagos(): void
{
    // S8.2: cada cliente ve SOLO las facturas que ÉL subió (lo propio).
    $miId = (int)(usuarioActual()['id'] ?? 0);

    $sql = 'SELECT f.id, o.codigo AS orden_codigo, o.proveedor AS orden_proveedor,
                   f.proveedor_detectado, f.folio, f.monto_facturado, f.riesgo,
                   f.estado, f.motivo_discrepancia, f.ruta_imagen, f.creado_en,
                   f.pagada_en, f.subida_por, f.evidencia_pago
            FROM facturas f
            JOIN ordenes_compra o ON o.id = f.orden_id';

    $q1 = db()->prepare(
        $sql . " WHERE f.estado IN ('Aprobada', 'Con Discrepancia') AND f.pagada = 0
                   AND f.subida_por = :yo ORDER BY f.id DESC");
    $q1->execute([':yo' => $miId]);
    $porPagar = $q1->fetchAll();

    $q2 = db()->prepare(
        $sql . ' WHERE f.pagada = 1 AND f.subida_por = :yo
                   ORDER BY f.pagada_en DESC, f.id DESC');
    $q2->execute([':yo' => $miId]);
    $pagadas = $q2->fetchAll();

    responder(200, 'OK', 'Tus cuentas por pagar y pagadas.', [
        'por_pagar'      => $porPagar,
        'total_por_pagar' => count($porPagar),
        'pagadas'        => $pagadas,
        'total_pagadas'  => count($pagadas),
    ]);
}

// ============================================================================
// POST — pagar / despagar (RF-P2)
// ============================================================================
function accionPago(): void
{
    $entrada = json_decode(file_get_contents('php://input'), true);
    if (!is_array($entrada)) {
        responder(400, 'ERROR_VALIDACION', 'Cuerpo JSON inválido o vacío.');
    }
    $accion = (string)($entrada['accion'] ?? '');
    $id = filter_var($entrada['id'] ?? null, FILTER_VALIDATE_INT,
        ['options' => ['min_range' => 1]]);

    if (!in_array($accion, ['comprobar', 'despagar'], true)) {
        responder(400, 'ERROR_VALIDACION',
            "accion debe ser 'comprobar' (con imagen_base64) o 'despagar'.");
    }
    if ($id === false || $id === null) {
        responder(400, 'ERROR_VALIDACION', 'id es obligatorio (número de factura).');
    }

    $stmt = db()->prepare('SELECT id, estado, pagada, subida_por FROM facturas WHERE id = :id');
    $stmt->execute([':id' => $id]);
    $f = $stmt->fetch();
    if (!$f) {
        responder(404, 'NO_ENCONTRADO', "No existe la factura con id=$id.");
    }

    // S8.2: cada cliente solo paga/devuelve SUS propias cuentas.
    $miId = (int)(usuarioActual()['id'] ?? 0);
    if ((int)($f['subida_por'] ?? 0) !== $miId) {
        responder(403, 'FACTURA_DE_OTRO_CLIENTE',
            'Esa factura no está entre tus cuentas (la subió otro cliente).');
    }

    if ($accion === 'comprobar') {
        if ((int)$f['pagada'] === 1) {
            responder(409, 'YA_PAGADA', "La factura $id ya estaba marcada pagada.");
        }
        if ($f['estado'] === 'Pendiente') {
            responder(409, 'FACTURA_SIN_VEREDICTO',
                "La factura $id aún no tiene veredicto del pipeline (está «Pendiente»).");
        }

        // --- La evidencia del pago: misma validación de imagen que las
        //     facturas (firma de bytes, límite 8 MB; helpers locales porque
        //     facturas.php es un endpoint y no se puede require).
        $imagenBase64 = trim((string)($entrada['imagen_base64'] ?? ''));
        if ($imagenBase64 === '') {
            responder(400, 'ERROR_VALIDACION',
                'imagen_base64 es obligatoria: SIN la foto del comprobante no '
                . 'se puede pagar (S8.3).');
        }
        if (strpos($imagenBase64, 'data:') === 0) {
            $imagenBase64 = substr($imagenBase64, strpos($imagenBase64, ',') + 1);
        }
        if (strlen($imagenBase64) > 8 * 1024 * 1024 * 1.4) {
            responder(413, 'IMAGEN_MUY_GRANDE', 'La imagen excede el límite de 8 MB.');
        }
        $binario = base64_decode($imagenBase64, true);
        if ($binario === false) {
            responder(400, 'ERROR_VALIDACION', 'imagen_base64 no es Base64 válido.');
        }
        $tipo = null;
        if (substr($binario, 0, 3) === "\xFF\xD8\xFF") {
            $tipo = 'jpg';
        } elseif (substr($binario, 0, 8) === "\x89PNG\r\n\x1A\n") {
            $tipo = 'png';
        }
        if ($tipo === null) {
            responder(415, 'FORMATO_NO_SOPORTADO', 'La imagen debe ser JPEG o PNG.');
        }
        if (!is_dir(UPLOAD_DIR)) {
            mkdir(UPLOAD_DIR, 0775, true);
        }
        $nombre = 'evidencia_' . date('Ymd_His') . '_' . bin2hex(random_bytes(4)) . '.' . $tipo;
        if (file_put_contents(UPLOAD_DIR . DIRECTORY_SEPARATOR . $nombre, $binario) === false) {
            responder(500, 'ERROR_ARCHIVO', 'No se pudo guardar el comprobante.');
        }

        db()->prepare(
            "UPDATE facturas SET pagada = 1, pagada_en = NOW(),
                    evidencia_pago = :evid WHERE id = :id"
        )->execute([':evid' => 'uploads/' . $nombre, ':id' => $id]);

        responder(200, 'OK', "Factura $id PAGADA: comprobante recibido.");
    }

    // despagar: deshacer un pago (se equivocó o el proveedor lo devolvió);
    // su comprobante deja de contar.
    if ((int)$f['pagada'] !== 1) {
        responder(409, 'NO_ESTA_PAGADA', "La factura $id no está pagada.");
    }
    db()->prepare(
        'UPDATE facturas SET pagada = 0, pagada_en = NULL, evidencia_pago = NULL
          WHERE id = :id'
    )->execute([':id' => $id]);
    responder(200, 'OK', "Factura $id devuelta a «por pagar» (sin comprobante).");
}
