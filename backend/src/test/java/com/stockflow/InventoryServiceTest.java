package com.stockflow;

import com.stockflow.dto.InventoryRow;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.InventoryMapper;
import com.stockflow.service.InventoryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    private final InventoryMapper mapper = mock(InventoryMapper.class);
    private final InventoryService service = new InventoryService(mapper);

    private InventoryRow row(long onHand, long locked) {
        return new InventoryRow(7, 1, "WH1", "广州仓", 2, "SKU1", "黑色", "手机",
                onHand, locked, 4, LocalDateTime.of(2026, 10, 5, 0, 0));
    }

    @Test
    void mapsValidatedBalanceAndPassesCombinedFiltersAndOffset() {
        when(mapper.findPage(1L, 2L, "SKU1", 10, 20L)).thenReturn(List.of(row(10, 3)));
        when(mapper.countFiltered(1L, 2L, "SKU1")).thenReturn(21L);
        var result = service.list(3, 10, 1L, 2L, " SKU1 ");
        assertThat(result.total()).isEqualTo(21);
        assertThat(result.records().getFirst().availableQty()).isEqualTo(7);
        assertThat(result.records().getFirst().version()).isEqualTo(4);
        assertThat(result.records().getFirst().warehouseName()).isEqualTo("广州仓");
        verify(mapper).findPage(1L, 2L, "SKU1", 10, 20L);
    }

    @Test
    void blankCodeMeansNoFilterAndEmptyResultsDoNotCreateInventory() {
        when(mapper.findPage(null, null, null, 10, 0L)).thenReturn(List.of());
        var result = service.list(1, 10, null, null, "  ");
        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
        verify(mapper).findPage(null, null, null, 10, 0L);
        verify(mapper).countFiltered(null, null, null);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void missingRecordReturnsNotFound() {
        assertThatThrownBy(() -> service.get(99)).isInstanceOf(ApiException.class)
                .hasMessage("库存记录不存在");
    }

    @Test
    void corruptDatabaseBalanceFailsClosedRatherThanReturningNegativeAvailability() {
        when(mapper.findById(7)).thenReturn(row(2, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> service.get(7));
    }
}
