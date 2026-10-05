package com.stockflow.controller;

import com.stockflow.common.ApiResponse;
import com.stockflow.dto.MasterDataRequests.SkuInput;
import com.stockflow.dto.PageResult;
import com.stockflow.entity.Sku;
import com.stockflow.service.SkuService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SkuController {
    private final SkuService service;
    public SkuController(SkuService service) { this.service = service; }

    @GetMapping("/products/{productId}/skus")
    public ApiResponse<PageResult<Sku>> list(
            @PathVariable @Positive long productId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 200) String keyword) {
        return ApiResponse.success(service.list(productId, page, size, keyword));
    }

    @PostMapping(value = "/products/{productId}/skus", consumes = "application/json")
    public ApiResponse<Sku> create(@PathVariable @Positive long productId, @Valid @RequestBody SkuInput input) {
        return ApiResponse.success(service.create(productId, input));
    }

    @GetMapping("/skus/{id}")
    public ApiResponse<Sku> get(@PathVariable @Positive long id) { return ApiResponse.success(service.get(id)); }

    @PutMapping(value = "/skus/{id}", consumes = "application/json")
    public ApiResponse<Sku> update(@PathVariable @Positive long id, @Valid @RequestBody SkuInput input) {
        return ApiResponse.success(service.update(id, input));
    }

    @DeleteMapping("/skus/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
