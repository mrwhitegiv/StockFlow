package com.stockflow;

import com.stockflow.dto.MasterDataRequests.*;
import com.stockflow.entity.Product;
import com.stockflow.entity.Sku;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.*;
import com.stockflow.service.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MasterDataServiceTest {
    @Test
    void missingCategoryStopsProductCreation() {
        var products = mock(ProductMapper.class);
        var categories = mock(CategoryMapper.class);
        var service = new ProductService(products, categories);
        assertThatThrownBy(() -> service.create(new ProductInput(10L, "Phone", null)))
                .isInstanceOf(ApiException.class).hasMessage("分类不存在");
        verifyNoInteractions(products);
    }

    @Test
    void disabledProductStopsSkuWrites() {
        var products = mock(ProductMapper.class);
        var skus = mock(SkuMapper.class);
        var product = new Product();
        product.setEnabled(false);
        when(products.findForUpdate(1)).thenReturn(product);
        var service = new SkuService(skus, products);
        assertThatThrownBy(() -> service.create(1, new SkuInput("SKU1", "Black")))
                .isInstanceOf(ApiException.class).hasMessageContaining("已禁用");
        verifyNoInteractions(skus);
        var sku = new Sku();
        sku.setProductId(1L);
        when(skus.selectById(2L)).thenReturn(sku);
        assertThatThrownBy(() -> service.update(2, new SkuInput("SKU1", "White")))
                .isInstanceOf(ApiException.class);
        verify(skus, never()).updateById(any(Sku.class));
    }

    @Test
    void absentDeleteReportsNotFound() {
        var mapper = mock(WarehouseMapper.class);
        assertThatThrownBy(() -> new WarehouseService(mapper).delete(99))
                .isInstanceOf(ApiException.class).hasMessage("仓库不存在");
    }
}
