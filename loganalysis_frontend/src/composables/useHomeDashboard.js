import dayjs from 'dayjs'
import { computed, onMounted, reactive, shallowRef } from 'vue'
import { esLogApi, logSourceApi, projectApi, rawLogApi } from '@/api'
import { alertStatisticsApi } from '@/api/alertApi'

const SOURCE_COLORS = Object.freeze(['#1f8a65', '#c96442', '#3f6fb0', '#b87a2e', '#6f58a8', '#2f7d96'])
const MAX_HEALTH_SOURCES = 8
const MAX_ANOMALY_TOPN_SOURCES = 8
const ABNORMAL_LEVELS = Object.freeze(['WARN', 'WARNING', 'ERROR', 'FATAL'])
const ALERT_LEVEL_ORDER = Object.freeze(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'])
const ALERT_LEVEL_LABELS = Object.freeze({
  CRITICAL: 'CRITICAL',
  HIGH: 'HIGH',
  MEDIUM: 'MEDIUM',
  LOW: 'LOW'
})
const ALERT_LEVEL_COLORS = Object.freeze({
  CRITICAL: '#b53333',
  HIGH: '#c96442',
  MEDIUM: '#b87a2e',
  LOW: '#3f6fb0'
})

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
const parseEsUtcKeyToLocal = (value) => {
  if (!value || typeof value !== 'string') {
    return dayjs.invalid()
  }

  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const withSecond = normalized.length === 16 ? `${normalized}:00` : normalized
  const asUtc = new Date(`${withSecond}Z`)

  if (!Number.isNaN(asUtc.getTime())) {
    return dayjs(asUtc)
  }
  return dayjs(normalized)
}

const parseUtcDateTimeStringToLocal = (value) => {
  if (!value || typeof value !== 'string') {
    return dayjs.invalid()
  }
  const withSecond = value.length === 16 ? `${value}:00` : value
  const asUtc = new Date(`${withSecond}Z`)
  if (!Number.isNaN(asUtc.getTime())) {
    return dayjs(asUtc)
  }
  return dayjs(value)
}

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

const getLast30DaysWindow = () => {
  const endDay = dayjs().startOf('day')
  const startDay = endDay.subtract(29, 'day')

  return {
    start: startDay,
    end: dayjs().endOf('minute'),
    labels: Array.from({ length: 30 }, (_, index) => startDay.add(index, 'day').format('MM-DD')),
    keys: Array.from({ length: 30 }, (_, index) => startDay.add(index, 'day').format('YYYY-MM-DD'))
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
      const time = parseEsUtcKeyToLocal(String(item.key)).startOf('minute')
      const rounded = time.minute(Math.floor(time.minute() / 30) * 30).second(0).millisecond(0)
      const key = toBucketKey(rounded)
      map.set(key, (map.get(key) ?? 0) + Number(item.docCount ?? 0))
    })

  return map
}

const aggregateBucketsToDay = (buckets) => {
  const map = new Map()

  buckets
    .filter((item) => typeof item?.key === 'string')
    .forEach((item) => {
      const dayKey = parseEsUtcKeyToLocal(String(item.key)).format('YYYY-MM-DD')
      map.set(dayKey, (map.get(dayKey) ?? 0) + Number(item.docCount ?? 0))
    })

  return map
}

const toBucketMap = (buckets) => {
  const map = new Map()

  buckets
    .filter((item) => typeof item?.key === 'string')
    .forEach((item) => {
      map.set(toBucketKey(parseEsUtcKeyToLocal(String(item.key))), Number(item.docCount ?? 0))
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
const RANGE_24H = '24h'
const RANGE_30D = '30d'
const toNullableNumber = (value) => {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

const formatLatency = (value, unit) => {
  if (value === null || value === undefined) {
    return '-'
  }
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return '-'
  }
  return `${numeric.toFixed(unit === 'ms' ? 2 : 3)} ${unit}`
}

const formatLatencyRange = (min, max, unit) => {
  if (min === null || min === undefined || max === null || max === undefined) {
    return '-'
  }
  const minNum = Number(min)
  const maxNum = Number(max)
  if (!Number.isFinite(minNum) || !Number.isFinite(maxNum)) {
    return '-'
  }
  return `${minNum.toFixed(unit === 'ms' ? 2 : 3)} ~ ${maxNum.toFixed(unit === 'ms' ? 2 : 3)} ${unit}`
}

export const useHomeDashboard = () => {
  const stats = reactive({
    sources: 0,
    logs: 0,
    collecting: 0,
    alerts: 0
  })

  const selectedProjectId = shallowRef('')
  const selectedTrendRange = shallowRef(RANGE_24H)
  const projects = shallowRef([])
  const sourceList = shallowRef([])
  const trendRangeOptions = Object.freeze([
    { label: '24小时', value: RANGE_24H },
    { label: '30天', value: RANGE_30D }
  ])

  const logIngestionTrendLoading = shallowRef(false)
  const miniTrend = shallowRef(
    Array.from({ length: 24 }, (_, index) => ({
      label: `${String(index).padStart(2, '0')}:00`,
      count: 0
    }))
  )

  const trafficLoading = shallowRef(false)
  const anomalyLoading = shallowRef(false)
  const alertTrendLoading = shallowRef(false)
  const alertLevelLoading = shallowRef(false)
  const healthLoading = shallowRef(false)
  const traceLoading = shallowRef(false)

  const trafficLabels = shallowRef([])
  const trafficLogs = shallowRef([])
  const trafficBandwidth = shallowRef([])

  const anomalyLabels = shallowRef([])
  const anomalyCounts = shallowRef([])
  const anomalyRates = shallowRef([])
  const alertTrendLabels = shallowRef([])
  const alertTrendCounts = shallowRef([])
  const alertLevelRows = shallowRef([])

  const healthRows = shallowRef([])
  const sourceAnomalyTopNRows = shallowRef([])
  const sourceQualityScatterRows = shallowRef([])

  const traceLabels = shallowRef([])
  const traceMin = shallowRef([])
  const traceP25 = shallowRef([])
  const traceP50 = shallowRef([])
  const traceP75 = shallowRef([])
  const traceMax = shallowRef([])
  const traceSampleCount = shallowRef([])

  const traceDisplayUnit = computed(() => {
    const values = [
      ...traceMin.value,
      ...traceP25.value,
      ...traceP50.value,
      ...traceP75.value,
      ...traceMax.value
    ]
      .map((item) => toNullableNumber(item))
      .filter((item) => item !== null && item > 0)

    if (!values.length) {
      return 's'
    }

    return Math.max(...values) < 1 ? 'ms' : 's'
  })

  const traceUnitFactor = computed(() => traceDisplayUnit.value === 'ms' ? 1000 : 1)
  const traceP50Display = computed(() => traceP50.value.map((item) => {
    const numeric = toNullableNumber(item)
    return numeric === null ? null : Number((numeric * traceUnitFactor.value).toFixed(3))
  }))
  const traceP25Display = computed(() => traceP25.value.map((item) => {
    const numeric = toNullableNumber(item)
    return numeric === null ? null : Number((numeric * traceUnitFactor.value).toFixed(3))
  }))
  const traceP75Display = computed(() => traceP75.value.map((item) => {
    const numeric = toNullableNumber(item)
    return numeric === null ? null : Number((numeric * traceUnitFactor.value).toFixed(3))
  }))
  const traceMinDisplay = computed(() => traceMin.value.map((item) => {
    const numeric = toNullableNumber(item)
    return numeric === null ? null : Number((numeric * traceUnitFactor.value).toFixed(3))
  }))
  const traceMaxDisplay = computed(() => traceMax.value.map((item) => {
    const numeric = toNullableNumber(item)
    return numeric === null ? null : Number((numeric * traceUnitFactor.value).toFixed(3))
  }))
  const traceBoxplotData = computed(() => traceLabels.value.map((_, index) => {
    const min = traceMinDisplay.value[index]
    const p25 = traceP25Display.value[index]
    const p50 = traceP50Display.value[index]
    const p75 = traceP75Display.value[index]
    const max = traceMaxDisplay.value[index]
    if (min === null || p25 === null || p50 === null || p75 === null || max === null) {
      return {
        value: [0, 0, 0, 0, 0],
        itemStyle: {
          opacity: 0,
          borderWidth: 0
        }
      }
    }

    return [min, p25, p50, p75, max]
  }))

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

  const operationalWindow = computed(() => (
    selectedTrendRange.value === RANGE_30D ? getLast30DaysWindow() : getLast24HoursWindow()
  ))

  const operationalInterval = computed(() => (
    selectedTrendRange.value === RANGE_30D ? 'day' : 'minute'
  ))

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
        name: selectedTrendRange.value === RANGE_30D ? 'MB/天' : 'MB/30m',
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '平均日志吞吐',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        showSymbol: false,
        lineStyle: { width: 2.4, color: '#1f8a65' },
        itemStyle: { color: '#1f8a65' },
        data: trafficLogs.value
      },
      {
        name: '平均估算带宽',
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

  const alertTrendOption = computed(() => ({
    tooltip: { trigger: 'axis' },
    grid: { left: '6%', right: '4%', top: '12%', bottom: '6%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.24)' } },
      data: alertTrendLabels.value
    },
    yAxis: {
      type: 'value',
      name: '告警数',
      nameLocation: 'middle',
      nameGap: 44,
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.10)' } }
    },
    series: [
      {
        name: '告警新增',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2.4, color: '#b53333' },
        itemStyle: { color: '#b53333' },
        areaStyle: { color: 'rgba(181, 51, 51, 0.12)' },
        data: alertTrendCounts.value
      }
    ]
  }))

  const alertLevelDistributionOption = computed(() => {
    const nonZeroRows = alertLevelRows.value.filter((item) => Number(item.count ?? 0) > 0)

    return {
      tooltip: {
        trigger: 'item',
        formatter: (params) => {
          const percent = Number(params.percent ?? 0).toFixed(1)
          return `${params.marker}${params.name}: ${Number(params.value ?? 0).toLocaleString('zh-CN')} (${percent}%)`
        }
      },
      legend: {
        show: nonZeroRows.length > 0,
        orient: 'vertical',
        right: '4%',
        top: 'middle',
        textStyle: { color: '#5d6d89' }
      },
      series: [
        {
          name: '级别分布',
          type: 'pie',
          radius: ['48%', '72%'],
          center: ['38%', '50%'],
          avoidLabelOverlap: true,
          itemStyle: {
            borderColor: '#ffffff',
            borderWidth: 2,
            borderRadius: 4
          },
          label: {
            show: true,
            formatter: '{b}: {d}%',
            color: '#5d6d89',
            fontSize: 12
          },
          labelLine: {
            length: 10,
            length2: 8
          },
          data: nonZeroRows.map((item) => ({
            name: item.label,
            value: item.count,
            itemStyle: {
              color: item.color
            }
          }))
        }
      ]
    }
  })

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
    grid: { left: '6%', right: '12%', top: '10%', bottom: '5%', containLabel: true },
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

  const sourceAnomalyTopNOption = computed(() => ({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const point = params?.[0]
        if (!point) return ''
        const row = sourceAnomalyTopNRows.value[point.dataIndex]
        if (!row) return ''
        return [
          `<strong>${row.name}</strong>`,
          `异常率: ${(row.abnormalRate * 100).toFixed(2)}%`,
          `异常条数: ${row.abnormalTotal.toLocaleString('zh-CN')}`,
          `总日志: ${row.total.toLocaleString('zh-CN')}`
        ].join('<br/>')
      }
    },
    grid: { left: '6%', right: '12%', top: '10%', bottom: '5%', containLabel: true },
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
      data: sourceAnomalyTopNRows.value.map((item) => item.name)
    },
    series: [
      {
        name: '异常率',
        type: 'bar',
        barWidth: 14,
        label: {
          show: true,
          position: 'right',
          color: '#5d6d89',
          formatter: '{c}%'
        },
        data: sourceAnomalyTopNRows.value.map((item) => ({
          value: Number((item.abnormalRate * 100).toFixed(2)),
          itemStyle: {
            color: '#c96442',
            borderRadius: [0, 6, 6, 0]
          }
        }))
      }
    ]
  }))

  const sourceQualityScatterOption = computed(() => ({
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const row = sourceQualityScatterRows.value[params.dataIndex]
        if (!row) return ''
        return [
          `<strong>${row.name}</strong>`,
          `时效延迟: ${row.freshnessSeconds} 秒`,
          `质量分: ${row.qualityScore.toFixed(1)}`,
          `异常率: ${(row.abnormalRate * 100).toFixed(2)}%`,
          `日志总量: ${row.total.toLocaleString('zh-CN')}`
        ].join('<br/>')
      }
    },
    grid: { left: '6%', right: '6%', top: '12%', bottom: '14%', containLabel: true },
    xAxis: {
      type: 'value',
      name: '时效延迟(秒)',
      nameLocation: 'middle',
      nameGap: 28,
      min: 0,
      splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.10)' } }
    },
    yAxis: {
      type: 'value',
      name: '质量分',
      nameLocation: 'middle',
      nameGap: 44,
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.10)' } }
    },
    series: [
      {
        type: 'scatter',
        data: sourceQualityScatterRows.value.map((item) => ({
          value: [item.freshnessSeconds, Number(item.qualityScore.toFixed(1)), item.total],
          name: item.name,
          symbolSize: clamp(Math.sqrt(item.total + 1) * 1.8, 8, 26),
          itemStyle: {
            color: item.qualityScore >= 80 ? '#1f8a65' : item.qualityScore >= 60 ? '#b87a2e' : '#b53333',
            opacity: 0.82
          }
        }))
      }
    ]
  }))

  const traceDistributionOption = computed(() => ({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const lines = []
        const axisLabel = params?.[0]?.axisValueLabel ?? ''
        if (axisLabel) {
          lines.push(axisLabel)
        }

        params?.forEach((item) => {
          if (item.seriesType === 'boxplot') {
            const source = traceBoxplotData.value[item.dataIndex]
            const box = Array.isArray(source) ? source : source?.value
            let [min, q1, median, q3, max] = Array.isArray(box) ? box : []
            if ((traceSampleCount.value[item.dataIndex] ?? 0) <= 0) {
              return
            }
            if ([min, q1, median, q3, max].every((v) => Number.isFinite(v))) {
              const sorted = [min, q1, median, q3, max].sort((a, b) => a - b)
              ;[min, q1, median, q3, max] = sorted
            }
            lines.push(`${item.marker}${item.seriesName}: ${formatLatencyRange(min, max, traceDisplayUnit.value)}`)
            lines.push(`最小值: ${formatLatency(min, traceDisplayUnit.value)}`)
            lines.push(`Q1: ${formatLatency(q1, traceDisplayUnit.value)}`)
            lines.push(`中位数: ${formatLatency(median, traceDisplayUnit.value)}`)
            lines.push(`Q3: ${formatLatency(q3, traceDisplayUnit.value)}`)
            lines.push(`最大值: ${formatLatency(max, traceDisplayUnit.value)}`)
            return
          }
          if (item.seriesName === '样本数') {
            const count = item.data === null || item.data === undefined ? '-' : Number(item.data).toLocaleString('zh-CN')
            lines.push(`${item.marker}${item.seriesName}: ${count}`)
            return
          }
          lines.push(`${item.marker}${item.seriesName}: ${formatLatency(item.data, traceDisplayUnit.value)}`)
        })
        return lines.join('<br/>')
      }
    },
    legend: { top: 0, textStyle: { color: '#5d6d89' } },
    grid: { left: '4%', right: '4%', top: '16%', bottom: '5%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.24)' } },
      data: traceLabels.value
    },
    yAxis: [
      {
        type: 'value',
        name: traceDisplayUnit.value === 'ms' ? '毫秒' : '秒',
        min: 0,
        splitLine: { lineStyle: { color: 'rgba(54, 80, 120, 0.10)' } }
      },
      {
        type: 'value',
        name: '样本数',
        min: 0,
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '链路耗时箱线',
        type: 'boxplot',
        yAxisIndex: 0,
        itemStyle: {
          color: 'rgba(63, 111, 176, 0.22)',
          borderColor: '#3f6fb0'
        },
        emphasis: {
          itemStyle: {
            color: 'rgba(63, 111, 176, 0.3)',
            borderColor: '#2e5488'
          }
        },
        data: traceBoxplotData.value
      },
      {
        name: '样本数',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 14,
        itemStyle: { color: 'rgba(93, 109, 137, 0.35)', borderRadius: [3, 3, 0, 0] },
        data: traceSampleCount.value
      }
    ]
  }))

  const fetchSourceOperationalMetrics = async (sourceId, start, end) => {
    const baseParams = {
      sourceId,
      startTime: toIsoSecondString(start),
      endTime: toIsoSecondString(end),
      timeInterval: operationalInterval.value,
      page: 0,
      size: 30,
      highlight: false,
      sortField: 'originalLogTime',
      sortOrder: 'desc'
    }

    const countBaseParams = {
      sourceId,
      startTime: toIsoSecondString(start),
      endTime: toIsoSecondString(end)
    }

    const [totalRes, abnormalRes, totalCountRes, abnormalCountRes] = await Promise.all([
      esLogApi.search(baseParams),
      esLogApi.search({
        ...baseParams,
        logLevels: ABNORMAL_LEVELS
      }),
      esLogApi.count(countBaseParams),
      esLogApi.count({
        ...countBaseParams,
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

    const latestTime = hits[0]?.originalLogTime ?? null

    return {
      totalMap: selectedTrendRange.value === RANGE_30D
        ? aggregateBucketsToDay(totalBuckets)
        : aggregateMinuteBucketsToHalfHour(totalBuckets),
      abnormalMap: selectedTrendRange.value === RANGE_30D
        ? aggregateBucketsToDay(abnormalBuckets)
        : aggregateMinuteBucketsToHalfHour(abnormalBuckets),
      total: Number(totalCountRes.data?.count ?? 0),
      abnormalTotal: Number(abnormalCountRes.data?.count ?? 0),
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
      const window = operationalWindow.value
      trafficLabels.value = window.labels
      anomalyLabels.value = window.labels

      if (!sources.length) {
        trafficLogs.value = window.keys.map(() => 0)
        trafficBandwidth.value = window.keys.map(() => 0)
        anomalyCounts.value = window.keys.map(() => 0)
        anomalyRates.value = window.keys.map(() => 0)
        healthRows.value = []
        sourceAnomalyTopNRows.value = []
        sourceQualityScatterRows.value = []
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

      const sourceCount = sourceMetrics.length || 1
      const totalLogsByBucket = window.keys.map((key) =>
        sourceMetrics.reduce((sum, source) => sum + (source.totalMap.get(key) ?? 0), 0)
      )

      trafficLogs.value = totalLogsByBucket.map((count) => Number((count / sourceCount).toFixed(2)))

      trafficBandwidth.value = window.keys.map((key) => {
        const totalBytes = sourceMetrics.reduce((sum, source) => {
          const count = source.totalMap.get(key) ?? 0
          return sum + count * source.avgBytes
        }, 0)
        return Number(((totalBytes / (1024 * 1024)) / sourceCount).toFixed(3))
      })

      anomalyCounts.value = window.keys.map((key) =>
        sourceMetrics.reduce((sum, source) => sum + (source.abnormalMap.get(key) ?? 0), 0)
      )

      anomalyRates.value = window.keys.map((key, index) => {
        const total = totalLogsByBucket[index] ?? 0
        const abnormal = anomalyCounts.value[index] ?? 0
        return total > 0 ? Number(((abnormal / total) * 100).toFixed(2)) : 0
      })

      const now = dayjs()
      healthRows.value = sourceMetrics
        .map((source) => {
          const abnormalRate = source.total > 0 ? source.abnormalTotal / source.total : 1
          const freshnessMins = source.latestTime
            ? Math.max(0, now.diff(parseUtcDateTimeStringToLocal(source.latestTime), 'minute'))
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

      sourceAnomalyTopNRows.value = sourceMetrics
        .map((source) => ({
          name: source.name,
          abnormalTotal: source.abnormalTotal,
          total: source.total,
          abnormalRate: source.total > 0 ? source.abnormalTotal / source.total : 0
        }))
        .sort((a, b) => {
          if (b.abnormalRate !== a.abnormalRate) {
            return b.abnormalRate - a.abnormalRate
          }
          return b.abnormalTotal - a.abnormalTotal
        })
        .slice(0, MAX_ANOMALY_TOPN_SOURCES)

      sourceQualityScatterRows.value = sourceMetrics.map((source) => {
        const abnormalRate = source.total > 0 ? source.abnormalTotal / source.total : 0
        const freshnessSeconds = source.latestTime
          ? Math.max(0, dayjs().diff(parseUtcDateTimeStringToLocal(source.latestTime), 'second'))
          : 24 * 60 * 60
        const qualityScore = clamp(100 - abnormalRate * 100, 0, 100)
        return {
          name: source.name,
          freshnessSeconds,
          qualityScore,
          abnormalRate,
          total: source.total
        }
      })
    } catch (error) {
      console.error('加载日志运营图表失败:', error)
      trafficLogs.value = []
      trafficBandwidth.value = []
      anomalyCounts.value = []
      anomalyRates.value = []
      healthRows.value = []
      sourceAnomalyTopNRows.value = []
      sourceQualityScatterRows.value = []
    } finally {
      trafficLoading.value = false
      anomalyLoading.value = false
      healthLoading.value = false
    }
  }

  const loadAlertTrend = async () => {
    alertTrendLoading.value = true

    try {
      const days = selectedTrendRange.value === RANGE_30D ? 30 : 7
      const params = { days }
      if (selectedProjectId.value) {
        params.projectId = selectedProjectId.value
      }

      const res = await alertStatisticsApi.getTrend(params)
      const rows = Array.isArray(res.data) ? res.data : []
      alertTrendLabels.value = rows.map((item) => dayjs(item.date).format('MM-DD'))
      alertTrendCounts.value = rows.map((item) => Number(item.count ?? 0))
    } catch (error) {
      console.error('加载告警趋势失败:', error)
      alertTrendLabels.value = []
      alertTrendCounts.value = []
    } finally {
      alertTrendLoading.value = false
    }
  }

  const loadAlertLevelDistribution = async () => {
    alertLevelLoading.value = true

    try {
      const params = selectedProjectId.value ? { projectId: selectedProjectId.value } : {}
      const res = await alertStatisticsApi.getLevelDistribution(params)
      const rows = Array.isArray(res.data) ? res.data : []
      const map = new Map(
        rows.map((item) => [String(item.level ?? '').toUpperCase(), Number(item.count ?? 0)])
      )
      alertLevelRows.value = ALERT_LEVEL_ORDER.map((level) => ({
        level,
        label: ALERT_LEVEL_LABELS[level],
        count: map.get(level) ?? 0,
        color: ALERT_LEVEL_COLORS[level]
      }))
    } catch (error) {
      console.error('加载告警级别分布失败:', error)
      alertLevelRows.value = ALERT_LEVEL_ORDER.map((level) => ({
        level,
        label: ALERT_LEVEL_LABELS[level],
        count: 0,
        color: ALERT_LEVEL_COLORS[level]
      }))
    } finally {
      alertLevelLoading.value = false
    }
  }

  const loadTraceDistribution = async () => {
    traceLoading.value = true

    try {
      const window = operationalWindow.value
      const params = {
        days: selectedTrendRange.value === RANGE_30D ? TRACE_DISTRIBUTION_DAYS : 1,
        interval: selectedTrendRange.value === RANGE_30D ? 'DAY' : 'HALF_HOUR'
      }
      if (selectedProjectId.value) {
        params.projectId = selectedProjectId.value
      }

      const res = await esLogApi.getTraceDistribution(params)
      const payload = res.data ?? {}
      const rawLabels = Array.isArray(payload.labels) ? payload.labels : []
      const rawMin = Array.isArray(payload.min) ? payload.min : []
      const rawP25 = Array.isArray(payload.p25) ? payload.p25 : []
      const rawP50 = Array.isArray(payload.p50) ? payload.p50 : []
      const rawP75 = Array.isArray(payload.p75) ? payload.p75 : []
      const rawMax = Array.isArray(payload.max) ? payload.max : []
      const rawSample = Array.isArray(payload.sampleCount) ? payload.sampleCount : []

      // 横轴标签与吞吐/异常图统一：24h 使用 HH:mm，30d 使用 MM-DD
      traceLabels.value = window.labels

      const minMap = new Map()
      const p25Map = new Map()
      const p50Map = new Map()
      const p75Map = new Map()
      const maxMap = new Map()
      const sampleMap = new Map()

      rawLabels.forEach((label, idx) => {
        const local = parseEsUtcKeyToLocal(String(label))
        if (!local.isValid()) return
        const key = selectedTrendRange.value === RANGE_30D
          ? local.format('YYYY-MM-DD')
          : toBucketKey(local.startOf('minute'))

        minMap.set(key, toNullableNumber(rawMin[idx]))
        p25Map.set(key, toNullableNumber(rawP25[idx]))
        p50Map.set(key, toNullableNumber(rawP50[idx]))
        p75Map.set(key, toNullableNumber(rawP75[idx]))
        maxMap.set(key, toNullableNumber(rawMax[idx]))
        sampleMap.set(key, Number(rawSample[idx] ?? 0))
      })

      traceMin.value = window.keys.map((k) => minMap.has(k) ? minMap.get(k) : null)
      traceP25.value = window.keys.map((k) => p25Map.has(k) ? p25Map.get(k) : null)
      traceP50.value = window.keys.map((k) => p50Map.has(k) ? p50Map.get(k) : null)
      traceP75.value = window.keys.map((k) => p75Map.has(k) ? p75Map.get(k) : null)
      traceMax.value = window.keys.map((k) => maxMap.has(k) ? maxMap.get(k) : null)
      traceSampleCount.value = window.keys.map((k) => sampleMap.get(k) ?? 0)
    } catch (error) {
      console.error('加载链路追踪分布失败:', error)
      traceLabels.value = []
      traceMin.value = []
      traceP25.value = []
      traceP50.value = []
      traceP75.value = []
      traceMax.value = []
      traceSampleCount.value = []
    } finally {
      traceLoading.value = false
    }
  }

  const refreshDashboard = async () => {
    await loadStats()
    await Promise.all([
      loadMiniTrend(),
      loadLogOperationalCharts(),
      loadAlertTrend(),
      loadAlertLevelDistribution(),
      loadTraceDistribution()
    ])
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

  const handleTrendRangeChange = () => {
    void Promise.all([loadLogOperationalCharts(), loadAlertTrend(), loadAlertLevelDistribution(), loadTraceDistribution()])
  }

  onMounted(async () => {
    await Promise.all([loadProjects(), refreshDashboard()])
  })

  return {
    stats,
    selectedProjectId,
    selectedTrendRange,
    trendRangeOptions,
    projects,
    logIngestionTrendLoading,
    logIngestionTrendOption,
    trafficLoading,
    trafficOption,
    anomalyLoading,
    anomalyOption,
    alertTrendLoading,
    alertTrendOption,
    alertLevelLoading,
    alertLevelDistributionOption,
    healthLoading,
    sourceHealthOption,
    sourceAnomalyTopNOption,
    sourceQualityScatterOption,
    traceLoading,
    traceDistributionOption,
    handleTrendRangeChange,
    handleProjectChange
  }
}
