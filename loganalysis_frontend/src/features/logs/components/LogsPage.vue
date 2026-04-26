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
          <el-select
            v-model="filter.sourceId"
            :disabled="!filter.projectId"
            :placeholder="filter.projectId ? '请选择日志源' : '请先选择项目'"
            clearable
            @change="handleSourceChange"
            style="width: 180px"
          >
            <el-option v-for="source in filteredSources" :key="source.id" :label="source.name" :value="source.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isNginxSource && availableLogFiles.length > 0" label="日志文件">
          <el-select v-model="filter.logFiles" placeholder="选择日志文件" multiple collapse-tags collapse-tags-tooltip @change="handleLogFileChange" style="width: 200px">
            <el-option v-for="file in availableLogFiles" :key="file.name" :label="file.name" :value="file.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志级别">
          <el-select v-model="esFilter.logLevels" placeholder="选择级别" multiple clearable collapse-tags style="width: 180px" @change="handleLogLevelsChange">
            <el-option label="ERROR" value="ERROR" />
            <el-option label="WARN" value="WARN" />
            <el-option label="INFO" value="INFO" />
            <el-option label="DEBUG" value="DEBUG" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="esFilter.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 340px"
            @change="handleDateRangeChange"
          />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="esFilter.keyword" placeholder="输入关键字搜索" clearable style="width: 200px" @input="handleKeywordInput" />
        </el-form-item>
        <el-form-item label="刷新时间">
          <el-select v-model="filter.refreshInterval" placeholder="选择刷新间隔" style="width: 140px">
            <el-option label="不刷新" :value="0" />
            <el-option label="每隔1秒" :value="1000" />
            <el-option label="每隔5秒" :value="5000" />
            <el-option label="每隔10秒" :value="10000" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

      <!-- 日志终端和解析信息面板 -->
    <div class="logs-content" :class="{ 'panel-hidden': !parsedInfoVisible }">
      <el-card class="terminal-card">
        <div class="terminal-header">
          <div class="terminal-toolbar">
            <span class="terminal-dot"></span>
            <span class="terminal-dot"></span>
            <span class="terminal-dot"></span>
            <span class="terminal-title">log terminal</span>
          </div>
          <span class="log-count">{{ total }} logs</span>
        </div>
        <div class="terminal-content" :class="{ 'log-list-entering': isLogListEntering }" ref="terminalRef" @scroll="handleScroll">
          <div
            v-for="(log, index) in logs"
            :key="log.uid"
            class="log-line"
            :class="[getLogLevelClass(log.parsedFields?.logLevel), {
              'log-query-enter': isLogQueryEntering(log),
              'log-highlight': highlightedIndex === index,
              'log-selected': selectedLogIndex === index
            }]"
            :style="getLogQueryEnterStyle(log)"
            @click="handleLogClick(log, index)"
          >
            <span v-if="isNginxSource && filter.logFiles && filter.logFiles.length > 1" class="log-file-tag">{{ getFileName(log.filePath) }}</span>
            <span class="log-time">{{ formatLogTimeDisplay(log) }}</span>
            <span class="log-level" :class="getLogLevelClass(log.logLevel || log.parsedFields?.logLevel)">
              {{ log.logLevel || log.parsedFields?.logLevel || 'INFO' }}
            </span>
            <span class="log-message" v-html="highlightText(log.rawContent || log.parsedFields?.message, esFilter.keyword)"></span>
          </div>
          <div v-if="logs.length === 0 && !loading" class="terminal-empty">
            暂无日志数据
          </div>
        </div>
      </el-card>

      <!-- 解析信息面板 -->
      <div class="parsed-info-sidebar">
        <div class="parsed-info-header">
          <div class="parsed-info-title-row">
            <span class="parsed-info-title">解析信息</span>
            <span class="parsed-info-count">{{ parsedInfoTableData.length }} 项</span>
          </div>
          <el-button type="primary" link @click="closeParsedInfo">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div class="parsed-info-content">
          <div class="parsed-info-field-list">
            <div
              v-for="row in parsedInfoTableData"
              :key="row.key"
              class="parsed-info-field-row"
              :class="getParsedFieldToneClass(row.key)"
            >
              <div class="parsed-info-field-name">
                <span class="parsed-info-field-label">{{ formatFieldKey(row.key) }}</span>
                <span v-if="formatFieldKey(row.key) !== row.key" class="parsed-info-field-key">{{ row.key }}</span>
              </div>
              <pre class="parsed-info-field-value">{{ formatFieldValue(row.value) }}</pre>
            </div>
          </div>
        </div>
        <div v-if="currentHoverTraceId" class="parsed-info-footer">
          <el-button type="primary" @click="openTraceTimeline">
            <el-icon><Link /></el-icon>
            链路追踪
          </el-button>
        </div>
      </div>
    </div>

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
      <template #footer v-if="currentLog?.parsedFields?.traceId">
        <el-button type="primary" @click="openTraceTimelineFromDetail">
          <el-icon><Link /></el-icon>
          查看链路追踪
        </el-button>
      </template>
    </el-dialog>

    <!-- 链路追踪时间线弹窗 -->
    <TraceTimeline v-model="traceTimelineVisible" :traceId="currentTraceId" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Refresh, Link, Close, Loading, MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { logSourceApi, projectApi, rawLogApi, analysisApi, analysisConfigApi, esLogApi } from '@/api'
import TraceTimeline from '@/components/TraceTimeline.vue'

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
const selectedLogIndex = ref(-1)

// 链路追踪相关状态
const currentHoverTraceId = ref('')
const currentTraceId = ref('')
const traceTimelineVisible = ref(false)

// 高亮相关状态
const highlightTime = ref(null)
const highlightId = ref(null)
const highlightedIndex = ref(-1)
const hasScrolledToHighlight = ref(false)
const cachedContextSize = ref(10)
let esQuerySeq = 0
let logFileQuerySeq = 0
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const normalizeUuid = (value) => {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  if (!trimmed || trimmed === 'undefined' || trimmed === 'null') return null
  return UUID_REGEX.test(trimmed) ? trimmed : null
}

const QUERY_ANIMATION_ROW_CAP = 30
const QUERY_ANIMATION_STAGGER_MS = 14
const QUERY_ANIMATION_DURATION_MS = 220
const QUERY_ANIMATION_CLEANUP_BUFFER_MS = 180
const QUERY_LIST_ENTER_DURATION_MS = 140
const AUTO_SCROLL_DURATION_MS = 260
const AUTO_SCROLL_NEAR_BOTTOM_THRESHOLD = 24
const AUTO_APPEND_LOG_CAP = 4000
const QUERY_TRIGGER_TYPES = new Set(['manual', 'filter', 'auto', 'silent'])

const animatedUidDelayMap = ref({})
const lastAnimatedUidCount = ref(0)
const currentAnimationMode = ref('none')
const isLogListEntering = ref(false)
let queryAnimationCleanupTimer = null
let listEnterCleanupTimer = null
let listEnterRaf = null
let autoScrollRaf = null

const normalizeQueryTrigger = (trigger) => {
  const triggerValue = String(trigger || 'silent')
  return QUERY_TRIGGER_TYPES.has(triggerValue) ? triggerValue : 'silent'
}

const clearQueryAnimationTimer = () => {
  if (queryAnimationCleanupTimer) {
    clearTimeout(queryAnimationCleanupTimer)
    queryAnimationCleanupTimer = null
  }
}

const clearListEnterAnimation = () => {
  if (listEnterCleanupTimer) {
    clearTimeout(listEnterCleanupTimer)
    listEnterCleanupTimer = null
  }
  if (listEnterRaf) {
    cancelAnimationFrame(listEnterRaf)
    listEnterRaf = null
  }
  isLogListEntering.value = false
}

const stopAutoScrollToBottom = () => {
  if (autoScrollRaf) {
    cancelAnimationFrame(autoScrollRaf)
    autoScrollRaf = null
  }
}

const isTerminalNearBottom = () => {
  const terminalEl = terminalRef.value
  if (!terminalEl) return true
  const distanceToBottom = terminalEl.scrollHeight - terminalEl.clientHeight - terminalEl.scrollTop
  return distanceToBottom <= AUTO_SCROLL_NEAR_BOTTOM_THRESHOLD
}

const smoothScrollTerminalToBottom = (duration = AUTO_SCROLL_DURATION_MS) => {
  stopAutoScrollToBottom()
  const terminalEl = terminalRef.value
  if (!terminalEl) return

  const from = terminalEl.scrollTop
  const to = Math.max(terminalEl.scrollHeight - terminalEl.clientHeight, 0)
  if (Math.abs(to - from) < 1) {
    terminalEl.scrollTop = to
    return
  }

  const start = performance.now()
  const step = (now) => {
    const progress = Math.min((now - start) / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    terminalEl.scrollTop = from + (to - from) * eased
    if (progress < 1) {
      autoScrollRaf = requestAnimationFrame(step)
    } else {
      autoScrollRaf = null
    }
  }

  autoScrollRaf = requestAnimationFrame(step)
}

const startListEnterAnimation = () => {
  clearListEnterAnimation()
  listEnterRaf = requestAnimationFrame(() => {
    isLogListEntering.value = true
    listEnterCleanupTimer = setTimeout(() => {
      isLogListEntering.value = false
      listEnterCleanupTimer = null
    }, QUERY_LIST_ENTER_DURATION_MS + 40)
    listEnterRaf = null
  })
}

const clearQueryEnterAnimation = () => {
  clearQueryAnimationTimer()
  animatedUidDelayMap.value = {}
  lastAnimatedUidCount.value = 0
  currentAnimationMode.value = 'none'
  clearListEnterAnimation()
  stopAutoScrollToBottom()
}

const scheduleQueryEnterAnimationCleanup = (animatedCount) => {
  clearQueryAnimationTimer()
  if (animatedCount <= 0) return
  const cleanupDelay = QUERY_ANIMATION_DURATION_MS + ((animatedCount - 1) * QUERY_ANIMATION_STAGGER_MS) + QUERY_ANIMATION_CLEANUP_BUFFER_MS
  queryAnimationCleanupTimer = setTimeout(() => {
    clearQueryEnterAnimation()
  }, cleanupDelay)
}

const hashString = (value) => {
  const input = String(value || '')
  let hash = 5381
  for (let i = 0; i < input.length; i += 1) {
    hash = ((hash << 5) + hash) ^ input.charCodeAt(i)
  }
  return (hash >>> 0).toString(36)
}

const buildLogUid = (log, fallbackIndex = 0) => {
  const stableId = log?.id || log?.dbId
  if (stableId !== null && stableId !== undefined && String(stableId).length > 0) {
    return `id:${stableId}`
  }
  const filePath = log?.filePath || '-'
  const lineNumber = log?.lineNumber ?? '-'
  const time = log?.originalLogTime || log?.collectionTime || log?.parsedFields?.logTime || '-'
  const level = log?.logLevel || log?.parsedFields?.logLevel || '-'
  const rawHash = hashString(log?.rawContent || '')
  return `fallback:${filePath}|${lineNumber}|${time}|${level}|${rawHash}|${fallbackIndex}`
}

const normalizeLogItem = (log, fallbackIndex = 0) => {
  const normalizedLog = {
    id: log?.id || log?.dbId || null,
    dbId: log?.dbId || null,
    rawContent: log?.rawContent || '',
    filePath: log?.filePath,
    lineNumber: log?.lineNumber,
    logLevel: log?.logLevel,
    collectionTime: log?.collectionTime,
    originalLogTime: log?.originalLogTime,
    parsedFields: log?.parsedFields || {}
  }
  normalizedLog.uid = buildLogUid(normalizedLog, fallbackIndex)
  return normalizedLog
}

const collectAnimatedUidsForTrigger = (nextLogs, trigger, previousUidSet) => {
  const normalizedTrigger = normalizeQueryTrigger(trigger)
  if (normalizedTrigger === 'silent' || !Array.isArray(nextLogs) || nextLogs.length === 0) {
    return []
  }

  let candidates = []
  if (normalizedTrigger === 'auto') {
    candidates = nextLogs
      .filter(log => log?.uid && !previousUidSet.has(log.uid))
      .map(log => log.uid)
      .slice(-QUERY_ANIMATION_ROW_CAP)
  } else {
    candidates = nextLogs
      .map(log => log?.uid)
      .filter(Boolean)
      .slice(-QUERY_ANIMATION_ROW_CAP)
  }

  const seen = new Set()
  return candidates.filter(uid => {
    if (seen.has(uid)) return false
    seen.add(uid)
    return true
  })
}

const applyQueryEnterAnimation = (nextLogs, trigger, previousUidSet = new Set()) => {
  const normalizedTrigger = normalizeQueryTrigger(trigger)
  const animatedUids = collectAnimatedUidsForTrigger(nextLogs, normalizedTrigger, previousUidSet)
  if (animatedUids.length === 0) {
    clearQueryEnterAnimation()
    return
  }

  const delayMap = {}
  animatedUids.forEach((uid, index) => {
    // 终端阅读顺序为“最新在下方”，入场顺序改为自下而上
    delayMap[uid] = (animatedUids.length - 1 - index) * QUERY_ANIMATION_STAGGER_MS
  })
  animatedUidDelayMap.value = delayMap
  lastAnimatedUidCount.value = animatedUids.length
  currentAnimationMode.value = normalizedTrigger
  if (normalizedTrigger === 'manual' || normalizedTrigger === 'filter') {
    startListEnterAnimation()
  } else {
    clearListEnterAnimation()
  }
  scheduleQueryEnterAnimationCleanup(animatedUids.length)
}

const isLogQueryEntering = (log) => {
  if (!log?.uid) return false
  return Object.prototype.hasOwnProperty.call(animatedUidDelayMap.value, log.uid)
}

const getLogQueryEnterStyle = (log) => {
  if (!isLogQueryEntering(log)) return null
  return {
    '--log-enter-delay': `${animatedUidDelayMap.value[log.uid]}ms`
  }
}

const buildLogsRouteQuery = () => {
  const query = {}
  const sourceId = normalizeUuid(filter.value.sourceId)
  if (sourceId) query.sourceId = sourceId
  if (filter.value.projectId && filter.value.projectId !== 'undefined' && filter.value.projectId !== 'null') {
    query.projectId = filter.value.projectId
  }
  return query
}

const parsedInfoTableData = computed(() => {
  const data = []
  for (const key in parsedInfoFields.value) {
    const value = parsedInfoFields.value[key]
    if (isDisplayableFieldValue(value)) {
      data.push({
        key: key,
        value: value
      })
    }
  }
  return data.sort((a, b) => {
    const isMessageA = isLogMessageField(a.key)
    const isMessageB = isLogMessageField(b.key)
    if (isMessageA !== isMessageB) return isMessageA ? 1 : -1
    const priorityA = getParsedFieldPriority(a.key)
    const priorityB = getParsedFieldPriority(b.key)
    if (priorityA !== priorityB) return priorityA - priorityB
    return a.key.localeCompare(b.key)
  })
})

const parsedFieldOrder = [
  'timestamp',
  'logLevel',
  'level',
  'logTime',
  'thread',
  'logger',
  'traceId',
  'spanId',
  'parentSpanId',
  'exceptionType',
  'exceptionMessage',
  'stackTrace',
  'threadName',
  'loggerName',
  'className',
  'methodName'
]

const isLogMessageField = (key) => {
  const normalizedKey = String(key).toLowerCase()
  return normalizedKey === 'message' || normalizedKey === 'logmessage' || normalizedKey.includes('content')
}

const getParsedFieldPriority = (key) => {
  const index = parsedFieldOrder.findIndex(item => item.toLowerCase() === String(key).toLowerCase())
  return index === -1 ? Number.MAX_SAFE_INTEGER : index
}

const isDisplayableFieldValue = (value) => {
  if (value === null || value === undefined) return false
  if (typeof value === 'string') return value.trim().length > 0
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.keys(value).length > 0
  return true
}

// ES 搜索相关状态
const esFilter = ref({
  keyword: '',
  logLevels: [],
  dateRange: null,
  page: 0,
  size: 1000
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
    return []
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

// ES 搜索方法
const loadEsLogs = async (trigger = 'silent') => {
  const normalizedTrigger = normalizeQueryTrigger(trigger)
  const validSourceId = normalizeUuid(filter.value.sourceId)
  if (!validSourceId) {
    logs.value = []
    total.value = 0
    clearQueryEnterAnimation()
    return
  }

  const previousUidSet = new Set(logs.value.map(log => log?.uid).filter(Boolean))
  const wasNearBottomBeforeQuery = normalizedTrigger === 'auto' ? isTerminalNearBottom() : false
  const querySeq = ++esQuerySeq
  try {
    loading.value = true

    const currentHighlightTime = highlightTime.value
    const currentHighlightId = highlightId.value
    let params = {
      sourceId: validSourceId,
      page: esFilter.value.page,
      size: esFilter.value.size
    }

    const hasManualDateRange = esFilter.value.dateRange && esFilter.value.dateRange.length === 2

    if (currentHighlightTime && !hasManualDateRange) {
      const targetDate = new Date(currentHighlightTime)
      const startTime = new Date(targetDate.getTime() - 30 * 60 * 1000)
      const endTime = new Date(targetDate.getTime() + 30 * 60 * 1000)
      params.startTime = startTime.toISOString().slice(0, 19)
      params.endTime = endTime.toISOString().slice(0, 19)
    } else if (hasManualDateRange) {
      params.startTime = esFilter.value.dateRange[0]
      params.endTime = esFilter.value.dateRange[1]
    }

    if (esFilter.value.keyword) {
      params.keyword = esFilter.value.keyword
    }
    if (esFilter.value.regex) {
      params.regex = esFilter.value.regex
    }
    if (esFilter.value.logLevels && esFilter.value.logLevels.length > 0) {
      params.logLevels = esFilter.value.logLevels
    }
    if (filter.value.logFiles && filter.value.logFiles.length > 0) {
      params.filePath = filter.value.logFiles.join(',')
    }

    const res = await esLogApi.search(params)
    // 仅保留最后一次查询结果，防止旧请求晚返回覆盖新筛选
    if (querySeq !== esQuerySeq) return

    if (res.data && res.data.hits) {
      let hits = res.data.hits.slice().reverse()

      if (filter.value.logFiles && filter.value.logFiles.length > 0) {
        hits = hits.filter(hit => {
          const filePath = hit.filePath || ''
          const fileName = filePath.split('/').pop() || filePath.split('\\').pop() || filePath
          return filter.value.logFiles.includes(fileName)
        })
      }

      const mappedLogs = hits.map((hit, index) => normalizeLogItem(hit, index))
      const canUseAutoAppend = normalizedTrigger === 'auto' && !currentHighlightId && !currentHighlightTime
      let nextLogs = mappedLogs
      if (canUseAutoAppend) {
        const appendedLogs = mappedLogs.filter(log => !previousUidSet.has(log.uid))
        if (appendedLogs.length > 0) {
          nextLogs = [...logs.value, ...appendedLogs].slice(-AUTO_APPEND_LOG_CAP)
        } else {
          nextLogs = logs.value
        }
      }

      applyQueryEnterAnimation(nextLogs, normalizedTrigger, previousUidSet)
      logs.value = nextLogs
      total.value = hits.length
      currentLoadPage = 0

      // 使用 count API 获取准确总数；失败时不影响本次结果展示
      try {
        const countParams = { ...params }
        delete countParams.page
        delete countParams.size
        const countRes = await esLogApi.count(countParams)
        if (querySeq !== esQuerySeq) return
        total.value = countRes.data?.count ?? hits.length
      } catch (countError) {
        console.warn('获取日志总数失败，使用当前结果条数兜底:', countError)
      }

      let foundIndex = -1

      if (currentHighlightId) {
        foundIndex = logs.value.findIndex(log => log.id === currentHighlightId)
        
        if (foundIndex === -1) {
          try {
            const idRes = await rawLogApi.getById(currentHighlightId)
            if (idRes.data) {
              logs.value.unshift(normalizeLogItem(idRes.data, 'highlight'))
              foundIndex = 0
            }
          } catch (error) {
            console.error('获取高亮日志失败:', error)
          }
        }
      } else if (currentHighlightTime) {
        const targetTime = new Date(currentHighlightTime).getTime()
        foundIndex = logs.value.findIndex(log => {
          const logTime = getLogTimestamp(log)
          return logTime && Math.abs(logTime - targetTime) < 5000
        })
      }

      if (foundIndex !== -1) {
        highlightedIndex.value = foundIndex
        nextTick(() => {
          scrollToHighlightedLog(foundIndex)
          setTimeout(() => {
            clearHighlight()
            if (route.query.highlightId || route.query.highlightTime) {
              router.replace({ path: '/logs', query: buildLogsRouteQuery() })
            }
          }, 3000)
        })
      } else {
        nextTick(() => {
          if (terminalRef.value) {
            if (normalizedTrigger === 'auto') {
              if (wasNearBottomBeforeQuery && lastAnimatedUidCount.value > 0) {
                smoothScrollTerminalToBottom()
              }
            } else {
              terminalRef.value.scrollTop = terminalRef.value.scrollHeight
            }
          }
          clearHighlight()
          if (route.query.highlightId || route.query.highlightTime) {
            router.replace({ path: '/logs', query: buildLogsRouteQuery() })
          }
        })
      }
    } else {
      logs.value = []
      total.value = 0
      clearQueryEnterAnimation()
    }
  } catch (error) {
    console.error('ES搜索失败:', error)
    ElMessage.error('ES搜索失败: ' + (error.message || '请检查ES连接'))
    clearQueryEnterAnimation()
  } finally {
    if (querySeq === esQuerySeq) {
      loading.value = false
    }
  }
}

const loadLogFilesForSource = async () => {
  const validSourceId = normalizeUuid(filter.value.sourceId)
  if (!validSourceId) {
    nginxLogFiles.value = []
    return
  }

  if (!isNginxSource.value) {
    nginxLogFiles.value = []
    return
  }

  const querySeq = ++logFileQuerySeq
  try {
    const countRes = await rawLogApi.getCount(validSourceId)
    if (querySeq !== logFileQuerySeq) return
    const totalCount = countRes.data?.count || 0

    let allLogs = []
    const batchSize = 1000
    const totalPages = Math.ceil(totalCount / batchSize)

    for (let page = 0; page < totalPages; page++) {
      const params = {
        page: page,
        size: batchSize
      }
      const res = await rawLogApi.getBySourceId(validSourceId, params)
      if (querySeq !== logFileQuerySeq) return
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

    if (querySeq !== logFileQuerySeq) return
    nginxLogFiles.value = Array.from(fileMap.entries()).map(([name, path]) => ({
      name,
      path
    }))

    const savedLogFiles = localStorage.getItem(`logFiles_${validSourceId}`)
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
    const routeSourceId = normalizeUuid(route.query.sourceId)
    if (routeSourceId && !logs.value.length) {
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

      // 加载日志由 watch sourceId 触发（见 onMounted 中设置的 sourceId）
    }
  } catch (error) {
    console.error('加载日志源失败:', error)
  }
}

const handleProjectChange = () => {
  filter.value.sourceId = null
  filter.value.page = 1
  esQuerySeq++
  logFileQuerySeq++
}

const loadLogs = async () => {
  const validSourceId = normalizeUuid(filter.value.sourceId)
  if (!validSourceId) {
    logs.value = []
    total.value = 0
    return
  }

  try {
    loading.value = true
    
    const countRes = await rawLogApi.getCount(validSourceId)
    const totalCount = countRes.data?.count || 0
    total.value = totalCount
    
    // 检查是否有高亮时间，如果有则加载包含该时间的日志范围
    const currentHighlightTime = highlightTime.value
    let page = 0
    let params = {}
    
    if (currentHighlightTime) {
      // 计算目标时间范围：向前后各扩展一段时间
      const targetDate = new Date(currentHighlightTime)
      const startTime = new Date(targetDate.getTime() - 30 * 60 * 1000)
      const endTime = new Date(targetDate.getTime() + 30 * 60 * 1000)
      
      params = {
        page: 0,
        size: 100,
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
    
    const res = await rawLogApi.getBySourceId(validSourceId, params)
    let allLogs = res.data?.content || []
    
    if (filter.value.logFiles && filter.value.logFiles.length > 0) {
      allLogs = allLogs.filter(log => {
        const filePath = log.filePath || ''
        const fileName = filePath.split('/').pop() || filePath.split('\\').pop() || filePath
        return filter.value.logFiles.includes(fileName)
      })
      total.value = allLogs.length
    }
    
    // 关键修复：将日志反转，最新的日志放在数组末尾（终端下方）
    // 后端返回 DESC（最新在前），我们需要升序显示（最早在上，最新在下）
    allLogs.reverse()
    
    // 初始化当前加载的页码
    currentLoadPage = 0
    
    logs.value = allLogs.map((log, index) => normalizeLogItem(log, index))

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
            router.replace({ path: '/logs', query: buildLogsRouteQuery() })
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
          router.replace({ path: '/logs', query: buildLogsRouteQuery() })
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

  if (target.scrollTop === 0 && !loading.value && normalizeUuid(filter.value.sourceId) && filter.value.refreshInterval === 0) {
    loadMoreLogsAtTop()
  }
}

const loadMoreLogsAtTop = async () => {
  const validSourceId = normalizeUuid(filter.value.sourceId)
  if (!validSourceId || loading.value) return

  
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

    const res = await rawLogApi.getBySourceId(validSourceId, params)
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
      const normalizedNewLogs = newLogs.map((log, index) => normalizeLogItem(log, `more-${nextPage}-${index}`))
      const combinedLogs = [...normalizedNewLogs, ...oldLogs]
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
    
    const countRes = await rawLogApi.getCount(validSourceId)
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
  const validSourceId = normalizeUuid(filter.value.sourceId)
  if (!validSourceId) {
    esQuerySeq++
    logFileQuerySeq++
    filter.value.sourceId = null
    nginxLogFiles.value = []
    logs.value = []
    total.value = 0
    clearQueryEnterAnimation()
    stopRefresh()
    return
  }
  filter.value.sourceId = validSourceId
  loadLogFilesForSource()
}

const handleLogFileChange = () => {
  const validSourceId = normalizeUuid(filter.value.sourceId)
  if (validSourceId) {
    localStorage.setItem(`logFiles_${validSourceId}`, JSON.stringify(filter.value.logFiles))
    filter.value.page = 1
    esFilter.value.page = 0
    loadEsLogs('filter')
  }
}

const handleSearch = () => {
  filter.value.page = 1
  esFilter.value.page = 0
  loadEsLogs('manual')
}

const handleReset = () => {
  filter.value = {
    projectId: null,
    sourceId: null,
    logLevel: null,
    dateRange: null,
    page: 1,
    pageSize: 20,
    refreshInterval: 0,
    logFiles: []
  }
  // 重置 ES 搜索条件
  esFilter.value = {
    keyword: '',
    logLevels: [],
    dateRange: null,
    page: 0,
    size: 100
  }
  logs.value = []
  clearQueryEnterAnimation()
}

// 时间范围变化时自动搜索
const handleDateRangeChange = () => {
  if (normalizeUuid(filter.value.sourceId)) {
    // 手动时间筛选时，不再使用路由高亮时间覆盖查询区间
    clearHighlight()
    filter.value.page = 1
    esFilter.value.page = 0
    loadEsLogs('filter')
  }
}

// 关键字输入时自动搜索
const handleKeywordInput = () => {
  if (normalizeUuid(filter.value.sourceId)) {
    loadEsLogs('filter')
  }
}

// 日志级别变化时自动搜索
const handleLogLevelsChange = () => {
  if (normalizeUuid(filter.value.sourceId)) {
    loadEsLogs('filter')
  }
}



const handlePageChange = (page) => {
  filter.value.page = page
  esFilter.value.page = page - 1
  loadEsLogs('filter')
}

const handleSizeChange = (size) => {
  filter.value.pageSize = size
  esFilter.value.size = size
  loadEsLogs('filter')
}

const clearHighlight = () => {
  highlightId.value = null
  highlightTime.value = null
  highlightedIndex.value = -1
}

const handleLogClick = (log, index) => {
  // 清除高亮
  clearHighlight()
  selectedLogIndex.value = index
  showParsedInfo(log)
}

const handleLogDoubleClick = (log) => {
  // 双击打开详情弹窗
  clearHighlight()
  currentLog.value = log
  detailVisible.value = true
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

const escapeHtml = (value) => {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// 高亮关键字文本（先转义日志内容，避免 v-html 执行用户输入）
const highlightText = (text, keyword) => {
  if (text === null || text === undefined) return ''

  const safeText = escapeHtml(text)
  const keywordText = keyword === null || keyword === undefined ? '' : String(keyword)

  if (!keywordText) return safeText

  // 在转义后的文本中匹配关键词，保留高亮标签但不执行原始 HTML/JS
  const safeKeyword = escapeHtml(keywordText)
  const escapedKeywordPattern = safeKeyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${escapedKeywordPattern})`, 'gi')
  return safeText.replace(regex, '<span class="highlight-keyword">$1</span>')
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
  if (filter.value.refreshInterval > 0 && normalizeUuid(filter.value.sourceId)) {
    refreshTimer = setInterval(() => {
      loadEsLogs('auto')
    }, filter.value.refreshInterval)
  }
}

const stopRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

// 加载分析配置（缓存contextSize）
const loadAnalysisConfig = async () => {
  try {
    const res = await analysisConfigApi.get()
    if (res.data) {
      cachedContextSize.value = res.data.contextSize || 10
    }
  } catch (error) {
    console.error('加载分析配置失败:', error)
  }
}

const showParsedInfo = (log) => {
  const parsedFields = log.parsedFields || {}

  const info = {}

  for (const key in parsedFields) {
    if (isDisplayableFieldValue(parsedFields[key])) {
      info[key] = parsedFields[key]
    }
  }

  if (Object.keys(info).length === 0 && log.rawContent) {
    info['rawContent'] = log.rawContent
  }

  parsedInfoFields.value = info

  // 设置当前日志，用于AI分析
  currentLog.value = log

  // 提取 traceId 用于链路追踪
  currentHoverTraceId.value = parsedFields.traceId || ''

  parsedInfoVisible.value = true
}

const closeParsedInfo = () => {
  parsedInfoVisible.value = false
  selectedLogIndex.value = -1
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
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}

const getParsedFieldToneClass = (key) => {
  const normalizedKey = String(key).toLowerCase()
  if (normalizedKey.includes('exception') || normalizedKey.includes('stack') || normalizedKey.includes('error')) return 'is-danger'
  if (normalizedKey.includes('trace') || normalizedKey.includes('span') || normalizedKey.includes('request')) return 'is-trace'
  if (normalizedKey.includes('message') || normalizedKey.includes('content')) return 'is-message'
  if (normalizedKey.includes('time') || normalizedKey.includes('duration')) return 'is-time'
  if (normalizedKey.includes('level') || normalizedKey.includes('status')) return 'is-level'
  return ''
}

// 打开链路追踪弹窗
const openTraceTimeline = () => {
  if (currentHoverTraceId.value) {
    currentTraceId.value = currentHoverTraceId.value
    traceTimelineVisible.value = true
  }
}

// 从详情弹窗打开链路追踪
const openTraceTimelineFromDetail = () => {
  if (currentLog.value?.parsedFields?.traceId) {
    currentTraceId.value = currentLog.value.parsedFields.traceId
    traceTimelineVisible.value = true
  }
}

watch(() => filter.value.refreshInterval, () => {
  if (normalizeUuid(filter.value.sourceId)) {
    startRefresh()
  }
})

watch(() => filter.value.sourceId, (newVal, oldVal) => {
  const validNewSourceId = normalizeUuid(newVal)
  if (validNewSourceId && newVal !== oldVal) {
    const routeSourceId = normalizeUuid(route.query.sourceId)
    const shouldSilentLoad = !oldVal && routeSourceId === validNewSourceId
    loadEsLogs(shouldSilentLoad ? 'silent' : 'filter')
    startRefresh()
  } else if (!validNewSourceId) {
    esQuerySeq++
    logFileQuerySeq++
    stopRefresh()
    logs.value = []
    total.value = 0
    clearQueryEnterAnimation()
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
      return 'info'
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
  const routeSourceId = normalizeUuid(route.query.sourceId)
  if (routeSourceId) {
    filter.value.sourceId = routeSourceId

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
  loadAnalysisConfig()
})

onUnmounted(() => {
  stopRefresh()
  clearQueryEnterAnimation()
  clearListEnterAnimation()
})
</script>

<style scoped src="../styles/logs-page.css"></style>
