<template>
  <div class="profile-page">
    <div class="page-container">
      <div class="profile-layout">
        <div class="profile-sidebar">
          <div class="avatar-section">
            <div class="avatar-wrapper" @click="triggerUpload">
              <img v-if="avatarUrl" :src="avatarUrl" class="avatar-img" alt="头像" />
              <div v-else class="avatar-placeholder">
                {{ userStore.nickname?.charAt(0)?.toUpperCase() }}
              </div>
              <div class="avatar-overlay">
                <svg viewBox="0 0 20 20" width="24" height="24">
                  <path d="M4 5a2 2 0 012-2h8a2 2 0 012 2v10a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm4 1a2 2 0 100 4 2 2 0 000-4zm-2 8a3 3 0 016 0H6z" fill="currentColor"/>
                </svg>
              </div>
            </div>
            <input ref="fileInput" type="file" accept="image/*" class="hidden-input" @change="handleAvatarChange" />
            <h2 class="profile-username">{{ userStore.userInfo?.nickname || '用户' }}</h2>
            <p class="profile-role">{{ roleText }}</p>
          </div>

          <nav class="sidebar-nav">
            <button class="nav-item" :class="{ active: activeTab === 'info' }" @click="activeTab = 'info'">
              <svg viewBox="0 0 20 20" width="18" height="18"><path d="M10 10a4 4 0 100-8 4 4 0 000 8zm-7 8a7 7 0 0114 0H3z" fill="currentColor"/></svg>
              个人信息
            </button>
            <button class="nav-item" :class="{ active: activeTab === 'security' }" @click="activeTab = 'security'">
              <svg viewBox="0 0 20 20" width="18" height="18"><path d="M10 2a4 4 0 00-4 4v2H5a1 1 0 00-1 1v8a1 1 0 001 1h10a1 1 0 001-1V9a1 1 0 00-1-1h-1V6a4 4 0 00-4-4z" fill="currentColor"/></svg>
              修改密码
            </button>
            <router-link :to="`/user/${userStore.userInfo?.id}`" class="nav-item">
              <svg viewBox="0 0 20 20" width="18" height="18"><path d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zm0 6a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1v-2zm0 6a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1v-2z" fill="currentColor"/></svg>
              我的主页
            </router-link>
            <router-link to="/publish/post" class="nav-item">
              <svg viewBox="0 0 20 20" width="18" height="18"><path d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" fill="currentColor"/></svg>
              发布动态
            </router-link>
          </nav>
        </div>

        <div class="profile-main">
          <div v-if="activeTab === 'info'" class="info-panel">
            <h3 class="panel-title">个人信息</h3>
            <div v-if="!editingInfo" class="info-display">
              <div class="info-row">
                <span class="info-label">昵称</span>
                <span class="info-value">{{ userStore.userInfo?.nickname || '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">手机号</span>
                <span class="info-value">{{ maskPhone(userStore.userInfo?.phone) }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">角色</span>
                <span class="info-value">{{ roleText }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">注册时间</span>
                <span class="info-value">{{ userStore.userInfo?.createdAt || '-' }}</span>
              </div>
              <button class="btn-edit" @click="startEditInfo">编辑信息</button>
            </div>

            <form v-else class="info-form" @submit.prevent="handleUpdateInfo">
              <div class="form-group">
                <label class="form-label">昵称</label>
                <div class="input-wrapper">
                  <input v-model="infoForm.nickname" type="text" class="form-input" placeholder="请输入昵称" maxlength="20" />
                </div>
              </div>
              <div class="form-actions">
                <button type="submit" class="btn-save" :disabled="saving">保存</button>
                <button type="button" class="btn-cancel" @click="cancelEditInfo">取消</button>
              </div>
            </form>
          </div>

          <div v-if="activeTab === 'security'" class="security-panel">
            <h3 class="panel-title">修改密码</h3>
            <form class="security-form" @submit.prevent="handleUpdatePassword">
              <div class="form-group">
                <label class="form-label">旧密码</label>
                <div class="input-wrapper">
                  <input v-model="passwordForm.oldPassword" type="password" class="form-input" placeholder="请输入当前密码" maxlength="20" />
                </div>
              </div>
              <div class="form-group">
                <label class="form-label">新密码</label>
                <div class="input-wrapper">
                  <input v-model="passwordForm.newPassword" type="password" class="form-input" placeholder="请输入新密码（6-12位）" maxlength="20" />
                </div>
              </div>
              <div class="form-group">
                <label class="form-label">确认新密码</label>
                <div class="input-wrapper">
                  <input v-model="passwordForm.confirmNewPassword" type="password" class="form-input" placeholder="请再次输入新密码" maxlength="20" />
                </div>
              </div>
              <div class="form-actions">
                <button type="submit" class="btn-save" :disabled="saving">确认修改</button>
                <button type="button" class="btn-cancel" @click="resetPasswordForm">重置</button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '../stores/user'
import { updateProfile, uploadAvatar } from '../api/user'
import { useMessage } from '../composables/useMessage'

const userStore = useUserStore()
const msg = useMessage()

const activeTab = ref('info')
const editingInfo = ref(false)
const saving = ref(false)
const fileInput = ref(null)

const infoForm = reactive({
  nickname: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmNewPassword: '',
})

const roleText = computed(() => {
  return userStore.userInfo?.role === 1 ? '管理员' : '普通用户'
})

const avatarUrl = computed(() => userStore.avatar)

onMounted(() => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.fetchProfile()
  }
})

function maskPhone(phone) {
  if (!phone) return '-'
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

function startEditInfo() {
  infoForm.nickname = userStore.userInfo?.nickname || ''
  editingInfo.value = true
}

function cancelEditInfo() {
  editingInfo.value = false
}

async function handleUpdateInfo() {
  if (!infoForm.nickname.trim()) {
    msg.error('昵称不能为空')
    return
  }
  if (infoForm.nickname.trim().length < 1 || infoForm.nickname.trim().length > 20) {
    msg.error('昵称长度应在1-20位之间')
    return
  }

  saving.value = true
  try {
    const res = await updateProfile({ nickname: infoForm.nickname.trim() })
    userStore.setUserInfo(res.data)
    editingInfo.value = false
    msg.success('信息更新成功')
  } catch (e) {
    msg.error(e.message || '更新失败')
  } finally {
    saving.value = false
  }
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmNewPassword = ''
}

async function handleUpdatePassword() {
  if (!passwordForm.oldPassword) {
    msg.error('请输入当前密码')
    return
  }
  if (!passwordForm.newPassword) {
    msg.error('请输入新密码')
    return
  }
  if (passwordForm.newPassword.length < 6 || passwordForm.newPassword.length > 12) {
    msg.error('新密码长度应在6-12位之间')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmNewPassword) {
    msg.error('两次输入的新密码不一致')
    return
  }

  saving.value = true
  try {
    await updateProfile({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmNewPassword: passwordForm.confirmNewPassword,
    })
    resetPasswordForm()
    msg.success('密码修改成功')
  } catch (e) {
    msg.error(e.message || '密码修改失败')
  } finally {
    saving.value = false
  }
}

function triggerUpload() {
  fileInput.value?.click()
}

async function handleAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return

  if (file.size > 5 * 1024 * 1024) {
    msg.error('头像文件不能超过5MB')
    return
  }

  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) {
    msg.error('仅支持 JPG、PNG、GIF、WebP 格式')
    return
  }

  try {
    const res = await uploadAvatar(file)
    userStore.setUserInfo({ ...userStore.userInfo, avatar: res.data.avatar })
    msg.success('头像上传成功')
  } catch (e) {
    msg.error(e.message || '头像上传失败')
  }

  e.target.value = ''
}
</script>

<style scoped>
.profile-page {
  min-height: calc(100vh - var(--header-height));
  padding-top: 20px;
  padding-bottom: 40px;
}

.profile-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.profile-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--bili-white);
  border-radius: var(--bili-radius-lg);
  box-shadow: var(--bili-shadow);
  overflow: hidden;
}

.avatar-section {
  padding: 32px 24px 24px;
  text-align: center;
  background: linear-gradient(135deg, #00A1D6 0%, #00B5E5 100%);
}

.avatar-wrapper {
  width: 96px;
  height: 96px;
  margin: 0 auto 16px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  border: 3px solid rgba(255, 255, 255, 0.4);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  font-weight: 700;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.hidden-input {
  display: none;
}

.profile-username {
  font-size: 18px;
  font-weight: 700;
  color: white;
  margin-bottom: 4px;
}

.profile-role {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
}

.sidebar-nav {
  padding: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 16px;
  border-radius: var(--bili-radius);
  color: var(--bili-gray-2);
  font-size: 14px;
  background: none;
  transition: all 0.2s;
  text-align: left;
}

.nav-item:hover {
  background: var(--bili-gray-6);
  color: var(--bili-blue);
}

.nav-item.active {
  background: rgba(0, 161, 214, 0.08);
  color: var(--bili-blue);
  font-weight: 600;
}

.profile-main {
  flex: 1;
  background: var(--bili-white);
  border-radius: var(--bili-radius-lg);
  box-shadow: var(--bili-shadow);
  padding: 32px;
  min-height: 400px;
}

.panel-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--bili-gray-1);
  margin-bottom: 28px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--bili-gray-5);
}

.info-display {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--bili-gray-6);
}

.info-label {
  width: 100px;
  font-size: 14px;
  color: var(--bili-gray-3);
  flex-shrink: 0;
}

.info-value {
  font-size: 14px;
  color: var(--bili-gray-1);
}

.btn-edit {
  align-self: flex-start;
  padding: 10px 28px;
  background: var(--bili-blue);
  color: white;
  border-radius: var(--bili-radius);
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s;
  margin-top: 8px;
}

.btn-edit:hover {
  background: var(--bili-blue-hover);
}

.info-form,
.security-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 480px;
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

.form-input {
  flex: 1;
  height: 44px;
  padding: 0 8px;
  font-size: 14px;
  color: var(--bili-gray-1);
  background: transparent;
}

.form-input::placeholder {
  color: var(--bili-gray-4);
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.btn-save {
  padding: 10px 28px;
  background: var(--bili-blue);
  color: white;
  border-radius: var(--bili-radius);
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s;
}

.btn-save:hover:not(:disabled) {
  background: var(--bili-blue-hover);
}

.btn-save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-cancel {
  padding: 10px 28px;
  background: var(--bili-gray-6);
  color: var(--bili-gray-2);
  border-radius: var(--bili-radius);
  font-size: 14px;
  transition: background 0.2s;
}

.btn-cancel:hover {
  background: var(--bili-gray-5);
}

@media (max-width: 768px) {
  .profile-layout {
    flex-direction: column;
  }

  .profile-sidebar {
    width: 100%;
  }
}
</style>
