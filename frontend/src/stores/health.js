import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getHealth } from '../api/health'

export const useHealthStore = defineStore('health', () => {
  const status = ref('UNKNOWN')
  const loading = ref(false)
  const error = ref('')

  async function refresh() {
    if (loading.value) return
    loading.value = true
    status.value = 'CHECKING'
    error.value = ''
    try {
      status.value = (await getHealth()).status
    } catch (cause) {
      status.value = 'DOWN'
      error.value = cause.response?.status === 503
        ? '后端已响应，但 MySQL 连接失败。请检查数据库服务和连接配置。'
        : '无法获取后端状态，请检查后端服务、API 地址和跨域配置。'
    } finally {
      loading.value = false
    }
  }

  return { status, loading, error, refresh }
})
