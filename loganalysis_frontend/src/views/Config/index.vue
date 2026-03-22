<template>
  <div class="system-config-container">
    <el-tabs v-model="activeTab" class="config-tabs" tab-position="top" stretch>
      <!-- 通知渠道配置 -->
      <el-tab-pane label="通知渠道" name="channel">
        <el-card class="tab-content-card animate-fade-in-up">
          <template #header>
            <div class="card-header">
              <span>通知渠道配置</span>
              <el-button type="primary" @click="handleSaveChannels" :loading="saving">
                <el-icon><Check /></el-icon>保存配置
              </el-button>
            </div>
          </template>

          <div class="config-tip">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                <span>启用渠道后，告警规则才能选择对应的通知渠道。未启用的渠道不会在告警规则中显示。</span>
              </template>
            </el-alert>
          </div>

          <el-table :data="channelList" v-loading="loadingChannels" style="width: 100%; margin-top: 20px;">
            <el-table-column prop="channel" label="渠道" width="120">
              <template #default="{ row }">
                <el-tag>{{ getChannelText(row.channel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="启用状态" width="120">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" />
              </template>
            </el-table-column>
            <el-table-column label="配置参数" min-width="300">
              <template #default="{ row }">
                <div v-if="row.channel === 'DINGTALK'">
                  <el-input
                    v-model="row.configParams.webhookUrl"
                    placeholder="请输入钉钉 webhook 地址"
                    size="small"
                    :disabled="!row.enabled"
                    style="margin-bottom: 8px;"
                  >
                    <template #prepend>Webhook URL</template>
                  </el-input>
                  <el-input
                    v-model="row.configParams.secret"
                    placeholder="请输入加签密钥(可选)"
                    size="small"
                    :disabled="!row.enabled"
                    style="margin-bottom: 8px;"
                  >
                    <template #prepend>加签密钥</template>
                  </el-input>
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <el-input
                      v-model="row.configParams.recipients"
                      placeholder="接收人，多个用逗号分隔(可选)"
                      size="small"
                    >
                      <template #prepend>接收人</template>
                    </el-input>
                    <el-button
                      size="small"
                      type="primary"
                      :disabled="!row.enabled || !row.configParams.webhookUrl"
                      :loading="testingDingtalk === row.channel"
                      @click="handleTestDingtalk(row)"
                    >
                      测试连接
                    </el-button>
                  </div>
                </div>
                <div v-else-if="row.channel === 'WECHAT'">
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <el-input
                      v-model="row.configParams.webhookUrl"
                      placeholder="请输入企业微信 webhook 地址"
                      size="small"
                      :disabled="!row.enabled"
                    >
                      <template #prepend>Webhook URL</template>
                    </el-input>
                    <el-button
                      size="small"
                      type="primary"
                      :disabled="!row.enabled || !row.configParams.webhookUrl"
                      :loading="testingWechatWork === row.channel"
                      @click="handleTestWechatWork(row)"
                    >
                      测试连接
                    </el-button>
                  </div>
                </div>
                <div v-else-if="row.channel === 'EMAIL'">
                  <el-input
                    v-model="row.configParams.smtpHost"
                    placeholder="SMTP 服务器地址"
                    size="small"
                    :disabled="!row.enabled"
                    style="margin-bottom: 8px;"
                  >
                    <template #prepend>SMTP</template>
                  </el-input>
                  <el-input
                    v-model="row.configParams.smtpPort"
                    placeholder="端口"
                    size="small"
                    :disabled="!row.enabled"
                    style="width: 120px; margin-right: 8px;"
                  />
                  <el-input
                    v-model="row.configParams.username"
                    placeholder="用户名"
                    size="small"
                    :disabled="!row.enabled"
                    style="width: 150px; margin-right: 8px;"
                  />
                  <el-input
                    v-model="row.configParams.password"
                    placeholder="密码"
                    type="password"
                    show-password
                    size="small"
                    :disabled="!row.enabled"
                    style="width: 150px;"
                  />
                  <el-input
                    v-model="row.configParams.from"
                    placeholder="发件人邮箱"
                    size="small"
                    :disabled="!row.enabled"
                    style="margin-top: 8px; margin-bottom: 8px;"
                  >
                    <template #prepend>发件人</template>
                  </el-input>
                  <el-input
                    v-model="row.configParams.recipients"
                    placeholder="收件人邮箱，多个用逗号分隔"
                    size="small"
                    :disabled="!row.enabled"
                  >
                    <template #prepend>收件人</template>
                  </el-input>
                </div>
                <div v-else-if="row.channel === 'WEBHOOK'">
                  <el-input
                    v-model="row.configParams.webhookUrl"
                    placeholder="请输入 Webhook 地址"
                    size="small"
                    :disabled="!row.enabled"
                  >
                    <template #prepend>Webhook URL</template>
                  </el-input>
                </div>
                <div v-else-if="row.channel === 'FEISHU'">
                  <el-input
                    v-model="row.configParams.webhookUrl"
                    placeholder="请输入飞书 webhook 地址"
                    size="small"
                    :disabled="!row.enabled"
                    style="margin-bottom: 8px;"
                  >
                    <template #prepend>Webhook URL</template>
                  </el-input>
                  <el-input
                    v-model="row.configParams.secret"
                    placeholder="请输入飞书机器人加签密钥(可选)"
                    size="small"
                    :disabled="!row.enabled"
                    style="margin-bottom: 8px;"
                  >
                    <template #prepend>加签密钥</template>
                  </el-input>
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <el-input
                      v-model="row.configParams.recipients"
                      placeholder="接收人，多个用逗号分隔(可选)"
                      size="small"
                    >
                      <template #prepend>接收人</template>
                    </el-input>
                    <el-button
                      size="small"
                      type="primary"
                      :disabled="!row.enabled || !row.configParams.webhookUrl"
                      :loading="testingFeishu === row.channel"
                      @click="handleTestFeishu(row)"
                    >
                      测试连接
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="150">
              <template #default="{ row }">
                <el-input
                  v-model="row.description"
                  placeholder="请输入描述"
                  size="small"
                  :disabled="!row.enabled"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- AI分析配置 -->
      <el-tab-pane label="AI分析配置" name="ai">
        <el-card class="tab-content-card animate-fade-in-up">
          <template #header>
            <div class="card-header">
              <span>AI分析配置</span>
            </div>
          </template>

          <el-form :model="analysisConfigForm" label-width="120px" style="max-width: 500px;">
            <el-form-item label="上下文行数">
              <el-input-number v-model="analysisConfigForm.contextSize" :min="10" :max="30" />
              <span style="margin-left: 10px; color: #909399">行 (10-30)</span>
            </el-form-item>
            <el-form-item label="自动分析级别">
              <el-select v-model="analysisConfigForm.autoAnalysisSeverity" style="width: 100%">
                <el-option label="ERROR 及以上" value="ERROR" />
                <el-option label="WARNING 及以上" value="WARNING" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用自动分析">
              <el-switch v-model="analysisConfigForm.autoAnalysisEnabled" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveAnalysisConfig" :loading="savingAnalysisConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- LLM 配置 -->
      <el-tab-pane label="LLM 配置" name="llm">
        <el-card class="tab-content-card animate-fade-in-up">
          <template #header>
            <div class="card-header">
              <span>LLM 配置管理</span>
              <div>
                <el-tag type="info" style="margin-right: 10px;">当前活跃: {{ activeConfigName }}</el-tag>
                <el-button type="primary" @click="handleAddLlm">
                  <el-icon><Plus /></el-icon>新增配置
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="llmConfigList" v-loading="loadingLlm" stripe style="margin-top: 10px;">
            <el-table-column prop="name" label="配置名称" width="150">
              <template #default="{ row }">
                <div class="config-name">
                  {{ row.name }}
                  <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="model" label="模型" min-width="150" />
            <el-table-column prop="endpoint" label="API 端点" min-width="200" show-overflow-tooltip />
            <el-table-column label="参数" width="200">
              <template #default="{ row }">
                <span class="param-text">
                  MaxTokens: {{ row.maxTokens }} | Temp: {{ row.temperature }}
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
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditLlm(row)">
                  编辑
                </el-button>
                <el-button type="success" link size="small" @click="handleSetDefaultLlm(row)" :disabled="row.isDefault">
                  设为默认
                </el-button>
                <el-button type="danger" link size="small" @click="handleDeleteLlm(row)" :disabled="llmConfigList.length <= 1">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
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
import { Check, Plus } from '@element-plus/icons-vue'
import { notificationChannelApi, feishuApi, dingtalkApi, wechatWorkApi } from '@/api/alertApi'
import { llmConfigApi, analysisConfigApi } from '@/api'

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

const getChannelText = (channel) => {
  const map = {
    EMAIL: '邮件',
    DINGTALK: '钉钉',
    WECHAT: '企微',
    FEISHU: '飞书',
    WEBHOOK: 'Webhook'
  }
  return map[channel] || channel
}

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
    endpoint: '',
    isDefault: false,
    enabled: true,
    remark: ''
  }
  llmDialogVisible.value = true
}

const handleEditLlm = (row) => {
  isEditLlm.value = true
  llmForm.value = { ...row }
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

<style scoped>
.system-config-container {
  padding: 20px;
}

.config-tabs {
  background: transparent;
}

:deep(.el-tabs__header) {
  background: transparent;
  margin-bottom: 0;
}

:deep(.el-tabs__nav-wrap) {
  background: transparent;
}

:deep(.el-tabs__nav-scroll) {
  background: transparent;
  display: flex;
  justify-content: center;
}

:deep(.el-tabs__content) {
  background: transparent;
}

:deep(.el-tab-pane) {
  background: transparent;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-tip {
  margin-bottom: 10px;
}

.config-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.param-text {
  font-size: 12px;
  color: #909399;
}

.tab-content-card {
  animation-fill-mode: forwards;
}

.tab-fade-enter-active,
.tab-fade-leave-active {
  transition: opacity var(--transition-normal), transform var(--transition-normal);
}

.tab-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.tab-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
