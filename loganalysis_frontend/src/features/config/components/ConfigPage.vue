<template>
  <div class="system-config-container">
    <el-tabs v-model="activeTab" class="config-tabs" tab-position="top" stretch>
      <!-- 通知渠道配置 -->
      <el-tab-pane label="通知渠道" name="channel">
        <ConfigChannelTab
          :channel-list="channelList"
          :loading-channels="loadingChannels"
          :saving="saving"
          :testing-feishu="testingFeishu"
          :testing-dingtalk="testingDingtalk"
          :testing-wechat-work="testingWechatWork"
          @save-channels="handleSaveChannels"
          @test-feishu="handleTestFeishu"
          @test-dingtalk="handleTestDingtalk"
          @test-wechat="handleTestWechatWork"
        />
      </el-tab-pane>

      <!-- LLM 配置 -->
      <el-tab-pane label="LLM配置" name="ai-llm">
        <div class="ai-llm-config-panel">
          <ConfigAnalysisTab
            :analysis-config-form="analysisConfigForm"
            :saving-analysis-config="savingAnalysisConfig"
            @save-analysis-config="saveAnalysisConfig"
          />
          <ConfigLlmTab
            :llm-config-list="llmConfigList"
            :loading-llm="loadingLlm"
            :active-config-name="activeConfigName"
            @add-llm="handleAddLlm"
            @edit-llm="handleEditLlm"
            @set-default-llm="handleSetDefaultLlm"
            @delete-llm="handleDeleteLlm"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- LLM 配置对话框 -->
    <el-dialog v-model="llmDialogVisible" :title="isEditLlm ? '编辑配置' : '新增配置'" width="600px" destroy-on-close>
      <el-form :model="llmForm" :rules="llmRules" ref="llmFormRef" label-width="120px">
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="llmForm.name" placeholder="如: OpenAI GPT-4" />
        </el-form-item>
        <el-form-item label="API 端点" prop="endpoint">
          <el-input v-model="llmForm.endpoint" placeholder="如: https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="llmForm.apiKey" type="password" show-password placeholder="请输入 API Key" />
        </el-form-item>
        <el-form-item label="模型名称" prop="model">
          <el-input v-model="llmForm.model" placeholder="如: gpt-4, deepseek-chat" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大 Token">
              <el-input-number v-model="llmForm.maxTokens" :min="100" :max="32000" :step="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度参数">
              <el-input-number v-model="llmForm.temperature" :min="0" :max="2" :step="0.1" :precision="1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="超时时间(秒)">
          <el-input-number v-model="llmForm.timeout" :min="10" :max="120" :step="5" />
        </el-form-item>
        <el-form-item label="开启思考">
          <el-switch v-model="llmForm.thinkingEnabled" />
        </el-form-item>
        <el-form-item label="思考强度">
          <el-select v-model="llmForm.reasoningEffort" style="width: 100%">
            <el-option label="最弱 (minimal)" value="minimal" />
            <el-option label="低 (low)" value="low" />
            <el-option label="中 (medium)" value="medium" />
            <el-option label="高 (high)" value="high" />
            <el-option label="最高 (xhigh)" value="xhigh" />
            <el-option label="关闭 (none)" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="llmForm.isDefault" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="llmForm.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="llmForm.remark" type="textarea" :rows="2" placeholder="可选备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="llmDialogVisible = false">取消</el-button>
        <el-button
          v-if="isEditLlm"
          type="success"
          plain
          @click="handleTestLlmConnection"
          :loading="testingLlmConnection"
          :disabled="submittingLlm"
        >
          保存并测试
        </el-button>
        <el-button type="primary" @click="handleSubmitLlm" :loading="submittingLlm">
          {{ isEditLlm ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { notificationChannelApi, feishuApi, dingtalkApi, wechatWorkApi } from '@/api/alertApi'
import { llmConfigApi, analysisConfigApi } from '@/api'
import ConfigChannelTab from './ConfigChannelTab.vue'
import ConfigAnalysisTab from './ConfigAnalysisTab.vue'
import ConfigLlmTab from './ConfigLlmTab.vue'
import '../styles/config-page.css'

const activeTab = ref('channel')

const loadingChannels = ref(false)
const saving = ref(false)
const testingFeishu = ref(null)
const testingDingtalk = ref(null)
const testingWechatWork = ref(null)
const channelList = ref([])
const channelOptions = ['EMAIL', 'DINGTALK', 'WECHAT', 'FEISHU', 'WEBHOOK']

const savingAnalysisConfig = ref(false)
const analysisConfigForm = ref({
  contextSize: 10,
  autoAnalysisSeverity: 'ERROR',
  autoAnalysisEnabled: true
})

const loadingLlm = ref(false)
const submittingLlm = ref(false)
const testingLlmConnection = ref(false)
const llmConfigList = ref([])
const llmDialogVisible = ref(false)
const isEditLlm = ref(false)
const llmFormRef = ref(null)

const llmForm = ref({
  id: null,
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

const llmRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入 API 端点', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  model: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

const activeConfigName = computed(() => {
  const defaultConfig = llmConfigList.value.find(c => c.isDefault)
  const enabledConfig = llmConfigList.value.find(c => c.enabled)
  return defaultConfig?.name || enabledConfig?.name || '未配置'
})

const loadAnalysisConfig = async () => {
  try {
    const res = await analysisConfigApi.get()
    if (res.data) {
      analysisConfigForm.value.contextSize = res.data.contextSize || 10
      analysisConfigForm.value.autoAnalysisSeverity = res.data.autoAnalysisSeverity || 'ERROR'
      analysisConfigForm.value.autoAnalysisEnabled = res.data.autoAnalysisEnabled !== false
    }
  } catch (error) {
    console.error('加载分析配置失败:', error)
  }
}

const saveAnalysisConfig = async () => {
  savingAnalysisConfig.value = true
  try {
    await analysisConfigApi.update(analysisConfigForm.value)
    ElMessage.success('配置保存成功')
  } catch (error) {
    console.error('保存分析配置失败:', error)
    ElMessage.error('保存失败')
  } finally {
    savingAnalysisConfig.value = false
  }
}

const fetchConfigs = async () => {
  loadingChannels.value = true
  try {
    const res = await notificationChannelApi.getAll()
    const configs = res.data || []
    channelList.value = channelOptions.map(channel => {
      const existing = configs.find(c => c.channel === channel)
      if (existing) {
        existing.configParams = existing.configParams ? JSON.parse(existing.configParams) : {}
        return existing
      }
      return { channel, enabled: false, configParams: {}, description: '' }
    })
  } catch (error) {
    console.error('获取渠道配置失败:', error)
  } finally {
    loadingChannels.value = false
  }
}

const handleSaveChannels = async () => {
  saving.value = true
  try {
    const data = channelList.value.map(item => ({
      ...item,
      configParams: item.configParams ? JSON.stringify(item.configParams) : null
    }))
    await notificationChannelApi.batchUpsert(data)
    ElMessage.success('保存成功')
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleTestFeishu = async (row) => {
  testingFeishu.value = row.channel
  try {
    const res = await feishuApi.testConnection({
      webhookUrl: row.configParams.webhookUrl,
      secret: row.configParams.secret
    })
    if (res.success) {
      ElMessage.success('飞书连接测试成功')
    } else {
      ElMessage.error(res.message || '飞书连接测试失败')
    }
  } catch (error) {
    ElMessage.error('飞书连接测试失败: ' + (error.message || '未知错误'))
  } finally {
    testingFeishu.value = null
  }
}

const handleTestDingtalk = async (row) => {
  testingDingtalk.value = row.channel
  try {
    const res = await dingtalkApi.testConnection({
      webhookUrl: row.configParams.webhookUrl,
      secret: row.configParams.secret
    })
    if (res.success) {
      ElMessage.success('钉钉连接测试成功')
    } else {
      ElMessage.error(res.message || '钉钉连接测试失败')
    }
  } catch (error) {
    ElMessage.error('钉钉连接测试失败: ' + (error.message || '未知错误'))
  } finally {
    testingDingtalk.value = null
  }
}

const handleTestWechatWork = async (row) => {
  testingWechatWork.value = row.channel
  try {
    const res = await wechatWorkApi.testConnection({
      webhookUrl: row.configParams.webhookUrl
    })
    if (res.success) {
      ElMessage.success('企业微信连接测试成功')
    } else {
      ElMessage.error(res.message || '企业微信连接测试失败')
    }
  } catch (error) {
    ElMessage.error('企业微信连接测试失败: ' + (error.message || '未知错误'))
  } finally {
    testingWechatWork.value = null
  }
}

const loadLlmConfigs = async () => {
  loadingLlm.value = true
  try {
    const res = await llmConfigApi.getAll()
    llmConfigList.value = res.data || []
  } catch (error) {
    console.error('加载 LLM 配置失败:', error)
    ElMessage.error('加载配置失败')
  } finally {
    loadingLlm.value = false
  }
}

const handleAddLlm = () => {
  isEditLlm.value = false
  llmForm.value = {
    id: null,
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
  }
  llmDialogVisible.value = true
}

const handleEditLlm = (row) => {
  isEditLlm.value = true
  // 如果有保存的 API Key（masked 形式），回填到表单供用户参考
  // 用户不改则保留原值，后端 apiKey 为空时不更新
  llmForm.value = {
    ...row,
    apiKey: row.maskedApiKey || '',
    thinkingEnabled: row.thinkingEnabled !== false,
    reasoningEffort: row.reasoningEffort || 'medium'
  }
  llmDialogVisible.value = true
}

const handleSubmitLlm = async () => {
  const valid = await llmFormRef.value.validate().catch(() => false)
  if (!valid) return

  submittingLlm.value = true
  try {
    if (isEditLlm.value) {
      await llmConfigApi.update(llmForm.value.id, llmForm.value)
      ElMessage.success('更新成功')
    } else {
      await llmConfigApi.create(llmForm.value)
      ElMessage.success('创建成功')
    }
    llmDialogVisible.value = false
    await loadLlmConfigs()
  } catch (error) {
    console.error('保存 LLM 配置失败:', error)
    ElMessage.error('保存失败')
  } finally {
    submittingLlm.value = false
  }
}

const handleTestLlmConnection = async () => {
  if (!isEditLlm.value || !llmForm.value.id) {
    ElMessage.warning('请先保存配置后再测试连接')
    return
  }

  const valid = await llmFormRef.value.validate().catch(() => false)
  if (!valid) return

  testingLlmConnection.value = true
  try {
    await llmConfigApi.update(llmForm.value.id, llmForm.value)
    const res = await llmConfigApi.validate(llmForm.value.id)
    if (res.data === true) {
      ElMessage.success('连接测试成功')
    } else {
      ElMessage.error('连接测试失败，请检查 API Key、模型和端点配置')
    }
  } catch (error) {
    console.error('LLM 连接测试失败:', error)
    ElMessage.error('测试连接失败: ' + (error.message || '未知错误'))
  } finally {
    testingLlmConnection.value = false
  }
}

const handleSetDefaultLlm = async (row) => {
  try {
    await llmConfigApi.update(row.id, { ...row, isDefault: true })
    ElMessage.success('已设为默认')
    await loadLlmConfigs()
  } catch (error) {
    console.error('设置默认失败:', error)
    ElMessage.error('设置失败')
  }
}

const handleDeleteLlm = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该配置吗?', '提示', { type: 'warning' })
    await llmConfigApi.delete(row.id)
    ElMessage.success('删除成功')
    await loadLlmConfigs()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchConfigs()
  loadLlmConfigs()
  loadAnalysisConfig()
})
</script>
