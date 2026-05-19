import request from './request'

/**
 * 发布动态（multipart/form-data，支持多图上传）
 *
 * @param {FormData} formData - 包含content(可选)和images(可选，多文件)
 * @returns {Promise}
 */
export function createPost(formData) {
  return request.post('/post', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 获取用户动态列表（游标分页）
 *
 * @param {number} userId - 用户ID
 * @param {Object} params - 查询参数 { cursor, size }
 * @returns {Promise}
 */
export function getUserPosts(userId, params) {
  return request.get(`/post/user/${userId}`, { params })
}

/**
 * 获取动态详情
 *
 * @param {number} postId - 动态ID
 * @returns {Promise}
 */
export function getPostDetail(postId) {
  return request.get(`/post/${postId}`)
}

/**
 * 删除动态
 *
 * @param {number} postId - 动态ID
 * @returns {Promise}
 */
export function deletePost(postId) {
  return request.delete(`/post/${postId}`)
}

/**
 * 编辑动态（multipart/form-data，支持修改文本和图片）
 *
 * @param {number} postId - 动态ID
 * @param {FormData} formData - 包含content(可选)、existingImages(逗号分隔)、images(新文件)
 * @returns {Promise}
 */
export function updatePost(postId, formData) {
  return request.post(`/post/${postId}/edit`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
