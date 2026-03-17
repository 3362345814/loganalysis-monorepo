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

      <el-table :data="alertList" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="alertId" label="告警编号" width="180" />
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
        <el-table-column prop="assignedToName" label="处理人" width="120" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="success"
              size="small"
              @click="handleAcknowledge(row)"
            >确认</el-button>
            <el-button
              v-if="row.status === 'ACKNOWLEDGED'"
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
        style="margin-top: 20px; justify-content: flex-end"
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
        <el-descriptions-item label="处理人">{{ currentAlert.assignedToName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="解决备注">{{ currentAlert.resolutionNote || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button
          v-if="currentAlert?.status === 'PENDING'"
          type="primary"
          @click="handleAcknowledge(currentAlert)"
        >确认告警</el-button>
        <el-button
          v-if="currentAlert?.status === 'ACKNOWLEDGED'"
          type="success"
          @click="handleResolve(currentAlert)"
        >解决告警</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, WarningFilled, Clock, Calendar, Search, Refresh, Setting } from '@element-plus/icons-vue'
import { alertRecordApi, alertStatisticsApi } from '@/api/alertApi'

const router = useRouter()

// 统计数据
const statistics = ref({})

// 筛选表单
const filterForm = reactive({
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

// 获取统计数据
const fetchStatistics = async () => {
  try {
    const res = await alertStatisticsApi.getStatistics()
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

// 搜索
const handleSearch = () => {
  pagination.page = 1
  fetchAlertList()
}

// 重置
const handleReset = () => {
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
  fetchStatistics()
  fetchAlertList()
})
</script>

<style scoped>
.alerts-container {
  padding: 20px;
}

.statistics-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
}

.stat-card :deep(.el-card__body) {
  padding: 20px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.stat-icon.total {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.stat-icon.pending {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.stat-icon.critical {
  background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%);
  color: #f5576c;
}

.stat-icon.today {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.filter-card {
  margin-bottom: 20px;
}

.filter-form {
  margin-bottom: 0;
}

.table-card :deep(.el-card__header) {
  padding: 14px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content-box {
  max-height: 100px;
  overflow: auto;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
