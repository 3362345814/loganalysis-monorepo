<template>
  <div class="analysis-container">
    <!-- 顶部统计 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon analysis-icon">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalAnalysis }}</div>
              <div class="stat-label">总分析次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon success-icon">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.completed }}</div>
              <div class="stat-label">分析成功</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon warning-icon">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.highSeverity }}</div>
              <div class="stat-label">高危问题</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon time-icon">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatDurationSeconds(stats.avgTimeMs) }}</div>
              <div class="stat-label">平均耗时</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工具栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="4">
          <el-select v-model="query.projectId" placeholder="选择项目" clearable @change="handleProjectChange" style="width: 100%">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </el-col>
        <el-col :span="16" class="toolbar-actions">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索分析结果..."
            class="search-input"
            clearable
            @change="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
      </el-row>
    </el-card>

    <!-- 分析结果列表 -->
    <el-card class="table-card">
      <el-table :data="paginatedList" v-loading="loading">
        <el-table-column prop="aggregationId" label="聚合组ID" width="200">
          <template #default="{ row }">
            <el-button type="primary" link @click="jumpToAggregation(row)">
              {{ row.aggregationId }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="rootCause" label="根因分析" min-width="250">
          <template #default="{ row }">
            <el-tooltip :content="row.rootCause" placement="top" :show-after="500">
              <span class="root-cause-text">{{ row.rootCause || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="rootCauseCategory" label="问题分类" width="120">
          <template #default="{ row }">
            <el-tag :type="getCategoryType(row.rootCauseCategory)" size="small">
              {{ row.rootCauseCategory || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confidence" label="置信度" width="100">
          <template #default="{ row }">
            <el-progress 
              :percentage="Math.round((row.confidence || 0) * 100)" 
              :color="getConfidenceColor(row.confidence)"
              :stroke-width="10"
              class="confidence-progress"
            />
          </template>
        </el-table-column>
        <el-table-column prop="impactSeverity" label="影响程度" width="100">
          <template #default="{ row }">
            <el-tag :type="getSeverityType(row.impactSeverity)" size="small">
              {{ row.impactSeverity || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="processingTimeMs" label="耗时" width="80">
          <template #default="{ row }">
            {{ formatDurationSeconds(row.processingTimeMs) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="分析时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewDetail(row)">
              查看详情
            </el-button>
            <el-button
              v-if="row.status === 'FAILED'"
              type="warning"
              link
              size="small"
              :loading="retryingId === row.aggregationId"
              :disabled="isAnalysisRunning(row.status)"
              @click="retryAnalysis(row)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="分析详情"
      width="800px"
      destroy-on-close
    >
      <div v-if="currentDetail" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="聚合组ID">
            {{ currentDetail.aggregationId }}
          </el-descriptions-item>
          <el-descriptions-item label="分析状态">
            <el-tag :type="getStatusType(currentDetail.status)">
              {{ getStatusText(currentDetail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="问题分类">
            <el-tag :type="getCategoryType(currentDetail.rootCauseCategory)">
              {{ currentDetail.rootCauseCategory || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="影响程度">
            <el-tag :type="getSeverityType(currentDetail.impactSeverity)">
              {{ currentDetail.impactSeverity || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="置信度">
            {{ currentDetail.confidence ? (currentDetail.confidence * 100).toFixed(1) + '%' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="处理耗时">
            {{ formatDurationSeconds(currentDetail.processingTimeMs) }}
          </el-descriptions-item>
          <el-descriptions-item label="使用模型">
            {{ currentDetail.modelName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="分析时间">
            {{ formatTime(currentDetail.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">根因分析</el-divider>
        <div class="detail-section">
          <p class="root-cause">{{ currentDetail.rootCause || '暂无' }}</p>
        </div>

        <el-divider content-position="left">详细分析</el-divider>
        <div class="detail-section">
          <p>{{ currentDetail.analysisDetail || '暂无' }}</p>
        </div>

        <el-divider content-position="left">影响范围</el-divider>
        <div class="detail-section">
          <p>{{ currentDetail.impactScope || '暂无' }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis, CircleCheck, Warning, Timer, Refresh, Search } from '@element-plus/icons-vue'
import { aggregationApi, analysisApi, projectApi } from '@/api'

const router = useRouter()

// 状态
const loading = ref(false)
const analysisList = ref([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailDialogVisible = ref(false)
const currentDetail = ref(null)
const projects = ref([])
const retryingId = ref('')

const query = ref({
  projectId: null
})

const handleProjectChange = () => {
  currentPage.value = 1
  handleRefresh()
}

const loadProjects = async () => {
  try {
    const res = await projectApi.getEnabled()
    projects.value = res.data || []
  } catch (error) {
    console.error('加载项目失败:', error)
  }
}

// 统计数据
const stats = ref({
  totalAnalysis: 0,
  completed: 0,
  highSeverity: 0,
  avgTimeMs: 0
})

// 筛选后的列表
const filteredList = computed(() => {
  if (!searchKeyword.value) {
    return analysisList.value
  }
  const keyword = searchKeyword.value.toLowerCase()
  return analysisList.value.filter(item => 
    item.aggregationId?.toLowerCase().includes(keyword) ||
    item.rootCause?.toLowerCase().includes(keyword) ||
    item.rootCauseCategory?.toLowerCase().includes(keyword)
  )
})

// 当前页显示的数据
const paginatedList = computed(() => {
  const list = filteredList.value
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return list.slice(start, end)
})

// 获取分类标签类型
const getCategoryType = (category) => {
  const typeMap = {
    'DATABASE': 'danger',
    'NETWORK': 'warning',
    'MEMORY': 'danger',
    'CODE': 'info',
    'CONFIG': 'warning',
    'UNKNOWN': 'info'
  }
  return typeMap[category] || 'info'
}

// 获取严重程度标签类型
const getSeverityType = (severity) => {
  const typeMap = {
    'HIGH': 'danger',
    'MEDIUM': 'warning',
    'LOW': 'info'
  }
  return typeMap[severity] || 'info'
}

// 获取状态标签类型
const getStatusType = (status) => {
  const typeMap = {
    'COMPLETED': 'success',
    'PROCESSING': 'warning',
    'FAILED': 'danger',
    'PENDING': 'info'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    'COMPLETED': '分析完成',
    'PROCESSING': '正在分析',
    'FAILED': '分析失败',
    'PENDING': '等待分析',
    'PARSE_ERROR': '解析失败'
  }
  return textMap[status] || status || '-'
}

const isAnalysisRunning = (status) => {
  return status === 'PROCESSING' || status === 'PENDING'
}

// 获取置信度颜色
const getConfidenceColor = (confidence) => {
  if (!confidence) return 'var(--text-tertiary)'
  if (confidence >= 0.9) return 'var(--color-success)'
  if (confidence >= 0.7) return 'var(--color-gold)'
  return 'var(--color-error)'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const formatDurationSeconds = (milliseconds) => {
  if (!milliseconds && milliseconds !== 0) return '-'
  return `${(milliseconds / 1000).toFixed(2)}s`
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await analysisApi.getRecent(100)
    let list = res.data || []
    
    if (query.value.projectId) {
      list = list.filter(item => item.projectId === query.value.projectId)
    }
    
    analysisList.value = list
    total.value = list.length
    calculateStats()
  } catch (error) {
    console.error('加载分析结果失败:', error)
    ElMessage.error('加载分析结果失败')
  } finally {
    loading.value = false
  }
}

// 计算统计数据
const calculateStats = () => {
  const list = analysisList.value
  stats.value.totalAnalysis = list.length
  stats.value.completed = list.filter(item => item.status === 'COMPLETED').length
  stats.value.highSeverity = list.filter(item => item.impactSeverity === 'HIGH').length
  
  const totalTime = list.reduce((sum, item) => sum + (item.processingTimeMs || 0), 0)
  stats.value.avgTimeMs = list.length > 0 ? Math.round(totalTime / list.length) : 0
}

// 刷新
const handleRefresh = () => {
  loadData()
  ElMessage.success('刷新成功')
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  total.value = filteredList.value.length
}

// 查看详情
const handleViewDetail = (row) => {
  currentDetail.value = row
  detailDialogVisible.value = true
}

const retryAnalysis = async (row) => {
  if (!row.aggregationId) {
    ElMessage.error('无法获取聚合组ID')
    return
  }

  retryingId.value = row.aggregationId
  try {
    const groupRes = await aggregationApi.getByGroupId(row.aggregationId)
    const group = groupRes.data
    if (!group?.id) {
      ElMessage.error('未找到对应聚合组')
      return
    }

    const contextRes = await aggregationApi.getContext(group.id, { contextSize: 10 })
    const contextData = contextRes.data || {}

    const analysisData = {
      aggregationId: group.id,
      groupId: group.groupId,
      severity: group.severity || 'INFO',
      name: contextData.name || group.name,
      eventCount: contextData.eventCount || group.eventCount,
      startTime: contextData.startTime || group.firstEventTime,
      endTime: contextData.endTime || group.lastEventTime,
      representativeLog: contextData.representativeLog || group.representativeLog,
      relatedLogs: contextData.relatedLogs || [],
      contextBefore: contextData.contextBefore || [],
      contextAfter: contextData.contextAfter || []
    }

    await analysisApi.trigger(analysisData)
    row.status = 'PROCESSING'
    row.statusMessage = '分析任务已提交，正在分析'
    ElMessage.success('已提交重试分析任务')
    await loadData()
  } catch (error) {
    console.error('重试分析失败:', error)
    ElMessage.error('重试分析失败: ' + (error.response?.data?.message || error.message || '未知错误'))
  } finally {
    retryingId.value = ''
  }
}

// 跳转到聚合组页面
const jumpToAggregation = (row) => {
  // 跳转到智能分析页面，并传入聚合组ID参数
  router.push({
    path: '/processing',
    query: {
      highlightGroupId: row.aggregationId
    }
  })
}

// 分页变化
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}

// 初始化
onMounted(() => {
  loadProjects()
  loadData()
})
</script>

<style scoped src="../styles/analysis-page.css"></style>
