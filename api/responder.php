<?php
/**
 * responder.php — RF-B5: respuestas JSON con código de resultado
 * interpretables por la app Android (contrato único de la API).
 *
 * Formato de salida SIEMPRE:
 * {
 *   "codigo":  "OK" | "ERROR_VALIDACION" | ... ,
 *   "mensaje": "texto legible para el usuario",
 *   "datos":   { ... } | [ ... ]
 * }
 */

function responder(int $httpCode, string $codigo, string $mensaje, array $datos = []): void
{
    http_response_code($httpCode);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        'codigo'  => $codigo,
        'mensaje' => $mensaje,
        'datos'   => $datos,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}
