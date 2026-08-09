-- =============================================================================
-- V2__add_tenant_support.sql
-- Migration: Single-Tenant -> SaaS Multi-Tenant
-- =============================================================================
-- NOTE: This script is designed to be idempotent (safe to re-run after failures).
-- DB state at time of writing (2026-08-01):
--   - Table 'tenants' already exists (created in a previous partial run)
--   - Column 'tenant_id' already exists on all tables as NOT NULL
--   - Data already backfilled to tenant_id = 1
--   - Single-column unique indexes have been dropped by Hibernate ddl-auto=update
--   - Composite unique indexes NOT yet created (STEP 7)
--   - Foreign keys NOT yet created (STEP 8)
--   - Tenant-scoped indexes NOT yet created (STEP 9)
-- =============================================================================

-- =============================================================================
-- STEP 1: Create tenants table (idempotent - CREATE TABLE IF NOT EXISTS)
-- =============================================================================

CREATE TABLE IF NOT EXISTS tenants (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(200)    NOT NULL,
    code                    VARCHAR(100)    NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    subscription_expired_at DATE            NULL,
    contact_email           VARCHAR(150)    NULL,
    contact_phone           VARCHAR(20)     NULL,
    admin_note              TEXT            NULL,
    created_at              DATETIME(6)     NOT NULL,
    updated_at              DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_tenant_code (code),
    INDEX      idx_tenant_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- STEP 2: Insert Default Tenant ID=1 (idempotent - INSERT IGNORE)
-- =============================================================================

INSERT IGNORE INTO tenants (id, name, code, status, subscription_expired_at, created_at, updated_at)
VALUES (1, 'Default Store', 'default-store', 'ACTIVE', NULL, NOW(), NOW());

-- =============================================================================
-- STEP 3: Add tenant_id columns (idempotent via information_schema check)
-- =============================================================================

SET @s='ALTER TABLE users ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE products ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='products' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE product_variants ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_variants' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE categories ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='categories' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE customers ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customers' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE suppliers ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='suppliers' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE import_receipts ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='import_receipts' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE import_receipt_details ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='import_receipt_details' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE orders ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE order_items ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='order_items' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE stock_history ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='stock_history' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s='ALTER TABLE audit_logs ADD COLUMN tenant_id BIGINT NULL';
SET @s=IF((SELECT COUNT(*)FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='audit_logs' AND COLUMN_NAME='tenant_id')>0,'SELECT 1',@s);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- =============================================================================
-- STEP 4: Backfill tenant_id = 1 for all existing data (idempotent - WHERE IS NULL)
-- =============================================================================

UPDATE users              SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE products           SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE product_variants   SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE categories         SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE customers          SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE suppliers          SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE import_receipts    SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE import_receipt_details SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE orders             SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE order_items        SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE stock_history      SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE audit_logs         SET tenant_id = 1 WHERE tenant_id IS NULL AND user_id IS NOT NULL;

-- =============================================================================
-- STEP 5: Make tenant_id NOT NULL (idempotent - MODIFY is safe if already NOT NULL)
-- =============================================================================

ALTER TABLE users              MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE products           MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE product_variants   MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE categories         MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE customers          MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE suppliers          MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE import_receipts    MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE import_receipt_details MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE orders             MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE order_items        MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE stock_history      MODIFY COLUMN tenant_id BIGINT NOT NULL;

-- =============================================================================
-- STEP 6: Drop old single-column unique indexes (idempotent via information_schema)
-- =============================================================================

SET @idx=(SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' LIMIT 1);
SET @s=IF(@idx IS NOT NULL,CONCAT('ALTER TABLE users DROP INDEX `',@idx,'`'),'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @idx=(SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='products' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' LIMIT 1);
SET @s=IF(@idx IS NOT NULL,CONCAT('ALTER TABLE products DROP INDEX `',@idx,'`'),'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @idx=(SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_variants' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' LIMIT 1);
SET @s=IF(@idx IS NOT NULL,CONCAT('ALTER TABLE product_variants DROP INDEX `',@idx,'`'),'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @idx=(SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='categories' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' LIMIT 1);
SET @s=IF(@idx IS NOT NULL,CONCAT('ALTER TABLE categories DROP INDEX `',@idx,'`'),'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @idx=(SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customers' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' LIMIT 1);
SET @s=IF(@idx IS NOT NULL,CONCAT('ALTER TABLE customers DROP INDEX `',@idx,'`'),'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @idx=(SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='suppliers' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' LIMIT 1);
SET @s=IF(@idx IS NOT NULL,CONCAT('ALTER TABLE suppliers DROP INDEX `',@idx,'`'),'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @idx=(SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='import_receipts' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' LIMIT 1);
SET @s=IF(@idx IS NOT NULL,CONCAT('ALTER TABLE import_receipts DROP INDEX `',@idx,'`'),'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @idx=(SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' LIMIT 1);
SET @s=IF(@idx IS NOT NULL,CONCAT('ALTER TABLE orders DROP INDEX `',@idx,'`'),'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- =============================================================================
-- STEP 7: Add composite unique constraints (tenant_id + column)
-- =============================================================================

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND INDEX_NAME='uq_user_tenant_username')>0,'SELECT 1','ALTER TABLE users ADD CONSTRAINT uq_user_tenant_username UNIQUE (tenant_id, username), ADD CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='products' AND INDEX_NAME='uq_product_tenant_code')>0,'SELECT 1','ALTER TABLE products ADD CONSTRAINT uq_product_tenant_code UNIQUE (tenant_id, code)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_variants' AND INDEX_NAME='uq_variant_tenant_sku')>0,'SELECT 1','ALTER TABLE product_variants ADD CONSTRAINT uq_variant_tenant_sku UNIQUE (tenant_id, sku)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='categories' AND INDEX_NAME='uq_category_tenant_slug')>0,'SELECT 1','ALTER TABLE categories ADD CONSTRAINT uq_category_tenant_slug UNIQUE (tenant_id, slug)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customers' AND INDEX_NAME='uq_customer_tenant_phone')>0,'SELECT 1','ALTER TABLE customers ADD CONSTRAINT uq_customer_tenant_phone UNIQUE (tenant_id, phone)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='suppliers' AND INDEX_NAME='uq_supplier_tenant_phone')>0,'SELECT 1','ALTER TABLE suppliers ADD CONSTRAINT uq_supplier_tenant_phone UNIQUE (tenant_id, phone)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='import_receipts' AND INDEX_NAME='uq_import_receipt_tenant_code')>0,'SELECT 1','ALTER TABLE import_receipts ADD CONSTRAINT uq_import_receipt_tenant_code UNIQUE (tenant_id, receipt_code)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND INDEX_NAME='uq_order_tenant_code')>0,'SELECT 1','ALTER TABLE orders ADD CONSTRAINT uq_order_tenant_code UNIQUE (tenant_id, order_code)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- =============================================================================
-- STEP 8: Add Foreign Keys (idempotent via information_schema check)
-- =============================================================================

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND CONSTRAINT_NAME='fk_users_tenant')>0,'SELECT 1','ALTER TABLE users ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='products' AND CONSTRAINT_NAME='fk_products_tenant')>0,'SELECT 1','ALTER TABLE products ADD CONSTRAINT fk_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_variants' AND CONSTRAINT_NAME='fk_product_variants_tenant')>0,'SELECT 1','ALTER TABLE product_variants ADD CONSTRAINT fk_product_variants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='categories' AND CONSTRAINT_NAME='fk_categories_tenant')>0,'SELECT 1','ALTER TABLE categories ADD CONSTRAINT fk_categories_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customers' AND CONSTRAINT_NAME='fk_customers_tenant')>0,'SELECT 1','ALTER TABLE customers ADD CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='suppliers' AND CONSTRAINT_NAME='fk_suppliers_tenant')>0,'SELECT 1','ALTER TABLE suppliers ADD CONSTRAINT fk_suppliers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='import_receipts' AND CONSTRAINT_NAME='fk_import_receipts_tenant')>0,'SELECT 1','ALTER TABLE import_receipts ADD CONSTRAINT fk_import_receipts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='import_receipt_details' AND CONSTRAINT_NAME='fk_import_receipt_details_tenant')>0,'SELECT 1','ALTER TABLE import_receipt_details ADD CONSTRAINT fk_import_receipt_details_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND CONSTRAINT_NAME='fk_orders_tenant')>0,'SELECT 1','ALTER TABLE orders ADD CONSTRAINT fk_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='order_items' AND CONSTRAINT_NAME='fk_order_items_tenant')>0,'SELECT 1','ALTER TABLE order_items ADD CONSTRAINT fk_order_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='stock_history' AND CONSTRAINT_NAME='fk_stock_history_tenant')>0,'SELECT 1','ALTER TABLE stock_history ADD CONSTRAINT fk_stock_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- =============================================================================
-- STEP 9: Add performance indexes for tenant_id lookups
-- =============================================================================

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND INDEX_NAME='idx_users_tenant')>0,'SELECT 1','CREATE INDEX idx_users_tenant ON users (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='products' AND INDEX_NAME='idx_products_tenant')>0,'SELECT 1','CREATE INDEX idx_products_tenant ON products (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_variants' AND INDEX_NAME='idx_variants_tenant')>0,'SELECT 1','CREATE INDEX idx_variants_tenant ON product_variants (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='categories' AND INDEX_NAME='idx_categories_tenant')>0,'SELECT 1','CREATE INDEX idx_categories_tenant ON categories (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customers' AND INDEX_NAME='idx_customers_tenant')>0,'SELECT 1','CREATE INDEX idx_customers_tenant ON customers (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='suppliers' AND INDEX_NAME='idx_suppliers_tenant')>0,'SELECT 1','CREATE INDEX idx_suppliers_tenant ON suppliers (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='import_receipts' AND INDEX_NAME='idx_import_receipts_tenant')>0,'SELECT 1','CREATE INDEX idx_import_receipts_tenant ON import_receipts (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND INDEX_NAME='idx_orders_tenant')>0,'SELECT 1','CREATE INDEX idx_orders_tenant ON orders (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='stock_history' AND INDEX_NAME='idx_stock_history_tenant')>0,'SELECT 1','CREATE INDEX idx_stock_history_tenant ON stock_history (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

SET @s=IF((SELECT COUNT(*)FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='audit_logs' AND INDEX_NAME='idx_audit_logs_tenant')>0,'SELECT 1','CREATE INDEX idx_audit_logs_tenant ON audit_logs (tenant_id)');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
