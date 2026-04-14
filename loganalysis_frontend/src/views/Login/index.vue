<script setup>
import { computed, reactive, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { authApi } from '@/api'
import { setAccessToken } from '@/features/auth/token'

const router = useRouter()
const route = useRoute()

const formRef = shallowRef(null)
const loading = shallowRef(false)
const form = reactive({
  username: '',
  password: ''
})

const rules = Object.freeze({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
})

const redirectPath = computed(() => {
  const redirect = route.query.redirect
  if (
    typeof redirect === 'string' &&
    redirect.startsWith('/') &&
    !redirect.startsWith('/login')
  ) {
    return redirect
  }
  return '/'
})

const handleSubmit = async () => {
  if (!formRef.value || loading.value) {
    return
  }

  const isValid = await formRef.value.validate().catch(() => false)
  if (!isValid) {
    return
  }

  loading.value = true
  try {
    const res = await authApi.login({
      username: form.username,
      password: form.password
    })
    const token = res?.data?.token || ''
    if (!token) {
      throw new Error('登录成功但未返回 token')
    }
    setAccessToken(token)
    await router.replace(redirectPath.value)
    ElMessage.success('登录成功')
  } catch (_) {
    // 错误提示已由 axios 全局拦截器统一处理，避免重复弹框
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <header class="login-header">
        <h1 class="login-title">LogAnalysis</h1>
        <p class="login-subtitle">管理员登录</p>
      </header>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="login-form"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="submit-button"
          :loading="loading"
          @click="handleSubmit"
        >
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100svh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 80% 0%, rgba(201, 100, 66, 0.22), transparent 46%),
    radial-gradient(circle at 0% 90%, rgba(38, 37, 30, 0.12), transparent 42%),
    #f6f3ea;
}

.login-card {
  width: min(440px, 100%);
  padding: 28px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(38, 37, 30, 0.12);
  box-shadow: 0 20px 60px rgba(38, 37, 30, 0.1);
}

.login-header {
  margin-bottom: 18px;
}

.login-title {
  margin: 0;
  font-size: 30px;
  letter-spacing: 0.02em;
  color: #26251e;
}

.login-subtitle {
  margin: 8px 0 0;
  color: rgba(38, 37, 30, 0.68);
}

.submit-button {
  width: 100%;
  margin-top: 8px;
}
</style>
