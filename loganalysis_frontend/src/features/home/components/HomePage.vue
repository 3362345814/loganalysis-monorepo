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
          <h2>运行概览</h2>
          <p>聚焦吞吐、异常、日志源健康度与链路时延分位，支持按项目筛选。</p>
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

      <div class="panel-grid panel-grid-4">
        <article class="chart-panel">
          <div class="chart-panel-top">
            <h3>日志吞吐与估算带宽</h3>
            <span class="level-hint">近24小时，30分钟粒度</span>
          </div>
          <div class="chart-container" v-loading="trafficLoading">
            <v-chart :option="trafficOption" autoresize />
          </div>
        </article>

        <article class="chart-panel">
          <div class="chart-panel-top">
            <h3>异常率趋势</h3>
            <span class="level-hint">WARN/ERROR/FATAL 占比</span>
          </div>
          <div class="chart-container" v-loading="anomalyLoading">
            <v-chart :option="anomalyOption" autoresize />
          </div>
        </article>

        <article class="chart-panel">
          <div class="chart-panel-top">
            <h3>日志源健康度 Top 8</h3>
            <span class="level-hint">综合异常率、时效、日志量</span>
          </div>
          <div class="chart-container" v-loading="healthLoading">
            <v-chart :option="sourceHealthOption" autoresize />
          </div>
        </article>

        <article class="chart-panel">
          <div class="chart-panel-top">
            <h3>链路追踪分布</h3>
            <span class="level-hint">近30天按日分位（P50 / P95 / P99）</span>
          </div>
          <div class="chart-container" v-loading="traceLoading">
            <v-chart :option="traceDistributionOption" autoresize />
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
  logIngestionTrendLoading,
  logIngestionTrendOption,
  trafficLoading,
  trafficOption,
  anomalyLoading,
  anomalyOption,
  healthLoading,
  sourceHealthOption,
  traceLoading,
  traceDistributionOption,
  handleProjectChange
} = useHomeDashboard()

const metricCards = computed(() => [
  {
    key: 'sources',
    label: '采集源',
    value: stats.sources,
    description: '当前筛选范围内的数据源总数',
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
    description: '当前筛选范围内实时采集的源数量',
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
