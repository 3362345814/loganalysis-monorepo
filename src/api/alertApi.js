import service from './index'

// ==================== 告警管理 API ====================

// 告警规则 API
export const alertRuleApi = {
  // 获取所有规则
  getAll: () => service.get('/alert/rules'),

  // 分页查询规则
  getPage: (params) => service.get('/alert/rules/page', { params }),

  // 获取规则详情
  getById: (id) => service.get(`/alert/rules/${id}`),

  // 创建规则
  create: (data) => service.post('/alert/rules', data),

  // 更新规则
  update: (id, data) => service.put(`/alert/rules/${id}`, data),

  // 删除规则
  delete: (id) => service.delete(`/alert/rules/${id}`),

  // 启用/禁用规则
  toggle: (id) => service.put(`/alert/rules/${id}/toggle`),

  // 根据类型查询
  getByType: (ruleType) => service.get(`/alert/rules/type/${ruleType}`),

  // 根据级别查询
  getByLevel: (alertLevel) => service.get(`/alert/rules/level/${alertLevel}`)
}

// 告警记录 API
export const alertRecordApi = {
  // 获取所有告警（分页）
  getAll: (params) => service.get('/alert/records', { params }),

  // 获取告警详情
  getById: (id) => service.get(`/alert/records/${id}`),

  // 根据告警编号获取
  getByAlertId: (alertId) => service.get(`/alert/records/no/${alertId}`),

  // 获取待处理告警
  getPending: () => service.get('/alert/records/pending'),

  // 获取未解决告警
  getUnresolved: () => service.get('/alert/records/unresolved'),

  // 根据状态查询
  getByStatus: (status, params) => service.get(`/alert/records/status/${status}`, { params }),

  // 根据级别查询
  getByLevel: (level, params) => service.get(`/alert/records/level/${level}`, { params }),

  // 复合条件查询
  query: (params) => service.get('/alert/records/query', { params }),

  // 确认告警
  acknowledge: (id, params) => service.put(`/alert/records/${id}/acknowledge`, null, { params }),

  // 解决告警
  resolve: (id, params) => service.put(`/alert/records/${id}/resolve`, null, { params }),

  // 分配告警
  assign: (id, params) => service.put(`/alert/records/${id}/assign`, null, { params }),

  // 升级告警
  escalate: (id) => service.put(`/alert/records/${id}/escalate`)
}

// 通知渠道配置 API
export const notificationChannelApi = {
  // 获取所有渠道配置
  getAll: () => service.get('/alert/channel-configs'),

  // 获取已启用的渠道配置
  getEnabled: () => service.get('/alert/channel-configs/enabled'),

  // 保存渠道配置
  save: (data) => service.post('/alert/channel-configs', data),

  // 批量保存渠道配置
  batchSave: (data) => service.post('/alert/channel-configs/batch', data),

  // 删除渠道配置
  delete: (id) => service.delete(`/alert/channel-configs/${id}`)
}

// 告警统计 API
export const alertStatisticsApi = {
  // 获取统计数据
  getStatistics: () => service.get('/alert/statistics'),

  // 获取趋势数据
  getTrend: (params) => service.get('/alert/statistics/trend', { params }),

  // 获取今日统计
  getToday: () => service.get('/alert/statistics/today'),

  // 按级别统计
  getByLevel: () => service.get('/alert/statistics/by-level'),

  // 按状态统计
  getByStatus: () => service.get('/alert/statistics/by-status')
}

// 飞书通知 API
export const feishuApi = {
  // 测试飞书连接
  testConnection: (config) => service.post('/alert/feishu/test', config)
}

// 钉钉通知 API
export const dingtalkApi = {
  // 测试钉钉连接
  testConnection: (config) => service.post('/alert/dingtalk/test', config)
}
