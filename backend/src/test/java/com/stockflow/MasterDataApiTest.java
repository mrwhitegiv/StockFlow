package com.stockflow;

import com.stockflow.entity.Product;
import com.stockflow.exception.ApiException;
import com.stockflow.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService products;
    @MockitoBean CategoryService categories;
    @MockitoBean SkuService skus;
    @MockitoBean WarehouseService warehouses;

    @Test
    void jsonWriteIsPublicAndDelegatesNormalizedInput() throws Exception {
        Product product = new Product();
        product.setId(1L);
        product.setName("Phone");
        when(products.create(any())).thenReturn(product);
        mvc.perform(post("/api/products").contentType("application/json")
                        .content("{\"categoryId\":1,\"name\":\"  Phone  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Phone"));
        verify(products).create(argThat(input -> input.name().equals("Phone") && input.categoryId() == 1));
    }

    @Test
    void invalidBodyDoesNotReachService() throws Exception {
        mvc.perform(post("/api/products").contentType("application/json")
                        .content("{\"categoryId\":0,\"name\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(products);
    }

    @Test
    void rejectsInvalidPaginationAndIdentifiers() throws Exception {
        for (String path : new String[]{"/api/products?page=0", "/api/products?size=101",
                "/api/products?size=no", "/api/products?categoryId=-1", "/api/products/0",
                "/api/products/1/skus?page=-1", "/api/skus/no"}) {
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(products, skus);
    }

    @Test
    void rejectsBadCodeAndExcessivelyLongText() throws Exception {
        mvc.perform(post("/api/warehouses").contentType("application/json")
                        .content("{\"code\":\"bad code\",\"name\":\"Warehouse\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/categories").contentType("application/json")
                        .content("{\"name\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(warehouses, categories);
    }

    @Test
    void malformedJsonAndMissingEnabledAre400() throws Exception {
        mvc.perform(post("/api/products").contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(patch("/api/products/1/enabled").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(products);
    }

    @Test
    void missingAndDuplicateRecordsHaveConsistentErrors() throws Exception {
        when(products.get(99)).thenThrow(ApiException.notFound("商品"));
        mvc.perform(get("/api/products/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.data").doesNotExist());
        when(warehouses.create(any())).thenThrow(new DuplicateKeyException("private SQL details"));
        mvc.perform(post("/api/warehouses").contentType("application/json")
                        .content("{\"code\":\"WH1\",\"name\":\"Warehouse\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
    }

    @Test
    void formAndUnsupportedMethodsHaveJsonErrors() throws Exception {
        mvc.perform(post("/api/categories").contentType("application/x-www-form-urlencoded").content("name=Test"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(415));
        mvc.perform(delete("/api/products/1")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void permitsWritePreflightOnlyForConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/products").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(post("/api/products").header("Origin", "https://untrusted.example")
                        .contentType("application/json").content("{\"name\":\"Phone\",\"categoryId\":1}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(products);
    }
}
