<template>
  <div class="home-page">
    <section class="metrics-strip">
      <article
        v-for="(item, index) in metricCards"
        :key="item.key"
        class="metric-item"
        :class="`stagger-${index + 1}`"
        :style="{ '--metric-accent': item.color, '--metric-accent-soft': item.softColor }"
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
    color: 'var(--color-accent)',
    softColor: 'rgba(201, 100, 66, 0.12)'
  },
  {
    key: 'logs',
    label: '日志总量',
    value: stats.logs,
    description: '已入库日志条目（累计）',
    icon: Document,
    color: 'var(--color-success)',
    softColor: 'rgba(31, 138, 101, 0.12)'
  },
  {
    key: 'collecting',
    label: '采集中',
    value: stats.collecting,
    description: '正在实时采集的源数量',
    icon: Loading,
    color: 'var(--color-gold)',
    softColor: 'rgba(184, 122, 46, 0.12)'
  },
  {
    key: 'alerts',
    label: '告警数',
    value: stats.alerts,
    description: '当前筛选范围的告警总数',
    icon: Bell,
    color: 'var(--color-error)',
    softColor: 'rgba(181, 51, 51, 0.12)'
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
  gap: var(--space-24);
}

.metrics-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-16);
}

.metric-item {
  position: relative;
  overflow: hidden;
  padding: var(--space-24);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-featured);
  background: var(--color-white);
  opacity: 0;
  transform: translateY(12px);
  animation: fadeInUp var(--duration-slow) var(--ease-out) forwards;
  cursor: default;
  transition: box-shadow var(--duration-normal) ease,
              border-color var(--duration-normal) ease,
              transform var(--duration-fast) ease;
}

.metric-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card);
  border-color: var(--border-medium);
}

.metric-top {
  display: flex;
  align-items: center;
  gap: var(--space-12);
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-comfortable);
  color: var(--metric-accent);
  background: var(--metric-accent-soft);
  font-size: 26px;
}

.metric-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 0.02em;
}

.metric-value {
  margin-top: var(--space-16);
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.metric-sub {
  margin: var(--space-8) 0 0;
  font-size: 13px;
  color: var(--text-tertiary);
  line-height: 1.4;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-16);
}

.action-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 0 var(--space-24);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-comfortable);
  background: var(--color-white);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
  transition: transform var(--duration-fast) ease,
              border-color var(--duration-fast) ease,
              background-color var(--duration-fast) ease,
              box-shadow var(--duration-normal) ease;
}

.action-link:hover {
  border-color: var(--color-accent);
  background: rgba(201, 100, 66, 0.04);
  transform: translateY(-2px);
  box-shadow: var(--shadow-card);
  color: var(--color-accent);
}

.overview-panel {
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-featured);
  padding: var(--space-24);
  background: var(--color-white);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-16);
  margin-bottom: var(--space-24);
}

.panel-headline h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.01em;
}

.panel-headline p {
  margin: var(--space-6) 0 0;
  font-size: 13px;
  color: var(--text-secondary);
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
  gap: var(--space-24);
}

.chart-panel {
  padding: var(--space-16);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-comfortable);
  background: var(--surface-100);
  transition: transform var(--duration-normal) ease,
              box-shadow var(--duration-normal) ease,
              border-color var(--duration-normal) ease;
}

.chart-panel:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-card);
  border-color: var(--border-medium);
}

.chart-panel-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-10);
}

.chart-panel-top h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: 0.01em;
}

.level-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.chart-container {
  width: 100%;
  height: 300px;
  margin-top: var(--space-16);
}

@keyframes fadeInUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1280px) {
  .metrics-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quick-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: var(--breakpoint-tablet)) {
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

@media (max-width: var(--breakpoint-mobile)) {
  .metrics-strip,
  .quick-actions {
    grid-template-columns: minmax(0, 1fr);
  }

  .metric-value {
    font-size: 28px;
  }

  .overview-panel {
    padding: var(--space-24);
  }

  .chart-panel {
    padding: var(--space-16);
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
