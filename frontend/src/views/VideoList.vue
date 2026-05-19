<template>
  <div class="video-list">
    <div class="category-bar">
      <button
        v-for="cat in categories"
        :key="cat"
        :class="{ active: currentCategory === cat }"
        @click="switchCategory(cat)"
      >
        {{ cat }}
      </button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="videos.length === 0" class="empty">暂无视频</div>

    <div v-else class="video-grid">
      <div
        v-for="video in videos"
        :key="video.id"
        class="video-card"
        @click="goDetail(video.id)"
      >
        <div class="cover">
          <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title" />
          <div v-else class="cover-placeholder">暂无封面</div>
          <span class="duration">▶</span>
        </div>
        <div class="info">
          <h3 class="title">{{ video.title }}</h3>
          <div class="meta">
            <span class="author">{{ video.nickname }}</span>
            <span class="stats">{{ formatCount(video.viewCount) }}播放 · {{ formatCount(video.likeCount) }}点赞</span>
          </div>
        </div>
      </div>
    </div>

    <div class="pagination" v-if="total > size">
      <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>{{ page }} / {{ Math.ceil(total / size) }}</span>
      <button :disabled="page >= Math.ceil(total / size)" @click="changePage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getVideoList } from '../api/video'

const router = useRouter()

const categories = ['全部', '搞笑', '美食', '探店', '科技', '音乐', '舞蹈', '游戏', '知识', '影视']
const currentCategory = ref('全部')
const videos = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(12)
const total = ref(0)

onMounted(() => {
  fetchVideos()
})

async function fetchVideos() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (currentCategory.value !== '全部') {
      params.category = currentCategory.value
    }
    const res = await getVideoList(params)
    videos.value = res.data.list
    total.value = res.data.total
  } catch (e) {
    console.error('获取视频列表失败:', e)
  } finally {
    loading.value = false
  }
}

function switchCategory(cat) {
  currentCategory.value = cat
  page.value = 1
  fetchVideos()
}

function changePage(p) {
  page.value = p
  fetchVideos()
  window.scrollTo(0, 0)
}

function goDetail(id) {
  router.push(`/video/${id}`)
}

function formatCount(num) {
  if (!num) return '0'
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toString()
}
</script>

<style scoped>
.video-list {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.category-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.category-bar button {
  padding: 6px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  transition: all 0.2s;
}

.category-bar button:hover {
  color: #00a1d6;
  border-color: #00a1d6;
}

.category-bar button.active {
  background: #00a1d6;
  color: #fff;
  border-color: #00a1d6;
}

.loading, .empty {
  text-align: center;
  padding: 60px 0;
  color: #999;
  font-size: 16px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.video-card {
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
  background: #fff;
}

.video-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.cover {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background: #f0f0f0;
  overflow: hidden;
}

.cover img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
}

.duration {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.info {
  padding: 12px;
}

.title {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #999;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 32px;
  padding: 20px 0;
}

.pagination button {
  padding: 8px 20px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination button:hover:not(:disabled) {
  color: #00a1d6;
  border-color: #00a1d6;
}
</style>
