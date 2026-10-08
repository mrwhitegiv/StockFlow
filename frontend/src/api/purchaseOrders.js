import request from '../utils/request'

const payload = (promise) => promise.then(({ data }) => data.data)
export const purchaseOrdersApi = {
  list: (params) => payload(request.get('/purchase-orders', { params })),
  get: (id) => payload(request.get('/purchase-orders/' + id)),
  create: (body) => payload(request.post('/purchase-orders', body)),
  addItem: (id, body) => payload(request.post('/purchase-orders/' + id + '/items', body)),
  changeQuantity: (id, itemId, body) => payload(request.put('/purchase-orders/' + id + '/items/' + itemId, body)),
  removeItem: (id, itemId) => payload(request.delete('/purchase-orders/' + id + '/items/' + itemId)),
  action: (id, action) => payload(request.post('/purchase-orders/' + id + '/' + action)),
}
export const purchaseStatuses = {
  DRAFT: '草稿', APPROVED: '已审核', RECEIVED: '已收货', COMPLETED: '已完成', CANCELLED: '已取消',
}
