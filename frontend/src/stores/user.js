import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, logout as logoutApi, getProfile as getProfileApi } from '../api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)
  const nickname = computed(() => userInfo.value?.nickname || '')
  const avatar = computed(() => {
    const a = userInfo.value?.avatar
    return a || ''
  })

  async function login(loginForm) {
    const res = await loginApi(loginForm)
    token.value = res.data.token
    localStorage.setItem('token', res.data.token)
    userInfo.value = res.data.user
  }

  async function fetchProfile() {
    if (!token.value) return
    try {
      const res = await getProfileApi()
      userInfo.value = res.data
    } catch {
      logout()
    }
  }

  function logout() {
    if (token.value) {
      logoutApi().catch(() => {})
    }
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  function setUserInfo(info) {
    userInfo.value = info
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    nickname,
    avatar,
    login,
    fetchProfile,
    logout,
    setUserInfo,
  }
})
