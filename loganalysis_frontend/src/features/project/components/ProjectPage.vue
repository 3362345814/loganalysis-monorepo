<template>
  <div class="project-page">
    <!-- 操作栏 -->
    <el-card class="toolbar-card">
      <el-row :gutter="20" align="middle">
        <el-col :span="18">
          <el-button type="primary" :icon="Plus" @click="handleCreate">新建项目</el-button>
          <el-button :icon="Refresh" @click="loadProjects">刷新</el-button>
        </el-col>
        <el-col :span="6" class="toolbar-right">
          <el-tag type="info">项目数: {{ projects.length }}</el-tag>
        </el-col>
      </el-row>
    </el-card>

    <!-- 项目列表 -->
    <el-card class="table-card">
      <el-table :data="projects" v-loading="loading">
        <el-table-column prop="name" label="项目名称" min-width="150" />
        <el-table-column prop="code" label="项目代码" width="120" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="owner" label="负责人" width="120" />
        <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
        <el-table-column label="采集配置" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.collectionSourceType" type="success">
              {{ formatSourceType(row.collectionSourceType) }}
            </el-tag>
            <el-tag v-else type="info">未配置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑项目' : '新建项目'" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="项目描述" />
        </el-form-item>
        <el-form-item label="负责人" prop="owner">
          <el-input v-model="form.owner" placeholder="项目负责人" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="联系人邮箱" />
        </el-form-item>
        <el-divider content-position="left">采集配置（可选）</el-divider>
        <el-form-item label="采集类型">
          <el-select v-model="form.collectionSourceType" placeholder="不配置" clearable @change="handleProjectSourceTypeChange">
            <el-option label="本地文件" value="LOCAL_FILE" />
            <el-option label="SSH远程" value="SSH" />
          </el-select>
        </el-form-item>
        <template v-if="form.collectionSourceType === 'SSH'">
          <el-form-item label="SSH主机" prop="sshHost">
            <el-input v-model="form.sshHost" placeholder="如 192.168.1.100" />
          </el-form-item>
          <el-form-item label="SSH端口" prop="sshPort">
            <el-input-number v-model="form.sshPort" :min="1" :max="65535" :controls="false" />
          </el-form-item>
          <el-form-item label="SSH用户" prop="sshUsername">
            <el-input v-model="form.sshUsername" placeholder="SSH用户名" />
          </el-form-item>
          <el-form-item label="SSH密码" prop="sshPassword">
            <el-input
              v-model="form.sshPassword"
              type="password"
              show-password
              :placeholder="isEdit && form.sshPasswordConfigured ? '已配置（留空则保留原密码）' : 'SSH密码'"
            />
          </el-form-item>
        </template>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Refresh } from '@element-plus/icons-vue'
import { projectApi } from '@/api'

const projects = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  name: '',
  description: '',
  owner: '',
  email: '',
  collectionSourceType: null,
  sshHost: '',
  sshPort: 22,
  sshUsername: '',
  sshPassword: '',
  sshPasswordConfigured: false,
  enabled: true
})

const rules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  sshHost: [{
    validator: (rule, value, callback) => {
      if (form.collectionSourceType !== 'SSH' || value?.trim()) return callback()
      callback(new Error('请输入SSH主机地址'))
    },
    trigger: 'blur'
  }],
  sshUsername: [{
    validator: (rule, value, callback) => {
      if (form.collectionSourceType !== 'SSH' || value?.trim()) return callback()
      callback(new Error('请输入SSH用户名'))
    },
    trigger: 'blur'
  }],
  sshPassword: [{
    validator: (rule, value, callback) => {
      if (form.collectionSourceType !== 'SSH') return callback()
      if (value?.trim() || (isEdit.value && form.sshPasswordConfigured)) return callback()
      callback(new Error('请输入SSH密码'))
    },
    trigger: 'blur'
  }]
}

onMounted(() => {
  loadProjects()
})

const loadProjects = async () => {
  loading.value = true
  try {
    const res = await projectApi.getAll()
    projects.value = res.data || []
  } catch (error) {
    console.error('加载项目失败:', error)
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  isEdit.value = false
  Object.assign(form, createEmptyProjectForm())
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(form, {
    ...row,
    sshPassword: '',
    sshPasswordConfigured: row.sshPasswordConfigured === true,
    sshPort: row.sshPort || 22
  })
  dialogVisible.value = true
}

const createEmptyProjectForm = () => ({
  id: null,
  name: '',
  code: '',
  description: '',
  owner: '',
  email: '',
  collectionSourceType: null,
  sshHost: '',
  sshPort: 22,
  sshUsername: '',
  sshPassword: '',
  sshPasswordConfigured: false,
  enabled: true
})

const handleProjectSourceTypeChange = (value) => {
  if (value !== 'SSH') {
    form.sshHost = ''
    form.sshPort = 22
    form.sshUsername = ''
    form.sshPassword = ''
  }
}

const formatSourceType = (sourceType) => {
  const map = {
    LOCAL_FILE: '本地文件',
    SSH: 'SSH远程'
  }
  return map[sourceType] || sourceType
}

const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await projectApi.update(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await projectApi.create({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadProjects()
  } catch (error) {
    console.error('操作失败:', error)
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除项目"${row.name}"吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await projectApi.delete(row.id)
      ElMessage.success('删除成功')
      loadProjects()
    } catch (error) {
      console.error('删除失败:', error)
    }
  }).catch(() => {})
}
</script>

<style scoped src="../styles/project-page.css"></style>
