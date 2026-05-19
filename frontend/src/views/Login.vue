<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <svg class="login-logo" viewBox="0 0 24 24" fill="currentColor" width="48" height="48">
          <path d="M17.813 4.653h.854c1.51.054 2.769.578 3.773 1.574 1.004.995 1.524 2.249 1.56 3.76v7.36c-.036 1.51-.556 2.769-1.56 3.773s-2.262 1.524-3.773 1.56H5.333c-1.51-.036-2.769-.556-3.773-1.56S.036 18.858 0 17.347v-7.36c.036-1.511.556-2.765 1.56-3.76 1.004-.996 2.262-1.52 3.773-1.574h.774l-1.174-1.12a1.234 1.234 0 0 1-.373-.906c0-.356.124-.658.373-.907l.027-.027c.267-.249.573-.373.92-.373.347 0 .653.124.92.373L9.653 4.44c.071.071.134.142.187.213h4.267a.836.836 0 0 1 .16-.213l2.853-2.747c.267-.249.573-.373.92-.373.347 0 .662.151.929.4.267.249.391.551.391.907 0 .355-.124.657-.373.906L17.813 4.653zM5.333 7.24c-.746.018-1.373.276-1.88.773-.506.498-.769 1.13-.786 1.894v7.52c.017.764.28 1.395.786 1.893.507.498 1.134.756 1.88.773h13.334c.746-.017 1.373-.275 1.88-.773.506-.498.769-1.129.786-1.893v-7.52c-.017-.765-.28-1.396-.786-1.894-.507-.497-1.134-.755-1.88-.773zM8 11.107c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c0-.373.129-.689.386-.947.258-.257.574-.386.947-.386zm8 0c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c.017-.391.15-.711.4-.96.249-.249.56-.373.933-.373z"/>
        </svg>
        <h1 class="login-title">登录</h1>
        <p class="login-subtitle">欢迎回到 MyBilibili</p>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label class="form-label">手机号</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 20 20" width="18" height="18">
              <path d="M2 4a2 2 0 012-2h12a2 2 0 012 2v12a2 2 0 01-2 2H4a2 2 0 01-2-2V4zm6 6a2 2 0 104 0 2 2 0 00-4 0zm2 4c-2.667 0-4 1.333-4 2v1h8v-1c0-.667-1.333-2-4-2z" fill="currentColor"/>
            </svg>
            <input
              v-model="form.phone"
              type="tel"
              class="form-input"
              placeholder="请输入手机号"
              maxlength="11"
            />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">密码</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 20 20" width="18" height="18">
              <path d="M10 2a4 4 0 00-4 4v2H5a1 1 0 00-1 1v8a1 1 0 001 1h10a1 1 0 001-1V9a1 1 0 00-1-1h-1V6a4 4 0 00-4-4zm-2 4a2 2 0 114 0v2H8V6zm2 6a1 1 0 011 1v2a1 1 0 11-2 0v-2a1 1 0 011-1z" fill="currentColor"/>
            </svg>
            <input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              class="form-input"
              placeholder="请输入密码"
            />
            <button type="button" class="toggle-password" @click="showPassword = !showPassword">
              <svg v-if="showPassword" viewBox="0 0 20 20" width="18" height="18">
                <path d="M10 3C5 3 1.73 7.11 1 10c.73 2.89 4 7 9 7s8.27-4.11 9-7c-.73-2.89-4-7-9-7zm0 12a5 5 0 110-10 5 5 0 010 10zm0-8a3 3 0 100 6 3 3 0 000-6z" fill="currentColor"/>
              </svg>
              <svg v-else viewBox="0 0 20 20" width="18" height="18">
                <path d="M10 3C5 3 1.73 7.11 1 10c.73 2.89 4 7 9 7s8.27-4.11 9-7c-.73-2.89-4-7-9-7zm-1 12.32A7.96 7.96 0 012.29 10C3.3 7.29 6.24 4.5 10 4.5c.91 0 1.78.15 2.59.42L11.1 6.4A3 3 0 006.4 11.1l-2.08 2.08A10.1 10.1 0 013 10c.73-2.11 3.56-6 7-6 .91 0 1.78.15 2.59.42L14.1 3.9A7.96 7.96 0 0010 3zm5.6 4.6L13.5 14.6A3 3 0 018.9 10l-2.5 2.5A7.96 7.96 0 0010 15c1.3 0 2.52-.36 3.58-.98l1.42 1.42A9.94 9.94 0 0110 17c-5 0-8.27-4.11-9-7a10.1 10.1 0 012.32-3.18L3.57 7.07A9.94 9.94 0 0010 3c5 0 8.27 4.11 9 7a10.1 10.1 0 01-3.4 3.92z" fill="currentColor"/>
              </svg>
            </button>
          </div>
        </div>

        <button type="submit" class="btn-submit" :disabled="loading">
          <span v-if="loading" class="loading-spinner"></span>
          <span v-else>登 录</span>
        </button>
      </form>

      <div class="login-footer">
        <span class="footer-text">还没有账号？</span>
        <router-link to="/register" class="footer-link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useMessage } from '../composables/useMessage'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const msg = useMessage()

const form = reactive({
  phone: '',
  password: '',
})

const showPassword = ref(false)
const loading = ref(false)

async function handleLogin() {
  if (!form.phone.trim()) {
    msg.error('请输入手机号')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    msg.error('请输入正确的手机号')
    return
  }
  if (!form.password) {
    msg.error('请输入密码')
    return
  }
  if (form.password.length < 6) {
    msg.error('密码长度不能少于6位')
    return
  }

  loading.value = true
  try {
    await userStore.login(form)
    msg.success('登录成功！')
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    msg.error(e.message || '登录失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: calc(100vh - var(--header-height));
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: linear-gradient(135deg, #E3F2FD 0%, #FCE4EC 100%);
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--bili-white);
  border-radius: var(--bili-radius-lg);
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
  padding: 48px 40px;
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-logo {
  color: var(--bili-blue);
  margin-bottom: 12px;
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--bili-gray-1);
  margin-bottom: 4px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--bili-gray-3);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--bili-gray-2);
}

.input-wrapper {
  display: flex;
  align-items: center;
  border: 1px solid var(--bili-gray-5);
  border-radius: var(--bili-radius);
  padding: 0 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: var(--bili-white);
}

.input-wrapper:focus-within {
  border-color: var(--bili-blue);
  box-shadow: 0 0 0 3px rgba(0, 161, 214, 0.1);
}

.input-icon {
  color: var(--bili-gray-4);
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  height: 44px;
  padding: 0 12px;
  font-size: 14px;
  color: var(--bili-gray-1);
  background: transparent;
}

.form-input::placeholder {
  color: var(--bili-gray-4);
}

.toggle-password {
  background: none;
  padding: 4px;
  color: var(--bili-gray-4);
  display: flex;
  align-items: center;
  transition: color 0.2s;
}

.toggle-password:hover {
  color: var(--bili-gray-2);
}

.btn-submit {
  width: 100%;
  height: 48px;
  background: var(--bili-blue);
  color: white;
  border-radius: var(--bili-radius);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  transition: background 0.2s, transform 0.1s;
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-submit:hover:not(:disabled) {
  background: var(--bili-blue-hover);
}

.btn-submit:active:not(:disabled) {
  transform: scale(0.98);
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--bili-gray-5);
}

.footer-text {
  color: var(--bili-gray-3);
  font-size: 14px;
}

.footer-link {
  color: var(--bili-blue);
  font-size: 14px;
  font-weight: 500;
  margin-left: 4px;
}

.footer-link:hover {
  color: var(--bili-blue-hover);
}
</style>
