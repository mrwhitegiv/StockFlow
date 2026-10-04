package com.stockflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stockflow.dto.MasterDataRequests.CategoryInput;
import com.stockflow.entity.Category;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.CategoryMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryMapper mapper;
    public CategoryService(CategoryMapper mapper) { this.mapper = mapper; }

    public List<Category> list() {
        return mapper.selectList(Wrappers.<Category>lambdaQuery().orderByDesc(Category::getId));
    }

    public Category get(long id) {
        Category entity = mapper.selectById(id);
        if (entity == null) throw ApiException.notFound("分类");
        return entity;
    }

    @Transactional
    public Category create(CategoryInput input) {
        Category entity = new Category();
        entity.setName(input.name());
        mapper.insert(entity);
        return get(entity.getId());
    }

    @Transactional
    public Category update(long id, CategoryInput input) {
        Category entity = get(id);
        entity.setName(input.name());
        mapper.updateById(entity);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (mapper.deleteById(id) == 0) throw ApiException.notFound("分类");
    }
}
