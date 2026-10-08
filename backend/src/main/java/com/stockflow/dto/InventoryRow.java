package com.stockflow.dto;

import java.time.LocalDateTime;

/** Read projection returned by the mapper; converted through the domain model before responding. */
public record InventoryRow(
        long id, long warehouseId, String warehouseCode, String warehouseName,
        long skuId, String skuCode, String skuName, String productName,
        long onHandQty, long lockedQty, long version, LocalDateTime updatedAt) {}
