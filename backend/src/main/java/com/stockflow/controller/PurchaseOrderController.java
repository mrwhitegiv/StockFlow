package com.stockflow.controller;

import com.stockflow.common.ApiResponse;
import com.stockflow.dto.*;
import com.stockflow.entity.PurchaseStatus;
import com.stockflow.service.PurchaseOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResult<PurchaseViews.Order>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) @Size(max = 64) String orderNo) {
        return ApiResponse.success(service.list(page, size, warehouseId, status, orderNo));
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseViews.Detail> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    public ApiResponse<PurchaseViews.Detail> create(@RequestBody @Valid PurchaseRequests.Create body) {
        return ApiResponse.success(service.create(body));
    }

    @PostMapping("/{id}/items")
    public ApiResponse<PurchaseViews.Detail> addItem(@PathVariable @Positive long id,
            @RequestBody @Valid PurchaseRequests.AddItem body) {
        return ApiResponse.success(service.addItem(id, body));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ApiResponse<PurchaseViews.Detail> changeQuantity(@PathVariable @Positive long id,
            @PathVariable @Positive long itemId, @RequestBody @Valid PurchaseRequests.ChangeQuantity body) {
        return ApiResponse.success(service.changeQuantity(id, itemId, body));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ApiResponse<PurchaseViews.Detail> removeItem(@PathVariable @Positive long id, @PathVariable @Positive long itemId) {
        return ApiResponse.success(service.removeItem(id, itemId));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<PurchaseViews.Detail> approve(@PathVariable @Positive long id) {
        return ApiResponse.success(service.approve(id));
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<PurchaseViews.Detail> receive(@PathVariable @Positive long id) {
        return ApiResponse.success(service.receive(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<PurchaseViews.Detail> complete(@PathVariable @Positive long id) {
        return ApiResponse.success(service.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<PurchaseViews.Detail> cancel(@PathVariable @Positive long id) {
        return ApiResponse.success(service.cancel(id));
    }
}
