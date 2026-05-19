<template>
  <transition name="msg-fade">
    <div v-if="visible" class="message" :class="type">
      <svg v-if="type === 'success'" viewBox="0 0 20 20" width="18" height="18">
        <path d="M10 0a10 10 0 100 20 10 10 0 000-20zm4.59 6.59L8 13.17l-2.59-2.58L4 12l4 4 8-8-1.41-1.41z" fill="currentColor"/>
      </svg>
      <svg v-else-if="type === 'error'" viewBox="0 0 20 20" width="18" height="18">
        <path d="M10 0a10 10 0 100 20 10 10 0 000-20zm1 15H9v-2h2v2zm0-4H9V5h2v6z" fill="currentColor"/>
      </svg>
      <svg v-else viewBox="0 0 20 20" width="18" height="18">
        <path d="M10 0a10 10 0 100 20 10 10 0 000-20zm1 15H9v-2h2v2zm0-4H9V5h2v6z" fill="currentColor"/>
      </svg>
      <span>{{ text }}</span>
    </div>
  </transition>
</template>

<script setup>
import { ref, watch } from 'vue'

const visible = ref(false)
const text = ref('')
const type = ref('info')
let timer = null

function show(msg, msgType = 'info', duration = 3000) {
  text.value = msg
  type.value = msgType
  visible.value = true
  clearTimeout(timer)
  timer = setTimeout(() => {
    visible.value = false
  }, duration)
}

defineExpose({ show })
</script>

<style scoped>
.message {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border-radius: var(--bili-radius);
  font-size: 14px;
  z-index: 9999;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  animation: slideDown 0.3s ease;
}

.message.success {
  background: #F0F9EB;
  color: #67C23A;
  border: 1px solid #E1F3D8;
}

.message.error {
  background: #FEF0F0;
  color: #F56C6C;
  border: 1px solid #FDE2E2;
}

.message.info {
  background: #F4F4F5;
  color: #909399;
  border: 1px solid #E9E9EB;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

.msg-fade-enter-active {
  animation: slideDown 0.3s ease;
}

.msg-fade-leave-active {
  transition: opacity 0.2s ease;
}

.msg-fade-leave-to {
  opacity: 0;
}
</style>
