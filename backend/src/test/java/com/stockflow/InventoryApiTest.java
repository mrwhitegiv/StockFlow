package com.stockflow;

import com.stockflow.dto.InventoryView;
import com.stockflow.dto.PageResult;
import com.stockflow.exception.ApiException;
import com.stockflow.service.InventoryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean InventoryService service;

    private InventoryView view() {
        return new InventoryView(7, 1, "WH1", "广州仓", 2, "SKU1", "黑色", "手机",
                Long.MAX_VALUE, 1, Long.MAX_VALUE - 1, 0, LocalDateTime.of(2026, 10, 5, 0, 0));
    }

    @Test
    void queryIsPublicAndBigintValuesRetainExactDecimalDigits() throws Exception {
        when(service.list(1, 10, 1L, 2L, "SKU1")).thenReturn(new PageResult<>(List.of(view()), 1, 1, 10));
        mvc.perform(get("/api/inventory?warehouseId=1&skuId=2&skuCode=SKU1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value("7"))
                .andExpect(jsonPath("$.data.records[0].onHandQty").value("9223372036854775807"))
                .andExpect(jsonPath("$.data.records[0].lockedQty").value("1"))
                .andExpect(jsonPath("$.data.records[0].availableQty").value("9223372036854775806"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void detailAndMissingRecordUseUnifiedResponses() throws Exception {
        when(service.get(7)).thenReturn(view());
        when(service.get(99)).thenThrow(ApiException.notFound("库存记录"));
        mvc.perform(get("/api/inventory/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warehouseName").value("广州仓"));
        mvc.perform(get("/api/inventory/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void invalidFiltersAndPaginationNeverReachService() throws Exception {
        for (String suffix : new String[]{"?warehouseId=0", "?skuId=-1", "?skuId=text",
                "?skuId=9223372036854775808", "?page=0", "?size=101", "?size=0",
                "?page=1.5", "?skuCode=" + "x".repeat(65), "/0", "/no"}) {
            mvc.perform(get("/api/inventory" + suffix)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void everyPublicStockMutationIsDenied() throws Exception {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            for (String path : List.of("/api/inventory", "/api/inventory/7")) {
                mvc.perform(request(method, path).contentType("application/json").content("{\"onHandQty\":999}"))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void corsAllowsReadsButRejectsWritePreflightAndUntrustedOrigins() throws Exception {
        mvc.perform(options("/api/inventory").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/inventory/7").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void internalInvariantFailureDoesNotLeakDetails() throws Exception {
        when(service.get(7)).thenThrow(new IllegalArgumentException("private invalid database balance"));
        mvc.perform(get("/api/inventory/7")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
