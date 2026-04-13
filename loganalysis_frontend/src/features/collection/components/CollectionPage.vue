<template>
  <div class="collection-page">
    <CollectionToolbar
      :source-count="sources.length"
      :has-current-project="Boolean(currentProject)"
      @create-project="handleCreateProject"
      @create-source="handleCreateSource"
      @refresh="loadSources"
    />

    <CollectionProjectInfo :current-project="currentProject" @select-project="showProjectSelector" />

    <CollectionSourcesTable
      :sources="sources"
      :loading="loading"
      @start="handleStart"
      @stop="handleStop"
      @edit="handleEdit"
      @delete="handleDelete"
    />

    <!-- 新建/编辑对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑采集源' : '新建采集源'" 
      width="700px"
    >
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入采集源名称" />
        </el-form-item>
        <el-form-item label="所属项目" prop="projectId">
          <el-tag type="info">{{ currentProject?.name || '未选择' }}</el-tag>
          <span class="form-tip form-tip-inline">采集源将绑定到此项目</span>
        </el-form-item>
        <el-form-item label="类型" prop="sourceType">
          <el-select v-model="form.sourceType" placeholder="请选择类型">
            <el-option label="SSH远程" value="SSH" />
            <el-option label="本地文件" value="LOCAL_FILE" />
          </el-select>
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            class="source-type-alert"
          >
            <template #title>
              提示：如果项目使用 CLI 部署（运行在 Docker 容器中），则只能使用 SSH 方式采集日志
            </template>
          </el-alert>
        </el-form-item>
        
        <!-- SSH配置 -->
        <template v-if="form.sourceType === 'SSH'">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="form.host" placeholder="SSH服务器地址，如 192.168.1.100" />
          </el-form-item>
        <el-form-item label="端口" prop="port">
            <el-input-number v-model="form.port" :min="1" :max="65535" :controls="false" placeholder="SSH端口，默认22" />
        </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="SSH用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              :placeholder="isEdit && sshPasswordConfigured ? '已配置（留空则保留原密码）' : 'SSH密码'"
              show-password
            />
            <span v-if="isEdit && sshPasswordConfigured" class="form-tip">
              未输入新密码时将保留已保存密码；如需重新测试连接，请输入新密码。
            </span>
          </el-form-item>
          <el-form-item>
            <el-button
              type="info"
              :icon="Connection"
              :loading="testingSsh"
              @click="handleTestSsh"
            >
              测试SSH连接
            </el-button>
            <span v-if="sshTestResult !== null" class="test-result" :class="sshTestResult ? 'success' : 'error'">
              {{ sshTestResult ? '连接成功' : '连接失败' }}
            </span>
          </el-form-item>
        </template>
        
        <el-form-item label="日志格式" prop="logFormat">
          <el-select v-model="form.logFormat" placeholder="选择日志格式" @change="handleLogFormatChange">
            <el-option label="Log4j" value="LOG4J" />
            <el-option label="Nginx" value="NGINX" />
            <el-option label="JSON" value="JSON" />
          </el-select>
          <span class="form-tip">选择日志格式以支持多行日志（如Java堆栈）合并</span>
        </el-form-item>

        <!-- Log4j Log 配置 -->
        <template v-if="form.logFormat === 'LOG4J'">
          <el-form-item label="日志路径" prop="paths">
            <el-input v-model="form.paths[0]" placeholder="如: /var/log/myapp/app.log" @blur="handleAutoTestPath" />
            <span class="form-tip">Log4j日志只支持单个文件路径，不支持通配符</span>
            <span v-if="pathTestResult !== null" class="test-result" :class="pathTestResult ? 'success' : 'error'">
              {{ pathTestMessage }}
            </span>
          </el-form-item>
          <el-form-item label="日志格式" prop="logFormatPattern">
            <el-input 
              v-model="form.logFormatPattern" 
              placeholder="如: %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
              type="textarea"
              :rows="3"
            />
            <span class="form-tip">请输入 Log4j/Logback 格式的日志格式字符串</span>
          </el-form-item>
          <el-form-item label="格式说明">
            <div class="pattern-help">
              <el-alert type="info" :closable="false" show-icon>
                <template #title>
                  <div>支持的占位符：</div>
                  <ul class="pattern-list">
                    <li><code>%d{format}</code> - 日期时间，如 <code>%d{yyyy-MM-dd HH:mm:ss.SSS}</code></li>
                    <li><code>%thread</code> - 线程名称</li>
                    <li><code>%level</code> - 日志级别</li>
                    <li><code>%-5level</code> - 左对齐宽度5的日志级别</li>
                    <li><code>%logger{length}</code> - Logger名称，可指定最大长度</li>
                    <li><code>%logger</code> - Logger名称（完整）</li>
                    <li><code>%msg</code> 或 <code>%m</code> - 消息内容</li>
                    <li><code>%n</code> - 换行符</li>
                    <li><code>%X{key}</code> - MDC 键值</li>
                  </ul>
                </template>
              </el-alert>
              <el-alert type="warning" :closable="false" show-icon class="pattern-warning-alert">
                <template #title>
                  如需支持链路追踪，日志中需包含 <code>traceId</code> 或 <code>trace_id</code> 字段
                </template>
              </el-alert>
              <div class="pattern-buttons">
                <el-button size="small" type="primary" plain @click="applySpringBootPattern">
                  使用 Spring Boot 格式
                </el-button>
                <el-button size="small" type="primary" plain @click="applyLog4jPattern">
                  使用 Log4j 格式
                </el-button>
              </div>
            </div>
          </el-form-item>
        </template>

        <!-- Nginx Log 配置 -->
        <template v-if="form.logFormat === 'NGINX'">
          <el-form-item label="Access日志" prop="paths">
            <el-input v-model="form.paths[0]" placeholder="如: /var/log/nginx/access.log" @blur="handleAutoTestPath" />
          </el-form-item>
          <el-form-item label="Error日志" prop="paths">
            <el-input v-model="form.paths[1]" placeholder="如: /var/log/nginx/error.log" @blur="handleAutoTestPath" />
          </el-form-item>
          <span
            v-if="pathTestResult !== null"
            class="test-result nginx-path-test-result"
            :class="pathTestResult ? 'success' : 'error'"
          >
            {{ pathTestMessage }}
          </span>
          <el-alert type="warning" :closable="false" show-icon class="form-alert-spacing">
            <template #title>
              如需支持链路追踪，日志中需包含 <code>traceId</code> 或 <code>trace_id</code> 字段
            </template>
          </el-alert>
        </template>

        <!-- JSON Log 配置 -->
        <template v-if="form.logFormat === 'JSON'">
          <el-form-item label="日志路径" prop="paths">
            <el-input v-model="form.paths[0]" placeholder="如: /var/log/myapp/app.log" @blur="handleAutoTestPath" />
            <span class="form-tip">JSON日志只支持单个文件路径，不支持通配符</span>
            <span v-if="pathTestResult !== null" class="test-result" :class="pathTestResult ? 'success' : 'error'">
              {{ pathTestMessage }}
            </span>
          </el-form-item>
          <el-alert type="info" :closable="false" show-icon class="form-alert-spacing">
            <template #title>
              JSON格式日志示例：<code>{"timestamp":"2026-03-19 10:00:00","level":"ERROR","message":"错误信息","traceId":"abc123"}</code>
            </template>
          </el-alert>
          <el-alert type="warning" :closable="false" show-icon>
            <template #title>
              如需支持链路追踪，日志中需包含 <code>traceId</code> 或 <code>trace_id</code> 字段
            </template>
          </el-alert>
        </template>
        
        <el-form-item label="编码" prop="encoding">
          <el-select v-model="form.encoding" placeholder="选择编码">
            <el-option label="UTF-8" value="UTF-8" />
            <el-option label="GBK" value="GBK" />
            <el-option label="GB2312" value="GB2312" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
        </el-tab-pane>
        
        <el-tab-pane label="脱敏配置" name="desensitization">
          <el-form label-width="120px">
            <!-- 脱敏开关 -->
            <el-form-item label="启用脱敏">
              <el-switch v-model="form.desensitizationEnabled" />
              <span class="form-tip">开启后将对采集的日志进行敏感信息脱敏处理</span>
            </el-form-item>
            
            <!-- 预设规则 -->
            <el-form-item label="预设脱敏规则" v-if="form.desensitizationEnabled">
              <el-checkbox-group v-model="form.enabledRuleIds">
                <el-checkbox label="phone">手机号</el-checkbox>
                <el-checkbox label="email">邮箱</el-checkbox>
                <el-checkbox label="idcard">身份证号</el-checkbox>
                <el-checkbox label="password">密码</el-checkbox>
                <el-checkbox label="token">Token/API Key</el-checkbox>
                <el-checkbox label="ip">IP地址</el-checkbox>
                <el-checkbox label="bankcard">银行卡号</el-checkbox>
              </el-checkbox-group>
              <div class="form-tip">选择需要脱敏的敏感信息类型</div>
            </el-form-item>
            
            <!-- 自定义规则 -->
            <el-form-item label="自定义规则" v-if="form.desensitizationEnabled">
              <div class="custom-rules">
                <el-button size="small" type="primary" plain @click="addCustomRule">
                  <el-icon><Plus /></el-icon> 添加自定义规则
                </el-button>
                
                <el-table
                  v-if="form.customRules && form.customRules.length > 0"
                  :data="form.customRules"
                  border
                  class="custom-rules-table"
                >
                  <el-table-column label="规则名称" min-width="100">
                    <template #default="{ row, $index }">
                      <el-input v-model="row.name" size="small" placeholder="规则名称" />
                    </template>
                  </el-table-column>
                  <el-table-column label="正则表达式" min-width="150">
                    <template #default="{ row }">
                      <el-input v-model="row.pattern" size="small" placeholder="正则表达式" />
                    </template>
                  </el-table-column>
                  <el-table-column label="脱敏方式" width="120">
                    <template #default="{ row }">
                      <el-select v-model="row.maskType" size="small">
                        <el-option label="完全脱敏" value="FULL" />
                        <el-option label="部分脱敏" value="PARTIAL" />
                        <el-option label="哈希脱敏" value="HASH" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="替换内容" min-width="120">
                    <template #default="{ row }">
                      <el-input v-model="row.replacement" size="small" placeholder="替换内容" />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="80">
                    <template #default="{ $index }">
                      <el-button type="danger" size="small" text @click="removeCustomRule($index)">
                        删除
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
                
                <div class="rule-examples" v-if="!form.customRules || form.customRules.length === 0">
                  <el-alert
                    title="暂无自定义规则"
                    type="info"
                    :closable="false"
                    show-icon
                  >
                    <template #default>
                      <div>示例：用户ID脱敏 - 正则: <code>userId=(\d+)</code> 替换: <code>userId=***</code></div>
                    </template>
                  </el-alert>
                </div>
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="聚合配置" name="aggregation">
          <el-form label-width="120px">
            <el-form-item label="聚合级别">
              <el-select v-model="form.aggregationLevel" placeholder="选择聚合级别" clearable class="aggregation-level-select">
                <el-option label="聚合所有级别" :value="null" />
                <el-option label="WARN 及以上（WARN, ERROR, FATAL）" value="WARN" />
                <el-option label="ERROR 及以上（ERROR, FATAL）" value="ERROR" />
              </el-select>
              <span class="form-tip">只有等于或高于此级别的日志才会被聚合，未选择则聚合所有日志</span>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新建项目对话框 -->
    <el-dialog v-model="projectDialogVisible" :title="isProjectEdit ? '编辑项目' : '新建项目'" width="600px">
      <el-form :model="projectForm" :rules="projectRules" ref="projectFormRef" label-width="100px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="projectForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="projectForm.description" type="textarea" :rows="2" placeholder="项目描述" />
        </el-form-item>
        <el-form-item label="负责人" prop="owner">
          <el-input v-model="projectForm.owner" placeholder="项目负责人" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="projectForm.email" placeholder="联系人邮箱" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="projectForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="projectDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleProjectSubmit" :loading="projectSubmitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 项目选择对话框 -->
    <el-dialog v-model="projectSelectDialogVisible" title="选择项目" width="500px">
      <div class="project-select-wrapper">
        <el-input
          v-model="projectSearchText"
          placeholder="搜索项目名称"
          :prefix-icon="Search"
          clearable
          class="project-search-input"
        />
        <div class="project-list">
          <div
            v-for="project in filteredProjects"
            :key="project.id"
            class="project-item"
            :class="{ active: currentProject?.id === project.id }"
            @click="selectProject(project)"
          >
            <div class="project-item-icon">
              <el-icon><Folder /></el-icon>
            </div>
            <div class="project-item-info">
              <div class="project-item-name">{{ project.name }}</div>
              <div class="project-item-code">{{ project.code }}</div>
            </div>
            <div class="project-item-check" v-if="currentProject?.id === project.id">
              <el-icon><Check /></el-icon>
            </div>
          </div>
          <el-empty v-if="filteredProjects.length === 0" description="暂无项目，请先创建" />
        </div>
      </div>
      <template #footer>
        <el-button @click="projectSelectDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleCreateProject">新建项目</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Plus, Search, Folder, Check, Connection } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logSourceApi, projectApi } from '@/api'
import CollectionToolbar from './CollectionToolbar.vue'
import CollectionProjectInfo from './CollectionProjectInfo.vue'
import CollectionSourcesTable from './CollectionSourcesTable.vue'
import '../styles/collection-page.css'

const sources = ref([])
const projects = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const activeTab = ref('basic')

const currentProject = ref(null)
const projectDialogVisible = ref(false)
const projectFormRef = ref(null)
const isProjectEdit = ref(false)
const projectSubmitting = ref(false)
const projectSelectDialogVisible = ref(false)
const projectSearchText = ref('')
const testingSsh = ref(false)
const sshTestResult = ref(null)
const sshPasswordConfigured = ref(false)
const testingPath = ref(false)
const pathTestResult = ref(null)
const pathTestMessage = ref('')

const filteredProjects = computed(() => {
  if (!projectSearchText.value) {
    return projects.value
  }
  const search = projectSearchText.value.toLowerCase()
  return projects.value.filter(p => 
    p.name.toLowerCase().includes(search) || 
    p.code.toLowerCase().includes(search)
  )
})

const projectForm = reactive({
  name: '',
  description: '',
  owner: '',
  email: '',
  enabled: true
})

const projectRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

const form = ref({
  id: null,
  name: '',
  projectId: null,
  sourceType: 'SSH',
  host: '',
  port: 22,
  username: '',
  password: '',
  paths: [],
  encoding: 'UTF-8',
  logFormat: 'LOG4J',
  logFormatPattern: '',
  description: '',
  desensitizationEnabled: false,
  aggregationLevel: 'WARN',
  enabledRuleIds: [],
  customRules: [],
  config: {
    accessLogPath: '',
    errorLogPath: ''
  }
})

const rules = {
  name: [{ required: true, message: '请输入采集源名称', trigger: 'blur' }],
  paths: [{ 
    validator: (rule, value, callback) => {
      if (!value || value.length === 0) {
        callback(new Error('请输入日志文件路径'))
      } else if (form.value.logFormat === 'LOG4J' && value.length > 1) {
        callback(new Error('该日志格式只支持单个文件路径'))
      } else if (form.value.logFormat === 'NGINX' && value.length !== 2) {
        callback(new Error('Nginx日志需要两个文件路径（access.log和error.log）'))
      } else {
        callback()
      }
    }, 
    trigger: 'blur' 
  }],
  sourceType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入SSH主机地址', trigger: 'blur' }],
  username: [{ required: true, message: '请输入SSH用户名', trigger: 'blur' }],
  password: [{
    validator: (rule, value, callback) => {
      if (form.value.sourceType !== 'SSH') {
        callback()
        return
      }
      if (value && String(value).trim()) {
        callback()
        return
      }
      if (isEdit.value && sshPasswordConfigured.value) {
        callback()
        return
      }
      callback(new Error('请输入SSH密码'))
    },
    trigger: 'blur'
  }],
}

// 日志格式变更处理
const handleLogFormatChange = (value) => {
  if (value === 'LOG4J') {
    form.value.paths = ['']
    form.value.logFormatPattern = form.value.logFormatPattern || '%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n'
  } else if (value === 'NGINX') {
    form.value.paths = ['', '']
    form.value.logFormatPattern = ''
  }
}

const loadSources = async () => {
  loading.value = true
  try {
    if (currentProject.value && currentProject.value.id) {
      const res = await logSourceApi.getByProjectId(currentProject.value.id)
      sources.value = res.data || []
    } else {
      const res = await logSourceApi.getAll()
      sources.value = res.data || []
    }
  } catch (error) {
    console.error('加载采集源失败:', error)
  } finally {
    loading.value = false
  }
}

const loadProjects = async () => {
  try {
    const res = await projectApi.getEnabled()
    projects.value = res.data || []
  } catch (error) {
    console.error('加载项目失败:', error)
  }
}

const handleCreateProject = () => {
  isProjectEdit.value = false
  Object.assign(projectForm, { name: '', description: '', owner: '', email: '', enabled: true })
  projectDialogVisible.value = true
}

const handleProjectSubmit = async () => {
  await projectFormRef.value.validate()
  projectSubmitting.value = true
  try {
    if (isProjectEdit.value) {
      await projectApi.update(projectForm.id, projectForm)
      ElMessage.success('更新成功')
    } else {
      const res = await projectApi.create(projectForm)
      ElMessage.success('创建成功')
      currentProject.value = res.data
      localStorage.setItem('currentProjectId', currentProject.value.id)
      localStorage.setItem('currentProjectName', currentProject.value.name)
    }
    projectDialogVisible.value = false
    loadProjects()
  } catch (error) {
    console.error('操作失败:', error)
  } finally {
    projectSubmitting.value = false
  }
}

const handleCreateSource = () => {
  if (!currentProject.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  isEdit.value = false
  // 重置测试结果
  testingSsh.value = false
  sshTestResult.value = null
  testingPath.value = false
  pathTestResult.value = null
  pathTestMessage.value = ''
  sshPasswordConfigured.value = false
  form.value = {
    id: null,
    name: '',
    projectId: currentProject.value.id,
    sourceType: 'SSH',
    host: '',
    port: 22,
    username: '',
    password: '',
    paths: [],
    encoding: 'UTF-8',
    logFormat: 'LOG4J',
    logFormatPattern: '%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n',
    description: '',
    desensitizationEnabled: false,
    aggregationLevel: 'WARN',
    enabledRuleIds: [],
    customRules: [],
    config: {
      accessLogPath: '',
      errorLogPath: ''
    }
  }
  dialogVisible.value = true
}

const showProjectSelector = async () => {
  await loadProjects()
  projectSearchText.value = ''
  projectSelectDialogVisible.value = true
}

const selectProject = (project) => {
  currentProject.value = project
  localStorage.setItem('currentProjectId', project.id)
  localStorage.setItem('currentProjectName', project.name)
  projectSelectDialogVisible.value = false
  loadSources()
  ElMessage.success(`已选择项目: ${project.name}`)
}

const handleEdit = (row) => {
  isEdit.value = true
  // 重置测试结果
  testingSsh.value = false
  sshTestResult.value = null
  testingPath.value = false
  pathTestResult.value = null
  pathTestMessage.value = ''
  sshPasswordConfigured.value = row.passwordConfigured === true

  let parsedPaths = []
  if (row.paths && Array.isArray(row.paths)) {
    parsedPaths = row.paths
  } else if (row.path) {
    parsedPaths = [row.path]
    if (row.filePattern) {
      const patterns = row.filePattern.split(',').map(p => p.trim()).filter(p => p)
      parsedPaths = [...parsedPaths, ...patterns]
    }
  }

  form.value = {
    ...row,
    password: '',
    paths: parsedPaths,
    desensitizationEnabled: row.desensitizationEnabled || false,
    aggregationLevel: row.aggregationLevel || null,
    enabledRuleIds: row.enabledRuleIds || [],
    customRules: row.customRules || [],
    logFormatPattern: row.logFormatPattern || '',
    config: row.config || {
      accessLogPath: '',
      errorLogPath: ''
    }
  }
  dialogVisible.value = true
}

// 添加自定义规则
const addCustomRule = () => {
  if (!form.value.customRules) {
    form.value.customRules = []
  }
  form.value.customRules.push({
    id: 'custom_' + Date.now(),
    name: '',
    pattern: '',
    maskType: 'PARTIAL',
    replacement: ''
  })
}

// 删除自定义规则
const removeCustomRule = (index) => {
  form.value.customRules.splice(index, 1)
}

// 应用 Spring Boot 格式
const applySpringBootPattern = () => {
  form.value.logFormatPattern = '%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n'
}

// 应用 Log4j 格式
const applyLog4jPattern = () => {
  form.value.logFormatPattern = '%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n'
}

// 测试SSH连接
const handleTestSsh = async () => {
  if (!form.value.host) {
    ElMessage.warning('请输入主机地址')
    return
  }
  if (!form.value.username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.value.password) {
    if (isEdit.value && sshPasswordConfigured.value) {
      ElMessage.warning('当前为保留旧密码模式，输入新密码后可执行连接测试')
      return
    }
    ElMessage.warning('请输入密码')
    return
  }

  testingSsh.value = true
  sshTestResult.value = null
  try {
    const config = {
      host: form.value.host,
      port: form.value.port || 22,
      username: form.value.username,
      password: form.value.password
    }
    const res = await logSourceApi.testSshConnection(config)
    sshTestResult.value = res.data.success
    if (res.data.success) {
      ElMessage.success(res.data.message || 'SSH连接成功')
    } else {
      ElMessage.error(res.data.message || 'SSH连接失败')
    }
  } catch (error) {
    sshTestResult.value = false
    ElMessage.error(error.message || 'SSH连接测试失败')
  } finally {
    testingSsh.value = false
  }
}

// 测试日志路径是否存在（自动验证）
const handleAutoTestPath = async () => {
  // 防抖：300ms 内不重复验证
  if (handleAutoTestPath.lastTime && Date.now() - handleAutoTestPath.lastTime < 300) {
    return
  }
  handleAutoTestPath.lastTime = Date.now()

  const paths = form.value.paths.filter(p => p && p.trim())
  if (paths.length === 0) {
    return
  }

  // 如果是 SSH 类型但没有配置 SSH，则跳过
  if (form.value.sourceType === 'SSH') {
    if (!form.value.host || !form.value.username || !form.value.password) {
      return
    }
  }

  testingPath.value = true
  pathTestResult.value = null
  pathTestMessage.value = ''
  try {
    const config = {
      sourceType: form.value.sourceType,
      paths: paths,
      host: form.value.host,
      port: form.value.port || 22,
      username: form.value.username,
      password: form.value.password
    }
    const res = await logSourceApi.testPathExists(config)
    pathTestResult.value = res.data.success
    pathTestMessage.value = res.data.message || (res.data.success ? '路径存在' : '路径不存在')
  } catch (error) {
    pathTestResult.value = false
    pathTestMessage.value = error.message || '验证失败'
  } finally {
    testingPath.value = false
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return

  // SSH 类型：点击确定时先执行连接和路径测试
  if (form.value.sourceType === 'SSH') {
    const paths = form.value.paths?.filter(p => p && p.trim()) || []
    const hasInputPassword = !!form.value.password?.trim()
    const keepExistingPassword = isEdit.value && sshPasswordConfigured.value && !hasInputPassword

    if (!form.value.host || !form.value.username) {
      ElMessage.warning('请填写完整的 SSH 连接信息')
      return
    }
    if (!hasInputPassword && !keepExistingPassword) {
      ElMessage.warning('请填写 SSH 密码')
      return
    }
    if (paths.length === 0) {
      ElMessage.warning('请填写日志路径')
      return
    }

    if (hasInputPassword) {
      // 1. 测试 SSH 连接
      testingSsh.value = true
      sshTestResult.value = null
      let sshOk = false
      try {
        const res = await logSourceApi.testSshConnection({
          host: form.value.host,
          port: form.value.port || 22,
          username: form.value.username,
          password: form.value.password
        })
        sshOk = res.data.success
        sshTestResult.value = sshOk
        if (!sshOk) {
          ElMessage.error('SSH 连接失败: ' + (res.data.message || '连接失败'))
        }
      } catch (error) {
        sshTestResult.value = false
        ElMessage.error('SSH 连接失败: ' + (error.message || '连接失败'))
      } finally {
        testingSsh.value = false
      }
      if (!sshOk) return

      // 2. 测试路径是否存在
      testingPath.value = true
      pathTestResult.value = null
      pathTestMessage.value = ''
      let pathOk = false
      try {
        const res = await logSourceApi.testPathExists({
          sourceType: 'SSH',
          paths: paths,
          host: form.value.host,
          port: form.value.port || 22,
          username: form.value.username,
          password: form.value.password
        })
        pathOk = res.data.success
        pathTestResult.value = pathOk
        pathTestMessage.value = res.data.message || (pathOk ? '路径存在' : '路径不存在')
        if (!pathOk) {
          ElMessage.error('路径验证失败: ' + (res.data.message || '路径不存在'))
        }
      } catch (error) {
        pathTestResult.value = false
        pathTestMessage.value = error.message || '验证失败'
        ElMessage.error('路径验证失败: ' + (error.message || '验证失败'))
      } finally {
        testingPath.value = false
      }
      if (!pathOk) return
    }
  }

  submitting.value = true
  try {
    const submitData = { ...form.value }
    if (!submitData.password || !submitData.password.trim()) {
      delete submitData.password
    }

    if (submitData.logFormat === 'NGINX') {
      if (submitData.paths && submitData.paths.length >= 2) {
        submitData.config = {
          ...submitData.config,
          accessLogPath: submitData.paths[0],
          errorLogPath: submitData.paths[1]
        }
      }
    }
    
    if (isEdit.value) {
      await logSourceApi.update(form.value.id, submitData)
      ElMessage.success('更新成功')
    } else {
      await logSourceApi.create(submitData)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadSources()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const handleStart = async (row) => {
  try {
    await logSourceApi.startCollector(row.id)
    ElMessage.success('采集器已启动')
    loadSources()
  } catch (error) {
    console.error('启动失败:', error)
    ElMessage.error(error.response?.data?.message || '启动失败')
  }
}

const handleStop = async (row) => {
  try {
    await logSourceApi.stopCollector(row.id)
    ElMessage.success('采集器已停止')
    loadSources()
  } catch (error) {
    console.error('停止失败:', error)
    ElMessage.error(error.response?.data?.message || '停止失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除采集源 "${row.name}" 吗？`,
      '警告',
      { type: 'warning' }
    )
    await logSourceApi.delete(row.id)
    ElMessage.success('删除成功')
    loadSources()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

onMounted(() => {
  const savedProjectId = localStorage.getItem('currentProjectId')
  const savedProjectName = localStorage.getItem('currentProjectName')
  if (savedProjectId && savedProjectName) {
    currentProject.value = { id: savedProjectId, name: savedProjectName }
  }
  loadProjects()
  loadSources()
})
</script>
