<template>
  <div class="skeleton-wrapper" :class="wrapperClass">
    <template v-if="variant === 'card'">
      <div class="skeleton-card-header">
        <div class="skeleton skeleton-circle" :style="{ width: `${avatarSize}px`, height: `${avatarSize}px` }" />
        <div class="skeleton-card-info">
          <div class="skeleton skeleton-text" style="width: 40%" />
          <div class="skeleton skeleton-text" style="width: 25%" />
        </div>
      </div>
      <div class="skeleton-card-body">
        <div v-for="i in rows" :key="i" class="skeleton skeleton-text" :style="{ width: i === rows ? '60%' : '100%' }" />
      </div>
    </template>

    <template v-else-if="variant === 'table'">
      <div class="skeleton-table-header">
        <div v-for="i in columns" :key="i" class="skeleton skeleton-text" :style="{ width: `${100 / columns}%` }" />
      </div>
      <div v-for="j in rows" :key="j" class="skeleton-table-row">
        <div v-for="i in columns" :key="i" class="skeleton skeleton-text" :style="{ width: `${80 + Math.random() * 20}%` }" />
      </div>
    </template>

    <template v-else-if="variant === 'chart'">
      <div class="skeleton-chart">
        <div v-for="i in 5" :key="i" class="skeleton-chart-bar">
          <div class="skeleton" :style="{ height: `${30 + Math.random() * 50}%` }" />
        </div>
      </div>
    </template>

    <template v-else-if="variant === 'list'">
      <div v-for="i in rows" :key="i" class="skeleton-list-item">
        <div class="skeleton skeleton-circle" :style="{ width: `${avatarSize}px`, height: `${avatarSize}px` }" />
        <div class="skeleton-list-content">
          <div class="skeleton skeleton-text" :style="{ width: `${50 + Math.random() * 30}%` }" />
          <div class="skeleton skeleton-text" :style="{ width: `${30 + Math.random() * 20}%` }" />
        </div>
      </div>
    </template>

    <template v-else>
      <div v-for="i in rows" :key="i" class="skeleton skeleton-text" :style="{ width: i === rows ? '60%' : '100%' }" />
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'text',
    validator: (val) => ['text', 'card', 'table', 'chart', 'list'].includes(val)
  },
  rows: {
    type: Number,
    default: 3
  },
  columns: {
    type: Number,
    default: 4
  },
  avatarSize: {
    type: Number,
    default: 40
  }
})

const wrapperClass = computed(() => [
  `skeleton-${props.variant}`,
  'skeleton-animated'
])
</script>

<style scoped>
.skeleton-wrapper {
  width: 100%;
}

.skeleton-card-header {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-md);
}

.skeleton-card-info {
  flex: 1;
}

.skeleton-card-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.skeleton-table-header {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-md);
  background: var(--surface-muted);
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
}

.skeleton-table-row {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-md);
  border-bottom: 1px solid var(--panel-border);
}

.skeleton-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 200px;
  padding: var(--space-md);
}

.skeleton-chart-bar {
  flex: 1;
  margin: 0 var(--space-xs);
  display: flex;
  align-items: flex-end;
}

.skeleton-chart-bar .skeleton {
  width: 100%;
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
}

.skeleton-list-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--panel-border);
}

.skeleton-list-item:last-child {
  border-bottom: none;
}

.skeleton-list-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.skeleton-animated .skeleton {
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}
</style>
