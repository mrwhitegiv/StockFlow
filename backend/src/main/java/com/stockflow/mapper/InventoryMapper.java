package com.stockflow.mapper;

import com.stockflow.dto.InventoryRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

// Intentionally does not extend BaseMapper: this issue exposes only read operations.
@Mapper
public interface InventoryMapper {
    String COLUMNS = """
            SELECT i.id, i.warehouse_id, w.code AS warehouse_code, w.name AS warehouse_name,
                   i.sku_id, s.code AS sku_code, s.name AS sku_name, p.name AS product_name,
                   i.on_hand_qty, i.locked_qty, i.version, i.updated_at
            """;
    String JOINS = """
            FROM inventory i
            JOIN warehouse w ON w.id = i.warehouse_id
            JOIN sku s ON s.id = i.sku_id
            JOIN product p ON p.id = s.product_id
            """;
    String FILTER = """
            <where>
                <if test="warehouseId != null">AND i.warehouse_id = #{warehouseId}</if>
                <if test="skuId != null">AND i.sku_id = #{skuId}</if>
                <if test="skuCode != null">AND s.code = #{skuCode}</if>
            </where>
            """;

    @Select("<script>SELECT COUNT(*) " + JOINS + FILTER + "</script>")
    long countFiltered(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("skuCode") String skuCode);

    @Select("<script>" + COLUMNS + JOINS + FILTER
            + " ORDER BY i.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<InventoryRow> findPage(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("skuCode") String skuCode, @Param("limit") int limit, @Param("offset") long offset);

    @Select(COLUMNS + JOINS + " WHERE i.id = #{id}")
    InventoryRow findById(@Param("id") long id);
}
