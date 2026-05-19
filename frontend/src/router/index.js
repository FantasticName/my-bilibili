import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/VideoList.vue'),
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/video/:id',
    name: 'VideoDetail',
    component: () => import('../views/VideoDetail.vue'),
  },
  {
    path: '/publish',
    name: 'VideoPublish',
    component: () => import('../views/VideoPublish.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/publish/post',
    name: 'PostPublish',
    component: () => import('../views/PostPublish.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/post/:postId',
    name: 'PostDetail',
    component: () => import('../views/PostDetail.vue'),
  },
  {
    path: '/user/:id',
    name: 'UserCenter',
    component: () => import('../views/UserCenter.vue'),
  },
  {
    path: '/favorite/:id',
    name: 'FavoriteDetail',
    component: () => import('../views/FavoriteDetail.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/feed',
    name: 'Feed',
    component: () => import('../views/Feed.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('../views/SearchResult.vue'),
  },
  {
    path: '/coupon',
    name: 'CouponCenter',
    component: () => import('../views/CouponCenter.vue'),
    meta: { requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
