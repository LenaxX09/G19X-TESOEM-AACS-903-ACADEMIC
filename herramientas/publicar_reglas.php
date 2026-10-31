<?php
/**
 * publicar_reglas.php — publica firestore.rules (y storage.rules solo
 * si se pide) en el proyecto Firebase (cpp-facturas) SIN consola y SIN
 * Firebase CLI
 * (correcciones 24/10: «corrige los paréntesis… y publica las reglas»).
 *
 * Usa la MISMA API REST que la consola (Firebase Rules API) con la cuenta
 * de servicio que ya vive en api/ (Admin: NO pasa por las reglas).
 * Reutiliza firebase.php (fb_b64url/fb_proyecto/fb_configurado) y firma
 * un JWT AUTO-FIRMADO (flujo «self-signed JWT» de Google) con
 * aud = https://firebaserules.googleapis.com/ — probado 24/10: el flujo
 * clásico del token endpoint responde con un id_token inútil cuando el
 * scope firebase.rules va en la lista (los 3 scopes de fb_token() sí
 * siguen devolviendo access_token; la semilla NO está afectada).
 *
 * USO (CLI, sin Apache ni MySQL):
 *   php publicar_reglas.php                  # SOLO firestore.rules (omisión 31/10)
 *   php publicar_reglas.php solo-firestore   # ídem, explícito
 *   php publicar_reglas.php solo-storage     # solo storage.rules (cuando haya Blaze)
 *   php publicar_reglas.php ambas            # las dos (cuando Storage esté activo)
 *
 * DECISIÓN (29-31/10/2026): por OMISIÓN este script publica únicamente
 * firestore.rules. Storage exige el plan Blaze (tarjeta de crédito)
 * en proyectos creados después de Oct/2024 y no se cuenta con el
 * medio económico para contratarlo (proyecto académico): storage.rules
 * queda DISEÑADO y VERSIONADO pero sin publicar (INSTALACION.md §8.4).
 * Usa «solo-storage»/«ambas» el día que Storage se active.
 *
 * Por cada producto (firestore → release «cloud.firestore», storage →
 * release «firebase.storage»):
 *   1) muestra qué ruleset sirve AHORA (informativo / para volver atrás),
 *   2) crea un ruleset NUEVO con el contenido del archivo local (la API
 *      valida la sintaxis aquí: si el archivo está mal, FALLA y nada
 *      cambia en el proyecto),
 *   3) apunta el release al ruleset nuevo (PATCH; lo crea si no existe),
 *   4) re-lee el release y CONFIRMA el cambio.
 * Los rulesets viejos no se borran (quedan en el proyecto: sirven para
 * rollback repuntando el release; se limpian desde la consola).
 */

define('FIREBASE_SA', dirname(__DIR__) . '/api/firebase-service-account.json');
require dirname(__DIR__) . '/api/firebase.php';

/**
 * JWT AUTO-FIRMADO (self-signed JWT, sin llamar al token endpoint):
 * aud = el endpoint de la Rules API. Es la autenticación
 * servicio-a-servicio recomendada por Google para las APIs en la nube.
 */
function pr_token(): string
{
    static $tok = null;
    if ($tok !== null) {
        return $tok;
    }
    $sa = json_decode((string)@file_get_contents(FIREBASE_SA), true);
    if (!is_array($sa) || empty($sa['client_email']) || empty($sa['private_key'])) {
        fwrite(STDERR, 'Clave de servicio invalida: ' . FIREBASE_SA . "\n");
        exit(1);
    }
    $ahora    = time();
    $cabecera = fb_b64url(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
    $cuerpo   = fb_b64url(json_encode([
        'iss' => $sa['client_email'],
        'sub' => $sa['client_email'],
        'aud' => 'https://firebaserules.googleapis.com/',
        'iat' => $ahora,
        'exp' => $ahora + 3600,
    ]));
    $clave = openssl_pkey_get_private($sa['private_key']);
    if ($clave === false || !openssl_sign("$cabecera.$cuerpo", $firma, $clave, OPENSSL_ALGO_SHA256)) {
        fwrite(STDERR, "No se pudo firmar el JWT (openssl)\n");
        exit(1);
    }
    return $tok = $cabecera . '.' . $cuerpo . '.' . fb_b64url($firma);
}

/** Llamada REST genérica a la Rules API. Devuelve [http, datos]. */
function pr_http(string $metodo, string $url, array $payload = []): array
{
    $tok = pr_token();
    $ch = curl_init($url);
    $op = [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 20,
           CURLOPT_CUSTOMREQUEST  => $metodo,
           CURLOPT_HTTPHEADER     => ['Authorization: Bearer ' . $tok,
                                      'Content-Type: application/json']];
    if ($payload) {
        $op[CURLOPT_POSTFIELDS] = json_encode($payload, JSON_UNESCAPED_UNICODE);
    }
    curl_setopt_array($ch, $op);
    $resp = curl_exec($ch);
    $http = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    return [$http, json_decode((string)$resp, true)];
}

/** project_id leído de la clave de servicio (cacheado en static). */
function pr_email(): string
{
    static $e = null;
    if ($e === null) {
        $sa = json_decode((string)@file_get_contents(FIREBASE_SA), true);
        $e = is_array($sa) ? (string)($sa['client_email'] ?? '?') : '?';
    }
    return $e;
}

/** Publica un archivo de reglas en su release. true si quedó verificado. */
function pr_publicar(string $release, string $archivo): bool
{
    $base   = 'https://firebaserules.googleapis.com/v1/projects/' . fb_proyecto();
    $nombre = 'projects/' . fb_proyecto() . "/releases/$release";

    echo "== $release  <-  $archivo\n";
    $contenido = @file_get_contents(dirname(__DIR__) . '/' . $archivo);
    if (!is_string($contenido) || trim($contenido) === '') {
        echo "  [FAIL] no se pudo leer $archivo\n";
        return false;
    }

    // 1) ¿qué hay publicado ahora?
    [$h, $r] = pr_http('GET', "$base/releases/$release");
    if ($h === 200) {
        echo '  · ahora sirve ' . ($r['rulesetName'] ?? '?')
           . '  (creado ' . ($r['createTime'] ?? '?') . ")\n";
    } else {
        echo "  · sin release todavia (HTTP $h)\n";
    }

    // 2) ruleset nuevo (la API valida la sintaxis AQUI: si falla, no cambia nada)
    [$h, $r] = pr_http('POST', "$base/rulesets",
        ['source' => ['files' => [['name' => $release, 'content' => $contenido]]]]);
    if ($h !== 200 || empty($r['name'])) {
        echo '  [FAIL] crear ruleset: HTTP ' . $h . ' '
            . substr((string)json_encode($r), 0, 500) . "\n";
        return false;
    }
    $ruleset = $r['name'];
    echo "  + ruleset $ruleset\n";

    // 3) apuntar el release (PATCH; si no existe, crearlo)
    [$h, $r] = pr_http('PATCH', "$base/releases/$release",
        ['name' => $nombre, 'rulesetName' => $ruleset]);
    if ($h !== 200 && $h !== 201) {
        [$h, $r] = pr_http('POST', "$base/releases",
            ['name' => $nombre, 'rulesetName' => $ruleset]);
    }
    if ($h !== 200 && $h !== 201) {
        echo '  [FAIL] actualizar release: HTTP ' . $h . ' '
            . substr((string)json_encode($r), 0, 500) . "\n";
        if ($h === 403) {
            echo "  -- La cuenta de servicio SUBE rulesets pero no publica el\n"
               . "     release (IAM: le falta firebaserules.releases.update/create).\n"
               . "     El ruleset NUEVO ya quedo subido ($ruleset). Dos salidas:\n"
               . "     a) Consola GCP -> IAM -> agrega a:\n"
               . "          " . pr_email() . "\n"
               . "        el rol «Firebase Rules Admin» (roles/firebaserules.admin)\n"
               . "        y vuelve a correr este script (queda automatico para\n"
               . "        siempre), o\n"
               . "     b) pega a mano el archivo en Consola Firebase ->\n"
               . "        Firestore Database / Storage -> Reglas -> Publicar.\n";
        }
        return false;
    }

    // 4) verificar
    [$h, $r] = pr_http('GET', "$base/releases/$release");
    $ok = $h === 200 && (($r['rulesetName'] ?? '') === $ruleset);
    echo $ok ? "  [OK] $release ahora sirve $ruleset\n"
             : '  [FAIL] verificacion: ' . substr((string)json_encode($r), 0, 400) . "\n";
    return $ok;
}

// ── main ──────────────────────────────────────────────────────────────────
if (PHP_SAPI !== 'cli') {
    exit("Solo CLI.\n");
}
if (!fb_configurado()) {
    fwrite(STDERR, "Falta api/firebase-service-account.json (secreto, fuera de git).\n");
    exit(1);
}
echo 'Proyecto: ' . fb_proyecto() . "\n";
// Omisión 31/10: sin argumentos publica SOLO firestore.rules — Storage
// exige el plan Blaze (tarjeta) y no hay medio económico para contratarlo
// (decisión documentada: cabecera e INSTALACION.md §8.4).
$solo = (string)($argv[1] ?? 'solo-firestore');
$ok   = true;
if ($solo !== 'solo-storage') {
    $ok = pr_publicar('cloud.firestore', 'firestore.rules') && $ok;
}
if ($solo === 'solo-storage' || $solo === 'ambas') {
    $ok = pr_publicar('firebase.storage', 'storage.rules') && $ok;
}
exit($ok ? 0 : 1);
