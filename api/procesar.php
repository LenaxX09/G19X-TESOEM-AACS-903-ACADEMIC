<?php
/**
 * procesar.php — Sprint 4 "Integración y Demo" (RF-B3: orquestación).
 *
 *   GET/POST /api/procesar.php?id=5   → procesa ESA factura (debe estar Pendiente).
 *   GET/POST /api/procesar.php        → procesa TODAS las facturas Pendientes.
 *
 * Cadena orquestada (contratos fijados en MVP §6.4, ya verificados en C3):
 *   1) OCR (Python) .... "PYTHON_BIN" "RUTA_MOTOR_OCR" <imagen>    → JSON 1 línea
 *   2) Riesgo (C) ...... RUTA_BIN_RIESGO <monto> <promedio>        → "27.3"
 *   3) Conciliar (Prolog) "SWIPL_BIN" "RUTA_REGLA_PROLOG" <9 args> → APROBADA|DISCREPANCIA:<motivo>
 *   4) UPDATE facturas (..., procesado_en = NOW())                 → KPI-1 (≤ 30 s)
 *
 * SEGURIDAD (R3/RM4 → RNF-4 del PRD):
 *   · escapeshellarg() en TODOS los argumentos dinámicos.
 *   · Rutas de binarios/scripts (constantes de config.php, de confianza) entre
 *     comillas dobles porque contienen espacios ("Program Files").
 *   · Cast numérico de montos/riesgo/promedio y validación de fecha (regex)
 *     antes de armar la línea de comandos.
 *   · Citado verificado en vivo (30/09): PHP/popen arma bien la línea en
 *     Windows tal cual — NO envolver el comando completo en comillas extra.
 *   · Proveedores → ASCII (iconv TRANSLIT): cmd.exe interpreta la línea con
 *     la página de códigos ANSI y los bytes UTF-8 de "Ferretería" llegarían
 *     manguados a regla.pl. ASCII es idempotente para su normaliza().
 *
 * RNF-5: si un motor falla, NO se escribe nada en MySQL y la factura QUEDA
 * "Pendiente" (reintentable con esta misma URL).
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';

try {
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT,
        ['options' => ['min_range' => 1]]);

    if ($id !== null && $id !== false) {
        procesarUna($id);
    } else {
        procesarTodas();
    }
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}

// ============================================================================
// Caso 1: procesar UNA factura (?id=N)
// ============================================================================
function procesarUna(int $id): void
{
    $factura = cargarFactura($id);
    if (!$factura) {
        responder(404, 'NO_ENCONTRADO', "No existe la factura con id=$id.");
    }

    // Idempotente: si ya está procesada se devuelve su estado actual.
    if ($factura['estado'] !== 'Pendiente') {
        responder(200, 'YA_PROCESADA',
            "La factura $id ya fue procesada (estado: {$factura['estado']}).",
            ['factura' => $factura]);
    }

    $r = ejecutarPipeline($factura);
    if (!$r['ok']) {
        responder($r['http'], $r['codigo'], $r['mensaje'], $r['detalles']);
    }

    responder(200, 'OK', "Factura {$id}: {$r['datos']['veredicto']}.", $r['datos']);
}

// ============================================================================
// Caso 2: procesar TODAS las Pendientes (sin ?id=) — útil para la demo y para
// reintentar en bloque las que quedaron Pendientes por un fallo (RNF-5).
// ============================================================================
function procesarTodas(): void
{
    $stmt = db()->query(
        "SELECT f.id FROM facturas f WHERE f.estado = 'Pendiente' ORDER BY f.id");
    $ids = $stmt->fetchAll(PDO::FETCH_COLUMN);

    $resultados = [];
    $errores = [];
    foreach ($ids as $id) {
        $factura = cargarFactura((int)$id);
        $r = ejecutarPipeline($factura);
        if ($r['ok']) {
            $resultados[] = $r['datos'];
        } else {
            $errores[] = ['factura_id' => (int)$id,
                          'codigo' => $r['codigo'], 'mensaje' => $r['mensaje']];
        }
    }

    responder(200, 'OK',
        'Pendientes procesadas: ' . count($resultados)
        . ' · fallos: ' . count($errores) . '.',
        ['procesadas' => count($resultados), 'resultados' => $resultados,
         'errores' => $errores]);
}

// ============================================================================
// Factura + orden de compra + promedio del historial (todo en una consulta)
// ============================================================================
function cargarFactura(int $id): ?array
{
    $stmt = db()->prepare(
        'SELECT f.id, f.orden_id, f.estado, f.ruta_imagen, f.creado_en,
                o.codigo AS orden_codigo, o.proveedor AS orden_proveedor,
                o.monto_autorizado, o.fecha_emision,
                (SELECT COUNT(h.id) FROM historial_proveedor h
                  WHERE h.proveedor = o.proveedor) AS historial_registros,
                (SELECT AVG(h.monto) FROM historial_proveedor h
                  WHERE h.proveedor = o.proveedor) AS historial_promedio
         FROM facturas f
         JOIN ordenes_compra o ON o.id = f.orden_id
         WHERE f.id = :id');
    $stmt->execute([':id' => $id]);
    $fila = $stmt->fetch();
    return $fila === false ? null : $fila;
}

// ============================================================================
// EL PIPELINE: OCR → riesgo → conciliación → UPDATE (RF-B3)
// Devuelve ['ok'=>true,'datos'=>...] ó ['ok'=>false,'http','codigo','mensaje',
// 'detalles'] SIN tocar MySQL en los fallos (RNF-5).
// ============================================================================
function ejecutarPipeline(array $factura): array
{
    $t0 = microtime(true);
    $id = (int)$factura['id'];

    // --- 0) La imagen debe existir en disco ---------------------------------
    $raiz = dirname(__DIR__);                       // carpeta raíz del proyecto
    $rutaImagen = $raiz . '/' . str_replace('\\', '/', $factura['ruta_imagen']);
    if (!is_file($rutaImagen)) {
        return fallo(500, 'IMAGEN_NO_ENCONTRADA',
            'No se encontró la imagen de la factura en el servidor.',
            ['factura_id' => $id, 'ruta_esperada' => $rutaImagen]);
    }

    // --- 1) MOTOR 1 · OCR en Python (contrato §6.4) ------------------------
    $cmdOcr = '"' . PYTHON_BIN . '" "' . RUTA_MOTOR_OCR . '" '
            . escapeshellarg($rutaImagen);
    $salida = ejecutar($cmdOcr);
    $ocr = json_decode(ultimaLinea($salida), true);
    if (!is_array($ocr) || isset($ocr['error'])) {
        return falloMotor('OCR', $id, $salida, $ocr);
    }

    $proveedor = trim((string)($ocr['proveedor'] ?? ''));
    $fecha     = trim((string)($ocr['fecha'] ?? ''));
    $folio     = trim((string)($ocr['folio'] ?? ''));
    $monto     = isset($ocr['monto']) ? filter_var($ocr['monto'], FILTER_VALIDATE_FLOAT) : false;

    // Nulls del OCR → decisión de S4: sin monto/fecha/proveedor no se puede
    // conciliar; la factura queda «Pendiente» (RNF-5) para reintento/revisión.
    $faltantes = [];
    if ($proveedor === '' || mb_strlen($proveedor) < 4) $faltantes[] = 'proveedor';
    if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $fecha))   $faltantes[] = 'fecha';
    if ($monto === false || $monto <= 0)                 $faltantes[] = 'monto';
    if ($faltantes) {
        return fallo(422, 'OCR_INCOMPLETO',
            'El OCR no pudo leer: ' . implode(', ', $faltantes)
            . '. La factura queda «Pendiente» para reintento o captura manual.',
            ['factura_id' => $id, 'campos_faltantes' => $faltantes, 'ocr' => $ocr]);
    }

    // Recortes defensivos según las columnas de MySQL (VARCHAR 100/50)
    $proveedor = mb_substr($proveedor, 0, 100);
    $folio     = substr($folio, 0, 50);

    // --- 2) MOTOR 2 · riesgo en C (contrato §6.4) --------------------------
    $promedio = (float)($factura['historial_promedio'] ?? 0); // NULL → 0 → 50.0
    $cmdRiesgo = '"' . RUTA_BIN_RIESGO . '" '
        . escapeshellarg(sprintf('%.2F', $monto)) . ' '
        . escapeshellarg(sprintf('%.2F', $promedio));
    $salida = ejecutar($cmdRiesgo);
    $riesgo = filter_var(trim(ultimaLinea($salida)), FILTER_VALIDATE_FLOAT);
    if ($riesgo === false || $riesgo < 0 || $riesgo > 100) {
        return falloMotor('RIESGO', $id, $salida, null);
    }

    // --- 3) MOTOR 3 · conciliación en Prolog (contrato §6.4) ---------------
    //    swipl regla.pl <id_orden> <folio> <monto> <fecha_factura>
    //                    <proveedor_factura> <riesgo> <monto_autorizado>
    //                    <proveedor_orden> <fecha_orden>
    $args = [
        (string)(int)$factura['orden_id'],             // id_orden (rastreabilidad)
        $folio !== '' ? $folio : '-',                  // folio (no usa el veredicto)
        sprintf('%.2F', $monto),                       // monto facturado
        $fecha,                                        // fecha factura YYYY-MM-DD
        a_ascii($proveedor),                           // proveedor factura (ASCII)
        sprintf('%.1F', $riesgo),                      // % de riesgo
        sprintf('%.2F', (float)$factura['monto_autorizado']),
        a_ascii((string)$factura['orden_proveedor']),  // proveedor de la orden
        (string)$factura['fecha_emision'],             // fecha de la orden
    ];
    $cmdProlog = '"' . SWIPL_BIN . '" "' . RUTA_REGLA_PROLOG . '" '
        . implode(' ', array_map('escapeshellarg', $args));
    $salida = ejecutar($cmdProlog);
    $veredicto = trim(ultimaLinea($salida));

    if ($veredicto === 'APROBADA') {
        $estado = 'Aprobada';
        $motivo = null;
    } elseif (preg_match('/^DISCREPANCIA:([A-Z_]+)$/', $veredicto, $m)) {
        $estado = 'Con Discrepancia';
        $motivo = $m[1];
    } else {
        return falloMotor('CONCILIACION', $id, $salida, null);
    }

    // --- 4) Persistir el veredicto + KPI-1 ---------------------------------
    $stmt = db()->prepare(
        'UPDATE facturas
            SET proveedor_detectado = :proveedor,
                fecha_documento     = :fecha,
                monto_facturado     = :monto,
                folio               = :folio,
                riesgo              = :riesgo,
                estado              = :estado,
                motivo_discrepancia = :motivo,
                procesado_en        = NOW()
          WHERE id = :id');
    $stmt->execute([
        ':proveedor' => $proveedor,
        ':fecha'     => $fecha,
        ':monto'     => number_format($monto, 2, '.', ''),
        ':folio'     => $folio !== '' ? $folio : null,
        ':riesgo'    => number_format($riesgo, 2, '.', ''),
        ':estado'    => $estado,
        ':motivo'    => $motivo,
        ':id'        => $id,
    ]);

    // KPI-1 en el reloj de MySQL (PHP y MySQL pueden tener zonas horarias
    // distintas; con TIMESTAMPDIFF ambos tiempos salen del MISMO reloj).
    $stmt = db()->prepare(
        'SELECT TIMESTAMPDIFF(SECOND, creado_en, NOW()) FROM facturas WHERE id = :id');
    $stmt->execute([':id' => $id]);
    $desdeCreacion = (float)($stmt->fetchColumn() ?: 0);
    $duracion = round(microtime(true) - $t0, 2);

    return ['ok' => true, 'datos' => [
        'factura_id'              => $id,
        'estado'                  => $estado,
        'veredicto'               => $veredicto,
        'motivo_discrepancia'     => $motivo,
        'riesgo'                  => number_format($riesgo, 1, '.', ''),
        'monto'                   => number_format($monto, 2, '.', ''),
        'folio'                   => $folio !== '' ? $folio : null,
        'proveedor'               => $proveedor,
        'fecha_documento'         => $fecha,
        'orden'                   => ['id' => (int)$factura['orden_id'],
                                      'codigo' => $factura['orden_codigo'],
                                      'proveedor' => $factura['orden_proveedor']],
        'duracion_pipeline_s'     => $duracion,
        'segundos_desde_creacion' => $desdeCreacion,   // KPI-1: ≤ 30 s
        'kpi1_ok'                 => $desdeCreacion <= 30.0,
    ]];
}

// ============================================================================
// Utilidades de ejecución y saneo
// ============================================================================

/** Ejecuta un comando y devuelve su salida completa (args ya saneados arriba).
 *  (Verificado en vivo el 30/09: PHP/popen ya arma bien la línea citada en
 *  Windows; NO hay que envolver el comando en comillas extra — cmd lo rompe.) */
function ejecutar(string $cmd): string
{
    return (string)shell_exec($cmd . ' 2>&1');
}

/** Última línea no vacía de la salida de un motor (avisos previos no estorban). */
function ultimaLinea(string $salida): string
{
    $lineas = array_values(array_filter(array_map('trim', explode("\n", $salida)),
                                        fn($l) => $l !== ''));
    return $lineas === [] ? '' : $lineas[count($lineas) - 1];
}

/** Proveedores → ASCII puro para que cmd.exe no mangué los acentos (cabecera). */
function a_ascii(string $s): string
{
    $t = @iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $s);
    if ($t === false || trim($t) === '') {
        $t = strtr($s, ['á'=>'a','é'=>'e','í'=>'i','ó'=>'o','ú'=>'u','ü'=>'u','ñ'=>'n',
                        'Á'=>'A','É'=>'E','Í'=>'I','Ó'=>'O','Ú'=>'U','Ü'=>'U','Ñ'=>'N']);
        $t = preg_replace('/[^\x20-\x7E]/', '', $t) ?? $s;
    }
    return trim($t);
}

/** Fallo de un motor: la factura NO se toca (queda «Pendiente», RNF-5). */
function falloMotor(string $motor, int $id, string $salida, ?array $ocr): array
{
    return fallo(500, "MOTOR_{$motor}_FALLO",
        "El motor de {$motor} falló; la factura $id queda «Pendiente» (RNF-5) "
        . 'y puede reintentarse.',
        ['factura_id' => $id,
         'salida_motor' => mb_substr($salida, 0, 500),
         'ocr' => $ocr]);
}

function fallo(int $http, string $codigo, string $mensaje, array $detalles): array
{
    return ['ok' => false, 'http' => $http, 'codigo' => $codigo,
            'mensaje' => $mensaje, 'detalles' => $detalles];
}



