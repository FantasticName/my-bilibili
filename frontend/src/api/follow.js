import request from './request'

export function toggleFollow(followeeId) {
  return request.post(`/follow/toggle/${followeeId}`)
}

export function checkFollow(followeeId) {
  return request.get(`/follow/check/${followeeId}`)
}

export function getFollowingList(userId, params) {
  return request.get(`/follow/following/${userId}`, { params })
}

export function getFollowerList(userId, params) {
  return request.get(`/follow/followers/${userId}`, { params })
}

export function getFollowCount(userId) {
  return request.get(`/follow/count/${userId}`)
}
