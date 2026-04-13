import dayjs from 'dayjs'
import { computed, onMounted, reactive, shallowRef } from 'vue'
import { esLogApi, logSourceApi, projectApi, rawLogApi } from '@/api'
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

const logLevelColorMap = Object.freeze({
  FATAL: '#8f2d56',
  ERROR: '#b53333',
  WARN: '#c96442',
  WARNING: '#c96442',
  INFO: '#1f8a65',
  DEBUG: '#3f6fb0',
  TRACE: '#7a879d'
})

const logLevelLabelMap = Object.freeze({
  FATAL: '致命',
  ERROR: '错误',
  WARN: '警告',
  WARNING: '警告',
  INFO: '信息',
  DEBUG: '调试',
  TRACE: '追踪'
})

const sourceLinePalette = Object.freeze([
  '#1f8a65',
  '#c96442',
  '#3f6fb0',
  '#b87a2e',
  '#6f58a8',
  '#2f7d96'
])

const MAX_LOG_SOURCE_SERIES = 6

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

const createLogTrendGradient = () => {
  const graphic = globalThis.echarts?.graphic
  if (!graphic?.LinearGradient) {
    return 'rgba(31, 138, 101, 0.14)'
  }

  return new graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: 'rgba(31, 138, 101, 0.24)' },
    { offset: 1, color: 'rgba(31, 138, 101, 0.02)' }
  ])
}

const EMPTY_LOG_TREND = Object.freeze(
  Array.from({ length: 24 }, (_, index) => ({
    label: `${String(index).padStart(2, '0')}:00`,
    count: 0
  }))
)

const toIsoSecondString = (target) => dayjs(target).format('YYYY-MM-DDTHH:mm:ss')
const normalizeLogLevel = (value) => String(value ?? '').trim().toUpperCase()

const toBucketKey = (value) => dayjs(value).format('YYYY-MM-DD HH:mm')

const getRecent24hWindow = () => {
  const nowHour = dayjs().startOf('hour')
  return {
    start: nowHour.subtract(23, 'hour'),
    end: nowHour.endOf('hour')
  }
}

const getHalfHourWindow = (rangeHours) => {
  const nowMinute = dayjs().startOf('minute')
  const alignedEnd = nowMinute.minute(Math.floor(nowMinute.minute() / 30) * 30).second(0).millisecond(0)
  const nodeCount = rangeHours * 2
  const start = alignedEnd.subtract((nodeCount - 1) * 30, 'minute')

  return {
    start,
    end: alignedEnd.endOf('minute'),
    labels: Array.from({ length: nodeCount }, (_, index) => start.add(index * 30, 'minute').format('HH:mm')),
    keys: Array.from({ length: nodeCount }, (_, index) => toBucketKey(start.add(index * 30, 'minute')))
  }
}

const aggregateMinuteHistogramToHalfHour = (buckets) => {
  const result = new Map()

  buckets
    .filter((item) => typeof item?.key === 'string')
    .forEach((item) => {
      const time = dayjs(String(item.key).replace(' ', 'T')).startOf('minute')
      const flooredMinute = Math.floor(time.minute() / 30) * 30
      const normalized = time.minute(flooredMinute).second(0).millisecond(0)
      const key = toBucketKey(normalized)
      result.set(key, (result.get(key) ?? 0) + Number(item.docCount ?? 0))
    })

  return result
}

const extractLevelBuckets = (response) => {
  const buckets = Array.isArray(response?.data?.aggregations?.field_agg)
    ? response.data.aggregations.field_agg
    : []

  return buckets
    .filter((item) => typeof item?.key === 'string')
    .map((item) => ({
      level: normalizeLogLevel(item.key),
      count: Number(item.docCount ?? 0)
    }))
    .filter((item) => item.level && item.count > 0)
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
  const sourceList = shallowRef([])

  const overviewMode = shallowRef('alert')

  const trendDays = shallowRef('7')
  const trendLoading = shallowRef(false)
  const levelLoading = shallowRef(false)
  const trendData = shallowRef([])
  const levelData = shallowRef([])

  const logIngestionTrendLoading = shallowRef(false)
  const logIngestionHourlyTrend = shallowRef([...EMPTY_LOG_TREND])

  const logTrendRange = shallowRef('24')
  const logOverviewTrendLoading = shallowRef(false)
  const logOverviewLevelLoading = shallowRef(false)
  const logOverviewTimeLabels = shallowRef([])
  const logOverviewTrendSeries = shallowRef([])
  const logOverviewLevelData = shallowRef([])

  const logLevelSourceId = shallowRef('')

  const logOverviewSourceMeta = reactive({
    total: 0,
    shown: 0
  })

  const getProjectParams = () => (selectedProjectId.value ? { projectId: selectedProjectId.value } : {})
  const collectingStatuses = Object.freeze(new Set(['RUNNING', 'COLLECTING']))

  const isCollectingSource = (source) => {
    const normalizedStatus = String(source?.status ?? '').trim().toUpperCase()
    return source?.running === true || collectingStatuses.has(normalizedStatus)
  }

  const filteredSources = computed(() => {
    if (!selectedProjectId.value) {
      return sourceList.value
    }
    return sourceList.value.filter((item) => item.projectId === selectedProjectId.value)
  })

  const logLevelSourceOptions = computed(() => [
    { id: '', name: '全部日志源' },
    ...filteredSources.value.map((item) => ({ id: item.id, name: item.name }))
  ])

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

  const alertLevelHint = computed(() => {
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

  const logIngestionTrendOption = computed(() => ({
    grid: {
      left: 6,
      right: 6,
      top: 8,
      bottom: 6
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      formatter: (params) => {
        const point = params?.[0]
        if (!point) {
          return ''
        }
        return `${point.axisValue}<br/>入库: ${Number(point.data ?? 0).toLocaleString('zh-CN')}`
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: true, lineStyle: { color: 'rgba(54, 80, 120, 0.05)' } },
      data: logIngestionHourlyTrend.value.map(({ label }) => label)
    },
    yAxis: {
      type: 'value',
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: true, lineStyle: { color: 'rgba(54, 80, 120, 0.07)' } },
      minInterval: 1
    },
    series: [
      {
        name: '每小时入库',
        type: 'line',
        smooth: 0.25,
        symbol: 'circle',
        showSymbol: false,
        symbolSize: 4,
        lineStyle: { color: '#1f8a65', width: 2.2 },
        itemStyle: { color: '#1f8a65' },
        areaStyle: { color: createLogTrendGradient() },
        data: logIngestionHourlyTrend.value.map(({ count }) => count)
      }
    ]
  }))

  const logOverviewTrendHint = computed(() => {
    const rangeHours = Number(logTrendRange.value) || 24

    if (logOverviewSourceMeta.total === 0) {
      return `近${rangeHours}小时暂无可展示日志源`
    }

    if (logOverviewSourceMeta.total > logOverviewSourceMeta.shown) {
      return `展示 ${logOverviewSourceMeta.shown}/${logOverviewSourceMeta.total} 个日志源`
    }

    return `${logOverviewSourceMeta.total} 个日志源`
  })

  const logOverviewTrendOption = computed(() => ({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' }
    },
    legend: {
      type: 'scroll',
      top: 0,
      textStyle: { color: '#5d6d89' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '18%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.26)' } },
      axisLabel: {
        color: '#5d6d89',
        interval: 'auto'
      },
      data: logOverviewTimeLabels.value
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.12)' } }
    },
    series: logOverviewTrendSeries.value.map((item, index) => ({
      name: item.name,
      type: 'line',
      smooth: 0.28,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2.2, color: item.color ?? sourceLinePalette[index % sourceLinePalette.length] },
      itemStyle: { color: item.color ?? sourceLinePalette[index % sourceLinePalette.length] },
      data: item.data
    }))
  }))

  const logOverviewLevelHint = computed(() => {
    if (!logOverviewLevelData.value.length) {
      return '暂无日志级别数据'
    }

    const topLevel = [...logOverviewLevelData.value].sort((a, b) => (b.value ?? 0) - (a.value ?? 0))[0]
    const levelName = logLevelLabelMap[topLevel.name] ?? topLevel.name
    return `${levelName}级日志占比最高`
  })

  const logOverviewLevelOption = computed(() => ({
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
        name: '日志级别',
        type: 'pie',
        radius: ['42%', '70%'],
        center: ['34%', '50%'],
        minAngle: 2,
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
        data: logOverviewLevelData.value
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

      const allSources = Array.isArray(sourcesRes.data) ? sourcesRes.data : []
      sourceList.value = allSources

      stats.sources = allSources.length
      stats.collecting = allSources.filter(isCollectingSource).length
      stats.logs = logsRes.data?.total ?? 0
      stats.alerts = alertRes.data?.totalAlerts ?? 0
    } catch (error) {
      console.error('加载统计数据失败:', error)
    }
  }

  const loadLogIngestionTrend = async () => {
    logIngestionTrendLoading.value = true

    try {
      const { start, end } = getRecent24hWindow()
      const res = await esLogApi.search({
        startTime: toIsoSecondString(start),
        endTime: toIsoSecondString(end),
        timeInterval: 'hour',
        page: 0,
        size: 0,
        highlight: false
      })

      const buckets = Array.isArray(res.data?.aggregations?.time_histogram)
        ? res.data.aggregations.time_histogram
        : []
      const bucketMap = new Map(
        buckets
          .filter((item) => typeof item?.key === 'string')
          .map((item) => [toBucketKey(dayjs(String(item.key).replace(' ', 'T'))), Number(item.docCount ?? 0)])
      )

      logIngestionHourlyTrend.value = Array.from({ length: 24 }, (_, index) => {
        const hour = start.add(index, 'hour')
        const key = toBucketKey(hour)
        return {
          label: hour.format('HH:mm'),
          count: bucketMap.get(key) ?? 0
        }
      })
    } catch (error) {
      console.error('加载日志入库趋势失败:', error)
      logIngestionHourlyTrend.value = [...EMPTY_LOG_TREND]
    } finally {
      logIngestionTrendLoading.value = false
    }
  }

  const loadLogOverviewTrendData = async () => {
    logOverviewTrendLoading.value = true

    try {
      const activeSources = filteredSources.value
      logOverviewSourceMeta.total = activeSources.length

      const rangeHours = Number(logTrendRange.value) || 24
      const window = getHalfHourWindow(rangeHours)
      logOverviewTimeLabels.value = window.labels

      if (!activeSources.length) {
        logOverviewSourceMeta.shown = 0
        logOverviewTrendSeries.value = []
        return
      }

      const results = await Promise.all(
        activeSources.map(async (source) => {
          try {
            const res = await esLogApi.search({
              sourceId: source.id,
              startTime: toIsoSecondString(window.start),
              endTime: toIsoSecondString(window.end),
              timeInterval: 'minute',
              page: 0,
              size: 0,
              highlight: false
            })

            const histogramBuckets = Array.isArray(res.data?.aggregations?.time_histogram)
              ? res.data.aggregations.time_histogram
              : []
            const aggregated = aggregateMinuteHistogramToHalfHour(histogramBuckets)
            const counts = window.keys.map((key) => aggregated.get(key) ?? 0)
            const total = counts.reduce((sum, value) => sum + value, 0)

            return {
              name: source.name || source.id,
              counts,
              total
            }
          } catch (error) {
            console.error(`加载日志源趋势失败: ${source?.name ?? source?.id ?? '-'}`, error)
            return null
          }
        })
      )

      const validResults = results.filter(Boolean)
      const sortedByTotal = [...validResults].sort((a, b) => b.total - a.total)
      const displayed = sortedByTotal.slice(0, MAX_LOG_SOURCE_SERIES)

      logOverviewSourceMeta.shown = displayed.length
      logOverviewTrendSeries.value = displayed.map((item, index) => ({
        name: item.name,
        color: sourceLinePalette[index % sourceLinePalette.length],
        data: item.counts
      }))
    } catch (error) {
      console.error('加载日志概览趋势失败:', error)
      logOverviewSourceMeta.shown = 0
      logOverviewTrendSeries.value = []
    } finally {
      logOverviewTrendLoading.value = false
    }
  }

  const loadLogOverviewLevelData = async () => {
    logOverviewLevelLoading.value = true

    try {
      let levelItems = []

      if (logLevelSourceId.value) {
        const res = await esLogApi.search({
          sourceId: logLevelSourceId.value,
          aggregationField: 'logLevel',
          page: 0,
          size: 0,
          highlight: false
        })
        levelItems = extractLevelBuckets(res)
      } else if (selectedProjectId.value) {
        const activeSources = filteredSources.value
        if (!activeSources.length) {
          levelItems = []
        } else {
          const levelResults = await Promise.all(
            activeSources.map(async (source) => {
              try {
                const res = await esLogApi.search({
                  sourceId: source.id,
                  aggregationField: 'logLevel',
                  page: 0,
                  size: 0,
                  highlight: false
                })
                return extractLevelBuckets(res)
              } catch (error) {
                console.error(`加载日志源级别分布失败: ${source?.name ?? source?.id ?? '-'}`, error)
                return []
              }
            })
          )
          levelItems = levelResults.flat()
        }
      } else {
        const res = await esLogApi.search({
          aggregationField: 'logLevel',
          page: 0,
          size: 0,
          highlight: false
        })
        levelItems = extractLevelBuckets(res)
      }

      const levelCounter = new Map()
      levelItems.forEach((item) => {
        levelCounter.set(item.level, (levelCounter.get(item.level) ?? 0) + item.count)
      })

      logOverviewLevelData.value = [...levelCounter.entries()]
        .sort((a, b) => b[1] - a[1])
        .map(([level, count]) => ({
          name: level,
          value: count,
          itemStyle: { color: logLevelColorMap[level] ?? '#7a879d' }
        }))
    } catch (error) {
      console.error('加载日志概览级别分布失败:', error)
      logOverviewLevelData.value = []
    } finally {
      logOverviewLevelLoading.value = false
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
    await Promise.all([loadStats(), loadTrendData(), loadLevelData(), loadLogIngestionTrend()])

    if (overviewMode.value === 'log') {
      await Promise.all([loadLogOverviewTrendData(), loadLogOverviewLevelData()])
    }
  }

  const handleProjectChange = () => {
    if (logLevelSourceId.value && !filteredSources.value.some((item) => item.id === logLevelSourceId.value)) {
      logLevelSourceId.value = ''
    }
    void refreshDashboard()
  }

  const handleOverviewModeChange = async () => {
    if (overviewMode.value === 'log') {
      await Promise.all([loadLogOverviewTrendData(), loadLogOverviewLevelData()])
    }
  }

  const handleLogTrendRangeChange = async () => {
    if (overviewMode.value === 'log') {
      await loadLogOverviewTrendData()
    }
  }

  const handleLogLevelSourceChange = async () => {
    if (overviewMode.value === 'log') {
      await loadLogOverviewLevelData()
    }
  }

  onMounted(async () => {
    await Promise.all([loadProjects(), refreshDashboard()])
  })

  return {
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
  }
}
