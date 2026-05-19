import axios from 'axios'
import { useUserStore } from '../stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code !== 0) {
      const error = new Error(data.message || '请求失败')
      error.code = data.code
      return Promise.reject(error)
    }
    return data
  },
  (error) => {
    if (error.response) {
      const data = error.response.data
      const message = data?.message || `服务器错误 (${error.response.status})`
      const err = new Error(message)
      err.code = data?.code || error.response.status
      if (error.response.status === 401) {
        useUserStore().logout()
        window.location.href = '/login'
      }
      return Promise.reject(err)
    }
    return Promise.reject(new Error('网络连接失败，请检查网络'))
  },
)

export default request
