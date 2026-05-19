<template>
  <div class="user-center" v-if="user">
    <div class="user-header">
      <img v-if="user.avatar" :src="user.avatar" class="user-avatar" />
      <div v-else class="user-avatar-placeholder">{{ user.nickname?.charAt(0) || 'U' }}</div>
      <div class="user-info">
        <h1>{{ user.nickname }}</h1>
        <div class="user-stats">
          <span class="stat-item" @click="showFollowList('following')">{{ user.followCount }} 关注</span>
          <span class="stat-item" @click="showFollowList('followers')">{{ user.fansCount }} 粉丝</span>
        </div>
        <div class="user-role" v-if="user.role === 2">管理员</div>
      </div>
      <button
        v-if="isLoggedIn && currentUserId !== user.id"
        :class="{ following: user.isFollowed }"
        @click="handleFollow"
        class="follow-btn"
      >
        {{ user.isFollowed ? '已关注' : '+ 关注' }}
      </button>
    </div>

    <div class="tab-bar">
      <button :class="{ active: activeTab === 'videos' }" @click="activeTab = 'videos'">视频</button>
      <button :class="{ active: activeTab === 'posts' }" @click="activeTab = 'posts'">动态</button>
      <button v-if="isSelf" :class="{ active: activeTab === 'favorites' }" @click="activeTab = 'favorites'">收藏夹</button>
    </div>

    <div v-if="activeTab === 'videos'" class="content-section">
      <div v-if="videos.length === 0" class="empty">暂无视频</div>
      <div class="video-grid">
        <div
          v-for="video in videos"
          :key="video.id"
          class="video-card"
        >
          <div class="cover" @click="$router.push(`/video/${video.id}`)">
            <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title" />
            <div v-else class="cover-placeholder">暂无封面</div>
          </div>
          <div class="card-info">
            <h3>{{ video.title }}</h3>
            <span>{{ formatCount(video.viewCount) }}播放 · {{ formatCount(video.likeCount) }}点赞</span>
            <button v-if="isSelf" class="delete-btn" @click="handleDeleteVideo(video.id)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'posts'" class="content-section">
      <div v-if="posts.length === 0" class="empty">暂无动态</div>
      <div v-for="post in posts" :key="post.id" class="post-item" @click="goToPost(post.id)">
        <div class="post-header">
          <span class="post-time">{{ formatDate(post.createdAt) }}</span>
          <div class="post-actions-bar" v-if="isSelf" @click.stop>
            <button class="edit-btn" @click="startEditPost(post)">编辑</button>
            <button class="delete-btn" @click="handleDeletePost(post.id)">删除</button>
          </div>
        </div>
        <div class="post-content" v-if="post.content">{{ post.content }}</div>
        <div class="post-images" v-if="post.images && post.images.length > 0">
          <div :class="['post-image-grid', 'grid-' + Math.min(post.images.length, 9)]">
            <img v-for="(img, idx) in post.images" :key="idx" :src="img" class="post-thumb" />
          </div>
        </div>
        <div class="post-stats">
          <span>❤ {{ post.likeCount || 0 }}</span>
          <span>💬 {{ post.commentCount || 0 }}</span>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'favorites'" class="content-section">
      <div class="folder-header">
        <h3>我的收藏夹</h3>
        <button class="create-folder-btn" @click="startCreateFolder">+ 新建收藏夹</button>
      </div>
      <div v-if="newFolderVisible" class="new-folder-row">
        <input v-model="newFolderName" placeholder="输入收藏夹名称" class="new-folder-input" @keyup.enter="confirmCreateFolder" ref="newFolderInputRef" />
        <button @click="confirmCreateFolder" class="icon-btn confirm">✓</button>
        <button @click="cancelCreateFolder" class="icon-btn cancel">✕</button>
      </div>
      <div v-if="folders.length === 0" class="empty">暂无收藏夹</div>
      <div v-for="folder in folders" :key="folder.id" class="folder-item">
        <div class="folder-info" @click="goToFolder(folder.id)">
          <span class="folder-name">
            {{ folder.name }}
            <span v-if="folder.isDefault" class="default-tag">默认</span>
          </span>
          <span class="folder-count">{{ folder.videoCount }}个视频</span>
        </div>
        <div class="folder-actions" v-if="!folder.isDefault">
          <button class="action-btn rename" @click="startRenameFolder(folder)">重命名</button>
          <button class="action-btn delete" @click="handleDeleteFolder(folder.id)">删除</button>
        </div>
        <div v-if="renamingFolderId === folder.id" class="rename-row">
          <input v-model="renameFolderName" class="rename-input" @keyup.enter="confirmRenameFolder" />
          <button @click="confirmRenameFolder" class="icon-btn confirm">✓</button>
          <button @click="cancelRenameFolder" class="icon-btn cancel">✕</button>
        </div>
      </div>
    </div>

    <div v-if="showFollowModal" class="modal-overlay" @click.self="showFollowModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ followModalType === 'following' ? '关注列表' : '粉丝列表' }}</h3>
          <button class="close-btn" @click="showFollowModal = false">✕</button>
        </div>
        <div class="follow-list">
          <div v-if="followList.length === 0" class="empty">暂无数据</div>
          <div v-for="item in followList" :key="item.id" class="follow-item">
            <div class="follow-user" @click="goToUser(item.id)">
              <img v-if="item.avatar" :src="item.avatar" class="follow-avatar" />
              <div v-else class="follow-avatar-placeholder">{{ item.nickname?.charAt(0) || 'U' }}</div>
              <div class="follow-info">
                <span class="follow-nickname">{{ item.nickname }}</span>
                <span class="follow-counts">{{ item.followCount }}关注 · {{ item.fansCount }}粉丝</span>
              </div>
            </div>
            <button
              v-if="isSelf && followModalType === 'following'"
              :class="{ following: item.isFollowed }"
              @click.stop="handleUnfollow(item)"
              class="follow-sm-btn"
            >
              {{ item.isFollowed ? '已关注' : '+ 关注' }}
            </button>
            <button
              v-else-if="isLoggedIn && item.id !== currentUserId"
              :class="{ following: item.isFollowed }"
              @click.stop="handleToggleFollowInList(item)"
              class="follow-sm-btn"
            >
              {{ item.isFollowed ? '已关注' : '+ 关注' }}
            </button>
          </div>
        </div>
        <div class="pagination" v-if="followTotal > followPageSize">
          <button :disabled="followPage <= 1" @click="followPage--; loadFollowList()">上一页</button>
          <span>{{ followPage }} / {{ Math.ceil(followTotal / followPageSize) }}</span>
          <button :disabled="followPage >= Math.ceil(followTotal / followPageSize)" @click="followPage++; loadFollowList()">下一页</button>
        </div>
      </div>
    </div>

    <!-- 编辑动态弹窗 -->
    <div v-if="editPostVisible" class="modal-overlay" @click.self="editPostVisible = false">
      <div class="modal edit-modal">
        <div class="modal-header">
          <h3>编辑动态</h3>
          <button class="close-btn" @click="editPostVisible = false">✕</button>
        </div>
        <div class="edit-form">
          <textarea v-model="editContent" placeholder="修改动态内容..." rows="5" maxlength="5000"></textarea>
          <div class="edit-image-section">
            <div class="edit-image-list">
              <div v-for="(img, index) in editPreviewImages" :key="index" class="edit-preview-item">
                <img :src="img" class="edit-preview-img" />
                <button type="button" class="edit-remove-btn" @click="removeEditImage(index)">✕</button>
              </div>
              <label v-if="editPreviewImages.length < 9" class="edit-add-btn">
                <input type="file" accept="image/*" multiple @change="handleEditImageSelect" hidden />
                <span>+</span>
              </label>
            </div>
            <div class="edit-image-tip">最多9张图片，新增图片将追加到末尾</div>
          </div>
          <div class="edit-actions">
            <button class="edit-cancel-btn" @click="editPostVisible = false">取消</button>
            <button class="edit-save-btn" @click="saveEditPost" :disabled="editSaving || (!editContent.trim() && editPreviewImages.length === 0)">
              {{ editSaving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div v-else-if="loading" class="loading">加载中...</div>
  <div v-else class="error">用户不存在</div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserVideos, deleteVideo } from '../api/video'
import { getUserPosts, deletePost, updatePost } from '../api/post'
import { toggleFollow } from '../api/follow'
import { getFoldersWithCount, createFolder, renameFolder as renameFolderApi, deleteFolder } from '../api/favorite'
import { useUserStore } from '../stores/user'
import request from '../api/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const user = ref(null)
const videos = ref([])
const posts = ref([])
const activeTab = ref('videos')
const loading = ref(true)
const folders = ref([])
const newFolderVisible = ref(false)
const newFolderName = ref('')
const newFolderInputRef = ref(null)
const renamingFolderId = ref(null)
const renameFolderName = ref('')

const showFollowModal = ref(false)
const followModalType = ref('following')
const followList = ref([])
const followPage = ref(1)
const followPageSize = ref(10)
const followTotal = ref(0)

const editPostVisible = ref(false)
const editPostId = ref(null)
const editContent = ref('')
const editExistingImages = ref([])
const editNewFiles = ref([])
const editPreviewImages = ref([])
const editSaving = ref(false)

const isLoggedIn = computed(() => userStore.isLoggedIn)
const currentUserId = computed(() => userStore.userInfo?.id)
const isSelf = computed(() => isLoggedIn.value && currentUserId.value === Number(route.params.id))

onMounted(async () => {
  await loadAllData()
  loading.value = false
})

watch(() => route.params.id, async (newId, oldId) => {
  if (newId && newId !== oldId) {
    loading.value = true
    await loadAllData()
    loading.value = false
  }
})

async function loadAllData() {
  const userId = route.params.id
  try {
    const res = await request.get(`/user/public/${userId}`)
    user.value = res.data
  } catch (e) {
    console.error('获取用户信息失败:', e)
  }

  await loadVideos()
  await loadPosts()
  if (isSelf.value) {
    await loadFolders()
  }
}

async function loadVideos() {
  try {
    const res = await getUserVideos(route.params.id, { page: 1, size: 50 })
    videos.value = res.data.list
  } catch (e) {
    console.error('获取用户视频失败:', e)
  }
}

async function loadPosts() {
  try {
    const res = await getUserPosts(route.params.id, { size: 50 })
    posts.value = res.data.list || []
  } catch (e) {
    console.error('获取用户动态失败:', e)
  }
}

async function loadFolders() {
  try {
    const res = await getFoldersWithCount()
    folders.value = res.data
  } catch (e) {
    console.error('获取收藏夹失败:', e)
  }
}

async function handleFollow() {
  try {
    const res = await toggleFollow(user.value.id)
    user.value.isFollowed = res.data.followed
    user.value.fansCount += res.data.followed ? 1 : -1
  } catch (e) {
    console.error('关注失败:', e)
    alert(e.message || '操作失败')
  }
}

async function handleDeleteVideo(videoId) {
  if (!confirm('确定要删除这个视频吗？')) return
  try {
    await deleteVideo(videoId)
    videos.value = videos.value.filter(v => v.id !== videoId)
  } catch (e) {
    alert('删除失败: ' + (e.message || '未知错误'))
  }
}

async function handleDeletePost(postId) {
  if (!confirm('确定要删除这条动态吗？')) return
  try {
    await deletePost(postId)
    posts.value = posts.value.filter(p => p.id !== postId)
  } catch (e) {
    alert('删除失败: ' + (e.message || '未知错误'))
  }
}

function startEditPost(post) {
  editPostId.value = post.id
  editContent.value = post.content || ''
  editExistingImages.value = post.images ? [...post.images] : []
  editNewFiles.value = []
  editPreviewImages.value = [...editExistingImages.value]
  editPostVisible.value = true
}

function removeEditImage(index) {
  if (index < editExistingImages.value.length) {
    editExistingImages.value.splice(index, 1)
  } else {
    const newFileIndex = index - editExistingImages.value.length
    editNewFiles.value.splice(newFileIndex, 1)
  }
  editPreviewImages.value.splice(index, 1)
}

function handleEditImageSelect(event) {
  const files = Array.from(event.target.files)
  const remaining = 9 - editPreviewImages.value.length
  const toAdd = files.slice(0, remaining)
  for (const file of toAdd) {
    if (!file.type.startsWith('image/')) continue
    if (file.size > 5 * 1024 * 1024) {
      alert('图片大小不能超过5MB: ' + file.name)
      continue
    }
    editNewFiles.value.push(file)
    const reader = new FileReader()
    reader.onload = (e) => {
      editPreviewImages.value.push(e.target.result)
    }
    reader.readAsDataURL(file)
  }
  event.target.value = ''
}

async function saveEditPost() {
  if (!editContent.value.trim() && editPreviewImages.value.length === 0) return
  editSaving.value = true
  try {
    const formData = new FormData()
    if (editContent.value.trim()) {
      formData.append('content', editContent.value.trim())
    }
    formData.append('existingImages', editExistingImages.value.join(','))
    for (const file of editNewFiles.value) {
      formData.append('images', file)
    }
    await updatePost(editPostId.value, formData)
    editPostVisible.value = false
    await loadPosts()
  } catch (e) {
    alert('保存失败: ' + (e.message || '未知错误'))
  } finally {
    editSaving.value = false
  }
}

function startCreateFolder() {
  newFolderVisible.value = true
  newFolderName.value = ''
  nextTick(() => newFolderInputRef.value?.focus())
}

function cancelCreateFolder() {
  newFolderVisible.value = false
  newFolderName.value = ''
}

async function confirmCreateFolder() {
  if (!newFolderName.value.trim()) {
    alert('收藏夹名称不能为空')
    return
  }
  try {
    await createFolder(newFolderName.value.trim())
    newFolderVisible.value = false
    newFolderName.value = ''
    await loadFolders()
  } catch (e) {
    alert('创建失败: ' + (e.message || '未知错误'))
  }
}

function startRenameFolder(folder) {
  renamingFolderId.value = folder.id
  renameFolderName.value = folder.name
}

function cancelRenameFolder() {
  renamingFolderId.value = null
  renameFolderName.value = ''
}

async function confirmRenameFolder() {
  if (!renameFolderName.value.trim()) {
    alert('收藏夹名称不能为空')
    return
  }
  try {
    await renameFolderApi(renamingFolderId.value, renameFolderName.value.trim())
    renamingFolderId.value = null
    renameFolderName.value = ''
    await loadFolders()
  } catch (e) {
    alert('重命名失败: ' + (e.message || '未知错误'))
  }
}

async function handleDeleteFolder(folderId) {
  if (!confirm('确定要删除这个收藏夹吗？其中的所有视频也会被移除。')) return
  try {
    await deleteFolder(folderId)
    await loadFolders()
  } catch (e) {
    alert('删除失败: ' + (e.message || '未知错误'))
  }
}

function goToFolder(folderId) {
  router.push(`/favorite/${folderId}`)
}

function showFollowList(type) {
  followModalType.value = type
  followPage.value = 1
  showFollowModal.value = true
  loadFollowList()
}

async function loadFollowList() {
  const userId = route.params.id
  const type = followModalType.value
  try {
    const res = await request.get(`/follow/${type}/${userId}`, { params: { page: followPage.value, size: followPageSize.value } })
    followList.value = res.data || []
    const countRes = await request.get(`/follow/count/${userId}`)
    followTotal.value = type === 'following' ? (countRes.data?.following || 0) : (countRes.data?.followers || 0)
  } catch (e) {
    console.error('获取关注列表失败:', e)
    followList.value = []
  }
}

async function handleUnfollow(item) {
  try {
    const res = await toggleFollow(item.id)
    item.isFollowed = res.data.followed
    if (user.value) {
      user.value.followCount += res.data.followed ? 1 : -1
    }
    await loadFollowList()
  } catch (e) {
    alert('操作失败: ' + (e.message || '未知错误'))
  }
}

async function handleToggleFollowInList(item) {
  try {
    const res = await toggleFollow(item.id)
    item.isFollowed = res.data.followed
  } catch (e) {
    alert('操作失败: ' + (e.message || '未知错误'))
  }
}

function goToUser(userId) {
  showFollowModal.value = false
  if (Number(route.params.id) === Number(userId)) {
    return
  }
  router.push(`/user/${userId}`)
}

function goToPost(postId) {
  router.push(`/post/${postId}`)
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
.user-center {
  max-width: 1000px;
  margin: 0 auto;
  padding: 30px 20px;
}

.user-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  margin-bottom: 24px;
}

.user-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
}

.user-avatar-placeholder {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #00a1d6, #00c6ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
}

.user-info {
  flex: 1;
}

.user-info h1 {
  font-size: 22px;
  margin-bottom: 8px;
}

.user-stats {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: #666;
}

.stat-item {
  cursor: pointer;
}

.stat-item:hover {
  color: #00a1d6;
}

.user-role {
  display: inline-block;
  margin-top: 6px;
  padding: 2px 10px;
  background: #fff7e6;
  color: #fa8c16;
  border-radius: 12px;
  font-size: 12px;
}

.follow-btn {
  padding: 10px 24px;
  border-radius: 20px;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #00a1d6;
  background: #00a1d6;
  color: #fff;
}

.follow-btn.following {
  background: #fff;
  color: #00a1d6;
}

.tab-bar {
  display: flex;
  gap: 0;
  margin-bottom: 20px;
  border-bottom: 2px solid #f0f0f0;
}

.tab-bar button {
  padding: 12px 24px;
  border: none;
  background: none;
  font-size: 15px;
  cursor: pointer;
  color: #666;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}

.tab-bar button.active {
  color: #00a1d6;
  border-bottom-color: #00a1d6;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.video-card {
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
  cursor: pointer;
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
  position: relative;
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

.delete-btn {
  padding: 2px 8px;
  font-size: 12px;
  border: 1px solid #ff4d4f;
  color: #ff4d4f;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
}

.delete-btn:hover {
  background: #ff4d4f;
  color: #fff;
}

.post-actions-bar {
  display: flex;
  gap: 6px;
}

.edit-btn {
  padding: 2px 8px;
  font-size: 12px;
  border: 1px solid #00a1d6;
  color: #00a1d6;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
}

.edit-btn:hover {
  background: #00a1d6;
  color: #fff;
}

.post-item {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  margin-bottom: 12px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.post-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.post-header {
  margin-bottom: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.post-time {
  font-size: 13px;
  color: #999;
}

.post-content {
  line-height: 1.8;
  color: #333;
  margin-bottom: 12px;
}

.post-stats {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #999;
}

.post-images {
  margin-bottom: 12px;
}

.post-image-grid {
  display: grid;
  gap: 4px;
}

.post-image-grid.grid-1 { grid-template-columns: 1fr; max-width: 240px; }
.post-image-grid.grid-2 { grid-template-columns: 1fr 1fr; max-width: 320px; }
.post-image-grid.grid-3 { grid-template-columns: 1fr 1fr 1fr; max-width: 360px; }
.post-image-grid.grid-4 { grid-template-columns: 1fr 1fr; max-width: 320px; }
.post-image-grid.grid-5,
.post-image-grid.grid-6 { grid-template-columns: 1fr 1fr 1fr; max-width: 360px; }
.post-image-grid.grid-7,
.post-image-grid.grid-8,
.post-image-grid.grid-9 { grid-template-columns: 1fr 1fr 1fr; max-width: 360px; }

.post-thumb {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 4px;
}

.folder-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.folder-header h3 {
  font-size: 16px;
}

.create-folder-btn {
  padding: 6px 16px;
  border: 1px dashed #00a1d6;
  border-radius: 6px;
  background: #fff;
  color: #00a1d6;
  cursor: pointer;
  font-size: 14px;
}

.create-folder-btn:hover {
  background: #e6f7ff;
}

.new-folder-row,
.rename-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 2px dashed #00a1d6;
  border-radius: 8px;
  background: #f0faff;
  margin-bottom: 8px;
}

.new-folder-input,
.rename-input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  font-size: 14px;
  outline: none;
}

.new-folder-input:focus,
.rename-input:focus {
  border-color: #00a1d6;
}

.icon-btn {
  width: 28px;
  height: 28px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-btn.confirm {
  background: #00a1d6;
  color: #fff;
  border-color: #00a1d6;
}

.icon-btn.cancel {
  background: #fff;
  color: #999;
}

.icon-btn.cancel:hover {
  color: #ff4d4f;
  border-color: #ff4d4f;
}

.folder-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.folder-info {
  cursor: pointer;
  flex: 1;
}

.folder-info:hover .folder-name {
  color: #00a1d6;
}

.folder-name {
  font-size: 15px;
  font-weight: 500;
}

.default-tag {
  font-size: 11px;
  padding: 1px 6px;
  background: #e6f7ff;
  color: #00a1d6;
  border-radius: 4px;
  margin-left: 6px;
  font-weight: normal;
}

.folder-count {
  font-size: 13px;
  color: #999;
  margin-left: 12px;
}

.folder-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid;
}

.action-btn.rename {
  border-color: #00a1d6;
  color: #00a1d6;
  background: #fff;
}

.action-btn.rename:hover {
  background: #e6f7ff;
}

.action-btn.delete {
  border-color: #ff4d4f;
  color: #ff4d4f;
  background: #fff;
}

.action-btn.delete:hover {
  background: #fff1f0;
}

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
  z-index: 1000;
}

.modal {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  width: 500px;
  max-width: 90vw;
  max-height: 80vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.modal-header h3 {
  font-size: 18px;
}

.close-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: #f0f0f0;
  border-radius: 50%;
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: #e0e0e0;
}

.follow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.follow-user {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.follow-user:hover .follow-nickname {
  color: #00a1d6;
}

.follow-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.follow-avatar-placeholder {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #00a1d6, #00c6ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.follow-nickname {
  font-size: 14px;
  font-weight: 500;
}

.follow-counts {
  font-size: 12px;
  color: #999;
}

.follow-sm-btn {
  padding: 4px 14px;
  border-radius: 14px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid #00a1d6;
  background: #00a1d6;
  color: #fff;
}

.follow-sm-btn.following {
  background: #fff;
  color: #00a1d6;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  font-size: 14px;
  color: #666;
}

.pagination button {
  padding: 4px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.edit-modal {
  width: 600px;
}

.edit-form textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  resize: vertical;
  font-size: 14px;
  font-family: inherit;
  box-sizing: border-box;
  line-height: 1.6;
}

.edit-form textarea:focus {
  border-color: #00a1d6;
  outline: none;
}

.edit-image-section {
  margin-top: 12px;
}

.edit-image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.edit-preview-item {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 6px;
  overflow: hidden;
}

.edit-preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.edit-remove-btn {
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

.edit-add-btn {
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

.edit-add-btn:hover {
  border-color: #00a1d6;
}

.edit-image-tip {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.edit-cancel-btn {
  padding: 8px 20px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}

.edit-save-btn {
  padding: 8px 20px;
  background: #00a1d6;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.edit-save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty {
  text-align: center;
  padding: 60px 0;
  color: #999;
}

.loading, .error {
  text-align: center;
  padding: 100px 0;
  color: #999;
  font-size: 16px;
}
</style>
