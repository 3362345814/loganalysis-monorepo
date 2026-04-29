<template>
  <el-card class="tab-content-card animate-fade-in-up">
    <template #header>
      <div class="card-header">
        <div class="header-title-group">
          <span>通知渠道配置</span>
          <el-tooltip
            effect="light"
            placement="right"
            :show-after="200"
            content="启用渠道后，告警规则才能选择对应的通知渠道。未启用的渠道不会在告警规则中显示。密钥类字段留空或保持掩码时，将保留旧值。"
          >
            <el-icon class="header-tip-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </div>
        <el-button type="primary" @click="emit('save-channels')" :loading="saving">
          保存配置
        </el-button>
      </div>
    </template>

    <el-table :data="channelList" v-loading="loadingChannels" class="channel-table">
      <el-table-column prop="channel" label="渠道" width="130">
        <template #default="{ row }">
          <div class="channel-info">
            <span class="channel-name">{{ getChannelText(row.channel) }}</span>
            <span class="channel-meta">{{ getChannelHint(row.channel) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="启用状态" width="120">
        <template #default="{ row }">
          <div class="channel-enable">
            <el-switch v-model="row.enabled" />
            <span class="enable-text">{{ row.enabled ? '已启用' : '未启用' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="配置参数" min-width="500">
        <template #default="{ row }">
          <div class="channel-config-panel" :class="{ 'is-disabled': !row.enabled }">
            <div v-if="row.channel === 'DINGTALK'" class="param-grid">
              <div class="param-item span-2">
                <span class="param-label required">Webhook URL</span>
                <el-input
                  v-model="row.configParams.webhookUrl"
                  placeholder="请输入钉钉 webhook 地址"
                  :disabled="!row.enabled"
                />
              </div>
              <div class="param-item span-2">
                <span class="param-label">加签密钥</span>
                <el-input
                  v-model="row.configParams.secret"
                  placeholder="留空或保留掩码则不变"
                  type="password"
                  show-password
                  :disabled="!row.enabled"
                />
              </div>
              <div class="param-item span-2">
                <span class="param-label">接收人</span>
                <div class="param-action-row">
                  <el-input
                    v-model="row.configParams.recipients"
                    placeholder="多个用逗号分隔（可选）"
                    :disabled="!row.enabled"
                  />
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
            </div>

            <div v-else-if="row.channel === 'WECHAT'" class="param-grid">
              <div class="param-item span-2">
                <span class="param-label required">Webhook URL</span>
                <div class="param-action-row">
                  <el-input
                    v-model="row.configParams.webhookUrl"
                    placeholder="请输入企业微信 webhook 地址"
                    :disabled="!row.enabled"
                  />
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
            </div>

            <div v-else-if="row.channel === 'EMAIL'" class="param-grid">
              <div class="param-item">
                <span class="param-label required">SMTP 服务器</span>
                <el-input
                  v-model="row.configParams.smtpHost"
                  placeholder="SMTP 服务器地址"
                  :disabled="!row.enabled"
                />
              </div>
              <div class="param-item">
                <span class="param-label required">端口</span>
                <el-input
                  v-model="row.configParams.smtpPort"
                  placeholder="如 465 / 587"
                  :disabled="!row.enabled"
                />
              </div>
              <div class="param-item">
                <span class="param-label required">发件邮箱（登录账号）</span>
                <el-input
                  v-model="row.configParams.username"
                  placeholder="如 no-reply@example.com"
                  :disabled="!row.enabled"
                />
              </div>
              <div class="param-item">
                <span class="param-label required">密码</span>
                <el-input
                  v-model="row.configParams.password"
                  placeholder="留空或保留掩码则不变"
                  type="password"
                  show-password
                  :disabled="!row.enabled"
                />
              </div>
              <div class="param-item span-2">
                <span class="param-label required">收件人邮箱</span>
                <el-input
                  v-model="row.configParams.recipients"
                  placeholder="多个用逗号分隔"
                  :disabled="!row.enabled"
                />
              </div>
            </div>

            <div v-else-if="row.channel === 'WEBHOOK'" class="param-grid">
              <div class="param-item span-2">
                <span class="param-label required">Webhook URL</span>
                <el-input
                  v-model="row.configParams.webhookUrl"
                  placeholder="请输入 Webhook 地址"
                  :disabled="!row.enabled"
                />
              </div>
            </div>

            <div v-else-if="row.channel === 'FEISHU'" class="param-grid">
              <div class="param-item span-2">
                <span class="param-label required">Webhook URL</span>
                <el-input
                  v-model="row.configParams.webhookUrl"
                  placeholder="请输入飞书 webhook 地址"
                  :disabled="!row.enabled"
                />
              </div>
              <div class="param-item span-2">
                <span class="param-label">加签密钥</span>
                <el-input
                  v-model="row.configParams.secret"
                  placeholder="留空或保留掩码则不变"
                  type="password"
                  show-password
                  :disabled="!row.enabled"
                />
              </div>
              <div class="param-item span-2">
                <span class="param-label">接收人</span>
                <div class="param-action-row">
                  <el-input
                    v-model="row.configParams.recipients"
                    placeholder="多个用逗号分隔（可选）"
                    :disabled="!row.enabled"
                  />
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
            </div>

            <div v-else class="config-empty">
              暂无可配置项
            </div>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { InfoFilled } from '@element-plus/icons-vue'
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

const getChannelHint = (channel) => {
  const map = {
    EMAIL: 'SMTP',
    DINGTALK: '机器人',
    WECHAT: '机器人',
    FEISHU: '机器人',
    WEBHOOK: 'HTTP 回调'
  }
  return map[channel] || '通知渠道'
}
</script>
