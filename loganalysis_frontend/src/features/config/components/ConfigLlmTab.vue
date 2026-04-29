<template>
  <el-card class="tab-content-card animate-fade-in-up">
    <template #header>
      <div class="card-header">
        <span>LLM 配置管理</span>
        <div class="llm-header-actions">
          <el-tag type="info" class="active-config-tag">当前活跃: {{ activeConfigName }}</el-tag>
          <el-button type="primary" @click="emit('add-llm')">
            <el-icon><Plus /></el-icon>新增配置
          </el-button>
        </div>
      </div>
    </template>

    <el-table :data="llmConfigList" v-loading="loadingLlm" class="llm-table" :tooltip-options="tableTooltipOptions">
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
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" :icon="Edit" @click="emit('edit-llm', row)">编辑</el-button>
          <el-button type="success" link size="small" :icon="Star" @click="emit('set-default-llm', row)" :disabled="row.isDefault">
            设为默认
          </el-button>
          <el-button
            type="danger"
            link
            size="small"
            :icon="Delete"
            @click="emit('delete-llm', row)"
            :disabled="llmConfigList.length <= 1"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { Plus, Edit, Star, Delete } from '@element-plus/icons-vue'
import '../styles/config-llm-tab.css'

defineProps({
  llmConfigList: {
    type: Array,
    default: () => []
  },
  loadingLlm: {
    type: Boolean,
    default: false
  },
  activeConfigName: {
    type: String,
    default: '未配置'
  }
})

const emit = defineEmits(['add-llm', 'edit-llm', 'set-default-llm', 'delete-llm'])
const tableTooltipOptions = {
  popperClass: 'limited-table-tooltip',
  enterable: true
}
</script>
