import request from '../utils/request'

export async function getHealth() {
  const { data } = await request.get('/health')
  if (data.code !== 200 || data.data?.status !== 'UP') {
    throw new Error('Unexpected health response')
  }
  return data.data
}
