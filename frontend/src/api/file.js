import axios from 'axios'
import { useUserStore } from '../stores/user'

const uploadClient = axios.create({
  baseURL: '/api',
  timeout: 600000,
})

uploadClient.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

uploadClient.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code !== 0) {
      const error = new Error(data.message || '上传失败')
      error.code = data.code
      return Promise.reject(error)
    }
    return data
  },
  (error) => {
    const message = error.response?.data?.message || '上传失败'
    return Promise.reject(new Error(message))
  }
)

export function uploadVideo(file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadClient.post('/file/upload/video', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
  })
}

export function uploadCover(file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadClient.post('/file/upload/cover', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
  })
}