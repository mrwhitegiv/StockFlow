package com.stockflow.controller;

import com.stockflow.common.ApiResponse;
import com.stockflow.dto.MasterDataRequests.*;
import com.stockflow.dto.PageResult;
import com.stockflow.entity.Product;
import com.stockflow.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResult<Product>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 200) String keyword,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(service.list(page, size, keyword, categoryId, enabled));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> get(@PathVariable @Positive long id) { return ApiResponse.success(service.get(id)); }

    @PostMapping(consumes = "application/json")
    public ApiResponse<Product> create(@Valid @RequestBody ProductInput input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ApiResponse<Product> update(@PathVariable @Positive long id, @Valid @RequestBody ProductInput input) {
        return ApiResponse.success(service.update(id, input));
    }

    @PatchMapping(value = "/{id}/enabled", consumes = "application/json")
    public ApiResponse<Product> setEnabled(@PathVariable @Positive long id, @Valid @RequestBody EnabledInput input) {
        return ApiResponse.success(service.setEnabled(id, input.enabled()));
    }
}
