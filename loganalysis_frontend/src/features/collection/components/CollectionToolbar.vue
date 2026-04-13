<template>
  <el-card class="toolbar-card">
    <el-row :gutter="20" align="middle">
      <el-col :span="18">
        <el-button type="primary" :icon="Plus" @click="emit('create-project')">新建项目</el-button>
        <el-button v-if="hasCurrentProject" type="success" :icon="Plus" @click="emit('create-source')">新建采集源</el-button>
        <el-button :icon="Refresh" @click="emit('refresh')">刷新</el-button>
      </el-col>
      <el-col :span="6" class="toolbar-right">
        <el-tag type="info">采集源: {{ sourceCount }}</el-tag>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
import { Plus, Refresh } from '@element-plus/icons-vue'
import '../styles/collection-toolbar.css'

defineProps({
  sourceCount: {
    type: Number,
    default: 0
  },
  hasCurrentProject: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['create-project', 'create-source', 'refresh'])
</script>
