<template>
  <div class="search-page">
    <div class="search-header">
      <div class="search-input-wrap">
        <svg class="search-icon" viewBox="0 0 24 24" width="20" height="20">
          <path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" fill="currentColor"/>
        </svg>
        <input
          v-model="keyword"
          type="text"
          class="search-input"
          placeholder="搜索视频、动态、用户..."
          @keyup.enter="doSearch"
        />
        <button v-if="keyword" class="btn-clear" @click="keyword='';doSearch()">✕</button>
      </div>
      <button class="btn-search" @click="doSearch">搜索</button>
    </div>

    <div class="search-tabs" v-if="searched">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
        <span class="tab-count" v-if="tab.count !== null">({{ tab.count }})</span>
      </button>
    </div>

    <div class="search-body" v-if="searched">
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>搜索中...</p>
      </div>

      <template v-else>
        <div v-if="results.length === 0" class="empty-state">
          <svg viewBox="0 0 80 80" width="80" height="80">
            <circle cx="40" cy="40" r="35" stroke="#ddd" stroke-width="2" fill="none"/>
            <path d="M25 40h30M40 25v30" stroke="#ddd" stroke-width="3" stroke-linecap="round"/>
          </svg>
          <p>暂无搜索结果</p>
          <span>换个关键词试试吧~</span>
        </div>

        <div v-else class="result-list">
          <div
            v-for="item in results"
            :key="item.type + '-' + item.id"
            class="result-card"
            @click="goDetail(item)"
          >
            <div class="card-cover" v-if="item.type === 'video' && item.cover">
              <img :src="item.cover" alt="cover" />
              <div class="play-icon">▶</div>
            </div>
            <div class="card-avatar" v-if="item.type === 'user'">
              <img v-if="item.avatar" :src="item.avatar" alt="avatar" />
              <div v-else class="avatar-placeholder">{{ item.nickname?.charAt(0)?.toUpperCase() }}</div>
            </div>
            <div class="card-info">
              <div class="card-type-tag" :class="'tag-' + item.type">
                {{ item.type === 'video' ? '视频' : item.type === 'post' ? '动态' : '用户' }}
              </div>
              <h4 class="card-title" v-if="item.type !== 'user'">{{ truncate(item.title || item.description, 60) }}</h4>
              <h4 class="card-title" v-else>{{ item.nickname }}</h4>
              <p class="card-sub" v-if="item.type === 'video'">
                <span>{{ item.nickname }}</span>
                <span class="dot">·</span>
                <span>{{ formatCount(item.viewCount) }}播放</span>
                <span class="dot">·</span>
                <span>{{ item.category || '其他' }}</span>
              </p>
              <p class="card-sub" v-if="item.type === 'post'">
                <span>{{ item.nickname }}</span>
                <span class="dot">·</span>
                <span>{{ formatCount(item.likeCount) }}赞</span>
              </p>
              <p class="card-sub" v-if="item.type === 'user'">
                <span>{{ item.nickname }}</span>
              </p>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../api/request'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const activeTab = ref('all')
const loading = ref(false)
const searched = ref(false)
const results = ref([])
const totals = ref({ video: 0, post: 0, user: 0 })

const tabs = [
  { key: 'all', label: '综合', count: null },
  { key: 'videos', label: '视频', get count() { return totals.value.video } },
  { key: 'posts', label: '动态', get count() { return totals.value.post } },
  { key: 'users', label: '用户', get count() { return totals.value.user } },
]

onMounted(() => {
  const q = route.query.q
  if (q) {
    keyword.value = q
    doSearch()
  }
})

async function doSearch() {
  if (!keyword.value.trim()) return
  searched.value = true
  loading.value = true

  try {
    let url
    if (activeTab.value === 'all') {
      url = `/search/all?keyword=${encodeURIComponent(keyword.value.trim())}`
    } else if (activeTab.value === 'videos') {
      url = `/search/videos?keyword=${encodeURIComponent(keyword.value.trim())}`
    } else if (activeTab.value === 'posts') {
      url = `/search/posts?keyword=${encodeURIComponent(keyword.value.trim())}`
    } else {
      url = `/search/users?keyword=${encodeURIComponent(keyword.value.trim())}`
    }

    const res = await request.get(url)
    if (res.code === 0) {
      if (activeTab.value === 'all') {
        totals.value.video = res.data.videoTotal || 0
        totals.value.post = res.data.postTotal || 0
        totals.value.user = res.data.userTotal || 0
        results.value = res.data.list || []
      } else {
        results.value = res.data.list || []
        if (activeTab.value === 'videos') totals.value.video = res.data.total || 0
        if (activeTab.value === 'posts') totals.value.post = res.data.total || 0
        if (activeTab.value === 'users') totals.value.user = res.data.total || 0
      }
      router.replace({ query: { q: keyword.value.trim() } })
    }
  } catch (e) {
    console.error('搜索失败:', e)
    results.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  activeTab.value = key
  if (searched.value) doSearch()
}

function goDetail(item) {
  if (item.type === 'video') router.push(`/video/${item.id}`)
  else if (item.type === 'post') router.push(`/post/${item.id}`)
  else if (item.type === 'user') router.push(`/user/${item.id}`)
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return '' + n
}

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.slice(0, len) + '...' : str
}
</script>

<style scoped>
.search-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 20px 80px;
}

.search-header {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  background: var(--bili-white);
  border: 2px solid var(--bili-gray-5);
  border-radius: 24px;
  padding: 0 16px;
  transition: border-color 0.2s;
}

.search-input-wrap:focus-within {
  border-color: var(--bili-blue);
}

.search-icon {
  color: var(--bili-gray-3);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  padding: 12px 8px;
  font-size: 16px;
  background: transparent;
  color: var(--bili-gray-1);
}

.btn-clear {
  background: var(--bili-gray-5);
  border: none;
  border-radius: 50%;
  width: 22px;
  height: 22px;
  cursor: pointer;
  color: var(--bili-gray-2);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-search {
  padding: 10px 28px;
  background: var(--bili-blue);
  color: white;
  border: none;
  border-radius: 24px;
  font-size: 15px;
  cursor: pointer;
  transition: background 0.2s;
  white-space: nowrap;
}

.btn-search:hover {
  background: var(--bili-blue-hover, #e05880);
}

.search-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--bili-gray-5);
  margin-bottom: 28px;
}

.tab-btn {
  padding: 10px 24px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 15px;
  color: var(--bili-gray-2);
  border-bottom: 3px solid transparent;
  transition: all 0.2s;
  position: relative;
  bottom: -1px;
}

.tab-btn:hover {
  color: var(--bili-blue);
}

.tab-btn.active {
  color: var(--bili-blue);
  font-weight: 600;
  border-bottom-color: var(--bili-blue);
}

.tab-count {
  color: var(--bili-gray-3);
  font-size: 13px;
  margin-left: 4px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 0;
  color: var(--bili-gray-3);
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--bili-gray-5);
  border-top-color: var(--bili-blue);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0;
  color: var(--bili-gray-3);
}

.empty-state p {
  font-size: 16px;
  margin-top: 16px;
}

.empty-state span {
  font-size: 13px;
  color: var(--bili-gray-4);
  margin-top: 8px;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.result-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.result-card:hover {
  background: var(--bili-gray-6);
}

.card-cover {
  width: 160px;
  height: 90px;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
  background: var(--bili-gray-5);
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.play-icon {
  position: absolute;
  bottom: 4px;
  left: 4px;
  font-size: 11px;
  color: white;
  background: rgba(0,0,0,0.5);
  padding: 2px 6px;
  border-radius: 4px;
}

.card-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}

.card-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  background: var(--bili-blue);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-type-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  margin-bottom: 6px;
}

.tag-video { background: rgba(251,114,153,0.1); color: #FB7299; }
.tag-post  { background: rgba(0,161,214,0.1); color: #00A1D6; }
.tag-user  { background: rgba(0,200,100,0.1); color: #00C864; }

.card-title {
  font-size: 15px;
  color: var(--bili-gray-1);
  margin: 0 0 4px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-sub {
  font-size: 13px;
  color: var(--bili-gray-3);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dot {
  margin: 0 6px;
}
</style>