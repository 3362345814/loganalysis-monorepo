<template>
  <div class="processing-page">
    <el-card class="tool-card">
      <template #header>
        <div class="card-header">
            <span>日志聚合</span>
            <el-button :icon="Refresh" @click="loadAggregationGroups" :loading="aggLoading">刷新</el-button>
          </div>
      </template>

      <!-- 统计卡片 -->
      <el-row :gutter="20" style="margin-bottom: 20px">
        <el-col :span="6">
          <el-statistic title="总聚合组" :value="aggSummary.total || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="活跃" :value="aggSummary.active || 0">
            <template #suffix>
              <el-tag type="success" size="small">ACTIVE</el-tag>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="已过期" :value="aggSummary.expired || 0">
            <template #suffix>
              <el-tag type="info" size="small">EXPIRED</el-tag>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="已分析" :value="aggSummary.analyzed || 0">
            <template #suffix>
              <el-tag type="warning" size="small">ANALYZED</el-tag>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

      <!-- 严重程度统计 -->
      <el-row :gutter="20" style="margin-bottom: 20px">
        <el-col :span="6">
          <el-statistic title="严重" :value="aggSummary.severityCounts?.CRITICAL || 0">
            <template #suffix>
              <el-tag type="danger" size="small">CRITICAL</el-tag>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="错误" :value="aggSummary.severityCounts?.ERROR || 0">
            <template #suffix>
              <el-tag type="danger" size="small">ERROR</el-tag>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="警告" :value="aggSummary.severityCounts?.WARNING || 0">
            <template #suffix>
              <el-tag type="warning" size="small">WARNING</el-tag>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="信息" :value="aggSummary.severityCounts?.INFO || 0">
            <template #suffix>
              <el-tag type="info" size="small">INFO</el-tag>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

          <!-- 筛选 -->
          <el-form inline style="margin-bottom: 10px">
            <el-form-item label="项目">
              <el-select v-model="aggQuery.projectId" placeholder="全部" clearable style="width: 150px" @change="handleProjectChange">
                <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="aggQuery.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="活跃" value="ACTIVE" />
                <el-option label="已过期" value="EXPIRED" />
                <el-option label="已分析" value="ANALYZED" />
              </el-select>
            </el-form-item>
            <el-form-item label="严重程度">
              <el-select v-model="aggQuery.severity" placeholder="全部" clearable style="width: 120px">
                <el-option label="严重" value="CRITICAL" />
                <el-option label="错误" value="ERROR" />
                <el-option label="警告" value="WARNING" />
                <el-option label="信息" value="INFO" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadAggregationGroups">查询</el-button>
            </el-form-item>
          </el-form>

          <!-- 聚合组列表 -->
          <el-table :data="aggGroups" v-loading="aggLoading" stripe>
            <el-table-column prop="groupId" label="聚合组ID" width="180" />
            <el-table-column prop="representativeLog" label="代表性日志" min-width="180" show-overflow-tooltip />
            <el-table-column prop="eventCount" label="事件数" width="100" />
            <el-table-column prop="severity" label="严重程度" width="120">
              <template #default="{ row }">
                <el-tag :type="getSeverityType(row.severity)">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sourceName" label="日志源" width="120" />
            <el-table-column prop="firstEventTime" label="首次发生" width="160">
              <template #default="{ row }">
                {{ formatTime(row.firstEventTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="lastEventTime" label="最后发生" width="160">
              <template #default="{ row }">
                {{ formatTime(row.lastEventTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" text @click="viewDetail(row)">详情</el-button>
                <el-button 
                  size="small" 
                  type="success" 
                  text 
                  @click="triggerAnalysis(row)"
                  :disabled="row.isAnalyzed"
                >
                  {{ row.isAnalyzed ? '已分析' : '分析' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <el-pagination
            v-model:current-page="aggQuery.page"
            v-model:page-size="aggQuery.size"
            :total="aggTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 20px; justify-content: flex-end"
            @size-change="loadAggregationGroups"
            @current-change="loadAggregationGroups"
          />
        </el-card>

    <!-- 聚合组详情对话框 -->
    <el-dialog v-model="detailVisible" title="聚合组详情" width="1000px" top="5vh">
      <el-descriptions :column="2" border v-if="currentGroup">
        <el-descriptions-item label="聚合组ID">{{ currentGroup.groupId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentGroup.status)">{{ currentGroup.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="日志源">{{ currentGroup.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="严重程度">
          <el-tag :type="getSeverityType(currentGroup.severity)">{{ currentGroup.severity }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="事件数">{{ currentGroup.eventCount }}</el-descriptions-item>
        <el-descriptions-item label="相似度">{{ currentGroup.similarityScore?.toFixed(2) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="首次发生" :span="2">
          {{ formatTime(currentGroup.firstEventTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="最后发生" :span="2">
          {{ formatTime(currentGroup.lastEventTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="代表性日志" :span="2">
          <pre class="log-content">{{ currentGroup.representativeLog }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentGroup.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 组内日志列表 -->
      <el-divider content-position="left">
        <span style="font-weight: bold;">组内日志列表</span>
        <el-tag type="info" size="small" style="margin-left: 8px;">共 {{ groupLogsTotal }} 条</el-tag>
      </el-divider>
      
      <el-table :data="groupLogs" v-loading="logsLoading" stripe max-height="400" size="small">
        <el-table-column prop="logTime" label="日志时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.logTime) || formatTime(row.originalLogTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="logLevel" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="getSeverityType(row.logLevel)" size="small">{{ row.logLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rawContent" label="日志内容" min-width="400" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="log-content-clickable">{{ row.desensitizedContent || row.rawContent }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sourceName" label="日志源" width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showParsedInfo(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-pagination
        v-model:current-page="logsQuery.page"
        v-model:page-size="logsQuery.size"
        :total="groupLogsTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 15px; justify-content: flex-end"
        @size-change="loadGroupLogs"
        @current-change="loadGroupLogs"
      />

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          type="primary"
          @click="triggerAnalysis(currentGroup)"
          :disabled="currentGroup?.isAnalyzed"
        >
          <el-icon><MagicStick /></el-icon>
          {{ currentGroup?.isAnalyzed ? '已分析' : '触发AI分析' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 解析信息面板 -->
    <el-dialog v-model="parsedInfoVisible" title="日志解析信息" width="600px" top="10vh">
      <div class="parsed-info-content">
        <div class="parsed-info-summary">
          <span class="parsed-info-count">{{ Object.keys(parsedInfoFields).length }} 个字段</span>
        </div>
        <el-table :data="parsedInfoTableData" size="small" max-height="400" stripe>
          <el-table-column prop="key" label="字段名" width="120">
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

        <div v-if="aiAnalysisLoading" class="ai-analysis-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在分析...</span>
        </div>
        <div v-else-if="aiAnalysisError" class="ai-analysis-error">
          <el-alert type="error" :closable="false" show-icon>
            <template #title>
              <span>分析失败</span>
            </template>
            <template #default>
              <span>{{ aiAnalysisError }}</span>
            </template>
          </el-alert>
        </div>
        <div v-else-if="aiAnalysisResult" class="ai-analysis-result">
          <el-alert type="success" :closable="false" show-icon>
            <template #title>
              <span>AI分析结果</span>
            </template>
            <template #default>
              <span>{{ aiAnalysisResult }}</span>
            </template>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button @click="parsedInfoVisible = false">关闭</el-button>
        <el-button v-if="currentHoverTraceId" type="primary" @click="openTraceTimeline">
          <el-icon><TraceIcon /></el-icon>
          链路追踪
        </el-button>
      </template>
    </el-dialog>

    <!-- 链路追踪时间线弹窗 -->
    <TraceTimeline v-model="traceTimelineVisible" :traceId="currentHoverTraceId" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, nextTick, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Refresh, MagicStick, Close, MagicStick as AiIcon, Link as TraceIcon, Loading } from '@element-plus/icons-vue'
import { aggregationApi, analysisApi, projectApi, logSourceApi, rawLogApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import TraceTimeline from '@/components/TraceTimeline.vue'

const router = useRouter()
const route = useRoute()

// 聚合组数据
const aggLoading = ref(false)
const aggGroups = ref([])
const aggTotal = ref(0)
const aggSummary = ref({})

// 解析信息面板状态
const parsedInfoVisible = ref(false)
const parsedInfoFields = ref({})
const currentLog = ref(null)
const currentHoverTraceId = ref('')
const aiAnalysisLoading = ref(false)
const aiAnalysisResult = ref('')
const aiAnalysisError = ref('')

// 链路追踪相关状态
const traceTimelineVisible = ref(false)

const projects = ref([])

const aggQuery = reactive({
  projectId: null,
  page: 1,
  size: 20,
  status: '',
  severity: ''
})

const handleProjectChange = () => {
  aggQuery.page = 1  // 重置为第1页
  loadAggregationGroups()
}

const loadProjects = async () => {
  try {
    const res = await projectApi.getEnabled()
    projects.value = res.data || []
  } catch (error) {
    console.error('加载项目失败:', error)
  }
}

// 详情对话框
const detailVisible = ref(false)
const currentGroup = ref(null)

// 组内日志数据
const logsLoading = ref(false)
const groupLogs = ref([])
const groupLogsTotal = ref(0)
const logsQuery = reactive({
  page: 1,
  size: 10
})

// 加载聚合组数据
const loadAggregationGroups = async () => {
  aggLoading.value = true
  try {
    const params = {
      page: aggQuery.page - 1,  // 转换为0-based索引
      size: aggQuery.size
    }
    if (aggQuery.status) params.status = aggQuery.status
    if (aggQuery.severity) params.severity = aggQuery.severity
    if (aggQuery.projectId) params.projectId = aggQuery.projectId

    const res = await aggregationApi.getAll(params)
    aggGroups.value = res.data.content || []
    aggTotal.value = res.data.total || 0
  } catch (error) {
    console.error('加载聚合组失败:', error)
  } finally {
    aggLoading.value = false
  }
}

// 加载聚合组统计摘要
const loadAggSummary = async () => {
  try {
    const res = await aggregationApi.getSummary()
    aggSummary.value = res.data || {}
  } catch (error) {
    console.error('加载聚合组摘要失败:', error)
  }
}

// 获取严重程度类型
const getSeverityType = (severity) => {
  const typeMap = {
    'CRITICAL': 'danger',
    'ERROR': 'danger',
    'WARNING': 'warning',
    'INFO': 'success'
  }
  return typeMap[severity] || 'info'
}

// 获取状态类型
const getStatusType = (status) => {
  const typeMap = {
    'ACTIVE': 'success',
    'EXPIRED': 'info',
    'ANALYZED': 'warning'
  }
  return typeMap[status] || 'info'
}

// 格式化时间
const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

// 检查是否允许触发分析（现在允许所有级别手动触发）
const canAnalysis = (row) => {
  // 手动分析现在允许所有严重程度
  return true
}

// 查看详情
const viewDetail = async (row) => {
  currentGroup.value = row
  logsQuery.page = 1
  logsQuery.size = 10
  groupLogs.value = []
  groupLogsTotal.value = 0
  detailVisible.value = true
  loadGroupLogs()
}

// 加载组内日志
const loadGroupLogs = async () => {
  if (!currentGroup.value) return
  
  logsLoading.value = true
  try {
    const params = {
      page: logsQuery.page - 1,
      size: logsQuery.size
    }
    const res = await aggregationApi.getLogsById(currentGroup.value.id, params)
    groupLogs.value = res.data.content || []
    groupLogsTotal.value = res.data.total || 0
  } catch (error) {
    console.error('加载组内日志失败:', error)
    groupLogs.value = []
    groupLogsTotal.value = 0
  } finally {
    logsLoading.value = false
  }
}

// 触发AI分析
const triggerAnalysis = async (row) => {
  const aggregationId = row.id
  if (!aggregationId) {
    ElMessage.error('无法获取聚合组ID')
    return
  }

  try {
    ElMessage.info('正在获取日志上下文...')
    const contextRes = await aggregationApi.getContext(aggregationId, { contextSize: 10 })

    if (!contextRes.data) {
      ElMessage.error('获取上下文失败')
      return
    }

    const contextData = contextRes.data

    const analysisData = {
      aggregationId: aggregationId,
      groupId: row.groupId,
      severity: row.severity || 'INFO',
      name: contextData.name,
      eventCount: contextData.eventCount,
      startTime: contextData.startTime,
      endTime: contextData.endTime,
      representativeLog: contextData.representativeLog,
      relatedLogs: contextData.relatedLogs || [],
      contextBefore: contextData.contextBefore || [],
      contextAfter: contextData.contextAfter || []
    }

    // 不等待结果，直接提示已触发
    analysisApi.trigger(analysisData).catch(() => {})

    ElMessage.success('已触发AI分析，请稍后在智能分析页面查看')
    detailVisible.value = false
  } catch (error) {
    console.error('触发分析失败:', error)
    ElMessage.error('触发分析失败: ' + (error.response?.data?.message || error.message || '未知错误'))
  }
}

// 重试分析
const retryAnalysis = async (row) => {
  // 对于失败的分析，直接调用triggerAnalysis重新分析
  await triggerAnalysis(row)
}

// 显示日志解析信息面板
const showParsedInfo = async (row) => {
  try {
    let logData = row
    
    if (!logData.parsedFields || Object.keys(logData.parsedFields).length === 0) {
      const idRes = await rawLogApi.getById(row.id)
      if (idRes.data) {
        logData = idRes.data
      }
    }
    
    const parsedFields = logData.parsedFields || {}
    
    const info = {}
    for (const key in parsedFields) {
      if (parsedFields[key] !== null && parsedFields[key] !== undefined) {
        info[key] = parsedFields[key]
      }
    }
    
    if (Object.keys(info).length === 0 && logData.rawContent) {
      info['rawContent'] = logData.rawContent
    }
    
    parsedInfoFields.value = info
    currentLog.value = logData
    currentHoverTraceId.value = parsedFields.traceId || ''
    aiAnalysisResult.value = ''
    aiAnalysisError.value = ''
    parsedInfoVisible.value = true
  } catch (error) {
    console.error('获取日志详情失败:', error)
    ElMessage.error('获取日志详情失败')
  }
}

// 关闭解析信息面板
const closeParsedInfo = () => {
  parsedInfoVisible.value = false
}

// 格式化字段名
const formatFieldKey = (key) => {
  const keyMap = {
    logTime: '日志时间',
    logLevel: '日志级别',
    threadName: '线程名',
    loggerName: '日志器',
    className: '类名',
    message: '消息',
    exceptionType: '异常类型',
    exceptionMessage: '异常信息',
    traceId: 'TraceId',
    spanId: 'SpanId',
    requestId: '请求ID',
    userId: '用户ID',
    ip: 'IP地址',
    url: '请求URL',
    method: '请求方法',
    statusCode: '状态码',
    responseTime: '响应时间',
    errorStack: '错误堆栈'
  }
  return keyMap[key] || key
}

// 格式化字段值
const formatFieldValue = (value) => {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

// 获取解析信息表格数据
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

// 打开链路追踪
const openTraceTimeline = () => {
  if (currentHoverTraceId.value) {
    traceTimelineVisible.value = true
  }
}

onMounted(() => {
  loadProjects()
  loadAggSummary()
  loadAggregationGroups()

  // 检测URL参数，如果传入了highlightGroupId则自动打开对应聚合组详情
  const highlightGroupId = route.query.highlightGroupId
  if (highlightGroupId) {
    // 直接根据groupId查询聚合组
    loadAggregationByGroupId(highlightGroupId, () => {
      // 加载成功后清除URL参数
      router.replace({ path: '/processing' })
    })
  }
})

// 根据groupId加载聚合组并打开详情
const loadAggregationByGroupId = async (groupId, onSuccess) => {
  try {
    const res = await aggregationApi.getByGroupId(groupId)
    if (res.data) {
      // 设置当前聚合组并打开详情
      currentGroup.value = res.data
      // 重置日志查询参数
      logsQuery.page = 1
      logsQuery.size = 10
      groupLogs.value = []
      groupLogsTotal.value = 0
      detailVisible.value = true
      // 加载组内日志
      loadGroupLogs()
      // 成功后回调
      if (onSuccess) onSuccess()
    } else {
      ElMessage.warning('未找到该聚合组: ' + groupId)
    }
  } catch (error) {
    console.error('加载聚合组失败:', error)
    ElMessage.error('加载聚合组失败: ' + (error.message || '未知错误'))
  }
}
</script>

<style scoped>
.processing-page {
  padding: var(--space-24);
}

.tool-card {
  margin-bottom: var(--space-24);
  border-radius: var(--radius-comfortable);
  border: 1px solid var(--border-primary);
  background: var(--color-white);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-content {
  margin: 0;
  padding: var(--space-16);
  background-color: var(--surface-300);
  border-radius: var(--radius-standard);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}

.parsed-info-content {
  padding: var(--space-24);
}

.parsed-info-summary {
  margin-bottom: var(--space-24);
  padding-bottom: var(--space-16);
  border-bottom: 1px solid var(--border-primary);
}

.parsed-info-count {
  font-size: 13px;
  color: var(--text-secondary);
}

.field-key {
  font-weight: 500;
  color: var(--text-secondary);
}

.field-value {
  color: var(--text-primary);
  word-break: break-all;
}

.parsed-info-footer {
  padding: var(--space-24);
  border-top: 1px solid var(--border-primary);
  display: flex;
  gap: var(--space-16);
}

.ai-analysis-loading {
  margin-top: var(--space-24);
  padding: var(--space-24);
  text-align: center;
  color: #c96442;
}

.ai-analysis-error {
  margin-top: var(--space-24);
}

.ai-analysis-result {
  margin-top: var(--space-24);
}
</style>
