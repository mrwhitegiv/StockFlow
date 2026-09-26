# StockFlow MVP Product Requirements

## 1. Positioning

StockFlow is an order, inventory and warehouse management system for small and medium businesses. The main goal of this project is to practice production-oriented software engineering rather than product innovation.

The MVP focuses on:
- business modeling
- database design
- REST APIs
- authentication and RBAC
- transactions
- concurrency control
- inventory consistency
- auditability
- testing
- containerization and deployment

The first stage uses a modular monolith. No microservices.

## 2. Roles

### Admin
- Manage users and roles
- Manage master data
- View all business data

### Purchaser
- Create purchase orders
- Submit purchase orders
- View purchase progress
- Cannot directly modify inventory

### Warehouse Manager
- Receive purchases
- Execute inbound/outbound operations
- Manage warehouse transfers
- Query inventory

### Sales
- Create sales orders
- Confirm orders
- Query order status
- Cannot directly modify inventory

## 3. Core Modules

MVP:
- Users and roles
- Product / SKU
- Warehouse
- Inventory
- Inventory transaction ledger
- Purchase inbound
- Sales outbound
- Warehouse transfer

Out of scope for MVP:
- Finance
- Payment
- Logistics delivery
- Complex supplier management
- CRM
- Consumer storefront
- AI features
- Microservices
- Inventory counting

## 4. Product and SKU

Product represents an abstract product.

Example:
- Product: iPhone 17
- SKU: iPhone 17 / Black / 256GB

All inventory, purchase, sales, and transfer operations are performed at SKU level.

Product does not store stock quantity.

## 5. Warehouse Model

The system supports multiple warehouses.

Example:
- Guangzhou Warehouse
- Shenzhen Warehouse
- Dongguan Warehouse

Inventory unique key:
- warehouse_id
- sku_id

One SKU has only one inventory record per warehouse.

## 6. Inventory Model

Core fields:
- warehouse_id
- sku_id
- on_hand_qty
- locked_qty
- version

Available quantity is calculated as:

available_qty = on_hand_qty - locked_qty

Do not persist available_qty separately.

Invariants:
- on_hand_qty >= 0
- locked_qty >= 0
- locked_qty <= on_hand_qty

version is reserved for optimistic locking.

## 7. Inventory Ledger

Every inventory change must create an immutable inventory transaction.

Core fields:
- warehouse_id
- sku_id
- business_type
- business_no
- operation_type
- quantity_before
- quantity_change
- quantity_after
- operator_id
- created_at
- remark

operation_type:
- PURCHASE_IN
- SALES_LOCK
- SALES_UNLOCK
- SALES_OUT
- TRANSFER_OUT
- TRANSFER_IN
- ADJUSTMENT

Business users cannot edit or delete inventory ledger records.

## 8. Purchase Flow

States:

DRAFT -> APPROVED -> RECEIVED -> COMPLETED

Alternative:

DRAFT -> CANCELLED

Rules:
- Purchaser creates purchase order
- Authorized user approves it
- Only APPROVED orders can be received
- Warehouse receiving increases on_hand_qty
- Create PURCHASE_IN ledger record
- Inventory update, ledger creation and order state transition must be in one transaction
- Partial receiving is out of scope for MVP

## 9. Sales Flow

States:

DRAFT -> CONFIRMED -> SHIPPED -> COMPLETED

Alternatives:
- DRAFT -> CANCELLED
- CONFIRMED -> CANCELLED

Confirm:
- Check available_qty
- Increase locked_qty
- Create SALES_LOCK ledger
- Move order to CONFIRMED

Ship:
- Decrease on_hand_qty
- Decrease locked_qty
- Create SALES_OUT ledger
- Move order to SHIPPED

Cancel confirmed order:
- Decrease locked_qty
- Create SALES_UNLOCK ledger

Inventory mutation, ledger creation and state transitions must be transactional.

## 10. Warehouse Transfer

States:

DRAFT -> APPROVED -> OUTBOUND -> INBOUND -> COMPLETED

Source outbound:
- Decrease source on_hand_qty
- Create TRANSFER_OUT ledger

Destination inbound:
- Increase destination on_hand_qty
- Create TRANSFER_IN ledger

MVP does not maintain a separate in-transit inventory table. In-transit quantity is represented by transfer order state and items.

## 11. Permission and State Transition Principle

Business state must not be changed through generic CRUD.

Bad:
PUT /sales-orders/{id}
{ "status": "SHIPPED" }

Good:
POST /sales-orders/{id}/ship

The backend must verify:
- current state
- user permission
- inventory constraints
- business invariants

## 12. Initial Database Tables

- sys_user
- sys_role
- sys_user_role
- category
- product
- sku
- warehouse
- inventory
- inventory_transaction
- purchase_order
- purchase_order_item
- sales_order
- sales_order_item
- stock_transfer
- stock_transfer_item

## 13. Architecture

Frontend:
Vue 3

REST API

Backend:
Spring Boot modular monolith

Layers:
Controller -> Service -> Mapper -> MySQL

Initial infrastructure:
- Java 21
- Spring Boot 4.1.x
- Spring Security
- MyBatis-Plus
- MySQL 8
- Maven
- Vue 3
- Element Plus
- Pinia
- Vue Router
- Axios

Redis and RabbitMQ are deliberately deferred until a real requirement justifies them.

## 14. Engineering Rules

- Controllers do not contain core business logic
- Business rules live in Services
- Transaction boundaries follow business actions
- Inventory is not editable through generic CRUD
- Every inventory mutation creates a ledger entry
- Order status cannot be arbitrarily changed
- Frontend validation never replaces backend validation
- Use unified API response structure
- Use centralized exception handling
- Log critical business operations

## 15. MVP Completion Criteria

The system is considered MVP-complete when:

- User can log in
- Admin can create products, SKUs and warehouses
- Purchaser can create purchase orders
- Warehouse manager can complete purchase inbound
- Inbound correctly increases inventory and creates ledger records
- Sales can create sales orders
- Confirming sales orders locks inventory
- Shipping reduces physical inventory
- Cancelling confirmed orders releases locked inventory
- Warehouse transfer can be completed between two warehouses
- Every inventory change is traceable to a business document
- Critical failures correctly roll back transactions
- Project can be deployed using Docker in a fresh environment
