import request from './request'

export function publishVideo(data) {
  return request.post('/video/publish', data)
}

export function getVideoDetail(videoId) {
  return request.get(`/video/${videoId}`)
}

export function getVideoList(params) {
  return request.get('/video/list', { params })
}

export function getUserVideos(userId, params) {
  return request.get(`/video/user/${userId}`, { params })
}

export function deleteVideo(videoId) {
  return request.delete(`/video/${videoId}`)
}
