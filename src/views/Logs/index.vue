<template>
  <div class="logs-page">
    <!-- 筛选栏 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="日志源">
          <el-select v-model="filter.sourceId" placeholder="请选择日志源" clearable @change="handleSourceChange">
            <el-option v-for="source in sources" :key="source.id" :label="source.name" :value="source.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filter.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 日志列表 -->
    <el-card class="table-card">
      <el-table :data="logs" v-loading="loading" stripe max-height="600">
        <el-table-column prop="id" label="ID" width="220" show-overflow-tooltip />
        <el-table-column prop="sourceId" label="日志源ID" width="220" show-overflow-tooltip />
        <el-table-column prop="message" label="日志内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="filter.page"
          v-model:page-size="filter.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="700px">
      <el-descriptions :column="1" border v-if="currentLog">
        <el-descriptions-item label="ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="日志源ID">{{ currentLog.sourceId }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(currentLog.timestamp) }}</el-descriptions-item>
        <el-descriptions-item label="日志内容">
          <pre class="log-content">{{ currentLog.message }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { logSourceApi, rawLogApi } from '@/api'

const sources = ref([])
const logs = ref([])
const loading = ref(false)
const total = ref(0)
const detailVisible = ref(false)
const currentLog = ref(null)

const filter = ref({
  sourceId: null,
  dateRange: null,
  page: 1,
  pageSize: 20
})

const loadSources = async () => {
  try {
    const res = await logSourceApi.getAll()
    sources.value = res.data || []
  } catch (error) {
    console.error('加载日志源失败:', error)
  }
}

const loadLogs = async () => {
  if (!filter.value.sourceId) {
    ElMessage.warning('请选择日志源')
    return
  }
  
  loading.value = true
  try {
    const params = {
      page: filter.value.page - 1,
      size: filter.value.pageSize
    }
    const res = await rawLogApi.getBySourceId(filter.value.sourceId, params)
    logs.value = res.data?.content || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('加载日志失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSourceChange = () => {
  filter.value.page = 1
  if (filter.value.sourceId) {
    loadLogs()
  } else {
    logs.value = []
  }
}

const handleSearch = () => {
  filter.value.page = 1
  loadLogs()
}

const handleReset = () => {
  filter.value = {
    sourceId: null,
    dateRange: null,
    page: 1,
    pageSize: 20
  }
  logs.value = []
}

const handlePageChange = (page) => {
  filter.value.page = page
  loadLogs()
}

const handleSizeChange = (size) => {
  filter.value.pageSize = size
  loadLogs()
}

const handleView = (row) => {
  currentLog.value = row
  detailVisible.value = true
}

const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

onMounted(() => {
  loadSources()
})
</script>

<style scoped>
.logs-page {
  padding: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.table-card {
  min-height: 500px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.log-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 300px;
  overflow: auto;
}
</style>
