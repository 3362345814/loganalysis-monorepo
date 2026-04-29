<template>
  <div class="llm-config-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>LLM 配置管理</h2>
      <p class="subtitle">配置 AI 分析所使用的语言模型服务</p>
    </div>

    <!-- 工具栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="12">
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增配置
          </el-button>
          <el-button @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </el-col>
        <el-col :span="12" class="toolbar-right">
          <el-tag type="info">当前活跃配置: {{ activeConfigName }}</el-tag>
        </el-col>
      </el-row>
    </el-card>

    <!-- 配置列表 -->
    <el-card class="table-card">
      <el-table :data="configList" v-loading="loading" :tooltip-options="tableTooltipOptions">
        <el-table-column prop="name" label="配置名称" width="150">
          <template #default="{ row }">
            <div class="config-name">
              {{ row.name }}
              <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" min-width="150" class-name="llm-model-cell" show-overflow-tooltip />
        <el-table-column prop="endpoint" label="API 端点" min-width="200" />
        <el-table-column label="参数" width="200">
          <template #default="{ row }">
            <span class="param-text">
              Max: {{ row.maxTokens }} | Temp: {{ row.temperature }} | 思考: {{ row.thinkingEnabled === false ? '关' : '开' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="llm-action-buttons">
              <el-button type="primary" link size="small" :icon="Edit" @click="handleEdit(row)">
                编辑
              </el-button>
              <el-button type="success" link size="small" :icon="Star" @click="handleSetDefault(row)" 
                :disabled="row.isDefault">
                设为默认
              </el-button>
              <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)"
                :disabled="configList.length <= 1">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 配置对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑配置' : '新增配置'"
      width="600px"
      destroy-on-close
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="form.name" placeholder="如: OpenAI GPT-4" />
        </el-form-item>
        
        <el-form-item label="API 端点" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="如: https://api.openai.com/v1" />
        </el-form-item>
        
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="请输入 API Key" />
        </el-form-item>
        
        <el-form-item label="模型名称" prop="model">
          <el-input v-model="form.model" placeholder="如: gpt-4, deepseek-chat" />
        </el-form-item>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大 Token">
              <el-input-number v-model="form.maxTokens" :min="100" :max="32000" :step="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度参数">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="超时时间(秒)">
          <el-input-number v-model="form.timeout" :min="10" :max="120" :step="5" />
        </el-form-item>

        <el-form-item label="开启思考">
          <el-switch v-model="form.thinkingEnabled" />
        </el-form-item>

        <el-form-item label="思考强度">
          <el-select v-model="form.reasoningEffort" style="width: 100%">
            <el-option label="最弱 (minimal)" value="minimal" />
            <el-option label="低 (low)" value="low" />
            <el-option label="中 (medium)" value="medium" />
            <el-option label="高 (high)" value="high" />
            <el-option label="最高 (xhigh)" value="xhigh" />
            <el-option label="关闭 (none)" value="none" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选备注信息" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTestConnection" :loading="testLoading">
          {{ isEdit ? '测试并更新' : '测试并创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Edit, Star, Delete } from '@element-plus/icons-vue'
import { llmConfigApi } from '@/api'

// 状态
const loading = ref(false)
const testLoading = ref(false)
const configList = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const tableTooltipOptions = {
  popperClass: 'limited-table-tooltip',
  enterable: true
}

// 表单数据
const form = ref({
  name: '',
  apiKey: '',
  model: '',
  maxTokens: 2000,
  temperature: 0.3,
  timeout: 30,
  thinkingEnabled: true,
  reasoningEffort: 'medium',
  endpoint: '',
  isDefault: false,
  enabled: true,
  remark: ''
})

// 表单验证规则
const rules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入 API 端点', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  model: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

// 当前活跃配置名称
const activeConfigName = computed(() => {
  const defaultConfig = configList.value.find(c => c.isDefault)
  const enabledConfig = configList.value.find(c => c.enabled)
  return defaultConfig?.name || enabledConfig?.name || '未配置'
})

// 加载配置列表
const loadData = async () => {
  loading.value = true
  try {
    const res = await llmConfigApi.getAll()
    configList.value = res.data || []
  } catch (error) {
    console.error('加载配置失败:', error)
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

// 刷新
const handleRefresh = () => {
  loadData()
  ElMessage.success('刷新成功')
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  form.value = {
    name: '',
    apiKey: '',
    model: '',
    maxTokens: 2000,
    temperature: 0.3,
    timeout: 30,
    thinkingEnabled: true,
    reasoningEffort: 'medium',
    endpoint: '',
    isDefault: configList.value.length === 0,
    enabled: true,
    remark: ''
  }
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  isEdit.value = true
  // 如果有保存的 API Key（masked 形式），回填到表单供用户参考
  // 用户不改则保留原值，后端 apiKey 为空时不更新
  form.value = {
    id: row.id,
    name: row.name,
    apiKey: row.maskedApiKey || '',
    model: row.model,
    maxTokens: row.maxTokens,
    temperature: row.temperature,
    timeout: row.timeout,
    thinkingEnabled: row.thinkingEnabled !== false,
    reasoningEffort: row.reasoningEffort || 'medium',
    endpoint: row.endpoint,
    isDefault: row.isDefault,
    enabled: row.enabled,
    remark: row.remark
  }
  dialogVisible.value = true
}

// 设为默认
const handleSetDefault = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要将 "${row.name}" 设为默认配置吗?`, '提示', {
      type: 'warning'
    })
    
    await llmConfigApi.update(row.id, { ...row, isDefault: true })
    ElMessage.success('设置成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('设置失败')
    }
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除配置 "${row.name}" 吗?`, '警告', {
      type: 'error'
    })
    
    await llmConfigApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 测试并保存配置（新增时先创建，编辑时先更新）
const handleTestConnection = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  testLoading.value = true
  try {
    const editing = isEdit.value
    let configId = form.value.id

    if (editing && configId) {
      await llmConfigApi.update(configId, form.value)
    } else {
      const createRes = await llmConfigApi.create(form.value)
      configId = createRes?.data?.id
      if (!configId) {
        await loadData()
        const matched = configList.value.find(item =>
          item.name === form.value.name &&
          item.model === form.value.model &&
          item.endpoint === form.value.endpoint
        )
        configId = matched?.id
      }
      if (!configId) {
        throw new Error('创建成功但未获取到配置ID，无法执行连接测试')
      }
      form.value = { ...form.value, id: configId }
      isEdit.value = true
    }

    const res = await llmConfigApi.validate(configId)
    if (res.data === true) {
      ElMessage.success(editing ? '测试并更新成功' : '测试并创建成功')
      dialogVisible.value = false
    } else {
      ElMessage.error('连接测试失败，请检查 API Key、模型和端点配置')
    }
    await loadData()
  } catch (error) {
    console.error('测试连接失败:', error)
    ElMessage.error('测试连接失败: ' + (error.message || '未知错误'))
  } finally {
    testLoading.value = false
  }
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped src="../styles/llm-config-page.css"></style>
