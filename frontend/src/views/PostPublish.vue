<template>
  <div class="post-publish">
    <h2>发布动态</h2>
    <form @submit.prevent="handleSubmit">
      <div class="form-group">
        <textarea v-model="content" placeholder="分享你的想法..." rows="6" maxlength="5000"></textarea>
        <div class="char-count">{{ content.length }}/5000</div>
      </div>

      <!-- 图片上传区域 -->
      <div class="image-upload-section">
        <div class="image-list">
          <div v-for="(img, index) in previewImages" :key="index" class="image-preview-item">
            <img :src="img" class="preview-img" />
            <button type="button" class="remove-btn" @click="removeImage(index)">✕</button>
          </div>
          <label v-if="previewImages.length < 9" class="add-image-btn">
            <input type="file" accept="image/*" multiple @change="handleImageSelect" hidden />
            <span class="add-icon">+</span>
            <span class="add-text">添加图片</span>
          </label>
        </div>
        <div class="image-tip">最多上传9张图片，支持jpg/png/gif/webp</div>
      </div>

      <button type="submit" class="submit-btn" :disabled="submitting || (!content.trim() && selectedFiles.length === 0)">
        {{ submitting ? '发布中...' : '发布动态' }}
      </button>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createPost } from '../api/post'

const router = useRouter()
const content = ref('')
const submitting = ref(false)
const selectedFiles = ref([])
const previewImages = ref([])

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

async function handleSubmit() {
  if (!content.value.trim() && selectedFiles.value.length === 0) return
  submitting.value = true
  try {
    const formData = new FormData()
    if (content.value.trim()) {
      formData.append('content', content.value.trim())
    }
    for (const file of selectedFiles.value) {
      formData.append('images', file)
    }
    await createPost(formData)
    alert('动态发布成功！')
    router.back()
  } catch (e) {
    alert('发布失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.post-publish {
  max-width: 600px;
  margin: 30px auto;
  padding: 0 20px;
}

.post-publish h2 {
  font-size: 22px;
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 16px;
}

textarea {
  width: 100%;
  padding: 14px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 15px;
  resize: vertical;
  outline: none;
  font-family: inherit;
  line-height: 1.6;
  box-sizing: border-box;
}

textarea:focus {
  border-color: #00a1d6;
}

.char-count {
  text-align: right;
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.image-upload-section {
  margin-bottom: 20px;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.image-preview-item {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 8px;
  overflow: hidden;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  border: none;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.remove-btn:hover {
  background: rgba(0, 0, 0, 0.7);
}

.add-image-btn {
  width: 100px;
  height: 100px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s;
}

.add-image-btn:hover {
  border-color: #00a1d6;
}

.add-icon {
  font-size: 28px;
  color: #999;
  line-height: 1;
}

.add-text {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.image-tip {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}

.submit-btn {
  padding: 10px 32px;
  background: #00a1d6;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  cursor: pointer;
}

.submit-btn:hover:not(:disabled) {
  background: #0091c7;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
