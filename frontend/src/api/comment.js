import request from './request'

export function createComment(data) {
  return request.post('/comment/create', data)
}

export function getCommentList(params) {
  return request.get('/comment/list', { params })
}

export function getCommentReplies(parentId, cursor, cursorId, size) {
  const params = {}
  if (cursor !== null && cursor !== undefined) params.cursor = cursor
  if (cursorId !== null && cursorId !== undefined) params.cursorId = cursorId
  if (size) params.size = size
  return request.get(`/comment/replies/${parentId}`, { params })
}

export function deleteComment(commentId) {
  return request.delete(`/comment/${commentId}`)
}
