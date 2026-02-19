<template>
  <div class="collection-page">
    <!-- 操作栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="18">
          <el-button type="primary" :icon="Plus" @click="handleCreate">新建采集源</el-button>
          <el-button :icon="Refresh" @click="loadSources">刷新</el-button>
        </el-col>
        <el-col :span="6" style="text-align: right">
          <el-tag type="success">采集源: {{ sources.length }}</el-tag>
        </el-col>
      </el-row>
    </el-card>

    <!-- 日志源列表 -->
    <el-card class="table-card">
      <el-table :data="sources" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="sourceType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.sourceType || 'LOCAL_FILE' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" disabled />
          </template>
        </el-table-column>
        <el-table-column prop="lastCollectionTime" label="最后采集" width="180">
          <template #default="{ row }">
            {{ formatTime(row.lastCollectionTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button 
              v-if="row.status !== 'COLLECTING'" 
              type="primary" 
              size="small" 
              :icon="VideoPlay" 
              @click="handleStart(row)"
            >
              启动
            </el-button>
            <el-button 
              v-else 
              type="warning" 
              size="small" 
              :icon="VideoPause" 
              @click="handleStop(row)"
            >
              停止
            </el-button>
            <el-button size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑采集源' : '新建采集源'" 
      width="600px"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入采集源名称" />
        </el-form-item>
        <el-form-item label="类型" prop="sourceType">
          <el-select v-model="form.sourceType" placeholder="请选择类型">
            <el-option label="本地文件" value="LOCAL_FILE" />
            <el-option label="远程日志" value="REMOTE" />
            <el-option label="数据库" value="DATABASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径" prop="path">
          <el-input v-model="form.path" placeholder="日志文件路径，如 /var/log/app.log" />
        </el-form-item>
        <el-form-item label="编码" prop="encoding">
          <el-select v-model="form.encoding" placeholder="选择编码">
            <el-option label="UTF-8" value="UTF-8" />
            <el-option label="GBK" value="GBK" />
            <el-option label="GB2312" value="GB2312" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, Refresh, Edit, Delete, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { logSourceApi } from '@/api'

const sources = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = ref({
  id: null,
  name: '',
  sourceType: 'LOCAL_FILE',
  path: '',
  encoding: 'UTF-8',
  description: ''
})

const rules = {
  name: [{ required: true, message: '请输入采集源名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入日志文件路径', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const getStatusType = (status) => {
  const map = {
    'STOPPED': 'info',
    'COLLECTING': 'success',
    'ERROR': 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    'STOPPED': '已停止',
    'COLLECTING': '采集中',
    'ERROR': '错误'
  }
  return map[status] || '未知'
}

const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

const loadSources = async () => {
  loading.value = true
  try {
    const res = await logSourceApi.getAll()
    sources.value = res.data || []
  } catch (error) {
    console.error('加载采集源失败:', error)
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  isEdit.value = false
  form.value = {
    id: null,
    name: '',
    sourceType: 'LOCAL_FILE',
    path: '',
    encoding: 'UTF-8',
    description: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await logSourceApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await logSourceApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadSources()
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    submitting.value = false
  }
}

const handleStart = async (row) => {
  try {
    await logSourceApi.startCollector(row.id)
    ElMessage.success('采集器已启动')
    loadSources()
  } catch (error) {
    console.error('启动失败:', error)
  }
}

const handleStop = async (row) => {
  try {
    await logSourceApi.stopCollector(row.id)
    ElMessage.success('采集器已停止')
    loadSources()
  } catch (error) {
    console.error('停止失败:', error)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除采集源 "${row.name}" 吗？`,
      '警告',
      { type: 'warning' }
    )
    await logSourceApi.delete(row.id)
    ElMessage.success('删除成功')
    loadSources()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

onMounted(() => {
  loadSources()
})
</script>

<style scoped>
.collection-page {
  padding: 20px;
}

.toolbar-card {
  margin-bottom: 20px;
}

.table-card {
  min-height: 500px;
}
</style>
