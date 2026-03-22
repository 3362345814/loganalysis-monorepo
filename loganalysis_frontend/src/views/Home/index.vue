<template>
  <div class="home-page">
    <section class="metrics-strip">
      <article
        v-for="(item, index) in metricCards"
        :key="item.key"
        class="metric-item"
        :style="{ '--metric-accent': item.color, '--metric-delay': `${index * 80}ms` }"
      >
        <div class="metric-top">
          <span class="metric-icon">
            <el-icon>
              <component :is="item.icon" />
            </el-icon>
          </span>
          <span class="metric-label">{{ item.label }}</span>
        </div>
        <div class="metric-value">{{ Number(item.value || 0).toLocaleString('zh-CN') }}</div>
        <p class="metric-sub">{{ item.description }}</p>
      </article>
    </section>

    <section class="quick-actions">
      <router-link v-for="action in quickActions" :key="action.to" :to="action.to" class="action-link">
        <span>{{ action.label }}</span>
        <el-icon><ArrowRight /></el-icon>
      </router-link>
    </section>

    <section class="overview-panel">
      <header class="panel-header">
        <div class="panel-headline">
          <h2>告警概览</h2>
          <p>聚焦近期趋势与级别分布，支持按项目快速筛选。</p>
        </div>

        <div class="panel-controls">
          <el-select
            v-model="selectedProjectId"
            placeholder="全部项目"
            clearable
            @change="handleProjectChange"
          >
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
        </div>
      </header>

      <div class="panel-grid">
        <article class="chart-panel">
          <div class="chart-panel-top">
            <h3>告警趋势</h3>
            <el-radio-group v-model="trendDays" size="small" @change="loadTrendData">
              <el-radio-button label="7">近7天</el-radio-button>
              <el-radio-button label="14">近14天</el-radio-button>
              <el-radio-button label="30">近30天</el-radio-button>
            </el-radio-group>
          </div>
          <div class="chart-container" v-loading="trendLoading">
            <v-chart :option="trendOption" autoresize />
          </div>
        </article>

        <article class="chart-panel">
          <div class="chart-panel-top">
            <h3>告警级别分布</h3>
            <span class="level-hint">{{ levelHint }}</span>
          </div>
          <div class="chart-container" v-loading="levelLoading">
            <v-chart :option="levelOption" autoresize />
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowRight, Bell, Collection, Document, Loading } from '@element-plus/icons-vue'
import { useHomeDashboard } from '@/composables/useHomeDashboard'

const {
  stats,
  selectedProjectId,
  projects,
  trendDays,
  trendLoading,
  levelLoading,
  trendOption,
  levelOption,
  levelHint,
  loadTrendData,
  handleProjectChange
} = useHomeDashboard()

const metricCards = computed(() => [
  {
    key: 'sources',
    label: '采集源',
    value: stats.sources,
    description: '当前已接入的数据源总数',
    icon: Collection,
    color: '#2f80ff'
  },
  {
    key: 'logs',
    label: '日志总量',
    value: stats.logs,
    description: '已入库日志条目（累计）',
    icon: Document,
    color: '#2db271'
  },
  {
    key: 'collecting',
    label: '采集中',
    value: stats.collecting,
    description: '正在实时采集的源数量',
    icon: Loading,
    color: '#f2994a'
  },
  {
    key: 'alerts',
    label: '告警数',
    value: stats.alerts,
    description: '当前筛选范围的告警总数',
    icon: Bell,
    color: '#e45b5b'
  }
])

const quickActions = Object.freeze([
  { label: '进入日志采集', to: '/collection' },
  { label: '进入日志查询', to: '/logs' },
  { label: '进入智能分析', to: '/analysis' }
])
</script>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metrics-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-item {
  position: relative;
  overflow: hidden;
  padding: 16px 18px;
  border: 1px solid var(--panel-border);
  border-radius: 16px;
  background: linear-gradient(150deg, rgba(255, 255, 255, 0.92), rgba(245, 249, 255, 0.85));
  box-shadow: var(--panel-shadow);
  opacity: 0;
  transform: translateY(10px);
  animation: metric-rise 0.5s cubic-bezier(0.2, 0.7, 0.2, 1) forwards;
  animation-delay: var(--metric-delay);
}

.metric-item::before {
  content: '';
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, var(--metric-accent), transparent);
}

.metric-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: #ffffff;
  background: var(--metric-accent);
}

.metric-label {
  font-size: 14px;
  color: #5d6d89;
}

.metric-value {
  margin-top: 12px;
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.01em;
  color: #1b2d4c;
}

.metric-sub {
  margin: 8px 0 0;
  font-size: 12px;
  color: #74849f;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.action-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid rgba(44, 87, 152, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  color: #30558c;
  font-size: 13px;
  font-weight: 500;
  transition: transform 0.2s ease, border-color 0.2s ease, background-color 0.2s ease;
}

.action-link:hover {
  border-color: rgba(47, 128, 255, 0.45);
  background: rgba(235, 244, 255, 0.86);
  transform: translateY(-1px);
}

.overview-panel {
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-xl);
  padding: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 250, 255, 0.88));
  box-shadow: var(--panel-shadow);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.panel-headline h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1b2d4c;
}

.panel-headline p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #6a7993;
}

.panel-controls {
  width: 220px;
}

.panel-controls :deep(.el-select) {
  width: 100%;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart-panel {
  padding: 14px;
  border: 1px solid rgba(35, 75, 136, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.chart-panel:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 28px -22px rgba(25, 55, 103, 0.8);
}

.chart-panel-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chart-panel-top h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f304d;
}

.level-hint {
  font-size: 12px;
  color: #6e7e98;
}

.chart-container {
  width: 100%;
  height: 300px;
  margin-top: 12px;
}

@keyframes metric-rise {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1200px) {
  .metrics-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quick-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .panel-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .panel-controls {
    width: 100%;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .metrics-strip,
  .quick-actions {
    grid-template-columns: minmax(0, 1fr);
  }

  .metric-value {
    font-size: 26px;
  }

  .overview-panel {
    padding: 14px;
  }

  .chart-panel {
    padding: 10px;
  }

  .chart-panel-top {
    flex-direction: column;
    align-items: flex-start;
  }

  .chart-container {
    height: 260px;
  }
}
</style>
