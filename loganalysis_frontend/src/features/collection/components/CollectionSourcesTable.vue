<template>
  <el-card class="table-card">
    <el-table :data="sources" v-loading="loading">
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="projectName" label="所属项目" width="120">
        <template #default="{ row }">
          <el-tag type="info">{{ row.projectName || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="类型" width="140">
        <template #default="{ row }">
          <el-tag type="info">{{ row.sourceType || 'LOCAL_FILE' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="路径" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ formatPaths(row.paths) }}
        </template>
      </el-table-column>
      <el-table-column prop="logFormat" label="格式" width="100">
        <template #default="{ row }">
          <el-tag type="info">{{ getLogFormatText(row.logFormat) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="aggregationLevel" label="聚合级别" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.aggregationLevel" :type="row.aggregationLevel === 'ERROR' ? 'danger' : 'warning'">
            {{ row.aggregationLevel }}及以上
          </el-tag>
          <el-tag v-else type="info">全部</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="错误信息" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip
            v-if="row.status === 'ERROR' && row.lastErrorMessage"
            :content="row.lastErrorMessage"
            placement="top"
          >
            <span class="collector-error">
              <WarningFilled class="collector-error-icon" />
              {{ row.lastErrorMessage }}
            </span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button
              v-if="row.status !== 'RUNNING'"
              type="primary"
              size="small"
              :icon="VideoPlay"
              @click="emit('start', row)"
            >
              启动
            </el-button>
            <el-button
              v-else
              type="warning"
              size="small"
              :icon="VideoPause"
              @click="emit('stop', row)"
            >
              停止
            </el-button>
            <el-button size="small" :icon="Edit" @click="emit('edit', row)">编辑</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="emit('delete', row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { Delete, Edit, VideoPause, VideoPlay, WarningFilled } from '@element-plus/icons-vue'
import '../styles/collection-sources-table.css'

defineProps({
  sources: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['start', 'stop', 'edit', 'delete'])

const getStatusType = (status) => {
  const map = {
    STOPPED: 'info',
    STOPPING: 'warning',
    RUNNING: 'success',
    ERROR: 'danger'
  }
  return map[status] || 'info'
}

const getLogFormatText = (format) => {
  const map = {
    LOG4J: 'Log4j',
    NGINX: 'Nginx',
    JSON: 'JSON'
  }
  return map[format] || 'Log4j'
}

const getStatusText = (status) => {
  const map = {
    STOPPED: '已停止',
    STOPPING: '停止中',
    RUNNING: '运行中',
    ERROR: '错误'
  }
  return map[status] || '未知'
}

const formatPaths = (paths) => {
  if (!paths || !Array.isArray(paths)) {
    return '-'
  }
  return paths.join(', ')
}
</script>
