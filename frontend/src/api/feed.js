import request from './request'

export function getFeed(params) {
  return request.get('/feed/following', { params })
}
