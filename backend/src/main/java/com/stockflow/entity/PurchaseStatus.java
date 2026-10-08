package com.stockflow.entity;

import com.stockflow.exception.ApiException;

public enum PurchaseStatus {
    DRAFT, APPROVED, RECEIVED, COMPLETED, CANCELLED;

    public void require(PurchaseStatus expected) {
        if (this != expected) {
            throw ApiException.conflict("当前状态为 " + this + "，此操作要求状态为 " + expected);
        }
    }
}
