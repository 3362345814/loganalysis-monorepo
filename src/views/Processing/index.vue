<template>
  <div class="processing-page">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 日志聚合组 -->
      <el-tab-pane label="聚合组" name="aggregation">
        <el-card class="tool-card">
          <template #header>
            <div class="card-header">
              <span>日志聚合组</span>
              <div>
                <el-button :icon="Refresh" @click="loadAggregationGroups" :loading="aggLoading">刷新</el-button>
              </div>
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
            <el-table-column prop="representativeLog" label="代表性日志" min-width="250" show-overflow-tooltip />
            <el-table-column prop="eventCount" label="事件数" width="100" />
            <el-table-column prop="severity" label="严重程度" width="100">
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
                  :disabled="row.isAnalyzed || !canAnalysis(row)"
                >
                  {{ row.isAnalyzed ? '已分析' : (canAnalysis(row) ? '分析' : '级别不足') }}
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
      </el-tab-pane>
    </el-tabs>

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
            <el-button type="primary" link @click="jumpToLogDetail(row)">查看</el-button>
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
          :disabled="currentGroup?.isAnalyzed || !canAnalysis(currentGroup)"
        >
          {{ currentGroup?.isAnalyzed ? '已分析' : (canAnalysis(currentGroup) ? '触发AI分析' : '级别不足') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { aggregationApi, analysisApi, projectApi, logSourceApi } from '@/api'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'

const router = useRouter()

const activeTab = ref('aggregation')

// 聚合组数据
const aggLoading = ref(false)
const aggGroups = ref([])
const aggTotal = ref(0)
const aggSummary = ref({})

const projects = ref([])

const aggQuery = reactive({
  projectId: null,
  page: 0,
  size: 20,
  status: '',
  severity: ''
})

const handleProjectChange = () => {
  aggQuery.page = 0
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
      page: aggQuery.page,
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

// 检查是否允许触发分析（ERROR 及以上级别）
const canAnalysis = (row) => {
  const severity = row.severity || 'INFO'
  const severityPriority = { 'CRITICAL': 3, 'ERROR': 2, 'WARNING': 1, 'INFO': 0 }
  return (severityPriority[severity] || 0) >= 2
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
  // 检查严重程度，只有 ERROR 及以上级别才能触发分析
  const severity = row.severity || 'INFO'
  const severityPriority = { 'CRITICAL': 3, 'ERROR': 2, 'WARNING': 1, 'INFO': 0 }
  if ((severityPriority[severity] || 0) < 2) {
    ElMessage.warning('只有 ERROR 及以上级别才能触发 AI 分析')
    return
  }

  try {
    await analysisApi.trigger({
      aggregationId: row.id || row.groupId,
      groupId: row.groupId,
      severity: severity
    })
    ElMessage.success('已触发AI分析，请稍后查看结果')
    detailVisible.value = false
    // 刷新列表
    loadAggregationGroups()
    loadAggSummary()
  } catch (error) {
    console.error('触发分析失败:', error)
    ElMessage.error('触发分析失败: ' + (error.message || '未知错误'))
  }
}

// 跳转到日志详情页面
const jumpToLogDetail = async (row) => {
  // 优先使用 row 的信息，如果没有则从 currentGroup 获取
  const sourceId = row.sourceId || currentGroup.value?.sourceId
  const sourceName = row.sourceName || currentGroup.value?.sourceName

  if (!sourceId) {
    ElMessage.warning('无法获取日志源信息')
    return
  }

  // 获取项目ID - 尝试从 currentGroup 获取，如果没有则通过 API 查询
  let projectId = currentGroup.value?.projectId

  // 如果没有项目ID，尝试通过日志源API获取
  if (!projectId) {
    try {
      const sourceRes = await logSourceApi.getById(sourceId)
      if (sourceRes.data && sourceRes.data.projectId) {
        projectId = sourceRes.data.projectId
      }
    } catch (error) {
      console.warn('获取日志源项目ID失败:', error)
    }
  }

  // 构建查询参数 - 使用日志ID精确定位
  const queryParams = {
    sourceId: sourceId,
    sourceName: sourceName
  }

  // 添加项目ID
  if (projectId) {
    queryParams.projectId = projectId
  }

  // 使用日志ID精确定位（优先使用ID，其次使用时间作为后备）
  if (row.id) {
    queryParams.highlightId = row.id
  } else if (row.logTime || row.originalLogTime) {
    // 如果没有ID，使用时间作为后备
    queryParams.highlightTime = row.logTime || row.originalLogTime
  }

  // 跳转到日志显示页面
  router.push({
    path: '/logs',
    query: queryParams
  })

  // 关闭详情对话框
  detailVisible.value = false
}

onMounted(() => {
  loadProjects()
  loadAggSummary()
  loadAggregationGroups()
})
</script>

<style scoped>
.processing-page {
  padding: 20px;
}

.tool-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-content {
  margin: 0;
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}
</style>
