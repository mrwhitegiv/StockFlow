package com.stockflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.stockflow.entity.PurchaseStatus;
import java.time.LocalDateTime;
import java.util.List;

public final class PurchaseViews {
    private PurchaseViews() {}

    public record Order(
            @JsonFormat(shape = JsonFormat.Shape.STRING) long id,
            String orderNo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long warehouseId,
            String warehouseCode, String warehouseName, PurchaseStatus status,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long createdBy,
            String creatorName, String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record Item(
            @JsonFormat(shape = JsonFormat.Shape.STRING) long id,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long skuId,
            String skuCode, String skuName, String productName,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long quantity) {}

    public record Transaction(
            @JsonFormat(shape = JsonFormat.Shape.STRING) long id,
            String businessNo, String operationType,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long skuId,
            String skuCode,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long quantityBefore,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long quantityChange,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long quantityAfter,
            @JsonFormat(shape = JsonFormat.Shape.STRING) long operatorId,
            String operatorName, LocalDateTime createdAt) {}

    public record Detail(Order order, List<Item> items, List<Transaction> transactions) {}
}
