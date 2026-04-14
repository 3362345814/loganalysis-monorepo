import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/main.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import ECharts from 'vue-echarts'
import * as echarts from 'echarts'

import App from './App.vue'
import router from './router'
import Skeleton from './components/Skeleton.vue'
import Loading from './components/Loading.vue'
import { authApi } from './api'
import { resolveAuthEnabled } from './features/auth/mode'
import { clearAccessToken, getAccessToken } from './features/auth/token'

const app = createApp(App)
const SESSION_CHECK_INTERVAL_MS = 30000
const ERROR_MESSAGE_DEDUPE_WINDOW_MS = 800
let sessionCheckTimer = null
let sessionCheckInFlight = false
let lastErrorToastMessage = ''
let lastErrorToastAt = 0

const resolveErrorToastMessage = (input) => {
  if (typeof input === 'string') {
    return input.trim()
  }
  if (input && typeof input === 'object' && typeof input.message === 'string') {
    return input.message.trim()
  }
  return ''
}

const patchErrorToastDedup = () => {
  const originalError = ElMessage.error.bind(ElMessage)
  ElMessage.error = (input) => {
    const message = resolveErrorToastMessage(input)
    const now = Date.now()
    if (message && message === lastErrorToastMessage && now - lastErrorToastAt <= ERROR_MESSAGE_DEDUPE_WINDOW_MS) {
      return undefined
    }

    if (message) {
      lastErrorToastMessage = message
      lastErrorToastAt = now
    }

    if (typeof input === 'string') {
      return originalError({ message: input, grouping: true })
    }
    if (input && typeof input === 'object') {
      return originalError({ grouping: true, ...input })
    }
    return originalError(input)
  }
}

patchErrorToastDedup()

app.component('v-chart', ECharts)
app.component('Skeleton', Skeleton)
app.component('Loading', Loading)

globalThis.echarts = echarts

Object.entries(ElementPlusIconsVue).forEach(([key, component]) => {
  app.component(key, component)
})

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

const bootstrap = async () => {
  const authEnabled = await resolveAuthEnabled()
  if (!authEnabled) {
    clearAccessToken()
    if (router.currentRoute.value?.path === '/login') {
      await router.replace('/')
    }
  } else if (getAccessToken()) {
    try {
      await authApi.me()
    } catch (_) {
      clearAccessToken()
      const currentPath = router.currentRoute.value?.path || '/'
      const currentFullPath = router.currentRoute.value?.fullPath || '/'
      if (currentPath !== '/login') {
        await router.replace({
          path: '/login',
          query: { redirect: currentFullPath }
        })
      }
    }
  }

  app.mount('#app')
  startSessionHeartbeat()
}

const startSessionHeartbeat = () => {
  if (typeof window === 'undefined' || sessionCheckTimer) {
    return
  }
  sessionCheckTimer = window.setInterval(async () => {
    if (sessionCheckInFlight) {
      return
    }
    sessionCheckInFlight = true
    try {
      const authEnabled = await resolveAuthEnabled({ force: true })
      const currentPath = router.currentRoute.value?.path || '/'
      if (!authEnabled) {
        clearAccessToken()
        if (currentPath === '/login') {
          await router.replace('/')
        }
        return
      }

      const token = getAccessToken()
      if (!token || currentPath === '/login') {
        return
      }
      await authApi.me()
    } catch (_) {
      // 401 handling (clear token + redirect) is centralized in axios interceptor.
    } finally {
      sessionCheckInFlight = false
    }
  }, SESSION_CHECK_INTERVAL_MS)
}

bootstrap()
