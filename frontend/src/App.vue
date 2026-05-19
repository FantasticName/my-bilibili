<template>
  <div class="app">
    <NavBar />
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <Message ref="messageRef" />
  </div>
</template>

<script setup>
import { ref, provide } from 'vue'
import NavBar from './components/NavBar.vue'
import Message from './components/Message.vue'
import { useUserStore } from './stores/user'

const messageRef = ref(null)

function $message(text, type = 'info', duration = 3000) {
  messageRef.value?.show(text, type, duration)
}

provide('message', $message)

const userStore = useUserStore()
if (userStore.isLoggedIn && !userStore.userInfo) {
  userStore.fetchProfile()
}
</script>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  margin-top: var(--header-height);
}
</style>
