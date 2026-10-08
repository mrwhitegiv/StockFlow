<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { purchaseOrdersApi, purchaseStatuses } from '../api/purchaseOrders'
import { warehousesApi } from '../api/masterData'
import { errorMessage } from '../utils/errors'

const router = useRouter()
const warehouses = ref([])
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const warehouseError = ref('')
const dialog = ref(false)
const filters = reactive({ warehouseId: undefined, status: '', orderNo: '' })
const form = reactive({ warehouseId: undefined, remark: '' })
let requestId = 0

async function load() {
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const result = await purchaseOrdersApi.list({
      page: page.value, size: size.value, warehouseId: filters.warehouseId || undefined,
      status: filters.status || undefined, orderNo: filters.orderNo.trim() || undefined,
    })
    if (current !== requestId) return
    rows.value = result.records
    total.value = result.total
  } catch (cause) {
    if (current === requestId) { rows.value = []; total.value = 0; error.value = errorMessage(cause) }
  } finally { if (current === requestId) loading.value = false }
}
async function refresh() {
  warehouseError.value = ''
  await Promise.all([load(), warehousesApi.list().then(result => { warehouses.value = result })
    .catch(cause => { warehouseError.value = errorMessage(cause) })])
}
function search() { page.value = 1; load() }
function reset() { Object.assign(filters, { warehouseId: undefined, status: '', orderNo: '' }); search() }
function openCreate() {
  Object.assign(form, { warehouseId: undefined, remark: '' })
  dialog.value = true
}
async function create() {
  if (!form.warehouseId) { ElMessage.warning('请选择收货仓库'); return }
  saving.value = true
  try {
    const detail = await purchaseOrdersApi.create({ ...form })
    dialog.value = false
    await router.push('/purchase-orders/' + detail.order.id)
  } catch (cause) { ElMessage.error(errorMessage(cause)) }
  finally { saving.value = false }
}
onMounted(refresh)
</script>

<template>
  <section>
    <div class="page-title">
      <div><h1>采购入库</h1><p class="subtitle">创建采购单、审核明细，再一次性收货入库。</p></div>
      <div><el-button :loading="loading" @click="refresh">刷新</el-button><el-button type="primary" @click="openCreate">新建采购单</el-button></div>
    </div>
    <el-alert title="本地开发模式：操作记录统一归属“本地开发操作者”，尚未接入登录。" type="info" :closable="false" />
    <el-card shadow="never">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="warehouseError" :title="'仓库加载失败：' + warehouseError" type="warning" :closable="false" />
      <form class="toolbar" @submit.prevent="search">
        <el-select v-model="filters.warehouseId" placeholder="全部仓库" aria-label="采购仓库筛选" clearable filterable>
          <el-option v-for="w in warehouses" :key="w.id" :label="w.name + ' · ' + w.code" :value="w.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" aria-label="采购状态筛选" clearable>
          <el-option v-for="(label, value) in purchaseStatuses" :key="value" :label="label" :value="value" />
        </el-select>
        <el-input v-model="filters.orderNo" placeholder="采购单号（精确匹配）" aria-label="采购单号筛选" maxlength="64" clearable />
        <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
      <el-table :data="rows" v-loading="loading" row-key="id" :empty-text="error ? '采购单未加载，请重试。' : '暂无采购单，点击右上角新建。'">
        <el-table-column prop="orderNo" label="采购单号" min-width="335" />
        <el-table-column prop="warehouseName" label="收货仓库" min-width="130" />
        <el-table-column label="状态" min-width="100"><template #default="{ row }"><el-tag>{{ purchaseStatuses[row.status] }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间（UTC）" min-width="215" />
        <el-table-column label="操作" width="85"><template #default="{ row }"><el-button link type="primary" @click="router.push('/purchase-orders/' + row.id)">详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    </el-card>
    <el-dialog v-model="dialog" title="新建采购单" class="form-dialog" :close-on-click-modal="false" :show-close="!saving" :close-on-press-escape="!saving">
      <el-form label-position="top" @submit.prevent="create">
        <el-form-item label="收货仓库" required>
          <el-select v-model="form.warehouseId" aria-label="收货仓库" filterable :disabled="saving">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name + ' · ' + w.code" :value="w.id" />
          </el-select>
        </el-form-item>
        <p v-if="!warehouses.length" class="subtitle">请先在仓库管理中创建仓库，再刷新此页面。</p>
        <el-form-item label="备注"><el-input v-model="form.remark" aria-label="采购备注" type="textarea" maxlength="500" show-word-limit :disabled="saving" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="create">创建草稿</el-button></template>
    </el-dialog>
  </section>
</template>
