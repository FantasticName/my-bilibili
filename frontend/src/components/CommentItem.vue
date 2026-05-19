<template>
  <div class="comment-item" :style="{ marginLeft: Math.min(depth, 5) * 20 + 'px' }">
    <div class="comment-main">
      <img v-if="comment.avatar" :src="comment.avatar" class="comment-avatar" @click="goUserCenter(comment.userId)" />
      <div v-else class="comment-avatar-placeholder" @click="goUserCenter(comment.userId)">U</div>
      <div class="comment-body">
        <div class="comment-header">
          <span class="comment-user" @click="goUserCenter(comment.userId)">{{ comment.nickname }}</span>
          <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
        </div>
        <div class="comment-content">{{ comment.content }}</div>
        <div class="comment-actions">
          <button @click="likeComment" class="comment-action-btn">
            {{ comment.isLiked ? '❤' : '🤍' }} {{ comment.likeCount || 0 }}
          </button>
          <button @click="replyTo" class="comment-action-btn">回复</button>
          <button v-if="canDelete" @click="deleteComment" class="comment-action-btn delete-btn">删除</button>
        </div>

        <!-- 子回复列表（递归调用自己） -->
        <div v-if="comment.replies && comment.replies.length > 0" class="replies">
          <CommentItem
            v-for="reply in comment.replies"
            :key="reply.id"
            :comment="reply"
            :depth="depth + 1"
            @reply="handleReply"
            @like="handleLike"
            @delete="handleDelete"
          />
        </div>

        <!-- 展开更多回复按钮：hasMoreReplies=true 或 还有下一页 -->
        <button
          v-if="comment.hasMoreReplies || hasNextPage"
          @click="loadMoreReplies"
          :disabled="loading"
          class="load-more-replies-btn"
        >
          {{ loading ? '加载中...' : (comment.replies && comment.replies.length > 0 ? '展开更多回复' : '展开回复') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCommentReplies } from '../api/comment'

const props = defineProps({
  comment: { type: Object, required: true },
  depth: { type: Number, default: 0 }
})

const emit = defineEmits(['reply', 'like', 'delete'])

defineOptions({ name: 'CommentItem' })

const router = useRouter()
const userStore = useUserStore()

// 游标分页状态：记录下一页的游标，用于继续加载更多子回复
const replyNextCursor = ref(null)
const replyNextCursorId = ref(null)
// 是否已加载过子回复（区分首次加载和加载更多）
const replyLoaded = ref(false)
// 加载中状态，防止重复点击
const loading = ref(false)

// 是否还有下一页（游标不为null表示还有更多数据）
const hasNextPage = computed(() => {
  return replyLoaded.value && replyNextCursor.value !== null
})

const canDelete = computed(() => {
  if (!userStore.isLoggedIn) return false
  if (userStore.isAdmin) return true
  return userStore.userId === props.comment.userId
})

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 30) return `${days}天前`
  return date.toLocaleDateString()
}

function goUserCenter(userId) {
  if (userId) router.push({ path: '/user-center', query: { userId } })
}

function likeComment() {
  emit('like', props.comment)
}

function replyTo() {
  emit('reply', props.comment)
}

function deleteComment() {
  emit('delete', props.comment)
}

function handleReply(comment) {
  emit('reply', comment)
}

function handleLike(comment) {
  emit('like', comment)
}

function handleDelete(comment) {
  emit('delete', comment)
}

async function loadMoreReplies() {
  if (loading.value) return
  loading.value = true

  try {
    // 首次加载不传游标，加载更多时传上一页的游标
    const cursor = replyLoaded.value ? replyNextCursor.value : null
    const cursorId = replyLoaded.value ? replyNextCursorId.value : null
    const res = await getCommentReplies(props.comment.id, cursor, cursorId, 10)
    const data = res.data || res
    const list = data.list || []

    if (!replyLoaded.value) {
      // 首次加载：替换
      props.comment.replies = list
      replyLoaded.value = true
    } else {
      // 加载更多：追加到已有列表
      props.comment.replies = [...(props.comment.replies || []), ...list]
    }

    // 更新游标
    replyNextCursor.value = data.nextCursor
    replyNextCursorId.value = data.nextCursorId

    // 如果没有下一页了，隐藏按钮
    if (data.nextCursor === null) {
      props.comment.hasMoreReplies = false
    }
  } catch (e) {
    console.error('加载回复失败:', e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
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
  border-radius: 6px;
}

.load-more-replies-btn {
  display: block;
  width: 100%;
  padding: 8px 0;
  margin-top: 8px;
  background: none;
  border: none;
  border-top: 1px solid #e8e8e8;
  color: #00a1d6;
  font-size: 13px;
  cursor: pointer;
  text-align: center;
}

.load-more-replies-btn:hover {
  color: #0089bb;
}

.load-more-replies-btn:disabled {
  color: #ccc;
  cursor: not-allowed;
}
</style>
