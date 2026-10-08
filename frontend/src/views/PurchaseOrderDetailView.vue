<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { purchaseOrdersApi, purchaseStatuses } from '../api/purchaseOrders'
import { errorMessage } from '../utils/errors'

const route = useRoute()
const detail = ref(null)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const dialog = ref(false)
const editId = ref(null)
const form = reactive({ skuCode: '', quantity: '1' })
const isDraft = computed(() => detail.value?.order.status === 'DRAFT')
let requestId = 0

async function load() {
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const result = await purchaseOrdersApi.get(route.params.id)
    if (current === requestId) detail.value = result
  } catch (cause) {
    if (current === requestId) { detail.value = null; error.value = errorMessage(cause) }
  } finally { if (current === requestId) loading.value = false }
}
watch(() => route.params.id, () => { detail.value = null; dialog.value = false; load() }, { immediate: true })
function openItem(item) {
  editId.value = item?.id || null
  Object.assign(form, { skuCode: item?.skuCode || '', quantity: item?.quantity || '1' })
  dialog.value = true
}
async function mutate(operation) {
  if (busy.value || loading.value) return false
  const id = route.params.id
  busy.value = true
  error.value = ''
  try {
    const result = await operation(id)
    if (id === route.params.id) detail.value = result
    return true
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
    // A concurrent user or a lost response may have changed the order. Re-read before retrying.
    if (id === route.params.id) await load()
    return false
  } finally { busy.value = false }
}
async function saveItem() {
  const quantity = form.quantity.trim()
  if (!/^[1-9][0-9]{0,18}$/.test(quantity) || BigInt(quantity) > 9223372036854775807n) {
    ElMessage.warning('数量必须是 1 到 9223372036854775807 之间的整数')
    return
  }
  if (!form.skuCode.trim()) { ElMessage.warning('请输入 SKU 编码'); return }
  const ok = await mutate(id => editId.value
    ? purchaseOrdersApi.changeQuantity(id, editId.value, { quantity })
    : purchaseOrdersApi.addItem(id, { skuCode: form.skuCode.trim(), quantity }))
  if (ok) { dialog.value = false; ElMessage.success('采购明细已保存') }
}
async function remove(item) {
  try { await ElMessageBox.confirm('移除明细 ' + item.skuCode + '？', '移除明细', { confirmButtonText: '移除', cancelButtonText: '返回' }) }
  catch { return }
  await mutate(id => purchaseOrdersApi.removeItem(id, item.id))
}
const actions = {
  approve: ['审核采购单', '审核后不能修改明细，也不能取消。确认数量和收货仓库无误后继续。'],
  receive: ['确认收货', '将按全部明细一次性增加实物库存并生成流水，不支持部分收货。确认货物已全部收到？'],
  complete: ['完成采购单', '此操作只结束采购流程，不会再次增加库存。'],
  cancel: ['取消采购单', '草稿取消后不能恢复，不会产生库存变化。'],
}
async function act(action) {
  const [title, message] = actions[action]
  try { await ElMessageBox.confirm(message, title, { confirmButtonText: '确认', cancelButtonText: '返回', type: 'warning' }) }
  catch { return }
  if (await mutate(id => purchaseOrdersApi.action(id, action))) ElMessage.success('操作成功')
}
</script>

<template>
  <section>
    <div class="page-title">
      <div><h1>采购单详情</h1><p class="subtitle">草稿 → 已审核 → 已收货 → 已完成；仅草稿可取消。</p></div>
      <div><RouterLink to="/purchase-orders">返回列表</RouterLink><el-button class="refresh" :disabled="busy" :loading="loading" @click="load">刷新</el-button></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="detail" v-loading="loading">
      <el-card shadow="never">
        <div class="order-heading"><strong>{{ detail.order.orderNo }}</strong><el-tag>{{ purchaseStatuses[detail.order.status] }}</el-tag></div>
        <p>收货仓库：{{ detail.order.warehouseName }}（{{ detail.order.warehouseCode }}）</p>
        <p class="subtitle">创建人：{{ detail.order.creatorName }} · 创建时间：{{ detail.order.createdAt }} UTC</p>
        <p v-if="detail.order.remark" class="remark">备注：{{ detail.order.remark }}</p>
        <div class="actions">
          <template v-if="isDraft">
            <el-button type="primary" :disabled="busy || loading || detail.items.length >= 100" @click="openItem()">添加明细</el-button>
            <el-button type="success" :disabled="busy || loading || !detail.items.length" @click="act('approve')">审核采购单</el-button>
            <el-button type="danger" plain :disabled="busy || loading" @click="act('cancel')">取消采购单</el-button>
          </template>
          <el-button v-if="detail.order.status === 'APPROVED'" type="primary" :loading="busy" :disabled="loading" @click="act('receive')">确认收货</el-button>
          <el-button v-if="detail.order.status === 'RECEIVED'" type="success" :loading="busy" :disabled="loading" @click="act('complete')">完成采购单</el-button>
          <RouterLink to="/inventory">查看库存</RouterLink>
        </div>
        <el-table :data="detail.items" row-key="id" empty-text="还没有采购明细，请添加 SKU 和数量。">
          <el-table-column prop="skuCode" label="SKU 编码" min-width="165" />
          <el-table-column prop="productName" label="商品" min-width="150" />
          <el-table-column prop="skuName" label="规格" min-width="150" />
          <el-table-column prop="quantity" label="采购数量" min-width="200" align="right" />
          <el-table-column v-if="isDraft" label="操作" width="135"><template #default="{ row }">
            <el-button link type="primary" :disabled="busy || loading" @click="openItem(row)">修改</el-button>
            <el-button link type="danger" :disabled="busy || loading" @click="remove(row)">移除</el-button>
          </template></el-table-column>
        </el-table>
      </el-card>
      <el-card class="ledger" shadow="never">
        <h2>收货流水</h2>
        <p class="subtitle">每条流水记录一次实物库存变化，保存后不可修改或删除。时间以 UTC 显示。</p>
        <el-table :data="detail.transactions" row-key="id" empty-text="尚未收货，无库存流水。">
          <el-table-column prop="skuCode" label="SKU" min-width="160" />
          <el-table-column prop="operationType" label="操作" min-width="140" />
          <el-table-column prop="quantityBefore" label="变化前" min-width="195" align="right" />
          <el-table-column prop="quantityChange" label="本次入库" min-width="195" align="right" />
          <el-table-column prop="quantityAfter" label="变化后" min-width="195" align="right" />
          <el-table-column prop="operatorName" label="操作人" min-width="145" />
          <el-table-column prop="createdAt" label="时间（UTC）" min-width="215" />
        </el-table>
      </el-card>
    </div>
    <el-dialog v-model="dialog" :title="editId ? '修改采购数量' : '添加采购明细'" class="form-dialog" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy">
      <el-form label-position="top" @submit.prevent="saveItem">
        <el-form-item label="SKU 编码" required><el-input v-model="form.skuCode" aria-label="采购 SKU 编码" placeholder="从商品的 SKU 管理页复制，区分大小写" maxlength="64" :disabled="busy || !!editId" /></el-form-item>
        <el-form-item label="采购数量（整数）" required><el-input v-model="form.quantity" aria-label="采购数量" inputmode="numeric" maxlength="19" :disabled="busy" /></el-form-item>
        <p class="subtitle">同一 SKU 只能添加一行；每张单最多 100 行。当前不支持部分收货。</p>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="dialog = false">返回</el-button><el-button type="primary" :loading="busy" @click="saveItem">保存明细</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.refresh { margin-left: 16px; }
.order-heading, .actions { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; }
.order-heading strong { overflow-wrap: anywhere; }
.actions { margin: 20px 0; }
.actions .el-button + .el-button { margin-left: 0; }
.ledger { margin-top: 20px; }
h2 { font-size: 18px; margin: 0 0 12px; }
.remark { white-space: pre-wrap; overflow-wrap: anywhere; }
:deep(.el-table .cell) { font-variant-numeric: tabular-nums; }
</style>
