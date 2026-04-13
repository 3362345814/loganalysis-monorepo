<template>
  <el-card v-if="currentProject" class="project-info-card">
    <div class="project-info">
      <el-icon><FolderOpened /></el-icon>
      <span class="project-name">{{ currentProject.name }}</span>
      <el-button type="info" link @click="emit('select-project')">切换项目</el-button>
    </div>
  </el-card>

  <el-card v-else class="project-info-card">
    <div class="project-info-empty">
      <el-icon><WarningFilled /></el-icon>
      <span>请先选择一个项目，或创建新项目</span>
      <el-button type="primary" size="small" class="project-select-btn" @click="emit('select-project')">选择项目</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { FolderOpened, WarningFilled } from '@element-plus/icons-vue'
import '../styles/collection-project-info.css'

defineProps({
  currentProject: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['select-project'])
</script>
