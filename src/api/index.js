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

// 日志源相关
export const logSourceApi = {
  // 获取所有日志源
  getAll: () => service.get('/collection/sources'),
  
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
