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

-- 3) Sprint 7 (RF-F5..F7): cuentas de proveedor — rol 'proveedor' + negocio
--    (el nombre de su negocio calza con ordenes_compra.proveedor).
ALTER TABLE usuarios
  MODIFY rol ENUM('capturista','revisor','admin','proveedor')
    NOT NULL DEFAULT 'capturista';

ALTER TABLE usuarios
  ADD COLUMN negocio VARCHAR(100) NULL
    COMMENT 'S7 RF-F5: solo rol proveedor — calza con ordenes_compra.proveedor';

-- Usuario proveedor de demo (password demo1234), solo si no existiera:
INSERT INTO usuarios (nombre, usuario, password_hash, rol, negocio)
SELECT 'Proveedor Demo', 'proveedor', SHA2('demo1234', 256), 'proveedor', 'Papelería Central'
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE usuario = 'proveedor');

-- 4) Sprint 8 (RF-P1..P3): cuentas por pagar — flag de pago en facturas.
ALTER TABLE facturas
  ADD COLUMN pagada    TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'S8 RF-P2: 1 = ya pagada por el cliente',
  ADD COLUMN pagada_en TIMESTAMP NULL
    COMMENT 'S8: cuándo se marcó pagada';

-- 5) Ajuste S8.2: cada cliente ve SOLO sus propias cuentas — quién subió
--    cada factura. Las históricas de la demo quedan del capturista (id 2).
ALTER TABLE facturas
  ADD COLUMN subida_por INT NULL
    COMMENT 'S8.2: cliente que subió la factura (cada quien ve solo la suya)';

UPDATE facturas SET subida_por = 2 WHERE subida_por IS NULL;

-- 6) Ajuste S8.3: pagar exige FOTO del comprobante (evidencia de pago).
ALTER TABLE facturas
  ADD COLUMN evidencia_pago VARCHAR(255) NULL
    COMMENT 'S8.3: foto del comprobante que subió el cliente para pagar';

-- 7) Sprint 9 (RF-D5 completo): persistir TODO el OCR, no solo 4 campos.
--    El texto crudo queda para auditoría (ver qué leyó el OCR de verdad).
ALTER TABLE facturas
  ADD COLUMN texto_ocr MEDIUMTEXT NULL
    COMMENT 'S9 RF-D5: texto crudo que devolvió el OCR (auditoría)';

-- Verificación rápida (debe listar 5 tablas):
--   SHOW TABLES FROM cpp;
--   -- sesiones, facturas, historial_proveedor, ordenes_compra, usuarios
--
-- Los usuarios semilla SIGUEN con SHA-256 (no hace falta tocarlos):
-- api/sesion.php acepta ambos formatos; los usuarios creados por el CRUD
-- v1.0 ya nacen con bcrypt (password_hash).
