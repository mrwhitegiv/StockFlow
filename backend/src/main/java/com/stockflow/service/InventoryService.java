package com.stockflow.service;

import com.stockflow.dto.InventoryRow;
import com.stockflow.dto.InventoryView;
import com.stockflow.dto.PageResult;
import com.stockflow.entity.Inventory;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.InventoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryMapper mapper;

    public InventoryService(InventoryMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public PageResult<InventoryView> list(int page, int size, Long warehouseId, Long skuId, String skuCode) {
        String code = skuCode == null || skuCode.isBlank() ? null : skuCode.strip();
        var records = mapper.findPage(warehouseId, skuId, code, size, (long) (page - 1) * size)
                .stream().map(this::toView).toList();
        return new PageResult<>(records, mapper.countFiltered(warehouseId, skuId, code), page, size);
    }

    @Transactional(readOnly = true)
    public InventoryView get(long id) {
        InventoryRow row = mapper.findById(id);
        if (row == null) throw ApiException.notFound("库存记录");
        return toView(row);
    }

    private InventoryView toView(InventoryRow row) {
        Inventory balance = new Inventory(row.warehouseId(), row.skuId(),
                row.onHandQty(), row.lockedQty(), row.version());
        return new InventoryView(row.id(), row.warehouseId(), row.warehouseCode(), row.warehouseName(),
                row.skuId(), row.skuCode(), row.skuName(), row.productName(), balance.onHandQty(),
                balance.lockedQty(), balance.availableQty(), balance.version(), row.updatedAt());
    }
}
