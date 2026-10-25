<?php
/**
 * semilla_firebase.php — Sprint 10 «Sin servidor» (S10).
 *
 * USO (CLI, sin Apache y sin MySQL) — la clave demo NUNCA vive en el
 * código (correcciones 22/10): viene de la variable de entorno
 * CPP_DEMO_CLAVE o del primer parámetro:
 *   $env:CPP_DEMO_CLAVE='tu-clave' ; php semilla_firebase.php
 *   php semilla_firebase.php 'tu-clave'
 *
 * Prepara el proyecto Firebase para las pruebas de la app:
 *   1) AUTH: crea los usuarios demo (cliente / proveedor, con la clave
 *      que configuraste). Si ya existen, reusa su uid. Los roles viejos
 *      admin/capturista/revisor SE BORRAN (S10.3: unificados en cliente).
 *   2) FIRESTORE `usuarios/{uid}`: perfil de cada uno (rol, empresa) y,
 *      para el cliente, su entrada en el directorio público
 *      `clientesPorCorreo/{slug}` (las REGLAS no dejan listar `usuarios`
 *      y existeCorreo() consulta ese directorio).
 *   3) FIRESTORE `ordenes`: borra las cuentas demo anteriores y sube 3
 *      cuentas que el proveedor le "cobra" a cliente@cpp.demo
 *      (una vence en 3 días y una VENCIDA: para ver las alertas M7).
 *
 * Requiere api/firebase-service-account.json (secreto, fuera de git).
 * Reutiliza el JWT/OAuth de firebase.php: cero dependencias extra.
 */

define('FIREBASE_SA', __DIR__ . '/firebase-service-account.json');
require __DIR__ . '/firebase.php';

/**
 * Clave de las cuentas demo (correcciones 22/10): FUERA del código y del
 * repo — variable de entorno CPP_DEMO_CLAVE o parámetro CLI. Si falta o
 * es corta, se detiene explicando cómo pasarla (nada de clave por
 * defecto conocida por todos).
 */
$DEMO_CLAVE = (string)(getenv('CPP_DEMO_CLAVE') ?: ($argv[1] ?? ''));
if (strlen($DEMO_CLAVE) < 6) {
    fwrite(STDERR,
        "Falta la clave demo (no se escribe en el codigo ni en el repo).\n" .
        "  PowerShell : \$env:CPP_DEMO_CLAVE = 'tu-clave-demo'\n" .
        "  o parametro: php semilla_firebase.php 'tu-clave-demo'\n");
    exit(1);
}

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
function sf_uid(string $correo, string $nombre, string $clave): string
{
    $base = 'https://identitytoolkit.googleapis.com/v1/projects/' . fb_proyecto();
    $r = sf_http('POST', "$base/accounts",
        ['email' => $correo, 'password' => $clave,
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
        ['localId' => $uid, 'password' => $clave]);
    echo "  = Auth ya existía: $correo (clave reseteada a la demo)\n";
    return $uid;
}

/** PATCH upsert de un documento por RUTA completa (usuarios/{uid},
 *  clientesPorCorreo/{slug}, …) con campos string + creado. */
function sf_perfil(string $ruta, array $campos): void
{
    $fields = [];
    foreach ($campos as $k => $v) {
        $fields[$k] = ['stringValue' => (string)$v];
    }
    $fields['creado'] = ['timestampValue' => gmdate('Y-m-d\TH:i:s\Z')];
    sf_http('PATCH', 'https://firestore.googleapis.com/v1/projects/' . fb_proyecto()
        . '/databases/(default)/documents/' . $ruta, ['fields' => $fields]);
}

/**
 * S10.3: los roles admin/capturista/revisor SE UNIFICARON en «cliente».
 * Borra de Auth y Firestore los usuarios demo de esos roles viejos para
 * que el proyecto quede solo con cliente@ y proveedor@ (UN correo por
 * empresa cliente). Best-effort: si ya no existen, no imprime nada.
 */
function sf_limpiar_roles_viejos(): void
{
    $base = 'https://identitytoolkit.googleapis.com/v1/projects/' . fb_proyecto();
    foreach (['admin@cpp.demo', 'capturista@cpp.demo', 'revisor@cpp.demo'] as $correo) {
        $l = sf_http('POST', "$base/accounts:lookup", ['email' => [$correo]]);
        $uid = (string)($l['datos']['users'][0]['localId'] ?? '');
        if ($uid === '') {
            continue;
        }
        sf_http('POST', "$base/accounts:delete", ['localId' => $uid]);
        sf_http('DELETE', 'https://firestore.googleapis.com/v1/projects/' . fb_proyecto()
            . '/databases/(default)/documents/usuarios/' . $uid);
        echo "  - rol viejo borrado: $correo\n";
    }
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
// S10.3: roles unificados — SOLO cliente (la EMPRESA, UN correo) y proveedor.
$usuarios = [
    ['correo' => 'cliente@cpp.demo',   'nombre' => 'Cliente Demo',           'rol' => 'cliente',
     'empresa' => 'Bodega El Surtidor'],
    ['correo' => 'proveedor@cpp.demo', 'nombre' => 'Ferretería El Tornillo', 'rol' => 'proveedor',
     'negocio' => 'Ferretería El Tornillo'],
];
$uids = [];
foreach ($usuarios as $u) {
    $uid = sf_uid($u['correo'], $u['nombre'], $DEMO_CLAVE);
    $uids[$u['correo']] = $uid;
    $empresa = $u['empresa'] ?? $u['negocio'] ?? '';
    sf_perfil('usuarios/' . $uid,
        ['nombre' => $u['nombre'], 'correo' => $u['correo'],
         'rol'    => $u['rol'], 'negocio' => $empresa, 'empresa' => $empresa]);
    echo "  · perfil usuarios/$uid → rol {$u['rol']} ($empresa)\n";
    // REGLAS + existeCorreo(): los clientes también quedan en el directorio
    // público clientesPorCorreo/{slug} (usuarios ya no se puede listar).
    if ($u['rol'] === 'cliente') {
        sf_perfil('clientesPorCorreo/' . fb_slug($u['correo']),
            ['uid' => $uid, 'correo' => $u['correo']]);
        echo '  · directorio clientesPorCorreo/' . fb_slug($u['correo']) . "\n";
    }
}
sf_limpiar_roles_viejos();   // S10.3: los 3 roles viejos ya no existen

// ── 2) cuentas demo (ordenes) ──────────────────────────────────────────────
echo "2) Cuentas que el proveedor le sube a cliente@cpp.demo (la empresa)\n";
$cliente = 'cliente@cpp.demo';
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

echo "\nLISTO. Entra a la app con cliente@cpp.demo / la clave que configuraste\n"
   . "(CPP_DEMO_CLAVE) — o con proveedor@cpp.demo para subir cuentas.\n";
