<template>
  <div class="favorite-detail">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()">← 返回</button>
      <h2>{{ folderName }}</h2>
    </div>
    <div v-if="videos.length === 0" class="empty">收藏夹为空</div>
    <div class="video-grid">
      <div
        v-for="video in videos"
        :key="video.id"
        class="video-card"
        @click="$router.push(`/video/${video.id}`)"
      >
        <div class="cover">
          <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title" />
          <div v-else class="cover-placeholder">暂无封面</div>
        </div>
        <div class="card-info">
          <h3>{{ video.title }}</h3>
          <span>{{ formatCount(video.viewCount) }}播放 · {{ formatCount(video.likeCount) }}点赞</span>
        </div>
      </div>
    </div>
    <div class="pagination" v-if="total > pageSize">
      <button :disabled="page <= 1" @click="page--; loadVideos()">上一页</button>
      <span>{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
      <button :disabled="page >= Math.ceil(total / pageSize)" @click="page++; loadVideos()">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getFavoriteVideos } from '../api/favorite'

const route = useRoute()
const folderId = route.params.id
const folderName = ref('收藏夹')
const videos = ref([])
const page = ref(1)
const pageSize = ref(12)
const total = ref(0)

onMounted(() => {
  loadVideos()
})

async function loadVideos() {
  try {
    const res = await getFavoriteVideos(folderId, { page: page.value, size: pageSize.value })
    videos.value = res.data.list
    total.value = res.data.total
  } catch (e) {
    console.error('获取收藏夹视频失败:', e)
  }
}

function formatCount(num) {
  if (!num) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return num.toString()
}
</script>

<style scoped>
.favorite-detail {
  max-width: 1000px;
  margin: 0 auto;
  padding: 30px 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.back-btn {
  padding: 6px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}

.back-btn:hover {
  border-color: #00a1d6;
  color: #00a1d6;
}

.page-header h2 {
  font-size: 20px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.video-card {
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  transition: transform 0.2s;
}

.video-card:hover {
  transform: translateY(-2px);
}

.cover {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background: #f0f0f0;
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
}

.card-info {
  padding: 10px;
}

.card-info h3 {
  font-size: 14px;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-info span {
  font-size: 12px;
  color: #999;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  font-size: 14px;
  color: #666;
}

.pagination button {
  padding: 6px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty {
  text-align: center;
  padding: 60px 0;
  color: #999;
}
</style>
