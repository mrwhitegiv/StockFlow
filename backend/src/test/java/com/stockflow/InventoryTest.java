package com.stockflow;

import com.stockflow.entity.Inventory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InventoryTest {
    @Test
    void calculatesAvailableForFreeLockedAndEmptyBalances() {
        assertThat(new Inventory(1, 2, 10, 3, 0).availableQty()).isEqualTo(7);
        assertThat(new Inventory(1, 2, 10, 10, 4).availableQty()).isZero();
        assertThat(new Inventory(1, 2, 0, 0, 0).availableQty()).isZero();
    }

    @Test
    void rejectsNegativeBalancesAndVersionsBeforeTheyCanBeUsed() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Inventory(1, 2, -1, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Inventory(1, 2, 1, -1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Inventory(1, 2, 1, 0, -1));
    }

    @Test
    void rejectsLockedQuantityGreaterThanOnHand() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Inventory(1, 2, 5, 6, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Inventory(1, 2, 0, 1, 0));
    }

    @Test
    void rejectsInvalidWarehouseAndSkuIdentities() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Inventory(0, 2, 0, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Inventory(1, -1, 0, 0, 0));
    }

    @Test
    void handlesFullSignedBigintRangeWithoutOverflow() {
        assertThat(new Inventory(1, 2, Long.MAX_VALUE, 1, Long.MAX_VALUE).availableQty())
                .isEqualTo(Long.MAX_VALUE - 1);
        assertThat(new Inventory(1, 2, Long.MAX_VALUE, Long.MAX_VALUE, 0).availableQty()).isZero();
    }
}
