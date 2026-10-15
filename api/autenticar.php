<?php
/**
 * autenticar.php — Sprint 5 "Acceso y roles" (RF-B6: control de acceso por rol).
 *
 * Helper compartido por TODOS los endpoints protegidos de la v1.0:
 *
 *   $usuario = requerirAutenticacion();          // 401 si el token falta/expiró
 *   $usuario = requerirRol(['admin']);           // 403 si el rol no corresponde
 *
 * El token llega en la cabecera HTTP «X-CPP-Token» (PHP la expone como
 * $_SERVER['HTTP_X_CPP_TOKEN']); también se acepta ?token=... para probar
 * rápido desde el navegador/PowerShell sin cliente que envíe cabeceras.
 *
 * Sesiones = tabla `sesiones` (token CHAR(64) PK, FK a usuarios, expira_en).
 * El token se genera con random_bytes(32) → 64 hex; NO se guarda nada
 * criptográfico del usuario en el cliente (deuda v1.1: prefs sin cifrar).
 */

require_once __DIR__ . '/responder.php';
require_once __DIR__ . '/db.php';

/** Token de la petición (cabecera X-CPP-Token o ?token=) saneado, o null. */
function tokenPedido(): ?string
{
    $crudo = $_SERVER['HTTP_X_CPP_TOKEN'] ?? ($_GET['token'] ?? null);
    if (!is_string($crudo)) {
        return null;
    }
    $token = strtolower(trim($crudo));
    // Solo hex de 64: evita inyecciones/palabras raras en la consulta.
    return preg_match('/^[a-f0-9]{64}$/', $token) === 1 ? $token : null;
}

/** Usuario de la sesión vigente (id, nombre, usuario, rol) o null si no hay. */
function usuarioActual(): ?array
{
    $token = tokenPedido();
    if ($token === null) {
        return null;
    }
    $stmt = db()->prepare(
        'SELECT u.id, u.nombre, u.usuario, u.rol
           FROM sesiones s
           JOIN usuarios u ON u.id = s.usuario_id
          WHERE s.token = :token
            AND s.expira_en > NOW()
            AND u.activo = 1');
    $stmt->execute([':token' => $token]);
    $usuario = $stmt->fetch();
    return $usuario === false ? null : $usuario;
}

/** Exige sesión válida; si no, responde 401 y corta la ejecución. */
function requerirAutenticacion(): array
{
    $usuario = usuarioActual();
    if ($usuario === null) {
        responder(401, 'NO_AUTENTICADO',
            'Se requiere sesión: inicia sesión en /api/sesion.php y envía el '
            . 'token en la cabecera X-CPP-Token.');
    }
    return $usuario;
}

/** Exige sesión válida Y uno de los roles indicados; si no, 403. */
function requerirRol(array $rolesPermitidos): array
{
    $usuario = requerirAutenticacion();
    if (!in_array($usuario['rol'], $rolesPermitidos, true)) {
        responder(403, 'ROL_NO_PERMITIDO',
            "Tu rol «{$usuario['rol']}» no puede realizar esta operación "
            . '(requiere: ' . implode(', ', $rolesPermitidos) . ').');
    }
    return $usuario;
}

/**
 * Abre una sesión para el usuario: genera el token, borra las sesiones ya
 * caducadas (limpieza oportunista, sin cron) y registra la expiración
 * según SESION_TTL_HORAS de config.php.
 *
 * @return array{token: string, expira_en: string}
 */
function crearSesion(int $usuarioId): array
{
    db()->prepare('DELETE FROM sesiones WHERE expira_en < NOW()')->execute();

    $token = bin2hex(random_bytes(32));
    $stmt = db()->prepare(
        'INSERT INTO sesiones (token, usuario_id, expira_en)
         VALUES (:token, :usuario_id, DATE_ADD(NOW(), INTERVAL :ttl HOUR))');
    $stmt->execute([':token' => $token, ':usuario_id' => $usuarioId,
                    ':ttl' => (int)SESION_TTL_HORAS]);

    $stmt = db()->prepare('SELECT DATE_FORMAT(expira_en, "%Y-%m-%d %H:%i") 
                             FROM sesiones WHERE token = :token');
    $stmt->execute([':token' => $token]);

    return ['token' => $token,
            'expira_en' => (string)($stmt->fetchColumn() ?: '')];
}

/** Cierra la sesión cuyo token llega en la petición (logout). */
function cerrarSesionDeLaPeticion(): bool
{
    $token = tokenPedido();
    if ($token === null) {
        return false;
    }
    $stmt = db()->prepare('DELETE FROM sesiones WHERE token = :token');
    $stmt->execute([':token' => $token]);
    return $stmt->rowCount() > 0;
}
