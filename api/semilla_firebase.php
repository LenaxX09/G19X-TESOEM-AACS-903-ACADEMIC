<?php
/**
 * semilla_firebase.php — Sprint 10 «Sin servidor» (S10).
 *
 * USO (CLI, sin Apache y sin MySQL):
 *   php semilla_firebase.php
 *
 * Prepara el proyecto Firebase para las pruebas de la app:
 *   1) AUTH: crea los usuarios demo (admin / capturista / revisor /
 *      proveedor, clave cppdemo123). Si ya existen, reusa su uid.
 *   2) FIRESTORE `usuarios/{uid}`: perfil de cada uno (rol, negocio).
 *   3) FIRESTORE `ordenes`: borra las cuentas demo anteriores y sube 3
 *      cuentas que el proveedor le "cobra" a capturista@cpp.demo
 *      (una vence en 3 días y una VENCIDA: para ver las alertas M7).
 *
 * Requiere api/firebase-service-account.json (secreto, fuera de git).
 * Reutiliza el JWT/OAuth de firebase.php: cero dependencias extra.
 */

define('FIREBASE_SA', __DIR__ . '/firebase-service-account.json');
require __DIR__ . '/firebase.php';

const DEMO_CLAVE = 'cppdemo123';

/** Llamada REST genérica con Bearer (POST/PATCH/DELETE). */
function sf_http(string $metodo, string $url, array $payload = []): array
{
    $tok = fb_token();
    if ($tok === null) {
        fwrite(STDERR, "SIN TOKEN: revisa api/firebase-service-account.json\n");
        exit(1);
    }
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
    $datos = json_decode((string)$resp, true);
    return ['http' => $http, 'datos' => is_array($datos) ? $datos : []];
}

/** Crea el usuario en Auth si falta (o reusa el uid y resetea la clave demo). */
function sf_uid(string $correo, string $nombre): string
{
    $base = 'https://identitytoolkit.googleapis.com/v1/projects/' . fb_proyecto();
    $r = sf_http('POST', "$base/accounts",
        ['email' => $correo, 'password' => DEMO_CLAVE,
         'displayName' => $nombre, 'emailVerified' => true]);
    if (!empty($r['datos']['localId'])) {
        echo "  + Auth creado: $correo\n";
        return $r['datos']['localId'];
    }
    $l = sf_http('POST', "$base/accounts:lookup", ['email' => [$correo]]);
    $uid = (string)($l['datos']['users'][0]['localId'] ?? '');
    if ($uid === '') {
        fwrite(STDERR, "  ! No se pudo crear ni hallar $correo → HTTP {$r['http']}: "
            . substr(json_encode($r['datos'], JSON_UNESCAPED_UNICODE), 0, 300) . "\n");
        exit(1);
    }
    sf_http('POST', "$base/accounts:update",
        ['localId' => $uid, 'password' => DEMO_CLAVE]);
    echo "  = Auth ya existía: $correo (clave reseteada a la demo)\n";
    return $uid;
}

/** PATCH upsert del documento con id fijo (usuarios/{uid}). */
function sf_perfil(string $uid, array $campos): void
{
    $fields = [];
    foreach ($campos as $k => $v) {
        $fields[$k] = ['stringValue' => (string)$v];
    }
    $fields['creado'] = ['timestampValue' => gmdate('Y-m-d\TH:i:s\Z')];
    sf_http('PATCH', 'https://firestore.googleapis.com/v1/projects/' . fb_proyecto()
        . '/databases/(default)/documents/usuarios/' . $uid, ['fields' => $fields]);
}

/** Borra las cuentas demo anteriores de `ordenes` (re-ejecutable). */
function sf_limpiar_ordenes(string $cliente): void
{
    $r = sf_http('POST', 'https://firestore.googleapis.com/v1/projects/' . fb_proyecto()
        . '/databases/(default)/documents:runQuery',
        ['structuredQuery' => [
            'from'   => [['collectionId' => 'ordenes']],
            'select' => ['fields' => [['fieldPath' => '__name__']]],
            'where'  => ['fieldFilter' => [
                'field' => ['fieldPath' => 'clienteCorreo'],
                'op'    => 'EQUAL',
                'value' => ['stringValue' => $cliente]]],
        ]]);
    $n = 0;
    foreach (($r['datos'] ?? []) as $doc) {
        $name = (string)($doc['document']['name'] ?? '');
        if ($name !== '') {
            sf_http('DELETE', 'https://firestore.googleapis.com/v1/' . $name);
            $n++;
        }
    }
    if ($n) {
        echo "  - $n cuenta(s) demo anterior(es) borrada(s)\n";
    }
}

// ── 1) usuarios + perfiles ─────────────────────────────────────────────────
echo "Proyecto: " . fb_proyecto() . "\n1) Usuarios Auth + perfiles Firestore\n";
$usuarios = [
    ['correo' => 'admin@cpp.demo',       'nombre' => 'Administrador Demo',      'rol' => 'admin'],
    ['correo' => 'capturista@cpp.demo',  'nombre' => 'Capturista Demo',         'rol' => 'capturista'],
    ['correo' => 'revisor@cpp.demo',     'nombre' => 'Revisor Demo',            'rol' => 'revisor'],
    ['correo' => 'proveedor@cpp.demo',   'nombre' => 'Ferretería El Tornillo',  'rol' => 'proveedor',
     'negocio' => 'Ferretería El Tornillo'],
];
$uids = [];
foreach ($usuarios as $u) {
    $uid = sf_uid($u['correo'], $u['nombre']);
    $uids[$u['correo']] = $uid;
    sf_perfil($uid, ['nombre' => $u['nombre'], 'correo' => $u['correo'],
                     'rol'    => $u['rol'], 'negocio' => $u['negocio'] ?? '']);
    echo "  · perfil usuarios/$uid → rol {$u['rol']}\n";
}

// ── 2) cuentas demo (ordenes) ──────────────────────────────────────────────
echo "2) Cuentas que el proveedor le sube a capturista@cpp.demo\n";
$cliente = 'capturista@cpp.demo';
sf_limpiar_ordenes($cliente);
$hoy = time();
$cuentas = [
    ['codigo' => 'OC-101', 'monto' => 1250.50, 'dias' => 10],
    ['codigo' => 'OC-102', 'monto' =>  890.00, 'dias' =>  3],   // ⏰ vence pronto (M7)
    ['codigo' => 'OC-103', 'monto' => 2300.00, 'dias' => -2],   // 🚨 VENCIDA (M7)
];
foreach ($cuentas as $c) {
    fb_firestore_crear('ordenes', [
        'codigo'        => $c['codigo'],
        'negocio'       => 'Ferretería El Tornillo',
        'proveedorUid'  => $uids['proveedor@cpp.demo'],
        'clienteCorreo' => $cliente,
        'monto'         => $c['monto'],          // double → doubleValue
        'fechaEmision'  => gmdate('Y-m-d', $hoy),
        'fechaLimite'   => gmdate('Y-m-d', $hoy + $c['dias'] * 86400),
        'estado'        => 'pendiente',
    ]);
    echo '  + ' . $c['codigo'] . '  $' . number_format($c['monto'], 2)
       . '  límite ' . gmdate('Y-m-d', $hoy + $c['dias'] * 86400) . "\n";
}

echo "\nLISTO. Entra a la app con capturista@cpp.demo / " . DEMO_CLAVE
   . " (o proveedor@cpp.demo para subir cuentas).\n";
