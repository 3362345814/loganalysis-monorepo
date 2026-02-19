import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAppStore = defineStore('app', () => {
  // 侧边栏折叠状态
  const sidebarOpened = ref(true)
  
  // 主题
  const theme = ref('light')
  
  // 设备类型
  const device = ref('desktop')
  
  // 切换侧边栏
  const toggleSidebar = () => {
    sidebarOpened.value = !sidebarOpened.value
  }
  
  // 设置主题
  const setTheme = (newTheme) => {
    theme.value = newTheme
  }
  
  return {
    sidebarOpened,
    theme,
    device,
    toggleSidebar,
    setTheme
  }
})

export const useLogSourceStore = defineStore('logSource', () => {
  // 日志源列表
  const sources = ref([])
  
  // 当前选中的日志源
  const currentSource = ref(null)
  
  // 加载状态
  const loading = ref(false)
  
  // 设置日志源列表
  const setSources = (list) => {
    sources.value = list
  }
  
  // 设置当前日志源
  const setCurrentSource = (source) => {
    currentSource.value = source
  }
  
  // 添加日志源
  const addSource = (source) => {
    sources.value.push(source)
  }
  
  // 更新日志源
  const updateSource = (id, data) => {
    const index = sources.value.findIndex(s => s.id === id)
    if (index !== -1) {
      sources.value[index] = { ...sources.value[index], ...data }
    }
  }
  
  // 删除日志源
  const removeSource = (id) => {
    sources.value = sources.value.filter(s => s.id !== id)
    if (currentSource.value?.id === id) {
      currentSource.value = null
    }
  }
  
  return {
    sources,
    currentSource,
    loading,
    setSources,
    setCurrentSource,
    addSource,
    updateSource,
    removeSource
  }
})
