import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/purchase-orders', name: 'purchase-orders', component: () => import('../views/PurchaseOrderView.vue') },
    { path: '/purchase-orders/:id', name: 'purchase-detail', component: () => import('../views/PurchaseOrderDetailView.vue') },
    { path: '/inventory', name: 'inventory', component: () => import('../views/InventoryView.vue') },
    { path: '/products', name: 'products', component: () => import('../views/ProductView.vue') },
    { path: '/products/:productId/skus', name: 'skus', component: () => import('../views/SkuView.vue') },
    { path: '/categories', name: 'categories', component: () => import('../views/CategoryView.vue') },
    { path: '/warehouses', name: 'warehouses', component: () => import('../views/WarehouseView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
