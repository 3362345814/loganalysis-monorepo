<template>
  <div class="collection-page">
    <!-- 操作栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="18">
          <el-button type="primary" :icon="Plus" @click="handleCreate">新建采集源</el-button>
          <el-button :icon="Refresh" @click="loadSources">刷新</el-button>
        </el-col>
        <el-col :span="6" style="text-align: right">
          <el-tag type="success">采集源: {{ sources.length }}</el-tag>
        </el-col>
      </el-row>
    </el-card>

    <!-- 日志源列表 -->
    <el-card class="table-card">
      <el-table :data="sources" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="sourceType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.sourceType || 'LOCAL_FILE' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
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
        <el-table-column prop="lastCollectionTime" label="最后采集" width="180">
          <template #default="{ row }">
            {{ formatTime(row.lastCollectionTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button 
              v-if="row.status !== 'COLLECTING'" 
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
        <el-form-item label="类型" prop="sourceType">
          <el-select v-model="form.sourceType" placeholder="请选择类型">
            <el-option label="本地文件" value="LOCAL_FILE" />
            <el-option label="远程日志" value="REMOTE" />
            <el-option label="数据库" value="DATABASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径" prop="path">
          <el-input v-model="form.path" placeholder="日志文件路径，如 /var/log/app.log" />
        </el-form-item>
        <el-form-item label="编码" prop="encoding">
          <el-select v-model="form.encoding" placeholder="选择编码">
            <el-option label="UTF-8" value="UTF-8" />
            <el-option label="GBK" value="GBK" />
            <el-option label="GB2312" value="GB2312" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志格式" prop="logFormat">
          <el-select v-model="form.logFormat" placeholder="选择日志格式" @change="handleLogFormatChange">
            <el-option label="Spring Boot" value="SPRING_BOOT" />
            <el-option label="Log4j" value="LOG4J" />
            <el-option label="Nginx" value="NGINX" />
            <el-option label="JSON" value="JSON" />
            <el-option label="普通文本" value="PLAIN_TEXT" />
            <el-option label="自定义正则" value="CUSTOM" />
          </el-select>
          <span class="form-tip">选择日志格式以支持多行日志（如Java堆栈）合并</span>
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
      </el-tabs>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, Refresh, Edit, Delete, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { logSourceApi } from '@/api'

const sources = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const activeTab = ref('basic')

const form = ref({
  id: null,
  name: '',
  sourceType: 'LOCAL_FILE',
  path: '',
  encoding: 'UTF-8',
  logFormat: 'SPRING_BOOT',
  customPattern: '',
  description: ''
})

const rules = {
  name: [{ required: true, message: '请输入采集源名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入日志文件路径', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const getStatusType = (status) => {
  const map = {
    'STOPPED': 'info',
    'COLLECTING': 'success',
    'ERROR': 'danger'
  }
  return map[status] || 'info'
}

const getLogFormatText = (format) => {
  const map = {
    'SPRING_BOOT': 'Spring Boot',
    'LOG4J': 'Log4j',
    'NGINX': 'Nginx',
    'JSON': 'JSON',
    'PLAIN_TEXT': '文本',
    'CUSTOM': '自定义'
  }
  return map[format] || 'Spring Boot'
}

const getStatusText = (status) => {
  const map = {
    'STOPPED': '已停止',
    'COLLECTING': '采集中',
    'ERROR': '错误'
  }
  return map[status] || '未知'
}

const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

// 日志格式变更处理
const handleLogFormatChange = (value) => {
  if (value !== 'CUSTOM') {
    form.value.customPattern = ''
  }
}

const loadSources = async () => {
  loading.value = true
  try {
    const res = await logSourceApi.getAll()
    sources.value = res.data || []
  } catch (error) {
    console.error('加载采集源失败:', error)
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  isEdit.value = false
  form.value = {
    id: null,
    name: '',
    sourceType: 'LOCAL_FILE',
    path: '',
    encoding: 'UTF-8',
    logFormat: 'SPRING_BOOT',
    customPattern: '',
    description: '',
    desensitizationEnabled: false,
    enabledRuleIds: [],
    customRules: []
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  form.value = { 
    ...row,
    desensitizationEnabled: row.desensitizationEnabled || false,
    enabledRuleIds: row.enabledRuleIds || [],
    customRules: row.customRules || []
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
    if (isEdit.value) {
      await logSourceApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await logSourceApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadSources()
  } catch (error) {
    console.error('保存失败:', error)
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
  }
}

const handleStop = async (row) => {
  try {
    await logSourceApi.stopCollector(row.id)
    ElMessage.success('采集器已停止')
    loadSources()
  } catch (error) {
    console.error('停止失败:', error)
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

.table-card {
  min-height: 500px;
}

.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
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
