<?php
/**
 * usuarios.php — Sprint 5 (RF-B4 completo): CRUD de usuarios. SOLO admin.
 *
 *   GET  /api/usuarios.php            → listado (NUNCA incluye password_hash).
 *   GET  /api/usuarios.php?id=3       → detalle de un usuario.
 *
 *   POST /api/usuarios.php            → cuerpo JSON { "accion": "..." }:
 *     · crear       { nombre, usuario, password, rol }
 *                    → 201. El password se guarda con password_hash() (bcrypt).
 *                      409 USUARIO_DUPLICADO si el usuario ya existe.
 *     · actualizar  { id, nombre?, rol?, activo?, password? }
 *                    → 200. password vacío/ausente = no cambiarla.
 *                      409 ULTIMO_ADMIN si se quitara el único admin activo.
 *
 * La "baja" es LÓGICA (activo=0): el historial sigue referenciando al
 * usuario y no se pierde trazabilidad. No hay DELETE físico.
 *
 * Acceso (RF-B6): GET y POST → solo admin. Cualquier otro rol recibe 403.
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/autenticar.php';

try {
    requerirRol(['admin']);

    $metodo = $_SERVER['REQUEST_METHOD'];
    if ($metodo === 'GET') {
        listarUsuarios();
    } elseif ($metodo === 'POST') {
        accionUsuario();
    } else {
        responder(405, 'METODO_NO_PERMITIDO', 'Use GET o POST.');
    }
} catch (PDOException $e) {
    responder(500, 'ERROR_DB', 'Error de base de datos: ' . $e->getMessage());
}

// ============================================================================
// GET — listado / detalle (sin hashes)
// ============================================================================
function listarUsuarios(): void
{
    $id = filter_var($_GET['id'] ?? null, FILTER_VALIDATE_INT,
        ['options' => ['min_range' => 1]]);

    $sql = 'SELECT id, nombre, usuario, rol, negocio, activo,
                   DATE_FORMAT(creado_en, "%Y-%m-%d %H:%i") AS creado_en
            FROM usuarios';
    if ($id !== null && $id !== false) {
        $stmt = db()->prepare($sql . ' WHERE id = :id');
        $stmt->execute([':id' => $id]);
        $usuario = $stmt->fetch();
        if (!$usuario) {
            responder(404, 'NO_ENCONTRADO', "No existe el usuario con id=$id.");
        }
        responder(200, 'OK', 'Usuario encontrado.', ['usuario' => $usuario]);
    }

    $stmt = db()->query($sql . ' ORDER BY id');
    $usuarios = $stmt->fetchAll();

    responder(200, 'OK', 'Listado de usuarios.', [
        'usuarios' => $usuarios,
        'total'    => count($usuarios),
    ]);
}

// ============================================================================
// POST — crear / actualizar (RF-B4)
// ============================================================================
function accionUsuario(): void
{
    $entrada = json_decode(file_get_contents('php://input'), true);
    if (!is_array($entrada)) {
        responder(400, 'ERROR_VALIDACION', 'Cuerpo JSON inválido o vacío.');
    }
    $accion = (string)($entrada['accion'] ?? '');
    if ($accion === 'crear') {
        crearUsuario($entrada);
    }
    if ($accion === 'actualizar') {
        actualizarUsuario($entrada);
    }
    responder(400, 'ERROR_VALIDACION',
        "accion debe ser 'crear' o 'actualizar' (llegó: '$accion').");
}

function crearUsuario(array $e): void
{
    $nombre   = trim((string)($e['nombre'] ?? ''));
    $login    = trim((string)($e['usuario'] ?? ''));
    $password = (string)($e['password'] ?? '');
    $rol      = (string)($e['rol'] ?? '');

    if ($nombre === '' || mb_strlen($nombre) > 100) {
        responder(400, 'ERROR_VALIDACION', 'nombre es obligatorio (máx. 100 caracteres).');
    }
    if (!preg_match('/^[a-zA-Z0-9._-]{3,50}$/', $login)) {
        responder(400, 'ERROR_VALIDACION',
            'usuario debe tener 3-50 caracteres: letras, números, punto, guion o guion bajo.');
    }
    if (strlen($password) < 6) {
        responder(400, 'ERROR_VALIDACION', 'password debe tener al menos 6 caracteres.');
    }
    validarRol($rol);

    $stmt = db()->prepare('SELECT id FROM usuarios WHERE usuario = :u');
    $stmt->execute([':u' => $login]);
    if ($stmt->fetch()) {
        responder(409, 'USUARIO_DUPLICADO', "El usuario '$login' ya existe.");
    }

    // Sprint 7 (RF-F5): el rol proveedor REQUIERE su negocio; los demás, NULL.
    $negocio = null;
    if ($rol === 'proveedor') {
        $negocio = trim((string)($e['negocio'] ?? ''));
        if ($negocio === '' || mb_strlen($negocio) > 100) {
            responder(400, 'ERROR_VALIDACION',
                'Un usuario proveedor necesita su negocio (3-100 caracteres).');
        }
    }

    $stmt = db()->prepare(
        'INSERT INTO usuarios (nombre, usuario, password_hash, rol, negocio)
         VALUES (:nombre, :usuario, :hash, :rol, :negocio)');
    $stmt->execute([':nombre' => $nombre, ':usuario' => $login,
                    ':hash' => password_hash($password, PASSWORD_DEFAULT),
                    ':rol' => $rol, ':negocio' => $negocio]);
    $id = (int)db()->lastInsertId();

    responder(201, 'OK', "Usuario '$login' creado con rol $rol.", ['usuario_id' => $id]);
}

function actualizarUsuario(array $e): void
{
    $id = filter_var($e['id'] ?? null, FILTER_VALIDATE_INT,
        ['options' => ['min_range' => 1]]);
    if ($id === false || $id === null) {
        responder(400, 'ERROR_VALIDACION', 'id es obligatorio y debe ser entero positivo.');
    }

    $stmt = db()->prepare('SELECT id, rol, activo FROM usuarios WHERE id = :id');
    $stmt->execute([':id' => $id]);
    $actual = $stmt->fetch();
    if (!$actual) {
        responder(404, 'NO_ENCONTRADO', "No existe el usuario con id=$id.");
    }

    // --- Campos opcionales: solo se actualizan los que llegan ---------------
    $sets   = [];
    $params = [':id' => $id];

    if (array_key_exists('nombre', $e)) {
        $nombre = trim((string)$e['nombre']);
        if ($nombre === '' || mb_strlen($nombre) > 100) {
            responder(400, 'ERROR_VALIDACION', 'nombre no puede quedar vacío (máx. 100).');
        }
        $sets[] = 'nombre = :nombre';
        $params[':nombre'] = $nombre;
    }
    if (array_key_exists('rol', $e)) {
        validarRol((string)$e['rol']);
        $sets[] = 'rol = :rol';
        $params[':rol'] = (string)$e['rol'];
    }
    if (array_key_exists('negocio', $e)) {                     // Sprint 7 (RF-F5)
        $neg = trim((string)$e['negocio']);
        if ($neg !== '' && mb_strlen($neg) > 100) {
            responder(400, 'ERROR_VALIDACION', 'negocio debe tener hasta 100 caracteres.');
        }
        $sets[] = 'negocio = :negocio';
        $params[':negocio'] = ($neg === '') ? null : $neg;
    }
    if (array_key_exists('activo', $e)) {
        $sets[] = 'activo = :activo';
        $params[':activo'] = ((int)$e['activo'] === 1) ? 1 : 0;
    }
    if (!empty($e['password'])) {
        if (strlen((string)$e['password']) < 6) {
            responder(400, 'ERROR_VALIDACION', 'password debe tener al menos 6 caracteres.');
        }
        $sets[] = 'password_hash = :hash';
        $params[':hash'] = password_hash((string)$e['password'], PASSWORD_DEFAULT);
    }
    if (!$sets) {
        responder(400, 'ERROR_VALIDACION',
            'Nada que actualizar: envía nombre, rol, activo o password.');
    }

    // --- Regla de negocio: nunca dejar el sistema sin un admin activo -------
    $rolFinal    = array_key_exists('rol', $e)    ? (string)$e['rol']    : $actual['rol'];
    $activoFinal = array_key_exists('activo', $e) ? (int)$e['activo']    : (int)$actual['activo'];
    if (($actual['rol'] === 'admin' && (int)$actual['activo'] === 1)
            && ($rolFinal !== 'admin' || $activoFinal !== 1)
            && contarAdminsActivos() <= 1) {
        responder(409, 'ULTIMO_ADMIN',
            'No puedes dejar el sistema sin administradores activos.');
    }

    db()->prepare('UPDATE usuarios SET ' . implode(', ', $sets) . ' WHERE id = :id')
        ->execute($params);

    responder(200, 'OK', "Usuario $id actualizado.");
}

function contarAdminsActivos(): int
{
    return (int)db()->query(
        "SELECT COUNT(*) FROM usuarios WHERE rol = 'admin' AND activo = 1"
    )->fetchColumn();
}

function validarRol(string $rol): void
{
    if (!in_array($rol, ['capturista', 'revisor', 'admin', 'proveedor'], true)) {
        responder(400, 'ERROR_VALIDACION',
            'rol debe ser: capturista, revisor, admin o proveedor.');
    }
}

