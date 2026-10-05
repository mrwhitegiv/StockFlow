<script setup>
import { reactive, ref, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productsApi, skusApi } from '../api/masterData'
import { errorMessage } from '../utils/errors'

const route = useRoute()
const product = ref(null)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const dialog = ref(false)
const saving = ref(false)
const deletingId = ref(null)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ code: '', name: '' })
const rules = {
  code: [{ required: true, pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: '请输入 1–64 位编码，以字母或数字开头，仅含字母、数字、点、横线或下划线', trigger: 'blur' }],
  name: [{ required: true, whitespace: true, message: '请输入规格名称', trigger: 'blur' }],
}
let requestId = 0
async function load() {
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const [parent, data] = await Promise.all([
      productsApi.get(route.params.productId),
      skusApi.list(route.params.productId, { page: page.value, size: size.value, keyword: keyword.value }),
    ])
    if (current !== requestId) return
    product.value = parent
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    if (current === requestId) { product.value = null; rows.value = []; total.value = 0; error.value = errorMessage(e) }
  } finally { if (current === requestId) loading.value = false }
}
function search() { page.value = 1; load() }
async function open(row) {
  editingId.value = row?.id ?? null
  Object.assign(form, { code: row?.code ?? '', name: row?.name ?? '' })
  dialog.value = true
  await nextTick()
  formRef.value?.clearValidate()
}
async function save() {
  if (saving.value || !(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editingId.value) await skusApi.update(editingId.value, { ...form })
    else await skusApi.create(route.params.productId, { ...form })
    dialog.value = false
    ElMessage.success('保存成功')
    if (!editingId.value) page.value = 1
    await load()
  } catch (e) { ElMessage.error(errorMessage(e)) }
  finally { saving.value = false }
}
async function remove(row) {
  try { await ElMessageBox.confirm(`确认删除 SKU「${row.code}」？已被库存或单据引用时无法删除。`, '删除 SKU', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) }
  catch { return }
  deletingId.value = row.id
  try {
    await skusApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
    if (!rows.value.length && page.value > 1) { page.value--; await load() }
  } catch (e) { ElMessage.error(errorMessage(e)) }
  finally { deletingId.value = null }
}
watch(() => route.params.productId, () => {
  product.value = null
  page.value = 1
  keyword.value = ''
  dialog.value = false
  load()
}, { immediate: true })
</script>

<template>
  <section>
    <div class="page-title"><div><el-button link type="primary" @click="$router.push('/products')">← 返回商品列表</el-button><h1>{{ product?.name ?? '商品' }} · SKU</h1><p class="subtitle">每个 SKU 是一种具体规格，编码在所有商品之间唯一。</p></div>
      <el-button type="primary" :disabled="!product?.enabled" @click="open()">新增 SKU</el-button>
    </div>
    <el-alert v-if="product && !product.enabled" title="商品已禁用，暂不能新增或修改 SKU。可返回商品列表重新启用。" type="warning" :closable="false" />
    <el-card shadow="never">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <form class="toolbar" @submit.prevent="search"><el-input v-model="keyword" placeholder="搜索编码或规格名称" aria-label="搜索 SKU" maxlength="200" clearable /><el-button type="primary" native-type="submit" :loading="loading">查询</el-button></form>
      <el-table :data="rows" v-loading="loading" row-key="id" empty-text="暂无 SKU">
        <el-table-column prop="code" label="SKU 编码" min-width="180" />
        <el-table-column prop="name" label="规格名称" min-width="220" />
        <el-table-column label="操作" width="160"><template #default="{ row }">
          <el-button link type="primary" :disabled="!product?.enabled" @click="open(row)">编辑</el-button>
          <el-button link type="danger" :disabled="deletingId !== null" :loading="deletingId === row.id" @click="remove(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    </el-card>
    <el-dialog v-model="dialog" :title="editingId ? '编辑 SKU' : '新增 SKU'" class="form-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="SKU 编码" prop="code"><el-input v-model="form.code" maxlength="64" placeholder="例如 PHONE-BLACK-256" /></el-form-item>
        <el-form-item label="规格名称" prop="name"><el-input v-model="form.name" maxlength="200" show-word-limit placeholder="例如 黑色 / 256GB" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
