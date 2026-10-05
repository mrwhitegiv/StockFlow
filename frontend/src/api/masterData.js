import request from '../utils/request'

const payload = (promise) => promise.then(({ data }) => data.data)
export const categoriesApi = {
  list: () => payload(request.get('/categories')),
  create: (body) => payload(request.post('/categories', body)),
  update: (id, body) => payload(request.put(`/categories/${id}`, body)),
  remove: (id) => payload(request.delete(`/categories/${id}`)),
}
export const productsApi = {
  list: (params) => payload(request.get('/products', { params })),
  get: (id) => payload(request.get(`/products/${id}`)),
  create: (body) => payload(request.post('/products', body)),
  update: (id, body) => payload(request.put(`/products/${id}`, body)),
  setEnabled: (id, enabled) => payload(request.patch(`/products/${id}/enabled`, { enabled })),
}
export const skusApi = {
  list: (productId, params) => payload(request.get(`/products/${productId}/skus`, { params })),
  create: (productId, body) => payload(request.post(`/products/${productId}/skus`, body)),
  update: (id, body) => payload(request.put(`/skus/${id}`, body)),
  remove: (id) => payload(request.delete(`/skus/${id}`)),
}
export const warehousesApi = {
  list: () => payload(request.get('/warehouses')),
  create: (body) => payload(request.post('/warehouses', body)),
  update: (id, body) => payload(request.put(`/warehouses/${id}`, body)),
  remove: (id) => payload(request.delete(`/warehouses/${id}`)),
}
