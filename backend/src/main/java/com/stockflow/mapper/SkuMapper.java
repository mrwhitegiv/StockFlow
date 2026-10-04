package com.stockflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockflow.entity.Sku;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {
    String FILTER = """
            WHERE product_id = #{productId}
            <if test="keyword != null and keyword != ''">
                AND (LOCATE(#{keyword}, name) &gt; 0 OR LOCATE(#{keyword}, code) &gt; 0)
            </if>
            """;

    @Select("<script>SELECT COUNT(*) FROM sku " + FILTER + "</script>")
    long countFiltered(@Param("productId") long productId, @Param("keyword") String keyword);

    @Select("<script>SELECT * FROM sku " + FILTER
            + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Sku> findPage(@Param("productId") long productId, @Param("keyword") String keyword,
            @Param("limit") int limit, @Param("offset") long offset);
}
