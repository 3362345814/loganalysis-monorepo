<template>
  <div class="alerts-container">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="statistics-row">
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon total">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.totalAlerts || 0 }}</div>
              <div class="stat-label">总告警数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon pending">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.pendingAlerts || 0 }}</div>
              <div class="stat-label">待处理</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon critical">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.criticalAlerts || 0 }}</div>
              <div class="stat-label">严重告警</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon today">
              <el-icon><Calendar /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.todayAlerts || 0 }}</div>
              <div class="stat-label">今日告警</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索筛选 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="filterForm.projectId" placeholder="全部项目" clearable style="width: 160px" @change="handleProjectChange">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警状态">
          <el-select v-model="filterForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已确认" value="ACKNOWLEDGED" />
            <el-option label="已解决" value="RESOLVED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警级别">
          <el-select v-model="filterForm.level" placeholder="请选择" clearable style="width: 120px">
            <el-option label="严重" value="CRITICAL" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
            <el-option label="信息" value="INFO" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 告警列表 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>告警列表</span>
          <el-button type="primary" @click="goToRuleManage">
            <el-icon><Setting /></el-icon>规则管理
          </el-button>
        </div>
      </template>

      <el-table :data="alertList" v-loading="loading" style="width: 100%">
        <el-table-column prop="alertId" label="告警编号" width="180" />
        <el-table-column prop="projectName" label="项目" width="150">
          <template #default="{ row }">
            {{ row.projectName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="title" label="告警标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="alertLevel" label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.alertLevel)" size="small">
              {{ getLevelText(row.alertLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggeredAt" label="触发时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.triggeredAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleView(row)">查看</el-button>
            <el-button
              link
              type="warning"
              size="small"
              @click="handleResolve(row)"
            >解决</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        class="list-pagination"
      />
    </el-card>

    <!-- 告警详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="告警详情" width="700px">
      <el-descriptions :column="2" border v-if="currentAlert">
        <el-descriptions-item label="告警编号">{{ currentAlert.alertId }}</el-descriptions-item>
        <el-descriptions-item label="告警级别">
          <el-tag :type="getLevelType(currentAlert.alertLevel)">
            {{ getLevelText(currentAlert.alertLevel) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="告警状态">
          <el-tag :type="getStatusType(currentAlert.status)">
            {{ getStatusText(currentAlert.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="所属项目">{{ currentAlert.projectName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="触发时间">
          {{ formatDateTime(currentAlert.triggeredAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="告警标题" :span="2">{{ currentAlert.title }}</el-descriptions-item>
        <el-descriptions-item label="告警内容" :span="2">
          <div class="content-box">{{ currentAlert.content }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="触发条件" :span="2">
          {{ currentAlert.triggerCondition }}
        </el-descriptions-item>
        <el-descriptions-item label="触发值" :span="2">
          <div class="content-box">{{ currentAlert.triggerValue }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="解决备注" :span="2">{{ currentAlert.resolutionNote || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="detail-footer">
          <div>
            <el-button
              v-if="currentAlert?.aggregationId"
              type="primary"
              plain
              @click="jumpToAggregation"
            >
              查看聚合组
            </el-button>
            <el-button
              v-if="currentAlert?.logId"
              type="primary"
              plain
              @click="showLogDetail"
            >
              查看日志详情
            </el-button>
            <el-button
              v-if="currentAlert?.traceId"
              type="primary"
              plain
              @click="openTraceTimeline"
            >
              链路追踪
            </el-button>
          </div>
          <div>
            <el-button @click="detailDialogVisible = false">关闭</el-button>
            <el-button
              v-if="currentAlert?.status !== 'RESOLVED'"
              type="success"
              @click="handleResolve(currentAlert)"
            >解决告警</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 解决告警对话框 -->
    <el-dialog v-model="resolveDialogVisible" title="解决告警" width="500px">
      <el-form :model="resolveForm" label-width="80px">
        <el-form-item label="解决备注">
          <el-input
            v-model="resolveForm.resolutionNote"
            type="textarea"
            :rows="4"
            placeholder="请输入解决备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmResolve">确定</el-button>
      </template>
    </el-dialog>

    <!-- 日志解析详情对话框 -->
    <el-dialog v-model="parsedInfoVisible" title="日志解析信息" width="600px" top="10vh">
      <div class="parsed-info-content">
        <div class="parsed-info-summary">
          <span class="parsed-info-count">{{ Object.keys(parsedInfoFields).length }} 个字段</span>
        </div>
        <el-table :data="parsedInfoTableData" size="small" max-height="400">
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
    <TraceTimeline v-model="traceTimelineVisible" :traceId="currentAlert?.traceId" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, WarningFilled, Clock, Calendar, Search, Refresh, Setting, MagicStick, Link as TraceIcon, Loading } from '@element-plus/icons-vue'
import { alertRecordApi, alertStatisticsApi } from '@/api/alertApi'
import { logSourceApi, projectApi, analysisApi, rawLogApi } from '@/api'
import TraceTimeline from '@/components/TraceTimeline.vue'

const router = useRouter()

// 项目列表
const projects = ref([])

// 统计数据
const statistics = ref({})

// 筛选表单
const filterForm = reactive({
  projectId: '',
  status: '',
  level: '',
  dateRange: []
})

// 告警列表
const alertList = ref([])
const loading = ref(false)

// 分页
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 详情对话框
const detailDialogVisible = ref(false)
const currentAlert = ref(null)

// 解决对话框
const resolveDialogVisible = ref(false)
const resolveForm = reactive({
  resolutionNote: ''
})

// 链路追踪相关状态
const traceTimelineVisible = ref(false)

// 日志详情相关状态
const parsedInfoVisible = ref(false)
const parsedInfoFields = ref({})
const currentLog = ref(null)
const currentHoverTraceId = ref('')

// 获取统计数据
const fetchStatistics = async () => {
  try {
    const res = await alertStatisticsApi.getStatistics(
      filterForm.projectId ? { projectId: filterForm.projectId } : {}
    )
    statistics.value = res.data
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

// 获取告警列表
const fetchAlertList = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      ...(filterForm.projectId && { projectId: filterForm.projectId }),
      ...(filterForm.status && { status: filterForm.status }),
      ...(filterForm.level && { level: filterForm.level }),
      ...(filterForm.dateRange?.length === 2 && {
        startTime: filterForm.dateRange[0] + 'T00:00:00',
        endTime: filterForm.dateRange[1] + 'T23:59:59'
      })
    }
    const res = await alertRecordApi.query(params)
    alertList.value = res.data.content || []
    pagination.total = res.data.totalElements || 0
  } catch (error) {
    console.error('获取告警列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取项目列表
const fetchProjects = async () => {
  try {
    const res = await projectApi.getAll()
    projects.value = res.data || []
  } catch (error) {
    console.error('获取项目列表失败:', error)
  }
}

// 项目选择变化
const handleProjectChange = () => {
  pagination.page = 1
  fetchAlertList()
  fetchStatistics()
}

// 搜索
const handleSearch = () => {
  pagination.page = 1
  fetchAlertList()
}

// 重置
const handleReset = () => {
  filterForm.projectId = ''
  filterForm.status = ''
  filterForm.level = ''
  filterForm.dateRange = []
  handleSearch()
}

// 分页变化
const handlePageChange = (page) => {
  pagination.page = page
  fetchAlertList()
}

const handleSizeChange = (size) => {
  pagination.size = size
  fetchAlertList()
}

// 查看详情
const handleView = async (row) => {
  try {
    const res = await alertRecordApi.getById(row.id)
    currentAlert.value = res.data
    detailDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取告警详情失败')
  }
}

// 跳转到聚合组页面
const jumpToAggregation = () => {
  if (currentAlert.value?.aggregationId) {
    router.push({
      path: '/processing',
      query: { highlightGroupId: currentAlert.value.aggregationId }
    })
    detailDialogVisible.value = false
  }
}

// 跳转到日志页面
const jumpToLogs = async () => {
  if (currentAlert.value?.sourceIds && currentAlert.value.sourceIds.length > 0) {
    const sourceId = currentAlert.value.sourceIds[0]
    // 获取日志源名称
    let sourceName = ''
    try {
      const res = await logSourceApi.getById(sourceId)
      sourceName = res.data?.name || ''
    } catch (error) {
      console.warn('获取日志源名称失败:', error)
    }
    
    const queryParams = {
      sourceId: sourceId,
      sourceName: sourceName,
      highlightTime: currentAlert.value.triggeredAt
    }
    router.push({
      path: '/logs',
      query: queryParams
    })
    detailDialogVisible.value = false
  } else {
    ElMessage.warning('无法获取日志源信息')
  }
}

// 打开链路追踪弹窗
const openTraceTimeline = () => {
  if (currentAlert.value?.traceId) {
    traceTimelineVisible.value = true
  } else {
    ElMessage.warning('该告警没有traceId')
  }
}

// 显示日志详情面板
const showLogDetail = async () => {
  try {
    let logData = null
    
    if (currentAlert.value?.logId) {
      const idRes = await rawLogApi.getById(currentAlert.value.logId)
      if (idRes.data) {
        logData = idRes.data
      }
    }
    
    if (!logData) {
      ElMessage.warning('无法获取日志详情')
      return
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
    currentHoverTraceId.value = parsedFields.traceId || currentAlert.value?.traceId || ''
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

// 确认告警
const handleAcknowledge = async (row) => {
  try {
    await ElMessageBox.confirm('确定要确认此告警吗？', '提示', {
      type: 'warning'
    })
    await alertRecordApi.acknowledge(row.id, { userName: 'Admin' })
    ElMessage.success('告警已确认')
    fetchAlertList()
    fetchStatistics()
    detailDialogVisible.value = false
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 解决告警
const handleResolve = (row) => {
  currentAlert.value = row
  resolveForm.resolutionNote = ''
  resolveDialogVisible.value = true
}

const confirmResolve = async () => {
  try {
    await alertRecordApi.resolve(currentAlert.value.id, {
      userName: 'Admin',
      resolutionNote: resolveForm.resolutionNote
    })
    ElMessage.success('告警已解决')
    resolveDialogVisible.value = false
    detailDialogVisible.value = false
    fetchAlertList()
    fetchStatistics()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 跳转到规则管理
const goToRuleManage = () => {
  router.push('/alerts/rules')
}

// 工具函数
const getLevelType = (level) => {
  const map = {
    CRITICAL: 'danger',
    HIGH: 'warning',
    MEDIUM: 'info',
    LOW: 'success',
    INFO: 'info'
  }
  return map[level] || 'info'
}

const getLevelText = (level) => {
  const map = {
    CRITICAL: '严重',
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低',
    INFO: '信息'
  }
  return map[level] || level
}

const getStatusType = (status) => {
  const map = {
    PENDING: 'danger',
    ACKNOWLEDGED: 'warning',
    RESOLVED: 'success',
    IGNORED: 'info'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    PENDING: '待处理',
    ACKNOWLEDGED: '已确认',
    RESOLVED: '已解决',
    IGNORED: '已忽略'
  }
  return map[status] || status
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchProjects()
  fetchStatistics()
  fetchAlertList()
})
</script>

<style scoped>
.alerts-container {
  padding: var(--space-24);
}

.statistics-row {
  margin-bottom: var(--space-24);
}

.stat-card {
  border-radius: var(--radius-comfortable);
  border: 1px solid var(--border-primary);
  background: var(--color-white);
}

.stat-card :deep(.el-card__body) {
  padding: var(--space-24);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: var(--space-24);
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-comfortable);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.stat-icon.total {
  background: rgba(201, 100, 66, 0.12);
  color: #c96442;
}

.stat-icon.pending {
  background: rgba(184, 122, 46, 0.12);
  color: #b87a2e;
}

.stat-icon.critical {
  background: rgba(181, 51, 51, 0.12);
  color: #b53333;
}

.stat-icon.today {
  background: rgba(31, 138, 101, 0.12);
  color: #1f8a65;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-top: 4px;
}

.filter-card {
  margin-bottom: var(--space-24);
  border-radius: var(--radius-comfortable);
  border: 1px solid var(--border-primary);
  background: var(--color-white);
}

.filter-form {
  margin-bottom: 0;
}

.table-card :deep(.el-card__header) {
  padding: var(--space-16) var(--space-24);
  border-bottom: 1px solid var(--border-primary);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-pagination {
  margin-top: var(--space-24);
  justify-content: flex-end;
}

.detail-footer {
  display: flex;
  justify-content: space-between;
  width: 100%;
}

.content-box {
  max-height: 100px;
  overflow: auto;
  background: var(--surface-300);
  padding: var(--space-8);
  border-radius: var(--radius-standard);
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: var(--font-family-mono);
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
</style>
