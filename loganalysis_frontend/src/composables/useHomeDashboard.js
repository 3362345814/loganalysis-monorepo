import dayjs from 'dayjs'
import { computed, onMounted, reactive, shallowRef } from 'vue'
import { esLogApi, logSourceApi, projectApi, rawLogApi } from '@/api'
import { alertStatisticsApi } from '@/api/alertApi'

const SOURCE_COLORS = Object.freeze(['#1f8a65', '#c96442', '#3f6fb0', '#b87a2e', '#6f58a8', '#2f7d96'])
const MAX_HEALTH_SOURCES = 8
const ABNORMAL_LEVELS = Object.freeze(['WARN', 'WARNING', 'ERROR', 'FATAL'])

const createMiniAreaGradient = () => {
  const graphic = globalThis.echarts?.graphic
  if (!graphic?.LinearGradient) {
    return 'rgba(31, 138, 101, 0.14)'
  }

  return new graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: 'rgba(31, 138, 101, 0.24)' },
    { offset: 1, color: 'rgba(31, 138, 101, 0.02)' }
  ])
}

const toIsoSecondString = (target) => dayjs(target).format('YYYY-MM-DDTHH:mm:ss')
const toBucketKey = (value) => dayjs(value).format('YYYY-MM-DD HH:mm')

const estimateByteSize = (text) => {
  if (!text) {
    return 0
  }

  try {
    return new TextEncoder().encode(text).length
  } catch {
    return String(text).length
  }
}

const getLast24HoursWindow = () => {
  const now = dayjs().startOf('minute')
  const alignedEnd = now.minute(Math.floor(now.minute() / 30) * 30).second(0).millisecond(0)
  const points = 48
  const start = alignedEnd.subtract((points - 1) * 30, 'minute')

  return {
    start,
    end: now.endOf('minute'),
    labels: Array.from({ length: points }, (_, index) => start.add(index * 30, 'minute').format('HH:mm')),
    keys: Array.from({ length: points }, (_, index) => toBucketKey(start.add(index * 30, 'minute')))
  }
}

const getMiniWindow = () => {
  const nowHour = dayjs().startOf('hour')
  const start = nowHour.subtract(23, 'hour')

  return {
    start,
    end: nowHour.endOf('hour'),
    labels: Array.from({ length: 24 }, (_, index) => start.add(index, 'hour').format('HH:mm')),
    keys: Array.from({ length: 24 }, (_, index) => toBucketKey(start.add(index, 'hour')))
  }
}

const aggregateMinuteBucketsToHalfHour = (buckets) => {
  const map = new Map()

  buckets
    .filter((item) => typeof item?.key === 'string')
    .forEach((item) => {
      const time = dayjs(String(item.key).replace(' ', 'T')).startOf('minute')
      const rounded = time.minute(Math.floor(time.minute() / 30) * 30).second(0).millisecond(0)
      const key = toBucketKey(rounded)
      map.set(key, (map.get(key) ?? 0) + Number(item.docCount ?? 0))
    })

  return map
}

const toBucketMap = (buckets) => {
  const map = new Map()

  buckets
    .filter((item) => typeof item?.key === 'string')
    .forEach((item) => {
      map.set(toBucketKey(dayjs(String(item.key).replace(' ', 'T'))), Number(item.docCount ?? 0))
    })

  return map
}

const avg = (numbers) => {
  if (!numbers.length) {
    return null
  }
  return numbers.reduce((sum, item) => sum + item, 0) / numbers.length
}

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))
const TRACE_DISTRIBUTION_DAYS = 30

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

  const logIngestionTrendLoading = shallowRef(false)
  const miniTrend = shallowRef(
    Array.from({ length: 24 }, (_, index) => ({
      label: `${String(index).padStart(2, '0')}:00`,
      count: 0
    }))
  )

  const trafficLoading = shallowRef(false)
  const anomalyLoading = shallowRef(false)
  const healthLoading = shallowRef(false)
  const traceLoading = shallowRef(false)

  const trafficLabels = shallowRef([])
  const trafficLogs = shallowRef([])
  const trafficBandwidth = shallowRef([])

  const anomalyLabels = shallowRef([])
  const anomalyCounts = shallowRef([])
  const anomalyRates = shallowRef([])

  const healthRows = shallowRef([])

  const traceLabels = shallowRef([])
  const traceP50 = shallowRef([])
  const traceP95 = shallowRef([])
  const traceP99 = shallowRef([])
  const traceSampleCount = shallowRef([])

  const collectingStatuses = Object.freeze(new Set(['RUNNING', 'COLLECTING']))

  const isCollectingSource = (source) => {
    const status = String(source?.status ?? '').trim().toUpperCase()
    return source?.running === true || collectingStatuses.has(status)
  }

  const filteredSources = computed(() => {
    if (!selectedProjectId.value) {
      return sourceList.value
    }

    return sourceList.value.filter((item) => item.projectId === selectedProjectId.value)
  })

  const logIngestionTrendOption = computed(() => ({
    grid: { left: 6, right: 6, top: 8, bottom: 6 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      formatter: (params) => {
        const point = params?.[0]
        if (!point) return ''
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
      data: miniTrend.value.map((item) => item.label)
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
        showSymbol: false,
        symbolSize: 4,
        lineStyle: { color: '#1f8a65', width: 2.2 },
        itemStyle: { color: '#1f8a65' },
        areaStyle: { color: createMiniAreaGradient() },
        data: miniTrend.value.map((item) => item.count)
      }
    ]
  }))

  const trafficOption = computed(() => ({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#5d6d89' } },
    grid: { left: '4%', right: '4%', top: '16%', bottom: '5%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.24)' } },
      data: trafficLabels.value
    },
    yAxis: [
      {
        type: 'value',
        name: '条数',
        minInterval: 1,
        splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.10)' } }
      },
      {
        type: 'value',
        name: 'MB/30m',
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '日志吞吐',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        showSymbol: false,
        lineStyle: { width: 2.4, color: '#1f8a65' },
        itemStyle: { color: '#1f8a65' },
        data: trafficLogs.value
      },
      {
        name: '估算带宽',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: '46%',
        itemStyle: { color: 'rgba(63, 111, 176, 0.62)', borderRadius: [4, 4, 0, 0] },
        data: trafficBandwidth.value
      }
    ]
  }))

  const anomalyOption = computed(() => ({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#5d6d89' } },
    grid: { left: '4%', right: '4%', top: '16%', bottom: '5%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.24)' } },
      data: anomalyLabels.value
    },
    yAxis: [
      {
        type: 'value',
        name: '异常条数',
        minInterval: 1,
        splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.10)' } }
      },
      {
        type: 'value',
        name: '异常率(%)',
        min: 0,
        max: 100,
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '异常条数',
        type: 'bar',
        yAxisIndex: 0,
        barWidth: '42%',
        itemStyle: { color: 'rgba(181, 51, 51, 0.58)', borderRadius: [4, 4, 0, 0] },
        data: anomalyCounts.value
      },
      {
        name: '异常率',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        showSymbol: false,
        lineStyle: { width: 2.4, color: '#c96442' },
        itemStyle: { color: '#c96442' },
        data: anomalyRates.value
      }
    ]
  }))

  const sourceHealthOption = computed(() => ({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const point = params?.[0]
        if (!point) return ''
        const row = healthRows.value[point.dataIndex]
        if (!row) return ''
        return [
          `<strong>${row.name}</strong>`,
          `健康分: ${row.score.toFixed(1)}`,
          `24h日志: ${row.total.toLocaleString('zh-CN')}`,
          `异常率: ${(row.abnormalRate * 100).toFixed(2)}%`,
          `最近日志: ${row.freshnessMins} 分钟前`
        ].join('<br/>')
      }
    },
    grid: { left: '4%', right: '4%', top: '10%', bottom: '5%', containLabel: true },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.10)' } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      axisTick: { show: false },
      data: healthRows.value.map((item) => item.name)
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: healthRows.value.map((item) => ({
          value: Number(item.score.toFixed(1)),
          itemStyle: {
            color: item.score >= 80 ? '#1f8a65' : item.score >= 60 ? '#b87a2e' : '#b53333',
            borderRadius: [0, 6, 6, 0]
          }
        }))
      }
    ]
  }))

  const traceDistributionOption = computed(() => ({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#5d6d89' } },
    grid: { left: '4%', right: '4%', top: '16%', bottom: '5%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.24)' } },
      data: traceLabels.value
    },
    yAxis: {
      type: 'value',
      name: '秒',
      min: 0,
      splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.10)' } }
    },
    series: [
      {
        name: 'P50',
        type: 'line',
        smooth: true,
        showSymbol: false,
        connectNulls: true,
        lineStyle: { width: 2.2, color: '#3f6fb0' },
        itemStyle: { color: '#3f6fb0' },
        data: traceP50.value
      },
      {
        name: 'P95',
        type: 'line',
        smooth: true,
        showSymbol: false,
        connectNulls: true,
        lineStyle: { width: 2.2, color: '#c96442' },
        itemStyle: { color: '#c96442' },
        data: traceP95.value
      },
      {
        name: 'P99',
        type: 'line',
        smooth: true,
        showSymbol: false,
        connectNulls: true,
        lineStyle: { width: 2.2, color: '#b87a2e' },
        itemStyle: { color: '#b87a2e' },
        data: traceP99.value
      }
    ]
  }))

  const fetchSourceOperationalMetrics = async (sourceId, start, end) => {
    const baseParams = {
      sourceId,
      startTime: toIsoSecondString(start),
      endTime: toIsoSecondString(end),
      timeInterval: 'minute',
      page: 0,
      size: 30,
      highlight: false,
      sortField: 'originalLogTime',
      sortOrder: 'desc'
    }

    const [totalRes, abnormalRes] = await Promise.all([
      esLogApi.search(baseParams),
      esLogApi.search({
        ...baseParams,
        logLevels: ABNORMAL_LEVELS
      })
    ])

    const totalBuckets = Array.isArray(totalRes.data?.aggregations?.time_histogram)
      ? totalRes.data.aggregations.time_histogram
      : []
    const abnormalBuckets = Array.isArray(abnormalRes.data?.aggregations?.time_histogram)
      ? abnormalRes.data.aggregations.time_histogram
      : []

    const hits = Array.isArray(totalRes.data?.hits) ? totalRes.data.hits : []
    const avgBytes = hits.length
      ? Math.max(120, avg(hits.map((item) => estimateByteSize(item?.rawContent ?? item?.desensitizedContent ?? ''))) ?? 220)
      : 220

    const latestTime = hits[0]?.originalLogTime ?? hits[0]?.collectionTime ?? null

    return {
      totalMap: aggregateMinuteBucketsToHalfHour(totalBuckets),
      abnormalMap: aggregateMinuteBucketsToHalfHour(abnormalBuckets),
      total: Number(totalRes.data?.total ?? 0),
      abnormalTotal: Number(abnormalRes.data?.total ?? 0),
      avgBytes,
      latestTime
    }
  }

  const loadStats = async () => {
    try {
      const [sourcesRes, alertRes] = await Promise.all([
        logSourceApi.getAll(),
        alertStatisticsApi.getStatistics(selectedProjectId.value ? { projectId: selectedProjectId.value } : {})
      ])

      const allSources = Array.isArray(sourcesRes.data) ? sourcesRes.data : []
      sourceList.value = allSources

      const scopedSources = selectedProjectId.value
        ? allSources.filter((item) => item.projectId === selectedProjectId.value)
        : allSources

      stats.sources = scopedSources.length
      stats.collecting = scopedSources.filter(isCollectingSource).length
      if (!scopedSources.length) {
        stats.logs = 0
      } else {
        const countResults = await Promise.all(
          scopedSources.map((source) => rawLogApi.getCount(source.id))
        )
        stats.logs = countResults.reduce((sum, res) => sum + Number(res.data?.count ?? 0), 0)
      }
      stats.alerts = Number(alertRes.data?.totalAlerts ?? 0)
    } catch (error) {
      console.error('加载统计数据失败:', error)
    }
  }

  const loadMiniTrend = async () => {
    logIngestionTrendLoading.value = true

    try {
      const window = getMiniWindow()

      if (!filteredSources.value.length) {
        miniTrend.value = window.labels.map((label) => ({ label, count: 0 }))
        return
      }

      const results = await Promise.all(
        filteredSources.value.map((source) =>
          esLogApi.search({
            sourceId: source.id,
            startTime: toIsoSecondString(window.start),
            endTime: toIsoSecondString(window.end),
            timeInterval: 'hour',
            page: 0,
            size: 0,
            highlight: false
          })
        )
      )

      const aggregate = new Map()
      results.forEach((res) => {
        const buckets = Array.isArray(res.data?.aggregations?.time_histogram) ? res.data.aggregations.time_histogram : []
        const map = toBucketMap(buckets)
        window.keys.forEach((key) => {
          aggregate.set(key, (aggregate.get(key) ?? 0) + (map.get(key) ?? 0))
        })
      })

      miniTrend.value = window.labels.map((label, index) => ({
        label,
        count: aggregate.get(window.keys[index]) ?? 0
      }))
    } catch (error) {
      console.error('加载日志小趋势失败:', error)
      miniTrend.value = miniTrend.value.map((item) => ({ ...item, count: 0 }))
    } finally {
      logIngestionTrendLoading.value = false
    }
  }

  const loadLogOperationalCharts = async () => {
    trafficLoading.value = true
    anomalyLoading.value = true
    healthLoading.value = true

    try {
      const sources = filteredSources.value
      const window = getLast24HoursWindow()
      trafficLabels.value = window.labels
      anomalyLabels.value = window.labels

      if (!sources.length) {
        trafficLogs.value = window.keys.map(() => 0)
        trafficBandwidth.value = window.keys.map(() => 0)
        anomalyCounts.value = window.keys.map(() => 0)
        anomalyRates.value = window.keys.map(() => 0)
        healthRows.value = []
        return
      }

      const sourceMetrics = await Promise.all(
        sources.map(async (source, index) => {
          const metrics = await fetchSourceOperationalMetrics(source.id, window.start, window.end)
          return {
            ...metrics,
            id: source.id,
            name: source.name || source.id,
            color: SOURCE_COLORS[index % SOURCE_COLORS.length]
          }
        })
      )

      trafficLogs.value = window.keys.map((key) =>
        sourceMetrics.reduce((sum, source) => sum + (source.totalMap.get(key) ?? 0), 0)
      )

      trafficBandwidth.value = window.keys.map((key) => {
        const totalBytes = sourceMetrics.reduce((sum, source) => {
          const count = source.totalMap.get(key) ?? 0
          return sum + count * source.avgBytes
        }, 0)
        return Number((totalBytes / (1024 * 1024)).toFixed(3))
      })

      anomalyCounts.value = window.keys.map((key) =>
        sourceMetrics.reduce((sum, source) => sum + (source.abnormalMap.get(key) ?? 0), 0)
      )

      anomalyRates.value = window.keys.map((key, index) => {
        const total = trafficLogs.value[index] ?? 0
        const abnormal = anomalyCounts.value[index] ?? 0
        return total > 0 ? Number(((abnormal / total) * 100).toFixed(2)) : 0
      })

      const now = dayjs()
      healthRows.value = sourceMetrics
        .map((source) => {
          const abnormalRate = source.total > 0 ? source.abnormalTotal / source.total : 1
          const freshnessMins = source.latestTime
            ? Math.max(0, now.diff(dayjs(source.latestTime), 'minute'))
            : 24 * 60
          const freshnessPenalty = Math.min(30, freshnessMins / 60)
          const volumeBonus = Math.min(10, Math.log10(source.total + 1) * 3)
          const score = clamp(100 - abnormalRate * 100 * 0.9 - freshnessPenalty + volumeBonus, 0, 100)

          return {
            name: source.name,
            score,
            total: source.total,
            abnormalRate,
            freshnessMins
          }
        })
        .sort((a, b) => b.score - a.score)
        .slice(0, MAX_HEALTH_SOURCES)
    } catch (error) {
      console.error('加载日志运营图表失败:', error)
      trafficLogs.value = []
      trafficBandwidth.value = []
      anomalyCounts.value = []
      anomalyRates.value = []
      healthRows.value = []
    } finally {
      trafficLoading.value = false
      anomalyLoading.value = false
      healthLoading.value = false
    }
  }

  const loadTraceDistribution = async () => {
    traceLoading.value = true

    try {
      const params = { days: TRACE_DISTRIBUTION_DAYS }
      if (selectedProjectId.value) {
        params.projectId = selectedProjectId.value
      }

      const res = await esLogApi.getTraceDistribution(params)
      const payload = res.data ?? {}

      const labels = Array.isArray(payload.labels) ? payload.labels : []
      traceLabels.value = labels.map((item) => dayjs(item).isValid() ? dayjs(item).format('MM-DD') : String(item))
      traceP50.value = Array.isArray(payload.p50) ? payload.p50 : []
      traceP95.value = Array.isArray(payload.p95) ? payload.p95 : []
      traceP99.value = Array.isArray(payload.p99) ? payload.p99 : []
      traceSampleCount.value = Array.isArray(payload.sampleCount) ? payload.sampleCount : []
    } catch (error) {
      console.error('加载链路追踪分布失败:', error)
      traceLabels.value = []
      traceP50.value = []
      traceP95.value = []
      traceP99.value = []
      traceSampleCount.value = []
    } finally {
      traceLoading.value = false
    }
  }

  const refreshDashboard = async () => {
    await loadStats()
    await Promise.all([loadMiniTrend(), loadLogOperationalCharts(), loadTraceDistribution()])
  }

  const loadProjects = async () => {
    try {
      const res = await projectApi.getAll()
      projects.value = Array.isArray(res.data) ? res.data : []
    } catch (error) {
      console.error('加载项目列表失败:', error)
      projects.value = []
    }
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
  }
}
