package com.stockflow.mapper;

import com.stockflow.dto.PurchaseViews;
import com.stockflow.entity.Inventory;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** Stock writes are available only through the transactional purchase receipt workflow. */
@Mapper
public interface PurchaseReceiptMapper {
    // The unique (warehouse_id, sku_id) key also serializes concurrent first receipts.
    @Insert("""
            INSERT INTO inventory(warehouse_id, sku_id) VALUES(#{warehouseId}, #{skuId})
            ON DUPLICATE KEY UPDATE id = id
            """)
    void ensureBalance(@Param("warehouseId") long warehouseId, @Param("skuId") long skuId);

    @Select("""
            SELECT warehouse_id, sku_id, on_hand_qty, locked_qty, version FROM inventory
            WHERE warehouse_id = #{warehouseId} AND sku_id = #{skuId} FOR UPDATE
            """)
    Inventory lockBalance(@Param("warehouseId") long warehouseId, @Param("skuId") long skuId);

    @Update("""
            UPDATE inventory SET on_hand_qty = #{balance.onHandQty}, version = #{balance.version}
            WHERE warehouse_id = #{balance.warehouseId} AND sku_id = #{balance.skuId} AND version = #{previousVersion}
            """)
    int receive(@Param("balance") Inventory balance, @Param("previousVersion") long previousVersion);

    @Insert("""
            INSERT INTO inventory_transaction(warehouse_id, sku_id, business_type, business_no,
                operation_type, quantity_type, quantity_before, quantity_change, quantity_after, operator_id)
            VALUES(#{before.warehouseId}, #{before.skuId}, 'PURCHASE', #{orderNo},
                'PURCHASE_IN', 'ON_HAND', #{before.onHandQty}, #{quantity}, #{after}, #{operatorId})
            """)
    void append(@Param("before") Inventory before, @Param("orderNo") String orderNo,
            @Param("quantity") long quantity, @Param("after") long after, @Param("operatorId") long operatorId);

    @Select("""
            SELECT t.id, t.business_no, t.operation_type, t.sku_id, s.code AS sku_code,
                   t.quantity_before, t.quantity_change, t.quantity_after, t.operator_id,
                   u.display_name AS operator_name, t.created_at
            FROM inventory_transaction t JOIN sku s ON s.id = t.sku_id
            JOIN sys_user u ON u.id = t.operator_id
            WHERE t.business_type = 'PURCHASE' AND t.business_no = #{orderNo} ORDER BY t.id
            """)
    List<PurchaseViews.Transaction> findByOrder(@Param("orderNo") String orderNo);
}
