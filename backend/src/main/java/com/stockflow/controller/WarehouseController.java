package com.stockflow.controller;

import com.stockflow.common.ApiResponse;
import com.stockflow.dto.MasterDataRequests.WarehouseInput;
import com.stockflow.entity.Warehouse;
import com.stockflow.service.WarehouseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
    private final WarehouseService service;
    public WarehouseController(WarehouseService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<Warehouse>> list() { return ApiResponse.success(service.list()); }

    @GetMapping("/{id}")
    public ApiResponse<Warehouse> get(@PathVariable @Positive long id) { return ApiResponse.success(service.get(id)); }

    @PostMapping(consumes = "application/json")
    public ApiResponse<Warehouse> create(@Valid @RequestBody WarehouseInput input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ApiResponse<Warehouse> update(@PathVariable @Positive long id, @Valid @RequestBody WarehouseInput input) {
        return ApiResponse.success(service.update(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
