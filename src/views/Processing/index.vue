<template>
  <div class="processing-page">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 日志解析测试 -->
      <el-tab-pane label="日志解析测试" name="parse">
        <el-card class="tool-card">
          <template #header>
            <div class="card-header">
              <span>日志解析测试</span>
            </div>
          </template>
          
          <el-form :model="parseForm" label-width="100px">
            <el-form-item label="日志格式">
              <el-select v-model="parseForm.format" placeholder="选择日志格式">
                <el-option label="默认格式" value="DEFAULT" />
                <el-option label="Spring Boot" value="SPRING_BOOT" />
                <el-option label="JSON" value="JSON" />
              </el-select>
            </el-form-item>
            <el-form-item label="日志内容">
              <el-input
                v-model="parseForm.content"
                type="textarea"
                :rows="8"
                placeholder="请输入日志内容进行解析测试"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleParse" :loading="parseLoading">解析</el-button>
              <el-button @click="parseForm.content = ''">清空</el-button>
              <el-button @click="loadParseSample">加载示例</el-button>
            </el-form-item>
          </el-form>
          
          <!-- 解析结果 -->
          <el-divider v-if="parseResult" content-position="left">解析结果</el-divider>
          <el-card v-if="parseResult" class="result-card">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="时间">{{ parseResult.logTime }}</el-descriptions-item>
              <el-descriptions-item label="级别">
                <el-tag :type="getLevelType(parseResult.logLevel)">{{ parseResult.logLevel }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="线程">{{ parseResult.threadName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="类名">{{ parseResult.className || '-' }}</el-descriptions-item>
              <el-descriptions-item label="方法">{{ parseResult.methodName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="行号">{{ parseResult.lineNumber || '-' }}</el-descriptions-item>
              <el-descriptions-item label="日志消息" :span="2">
                <pre class="message-content">{{ parseResult.message }}</pre>
              </el-descriptions-item>
              <el-descriptions-item v-if="parseResult.exceptionType" label="异常类型" :span="2">
                {{ parseResult.exceptionType }}
              </el-descriptions-item>
              <el-descriptions-item v-if="parseResult.stackTrace" label="堆栈跟踪" :span="2">
                <pre class="stack-trace">{{ parseResult.stackTrace }}</pre>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-card>
      </el-tab-pane>

      <!-- 敏感信息脱敏测试 -->
      <el-tab-pane label="脱敏测试" name="desensitize">
        <el-card class="tool-card">
          <template #header>
            <div class="card-header">
              <span>敏感信息脱敏测试</span>
            </div>
          </template>
          
          <el-form :model="desensitizeForm" label-width="100px">
            <el-form-item label="待脱敏内容">
              <el-input
                v-model="desensitizeForm.content"
                type="textarea"
                :rows="8"
                placeholder="请输入需要脱敏的内容，支持测试：手机号、邮箱、密码、Token、身份证等"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleDesensitize" :loading="desensitizeLoading">脱敏</el-button>
              <el-button @click="desensitizeForm.content = ''">清空</el-button>
              <el-button @click="loadDesensitizeSample">加载示例</el-button>
            </el-form-item>
          </el-form>
          
          <!-- 脱敏结果 -->
          <el-divider v-if="desensitizeResult" content-position="left">脱敏结果</el-divider>
          <el-card v-if="desensitizeResult" class="result-card">
            <el-form label-width="100px">
              <el-form-item label="脱敏后">
                <el-input
                  v-model="desensitizeResult"
                  type="textarea"
                  :rows="8"
                  readonly
                />
              </el-form-item>
            </el-form>
          </el-card>
        </el-card>
      </el-tab-pane>

      <!-- 处理管道状态 -->
      <el-tab-pane label="管道状态" name="status">
        <el-card class="tool-card">
          <template #header>
            <div class="card-header">
              <span>处理管道状态</span>
              <el-button :icon="Refresh" @click="loadStatus" :loading="statusLoading">刷新</el-button>
            </div>
          </template>
          
          <el-descriptions :column="2" border v-if="statusData">
            <el-descriptions-item label="模块">{{ statusData.module }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusData.status === 'RUNNING' ? 'success' : 'danger'">
                {{ statusData.status }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- 处理规则说明 -->
      <el-tab-pane label="规则说明" name="rules">
        <el-card class="tool-card">
          <template #header>
            <div class="card-header">
              <span>处理规则说明</span>
            </div>
          </template>
          
          <el-collapse v-model="activeCollapse">
            <el-collapse-item title="日志解析" name="parse">
              <p>系统支持多种日志格式的自动解析：</p>
              <ul>
                <li><strong>Spring Boot 格式</strong>：标准 Spring Boot 日志格式</li>
                <li><strong>JSON 格式</strong>：JSON 格式的结构化日志</li>
                <li><strong>默认格式</strong>：其他通用日志格式</li>
              </ul>
            </el-collapse-item>
            
            <el-collapse-item title="事件识别规则" name="event">
              <p>系统内置以下事件识别规则：</p>
              <el-table :data="eventRules" size="small">
                <el-table-column prop="name" label="规则名称" />
                <el-table-column prop="ruleType" label="类型" />
                <el-table-column prop="pattern" label="匹配模式" />
                <el-table-column prop="eventLevel" label="事件级别">
                  <template #default="{ row }">
                    <el-tag :type="getLevelType(row.eventLevel)">{{ row.eventLevel }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
            
            <el-collapse-item title="敏感信息脱敏" name="desensitize">
              <p>系统支持以下敏感信息脱敏：</p>
              <ul>
                <li><strong>手机号</strong>：138****1234</li>
                <li><strong>邮箱</strong>：te***@example.com</li>
                <li><strong>身份证号</strong>：330***********1234</li>
                <li><strong>密码</strong>：password=******</li>
                <li><strong>Token</strong>：token=******</li>
                <li><strong>IP地址</strong>：192.168.*.*</li>
              </ul>
            </el-collapse-item>
            
            <el-collapse-item title="日志聚合" name="aggregation">
              <p>日志聚合功能：</p>
              <ul>
                <li>基于模板相似度进行聚合</li>
                <li>相似度阈值默认 0.85</li>
                <li>聚合组超时时间默认 60 分钟</li>
                <li>支持自动识别异常严重程度</li>
              </ul>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { logProcessingApi } from '@/api'
import { ElMessage } from 'element-plus'

const activeTab = ref('parse')
const activeCollapse = ref(['parse', 'event', 'desensitize', 'aggregation'])

// 解析表单
const parseForm = reactive({
  format: 'SPRING_BOOT',
  content: ''
})
const parseLoading = ref(false)
const parseResult = ref(null)

// 脱敏表单
const desensitizeForm = reactive({
  content: ''
})
const desensitizeLoading = ref(false)
const desensitizeResult = ref(null)

// 状态数据
const statusLoading = ref(false)
const statusData = ref(null)

// 事件规则
const eventRules = ref([
  { name: '致命错误', ruleType: 'LEVEL', pattern: 'FATAL', eventLevel: 'FATAL' },
  { name: '错误日志', ruleType: 'LEVEL', pattern: 'ERROR', eventLevel: 'ERROR' },
  { name: '异常检测', ruleType: 'KEYWORD', pattern: 'Exception', eventLevel: 'ERROR' },
  { name: '空指针异常', ruleType: 'KEYWORD', pattern: 'NullPointerException', eventLevel: 'ERROR' }
])

// 获取日志级别类型
const getLevelType = (level) => {
  const typeMap = {
    'TRACE': 'info',
    'DEBUG': 'info',
    'INFO': 'success',
    'WARN': 'warning',
    'WARNING': 'warning',
    'ERROR': 'danger',
    'FATAL': 'danger'
  }
  return typeMap[level] || 'info'
}

// 解析日志
const handleParse = async () => {
  if (!parseForm.content) {
    ElMessage.warning('请输入日志内容')
    return
  }
  
  parseLoading.value = true
  parseResult.value = null
  try {
    const res = await logProcessingApi.testParse(parseForm.content, parseForm.format)
    parseResult.value = res.data
    ElMessage.success('解析成功')
  } catch (error) {
    console.error('解析失败:', error)
  } finally {
    parseLoading.value = false
  }
}

// 脱敏
const handleDesensitize = async () => {
  if (!desensitizeForm.content) {
    ElMessage.warning('请输入待脱敏内容')
    return
  }
  
  desensitizeLoading.value = true
  desensitizeResult.value = null
  
  try {
    const res = await logProcessingApi.testDesensitize(desensitizeForm.content)
    desensitizeResult.value = res.data
    ElMessage.success('脱敏成功')
  } catch (error) {
    console.error('脱敏失败:', error)
  } finally {
    desensitizeLoading.value = false
  }
}

// 加载解析示例
const loadParseSample = () => {
  parseForm.content = '2026-01-15 10:30:00.123 [INFO] [http-nio-8080-exec-1] [com.example.controller.UserController:45] User login successful'
}

// 加载脱敏示例
const loadDesensitizeSample = () => {
  desensitizeForm.content = `用户信息：
手机号：13812345678
邮箱：testuser@example.com
密码：password=mysecret123
Token：token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ
身份证：330101199001011234
IP地址：192.168.1.100`
}

// 加载状态
const loadStatus = async () => {
  statusLoading.value = true
  try {
    const res = await logProcessingApi.getStatus()
    statusData.value = res.data
  } catch (error) {
    console.error('加载状态失败:', error)
  } finally {
    statusLoading.value = false
  }
}

onMounted(() => {
  loadStatus()
})
</script>

<style scoped>
.processing-page {
  padding: 20px;
}

.tool-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-card {
  margin-top: 20px;
}

.message-content,
.stack-trace {
  margin: 0;
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}

.el-collapse {
  margin-top: 10px;
}
</style>
