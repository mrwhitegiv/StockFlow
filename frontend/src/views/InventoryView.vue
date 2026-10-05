<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getInventory } from '../api/inventory'
import { warehousesApi } from '../api/masterData'
import { errorMessage } from '../utils/errors'

const warehouses = ref([])
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const error = ref('')
const warehouseError = ref('')
const filters = reactive({ warehouseId: undefined, skuId: '', skuCode: '' })
let requestId = 0

async function load() {
  const current = ++requestId
  const skuId = filters.skuId.trim()
  if (skuId && (!/^[1-9][0-9]{0,18}$/.test(skuId) || BigInt(skuId) > 9223372036854775807n)) {
    rows.value = []
    total.value = 0
    loading.value = false
    error.value = 'SKU ID 必须是有效的正整数；也可以按 SKU 编码查询。'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await getInventory({
      page: page.value, size: size.value, warehouseId: filters.warehouseId || undefined,
      skuId: skuId || undefined, skuCode: filters.skuCode.trim() || undefined,
    })
    if (current !== requestId) return
    rows.value = result.records
    total.value = result.total
  } catch (cause) {
    if (current === requestId) {
      rows.value = []
      total.value = 0
      error.value = errorMessage(cause)
    }
  } finally { if (current === requestId) loading.value = false }
}
async function loadWarehouses() {
  warehouseError.value = ''
  try { warehouses.value = await warehousesApi.list() }
  catch (cause) { warehouseError.value = errorMessage(cause) }
}
function search() { page.value = 1; load() }
function reset() { Object.assign(filters, { warehouseId: undefined, skuId: '', skuCode: '' }); search() }
async function refresh() { await Promise.all([loadWarehouses(), load()]) }
onMounted(refresh)
</script>

<template>
  <section>
    <div class="page-title">
      <div><h1>库存查询</h1><p class="subtitle">按仓库与 SKU 查看实物、锁定及可用数量。</p></div>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </div>
    <el-alert title="只读查询：可用数量 = 实物数量 − 锁定数量。此页面不提供库存修改。" type="info" :closable="false" />
    <el-card shadow="never">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="warehouseError" :title="'仓库选项加载失败：' + warehouseError" type="warning" :closable="false" />
      <form class="toolbar" @submit.prevent="search">
        <el-select v-model="filters.warehouseId" placeholder="全部仓库" aria-label="仓库筛选" clearable filterable>
          <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name + ' · ' + warehouse.code" :value="warehouse.id" />
        </el-select>
        <el-input v-model="filters.skuCode" placeholder="SKU 编码（精确匹配）" aria-label="SKU 编码筛选" maxlength="64" clearable />
        <el-input v-model="filters.skuId" placeholder="SKU ID（可选）" aria-label="SKU ID 筛选" maxlength="19" inputmode="numeric" clearable />
        <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
      <el-table :data="rows" v-loading="loading" row-key="id" :empty-text="error ? '库存未加载，请处理上方提示后重试。' : '没有匹配的库存记录。新建商品或仓库不会自动产生库存。'">
        <el-table-column prop="warehouseName" label="仓库" min-width="125" />
        <el-table-column label="SKU" min-width="210">
          <template #default="{ row }">{{ row.skuCode }}<div class="inventory-detail">ID: {{ row.skuId }}</div></template>
        </el-table-column>
        <el-table-column label="商品 / 规格" min-width="210">
          <template #default="{ row }">{{ row.productName }}<div class="inventory-detail">{{ row.skuName }}</div></template>
        </el-table-column>
        <el-table-column prop="onHandQty" label="实物数量" min-width="180" align="right" class-name="inventory-number" />
        <el-table-column prop="lockedQty" label="锁定数量" min-width="180" align="right" class-name="inventory-number" />
        <el-table-column prop="availableQty" label="可用数量" min-width="180" align="right" class-name="inventory-number" />
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    </el-card>
  </section>
</template>

<style scoped>
.inventory-detail { color: #758094; font-size: 12px; }
:deep(.inventory-number .cell) { white-space: nowrap; font-variant-numeric: tabular-nums; }
</style>
