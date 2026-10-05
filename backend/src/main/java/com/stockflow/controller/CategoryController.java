package com.stockflow.controller;

import com.stockflow.common.ApiResponse;
import com.stockflow.dto.MasterDataRequests.CategoryInput;
import com.stockflow.entity.Category;
import com.stockflow.service.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<Category>> list() { return ApiResponse.success(service.list()); }

    @GetMapping("/{id}")
    public ApiResponse<Category> get(@PathVariable @Positive long id) { return ApiResponse.success(service.get(id)); }

    @PostMapping(consumes = "application/json")
    public ApiResponse<Category> create(@Valid @RequestBody CategoryInput input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public ApiResponse<Category> update(@PathVariable @Positive long id, @Valid @RequestBody CategoryInput input) {
        return ApiResponse.success(service.update(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
