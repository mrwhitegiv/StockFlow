<script setup>
import { onMounted, reactive, ref, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoriesApi as api } from '../api/masterData'
import { errorMessage } from '../utils/errors'

const rows = ref([])
const loading = ref(false)
const error = ref('')
const dialog = ref(false)
const saving = ref(false)
const editingId = ref(null)
const deletingId = ref(null)
const formRef = ref()
const form = reactive({ name: '' })
const rules = {
  name: [{ required: true, whitespace: true, message: '请输入分类名称', trigger: 'blur' }],

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
  Object.assign(form, { name: row?.name ?? '' })
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
    await ElMessageBox.confirm(`确认删除「${row.name}」？已有其他数据引用时无法删除。`, '删除分类', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
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
    <div class="page-title"><div><h1>分类管理</h1><p class="subtitle">先建立分类，再为分类添加商品。</p></div>
      <el-button type="primary" @click="open()">新增分类</el-button>
    </div>
    <el-card shadow="never">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div class="toolbar"><el-button :loading="loading" @click="load">刷新列表</el-button></div>
      <el-table :data="rows" v-loading="loading" empty-text="暂无分类，请点击右上角新增" row-key="id">

        <el-table-column prop="name" label="分类名称" min-width="200" />

        <el-table-column label="操作" width="160"><template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="danger" :disabled="deletingId !== null" :loading="deletingId === row.id" @click="remove(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialog" :title="editingId ? '编辑分类' : '新增分类'" class="form-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">

        <el-form-item label="分类名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit /></el-form-item>

      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
