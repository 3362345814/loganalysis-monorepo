<template>
  <div class="rule-manage-container">
    <!-- 项目选择器 -->
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="项目">
          <el-select v-model="selectedProjectId" placeholder="全部项目" clearable style="width: 200px" @change="handleProjectChange">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 规则列表 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>告警规则管理</span>
          <div class="header-actions">
            <el-alert
              v-if="enabledChannels.length === 0"
              type="warning"
              :closable="false"
              class="channel-warning-alert"
            >
              请先在"系统配置"中配置通知渠道
            </el-alert>
            <el-button type="primary" @click="handleCreate" :disabled="enabledChannels.length === 0">
              <el-icon><Plus /></el-icon>创建规则
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="ruleList" v-loading="loading">
        <el-table-column prop="name" label="规则名称" min-width="150" />
        <el-table-column prop="projectId" label="项目" width="150">
          <template #default="{ row }">
            <span>{{ getProjectName(row.projectId) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ruleType" label="规则类型" width="120">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ getRuleTypeText(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="conditionExpression" label="触发条件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="alertLevel" label="告警级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.alertLevel)" size="small">
              {{ getLevelText(row.alertLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="notificationChannels" label="通知渠道" width="150">
          <template #default="{ row }">
            <el-tag
              v-for="channel in row.notificationChannels"
              :key="channel"
              type="info"
              size="small"
              class="channel-tag"
            >
              {{ getChannelText(channel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              @change="handleToggle(row)"
              :loading="row.toggleLoading"
            />
          </template>
        </el-table-column>
        <el-table-column prop="triggerCountToday" label="今日触发" width="90" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        class="list-pagination"
      />
    </el-card>

    <!-- 创建/编辑规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑规则' : '创建规则'"
      width="700px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="项目" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择所属项目" style="width: 100%">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入规则描述" />
        </el-form-item>
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="form.ruleType" placeholder="请选择规则类型" style="width: 100%">
            <el-option label="关键词匹配" value="KEYWORD" />
            <el-option label="正则表达式" value="REGEX" />
            <el-option label="日志级别" value="LEVEL" />
            <el-option label="阈值检测" value="THRESHOLD" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发条件" prop="conditionExpression">
          <el-input v-model="form.conditionExpression" placeholder="请输入触发条件">
            <template #append>
              <el-tooltip content="关键词: 直接输入关键词&#13;正则: 使用正则表达式&#13;级别: ERROR, WARN&#13;阈值: ERROR > 100">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip" v-if="form.ruleType === 'KEYWORD'">例如: NullPointerException, Connection refused</div>
          <div class="form-tip" v-if="form.ruleType === 'REGEX'">例如: (Exception|Error).*</div>
          <div class="form-tip" v-if="form.ruleType === 'LEVEL'">例如: ERROR, WARN, ERROR,WARN</div>
          <div class="form-tip" v-if="form.ruleType === 'THRESHOLD'">例如: ERROR > 100 (5分钟内超过100条ERROR)</div>
        </el-form-item>
        <el-form-item label="告警级别" prop="alertLevel">
          <el-select v-model="form.alertLevel" placeholder="请选择告警级别" style="width: 100%">
            <el-option label="严重 (CRITICAL)" value="CRITICAL" />
            <el-option label="高 (HIGH)" value="HIGH" />
            <el-option label="中 (MEDIUM)" value="MEDIUM" />
            <el-option label="低 (LOW)" value="LOW" />
            <el-option label="信息 (INFO)" value="INFO" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警标题" prop="alertTitle">
          <el-input v-model="form.alertTitle" placeholder="请输入告警标题" />
        </el-form-item>
        <el-form-item label="告警消息" prop="alertMessage">
          <el-input v-model="form.alertMessage" type="textarea" :rows="3" placeholder="请输入告警消息模板" />
        </el-form-item>
        <el-form-item label="通知渠道" prop="notificationChannels">
          <el-checkbox-group v-model="form.notificationChannels">
            <el-checkbox label="EMAIL" :disabled="!enabledChannels.includes('EMAIL')">邮件</el-checkbox>
            <el-checkbox label="DINGTALK" :disabled="!enabledChannels.includes('DINGTALK')">钉钉</el-checkbox>
            <el-checkbox label="WECHAT" :disabled="!enabledChannels.includes('WECHAT')">企业微信</el-checkbox>
            <el-checkbox label="FEISHU" :disabled="!enabledChannels.includes('FEISHU')">飞书</el-checkbox>
            <el-checkbox label="WEBHOOK" :disabled="!enabledChannels.includes('WEBHOOK')">Webhook</el-checkbox>
          </el-checkbox-group>
          <div class="form-tip" v-if="enabledChannels.length === 0">
            请先在"系统配置"中配置并启用通知渠道
          </div>
        </el-form-item>
        <el-form-item label="冷却时间" prop="cooldownMinutes">
          <el-input-number v-model="form.cooldownMinutes" :min="1" :max="1440" />
          <span class="form-unit">分钟</span>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, QuestionFilled } from '@element-plus/icons-vue'
import { alertRuleApi, notificationChannelApi } from '@/api/alertApi'
import { projectApi } from '@/api'

// 项目列表
const projects = ref([])
const selectedProjectId = ref('')

// 规则列表
const ruleList = ref([])
const loading = ref(false)

// 已启用的通知渠道
const enabledChannels = ref([])

// 获取已启用的通知渠道
const fetchEnabledChannels = async () => {
  try {
    const res = await notificationChannelApi.getEnabled()
    enabledChannels.value = (res.data || []).map(c => c.channel)
  } catch (error) {
    console.error('获取启用的通知渠道失败:', error)
    // 默认全部启用
    enabledChannels.value = ['EMAIL', 'DINGTALK', 'WECHAT', 'FEISHU']
  }
}

// 分页
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

// 表单
const form = reactive({
  id: null,
  projectId: '',
  name: '',
  description: '',
  ruleType: 'KEYWORD',
  conditionExpression: '',
  alertLevel: 'HIGH',
  alertTitle: '',
  alertMessage: '',
  notificationChannels: [],
  cooldownMinutes: 10,
  enabled: true
})

// 表单验证
const rules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  conditionExpression: [{ required: true, message: '请输入触发条件', trigger: 'blur' }],
  alertLevel: [{ required: true, message: '请选择告警级别', trigger: 'change' }],
  notificationChannels: [
    { 
      validator: (rule, value, callback) => {
        if (enabledChannels.value.length > 0 && (!value || value.length === 0)) {
          callback(new Error('请至少选择一个通知渠道'))
        } else {
          callback()
        }
      }, 
      trigger: 'change' 
    }
  ]
}

// 获取规则列表
const fetchRuleList = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      ...(selectedProjectId.value && { projectId: selectedProjectId.value })
    }
    const res = await alertRuleApi.getPage(params)
    ruleList.value = res.data.content || []
    pagination.total = res.data.totalElements || 0
  } catch (error) {
    console.error('获取规则列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取项目列表
const fetchProjects = async () => {
  try {
    const res = await projectApi.getAll()
    projects.value = res.data || []
  } catch (error) {
    console.error('获取项目列表失败:', error)
  }
}

// 项目选择变化
const handleProjectChange = () => {
  pagination.page = 1
  fetchRuleList()
}

// 创建规则
const handleCreate = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑规则
const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    projectId: row.projectId || '',
    name: row.name,
    description: row.description || '',
    ruleType: row.ruleType,
    conditionExpression: row.conditionExpression,
    alertLevel: row.alertLevel,
    alertTitle: row.alertTitle || '',
    alertMessage: row.alertMessage || '',
    notificationChannels: row.notificationChannels || [],
    cooldownMinutes: row.cooldownMinutes || 10,
    enabled: row.enabled
  })
  dialogVisible.value = true
}

// 删除规则
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除此规则吗？', '提示', {
      type: 'warning'
    })
    await alertRuleApi.delete(row.id)
    ElMessage.success('删除成功')
    fetchRuleList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 启用/禁用规则
const handleToggle = async (row) => {
  row.toggleLoading = true
  try {
    await alertRuleApi.toggle(row.id)
    ElMessage.success(row.enabled ? '规则已启用' : '规则已禁用')
  } catch (error) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败')
  } finally {
    row.toggleLoading = false
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (isEdit.value) {
          await alertRuleApi.update(form.id, form)
          ElMessage.success('更新成功')
        } else {
          await alertRuleApi.create(form)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        fetchRuleList()
      } catch (error) {
        ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
    id: null,
    projectId: selectedProjectId.value || '',
    name: '',
    description: '',
    ruleType: 'KEYWORD',
    conditionExpression: '',
    alertLevel: 'HIGH',
    alertTitle: '',
    alertMessage: '',
    notificationChannels: [],
    cooldownMinutes: 10,
    enabled: true
  })
}

// 分页变化
const handlePageChange = (page) => {
  pagination.page = page
  fetchRuleList()
}

const handleSizeChange = (size) => {
  pagination.size = size
  fetchRuleList()
}

// 工具函数
const getRuleTypeText = (type) => {
  const map = {
    KEYWORD: '关键词',
    REGEX: '正则',
    LEVEL: '级别',
    THRESHOLD: '阈值',
    COMBINATION: '组合'
  }
  return map[type] || type
}

const getLevelType = (level) => {
  const map = {
    CRITICAL: 'danger',
    HIGH: 'warning',
    MEDIUM: 'info',
    LOW: 'success',
    INFO: 'info'
  }
  return map[level] || 'info'
}

const getLevelText = (level) => {
  const map = {
    CRITICAL: '严重',
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低',
    INFO: '信息'
  }
  return map[level] || level
}

const getChannelText = (channel) => {
  const channelMap = {
    EMAIL: '邮件',
    DINGTALK: '钉钉',
    WECHAT: '企微',
    FEISHU: '飞书',
    WEBHOOK: 'Webhook'
  }
  return channelMap[channel] || channel
}

const getProjectName = (projectId) => {
  if (!projectId) return '-'
  const project = projects.value.find(p => p.id === projectId)
  return project ? project.name : '-'
}

onMounted(() => {
  fetchProjects()
  fetchRuleList()
  fetchEnabledChannels()
})
</script>

<style scoped>
.rule-manage-container {
  padding: var(--space-24);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-16) var(--space-24);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.header-actions {
  display: flex;
  align-items: center;
}

.channel-warning-alert {
  margin-right: var(--space-12);
  padding: var(--space-4) var(--space-12);
}

.channel-tag {
  margin-right: var(--space-4);
}

.list-pagination {
  margin-top: var(--space-24);
  justify-content: flex-end;
}

.form-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: var(--space-4);
}

.form-unit {
  margin-left: var(--space-8);
  color: var(--text-secondary);
}
</style>
