<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
  <header class="app-header">
    <RouterLink to="/" class="brand">StockFlow</RouterLink>
    <nav aria-label="主导航">
      <RouterLink to="/products">商品管理</RouterLink>
      <RouterLink to="/categories">分类管理</RouterLink>
      <RouterLink to="/warehouses">仓库管理</RouterLink>
      <RouterLink to="/purchase-orders">采购入库</RouterLink>
      <RouterLink to="/inventory">库存查询</RouterLink>
      <RouterLink to="/">服务状态</RouterLink>
    </nav>
  </header>
  <div class="page-shell"><RouterView /></div>
  </el-config-provider>
</template>

<style>
* { box-sizing: border-box; }
body { margin: 0; font-family: system-ui, sans-serif; color: #273449; background: #f4f6f9; }
.app-header { display: flex; align-items: center; gap: 40px; padding: 0 32px; min-height: 68px; background: #fff; border-bottom: 1px solid #e4e7ed; flex-wrap: wrap; }
.brand { font-size: 22px; font-weight: 750; color: #1c4072; text-decoration: none; }
nav { display: flex; flex-wrap: wrap; gap: 24px; }
nav a { color: #606b7a; text-decoration: none; padding: 22px 0; border-bottom: 2px solid transparent; }
nav a.router-link-exact-active { color: #235bc2; border-color: #235bc2; }
.page-shell { max-width: 1200px; margin: 32px auto; padding: 0 24px; }
.page-title { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; }
h1 { font-size: 26px; margin: 0 0 8px; }
.subtitle { color: #758094; margin: 0; font-size: 14px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.toolbar .el-input { width: 240px; }
.toolbar .el-select { width: 180px; }
.el-pagination { margin-top: 20px; justify-content: flex-end; }
.el-alert { margin-bottom: 16px; }
.form-dialog { width: min(560px, 94vw) !important; }
.el-dialog .el-select { width: 100%; }
@media (max-width: 640px) {
  .app-header { padding: 12px 20px; gap: 8px; }
  nav { gap: 16px; } nav a { padding: 10px 0; font-size: 14px; }
  .page-shell { padding: 0 12px; margin-top: 20px; }
  .page-title { align-items: flex-start; }
}
</style>
