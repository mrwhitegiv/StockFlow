package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/** BIGINT values are decimal strings over JSON to preserve precision in the browser. */
public record InventoryView(
        @JsonFormat(shape = JsonFormat.Shape.STRING) long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long warehouseId,
        String warehouseCode,
        String warehouseName,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long skuId,
        String skuCode,
        String skuName,
        String productName,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long onHandQty,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long lockedQty,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long availableQty,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long version,
        LocalDateTime updatedAt) {}
