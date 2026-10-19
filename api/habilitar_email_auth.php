<?php
/**
 * habilitar_email_auth.php — Sprint 10 (S10), UNA SOLA VEZ por proyecto.
 *
 * Habilita el proveedor Email/Password en Firebase Auth (en S6 solo estaba
 * "Anonymous"). La app S10 entra con correo+contraseña directo a Firebase,
 * sin servidor. Usa la misma cuenta de servicio que firebase.php.
 *
 * USO: php habilitar_email_auth.php
 */

define('FIREBASE_SA', __DIR__ . '/firebase-service-account.json');
require __DIR__ . '/firebase.php';

$tok = fb_token();
$url = 'https://identitytoolkit.googleapis.com/v1/projects/' . fb_proyecto()
     . '/config?updateMask=signIn.email';
$cuerpo = json_encode(['signIn' => ['email' => ['enabled' => true, 'passwordRequired' => true]]]);

$ch = curl_init($url);
curl_setopt_array($ch, [
    CURLOPT_POST           => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_TIMEOUT        => 20,
    CURLOPT_HTTPHEADER     => ['Authorization: Bearer ' . $tok, 'Content-Type: application/json'],
    CURLOPT_POSTFIELDS     => $cuerpo,
]);
$resp = curl_exec($ch);
$http  = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

echo "HTTP $http\n" . substr((string)$resp, 0, 900) . "\n";
$r = json_decode((string)$resp, true);
$ok = is_array($r) && ($r['signIn']['email']['enabled'] ?? false) === true;
echo $ok ? "OK: Email/Password HABILITADO en " . fb_proyecto() . "\n"
         : "ERROR: revisa consola\n";
