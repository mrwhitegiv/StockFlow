package com.stockflow;

import com.stockflow.entity.*;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.*;
import com.stockflow.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchaseReceiptTest {
    @Test
    void receiptPreservesLocksAndOriginalBalanceWhileIncrementingVersion() {
        Inventory before = new Inventory(1, 2, 10, 4, 7);
        Inventory after = before.receive(6);
        assertThat(after).isEqualTo(new Inventory(1, 2, 16, 4, 8));
        assertThat(after.availableQty()).isEqualTo(12);
        assertThat(before.onHandQty()).isEqualTo(10);
    }

    @Test
    void invalidReceiptAndOverflowCannotProduceBalance() {
        Inventory empty = new Inventory(1, 2, 0, 0, 0);
        assertThatIllegalArgumentException().isThrownBy(() -> empty.receive(0));
        assertThatIllegalArgumentException().isThrownBy(() -> empty.receive(-1));
        assertThatThrownBy(() -> new Inventory(1, 2, Long.MAX_VALUE, 0, 0).receive(1))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> new Inventory(1, 2, 0, 0, Long.MAX_VALUE).receive(1))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void reassignedSkuCodeCannotBypassTheProductCheckWhileWaitingForLocks() {
        PurchaseOrderMapper orders = mock(PurchaseOrderMapper.class);
        PurchaseReceiptMapper receipts = mock(PurchaseReceiptMapper.class);
        ProductMapper products = mock(ProductMapper.class);
        PurchaseOrderService service = new PurchaseOrderService(orders, receipts, mock(WarehouseMapper.class),
                products, "development");
        when(orders.findForUpdate(1)).thenReturn(new PurchaseOrder(1, "PO-test", 1, PurchaseStatus.DRAFT));
        Sku original = new Sku();
        original.setId(10L);
        original.setProductId(100L);
        Sku reassigned = new Sku();
        reassigned.setId(20L);
        reassigned.setProductId(200L);
        Product enabled = new Product();
        enabled.setEnabled(true);
        when(orders.findSku("CODE")).thenReturn(original);
        when(products.findForUpdate(100L)).thenReturn(enabled);
        when(orders.lockSku("CODE")).thenReturn(reassigned);
        assertThatThrownBy(() -> service.addItem(1, new com.stockflow.dto.PurchaseRequests.AddItem("CODE", 2L)))
                .isInstanceOf(ApiException.class).hasMessageContaining("SKU 编码对应的记录已变化");
        verify(orders, never()).addItem(anyLong(), anyLong(), anyLong());
    }

    @Test
    void onlyApprovedOrdersCanReachAnyStockOperation() {
        PurchaseOrderMapper orders = mock(PurchaseOrderMapper.class);
        PurchaseReceiptMapper receipts = mock(PurchaseReceiptMapper.class);
        PurchaseOrderService service = new PurchaseOrderService(orders, receipts, mock(WarehouseMapper.class),
                mock(ProductMapper.class), "development");
        for (PurchaseStatus state : PurchaseStatus.values()) {
            if (state == PurchaseStatus.APPROVED) continue;
            when(orders.findForUpdate(1)).thenReturn(new PurchaseOrder(1, "PO-test", 1, state));
            assertThatThrownBy(() -> service.receive(1)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(receipts);
        verify(orders, never()).findItems(anyLong());
    }
}
