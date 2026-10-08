package com.stockflow.entity;

/** Validated, immutable stock balance. Business actions produce a new balance. */
public record Inventory(long warehouseId, long skuId, long onHandQty, long lockedQty, long version) {
    public Inventory {
        if (warehouseId <= 0 || skuId <= 0) {
            throw new IllegalArgumentException("Warehouse and SKU IDs must be positive");
        }
        if (onHandQty < 0 || lockedQty < 0) {
            throw new IllegalArgumentException("Stock quantities must not be negative");
        }
        if (lockedQty > onHandQty) {
            throw new IllegalArgumentException("Locked quantity must not exceed on-hand quantity");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Inventory version must not be negative");
        }
    }

    public Inventory receive(long quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Receipt quantity must be positive");
        return new Inventory(warehouseId, skuId, Math.addExact(onHandQty, quantity), lockedQty, Math.incrementExact(version));
    }

    public long availableQty() {
        // Validation guarantees this subtraction is nonnegative and cannot overflow.
        return onHandQty - lockedQty;
    }
}
