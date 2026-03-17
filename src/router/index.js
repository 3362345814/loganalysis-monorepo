import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home/index.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/project',
    name: 'Project',
    component: () => import('@/views/Project/index.vue'),
    meta: { title: '项目管理' }
  },
  {
    path: '/collection',
    name: 'Collection',
    component: () => import('@/views/Collection/index.vue'),
    meta: { title: '日志采集' }
  },
  {
    path: '/logs',
    name: 'Logs',
    component: () => import('@/views/Logs/index.vue'),
    meta: { title: '日志查询' }
  },
  {
    path: '/processing',
    name: 'Processing',
    component: () => import('@/views/Processing/index.vue'),
    meta: { title: '日志处理' }
  },
  {
    path: '/analysis',
    name: 'Analysis',
    component: () => import('@/views/Analysis/index.vue'),
    meta: { title: '智能分析' }
  },
  {
    path: '/alerts',
    name: 'Alerts',
    component: () => import('@/views/Alerts/index.vue'),
    meta: { title: '告警管理' }
  },
  {
    path: '/alerts/rules',
    name: 'AlertRuleManage',
    component: () => import('@/views/Alerts/RuleManage.vue'),
    meta: { title: '告警规则管理' }
  },
  {
    path: '/llm-config',
    name: 'LlmConfig',
    component: () => import('@/views/LlmConfig/index.vue'),
    meta: { title: 'LLM 配置' }
  },
  {
    path: '/config',
    name: 'Config',
    component: () => import('@/views/Config/index.vue'),
    meta: { title: '系统配置' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = `${to.meta.title} - 日志分析系统`
  }
  next()
})

export default router
