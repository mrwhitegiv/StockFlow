package com.stockflow.service;

import com.stockflow.dto.*;
import com.stockflow.entity.*;
import com.stockflow.exception.ApiException;
import com.stockflow.mapper.*;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {
    private static final int MAX_ITEMS = 100;
    private final PurchaseOrderMapper orders;
    private final PurchaseReceiptMapper receipts;
    private final WarehouseMapper warehouses;
    private final ProductMapper products;
    private final String operatorUsername;

    public PurchaseOrderService(PurchaseOrderMapper orders, PurchaseReceiptMapper receipts,
            WarehouseMapper warehouses, ProductMapper products,
            @Value("${app.development.operator-username}") String operatorUsername) {
        this.orders = orders;
        this.receipts = receipts;
        this.warehouses = warehouses;
        this.products = products;
        this.operatorUsername = operatorUsername;
    }

    @Transactional(readOnly = true)
    public PageResult<PurchaseViews.Order> list(int page, int size, Long warehouseId, PurchaseStatus status, String orderNo) {
        String number = orderNo == null || orderNo.isBlank() ? null : orderNo.trim();
        return new PageResult<>(orders.findPage(warehouseId, status, number, size, ((long) page - 1) * size),
                orders.countFiltered(warehouseId, status, number), page, size);
    }

    @Transactional(readOnly = true)
    public PurchaseViews.Detail get(long id) {
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseViews.Detail create(PurchaseRequests.Create body) {
        if (warehouses.selectById(body.warehouseId()) == null) throw ApiException.notFound("仓库");
        String number = "PO-" + UUID.randomUUID().toString().replace("-", "");
        orders.insert(number, body.warehouseId(), operatorId(), body.remark() == null ? null : body.remark().trim());
        return detail(orders.findId(number));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseViews.Detail addItem(long id, PurchaseRequests.AddItem body) {
        locked(id, PurchaseStatus.DRAFT);
        List<PurchaseViews.Item> items = orders.findItems(id);
        if (items.size() >= MAX_ITEMS) throw ApiException.conflict("每张采购单最多 100 条明细");
        String code = body.skuCode().trim();
        Sku sku = orders.findSku(code);
        if (sku == null) throw ApiException.notFound("SKU");
        long expectedSkuId = sku.getId();
        requireEnabledProduct(sku.getProductId());
        // Product first, then SKU: the same lock order as SKU maintenance.
        sku = orders.lockSku(code);
        if (sku == null) throw ApiException.notFound("SKU");
        if (sku.getId() != expectedSkuId) throw ApiException.conflict("SKU 编码对应的记录已变化，请刷新后重试");
        long skuId = sku.getId();
        if (items.stream().anyMatch(item -> item.skuId() == skuId)) {
            throw ApiException.conflict("此 SKU 已在采购单中，请修改已有明细的数量");
        }
        orders.addItem(id, skuId, body.quantity());
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseViews.Detail changeQuantity(long id, long itemId, PurchaseRequests.ChangeQuantity body) {
        locked(id, PurchaseStatus.DRAFT);
        if (orders.changeQuantity(id, itemId, body.quantity()) != 1) throw ApiException.notFound("采购明细");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseViews.Detail removeItem(long id, long itemId) {
        locked(id, PurchaseStatus.DRAFT);
        if (orders.removeItem(id, itemId) != 1) throw ApiException.notFound("采购明细");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseViews.Detail approve(long id) {
        locked(id, PurchaseStatus.DRAFT);
        if (orders.findItems(id).isEmpty()) throw ApiException.conflict("请先添加采购明细");
        // Serialize approval against product disabling, locking products in a stable order.
        orders.findProductIds(id).forEach(this::requireEnabledProduct);
        transition(id, PurchaseStatus.DRAFT, PurchaseStatus.APPROVED);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseViews.Detail receive(long id) {
        PurchaseOrder order = locked(id, PurchaseStatus.APPROVED);
        List<PurchaseViews.Item> items = orders.findItems(id);
        if (items.isEmpty() || items.size() > MAX_ITEMS) throw ApiException.conflict("采购明细不合法");
        long actor = operatorId();
        // SQL orders items by SKU ID so multi-line receipts acquire stock locks consistently.
        for (PurchaseViews.Item item : items) {
            receipts.ensureBalance(order.warehouseId(), item.skuId());
            Inventory before = receipts.lockBalance(order.warehouseId(), item.skuId());
            Inventory after;
            try {
                after = before.receive(item.quantity());
            } catch (ArithmeticException exception) {
                throw ApiException.conflict("库存数量或版本超出允许范围，收货已回滚");
            }
            if (receipts.receive(after, before.version()) != 1) {
                throw ApiException.conflict("库存已变化，请刷新后重试");
            }
            receipts.append(before, order.orderNo(), item.quantity(), after.onHandQty(), actor);
        }
        transition(id, PurchaseStatus.APPROVED, PurchaseStatus.RECEIVED);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseViews.Detail complete(long id) {
        locked(id, PurchaseStatus.RECEIVED);
        transition(id, PurchaseStatus.RECEIVED, PurchaseStatus.COMPLETED);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseViews.Detail cancel(long id) {
        locked(id, PurchaseStatus.DRAFT);
        transition(id, PurchaseStatus.DRAFT, PurchaseStatus.CANCELLED);
        return detail(id);
    }

    private PurchaseOrder locked(long id, PurchaseStatus expected) {
        PurchaseOrder order = orders.findForUpdate(id);
        if (order == null) throw ApiException.notFound("采购单");
        order.status().require(expected);
        return order;
    }

    private void transition(long id, PurchaseStatus expected, PurchaseStatus next) {
        if (orders.transition(id, expected, next) != 1) throw ApiException.conflict("采购单状态已变化，请刷新后重试");
    }

    private PurchaseViews.Detail detail(long id) {
        PurchaseViews.Order order = orders.findView(id);
        if (order == null) throw ApiException.notFound("采购单");
        return new PurchaseViews.Detail(order, orders.findItems(id), receipts.findByOrder(order.orderNo()));
    }

    private void requireEnabledProduct(long productId) {
        Product product = products.findForUpdate(productId);
        if (product == null) throw ApiException.notFound("商品");
        if (!Boolean.TRUE.equals(product.getEnabled())) throw ApiException.conflict("停用商品不能添加到采购单或通过审核");
    }

    private long operatorId() {
        Long id = orders.findDevelopmentOperator(operatorUsername);
        if (id == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "本地开发操作者未配置，请按 README 执行 dev_purchase_operator.sql");
        }
        return id;
    }
}
