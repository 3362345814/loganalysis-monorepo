<template>
  <el-card class="tab-content-card animate-fade-in-up">
    <template #header>
      <div class="card-header">
        <span>通知渠道配置</span>
        <el-button type="primary" @click="emit('save-channels')" :loading="saving">
          保存配置
        </el-button>
      </div>
    </template>

    <div class="config-tip">
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          <span>启用渠道后，告警规则才能选择对应的通知渠道。未启用的渠道不会在告警规则中显示。密钥类字段留空或保持掩码时，将保留旧值。</span>
        </template>
      </el-alert>
    </div>

    <el-table :data="channelList" v-loading="loadingChannels" class="channel-table">
      <el-table-column prop="channel" label="渠道" width="120">
        <template #default="{ row }">
          <span class="channel-name">{{ getChannelText(row.channel) }}</span>
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
              :disabled="!row.enabled"
              class="field-spacing-8"
            >
              <template #prepend>Webhook URL</template>
            </el-input>
            <el-input
              v-model="row.configParams.secret"
              placeholder="加签密钥（留空或保留掩码则不变）"
              type="password"
              show-password
              :disabled="!row.enabled"
              class="field-spacing-8"
            >
              <template #prepend>加签密钥</template>
            </el-input>
            <div class="inline-flex-row">
              <el-input v-model="row.configParams.recipients" placeholder="接收人，多个用逗号分隔(可选)">
                <template #prepend>接收人</template>
              </el-input>
              <el-button
                type="primary"
                :disabled="!row.enabled || !row.configParams.webhookUrl"
                :loading="testingDingtalk === row.channel"
                @click="emit('test-dingtalk', row)"
              >
                测试连接
              </el-button>
            </div>
          </div>

          <div v-else-if="row.channel === 'WECHAT'">
            <div class="inline-flex-row">
              <el-input
                v-model="row.configParams.webhookUrl"
                placeholder="请输入企业微信 webhook 地址"
                :disabled="!row.enabled"
              >
                <template #prepend>Webhook URL</template>
              </el-input>
              <el-button
                type="primary"
                :disabled="!row.enabled || !row.configParams.webhookUrl"
                :loading="testingWechatWork === row.channel"
                @click="emit('test-wechat', row)"
              >
                测试连接
              </el-button>
            </div>
          </div>

          <div v-else-if="row.channel === 'EMAIL'">
            <el-input
              v-model="row.configParams.smtpHost"
              placeholder="SMTP 服务器地址"
              :disabled="!row.enabled"
              class="field-spacing-8"
            >
              <template #prepend>SMTP</template>
            </el-input>
            <div class="smtp-row">
              <el-input
                v-model="row.configParams.smtpPort"
                placeholder="端口"
                :disabled="!row.enabled"
                class="smtp-port-input"
              />
              <el-input
                v-model="row.configParams.username"
                placeholder="用户名"
                :disabled="!row.enabled"
                class="smtp-username-input"
              />
              <el-input
                v-model="row.configParams.password"
                placeholder="密码（留空或保留掩码则不变）"
                type="password"
                show-password
                :disabled="!row.enabled"
                class="smtp-password-input"
              />
            </div>
            <el-input
              v-model="row.configParams.from"
              placeholder="发件人邮箱"
              :disabled="!row.enabled"
              class="email-from-input"
            >
              <template #prepend>发件人</template>
            </el-input>
            <el-input
              v-model="row.configParams.recipients"
              placeholder="收件人邮箱，多个用逗号分隔"
              :disabled="!row.enabled"
            >
              <template #prepend>收件人</template>
            </el-input>
          </div>

          <div v-else-if="row.channel === 'WEBHOOK'">
            <el-input
              v-model="row.configParams.webhookUrl"
              placeholder="请输入 Webhook 地址"
              :disabled="!row.enabled"
            >
              <template #prepend>Webhook URL</template>
            </el-input>
          </div>

          <div v-else-if="row.channel === 'FEISHU'">
            <el-input
              v-model="row.configParams.webhookUrl"
              placeholder="请输入飞书 webhook 地址"
              :disabled="!row.enabled"
              class="field-spacing-8"
            >
              <template #prepend>Webhook URL</template>
            </el-input>
            <el-input
              v-model="row.configParams.secret"
              placeholder="飞书加签密钥（留空或保留掩码则不变）"
              type="password"
              show-password
              :disabled="!row.enabled"
              class="field-spacing-8"
            >
              <template #prepend>加签密钥</template>
            </el-input>
            <div class="inline-flex-row">
              <el-input v-model="row.configParams.recipients" placeholder="接收人，多个用逗号分隔(可选)">
                <template #prepend>接收人</template>
              </el-input>
              <el-button
                type="primary"
                :disabled="!row.enabled || !row.configParams.webhookUrl"
                :loading="testingFeishu === row.channel"
                @click="emit('test-feishu', row)"
              >
                测试连接
              </el-button>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.description" placeholder="请输入描述" :disabled="!row.enabled" />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import '../styles/config-channel-tab.css'

defineProps({
  channelList: {
    type: Array,
    default: () => []
  },
  loadingChannels: {
    type: Boolean,
    default: false
  },
  saving: {
    type: Boolean,
    default: false
  },
  testingFeishu: {
    default: null
  },
  testingDingtalk: {
    default: null
  },
  testingWechatWork: {
    default: null
  }
})

const emit = defineEmits(['save-channels', 'test-feishu', 'test-dingtalk', 'test-wechat'])

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
</script>
