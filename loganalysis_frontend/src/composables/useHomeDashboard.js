import { computed, onMounted, reactive, shallowRef } from 'vue'
import { logSourceApi, projectApi, rawLogApi } from '@/api'
import { alertStatisticsApi } from '@/api/alertApi'

const levelColorMap = Object.freeze({
  CRITICAL: '#b53333',
  HIGH: '#c96442',
  MEDIUM: '#b87a2e',
  LOW: '#1f8a65',
  INFO: '#9fbbe0'
})

const levelLabelMap = Object.freeze({
  CRITICAL: '严重',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
  INFO: '信息'
})

const createAreaGradient = () => {
  const graphic = globalThis.echarts?.graphic
  if (!graphic?.LinearGradient) {
    return 'rgba(201, 100, 66, 0.16)'
  }

  return new graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: 'rgba(201, 100, 66, 0.32)' },
    { offset: 1, color: 'rgba(201, 100, 66, 0.04)' }
  ])
}

export const useHomeDashboard = () => {
  const stats = reactive({
    sources: 0,
    logs: 0,
    collecting: 0,
    alerts: 0
  })

  const selectedProjectId = shallowRef('')
  const projects = shallowRef([])

  const trendDays = shallowRef('7')
  const trendLoading = shallowRef(false)
  const levelLoading = shallowRef(false)
  const trendData = shallowRef([])
  const levelData = shallowRef([])

  const getProjectParams = () => (selectedProjectId.value ? { projectId: selectedProjectId.value } : {})

  const trendOption = computed(() => ({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '13%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.26)' } },
      data: trendData.value.map(({ date }) => date)
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.12)' } }
    },
    series: [
      {
        name: '告警数量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        areaStyle: {
          color: createAreaGradient()
        },
        lineStyle: { color: '#c96442', width: 2.5 },
        itemStyle: { color: '#c96442' },
        data: trendData.value.map(({ count }) => count)
      }
    ]
  }))

  const levelHint = computed(() => {
    if (!levelData.value.length) {
      return '暂无级别分布数据'
    }

    const sortedData = [...levelData.value].sort((first, second) => (second.count ?? 0) - (first.count ?? 0))
    const topLevel = sortedData[0]
    const levelName = levelLabelMap[topLevel.level] ?? topLevel.level

    return `${levelName}级告警占比最高`
  })

  const levelOption = computed(() => ({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: '3%',
      top: 'center',
      textStyle: { color: '#5d6d89' }
    },
    series: [
      {
        name: '告警级别',
        type: 'pie',
        radius: ['42%', '70%'],
        center: ['34%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#ffffff',
          borderWidth: 2
        },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' }
        },
        data: levelData.value.map(({ count, level }) => ({
          value: count,
          name: level,
          itemStyle: { color: levelColorMap[level] ?? '#7a879d' }
        }))
      }
    ]
  }))

  const loadStats = async () => {
    try {
      const [sourcesRes, logsRes, alertRes] = await Promise.all([
        logSourceApi.getAll(),
        rawLogApi.getAll({ page: 0, size: 1 }),
        alertStatisticsApi.getStatistics(getProjectParams())
      ])

      const sourceList = sourcesRes.data ?? []

      stats.sources = sourceList.length
      stats.collecting = sourceList.filter(({ status }) => status === 'COLLECTING').length
      stats.logs = logsRes.data?.total ?? 0
      stats.alerts = alertRes.data?.totalAlerts ?? 0
    } catch (error) {
      console.error('加载统计数据失败:', error)
    }
  }

  const loadTrendData = async () => {
    trendLoading.value = true

    try {
      const params = {
        days: Number.parseInt(trendDays.value, 10),
        ...getProjectParams()
      }

      const res = await alertStatisticsApi.getTrend(params)
      trendData.value = Array.isArray(res.data) ? res.data : []
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
      const res = await alertStatisticsApi.getLevelDistribution(getProjectParams())
      levelData.value = Array.isArray(res.data) ? res.data : []
    } catch (error) {
      console.error('加载告警级别分布失败:', error)
      levelData.value = []
    } finally {
      levelLoading.value = false
    }
  }

  const loadProjects = async () => {
    try {
      const res = await projectApi.getAll()
      projects.value = res.data ?? []
    } catch (error) {
      console.error('加载项目列表失败:', error)
      projects.value = []
    }
  }

  const refreshDashboard = async () => {
    await Promise.all([loadStats(), loadTrendData(), loadLevelData()])
  }

  const handleProjectChange = () => {
    void refreshDashboard()
  }

  onMounted(async () => {
    await Promise.all([loadProjects(), refreshDashboard()])
  })

  return {
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
  }
}
