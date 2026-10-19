-- ============================================================================
-- CPP — Plataforma Inteligente para la Gestión de Cuentas por Pagar
-- Script de base de datos + datos semilla (Sprint 1 / Fase 1 · v1.0 en S5)
--
-- Cumple: RF-D1, RF-D2, RF-D3, RF-D4 (integridad FK) y RF-D5 simplificado
--         (columnas folio y riesgo en `facturas`).
-- v1.0 (Sprint 5): usuarios.password_hash VARCHAR(255) — admite bcrypt de
--         password_hash() además del SHA-256 del seed — y tabla `sesiones`
--         para el login con token X-CPP-Token (RF-M1 / RF-B6).
--
-- Motor:    MySQL 5.7+ / MariaDB 10.4+ (Laragon o XAMPP)
-- Importar: phpMyAdmin → pestaña "Importar" → seleccionar este archivo
--           o por consola:  mysql -u root -p < database\seed.sql
-- Es re-ejecutable: borra y recrea las tablas (solo para entorno de desarrollo).
-- SI YA TIENES DATOS y no quieres partir de cero: usar migrar_v1.sql.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS cpp
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE cpp;

-- ----------------------------------------------------------------------------
-- Tabla: usuarios (RF-D1)
-- Sprint 5 (v1.0, RF-M1/RF-B6): password_hash pasa de CHAR(64) a VARCHAR(255)
-- para admitir bcrypt (password_hash de PHP, ~60 chars). Los usuarios semilla
-- siguen con SHA-256 hex y sesion.php acepta AMBOS formatos.
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS facturas;
DROP TABLE IF EXISTS historial_proveedor;
DROP TABLE IF EXISTS sesiones;
DROP TABLE IF EXISTS ordenes_compra;
DROP TABLE IF EXISTS usuarios;

CREATE TABLE usuarios (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  nombre        VARCHAR(100) NOT NULL,
  usuario       VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL COMMENT 'bcrypt (v1.0) o SHA-256 hex del seed (demo)',
  rol           ENUM('capturista','revisor','admin','proveedor') NOT NULL DEFAULT 'capturista',
  negocio       VARCHAR(100)  NULL COMMENT 'S7 RF-F5: solo rol proveedor — nombre de su negocio (calza con ordenes_compra.proveedor)',
  activo        TINYINT(1)   NOT NULL DEFAULT 1,
  creado_en     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Tabla: sesiones (Sprint 5, RF-M1/RF-B6)
-- Una fila por sesión abierta: token aleatorio de 64 hex + vigencia.
-- El login (api/sesion.php) crea; el logout o la expiración (TTL 8 h) destruye.
-- ----------------------------------------------------------------------------
CREATE TABLE sesiones (
  token      CHAR(64)  PRIMARY KEY COMMENT 'bin2hex(random_bytes(32))',
  usuario_id INT       NOT NULL,
  creado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expira_en  DATETIME  NOT NULL,
  CONSTRAINT fk_sesiones_usuario FOREIGN KEY (usuario_id)
      REFERENCES usuarios (id) ON DELETE CASCADE,
  INDEX idx_sesiones_expira (expira_en)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Tabla: ordenes_compra (RF-D2)
-- ----------------------------------------------------------------------------
CREATE TABLE ordenes_compra (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  codigo           VARCHAR(20)   NOT NULL UNIQUE COMMENT 'Ej. OC-001',
  proveedor        VARCHAR(100)  NOT NULL,
  monto_autorizado DECIMAL(12,2) NOT NULL,
  fecha_emision    DATE          NOT NULL,
  estado           ENUM('abierta','cerrada') NOT NULL DEFAULT 'abierta',
  creado_en        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Tabla: facturas (RF-D3 + RF-D5 simplificado + marcas de tiempo para KPI-1)
-- Estados del MVP: Pendiente → Aprobada | Con Discrepancia.
-- 'Rechazada' queda reservado para la revisión manual de v1.0 (nota 3.3 MVP).
-- ----------------------------------------------------------------------------
CREATE TABLE facturas (
  id                  INT AUTO_INCREMENT PRIMARY KEY,
  orden_id            INT           NOT NULL,
  proveedor_detectado VARCHAR(100)  NULL COMMENT 'Extraído por OCR (Sprint 3)',
  fecha_documento     DATE          NULL COMMENT 'Extraída por OCR (Sprint 3)',
  monto_facturado     DECIMAL(12,2) NULL,
  folio               VARCHAR(50)   NULL COMMENT 'RF-D5 simplificado en el MVP',
  riesgo              DECIMAL(5,2)  NULL COMMENT '% que devolverá clasificador_riesgo (C)',
  estado              ENUM('Pendiente','Aprobada','Con Discrepancia','Rechazada')
                      NOT NULL DEFAULT 'Pendiente',
  motivo_discrepancia VARCHAR(255)  NULL,
  texto_ocr           MEDIUMTEXT    NULL COMMENT 'S9 RF-D5: texto crudo que devolvió el OCR (auditoría)',
  ruta_imagen         VARCHAR(255)  NOT NULL,
  creado_en           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  procesado_en        TIMESTAMP     NULL COMMENT 'KPI-1: creado_en → procesado_en ≤ 30 s',
  pagada              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'S8 RF-P2: 1 = ya pagada por el cliente',
  pagada_en           TIMESTAMP    NULL COMMENT 'S8: cuándo se marcó pagada',
  evidencia_pago      VARCHAR(255)  NULL COMMENT 'S8.3: foto del comprobante que subió el cliente para pagar',
  subida_por          INT           NULL COMMENT 'S8.2: cliente que subió la factura (cada quien ve solo la suya)',
  CONSTRAINT fk_facturas_orden FOREIGN KEY (orden_id)
      REFERENCES ordenes_compra (id),
  INDEX idx_facturas_estado (estado),
  INDEX idx_facturas_orden (orden_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Tabla: historial_proveedor (mitigación del riesgo RM6 del MVP:
-- "falta de historial real para alimentar el módulo C" → historial sintético).
-- ----------------------------------------------------------------------------
CREATE TABLE historial_proveedor (
  id        INT AUTO_INCREMENT PRIMARY KEY,
  proveedor VARCHAR(100)  NOT NULL,
  monto     DECIMAL(12,2) NOT NULL,
  fecha     DATE          NOT NULL,
  INDEX idx_hist_proveedor (proveedor)
) ENGINE=InnoDB;

-- ============================ DATOS SEMILLA =================================

-- Usuarios de demostración (password de todos: demo1234).
-- Sprint 7 (RF-F5): 'proveedor' es el 4.º rol — cuenta EXCLUSIVA de proveedor
-- con su negocio (solo ve y factura las órdenes de "Papelería Central").
INSERT INTO usuarios (nombre, usuario, password_hash, rol, negocio) VALUES
('Administrador Demo', 'admin',      SHA2('demo1234', 256), 'admin',      NULL),
('Capturista Demo',    'capturista', SHA2('demo1234', 256), 'capturista', NULL),
('Revisor Contable',   'revisor',    SHA2('demo1234', 256), 'revisor',    NULL),
('Proveedor Demo',     'proveedor',  SHA2('demo1234', 256), 'proveedor',  'Papelería Central');

-- Órdenes de compra. La OC-001 es la del guion de demo (MVP sección 9.2):
--   Factura A: Papelería Central 4,850.00  → Aprobada (≤ 5,000 y riesgo bajo)
--   Factura B: Papelería Central 6,200.00  → Con Discrepancia (excede orden)
--   Factura C: proveedor distinto          → Con Discrepancia (no coincide)
INSERT INTO ordenes_compra (codigo, proveedor, monto_autorizado, fecha_emision) VALUES
('OC-001', 'Papelería Central',      5000.00, DATE_SUB(CURDATE(), INTERVAL 10 DAY)),
('OC-002', 'Ferretería El Tornillo', 8500.00, DATE_SUB(CURDATE(), INTERVAL  5 DAY)),
('OC-003', 'Oficinas del Norte',     3200.50, DATE_SUB(CURDATE(), INTERVAL  2 DAY));

-- Historial sintético por proveedor (alimenta a clasificador_riesgo en C).
-- Papelería Central: promedio ≈ 3,941.67
--   → 4,850.00 (+23 %) debe dar riesgo BAJO (< 40 %, aprueba la OC-001)
--   → 6,200.00 (+57 %) debe dar riesgo ALTO (alza repentina detectada, RF-R3)
INSERT INTO historial_proveedor (proveedor, monto, fecha) VALUES
('Papelería Central',      3800.00, DATE_SUB(CURDATE(), INTERVAL 75 DAY)),
('Papelería Central',      4200.00, DATE_SUB(CURDATE(), INTERVAL 64 DAY)),
('Papelería Central',      3600.00, DATE_SUB(CURDATE(), INTERVAL 52 DAY)),
('Papelería Central',      4100.00, DATE_SUB(CURDATE(), INTERVAL 41 DAY)),
('Papelería Central',      3900.00, DATE_SUB(CURDATE(), INTERVAL 27 DAY)),
('Papelería Central',      4050.00, DATE_SUB(CURDATE(), INTERVAL 13 DAY)),
('Ferretería El Tornillo', 8100.00, DATE_SUB(CURDATE(), INTERVAL 70 DAY)),
('Ferretería El Tornillo', 8300.00, DATE_SUB(CURDATE(), INTERVAL 48 DAY)),
('Ferretería El Tornillo', 7900.00, DATE_SUB(CURDATE(), INTERVAL 30 DAY)),
('Ferretería El Tornillo', 8200.00, DATE_SUB(CURDATE(), INTERVAL 11 DAY));

-- Nota: NO se siembran facturas; se crean vía la API (checkpoint C1) y a
-- partir del Sprint 4 quedarán en estado Aprobada / Con Discrepancia.

