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
        <div class="metric-main" :class="{ 'has-trend': item.showTrend }">
          <div class="metric-content">
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
          </div>
          <div v-if="item.showTrend" class="metric-trend" v-loading="logIngestionTrendLoading">
            <v-chart :option="item.trendOption" autoresize />
          </div>
        </div>
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
          <h2>{{ isLogOverview ? '日志概览' : '告警概览' }}</h2>
          <p>{{ isLogOverview ? '查看近24小时日志入库趋势与级别分布。' : '聚焦近期趋势与级别分布，支持按项目快速筛选。' }}</p>
        </div>

        <div class="panel-controls">
          <el-radio-group v-model="overviewMode" size="small" @change="handleOverviewModeChange">
            <el-radio-button label="alert">告警概览</el-radio-button>
            <el-radio-button label="log">日志概览</el-radio-button>
          </el-radio-group>
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
            <h3>{{ isLogOverview ? '日志入库趋势' : '告警趋势' }}</h3>
            <el-radio-group v-if="!isLogOverview" v-model="trendDays" size="small" @change="loadTrendData">
              <el-radio-button label="7">近7天</el-radio-button>
              <el-radio-button label="14">近14天</el-radio-button>
              <el-radio-button label="30">近30天</el-radio-button>
            </el-radio-group>
            <div v-else class="chart-top-controls">
              <el-radio-group v-model="logTrendRange" size="small" @change="handleLogTrendRangeChange">
                <el-radio-button label="12">12小时</el-radio-button>
                <el-radio-button label="24">24小时</el-radio-button>
              </el-radio-group>
            </div>
          </div>
          <p v-if="isLogOverview" class="chart-hint">{{ logOverviewTrendHint }}</p>
          <div class="chart-container" v-loading="isLogOverview ? logOverviewTrendLoading : trendLoading">
            <v-chart :option="isLogOverview ? logOverviewTrendOption : trendOption" autoresize />
          </div>
        </article>

        <article class="chart-panel">
          <div class="chart-panel-top">
            <h3>{{ isLogOverview ? '日志级别分布' : '告警级别分布' }}</h3>
            <div v-if="isLogOverview" class="chart-top-controls">
              <el-select
                v-model="logLevelSourceId"
                placeholder="全部日志源"
                @change="handleLogLevelSourceChange"
              >
                <el-option
                  v-for="source in logLevelSourceOptions"
                  :key="source.id || 'all'"
                  :label="source.name"
                  :value="source.id"
                />
              </el-select>
            </div>
            <span v-else class="level-hint">{{ alertLevelHint }}</span>
          </div>
          <p v-if="isLogOverview" class="chart-hint">{{ logOverviewLevelHint }}</p>
          <div class="chart-container" v-loading="isLogOverview ? logOverviewLevelLoading : levelLoading">
            <v-chart :option="isLogOverview ? logOverviewLevelOption : levelOption" autoresize />
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
  overviewMode,
  trendDays,
  trendLoading,
  levelLoading,
  trendOption,
  levelOption,
  logIngestionTrendLoading,
  logIngestionTrendOption,
  logTrendRange,
  logOverviewTrendLoading,
  logOverviewLevelLoading,
  logOverviewTrendOption,
  logOverviewLevelOption,
  logLevelSourceId,
  logLevelSourceOptions,
  alertLevelHint,
  logOverviewLevelHint,
  logOverviewTrendHint,
  loadTrendData,
  handleProjectChange,
  handleOverviewModeChange,
  handleLogTrendRangeChange,
  handleLogLevelSourceChange
} = useHomeDashboard()

const isLogOverview = computed(() => overviewMode.value === 'log')

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
    description: '已入库日志条目',
    icon: Document,
    color: 'var(--color-success)',
    softColor: 'rgba(31, 138, 101, 0.12)',
    showTrend: true,
    trendOption: logIngestionTrendOption.value
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

<style scoped src="../styles/home-page.css"></style>
