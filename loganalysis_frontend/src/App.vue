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
  padding: var(--space-16) var(--space-12);
  background: var(--color-cream);
  border-right: 1px solid var(--border-primary);
  transition: width 0.28s ease, padding 0.28s ease;
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--space-10);
  margin-bottom: var(--space-14);
  padding: var(--space-8);
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-comfortable);
  color: var(--color-white);
  background: var(--color-accent);
}

.logo-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  color: var(--text-primary);
}

.logo-text strong {
  font-size: 16px;
  line-height: 1.1;
  letter-spacing: 0.02em;
  font-weight: 600;
}

.logo-text span {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-secondary);
}

.nav-menu {
  flex: 1;
  border-right: none;
  background: transparent;
}

.nav-menu :deep(.el-menu-item) {
  margin: var(--space-6) 0;
  height: 46px;
  border-radius: var(--radius-comfortable);
  color: var(--text-secondary);
  transition: background-color var(--duration-fast) ease,
              color var(--duration-fast) ease,
              transform var(--duration-fast) ease;
}

.nav-menu :deep(.el-menu-item:hover) {
  color: var(--color-accent);
  background-color: rgba(38, 37, 30, 0.04);
  transform: translateX(2px);
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: #c96442;
  background-color: rgba(201, 100, 66, 0.08);
}

.nav-menu :deep(.el-menu-item .el-icon) {
  font-size: 17px;
}

.aside-footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-12) var(--space-10) var(--space-6);
  font-size: 11px;
  line-height: 1.4;
  color: var(--text-tertiary);
}

.aside-version {
  font-size: 12px;
  color: var(--text-secondary);
}

.workspace {
  min-width: 0;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 var(--space-24);
  border-bottom: 1px solid var(--border-primary);
  background: var(--color-cream);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-14);
  min-width: 0;
}

.route-pill {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 var(--space-12);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
  color: #c96442;
  background: rgba(201, 100, 66, 0.1);
  transition: transform var(--duration-fast) ease,
              box-shadow var(--duration-fast) ease;
}

.route-pill:hover {
  transform: scale(1.02);
  box-shadow: 0 2px 8px rgba(201, 100, 66, 0.15);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-7);
  padding: 5px var(--space-10);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-success);
  background: rgba(31, 138, 101, 0.1);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-success);
  box-shadow: 0 0 0 6px rgba(31, 138, 101, 0.12);
}

.app-main {
  min-width: 0;
  padding: var(--space-24) var(--space-24) var(--space-24);
}

.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity var(--duration-slow) ease,
              transform var(--duration-slow) ease;
}

.route-fade-enter-from,
.route-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 900px) {
  .app-header {
    height: 60px;
    padding: 0 var(--space-14);
  }

  .status-pill {
    display: none;
  }

  .header-left {
    gap: var(--space-8);
  }

  .app-main {
    padding: var(--space-24) var(--space-14) var(--space-32);
  }

  .logo-text {
    display: none;
  }

  .aside-footer {
    display: none;
  }
}
</style>
