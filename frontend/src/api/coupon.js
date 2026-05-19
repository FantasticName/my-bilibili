import request from './request'

/**
 * 获取进行中的优惠券活动列表（用户抢购页面使用）
 */
export function listActiveActivities() {
  return request.get('/coupon/activities')
}

/**
 * 获取活动详情
 */
export function getActivityDetail(id) {
  return request.get(`/coupon/activity/${id}`)
}

/**
 * 获取幂等性Token（抢购前调用，放在请求Header中）
 */
export function getToken() {
  return request.get('/coupon/token')
}

/**
 * 抢购优惠券（核心秒杀接口）
 */
export function grabCoupon(activityId, idempotentToken) {
  const config = {}
  if (idempotentToken) {
    config.headers = { 'X-Idempotent-Token': idempotentToken }
  }
  return request.post(`/coupon/grab?activityId=${activityId}`, null, config)
}

/**
 * 获取用户抢购记录
 */
export function getRecords(activityId) {
  const params = activityId ? { activityId } : {}
  return request.get('/coupon/records', { params })
}

/**
 * 创建优惠券活动（管理员接口）
 */
export function createActivity(data) {
  return request.post('/coupon/create', data)
}

/**
 * 发布/启动优惠券活动（管理员接口）
 */
export function startActivity(id) {
  return request.post(`/coupon/activity/${id}/start`)
}

/**
 * 查询所有活动（管理员接口，包括未发布的）
 */
export function listAllActivities() {
  return request.get('/coupon/all')
}

export default {
  listActiveActivities,
  getActivityDetail,
  getToken,
  grabCoupon,
  getRecords,
  createActivity,
  startActivity,
  listAllActivities,
}