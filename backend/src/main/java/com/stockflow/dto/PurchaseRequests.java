package com.stockflow.dto;

import jakarta.validation.constraints.*;

public final class PurchaseRequests {
    private PurchaseRequests() {}

    public record Create(@NotNull @Positive Long warehouseId, @Size(max = 500) String remark) {}
    public record AddItem(@NotBlank @Size(max = 64) String skuCode, @NotNull @Positive Long quantity) {}
    public record ChangeQuantity(@NotNull @Positive Long quantity) {}
}
