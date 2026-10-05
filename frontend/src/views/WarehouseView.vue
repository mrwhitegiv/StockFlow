<script setup>
import { onMounted, reactive, ref, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { warehousesApi as api } from '../api/masterData'
import { errorMessage } from '../utils/errors'

const rows = ref([])
const loading = ref(false)
const error = ref('')
const dialog = ref(false)
const saving = ref(false)
const editingId = ref(null)
const deletingId = ref(null)
const formRef = ref()
const form = reactive({ name: '', code: '', address: '' })
const rules = {
  name: [{ required: true, whitespace: true, message: '请输入仓库名称', trigger: 'blur' }],
  code: [{ required: true, pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: '编码需为 1–64 位字母、数字、点、横线或下划线，以字母或数字开头', trigger: 'blur' }],
}
async function load() {
  loading.value = true
  error.value = ''
  try { rows.value = await api.list() }
  catch (e) { error.value = errorMessage(e) }
  finally { loading.value = false }
}
async function open(row) {
  editingId.value = row?.id ?? null
  Object.assign(form, { name: row?.name ?? '', code: row?.code ?? '', address: row?.address ?? '' })
  dialog.value = true
  await nextTick()
  formRef.value?.clearValidate()
}
async function save() {
  if (saving.value || !(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editingId.value) await api.update(editingId.value, { ...form })
    else await api.create({ ...form })
    dialog.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (e) { ElMessage.error(errorMessage(e)) }
  finally { saving.value = false }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？已有其他数据引用时无法删除。`, '删除仓库', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  } catch { return }
  deletingId.value = row.id
  try { await api.remove(row.id); ElMessage.success('已删除'); await load() }
  catch (e) { ElMessage.error(errorMessage(e)) }
  finally { deletingId.value = null }
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-title"><div><h1>仓库管理</h1><p class="subtitle">维护仓库编码、名称与地址。此处不调整库存。</p></div>
      <el-button type="primary" @click="open()">新增仓库</el-button>
    </div>
    <el-card shadow="never">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div class="toolbar"><el-button :loading="loading" @click="load">刷新列表</el-button></div>
      <el-table :data="rows" v-loading="loading" empty-text="暂无仓库，请点击右上角新增" row-key="id">
        <el-table-column prop="code" label="编码" min-width="180" />
        <el-table-column prop="name" label="仓库名称" min-width="200" />
        <el-table-column prop="address" label="地址" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="160"><template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="danger" :disabled="deletingId !== null" :loading="deletingId === row.id" @click="remove(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialog" :title="editingId ? '编辑仓库' : '新增仓库'" class="form-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="仓库编码" prop="code"><el-input v-model="form.code" maxlength="64" placeholder="例如 WH-GZ" /></el-form-item>
        <el-form-item label="仓库名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="地址（可选）" prop="address"><el-input v-model="form.address" type="textarea" maxlength="255" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
