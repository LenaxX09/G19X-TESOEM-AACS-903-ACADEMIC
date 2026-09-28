<?php
/**
 * db.php — Conexión a MySQL mediante PDO (patrón singleton).
 * Credenciales definidas en config.php.
 */

require_once __DIR__ . '/config.php';

function db(): PDO
{
    static $pdo = null;

    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=' . DB_CHARSET;
        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, [
                PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES   => false,
            ]);
        } catch (PDOException $e) {
            responder(500, 'ERROR_DB', 'No se pudo conectar a MySQL: ' . $e->getMessage()
                . '. Verifica que Apache/MySQL estén activos y que hayas importado database/seed.sql.');
        }
    }

    return $pdo;
}
