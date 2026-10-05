package com.stockflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockflow.entity.Product;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @Select("SELECT * FROM product WHERE id = #{id} FOR UPDATE")
    Product findForUpdate(@Param("id") long id);

    String FILTER = """
            <where>
                <if test="keyword != null and keyword != ''">AND LOCATE(#{keyword}, name) &gt; 0</if>
                <if test="categoryId != null">AND category_id = #{categoryId}</if>
                <if test="enabled != null">AND enabled = #{enabled}</if>
            </where>
            """;

    @Select("<script>SELECT COUNT(*) FROM product " + FILTER + "</script>")
    long countFiltered(@Param("keyword") String keyword, @Param("categoryId") Long categoryId, @Param("enabled") Boolean enabled);

    @Select("<script>SELECT * FROM product " + FILTER
            + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Product> findPage(@Param("keyword") String keyword, @Param("categoryId") Long categoryId, @Param("enabled") Boolean enabled,
            @Param("limit") int limit, @Param("offset") long offset);
}
