<script setup>
import { onMounted, reactive, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoriesApi, productsApi } from '../api/masterData'
import { errorMessage } from '../utils/errors'

const router = useRouter()
const categories = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const filters = reactive({ keyword: '', categoryId: undefined, enabled: undefined })
const page = ref(1)
const size = ref(10)
const dialog = ref(false)
const saving = ref(false)
const togglingId = ref(null)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ categoryId: undefined, name: '', description: '' })
const rules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  name: [{ required: true, whitespace: true, message: '请输入商品名称', trigger: 'blur' }],
}
let requestId = 0
async function load() {
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const data = await productsApi.list({ page: page.value, size: size.value, keyword: filters.keyword, categoryId: filters.categoryId || undefined, enabled: typeof filters.enabled === 'boolean' ? filters.enabled : undefined })
    if (current !== requestId) return
    rows.value = data.records
    total.value = data.total
  } catch (e) { if (current === requestId) error.value = errorMessage(e) }
  finally { if (current === requestId) loading.value = false }
}
function search() { page.value = 1; load() }
function reset() { Object.assign(filters, { keyword: '', categoryId: undefined, enabled: undefined }); search() }
async function open(row) {
  try { categories.value = await categoriesApi.list() }
  catch (e) { ElMessage.error(errorMessage(e)); return }
  editingId.value = row?.id ?? null
  Object.assign(form, { categoryId: row?.categoryId, name: row?.name ?? '', description: row?.description ?? '' })
  dialog.value = true
  await nextTick()
  formRef.value?.clearValidate()
}
async function save() {
  if (saving.value || !(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editingId.value) await productsApi.update(editingId.value, { ...form })
    else await productsApi.create({ ...form })
    dialog.value = false
    ElMessage.success('保存成功')
    if (!editingId.value) page.value = 1
    await load()
  } catch (e) { ElMessage.error(errorMessage(e)) }
  finally { saving.value = false }
}
async function toggle(row) {
  const action = row.enabled ? '禁用' : '启用'
  try { await ElMessageBox.confirm(`确认${action}「${row.name}」？禁用后仍可查询，不能新增或修改其 SKU。`, `${action}商品`, { confirmButtonText: action, cancelButtonText: '取消', type: 'warning' }) }
  catch { return }
  togglingId.value = row.id
  try {
    await productsApi.setEnabled(row.id, !row.enabled)
    ElMessage.success(`已${action}`)
    await load()
    if (!rows.value.length && page.value > 1) { page.value--; await load() }
  } catch (e) { ElMessage.error(errorMessage(e)) }
  finally { togglingId.value = null }
}
onMounted(async () => {
  try { categories.value = await categoriesApi.list() }
  catch (e) { ElMessage.error(errorMessage(e)) }
  await load()
})
</script>

<template>
  <section>
    <div class="page-title"><div><h1>商品管理</h1><p class="subtitle">维护商品信息，进入商品管理具体 SKU。</p></div><el-button type="primary" @click="open()">新增商品</el-button></div>
    <el-card shadow="never">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <form class="toolbar" @submit.prevent="search">
        <el-input v-model="filters.keyword" placeholder="搜索商品名称" aria-label="搜索商品名称" maxlength="200" clearable />
        <el-select v-model="filters.categoryId" placeholder="全部分类" aria-label="分类筛选" clearable><el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        <el-select v-model="filters.enabled" placeholder="全部状态" aria-label="状态筛选" clearable><el-option label="启用" :value="true" /><el-option label="禁用" :value="false" /></el-select>
        <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
      <el-table :data="rows" row-key="id" v-loading="loading" empty-text="暂无商品，请新增商品或调整筛选条件">
        <el-table-column prop="name" label="商品名称" min-width="180" />
        <el-table-column label="分类" min-width="140"><template #default="{ row }">{{ categories.find(c => c.id === row.categoryId)?.name ?? row.categoryId }}</template></el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="230"><template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="primary" @click="router.push({ name: 'skus', params: { productId: row.id } })">管理 SKU</el-button>
          <el-button link :type="row.enabled ? 'danger' : 'success'" :disabled="togglingId !== null" :loading="togglingId === row.id" @click="toggle(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    </el-card>
    <el-dialog v-model="dialog" :title="editingId ? '编辑商品' : '新增商品'" class="form-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <el-alert v-if="!categories.length" title="还没有分类，请先在分类管理中新增。" type="info" :closable="false" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="商品名称" prop="name"><el-input v-model="form.name" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="所属分类" prop="categoryId"><el-select v-model="form.categoryId" placeholder="请选择分类" filterable><el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="描述（可选）" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!categories.length" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
