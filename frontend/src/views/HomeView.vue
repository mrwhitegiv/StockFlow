<script setup>
import { onMounted } from 'vue'
import { useHealthStore } from '../stores/health'

const health = useHealthStore()
onMounted(() => health.refresh())
</script>

<template>
  <main>
    <el-card>
      <h1>StockFlow</h1>
      <p role="status" aria-live="polite">Backend Status: {{ health.status }}</p>
      <el-alert v-if="health.error" :title="health.error" type="error" :closable="false" />
      <el-button type="primary" :loading="health.loading" @click="health.refresh">
        重新检查
      </el-button>
    </el-card>
  </main>
</template>

<style scoped>
main {
  max-width: 640px;
  margin: 64px auto;
  padding: 0 20px;
}
.el-button {
  margin-top: 16px;
}
</style>
