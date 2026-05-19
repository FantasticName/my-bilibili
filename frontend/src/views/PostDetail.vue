<template>
  <div class="post-detail" v-if="post">
    <div class="post-card">
      <div class="post-header">
        <img :src="post.avatar || '/default-avatar.png'" class="avatar" @click="goUser(post.userId)" />
        <div class="user-info">
          <span class="nickname" @click="goUser(post.userId)">{{ post.nickname }}</span>
          <span class="time">{{ formatTime(post.createdAt) }}</span>
        </div>
      </div>

      <div class="post-content" v-if="post.content">{{ post.content }}</div>

      <div class="post-images" v-if="post.images && post.images.length > 0">
        <div :class="['image-grid', 'grid-' + Math.min(post.images.length, 9)]">
          <img
            v-for="(img, idx) in post.images"
            :key="idx"
            :src="img"
            class="post-image"
            @click="previewImage(idx)"
          />
        </div>
      </div>

      <div class="post-actions">
        <button class="action-btn" :class="{ liked: post.isLiked }" @click="toggleLike">
          <span class="icon">{{ post.isLiked ? '❤' : '♡' }}</span>
          <span>{{ post.likeCount || 0 }}</span>
        </button>
        <button class="action-btn">
          <span class="icon">💬</span>
          <span>{{ post.commentCount || 0 }}</span>
        </button>
      </div>
    </div>

    <!-- 评论区 -->
    <div class="comment-section">
      <h3>评论 ({{ post.commentCount || 0 }})</h3>

      <div class="comment-input" v-if="isLoggedIn">
        <textarea v-model="commentContent" placeholder="写下你的评论..." rows="3"></textarea>
        <button class="comment-submit-btn" @click="submitComment" :disabled="!commentContent.trim()">发送</button>
      </div>
      <div class="login-tip" v-else>
        <router-link to="/login">登录</router-link>后可以评论
      </div>

      <div class="comment-list">
        <div v-for="comment in comments" :key="comment.id" class="comment-item">
          <img :src="comment.avatar || '/default-avatar.png'" class="comment-avatar" />
          <div class="comment-body">
            <div class="comment-meta">
              <span class="comment-nickname">{{ comment.nickname }}</span>
              <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
            </div>
            <div class="comment-text">{{ comment.content }}</div>
          </div>
        </div>
        <div v-if="comments.length === 0" class="no-comments">暂无评论，快来抢沙发~</div>
      </div>
    </div>

    <!-- 图片预览遮罩 -->
    <div class="image-preview-mask" v-if="showPreview" @click="showPreview = false">
      <img :src="post.images[previewIndex]" class="preview-full-img" @click.stop />
    </div>
  </div>

  <div v-else class="loading">加载中...</div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPostDetail } from '../api/post'
import { toggleLike as apiToggleLike } from '../api/like'
import { getCommentList, createComment } from '../api/comment'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const post = ref(null)
const comments = ref([])
const commentContent = ref('')
const showPreview = ref(false)
const previewIndex = ref(0)

const isLoggedIn = computed(() => !!userStore.token)

onMounted(async () => {
  await loadPost()
  await loadComments()
})

async function loadPost() {
  try {
    const res = await getPostDetail(route.params.postId)
    post.value = res.data
  } catch (e) {
    console.error('加载动态失败', e)
  }
}

async function loadComments() {
  try {
    const res = await getCommentList({ targetType: 2, targetId: route.params.postId, page: 1, size: 50 })
    comments.value = res.data.list || []
  } catch (e) {
    console.error('加载评论失败', e)
  }
}

async function toggleLike() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await apiToggleLike({ targetType: 3, targetId: post.value.id })
    const liked = res.data.liked
    post.value.isLiked = liked
    post.value.likeCount = (post.value.likeCount || 0) + (liked ? 1 : -1)
  } catch (e) {
    console.error('点赞失败', e)
  }
}

async function submitComment() {
  if (!commentContent.value.trim()) return
  try {
    await createComment({
      targetType: 2,
      targetId: Number(route.params.postId),
      content: commentContent.value.trim()
    })
    commentContent.value = ''
    post.value.commentCount = (post.value.commentCount || 0) + 1
    await loadComments()
  } catch (e) {
    alert('评论失败: ' + (e.message || '未知错误'))
  }
}

function previewImage(idx) {
  previewIndex.value = idx
  showPreview.value = true
}

function goUser(userId) {
  router.push(`/user/${userId}`)
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return d.toLocaleDateString()
}
</script>

<style scoped>
.post-detail {
  max-width: 650px;
  margin: 20px auto;
  padding: 0 16px;
}

.post-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.post-header {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
}

.user-info {
  margin-left: 12px;
}

.nickname {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  cursor: pointer;
}

.nickname:hover {
  color: #00a1d6;
}

.time {
  display: block;
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.post-content {
  font-size: 15px;
  line-height: 1.7;
  color: #333;
  margin-bottom: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.post-images {
  margin-bottom: 14px;
}

.image-grid {
  display: grid;
  gap: 6px;
}

.grid-1 { grid-template-columns: 1fr; max-width: 360px; }
.grid-2 { grid-template-columns: 1fr 1fr; }
.grid-3 { grid-template-columns: 1fr 1fr 1fr; }
.grid-4 { grid-template-columns: 1fr 1fr; }
.grid-5, .grid-6 { grid-template-columns: 1fr 1fr 1fr; }
.grid-7, .grid-8, .grid-9 { grid-template-columns: 1fr 1fr 1fr; }

.post-image {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.post-image:hover {
  opacity: 0.85;
}

.post-actions {
  display: flex;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  padding: 4px 0;
}

.action-btn:hover {
  color: #00a1d6;
}

.action-btn.liked {
  color: #fb7299;
}

.action-btn.liked .icon {
  color: #fb7299;
}

.icon {
  font-size: 18px;
}

.comment-section {
  margin-top: 16px;
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.comment-section h3 {
  font-size: 16px;
  margin-bottom: 16px;
}

.comment-input {
  margin-bottom: 16px;
}

.comment-input textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
}

.comment-input textarea:focus {
  border-color: #00a1d6;
}

.comment-submit-btn {
  margin-top: 8px;
  padding: 6px 20px;
  background: #00a1d6;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.comment-submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.login-tip {
  font-size: 14px;
  color: #999;
  margin-bottom: 16px;
}

.login-tip a {
  color: #00a1d6;
}

.comment-item {
  display: flex;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.comment-body {
  margin-left: 10px;
  flex: 1;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-nickname {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.comment-time {
  font-size: 12px;
  color: #999;
}

.comment-text {
  font-size: 14px;
  color: #333;
  margin-top: 4px;
  line-height: 1.5;
}

.no-comments {
  text-align: center;
  color: #999;
  padding: 20px 0;
  font-size: 14px;
}

.image-preview-mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  cursor: pointer;
}

.preview-full-img {
  max-width: 90%;
  max-height: 90%;
  object-fit: contain;
}

.loading {
  text-align: center;
  padding: 60px 0;
  color: #999;
  font-size: 16px;
}
</style>
