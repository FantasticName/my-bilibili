import request from './request'

export function register(data) {
  return request.post('/user/register', data)
}

export function login(data) {
  return request.post('/user/login', data)
}

export function getProfile() {
  return request.post('/user/profile')
}

export function updateProfile(data) {
  return request.put('/user/profile', data)
}

export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/user/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export function logout() {
  return request.post('/user/logout')
}
