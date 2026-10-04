-- Issue #3: run ONCE after 001_init_schema.sql, with the intended database selected.
-- Existing products remain enabled. No data is deleted.
ALTER TABLE product
    ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER description,
    ADD CONSTRAINT ck_product_enabled CHECK (enabled IN (0, 1));
