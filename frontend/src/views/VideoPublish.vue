<template>
  <div class="publish-container">
    <div class="publish-card">
      <h2>发布视频</h2>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>视频标题</label>
          <input v-model="form.title" type="text" required placeholder="请输入视频标题" maxlength="100" />
        </div>
        <div class="form-group">
          <label>分区</label>
          <select v-model="form.category" required>
            <option value="">请选择分区</option>
            <option value="动画">动画</option>
            <option value="音乐">音乐</option>
            <option value="游戏">游戏</option>
            <option value="知识">知识</option>
            <option value="生活">生活</option>
            <option value="娱乐">娱乐</option>
            <option value="影视">影视</option>
            <option value="科技">科技</option>
            <option value="运动">运动</option>
          </select>
        </div>
        <div class="form-group">
          <label>视频简介</label>
          <textarea v-model="form.description" rows="4" placeholder="介绍一下你的视频吧" maxlength="2000"></textarea>
        </div>
        <div class="form-group">
          <label>封面图片</label>
          <div class="file-upload-area" @click="triggerCoverInput" :class="{ 'has-file': uploadedCover }">
            <template v-if="coverPreviewUrl">
              <img :src="coverPreviewUrl" alt="封面预览" class="cover-preview" />
              <div class="file-hover-msg">点击更换封面</div>
            </template>
            <template v-else>
              <div class="upload-placeholder">
                <svg viewBox="0 0 24 24" width="48" height="48" fill="#999"><path d="M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-4.86 8.86l-3 3.87L9 13.14 6 17h12l-3.86-5.14z"/></svg>
                <p>点击选择封面图片</p>
                <p class="file-hint">支持 JPG、PNG、GIF，大小不超过 5MB</p>
              </div>
            </template>
          </div>
          <input ref="coverInputRef" type="file" accept="image/*" @change="handleCoverSelected" class="file-input-hidden" />
          <div v-if="coverUploading" class="upload-progress">
            <div class="progress-bar"><div class="progress-fill" :style="{ width: coverProgress + '%' }"></div></div>
            <span>{{ coverProgress }}%</span>
          </div>
        </div>
        <div class="form-group">
          <label>视频文件</label>
          <div class="file-upload-area" @click="triggerVideoInput" :class="{ 'has-file': uploadedVideo }">
            <template v-if="selectedVideoName">
              <div class="video-selected">
                <svg viewBox="0 0 24 24" width="40" height="40" fill="#4CAF50"><path d="M8 5v14l11-7z"/></svg>
                <div class="video-file-info">
                  <p class="video-file-name">{{ selectedVideoName }}</p>
                  <p class="video-file-size">{{ formatFileSize(selectedVideoSize) }}</p>
                </div>
                <div class="file-hover-msg">点击更换视频</div>
              </div>
            </template>
            <template v-else>
              <div class="upload-placeholder">
                <svg viewBox="0 0 24 24" width="48" height="48" fill="#999"><path d="M10 8v8l5-4-5-4zm9-5H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z"/></svg>
                <p>点击选择视频文件</p>
                <p class="file-hint">支持 MP4、AVI、MOV、MKV、FLV、WMV、WebM，大小不超过 500MB</p>
              </div>
            </template>
          </div>
          <input ref="videoInputRef" type="file" accept="video/*" @change="handleVideoSelected" class="file-input-hidden" />
          <div v-if="videoUploading" class="upload-progress">
            <div class="progress-bar"><div class="progress-fill" :style="{ width: videoProgress + '%' }"></div></div>
            <span>{{ videoProgress }}%</span>
          </div>
        </div>
        <button type="submit" class="submit-btn" :disabled="submitting || coverUploading || videoUploading">
          {{ submitting ? '发布中...' : '发布视频' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { publishVideo } from '../api/video'
import { uploadVideo, uploadCover } from '../api/file'

const router = useRouter()

const form = reactive({
  title: '',
  category: '',
  description: '',
  videoUrl: '',
  coverUrl: '',
})

const submitting = ref(false)

const coverInputRef = ref(null)
const videoInputRef = ref(null)

const uploadedCover = ref('')
const uploadedVideo = ref('')
const coverPreviewUrl = ref('')
const coverUploading = ref(false)
const coverProgress = ref(0)
const videoUploading = ref(false)
const videoProgress = ref(0)
const selectedVideoName = ref('')
const selectedVideoSize = ref(0)

function triggerCoverInput() {
  if (!coverUploading.value) {
    coverInputRef.value?.click()
  }
}

function triggerVideoInput() {
  if (!videoUploading.value) {
    videoInputRef.value?.click()
  }
}

function handleCoverSelected(e) {
  const file = e.target.files[0]
  if (!file) return

  if (file.size > 5 * 1024 * 1024) {
    alert('封面图片大小不能超过5MB')
    e.target.value = ''
    return
  }

  const reader = new FileReader()
  reader.onload = (ev) => {
    coverPreviewUrl.value = ev.target.result
  }
  reader.readAsDataURL(file)

  coverUploading.value = true
  coverProgress.value = 0

  uploadCover(file, (progressEvent) => {
    coverProgress.value = Math.round((progressEvent.loaded * 100) / progressEvent.total)
  })
    .then((res) => {
      uploadedCover.value = res.data
      form.coverUrl = res.data
    })
    .catch((err) => {
      alert('封面上传失败: ' + err.message)
      coverPreviewUrl.value = ''
    })
    .finally(() => {
      coverUploading.value = false
    })

  e.target.value = ''
}

function handleVideoSelected(e) {
  const file = e.target.files[0]
  if (!file) return

  if (file.size > 500 * 1024 * 1024) {
    alert('视频文件大小不能超过500MB')
    e.target.value = ''
    return
  }

  selectedVideoName.value = file.name
  selectedVideoSize.value = file.size

  videoUploading.value = true
  videoProgress.value = 0

  uploadVideo(file, (progressEvent) => {
    videoProgress.value = Math.round((progressEvent.loaded * 100) / progressEvent.total)
  })
    .then((res) => {
      uploadedVideo.value = res.data
      form.videoUrl = res.data
    })
    .catch((err) => {
      alert('视频上传失败: ' + err.message)
      selectedVideoName.value = ''
      selectedVideoSize.value = 0
    })
    .finally(() => {
      videoUploading.value = false
    })

  e.target.value = ''
}

function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(1) + ' ' + units[i]
}

async function handleSubmit() {
  if (!form.title || !form.category) {
    alert('请填写标题和分区')
    return
  }
  if (!form.videoUrl) {
    alert('请先上传视频文件')
    return
  }
  if (!form.coverUrl) {
    alert('请先上传封面图片')
    return
  }

  submitting.value = true
  try {
    await publishVideo(form)
    alert('视频发布成功！')
    router.push('/')
  } catch (err) {
    alert('发布失败: ' + err.message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.publish-container {
  max-width: 680px;
  margin: 40px auto;
  padding: 0 20px;
}
.publish-card {
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.publish-card h2 {
  margin-bottom: 30px;
  font-size: 22px;
  color: #333;
}
.form-group {
  margin-bottom: 24px;
}
.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #555;
  font-size: 14px;
}
.form-group input[type="text"],
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.form-group input[type="text"]:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #4a90d9;
}
.file-upload-area {
  border: 2px dashed #ddd;
  border-radius: 10px;
  padding: 30px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
}
.file-upload-area:hover {
  border-color: #4a90d9;
  background: #f7faff;
}
.file-upload-area.has-file {
  border-style: solid;
  border-color: #4CAF50;
}
.upload-placeholder p {
  margin: 8px 0 0;
  color: #666;
  font-size: 14px;
}
.file-hint {
  color: #999 !important;
  font-size: 12px !important;
}
.file-input-hidden {
  display: none;
}
.cover-preview {
  max-width: 100%;
  max-height: 200px;
  border-radius: 6px;
  object-fit: cover;
}
.file-hover-msg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  opacity: 0;
  transition: opacity 0.2s;
  border-radius: 10px;
}
.file-upload-area:hover .file-hover-msg {
  opacity: 1;
}
.video-selected {
  display: flex;
  align-items: center;
  gap: 16px;
}
.video-file-info {
  text-align: left;
}
.video-file-name {
  font-weight: 600;
  color: #333;
  word-break: break-all;
}
.video-file-size {
  color: #999;
  font-size: 13px;
  margin-top: 4px;
}
.upload-progress {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.upload-progress span {
  font-size: 13px;
  color: #4a90d9;
  font-weight: 600;
  min-width: 40px;
}
.progress-bar {
  flex: 1;
  height: 6px;
  background: #eee;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: #4a90d9;
  border-radius: 3px;
  transition: width 0.3s;
}
.submit-btn {
  width: 100%;
  padding: 12px;
  background: #4a90d9;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
  margin-top: 8px;
}
.submit-btn:hover:not(:disabled) {
  background: #357abd;
}
.submit-btn:disabled {
  background: #aaa;
  cursor: not-allowed;
}
</style>