import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
const service = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 可以在这里添加 token 等认证信息
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
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service

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
  stopCollector: (id) => service.post(`/collection/collectors/${id}/stop`)
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
  cleanup: (days) => service.delete('/collection/logs/cleanup', { params: { days } })
}

// 测试日志相关
export const testLogApi = {
  // 生成测试日志
  generate: (path, content) => service.post('/collection/test/log', null, {
    params: { path, content }
  })
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
  })
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
