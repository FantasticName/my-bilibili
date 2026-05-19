<template>
  <div class="coupon-center">
    <div class="page-header">
      <h1>优惠券秒杀</h1>
      <p class="page-desc">限时限量，手快有手慢无！</p>
    </div>

    <!-- ========== 管理面板：创建活动（所有登录用户可见） ========== -->
    <div v-if="userStore.isLoggedIn" class="admin-panel">
      <div class="panel-header" @click="showCreatePanel = !showCreatePanel">
        <h2>管理面板</h2>
        <span class="toggle-arrow" :class="{ open: showCreatePanel }">▼</span>
      </div>
      <div v-if="showCreatePanel" class="create-form">
        <h3>创建新的优惠券活动</h3>
        <div class="form-row">
          <div class="form-group">
            <label>活动名称</label>
            <input v-model="createForm.name" type="text" placeholder="如：618年中大促优惠券" />
          </div>
          <div class="form-group form-group-sm">
            <label>总库存</label>
            <input v-model.number="createForm.totalStock" type="number" min="1" placeholder="如：100" />
          </div>
        </div>
        <div class="form-group">
          <label>活动描述</label>
          <textarea v-model="createForm.description" rows="2" placeholder="简单描述活动内容..."></textarea>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>开始时间</label>
            <input v-model="createForm.startTime" type="datetime-local" />
          </div>
          <div class="form-group">
            <label>结束时间</label>
            <input v-model="createForm.endTime" type="datetime-local" />
          </div>
          <div class="form-group form-group-xs">
            <label>每人限抢</label>
            <input v-model.number="createForm.perUserLimit" type="number" min="0" placeholder="0=不限" />
          </div>
        </div>
        <div class="form-actions">
          <button class="btn-primary" @click="handleCreate" :disabled="creating">
            {{ creating ? '创建中...' : '创建活动' }}
          </button>
        </div>

        <!-- 活动管理列表 -->
        <div class="manage-list" v-if="allActivities.length > 0">
          <h3>活动管理</h3>
          <div v-for="item in allActivities" :key="item.id" class="manage-item">
            <div class="manage-info">
              <span class="manaage-status" :class="statusClass(item.status)">
                {{ statusText(item.status) }}
              </span>
              <span class="manage-name">{{ item.name }}</span>
              <span class="manage-stock">库存: {{ item.remainStock }}/{{ item.totalStock }}</span>
            </div>
            <button
              v-if="item.status === 0"
              class="btn-start"
              @click="handleStart(item.id)"
              :disabled="starting === item.id"
            >
              {{ starting === item.id ? '发布中...' : '发布活动' }}
            </button>
            <span v-else class="manage-hint">已发布</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 活动列表 ========== -->
    <div class="activity-section">
      <div class="section-header">
        <h2>进行中的活动</h2>
        <button class="btn-refresh" @click="loadActivities" :disabled="loading">
          <svg viewBox="0 0 24 24" width="16" height="16"><path d="M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0112 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z" fill="currentColor"/></svg>
          刷新
        </button>
      </div>

      <div v-if="loading" class="loading-state">加载中...</div>

      <div v-else-if="activities.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" width="48" height="48"><path d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z" fill="#ccc"/></svg>
        <p>暂无进行中的活动</p>
        <p class="sub-text">优惠券活动发布后会在这里显示</p>
      </div>

      <div v-else class="activity-list">
        <div v-for="item in activities" :key="item.id" class="activity-card" :class="{ ended: item.remainStock <= 0 }">
          <div class="card-top">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" width="32" height="32"><path d="M20 12v6a2 2 0 01-2 2H6a2 2 0 01-2-2v-6m16 0V8a2 2 0 00-2-2h-1.5m3.5 6H4m0 0V8a2 2 0 012-2h1.5m-1.5 6h16m-3-6l-1.5-3h-5L9 6m3 3v6m-3-3h6" stroke="currentColor" stroke-width="1.5" fill="none"/></svg>
            </div>
            <div class="card-body">
              <h3 class="card-title">{{ item.name }}</h3>
              <p class="card-desc">{{ item.description || '限时优惠券，抢到就是赚到！' }}</p>
              <div class="card-meta">
                <span class="meta-item">
                  <svg viewBox="0 0 20 20" width="14" height="14"><path d="M10 2a6 6 0 00-6 6v3.5l-1.5 3h15l-1.5-3V8a6 6 0 00-6-6z" fill="currentColor"/></svg>
                  每人限抢 {{ item.perUserLimit > 0 ? item.perUserLimit + '张' : '不限' }}
                </span>
                <span class="meta-item">
                  <svg viewBox="0 0 20 20" width="14" height="14"><path d="M10 2a8 8 0 100 16 8 8 0 000-16zm0 14A6 6 0 1110 4a6 6 0 010 12zm1-6.41V4h-2v6.59l3.71 3.7 1.42-1.41L11 9.59z" fill="currentColor"/></svg>
                  {{ item.startTime?.substring(0, 16) }} ~ {{ item.endTime?.substring(0, 16) }}
                </span>
              </div>
            </div>
            <div class="card-right">
              <div class="stock-ring">
                <svg viewBox="0 0 80 80" class="ring-bg">
                  <circle cx="40" cy="40" r="34" stroke="#E3E5E7" stroke-width="6" fill="none"/>
                  <circle
                    cx="40" cy="40" r="34"
                    :stroke="item.remainStock > 0 ? '#FB7299' : '#C9CCD0'"
                    stroke-width="6" fill="none"
                    stroke-linecap="round"
                    :stroke-dasharray="214"
                    :stroke-dashoffset="214 - 214 * (item.remainStock / item.totalStock)"
                    transform="rotate(-90 40 40)"
                  />
                </svg>
                <div class="ring-text">
                  <span class="ring-num">{{ item.remainStock }}</span>
                  <span class="ring-label">剩余</span>
                </div>
              </div>
              <button
                class="btn-grab"
                :disabled="item.remainStock <= 0 || grabbing === item.id"
                @click="handleGrab(item)"
              >
                {{ grabbing === item.id ? '抢购中...' : (item.remainStock <= 0 ? '已抢光' : '立即抢购') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 我的抢购记录 ========== -->
    <div class="records-section">
      <div class="section-header">
        <h2>我的抢购记录</h2>
      </div>
      <div v-if="recordsLoading" class="loading-state">加载中...</div>
      <div v-else-if="records.length === 0" class="empty-state-sm">暂无抢购记录</div>
      <div v-else class="records-list">
        <div v-for="r in records" :key="r.id" class="record-item">
          <span class="record-name">{{ r.activityName }}</span>
          <span class="record-time">{{ r.grabTime }}</span>
          <span class="record-status" :class="{ used: r.status === 1 }">
            {{ r.status === 0 ? '有效' : r.status === 1 ? '已使用' : '已过期' }}
          </span>
        </div>
      </div>
    </div>

    <!-- 抢购结果弹窗 -->
    <div v-if="grabResult" class="modal-overlay" @click="grabResult = null">
      <div class="modal-card" @click.stop>
        <div class="modal-icon" :class="{ success: grabResult.success }">
          {{ grabResult.success ? '🎉' : '😅' }}
        </div>
        <h3>{{ grabResult.message }}</h3>
        <button class="btn-primary" @click="grabResult = null">知道了</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '../stores/user'
import { useMessage } from '../composables/useMessage'
import {
  listActiveActivities,
  listAllActivities,
  getToken,
  grabCoupon,
  getRecords,
  createActivity,
  startActivity,
} from '../api/coupon'

const userStore = useUserStore()
const msg = useMessage()

const isAdmin = computed(() => userStore.userInfo?.role === 1)

// 活动列表
const activities = ref([])
const loading = ref(false)

// 抢购状态
const grabbing = ref(null)
const grabResult = ref(null)

// 管理员：创建表单
const showCreatePanel = ref(false)
const creating = ref(false)
const starting = ref(null)
const allActivities = ref([])
const createForm = ref({
  name: '',
  description: '',
  totalStock: 100,
  perUserLimit: 1,
  startTime: '',
  endTime: '',
})

// 抢购记录
const records = ref([])
const recordsLoading = ref(false)

// 状态映射
function statusText(s) {
  const map = { 0: '未发布', 1: '进行中', 2: '已结束' }
  return map[s] || '未知'
}

function statusClass(s) {
  const map = { 0: 'status-draft', 1: 'status-active', 2: 'status-ended' }
  return map[s] || ''
}

// 加载进行中的活动
async function loadActivities() {
  loading.value = true
  try {
    const res = await listActiveActivities()
    activities.value = res.data || []
  } catch (e) {
    console.error('加载活动列表失败', e)
  } finally {
    loading.value = false
  }
}

// 加载抢购记录
async function loadRecords() {
  recordsLoading.value = true
  try {
    const res = await getRecords()
    records.value = res.data || []
  } catch (e) {
    console.error('加载抢购记录失败', e)
  } finally {
    recordsLoading.value = false
  }
}

// 抢购
async function handleGrab(item) {
  if (grabbing.value) return
  grabbing.value = item.id

  try {
    // 获取幂等性Token
    const tokenRes = await getToken()
    const token = tokenRes.data?.token

    // 发起抢购
    const res = await grabCoupon(item.id, token)
    const code = res.data?.resultCode
    const message = res.data?.message || '操作完成'

    grabResult.value = { success: code === 1, message }

    // 成功则刷新列表
    if (code === 1) {
      await loadActivities()
      await loadRecords()
    }
  } catch (e) {
    grabResult.value = { success: false, message: e.message || '抢购失败' }
  } finally {
    grabbing.value = null
  }
}

// 管理员：创建活动
async function handleCreate() {
  const form = createForm.value
  if (!form.name.trim()) {
    msg.error('请输入活动名称')
    return
  }
  if (!form.totalStock || form.totalStock <= 0) {
    msg.error('库存必须大于0')
    return
  }
  creating.value = true
  try {
    await createActivity({
      name: form.name.trim(),
      description: form.description.trim(),
      totalStock: form.totalStock,
      perUserLimit: form.perUserLimit || 1,
      startTime: form.startTime ? form.startTime.replace('T', ' ') + ':00' : null,
      endTime: form.endTime ? form.endTime.replace('T', ' ') + ':00' : null,
      status: 0,
    })
    msg.success('活动创建成功！请在下方点击"发布活动"按钮')
    // 重置表单
    createForm.value = { name: '', description: '', totalStock: 100, perUserLimit: 1, startTime: '', endTime: '' }
    await loadAllActivities()
  } catch (e) {
    msg.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

// 管理员：发布活动
async function handleStart(activityId) {
  starting.value = activityId
  try {
    await startActivity(activityId)
    msg.success('活动已发布！库存已预热到Redis')
    await loadAllActivities()
    await loadActivities()
  } catch (e) {
    msg.error(e.message || '发布失败')
  } finally {
    starting.value = null
  }
}

// 管理员：加载所有活动
async function loadAllActivities() {
  try {
    const res = await listAllActivities()
    allActivities.value = res.data || []
  } catch (e) {
    console.error('加载管理列表失败', e)
  }
}

onMounted(() => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.fetchProfile().then(() => {
      loadActivities()
      loadRecords()
      loadAllActivities()
    })
  } else {
    loadActivities()
    loadRecords()
    loadAllActivities()
  }
})
</script>

<style scoped>
.coupon-center {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 20px 60px;
}

.page-header {
  text-align: center;
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--bili-gray-1);
  margin-bottom: 8px;
}

.page-header .page-desc {
  font-size: 14px;
  color: var(--bili-gray-3);
}

/* ====== 管理员面板 ====== */
.admin-panel {
  background: var(--bili-white);
  border-radius: var(--bili-radius-lg);
  box-shadow: var(--bili-shadow);
  margin-bottom: 24px;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  cursor: pointer;
  background: linear-gradient(135deg, #f0f4ff, #fdf2f5);
  transition: background 0.2s;
}

.panel-header:hover {
  background: linear-gradient(135deg, #e4ebff, #fce4ec);
}

.panel-header h2 {
  font-size: 16px;
  font-weight: 600;
  color: var(--bili-blue);
}

.toggle-arrow {
  font-size: 12px;
  color: var(--bili-gray-3);
  transition: transform 0.3s;
}

.toggle-arrow.open {
  transform: rotate(180deg);
}

.create-form {
  padding: 20px;
}

.create-form h3 {
  font-size: 15px;
  margin-bottom: 16px;
  color: var(--bili-gray-1);
}

.form-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.form-group {
  flex: 1;
  margin-bottom: 12px;
}

.form-group-sm {
  flex: 0.3;
}

.form-group-xs {
  flex: 0.25;
}

.form-group label {
  display: block;
  font-size: 13px;
  color: var(--bili-gray-2);
  margin-bottom: 4px;
  font-weight: 500;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--bili-gray-5);
  border-radius: var(--bili-radius);
  font-size: 14px;
  background: var(--bili-bg);
  transition: border-color 0.2s;
}

.form-group input:focus,
.form-group textarea:focus {
  border-color: var(--bili-blue);
  background: var(--bili-white);
}

.form-group textarea {
  resize: vertical;
}

.form-actions {
  margin-top: 8px;
}

/* ====== 管理列表 ====== */
.manage-list {
  margin-top: 24px;
  border-top: 1px solid var(--bili-gray-5);
  padding-top: 16px;
}

.manage-list h3 {
  margin-bottom: 12px;
}

.manage-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: var(--bili-gray-6);
  border-radius: var(--bili-radius);
  margin-bottom: 8px;
}

.manage-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.manage-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--bili-gray-1);
}

.manage-stock {
  font-size: 12px;
  color: var(--bili-gray-3);
}

.manaage-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.status-draft {
  background: #FFF3E0;
  color: #E65100;
}

.status-active {
  background: #E8F5E9;
  color: #2E7D32;
}

.status-ended {
  background: #ECEFF1;
  color: #546E7A;
}

.btn-start {
  padding: 6px 16px;
  background: var(--bili-orange);
  color: white;
  border: none;
  border-radius: var(--bili-radius);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
  white-space: nowrap;
}

.btn-start:hover {
  background: #e55a2e;
}

.btn-start:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.manage-hint {
  font-size: 12px;
  color: var(--bili-gray-4);
}

/* ====== 活动列表 ====== */
.activity-section,
.records-section {
  background: var(--bili-white);
  border-radius: var(--bili-radius-lg);
  box-shadow: var(--bili-shadow);
  padding: 20px;
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h2 {
  font-size: 17px;
  font-weight: 600;
  color: var(--bili-gray-1);
}

.btn-refresh {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: var(--bili-gray-6);
  border: none;
  border-radius: var(--bili-radius);
  color: var(--bili-gray-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-refresh:hover {
  background: var(--bili-gray-5);
  color: var(--bili-blue);
}

.loading-state {
  text-align: center;
  padding: 40px;
  color: var(--bili-gray-3);
}

.empty-state {
  text-align: center;
  padding: 48px 20px;
  color: var(--bili-gray-3);
}

.empty-state p {
  margin-top: 12px;
  font-size: 15px;
}

.empty-state .sub-text {
  font-size: 13px;
  color: var(--bili-gray-4);
}

.empty-state-sm {
  text-align: center;
  padding: 24px;
  color: var(--bili-gray-4);
  font-size: 14px;
}

/* ====== 活动卡片 ====== */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-card {
  border: 1px solid var(--bili-gray-5);
  border-radius: var(--bili-radius-lg);
  padding: 16px 20px;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.activity-card:hover {
  border-color: var(--bili-pink);
  box-shadow: 0 2px 12px rgba(251, 114, 153, 0.1);
}

.activity-card.ended {
  opacity: 0.6;
}

.card-top {
  display: flex;
  align-items: center;
  gap: 16px;
}

.card-icon {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #fff5f7, #fff0f5);
  border-radius: var(--bili-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--bili-pink);
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--bili-gray-1);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-desc {
  font-size: 13px;
  color: var(--bili-gray-3);
  margin-bottom: 8px;
}

.card-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--bili-gray-3);
}

.card-right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.stock-ring {
  position: relative;
  width: 70px;
  height: 70px;
}

.ring-bg {
  width: 100%;
  height: 100%;
}

.ring-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
}

.ring-num {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--bili-pink);
  line-height: 1;
}

.ring-label {
  font-size: 11px;
  color: var(--bili-gray-3);
}

.btn-grab {
  padding: 8px 28px;
  background: linear-gradient(135deg, #FB7299, #FF8CB3);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-grab:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(251, 114, 153, 0.4);
}

.btn-grab:disabled {
  background: var(--bili-gray-4);
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* ====== 按钮 ====== */
.btn-primary {
  padding: 8px 24px;
  background: var(--bili-blue);
  color: white;
  border: none;
  border-radius: var(--bili-radius);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-primary:hover {
  background: var(--bili-blue-hover);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ====== 抢购记录 ====== */
.records-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.record-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  background: var(--bili-gray-6);
  border-radius: var(--bili-radius);
  gap: 12px;
}

.record-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--bili-gray-1);
  flex: 1;
}

.record-time {
  font-size: 12px;
  color: var(--bili-gray-4);
}

.record-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #E8F5E9;
  color: #2E7D32;
  font-weight: 500;
}

.record-status.used {
  background: #FFF3E0;
  color: #E65100;
}

/* ====== 弹窗 ====== */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.modal-card {
  background: var(--bili-white);
  border-radius: var(--bili-radius-lg);
  padding: 32px 40px;
  text-align: center;
  min-width: 280px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
}

.modal-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.modal-icon.success {
  animation: bounce 0.6s;
}

@keyframes bounce {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.3); }
}

.modal-card h3 {
  font-size: 16px;
  color: var(--bili-gray-1);
  margin-bottom: 20px;
}
</style>