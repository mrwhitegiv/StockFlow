package com.stockflow;

import com.stockflow.dto.PurchaseRequests;
import com.stockflow.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean PurchaseOrderService service;

    @Test
    void quantityRejectsFractionalCoercionAndOverflowBeforeService() throws Exception {
        for (String quantity : new String[]{"0", "-1", "1.5", "9223372036854775808", "null", "\"1.5\""}) {
            mvc.perform(post("/api/purchase-orders/1/items").contentType("application/json")
                    .content("{\"skuCode\":\"SKU1\",\"quantity\":" + quantity + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        mvc.perform(post("/api/purchase-orders").contentType("application/json").content("{\"warehouseId\":1.5}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void decimalStringPreservesEntireBigintQuantity() throws Exception {
        mvc.perform(post("/api/purchase-orders/1/items").contentType("application/json")
                .content("{\"skuCode\":\"SKU1\",\"quantity\":\"9223372036854775807\"}"))
                .andExpect(status().isOk());
        verify(service).addItem(1, new PurchaseRequests.AddItem("SKU1", Long.MAX_VALUE));
    }

    @Test
    void genericStatusAndLedgerWritesRemainForbidden() throws Exception {
        mvc.perform(patch("/api/purchase-orders/1").contentType("application/json").content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/purchase-orders/1/status").contentType("application/json").content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory-transactions").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void invalidPaginationIdsStatusAndBodiesAreRejected() throws Exception {
        for (String path : new String[]{"?page=0", "?size=101", "?status=BOGUS", "?warehouseId=0", "/0"}) {
            mvc.perform(get("/api/purchase-orders" + path)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/purchase-orders").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/purchase-orders/1/items").contentType("application/json").content("{\"skuCode\":\" \",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
