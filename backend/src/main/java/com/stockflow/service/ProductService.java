package com.stockflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stockflow.dto.MasterDataRequests.ProductInput;
import com.stockflow.dto.PageResult;
import com.stockflow.entity.Product;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.CategoryMapper;
import com.stockflow.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductMapper mapper;
    private final CategoryMapper categories;

    public ProductService(ProductMapper mapper, CategoryMapper categories) {
        this.mapper = mapper;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public PageResult<Product> list(int page, int size, String keyword, Long categoryId, Boolean enabled) {
        return new PageResult<>(mapper.findPage(keyword, categoryId, enabled, size, (long) (page - 1) * size),
                mapper.countFiltered(keyword, categoryId, enabled), page, size);
    }

    public Product get(long id) {
        Product product = mapper.selectById(id);
        if (product == null) throw ApiException.notFound("商品");
        return product;
    }

    @Transactional
    public Product create(ProductInput input) {
        requireCategory(input.categoryId());
        Product product = new Product();
        apply(product, input);
        product.setEnabled(true);
        mapper.insert(product);
        return get(product.getId());
    }

    @Transactional
    public Product update(long id, ProductInput input) {
        Product product = get(id);
        requireCategory(input.categoryId());
        apply(product, input);
        // Only update editable fields: a concurrent disable must never be overwritten.
        mapper.update(Wrappers.<Product>lambdaUpdate().eq(Product::getId, id)
                .set(Product::getCategoryId, product.getCategoryId()).set(Product::getName, product.getName())
                .set(Product::getDescription, product.getDescription()));
        return get(id);
    }

    @Transactional
    public Product setEnabled(long id, boolean enabled) {
        get(id);
        mapper.update(Wrappers.<Product>lambdaUpdate().eq(Product::getId, id).set(Product::getEnabled, enabled));
        return get(id);
    }

    private void requireCategory(long id) {
        if (categories.selectById(id) == null) throw ApiException.notFound("分类");
    }

    private void apply(Product product, ProductInput input) {
        product.setCategoryId(input.categoryId());
        product.setName(input.name());
        product.setDescription(input.description());
    }
}
