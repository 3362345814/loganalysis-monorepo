<template>
  <div class="logs-page">
    <!-- 筛选栏 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="项目">
          <el-select v-model="filter.projectId" placeholder="请选择项目" clearable @change="handleProjectChange" style="width: 180px">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志源">
          <el-select v-model="filter.sourceId" placeholder="请选择日志源" clearable @change="handleSourceChange" style="width: 180px">
            <el-option v-for="source in filteredSources" :key="source.id" :label="source.name" :value="source.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isNginxSource && availableLogFiles.length > 0" label="日志文件">
          <el-select v-model="filter.logFiles" placeholder="选择日志文件" multiple collapse-tags collapse-tags-tooltip @change="handleLogFileChange" style="width: 200px">
            <el-option v-for="file in availableLogFiles" :key="file.name" :label="file.name" :value="file.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="刷新频率">
          <el-select v-model="filter.refreshInterval" placeholder="刷新频率" style="width: 120px">
            <el-option label="关闭" :value="0" />
            <el-option label="1秒" :value="1000" />
            <el-option label="3秒" :value="3000" />
            <el-option label="5秒" :value="5000" />
            <el-option label="10秒" :value="10000" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 终端风格日志展示 -->
    <el-card class="terminal-card">
      <div class="terminal-header">
        <span class="terminal-title">日志终端</span>
        <span class="log-count">共 {{ total }} 条</span>
      </div>
      <div class="terminal-content" ref="terminalRef" @scroll="handleScroll">
        <div 
          v-for="(log, index) in logs" 
          :key="index" 
          class="log-line"
          :class="[getLogLevelClass(log.parsedFields?.logLevel), { 'log-highlight': highlightedIndex === index }]"
          @mouseenter="showParsedInfo(log, $event)"
          @mouseleave="hideParsedInfo"
          @click="handleView(log)"
        >
          <span v-if="isNginxSource && filter.logFiles && filter.logFiles.length > 1" class="log-file-tag">{{ getFileName(log.filePath) }}</span>
          <span class="log-time">{{ formatLogTimeDisplay(log) }}</span>
          <span class="log-level" :class="getLogLevelClass(log.logLevel || log.parsedFields?.logLevel)">
            {{ log.logLevel || log.parsedFields?.logLevel || 'INFO' }}
          </span>
          <span class="log-message">{{ log.rawContent || log.parsedFields?.message }}</span>
        </div>
        <div v-if="logs.length === 0 && !loading" class="terminal-empty">
          暂无日志数据
        </div>
      </div>

      <el-popover
        v-model:visible="parsedInfoVisible"
        :width="400"
        trigger="manual"
        placement="right"
        :style="{ position: 'fixed', left: popoverX + 'px', top: popoverY + 'px', zIndex: 9999 }"
      >
        <template #reference>
          <div v-if="false"></div>
        </template>
        <div class="parsed-info-panel">
          <div class="parsed-info-header">
            <span class="parsed-info-title">解析信息</span>
            <span class="parsed-info-count">{{ Object.keys(parsedInfoFields).length }} 个字段</span>
          </div>
          <el-table :data="parsedInfoTableData" size="small" max-height="400" stripe>
            <el-table-column prop="key" label="字段名" width="140">
              <template #default="{ row }">
                <span class="field-key">{{ formatFieldKey(row.key) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="value" label="值">
              <template #default="{ row }">
                <span class="field-value">{{ formatFieldValue(row.value) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-popover>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="800px">
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="事件ID" :span="2">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="日志源">{{ currentLog.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="文件路径">{{ currentLog.filePath }}</el-descriptions-item>
        <el-descriptions-item label="行号">{{ currentLog.lineNumber }}</el-descriptions-item>
        <el-descriptions-item :label="currentLog.originalLogTime ? '日志原始时间' : '采集时间'">
          {{ formatTime(currentLog.originalLogTime || currentLog.collectionTime) }}
        </el-descriptions-item>
        
        <!-- 解析字段 -->
        <el-descriptions-item label="日志时间" :span="2">
          {{ formatLogTime(currentLog.parsedFields) }}
        </el-descriptions-item>
        <el-descriptions-item label="日志级别">
          <el-tag :type="getLogLevelType(currentLog.logLevel || currentLog.parsedFields?.logLevel)" size="small">
            {{ currentLog.logLevel || currentLog.parsedFields?.logLevel || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="线程名">{{ currentLog.parsedFields?.threadName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Logger名" :span="2">{{ currentLog.parsedFields?.loggerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类名">{{ currentLog.parsedFields?.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="方法名">{{ currentLog.parsedFields?.methodName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日志消息" :span="2">
          <pre class="log-content">{{ currentLog.parsedFields?.message || currentLog.rawContent }}</pre>
        </el-descriptions-item>
        
        <!-- 异常信息 -->
        <el-descriptions-item v-if="currentLog.parsedFields?.exceptionType" label="异常类型" :span="2">
          <el-tag type="danger" size="small">{{ currentLog.parsedFields.exceptionType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.parsedFields?.exceptionMessage" label="异常消息" :span="2">
          <pre class="log-content error">{{ currentLog.parsedFields.exceptionMessage }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.parsedFields?.stackTrace" label="堆栈跟踪" :span="2">
          <pre class="log-content error">{{ currentLog.parsedFields.stackTrace }}</pre>
        </el-descriptions-item>
        
        <!-- 链路追踪 -->
        <el-descriptions-item v-if="currentLog.parsedFields?.traceId" label="TraceID" :span="2">
          {{ currentLog.parsedFields.traceId }}
        </el-descriptions-item>
        
        <!-- 原始内容 -->
        <el-descriptions-item label="原始内容" :span="2">
          <el-collapse>
            <el-collapse-item title="点击查看原始内容" name="raw">
              <pre class="log-content">{{ currentLog.rawContent }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-descriptions-item>
        
        <!-- 脱敏内容 -->
        <el-descriptions-item v-if="currentLog.masked" label="脱敏内容" :span="2">
          <pre class="log-content">{{ currentLog.desensitizedContent }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { logSourceApi, projectApi, rawLogApi } from '@/api'

const route = useRoute()
const router = useRouter()

const projects = ref([])
const sources = ref([])
const logs = ref([])
const loading = ref(false)
const terminalRef = ref(null)
let refreshTimer = null
let currentLoadPage = 0
const total = ref(0)
const detailVisible = ref(false)
const currentLog = ref(null)
const parsedInfoVisible = ref(false)
const parsedInfoFields = ref({})
const popoverX = ref(0)
const popoverY = ref(0)

// 高亮相关状态
const highlightTime = ref(null)
const highlightId = ref(null)
const highlightedIndex = ref(-1)
const hasScrolledToHighlight = ref(false)

const parsedInfoTableData = computed(() => {
  const data = []
  for (const key in parsedInfoFields.value) {
    data.push({
      key: key,
      value: parsedInfoFields.value[key]
    })
  }
  return data
})

const filter = ref({
  projectId: null,
  sourceId: null,
  logLevel: null,
  dateRange: null,
  page: 1,
  pageSize: 100,
  refreshInterval: 0,
  logFiles: []
})

const nginxLogFiles = ref([])

const filteredSources = computed(() => {
  if (!filter.value.projectId) {
    return sources.value
  }
  return sources.value.filter(s => s.projectId === filter.value.projectId)
})

const currentSource = computed(() => {
  return sources.value.find(s => s.id === filter.value.sourceId)
})

const isNginxSource = computed(() => {
  if (!currentSource.value) return false
  const format = currentSource.value.logFormat
  return format === 'NGINX' || format === 'NGINX_ACCESS' || format === 'NGINX_ERROR'
})

const availableLogFiles = computed(() => {
  if (!isNginxSource.value) return []
  return nginxLogFiles.value
})

const loadLogFilesForSource = async () => {
  if (!filter.value.sourceId) {
    nginxLogFiles.value = []
    return
  }
  
  if (!isNginxSource.value) {
    nginxLogFiles.value = []
    return
  }
  
  try {
    const countRes = await rawLogApi.getCount(filter.value.sourceId)
    const totalCount = countRes.data?.count || 0
    
    let allLogs = []
    const batchSize = 1000
    const totalPages = Math.ceil(totalCount / batchSize)
    
    for (let page = 0; page < totalPages; page++) {
      const params = {
        page: page,
        size: batchSize
      }
      const res = await rawLogApi.getBySourceId(filter.value.sourceId, params)
      const logs = res.data?.content || []
      allLogs = allLogs.concat(logs)
    }
    
    const fileMap = new Map()
    allLogs.forEach(log => {
      const filePath = log.filePath || ''
      const fileName = filePath.split('/').pop() || filePath.split('\\').pop() || filePath
      if (fileName && !fileMap.has(fileName)) {
        fileMap.set(fileName, filePath)
      }
    })
    
    nginxLogFiles.value = Array.from(fileMap.entries()).map(([name, path]) => ({
      name,
      path
    }))
    
    const savedLogFiles = localStorage.getItem(`logFiles_${filter.value.sourceId}`)
    if (savedLogFiles) {
      try {
        const parsed = JSON.parse(savedLogFiles)
        const validFiles = parsed.filter(f => nginxLogFiles.value.some(lf => lf.name === f))
        filter.value.logFiles = validFiles
      } catch (e) {
        filter.value.logFiles = []
      }
    }
  } catch (error) {
    console.error('获取日志文件列表失败:', error)
    nginxLogFiles.value = []
  }
}

const loadProjects = async () => {
  try {
    const res = await projectApi.getEnabled()
    projects.value = res.data || []
  } catch (error) {
    console.error('加载项目失败:', error)
  }
}

const loadSources = async () => {
  try {
    const res = await logSourceApi.getAll()
    sources.value = res.data || []

    // 如果有路由参数传入的 sourceId，在日志源加载完成后自动查询日志
    if (route.query.sourceId && !logs.value.length) {
      // 如果有项目ID，设置项目筛选
      if (route.query.projectId) {
        filter.value.projectId = route.query.projectId
      }

      // 优先使用ID精确定位
      if (route.query.highlightId) {
        highlightId.value = route.query.highlightId
      } else if (route.query.highlightTime) {
        // 使用时间定位作为后备
        highlightTime.value = route.query.highlightTime
      }

      // 加载日志
      loadLogs()
    }
  } catch (error) {
    console.error('加载日志源失败:', error)
  }
}

const handleProjectChange = () => {
  filter.value.sourceId = null
  filter.value.page = 1
}

const loadLogs = async () => {
  if (!filter.value.sourceId) {
    logs.value = []
    total.value = 0
    return
  }

  try {
    loading.value = true
    
    const countRes = await rawLogApi.getCount(filter.value.sourceId)
    const totalCount = countRes.data?.count || 0
    total.value = totalCount
    
    // 检查是否有高亮时间，如果有则加载包含该时间的日志范围
    const currentHighlightTime = highlightTime.value
    let page = 0
    let params = {}
    
    if (currentHighlightTime) {
      // 计算目标时间范围：向前后各扩展一段时间
      const targetDate = new Date(currentHighlightTime)
      const startTime = new Date(targetDate.getTime() - 30 * 60 * 1000) // 往前30分钟
      const endTime = new Date(targetDate.getTime() + 30 * 60 * 1000)   // 往后30分钟
      
        params = {
        page: 0,
        size: 1000,
        startTime: startTime.toISOString().slice(0, 19),
        endTime: endTime.toISOString().slice(0, 19)
      }
    } else {
      // 默认只先加载第 0 页（最新 1000 条日志）
      params = {
        page: 0,
        size: 1000
      }
    }
    
    const res = await rawLogApi.getBySourceId(filter.value.sourceId, params)
    let allLogs = res.data?.content || []
    
    if (filter.value.logFiles && filter.value.logFiles.length > 0) {
      allLogs = allLogs.filter(log => {
        const filePath = log.filePath || ''
        const fileName = filePath.split('/').pop() || filePath.split('\\').pop() || filePath
        return filter.value.logFiles.includes(fileName)
      })
      total.value = allLogs.length
    }
    
    // 查找并高亮匹配的日志
    // 优先使用 ID 精确定位（从后端获取），其次使用时间定位
    const currentHighlightId = highlightId.value
    let highlightLogData = null

    if (currentHighlightId) {
      // 先尝试从已加载日志中查找
      const foundIdx = allLogs.findIndex(log => log.id === currentHighlightId)
      
      // 如果没找到，从后端获取该日志
      if (foundIdx === -1) {
        try {
          const idRes = await rawLogApi.getById(currentHighlightId)
          if (idRes.data) {
            // 保存获取到的日志数据
            highlightLogData = idRes.data
          }
        } catch (error) {
          console.error('获取高亮日志失败:', error)
        }
      }
    }
    
    // 关键修复：将日志反转，最新的日志放在数组末尾（终端下方）
    // 后端返回 DESC（最新在前），我们需要升序显示（最早在上，最新在下）
    allLogs.reverse()
    
    // 如果有从后端获取的高亮日志，插入到列表开头
    if (highlightLogData) {
      allLogs.unshift(highlightLogData)
    }
    
    // 初始化当前加载的页码
    currentLoadPage = 0
    
    logs.value = allLogs

    // 在反转后的日志列表中查找并高亮
    let foundIndex = -1

    if (currentHighlightId) {
      foundIndex = logs.value.findIndex(log => log.id === currentHighlightId)
    } else if (currentHighlightTime) {
      // 使用时间定位（在已加载的日志中查找）
      const targetTime = new Date(currentHighlightTime).getTime()
      foundIndex = logs.value.findIndex(log => {
        const logTime = getLogTimestamp(log)
        return logTime && Math.abs(logTime - targetTime) < 5000 // 5秒内视为匹配
      })
    }

    if (foundIndex !== -1) {
      highlightedIndex.value = foundIndex
      nextTick(() => {
        scrollToHighlightedLog(foundIndex)
        // 延迟清除定位状态和URL参数，让用户能看到高亮效果
        setTimeout(() => {
          clearHighlight()
          // 清除URL参数，避免刷新时重复定位
          if (route.query.highlightId || route.query.highlightTime) {
            router.replace({ path: '/logs', query: { sourceId: filter.value.sourceId, projectId: filter.value.projectId } })
          }
        }, 3000)
      })
    } else {
      // 没有找到高亮日志，滚动到最新
      nextTick(() => {
        if (terminalRef.value) {
          terminalRef.value.scrollTop = terminalRef.value.scrollHeight
        }
        // 清除定位状态和URL参数
        clearHighlight()
        if (route.query.highlightId || route.query.highlightTime) {
          router.replace({ path: '/logs', query: { sourceId: filter.value.sourceId, projectId: filter.value.projectId } })
        }
      })
    }
  } catch (error) {
    console.error('加载日志失败:', error)
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

let lastScrollHeight = 0

const handleScroll = (e) => {
  const target = e.target
  lastScrollHeight = target.scrollHeight - target.clientHeight
  
  if (target.scrollTop === 0 && !loading.value && filter.value.sourceId && filter.value.refreshInterval === 0) {
    loadMoreLogsAtTop()
  }
}

const loadMoreLogsAtTop = async () => {
  if (!filter.value.sourceId || loading.value) return

  
  try {
    loading.value = true
    const oldLogs = [...logs.value]
    // oldestTime 现在是数组第一个元素的时间（最早日志）
    const oldestTime = oldLogs.length > 0 ? getLogTimestamp(oldLogs[0]) : null
    
    // 正确计算需要请求的页面
    // 后端返回 DESC（最新在前），我们需要获取比当前最早日志更早的日志
    // 由于数据已反转，我们需要请求更后面的页面
    const currentPage = currentLoadPage
    const nextPage = currentPage + 1
    
    
    // 使用与初始加载一致的 pageSize (1000)
    const params = {
      page: nextPage,
      size: 1000
    }
    
    const res = await rawLogApi.getBySourceId(filter.value.sourceId, params)
    let newLogs = res.data?.content || []
    
    if (filter.value.logFiles && filter.value.logFiles.length > 0) {
      newLogs = newLogs.filter(log => {
        const filePath = log.filePath || ''
        const fileName = filePath.split('/').pop() || filePath.split('\\').pop() || filePath
        return filter.value.logFiles.includes(fileName)
      })
    }
    
    // 过滤条件应该是 logTime < oldestTime，获取更早的日志
    // 因为后端返回的是 DESC 排序，最新在前，所以后面的页面是更旧的日志
    // 注意：移除时间过滤逻辑，因为后端已经返回了正确页面的数据
    // 前端再做时间过滤会导致数据丢失
    
    // 反转新日志（与 loadLogs 保持一致）
    newLogs.reverse()
    
    if (newLogs.length > 0) {
      // 将新日志添加到数组开头（更早的日志在上方）
      const combinedLogs = [...newLogs, ...oldLogs]
      logs.value = combinedLogs.slice(0, 4000)
      currentLoadPage = nextPage
      
      
      nextTick(() => {
        if (terminalRef.value) {
          // 保持滚动位置不变
          const newScrollHeight = terminalRef.value.scrollHeight
          terminalRef.value.scrollTop = newScrollHeight - lastScrollHeight
        }
      })
    }
    
    const countRes = await rawLogApi.getCount(filter.value.sourceId)
    const totalCount = countRes.data?.count || 0
    total.value = totalCount
    
  } catch (error) {
    console.error('加载更多日志失败:', error)
  } finally {
    loading.value = false
  }
}

const getLogTimestamp = (log) => {
  // 优先使用 originalLogTime
  if (log.originalLogTime) {
    return new Date(log.originalLogTime).getTime()
  }
  if (log.collectionTime) {
    return new Date(log.collectionTime).getTime()
  }
  if (log.parsedFields?.logTime) {
    return new Date(log.parsedFields.logTime).getTime()
  }
  if (log.parsedFields?.timestamp) {
    return new Date(log.parsedFields.timestamp).getTime()
  }
  return null
}

const handleSourceChange = () => {
  filter.value.page = 1
  filter.value.logFiles = []
  if (filter.value.sourceId) {
    loadLogFilesForSource().then(() => {
      loadLogs()
    })
  } else {
    nginxLogFiles.value = []
    logs.value = []
  }
}

const handleLogFileChange = () => {
  if (filter.value.sourceId) {
    localStorage.setItem(`logFiles_${filter.value.sourceId}`, JSON.stringify(filter.value.logFiles))
    filter.value.page = 1
    loadLogs()
  }
}

const handleSearch = () => {
  filter.value.page = 1
  loadLogs()
}

const handleReset = () => {
  filter.value = {
    projectId: null,
    sourceId: null,
    logLevel: null,
    dateRange: null,
    page: 1,
    pageSize: 20
  }
  logs.value = []
}

const handlePageChange = (page) => {
  filter.value.page = page
  loadLogs()
}

const handleSizeChange = (size) => {
  filter.value.pageSize = size
  loadLogs()
}

const handleView = (row) => {
  // 点击日志时清除高亮状态
  clearHighlight()
  currentLog.value = row
  detailVisible.value = true
}

// 清除高亮状态
const clearHighlight = () => {
  highlightId.value = null
  highlightTime.value = null
  highlightedIndex.value = -1
}

// 滚动到高亮的日志
const scrollToHighlightedLog = (index) => {
  if (!terminalRef.value || index < 0) return

  // 获取所有日志行
  const logLines = terminalRef.value.querySelectorAll('.log-line')
  if (logLines[index]) {
    logLines[index].scrollIntoView({ behavior: 'smooth', block: 'center' })
    hasScrolledToHighlight.value = true
  }
}

const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

// 格式化解析后的日志时间
const formatLogTime = (parsedFields) => {
  if (!parsedFields) return '-'
  const logTime = parsedFields.logTime
  if (!logTime) return '-'
  try {
    return dayjs(logTime).format('YYYY-MM-DD HH:mm:ss')
  } catch {
    return logTime
  }
}

const getFileName = (filePath) => {
  if (!filePath) return ''
  return filePath.split('/').pop() || filePath.split('\\').pop() || filePath
}

const formatLogTimeDisplay = (log) => {
  // 优先使用 originalLogTime（从日志内容提取的原始时间）
  if (log.originalLogTime) {
    try {
      return dayjs(log.originalLogTime).format('HH:mm:ss')
    } catch {
      return ''
    }
  }
  // 其次使用 parsedFields.logTime
  if (log.parsedFields?.logTime) {
    try {
      return dayjs(log.parsedFields.logTime).format('HH:mm:ss')
    } catch {
      return ''
    }
  }
  return ''
}

const getLogLevelClass = (level) => {
  if (!level) return 'info'
  const l = level.toUpperCase()
  if (l === 'ERROR' || l === 'ERR') return 'error'
  if (l === 'WARN' || l === 'WARNING') return 'warn'
  if (l === 'DEBUG') return 'debug'
  if (l === 'TRACE') return 'trace'
  return 'info'
}

const startRefresh = () => {
  stopRefresh()
  if (filter.value.refreshInterval > 0 && filter.value.sourceId) {
    refreshTimer = setInterval(() => {
      loadLogs()
    }, filter.value.refreshInterval)
  }
}

const stopRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const showParsedInfo = (log, event) => {
  const parsedFields = log.parsedFields || {}
  
  const info = {}
  
  for (const key in parsedFields) {
    if (parsedFields[key] !== null && parsedFields[key] !== undefined) {
      info[key] = parsedFields[key]
    }
  }
  
  if (Object.keys(info).length === 0 && log.rawContent) {
    info['rawContent'] = log.rawContent
  }
  
  parsedInfoFields.value = info
  
  const rect = event.target.getBoundingClientRect()
  const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
  const scrollLeft = document.documentElement.scrollLeft || document.body.scrollLeft
  
  popoverX.value = rect.right + scrollLeft + 10
  popoverY.value = rect.top + scrollTop
  
  parsedInfoVisible.value = true
}

const hideParsedInfo = () => {
  parsedInfoVisible.value = false
}

const formatFieldKey = (key) => {
  const keyMap = {
    'timestamp': '时间戳',
    'logTime': '日志时间',
    'level': '日志级别',
    'logLevel': '日志级别',
    'message': '消息',
    'thread': '线程',
    'threadName': '线程名',
    'logger': 'Logger',
    'loggerName': 'Logger名',
    'className': '类名',
    'methodName': '方法名',
    'fileName': '文件名',
    'lineNumber': '行号',
    'exceptionType': '异常类型',
    'exceptionMessage': '异常消息',
    'stackTrace': '堆栈跟踪',
    'traceId': 'TraceID',
    'request_method': '请求方法',
    'request_uri': '请求URI',
    'status': '状态码',
    'client_ip': '客户端IP',
    'bytes': '字节数',
    'pid': '进程ID',
    'tid': '线程ID',
    'connection_id': '连接ID',
    'rawLength': '原始长度',
    'hasException': '包含异常',
    'time_local': '本地时间',
    'raw_content': '原始内容'
  }
  return keyMap[key] || key
}

const formatFieldValue = (value) => {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

watch(() => filter.value.refreshInterval, () => {
  if (filter.value.sourceId) {
    startRefresh()
  }
})

watch(() => filter.value.sourceId, (newVal, oldVal) => {
  if (newVal && newVal !== oldVal) {
    // 加载日志
    loadLogs()
    // 启动定时刷新
    startRefresh()
  } else if (!newVal) {
    stopRefresh()
    logs.value = []
    total.value = 0
  }
})

// 根据日志级别返回 Element Plus 标签类型
const getLogLevelType = (level) => {
  if (!level) return 'info'
  switch (level.toUpperCase()) {
    case 'ERROR':
    case 'FATAL':
      return 'danger'
    case 'WARN':
      return 'warning'
    case 'INFO':
      return 'success'
    case 'DEBUG':
      return 'info'
    case 'TRACE':
      return 'info'
    default:
      return 'info'
  }
}

onMounted(() => {
  // 先设置项目ID（用于筛选日志源）
  if (route.query.projectId) {
    filter.value.projectId = route.query.projectId
  }

  // 设置日志源ID
  if (route.query.sourceId) {
    filter.value.sourceId = route.query.sourceId

    // 优先使用ID精确定位
    if (route.query.highlightId) {
      highlightId.value = route.query.highlightId
    } else if (route.query.highlightTime) {
      // 使用时间定位作为后备
      highlightTime.value = route.query.highlightTime
    }
  }

  // 加载项目、日志源和日志
  loadProjects()
  loadSources()
})

onUnmounted(() => {
  stopRefresh()
})
</script>

<style scoped>
.logs-page {
  padding: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.terminal-card {
  margin-top: 16px;
}

.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #1a1a1a;
  border-radius: 4px 4px 0 0;
  border-bottom: 1px solid #333;
}

.terminal-title {
  color: #00ff00;
  font-family: 'Courier New', monospace;
  font-size: 14px;
}

.log-count {
  color: #888;
  font-size: 12px;
}

.terminal-content {
  background: #0d0d0d;
  color: #ccc;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 12px;
  max-height: 600px;
  overflow-y: auto;
  border-radius: 0 0 4px 4px;
}

.terminal-content::-webkit-scrollbar {
  width: 8px;
}

.terminal-content::-webkit-scrollbar-track {
  background: #1a1a1a;
}

.terminal-content::-webkit-scrollbar-thumb {
  background: #444;
  border-radius: 4px;
}

.log-line {
  padding: 2px 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-file-tag {
  display: inline-block;
  padding: 1px 6px;
  background: #409eff;
  color: #fff;
  border-radius: 3px;
  font-size: 11px;
  margin-right: 8px;
  font-family: 'Courier New', monospace;
}

.log-time {
  color: #888;
  margin-right: 8px;
}

.log-level {
  display: inline-block;
  width: 50px;
  text-align: center;
  margin-right: 8px;
  font-weight: bold;
}

.log-level.error {
  color: #ff4d4f;
}

.log-level.warn {
  color: #faad14;
}

.log-level.info {
  color: #1890ff;
}

.log-level.debug {
  color: #52c41a;
}

.log-level.trace {
  color: #722ed1;
}

.log-message {
  color: #e6e6e6;
}

.log-line:hover {
  background: #1a1a1a;
  cursor: pointer;
}

.log-highlight {
  background: rgba(64, 158, 255, 0.3) !important;
  border-left: 3px solid #409eff;
  animation: highlight-pulse 2s ease-out;
}

@keyframes highlight-pulse {
  0% {
    background: rgba(64, 158, 255, 0.6);
  }
  100% {
    background: rgba(64, 158, 255, 0.3);
  }
}

.terminal-empty {
  text-align: center;
  color: #666;
  padding: 40px;
}

.parsed-info-panel {
  padding: 0;
}

.parsed-info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #eee;
  background: #f5f7fa;
}

.parsed-info-title {
  font-weight: bold;
  font-size: 14px;
  color: #333;
}

.parsed-info-count {
  font-size: 12px;
  color: #909399;
}

.field-key {
  font-weight: 500;
  color: #606266;
}

.field-value {
  color: #303133;
  word-break: break-all;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.log-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 300px;
  overflow: auto;
}

.log-content.error {
  background: #fef0f0;
  border: 1px solid #fde2e2;
  color: #f56c6c;
}
</style>
