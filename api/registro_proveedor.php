<?php
/**
 * registro_proveedor.php — Sprint 7 (RF-F5): auto-registro de proveedores.
 *
 *   POST /api/registro_proveedor.php
 *        { "usuario": "ferreteria87", "password": "******", "negocio": "Ferretería El Tornillo" }
 *        → 201 { token, expira_en, usuario: {id, nombre, usuario, rol, negocio} }
 *
 * PÚBLICO a propósito (decisión de seguridad documentada, como proveedores.php):
 * cualquiera puede crear una cuenta PROVEEDOR, que es el rol de MÍNIMOS —
 * solo ve y factura las órdenes cuyo proveedor calza con su negocio, jamás
 * lista usuarios ni crea órdenes. En producción se endurecería (verificar
 * correo o un código entregado por el comprador que registró la orden).
 *
 * Validaciones: usuario 3-50 [a-z0-9_.-] · password ≥ 6 · negocio 3-100.
 * 409 USUARIO_DUPLICADO si el login ya existe. Hash bcrypt (password_hash).
 * Al crear la sesión al instante, la app entra sin volver a loguearse.
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/autenticar.php';

try {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        responder(405, 'METODO_NO_PERMITIDO', 'Use POST.');
    }

    $entrada = json_decode(file_get_contents('php://input'), true);
    if (!is_array($entrada)) {
        responder(400, 'ERROR_VALIDACION', 'Cuerpo JSON inválido o vacío.');
    }

    $login    = strtolower(trim((string)($entrada['usuario'] ?? '')));
    $password = (string)($entrada['password'] ?? '');
    $negocio  = trim((string)($entrada['negocio'] ?? ''));

    if (!preg_match('/^[a-z0-9_.-]{3,50}$/', $login)) {
        responder(400, 'ERROR_VALIDACION',
            'usuario: 3-50 letras/números . _ - (minúsculas, sin acentos ni espacios).');
    }
    if (strlen($password) < 6) {
        responder(400, 'ERROR_VALIDACION', 'password debe tener al menos 6 caracteres.');
    }
    if ($negocio === '' || mb_strlen($negocio) < 3 || mb_strlen($negocio) > 100) {
        responder(400, 'ERROR_VALIDACION',
            'negocio es obligatorio (3-100): el nombre con el que el comprador '
            . 'registra tus órdenes de compra.');
    }

    $stmt = db()->prepare('SELECT id FROM usuarios WHERE usuario = :u');
    $stmt->execute([':u' => $login]);
    if ($stmt->fetch()) {
        responder(409, 'USUARIO_DUPLICADO', "El usuario '$login' ya existe.");
    }

    $stmt = db()->prepare(
        "INSERT INTO usuarios (nombre, usuario, password_hash, rol, negocio)
         VALUES (:nombre, :usuario, :hash, 'proveedor', :negocio)");
    $stmt->execute([':nombre' => $negocio, ':usuario' => $login,
                    ':hash' => password_hash($password, PASSWORD_DEFAULT),
                    ':negocio' => $negocio]);
    $id = (int)db()->lastInsertId();

    $sesion = crearSesion($id);

    responder(201, 'OK', 'Cuenta de proveedor creada. Sesión iniciada.', [
        'token'     => $sesion['token'],
        'expira_en' => $sesion['expira_en'],
        'usuario'   => ['id'      => $id,
                        'nombre'  => $negocio,
                        'usuario' => $login,
                        'rol'     => 'proveedor',
                        'negocio' => $negocio],
    ]);
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}
