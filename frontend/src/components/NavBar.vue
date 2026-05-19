<template>
  <header class="navbar">
    <div class="navbar-inner">
      <router-link to="/" class="navbar-logo">
        <svg class="logo-icon" viewBox="0 0 24 24" fill="currentColor" width="32" height="32">
          <path d="M17.813 4.653h.854c1.51.054 2.769.578 3.773 1.574 1.004.995 1.524 2.249 1.56 3.76v7.36c-.036 1.51-.556 2.769-1.56 3.773s-2.262 1.524-3.773 1.56H5.333c-1.51-.036-2.769-.556-3.773-1.56S.036 18.858 0 17.347v-7.36c.036-1.511.556-2.765 1.56-3.76 1.004-.996 2.262-1.52 3.773-1.574h.774l-1.174-1.12a1.234 1.234 0 0 1-.373-.906c0-.356.124-.658.373-.907l.027-.027c.267-.249.573-.373.92-.373.347 0 .653.124.92.373L9.653 4.44c.071.071.134.142.187.213h4.267a.836.836 0 0 1 .16-.213l2.853-2.747c.267-.249.573-.373.92-.373.347 0 .662.151.929.4.267.249.391.551.391.907 0 .355-.124.657-.373.906L17.813 4.653zM5.333 7.24c-.746.018-1.373.276-1.88.773-.506.498-.769 1.13-.786 1.894v7.52c.017.764.28 1.395.786 1.893.507.498 1.134.756 1.88.773h13.334c.746-.017 1.373-.275 1.88-.773.506-.498.769-1.129.786-1.893v-7.52c-.017-.765-.28-1.396-.786-1.894-.507-.497-1.134-.755-1.88-.773zM8 11.107c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c0-.373.129-.689.386-.947.258-.257.574-.386.947-.386zm8 0c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c.017-.391.15-.711.4-.96.249-.249.56-.373.933-.373z"/>
        </svg>
        <span class="logo-text">MyBilibili</span>
      </router-link>

      <nav class="navbar-links">
        <router-link to="/" class="nav-link" :class="{ active: $route.path === '/' }">首页</router-link>
        <router-link to="/feed" class="nav-link" :class="{ active: $route.path === '/feed' }">动态</router-link>
        <router-link to="/coupon" class="nav-link" :class="{ active: $route.path === '/coupon' }">优惠券</router-link>
        <router-link v-if="userStore.isLoggedIn" to="/publish" class="nav-link" :class="{ active: $route.path === '/publish' }">发布</router-link>
      </nav>

      <div class="navbar-search">
        <svg class="search-icon" viewBox="0 0 24 24" width="18" height="18">
          <path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" fill="currentColor"/>
        </svg>
        <input
          v-model="searchText"
          class="search-input"
          type="text"
          placeholder="搜索视频、动态、用户..."
          @keyup.enter="doSearch"
        />
      </div>

      <div class="navbar-right">
        <template v-if="userStore.isLoggedIn">
          <div class="user-menu" @click="showDropdown = !showDropdown" ref="menuRef">
            <img
              v-if="userStore.avatar"
              :src="userStore.avatar"
              class="user-avatar"
              alt="头像"
            />
            <div v-else class="user-avatar-placeholder">
              {{ userStore.nickname?.charAt(0)?.toUpperCase() }}
            </div>
            <span class="user-name">{{ userStore.nickname }}</span>
            <svg class="dropdown-arrow" :class="{ open: showDropdown }" viewBox="0 0 12 12" width="12" height="12">
              <path d="M2 4l4 4 4-4" stroke="currentColor" stroke-width="1.5" fill="none"/>
            </svg>
            <transition name="fade">
              <div v-if="showDropdown" class="dropdown-menu">
                <router-link to="/profile" class="dropdown-item" @click="showDropdown = false">
                  <svg viewBox="0 0 20 20" width="16" height="16"><path d="M10 10a4 4 0 100-8 4 4 0 000 8zm-7 8a7 7 0 0114 0H3z" fill="currentColor"/></svg>
                  个人中心
                </router-link>
                <div class="dropdown-divider"></div>
                <button class="dropdown-item logout" @click="handleLogout">
                  <svg viewBox="0 0 20 20" width="16" height="16"><path d="M3 3h8v2H5v10h6v2H3V3zm10.5 4L17 10l-3.5 3v-2H7v-2h6.5V7z" fill="currentColor"/></svg>
                  退出登录
                </button>
              </div>
            </transition>
          </div>
        </template>
        <template v-else>
          <router-link to="/login" class="btn-login">登录</router-link>
          <router-link to="/register" class="btn-register">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const router = useRouter()
const showDropdown = ref(false)
const menuRef = ref(null)
const searchText = ref('')

function handleClickOutside(e) {
  if (menuRef.value && !menuRef.value.contains(e.target)) {
    showDropdown.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

function doSearch() {
  const q = searchText.value.trim()
  if (q) {
    router.push({ name: 'Search', query: { q } })
  }
}

async function handleLogout() {
  showDropdown.value = false
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: var(--header-height);
  background: var(--bili-white);
  box-shadow: var(--bili-shadow);
  z-index: 1000;
}

.navbar-inner {
  max-width: 1400px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 24px;
}

.navbar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--bili-blue);
  text-decoration: none;
  flex-shrink: 0;
}

.logo-icon {
  color: var(--bili-blue);
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.navbar-links {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.nav-link {
  padding: 8px 16px;
  border-radius: var(--bili-radius);
  color: var(--bili-gray-2);
  font-size: 15px;
  transition: all 0.2s;
  text-decoration: none;
}

.nav-link:hover {
  color: var(--bili-blue);
  background: var(--bili-gray-6);
}

.nav-link.active {
  color: var(--bili-blue);
  font-weight: 600;
}

.navbar-search {
  flex: 1;
  max-width: 400px;
  min-width: 180px;
  display: flex;
  align-items: center;
  background: var(--bili-gray-6);
  border: 1px solid var(--bili-gray-5);
  border-radius: 20px;
  padding: 0 14px;
  height: 36px;
  transition: all 0.2s;
}

.navbar-search:focus-within {
  background: var(--bili-white);
  border-color: var(--bili-blue);
  box-shadow: 0 0 0 2px rgba(251, 114, 153, 0.15);
}

.navbar-search .search-icon {
  color: var(--bili-gray-3);
  flex-shrink: 0;
}

.navbar-search .search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  padding: 6px 8px;
  font-size: 14px;
  color: var(--bili-gray-1);
}

.navbar-search .search-input::placeholder {
  color: var(--bili-gray-4);
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.btn-login {
  padding: 6px 20px;
  border-radius: var(--bili-radius);
  color: var(--bili-blue);
  border: 1px solid var(--bili-blue);
  font-size: 14px;
  transition: all 0.2s;
  text-decoration: none;
}

.btn-login:hover {
  background: var(--bili-blue);
  color: white;
}

.btn-register {
  padding: 6px 20px;
  border-radius: var(--bili-radius);
  background: var(--bili-blue);
  color: white;
  font-size: 14px;
  transition: all 0.2s;
  text-decoration: none;
}

.btn-register:hover {
  background: var(--bili-blue-hover);
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--bili-radius);
  position: relative;
  transition: background 0.2s;
}

.user-menu:hover {
  background: var(--bili-gray-6);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.user-avatar-placeholder {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--bili-blue);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.user-name {
  color: var(--bili-gray-1);
  font-size: 14px;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-arrow {
  color: var(--bili-gray-3);
  transition: transform 0.2s;
}

.dropdown-arrow.open {
  transform: rotate(180deg);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  background: var(--bili-white);
  border-radius: var(--bili-radius);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  min-width: 160px;
  padding: 4px 0;
  z-index: 100;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  color: var(--bili-gray-1);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s;
  width: 100%;
  background: none;
  text-decoration: none;
  text-align: left;
}

.dropdown-item:hover {
  background: var(--bili-gray-6);
  color: var(--bili-blue);
}

.dropdown-item.logout {
  color: var(--bili-gray-3);
}

.dropdown-item.logout:hover {
  color: var(--bili-pink);
  background: #FFF0F5;
}

.dropdown-divider {
  height: 1px;
  background: var(--bili-gray-5);
  margin: 4px 0;
}
</style>
