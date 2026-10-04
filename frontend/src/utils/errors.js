export function errorMessage(error) {
  if (error.response?.data?.message) return error.response.data.message
  return '请求失败，请确认后端和 MySQL 已启动后重试。'
}
