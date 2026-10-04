# StockFlow MVP ER 图

对应 [001_init_schema.sql](../backend/database/001_init_schema.sql)。这里只展示关键字段，完整列定义以 SQL 为准。
`||` 表示一个，`o{` 表示零到多个；FK 是外键，UK 是唯一键。

```mermaid
erDiagram
    sys_user ||--o{ sys_user_role : has
    sys_role ||--o{ sys_user_role : assigned
    category ||--o{ product : contains
    product ||--o{ sku : has
    warehouse ||--o{ inventory : stores
    sku ||--o{ inventory : stocked
    inventory ||--o{ inventory_transaction : records
    sys_user ||--o{ inventory_transaction : operates
    sys_user ||--o{ purchase_order : creates
    warehouse ||--o{ purchase_order : receives
    purchase_order ||--o{ purchase_order_item : contains
    sku ||--o{ purchase_order_item : purchased
    sys_user ||--o{ sales_order : creates
    warehouse ||--o{ sales_order : ships
    sales_order ||--o{ sales_order_item : contains
    sku ||--o{ sales_order_item : sold
    sys_user ||--o{ stock_transfer : creates
    warehouse ||--o{ stock_transfer : source
    warehouse ||--o{ stock_transfer : destination
    stock_transfer ||--o{ stock_transfer_item : contains
    sku ||--o{ stock_transfer_item : transferred

    sys_user {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR password_hash
        TINYINT enabled
    }
    sys_role {
        BIGINT id PK
        VARCHAR code UK
        VARCHAR name
    }
    sys_user_role {
        BIGINT user_id PK, FK
        BIGINT role_id PK, FK
    }
    category {
        BIGINT id PK
        VARCHAR name UK
    }
    product {
        BIGINT id PK
        BIGINT category_id FK
        VARCHAR name
    }
    sku {
        BIGINT id PK
        BIGINT product_id FK
        VARCHAR code UK
        VARCHAR name
    }
    warehouse {
        BIGINT id PK
        VARCHAR code UK
        VARCHAR name
    }
    inventory {
        BIGINT id PK
        BIGINT warehouse_id FK "UK pair with sku_id"
        BIGINT sku_id FK
        BIGINT on_hand_qty
        BIGINT locked_qty
        BIGINT version
    }
    inventory_transaction {
        BIGINT id PK
        BIGINT warehouse_id FK "Composite FK with sku_id"
        BIGINT sku_id FK
        VARCHAR business_type
        VARCHAR business_no
        VARCHAR operation_type
        VARCHAR quantity_type
        BIGINT quantity_before
        BIGINT quantity_change
        BIGINT quantity_after
        BIGINT operator_id FK
        DATETIME created_at
    }
    purchase_order {
        BIGINT id PK
        VARCHAR order_no UK
        BIGINT warehouse_id FK
        BIGINT created_by FK
        VARCHAR status
    }
    purchase_order_item {
        BIGINT id PK
        BIGINT purchase_order_id FK "UK pair with sku_id"
        BIGINT sku_id FK
        BIGINT quantity
    }
    sales_order {
        BIGINT id PK
        VARCHAR order_no UK
        BIGINT warehouse_id FK
        BIGINT created_by FK
        VARCHAR status
    }
    sales_order_item {
        BIGINT id PK
        BIGINT sales_order_id FK "UK pair with sku_id"
        BIGINT sku_id FK
        BIGINT quantity
    }
    stock_transfer {
        BIGINT id PK
        VARCHAR transfer_no UK
        BIGINT source_warehouse_id FK
        BIGINT destination_warehouse_id FK
        BIGINT created_by FK
        VARCHAR status
    }
    stock_transfer_item {
        BIGINT id PK
        BIGINT stock_transfer_id FK "UK pair with sku_id"
        BIGINT sku_id FK
        BIGINT quantity
    }
```

说明：

- inventory_transaction 的外键是 (warehouse_id, sku_id) → inventory 的联合唯一键，不是两个各自独立的引用。
- business_type + business_no 只建立查询索引，不画成外键；它由后续业务代码关联不同类型单据。
- 草稿允许零条明细；提交前至少一条明细由后续业务代码保证。
- 同一 SKU 可在不同仓库有库存。同一订单内，同一 SKU 只占一条明细。
- 图中没有 available_qty，读取时计算 on_hand_qty - locked_qty。
