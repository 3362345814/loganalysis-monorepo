<template>
  <div class="collection-page">
    <!-- 操作栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="18">
          <el-button type="primary" :icon="Plus" @click="handleCreateProject">新建项目</el-button>
          <el-button v-if="currentProject" type="success" :icon="Plus" @click="handleCreateSource">新建采集源</el-button>
          <el-button :icon="Refresh" @click="loadSources">刷新</el-button>
        </el-col>
        <el-col :span="6" style="text-align: right">
          <el-tag type="success">采集源: {{ sources.length }}</el-tag>
        </el-col>
      </el-row>
    </el-card>

    <!-- 当前项目提示 -->
    <el-card class="project-info-card" v-if="currentProject">
      <div class="project-info">
        <el-icon><FolderOpened /></el-icon>
        <span class="project-name">{{ currentProject.name }}</span>
        <el-button type="info" link @click="showProjectSelector">切换项目</el-button>
      </div>
    </el-card>
    <el-card class="project-info-card" v-else>
      <div class="project-info-empty">
        <el-icon><WarningFilled /></el-icon>
        <span>请先选择一个项目，或创建新项目</span>
        <el-button type="primary" size="small" style="margin-left: 15px" @click="showProjectSelector">选择项目</el-button>
      </div>
    </el-card>

    <!-- 日志源列表 -->
    <el-card class="table-card">
      <el-table :data="sources" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="projectName" label="所属项目" width="120">
          <template #default="{ row }">
            <el-tag type="info">{{ row.projectName || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.sourceType || 'LOCAL_FILE' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路径" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatPaths(row.paths) }}
          </template>
        </el-table-column>
        <el-table-column prop="logFormat" label="格式" width="100">
          <template #default="{ row }">
            <el-tag type="info">{{ getLogFormatText(row.logFormat) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" disabled />
          </template>
        </el-table-column>
        <el-table-column prop="aggregationLevel" label="聚合级别" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.aggregationLevel" :type="row.aggregationLevel === 'ERROR' ? 'danger' : 'warning'">
              {{ row.aggregationLevel }}及以上
            </el-tag>
            <el-tag v-else type="info">全部</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastCollectionTime" label="最后采集" width="180">
          <template #default="{ row }">
            {{ formatTime(row.lastCollectionTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button 
                v-if="row.status !== 'RUNNING'" 
                type="primary" 
                size="small" 
                :icon="VideoPlay" 
                @click="handleStart(row)"
              >
                启动
              </el-button>
              <el-button 
                v-else 
                type="warning" 
                size="small" 
                :icon="VideoPause" 
                @click="handleStop(row)"
              >
                停止
              </el-button>
              <el-button size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
          <el-tag type="success">{{ currentProject?.name || '未选择' }}</el-tag>
          <span class="form-tip" style="margin-left: 10px">采集源将绑定到此项目</span>
        </el-form-item>
        <el-form-item label="类型" prop="sourceType">
          <el-select v-model="form.sourceType" placeholder="请选择类型">
            <el-option label="本地文件" value="LOCAL_FILE" />
            <el-option label="SSH远程" value="SSH" />
          </el-select>
        </el-form-item>
        
        <!-- SSH配置 -->
        <template v-if="form.sourceType === 'SSH'">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="form.host" placeholder="SSH服务器地址，如 192.168.1.100" />
          </el-form-item>
          <el-form-item label="端口" prop="port">
            <el-input-number v-model="form.port" :min="1" :max="65535" placeholder="SSH端口，默认22" />
          </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="SSH用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="SSH密码" show-password />
          </el-form-item>
        </template>
        
        <el-form-item label="日志格式" prop="logFormat">
          <el-select v-model="form.logFormat" placeholder="选择日志格式" @change="handleLogFormatChange">
            <el-option label="Spring Boot" value="SPRING_BOOT" />
            <el-option label="Log4j" value="LOG4J" />
            <el-option label="Nginx" value="NGINX" />
          </el-select>
          <span class="form-tip">选择日志格式以支持多行日志（如Java堆栈）合并</span>
        </el-form-item>

        <!-- Spring Boot Log 配置 -->
        <template v-if="form.logFormat === 'SPRING_BOOT'">
          <el-form-item label="日志路径" prop="paths">
            <el-input v-model="form.paths[0]" placeholder="如: /var/log/myapp/application.log" />
            <span class="form-tip">Spring Boot日志只支持单个文件路径，不支持通配符</span>
          </el-form-item>
        </template>

        <!-- Log4j Log 配置 -->
        <template v-if="form.logFormat === 'LOG4J'">
          <el-form-item label="日志路径" prop="paths">
            <el-input v-model="form.paths[0]" placeholder="如: /var/log/myapp/app.log" />
            <span class="form-tip">Log4j日志只支持单个文件路径，不支持通配符</span>
          </el-form-item>
        </template>

        <!-- Nginx Log 配置 -->
        <template v-if="form.logFormat === 'NGINX'">
          <el-form-item label="Access日志" prop="paths">
            <el-input v-model="form.paths[0]" placeholder="如: /var/log/nginx/access.log" />
          </el-form-item>
          <el-form-item label="Error日志" prop="paths">
            <el-input v-model="form.paths[1]" placeholder="如: /var/log/nginx/error.log" />
          </el-form-item>
        </template>
        
        <el-form-item label="编码" prop="encoding">
          <el-select v-model="form.encoding" placeholder="选择编码">
            <el-option label="UTF-8" value="UTF-8" />
            <el-option label="GBK" value="GBK" />
            <el-option label="GB2312" value="GB2312" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="自定义正则" v-if="form.logFormat === 'CUSTOM'">
          <el-input v-model="form.customPattern" placeholder="请输入自定义正则表达式，如 ^\\d{4}-\\d{2}-\\d{2}" />
          <span class="form-tip">用于匹配日志开始行，正则需匹配日志行首</span>
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
                
                <el-table :data="form.customRules" border style="margin-top: 10px" v-if="form.customRules && form.customRules.length > 0">
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
              <el-select v-model="form.aggregationLevel" placeholder="选择聚合级别" clearable style="width: 250px">
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
        <el-form-item label="项目代码" prop="code">
          <el-input v-model="projectForm.code" placeholder="用于关联日志中的项目标识" />
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
          style="margin-bottom: 15px"
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
import { useRouter } from 'vue-router'
import { Plus, Refresh, Edit, Delete, VideoPlay, VideoPause, FolderOpened, WarningFilled, Search, Folder, Check } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { logSourceApi, projectApi } from '@/api'

const router = useRouter()

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
  code: '',
  description: '',
  owner: '',
  email: '',
  enabled: true
})

const projectRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入项目代码', trigger: 'blur' }]
}

const form = ref({
  id: null,
  name: '',
  projectId: null,
  sourceType: 'LOCAL_FILE',
  host: '',
  port: 22,
  username: '',
  password: '',
  paths: [],
  encoding: 'UTF-8',
  logFormat: 'SPRING_BOOT',
  logFormatPattern: '',
  customPattern: '',
  description: '',
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
      } else if ((form.value.logFormat === 'SPRING_BOOT' || form.value.logFormat === 'LOG4J') && value.length > 1) {
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
  password: [{ required: true, message: '请输入SSH密码', trigger: 'blur' }],
}

const getStatusType = (status) => {
  const map = {
    'STOPPED': 'info',
    'RUNNING': 'success',
    'ERROR': 'danger'
  }
  return map[status] || 'info'
}

const getLogFormatText = (format) => {
  const map = {
    'SPRING_BOOT': 'Spring Boot',
    'LOG4J': 'Log4j',
    'NGINX': 'Nginx'
  }
  return map[format] || 'Spring Boot'
}

const getStatusText = (status) => {
  const map = {
    'STOPPED': '已停止',
    'RUNNING': '运行中',
    'ERROR': '错误'
  }
  return map[status] || '未知'
}

const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

const formatPaths = (paths) => {
  if (!paths || !Array.isArray(paths)) {
    return '-'
  }
  return paths.join(', ')
}

// 日志格式变更处理
const handleLogFormatChange = (value) => {
  if (value !== 'CUSTOM') {
    form.value.customPattern = ''
  }
  if (value === 'SPRING_BOOT' || value === 'LOG4J') {
    form.value.paths = ['']
  } else if (value === 'NGINX') {
    form.value.paths = ['', '']
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
  Object.assign(projectForm, { name: '', code: '', description: '', owner: '', email: '', enabled: true })
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
  form.value = {
    id: null,
    name: '',
    projectId: currentProject.value.id,
    sourceType: 'LOCAL_FILE',
    host: '',
    port: 22,
    username: '',
    password: '',
    paths: [],
    encoding: 'UTF-8',
    logFormat: 'SPRING_BOOT',
    logFormatPattern: '',
    customPattern: '',
    description: '',
    desensitizationEnabled: false,
    aggregationLevel: null,
    enabledRuleIds: [],
    customRules: [],
    config: {
      accessLogPath: '',
      errorLogPath: ''
    }
  }
  dialogVisible.value = true
}

const clearProject = () => {
  currentProject.value = null
  localStorage.removeItem('currentProjectId')
  localStorage.removeItem('currentProjectName')
  loadSources()
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

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return

  submitting.value = true
  try {
    const submitData = { ...form.value }
    
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

<style scoped>
.collection-page {
  padding: 20px;
}

.toolbar-card {
  margin-bottom: 20px;
}

.project-info-card {
  margin-bottom: 20px;
}

.project-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.project-info .el-icon {
  font-size: 20px;
  color: #67c23a;
}

.project-name {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.project-info-empty {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #909399;
}

.project-info-empty .el-icon {
  font-size: 18px;
}

.project-select-wrapper {
  max-height: 400px;
  overflow-y: auto;
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.project-item {
  display: flex;
  align-items: center;
  padding: 12px 15px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  gap: 12px;
}

.project-item:hover {
  background-color: #f5f7fa;
  border-color: #409eff;
}

.project-item.active {
  background-color: #ecf5ff;
  border-color: #409eff;
}

.project-item-icon {
  font-size: 24px;
  color: #409eff;
}

.project-item-info {
  flex: 1;
}

.project-item-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.project-item-code {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.project-item-check {
  color: #409eff;
  font-size: 18px;
}

.table-card {
  min-height: 500px;
}

.action-buttons {
  display: flex;
  gap: 5px;
  flex-wrap: nowrap;
}

.action-buttons .el-button {
  padding: 5px 8px;
  font-size: 12px;
}

.form-tip {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}

.custom-rules {
  width: 100%;
}

.rule-examples {
  margin-top: 10px;
}

.rule-examples code {
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  color: #409eff;
}
</style>
