import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '@/features/auth/token'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login/index.vue'),
    meta: { title: '登录', public: true, hideShell: true, transition: 'route-fade' }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home/index.vue'),
    meta: { title: '首页', transition: 'route-slide' }
  },
  {
    path: '/project',
    name: 'Project',
    component: () => import('@/views/Project/index.vue'),
    meta: { title: '项目管理', transition: 'route-slide' }
  },
  {
    path: '/collection',
    name: 'Collection',
    component: () => import('@/views/Collection/index.vue'),
    meta: { title: '日志采集', transition: 'route-slide' }
  },
  {
    path: '/logs',
    name: 'Logs',
    component: () => import('@/views/Logs/index.vue'),
    meta: { title: '日志查询', transition: 'route-slide' }
  },
  {
    path: '/processing',
    name: 'Processing',
    component: () => import('@/views/Processing/index.vue'),
    meta: { title: '日志聚合', transition: 'route-slide' }
  },
  {
    path: '/analysis',
    name: 'Analysis',
    component: () => import('@/views/Analysis/index.vue'),
    meta: { title: '智能分析', transition: 'route-slide' }
  },
  {
    path: '/alerts',
    name: 'Alerts',
    component: () => import('@/views/Alerts/index.vue'),
    meta: { title: '告警管理', transition: 'route-slide' }
  },
  {
    path: '/alerts/rules',
    name: 'AlertRuleManage',
    component: () => import('@/views/Alerts/RuleManage.vue'),
    meta: { title: '告警规则管理', transition: 'route-slide' }
  },
  {
    path: '/config',
    name: 'Config',
    component: () => import('@/views/Config/index.vue'),
    meta: { title: '系统配置', transition: 'route-slide' }
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

  if (to.meta.public) {
    if (to.path === '/login' && getAccessToken()) {
      const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/'
      next(redirect.startsWith('/') ? redirect : '/')
      return
    }
    next()
    return
  }

  if (!getAccessToken()) {
    next({
      path: '/login',
      query: { redirect: to.fullPath }
    })
    return
  }

  next()
})

export default router
