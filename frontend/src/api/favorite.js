import request from './request'

export function createFolder(name) {
  return request.post('/favorite/folder/create', { name })
}

export function getFolders() {
  return request.get('/favorite/folders')
}

export function getFoldersWithStatus(targetType, targetId) {
  return request.get('/favorite/folders-with-status', { params: { targetType, targetId } })
}

export function getFoldersWithCount() {
  return request.get('/favorite/folders-with-count')
}

export function renameFolder(folderId, name) {
  return request.put('/favorite/folder/rename', { folderId, name })
}

export function deleteFolder(folderId) {
  return request.delete(`/favorite/folder/${folderId}`)
}

export function toggleFavorite(data) {
  return request.post('/favorite/toggle', data)
}

export function batchUpdateFavorite(data) {
  return request.post('/favorite/batch-update', data)
}

export function getFavoriteVideos(folderId, params) {
  return request.get(`/favorite/videos/${folderId}`, { params })
}
