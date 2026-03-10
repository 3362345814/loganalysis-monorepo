<template>
  <div class="logs-page">
    <!-- 筛选栏 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="日志源">
          <el-select v-model="filter.sourceId" placeholder="请选择日志源" clearable @change="handleSourceChange">
            <el-option v-for="source in sources" :key="source.id" :label="source.name" :value="source.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志级别">
          <el-select v-model="filter.logLevel" placeholder="请选择日志级别" clearable>
            <el-option label="ERROR" value="ERROR" />
            <el-option label="WARN" value="WARN" />
            <el-option label="INFO" value="INFO" />
            <el-option label="DEBUG" value="DEBUG" />
            <el-option label="TRACE" value="TRACE" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filter.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 日志列表 -->
    <el-card class="table-card">
      <el-table :data="logs" v-loading="loading" stripe max-height="600">
        <el-table-column prop="parsedFields.logTime" label="日志时间" width="180">
          <template #default="{ row }">
            {{ formatLogTime(row.parsedFields) }}
          </template>
        </el-table-column>
        <el-table-column prop="parsedFields.logLevel" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="getLogLevelType(row.parsedFields?.logLevel)" size="small">
              {{ row.parsedFields?.logLevel || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="parsedFields.threadName" label="线程" width="120" show-overflow-tooltip />
        <el-table-column prop="parsedFields.loggerName" label="Logger" min-width="200" show-overflow-tooltip />
        <el-table-column prop="rawContent" label="日志内容" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.parsedFields?.message || row.rawContent }}
          </template>
        </el-table-column>
        <el-table-column prop="collectionTime" label="采集时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.collectionTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="filter.page"
          v-model:page-size="filter.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="800px">
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="事件ID" :span="2">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="日志源">{{ currentLog.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="文件路径">{{ currentLog.filePath }}</el-descriptions-item>
        <el-descriptions-item label="行号">{{ currentLog.lineNumber }}</el-descriptions-item>
        <el-descriptions-item label="采集时间">{{ formatTime(currentLog.collectionTime) }}</el-descriptions-item>
        
        <!-- 解析字段 -->
        <el-descriptions-item label="日志时间" :span="2">
          {{ formatLogTime(currentLog.parsedFields) }}
        </el-descriptions-item>
        <el-descriptions-item label="日志级别">
          <el-tag :type="getLogLevelType(currentLog.parsedFields?.logLevel)" size="small">
            {{ currentLog.parsedFields?.logLevel || '-' }}
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
import { ref, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { logSourceApi, rawLogApi } from '@/api'

const sources = ref([])
const logs = ref([])
const loading = ref(false)
const total = ref(0)
const detailVisible = ref(false)
const currentLog = ref(null)

const filter = ref({
  sourceId: null,
  logLevel: null,
  dateRange: null,
  page: 1,
  pageSize: 20
})

const loadSources = async () => {
  try {
    const res = await logSourceApi.getAll()
    sources.value = res.data || []
  } catch (error) {
    console.error('加载日志源失败:', error)
  }
}

const loadLogs = async () => {
  if (!filter.value.sourceId) {
    ElMessage.warning('请选择日志源')
    return
  }
  
  loading.value = true
  try {
    const params = {
      page: filter.value.page - 1,
      size: filter.value.pageSize
    }
    // 如果选择了时间范围，添加时间参数
    if (filter.value.dateRange && filter.value.dateRange.length === 2) {
      params.startTime = filter.value.dateRange[0]
      params.endTime = filter.value.dateRange[1]
    }
    // 如果选择了日志级别，添加日志级别过滤
    if (filter.value.logLevel) {
      params.logLevel = filter.value.logLevel
    }
    const res = await rawLogApi.getBySourceId(filter.value.sourceId, params)
    logs.value = res.data?.content || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('加载日志失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSourceChange = () => {
  filter.value.page = 1
  if (filter.value.sourceId) {
    loadLogs()
  } else {
    logs.value = []
  }
}

const handleSearch = () => {
  filter.value.page = 1
  loadLogs()
}

const handleReset = () => {
  filter.value = {
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
  currentLog.value = row
  detailVisible.value = true
}

const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

// 格式化解析后的日志时间
const formatLogTime = (parsedFields) => {
  if (!parsedFields) return '-'
  const logTime = parsedFields.logTime
  if (!logTime) return '-'
  // 尝试解析多种时间格式
  try {
    return dayjs(logTime).format('YYYY-MM-DD HH:mm:ss')
  } catch {
    return logTime
  }
}

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
  loadSources()
})
</script>

<style scoped>
.logs-page {
  padding: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.table-card {
  min-height: 500px;
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
