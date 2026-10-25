<?php
/**
 * habilitar_email_auth.php — Sprint 10 (S10), UNA SOLA VEZ por proyecto.
 *
 * Habilita el proveedor Email/Password en Firebase Auth (en S6 solo estaba
 * "Anonymous"). La app S10 entra con correo+contraseña directo a Firebase,
 * sin servidor. Usa la misma cuenta de servicio que firebase.php.
 *
 * NOTA (20/10): el endpoint v1 /config YA NO EXISTE (responde 404 HTML);
 * se usa la API v2 (PATCH .../v2/projects/{p}/config?updateMask=signIn.email),
 * verificada en vivo contra el proyecto cpp-facturas.
 *
 * USO: php habilitar_email_auth.php
 */

define('FIREBASE_SA', __DIR__ . '/firebase-service-account.json');
require __DIR__ . '/firebase.php';

$tok = fb_token();
$url = 'https://identitytoolkit.googleapis.com/v2/projects/' . fb_proyecto()
     . '/config?updateMask=signIn.email';
$cuerpo = json_encode(['signIn' => ['email' => ['enabled' => true, 'passwordRequired' => true]]]);

$ch = curl_init($url);
curl_setopt_array($ch, [
    CURLOPT_CUSTOMREQUEST      => 'PATCH',
    CURLOPT_RETURNTRANSFER     => true,
    CURLOPT_TIMEOUT            => 20,
    CURLOPT_HTTPHEADER         => ['Authorization: Bearer ' . $tok, 'Content-Type: application/json'],
    CURLOPT_POSTFIELDS         => $cuerpo,
]);
$resp  = curl_exec($ch);
$http  = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

$r  = json_decode((string)$resp, true);
$ok = is_array($r) && (($r['signIn']['email']['enabled'] ?? false) === true);
echo "HTTP $http\n"
    . ($ok ? 'signIn.email.enabled = ' . var_export($r['signIn']['email']['enabled'], true) . "\n"
             . 'passwordRequired  = ' . var_export($r['signIn']['email']['passwordRequired'] ?? null, true) . "\n"
           : substr((string)$resp, 0, 300) . "\n");
echo $ok ? "OK: Email/Password HABILITADO en " . fb_proyecto() . "\n"
         : "ERROR: revisa consola\n";
