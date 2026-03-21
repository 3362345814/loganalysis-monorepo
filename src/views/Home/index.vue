<template>
  <div class="home-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon-container">
            <div class="stat-icon" style="background: #409EFF">
            <el-icon :size="30"><Collection /></el-icon>
          </div>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.sources }}</div>
            <div class="stat-label">采集源</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon-container">
            <div class="stat-icon" style="background: #67C23A">
              <el-icon :size="30"><Document /></el-icon>
            </div>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.logs }}</div>
            <div class="stat-label">日志总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon-container">
          <div class="stat-icon" style="background: #E6A23C">
            <el-icon :size="30"><Loading /></el-icon>
          </div>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.collecting }}</div>
            <div class="stat-label">采集中</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon-container">
          <div class="stat-icon" style="background: #F56C6C">
            <el-icon :size="30"><Bell /></el-icon>
          </div>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.alerts }}</div>
            <div class="stat-label">告警数</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="content-row">
      <el-col :span="24">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>告警概览</span>
              <div class="header-controls">
                <el-select v-model="selectedProjectId" placeholder="全部项目" clearable style="width: 180px" @change="handleProjectChange">
                  <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
                </el-select>
              </div>
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="12">
              <div class="chart-wrapper">
                <div class="chart-title">告警趋势</div>
                <el-radio-group v-model="trendDays" size="small" @change="loadTrendData">
                  <el-radio-button label="7">近7天</el-radio-button>
                  <el-radio-button label="14">近14天</el-radio-button>
                  <el-radio-button label="30">近30天</el-radio-button>
                </el-radio-group>
                <div class="chart-container" v-loading="trendLoading">
                  <v-chart :option="trendOption" autoresize />
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="chart-wrapper">
                <div class="chart-title">告警级别分布</div>
                <div class="chart-container" v-loading="levelLoading">
                  <v-chart :option="levelOption" autoresize />
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { logSourceApi, rawLogApi, projectApi } from '@/api'
import { alertStatisticsApi } from '@/api/alertApi'

const stats = ref({
  sources: 0,
  logs: 0,
  collecting: 0,
  alerts: 0
})

const selectedProjectId = ref('')
const projects = ref([])

const trendDays = ref('7')
const trendLoading = ref(false)
const levelLoading = ref(false)
const trendData = ref([])
const levelData = ref([])

const trendOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' }
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    top: '10%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trendData.value.map(item => item.date)
  },
  yAxis: {
    type: 'value',
    minInterval: 1
  },
  series: [{
    name: '告警数量',
    type: 'line',
    smooth: true,
    areaStyle: {
      color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
        { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
      ])
    },
    lineStyle: { color: '#409EFF', width: 2 },
    itemStyle: { color: '#409EFF' },
    data: trendData.value.map(item => item.count)
  }]
}))

const levelOption = computed(() => {
  const colors = {
    CRITICAL: '#FF4D4F',
    HIGH: '#FF7A45',
    MEDIUM: '#FFA940',
    LOW: '#52C41A',
    INFO: '#909399'
  }
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center'
    },
    series: [{
      name: '告警级别',
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 4,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: 'bold' }
      },
      data: levelData.value.map(item => ({
        value: item.count,
        name: item.level,
        itemStyle: { color: colors[item.level] || '#909399' }
      }))
    }]
  }
})

const loadStats = async () => {
  try {
    const sourcesRes = await logSourceApi.getAll()
    stats.value.sources = sourcesRes.data?.length || 0
    stats.value.collecting = sourcesRes.data?.filter(s => s.status === 'COLLECTING').length || 0

    const logsRes = await rawLogApi.getAll({ page: 0, size: 1 })
    stats.value.logs = logsRes.data?.total || 0

    const alertRes = await alertStatisticsApi.getStatistics(
      selectedProjectId.value ? { projectId: selectedProjectId.value } : {}
    )
    if (alertRes.data) {
      stats.value.alerts = alertRes.data.totalAlerts || 0
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

const loadTrendData = async () => {
  trendLoading.value = true
  try {
    const params = {
      days: parseInt(trendDays.value),
      ...(selectedProjectId.value && { projectId: selectedProjectId.value })
    }
    const res = await alertStatisticsApi.getTrend(params)
    trendData.value = res.data || []
  } catch (error) {
    console.error('加载告警趋势失败:', error)
    trendData.value = []
  } finally {
    trendLoading.value = false
  }
}

const loadLevelData = async () => {
  levelLoading.value = true
  try {
    const params = selectedProjectId.value ? { projectId: selectedProjectId.value } : {}
    const res = await alertStatisticsApi.getLevelDistribution(params)
    levelData.value = res.data || []
  } catch (error) {
    console.error('加载告警级别分布失败:', error)
    levelData.value = []
  } finally {
    levelLoading.value = false
  }
}

// 获取项目列表
const loadProjects = async () => {
  try {
    const res = await projectApi.getAll()
    projects.value = res.data || []
  } catch (error) {
    console.error('加载项目列表失败:', error)
  }
}

// 项目选择变化
const handleProjectChange = () => {
  loadStats()
  loadTrendData()
  loadLevelData()
}

onMounted(() => {
  loadProjects()
  loadStats()
  loadTrendData()
  loadLevelData()
})
</script>

<style scoped>
.home-page {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.stat-icon-container {
  display: flex;
  justify-content: center;
  align-items: center;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  display: flex;
  justify-content: center;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.stat-value {
  text-align: center;
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  text-align: center;
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.content-row {
  margin-top: 20px;
}

.chart-card {
  height: auto;
}

.chart-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.chart-card :deep(.el-card__body) {
  padding: 10px;
}

.card-header {
  font-weight: bold;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chart-wrapper {
  padding: 0 10px;
}

.chart-title {
  font-weight: bold;
  font-size: 14px;
  color: #303133;
  margin-bottom: 12px;
}

.chart-container {
  width: 100%;
  height: 280px;
  margin-top: 12px;
}
</style>
