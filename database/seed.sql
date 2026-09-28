-- ============================================================================
-- CPP — Plataforma Inteligente para la Gestión de Cuentas por Pagar
-- Script de base de datos + datos semilla (Sprint 1 / Fase 1)
--
-- Cumple: RF-D1, RF-D2, RF-D3, RF-D4 (integridad FK) y RF-D5 simplificado
--         (columnas folio y riesgo en `facturas`).
-- Checklist MVP 9.1: "crear las 3 tablas con al menos 1 orden de compra e
--         historial de proveedor de prueba".
--
-- Motor:    MySQL 5.7+ / MariaDB 10.4+ (Laragon o XAMPP)
-- Importar: phpMyAdmin → pestaña "Importar" → seleccionar este archivo
--           o por consola:  mysql -u root -p < database\seed.sql
-- Es re-ejecutable: borra y recrea las tablas (solo para entorno de desarrollo).
-- ============================================================================

CREATE DATABASE IF NOT EXISTS cpp
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE cpp;

-- ----------------------------------------------------------------------------
-- Tabla: usuarios (RF-D1)
-- El login real llega en v1.0 (RF-M1 / RF-B6); el MVP usa usuarios semilla.
-- password_hash = SHA-256 en hex (CHAR 64). v1.0 migrará a password_hash() PHP.
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS facturas;
DROP TABLE IF EXISTS historial_proveedor;
DROP TABLE IF EXISTS ordenes_compra;
DROP TABLE IF EXISTS usuarios;

CREATE TABLE usuarios (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  nombre        VARCHAR(100) NOT NULL,
  usuario       VARCHAR(50)  NOT NULL UNIQUE,
  password_hash CHAR(64)     NOT NULL COMMENT 'SHA-256 hex del password (demo)',
  rol           ENUM('capturista','revisor','admin') NOT NULL DEFAULT 'capturista',
  activo        TINYINT(1)   NOT NULL DEFAULT 1,
  creado_en     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
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
  ruta_imagen         VARCHAR(255)  NOT NULL,
  creado_en           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  procesado_en        TIMESTAMP     NULL COMMENT 'KPI-1: creado_en → procesado_en ≤ 30 s',
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

-- Usuarios de demostración (password de todos: demo1234)
INSERT INTO usuarios (nombre, usuario, password_hash, rol) VALUES
('Administrador Demo', 'admin',      SHA2('demo1234', 256), 'admin'),
('Capturista Demo',    'capturista', SHA2('demo1234', 256), 'capturista'),
('Revisor Contable',   'revisor',    SHA2('demo1234', 256), 'revisor');

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

