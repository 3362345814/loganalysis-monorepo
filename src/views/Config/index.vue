<template>
  <div class="channel-config-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>通知渠道配置</span>
          <el-button type="primary" @click="handleSave" :loading="saving">
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

      <el-table :data="channelList" v-loading="loading" style="width: 100%; margin-top: 20px;">
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
              >
                <template #prepend>Webhook URL</template>
              </el-input>
              <el-input
                v-model="row.configParams.secret"
                placeholder="请输入加签密钥(可选)"
                size="small"
                :disabled="!row.enabled"
                style="margin-top: 8px;"
              >
                <template #prepend>加签密钥</template>
              </el-input>
            </div>
            <div v-else-if="row.channel === 'WECHAT'">
              <el-input
                v-model="row.configParams.webhookUrl"
                placeholder="请输入企业微信 webhook 地址"
                size="small"
                :disabled="!row.enabled"
              >
                <template #prepend>Webhook URL</template>
              </el-input>
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
              >
                <template #prepend>Webhook URL</template>
              </el-input>
            </div>
            <div v-else-if="row.channel === 'SMS'">
              <el-input
                v-model="row.configParams.provider"
                placeholder="短信服务商"
                size="small"
                :disabled="!row.enabled"
                style="margin-bottom: 8px;"
              >
                <template #prepend>服务商</template>
              </el-input>
              <el-input
                v-model="row.configParams.apiKey"
                placeholder="API Key"
                size="small"
                :disabled="!row.enabled"
              >
                <template #prepend>API Key</template>
              </el-input>
            </div>
            <div v-else-if="row.channel === 'SLACK'">
              <el-input
                v-model="row.configParams.webhookUrl"
                placeholder="请输入 Slack webhook 地址"
                size="small"
                :disabled="!row.enabled"
              >
                <template #prepend>Webhook URL</template>
              </el-input>
            </div>
            <div v-else>
              <span style="color: #909399;">暂无配置</span>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { notificationChannelApi } from '@/api/alertApi'

const loading = ref(false)
const saving = ref(false)

const channelList = ref([])

const channelOptions = ['EMAIL', 'SMS', 'DINGTALK', 'WECHAT', 'FEISHU', 'SLACK', 'WEBHOOK']

// 获取渠道配置
const fetchConfigs = async () => {
  loading.value = true
  try {
    const res = await notificationChannelApi.getAll()
    const configs = res.data || []
    
    // 补全所有渠道
    channelList.value = channelOptions.map(channel => {
      const existing = configs.find(c => c.channel === channel)
      if (existing) {
        existing.configParams = existing.configParams ? JSON.parse(existing.configParams) : {}
        return existing
      }
      return {
        channel,
        enabled: false,
        configParams: {},
        description: ''
      }
    })
  } catch (error) {
    console.error('获取渠道配置失败:', error)
  } finally {
    loading.value = false
  }
}

// 保存配置
const handleSave = async () => {
  saving.value = true
  try {
    const data = channelList.value.map(item => ({
      ...item,
      configParams: item.configParams ? JSON.stringify(item.configParams) : null
    }))
    await notificationChannelApi.batchSave(data)
    ElMessage.success('保存成功')
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const getChannelText = (channel) => {
  const map = {
    EMAIL: '邮件',
    SMS: '短信',
    DINGTALK: '钉钉',
    WECHAT: '企微',
    FEISHU: '飞书',
    SLACK: 'Slack',
    WEBHOOK: 'Webhook'
  }
  return map[channel] || channel
}

onMounted(() => {
  fetchConfigs()
})
</script>

<style scoped>
.channel-config-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-tip {
  margin-bottom: 10px;
}
</style>
