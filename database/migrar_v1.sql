-- ----------------------------------------------------------------------------
-- Tabla: sesiones (Sprint 5, RF-M1/RF-B6) — ver seed.sql para la definición.
--    Este script es para INSTALACIONES EXISTENTES: sube el esquema del MVP a
--    la v1.0 SIN perder datos. Si ya se había corrido y la columna es
--    VARCHAR(255), el ALTER fallará con un error inofensivo (ya migrada).
-- ----------------------------------------------------------------------------

USE cpp;

-- 1) usuarios.password_hash: CHAR(64) (solo SHA-256) → VARCHAR(255) (bcrypt+)
ALTER TABLE usuarios
  MODIFY password_hash VARCHAR(255) NOT NULL
  COMMENT 'bcrypt (v1.0) o SHA-256 hex del seed (demo)';

-- 2) Tabla de sesiones para el login con token X-CPP-Token
CREATE TABLE IF NOT EXISTS sesiones (
  token      CHAR(64)  PRIMARY KEY COMMENT 'bin2hex(random_bytes(32))',
  usuario_id INT       NOT NULL,
  creado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expira_en  DATETIME  NOT NULL,
  CONSTRAINT fk_sesiones_usuario FOREIGN KEY (usuario_id)
      REFERENCES usuarios (id) ON DELETE CASCADE,
  INDEX idx_sesiones_expira (expira_en)
) ENGINE=InnoDB;

-- Verificación rápida (debe listar 5 tablas):
--   SHOW TABLES FROM cpp;
--   -- sesiones, facturas, historial_proveedor, ordenes_compra, usuarios
--
-- Los usuarios semilla SIGUEN con SHA-256 (no hace falta tocarlos):
-- api/sesion.php acepta ambos formatos; los usuarios creados por el CRUD
-- v1.0 ya nacen con bcrypt (password_hash).
