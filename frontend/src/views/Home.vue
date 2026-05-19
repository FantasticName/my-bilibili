<template>
  <div class="home-page">
    <div class="page-container">
      <div class="hero-section">
        <div class="hero-content">
          <h1 class="hero-title">欢迎来到 <span class="highlight">MyBilibili</span></h1>
          <p class="hero-desc">一个仿哔哩哔哩风格的全栈项目，手写 Spring + SpringMVC 框架</p>
          <div class="hero-actions">
            <router-link v-if="!userStore.isLoggedIn" to="/register" class="btn-primary">立即加入</router-link>
            <router-link v-else to="/profile" class="btn-primary">个人中心</router-link>
            <router-link to="/login" class="btn-secondary" v-if="!userStore.isLoggedIn">登录</router-link>
          </div>
        </div>
        <div class="hero-illustration">
          <div class="bili-tv">
            <div class="tv-screen">
              <div class="tv-content">
                <svg viewBox="0 0 24 24" fill="currentColor" width="64" height="64" class="tv-icon">
                  <path d="M17.813 4.653h.854c1.51.054 2.769.578 3.773 1.574 1.004.995 1.524 2.249 1.56 3.76v7.36c-.036 1.51-.556 2.769-1.56 3.773s-2.262 1.524-3.773 1.56H5.333c-1.51-.036-2.769-.556-3.773-1.56S.036 18.858 0 17.347v-7.36c.036-1.511.556-2.765 1.56-3.76 1.004-.996 2.262-1.52 3.773-1.574h.774l-1.174-1.12a1.234 1.234 0 0 1-.373-.906c0-.356.124-.658.373-.907l.027-.027c.267-.249.573-.373.92-.373.347 0 .653.124.92.373L9.653 4.44c.071.071.134.142.187.213h4.267a.836.836 0 0 1 .16-.213l2.853-2.747c.267-.249.573-.373.92-.373.347 0 .662.151.929.4.267.249.391.551.391.907 0 .355-.124.657-.373.906L17.813 4.653z"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="features-section">
        <h2 class="section-title">项目特色</h2>
        <div class="features-grid">
          <div class="feature-card">
            <div class="feature-icon" style="background: #E3F2FD;">
              <svg viewBox="0 0 24 24" width="28" height="28" fill="#00A1D6">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
              </svg>
            </div>
            <h3 class="feature-title">手写 IoC 容器</h3>
            <p class="feature-desc">自定义注解 + 反射实现依赖注入，理解 Spring 底层原理</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon" style="background: #FCE4EC;">
              <svg viewBox="0 0 24 24" width="28" height="28" fill="#FB7299">
                <path d="M4 4h16v16H4V4zm2 2v12h12V6H6zm3 3h6v2H9V9zm0 4h6v2H9v-2z"/>
              </svg>
            </div>
            <h3 class="feature-title">手写 MVC 框架</h3>
            <p class="feature-desc">DispatcherServlet + 路由匹配 + 参数解析，零依赖实现 Web 层</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon" style="background: #E8F5E9;">
              <svg viewBox="0 0 24 24" width="28" height="28" fill="#4CAF50">
                <path d="M12 3C7 3 3 7 3 12s4 9 9 9 9-4 9-9-4-9-9-9zm0 16c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7zm-1-11h2v4h-2V8zm0 6h2v2h-2v-2z"/>
              </svg>
            </div>
            <h3 class="feature-title">连接池 + 事务</h3>
            <p class="feature-desc">自研数据库连接池与事务管理器，掌握 JDBC 核心技术</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon" style="background: #FFF3E0;">
              <svg viewBox="0 0 24 24" width="28" height="28" fill="#FF9800">
                <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/>
              </svg>
            </div>
            <h3 class="feature-title">JWT 鉴权</h3>
            <p class="feature-desc">Token 认证 + Redis 会话管理，实现安全的用户鉴权体系</p>
          </div>
        </div>
      </div>

      <div class="tech-section">
        <h2 class="section-title">技术栈</h2>
        <div class="tech-list">
          <div class="tech-item">
            <span class="tech-name">后端</span>
            <span class="tech-value">Java Servlet + 手写框架 + MySQL + Redis</span>
          </div>
          <div class="tech-item">
            <span class="tech-name">前端</span>
            <span class="tech-value">Vue 3 + Vite + Pinia + Vue Router</span>
          </div>
          <div class="tech-item">
            <span class="tech-name">部署</span>
            <span class="tech-value">Nginx 反向代理 + 动静分离</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
</script>

<style scoped>
.home-page {
  min-height: calc(100vh - var(--header-height));
}

.hero-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 60px 0 40px;
  gap: 40px;
}

.hero-content {
  flex: 1;
}

.hero-title {
  font-size: 36px;
  font-weight: 800;
  color: var(--bili-gray-1);
  line-height: 1.3;
  margin-bottom: 12px;
}

.highlight {
  color: var(--bili-blue);
}

.hero-desc {
  font-size: 16px;
  color: var(--bili-gray-2);
  line-height: 1.8;
  margin-bottom: 32px;
}

.hero-actions {
  display: flex;
  gap: 16px;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12px 32px;
  background: var(--bili-blue);
  color: white;
  border-radius: var(--bili-radius);
  font-size: 16px;
  font-weight: 600;
  transition: background 0.2s, transform 0.1s;
  text-decoration: none;
}

.btn-primary:hover {
  background: var(--bili-blue-hover);
  color: white;
}

.btn-primary:active {
  transform: scale(0.98);
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12px 32px;
  border: 1px solid var(--bili-blue);
  color: var(--bili-blue);
  border-radius: var(--bili-radius);
  font-size: 16px;
  font-weight: 600;
  transition: all 0.2s;
  text-decoration: none;
}

.btn-secondary:hover {
  background: var(--bili-blue);
  color: white;
}

.hero-illustration {
  flex-shrink: 0;
}

.bili-tv {
  width: 240px;
  height: 200px;
  background: var(--bili-blue);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  box-shadow: 0 12px 40px rgba(0, 161, 214, 0.3);
}

.tv-screen {
  width: 200px;
  height: 160px;
  background: white;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tv-content {
  display: flex;
  align-items: center;
  justify-content: center;
}

.tv-icon {
  color: var(--bili-blue);
}

.features-section {
  padding: 40px 0;
}

.section-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--bili-gray-1);
  margin-bottom: 24px;
  text-align: center;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.feature-card {
  background: var(--bili-white);
  border-radius: var(--bili-radius-lg);
  padding: 28px 20px;
  text-align: center;
  box-shadow: var(--bili-shadow);
  transition: transform 0.2s, box-shadow 0.2s;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
}

.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--bili-gray-1);
  margin-bottom: 8px;
}

.feature-desc {
  font-size: 13px;
  color: var(--bili-gray-3);
  line-height: 1.6;
}

.tech-section {
  padding: 40px 0 60px;
}

.tech-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 600px;
  margin: 0 auto;
}

.tech-item {
  display: flex;
  align-items: center;
  background: var(--bili-white);
  border-radius: var(--bili-radius);
  padding: 16px 24px;
  box-shadow: var(--bili-shadow);
}

.tech-name {
  width: 80px;
  font-weight: 600;
  color: var(--bili-blue);
  font-size: 15px;
  flex-shrink: 0;
}

.tech-value {
  color: var(--bili-gray-2);
  font-size: 14px;
}

@media (max-width: 768px) {
  .hero-section {
    flex-direction: column;
    text-align: center;
  }

  .hero-actions {
    justify-content: center;
  }

  .hero-illustration {
    display: none;
  }

  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
