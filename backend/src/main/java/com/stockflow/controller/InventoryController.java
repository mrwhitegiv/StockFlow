package com.stockflow.controller;

import com.stockflow.common.ApiResponse;
import com.stockflow.dto.InventoryView;
import com.stockflow.dto.PageResult;
import com.stockflow.service.InventoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResult<InventoryView>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) @Positive Long skuId,
            @RequestParam(required = false) @Size(max = 64) String skuCode) {
        return ApiResponse.success(service.list(page, size, warehouseId, skuId, skuCode));
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryView> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.get(id));
    }
}
