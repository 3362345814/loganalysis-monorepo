<template>
  <el-container class="app-shell">
    <el-aside :width="asideWidth" class="app-aside" :class="{ 'is-compact': isCompact }">
      <div class="logo">
        <div class="logo-mark">
          <el-icon><Monitor /></el-icon>
        </div>
        <div v-show="!isCompact" class="logo-text">
          <strong>LogAnalysis</strong>
          <span>日志智能工作台</span>
        </div>
      </div>

      <el-menu :default-active="activeMenuPath" router :collapse="isCompact" class="nav-menu">
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
          <el-icon>
            <component :is="item.icon" />
          </el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>

      <div v-show="!isCompact" class="aside-footer">
        <span class="aside-version">{{ versionInfo.version }}</span>
        <span>Collect · Search · Analyze · Alert</span>
      </div>
    </el-aside>

    <el-container class="workspace">
      <el-header class="app-header">
        <div class="header-left">
          <span class="route-pill">{{ currentSection }}</span>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle !== '首页'">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <span class="status-pill">
            <i class="status-dot" />
            系统运行正常
          </span>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view v-slot="{ Component, route }">
          <transition
            :name="route.meta.transition || 'route-fade'"
            mode="out-in"
          >
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import versionInfo from './version.json'
import {
  Bell,
  Collection,
  Cpu,
  DataAnalysis,
  Document,
  HomeFilled,
  Monitor,
  Setting
} from '@element-plus/icons-vue'

const route = useRoute()

const navItems = Object.freeze([
  { path: '/', label: '首页', icon: HomeFilled },
  { path: '/collection', label: '日志采集', icon: Collection },
  { path: '/logs', label: '日志查询', icon: Document },
  { path: '/processing', label: '日志聚合', icon: Cpu },
  { path: '/analysis', label: '智能分析', icon: DataAnalysis },
  { path: '/alerts', label: '告警管理', icon: Bell },
  { path: '/config', label: '系统配置', icon: Setting }
])

const isCompact = shallowRef(false)
const mediaQueryRef = shallowRef(null)

const updateCompact = (event) => {
  if (typeof event?.matches === 'boolean') {
    isCompact.value = event.matches
    return
  }
  isCompact.value = globalThis.innerWidth <= 1180
}

onMounted(() => {
  if (typeof window === 'undefined') {
    return
  }

  const mediaQuery = window.matchMedia('(max-width: 1180px)')
  mediaQueryRef.value = mediaQuery
  updateCompact(mediaQuery)

  if (typeof mediaQuery.addEventListener === 'function') {
    mediaQuery.addEventListener('change', updateCompact)
    return
  }

  mediaQuery.addListener(updateCompact)
})

onUnmounted(() => {
  const mediaQuery = mediaQueryRef.value
  if (!mediaQuery) {
    return
  }

  if (typeof mediaQuery.removeEventListener === 'function') {
    mediaQuery.removeEventListener('change', updateCompact)
    return
  }

  mediaQuery.removeListener(updateCompact)
})

const asideWidth = computed(() => (isCompact.value ? '88px' : '248px'))

const activeMenuPath = computed(() => {
  const matchedItem = navItems.find(
    ({ path }) => route.path === path || (path !== '/' && route.path.startsWith(`${path}/`))
  )

  return matchedItem?.path ?? route.path
})

const currentSection = computed(() => {
  const matchedItem = navItems.find(({ path }) => path === activeMenuPath.value)
  return matchedItem?.label ?? '工作台'
})

const currentTitle = computed(() => route.meta?.title ?? '首页')
</script>

<style scoped>
.app-shell {
  min-height: 100svh;
}

.app-aside {
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
  background: linear-gradient(180deg, #17263f 0%, #101a2f 100%);
  box-shadow: inset -1px 0 rgba(255, 255, 255, 0.05);
  transition: width 0.28s ease, padding 0.28s ease;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 8px;
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  color: #e8f1ff;
  background: linear-gradient(140deg, rgba(47, 128, 255, 0.8), rgba(28, 96, 208, 0.8));
}

.logo-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  color: rgba(236, 243, 255, 0.92);
}

.logo-text strong {
  font-size: 16px;
  line-height: 1.1;
  letter-spacing: 0.02em;
}

.logo-text span {
  margin-top: 3px;
  font-size: 12px;
  color: rgba(196, 212, 235, 0.78);
}

.nav-menu {
  flex: 1;
  border-right: none;
  background: transparent;
}

.nav-menu :deep(.el-menu-item) {
  margin: 6px 0;
  height: 46px;
  border-radius: var(--radius-md);
  color: rgba(212, 222, 238, 0.8);
  transition: background-color var(--transition-fast), color var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast);
}

.nav-menu :deep(.el-menu-item:hover) {
  color: #f2f7ff;
  background-color: rgba(255, 255, 255, 0.08);
  transform: translateX(2px);
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: #f5f8ff;
  background: linear-gradient(120deg, rgba(47, 128, 255, 0.34), rgba(47, 128, 255, 0.16));
  box-shadow: 0 4px 12px rgba(47, 128, 255, 0.25);
}

.nav-menu :deep(.el-menu-item .el-icon) {
  font-size: 17px;
}

.aside-footer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 10px 6px;
  font-size: 11px;
  line-height: 1.4;
  color: rgba(176, 193, 219, 0.8);
}

.aside-version {
  font-size: 12px;
  color: rgba(214, 226, 245, 0.95);
}

.workspace {
  min-width: 0;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid rgba(33, 72, 132, 0.13);
  background: rgba(248, 251, 255, 0.86);
  backdrop-filter: blur(10px);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.route-pill {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  border-radius: var(--radius-full);
  font-size: 12px;
  color: #1c5ec0;
  background: rgba(47, 128, 255, 0.13);
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.route-pill:hover {
  transform: scale(1.02);
  box-shadow: 0 2px 8px rgba(47, 128, 255, 0.2);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #23553f;
  background: rgba(45, 178, 113, 0.12);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2db271;
  box-shadow: 0 0 0 6px rgba(45, 178, 113, 0.15);
}

.app-main {
  min-width: 0;
  padding: 20px 24px 24px;
}

.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.route-fade-enter-from,
.route-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 900px) {
  .app-header {
    height: 60px;
    padding: 0 14px;
  }

  .status-pill {
    display: none;
  }

  .header-left {
    gap: 8px;
  }

  .app-main {
    padding: var(--space-md);
  }

  .logo-text {
    display: none;
  }

  .aside-footer {
    display: none;
  }
}
</style>
