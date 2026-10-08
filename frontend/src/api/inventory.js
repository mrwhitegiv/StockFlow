import request from '../utils/request'

export async function getInventory(params) {
  const { data } = await request.get('/inventory', { params })
  return data.data
}
