import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/main.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import ECharts from 'vue-echarts'
import * as echarts from 'echarts'

import App from './App.vue'
import router from './router'
import Skeleton from './components/Skeleton.vue'
import Loading from './components/Loading.vue'

const app = createApp(App)

app.component('v-chart', ECharts)
app.component('Skeleton', Skeleton)
app.component('Loading', Loading)

globalThis.echarts = echarts

Object.entries(ElementPlusIconsVue).forEach(([key, component]) => {
  app.component(key, component)
})

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
