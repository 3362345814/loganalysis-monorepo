<template>
  <el-card class="tab-content-card analysis-config-card animate-fade-in-up">
    <template #header>
      <div class="card-header">
        <span>分析参数</span>
        <el-button type="primary" @click="emit('save-analysis-config')" :loading="savingAnalysisConfig">
          保存配置
        </el-button>
      </div>
    </template>

    <el-form :model="analysisConfigForm" inline class="analysis-form">
      <el-form-item label="上下文行数">
        <el-input-number v-model="analysisConfigForm.contextSize" :min="10" :max="30" :controls="false" />
        <span class="context-range-tip">行 (10-30)</span>
      </el-form-item>
      <el-form-item label="自动分析级别">
        <el-select v-model="analysisConfigForm.autoAnalysisSeverity" class="analysis-level-select">
          <el-option label="ERROR 及以上" value="ERROR" />
          <el-option label="WARNING 及以上" value="WARNING" />
        </el-select>
      </el-form-item>
      <el-form-item label="启用自动分析">
        <el-switch v-model="analysisConfigForm.autoAnalysisEnabled" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import '../styles/config-analysis-tab.css'

defineProps({
  analysisConfigForm: {
    type: Object,
    required: true
  },
  savingAnalysisConfig: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['save-analysis-config'])
</script>
