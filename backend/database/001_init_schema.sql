-- StockFlow MVP baseline. Run once against an EMPTY selected database.
-- Requires MySQL 8.0.16+ (enforced CHECK constraints), InnoDB, strict SQL mode.
-- No CREATE DATABASE, USE, DROP TABLE, seed users, or credentials here.
SET NAMES utf8mb4;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,NO_ZERO_DATE,NO_ZERO_IN_DATE';

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL COMMENT 'Encoded password hash, never plaintext',
    display_name VARCHAR(100) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    CONSTRAINT ck_sys_user_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_sys_user_username CHECK (CHAR_LENGTH(TRIM(username)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code),
    CONSTRAINT ck_sys_role_code CHECK (CHAR_LENGTH(TRIM(code)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name),
    CONSTRAINT ck_category_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_product_category (category_id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_product_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sku (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(200) NOT NULL COMMENT 'Human-readable variant, e.g. Black / 256GB',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (code),
    KEY idx_sku_product (product_id),
    CONSTRAINT fk_sku_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_sku_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_sku_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_code (code),
    CONSTRAINT ck_warehouse_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_warehouse_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    on_hand_qty BIGINT NOT NULL DEFAULT 0,
    locked_qty BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_warehouse_sku (warehouse_id, sku_id),
    KEY idx_inventory_sku (sku_id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_inventory_sku FOREIGN KEY (sku_id) REFERENCES sku (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_inventory_on_hand CHECK (on_hand_qty >= 0),
    CONSTRAINT ck_inventory_locked CHECK (locked_qty >= 0 AND locked_qty <= on_hand_qty),
    CONSTRAINT ck_inventory_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_order_no (order_no),
    KEY idx_purchase_order_warehouse_status (warehouse_id, status, created_at),
    KEY idx_purchase_order_creator (created_by),
    CONSTRAINT fk_purchase_order_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_purchase_order_creator FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_purchase_order_no CHECK (CHAR_LENGTH(TRIM(order_no)) > 0),
    CONSTRAINT ck_purchase_order_status CHECK (status IN ('DRAFT', 'APPROVED', 'RECEIVED', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_order_item_sku (purchase_order_id, sku_id),
    KEY idx_purchase_order_item_sku (sku_id),
    CONSTRAINT fk_purchase_order_item_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_purchase_order_item_sku FOREIGN KEY (sku_id) REFERENCES sku (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_purchase_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sales_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_no (order_no),
    KEY idx_sales_order_warehouse_status (warehouse_id, status, created_at),
    KEY idx_sales_order_creator (created_by),
    CONSTRAINT fk_sales_order_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_sales_order_creator FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_sales_order_no CHECK (CHAR_LENGTH(TRIM(order_no)) > 0),
    CONSTRAINT ck_sales_order_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'SHIPPED', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sales_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sales_order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_item_sku (sales_order_id, sku_id),
    KEY idx_sales_order_item_sku (sku_id),
    CONSTRAINT fk_sales_order_item_order FOREIGN KEY (sales_order_id) REFERENCES sales_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_sales_order_item_sku FOREIGN KEY (sku_id) REFERENCES sku (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_sales_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_transfer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_warehouse_id BIGINT NOT NULL,
    destination_warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfer_no (transfer_no),
    KEY idx_transfer_source_status (source_warehouse_id, status, created_at),
    KEY idx_transfer_destination_status (destination_warehouse_id, status, created_at),
    KEY idx_transfer_creator (created_by),
    CONSTRAINT fk_transfer_source FOREIGN KEY (source_warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transfer_destination FOREIGN KEY (destination_warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transfer_creator FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_transfer_no CHECK (CHAR_LENGTH(TRIM(transfer_no)) > 0),
    CONSTRAINT ck_transfer_warehouses CHECK (source_warehouse_id <> destination_warehouse_id),
    CONSTRAINT ck_transfer_status CHECK (status IN ('DRAFT', 'APPROVED', 'OUTBOUND', 'INBOUND', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_transfer_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_transfer_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfer_item_sku (stock_transfer_id, sku_id),
    KEY idx_stock_transfer_item_sku (sku_id),
    CONSTRAINT fk_transfer_item_transfer FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfer (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transfer_item_sku FOREIGN KEY (sku_id) REFERENCES sku (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_transfer_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    business_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    business_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Which balance changes: ON_HAND or LOCKED',
    quantity_before BIGINT NOT NULL,
    quantity_change BIGINT NOT NULL COMMENT 'Signed delta',
    quantity_after BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_inventory_transaction_balance (warehouse_id, sku_id, created_at, id),
    KEY idx_inventory_transaction_business (business_type, business_no),
    KEY idx_inventory_transaction_operator (operator_id),
    CONSTRAINT fk_inventory_transaction_balance FOREIGN KEY (warehouse_id, sku_id) REFERENCES inventory (warehouse_id, sku_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_inventory_transaction_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_inventory_transaction_business_no CHECK (CHAR_LENGTH(TRIM(business_no)) > 0),
    CONSTRAINT ck_inventory_transaction_arithmetic CHECK (
        quantity_before >= 0 AND quantity_after >= 0 AND quantity_change <> 0
        AND quantity_after = quantity_before + quantity_change),
    CONSTRAINT ck_inventory_transaction_operation CHECK (
        (business_type = 'PURCHASE' AND operation_type = 'PURCHASE_IN' AND quantity_type = 'ON_HAND' AND quantity_change > 0)
        OR (business_type = 'SALES' AND operation_type = 'SALES_LOCK' AND quantity_type = 'LOCKED' AND quantity_change > 0)
        OR (business_type = 'SALES' AND operation_type = 'SALES_UNLOCK' AND quantity_type = 'LOCKED' AND quantity_change < 0)
        OR (business_type = 'SALES' AND operation_type = 'SALES_OUT' AND quantity_type IN ('ON_HAND', 'LOCKED') AND quantity_change < 0)
        OR (business_type = 'TRANSFER' AND operation_type = 'TRANSFER_OUT' AND quantity_type = 'ON_HAND' AND quantity_change < 0)
        OR (business_type = 'TRANSFER' AND operation_type = 'TRANSFER_IN' AND quantity_type = 'ON_HAND' AND quantity_change > 0)
        OR (business_type = 'ADJUSTMENT' AND operation_type = 'ADJUSTMENT' AND quantity_type = 'ON_HAND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Append-only ledger; corrections must be new entries, never history rewrites.
DELIMITER $$
CREATE TRIGGER trg_inventory_transaction_no_update
BEFORE UPDATE ON inventory_transaction FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory ledger is append-only';
END$$
CREATE TRIGGER trg_inventory_transaction_no_delete
BEFORE DELETE ON inventory_transaction FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory ledger is append-only';
END$$
DELIMITER ;
