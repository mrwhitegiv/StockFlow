package com.stockflow.entity;

/** The row locked before every purchase action, including draft item edits. */
public record PurchaseOrder(long id, String orderNo, long warehouseId, PurchaseStatus status) {}
