<template>
  <el-dialog
    v-model="dialogVisible"
    title="链路追踪"
    width="900px"
    :close-on-click-modal="false"
    class="trace-timeline-dialog"
  >
    <div class="trace-header" v-if="traceId">
      <div class="trace-info">
        <span class="trace-label">TraceID:</span>
        <span class="trace-value">{{ traceId }}</span>
      </div>
      <div class="trace-stats">
        <span class="trace-count-chip">{{ logs.length }} logs</span>
      </div>
    </div>

    <div class="timeline-container" v-loading="loading">
      <div v-if="!loading && logs.length === 0" class="timeline-empty">
        <el-empty description="暂无关联日志" />
      </div>

      <div v-else class="timeline">
        <div
          v-for="(log, index) in logs"
          :key="log.id || index"
          class="timeline-item"
          :class="{ 'is-expanded': expandedIndex === index }"
        >
          <div class="timeline-marker">
            <div class="marker-dot" :class="getLogLevelClass(log.logLevel || log.parsedFields?.logLevel)"></div>
            <div class="marker-line" v-if="index < logs.length - 1"></div>
          </div>

          <div class="timeline-content" @click="toggleExpand(index)">
            <div class="timeline-header">
              <span class="timeline-time">{{ formatLogTime(log) }}</span>
              <span class="timeline-level" :class="getLogLevelClass(log.logLevel || log.parsedFields?.logLevel)">
                {{ log.logLevel || log.parsedFields?.logLevel || 'INFO' }}
              </span>
              <span class="timeline-source" v-if="log.sourceName">{{ log.sourceName }}</span>
              <el-icon class="expand-icon" :class="{ 'is-expanded': expandedIndex === index }">
                <ArrowDown />
              </el-icon>
            </div>

            <div class="timeline-message">
              {{ truncateMessage(log.parsedFields?.message || log.rawContent, 100) }}
            </div>

            <Transition
              @before-enter="beforeDetailsEnter"
              @enter="enterDetails"
              @after-enter="afterDetailsEnter"
              @before-leave="beforeDetailsLeave"
              @leave="leaveDetails"
              @after-leave="afterDetailsLeave"
            >
              <div class="timeline-details-motion" v-if="expandedIndex === index">
                <div class="timeline-details">
                  <div class="timeline-keyline">
                    <div class="keyline-card">
                      <div class="keyline-label">日志源</div>
                      <div class="keyline-value">{{ log.sourceName || '-' }}</div>
                    </div>
                    <div class="keyline-card">
                      <div class="keyline-label">行号</div>
                      <div class="keyline-value">{{ log.lineNumber ?? '-' }}</div>
                    </div>
                    <div class="keyline-card is-wide">
                      <div class="keyline-label">文件路径</div>
                      <div class="keyline-value">{{ log.filePath || '-' }}</div>
                    </div>
                  </div>

                  <div class="detail-section parsed-fields-section" v-if="log.parsedFields && Object.keys(log.parsedFields).length > 0">
                    <div class="detail-section-header">
                      <div>
                        <div class="section-eyebrow">PARSED FIELDS</div>
                        <div class="section-title">解析信息</div>
                      </div>
                      <span class="section-count">{{ getParsedFieldsTableData(log.parsedFields).length }} 项</span>
                    </div>

                    <div class="parsed-field-list">
                      <div
                        v-for="row in getParsedFieldsTableData(log.parsedFields)"
                        :key="row.key"
                        class="parsed-field-row"
                        :class="getParsedFieldToneClass(row.key)"
                      >
                        <div class="parsed-field-name">
                          <span class="parsed-field-label">{{ formatParsedFieldLabel(row.key) }}</span>
                          <span v-if="formatParsedFieldLabel(row.key) !== row.key" class="parsed-field-key">{{ row.key }}</span>
                        </div>
                        <pre class="parsed-field-value">{{ formatFieldValue(row.value) }}</pre>
                      </div>
                    </div>
                  </div>

                  <div class="detail-section raw-content-section" v-if="log.rawContent">
                    <div class="detail-section-header">
                      <div>
                        <div class="section-eyebrow">RAW LOG</div>
                        <div class="section-title">原始内容</div>
                      </div>
                      <span class="section-count">{{ log.rawContent.length }} 字符</span>
                    </div>

                    <div class="raw-log-frame">
                      <div class="raw-log-toolbar">
                        <span class="raw-log-dot"></span>
                        <span class="raw-log-dot"></span>
                        <span class="raw-log-dot"></span>
                        <span class="raw-log-label">source payload</span>
                      </div>
                      <pre class="detail-content raw"><template
                        v-for="(segment, segmentIndex) in getRawContentSegments(log)"
                        :key="segmentIndex"
                      ><span
                        v-if="segment.field"
                        class="raw-highlight"
                        :class="segment.toneClass"
                      >{{ segment.text }}</span><span v-else>{{ segment.text }}</span></template></pre>
                    </div>
                  </div>
                </div>
              </div>
            </Transition>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { rawLogApi } from '@/api'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  traceId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const logs = ref([])
const loading = ref(false)
const expandedIndex = ref(-1)

const loadLogs = async () => {
  if (!props.traceId) {
    logs.value = []
    return
  }

  loading.value = true
  try {
    const res = await rawLogApi.getAllByTraceId(props.traceId)
    logs.value = res.data || []
    logs.value.sort((a, b) => {
      const timeA = getLogTimestamp(a)
      const timeB = getLogTimestamp(b)
      if (timeA === null && timeB === null) return 0
      if (timeA === null) return -1
      if (timeB === null) return 1
      return timeA - timeB
    })
  } catch (error) {
    console.error('加载链路日志失败:', error)
    logs.value = []
  } finally {
    loading.value = false
  }
}

const getLogTimestamp = (log) => {
  if (log.originalLogTime) return new Date(log.originalLogTime).getTime()
  if (log.parsedFields?.logTime) return new Date(log.parsedFields.logTime).getTime()
  if (log.collectionTime) return new Date(log.collectionTime).getTime()
  return null
}

const formatLogTime = (log) => {
  const time = log.originalLogTime || log.parsedFields?.logTime || log.collectionTime
  if (!time) return '-'
  try {
    return dayjs(time).format('HH:mm:ss.SSS')
  } catch {
    return time
  }
}

const formatFullTime = (time) => {
  if (!time) return '-'
  try {
    return dayjs(time).format('YYYY-MM-DD HH:mm:ss.SSS')
  } catch {
    return time
  }
}

const getLogLevelClass = (level) => {
  if (!level) return 'info'
  const l = level.toUpperCase()
  if (l === 'ERROR' || l === 'ERR' || l === 'FATAL') return 'error'
  if (l === 'WARN' || l === 'WARNING') return 'warn'
  if (l === 'DEBUG') return 'debug'
  if (l === 'TRACE') return 'trace'
  return 'info'
}

const toggleExpand = (index) => {
  if (expandedIndex.value === index) {
    expandedIndex.value = -1
  } else {
    expandedIndex.value = index
  }
}

const DETAILS_TRANSITION_DURATION = 280
const DETAILS_TRANSITION_EASING = 'cubic-bezier(0.2, 0.65, 0.2, 1)'

const clearDetailsMotionStyles = (el) => {
  el.style.height = ''
  el.style.opacity = ''
  el.style.transform = ''
  el.style.overflow = ''
  el.style.willChange = ''
  el.style.transition = ''
}

const beforeDetailsEnter = (el) => {
  el.style.height = '0'
  el.style.opacity = '0'
  el.style.transform = 'translateY(-6px)'
  el.style.overflow = 'hidden'
  el.style.willChange = 'height, opacity, transform'
}

const enterDetails = (el, done) => {
  const targetHeight = `${el.scrollHeight}px`
  el.style.transition = `height ${DETAILS_TRANSITION_DURATION}ms ${DETAILS_TRANSITION_EASING}, opacity 220ms ease, transform ${DETAILS_TRANSITION_DURATION}ms ${DETAILS_TRANSITION_EASING}`
  requestAnimationFrame(() => {
    el.style.height = targetHeight
    el.style.opacity = '1'
    el.style.transform = 'translateY(0)'
  })
  setTimeout(done, DETAILS_TRANSITION_DURATION + 40)
}

const afterDetailsEnter = (el) => {
  el.style.height = 'auto'
  el.style.overflow = 'visible'
  el.style.willChange = ''
  el.style.transition = ''
}

const beforeDetailsLeave = (el) => {
  el.style.height = `${el.scrollHeight}px`
  el.style.opacity = '1'
  el.style.transform = 'translateY(0)'
  el.style.overflow = 'hidden'
  el.style.willChange = 'height, opacity, transform'
}

const leaveDetails = (el, done) => {
  el.style.transition = `height ${DETAILS_TRANSITION_DURATION}ms ${DETAILS_TRANSITION_EASING}, opacity 200ms ease, transform ${DETAILS_TRANSITION_DURATION}ms ${DETAILS_TRANSITION_EASING}`
  void el.offsetHeight
  requestAnimationFrame(() => {
    el.style.height = '0'
    el.style.opacity = '0'
    el.style.transform = 'translateY(-6px)'
  })
  setTimeout(done, DETAILS_TRANSITION_DURATION + 40)
}

const afterDetailsLeave = (el) => {
  clearDetailsMotionStyles(el)
}

const truncateMessage = (text, maxLength) => {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

const parsedFieldLabels = {
  timestamp: '时间戳',
  level: '级别',
  thread: '线程',
  logger: 'Logger',
  logTime: '日志时间',
  logLevel: '日志级别',
  message: '日志消息',
  traceId: 'TraceID',
  spanId: 'SpanID',
  parentSpanId: '父 SpanID',
  threadName: '线程名',
  loggerName: 'Logger',
  className: '类名',
  methodName: '方法名',
  exceptionType: '异常类型',
  exceptionMessage: '异常消息',
  stackTrace: '堆栈跟踪',
  requestId: '请求ID',
  userId: '用户ID',
  path: '请求路径',
  method: '请求方法',
  status: '状态码',
  duration: '耗时'
}

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

const getParsedFieldsTableData = (parsedFields) => {
  if (!parsedFields) return []
  const data = []
  for (const key in parsedFields) {
    if (parsedFields[key] !== null && parsedFields[key] !== undefined) {
      data.push({
        key: key,
        value: parsedFields[key]
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
}

const formatFieldValue = (value) => {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}

const formatRawHighlightValue = (value) => {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value).trim()
}

const formatParsedFieldLabel = (key) => {
  return parsedFieldLabels[key] || key
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

const rangesOverlap = (rangeA, rangeB) => {
  return rangeA.start < rangeB.end && rangeB.start < rangeA.end
}

const findAvailableRange = (rawContent, value, occupiedRanges) => {
  let start = rawContent.indexOf(value)
  while (start !== -1) {
    const candidate = {
      start,
      end: start + value.length
    }
    if (!occupiedRanges.some(range => rangesOverlap(range, candidate))) {
      return candidate
    }
    start = rawContent.indexOf(value, start + 1)
  }
  return null
}

const getRawHighlightCandidates = (log) => {
  const rawContent = String(log?.rawContent || '')
  if (!rawContent || !log?.parsedFields) return []

  return getParsedFieldsTableData(log.parsedFields)
    .map(field => ({
      key: field.key,
      value: formatRawHighlightValue(field.value),
      toneClass: getParsedFieldToneClass(field.key) || 'is-default'
    }))
    .filter(field => field.value && field.value.length > 1 && field.value.length <= rawContent.length)
}

const getRawHighlightRanges = (log) => {
  const rawContent = String(log?.rawContent || '')
  if (!rawContent) return []

  const ranges = []
  for (const field of getRawHighlightCandidates(log)) {
    const range = findAvailableRange(rawContent, field.value, ranges)
    if (range) {
      ranges.push({
        ...range,
        field: field.key,
        toneClass: field.toneClass
      })
    }
  }

  return ranges.sort((a, b) => a.start - b.start)
}

const getRawContentSegments = (log) => {
  const rawContent = String(log?.rawContent || '')
  const ranges = getRawHighlightRanges(log)
  if (!rawContent || ranges.length === 0) return [{ text: rawContent }]

  const segments = []
  let cursor = 0
  for (const range of ranges) {
    if (range.start > cursor) {
      segments.push({ text: rawContent.slice(cursor, range.start) })
    }
    segments.push({
      text: rawContent.slice(range.start, range.end),
      field: range.field,
      toneClass: range.toneClass
    })
    cursor = range.end
  }
  if (cursor < rawContent.length) {
    segments.push({ text: rawContent.slice(cursor) })
  }
  return segments
}

watch(() => props.traceId, (newVal) => {
  if (newVal && dialogVisible.value) {
    expandedIndex.value = -1
    loadLogs()
  }
}, { immediate: true })

watch(dialogVisible, (newVal) => {
  if (newVal && props.traceId) {
    loadLogs()
  }
})
</script>

<script>
export default {
  name: 'TraceTimeline'
}
</script>

<style scoped>
:deep(.trace-timeline-dialog.el-dialog),
:deep(.trace-timeline-dialog .el-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: var(--radius-comfortable) !important;
  border: 1px solid var(--border-primary) !important;
  background: var(--color-white) !important;
  overflow: hidden;
}

:deep(.trace-timeline-dialog.el-dialog .el-dialog__header),
:deep(.trace-timeline-dialog .el-dialog__header) {
  margin: 0;
  padding: 10px var(--space-24);
  border-bottom: 1px solid var(--border-primary) !important;
  background: var(--color-white) !important;
}

:deep(.trace-timeline-dialog.el-dialog .el-dialog__title),
:deep(.trace-timeline-dialog .el-dialog__title) {
  color: var(--text-primary) !important;
  font-family: var(--font-family-serif) !important;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

:deep(.trace-timeline-dialog.el-dialog .el-dialog__headerbtn),
:deep(.trace-timeline-dialog .el-dialog__headerbtn) {
  top: 12px;
}

:deep(.trace-timeline-dialog.el-dialog .el-dialog__close),
:deep(.trace-timeline-dialog .el-dialog__close) {
  color: var(--text-tertiary);
}

:deep(.trace-timeline-dialog.el-dialog .el-dialog__close:hover),
:deep(.trace-timeline-dialog .el-dialog__close:hover) {
  color: var(--text-primary);
}

:deep(.trace-timeline-dialog.el-dialog .el-dialog__body),
:deep(.trace-timeline-dialog .el-dialog__body) {
  padding: 0 var(--space-24) var(--space-20);
  overflow-x: hidden;
  background: var(--color-white) !important;
  color: var(--text-primary) !important;
}

.trace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-10);
  padding: var(--space-12) 0;
  border-bottom: 1px solid var(--border-primary);
  margin-bottom: var(--space-12);
}

.trace-info {
  display: flex;
  align-items: center;
  gap: var(--space-10);
  min-width: 0;
}

.trace-label {
  color: var(--text-tertiary);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 500;
}

.trace-value {
  font-family: var(--font-family-terminal-mono);
  color: #c96442;
  border: 1px solid rgba(201, 100, 66, 0.3);
  background: rgba(201, 100, 66, 0.08);
  border-radius: var(--radius-small);
  padding: 3px 8px;
  font-size: 12px;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.trace-stats {
  display: flex;
  gap: var(--space-10);
}

.trace-count-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  font-family: var(--font-family-sans);
  font-size: 12px;
  color: #4f6f9f;
  border: 1px solid rgba(159, 187, 224, 0.36);
  border-radius: var(--radius-small);
  background: rgba(79, 111, 159, 0.1);
}

.timeline-container {
  max-height: min(68vh, 620px);
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-color: rgba(38, 37, 30, 0.2) transparent;
}

.timeline-container::-webkit-scrollbar {
  width: 8px;
}

.timeline-container::-webkit-scrollbar-thumb {
  background: rgba(38, 37, 30, 0.2);
  border-radius: 999px;
}

.timeline-container::-webkit-scrollbar-track {
  background: transparent;
}

.timeline-empty {
  padding: var(--space-48) 0;
}

.timeline-empty :deep(.el-empty__description p) {
  color: var(--text-tertiary);
}

.timeline {
  padding: var(--space-6) 0;
}

.timeline-item {
  display: flex;
  position: relative;
  min-width: 0;
  margin-bottom: var(--space-8);
}

.timeline-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24px;
  flex-shrink: 0;
  padding-top: 7px;
}

.marker-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #9fbbe0;
  border: 2px solid var(--color-white);
  box-shadow: 0 0 0 1px rgba(79, 111, 159, 0.3);
  flex-shrink: 0;
}

.marker-dot.error {
  background: #b53333;
}

.marker-dot.warn {
  background: #b87a2e;
}

.marker-dot.debug {
  background: #9fc9a2;
}

.marker-dot.trace {
  background: #c0a8dd;
}

.marker-line {
  flex: 1;
  width: 2px;
  background: var(--border-primary);
  margin-top: var(--space-5);
  min-height: 20px;
}

.timeline-content {
  flex: 1;
  min-width: 0;
  padding: 8px 10px 10px 12px;
  cursor: pointer;
  margin-left: 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-standard);
  transition: background-color var(--duration-fast) ease, border-color var(--duration-fast) ease;
}

.timeline-content:hover {
  background: var(--surface-100);
  border-color: rgba(201, 100, 66, 0.2);
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: var(--space-10);
  margin-bottom: 3px;
  min-width: 0;
}

.timeline-time {
  font-family: var(--font-family-terminal-mono);
  color: var(--text-tertiary);
  font-size: 12px;
}

.timeline-level {
  font-family: var(--font-family-sans);
  font-weight: 600;
  font-size: 11px;
  letter-spacing: 0.02em;
  padding: 1px 6px;
  border: 1px solid transparent;
  border-radius: var(--radius-small);
}

.timeline-level.info {
  color: #4f6f9f;
  border-color: rgba(159, 187, 224, 0.46);
  background: rgba(159, 187, 224, 0.14);
}

.timeline-level.error {
  color: #b53333;
  border-color: rgba(181, 51, 51, 0.56);
  background: rgba(181, 51, 51, 0.15);
}

.timeline-level.warn {
  color: #b87a2e;
  border-color: rgba(184, 122, 46, 0.56);
  background: rgba(184, 122, 46, 0.15);
}

.timeline-level.debug {
  color: #1f8a65;
  border-color: rgba(159, 201, 162, 0.56);
  background: rgba(159, 201, 162, 0.14);
}

.timeline-level.trace {
  color: #7c62a3;
  border-color: rgba(192, 168, 221, 0.56);
  background: rgba(192, 168, 221, 0.15);
}

.timeline-source {
  font-size: 11px;
  color: var(--text-tertiary);
  font-family: var(--font-family-sans);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.expand-icon {
  margin-left: auto;
  color: var(--text-tertiary);
  transition: transform var(--duration-normal) ease;
}

.expand-icon.is-expanded {
  transform: rotate(180deg);
  color: #c96442;
}

.timeline-message {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.timeline-item.is-expanded .timeline-content {
  background: rgba(201, 100, 66, 0.08);
  border-color: rgba(201, 100, 66, 0.32);
  box-shadow: inset 2px 0 0 rgba(201, 100, 66, 0.75);
}

.timeline-details-motion {
  overflow: hidden;
  transform-origin: top center;
}

.timeline-details {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-standard);
  background: var(--surface-100);
  min-width: 0;
}

.timeline-keyline {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.keyline-card {
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid rgba(201, 100, 66, 0.22);
  border-radius: var(--radius-small);
  background: rgba(201, 100, 66, 0.05);
}

.keyline-card.is-wide {
  grid-column: 1 / -1;
}

.keyline-label {
  font-size: 10px;
  color: var(--text-tertiary);
  font-family: var(--font-family-sans);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  line-height: 1.2;
  margin-bottom: 3px;
}

.keyline-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  font-family: var(--font-family-sans);
  line-height: 1.4;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.detail-section {
  margin-top: 10px;
  min-width: 0;
}

.detail-section-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-12);
  margin-bottom: 8px;
  border-bottom: 1px solid var(--border-primary);
  padding-bottom: 6px;
}

.section-eyebrow {
  color: var(--text-tertiary);
  font-size: 10px;
  font-weight: 600;
  font-family: var(--font-family-sans);
  letter-spacing: 0.08em;
  line-height: 1;
}

.section-title {
  margin-top: 3px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
  font-family: var(--font-family-serif);
  line-height: 1.2;
}

.section-count {
  flex-shrink: 0;
  padding: 2px 7px;
  color: var(--text-tertiary);
  font-size: 10px;
  font-family: var(--font-family-sans);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  line-height: 1.2;
}

.parsed-field-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
}

.parsed-field-row {
  display: block;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-small);
  background: var(--color-white);
}

.parsed-field-row.is-message,
.parsed-field-row.is-danger {
  grid-column: 1 / -1;
}

.parsed-field-row.is-danger {
  border-color: rgba(181, 51, 51, 0.3);
  box-shadow: inset 2px 0 0 rgba(181, 51, 51, 0.72);
  background: rgba(181, 51, 51, 0.08);
}

.parsed-field-row.is-trace {
  border-color: rgba(79, 111, 159, 0.28);
  box-shadow: inset 2px 0 0 rgba(79, 111, 159, 0.72);
  background: rgba(79, 111, 159, 0.08);
}

.parsed-field-row.is-message {
  border-color: rgba(201, 100, 66, 0.3);
  box-shadow: inset 2px 0 0 rgba(201, 100, 66, 0.75);
  background: rgba(201, 100, 66, 0.08);
}

.parsed-field-row.is-time,
.parsed-field-row.is-level {
  border-color: rgba(159, 187, 224, 0.28);
  box-shadow: inset 2px 0 0 rgba(159, 187, 224, 0.75);
  background: rgba(159, 187, 224, 0.07);
}

.parsed-field-name {
  min-width: 0;
  padding: 5px 7px 0;
}

.parsed-field-label {
  display: inline;
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 600;
  font-family: var(--font-family-sans);
  letter-spacing: 0.04em;
  line-height: 1.2;
}

.parsed-field-key {
  display: inline;
  margin-left: var(--space-6);
  color: var(--text-tertiary);
  font-family: var(--font-family-mono);
  font-size: 10px;
  line-height: 1.2;
}

.parsed-field-value {
  min-width: 0;
  margin: 0;
  max-height: 122px;
  overflow: auto;
  padding: 3px 7px 7px;
  color: var(--text-primary);
  font-family: var(--font-family-mono);
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.parsed-field-value::-webkit-scrollbar {
  width: 6px;
}

.parsed-field-value::-webkit-scrollbar-thumb {
  background: rgba(38, 37, 30, 0.2);
}

.parsed-field-value::-webkit-scrollbar-track {
  background: transparent;
}

.raw-log-frame {
  overflow: hidden;
  border: 1px solid rgba(38, 37, 30, 0.22);
  border-radius: var(--radius-small);
  background: rgba(27, 26, 22, 0.88);
}

.raw-log-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px var(--space-10);
  border-bottom: 1px solid rgba(242, 241, 237, 0.12);
}

.raw-log-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(242, 241, 237, 0.35);
}

.raw-log-label {
  margin-left: 4px;
  color: rgba(242, 241, 237, 0.55);
  font-family: var(--font-family-mono);
  font-size: 11px;
}

.detail-content {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  padding: 9px 10px;
  font-family: var(--font-family-mono);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.detail-content.raw {
  color: rgba(242, 241, 237, 0.84);
}

.raw-highlight {
  display: inline;
}

.raw-highlight.is-danger {
  color: #ff8f8f;
}

.raw-highlight.is-trace {
  color: #7ed0d2;
}

.raw-highlight.is-message {
  color: #ffb48f;
}

.raw-highlight.is-time,
.raw-highlight.is-level {
  color: #9fc2ff;
}

.raw-highlight.is-default {
  color: var(--color-cream);
}

@media (max-width: 1200px) {
  .parsed-field-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .trace-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .parsed-field-list {
    grid-template-columns: 1fr;
  }

  .timeline-keyline {
    grid-template-columns: 1fr;
  }
}
</style>
