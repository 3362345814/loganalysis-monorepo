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
        <el-tag type="info" size="small">共 {{ logs.length }} 条日志</el-tag>
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

            <div class="timeline-details" v-if="expandedIndex === index">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="事件ID" :span="2">{{ log.id || '-' }}</el-descriptions-item>
                <el-descriptions-item label="日志源">{{ log.sourceName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="文件路径">{{ log.filePath || '-' }}</el-descriptions-item>
                <el-descriptions-item label="行号">{{ log.lineNumber || '-' }}</el-descriptions-item>
                <el-descriptions-item label="日志时间" :span="2">
                  {{ formatFullTime(log.originalLogTime || log.parsedFields?.logTime || log.collectionTime) }}
                </el-descriptions-item>
              </el-descriptions>

              <div class="parsed-fields-section" v-if="log.parsedFields && Object.keys(log.parsedFields).length > 0">
                <div class="section-header">解析信息</div>
                <el-table :data="getParsedFieldsTableData(log.parsedFields)" size="small" border>
                  <el-table-column prop="key" label="字段名" width="140">
                    <template #default="{ row }">
                      <span class="field-key">{{ row.key }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="value" label="值">
                    <template #default="{ row }">
                      <span class="field-value">{{ formatFieldValue(row.value) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="raw-content-section" v-if="log.rawContent">
                <div class="section-header">原始内容</div>
                <pre class="detail-content raw">{{ log.rawContent }}</pre>
              </div>
            </div>
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

const truncateMessage = (text, maxLength) => {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
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
  return data
}

const formatFieldValue = (value) => {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
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
.trace-timeline-dialog :deep(.el-dialog__body) {
  padding: 0 20px 20px 20px;
}

.trace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
  margin-bottom: 16px;
}

.trace-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-label {
  font-weight: 600;
  color: #606266;
}

.trace-value {
  font-family: 'Courier New', monospace;
  color: #409eff;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
}

.trace-stats {
  display: flex;
  gap: 8px;
}

.timeline-container {
  max-height: 600px;
  overflow-y: auto;
}

.timeline-empty {
  padding: 40px 0;
}

.timeline {
  padding: 8px 0;
}

.timeline-item {
  display: flex;
  position: relative;
}

.timeline-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24px;
  flex-shrink: 0;
}

.marker-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #409eff;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px #409eff;
  z-index: 1;
  flex-shrink: 0;
}

.marker-dot.error {
  background: #f56c6c;
  box-shadow: 0 0 0 2px #f56c6c;
}

.marker-dot.warn {
  background: #e6a23c;
  box-shadow: 0 0 0 2px #e6a23c;
}

.marker-dot.debug {
  background: #67c23a;
  box-shadow: 0 0 0 2px #67c23a;
}

.marker-dot.trace {
  background: #909399;
  box-shadow: 0 0 0 2px #909399;
}

.marker-line {
  flex: 1;
  width: 2px;
  background: #e4e7ed;
  margin-top: 4px;
  min-height: 20px;
}

.timeline-content {
  flex: 1;
  padding: 0 0 16px 12px;
  cursor: pointer;
  margin-left: 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.timeline-content:hover {
  background: #f5f7fa;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}

.timeline-time {
  font-family: 'Courier New', monospace;
  color: #909399;
  font-size: 13px;
}

.timeline-level {
  font-weight: 600;
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 3px;
}

.timeline-level.info {
  color: #409eff;
  background: #ecf5ff;
}

.timeline-level.error {
  color: #f56c6c;
  background: #fef0f0;
}

.timeline-level.warn {
  color: #e6a23c;
  background: #fdf6ec;
}

.timeline-level.debug {
  color: #67c23a;
  background: #f0f9eb;
}

.timeline-level.trace {
  color: #909399;
  background: #f4f4f5;
}

.timeline-source {
  font-size: 12px;
  color: #c0c4cc;
}

.expand-icon {
  margin-left: auto;
  color: #c0c4cc;
  transition: transform 0.3s;
}

.expand-icon.is-expanded {
  transform: rotate(180deg);
}

.timeline-message {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
  word-break: break-all;
}

.timeline-details {
  margin-top: 12px;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.detail-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  background: #fff;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.4;
  max-height: 200px;
  overflow-y: auto;
  margin: 4px 0;
}

.detail-content.error {
  background: #fff5f5;
  color: #f56c6c;
}

.detail-content.error.stack {
  max-height: 300px;
  font-size: 11px;
}

.detail-content.raw {
  background: #f5f7fa;
  color: #606266;
  font-family: 'Courier New', monospace;
  font-size: 11px;
}

.raw-content-section {
  margin-top: 12px;
}

.section-header {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.parsed-fields-section {
  margin-top: 12px;
}

.parsed-fields-section .section-header {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.field-key {
  color: #606266;
  font-weight: 500;
}

.field-value {
  color: #303133;
  word-break: break-all;
}
</style>
