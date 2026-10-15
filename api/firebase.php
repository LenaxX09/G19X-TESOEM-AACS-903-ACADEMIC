<?php
/**
 * firebase.php — Sprint 6 "Firebase" (RF-F4): notificaciones al proveedor.
 *
 * El PHP es quien AVISA cuando se sube una factura, por dos vías:
 *   1) FIRESTORE (REST): crea el documento en `notificaciones` → alimenta
 *      el oyente EN TIEMPO REAL de la app abierta (ProveedorActivity).
 *   2) FCM v1 (push): mensaje al tema proveedor_<slug> → notificación del
 *      sistema aunque la app esté cerrada (PushServicio la muestra).
 *
 * Autenticación con Google: cuenta de servicio (api/firebase-service-account.json,
 * SECRETO, fuera de git) → JWT firmado RS256 con openssl → access token
 * OAuth2 (cacheado ~50 min en temp) → llamadas Bearer. Cero dependencias
 * PHP: openssl + curl + json, que Laragon ya trae.
 *
 * TODO es best-effort: cualquier fallo se anota en error_log y la factura
 * NUNCA se ve afectada (ya quedó en MySQL antes de notificar).
 */

// ============================================================================
// Utilidades puras
// ============================================================================

/** ¿Está la clave de servicio? (sin ella solo fb_slug sigue siendo usable). */
function fb_configurado(): bool
{
    return defined('FIREBASE_SA') && is_file(FIREBASE_SA);
}

/**
 * Normaliza el nombre del proveedor → slug. ESPEJO EXACTO de
 * FirebaseServicio.slug() en la app: sin acentos, minúsculas, solo
 * letras/números. Con este slug coinciden el tema FCM y el documento:
 * "Ferretería El Tornillo" → "ferreteriaeltornillo".
 */
function fb_slug(string $proveedor): string
{
    $t = @iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $proveedor);
    if ($t === false || trim($t) === '') {
        $t = strtr($proveedor, ['á'=>'a','é'=>'e','í'=>'i','ó'=>'o','ú'=>'u','ü'=>'u','ñ'=>'n',
                                'Á'=>'A','É'=>'E','Í'=>'I','Ó'=>'O','Ú'=>'U','Ü'=>'U','Ñ'=>'N']);
        $t = preg_replace('/[^\x20-\x7E]/', '', $t) ?? $proveedor;
    }
    $t = strtolower(trim($t));
    return preg_replace('/[^a-z0-9]/', '', $t) ?? '';
}

/** base64 URL-safe (para el JWT). */
function fb_b64url(string $s): string
{
    return rtrim(strtr(base64_encode($s), '+/', '-_'), '=');
}

/** project_id leído de la clave de servicio (cacheado en static). */
function fb_proyecto(): string
{
    static $p = null;
    if ($p === null) {
        $sa = json_decode((string)@file_get_contents(FIREBASE_SA), true);
        $p = is_array($sa) ? (string)($sa['project_id'] ?? '') : '';
    }
    return $p;
}

// ============================================================================
// OAuth2: JWT RS256 (cuenta de servicio) → access token, cacheado
// ============================================================================

function fb_token(): ?string
{
    static $token = null;
    if ($token !== null) {
        return $token;
    }

    // Caché en disco: evita firmar+pedir token en cada factura (~2 s ahorrados)
    $cache = sys_get_temp_dir() . '/cpp_fb_token.json';
    if (is_file($cache)) {
        $c = json_decode((string)@file_get_contents($cache), true);
        if (is_array($c) && ($c['exp'] ?? 0) > time() + 60) {
            return $token = (string)$c['token'];
        }
    }

    $sa = json_decode((string)@file_get_contents(FIREBASE_SA), true);
    if (!is_array($sa) || empty($sa['client_email']) || empty($sa['private_key'])) {
        error_log('FB: clave de servicio inválida');
        return null;
    }

    $ahora    = time();
    $cabecera = fb_b64url(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
    $cuerpo   = fb_b64url(json_encode([
        'iss'   => $sa['client_email'],
        'scope' => 'https://www.googleapis.com/auth/firebase.messaging'
                 . ' https://www.googleapis.com/auth/datastore',
        'aud'   => $sa['token_uri'],
        'iat'   => $ahora,
        'exp'   => $ahora + 3600,
    ]));

    $clave = openssl_pkey_get_private($sa['private_key']);
    if ($clave === false || !openssl_sign("$cabecera.$cuerpo", $firma, $clave, OPENSSL_ALGO_SHA256)) {
        error_log('FB: no se pudo firmar el JWT');
        return null;
    }
    $jwt = $cabecera . '.' . $cuerpo . '.' . fb_b64url($firma);

    $ch = curl_init($sa['token_uri']);
    curl_setopt_array($ch, [
        CURLOPT_POST           => true,
        CURLOPT_POSTFIELDS     => http_build_query([
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion'  => $jwt,
        ]),
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 10,
    ]);
    $resp = curl_exec($ch);
    curl_close($ch);

    $j = is_string($resp) ? json_decode($resp, true) : null;
    if (!is_array($j) || empty($j['access_token'])) {
        error_log('FB: token rechazado → ' . substr((string)$resp, 0, 300));
        return null;
    }

    @file_put_contents($cache, json_encode([
        'token' => $j['access_token'],
        'exp'   => time() + (int)($j['expires_in'] ?? 3600),
    ]));
    return $token = (string)$j['access_token'];
}

// ============================================================================
// Llamadas a las APIs de Google (Bearer + JSON)
// ============================================================================

function fb_http(string $url, array $payload): ?array
{
    $tok = fb_token();
    if ($tok === null) {
        return null;
    }
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST           => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 10,
        CURLOPT_HTTPHEADER     => ['Authorization: Bearer ' . $tok,
                                   'Content-Type: application/json'],
        CURLOPT_POSTFIELDS     => json_encode($payload, JSON_UNESCAPED_UNICODE),
    ]);
    $resp = curl_exec($ch);
    $http = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($http >= 300) {
        error_log("FB: HTTP $http en $url → " . substr((string)$resp, 0, 300));
        return null;
    }
    return is_string($resp) ? json_decode($resp, true) : null;
}

/** Crea un documento en Firestore (REST). Retorna la respuesta o null. */
function fb_firestore_crear(string $coleccion, array $campos): ?array
{
    $fields = [];
    foreach ($campos as $k => $v) {
        if (is_int($v)) {
            $fields[$k] = ['integerValue' => (string)$v];   // REST exige string
        } elseif (is_float($v)) {
            $fields[$k] = ['doubleValue' => $v];
        } else {
            $fields[$k] = ['stringValue' => (string)$v];
        }
    }
    $fields['creado'] = ['timestampValue' => gmdate('Y-m-d\TH:i:s\Z')];

    return fb_http('https://firestore.googleapis.com/v1/projects/' . fb_proyecto()
        . '/databases/(default)/documents/' . rawurlencode($coleccion),
        ['fields' => $fields]);
}

/** Push FCM v1 a un TEMA (todos los teléfonos suscritos al proveedor). */
function fb_push_tema(string $tema, string $titulo, string $cuerpo): ?array
{
    return fb_http('https://fcm.googleapis.com/v1/projects/' . fb_proyecto()
        . '/messages:send', [
            'message' => [
                'topic'        => $tema,
                'notification' => ['title' => $titulo, 'body' => $cuerpo],
                'android'      => ['priority' => 'HIGH'],
            ],
        ]);
}

// ============================================================================
// RF-F4: la función que llama facturas.php al subir una factura
// ============================================================================

/**
 * Notifica al proveedor que su factura fue subida:
 *   1) escribe en Firestore `notificaciones` (oyente en tiempo real);
 *   2) envía push FCM al tema proveedor_<slug> (app cerrada).
 * NUNCA lanza excepción: la factura ya está guardada en MySQL.
 */
function fb_notificar_factura(int $facturaId, string $ordenCodigo,
                              string $proveedor, string $monto = ''): void
{
    try {
        if (!fb_configurado()) {
            error_log('FB: sin clave de servicio; factura sin notificación');
            return;
        }
        $slug = fb_slug($proveedor);

        fb_firestore_crear('notificaciones', [
            'factura_id' => $facturaId,
            'orden'      => $ordenCodigo,
            'proveedor'  => $proveedor,
            'slug'       => $slug,
            'monto'      => $monto,
            'estado'     => 'subida',
        ]);

        fb_push_tema('proveedor_' . $slug, 'Factura registrada',
            "Se subió la factura de $proveedor (orden $ordenCodigo, "
            . "factura Nº $facturaId) para conciliación.");
    } catch (Throwable $e) {
        error_log('FB notificar: ' . $e->getMessage());
    }
}


