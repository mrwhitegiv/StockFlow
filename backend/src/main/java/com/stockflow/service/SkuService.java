package com.stockflow.service;

import com.stockflow.dto.MasterDataRequests.SkuInput;
import com.stockflow.dto.PageResult;
import com.stockflow.entity.Product;
import com.stockflow.entity.Sku;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.ProductMapper;
import com.stockflow.mapper.SkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkuService {
    private final SkuMapper mapper;
    private final ProductMapper products;

    public SkuService(SkuMapper mapper, ProductMapper products) {
        this.mapper = mapper;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PageResult<Sku> list(long productId, int page, int size, String keyword) {
        requireProduct(productId, false);
        return new PageResult<>(mapper.findPage(productId, keyword, size, (long) (page - 1) * size),
                mapper.countFiltered(productId, keyword), page, size);
    }

    public Sku get(long id) {
        Sku sku = mapper.selectById(id);
        if (sku == null) throw ApiException.notFound("SKU");
        return sku;
    }

    @Transactional
    public Sku create(long productId, SkuInput input) {
        requireProduct(productId, true);
        Sku sku = new Sku();
        sku.setProductId(productId);
        sku.setCode(input.code());
        sku.setName(input.name());
        mapper.insert(sku);
        return get(sku.getId());
    }

    @Transactional
    public Sku update(long id, SkuInput input) {
        Sku sku = get(id);
        requireProduct(sku.getProductId(), true);
        sku.setCode(input.code());
        sku.setName(input.name());
        mapper.updateById(sku);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (mapper.deleteById(id) == 0) throw ApiException.notFound("SKU");
    }

    private Product requireProduct(long id, boolean forWrite) {
        // Serialize SKU writes with disabling their parent product.
        Product product = forWrite ? products.findForUpdate(id) : products.selectById(id);
        if (product == null) throw ApiException.notFound("商品");
        if (forWrite && !Boolean.TRUE.equals(product.getEnabled())) {
            throw ApiException.conflict("商品已禁用，请先启用商品再新增或修改 SKU");
        }
        return product;
    }
}
