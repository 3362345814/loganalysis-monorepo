import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { clearAccessToken, getAccessToken } from '@/features/auth/token'

// 创建 axios 实例
const service = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  paramsSerializer: (params) => {
    // 正确序列化数组参数，例如 logLevels=ERROR&logLevels=WARN
    const parts = []
    for (const key in params) {
      const value = params[key]
      if (value !== null && value !== undefined) {
        if (Array.isArray(value)) {
          value.forEach(v => {
            if (v instanceof Date) {
              parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(v.toISOString())}`)
            } else {
              parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(v)}`)
            }
          })
        } else if (value instanceof Date) {
          parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value.toISOString())}`)
        } else {
          parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
        }
      }
    }
    return parts.join('&')
  }
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    const token = getAccessToken()
    if (token) {
      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    const res = response.data
    
    // 根据后端返回的 code 判断是否成功
    if (res.code === 0 || res.success) {
      return res
    } else {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  error => {
    console.error('响应错误:', error)
    const status = error?.response?.status
    if (status === 401) {
      clearAccessToken()
      const currentRoute = router.currentRoute.value
      const currentPath = currentRoute?.path || '/'
      const currentFullPath = currentRoute?.fullPath || '/'
      if (currentPath !== '/login') {
        router.replace({
          path: '/login',
          query: { redirect: currentFullPath }
        })
      }
    }
    const message = error?.response?.data?.message || error.message || '网络错误'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service

export const authApi = {
  login: (data) => service.post('/auth/login', data),
  me: () => service.get('/auth/me')
}

// ==================== 日志采集 API ====================

// 项目相关
export const projectApi = {
  // 获取所有项目
  getAll: () => service.get('/projects'),

  // 获取所有启用的项目
  getEnabled: () => service.get('/projects/enabled'),

  // 获取单个项目
  getById: (id) => service.get(`/projects/${id}`),

  // 创建项目
  create: (data) => service.post('/projects', data),

  // 更新项目
  update: (id, data) => service.put(`/projects/${id}`, data),

  // 删除项目
  delete: (id) => service.delete(`/projects/${id}`)
}

// 日志源相关
export const logSourceApi = {
  // 获取所有日志源
  getAll: () => service.get('/collection/sources'),

  // 根据项目ID获取日志源
  getByProjectId: (projectId) => service.get(`/collection/sources/project/${projectId}`),

  // 获取单个日志源
  getById: (id) => service.get(`/collection/sources/${id}`),

  // 创建日志源
  create: (data) => service.post('/collection/sources', data),

  // 更新日志源
  update: (id, data) => service.put(`/collection/sources/${id}`, data),

  // 删除日志源
  delete: (id) => service.delete(`/collection/sources/${id}`),

  // 启动采集器
  startCollector: (id) => service.post(`/collection/collectors/${id}/start`),

  // 停止采集器
  stopCollector: (id) => service.post(`/collection/collectors/${id}/stop`),

  // 测试SSH连接
  testSshConnection: (config) => service.post('/collection/sources/test-ssh', config),

  // 测试日志路径是否存在
  testPathExists: (config) => service.post('/collection/sources/test-path', config)
}

// 原始日志相关
export const rawLogApi = {
  // 根据日志源查询日志
  getBySourceId: (sourceId, params) => service.get(`/collection/logs/${sourceId}`, { params }),

  // 查询所有日志
  getAll: (params) => service.get('/collection/logs', { params }),

  // 根据ID查询日志
  getById: (id) => service.get(`/collection/log/${id}`),

  // 获取日志数量
  getCount: (sourceId) => service.get(`/collection/logs/${sourceId}/count`),

  // 清理日志
  cleanup: (days) => service.delete('/collection/logs/cleanup', { params: { days } }),

  // 根据 traceId 查询日志（用于链路追踪）
  getByTraceId: (traceId, params) => service.get(`/collection/logs/trace/${traceId}`, { params }),

  // 根据 traceId 查询所有日志（不分页，用于链路追踪）
  getAllByTraceId: (traceId) => service.get(`/collection/logs/trace/${traceId}/all`),

  // 统计 traceId 相关日志数量
  countByTraceId: (traceId) => service.get(`/collection/logs/trace/${traceId}/count`)
}

// ==================== 日志处理 API ====================

// 日志解析测试
export const logProcessingApi = {
  // 测试日志解析
  testParse: (content, format) => service.get('/processing/parse/test', {
    params: { content, format }
  }),
  
  // 测试脱敏
  testDesensitize: (content) => service.get('/processing/desensitize/test', {
    params: { content }
  }),
  
  // 获取处理状态
  getStatus: () => service.get('/processing/status')
}

// 聚合组相关
export const aggregationApi = {
  // 获取统计摘要
  getSummary: () => service.get('/processing/aggregation/summary'),
  
  // 获取活跃聚合组
  getActive: () => service.get('/processing/aggregation/active'),
  
  // 获取未分析的聚合组
  getUnanalyzed: () => service.get('/processing/aggregation/unanalyzed'),
  
  // 分页查询聚合组
  getAll: (params) => service.get('/processing/aggregation', { params }),
  
  // 根据ID查询聚合组
  getById: (id) => service.get(`/processing/aggregation/${id}`),
  
  // 根据groupId查询聚合组
  getByGroupId: (groupId) => service.get(`/processing/aggregation/group/${groupId}`),
  
  // 根据日志源查询聚合组
  getBySourceId: (sourceId) => service.get(`/processing/aggregation/source/${sourceId}`),
  
  // 删除聚合组
  delete: (id) => service.delete(`/processing/aggregation/${id}`),
  
  // 清理过期聚合组
  cleanup: (timeoutMinutes) => service.post('/processing/aggregation/cleanup', null, {
    params: { timeoutMinutes }
  }),
  
  // 根据聚合组ID查询组内日志（分页）
  getLogsById: (id, params) => service.get(`/processing/aggregation/${id}/logs`, { params }),
  
  // 根据groupId查询组内日志（分页）
  getLogsByGroupId: (groupId, params) => service.get(`/processing/aggregation/group/${groupId}/logs`, { params }),

  // 获取聚合组上下文（用于AI分析）
  getContext: (id, params) => service.get(`/processing/aggregation/${id}/context`, { params })
}

// ==================== AI 分析 API ====================

// AI 分析相关
export const analysisApi = {
  // 触发分析
  trigger: (data) => service.post('/analysis', data),
  
  // 获取分析结果
  getResult: (aggregationId) => service.get(`/analysis/${aggregationId}`),
  
  // 获取所有分析结果
  getAll: () => service.get('/analysis'),
  
  // 获取最近的分析结果
  getRecent: (limit = 10) => service.get('/analysis/recent', { params: { limit } })
}

// ==================== LLM 配置 API ====================

// LLM 配置相关
export const llmConfigApi = {
  // 获取所有配置
  getAll: () => service.get('/llm-config'),

  // 获取启用的配置
  getEnabled: () => service.get('/llm-config/enabled'),

  // 获取默认配置
  getDefault: () => service.get('/llm-config/default'),

  // 获取当前活跃配置
  getActive: () => service.get('/llm-config/active'),

  // 获取单个配置
  getById: (id) => service.get(`/llm-config/${id}`),

  // 创建配置
  create: (data) => service.post('/llm-config', data),

  // 更新配置
  update: (id, data) => service.put(`/llm-config/${id}`, data),

  // 删除配置
  delete: (id) => service.delete(`/llm-config/${id}`),

  // 验证 API Key
  validate: (id) => service.post(`/llm-config/${id}/validate`)
}

// ==================== AI 分析配置 API ====================

// AI 分析配置相关
export const analysisConfigApi = {
  // 获取配置
  get: () => service.get('/analysis-config'),

  // 更新配置
  update: (data) => service.put('/analysis-config', data)
}

// ==================== ES 日志查询 API ====================

// ES 日志查询相关
export const esLogApi = {
  // ES 高级搜索
  search: (params) => service.get('/collection/logs/es/search', { params }),

  // 获取准确的文档总数（使用 count API）
  count: (params) => service.get('/collection/logs/es/count', { params }),

  // 获取聚合统计
  stats: (params) => service.get('/collection/logs/es/stats', { params }),

  // 手动触发索引同步 (按日志源)
  syncBySourceId: (sourceId, params) => service.post(`/collection/logs/es/index/${sourceId}`, null, { params }),

  // 手动触发全量同步
  syncAll: (params) => service.post('/collection/logs/es/sync-all', null, { params }),

  // 获取 ES 索引信息
  getInfo: () => service.get('/collection/logs/es/info'),

  // ES 健康检查
  health: () => service.get('/collection/logs/es/health')
  ,

  // 链路追踪耗时分布（P50/P95/P99）
  getTraceDistribution: (params) => service.get('/collection/logs/es/trace-distribution', { params })
}
