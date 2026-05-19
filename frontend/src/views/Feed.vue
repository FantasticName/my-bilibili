<template>
  <div class="feed-page">
    <div class="feed-header">
      <h1>关注动态</h1>
      <button @click="showPublish = !showPublish" class="publish-toggle">
        {{ showPublish ? '取消' : '发布动态' }}
      </button>
    </div>

    <div v-if="showPublish" class="publish-box">
      <textarea v-model="postContent" placeholder="分享你的想法..." rows="3"></textarea>
      <div class="publish-image-section">
        <div class="publish-image-list">
          <div v-for="(img, index) in previewImages" :key="index" class="publish-preview-item">
            <img :src="img" class="publish-preview-img" />
            <button type="button" class="publish-remove-btn" @click="removeImage(index)">✕</button>
          </div>
          <label v-if="previewImages.length < 9" class="publish-add-btn">
            <input type="file" accept="image/*" multiple @change="handleImageSelect" hidden />
            <span>+</span>
          </label>
        </div>
      </div>
      <button @click="handlePublish" :disabled="(!postContent.trim() && selectedFiles.length === 0) || publishing" class="publish-btn">
        {{ publishing ? '发布中...' : '发布' }}
      </button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="posts.length === 0" class="empty">
      <p>还没有关注动态</p>
      <p class="hint">去关注一些博主吧~</p>
    </div>

    <div v-else class="post-list">
      <div v-for="post in posts" :key="post.id" class="post-item" @click="goToPost(post.id)">
        <div class="post-author" @click.stop="$router.push(`/user/${post.userId}`)">
          <img v-if="post.avatar" :src="post.avatar" class="author-avatar" />
          <div v-else class="author-avatar-placeholder">{{ post.nickname?.charAt(0) }}</div>
          <span class="author-name">{{ post.nickname }}</span>
          <span class="post-time">{{ formatDate(post.createdAt) }}</span>
        </div>
        <div class="post-content" v-if="post.content">{{ post.content }}</div>
        <div class="post-images" v-if="post.images && post.images.length > 0">
          <div :class="['post-image-grid', 'grid-' + Math.min(post.images.length, 9)]">
            <img v-for="(img, idx) in post.images" :key="idx" :src="img" class="post-thumb" />
          </div>
        </div>
        <div class="post-actions" @click.stop>
          <button @click="likePost(post)" class="action-btn" :class="{ liked: post.isLiked }">
            {{ post.isLiked ? '❤' : '🤍' }} {{ post.likeCount || 0 }}
          </button>
          <button @click="commentPost(post)" class="action-btn">
            💬 {{ post.commentCount || 0 }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="hasMore && !loading" class="load-more">
      <button @click="loadMore">加载更多</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getFeed } from '../api/feed'
import { createPost } from '../api/post'
import { toggleLike } from '../api/like'

const router = useRouter()

const posts = ref([])
const loading = ref(false)
const showPublish = ref(false)
const postContent = ref('')
const publishing = ref(false)
const hasMore = ref(true)
const cursor = ref(null)
const selectedFiles = ref([])
const previewImages = ref([])

onMounted(() => {
  fetchFeed()
})

async function fetchFeed() {
  loading.value = true
  try {
    const params = { limit: 10 }
    if (cursor.value) params.cursor = cursor.value
    const res = await getFeed(params)
    if (res.data.length < 10) hasMore.value = false
    posts.value = [...posts.value, ...res.data]
    if (res.data.length > 0) {
      cursor.value = res.data[res.data.length - 1].createdAt
    }
  } catch (e) {
    console.error('获取Feed失败:', e)
  } finally {
    loading.value = false
  }
}

function loadMore() {
  fetchFeed()
}

function handleImageSelect(event) {
  const files = Array.from(event.target.files)
  const remaining = 9 - selectedFiles.value.length
  const toAdd = files.slice(0, remaining)
  for (const file of toAdd) {
    if (!file.type.startsWith('image/')) continue
    if (file.size > 5 * 1024 * 1024) {
      alert('图片大小不能超过5MB: ' + file.name)
      continue
    }
    selectedFiles.value.push(file)
    const reader = new FileReader()
    reader.onload = (e) => {
      previewImages.value.push(e.target.result)
    }
    reader.readAsDataURL(file)
  }
  event.target.value = ''
}

function removeImage(index) {
  selectedFiles.value.splice(index, 1)
  previewImages.value.splice(index, 1)
}

async function handlePublish() {
  if (!postContent.value.trim() && selectedFiles.value.length === 0) return
  publishing.value = true
  try {
    const formData = new FormData()
    if (postContent.value.trim()) {
      formData.append('content', postContent.value.trim())
    }
    for (const file of selectedFiles.value) {
      formData.append('images', file)
    }
    await createPost(formData)
    postContent.value = ''
    selectedFiles.value = []
    previewImages.value = []
    showPublish.value = false
    posts.value = []
    cursor.value = null
    hasMore.value = true
    await fetchFeed()
  } catch (e) {
    console.error('发布动态失败:', e)
  } finally {
    publishing.value = false
  }
}

async function likePost(post) {
  try {
    const res = await toggleLike({ targetType: 3, targetId: post.id })
    const liked = res.data.liked
    post.isLiked = liked
    post.likeCount += liked ? 1 : -1
  } catch (e) {
    console.error('点赞失败:', e)
  }
}

function commentPost(post) {
  router.push(`/post/${post.id}`)
}

function goToPost(postId) {
  router.push(`/post/${postId}`)
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.feed-page {
  max-width: 700px;
  margin: 0 auto;
  padding: 20px;
}

.feed-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.feed-header h1 {
  font-size: 22px;
}

.publish-toggle {
  padding: 8px 20px;
  background: #00a1d6;
  color: #fff;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
}

.publish-box {
  margin-bottom: 20px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.publish-box textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  resize: vertical;
  font-size: 14px;
  font-family: inherit;
  box-sizing: border-box;
}

.publish-image-section {
  margin-top: 10px;
}

.publish-image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.publish-preview-item {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 6px;
  overflow: hidden;
}

.publish-preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.publish-remove-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  border: none;
  cursor: pointer;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.publish-add-btn {
  width: 72px;
  height: 72px;
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 24px;
  color: #999;
}

.publish-add-btn:hover {
  border-color: #00a1d6;
}

.publish-btn {
  margin-top: 8px;
  padding: 8px 24px;
  background: #00a1d6;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.publish-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading, .empty {
  text-align: center;
  padding: 60px 0;
  color: #999;
}

.empty .hint {
  font-size: 13px;
  margin-top: 8px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.post-item {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.post-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.post-author {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  cursor: pointer;
}

.author-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.author-avatar-placeholder {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #00a1d6;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.author-name {
  font-weight: 500;
  font-size: 14px;
}

.post-time {
  font-size: 12px;
  color: #999;
  margin-left: auto;
}

.post-content {
  line-height: 1.8;
  color: #333;
  margin-bottom: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.post-images {
  margin-bottom: 12px;
}

.post-image-grid {
  display: grid;
  gap: 4px;
}

.post-image-grid.grid-1 { grid-template-columns: 1fr; max-width: 280px; }
.post-image-grid.grid-2 { grid-template-columns: 1fr 1fr; max-width: 360px; }
.post-image-grid.grid-3 { grid-template-columns: 1fr 1fr 1fr; max-width: 400px; }
.post-image-grid.grid-4 { grid-template-columns: 1fr 1fr; max-width: 360px; }
.post-image-grid.grid-5,
.post-image-grid.grid-6 { grid-template-columns: 1fr 1fr 1fr; max-width: 400px; }
.post-image-grid.grid-7,
.post-image-grid.grid-8,
.post-image-grid.grid-9 { grid-template-columns: 1fr 1fr 1fr; max-width: 400px; }

.post-thumb {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 4px;
}

.post-actions {
  display: flex;
  gap: 16px;
}

.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: #999;
  padding: 0;
}

.action-btn:hover {
  color: #00a1d6;
}

.action-btn.liked {
  color: #fb7299;
}

.load-more {
  text-align: center;
  margin-top: 20px;
}

.load-more button {
  padding: 10px 32px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  color: #666;
}

.load-more button:hover {
  color: #00a1d6;
  border-color: #00a1d6;
}
</style>
