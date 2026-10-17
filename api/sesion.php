<?php
/**
 * sesion.php — Sprint 5 (RF-M1): inicio y cierre de sesión.
 *
 *   POST   /api/sesion.php  { "usuario": "admin", "password": "demo1234" }
 *          → 200 { token, expira_en, usuario: {id, nombre, usuario, rol} }
 *            · 401 CREDENCIALES_INVALIDAS  (usuario o contraseña mal)
 *            · 403 USUARIO_INACTIVO        (fue deshabilitado por un admin)
 *
 *   GET    /api/sesion.php  (con X-CPP-Token) → ¿quién soy? / valida el token.
 *   DELETE /api/sesion.php  (con X-CPP-Token) → logout (borra la fila en `sesiones`).
 *
 * CONTRASEÑAS — migración sin dolor (seed.sql dice: "v1.0 migrará a
 * password_hash() PHP"): se acepta AMBOS formatos mientras los usuarios
 * semilla siguen con SHA-256 hex (CHAR 64) y los creados por el CRUD de la
 * v1.0 ya nacen con bcrypt (password_hash, ~60 chars, VARCHAR 255).
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/autenticar.php';

try {
    $metodo = $_SERVER['REQUEST_METHOD'];

    if ($metodo === 'POST') {
        iniciarSesion();
    } elseif ($metodo === 'GET') {
        quienSoy();
    } elseif ($metodo === 'DELETE') {
        cerrar();
    } else {
        responder(405, 'METODO_NO_PERMITIDO', 'Use POST (login), GET (quién soy) o DELETE (logout).');
    }
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}

// ============================================================================
// POST — login (RF-M1)
// ============================================================================
function iniciarSesion(): void
{
    $entrada = json_decode(file_get_contents('php://input'), true);
    if (!is_array($entrada)) {
        responder(400, 'ERROR_VALIDACION', 'Cuerpo JSON inválido o vacío.');
    }

    $nombreUsuario = trim((string)($entrada['usuario'] ?? ''));
    $password      = (string)($entrada['password'] ?? '');
    if ($nombreUsuario === '' || $password === '') {
        responder(400, 'ERROR_VALIDACION', 'usuario y password son obligatorios.');
    }

    $stmt = db()->prepare(
        'SELECT id, nombre, usuario, password_hash, rol, negocio, activo
           FROM usuarios WHERE usuario = :usuario');
    $stmt->execute([':usuario' => $nombreUsuario]);
    $usuario = $stmt->fetch();

    // Mensaje genérico para no revelar SI el usuario existe (enumeración).
    if ($usuario === false || !verificarPassword($password, $usuario['password_hash'])) {
        responder(401, 'CREDENCIALES_INVALIDAS', 'Usuario o contraseña incorrectos.');
    }
    if ((int)$usuario['activo'] !== 1) {
        responder(403, 'USUARIO_INACTIVO',
            'El usuario está deshabilitado. Pide a un administrador que lo reactive.');
    }

    $sesion = crearSesion((int)$usuario['id']);

    responder(200, 'OK', 'Sesión iniciada. Envía el token en la cabecera X-CPP-Token.', [
        'token'     => $sesion['token'],
        'expira_en' => $sesion['expira_en'],
        'usuario'   => ['id'       => (int)$usuario['id'],
                        'nombre'   => $usuario['nombre'],
                        'usuario'  => $usuario['usuario'],
                        'rol'      => $usuario['rol'],
                        // Sprint 7 (RF-F5): el rol proveedor viaja con su negocio.
                        'negocio'  => $usuario['negocio'] ?? ''],
    ]);
}

/**
 * True si el password calza con el hash guardado. Soporta los DOS formatos:
 * bcrypt de password_hash() (usuarios creados por el CRUD v1.0) y el SHA-256
 * hex de demo del seed del MVP (hash('sha256') produce exactamente lo mismo
 * que SHA2(...,256) de MySQL, así que los usuarios semilla entran sin migrar).
 */
function verificarPassword(string $password, string $hash): bool
{
    if (preg_match('/^\$2y\$/', $hash) === 1) {          // bcrypt ($2y$ = PHP)
        return password_verify($password, $hash);
    }
    return hash_equals(strtolower($hash), hash('sha256', $password));
}

// ============================================================================
// GET — ¿quién soy? (la app valida al arrancar si su token sigue vivo)
// ============================================================================
function quienSoy(): void
{
    $usuario = requerirAutenticacion();
    responder(200, 'OK', 'Sesión vigente.', ['usuario' => $usuario]);
}

// ============================================================================
// DELETE — logout
// ============================================================================
function cerrar(): void
{
    if (!cerrarSesionDeLaPeticion()) {
        responder(401, 'NO_AUTENTICADO',
            'No había sesión válida que cerrar (token ausente, inválido o ya cerrado).');
    }
    responder(200, 'OK', 'Sesión cerrada.');
}
