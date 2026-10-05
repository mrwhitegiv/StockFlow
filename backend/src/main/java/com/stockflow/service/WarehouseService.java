package com.stockflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stockflow.dto.MasterDataRequests.WarehouseInput;
import com.stockflow.entity.Warehouse;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.WarehouseMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {
    private final WarehouseMapper mapper;
    public WarehouseService(WarehouseMapper mapper) { this.mapper = mapper; }

    public List<Warehouse> list() {
        return mapper.selectList(Wrappers.<Warehouse>lambdaQuery().orderByDesc(Warehouse::getId));
    }

    public Warehouse get(long id) {
        Warehouse entity = mapper.selectById(id);
        if (entity == null) throw ApiException.notFound("仓库");
        return entity;
    }

    @Transactional
    public Warehouse create(WarehouseInput input) {
        Warehouse entity = new Warehouse();
        entity.setCode(input.code());
        entity.setName(input.name());
        entity.setAddress(input.address());
        mapper.insert(entity);
        return get(entity.getId());
    }

    @Transactional
    public Warehouse update(long id, WarehouseInput input) {
        Warehouse entity = get(id);
        entity.setCode(input.code());
        entity.setName(input.name());
        entity.setAddress(input.address());
        mapper.updateById(entity);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (mapper.deleteById(id) == 0) throw ApiException.notFound("仓库");
    }
}
