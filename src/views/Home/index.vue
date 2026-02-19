<template>
  <div class="home-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon" style="background: #409EFF">
            <el-icon :size="30"><Collection /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.sources }}</div>
            <div class="stat-label">采集源</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon" style="background: #67C23A">
            <el-icon :size="30"><Document /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.logs }}</div>
            <div class="stat-label">日志总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon" style="background: #E6A23C">
            <el-icon :size="30"><Loading /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.collecting }}</div>
            <div class="stat-label">采集中</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon" style="background: #F56C6C">
            <el-icon :size="30"><Bell /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.alerts }}</div>
            <div class="stat-label">告警数</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <el-row :gutter="20" class="content-row">
      <el-col :span="12">
        <el-card class="quick-actions">
          <template #header>
            <div class="card-header">
              <span>快捷操作</span>
            </div>
          </template>
          <div class="actions-grid">
            <div class="action-item" @click="$router.push('/collection')">
              <el-icon :size="32" color="#409EFF"><Plus /></el-icon>
              <span>新建采集源</span>
            </div>
            <div class="action-item" @click="$router.push('/logs')">
              <el-icon :size="32" color="#67C23A"><Search /></el-icon>
              <span>查询日志</span>
            </div>
            <div class="action-item" @click="$router.push('/processing')">
              <el-icon :size="32" color="#E6A23C"><Refresh /></el-icon>
              <span>处理日志</span>
            </div>
            <div class="action-item" @click="$router.push('/alerts')">
              <el-icon :size="32" color="#F56C6C"><Setting /></el-icon>
              <span>告警配置</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="system-info">
          <template #header>
            <div class="card-header">
              <span>系统信息</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="系统版本">v1.0.0</el-descriptions-item>
            <el-descriptions-item label="运行时间">2024-01-01</el-descriptions-item>
            <el-descriptions-item label="数据库">PostgreSQL</el-descriptions-item>
            <el-descriptions-item label="缓存">Redis</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { logSourceApi, rawLogApi } from '@/api'

const stats = ref({
  sources: 0,
  logs: 0,
  collecting: 0,
  alerts: 0
})

const loadStats = async () => {
  try {
    // 获取日志源数量
    const sourcesRes = await logSourceApi.getAll()
    stats.value.sources = sourcesRes.data?.length || 0
    
    // 统计采集中数量
    stats.value.collecting = sourcesRes.data?.filter(s => s.status === 'COLLECTING').length || 0
    
    // 获取日志总数
    const logsRes = await rawLogApi.getAll({ page: 0, size: 1 })
    stats.value.logs = logsRes.data?.total || 0
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

onMounted(() => {
  loadStats()
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
  align-items: center;
  padding: 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-right: 15px;
}

.stat-content {
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
  margin-top: 5px;
}

.content-row {
  margin-top: 20px;
}

.card-header {
  font-weight: bold;
}

.actions-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.action-item:hover {
  background: #ecf5ff;
  transform: translateY(-2px);
}

.action-item span {
  margin-top: 10px;
  font-size: 14px;
  color: #606266;
}
</style>
