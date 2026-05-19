<template>
  <div class="video-detail" v-if="video">
    <div class="player-section">
      <video
        v-if="video.videoUrl"
        :src="video.videoUrl"
        controls
        class="video-player"
      ></video>
      <div v-else class="player-placeholder">视频加载中...</div>
    </div>

    <div class="content-section">
      <h1 class="title">{{ video.title }}</h1>

      <div class="video-meta">
        <span class="category-tag">{{ video.category }}</span>
        <span class="stats">{{ formatCount(video.viewCount) }}播放 · {{ formatCount(video.likeCount) }}点赞</span>
        <span class="date">{{ formatDate(video.createdAt) }}</span>
      </div>

      <div class="author-bar">
        <div class="author-left">
          <div class="author-info" @click="goUserCenter(video.userId)">
            <img v-if="video.avatar" :src="video.avatar" class="author-avatar" />
            <div v-else class="author-avatar-placeholder">U</div>
            <span class="author-name">{{ video.nickname }}</span>
          </div>
          <button
            v-if="isLoggedIn && currentUserId !== video.userId"
            :class="{ following: isFollowed }"
            @click="handleFollow"
            class="follow-btn"
          >
            {{ isFollowed ? '已关注' : '+ 关注' }}
          </button>
        </div>
        <div class="actions">
          <button
            :class="{ active: video.isLiked }"
            @click="handleLike"
            class="action-btn"
          >
            {{ video.isLiked ? '❤ 已点赞' : '🤍 点赞' }}
          </button>
          <button
            :class="{ active: video.isFavorited }"
            @click="handleFavorite"
            class="action-btn"
          >
            {{ video.isFavorited ? '⭐ 已收藏' : '☆ 收藏' }}
          </button>
          <button @click="handleDoubleTap" class="action-btn double-tap">
            🔥 一键二连
          </button>
        </div>
      </div>

      <div class="description" v-if="video.description">
        <p>{{ video.description }}</p>
      </div>
    </div>

    <div class="comment-section">
      <h2>评论 ({{ totalCommentCount }})</h2>

      <div class="comment-input" v-if="isLoggedIn">
        <div class="reply-indicator" v-if="replyingTo">
          <span>回复 @{{ replyingTo.nickname }}</span>
          <button class="cancel-reply-btn" @click="cancelReply">✕</button>
        </div>
        <textarea v-model="commentContent" :placeholder="replyingTo ? `回复 @${replyingTo.nickname}...` : '发一条友善的评论吧~'" rows="3"></textarea>
        <button @click="submitComment" :disabled="!commentContent.trim()">发表评论</button>
      </div>
      <div class="comment-login-hint" v-else>
        <router-link to="/login">登录</router-link>后即可发表评论
      </div>

      <div class="comment-list" v-if="comments.length > 0">
        <CommentItem
          v-for="comment in comments"
          :key="comment.id"
          :comment="comment"
          :depth="0"
          @reply="handleReplyFromItem"
          @like="handleLikeFromItem"
          @delete="handleDeleteFromItem"
        />
      </div>

      <div v-if="hasMoreComments" class="load-more-comments">
        <button @click="loadMoreComments" class="load-more-btn">加载更多评论</button>
      </div>
    </div>

    <div v-if="showFavoriteModal" class="modal-overlay" @click.self="cancelFavoriteModal">
      <div class="modal">
        <h3>选择收藏夹</h3>
        <div class="folder-list">
          <div
            v-for="folder in folderCheckList"
            :key="folder.id"
            :class="['folder-check-item', { checked: folder.checked }]"
            @click="toggleFolderCheck(folder)"
          >
            <span class="checkbox">{{ folder.checked ? '☑' : '☐' }}</span>
            <span class="folder-name">{{ folder.name }}</span>
          </div>
          <div v-if="newFolderName !== null" class="new-folder-row">
            <input
              v-model="newFolderName"
              placeholder="输入收藏夹名称"
              class="new-folder-input"
              @keyup.enter="confirmNewFolder"
              ref="newFolderInputRef"
            />
            <button @click="confirmNewFolder" class="new-folder-confirm-btn">✓</button>
            <button @click="cancelNewFolder" class="new-folder-cancel-btn">✕</button>
          </div>
        </div>
        <div class="modal-footer">
          <button @click="startNewFolder" class="new-folder-btn" v-if="newFolderName === null">+ 新建收藏夹</button>
          <button @click="confirmFavorite" class="confirm-btn">确认</button>
          <button @click="cancelFavoriteModal" class="cancel-btn">取消</button>
        </div>
      </div>
    </div>
  </div>

  <div v-else-if="loading" class="loading">加载中...</div>
  <div v-else class="error">视频不存在或已下架</div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getVideoDetail } from '../api/video'
import { getCommentList, createComment, getCommentReplies, deleteComment as deleteCommentApi } from '../api/comment'
import { toggleLike, doubleTap } from '../api/like'
import { getFoldersWithStatus, batchUpdateFavorite, createFolder } from '../api/favorite'
import { toggleFollow, checkFollow } from '../api/follow'
import { useUserStore } from '../stores/user'
import CommentItem from '../components/CommentItem.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const video = ref(null)
const comments = ref([])
const commentContent = ref('')
const loading = ref(true)
const showFavoriteModal = ref(false)
const folderCheckList = ref([])
const newFolderName = ref(null)
const newFolderInputRef = ref(null)
const replyingTo = ref(null)
const nextCursor = ref(null)
const nextCursorId = ref(null)
const hasMoreComments = ref(false)
const totalCommentCount = ref(0)
const isFollowed = ref(false)

const isLoggedIn = computed(() => userStore.isLoggedIn)
const currentUserId = computed(() => userStore.userInfo?.id)

onMounted(async () => {
  const videoId = route.params.id
  try {
    const res = await getVideoDetail(videoId)
    video.value = res.data
    if (isLoggedIn.value && video.value.userId !== currentUserId.value) {
      try {
        const followRes = await checkFollow(video.value.userId)
        isFollowed.value = followRes.data.followed
      } catch (e) {
        console.error('获取关注状态失败:', e)
      }
    }
  } catch (e) {
    console.error('获取视频详情失败:', e)
  } finally {
    loading.value = false
  }

  await loadComments()
})

async function loadComments() {
  const videoId = route.params.id
  try {
    const params = { targetType: 1, targetId: videoId, sort: 'hot', size: 10 }
    if (nextCursor.value !== null) {
      params.cursor = nextCursor.value
      params.cursorId = nextCursorId.value
    }
    const res = await getCommentList(params)
    const data = res.data
    if (nextCursor.value === null) {
      comments.value = data.list || []
    } else {
      comments.value = [...comments.value, ...(data.list || [])]
    }
    nextCursor.value = data.nextCursor
    nextCursorId.value = data.nextCursorId
    hasMoreComments.value = data.nextCursor !== null && data.nextCursor !== undefined
    totalCommentCount.value = comments.value.length
  } catch (e) {
    console.error('获取评论失败:', e)
  }
}

async function loadMoreComments() {
  await loadComments()
}

async function loadMoreReplies(comment) {
  try {
    const res = await getCommentReplies(comment.id)
    const allReplies = res.data || []
    comment.replies = allReplies
    comment.hasMoreReplies = false
  } catch (e) {
    console.error('加载更多回复失败:', e)
  }
}

async function handleLike() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await toggleLike({ targetType: 1, targetId: video.value.id })
    video.value.isLiked = res.data.liked
    video.value.likeCount += res.data.liked ? 1 : -1
  } catch (e) {
    console.error('点赞失败:', e)
  }
}

async function handleFollow() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await toggleFollow(video.value.userId)
    isFollowed.value = res.data.followed
  } catch (e) {
    console.error('关注失败:', e)
  }
}

async function handleFavorite() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await getFoldersWithStatus(1, video.value.id)
    folderCheckList.value = res.data.map(f => ({
      id: f.id,
      name: f.name,
      isDefault: f.isDefault,
      checked: !!f.isFavorited
    }))
    newFolderName.value = null
    showFavoriteModal.value = true
  } catch (e) {
    console.error('获取收藏夹失败:', e)
  }
}

function toggleFolderCheck(folder) {
  folder.checked = !folder.checked
}

function startNewFolder() {
  newFolderName.value = ''
  setTimeout(() => {
    newFolderInputRef.value?.focus()
  }, 50)
}

function cancelNewFolder() {
  newFolderName.value = null
}

async function confirmNewFolder() {
  const name = newFolderName.value?.trim()
  if (!name) {
    alert('收藏夹名称不能为空')
    return
  }
  try {
    const res = await createFolder(name)
    const newFolder = {
      id: res.data.id,
      name: res.data.name,
      isDefault: res.data.isDefault,
      checked: true
    }
    folderCheckList.value.push(newFolder)
    newFolderName.value = null
  } catch (e) {
    console.error('创建收藏夹失败:', e)
    alert('创建收藏夹失败: ' + (e.message || '未知错误'))
  }
}

async function confirmFavorite() {
  const checkedFolderIds = folderCheckList.value
    .filter(f => f.checked)
    .map(f => f.id)

  try {
    const res = await batchUpdateFavorite({
      folderIds: checkedFolderIds,
      targetType: 1,
      targetId: video.value.id
    })
    video.value.isFavorited = res.data.favorited
    showFavoriteModal.value = false
  } catch (e) {
    console.error('收藏操作失败:', e)
    alert('收藏操作失败: ' + (e.message || '未知错误'))
  }
}

function cancelFavoriteModal() {
  showFavoriteModal.value = false
  newFolderName.value = null
}

async function handleDoubleTap() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await doubleTap(video.value.id)
    const data = res.data
    // 根据后端返回的实际状态更新UI，而不是盲目+1
    if (data.liked && !video.value.isLiked) {
      // 之前未点赞，现在点赞了，点赞数+1
      video.value.likeCount += 1
    }
    video.value.isLiked = data.liked
    video.value.isFavorited = data.favorited
    alert(data.message || '一键二连成功！')
  } catch (e) {
    console.error('一键二连失败:', e)
  }
}

async function submitComment() {
  if (!commentContent.value.trim()) return
  try {
    await createComment({
      content: commentContent.value,
      targetType: 1,
      targetId: video.value.id,
      parentId: replyingTo.value ? replyingTo.value.id : null
    })
    commentContent.value = ''
    replyingTo.value = null
    nextCursor.value = null
    nextCursorId.value = null
    await loadComments()
  } catch (e) {
    console.error('发表评论失败:', e)
  }
}

function replyTo(comment) {
  replyingTo.value = comment
  commentContent.value = `@${comment.nickname} `
}

function cancelReply() {
  replyingTo.value = null
  commentContent.value = ''
}

async function likeComment(comment) {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  try {
    const res = await toggleLike({ targetType: 2, targetId: comment.id })
    comment.isLiked = res.data.liked
    comment.likeCount += res.data.liked ? 1 : -1
  } catch (e) {
    console.error('点赞评论失败:', e)
  }
}

function canDeleteComment(comment) {
  if (!isLoggedIn.value) return false
  return comment.userId === currentUserId.value || userStore.user?.role === 2
}

async function deleteComment(comment) {
  if (!confirm('确定删除这条评论吗？')) return
  try {
    await deleteCommentApi(comment.id)
    nextCursor.value = null
    nextCursorId.value = null
    await loadComments()
  } catch (e) {
    console.error('删除评论失败:', e)
    alert('删除评论失败: ' + (e.message || '未知错误'))
  }
}

function handleReplyFromItem(comment) {
  replyTo(comment)
}

function handleLikeFromItem(comment) {
  likeComment(comment)
}

async function handleDeleteFromItem(comment) {
  await deleteComment(comment)
}

function goUserCenter(userId) {
  router.push(`/user/${userId}`)
}

function formatCount(num) {
  if (!num) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return num.toString()
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.video-detail {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.player-section {
  width: 100%;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 20px;
}

.video-player {
  width: 100%;
  max-height: 600px;
}

.player-placeholder {
  width: 100%;
  padding-top: 56.25%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
}

.content-section {
  margin-bottom: 32px;
}

.title {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 12px;
}

.video-meta {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
  font-size: 14px;
  color: #999;
}

.category-tag {
  background: #e6f7ff;
  color: #00a1d6;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 13px;
}

.author-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 16px;
}

.author-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.author-info:hover .author-name {
  color: #00a1d6;
}

.follow-btn {
  padding: 6px 20px;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #00a1d6;
  background: #00a1d6;
  color: #fff;
}

.follow-btn:hover {
  background: #0091c7;
  border-color: #0091c7;
}

.follow-btn.following {
  background: #fff;
  color: #00a1d6;
}

.follow-btn.following:hover {
  background: #e6f7ff;
}

.author-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.author-avatar-placeholder {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #00a1d6;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.author-name {
  font-size: 16px;
  font-weight: 500;
}

.actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 8px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.action-btn:hover {
  border-color: #00a1d6;
  color: #00a1d6;
}

.action-btn.active {
  background: #00a1d6;
  color: #fff;
  border-color: #00a1d6;
}

.action-btn.double-tap {
  background: linear-gradient(135deg, #ff6b6b, #ffa500);
  color: #fff;
  border: none;
}

.description {
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  line-height: 1.8;
  color: #333;
}

.comment-section {
  margin-top: 32px;
}

.comment-section h2 {
  font-size: 18px;
  margin-bottom: 16px;
}

.comment-input {
  margin-bottom: 20px;
}

.reply-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #e6f7ff;
  border-radius: 4px 4px 0 0;
  font-size: 13px;
  color: #00a1d6;
}

.cancel-reply-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #999;
  font-size: 14px;
  padding: 0 4px;
}

.cancel-reply-btn:hover {
  color: #ff4d4f;
}

.comment-input textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 0 0 8px 8px;
  resize: vertical;
  font-size: 14px;
  font-family: inherit;
}

.comment-input button {
  margin-top: 8px;
  padding: 8px 24px;
  background: #00a1d6;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.comment-input button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.comment-login-hint {
  padding: 20px;
  text-align: center;
  color: #999;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 20px;
}

.comment-login-hint a {
  color: #00a1d6;
}

.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.comment-main {
  display: flex;
  gap: 12px;
}

.comment-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  flex-shrink: 0;
}

.comment-avatar-placeholder {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #00a1d6;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  cursor: pointer;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.comment-user {
  font-weight: 500;
  color: #333;
  cursor: pointer;
}

.comment-user:hover {
  color: #00a1d6;
}

.comment-time {
  font-size: 13px;
  color: #999;
}

.comment-content {
  line-height: 1.6;
  color: #333;
  margin-bottom: 8px;
}

.comment-actions {
  display: flex;
  gap: 16px;
}

.comment-action-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: #999;
  padding: 0;
}

.comment-action-btn:hover {
  color: #00a1d6;
}

.comment-action-btn.small {
  font-size: 12px;
}

.comment-action-btn.delete-btn {
  color: #ccc;
}

.comment-action-btn.delete-btn:hover {
  color: #ff4d4f;
}

.replies {
  margin-top: 12px;
  margin-left: 20px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}

.reply-item {
  padding: 8px 0;
  display: flex;
  gap: 8px;
}

.reply-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  flex-shrink: 0;
}

.reply-avatar.tiny {
  width: 22px;
  height: 22px;
}

.reply-avatar-placeholder {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #fb7299;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  cursor: pointer;
  flex-shrink: 0;
}

.reply-avatar-placeholder.tiny {
  width: 22px;
  height: 22px;
  font-size: 10px;
}

.reply-body {
  flex: 1;
  min-width: 0;
}

.reply-user {
  font-weight: 500;
  color: #00a1d6;
  margin-right: 6px;
  cursor: pointer;
  font-size: 13px;
}

.reply-user:hover {
  text-decoration: underline;
}

.reply-content {
  color: #333;
  font-size: 14px;
}

.reply-actions {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.nested-replies {
  margin-top: 8px;
  margin-left: 12px;
  padding-left: 12px;
  border-left: 2px solid #e0e0e0;
}

.nested-reply-item {
  padding: 6px 0;
  display: flex;
  gap: 6px;
}

.load-more-replies-btn {
  display: block;
  width: 100%;
  padding: 8px;
  margin-top: 8px;
  background: none;
  border: 1px dashed #e0e0e0;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #00a1d6;
  transition: all 0.2s;
}

.load-more-replies-btn:hover {
  background: #e6f7ff;
  border-color: #00a1d6;
}

.load-more-comments {
  text-align: center;
  padding: 20px;
}

.load-more-btn {
  padding: 10px 32px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  color: #00a1d6;
  transition: all 0.2s;
}

.load-more-btn:hover {
  background: #e6f7ff;
  border-color: #00a1d6;
}

.loading, .error {
  text-align: center;
  padding: 100px 0;
  color: #999;
  font-size: 16px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  width: 400px;
  max-height: 60vh;
  overflow-y: auto;
}

.modal h3 {
  margin-bottom: 16px;
}

.folder-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 50vh;
  overflow-y: auto;
}

.folder-check-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.folder-check-item:hover {
  border-color: #00a1d6;
  color: #00a1d6;
}

.folder-check-item.checked {
  border-color: #00a1d6;
  background: #e6f7ff;
  color: #00a1d6;
}

.folder-check-item .checkbox {
  font-size: 18px;
  width: 24px;
  text-align: center;
  flex-shrink: 0;
}

.folder-check-item .folder-name {
  flex: 1;
  font-size: 14px;
}

.new-folder-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 2px dashed #00a1d6;
  border-radius: 8px;
  background: #f0faff;
}

.new-folder-input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  font-size: 14px;
  outline: none;
}

.new-folder-input:focus {
  border-color: #00a1d6;
}

.new-folder-confirm-btn,
.new-folder-cancel-btn {
  width: 30px;
  height: 30px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.new-folder-confirm-btn {
  background: #00a1d6;
  color: #fff;
  border-color: #00a1d6;
}

.new-folder-confirm-btn:hover {
  background: #0091c7;
}

.new-folder-cancel-btn {
  background: #fff;
  color: #999;
}

.new-folder-cancel-btn:hover {
  color: #ff4d4f;
  border-color: #ff4d4f;
}

.modal-footer {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  align-items: center;
}

.new-folder-btn {
  padding: 8px 16px;
  border: 1px dashed #00a1d6;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  color: #00a1d6;
  margin-right: auto;
}

.new-folder-btn:hover {
  background: #e6f7ff;
}

.confirm-btn {
  padding: 8px 24px;
  background: #00a1d6;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
}

.confirm-btn:hover {
  background: #0091c7;
}

.cancel-btn {
  margin-top: 16px;
  width: 100%;
  padding: 10px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}
</style>
