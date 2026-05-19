import request from './request'

export function toggleLike(data) {
  return request.post('/like/toggle', data)
}

export function doubleTap(videoId) {
  return request.post(`/like/double-tap/${videoId}`)
}
