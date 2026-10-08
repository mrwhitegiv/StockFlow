package com.stockflow.mapper;

import com.stockflow.dto.PurchaseViews;
import com.stockflow.entity.PurchaseOrder;
import com.stockflow.entity.PurchaseStatus;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PurchaseOrderMapper {
    String COLUMNS = """
            SELECT o.id, o.order_no, o.warehouse_id, w.code AS warehouse_code,
                   w.name AS warehouse_name, o.status, o.created_by, u.display_name AS creator_name,
                   o.remark, o.created_at, o.updated_at
            FROM purchase_order o JOIN warehouse w ON w.id = o.warehouse_id
            JOIN sys_user u ON u.id = o.created_by
            """;
    String FILTER = """
            <where>
                <if test="warehouseId != null">AND o.warehouse_id = #{warehouseId}</if>
                <if test="status != null">AND o.status = #{status}</if>
                <if test="orderNo != null">AND o.order_no = #{orderNo}</if>
            </where>
            """;

    @Select("<script>SELECT COUNT(*) FROM purchase_order o " + FILTER + "</script>")
    long countFiltered(@Param("warehouseId") Long warehouseId, @Param("status") PurchaseStatus status,
            @Param("orderNo") String orderNo);

    @Select("<script>" + COLUMNS + FILTER + " ORDER BY o.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<PurchaseViews.Order> findPage(@Param("warehouseId") Long warehouseId, @Param("status") PurchaseStatus status,
            @Param("orderNo") String orderNo, @Param("limit") int limit, @Param("offset") long offset);

    @Select(COLUMNS + " WHERE o.id = #{id}")
    PurchaseViews.Order findView(@Param("id") long id);

    @Select("SELECT id, order_no, warehouse_id, status FROM purchase_order WHERE id = #{id} FOR UPDATE")
    PurchaseOrder findForUpdate(@Param("id") long id);

    @Insert("""
            INSERT INTO purchase_order(order_no, warehouse_id, created_by, remark)
            VALUES(#{orderNo}, #{warehouseId}, #{operatorId}, #{remark})
            """)
    void insert(@Param("orderNo") String orderNo, @Param("warehouseId") long warehouseId,
            @Param("operatorId") long operatorId, @Param("remark") String remark);

    @Select("SELECT id FROM purchase_order WHERE order_no = #{orderNo}")
    long findId(@Param("orderNo") String orderNo);

    @Update("UPDATE purchase_order SET status = #{next} WHERE id = #{id} AND status = #{expected}")
    int transition(@Param("id") long id, @Param("expected") PurchaseStatus expected, @Param("next") PurchaseStatus next);

    @Select("""
            SELECT i.id, i.sku_id, s.code AS sku_code, s.name AS sku_name,
                   p.name AS product_name, i.quantity
            FROM purchase_order_item i JOIN sku s ON s.id = i.sku_id
            JOIN product p ON p.id = s.product_id
            WHERE i.purchase_order_id = #{orderId} ORDER BY i.sku_id
            """)
    List<PurchaseViews.Item> findItems(@Param("orderId") long orderId);

    @Select("""
            SELECT DISTINCT s.product_id FROM purchase_order_item i JOIN sku s ON s.id = i.sku_id
            WHERE i.purchase_order_id = #{orderId} ORDER BY s.product_id
            """)
    List<Long> findProductIds(@Param("orderId") long orderId);

    @Insert("INSERT INTO purchase_order_item(purchase_order_id, sku_id, quantity) VALUES(#{orderId}, #{skuId}, #{quantity})")
    void addItem(@Param("orderId") long orderId, @Param("skuId") long skuId, @Param("quantity") long quantity);

    @Update("UPDATE purchase_order_item SET quantity = #{quantity} WHERE id = #{itemId} AND purchase_order_id = #{orderId}")
    int changeQuantity(@Param("orderId") long orderId, @Param("itemId") long itemId, @Param("quantity") long quantity);

    @Delete("DELETE FROM purchase_order_item WHERE id = #{itemId} AND purchase_order_id = #{orderId}")
    int removeItem(@Param("orderId") long orderId, @Param("itemId") long itemId);

    @Select("SELECT id FROM sys_user WHERE username = #{username} AND enabled = 0")
    Long findDevelopmentOperator(@Param("username") String username);

    @Select("SELECT * FROM sku WHERE code = #{code}")
    com.stockflow.entity.Sku findSku(@Param("code") String code);

    @Select("SELECT * FROM sku WHERE code = #{code} FOR UPDATE")
    com.stockflow.entity.Sku lockSku(@Param("code") String code);
}
